# Project Status

이 문서는 2026-08-23 working tree의 production source, active resources, 최종 backend regression과 직접 생성한 structured diagnostic을 기준으로 한 현재 snapshot이다. 과거 build output이나 현재 HEAD보다 앞선 report는 baseline으로 간주하지 않는다.

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

실행 이력 V6는 `engineImplementationVersion=MATCH_SIMULATOR_ENGINE_IMPLEMENTATION_V6`와 profile별 active rules를 분리한다. 기존 세 profile은 `MATCH_SIMULATOR_PRE_JUNGLE_RULES_V2`, pure-JRM Jungle Economy candidate는 `MATCH_SIMULATOR_JUNGLE_ECONOMY_RULES_V2`, Tempo candidate는 `MATCH_SIMULATOR_JUNGLE_TEMPO_RULES_V1`이다. Batch C V4의 structured eligibility diagnostics 뒤 V5에서 death/activity reason과 one-major-combat gate를 강화했고, V6는 Champion Power 참가자 평균과 diagnostic map을 명시적 `PlayerKey(TeamSide, Position)` 순서로 canonicalize해 JVM별 부동소수점 마지막 비트 차이를 제거했다. Gameplay tuning/rules contract와 다섯 configuration hash는 그대로다. `replayProvenanceHash`는 두 version, configuration, raw resource/version snapshot, side/team/roster, series history, Draft rules/scoring policy/final draft/final assignment, seed를 묶는다. `timelineHash`는 complete output을 별도로 고정한다. `randomFingerprint`는 실제 seeded `Random.next(bits)` 소비의 draw count와 ordered trace SHA-256을 기록하는 observational output이며 replay input hash에는 들어가지 않는다. 기존 `engineRulesVersion` field는 active rules의 additive compatibility alias다. 기존 `draftIdentity()`와 Hard Fearless commit semantics는 변경하지 않았다.

`BASELINE_V1`과 기존 autowired simulator의 same teams/assignments/seed complete timeline은 exact parity다. 기존 Spring `MatchController`는 계속 direct autowired simulator와 DummyDataFactory를 사용하고 HTTP profile 선택은 추가하지 않았다.

### Final 13G-B1 audit contract and real-match harness

`PHASE_13G_B1_AUDIT_CONTRACT_AND_REAL_MATCH_HARNESS`는 gameplay를 바꾸지 않는 test-side 감사 기반이다. 실제 10개 LCK 팀의 모든 unordered pair 45개를 양 진영으로 뒤집은 G1 90 fixtures를 주 감사 축으로 고정하고, SHA 기반 deterministic pairing으로 모든 팀을 한 번씩 포함한 Hard Fearless G2 5 pairs × 양 진영 10 fixtures를 보조 민감도 축으로 고정했다. 각 fixture의 calibration 24 seeds와 holdout 8 seeds는 서로 분리해 미리 예약했고 schedule hash `3bb5e81241a3be2a1509e67528e577ae8f48fca94dec5fc15f93ec8ac78052ef`로 전체 계약을 고정한다.

한 fixture는 production `RealDraftMatchOrchestrator`로 series target game까지 한 번 준비한 뒤 그 `FinalDraftResult`, 실제 양 팀 roster와 final assignment를 profile loop 밖에서 고정한다. `PreparedFixture`는 이 controlled 경로만 생성할 수 있고, writer는 schedule content hash를 재계산한 뒤 canonical frozen schedule과 exact equality를 확인한다. Match 실행만 다섯 closed profile로 바꾸므로 paired comparison 사이에 re-Draft나 champion assignment drift가 없다. 각 결과에는 configuration/rules, resource/roster/series/Draft/final assignment/replay provenance, complete timeline hash, Random fingerprint, 최종 팀·정글러 checkpoint, selected diagnostics, 전체 structured diagnostics hash와 domain별 integrity가 기록된다. 이 bridge는 test source에만 존재하며 production simulator API, Spring runtime, REST와 frontend는 변경하지 않았다.

B1 bounded dry-run은 `GEN` Blue–`T1` Red G1 fixture와 calibration/holdout에 속하지 않는 별도 seed 하나로 다섯 profile을 한 번씩 실행하고 `BASELINE_V1`을 한 번 재실행했다. 다섯 실행의 Draft/final assignment/resource/roster identity가 exact equality였고 BASELINE same-seed replay의 replay provenance, timeline, Random fingerprint와 simulator의 전체 diagnostic snapshot/history가 exact equality였다. Champion Power/Matchup, Composition, Combat Outcome, Objective Priority, Structure, Lane Phase, Mid Game Macro, Progression과 Jungle Economy의 명시적 구조 오류는 모두 0이다. 세 pre-Jungle profile은 Jungle Economy/Tempo 실행 0, Economy candidate는 CS/gold/XP 경로 reachability, Tempo candidate는 READY 관찰 23회와 Gank actual consumption 1회를 보였다. 이 단일 fixture 관찰의 winner, 경기 시간이나 profile 차이는 balance 근거가 아니다.

