package com.pekochan069.guitarlearner.presentation.logic

import androidx.compose.runtime.saveable.Saver
import com.pekochan069.guitarlearner.domain.*
import com.pekochan069.guitarlearner.presentation.contract.*

internal enum class LessonOrigin { Topics, Course }
internal sealed interface LearningPage {
    data object Topics : LearningPage
    data object Courses : LearningPage
    data class CourseOverview(val family: LessonFamily) : LearningPage
    data class Lesson(val id: LessonId, val origin: LessonOrigin) : LearningPage
}

internal val FeatureId.isLearning: Boolean get() = this == FeatureId.Learning || this == FeatureId.LearningCourses
internal val LearningPage.feature: FeatureId get() = when (this) {
    LearningPage.Topics -> FeatureId.Learning
    LearningPage.Courses, is LearningPage.CourseOverview -> FeatureId.LearningCourses
    is LearningPage.Lesson -> if (origin == LessonOrigin.Course) FeatureId.LearningCourses else FeatureId.Learning
}
internal val LearningPage.parent: LearningPage? get() = when (this) {
    LearningPage.Topics, LearningPage.Courses -> null
    is LearningPage.CourseOverview -> LearningPage.Courses
    is LearningPage.Lesson -> if (origin == LessonOrigin.Course) LearningPage.CourseOverview(id.family) else LearningPage.Topics
}
internal val LearningPage.target: LearningTarget? get() = (this as? LearningPage.Lesson)?.let { LearningTarget.Lesson(it.id) }

internal fun CourseUi.toDomain(): LessonFamily = when (this) { CourseUi.Theory -> LessonFamily.Theory; CourseUi.Technique -> LessonFamily.Technique }
private fun LessonFamily.toUi(): CourseUi = when (this) { LessonFamily.Theory -> CourseUi.Theory; LessonFamily.Technique -> CourseUi.Technique }
internal fun LearningProgress.courseEntry(family: LessonFamily): LessonId {
    val lessons = LessonId.entries.filter { it.family == family }
    return lessons.firstOrNull { it !in completed } ?: lessons.first()
}

internal data class LinkedLessonReturn(val page: LearningPage.Lesson, val selection: LearningSelection,
    val instrument: TrainingInstrumentUi, val feature: FeatureId)

internal fun LearningSnapshot.toUi(page: LearningPage, selection: LearningSelection, instrument: TrainingInstrumentUi): LearningUiState {
    val lesson = (page as? LearningPage.Lesson)?.id
    val model = page.target?.let { LearningRelations.describe(it, selection) } ?: LearningModel()
    val rows = LessonId.entries.map { LearningLessonRowUi(it.toUi(), it.family == LessonFamily.Theory, it in progress.completed) }
    fun course(family: LessonFamily) = LearningCourseUi(family.toUi(), rows.filter { it.theory == (family == LessonFamily.Theory) },
        progress.courseEntry(family).toUi())
    val uiPage = when (page) {
        LearningPage.Topics -> LearningUiPage.Topics(rows, progress.lastViewed?.toUi())
        LearningPage.Courses -> LearningUiPage.Courses(LessonFamily.entries.map(::course))
        is LearningPage.CourseOverview -> LearningUiPage.CourseOverview(course(page.family))
        is LearningPage.Lesson -> {
            val lessons = LessonId.entries.filter { it.family == page.id.family }
            val index = lessons.indexOf(page.id)
            LearningUiPage.Lesson(page.id.toUi(), page.id in progress.completed, if (page.origin == LessonOrigin.Course)
                LearningLessonContextUi.Course(page.id.family.toUi(), index + 1, lessons.size,
                    lessons.getOrNull(index - 1)?.toUi(), lessons.getOrNull(index + 1)?.toUi()) else LearningLessonContextUi.Topics)
        }
    }
    return LearningUiState(
        page = uiPage,
        roots = LearningRelations.tonics.map { it.symbol }, root = selection.tonic.symbol,
        scale = when (selection.scale) { BeginnerScale.Major -> LearningScaleUi.Major; BeginnerScale.NaturalMinor -> LearningScaleUi.NaturalMinor },
        chord = ChordQualityUi.valueOf(selection.chord.name),
        chordChoices = LearningRelations.beginnerChords.map { ChordQualityUi.valueOf(it.name) },
        interval = TrainingIntervalUi.entries[selection.interval.semitones],
        progression = when (selection.progression) {
            BeginnerProgression.OneFourFiveOne -> LearningProgressionUi.OneFourFiveOne
            BeginnerProgression.OneFiveSixFour -> LearningProgressionUi.OneFiveSixFour
            BeginnerProgression.TwoFiveOne -> LearningProgressionUi.TwoFiveOne
        },
        chordIndex = selection.chordIndex.coerceAtMost(model.chords.lastIndex.coerceAtLeast(0)),
        instrument = instrument,
        notes = model.notes.map { it.toUi() },
        frets = model.fretboard.map { LearningFretUi(it.stringNumber, it.fret, it.note.spelling.symbol, it.note.degree.symbol) },
        chords = model.chords.map { LearningChordUi(it.symbol, it.roman, when (it.function) {
            HarmonicFunction.Tonic -> LearningFunctionUi.Tonic
            HarmonicFunction.Predominant -> LearningFunctionUi.Predominant
            HarmonicFunction.Dominant -> LearningFunctionUi.Dominant
        }, it.notes.map { note -> note.toUi() }) },
        circle = model.circle.map { key ->
            val signature = key.keySignature
            LearningCircleKeyUi(key.tonic.symbol, key.relativeMinor.symbol,
                if (signature.isEmpty()) "0" else "${signature.size}${if (signature.first().accidental > 0) "♯" else "♭"}",
                signature.map { it.symbol })
        },
        trainingLinks = lesson?.trainingLinks().orEmpty(), toolLinks = lesson?.toolLinks().orEmpty(),
        audio = when (audio) {
            LearningAudioState.Idle -> LearningAudioUi.Idle
            is LearningAudioState.Preparing -> LearningAudioUi.Preparing
            is LearningAudioState.Playing -> LearningAudioUi.Playing
            is LearningAudioState.Failed -> LearningAudioUi.Failed
        },
        audioNotice = (audio as? LearningAudioState.Failed)?.failure?.toUi(),
        saving = storage == LearningStorageState.Saving,
        saveNotice = (storage as? LearningStorageState.Failed)?.failure?.toUi(),
    )
}

