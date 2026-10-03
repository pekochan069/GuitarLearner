package com.pekochan069.guitarlearner

import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import android.content.SharedPreferences
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import arrow.core.Either
import com.pekochan069.guitarlearner.adapters.AndroidMetronomeHost
import com.pekochan069.guitarlearner.domain.BeatAccent
import com.pekochan069.guitarlearner.domain.BeatUnit
import com.pekochan069.guitarlearner.domain.MetronomeCommand
import com.pekochan069.guitarlearner.domain.MetronomeConfig
import com.pekochan069.guitarlearner.domain.MetronomeFailure
import com.pekochan069.guitarlearner.domain.MetronomePreset
import com.pekochan069.guitarlearner.domain.PlaybackState
import java.lang.reflect.Proxy
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
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
