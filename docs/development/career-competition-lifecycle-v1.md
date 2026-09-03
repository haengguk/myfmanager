# Career Competition Lifecycle V1 implementation and verification

## 구현 결과

Phase A의 다섯 Calendar 결함을 먼저 수정하고 focused gate를 통과한 뒤 Phase B를 시작했다.
Phase B는 source가 충분한 Road/R3~4/Play-in graph와 durable transition 경계를 구현했고, Cup과
Playoffs의 부족한 규칙은 structured blocker로 유지했다. 따라서 최종 제품 상태는
`CAREER_COMPETITION_LIFECYCLE_V1_PARTIALLY_IMPLEMENTED_RULE_SOURCE_GAP`이다.

### Phase A

- pending advance는 mode/original revision/UUID/status/timestamp를 DB와 Calendar GET에서 복구한다.
- frontend pending map은 Career별 V2 map이며 A/B 조회와 정리가 서로 영향을 주지 않는다.
- PAUSED/BLOCKED/CANCELLED/not-ready는 날짜 이동·dispatch 0, 불완전 COMPLETED는 transition
  blocker다.
- completed replay는 frozen `commandResult`와 응답 시점 live `calendar`를 분리한다.
- R1~2 overlay V2는 schedule/team/mode/root seed/bound Series까지 hash에 넣고 V1/date/seed는
  그대로 둔다.
- 대표 Auto fixture는 Calendar→job→receipt/outbox→standings를 한 번 적용하고 replay mutation 0이다.

### Phase B

- classpath executable rule resource와 byte hash 검증을 추가했다.
- Flyway V7에 competition cycle/instance/seed/fixture/output/application ledger를 추가했다.
- R1~2 final ranking을 한 번 봉인해 Road 5경기와 R3~4 40경기를 결정적으로 materialize한다.
- pure aggregate가 Road/Play-in의 predecessor winner/loser를 순서대로 resolve하고 qualification
  output을 exactly once 만든다.
- Calendar API에 compact competition/stage/progress/next fixture/source gap/external limitation을
  additive하게 노출했다.
- 기존 Dashboard rail과 진행 버튼 위치를 유지하고 대회 상태 strip만 추가했다.

## Source integrity

검증 시 raw source SHA는 다음과 같았고 요청에 제시된 값과 일치했다.

- README: `853851cb54843a6b5393d89915220220faada8b9284593822bd271af837bab26`
- Calendar JSON: `b47a681950382b3a67be7d4d7d43ed957796470b667c490dc4ce51e2bf3f7e01`
- Official report: `86b16a278d09763260bdd46b0be1047146eca02c2ebd893f66be6e74f7812b0a`
- Source ledger: `0dd2a2818d24d3e212e9f00b51790b8f599b0b57ef67e23486491d80a2dd09b6`

원본은 읽기만 했고 `/일정/lck일정/`, `/능력치/`, `/선수정보/` ignore 경계를 유지했다.

## 검증

Phase A backend gate는 Career Calendar/League persistence/controller의 3 suite가 42초에
`BUILD SUCCESSFUL`이었다. frontend `career:verify`와 production build도 통과했다.

Phase B focused 명령은 `CareerCompetitionRulesTest`, `CareerModePersistenceTest`,
`CareerApiV1ControllerTest`, `LeagueRelationalPersistenceAndJobTest`를 실행했다. 검증 범위는
Road/Play-in exact routing, R3~4 40 fixture uniqueness/collision/determinism/record carry,
V7 migration, R1~2 import replay, completion receipt replay/cross-scope 거부와 restart 복구,
Calendar/API 회귀다. 최종 focused 결과는 4 suites / 19 tests / failures 0 / errors 0 /
skipped 0, Gradle wall 33초, `BUILD SUCCESSFUL`이다.

Frontend는 `npm run career:verify`와 `npm run build`를 실행했다. 15개 계약 시나리오와
`CAREER_COMPETITION_LIFECYCLE_V1_FRONTEND_CONTRACT_VERIFICATION_PASSED` marker가 통과했고,
production build는 153 modules를 transform했다. Browser 자동화용 격리 backend는
V7 migration과 8085 startup까지 성공했지만, WSL browser host에 Chrome이 없고 Firefox도
`libasound2t64`가 없어 Playwright browser process를 시작하지 못했다. 사용자 서버/DB는 건드리지
않았고 임시 서버는 종료했다.

Final executable production tree에서 backend complete regression은 정확히 한 번 실행했다. 결과는
269 suites / 2,375 tests / failures 0 / errors 0 / skipped 2, aggregate JUnit XML 1,366.527초,
Gradle wall 23분, `BUILD SUCCESSFUL`이다. 두 skip은 기존 explicit 대형 diagnostic이다. Clean full
뒤 executable production Java/resource/Gradle/shared fixture는 변경하지 않고 문서만 갱신했다.

## 실행하지 않은 검증

90/130경기 전체 LIVE, 모든 Cup/playoff Match 실행, 대형 seed population, balance/calibration/
holdout, fresh-JVM proof bundle, JFR은 실행하지 않는다. source가 없는 edge의 결과를 만들기 위한
simulation도 실행하지 않는다.

## 다음 작업

`CAREER_COMPETITION_RULE_SOURCE_AND_PRODUCT_DECISION_CLOSURE`에서 Cup opponent choice와 Cup/LCK
Playoff routing을 공식 source 또는 명시적 product decision으로 닫고, competition fixture를 기존
Production V9 Auto/Player Series completion verifier에 결속한다. 그 뒤 R3~4 final standings를
Play-in seed로 봉인하는 transition과 다음 season prior-result rollover를 완성한다.
