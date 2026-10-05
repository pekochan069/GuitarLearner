package com.pekochan069.guitarlearner

import android.content.res.Configuration
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import com.pekochan069.guitarlearner.presentation.contract.DevelopmentSample
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.pekochan069.guitarlearner.ui.R as UiR
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DesignFoundationTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun preferencesAndToolSelectionsSurviveRecreation() {
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

        compose.openTuner()
        compose.onNodeWithTag("tuner_string_E4").performScrollTo().performClick()
        compose.onNodeWithTag("tuner_tolerance_3").performScrollTo().performClick()
        compose.waitUntil(5_000) {
            compose.onNodeWithTag("tuner_tolerance_3").fetchSemanticsNode().config[SemanticsProperties.Selected]
        }
        compose.onNodeWithTag("tuner_status").assertTextEquals("Stopped")
        compose.onNodeWithTag("tuner_cents").assertDoesNotExist()

        compose.openMetronome()
        compose.onNodeWithTag("tempo_slider").performScrollTo().performSemanticsAction(SemanticsActions.SetProgress) { it(91f) }
        waitForText("bpm_value", "91")
        compose.onNodeWithTag("bpm_value").assertTextEquals("91")
        compose.onNodeWithTag("metronome_status").assertDoesNotExist()
        compose.onNodeWithTag("toggle_metronome").assertTextEquals(compose.activity.getString(UiR.string.start_metronome))
        compose.onNodeWithTag("increase_bpm").performScrollTo().performClick()
        waitForText("bpm_value", "92")
        compose.onNodeWithTag("bpm_value").assertTextEquals("92")
        compose.onNodeWithTag("open_presets").performScrollTo().performClick()
        compose.onNodeWithTag("preset_name").performTextInput("연습 A")
        compose.onNodeWithTag("close_presets").performScrollTo().performClick()

        compose.openDevelopmentSample(DevelopmentSample.Gallery)
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
        compose.onNodeWithText(compose.activity.getString(UiR.string.gallery_title)).assertExists()
        compose.onNodeWithTag("gallery_selection").performScrollTo().assertIsOff()
        compose.openTuner()
        compose.onNodeWithTag("tuner_status").assertTextEquals("정지됨")
        compose.onNodeWithTag("tuner_string_E4").performScrollTo().assertIsSelected()
        compose.onNodeWithTag("tuner_tolerance_3").performScrollTo().assertIsSelected()
        compose.openMetronome()
        compose.onNodeWithTag("bpm_value").assertTextEquals("92")
        compose.onNodeWithTag("metronome_status").assertDoesNotExist()
        compose.onNodeWithTag("toggle_metronome").assertTextEquals(compose.activity.getString(UiR.string.start_metronome))
        compose.onNodeWithTag("open_presets").performScrollTo().performClick()
        compose.onNodeWithTag("preset_name").assertTextContains("연습 A")
        compose.activityRule.scenario.recreate()
        compose.onNodeWithTag("preset_name").assertTextContains("연습 A")
        compose.onNodeWithTag("close_presets").performScrollTo().performClick()
        compose.onNodeWithTag("settings").performClick()
        compose.onNodeWithTag("theme_Dark").assertIsSelected()
        compose.onNodeWithTag("language_ko").performScrollTo().assertIsSelected()
        assertEquals(
            Configuration.UI_MODE_NIGHT_YES,
            compose.activity.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK,
        )
    }

    private fun waitForText(tag: String, value: String): Unit {
        compose.waitUntil(5_000) {
            compose.onNodeWithTag(tag).fetchSemanticsNode().config[SemanticsProperties.Text].any { it.text == value }
        }
    }
}
