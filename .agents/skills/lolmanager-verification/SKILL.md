---
name: lolmanager-verification
description: Route LoL Manager changes to the smallest sufficient tests while keeping new test code, harnesses, and execution cost proportional to actual risk. Use for implementation prompts, test-design decisions, verification scope, and code review; do not use for documentation-only work or ordinary explanation.
---

# LoL Manager Verification

Select the smallest check that can establish the requested claim. The applicable
`AGENTS.md` remains authoritative for simulation invariants and the full-regression
budget; do not duplicate its rules here.

Test code is maintained product code. Minimize both the tests executed and the new
test infrastructure written. Do not turn an ordinary feature task into release
acceptance, an audit package, or a proof system unless its actual risk requires one.

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

## Test-code budget

Use these as default budgets, not correctness-avoiding hard caps. Exceed them only
for a concrete, distinct failure risk and state that reason before adding the extra
test layer or file.

| Changed surface | Default new test-code budget |
| --- | --- |
| Isolated frontend screen or read-only view | Prefer one existing/new verifier file with roughly 5-8 meaningful scenarios |
| Read-only API or immutable catalog | Prefer extending existing tests or at most 2-3 focused classes covering the main success path, one integrity boundary, and representative errors |
| Small domain rule or bug fix | Extend the nearest existing class; use parameterized cases instead of creating a new class per boundary |
| Match gameplay, seeded Random, durable concurrency, receipt/commit identity | Risk-driven; add every test needed for the applicable `AGENTS.md` invariants, but still reuse fixtures and avoid duplicated evidence |

For ordinary work:

- extend the closest existing test before creating another test class;
- test externally meaningful behavior, not every private branch, DTO accessor, or
  implementation step;
- do not mirror every production class with a test class merely for symmetry;
- do not test all 50 players, all 10 teams, every nullable field, or every viewport
  when a representative case plus structural/count invariants proves the contract;
- prefer one parameterized boundary test over many near-identical methods;
- keep test helpers small and shared only when they remove real repetition;
- do not build a generic framework in test code for one feature.

The budget does not waive a required final full regression. It limits new test
implementation and duplicate evidence; `AGENTS.md` still decides when the existing
complete suite must run.

## Evidence deduplication

Prove one claim at the cheapest layer that can actually establish it.

- Use unit tests for formulas, pure validation, ordering, and state transitions.
- Use one integration test for component wiring, serialization, or transaction
  behavior that a unit test cannot establish.
- Use browser E2E for a user flow, focus/layout behavior, or real transport—not to
  reassert every DTO field already covered by API tests.
- Do not repeat the same success and error matrix in unit, controller, LIVE HTTP,
  browser, and artifact tests.
- Do not test standard Spring, Jackson, React, TypeScript, gzip, or browser behavior
  unless project code customizes that behavior or a demonstrated regression exists.
- A shared navigation edit normally needs the feature build and one navigation
  smoke, not every unrelated feature verifier.

Create a custom harness, artifact writer, SHA manifest, fresh-JVM probe, large seed
runner, or long-running E2E only when the requested acceptance claim specifically
depends on artifact authenticity, cross-process determinism, statistical evidence,
crash recovery, or a long lifecycle. Ordinary CRUD, reference data, and UI work must
not acquire these by default.

Do not create a separate hardening milestone merely to add exhaustive tests to an
otherwise complete ordinary feature. Include the small number of high-value failure
boundaries in the implementation task and move genuine release/load/security work to
an explicitly requested release gate.

## Verification router

| Changed surface | Default evidence | Full backend regression |
| --- | --- | --- |
| Documentation or report wording only | None; inspect the diff | Never |
| Isolated test, test-side parser, or artifact consumer | Narrow focused test; reproduce canonical output when that is the claim | No, unless it also changes shared fixtures, Gradle, global state, or suite order |
| Backend production Java, API contract, runtime wiring, or production resource | Focused behavioral tests for the changed path and directly affected invariants | One final run after the production tree is complete |
| Random order, determinism, global/static state, shared fixture, or Gradle/test configuration | Focused determinism and affected contract tests | Required on the final tree |
| Frontend source | `npm run build`; add browser verification only when the requested user flow needs it | No backend full run unless backend/runtime also changed |
| Diagnostic harness or generated artifact | Focused contract/smoke; add input binding and manifest checks only when the requested acceptance contract requires them | No by default; apply the executable surface rules above |
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

