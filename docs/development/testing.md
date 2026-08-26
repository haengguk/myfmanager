# Testing

## Focused Test

기능을 변경할 때는 관련된 작은 deterministic test를 먼저 실행한다.

```bash
cd backend
./gradlew test --tests 'fully.qualified.TestClass'
```

현재 test source는 대략 다음 책임으로 나뉜다.

- `com.lolfm.champion`: catalog/resource coverage, power, matchup, active-full/historical-subset integrity
- `com.lolfm.composition`: profile aggregation, interaction semantics, production candidate identity
- `com.lolfm.player`: rating loader/catalog와 semantic isolation
- `com.lolfm.draft`: role feasibility, candidate/evaluation/search, final assignment, Hard Fearless series
- `com.lolfm.simulator`: resolver eligibility/duplicate/fallthrough, reward integrity, determinism, timeline integration
- `com.lolfm.controller`: champion/match API response and validation

Focused correctness test는 큰 seed sample이나 분포 목표가 아니라 formula boundary, state transition, duplicate protection, Random non-consumption, structured event를 검증해야 한다.

## Full Regression

Backend normal regression:

```bash
cd backend
./gradlew test
```

Frontend 정적 검증:

```bash
cd frontend
npm run build
```

Production source, resource, runtime wiring, shared fixture 또는 Gradle/test configuration 변경을 마친 뒤 complete backend `test`를 final verification으로 한 번 실행한다. 실제 product regression을 발견해 고친 경우에만 affected focused tests 뒤 최종 full regression을 한 번 더 실행하며, 정상적으로는 최대 2회다. Clean full pass 이후 docs/report wording/assertion-only/isolated test-local fixture만 바뀌면 full regression을 반복하지 않는다. 현재 실행 수치는 [Project Status](../project-status.md)를 따른다.

## Determinism

Same-seed regression은 winner 하나만 비교하지 않는다. 같은 teams, champion assignments, options, seed에 대해 다음 전체 의미가 같아야 한다.

- action attempts와 priority fallthrough
- Random draw order, target/participant 선택
- combat outcomes, kills, assists, bounty/death/respawn
- FARM/XP/gold/item progression
- objective/structure 결과
- events와 snapshots
- final winner와 duration

Complete timeline equality는 reflection 기반 deep traversal 대신 sorted-property/map-key canonical JSON SHA-256을 사용한다. 성공 경로는 hash로 비교하고 불일치 시 canonical JSON structural diff로 내려가며, winner, duration, event 수, snapshot 수와 테스트 고유 의미는 명시 assertion으로 남긴다. 별도 mutation contract가 duration/winner, event 순서와 structured participant/combat source, snapshot/player economy, objective, structure 변경을 각각 탐지하는지 검증한다.

같은 JVM 안의 equality만으로 cross-process 결정성을 증명할 수는 없다. Seeded draw를 enum/set iteration에 배정하거나 timeline hash에 collection serialization이 들어가는 변경은 별도 JVM 두 번의 artifact/canonical timeline SHA도 비교한다.

Draft는 Random을 사용하지 않는다. 동일 resource, `DraftTeamContext`, `SeriesDraftHistory`에서 decisions, final role assignments, `draftIdentity()`가 같아야 한다.

Diagnostics를 켠 실행과 끈 실행이 동일 gameplay configuration이라면 instrumentation 자체가 Random/state/timeline을 바꾸면 안 된다.

## Frozen Identity

Frozen identity test는 서로 다른 범위를 보호한다.

- initial-30 champion resources: 확장 전 semantic oracle가 active full set 안에서 보존되는지 확인
- active full population: manifest version, 현재 champion/legal-role count, exact cross-catalog coverage 확인
- Composition: canonical ordered serialization의 profile hash 확인
- Draft Meta: sorted `ChampionId:Position` lines와 trailing newline의 SHA-256으로 legal-role set 고정
- Player Ratings: raw resource bytes의 pinned SHA-256과 exact roster envelope 확인
- Draft result: ordered decisions의 SHA-256 `draftIdentity()`로 duplicate series commit 방지

Historical hash를 active full dataset 전체 hash로 해석하지 않는다. scope 차이는 [ADR-002](../adr/ADR-002-historical-frozen-vs-active-resource-identity.md)에 있다.

## Diagnostic Tasks

`backend/build.gradle`에는 `run*Diagnostics`, audit/finalization용 `JavaExec` task가 다수 있다. 이들은 test runtime classpath를 사용하지만 일반 `test` task의 필수 dependency가 아니다. 수천 match 분포, holdout, calibration, full-population artifact 생성은 요청된 경우에만 해당 전용 task로 실행한다.

Real proficiency reachability는 다음처럼 분리한다.

```bash
cd backend
./gradlew test                         # fast deterministic correctness; diagnostic tag 제외
./gradlew phase13gRealProficiencyAudit # 537 keys / 1,611 scenarios / JSON+CSV+SHA
```

Composition full-population/holdout 및 simulation distribution은 다음 전용 lane을 사용한다.

```bash
cd backend
./gradlew compositionHoldoutAudit      # 7,776 lineups, 240 holdout, 1,000 pairs, artifact inventory
./gradlew simulationDistributionAudit  # large-seed duration/objective/soul/player-impact statistics
```

Diagnostic JUnit은 공통 `diagnostic` tag와 task별 tag를 함께 가진다. 각 custom task는 `composition-holdout`, `simulation-distribution`, `phase13g-real-proficiency`처럼 자기 domain tag만 include하므로 다른 diagnostic을 우연히 함께 실행하지 않는다. 기본 lane에는 bounded in-memory holdout selection/schedule 계약과 composition runtime identity/authorization/gain/sign/Random-isolation 계약이 남는다. 다중 seed라도 participant legality, duplicate prevention, respawn, one-action-per-tick처럼 deterministic invariant를 검증하는 테스트는 diagnostic으로 이동하지 않는다.

Composition V9 causality evidence repair는 다음 lane을 사용한다.

```bash
cd backend
./gradlew test                                      # diagnostic tag 제외
./gradlew verifyCompositionV9CausalityFocusedProof # exact selector 2건의 JUnit receipt
./gradlew runCompositionV9ApplicationCausality     # freeze → 4 JVM workers → finalize
```

마지막 task는 기존 V5의 400 seeds를 evidence repair로만 재사용해 1,100 simulations을 실행한다. Freeze는 default `test` XML에서 대형 Composition 5 classes가 0건인지 확인하고, explicit runner dependency manifest와 focused proof receipt를 source contract에 결속한다. Finalizer는 worker PID 고유성, source/authenticated checkpoint와 sidecar, canonical receipt bytes와 recursive `SHA256SUMS.txt`를 다시 검증한다. 새 shared runner/proof/Gradle dependency를 도입할 때만 explicit manifest를 갱신하며 무관한 test source는 추가하지 않는다.

기본 `test`에는 role-fixed completion helper, flex false-positive cases, identity/catalog/resource/API contract, 소수 representative real keys, `@TempDir` report writer만 남는다. 전체 audit JUnit class는 `diagnostic` tag이며 default task가 제외한다. 따라서 기본 test는 shared full-population report를 만들거나 입력으로 읽지 않는다. Artifact inventory diagnostic은 CSV를 line streaming으로 읽어 같은 header/row semantics를 유지하면서 전체 report tree를 한꺼번에 heap에 올리지 않는다.

Diagnostic 결과를 normal unit-test assertion으로 옮기려면 먼저 그것이 balance observation이 아니라 deterministic invariant인지 확인한다.

### Structure engine V9

