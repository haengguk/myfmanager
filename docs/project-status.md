# Project Status

이 문서는 2026-08-22 working tree의 production source, active resources, 최종 backend regression과 직접 생성한 structured diagnostic을 기준으로 한 현재 snapshot이다. 과거 build output이나 현재 HEAD보다 앞선 report는 baseline으로 간주하지 않는다.

## Current Production Snapshot

### Champion resources

| 항목 | 현재 값 |
| --- | --- |
| Active manifest | `full-173-resource-set-2026-08-v2` |
| Active manifest raw SHA-256 | `7c2ba352e4fd5cb2e849504ebf644479aa1b367e250b46ceac5f089922169721` |
| Champion pool | `full-173-2026-08-v1` |
| Champions | 173 |
| Legal `ChampionRoleKey` | 216 |
| Role counts | TOP 54 / JUNGLE 51 / MID 45 / ADC 31 / SUPPORT 35 |
| Flex champions | 36 |
| Power | 173 profiles, `full-173-power-2026-08-v1` |
| Matchup | 216 materialized roles, `full-173-role-matchup-profile-2026-08-v2` |
| Composition | 216 materialized roles, `full-173-composition-profile-2026-08-v2` |
| Composition full-profile hash | `23d616cab6abea69d5ad783f405b0b4518a14608b0be4eac3d53f669acab6877` |
| Jungle Clear | 51 JUNGLE profiles, `full-173-jungle-clear-economy-2026-08-v1` |
| Jungle Clear gameplay enabled | 51 in active resource; runtime contribution is profile-controlled |
| Jungle Clear raw SHA-256 | `daf3757c6598056c9d01c3fcb3899e2b6dd970ffbda94f7b755f9d40cd2e967b` |

Champion Power resource에는 8 level curves와 8 item curves가 있다. Cross-catalog completeness는 `ChampionResourceSet` 생성 시 exact set equality로 검증된다. Jungle V1-A는 historical disabled clear file을 변경하지 않고 새 versioned economy resource와 manifest V2를 추가했다. V1-B는 이 authored data를 다시 수정하지 않고 V1-A의 successful `JungleEconomyOutcome`만 bounded gank-tempo credit input으로 사용한다.

### Player identity, ratings, and proficiency

| 항목 | 현재 값 |
| --- | --- |
| Player Identity version | `lck-player-identities-2026-08-21-v1` |
| Player Identity snapshotAt | `2026-08-21` |
| Player Identity SHA-256 | `badbbaa3ae7fbe5eaaf83ee8e97a93134476493a45167ec3d1637c7243909018` |
| Stable identities | 50 unique `PlayerId`, 50 unique `PlayerRatingKey` |
| Player Ratings version | `lck-player-ratings-2026-08-19-v1` |
| Player Ratings SHA-256 | `2312a8bc7d222fd63b57d1255210fb25104432a90a954d854b2090cc2acb28e0` |
| Authored rating values | 50 players × 12 = 600, unchanged |
| Proficiency version | `lck-champion-proficiency-2026-08-21-v1` |
| Proficiency SHA-256 | `2c36b8a109aba9dfe84c1da319fe02708a72a1341d334dc6d5e3f605b0023aad` |
| Authored proficiency overrides | 732 sparse entries |
| Potential / fallback keys | 2,160 / 1,428 |
| Score distribution | 15=35 / 16=160 / 17=228 / 18=210 / 19=81 / 20=18 |
| High / elite / benchmark | 17+=537 / 19+=99 / 20=18 |
| Scope-inexpressible review evidence | 11, legal-role expansion 0 |

`PlayerId`는 사람, `PlayerRatingKey(teamCode, Position)`는 current roster snapshot, `PlayerKey(TeamSide, Position)`는 match slot identity다. nickname은 display metadata이며 gameplay lookup key가 아니다.

`LckTeamAssembler`는 같은 identity instance를 사용하는 ratings와, 같은 ratings/champion instances를 사용하는 proficiency catalog를 하나의 immutable graph로 결합해 10개 팀 × 정확히 5명의 real LCK roster를 deterministic하게 조립한다. 각 player는 explicit stable ID, 실제 12 ratings, 실제 sparse proficiency profile을 가진다. 작성되지 않은 legal same-position key만 neutral 14이며 unknown/illegal/cross-position subject는 fail-fast한다.