private fun LearningNote.toUi(): LearningNoteUi = LearningNoteUi(spelling.symbol, degree.symbol)
private fun LearningFailure.toUi(): LearningNoticeUi = when (this) {
    LearningFailure.ReadFailed -> LearningNoticeUi.ReadFailed
    LearningFailure.WriteFailed -> LearningNoticeUi.WriteFailed
    LearningFailure.PlaybackFailed -> LearningNoticeUi.PlaybackFailed
    LearningFailure.ShutdownFailed -> LearningNoticeUi.ShutdownFailed
    LearningFailure.FocusDenied -> LearningNoticeUi.FocusDenied
    LearningFailure.OutputInterrupted -> LearningNoticeUi.OutputInterrupted
    is LearningFailure.MetronomeStopFailed -> LearningNoticeUi.MetronomeStopFailed
}

internal fun LessonId.toUi(): LessonUi = when (this) {
    LessonId.NotesIntervals -> LessonUi.NotesIntervals; LessonId.Scales -> LessonUi.Scales
    LessonId.ChordConstruction -> LessonUi.ChordConstruction; LessonId.DiatonicFunctions -> LessonUi.DiatonicFunctions
    LessonId.BasicProgressions -> LessonUi.BasicProgressions; LessonId.CircleOfFifths -> LessonUi.CircleOfFifths
    LessonId.Strumming -> LessonUi.Strumming; LessonId.AlternatePicking -> LessonUi.AlternatePicking
    LessonId.HammerOnPullOff -> LessonUi.HammerOnPullOff; LessonId.Slide -> LessonUi.Slide
    LessonId.Bending -> LessonUi.Bending; LessonId.Vibrato -> LessonUi.Vibrato; LessonId.PalmMute -> LessonUi.PalmMute
}
internal fun LessonUi.toDomain(): LessonId = when (this) {
    LessonUi.NotesIntervals -> LessonId.NotesIntervals; LessonUi.Scales -> LessonId.Scales
    LessonUi.ChordConstruction -> LessonId.ChordConstruction; LessonUi.DiatonicFunctions -> LessonId.DiatonicFunctions
    LessonUi.BasicProgressions -> LessonId.BasicProgressions; LessonUi.CircleOfFifths -> LessonId.CircleOfFifths
    LessonUi.Strumming -> LessonId.Strumming; LessonUi.AlternatePicking -> LessonId.AlternatePicking
    LessonUi.HammerOnPullOff -> LessonId.HammerOnPullOff; LessonUi.Slide -> LessonId.Slide
    LessonUi.Bending -> LessonId.Bending; LessonUi.Vibrato -> LessonId.Vibrato; LessonUi.PalmMute -> LessonId.PalmMute
}
internal fun LessonId.trainingLinks(): List<TrainingExerciseUi> = when (this) {
    LessonId.NotesIntervals -> listOf(TrainingExerciseUi.NoteListening, TrainingExerciseUi.IntervalListening, TrainingExerciseUi.FretboardNote)
    LessonId.Scales, LessonId.ChordConstruction -> listOf(TrainingExerciseUi.FretboardNote)
    LessonId.DiatonicFunctions, LessonId.BasicProgressions, LessonId.CircleOfFifths -> emptyList()
    else -> listOf(TrainingExerciseUi.TabNote)
}
internal fun LessonId.toolLinks(): List<FeatureId> = when (this) {
    LessonId.ChordConstruction, LessonId.DiatonicFunctions -> listOf(FeatureId.Chords)
    LessonId.BasicProgressions -> listOf(FeatureId.Progressions, FeatureId.Chords)
    LessonId.NotesIntervals, LessonId.Scales, LessonId.CircleOfFifths -> emptyList()
    else -> listOf(FeatureId.Metronome)
}

