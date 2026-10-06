package com.pekochan069.guitarlearner.presentation.contract

enum class LessonUi {
    NotesIntervals, Scales, ChordConstruction, DiatonicFunctions, BasicProgressions, CircleOfFifths,
    Strumming, AlternatePicking, HammerOnPullOff, Slide, Bending, Vibrato, PalmMute,
}
enum class LearningModeUi { Topics, Courses, Explore }
enum class LearningConceptUi { NotesIntervals, Scales, Chords, DiatonicFunctions, Progressions, CircleOfFifths }
enum class LearningScaleUi { Major, NaturalMinor }
enum class LearningProgressionUi { OneFourFiveOne, OneFiveSixFour, TwoFiveOne }
enum class LearningFunctionUi { Tonic, Predominant, Dominant }
enum class LearningNoticeUi { ReadFailed, WriteFailed, PlaybackFailed, ShutdownFailed, FocusDenied, OutputInterrupted, MetronomeStopFailed }
enum class LearningAudioUi { Idle, Preparing, Playing, Failed }

data class LearningLessonRowUi(val id: LessonUi, val theory: Boolean, val completed: Boolean)
data class LearningNoteUi(val name: String, val degree: String)
data class LearningFretUi(val stringNumber: Int, val fret: Int, val name: String, val degree: String)
data class LearningChordUi(val symbol: String, val roman: String, val function: LearningFunctionUi,
    val notes: List<LearningNoteUi>)
data class LearningCircleKeyUi(val tonic: String, val relativeMinor: String)

data class LearningUiState(
    val mode: LearningModeUi = LearningModeUi.Topics,
    val lessons: List<LearningLessonRowUi> = emptyList(),
    val lesson: LessonUi? = null,
    val concept: LearningConceptUi? = null,
    val lastViewed: LessonUi? = null,
    val previousLesson: LessonUi? = null,
    val nextLesson: LessonUi? = null,
    val completed: Boolean = false,
    val roots: List<String> = emptyList(),
    val root: String = "C",
    val scale: LearningScaleUi = LearningScaleUi.Major,
    val chord: ChordQualityUi = ChordQualityUi.Major,
    val chordChoices: List<ChordQualityUi> = emptyList(),
    val interval: TrainingIntervalUi = TrainingIntervalUi.MajorThird,
    val progression: LearningProgressionUi = LearningProgressionUi.OneFourFiveOne,
    val chordIndex: Int = 0,
    val instrument: TrainingInstrumentUi = TrainingInstrumentUi.Piano,
    val notes: List<LearningNoteUi> = emptyList(),
    val frets: List<LearningFretUi> = emptyList(),
    val chords: List<LearningChordUi> = emptyList(),
    val circle: List<LearningCircleKeyUi> = emptyList(),
    val trainingLinks: List<TrainingExerciseUi> = emptyList(),
    val toolLinks: List<FeatureId> = emptyList(),
    val audio: LearningAudioUi = LearningAudioUi.Idle,
    val audioNotice: LearningNoticeUi? = null,
    val saving: Boolean = false,
    val saveNotice: LearningNoticeUi? = null,
)

sealed interface LearningEvent {
    data class SetMode(val value: LearningModeUi) : LearningEvent
    data class OpenLesson(val id: LessonUi) : LearningEvent
    data class OpenConcept(val value: LearningConceptUi) : LearningEvent
    data object Resume : LearningEvent
    data object Complete : LearningEvent
    data object RetrySave : LearningEvent
    data class SetRoot(val index: Int) : LearningEvent
    data class SetScale(val value: LearningScaleUi) : LearningEvent
    data class SetChord(val value: ChordQualityUi) : LearningEvent
    data class SetInterval(val value: TrainingIntervalUi) : LearningEvent
    data class SetProgression(val value: LearningProgressionUi) : LearningEvent
    data class SelectChord(val index: Int) : LearningEvent
    data class SetInstrument(val value: TrainingInstrumentUi) : LearningEvent
    data object Listen : LearningEvent
    data object Stop : LearningEvent
    data class OpenTraining(val exercise: TrainingExerciseUi) : LearningEvent
    data class OpenTool(val feature: FeatureId) : LearningEvent
}