Identity/proficiency companion JSON은 exact SHA의 local runnable working tree에는 존재하지만 의도적으로 untracked다. 기준 commit만으로는 이 runtime data가 포함되지 않으며, 이 상태를 authored resource 결함으로 분류하지 않는다.

### Draft and reachability

| 항목 | 현재 값 |
| --- | --- |
| Draft Meta | `draft-meta-full-173-216-role-2026-08-18-v3` |
| Draft Meta role profiles | 216 |
| Rule sequence | professional 5 bans + 5 picks per team, 20 turns |
| Series rule | completed picks를 양 팀 공통 Hard Fearless exclusion으로 누적 |
| Determinism | Random/time input 없음; stable lexical tie-break |
| API/frontend exposure | 없음 |

보정된 `RealProficiencyCandidateReachabilityGate`를 real `DraftTeamContext`와 실제 17+ proficiency 537개에 key당 3개의 bounded scenario로 실행했다. ChampionId presence와 target-position-fixed `ChampionRoleKey` completion을 별도 metric으로 기록한다.

| Reachability metric | 값 |
| --- | ---: |
| Bounded scenarios | 1,611 |
| Champion-level legal scenarios | 1,349 |
| Role-specific legal scenarios | 1,319 |
| Champion appearances | 261 |
| Champion-present high keys | 150 / 537 (0.2793296089) |
| Role-key reachable scenarios | 260 |
| Role-key reachable high keys | 150 / 537 (0.2793296089) |
| Champion present / target role infeasible | 1 (`player-kiin/varus:TOP`) |
| Champion present / role completion impossible | 0 |
| Role-key unreachable high keys | 387 |
| No role-specific legal-scenario keys | 0 |

387건은 blocker가 아니라 `REVIEW_REAL_PROFICIENCY_ROLE_KEY_UNREACHABLE` review signal이다. Key count는 이 schedule에서 양쪽 모두 150이지만 scenario-level champion presence 261 중 1건은 target role이 불가능해 role-key reachability가 260이다. 이 Gate는 bounded feasibility를 증명할 뿐 proficiency가 shortlist inclusion의 원인임을 증명하지 않는다. Draft weight, candidate generator, shortlist size, search bound, Draft Meta와 proficiency 값은 변경하지 않았다.

전체 population은 기본 `test`에서 제외되며 `phase13gRealProficiencyAudit` 전용 task에서만 실행한다. 이번 명시적 실행은 1 suite / 1 test, failures/errors/skipped 0, JUnit 7.444초, Gradle 약 11초였다.

Generated report:

- `backend/build/reports/phase13g-real-proficiency-reachability/phase13g-real-proficiency-reachability-summary.json`
- `backend/build/reports/phase13g-real-proficiency-reachability/phase13g-real-proficiency-reachability-keys.csv`
- `backend/build/reports/phase13g-real-proficiency-reachability/phase13g-real-proficiency-reachability-SHA256SUMS.txt`

Report SHA-256: summary `4f3a13ca19cb00abc8da463173578b222fa55e389bb34d1a96abe6f2b10bfde3`, key CSV `936346bc52dfbfab430634d1fcb44781bb765c82f3558f60565e1c36541b1f61`.

### Real Draft→Match backend application flow

`RealDraftMatchOrchestrator`는 Spring application component로서 다음 deterministic 경계를 연결한다.

```text
explicit LCK team codes
  → LckTeamAssembler real Teams
  → DraftTeamContext
  → DraftEngine / FinalDraftResult
  → FinalDraftResult.matchChampionAssignments()
  → same real Teams + seeded MatchSimulator
  → MatchTimeline
```

`FinalDraftResult`의 final role assignment가 match champion assignment의 유일한 source다. Application preflight는 explicit team code 기반 current `PlayerRatingKey`/`PlayerId`, 정확한 five-position roster, Draft context identity/proficiency, legal `ChampionRoleKey`, final-role/assignment equality, match-wide duplicate stable ID, caller-owned Hard Fearless exclusions를 검증한다. Team/player display name과 array index는 identity로 사용하지 않는다.

