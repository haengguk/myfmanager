# Composition V9 application and causality hardening

## Verdict

현재 authoritative evidence-repair 판정은
`COMPOSITION_V9_CAUSALITY_AUDIT_HARDENED_AND_V5_EVIDENCE_REPAIRED`다.
선행 V5 판정 `COMPOSITION_APPLICATION_CAUSALITY_HARDENED_READY_FOR_FRESH_REQUALIFICATION_DESIGN`은
historical diagnostic 결론으로 보존한다.

이 판정은 Composition의 production eligibility나 activation 판정이 아니다. Authoritative application policy는 계속 `BASELINE_V1`이고 Matchup/Composition/Jungle contribution은 각각 `OFF`/`OFF`/`DISABLED_NOT_INTEGRATED`다.

기준 HEAD는 `b49773822dc5eb030a166d4e7bc93180dc46f470`이다. 최종 V5 contract가 동결한 production source hash는 `66e5f3f2148f47029e14b2395b692bea1e949284726fcf22478fb07282505b3d`, harness source hash는 `73544c08463f7d4bddc991b041834bc3bf49beed982986f43b9b0aa8755f3a6a`다.

## V6 causality audit hardening and V5 evidence repair

기준 커밋 `4c02debd19b5e0286cd586de02f106b44eb30499`에서 V5의 gameplay 수치나 eligibility를 새로 평가하지 않고 감사 계약과 증거 무결성만 복구했다. 최종 artifact는 `backend/build/reports/composition-v9-application-causality-hardening-v6/`이며, 기존 V5 root manifest raw SHA-256 `cc5d02b4c97e636cf927b07275ffcaff8eb4ec0badaaa307883d5391a5b45af9`와 `EVIDENCE_REPAIR_REUSES_V5_SEEDS_NOT_FRESH_ELIGIBILITY` 관계를 고정한다. V5 schedule의 400 distinct seeds를 재사용했고 `freshEligibilityEvaluated=false`, `freshSeedConsumed=false`다.

대형 freeze/4-shard worker/finalize JUnit은 `diagnostic` tag를 가지며 기본 `test`에서 제외된다. 최종 default suite receipt는 2,169 test cases 중 forbidden Composition class 실행 0건을 증명한다. 빠른 contract/unit/rejection test와 exact-selector proof test는 기본 또는 전용 focused lane에 남는다.

Diagnostic source identity는 파일명 prefix가 아니라 runner의 explicit dependency manifest를 사용한다. Composition manifest는 runner/contract/worker/gate, real-match harness와 executor, instrumentation executor, attribution classifier, proof source, 공통 receipt/manifest utility, production provenance/orchestration/policy와 해당 Gradle contract section을 포함한 23개 dependency의 logical path, raw SHA-256, CRLF→LF canonical SHA-256을 기록한다. Harness hash는 `c30b1b91c733e5af23ed9d7d3be97e5e3dc4785ab81d4770b414a53e117ae740`, production source guard는 `983daadb96bbcb53ea943f6abbf1aa4d69942c33c85f93dd1592d4610bcfe229`다. Runner가 새 direct/shared dependency를 사용하면 manifest 목록도 함께 갱신해야 하며, 무관한 test 파일은 포함하지 않는다. 중복 logical path, 누락 파일/section과 content/hash mismatch는 freeze 전에 거부한다. Matchup attribution manifest도 공통 `PairedDiagnosticAuditGate`, proof/manifest utility와 변경된 Gradle section을 포함한다.

Focused invariant evidence는 문자열 label 대신 두 exact JUnit receipt다. 각 receipt는 class/method selector, source logical path/raw SHA, 고정 Gradle task/selector, production guard, 1/0/0/0 결과와 canonical PASS hash를 결속한다. Raw XML set hash는 관측값으로 별도 보존한다. 존재하지 않는 selector, source/production/task/result/payload 변조와 다른 task 재라벨은 rejection test가 거부한다.

