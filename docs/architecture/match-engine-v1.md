# Match Engine V1 Contract

## Purpose

`MatchEngineV1`은 Final 13G-B production decision에서 승인된 현재 runtime을 실제 경기 기능이 사용할 수 있는 하나의 application boundary로 고정한다. Draft 결과를 다시 계산하거나 caller가 profile과 gameplay boolean을 조합하게 하지 않고, 완성된 두 roster와 final Draft, seed를 immutable input으로 받아 immutable result와 timeline, execution provenance를 반환한다.

이 freeze는 Economy/Tempo candidate를 production으로 승격한 작업이 아니다. V1의 유일한 authoritative policy는 다음과 같다.

| 항목 | 동결 값 |
| --- | --- |
| Contract schema | `MATCH_ENGINE_CONTRACT_V1` |
| Policy | `MATCH_ENGINE_V1_BASELINE_PRODUCTION_POLICY` |
| Policy hash | `61ec36e4ec36a3693a7fd34f9acbd018f615115dda45b558580f1ee7ff1a02a5` |
| Runtime profile | `BASELINE_V1` |
| Configuration hash | `c8cc557bd721228c473e30d31b7258510f9608a18098578bc1da36e603536215` |
| Gameplay rules | `MATCH_SIMULATOR_PRE_JUNGLE_RULES_V2` |
| Current engine implementation | `MATCH_SIMULATOR_ENGINE_IMPLEMENTATION_V8` |
| Matchup / Composition / Jungle contribution | `OFF` / `OFF` / `DISABLED_NOT_INTEGRATED` |
| Economy / Tempo candidate activation | `false` / `false` |

`SimulationOptions.productionDefaults()`는 Matchup `GEOMETRIC_V2`, Composition `PRODUCTION_V2`를 사용하는 저수준 constructor default다. 이름이 비슷하더라도 Match Engine V1의 authoritative application policy가 아니며 V1 facade는 이를 선택하지 않는다.

이 표는 현재 application policy를 설명한다. 아래 Freeze Evidence는 Match Engine V1 경계를 처음 고정했을 때의 historical V6 artifact이며 재생성하지 않는다. 이후 production implementation은 player ratings의 전투·오브젝트·운영 반영과 ability profile projection을 포함한 V8로 진화했지만, retained `BASELINE_V1`, configuration/policy, candidate 비활성화와 HTTP 경계 의미는 유지된다. 현재 V8 실행 증거는 별도의 Real Match API frontend handoff에 결속한다.

## Immutable Input

`MatchEngineV1Input`은 다음 gameplay input을 한 번에 검증하고 고정한다.

- BLUE/RED team identity와 각 팀의 정확한 TOP/JUNGLE/MID/ADC/SUPPORT 5명
- stable `PlayerId`, position, 전체 rating snapshot과 champion proficiency snapshot
- stable player slot에 연결된 10개의 `ChampionId` final assignment
- ordered Draft decisions, 양 팀 ban/pick, final role, Draft Meta와 legal-role hash
- Hard Fearless exclusion과 series game number를 포함한 series-history-before identity
- roster identity, match identity, match seed와 authoritative production-policy requirement

입력 생성의 정상 경로는 `MatchEngineV1InputFactory`가 authoritative `FinalDraftResult`를 변환하는 것이다. V1 실행 중 재-Draft하지 않는다. 누락·중복 player/position/champion, roster/assignment/Draft hash 불일치, illegal champion-role, candidate policy 요청은 simulator와 seeded `Random` 생성 전에 거부한다.

`inputHash`는 명시적 순서의 gameplay field로 계산한다. Player/team display label은 gameplay identity에서 제외되므로 nickname이나 표시 문구를 바꿔도 같은 구조화 입력의 hash는 변하지 않는다.

## Execution and Series Commit

`MatchEngineV1.execute`는 caller가 gameplay profile이나 candidate boolean을 전달할 수 없는 facade다. 변경할 수 있는 것은 observational `SimulationInstrumentation`뿐이며 diagnostics ON/OFF는 gameplay input identity, Random 소비와 timeline을 바꾸지 않는다. 실행마다 fresh configured simulator와 match-scoped state를 사용한다.

`RealDraftMatchOrchestrator.orchestrateV1`은 기존 Real Draft 경로에 additive하게 연결된다.

```text
team/series preflight
  -> one authoritative Draft and final assignment
  -> immutable MatchEngineV1Input
  -> MatchEngineV1 execution and projection
  -> immutable output/provenance validation
  -> completed Draft identity series commit
```

Preflight, simulation, projection 또는 output validation이 실패하면 caller-owned `SeriesDraftHistory`에는 commit하지 않는다. 성공한 경기만 기존 idempotent completed-Draft 경로로 반영한다.

## Immutable Output

`MatchEngineV1Output`은 다음을 항상 함께 반환한다.

