package com.pekochan069.guitarlearner.adapters

import android.app.Application
import android.content.SharedPreferences
import arrow.core.Either
import arrow.core.right
import com.pekochan069.guitarlearner.domain.*
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
import kotlin.random.Random

enum class TrainingVisibility { Foreground, ConfigurationContinuation, Background, Locked }

class AndroidTrainingHost internal constructor(
    private val ownedScope: CoroutineScope,
    private val metronome: Metronome,
    private val storage: TrainingSettingsStore,
    private val outputFactory: TrainingToneOutputFactory,
    private val choose: (Int) -> Int,
    private val main: CoroutineDispatcher = Dispatchers.Main.immediate,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) : Training, AutoCloseable {
    private val initial = storage.read()
    private val snapshot = MutableStateFlow(TrainingSnapshot(settings = initial.getOrNull() ?: TrainingSettings(),
        storage = initial.fold({ TrainingStorageStatus.Failed(it) }, { TrainingStorageStatus.Ready })))
    override val current: StateFlow<TrainingSnapshot> = snapshot.asStateFlow()
    private var nextSessionId = 0L
    @Volatile private var generation = 0L
    @Volatile private var eligible = false
    @Volatile private var closed = false
    private var prepared = false
    private var preparation: Job? = null
    private var output: TrainingToneOutput? = null
    private val metronomeObserver = ownedScope.launch(main, start = CoroutineStart.UNDISPATCHED) {
        metronome.current.collect {
            if (prepared && (metronome.current.value.playback is PlaybackState.Preparing ||
                metronome.current.value.playback is PlaybackState.Playing)) {
                stopAudio().fold({}, {})
            }
        }
    }

    override fun submit(request: TrainingRequest) {
        if (closed) return
        when (request) {
            is TrainingRequest.SetSettings -> saveSettings(request.settings)
            TrainingRequest.RetrySettings -> retrySettings()
            TrainingRequest.Start -> start()
            is TrainingRequest.Answer -> {
                val session = active() ?: return
                snapshot.value = snapshot.value.copy(stage = TrainingStage.Active(session.answer(request.key, request.answer)))
            }
            is TrainingRequest.Next -> next(request.key)
            is TrainingRequest.Replay -> replay(request.key, request.sound)
            TrainingRequest.Exit -> {
                stopAudio().fold({ snapshot.value = snapshot.value.copy(notice = it) }, {})
                snapshot.value = snapshot.value.copy(stage = TrainingStage.Setup)
            }
        }
    }

    private fun active(): TrainingSession? = (snapshot.value.stage as? TrainingStage.Active)?.session

    private fun start() {
        if (active() != null || !eligible || snapshot.value.storage is TrainingStorageStatus.Saving) return
        stopAudio().fold(
            { snapshot.value = snapshot.value.copy(notice = it) },
            { TrainingSession.start(++nextSessionId, snapshot.value.settings, choose).fold(
                { snapshot.value = snapshot.value.copy(notice = it) },
                { session ->
                    snapshot.value = snapshot.value.copy(stage = TrainingStage.Active(session), notice = null)
                    if (session.settings.representation == TrainingRepresentation.Listening) replay(session.key, TrainingSound.Question)
                },
            ) },
        )
    }

    private fun next(key: TrainingQuestionKey) {
        val session = active() ?: return
        if (session.key != key || session.response == null) return
        stopAudio().fold({}, {
            val next = session.next(key)
            snapshot.value = snapshot.value.copy(stage = next, notice = null)
            if (next is TrainingStage.Active && next.session.settings.representation == TrainingRepresentation.Listening) {
                replay(next.session.key, TrainingSound.Question)
            }
        })
    }

    private fun replay(key: TrainingQuestionKey, sound: TrainingSound) {
        val session = active() ?: return
        if (session.key != key || !eligible || session.settings.representation != TrainingRepresentation.Listening ||
            sound == TrainingSound.Comparison && session.settings.subject != TrainingSubject.Note) return
        stopAudio().fold({}, {
            val token = generation
            snapshot.value = snapshot.value.copy(audio = TrainingAudioStatus.Preparing(sound))
            preparation = ownedScope.launch(main) {
                metronome.execute(MetronomeCommand.Stop).fold(
                    { if (accepts(token, key)) audioFailed(sound, TrainingFailure.MetronomeStopFailed(it)) },
                    {
                        if (!accepts(token, key)) return@fold
                        prepared = true
                        val permitted = { accepts(token, key) && metronome.current.value.playback !is PlaybackState.Preparing &&
                            metronome.current.value.playback !is PlaybackState.Playing }
                        if (!permitted()) { audioFailed(sound, TrainingFailure.OutputInterrupted); return@fold }
                        val requestedOutput = outputFactory.create(permitted) { failure -> ownedScope.launch(main) {
                            if (accepts(token, key)) {
                                stopAudio().fold({ audioFailed(sound, it) }, { audioFailed(sound, failure) })
                            }
                        } }
                        output = requestedOutput
                        val pitches = if (sound == TrainingSound.Comparison) listOf(60) else session.question.positions.map { it.midi }
                        requestedOutput.play(TrainingTone(pitches, session.settings.intervalPresentation)) {
                            withContext(main) {
                                if (accepts(token, key)) snapshot.value = snapshot.value.copy(audio = TrainingAudioStatus.Playing(sound))
                            }
                        }.fold(
                            { if (accepts(token, key)) audioFailed(sound, it) },
                            { if (accepts(token, key)) {
                                output = null
                                prepared = false
                                snapshot.value = snapshot.value.copy(audio = TrainingAudioStatus.Idle)
                            } },
                        )
                    },
                )
            }
        })
    }

    private fun accepts(token: Long, key: TrainingQuestionKey): Boolean = !closed && eligible && generation == token &&
        ownedScope.isActive && active()?.key == key

    private fun audioFailed(sound: TrainingSound, failure: TrainingFailure) {
        snapshot.value = snapshot.value.copy(audio = TrainingAudioStatus.Failed(sound, failure))
    }

    private fun stopAudio(): Either<TrainingFailure, Unit> {
        ++generation
        prepared = false
        preparation?.cancel()
        preparation = null
        val sound = when (val status = snapshot.value.audio) {
            is TrainingAudioStatus.Preparing -> status.sound
            is TrainingAudioStatus.Playing -> status.sound
            is TrainingAudioStatus.Failed -> status.sound
            TrainingAudioStatus.Idle -> TrainingSound.Question
        }
        val result = output?.stop() ?: Unit.right()
        result.fold({ audioFailed(sound, it) }, {
            output = null
            snapshot.value = snapshot.value.copy(audio = TrainingAudioStatus.Idle)
        })
        return result
    }

    private fun saveSettings(settings: TrainingSettings) {
        if (snapshot.value.storage is TrainingStorageStatus.Saving || snapshot.value.storage is TrainingStorageStatus.Failed &&
            (snapshot.value.storage as TrainingStorageStatus.Failed).failure == TrainingFailure.SettingsReadFailed) return
        snapshot.value = snapshot.value.copy(storage = TrainingStorageStatus.Saving(settings), notice = null)
        ownedScope.launch(main) {
            withContext(NonCancellable) {
                withContext(io) { storage.write(settings) }.fold(
                    { if (!closed) snapshot.value = snapshot.value.copy(storage = TrainingStorageStatus.Failed(it, settings)) },
                    { if (!closed) snapshot.value = snapshot.value.copy(settings = settings, storage = TrainingStorageStatus.Ready) },
                )
            }
        }
    }

    private fun retrySettings() {
        val status = snapshot.value.storage as? TrainingStorageStatus.Failed ?: return
        val requested = status.requested
        if (requested != null && status.failure == TrainingFailure.SettingsWriteFailed) { saveSettings(requested); return }
        snapshot.value = snapshot.value.copy(storage = TrainingStorageStatus.Saving(snapshot.value.settings))
        ownedScope.launch(main) {
            withContext(io) { storage.read() }.fold(
                { if (!closed) snapshot.value = snapshot.value.copy(storage = TrainingStorageStatus.Failed(it)) },
                { if (!closed) snapshot.value = snapshot.value.copy(settings = it, storage = TrainingStorageStatus.Ready) },
            )
        }
    }

    fun visibilityChanged(visibility: TrainingVisibility) {
        when (visibility) {
            TrainingVisibility.ConfigurationContinuation -> Unit
            TrainingVisibility.Foreground -> if (!closed) eligible = true
            TrainingVisibility.Background, TrainingVisibility.Locked -> {
                eligible = false
                stopAudio().fold({}, {})
            }
        }
    }

    override fun close() {
        closed = true
        eligible = false
        stopAudio().fold({}, {})
        metronomeObserver.cancel()
    }

    class Factory(private val application: Application, private val preferences: SharedPreferences, private val metronome: Metronome) {
        fun create(scope: CoroutineScope): AndroidTrainingHost = AndroidTrainingHost(scope, metronome, TrainingStorage(preferences),
            TrainingToneOutputFactory { permitted, interrupted -> AndroidTrainingToneOutput(application, permitted, interrupted) },
            { bound -> Random.nextInt(bound) })
    }
}