Series overload는 호출자가 소유한 `SeriesDraftHistory`만 변경한다. Draft와 Match가 모두 성공한 뒤 양 팀 completed picks를 commit하고, 다음 game은 그 exact set을 exclusion으로 받는다. Static/global series state와 BO3/BO5 scheduling은 추가하지 않았다.

GEN 대 T1 representative flow는 game 1과 Hard Fearless game 2, fresh-history replay를 실행해 flex final role mapping, 실제 LCK `PlayerId`의 KILL/assist participant 보존, complete timeline replay를 검증했다. 이 backend component는 아직 REST/frontend에 노출되지 않는다.

### Explicit simulation runtime profiles and provenance

`RealDraftMatchOrchestrator`의 기존 overload는 이제 `BASELINE_V1`을 resolve하며, additive overload는 다음 closed-set profile ID를 받는다. `ConfiguredMatchSimulatorFactory`의 유일한 public `create` 경계도 profile ID와 instrumentation만 받아 registry를 다시 resolve한다. Caller가 직접 만든 `ResolvedSimulationRuntimeProfile`은 provenance 경계에서 exact registry membership을 재검증하므로 임의 gameplay boolean 또는 internal audit mode를 application runtime에 주입할 수 없다.

| Profile | 공통 gameplay | Matchup | Composition | Jungle Clear | Configuration hash |
| --- | --- | --- | --- | --- | --- |
| `BASELINE_V1` | 전부 현재 Spring snapshot과 동일하게 ON | `OFF` | `OFF` | `DISABLED_NOT_INTEGRATED` | `c8cc557bd721228c473e30d31b7258510f9608a18098578bc1da36e603536215` |
| `MATCHUP_ONLY_CANDIDATE_V1` | 동일 | `GEOMETRIC_V2` | `OFF` | `DISABLED_NOT_INTEGRATED` | `58714464c19a2cffd108d47a93a0909126513c8bb10cb0e19bbd87f8e78532ec` |
| `FULL_SYSTEM_CANDIDATE_V1` | 동일 | `GEOMETRIC_V2` | `PRODUCTION_V2` | `DISABLED_NOT_INTEGRATED` | `caaf76274dc148040b0a95eae1ed5181790b2fc840f45af9b109ea7951c1fd5d` |
| `FULL_SYSTEM_WITH_JUNGLE_ECONOMY_CANDIDATE_V1` | 동일 | `GEOMETRIC_V2` | `PRODUCTION_V2` | `ECONOMY_V1` | `e04869bca5281f7f416c8191d7bf1b5be04b3129f33f6dfd4de83e8d8e92743b` |
| `FULL_SYSTEM_WITH_JUNGLE_TEMPO_CANDIDATE_V1` | 동일 | `GEOMETRIC_V2` | `PRODUCTION_V2` | `ECONOMY_AND_GANK_TEMPO_V1` | `c835280cbaa1244f4fecb099b19f71111c6d77aa1aeb1b7110a6e86e6381451c` |

공통 ON snapshot은 Lane Combat/FARM Recovery/Jungle Gank/Counter Gank/Roam/Objective Priority/Lane Phase/Mid Game Macro/Objective Decision/Late Game Macro/Progression/Progression Power/Champion Power 전부다. Jungle Clear contribution도 canonical configuration field다. Economy candidate는 `ECONOMY_V1`, Tempo candidate는 `ECONOMY_AND_GANK_TEMPO_V1`이며 별도 `SimulationOptions` boolean은 추가하지 않았다.

`configurationHash`는 이 field-complete gameplay configuration만 고정한다. Diagnostics ON/OFF는 별도 `SimulationInstrumentation`이며 configuration/replay hash에서 제외되고 complete timeline과 Random fingerprint도 바꾸지 않는다.

