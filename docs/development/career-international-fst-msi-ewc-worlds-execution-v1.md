# Career 국제대회 실행 V1

네 국제대회의 같은 시즌 실행·결과 연결을 구현하고 집중·브라우저·최종 전체 회귀를 완료했다.
기준 HEAD: `c0a3fe1938945daeb2624c8f6e5be8dd49a9279a`.

## 실행과 저장

국제대회 참가 등록은 `CareerInternationalParticipants`에서 해외 지역 순위 입력을 받고,
LCK는 Cup/Road to MSI/봉인된 시즌 최종 순위를 소비한다. 현재 공급자는 등록된 주전 5명의
각 포지션 gameplay 능력치 12개씩, 총 60개 값을 같은 가중치로 합산한다. 내림차순 동률은
구조화된 지역·팀 코드의 사전순으로 고정한다. 해외 리그를 실제로 치렀다는 뜻이 아니다.
향후 해외 리그 결과 공급자로 이 인터페이스를 교체할 수 있으며, 등록된 대회에서는 다시 호출하지 않는다.

`career_international_state`(V12)는 Career/연도/대회별 참가 경로·지역 시드·pool·정책·선정 입력,
대진 추첨·전이·최종 순위를 저장한다. 참가자 로스터에는 stable player ID, 이름, 포지션,
능력치, 챔피언/역할별 숙련도 값 전체가 들어간다. 경기 binding과 Series checkpoint에도
해당 두 팀의 불변 입력을 보관한다. 매 게임 새 Team/Player를 조립한다. 현 데이터는 주전 5명이며
공식 후보/교체 선수·코치 등록까지 구현했다고 주장하지 않는다.

국제대회에서는 LCK를 포함해 `지역:팀코드`를 사용한다. 기존 국내 팀 코드는 유지한다.
실행은 기존 Production V9 Draft/Player/Auto kernel, completion receipt, command UUID,
Auto job lease/heartbeat/fence를 공유한다. 검증된 실제 경기 receipt의 로스터 identity도
고정 입력과 비교한다. BO1/3/5 모두 시리즈 안에서 Hard Fearless를 누적하고 새 시리즈에서 초기화한다.
추첨의 SHA256 순서는 Career root seed/연도/대회/단계/용도/정책과 결속되며 경기 RNG를 소비하지 않는다.

V2/V3 국내 JSON은 수정하지 않았다. V3의 미실행 국제대회만 별도 V1 규칙으로 활성화하고,
완료된 국내 fixture/binding/receipt 및 최종 순위 봉인은 보존한다. 기존 국내 `M1`처럼
서로 다른 대회의 경기 ID가 중복되는 경우도 completion/Calendar 조회에 대회 ID를 포함한다.
국제 등록 없이 fixture 이력이 존재하는 개별 대회는 `INTERNATIONAL_EXTENSION_HISTORY_CONFLICT`로
차단하고 다른 대회·기존 binding/receipt 조회를 보존한다. V2 호환 정책은 기존 동작을 유지한다. 로스터 파일 변경으로 이전 참가자를 다시 선정하거나
최신 선수 값으로 복원하지 않는다. 공통 챔피언/드래프트/엔진 provenance가 달라진 실행은
기존의 fail-closed 정책을 유지하며, 엔진 버전 간 이력 마이그레이션은 별도 범위다.

## 공식 규칙과 V1 보충 정책

공식 원문은 [규칙 근거 문서](career-competition-rule-source-closure-v1.md)의 C4–C8을 따른다.
현실 2026 참가팀과 확정 pool을 게임 내 대회 결과로 복사하지 않는다.

| 대회 | 구현 전이 | 자격 연결 |
|---|---|---|
| First Stand | 8팀, 지역 중복 없는 2개 GSL 그룹, 교차 준결승·결승, 13 BO5 | 실제 Cup LCK 1/2; 최종 지역 성과를 MSI에 전달 |
| MSI | 4팀 Play-in 6 BO5, 8팀 본선 14 BO5, U2 패자의 반대 half L2 진입, 결승 reset 없음 | 실제 Road 시드; FST 지역 성과로 직행·pool 결정; Worlds 보너스 |
| EWC | 16팀 4개 GSL 그룹, 8강·4강·3위전·결승, 28시리즈 | 직접/예선 대체/첫 cycle 타이틀 슬롯 구분; Worlds 보너스 없음 |
| Worlds | 19팀 중 Play-in 4팀/6 BO5 → Swiss 16팀/33시리즈 → KO 7 BO5 | 봉인된 국내 순위와 MSI 성과; 3승 진출·3패 탈락 |

