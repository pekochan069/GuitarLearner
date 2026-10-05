package com.pekochan069.guitarlearner.adapters

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioRecordingConfiguration
import android.media.AudioTimestamp
import android.media.MediaRecorder
import android.os.Build
import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.pekochan069.guitarlearner.domain.MonotonicNanos
import com.pekochan069.guitarlearner.domain.TunerFailure
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicBoolean

internal class AndroidTunerInputFactory(private val application: Application) : TunerInputFactory {
    override fun open(): Either<TunerFailure, TunerInput> {
        if (application.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            return TunerFailure.PermissionRevoked.left()
        }
        val minimum = AudioRecord.getMinBufferSize(RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        if (minimum <= 0) return TunerFailure.InputUnavailable.left()
        var allocated: AudioRecord? = null
        var wrapped: AndroidTunerInput? = null
        var transferred = false
        var cleanupFailure: TunerFailure? = null
        val result = try {
            val record = AudioRecord.Builder().setAudioSource(MediaRecorder.AudioSource.UNPROCESSED)
                .setAudioFormat(AudioFormat.Builder().setSampleRate(RATE).setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT).build())
                .setBufferSizeInBytes(minimum.coerceAtLeast(8192 * 2)).build()
            allocated = record
            val input = AndroidTunerInput(application, record)
            wrapped = input
            if (record.state != AudioRecord.STATE_INITIALIZED) TunerFailure.InputUnavailable.left() else {
                input.register()
                record.startRecording()
                if (record.recordingState != AudioRecord.RECORDSTATE_RECORDING) TunerFailure.InputUnavailable.left() else {
                    transferred = true
                    input.right()
                }
            }
        } catch (_: SecurityException) {
            TunerFailure.PermissionRevoked.left()
        } catch (_: IllegalArgumentException) {
            TunerFailure.InputUnavailable.left()
        } catch (_: IllegalStateException) {
            TunerFailure.InputUnavailable.left()
        } catch (_: UnsupportedOperationException) {
            TunerFailure.InputUnavailable.left()
        } finally {
            if (!transferred) allocated?.let { record ->
                (wrapped?.release() ?: releaseRecord(record)).fold({ cleanupFailure = it }, {})
            }
        }
        return cleanupFailure?.left() ?: result
    }

    companion object { private const val RATE = 48_000 }
}

internal class AndroidTunerInput(private val application: Application, private val record: AudioRecord) : TunerInput {
    override val sampleRateHz: Int get() = record.sampleRate
    private val manager = application.getSystemService(AudioManager::class.java)
    private val released = AtomicBoolean(false)
    private val silenced = AtomicBoolean(false)
    private val timestamp = AudioTimestamp()
    private var framesRead = 0L
    private var registered = false
    private val callback = object : AudioManager.AudioRecordingCallback() {
        override fun onRecordingConfigChanged(configs: MutableList<AudioRecordingConfiguration>) {
            if (Build.VERSION.SDK_INT >= 29) {
                silenced.set(configs.any { it.clientAudioSessionId == record.audioSessionId && it.isClientSilenced })
            }
        }
    }

    fun register() {
        if (Build.VERSION.SDK_INT >= 29) {
            record.registerAudioRecordingCallback(Executor { it.run() }, callback)
            registered = true
        }
    }

    override fun read(buffer: ShortArray): Either<TunerFailure, InputRead> = try {
        val count = record.read(buffer, 0, buffer.size, AudioRecord.READ_NON_BLOCKING)
        when {
            count < 0 -> TunerFailure.InputReadFailed.left()
            count > buffer.size -> TunerFailure.InputReadFailed.left()
            else -> {
                framesRead += count
                val now = System.nanoTime()
                val captured = if (record.getTimestamp(timestamp, AudioTimestamp.TIMEBASE_MONOTONIC) == AudioRecord.SUCCESS) {
                    (timestamp.nanoTime + (framesRead - timestamp.framePosition) * 1_000_000_000L / sampleRateHz).coerceAtMost(now)
                } else now - record.bufferSizeInFrames * 1_000_000_000L / sampleRateHz
                InputRead(count, MonotonicNanos(captured)).right()
            }
        }
    } catch (_: SecurityException) {
        TunerFailure.PermissionRevoked.left()
    } catch (_: IllegalStateException) {
        TunerFailure.InputReadFailed.left()
    } catch (_: IllegalArgumentException) {
        TunerFailure.InputReadFailed.left()
    }

    override fun health(): TunerFailure? = try {
        when {
            application.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED -> TunerFailure.PermissionRevoked
            manager.isMicrophoneMute -> TunerFailure.MicrophoneMuted
            silenced.get() -> TunerFailure.ClientSilenced
            record.state != AudioRecord.STATE_INITIALIZED || record.recordingState != AudioRecord.RECORDSTATE_RECORDING -> TunerFailure.InputUnavailable
            Build.VERSION.SDK_INT >= 29 && record.activeRecordingConfiguration?.isClientSilenced == true -> TunerFailure.ClientSilenced
            else -> null
        }
    } catch (_: SecurityException) {
        TunerFailure.PermissionRevoked
    } catch (_: IllegalStateException) {
        TunerFailure.InputUnavailable
    }

    override fun interrupt(): Either<TunerFailure, Unit> = try {
        if (record.recordingState == AudioRecord.RECORDSTATE_RECORDING) record.stop()
        Unit.right()
    } catch (_: IllegalStateException) {
        TunerFailure.ShutdownFailed.left()
    }

    override fun release(): Either<TunerFailure, Unit> {
        if (!released.compareAndSet(false, true)) return Unit.right()
        var unregisterFailure: TunerFailure? = null
        try {
            if (registered && Build.VERSION.SDK_INT >= 29) record.unregisterAudioRecordingCallback(callback)
        } catch (_: IllegalArgumentException) {
            unregisterFailure = TunerFailure.ShutdownFailed
        } catch (_: IllegalStateException) {
            unregisterFailure = TunerFailure.ShutdownFailed
        } catch (_: SecurityException) {
            unregisterFailure = TunerFailure.ShutdownFailed
        }
        return releaseRecord(record).fold({ it.left() }, { unregisterFailure?.left() ?: Unit.right() })
    }
}

private fun releaseRecord(record: AudioRecord): Either<TunerFailure, Unit> = try {
    record.release()
    Unit.right()
} catch (_: IllegalStateException) {
    TunerFailure.ShutdownFailed.left()
} catch (_: SecurityException) {
    TunerFailure.ShutdownFailed.left()
}
