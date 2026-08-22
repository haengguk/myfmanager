---
name: lolmanager-verification
description: Select and run proportionate verification for LoL Manager code, resource, runtime, and test changes. Use when implementing or reviewing changes that need focused tests, dedicated diagnostics, frontend build checks, or a backend full-regression decision; do not use for documentation-only edits.
---

# LoL Manager Verification

Produce the smallest sufficient verification evidence for the requested change.
Keep the repository's applicable `AGENTS.md` authoritative; do not restate or
override its simulation invariants or full-regression budget.

## Establish the scope

1. Confirm whether the user asked for implementation, diagnosis, review, or
   test execution. Do not expand a read-only request into broad or expensive
   diagnostic execution.
2. Inspect the relevant working-tree changes and distinguish task changes from
   unrelated pre-existing user changes. Preserve unrelated files.
3. Read `docs/development/testing.md` and the affected production and test code.
   Inspect the current `backend/build.gradle` declaration before selecting a
   custom diagnostic task; do not rely on a task name or old report alone.
4. Classify the affected surface: backend production Java, production resource,
   runtime wiring or API contract, frontend, shared test infrastructure,
   isolated test code, or documentation/report only.

## Choose checks

- Start with deterministic focused tests for the changed behavior and its
  directly affected invariants. Run them from `backend/` with a narrow
  `./gradlew test --tests 'fully.qualified.TestClass'` selector.
- Include boundary, duplicate-call, Random-consumption, priority/fallthrough,
  participant/reward, structured-event, and same-seed checks only when the
  changed behavior touches those concerns.
- For frontend source changes, run `npm run build` from `frontend/`. Do not
  invent a frontend test command when no test script exists.
- Treat the default backend `test` task as correctness regression, not as a
  balance or full-population audit. It excludes diagnostic-tagged tests.
- Run a large-seed, distribution, calibration, holdout, full-population, or
  artifact-producing diagnostic only when the user explicitly requests that
  evidence or focused correctness tests cannot establish the requested claim.
  Select only the relevant declared Gradle task; never sweep all diagnostics.
- Apply the `Full regression budget` section of `AGENTS.md` exactly. Finish
  production and runtime changes first, run at most the necessary final full
  regressions, and record why each full run was required. Documentation-only
  changes never trigger a backend full regression.

## Handle outcomes

- When a focused test fails, determine whether the cause is intended behavior,
  a product regression, changed Random order or eligibility, duplicate state
  mutation, event classification, stale expectation, or an environment/tooling
  failure before changing code or expected values.
- If an environment or tooling failure aborts a command, diagnose it before
  repeating the unchanged command.
- Treat generated reports and build artifacts as evidence from a particular
  run, never as production source of truth or correctness-test input.
- Reuse a clean full-regression result when later changes fall into an allowed
  post-pass category. Do not rerun merely to refresh report wording.

## Report

Summarize verification with exact commands and outcomes:

- focused checks run and their pass/fail status
- frontend build status when applicable
- diagnostic task and artifact scope, or why no diagnostic was needed
- full-regression status, run count, and the rule supporting run or reuse
- unresolved failures, skipped checks, and the concrete reason for each

Do not claim success from compilation alone when behavioral tests are required,
and do not claim a full regression from a focused `--tests` invocation.
