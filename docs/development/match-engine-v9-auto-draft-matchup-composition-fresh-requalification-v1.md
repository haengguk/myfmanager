# Match Engine V9 Auto Draft·Matchup·Composition fresh 재검증 V1

이 milestone은 production Auto Draft를 fixture/seed마다 정확히 한 번 만들고 동일 immutable input을 `BASELINE_V1`, `MATCHUP_ONLY_CANDIDATE_V1`, `FULL_SYSTEM_CANDIDATE_V1`이 공유하는 fresh 자격 검증이다. 최종 상태는 `MATCH_ENGINE_V9_AUTO_DRAFT_FRESH_REQUALIFICATION_BLOCKED_HOLDOUT_NOT_CONSUMED`이다.

## Production과 구현 결과

이 재검증 종료 당시 production profile은 계속 `BASELINE_V1`이었다. Engine은 `MATCH_SIMULATOR_ENGINE_IMPLEMENTATION_V9`, active rules는 `MATCH_SIMULATOR_PRE_JUNGLE_RULES_V3`, resource provenance는 `64ab1be3fdfe8d6660648ac634b52a86a5693d264bfbe707153dac9c17d39b4f`로 유지됐다. Matchup/Composition gain, 확률, timing, resource와 HTTP 기본 wiring은 바꾸지 않았다. 이후 별도 production activation decision은 이 문서의 blocked verdict를 holdout pass로 재해석하지 않는다.

Auto Draft post-review gap 세 건은 다음처럼 보완했다.

- 선택된 rank 2/3 champion을 `topAlternatives`에서 제외해 실제 대안만 남겼다.
- backend authoritative input이 20개 trace의 policy, weight/sum/draw bucket, rank/pool, context/history/roster/seed binding과 trace-set hash를 다시 검증한다.
- trace hash V2는 raw `double` last bit를 제거하고 fixed-point evidence와 schema/algorithm identity를 결속한다.

Matchup `GEOMETRIC_V2`는 match-scoped structured application provenance를 추가했다. 실제 consumer 입력 지점, simulation time, side/perspective, position/player/champion pair, context/stage, before/after/delta와 structured action ID가 있을 때만 그 ID를 기록한다. Resolver는 stateless이고 diagnostic은 gameplay/Random에 영향을 주지 않는다.

Composition V6의 scalar/non-scalar decomposition과 public action binding은 read-only predecessor로 유지했다. 이번 작업은 production tuning을 자동 변경하지 않았다.

## 동결 identity

| 항목 | 값 |
| --- | --- |
| HEAD | `9d87bfe610820457e3a9114bde05a342567b1156` |
| Contract SHA-256 | `3b535646f1bb6a3942f2e323aa3ccabb776b311ce5f9b9022f8f71c43b55e64b` |
| Combined source SHA-256 | `4622e700acb4643637f55f6ae7ff951e106f282dc8e960051a4157bb7523ad26` |
| Schedule SHA-256 | `2ae331a00765a5b44ce30ab0f6420fa93914dea02d9ca595a3f8c968537e2512` |
| Consumed-seed ledger SHA-256 | `b2115643fdb6011ab6a84f5f3da8fa217d28d44eff8a0cfd4e0c75f88f4617af` |
| Baseline configuration | `c8cc557bd721228c473e30d31b7258510f9608a18098578bc1da36e603536215` |
| Matchup-only configuration | `58714464c19a2cffd108d47a93a0909126513c8bb10cb0e19bbd87f8e78532ec` |
| Full-system configuration | `caaf76274dc148040b0a95eae1ed5181790b2fc840f45af9b109ea7951c1fd5d` |

Ledger는 Phase 13G, historical V9, Matchup, Composition V5/V6와 Auto Draft Variety fixed seeds를 source manifest/hash와 함께 읽었다. 이전 V6가 V5 seed를 재사용한 관계도 별도 새 소비로 부풀리지 않았다. Serialization preflight에서 일부 official seed가 시작된 최초 namespace는 전체 schedule을 폐기한 historical namespace로 ledger에 포함했다. 현재 official calibration은 별도 `RETRY_1_AFTER_SERIALIZATION_PREFLIGHT_FAILURE` namespace를 사용했다.

## 실행 구조와 결과

Fixture는 real LCK G1 90개와 Hard Fearless G2 10개다. Calibration은 fixture당 4 seeds이며 seed마다 production Auto Draft 1개를 생성한 뒤 세 profile이 같은 input/draft/assignment hash를 공유했다.

