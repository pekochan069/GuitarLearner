package com.pekochan069.guitarlearner.adapters

import android.content.SharedPreferences
import arrow.core.Either
import com.pekochan069.guitarlearner.domain.ChordCommand
import com.pekochan069.guitarlearner.domain.ChordFailure
import com.pekochan069.guitarlearner.domain.ChordIdentity
import com.pekochan069.guitarlearner.domain.ChordLookup
import com.pekochan069.guitarlearner.domain.ChordQuality
import com.pekochan069.guitarlearner.domain.ChordTheory
import com.pekochan069.guitarlearner.domain.DraftPersistence
import com.pekochan069.guitarlearner.domain.GuitarPitch
import com.pekochan069.guitarlearner.domain.GuitarTuning
import com.pekochan069.guitarlearner.domain.PitchClass
import com.pekochan069.guitarlearner.domain.StringStop
import java.lang.reflect.Proxy
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AndroidChordsHostTest {
    @Test fun repeatedQueuedSavesUpdateOneStableRecordAndRecoverTheDraft() = runTest {
        val fixture = ChordFixture()
        val dispatcher = StandardTestDispatcher(testScheduler)
        val host = fixture.host(dispatcher)
        succeed(host, ChordCommand.SetStop(0, StringStop.Open))
        succeed(host, ChordCommand.SetName("  My note  "))
        List(3) { launch { succeed(host, ChordCommand.SaveDraft) } }.forEach { it.join() }
        val state = host.current.value
        assertEquals(1, state.records.size)
        assertEquals("My note", state.records.single().content.name)
        assertEquals(state.records.single().id, state.draft.targetId)
        val restored = fixture.host(dispatcher).current.value
        assertEquals(state.draft, restored.draft)
        assertEquals(state.records, restored.records)
    }

    @Test fun equalPersonalNamesDoNotReplaceDifferentOriginalsAndDraftEditsStayIndependent() = runTest {
        val fixture = ChordFixture()
        val dispatcher = StandardTestDispatcher(testScheduler)
        val host = fixture.host(dispatcher)
        succeed(host, ChordCommand.SetStop(0, StringStop.Open))
        succeed(host, ChordCommand.SetName("Same name"))
        succeed(host, ChordCommand.SaveDraft)
        val first = host.current.value.records.single()
        succeed(host, ChordCommand.NewDraft)
        succeed(host, ChordCommand.SetStop(1, StringStop.Open))
        succeed(host, ChordCommand.SetName("Same name"))
        succeed(host, ChordCommand.SaveDraft)
        val second = host.current.value.records.first { it.id != first.id }
        succeed(host, ChordCommand.LoadRecord(first.id))
        succeed(host, ChordCommand.SetCapo(5))
        assertEquals(first, host.current.value.records.first { it.id == first.id })
        val restarted = fixture.host(dispatcher)
        assertEquals(5, restarted.current.value.draft.context.capo)
        assertEquals(first, restarted.current.value.records.first { it.id == first.id })
        succeed(restarted, ChordCommand.SaveDraft)
        assertEquals(5, restarted.current.value.records.first { it.id == first.id }.content.context.capo)
        assertEquals(second, restarted.current.value.records.first { it.id == second.id })
    }

    @Test fun deleteDetachesRestoredDraftAndOnlyExplicitSaveCreatesANewIdentity() = runTest {
        val fixture = ChordFixture()
        val dispatcher = StandardTestDispatcher(testScheduler)
        val host = fixture.host(dispatcher)
        succeed(host, ChordCommand.SetStop(0, StringStop.Open))
        succeed(host, ChordCommand.SetName("Note"))
        succeed(host, ChordCommand.SaveDraft)
        val oldId = host.current.value.draft.targetId!!
        succeed(host, ChordCommand.DeleteRecord(oldId))
        val restored = fixture.host(dispatcher)
        assertTrue(restored.current.value.records.isEmpty())
        assertEquals(null, restored.current.value.draft.targetId)
        succeed(restored, ChordCommand.RetryDraftWrite)
        assertTrue(restored.current.value.records.isEmpty())
        succeed(restored, ChordCommand.SaveDraft)
        assertNotEquals(oldId, restored.current.value.records.single().id)
    }

    @Test fun failedDraftCommitRetainsVisibleWorkAndRetryDoesNotSaveAnOriginal() = runTest {
        val fixture = ChordFixture()
        val dispatcher = StandardTestDispatcher(testScheduler)
        val host = fixture.host(dispatcher)
        succeed(host, ChordCommand.SetStop(0, StringStop.Open))
        val accepted = fixture.preferences.source
        fixture.preferences.failNextCommit = true
        assertEquals(Either.Left(ChordFailure.WriteFailed), host.execute(ChordCommand.SetName("Unfinished")))
        assertEquals("Unfinished", host.current.value.draft.name)
        assertEquals(DraftPersistence.Unsynced, host.current.value.persistence)
        assertEquals(accepted, fixture.preferences.source)
        assertEquals("", fixture.host(dispatcher).current.value.draft.name)
        succeed(host, ChordCommand.RetryDraftWrite)
        assertEquals("Unfinished", fixture.host(dispatcher).current.value.draft.name)
        assertTrue(host.current.value.records.isEmpty())
    }

    @Test fun failedSaveAndDeleteKeepTheAcceptedCollectionAndPreferenceMemory() = runTest {
        val fixture = ChordFixture()
        val dispatcher = StandardTestDispatcher(testScheduler)
        val host = fixture.host(dispatcher)
        succeed(host, ChordCommand.SetStop(0, StringStop.Open))
        succeed(host, ChordCommand.SetName("Original"))
        succeed(host, ChordCommand.SaveDraft)
        val original = host.current.value.records.single()
        succeed(host, ChordCommand.SetName("Changed draft"))
        val accepted = fixture.preferences.source
        fixture.preferences.failNextCommit = true
        assertEquals(Either.Left(ChordFailure.WriteFailed), host.execute(ChordCommand.SaveDraft))
        assertEquals(listOf(original), host.current.value.records)
        assertEquals(accepted, fixture.preferences.source)
        fixture.preferences.failNextCommit = true
        assertEquals(Either.Left(ChordFailure.WriteFailed), host.execute(ChordCommand.DeleteRecord(original.id)))
        assertEquals(original.id, host.current.value.draft.targetId)
        assertEquals(listOf(original), fixture.host(dispatcher).current.value.records)
    }

    @Test fun corruptReadBlocksWritesAndReadRetryKeepsUnsyncedWork() = runTest {
        val fixture = ChordFixture("unsupported or corrupt")
        val dispatcher = StandardTestDispatcher(testScheduler)
        val host = fixture.host(dispatcher)
        assertEquals(ChordFailure.ReadFailed, host.current.value.readFailure)
        assertEquals(Either.Left(ChordFailure.ReadFailed), host.execute(ChordCommand.SetName("Keep visible")))
        assertEquals("Keep visible", host.current.value.draft.name)
        assertEquals(0, fixture.preferences.commits)
        assertEquals("unsupported or corrupt", fixture.preferences.source)
        assertEquals(Either.Left(ChordFailure.ReadFailed), host.execute(ChordCommand.RetryStorageRead))
        fixture.preferences.source = null
        succeed(host, ChordCommand.RetryStorageRead)
        assertEquals("Keep visible", host.current.value.draft.name)
        assertEquals(DraftPersistence.Unsynced, host.current.value.persistence)
        succeed(host, ChordCommand.RetryDraftWrite)
        assertEquals("Keep visible", fixture.host(dispatcher).current.value.draft.name)
    }

    @Test fun validationKeepsValidContextAndTypedActionFailures() = runTest {
        val fixture = ChordFixture()
        val host = fixture.host(StandardTestDispatcher(testScheduler))
        assertEquals(Either.Left(ChordFailure.EmptyShape), host.execute(ChordCommand.SaveDraft))
        succeed(host, ChordCommand.SetStop(0, StringStop.Open))
        assertEquals(Either.Left(ChordFailure.InvalidName), host.execute(ChordCommand.SaveDraft))
        val draft = host.current.value.draft
        assertEquals(Either.Left(ChordFailure.InvalidInput), host.execute(ChordCommand.SetCapo(13)))
        assertEquals(draft, host.current.value.draft)
        assertEquals(Either.Left(ChordFailure.RecordMissing), host.execute(ChordCommand.LoadRecord("missing")))
        assertEquals(Either.Left(ChordFailure.CandidateMissing), host.execute(ChordCommand.CopyRepresentative))
    }

    @Test fun staleLookupCannotPublishAfterContextOrQueryChanges() = runTest {
        val fixture = ChordFixture()
        val dispatcher = StandardTestDispatcher(testScheduler)
        val oldSearch = CompletableDeferred<Unit>()
        val storage = fixture.storage()
        val host = AndroidChordsHost(storage, dispatcher, dispatcher, search = { query ->
            if (query.context.capo == 0 && query.identity.root == PitchClass.C) oldSearch.await()
            ChordTheory.representatives(query)
        })
        val first = launch { succeed(host, ChordCommand.Search(ChordIdentity(PitchClass.C, ChordQuality.Major))) }
        runCurrent()
        assertTrue(host.current.value.lookup is ChordLookup.Searching)
        succeed(host, ChordCommand.SetCapo(2))
        succeed(host, ChordCommand.Search(ChordIdentity(PitchClass.D, ChordQuality.Major)))
        oldSearch.complete(Unit)
        first.join()
        val lookup = host.current.value.lookup as ChordLookup.Ready
        assertEquals(2, lookup.query.context.capo)
        assertEquals(PitchClass.D, lookup.query.identity.root)
        assertFalse(lookup.shapes.isEmpty())
        succeed(host, ChordCommand.CopyRepresentative)
        assertEquals(lookup.query.context, host.current.value.draft.context)
        assertEquals(lookup.query.identity, host.current.value.draft.selected)
        assertEquals(null, host.current.value.draft.targetId)
    }

    @Test fun canceledCommitStillPublishesTheAcknowledgedSave() = runTest {
        val fixture = ChordFixture()
        val dispatcher = StandardTestDispatcher(testScheduler)
        val host = fixture.host(dispatcher)
        succeed(host, ChordCommand.SetStop(0, StringStop.Open))
        succeed(host, ChordCommand.SetName("Cancellation"))
        val job = launch { host.execute(ChordCommand.SaveDraft).fold({ error("Unexpected $it") }, {}) }
        fixture.preferences.onCommit = { job.cancel() }
        job.join()
        assertTrue(job.isCancelled)
        assertEquals(host.current.value.records, fixture.host(dispatcher).current.value.records)
        assertEquals(1, host.current.value.records.size)
        assertEquals(DraftPersistence.Synced, host.current.value.persistence)
    }

    @Test fun canceledSearchLeavesAnActionableIdleState() = runTest {
        val fixture = ChordFixture()
        val dispatcher = StandardTestDispatcher(testScheduler)
        val host = AndroidChordsHost(fixture.storage(), dispatcher, dispatcher, search = { CompletableDeferred<Unit>().await(); emptyList() })
        val job = launch { host.execute(ChordCommand.Search(ChordIdentity(PitchClass.C, ChordQuality.Major))).fold({}, {}) }
        runCurrent()
        job.cancelAndJoin()
        assertEquals(ChordLookup.Idle, host.current.value.lookup)
    }

    @Test fun failedFirstCommitRestoresAbsentStorageAndCanceledFailureKeepsTheOriginal() = runTest {
        val fixture = ChordFixture()
        val dispatcher = StandardTestDispatcher(testScheduler)
        val host = fixture.host(dispatcher)
        fixture.preferences.failNextCommit = true
        assertEquals(Either.Left(ChordFailure.WriteFailed), host.execute(ChordCommand.SetName("Visible")))
        assertEquals(null, fixture.preferences.source)
        assertEquals("Visible", host.current.value.draft.name)
        succeed(host, ChordCommand.SetStop(0, StringStop.Open))
        succeed(host, ChordCommand.SaveDraft)
        val original = host.current.value.records.single()
        succeed(host, ChordCommand.SetCapo(2))
        val accepted = fixture.preferences.source
        fixture.preferences.failNextCommit = true
        val job = launch { host.execute(ChordCommand.SaveDraft).fold({}, { error("Commit should fail") }) }
        fixture.preferences.onCommit = { job.cancel() }
        job.join()
        assertTrue(job.isCancelled)
        assertEquals(listOf(original), host.current.value.records)
        assertEquals(accepted, fixture.preferences.source)
        assertEquals(listOf(original), fixture.host(dispatcher).current.value.records)
    }

    @Test fun noResultIsDistinctAndAnUnrecognizedShapeRemainsSaveable() = runTest {
        val fixture = ChordFixture()
        val dispatcher = StandardTestDispatcher(testScheduler)
        val host = fixture.host(dispatcher)
        succeed(host, ChordCommand.SetTuning(GuitarTuning(List(6) { GuitarPitch(PitchClass.C, 2) })))
        succeed(host, ChordCommand.Search(ChordIdentity(PitchClass.C, ChordQuality.Seventh)))
        assertTrue((host.current.value.lookup as ChordLookup.Ready).shapes.isEmpty())
        assertEquals(Either.Left(ChordFailure.CandidateMissing), host.execute(ChordCommand.CopyRepresentative))
        succeed(host, ChordCommand.SetTuning(GuitarTuning(List(6) { GuitarPitch(if (it % 2 == 0) PitchClass.C else PitchClass.Cs, 2) })))
        for (index in 0..5) succeed(host, ChordCommand.SetStop(index, StringStop.Open))
        assertEquals(com.pekochan069.guitarlearner.domain.ChordAnalysis.Unrecognized, host.current.value.analysis)
        succeed(host, ChordCommand.SetName("My cluster"))
        succeed(host, ChordCommand.SaveDraft)
        assertEquals("My cluster", host.current.value.records.single().content.name)
        assertEquals(null, fixture.host(dispatcher).current.value.draft.selected)
    }

    private suspend fun succeed(host: AndroidChordsHost, command: ChordCommand) {
        assertEquals(Either.Right(Unit), host.execute(command))
    }
}

