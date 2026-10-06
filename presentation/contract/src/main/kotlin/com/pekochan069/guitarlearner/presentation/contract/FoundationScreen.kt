package com.pekochan069.guitarlearner.presentation.contract

import com.slack.circuit.runtime.CircuitUiEvent
import com.slack.circuit.runtime.CircuitUiState
import com.slack.circuit.runtime.screen.Screen
import kotlinx.parcelize.Parcelize

@Parcelize
data object FoundationScreen : Screen

enum class FeatureCategory { Tools, Training, Learning }
enum class FeatureId(val savedId: String) { Metronome("metronome"), Tuner("tuner"), Chords("chords"), Progressions("progressions"), Training("training"), Learning("learning") }
enum class DevelopmentSample(val savedId: String) { Gallery("gallery") }

sealed interface FoundationDestination {
    data object Home : FoundationDestination
    data class Feature(val id: FeatureId) : FoundationDestination
    data class Sample(val id: DevelopmentSample) : FoundationDestination
}

data class FeatureGroup(val category: FeatureCategory, val features: List<FeatureId>)
enum class ThemeOption { System, Light, Dark }
enum class LanguageOption { System, Korean, English }
enum class AppearanceNotice { ReadFailed, SaveFailed, ApplyFailed }

sealed interface SettingsStatus {
    data object Idle : SettingsStatus
    data object Saving : SettingsStatus
    data class Failed(val notice: AppearanceNotice) : SettingsStatus
}

data class FoundationState(
    val destination: FoundationDestination,
    val featureGroups: List<FeatureGroup>,
    val developmentSamples: List<DevelopmentSample>,
    val canNavigateBack: Boolean,
    val tuner: TunerUiState,
    val metronome: MetronomeUiState,
    val chords: ChordUiState,
    val progressions: ProgressionUiState,
    val training: TrainingUiState,
    val learning: LearningUiState,
    val returningToLesson: Boolean,
    val gallerySelected: Boolean,
    val settingsOpen: Boolean,
    val theme: ThemeOption,
    val language: LanguageOption?,
    val settingsStatus: SettingsStatus,
    val eventSink: (FoundationEvent) -> Unit,
) : CircuitUiState {
    val bpm: Int get() = metronome.config.bpm
    val running: Boolean get() = metronome.playback is MetronomePlaybackUi.Playing
}

sealed interface FoundationEvent : CircuitUiEvent {
    data class Learning(val value: LearningEvent) : FoundationEvent
    data class Progression(val value: ProgressionEvent) : FoundationEvent
    data class Training(val value: TrainingEvent) : FoundationEvent
    data class Chord(val value: ChordEvent) : FoundationEvent
    data class OpenFeature(val id: FeatureId) : FoundationEvent
    data class OpenSample(val id: DevelopmentSample) : FoundationEvent
    data object NavigateBack : FoundationEvent
    data object StartTuner : FoundationEvent
    data object StopTuner : FoundationEvent
    data class SelectHeadstockLayout(val value: HeadstockLayoutUi) : FoundationEvent
    data class SelectTunerTarget(val value: TunerTargetUi) : FoundationEvent
    data class SelectTunerTolerance(val value: ToleranceUi) : FoundationEvent
    data object ReloadTunerTolerance : FoundationEvent
    data class OpenTunerSettings(val action: TunerActionUi) : FoundationEvent
    data class SetBpm(val value: Int) : FoundationEvent
    data class AdjustBpm(val delta: Int) : FoundationEvent
    data class SetRunning(val value: Boolean) : FoundationEvent
    data class SetBeatCount(val value: Int) : FoundationEvent
    data class AdjustBeatCount(val delta: Int) : FoundationEvent
    data class SetBeatUnit(val value: BeatUnitUi) : FoundationEvent
    data class SetBeatAccent(val index: Int, val value: BeatAccentUi) : FoundationEvent
    data class CycleBeatAccent(val index: Int) : FoundationEvent
    data class SetPresetsOpen(val value: Boolean) : FoundationEvent
    data class SetPresetName(val value: String) : FoundationEvent
    data object SavePreset : FoundationEvent
    data object ConfirmPresetOverwrite : FoundationEvent
    data object CancelPresetOverwrite : FoundationEvent
    data class LoadPreset(val name: String) : FoundationEvent
    data class DeletePreset(val name: String) : FoundationEvent
    data object DismissMetronomeNotice : FoundationEvent
    data class SetGallerySelected(val value: Boolean) : FoundationEvent
    data class SetSettingsOpen(val value: Boolean) : FoundationEvent
    data class SelectTheme(val value: ThemeOption) : FoundationEvent
    data class SelectLanguage(val value: LanguageOption) : FoundationEvent
    data object DismissNotice : FoundationEvent
}
