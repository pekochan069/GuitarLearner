package com.pekochan069.guitarlearner

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.pekochan069.guitarlearner.adapters.AndroidTrainingHost
import com.pekochan069.guitarlearner.domain.*
import com.pekochan069.guitarlearner.presentation.contract.TrainingNoteUi
import com.pekochan069.guitarlearner.ui.R as UiR
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class TrainingSessionTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private lateinit var owner: TrainingSessionOwner
    private lateinit var saved: TrainingSettings
    private val host: AndroidTrainingHost get() = owner.host

    @Before fun prepareSession() {
        owner = compose.runOnIdle { ViewModelProvider(compose.activity)[TrainingSessionOwner::class.java] }
        saved = host.current.value.settings
        runBlocking { assertTrue((compose.activity.application as GuitarLearnerApplication).graph.metronomeHost.execute(MetronomeCommand.Stop).isRight()) }
        compose.runOnIdle { host.submit(TrainingRequest.Exit) }
        configure(TrainingSettings())
    }

    @After fun restoreSettings() {
        compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        compose.runOnIdle { host.submit(TrainingRequest.Exit) }
        configure(saved)
        runBlocking { assertTrue((compose.activity.application as GuitarLearnerApplication).graph.metronomeHost.execute(MetronomeCommand.Stop).isRight()) }
    }

    @Test fun nativeQuestionComparisonRotationAndBackgroundKeepAnswersWithoutAutoplay() {
        openTraining()
        nativePlay { compose.onNodeWithTag("training_start").performScrollTo().performClick() }
        repeat(3) {
            answerCorrectly()
            compose.onNodeWithTag("training_next").performScrollTo().performClick()
        }
        answerCorrectly()
        val before = session()
        assertEquals(3, before.index)
        assertEquals(4, before.responses.size)
        nativePlay { compose.onNodeWithTag("training_replay_comparison").performScrollTo().performClick() }
        assertEquals(TrainingSound.Comparison, (host.current.value.audio as TrainingAudioStatus.Playing).sound)
        compose.activityRule.scenario.recreate()
        compose.waitForIdle()
        val retained = compose.runOnIdle { ViewModelProvider(compose.activity)[TrainingSessionOwner::class.java] }
        assertSame(owner, retained)
        assertEquals(before.key, session().key)
        assertEquals(before.response, session().response)
        compose.onNodeWithTag("training_feedback").assertExists()
        compose.onNodeWithTag("training_answer_C").assertDoesNotExist()
        nativePlay { compose.onNodeWithTag("training_replay_question").performScrollTo().performClick() }
        compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        assertEquals(TrainingAudioStatus.Idle, host.current.value.audio)
        compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        compose.waitForIdle()
        runBlocking { delay(900) }
        assertEquals(TrainingAudioStatus.Idle, host.current.value.audio)
        assertEquals(before.response, session().response)
        nativePlay { compose.onNodeWithTag("training_replay_question").performScrollTo().performClick() }
        compose.onNodeWithTag("navigate_up").performClick()
        assertEquals(TrainingStage.Setup, host.current.value.stage)
        assertEquals(TrainingAudioStatus.Idle, host.current.value.audio)
    }

    @Test fun everySubjectAndFormatHasHiddenAnswersAndTenResponsesProduceCurrentResults() {
        for (subject in TrainingSubject.entries) for (representation in TrainingRepresentation.entries) {
            configure(TrainingSettings(subject, representation, intervals = setOf(TrainingInterval.MinorThird, TrainingInterval.PerfectFifth)))
            openTraining()
            compose.onNodeWithTag("training_start").performScrollTo().performClick()
            compose.onNodeWithTag("training_feedback").assertDoesNotExist()
            when (representation) {
                TrainingRepresentation.Listening -> compose.onNodeWithTag("training_replay_question").assertExists()
                else -> {
                    val tag = when (representation) {
                        TrainingRepresentation.Staff -> "training_staff"
                        TrainingRepresentation.Fretboard -> "training_fretboard"
                        TrainingRepresentation.Tab -> "training_tab"
                        TrainingRepresentation.Listening -> error("Handled above")
                    }
                    val semantics = compose.onNodeWithTag(tag).performScrollTo().fetchSemanticsNode().config[SemanticsProperties.ContentDescription].joinToString()
                    assertFalse("Preanswer notation disclosed a pitch: $semantics", Regex("\\b[A-G](?:[♯♭]?[0-9])?\\b").containsMatchIn(semantics))
                }
            }
            answerCorrectly()
            assertTrue(requireNotNull(session().response).correct)
            compose.onNodeWithTag("navigate_up").performClick()
            assertEquals(TrainingStage.Setup, host.current.value.stage)
        }
        configure(TrainingSettings(representation = TrainingRepresentation.Tab))
        openTraining()
        compose.onNodeWithTag("training_start").performScrollTo().performClick()
        repeat(10) { number ->
            val current = session()
            val answer = if (number == 0) TrainingAnswer.Note((current.question.answer as TrainingAnswer.Note).pitchClass.transpose(1)) else current.question.answer
            compose.onNodeWithTag(answerTag(answer)).performScrollTo().performClick()
            compose.onNodeWithTag("training_feedback").assertExists()
            compose.onNodeWithTag(answerTag(answer)).assertDoesNotExist()
            assertEquals(number, session().index)
            compose.onNodeWithTag("training_next").performScrollTo().performClick()
            val scroll = compose.onNodeWithTag("feature_scroll").fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange]
            assertEquals("Next must show the new prompt from the top", 0f, scroll.value(), 0f)
        }
        val results = host.current.value.stage as TrainingStage.Results
        assertEquals(9, results.correctCount)
        assertEquals(10, results.responses.size)
        compose.onNodeWithTag("training_results").performScrollTo().assertIsDisplayed()
        (1..10).forEach { compose.onNodeWithTag("training_result_$it").assertExists() }
        compose.onNodeWithTag("training_setup_again").performScrollTo().performClick()
        compose.onNodeWithTag("training_start").performScrollTo().assertIsEnabled()
    }

    @Test fun listeningStopsNativeMetronomeAndAFreshHostKeepsSettingsOnly() {
        val graph = (compose.activity.application as GuitarLearnerApplication).graph
        compose.openMetronome()
        runBlocking { assertTrue(graph.metronomeHost.execute(MetronomeCommand.Start).isRight()) }
        runBlocking { withTimeout(5_000) { graph.metronomeHost.current.first { it.playback is PlaybackState.Playing } } }
        openTraining()
        nativePlay { compose.onNodeWithTag("training_start").performScrollTo().performClick() }
        assertTrue(graph.metronomeHost.current.value.playback is PlaybackState.Stopped)
        val current = session()
        answerCorrectly()
        configure(TrainingSettings(TrainingSubject.Interval, TrainingRepresentation.Fretboard,
            IntervalPresentation.Harmonic, setOf(TrainingInterval.Unison, TrainingInterval.Octave)))
        assertEquals(current.settings, session().settings)
        val fresh = compose.runOnIdle { graph.trainingHostFactory.create(compose.activity.lifecycleScope) }
        try {
            assertEquals(host.current.value.settings, fresh.current.value.settings)
            assertEquals(TrainingStage.Setup, fresh.current.value.stage)
            assertEquals(TrainingAudioStatus.Idle, fresh.current.value.audio)
        } finally { compose.runOnIdle { fresh.close() } }
        compose.onNodeWithTag("navigate_up").performClick()
        assertTrue(graph.metronomeHost.current.value.playback is PlaybackState.Stopped)
    }

    @Test fun formatMenusFixedSetupRotationAndBackKeepPagesSeparate() {
        openTrainingMenu()
        compose.onNodeWithTag("training_start").assertDoesNotExist()
        TrainingRepresentation.entries.forEach { compose.onNodeWithTag("training_format_${it.name}").assertExists() }
        compose.onNodeWithTag("training_exercise_Note").assertDoesNotExist()
        compose.onNodeWithTag("training_format_Staff").performClick()
        compose.onNodeWithTag("training_format_title").assertTextEquals(compose.activity.getString(UiR.string.training_staff_menu))
        compose.activityRule.scenario.recreate()
        compose.waitForIdle()
        compose.onNodeWithTag("training_format_title").assertTextEquals(compose.activity.getString(UiR.string.training_staff_menu))
        compose.onNodeWithTag("training_exercise_Interval").performClick()
        awaitSettings()
        compose.onNodeWithTag("training_exercise_title").assertTextEquals(compose.activity.getString(UiR.string.training_interval_exercise))
        compose.onNodeWithTag("training_fixed_format").assertTextEquals(compose.activity.getString(UiR.string.training_staff_menu))
        compose.onNodeWithTag("training_format").assertDoesNotExist()
        compose.onNodeWithTag("training_instrument").assertDoesNotExist()
        assertEquals(TrainingRepresentation.Staff, host.current.value.settings.representation)
        compose.activityRule.scenario.recreate()
        compose.waitForIdle()
        compose.onNodeWithTag("training_exercise_title").assertTextEquals(compose.activity.getString(UiR.string.training_interval_exercise))
        compose.onNodeWithTag("training_start").performScrollTo().performClick()
        assertEquals(TrainingSubject.Interval, session().settings.subject)
        assertEquals(TrainingRepresentation.Staff, session().settings.representation)
        compose.onNodeWithTag("navigate_up").performClick()
        compose.onNodeWithTag("training_fixed_format").assertExists()
        assertEquals(TrainingStage.Setup, host.current.value.stage)
        compose.onNodeWithTag("navigate_up").performClick()
        compose.onNodeWithTag("training_exercise_Interval").assertExists()
        compose.onNodeWithTag("training_start").assertDoesNotExist()
        compose.onNodeWithTag("navigate_up").performClick()
        TrainingRepresentation.entries.forEach { compose.onNodeWithTag("training_format_${it.name}").assertExists() }
        compose.onNodeWithTag("training_exercise_Interval").assertDoesNotExist()
        compose.onNodeWithTag("navigate_up").performClick()
        compose.onNodeWithTag("feature_Training").performScrollTo().assertIsDisplayed()
    }

    @Test fun selectedPianoAndGuitarPlayNativeQuestionAndComparisonWithFrozenResponses() {
        for (instrument in TrainingInstrument.entries) {
            configure(TrainingSettings())
            openTraining()
            compose.onNodeWithTag("training_instrument").performClick()
            compose.onNodeWithTag("training_instrument_${instrument.name}").performClick()
            awaitSettings()
            val label = if (instrument == TrainingInstrument.Piano) UiR.string.training_piano else UiR.string.training_guitar
            compose.onNodeWithTag("training_instrument").assertTextContains(compose.activity.getString(label))
            nativePlay { compose.onNodeWithTag("training_start").performScrollTo().performClick() }
            assertEquals(instrument, session().settings.instrument)
            answerCorrectly()
            val response = session().response
            nativePlay { compose.onNodeWithTag("training_replay_comparison").performScrollTo().performClick() }
            assertEquals(TrainingSound.Comparison, (host.current.value.audio as TrainingAudioStatus.Playing).sound)
            assertEquals(instrument, session().settings.instrument)
            assertEquals(response, session().response)
            compose.onNodeWithTag("navigate_up").performClick()
            assertEquals(TrainingAudioStatus.Idle, host.current.value.audio)
        }
    }

    private fun openTrainingMenu() {
        repeat(4) {
            if (compose.onAllNodesWithTag("navigate_up").fetchSemanticsNodes().isNotEmpty()) compose.onNodeWithTag("navigate_up").performClick()
        }
        compose.onNodeWithTag("feature_Training").performScrollTo().performClick()
    }

    private fun openTraining() {
        openTrainingMenu()
        compose.onNodeWithTag("training_format_${host.current.value.settings.representation.name}").performScrollTo().performClick()
        compose.onNodeWithTag("training_exercise_${host.current.value.settings.subject.name}").performScrollTo().performClick()
        awaitSettings()
    }

    private fun configure(settings: TrainingSettings) {
        compose.runOnIdle { host.submit(TrainingRequest.SetSettings(settings)) }
        awaitSettings()
        assertEquals(settings, host.current.value.settings)
    }

    private fun awaitSettings() {
        runBlocking { withTimeout(5_000) { host.current.first { it.storage !is TrainingStorageStatus.Saving } } }
        assertEquals(TrainingStorageStatus.Ready, host.current.value.storage)
        compose.waitForIdle()
    }

    private fun session(): TrainingSession = (host.current.value.stage as TrainingStage.Active).session
    private fun answerCorrectly() { compose.onNodeWithTag(answerTag(session().question.answer)).performScrollTo().performClick() }
    private fun answerTag(answer: TrainingAnswer): String = "training_answer_" + when (answer) {
        is TrainingAnswer.Note -> TrainingNoteUi.entries[answer.pitchClass.ordinal].name
        is TrainingAnswer.Interval -> answer.interval.name
    }

    private fun nativePlay(action: () -> Unit): Unit = runBlocking {
        val started = async(Dispatchers.Main, start = CoroutineStart.UNDISPATCHED) { withTimeout(5_000) {
            host.current.drop(1).first { it.audio is TrainingAudioStatus.Playing || it.audio is TrainingAudioStatus.Failed }
        } }
        action()
        val result = started.await().audio
        assertTrue("Native training audio did not start: $result", result is TrainingAudioStatus.Playing)
    }
}
