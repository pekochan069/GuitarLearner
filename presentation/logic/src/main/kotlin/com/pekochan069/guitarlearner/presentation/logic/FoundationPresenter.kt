package com.pekochan069.guitarlearner.presentation.logic

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.pekochan069.guitarlearner.domain.AppearanceChange
import com.pekochan069.guitarlearner.domain.AppearanceFailure
import com.pekochan069.guitarlearner.domain.AppearanceSettings
import com.pekochan069.guitarlearner.domain.LanguagePreference
import com.pekochan069.guitarlearner.domain.ThemePreference
import com.pekochan069.guitarlearner.presentation.contract.AppearanceNotice
import com.pekochan069.guitarlearner.presentation.contract.FoundationEvent
import com.pekochan069.guitarlearner.presentation.contract.FoundationScreen
import com.pekochan069.guitarlearner.presentation.contract.FoundationState
import com.pekochan069.guitarlearner.presentation.contract.LanguageOption
import com.pekochan069.guitarlearner.presentation.contract.Page
import com.pekochan069.guitarlearner.presentation.contract.Reading
import com.pekochan069.guitarlearner.presentation.contract.SettingsStatus
import com.pekochan069.guitarlearner.presentation.contract.ThemeOption
import com.slack.circuit.runtime.CircuitContext
import com.slack.circuit.runtime.Navigator
import com.slack.circuit.runtime.presenter.Presenter
import com.slack.circuit.runtime.screen.Screen
import kotlinx.coroutines.launch

class FoundationPresenter(private val appearance: AppearanceSettings) : Presenter<FoundationState> {
    @Composable
    override fun present(): FoundationState {
        var pageName by rememberSaveable { mutableStateOf(Page.Tuner.name) }
        var readingName by rememberSaveable { mutableStateOf(Reading.NoSignal.name) }
        var bpm by rememberSaveable { mutableIntStateOf(90) }
        var running by rememberSaveable { mutableStateOf(false) }
        var gallerySelected by rememberSaveable { mutableStateOf(true) }
        var settingsOpen by rememberSaveable { mutableStateOf(false) }
        var settingsStatus by remember { mutableStateOf<SettingsStatus>(SettingsStatus.Idle) }
        val snapshot by appearance.current.collectAsState()
        val scope = rememberCoroutineScope()

        fun select(change: AppearanceChange) {
            if (settingsStatus == SettingsStatus.Saving) return
            settingsStatus = SettingsStatus.Saving
            scope.launch {
                try {
                    appearance.select(change).fold(
                        ifLeft = { settingsStatus = SettingsStatus.Failed(it.toNotice()) },
                        ifRight = { settingsStatus = SettingsStatus.Idle },
                    )
                } finally {
                    if (settingsStatus == SettingsStatus.Saving) settingsStatus = SettingsStatus.Idle
                }
            }
        }

        return FoundationState(
            page = Page.entries.firstOrNull { it.name == pageName } ?: Page.Tuner,
            reading = Reading.entries.firstOrNull { it.name == readingName } ?: Reading.NoSignal,
            bpm = bpm,
            running = running,
            gallerySelected = gallerySelected,
            settingsOpen = settingsOpen,
            theme = snapshot.theme.toOption(),
            language = snapshot.language?.toOption(),
            settingsStatus = settingsStatus,
            eventSink = { event ->
                when (event) {
                    is FoundationEvent.SelectPage -> pageName = event.value.name
                    is FoundationEvent.SetReading -> readingName = event.value.name
                    is FoundationEvent.SetBpm -> bpm = event.value.coerceIn(40, 240)
                    is FoundationEvent.SetRunning -> running = event.value
                    is FoundationEvent.SetGallerySelected -> gallerySelected = event.value
                    is FoundationEvent.SetSettingsOpen -> settingsOpen = event.value
                    is FoundationEvent.SelectTheme -> select(AppearanceChange.Theme(event.value.toPreference()))
                    is FoundationEvent.SelectLanguage -> select(AppearanceChange.Language(event.value.toPreference()))
                    FoundationEvent.DismissNotice -> if (settingsStatus is SettingsStatus.Failed) settingsStatus = SettingsStatus.Idle
                }
            },
        )
    }

    class Factory(private val appearance: AppearanceSettings) : Presenter.Factory {
        override fun create(screen: Screen, navigator: Navigator, context: CircuitContext): Presenter<*>? =
            if (screen == FoundationScreen) FoundationPresenter(appearance) else null
    }
}

private fun ThemePreference.toOption(): ThemeOption = when (this) {
    ThemePreference.System -> ThemeOption.System
    ThemePreference.Light -> ThemeOption.Light
    ThemePreference.Dark -> ThemeOption.Dark
}

private fun ThemeOption.toPreference(): ThemePreference = when (this) {
    ThemeOption.System -> ThemePreference.System
    ThemeOption.Light -> ThemePreference.Light
    ThemeOption.Dark -> ThemePreference.Dark
}

private fun LanguagePreference.toOption(): LanguageOption = when (this) {
    LanguagePreference.System -> LanguageOption.System
    LanguagePreference.Korean -> LanguageOption.Korean
    LanguagePreference.English -> LanguageOption.English
}

private fun LanguageOption.toPreference(): LanguagePreference = when (this) {
    LanguageOption.System -> LanguagePreference.System
    LanguageOption.Korean -> LanguagePreference.Korean
    LanguageOption.English -> LanguagePreference.English
}

private fun AppearanceFailure.toNotice(): AppearanceNotice = when (this) {
    AppearanceFailure.ReadFailed -> AppearanceNotice.ReadFailed
    AppearanceFailure.WriteFailed -> AppearanceNotice.SaveFailed
    AppearanceFailure.ThemeApplyFailed, AppearanceFailure.LanguageApplyFailed -> AppearanceNotice.ApplyFailed
}
