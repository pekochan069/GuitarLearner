package com.pekochan069.guitarlearner

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.pekochan069.guitarlearner.presentation.contract.FoundationEvent
import com.pekochan069.guitarlearner.presentation.contract.GuitarStringUi
import com.pekochan069.guitarlearner.presentation.contract.HeadstockLayoutUi
import com.pekochan069.guitarlearner.presentation.contract.TunerFeedbackUi
import com.pekochan069.guitarlearner.presentation.contract.TunerJudgmentUi
import com.pekochan069.guitarlearner.presentation.contract.TunerListeningUi
import com.pekochan069.guitarlearner.presentation.contract.TunerTargetUi
import com.pekochan069.guitarlearner.presentation.contract.TunerUiState
import com.pekochan069.guitarlearner.ui.demo.TunerScreen
import com.pekochan069.guitarlearner.ui.theme.GuitarLearnerTheme
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class TunerLayoutTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun pitchAndConfidenceChangesKeepTheReadingAndControlsInPlace() {
        val locale = mutableStateOf("en")
        val fontScale = mutableStateOf(1f)
        val state = mutableStateOf(TunerUiState())
        val events = mutableListOf<FoundationEvent>()
        compose.setContent {
            val configuration = Configuration(LocalConfiguration.current).apply {
                setLocale(Locale.forLanguageTag(locale.value))
            }
            val context = LocalContext.current.createConfigurationContext(configuration)
            CompositionLocalProvider(
                LocalContext provides context,
                LocalConfiguration provides configuration,
                LocalDensity provides Density(LocalDensity.current.density, fontScale.value),
            ) {
                GuitarLearnerTheme(false) {
                    Column(Modifier.width(320.dp).verticalScroll(rememberScrollState()).padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(24.dp)) {
                        TunerScreen(state.value) { event ->
                            events += event
                            state.value = when (event) {
                                is FoundationEvent.SelectHeadstockLayout -> state.value.copy(headstockLayout = event.value)
                                is FoundationEvent.SelectTunerTarget -> state.value.copy(target = event.value)
                                else -> state.value
                            }
                        }
                    }
                }
            }
        }
        val feedbacks = GuitarStringUi.entries.map {
            TunerFeedbackUi.Measured(it, -24.3, TunerJudgmentUi.Low)
        } + TunerJudgmentUi.entries.map {
            TunerFeedbackUi.Measured(GuitarStringUi.E2, 4.2, it)
        } + listOf(-0.1, 0.1).map {
            TunerFeedbackUi.Measured(GuitarStringUi.E2, it, TunerJudgmentUi.Settling)
        } + listOf(TunerFeedbackUi.Uncertain, TunerFeedbackUi.PluckOneString, TunerFeedbackUi.WrongOctave)
        for (language in listOf("en", "ko")) for (scale in listOf(1f, 2f)) for (layout in HeadstockLayoutUi.entries) {
            compose.runOnIdle {
                locale.value = language
                fontScale.value = scale
                state.value = TunerUiState(headstockLayout = layout)
            }
            compose.onNodeWithTag("tuner_headstock_" + layout.name).performScrollTo()
                .assertIsSelected().assertHasClickAction().assertHeightIsAtLeast(48.dp)
                .assertTextEquals(when (layout) {
                    HeadstockLayoutUi.ThreePlusThree -> "3+3"
                    HeadstockLayoutUi.InlineSix -> if (language == "ko") "일렬 6" else "Inline 6"
                }).assertContentDescriptionEquals(when (layout) {
                    HeadstockLayoutUi.ThreePlusThree -> if (language == "ko") "3+3 헤드스톡" else "3+3 headstock"
                    HeadstockLayoutUi.InlineSix -> if (language == "ko") "일렬 6 헤드스톡" else "Inline 6 headstock"
                }).performClick()
            assertEquals(FoundationEvent.SelectHeadstockLayout(layout), events.last())
            compose.onNodeWithTag("tuner_auto").performScrollTo().assertIsSelected()
            val note = compose.onNodeWithTag("tuner_note").getUnclippedBoundsInRoot()
            val controls = compose.onNodeWithTag("tuner_auto").getUnclippedBoundsInRoot()
            for (feedback in feedbacks) {
                compose.runOnIdle { state.value = state.value.copy(listening = TunerListeningUi.Listening(feedback)) }
                assertEquals("Controls moved for $language/$scale/$layout/$feedback", controls,
                    compose.onNodeWithTag("tuner_auto").getUnclippedBoundsInRoot())
                assertEquals("Note moved for $language/$scale/$layout/$feedback", note,
                    compose.onNodeWithTag("tuner_note").getUnclippedBoundsInRoot())
                if (feedback !is TunerFeedbackUi.Measured) {
                    compose.onNodeWithTag("tuner_cents").assertDoesNotExist()
                    compose.onNodeWithTag("tuner_note").assertTextEquals("--")
                } else {
                    val string = feedback.string
                    compose.onNodeWithTag("tuner_note").assertContentDescriptionEquals(if (language == "ko")
                        "${string.number}번 줄 · ${string.note}${string.octave}" else "String ${string.number} · ${string.note}${string.octave}")
                    if (feedback.cents in -0.1..0.1) {
                        compose.onNodeWithTag("tuner_cents").assertTextEquals(if (language == "ko") "+0센트" else "+0 cents")
                    }
                }
            }
            compose.onNodeWithTag("tuner_status").assertTextEquals(if (language == "ko")
                "선택한 개방현을 튕기세요" else "Pluck the selected open string")
            for (string in GuitarStringUi.entries) {
                compose.onNodeWithTag("tuner_string_" + string.name).performScrollTo()
                    .assertHeightIsAtLeast(48.dp).performClick().assertIsSelected()
                assertEquals(FoundationEvent.SelectTunerTarget(TunerTargetUi.Manual(string)), events.last())
                compose.onNodeWithTag("tuner_note").assertTextEquals(string.note + when (string.octave) {
                    2 -> "₂"
                    3 -> "₃"
                    else -> "₄"
                })
            }
            val other = HeadstockLayoutUi.entries.first { it != layout }
            compose.onNodeWithTag("tuner_headstock_" + other.name).performScrollTo().performClick().assertIsSelected()
            assertEquals(FoundationEvent.SelectHeadstockLayout(other), events.last())
            compose.onNodeWithTag("tuner_string_E4").assertIsSelected()
            compose.onNodeWithTag("tuner_note").assertTextEquals("E₄")
            compose.onNodeWithTag("tuner_auto").performScrollTo().performClick().assertIsSelected()
            assertEquals(FoundationEvent.SelectTunerTarget(TunerTargetUi.Automatic), events.last())
            compose.onNodeWithTag("tuner_note").assertTextEquals("--")
        }
    }
}
