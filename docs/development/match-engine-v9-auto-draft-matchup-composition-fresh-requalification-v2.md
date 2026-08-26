# Match Engine V9 fresh Auto Draft Matchup/Composition 재검증 V2

## 최종 상태

`MATCH_ENGINE_V9_FRESH_REQUALIFICATION_V2_BLOCKED_HOLDOUT_NOT_CONSUMED`

V2는 evidence canonicalization과 Matchup causal provenance 경계를 보강한 뒤 새 calibration을 실행했다. Calibration exact integrity와 Composition causal gate는 통과했지만 Matchup 공개 divergence 400쌍 가운데 399쌍이 `UNRESOLVED_SNAPSHOT_CAUSE`로 남아 holdout을 시작하지 않았다. Production application policy와 공개 runtime profile은 계속 `BASELINE_V1`이다.

## V1에서 이어받은 경계

V1은 400 calibration seeds, 400 production Auto Draft, 1,200 core rows와 replay/instrumentation 각 300건을 소비했다. Raw checkpoint sidecar는 100/100 정상이었지만 signed `divergenceActionIds`가 unordered `Set`으로 복원돼 fresh-JVM typed reserialization digest가 달라졌다. Matchup runtime consumer action ID도 연결되지 않았고, 시간상 앞선 application만으로 indirect cause를 인정할 수 있었다.

V1 calibration은 `CONSUMED_BLOCKED_EVIDENCE`, 실행하지 않은 V1 holdout은 source/harness contract 변경 때문에 `RETIRED_UNCONSUMED_SOURCE_CONTRACT_CHANGED`로 보존했다. V1 checkpoint, sidecar, receipt와 실패 artifact는 수정하거나 V2 evidence로 재라벨링하지 않았다.

## 구현된 hardening

Signed evidence는 null/blank action ID를 거부하고 중복 제거 후 lexicographic order의 immutable `List<String>`로 저장한다. Checkpoint와 worker receipt는 typed canonical bytes, deserialize/reserialize bytes와 raw JSON tree canonical bytes의 exact equality를 검증한다. Worker receipt가 이미 존재하면 source/contract/receipt binding이 exact한 경우만 안전하게 skip하고, stale 또는 conflicting 결과를 덮어쓰지 않는다.

실제 major-combat attempt가 확정된 뒤 `CombatActionIdentity`를 Matchup combat consumer까지 전달한다. 동일 combat의 summary/KILL/ASSIST는 같은 structured action ID에 결속한다. Pure evaluation, ineligible branch와 action attempt 이전에는 ID를 만들거나 Matchup application provenance를 기록하지 않는다.

Lane pressure에는 match-scoped mutation version, before/after, Matchup delta, clamp effect와 이후 exact state/version을 읽은 consumer action provenance를 분리했다. Causal classification은 다음처럼 상호 배타적이다.

- `EXACT_DIRECT_ACTION_CAUSE`: 동일 action/time/context/stage의 non-zero actual consumer
- `INDIRECT_PRIOR_STATE_CAUSE`: Matchup이 변경한 exact pressure mutation version을 divergence action이 소비
- `UNRESOLVED_SNAPSHOT_CAUSE`: state 차이는 관측됐지만 exact consumer lineage가 불완전
- `UNEXPLAINED_PUBLIC_DIVERGENCE`: 위 어느 경로로도 설명되지 않음

Duplicate protection은 logical consumer slot과 semantic payload를 분리한다. 같은 slot과 exact payload는 idempotent duplicate, 같은 slot과 다른 payload는 conflicting duplicate error, 서로 다른 action 또는 pressure mutation identity는 별도 application이다. Binding error와 stale assignment/participant error도 별도 counter다.

Standalone calibration/final finalizer에서 gameplay worker `dependsOn`을 제거했다. 전체 aggregate task만 worker 순서를 소유하고, historical `registerV9RequalificationTest`의 `forkEvery=1` 계약을 복구했다. Finalizer는 checkpoint가 없거나 stale이면 fail-fast하며 실행 전후 gameplay count를 zero proof로 남긴다.

