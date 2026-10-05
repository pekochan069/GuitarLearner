package com.pekochan069.guitarlearner.adapters

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioDeviceInfo
import android.media.AudioRouting
import android.media.AudioTimestamp
import android.media.AudioTrack
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.Process
import android.util.Log
import java.util.concurrent.ConcurrentLinkedQueue
import com.pekochan069.guitarlearner.domain.ProgressionContent
import com.pekochan069.guitarlearner.domain.ProgressionPosition
import com.pekochan069.guitarlearner.domain.ProgressionSequencer
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.android.asCoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlin.coroutines.coroutineContext
import kotlin.math.max
import kotlin.math.min

internal class ProgressionAudio(
    private val manager: AudioManager,
    private val content: ProgressionContent,
    private val startIndex: Int,
    private val onPosition: (ProgressionPosition) -> Unit,
    private val onComplete: () -> Unit,
    private val onOutputDisconnected: () -> Unit,
    private val onFailure: () -> Unit,
) : ProgressionOutput {
    private val edits = ConcurrentLinkedQueue<(ProgressionSequencer) -> Unit>()
    private val trackGate = Any()
    @Volatile private var track: AudioTrack? = null
    @Volatile override var routedDeviceId: Int? = null
        private set
    @Volatile override var diagnostics: MetronomeAudioDiagnostics = MetronomeAudioDiagnostics()
        private set
    private var stopped = false
    private var routing: AudioRouting.OnRoutingChangedListener? = null
    private var job: Job? = null

    private data class Control(val paused: Boolean, val done: CompletableDeferred<Unit>)
    private val controls = ConcurrentLinkedQueue<Control>()
    @Volatile private var position = ProgressionPosition(bpm = content.timing.bpm, metronomeEnabled = content.metronomeEnabled)
    override fun setTempo(bpm: Int) { edits.add { it.setTempo(bpm) } }
    override fun setMetronome(enabled: Boolean) { edits.add { it.setMetronome(enabled) } }
    override suspend fun pause(): ProgressionPosition { control(true); return position }
    override suspend fun resume() { control(false) }
    private suspend fun control(paused: Boolean) {
        val done = CompletableDeferred<Unit>()
        synchronized(trackGate) {
            if (stopped) throw CancellationException("Output stopped")
            controls.add(Control(paused, done))
        }
        val completion = job?.invokeOnCompletion { done.cancel() }
        try { done.await() } finally { completion?.dispose() }
    }

    override fun start(scope: CoroutineScope) {
        val thread = HandlerThread("ProgressionAudio", Process.THREAD_PRIORITY_AUDIO).apply { start() }
        val dispatcher = Handler(thread.looper).asCoroutineDispatcher("ProgressionAudio")
        val worker = scope.launch(dispatcher) {
            try {
                play()
            } catch (failure: IllegalArgumentException) {
                Log.e(TAG, "Invalid audio configuration", failure)
                onFailure()
            } catch (failure: IllegalStateException) {
                if (failure is CancellationException) throw failure
                Log.e(TAG, "Audio state failed", failure)
                onFailure()
            } catch (failure: UnsupportedOperationException) {
                Log.e(TAG, "Audio operation unsupported", failure)
                onFailure()
            } catch (failure: SecurityException) {
                Log.e(TAG, "Audio access denied", failure)
                onFailure()
            } finally {
                synchronized(trackGate) {
                    routing?.let { track?.removeOnRoutingChangedListener(it) }
                    track?.release()
                    track = null
                }
            }
        }
        worker.invokeOnCompletion {
            while (true) { (controls.poll() ?: break).done.cancel() }
            thread.quitSafely()
        }
        job = worker
    }

    override fun stop() {
        synchronized(trackGate) {
            stopped = true
            job?.cancel()
            try {
                track?.pause()
                track?.flush()
            } catch (_: IllegalStateException) {
            }
        }
    }

    private suspend fun play() {
        val sampleRate = manager.getProperty(AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE)
            ?.toIntOrNull()?.takeIf { it > 0 } ?: 48_000
        val chunkFrames = max(1, sampleRate / 200)
        val minimumBytes = AudioTrack.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
        if (minimumBytes <= 0) {
            Log.e(TAG, "Invalid minimum audio buffer result=$minimumBytes sampleRate=$sampleRate")
            onFailure()
            return
        }
        val audio = AudioTrack.Builder()
            .setAudioAttributes(mediaAttributes())
            .setAudioFormat(AudioFormat.Builder().setSampleRate(sampleRate)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO).setEncoding(AudioFormat.ENCODING_PCM_16BIT).build())
            .setTransferMode(AudioTrack.MODE_STREAM)
            .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
            .setBufferSizeInBytes(max(minimumBytes, chunkFrames * 4))
            .build()
        synchronized(trackGate) { track = audio }
        coroutineContext.ensureActive()
        if (audio.state != AudioTrack.STATE_INITIALIZED) {
            Log.e(TAG, "AudioTrack not initialized state=${audio.state}")
            onFailure()
            return
        }
        val capacity = audio.bufferSizeInFrames
        val startThreshold = if (Build.VERSION.SDK_INT >= 31) audio.startThresholdInFrames else capacity
        val sequencer = ProgressionSequencer(content, startIndex, sampleRate)
        val renderer = ProgressionPcm(sequencer, sampleRate)
        val buffer = ShortArray(chunkFrames)
        val markers = ArrayDeque<ProgressionMarker>()
        var writtenFrames = 0L
        var pendingOffset = 0
        var pendingSize = 0
        var started = false
        var paused = false
        var pauseAck: CompletableDeferred<Unit>? = null
        var resumeAck: CompletableDeferred<Unit>? = null
        var rawHead = 0L
        var headEpoch = 0L
        var lastProgressNs = System.nanoTime()
        var lastLoopNs = lastProgressNs
        var maxLoopGapNs = 0L
        var maxHeadDelta = 0L
        var lastWriteResult = 0
        var nextDiagnosticsNs = 0L
        val clock = PresentedFrameClock(sampleRate)
        val timestamp = AudioTimestamp()
        var nextTimestampNs = 0L
        var lastRoute: AudioDeviceInfo? = null
        val underruns = audio.underrunCount
        val writeHorizon = max(chunkFrames * 2, sampleRate / 25)
        Log.d(TAG, "Preparing sampleRate=$sampleRate chunk=$chunkFrames buffer=$capacity horizon=$writeHorizon start=$startThreshold")

        fun refreshRoute() = synchronized(trackGate) {
            if (stopped || track !== audio) return@synchronized
            val route = audio.routedDevice
            if (lastRoute?.id != route?.id) {
                clock.invalidate()
                nextTimestampNs = 0
                if (activeRouteRemoved(lastRoute?.id, route?.id, manager.getDevices(AudioManager.GET_DEVICES_OUTPUTS).map { it.id })) {
                    onOutputDisconnected()
                }
                if (route != null) lastRoute = route
            }
            routedDeviceId = lastRoute?.id
            diagnostics = MetronomeAudioDiagnostics(route?.type, route?.id, route?.productName?.toString(),
                sampleRate, audio.underrunCount, clock.anchored)
        }
        val routeListener = AudioRouting.OnRoutingChangedListener { refreshRoute() }
        routing = routeListener
        audio.addOnRoutingChangedListener(routeListener, Handler(requireNotNull(Looper.myLooper())))

        while (true) {
            coroutineContext.ensureActive()
            val now = System.nanoTime()
            while (true) {
                val control = controls.poll() ?: break
                synchronized(trackGate) {
                    if (stopped) { control.done.cancel(); return }
                    clock.invalidate()
                    nextTimestampNs = 0
                    paused = control.paused
                    if (paused) { audio.pause(); pauseAck = control.done }
                    else { started = false; resumeAck = control.done; lastProgressNs = now; lastLoopNs = now }
                }
            }
            if (paused && pauseAck == null) { delay(10); continue }
            val loopGapNs = now - lastLoopNs
            lastLoopNs = now
            maxLoopGapNs = max(maxLoopGapNs, loopGapNs)
            val previousHead = headEpoch + rawHead
            val previousQueuedFrames = writtenFrames - previousHead
            val currentRaw = audio.playbackHeadPosition.toLong() and 0xffff_ffffL
            if (currentRaw < rawHead && rawHead - currentRaw > 0x8000_0000L) headEpoch += 0x1_0000_0000L
            if (currentRaw != rawHead) lastProgressNs = now
            rawHead = currentRaw
            val head = headEpoch + currentRaw
            val headDelta = head - previousHead
            maxHeadDelta = max(maxHeadDelta, headDelta)
            if (started && !paused && now - lastProgressNs > 3_000_000_000L) {
                Log.e(TAG, "Playback stalled head=$head written=$writtenFrames queued=${writtenFrames - head} " +
                    "loopGapNs=$loopGapNs maxLoopGapNs=$maxLoopGapNs lastWrite=$lastWriteResult")
                onFailure()
                return
            }
            if (started && !paused && audio.underrunCount > underruns) {
                Log.w(TAG, "Playback underrun count=${audio.underrunCount} " +
                    "loopGapNs=$loopGapNs maxLoopGapNs=$maxLoopGapNs " +
                    "previousHead=$previousHead head=$head headDelta=$headDelta maxHeadDelta=$maxHeadDelta " +
                    "written=$writtenFrames previousQueued=$previousQueuedFrames queued=${writtenFrames - head} " +
                    "pending=$pendingSize lastWrite=$lastWriteResult buffer=$capacity horizon=$writeHorizon start=$startThreshold")
                onFailure()
                return
            }
            if (started && !paused && now >= nextTimestampNs) {
                val anchored = audio.getTimestamp(timestamp) && clock.anchor(timestamp.framePosition, timestamp.nanoTime, head, writtenFrames, now)
                nextTimestampNs = now + if (anchored) 10_000_000_000L else 200_000_000L
            }
            val presented = if (paused) head else clock.frame(now, head, writtenFrames)
            if (started && head > 0) {
                while (markers.isNotEmpty() && markers.first().frame <= presented) {
                    val marker = markers.removeFirst()
                    position = marker.position
                    onPosition(marker.position)
                    if (marker.complete) onComplete()
                }
            }
            pauseAck?.let { it.complete(Unit); pauseAck = null }
            if (paused) { delay(10); continue }

            val queuedFrames = writtenFrames - head
            val queueLimit = if (started) min(audio.bufferSizeInFrames, writeHorizon) else startThreshold
            if (pendingSize == 0 && queuedFrames < queueLimit) {
                while (true) { (edits.poll() ?: break).invoke(sequencer) }
                pendingSize = min(chunkFrames.toLong(), queueLimit - queuedFrames).toInt()
                pendingOffset = 0
                renderer.render(buffer, pendingSize, markers::addLast)
            }
            if (pendingSize > 0) {
                val written = audio.write(buffer, pendingOffset, pendingSize, AudioTrack.WRITE_NON_BLOCKING)
                lastWriteResult = written
                if (written < 0) {
                    Log.e(TAG, "AudioTrack write failed result=$written offset=$pendingOffset size=$pendingSize " +
                        "head=$head written=$writtenFrames queued=${writtenFrames - head}")
                    onFailure()
                    return
                }
                writtenFrames += written
                pendingOffset += written
                pendingSize -= written
            }
            if (!started && writtenFrames - head >= startThreshold) {
                coroutineContext.ensureActive()
                synchronized(trackGate) {
                    if (stopped) return
                    coroutineContext.ensureActive()
                    audio.play()
                }
                started = true
                lastProgressNs = now
                resumeAck?.complete(Unit)
                resumeAck = null
            }
            if (started && now >= nextDiagnosticsNs) {
                nextDiagnosticsNs = now + 100_000_000L
                refreshRoute()
            }
            if (pendingSize != 0 || writtenFrames - head >= queueLimit) delay(2)
        }
    }

    companion object { const val TAG: String = "ProgressionAudio" }
}
