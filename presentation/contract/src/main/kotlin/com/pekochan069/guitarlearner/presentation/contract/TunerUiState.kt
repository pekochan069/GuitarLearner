package com.pekochan069.guitarlearner.presentation.contract

enum class GuitarStringUi(val number: Int, val note: String, val octave: Int) {
    E2(6, "E", 2), A2(5, "A", 2), D3(4, "D", 3), G3(3, "G", 3), B3(2, "B", 3), E4(1, "E", 4),
}

enum class HeadstockLayoutUi { ThreePlusThree, InlineSix }

sealed interface TunerTargetUi {
    data object Automatic : TunerTargetUi
    data class Manual(val string: GuitarStringUi) : TunerTargetUi
}

enum class ToleranceUi(val cents: Int) { Strict(3), Normal(5), Relaxed(10) }
enum class TunerNoticeUi { PermissionDenied, PermissionRevoked, MicBlocked, InputFailed, NoInput, ShutdownFailed, MetronomeStopFailed, SettingsFailed, ReadFailed, SaveFailed }
enum class TunerActionUi { Retry, AppSettings, PrivacySettings }
enum class TunerJudgmentUi { Low, High, Settling, InTune }

sealed interface TunerFeedbackUi {
    data object PluckOneString : TunerFeedbackUi
    data object Uncertain : TunerFeedbackUi
    data object WrongOctave : TunerFeedbackUi
    data class Measured(val string: GuitarStringUi, val cents: Double, val judgment: TunerJudgmentUi) : TunerFeedbackUi
}

sealed interface TunerListeningUi {
    data object Stopped : TunerListeningUi
    data object Starting : TunerListeningUi
    data object Stopping : TunerListeningUi
    data class Listening(val feedback: TunerFeedbackUi) : TunerListeningUi
    data class Failed(val notice: TunerNoticeUi, val actions: Set<TunerActionUi>) : TunerListeningUi
}

data class TunerUiState(
    val target: TunerTargetUi = TunerTargetUi.Automatic,
    val tolerance: ToleranceUi = ToleranceUi.Normal,
    val listening: TunerListeningUi = TunerListeningUi.Stopped,
    val savingTolerance: Boolean = false,
    val preferenceNotice: TunerNoticeUi? = null,
    val headstockLayout: HeadstockLayoutUi = HeadstockLayoutUi.ThreePlusThree,
)
