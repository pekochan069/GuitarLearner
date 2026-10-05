package com.pekochan069.guitarlearner.presentation.logic

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.LocalSaveableStateRegistry
import androidx.compose.runtime.saveable.SaveableStateRegistry
import arrow.core.Either
import com.pekochan069.guitarlearner.domain.AppearanceChange
import com.pekochan069.guitarlearner.domain.AppearanceFailure
import com.pekochan069.guitarlearner.domain.AppearanceSettings
import com.pekochan069.guitarlearner.domain.AppearanceSnapshot
import com.pekochan069.guitarlearner.domain.ChordCommand
import com.pekochan069.guitarlearner.domain.ChordFailure
import com.pekochan069.guitarlearner.domain.ChordWorkspace
import com.pekochan069.guitarlearner.domain.Chords
import com.pekochan069.guitarlearner.domain.BeatAccent
import com.pekochan069.guitarlearner.domain.BeatUnit
import com.pekochan069.guitarlearner.domain.LanguagePreference
import com.pekochan069.guitarlearner.domain.Metronome
import com.pekochan069.guitarlearner.domain.MetronomeCommand
import com.pekochan069.guitarlearner.domain.MetronomeConfig
import com.pekochan069.guitarlearner.domain.MetronomeFailure
import com.pekochan069.guitarlearner.domain.MetronomePreset
import com.pekochan069.guitarlearner.domain.MetronomeSnapshot
import com.pekochan069.guitarlearner.domain.PlaybackState
import com.pekochan069.guitarlearner.domain.StopReason
import com.pekochan069.guitarlearner.domain.ThemePreference
import com.pekochan069.guitarlearner.domain.StandardString
import com.pekochan069.guitarlearner.domain.ToleranceStorageStatus
import com.pekochan069.guitarlearner.domain.Tuner
import com.pekochan069.guitarlearner.domain.TunerFailure
import com.pekochan069.guitarlearner.domain.TunerListening
import com.pekochan069.guitarlearner.domain.TunerRecovery
import com.pekochan069.guitarlearner.domain.TunerRequest
import com.pekochan069.guitarlearner.domain.TunerSnapshot
import com.pekochan069.guitarlearner.domain.TunerTarget
import com.pekochan069.guitarlearner.domain.TuningTolerance
import com.pekochan069.guitarlearner.domain.TuningFeedback
import com.pekochan069.guitarlearner.domain.TuningJudgment
import com.pekochan069.guitarlearner.presentation.contract.AppearanceNotice
import com.pekochan069.guitarlearner.presentation.contract.BeatAccentUi
import com.pekochan069.guitarlearner.presentation.contract.BeatUnitUi
import com.pekochan069.guitarlearner.presentation.contract.DevelopmentSample
import com.pekochan069.guitarlearner.presentation.contract.FeatureCategory
import com.pekochan069.guitarlearner.presentation.contract.FeatureId
import com.pekochan069.guitarlearner.presentation.contract.FoundationDestination
import com.pekochan069.guitarlearner.presentation.contract.FoundationEvent
import com.pekochan069.guitarlearner.presentation.contract.FoundationState
import com.pekochan069.guitarlearner.presentation.contract.LanguageOption
import com.pekochan069.guitarlearner.presentation.contract.MetronomeNotice
import com.pekochan069.guitarlearner.presentation.contract.MetronomePlaybackUi
import com.pekochan069.guitarlearner.presentation.contract.MetronomeStopUi
import com.pekochan069.guitarlearner.presentation.contract.GuitarStringUi
import com.pekochan069.guitarlearner.presentation.contract.HeadstockLayoutUi
import com.pekochan069.guitarlearner.presentation.contract.ToleranceUi
import com.pekochan069.guitarlearner.presentation.contract.TunerActionUi
import com.pekochan069.guitarlearner.presentation.contract.TunerListeningUi
import com.pekochan069.guitarlearner.presentation.contract.TunerNoticeUi
import com.pekochan069.guitarlearner.presentation.contract.TunerTargetUi
import com.pekochan069.guitarlearner.presentation.contract.SettingsStatus
import com.pekochan069.guitarlearner.presentation.contract.ThemeOption
import com.slack.circuit.test.CircuitReceiveTurbine
import com.slack.circuit.test.presenterTestOf
import com.slack.circuit.test.test
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FoundationPresenterTest {
    @Test
    fun homeCatalogOpensOnlyUsableFeaturesAndBackDismissesSettingsBeforeReturningHome(): Unit = runTest {
        val metronome = ControlledMetronome()
        FoundationPresenter(ControlledAppearance(), metronome, ControlledTuner(), ControlledChords(), ControlledTraining()).test {
            var state = awaitItem()
            assertEquals(FoundationDestination.Home, state.destination)
            assertEquals(listOf(FeatureCategory.Tools, FeatureCategory.Training), state.featureGroups.map { it.category })
            assertEquals(listOf(FeatureId.Metronome, FeatureId.Tuner, FeatureId.Chords), state.featureGroups.first().features)
            assertTrue(state.developmentSamples.isEmpty())
            assertFalse(state.canNavigateBack)
            state.eventSink(FoundationEvent.OpenFeature(FeatureId.Metronome))
            state = awaitItem()
            assertEquals(FoundationDestination.Feature(FeatureId.Metronome), state.destination)
            state.eventSink(FoundationEvent.SetSettingsOpen(true))
            state = awaitItem()
            state.eventSink(FoundationEvent.NavigateBack)
            state = awaitItem()
            assertFalse(state.settingsOpen)
            assertEquals(FoundationDestination.Feature(FeatureId.Metronome), state.destination)
            state.eventSink(FoundationEvent.NavigateBack)
            state = awaitItem()
            assertEquals(FoundationDestination.Home, state.destination)
            assertFalse(state.canNavigateBack)
            assertTrue(metronome.requests.isEmpty())
        }
    }

    @Test
    fun productionPresenterRejectsSampleNavigationAndSampleEdits(): Unit = runTest {
        val metronome = ControlledMetronome()
        FoundationPresenter(ControlledAppearance(), metronome, ControlledTuner(), ControlledChords(), ControlledTraining()).test {
            val state = awaitItem()
            state.eventSink(FoundationEvent.OpenSample(DevelopmentSample.Gallery))
            state.eventSink(FoundationEvent.SetGallerySelected(false))
            runCurrent()
            expectNoEvents()
            assertEquals(FoundationDestination.Home, state.destination)
            assertTrue(state.gallerySelected)
            assertTrue(metronome.requests.isEmpty())
        }
    }

    @Test
    fun obsoleteDestinationsAndLegacyOverlayPayloadsRestoreHomeSafely(): Unit = runTest {
        val saved = captureSavedState()
        val slots = saved.flatMap { (key, values) -> values.mapIndexed { index, value -> Triple(key, index, value) } }
        val destinationSlot = slots.single { unwrapSavedValue(it.third) == "home" }
        val overlaySlot = slots.single { unwrapSavedValue(it.third) == listOf("none") }
        for ((destinationId, overlayValue) in listOf(
            "sample:tuner" to true,
            "sample:gallery" to false,
            "feature:removed" to listOf("overwrite", 42),
            "Metronome" to "old-settings",
            "missing" to listOf("unknown"),
        )) {
            val restored = saved.mapValues { (key, values) ->
                values.mapIndexed { index, value ->
                    when (key to index) {
                        destinationSlot.first to destinationSlot.second -> replaceSavedValue(value, destinationId)
                        overlaySlot.first to overlaySlot.second -> replaceSavedValue(value, overlayValue)
                        else -> value
                    }
                }
            }
            val registry = SaveableStateRegistry(restored) { true }
            val presenter = FoundationPresenter(ControlledAppearance(), ControlledMetronome(), ControlledTuner(), ControlledChords(), ControlledTraining())
            presenterTestOf({ presenter.presentWithRegistry(registry) }) {
                val state = awaitItem()
                assertEquals(FoundationDestination.Home, state.destination)
                assertFalse(state.settingsOpen)
                assertFalse(state.metronome.presetsOpen)
                assertNull(state.metronome.overwriteName)
                assertFalse(state.canNavigateBack)
            }
        }
    }

    @Test
    fun overlaysReplaceEachOtherAndStaleDismissalsCannotCloseTheNewOverlay(): Unit = runTest {
        val metronome = ControlledMetronome()
        metronome.snapshot.value = MetronomeSnapshot(presets = listOf(MetronomePreset("Practice", MetronomeConfig())))
        FoundationPresenter(ControlledAppearance(), metronome, ControlledTuner(), ControlledChords(), ControlledTraining()).test {
            var state = enterMetronome()
            state.eventSink(FoundationEvent.SetPresetsOpen(true))
            state = awaitItem()
            state.eventSink(FoundationEvent.SetSettingsOpen(true))
            state = awaitItem()
            assertTrue(state.settingsOpen)
            assertFalse(state.metronome.presetsOpen)
            state.eventSink(FoundationEvent.SetPresetsOpen(false))
            runCurrent()
            expectNoEvents()
            state.eventSink(FoundationEvent.SetPresetsOpen(true))
            state = awaitItem()
            assertFalse(state.settingsOpen)
            assertTrue(state.metronome.presetsOpen)
            state.eventSink(FoundationEvent.SetSettingsOpen(false))
            runCurrent()
            expectNoEvents()
            state.eventSink(FoundationEvent.SetPresetName("Practice"))
            state = awaitItem()
            state.eventSink(FoundationEvent.SavePreset)
            state = awaitItem()
            assertEquals("Practice", state.metronome.overwriteName)
            assertTrue(state.metronome.presetsOpen)
            assertFalse(state.settingsOpen)
            state.eventSink(FoundationEvent.NavigateBack)
            state = awaitItem()
            assertNull(state.metronome.overwriteName)
            assertTrue(state.metronome.presetsOpen)
            state.eventSink(FoundationEvent.NavigateBack)
            state = awaitItem()
            assertFalse(state.metronome.presetsOpen)
            assertEquals("Practice", state.metronome.presetName)
            state.eventSink(FoundationEvent.NavigateBack)
            assertEquals(FoundationDestination.Home, awaitItem().destination)
            assertTrue(metronome.requests.isEmpty())
        }
    }

    @Test
    fun failedStopRetainsActualPlaybackAndNavigationDoesNotRestartIt(): Unit = runTest {
        val metronome = ControlledMetronome()
        val audible = MetronomeConfig(90, BeatUnit.Eighth, List(7) { BeatAccent.Normal })
        metronome.snapshot.value = MetronomeSnapshot(selected = MetronomeConfig(140), playback = PlaybackState.Playing(audible, 3))
        metronome.stopFailure = MetronomeFailure.ServiceUnavailable
        FoundationPresenter(ControlledAppearance(), metronome, ControlledTuner(), ControlledChords(), ControlledTraining()).test {
            var state = awaitItem()
            assertEquals(90, (state.metronome.playback as MetronomePlaybackUi.Playing).config.bpm)
            assertTrue(state.metronome.pendingChange)
            state.eventSink(FoundationEvent.OpenFeature(FeatureId.Metronome))
            state = awaitItem()
            state.eventSink(FoundationEvent.NavigateBack)
            state = awaitItem()
            assertEquals(FoundationDestination.Home, state.destination)
            assertEquals(3, (state.metronome.playback as MetronomePlaybackUi.Playing).beatIndex)
            assertTrue(metronome.requests.isEmpty())
            state.eventSink(FoundationEvent.SetRunning(false))
            state = awaitItem()
            assertTrue(state.running)
            assertEquals(MetronomeNotice.ServiceUnavailable, state.metronome.notice)
            assertEquals(listOf(MetronomeCommand.Stop), metronome.requests)
            metronome.stopFailure = null
            state.eventSink(FoundationEvent.SetRunning(false))
            state = awaitItem()
            assertFalse(state.running)
            assertNull(state.metronome.notice)
            assertEquals(listOf(MetronomeCommand.Stop, MetronomeCommand.Stop), metronome.requests)
        }
    }

    @Test
    fun dismissedPendingPresetWriteKeepsItsDraftAndCannotReopenADialogOnHome(): Unit = runTest {
        val metronome = ControlledMetronome()
        metronome.result = CompletableDeferred()
        FoundationPresenter(ControlledAppearance(), metronome, ControlledTuner(), ControlledChords(), ControlledTraining()).test {
            var state = enterMetronome()
            state.eventSink(FoundationEvent.SetPresetsOpen(true))
            state = awaitItem()
            state.eventSink(FoundationEvent.SetPresetName("Practice"))
            state = awaitItem()
            state.eventSink(FoundationEvent.SavePreset)
            state = awaitItem()
            assertTrue(state.metronome.savingPreset)
            state.eventSink(FoundationEvent.NavigateBack)
            state = awaitItem()
            state.eventSink(FoundationEvent.NavigateBack)
            state = awaitItem()
            assertEquals(FoundationDestination.Home, state.destination)
            metronome.result!!.complete(Either.Left(MetronomeFailure.PresetExists))
            state = awaitItem()
            assertFalse(state.metronome.savingPreset)
            assertFalse(state.metronome.presetsOpen)
            assertNull(state.metronome.overwriteName)
            assertEquals("Practice", state.metronome.presetName)
            assertEquals(MetronomeNotice.PresetExists, state.metronome.notice)
            assertEquals(listOf(MetronomeCommand.SavePreset("Practice")), metronome.requests)
        }
    }

    @Test
    fun failedSettingRetainsCommittedSelectionAndRejectsASecondTap(): Unit = runTest {
        val settings = ControlledAppearance()
        FoundationPresenter(settings, ControlledMetronome(), ControlledTuner(), ControlledChords(), ControlledTraining()).test {
            val initial = awaitItem()
            assertEquals(ThemeOption.Light, initial.theme)
            initial.eventSink(FoundationEvent.SelectTheme(ThemeOption.Dark))
            initial.eventSink(FoundationEvent.SelectTheme(ThemeOption.System))
            assertEquals(SettingsStatus.Saving, awaitItem().settingsStatus)
            runCurrent()
            assertEquals(1, settings.requests.size)
            settings.result.complete(Either.Left(AppearanceFailure.WriteFailed))
            val failed = awaitItem()
            assertEquals(SettingsStatus.Failed(AppearanceNotice.SaveFailed), failed.settingsStatus)
            assertEquals(ThemeOption.Light, failed.theme)
            failed.eventSink(FoundationEvent.DismissNotice)
            assertEquals(SettingsStatus.Idle, awaitItem().settingsStatus)
        }
    }

    @Test
    fun samplesStayLocalAndSharedCapabilitiesAreObserved(): Unit = runTest {
        val settings = ControlledAppearance()
        val metronome = ControlledMetronome()
        FoundationPresenter(settings, metronome, ControlledTuner(), ControlledChords(), ControlledTraining(), developmentSamplesEnabled = true).test {
            var state = awaitItem()
            assertEquals(90, state.bpm)
            assertFalse(state.running)
            assertTrue(state.gallerySelected)
            state.eventSink(FoundationEvent.OpenSample(DevelopmentSample.Gallery))
            state = awaitItem()
            assertEquals(FoundationDestination.Sample(DevelopmentSample.Gallery), state.destination)
            state.eventSink(FoundationEvent.SetGallerySelected(false))
            state = awaitItem()
            assertFalse(state.gallerySelected)
            state.eventSink(FoundationEvent.SetSettingsOpen(true))
            state = awaitItem()
            assertTrue(state.settingsOpen)
            settings.snapshot.value = AppearanceSnapshot(ThemePreference.Dark, LanguagePreference.Korean)
            state = awaitItem()
            assertEquals(ThemeOption.Dark, state.theme)
            assertEquals(LanguageOption.Korean, state.language)
            val selected = MetronomeConfig(120, BeatUnit.Eighth, List(8) { BeatAccent.Normal })
            val audible = selected.copy(bpm = 90)
            metronome.snapshot.value = MetronomeSnapshot(selected, PlaybackState.Playing(audible, 3))
            state = awaitItem()
            assertEquals(120, state.bpm)
            assertTrue(state.running)
            assertTrue(state.metronome.pendingChange)
            assertEquals(3, (state.metronome.playback as MetronomePlaybackUi.Playing).beatIndex)
            metronome.snapshot.value = metronome.snapshot.value.copy(playback = PlaybackState.Stopped(StopReason.FocusLoss))
            state = awaitItem()
            assertFalse(state.running)
            assertEquals(MetronomeStopUi.FocusLoss, (state.metronome.playback as MetronomePlaybackUi.Stopped).reason)
            assertTrue(settings.requests.isEmpty())
            assertTrue(metronome.requests.isEmpty())
        }
    }

    @Test
    fun tunerUsesTheCanonicalSnapshotAndEveryDepartureStopsWithoutStartingOnEntry(): Unit = runTest {
        val tuner = ControlledTuner()
        FoundationPresenter(ControlledAppearance(), ControlledMetronome(), tuner, ControlledChords(), ControlledTraining(), developmentSamplesEnabled = true).test {
            var state = awaitItem()
            state.eventSink(FoundationEvent.StartTuner)
            assertTrue(tuner.requests.isEmpty())
            state.eventSink(FoundationEvent.OpenFeature(FeatureId.Tuner))
            state = awaitItem()
            assertEquals(TunerTargetUi.Automatic, state.tuner.target)
            assertEquals(ToleranceUi.Normal, state.tuner.tolerance)
            assertEquals(TunerListeningUi.Stopped, state.tuner.listening)
            assertEquals(HeadstockLayoutUi.ThreePlusThree, state.tuner.headstockLayout)
            assertTrue(tuner.requests.isEmpty())
            state.eventSink(FoundationEvent.StartTuner)
            runCurrent()
            expectNoEvents()
            assertEquals(listOf(TunerRequest.Start), tuner.requests)
            state.eventSink(FoundationEvent.SelectTunerTarget(TunerTargetUi.Manual(GuitarStringUi.B3)))
            state = awaitItem()
            assertEquals(TunerTarget.Manual(StandardString.B3), tuner.current.value.target)
            tuner.snapshot.value = tuner.snapshot.value.copy(listening = TunerListening.Listening(
                TuningFeedback.Measured(StandardString.B3, 4.2, TuningJudgment.Settling)))
            state = awaitItem()
            val reading = state.tuner
            val requests = tuner.requests.toList()
            for (layout in listOf(HeadstockLayoutUi.InlineSix, HeadstockLayoutUi.ThreePlusThree, HeadstockLayoutUi.InlineSix)) {
                state.eventSink(FoundationEvent.SelectHeadstockLayout(layout))
                state = awaitItem()
                assertEquals(reading.copy(headstockLayout = layout), state.tuner)
                assertEquals(requests, tuner.requests)
            }
            state.eventSink(FoundationEvent.SelectHeadstockLayout(HeadstockLayoutUi.InlineSix))
            runCurrent()
            expectNoEvents()
            assertEquals(requests, tuner.requests)
            tuner.snapshot.value = tuner.snapshot.value.copy(listening = TunerListening.Stopped)
            state = awaitItem()
            assertEquals(TunerListeningUi.Stopped, state.tuner.listening)
            state.eventSink(FoundationEvent.SetSettingsOpen(true))
            state = awaitItem()
            state.eventSink(FoundationEvent.NavigateBack)
            state = awaitItem()
            assertEquals(FoundationDestination.Feature(FeatureId.Tuner), state.destination)
            assertFalse(tuner.requests.contains(TunerRequest.Stop))
            for (departure in listOf(FoundationEvent.NavigateBack,
                FoundationEvent.OpenFeature(FeatureId.Metronome), FoundationEvent.OpenFeature(FeatureId.Chords), FoundationEvent.OpenSample(DevelopmentSample.Gallery))) {
                val stopCount = tuner.requests.count { it == TunerRequest.Stop }
                state.eventSink(departure)
                assertEquals(stopCount + 1, tuner.requests.count { it == TunerRequest.Stop })
                state.eventSink(FoundationEvent.StartTuner)
                assertEquals(1, tuner.requests.count { it == TunerRequest.Start })
                state = awaitItem()
                state.eventSink(FoundationEvent.OpenFeature(FeatureId.Tuner))
                state = awaitItem()
                assertEquals(TunerTargetUi.Manual(GuitarStringUi.B3), state.tuner.target)
                assertEquals(HeadstockLayoutUi.InlineSix, state.tuner.headstockLayout)
                assertEquals(1, tuner.requests.count { it == TunerRequest.Start })
            }
        }
    }

    @Test
    fun tunerPermissionAndPreferenceFailuresAreObservedWithoutInventingAcceptedChoices(): Unit = runTest {
        val tuner = ControlledTuner()
        FoundationPresenter(ControlledAppearance(), ControlledMetronome(), tuner, ControlledChords(), ControlledTraining()).test {
            awaitItem().eventSink(FoundationEvent.OpenFeature(FeatureId.Tuner))
            var state = awaitItem()
            tuner.snapshot.value = TunerSnapshot(target = TunerTarget.Manual(StandardString.E4), tolerance = TuningTolerance.Strict,
                listening = TunerListening.Failed(TunerFailure.PermissionDenied(TunerRecovery.AppSettings)),
                storage = ToleranceStorageStatus.Failed(TunerFailure.ToleranceWriteFailed))
            state = awaitItem()
            assertEquals(TunerTargetUi.Manual(GuitarStringUi.E4), state.tuner.target)
            assertEquals(ToleranceUi.Strict, state.tuner.tolerance)
            assertEquals(TunerNoticeUi.SaveFailed, state.tuner.preferenceNotice)
            assertEquals(TunerListeningUi.Failed(TunerNoticeUi.PermissionDenied,
                setOf(TunerActionUi.AppSettings, TunerActionUi.Retry)), state.tuner.listening)
            state.eventSink(FoundationEvent.OpenTunerSettings(TunerActionUi.AppSettings))
            assertEquals(TunerRequest.OpenSettings(com.pekochan069.guitarlearner.domain.TunerSettingsPage.AppPermission), tuner.requests.last())
            tuner.snapshot.value = tuner.snapshot.value.copy(listening = TunerListening.Failed(TunerFailure.ShutdownFailed))
            state = awaitItem()
            assertEquals(TunerListeningUi.Failed(TunerNoticeUi.ShutdownFailed, setOf(TunerActionUi.AppSettings)), state.tuner.listening)
            state.eventSink(FoundationEvent.OpenTunerSettings(TunerActionUi.AppSettings))
            assertEquals(TunerRequest.OpenSettings(com.pekochan069.guitarlearner.domain.TunerSettingsPage.AppPermission), tuner.requests.last())
        }
    }

    @Test
    fun tempoAndQueuedPatternEditsUseTheLatestSharedSelection(): Unit = runTest {
        val metronome = ControlledMetronome()
        FoundationPresenter(ControlledAppearance(), metronome, ControlledTuner(), ControlledChords(), ControlledTraining()).test {
            var state = enterMetronome()
            state.eventSink(FoundationEvent.SetBpm(999))
            state = awaitItem()
            assertEquals(240, state.bpm)
            assertEquals(MetronomeCommand.SetTempo(240), metronome.requests.last())
            state.eventSink(FoundationEvent.SetBpm(-1))
            state = awaitItem()
            assertEquals(40, state.bpm)
            state.eventSink(FoundationEvent.SetBeatCount(8))
            state.eventSink(FoundationEvent.SetBeatUnit(BeatUnitUi.Eighth))
            state.eventSink(FoundationEvent.SetBeatAccent(0, BeatAccentUi.Mute))
            do {
                state = awaitItem()
            } while (state.metronome.config.beats.first() != BeatAccentUi.Mute)
            assertEquals(8, state.metronome.config.numerator)
            assertEquals(BeatUnitUi.Eighth, state.metronome.config.denominator)
            assertEquals(List(7) { BeatAccentUi.Normal }, state.metronome.config.beats.drop(1))
            assertEquals(
                MetronomeCommand.SetPattern(BeatUnit.Eighth, listOf(BeatAccent.Mute) + List(7) { BeatAccent.Normal }),
                metronome.requests.last(),
            )
        }
    }

    @Test
    fun burstStepButtonsKeepEveryAdjustmentWhileTheFirstWriteIsPending(): Unit = runTest {
        val metronome = ControlledMetronome()
        metronome.result = CompletableDeferred()
        FoundationPresenter(ControlledAppearance(), metronome, ControlledTuner(), ControlledChords(), ControlledTraining()).test {
            var state = enterMetronome()
            state.eventSink(FoundationEvent.AdjustBpm(1))
            state.eventSink(FoundationEvent.AdjustBpm(1))
            state.eventSink(FoundationEvent.AdjustBeatCount(1))
            state.eventSink(FoundationEvent.AdjustBeatCount(1))
            runCurrent()
            assertEquals(listOf(MetronomeCommand.SetTempo(91)), metronome.requests)
            assertEquals(90, metronome.snapshot.value.selected.bpm)
            metronome.result!!.complete(Either.Right(Unit))
            do {
                state = awaitItem()
            } while (state.bpm != 92 || state.metronome.config.numerator != 6)
            assertEquals(listOf(MetronomeCommand.SetTempo(91), MetronomeCommand.SetTempo(92)), metronome.requests.take(2))
            assertEquals(BeatAccentUi.Accent, state.metronome.config.beats.first())
            assertEquals(List(5) { BeatAccentUi.Normal }, state.metronome.config.beats.drop(1))
        }
    }

    @Test
    fun stopReachesTheSharedOwnerWhileAConfigurationWriteIsPending(): Unit = runTest {
        val metronome = ControlledMetronome()
        metronome.snapshot.value = MetronomeSnapshot(playback = PlaybackState.Playing(MetronomeConfig(), 0))
        metronome.result = CompletableDeferred()
        FoundationPresenter(ControlledAppearance(), metronome, ControlledTuner(), ControlledChords(), ControlledTraining()).test {
            var state = enterMetronome()
            assertTrue(state.running)
            state.eventSink(FoundationEvent.SetBpm(91))
            runCurrent()
            assertEquals(listOf(MetronomeCommand.SetTempo(91)), metronome.requests)
            state.eventSink(FoundationEvent.SetRunning(false))
            state = awaitItem()
            assertFalse(state.running)
            assertEquals(90, state.bpm)
            assertFalse(metronome.result!!.isCompleted)
            assertEquals(listOf(MetronomeCommand.SetTempo(91), MetronomeCommand.Stop), metronome.requests)
            metronome.result!!.complete(Either.Right(Unit))
            state = awaitItem()
            assertEquals(91, state.bpm)
            assertFalse(state.running)
        }
    }

    @Test
    fun burstAccentCyclesUseTheCommittedAccentAfterThePendingWrite(): Unit = runTest {
        val metronome = ControlledMetronome()
        metronome.result = CompletableDeferred()
        FoundationPresenter(ControlledAppearance(), metronome, ControlledTuner(), ControlledChords(), ControlledTraining()).test {
            var state = enterMetronome()
            state.eventSink(FoundationEvent.CycleBeatAccent(0))
            state.eventSink(FoundationEvent.CycleBeatAccent(0))
            runCurrent()
            assertEquals(1, metronome.requests.size)
            assertEquals(BeatAccent.Normal, (metronome.requests.single() as MetronomeCommand.SetPattern).beats.first())
            assertEquals(BeatAccent.Accent, metronome.snapshot.value.selected.beats.first())
            metronome.result!!.complete(Either.Right(Unit))
            do {
                state = awaitItem()
            } while (state.metronome.config.beats.first() != BeatAccentUi.Mute)
            assertEquals(2, metronome.requests.size)
            assertEquals(BeatAccent.Mute, (metronome.requests.last() as MetronomeCommand.SetPattern).beats.first())
        }
    }

    @Test
    fun queuedResizeRejectsAccentEditsForRemovedBeatsWithoutAnotherWrite(): Unit = runTest {
        val metronome = ControlledMetronome()
        metronome.result = CompletableDeferred()
        FoundationPresenter(ControlledAppearance(), metronome, ControlledTuner(), ControlledChords(), ControlledTraining()).test {
            var state = enterMetronome()
            state.eventSink(FoundationEvent.SetBeatCount(1))
            state.eventSink(FoundationEvent.CycleBeatAccent(3))
            state.eventSink(FoundationEvent.SetBeatAccent(3, BeatAccentUi.Mute))
            runCurrent()
            assertEquals(1, metronome.requests.size)
            metronome.result!!.complete(Either.Right(Unit))
            do {
                state = awaitItem()
            } while (state.metronome.notice != MetronomeNotice.InvalidConfiguration)
            assertEquals(listOf(BeatAccentUi.Accent), state.metronome.config.beats)
            assertEquals(listOf(MetronomeCommand.SetPattern(BeatUnit.Quarter, listOf(BeatAccent.Accent))), metronome.requests)
        }
    }

    @Test
    fun startDoesNotInventRunningAndTypedFailureRemainsRecoverable(): Unit = runTest {
        val metronome = ControlledMetronome()
        metronome.result = CompletableDeferred()
        FoundationPresenter(ControlledAppearance(), metronome, ControlledTuner(), ControlledChords(), ControlledTraining()).test {
            val initial = enterMetronome()
            initial.eventSink(FoundationEvent.SetRunning(true))
            runCurrent()
            assertEquals(listOf(MetronomeCommand.Start), metronome.requests)
            assertFalse(initial.running)
            metronome.snapshot.value = metronome.snapshot.value.copy(playback = PlaybackState.Preparing)
            assertEquals(MetronomePlaybackUi.Preparing, awaitItem().metronome.playback)
            metronome.result!!.complete(Either.Left(MetronomeFailure.FocusDenied))
            val failed = awaitItem()
            assertFalse(failed.running)
            assertEquals(MetronomeNotice.FocusDenied, failed.metronome.notice)
            failed.eventSink(FoundationEvent.DismissMetronomeNotice)
            assertNull(awaitItem().metronome.notice)
        }
    }

    @Test
    fun duplicatePresetNeedsConfirmationAndFailedOverwriteKeepsTheNameAndSavedData(): Unit = runTest {
        val metronome = ControlledMetronome()
        val saved = MetronomePreset("Practice", MetronomeConfig(140))
        metronome.snapshot.value = MetronomeSnapshot(presets = listOf(saved))
        metronome.result = CompletableDeferred()
        FoundationPresenter(ControlledAppearance(), metronome, ControlledTuner(), ControlledChords(), ControlledTraining()).test {
            var state = enterMetronome()
            state.eventSink(FoundationEvent.SetPresetsOpen(true))
            state = awaitItem()
            state.eventSink(FoundationEvent.SetPresetName(" Practice "))
            state = awaitItem()
            state.eventSink(FoundationEvent.SavePreset)
            state = awaitItem()
            assertEquals("Practice", state.metronome.overwriteName)
            assertTrue(metronome.requests.isEmpty())
            state.eventSink(FoundationEvent.CancelPresetOverwrite)
            state = awaitItem()
            assertNull(state.metronome.overwriteName)
            state.eventSink(FoundationEvent.SavePreset)
            state = awaitItem()
            state.eventSink(FoundationEvent.ConfirmPresetOverwrite)
            state.eventSink(FoundationEvent.ConfirmPresetOverwrite)
            assertTrue(awaitItem().metronome.savingPreset)
            runCurrent()
            assertEquals(listOf(MetronomeCommand.SavePreset("Practice", overwrite = true)), metronome.requests)
            metronome.result!!.complete(Either.Left(MetronomeFailure.WriteFailed))
            state = awaitItem()
            assertFalse(state.metronome.savingPreset)
            assertEquals(MetronomeNotice.SaveFailed, state.metronome.notice)
            assertEquals(" Practice ", state.metronome.presetName)
            assertEquals("Practice", state.metronome.overwriteName)
            assertEquals(140, state.metronome.presets.single().config.bpm)
        }
    }

    @Test
    fun startupReadFailureAndLoadDeleteOutcomesAreReported(): Unit = runTest {
        val metronome = ControlledMetronome()
        metronome.snapshot.value = MetronomeSnapshot(readFailure = MetronomeFailure.ReadFailed)
        FoundationPresenter(ControlledAppearance(), metronome, ControlledTuner(), ControlledChords(), ControlledTraining()).test {
            var state = enterMetronome()
            assertEquals(MetronomeNotice.ReadFailed, state.metronome.notice)
            state.eventSink(FoundationEvent.DismissMetronomeNotice)
            state = awaitItem()
            assertNull(state.metronome.notice)
            metronome.result = CompletableDeferred()
            state.eventSink(FoundationEvent.LoadPreset("Missing"))
            assertTrue(awaitItem().metronome.savingPreset)
            metronome.result!!.complete(Either.Left(MetronomeFailure.PresetMissing))
            state = awaitItem()
            assertEquals(MetronomeNotice.PresetMissing, state.metronome.notice)
            metronome.result = CompletableDeferred()
            state.eventSink(FoundationEvent.DeletePreset("Practice"))
            assertTrue(awaitItem().metronome.savingPreset)
            metronome.result!!.complete(Either.Left(MetronomeFailure.WriteFailed))
            assertEquals(MetronomeNotice.SaveFailed, awaitItem().metronome.notice)
            assertEquals(listOf(MetronomeCommand.LoadPreset("Missing"), MetronomeCommand.DeletePreset("Practice")), metronome.requests)
        }
    }
}

