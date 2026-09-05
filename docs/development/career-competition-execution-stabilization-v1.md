# Career competition execution stabilization V1

작업일: 2026-09-05. 실제 시작 HEAD: `bab58776b94acade3237c1a0303c12bd9705ee49`.
시작 `git status --short`에는 사용자 파일
`?? docs/development/career-competition-stabilization-and-rule-source-closure-prompt.txt`만 있었다.
기능 검토 커밋 `829916e8a1c1b13bbde890748adebf0f1635748f`와 부모
`8ce3ddc789e0fee54fffbb081bc1f907611d2487`으로 reset/checkout하지 않았다.

## 네 결함과 변경 경계

| 항목 | 원인과 수정 | 사용자가 얻는 동작 |
|---|---|---|
| 1-A 선택 저장 충돌 | V9의 global `choice_hash` PK를 새 Flyway V10에서 Career/year/competition/match 복합 PK로 변경 | 다른 Career·시즌에서 같은 선택 결과를 저장할 수 있고 같은 fixture는 하나만 저장 |
| 1-B 긴 Auto lease | 개별 game·완료 검증과 독립적인 heartbeat; 실패는 sticky; job 소유권과 결과 적용을 하나의 DB transaction에서 fence | lease 기간을 넘는 정상 계산도 주기적으로 소유권을 유지하며 완료. 만료/탈취 worker는 결과·다음 대진을 반영할 수 없음 |
| 1-C 완료 Player replay | command payload와 binding 범위, persisted receipt/application/result/fixture/canonical graph를 검증하는 조기 반환 | 이미 반영된 동일 요청은 Player 복원·completedEvidence·경기 재계산 없이 COMPLETED 반환 |
| 1-D 화면 요청 격리 | 선택/재조회/unmount에서 abort+generation 무효화, 응답마다 Career/controller/generation 검증 | A 실행 중 B를 선택해도 늦은 A 성공·오류·finally·Series 이동이 B를 덮어쓰지 않음. 복귀 시 원래 UUID 유지 |

V10은 적용된 V9을 수정하지 않는다. `choice_hash`의 내용·해시 소비자를 바꾸지 않았고 이 값을
참조하는 inbound FK는 없다. 조회·canonical graph의 scoped choice 사용은 유지한다.
기존 별도 scoped UNIQUE도 그대로 유효하다. 신규 DB와 V9 데이터 보존 업그레이드를 모두 확인한다.

Heartbeat 설정은 `lolfm.career.competition.lease.duration-millis=300000`,
`lolfm.career.competition.lease.heartbeat-millis=60000`이 기본값이며 heartbeat가 lease보다 작아야 한다.
단일 daemon scheduler는 game/verifier와 독립적으로 갱신한다. guard는 완료·실패 시 예약을 취소하고
application 종료 시 scheduler를 정리한다. 갱신 false/예외는 해당 worker의 소유권 상실로 고정된다.
짧은 DB 완료 구간과 callback을 직렬화하며, transaction은 job token/status/expiry 조건부 UPDATE로
row를 잠근 뒤 결과/대진/receipt/application/outbox와 job COMPLETED를 함께 commit한다.
마지막 만료 검사 실패도 전체 rollback하고 stale worker의 실패 기록은 새 token을 덮어쓰지 못한다.

Single-node 범위를 유지한다. CPU 계산은 crash·lease 상실 시 같은 frozen input으로 재실행될 수 있다.
여기서 exactly-once는 **검증된 결과와 downstream graph의 영속 반영**을 뜻한다.
기존 command UUID, job ID, bound Series 복구와 최초 completion verifier는 보존했다.
SeriesLifecycleService/SeriesMatchExecutor/실제 simulation kernel은 수정하지 않았다.

## 검증 설계와 비용

AGENTS.md, `lolmanager-verification` skill, `docs/development/testing.md`, 실제 Gradle/npm 선언을
읽고 가까운 테스트를 확장했다. 신규 backend 테스트 클래스는 **0개**다.
`CareerModePersistenceTest`에 네 실행/재요청 테스트를 추가하고 기존 Cup/migration 사례를 확장했다.
`CareerCompetitionTestSupport`의 synthetic verified token과 제어 scheduler를 재사용한다.
화면 검증은 기존 Career 계약 fixture를 export해 **한 파일의 6개 시나리오**로 구성했다.

| 검증 | 직접 관찰한 불변식 |
|---|---|
| 두 Career의 synthetic Cup 40+40 전이 | 첫 Career V9에서40회→V10 업그레이드 전후 row/cycle 동일→둘째40회. application80, detail80, choice6, distinct choice hash3. 실제 엔진80 Series 실행이 아님 |
| season scope/중복 | 같은 Career의2027/2028 동일 내용 선택 저장 허용, 같은 scoped fixture의 다른 hash 중복 거부. 완료 receipt 재요청은 추가 전이 없음 |
| heartbeat 실행·검증 | 제어 Clock을 game 구간480초+verifier240초 진행하면서 pulse. 300초 lease보다 긴720초 논리 실행이 완료되고 engine adapter1회, application1회 |
| 오래된 worker와 재획득 | 301초 만료 후 새 worker가 같은 job 재획득·완료. old worker는 DB fence에서 거부되고 새 상태/receipt를 덮어쓰지 않음. attempt2/application1 |
| 만료 경계·transaction | expiry와 현재 시각이 같은300초에서 갱신 실패; verifier 호출0. 적용 transaction 도중301초 경과 시 최종 fence가 receipt/detail/application/graph 전부 rollback |
| durable Player replay | 최초 completedEvidence/검증1회. 이후 reconcile/start 반복3회에서 player/auto kernel 상호작용0, 관찰 DB row/timestamp/hash 변화0. payload revision·다른 Career binding·손상 receipt pointer 거부 |
| 화면6사례 | 늦은 Auto 시작, 늦은 poll Calendar, A→B→A Player 이동/old finally, B pending 중 A 완료, B pending 중 A 실패, 늦은 detail 응답 |

