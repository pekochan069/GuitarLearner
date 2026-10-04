package com.pekochan069.guitarlearner

import android.content.Context
import android.content.res.Configuration
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import arrow.core.Either
import com.pekochan069.guitarlearner.adapters.AndroidChordsHost
import com.pekochan069.guitarlearner.domain.AppearanceChange
import com.pekochan069.guitarlearner.domain.AppearanceFailure
import com.pekochan069.guitarlearner.domain.AppearanceSettings
import com.pekochan069.guitarlearner.domain.AppearanceSnapshot
import com.pekochan069.guitarlearner.domain.ChordCommand
import com.pekochan069.guitarlearner.domain.ChordLookup
import com.pekochan069.guitarlearner.domain.DraftPersistence
import com.pekochan069.guitarlearner.domain.LanguagePreference
import com.pekochan069.guitarlearner.domain.Metronome
import com.pekochan069.guitarlearner.domain.MetronomeCommand
import com.pekochan069.guitarlearner.domain.MetronomeConfig
import com.pekochan069.guitarlearner.domain.MetronomeFailure
import com.pekochan069.guitarlearner.domain.MetronomeSnapshot
import com.pekochan069.guitarlearner.domain.PlaybackState
import com.pekochan069.guitarlearner.domain.StringStop
import com.pekochan069.guitarlearner.domain.ThemePreference
import com.pekochan069.guitarlearner.presentation.contract.FoundationScreen
import com.pekochan069.guitarlearner.presentation.logic.FoundationPresenter
import com.pekochan069.guitarlearner.ui.FoundationUiFactory
import com.pekochan069.guitarlearner.ui.R as UiR
import com.pekochan069.guitarlearner.ui.theme.GuitarLearnerTheme
import com.slack.circuit.foundation.Circuit
import com.slack.circuit.foundation.CircuitCompositionLocals
import com.slack.circuit.foundation.CircuitContent
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ChordPresentationUiTest {
    @get:Rule val compose = createComposeRule()
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val preferenceName = "chord-ui-test-${UUID.randomUUID()}"
    private lateinit var host: AndroidChordsHost
    private val metronome = ChordUiMetronome()

    @Before fun setUp() { host = AndroidChordsHost(context.getSharedPreferences(preferenceName, Context.MODE_PRIVATE)) }
    @After fun tearDown() { context.deleteSharedPreferences(preferenceName) }

    @Test fun lookupEditSaveRestartLoadAndDeleteUseTheRealLocalWorkspace() {
        show(Locale.ENGLISH, dark = false, scale = 1f)
        click("feature_Chords")
        compose.onNodeWithTag("compact_metronome").assertExists()
        click("chord_search")
        compose.waitUntil(10_000) { (host.current.value.lookup as? ChordLookup.Ready)?.shapes?.isNotEmpty() == true }
        compose.onNodeWithTag("chord_lookup_fretboard").assertExists()
        compose.onNodeWithTag("chord_lookup_summary").assertTextEquals("C")
        click("chord_next")
        click("chord_copy")
        compose.onNodeWithTag("chord_editor_fretboard").assertExists()
        click("chord_new")
        compose.waitUntil(5_000) { !host.current.value.draft.shape.hasSound }
        compose.onNodeWithTag("chord_save").assertIsNotEnabled()
        setFret(1, 3)
        setFret(2, 2)
        setOpen(3)
        setFret(4, 1)
        setOpen(5)
        compose.onNodeWithTag("chord_summary").assertTextEquals("C")
        compose.onNodeWithTag("chord_editor_position_0").assertExists()
        compose.onNodeWithTag("chord_name").performScrollTo().performTextInput("Home C")
        compose.waitUntil(5_000) { host.current.value.draft.name == "Home C" }
        compose.onNodeWithTag("chord_save").performScrollTo().assertIsEnabled().performClick()
        compose.waitUntil(5_000) { host.current.value.records.size == 1 }
        val id = host.current.value.records.single().id
        val savedShape = host.current.value.draft.shape
        compose.onNodeWithTag("chord_capo").performScrollTo().performTextReplacement("2")
        compose.waitUntil(5_000) { host.current.value.draft.context.capo == 2 && host.current.value.persistence == DraftPersistence.Synced }
        compose.onNodeWithTag("chord_summary").assertTextEquals("D")
        compose.onNodeWithTag("chord_shape_summary").assertTextContains("C")
        assertEquals(savedShape, host.current.value.draft.shape)
        val restarted = AndroidChordsHost(context.getSharedPreferences(preferenceName, Context.MODE_PRIVATE))
        assertEquals(host.current.value.draft, restarted.current.value.draft)
        assertEquals(0, restarted.current.value.records.single().content.context.capo)
        click("chord_section_Collection")
        click("chord_load_$id")
        compose.waitUntil(5_000) { host.current.value.draft.context.capo == 0 }
        compose.onNodeWithTag("chord_summary").assertTextEquals("C")
        click("chord_section_Collection")
        click("chord_delete_$id")
        compose.onNodeWithTag("chord_confirm_delete").performClick()
        compose.waitUntil(5_000) { host.current.value.records.isEmpty() }
        assertEquals(null, host.current.value.draft.targetId)
        val afterDelete = AndroidChordsHost(context.getSharedPreferences(preferenceName, Context.MODE_PRIVATE))
        assertTrue(afterDelete.current.value.records.isEmpty())
        assertEquals(null, afterDelete.current.value.draft.targetId)
        assertTrue(metronome.requests.isEmpty())
    }

    @Test fun englishLightLargeTextKeepsErrorsOmissionsAndSaveRecoveryAccessible() { largeTextJourney(Locale.ENGLISH, false) }
    @Test fun koreanDarkLargeTextKeepsErrorsOmissionsAndSaveRecoveryAccessible() { largeTextJourney(Locale.KOREAN, true) }

    private fun largeTextJourney(locale: Locale, dark: Boolean) {
        runBlocking {
            val stops = listOf(StringStop.Muted, StringStop.Fretted(3), StringStop.Fretted(2), StringStop.Fretted(3), StringStop.Fretted(1), StringStop.Open)
            for (index in 0..5) assertEquals(Either.Right(Unit), host.execute(ChordCommand.SetStop(index, stops[index])))
            assertEquals(Either.Right(Unit), host.execute(ChordCommand.SetName("My C7")))
        }
        show(locale, dark, 2f)
        click("feature_Chords")
        click("chord_section_Edit")
        compose.onNodeWithTag("chord_summary").assertTextEquals("C7")
        compose.onNodeWithTag("chord_candidate_0").assertTextContains(if (locale == Locale.KOREAN) "생략음 5" else "omitted 5")
        compose.onNodeWithTag("chord_save").performScrollTo().assertHeightIsAtLeast(48.dp).assertIsEnabled()
        compose.onNodeWithTag("chord_capo").performScrollTo().performTextReplacement("99")
        compose.onNodeWithTag("chord_capo").assertTextContains("99")
        val localized = context.createConfigurationContext(Configuration(context.resources.configuration).apply { setLocale(locale) })
        compose.onNodeWithText(localized.getString(UiR.string.chord_capo_error)).assertExists()
        compose.onNodeWithTag("chord_accepted_capo").assertTextContains("0")
        compose.onNodeWithTag("chord_save").assertIsNotEnabled()
        compose.onNodeWithTag("chord_summary").assertTextEquals("C7")
        compose.onNodeWithTag("chord_capo").performScrollTo().performTextReplacement("0")
        click("chord_custom_tuning")
        compose.onNodeWithTag("chord_octave_0").performScrollTo().performTextReplacement("bad")
        compose.onNodeWithTag("chord_octave_0").assertTextContains("bad")
        compose.onNodeWithText(localized.getString(UiR.string.chord_octave_error)).assertExists()
        assertEquals(2, host.current.value.draft.context.tuning.pitches[0].octave)
        compose.onNodeWithTag("chord_save").assertIsNotEnabled()
        compose.onNodeWithTag("chord_octave_0").performScrollTo().performTextReplacement("2")
        compose.onNodeWithTag("chord_save").performScrollTo().assertIsEnabled()
        compose.onNodeWithTag("chord_stop_5_Open").performScrollTo().assertHasClickAction().assertHeightIsAtLeast(48.dp)
        compose.onNodeWithTag("chord_editor_fretboard").assertExists()
        compose.onNodeWithTag("chord_editor_position_0").assertContentDescriptionEquals(
            if (locale == Locale.KOREAN) "6번 줄, X · 뮤트" else "String 6, X · muted")
    }

    private fun show(locale: Locale, dark: Boolean, scale: Float) {
        val localized = context.createConfigurationContext(Configuration(context.resources.configuration).apply { setLocale(locale) })
        val circuit = Circuit.Builder().addPresenterFactory(FoundationPresenter.Factory(ChordUiAppearance(), metronome, host))
            .addUiFactory(FoundationUiFactory).build()
        compose.setContent {
            CompositionLocalProvider(LocalContext provides localized, LocalDensity provides Density(LocalDensity.current.density, scale)) {
                GuitarLearnerTheme(dark) { CircuitCompositionLocals(circuit) { CircuitContent(FoundationScreen) } }
            }
        }
    }

    private fun click(tag: String) { compose.onNodeWithTag(tag).performScrollTo().performClick() }
    private fun setFret(index: Int, fret: Int) {
        click("chord_stop_${index}_Fretted")
        compose.waitUntil(5_000) { host.current.value.draft.shape.stops[index] is StringStop.Fretted }
        compose.onNodeWithTag("chord_fret_$index").performScrollTo().performTextReplacement(fret.toString())
        compose.waitUntil(5_000) { host.current.value.draft.shape.stops[index] == StringStop.Fretted(fret) }
    }
    private fun setOpen(index: Int) {
        click("chord_stop_${index}_Open")
        compose.waitUntil(5_000) { host.current.value.draft.shape.stops[index] == StringStop.Open }
    }
}

private class ChordUiAppearance : AppearanceSettings {
    override val current = MutableStateFlow(AppearanceSnapshot(ThemePreference.System, LanguagePreference.System)).asStateFlow()
    override suspend fun select(change: AppearanceChange): Either<AppearanceFailure, Unit> = Either.Right(Unit)
}

private class ChordUiMetronome : Metronome {
    private val config = MetronomeConfig()
    override val current = MutableStateFlow(MetronomeSnapshot(playback = PlaybackState.Playing(config, 0))).asStateFlow()
    val requests = mutableListOf<MetronomeCommand>()
    override suspend fun execute(command: MetronomeCommand): Either<MetronomeFailure, Unit> { requests += command; return Either.Right(Unit) }
}
