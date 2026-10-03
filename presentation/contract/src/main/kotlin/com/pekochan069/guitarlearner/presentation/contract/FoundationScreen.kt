package com.pekochan069.guitarlearner.presentation.contract

import com.slack.circuit.runtime.CircuitUiEvent
import com.slack.circuit.runtime.CircuitUiState
import com.slack.circuit.runtime.screen.Screen
import kotlinx.parcelize.Parcelize

@Parcelize
data object FoundationScreen : Screen

enum class Page { Tuner, Metronome, Gallery }
enum class Reading { NoSignal, Flat, InTune, Sharp }
enum class ThemeOption { System, Light, Dark }
enum class LanguageOption { System, Korean, English }
enum class AppearanceNotice { ReadFailed, SaveFailed, ApplyFailed }

sealed interface SettingsStatus {
    data object Idle : SettingsStatus
    data object Saving : SettingsStatus
    data class Failed(val notice: AppearanceNotice) : SettingsStatus
}

data class FoundationState(
    val page: Page,
    val reading: Reading,
    val bpm: Int,
    val running: Boolean,
    val gallerySelected: Boolean,
    val settingsOpen: Boolean,
    val theme: ThemeOption,
    val language: LanguageOption?,
    val settingsStatus: SettingsStatus,
    val eventSink: (FoundationEvent) -> Unit,
) : CircuitUiState

sealed interface FoundationEvent : CircuitUiEvent {
    data class SelectPage(val value: Page) : FoundationEvent
    data class SetReading(val value: Reading) : FoundationEvent
    data class SetBpm(val value: Int) : FoundationEvent
    data class SetRunning(val value: Boolean) : FoundationEvent
    data class SetGallerySelected(val value: Boolean) : FoundationEvent
    data class SetSettingsOpen(val value: Boolean) : FoundationEvent
    data class SelectTheme(val value: ThemeOption) : FoundationEvent
    data class SelectLanguage(val value: LanguageOption) : FoundationEvent
    data object DismissNotice : FoundationEvent
}
