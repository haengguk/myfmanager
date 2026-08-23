---
name: lolmanager-verification
description: Route LoL Manager implementation or code-review changes to the smallest sufficient focused, build, diagnostic, or full-regression checks. Use when a verification-scope decision is needed; do not use for documentation-only work or ordinary explanation.
---

# LoL Manager Verification

Select the smallest check that can establish the requested claim. The applicable
`AGENTS.md` remains authoritative for simulation invariants and the full-regression
budget; do not duplicate its rules here.

## Scope first

1. Distinguish implementation, review, diagnosis, and explicit test execution.
   A read-only review does not automatically authorize expensive diagnostics.
2. Inspect the requested diff and `git status --short`. Separate task changes from
   pre-existing user changes and preserve unrelated files.
3. Classify the changed surface using the routing table below.
4. Read only the context needed for that route:
   - affected production and test code for all executable changes;
   - `docs/development/testing.md` when choosing or interpreting project test tasks;
   - the relevant `backend/build.gradle` block before invoking a custom diagnostic;
   - frontend scripts before selecting a frontend command.

## Verification router

| Changed surface | Default evidence | Full backend regression |
| --- | --- | --- |
| Documentation or report wording only | None; inspect the diff | Never |
| Isolated test, test-side parser, or artifact consumer | Narrow focused test; reproduce canonical output when that is the claim | No, unless it also changes shared fixtures, Gradle, global state, or suite order |
| Backend production Java, API contract, runtime wiring, or production resource | Focused behavioral tests for the changed path and directly affected invariants | One final run after the production tree is complete |
| Random order, determinism, global/static state, shared fixture, or Gradle/test configuration | Focused determinism and affected contract tests | Required on the final tree |
| Frontend source | `npm run build`; add browser verification only when the requested user flow needs it | No backend full run unless backend/runtime also changed |
| Diagnostic harness or generated artifact | Focused contract/smoke plus input binding and manifest checks | No by default; apply the executable surface rules above |
| Calibration, holdout, distribution, or full-population audit | Do not run unless explicitly requested or correctness cannot establish the claim | Not a substitute for the default backend regression |

## Project-specific selection

- Match-engine gameplay changes: select the changed resolver/system tests and the
  affected boundary, duplicate, Random-consumption, priority/fallthrough,
  participant/reward, structured-event, and same-seed checks. Include only concerns
  the change can affect.
- Runtime profile, provenance, or authored resource changes: include configuration
  identity, resource/provenance, same-seed, and applicable immutable-baseline parity
  checks before the final full regression.
- B1/B2/B3 or later audit harness changes: run the focused contract or smoke route.
  Never reopen an already consumed official holdout merely to confirm a report.
- API changes: verify the affected backend contract and preserve existing fields;
  run frontend build verification when frontend consumers changed.

Start backend focused checks from `backend/` with the narrowest stable selector:

```bash
./gradlew test --tests 'fully.qualified.TestClass' --console=plain --no-daemon
```

Before using a named diagnostic task, confirm its current declaration and exclusions
in `backend/build.gradle`. Never infer a task from an old report and never sweep all
diagnostics.

## Full-regression decision

Apply the `AGENTS.md` full-regression budget exactly. In particular:

- finish production/resource/runtime/build changes before the final full run;
- reuse a clean full result after documentation, report wording, assertion-only, or
  isolated test-local changes when `AGENTS.md` permits it;
- require a new full run after post-pass production, resource, runtime, shared-fixture,
  Gradle, global-state, Random-order, or suite-order changes;
- do not describe a focused `--tests` invocation as a full regression.

## Failures and artifacts

- Classify a failure before editing expectations: intended behavior, product
  regression, Random-order or eligibility change, duplicate mutation, event
  classification, stale expectation, or environment/tooling failure.
- Diagnose an environment failure before repeating the unchanged command.
- Treat generated reports as evidence from one run, not production source or a
  correctness-test oracle. Prefer manifest verification or artifact-only
  regeneration over repeating expensive simulations.

## Report

State what was actually established:

- focused/build/diagnostic commands and pass/fail results;
- artifact and manifest scope when applicable;
- full-regression run, reuse, or omission and the applicable rule;
- skipped or unresolved checks with a concrete reason.

Compilation alone is insufficient when the requested claim is behavioral.