구조물 correctness는 `StructureEngineRedesignTest`와 관련 resolver/integration suite에서 explicit cross-lane target, 3인 base minimum, 종료 guard, duplicate mutation/reward/Random 0회, display-name isolation, HP/plate/partial damage, local defender/backdoor, wave/attacker/defender stop, 180초·40% 넥서스 포탑 재생성, post-fight 연속 종료와 structured snapshot/event를 deterministic invariant로 검증한다. `MatchEngineV1CrossJvmDeterminismTest`는 새 enum set/map과 구조물 상태의 process-level canonical order를 별도로 검증한다.

분포 관측은 기본 `test`에 넣지 않고 다음 전용 task로 실행한다.

```bash
cd backend
./gradlew runStructureRealismDiagnostics -PstructureDiagnosticSeeds=200
```

이 diagnostic은 첫 구조물 피해/첫 포탑/기지 개방/경기 종료 분포, 넥서스 포탑 철거 뒤 넥서스 연결, kill 사이 구조물 연속 철거와 duplicate event ID, 비정상 HP, source 누락, 보호 중 넥서스 파괴, 종료 뒤 mutation을 structured field만으로 집계한다. Event message나 frontend text는 읽지 않으며 production tuning의 assertion oracle로 사용하지 않는다.

V9 provenance/profile/API/압축/artifact-boundary/cross-JVM 집중 검증은 clean pass했다. 최종 production tree의 complete backend regression은 205 suites / 2,132 tests / failures 0 / errors 0 / skipped 0, Gradle wall 11분 11초로 통과했다. 이 실행은 앞선 clean full 뒤 구조물 의미 변경이 여전히 V8/rules V2로 표시되는 production provenance 결함을 발견해 V9/rules V3·V2로 고친 후 수행한 예외적 세 번째 full이다. Profile 전체의 replay/output identity가 바뀌므로 focused evidence만으로는 충분하지 않았다. 이 최종 pass 뒤에는 Markdown만 갱신했다.

## Pre-Jungle Runtime Baseline

Runtime profile/provenance milestone의 baseline은 normal `test` task가 생성하지 않는다. 반드시 production source/resource/build wiring을 끝내고 focused tests, final full regression과 source guard 확인을 마친 뒤에만 실행한다.

```bash
cd backend
./gradlew test --tests 'com.lolfm.simulator.SimulationRuntimeProfilesTest' \
  --tests 'com.lolfm.simulator.ConfiguredMatchSimulatorParityTest' \
  --tests 'com.lolfm.simulator.SimulationRandomFingerprintTest' \
  --tests 'com.lolfm.application.RealDraftMatchOrchestratorTest' \
  --tests 'com.lolfm.application.RealDraftRandomObservationParityTest' \
  --tests 'com.lolfm.application.PreJungleBaselineV2GeneratorTest'
./gradlew test --console=plain --no-daemon
./gradlew generatePreJungleRuntimeBaselineV2 \
  -PbaselineFullRegressionStatus=CLEAN_PASS \
  -PbaselineSourceRevision=<git-revision-or-working-tree-identity>
```

Generator는 `CLEAN_PASS`와 source revision property가 없으면 fail-fast한다. Profile schedule은 `BASELINE_V1`, `MATCHUP_ONLY_CANDIDATE_V1`, `FULL_SYSTEM_CANDIDATE_V1` 세 개로 고정되고 각 profile의 Jungle contribution이 `DISABLED_NOT_INTEGRATED`인지 확인한다. 결과는 `backend/baseline/pre-jungle-runtime-v2/`과 동일 bytes의 `backend/build/reports/pre-jungle-runtime-baseline-v2/`에 기록된다. Existing source와 candidate bytes가 다르면 report candidate만 남기고 source overwrite를 거부한다. Immutable baseline JSON은 생성 OS와 무관하게 canonical CRLF bytes로 직렬화하고 `.gitattributes`의 `-text` 규칙으로 Git line-ending 변환을 금지한다. `BaselineArtifactByteIntegrityTest`가 tracked raw bytes와 `SHA256SUMS.txt`의 일치를 기본 focused lane에서 확인한다.

V1 task `generatePreJungleRuntimeBaseline`은 immutable predecessor 재생성을 항상 거부한다. Official V2 생성 뒤에는 같은 명령을 새 JVM에서 다시 실행해 source bytes가 exact equality로 승인되는지 확인한다.

### Jungle Economy OFF parity

Jungle Economy V1-A 뒤에는 immutable baseline을 재생성하지 않는다. 기존 세 OFF profile만 같은 9경기 schedule로 실행하고 gameplay output을 V2 oracle과 비교한다.

```bash
cd backend
./gradlew verifyJungleEconomyOffParity
```

Exact 비교 대상은 configuration hash, Draft/final assignment identity, complete timeline hash, Random draw count/trace hash, winner/duration/event/snapshot count다. Engine implementation과 active resource snapshot이 바뀌었으므로 replay provenance hash는 달라져야 하며 equality 대상에서 제외한다. 결과는 `build/reports/jungle-economy-v1-a/off-parity-report.json`에 기록한다. 이 task는 `diagnostic` + `jungle-economy-off-parity` tag만 실행하고 기본 `test`에는 포함되지 않는다.

Jungle Economy candidate correctness는 별도 focused tests에서 pure JRM/PATHING orthogonality, 모든 skip reason의 reward·Random 불변식, progression OFF의 CS/gold-only 결과, enum-map canonical order, real GEN–T1 same-seed replay와 Jungle-OFF FULL 대비 runtime reachability를 검증한다. 기존 세 OFF profile은 계속 위 oracle diagnostic이 담당한다.

### Pre-Jungle Tempo oracle and V1-B diagnostics

Jungle Tempo production 수정 직전에 기존 네 profile을 3개 real-match case로 실행한 immutable oracle을 만들었다. Artifact는 `baseline/pre-jungle-tempo-runtime-v1/pre-jungle-tempo-runtime-baseline-v1.json`, canonical CRLF raw SHA-256은 `17f703a48949b63bf4ca25f4b32be2bc22fac87a439cdd8cb7c18aadc7f82074`다. 생성기는 당시 464-file canonical production guard를 고정하므로 V1-B production tree에서 재생성할 수 없고 existing bytes도 overwrite하지 않는다.

V1-B 이후 기존 네 profile exact parity와 bounded candidate 관찰은 각각 분리된 diagnostic task다.

```bash
cd backend
./gradlew verifyPreJungleTempoParity
./gradlew runJungleTempoCandidateDiagnostic
```

첫 task는 12 matches의 configuration/Draft/final assignment/complete timeline/Random fingerprint/result exact equality를 검증하고 `build/reports/jungle-tempo-v1-b/pre-tempo-parity-report.json`을 쓴다. Replay provenance hash는 baseline engine V2와 해당 report 생성 당시 engine V6가 다르므로 equality에서 제외했다. Historical V6 report SHA-256은 `a38b16811a3b74f5fe8958bce5eb5b8e1310ba18795af46ea016f705bbec22c9`다. 두 번째 task는 12 fixed same-seed Economy-only/Tempo pair의 readiness와 actual consumption을 기록한다. 이 작은 sample은 구조 확인용이며 calibration이나 production-activation gate가 아니다.

V1-B focused correctness는 `JungleTempoStateTest`, `JungleTempoGankIntegrationTest`, economy/runtime integration과 real GEN–T1 smoke가 담당한다. Tempo-not-ready/ineligible/duplicate path는 tempo state와 trigger Random을 소비하지 않는다. Tempo-ready 뒤 failed trigger는 eligible trigger Random만 소비하고 tempo credit, action state와 downstream action Random은 보존한다. 실제 no-kill gank와 successful counter response는 각각 자기 side의 credit만 한 번 소비하며, non-attempt는 lane combat으로 fall through해야 한다.