| 항목 | Calibration | Holdout |
| --- | ---: | ---: |
| Fixtures | 100 | 0 |
| Production Auto Drafts | 400 | 0 |
| Core match rows | 1,200 | 0 |
| Paired marginals | 800 | 0 |
| Same-profile replay checks | 300 | 0 |
| Instrumentation parity checks | 300 | 0 |
| Authenticated checkpoints | 100 | 0 |
| Worker receipts | 4 | 0 |

Calibration worker 네 JVM은 모두 성공했고 checkpoint raw-byte sidecar는 100/100 일치했다. 그러나 artifact-only finalizer의 fresh-JVM reload에서 첫 checkpoint canonical payload digest가 불일치했다. 저장 payload와 raw JSON tree 재계산 값은 모두 `bfb459355840cb92c29d74f962ca87f437277640c7e9d87ac8dc29eacc602227`이고 raw file SHA-256도 sidecar와 같았다. 파일 손상이 아니라 `PairObservation.divergenceActionIds`를 `Set.copyOf`로 저장·복원하면서 JVM별 iteration order를 canonical array 순서로 사용한 evidence serialization 결함이다.

## 판정 경계

Checkpoint/receipt/manifest integrity가 calibration operational gate의 선행 조건이므로 Matchup/Composition reachability, direct/indirect cause, winner/objective/structure/duration과 Auto Draft 분포를 공식 eligibility 근거로 승격하지 않았다. Candidate를 `NOT_ELIGIBLE`로 balance 판정한 것도 아니며, 둘 다 `BLOCKED_BY_CALIBRATION_EVIDENCE_INTEGRITY`다.

Holdout authorization 파일은 생성되지 않았고 `.started`도 없다. 따라서 holdout은 미소비이며 같은 seed를 resume/re-run할 수 있는 상태가 아니다. Source/harness 수정 후 같은 holdout seed를 쓰는 것도 금지된다.

Production은 바뀌지 않았고 machine-readable recommendation은 `BASELINE_V1` 유지다. 이 의미는 fresh calibration이 Baseline 안정성을 새로 승인했다는 뜻이 아니라, candidate evidence가 승격되지 못했으므로 기존 production policy를 그대로 둔다는 뜻이다.

## 검증

- Auto Draft/authoritative evidence/Matchup provenance/Match Engine/API focused tests: clean
- Multi-fixture fresh-JVM Draft probe A/B: byte-exact
- 강화 DRY_RUN smoke: replay, instrumentation, immutable Draft sharing, Matchup/Composition reachability, finalizer transforms clean
- Frontend `npm run build`: clean; 화면 flow 변경이 없어 Playwright는 생략
- 최종 complete backend regression: 217 suites / 2,194 tests / failures 0 / errors 0 / skipped 0, aggregate XML 721.031초, Gradle wall 12분 6초
- Official calibration workers: 4/4 성공, 약 16분 2초–16분 25초
- Calibration finalizer: evidence-integrity failure, holdout 0

Full regression 예산 초과 사유는 report의 `third-full-regression-justification.json`과 `fourth-full-regression-justification.json`에 보존했다. 첫 clean full 뒤 official serialization preflight가 shared diagnostic canonicalization 결함을, 그 다음 finalizer static preflight가 non-comparable `TreeMap` key를 발견했기 때문에 각각 source 재동결과 full regression이 필요했다.

## 선행 evidence와 다음 단계

Historical [V9 Matchup/Composition 재검증](match-engine-v9-matchup-composition-requalification-v1.md), [Matchup structure attribution](matchup-v9-structure-effect-attribution-v1.md), [Composition causality hardening](composition-v9-application-causality-hardening-v1.md)과 [Auto Draft Variety V1](auto-draft-variety-v1.md)은 read-only predecessor다. Historical verdict와 artifact hash는 덮어쓰지 않았다.

다음 requalification 전에 signed payload의 모든 unordered collection을 명시적 정렬 순서로 canonicalize하고, raw-tree 및 deserialize/reserialize digest를 여러 fresh JVM에서 사전 검증해야 한다. 그 뒤 이번 calibration을 consumed ledger에 추가한 새 versioned contract와 비중첩 calibration/holdout namespace를 만들어야 한다. 같은 calibration을 다시 읽어 우연히 통과할 때까지 finalizer를 반복하거나 같은 seed로 재실행해서는 안 된다.
