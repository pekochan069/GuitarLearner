package com.pekochan069.guitarlearner

import android.content.res.Configuration
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DesignFoundationTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun preferencesAndDemoSelectionsSurviveRecreation() {
        compose.onNodeWithTag("settings").performClick()
        compose.onNodeWithTag("language_en").performScrollTo().performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("language_en").assertIsSelected()
        compose.onNodeWithTag("language_").performScrollTo().performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("language_").assertIsSelected()
        compose.onNodeWithTag("language_en").performScrollTo().performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("language_en").assertIsSelected()
        compose.onNodeWithTag("theme_Light").performScrollTo().performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("close_settings").performScrollTo().performClick()

        listOf(
            "NoSignal" to "No signal",
            "Flat" to "Flat",
            "InTune" to "In tune",
            "Sharp" to "Sharp",
        ).forEach { (reading, label) ->
            compose.onNodeWithTag("reading_" + reading).performScrollTo().performClick()
            compose.onNodeWithTag("tuner_status").assertTextEquals(label)
        }

        compose.onNodeWithTag("page_Metronome").performClick()
        compose.onNodeWithTag("increase_bpm").performScrollTo().performClick()
        compose.onNodeWithTag("bpm_value").assertTextEquals("91")
        compose.onNodeWithTag("metronome_status").assertTextEquals("Stopped · simulated")
        compose.onNodeWithTag("toggle_metronome").performScrollTo().performClick()
        compose.onNodeWithTag("increase_bpm").performScrollTo().performClick()
        compose.onNodeWithTag("bpm_value").assertTextEquals("92")
        compose.onNodeWithTag("metronome_status").assertTextEquals("Running · simulated")

        compose.onNodeWithTag("page_Gallery").performClick()
        compose.onNodeWithTag("gallery_selection").performScrollTo().assertIsOn().performClick()
        compose.onNodeWithTag("gallery_selection").assertIsOff()

        compose.onNodeWithTag("settings").performClick()
        compose.onNodeWithTag("theme_Dark").performScrollTo().performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("language_ko").performScrollTo().performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("close_settings").performScrollTo().performClick()

        compose.activityRule.scenario.recreate()
        compose.waitForIdle()
        compose.onNodeWithText("매일 편안하게").assertExists()
        compose.onNodeWithTag("gallery_selection").performScrollTo().assertIsOff()
        compose.onNodeWithTag("page_Tuner").performClick()
        compose.onNodeWithTag("tuner_status").assertTextEquals("높음")
        compose.onNodeWithTag("page_Metronome").performClick()
        compose.onNodeWithTag("bpm_value").assertTextEquals("92")
        compose.onNodeWithTag("metronome_status").assertTextEquals("실행 중 · 시뮬레이션")
        compose.onNodeWithTag("settings").performClick()
        compose.onNodeWithTag("theme_Dark").assertIsSelected()
        compose.onNodeWithTag("language_ko").performScrollTo().assertIsSelected()
        assertEquals(
            Configuration.UI_MODE_NIGHT_YES,
            compose.activity.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK,
        )
    }
}
