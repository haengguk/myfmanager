## Task execution and approvals

Continue requested implementation, necessary verification, and fixes for discovered
regressions within the authorized scope unless a separate explicit approval is
required. Instructions to confirm a declaration, verify a result, record a reason,
or report findings do not by themselves require asking the user for permission.
Before asking a question, check the conversation and relevant files for the answer.

Pause only work that depends on missing information or required approval. Continue
independent authorized work. Do not omit required verification or report incomplete
work as complete. These rules do not relax explicit action-time approvals, scope
restrictions, or environment permissions.

## Simulation Integration Rules

### Match-scoped state

All mutable gameplay state must be owned by the current match through
`GameState`, `TeamState`, `PlayerState`, or another explicitly match-scoped
state object.

Resolvers must remain stateless.

Do not store match state in:

- static mutable fields
- resolver-owned maps
- player-name or team-name keyed caches
- global collections that survive after a match ends

A new match must always start with fresh state.

---

### Explicit domain identity

Use structured domain values such as:

- `TeamSide`
- `Position`
- `Lane`
- player ID
- enums
- structured action data
- structured event data

Do not infer gameplay identity from:

- player or team display names
- array indexes
- event messages
- event descriptions
- formatted frontend text

Gameplay rules must continue to work when display names, event wording, or
frontend formatting change.

---

### Evaluation is not an action attempt

Resolvers must explicitly distinguish:

1. resolver evaluation
2. trigger success
3. actual action attempt
4. combat outcome
5. actual kill

Merely evaluating a resolver must not:

- consume the major-combat slot
- block a later resolver
- apply cooldown
- update action timestamps
- block FARM
- mutate pressure
- emit a gameplay summary event

Trigger success may be recorded in diagnostics, but must not emit a gameplay
summary event unless an actual action attempt begins.

Only an actual action attempt may consume the major-combat slot.

An actual attempt may consume the slot even when its outcome is `NO_KILL`,
according to the action's configured rules.

---

### Priority fallthrough

A higher-priority resolver that is evaluated but does not produce an actual
attempt must allow lower-priority eligible resolvers to be considered.

For example:

- a failed jungle-gank evaluation must allow lane combat
- a failed lane-combat evaluation must allow the next combat resolver
- an ineligible action must not block another eligible action

A resolver that begins an actual attempt may consume the major-combat slot
according to its rules, including when the outcome is `NO_KILL`.

Priority bypasses must be observable in diagnostics.

A bypassed code path must not be reported as an eligible skipped opportunity
unless eligibility was fully satisfied at that simulation time.

---

### One major combat per tick

A simulation tick may contain at most one actual major-combat attempt.

All combat systems must integrate with the central priority flow.

Examples include:

- jungle gank
- counter gank
- lane combat
- roaming combat
- skirmish
- teamfight
- objective fight
- future combat systems

Do not allow independent resolvers to create parallel major-combat attempts on
the same tick.

Eligibility and priority checks must prevent duplication before combat is
resolved.

Do not generate multiple combat attempts and then remove duplicate events
afterward.

---

### Seeded Random discipline

All gameplay randomness must use the match-provided seeded `Random`.

Do not use:

- `Math.random()`
- unseeded resolver-local `Random`
- current time as a seed
- system time as gameplay input
- unordered collection iteration as implicit randomness

Random consumption order must be deterministic and documented by resolver
execution order.

The following cases must not consume Random unless an existing documented rule
explicitly requires it:

- an ineligible action
- a duplicate call at the same simulation time
- a rejected action before trigger evaluation
- a branch replaced by a higher-priority actual action
- a combat outcome branch that is not executed
- diagnostic collection

The same seed, teams, configuration, and enabled gameplay systems must produce
the same complete timeline.

This includes the same:

- action attempts
- target selection
- participants
- outcomes
- kills and assists
- pressure changes
- economy changes
- objective results
- final winner

---

### Simulation state mutation order

A resolver must complete eligibility checks before mutating gameplay state.

An ineligible action must not:

- update cooldowns
- update action timestamps
- block FARM
- change player activity
- change pressure
- grant rewards
- register deaths
- create gameplay events
- consume Random

State mutation should begin only after an actual action attempt has been
confirmed.

Duplicate resolution of the same action must not produce additional state
changes, rewards, events, or Random consumption.

Calls that move simulation time backwards must follow the project's existing
exception policy.

---

### Duplicate protection

Duplicate protection must use structured, match-scoped identity.

It must not depend on:

- display names
- event messages
- formatted descriptions
- frontend text

Structured duplicate identity may include values such as:

- simulation time
- action type
- `TeamSide`
- `Lane`
- player ID
- objective type
- match-scoped action identity

Repeated resolution of the same structured action must be idempotent with
respect to:

- gameplay state
- rewards
- deaths
- FARM restrictions
- cooldowns
- action timestamps
- pressure changes
- event generation
- Random consumption

