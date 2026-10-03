package com.pekochan069.guitarlearner.adapters

import android.content.SharedPreferences
import arrow.core.Either
import com.pekochan069.guitarlearner.domain.AppearanceChange
import com.pekochan069.guitarlearner.domain.AppearanceFailure
import com.pekochan069.guitarlearner.domain.LanguagePreference
import com.pekochan069.guitarlearner.domain.ThemePreference
import java.lang.reflect.Proxy
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AndroidAppearanceHostTest {
    @Test
    fun failedCommitKeepsAcceptedThemeEvenWhenPreferenceMemoryChanges(): Unit = runTest {
        val storage = PreferenceDouble("Light")
        storage.commitSucceeds = false
        val applied = mutableListOf<ThemePreference>()
        val dispatcher = StandardTestDispatcher(testScheduler)
        val host = AndroidAppearanceHost(storage.preferences, dispatcher, dispatcher, applyTheme = applied::add)

        assertEquals(Either.Left(AppearanceFailure.WriteFailed), host.select(AppearanceChange.Theme(ThemePreference.Dark)))
        assertEquals("Dark", storage.theme)
        assertEquals(ThemePreference.Light, host.current.value.theme)
        assertTrue(applied.isEmpty())
        assertEquals(Either.Right(Unit), host.prepareActivityTheme())
        assertEquals(listOf(ThemePreference.Light), applied)
        assertEquals(1, storage.reads)
    }

    @Test
    fun successfulCommitPublishesBeforeApplyingAndDoesNotRepeatCommit(): Unit = runTest {
        val storage = PreferenceDouble("Light")
        val dispatcher = StandardTestDispatcher(testScheduler)
        lateinit var host: AndroidAppearanceHost
        val applied = mutableListOf<ThemePreference>()
        host = AndroidAppearanceHost(storage.preferences, dispatcher, dispatcher, applyTheme = {
            assertEquals(it, host.current.value.theme)
            applied += it
        })

        assertEquals(Either.Right(Unit), host.select(AppearanceChange.Theme(ThemePreference.Dark)))
        assertEquals("Dark", storage.theme)
        assertEquals(listOf(ThemePreference.Dark), applied)
        assertEquals(Either.Right(Unit), host.select(AppearanceChange.Theme(ThemePreference.Dark)))
        assertEquals(1, storage.commits)
    }

    @Test
    fun selectingCommittedThemeRetriesANativeFailure(): Unit = runTest {
        val storage = PreferenceDouble("Light")
        val dispatcher = StandardTestDispatcher(testScheduler)
        var nativeFailure = true
        val applied = mutableListOf<ThemePreference>()
        val host = AndroidAppearanceHost(storage.preferences, dispatcher, dispatcher, applyTheme = {
            if (nativeFailure) throw SecurityException()
            applied += it
        })
        assertEquals(Either.Left(AppearanceFailure.ThemeApplyFailed), host.select(AppearanceChange.Theme(ThemePreference.Dark)))
        nativeFailure = false

        assertEquals(Either.Right(Unit), host.select(AppearanceChange.Theme(ThemePreference.Dark)))
        assertEquals(listOf(ThemePreference.Dark), applied)
        assertEquals(1, storage.commits)
    }

    @Test
    fun selectingSystemRepairsAFailedBootstrapRead(): Unit = runTest {
        val storage = PreferenceDouble()
        storage.failReads = true
        val dispatcher = StandardTestDispatcher(testScheduler)
        val host = AndroidAppearanceHost(storage.preferences, dispatcher, dispatcher, applyTheme = {})
        assertEquals(Either.Left(AppearanceFailure.ReadFailed), host.prepareActivityTheme())

        assertEquals(Either.Right(Unit), host.select(AppearanceChange.Theme(ThemePreference.System)))
        assertEquals("System", storage.theme)
        assertEquals(Either.Right(Unit), host.prepareActivityTheme())
        assertEquals(1, storage.reads)
    }

    @Test
    fun themePublicationMergesLatestLanguageRefreshedDuringIo(): Unit = runTest {
        val storage = PreferenceDouble("Light")
        val main = StandardTestDispatcher(testScheduler, "main")
        val io = StandardTestDispatcher(testScheduler, "io")
        var nativeLanguage: LanguagePreference? = LanguagePreference.English
        val host = AndroidAppearanceHost(storage.preferences, io, main, readLanguage = { nativeLanguage }, applyTheme = {})
        storage.onCommit = {
            nativeLanguage = LanguagePreference.Korean
            backgroundScope.launch(main) {
                assertEquals(Either.Right(Unit), host.refreshPlatformLanguage())
            }
        }

        assertEquals(Either.Right(Unit), host.select(AppearanceChange.Theme(ThemePreference.Dark)))
        assertEquals(ThemePreference.Dark, host.current.value.theme)
        assertEquals(LanguagePreference.Korean, host.current.value.language)
    }

    @Test
    fun cancellationBeforeMutationDoesNotWrite(): Unit = runTest {
        val storage = PreferenceDouble("Light")
        val dispatcher = StandardTestDispatcher(testScheduler)
        val host = AndroidAppearanceHost(storage.preferences, dispatcher, dispatcher, applyTheme = {})
        val change = launch { host.select(AppearanceChange.Theme(ThemePreference.Dark)) }
        change.cancel()
        runCurrent()

        assertTrue(change.isCancelled)
        assertEquals(0, storage.commits)
        assertEquals(ThemePreference.Light, host.current.value.theme)
    }

    @Test
    fun waitingMutationCanBeCancelledWithoutStartingASecondCommit(): Unit = runTest {
        val storage = PreferenceDouble("Light")
        val dispatcher = StandardTestDispatcher(testScheduler)
        val host = AndroidAppearanceHost(storage.preferences, dispatcher, dispatcher, applyTheme = {})
        val accepted = async { host.select(AppearanceChange.Theme(ThemePreference.Dark)) }
        val waiting = launch { host.select(AppearanceChange.Theme(ThemePreference.Light)) }
        storage.onCommit = { waiting.cancel() }
        runCurrent()

        assertEquals(Either.Right(Unit), accepted.await())
        assertTrue(waiting.isCancelled)
        assertEquals(1, storage.commits)
        assertEquals(ThemePreference.Dark, host.current.value.theme)
    }

    @Test
    fun cancellationDuringCommitStillPublishesAndAppliesThenPropagates(): Unit = runTest {
        val storage = PreferenceDouble("Light")
        val dispatcher = StandardTestDispatcher(testScheduler)
        val applied = mutableListOf<ThemePreference>()
        val host = AndroidAppearanceHost(storage.preferences, dispatcher, dispatcher, applyTheme = applied::add)
        lateinit var change: Job
        var returned: Either<AppearanceFailure, Unit>? = null
        storage.onCommit = { change.cancel() }
        change = launch { returned = host.select(AppearanceChange.Theme(ThemePreference.Dark)) }
        runCurrent()

        assertTrue(change.isCancelled)
        assertNull(returned)
        assertEquals("Dark", storage.theme)
        assertEquals(ThemePreference.Dark, host.current.value.theme)
        assertEquals(listOf(ThemePreference.Dark), applied)
    }

    @Test
    fun platformFailureDoesNotPretendSuccessfulStorageWasRolledBack(): Unit = runTest {
        val storage = PreferenceDouble("Light")
        val dispatcher = StandardTestDispatcher(testScheduler)
        val host = AndroidAppearanceHost(storage.preferences, dispatcher, dispatcher, applyTheme = { throw SecurityException() })

        assertEquals(Either.Left(AppearanceFailure.ThemeApplyFailed), host.select(AppearanceChange.Theme(ThemePreference.Dark)))
        assertEquals("Dark", storage.theme)
        assertEquals(ThemePreference.Dark, host.current.value.theme)
    }

    @Test
    fun partialNativeFailurePublishesObservedLanguageAndCanBeRepaired(): Unit = runTest {
        val storage = PreferenceDouble()
        val dispatcher = StandardTestDispatcher(testScheduler)
        var nativeLanguage: LanguagePreference? = LanguagePreference.System
        var failApplication = true
        val host = AndroidAppearanceHost(
            storage.preferences, dispatcher, dispatcher,
            readLanguage = { nativeLanguage },
            applyLanguage = {
                nativeLanguage = it
                if (failApplication) throw SecurityException()
            },
        )
        assertEquals(Either.Left(AppearanceFailure.LanguageApplyFailed), host.select(AppearanceChange.Language(LanguagePreference.Korean)))
        assertEquals(LanguagePreference.Korean, host.current.value.language)
        assertEquals(LanguagePreference.Korean, nativeLanguage)
        failApplication = false

        assertEquals(Either.Right(Unit), host.select(AppearanceChange.Language(LanguagePreference.System)))
        assertEquals(LanguagePreference.System, nativeLanguage)
        assertEquals(LanguagePreference.System, host.current.value.language)
    }

    @Test
    fun localeSelectionReadsNativeAcceptanceAndUnknownResumeHasNoOfferedChoice(): Unit = runTest {
        val storage = PreferenceDouble()
        val dispatcher = StandardTestDispatcher(testScheduler)
        var nativeLanguage: LanguagePreference? = LanguagePreference.System
        val host = AndroidAppearanceHost(
            storage.preferences, dispatcher, dispatcher,
            readLanguage = { nativeLanguage },
            applyLanguage = { nativeLanguage = it },
        )

        assertEquals(Either.Right(Unit), host.select(AppearanceChange.Language(LanguagePreference.Korean)))
        assertEquals(LanguagePreference.Korean, host.current.value.language)
        nativeLanguage = null
        assertEquals(Either.Right(Unit), host.refreshPlatformLanguage())
        assertNull(host.current.value.language)
        assertEquals(0, storage.commits)
    }
}

