package com.pekochan069.guitarlearner.presentation.contract

enum class BeatUnitUi(val denominator: Int) {
    Half(2), Quarter(4), Eighth(8), Sixteenth(16),
}

enum class BeatAccentUi { Accent, Normal, Mute }

data class MetronomeConfigUi(
    val bpm: Int,
    val denominator: BeatUnitUi,
    val beats: List<BeatAccentUi>,
) {
    val numerator: Int get() = beats.size
}

enum class MetronomeStopUi { User, FocusLoss, OutputDisconnected, ServiceEnded }
enum class MetronomeNotice {
    InvalidConfiguration, InvalidName, PresetExists, PresetMissing,
    ReadFailed, SaveFailed, FocusDenied, ServiceUnavailable, AudioUnavailable,
}

sealed interface MetronomePlaybackUi {
    data class Stopped(val reason: MetronomeStopUi?) : MetronomePlaybackUi
    data object Preparing : MetronomePlaybackUi
    data class Playing(val config: MetronomeConfigUi, val beatIndex: Int) : MetronomePlaybackUi
    data class Failed(val notice: MetronomeNotice) : MetronomePlaybackUi
}

data class MetronomePresetUi(val name: String, val config: MetronomeConfigUi)

data class MetronomeUiState(
    val config: MetronomeConfigUi,
    val playback: MetronomePlaybackUi,
    val pendingChange: Boolean,
    val presets: List<MetronomePresetUi>,
    val presetName: String,
    val overwriteName: String?,
    val savingPreset: Boolean,
    val notice: MetronomeNotice?,
)
