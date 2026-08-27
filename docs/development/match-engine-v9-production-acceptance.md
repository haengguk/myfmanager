# Match Engine V9 Production Acceptance

## 결정

최종 제품 결정은 `MATCH_ENGINE_V9_PRODUCTION_ACCEPTED_WITH_KNOWN_LIMITATIONS`이며 machine-readable acceptance status는 `PRODUCT_ACCEPTED_WITH_KNOWN_LIMITATIONS_NOT_STATISTICAL_HOLDOUT`이다. 공개 runtime은 계속 `PRODUCTION_MATCHUP_COMPOSITION_V1`이고 statistical holdout approval은 `false`다. 이번 작업은 새 calibration/holdout이나 balance 승인, Matchup causal lineage 완결을 뜻하지 않는다.

순서가 고정된 known risk는 다음 두 개다.

1. `MATCHUP_CAUSAL_LINEAGE_UNRESOLVED_399_OF_400_CALIBRATION_PUBLIC_DIVERGENCES`
2. `COMPOSITION_NEXUS_ENDING_SENSITIVITY_9_25_PERCENT_EXCEEDS_PROPOSED_7_5_PERCENT_TOLERANCE`

두 번째 값은 직전 paired calibration에서 Matchup-only와 Full의 final Nexus/ending signature가 9.25%의 pair에서 달랐다는 뜻이다. 넥서스를 9.25% 더 파괴했다는 의미가 아니고, 7.5%도 직전 proposed tolerance이지 보편적인 통계 기준이 아니다.

## Production 계약

| 항목 | 값 |
| --- | --- |
| Policy schema / ID | `MATCH_ENGINE_V1_PRODUCTION_POLICY_V3` / `MATCH_ENGINE_V1_MATCHUP_COMPOSITION_ACCEPTED_PRODUCTION_POLICY` |
| Policy SHA-256 | `78c3bb1cffe2cd90a1f7acab6923a1813fea40acd135186ff522eabf95d38493` |
| Runtime / configuration SHA-256 | `PRODUCTION_MATCHUP_COMPOSITION_V1` / `caaf76274dc148040b0a95eae1ed5181790b2fc840f45af9b109ea7951c1fd5d` |
| Engine / rules | `MATCH_SIMULATOR_ENGINE_IMPLEMENTATION_V9` / `MATCH_SIMULATOR_PRE_JUNGLE_RULES_V3` |
| Matchup / Composition | `GEOMETRIC_V2` / `PRODUCTION_V2` |
| Jungle clear / Economy / Tempo | `DISABLED_NOT_INTEGRATED` / `false` / `false` |
| Rollback | `BASELINE_V1` / `EXPLICIT_VERSIONED_POLICY_CHANGE_ONLY` / automatic fallback `false` |

공통 gameplay 13개 시스템은 모두 ON이다. Acceptance/risk/rollback metadata는 policy canonical hash에는 들어가지만 gameplay configuration hash에는 들어가지 않는다. 그래서 exact gameplay clone인 Full candidate와 Production의 configuration hash가 같을 수 있다.

Generic/legacy replay provenance hash는 gameplay configuration과 immutable simulator input을 결속하지만 profile alias를 제외한다. 공개 Match Engine V1은 이 legacy hash를 policy/profile requirement를 포함한 `MatchEngineV1Input.inputHash`와 다시 결속한다. 따라서 generic replay hash 단독으로 candidate와 production을 구분한다고 설명하지 않는다.

## Fixed Draft contract와 preflight

Diagnostic identity는 `T1_GEN_FIXED_DRAFT_COMPOSITION_ARCHETYPE_DIAGNOSTIC_V1`, namespace는 `PRODUCT_SANITY_ONLY_NOT_CALIBRATION_OR_HOLDOUT`이다. Auto Draft나 가짜 20-turn trace를 만들지 않고 final role assignment를 직접 고정했다. 밴은 final assignment 이후 Match Simulator input에 영향을 주지 않으므로 `banControl=NOT_APPLICABLE_AFTER_FINAL_ASSIGNMENT`다.

| Lineup | TOP | JUNGLE | MID | ADC | SUPPORT |
| --- | --- | --- | --- | --- | --- |
| POKE | rumble | nidalee | azir | varus | karma |
| ENGAGE/DIVE | camille | wukong | galio | kaisa | rakan |
| COUNTER/RESPONSE | sion | poppy | taliyah | xayah | braum |

