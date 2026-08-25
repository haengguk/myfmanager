# Matchup V9 구조물 영향 Attribution 및 Acceptance Contract V1

이 문서는 `MATCHUP_V9_STRUCTURE_EFFECT_ATTRIBUTION_AND_ACCEPTANCE_CONTRACT_REDESIGN`의 결과를 기록한다. 이번 작업은 Matchup 수치를 조정하거나 production profile을 바꾸는 작업이 아니라, V9 구조물 시스템에서 Matchup 효과가 남긴 최종 상태 차이를 중요도별로 분해하고 다음 공식 재검증에 사용할 acceptance contract의 형태를 제안하는 진단 작업이다.

최종 판정은 `MATCHUP_V9_STRUCTURE_ATTRIBUTION_BLOCKED_BY_DIAGNOSTIC_GAP`이다. 최종 구조물 HP와 파괴 진행 차이는 충분히 관측했지만, 개별 Matchup contribution의 적용 시각·position·context가 structured diagnostic에 남지 않아 public divergence까지의 인과를 증명할 수 없었다. 따라서 gameplay tuning 없이 곧바로 fresh eligibility requalification으로 진행하지 않으며, 선행 `RECOMMEND_BASELINE_V1`과 실제 production `BASELINE_V1`을 그대로 보존한다.

## 선행 evidence 검증과 legacy 31.5%

선행 artifact `backend/build/reports/match-engine-v9-matchup-composition-requalification-v1/`의 contract, 200개 checkpoint와 sidecar, canonical raw 3,600행, consumed holdout 1,200행, fresh-JVM equality, recommendation과 SHA manifest를 read-only로 다시 검증했다. 18개 manifest entry는 모두 원본 SHA와 일치했고, checkpoint projection에서 canonical raw row를 byte-for-byte 재구성했다.

검증된 실행 identity는 다음과 같다.

| 항목 | 값 |
| --- | --- |
| 현재/진단 Source HEAD | `66fe9b385423e9fa3623a463347d0c78a650e33a` |
| 선행 V9 artifact 생성 source revision | `c2814b63b6fa40487f893c510fbe5868e508724a` |
| Engine / rules | `MATCH_SIMULATOR_ENGINE_IMPLEMENTATION_V9` / `MATCH_SIMULATOR_PRE_JUNGLE_RULES_V3` |
| Production profile / configuration | `BASELINE_V1` / `c8cc557bd721228c473e30d31b7258510f9608a18098578bc1da36e603536215` |
| Matchup candidate configuration | `58714464c19a2cffd108d47a93a0909126513c8bb10cb0e19bbd87f8e78532ec` |
| Production policy hash | `fb6b37ba770af03c176ff00bdbe683afb1e2701473461ceef6cd808bf5e970e5` |
| 선행 production source identity | 517 files / `7ec648bc98d993e9172d2bf0e2317eaafa9f30f1e1228501b7e49b3371ae40b6` |
| 선행 recommendation | `RECOMMEND_BASELINE_V1` |

기존 `31.5%`의 정확한 뜻은 consumed holdout의 400 paired match 중 126개에서 최종 canonical structure state signature가 달랐다는 것이다. 구조물이 31.5% 더 파괴됐다는 뜻도, 구조물 버그가 31.5% 발생했다는 뜻도 아니다. 이 signature는 파괴 여부뿐 아니라 전체 구조물 snapshot과 잔여 HP까지 포함하므로 HP 한 곳만 달라도 changed가 된다.

기존 raw row만으로는 changed 126쌍, 그 안의 winner changed 16쌍, objective changed 30쌍, changed pair 평균 경기 시간 차이 3.254초, 전체 damage event `+32`, destruction `+36`, siege start `+6`, stop `+20`을 확인할 수 있었다. changed pair의 first-tower lane은 BOT 29 / MID 38 / TOP 59였고 first-tower identity가 달라진 pair는 24개였다. fixture·team·side concentration도 복구했다.

반면 기존 artifact에는 개별 구조물 HP/alive component, 억제기·base-open·Nexus milestone, inhibitor/Nexus turret respawn history, 그리고 Matchup 적용 시각·position·context가 없었다. 따라서 legacy hash에서 이 정보를 역추론하지 않고 새 attribution-only diagnostic을 만들었다.

## Frozen attribution contract

