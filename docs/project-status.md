# Project Status

이 문서는 2026-08-21 working tree의 production source, active resources, 최종 backend regression과 이번 milestone에서 직접 생성한 structured diagnostic을 기준으로 한 현재 snapshot이다. 과거 build output이나 현재 HEAD보다 앞선 report는 baseline으로 간주하지 않는다.

## Current Production Snapshot

### Champion resources

| 항목 | 현재 값 |
| --- | --- |
| Active manifest | `full-173-resource-set-2026-08-v1` |
| Champion pool | `full-173-2026-08-v1` |
| Champions | 173 |
| Legal `ChampionRoleKey` | 216 |
| Role counts | TOP 54 / JUNGLE 51 / MID 45 / ADC 31 / SUPPORT 35 |
| Flex champions | 36 |
| Power | 173 profiles, `full-173-power-2026-08-v1` |
| Matchup | 216 materialized roles, `full-173-role-matchup-profile-2026-08-v2` |
| Composition | 216 materialized roles, `full-173-composition-profile-2026-08-v2` |
| Composition full-profile hash | `23d616cab6abea69d5ad783f405b0b4518a14608b0be4eac3d53f669acab6877` |
| Jungle Clear | 51 JUNGLE profiles, `full-173-jungle-clear-candidate-2026-08-v1` |
| Jungle Clear gameplay enabled | 0 |

Champion Power resource에는 8 level curves와 8 item curves가 있다. Cross-catalog completeness는 `ChampionResourceSet` 생성 시 exact set equality로 검증된다. 이 player-data milestone은 Champion resource, supported position, Power, Matchup, Composition, Jungle Clear를 변경하지 않았다.

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

### HTTP match runtime

Spring `MatchController`에 실제 주입되는 기본 roster는 계속 `DummyDataFactory` legacy/demo team이다. Fixed-seed HTTP test는 실제 KILL event가 기존 display fields와 additive `player-fixture-*` ID fields를 함께 직렬화함을 검증한다. Match preflight는 양 팀 전체의 duplicate stable `PlayerId`를 gameplay Random 전에 거부한다. `LckTeamAssembler`를 통한 real team은 production-capable backend 경계와 focused simulator smoke까지 도달했지만 HTTP default path로 전환하지 않았다.

현재 autowired simulator path는 lane/gank/roam/objective/macro/progression/Champion Power를 활성화하지만 `ChampionMatchupMode.OFF`와 `TeamCompositionGameplayMode.OFF`를 사용한다. `SimulationOptions.productionDefaults()`가 제공하는 Matchup `GEOMETRIC_V2`와 Composition `PRODUCTION_V2`와 구분해야 한다.

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
- seed 기반 Match Simulation, event/snapshot timeline, common kill/reward/death path
- lane pressure/combat, gank/counter-gank, roam, position economy, progression
- Dragon/Baron/Elder, objective decision/contest/trade, structure/Nexus end game
- Champion Power, `GEOMETRIC_V2` Matchup, full Composition analysis와 `PRODUCTION_V2` decision channel
- Draft planning, candidate generation, bounded search, final flex-role assignment와 Hard Fearless history
- champion catalog/match simulation API와 React timeline UI

## Partial / Disabled

- Real LCK team/proficiency path는 backend에서 준비됐지만 `MatchController`와 Draft API/frontend orchestration에는 아직 연결되지 않았다.
- Active Matchup/Composition resource는 완전하지만 현재 HTTP MatchSimulator mode는 둘 다 `OFF`다.
- Jungle Clear는 51-role foundation과 evaluator가 있으나 모든 profile이 `gameplayEnabled: false`이고 simulator economy/pathing에 연결되지 않았다.
- DraftEngine은 real `DraftTeamContext`와 match assignments를 만들 수 있지만 Spring bean/API/frontend orchestration이 없다.
- 첫 game은 exclusion이 없어 단판처럼 동작하지만 별도 Standard ruleset 선택 기능은 없다.

## Pending

1. `REAL_DRAFT_TO_MATCH_BACKEND_ORCHESTRATION`: real roster 선택, Draft 결과, match start를 backend service/config 경계에서 연결한다.
2. `PHASE_13G_B_REAL_DATA_INTEGRATED_AUDIT_AND_CALIBRATION`: 이번 reachability review signal을 입력으로 별도 진단·calibration을 수행한다.
3. HTTP simulator가 의도한 production Matchup/Composition mode와 일치하는지 explicit configuration audit 후 결정한다.
4. Jungle Clear는 calibration, eligibility, Random-consumption 검증 뒤에만 gameplay에 연결한다.
5. 별도 Standard draft mode가 필요하면 Hard Fearless identity/availability와 분리한 additive ruleset으로 추가한다.

## Test Snapshot

Final command:

```text
.\gradlew.bat test --console=plain --no-daemon
```

| 항목 | 결과 |
| --- | ---: |
| JUnit suites | 148 |
| Tests | 1,944 |
| Failures | 0 |
| Errors | 0 |
| Skipped | 0 |
| Aggregate JUnit XML time | 957.868 seconds |
| Gradle wall duration | 16m 9s |
| Build | `BUILD SUCCESSFUL` |

이번 hardening의 full regression은 최종 production/Gradle tree에서 1회 실행해 clean pass했다. Default XML에는 diagnostic-tagged 537-key audit suite가 없고, full run 전후 full-population report timestamp도 동일해 기본 `test`가 report를 다시 생성하지 않았음을 확인했다. Role-specific flex cases, Gate binding, catalog graph/prerequisites, resource envelope, match-wide identity, HTTP serialization, representative real keys, small report writer focused tests도 각각 통과했다.

테스트/diagnostic 실행 경계는 [Testing](development/testing.md), player contract는 [Player System](architecture/player-system.md)과 [Player Data Schema](reference/player-data-schema.md)를 참고한다.

## Last Updated

2026-08-21 (Asia/Seoul)