P1 선행 gate는 같은 B1 fixture를 두 fresh JVM에서 각각 실행해 7개 artifact 전체를 byte-for-byte 비교한다. Phase-specific source/build guard로 재고정한 V6 tree에서 두 manifest SHA가 `dc8f63a117bbd15dc05ca533ae8c98a3707ae70fa9af810f6f14539d8ee9b9cd`로 같았고, 직후 canonical B1 manifest SHA도 exact equality였다. 이 연결이 다르면 B2 worker는 시작하지 않는다.

재고정한 generated artifact는 `backend/build/reports/phase13g-b1/`의 contract/profile/schedule JSON, 3,200-row reserved schedule CSV, 5-row dry-run JSON/CSV와 `SHA256SUMS.txt`다. Summary SHA-256은 `37ce2c275d5dc4837328c7e8c9fb7b100f62ffcdee4ee740217140d8c751f5c0`, manifest SHA-256은 `dc8f63a117bbd15dc05ca533ae8c98a3707ae70fa9af810f6f14539d8ee9b9cd`다. Report status는 `HARNESS_HARDENED_READY_FOR_CALIBRATION`이며 `calibrationExecuted=false`, `holdoutExecuted=false`, `productionDecision=NOT_EVALUATED`를 명시한다. Canonical production guard는 472 files / `68edbcb7393c9a54c0888a4f27a4e286774306675dce48991554fd22dcb2ddac`, B1 audit harness guard는 9 files / `20c6d834dd01d90688c377da1f68afd28abb20dff34e7cf02bb0253a22271b74`이다. Build report는 증거이지 baseline 또는 correctness input이 아니다.

### Final 13G-B2 real-data calibration

`PHASE_13G_B2_REAL_DATA_CALIBRATION`은 B1이 예약한 calibration lane만 열었다. 100 fixtures × 24 seeds × 5 fixed profiles로 정확히 12,000경기를 실행했고 G1 10,800 / Hard Fearless G2 1,200으로 분리했다. Fixture별 production Draft는 profile/seed loop 밖에서 한 번 고정하며 G1 1회, G2는 series history를 포함해 2회씩 총 110 orchestration을 사용한다. 이 준비 실행과 fixture별 BASELINE 결정성 replay 100회는 12,000 calibration 표본에서 제외된다. Holdout 실행은 0이다.

네 shard class는 `forkEvery=1`로 각각 별도 fresh JVM에서 실행되며, 동시 실행 수가 환경의 Gradle worker 수에 제한되더라도 JVM 재사용은 허용하지 않는다. 각 fixture의 120행은 job/fixture/roster/Draft/profile/seed에서 replay provenance를 다시 계산하고, 결과·진단·정글 관측 전체 row payload와 fixed Draft/replay evidence의 canonical digest를 확인한 뒤 임시 파일에서 atomic move로 승격한다. Shard 완료 시 checkpoint raw-byte SHA를 담은 worker receipt를 남기며 finalizer는 서로 다른 JVM identity 4개, fixture ownership과 payload digest 100개를 다시 검증한다. 이 receipt 경계를 통과하지 않은 synthetic output은 `SYNTHETIC_VALIDATION_ONLY`이고 공식 READY를 만들 수 없다. V2 공식 재실행은 checkpoint fixture 100, resume 0, `seed 24/24` 100을 로그에 남겼고 12,000 replay provenance가 모두 고유했다. 매 fixture의 첫 BASELINE replay 100/100이 exact였고 12,000/12,000 match의 Champion Power/Matchup, Composition, Combat Outcome, Objective Priority, Structure, Lane Phase, Mid Game Macro, Progression, Economy 구조 오류 합계는 모두 0이다.

고정 관측 시각은 600/900/1,200/1,500/1,800초다. Structure push가 게임 시계를 tick 경계 너머로 전진시킬 수 있으므로 상태를 보간하지 않고 요청 시각 이상인 첫 실제 snapshot을 사용하며 requested/actual time을 함께 기록한다. 종료 전에 도달하지 못한 시각에는 final state를 복제하지 않는다. Fixed side observations 101,890개 중 14,778개가 요청 시각보다 늦었고 최대 지연은 54초였다.