모든 champion-role은 current catalog에서 legal이고 한 경기 duplicate champion은 0이다. T1/GEN의 실제 production roster를 `LckTeamAssembler`로 매 경기 새로 만들었다. T1/GEN × 세 lineup × 다섯 position = 30개 proficiency binding은 모두 authored entry이며 neutral fallback과 player/team/position/champion identity mismatch는 각각 0이다.

Static production evaluator full-precision 결과는 다음과 같다.

| Signal | 값 | 최소 계약 |
| --- | ---: | ---: |
| POKE `POKE_SIEGE` readiness | 0.8873159044193729 | 0.85 |
| ENGAGE `ENGAGE_CHAIN` readiness | 0.9416526424761142 | 0.90 |
| COUNTER `DISENGAGE` coverage | 0.9925 | 0.95 |
| COUNTER `PEEL` coverage | 0.9700000000000001 | 0.95 |
| COUNTER `FRONTLINE` coverage | 0.9400000000000001 | 0.90 |
| COUNTER `WAVE_CLEAR` coverage | 0.9275 | 0.90 |

받아치기는 단일 enum이 아니다. DISENGAGE, PEEL, FRONTLINE, WAVE_CLEAR, ZONE_CONTROL과 opponent interaction rule이 context별로 결합된 결과다.

두 scenario 각각 다음 네 orientation을 모두 사용했다.

1. `T1_ARCHETYPE_BLUE__GEN_COUNTER_RED`
2. `GEN_COUNTER_BLUE__T1_ARCHETYPE_RED`
3. `GEN_ARCHETYPE_BLUE__T1_COUNTER_RED`
4. `T1_COUNTER_BLUE__GEN_ARCHETYPE_RED`

Seed는 `9_270_001 + 104_729 × i`, `i=0..49`로 고정했다. 두 scenario × 네 orientation × `BASELINE_V1`, `MATCHUP_ONLY_CANDIDATE_V1`, `PRODUCTION_MATCHUP_COMPOSITION_V1` × 50 seeds = 1,200 core simulation이다. Replay 4회, instrumentation-only 2회, rollback oracle 4회는 core와 분리한 추가 unique simulation 10회다.

## Product-sanity 결과

아래 승률은 실제 T1/GEN 선수 rating/proficiency, Champion Power, champion Matchup과 Composition을 모두 포함한다. 서로 다른 lineup의 raw 승률을 순수 조합 효과나 현실 LCK 승률로 해석하면 안 된다.

| Scenario | Profile | Games | BLUE WR | T1 WR | Side-normalized archetype WR |
| --- | --- | ---: | ---: | ---: | ---: |
| POKE vs COUNTER | Baseline | 200 | 54.00% | 46.00% | 54.00% |
| POKE vs COUNTER | Matchup-only | 200 | 55.50% | 43.50% | 51.50% |
| POKE vs COUNTER | Production | 200 | 56.00% | 43.00% | 48.00% |
| ENGAGE vs COUNTER | Baseline | 200 | 50.50% | 45.50% | 52.50% |
| ENGAGE vs COUNTER | Matchup-only | 200 | 50.50% | 43.50% | 50.50% |
| ENGAGE vs COUNTER | Production | 200 | 50.50% | 41.50% | 54.50% |
| 합계 | Baseline | 400 | 52.25% | 45.75% | 53.25% |
| 합계 | Matchup-only | 400 | 53.00% | 43.50% | 51.00% |
| 합계 | Production | 400 | 53.25% | 42.25% | 51.25% |

동일 team/orientation/final assignment/seed의 paired 변화는 다음과 같다.

| 비교 | Winner change | Objective signature | Full structure state | Nexus/ending | 평균 duration delta |
| --- | ---: | ---: | ---: | ---: | ---: |
| Baseline → Matchup-only | 21/400 (5.25%) | 400/400 (100.00%) | 151/400 (37.75%) | 27/400 (6.75%) | -11.00초 |
| Matchup-only → Production | 25/400 (6.25%) | 46/400 (11.50%) | 49/400 (12.25%) | 30/400 (7.50%) | +1.45초 |
| Baseline → Production | 42/400 (10.50%) | 400/400 (100.00%) | 187/400 (46.75%) | 53/400 (13.25%) | -9.55초 |

Objective signature는 dragon/baron/elder의 structured 시간·action·decision/fight identity를 포함하므로 작은 downstream 경로 변화도 잡는다. 특히 Baseline→Matchup의 100%를 objective 승패가 모두 바뀌었다고 해석하면 안 된다. Winner, structure, Nexus/ending은 별도 field로 분리했다.

Matchup structured non-zero consumed application은 Matchup-only 44,747회, Production 44,743회다. Production의 scalar Composition application/consumption은 4,885회이며, 비-scalar OBJECTIVE_SETUP effect 454회를 포함한 structured effect application은 5,339회다. 모든 400 production pair에서 Composition consumer가 도달했다.