V1-B 당시 serial final full regression은 163 suites / 1,940 tests / failures 0 / errors 0 / skipped 0, aggregate JUnit XML 500.926초, Gradle wall 8분 32초로 clean pass했다. V1-B final canonical production guard는 471 files / `143112d499c9731e25de80edf2883621c7bb9c3c948907f4a0c8d0146093a260`이다.

### Jungle V1 focused hardening

Batch C는 balance sample을 늘리지 않고 structured eligibility와 cross-system deterministic invariant를 normal focused lane에 추가한다.

```bash
cd backend
./gradlew test \
  --tests 'com.lolfm.simulator.JungleEligibilityDiagnosticsTest' \
  --tests 'com.lolfm.simulator.JungleV1FocusedHardeningIntegrationTest'
./gradlew verifyPreJungleTempoParity
./gradlew test --console=plain --no-daemon
```

Expanded focused regression은 위 두 class와 Jungle Economy/Tempo/Gank/Counter Gank/Lane Combat/FARM Recovery/Mid Game Macro/runtime profile/Random fingerprint/real Draft 경로 17개 class, 168 tests를 실행한다. 구조화된 reason count와 trigger roll algebra, actual action/Tempo consumption equality, action/death/recovery/macro FARM block의 CS/FARM gold/XP/passive/Random boundary, priority/fallthrough, common reward와 event linkage, match state isolation, same-seed complete timeline을 검증한다. V5 follow-up은 살아 있는 non-default activity를 death와 구분하고, 같은 tick의 같은 종류 actual attempt 두 개가 gate를 통과하지 못하는 negative fixture를 추가한다.

`verifyPreJungleTempoParity`는 Batch C engine V4/V5에서 기존 네 profile 12/12 exact gameplay parity로 통과했고 report SHA-256은 각각 `6ba6d0c33332fa4a6eef343a9030fd3f031f6cef3a6d0363c6877db25fb5878f` / `87577b6c26073fb748ab6d9b9d2bda719437c60381ba99f71b07482bcdeca927`였다. V6도 12/12 exact pass했고 current report SHA-256은 `a38b16811a3b74f5fe8958bce5eb5b8e1310ba18795af46ea016f705bbec22c9`다. Batch C 전후와 V5의 historical `runJungleTempoCandidateDiagnostic` report SHA-256은 모두 `3f94b100464a48181fccf5a04a5e16f62dea3e17a05b1398215f65afddab1199`로 byte-identical하다. 이는 Economy-only 15/3과 Tempo 15/2 gank/counter-gank, Tempo consumption 15/2를 그대로 보존한다.

Batch C V4 final full regression은 166 suites / 1,951 tests / failures 0 / errors 0 / skipped 0, aggregate JUnit XML 519.521초, Gradle wall 8분 51초로 clean pass했고 당시 production guard는 472 files / `54b53ea30453e39791dd8aa0197e95ed190697ce1b83c010baff1df540c833d9`였다. V5 follow-up final full regression은 166 suites / 1,953 tests / failures 0 / errors 0 / skipped 0, aggregate JUnit XML 513.886초, Gradle wall 8분 49초로 첫 실행에서 clean pass했다. Full 뒤에는 production/shared fixture를 바꾸지 않고 one-major-combat test의 marker 분류만 assertion-only로 좁혔으며 affected 5 tests가 clean pass했다. Full regression budget의 재사용 조건에 따라 full은 반복하지 않았고 immutable baseline도 재생성하지 않았다.

### Final 13G-B1 audit contract and bounded dry-run

B1은 통계 보정 task가 아니라 real-data 감사 입력과 실행 형식을 고정하는 단계다. Schedule은 10개 LCK 팀의 G1 90 fixtures와 all-team Hard Fearless G2 10 fixtures, fixture별 calibration 24 seeds와 holdout 8 seeds를 포함한다. 전체 hash는 `3bb5e81241a3be2a1509e67528e577ae8f48fca94dec5fc15f93ec8ac78052ef`다. Dry-run은 두 reserved lane과 분리된 seed를 사용하므로 calibration 또는 holdout 표본으로 세지 않는다.

```bash
cd backend
./gradlew test \
  --tests 'com.lolfm.application.Phase13GB1AuditScheduleTest' \
  --tests 'com.lolfm.application.Phase13GB1AuditContractTest'
./gradlew verifyPhase13GB1CrossJvmDeterminism
./gradlew runPhase13GB1DryRun
```

첫 command는 fixture cardinality/orientation, all-team G2 pairing, seed split, schedule hash와 다섯 profile의 exact configuration/rules identity를 검증한다. Writer는 전달받은 schedule의 content hash를 재계산하고 canonical frozen schedule과 exact equality인지 다시 확인한다. `PreparedFixture`는 harness의 production orchestration 경로만 생성할 수 있다. 두 번째 command는 실제 `GEN`–`T1` Draft fixture 하나를 profile loop 밖에서 준비하고 BASELINE / MATCHUP ONLY / FULL / FULL+JUNGLE ECONOMY / FULL+JUNGLE TEMPO를 같은 seed로 실행한다. 이어 BASELINE을 한 번 재실행해 replay provenance, complete timeline, Random fingerprint와 simulator가 반환한 전체 structured diagnostic snapshot/history의 exact equality를 확인한다.

Report는 `build/reports/phase13g-b1/`에 생성한다.

- `phase13g-b1-audit-contract.json`: 범위, source identity, 실행/미실행 lane과 다음 단계
- `phase13g-b1-profile-contract.json`: 다섯 resolved gameplay configuration/hash/rules
- `phase13g-b1-schedule.json`, `phase13g-b1-schedule.csv`: 100 fixtures와 3,200 reserved seed rows
- `phase13g-b1-dry-run-provenance.json`, `phase13g-b1-dry-run-matches.csv`: 고정 Draft 5-profile 결과, selected diagnostics, 전체 diagnostics canonical hash와 domain별 structural integrity
- `SHA256SUMS.txt`: 위 6개 파일의 raw-byte SHA-256

B1 hardening은 Champion Power/Matchup, Composition, Combat Outcome, Objective Priority, Structure, Lane Phase, Mid Game Macro, Progression과 Jungle Economy의 명시적 오류 카운터를 domain별 integrity로 집계한다. 정상 rejection/ineligible count는 오류로 오인하지 않는다. Full diagnostic equality는 메모리 record equality와 `SHA256_UTF8_RECORD_COMPONENT_MAP_KEY_CANONICAL_V1` hash로 이중 확인하며 Random raw trace는 기존 fingerprint가 담당한다. Tempo dry-run은 attempt-consumption equality뿐 아니라 READY 관찰과 GANK+COUNTER_GANK actual consumption 합계가 양수인지 고정한다.

B1 task는 `diagnostic` tag라 기본 `test`에서 제외된다. P1 gate는 두 fresh JVM이 각각 쓴 7개 B1 artifact 전체를 byte-for-byte 비교하고, 그 manifest SHA를 직후 canonical `runPhase13GB1DryRun` manifest와 다시 대조한다. Phase-specific guard 재고정에서도 두 probe와 dry-run이 모두 clean pass했고 세 manifest SHA-256은 `dc8f63a117bbd15dc05ca533ae8c98a3707ae70fa9af810f6f14539d8ee9b9cd`로 같았다. Summary SHA-256은 `37ce2c275d5dc4837328c7e8c9fb7b100f62ffcdee4ee740217140d8c751f5c0`이다. `calibrationExecuted=false`, `holdoutExecuted=false`, `productionDecision=NOT_EVALUATED`가 B1 summary contract다. 단일 dry-run의 승패·경기 시간·profile 차이로 balance 결론을 내리면 안 된다.

### Final 13G-B2 real-data calibration

