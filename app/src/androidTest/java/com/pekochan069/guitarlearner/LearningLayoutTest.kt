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
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertTextContains
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
import com.pekochan069.guitarlearner.ui.demo.LearningScreen
import com.pekochan069.guitarlearner.ui.theme.GuitarLearnerTheme
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class LearningLayoutTest {
    @get:Rule val compose = createComposeRule()

    @Test fun enlargedKoreanAndEnglishKeepLessonReadableAndOfferBothRecoveryActions() {
        val language = mutableStateOf("en")
        val fontScale = mutableStateOf(2f)
        val page = mutableStateOf<LearningUiPage>(LearningUiPage.Lesson(LessonUi.Scales))
        val events = mutableListOf<LearningEvent>()
        compose.setContent {
            CompositionLocalProvider(
                LocalContext provides localizedContext(language.value),
                LocalDensity provides Density(LocalDensity.current.density, fontScale.value),
            ) {
                GuitarLearnerTheme(false) {
                    Column(Modifier.width(320.dp).verticalScroll(rememberScrollState())) {
                        LearningScreen(LearningUiState(
                            page = page.value,
                            roots = listOf("C", "G", "F"),
                            circle = circle,
                            notes = listOf(LearningNoteUi("C", "1"), LearningNoteUi("E", "3"), LearningNoteUi("G", "5")),
                            frets = listOf(LearningFretUi(5, 3, "C", "1")),
                            audio = LearningAudioUi.Failed,
                            audioNotice = LearningNoticeUi.PlaybackFailed,
                            saveNotice = LearningNoticeUi.WriteFailed,
                        ), events::add)
                    }
                }
            }
        }
        for (locale in listOf("en", "ko")) {
            compose.runOnIdle { language.value = locale; page.value = LearningUiPage.Lesson(LessonUi.Scales) }
            compose.onNodeWithTag("learning_explanation").assertExists()
            val layouts = mutableListOf<TextLayoutResult>()
            compose.onNodeWithTag("learning_explanation")
                .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
            assertFalse("$locale explanation clips at 200% text size", layouts.single().hasVisualOverflow)
            val goals = mutableListOf<TextLayoutResult>()
            compose.onNodeWithTag("learning_lesson_goal")
                .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(goals) }
            assertFalse("$locale lesson goal clips at 200% text size", goals.single().hasVisualOverflow)
            compose.onNodeWithTag("learning_notes").assertTextContains("C (1)", substring = true)
                .assertTextContains("E (3)", substring = true).assertTextContains("G (5)", substring = true)
            compose.onNodeWithTag("learning_retry_save").performScrollTo().assertHasClickAction()
                .assertHeightIsAtLeast(48.dp).performClick()
            compose.onNodeWithTag("learning_retry_audio").performScrollTo().assertHasClickAction()
                .assertHeightIsAtLeast(48.dp).performClick()
            compose.runOnIdle { assertEquals(listOf(LearningEvent.RetrySave, LearningEvent.Listen), events.takeLast(2)) }
            compose.runOnIdle {
                page.value = LearningUiPage.CourseOverview(LearningCourseUi(CourseUi.Theory,
                    listOf(LessonUi.NotesIntervals, LessonUi.Scales, LessonUi.ChordConstruction,
                        LessonUi.DiatonicFunctions, LessonUi.BasicProgressions, LessonUi.CircleOfFifths)
                        .map { LearningLessonRowUi(it, true, it == LessonUi.NotesIntervals) }, LessonUi.Scales))
            }
            val courseGoals = mutableListOf<TextLayoutResult>()
            compose.onNodeWithTag("learning_course_goal")
                .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(courseGoals) }
            assertFalse("$locale course goal clips at 200% text size", courseGoals.single().hasVisualOverflow)
            compose.onNodeWithTag("learning_course_continue").performScrollTo().assertHeightIsAtLeast(48.dp).performClick()
            compose.runOnIdle { assertEquals(LearningEvent.ContinueCourse(CourseUi.Theory), events.last()) }
            for (scale in listOf(1f, 1.2f, 2f)) {
                compose.runOnIdle { fontScale.value = scale; page.value = LearningUiPage.Lesson(LessonUi.CircleOfFifths) }
                circle.forEach { key ->
                    compose.onNodeWithTag("learning_circle_${key.tonic}").assertHeightIsAtLeast(48.dp).assertWidthIsAtLeast(48.dp)
                }
                val controls = circle.map { compose.onNodeWithTag("learning_circle_${it.tonic}").getUnclippedBoundsInRoot() }
                if (scale == 1f) {
                    val viewport = compose.onNodeWithTag("learning_circle").getUnclippedBoundsInRoot()
                    assertTrue("$locale normal-size circle fits the content width", controls.all { it.left >= viewport.left && it.right <= viewport.right })
                }
                controls.forEachIndexed { index, first ->
                    controls.drop(index + 1).forEach { second ->
                        assertFalse("$locale circle key touch targets overlap at $scale font scale",
                            first.left < second.right && first.right > second.left && first.top < second.bottom && first.bottom > second.top)
                    }
                }
            }
            for (technique in listOf(LessonUi.Strumming, LessonUi.AlternatePicking, LessonUi.HammerOnPullOff,
                LessonUi.Slide, LessonUi.Bending, LessonUi.Vibrato, LessonUi.PalmMute)) {
                compose.runOnIdle { page.value = LearningUiPage.Lesson(technique) }
                compose.onNodeWithTag("learning_tab").assertExists()
                if (technique == LessonUi.Strumming || technique == LessonUi.AlternatePicking || technique == LessonUi.PalmMute) {
                    val tabs = mutableListOf<TextLayoutResult>()
                    compose.onNodeWithTag("learning_tab")
                        .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(tabs) }
                    val lines = tabs.single().layoutInput.text.text.lines()
                    val noteColumns = lines.first().indices.filter { lines.first()[it].isDigit() }
                    val strokeColumns = lines.last().indices.filter { lines.last()[it] == 'D' || lines.last()[it] == 'U' }
                    if (technique == LessonUi.PalmMute) assertEquals(noteColumns.first(), lines.last().indexOf("PM"))
                    else assertEquals("$locale $technique strokes align with their fretted notes", noteColumns, strokeColumns)
                }
                compose.onNodeWithTag("learning_practice").assertExists()
                val practice = mutableListOf<TextLayoutResult>()
                compose.onNodeWithTag("learning_practice")
                    .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(practice) }
                assertFalse("$locale $technique practice instructions clip", practice.single().hasVisualOverflow)
                assertTrue(practice.single().layoutInput.text.text.isNotBlank())
                compose.onNodeWithTag("learning_listen").assertDoesNotExist()
            }
        }
    }

    private fun localizedContext(language: String): Context {
        val context = ApplicationProvider.getApplicationContext<Context>()
        return context.createConfigurationContext(Configuration(context.resources.configuration).apply {
            setLocale(Locale.forLanguageTag(language))
        })
    }

    private val circle = listOf(
        "C" to "A", "G" to "E", "D" to "B", "A" to "F♯", "E" to "C♯", "B" to "G♯",
        "F♯" to "D♯", "D♭" to "B♭", "A♭" to "F", "E♭" to "C", "B♭" to "G", "F" to "D",
    ).map { (major, minor) -> LearningCircleKeyUi(major, minor) }
}