Public causality는 event/snapshot/objective/structure scope, deterministic final-timeline ordinal, time, action/parent action, event type, `CombatSource`, lane과 before/after structured payload hash를 기록한다. Exact direct cause는 complete event identity가 일치하고 non-null `actionId`가 있을 때만 인정한다. Snapshot-only 차이는 `INDIRECT_CAUSE` 또는 `UNRESOLVED_SNAPSHOT_CAUSE`이며 direct coverage에 포함하지 않는다. Final 400 pairs 중 public/event divergence 59건은 모두 exact direct cause였고 actionId null 0, indirect/unresolved/unexplained 0, exact event coverage 100%다. 같은 tick의 다른 changed attempt, altered binding identity, nested receipt/checkpoint/root manifest mutation은 focused rejection test에서 거부된다.

Objective provenance는 실제 initiative/owner의 `routingPerspectiveSide`와 canonical `scoreOrientation=BLUE_MINUS_RED`를 분리한다. 486 applied objective traces는 BLUE initiative 249, RED 237, BLUE→RED flip 1, RED→BLUE flip 2, unchanged 483, orientation mismatch 0이었다. Gameplay winner와 secure/capture Random을 다시 뽑지 않고 baseline/runtime projection이 같은 sample을 공유한다.

Teamfight/Siege/Base Defense의 기존 support-tool Composition component는 잔차가 아니라 explicit pre-clamp component로 전달한다. Post-clamp delta는 frozen scalar + exact existing non-scalar + differential clamp effect로 검증한다. SKIRMISH 3,224건은 existing non-scalar와 clamp effect가 모두 bit-exact positive zero였다. Counterfactual `CombatProgressionEvaluator`, Champion Power와 Matchup은 pure 계산과 actual record 경계를 분리해 pure 전후 execution stats/gameplay snapshot이 exact하고 runtime 경로만 1회 기록된다.

Final correctness/replay/instrumentation gate는 모두 0 mismatch였다. Applied traces 4,967건의 consumer/public binding/actionId가 완전했고 scalar calculated/consumed 4,481/4,481, duplicate/conflicting binding, perspective/decomposition, direct Composition Random, replay 100, instrumentation 200 mismatch가 모두 0이었다. Resolver evaluation/trigger count는 계측되지 않은 0을 실제 0으로 과장하지 않고 400/400 모두 `NOT_INSTRUMENTED`로 기록한다.

Worker receipt는 4개 distinct JVM PID `8548`, `26216`, `35088`, `31848`를 고정한다. Recursive root manifest는 top-level contract/JSON/JSONL/Markdown뿐 아니라 source checkpoints와 sidecar, authenticated checkpoints와 sidecar, worker receipts까지 39 files(20 nested)를 raw-byte hash로 검증한다. Final `SHA256SUMS.txt` raw SHA-256은 `79f957365e72fb201ca113ce19463e5298a51c102f9dc8f82af8851af870422f`이며 39/39 entry가 통과했다.

최종 backend regression은 212 suites / 2,169 tests / failures 0 / errors 0 / skipped 0, aggregate JUnit 732.447초, Gradle wall 12분 18초로 통과했다. 첫 clean full 뒤 최초 V6 evidence에서 SKIRMISH event 6건의 null action identity를 발견해 기존 structured `actionId`를 결정적으로 연결하고 affected focused tests 뒤 두 번째 full을 최종 결과로 사용했다. 이 변경은 event 순서, Random, winner, reward 또는 snapshot을 바꾸지 않는다.

Production runtime은 계속 `BASELINE_V1`이며 Matchup/Composition/Jungle activation, gain/threshold tuning, resource/profile/API/frontend 변경은 없다. 다음 단계는 `AUTO_DRAFT_VARIETY_V1`이고, 그 이후 별도 계약과 새 non-overlapping seed를 가진 fresh requalification을 수행한다.

## Stage A: attribution audit harness gate

