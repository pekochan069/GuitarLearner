package com.pekochan069.guitarlearner.presentation.logic

import com.pekochan069.guitarlearner.domain.Ambiguity
import com.pekochan069.guitarlearner.domain.StandardString
import com.pekochan069.guitarlearner.domain.ToleranceStorageStatus
import com.pekochan069.guitarlearner.domain.TunerFailure
import com.pekochan069.guitarlearner.domain.TunerListening
import com.pekochan069.guitarlearner.domain.TunerRecovery
import com.pekochan069.guitarlearner.domain.TunerSnapshot
import com.pekochan069.guitarlearner.domain.TunerTarget
import com.pekochan069.guitarlearner.domain.TuningFeedback
import com.pekochan069.guitarlearner.domain.TuningJudgment
import com.pekochan069.guitarlearner.domain.TuningTolerance
import com.pekochan069.guitarlearner.presentation.contract.GuitarStringUi
import com.pekochan069.guitarlearner.presentation.contract.ToleranceUi
import com.pekochan069.guitarlearner.presentation.contract.TunerActionUi
import com.pekochan069.guitarlearner.presentation.contract.TunerFeedbackUi
import com.pekochan069.guitarlearner.presentation.contract.TunerJudgmentUi
import com.pekochan069.guitarlearner.presentation.contract.TunerListeningUi
import com.pekochan069.guitarlearner.presentation.contract.TunerNoticeUi
import com.pekochan069.guitarlearner.presentation.contract.TunerTargetUi
import com.pekochan069.guitarlearner.presentation.contract.TunerUiState

internal fun TunerSnapshot.toUi(): TunerUiState = TunerUiState(
    target = when (val selected = target) {
        TunerTarget.Automatic -> TunerTargetUi.Automatic
        is TunerTarget.Manual -> TunerTargetUi.Manual(selected.string.toUi())
    },
    tolerance = when (tolerance) {
        TuningTolerance.Strict -> ToleranceUi.Strict
        TuningTolerance.Normal -> ToleranceUi.Normal
        TuningTolerance.Relaxed -> ToleranceUi.Relaxed
    },
    listening = when (val state = listening) {
        TunerListening.Stopped -> TunerListeningUi.Stopped
        TunerListening.Starting -> TunerListeningUi.Starting
        TunerListening.Stopping -> TunerListeningUi.Stopping
        is TunerListening.Listening -> TunerListeningUi.Listening(state.feedback.toUi())
        is TunerListening.Failed -> TunerListeningUi.Failed(state.failure.toNotice(), state.failure.actions())
    },
    savingTolerance = storage is ToleranceStorageStatus.Saving,
    preferenceNotice = (storage as? ToleranceStorageStatus.Failed)?.failure?.toNotice(),
)

private fun TuningFeedback.toUi(): TunerFeedbackUi = when (this) {
    TuningFeedback.PluckOneString -> TunerFeedbackUi.PluckOneString
    is TuningFeedback.Uncertain -> if (reason == Ambiguity.WrongOctave) TunerFeedbackUi.WrongOctave else TunerFeedbackUi.Uncertain
    is TuningFeedback.Measured -> TunerFeedbackUi.Measured(string.toUi(), cents, when (judgment) {
        TuningJudgment.Low -> TunerJudgmentUi.Low
        TuningJudgment.High -> TunerJudgmentUi.High
        TuningJudgment.Settling -> TunerJudgmentUi.Settling
        TuningJudgment.InTune -> TunerJudgmentUi.InTune
    })
}

private fun StandardString.toUi(): GuitarStringUi = when (this) {
    StandardString.E2 -> GuitarStringUi.E2
    StandardString.A2 -> GuitarStringUi.A2
    StandardString.D3 -> GuitarStringUi.D3
    StandardString.G3 -> GuitarStringUi.G3
    StandardString.B3 -> GuitarStringUi.B3
    StandardString.E4 -> GuitarStringUi.E4
}
internal fun TunerTargetUi.toDomain(): TunerTarget = when (this) {
    TunerTargetUi.Automatic -> TunerTarget.Automatic
    is TunerTargetUi.Manual -> TunerTarget.Manual(when (string) {
        GuitarStringUi.E2 -> StandardString.E2
        GuitarStringUi.A2 -> StandardString.A2
        GuitarStringUi.D3 -> StandardString.D3
        GuitarStringUi.G3 -> StandardString.G3
        GuitarStringUi.B3 -> StandardString.B3
        GuitarStringUi.E4 -> StandardString.E4
    })
}
internal fun ToleranceUi.toDomain(): TuningTolerance = when (this) {
    ToleranceUi.Strict -> TuningTolerance.Strict
    ToleranceUi.Normal -> TuningTolerance.Normal
    ToleranceUi.Relaxed -> TuningTolerance.Relaxed
}

private fun TunerFailure.toNotice(): TunerNoticeUi = when (this) {
    is TunerFailure.PermissionDenied -> TunerNoticeUi.PermissionDenied
    TunerFailure.PermissionRevoked -> TunerNoticeUi.PermissionRevoked
    TunerFailure.MicrophoneMuted, TunerFailure.ClientSilenced -> TunerNoticeUi.MicBlocked
    TunerFailure.InputUnavailable, TunerFailure.InputReadFailed -> TunerNoticeUi.InputFailed
    TunerFailure.NoInput -> TunerNoticeUi.NoInput
    TunerFailure.ShutdownFailed -> TunerNoticeUi.ShutdownFailed
    TunerFailure.SettingsUnavailable -> TunerNoticeUi.SettingsFailed
    is TunerFailure.MetronomeStopFailed -> TunerNoticeUi.MetronomeStopFailed
    is TunerFailure.ProgressionStopFailed -> TunerNoticeUi.ProgressionStopFailed
    TunerFailure.ToleranceReadFailed -> TunerNoticeUi.ReadFailed
    TunerFailure.ToleranceWriteFailed -> TunerNoticeUi.SaveFailed
}

private fun TunerFailure.actions(): Set<TunerActionUi> = when (this) {
    is TunerFailure.PermissionDenied -> when (recovery) {
        TunerRecovery.RetryStart -> setOf(TunerActionUi.Retry, TunerActionUi.AppSettings)
        TunerRecovery.AppSettings -> setOf(TunerActionUi.AppSettings, TunerActionUi.Retry)
        TunerRecovery.PrivacySettings -> setOf(TunerActionUi.PrivacySettings, TunerActionUi.Retry)
    }
    TunerFailure.PermissionRevoked -> setOf(TunerActionUi.AppSettings, TunerActionUi.Retry)
    TunerFailure.MicrophoneMuted, TunerFailure.ClientSilenced -> setOf(TunerActionUi.PrivacySettings, TunerActionUi.Retry)
    TunerFailure.ShutdownFailed -> setOf(TunerActionUi.AppSettings)
    else -> setOf(TunerActionUi.Retry)
}