Calibration의 paired winner identity 변화는 다음과 같다. 이는 같은 seed에서 profile이 action eligibility와 이후 Random 소비 순서를 바꾼 결과까지 포함하는 민감도 지표이며, 곧바로 승률 인과효과나 production 채택 판정으로 해석하지 않는다.

| Paired comparison | G1 flips | G2 flips | 합계 |
| --- | ---: | ---: | ---: |
| Matchup − Baseline | 39 / 2,160 | 5 / 240 | 44 / 2,400 (1.83%) |
| Full − Matchup | 74 / 2,160 | 9 / 240 | 83 / 2,400 (3.46%) |
| Economy − Full | 12 / 2,160 | 6 / 240 | 18 / 2,400 (0.75%) |
| Tempo − Economy | 732 / 2,160 | 79 / 240 | 811 / 2,400 (33.79%) |
| Tempo − Baseline | 755 / 2,160 | 87 / 240 | 842 / 2,400 (35.08%) |

Economy − Full은 G1에서 Blue/Red 정글 최종 CS가 평균 +3.382/+4.230, XP가 +644.8/+691.5였고 G2에서는 CS +6.233/+2.096, XP +814.3/+579.0이었다. Tempo − Economy는 G1에서 경기 시간 평균 +7.79초, 정글 CS +1.137/+0.644, XP +80.4/+30.7이고 G2에서는 +33.42초, CS +2.925/+4.063, XP +167.6/+272.7이었다. Tempo profile 2,400경기에서 Gank readiness/actual consumption은 47,495/5,175회, Counter-gank는 4,447/660회로 실제 경로가 충분히 도달했다. Tempo − Economy flip 방향은 Blue→Red 386, Red→Blue 425로 한 방향 독점은 아니지만, 33.79%의 높은 paired 민감도는 B3 gate 동결 시 별도 product review 대상으로 보존했다. 모든 profile의 timeout은 0이었다.

Artifact는 `backend/build/reports/phase13g-b2/`의 contract/job/fixed-Draft/replay/checkpoint receipt manifest/match/checkpoint/marginal 및 profile/team/jungler summary, integrity/review JSON과 `SHA256SUMS.txt`다. Phase-specific source/build guard 도입으로 stale checkpoint를 재사용하지 않고 V3 경로에서 12,000경기를 다시 고정했다. 16/16 raw SHA가 통과했고 review SHA-256은 `32c1770b6971179c0cb7033e853882e7e9fb06c6980285eb2dba23210993fbee`, manifest SHA-256은 `71ac3a26cc4df6c49794c2daeb2efc75bd2667b39237b89af4a1a6bda963d7e4`다. Status는 `CALIBRATION_EVIDENCE_READY_FOR_REVIEW`, run guard는 `281ecd7b4985695f0c3168fd699b6be0c525387aea14f701a23d2f0614485e30`, checkpoint payload manifest는 `f1945f8333733a4c3ecefdfcaa30276129a98a3b58ae9e609e45d247de79df58`다. B1/B2 combined guard는 18 files / `040800b178865ebec1a41e9dbbd78a2b7166c52b5e94a14df17fad7d6dea4a24`이며 calibration behavior는 이전 B2와 exact equality다. 자동 tuning과 `PRODUCTION_V1` 결정은 수행하지 않았다.

### Final 13G-B3 frozen holdout

`PHASE_13G_B3_FROZEN_HOLDOUT`은 B2를 다시 실행한 최종 executable tree에서 candidate와 acceptance gate를 먼저 canonical JSON으로 고정한 뒤, B1에 예약된 `HOLDOUT` seed만 한 번 materialize했다. Contract 생성 시 holdout execution count는 0이었고, contract SHA-256 `7b7de51ac242e0bd260efe7f2130db17c538cef49b897c02bfe3579484aa8fa4`, candidate identity `6a920353b5436d7053fa2863de361a1201b09d88a08ef0c25ecaca20051ea81f`, acceptance gate identity `b05a346777cce78f0c31f7916de24d062a6e55bb4d17bf67eafe92eb225a00dd`를 official runner가 exact match로 요구한다. Economy의 주 판정은 `ECONOMY_MINUS_FULL`, Tempo는 `TEMPO_MINUS_ECONOMY`이며 나머지 비교는 regression/context evidence다.

