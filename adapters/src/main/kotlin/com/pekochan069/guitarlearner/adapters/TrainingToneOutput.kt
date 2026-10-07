package com.pekochan069.guitarlearner.adapters

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.pekochan069.guitarlearner.domain.TrainingFailure
import com.pekochan069.guitarlearner.domain.TrainingInstrument
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

internal data class TrainingTone(val steps: List<List<Int>>, val instrument: TrainingInstrument = TrainingInstrument.Piano) {
    init { require(steps.isNotEmpty() && steps.all { it.isNotEmpty() && it.all { midi -> midi in 40..76 } }) }
}

internal interface TrainingToneOutput {
    suspend fun play(tone: TrainingTone, onPlaying: suspend () -> Unit): Either<TrainingFailure, Unit>
    fun stop(): Either<TrainingFailure, Unit>
}

internal fun interface TrainingToneOutputFactory {
    fun create(permitted: () -> Boolean, onInterrupted: (TrainingFailure) -> Unit): TrainingToneOutput
}

internal object TrainingPcm {
    const val SAMPLE_RATE: Int = 48_000
    const val NOTE_FRAMES: Int = 31_200
    const val GAP_FRAMES: Int = 6_720

    fun render(tone: TrainingTone, samples: (TrainingInstrument, Int) -> ShortArray): ShortArray {
        val sources = tone.steps.map { pitches ->
            pitches.map { midi -> samples(tone.instrument, midi).also { require(it.size == NOTE_FRAMES) } }
        }
        val frames = NOTE_FRAMES * sources.size + GAP_FRAMES * (sources.size - 1)
        return ShortArray(frames) { frame ->
            val local = frame % (NOTE_FRAMES + GAP_FRAMES)
            if (local >= NOTE_FRAMES) 0 else {
                val step = sources[frame / (NOTE_FRAMES + GAP_FRAMES)]
                (step.sumOf { it[local].toInt() }.toDouble() / step.size).toInt().toShort()
            }
        }
    }
}

internal class AndroidTrainingToneOutput(
    private val application: Application,
    private val permitted: () -> Boolean,
    private val onInterrupted: (TrainingFailure) -> Unit,
    private val worker: CoroutineDispatcher = Dispatchers.IO,
) : TrainingToneOutput {
    private val gate = Any()
    private val manager = application.getSystemService(AudioManager::class.java)
    private var track: AudioTrack? = null
    private var stopped = false
    private var focused = false
    private var registered = false
    private val focus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
        .setAudioAttributes(mediaAttributes()).setWillPauseWhenDucked(true)
        .setOnAudioFocusChangeListener({ change -> if (change != AudioManager.AUDIOFOCUS_GAIN) interrupt() }, Handler(Looper.getMainLooper()))
        .build()
    private val noisy = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) interrupt()
        }
    }

    override suspend fun play(tone: TrainingTone, onPlaying: suspend () -> Unit): Either<TrainingFailure, Unit> {
        var cleanupFailure: TrainingFailure? = null
        val result = try {
            withContext(worker) {
                val samples = TrainingPcm.render(tone) { instrument, midi ->
                    val bytes = application.assets.open("training/${instrument.name.lowercase(Locale.ROOT)}/$midi.pcm").use { it.readBytes() }
                    require(bytes.size == TrainingPcm.NOTE_FRAMES * Short.SIZE_BYTES)
                    val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
                    ShortArray(TrainingPcm.NOTE_FRAMES) { buffer.short }
                }
                coroutineContext.ensureActive()
                val audio = synchronized(gate) {
                    if (stopped || !permitted()) return@withContext TrainingFailure.OutputInterrupted.left()
                    val output = AudioTrack.Builder().setAudioAttributes(mediaAttributes())
                        .setAudioFormat(AudioFormat.Builder().setSampleRate(TrainingPcm.SAMPLE_RATE)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO).setEncoding(AudioFormat.ENCODING_PCM_16BIT).build())
                        .setTransferMode(AudioTrack.MODE_STATIC).setBufferSizeInBytes(samples.size * 2).build()
                    track = output
                    output
                }
                if (audio.write(samples, 0, samples.size) != samples.size || audio.state != AudioTrack.STATE_INITIALIZED) {
                    return@withContext TrainingFailure.PlaybackFailed.left()
                }
                withContext(Dispatchers.Main.immediate) {
                    synchronized(gate) {
                        if (stopped || !permitted()) return@withContext TrainingFailure.OutputInterrupted.left()
                        if (manager.mode != AudioManager.MODE_NORMAL || manager.requestAudioFocus(focus) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                            return@withContext TrainingFailure.FocusDenied.left()
                        }
                        focused = true
                        ContextCompat.registerReceiver(application, noisy, IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY), ContextCompat.RECEIVER_NOT_EXPORTED)
                        registered = true
                        if (!permitted()) return@withContext TrainingFailure.OutputInterrupted.left()
                        audio.play()
                    }
                    onPlaying()
                    Unit.right()
                }.fold({ return@withContext it.left() }, {})
                var elapsed = 0
                while (elapsed < samples.size.toLong() * 1000 / TrainingPcm.SAMPLE_RATE + 1000) {
                    coroutineContext.ensureActive()
                    val finished = synchronized(gate) { stopped || audio.playbackHeadPosition >= samples.size }
                    if (finished) return@withContext Unit.right()
                    delay(10)
                    elapsed += 10
                }
                TrainingFailure.PlaybackFailed.left()
            }
        } catch (_: IOException) {
            TrainingFailure.PlaybackFailed.left()
        } catch (failure: IllegalStateException) {
            if (failure is CancellationException) throw failure
            TrainingFailure.PlaybackFailed.left()
        } catch (_: IllegalArgumentException) {
            TrainingFailure.PlaybackFailed.left()
        } catch (_: SecurityException) {
            TrainingFailure.PlaybackFailed.left()
        } catch (_: UnsupportedOperationException) {
            TrainingFailure.PlaybackFailed.left()
        } finally {
            stop().fold({ cleanupFailure = it }, {})
        }
        return cleanupFailure?.left() ?: result
    }

    private fun interrupt() {
        val active = synchronized(gate) { !stopped }
        if (!active) return
        stop().fold(onInterrupted) { onInterrupted(TrainingFailure.OutputInterrupted) }
    }

    override fun stop(): Either<TrainingFailure, Unit> = synchronized(gate) {
        stopped = true
        var failure: TrainingFailure? = null
        track?.let { audio ->
            try {
                audio.pause()
                audio.flush()
            } catch (_: IllegalStateException) {
                failure = TrainingFailure.ShutdownFailed
            }
            try {
                audio.release()
                track = null
            } catch (_: IllegalStateException) {
                failure = TrainingFailure.ShutdownFailed
            }
        }
        if (registered) {
            application.unregisterReceiver(noisy)
            registered = false
        }
        if (focused) {
            manager.abandonAudioFocusRequest(focus)
            focused = false
        }
        failure?.left() ?: Unit.right()
    }
}
