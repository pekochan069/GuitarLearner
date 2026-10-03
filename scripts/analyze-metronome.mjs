#!/usr/bin/env node
import { readFileSync, existsSync } from "node:fs";
import { extname } from "node:path";
import { pathToFileURL } from "node:url";

function readWav(path) {
  const bytes = readFileSync(path);
  if (bytes.length < 44 || bytes.toString("ascii", 0, 4) !== "RIFF" || bytes.toString("ascii", 8, 12) !== "WAVE") {
    throw new Error("Expected a RIFF WAV file");
  }
  let format;
  let audio;
  for (let offset = 12; offset + 8 <= bytes.length;) {
    const name = bytes.toString("ascii", offset, offset + 4);
    const length = bytes.readUInt32LE(offset + 4);
    const start = offset + 8;
    if (start + length > bytes.length) throw new Error("Truncated WAV chunk");
    if (name === "fmt ") {
      if (length < 16) throw new Error("Invalid WAV format chunk");
      format = { encoding: bytes.readUInt16LE(start), channels: bytes.readUInt16LE(start + 2), sampleRate: bytes.readUInt32LE(start + 4), bits: bytes.readUInt16LE(start + 14) };
    }
    if (name === "data") audio = bytes.subarray(start, start + length);
    offset = start + length + (length % 2);
  }
  if (!format || !audio || format.encoding !== 1 || format.channels !== 1 || format.bits !== 16 || audio.length % 2) {
    throw new Error("Expected uncompressed mono PCM16 WAV");
  }
  if (format.sampleRate < 1000 || audio.length / 2 < format.sampleRate) throw new Error("Recording is shorter than one second or has an invalid sample rate");
  return { audio, sampleRate: format.sampleRate };
}

function percentile(sorted, fraction) {
  const position = fraction * (sorted.length - 1);
  const lower = Math.floor(position);
  const upper = Math.ceil(position);
  return sorted[lower] + (sorted[upper] - sorted[lower]) * (position - lower);
}