수치 gate 67개는 B2 point estimate와 calibration/holdout 표본 수를 사용한 99% two-sample normal prediction interval로 고정했다. 연속값은 `mean ± z·s·sqrt(1/nCalibration + 1/nHoldout)`, 비율은 `p ± z·sqrt(p(1-p)·(1/nCalibration + 1/nHoldout))`, `z=2.5758293035489004`, 소수 12자리 바깥쪽 반올림, 양 끝 inclusive다. G1/G2/전체 winner flip, 경기 시간, Blue gold edge, Blue/Red 정글 CS·XP, gank/counter-gank attempt drift와 side gap을 분리했고 Tempo flip 방향도 별도 gate로 고정했다. Award reachability, pre-Jungle/Tempo contribution 0, Tempo readiness/consumption reachability 및 actual attempt binding, SUPPORT FARM CS 0, timeout 0은 7개 exact behavior gate다. 명시적 product tolerance가 없는 Tempo 33.79%는 수치 일관성이 있어도 자동 PASS로 만들지 않고 `REVIEW_REQUIRED`로 남긴다.

공식 holdout은 G1 3,600 + Hard Fearless G2 400 = 4,000 matches, 4,000 unique jobs/provenance와 paired marginals를 만들었다. Economy − Full winner flip은 G1 10/720(1.389%), G2 1/80(1.25%), 전체 11/800(1.375%)였다. G1 상한 1.379455%를 1건에 해당하는 0.00943%p만 넘겨 수치 gate 67개 중 이 gate 하나만 실패했고 Economy verdict는 `FAIL`이다. 나머지 Economy 축은 범위 안이었으며 G1/G2 Blue/Red 정글 CS 변화는 +3.606/+4.226 및 +6.438/+2.050, XP 변화는 +644.2/+705.7 및 +785.3/+549.3이었다.

Tempo − Economy winner flip은 G1 249/720(34.58%), G2 23/80(28.75%), 전체 272/800(34.00%)로 B2 전체 33.79%의 높은 민감도를 재현했다. 방향은 Blue→Red 135, Red→Blue 137로 한 방향 독점이 아니며, Gank readiness/consumption 15,858/1,715와 Counter-gank 1,489/189가 모두 도달했고 consumption과 actual attempt 차이는 0이다. Tempo 수치 gate는 모두 통과했지만 높은 민감도에 대한 product tolerance가 없으므로 verdict는 `REVIEW_REQUIRED`다. Exact behavior gate는 7/7, numeric gate는 66/67이며 evidence 자체는 `HOLDOUT_EVIDENCE_READY_FOR_FINAL_REVIEW`, `productionDecision=NOT_EVALUATED`다.

각 shard는 자기 authorization 파일을 atomic move해 한 번만 시작할 수 있고 `forkEvery=1`인 네 독립 Test task가 4개 distinct JVM receipt를 남긴다. Finalizer는 authenticated checkpoint 100/100, raw payload digest 100/100, BASELINE replay 100/100 exact, calibration 실행 0, domain error 0, SUPPORT FARM CS 0, timeout 0을 재검증했다. Artifact는 `backend/build/reports/phase13g-b3/`에 있으며 final review SHA-256은 `60df0caa7b0193a4b67fe0380bc326d9e7efdae34f794515679131c3f305badf`, integrity SHA-256은 `909d2abb5869e68d7102fa7245410e535d8792dafe68051b200a352664b73bf7`, gate evaluation SHA-256은 `c1513e6adc633a1a4692cbe6a2b62a05a0977335fc6b143d4a82a522026b9ba1`, 18-entry manifest SHA-256은 `460d74573ffb971e9e2ff491a039d2002fc6e02a4f37067a9c18da73f0b70c9d`다. 이 holdout은 소비됐으며 결과가 불리해도 재실행하거나 gate/tuning을 바꾸는 입력으로 재사용하지 않는다.

### Final 13G-B synthesis and Production V1 decision

`FINAL_13G_B_SYNTHESIS_AND_PRODUCTION_V1_DECISION`은 새 match를 실행하지 않고 B2 12,000경기와 B3 4,000경기의 raw SHA manifest, review 상호 참조와 6,400개 주 비교 paired row만 읽었다. Final evidence는 `FINAL_EVIDENCE_VALID`, 새 simulation execution은 0이다. 결정은 `KEEP_CURRENT_RUNTIME_DEFAULT`: `FULL_SYSTEM_WITH_JUNGLE_ECONOMY_CANDIDATE_V1`과 `FULL_SYSTEM_WITH_JUNGLE_TEMPO_CANDIDATE_V1`을 Production V1 기본값으로 활성화하지 않고 실제 application default인 `BASELINE_V1`을 유지한다. Runtime identity는 `EXACT`, freeze readiness는 `READY_FOR_MATCH_ENGINE_V1_FREEZE`이며 다음 단계는 별도 작업인 `MATCH_ENGINE_V1_FREEZE`다.

