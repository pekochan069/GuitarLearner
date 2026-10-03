package com.pekochan069.guitarlearner.adapters

import android.content.SharedPreferences
import arrow.core.Either
import com.pekochan069.guitarlearner.domain.MetronomeConfig
import com.pekochan069.guitarlearner.domain.MetronomeFailure
import com.pekochan069.guitarlearner.domain.MetronomePreset
import java.lang.reflect.Proxy
import org.junit.Assert.assertEquals
import org.junit.Test

class MetronomeStorageTest {
    private val original = MetronomeDocument(MetronomeConfig(90), listOf(MetronomePreset("Practice", MetronomeConfig(120))))
    private val replacement = original.copy(selected = MetronomeConfig(240), presets = listOf(MetronomePreset("Practice", MetronomeConfig(240))))

    private fun storage(preferences: SharedPreferences): MetronomeStorage = MetronomeStorage(preferences,
        decode = { when (it) { "original" -> original; "replacement" -> replacement; else -> throw IllegalArgumentException() } },
        encode = { when (it) { original -> "original"; replacement -> "replacement"; else -> "empty" } },
    )

    @Test
    fun successfulWriteLoadsBothCurrentConfigurationAndPresetsInAFreshStore() {
        val preferences = MetronomePreferences("original")
        assertEquals(Either.Right(Unit), storage(preferences.value).save(replacement))
        assertEquals(Either.Right(replacement), storage(preferences.value).initial)
    }

    @Test
    fun failedOverwriteRestoresPreferenceMemoryAsWellAsRetainingSavedSource() {
        val preferences = MetronomePreferences("original")
        preferences.failNextCommit = true
        assertEquals(Either.Left(MetronomeFailure.WriteFailed), storage(preferences.value).save(replacement))
        assertEquals("original", preferences.source)
        assertEquals(Either.Right(original), storage(preferences.value).initial)
        assertEquals(2, preferences.commits)
    }

    @Test
    fun failedFirstWriteRestoresAbsentDocument() {
        val preferences = MetronomePreferences(null)
        preferences.failNextCommit = true
        assertEquals(Either.Left(MetronomeFailure.WriteFailed), storage(preferences.value).save(replacement))
        assertEquals(null, preferences.source)
        assertEquals(Either.Right(MetronomeDocument()), storage(preferences.value).initial)
    }

    @Test
    fun corruptSourceIsReportedAndNotSilentlyReplaced() {
        val preferences = MetronomePreferences("corrupt")
        val store = storage(preferences.value)
        assertEquals(Either.Left(MetronomeFailure.ReadFailed), store.initial)
        assertEquals(Either.Left(MetronomeFailure.ReadFailed), store.save(replacement))
        assertEquals("corrupt", preferences.source)
        assertEquals(0, preferences.commits)
    }
}

private class MetronomePreferences(var source: String?) {
    var failNextCommit = false
    var commits = 0
    val value: SharedPreferences = Proxy.newProxyInstance(SharedPreferences::class.java.classLoader,
        arrayOf(SharedPreferences::class.java)) { _, method, arguments ->
        when (method.name) {
            "getString" -> source ?: arguments?.get(1)
            "edit" -> editor()
            else -> error("Unexpected preferences call ${method.name}")
        }
    } as SharedPreferences

    private fun editor(): SharedPreferences.Editor {
        var pending: String? = source
        return Proxy.newProxyInstance(SharedPreferences.Editor::class.java.classLoader,
            arrayOf(SharedPreferences.Editor::class.java)) { proxy, method, arguments ->
            when (method.name) {
                "putString" -> { pending = arguments?.get(1) as String?; proxy }
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