export function analyze(path, bpm, minimumSeconds = 300, thresholdRatio = 0.15, refractoryMs = 20) {
  if (!Number.isInteger(bpm) || bpm < 40 || bpm > 240 || !Number.isFinite(minimumSeconds) || minimumSeconds < 0) {
    throw new Error("BPM must be 40-240 and minimum duration must be nonnegative");
  }
  const period = 60 / bpm;
  if (!(thresholdRatio >= 0.01 && thresholdRatio <= 0.8 && refractoryMs >= 1 && refractoryMs < period * 500)) {
    throw new Error("Invalid detector threshold or refractory period");
  }
  const { audio, sampleRate } = readWav(path);
  const sampleCount = audio.length / 2;
  const window = Math.max(1, Math.floor(sampleRate / 1000));
  const envelope = [];
  let peak = 0;
  let clipped = 0;
  for (let start = 0; start < sampleCount; start += window) {
    let level = 0;
    for (let frame = start; frame < Math.min(start + window, sampleCount); frame++) {
      const sample = Math.abs(audio.readInt16LE(frame * 2));
      level = Math.max(level, sample);
      if (sample >= 32760) clipped++;
    }
    envelope.push(level);
    peak = Math.max(peak, level);
  }
  const noise = percentile([...envelope].sort((left, right) => left - right), 0.5);
  if (peak < 200 || peak < noise * 8) throw new Error("Insufficient separation between clicks and background noise");
  const high = Math.max(100, peak * thresholdRatio, noise * 6);
  const low = Math.max(noise * 3, high * 0.4);
  const refractoryFrames = Math.ceil(sampleRate * refractoryMs / 1000);
  const quietWindows = Math.ceil(0.005 * sampleRate / window);
  let armed = true;
  let quiet = 0;
  const onsets = [];
  for (let index = 0; index < envelope.length; index++) {
    const level = envelope[index];
    if (level <= low) {
      if (++quiet >= quietWindows) armed = true;
    } else {
      quiet = 0;
    }
    if (armed && level >= high) {
      let onset = index * window;
      while (Math.abs(audio.readInt16LE(onset * 2)) < high) onset++;
      if (!onsets.length || onset - onsets.at(-1) >= refractoryFrames) {
        onsets.push(onset);
        armed = false;
      }
    }
  }
  if (onsets.length < 3) throw new Error("Fewer than three distinct audible click candidates");

  const intervals = onsets.slice(1).map((right, index) => (right - onsets[index]) / sampleRate);
  const observed = (onsets.at(-1) - onsets[0]) / sampleRate;
  const meanBpm = 60 / (intervals.reduce((sum, interval) => sum + interval, 0) / intervals.length);
  const meanErrorPercent = Math.abs(meanBpm - bpm) / bpm * 100;
  const errors = intervals.map(interval => Math.abs(interval - period) * 1000).sort((left, right) => left - right);
  const p95 = percentile(errors, 0.95);
  const missing = intervals.reduce((count, interval) => count + Math.max(0, Math.floor(interval / period + 0.5) - 1), 0);
  const expectedCount = Math.floor(observed / period + 0.5) + 1;
  const extra = Math.max(0, onsets.length - expectedCount + missing);
  const ambiguous = intervals.filter(interval => interval < 0.75 * period || interval > 1.25 * period).length;
  const checks = {
    minimum_observed_duration: observed >= minimumSeconds,
    mean_tempo_error_at_most_0_1_percent: meanErrorPercent <= 0.1,
    p95_interval_error_at_most_5_ms: p95 <= 5,
    no_estimated_missing_clicks: missing === 0,
    no_estimated_extra_clicks: extra === 0,
    no_ambiguous_intervals: ambiguous === 0,
    unclipped_recording: clipped === 0,
  };
  const metadataPath = path.slice(0, path.length - extname(path).length) + ".json";
  const metadata = existsSync(metadataPath) ? JSON.parse(readFileSync(metadataPath, "utf8")) : null;
  if (metadata) checks.capture_metadata_valid = Boolean(metadata.capture_valid && metadata.route_verified && metadata.bpm === bpm);
  return {
    wav: path,
    sample_rate_hz: sampleRate,
    recorded_seconds: sampleCount / sampleRate,
    observed_click_span_seconds: observed,
    requested_bpm: bpm,
    click_count: onsets.length,
    expected_click_count_between_first_and_last: expectedCount,
    mean_bpm: meanBpm,
    mean_tempo_error_percent: meanErrorPercent,
    p95_absolute_interval_error_ms: p95,
    maximum_absolute_interval_error_ms: errors.at(-1),
    cumulative_drift_ms: (observed - (onsets.length - 1) * period) * 1000,
    estimated_missing_clicks: missing,
    estimated_extra_clicks: extra,
    ambiguous_interval_count: ambiguous,
    clipped_samples: clipped,
    detector: { high_threshold: high, low_threshold: low, noise_median: noise, peak, minimum_resolvable_separation_ms: refractoryMs },
    click_onsets_seconds: onsets.map(onset => onset / sampleRate),
    intervals_seconds: intervals,
    checks,
    pass: Object.values(checks).every(Boolean),
    acceptance_duration_met: observed >= 300,
    capture_metadata: metadata,
    capture_route_verified: Boolean(metadata?.route_verified),
    absolute_clock_accuracy: "unverified; capture and output may share the phone clock",
    start_output_latency: "unverified; requires a synchronized independent reference",
    screen_sound_offset: "unverified; requires synchronized external video",
    detection_limits: "Counts describe detected onsets between the first and last candidate. Closely spaced or sub-threshold clicks may merge or be missed; unrelated transients may be counted. Inspect and listen to the raw recording.",
  };
}

function main(args) {
  const [path, ...options] = args;
  if (!path || options.length % 2) throw new Error("Usage: node scripts/analyze-metronome.mjs recording.wav --bpm 120 [--minimum-seconds 300] [--threshold-ratio 0.15] [--refractory-ms 20]");
  const allowed = new Set(["--bpm", "--minimum-seconds", "--threshold-ratio", "--refractory-ms"]);
  const values = new Map();
  for (let index = 0; index < options.length; index += 2) {
    if (!allowed.has(options[index]) || values.has(options[index])) throw new Error("Unknown or repeated option: " + options[index]);
    values.set(options[index], Number(options[index + 1]));
  }
  const result = analyze(path, values.get("--bpm"), values.get("--minimum-seconds") ?? 300, values.get("--threshold-ratio") ?? 0.15, values.get("--refractory-ms") ?? 20);
  console.log(JSON.stringify(result, null, 2));
  return result.pass ? 0 : 1;
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  try {
    process.exitCode = main(process.argv.slice(2));
  } catch (failure) {
    console.log(JSON.stringify({ pass: false, error: failure.message }, null, 2));
    process.exitCode = 2;
  }
}