첫 match 실행 전에 contract와 schedule을 canonical JSON 및 SHA-256으로 동결했다. 기본 suite에서 generated predecessor report를 읽는 evidence test를 제외하는 최종 `diagnostic` tag까지 반영한 current-source contract SHA-256은 `84d96de3bbcf0460481593eae6d7909b4ebd8fc0ae8ccf4b44a63d6240c4b477`이고, attribution harness identity는 8 files / `6db556133c8102e1905d3d0645af0a7ec61d68fa810fa7f42c905aa627f48e4a`다.

| 계약 항목 | 값 |
| --- | ---: |
| Real LCK fixture | 100: G1 primary 90 + Hard Fearless G2 10 |
| Fixture당 seed | 4 |
| Profile | `BASELINE_V1`, `MATCHUP_ONLY_CANDIDATE_V1` |
| Match row | 800 |
| Paired comparison | 400 |
| Seed namespace | `MATCHUP_V9_STRUCTURE_ATTRIBUTION_DIAGNOSTIC_SEEDS_V1` |
| Seed 상태 | `CONSUMED_AS_DIAGNOSTIC_NOT_HOLDOUT` |

Final 13G-B historical 3,200 seeds, V9 calibration 800 seeds, consumed holdout 400 seeds와의 overlap은 모두 0이다. 알려진 future reserved seed는 없으며 이 400개 attribution seed는 실행 즉시 diagnostic/calibration으로 소비돼 향후 공식 holdout으로 승격할 수 없다. Fixture의 roster, 20-turn Draft, bans/picks, final role assignment와 Hard Fearless history를 한 번 준비한 뒤 모든 seed/profile이 동일한 frozen Draft를 공유했다.

4개 JVM shard가 core 800 matches를 실행했다. 추가로 fixture별 첫 seed의 baseline replay 100건과 diagnostics OFF인 두 profile 200건을 실행해 총 1,100 simulations을 관측했다. Core paired input은 전부 exact였고 replay 100/100, diagnostics ON/OFF timeline·Random 200/200이 exact였다.

## 최종 상태 severity

Primary bucket은 mutually exclusive이며 한 pair가 여러 차이를 가질 때 가장 높은 severity를 사용한다. 실제 component 차이는 별도 multi-label로 보존한다.

| Primary severity | Pair | 비율 |
| --- | ---: | ---: |
| `EXACT` | 269 | 67.25% |
| `HP_ONLY` | 104 | 26.00% |
| `LANE_TOWER_PROGRESSION` | 1 | 0.25% |
| `INHIBITOR_PROGRESSION` | 15 | 3.75% |
| `NEXUS_TURRET_PROGRESSION` | 1 | 0.25% |
| `NEXUS_OR_ENDING` | 10 | 2.50% |

최종 canonical state가 하나라도 다른 pair는 131/400, 32.75%였다. 이 중 HP-only가 104개이고 실제 파괴·진행 component가 달라진 pair는 27/400, 6.75%다. 즉 legacy-style changed 비율 대부분은 파괴 단계 차이가 아니라 잔여 HP 차이였다. 다만 15개 inhibitor progression과 11개 individual Nexus-turret alive difference, 10개 Nexus alive difference가 관측됐으므로 HP-only라는 설명만으로 전체 영향을 축소할 수도 없다.

Multi-label component pair 수는 outer tower alive 27, inner tower alive 27, inhibitor turret alive 26, inhibitor alive/respawn state 20, individual Nexus turret alive 11, individual Nexus turret HP 19, Nexus alive 10, towers-destroyed count 25, inhibitors-remaining 9, Nexus-turrets-remaining 3이다. 26개 pair가 복수 severity label을 가졌으며 primary count와 중복 계산하지 않았다. Outer/inhibitor/Nexus 계층의 max-health 차이는 0이었다.

## 시간, source, lane 및 siege 변화

| Paired milestone/event | 다른 pair 수 |
| --- | ---: |
| First tower any / time / lane / source | 21 / 21 / 8 / 10 |
| First inhibitor 또는 base-open / time / lane / source | 28 / 28 / 16 / 20 |
| First Nexus turret / source | 28 / 19 |
| Nexus destruction / source | 28 / 20 |
| Structure damage event count | 25 |
| Structure destroyed event count | 24 |
| Persistent siege start / stop / reconstructed duration | 28 / 27 / 28 |
| Inhibitor respawn history | 17 |
| Nexus turret respawn count | 13 |

