import assert from "node:assert/strict";
import { mkdtempSync, rmSync, rmdirSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { analyze } from "./analyze-metronome.mjs";

function record(path, times, duration = 13, sampleRate = 8000, bpm = 120) {
  const bytes = Buffer.alloc(44 + Math.round(duration * sampleRate) * 2);
  bytes.write("RIFF", 0);
  bytes.writeUInt32LE(bytes.length - 8, 4);
  bytes.write("WAVEfmt ", 8);
  bytes.writeUInt32LE(16, 16);
  bytes.writeUInt16LE(1, 20);
  bytes.writeUInt16LE(1, 22);
  bytes.writeUInt32LE(sampleRate, 24);
  bytes.writeUInt32LE(sampleRate * 2, 28);
  bytes.writeUInt16LE(2, 32);
  bytes.writeUInt16LE(16, 34);
  bytes.write("data", 36);
  bytes.writeUInt32LE(bytes.length - 44, 40);
  for (const time of times) {
    const start = Math.round(time * sampleRate);
    for (let offset = 0; offset < Math.round(0.008 * sampleRate); offset++) {
      if (44 + (start + offset) * 2 < bytes.length) {
        const sample = Math.round(12000 * Math.sin(2 * Math.PI * 1000 * offset / sampleRate) * Math.exp(-offset / (0.003 * sampleRate)));
        bytes.writeInt16LE(sample, 44 + (start + offset) * 2);
      }
    }
  }
  writeFileSync(path, bytes);
  writeFileSync(path.replace(/\.wav$/, ".json"), JSON.stringify({ capture_valid: true, route_verified: true, capture_continuity_verified: true, bpm }));
}

const temporary = mkdtempSync(join(tmpdir(), "metronome-acoustic-check-"));
try {
  const path = join(temporary, "clicks.wav");
  const clean = Array.from({ length: 24 }, (_, index) => 0.5 + index * 0.5);
  record(path, clean);
  const result = analyze(path, 120, 10);
  assert.equal(result.pass, true);
  assert.equal(result.click_count, 24);
  assert.ok(result.mean_tempo_error_percent < 0.001);
  assert.ok(result.p95_absolute_interval_error_ms < 0.001);
  assert.equal(result.acceptance_duration_met, false);
  assert.equal(result.hardware_acceptance_pass, false);

  record(path, clean.map((time, index) => time + (index % 2 ? 0.008 : 0)));
  const jitter = analyze(path, 120, 10);
  assert.equal(jitter.pass, false);
  assert.ok(jitter.p95_absolute_interval_error_ms > 5);

  record(path, clean.filter((_, index) => index !== 10));
  const missing = analyze(path, 120, 10);
  assert.equal(missing.pass, false);
  assert.equal(missing.estimated_missing_clicks, 1);

  record(path, [...clean, clean[10] + 0.25].sort((left, right) => left - right));
  const extra = analyze(path, 120, 10);
  assert.equal(extra.pass, false);
  assert.equal(extra.estimated_extra_clicks, 1);

  record(path, []);
  assert.throws(() => analyze(path, 120, 10), /Insufficient/);

  record(path, clean.slice(0, 6), 4);
  const short = analyze(path, 120, 300);
  assert.equal(short.pass, false);
  assert.equal(short.checks.minimum_observed_duration, false);

  for (const bpm of [40, 240]) {
    const period = 60 / bpm;
    record(path, Array.from({ length: Math.floor(304 / period) + 1 }, (_, index) => 0.5 + index * period), 306, 8000, bpm);
    const full = analyze(path, bpm, 300);
    assert.equal(full.pass, true);
    assert.equal(full.acceptance_duration_met, true);
    assert.equal(full.hardware_acceptance_pass, true);
  }

  record(path, clean);
  writeFileSync(join(temporary, "clicks.json"), JSON.stringify({ capture_valid: true, route_verified: true, bpm: 120 }));
  const oldCapture = analyze(path, 120, 10);
  assert.equal(oldCapture.checks.capture_continuity_verified, false);
  assert.equal(oldCapture.pass, false);
  assert.equal(oldCapture.hardware_acceptance_pass, false);
  rmSync(join(temporary, "clicks.json"));
  assert.equal(analyze(path, 120, 10).pass, false);
  writeFileSync(join(temporary, "clicks.json"), JSON.stringify({ capture_valid: true, route_verified: true, capture_continuity_verified: false, bpm: 120 }));
  assert.equal(analyze(path, 120, 10).pass, false);
  writeFileSync(join(temporary, "clicks.json"), JSON.stringify({ capture_valid: false, route_verified: true, bpm: 120 }));
  assert.equal(analyze(path, 120, 10).checks.capture_metadata_valid, false);
  assert.equal(analyze(path, 120, 10).pass, false);
  console.log("PASS: clean, jitter, missing, extra, silent, short, five-minute, invalid-capture, and unverified-continuity recordings");
} finally {
  rmSync(join(temporary, "clicks.wav"), { force: true });
  rmSync(join(temporary, "clicks.json"), { force: true });
  rmdirSync(temporary);
}