브라우저 verifier는 Vite가 제공하는 **실제 `CareerDashboardPage`**를 Chromium에 mount하고 API만
제어된 응답으로 바꾼다. transport는 abort를 의도적으로 무시해 identity 검사도 확인한다.
A→B 후 A응답 도착 시 B 유지, A복귀 후 기존 UUID 재사용을 확인했다. DB/엔진까지 연결한 신규
전체 시즌 E2E나 실제5분 대기는 하지 않았다. backend API 기존 대표 테스트가 실제 Auto kernel 연결을
별도로 검증한다. 프로세스 restart 계약은 변경하지 않았으며 기존 persistence/API 회귀에 포함된다.

## 실행한 명령과 결과

backend 명령은 저장소 `backend/`에서 실행했다. PATH에 Java가 없어 Temurin21 경로를 지정했다.
기본 sandbox는 기존 Gradle cache lock을 쓰지 못했으므로 해당 실행만 승인된 escalation으로 수행했다.

```bash
JAVA_HOME=/tmp/lolmanager-temurin21 ./gradlew test --tests com.lolfm.career.CareerCompetitionRulesTest --console=plain --no-daemon
JAVA_HOME=/tmp/lolmanager-temurin21 ./gradlew test --tests com.lolfm.league.CareerModePersistenceTest --tests com.lolfm.league.LeagueRelationalPersistenceAndJobTest --tests com.lolfm.career.CareerCompetitionRulesTest --console=plain --no-daemon
JAVA_HOME=/tmp/lolmanager-temurin21 ./gradlew test --tests com.lolfm.league.CareerModePersistenceTest --tests com.lolfm.controller.CareerApiV1ControllerTest --console=plain --no-daemon
JAVA_HOME=/tmp/lolmanager-temurin21 ./gradlew test --console=plain --no-daemon
```

- Rules focused: 7 tests, `BUILD SUCCESSFUL`, Gradle3분29초.
- Persistence/Rules focused: `BUILD SUCCESSFUL`, Gradle1분33초.
- 최종 production 경계 focused: 2 suites/19 tests, failures0/errors0/skipped0,
  Gradle2분59초. XML 합계129.341초(Career18 + 실제 API1).
- 최종 전체 regression: **260 suites/1,984 tests, failures0/errors0/skipped2**,
  `BUILD SUCCESSFUL`, Gradle36분42초, XML 합계2,139.457초.
  최종 production/migration tree에서 full은 한 번 실행했다. skipped2는 기존
  `PlayerDraftLatencyProfilingV1DiagnosticTest.captureOfficialPlayerDraftInteractiveAndSimulationLatencyProfile`
  및 `PlayerDraftPerformanceHardeningV1DiagnosticTest.capturePairedBackendProbe`다.
  이번 작업에서 테스트 제외나 Gradle 분류를 변경하지 않았다.

네 결함의 구현·저장 호환성·집중/브라우저·최종 전체 회귀 검증을 완료했다.
이는 대회 실행 안정화 판정이며, 새 국제대회 구현이나 시즌 완주 판정이 아니다.

frontend 명령은 `frontend/`에서 실행했다.

```bash
npm run career:verify
npm run build
PLAYWRIGHT_CLI=/mnt/c/Users/guddn/.codex/skills/playwright/scripts/playwright_cli.sh \
PLAYWRIGHT_CLI_SESSION=career-stabilization \
node --experimental-strip-types scripts/verify-career-request-isolation.mjs
```

Career 계약21 PASS/기존 marker4개. 브라우저6 PASS, 해당 시나리오 page error0.
마지막 production build는 TypeScript와153-module Vite build 통과.
공용 Series 계약/코드를 바꾸지 않아 모든 frontend verifier를 나열해 반복하지 않았다.

브라우저 환경은 Chromium headless이며 missing `libnspr4/libnss3`를 `/tmp`에 추출해 로드했다.
Vite5197과 Playwright `career-stabilization` session을 사용했다.
검증 페이지의 ReactDOM import 및 mock command enum 오류는 test-local 준비 문제로 수정한 뒤
6개 실제 화면 시나리오를 통과시켰다. Vite 개발 WebSocket의 local-network 제한 로그와
시나리오 내 page error는 구분한다. production 브라우저 성능/레이아웃 검증을 주장하지 않는다.

## 변경 범위와 남은 한계

규칙 조사는 [rule source closure V1](career-competition-rule-source-closure-v1.md)에 출처·연도·
단계별 경로·현재 blocker·코드/데이터 공백·의존 순서를 기록했다. 새 국제대회, 시즌 말 PO,
KeSPA 실행, 동률 엔진, rollover 및 운영 범위 확장은 구현하지 않았다. production 규칙 JSON과
readiness를 바꾸지 않았고 현실 경기 결과를 가져오지 않았다.

최종 변경 목록은 production Java3개·migration1개, 기존 backend test/helper3개,
frontend component/verifier3개, 문서4개다. 사용자 prompt 파일, `prompts/`, `능력치/`,
`선수정보/`, `일정/lck일정/`, AGENTS와 skill은 보존했다.
자동 commit/push/배포는 하지 않았다. 기존 보고서의 ACCEPTED/test 수치는 역사적 결과로 유지했다.
본 작업에서 시작한 Vite5197과 Playwright session은 모두 종료했다.
최종 문서 수치 반영 후 `git diff --check`를 통과했다. 이후 executable 변경은 없다.
