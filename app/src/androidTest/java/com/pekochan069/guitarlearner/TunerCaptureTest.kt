package com.pekochan069.guitarlearner

import android.Manifest
import android.content.pm.PackageManager
import android.os.ParcelFileDescriptor
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.pekochan069.guitarlearner.adapters.AndroidTunerHost
import com.pekochan069.guitarlearner.domain.StandardString
import com.pekochan069.guitarlearner.domain.TunerListening
import com.pekochan069.guitarlearner.domain.TunerRequest
import com.pekochan069.guitarlearner.domain.TunerTarget
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class TunerCaptureTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Before fun grantMicrophone() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val result = ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand(
            "pm grant ${context.packageName} ${Manifest.permission.RECORD_AUDIO}",
        )).use { it.readBytes().decodeToString() }
        assertEquals("Microphone grant failed: $result", PackageManager.PERMISSION_GRANTED,
            context.checkSelfPermission(Manifest.permission.RECORD_AUDIO))
    }

    @Test
    fun actualCaptureSurvivesRecreationAndBackgroundResumeRequiresExplicitStart() {
        val owner = compose.runOnIdle { ViewModelProvider(compose.activity)[TunerSessionOwner::class.java] }
        val host = owner.host
        val scenario = compose.activityRule.scenario
        try {
            compose.openTuner()
            compose.onNodeWithTag("tuner_string_B3").performScrollTo().performClick()
            assertEquals(TunerListening.Stopped, host.current.value.listening)
            compose.onNodeWithTag("tuner_start").performScrollTo().performClick()
            expectListening(host)
            scenario.recreate()
            compose.waitForIdle()
            val recreated = compose.runOnIdle { ViewModelProvider(compose.activity)[TunerSessionOwner::class.java] }
            assertSame(owner, recreated)
            assertEquals(TunerTarget.Manual(StandardString.B3), recreated.host.current.value.target)
            assertTrue("Rotation ended native capture: ${host.current.value.listening}", host.current.value.listening is TunerListening.Listening)
            compose.onNodeWithTag("tuner_string_B3").performScrollTo().assertIsSelected()

            scenario.moveToState(Lifecycle.State.CREATED)
            expectStopped(host)
            scenario.moveToState(Lifecycle.State.RESUMED)
            compose.waitForIdle()
            runBlocking { delay(1_000) }
            assertEquals(TunerListening.Stopped, host.current.value.listening)
            compose.onNodeWithTag("tuner_stop").assertDoesNotExist()
            compose.onNodeWithTag("tuner_start").performScrollTo().performClick()
            expectListening(host)
        } finally {
            scenario.moveToState(Lifecycle.State.RESUMED)
            compose.runOnIdle { host.submit(TunerRequest.Stop) }
            expectStopped(host)
        }
    }

    private fun expectListening(host: AndroidTunerHost) {
        val state = runBlocking { withTimeout(5_000) {
            host.current.first { it.listening is TunerListening.Listening || it.listening is TunerListening.Failed }
        } }
        assertTrue("Native microphone did not start: ${state.listening}", state.listening is TunerListening.Listening)
    }

    private fun expectStopped(host: AndroidTunerHost) {
        val state = runBlocking { withTimeout(5_000) {
            host.current.first { it.listening == TunerListening.Stopped || it.listening is TunerListening.Failed }
        } }
        assertEquals(TunerListening.Stopped, state.listening)
    }
}