기존 `matchup-v9-structure-effect-attribution-v1` artifact는 read-only historical evidence로 유지했다. `SHA256SUMS.txt`의 모든 entry가 통과했고 manifest raw SHA-256은 `5ee0cec10b464117421ce347235ec651b94daaaadb8e6d94b6ca39daf660410c`다. 기존 recommendation `MATCHUP_V9_STRUCTURE_ATTRIBUTION_BLOCKED_BY_DIAGNOSTIC_GAP`과 predecessor `RECOMMEND_BASELINE_V1`도 그대로다. 기존 400 pair / 1,100 simulations는 재실행하지 않았다.

공통 `PairedDiagnosticAuditGate`는 frozen schedule에서 expected pair identity를 재구성하고 다음을 finalizer에서 exact 검증한다.

- fixture index/ID/lane/pair/team/series/shard/seed/pair key
- profile/configuration/engine/rules/resource/source identity
- roster/series history/Draft/final Draft/final assignment identity
- canonical replay provenance와 outcome/structure/diagnostic payload digest
- checkpoint sidecar, worker receipt와 source checkpoint payload digest
- exact row/pair/replay/instrumentation coverage
- 서로 다른 worker JVM process identity

Synthetic rejection test 8개는 relabel, observation/outcome/maximum-HP mutation, replay/instrumentation coverage mutation, checkpoint/receipt mismatch, duplicate/missing coverage와 non-distinct JVM을 거부했다. Standalone finalizer는 worker task에 의존하지 않고 aggregate task만 freeze → workers → finalize 순서를 소유한다.

Stage A verdict는 `ATTRIBUTION_AUDIT_HARNESS_HARDENED_WITHOUT_DIAGNOSTIC_RERUN`이다.

## Root cause

선행 V9의 `approved gameplay application=0`은 실제 gameplay zero가 아니었다.

1. `PRODUCTION_V2`는 이미 frozen scalar modifier를 skirmish weighted selection과 teamfight/siege/base-defense uniform winner decision에 소비했다.
2. `gameplayApplicationCount`, `candidateApplications`, `localDecisionComparisons`는 historical `CANDIDATE` 의미만 관측해 production 소비를 0으로 보고했다.
3. application, winner/grade decision과 public event가 동일 `GameplayAttemptId`로 결속되지 않았다.
4. 팀파이트 기본 점수의 기존 `supportToolExecution` Composition 기여도 production gameplay에 존재했지만 scalar modifier-only counterfactual은 이를 Composition OFF baseline에서 분리하지 못했다.
5. `ObjectiveFightResolver`도 같은 팀파이트 점수를 재사용했다. `OBJECTIVE_SETUP` frozen scalar key는 계속 미승인이지만, 기존 non-scalar support-tool 기여는 오브젝트 교전 winner input에 실제로 소비됐다.

따라서 root-cause 분류는 `APPLICATION_ACCOUNTING_ONLY_ZERO`, `DIAGNOSTIC_MODE_CONFLATION`, `ATTEMPT_OR_CAUSE_IDENTITY_BROKEN`이다. `GAMEPLAY_APPLICATION_REALLY_ZERO`, scalar eligibility 완화, computed-but-not-consumed와 새 gameplay 효과 추가는 해당하지 않는다.

## Hardened production diagnostics

`CompositionRuntimeState`가 match-scoped application trace를 소유한다. 각 actual attempt는 routing 직후 pending trace를 만들고 실제 consumer가 도달한 경우에만 계산·적용·소비 상태가 전환된다. Display name이나 event text는 identity에 사용하지 않는다.

`CompositionApplicationProvenance` V4는 structured attempt/context/action/domain/application-point, side/role/lane/objective/structure/fight scale, scalar approval와 eligibility, edge/gain/modifier, score/gap/probability before/after, consumer, Random ordinal/sample, baseline/runtime local result와 public action/event binding을 제공한다.

두 gameplay channel은 구분한다.

