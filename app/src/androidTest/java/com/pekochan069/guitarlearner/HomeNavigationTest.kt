package com.pekochan069.guitarlearner

import android.content.Intent
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.v2.AndroidComposeTestRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import com.pekochan069.guitarlearner.adapters.AndroidMetronomeHost
import com.pekochan069.guitarlearner.domain.MetronomeCommand
import com.pekochan069.guitarlearner.domain.PlaybackState
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HomeNavigationTest {
    @get:Rule val compose = AndroidComposeTestRule(
        activityRule = NotificationActivityRule(),
        activityProvider = { it.activity },
    )

    @Test
    fun systemBackDismissesSettingsAndPresetsBeforeReturningHomeAndKeepsTheDraft(): Unit {
        compose.onNodeWithTag("feature_Metronome").assertExists()
        compose.openMetronome()
        compose.onNodeWithTag("settings").performClick()
        pressBack()
        compose.onNodeWithTag("close_settings").assertDoesNotExist()
        compose.onNodeWithTag("bpm_value").assertExists()
        compose.onNodeWithTag("open_presets").performScrollTo().performClick()
        compose.onNodeWithTag("preset_name").performTextInput("Next practice")
        pressBack()
        compose.onNodeWithTag("preset_name").assertDoesNotExist()
        compose.onNodeWithTag("bpm_value").assertExists()
        pressBack()
        compose.onNodeWithTag("feature_Metronome").assertExists()
        compose.openMetronome()
        compose.onNodeWithTag("open_presets").performScrollTo().performClick()
        compose.onNodeWithTag("preset_name").assertTextContains("Next practice")
    }

    @Test
    fun warmNotificationUsesTheExistingActivityAndConsumedInputDoesNotReplayAfterHomeAndRecreation(): Unit {
        val activity = compose.activity
        val host = (activity.application as GuitarLearnerApplication).graph.metronomeHost
        runBlocking { host.execute(MetronomeCommand.Stop).fold({ error("Stop failed: $it") }, {}) }
        compose.waitForIdle()
        compose.runOnIdle {
            activity.startActivity(Intent(activity, MainActivity::class.java)
                .setAction(AndroidMetronomeHost.ACTION_OPEN_METRONOME)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP))
        }
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("bpm_value").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("bpm_value").assertExists()
        compose.runOnIdle {
            val resumed = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)
                .filterIsInstance<MainActivity>().single()
            assertSame(activity, resumed)
            assertEquals(activity.taskId, resumed.taskId)
            assertNull(resumed.intent.action)
        }
        compose.onNodeWithTag("navigate_up").performClick()
        compose.onNodeWithTag("feature_Metronome").assertExists()
        compose.activityRule.recreate()
        compose.onNodeWithTag("feature_Metronome").assertExists()
        compose.onNodeWithTag("bpm_value").assertDoesNotExist()
        assertTrue(host.current.value.playback is PlaybackState.Stopped)
        assertNull(host.currentAudioDiagnostics())
    }

    @Test
    fun warmNotificationWhileAlreadyOnMetronomeDoesNotReplayWhenBackReturnsHome(): Unit {
        val activity = compose.activity
        val host = (activity.application as GuitarLearnerApplication).graph.metronomeHost
        runBlocking { host.execute(MetronomeCommand.Stop).fold({ error("Stop failed: $it") }, {}) }
        compose.openMetronome()
        compose.waitForIdle()
        val delivery = System.nanoTime()
        compose.runOnIdle {
            activity.startActivity(Intent(activity, MainActivity::class.java)
                .setAction(AndroidMetronomeHost.ACTION_OPEN_METRONOME)
                .putExtra("notification_test_delivery", delivery)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP))
        }
        compose.waitUntil(5_000) { activity.intent.getLongExtra("notification_test_delivery", 0) == delivery }
        compose.waitForIdle()
        pressBack()
        compose.onNodeWithTag("feature_Metronome").assertExists()
        compose.onNodeWithTag("bpm_value").assertDoesNotExist()
        compose.activityRule.recreate()
        compose.onNodeWithTag("feature_Metronome").assertExists()
        assertTrue(host.current.value.playback is PlaybackState.Stopped)
        assertNull(host.currentAudioDiagnostics())
    }

    private fun pressBack() {
        compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
    }
}