## V2 동결 계약과 seed

| 항목 | 값 |
| --- | --- |
| Contract schema | `MATCH_ENGINE_V9_AUTO_DRAFT_MATCHUP_COMPOSITION_FRESH_REQUALIFICATION_CONTRACT_V2` |
| Schedule | `MATCH_ENGINE_V9_REAL_LCK_AUTO_DRAFT_PAIRED_100_FIXTURE_4_4_V2` |
| Seed namespace | `MATCH_ENGINE_V9_AUTO_DRAFT_FRESH_REQUALIFICATION_SEEDS_V2_AFTER_CAUSALITY_EVIDENCE_HARDENING` |
| Contract SHA-256 | `674b8a52aa7079b8b659a39656cf76a23362574e8db5e595693ee1e8ae78599e` |
| Source identity | `2c764c9a92cbdee3bcc4484cfce5a2e5c9baaa68ec39c8b66339f95b9cd64f11` |
| Fixtures | G1 90 + Hard Fearless G2 10 |
| Profiles | `BASELINE_V1`, `MATCHUP_ONLY_CANDIDATE_V1`, `FULL_SYSTEM_CANDIDATE_V1` |
| Draft reuse | fixture/seed당 production Auto Draft 1회, 세 profile의 immutable input 공유 |

Historical unique seed 8,408개와 V2 official seed 800개 사이 overlap은 0이다. Calibration↔holdout, DRY_RUN↔official, official 내부 collision도 모두 0이다. V2 calibration 400개는 worker receipt와 calibration failure artifact로 소비가 고정됐다. V2 holdout 400개는 authorization이 생성되지 않아 소비하지 않았다.

## Official 시작 전 검증

- Focused 92 tests: PASS
- Serialization fresh-JVM A/B byte exact: PASS, 양쪽 SHA-256 `9351da98de9ed2ad6a524b835aee1eeb42da4f0e0af83f5ff792074b78cb2e1d`
- Auto Draft multi-fixture fresh-JVM probe A/B byte exact: PASS, 양쪽 SHA-256 `dd33583045cf1f0806b44569eab80ce5881ba7428f1d92629393d46cb65f70f5`
- DRY_RUN smoke: fixture 2, Draft 2, rows 6, replay/instrumentation/input sharing exact, integrity error 0
- Immutable Pre-Jungle/Match Engine V1 parity: PASS
- Complete backend regression: 219 suites / 2,200 tests / failures 0 / errors 0 / skipped 0, Gradle wall 14분 43초
- Full regression receipt: clean, SHA-256 `016fac3574267b5e593852a16a06ff84be3ecf837037e2f2869940f185769340`

Frontend production source와 API contract는 바꾸지 않았으므로 frontend build와 Playwright는 실행하지 않았다.

## Calibration 실행 결과

| 항목 | 결과 |
| --- | ---: |
| Fresh worker JVM | 4 |
| Authenticated checkpoint / sidecar | 100 / 100 |
| Worker receipt | 4 / 4 |
| Production Auto Draft | 400 |
| Core match rows | 1,200 |
| Paired marginal rows | 800 |
| Replay checks | 300 |
| Instrumentation checks | 300 |
| Official simulations | 1,800 |

Exact integrity는 replay mismatch, instrumentation mismatch, timeout, invalid structure, Nexus ordering, post-finish mutation, SUPPORT FARM CS, domain/structured binding error가 모두 0이다. Matchup/Composition direct Random call도 0이다.

Composition gate는 PASS다. Initialized rows 400, actual/mapped attempts 22,579/22,579, calculated/consumed modifier 4,621/4,621, public divergence 62와 direct-covered divergence 62, causal error 0이다. Coverage는 `BASE_DEFENSE`, `OBJECTIVE_SETUP`, `SIEGE`, `SKIRMISH`, `TEAMFIGHT`다.

Matchup gate는 FAIL이다.

