# Measure acoustic metronome intervals

The debug-only recording test captures the physical output through the phone's built-in microphone. It uses an all-normal 4/4 pattern, preserves media volume, verifies the actual input and playback route, saves WAV and JSON files, attempts every configuration restoration step, and leaves playback stopped. Normal test runs skip recording. Only the debug manifest requests microphone permission.

The user limits recordings to 30 seconds. The test caps captured frames at 30 seconds, including microphone warmup, playback startup, and the trailing second. Requested playback duration is 1–27 seconds, default 15. If startup leaves too little time, the capture fails and stops instead of extending the recording.

Use a quiet room. Keep the phone and output source stationary. For Buds2, select the earbuds as the media output and place one earbud near the phone microphone. Confirm that the JSON `output_name` identifies the intended earbuds. Keep other sounds out of the recording. A weak, noisy, or clipped capture cannot establish missing or extra clicks.

Build and install the debug app and instrumentation APK through the project's normal Android workflow. Grant microphone permission to the debug target explicitly:

```powershell
adb shell pm grant com.pekochan069.guitarlearner android.permission.RECORD_AUDIO
```

Record a short speaker smoke test first. Use a new filename for every capture; the test preserves existing recordings.

```powershell
adb shell am instrument -w -e class com.pekochan069.guitarlearner.MetronomeTimingRecordingTest -e record_acoustic true -e bpm 120 -e duration_seconds 15 -e route speaker -e filename speaker-120-smoke.wav com.pekochan069.guitarlearner.test/androidx.test.runner.AndroidJUnitRunner
```

For the current run, use `duration_seconds 25` at each speaker tempo below. Total capture remains within 30 seconds. The user excluded Buds tests from this run; the Buds rows describe optional future captures and provide no current evidence.

| Output | BPM | Filename |
| --- | --- | --- |
| S26 Ultra speaker | 40 | speaker-40-short.wav |
| S26 Ultra speaker | 120 | speaker-120-short.wav |
| S26 Ultra speaker | 240 | speaker-240-short.wav |
| Galaxy Buds2, optional | 40 | buds2-40-short.wav |
| Galaxy Buds2, optional | 120 | buds2-120-short.wav |
| Galaxy Buds2, optional | 240 | buds2-240-short.wav |

```powershell
adb shell am instrument -w -e class com.pekochan069.guitarlearner.MetronomeTimingRecordingTest -e record_acoustic true -e bpm 120 -e duration_seconds 25 -e route speaker -e filename speaker-120-short.wav com.pekochan069.guitarlearner.test/androidx.test.runner.AndroidJUnitRunner
```

Retrieve both files without converting binary WAV data through a text pipeline. This Node command writes the raw `adb exec-out` bytes:

```powershell
node --input-type=module -e 'import {spawnSync} from "node:child_process"; import {writeFileSync} from "node:fs"; for (const name of ["speaker-120-smoke.wav","speaker-120-smoke.json"]) { const r=spawnSync("adb",["exec-out","run-as","com.pekochan069.guitarlearner","cat","files/"+name],{maxBuffer:100*1024*1024}); if(r.error||r.status!==0) throw r.error||Error(r.stderr.toString()); writeFileSync(name,r.stdout); }'
node scripts/analyze-metronome.mjs speaker-120-smoke.wav --bpm 120 --minimum-seconds 10
node scripts/analyze-metronome.mjs speaker-120-short.wav --bpm 120 --minimum-seconds 20
node scripts/test-analyze-metronome.mjs
```

The analyzer reports raw onset times, every interval, mean BPM/error, p95 and maximum absolute interval error, cumulative drift, count estimates, clipping, thresholds, route metadata, and individual pass criteria. Exit codes are 0 for the requested criteria, 1 for a failed criterion, and 2 for invalid or insufficient input. With `--minimum-seconds 20`, `pass` checks the measured short interval against mean tempo error ≤0.1%, p95 absolute interval error ≤5 ms, no detected missing/extra/ambiguous intervals, and valid route/input-continuity metadata. Missing JSON files and older captures without continuity evidence cannot pass.

The previous five-minute acceptance criterion is not evaluated because of the user's recording limit. `acceptance_duration_met` and `hardware_acceptance_pass` retain that criterion and remain false for these short captures. Report short interval results with their actual measured duration; do not claim five-minute stability.

The recorder samples input timestamps on every approximately 100 ms read. It rejects timestamp regressions or staleness, read stalls longer than the native buffer duration, and accumulated time/frame or producer/read discrepancies larger than the native buffer capacity. The JSON retains the raw observations, buffer size, and any continuity failures. These bounds detect some input overruns; smaller drops and vendor timestamp behavior can evade them. A checked capture is not proof of a lossless input pipeline. [AudioRecord timestamps and buffer size](https://developer.android.com/reference/android/media/AudioRecord#getTimestamp(android.media.AudioTimestamp,%20int))

Listen to the WAV and inspect its waveform before accepting detected counts. The detector uses relative amplitude, hysteresis, and a 20 ms refractory interval. Echoes or unrelated transients can count as clicks; clicks below the threshold or closer than the refractory interval can be missed or merged. First/last missing clicks cannot be proved from an unanchored recording. Adjust `--threshold-ratio` or `--refractory-ms` only to match an inspected capture, retain the raw files and settings, and report the resulting detection limit.

Phone loopback capture and playback may share a clock. This gives acoustic interval evidence relative to that capture clock, with no independent clock calibration. Report the device/OS, actual output ID/name/type, microphone source, volume, capture timestamps, and underruns alongside results. [Android audio latency measurement](https://source.android.com/docs/core/audio/latency/measure)

Start/output latency and screen/sound offset remain unverified by this tool. Measure them with a synchronized independent reference and external video that captures actual display changes and audible clicks. Software state timestamps cannot replace screen photons. Bluetooth results apply only to the captured route and conditions. The test requests UNPROCESSED capture when supported and otherwise VOICE_RECOGNITION; actual microphone routing is checked after capture starts. [AudioRecord routing](https://developer.android.com/reference/android/media/AudioRecord#getRoutedDevice()), [Android audio sources](https://developer.android.com/reference/android/media/MediaRecorder.AudioSource)
