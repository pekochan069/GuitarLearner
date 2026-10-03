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
import com.pekochan069.guitarlearner.domain.BeatAccent
import com.pekochan069.guitarlearner.domain.BeatUnit
import com.pekochan069.guitarlearner.domain.MetronomeConfig
import com.pekochan069.guitarlearner.domain.MetronomeSequencer
import com.pekochan069.guitarlearner.domain.ScheduledBeat
import java.util.concurrent.ConcurrentLinkedQueue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.android.asCoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlin.coroutines.coroutineContext
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

data class MetronomeAudioDiagnostics(
    val outputType: Int? = null,
    val outputDeviceId: Int? = null,
    val outputName: String? = null,
    val sampleRate: Int? = null,
    val underruns: Int = 0,
    val timestampUsed: Boolean = false,
)

internal class MetronomeAudio(
    private val manager: AudioManager,
    private val config: MetronomeConfig,
    private val onBeat: (ScheduledBeat) -> Unit,
    private val onOutputDisconnected: () -> Unit,
    private val onFailure: () -> Unit,
) {
    private val edits = ConcurrentLinkedQueue<(MetronomeSequencer) -> Unit>()
    private val trackGate = Any()
    @Volatile private var track: AudioTrack? = null
    @Volatile var routedDeviceId: Int? = null
        private set
    @Volatile var diagnostics: MetronomeAudioDiagnostics = MetronomeAudioDiagnostics()
        private set
    private var stopped = false
    private var routing: AudioRouting.OnRoutingChangedListener? = null
    private var job: Job? = null

    fun setTempo(bpm: Int) { edits.add { it.setTempo(bpm) } }
    fun setPattern(denominator: BeatUnit, beats: List<BeatAccent>) {
        edits.add { it.setPattern(denominator, beats) }
    }
    fun load(config: MetronomeConfig) { edits.add { it.setConfig(config) } }

    fun start(scope: CoroutineScope) {
        val thread = HandlerThread("MetronomeAudio", Process.THREAD_PRIORITY_AUDIO).apply { start() }
        val dispatcher = Handler(thread.looper).asCoroutineDispatcher("MetronomeAudio")
        val worker = scope.launch(dispatcher) {
            try {
                play()
            } catch (failure: CancellationException) {
                throw failure
            } catch (failure: IllegalArgumentException) {
                Log.e(TAG, "Invalid audio configuration", failure)
                onFailure()
            } catch (failure: IllegalStateException) {
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
        worker.invokeOnCompletion { thread.quitSafely() }
        job = worker
    }

    fun stop() {
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
        if (Build.VERSION.SDK_INT >= 31) audio.setStartThresholdInFrames(min(capacity, chunkFrames * 2))
        val startThreshold = if (Build.VERSION.SDK_INT >= 31) audio.startThresholdInFrames else capacity
        val sequencer = MetronomeSequencer(config, sampleRate)
        val renderer = MetronomePcm(sequencer, sampleRate)
        val buffer = ShortArray(chunkFrames)
        val markers = ArrayDeque<ScheduledBeat>()
        var writtenFrames = 0L
        var pendingOffset = 0
        var pendingSize = 0
        var started = false
        var reportedBeat: ScheduledBeat? = null
        var rawHead = 0L
        var headEpoch = 0L
        var lastProgressNs = System.nanoTime()
        var lastLoopNs = lastProgressNs
        var maxLoopGapNs = 0L
        var maxHeadDelta = 0L
        var lastWriteResult = 0
        var lastLogNs = 0L
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
            if (started && now - lastProgressNs > 3_000_000_000L) {
                Log.e(TAG, "Playback stalled head=$head written=$writtenFrames queued=${writtenFrames - head} " +
                    "loopGapNs=$loopGapNs maxLoopGapNs=$maxLoopGapNs lastWrite=$lastWriteResult")
                onFailure()
                return
            }
            if (started && audio.underrunCount > underruns) {
                Log.w(TAG, "Playback underrun count=${audio.underrunCount} " +
                    "loopGapNs=$loopGapNs maxLoopGapNs=$maxLoopGapNs " +
                    "previousHead=$previousHead head=$head headDelta=$headDelta maxHeadDelta=$maxHeadDelta " +
                    "written=$writtenFrames previousQueued=$previousQueuedFrames queued=${writtenFrames - head} " +
                    "pending=$pendingSize lastWrite=$lastWriteResult buffer=$capacity horizon=$writeHorizon start=$startThreshold")
                onFailure()
                return
            }
            if (started && now >= nextTimestampNs) {
                val anchored = audio.getTimestamp(timestamp) && clock.anchor(timestamp.framePosition, timestamp.nanoTime, head, writtenFrames, now)
                nextTimestampNs = now + if (anchored) 10_000_000_000L else 200_000_000L
            }
            val presented = clock.frame(now, head, writtenFrames)
            if (started && head > 0) {
                while (markers.isNotEmpty() && markers.first().frame <= presented) reportedBeat = markers.removeFirst()
                reportedBeat?.let { beat ->
                    onBeat(beat)
                    reportedBeat = null
                    if (now - lastLogNs >= 10_000_000_000L) {
                        lastLogNs = now
                        Log.d(TAG, "beat=${beat.beatIndex} scheduled=${beat.frame} presented=$presented timestamp=${clock.anchored} route=${audio.routedDevice?.type}")
                    }
                }
            }

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
            if (!started && writtenFrames >= startThreshold) {
                coroutineContext.ensureActive()
                synchronized(trackGate) {
                    if (stopped) return
                    coroutineContext.ensureActive()
                    audio.play()
                }
                started = true
                lastProgressNs = now
            }
            if (started && now >= nextDiagnosticsNs) {
                nextDiagnosticsNs = now + 100_000_000L
                refreshRoute()
            }
            if (pendingSize != 0 || writtenFrames - head >= queueLimit) delay(2)
        }
    }

    companion object { const val TAG: String = "MetronomeAudio" }
}

internal fun mediaAttributes(): AudioAttributes = AudioAttributes.Builder()
    .setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build()

internal fun activeRouteRemoved(previousId: Int?, currentId: Int?, availableIds: List<Int>): Boolean =
    previousId != null && previousId != currentId && previousId !in availableIds

internal fun clickSamples(sampleRate: Int, accented: Boolean): ShortArray {
    val count = max(1, sampleRate / 50)
    val frequency = if (accented) 1_760.0 else 880.0
    val amplitude = if (accented) 0.8 else 0.45
    return ShortArray(count) { index ->
        (Short.MAX_VALUE * amplitude * sin(2 * PI * frequency * index / sampleRate) * exp(-7.0 * index / count)).toInt().toShort()
    }
}

internal class MetronomePcm(private val sequencer: MetronomeSequencer, sampleRate: Int) {
    private val prerollFrames = sampleRate / 20L
    private val normal = clickSamples(sampleRate, false)
    private val accent = clickSamples(sampleRate, true)
    private var sounding: ShortArray? = null
    private var soundOffset = 0
    private var frame = -prerollFrames

    fun render(buffer: ShortArray, size: Int, onBeat: (ScheduledBeat) -> Unit) {
        for (index in 0 until size) {
            if (frame == sequencer.nextFrame) {
                val beat = sequencer.nextBeat()
                onBeat(beat.copy(frame = beat.frame + prerollFrames))
                sounding = when (beat.config.beats[beat.beatIndex]) {
                    BeatAccent.Accent -> accent
                    BeatAccent.Normal -> normal
                    BeatAccent.Mute -> null
                }
                soundOffset = 0
            }
            buffer[index] = sounding?.getOrNull(soundOffset) ?: 0
            soundOffset++
            frame++
        }
    }
}

internal class PresentedFrameClock(private val sampleRate: Int) {
    private var anchorFrame = 0L
    private var anchorNs = 0L
    private var lastPresented = -1L
    var anchored: Boolean = false
        private set

    fun invalidate() { anchored = false }

    fun anchor(rawFrame: Long, nanos: Long, head: Long, written: Long, now: Long): Boolean {
        val unsigned = rawFrame and 0xffff_ffffL
        var frame = (head and -0x1_0000_0000L) + unsigned
        if (frame - head > 0x8000_0000L) frame -= 0x1_0000_0000L
        if (head - frame > 0x8000_0000L) frame += 0x1_0000_0000L
        if (nanos <= 0 || nanos < now - 2_000_000_000L || nanos > now + 2_000_000_000L ||
            frame !in 0..written || anchored && (frame < anchorFrame || nanos <= anchorNs)) {
            anchored = false
            return false
        }
        anchorFrame = frame
        anchorNs = nanos
        anchored = true
        return true
    }

    fun frame(now: Long, head: Long, written: Long): Long {
        val estimated = if (anchored) anchorFrame + (now - anchorNs) * sampleRate / 1_000_000_000L else head
        lastPresented = max(lastPresented, estimated.coerceIn(-1, written))
        return lastPresented
    }
}
