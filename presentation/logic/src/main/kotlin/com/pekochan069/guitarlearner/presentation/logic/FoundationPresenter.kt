package com.pekochan069.guitarlearner.presentation.logic

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import arrow.core.raise.either
import arrow.core.raise.ensure
import com.pekochan069.guitarlearner.domain.AppearanceChange
import com.pekochan069.guitarlearner.domain.AppearanceFailure
import com.pekochan069.guitarlearner.domain.AppearanceSettings
import com.pekochan069.guitarlearner.domain.LanguagePreference
import com.pekochan069.guitarlearner.domain.BeatAccent
import com.pekochan069.guitarlearner.domain.BeatUnit
import com.pekochan069.guitarlearner.domain.Metronome
import com.pekochan069.guitarlearner.domain.MetronomeCommand
import com.pekochan069.guitarlearner.domain.MetronomeConfig
import com.pekochan069.guitarlearner.domain.MetronomeFailure
import com.pekochan069.guitarlearner.domain.PlaybackState
import com.pekochan069.guitarlearner.domain.StopReason
import com.pekochan069.guitarlearner.domain.ThemePreference
import com.pekochan069.guitarlearner.presentation.contract.AppearanceNotice
import com.pekochan069.guitarlearner.presentation.contract.BeatAccentUi
import com.pekochan069.guitarlearner.presentation.contract.BeatUnitUi
import com.pekochan069.guitarlearner.presentation.contract.FoundationEvent
import com.pekochan069.guitarlearner.presentation.contract.FoundationScreen
import com.pekochan069.guitarlearner.presentation.contract.FoundationState
import com.pekochan069.guitarlearner.presentation.contract.LanguageOption
import com.pekochan069.guitarlearner.presentation.contract.MetronomeConfigUi
import com.pekochan069.guitarlearner.presentation.contract.MetronomeNotice
import com.pekochan069.guitarlearner.presentation.contract.MetronomePlaybackUi
import com.pekochan069.guitarlearner.presentation.contract.MetronomePresetUi
import com.pekochan069.guitarlearner.presentation.contract.MetronomeStopUi
import com.pekochan069.guitarlearner.presentation.contract.MetronomeUiState
import com.pekochan069.guitarlearner.presentation.contract.Page
import com.pekochan069.guitarlearner.presentation.contract.Reading
import com.pekochan069.guitarlearner.presentation.contract.SettingsStatus
import com.pekochan069.guitarlearner.presentation.contract.ThemeOption
import com.slack.circuit.runtime.CircuitContext
import com.slack.circuit.runtime.Navigator
import com.slack.circuit.runtime.presenter.Presenter
import com.slack.circuit.runtime.screen.Screen
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class FoundationPresenter(
    private val appearance: AppearanceSettings,
    private val metronome: Metronome,
) : Presenter<FoundationState> {
    @Composable
    override fun present(): FoundationState {
        var pageName by rememberSaveable { mutableStateOf(Page.Tuner.name) }
        var readingName by rememberSaveable { mutableStateOf(Reading.NoSignal.name) }
        var gallerySelected by rememberSaveable { mutableStateOf(true) }
        var settingsOpen by rememberSaveable { mutableStateOf(false) }
        var settingsStatus by remember { mutableStateOf<SettingsStatus>(SettingsStatus.Idle) }
        val snapshot by appearance.current.collectAsState()
        val metronomeSnapshot by metronome.current.collectAsState()
        var presetName by rememberSaveable { mutableStateOf("") }
        var overwriteName by rememberSaveable { mutableStateOf<String?>(null) }
        var savingPreset by remember { mutableStateOf(false) }
        var metronomeNotice by remember { mutableStateOf<MetronomeNotice?>(null) }
        var dismissedReadFailure by remember { mutableStateOf<MetronomeFailure?>(null) }
        val commands = remember { Mutex() }
        val scope = rememberCoroutineScope()

        fun execute(
            presetOperation: Boolean = false,
            command: () -> MetronomeCommand,
        ) {
            if (presetOperation && savingPreset) return
            if (presetOperation) savingPreset = true
            scope.launch {
                try {
                    commands.withLock {
                        val request = command()
                        metronome.execute(request).fold(
                            ifLeft = {
                                if (it == MetronomeFailure.PresetExists && request is MetronomeCommand.SavePreset && !request.overwrite) {
                                    overwriteName = request.name.trim()
                                } else {
                                    metronomeNotice = it.toNotice()
                                }
                            },
                            ifRight = {
                                metronomeNotice = null
                                if (request is MetronomeCommand.SavePreset) overwriteName = null
                            },
                        )
                    }
                } finally {
                    if (presetOperation) savingPreset = false
                }
            }
        }

        fun setPattern(transform: (MetronomeConfig) -> MetronomeConfig) {
            execute {
                val config = transform(metronome.current.value.selected)
                MetronomeCommand.SetPattern(config.denominator, config.beats)
            }
        }

        fun setAccent(index: Int, transform: (BeatAccent) -> BeatAccent) {
            scope.launch {
                commands.withLock {
                    either<MetronomeFailure, Unit> {
                        val config = metronome.current.value.selected
                        ensure(index in config.beats.indices) { MetronomeFailure.InvalidConfiguration }
                        val beats = config.beats.mapIndexed { position, accent ->
                            if (position == index) transform(accent) else accent
                        }
                        metronome.execute(MetronomeCommand.SetPattern(config.denominator, beats)).bind()
                    }.fold(
                        ifLeft = { metronomeNotice = it.toNotice() },
                        ifRight = { metronomeNotice = null },
                    )
                }
            }
        }

        fun setPlaybackImmediately(value: Boolean) {
            scope.launch {
                metronome.execute(if (value) MetronomeCommand.Start else MetronomeCommand.Stop).fold(
                    ifLeft = { metronomeNotice = it.toNotice() },
                    ifRight = { metronomeNotice = null },
                )
            }
        }

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
            metronome = MetronomeUiState(
                config = metronomeSnapshot.selected.toUi(),
                playback = metronomeSnapshot.playback.toUi(),
                pendingChange = (metronomeSnapshot.playback as? PlaybackState.Playing)?.config?.let {
                    it != metronomeSnapshot.selected
                } ?: false,
                presets = metronomeSnapshot.presets.map { MetronomePresetUi(it.name, it.config.toUi()) },
                presetName = presetName,
                overwriteName = overwriteName,
                savingPreset = savingPreset,
                notice = metronomeNotice ?: metronomeSnapshot.readFailure
                    ?.takeUnless { it == dismissedReadFailure }?.toNotice(),
            ),
            gallerySelected = gallerySelected,
            settingsOpen = settingsOpen,
            theme = snapshot.theme.toOption(),
            language = snapshot.language?.toOption(),
            settingsStatus = settingsStatus,
            eventSink = { event ->
                when (event) {
                    is FoundationEvent.SelectPage -> pageName = event.value.name
                    is FoundationEvent.SetReading -> readingName = event.value.name
                    is FoundationEvent.SetBpm -> execute { MetronomeCommand.SetTempo(event.value.coerceIn(40, 240)) }
                    is FoundationEvent.AdjustBpm -> execute {
                        MetronomeCommand.SetTempo((metronome.current.value.selected.bpm + event.delta).coerceIn(40, 240))
                    }
                    is FoundationEvent.SetRunning -> setPlaybackImmediately(event.value)
                    is FoundationEvent.SetBeatCount -> setPattern { it.withBeatCount(event.value) }
                    is FoundationEvent.AdjustBeatCount -> setPattern { it.withBeatCount(it.numerator + event.delta) }
                    is FoundationEvent.SetBeatUnit -> setPattern { it.copy(denominator = event.value.toDomain()) }
                    is FoundationEvent.SetBeatAccent -> setAccent(event.index) { event.value.toDomain() }
                    is FoundationEvent.CycleBeatAccent -> setAccent(event.index) {
                        BeatAccent.entries[(it.ordinal + 1) % BeatAccent.entries.size]
                    }
                    is FoundationEvent.SetPresetName -> presetName = event.value
                    FoundationEvent.SavePreset -> {
                        val name = presetName.trim()
                        if (!savingPreset && metronome.current.value.presets.any { it.name == name }) {
                            overwriteName = name
                            metronomeNotice = null
                        } else {
                            execute(presetOperation = true) { MetronomeCommand.SavePreset(name) }
                        }
                    }
                    FoundationEvent.ConfirmPresetOverwrite -> overwriteName?.let { name ->
                        execute(presetOperation = true) { MetronomeCommand.SavePreset(name, overwrite = true) }
                    }
                    FoundationEvent.CancelPresetOverwrite -> if (!savingPreset) overwriteName = null
                    is FoundationEvent.LoadPreset -> execute(presetOperation = true) { MetronomeCommand.LoadPreset(event.name) }
                    is FoundationEvent.DeletePreset -> execute(presetOperation = true) { MetronomeCommand.DeletePreset(event.name) }
                    FoundationEvent.DismissMetronomeNotice -> {
                        metronomeNotice = null
                        dismissedReadFailure = metronomeSnapshot.readFailure
                    }
                    is FoundationEvent.SetGallerySelected -> gallerySelected = event.value
                    is FoundationEvent.SetSettingsOpen -> settingsOpen = event.value
                    is FoundationEvent.SelectTheme -> select(AppearanceChange.Theme(event.value.toPreference()))
                    is FoundationEvent.SelectLanguage -> select(AppearanceChange.Language(event.value.toPreference()))
                    FoundationEvent.DismissNotice -> if (settingsStatus is SettingsStatus.Failed) settingsStatus = SettingsStatus.Idle
                }
            },
        )
    }

    class Factory(private val appearance: AppearanceSettings, private val metronome: Metronome) : Presenter.Factory {
        override fun create(screen: Screen, navigator: Navigator, context: CircuitContext): Presenter<*>? =
            if (screen == FoundationScreen) FoundationPresenter(appearance, metronome) else null
    }
}

