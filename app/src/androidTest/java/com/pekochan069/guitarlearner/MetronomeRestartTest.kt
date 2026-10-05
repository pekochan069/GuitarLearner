package com.pekochan069.guitarlearner

import android.app.ActivityManager
import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Intent
import android.content.SharedPreferences
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.session.MediaSession
import android.os.Process
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.core.os.BundleCompat
import com.pekochan069.guitarlearner.adapters.AndroidMetronomeHost
import com.pekochan069.guitarlearner.adapters.MetronomeAudioDiagnostics
import com.pekochan069.guitarlearner.adapters.MetronomeOutput
import com.pekochan069.guitarlearner.adapters.MetronomeOutputFactory
import com.pekochan069.guitarlearner.domain.BeatAccent
import com.pekochan069.guitarlearner.domain.BeatUnit
import com.pekochan069.guitarlearner.domain.MetronomeCommand
import com.pekochan069.guitarlearner.domain.MetronomeConfig
import com.pekochan069.guitarlearner.domain.MetronomeFailure
import com.pekochan069.guitarlearner.domain.MetronomeSequencer
import com.pekochan069.guitarlearner.domain.PlaybackState
import com.pekochan069.guitarlearner.domain.ScheduledBeat
import com.pekochan069.guitarlearner.domain.StopReason
import dev.zacsweers.metro.createGraphFactory
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class MetronomeRestartTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private lateinit var application: GuitarLearnerApplication
    private lateinit var host: AndroidMetronomeHost
    private lateinit var preferences: ControlledMetronomePreferences
    private lateinit var outputs: ControlledMetronomeOutputs

    @Before
    fun installControlledOutput(): Unit = runBlocking {
        application = compose.activity.application as GuitarLearnerApplication
        application.graph.metronomeHost.execute(MetronomeCommand.Stop).assertSuccess()
        awaitServiceRemoved()
        val stored = application.getSharedPreferences("metronome_restart_test", Application.MODE_PRIVATE)
        assertTrue(stored.edit().clear().commit())
        preferences = ControlledMetronomePreferences(stored)
        outputs = ControlledMetronomeOutputs()
        host = AndroidMetronomeHost(application, MetronomePlaybackService::class.java, MainActivity::class.java,
            preferences, outputFactory = outputs)
        application.graphOverride = createGraphFactory<AppGraph.Factory>().create(application, host)
    }

    @After
    fun restoreProductionGraph(): Unit = runBlocking {
        if (::preferences.isInitialized) preferences.releaseCommit()
        if (::outputs.isInitialized) outputs.created.forEach { it.acknowledgeStop = true }
        if (::host.isInitialized) {
            host.execute(MetronomeCommand.Stop).assertSuccess()
            awaitServiceRemoved()
        }
        if (::application.isInitialized) application.graphOverride = null
    }

    @Test
    fun numeratorAndDenominatorReplaceAudioWithoutReplacingTheForegroundSession(): Unit = runBlocking {
        val initial = startOutput()
        present(initial, 0)
        present(initial, 1)
        val service = requireNotNull(runningService())
        val mediaToken = requireNotNull(BundleCompat.getParcelable(notification().extras,
            Notification.EXTRA_MEDIA_SESSION, MediaSession.Token::class.java))

        host.execute(MetronomeCommand.SetPattern(BeatUnit.Quarter, List(7) { BeatAccent.Normal })).assertSuccess()
        assertEquals(PlaybackState.Preparing, host.current.value.playback)
        assertTrue(initial.stopped)
        val numerator = outputs.created.last()
        assertEquals(host.current.value.selected, numerator.initial)
        assertEquals(0, present(numerator, 0).beatIndex)

        host.execute(MetronomeCommand.SetPattern(BeatUnit.Eighth, numerator.initial.beats)).assertSuccess()
        assertEquals(PlaybackState.Preparing, host.current.value.playback)
        assertTrue(numerator.stopped)
        val denominator = outputs.created.last()
        assertEquals(BeatUnit.Eighth, present(denominator, 0).config.denominator)
        assertEquals(3, outputs.created.size)
        assertEquals(service.activeSince, requireNotNull(runningService()).activeSince)
        assertEquals(service.lastActivityTime, requireNotNull(runningService()).lastActivityTime)
        assertEquals(mediaToken, BundleCompat.getParcelable(notification().extras,
            Notification.EXTRA_MEDIA_SESSION, MediaSession.Token::class.java))
    }

    @Test
    fun aDifferentSignaturePresetRestartsWithItsWholeSavedConfigurationAndMutedFirstBeat(): Unit = runBlocking {
        val preset = MetronomeConfig(137, BeatUnit.Eighth,
            listOf(BeatAccent.Mute, BeatAccent.Accent, BeatAccent.Normal, BeatAccent.Mute,
                BeatAccent.Normal, BeatAccent.Accent, BeatAccent.Normal))
        host.execute(MetronomeCommand.SetPattern(preset.denominator, preset.beats)).assertSuccess()
        host.execute(MetronomeCommand.SetTempo(preset.bpm)).assertSuccess()
        host.execute(MetronomeCommand.SavePreset("Seven")).assertSuccess()
        host.execute(MetronomeCommand.SetPattern(BeatUnit.Quarter, MetronomeConfig().beats)).assertSuccess()
        host.execute(MetronomeCommand.SetTempo(90)).assertSuccess()
        val original = startOutput()
        present(original, 0)
        present(original, 1)

        host.execute(MetronomeCommand.LoadPreset("Seven")).assertSuccess()
        assertEquals(PlaybackState.Preparing, host.current.value.playback)
        val replacement = outputs.created.last()
        assertEquals(preset, replacement.initial)
        val first = present(replacement, 0)
        assertEquals(preset, first.config)
        assertEquals(BeatAccent.Mute, first.config.beats.first())
        assertTrue(original.stopped)
    }

    @Test
    fun tempoAccentsAndSameSignaturePresetsKeepTheirExistingBeatAndBarBoundaries(): Unit = runBlocking {
        val preset = MetronomeConfig(137, beats = List(4) { BeatAccent.Mute })
        host.execute(MetronomeCommand.SetTempo(preset.bpm)).assertSuccess()
        host.execute(MetronomeCommand.SetPattern(preset.denominator, preset.beats)).assertSuccess()
        host.execute(MetronomeCommand.SavePreset("Same meter")).assertSuccess()
        host.execute(MetronomeCommand.SetTempo(90)).assertSuccess()
        host.execute(MetronomeCommand.SetPattern(BeatUnit.Quarter, MetronomeConfig().beats)).assertSuccess()
        val output = startOutput()
        present(output, 0)
        host.execute(MetronomeCommand.SetTempo(140)).assertSuccess()
        assertEquals(140, present(output, 1).config.bpm)
        val accents = listOf(BeatAccent.Normal, BeatAccent.Accent, BeatAccent.Mute, BeatAccent.Normal)
        host.execute(MetronomeCommand.SetPattern(BeatUnit.Quarter, accents)).assertSuccess()
        assertEquals(MetronomeConfig().beats, present(output, 2).config.beats)
        present(output, 3)
        assertEquals(accents, present(output, 0).config.beats)
        host.execute(MetronomeCommand.LoadPreset("Same meter")).assertSuccess()
        assertEquals(140, present(output, 1).config.bpm)
        present(output, 2)
        present(output, 3)
        assertEquals(preset, present(output, 0).config)
        assertEquals(1, outputs.created.size)
        assertFalse(output.stopped)
    }

    @Test
    fun stoppedEditsAndFailedSavesDoNotStartOrReplaceAudio(): Unit = runBlocking {
        val seven = List(7) { BeatAccent.Normal }
        host.execute(MetronomeCommand.SetPattern(BeatUnit.Eighth, seven)).assertSuccess()
        assertTrue(host.current.value.playback is PlaybackState.Stopped)
        assertTrue(outputs.created.isEmpty())
        assertEquals(null, runningService())
        val output = startOutput()
        val playing = present(output, 0)
        preferences.failNext = true
        assertEquals(arrow.core.Either.Left(MetronomeFailure.WriteFailed),
            host.execute(MetronomeCommand.SetPattern(BeatUnit.Quarter, MetronomeConfig().beats)))
        assertEquals(playing.config, host.current.value.selected)
        assertEquals(playing, host.current.value.playback)
        assertEquals(1, outputs.created.size)
        assertFalse(output.stopped)
    }

    @Test
    fun editsDuringPreparationUseTheLatestSavedConfigAndRetiringCallbacksCannotWin(): Unit = runBlocking {
        val initial = startOutput()
        host.execute(MetronomeCommand.SetPattern(BeatUnit.Quarter, List(7) { BeatAccent.Normal })).assertSuccess()
        val retiring = outputs.created.last()
        host.execute(MetronomeCommand.SetPattern(BeatUnit.Eighth, List(7) { BeatAccent.Mute })).assertSuccess()
        val latest = outputs.created.last()
        assertEquals(3, outputs.created.size)
        initial.nextBeat()
        initial.fail()
        retiring.nextBeat()
        retiring.disconnect()
        retiring.fail()
        withContext(Dispatchers.Main) { }
        assertEquals(PlaybackState.Preparing, host.current.value.playback)
        assertEquals(host.current.value.selected, present(latest, 0).config)
        host.execute(MetronomeCommand.Stop).assertSuccess()
        latest.nextBeat()
        latest.fail()
        withContext(Dispatchers.Main) { }
        assertEquals(PlaybackState.Stopped(StopReason.User), host.current.value.playback)
    }

    @Test
    fun stopDuringASignatureSavePublishesOnlySelection(): Unit = runBlocking {
        val output = startOutput()
        present(output, 0)
        val gate = preferences.blockNextCommit()
        val edit = async(Dispatchers.Default) {
            host.execute(MetronomeCommand.SetPattern(BeatUnit.Eighth, List(7) { BeatAccent.Mute }))
        }
        withTimeout(5_000) { gate.entered.await() }
        host.execute(MetronomeCommand.Stop).assertSuccess()
        gate.open()
        edit.await().assertSuccess()
        output.nextBeat()
        withContext(Dispatchers.Main) { }
        assertEquals(7, host.current.value.selected.numerator)
        assertEquals(PlaybackState.Stopped(StopReason.User), host.current.value.playback)
        assertEquals(1, outputs.created.size)
    }

    @Test
    fun anEditWaitingForTheMutationLockCannotReplaceALaterManualStart(): Unit = runBlocking {
        val original = startOutput()
        present(original, 0)
        val gate = preferences.blockNextCommit()
        val first = async(Dispatchers.Default) { host.execute(MetronomeCommand.SetTempo(120)) }
        withTimeout(5_000) { gate.entered.await() }
        val scope = this
        val waiting = withContext(Dispatchers.Main.immediate) {
            scope.async(start = CoroutineStart.UNDISPATCHED) {
                host.execute(MetronomeCommand.SetPattern(BeatUnit.Eighth, List(7) { BeatAccent.Normal }))
            }
        }
        host.execute(MetronomeCommand.Stop).assertSuccess()
        awaitServiceRemoved()
        val manual = startOutput()
        present(manual, 0)
        gate.open()
        first.await().assertSuccess()
        waiting.await().assertSuccess()
        assertEquals(2, outputs.created.size)
        assertFalse(manual.stopped)
        assertEquals(120, present(manual, 1).config.bpm)
        present(manual, 2)
        present(manual, 3)
        assertEquals(host.current.value.selected, present(manual, 0).config)
        original.fail()
        original.disconnect()
        withContext(Dispatchers.Main) { }
        assertTrue(host.current.value.playback is PlaybackState.Playing)
    }

    @Test
    fun interruptionAndAudioFailureDuringSaveDoNotResumeAfterTheLateCommit(): Unit = runBlocking {
        for (failure in listOf(false, true)) {
            val output = startOutput()
            present(output, 0)
            val gate = preferences.blockNextCommit()
            val config = host.current.value.selected
            val edit = async(Dispatchers.Default) {
                host.execute(MetronomeCommand.SetPattern(
                    if (config.denominator == BeatUnit.Quarter) BeatUnit.Eighth else BeatUnit.Quarter, config.beats))
            }
            withTimeout(5_000) { gate.entered.await() }
            if (failure) output.fail() else output.disconnect()
            val interrupted = if (failure) PlaybackState.Failed(MetronomeFailure.AudioUnavailable)
                else PlaybackState.Stopped(StopReason.OutputDisconnected)
            awaitPlayback(interrupted)
            gate.open()
            edit.await().assertSuccess()
            output.nextBeat()
            withContext(Dispatchers.Main) { }
            assertEquals(interrupted, host.current.value.playback)
            awaitServiceRemoved()
        }
        assertEquals(2, outputs.created.size)
    }

    @Test
    fun focusLossDuringSaveAndServiceDestructionDuringPreparationCannotResume(): Unit = runBlocking {
        val output = startOutput()
        present(output, 0)
        val gate = preferences.blockNextCommit()
        val edit = async(Dispatchers.Default) {
            host.execute(MetronomeCommand.SetPattern(BeatUnit.Eighth, List(7) { BeatAccent.Normal }))
        }
        withTimeout(5_000) { gate.entered.await() }
        val manager = application.getSystemService(AudioManager::class.java)
        val interruption = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
            .setOnAudioFocusChangeListener { }.build()
        try {
            withContext(Dispatchers.Main) {
                assertEquals(AudioManager.AUDIOFOCUS_REQUEST_GRANTED, manager.requestAudioFocus(interruption))
            }
            awaitPlayback(PlaybackState.Stopped(StopReason.FocusLoss))
            gate.open()
            edit.await().assertSuccess()
            manager.abandonAudioFocusRequest(interruption)
            withContext(Dispatchers.Main) { }
            assertEquals(PlaybackState.Stopped(StopReason.FocusLoss), host.current.value.playback)
            awaitServiceRemoved()
            val preparing = startOutput()
            assertTrue(application.stopService(Intent(application, MetronomePlaybackService::class.java)))
            awaitPlayback(PlaybackState.Stopped(StopReason.ServiceEnded))
            preparing.nextBeat()
            withContext(Dispatchers.Main) { }
            assertEquals(PlaybackState.Stopped(StopReason.ServiceEnded), host.current.value.playback)
        } finally {
            gate.open()
            manager.abandonAudioFocusRequest(interruption)
        }
    }

    @Test
    fun aFailedReplacementStopsAndRemainsManuallyRecoverable(): Unit = runBlocking {
        val original = startOutput()
        present(original, 0)
        outputs.failNextStart = true
        host.execute(MetronomeCommand.SetPattern(BeatUnit.Eighth, List(7) { BeatAccent.Mute })).assertSuccess()
        assertEquals(PlaybackState.Failed(MetronomeFailure.AudioUnavailable), host.current.value.playback)
        assertTrue(original.stopped)
        assertTrue(outputs.created.last().stopped)
        original.nextBeat()
        original.fail()
        withContext(Dispatchers.Main) { }
        assertEquals(PlaybackState.Failed(MetronomeFailure.AudioUnavailable), host.current.value.playback)
        awaitServiceRemoved()
        val recovered = startOutput()
        assertEquals(host.current.value.selected, present(recovered, 0).config)
    }

    private suspend fun startOutput(): ControlledMetronomeOutput {
        val count = outputs.created.size
        host.execute(MetronomeCommand.Start).assertSuccess()
        withTimeout(5_000) { while (outputs.created.size == count || !outputs.created.last().started) delay(10) }
        return outputs.created.last().also { assertTrue(it.started) }
    }

    @Test
    fun failedStopAcknowledgementCleansUpAndCannotReviveOrStartUntilRetrySucceeds(): Unit = runBlocking {
        val original = startOutput()
        present(original, 0)
        original.acknowledgeStop = false
        assertEquals(arrow.core.Either.Left(MetronomeFailure.AudioUnavailable), host.execute(MetronomeCommand.Stop))
        assertEquals(PlaybackState.Failed(MetronomeFailure.AudioUnavailable), host.current.value.playback)
        assertTrue(original.stopped)
        awaitServiceRemoved()
        assertTrue(application.getSystemService(NotificationManager::class.java).activeNotifications.none { it.id == 1 })
        original.nextBeat()
        original.fail()
        withContext(Dispatchers.Main) { }
        assertEquals(PlaybackState.Failed(MetronomeFailure.AudioUnavailable), host.current.value.playback)
        assertEquals(arrow.core.Either.Left(MetronomeFailure.AudioUnavailable), host.execute(MetronomeCommand.Start))
        assertEquals(1, outputs.created.size)
        original.acknowledgeStop = true
        host.execute(MetronomeCommand.Stop).assertSuccess()
        present(startOutput(), 0)
    }

    @Test
    fun failedReplacementStopNeverCreatesASuccessorOutput(): Unit = runBlocking {
        val original = startOutput()
        present(original, 0)
        original.acknowledgeStop = false
        host.execute(MetronomeCommand.SetPattern(BeatUnit.Eighth, List(7) { BeatAccent.Normal })).assertSuccess()
        assertEquals(PlaybackState.Failed(MetronomeFailure.AudioUnavailable), host.current.value.playback)
        assertEquals(1, outputs.created.size)
        awaitServiceRemoved()
        original.acknowledgeStop = true
        host.execute(MetronomeCommand.Stop).assertSuccess()
        present(startOutput(), 0)
    }

    private suspend fun present(output: ControlledMetronomeOutput, index: Int): PlaybackState.Playing {
        val beat = output.nextBeat()
        assertEquals(index, beat.beatIndex)
        val expected = PlaybackState.Playing(beat.config, index)
        awaitPlayback(expected)
        return expected
    }

    private suspend fun awaitPlayback(expected: PlaybackState) {
        withTimeout(5_000) { host.current.first { it.playback == expected } }
    }

    private suspend fun awaitServiceRemoved() {
        val notifications = application.getSystemService(NotificationManager::class.java)
        withTimeout(5_000) {
            while (runningService() != null || notifications.activeNotifications.any { it.id == 1 }) delay(10)
        }
    }

    @Suppress("DEPRECATION")
    private fun runningService(): ActivityManager.RunningServiceInfo? = application
        .getSystemService(ActivityManager::class.java).getRunningServices(Int.MAX_VALUE)
        .firstOrNull { it.service == ComponentName(application, MetronomePlaybackService::class.java) && it.pid == Process.myPid() }

    private fun notification(): Notification = application.getSystemService(NotificationManager::class.java)
        .activeNotifications.single { it.id == 1 }.notification
}

