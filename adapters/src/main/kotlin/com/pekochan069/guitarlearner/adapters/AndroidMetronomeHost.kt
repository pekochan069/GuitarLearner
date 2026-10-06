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
import com.pekochan069.guitarlearner.domain.Metronome
import com.pekochan069.guitarlearner.domain.MetronomeCommand
import com.pekochan069.guitarlearner.domain.MetronomeConfig
import com.pekochan069.guitarlearner.domain.MetronomeFailure
import com.pekochan069.guitarlearner.domain.MetronomePreset
import com.pekochan069.guitarlearner.domain.MetronomeSnapshot
import com.pekochan069.guitarlearner.domain.PlaybackState
import com.pekochan069.guitarlearner.domain.ScheduledBeat
import com.pekochan069.guitarlearner.domain.StopReason
import java.util.Collections
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CancellationException
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

class AndroidMetronomeHost(
    private val application: Application,
    private val serviceClass: Class<out Service>,
    private val activityClass: Class<out Activity>,
    preferences: SharedPreferences,
    private val io: CoroutineDispatcher = Dispatchers.IO,
    private val main: CoroutineDispatcher = Dispatchers.Main.immediate,
    private val outputFactory: MetronomeOutputFactory = MetronomeOutputFactory(::MetronomeAudio),
    private val startGate: Mutex = Mutex(),
    private val beforeStart: suspend () -> Unit = {},
) : Metronome {
    private val storage = MetronomeStorage(preferences)
    private var document = storage.initial.getOrNull() ?: MetronomeDocument()
    private val snapshot = MutableStateFlow(MetronomeSnapshot(
        selected = document.selected,
        presets = Collections.unmodifiableList(document.presets.toList()),
        readFailure = storage.initial.fold({ it }, { null }),
    ))
    override val current: StateFlow<MetronomeSnapshot> = snapshot.asStateFlow()
    private val mutations = Mutex()
    private var nextRunId = 0L
    private var requestedRunId: Long? = null
    @Volatile private var run: PlaybackRun? = null
    private val pendingStops = mutableListOf<MetronomeOutput>()

    fun currentAudioDiagnostics(): MetronomeAudioDiagnostics? = run?.audio?.diagnostics

    override suspend fun execute(command: MetronomeCommand): Either<MetronomeFailure, Unit> = when (command) {
        MetronomeCommand.Start -> withContext(main) { requestStart() }
        MetronomeCommand.Stop -> withContext(main) {
            if (stop(StopReason.User)) Unit.right() else MetronomeFailure.AudioUnavailable.left()
        }
        else -> change(command)
    }

    private suspend fun change(command: MetronomeCommand): Either<MetronomeFailure, Unit> {
        val caller = currentCoroutineContext()
        val originRunId = withContext(main) { requestedRunId }
        val result = mutations.withLock {
            caller.ensureActive()
            val previous = document.selected
            val changed = changedDocument(command)
            changed.fold(
                ifLeft = { it.left() },
                ifRight = { next ->
                    withContext(NonCancellable) {
                        withContext(io) { storage.save(next) }.fold(
                            ifLeft = { it.left() },
                            ifRight = {
                                withContext(main) {
                                    document = next
                                    snapshot.value = snapshot.value.copy(selected = next.selected,
                                        presets = Collections.unmodifiableList(next.presets.toList()))
                                    applySavedEdit(command, previous, next.selected, originRunId)
                                }
                                Unit.right()
                            },
                        )
                    }
                },
            )
        }
        caller.ensureActive()
        return result
    }

    private fun applySavedEdit(
        command: MetronomeCommand,
        previous: MetronomeConfig,
        selected: MetronomeConfig,
        originRunId: Long?,
    ) {
        val session = run?.takeIf { it.id == requestedRunId } ?: return
        val signatureChanged = previous.numerator != selected.numerator || previous.denominator != selected.denominator
        if (signatureChanged && originRunId == requestedRunId) {
            try {
                session.startAudio(selected)
            } catch (_: IllegalStateException) {
                fail(session.id, MetronomeFailure.AudioUnavailable)
            } catch (_: IllegalArgumentException) {
                fail(session.id, MetronomeFailure.AudioUnavailable)
            } catch (_: SecurityException) {
                fail(session.id, MetronomeFailure.ServiceUnavailable)
            }
        } else {
            session.queue(command, selected)
        }
    }

    private fun changedDocument(command: MetronomeCommand): Either<MetronomeFailure, MetronomeDocument> {
        if (snapshot.value.readFailure != null) return MetronomeFailure.ReadFailed.left()
        return when (command) {
            is MetronomeCommand.SetTempo -> if (command.bpm in 40..240) {
                document.copy(selected = document.selected.copy(bpm = command.bpm)).right()
            } else MetronomeFailure.InvalidConfiguration.left()
            is MetronomeCommand.SetPattern -> if (command.beats.size in 1..16) {
                document.copy(selected = document.selected.copy(denominator = command.denominator, beats = command.beats)).right()
            } else MetronomeFailure.InvalidConfiguration.left()
            is MetronomeCommand.SavePreset -> {
                val name = command.name.trim()
                when {
                    name.isBlank() -> MetronomeFailure.InvalidName.left()
                    document.presets.any { it.name == name } && !command.overwrite -> MetronomeFailure.PresetExists.left()
                    else -> document.copy(presets = document.presets.filterNot { it.name == name } +
                        MetronomePreset(name, document.selected)).right()
                }
            }
            is MetronomeCommand.LoadPreset -> document.presets.firstOrNull { it.name == command.name }
                ?.let { document.copy(selected = it.config).right() } ?: MetronomeFailure.PresetMissing.left()
            is MetronomeCommand.DeletePreset -> if (document.presets.any { it.name == command.name }) {
                document.copy(presets = document.presets.filterNot { it.name == command.name }).right()
            } else MetronomeFailure.PresetMissing.left()
            MetronomeCommand.Start, MetronomeCommand.Stop -> error("Playback commands do not modify storage")
        }
    }

    private suspend fun requestStart(): Either<MetronomeFailure, Unit> {
        if (requestedRunId != null) return Unit.right()
        if (!retryPendingStops()) return MetronomeFailure.AudioUnavailable.left()
        val id = ++nextRunId
        requestedRunId = id
        snapshot.value = snapshot.value.copy(playback = PlaybackState.Preparing)
        return withContext(NonCancellable) { startGate.withLock {
            if (requestedRunId != id) return@withLock Unit.right()
            try {
                beforeStart()
                if (requestedRunId != id) return@withLock Unit.right()
                application.startForegroundService(serviceIntent(ACTION_START, id))
                Unit.right()
            } catch (failure: IllegalStateException) {
                if (failure is kotlinx.coroutines.CancellationException) throw failure
                fail(id, MetronomeFailure.ServiceUnavailable)
                MetronomeFailure.ServiceUnavailable.left()
            } catch (_: SecurityException) {
                fail(id, MetronomeFailure.ServiceUnavailable)
                MetronomeFailure.ServiceUnavailable.left()
            }
        } }
    }

    fun onServiceCommand(service: Service, scope: CoroutineScope, intent: Intent?, startId: Int) {
        run?.takeIf { it.service === service }?.startId = startId
        val id = intent?.getLongExtra(EXTRA_RUN_ID, -1) ?: -1
        when (intent?.action) {
            ACTION_START -> if (run == null) begin(service, scope, startId, id)
            ACTION_STOP -> if (id == requestedRunId) stop(StopReason.User)
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
            if (requestedRunId == id) session.startAudio(snapshot.value.selected)
        } catch (_: IllegalStateException) {
            fail(id, MetronomeFailure.AudioUnavailable)
        } catch (_: IllegalArgumentException) {
            fail(id, MetronomeFailure.AudioUnavailable)
        } catch (_: SecurityException) {
            fail(id, MetronomeFailure.ServiceUnavailable)
        }
    }

    private fun stop(reason: StopReason): Boolean {
        requestedRunId = null
        val previous = run
        run = null
        val pendingSilent = retryPendingStops()
        val currentSilent = previous?.close() ?: true
        val silent = pendingSilent && currentSilent
        snapshot.value = snapshot.value.copy(playback = if (silent) PlaybackState.Stopped(reason)
            else PlaybackState.Failed(MetronomeFailure.AudioUnavailable))
        return silent
    }

    private fun stopOutput(output: MetronomeOutput?): Boolean {
        if (output == null) return true
        val silent = try {
            output.stop()
        } catch (failure: IllegalStateException) {
            if (failure is CancellationException) throw failure
            false
        }
        if (!silent && output !in pendingStops) pendingStops.add(output)
        return silent
    }

    private fun retryPendingStops(): Boolean {
        pendingStops.removeAll { stopOutput(it) }
        return pendingStops.isEmpty()
    }

    private fun fail(id: Long, failure: MetronomeFailure) {
        if (requestedRunId != id) return
        requestedRunId = null
        val previous = run
        run = null
        snapshot.value = snapshot.value.copy(playback = PlaybackState.Failed(failure))
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
        private val media = MediaSession(application, "GuitarLearnerMetronome")
        private val focus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(mediaAttributes()).setWillPauseWhenDucked(true)
            .setOnAudioFocusChangeListener { change ->
                if (change != AudioManager.AUDIOFOCUS_GAIN && requestedRunId == id) stop(StopReason.FocusLoss)
            }.build()
        private var focused = false
        private var registered = false
        private var foreground = false
        private var closed = false
        private var lastConfig: MetronomeConfig? = null
        private var audioEpoch = 0L
        private var lastActiveOutputId: Int? = null
        private val wakeLock = application.getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "GuitarLearner:Metronome")
            .apply { setReferenceCounted(false) }
        private var wakeJob: Job? = null
        @Volatile var audio: MetronomeOutput? = null
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
            notifications.createNotificationChannel(NotificationChannel(CHANNEL, application.getString(R.string.metronome_notification_channel), NotificationManager.IMPORTANCE_LOW))
            media.setCallback(object : MediaSession.Callback() {
                override fun onStop() { if (requestedRunId == id) stop(StopReason.User) }
                override fun onPause() { if (requestedRunId == id) stop(StopReason.User) }
            })
            media.isActive = true
            media.setPlaybackState(NativePlaybackState.Builder().setActions(NativePlaybackState.ACTION_STOP)
                .setState(NativePlaybackState.STATE_CONNECTING, NativePlaybackState.PLAYBACK_POSITION_UNKNOWN, 0f).build())
            service.startForeground(NOTIFICATION, notification(snapshot.value.selected, preparing = true))
            foreground = true
        }

        fun prepare() {
            prepareForeground()
            if (manager.mode != AudioManager.MODE_NORMAL || manager.requestAudioFocus(focus) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                fail(id, MetronomeFailure.FocusDenied)
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

        fun startAudio(config: MetronomeConfig) {
            val previous = audio
            lastActiveOutputId = previous?.routedDeviceId ?: lastActiveOutputId
            val epoch = ++audioEpoch
            audio = null
            if (!stopOutput(previous)) { fail(id, MetronomeFailure.AudioUnavailable); return }
            lastConfig = null
            snapshot.value = snapshot.value.copy(playback = PlaybackState.Preparing)
            media.setPlaybackState(NativePlaybackState.Builder().setActions(NativePlaybackState.ACTION_STOP)
                .setState(NativePlaybackState.STATE_CONNECTING, NativePlaybackState.PLAYBACK_POSITION_UNKNOWN, 0f).build())
            service.startForeground(NOTIFICATION, notification(config, preparing = true))
            val output = outputFactory.create(manager, config,
                onBeat = { beat -> scope.launch(main) {
                    if (!acceptsAudio(epoch)) return@launch
                    try {
                        present(beat)
                    } catch (_: SecurityException) {
                        fail(id, MetronomeFailure.ServiceUnavailable)
                    } catch (_: IllegalStateException) {
                        fail(id, MetronomeFailure.AudioUnavailable)
                    }
                } },
                onOutputDisconnected = { scope.launch(main) {
                    if (acceptsAudio(epoch)) stop(StopReason.OutputDisconnected)
                } },
                onFailure = { scope.launch(main) {
                    if (acceptsAudio(epoch)) fail(id, MetronomeFailure.AudioUnavailable)
                } },
            )
            if (!acceptsAudio(epoch)) {
                stopOutput(output)
                return
            }
            audio = output
            output.start(scope)
        }

        fun queue(command: MetronomeCommand, config: MetronomeConfig) {
            audio?.let { output ->
                when (command) {
                    is MetronomeCommand.SetTempo -> output.setTempo(config.bpm)
                    is MetronomeCommand.SetPattern -> output.setPattern(config.denominator, config.beats)
                    is MetronomeCommand.LoadPreset -> output.load(config)
                    else -> Unit
                }
            }
        }

        private fun acceptsAudio(epoch: Long): Boolean = requestedRunId == id && !closed && audioEpoch == epoch

        private fun present(beat: ScheduledBeat) {
            lastActiveOutputId = audio?.routedDeviceId ?: lastActiveOutputId
            snapshot.value = snapshot.value.copy(playback = PlaybackState.Playing(beat.config, beat.beatIndex))
            if (lastConfig != beat.config) {
                lastConfig = beat.config
                media.setPlaybackState(NativePlaybackState.Builder()
                    .setActions(NativePlaybackState.ACTION_STOP or NativePlaybackState.ACTION_PAUSE)
                    .setState(NativePlaybackState.STATE_PLAYING, NativePlaybackState.PLAYBACK_POSITION_UNKNOWN, 1f).build())
                media.setMetadata(MediaMetadata.Builder().putString(MediaMetadata.METADATA_KEY_TITLE, application.getString(R.string.metronome_notification_title))
                    .putString(MediaMetadata.METADATA_KEY_ARTIST, description(beat.config)).build())
                service.startForeground(NOTIFICATION, notification(beat.config, preparing = false))
            }
        }

        private fun description(config: MetronomeConfig): String = application.getString(
            R.string.metronome_notification_configuration, config.bpm, config.numerator, config.denominator.denominator,
        )

        private fun notification(config: MetronomeConfig, preparing: Boolean): Notification {
            val open = PendingIntent.getActivity(application, 0,
                Intent(application, activityClass).setAction(ACTION_OPEN_METRONOME)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            val stop = PendingIntent.getService(application, 1, serviceIntent(ACTION_STOP, id), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            return Notification.Builder(application, CHANNEL)
                .setSmallIcon(R.drawable.ic_metronome_notification)
                .setContentTitle(application.getString(R.string.metronome_notification_title))
                .setContentText(if (preparing) application.getString(R.string.metronome_notification_preparing) else description(config))
                .setContentIntent(open).setOngoing(true).setOnlyAlertOnce(true)
                .addAction(Notification.Action.Builder(null, application.getString(R.string.metronome_notification_stop), stop).build())
                .setStyle(Notification.MediaStyle().setMediaSession(media.sessionToken).setShowActionsInCompactView(0))
                .build()
        }

        fun close(): Boolean {
            if (closed) return true
            closed = true
            ++audioEpoch
            val silent = try {
                stopOutput(audio)
            } finally {
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
            return silent
        }
    }

    companion object {
        const val ACTION_OPEN_METRONOME = "com.pekochan069.guitarlearner.metronome.OPEN"
        private const val ACTION_START = "com.pekochan069.guitarlearner.metronome.START"
        private const val ACTION_STOP = "com.pekochan069.guitarlearner.metronome.STOP"
        private const val EXTRA_RUN_ID = "run_id"
        private const val CHANNEL = "metronome_playback"
        private const val NOTIFICATION = 1
    }
}
