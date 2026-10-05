package com.pekochan069.guitarlearner

import android.app.Application
import android.app.NotificationManager
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.session.MediaSession
import android.media.session.MediaController
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.core.os.BundleCompat
import arrow.core.Either
import com.pekochan069.guitarlearner.adapters.AndroidMetronomeHost
import com.pekochan069.guitarlearner.adapters.AndroidProgressionsHost
import com.pekochan069.guitarlearner.adapters.ProgressionOutput
import com.pekochan069.guitarlearner.adapters.ProgressionOutputFactory
import com.pekochan069.guitarlearner.adapters.MetronomeAudioDiagnostics
import com.pekochan069.guitarlearner.domain.*
import dev.zacsweers.metro.createGraphFactory
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class ProgressionPlaybackTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private lateinit var application: GuitarLearnerApplication
    private lateinit var host: AndroidProgressionsHost
    private lateinit var preferences: ControlledCommitPreferences
    private lateinit var metronome: AndroidMetronomeHost
    private val startGate = Mutex()

    @Before fun installHost(): Unit = runBlocking {
        application = compose.activity.application as GuitarLearnerApplication
        application.graph.metronomeHost.execute(MetronomeCommand.Stop)
        application.graph.progressions.execute(ProgressionCommand.Stop)
        val stored = application.getSharedPreferences("progression_playback_test", Application.MODE_PRIVATE)
        assertTrue(stored.edit().clear().commit())
        preferences = ControlledCommitPreferences(stored)
        metronome = AndroidMetronomeHost(application, MetronomePlaybackService::class.java, MainActivity::class.java,
            application.getSharedPreferences("progression_peer_test", Application.MODE_PRIVATE), startGate = startGate,
            beforeStart = { host.execute(ProgressionCommand.Stop) })
        host = AndroidProgressionsHost(application, ProgressionPlaybackService::class.java, MainActivity::class.java,
            preferences, startGate = startGate, beforeStart = { metronome.execute(MetronomeCommand.Stop) })
        application.graphOverride = createGraphFactory<AppGraph.Factory>().create(application, metronome, host)
        host.execute(ProgressionCommand.Insert(ProgressionStep.Chord("C", ChordShape(listOf(
            StringStop.Muted, StringStop.Fretted(3), StringStop.Fretted(2), StringStop.Open, StringStop.Fretted(1), StringStop.Open))))).success()
        host.execute(ProgressionCommand.SetLoop(true)).success()
        host.execute(ProgressionCommand.SetTempo(160)).success()
        host.execute(ProgressionCommand.SetSignature(BeatUnit.Eighth, 3)).success()
    }

    @After fun restoreGraph(): Unit = runBlocking {
        if (::preferences.isInitialized) preferences.releaseCommit()
        if (startGate.isLocked) startGate.unlock()
        if (::host.isInitialized) host.execute(ProgressionCommand.Stop)
        if (::metronome.isInitialized) metronome.execute(MetronomeCommand.Stop)
        if (::application.isInitialized) application.graphOverride = null
    }

    @Test fun nativeConcurrentPauseAndResumeAcknowledgeEveryCallerAndFreezePosition(): Unit = runBlocking {
        host.execute(ProgressionCommand.Play()).success()
        await { it is ProgressionPlayback.Playing }
        val mediaToken = token()
        withContext(Dispatchers.Main) {
            withTimeout(5_000) { coroutineScope {
                List(10) { async(start = CoroutineStart.UNDISPATCHED) { host.execute(ProgressionCommand.Pause).success() } }.awaitAll()
            } }
        }
        val paused = host.current.value.playback as ProgressionPlayback.Paused
        delay(150)
        assertEquals(paused, host.current.value.playback)
        assertEquals(mediaToken, token())
        withContext(Dispatchers.Main) {
            withTimeout(5_000) { coroutineScope {
                List(10) { async(start = CoroutineStart.UNDISPATCHED) { host.execute(ProgressionCommand.Resume).success() } }.awaitAll()
            } }
        }
        await { it is ProgressionPlayback.Playing && it.position.countInBeat == null }
        assertEquals(mediaToken, token())
        assertEquals(0, host.currentAudioDiagnostics()?.underruns)
    }

    @Test fun queuedStructuralEditStopsPlaybackStartedDuringAnEarlierWrite(): Unit = runBlocking {
        val gate = preferences.blockNextCommit()
        val naming = async { host.execute(ProgressionCommand.SetName("Queued edit")) }
        withTimeout(5_000) { gate.entered.await() }
        val editing = async(start = CoroutineStart.UNDISPATCHED) { host.execute(ProgressionCommand.SetDuration(0, NoteDuration(NoteValue.Whole, true))) }
        host.execute(ProgressionCommand.Play()).success()
        await { it is ProgressionPlayback.Playing }
        gate.open()
        naming.await().success(); editing.await().success()
        assertTrue(host.current.value.playback is ProgressionPlayback.Stopped)
        assertEquals(NoteDuration(NoteValue.Whole, true), host.current.value.draft.content.steps.first().duration)
    }

    @Test fun cancelledQueuedStartsFinishAuthorizedPreparationForBothTools(): Unit = runBlocking {
        startGate.lock()
        val progressionStart = launch(Dispatchers.Main, start = CoroutineStart.UNDISPATCHED) { host.execute(ProgressionCommand.Play()) }
        await { it == ProgressionPlayback.Preparing }
        progressionStart.cancel()
        startGate.unlock()
        await { it is ProgressionPlayback.Playing }
        progressionStart.join()
        host.execute(ProgressionCommand.Stop).success()
        startGate.lock()
        val metronomeStart = launch(Dispatchers.Main, start = CoroutineStart.UNDISPATCHED) { metronome.execute(MetronomeCommand.Start) }
        withTimeout(5_000) { metronome.current.first { it.playback == PlaybackState.Preparing } }
        metronomeStart.cancel()
        startGate.unlock()
        withTimeout(5_000) { metronome.current.first { it.playback is PlaybackState.Playing } }
        metronomeStart.join()
    }

    @Test fun switchingToolsStopsAPausedProgressionAndPreparingAuthorityCannotLeak(): Unit = runBlocking {
        host.execute(ProgressionCommand.Play()).success()
        await { it is ProgressionPlayback.Playing }
        host.execute(ProgressionCommand.Pause).success()
        metronome.execute(MetronomeCommand.Start)
        withTimeout(5_000) { metronome.current.first { it.playback is PlaybackState.Playing } }
        assertTrue(host.current.value.playback is ProgressionPlayback.Stopped)
        host.execute(ProgressionCommand.Resume).success()
        assertTrue(host.current.value.playback is ProgressionPlayback.Stopped)
        host.execute(ProgressionCommand.Play()).success()
        await { it is ProgressionPlayback.Playing }
        assertTrue(metronome.current.value.playback is PlaybackState.Stopped)
    }

    @Test fun cancelledAcceptedPauseAndResumeFinishNativeAndForegroundAcknowledgment(): Unit = runBlocking {
        lateinit var output: HeldProgressionOutput
        host = AndroidProgressionsHost(application, ProgressionPlaybackService::class.java, MainActivity::class.java, preferences,
            outputFactory = ProgressionOutputFactory { _, _, _, onPosition, _, _, _ ->
                HeldProgressionOutput(onPosition).also { output = it }
            })
        application.graphOverride = createGraphFactory<AppGraph.Factory>().create(application, metronome, host)
        host.execute(ProgressionCommand.Play()).success()
        await { it is ProgressionPlayback.Playing }
        val pause = launch(Dispatchers.Main) { host.execute(ProgressionCommand.Pause) }
        withTimeout(5_000) { output.pauseEntered.await() }
        pause.cancel()
        output.pauseAck.complete(Unit)
        pause.join()
        assertTrue(output.paused)
        assertTrue(host.current.value.playback is ProgressionPlayback.Paused)
        assertEquals(android.media.session.PlaybackState.STATE_PAUSED, MediaController(application, token()).playbackState?.state)
        val resume = launch(Dispatchers.Main) { host.execute(ProgressionCommand.Resume) }
        withTimeout(5_000) { output.resumeEntered.await() }
        resume.cancel()
        output.resumeAck.complete(Unit)
        resume.join()
        assertFalse(output.paused)
        assertTrue(host.current.value.playback is ProgressionPlayback.Playing)
        assertEquals(android.media.session.PlaybackState.STATE_PLAYING, MediaController(application, token()).playbackState?.state)
    }

    @Test fun failedNamedSaveDoesNotAcknowledgeARecordOrTargetAndRetryCommitsOnce(): Unit = runBlocking {
        host.execute(ProgressionCommand.SetName("Practice")).success()
        preferences.failNext = true
        assertEquals(Either.Left(ProgressionFailure.WriteFailed), host.execute(ProgressionCommand.Save))
        assertTrue(host.current.value.records.isEmpty())
        assertNull(host.current.value.draft.targetId)
        host.execute(ProgressionCommand.Save).success()
        val saved = host.current.value.records.single()
        assertEquals(saved.id, host.current.value.draft.targetId)
        host.execute(ProgressionCommand.SetName("Renamed")).success()
        host.execute(ProgressionCommand.Save).success()
        assertEquals(saved.id, host.current.value.records.single().id)
        preferences.failNext = true
        assertEquals(Either.Left(ProgressionFailure.WriteFailed), host.execute(ProgressionCommand.Delete(saved.id)))
        assertEquals(saved.id, host.current.value.records.single().id)
        host.execute(ProgressionCommand.Delete(saved.id)).success()
        assertTrue(host.current.value.records.isEmpty())
        assertNull(host.current.value.draft.targetId)
    }

    @Test fun noisyOutputStopsPausedPlaybackAndResumeCannotRestartIt(): Unit = runBlocking {
        host.execute(ProgressionCommand.Play()).success()
        await { it is ProgressionPlayback.Playing }
        host.execute(ProgressionCommand.Pause).success()
        application.sendBroadcast(Intent(AudioManager.ACTION_AUDIO_BECOMING_NOISY).setPackage(application.packageName))
        await { it == ProgressionPlayback.Stopped(StopReason.OutputDisconnected) }
        host.execute(ProgressionCommand.Resume).success()
        assertEquals(ProgressionPlayback.Stopped(StopReason.OutputDisconnected), host.current.value.playback)
    }

    @Test fun transientFocusLossStopsUntilTheNextManualPlay(): Unit = runBlocking {
        host.execute(ProgressionCommand.Play()).success()
        await { it is ProgressionPlayback.Playing }
        val manager = application.getSystemService(AudioManager::class.java)
        val interruption = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
            .setOnAudioFocusChangeListener { }.build()
        try {
            withContext(Dispatchers.Main) { assertEquals(AudioManager.AUDIOFOCUS_REQUEST_GRANTED, manager.requestAudioFocus(interruption)) }
            await { it == ProgressionPlayback.Stopped(StopReason.FocusLoss) }
            manager.abandonAudioFocusRequest(interruption)
            host.execute(ProgressionCommand.Resume).success()
            assertEquals(ProgressionPlayback.Stopped(StopReason.FocusLoss), host.current.value.playback)
            host.execute(ProgressionCommand.Play()).success()
            await { it is ProgressionPlayback.Playing }
        } finally { manager.abandonAudioFocusRequest(interruption) }
    }

    @Test fun outputFailureIsTypedAndStaleCallbacksCannotStopTheManualRetry(): Unit = runBlocking {
        var fail: () -> Unit = {}
        var disconnect: () -> Unit = {}
        host = AndroidProgressionsHost(application, ProgressionPlaybackService::class.java, MainActivity::class.java, preferences,
            outputFactory = ProgressionOutputFactory { _, _, _, onPosition, _, onDisconnected, onFailure ->
                fail = onFailure; disconnect = onDisconnected; HeldProgressionOutput(onPosition)
            })
        application.graphOverride = createGraphFactory<AppGraph.Factory>().create(application, metronome, host)
        host.execute(ProgressionCommand.Play()).success()
        await { it is ProgressionPlayback.Playing }
        val staleFailure = fail
        fail()
        await { it == ProgressionPlayback.Failed(ProgressionFailure.AudioUnavailable) }
        host.execute(ProgressionCommand.Play()).success()
        await { it is ProgressionPlayback.Playing }
        staleFailure()
        withContext(Dispatchers.Main) { }
        assertTrue(host.current.value.playback is ProgressionPlayback.Playing)
        disconnect()
        await { it == ProgressionPlayback.Stopped(StopReason.OutputDisconnected) }
    }

    private suspend fun await(predicate: (ProgressionPlayback) -> Boolean) {
        withTimeout(5_000) { host.current.first { predicate(it.playback) } }
    }
    private fun token(): MediaSession.Token = requireNotNull(BundleCompat.getParcelable(application
        .getSystemService(NotificationManager::class.java).activeNotifications.single { it.id == 2 }.notification.extras,
        android.app.Notification.EXTRA_MEDIA_SESSION, MediaSession.Token::class.java))
}

private fun Either<ProgressionFailure, Unit>.success() { assertEquals(Either.Right(Unit), this) }

private class HeldProgressionOutput(private val onPosition: (ProgressionPosition) -> Unit) : ProgressionOutput {
    override val routedDeviceId: Int? = null
    override val diagnostics = MetronomeAudioDiagnostics()
    val pauseEntered = CompletableDeferred<Unit>()
    val pauseAck = CompletableDeferred<Unit>()
    val resumeEntered = CompletableDeferred<Unit>()
    val resumeAck = CompletableDeferred<Unit>()
    var paused = false
    private val position = ProgressionPosition(0)
    override fun start(scope: CoroutineScope) { onPosition(position) }
    override suspend fun pause(): ProgressionPosition {
        paused = true; pauseEntered.complete(Unit); pauseAck.await(); return position
    }
    override suspend fun resume() { paused = false; resumeEntered.complete(Unit); resumeAck.await() }
    override fun stop() { pauseAck.cancel(); resumeAck.cancel() }
    override fun setTempo(bpm: Int) = Unit
    override fun setMetronome(enabled: Boolean) = Unit
}