private fun arrow.core.Either<MetronomeFailure, Unit>.assertSuccess() {
    assertEquals(arrow.core.Either.Right(Unit), this)
}

private class ControlledMetronomeOutputs : MetronomeOutputFactory {
    val created = CopyOnWriteArrayList<ControlledMetronomeOutput>()
    var failNextStart = false

    override fun create(manager: AudioManager, config: MetronomeConfig, onBeat: (ScheduledBeat) -> Unit,
        onOutputDisconnected: () -> Unit, onFailure: () -> Unit): MetronomeOutput =
        ControlledMetronomeOutput(config, onBeat, onOutputDisconnected, onFailure, failNextStart)
            .also { failNextStart = false; created.add(it) }
}

private class ControlledMetronomeOutput(
    val initial: MetronomeConfig,
    private val onBeat: (ScheduledBeat) -> Unit,
    private val onDisconnect: () -> Unit,
    private val onFailure: () -> Unit,
    private val failStart: Boolean,
) : MetronomeOutput {
    private val sequencer = MetronomeSequencer(initial, 48_000)
    @Volatile var started = false
    @Volatile var stopped = false
    @Volatile var acknowledgeStop = true
    override val routedDeviceId: Int? = null
    override val diagnostics = MetronomeAudioDiagnostics()
    override fun start(scope: CoroutineScope) { started = true; if (failStart) onFailure() }
    override fun stop(): Boolean { stopped = true; return acknowledgeStop }
    override fun setTempo(bpm: Int) = sequencer.setTempo(bpm)
    override fun setPattern(denominator: BeatUnit, beats: List<BeatAccent>) = sequencer.setPattern(denominator, beats)
    override fun load(config: MetronomeConfig) = sequencer.setConfig(config)
    fun nextBeat(): ScheduledBeat = sequencer.nextBeat().also(onBeat)
    fun fail() = onFailure()
    fun disconnect() = onDisconnect()
}

private class ControlledMetronomePreferences(private val delegate: SharedPreferences) : SharedPreferences by delegate {
    @Volatile var failNext = false
    @Volatile private var nextGate: CommitGate? = null
    private var activeGate: CommitGate? = null
    fun blockNextCommit(): CommitGate = CommitGate().also { nextGate = it; activeGate = it }
    fun releaseCommit() { activeGate?.open() }

    override fun edit(): SharedPreferences.Editor {
        val editor = delegate.edit()
        return object : SharedPreferences.Editor by editor {
            override fun putString(key: String?, value: String?): SharedPreferences.Editor {
                editor.putString(key, value)
                return this
            }
            override fun remove(key: String?): SharedPreferences.Editor { editor.remove(key); return this }
            override fun commit(): Boolean {
                nextGate?.let { gate -> nextGate = null; gate.waitForRelease() }
                val saved = editor.commit()
                return if (failNext) { failNext = false; false } else saved
            }
        }
    }
}

private class CommitGate {
    val entered = CompletableDeferred<Unit>()
    private val released = CountDownLatch(1)
    fun open() = released.countDown()
    fun waitForRelease() {
        entered.complete(Unit)
        check(released.await(10, TimeUnit.SECONDS)) { "Test did not release the preference commit" }
    }
}