Retained configuration hash는 `c8cc557bd721228c473e30d31b7258510f9608a18098578bc1da36e603536215`, active rules는 `MATCH_SIMULATOR_PRE_JUNGLE_RULES_V2`, engine은 `MATCH_SIMULATOR_ENGINE_IMPLEMENTATION_V6`다. Matchup/Composition/Jungle contribution은 각각 `OFF`/`OFF`/`DISABLED_NOT_INTEGRATED`이며 diagnostics는 configuration identity와 분리한다. Production source identity는 472 files / `68edbcb7393c9a54c0888a4f27a4e286774306675dce48991554fd22dcb2ddac`, resource provenance는 `64ab1be3fdfe8d6660648ac634b52a86a5693d264bfbe707153dac9c17d39b4f`다. Runtime identity hash는 `bcb3d2bdf009a8b53d6f99db69ad3f129a7c3c2f29570bcdf12ee0c0655ba675`다.

실제 wiring fixed-seed 검증에서 `RealDraftMatchOrchestrator` 기본 overload와 explicit `BASELINE_V1`은 execution provenance/replay identity/complete timeline이 exact였고, Spring autowired `MatchSimulator`도 configured `BASELINE_V1`과 complete timeline parity였다. `POST /api/matches/simulate`는 동일한 autowired simulator와 `DummyDataFactory` roster를 계속 사용하며 RealDraft HTTP 전환은 하지 않았다. `SimulationOptions.productionDefaults()`는 Matchup `GEOMETRIC_V2`, Composition `PRODUCTION_V2`, Jungle contribution OFF인 저수준 constructor default이며 configuration hash `caaf76274dc148040b0a95eae1ed5181790b2fc840f45af9b109ea7951c1fd5d`다. 이는 authoritative application runtime default가 아니다.

Economy의 frozen `FAIL`은 그대로 보존한다. G1 실제 1.388889%와 상한 1.379455%의 차이는 0.009434%p, 즉 이 표본 크기에서는 winner flip 한 건 경계다. 이를 구조적 결함이나 대규모 balance 붕괴로 확대 해석하지 않지만, 결과가 근소하다는 이유로 holdout을 다시 돌리거나 gate를 완화해 승인하지도 않는다. B2→B3 segment flip-rate 상관은 champion -0.394, player/team 0.104, player×champion -0.182로 안정된 특정 segment 패턴을 재현하지 않았다.

Tempo의 10개 공통 champion bucket별 민감도 순서는 calibration과 holdout에서 unweighted Pearson 0.906으로 재현됐다. 이 계산은 pre-registered decision gate가 아니고 player, team, fixture, matchup을 통제하지 않았으므로 champion의 독립 효과나 인과관계를 확립하지 않는다. Player×champion 0.588, fixture 0.422, player/team 0.219이고 현재 snapshot은 팀당 정글러가 한 명이라 player와 team 효과도 분리할 수 없다. 33.79%→34.00% winner flip은 같은 seed에서 action eligibility와 이후 Random trajectory가 달라진 민감도이지 직접적인 승률 인과효과가 아니다. 따라서 Tempo 미채택과 `DEFER_TO_V2_PRODUCT_TOLERANCE_AND_TRAJECTORY_ISOLATION_REVIEW`는 그대로다.

Machine-readable artifact는 `backend/build/reports/final-13g-b/`의 evidence binding, retained runtime identity, segmented sensitivity, flipped pairs, synthesis, Production Decision V2와 `SHA256SUMS.txt`다. 이전 5-entry manifest `f21a3bd1eaf34d9361c87c299199bce2432e6edb62585088cbc251a6e0145542`는 historical reference다. Hardened 6-entry manifest SHA-256은 `bd9a9cf3b089cfc76fceb0311094c1b70232278404f5675c42d89849d927bc98`이며 6/6 raw SHA가 통과했다. 이 결정은 gameplay source, resource, tuning, Gradle wiring, HTTP/frontend를 변경하지 않는다.

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

