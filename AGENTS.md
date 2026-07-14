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
- regression execution for existing systems

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

A balance observation should not be expressed as a brittle unit-test assertion
unless it represents a true deterministic invariant.