- frozen scalar channel: `SKIRMISH`, `TEAMFIGHT`, `SIEGE`, `BASE_DEFENSE`의 기존 승인 key/gain/formula
- existing non-scalar channel: teamfight score에 이미 존재한 support-tool Composition 기여

`OBJECTIVE_SETUP`은 계속 `DISABLED_NOT_APPROVED`로 남아 scalar modifier를 계산하거나 소비하지 않는다. 다만 실제 objective fight에서 기존 non-scalar 기여가 소비된 경우 `SCALAR_DISABLED_EXISTING_NON_SCALAR_EFFECT_CONSUMED`로 별도 기록한다. 새 objective scalar gain이나 eligibility는 추가하지 않았다.

`OFF`, `SHADOW`, historical `CANDIDATE`, frozen `PRODUCTION_V2`의 기존 의미는 섞지 않는다. Production provenance는 observational이며 diagnostics ON/OFF에 따라 gameplay, timeline, Random, winner 또는 replay identity가 변하지 않는다.

## V5 fresh diagnostic

Final artifact는 `backend/build/reports/composition-v9-application-causality-hardening-v5/`다. V1 worker-isolation 실패와 V2/V3/V4 provenance-gap 실행은 모두 consumed diagnostic으로 별도 보존했다. V5는 이 1,600개 seed, Phase 13G-B, V9 calibration/holdout과 Matchup attribution seed에 overlap 0인 새 namespace를 사용했다.

- fixture: real LCK 90 G1 + Hard Fearless 10 G2 = 100
- seeds: fixture당 4, 총 400 distinct pair seeds
- profiles: `MATCHUP_ONLY_CANDIDATE_V1` / `FULL_SYSTEM_CANDIDATE_V1`
- core: 800 match rows / 400 pairs
- replay: 100
- instrumentation profile checks: 200
- total simulations: 1,100
- workers: 4 shards / 4 distinct JVM PIDs (`788`, `38276`, `15512`, `36788`)

### Application pipeline

| 단계 | 수치 |
| --- | ---: |
| Composition initialized matches | 400 / 400 |
| actual attempts | 22,219 |
| mapped / unmapped | 22,219 / 0 |
| approved scalar calculated/applied/consumed | 4,481 / 4,481 / 4,481 |
| non-zero scalar modifier | 4,481 |
| existing non-scalar effect consumed | 1,743 |
| total Composition effect applications | 4,967 |
| local decision changed / unchanged | 65 / 4,902 |

`existing non-scalar effect consumed`는 approved formal combat 1,257건과 scalar-disabled objective fight 486건이다. 한 attempt가 scalar와 non-scalar를 함께 소비할 수 있으므로 4,481 + 1,743은 total application 4,967과 단순 합산하지 않는다.

### Context/application point

| Context/action/domain/application point | actual attempts | scalar status/effect |
| --- | ---: | --- |
| `SKIRMISH/SKIRMISH/SKIRMISH_COMBAT_SCORE/SKIRMISH_COMBAT` | 3,224 | approved scalar consumed |
| `TEAMFIGHT/TEAMFIGHT/TEAMFIGHT_COMBAT_SCORE/TEAMFIGHT_COMBAT` | 730 | approved scalar + existing non-scalar |
| `SIEGE/SIEGE_COMBAT/SIEGE_PUSH_SCORE/SIEGE_PUSH` | 199 | approved scalar + existing non-scalar |
| `BASE_DEFENSE/BASE_DEFENSE/BASE_DEFENSE_SCORE/BASE_DEFENSE` | 328 | approved scalar + existing non-scalar; structured attacker/defender |
| `OBJECTIVE_SETUP/OBJECTIVE_SETUP/NOT_AVAILABLE/OBJECTIVE_SETUP` | 486 | scalar disabled; existing non-scalar consumed |
| observation-only `SIEGE` | 14,693 | `DISABLED_NOT_APPROVED` |
| gank/lane/roam mapped to `SKIRMISH` | 919 / 689 / 951 | no approved score domain |

### Causal and integrity result

