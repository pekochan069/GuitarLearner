package com.pekochan069.guitarlearner.adapters

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.pekochan069.guitarlearner.domain.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AndroidTunerHostTest {
    @Test fun stopRevokesStartBeforeMetronomePreflightCompletes() = runTest {
        val rig = Rig(this)
        rig.metronome.gate = CompletableDeferred()
        rig.metronome.current.value = MetronomeSnapshot(playback = PlaybackState.Preparing)
        rig.host.submit(TunerRequest.Start)
        assertEquals(TunerListening.Starting, rig.host.current.value.listening)
        runCurrent()
        rig.host.submit(TunerRequest.Stop)
        assertEquals(TunerListening.Stopped, rig.host.current.value.listening)
        rig.metronome.gate!!.complete(Unit.right())
        runCurrent()
        assertEquals(0, rig.captures.size)
        assertEquals(listOf(MetronomeCommand.Stop), rig.metronome.commands)
    }

    @Test fun permissionRendezvousRejectsOldTokensAndDuplicateStarts() = runTest {
        val rig = Rig(this)
        rig.granted = false
        rig.host.submit(TunerRequest.Start)
        rig.host.submit(TunerRequest.Start)
        runCurrent()
        val old = requireNotNull(rig.host.permissionRequest.value).id
        assertEquals(1, rig.metronome.commands.size)
        rig.host.submit(TunerRequest.Stop)
        assertNull(rig.host.permissionRequest.value)
        rig.host.submit(TunerRequest.Start)
        runCurrent()
        val fresh = requireNotNull(rig.host.permissionRequest.value).id
        assertNotEquals(old, fresh)
        rig.granted = true
        rig.host.permissionResult(old, TunerPermissionOutcome.Granted)
        runCurrent()
        assertEquals(0, rig.captures.size)
        rig.host.permissionResult(fresh, TunerPermissionOutcome.Granted)
        runCurrent()
        assertTrue(rig.host.current.value.listening is TunerListening.Listening)
        assertEquals(1, rig.captures.size)
        assertFalse(rig.metronome.commands.contains(MetronomeCommand.Start))
    }

    @Test fun metronomeStopFailureAndPermissionDenialNeverOpenInput() = runTest {
        val rig = Rig(this)
        rig.metronome.result = MetronomeFailure.AudioUnavailable.left()
        rig.start()
        assertEquals(TunerListening.Failed(TunerFailure.MetronomeStopFailed(MetronomeFailure.AudioUnavailable)), rig.host.current.value.listening)
        assertEquals(0, rig.captures.size)
        rig.metronome.result = Unit.right()
        rig.granted = false
        rig.start()
        val request = requireNotNull(rig.host.permissionRequest.value)
        rig.host.permissionResult(request.id, TunerPermissionOutcome.Denied(TunerRecovery.AppSettings))
        runCurrent()
        assertEquals(TunerListening.Failed(TunerFailure.PermissionDenied(TunerRecovery.AppSettings)), rig.host.current.value.listening)
        assertEquals(0, rig.captures.size)
    }

    @Test fun stoppingDuringOpenWaitsForCleanupAndCannotAcceptLateReadyOrOldSessionResults() = runTest {
        val rig = Rig(this)
        rig.autoReady = false
        rig.autoClose = false
        rig.start()
        val old = rig.captures.single()
        rig.host.submit(TunerRequest.Stop)
        assertEquals(TunerListening.Stopping, rig.host.current.value.listening)
        rig.host.submit(TunerRequest.Start)
        runCurrent()
        assertEquals(1, rig.captures.size)
        old.ready.complete(Unit.right())
        runCurrent()
        assertEquals(TunerListening.Stopping, rig.host.current.value.listening)
        old.closed.complete(Unit.right())
        runCurrent()
        assertEquals(TunerListening.Stopped, rig.host.current.value.listening)
        rig.autoReady = true
        rig.start()
        old.emit(CaptureObservation.Failed(TunerFailure.PermissionRevoked))
        runCurrent()
        assertTrue(rig.host.current.value.listening is TunerListening.Listening)
        assertEquals(2, rig.captures.size)
        rig.captures.last().closed.complete(Unit.right())
        rig.host.submit(TunerRequest.Stop)
        runCurrent()
    }

    @Test fun revisionsResetDwellAndDropBothQueuedAndAlreadyAnalyzingOldEvidence() = runTest {
        val rig = Rig(this)
        rig.start()
        val capture = rig.captures.single()
        for (time in 0L..300L step 100) {
            advanceTimeBy(if (time == 0L) 0 else 100)
            capture.samples(rig.now())
            runCurrent()
        }
        assertEquals(TuningJudgment.InTune, rig.feedback().judgment)
        val oldEpoch = capture.epoch
        rig.host.submit(TunerRequest.SelectTarget(TunerTarget.Manual(StandardString.E4)))
        assertEquals(TuningFeedback.PluckOneString, (rig.host.current.value.listening as TunerListening.Listening).feedback)
        capture.emit(CaptureObservation.Samples(oldEpoch, rig.now(), floatArrayOf(1f), 48_000))
        runCurrent()
        assertEquals(TuningFeedback.PluckOneString, (rig.host.current.value.listening as TunerListening.Listening).feedback)
        rig.duringAnalysis = { rig.host.submit(TunerRequest.SelectTarget(TunerTarget.Automatic)) }
        capture.samples(rig.now())
        runCurrent()
        assertEquals(TuningFeedback.PluckOneString, (rig.host.current.value.listening as TunerListening.Listening).feedback)
        advanceTimeBy(20)
        capture.samples(rig.now())
        runCurrent()
        assertEquals(TuningJudgment.Settling, rig.feedback().judgment)
        capture.failure = TunerFailure.ClientSilenced
        advanceTimeBy(50)
        runCurrent()
        assertEquals(TunerListening.Failed(TunerFailure.ClientSilenced), rig.host.current.value.listening)
    }

    @Test fun independentWatchdogExpiresReadingsWithoutAnyAnalysisCompletion() = runTest {
        val rig = Rig(this)
        rig.start()
        val capture = rig.captures.single()
        capture.keepReading = true
        for (time in 0L..300L step 100) {
            advanceTimeBy(if (time == 0L) 0 else 100)
            capture.samples(rig.now())
            runCurrent()
        }
        assertEquals(TuningJudgment.InTune, rig.feedback().judgment)
        advanceTimeBy(900)
        runCurrent()
        assertEquals(TuningFeedback.PluckOneString, (rig.host.current.value.listening as TunerListening.Listening).feedback)
        capture.keepReading = false
        capture.progress = rig.now()
        advanceTimeBy(950)
        runCurrent()
        assertEquals(TunerListening.Failed(TunerFailure.NoInput), rig.host.current.value.listening)
    }

    @Test fun checkedToleranceChangesResetAndFailedWritesRetainAcceptedSelectionWithoutRestart() = runTest {
        val rig = Rig(this)
        rig.start()
        rig.store.writeResult = TunerFailure.ToleranceWriteFailed.left()
        rig.host.submit(TunerRequest.SelectTolerance(TuningTolerance.Strict))
        assertEquals(ToleranceStorageStatus.Saving(TuningTolerance.Strict), rig.host.current.value.storage)
        rig.host.visibilityChanged(TunerVisibility.Background)
        runCurrent()
        assertEquals(TuningTolerance.Normal, rig.host.current.value.tolerance)
        assertEquals(ToleranceStorageStatus.Failed(TunerFailure.ToleranceWriteFailed), rig.host.current.value.storage)
        assertEquals(TunerListening.Stopped, rig.host.current.value.listening)
        rig.host.visibilityChanged(TunerVisibility.Foreground)
        rig.store.writeResult = Unit.right()
        rig.host.submit(TunerRequest.SelectTolerance(TuningTolerance.Relaxed))
        runCurrent()
        assertEquals(TuningTolerance.Relaxed, rig.host.current.value.tolerance)
        assertEquals(TunerListening.Stopped, rig.host.current.value.listening)
        assertEquals(1, rig.captures.size)
    }

    @Test fun backgroundLockAndOwnerClearingRevokeAuthorityButConfigurationContinuationKeepsCapture() = runTest {
        val rig = Rig(this)
        rig.host.submit(TunerRequest.SelectTarget(TunerTarget.Manual(StandardString.B3)))
        rig.start()
        rig.host.visibilityChanged(TunerVisibility.ConfigurationContinuation)
        runCurrent()
        assertTrue(rig.host.current.value.listening is TunerListening.Listening)
        assertEquals(TunerTarget.Manual(StandardString.B3), rig.host.current.value.target)
        rig.host.visibilityChanged(TunerVisibility.Locked)
        runCurrent()
        rig.host.visibilityChanged(TunerVisibility.Foreground)
        assertEquals(TunerListening.Stopped, rig.host.current.value.listening)
        rig.start()
        backgroundScope.cancel()
        rig.host.close()
        runCurrent()
        assertTrue(rig.captures.all { it.stopRequests > 0 && it.closed.isCompleted })
        assertEquals(TunerListening.Stopped, rig.host.current.value.listening)
    }

    @Test fun aBlockedPreferenceWriteCannotDelayStopOrRestartCaptureAfterItsAcknowledgment() = runTest {
        val rig = Rig(this, Dispatchers.IO)
        rig.start()
        rig.store.writeEntered = CountDownLatch(1)
        rig.store.allowWrite = CountDownLatch(1)
        rig.host.submit(TunerRequest.SelectTolerance(TuningTolerance.Strict))
        runCurrent()
        withContext(Dispatchers.IO) { assertTrue(rig.store.writeEntered!!.await(5, TimeUnit.SECONDS)) }
        try {
            rig.host.visibilityChanged(TunerVisibility.Background)
            runCurrent()
            assertEquals(TunerListening.Stopped, rig.host.current.value.listening)
            assertEquals(TuningTolerance.Normal, rig.host.current.value.tolerance)
        } finally {
            rig.store.allowWrite!!.countDown()
        }
        withContext(Dispatchers.IO) { assertTrue(rig.store.writeCompleted.await(5, TimeUnit.SECONDS)) }
        runCurrent()
        assertEquals(TuningTolerance.Strict, rig.host.current.value.tolerance)
        assertEquals(TunerListening.Stopped, rig.host.current.value.listening)
        assertEquals(1, rig.captures.size)
    }

    @Test fun stalledOpeningAndUnacknowledgedShutdownFailWithoutAdmittingASuccessor() = runTest {
        val rig = Rig(this)
        rig.autoReady = false
        rig.autoClose = false
        rig.start()
        advanceTimeBy(3000)
        runCurrent()
        assertEquals(TunerListening.Failed(TunerFailure.NoInput), rig.host.current.value.listening)
        advanceTimeBy(1500)
        runCurrent()
        assertEquals(TunerListening.Failed(TunerFailure.ShutdownFailed), rig.host.current.value.listening)
        rig.start()
        assertEquals(1, rig.captures.size)
        rig.captures.single().closed.complete(Unit.right())
        runCurrent()
        rig.autoReady = true
        rig.autoClose = true
        rig.start()
        assertEquals(2, rig.captures.size)
    }

    @Test fun shutdownFailureIsFailedClosedAndSettingsFailureUsesCanonicalSnapshot() = runTest {
        val rig = Rig(this)
        rig.start()
        val capture = rig.captures.single()
        capture.shutdownResult = TunerFailure.ShutdownFailed.left()
        rig.host.submit(TunerRequest.Stop)
        runCurrent()
        assertEquals(TunerListening.Failed(TunerFailure.ShutdownFailed), rig.host.current.value.listening)
        rig.start()
        assertEquals(1, rig.captures.size)
        rig.settingsResult = TunerFailure.SettingsUnavailable.left()
        rig.host.submit(TunerRequest.OpenSettings(TunerSettingsPage.AppPermission))
        assertEquals(TunerListening.Failed(TunerFailure.SettingsUnavailable), rig.host.current.value.listening)
        assertEquals(listOf(TunerSettingsPage.AppPermission), rig.openedSettings)
    }

    private class Rig(val scope: TestScope, io: CoroutineDispatcher? = null) {
        val metronome = FakeMetronome()
        val store = FakeStore()
        val captures = mutableListOf<FakeCapture>()
        var granted = true
        var autoReady = true
        var autoClose = true
        var duringAnalysis: (() -> Unit)? = null
        var settingsResult: Either<TunerFailure, Unit> = Unit.right()
        val openedSettings = mutableListOf<TunerSettingsPage>()
        val dispatcher = StandardTestDispatcher(scope.testScheduler)
        val host = AndroidTunerHost(scope.backgroundScope, metronome,
            TunerCaptureFactory { FakeCapture(::now, autoReady, autoClose).also(captures::add) }, store,
            { granted }, { openedSettings += it; settingsResult },
            GuitarPitchDetector { _, _ ->
                duringAnalysis?.also { duringAnalysis = null }?.invoke()
                PitchEvidence.Supported(requireNotNull(PitchHz.checked(StandardString.E2.frequencyHz)))
            }, ::now, dispatcher, io ?: dispatcher, dispatcher)

        init { host.visibilityChanged(TunerVisibility.Foreground) }
        fun now(): MonotonicNanos = MonotonicNanos(scope.testScheduler.currentTime * 1_000_000)
        fun start() { host.submit(TunerRequest.Start); scope.runCurrent() }
        fun feedback(): TuningFeedback.Measured = (host.current.value.listening as TunerListening.Listening).feedback as TuningFeedback.Measured
    }

    private class FakeMetronome : Metronome {
        override val current = MutableStateFlow(MetronomeSnapshot())
        val commands = mutableListOf<MetronomeCommand>()
        var result: Either<MetronomeFailure, Unit> = Unit.right()
        var gate: CompletableDeferred<Either<MetronomeFailure, Unit>>? = null
        override suspend fun execute(command: MetronomeCommand): Either<MetronomeFailure, Unit> {
            commands += command
            return (gate?.await() ?: result).also { if (it.isRight()) current.value = current.value.copy(playback = PlaybackState.Stopped()) }
        }
    }

    private class FakeStore : TunerToleranceStore {
        var value = TuningTolerance.Normal
        var writeResult: Either<TunerFailure, Unit> = Unit.right()
        var writeEntered: CountDownLatch? = null
        var allowWrite: CountDownLatch? = null
        val writeCompleted = CountDownLatch(1)
        override fun read(): Either<TunerFailure, TuningTolerance> = value.right()
        override fun write(value: TuningTolerance): Either<TunerFailure, Unit> {
            writeEntered?.countDown()
            allowWrite?.let { check(it.await(5, TimeUnit.SECONDS)) }
            if (writeResult.isRight()) this.value = value
            writeCompleted.countDown()
            return writeResult
        }
    }

    private class FakeCapture(val clock: () -> MonotonicNanos, val autoReady: Boolean, val autoClose: Boolean) : TunerCapture {
        override val ready = CompletableDeferred<Either<TunerFailure, Unit>>()
        override val closed = CompletableDeferred<Either<TunerFailure, Unit>>()
        var progress = clock()
        var keepReading = false
        override val lastReadProgress: MonotonicNanos get() = if (keepReading) clock() else progress
        override var failure: TunerFailure? = null
        var epoch = MeasurementEpoch(0, clock())
        var stopRequests = 0
        var shutdownResult: Either<TunerFailure, Unit> = Unit.right()
        private val messages = Channel<CaptureObservation>(Channel.UNLIMITED)
        override fun begin(scope: CoroutineScope, epoch: MeasurementEpoch) { this.epoch = epoch; if (autoReady) ready.complete(Unit.right()) }
        override fun revise(epoch: MeasurementEpoch) { this.epoch = epoch }
        override suspend fun next(): CaptureObservation? = messages.receiveCatching().getOrNull()
        override fun requestStop() { stopRequests++; if (autoClose) closed.complete(shutdownResult) }
        fun samples(at: MonotonicNanos) { progress = clock(); emit(CaptureObservation.Samples(epoch, at, floatArrayOf(1f), 48_000)) }
        fun emit(message: CaptureObservation) { messages.trySend(message) }
    }
}