B2는 기본 `test`와 분리된 대규모 diagnostic이다. 전체 lifecycle은 fresh P1/B1 gate를 dependency로 실행하고 `forkEvery=1`로 JVM 재사용을 금지한 네 shard class가 서로 다른 fixture checkpoint를 만든 뒤, gameplay를 실행하지 않는 finalizer가 worker receipt와 artifact를 검증한다. `maxParallelForks=4`는 동시 실행 상한일 뿐이므로 실제 병렬도와 무관하게 네 class가 네 fresh JVM을 사용한다.

```bash
cd backend
./gradlew runPhase13GB2Smoke
./gradlew test --console=plain --no-daemon
./gradlew runPhase13GB2Calibration --console=plain --no-daemon
```

`runPhase13GB2Smoke`는 실제 LCK fixed Draft 1개 × calibration seed 1개 × 5 profiles와 same-seed BASELINE replay를 실행한다. Replay provenance 재계산과 canonical row evidence를 확인하고 seed/job 재라벨링, outcome 변조, Jungle observation 변조, checkpoint raw bytes 변조가 각각 거부되는지 검증한다. Synthetic report는 별도 `SYNTHETIC_VALIDATION_ONLY` 경로만 사용하며 공식 READY가 될 수 없다. 시간 점프가 10초 경계의 exact snapshot을 건너뛰는 real fixture를 사용해 requested/actual time을 모두 보존하고, 상태를 보간하거나 종료 뒤 checkpoint를 복제하지 않는지도 확인한다.

Official contract는 다음을 고정한다.

- 90 G1 + 10 Hard Fearless G2 fixtures, calibration seed 24개, 5-profile fixed order: 12,000 jobs
- holdout seed 실행 경로 없음; 준비 orchestration 110회와 결정성 replay 100회는 calibration count에서 제외
- fixture당 120행 canonical execution evidence와 atomic checkpoint, changed guard rejection
- job/fixture/roster/Draft/profile/seed에서 replay provenance 재계산
- outcome/diagnostics/Jungle observations를 포함한 row payload digest
- shard별 raw checkpoint digest receipt, fixture ownership과 서로 다른 worker JVM identity 4개
- 모든 match의 profile semantics, non-empty Random fingerprint와 전체 domain integrity
- fixture당 BASELINE replay provenance/timeline/Random/full structured diagnostics exact equality
- fixed Draft/final assignment, profile/source/resource/schedule hash와 job order exact equality

Artifact는 `build/reports/phase13g-b2/`에 생성한다.

- contract, 12,000-job manifest, 100 fixed Draft와 100 determinism replay CSV
- 12,000-row JSONL/CSV와 600/900/1,200/1,500/1,800/final Jungle checkpoint CSV
- 12,000 paired marginal rows, lane/pair/profile/team/jungler-champion summaries
- normalized checkpoint receipt manifest, full-domain integrity JSON, review-only balance JSON, 16-file `SHA256SUMS.txt`

Phase-specific source/build guard 도입 뒤 stale checkpoint를 사용하지 않고 V3 경로에서 B2를 다시 실행했다. Final 실행은 100/100 fixture, resume 0, 각 fixture `seed 24/24`, 12,000/12,000 unique jobs와 replay provenance, 100/100 exact replay, worker receipt와 distinct fresh JVM 4/4, checkpoint payload digest 100/100, holdout 0, domain integrity error 0과 SHA 16/16으로 `CALIBRATION_EVIDENCE_READY_FOR_REVIEW`를 기록했다. Lifecycle wall time은 20분 4초였다. Review/manifest SHA-256은 `32c1770b6971179c0cb7033e853882e7e9fb06c6980285eb2dba23210993fbee` / `71ac3a26cc4df6c49794c2daeb2efc75bd2667b39237b89af4a1a6bda963d7e4`이고 checkpoint payload manifest SHA-256은 `f1945f8333733a4c3ecefdfcaa30276129a98a3b58ae9e609e45d247de79df58`다. 이전 B2와 calibration behavior는 exact equality다.

Balance output은 correctness assertion이 아니다. Economy − Full winner flip은 18/2,400, Tempo − Economy는 811/2,400이며 Tempo actual Gank/Counter-gank consumption은 5,175/660회였다. 이 값은 calibration human review와 B3 gate freeze의 입력이다. 자동 tuning, candidate freeze, holdout과 `PRODUCTION_V1` 결정은 이 task에서 금지한다.

### Final 13G-B3 frozen holdout

B3 contract/smoke/official population은 모두 기본 `test`와 분리된 diagnostic task다. Smoke는 dry-run 전용 seed만 사용하며 reserved holdout을 소비하지 않는다. Official task는 이미 한 번 완료됐으므로 결과 확인이나 FAIL 분석을 위해 다시 실행하면 안 된다.

```bash
cd backend
./gradlew test \
  --tests 'com.lolfm.application.Phase13GB1AuditContractTest' \
  --tests 'com.lolfm.application.Phase13GB2CalibrationContractTest' \
  --tests 'com.lolfm.application.Phase13GB3FrozenHoldoutContractTest' \
  --console=plain --no-daemon
./gradlew runPhase13GB3Smoke --console=plain --no-daemon
./gradlew test --console=plain --no-daemon
./gradlew runPhase13GB2Calibration --console=plain --no-daemon
./gradlew freezePhase13GB3CandidateAndGates --console=plain --no-daemon
./gradlew runPhase13GB3FrozenHoldout --console=plain --no-daemon # one-time: 완료됨, 재실행 금지
cd build/reports/phase13g-b3
sha256sum -c SHA256SUMS.txt
```

Focused contract tests는 4,000-job/G1·G2 cardinality, seed disjoint와 calibration 거부, profile 누락·중복·순서 변경, contract/hash 및 B2 binding 변조, source/resource/configuration mismatch, row/job/fixture/roster/Draft/profile/seed·outcome·diagnostics·Jungle observation·checkpoint bytes·receipt ownership/JVM identity 변조, synthetic official READY 거부를 검증한다. Smoke는 one-fixture five-profile row와 same-seed replay, full diagnostics/Random/timeline equality를 확인한다.

`freezePhase13GB3CandidateAndGates`는 B2 evidence와 최종 source guard를 확인한 뒤 contract와 shard별 authorization만 생성하며 이때 holdout execution count가 0이어야 한다. 네 shard별 Test class/task는 각각 `forkEvery=1`로 fresh JVM receipt를 남기고 fixture index modulo 4만 소유한다. Fixture당 40행 전체가 검증된 뒤 임시 checkpoint를 atomic move한다. Authorization은 shard 시작 시 `.authorized`에서 `.started`로 atomic move되어 completion receipt가 있는 공식 run을 반복할 수 없다. Finalizer만 4 receipt, 100 checkpoint, 4,000 rows와 provenance, replay 및 domain integrity를 결합해 official artifact writer를 연다.

Final executable tree의 B1/B2/B3 focused contract tests와 B3 smoke는 clean pass했다. Default full regression은 첫 실행에서 170 suites / 1,969 tests / failures 0 / errors 0 / skipped 0, aggregate JUnit XML 488.240초, Gradle wall 8분 23초였다. B2 V3 재고정은 20분 4초, contract freeze는 16초, official B3는 22분 32초에 성공했다. B3는 100/100 checkpoint, payload digest 100/100, unique job/provenance 4,000/4,000, exact BASELINE replay 100/100, distinct fresh JVM 4/4, calibration 0, domain error/timeout/SUPPORT FARM CS 0과 SHA manifest 18/18을 기록했다.