private fun MetronomeConfig.withBeatCount(value: Int): MetronomeConfig = copy(
    beats = List(value.coerceIn(1, 16)) { index -> beats.getOrElse(index) { BeatAccent.Normal } },
)

private fun MetronomeConfig.toUi(): MetronomeConfigUi = MetronomeConfigUi(
    bpm = bpm,
    denominator = when (denominator) {
        BeatUnit.Half -> BeatUnitUi.Half
        BeatUnit.Quarter -> BeatUnitUi.Quarter
        BeatUnit.Eighth -> BeatUnitUi.Eighth
        BeatUnit.Sixteenth -> BeatUnitUi.Sixteenth
    },
    beats = beats.map { when (it) {
        BeatAccent.Accent -> BeatAccentUi.Accent
        BeatAccent.Normal -> BeatAccentUi.Normal
        BeatAccent.Mute -> BeatAccentUi.Mute
    } },
)

private fun BeatUnitUi.toDomain(): BeatUnit = when (this) {
    BeatUnitUi.Half -> BeatUnit.Half
    BeatUnitUi.Quarter -> BeatUnit.Quarter
    BeatUnitUi.Eighth -> BeatUnit.Eighth
    BeatUnitUi.Sixteenth -> BeatUnit.Sixteenth
}

private fun BeatAccentUi.toDomain(): BeatAccent = when (this) {
    BeatAccentUi.Accent -> BeatAccent.Accent
    BeatAccentUi.Normal -> BeatAccent.Normal
    BeatAccentUi.Mute -> BeatAccent.Mute
}