| Matchup causal counter | 값 |
| --- | ---: |
| Public divergence pairs | 400 |
| `EXACT_DIRECT_ACTION_CAUSE` | 1 |
| `INDIRECT_PRIOR_STATE_CAUSE` | 0 |
| `UNRESOLVED_SNAPSHOT_CAUSE` | 399 |
| `UNEXPLAINED_PUBLIC_DIVERGENCE` | 0 |
| Consumed / non-zero applications | 43,964 / 43,964 |
| Duplicate / binding errors | 0 / 0 |
| Direct objective/structure mutation | 0 |
| Covered positions | 5 |

시간 선행만으로 indirect를 인정하지 않도록 바뀐 결과, 기존에는 설명된 것으로 보일 수 있던 399쌍이 정확한 state-version consumer lineage가 없는 unresolved로 드러났다. 이 exact-zero correctness gate는 balance threshold가 아니므로 완화하지 않았다.

Calibration 관찰값에서 Matchup−Baseline은 Blue win-rate +1.5pp, winner-changed 4.5%, objective-changed 100.0%, actual structure progression-changed 10.25%, Nexus/ending changed 5.5%, mean duration +1.1초였다. Full−Matchup은 Blue win-rate -1.0pp, winner-changed 8.5%, objective-changed 14.75%, actual structure progression-changed 14.75%, Nexus/ending changed 9.25%, mean duration +6.3초였다. 두 marginal의 `macroSafetyPass`는 false지만 calibration blocker의 machine-readable failure reason은 Matchup `UNRESOLVED_SNAPSHOT_CAUSE`다. Holdout이 없으므로 이 수치를 최종 eligibility 또는 production recommendation으로 승격하지 않는다.

## Lifecycle 중단과 산출물

Calibration standalone finalizer는 `gameplayExecutionCountBefore=0`, `gameplayExecutionCountAfter=0`, `coreSimulationCount=0`을 남겼다. Calibration review SHA-256은 `7c199a522c7b9a608700de135d5731a3f6db52c20c19eaa69b7ae7060dd18bbb`, gate-failed artifact는 `bd1d8115a2adae86cb7ad9c5a54af660d781f5946d5efc16164513cf2d6824b3`, zero-simulation proof는 `5a8abf37e7b8ec711537bef1982a302340962d146c9cbad3e41091df7e8fd9a5`다.

`holdoutAuthorized=false`이므로 holdout authorization/start/completion, holdout checkpoint/sidecar/worker receipt는 모두 0이다. Finalizer A/B, byte-equality promotion, eligible-production-profile artifact, final recommendation과 recursive `SHA256SUMS.txt`에도 도달하지 않았다. 따라서 이번 BLOCKED run에 official final manifest SHA가 있다고 보고하지 않는다.

판정 matrix상 Matchup이 BLOCKED이면 candidate를 추천할 수 없으며 유지 가능한 runtime은 `BASELINE_V1`뿐이다. 다만 holdout과 final promotion이 없으므로 새 final recommendation artifact를 발행한 것은 아니다. `FINAL_PRODUCTION_PROFILE_SELECTION`으로 넘길 PASS evidence도 아직 없다.

## Production과 다음 단계

`source-resource-runtime-identity.json`은 retained runtime profile `BASELINE_V1`, Matchup `OFF`, Composition `OFF`, Jungle contribution `DISABLED_NOT_INTEGRATED`, Economy/Tempo activation `false`를 증명한다. `MatchEngineV1Policy`, Real Match HTTP 기본 profile, frontend와 gameplay tuning은 변경하지 않았다.

다음 작업은 새로운 official seed를 즉시 소비하는 단계가 아니다. 먼저 lane-pressure mutation version이 실제 divergence action의 eligibility/score/selection에서 읽힐 때 exact consumer action ID까지 연결되는지를 설계·검증해야 한다. 그 hardening으로 source/harness contract가 바뀌면 이미 소비한 V2 calibration seed와 실행되지 않은 V2 holdout seed를 재사용하지 않고 새 versioned contract와 fresh seed namespace를 준비해야 한다.