private class PreferenceDouble(initialTheme: String? = null) {
    var theme: String? = initialTheme
    var commitSucceeds: Boolean = true
    var commits: Int = 0
    var reads: Int = 0
    var failReads: Boolean = false
    var onCommit: () -> Unit = {}
    val preferences: SharedPreferences = Proxy.newProxyInstance(
        SharedPreferences::class.java.classLoader,
        arrayOf(SharedPreferences::class.java),
    ) { _, method, arguments ->
        when (method.name) {
            "getString" -> {
                reads++
                if (failReads) throw ClassCastException()
                theme ?: arguments?.get(1)
            }
            "edit" -> editor()
            else -> error("Unexpected SharedPreferences operation ${method.name}")
        }
    } as SharedPreferences

    private fun editor(): SharedPreferences.Editor {
        var pending: String? = null
        return Proxy.newProxyInstance(
            SharedPreferences.Editor::class.java.classLoader,
            arrayOf(SharedPreferences.Editor::class.java),
        ) { proxy, method, arguments ->
            when (method.name) {
                "putString" -> { pending = arguments?.get(1) as String?; proxy }
                "commit" -> { theme = pending; commits++; onCommit(); commitSucceeds }
                else -> error("Unexpected Editor operation ${method.name}")
            }
        } as SharedPreferences.Editor
    }
}
