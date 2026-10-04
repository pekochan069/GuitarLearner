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
import android.os.Process
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.lifecycle.Lifecycle
import arrow.core.Either
import com.pekochan069.guitarlearner.adapters.AndroidMetronomeHost
import com.pekochan069.guitarlearner.domain.BeatAccent
import com.pekochan069.guitarlearner.domain.BeatUnit
import com.pekochan069.guitarlearner.domain.MetronomeCommand
import com.pekochan069.guitarlearner.domain.MetronomeConfig
import com.pekochan069.guitarlearner.domain.MetronomeFailure
import com.pekochan069.guitarlearner.domain.MetronomePreset
import com.pekochan069.guitarlearner.domain.PlaybackState
import com.pekochan069.guitarlearner.domain.StopReason
import java.lang.reflect.Proxy
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class MetronomePlaybackTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    private fun host(preferences: SharedPreferences): AndroidMetronomeHost = AndroidMetronomeHost(
        compose.activity.application, MetronomePlaybackService::class.java, MainActivity::class.java, preferences,
    )

    @Test
    fun presetsRoundTripAndAFailedOverwriteOrDeleteRetainsTheSavedData(): Unit = runBlocking {
        val preferences = compose.activity.getSharedPreferences("metronome_storage_test", Application.MODE_PRIVATE)
        assertTrue(preferences.edit().clear().commit())
        val failure = FailingPreferenceEditor(preferences)
        val original = host(failure.preferences)
        val pattern = listOf(BeatAccent.Accent, BeatAccent.Mute, BeatAccent.Normal, BeatAccent.Accent,
            BeatAccent.Normal, BeatAccent.Normal, BeatAccent.Accent, BeatAccent.Normal)
        assertEquals(Either.Right(Unit), original.execute(MetronomeCommand.SetPattern(BeatUnit.Eighth, pattern)))
        assertEquals(Either.Right(Unit), original.execute(MetronomeCommand.SetTempo(137)))
        assertEquals(Either.Right(Unit), original.execute(MetronomeCommand.SavePreset(" 연습 ")))
        val expected = MetronomeConfig(137, BeatUnit.Eighth, pattern)
        assertEquals(Either.Left(MetronomeFailure.PresetExists), original.execute(MetronomeCommand.SavePreset("연습")))
        assertEquals(Either.Right(Unit), original.execute(MetronomeCommand.SetTempo(240)))
        failure.failNext = true
        assertEquals(Either.Left(MetronomeFailure.WriteFailed), original.execute(MetronomeCommand.SavePreset("연습", overwrite = true)))
        failure.failNext = true
        assertEquals(Either.Left(MetronomeFailure.WriteFailed), original.execute(MetronomeCommand.DeletePreset("연습")))
        val reloaded = host(preferences)
        assertEquals(listOf(MetronomePreset("연습", expected)), reloaded.current.value.presets)
        assertEquals(240, reloaded.current.value.selected.bpm)
        assertTrue(reloaded.current.value.playback is PlaybackState.Stopped)
        assertEquals(Either.Right(Unit), reloaded.execute(MetronomeCommand.LoadPreset("연습")))
        assertEquals(expected, reloaded.current.value.selected)
        assertTrue(preferences.edit().clear().commit())
    }

    @Test
    fun invalidStoredDenominatorAndVersionAreReportedWithoutOverwritingSource(): Unit = runBlocking {
        val preferences = compose.activity.getSharedPreferences("metronome_corruption_test", Application.MODE_PRIVATE)
        for (source in listOf(
            "{\"version\":1,\"selected\":{\"bpm\":120,\"denominator\":3,\"beats\":[\"Normal\"]},\"presets\":[]}",
            "{\"version\":1.9,\"selected\":{\"bpm\":120,\"denominator\":4,\"beats\":[\"Normal\"]},\"presets\":[]}",
        )) {
            assertTrue(preferences.edit().putString("document", source).commit())
            val broken = host(preferences)
            assertEquals(MetronomeFailure.ReadFailed, broken.current.value.readFailure)
            assertEquals(Either.Left(MetronomeFailure.ReadFailed), broken.execute(MetronomeCommand.SetTempo(140)))
            assertEquals(source, preferences.getString("document", null))
        }
        assertTrue(preferences.edit().clear().commit())
    }

    @Test
    fun stopDuringPreparationAndRapidRestartCannotPublishAnOldRun(): Unit = runBlocking {
        val host = (compose.activity.application as GuitarLearnerApplication).graph.metronomeHost
        val original = host.current.value.selected
        try {
            assertEquals(Either.Right(Unit), host.execute(MetronomeCommand.SetTempo(40)))
            assertEquals(Either.Right(Unit), host.execute(MetronomeCommand.SetPattern(BeatUnit.Quarter,
                listOf(BeatAccent.Mute, BeatAccent.Normal, BeatAccent.Normal, BeatAccent.Normal))))
            repeat(3) {
                assertEquals(Either.Right(Unit), host.execute(MetronomeCommand.Start))
                assertEquals(Either.Right(Unit), host.execute(MetronomeCommand.Stop))
            }
            delay(500)
            assertTrue(host.current.value.playback is PlaybackState.Stopped)
            withTimeout(5_000) {
                while (Thread.getAllStackTraces().keys.any { it.name == "MetronomeAudio" && it.isAlive }) delay(25)
            }
            assertEquals(Either.Right(Unit), host.execute(MetronomeCommand.Start))
            val result = withTimeout(10_000) { host.current.first { it.playback is PlaybackState.Playing || it.playback is PlaybackState.Failed } }
            assertTrue("Expected playback, received ${result.playback}", result.playback is PlaybackState.Playing)
            val playing = result.playback as PlaybackState.Playing
            assertEquals(0, playing.beatIndex)
            assertEquals(BeatAccent.Mute, playing.config.beats.first())
            val manager = compose.activity.getSystemService(NotificationManager::class.java)
            val notification = manager.activeNotifications.first { it.id == 1 }.notification
            assertEquals("android.app.Notification\$MediaStyle", notification.extras.getString(Notification.EXTRA_TEMPLATE))
            assertTrue(notification.extras.containsKey(Notification.EXTRA_MEDIA_SESSION))
            notification.actions.single().actionIntent.send()
            withTimeout(5_000) { host.current.first { it.playback is PlaybackState.Stopped } }
        } finally {
            assertEquals(Either.Right(Unit), host.execute(MetronomeCommand.Stop))
            assertEquals(Either.Right(Unit), host.execute(MetronomeCommand.SetPattern(original.denominator, original.beats)))
            assertEquals(Either.Right(Unit), host.execute(MetronomeCommand.SetTempo(original.bpm)))
        }
        delay(250)
        assertTrue(host.current.value.playback is PlaybackState.Stopped)
        assertEquals(null, host.currentAudioDiagnostics())
    }

    @Test
    fun signatureEditsRestartTheNativeOutputAtBeatZeroAndStoppedEditsStayStopped(): Unit = runBlocking {
        val application = compose.activity.application as GuitarLearnerApplication
        val host = application.graph.metronomeHost
        val original = host.current.value.selected
        val presetName = "Issue 7 native restart ${System.nanoTime()}"
        val preset = MetronomeConfig(137, BeatUnit.Sixteenth,
            listOf(BeatAccent.Mute, BeatAccent.Accent, BeatAccent.Normal, BeatAccent.Mute, BeatAccent.Normal))
        try {
            host.execute(MetronomeCommand.Stop).assertNativeSuccess()
            host.execute(MetronomeCommand.SetPattern(preset.denominator, preset.beats)).assertNativeSuccess()
            host.execute(MetronomeCommand.SetTempo(preset.bpm)).assertNativeSuccess()
            assertTrue(host.current.value.playback is PlaybackState.Stopped)
            assertEquals(null, host.currentAudioDiagnostics())
            host.execute(MetronomeCommand.SavePreset(presetName, overwrite = true)).assertNativeSuccess()
            host.execute(MetronomeCommand.SetPattern(BeatUnit.Quarter, MetronomeConfig().beats)).assertNativeSuccess()
            host.execute(MetronomeCommand.SetTempo(40)).assertNativeSuccess()
            host.execute(MetronomeCommand.Start).assertNativeSuccess()
            assertEquals(0, expectPlaying(host).beatIndex)
            val service = requireNotNull(runningOwnMetronomeService(application))

            val seven = List(7) { BeatAccent.Mute }
            for (unit in listOf(BeatUnit.Quarter, BeatUnit.Eighth)) {
                withTimeout(5_000) { host.current.first { (it.playback as? PlaybackState.Playing)?.beatIndex == 1 } }
                host.execute(MetronomeCommand.SetPattern(unit, seven)).assertNativeSuccess()
                val expected = MetronomeConfig(40, unit, seven)
                val restarted = withTimeout(5_000) {
                    host.current.first { (it.playback as? PlaybackState.Playing)?.config == expected || it.playback is PlaybackState.Failed }
                }.playback
                assertEquals(PlaybackState.Playing(expected, 0), restarted)
                assertEquals(service.activeSince, requireNotNull(runningOwnMetronomeService(application)).activeSince)
            }
            withTimeout(5_000) { host.current.first { (it.playback as? PlaybackState.Playing)?.beatIndex == 1 } }
            host.execute(MetronomeCommand.LoadPreset(presetName)).assertNativeSuccess()
            val loaded = withTimeout(5_000) {
                host.current.first { (it.playback as? PlaybackState.Playing)?.config == preset || it.playback is PlaybackState.Failed }
            }.playback
            assertEquals(PlaybackState.Playing(preset, 0), loaded)
            assertEquals(service.activeSince, requireNotNull(runningOwnMetronomeService(application)).activeSince)
        } finally {
            host.execute(MetronomeCommand.Stop).assertNativeSuccess()
            if (host.current.value.presets.any { it.name == presetName }) {
                host.execute(MetronomeCommand.DeletePreset(presetName)).assertNativeSuccess()
            }
            host.execute(MetronomeCommand.SetPattern(original.denominator, original.beats)).assertNativeSuccess()
            host.execute(MetronomeCommand.SetTempo(original.bpm)).assertNativeSuccess()
        }
    }

    @Test
    fun losingFocusStopsAndAbandoningTheInterruptionDoesNotResume(): Unit = runBlocking {
        val host = (compose.activity.application as GuitarLearnerApplication).graph.metronomeHost
        val original = host.current.value.selected
        val manager = compose.activity.getSystemService(AudioManager::class.java)
        val interruption = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
            .setOnAudioFocusChangeListener { }.build()
        try {
            assertEquals(Either.Right(Unit), host.execute(MetronomeCommand.Stop))
            assertEquals(Either.Right(Unit), host.execute(MetronomeCommand.SetTempo(40)))
            assertEquals(Either.Right(Unit), host.execute(MetronomeCommand.Start))
            assertEquals(0, expectPlaying(host).beatIndex)
            compose.runOnUiThread {
                assertEquals(AudioManager.AUDIOFOCUS_REQUEST_GRANTED, manager.requestAudioFocus(interruption))
            }
            withTimeout(5_000) {
                host.current.first { it.playback == PlaybackState.Stopped(StopReason.FocusLoss) }
            }
            assertEquals(null, host.currentAudioDiagnostics())
            manager.abandonAudioFocusRequest(interruption)
            delay(750)
            assertEquals(PlaybackState.Stopped(StopReason.FocusLoss), host.current.value.playback)
            assertEquals(Either.Right(Unit), host.execute(MetronomeCommand.Start))
            assertEquals(0, expectPlaying(host).beatIndex)
        } finally {
            manager.abandonAudioFocusRequest(interruption)
            assertEquals(Either.Right(Unit), host.execute(MetronomeCommand.Stop))
            assertEquals(Either.Right(Unit), host.execute(MetronomeCommand.SetTempo(original.bpm)))
        }
    }

    @Test
    fun playbackAdvancesInBackgroundAndSurvivesActivityRecreation(): Unit = runBlocking {
        val host = (compose.activity.application as GuitarLearnerApplication).graph.metronomeHost
        val original = host.current.value.selected
        val scenario = compose.activityRule.scenario
        try {
            assertEquals(Either.Right(Unit), host.execute(MetronomeCommand.Stop))
            assertEquals(Either.Right(Unit), host.execute(MetronomeCommand.SetTempo(40)))
            assertEquals(Either.Right(Unit), host.execute(MetronomeCommand.SetPattern(BeatUnit.Quarter,
                List(8) { BeatAccent.Mute })))
            assertEquals(Either.Right(Unit), host.execute(MetronomeCommand.Start))
            assertEquals(0, expectPlaying(host).beatIndex)
            scenario.moveToState(Lifecycle.State.CREATED)
            withTimeout(5_000) { host.current.first { (it.playback as? PlaybackState.Playing)?.beatIndex == 1 } }
            assertTrue(host.currentAudioDiagnostics() != null)
            scenario.moveToState(Lifecycle.State.RESUMED)
            scenario.recreate()
            assertSame(host, (compose.activity.application as GuitarLearnerApplication).graph.metronomeHost)
            val afterRecreation = host.current.value.playback
            assertTrue("Playback changed during recreation: $afterRecreation", afterRecreation is PlaybackState.Playing)
            assertTrue((afterRecreation as PlaybackState.Playing).beatIndex != 0)
            withTimeout(5_000) {
                host.current.first { (it.playback as? PlaybackState.Playing)?.beatIndex == afterRecreation.beatIndex + 1 }
            }
        } finally {
            scenario.moveToState(Lifecycle.State.RESUMED)
            assertEquals(Either.Right(Unit), host.execute(MetronomeCommand.Stop))
            assertEquals(Either.Right(Unit), host.execute(MetronomeCommand.SetPattern(original.denominator, original.beats)))
            assertEquals(Either.Right(Unit), host.execute(MetronomeCommand.SetTempo(original.bpm)))
        }
    }

    @Test
    fun staleStopLeavesPlaybackRunningAndTheUiStopRemovesTheStartedService(): Unit = runBlocking {
        val application = compose.activity.application as GuitarLearnerApplication
        val host = application.graph.metronomeHost
        val original = host.current.value.selected
        val serviceIntent = Intent(application, MetronomePlaybackService::class.java)
        try {
            assertEquals(Either.Right(Unit), host.execute(MetronomeCommand.Stop))
            withTimeout(5_000) { while (runningOwnMetronomeService(application) != null) delay(10) }
            assertEquals(Either.Right(Unit), host.execute(MetronomeCommand.SetTempo(40)))
            assertEquals(Either.Right(Unit), host.execute(MetronomeCommand.SetPattern(BeatUnit.Quarter,
                List(8) { BeatAccent.Mute })))
            compose.onNodeWithTag("page_Metronome").performClick()
            compose.onNodeWithTag("toggle_metronome").performScrollTo().performClick()
            expectPlaying(host)
            val started = requireNotNull(runningOwnMetronomeService(application))
            assertTrue(started.started)
            assertTrue(started.foreground)

            application.startService(Intent(serviceIntent)
                .setAction("com.pekochan069.guitarlearner.metronome.STOP").putExtra("run_id", -1L))
            withTimeout(5_000) {
                while ((runningOwnMetronomeService(application)?.lastActivityTime ?: 0L) <= started.lastActivityTime) delay(10)
            }
            val afterStaleStop = requireNotNull(runningOwnMetronomeService(application))
            assertEquals(started.activeSince, afterStaleStop.activeSince)
            assertTrue(afterStaleStop.started)
            val continuing = expectPlaying(host)
            withTimeout(5_000) {
                host.current.first {
                    val playback = it.playback
                    playback is PlaybackState.Playing && playback.beatIndex != continuing.beatIndex
                }
            }

            compose.onNodeWithTag("toggle_metronome").performScrollTo().performClick()
            withTimeout(5_000) { host.current.first { it.playback == PlaybackState.Stopped(StopReason.User) } }
            withTimeout(5_000) { while (runningOwnMetronomeService(application) != null) delay(10) }
            assertEquals(null, host.currentAudioDiagnostics())
        } finally {
            assertEquals(Either.Right(Unit), host.execute(MetronomeCommand.Stop))
            application.stopService(serviceIntent)
            assertEquals(Either.Right(Unit), host.execute(MetronomeCommand.SetPattern(original.denominator, original.beats)))
            assertEquals(Either.Right(Unit), host.execute(MetronomeCommand.SetTempo(original.bpm)))
        }
    }

    @Suppress("DEPRECATION")
    private fun runningOwnMetronomeService(application: Application): ActivityManager.RunningServiceInfo? {
        val component = ComponentName(application, MetronomePlaybackService::class.java)
        return application.getSystemService(ActivityManager::class.java).getRunningServices(Int.MAX_VALUE)
            .firstOrNull { it.service == component && it.pid == Process.myPid() }
    }

    private suspend fun expectPlaying(host: AndroidMetronomeHost): PlaybackState.Playing {
        val result = withTimeout(10_000) {
            host.current.first { it.playback is PlaybackState.Playing || it.playback is PlaybackState.Failed }
        }
        assertTrue("Expected playback, received ${result.playback}", result.playback is PlaybackState.Playing)
        return result.playback as PlaybackState.Playing
    }
}

private fun Either<MetronomeFailure, Unit>.assertNativeSuccess() {
    assertEquals(Either.Right(Unit), this)
}

private class FailingPreferenceEditor(private val delegate: SharedPreferences) {
    var failNext = false
    val preferences: SharedPreferences = Proxy.newProxyInstance(SharedPreferences::class.java.classLoader,
        arrayOf(SharedPreferences::class.java)) { _, method, arguments ->
        if (method.name != "edit") method.invoke(delegate, *(arguments ?: emptyArray())) else {
            val editor = delegate.edit()
            Proxy.newProxyInstance(SharedPreferences.Editor::class.java.classLoader,
                arrayOf(SharedPreferences.Editor::class.java)) { proxy, operation, values ->
                when (operation.name) {
                    "commit" -> {
                        val committed = editor.commit()
                        if (failNext) { failNext = false; false } else committed
                    }
                    else -> { operation.invoke(editor, *(values ?: emptyArray())); proxy }
                }
            }
        }
    } as SharedPreferences
}
