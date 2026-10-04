package com.pekochan069.guitarlearner

import android.content.Context
import android.content.res.Configuration
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import com.pekochan069.guitarlearner.presentation.contract.DevelopmentSample
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import arrow.core.Either
import com.pekochan069.guitarlearner.domain.AppearanceChange
import com.pekochan069.guitarlearner.domain.AppearanceFailure
import com.pekochan069.guitarlearner.domain.AppearanceSettings
import com.pekochan069.guitarlearner.domain.AppearanceSnapshot
import com.pekochan069.guitarlearner.domain.BeatAccent
import com.pekochan069.guitarlearner.domain.LanguagePreference
import com.pekochan069.guitarlearner.domain.Metronome
import com.pekochan069.guitarlearner.domain.MetronomeCommand
import com.pekochan069.guitarlearner.domain.MetronomeConfig
import com.pekochan069.guitarlearner.domain.MetronomeFailure
import com.pekochan069.guitarlearner.domain.MetronomePreset
import com.pekochan069.guitarlearner.domain.MetronomeSnapshot
import com.pekochan069.guitarlearner.domain.PlaybackState
import com.pekochan069.guitarlearner.domain.StopReason
import com.pekochan069.guitarlearner.domain.ThemePreference
import com.pekochan069.guitarlearner.presentation.contract.FoundationScreen
import com.pekochan069.guitarlearner.presentation.logic.FoundationPresenter
import com.pekochan069.guitarlearner.ui.FoundationUiFactory
import com.pekochan069.guitarlearner.ui.theme.GuitarLearnerTheme
import com.slack.circuit.foundation.Circuit
import com.slack.circuit.foundation.CircuitCompositionLocals
import com.slack.circuit.foundation.CircuitContent
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FoundationPresentationTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun productionHomeShowsOnlyUsableToolsAndHasNoDevelopmentSection(): Unit {
        val metronome = FakeMetronome()
        val circuit = testCircuit(FakeAppearance(), metronome, developmentSamplesEnabled = false)
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 2f)) {
                GuitarLearnerTheme(false) {
                    CircuitCompositionLocals(circuit) { CircuitContent(FoundationScreen) }
                }
            }
        }
        compose.onNodeWithTag("destination_title").assertDoesNotExist()
        compose.onNodeWithTag("category_Tools").assertExists()
        compose.onNodeWithTag("feature_Metronome").assertHasClickAction().assertIsDisplayed()
        val category = compose.onNodeWithTag("category_Tools").getUnclippedBoundsInRoot()
        val tile = compose.onNodeWithTag("feature_Metronome").getUnclippedBoundsInRoot()
        assertEquals((category.width.value - 12f) / 2, tile.width.value, 1f)
        assertEquals(category.left.value, tile.left.value, 1f)
        compose.onNodeWithTag("category_Training").assertDoesNotExist()
        compose.onNodeWithTag("category_Learning").assertDoesNotExist()
        compose.onNodeWithTag("sample_Tuner").assertDoesNotExist()
        compose.onNodeWithTag("sample_Gallery").assertDoesNotExist()
        compose.onNodeWithTag("navigate_up").assertDoesNotExist()
        compose.onNodeWithTag("settings").performClick()
        compose.onNodeWithTag("development_samples").assertDoesNotExist()
        compose.onNodeWithTag("close_settings").performClick()
        compose.openMetronome()
        compose.onNodeWithTag("navigate_up").performClick()
        compose.onNodeWithTag("feature_Metronome").assertExists()
        assertTrue(metronome.requests.isEmpty())
    }

    @Test
    fun homeScrollSurvivesFeatureVisitsAndRestoration(): Unit {
        val restore = StateRestorationTester(compose)
        val metronome = FakeMetronome()
        metronome.snapshot.value = MetronomeSnapshot(playback = PlaybackState.Playing(MetronomeConfig(), 0))
        val circuit = testCircuit(FakeAppearance(), metronome)
        restore.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 2f)) {
                Box(Modifier.height(280.dp)) {
                    GuitarLearnerTheme(false) {
                        CircuitCompositionLocals(circuit) { CircuitContent(FoundationScreen) }
                    }
                }
            }
        }
        compose.onNodeWithTag("feature_Metronome").performScrollTo()
        val position = compose.onNodeWithTag("home_scroll").fetchSemanticsNode()
            .config[SemanticsProperties.VerticalScrollAxisRange].value()
        assertTrue(position > 0f)
        compose.onNodeWithTag("feature_Metronome").performClick()
        compose.onNodeWithTag("navigate_up").performClick()
        assertEquals(position, compose.onNodeWithTag("home_scroll").fetchSemanticsNode()
            .config[SemanticsProperties.VerticalScrollAxisRange].value())
        restore.emulateSavedInstanceStateRestore()
        assertEquals(position, compose.onNodeWithTag("home_scroll").fetchSemanticsNode()
            .config[SemanticsProperties.VerticalScrollAxisRange].value())
        assertTrue(metronome.requests.isEmpty())
    }

    @Test
    fun compactControlUsesActualPlaybackAndFailedStopKeepsItsControls(): Unit {
        val metronome = FakeMetronome()
        val audible = MetronomeConfig(90, com.pekochan069.guitarlearner.domain.BeatUnit.Eighth, List(7) { BeatAccent.Normal })
        metronome.snapshot.value = MetronomeSnapshot(selected = MetronomeConfig(140), playback = PlaybackState.Playing(audible, 3))
        metronome.stopFailure = MetronomeFailure.ServiceUnavailable
        val circuit = testCircuit(FakeAppearance(), metronome)
        compose.setContent {
            GuitarLearnerTheme(false) {
                CircuitCompositionLocals(circuit) { CircuitContent(FoundationScreen) }
            }
        }
        val context = ApplicationProvider.getApplicationContext<Context>()
        val actual = context.getString(com.pekochan069.guitarlearner.ui.R.string.metronome_active_config, 7, 8, 90)
        compose.onNodeWithTag("compact_metronome_config").assertTextEquals(actual)
        compose.onNodeWithTag("compact_metronome_pending").assertExists()
        compose.onNodeWithTag("compact_metronome_open").performScrollTo().performClick()
        compose.onNodeWithTag("bpm_value").assertTextEquals("140")
        assertEquals(PlaybackState.Playing(audible, 3), metronome.snapshot.value.playback)
        assertTrue(metronome.requests.isEmpty())
        compose.onNodeWithTag("navigate_up").performClick()
        compose.onNodeWithTag("compact_metronome_stop").performScrollTo().performClick()
        compose.onNodeWithTag("compact_metronome_config").assertTextEquals(actual)
        compose.onNodeWithTag("metronome_error").performScrollTo().assertTextEquals(
            context.getString(com.pekochan069.guitarlearner.ui.R.string.metronome_service_unavailable))
        compose.onNodeWithTag("metronome_error_open").assertHasClickAction()
        metronome.stopFailure = null
        compose.onNodeWithTag("compact_metronome_stop").performScrollTo().performClick()
        compose.onNodeWithTag("compact_metronome").assertDoesNotExist()
        compose.onNodeWithTag("metronome_error").assertDoesNotExist()
        metronome.snapshot.value = metronome.snapshot.value.copy(playback = PlaybackState.Failed(MetronomeFailure.AudioUnavailable))
        compose.onNodeWithTag("compact_metronome").assertDoesNotExist()
        compose.onNodeWithTag("metronome_error").performScrollTo().assertTextEquals(
            context.getString(com.pekochan069.guitarlearner.ui.R.string.metronome_audio_unavailable))
        compose.onNodeWithTag("metronome_error_open").performScrollTo().performClick()
        compose.onNodeWithTag("toggle_metronome").assertTextEquals(context.getString(com.pekochan069.guitarlearner.ui.R.string.start_metronome))
        assertEquals(listOf(MetronomeCommand.Stop, MetronomeCommand.Stop), metronome.requests)
    }

    @Test
    fun preparationOnHomeHasStopAndOpenWithoutClaimingAnAudibleConfiguration(): Unit {
        val metronome = FakeMetronome()
        metronome.snapshot.value = MetronomeSnapshot(playback = PlaybackState.Preparing)
        val circuit = testCircuit(FakeAppearance(), metronome)
        compose.setContent {
            GuitarLearnerTheme(false) {
                CircuitCompositionLocals(circuit) { CircuitContent(FoundationScreen) }
            }
        }
        compose.onNodeWithTag("compact_metronome_config").assertDoesNotExist()
        compose.onNodeWithTag("compact_metronome_status").assertTextEquals(
            ApplicationProvider.getApplicationContext<Context>().getString(com.pekochan069.guitarlearner.ui.R.string.state_preparing))
        compose.onNodeWithTag("compact_metronome_open").assertHasClickAction()
        compose.onNodeWithTag("compact_metronome_stop").performScrollTo().performClick()
        compose.onNodeWithTag("compact_metronome").assertDoesNotExist()
        assertEquals(listOf(MetronomeCommand.Stop), metronome.requests)
    }

    @Test
    fun typedSaveFailureRendersLocalizedErrorAndKeepsCommittedChoice(): Unit {
        val settings = FakeAppearance()
        val circuit = testCircuit(settings, FakeMetronome())
        compose.setContent {
            GuitarLearnerTheme(false) {
                CircuitCompositionLocals(circuit) { CircuitContent(FoundationScreen) }
            }
        }
        compose.onNodeWithTag("settings").performClick()
        compose.onNodeWithTag("theme_Dark").performScrollTo().performClick()
        compose.onNodeWithTag("theme_Light").assertIsSelected().assertIsNotEnabled()
        compose.onNodeWithTag("theme_System").assertIsNotEnabled()
        settings.result.complete(Either.Left(AppearanceFailure.WriteFailed))
        compose.waitForIdle()

        val context = ApplicationProvider.getApplicationContext<Context>()
        compose.onNodeWithTag("appearance_error").performScrollTo().assertTextEquals(
            context.getString(com.pekochan069.guitarlearner.ui.R.string.appearance_save_failed),
        )
        compose.onNodeWithTag("theme_Light").assertIsSelected()
    }

    @Test
    fun samplesRestoreWhilePlaybackRemainsOwnedByTheSharedCapability(): Unit {
        val restore = StateRestorationTester(compose)
        val metronome = FakeMetronome()
        val circuit = testCircuit(FakeAppearance(), metronome)
        restore.setContent {
            GuitarLearnerTheme(false) {
                CircuitCompositionLocals(circuit) { CircuitContent(FoundationScreen) }
            }
        }
        compose.openDevelopmentSample(DevelopmentSample.Tuner)
        compose.onNodeWithTag("reading_Sharp").performScrollTo().performClick()
        compose.openMetronome()
        compose.onNodeWithTag("increase_bpm").performScrollTo().performClick()
        compose.onNodeWithTag("increase_bpm").performScrollTo().performClick()
        compose.onNodeWithTag("toggle_metronome").performScrollTo().performClick()
        compose.openDevelopmentSample(DevelopmentSample.Gallery)
        compose.onNodeWithTag("gallery_selection").performScrollTo().performClick()
        compose.onNodeWithTag("settings").performClick()

        restore.emulateSavedInstanceStateRestore()
        compose.onNodeWithTag("close_settings").performScrollTo().performClick()
        compose.onNodeWithTag("destination_title").assertTextEquals(
            ApplicationProvider.getApplicationContext<Context>().getString(com.pekochan069.guitarlearner.ui.R.string.gallery_title))
        compose.onNodeWithTag("gallery_selection").performScrollTo().assertIsOff()
        compose.openDevelopmentSample(DevelopmentSample.Tuner)
        compose.onNodeWithTag("reading_Sharp").performScrollTo().assertIsSelected()
        compose.openMetronome()
        compose.onNodeWithTag("bpm_value").assertTextEquals("92")
        val context = ApplicationProvider.getApplicationContext<Context>()
        compose.onNodeWithTag("metronome_status").assertTextEquals(context.getString(com.pekochan069.guitarlearner.ui.R.string.state_running))
        assertEquals(1, metronome.requests.count { it == MetronomeCommand.Start })

        metronome.snapshot.value = metronome.snapshot.value.copy(playback = PlaybackState.Stopped())
        restore.emulateSavedInstanceStateRestore()
        compose.onNodeWithTag("metronome_status").assertDoesNotExist()
        compose.onNodeWithTag("toggle_metronome").assertTextEquals(context.getString(com.pekochan069.guitarlearner.ui.R.string.start_metronome))
        assertEquals(1, metronome.requests.count { it == MetronomeCommand.Start })
    }

    @Test
    fun audibleBeatAndPendingSettingsRenderWithoutPerBeatLiveAnnouncements(): Unit {
        val metronome = FakeMetronome()
        val audible = MetronomeConfig(120)
        metronome.snapshot.value = MetronomeSnapshot(selected = audible.copy(bpm = 140,
            beats = listOf(BeatAccent.Accent, BeatAccent.Normal, BeatAccent.Mute, BeatAccent.Normal)),
            playback = PlaybackState.Playing(audible, 2))
        val circuit = testCircuit(FakeAppearance(), metronome)
        compose.setContent {
            GuitarLearnerTheme(false) {
                CircuitCompositionLocals(circuit) { CircuitContent(FoundationScreen) }
            }
        }
        compose.openMetronome()
        compose.onNodeWithTag("bpm_value").assertTextEquals("140")
        val context = ApplicationProvider.getApplicationContext<Context>()
        compose.onNodeWithTag("beat_accent_3").assertHasClickAction().assertContentDescriptionEquals(
            context.getString(com.pekochan069.guitarlearner.ui.R.string.beat_edit_description, 3,
                context.getString(com.pekochan069.guitarlearner.ui.R.string.beat_mute)),
        ).assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription,
            context.getString(com.pekochan069.guitarlearner.ui.R.string.beat_current_pending,
                context.getString(com.pekochan069.guitarlearner.ui.R.string.beat_normal))))
            .assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.LiveRegion)).performClick()
        assertEquals(BeatAccent.Accent, metronome.snapshot.value.selected.beats[2])
        compose.onNodeWithTag("current_beat").assertTextEquals(
            context.getString(com.pekochan069.guitarlearner.ui.R.string.current_beat_description, 3, 4,
                context.getString(com.pekochan069.guitarlearner.ui.R.string.beat_normal)),
        ).assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.LiveRegion))
        compose.onNodeWithTag("metronome_pending").assertExists()
        metronome.snapshot.value = metronome.snapshot.value.copy(playback = PlaybackState.Playing(metronome.snapshot.value.selected, 3))
        compose.onNodeWithTag("metronome_pending").assertDoesNotExist()
        compose.onNodeWithTag("current_beat").assertTextEquals(
            context.getString(com.pekochan069.guitarlearner.ui.R.string.current_beat_description, 4, 4,
                context.getString(com.pekochan069.guitarlearner.ui.R.string.beat_normal)),
        )
    }

    @Test
    fun overwriteCancelAndFailedSaveKeepTheEnteredNameAndSavedPreset(): Unit {
        val metronome = FakeMetronome()
        metronome.snapshot.value = MetronomeSnapshot(presets = listOf(MetronomePreset("Practice", MetronomeConfig(140))))
        metronome.presetResult = CompletableDeferred()
        val circuit = testCircuit(FakeAppearance(), metronome)
        compose.setContent {
            GuitarLearnerTheme(false) {
                CircuitCompositionLocals(circuit) { CircuitContent(FoundationScreen) }
            }
        }
        compose.openMetronome()
        compose.onNodeWithTag("preset_name").assertDoesNotExist()
        compose.onNodeWithTag("open_presets").performScrollTo().performClick()
        compose.onNodeWithTag("preset_name").performScrollTo().performTextInput("Practice")
        compose.onNodeWithTag("save_preset").performScrollTo().performClick()
        compose.onNodeWithTag("cancel_preset_overwrite").performClick()
        assertTrue(metronome.requests.isEmpty())
        compose.onNodeWithTag("preset_name").assertTextContains("Practice")
        compose.onNodeWithTag("save_preset").performScrollTo().performClick()
        compose.onNodeWithTag("confirm_preset_overwrite").performClick().assertIsNotEnabled()
        metronome.presetResult!!.complete(Either.Left(MetronomeFailure.WriteFailed))
        compose.onNodeWithTag("confirm_preset_overwrite").assertExists()
        val context = ApplicationProvider.getApplicationContext<Context>()
        compose.onNodeWithTag("preset_overwrite_error").assertIsDisplayed().assertTextEquals(
            context.getString(com.pekochan069.guitarlearner.ui.R.string.metronome_save_failed))
        compose.onNodeWithTag("cancel_preset_overwrite").performClick()
        compose.onNodeWithTag("metronome_error").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("preset_name").assertTextContains("Practice")
        compose.onNodeWithTag("load_preset_Practice").performScrollTo().assertExists()
        assertEquals(140, metronome.snapshot.value.presets.single().config.bpm)
        assertEquals(listOf(MetronomeCommand.SavePreset("Practice", overwrite = true)), metronome.requests)
    }

    @Test
    fun defaultPracticeControlsAreVisibleAndBeatsKeepIndividualTouchTargets(): Unit {
        val circuit = testCircuit(FakeAppearance(), FakeMetronome())
        compose.setContent {
            GuitarLearnerTheme(false) {
                CircuitCompositionLocals(circuit) { CircuitContent(FoundationScreen) }
            }
        }
        compose.openMetronome()
        val viewportBottom = compose.onNodeWithTag("feature_scroll").getUnclippedBoundsInRoot().bottom
        val minimumTouchSize = with(compose.density) { 48.dp.toPx() }
        val buttons = listOf("decrease_bpm", "increase_bpm", "beat_count", "beat_unit",
            "beat_accent_1", "beat_accent_2", "beat_accent_3", "beat_accent_4", "toggle_metronome", "open_presets")
        buttons.forEach { tag ->
            val button = compose.onNodeWithTag(tag)
            button.assertIsDisplayed().assertHasClickAction()
            val touch = button.fetchSemanticsNode().touchBoundsInRoot
            assertTrue("$tag touch width is ${touch.width}px", touch.width >= minimumTouchSize - 1f)
            assertTrue("$tag touch height is ${touch.height}px", touch.height >= minimumTouchSize - 1f)
            assertTrue("$tag extends below practice viewport", button.getUnclippedBoundsInRoot().bottom <= viewportBottom)
        }
        val slider = compose.onNodeWithTag("tempo_slider").assertIsDisplayed()
        val sliderTouch = slider.fetchSemanticsNode().touchBoundsInRoot
        assertTrue("Slider touch height is ${sliderTouch.height}px", sliderTouch.height >= minimumTouchSize - 1f)
        compose.onNodeWithTag("bpm_value").assertIsDisplayed()
        val rowTop = compose.onNodeWithTag("beat_accent_1").getUnclippedBoundsInRoot().top
        assertEquals(rowTop, compose.onNodeWithTag("beat_accent_4").getUnclippedBoundsInRoot().top)
        compose.onNodeWithTag("preset_name").assertDoesNotExist()
    }

    @Test
    fun beatsCycleThroughAllAccentsInThePracticeArea(): Unit {
        val metronome = FakeMetronome()
        val circuit = testCircuit(FakeAppearance(), metronome)
        compose.setContent {
            GuitarLearnerTheme(false) {
                CircuitCompositionLocals(circuit) { CircuitContent(FoundationScreen) }
            }
        }
        compose.openMetronome()
        val context = ApplicationProvider.getApplicationContext<Context>()
        for ((accent, label) in listOf(BeatAccent.Normal to com.pekochan069.guitarlearner.ui.R.string.beat_normal,
            BeatAccent.Mute to com.pekochan069.guitarlearner.ui.R.string.beat_mute,
            BeatAccent.Accent to com.pekochan069.guitarlearner.ui.R.string.beat_accent)) {
            compose.onNodeWithTag("beat_accent_1").performClick().assertContentDescriptionEquals(
                context.getString(com.pekochan069.guitarlearner.ui.R.string.beat_edit_description, 1, context.getString(label)))
            assertEquals(accent, metronome.snapshot.value.selected.beats.first())
        }
        assertTrue(metronome.snapshot.value.playback is PlaybackState.Stopped)
    }

    @Test
    fun beatAccentCyclesKeepEveryDefaultPracticeControlInPlace(): Unit {
        assertAccentCyclesKeepPracticeBounds(4, 1f)
    }

    @Test
    fun beatAccentCyclesKeepWrappedControlsInPlaceAtDoubleTextSize(): Unit {
        assertAccentCyclesKeepPracticeBounds(16, 2f)
    }

    @Test
    fun pendingAccentEditsKeepRunningPracticeControlsInPlace(): Unit {
        assertAccentCyclesKeepPracticeBounds(7, 1f, running = true)
    }

    private fun assertAccentCyclesKeepPracticeBounds(beatCount: Int, fontScale: Float, running: Boolean = false) {
        val metronome = FakeMetronome()
        val config = MetronomeConfig(beats = List(beatCount) { if (it == 0) BeatAccent.Accent else BeatAccent.Normal })
        metronome.snapshot.value = MetronomeSnapshot(selected = config,
            playback = if (running) PlaybackState.Playing(config, 0) else PlaybackState.Stopped())
        val circuit = testCircuit(FakeAppearance(), metronome)
        val context = ApplicationProvider.getApplicationContext<Context>()
        val english = context.createConfigurationContext(Configuration(context.resources.configuration).apply {
            setLocale(Locale.ENGLISH)
        })
        compose.setContent {
            CompositionLocalProvider(LocalContext provides english,
                LocalDensity provides Density(LocalDensity.current.density, fontScale)) {
                GuitarLearnerTheme(false) {
                    CircuitCompositionLocals(circuit) { CircuitContent(FoundationScreen) }
                }
            }
        }
        compose.openMetronome()
        val tags = (1..beatCount).map { "beat_accent_$it" } + listOf("toggle_metronome", "open_presets")
        val bounds = tags.associateWith { compose.onNodeWithTag(it).getUnclippedBoundsInRoot() }
        if (beatCount > 4) {
            assertTrue(bounds.getValue("beat_accent_$beatCount").top > bounds.getValue("beat_accent_1").top)
        }
        for (beat in listOf(1, beatCount / 2 + 1, beatCount)) {
            repeat(6) { cycle ->
                compose.onNodeWithTag("beat_accent_$beat").performSemanticsAction(SemanticsActions.OnClick) { it() }
                compose.waitForIdle()
                if (running && cycle % 3 == 0) compose.onNodeWithTag("metronome_pending").assertExists()
                tags.forEach { tag ->
                    assertEquals("$tag moved or resized after beat $beat accent cycle $cycle", bounds.getValue(tag),
                        compose.onNodeWithTag(tag).getUnclippedBoundsInRoot())
                }
            }
        }
        assertEquals(running, metronome.snapshot.value.playback is PlaybackState.Playing)
    }

    @Test
    fun everyMeterUsesEqualBeatCellsAndFixedColumns(): Unit {
        assertMetersKeepEqualGridGeometry(1f)
    }

    @Test
    fun everyMeterKeepsEqualCellsAndFixedColumnsAtDoubleTextSize(): Unit {
        assertMetersKeepEqualGridGeometry(2f)
    }

    private fun assertMetersKeepEqualGridGeometry(fontScale: Float) {
        val metronome = FakeMetronome()
        val circuit = testCircuit(FakeAppearance(), metronome)
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale)) {
                GuitarLearnerTheme(false) {
                    CircuitCompositionLocals(circuit) { CircuitContent(FoundationScreen) }
                }
            }
        }
        compose.openMetronome()
        val expectedColumns = listOf(1, 2, 3, 4, 4, 3, 4, 4, 3, 4, 4, 3, 4, 4, 3, 4)
        val tolerance = with(compose.density) { 1f.toDp().value.toDouble() }
        expectedColumns.forEachIndexed { index, columns ->
            val count = index + 1
            compose.onNodeWithTag("beat_count").performScrollTo().performClick()
            compose.onNodeWithTag("beat_count_option_$count").performScrollTo().performClick()
            assertEquals(count, metronome.snapshot.value.selected.numerator)
            val countField = compose.onNodeWithTag("beat_count").getUnclippedBoundsInRoot()
            val unitField = compose.onNodeWithTag("beat_unit").getUnclippedBoundsInRoot()
            assertEquals("Meter selectors have different widths", countField.width.value.toDouble(), unitField.width.value.toDouble(), tolerance)
            val cells = (1..count).map { compose.onNodeWithTag("beat_accent_$it").getUnclippedBoundsInRoot() }
            val rows = (count + columns - 1) / columns
            val firstRow = compose.onNodeWithTag("beat_row_1").getUnclippedBoundsInRoot()
            cells.forEachIndexed { cellIndex, cell ->
                assertEquals("$count beats: cell ${cellIndex + 1} width", cells.first().width.value.toDouble(), cell.width.value.toDouble(), tolerance)
                assertEquals("$count beats: cell ${cellIndex + 1} height", cells.first().height.value.toDouble(), cell.height.value.toDouble(), tolerance)
                val row = compose.onNodeWithTag("beat_row_${cellIndex / columns + 1}").getUnclippedBoundsInRoot()
                assertEquals("$count beats: cell ${cellIndex + 1} row", row.top, cell.top)
                assertEquals("$count beats: cell ${cellIndex + 1} column", cells[cellIndex % columns].left, cell.left)
            }
            for (row in 1..rows) {
                val rect = compose.onNodeWithTag("beat_row_$row").getUnclippedBoundsInRoot()
                assertEquals("$count beats: partial row must reserve every column", firstRow.width.value.toDouble(), rect.width.value.toDouble(), tolerance)
                assertEquals("$count beats: row heights", firstRow.height.value.toDouble(), rect.height.value.toDouble(), tolerance)
            }
            compose.onNodeWithTag("beat_row_${rows + 1}").assertDoesNotExist()
        }
    }

    @Test
    fun tempoKeepsItsQuarterNoteReferenceWhenTheMeterChanges(): Unit {
        val metronome = FakeMetronome()
        val circuit = testCircuit(FakeAppearance(), metronome)
        compose.setContent {
            GuitarLearnerTheme(false) {
                CircuitCompositionLocals(circuit) { CircuitContent(FoundationScreen) }
            }
        }
        compose.openMetronome()
        val context = ApplicationProvider.getApplicationContext<Context>()
        val tempo = context.resources.getQuantityString(com.pekochan069.guitarlearner.ui.R.plurals.tempo_bpm, 90, 90)
        val description = context.getString(com.pekochan069.guitarlearner.ui.R.string.tempo_note_description,
            context.getString(com.pekochan069.guitarlearner.ui.R.string.beat_quarter), tempo)
        for (unit in listOf(2, 4, 8, 16)) {
            compose.onNodeWithTag("beat_unit").performClick()
            compose.onNodeWithTag("beat_unit_option_$unit").performClick()
            compose.onNodeWithTag("bpm_value").assertTextEquals("90").assertContentDescriptionEquals(description)
            compose.onNodeWithTag("tempo_slider").assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, description))
            assertEquals(unit, metronome.snapshot.value.selected.denominator.denominator)
        }
        assertTrue(metronome.snapshot.value.playback is PlaybackState.Stopped)
    }

    @Test
    fun sixteenBeatsWrapAndRemainInteractiveAtDoubleTextSize(): Unit {
        val metronome = FakeMetronome()
        metronome.snapshot.value = MetronomeSnapshot(selected = MetronomeConfig(240, beats = List(16) { BeatAccent.Normal }))
        val circuit = testCircuit(FakeAppearance(), metronome)
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 2f)) {
                GuitarLearnerTheme(true) {
                    CircuitCompositionLocals(circuit) { CircuitContent(FoundationScreen) }
                }
            }
        }
        compose.openMetronome()
        val first = compose.onNodeWithTag("beat_accent_1").getUnclippedBoundsInRoot().top
        val last = compose.onNodeWithTag("beat_accent_16").getUnclippedBoundsInRoot().top
        assertTrue(last > first)
        compose.onNodeWithTag("beat_indicators").performScrollTo()
        compose.onNodeWithTag("beat_accent_16").performScrollTo().assertIsDisplayed()
            .assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp).performClick()
        compose.waitForIdle()
        assertEquals(BeatAccent.Mute, metronome.snapshot.value.selected.beats.last())
        compose.onNodeWithTag("toggle_metronome").performScrollTo().assertIsDisplayed().assertHasClickAction()
        compose.onNodeWithTag("open_presets").performScrollTo().performClick()
        compose.onNodeWithTag("preset_name").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("close_presets").performScrollTo().assertHasClickAction()
    }

    @Test
    fun presetsStayOnDemandAndSaveLoadOverwriteAndDeleteRemainAvailable(): Unit {
        val metronome = FakeMetronome()
        val circuit = testCircuit(FakeAppearance(), metronome)
        compose.setContent {
            GuitarLearnerTheme(false) {
                CircuitCompositionLocals(circuit) { CircuitContent(FoundationScreen) }
            }
        }
        compose.openMetronome()
        compose.onNodeWithTag("preset_name").assertDoesNotExist()
        compose.onNodeWithTag("open_presets").performScrollTo().performClick()
        compose.onNodeWithTag("preset_name").performTextInput("Practice")
        compose.onNodeWithTag("save_preset").performScrollTo().performClick()
        compose.onNodeWithTag("load_preset_Practice").performScrollTo().assertHasClickAction()
        compose.onNodeWithTag("close_presets").performScrollTo().performClick()
        compose.onNodeWithTag("preset_name").assertDoesNotExist()
        compose.onNodeWithTag("increase_bpm").performClick()
        compose.onNodeWithTag("open_presets").performScrollTo().performClick()
        compose.onNodeWithTag("load_preset_Practice").performScrollTo().performClick()
        compose.waitForIdle()
        assertEquals(90, metronome.snapshot.value.selected.bpm)
        compose.onNodeWithTag("preset_name").assertExists()
        compose.onNodeWithTag("close_presets").performScrollTo().performClick()
        compose.onNodeWithTag("increase_bpm").performClick()
        compose.onNodeWithTag("open_presets").performScrollTo().performClick()
        compose.onNodeWithTag("save_preset").performScrollTo().performClick()
        compose.onNodeWithTag("confirm_preset_overwrite").performClick()
        compose.waitForIdle()
        assertEquals(91, metronome.snapshot.value.presets.single().config.bpm)
        compose.onNodeWithTag("delete_preset_Practice").performScrollTo().performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("load_preset_Practice").assertDoesNotExist()
        compose.onNodeWithTag("preset_name").assertTextContains("Practice")
        assertTrue(metronome.snapshot.value.presets.isEmpty())
        assertFalse(metronome.requests.any { it == MetronomeCommand.Start })
    }

    @Test
    fun openPresetSheetAndEnteredNameSurviveStateRestoration(): Unit {
        val restore = StateRestorationTester(compose)
        val circuit = testCircuit(FakeAppearance(), FakeMetronome())
        restore.setContent {
            GuitarLearnerTheme(false) {
                CircuitCompositionLocals(circuit) { CircuitContent(FoundationScreen) }
            }
        }
        compose.openMetronome()
        compose.onNodeWithTag("open_presets").performScrollTo().performClick()
        compose.onNodeWithTag("preset_name").performTextInput("다음 연습")
        restore.emulateSavedInstanceStateRestore()
        compose.onNodeWithTag("preset_name").assertTextContains("다음 연습")
        compose.onNodeWithTag("close_presets").performClick()
        compose.onNodeWithTag("preset_name").assertDoesNotExist()
        compose.onNodeWithTag("open_presets").performScrollTo().performClick()
        compose.onNodeWithTag("preset_name").assertTextContains("다음 연습")
    }
}