private suspend fun CircuitReceiveTurbine<FoundationState>.enterMetronome(): FoundationState {
    awaitItem().eventSink(FoundationEvent.OpenFeature(FeatureId.Metronome))
    return awaitItem()
}

private suspend fun captureSavedState(): Map<String, List<Any?>> {
    val registry = SaveableStateRegistry(null) { true }
    val presenter = FoundationPresenter(ControlledAppearance(), ControlledMetronome(), ControlledTuner(), ControlledChords(), ControlledTraining())
    var saved: Map<String, List<Any?>> = emptyMap()
    presenterTestOf({ presenter.presentWithRegistry(registry) }) {
        awaitItem()
        saved = registry.performSave()
    }
    return saved
}

private fun unwrapSavedValue(value: Any?): Any? = if (value is MutableState<*>) value.value else value

private fun replaceSavedValue(previous: Any?, replacement: Any): Any =
    if (previous is MutableState<*>) mutableStateOf(replacement) else replacement

@Composable
private fun FoundationPresenter.presentWithRegistry(registry: SaveableStateRegistry): FoundationState {
    lateinit var state: FoundationState
    CompositionLocalProvider(LocalSaveableStateRegistry provides registry) { state = present() }
    return state
}

private class ControlledAppearance : AppearanceSettings {
    val snapshot = MutableStateFlow(AppearanceSnapshot(ThemePreference.Light, LanguagePreference.System))
    override val current: StateFlow<AppearanceSnapshot> = snapshot.asStateFlow()
    val requests = mutableListOf<AppearanceChange>()
    val result = CompletableDeferred<Either<AppearanceFailure, Unit>>()