400쌍 모두 각 milestone에 도달했다. Baseline 대비 candidate의 paired 평균 milestone delta는 first tower `+0.875s`, first inhibitor/base-open `+5.425s`, first Nexus turret `+6.95s`, Nexus destruction `+5.825s`였다. 네 지표의 P95 paired delta는 모두 0초였지만 개별 범위는 first tower `-340..+410s`, first inhibitor/base-open `-780..+1160s`, first Nexus turret `-680..+940s`, Nexus destruction `-1140..+1080s`였다. 평균만으로 tail pair의 큰 양방향 이동을 숨기지 않는다.

Aggregate event는 baseline → candidate에서 structure damage `12,350 → 12,392`(+42), destroyed `5,550 → 5,558`(+8), persistent siege start `6,499 → 6,530`(+31), stop `8,131 → 8,158`(+27), inhibitor respawn `234 → 238`(+4), Nexus turret respawn `176 → 179`(+3)이었다. Structured siege lifecycle/action ID를 짝지어 재구성한 지속시간은 `136,480 → 137,270s`(+790s)다. 이 지속시간은 gameplay input이 아닌 observational reconstruction이다. 세부 source×lane×side 분포는 `structure-timing-source-lane-summary.json`에 canonical map으로 보존했다.

## Winner, objective, side와 concentration

Blue win-rate delta는 0.0pp였다. Winner changed는 10/400(2.50%)이고 방향은 BLUE→RED 5, RED→BLUE 5로 정확히 균형이었다. Objective changed는 28/400(7.00%), 평균 경기 시간 delta는 `+5.825s`, P95는 0초였으며 paired 최소/최대는 `-1140/+1080s`였다.

최종 상태 component가 달라진 defending side count는 BLUE 75, RED 85이고, 실제 progression component는 BLUE 27, RED 26이었다. Progression pair는 양쪽 모두 달라진 경우가 26, BLUE-only 1, RED-only 0이었다. Matchup edge perspective mismatch는 0이다. 이 표본에서는 한쪽 winner-flip 독점이나 명백한 progression side 독점이 관측되지 않았다.

모든 팀 exposure는 80 pair로 동일했다. Any-final-state rate는 BRO 25.00%에서 DNS/NS 41.25% 사이, progression rate는 GEN 1.25%에서 BFX/HLE/KT 10.00% 사이였다. 비교적 exposure가 큰 champion 관측으로 Ziggs 70/176 any 및 15/176 progression, Naafiri 44/128 및 15/128, Gnar 43/120 및 10/120, Azir 26/56 및 3/56, Taliyah 21/52 및 5/52가 있었다. Sion처럼 8회뿐인 작은 표본의 높은 비율은 일반화하지 않는다. Team·player·champion·fixture concentration은 exposure observation일 뿐 원인 추정이 아니며, 현재 fixture의 안정된 team roster 때문에 player와 team 효과도 독립적으로 분리할 수 없다.

## Local attribution과 correctness

Candidate에서는 non-zero Matchup application 132,135회와 edge 합 `-7.2197828422`가 관측됐다. Baseline OFF contribution, Matchup direct Random call, edge perspective mismatch는 모두 0이다.

첫 public timeline 및 pressure divergence는 400/400에서 모두 30초였다. 첫 combat divergence는 400/400으로 평균 240초, 첫 economy divergence는 65/400으로 평균 약 460.46초, 첫 structure divergence는 387/400으로 평균 약 330.78초였다. 이 시간 순서는 Matchup이 실제로 작동하고 이후 timeline과 구조물 결과가 달라졌다는 reachability 관측이다. 그러나 application 자체의 시각·position·context가 기록되지 않아 `Matchup local contribution → combat/pressure → structure`의 개별 인과 사슬은 증명하지 못했다. 상태는 `CAUSAL_PROVENANCE_UNAVAILABLE`이다.

다음 exact correctness failure는 모두 0이었다.

- display-name identity 사용과 duplicate structured action
- invalid/non-finite/out-of-range HP 및 impossible respawn/state transition
- Nexus-turret ordering, post-finish mutation/event, timeout
- ineligible/duplicate structure path Random consumption
- Matchup direct Random, OFF contribution, perspective mismatch
- gameplay integrity와 SUPPORT FARM CS 위반

Diagnostic instrumentation은 complete timeline과 Random fingerprint를 바꾸지 않았고, same-seed replay와 fixture/profile paired identity도 exact였다. Validator는 structured event의 positive inhibitor `healthBefore`를 observable respawn으로 취급한다. V9은 300초 후 inhibitor가 살아나도 별도 `RESPAWNED` event를 발행하지 않기 때문에, 이를 불가능한 post-destroy attack으로 오인하지 않도록 diagnostic 해석만 보완했으며 production gameplay는 변경하지 않았다.

