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
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class ProgressionUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private lateinit var host: AndroidProgressionsHost
    private lateinit var chords: AndroidChordsHost
    private lateinit var preferences: ControlledCommitPreferences

    @Before fun createWorkspace(): Unit = runBlocking {
        val app = compose.activity.application
        preferences = ControlledCommitPreferences(app.getSharedPreferences("progression_ui_${UUID.randomUUID()}", Application.MODE_PRIVATE))
        host = AndroidProgressionsHost(app, ProgressionPlaybackService::class.java, MainActivity::class.java, preferences)
        chords = AndroidChordsHost(app.getSharedPreferences("progression_source_${UUID.randomUUID()}", Application.MODE_PRIVATE))
        listOf(-1, 3, 2, 0, 1, 0).forEachIndexed { index, fret -> chords.execute(ChordCommand.SetStop(index,
            when (fret) { -1 -> StringStop.Muted; 0 -> StringStop.Open; else -> StringStop.Fretted(fret) })) }
        chords.execute(ChordCommand.SetName("Source C"))
        chords.execute(ChordCommand.SaveDraft)
    }

    @Test fun chordRestTieContextAndSavedCollectionJourneyKeepsCopiedMusicIndependent(): Unit {
        show(Locale.ENGLISH, false, 1f)
        click("feature_Progressions")
        compose.onNodeWithTag("progression_save").assertIsNotEnabled()
        val source = chords.current.value.records.single()
        repeat(2) {
            click("progression_add_chord")
            click("progression_copy_${source.id}")
            compose.onNodeWithTag("progression_editor_fret_5_0").performScrollTo().assertIsOn()
                .assertHeightIsAtLeast(48.dp).assertWidthIsAtLeast(48.dp)
            click("progression_commit_chord")
            compose.waitUntil(5_000) { host.current.value.draft.content.steps.size == it + 1 }
        }
        click("progression_step_0")
        click("progression_duration_0")
        compose.onNodeWithText("1/1").performClick()
        click("progression_dot_0")
        click("progression_tie_0")
        compose.waitUntil(5_000) { (host.current.value.draft.content.steps[0] as ProgressionStep.Chord).tieToNext }
        click("progression_step_1")
        click("progression_add_rest")
        compose.waitUntil(5_000) { host.current.value.draft.content.steps.size == 3 }
        click("progression_up_2")
        compose.waitUntil(5_000) { host.current.value.draft.content.steps[1] is ProgressionStep.Rest }
        assertFalse((host.current.value.draft.content.steps[0] as ProgressionStep.Chord).tieToNext)
        click("progression_down_1")
        compose.waitUntil(5_000) { host.current.value.draft.content.steps[2] is ProgressionStep.Rest }
        click("progression_context")
        compose.onNodeWithTag("chord_capo").performScrollTo().performTextReplacement("2")
        click("progression_close_sheet")
        compose.waitUntil(5_000) { host.current.value.draft.content.context.capo == 2 }
        click("progression_step_0")
        compose.onNodeWithTag("progression_step_0_fretboard").assertExists()
        compose.onNodeWithTag("progression_step_0_position_1").assertContentDescriptionContains("D3")
        compose.onNodeWithTag("progression_name").performScrollTo().performTextReplacement("Practice")
        compose.waitUntil(5_000) { host.current.value.draft.name == "Practice" }
        click("progression_save")
        compose.waitUntil(5_000) { host.current.value.records.size == 1 }
        val saved = host.current.value.records.single()
        click("progression_new")
        compose.waitUntil(5_000) { host.current.value.draft.content.steps.isEmpty() }
        runBlocking { chords.execute(ChordCommand.DeleteRecord(source.id)); chords.execute(ChordCommand.SetStop(5, StringStop.Fretted(8))) }
        click("progression_load_${saved.id}")
        compose.waitUntil(5_000) { host.current.value.draft.targetId == saved.id }
        assertEquals(saved.content, host.current.value.draft.content)
        val reloaded = AndroidProgressionsHost(compose.activity.application, ProgressionPlaybackService::class.java, MainActivity::class.java, preferences)
        assertEquals(saved.content, reloaded.current.value.draft.content)
        click("progression_delete_${saved.id}")
        click("progression_confirm_delete")
        compose.waitUntil(5_000) { host.current.value.records.isEmpty() }
        assertNull(host.current.value.draft.targetId)
    }

    @Test fun koreanLargeTextLandscapeKeepsManualEditorAndRecoverableSaveControlsReachable(): Unit {
        compose.activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        compose.waitUntil(5_000) { compose.activity.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE }
        show(Locale.KOREAN, true, 2f)
        click("feature_Progressions")
        click("progression_add_chord")
        click("progression_copy_current")
        compose.onNodeWithTag("progression_editor_fret_5_3").performScrollTo().assertHasClickAction()
            .assertHeightIsAtLeast(48.dp).assertWidthIsAtLeast(48.dp).performClick()
        click("progression_commit_chord")
        compose.waitUntil(5_000) { host.current.value.draft.content.steps.isNotEmpty() }
        click("progression_context")
        compose.onNodeWithTag("chord_capo").performScrollTo().performTextReplacement("bad")
        click("progression_close_sheet")
        compose.onNodeWithTag("progression_context_error").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("progression_play").performScrollTo().assertIsNotEnabled()
        click("progression_context")
        compose.onNodeWithTag("chord_capo").performScrollTo().performTextReplacement("0")
        click("progression_close_sheet")
        compose.onNodeWithTag("progression_name").performScrollTo().performTextReplacement("연습")
        compose.waitUntil(5_000) { host.current.value.draft.name == "연습" }
        preferences.failNext = true
        click("progression_save")
        compose.onNodeWithTag("progression_error").performScrollTo().assertIsDisplayed()
        assertTrue(host.current.value.records.isEmpty())
        click("progression_save")
        compose.waitUntil(5_000) { host.current.value.records.size == 1 }
        compose.onNodeWithTag("progression_play_selected").performScrollTo().assertIsDisplayed().assertIsEnabled()
        click("progression_add_rest")
        compose.waitUntil(5_000) { host.current.value.draft.content.steps.size == 2 }
    }

    private fun click(tag: String) { compose.onNodeWithTag(tag).performScrollTo().performClick() }
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
