package com.pekochan069.guitarlearner

import android.content.Context
import android.content.res.Configuration
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.pekochan069.guitarlearner.presentation.contract.*
import com.pekochan069.guitarlearner.ui.R as UiR
import com.pekochan069.guitarlearner.ui.demo.TrainingScreen
import com.pekochan069.guitarlearner.ui.theme.GuitarLearnerTheme
import java.util.Locale
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class TrainingLayoutTest {
    @get:Rule val compose = createComposeRule()

    @Test fun lowestStaffPitchDescribesBelowTheStaffWithoutNamingTheAnswer() {
        val context = localizedContext("en")
        val question = question(TrainingRepresentationUi.Staff).copy(staff = listOf(TrainingStaffNoteUi(-7, TrainingAccidentalUi.Natural)))
        compose.setContent { CompositionLocalProvider(LocalContext provides context) {
            GuitarLearnerTheme(false) { TrainingScreen(TrainingUiState(stage = question)) {} }
        } }
        compose.onNodeWithTag("training_staff").assertContentDescriptionEquals(context.resources.getQuantityString(UiR.plurals.training_staff_description_below,
            7, 1, 7, context.getString(UiR.string.training_natural)))
        compose.onNodeWithTag("training_staff_octave").assertExists()
    }

    @Test fun coincidentUnisonAndSameStringPositionsHaveDistinctOrdinalDescriptions() {
        val context = localizedContext("en")
        val positions = listOf(TrainingPositionUi(6, 0), TrainingPositionUi(6, 0))
        val state = TrainingUiState(stage = question(TrainingRepresentationUi.Fretboard).copy(positions = positions))
        compose.setContent { CompositionLocalProvider(LocalContext provides context) {
            GuitarLearnerTheme(true) { TrainingScreen(state) {} }
        } }
        compose.onNodeWithTag("training_fretboard").assertContentDescriptionEquals(
            context.getString(UiR.string.training_position_description, 1, 6, 0) + ". " +
                context.getString(UiR.string.training_position_description, 2, 6, 0))
    }

    @Test fun koreanEnlargedTextKeepsAnswerControlsAndFeedbackReachable() {
        val context = localizedContext("ko")
        val stage = mutableStateOf(question(TrainingRepresentationUi.Tab))
        compose.setContent { CompositionLocalProvider(LocalContext provides context,
            LocalDensity provides Density(LocalDensity.current.density, 2f)) {
            GuitarLearnerTheme(true) {
                Column(Modifier.width(320.dp).verticalScroll(rememberScrollState())) {
                    TrainingScreen(TrainingUiState(stage = stage.value)) { event ->
                        if (event is TrainingEvent.Answer) stage.value = stage.value.copy(
                            feedback = TrainingFeedbackUi(chosen = event.answer, answer = event.answer, correct = true))
                    }
                }
            }
        } }
        compose.onNodeWithTag("training_tab").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("training_answer_CSharp").performScrollTo().assertIsDisplayed()
            .assertHeightIsAtLeast(48.dp).assertWidthIsAtLeast(48.dp).performClick()
        compose.onNodeWithTag("training_answer_CSharp").assertDoesNotExist()
        compose.onNodeWithTag("training_feedback").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("training_next").performScrollTo().assertIsDisplayed().assertHeightIsAtLeast(48.dp)
    }

    @Test fun highFretStartsInTheViewportWithFixedStringNumbers() {
        val stage = question(TrainingRepresentationUi.Fretboard).copy(positions = listOf(TrainingPositionUi(5, 11)))
        compose.setContent { GuitarLearnerTheme(false) {
            Column(Modifier.width(320.dp).verticalScroll(rememberScrollState())) { TrainingScreen(TrainingUiState(stage = stage)) {} }
        } }
        val canvas = compose.onNodeWithTag("training_fretboard").getUnclippedBoundsInRoot()
        val viewport = compose.onNodeWithTag("training_positions_scroll").getUnclippedBoundsInRoot()
        val markerCenter = canvas.left + (canvas.right - canvas.left) * (11.5f / 14)
        assertTrue("Fret 11 must be visible without scrolling", markerCenter > viewport.left && markerCenter < viewport.right)
        (1..6).forEach { compose.onNodeWithTag("training_string_$it").assertIsDisplayed() }
    }

    private fun question(representation: TrainingRepresentationUi): TrainingStageUi.Question = TrainingStageUi.Question(
        TrainingQuestionKeyUi(1, 0), 1, TrainingSettingsUi(representation = representation),
        listOf(TrainingPositionUi(6, 0)), listOf(TrainingStaffNoteUi(-7, TrainingAccidentalUi.Natural)),
        TrainingNoteUi.entries.map { TrainingAnswerUi.Note(it) }, null,
    )

    private fun localizedContext(language: String): Context {
        val context = ApplicationProvider.getApplicationContext<Context>()
        return context.createConfigurationContext(Configuration(context.resources.configuration).apply { setLocale(Locale.forLanguageTag(language)) })
    }
}
