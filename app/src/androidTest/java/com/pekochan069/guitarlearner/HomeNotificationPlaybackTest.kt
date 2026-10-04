package com.pekochan069.guitarlearner

import android.app.ActivityManager
import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import android.content.ComponentName
import android.media.session.MediaSession
import android.os.Process
import androidx.compose.ui.test.junit4.v2.AndroidComposeTestRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.core.os.BundleCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import arrow.core.Either
import com.pekochan069.guitarlearner.adapters.AndroidMetronomeHost
import com.pekochan069.guitarlearner.domain.MetronomeCommand
import com.pekochan069.guitarlearner.domain.MetronomeFailure
import com.pekochan069.guitarlearner.domain.PlaybackState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HomeNotificationPlaybackTest {
    @get:Rule val compose = AndroidComposeTestRule(
        activityRule = NotificationActivityRule(),
        activityProvider = { it.activity },
    )

    @Test
    fun playbackNotificationReusesTheTaskAndPreservesTheRunAcrossHomeAndRecreation(): Unit = runBlocking {
        val activity = compose.activity
        val application = activity.application as GuitarLearnerApplication
        val host = application.graph.metronomeHost
        val original = host.current.value.selected
        try {
            host.execute(MetronomeCommand.Stop).assertNativeSuccess()
            host.execute(MetronomeCommand.SetTempo(40)).assertNativeSuccess()
            host.execute(MetronomeCommand.Start).assertNativeSuccess()
            expectPlaying(host)
            val service = requireNotNull(runningOwnMetronomeService(application))
            val manager = activity.getSystemService(NotificationManager::class.java)
            val notification = manager.activeNotifications.single { it.id == 1 }.notification
            val session = requireNotNull(BundleCompat.getParcelable(notification.extras,
                Notification.EXTRA_MEDIA_SESSION, MediaSession.Token::class.java))
            notification.contentIntent.send()
            compose.waitUntil(5_000) { compose.onAllNodesWithTag("bpm_value").fetchSemanticsNodes().isNotEmpty() }
            compose.runOnIdle {
                val resumed = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)
                    .filterIsInstance<MainActivity>().single()
                assertSame(activity, resumed)
                assertEquals(activity.taskId, resumed.taskId)
            }
            compose.onNodeWithTag("navigate_up").performClick()
            compose.onNodeWithTag("compact_metronome_open").performScrollTo().performClick()
            compose.onNodeWithTag("navigate_up").performClick()
            compose.activityRule.recreate()
            compose.onNodeWithTag("feature_Metronome").assertExists()
            compose.onNodeWithTag("bpm_value").assertDoesNotExist()
            assertEquals(service.activeSince, requireNotNull(runningOwnMetronomeService(application)).activeSince)
            assertEquals(session, BundleCompat.getParcelable(manager.activeNotifications.single { it.id == 1 }.notification.extras,
                Notification.EXTRA_MEDIA_SESSION, MediaSession.Token::class.java))
            assertTrue(host.current.value.playback is PlaybackState.Playing)
            host.execute(MetronomeCommand.Stop).assertNativeSuccess()
            compose.onNodeWithTag("compact_metronome").assertDoesNotExist()
            notification.contentIntent.send()
            compose.waitUntil(5_000) { compose.onAllNodesWithTag("bpm_value").fetchSemanticsNodes().isNotEmpty() }
            assertTrue(host.current.value.playback is PlaybackState.Stopped)
            assertEquals(null, host.currentAudioDiagnostics())
        } finally {
            host.execute(MetronomeCommand.Stop).assertNativeSuccess()
            host.execute(MetronomeCommand.SetPattern(original.denominator, original.beats)).assertNativeSuccess()
            host.execute(MetronomeCommand.SetTempo(original.bpm)).assertNativeSuccess()
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
