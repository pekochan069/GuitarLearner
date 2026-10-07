package com.pekochan069.guitarlearner.adapters

import android.app.Application
import android.content.SharedPreferences
import arrow.core.Either
import arrow.core.right
import com.pekochan069.guitarlearner.domain.*
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class LearningVisibility { Foreground, ConfigurationContinuation, Background, Locked }

class AndroidLearningHost internal constructor(
    private val ownedScope: CoroutineScope,
    private val metronome: Metronome,
    private val storage: LearningProgressStore,
    private val outputFactory: TrainingToneOutputFactory,
    private val main: CoroutineDispatcher = Dispatchers.Main.immediate,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) : Learning, AutoCloseable {
    private val snapshot = MutableStateFlow(LearningSnapshot(storage = LearningStorageState.Saving))
    override val current: StateFlow<LearningSnapshot> = snapshot.asStateFlow()
    private var readable = false
    private var reading = false
    private var writeBlocked = false
    private var revision = 0L
    private var savedRevision = 0L
    private var writer: Job? = null
    private var active: ActiveExample? = null
    private val generation = AtomicLong()
    @Volatile private var eligible = false
    @Volatile private var closed = false
    private var prepared = false
    private var preparation: Job? = null
    private var output: TrainingToneOutput? = null
    private val metronomeObserver = ownedScope.launch(main, start = CoroutineStart.UNDISPATCHED) {
        metronome.current.collect {
            if (prepared && metronomeRunning()) {
                stopAudio().fold({}, { audioFailed(LearningFailure.OutputInterrupted) })
            }
        }
    }

    init { readProgress() }

    override fun submit(request: LearningRequest) {
        if (closed) return
        if (request == LearningRequest.Deactivate) generation.incrementAndGet()
        ownedScope.launch(main) {
            if (closed) return@launch
            when (request) {
                is LearningRequest.Activate -> activate(request)
                is LearningRequest.Complete -> updateProgress(snapshot.value.progress.copy(
                    completed = snapshot.value.progress.completed + request.lesson,
                ))
                is LearningRequest.Listen -> listen(request.instrument)
                LearningRequest.RetrySave -> if (readable && writeBlocked) {
                    writeBlocked = false
                    writeProgress()
                }
                LearningRequest.RetryRead -> if (!readable) readProgress()
                LearningRequest.Deactivate -> {
                    active = null
                    stopAudio().fold({}, {})
                }
            }
        }
    }

    private fun activate(request: LearningRequest.Activate) {
        if (active?.target == request.target && active?.selection == request.selection) return
        stopAudio().fold({}, {})
        active = ActiveExample(request.target, request.selection, LearningRelations.describe(request.target, request.selection).example)
        val lesson = (request.target as? LearningTarget.Lesson)?.id ?: return
        updateProgress(snapshot.value.progress.copy(lastViewed = lesson))
    }

    private fun updateProgress(progress: LearningProgress) {
        if (progress == snapshot.value.progress) return
        ++revision
        snapshot.value = snapshot.value.copy(progress = progress,
            storage = if (readable && !writeBlocked) LearningStorageState.Saving else snapshot.value.storage)
        if (readable && !writeBlocked) writeProgress()
    }

    private fun readProgress() {
        if (reading || readable) return
        reading = true
        snapshot.value = snapshot.value.copy(storage = LearningStorageState.Saving)
        ownedScope.launch(main) {
            val result = withContext(io) { storage.read() }
            reading = false
            if (closed) return@launch
            result.fold(
                { snapshot.value = snapshot.value.copy(storage = LearningStorageState.Failed(it)) },
                { saved ->
                    readable = true
                    val local = snapshot.value.progress
                    val merged = LearningProgress(saved.completed + local.completed, local.lastViewed ?: saved.lastViewed)
                    snapshot.value = snapshot.value.copy(progress = merged,
                        storage = if (merged == saved) LearningStorageState.Saved else LearningStorageState.Saving)
                    savedRevision = if (merged == saved) revision else revision - 1
                    writeProgress()
                },
            )
        }
    }

    private fun writeProgress() {
        if (!readable || writeBlocked || savedRevision == revision || writer?.isActive == true) return
        snapshot.value = snapshot.value.copy(storage = LearningStorageState.Saving)
        writer = ownedScope.launch(main) {
            try {
                withContext(NonCancellable) {
                    while (savedRevision < revision) {
                        val requested = snapshot.value.progress
                        val requestedRevision = revision
                        withContext(io) { storage.write(requested) }.fold(
                            {
                                writeBlocked = true
                                if (!closed) snapshot.value = snapshot.value.copy(storage = LearningStorageState.Failed(it))
                            },
                            { savedRevision = requestedRevision },
                        )
                        if (writeBlocked) return@withContext
                    }
                    if (!closed) snapshot.value = snapshot.value.copy(storage = LearningStorageState.Saved)
                }
            } finally {
                writer = null
            }
        }
    }

    private fun listen(instrument: TrainingInstrument) {
        val example = active?.example ?: return
        if (!eligible) return
        stopAudio().fold({}, {
            val token = generation.get()
            snapshot.value = snapshot.value.copy(audio = LearningAudioState.Preparing(instrument))
            preparation = ownedScope.launch(main) {
                metronome.execute(MetronomeCommand.Stop).fold(
                    { if (accepts(token)) audioFailed(LearningFailure.MetronomeStopFailed(it)) },
                    {
                        if (!accepts(token)) return@fold
                        prepared = true
                        val permitted = { accepts(token) && !metronomeRunning() }
                        if (!permitted()) { audioFailed(LearningFailure.OutputInterrupted); return@fold }
                        val requestedOutput = outputFactory.create(permitted) { failure -> ownedScope.launch(main) {
                            if (accepts(token)) stopAudio().fold({}, { audioFailed(failure.toLearningFailure()) })
                        } }
                        output = requestedOutput
                        val tone = TrainingTone(example.steps.map { step -> step.notes.map { it.midi } }, instrument)
                        requestedOutput.play(tone) {
                            withContext(main) {
                                if (accepts(token)) snapshot.value = snapshot.value.copy(audio = LearningAudioState.Playing(instrument))
                            }
                        }.fold(
                            { if (accepts(token)) audioFailed(it.toLearningFailure()) },
                            { if (accepts(token)) {
                                generation.incrementAndGet()
                                output = null
                                prepared = false
                                snapshot.value = snapshot.value.copy(audio = LearningAudioState.Idle)
                            } },
                        )
                    },
                )
            }
        })
    }

    private fun metronomeRunning(): Boolean = metronome.current.value.playback is PlaybackState.Preparing ||
        metronome.current.value.playback is PlaybackState.Playing

    private fun accepts(token: Long): Boolean = !closed && eligible && generation.get() == token && active != null && ownedScope.isActive

    private fun audioFailed(failure: LearningFailure) {
        generation.incrementAndGet()
        prepared = false
        snapshot.value = snapshot.value.copy(audio = LearningAudioState.Failed(failure))
    }

    private fun stopAudio(): Either<TrainingFailure, Unit> {
        generation.incrementAndGet()
        prepared = false
        preparation?.cancel()
        preparation = null
        val result = output?.stop() ?: Unit.right()
        result.fold(
            { audioFailed(it.toLearningFailure()) },
            {
                output = null
                snapshot.value = snapshot.value.copy(audio = LearningAudioState.Idle)
            },
        )
        return result
    }

    fun visibilityChanged(visibility: LearningVisibility) {
        when (visibility) {
            LearningVisibility.ConfigurationContinuation -> Unit
            LearningVisibility.Foreground -> if (!closed) eligible = true
            LearningVisibility.Background, LearningVisibility.Locked -> {
                eligible = false
                ownedScope.launch(main) { stopAudio().fold({}, {}) }
            }
        }
    }

    override fun close() {
        closed = true
        eligible = false
        active = null
        stopAudio().fold({}, {})
        metronomeObserver.cancel()
    }

    private data class ActiveExample(val target: LearningTarget, val selection: LearningSelection, val example: LearningExample?)

    class Factory(private val application: Application, private val preferences: SharedPreferences, private val metronome: Metronome) {
        fun create(scope: CoroutineScope): AndroidLearningHost = AndroidLearningHost(scope, metronome, LearningStorage(preferences),
            TrainingToneOutputFactory { permitted, interrupted -> AndroidTrainingToneOutput(application, permitted, interrupted) })
    }
}

private fun TrainingFailure.toLearningFailure(): LearningFailure = when (this) {
    TrainingFailure.PlaybackFailed -> LearningFailure.PlaybackFailed
    TrainingFailure.ShutdownFailed -> LearningFailure.ShutdownFailed
    TrainingFailure.FocusDenied -> LearningFailure.FocusDenied
    TrainingFailure.OutputInterrupted -> LearningFailure.OutputInterrupted
    is TrainingFailure.MetronomeStopFailed -> LearningFailure.MetronomeStopFailed(cause)
    TrainingFailure.EmptyIntervalPool, TrainingFailure.SettingsReadFailed, TrainingFailure.SettingsWriteFailed -> LearningFailure.PlaybackFailed
}
