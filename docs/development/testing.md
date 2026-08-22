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

기본 `test`에는 role-fixed completion helper, flex false-positive cases, identity/catalog/resource/API contract, 소수 representative real keys, `@TempDir` report writer만 남는다. 전체 audit JUnit class는 `diagnostic` tag이며 default task가 제외한다. 따라서 기본 test는 shared full-population report를 만들거나 입력으로 읽지 않는다.

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

Generator는 `CLEAN_PASS`와 source revision property가 없으면 fail-fast한다. Profile schedule은 `BASELINE_V1`, `MATCHUP_ONLY_CANDIDATE_V1`, `FULL_SYSTEM_CANDIDATE_V1` 세 개로 고정되고 각 profile의 Jungle contribution이 `DISABLED_NOT_INTEGRATED`인지 확인한다. 결과는 `backend/baseline/pre-jungle-runtime-v2/`과 동일 bytes의 `backend/build/reports/pre-jungle-runtime-baseline-v2/`에 기록된다. Existing source와 candidate bytes가 다르면 report candidate만 남기고 source overwrite를 거부한다.

V1 task `generatePreJungleRuntimeBaseline`은 immutable predecessor 재생성을 항상 거부한다. Official V2 생성 뒤에는 같은 명령을 새 JVM에서 다시 실행해 source bytes가 exact equality로 승인되는지 확인한다.

현재 hardening focused 묶음은 12 suites / 52 tests, final full regression은 156 suites / 1,978 tests가 clean pass했다. Production guard는 full 전후 456 files / `b7965a1d1ebb9d76f298bc65e957da79c4e7cf2a3d0df35a6eca29ebaa0ab350`으로 동일했다. V2 baseline은 full pass 뒤 생성했고 새 JVM에서 같은 raw SHA `0bce126117683e47ace908c348dbe2448f21592dc5009bd9f4514bb566fadb8e`로 재검증했다.

## Generated Reports

다음은 검증 결과 또는 일시 artifact이며 correctness input이나 source of truth가 아니다.

- `backend/build/`, `backend/bin/`
- `backend/*.log`, `backend/*.csv`, generated JSON
- `frontend/dist/`, `frontend/node_modules/`, `*.tsbuildinfo`
- repository `output/`

기존 report를 baseline 참고로 읽을 수는 있지만 architecture/resource contract는 production source와 active JSON에서 복원한다. expected result를 바꾸기 전에는 intended behavior, Random order, eligibility, priority, duplicate mutation, event classification 중 원인을 구분한다.

## Test Memory

`backend/build.gradle`의 `test` task는 `maxHeapSize = '2g'`를 설정한다. 이는 test JVM 전용 heap 상한이며 Spring Boot production JVM 또는 frontend process의 memory 설정이 아니다.