Machine-readable 결과는 `build/reports/phase13g-b3/phase13g-b3-final-review.json`과 `phase13g-b3-frozen-gate-evaluation.json`을 사용한다. Evidence는 READY이고 exact 7/7, numeric 66/67이다. Economy G1 winner flip 10/720(1.3889%)이 frozen inclusive 상한 1.379455%를 넘겨 Economy `FAIL`; Tempo 전체 flip 272/800(34.00%)은 B2 33.79%의 interval과 일치하지만 product tolerance가 없어 `REVIEW_REQUIRED`다. 이는 holdout 재실행·threshold 완화·자동 tuning의 근거가 아니며 `productionDecision`은 Final 13G-B까지 `NOT_EVALUATED`다.

### Final 13G-B synthesis and Production V1 decision

Final 합성기는 test-side의 plain-JDK consumer다. B2/B3 match를 재실행하지 않고 두 `SHA256SUMS.txt`의 16/18 entries, B3의 B2 review/manifest binding, frozen verdict와 실행 횟수를 먼저 확인한다. 별도 Spring-wired inspector는 closed registry, RealDraft 기본/explicit overload, autowired simulator, HTTP controller와 `SimulationOptions.productionDefaults()`를 소수 fixed seed로 검증하고 canonical runtime evidence를 만든다. Standalone consumer는 그 evidence의 raw SHA와 내부 identity hash가 frozen contract와 모두 일치해야만 `runtimeIdentityStatus=EXACT`를 허용한다.

```bash
cd backend
./gradlew test \
  --tests 'com.lolfm.application.Phase13GBFinalSynthesisContractTest' \
  --tests 'com.lolfm.application.Phase13GBFinalSynthesisE2ETest' \
  --tests 'com.lolfm.application.Phase13GBFinalRuntimeIdentityInspectorTest' \
  --console=plain --no-daemon

# 합성기는 외부 dependency가 없는 Java 17 source로 독립 컴파일 가능
javac --release 17 \
  -d build/classes/java/final-13g-b-hardening \
  src/test/java/com/lolfm/application/Phase13GBFinalRuntimeIdentityEvidence.java \
  src/test/java/com/lolfm/application/Phase13GBFinalSynthesis.java
java -cp build/classes/java/final-13g-b-hardening \
  com.lolfm.application.Phase13GBFinalSynthesis \
  build/reports/phase13g-b2 \
  build/reports/phase13g-b3 \
  build/reports/final-13g-b-runtime-identity \
  build/reports/final-13g-b

cd build/reports/final-13g-b
sha256sum -c SHA256SUMS.txt
```

Focused verification은 3 suites / 14 tests다. 기존 helper 계약과 runtime identity 상태 전이, 실제 Spring/RealDraft/HTTP wiring, 6,400-row synthetic full `write()`를 검증한다. Synthetic E2E는 official generated report를 fixture로 사용하지 않고 B2 4,800 + B3 1,600 paired row와 16/18-entry manifest를 programmatically 만든다. Profile/configuration/engine/source/resource/HTTP wiring identity 변조, B2/B3 raw manifest 변조, paired row 누락·중복과 runtime evidence 부재는 READY를 만들지 못한다.

실제 합성은 input paired row 6,400개, 새 simulation 0개로 `FINAL_EVIDENCE_VALID`, `KEEP_CURRENT_RUNTIME_DEFAULT`, retained `BASELINE_V1`, runtime identity `EXACT`, `READY_FOR_MATCH_ENGINE_V1_FREEZE`를 만들었다. Runtime evidence raw SHA는 `7e54d89df8d3364e845703181a3214367818e7a34a122714299c65b480d0e109`, runtime identity hash는 `bcb3d2bdf009a8b53d6f99db69ad3f129a7c3c2f29570bcdf12ee0c0655ba675`다. Hardened output manifest SHA-256은 `bd9a9cf3b089cfc76fceb0311094c1b70232278404f5675c42d89849d927bc98`이고 6/6 entry가 통과했다. Java 17 두 별도 output directory의 7개 파일은 byte-for-byte identical이었다.

Artifact는 `build/reports/final-13g-b/`의 다음 파일이다.

- `final-13g-b-evidence-binding.json`: B2/B3 manifest/review/contract identity와 no-rerun binding
- `final-13g-b-retained-runtime-identity.json`: retained configuration/rules/engine/source/resource/Draft identity와 실제 wiring
- `final-13g-b-segmented-sensitivity.csv`: calibration/holdout/combined의 fixture/team/side/player/champion/player×champion/matchup 집계
- `final-13g-b-flipped-pairs.csv`: winner가 바뀐 1,112개 paired row의 structured attribution
- `final-13g-b-sensitivity-synthesis.json`: aggregate, B2↔B3 segment correlation과 top holdout segments
- `final-13g-b-production-decision.json`: Decision V2, candidate activation false, retained `BASELINE_V1`, exact runtime identity와 Match Engine V1 freeze readiness

Final hardening은 production Java/resource/Gradle/shared fixture를 변경하지 않았다. 따라서 B3 final tree에서 이미 통과한 170 suites / 1,969 tests full regression을 재사용하고 test-side focused tests만 실행했다. `runPhase13GB2Calibration`, `freezePhase13GB3CandidateAndGates`, `runPhase13GB3FrozenHoldout` 및 B3 worker/finalizer는 실행하지 않았다.

### Match Engine V1 freeze

Match Engine V1 correctness는 대규모 balance population이 아니라 boundary와 결정성 invariant로 검증한다.

- policy/configuration/rules/engine과 candidate 비활성화 exact equality
- roster/position/player/assignment/Draft/policy의 completeness와 fail-fast
- illegal champion-role의 pre-Random rejection과 실패 시 series non-commit
- final snapshot 기반 summary와 summary action/`KILL` event non-double-counting
- input/output/timeline/provenance deep immutability와 display-label isolation
- same seed complete structured output, diagnostics observational equality
- legacy Real Draft↔V1 complete timeline/Random/common provenance field parity와 V1 `inputHash` replay 결속
- 실제 structured timeline 변조 거부와 display-message-only hash 제외
- 두 fresh JVM의 canonical output/summary/verification byte equality

영향 범위 focused 묶음은 8 suites / 42 tests, failures/errors/skipped 0으로 통과했고 최종 replay binding material과 snapshot 변조 assertion 보강 뒤 핵심 2 suites / 12 tests도 다시 통과했다. 이어 production final tree의 complete backend regression은 첫 실행에서 175 suites / 1,995 tests / failures 0 / errors 0 / skipped 0, aggregate JUnit XML 628.861초, Gradle wall 10분 39초로 clean pass했다. 그 뒤에는 freeze artifact와 문서만 생성·갱신했으므로 full regression을 반복하지 않았다.

Artifact writer는 clean full XML이 최소 170 suites / 1,970 tests이고 failure/error가 0인지 먼저 확인한다. 그 다음 historical Final 13G-B manifest 6/6, 실제 legacy/V1 gameplay/common provenance parity, V1 replay input 결속과 fresh-JVM 두 번을 재검증한 뒤 `build/reports/match-engine-v1-freeze/`의 JSON 7개와 `SHA256SUMS.txt`를 쓴다. 이 freeze 작업은 B2 calibration, B3 holdout 또는 baseline generator를 다시 실행하지 않는다.

### Real Match API V1

Real Match API V1은 큰 seed population이 아니라 strict HTTP boundary와 current Match Engine V1 parity를 검증한다. Core focused 명령은 다음 9개 class를 실행한다.