V1-B production tree에서 `verifyPreJungleTempoParity`를 실행한 결과 12/12 configuration/Draft/final assignment/complete timeline/Random fingerprint/winner/duration/event/snapshot exact parity가 통과했다. Batch C engine V4/V5와 P1 determinism V6 tree에서도 같은 12/12 exact gameplay parity를 다시 통과했다. Baseline engine V2와 현재 engine V6가 다르므로 replay provenance hash만 의도적으로 달라지며, current report SHA-256은 `a38b16811a3b74f5fe8958bce5eb5b8e1310ba18795af46ea016f705bbec22c9`다.

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
- 10개 raw resource identity와 roster/series/Draft/final assignment, engine/rules version을 포함한 structured execution provenance V6
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
- Jungle Gank의 unavailable/cooldown/no-lane/not-ready와 Counter Gank의 death/cooldown/activity/not-ready를 reason별·side별 immutable match diagnostic으로 노출하는 structured eligibility
- action/death/recovery/macro FARM block의 CS/FARM gold/XP/passive/Random 경계, priority/fallthrough/one-major-combat, reward/event integrity, fresh state와 same-seed replay를 묶고 same-kind duplicate attempt까지 검출하는 `JUNGLE_V1_FOCUSED_HARDENING` gate
- 다섯 번째 closed profile `FULL_SYSTEM_WITH_JUNGLE_TEMPO_CANDIDATE_V1`, rules V1, 고정 configuration hash와 engine provenance V6
- 실제 10개 LCK 팀의 90 G1 + 10 Hard Fearless G2 fixture, calibration/holdout seed split, frozen input rejection, full diagnostics integrity/hash와 fixed-Draft 5-profile JSON/CSV/SHA 출력을 고정한 hardened Final 13G-B1 test-side audit harness
- 명시적 PlayerKey 순서의 Champion Power 평균, fresh-JVM 2회 B1 artifact 7/7 byte equality와 canonical B1 manifest binding gate
- calibration-only 12,000경기, fixture-atomic resume, 100 replay exact, fixed-time Jungle 관측과 paired marginal/SHA artifact를 갖춘 Final 13G-B2 audit pipeline
- 사전 동결 contract, one-time authorization, 4 fresh-JVM receipt, 100 authenticated checkpoint와 4,000-row paired evidence를 갖춘 Final 13G-B3 holdout pipeline
- B2/B3 manifest를 다시 검증하고 player/champion/team/fixture 민감도를 분리해 현재 runtime 유지와 Match Engine V1 freeze readiness를 고정한 Final 13G-B production decision
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
- Final 13G-B는 Economy `FAIL`과 Tempo `REVIEW_REQUIRED`를 보존한 채 Production V1을 `KEEP_CURRENT_RUNTIME_DEFAULT`로 결정했다. 두 candidate profile은 감사/향후 설계용으로 남지만 runtime 기본값에는 활성화하지 않는다.
- Jungle Economy V1-A는 economy-only candidate profile에서 계속 CS/gold/XP에만 연결된다. V1-B Tempo candidate는 별도 profile에서 bounded readiness를 gank와 counter-gank eligibility에만 연결한다. Objective eligibility/확률/reward에는 직접 연결하지 않았고 Production V1 tuning은 수행하지 않았다.
- `DraftEngine`은 application component 내부의 pure domain dependency이며 독립 Spring bean/API로 공개되지 않는다.
- 첫 game은 exclusion이 없어 단판처럼 동작하지만 별도 Standard ruleset 선택 기능은 없다.

## Pending

1. 결정된 현재 runtime 기준으로 Match Engine V1 input/output/profile/provenance를 freeze한다. Economy/Tempo candidate는 V1 기본 범위에서 제외한다.
2. Economy를 변경하거나 Tempo V2를 설계한다면 이미 소비한 seed를 새 candidate의 검증 표본으로 재사용하지 말고 새 contract/calibration/holdout을 만든다.
3. Objective eligibility/reward 직접 연결은 별도 설계·검증 전까지 보류한다.
4. 이후 Real Match API/frontend/BO3·BO5, Career/Save/Season 순으로 진행한다.

## Test Snapshot

Final command:

```text
.\gradlew.bat test --console=plain --no-daemon
```

| 항목 | 결과 |
| --- | ---: |
| JUnit suites | 170 |
| Tests | 1,969 |
| Failures | 0 |
| Errors | 0 |
| Skipped | 0 |
| Aggregate JUnit XML time | 488.240 seconds |
| Gradle wall duration | 8m 23s |
| Build | `BUILD SUCCESSFUL` |