실행 이력 V3는 `engineImplementationVersion=MATCH_SIMULATOR_ENGINE_IMPLEMENTATION_V3`와 profile별 active rules를 분리한다. 기존 세 profile은 `MATCH_SIMULATOR_PRE_JUNGLE_RULES_V2`, pure-JRM Jungle Economy candidate는 `MATCH_SIMULATOR_JUNGLE_ECONOMY_RULES_V2`, Tempo candidate는 `MATCH_SIMULATOR_JUNGLE_TEMPO_RULES_V1`이다. `replayProvenanceHash`는 두 version, configuration, raw resource/version snapshot, side/team/roster, series history, Draft rules/scoring policy/final draft/final assignment, seed를 묶는다. `timelineHash`는 complete output을 별도로 고정한다. `randomFingerprint`는 실제 seeded `Random.next(bits)` 소비의 draw count와 ordered trace SHA-256을 기록하는 observational output이며 replay input hash에는 들어가지 않는다. 기존 `engineRulesVersion` field는 active rules의 additive compatibility alias다. 기존 `draftIdentity()`와 Hard Fearless commit semantics는 변경하지 않았다.

`BASELINE_V1`과 기존 autowired simulator의 same teams/assignments/seed complete timeline은 exact parity다. 기존 Spring `MatchController`는 계속 direct autowired simulator와 DummyDataFactory를 사용하고 HTTP profile 선택은 추가하지 않았다.

### Pre-Jungle baseline

공식 oracle은 `PRE_JUNGLE_RUNTIME_BASELINE_V2`다. Focused tests와 final full regression이 clean pass하고 production source guard가 동일한 tree에서만 `generatePreJungleRuntimeBaselineV2`를 실행했다. 새 JVM 재생성도 source artifact와 byte-identical하게 성공했다.

| 항목 | 값 |
| --- | --- |
| Baseline ID | `PRE_JUNGLE_RUNTIME_BASELINE_V2` |
| Fixed schedule | 3 profiles × GEN–T1 G1/G2 + T1–GEN mirror G1 = 9 matches |
| JSON | `backend/baseline/pre-jungle-runtime-v2/pre-jungle-runtime-baseline-v2.json` |
| JSON SHA-256 | `0bce126117683e47ace908c348dbe2448f21592dc5009bd9f4514bb566fadb8e` |
| Resource provenance hash | `3affaf03ce588e0d1054e35ab1839f04e262d8ba3c512188374707ce3c1b8a4e` |
| Production source tree | 456 files / `b7965a1d1ebb9d76f298bc65e957da79c4e7cf2a3d0df35a6eca29ebaa0ab350` |
| Source revision | `8a55664955fad82d037ca9286be6bdb050b029fe` + exact working-tree hash |
| Rules / engine | `MATCH_SIMULATOR_PRE_JUNGLE_RULES_V2` / `MATCH_SIMULATOR_ENGINE_IMPLEMENTATION_V1` |
| Random identity | per-match draw count + ordered `next(bits)` trace SHA-256 |
| Jungle Clear invariant | artifact 당시 contribution disabled, authored gameplay-enabled profile count 0 |

Artifact와 build report mirror는 canonical CRLF로 byte-identical하고 `SHA256SUMS.txt` 검증을 통과했다. Baseline JSON은 `.gitattributes`의 `-text` 규칙으로 Git line-ending 변환을 금지하며 generator도 host OS 대신 CRLF를 명시한다. Generator는 V2 profile 목록을 세 개로 고정하고 future enum 값을 자동 포함하지 않으며 existing source bytes가 다르면 overwrite를 거부한다. V1 task도 재생성을 거부한다.

현재 active resource와 engine identity는 artifact 생성 시점과 달라졌으므로 replay provenance hash는 의도적으로 달라진다. `verifyJungleEconomyOffParity`는 이 차이를 제외하고 기존 세 OFF profile의 9개 configuration hash, Draft/final assignment identity, complete timeline hash, Random draw count/trace hash, winner/duration/event/snapshot count가 공식 V2와 exact equality인지 검증하며 9/9 clean pass했다.

Jungle Tempo production code를 추가하기 직전에는 V1-A clean full을 재사용한 동일 production tree에서 `PRE_JUNGLE_TEMPO_RUNTIME_BASELINE_V1`을 먼저 생성했다. Schedule은 기존 네 profile × GEN–T1 G1/G2 + T1–GEN mirror = 12 matches다. Artifact canonical CRLF raw SHA-256은 `17f703a48949b63bf4ca25f4b32be2bc22fac87a439cdd8cb7c18aadc7f82074`, 당시 canonical production guard는 464 files / `674b3148d782e98753bfd9e79f8b5f78b12cc3ecea8a85bc34ff8fbcaa2def0d`, engine은 V2다. JSON bytes는 동봉된 `SHA256SUMS.txt`로 고정한다.

