package com.pekochan069.guitarlearner.presentation.logic

import arrow.core.Either
import com.pekochan069.guitarlearner.domain.AppearanceChange
import com.pekochan069.guitarlearner.domain.AppearanceFailure
import com.pekochan069.guitarlearner.domain.AppearanceSettings
import com.pekochan069.guitarlearner.domain.AppearanceSnapshot
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
import com.pekochan069.guitarlearner.presentation.contract.AppearanceNotice
import com.pekochan069.guitarlearner.presentation.contract.BeatAccentUi
import com.pekochan069.guitarlearner.presentation.contract.BeatUnitUi
import com.pekochan069.guitarlearner.presentation.contract.FoundationEvent
import com.pekochan069.guitarlearner.presentation.contract.LanguageOption
import com.pekochan069.guitarlearner.presentation.contract.MetronomeNotice
import com.pekochan069.guitarlearner.presentation.contract.MetronomePlaybackUi
import com.pekochan069.guitarlearner.presentation.contract.MetronomeStopUi
import com.pekochan069.guitarlearner.presentation.contract.Page
import com.pekochan069.guitarlearner.presentation.contract.Reading
import com.pekochan069.guitarlearner.presentation.contract.SettingsStatus
import com.pekochan069.guitarlearner.presentation.contract.ThemeOption
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
    fun failedSettingRetainsCommittedSelectionAndRejectsASecondTap(): Unit = runTest {
        val settings = ControlledAppearance()
        FoundationPresenter(settings, ControlledMetronome()).test {
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
        FoundationPresenter(settings, metronome).test {
            var state = awaitItem()
            assertEquals(90, state.bpm)
            assertFalse(state.running)
            assertTrue(state.gallerySelected)
            state.eventSink(FoundationEvent.SelectPage(Page.Gallery))
            state = awaitItem()
            assertEquals(Page.Gallery, state.page)
            state.eventSink(FoundationEvent.SetReading(Reading.Sharp))
            state = awaitItem()
            assertEquals(Reading.Sharp, state.reading)
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
    fun tempoAndQueuedPatternEditsUseTheLatestSharedSelection(): Unit = runTest {
        val metronome = ControlledMetronome()
        FoundationPresenter(ControlledAppearance(), metronome).test {
            var state = awaitItem()
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
        FoundationPresenter(ControlledAppearance(), metronome).test {
            var state = awaitItem()
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
        FoundationPresenter(ControlledAppearance(), metronome).test {
            var state = awaitItem()
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
        FoundationPresenter(ControlledAppearance(), metronome).test {
            var state = awaitItem()
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
        FoundationPresenter(ControlledAppearance(), metronome).test {
            var state = awaitItem()
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
        FoundationPresenter(ControlledAppearance(), metronome).test {
            val initial = awaitItem()
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
        FoundationPresenter(ControlledAppearance(), metronome).test {
            var state = awaitItem()
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
        FoundationPresenter(ControlledAppearance(), metronome).test {
            var state = awaitItem()
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

    override suspend fun execute(command: MetronomeCommand): Either<MetronomeFailure, Unit> {
        requests += command
        val outcome = if (command == MetronomeCommand.Stop) Either.Right(Unit) else result?.await() ?: Either.Right(Unit)
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
