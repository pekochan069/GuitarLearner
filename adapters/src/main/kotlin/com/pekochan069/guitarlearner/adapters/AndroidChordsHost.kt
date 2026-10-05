package com.pekochan069.guitarlearner.adapters

import android.content.SharedPreferences
import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.pekochan069.guitarlearner.domain.ChordAnalysis
import com.pekochan069.guitarlearner.domain.ChordCommand
import com.pekochan069.guitarlearner.domain.ChordDraft
import com.pekochan069.guitarlearner.domain.ChordFailure
import com.pekochan069.guitarlearner.domain.ChordIdentity
import com.pekochan069.guitarlearner.domain.ChordLookup
import com.pekochan069.guitarlearner.domain.ChordQuery
import com.pekochan069.guitarlearner.domain.ChordShape
import com.pekochan069.guitarlearner.domain.ChordTheory
import com.pekochan069.guitarlearner.domain.ChordWorkspace
import com.pekochan069.guitarlearner.domain.Chords
import com.pekochan069.guitarlearner.domain.DraftPersistence
import com.pekochan069.guitarlearner.domain.SavedChord
import java.util.Collections
import java.util.UUID
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class AndroidChordsHost internal constructor(
    private val storage: ChordStorage,
    private val io: CoroutineDispatcher,
    private val compute: CoroutineDispatcher,
    private val nextId: () -> String = { UUID.randomUUID().toString() },
    private val search: suspend (ChordQuery) -> List<ChordShape> = { ChordTheory.representatives(it) },
) : Chords {
    constructor(preferences: SharedPreferences, io: CoroutineDispatcher = Dispatchers.IO, compute: CoroutineDispatcher = Dispatchers.Default) :
        this(ChordStorage(preferences), io, compute)

    private val initialDocument = storage.initial.getOrNull() ?: ChordDocument()
    private val state = MutableStateFlow(ChordWorkspace(
        draft = initialDocument.draft,
        records = immutable(initialDocument.records),
        analysis = ChordTheory.analyze(initialDocument.draft.context, initialDocument.draft.shape),
        readFailure = storage.initial.fold({ it }, { null }),
    ))
    override val current: StateFlow<ChordWorkspace> = state.asStateFlow()
    private val mutations = Mutex()
    private var lookupGeneration = 0L

    override suspend fun execute(command: ChordCommand): Either<ChordFailure, Unit> {
        val caller = currentCoroutineContext()
        val (result, task) = mutations.withLock {
            caller.ensureActive()
            val previousGeneration = lookupGeneration
            val result = change(command)
            val lookup = state.value.lookup as? ChordLookup.Searching
            result to if (lookupGeneration != previousGeneration && lookup != null) LookupTask(lookupGeneration, lookup.query) else null
        }
        if (task != null) {
            try {
                runSearch(task)
            } finally {
                withContext(NonCancellable) {
                    mutations.withLock {
                        if (lookupGeneration == task.generation && state.value.lookup is ChordLookup.Searching) {
                            state.value = state.value.copy(lookup = ChordLookup.Idle)
                        }
                    }
                }
            }
        }
        caller.ensureActive()
        return result
    }

    private suspend fun change(command: ChordCommand): Either<ChordFailure, Unit> {
        val workspace = state.value
        val draft = workspace.draft
        return when (command) {
            is ChordCommand.SetName -> edit(draft.copy(name = command.name))
            is ChordCommand.SetTuning -> edit(draft.copy(context = draft.context.copy(tuning = command.tuning)))
            is ChordCommand.SetStringPitch -> if (command.index in 0..5) {
                edit(draft.copy(context = draft.context.copy(tuning = draft.context.tuning.withString(command.index, command.pitch))))
            } else failure(ChordFailure.InvalidInput)
            is ChordCommand.SetCapo -> if (command.capo in 0..12) {
                val selected = draft.selected?.let { it.copy(root = it.root.transpose(command.capo - draft.context.capo)) }
                edit(draft.copy(context = draft.context.copy(capo = command.capo), selected = selected))
            } else failure(ChordFailure.InvalidInput)
            is ChordCommand.SetStop -> if (command.index in 0..5) {
                edit(draft.copy(shape = draft.shape.withString(command.index, command.stop)))
            } else failure(ChordFailure.InvalidInput)
            is ChordCommand.SelectCandidate -> if ((workspace.analysis as? ChordAnalysis.Recognized)?.candidates
                    ?.any { it.identity == command.identity } == true) {
                edit(draft.copy(selected = command.identity))
            } else failure(ChordFailure.CandidateMissing)
            is ChordCommand.Search -> {
                beginSearch(command.identity)
                Unit.right()
            }
            is ChordCommand.SelectRepresentative -> {
                val lookup = workspace.lookup as? ChordLookup.Ready
                if (lookup != null && command.index in lookup.shapes.indices) {
                    state.value = workspace.copy(lookup = lookup.copy(selectedIndex = command.index), actionFailure = null)
                    Unit.right()
                } else failure(ChordFailure.CandidateMissing)
            }
            ChordCommand.CopyRepresentative -> {
                val lookup = workspace.lookup as? ChordLookup.Ready
                val shape = lookup?.shapes?.getOrNull(lookup.selectedIndex)
                if (lookup != null && shape != null) edit(ChordDraft(context = lookup.query.context, shape = shape, selected = lookup.query.identity))
                else failure(ChordFailure.CandidateMissing)
            }
            ChordCommand.NewDraft -> edit(ChordDraft(context = draft.context))
            ChordCommand.SaveDraft -> saveDraft()
            is ChordCommand.LoadRecord -> if (workspace.readFailure != null) failure(ChordFailure.ReadFailed) else {
                workspace.records.firstOrNull { it.id == command.id }?.let { edit(it.content.copy(targetId = it.id)) }
                    ?: failure(ChordFailure.RecordMissing)
            }
            is ChordCommand.DeleteRecord -> deleteRecord(command.id)
            ChordCommand.RetryDraftWrite -> persist(ChordDocument(draft, workspace.records), publishRecords = false)
            ChordCommand.RetryStorageRead -> retryRead()
        }
    }

    private suspend fun edit(value: ChordDraft): Either<ChordFailure, Unit> {
        val draft = withContext(compute) { ChordTheory.normalize(value) }
        val previous = state.value
        state.value = previous.copy(draft = draft, analysis = withContext(compute) { ChordTheory.analyze(draft.context, draft.shape) },
            persistence = DraftPersistence.Unsynced, actionFailure = null)
        if (previous.draft.context != draft.context) lookupIdentity(previous.lookup)?.let(::beginSearch)
        return persist(ChordDocument(draft, previous.records), publishRecords = false)
    }

    private suspend fun saveDraft(): Either<ChordFailure, Unit> {
        val draft = state.value.draft
        if (state.value.readFailure != null) return failure(ChordFailure.ReadFailed)
        if (!draft.shape.hasSound) return failure(ChordFailure.EmptyShape)
        val name = draft.name.trim()
        if (name.length !in 1..80) return failure(ChordFailure.InvalidName)
        if (draft.targetId != null && state.value.records.none { it.id == draft.targetId }) return failure(ChordFailure.RecordMissing)
        val id = draft.targetId ?: nextId()
        val record = SavedChord(id, draft.copy(name = name, targetId = null))
        val records = state.value.records.filterNot { it.id == id } + record
        return persist(ChordDocument(draft.copy(name = name, targetId = id), records), publishRecords = true)
    }

    private suspend fun deleteRecord(id: String): Either<ChordFailure, Unit> {
        val workspace = state.value
        if (workspace.readFailure != null) return failure(ChordFailure.ReadFailed)
        if (workspace.records.none { it.id == id }) return failure(ChordFailure.RecordMissing)
        val draft = workspace.draft.let { if (it.targetId == id) it.copy(targetId = null) else it }
        return persist(ChordDocument(draft, workspace.records.filterNot { it.id == id }), publishRecords = true)
    }

    private suspend fun persist(next: ChordDocument, publishRecords: Boolean): Either<ChordFailure, Unit> =
        withContext(NonCancellable) {
            withContext(io) { storage.save(next) }.fold(
                ifLeft = { failure(it) },
                ifRight = {
                    state.value = state.value.copy(draft = next.draft,
                        records = if (publishRecords) immutable(next.records) else state.value.records,
                        persistence = DraftPersistence.Synced, actionFailure = null)
                    Unit.right()
                },
            )
        }

    private suspend fun retryRead(): Either<ChordFailure, Unit> = withContext(NonCancellable) {
        withContext(io) { storage.read() }.fold(
            ifLeft = {
                state.value = state.value.copy(readFailure = it, actionFailure = it)
                it.left()
            },
            ifRight = { restored ->
                val previous = state.value
                val draft = if (previous.persistence == DraftPersistence.Unsynced) {
                    previous.draft.let { value -> value.copy(targetId = value.targetId?.takeIf { id -> restored.records.any { it.id == id } }) }
                } else restored.draft
                state.value = previous.copy(draft = draft, records = immutable(restored.records),
                    analysis = ChordTheory.analyze(draft.context, draft.shape), readFailure = null, actionFailure = null,
                    persistence = if (draft == restored.draft) DraftPersistence.Synced else DraftPersistence.Unsynced)
                if (previous.draft.context != draft.context) lookupIdentity(previous.lookup)?.let(::beginSearch)
                Unit.right()
            },
        )
    }

    private fun beginSearch(identity: ChordIdentity) {
        lookupGeneration++
        state.value = state.value.copy(lookup = ChordLookup.Searching(ChordQuery(state.value.draft.context, identity)), actionFailure = null)
    }

    private suspend fun runSearch(task: LookupTask) {
        val shapes = withContext(compute) {
            currentCoroutineContext().ensureActive()
            search(task.query).also { currentCoroutineContext().ensureActive() }
        }
        mutations.withLock {
            if (lookupGeneration == task.generation && (state.value.lookup as? ChordLookup.Searching)?.query == task.query) {
                state.value = state.value.copy(lookup = ChordLookup.Ready(task.query, immutable(shapes)))
            }
        }
    }

    private fun failure(value: ChordFailure): Either<ChordFailure, Unit> {
        state.value = state.value.copy(actionFailure = value)
        return value.left()
    }

    private fun lookupIdentity(lookup: ChordLookup): ChordIdentity? = when (lookup) {
        ChordLookup.Idle -> null
        is ChordLookup.Searching -> lookup.query.identity
        is ChordLookup.Ready -> lookup.query.identity
    }

    private data class LookupTask(val generation: Long, val query: ChordQuery)
}

private fun <T> immutable(values: List<T>): List<T> = Collections.unmodifiableList(values.toList())
