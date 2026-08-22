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

기본 `test`에는 role-fixed completion helper, flex false-positive cases, identity/catalog/resource/API contract, 소수 representative real keys, `@TempDir` report writer만 남는다. 전체 audit JUnit class는 `diagnostic` tag이며 default task가 제외한다. 따라서 기본 test는 shared full-population report를 만들거나 입력으로 읽지 않는다. Artifact inventory diagnostic은 CSV를 line streaming으로 읽어 같은 header/row semantics를 유지하면서 전체 report tree를 한꺼번에 heap에 올리지 않는다.

Diagnostic 결과를 normal unit-test assertion으로 옮기려면 먼저 그것이 balance observation이 아니라 deterministic invariant인지 확인한다.

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

첫 task는 12 matches의 configuration/Draft/final assignment/complete timeline/Random fingerprint/result exact equality를 검증하고 `build/reports/jungle-tempo-v1-b/pre-tempo-parity-report.json`을 쓴다. Replay provenance hash는 baseline engine V2와 현재 engine V5가 다르므로 equality에서 제외한다. 두 번째 task는 12 fixed same-seed Economy-only/Tempo pair의 readiness와 actual consumption을 기록한다. 이 작은 sample은 구조 확인용이며 calibration이나 production-activation gate가 아니다.

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

`verifyPreJungleTempoParity`는 Batch C engine V4에서 기존 네 profile 12/12 exact gameplay parity로 통과했고 report SHA-256은 `6ba6d0c33332fa4a6eef343a9030fd3f031f6cef3a6d0363c6877db25fb5878f`였다. V5 follow-up도 12/12 exact pass했고 current report SHA-256은 `87577b6c26073fb748ab6d9b9d2bda719437c60381ba99f71b07482bcdeca927`다. Batch C 전후와 V5의 `runJungleTempoCandidateDiagnostic` report SHA-256은 모두 `3f94b100464a48181fccf5a04a5e16f62dea3e17a05b1398215f65afddab1199`로 byte-identical하다. 이는 Economy-only 15/3과 Tempo 15/2 gank/counter-gank, Tempo consumption 15/2를 그대로 보존한다.

Batch C V4 final full regression은 166 suites / 1,951 tests / failures 0 / errors 0 / skipped 0, aggregate JUnit XML 519.521초, Gradle wall 8분 51초로 clean pass했고 당시 production guard는 472 files / `54b53ea30453e39791dd8aa0197e95ed190697ce1b83c010baff1df540c833d9`였다. V5 follow-up final full regression은 166 suites / 1,953 tests / failures 0 / errors 0 / skipped 0, aggregate JUnit XML 513.886초, Gradle wall 8분 49초로 첫 실행에서 clean pass했다. Full 뒤에는 production/shared fixture를 바꾸지 않고 one-major-combat test의 marker 분류만 assertion-only로 좁혔으며 affected 5 tests가 clean pass했다. Full regression budget의 재사용 조건에 따라 full은 반복하지 않았고 immutable baseline도 재생성하지 않았다.

### Final 13G-B1 audit contract and bounded dry-run

B1은 통계 보정 task가 아니라 real-data 감사 입력과 실행 형식을 고정하는 단계다. Schedule은 10개 LCK 팀의 G1 90 fixtures와 all-team Hard Fearless G2 10 fixtures, fixture별 calibration 24 seeds와 holdout 8 seeds를 포함한다. 전체 hash는 `3bb5e81241a3be2a1509e67528e577ae8f48fca94dec5fc15f93ec8ac78052ef`다. Dry-run은 두 reserved lane과 분리된 seed를 사용하므로 calibration 또는 holdout 표본으로 세지 않는다.

```bash
cd backend
./gradlew test \
  --tests 'com.lolfm.application.Phase13GB1AuditScheduleTest' \
  --tests 'com.lolfm.application.Phase13GB1AuditContractTest'
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

B1 task는 `diagnostic` tag라 기본 `test`에서 제외된다. Hardened 실행 결과는 2 focused suites / 7 tests와 1 dry-run test가 모두 clean pass했고 manifest 6/6이 일치했다. Dry-run JUnit suite는 18.445초, Gradle wall은 33초였다. 이어 B1에서 추가했던 Gradle/test configuration까지 포함해 backend full regression을 첫 실행에서 168 suites / 1,960 tests / failures 0 / errors 0 / skipped 0, aggregate JUnit XML 540.965초, Gradle wall 9분 15초로 clean pass했다. `calibrationExecuted=false`, `holdoutExecuted=false`, `productionDecision=NOT_EVALUATED`가 summary contract다. 단일 dry-run의 승패·경기 시간·profile 차이로 balance 결론을 내리거나 expected value를 바꾸면 안 된다. 다음 B2는 calibration seed만 소비하며, holdout seed는 B3 전까지 실행하지 않는다.

## Generated Reports

다음은 검증 결과 또는 일시 artifact이며 correctness input이나 source of truth가 아니다.

- `backend/build/`, `backend/bin/`
- `backend/*.log`, `backend/*.csv`, generated JSON
- `frontend/dist/`, `frontend/node_modules/`, `*.tsbuildinfo`
- repository `output/`

기존 report를 baseline 참고로 읽을 수는 있지만 architecture/resource contract는 production source와 active JSON에서 복원한다. expected result를 바꾸기 전에는 intended behavior, Random order, eligibility, priority, duplicate mutation, event classification 중 원인을 구분한다.

## Test Memory

`backend/build.gradle`의 `test` task는 `maxHeapSize = '2g'`를 설정한다. 이는 test JVM 전용 heap 상한이며 Spring Boot production JVM 또는 frontend process의 memory 설정이 아니다.