```text
gradlew.bat test \
  --tests com.lolfm.controller.RealMatchApiV1RequestParserTest \
  --tests com.lolfm.application.RealMatchApiV1ServiceTest \
  --tests com.lolfm.controller.RealMatchApiV1ControllerTest \
  --tests com.lolfm.controller.RealMatchApiV1ErrorBoundaryTest \
  --tests com.lolfm.controller.RealMatchApiV1VerificationBindingTest \
  --tests com.lolfm.controller.ChampionApiTest \
  --tests com.lolfm.application.MatchEngineV1ContractTest \
  --tests com.lolfm.application.RealDraftMatchOrchestratorTest \
  --tests com.lolfm.application.PlayerAbilityProfileContractTest \
  --console=plain --no-daemon
```

이 묶음은 9 suites / 55 tests, failures 0 / errors 0 / skipped 0으로 통과했다. 주요 고정 항목은 다음과 같다.

- options의 10팀, 팀당 5명, stable PlayerId 50개와 canonical ordering
- required schema/team/seed, canonical signed long string과 V1-scoped unknown-field rejection
- invalid 요청과 preflight rejection에서 orchestrator/Random 실행 없음
- typed preflight만 422이며 일반 engine/orchestration `IllegalArgumentException`은 stable 500
- 현재 요청 team/seed와 output provenance가 다르거나 Game 2/history가 섞이면 response mapping 금지
- production policy/provenance/output hash 검증 전 response mapping 금지
- display team name이 바뀌어도 explicit team code identity와 canonical ordering 유지
- fixed `GEN` 대 `T1`, seed `"73"`의 실제 roster/Draft/result/timeline/integrity
- current V9의 player별 ability profile과 additive structure action/snapshot HTTP projection
- 같은 HTTP 요청 2회의 exact Draft/result/structured timeline/hash/Random fingerprint와 두 번째도 Game 1인 격리
- direct `orchestrateV1` output과 HTTP projection의 JSON 의미 exact parity
- seed/PlayerId/ChampionId/enum string, timeout winner null과 structured error serialization
- 기존 Champion API와 frozen Match Engine V1 contract 보존

이번 refresh 전 남아 있던 6 failures는 gameplay assertion이 아니라 backend 테스트가 `frontend/src`의 `.woff2`를 UTF-8 text로 읽은 `MalformedInputException`이었다. 공통 test-side scanner는 `.ts`, `.tsx`, `.js`, `.jsx`, `.css`, `.html`, `.json`, `.mjs`, `.cjs`, `.md`, `.svg`만 읽고 `node_modules`, `dist`, `build`, `out`, `coverage`, binary와 source root 밖을 제외한다. 허용 text 파일의 encoding/I/O 오류는 숨기지 않으며 canonical relative path로 정렬한다. 새 scanner contract와 영향 5개 class의 focused 결과는 6 suites / 214 tests, failures/errors/skipped 0이다.

아래 V8 handoff refresh 결과는 historical artifact 생성 기록이다. 현재 V9 structure engine 검증 결과는 위 `Structure engine V9` 절과 [Project Status](../project-status.md)를 따른다.

```text
gradlew.bat test --console=plain --no-daemon
```

Transport compression final tree의 결과는 204 suites / 2,118 tests / failures 0 / errors 0 / skipped 0, aggregate JUnit XML 622.904초, Gradle wall 10분 37초로 첫 실행에서 clean pass했다. 이후 backend production Java, resource, Gradle/shared fixture를 바꾸지 않고 artifact 실행과 문서만 갱신했으므로 full regression을 반복하지 않았다.

V8 `RealMatchApiV1ArtifactWriter`는 당시 clean full XML, source binding, options/roster/Draft/result/final snapshot/structured participant/hash/Random/ability profile과 same-request replay를 검증했다. 그 artifact와 source hash는 historical evidence이며 V9 handoff를 생성할 때 재사용하지 않는다.

Writer는 공식 폴더를 바로 덮지 않고 두 fresh JVM에서 candidate A/B를 생성했다. JSON 6개와 `SHA256SUMS.txt`가 byte-for-byte exact였고 semantic audit가 통과한 뒤 `build/reports/real-match-api-v1/`로 승격했다. 고정 V8 결과는 GEN(BLUE) 승, `NEXUS_DESTROYED`, 3,430초, output hash `bdc597af083aa4f081cf4fe7a242d0e36eec7744b186d998d6f83b717648e874`다. Transport compression resource binding까지 반영한 manifest 6/6 raw SHA가 통과했고 manifest raw SHA-256은 `9767356ce01243ff67441354a24d2d54df86fd30ed69cb57397ed36629876fad`다. Fixed response JSON 의미는 이전 handoff와 exact이며 runtime은 이 report를 읽지 않는다.

이 backend V8 handoff milestone 당시에는 frontend 파일을 바꾸지 않아 npm build와 Playwright를 실행하지 않았다. Balance 변경이 없으므로 B2 calibration, B3 holdout, Final 13G-B, 대규모 diagnostics와 baseline generator도 실행하지 않았다.

### Real Match Frontend V1-A

V1-A reference 변경은 backend full regression 대신 다음 frontend 경계를 검증한다.

```text
cd frontend
npm run reference:check
npm run reference:verify
npx tsc -b
npm run build
```

`reference:check`는 현재 handoff에서 다시 만든 projection과 checked-in JSON의 byte equality를 확인한다. `reference:verify`는 10팀/50명 stable ID, GEN/T1/73/Game 1, Draft 20개와 assignment 10개, cross-screen final state, engine/output hash, 10명×12 ability rating과 canonical signed-int64 seed 경계를 확인한다. Browser smoke는 대시보드/수신함, 경기 설정, 자동 Draft, 재생, 결과를 1440×900과 1280×720에서 클릭하고 가로 overflow, 57:10 seek, 재생/일시정지/속도/event 선택, modal/ability/integrity, console과 비정적 network 요청을 확인한다. 이 검증은 live API를 호출하지 않는다.

### Real Match Frontend V1-B

V1-B는 `npm run live:verify`로 options/full response strict runtime validation, canonical signed-long client 경계, 10팀/50명과 Draft/result/timeline/integrity 정규화를 확인한다. `npm run bundle:verify`는 LIVE 초기 entry가 checked-in reference payload를 포함하지 않고 reference adapter가 lazy chunk로 분리되는지 검사한다. `reference:check`, `reference:verify`, `npx tsc -b`, production build도 함께 실행한다.

Browser E2E는 실제 backend와 LIVE frontend를 동시에 켜고 고정 GEN/T1/73, 비고정 HLE/DK/-73, cancel/late response/retry, double click 1 POST, options network 오류/retry/no fallback, Draft→Playback→Result와 결과→Playback no-refetch를 검증했다. 1440×900과 1280×720에서 레이아웃과 console error 0을 확인했다. 전체 결과와 성능 수치는 [Real Match Frontend V1-B](real-match-frontend-v1-b.md)에 기록한다. Backend production을 바꾸지 않은 frontend-only milestone이므로 backend full regression은 실행하지 않는다.

### Real Match Performance Baseline V1

공식 성능 capture는 default `test`에서 제외된 `diagnostic` tag와 전용 single-fork task로만 실행한다. 한 fresh JVM에서 GEN/T1/73을 먼저, HLE/DK/-73을 다음에 실행하며 fixture마다 warmup 1회와 measured 3회를 병렬 없이 수행한다.

```text
gradlew.bat runRealMatchPerformanceBaselineV1 --console=plain --no-daemon
```

각 iteration은 요청 validation/preflight, roster/Draft/input 준비, MatchEngine, series finalization, output integrity, response mapping, JSON serialization을 test-side에서 분해하고, 별도의 실제 random-port HTTP replay를 수행한다. 두 경로의 typed response와 result/output/replay/timeline/Random이 exact여야 run이 유효하다. HTTP body byte와 그 body의 offline gzip도 기록하지만 서버 압축은 켜지 않는다. Section byte는 top-level value를 각각 독립 직렬화한 값이라 합산하지 않는다.