private fun LearningPage.saved(): List<String> = when (this) {
    LearningPage.Topics -> listOf("topics")
    LearningPage.Courses -> listOf("courses")
    is LearningPage.CourseOverview -> listOf("course", family.name)
    is LearningPage.Lesson -> listOf("lesson", id.savedId, origin.name)
}
internal fun restoreLearningPage(saved: Any?): LearningPage {
    val values = saved as? List<*>
    return when (values?.firstOrNull()) {
        "lesson" -> {
            val id = LessonId.entries.firstOrNull { it.savedId == values.getOrNull(1) }
            val origin = when (values.getOrNull(2)) { "Topics" -> LessonOrigin.Topics; "Course", "Courses" -> LessonOrigin.Course; else -> null }
            if (id != null && origin != null) LearningPage.Lesson(id, origin) else LearningPage.Topics
        }
        "exploration" -> ExplorationConcept.entries.firstOrNull { it.name == values.getOrNull(1) }
            ?.let { LearningPage.Lesson(it.lesson, LessonOrigin.Topics) } ?: LearningPage.Topics
        "catalog" -> if (values.getOrNull(1) == "Courses") LearningPage.Courses else LearningPage.Topics
        "courses" -> LearningPage.Courses
        "course" -> LessonFamily.entries.firstOrNull { it.name == values.getOrNull(1) }?.let(LearningPage::CourseOverview) ?: LearningPage.Topics
        else -> LearningPage.Topics
    }
}
internal val LearningPageSaver = Saver<LearningPage, Any>(save = { it.saved() }, restore = { restoreLearningPage(it) })

private fun LearningSelection.saved(): List<Any> = listOf(tonic.letter.name, tonic.accidental, scale.name, chord.name,
    interval.name, progression.name, chordIndex)
private fun restoreLearningSelection(saved: Any?): LearningSelection {
    val values = saved as? List<*> ?: return LearningSelection()
    return try {
        val letter = NoteLetter.entries.firstOrNull { it.name == values.getOrNull(0) } ?: return LearningSelection()
        val accidental = values.getOrNull(1) as? Int ?: return LearningSelection()
        val scale = BeginnerScale.entries.firstOrNull { it.name == values.getOrNull(2) } ?: return LearningSelection()
        val chord = ChordQuality.entries.firstOrNull { it.name == values.getOrNull(3) } ?: return LearningSelection()
        val interval = TrainingInterval.entries.firstOrNull { it.name == values.getOrNull(4) } ?: return LearningSelection()
        val progression = BeginnerProgression.entries.firstOrNull { it.name == values.getOrNull(5) } ?: return LearningSelection()
        val index = values.getOrNull(6) as? Int ?: return LearningSelection()
        LearningSelection(SpelledNote(letter, accidental), scale, chord, interval, progression, index)
    } catch (_: IllegalArgumentException) { LearningSelection() }
}
internal val LearningSelectionSaver = Saver<LearningSelection, Any>(save = { it.saved() }, restore = { restoreLearningSelection(it) })
internal val LinkedLessonReturnSaver = Saver<LinkedLessonReturn?, Any>(
    save = { link -> link?.let { listOf(it.page.saved(), it.selection.saved(), it.instrument.name, it.feature.name) } ?: listOf("no_learning_return") },
    restore = { saved ->
        val values = saved as? List<*>
        val page = restoreLearningPage(values?.getOrNull(0)) as? LearningPage.Lesson
        val instrument = TrainingInstrumentUi.entries.firstOrNull { it.name == values?.getOrNull(2) }
        val feature = FeatureId.entries.firstOrNull { it.name == values?.getOrNull(3) && !it.isLearning }
        if (page != null && instrument != null && feature != null) LinkedLessonReturn(page,
            restoreLearningSelection(values?.getOrNull(1)), instrument, feature) else null
    },
)
