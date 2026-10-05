package com.pekochan069.guitarlearner.adapters

import android.content.SharedPreferences
import arrow.core.Either
import com.pekochan069.guitarlearner.domain.*
import java.lang.reflect.Proxy
import org.junit.Assert.*
import org.junit.Test

class TrainingStorageTest {
    @Test fun everySettingAndEmptyOrSelectedPoolsRoundTripWithoutSessionData() {
        for (subject in TrainingSubject.entries) for (representation in TrainingRepresentation.entries) {
            for (presentation in IntervalPresentation.entries) for (pool in listOf(emptySet(), TrainingInterval.entries.toSet(),
                setOf(TrainingInterval.MinorThird, TrainingInterval.PerfectFifth))) {
                val settings = TrainingSettings(subject, representation, presentation, pool)
                assertEquals(settings, decodeTrainingSettings(encodeTrainingSettings(settings)))
            }
        }
        val preferences = Preferences(null)
        val store = TrainingStorage(preferences.value)
        assertEquals(Either.Right(TrainingSettings()), store.read())
        val selected = TrainingSettings(TrainingSubject.Interval, TrainingRepresentation.Tab,
            IntervalPresentation.Descending, setOf(TrainingInterval.Octave))
        assertEquals(Either.Right(Unit), store.write(selected))
        assertEquals(Either.Right(selected), TrainingStorage(preferences.value).read())
    }

    @Test fun failedCommitRestoresAcceptedMemoryAndRetryPersistsOnlyTheAcknowledgedSelection() {
        val selected = TrainingSettings(representation = TrainingRepresentation.Staff)
        val original = encodeTrainingSettings(selected)
        val preferences = Preferences(original)
        val store = TrainingStorage(preferences.value)
        assertEquals(Either.Right(selected), store.read())
        preferences.failNextCommit = true
        assertEquals(Either.Left(TrainingFailure.SettingsWriteFailed), store.write(TrainingSettings()))
        assertEquals(original, preferences.source)
        assertEquals(Either.Right(selected), TrainingStorage(preferences.value).read())
        assertEquals(2, preferences.commits)
        assertEquals(Either.Right(Unit), store.write(TrainingSettings()))
        assertEquals(Either.Right(TrainingSettings()), TrainingStorage(preferences.value).read())
        val absent = Preferences(null)
        val absentStore = TrainingStorage(absent.value)
        assertTrue(absentStore.read().isRight())
        absent.failNextCommit = true
        assertTrue(absentStore.write(selected).isLeft())
        assertNull(absent.source)
    }

    @Test fun malformedSourcesCannotBeOverwrittenAndExplicitReloadAllowsRecovery() {
        val valid = encodeTrainingSettings(TrainingSettings())
        for (source in listOf("", "2|Note|Listening|Ascending|", "1|Note|Missing|Ascending|",
            "1|Note|Listening|Ascending|Unison,Unison", "1|Note|Listening|Ascending|Unknown", 17)) {
            val preferences = Preferences(source)
            val store = TrainingStorage(preferences.value)
            assertEquals(Either.Left(TrainingFailure.SettingsReadFailed), store.read())
            assertEquals(Either.Left(TrainingFailure.SettingsReadFailed), store.write(TrainingSettings()))
            assertEquals(source, preferences.source)
            assertEquals(0, preferences.commits)
            preferences.source = valid
            assertEquals(Either.Right(TrainingSettings()), store.read())
            assertTrue(store.write(TrainingSettings(representation = TrainingRepresentation.Tab)).isRight())
        }
        val denied = Preferences(null)
        denied.failRead = true
        assertEquals(Either.Left(TrainingFailure.SettingsReadFailed), TrainingStorage(denied.value).read())
    }

    private class Preferences(var source: Any?) {
        var failNextCommit = false
        var failRead = false
        var commits = 0
        val value: SharedPreferences = Proxy.newProxyInstance(SharedPreferences::class.java.classLoader,
            arrayOf(SharedPreferences::class.java)) { _, method, arguments ->
            when (method.name) {
                "getString" -> {
                    if (failRead) throw SecurityException()
                    if (source != null && source !is String) throw ClassCastException()
                    source ?: arguments?.get(1)
                }
                "edit" -> editor()
                else -> error("Unexpected preferences call ${method.name}")
            }
        } as SharedPreferences

        private fun editor(): SharedPreferences.Editor {
            var pending = source
            return Proxy.newProxyInstance(SharedPreferences.Editor::class.java.classLoader,
                arrayOf(SharedPreferences.Editor::class.java)) { proxy, method, arguments ->
                when (method.name) {
                    "putString" -> { pending = arguments?.get(1); proxy }
                    "remove" -> { pending = null; proxy }
                    "commit" -> {
                        source = pending
                        commits++
                        val success = !failNextCommit
                        failNextCommit = false
                        success
                    }
                    else -> error("Unexpected editor call ${method.name}")
                }
            } as SharedPreferences.Editor
        }
    }
}