빠른 contract 검증은 다음 class로 실행한다.

```text
gradlew.bat test \
  --tests com.lolfm.controller.RealMatchPerformanceBaselineV1ArtifactsTest \
  --console=plain --no-daemon

gradlew.bat test \
  --tests com.lolfm.application.RealMatchPerformanceBaselineV1HarnessTest \
  --console=plain --no-daemon
```

Contract test는 incomplete schedule, Fixture A drift, output/result/Random/HTTP tamper, warmup 제외 min/median/max, gzip, manifest와 default diagnostic 제외를 검증한다. Harness test는 Fixture A 계측 ON/OFF의 complete response와 output/replay/timeline/Random exact parity를 확인했다. Build task가 추가된 최종 executable tree의 complete backend regression은 198 suites / 2,097 tests / failures 0 / errors 0 / skipped 0, Gradle wall 15분 21초로 한 번에 clean pass했다.

공식 capture 첫 시도는 HTTP raw `JsonNode` field order와 typed response canonical order를 직접 비교해 의미가 같은 응답을 mismatch로 분류했고, finalizer가 `Invalid or partial HTTP observation`으로 거부해 공식 파일을 쓰지 않았다. HTTP body를 공식 typed `REAL_MATCH_RESPONSE_V1`으로 역직렬화한 뒤 비교하도록 test-only 코드를 고치고 round-trip 집중 테스트를 통과한 다음 fresh output에서 capture가 성공했다. Production Java/resource/Gradle wiring은 이 교정으로 바뀌지 않아 complete regression은 반복하지 않았다.

성공한 결과는 `backend/build/reports/real-match-performance-baseline-v1/`에 다음 다섯 파일로 남는다.

- `real-match-performance-baseline-v1-contract.json`
- `real-match-performance-baseline-v1-runs.csv`
- `real-match-performance-baseline-v1-summary.json`
- `real-match-performance-baseline-v1-analysis.md`
- `SHA256SUMS.txt`

Status는 `REAL_MATCH_PERFORMANCE_BASELINE_CAPTURED`, run은 warmup 2 + measured 6으로 완전하며 manifest 4/4 raw SHA가 통과했다. Manifest raw SHA-256은 `c9b4659c4d602fb33c7295885cdc2685a4991469cc4cc0b097ca2d1a20cb26ee`다. 이 timing은 현재 측정 환경의 관찰값이며 correctness 또는 brittle latency gate가 아니다.

### Real Match runtime hardening / Auto Draft scalability V1

`bootRun`은 CPU 집약 Real Match 서버에서 Spring Boot C1-only optimized launch를 사용하지 않는다. 빠른 설정 및 Draft contract 검증은 다음 순서로 실행한다.

```text
gradlew.bat verifyRealMatchBootRunRuntimeHardeningV1 --console=plain --no-daemon
gradlew.bat test \
  --tests com.lolfm.draft.AutoDraftScalabilityScheduleV1Test \
  --tests com.lolfm.controller.RealMatchRuntimeAutoDraftScalabilityV1ArtifactsTest \
  --console=plain --no-daemon
gradlew.bat test \
  --tests com.lolfm.draft.AutoDraftObservationHarnessV1Test \
  --console=plain --no-daemon
gradlew.bat verifyRealMatchAutoDraftCrossJvmV1 --console=plain --no-daemon
```

Contract는 `bootRun.optimizedLaunch=false`와 `TieredStopAtLevel` JVM arg 부재, 기존 performance manifest raw SHA와 4/4 entry, 12-fixture/양 side 10팀, canonical JSON/CSV, manifest one-byte tamper 거부와 default diagnostic 제외를 검사한다. Draft harness는 production implementation과 결정/점수/alternatives/bans/picks/final role/Match assignment를 비교하고 JFR ON/OFF, same-JVM counter, 20턴 BAN/PICK coverage를 검증한다. Cross-JVM task는 GEN–T1 production Draft/final-assignment/input identity를 두 fresh JVM에서 byte 비교한다. Timing 숫자는 assertion gate가 아니다.

Final executable tree에서는 `gradlew.bat test --console=plain --no-daemon`을 한 번 실행했고 201 suites / 2,106 tests / failures 0 / errors 0 / skipped 0, JUnit XML 1,125.077초, Gradle wall 19분 2초로 통과했다. 이후 production Java/resource/build wiring을 바꾸지 않았다.

공식 runtime evidence는 fixture마다 별도 fresh `bootRun`/JAR JVM과 전용 임시 port를 사용한다. `runRealMatchExternalRuntimeProbeV1`에 base URL, launch mode, 소유 PID와 frozen fixture/output path를 전달하면 options만 preflight한 뒤 first/warm simulation을 실행한다. Probe는 실제 `jcmd VM.flags -all`/`Compiler.codecache`, HTTP status/body/encoding, output/replay/timeline/Random identity를 검증한다. 다른 서버를 broad kill하지 말고 자신이 시작한 PID만 종료한다.

네 runtime JSON이 `build/reports/real-match-runtime-auto-draft-scalability-v1-inputs/`에 있으면 다음 diagnostic이 global warmup 1회 후 12 fixture×2 measured Draft를 순차 실행한다.

```text
gradlew.bat runRealMatchRuntimeAutoDraftScalabilityAuditV1 \
  --console=plain --no-daemon
cd build/reports/real-match-runtime-auto-draft-scalability-v1
sha256sum -c SHA256SUMS.txt
```

공식 결과는 full Draft median/p90/max 11.173/13.420/15.412초, BAN median 733.136ms, PICK median 487.298ms, 준비 구간 내 Draft median share 99.9901%다. Exact counter는 Draft당 replan 1,362회, candidate generation 680회/8,160개, action evaluation 1,560회다. JFR CPU/allocation 상위는 `DraftAvailability`, `PreDraftPlanner.candidatePlanValue`, `RoleAssignmentSolver`였지만 sample/profiler evidence일 뿐 exact byte나 인과 비율은 아니다.

Artifact status는 `REAL_MATCH_RUNTIME_HARDENED_AND_AUTO_DRAFT_SCALABILITY_AUDIT_CAPTURED`, manifest 7/7과 raw SHA-256 `751cb19ccf55b34cc0bf4a410a292ba66df4e84d566dd1e217b4a68712d3be8b`다. 이 report는 correctness input이나 production source of truth가 아니며, search/scoring/tuning/cache 변경 없이 다음 `DRAFT_ENGINE_PERFORMANCE_HARDENING_V1`의 기준선으로만 사용한다.

### Auto Draft Variety V1

Seeded selection의 빠른 correctness와 fresh-JVM 검증은 다음처럼 실행한다.

```text
gradlew.bat test --tests com.lolfm.draft.AutoDraftSelectorTest --console=plain --no-daemon
gradlew.bat test --tests com.lolfm.draft.AutoDraftVarietyV1ProductionIntegrationTest --console=plain --no-daemon
gradlew.bat verifyAutoDraftVarietyV1CrossJvm --console=plain --no-daemon
```

고정 population diagnostic은 default `test`에서 제외된다. LCK 10개 순환 fixture × 8 seeds, 총 80 production Draft와 fixture별 same-seed 10건만 실행하며 Match Simulator나 대규모 balance population은 실행하지 않는다.

```text
gradlew.bat runAutoDraftVarietyV1Diagnostic --console=plain --no-daemon
cd backend/build/reports/auto-draft-variety-v1
sha256sum -c SHA256SUMS.txt
```