| Production context | Observations | Effect applications | Scalar consumed | Signed modifier mean | Min | Max |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| SKIRMISH | 6,221 | 3,563 | 3,563 | -0.010236085418333572 | -2.805474795809362 | 2.805474795809362 |
| TEAMFIGHT | 762 | 762 | 762 | -0.03127080387045981 | -3.0876983379298584 | 3.0876983379298584 |
| SIEGE | 16,150 | 230 | 230 | 0.27941939802813287 | -6.433624086041188 | 6.433624086041188 |
| BASE_DEFENSE | 330 | 330 | 330 | 0.13370509003644473 | -3.9502030119996094 | 3.9502030119996094 |
| OBJECTIVE_SETUP | 454 | 454 | 0 | 0.0 | 0.0 | 0.0 |
| SIDE_LANE | 0 | 0 | 0 | 0.0 | 0.0 | 0.0 |

OBJECTIVE_SETUP은 기존 non-scalar effect만 사용하고 scalar modifier는 승인되지 않았으며, SIDE_LANE scalar도 직접 적용되지 않는다. Structured public binding 분류는 direct 5,339, indirect 0, unresolved 0이다. 이 product-sanity 결과가 과거 Matchup calibration의 399 unresolved risk를 해소하지는 않는다.

Correctness는 timeout, domain/structure/Nexus/post-finish/SUPPORT CS 오류, orientation/duplicate/conflicting application, direct Matchup/Composition Random call, Jungle Economy/Tempo 실행이 모두 0이다. Same-seed replay 두 쌍과 instrumentation ON/OFF 두 쌍은 timeline/Random에서 exact했고, replay는 structured diagnostics까지 exact했다.

## BASELINE rollback

`BASELINE_V1` configuration SHA-256 `c8cc557bd721228c473e30d31b7258510f9608a18098578bc1da36e603536215`는 변경하지 않았다. Acceptance-time oracle은 다음 두 fixture를 explicit baseline으로 fresh state에서 각각 두 번 실행했다.

- GEN BLUE vs T1 RED, POKE vs COUNTER, seed 73
- T1 BLUE vs GEN RED, ENGAGE vs COUNTER, seed -73

두 fixture 모두 complete timeline/events/snapshots byte hash, winner/end reason/duration과 마지막 player/economy/reward state, Random fingerprint, structured diagnostics, timeline/output hash가 재실행 간 exact다. 이는 current acceptance tree의 explicit baseline replay oracle이다. Matching V9 pre-activation immutable output oracle이 없으므로 cross-commit byte parity를 증명한 것으로 과장하지 않는다.

| Fixture | Timeline SHA-256 | Output SHA-256 | Random draws |
| --- | --- | --- | ---: |
| GEN BLUE / T1 RED / POKE / seed 73 | `ff9801ac8999fd95dc79c56237396c0c89a6353b686d36a5face8200fc131514` | `485719da092c8f79f93f554cc11c121e30f3d45027c03a6e6ee9509c4ab5e8c6` | 4,221 |
| T1 BLUE / GEN RED / ENGAGE / seed -73 | `2f1780325cc5a39c5ae6fe7203de504574cac3a65068484544c860d21c4c46bd` | `d1fe32a1368ac533e84076f350c5e4b9a4aafd1b4b2f71f113c116d7211fac68` | 5,550 |

실제 rollback 절차는 (1) 새 versioned `MatchEngineV1Policy`가 `BASELINE_V1`을 authoritative profile로 선택하고, (2) policy hash/API identity를 갱신하며, (3) focused rollback oracle을 확인하고, (4) 필요한 final regression과 배포를 수행하는 것이다. Runtime exception이나 diagnostic warning이 baseline을 자동 선택하지 않는다.

## API와 frontend

Options production policy에는 V3 policy ID/hash, acceptance status, primary compatibility limitation과 ordered risk 2개, holdout false, runtime/configuration/engine/rules/Matchup/Composition/Jungle mode, rollback profile/mode와 automatic fallback false가 함께 노출된다. 개별 simulate 결과의 `integrity`에도 acceptance status, ordered risks, holdout false, rollback profile과 automatic fallback false를 넣었다. 기존 field는 제거·개명하지 않았다.

Frontend type과 strict LIVE validator는 두 additive block을 검증하며 holdout과 automatic fallback이 반드시 false인지 확인한다. Setup → Draft → playback → result 흐름이나 UI는 재설계하지 않았다.

## Artifact와 검증 경계

