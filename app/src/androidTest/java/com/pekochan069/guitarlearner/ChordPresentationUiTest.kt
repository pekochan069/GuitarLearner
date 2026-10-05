package com.pekochan069.guitarlearner

import android.content.Context
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.view.ContextThemeWrapper
import androidx.activity.ComponentActivity
import androidx.core.content.edit
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
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
import com.pekochan069.guitarlearner.domain.ChordFailure
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
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
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
        assertEquals(listOf(StringStop.Muted, StringStop.Fretted(3), StringStop.Fretted(2), StringStop.Open,
            StringStop.Fretted(1), StringStop.Open), (host.current.value.lookup as ChordLookup.Ready).shapes.first().stops)
        compose.onNodeWithTag("chord_lookup_fretboard").assertExists()
        compose.onNodeWithTag("chord_lookup_summary").assertTextEquals("C")
        compose.onNodeWithTag("chord_lookup_position_2").assertContentDescriptionEquals(
            "String 4, relative fret 2, sounds E3, degree 3")
        click("chord_next")
        click("chord_copy")
        compose.onNodeWithTag("chord_editor_fretboard").assertExists()
        click("chord_new")
        compose.waitUntil(5_000) { !host.current.value.draft.shape.hasSound }
        compose.onNodeWithTag("chord_save").assertIsNotEnabled()
        compose.onNodeWithTag("chord_fret_0").assertDoesNotExist()
        val treble = compose.onNodeWithTag("chord_editor_position_5").getUnclippedBoundsInRoot()
        val bass = compose.onNodeWithTag("chord_editor_position_0").getUnclippedBoundsInRoot()
        assertTrue(treble.top < bass.top)
        setFret(0, 12)
        compose.onNodeWithTag("chord_editor_fret_0_12").assertIsOn().performClick()
        compose.waitUntil(5_000) { host.current.value.draft.shape.stops[0] == StringStop.Muted }
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
        click("chord_open_context")
        compose.onNodeWithTag("chord_capo").performScrollTo().performTextReplacement("2")
        compose.waitUntil(5_000) { host.current.value.draft.context.capo == 2 && host.current.value.persistence == DraftPersistence.Synced }
        click("chord_close_context")
        compose.onNodeWithTag("chord_summary").assertTextEquals("D")
        compose.onNodeWithTag("chord_shape_summary").assertTextEquals("Shape name without capo · C")
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

    @Test fun englishLightLargeTextKeepsInputCorrectionAndOmissionsAccessible() { largeTextInputCorrectionJourney(Locale.ENGLISH, false) }
    @Test fun englishLightLandscapeLargeTextKeepsInputCorrectionAndOmissionsAccessible() {
        largeTextInputCorrectionJourney(Locale.ENGLISH, false, landscape = true)
    }
    @Test fun koreanDarkLargeTextKeepsInputCorrectionAndOmissionsAccessible() { largeTextInputCorrectionJourney(Locale.KOREAN, true) }

    @Test fun unreadableCollectionShowsRecoveryUntilStorageCanConfirmItIsEmpty() {
        val preferences = context.getSharedPreferences(preferenceName, Context.MODE_PRIVATE)
        preferences.edit(commit = true) { putString("document", "not json") }
        host = AndroidChordsHost(preferences)
        assertEquals(ChordFailure.ReadFailed, host.current.value.readFailure)
        show(Locale.ENGLISH, dark = false, scale = 1f)
        click("feature_Chords")
        click("chord_section_Collection")
        compose.onNodeWithTag("chord_read_error").assertExists()
        compose.onNodeWithTag("chord_retry_read").assertIsEnabled()
        compose.onNodeWithTag("chord_collection_empty").assertDoesNotExist()
        preferences.edit(commit = true) { remove("document") }
        click("chord_retry_read")
        compose.waitUntil(5_000) { host.current.value.readFailure == null }
        compose.onNodeWithTag("chord_read_error").assertDoesNotExist()
        compose.onNodeWithTag("chord_collection_empty").assertExists()
    }

    private fun largeTextInputCorrectionJourney(locale: Locale, dark: Boolean, landscape: Boolean = false) {
        if (landscape) {
            compose.runOnUiThread { compose.activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
            compose.waitUntil(5_000) { compose.activity.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE }
        }
        runBlocking {
            val stops = listOf(StringStop.Muted, StringStop.Fretted(3), StringStop.Fretted(2), StringStop.Fretted(3), StringStop.Fretted(1), StringStop.Open)
            for (index in 0..5) assertEquals(Either.Right(Unit), host.execute(ChordCommand.SetStop(index, stops[index])))
            assertEquals(Either.Right(Unit), host.execute(ChordCommand.SetName("My C7")))
        }
        show(locale, dark, 2f)
        click("feature_Chords")
        click("chord_section_Edit")
        compose.onNodeWithTag("chord_summary").assertTextEquals("C7")
        compose.onNodeWithTag("chord_candidate_0").assertTextEquals(if (locale == Locale.KOREAN) "C7 · 생략음 5" else "C7 · omitted 5")
        compose.onNodeWithTag("chord_save").performScrollTo().assertHeightIsAtLeast(48.dp).assertIsEnabled()
        click("chord_open_context")
        compose.onNodeWithTag("chord_context_sheet").assertIsDisplayed()
        compose.onNodeWithTag("chord_capo").performScrollTo().performTextReplacement("99")
        compose.onNodeWithTag("chord_capo").assertTextContains("99")
        val localized = context.createConfigurationContext(Configuration(context.resources.configuration).apply { setLocale(locale) })
        compose.onNodeWithText(localized.getString(UiR.string.chord_capo_error)).assertExists()
        compose.onNodeWithTag("chord_accepted_capo").assertTextEquals(
            if (locale == Locale.KOREAN) "현재 전체 카포 · 0프렛" else "Current full capo · fret 0")
        click("chord_close_context")
        compose.onNodeWithTag("chord_context_sheet").assertDoesNotExist()
        compose.onNodeWithTag("chord_context_error").assertExists()
        compose.onNodeWithTag("chord_save").assertIsNotEnabled()
        compose.onNodeWithTag("chord_summary").assertTextEquals("C7")
        click("chord_open_context")
        compose.onNodeWithTag("chord_capo").performScrollTo().performTextReplacement("0")
        click("chord_custom_tuning")
        compose.onNodeWithTag("chord_octave_0").performScrollTo().performTextReplacement("bad")
        compose.onNodeWithTag("chord_octave_0").assertTextContains("bad")
        compose.onNodeWithText(localized.getString(UiR.string.chord_octave_error)).assertExists()
        assertEquals(2, host.current.value.draft.context.tuning.pitches[0].octave)
        click("chord_close_context")
        compose.onNodeWithTag("chord_context_error").assertExists()
        compose.onNodeWithTag("chord_save").assertIsNotEnabled()
        click("chord_open_context")
        compose.onNodeWithTag("chord_octave_0").performScrollTo().performTextReplacement("2")
        click("chord_close_context")
        compose.onNodeWithTag("chord_context_error").assertDoesNotExist()
        compose.onNodeWithTag("chord_save").performScrollTo().assertIsEnabled()
        compose.onNodeWithTag("chord_editor_fret_5_0").performScrollTo().assertHasClickAction()
            .assertHeightIsAtLeast(48.dp).assertWidthIsAtLeast(48.dp)
        click("chord_precise_input")
        compose.onNodeWithTag("chord_stop_5_Open").performScrollTo().assertHasClickAction().assertHeightIsAtLeast(48.dp)
        compose.onNodeWithTag("chord_editor_fretboard").assertExists()
        compose.onNodeWithTag("chord_editor_position_0").assertContentDescriptionEquals(
            if (locale == Locale.KOREAN) "6번 줄, X · 뮤트" else "String 6, X · muted")
    }

    private fun show(locale: Locale, dark: Boolean, scale: Float) {
        val circuit = Circuit.Builder().addPresenterFactory(FoundationPresenter.Factory(ChordUiAppearance(), metronome, FakeTuner(), host, FakeProgressions()))
            .addUiFactory(FoundationUiFactory).build()
        compose.runOnUiThread {
            val localized = ContextThemeWrapper(compose.activity, compose.activity.theme).apply {
                applyOverrideConfiguration(Configuration(compose.activity.resources.configuration).apply {
                    setLocale(locale)
                    fontScale = scale
                })
            }
            compose.activity.setContentView(ComposeView(localized).apply {
                setContent { GuitarLearnerTheme(dark) { CircuitCompositionLocals(circuit) { CircuitContent(FoundationScreen) } } }
            })
        }
    }

    private fun click(tag: String) {
        if (tag.startsWith("chord_section_")) {
            compose.onNodeWithTag("feature_scroll").performScrollToNode(
                hasScrollAction() and hasAnyDescendant(hasTestTag(tag)))
            compose.onNodeWithTag(tag).performScrollTo().assertIsDisplayed().performClick().assertIsSelected()
        } else {
            compose.onNodeWithTag(tag).performScrollTo().performClick()
        }
    }
    private fun setFret(index: Int, fret: Int) {
        compose.onNodeWithTag("chord_editor_fret_${index}_$fret").performScrollTo()
            .assertHeightIsAtLeast(48.dp).assertWidthIsAtLeast(48.dp).performClick()
        compose.waitUntil(5_000) { host.current.value.draft.shape.stops[index] == StringStop.Fretted(fret) }
        compose.onNodeWithTag("chord_editor_fret_${index}_$fret").assertIsOn()
    }
    private fun setOpen(index: Int) {
        compose.onNodeWithTag("chord_editor_fret_${index}_0").performScrollTo().performClick()
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
