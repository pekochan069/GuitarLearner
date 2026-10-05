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
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.pekochan069.guitarlearner.presentation.contract.*
import com.pekochan069.guitarlearner.ui.R as UiR
import com.pekochan069.guitarlearner.ui.demo.TrainingScreen
import com.pekochan069.guitarlearner.ui.theme.GuitarLearnerTheme
import java.util.Locale
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class TrainingLayoutTest {
    @get:Rule val compose = createComposeRule()

    @Test fun enlargedEnglishAndKoreanMenusKeepFourEqualFormatCardsAndExercisesAccessible() {
        val language = mutableStateOf("en")
        val dark = mutableStateOf(false)
        val page = mutableStateOf<TrainingPageUi>(TrainingPageUi.Root)
        val events = mutableListOf<TrainingEvent>()
        compose.setContent { CompositionLocalProvider(LocalContext provides localizedContext(language.value),
            LocalDensity provides Density(LocalDensity.current.density, 2f)) {
            GuitarLearnerTheme(dark.value) {
                Column(Modifier.width(320.dp).verticalScroll(rememberScrollState())) {
                    TrainingScreen(TrainingUiState(stage = TrainingStageUi.Navigation(page.value))) { event ->
                        events.add(event)
                        if (event is TrainingEvent.OpenFormat) page.value = TrainingPageUi.Exercises(event.format)
                    }
                }
            }
        } }
        for (locale in listOf("en", "ko")) for (theme in listOf(false, true)) {
            compose.runOnIdle { language.value = locale; dark.value = theme; page.value = TrainingPageUi.Root }
            val context = localizedContext(locale)
            val cards = TrainingRepresentationUi.entries.map { compose.onNodeWithTag("training_format_${it.name}").getUnclippedBoundsInRoot() }
            assertTrue(cards.all { it.right - it.left == cards.first().right - cards.first().left &&
                it.bottom - it.top == cards.first().bottom - cards.first().top })
            assertEquals(cards[0].top, cards[1].top)
            assertEquals(cards[2].top, cards[3].top)
            assertTrue(cards[1].left > cards[0].right && cards[2].top > cards[0].bottom)
            if (locale == "en") {
                val layouts = mutableListOf<TextLayoutResult>()
                compose.onNodeWithTag("training_format_label_Fretboard", useUnmergedTree = true)
                    .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
                val layout = layouts.single()
                assertTrue("Fretboard must fit on one line at enlarged text size", layout.getLineEnd(0, visibleEnd = true) >= "Fretboard".length)
            }
            for (format in TrainingRepresentationUi.entries) {
                compose.runOnIdle { page.value = TrainingPageUi.Root }
                val title = when (format) {
                    TrainingRepresentationUi.Listening -> UiR.string.training_listening_menu
                    TrainingRepresentationUi.Staff -> UiR.string.training_staff_menu
                    TrainingRepresentationUi.Fretboard -> UiR.string.training_fretboard_menu
                    TrainingRepresentationUi.Tab -> UiR.string.training_tab_menu
                }
                compose.onNodeWithTag("training_format_${format.name}").performScrollTo().assertIsDisplayed()
                    .assertHasClickAction().assertHeightIsAtLeast(48.dp).assertWidthIsAtLeast(48.dp)
                    .assertTextEquals(context.getString(title)).performClick()
                assertEquals(TrainingEvent.OpenFormat(format), events.last())
                compose.onNodeWithTag("training_format_title").assertTextEquals(context.getString(title))
                for (subject in TrainingSubjectUi.entries) {
                    val exerciseTitle = if (subject == TrainingSubjectUi.Note) UiR.string.training_note_exercise else UiR.string.training_interval_exercise
                    compose.onNodeWithTag("training_exercise_${subject.name}").performScrollTo().assertIsDisplayed()
                        .assertHasClickAction().assertHeightIsAtLeast(48.dp).assertWidthIsAtLeast(48.dp)
                        .assertTextEquals(context.getString(exerciseTitle)).performClick()
                    assertEquals(TrainingEvent.OpenExercise(format, subject), events.last())
                }
                compose.onNodeWithTag("training_start").assertDoesNotExist()
                compose.onNodeWithTag("training_instrument").assertDoesNotExist()
            }
            compose.onNodeWithTag("training_format").assertDoesNotExist()
        }
    }

    @Test fun failedExerciseSettingsKeepStartDisabledAndRetryAvailable() {
        val state = TrainingUiState(settings = TrainingSettingsUi(subject = TrainingSubjectUi.Interval),
            stage = TrainingStageUi.Navigation(TrainingPageUi.Setup(TrainingRepresentationUi.Listening, TrainingSubjectUi.Interval)),
            settingsNotice = TrainingNoticeUi.SettingsWriteFailed)
        compose.setContent { GuitarLearnerTheme(false) {
            Column(Modifier.width(320.dp).verticalScroll(rememberScrollState())) { TrainingScreen(state) {} }
        } }
        compose.onNodeWithTag("training_start").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithTag("training_settings_retry").performScrollTo().assertHasClickAction()
    }

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

    @Test fun enlargedSameStepIntervalsKeepAccidentalsApartAndStaffScrollable() {
        val context = localizedContext("en")
        val stage = mutableStateOf(question(TrainingRepresentationUi.Staff))
        compose.setContent { CompositionLocalProvider(LocalContext provides context,
            LocalDensity provides Density(LocalDensity.current.density, 2f)) {
            GuitarLearnerTheme(false) {
                Column(Modifier.width(320.dp).verticalScroll(rememberScrollState())) {
                    TrainingScreen(TrainingUiState(stage = stage.value)) {}
                }
            }
        } }
        for ((step, accidentals) in listOf(-2 to listOf(TrainingAccidentalUi.Natural, TrainingAccidentalUi.Sharp),
            0 to listOf(TrainingAccidentalUi.Flat, TrainingAccidentalUi.Natural))) {
            compose.runOnIdle { stage.value = stage.value.copy(staff = accidentals.map { TrainingStaffNoteUi(step, it) }) }
            val bounds = compose.onNodeWithTag("training_staff").getUnclippedBoundsInRoot()
            val gap = (bounds.bottom - bounds.top) / 18
            val noteSeparation = ((bounds.right - bounds.left) - gap * 7) / 3
            assertTrue("A following accidental must clear the previous note head", noteSeparation > gap * 2.5f)
        }
        val staffScroll = compose.onNodeWithTag("training_staff_scroll")
        staffScroll.performSemanticsAction(SemanticsActions.ScrollBy) { it(999f, 0f) }
        assertTrue(staffScroll.fetchSemanticsNode().config[SemanticsProperties.HorizontalScrollAxisRange].value() > 0f)
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