Candidate raw는 `backend/build/reports/match-engine-v9-production-acceptance-raw-candidate/`, candidate review output은 `backend/build/reports/match-engine-v9-production-acceptance-candidate/`에 생성한다. Clean full regression 뒤 raw-only finalizer A/B가 simulation 없이 같은 input을 검증·집계하고 byte-identical일 때만 `backend/build/reports/match-engine-v9-production-acceptance/`로 승격한다.

Artifact는 reviewed/current HEAD, production source tree, companion player identity/ratings/proficiency raw SHA, champion manifest/power/matchup/composition SHA, policy/profile/configuration/engine/rules, lineups/orientations/seeds와 rollback scope를 결속한다. 이 artifact는 historical baseline/calibration/holdout의 대체물이 아니다.

최종 official directory는 `backend/build/reports/match-engine-v9-production-acceptance/`다. Candidate 1,200+10회는 약 3분에 한 번만 실행했고, clean full 뒤 raw-only finalizer A/B는 약 4초에 gameplay simulation 0회로 byte-identical output을 만들었다. `SHA256SUMS.txt`의 12/12 entry가 통과했으며 manifest raw SHA-256은 `1b3094dc2f59b0c4fb32130c2e5b6bac8060d73c99f4c7c9c696561d59c316e1`이다.

## 최종 검증과 LIVE smoke

- Focused policy/API/acceptance/rollback: 4 suites / 22 tests, failures 0 / errors 0 / skipped 0, Gradle wall 1분 26초
- Frontend production build: 87 modules, failures 0, Vite 1.47초
- 최종 complete backend regression: 221 suites / 2,203 tests, failures 0 / errors 0 / skipped 0, aggregate JUnit XML 859.986초, 첫 실행 Gradle wall 14분 29초
- Artifact finalizer A/B 및 promotion: 약 4초, byte-identical, manifest 12/12

현재 tree를 별도 backend `localhost:8086`과 frontend `localhost:5174`에서 실행했다. 기존 사용자 프로세스가 점유한 8080/8085/5173은 종료하거나 변경하지 않았다. Options와 simulate는 모두 HTTP 200이었고 simulate는 실제 `Content-Encoding: gzip`과 `Vary: Accept-Encoding`을 유지했다. Options `productionPolicy`와 simulate `integrity`가 공유하는 V3 policy/acceptance/ordered risk/holdout false/rollback metadata가 일치했고 runtime은 `PRODUCTION_MATCHUP_COMPOSITION_V1`, Matchup `GEOMETRIC_V2`, Composition `PRODUCTION_V2`, Jungle Economy/Tempo `false`였다.

공개 API는 고정 archetype이 아니라 Auto Draft를 그대로 사용했다. GEN(BLUE) 대 T1(RED), seed 73은 Draft 20 decisions / 10 final assignments, T1 승리, `NEXUS_DESTROYED`, 2,320초, event 376개, snapshot 233개, Random draw 4,866, output SHA-256 `4774cec14fcd0606c00421b7fce29f8159f4950a77814212cd3417b30a2d617b`를 반환했다. 브라우저에서 Setup → Draft → Playback, x30 speed, 종료 시점 seek, Result와 실제 snapshot 233개 골드 차트를 확인했다. 최종 clean session은 console errors 0 / warnings 0, page/runtime validation error 0, reference fallback 0이었다. 이 한 경기는 API/화면 호환성 smoke이지 balance 표본이 아니다.

검증 후 전용 브라우저와 8086/5174 프로세스, 임시 `frontend/.env.local`만 정리했다. Git add/commit/push는 수행하지 않았고 사용자 소유 untracked `prompts/`, local player companion resource, baseline JSON과 historical artifact를 보존했다.

## 해석과 다음 단계

이 결과는 포킹이 받아치기에게 항상 지거나 돌진이 항상 이긴다는 선언이 아니다. 50 seeds의 product sanity에서 POKE archetype WR은 Composition 추가 뒤 51.5%→48.0%, ENGAGE archetype WR은 50.5%→54.5%로 관측됐지만, 선수 binding과 Champion Power/Matchup까지 함께 들어간 결과다. 승률 방향이나 0이 아닌 차이를 correctness assertion으로 고정하지 않았다.

다음 단계는 production structured telemetry에서 winner/side, structure·Nexus, duration과 integrity를 관찰하고, 새 versioned·비중첩 evidence 계약이 필요할 때만 Matchup causal lineage 연구나 Composition sensitivity 관찰을 별도 수행하는 것이다. Jungle Economy/Tempo와 clear contribution은 이번 승인으로 활성화되지 않았다.
