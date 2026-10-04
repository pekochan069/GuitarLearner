package com.pekochan069.guitarlearner.adapters

import android.content.SharedPreferences
import arrow.core.Either
import com.pekochan069.guitarlearner.domain.TunerFailure
import com.pekochan069.guitarlearner.domain.TuningTolerance
import java.lang.reflect.Proxy
import org.junit.Assert.*
import org.junit.Test

class TunerToleranceStorageTest {
    @Test fun missingDefaultsToFiveAndOnlyAcknowledgedChoicesLoadInAFreshStore() {
        val preferences = Preferences(null)
        val store = TunerToleranceStorage(preferences.value)
        assertEquals(Either.Right(TuningTolerance.Normal), store.read())
        assertEquals(Either.Right(Unit), store.write(TuningTolerance.Strict))
        assertEquals("3", preferences.source)
        assertEquals(Either.Right(TuningTolerance.Strict), TunerToleranceStorage(preferences.value).read())
    }

    @Test fun failedCommitRestoresAcceptedMemoryAndLeavesTheDurableChoiceAcknowledged() {
        val preferences = Preferences("10")
        val store = TunerToleranceStorage(preferences.value)
        store.read().fold({ fail("Initial read failed: $it") }, {})
        preferences.failNextCommit = true
        assertEquals(Either.Left(TunerFailure.ToleranceWriteFailed), store.write(TuningTolerance.Strict))
        assertEquals("10", preferences.source)
        assertEquals(Either.Right(TuningTolerance.Relaxed), TunerToleranceStorage(preferences.value).read())
        assertEquals(2, preferences.commits)
    }

    @Test fun failedFirstWriteRestoresAbsence() {
        val preferences = Preferences(null)
        val store = TunerToleranceStorage(preferences.value)
        store.read().fold({ fail("Initial read failed: $it") }, {})
        preferences.failNextCommit = true
        assertEquals(Either.Left(TunerFailure.ToleranceWriteFailed), store.write(TuningTolerance.Relaxed))
        assertNull(preferences.source)
        assertEquals(Either.Right(TuningTolerance.Normal), TunerToleranceStorage(preferences.value).read())
    }

    @Test fun corruptOrInaccessiblePreferencesFailVisiblyAndCanBeReloadedAfterRecovery() {
        for (source in listOf("bad", "4", 10)) {
            val preferences = Preferences(source)
            val store = TunerToleranceStorage(preferences.value)
            assertEquals(Either.Left(TunerFailure.ToleranceReadFailed), store.read())
            assertEquals(Either.Left(TunerFailure.ToleranceReadFailed), store.write(TuningTolerance.Strict))
            assertEquals(source, preferences.source)
            preferences.source = "5"
            assertEquals(Either.Right(TuningTolerance.Normal), store.read())
            assertEquals(Either.Right(Unit), store.write(TuningTolerance.Relaxed))
        }
        val inaccessible = Preferences(null)
        inaccessible.failRead = true
        assertEquals(Either.Left(TunerFailure.ToleranceReadFailed), TunerToleranceStorage(inaccessible.value).read())
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
