package com.pekochan069.guitarlearner.adapters

import android.app.Activity
import android.app.Application
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState as NativePlaybackState
import android.os.Build
import android.os.PowerManager
import androidx.core.content.ContextCompat
import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.pekochan069.guitarlearner.domain.*
import java.util.Collections
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class AndroidProgressionsHost(
    private val application: Application,
    private val serviceClass: Class<out Service>,
    private val activityClass: Class<out Activity>,
    preferences: SharedPreferences,
    private val io: CoroutineDispatcher = Dispatchers.IO,
    private val main: CoroutineDispatcher = Dispatchers.Main.immediate,
    private val outputFactory: ProgressionOutputFactory = ProgressionOutputFactory(::ProgressionAudio),
    private val startGate: Mutex = Mutex(),
    private val beforeStart: suspend () -> Unit = {},
) : Progressions {
    private val storage = ProgressionStorage(preferences)
    private val initial = storage.initial.getOrNull() ?: ProgressionDocument()
    private val snapshot = MutableStateFlow(ProgressionWorkspace(draft = initial.draft,
        records = Collections.unmodifiableList(initial.records.toList()), readFailure = storage.initial.fold({ it }, { null })))
    override val current: StateFlow<ProgressionWorkspace> = snapshot.asStateFlow()
    private val mutations = Mutex()
    private var nextRunId = 0L
    private var requestedRunId: Long? = null
    private var requestedStart = 0
    @Volatile private var run: PlaybackRun? = null
    fun currentAudioDiagnostics(): MetronomeAudioDiagnostics? = run?.audio?.diagnostics

    override suspend fun execute(command: ProgressionCommand): Either<ProgressionFailure, Unit> = when (command) {
        is ProgressionCommand.Play -> withContext(main) { requestStart(command.startIndex) }
        ProgressionCommand.Stop -> withContext(main) { stop(StopReason.User); Unit.right() }
        ProgressionCommand.Pause -> withContext(main) { pause(); Unit.right() }
        ProgressionCommand.Resume -> withContext(main) { resume(); Unit.right() }
        is ProgressionCommand.Select -> withContext(main) {
            if (command.index !in snapshot.value.draft.content.steps.indices) ProgressionFailure.InvalidInput.left()
            else { snapshot.value = snapshot.value.copy(selectedIndex = command.index); Unit.right() }
        }
        else -> {
            if (command.stopsPlayback()) withContext(main) { stop(StopReason.User) }
            val caller = currentCoroutineContext()
            val result = mutations.withLock {
                caller.ensureActive()
                withContext(NonCancellable) { withContext(main) { change(command) } }
            }
            caller.ensureActive()
            result
        }
    }
    private suspend fun change(command: ProgressionCommand): Either<ProgressionFailure, Unit> {
        val state = snapshot.value
        val draft = state.draft
        val content = draft.content
        val steps = content.steps.toMutableList()
        var selected = state.selectedIndex
        val next = when (command) {
            is ProgressionCommand.Insert -> {
                val at = (selected + 1).coerceIn(0, steps.size)
                steps.add(at, command.step); selected = at
                draft.copy(content = content.withEditedSteps(steps))
            }
            is ProgressionCommand.ReplaceChord -> {
                val old = steps.getOrNull(command.index) as? ProgressionStep.Chord ?: return failure(ProgressionFailure.InvalidInput)
                if (!command.shape.hasSound) return failure(ProgressionFailure.EmptyShape)
                if (command.name.length > 80) return failure(ProgressionFailure.InvalidName)
                steps[command.index] = old.copy(name = command.name, shape = command.shape)
                draft.copy(content = content.withEditedSteps(steps))
            }
            is ProgressionCommand.SetDuration -> {
                val old = steps.getOrNull(command.index) ?: return failure(ProgressionFailure.InvalidInput)
                steps[command.index] = when (old) {
                    is ProgressionStep.Chord -> old.copy(duration = command.duration)
                    is ProgressionStep.Rest -> old.copy(duration = command.duration)
                }
                draft.copy(content = content.copy(steps = steps))
            }
            is ProgressionCommand.SetTie -> {
                val old = steps.getOrNull(command.index) as? ProgressionStep.Chord ?: return failure(ProgressionFailure.InvalidTie)
                if (command.enabled && !content.canTie(command.index)) return failure(ProgressionFailure.InvalidTie)
                steps[command.index] = old.copy(tieToNext = command.enabled)
                draft.copy(content = content.copy(steps = steps))
            }
            is ProgressionCommand.Move -> {
                if (command.index !in steps.indices || command.destination !in steps.indices) return failure(ProgressionFailure.InvalidInput)
                steps.add(command.destination, steps.removeAt(command.index))
                selected = when {
                    selected == command.index -> command.destination
                    command.index < selected && selected <= command.destination -> selected - 1
                    command.destination <= selected && selected < command.index -> selected + 1
                    else -> selected
                }
                draft.copy(content = content.withEditedSteps(steps))
            }
            is ProgressionCommand.Remove -> {
                if (command.index !in steps.indices) return failure(ProgressionFailure.InvalidInput)
                steps.removeAt(command.index)
                selected = if (selected > command.index) selected - 1 else selected.coerceAtMost(steps.lastIndex)
                draft.copy(content = content.withEditedSteps(steps))
            }
            is ProgressionCommand.SetContext -> draft.copy(content = content.copy(context = command.context))
            is ProgressionCommand.SetSignature -> {
                if (command.numerator !in 1..16) return failure(ProgressionFailure.InvalidInput)
                draft.copy(content = content.copy(timing = MetronomeConfig(content.timing.bpm, command.denominator,
                    List(command.numerator) { if (it == 0) BeatAccent.Accent else BeatAccent.Normal })))
            }
            is ProgressionCommand.SetTempo -> {
                if (command.bpm !in 40..240) return failure(ProgressionFailure.InvalidInput)
                run?.audio?.setTempo(command.bpm)
                draft.copy(content = content.copy(timing = content.timing.copy(bpm = command.bpm)))
            }
            is ProgressionCommand.SetMetronome -> {
                run?.audio?.setMetronome(command.enabled)
                draft.copy(content = content.copy(metronomeEnabled = command.enabled))
            }
            is ProgressionCommand.SetLoop -> draft.copy(content = content.copy(loop = command.enabled))
            is ProgressionCommand.SetName -> {
                if (command.name.length > 80) return failure(ProgressionFailure.InvalidName)
                draft.copy(name = command.name)
            }
            ProgressionCommand.NewDraft -> { selected = -1; ProgressionDraft(content = ProgressionContent(context = content.context)) }
            is ProgressionCommand.Load -> {
                if (state.readFailure != null) return failure(ProgressionFailure.ReadFailed)
                val record = state.records.firstOrNull { it.id == command.id } ?: return failure(ProgressionFailure.RecordMissing)
                selected = 0; ProgressionDraft(record.name, record.content, record.id)
            }
            ProgressionCommand.Save -> {
                if (state.readFailure != null) return failure(ProgressionFailure.ReadFailed)
                val name = draft.name.trim()
                if (name.length !in 1..80) return failure(ProgressionFailure.InvalidName)
                if (steps.isEmpty()) return failure(ProgressionFailure.EmptyProgression)
                if (draft.targetId != null && state.records.none { it.id == draft.targetId }) return failure(ProgressionFailure.RecordMissing)
                val id = draft.targetId ?: java.util.UUID.randomUUID().toString()
                return persist(ProgressionDocument(draft.copy(name = name, targetId = id),
                    state.records.filterNot { it.id == id } + SavedProgression(id, name, content)), true)
            }
            is ProgressionCommand.Delete -> {
                if (state.readFailure != null) return failure(ProgressionFailure.ReadFailed)
                if (state.records.none { it.id == command.id }) return failure(ProgressionFailure.RecordMissing)
                return persist(ProgressionDocument(draft.copy(targetId = draft.targetId?.takeUnless { it == command.id }),
                    state.records.filterNot { it.id == command.id }), true)
            }
            ProgressionCommand.RetryDraftWrite -> return persist(ProgressionDocument(draft, state.records), false)
            ProgressionCommand.RetryStorageRead -> return retryRead()
            else -> error("Transport commands do not edit content")
        }
        snapshot.value = state.copy(draft = next, selectedIndex = selected, persistence = DraftPersistence.Unsynced, actionFailure = null)
        return persist(ProgressionDocument(next, state.records), false)
    }
    private suspend fun persist(document: ProgressionDocument, records: Boolean): Either<ProgressionFailure, Unit> =
        withContext(io) { storage.save(document) }.fold({ failure(it) }, {
            snapshot.value = snapshot.value.copy(draft = document.draft,
                records = if (records) Collections.unmodifiableList(document.records.toList()) else snapshot.value.records,
                persistence = DraftPersistence.Synced, actionFailure = null)
            Unit.right()
        })
    private suspend fun retryRead(): Either<ProgressionFailure, Unit> = withContext(io) { storage.read() }.fold({ failure(it) }, { restored ->
        val previous = snapshot.value
        val draft = if (previous.persistence == DraftPersistence.Unsynced) previous.draft.copy(
            targetId = previous.draft.targetId?.takeIf { id -> restored.records.any { it.id == id } }) else restored.draft
        snapshot.value = previous.copy(draft = draft, records = Collections.unmodifiableList(restored.records.toList()),
            selectedIndex = previous.selectedIndex.coerceAtMost(draft.content.steps.lastIndex), readFailure = null, actionFailure = null,
            persistence = if (draft == restored.draft) DraftPersistence.Synced else DraftPersistence.Unsynced)
        Unit.right()
    })
    private fun failure(value: ProgressionFailure): Either<ProgressionFailure, Unit> {
        snapshot.value = snapshot.value.copy(actionFailure = value)
        return value.left()
    }
    private fun ProgressionCommand.stopsPlayback(): Boolean = when (this) {
        is ProgressionCommand.Insert, is ProgressionCommand.ReplaceChord, is ProgressionCommand.SetDuration,
        is ProgressionCommand.Move, is ProgressionCommand.Remove, is ProgressionCommand.SetTie,
        is ProgressionCommand.SetContext, is ProgressionCommand.SetSignature, is ProgressionCommand.SetLoop,
        ProgressionCommand.NewDraft, is ProgressionCommand.Load -> true
        else -> false
    }
    private suspend fun requestStart(startIndex: Int): Either<ProgressionFailure, Unit> {
        if (requestedRunId != null) return Unit.right()
        val content = snapshot.value.draft.content
        if (content.steps.isEmpty()) return failure(ProgressionFailure.EmptyProgression)
        if (startIndex !in content.steps.indices) return failure(ProgressionFailure.InvalidInput)
        val id = ++nextRunId
        requestedRunId = id; requestedStart = startIndex
        snapshot.value = snapshot.value.copy(playback = ProgressionPlayback.Preparing, actionFailure = null)
        return startGate.withLock {
            if (requestedRunId != id) return@withLock Unit.right()
            beforeStart()
            if (requestedRunId != id) return@withLock Unit.right()
            try {
                application.startForegroundService(serviceIntent(ACTION_START, id)); Unit.right()
            } catch (_: IllegalStateException) { fail(id, ProgressionFailure.ServiceUnavailable); ProgressionFailure.ServiceUnavailable.left() }
              catch (_: SecurityException) { fail(id, ProgressionFailure.ServiceUnavailable); ProgressionFailure.ServiceUnavailable.left() }
        }
    }
    private suspend fun pause() {
        val session = run ?: return
        if (snapshot.value.playback !is ProgressionPlayback.Playing) return
        val position = session.audio?.pause() ?: return
        if (requestedRunId != session.id) return
        snapshot.value = snapshot.value.copy(playback = ProgressionPlayback.Paused(position))
        session.pauseForeground()
    }
    private suspend fun resume() {
        val session = run ?: return
        val paused = snapshot.value.playback as? ProgressionPlayback.Paused ?: return
        startGate.withLock {
            if (requestedRunId != session.id) return@withLock
            beforeStart()
            if (requestedRunId != session.id) return@withLock
            try {
                if (!session.ensureFocus()) return@withLock
                session.audio?.resume()
                if (requestedRunId != session.id) return@withLock
                snapshot.value = snapshot.value.copy(playback = ProgressionPlayback.Playing(paused.position))
                session.resumeForeground()
            } catch (failure: IllegalStateException) {
                if (failure is kotlinx.coroutines.CancellationException) throw failure
                fail(session.id, ProgressionFailure.AudioUnavailable)
            } catch (_: SecurityException) { fail(session.id, ProgressionFailure.ServiceUnavailable) }
        }
    }

    fun onServiceCommand(service: Service, scope: CoroutineScope, intent: Intent?, startId: Int) {
        run?.takeIf { it.service === service }?.startId = startId
        val id = intent?.getLongExtra(EXTRA_RUN_ID, -1) ?: -1
        when (intent?.action) {
            ACTION_START -> if (run == null) begin(service, scope, startId, id)
            ACTION_STOP -> if (id == requestedRunId) stop(StopReason.User)
            ACTION_PAUSE -> if (id == requestedRunId) scope.launch(main) { pause() }
            ACTION_RESUME -> if (id == requestedRunId) scope.launch(main) { resume() }
        }
        if (run == null) service.stopSelfResult(startId)
    }

    fun onServiceDestroyed(service: Service) {
        if (run?.service === service) stop(StopReason.ServiceEnded)
    }

    private fun begin(service: Service, scope: CoroutineScope, startId: Int, id: Long) {
        try {
            val session = PlaybackRun(service, scope, startId, id)
            if (id != requestedRunId) {
                try {
                    session.prepareForeground()
                } finally {
                    session.close()
                }
                return
            }
            run = session
            session.prepare()
            if (requestedRunId == id) session.startAudio(snapshot.value.draft.content)
        } catch (_: IllegalStateException) {
            fail(id, ProgressionFailure.AudioUnavailable)
        } catch (_: IllegalArgumentException) {
            fail(id, ProgressionFailure.AudioUnavailable)
        } catch (_: SecurityException) {
            fail(id, ProgressionFailure.ServiceUnavailable)
        }
    }

    private fun stop(reason: StopReason) {
        requestedRunId = null
        val previous = run
        run = null
        snapshot.value = snapshot.value.copy(playback = ProgressionPlayback.Stopped(reason))
        previous?.close()
    }

    private fun fail(id: Long, failure: ProgressionFailure) {
        if (requestedRunId != id) return
        requestedRunId = null
        val previous = run
        run = null
        snapshot.value = snapshot.value.copy(playback = ProgressionPlayback.Failed(failure))
        previous?.close()
    }

    private fun serviceIntent(action: String, id: Long): Intent = Intent(application, serviceClass)
        .setAction(action).putExtra(EXTRA_RUN_ID, id)

    private inner class PlaybackRun(
        val service: Service,
        private val scope: CoroutineScope,
        var startId: Int,
        val id: Long,
    ) {
        private val manager = application.getSystemService(AudioManager::class.java)
        private val notifications = application.getSystemService(NotificationManager::class.java)
        private val media = MediaSession(application, "GuitarLearnerProgression")
        private val focus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(mediaAttributes()).setWillPauseWhenDucked(true)
            .setOnAudioFocusChangeListener { change ->
                if (change != AudioManager.AUDIOFOCUS_GAIN && requestedRunId == id) stop(StopReason.FocusLoss)
            }.build()
        private var focused = false
        private var registered = false
        private var foreground = false
        private var closed = false
        private var lastConfig: Int? = null
        private var audioEpoch = 0L
        private var lastActiveOutputId: Int? = null
        private val wakeLock = application.getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "GuitarLearner:Progression")
            .apply { setReferenceCounted(false) }
        private var wakeJob: Job? = null
        @Volatile var audio: ProgressionOutput? = null
            private set
        private val noisy = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY && requestedRunId == id) {
                    stop(StopReason.OutputDisconnected)
                }
            }
        }
        private val devices = object : AudioDeviceCallback() {
            override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) {
                if (requestedRunId == id && removedDevices.any { it.id == audio?.routedDeviceId || it.id == lastActiveOutputId }) {
                    stop(StopReason.OutputDisconnected)
                }
            }
        }
        private val modes = if (Build.VERSION.SDK_INT >= 31) AudioManager.OnModeChangedListener { mode ->
            if (mode != AudioManager.MODE_NORMAL && requestedRunId == id) stop(StopReason.FocusLoss)
        } else null

        fun prepareForeground() {
            notifications.createNotificationChannel(NotificationChannel(CHANNEL, application.getString(R.string.progression_notification_channel), NotificationManager.IMPORTANCE_LOW))
            media.setCallback(object : MediaSession.Callback() {
                override fun onStop() { if (requestedRunId == id) stop(StopReason.User) }
                override fun onPause() { if (requestedRunId == id) scope.launch(main) { pause() } }
                override fun onPlay() { if (requestedRunId == id) scope.launch(main) { resume() } }
            })
            media.isActive = true
            media.setPlaybackState(NativePlaybackState.Builder().setActions(NativePlaybackState.ACTION_STOP)
                .setState(NativePlaybackState.STATE_CONNECTING, NativePlaybackState.PLAYBACK_POSITION_UNKNOWN, 0f).build())
            service.startForeground(NOTIFICATION, notification(snapshot.value.draft.content, preparing = true))
            foreground = true
        }

        fun prepare() {
            prepareForeground()
            if (manager.mode != AudioManager.MODE_NORMAL || manager.requestAudioFocus(focus) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                fail(id, ProgressionFailure.FocusDenied)
                return
            }
            focused = true
            ContextCompat.registerReceiver(application, noisy, IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY), ContextCompat.RECEIVER_NOT_EXPORTED)
            registered = true
            manager.registerAudioDeviceCallback(devices, null)
            if (Build.VERSION.SDK_INT >= 31) modes?.let { manager.addOnModeChangedListener(application.mainExecutor, it) }
            wakeLock.acquire(60_000)
            wakeJob = scope.launch {
                while (true) {
                    delay(30_000)
                    wakeLock.acquire(60_000)
                }
            }
        }

        fun startAudio(config: ProgressionContent) {
            val previous = audio
            lastActiveOutputId = previous?.routedDeviceId ?: lastActiveOutputId
            val epoch = ++audioEpoch
            audio = null
            previous?.stop()
            lastConfig = null
            snapshot.value = snapshot.value.copy(playback = ProgressionPlayback.Preparing)
            media.setPlaybackState(NativePlaybackState.Builder().setActions(NativePlaybackState.ACTION_STOP)
                .setState(NativePlaybackState.STATE_CONNECTING, NativePlaybackState.PLAYBACK_POSITION_UNKNOWN, 0f).build())
            service.startForeground(NOTIFICATION, notification(config, preparing = true))
            val output = outputFactory.create(manager, config, requestedStart,
                onPosition = { position -> scope.launch(main) {
                    if (!acceptsAudio(epoch)) return@launch
                    try {
                        present(position)
                    } catch (_: SecurityException) {
                        fail(id, ProgressionFailure.ServiceUnavailable)
                    } catch (_: IllegalStateException) {
                        fail(id, ProgressionFailure.AudioUnavailable)
                    }
                } },
                onComplete = { scope.launch(main) { if (acceptsAudio(epoch)) stop(StopReason.User) } },
                onOutputDisconnected = { scope.launch(main) {
                    if (acceptsAudio(epoch)) stop(StopReason.OutputDisconnected)
                } },
                onFailure = { scope.launch(main) {
                    if (acceptsAudio(epoch)) fail(id, ProgressionFailure.AudioUnavailable)
                } },
            )
            if (!acceptsAudio(epoch)) {
                output.stop()
                return
            }
            audio = output
            output.start(scope)
        }

        private fun acceptsAudio(epoch: Long): Boolean = requestedRunId == id && !closed && audioEpoch == epoch

        private fun present(position: ProgressionPosition) {
            if (snapshot.value.playback is ProgressionPlayback.Paused) return
            lastActiveOutputId = audio?.routedDeviceId ?: lastActiveOutputId
            snapshot.value = snapshot.value.copy(playback = ProgressionPlayback.Playing(position))
            if (lastConfig != position.bpm) {
                lastConfig = position.bpm
                resumeForeground()
            }
        }
        fun ensureFocus(): Boolean {
            if (manager.mode != AudioManager.MODE_NORMAL || manager.requestAudioFocus(focus) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                fail(id, ProgressionFailure.FocusDenied); return false
            }
            focused = true; return true
        }
        fun pauseForeground() {
            wakeJob?.cancel(); wakeJob = null
            if (wakeLock.isHeld) wakeLock.release()
            media.setPlaybackState(NativePlaybackState.Builder().setActions(NativePlaybackState.ACTION_PLAY or NativePlaybackState.ACTION_STOP)
                .setState(NativePlaybackState.STATE_PAUSED, NativePlaybackState.PLAYBACK_POSITION_UNKNOWN, 0f).build())
            service.startForeground(NOTIFICATION, notification(snapshot.value.draft.content, false, paused = true))
        }
        fun resumeForeground() {
            if (wakeJob == null) {
                wakeLock.acquire(60_000)
                wakeJob = scope.launch { while (true) { delay(30_000); wakeLock.acquire(60_000) } }
            }
            media.setPlaybackState(NativePlaybackState.Builder().setActions(NativePlaybackState.ACTION_PAUSE or NativePlaybackState.ACTION_STOP)
                .setState(NativePlaybackState.STATE_PLAYING, NativePlaybackState.PLAYBACK_POSITION_UNKNOWN, 1f).build())
            media.setMetadata(MediaMetadata.Builder().putString(MediaMetadata.METADATA_KEY_TITLE, application.getString(R.string.progression_notification_title)).build())
            service.startForeground(NOTIFICATION, notification(snapshot.value.draft.content, false))
        }
        private fun description(config: ProgressionContent): String = application.getString(
            R.string.metronome_notification_configuration, config.timing.bpm, config.timing.numerator, config.timing.denominator.denominator)

        private fun notification(config: ProgressionContent, preparing: Boolean, paused: Boolean = false): Notification {
            val open = PendingIntent.getActivity(application, 0,
                Intent(application, activityClass).setAction(ACTION_OPEN_PROGRESSION)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            val stop = PendingIntent.getService(application, 1, serviceIntent(ACTION_STOP, id), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            val toggle = PendingIntent.getService(application, 3, serviceIntent(if (paused) ACTION_RESUME else ACTION_PAUSE, id), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            return Notification.Builder(application, CHANNEL)
                .setSmallIcon(R.drawable.ic_metronome_notification)
                .setContentTitle(application.getString(R.string.progression_notification_title))
                .setContentText(if (preparing) application.getString(R.string.metronome_notification_preparing) else if (paused) application.getString(R.string.progression_notification_paused) else description(config))
                .setContentIntent(open).setOngoing(true).setOnlyAlertOnce(true)
                .addAction(Notification.Action.Builder(null, application.getString(if (paused) R.string.progression_notification_resume else R.string.progression_notification_pause), toggle).build())
                .addAction(Notification.Action.Builder(null, application.getString(R.string.metronome_notification_stop), stop).build())
                .setStyle(Notification.MediaStyle().setMediaSession(media.sessionToken).setShowActionsInCompactView(0))
                .build()
        }

        fun close() {
            if (closed) return
            closed = true
            ++audioEpoch
            audio?.stop()
            audio = null
            wakeJob?.cancel()
            if (wakeLock.isHeld) wakeLock.release()
            if (focused) manager.abandonAudioFocusRequest(focus)
            if (registered) {
                application.unregisterReceiver(noisy)
                manager.unregisterAudioDeviceCallback(devices)
                if (Build.VERSION.SDK_INT >= 31) modes?.let(manager::removeOnModeChangedListener)
            }
            media.isActive = false
            media.release()
            if (foreground) service.stopForeground(Service.STOP_FOREGROUND_REMOVE)
            service.stopSelfResult(startId)
        }
    }

    companion object {
        const val ACTION_OPEN_PROGRESSION = "com.pekochan069.guitarlearner.progression.OPEN"
        private const val ACTION_START = "com.pekochan069.guitarlearner.progression.START"
        private const val ACTION_PAUSE = "com.pekochan069.guitarlearner.progression.PAUSE"
        private const val ACTION_RESUME = "com.pekochan069.guitarlearner.progression.RESUME"
        private const val ACTION_STOP = "com.pekochan069.guitarlearner.progression.STOP"
        private const val EXTRA_RUN_ID = "run_id"
        private const val CHANNEL = "progression_playback"
        private const val NOTIFICATION = 2
    }
}
