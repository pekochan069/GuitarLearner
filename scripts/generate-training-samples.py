"""Convert the two pinned FreePats SFZ banks into the app's short PCM notes."""

import argparse
import math
from pathlib import Path
import re
import struct
import subprocess


RATE = 48_000
FRAMES = 31_200
BANKS = {
    "piano": "piano/UprightPianoKW-small-SFZ-20190703",
    "guitar": "guitar/SpanishClassicalGuitar-SFZ-20190618",
}


def decode(path, rate):
    return subprocess.check_output([
        "ffmpeg", "-v", "error", "-i", str(path), "-ac", "1", "-ar", str(rate),
        "-f", "f32le", "pipe:1",
    ])


def floats(data):
    return [value[0] for value in struct.iter_unpack("<f", data)]


def fundamental(samples, expected, rate=16_000):
    start = int(rate * 0.10)
    count = min(int(rate * 0.4), len(samples) - start - int(rate / expected * 1.1))
    if count <= 0:
        raise ValueError("Sample too short to check its pitch")
    lower = math.floor(rate / (expected * 2 ** (70 / 1200)))
    upper = math.ceil(rate / (expected / 2 ** (70 / 1200)))
    scores = {}
    for lag in range(lower - 1, upper + 2):
        pairs = zip(samples[start:start + count], samples[start + lag:start + lag + count])
        cross = left = right = 0.0
        for a, b in pairs:
            cross += a * b
            left += a * a
            right += b * b
        scores[lag] = cross / math.sqrt(left * right) if left * right else 0.0
    peak = max(range(lower, upper + 1), key=scores.get)
    a, b, c = scores[peak - 1], scores[peak], scores[peak + 1]
    fraction = (a - c) / (2 * (a - 2 * b + c)) if a - 2 * b + c else 0.0
    if peak in (lower, upper) or b < 0.65:
        raise ValueError(f"Uncertain sample pitch: correlation {b:.3f}, lag {peak}")
    return rate / (peak + fraction)


def regions(directory):
    source = next(directory.glob("*.sfz")).read_text()
    result = []
    for region in source.split("<region>")[1:]:
        values = dict(re.findall(r"(\w+)=([^\s]+)", region))
        root = int(values.get("pitch_keycenter", values.get("key")))
        low = int(values.get("lokey", values.get("key", root)))
        high = int(values.get("hikey", values.get("key", root)))
        result.append((low, high, root, directory / values["sample"]))
    return result


def generate(sources, destination):
    for instrument, relative in BANKS.items():
        mapping = regions(sources / relative)
        roots = {}
        output = destination / instrument
        output.mkdir(parents=True, exist_ok=True)
        for midi in range(40, 77):
            low, high, root, path = next(region for region in mapping if region[0] <= midi <= region[1])
            if path not in roots:
                expected = 440 * 2 ** ((root - 69) / 12)
                measured = fundamental(floats(decode(path, 16_000)), expected)
                roots[path] = (floats(decode(path, RATE)), measured)
            original, measured = roots[path]
            target = 440 * 2 ** ((midi - 69) / 12)
            step = target / measured
            samples = []
            for frame in range(FRAMES):
                position = frame * step
                index = int(position)
                if index + 1 < len(original):
                    fraction = position - index
                    value = original[index] * (1 - fraction) + original[index + 1] * fraction
                else:
                    value = 0.0
                fade = min(1.0, frame / 480, (FRAMES - 1 - frame) / 480)
                samples.append(value * fade)
            peak = max(map(abs, samples))
            if peak < 0.001:
                raise ValueError(f"Silent sample: {path}")
            pcm = b"".join(struct.pack("<h", round(value / peak * 12_000)) for value in samples)
            (output / f"{midi}.pcm").write_bytes(pcm)
            print(f"{instrument} {midi}: {path.name}, source {measured:.3f} Hz, target {target:.3f} Hz")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("sources", type=Path, help="Extracted banks, in piano/ and guitar/ subfolders")
    parser.add_argument("--output", type=Path, default=Path(__file__).resolve().parents[1] / "adapters/src/main/assets/training")
    args = parser.parse_args()
    generate(args.sources, args.output)
