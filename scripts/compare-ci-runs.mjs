#!/usr/bin/env node
import { execFileSync } from "node:child_process";
import { mkdirSync, writeFileSync } from "node:fs";
import { dirname } from "node:path";

const usage = "node scripts/compare-ci-runs.mjs --before RUN[/ATTEMPT],... --after RUN[/ATTEMPT],... [--repo OWNER/REPO] [--output build/ci-comparison.json]";
const options = {};
const args = process.argv.slice(2);
for (let index = 0; index < args.length; index += 2) {
  const key = args[index];
  if (!["--before", "--after", "--repo", "--output"].includes(key) || !args[index + 1] || options[key]) throw new Error(usage);
  options[key] = args[index + 1];
}
if (!options["--before"] || !options["--after"]) throw new Error(usage);
const gh = (...args) => execFileSync("gh", args, { encoding: "utf8", maxBuffer: 16 * 1024 * 1024 });
const repository = options["--repo"] ?? gh("repo", "view", "--json", "nameWithOwner", "--jq", ".nameWithOwner").trim();
if (!/^[\w.-]+\/[\w.-]+$/.test(repository)) throw new Error("Expected OWNER/REPO");
const api = path => JSON.parse(gh("api", path));

function seconds(start, end) {
  const duration = (Date.parse(end) - Date.parse(start)) / 1000;
  if (!Number.isFinite(duration) || duration < 0) throw new Error(`Invalid interval ${start} to ${end}`);
  return duration;
}

function sample(reference) {
  const match = /^(\d+)(?:\/([1-9]\d*))?$/.exec(reference);
  if (!match) throw new Error(`Invalid run reference ${reference}`);
  const base = `repos/${repository}/actions/runs/${match[1]}`;
  const attempt = match[2] ? Number(match[2]) : api(base).run_attempt;
  const run = api(`${base}/attempts/${attempt}`);
  const { jobs, total_count } = api(`${base}/attempts/${attempt}/jobs?per_page=100`);
  if (String(run.id) !== match[1] || run.run_attempt !== attempt || run.status !== "completed" || run.conclusion !== "success") {
    throw new Error(`Run ${reference} is not a completed successful attempt`);
  }
  const graph = jobs.map(job => job.name).sort().join(",");
  if (jobs.length !== total_count || !["verify", "connected,static,verify"].includes(graph)) throw new Error(`Run ${reference} has an incomplete or unexpected job graph`);
  const timings = jobs.map(job => {
    if (job.run_id !== run.id || job.run_attempt !== attempt || job.status !== "completed" || job.conclusion !== "success") {
      throw new Error(`Job ${job.name} is not successful in attempt ${attempt}`);
    }
    return {
      id: job.id, name: job.name, conclusion: job.conclusion, labels: job.labels,
      startedAt: job.started_at, completedAt: job.completed_at, seconds: seconds(job.started_at, job.completed_at),
      steps: job.steps.map(step => ({
        name: step.name, conclusion: step.conclusion, startedAt: step.started_at, completedAt: step.completed_at,
        seconds: step.conclusion === "skipped" ? null : seconds(step.started_at, step.completed_at),
      })),
    };
  });
  const completedAt = timings.reduce((latest, job) => job.completedAt > latest ? job.completedAt : latest, timings[0].completedAt);
  return {
    runId: run.id, attempt, commit: run.head_sha, event: run.event, conclusion: run.conclusion,
    workflowId: run.workflow_id, path: run.path, url: run.html_url,
    startedAt: run.run_started_at, completedAt, elapsedSeconds: seconds(run.run_started_at, completedAt),
    jobs: timings, raw: { run, jobs },
  };
}

const before = options["--before"].split(",").map(sample);
const after = options["--after"].split(",").map(sample);
for (const samples of [before, after]) {
  if (new Set(samples.map(sample => `${sample.runId}/${sample.attempt}`)).size !== samples.length) throw new Error("Duplicate run attempt");
}
for (const current of [...before, ...after]) {
  if (current.workflowId !== before[0].workflowId || current.path !== before[0].path) throw new Error("Samples must use the same workflow");
}

function median(samples) {
  const durations = samples.map(sample => sample.elapsedSeconds).sort((left, right) => left - right);
  const middle = Math.floor(durations.length / 2);
  return durations.length % 2 ? durations[middle] : (durations[middle - 1] + durations[middle]) / 2;
}

const beforeMedianSeconds = median(before);
const afterMedianSeconds = median(after);
const deltaSeconds = afterMedianSeconds - beforeMedianSeconds;
const summary = { beforeMedianSeconds, afterMedianSeconds, deltaSeconds, deltaPercent: deltaSeconds / beforeMedianSeconds * 100 };
const output = options["--output"] ?? "build/ci-comparison.json";
mkdirSync(dirname(output), { recursive: true });
writeFileSync(output, JSON.stringify({ repository, elapsedDefinition: "attempt run_started_at through latest job completed_at", summary, before, after }, null, 2) + "\n");
for (const [group, samples] of [["before", before], ["after", after]]) {
  for (const current of samples) {
    console.log(`${group} ${current.runId}/${current.attempt} ${current.event} ${current.commit} ${current.elapsedSeconds}s`);
    for (const job of current.jobs) {
      console.log(`  ${job.name} ${job.seconds}s`);
      for (const step of job.steps) console.log(`    ${step.name} ${step.seconds ?? "skipped"}s`);
    }
  }
}
console.log(`Median ${beforeMedianSeconds}s -> ${afterMedianSeconds}s; delta ${deltaSeconds}s (${summary.deltaPercent.toFixed(1)}%). Evidence ${output}`);