- `MATCH_RESULT_SUMMARY_V1`: winner/end reason/duration, 양 팀 최종 상태, stable player별 champion/KDA/CS/gold/XP/level
- final Draft와 final assignment identity
- `MATCH_ENGINE_TIMELINE_V1`: structured events와 모든 structured snapshots의 deep immutable copy
- non-null `SimulationExecutionProvenance`: configuration/resource/replay/timeline identity와 Random fingerprint. V1 replay identity는 legacy 실행 입력 hash와 전체 `inputHash`를 다시 결속해 동적 rating/proficiency snapshot까지 포함한다.
- input, simulator timeline, structured timeline, output hash와 각 hash algorithm/scope

최종 summary는 event를 다시 세어 만들지 않고 마지막 structured snapshot에서 투영한다. Summary action event와 그 전투의 `KILL` event를 별도 전투 두 번으로 세지 않으며, assistants는 `KILL` event의 structured stable IDs로 유지한다. Display message는 표시용일 뿐 action, participant, winner 또는 reward 판단에 사용하지 않는다.

`outputHash`는 policy, input, final result/Draft, structured timeline, replay/resource provenance와 Random fingerprint를 결합한다. Hash 자신, display name/message, legacy display-sensitive simulator timeline hash는 V1 gameplay identity 범위에서 제외한다. `hasValidOutputHash(...)`는 실제 structured timeline에서 hash를 먼저 다시 계산해 저장된 `structuredTimelineHash`와 비교하고, 그 검증이 성공한 경우에만 최상위 output envelope hash를 확인한다. 따라서 event/snapshot 내용이 바뀌고 이전 child hash만 남은 재구성 output은 거부한다.

Nexus 파괴 종료는 반드시 winner가 있고, safety timeout은 winner가 `null`이다. Timeline의 winner/end reason/duration은 result summary와 exact equality여야 한다.

## Compatibility Boundary

기존 `MatchSimulator.simulate`, `simulateObserved`, legacy `RealDraftMatchOrchestrator` overload와 기존 response field는 유지한다. `simulateStructuredObserved`와 V1 input/output/orchestration만 additive하게 추가했다. 같은 Real Draft fixture와 seed에서 legacy 경로와 V1 경로는 Draft, complete legacy timeline, Random fingerprint와 replay hash/algorithm을 제외한 provenance field가 exact equality다. V1 replay hash는 legacy replay hash에 전체 `inputHash`를 추가 결속하므로 의도적으로 legacy 값과 다르다.

현재 `POST /api/matches/simulate`는 계속 `DummyDataFactory`와 legacy HTTP response를 사용한다. Match Engine V1을 HTTP나 frontend에 노출하거나 기존 demo response를 교체하지 않았다. Career/Save/Season, BO3/BO5 series service와 persistence도 이 freeze 범위에 포함되지 않는다.

## Historical Freeze Evidence

Freeze artifact는 V6 당시 `backend/build/reports/match-engine-v1-freeze/`에 생성됐다.

- `match-engine-v1-contract.json`
- `match-engine-v1-production-policy.json`
- `match-engine-v1-input-contract.json`
- `match-engine-v1-output-contract.json`
- `match-engine-v1-fixed-fixtures.json`
- `match-engine-v1-cross-jvm-verification.json`
- `match-engine-v1-freeze-summary.json`
- `SHA256SUMS.txt`

Manifest는 위 JSON 7개를 묶으며 SHA-256은 `1f5bc20c347d25d833e822325de1fa294dc61d38c55da121ea30d15ab70a0728`이다. Raw checksum은 7/7 통과했다. 두 fresh JVM의 canonical output/summary/verification은 exact equality이며, 실제 Real Draft legacy↔V1은 gameplay/Random/common provenance field가 exact이고 V1 replay identity만 전체 `inputHash` 결속으로 확장된다.

Freeze production source identity는 480 files / `5bb14af3eab33ceb5c9b6fed6f88d7bfa421b212663ac94e99067f05fdcafac6`다. Final 13G-B가 승인한 historical source identity 472 files / `68edbcb7393c9a54c0888a4f27a4e286774306675dce48991554fd22dcb2ddac`는 decision 당시 audit evidence로 그대로 보존한다. 두 값이 다른 것은 승인된 runtime semantics나 resource를 다시 튜닝한 것이 아니라 V1 application contract source 8개를 추가하고 replay/output 무결성 계약을 강화한 결과다.

Final 13G-B evidence manifest `bd9a9cf3b089cfc76fceb0311094c1b70232278404f5675c42d89849d927bc98`와 resource provenance `64ab1be3fdfe8d6660648ac634b52a86a5693d264bfbe707153dac9c17d39b4f`는 변경하지 않았다. 이 작업에서는 calibration, holdout, baseline 생성과 frontend build를 실행하지 않았다.

## Next Integration Steps

1. V1 request/response를 사용하는 Real Match API를 별도 additive endpoint로 설계한다.
2. 실제 roster/Draft 선택과 immutable result/timeline playback을 frontend에 연결한다.
3. caller-owned 임시 series history를 BO3/BO5 lifecycle과 영속 Save/Career 경계로 승격한다.
4. Economy 수정이나 Tempo V2가 필요하면 기존 B3 seed를 재사용하지 말고 새 candidate identity, tolerance, calibration과 fresh holdout을 만든다.
