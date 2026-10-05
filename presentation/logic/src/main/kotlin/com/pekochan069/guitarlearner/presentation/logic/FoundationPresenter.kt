package com.pekochan069.guitarlearner.presentation.logic

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
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
import com.pekochan069.guitarlearner.presentation.contract.DevelopmentSample
import com.pekochan069.guitarlearner.presentation.contract.FeatureCategory
import com.pekochan069.guitarlearner.presentation.contract.FeatureGroup
import com.pekochan069.guitarlearner.presentation.contract.FeatureId
import com.pekochan069.guitarlearner.presentation.contract.FoundationDestination
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
import com.pekochan069.guitarlearner.domain.Tuner
import com.pekochan069.guitarlearner.domain.TunerRequest
import com.pekochan069.guitarlearner.domain.TunerSettingsPage
import com.pekochan069.guitarlearner.presentation.contract.TunerActionUi
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
    private val tuner: Tuner,
    private val developmentSamplesEnabled: Boolean = false,
) : Presenter<FoundationState> {
    @Composable
    override fun present(): FoundationState {
        var destinationId by rememberSaveable { mutableStateOf("home") }
        var gallerySelected by rememberSaveable { mutableStateOf(true) }
        var overlay by rememberSaveable(stateSaver = OverlaySaver) { mutableStateOf<Overlay>(Overlay.None) }
        var settingsStatus by remember { mutableStateOf<SettingsStatus>(SettingsStatus.Idle) }
        val snapshot by appearance.current.collectAsState()
        val metronomeSnapshot by metronome.current.collectAsState()
        val tunerSnapshot by tuner.current.collectAsState()
        var presetName by rememberSaveable { mutableStateOf("") }
        var savingPreset by remember { mutableStateOf(false) }
        var metronomeNotice by remember { mutableStateOf<MetronomeNotice?>(null) }
        var dismissedReadFailure by remember { mutableStateOf<MetronomeFailure?>(null) }
        val commands = remember { Mutex() }
        val scope = rememberCoroutineScope()
        val samples = if (developmentSamplesEnabled) DevelopmentSample.entries else emptyList()
        val destination = restoreDestination(destinationId, samples)
        val visibleOverlay = when {
            overlay == Overlay.Settings -> overlay
            destination == FoundationDestination.Feature(FeatureId.Metronome) -> overlay
            else -> Overlay.None
        }

        fun navigate(next: FoundationDestination) {
            if (destinationId == "feature:tuner" && next != FoundationDestination.Feature(FeatureId.Tuner)) {
                tuner.submit(TunerRequest.Stop)
            }
            overlay = Overlay.None
            destinationId = next.savedId()
        }

        fun navigateBack() {
            overlay = when (overlay) {
                is Overlay.Overwrite -> Overlay.Presets
                Overlay.Settings, Overlay.Presets -> Overlay.None
                Overlay.None -> {
                    navigate(FoundationDestination.Home)
                    Overlay.None
                }
            }
        }

        fun execute(
            presetOperation: Boolean = false,
            command: () -> MetronomeCommand,
        ) {
            if (presetOperation && savingPreset) return
            if (presetOperation) savingPreset = true
            val requestedOverlay = overlay
            scope.launch {
                try {
                    commands.withLock {
                        val request = command()
                        metronome.execute(request).fold(
                            ifLeft = {
                                if (it == MetronomeFailure.PresetExists && request is MetronomeCommand.SavePreset && !request.overwrite) {
                                    if (destinationId == "feature:metronome" && overlay == requestedOverlay && overlay == Overlay.Presets) {
                                        overlay = Overlay.Overwrite(request.name.trim())
                                    } else {
                                        metronomeNotice = it.toNotice()
                                    }
                                } else {
                                    metronomeNotice = it.toNotice()
                                }
                            },
                            ifRight = {
                                metronomeNotice = null
                                if (request is MetronomeCommand.SavePreset && overlay == Overlay.Overwrite(request.name)) {
                                    overlay = Overlay.Presets
                                }
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
            destination = destination,
            featureGroups = featureCatalog.filter { it.features.isNotEmpty() },
            developmentSamples = samples,
            canNavigateBack = destination != FoundationDestination.Home || visibleOverlay != Overlay.None,
            tuner = tunerSnapshot.toUi(),
            metronome = MetronomeUiState(
                config = metronomeSnapshot.selected.toUi(),
                playback = metronomeSnapshot.playback.toUi(),
                pendingChange = (metronomeSnapshot.playback as? PlaybackState.Playing)?.config?.let {
                    it != metronomeSnapshot.selected
                } ?: false,
                presets = metronomeSnapshot.presets.map { MetronomePresetUi(it.name, it.config.toUi()) },
                presetsOpen = visibleOverlay == Overlay.Presets || visibleOverlay is Overlay.Overwrite,
                presetName = presetName,
                overwriteName = (visibleOverlay as? Overlay.Overwrite)?.name,
                savingPreset = savingPreset,
                notice = metronomeNotice ?: metronomeSnapshot.readFailure
                    ?.takeUnless { it == dismissedReadFailure }?.toNotice(),
            ),
            gallerySelected = gallerySelected,
            settingsOpen = visibleOverlay == Overlay.Settings,
            theme = snapshot.theme.toOption(),
            language = snapshot.language?.toOption(),
            settingsStatus = settingsStatus,
            eventSink = { event ->
                when (event) {
                    is FoundationEvent.OpenFeature -> if (featureCatalog.any { event.id in it.features }) {
                        navigate(FoundationDestination.Feature(event.id))
                    }
                    is FoundationEvent.OpenSample -> if (event.id in samples) {
                        navigate(FoundationDestination.Sample(event.id))
                    }
                    FoundationEvent.NavigateBack -> navigateBack()
                    FoundationEvent.StartTuner -> if (destinationId == "feature:tuner") tuner.submit(TunerRequest.Start)
                    FoundationEvent.StopTuner -> tuner.submit(TunerRequest.Stop)
                    is FoundationEvent.SelectTunerTarget -> if (destinationId == "feature:tuner") tuner.submit(TunerRequest.SelectTarget(event.value.toDomain()))
                    is FoundationEvent.SelectTunerTolerance -> if (destinationId == "feature:tuner") tuner.submit(TunerRequest.SelectTolerance(event.value.toDomain()))
                    FoundationEvent.ReloadTunerTolerance -> tuner.submit(TunerRequest.ReloadTolerance)
                    is FoundationEvent.OpenTunerSettings -> if (destinationId == "feature:tuner") {
                        when (event.action) {
                            TunerActionUi.Retry -> tuner.submit(TunerRequest.Start)
                            TunerActionUi.AppSettings -> tuner.submit(TunerRequest.OpenSettings(TunerSettingsPage.AppPermission))
                            TunerActionUi.PrivacySettings -> tuner.submit(TunerRequest.OpenSettings(TunerSettingsPage.MicrophonePrivacy))
                        }
                    }
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
                    is FoundationEvent.SetPresetsOpen -> {
                        if (event.value) {
                            if (destination == FoundationDestination.Feature(FeatureId.Metronome)) overlay = Overlay.Presets
                        } else if (overlay == Overlay.Presets || overlay is Overlay.Overwrite) overlay = Overlay.None
                    }
                    is FoundationEvent.SetPresetName -> presetName = event.value
                    FoundationEvent.SavePreset -> {
                        val name = presetName.trim()
                        if (!savingPreset && metronome.current.value.presets.any { it.name == name }) {
                            overlay = Overlay.Overwrite(name)
                            metronomeNotice = null
                        } else {
                            execute(presetOperation = true) { MetronomeCommand.SavePreset(name) }
                        }
                    }
                    FoundationEvent.ConfirmPresetOverwrite -> (overlay as? Overlay.Overwrite)?.name?.let { name ->
                        execute(presetOperation = true) { MetronomeCommand.SavePreset(name, overwrite = true) }
                    }
                    FoundationEvent.CancelPresetOverwrite -> if (overlay is Overlay.Overwrite) overlay = Overlay.Presets
                    is FoundationEvent.LoadPreset -> execute(presetOperation = true) { MetronomeCommand.LoadPreset(event.name) }
                    is FoundationEvent.DeletePreset -> execute(presetOperation = true) { MetronomeCommand.DeletePreset(event.name) }
                    FoundationEvent.DismissMetronomeNotice -> {
                        metronomeNotice = null
                        dismissedReadFailure = metronomeSnapshot.readFailure
                    }
                    is FoundationEvent.SetGallerySelected -> if (developmentSamplesEnabled) gallerySelected = event.value
                    is FoundationEvent.SetSettingsOpen -> {
                        if (event.value) overlay = Overlay.Settings
                        else if (overlay == Overlay.Settings) overlay = Overlay.None
                    }
                    is FoundationEvent.SelectTheme -> select(AppearanceChange.Theme(event.value.toPreference()))
                    is FoundationEvent.SelectLanguage -> select(AppearanceChange.Language(event.value.toPreference()))
                    FoundationEvent.DismissNotice -> if (settingsStatus is SettingsStatus.Failed) settingsStatus = SettingsStatus.Idle
                }
            },
        )
    }

    class Factory(
        private val appearance: AppearanceSettings,
        private val metronome: Metronome,
        private val tuner: Tuner,
        private val developmentSamplesEnabled: Boolean = false,
    ) : Presenter.Factory {
        fun create(): FoundationPresenter = FoundationPresenter(appearance, metronome, tuner, developmentSamplesEnabled)

        override fun create(screen: Screen, navigator: Navigator, context: CircuitContext): Presenter<*>? =
            if (screen == FoundationScreen) create() else null
    }
}

private val featureCatalog = listOf(FeatureGroup(FeatureCategory.Tools, listOf(FeatureId.Metronome, FeatureId.Tuner)))

private fun FoundationDestination.savedId(): String = when (this) {
    FoundationDestination.Home -> "home"
    is FoundationDestination.Feature -> "feature:${id.savedId}"
    is FoundationDestination.Sample -> "sample:${id.savedId}"
}

private fun restoreDestination(savedId: String, samples: List<DevelopmentSample>): FoundationDestination =
    featureCatalog.flatMap { it.features }.firstOrNull { savedId == "feature:${it.savedId}" }
        ?.let { FoundationDestination.Feature(it) }
        ?: samples.firstOrNull { savedId == "sample:${it.savedId}" }?.let { FoundationDestination.Sample(it) }
        ?: FoundationDestination.Home

private sealed interface Overlay {
    data object None : Overlay
    data object Settings : Overlay
    data object Presets : Overlay
    data class Overwrite(val name: String) : Overlay
}

private val OverlaySaver = Saver<Overlay, Any>(
    save = {
        when (it) {
            Overlay.None -> listOf("none")
            Overlay.Settings -> listOf("settings")
            Overlay.Presets -> listOf("presets")
            is Overlay.Overwrite -> listOf("overwrite", it.name)
        }
    },
    restore = {
        val values = it as? List<*>
        when (values?.firstOrNull()) {
            "settings" -> Overlay.Settings
            "presets" -> Overlay.Presets
            "overwrite" -> (values.getOrNull(1) as? String)?.let(Overlay::Overwrite) ?: Overlay.None
            else -> Overlay.None
        }
    },
)

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