공식 실행 status는 `AUTO_DRAFT_VARIETY_V1_ACCEPTED`이고 manifest raw SHA-256은 `772d1b5c55cb254cb3eb06149098e56730daca302fe0c04a88d13ed46afccd51`다. 10/10 fixture가 complete Draft identity와 final pick tuple variety gate를 통과했고 correctness error는 모두 0이다. Rank/loss 분포는 정책 reachability 관찰값이지 balance, 승률 또는 brittle unit-test oracle이 아니다. 전체 설계와 결과는 [Auto Draft Variety V1](auto-draft-variety-v1.md)에 있다.

### Draft Engine performance hardening V1

빠른 계약/경계 검증과 fresh JVM 재현성은 다음처럼 실행한다.

```text
gradlew.bat test \
  --tests com.lolfm.draft.DraftComputationContextTest \
  --tests com.lolfm.controller.DraftEnginePerformanceHardeningV1ArtifactsTest \
  --console=plain --no-daemon
gradlew.bat verifyDraftEnginePerformanceCrossJvmV1 --console=plain --no-daemon
```

`DraftComputationContextTest`는 0/duplicate/1–5 champion 경계, canonical key와 immutable value, cached/uncached full Draft 및 모든 root score exact equality, 물리 계산 감소, 100회 fresh lifecycle, 실패 뒤 격리, singleton engine의 동시 same/different input, static mutable cache 부재를 검증한다. 기존 Draft core/evaluation/search/semantic/integration/scenario/observation과 함께 실행한 focused 묶음은 58 tests / failures 0 / errors 0 / skipped 0이다. Cross-JVM A/B output SHA-256은 모두 `abc0bf8ffd57d87c6bded2d4a58cb8bacac6c12ee9bb4defdf8e361738273511`였다.

성능 diagnostic은 default `test`에서 제외한다. Candidate는 full regression 전에 threshold와 evidence completeness를 확인하고, official task는 final executable tree의 clean full regression 뒤 실행한다.

```text
gradlew.bat runDraftEnginePerformanceCandidateV1 --console=plain --no-daemon
gradlew.bat test --console=plain --no-daemon
gradlew.bat generateRealMatchApiV1HandoffRefreshOfficialV1 --console=plain --no-daemon
gradlew.bat runDraftEnginePerformanceHardeningV1 --console=plain --no-daemon
cd build/reports/draft-engine-performance-hardening-v1
sha256sum -c SHA256SUMS.txt
```

Candidate/official diagnostic은 global warmup 1회, 12 fixtures × 2 cached measured Draft와 fixture별 uncached reference 1회를 순차 실행한다. Acceptance는 upstream 24개 final Draft/API identity와 동일 JVM uncached reference의 480개 turn decision/component/alternative/root score/counter exact equality다. 과거 timing JVM의 비선택 후보 double bit 비교는 observational field로만 기록한다. Timing gate는 frozen median 11.173초 대비 40% 이상, p90 13.420초 대비 30% 이상 단축이다.

최종 full regression은 203 suites / 2,117 tests / failures 0 / errors 0 / skipped 0, Gradle wall 11분 33초로 첫 실행에서 clean pass했다. Official full Draft median/p90/max는 4.032/4.314/5.854초이고 status는 `DRAFT_ENGINE_PERFORMANCE_HARDENED`다. Manifest 7/7 raw SHA-256은 `ae11f4eb368a8b796a113b32963048a764509b0bb98e27ebce313b7ec645d694`다. Full pass 뒤 실행 코드 변경은 없으므로 official report와 문서 갱신 때문에 full regression을 반복하지 않는다.

### Real Match transport compression V1

압축 계약은 actual random-port Spring server의 `RealMatchTransportCompressionV1IntegrationTest`로 검증한다. `Accept-Encoding: gzip`은 HTTP 200, JSON content type, gzip magic/stream, `Content-Encoding`, `Vary: Accept-Encoding`, 단일 압축과 해제 후 fixed result exact를 확인한다. `identity`와 무헤더 요청은 uncompressed JSON이며 gzip 해제 결과와 output/replay/simulator timeline/structured timeline/Random fingerprint가 exact다. CORS와 validation error 의미도 유지한다.

`server.compression.min-response-size=8KB`는 Content-Length가 알려진 응답의 합리적 하한이다. 다만 현재 Tomcat MVC의 negotiated streaming/unknown-length 작은 응답은 gzip될 수 있다. 작은 응답을 controller에서 강제로 압축하거나 해제하지 않으며 identity/무헤더 요청은 계속 uncompressed다.

Focused 및 artifact 흐름은 다음과 같다.

```text
gradlew.bat test \
  --tests com.lolfm.controller.RealMatchTransportCompressionV1IntegrationTest \
  --tests com.lolfm.controller.RealMatchApiV1ControllerTest \
  --tests com.lolfm.application.RealMatchApiV1ServiceTest \
  --tests com.lolfm.application.RealDraftMatchOrchestratorTest \
  --tests com.lolfm.application.RealDraftRandomObservationParityTest \
  --tests com.lolfm.simulator.SimulationRandomFingerprintTest \
  --tests com.lolfm.draft.AutoDraftObservationHarnessV1Test \
  --tests com.lolfm.controller.DraftEnginePerformanceHardeningV1ArtifactsTest \
  --console=plain --no-daemon

gradlew.bat generateRealMatchTransportCompressionV1Candidate --console=plain --no-daemon
gradlew.bat test --console=plain --no-daemon
gradlew.bat generateRealMatchApiV1HandoffRefreshOfficialV1 --console=plain --no-daemon
gradlew.bat generateRealMatchTransportCompressionV1Official --console=plain --no-daemon
```

외부 probe는 caller-owned bootRun/JAR PID, base URL과 fixture를 받아 gzip first/warm, identity, 무헤더 요청을 실행한다. 각 요청에서 raw compressed/decompressed bytes, header, frozen response canonical hash와 output/replay/timeline/Random identity를 검증한다. Fixture별 first는 별도 fresh JVM에서 측정하고 자신이 시작한 PID만 종료한다. 실제 Chrome은 CDP `Network.loadingFinished.encodedDataLength`와 response header를 사용하며 `response.text()`/Blob decoded bytes를 wire bytes로 잘못 보고하지 않는다.

최종 full은 204 suites / 2,118 tests / failures 0 / errors 0 / skipped 0, aggregate XML 622.904초, Gradle 10분 37초로 첫 실행에서 clean pass했다. 두 fixture의 외부 HTTP wire 감소율은 91.701%와 90.766%이고, setup→Draft→playback→result의 Chrome first/warm은 console/page error 및 reference fallback 0이다. Official artifact는 `build/reports/real-match-transport-compression-v1/`의 JSON/CSV/Markdown 5개와 manifest이며 status는 `REAL_MATCH_TRANSPORT_COMPRESSION_AND_LIVE_E2E_ACCEPTED`, manifest raw SHA-256은 `860f6cea4e8dfc42e1a38148dc5c2763331bcd899d784670af4e3222d89a068f`다.

## Generated Reports

다음은 검증 결과 또는 일시 artifact이며 correctness input이나 source of truth가 아니다.

- `backend/build/`, `backend/bin/`
- `backend/*.log`, `backend/*.csv`, generated JSON
- `frontend/dist/`, `frontend/node_modules/`, `*.tsbuildinfo`
- repository `output/`

기존 report를 baseline 참고로 읽을 수는 있지만 architecture/resource contract는 production source와 active JSON에서 복원한다. expected result를 바꾸기 전에는 intended behavior, Random order, eligibility, priority, duplicate mutation, event classification 중 원인을 구분한다.

## Test Memory

`backend/build.gradle`의 `test` task는 `maxHeapSize = '2g'`를 설정한다. 이는 test JVM 전용 heap 상한이며 Spring Boot production JVM 또는 frontend process의 memory 설정이 아니다.
