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
        val lesson = mutableStateOf(LessonUi.Scales)
        val events = mutableListOf<LearningEvent>()
        compose.setContent {
            CompositionLocalProvider(
                LocalContext provides localizedContext(language.value),
                LocalDensity provides Density(LocalDensity.current.density, 2f),
            ) {
                GuitarLearnerTheme(false) {
                    Column(Modifier.width(320.dp).verticalScroll(rememberScrollState())) {
                        LearningScreen(LearningUiState(
                            lesson = lesson.value,
                            roots = listOf("C", "G", "F"),
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
            compose.runOnIdle { language.value = locale; lesson.value = LessonUi.Scales }
            compose.onNodeWithTag("learning_explanation").assertExists()
            val layouts = mutableListOf<TextLayoutResult>()
            compose.onNodeWithTag("learning_explanation")
                .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
            assertFalse("$locale explanation clips at 200% text size", layouts.single().hasVisualOverflow)
            compose.onNodeWithTag("learning_notes").assertTextContains("C (1)", substring = true)
                .assertTextContains("E (3)", substring = true).assertTextContains("G (5)", substring = true)
            compose.onNodeWithTag("learning_retry_save").performScrollTo().assertHasClickAction()
                .assertHeightIsAtLeast(48.dp).performClick()
            compose.onNodeWithTag("learning_retry_audio").performScrollTo().assertHasClickAction()
                .assertHeightIsAtLeast(48.dp).performClick()
            compose.runOnIdle { assertEquals(listOf(LearningEvent.RetrySave, LearningEvent.Listen), events.takeLast(2)) }
            for (technique in listOf(LessonUi.Strumming, LessonUi.AlternatePicking, LessonUi.HammerOnPullOff,
                LessonUi.Slide, LessonUi.Bending, LessonUi.Vibrato, LessonUi.PalmMute)) {
                compose.runOnIdle { lesson.value = technique }
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
}