V1-B production tree에서 `verifyPreJungleTempoParity`를 실행한 결과 12/12 configuration/Draft/final assignment/complete timeline/Random fingerprint/winner/duration/event/snapshot exact parity가 통과했다. Engine V3 때문에 replay provenance hash만 의도적으로 달라지며, report는 `build/reports/jungle-tempo-v1-b/pre-tempo-parity-report.json`에 기록된다.

V1 JSON의 raw SHA `2dcf67a3501200f0bce3de6239dcfbed3b27bafdc9287940f3f56171223a1d71`은 그대로 유효하다. 다만 V1 생성 뒤 별도 JVM 재생성에서 `Set.copyOf(EnumSet)` iteration이 player skill별 seeded draw 배정을 바꾸고 Champion Power tag order가 snapshot summary를 바꾸는 두 cross-process 비결정성을 발견했다. 따라서 V1은 당시 실행의 immutable 기록으로 보존하되 공식 replay/timeline oracle로 사용하지 않고 V2가 이를 대체한다. V2는 canonical enum order를 production contract로 고정하고 두 별도 JVM에서 문서와 9개 timeline이 모두 동일함을 확인했다.

### HTTP match runtime

Spring `MatchController`에 실제 주입되는 기본 roster는 계속 `DummyDataFactory` legacy/demo team이다. Fixed-seed HTTP test는 실제 KILL event가 기존 display fields와 additive `player-fixture-*` ID fields를 함께 직렬화함을 검증한다. Match preflight는 양 팀 전체의 duplicate stable `PlayerId`를 gameplay Random 전에 거부한다. 별도 `RealDraftMatchOrchestrator`가 real backend flow를 제공하지만 HTTP default path로 전환하지 않았다.

현재 autowired simulator path는 lane/gank/roam/objective/macro/progression/Champion Power를 활성화하지만 `ChampionMatchupMode.OFF`와 `TeamCompositionGameplayMode.OFF`를 사용한다. 이 exact snapshot은 `BASELINE_V1`이다. `SimulationOptions.productionDefaults()` 및 `FULL_SYSTEM_CANDIDATE_V1`의 Matchup `GEOMETRIC_V2`와 Composition `PRODUCTION_V2`와 구분해야 한다.

## Implemented

