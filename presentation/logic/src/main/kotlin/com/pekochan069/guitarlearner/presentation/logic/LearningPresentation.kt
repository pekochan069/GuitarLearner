package com.pekochan069.guitarlearner.presentation.logic

import androidx.compose.runtime.saveable.Saver
import com.pekochan069.guitarlearner.domain.*
import com.pekochan069.guitarlearner.presentation.contract.*

internal sealed interface LearningPage {
    val mode: LearningModeUi
    data class Catalog(override val mode: LearningModeUi = LearningModeUi.Topics) : LearningPage
    data class Lesson(val id: LessonId, override val mode: LearningModeUi) : LearningPage
    data class Exploration(val concept: ExplorationConcept) : LearningPage {
        override val mode: LearningModeUi get() = LearningModeUi.Explore
    }
}

internal val LearningPage.target: LearningTarget? get() = when (this) {
    is LearningPage.Catalog -> null
    is LearningPage.Lesson -> LearningTarget.Lesson(id)
    is LearningPage.Exploration -> LearningTarget.Explore(concept)
}

internal data class LinkedLessonReturn(val page: LearningPage.Lesson, val selection: LearningSelection,
    val instrument: TrainingInstrumentUi, val feature: FeatureId)

internal fun LearningSnapshot.toUi(page: LearningPage, selection: LearningSelection, instrument: TrainingInstrumentUi): LearningUiState {
    val lesson = (page as? LearningPage.Lesson)?.id
    val model = page.target?.let { LearningRelations.describe(it, selection) } ?: LearningModel()
    val course = lesson?.let { current -> LessonId.entries.filter { it.family == current.family } }.orEmpty()
    val index = course.indexOf(lesson)
    return LearningUiState(
        mode = page.mode,
        lessons = LessonId.entries.map { LearningLessonRowUi(it.toUi(), it.family == LessonFamily.Theory, it in progress.completed) },
        lesson = lesson?.toUi(),
        concept = (page as? LearningPage.Exploration)?.concept?.toUi(),
        lastViewed = progress.lastViewed?.toUi(),
        previousLesson = course.getOrNull(index - 1)?.toUi(),
        nextLesson = course.getOrNull(index + 1)?.toUi(),
        completed = lesson in progress.completed,
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
        circle = model.circle.map { LearningCircleKeyUi(it.tonic.symbol, it.relativeMinor.symbol) },
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
private fun ExplorationConcept.toUi(): LearningConceptUi = when (this) {
    ExplorationConcept.NotesIntervals -> LearningConceptUi.NotesIntervals; ExplorationConcept.Scales -> LearningConceptUi.Scales
    ExplorationConcept.ChordConstruction -> LearningConceptUi.Chords; ExplorationConcept.DiatonicFunctions -> LearningConceptUi.DiatonicFunctions
    ExplorationConcept.BasicProgressions -> LearningConceptUi.Progressions; ExplorationConcept.CircleOfFifths -> LearningConceptUi.CircleOfFifths
}
internal fun LearningConceptUi.toDomain(): ExplorationConcept = when (this) {
    LearningConceptUi.NotesIntervals -> ExplorationConcept.NotesIntervals; LearningConceptUi.Scales -> ExplorationConcept.Scales
    LearningConceptUi.Chords -> ExplorationConcept.ChordConstruction; LearningConceptUi.DiatonicFunctions -> ExplorationConcept.DiatonicFunctions
    LearningConceptUi.Progressions -> ExplorationConcept.BasicProgressions; LearningConceptUi.CircleOfFifths -> ExplorationConcept.CircleOfFifths
}

internal fun LessonId.trainingLinks(): List<TrainingExerciseUi> = when (this) {
    LessonId.NotesIntervals -> listOf(TrainingExerciseUi.NoteListening, TrainingExerciseUi.IntervalListening, TrainingExerciseUi.FretboardNote)
    LessonId.Scales, LessonId.ChordConstruction -> listOf(TrainingExerciseUi.FretboardNote)
    LessonId.DiatonicFunctions, LessonId.BasicProgressions, LessonId.CircleOfFifths -> emptyList()
    else -> listOf(TrainingExerciseUi.TabNote)
}
internal fun LessonId.toolLinks(): List<FeatureId> = when (this) {
    LessonId.ChordConstruction, LessonId.DiatonicFunctions, LessonId.BasicProgressions -> listOf(FeatureId.Chords)
    LessonId.NotesIntervals, LessonId.Scales, LessonId.CircleOfFifths -> emptyList()
    else -> listOf(FeatureId.Metronome)
}

private fun LearningPage.saved(): List<String> = when (this) {
    is LearningPage.Catalog -> listOf("catalog", mode.name)
    is LearningPage.Lesson -> listOf("lesson", id.savedId, mode.name)
    is LearningPage.Exploration -> listOf("exploration", concept.name)
}
private fun restoreLearningPage(saved: Any?): LearningPage {
    val values = saved as? List<*>
    return when (values?.firstOrNull()) {
        "lesson" -> {
            val id = LessonId.entries.firstOrNull { it.savedId == values.getOrNull(1) }
            val mode = LearningModeUi.entries.firstOrNull { it.name == values.getOrNull(2) && it != LearningModeUi.Explore }
            if (id != null && mode != null) LearningPage.Lesson(id, mode) else LearningPage.Catalog()
        }
        "exploration" -> ExplorationConcept.entries.firstOrNull { it.name == values.getOrNull(1) }
            ?.let(LearningPage::Exploration) ?: LearningPage.Catalog()
        "catalog" -> LearningModeUi.entries.firstOrNull { it.name == values.getOrNull(1) }
            ?.let(LearningPage::Catalog) ?: LearningPage.Catalog()
        else -> LearningPage.Catalog()
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
        val feature = FeatureId.entries.firstOrNull { it.name == values?.getOrNull(3) && it != FeatureId.Learning }
        if (page != null && instrument != null && feature != null) LinkedLessonReturn(page,
            restoreLearningSelection(values?.getOrNull(1)), instrument, feature) else null
    },
)
