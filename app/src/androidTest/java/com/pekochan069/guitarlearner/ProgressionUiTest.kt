package com.pekochan069.guitarlearner

import android.app.Application
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.view.ContextThemeWrapper
import androidx.activity.ComponentActivity
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import android.util.Log
import android.os.ParcelFileDescriptor
import arrow.core.Either
import com.pekochan069.guitarlearner.adapters.AndroidChordsHost
import com.pekochan069.guitarlearner.adapters.AndroidProgressionsHost
import com.pekochan069.guitarlearner.domain.*
import com.pekochan069.guitarlearner.presentation.contract.FoundationScreen
import com.pekochan069.guitarlearner.presentation.logic.FoundationPresenter
import com.pekochan069.guitarlearner.ui.FoundationUiFactory
import com.pekochan069.guitarlearner.ui.theme.GuitarLearnerTheme
import com.slack.circuit.foundation.Circuit
import com.slack.circuit.foundation.CircuitCompositionLocals
import com.slack.circuit.foundation.CircuitContent
import dev.zacsweers.metro.createGraphFactory
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.*
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class ProgressionUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private lateinit var application: GuitarLearnerApplication
    private lateinit var host: AndroidProgressionsHost
    private lateinit var chords: AndroidChordsHost
    private lateinit var preferences: ControlledCommitPreferences

    @Before fun createWorkspace(): Unit = runBlocking {
        application = compose.activity.application as GuitarLearnerApplication
        val previousGraph = application.graph
        previousGraph.metronomeHost.execute(MetronomeCommand.Stop)
        previousGraph.progressions.execute(ProgressionCommand.Stop)
        val app = application
        preferences = ControlledCommitPreferences(app.getSharedPreferences("progression_ui_${UUID.randomUUID()}", Application.MODE_PRIVATE))
        host = AndroidProgressionsHost(app, ProgressionPlaybackService::class.java, MainActivity::class.java, preferences)
        application.graphOverride = createGraphFactory<AppGraph.Factory>().create(application, previousGraph.metronomeHost, host)
        chords = AndroidChordsHost(app.getSharedPreferences("progression_source_${UUID.randomUUID()}", Application.MODE_PRIVATE))
        listOf(-1, 3, 2, 0, 1, 0).forEachIndexed { index, fret -> chords.execute(ChordCommand.SetStop(index,
            when (fret) { -1 -> StringStop.Muted; 0 -> StringStop.Open; else -> StringStop.Fretted(fret) })) }
        chords.execute(ChordCommand.SetName("Source C"))
        chords.execute(ChordCommand.SaveDraft)
    }

    @After fun restoreGraph(): Unit = runBlocking {
        try {
            if (::preferences.isInitialized) preferences.releaseCommit()
            if (::host.isInitialized) host.execute(ProgressionCommand.Stop)
        } finally {
            if (::application.isInitialized) application.graphOverride = null
        }
    }

    @Test fun chordRestTieContextAndSavedCollectionJourneyKeepsCopiedMusicIndependent(): Unit {
        show(Locale.ENGLISH, false, 1f)
        click("feature_Progressions")
        compose.onNodeWithTag("progression_open_save").assertIsNotEnabled()
        val source = chords.current.value.records.single()
        repeat(2) {
            click("progression_add_chord")
            click("progression_source_Manual")
            click("progression_shapes", scroll = true)
            click("progression_copy_${source.id}")
            fret(5, 0).assertIsOn()
            click("progression_commit_chord")
            compose.waitUntil(5_000) { host.current.value.draft.content.steps.size == it + 1 }
            awaitSheetClosed()
        }
        step(0)
        click("progression_duration_0", scroll = true)
        click("progression_duration_0_option_0")
        compose.waitUntil(5_000) { host.current.value.draft.content.steps[0].duration.value == NoteValue.Whole }
        click("progression_dot_0", scroll = true)
        click("progression_tie_0", scroll = true)
        compose.waitUntil(5_000) { (host.current.value.draft.content.steps[0] as ProgressionStep.Chord).tieToNext }
        closeSheet()
        val tiedChords = host.current.value.draft.content.steps
        step(0)
        closeSheet()
        click("progression_add_rest")
        compose.waitUntil(5_000) { host.current.value.draft.content.steps.size == 3 }
        assertEquals(tiedChords, host.current.value.draft.content.steps.take(2))
        assertTrue(host.current.value.draft.content.steps[2] is ProgressionStep.Rest)
        step(2)
        click("progression_up_2", scroll = true)
        compose.waitUntil(5_000) { host.current.value.draft.content.steps[1] is ProgressionStep.Rest }
        assertFalse((host.current.value.draft.content.steps[0] as ProgressionStep.Chord).tieToNext)
        click("progression_down_1", scroll = true)
        compose.waitUntil(5_000) { host.current.value.draft.content.steps[2] is ProgressionStep.Rest }
        closeSheet()
        click("progression_settings")
        compose.onNodeWithTag("chord_capo").performScrollTo().performTextReplacement("2")
        closeSheet()
        compose.waitUntil(5_000) { host.current.value.draft.content.context.capo == 2 }
        step(0)
        compose.onNodeWithTag("progression_step_0_fretboard").assertExists()
        compose.onNodeWithTag("progression_step_0_position_1").assertContentDescriptionContains("D3", substring = true)
        closeSheet()
        click("progression_open_save")
        compose.onNodeWithTag("progression_name").performScrollTo().performTextReplacement("Practice")
        compose.onNodeWithTag("progression_name").performImeAction()
        compose.waitUntil(5_000) { host.current.value.draft.name == "Practice" && host.current.value.persistence == DraftPersistence.Synced }
        click("progression_save")
        compose.waitUntil(5_000) { host.current.value.records.size == 1 }
        awaitSheetClosed()
        val saved = host.current.value.records.single()
        menu("progression_new")
        compose.waitUntil(5_000) { host.current.value.draft.content.steps.isEmpty() }
        runBlocking { chords.execute(ChordCommand.DeleteRecord(source.id)); chords.execute(ChordCommand.SetStop(5, StringStop.Fretted(8))) }
        menu("progression_collection")
        click("progression_load_${saved.id}", scroll = true)
        compose.waitUntil(5_000) { host.current.value.draft.targetId == saved.id }
        assertEquals(saved.content, host.current.value.draft.content)
        val reloaded = AndroidProgressionsHost(compose.activity.application, ProgressionPlaybackService::class.java, MainActivity::class.java, preferences)
        assertEquals(saved.content, reloaded.current.value.draft.content)
        awaitSheetClosed()
        menu("progression_collection")
        click("progression_delete_${saved.id}", scroll = true)
        click("progression_confirm_delete", scroll = false)
        compose.waitUntil(5_000) { host.current.value.records.isEmpty() }
        assertNull(host.current.value.draft.targetId)
    }

    @Test fun koreanLargeTextLandscapeKeepsManualEditorAndRecoverableSaveControlsReachable(): Unit {
        compose.activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        compose.waitUntil(5_000) { compose.activity.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE }
        show(Locale.KOREAN, true, 2f)
        click("feature_Progressions")
        click("progression_add_chord")
        click("progression_source_Manual")
        click("progression_shapes", scroll = true)
        click("progression_copy_current")
        fret(5, 3).performClick()
        click("progression_commit_chord")
        compose.waitUntil(5_000) { host.current.value.draft.content.steps.isNotEmpty() }
        awaitSheetClosed()
        compose.onNodeWithTag("progression_list").performScrollToNode(hasTestTag("progression_step_0"))
        val row = compose.onNodeWithTag("progression_step_0").getUnclippedBoundsInRoot()
        val viewport = compose.onNodeWithTag("progression_list").getUnclippedBoundsInRoot()
        assertTrue("The entire chord row must fit in the large-text landscape viewport", row.top >= viewport.top && row.bottom <= viewport.bottom)
        timing()
        compose.onNodeWithTag("chord_capo").performScrollTo().performTextReplacement("bad")
        closeSheet()
        compose.onNodeWithTag("progression_list").performScrollToNode(hasTestTag("progression_context_error"))
        compose.onNodeWithTag("progression_context_error").assertIsDisplayed()
        compose.onNodeWithTag("progression_play").assertIsDisplayed().assertIsNotEnabled()
        timing()
        compose.onNodeWithTag("chord_capo").performScrollTo().performTextReplacement("0")
        closeSheet()
        click("progression_open_save")
        compose.onNodeWithTag("progression_name").performScrollTo().performTextReplacement("연습")
        compose.onNodeWithTag("progression_name").performImeAction()
        compose.waitUntil(5_000) { host.current.value.draft.name == "연습" && host.current.value.persistence == DraftPersistence.Synced }
        compose.waitUntil(5_000) { compose.onNodeWithTag("progression_save").isDisplayed() }
        preferences.failNext = true
        click("progression_save")
        compose.waitUntil(5_000) { host.current.value.actionFailure == ProgressionFailure.WriteFailed }
        compose.onNodeWithTag("progression_sheet_error").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("progression_sheet").assertExists()
        compose.onNodeWithTag("progression_name").assertTextContains("연습")
        assertTrue(host.current.value.records.isEmpty())
        compose.waitUntil(5_000) { compose.onAllNodes(hasTestTag("progression_save") and isEnabled()).fetchSemanticsNodes().size == 1 &&
            compose.onNodeWithTag("progression_save").isDisplayed() }
        click("progression_save")
        compose.waitUntil(5_000) { host.current.value.records.size == 1 }
        awaitSheetClosed()
        step(0)
        compose.onNodeWithTag("progression_play_selected").assertIsDisplayed().assertIsEnabled()
        closeSheet()
        compose.onNodeWithTag("progression_list").performScrollToNode(hasTestTag("progression_add_rest"))
        click("progression_add_rest")
        compose.waitUntil(5_000) { host.current.value.draft.content.steps.size == 2 }
    }

    @Test fun namedCAmFGJourneyKeepsTheMusicAndNativeTransportVisible(): Unit {
        show(Locale.ENGLISH, false, 1f)
        click("feature_Progressions")
        val originalSource = chords.current.value
        compose.onAllNodesWithTag("destination_title").assertCountEquals(1)
        compose.onNodeWithTag("progression_empty").assertIsDisplayed()
        compose.onNodeWithTag("progression_add_chord").assertIsDisplayed().assertIsEnabled()
        compose.onNodeWithTag("progression_transport").assertIsDisplayed()
        compose.onNodeWithTag("progression_settings").assertTextContains("90 BPM · 4/4")
        compose.onNodeWithTag("progression_name").assertDoesNotExist()
        val identities = listOf(ChordIdentity(PitchClass.C, ChordQuality.Major), ChordIdentity(PitchClass.A, ChordQuality.Minor),
            ChordIdentity(PitchClass.F, ChordQuality.Major), ChordIdentity(PitchClass.G, ChordQuality.Major))
        identities.forEachIndexed { index, identity ->
            if (index > 0) {
                step(0)
                closeSheet()
            }
            click("progression_add_chord")
            compose.onNodeWithTag("progression_root").assertIsDisplayed()
            compose.onNodeWithTag("progression_quality").assertIsDisplayed()
            if (identity.root != PitchClass.C) {
                click("progression_root")
                click("progression_root_option_${identity.root.ordinal}", scroll = true)
            }
            if (identity.quality != ChordQuality.Major) {
                click("progression_quality")
                click("progression_quality_option_${identity.quality.ordinal}")
            }
            val symbol = identity.root.symbol + identity.quality.symbol
            compose.waitUntil(10_000) {
                compose.onAllNodes(hasTestTag("progression_lookup_shape") and hasText(symbol)).fetchSemanticsNodes().size == 1 &&
                    compose.onAllNodes(hasTestTag("progression_commit_chord") and isEnabled()).fetchSemanticsNodes().size == 1
            }
            compose.onNodeWithTag("progression_lookup_fretboard").assertExists()
            compose.onNodeWithTag("progression_source_Saved").assertDoesNotExist()
            compose.onNodeWithTag("progression_commit_chord").assertIsDisplayed().assertIsEnabled()
            click("progression_commit_chord")
            compose.waitUntil(5_000) { host.current.value.draft.content.steps.size == index + 1 }
            awaitSheetClosed()
            val inserted = host.current.value.draft.content.steps[index] as ProgressionStep.Chord
            assertEquals("", inserted.name)
            assertEquals(NoteDuration(NoteValue.Quarter), inserted.duration)
            assertTrue((ChordTheory.analyze(host.current.value.draft.content.context, inserted.shape) as ChordAnalysis.Recognized)
                .candidates.any { it.identity == identity })
        }
        identities.indices.forEach { index ->
            val symbol = identities[index].root.symbol + identities[index].quality.symbol
            compose.onNodeWithTag("progression_step_$index").assertIsDisplayed().assertTextContains(symbol, substring = true)
                .assertTextContains("Quarter note", substring = true)
        }
        compose.onNodeWithTag("progression_add_chord").assertIsDisplayed()
        compose.onNodeWithTag("progression_play").assertIsDisplayed().assertIsEnabled()
        compose.onNodeWithTag("progression_stop").assertIsDisplayed()
        compose.onNodeWithTag("progression_loop").assertIsDisplayed()
        val originalSteps = host.current.value.draft.content.steps
        drag(1, 3)
        val reordered = listOf(originalSteps[0], originalSteps[2], originalSteps[3], originalSteps[1])
        compose.waitUntil(5_000) { host.current.value.draft.content.steps == reordered }
        compose.onNodeWithTag("progression_sheet").assertDoesNotExist()
        listOf("C", "F", "G", "Am").forEachIndexed { index, symbol ->
            compose.onNodeWithTag("progression_step_$index").assertTextContains(symbol).assertTextContains("Quarter note")
            compose.onNodeWithTag("progression_quick_remove_$index").assertIsDisplayed().assertHeightIsAtLeast(48.dp).assertWidthIsAtLeast(48.dp)
        }
        assertEquals(originalSource, chords.current.value)
        click("progression_loop")
        click("progression_play")
        compose.waitUntil(5_000) { host.current.value.playback is ProgressionPlayback.Playing }
        compose.onNodeWithTag("progression_pause").assertIsDisplayed().assertIsEnabled()
        compose.waitUntil(5_000) { compose.onNodeWithTag("progression_active_fretboard").isDisplayed() }
        (0..5).forEach { compose.onNodeWithTag("progression_active_position_$it").assertIsDisplayed() }
        step(0)
        compose.onNodeWithTag("progression_step_0_fretboard").assertExists()
        assertTrue(host.current.value.playback is ProgressionPlayback.Playing)
        click("progression_play_selected")
        awaitSheetClosed()
        compose.waitUntil(5_000) { host.current.value.playback is ProgressionPlayback.Playing }
        click("progression_pause")
        compose.waitUntil(5_000) { host.current.value.playback is ProgressionPlayback.Paused }
        compose.onNodeWithTag("progression_resume").assertIsDisplayed().assertIsEnabled()
        click("progression_resume")
        compose.waitUntil(5_000) { host.current.value.playback is ProgressionPlayback.Playing }
        compose.onNodeWithTag("progression_list").performScrollToNode(hasTestTag("progression_quick_remove_0"))
        click("progression_quick_remove_0")
        compose.waitUntil(5_000) { host.current.value.playback is ProgressionPlayback.Stopped && host.current.value.draft.content.steps == reordered.drop(1) }
        compose.onNodeWithTag("progression_sheet").assertDoesNotExist()
        compose.onNodeWithTag("progression_active_fretboard").assertDoesNotExist()
        compose.onNodeWithTag("progression_removal_feedback").assertIsDisplayed()
        compose.onNodeWithText("C removed").assertIsDisplayed()
        click("progression_dismiss_removal")
        click("progression_play")
        compose.waitUntil(5_000) { host.current.value.playback is ProgressionPlayback.Playing }
        click("progression_stop")
        compose.waitUntil(5_000) { host.current.value.playback is ProgressionPlayback.Stopped }
        assertEquals(originalSource, chords.current.value)
    }

    @Test fun quickRemovalConfirmsDuplicateChordsRestsAndTheLastItemWithoutMovingControls(): Unit {
        runBlocking {
            repeat(2) { host.execute(ProgressionCommand.Insert(ProgressionStep.Chord("C", chords.current.value.draft.shape))) }
            host.execute(ProgressionCommand.Insert(ProgressionStep.Rest()))
        }
        show(Locale.KOREAN, true, 1f)
        click("feature_Progressions")
        val viewport = compose.onNodeWithTag("progression_list").getUnclippedBoundsInRoot()
        val timing = compose.onNodeWithTag("progression_settings").getUnclippedBoundsInRoot()
        val transport = compose.onNodeWithTag("progression_transport").getUnclippedBoundsInRoot()
        val original = host.current.value.draft.content.steps
        click("progression_quick_remove_1")
        compose.waitUntil(5_000) { host.current.value.draft.content.steps == listOf(original[0], original[2]) }
        compose.onNodeWithTag("progression_removal_feedback").assertIsDisplayed()
        compose.onNodeWithText("C 삭제됨").assertIsDisplayed()
        assertEquals(viewport, compose.onNodeWithTag("progression_list").getUnclippedBoundsInRoot())
        assertEquals(timing, compose.onNodeWithTag("progression_settings").getUnclippedBoundsInRoot())
        assertEquals(transport, compose.onNodeWithTag("progression_transport").getUnclippedBoundsInRoot())
        click("progression_quick_remove_1")
        compose.waitUntil(5_000) { host.current.value.draft.content.steps == listOf(original[0]) }
        compose.onNodeWithTag("progression_removal_feedback").assertIsDisplayed()
        compose.onNodeWithText("쉼표 삭제됨").assertIsDisplayed()
        diagnostic("removal_feedback_ko_dark")
        click("progression_dismiss_removal")
        compose.onNodeWithTag("progression_removal_feedback").assertDoesNotExist()
        click("progression_quick_remove_0")
        compose.waitUntil(5_000) { host.current.value.draft.content.steps.isEmpty() }
        compose.onNodeWithTag("progression_removal_feedback").assertIsDisplayed()
        compose.onNodeWithText("C 삭제됨").assertIsDisplayed()
        compose.onNodeWithTag("progression_empty").assertIsDisplayed()
        compose.onNodeWithTag("progression_play").assertIsNotEnabled()
        compose.waitUntil(6_000) { compose.onAllNodesWithTag("progression_removal_feedback").fetchSemanticsNodes().isEmpty() }
        assertEquals(transport, compose.onNodeWithTag("progression_transport").getUnclippedBoundsInRoot())
    }

    @Test fun aRestClearsTheActiveFretboardWithoutMovingTheSequence(): Unit {
        runBlocking {
            host.execute(ProgressionCommand.Insert(ProgressionStep.Chord("", chords.current.value.draft.shape, NoteDuration(NoteValue.Whole))))
            host.execute(ProgressionCommand.Insert(ProgressionStep.Rest(NoteDuration(NoteValue.Whole))))
            host.execute(ProgressionCommand.SetTempo(120))
            host.execute(ProgressionCommand.SetSignature(BeatUnit.Quarter, 1))
            host.execute(ProgressionCommand.SetLoop(true))
        }
        show(Locale.ENGLISH, false, 1f)
        click("feature_Progressions")
        click("progression_play")
        compose.waitUntil(5_000) { compose.onNodeWithTag("progression_active_fretboard").isDisplayed() }
        val bounds = compose.onNodeWithTag("progression_list").getUnclippedBoundsInRoot()
        (0..5).forEach { compose.onNodeWithTag("progression_active_position_$it").assertIsDisplayed() }
        compose.waitUntil(5_000) { compose.onNodeWithTag("progression_active_rest").isDisplayed() }
        compose.onNodeWithTag("progression_active_fretboard").assertDoesNotExist()
        compose.onNodeWithTag("progression_playback_pane").assertIsDisplayed()
        assertEquals(bounds, compose.onNodeWithTag("progression_list").getUnclippedBoundsInRoot())
        compose.waitUntil(5_000) { compose.onNodeWithTag("progression_active_fretboard").isDisplayed() }
        assertEquals(bounds, compose.onNodeWithTag("progression_list").getUnclippedBoundsInRoot())
        click("progression_stop")
    }

    @Test fun failedDraftEditUsesTheSaveBadgeAndMenuRetryWithoutMovingTheMusic(): Unit {
        runBlocking {
            host.execute(ProgressionCommand.Insert(ProgressionStep.Chord("", chords.current.value.draft.shape)))
            host.execute(ProgressionCommand.Insert(ProgressionStep.Rest()))
        }
        show(Locale.ENGLISH, false, 1f)
        click("feature_Progressions")
        val viewport = compose.onNodeWithTag("progression_list").getUnclippedBoundsInRoot()
        val rows = (0..1).map { compose.onNodeWithTag("progression_step_$it").getUnclippedBoundsInRoot() }
        val timing = compose.onNodeWithTag("progression_settings").getUnclippedBoundsInRoot()
        val save = compose.onNodeWithTag("progression_open_save").getUnclippedBoundsInRoot()
        compose.onNodeWithTag("progression_open_save").assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Draft saved"))
        step(0)
        val gate = preferences.blockNextCommit()
        preferences.failNext = true
        click("progression_dot_0", scroll = true)
        compose.waitUntil(5_000) { gate.entered.isCompleted && host.current.value.persistence == DraftPersistence.Unsynced }
        val edited = host.current.value.draft.content
        assertTrue(edited.steps[0].duration.dotted)
        closeSheet()
        compose.onNodeWithTag("progression_draft_badge", useUnmergedTree = true).assertExists()
        compose.onNodeWithTag("progression_open_save").assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Draft not saved"))
        assertEquals(rows, (0..1).map { compose.onNodeWithTag("progression_step_$it").getUnclippedBoundsInRoot() })
        assertEquals(timing, compose.onNodeWithTag("progression_settings").getUnclippedBoundsInRoot())
        assertEquals(save, compose.onNodeWithTag("progression_open_save").getUnclippedBoundsInRoot())
        gate.open()
        compose.waitUntil(5_000) { host.current.value.persistence == DraftPersistence.Unsynced && host.current.value.actionFailure == ProgressionFailure.WriteFailed }
        compose.onNodeWithTag("progression_draft_badge", useUnmergedTree = true).assertExists()
        compose.onNodeWithTag("progression_open_save").assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Draft not saved"))
        compose.onNodeWithTag("progression_unsynced").assertDoesNotExist()
        compose.onNodeWithTag("progression_error").assertDoesNotExist()
        assertEquals(viewport, compose.onNodeWithTag("progression_list").getUnclippedBoundsInRoot())
        assertEquals(rows, (0..1).map { compose.onNodeWithTag("progression_step_$it").getUnclippedBoundsInRoot() })
        assertEquals(timing, compose.onNodeWithTag("progression_settings").getUnclippedBoundsInRoot())
        assertEquals(save, compose.onNodeWithTag("progression_open_save").getUnclippedBoundsInRoot())
        diagnostic("draft_badge_unsaved")
        click("progression_play")
        compose.waitUntil(5_000) { host.current.value.playback is ProgressionPlayback.Playing }
        assertEquals(DraftPersistence.Unsynced, host.current.value.persistence)
        assertNull(host.current.value.actionFailure)
        compose.onNodeWithTag("progression_draft_badge", useUnmergedTree = true).assertExists()
        compose.onNodeWithTag("progression_open_save").assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Draft not saved"))
        click("progression_stop")
        compose.waitUntil(5_000) { host.current.value.playback is ProgressionPlayback.Stopped }
        menu("progression_retry_draft")
        compose.waitUntil(5_000) { host.current.value.persistence == DraftPersistence.Synced && host.current.value.actionFailure == null }
        compose.onNodeWithTag("progression_draft_badge", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithTag("progression_open_save").assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Draft saved"))
        assertEquals(edited, host.current.value.draft.content)
        assertTrue(host.current.value.records.isEmpty())
        assertEquals(rows, (0..1).map { compose.onNodeWithTag("progression_step_$it").getUnclippedBoundsInRoot() })
        assertEquals(timing, compose.onNodeWithTag("progression_settings").getUnclippedBoundsInRoot())
    }

    @Test fun longDragRendersAboveTheFooterCancelsAndScrollsToTheEndBeforeOneDrop(): Unit {
        runBlocking {
            host.execute(ProgressionCommand.Insert(ProgressionStep.Chord("", chords.current.value.draft.shape)))
            repeat(9) { host.execute(ProgressionCommand.Insert(ProgressionStep.Rest(NoteDuration(if (it == 8) NoteValue.Whole else NoteValue.Half)))) }
        }
        show(Locale.ENGLISH, false, 1f)
        click("feature_Progressions")
        val original = host.current.value.draft.content.steps
        val viewport = compose.onNodeWithTag("progression_list").fetchSemanticsNode().boundsInRoot
        val header = compose.onNodeWithTag("destination_title").fetchSemanticsNode().boundsInRoot
        val footer = compose.onNodeWithTag("progression_transport").fetchSemanticsNode().boundsInRoot
        beginDrag(0, Offset(24f, header.center.y - viewport.top))
        compose.waitUntil(5_000) { compose.onNodeWithTag("progression_drag_overlay").isDisplayed() }
        val aboveHeader = compose.onNodeWithTag("progression_drag_overlay").fetchSemanticsNode().boundsInRoot
        assertTrue("The dragged card must cross the app header", aboveHeader.top < header.bottom && aboveHeader.bottom > header.top)
        diagnostic("drag_overlay_above_header")
        compose.onNodeWithTag("progression_list").performTouchInput { moveTo(Offset(24f, footer.center.y - viewport.top), delayMillis = 200) }
        compose.waitUntil(5_000) { compose.onNodeWithTag("progression_drag_overlay").fetchSemanticsNode().boundsInRoot.bottom > footer.top }
        val overlay = compose.onNodeWithTag("progression_drag_overlay").fetchSemanticsNode().boundsInRoot
        assertTrue("The dragged card must extend beyond the list and over the footer", overlay.bottom > viewport.bottom && overlay.bottom > footer.top)
        diagnostic("drag_overlay_above_footer")
        assertEquals(original, host.current.value.draft.content.steps)
        compose.onNodeWithTag("progression_list").performTouchInput { cancel() }
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("progression_drag_overlay").fetchSemanticsNodes().isEmpty() }
        assertEquals(original, host.current.value.draft.content.steps)
        compose.onNodeWithTag("progression_sheet").assertDoesNotExist()
        compose.onNodeWithTag("progression_list").performScrollToNode(hasTestTag("progression_step_0"))
        beginDrag(0, Offset(24f, viewport.height - 2f))
        compose.waitUntil(5_000) {
            val last = compose.onAllNodesWithTag("progression_step_9").fetchSemanticsNodes().singleOrNull()?.boundsInRoot
            val placeholder = compose.onAllNodesWithTag("progression_step_0", useUnmergedTree = true).fetchSemanticsNodes().singleOrNull()?.boundsInRoot
            last != null && placeholder != null && last.top < placeholder.top && placeholder.bottom <= viewport.bottom
        }
        compose.onNodeWithTag("progression_drag_overlay").assertIsDisplayed()
        assertEquals(original, host.current.value.draft.content.steps)
        compose.onNodeWithTag("progression_list").performTouchInput { up() }
        compose.waitUntil(5_000) { host.current.value.draft.content.steps == original.drop(1) + original.first() }
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("progression_drag_overlay").fetchSemanticsNodes().isEmpty() }
        compose.onNodeWithTag("progression_sheet").assertDoesNotExist()
    }

    private fun click(tag: String, scroll: Boolean = false) {
        try {
            compose.waitUntil(5_000) { compose.onAllNodesWithTag(tag).fetchSemanticsNodes().size == 1 }
            val node = compose.onNodeWithTag(tag)
            if (scroll) node.performScrollTo()
            node.assertIsDisplayed().assertIsEnabled().assertHasClickAction().performClick()
        }
        catch (failure: AssertionError) { diagnostic("failed_$tag"); throw failure }
    }
    private fun menu(tag: String) { click("progression_actions"); click(tag) }
    private fun timing() {
        if (!compose.onNodeWithTag("progression_settings").isDisplayed()) {
            compose.onNodeWithTag("progression_list").performScrollToNode(hasTestTag("progression_settings"))
        }
        click("progression_settings")
    }
    private fun drag(origin: Int, destination: Int) {
        val viewport = compose.onNodeWithTag("progression_list").fetchSemanticsNode().boundsInRoot
        val first = compose.onNodeWithTag("progression_step_$origin").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val last = compose.onNodeWithTag("progression_step_$destination").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val start = Offset(first.left + first.width * 0.25f - viewport.left, first.center.y - viewport.top)
        val end = Offset(start.x, last.center.y - viewport.top)
        val automatic = compose.mainClock.autoAdvance
        try {
            compose.mainClock.autoAdvance = false
            compose.onNodeWithTag("progression_list").performTouchInput {
                down(start)
                moveTo(start, delayMillis = viewConfiguration.longPressTimeoutMillis + 100)
                moveTo(end, delayMillis = 200)
            }
            compose.mainClock.advanceTimeByFrame()
            compose.waitForIdle()
            diagnostic("drag_neighbors_start")
            compose.mainClock.advanceTimeBy(32)
            compose.waitForIdle()
            diagnostic("drag_neighbors_mid")
            compose.onNodeWithTag("progression_list").performTouchInput { up() }
        } finally {
            compose.mainClock.autoAdvance = automatic
        }
    }
    private fun beginDrag(origin: Int, end: Offset) {
        val viewport = compose.onNodeWithTag("progression_list").fetchSemanticsNode().boundsInRoot
        val row = compose.onNodeWithTag("progression_step_$origin").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val start = Offset(row.left + row.width * 0.25f - viewport.left, row.center.y - viewport.top)
        compose.onNodeWithTag("progression_list").performTouchInput {
            down(start)
            moveTo(start, delayMillis = viewConfiguration.longPressTimeoutMillis + 100)
            moveTo(end, delayMillis = 200)
        }
    }
    private fun step(index: Int) {
        compose.onNodeWithTag("progression_list").performScrollToNode(hasTestTag("progression_step_$index"))
        click("progression_step_$index")
    }
    private fun closeSheet() { click("progression_close_sheet"); awaitSheetClosed() }
    private fun awaitSheetClosed() { compose.waitUntil(5_000) { compose.onAllNodesWithTag("progression_sheet").fetchSemanticsNodes().isEmpty() } }
    private fun fret(stringIndex: Int, fretNumber: Int): SemanticsNodeInteraction {
        val tag = "progression_editor_fret_${stringIndex}_$fretNumber"
        try {
            compose.onNodeWithTag("progression_editor_position_$stringIndex").performScrollTo().assertIsDisplayed()
            return compose.onNodeWithTag(tag).performScrollTo().assertIsDisplayed().assertHasClickAction()
                .assertHeightIsAtLeast(48.dp).assertWidthIsAtLeast(48.dp)
        } catch (failure: AssertionError) { diagnostic("failed_$tag"); throw failure }
    }
    private fun diagnostic(label: String) {
        Log.d("ProgressionUiDiagnostic", "$label merged tree")
        compose.onAllNodes(isRoot()).printToLog("ProgressionUiDiagnostic", maxDepth = 8)
        Log.d("ProgressionUiDiagnostic", "$label unmerged tree")
        compose.onAllNodes(isRoot(), useUnmergedTree = true).printToLog("ProgressionUiDiagnostic", maxDepth = 8)
        val path = "/sdcard/Download/progression_$label.png"
        ParcelFileDescriptor.AutoCloseInputStream(InstrumentationRegistry.getInstrumentation().uiAutomation
            .executeShellCommand("screencap -p $path")).bufferedReader().use { Log.d("ProgressionUiDiagnostic", it.readText()) }
        Log.d("ProgressionUiDiagnostic", "$label screenshot: $path")
    }
    private fun show(locale: Locale, dark: Boolean, scale: Float) {
        val circuit = Circuit.Builder().addPresenterFactory(FoundationPresenter.Factory(ProgressionUiAppearance(), ProgressionUiMetronome(), FakeTuner(), chords, host, TrainingTestPort(), LearningTestPort())).addUiFactory(FoundationUiFactory).build()
        compose.runOnUiThread {
            val localized = ContextThemeWrapper(compose.activity, compose.activity.theme).apply {
                applyOverrideConfiguration(Configuration(compose.activity.resources.configuration).apply { setLocale(locale); fontScale = scale })
            }
            compose.activity.setContentView(ComposeView(localized).apply {
                setContent { GuitarLearnerTheme(dark) { CircuitCompositionLocals(circuit) { CircuitContent(FoundationScreen) } } }
            })
        }
    }
}
private class ProgressionUiAppearance : AppearanceSettings {
    override val current = MutableStateFlow(AppearanceSnapshot(ThemePreference.System, LanguagePreference.System))
    override suspend fun select(change: AppearanceChange): Either<AppearanceFailure, Unit> = error("Unexpected appearance request")
}
private class ProgressionUiMetronome : Metronome {
    override val current = MutableStateFlow(MetronomeSnapshot())
    override suspend fun execute(command: MetronomeCommand): Either<MetronomeFailure, Unit> = error("Unexpected metronome request")
}