- versioned Champion Catalog/Power/Matchup/Composition/Jungle resources와 coherent manifest loading
- `PlayerId` value object와 explicit 50-record identity resource/catalog
- `PlayerRatingCatalog`의 기존 roster-key lookup 및 additive PlayerId dual lookup
- exact-SHA proficiency resource loader, semantic/cross-catalog validation, sparse PlayerId catalog
- stable `PlayerId`를 보유한 `Player`/`PlayerState`, match-scoped `PlayerKey`, structured `TeamState` lookup
- KILL/action event의 stable participant IDs와 기존 display fields의 additive 분리
- 10개 실제 LCK 팀의 deterministic assembly 및 GEN 대 T1 same-seed simulator smoke
- ChampionId presence와 target-role completion을 분리한 537-key reachability diagnostic와 JSON/CSV/SHA report
- coherent default player catalog graph, exact resource semantic envelope, match-wide stable PlayerId invariant
- real LCK Team→DraftEngine→final role→MatchChampionAssignments→seeded MatchSimulator application orchestration
- field-complete closed-set runtime profiles, instrumentation isolation, configuration/replay/timeline hashing
- ID-only factory boundary와 exact registry-membership 재검증으로 막은 fabricated resolved-profile 우회
- 10개 raw resource identity와 roster/series/Draft/final assignment, engine/rules version을 포함한 structured execution provenance V3
- gameplay와 격리된 per-match Random draw count/trace hash와 observed/plain exact timeline parity
- canonical PlayerSkill realization 순서와 enum-set timeline serialization
- clean full regression 뒤 생성하고 별도 JVM에서 재검증한 9-match Pre-Jungle V2 baseline
- match-scoped `JungleEconomyState`, unified `JungleEconomyOutcome`, structured execution stats와 `Champion Clear × pure Jungle Resource Management` CS/gold/XP V1-A candidate
- candidate pure-JRM/PATHING orthogonality, complete skip-reason Random non-consumption, progression-OFF CS/gold, canonical diagnostic map order와 real GEN–T1 candidate replay smoke
- historical OFF branch의 player iteration/Random 순서를 보존하고 공식 Pre-Jungle V2 9경기와 exact parity를 검증하는 전용 diagnostic
- Tempo production 수정 전에 고정한 four-profile/12-match `PRE_JUNGLE_TEMPO_RUNTIME_BASELINE_V1`과 V1-B 이후 12/12 exact parity diagnostic
- match-scoped `JungleTempoState`, bounded efficiency/credit/readiness/action-cost rules, structured readiness/consumption diagnostics
- Economy V1-A의 actual successful outcome만 credit으로 연결하고, gank/counter-gank base eligibility 뒤·trigger Random 전에 readiness를 적용하는 V1-B candidate
- actual gank/counter attempt만 side별 credit을 소비한다. Tempo-not-ready/ineligible/duplicate path는 state와 trigger Random을 보존하고, eligible failed-trigger fallthrough는 trigger Random만 소비하면서 credit, action state와 downstream Random을 보존한다.
- 다섯 번째 closed profile `FULL_SYSTEM_WITH_JUNGLE_TEMPO_CANDIDATE_V1`, rules V1, configuration hash와 engine provenance V3
- explicit team-code/roster/rating/final-role/assignment/Hard Fearless preflight와 caller-owned series commit
- seed 기반 Match Simulation, event/snapshot timeline, common kill/reward/death path
- lane pressure/combat, gank/counter-gank, roam, position economy, progression
- Dragon/Baron/Elder, objective decision/contest/trade, structure/Nexus end game
- Champion Power, `GEOMETRIC_V2` Matchup, full Composition analysis와 `PRODUCTION_V2` decision channel
- Draft planning, candidate generation, bounded search, final flex-role assignment와 Hard Fearless history
- champion catalog/match simulation API와 React timeline UI

## Partial / Disabled

- Real LCK Draft→Match flow는 backend component로 연결됐지만 `MatchController`, Draft API와 frontend에는 아직 노출되지 않았다.
- Active Matchup/Composition resource는 완전하지만 현재 HTTP MatchSimulator mode는 둘 다 `OFF`다.
- Explicit runtime profiles는 backend orchestration input이며 아직 HTTP/frontend profile selector로 노출하지 않았다.
- Jungle Economy V1-A는 economy-only profile에서 계속 CS/gold/XP에만 연결된다. V1-B Tempo candidate는 별도 profile에서 bounded readiness를 gank와 counter-gank eligibility에만 연결했다. Objective eligibility/확률/reward에는 직접 연결하지 않았고 production 채택·튜닝도 아직 결정하지 않았다.
- `DraftEngine`은 application component 내부의 pure domain dependency이며 독립 Spring bean/API로 공개되지 않는다.
- 첫 game은 exclusion이 없어 단판처럼 동작하지만 별도 Standard ruleset 선택 기능은 없다.

## Pending

1. `JUNGLE_V1_FOCUSED_HARDENING`으로 더 넓은 eligibility, FARM/XP/gold integrity, Random non-consumption, priority/fallthrough, same-seed determinism을 하나의 gate로 묶는다.
2. 그 뒤 다섯 profile lane(BASELINE / MATCHUP ONLY / FULL / FULL+JUNGLE ECONOMY / FULL+JUNGLE TEMPO)으로 `PHASE_13G_B_REAL_DATA_INTEGRATED_AUDIT_AND_CALIBRATION`을 수행한다.
3. V1-A/B 진단 결과를 근거로 objective 직접 연결 여부를 별도 결정하고, `PRODUCTION_V1` 범위를 선택한다.
4. Match Engine V1 input/output/profile/provenance를 freeze한다.
5. 이후 Real Match API/frontend/BO3·BO5, Career/Save/Season 순으로 진행한다.

## Test Snapshot

Final command:

```text
.\gradlew.bat test --console=plain --no-daemon
```

