package com.pekochan069.guitarlearner

import android.content.Context
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import arrow.core.Either
import com.pekochan069.guitarlearner.domain.AppearanceChange
import com.pekochan069.guitarlearner.domain.AppearanceFailure
import com.pekochan069.guitarlearner.domain.AppearanceSettings
import com.pekochan069.guitarlearner.domain.AppearanceSnapshot
import com.pekochan069.guitarlearner.domain.LanguagePreference
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
        val circuit = testCircuit(settings)
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
    fun allSixSampleValuesRestoreIncludingOpenSettings(): Unit {
        val restore = StateRestorationTester(compose)
        val circuit = testCircuit(FakeAppearance())
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
        compose.onNodeWithTag("metronome_status").assertTextEquals(
            context.getString(com.pekochan069.guitarlearner.ui.R.string.state_running),
        )
    }
}

private fun testCircuit(settings: AppearanceSettings): Circuit = Circuit.Builder()
    .addPresenterFactory(FoundationPresenter.Factory(settings))
    .addUiFactory(FoundationUiFactory)
    .build()

private class FakeAppearance : AppearanceSettings {
    override val current: StateFlow<AppearanceSnapshot> = MutableStateFlow(
        AppearanceSnapshot(ThemePreference.Light, LanguagePreference.System),
    ).asStateFlow()
    val result: CompletableDeferred<Either<AppearanceFailure, Unit>> = CompletableDeferred()

    override suspend fun select(change: AppearanceChange): Either<AppearanceFailure, Unit> = result.await()
}