private fun PlaybackState.toUi(): MetronomePlaybackUi = when (this) {
    PlaybackState.Preparing -> MetronomePlaybackUi.Preparing
    is PlaybackState.Playing -> MetronomePlaybackUi.Playing(config.toUi(), beatIndex)
    is PlaybackState.Failed -> MetronomePlaybackUi.Failed(failure.toNotice())
    is PlaybackState.Stopped -> MetronomePlaybackUi.Stopped(when (reason) {
        StopReason.User -> MetronomeStopUi.User
        StopReason.FocusLoss -> MetronomeStopUi.FocusLoss
        StopReason.OutputDisconnected -> MetronomeStopUi.OutputDisconnected
        StopReason.ServiceEnded -> MetronomeStopUi.ServiceEnded
        null -> null
    })
}

private fun MetronomeFailure.toNotice(): MetronomeNotice = when (this) {
    MetronomeFailure.InvalidConfiguration -> MetronomeNotice.InvalidConfiguration
    MetronomeFailure.InvalidName -> MetronomeNotice.InvalidName
    MetronomeFailure.PresetExists -> MetronomeNotice.PresetExists
    MetronomeFailure.PresetMissing -> MetronomeNotice.PresetMissing
    MetronomeFailure.ReadFailed -> MetronomeNotice.ReadFailed
    MetronomeFailure.WriteFailed -> MetronomeNotice.SaveFailed
    MetronomeFailure.FocusDenied -> MetronomeNotice.FocusDenied
    MetronomeFailure.ServiceUnavailable -> MetronomeNotice.ServiceUnavailable
    MetronomeFailure.AudioUnavailable -> MetronomeNotice.AudioUnavailable
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