A duplicate call must not extend an existing restriction or cooldown unless a
new, distinct gameplay action actually occurred.

---

### Common reward and death path

All actual kills must use the existing common kill, reward, and death handling
path.

Do not duplicate or directly mutate:

- kill gold
- assist gold
- KDA
- shutdown gold
- bounty state
- death state
- respawn time
- FARM recovery time

A new combat resolver must pass the correct:

- killer
- victim
- assistants
- simulation time
- `CombatSource`

to the common resolver.

Do not directly call methods such as:

- `addKill`
- `addDeath`
- manual kill-gold mutation
- manual assist-gold mutation
- manual shutdown payment
- duplicate `markDead`

from an individual combat resolver when the common path already owns that
responsibility.

One death must never produce duplicate rewards or duplicate death processing.

---

### FARM opportunity costs

Gameplay opportunity costs must be implemented through blocked or missed
economy ticks where possible.

Do not directly subtract previously earned CS or FARM gold.

When multiple FARM restrictions overlap, the player remains unable to FARM
until the latest effective restriction expires.

The same economy tick must not be counted or charged more than once.

While FARM is blocked:

- no FARM CS is awarded
- no FARM gold is awarded
- no FARM bounty progress is awarded
- no FARM Random is consumed
- PASSIVE gold continues unless a separate global rule explicitly says
  otherwise

Missed FARM must not be restored later through catch-up CS or retroactive FARM
gold.

A gameplay action that occurs after the FARM phase of a tick must not remove
FARM that was already awarded during that tick.

SUPPORT FARM CS must remain zero unless a separate explicit design change is
requested.

---

### Structured events and combat sources

Gameplay meaning must be exposed through structured fields.

Diagnostics and frontend code must not parse `message` or `description` to
determine:

- action type
- team side
- lane
- objective
- outcome
- killer
- victim
- assistants
- combat source
- gameplay eligibility
- cooldown or FARM state

Every actual kill must produce a structured `KILL` event with the correct
`CombatSource`.

Examples include:

- `LANE_COMBAT`
- `JUNGLE_GANK`
- `COUNTER_GANK`
- `SKIRMISH`
- `TEAMFIGHT`
- `OBJECTIVE_FIGHT`
- future structured combat sources

A summary action event and its associated `KILL` and `ASSIST` events describe
the same combat.

They must not be counted as separate combat attempts.

Use:

- summary action events to count attempts and outcomes
- `KILL` events with `CombatSource` to count actual kills
- structured participant fields to count killers, victims, and assistants

A gameplay summary event must not be emitted for trigger success alone when no
actual action attempt begins.

---

### Diagnostics isolation

Diagnostics, counters, and logging must be observational only.

Enabling, disabling, or collecting diagnostic instrumentation must not change:

- gameplay state
- Random consumption
- resolver eligibility
- resolver priority
- event generation
- the resulting timeline

Diagnostic code must not become a source of gameplay state.

Do not place gameplay decisions behind conditions such as:

- `diagnosticsEnabled`
- `collectStats`
- `loggingEnabled`

This does not prohibit diagnostic experiments from running explicitly
different simulation configurations.

For example, a diagnostic may compare a feature-enabled configuration with a
feature-disabled configuration.

Within the same simulation configuration, diagnostic instrumentation must
remain fully observational.

Diagnostic counters should record structured facts produced by gameplay
execution rather than influence those facts.

---

### Additive API changes

Gameplay-phase API changes must be additive unless an explicit migration is
requested.

Do not rename, remove, or change the meaning of existing response fields during
an unrelated gameplay task.

New gameplay data should be added through:

- new nullable fields
- new structured event data
- new enum values
- new snapshot fields

Frontend code must consume structured fields and must not reconstruct gameplay
state from display text.

Preserve existing:

- timeline playback
- snapshots
- speed controls
- charts
- response structure

Avoid unrelated backend or frontend refactoring.

---

### Configuration ownership

Production tuning values must live in dedicated rule configuration classes.

Examples include:

- timing windows
- cooldowns
- probability bounds
- combat weights
- pressure shocks
- FARM block durations
- participant weights
- target-selection weights

Do not hardcode tuning numbers across resolvers.

Do not duplicate the same tuning constant in multiple classes.

Do not place phase-specific numeric values in `AGENTS.md`.

`AGENTS.md` should contain stable architectural rules, not temporary balance
values.

Do not automatically tune production constants merely to make diagnostics
match a desired result.

Implement the requested initial values, run diagnostics, and report the actual
result before proposing balance changes.

---

### Verification requirements

New gameplay systems must include:

- focused unit tests
- boundary-value tests
- duplicate-call tests
- ineligible-action Random-consumption tests
- integration tests for resolver priority and fallthrough
- participant and reward integrity tests
- same-seed reproducibility tests
- structured diagnostic output
- one planned full-regression pass for production/runtime-sensitive changes,
  with subsequent fixes verified under the Full regression budget below

