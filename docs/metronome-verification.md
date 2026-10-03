# Metronome verification

Verified on 2026-10-04 against production commit `c6b4024`.

## Automated checks

The clean checkout with committed Gradle 9.5.0, AGP 9.3.3 and lint 32.3.3 passed module boundaries, all product lint, both debug APK builds and 62 unit tests: 20 architecture, 11 domain, 21 adapter and 10 presenter tests. The user's workspace with Gradle 9.6 and AGP 9.4.1 also passed the affected checks. Those local tooling changes are excluded from this feature.

The boundary guard rejected all three forbidden dependency fixtures and restored its input. The Node acoustic analyzer self-check passed clean, jittered, missing, extra, silent, short, invalid-capture and unverified-continuity fixtures.

Galaxy S26 Ultra instrumentation passed 11 functional tests. The opt-in recording test was skipped in the normal suite. Checks include actual JSON persistence and failed-write retention, rapid Start/Stop, media notification Stop, muted first positions, audio focus loss without automatic restart, background playback, activity recreation, and service removal after a stale Stop command.

## Short acoustic captures

The user limited new recordings to 30 seconds and excluded Buds2 testing. Each final capture requested 25 seconds of playback and recorded 27.1 seconds including microphone warmup and tail. The debug recorder enforces a hard 30-second frame limit. Media volume was temporarily 6/15 with permission, then restored to 3/15. Microphone permission was revoked after the captures.

Device: Samsung SM-S948N, Android 17, API 37. Actual output was the built-in speaker, device ID 3. Input was the built-in microphone, device ID 22, at 48,000 Hz with VOICE_RECOGNITION capture. All final captures reported zero output underruns and passed the input continuity guards.

Analysis used `--minimum-seconds 20`, the default threshold ratio 0.15 and the default 20 ms refractory interval. No timing normalization or waveform filtering was applied.

| Requested BPM | Detected clicks | Click span, s | Mean BPM | Mean error, % | p95 interval error, ms | Maximum error, ms | Cumulative drift, ms |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 40 | 17 | 24.0000000 | 40.000000 | 0 | 0 | 0 | 0 |
| 120 | 50 | 24.4999375 | 120.000306 | 0.000255103 | 0 | 0.0625 | -0.0625 |
| 240 | 100 | 24.7499375 | 240.000606 | 0.000252526 | 0 | 0.0625 | -0.0625 |

All three passed the requested short-capture criteria. No missing, extra or ambiguous intervals were detected between their first and last candidate clicks. A reported 0 ms error means matching detected sample positions at this capture resolution, not an independent guarantee of zero acoustic jitter.

The initial engine produced a weak first normal click in the 40 BPM capture: peak 227 versus 2,729 for the next click. After adding 50 ms of silent output preroll, the first two peaks were 2,843 and 3,310, and all 17 expected candidates were detected. Presentation markers include the same preroll offset; the domain beat index and spacing are unchanged.

Earlier noisy captures failed the detector criteria and were retained. The already running long 120 BPM recording was stopped when the user imposed the 30-second limit; its metadata is invalid and it is excluded from these results.

## Reproduction artifacts

Local raw WAV files and their full JSON metadata are retained under `%TEMP%/GuitarLearner-metronome-recordings`. The repository contains the [recorder and analysis instructions](metronome-timing.md), not the microphone recordings.

| WAV filename | SHA-256 |
| --- | --- |
| speaker-40-short-2.wav | 1339330db17f557b3c3799b09c48e1d2557ed3c9691390e013bfc34ff1171ba7 |
| speaker-120-short-2.wav | a74eaba6807eb888874038e3f03fca5643d54af6ad3311623b0e4280f5137ca4 |
| speaker-240-short-2.wav | 3f98233c259944832c6bea255003a442792693064f6a4b5fdcd985fe9600518b |

## Evidence limits

These intervals are relative to the phone's capture clock; playback and capture can share a clock. Independent absolute tempo calibration, Start-to-output latency and actual screen/sound offset remain unverified. Software playback timestamps are not measurements of display photons or independent acoustic output latency.

The amplitude detector can merge close clicks or miss sub-threshold clicks; unrelated transients can count as clicks. Input continuity guards cannot prove a lossless vendor capture pipeline. First and last missing clicks are not proved by an unanchored recording.

Five-minute stability was not evaluated after the recording limit changed. Buds2 timing and disconnection were not physically tested. Actual calls, screen-lock continuity and OEM notification pill/Now Bar presentation remain unverified; native media notifications and their Stop action passed on the phone. The implementation supplies a media session and background playback service, while pill presentation depends on the operating system.
