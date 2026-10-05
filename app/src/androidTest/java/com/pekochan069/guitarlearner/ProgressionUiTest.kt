package com.pekochan069.guitarlearner

import android.app.Application
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.view.ContextThemeWrapper
import androidx.activity.ComponentActivity
import androidx.compose.ui.platform.ComposeView
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
            click("progression_source_Saved")
            click("progression_copy_${source.id}", scroll = true)
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
        step(1)
        closeSheet()
        click("progression_add_rest")
        compose.waitUntil(5_000) { host.current.value.draft.content.steps.size == 3 }
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
        click("progression_source_Saved")
        click("progression_copy_current", scroll = true)
        fret(5, 3).performClick()
        click("progression_commit_chord")
        compose.waitUntil(5_000) { host.current.value.draft.content.steps.isNotEmpty() }
        awaitSheetClosed()
        click("progression_settings")
        compose.onNodeWithTag("chord_capo").performScrollTo().performTextReplacement("bad")
        closeSheet()
        compose.onNodeWithTag("progression_list").performScrollToNode(hasTestTag("progression_context_error"))
        compose.onNodeWithTag("progression_context_error").assertIsDisplayed()
        compose.onNodeWithTag("progression_play").assertIsDisplayed().assertIsNotEnabled()
        click("progression_settings")
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
        click("progression_save")
        compose.waitUntil(5_000) { host.current.value.records.size == 1 }
        awaitSheetClosed()
        step(0)
        compose.onNodeWithTag("progression_play_selected").assertIsDisplayed().assertIsEnabled()
        closeSheet()
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
        compose.onNodeWithTag("progression_settings").assertTextContains("90 BPM").assertTextContains("4/4")
        compose.onNodeWithTag("progression_name").assertDoesNotExist()
        val identities = listOf(ChordIdentity(PitchClass.C, ChordQuality.Major), ChordIdentity(PitchClass.A, ChordQuality.Minor),
            ChordIdentity(PitchClass.F, ChordQuality.Major), ChordIdentity(PitchClass.G, ChordQuality.Major))
        identities.forEachIndexed { index, identity ->
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
                compose.onAllNodes(hasTestTag("progression_lookup_shape") and hasText("Shape name without capo · $symbol")).fetchSemanticsNodes().size == 1 &&
                    compose.onAllNodes(hasTestTag("progression_commit_chord") and isEnabled()).fetchSemanticsNodes().size == 1
            }
            compose.onNodeWithTag("progression_lookup_fretboard").assertExists()
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
        assertEquals(originalSource, chords.current.value)
        click("progression_loop")
        click("progression_play")
        compose.waitUntil(5_000) { host.current.value.playback is ProgressionPlayback.Playing }
        compose.onNodeWithTag("progression_pause").assertIsDisplayed().assertIsEnabled()
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
        click("progression_stop")
        compose.waitUntil(5_000) { host.current.value.playback is ProgressionPlayback.Stopped }
        assertEquals(originalSource, chords.current.value)
    }

    private fun click(tag: String, scroll: Boolean = false) {
        try {
            val node = compose.onNodeWithTag(tag)
            if (scroll) node.performScrollTo()
            node.assertIsDisplayed().assertHasClickAction().performClick()
        }
        catch (failure: AssertionError) { diagnostic("failed_$tag"); throw failure }
    }
    private fun menu(tag: String) { click("progression_actions"); click(tag) }
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
        val circuit = Circuit.Builder().addPresenterFactory(FoundationPresenter.Factory(ProgressionUiAppearance(),
            ProgressionUiMetronome(), FakeTuner(), chords, host)).addUiFactory(FoundationUiFactory).build()
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