Verification performance hardening은 complete timeline 재귀 reflection 비교를 canonical SHA-256 + mismatch structural diff로 교체하고 독립 mutation contract를 추가했다. Full-population composition 검사는 `compositionHoldoutAudit`로, large-seed 통계는 `simulationDistributionAudit`로 분리했다. 기본 regression에는 bounded selection/schedule 계약과 다중-seed gameplay invariant가 남아 있다. 최종 full은 single fork로 실행했으며 병렬화는 적용하지 않았다.

Jungle V1-B는 18개 default correctness test를 추가했고, affected focused regression은 기존 gank/counter/lane/FARM/simulator/Random/real Draft 경로까지 포함해 1분 52초에 clean pass했다. 새 경계는 first/repeat readiness, efficiency clamp, bank cap, continuity reset, duplicate/backward time, ineligible Random 0회, failed-trigger의 eligible trigger Random 소비와 credit/action-state 보존, lane fallthrough, no-kill actual consumption, counter-gank side별 consumption, diagnostics ON/OFF complete timeline equality와 real GEN–T1 replay를 포함한다.

Jungle V1 focused hardening은 새 2개 class의 11개 cross-system test를 포함한다. Expanded focused regression은 17개 class / 168 tests이며 structured gank/counter eligibility, death/activity 분리, Random 0회, trigger/attempt/consumption algebra, action/death/recovery/macro FARM opportunity cost, passive gold, reward/event integrity, multiplicity-sensitive one-major-combat, match isolation과 same-seed replay를 포함한다.

Batch C의 `verifyPreJungleTempoParity`는 기존 네 profile 12/12 exact pass했고 V4/V5 report SHA-256은 각각 `6ba6d0c33332fa4a6eef343a9030fd3f031f6cef3a6d0363c6877db25fb5878f` / `87577b6c26073fb748ab6d9b9d2bda719437c60381ba99f71b07482bcdeca927`다. V6도 12/12 exact pass했고 current report SHA-256은 `a38b16811a3b74f5fe8958bce5eb5b8e1310ba18795af46ea016f705bbec22c9`다. `runJungleTempoCandidateDiagnostic`의 historical 고정 12-seed 관찰에서는 Economy-only 15 gank/3 counter-gank, Tempo 15 gank/2 counter-gank가 발생했고 Tempo의 structured consumption은 각각 15/2로 actual action과 exact equality였다. 이는 calibration이나 production gate가 아니며 Batch C 전후와 V5 report SHA-256은 모두 `3f94b100464a48181fccf5a04a5e16f62dea3e17a05b1398215f65afddab1199`로 byte-identical하다.

Batch C V4 final full 전후 canonical production source/resource/build guard는 모두 472 files / `54b53ea30453e39791dd8aa0197e95ed190697ce1b83c010baff1df540c833d9`로 동일했다. V5 follow-up은 2개 경계 테스트와 activity reason 분리를 반영한 뒤 final full을 첫 실행에서 166 suites / 1,953 tests clean pass했다. Full 뒤에는 production/shared fixture를 바꾸지 않고 one-major-combat marker 분류만 assertion-only로 좁혔고 affected 5 tests가 clean pass했으므로 full 결과를 재사용했다. 직전 V1-B guard는 471 files / `143112d499c9731e25de80edf2883621c7bb9c3c948907f4a0c8d0146093a260`이었다. Baseline byte-integrity correction과 V5 follow-up 모두 immutable baseline을 재생성하지 않았고 세 baseline raw SHA는 기존 `SHA256SUMS.txt`와 exact equality다.

Final 13G-B1 hardening의 schedule/profile focused verification은 2 suites / 7 tests, failures/errors/skipped 0으로 통과했다. `runPhase13GB1DryRun`은 1 suite / 1 test, failures/errors/skipped 0, JUnit suite 18.445초, Gradle wall 33초에 통과했고 generated SHA manifest 6/6을 검증했다. Schedule hash 위조와 임의 prepared fixture 생성 경로를 닫고, 누락됐던 전체 simulator diagnostics replay equality와 domain별 structural integrity, Tempo positive reachability를 고정했다. 이어 B1의 Gradle/test-harness 변경까지 포함한 complete backend full을 첫 실행에서 168 suites / 1,960 tests / failures 0 / errors 0 / skipped 0, aggregate JUnit XML 540.965초, Gradle wall 9분 15초로 clean pass했다. Canonical main source/resource/build guard는 472 files / `692cc941664ccecfd551a7b08c63f724d055fbfcdbfa721f7aca9ea8cbfd8458`로 B1 전과 같고, hardened audit harness guard는 9 files / `679ce37d608f83a4ffe7a8f708dbab882d8f20044817c98eeeed09716f03b130`이다.