Diagnostics must distinguish:

- resolver evaluations
- trigger successes
- actual attempts
- combat outcomes
- actual kills by `CombatSource`
- paths bypassed
- eligible opportunities actually skipped

A bypassed code path must not be reported as an eligible action skipped unless
the action was genuinely eligible at that simulation time.

Tests must verify that summary events and associated `KILL` events are not
counted as multiple combat attempts.

Existing expected results must not be overwritten until intended behavior has
been separated from a regression.

When existing values change, determine whether the cause is:

- intended new gameplay behavior
- changed Random consumption
- changed eligibility
- changed resolver priority
- duplicate state mutation
- event-classification error
- an unrelated regression

---

### Test execution boundaries

Focused correctness tests must remain fast, deterministic, and independent of
large statistical samples.

Normal unit and integration tests should verify:

- formulas
- state transitions
- timing boundaries
- participant eligibility
- reward handling
- duplicate protection
- Random-consumption rules
- resolver fallthrough
- same-seed reproducibility

Large-seed distribution checks and balance diagnostics must be kept in
separate diagnostic classes and execution tasks.

Examples include:

- thousands of simulated matches
- win-rate distribution
- target-lane distribution
- average CS and gold
- P90 or P95 match duration
- mirror-scenario analysis
- balance comparisons between enabled and disabled features

Large statistical diagnostics must not be added to normal unit or integration
test execution unless explicitly requested.

Full-population and statistical audits must be excluded from the default test
task and run only through an explicit diagnostic task. Generated reports are
diagnostic outputs, not correctness-test inputs or sources of truth.

A balance observation should not be expressed as a brittle unit-test assertion
unless it represents a true deterministic invariant.

---

### Full regression budget

The complete backend Gradle `test` task is expensive. A focused `--tests`
invocation is not a full regression. The default for work requiring backend
regression coverage is one planned full-suite pass, followed by scoped
verification of any fixes. A second full run is an exception, not the next
automatic step after a failure or a production-code edit.

During implementation:

- use focused tests for changed behavior and directly affected invariants
- do not run the full backend regression after every intermediate change
- finish the planned implementation, runtime wiring, and focused checks before
  the planned full pass when the change requires one
- retain results from that full pass when investigating failures; resuming the
  same task does not reset the execution budget

After a full pass, whether it passed or failed:

1. classify all observed failures and group fixes by their actual cause
2. fix authorized regressions without weakening assertions to hide defects
3. run the failed tests and the directly affected tests for each fix, including
   affected callers or shared contracts where needed
4. when those checks pass and no wider verification gap remains, finish the
   task without another full run, even if the fix changed production code

Choose the scope from the affected behavior and dependencies, not the number
of failed tests or the file extension. A local production/API/resource fix
does not by itself require another full run. Assertion-only changes and
isolated test-local fixture corrections need only their affected tests;
documentation, comments, formatting, and report wording need no full run.

Repeat the full suite only when focused tests and a bounded set of affected
suites cannot establish correctness, or the user explicitly requires a new
full run. Possible reasons include a demonstrated suite-order interaction,
shared global-state or Random behavior affecting independent subsystems, or
a build/runtime/shared-fixture change whose consumers cannot be covered by a
bounded selection. These are reasons to assess scope, not automatic rerun
triggers. A change touching transactions, persistence, a migration, or an API
alone is not enough.

Before every repeat, including the second run, briefly record:

- the changed shared behavior or observed interaction
- why the selected focused/affected suites cannot cover it
- what the additional full run will establish

"One test failed", "production code changed", "the final tree differs", and
"for reassurance" are not sufficient reasons. Recording a concrete reason
is not an approval request; continue necessary authorized verification.

Report the evidence accurately:

- distinguish the original full result from subsequent focused results
- if the full pass failed and fixes passed focused verification, state both
  results and the reason another full run was unnecessary
- do not claim a clean full-suite pass on the final tree when none occurred
- preserve the original full-run summary before focused runs overwrite test
  outputs; use existing logs/reporting rather than a new audit framework

Do not skip unresolved failures, required behavioral checks, or an explicitly
requested full run to save time. Do not change Gradle exclusions, disable
tests, or reduce assertions merely to avoid a rerun. Existing explicit
artifact-acceptance requirements for a clean full result on a particular
input remain applicable only when that acceptance work is actually in scope.

If a full regression is aborted by an environment or tooling failure, do not
immediately repeat the unchanged command. Diagnose or fix the external failure
first, preserve completed evidence, and identify the unverified coverage.
Resume the smallest sufficient remaining scope; repeat the full run only when
that remaining coverage cannot be established otherwise. Never report an
aborted run as complete.

Documentation-only changes must never trigger a full backend regression.
This section replaces blanket rerun requirements in older repository prompts
or verification guidance. Preserve separate explicit user requirements and
action-time approvals.