## Implementation-prompt rules

When writing a prompt for another Codex task, prescribe the minimum required checks
instead of listing every repository verifier "for safety."

- Name the one or two claims the tests must establish.
- Give a default test-code budget and make extra test files conditional on a stated
  uncovered risk.
- Make adjacent suites conditional on the files or shared contracts actually
  changed; do not require them merely because the feature lives in the same app.
- For frontend-only work, normally request the feature-focused check and production
  build. Add a representative browser flow only when the requested user flow needs
  browser evidence under the verification router. Add a second viewport only when
  responsive layout changed and a failure boundary only when state recovery or
  stale-response behavior is part of the feature. Browser-tool skills implement
  the selected checks; they do not automatically expand verification scope or waive
  a product-specific requirement for an explicit browser-testing request.
- For a read-only API, normally cover one representative detail, population/count or
  ordering invariants, and representative 4xx/5xx handling. Do not generate an
  audit artifact or test every subject independently.
- Explicitly forbid large diagnostics, population runs, cross-JVM probes, artifact
  generation, and long-running E2E unless they are necessary to the task's stated
  acceptance claim.
- Keep final reporting proportional. A short feature does not need a regulatory-style
  proof report or hundreds of lines of verification documentation.

If compilation or an existing focused test already proves a claim, do not add a new
test whose only purpose is to restate it. If an agent wants to exceed the prescribed
budget, it should first record the concrete regression that the additional test can
catch and why the existing layer cannot catch it.

Start backend focused checks from `backend/` with the narrowest stable selector:

```bash
./gradlew test --tests 'fully.qualified.TestClass' --console=plain --no-daemon
```

Before using a named diagnostic task, confirm its current declaration and exclusions
in `backend/build.gradle`. Never infer a task from an old report and never sweep all
diagnostics.

## Artifact-bound finalization

Apply this workflow only when the requested acceptance contract depends on artifact
provenance binding, an immutable baseline, deterministic regeneration, or official
promotion. An example response, build output, screenshot, or handoff report alone
does not trigger it. Preserve the integrity requirements of official baselines.

1. Define the artifact's semantic acceptance checks, generation command, expected
   output, and execution budget before the first full regression.
2. Before the final full regression, generate an explicitly unverified candidate
   under `build/reports`, or exercise the same serializer and projector through a
   focused in-memory path. Candidate generation must not overwrite or promote the
   official artifact.
3. Audit the candidate's relevant cross-field and domain invariants. Inspect the
   complete candidate and batch related findings before changing production code;
   do not discover one artifact defect per full-regression cycle.
4. Fix the production tree, run the affected focused checks, and freeze production,
   resource, runtime, shared-fixture, and build inputs before the final full run.
5. After a clean full regression, only bind, promote, or deterministically regenerate
   the official artifact and verify its focused acceptance checks and manifest. Do
   not reopen exploratory production analysis that could have run on the candidate.
   Apply the `AGENTS.md` post-pass change rules to promotion and regeneration too:
   if they change production resources or runtime inputs, run the required full
   regression again. Do not classify a runtime artifact as a mere report to reuse
   a result from before those inputs changed.
6. Keep full-regression reuse identity distinct from focused artifact-acceptance
   evidence. An assertion-only or isolated acceptance-test change that `AGENTS.md`
   permits after a clean full run must not force another full run merely to refresh a
   combined source hash. If current tooling couples those identities, report or fix
   that workflow rather than rerunning the unchanged full suite for reassurance.
7. If post-full artifact inspection reveals a production defect, inspect the rest of
   the artifact first, apply the smallest batched causal fix, rerun focused evidence,
   and then apply the `AGENTS.md` rule for any required final full regression.

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
