package com.pekochan069.guitarlearner

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTimestamp
import android.media.MediaRecorder
import android.os.Build
import android.os.Bundle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.pekochan069.guitarlearner.adapters.AndroidMetronomeHost
import com.pekochan069.guitarlearner.domain.BeatAccent
import com.pekochan069.guitarlearner.domain.BeatUnit
import com.pekochan069.guitarlearner.domain.MetronomeCommand
import com.pekochan069.guitarlearner.domain.PlaybackState
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MetronomeTimingRecordingTest {
    @Test
    @SuppressLint("MissingPermission")
    fun recordAcousticClicks() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val args = InstrumentationRegistry.getArguments()
        assumeTrue("Explicit acoustic recording only", args.getString("record_acoustic") == "true")
        check(context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            "Grant RECORD_AUDIO to the debug target package before this explicit recording test"
        }
        val bpm = intArgument(args, "bpm", 120)
        val duration = intArgument(args, "duration_seconds", 15)
        val route = args.getString("route") ?: "speaker"
        require(bpm in 40..240 && duration in 1..3600)
        require(route == "speaker" || route == "buds2")
        val filename = args.getString("filename") ?: "metronome-$route-$bpm-${System.currentTimeMillis()}.wav"
        require(filename.matches(Regex("[A-Za-z0-9._-]+\\.wav")))
        val wav = File(context.filesDir, filename)
        check(!wav.exists()) { "Use a new filename to preserve the existing recording" }

        ActivityScenario.launch(MainActivity::class.java).use {
            runBlocking {
                val host = (context.applicationContext as GuitarLearnerApplication).graph.metronomeHost
                val previous = host.current.value.selected
                try {
                    host.checked(MetronomeCommand.Stop)
                    host.checked(MetronomeCommand.SetTempo(bpm))
                    host.checked(MetronomeCommand.SetPattern(BeatUnit.Quarter, List(4) { BeatAccent.Normal }))
                    val manager = context.getSystemService(AudioManager::class.java)
                    val source = if (manager.getProperty(AudioManager.PROPERTY_SUPPORT_AUDIO_SOURCE_UNPROCESSED) == "true") {
                        MediaRecorder.AudioSource.UNPROCESSED
                    } else {
                        MediaRecorder.AudioSource.VOICE_RECOGNITION
                    }
                    val sampleRate = 48_000
                    val minimumBuffer = AudioRecord.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
                    check(minimumBuffer > 0) { "Microphone format is unsupported" }
                    val recorder = AudioRecord.Builder()
                        .setAudioSource(source)
                        .setAudioFormat(AudioFormat.Builder().setSampleRate(sampleRate).setChannelMask(AudioFormat.CHANNEL_IN_MONO).setEncoding(AudioFormat.ENCODING_PCM_16BIT).build())
                        .setBufferSizeInBytes(maxOf(minimumBuffer * 4, sampleRate * 2 / 5))
                        .build()
                    try {
                        check(recorder.state == AudioRecord.STATE_INITIALIZED)
                        val builtInMic = manager.getDevices(AudioManager.GET_DEVICES_INPUTS).firstOrNull { device ->
                            device.type == AudioDeviceInfo.TYPE_BUILTIN_MIC
                        } ?: error("No built-in microphone available")
                        check(recorder.setPreferredDevice(builtInMic))

                        val frames = AtomicLong(0)
                        val playbackStart = AtomicLong(Long.MAX_VALUE)
                        val captureEnd = AtomicLong(Long.MAX_VALUE)
                        val validateOutput = AtomicBoolean(false)
                        val warmupComplete = CompletableDeferred<Unit>()
                        val durationComplete = CompletableDeferred<Unit>()
                        val routes = JSONArray()
                        val timestamps = JSONArray()
                        var completed = false
                        val metadata = JSONObject()
                            .put("bpm", bpm).put("duration_seconds", duration).put("route_label", route)
                            .put("audio_source", source).put("sample_rate_hz", sampleRate)
                            .put("media_volume", manager.getStreamVolume(AudioManager.STREAM_MUSIC))
                            .put("manufacturer", Build.MANUFACTURER).put("model", Build.MODEL)
                            .put("sdk", Build.VERSION.SDK_INT).put("os_release", Build.VERSION.RELEASE)
                            .put("capture_clock_independently_calibrated", false)
                        var lastRoute = ""
                        recorder.startRecording()
                        val capture = async(Dispatchers.IO) {
                            val samples = ShortArray(sampleRate / 10)
                            val bytes = ByteBuffer.allocate(samples.size * 2).order(ByteOrder.LITTLE_ENDIAN)
                            RandomAccessFile(wav, "rw").use { output ->
                                output.write(ByteArray(44))
                                try {
                                    while (frames.get() < captureEnd.get()) {
                                        val requested = minOf(samples.size.toLong(), captureEnd.get() - frames.get()).toInt()
                                        val count = recorder.read(samples, 0, requested, AudioRecord.READ_BLOCKING)
                                        check(count > 0) { "AudioRecord read failed: $count" }
                                        bytes.clear()
                                        repeat(count) { index -> bytes.putShort(samples[index]) }
                                        output.write(bytes.array(), 0, count * 2)
                                        val captured = frames.addAndGet(count.toLong())
                                        val input = recorder.routedDevice
                                        check(input?.type == AudioDeviceInfo.TYPE_BUILTIN_MIC) { "Capture did not use the built-in microphone" }
                                        if (captured >= sampleRate) warmupComplete.complete(Unit)
                                        val startFrame = playbackStart.get()
                                        if (validateOutput.get() && captured < startFrame + duration * sampleRate.toLong()) {
                                            val diagnostics = host.currentAudioDiagnostics() ?: error("Playback diagnostics disappeared during recording")
                                            check(routeMatches(route, diagnostics.outputType)) { "Expected $route, actual output type ${diagnostics.outputType}" }
                                            check(host.current.value.playback is PlaybackState.Playing) { "Playback stopped during the capture" }
                                            val routeEntry = JSONObject()
                                                .put("captured_frame", captured).put("input_type", input.type).put("input_id", input.id)
                                                .put("output_type", diagnostics.outputType).put("output_id", diagnostics.outputDeviceId)
                                                .put("output_name", diagnostics.outputName).put("output_sample_rate_hz", diagnostics.sampleRate)
                                                .put("output_underruns", diagnostics.underruns).put("output_timestamp_used", diagnostics.timestampUsed)
                                            val key = "${input.id}:${diagnostics.outputDeviceId}:${diagnostics.outputType}:${diagnostics.underruns}"
                                            if (key != lastRoute) {
                                                routes.put(routeEntry)
                                                lastRoute = key
                                            }
                                        }
                                        if (startFrame != Long.MAX_VALUE && captured >= startFrame + duration * sampleRate.toLong()) durationComplete.complete(Unit)
                                        val timestamp = AudioTimestamp()
                                        if (recorder.getTimestamp(timestamp, AudioTimestamp.TIMEBASE_MONOTONIC) == AudioRecord.SUCCESS &&
                                            (timestamps.length() == 0 || captured >= captureEnd.get())
                                        ) {
                                            timestamps.put(JSONObject().put("captured_frame", captured).put("frame_position", timestamp.framePosition).put("monotonic_nanos", timestamp.nanoTime))
                                        }
                                    }
                                } finally {
                                    writeWavHeader(output, sampleRate, frames.get().toInt() * 2)
                                }
                            }
                        }
                        try {
                            withTimeout(10_000) { warmupComplete.await() }
                            host.checked(MetronomeCommand.Start)
                            withTimeout(10_000) { host.current.first { snapshot -> snapshot.playback is PlaybackState.Playing || snapshot.playback is PlaybackState.Failed } }
                            check(host.current.value.playback is PlaybackState.Playing) { "Playback failed to start" }
                            withTimeout(3_000) {
                                while (host.currentAudioDiagnostics()?.outputType == null) kotlinx.coroutines.delay(20)
                            }
                            check(routeMatches(route, host.currentAudioDiagnostics()?.outputType)) { "Playback route does not match the requested route" }
                            playbackStart.set(frames.get())
                            captureEnd.set(playbackStart.get() + (duration + 1) * sampleRate.toLong())
                            validateOutput.set(true)
                            withTimeout((duration + 15) * 1000L) { durationComplete.await() }
                            validateOutput.set(false)
                            host.checked(MetronomeCommand.Stop)
                            withTimeout(5_000) { capture.await() }
                            completed = true
                        } finally {
                            withContext(NonCancellable) {
                                validateOutput.set(false)
                                try {
                                    if (recorder.recordingState == AudioRecord.RECORDSTATE_RECORDING) recorder.stop()
                                } finally {
                                    capture.cancelAndJoin()
                                    metadata.put("capture_valid", completed).put("route_verified", completed && routes.length() > 0)
                                        .put("captured_frames", frames.get()).put("playback_start_capture_frame", playbackStart.get())
                                        .put("recorded_seconds", frames.get().toDouble() / sampleRate)
                                        .put("route_observations", routes).put("capture_timestamps", timestamps)
                                    if (timestamps.length() >= 2) {
                                        val first = timestamps.getJSONObject(0)
                                        val last = timestamps.getJSONObject(timestamps.length() - 1)
                                        metadata.put("capture_timestamp_elapsed_seconds", (last.getLong("monotonic_nanos") - first.getLong("monotonic_nanos")) / 1e9)
                                    }
                                    File(context.filesDir, filename.removeSuffix(".wav") + ".json").writeText(metadata.toString(2))
                                }
                            }
                        }
                        instrumentation.sendStatus(0, Bundle().apply { putString("acoustic_recording", wav.absolutePath) })
                    } finally {
                        recorder.release()
                    }
                } finally {
                    withContext(NonCancellable) {
                        host.checked(MetronomeCommand.Stop)
                        host.checked(MetronomeCommand.SetPattern(previous.denominator, previous.beats))
                        host.checked(MetronomeCommand.SetTempo(previous.bpm))
                    }
                }
            }
        }
    }
}

private suspend fun AndroidMetronomeHost.checked(command: MetronomeCommand) {
    execute(command).fold({ failure -> error("$command failed: $failure") }, {})
}

private fun intArgument(args: Bundle, name: String, default: Int): Int =
    args.getString(name)?.let { value -> value.toIntOrNull() ?: error("$name must be an integer") } ?: default

private fun routeMatches(route: String, outputType: Int?): Boolean = when (route) {
    "speaker" -> outputType == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER
    "buds2" -> outputType == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
        (Build.VERSION.SDK_INT >= 31 && outputType == AudioDeviceInfo.TYPE_BLE_HEADSET)
    else -> false
}

private fun writeWavHeader(output: RandomAccessFile, sampleRate: Int, dataBytes: Int) {
    val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
        .put("RIFF".toByteArray(Charsets.US_ASCII)).putInt(36 + dataBytes)
        .put("WAVEfmt ".toByteArray(Charsets.US_ASCII)).putInt(16)
        .putShort(1).putShort(1).putInt(sampleRate).putInt(sampleRate * 2)
        .putShort(2).putShort(16).put("data".toByteArray(Charsets.US_ASCII)).putInt(dataBytes)
    output.seek(0)
    output.write(header.array())
}
