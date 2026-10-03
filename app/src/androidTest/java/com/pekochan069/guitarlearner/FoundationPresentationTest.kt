package com.pekochan069.guitarlearner

import android.content.Context
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import arrow.core.Either
import com.pekochan069.guitarlearner.domain.AppearanceChange
import com.pekochan069.guitarlearner.domain.AppearanceFailure
import com.pekochan069.guitarlearner.domain.AppearanceSettings
import com.pekochan069.guitarlearner.domain.AppearanceSnapshot
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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FoundationPresentationTest {
    @get:Rule
    val compose = createComposeRule()

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
        compose.onNodeWithTag("reading_Sharp").performScrollTo().performClick()
        compose.onNodeWithTag("page_Metronome").performClick()
        compose.onNodeWithTag("increase_bpm").performScrollTo().performClick()
        compose.onNodeWithTag("increase_bpm").performScrollTo().performClick()
        compose.onNodeWithTag("toggle_metronome").performScrollTo().performClick()
        compose.onNodeWithTag("page_Gallery").performClick()
        compose.onNodeWithTag("gallery_selection").performScrollTo().performClick()
        compose.onNodeWithTag("settings").performClick()

        restore.emulateSavedInstanceStateRestore()
        compose.onNodeWithTag("close_settings").performScrollTo().performClick()
        compose.onNodeWithTag("page_Gallery").assertIsSelected()
        compose.onNodeWithTag("gallery_selection").performScrollTo().assertIsOff()
        compose.onNodeWithTag("page_Tuner").performClick()
        compose.onNodeWithTag("reading_Sharp").performScrollTo().assertIsSelected()
        compose.onNodeWithTag("page_Metronome").performClick()
        compose.onNodeWithTag("bpm_value").assertTextEquals("92")
        val context = ApplicationProvider.getApplicationContext<Context>()
        compose.onNodeWithTag("metronome_status").assertTextEquals(context.getString(com.pekochan069.guitarlearner.ui.R.string.state_running))
        assertEquals(1, metronome.requests.count { it == MetronomeCommand.Start })

        metronome.snapshot.value = metronome.snapshot.value.copy(playback = PlaybackState.Stopped())
        restore.emulateSavedInstanceStateRestore()
        compose.onNodeWithTag("metronome_status").assertTextEquals(context.getString(com.pekochan069.guitarlearner.ui.R.string.state_stopped))
        assertEquals(1, metronome.requests.count { it == MetronomeCommand.Start })
    }

    @Test
    fun audibleBeatAndPendingSettingsRenderWithoutPerBeatLiveAnnouncements(): Unit {
        val metronome = FakeMetronome()
        val audible = MetronomeConfig(120)
        metronome.snapshot.value = MetronomeSnapshot(selected = audible.copy(bpm = 140), playback = PlaybackState.Playing(audible, 2))
        val circuit = testCircuit(FakeAppearance(), metronome)
        compose.setContent {
            GuitarLearnerTheme(false) {
                CircuitCompositionLocals(circuit) { CircuitContent(FoundationScreen) }
            }
        }
        compose.onNodeWithTag("page_Metronome").performClick()
        compose.onNodeWithTag("bpm_value").assertTextEquals("140")
        val context = ApplicationProvider.getApplicationContext<Context>()
        compose.onNodeWithTag("beat_indicators").assertContentDescriptionEquals(
            context.getString(com.pekochan069.guitarlearner.ui.R.string.current_beat_description, 3, 4,
                context.getString(com.pekochan069.guitarlearner.ui.R.string.beat_normal)),
        ).assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.LiveRegion))
        compose.onNodeWithTag("metronome_pending").assertExists()
        metronome.snapshot.value = metronome.snapshot.value.copy(playback = PlaybackState.Playing(audible.copy(bpm = 140), 3))
        compose.onNodeWithTag("metronome_pending").assertDoesNotExist()
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
        compose.onNodeWithTag("page_Metronome").performClick()
        compose.onNodeWithTag("preset_name").performScrollTo().performTextInput("Practice")
        compose.onNodeWithTag("save_preset").performScrollTo().performClick()
        compose.onNodeWithTag("cancel_preset_overwrite").performClick()
        assertTrue(metronome.requests.isEmpty())
        compose.onNodeWithTag("preset_name").assertTextContains("Practice")
        compose.onNodeWithTag("save_preset").performScrollTo().performClick()
        compose.onNodeWithTag("confirm_preset_overwrite").performClick().assertIsNotEnabled()
        metronome.presetResult!!.complete(Either.Left(MetronomeFailure.WriteFailed))
        compose.onNodeWithTag("confirm_preset_overwrite").assertExists()
        compose.onNodeWithTag("cancel_preset_overwrite").performClick()
        compose.onNodeWithTag("preset_name").assertTextContains("Practice")
        compose.onNodeWithTag("load_preset_Practice").performScrollTo().assertExists()
        assertEquals(140, metronome.snapshot.value.presets.single().config.bpm)
        assertEquals(listOf(MetronomeCommand.SavePreset("Practice", overwrite = true)), metronome.requests)
    }
}

private fun testCircuit(settings: AppearanceSettings, metronome: Metronome): Circuit = Circuit.Builder()
    .addPresenterFactory(FoundationPresenter.Factory(settings, metronome))
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

    override suspend fun execute(command: MetronomeCommand): Either<MetronomeFailure, Unit> {
        requests += command
        when (command) {
            MetronomeCommand.Start -> snapshot.value = snapshot.value.copy(playback = PlaybackState.Playing(snapshot.value.selected, 0))
            MetronomeCommand.Stop -> snapshot.value = snapshot.value.copy(playback = PlaybackState.Stopped(StopReason.User))
            is MetronomeCommand.SetTempo -> snapshot.value = snapshot.value.copy(selected = snapshot.value.selected.copy(bpm = command.bpm))
            is MetronomeCommand.SetPattern -> snapshot.value = snapshot.value.copy(selected = snapshot.value.selected.copy(denominator = command.denominator, beats = command.beats))
            is MetronomeCommand.SavePreset -> presetResult?.let { return it.await() }
            is MetronomeCommand.LoadPreset, is MetronomeCommand.DeletePreset -> Unit
        }
        return Either.Right(Unit)
    }
}
