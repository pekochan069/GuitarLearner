# Training instrument samples

Listening exercises use recorded piano and classical guitar samples from FreePats. The original banks and the derived PCM notes are covered by the [CC0 1.0 public domain dedication](https://creativecommons.org/publicdomain/zero/1.0/). The full license is bundled at `adapters/src/main/assets/training/CC0-1.0.txt`.

| Instrument | Source | Version | Archive SHA-256 |
| --- | --- | --- | --- |
| Piano | [Upright piano KW](https://freepats.zenvoid.org/Piano/acoustic-grand-piano.html#UprightKW), recorded by Gonzalo and Roberto from a Kawai upright piano | 2019-07-03, small SFZ/WAV bank | `ffb547fceb91eeb93d78cf8d220d1d5f726dcac891afc15e517805ac48abb39d` |
| Guitar | [Spanish classical guitar](https://freepats.zenvoid.org/Guitar/acoustic-guitar.html), recorded and processed by Roberto | 2019-06-18, SFZ/WAV bank | `ef2fb7de0cc0ab561c4ebc28494f3fc2962596e4f32f16d6c96b8a385c7c098b` |

Each instrument has 37 bundled notes, MIDI 40 through 76, covering E2 through E5 and the named C4 comparison note. Each file contains 31,200 mono signed 16-bit little-endian samples at 48 kHz. Each note lasts 650 ms. The runtime combines these notes for ascending, descending, or simultaneous intervals using the existing AudioTrack lifecycle.

The conversion follows each bank's SFZ key ranges. It measures the recorded root's fundamental over 100–500 ms, resamples it toward A4=440 Hz equal temperament, and applies a 10 ms fade at each end. Peak amplitude is normalized to 12,000, leaving mixing headroom. Real instrument recordings contain harmonics and slight pitch variation; this conversion does not establish microphone or acoustic timing accuracy.

## Regenerate

Install Python 3, FFmpeg, and 7-Zip on the development machine. No Python packages are needed. Download the pinned archives and verify their SHA-256 values above:

- [Piano archive](https://freepats.zenvoid.org/Piano/UprightPianoKW/UprightPianoKW-small-SFZ-20190703.7z)
- [Guitar archive](https://freepats.zenvoid.org/Guitar/SpanishClassicalGuitar/SpanishClassicalGuitar-SFZ-20190618.7z)

Extract the piano archive into `<sources>/piano/` and the guitar archive into `<sources>/guitar/`, preserving the archive directories. Run from the repository root:

```powershell
python scripts/generate-training-samples.py <sources>
```

The script writes the PCM notes under `adapters/src/main/assets/training/`. Source archives and development tools are not required to build or run the app.