Checkpoint authenticity/fresh-JVM hardening final tree의 default full regression은 첫 실행에서 169 suites / 1,964 tests / failures 0 / errors 0 / skipped 0, aggregate JUnit XML 497.860초, Gradle wall 8분 28초로 clean pass했다. Production replay hash canonical 함수의 package 경계, B1 harness provenance context, Gradle `forkEvery=1`까지 모두 반영한 final executable tree에서 수행했으며 이후에는 generated artifact와 문서만 갱신했으므로 full을 반복하지 않았다. 최종 smoke는 1 suite / 1 test, JUnit 15.121초로 재라벨링·outcome·observation·checkpoint bytes 변조 거부와 synthetic non-official status를 확인했다. Cross-JVM probes는 각 1 suite / 1 test와 17.020/16.786초, canonical B1 dry-run은 17.361초로 모두 clean했다.

Phase-specific guard로 재고정한 공식 `runPhase13GB2Calibration`은 P1 cross-JVM → B1 dry-run → `forkEvery=1`인 4 fresh-JVM calibration workers → receipt-bound artifact finalizer 순으로 20분 4초에 clean pass했다. 서로 다른 JVM identity 4/4, authenticated fixture checkpoint 100/100, checkpoint payload digest 100/100, 12,000 calibration rows, 12,000 paired marginals, 100 exact replay, holdout 0과 SHA manifest 16/16을 확인했고 기존 B2 balance signal과 exact equality였다.

Final 13G-B3 final executable tree에서는 B1/B2/B3 contract focused tests가 clean pass했고 `runPhase13GB3Smoke`는 reserved holdout seed 없이 1 suite / 1 test, failures/errors/skipped 0, JUnit 14.262초, Gradle wall 26초로 통과했다. 이어 default full regression은 첫 실행에서 170 suites / 1,969 tests / failures 0 / errors 0 / skipped 0, aggregate JUnit XML 488.240초, Gradle wall 8분 23초로 clean pass했다. Phase-specific guard에 맞춘 B2 재고정은 20분 4초, frozen contract 생성은 16초, one-time `runPhase13GB3FrozenHoldout`은 22분 32초에 성공했다. 이후 executable source/resource/Gradle은 바꾸지 않고 artifact 검증과 문서만 갱신했으므로 full을 반복하지 않았다.

Final 13G-B hardening은 3 suites / 14 focused tests, failures/errors/skipped 0으로 통과했다. Actual registry/RealDraft/Spring/HTTP wiring의 fixed-seed parity와 synthetic 6,400-row full-write/negative E2E를 확인했다. 독립 Java 17 compile/run은 두 output directory에서 같은 manifest `bd9a9cf3b089cfc76fceb0311094c1b70232278404f5675c42d89849d927bc98`와 7/7 byte-identical files를 만들었고 final manifest의 6/6 raw SHA가 통과했다. 이 후속 작업은 production Java/resource/Gradle/shared fixture를 바꾸지 않은 isolated test-side consumer와 문서 변경이므로 위 B3 clean full을 재사용했다.

Pre-Jungle V2 determinism hardening에서는 세 번째 full regression이 필요했다. 첫 clean full 뒤 별도 JVM artifact oracle이 unordered `PlayerSkill` set iteration으로 seeded realization draw-to-skill 배정이 달라지는 production 결함을 발견했고, 이를 고친 두 번째 clean full 뒤 다시 별도 JVM에서 Champion Power tag summary ordering이 timeline hash를 바꾸는 독립 production 결함을 발견했다. 두 문제 모두 single-JVM focused/full suite만으로 검출할 수 없었으므로 canonical ordering 수정과 cross-JVM 후보 2회 exact equality를 먼저 확정한 뒤 당시 final full을 실행했다.

Pre-Jungle V2 생성 당시 production source/resource/build guard는 456 files / `b7965a1d1ebb9d76f298bc65e957da79c4e7cf2a3d0df35a6eca29ebaa0ab350`으로 동일했다. 당시 공식 V2를 새 JVM에서 같은 SHA로 재생성한 기록은 historical baseline provenance로 계속 보존한다.

테스트/diagnostic 실행 경계는 [Testing](development/testing.md), player contract는 [Player System](architecture/player-system.md)과 [Player Data Schema](reference/player-data-schema.md)를 참고한다.

## Last Updated

2026-08-23 (Asia/Seoul)