- 지역 성과는 각 대표의 최종 순위 벡터를 좋은 순위부터 사전식 비교한다. 없는 두 번째 대표는
  마지막으로 비교하고, 완전 동률은 해당 대회 seed에 결속한 추첨으로 결정한다. 공동 3위·5위 등은
  경기하지 않은 순위결정전을 꾸미지 않고 공동 순위로 저장한다.
- FST 첫 cycle은 공식 기준 LCK/LPL 1시드 pool1, 다른 지역 1시드 pool2, LCK/LPL 2시드 pool3이다.
  다음 시즌 생성은 이번에 구현하지 않으며, seasonOrdinal>1에 첫 기준을 반복 적용하지 않는다.
- MSI 직행 추가 슬롯은 FST 우승 지역 자체가 아니라 **두 슬롯을 가진 지역 중 FST 성적이 가장
  높은 지역의 2시드**다. [Riot 공식 설명](https://lolesports.com/en-US/news/msi-and-worlds-updates).
  CBLOL이 FST에서 우승해도 11팀/7직행/4 Play-in을 유지한다. 본선 pool1/2/3은 FST 지역 순위
  상위 두 지역/다음 두 지역/나머지 두 지역의 1시드, pool4는 추가 직행 2시드와 Play-in 승자다.
- EWC 원문 `LCP#2`를 그대로 적용한다. 지역 예선 전체 대신 등록된 적격 후보 중 기존 진출자를
  제외한 다음 순위를 배정한다. LCK 입력은 실제 Road 대표 순위 다음 R1–R2 순위이며 Road를
  공식 EWC 예선이라고 표시하지 않는다. 첫 cycle은 실제 전년도 우승을 만들지 않고, 공식
  §2.5.4의 우승팀 부재 시 LCK 대체 조항을 기준으로 **일반 LCK 슬롯 배정 후** 남은 최상위 팀을
  타이틀 슬롯에 둔다. 이는 명시적인 초기 게임 정책이다.
- Worlds 기본 5지역×3+CBLOL2에 MSI 우승 지역과 다른 최상위 지역이 각 한 슬롯을 더 받는다.
  MSI 우승팀의 LCK PO 참가 조건은 실제 최종 순위 6위 이내로 판정한다. 5/6위 우승팀은 마지막
  지역 슬롯을 우선 배정받으며, 조건 미충족 시 지역 보너스는 유지하고 지역 순위로 대체한다.
  해외 PO 조건은 임시 지역 순위 6위 이내 정책이다. 현실과 다른 CBLOL 보너스도 처리한다.
- Worlds Play-in은 MSI 지역 성과 하위 네 지역의 마지막 시드다. Swiss pool1은 상위 네 지역 1시드,
  pool2는 하위 두 지역 1시드+상위 두 지역 2시드, pool3은 중위 두 지역 2시드+상위 두 지역 3시드,
  pool4는 나머지 직행 세 팀+Play-in 승자다.
- Swiss는 같은 전적을 유지하고 첫 라운드 pool1–4/pool2–3·다른 지역, 이후 재대결 금지를
  적용한다. 유한한 완전 매칭 탐색이 모든 가능한 배치를 확인한다. 불가능할 때만 첫 지역 제한
  또는 재대결 금지를 완화하고 scope/사유를 저장한다. 전적 그룹은 완화하지 않는다.
  8강의 두 3–0 팀은 반대 half에서 3–2 팀과 만나며 나머지는 추첨한다.
- RoFS 보유 팀은 첫 픽, 상대는 red를 선택하는 게임 정책이다. RoDS 보유 팀은 blue와 첫 픽을
  모두 선택한다. 이후 세트는 직전 패자 RoFS. EWC 그룹 최종전은 승자조 출신이 선택권을 갖는다.
  경기별 날짜는 공식 대회 창 안에서 배정한 V1 게임 일정이며 원문의 모든 날짜별 경기 배치를
  그대로 복제했다는 의미는 아니다.

Calendar/Series는 참가자·단계·대진·결과·다음 명령을 기존 화면에 추가한다.
국제 Player Draft는 응답의 고정 주전 lineup, 결과·리플레이는 저장된 경기의 팀/선수 정보를 사용한다.
LCK 전용 setup options에 해외 팀이 없어도 표시되며, BO1 game rail은 한 게임만 표시한다. 아시안게임은
`EXCLUDED_BY_GAME_POLICY`로 명시하고 가짜 경기/receipt를 생성하지 않는다. KeSPA와
연말 rollover 제한은 유지한다. 국제대회 결과는 국내 최종 순위를 다시 봉인하지 않고 별도로 조회한다.

## 검증 기록

환경은 Java 21(`JAVA_HOME=/tmp/lolmanager-temurin21`)이며 기존 Gradle 2-worker 설정을 유지했다.
새 테스트 클래스는 순수 대회 전이 1개이고 실제 경기·DB 검증은 기존 두 클래스를 확장했다.

```bash
cd backend
./gradlew test \
  --tests 'com.lolfm.career.CareerInternationalTournamentTest' \
  --tests 'com.lolfm.application.CareerDomesticExecutionTest.foreignRostersRunThroughExistingAutoPlayerReceiptsAndDurableCheckpoint' \
  --tests 'com.lolfm.league.CareerModePersistenceTest.internationalUpgradePreservesUsedV3CupHistoryAndFrozenRegistrationAcrossRestart' \
  --console=plain --no-daemon
```

- 3 suites / 7 tests, 실패·오류 0, Gradle **3분 58초**. 네 대회 전체 전이, CBLOL FST/MSI 우승,
  조건부 LCK MSI 우승팀, EWC pool/교차 이동, Swiss 전적·형식·인원과 완전 매칭 탐색을 확인했다.
- 이후 고정 lineup 응답을 보강하고 위 실행에서 순수 전이 selector만
  제외한 2 suites / 2 tests를 다시 실행했다. 실패·오류 0, **3분 28초**.
- 실제 해외 Auto BO1 및 Player BO1은 공통 Draft·Production V9·receipt 검증·DB/checkpoint 복구를
  사용했다. 전체 대회/국내 시즌 연결은 작은 테스트용 검증 결과 입력으로 production 전이와
  DB 자격 계산을 통과시켰다. 실제 엔진으로 네 대회 전체를 완주한 증거가 아니다.
- 기존 V3 Cup 완료 기록의 binding/receipt/instance 보존, 등록 후 공급자 재호출 없음,
  중복 completion 재사용, 국내 최종 순위 봉인 보존, Asian 제외·KeSPA gate를 확인했다.

```bash
cd frontend
npm run career:verify
npm run series:verify
npm run build
```

Career 28개, Series·Player Draft 84개 계약 검증 및 build가 통과했다.
Series verifier는 기존 Player Draft verifier도 포함한다. 국제 참가 계약·지역 시드
중복·위조 승자와 해외 child lineup, setup 목록 없이 저장 경기 결과를 여는 회귀 사례를 추가했다.

대표 실제 브라우저는 localhost 전용 임시 H2/기존 UI에서 검증했다.

- Auto: `LEC:KC`–`CBLOL:LOS`의 `RUNNING` 표시 → 새로고침 → 완료 결과 한 번 저장 → 다음 대진.
- Player: `LCK:HLE`–`CBLOL:LOS` BO1 → 동일 Series/child의 서버 재시작 복구 → 수동 10선택과 AI
  응답으로 20턴 완료 → 실제 Production V9 → HLE 1:0 완료 → 저장 리플레이/최종 통계 → Calendar
  복귀·결과 확인 → `1/8` 완료와 다음 Auto 대진.
- 브라우저가 발견한 LCK 전용 목록 의존을 Draft와 결과 adapter에서 수정하고 재검증했다.
  BO1의 불필요한 예정 게임 표시도 수정했다. 수정 전 실패를 성공 기록으로 계산하지 않았다.
- 준비 데이터는 임시 DB에 실제 등록 로스터 16팀과 테스트용 국내 선정 순서를 설치한 것이다.
  이 UI용 EWC 진입 fixture는 시즌 자격 검증을 대신하지 않는다. 사용자 DB는 초기화하지 않았다.
- 화면 기록: `output/playwright/career-international-player-result.png`,
  `output/playwright/career-international-calendar-return.png`. 전용 서버·브라우저는 종료했다.

전체 검증 명령은 `cd backend && ./gradlew test --console=plain --no-daemon`이다.
첫 전체 실행은 **23분 57초**, **264 suites / 1,999 tests / 실패 5 / 오류 0 / 기존 skip 2**였다.
`test`는 실제 실행됐으며 5개 Gradle 작업 중 2개 실행/3개 UP-TO-DATE였다.

확인한 실패는 아래와 같이 교정했다. 실행이 종료된 후에 검증 입력을 수정했다.

- migration 기대값 3건: 새 V12 적용에 따라 전체 11→12, V1 이후 10→11, V4 이후 7→8로 교정.
- 국제 binding 보존 1건: 재구성한 객체 참조 비교를 명시적 canonical 내용 비교로 교정.
- 기존 백그라운드 리그 1건: job/receipt 완료와 outbox 순위 적용이 별도 단계인데 테스트가
  job 완료만 기다렸다. 또한 season 응답이 aggregate/fixture를 다른 시점에 읽어 revision 4와
  완료 5경기가 섞였다. 기존 repeatable-read 트랜잭션으로 season 응답 전체를 묶고,
  테스트는 순위 적용도 기다리며 매 중간 응답의 revision/완료 경기 수 일치를 추가로 확인한다.
  최종 5경기·revision 5·시도 1회·실패 없음 조건은 유지했다.

위 실패 사례를 다음 명령으로 집중 재검증했고 **3 suites / 5 tests / 실패·오류 0, 3분 9초**로 통과했다.

```bash
./gradlew test \
  --tests 'com.lolfm.controller.LeagueApiV1BackgroundExecutionIntegrationTest' \
  --tests 'com.lolfm.league.CareerModePersistenceTest.internationalUpgradePreservesUsedV3CupHistoryAndFrozenRegistrationAcrossRestart' \
  --tests 'com.lolfm.league.CareerModePersistenceTest.atomicProvisionReplayPlayerResumeAndFileRestartReuseExistingAuthority' \
  --tests 'com.lolfm.league.CareerModePersistenceTest.v4CareerMigratesToFrozenV5CalendarWithoutBackdatingFoundationBinding' \
  --tests 'com.lolfm.league.LeagueRelationalPersistenceAndJobTest.migratesEmptyAndPreviousSchemaThenRestartsFromSameFile' \
  --console=plain --no-daemon
```

최종 두 번째 전체 실행은 **22분 1초**, **264 suites / 1,999 tests / 실패 0 / 오류 0 / 기존 skip 2**로
통과했다. `test`가 실제 실행됐고 5개 Gradle 작업 중 1개 실행/4개 UP-TO-DATE였다.
기존 skip은 `PlayerDraftLatencyProfilingV1DiagnosticTest`,
`PlayerDraftPerformanceHardeningV1DiagnosticTest`의 명시적 진단 각 1개다.
첫 실패 → 원인 교정 → 집중 통과 → 최종 전체 통과 순서로 총 2회 실행했다.
최종 통과 후에는 문서만 수정했고 `git diff --check`도 통과했다.
`clean`, 임의 skip/제외, 대형 진단은 추가하지 않았다.

## 남은 범위

해외 리그 실제 시뮬레이션과 결과 공급자, 후속 cycle/시즌 rollover, KeSPA Cup, 구단 운영,
후보/교체 선수 등록은 후속 범위다. 아시안게임은 명시적으로 제외한다.
자동 commit/push/배포는 하지 않았다.
