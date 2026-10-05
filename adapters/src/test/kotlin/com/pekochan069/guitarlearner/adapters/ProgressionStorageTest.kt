package com.pekochan069.guitarlearner.adapters

import android.content.SharedPreferences
import arrow.core.Either
import com.pekochan069.guitarlearner.domain.*
import java.lang.reflect.Proxy
import org.junit.Assert.*
import org.junit.Test

class ProgressionStorageTest {
    @Test fun failedCommitRestoresAcceptedPreferenceMemoryAndRetryKeepsTheEdit() {
        val fixture = Fixture()
        val store = fixture.storage()
        val original = ProgressionDocument(ProgressionDraft("first"))
        val edited = ProgressionDocument(ProgressionDraft("edited"))
        assertEquals(Either.Right(Unit), store.save(original))
        val accepted = fixture.source
        fixture.fail = true
        assertEquals(Either.Left(ProgressionFailure.WriteFailed), store.save(edited))
        assertEquals(accepted, fixture.source)
        assertEquals(Either.Right(original), store.read())
        assertEquals(Either.Right(Unit), store.save(edited))
        assertEquals(Either.Right(edited), fixture.storage().initial)
    }
    @Test fun unreadableSourceCannotBeOverwrittenWithAnEmptyDocument() {
        val fixture = Fixture().also { it.source = "bad document" }
        val store = fixture.storage()
        assertEquals(Either.Left(ProgressionFailure.ReadFailed), store.initial)
        assertEquals(Either.Left(ProgressionFailure.ReadFailed), store.save(ProgressionDocument()))
        assertEquals("bad document", fixture.source)
        fixture.source = null
        assertEquals(Either.Right(ProgressionDocument()), store.read())
        assertEquals(Either.Right(Unit), store.save(ProgressionDocument()))
    }
    private class Fixture {
        var source: String? = null
        var fail = false
        private var serial = 0
        private val documents = mutableMapOf<String, ProgressionDocument>()
        private val preferences = Proxy.newProxyInstance(SharedPreferences::class.java.classLoader,
            arrayOf(SharedPreferences::class.java)) { _, method, args ->
            when (method.name) {
                "getString" -> source ?: args?.get(1)
                "edit" -> {
                    var pending = source
                    Proxy.newProxyInstance(SharedPreferences.Editor::class.java.classLoader, arrayOf(SharedPreferences.Editor::class.java)) { proxy, edit, values ->
                        when (edit.name) {
                            "putString" -> { pending = values?.get(1) as String?; proxy }
                            "remove" -> { pending = null; proxy }
                            "commit" -> { source = pending; (!fail).also { fail = false } }
                            else -> error(edit.name)
                        }
                    }
                }
                else -> error(method.name)
            }
        } as SharedPreferences
        fun storage(): ProgressionStorage = ProgressionStorage(preferences,
            decode = { documents[it] ?: throw IllegalArgumentException("bad document") },
            encode = { value -> "document-${++serial}".also { documents[it] = value } })
    }
}