## V9 acceptance contract 제안

과거 Phase 13D Composition sanity fallback인 `structure changed <= 12%`는 참고값으로만 보존하고 V9 Matchup hard gate로 복사하지 않는다. 새 proposal은 다음 네 층을 분리한다.

1. Correctness exact gate: invalid HP, duplicate, Nexus order, post-finish, impossible respawn, display identity, ineligible/duplicate Random failure가 정확히 0이어야 한다.
2. Observational sensitivity: any final state, HP-only, event/timing/source/lane 변화를 Matchup reachability 관측값으로 보고 근거 없는 낮은 ceiling을 두지 않는다.
3. Gameplay-critical macro safety: lane/inhibitor/Nexus progression, ending, winner, objective, side asymmetry, mean/P95 duration을 각각 평가한다. 이번 calibration으로 통과하도록 숫자를 만들지 않고 `THRESHOLD_REQUIRES_PRODUCT_DECISION`으로 남긴다.
4. Causal/reachability: application > 0, OFF contribution 0, direct Random 0, perspective mismatch 0과 중요 public divergence의 local attribution을 요구한다. 이번 결과는 마지막 항목이 없다.

이 proposal은 `PROPOSED_FROM_ATTRIBUTION_CALIBRATION_NOT_OFFICIAL_ELIGIBILITY`다. 공식 threshold를 제품/게임 디자인 근거와 함께 먼저 정한 다음, 이번에 소비한 seed와 겹치지 않는 별도 frozen contract와 fresh calibration/holdout으로만 eligibility를 판단해야 한다.

## 판정과 다음 단계

현재 final progression 6.75%, balanced winner-flip 방향과 correctness exact pass만 보면 무조건 gameplay hardening이 필요하다고 단정할 근거는 없다. 반대로 inhibitor/Nexus progression이 실제로 존재하고 local-cause provenance가 빠져 있어 “tuning 없이 곧바로 fresh requalification”을 지지할 인과 증거도 부족하다.

따라서 다음 단계는 production gameplay tuning이 아니라 test-side structured diagnostic에 Matchup application의 simulation time, `TeamSide`, `Position`, champion/player identity, context와 contribution sign을 observational field로 추가하는 별도 계약이다. 그 evidence로 critical divergence의 local attribution과 product threshold를 결정한 뒤에만 non-overlapping fresh requalification을 설계한다. 이번 작업에서는 `MatchEngineV1Policy`, production/HTTP profile, Matchup/Composition/Jungle tuning, 구조물 gameplay, API/frontend를 변경하지 않았다.

## 산출물과 검증

공식 diagnostic artifact는 `backend/build/reports/matchup-v9-structure-effect-attribution-v1/`에 있다. Contract/source/seed binding, paired JSONL, severity/HP/progression/timing/source/lane/side/local/winner summaries, proposed acceptance contract, recommendation, analysis와 `SHA256SUMS.txt`를 포함한다.

최종 manifest raw SHA-256은 `5ee0cec10b464117421ce347235ec651b94daaaadb8e6d94b6ca39daf660410c`이며 전체 entry가 검증됐다. 최종 tag 분류 전에 생성한 tree는 `/tmp/matchup-v9-structure-effect-attribution-v1-pre-final-source-tag`에 보존했고 current-source tree와 비교했다. Contract/source/checkpoint/manifest 및 이를 인용한 analysis만 달랐으며, paired 400행과 severity/HP/progression/timing/side/local/winner/recommendation gameplay evidence는 byte-identical이었다.

실행한 검증은 다음과 같다.

- Contract/classifier/source/checkpoint/artifact canonicalization focused tests: 6 tests clean pass
- Structure/Match Engine V1/V9/Matchup resolver affected focused tests: clean pass
- Explicit 4-JVM attribution diagnostic: current-source aggregate task 13분 17초, 800 core rows, replay 100, instrumentation parity 200, clean pass
- Artifact finalizer: paired input 400/400, replay 100/100, instrumentation parity 200/200, exact gates clean
- Complete backend regression: 207 suites / 2,140 tests / failures 0 / errors 0 / skipped 0, aggregate JUnit XML 754.243초, Gradle wall 13분 8초, `BUILD SUCCESSFUL`

대규모 attribution test는 기본 `test`에서 제외했다. 선행 artifact를 직접 읽는 evidence test도 `diagnostic` tag로 분류해 generated report가 기본 correctness suite의 정답 입력이 되지 않게 했다.
