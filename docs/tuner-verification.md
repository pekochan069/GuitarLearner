# Tuner verification

The production detector uses periodicity, an observed fundamental, and harmonic energy to support a pitch. Uncertain input does not earn an in-tune judgment. Pluck one string at a time. A microphone cannot distinguish every mixture from the same waveform produced by one string, including some octave-related strings and harmonics. These tests do not establish universal monophony or phone microphone accuracy.

## Run recorded-guitar checks

The optional test calls `YinHarmonicDetector` and `TuningPolicy`. It fails if an accepted result selects the wrong standard string, differs from the independent reference by 30 cents, or has a median error above 12 cents. Each recording must support at least 20 of the 50 tested windows between 0.25 and 1.25 seconds. Other windows may abstain.

Download the medium-dynamic mono recordings from the [University of Iowa guitar sample collection](https://theremin.music.uiowa.edu/MISguitar.html). The recordings use a Raimundo 118, performed by Brian Penkrot on December 11, 2011. They were recorded in an anechoic chamber with an Earthwork QTC40 and Metric Halo interface. The first note in each chromatic recording supplies the corresponding fixture.

The source prefix is `https://theremin.music.uiowa.edu/sound%20files/MIS/Piano_Other/guitar/`. Convert the first four seconds to 48 kHz PCM16 mono WAV using `ffmpeg -i SOURCE -t 4 -ar 48000 -ac 1 -c:a pcm_s16le NOTE.wav`. Keep the external audio outside the repository.

| WAV | Source filename | Independent fundamental Hz | Accepted windows | Detector median Hz | Cents from standard |
| --- | --- | ---: | ---: | ---: | ---: |
| E2.wav | Guitar.mf.sulE.E2B2.mono.aif | 80.30420 | 33/50 | 80.28624 | -45.13 |
| A2.wav | Guitar.mf.sulA.A2B2.mono.aif | 108.60821 | 46/50 | 108.55266 | -22.93 |
| D3.wav | Guitar.mf.sulD.D3B3.mono.aif | 144.65742 | 50/50 | 144.65975 | -25.81 |
| G3.wav | Guitar.mf.sulG.G3B3.mono.aif | 192.91610 | 50/50 | 193.04011 | -26.32 |
| B3.wav | Guitar.mf.sulB.B3.mono.aif | 244.00083 | 43/50 | 244.00724 | -20.70 |
| E4.wav | Guitar.mf.sul_E.E4B4.mono.aif | 325.18869 | 49/50 | 325.13879 | -23.74 |

These are human performances below exact standard tuning, so acceptance does not mean that they are in tune. Independent frequencies came from a Hann spectrum of 0.25 to 1.25 seconds, with quadratic interpolation near each expected note.

The desktop JVM run measured mean detector time per window from 0.49 to 2.06 ms, including the first fixture's warmup. This is desktop evidence and does not measure Android capture or display latency.

Run the check from the repository root:

```powershell
pwsh scripts/verify-tuner-guitar.ps1 -FixtureDirectory C:/path/to/guitar-fixtures
```

You can also set `TUNER_GUITAR_FIXTURES` and run `./gradlew.bat :domain:test --rerun-tasks`. The rerun flag prevents a prior skipped fixture test from being reused. The fixture test is skipped when the environment variable is absent.

## Evidence limits

Synthetic tests cover exact tones, signed cents, six strings, dominant harmonics, a missing fundamental, identifiable mixed strings, noise, decay, weak input, and clipping. Policy tests cover continuous 300 ms dwell, tolerance boundaries, invalid input, gaps, resets, absolute octaves, and independent 900 ms expiry. Recorded fixtures exercise the actual detector on authored guitar audio. Live microphone capture, device privacy controls, rotation, and shutdown need separate Android device evidence.