| 항목 | 결과 |
| --- | ---: |
| JUnit suites | 163 |
| Tests | 1,940 |
| Failures | 0 |
| Errors | 0 |
| Skipped | 0 |
| Aggregate JUnit XML time | 500.926 seconds |
| Gradle wall duration | 8m 32s |
| Build | `BUILD SUCCESSFUL` |

Verification performance hardening은 complete timeline 재귀 reflection 비교를 canonical SHA-256 + mismatch structural diff로 교체하고 독립 mutation contract를 추가했다. Full-population composition 검사는 `compositionHoldoutAudit`로, large-seed 통계는 `simulationDistributionAudit`로 분리했다. 기본 regression에는 bounded selection/schedule 계약과 다중-seed gameplay invariant가 남아 있다. 최종 full은 single fork로 실행했으며 병렬화는 적용하지 않았다.

Jungle V1-B는 18개 default correctness test를 추가했고, affected focused regression은 기존 gank/counter/lane/FARM/simulator/Random/real Draft 경로까지 포함해 1분 52초에 clean pass했다. 새 경계는 first/repeat readiness, efficiency clamp, bank cap, continuity reset, duplicate/backward time, ineligible Random 0회, failed-trigger의 eligible trigger Random 소비와 credit/action-state 보존, lane fallthrough, no-kill actual consumption, counter-gank side별 consumption, diagnostics ON/OFF complete timeline equality와 real GEN–T1 replay를 포함한다.

`verifyPreJungleTempoParity`는 1분 50초에 기존 네 profile 12/12 exact pass했고 report SHA-256은 `7bac4c34548019aaa2af422aa5e366dee42569dd8c4ed393645af8de33665313`이다. `runJungleTempoCandidateDiagnostic`의 고정 12-seed 관찰에서는 Economy-only 15 gank/3 counter-gank, Tempo 15 gank/2 counter-gank가 발생했고 Tempo의 structured consumption은 각각 15/2로 actual action과 exact equality였다. 이는 calibration이나 production gate가 아니며 report SHA-256은 `3f94b100464a48181fccf5a04a5e16f62dea3e17a05b1398215f65afddab1199`다.

Final full 전후 canonical production source/resource/build guard는 모두 471 files / `143112d499c9731e25de80edf2883621c7bb9c3c948907f4a0c8d0146093a260`으로 동일하다. 이후 baseline byte-integrity correction에서 production source/resource/build는 변경하지 않고 test-only canonical serializer, raw-byte checksum test, `.gitattributes`, baseline CRLF bytes와 문서만 수정했다. 세 baseline raw SHA는 기존 `SHA256SUMS.txt`와 exact equality이며 focused 13 tests, Jungle Economy OFF 9/9 parity와 Pre-Tempo 12/12 parity가 통과했다. 이 isolated test/artifact/docs correction은 Full regression budget의 clean-pass 재사용 범위이므로 backend full regression을 반복하지 않았다.

이번 hardening에서는 세 번째 full regression이 필요했다. 첫 clean full 뒤 별도 JVM artifact oracle이 unordered `PlayerSkill` set iteration으로 seeded realization draw-to-skill 배정이 달라지는 production 결함을 발견했고, 이를 고친 두 번째 clean full 뒤 다시 별도 JVM에서 Champion Power tag summary ordering이 timeline hash를 바꾸는 독립 production 결함을 발견했다. 두 문제 모두 single-JVM focused/full suite만으로 검출할 수 없었으므로 canonical ordering 수정과 cross-JVM 후보 2회 exact equality를 먼저 확정한 뒤 위 final full을 실행했다. 그 뒤 executable production/test source는 변경하지 않았다.

Pre-Jungle V2 생성 당시 production source/resource/build guard는 456 files / `b7965a1d1ebb9d76f298bc65e957da79c4e7cf2a3d0df35a6eca29ebaa0ab350`으로 동일했다. 당시 공식 V2를 새 JVM에서 같은 SHA로 재생성한 기록은 historical baseline provenance로 계속 보존한다.

테스트/diagnostic 실행 경계는 [Testing](development/testing.md), player contract는 [Player System](architecture/player-system.md)과 [Player Data Schema](reference/player-data-schema.md)를 참고한다.

## Last Updated

2026-08-22 (Asia/Seoul)
