package com.pekochan069.guitarlearner.domain

import kotlin.math.pow
import kotlinx.coroutines.flow.StateFlow

enum class StandardString(val number: Int, val midi: Int, val note: String, val octave: Int) {
    E2(6, 40, "E", 2), A2(5, 45, "A", 2), D3(4, 50, "D", 3),
    G3(3, 55, "G", 3), B3(2, 59, "B", 3), E4(1, 64, "E", 4);

    val frequencyHz: Double = 440.0 * 2.0.pow((midi - 69) / 12.0)
}

sealed interface TunerTarget {
    data object Automatic : TunerTarget
    data class Manual(val string: StandardString) : TunerTarget
}

enum class TuningTolerance(val cents: Int) { Strict(3), Normal(5), Relaxed(10) }
enum class TunerSettingsPage { AppPermission, MicrophonePrivacy }
enum class TunerRecovery { RetryStart, AppSettings, PrivacySettings }

sealed interface TunerRequest {
    data object Start : TunerRequest
    data object Stop : TunerRequest
    data class SelectTarget(val target: TunerTarget) : TunerRequest
    data class SelectTolerance(val tolerance: TuningTolerance) : TunerRequest
    data object ReloadTolerance : TunerRequest
    data class OpenSettings(val page: TunerSettingsPage) : TunerRequest
}

sealed interface TunerFailure {
    data class PermissionDenied(val recovery: TunerRecovery) : TunerFailure
    data object PermissionRevoked : TunerFailure
    data object MicrophoneMuted : TunerFailure
    data object ClientSilenced : TunerFailure
    data object InputUnavailable : TunerFailure
    data object InputReadFailed : TunerFailure
    data object NoInput : TunerFailure
    data object ShutdownFailed : TunerFailure
    data object SettingsUnavailable : TunerFailure
    data class MetronomeStopFailed(val cause: MetronomeFailure) : TunerFailure
    data object ToleranceReadFailed : TunerFailure
    data object ToleranceWriteFailed : TunerFailure
}

sealed interface TunerListening {
    data object Stopped : TunerListening
    data object Starting : TunerListening
    data object Stopping : TunerListening
    data class Listening(val feedback: TuningFeedback) : TunerListening
    data class Failed(val failure: TunerFailure) : TunerListening
}

sealed interface ToleranceStorageStatus {
    data object Ready : ToleranceStorageStatus
    data class Saving(val requested: TuningTolerance) : ToleranceStorageStatus
    data class Failed(val failure: TunerFailure) : ToleranceStorageStatus
}

data class TunerSnapshot(
    val target: TunerTarget = TunerTarget.Automatic,
    val tolerance: TuningTolerance = TuningTolerance.Normal,
    val listening: TunerListening = TunerListening.Stopped,
    val storage: ToleranceStorageStatus = ToleranceStorageStatus.Ready,
)

interface Tuner {
    val current: StateFlow<TunerSnapshot>

    // Start admits authority and Stop revokes it before returning to the main-thread caller.
    fun submit(request: TunerRequest)
}