    override suspend fun select(change: AppearanceChange): Either<AppearanceFailure, Unit> {
        requests += change
        return result.await()
    }
}

private class ControlledMetronome : Metronome {
    val snapshot = MutableStateFlow(MetronomeSnapshot())
    override val current: StateFlow<MetronomeSnapshot> = snapshot.asStateFlow()
    val requests = mutableListOf<MetronomeCommand>()
    var result: CompletableDeferred<Either<MetronomeFailure, Unit>>? = null
    var stopFailure: MetronomeFailure? = null

    override suspend fun execute(command: MetronomeCommand): Either<MetronomeFailure, Unit> {
        requests += command
        val outcome = if (command == MetronomeCommand.Stop) stopFailure?.let { Either.Left(it) } ?: Either.Right(Unit)
            else result?.await() ?: Either.Right(Unit)
        if (outcome is Either.Left) return outcome
        when (command) {
            MetronomeCommand.Stop -> snapshot.value = snapshot.value.copy(playback = PlaybackState.Stopped(StopReason.User))
            is MetronomeCommand.SetTempo -> snapshot.value = snapshot.value.copy(selected = snapshot.value.selected.copy(bpm = command.bpm))
            is MetronomeCommand.SetPattern -> snapshot.value = snapshot.value.copy(selected = snapshot.value.selected.copy(denominator = command.denominator, beats = command.beats))
            else -> Unit
        }
        return Either.Right(Unit)
    }
}

private class ControlledChords : Chords {
    override val current = MutableStateFlow(ChordWorkspace()).asStateFlow()
    override suspend fun execute(command: ChordCommand): Either<ChordFailure, Unit> = Either.Right(Unit)
}

internal class ControlledTuner : Tuner {
    val snapshot = MutableStateFlow(TunerSnapshot())
    override val current: StateFlow<TunerSnapshot> = snapshot.asStateFlow()
    val requests = mutableListOf<TunerRequest>()
    override fun submit(request: TunerRequest) {
        requests += request
        when (request) {
            TunerRequest.Stop -> snapshot.value = snapshot.value.copy(listening = TunerListening.Stopped)
            is TunerRequest.SelectTarget -> snapshot.value = snapshot.value.copy(target = request.target)
            else -> Unit
        }
    }
}