private class ChordFixture(source: String? = null) {
    val preferences = ChordPreferences(source)
    private val documents = mutableMapOf<String, ChordDocument>()
    private var serial = 0
    private var identity = 0
    fun storage(): ChordStorage = ChordStorage(preferences.value,
        decode = { documents[it] ?: throw IllegalArgumentException("Invalid test document") },
        encode = { value -> "document-${++serial}".also { documents[it] = value } },
    )
    fun host(dispatcher: kotlinx.coroutines.CoroutineDispatcher): AndroidChordsHost =
        AndroidChordsHost(storage(), dispatcher, dispatcher, nextId = { "record-${++identity}" })
}

private class ChordPreferences(var source: String?) {
    var failNextCommit = false
    var commits = 0
    var onCommit: (() -> Unit)? = null
    val value: SharedPreferences = Proxy.newProxyInstance(SharedPreferences::class.java.classLoader,
        arrayOf(SharedPreferences::class.java)) { _, method, arguments ->
        when (method.name) {
            "getString" -> source ?: arguments?.get(1)
            "edit" -> editor()
            else -> error("Unexpected preferences call ${method.name}")
        }
    } as SharedPreferences

    private fun editor(): SharedPreferences.Editor {
        var pending = source
        return Proxy.newProxyInstance(SharedPreferences.Editor::class.java.classLoader,
            arrayOf(SharedPreferences.Editor::class.java)) { proxy, method, arguments ->
            when (method.name) {
                "putString" -> { pending = arguments?.get(1) as String?; proxy }
                "remove" -> { pending = null; proxy }
                "commit" -> {
                    source = pending
                    commits++
                    onCommit?.invoke()
                    val success = !failNextCommit
                    failNextCommit = false
                    success
                }
                else -> error("Unexpected editor call ${method.name}")
            }
        } as SharedPreferences.Editor
    }
}
