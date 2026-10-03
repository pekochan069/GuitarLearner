package com.pekochan069.guitarlearner.presentation.logic

import arrow.core.Either
import com.pekochan069.guitarlearner.domain.AppearanceChange
import com.pekochan069.guitarlearner.domain.AppearanceFailure
import com.pekochan069.guitarlearner.domain.AppearanceSettings
import com.pekochan069.guitarlearner.domain.AppearanceSnapshot
import com.pekochan069.guitarlearner.domain.LanguagePreference
import com.pekochan069.guitarlearner.domain.ThemePreference
import com.pekochan069.guitarlearner.presentation.contract.AppearanceNotice
import com.pekochan069.guitarlearner.presentation.contract.FoundationEvent
import com.pekochan069.guitarlearner.presentation.contract.LanguageOption
import com.pekochan069.guitarlearner.presentation.contract.Page
import com.pekochan069.guitarlearner.presentation.contract.Reading
import com.pekochan069.guitarlearner.presentation.contract.SettingsStatus
import com.pekochan069.guitarlearner.presentation.contract.ThemeOption
import com.slack.circuit.test.test
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FoundationPresenterTest {
    @Test
    fun failedSettingRetainsCommittedSelectionAndRejectsASecondTap(): Unit = runTest {
        val settings = ControlledAppearance()
        FoundationPresenter(settings).test {
            val initial = awaitItem()
            assertEquals(ThemeOption.Light, initial.theme)
            initial.eventSink(FoundationEvent.SelectTheme(ThemeOption.Dark))
            initial.eventSink(FoundationEvent.SelectTheme(ThemeOption.System))
            assertEquals(SettingsStatus.Saving, awaitItem().settingsStatus)
            runCurrent()
            assertEquals(1, settings.requests.size)
            settings.result.complete(Either.Left(AppearanceFailure.WriteFailed))
            val failed = awaitItem()
            assertEquals(SettingsStatus.Failed(AppearanceNotice.SaveFailed), failed.settingsStatus)
            assertEquals(ThemeOption.Light, failed.theme)
            failed.eventSink(FoundationEvent.DismissNotice)
            assertEquals(SettingsStatus.Idle, awaitItem().settingsStatus)
        }
    }

    @Test
    fun samplesStayLocalAndAppearanceIsObservedFromTheCapability(): Unit = runTest {
        val settings = ControlledAppearance()
        FoundationPresenter(settings).test {
            var state = awaitItem()
            assertEquals(90, state.bpm)
            assertFalse(state.running)
            assertTrue(state.gallerySelected)
            assertFalse(state.settingsOpen)
            state.eventSink(FoundationEvent.SetBpm(999))
            state = awaitItem()
            assertEquals(240, state.bpm)
            state.eventSink(FoundationEvent.SetBpm(-1))
            state = awaitItem()
            assertEquals(40, state.bpm)
            state.eventSink(FoundationEvent.SelectPage(Page.Gallery))
            state = awaitItem()
            assertEquals(Page.Gallery, state.page)
            state.eventSink(FoundationEvent.SetReading(Reading.Sharp))
            state = awaitItem()
            assertEquals(Reading.Sharp, state.reading)
            state.eventSink(FoundationEvent.SetRunning(true))
            state = awaitItem()
            assertTrue(state.running)
            state.eventSink(FoundationEvent.SetGallerySelected(false))
            state = awaitItem()
            assertFalse(state.gallerySelected)
            state.eventSink(FoundationEvent.SetSettingsOpen(true))
            state = awaitItem()
            assertTrue(state.settingsOpen)
            settings.snapshot.value = AppearanceSnapshot(ThemePreference.Dark, LanguagePreference.Korean)
            state = awaitItem()
            assertEquals(ThemeOption.Dark, state.theme)
            assertEquals(LanguageOption.Korean, state.language)
            assertTrue(settings.requests.isEmpty())
        }
    }
}

private class ControlledAppearance : AppearanceSettings {
    val snapshot: MutableStateFlow<AppearanceSnapshot> = MutableStateFlow(
        AppearanceSnapshot(ThemePreference.Light, LanguagePreference.System),
    )
    override val current: StateFlow<AppearanceSnapshot> = snapshot.asStateFlow()
    val requests: MutableList<AppearanceChange> = mutableListOf()
    val result: CompletableDeferred<Either<AppearanceFailure, Unit>> = CompletableDeferred()

    override suspend fun select(change: AppearanceChange): Either<AppearanceFailure, Unit> {
        requests += change
        return result.await()
    }
}