private fun testCircuit(settings: AppearanceSettings, metronome: Metronome, developmentSamplesEnabled: Boolean = true): Circuit = Circuit.Builder()
    .addPresenterFactory(FoundationPresenter.Factory(settings, metronome, developmentSamplesEnabled))
    .addUiFactory(FoundationUiFactory)
    .build()

private class FakeAppearance : AppearanceSettings {
    override val current: StateFlow<AppearanceSnapshot> = MutableStateFlow(
        AppearanceSnapshot(ThemePreference.Light, LanguagePreference.System),
    ).asStateFlow()
    val result = CompletableDeferred<Either<AppearanceFailure, Unit>>()

    override suspend fun select(change: AppearanceChange): Either<AppearanceFailure, Unit> = result.await()
}

private class FakeMetronome : Metronome {
    val snapshot = MutableStateFlow(MetronomeSnapshot())
    override val current: StateFlow<MetronomeSnapshot> = snapshot.asStateFlow()
    val requests = mutableListOf<MetronomeCommand>()
    var presetResult: CompletableDeferred<Either<MetronomeFailure, Unit>>? = null
    var stopFailure: MetronomeFailure? = null

    override suspend fun execute(command: MetronomeCommand): Either<MetronomeFailure, Unit> {
        requests += command
        if (command == MetronomeCommand.Stop) stopFailure?.let { return Either.Left(it) }
        if (command is MetronomeCommand.SavePreset || command is MetronomeCommand.LoadPreset || command is MetronomeCommand.DeletePreset) {
            presetResult?.await()?.let { if (it.isLeft()) return it }
        }
        when (command) {
            MetronomeCommand.Start -> snapshot.value = snapshot.value.copy(playback = PlaybackState.Playing(snapshot.value.selected, 0))
            MetronomeCommand.Stop -> snapshot.value = snapshot.value.copy(playback = PlaybackState.Stopped(StopReason.User))
            is MetronomeCommand.SetTempo -> snapshot.value = snapshot.value.copy(selected = snapshot.value.selected.copy(bpm = command.bpm))
            is MetronomeCommand.SetPattern -> snapshot.value = snapshot.value.copy(selected = snapshot.value.selected.copy(denominator = command.denominator, beats = command.beats))
            is MetronomeCommand.SavePreset -> snapshot.value = snapshot.value.copy(presets =
                snapshot.value.presets.filterNot { it.name == command.name } + MetronomePreset(command.name, snapshot.value.selected))
            is MetronomeCommand.LoadPreset -> snapshot.value = snapshot.value.copy(selected = snapshot.value.presets.single { it.name == command.name }.config)
            is MetronomeCommand.DeletePreset -> snapshot.value = snapshot.value.copy(presets = snapshot.value.presets.filterNot { it.name == command.name })
        }
        return Either.Right(Unit)
    }
}