Public timeline divergence는 59/400 pair였다. 같은 시각의 structured public action에 바인딩된 local changed attempt도 59/59로 coverage 100%였고 `UNEXPLAINED_PUBLIC_DIVERGENCE`는 0이다.

- correctness failure pairs: 0
- replay checks/mismatches: 100 / 0
- instrumentation profile checks/mismatches: 200 / 0
- direct Composition Random: 0
- Composition-owned Random draws: 0
- Matchup-only Composition contribution: 0
- winner/objective/structure changed: 23 / 50 / 58 pairs
- mean duration delta: -6.6 seconds

Sensitivity 수치는 calibration-only observation이며 production eligibility나 balance acceptance threshold가 아니다.

## Version and runtime policy

실제 frozen gain/formula/resource, decision Random order와 public timeline semantics는 바꾸지 않았다. 기존 gameplay input을 MATCHUP_ONLY counterfactual, frozen scalar delta와 existing non-scalar delta로 분해하고 같은 Random sample에 관측적으로 투영했다. 따라서 engine은 `MATCH_SIMULATOR_ENGINE_IMPLEMENTATION_V9`, active rules는 `MATCH_SIMULATOR_PRE_JUNGLE_RULES_V3`, configuration hashes는 유지했다.

Diagnostic schema는 additive evidence 변경을 반영해 `COMPOSITION_RUNTIME_DIAGNOSTICS_V4`, application provenance는 `COMPOSITION_APPLICATION_CAUSAL_PROVENANCE_V4`로 올렸다. Production resources, runtime profile registry, `MatchEngineV1Policy`, 공개 API와 frontend는 변경하지 않았다.

## Verification

Focused verification은 production application boundary/duplicate/formula, non-zero unchanged and threshold change, objective non-scalar consumer, Base Defense roles, OFF/fresh state, profile parity, candidate/shadow/audit mode isolation, same-seed/instrumentation parity, FARM/reward/death, objective priority/secure와 V9 structure integrity를 검증했다.

최종 executable tree의 complete backend regression은 215 suites / 2,163 tests / failures 0 / errors 0 / skipped 0으로 21분 46초에 통과했다. 그 뒤 executable source/resource/Gradle/shared fixture는 변경하지 않았다.

V5 `SHA256SUMS.txt`의 모든 entry가 통과했고 manifest raw SHA-256은 `cc5d02b4c97e636cf927b07275ffcaff8eb4ec0badaaa307883d5391a5b45af9`다.

## Next step

이번 결과로 fresh requalification의 causal evidence 계약을 설계할 수 있게 됐다. 다음 단계는 Matchup local application provenance와 Composition provenance의 통합 검토, 결과를 보기 전에 gameplay-critical product tolerance 고정, 새 non-overlapping calibration/holdout, Matchup-only/Full-system final requalification과 explicit production runtime 결정을 순서대로 수행하는 것이다. Jungle Economy/Tempo는 자동으로 다시 열지 않는다.

## Fresh requalification 소비 결과

[Match Engine V9 fresh 재검증 V1](match-engine-v9-auto-draft-matchup-composition-fresh-requalification-v1.md)은 V6 scalar/non-scalar decomposition과 public action binding을 변경하지 않고 공식 calibration에 소비했다. Smoke에서는 `SKIRMISH`/`TEAMFIGHT`/`SIEGE`/`BASE_DEFENSE` consumer reachability와 instrumentation parity가 확인됐다.

그러나 calibration checkpoint를 fresh JVM에서 재로드하는 evidence-integrity gate가 unordered Set canonicalization 때문에 실패했다. 따라서 calculated/applied/consumed cardinality, direct/indirect/unresolved cause, macro sensitivity를 official Composition eligibility로 집계하지 않았고 holdout도 0개 소비다. V6 artifact와 causality 결론은 historical predecessor로 유지되며, 다음 시도는 serializer fix 뒤 새 non-overlapping seed contract가 필요하다.
