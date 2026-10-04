package com.pekochan069.guitarlearner

import androidx.compose.ui.test.junit4.v2.AndroidComposeTestRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.pekochan069.guitarlearner.adapters.AndroidMetronomeHost
import com.pekochan069.guitarlearner.domain.MetronomeCommand
import com.pekochan069.guitarlearner.domain.PlaybackState
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.rules.ExternalResource

@RunWith(AndroidJUnit4::class)
class HomeColdNotificationTest {
    @get:Rule(order = 0) val stoppedMetronome = object : ExternalResource() {
        override fun before() = stopPlayback()
        override fun after() = stopPlayback()

        private fun stopPlayback(): Unit = runBlocking {
            ApplicationProvider.getApplicationContext<GuitarLearnerApplication>().graph.metronomeHost
                .execute(MetronomeCommand.Stop).fold({ error("Stop failed: $it") }, {})
        }
    }

    @get:Rule(order = 1) val compose = AndroidComposeTestRule(
        activityRule = NotificationActivityRule(AndroidMetronomeHost.ACTION_OPEN_METRONOME),
        activityProvider = { it.activity },
    )

    @Test
    fun coldNotificationHasHomeAsItsBackDestinationAndConsumedLaunchCannotReplay(): Unit {
        val host = (compose.activity.application as GuitarLearnerApplication).graph.metronomeHost
        compose.onNodeWithTag("bpm_value").assertExists()
        assertNull(compose.activity.intent.action)
        compose.onNodeWithTag("navigate_up").performClick()
        compose.onNodeWithTag("feature_Metronome").assertExists()
        compose.activityRule.recreate()
        compose.onNodeWithTag("feature_Metronome").assertExists()
        compose.onNodeWithTag("bpm_value").assertDoesNotExist()
        assertTrue(host.current.value.playback is PlaybackState.Stopped)
        assertNull(host.currentAudioDiagnostics())
    }
}
