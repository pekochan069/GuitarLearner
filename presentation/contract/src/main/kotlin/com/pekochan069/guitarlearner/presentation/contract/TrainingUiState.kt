package com.pekochan069.guitarlearner.presentation.contract

enum class TrainingSubjectUi { Note, Interval }
enum class TrainingRepresentationUi { Listening, Staff, Fretboard, Tab }
enum class IntervalPresentationUi { Ascending, Descending, Harmonic }
enum class TrainingIntervalUi {
    Unison, MinorSecond, MajorSecond, MinorThird, MajorThird, PerfectFourth, Tritone,
    PerfectFifth, MinorSixth, MajorSixth, MinorSeventh, MajorSeventh, Octave,
}
enum class TrainingNoteUi { C, CSharp, D, EFlat, E, F, FSharp, G, AFlat, A, BFlat, B }
enum class TrainingAccidentalUi { Natural, Sharp, Flat }
enum class TrainingSoundUi { Question, Comparison }
enum class TrainingNoticeUi { EmptyIntervalPool, SettingsReadFailed, SettingsWriteFailed, MetronomeStopFailed,
    PlaybackFailed, ShutdownFailed, FocusDenied, OutputInterrupted }

data class TrainingSettingsUi(
    val subject: TrainingSubjectUi = TrainingSubjectUi.Note,
    val representation: TrainingRepresentationUi = TrainingRepresentationUi.Listening,
    val intervalPresentation: IntervalPresentationUi = IntervalPresentationUi.Ascending,
    val intervals: Set<TrainingIntervalUi> = TrainingIntervalUi.entries.toSet(),
)
data class TrainingQuestionKeyUi(val sessionId: Long, val questionIndex: Int)
data class TrainingPositionUi(val stringNumber: Int, val fret: Int)
data class TrainingStaffNoteUi(val step: Int, val accidental: TrainingAccidentalUi)
sealed interface TrainingAnswerUi {
    data class Note(val note: TrainingNoteUi) : TrainingAnswerUi
    data class Interval(val interval: TrainingIntervalUi) : TrainingAnswerUi
}
data class TrainingFeedbackUi(val chosen: TrainingAnswerUi, val answer: TrainingAnswerUi, val correct: Boolean)
data class TrainingResultUi(val number: Int, val feedback: TrainingFeedbackUi)
sealed interface TrainingStageUi {
    data object Menu : TrainingStageUi
    data object Setup : TrainingStageUi
    data class Question(val key: TrainingQuestionKeyUi, val number: Int, val settings: TrainingSettingsUi,
        val positions: List<TrainingPositionUi>, val staff: List<TrainingStaffNoteUi>,
        val choices: List<TrainingAnswerUi>, val feedback: TrainingFeedbackUi?) : TrainingStageUi
    data class Results(val correctCount: Int, val rows: List<TrainingResultUi>) : TrainingStageUi
}
sealed interface TrainingAudioUi {
    data object Idle : TrainingAudioUi
    data class Preparing(val sound: TrainingSoundUi) : TrainingAudioUi
    data class Playing(val sound: TrainingSoundUi) : TrainingAudioUi
    data class Failed(val sound: TrainingSoundUi, val notice: TrainingNoticeUi) : TrainingAudioUi
}
data class TrainingUiState(
    val settings: TrainingSettingsUi = TrainingSettingsUi(),
    val stage: TrainingStageUi = TrainingStageUi.Menu,
    val audio: TrainingAudioUi = TrainingAudioUi.Idle,
    val settingsSaving: Boolean = false,
    val settingsNotice: TrainingNoticeUi? = null,
    val notice: TrainingNoticeUi? = null,
)
sealed interface TrainingEvent {
    data class OpenExercise(val settings: TrainingSettingsUi) : TrainingEvent
    data class SetSettings(val settings: TrainingSettingsUi) : TrainingEvent
    data object RetrySettings : TrainingEvent
    data object Start : TrainingEvent
    data class Answer(val key: TrainingQuestionKeyUi, val answer: TrainingAnswerUi) : TrainingEvent
    data class Next(val key: TrainingQuestionKeyUi) : TrainingEvent
    data class Replay(val key: TrainingQuestionKeyUi, val sound: TrainingSoundUi) : TrainingEvent
    data object Exit : TrainingEvent
}
