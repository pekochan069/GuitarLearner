package com.pekochan069.guitarlearner

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ApplicationProvider
import android.content.Context
import com.pekochan069.guitarlearner.ui.R as UiR
import com.pekochan069.guitarlearner.domain.LearningAudioState
import com.pekochan069.guitarlearner.domain.LearningStorageState
import com.pekochan069.guitarlearner.domain.LessonId
import com.pekochan069.guitarlearner.domain.TrainingInstrument
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain

class LearningJourneyTest {
    private val compose = createAndroidComposeRule<MainActivity>()
    @get:Rule val rules = RuleChain.outerRule(object : ExternalResource() {
        override fun before() {
            val context = ApplicationProvider.getApplicationContext<Context>()
            assertTrue(context.getSharedPreferences("learning", Context.MODE_PRIVATE).edit().clear().commit())
        }
    }).around(compose)

    private val host get() = compose.runOnIdle {
        ViewModelProvider(compose.activity)[LearningSessionOwner::class.java].host
    }

    @Test fun unrestrictedTopicsCoursesAndExplorationShareProgressAndContextualMusic() {
        openLearning()
        click("learning_lesson_CircleOfFifths")
        compose.onNodeWithTag("learning_circle").assertExists()
        click("learning_complete")
        val active = host
        compose.waitUntil(5_000) {
            LessonId.CircleOfFifths in active.current.value.progress.completed &&
                active.current.value.storage == LearningStorageState.Saved
        }
        back()
        click("learning_mode_Courses")
        val completed = compose.onNodeWithTag("learning_lesson_CircleOfFifths")
            .fetchSemanticsNode().config[SemanticsProperties.StateDescription]
        assertEquals(compose.activity.getString(UiR.string.learning_completed), completed)
        click("learning_lesson_CircleOfFifths")
        compose.onNodeWithTag("learning_circle").assertExists()
        back()
        click("learning_mode_Explore")
        click("learning_concept_CircleOfFifths")
        compose.onNodeWithTag("learning_circle").assertExists()
        back()
        click("learning_concept_Scales")
        selectRoot("G")
        compose.onNodeWithTag("learning_notes").assertTextContains("F♯", substring = true)
        selectRoot("F")
        compose.onNodeWithTag("learning_notes").assertTextContains("B♭", substring = true)
        back()
        click("learning_concept_Chords")
        selectRoot("D")
        compose.onNodeWithTag("learning_notes").assertTextContains("D", substring = true)
            .assertTextContains("F♯", substring = true).assertTextContains("A", substring = true)
        assertTrue(LessonId.CircleOfFifths in host.current.value.progress.completed)
    }

    @Test fun explicitNativePianoAndGuitarExamplesFinishAndStopOnTopicOrBackgroundDeparture() {
        openLearning()
        click("learning_lesson_Scales")
        for (instrument in TrainingInstrument.entries) {
            click("learning_instrument_${instrument.name}")
            val active = host
            runBlocking {
                withTimeout(15_000) {
                    val started = async(start = CoroutineStart.UNDISPATCHED) {
                        active.current.first { it.audio is LearningAudioState.Playing }
                    }
                    click("learning_listen")
                    assertTrue(started.await().audio is LearningAudioState.Playing)
                    val finished = active.current.first { it.audio !is LearningAudioState.Playing && it.audio !is LearningAudioState.Preparing }
                    assertEquals(LearningAudioState.Idle, finished.audio)
                }
            }
        }
        val active = host
        startNativeExample()
        back()
        assertStopped(active.current.value.audio)
        click("learning_lesson_Scales")
        startNativeExample()
        compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        compose.waitUntil(5_000) {
            active.current.value.audio !is LearningAudioState.Playing &&
                active.current.value.audio !is LearningAudioState.Preparing
        }
        assertStopped(active.current.value.audio)
        compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        compose.onNodeWithTag("learning_lesson_title").assertExists()
        assertStopped(active.current.value.audio)
    }

    @Test fun linkedToolsAndTrainingReturnToTheSameLessonAcrossRecreation() {
        openLearning()
        click("learning_lesson_NotesIntervals")
        click("learning_link_NoteListening")
        compose.onNodeWithTag("training_start").assertExists()
        compose.waitUntil(5_000) {
            !compose.onNodeWithTag("training_start").fetchSemanticsNode().config.contains(SemanticsProperties.Disabled)
        }
        click("training_start")
        compose.waitUntil(5_000) {
            compose.onAllNodesWithTag("training_progress").fetchSemanticsNodes().isNotEmpty()
        }
        compose.activityRule.scenario.recreate()
        back()
        compose.onNodeWithTag("training_start").assertExists()
        back()
        compose.onNodeWithTag("learning_lesson_title").assertExists()
        assertEquals(LessonId.NotesIntervals, host.current.value.progress.lastViewed)
        back()
        click("learning_lesson_Strumming")
        click("learning_link_Metronome")
        compose.onNodeWithTag("bpm_value").assertExists()
        back()
        compose.onNodeWithTag("learning_lesson_title").assertExists()
        assertEquals(LessonId.Strumming, host.current.value.progress.lastViewed)
        back()
        click("learning_lesson_BasicProgressions")
        click("learning_link_Progressions")
        compose.onNodeWithTag("progression_tool").assertExists()
        back()
        compose.onNodeWithTag("learning_lesson_title").assertExists()
        assertEquals(LessonId.BasicProgressions, host.current.value.progress.lastViewed)
    }

    private fun openLearning() = click("feature_Learning")
    private fun click(tag: String) = compose.onNodeWithTag(tag).performScrollTo().performClick()
    private fun selectRoot(name: String) {
        click("learning_root")
        compose.onNodeWithTag("learning_key_$name").performScrollTo().assertIsDisplayed().performClick()
    }
    private fun back() {
        compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
    }
    private fun startNativeExample() {
        val active = host
        runBlocking {
            withTimeout(10_000) {
                val started = async(start = CoroutineStart.UNDISPATCHED) {
                    active.current.first { it.audio is LearningAudioState.Playing }
                }
                click("learning_listen")
                assertTrue(started.await().audio is LearningAudioState.Playing)
            }
        }
    }
    private fun assertStopped(audio: LearningAudioState) {
        assertFalse(audio is LearningAudioState.Playing)
        assertFalse(audio is LearningAudioState.Preparing)
    }
}
