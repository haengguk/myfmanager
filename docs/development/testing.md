# Testing

## Career 소식함·기록 조회 교정 V1 (2026-09-09)

G1/G2는 기존 CareerRecordsStorageTest의 500개 무관 사건과 부분/미수집 CS 준비로 검증한다.
신규 CareerInboxStorageTest 하나에서 페이지 상한·읽음 반복·파일 재열기·Career/시즌 범위를
확인한다. 기존 ContinuousExecution/Planner와 CareerModePersistenceTest의 lifecycle 파일
복구 흐름으로 실제 계약 철회, 정보성 소식의 진행 독립, 새 조건, 최종 결산 보존을 확인한다.
프런트는 career:verify의 6개 소식함 시나리오 그룹과 build, 격리 브라우저의 읽음→원래 계약
철회→부모 갱신→reload 흐름을 사용한다. 새 경기를 돌려 뉴스 fixture를 만들지 않는다.
통합 후 전체 backend test는 1회, 283 suites/2,243건, 통과 2,241·실패/오류 0·기존 skip 2,
wall 1,923.33초로 통과했다. 프런트는 114 PASS와 build(Vite 7.97초)다.
전체 후 선수 이름 표시 보완은 기존 계약 통합 1건이 28.16초에 통과했다. 추가 전체는 0회다.
상세 명령·원본 결과는
[구현·검증 보고서](career-inbox-and-decisions-v1.md)에 구분해 기록한다.


## Career 성적·개인상·이력 V1 (2026-09-09)

CareerPerformanceAwardsPolicyTest는 작은 점수·자격·슬롯·scope 경계,
CareerRecordsStorageTest는 합성 5세트의 원자성·충돌·컷오프·권리·페이지·파일 복구를 검증한다.
기존 CareerDomesticExecutionTest 실제 Auto/Player 완료, CareerModePersistenceTest 시즌 전환 2회·
이적/임대·은퇴·stale fence, LeagueBoundSeriesCheckpointRecoveryTest를 직접 영향 범위로 확장/실행했다.
G1은 Overseas/Planner 기존 테스트, G2는 career:verify의 실제 TSX 경계에서 확인한다.
프런트는 career:verify/build 및 실제 보존 경기 결과→상→선수/팀→새로고침 흐름을 사용한다.
전체 회귀는 통합 구현 뒤 1회/282 suites/2,238건/실패4·기존 skip2/wall1,638.02초였다.
원본 XML을 보존한 뒤 migration 개수3건과 nullable 등록 연도1건을 수정하고,
순위·조회·성장 보강을 포함한 직접 영향27건이 모두 통과했다(wall153.26초).
추가 전체0회이며 최종 트리의 clean full 통과로 표기하지 않는다.
[정확한 실행 결과와 한계](career-records-performance-awards-and-player-team-history-v1.md)를 참고한다.

## Career 연속 진행·AI 자동 처리 V1 (2026-09-08)

선행 경계는 기존 UpperObjectiveTest, DraftAbilityTest, CareerOverseasExecutionTest의
혼합 저장 startup 사례와 RealismPolicyCompatibilityTest로 검증한다.
본 기능은 CareerContinuousPlannerTest(소유권/당일 종료/자동 대기),
CareerContinuousExecutionTest(기존 날짜/실제 BO3/수동 busy/Player 진입),
CareerContinuousRecoveryTest(파일 DB 재열기/자식 커밋 후 부모 미기록/pause/fence) 3개 집중 클래스다.
각 날짜와 fixture의 합성 준비를 실제 경기 증거와 구분한다. 통계 진단은 추가하지 않았다.

```bash
JAVA_HOME=/path/to/jdk-21 bash scripts/test-linux.sh --tests '*CareerContinuous*Test' --tests '*SimulationRuntimeProfilesTest' --tests '*RealismPolicyCompatibilityTest'
```

이 선택은 33건 통과/2분 25초였고, 미접수 intent의 pause 경합을 보강한 파일 복구 1건은
33초에 통과했다. 준비 기간의 목표 날짜 첫 실패는 실제 제품 조건을 수정해 확인했다.
전체 회귀는 계획된 **1회 / 280 suites / 2,211건 / 15 실패·기존 skip 2 / wall 1,769.57초**로 완료했다.
원본 XML을 `/tmp/continuous-evidence/full-original/`에 보존한 뒤 실패 15건을 포함한 집중 43건이
통과했다(wall 579.81초). 마지막 제품 보강의 직접 영향 68건 중 66건 통과·새 테스트 준비 오류
2건이었고(wall 211.55초), ID/준비 순서만 고친 두 메서드가 통과했다(wall 17.23초).
추가 전체 실행은 0회이며 최종 clean full 통과로 표기하지 않는다. 미해결 실패는 없다.
정확한 선택자·원인·검증별 증거는 현재 원본 로그를 따른다. 해당 과거 보고서는 사용자가 삭제했으며 복원하지 않는다.
프런트는 career:verify/build, 공유 Player policy 및 Series 명령 경계 변경에 따른
player-draft:verify/series:verify를 사용한다. 프런트 최종 Career verifier는 110 PASS 표기, build는 통과했다.

## Career 시작 복구·회귀 비용 V2 (2026-09-08)

Windows 마운트에서의 클래스 로딩 비용을 줄이려면 backend에서 다음 선택 실행 경로를 사용한다.
현재 소스(미커밋·신규 파일 포함)를 읽고 Linux 출력만 사용한다. 최대 2 worker/각 1,536MiB이며
`test --rerun --no-build-cache`로 실제 검사를 수행하고 compile/resources는 증분 결과를 재사용한다.

```bash
JAVA_HOME=/path/to/jdk-21 /usr/bin/time -p bash scripts/test-linux.sh
# 집중 선택자는 동일하게 전달한다.
JAVA_HOME=/path/to/jdk-21 bash scripts/test-linux.sh --tests 'fully.qualified.TestClass.method'
```

출력 위치는 실행 첫 줄에 표시된다. `LOLFM_TEST_BUILD_DIR`로 절대 경로를 지정할 수 있다.
동일 출력은 스크립트 파일 잠금으로 직렬화하며 원본 소스/DB 이동은 필요 없다.
기존 `./gradlew test` 경로와 진단 제외/독립 JVM 검증도 유지한다.
계획된 full **1회는 24분 10.63초**, 274 classes / 2,164 tests 중 2,162 통과,
실패·오류 0 / 기존 skip 2였다. 과거 44분 13초 대비 **45.32% 단축**했으나
50%/22분 6초 및 권장 20분 목표에는 미달했다. 전체 이후 테스트 fixture의 저장 날짜 형식과 월별 요약을
한정 보정한 뒤 직접 호출자 2건 모두 통과했다(1분 39.63초). API/G1 메서드 XML 시간은
각각 49.609초/77.914초다. 제품 코드는 전체 이후 변경하지 않았고,
원본 full과 후속 결과를 구분한다. 최종 fixture의 추가 절감을 full 수치에 더하지 않는다.
구현 범위·유지한 경계·실행 비용은 [V2 결과](backend-regression-runtime-optimization-v2.md)를 참고한다.

## Career 해외 실행 교정·저장 호환 V1 (2026-09-08)

새 테스트 클래스 없이 Tournament/Qualification의 순수 단계·이변 구간 검사, 공유 Execution의
등록 전 정상 시장 복구·독립 경기 게이트·기존 규칙 적용 경계, 기존 파일 DB의 A→B→시즌 이월과
API 계약을 확장한다. 파일 DB의 경기 준비는 기존 통제 helper에 이미 고정된 참가자를 전달하며
실제 장시간 Series를 다시 실행하지 않는다. 브라우저는 저장 진입/보존 안내/새로고침 한 흐름이다.

```bash
# backend/에서 순차 실행. 아래는 전체 실행 전 결합 집중 범위다.
JAVA_HOME=/tmp/career-development-jdk ./gradlew test \
  --tests 'com.lolfm.league.CareerModePersistenceTest.savedPlayerAuthoritySurvivesCatalogReplacementAndFileRestart' \
  --tests 'com.lolfm.career.CareerOverseasExecutionTest.correctionAdoptsUnusedEventsAndPreservesStartedAndCompletedHistory' \
  --tests 'com.lolfm.career.CareerOverseasTournamentTest' \
  --tests 'com.lolfm.career.CareerOverseasQualificationTest' --console=plain --no-daemon
# frontend/
npm run career:verify
npm run build
# 구현·집중 검증 완료 후 backend/의 계획된 전체 1회
JAVA_HOME=/tmp/career-development-jdk ./gradlew test --console=plain --no-daemon
```

프런트 계약 106건과 build, 저장 진입 대표 브라우저 흐름은 통과했다.
전체 전 결합 집중 30건(4분 15초), 실제 League handoff/가격 보존을 보강한 파일 DB 1건(4분 7초)이 통과했다.
계획된 전체는 **1회, 44분 13초, 274 suites/2,162건 중 2,159 통과·1 실패·기존 skip 2**다.
원본은 `/tmp/career-compat-full-evidence/`에 보존했다.
신인 영입 후 수개월의 시장 운영에도 원구단에서 선발할 수 있다고 가정한 fixture를 경기 직전 영입으로 수정했다.
후속 1차 2건/5분 9초에서 실제 Auto는 통과했고, 추가 G1 사례는 진행 모드가 0개인 교착으로 실패하여 결함을 재현했다.
해당 gate 수정 뒤 **G1 + 기존 Calendar/API 2건은 모두 통과했다(8분 5초)**.

```bash
# 전체 이후 1차: 기존 실패 수정 + G1 추가 교착 재현
JAVA_HOME=/tmp/career-development-jdk ./gradlew test \
  --tests 'com.lolfm.league.LeagueAutomatedSeriesRunnerProductionV9Test.calendarDateAdvanceCapturesSettledStartAndAppliesActualAutoExactlyOnce' \
  --tests 'com.lolfm.career.CareerOverseasExecutionTest.registrationWithoutFixturesRepairsThroughMarketDatesAndKeepsIndependentGates' --console=plain --no-daemon
# 최종 gate 수정: G1 및 직접 연결된 Calendar/API
JAVA_HOME=/tmp/career-development-jdk ./gradlew test \
  --tests 'com.lolfm.career.CareerOverseasExecutionTest.registrationWithoutFixturesRepairsThroughMarketDatesAndKeepsIndependentGates' \
  --tests 'com.lolfm.controller.CareerApiV1ControllerTest.createListGetReplayConflictAndStrictErrorsPreserveLeagueState' --console=plain --no-daemon
```

후속 원본/집계는 `/tmp/career-compat-post-full-evidence/first/`, `final/`에 보존했다.
수정은 로컬 등록 gate와 테스트 준비 순서이며 공유 엔진/Random/저장 형식을 바꾸지 않았다.
실패한 실제 Auto와 해당 gate의 Calendar/API 호출자를 집중 검증했으므로 두 번째 전체는 실행하지 않았다.
미해결 실패는 없으며 최종 tree의 clean full 통과로 표시하지 않는다.
[이번 보고서](career-overseas-fixes-and-save-compatibility-v1.md)의 실제 실행/통제 fixture 구분을 따른다.
아래 선행 작업의 검증 결과와 합치지 않는다.


## Career 해외 리그 실행 V1 + 선행 재정·AI 게이트 (2026-09-08)

신규 검증은 `CareerOverseasTournamentTest`, `CareerOverseasQualificationTest`의 순수 클래스 2개와
`CareerOverseasExecutionTest`의 공유 저장/실행 통합 1개다. 기존 AI·재정·국제전·API·파일 저장 검사를 확장했다.
17개 이벤트의 전체 대진은 통제 점수로 확인하고, 실제 Auto는 공통 BO1/BO3/BO5만 대표 실행했다.
게이트의 초기 재현 3실패 뒤 29건 통과, 결합 집중 38건 통과, 추가 경계/롤백/정상 영입을 확인했다.
프런트 `career:verify` 100건 및 build가 통과했으며, 격리 브라우저에서 실제 날짜 명령·BO1·결과/새로고침과
G2 미실행 원본 요청 보관·명시적 KRW 재작성을 확인했다.

계획된 전체 `test`는 **1회, 38분 28초, 274 suites/2,155건 중 2,141 통과·12 실패·기존 skip 2**다.
후속 실행 전에 `/tmp/career-overseas-full-evidence/`에 원본 로그·XML·HTML·실패 목록을 보존했다.
일반 리그 출전의 null competition 처리와 CL 완료 후 해시 갱신 순서라는 실제 회귀 2건,
V23/추가 대회·상금 수 기대값과 기존 Cup/정규 시즌 날짜 준비를 고쳤다.
실패 12건과 직접 영향을 받는 국내/CL/해외 실제 완료를 포함한 **8 suites/18건이 모두 통과했다(11분 26초)**.
후속 로그·XML·집계는 `/tmp/career-overseas-post-full-evidence/`에 따로 보존했다.
한정된 null/transaction 내 갱신 순서의 호출자와 API/파일 복구를 모두 검사했으므로 두 번째 전체 실행은 불필요했다.
미해결 실패는 없지만 최종 tree의 clean full 통과로 보고하지 않는다.

전체·집중 선택자, 브라우저 준비와 실제 엔진 실행 구간, 공식 규칙 공백/게임 정책은
[해외 리그 통합 보고서](career-overseas-league-execution-v1.md)에 기록했다.

## Career AI 후속 수정·원화 재정·시즌 목표 V1 (2026-09-08)

새 정책 테스트 클래스는 `CareerFinancePolicyTest` 한 개다. 기존 AI 계획, CL 완료,
시장/거래, 파일 DB 이주·재시작과 두 시즌 fixture를 확장했다. 별도 통합 클래스나
대규모 경제 진단은 추가하지 않았다. 기존 명령 UUID/receipt와 임대·체불 환산, 전환
archive 기록 뒤 저장 실패의 전체 롤백을 같은 파일 DB 사례에서 확인한다.

```bash
# 저장소 루트에서 실행. Gradle 명령은 같은 출력 디렉터리에서 순차 실행한다.
JAVA_HOME=/tmp/career-development-jdk ./backend/gradlew -p backend test \
  --tests 'com.lolfm.career.CareerFinancePolicyTest' \
  --tests 'com.lolfm.career.CareerSquadPlanningPolicyTest' \
  --tests 'com.lolfm.career.CareerMarketEngineTest' \
  --tests 'com.lolfm.league.CareerModePersistenceTest.marketCommandsPersistMembershipMoneyAndOriginalReceiptsWithoutChangingFrozenSeries' \
  --console=plain --no-daemon
npm --prefix frontend run career:verify
npm --prefix frontend run build
JAVA_HOME=/tmp/career-development-jdk ./backend/gradlew -p backend test --console=plain --no-daemon
```

마지막 집중 66건은 2분 51초 통과했다. 앞선 정책·CL·두 시즌 집중 15건은 10분 49초
통과했다. 프런트는 93건과 TypeScript/Vite build 통과다. 브라우저는 통제 CL 완료
fixture에서 권리를 준비한 뒤 실제 API/UI 제안·철회와 7일 날짜 명령, 입금 및 재조회
보존을 확인했다. 새 실제 경기 완주 행렬이나 다년 시장 분포 검증으로 보고하지 않는다.

계획된 전체 회귀 **1회, 36분 27초**는 271 suites/총 2,123건 중 2,115 통과, 실패 6,
오류 0, 기존 skip 2다. `/tmp/career-finance-full-evidence/`에 원본 로그/집계/XML을 보존했다.
V22 이주 수 세 곳, 구 크레딧 신인 영입 준비, 종전 고정 가격 단언, 이주 후 revision 준비를
교정했다. 후속 6건은 2분 32초에 5 통과/1 실패였다. 남은 신인 Auto 준비는 구단 예산을
올리지 않고 현재 급여 여유가 큰 적격 fixture를 선택했고, 해당 1건은 3분 19초에 통과했다.
backend 제품 코드 수정 없이 실패 사례와 helper의 유일한 호출자를 모두 검증했으므로
전체는 추가 실행하지 않았다. 원본과 후속 XML/log를 별도 보존했으며 미해결 실패는 없다.
최종 tree의 clean full 통과로 표시하지 않는다.
집중 실행 중의 준비값/기대값 교정과 실제 검증 범위는
[통합 구현 보고서](career-finance-season-targets-krw-and-ai-followups-v1.md)에 기록했다.


## Career 성장 경계·노쇠화·은퇴·신인 공급 V1 (2026-09-07)

순수 생애주기 정책 테스트 1개와 작은 기존 Auto fixture helper 1개를 추가하고 기존 성장·시장·
파일 저장·production Auto 테스트를 확장했다. PA 마지막 1~11 단위, 소수 노쇠화와 총량/한도,
관측 불가 중립·젊은 FA 보호, 생성 수/희소성, 중복·롤백·파일 재시작·지연 시즌을 확인한다.

수정 후 집중 70건과 관측 구간/기존 스토브 도입을 포함한 최종 집중 16건이 각각 통과했다.
실제 Calendar에서 시작한 Auto에 생성 신인의 3게임 출전·성장 중복 방지를 함께 검증했다.
브라우저는 격리 DB에서 심사·은퇴 정보 → 신인 영입 → 직접 선발 → 다음 시즌 관리 Series
진입 한 흐름을 확인했다. 프런트 Career 계약 81건과 production build가 통과했다.
계획된 전체 백엔드 회귀 1회는 267 suites / 2,086건 중 2,084 통과·실패/오류 0·기존 진단
skip 2, 38분 29초로 완료했다. 전체 이후 제품/테스트 수정이나 추가 전체 실행은 없었다.
상세 명령·시간·합성 fixture/실제 경기/순수 모델의 구분은
[통합 보고서](career-player-lifecycle-aging-retirement-and-rookie-supply-v1.md)를 따른다.

## Career 성장·훈련·챔피언 숙련도·피로 V1 (2026-09-07)

순수 정책 테스트1개와 기존 저장/시장/Auto 테스트를 확장했다. 작은365일 프로필7개와28일
피로 달력은 실제 경기 엔진 없이 계산한다. 실제 Auto Series3게임의 성장 입력·사용 챔피언
보상과 완료 롤백/중복, Player 시작 시 입력 고정, 파일 DB 복구, 날짜/AI 시장 동등성을 확인한다.
Career 프런트 verifier는 기존 한 곳에8건 추가해74건 통과했고 build 및 실제 브라우저의
훈련 예약·날짜 진행·새로고침·응답 소실 후 원본 UUID 복구를 확인했다.
전체 회귀1회는266 suites/2,062건 중2,059 통과·이주 개수 기대값1 실패·기존 skip2였고36분25초가 걸렸다.
기대값 교정 후 해당 이주 테스트1건이1분41초에 통과했다. 제품 로직 변경 없는 단언 교정이므로
전체를 재실행하지 않았으며 최종 전체 성공으로 표기하지 않는다.
명령·건수·실패 원인·소요 시간·전체 회귀 실행 횟수는
[통합 구현 보고서](career-player-development-training-proficiency-fatigue-v1.md)를 따른다.

## CA/PA 편집 및 계약 경계 수정 V1 (2026-09-07)

계약 시장과 편집 API는 집중 39건(5분 53초), 경기 입력·provenance·동일 seed·파일 DB 이주는
대표 7건(3분 14초)으로 확인했다. 두 실행 모두 실패·오류·skip 0이다. 프런트 Career verifier는
66건, production build는 167 modules 통과이며 실제 브라우저에서 저장·새로고침·응답 소실
복구를 확인했다. 사용자 요청으로 선행 전체 회귀를 중단했고 재실행하지 않았다.
완료한 전체 회귀로 집계하지 않는다. 명령 선택과 검증 범위는
[통합 구현 보고서](career-market-fixes-and-player-ability-editor-v1.md)에 기록했다.

## Focused Test

기능을 변경할 때는 관련된 작은 deterministic test를 먼저 실행한다.

```bash
cd backend
./gradlew test --tests 'fully.qualified.TestClass'
```

기본 `test`는 고유 회귀 범위를 그대로 실행하며 최대 2개 JVM worker(각 heap 1,536MiB)를 사용한다.
메모리가 제한된 환경은 `-PtestForks=1`로 실행한다. 테스트 메서드는 병렬 실행하지 않는다.
실행 비용 개선과 중복 통합 근거는 [실행 비용 개선 V1](backend-regression-runtime-optimization-v1.md)을 참고한다.

현재 test source는 대략 다음 책임으로 나뉜다.

- `com.lolfm.champion`: catalog/resource coverage, power, matchup, active-full/historical-subset integrity
- `com.lolfm.composition`: profile aggregation, interaction semantics, production candidate identity
- `com.lolfm.player`: rating loader/catalog와 semantic isolation
- `com.lolfm.draft`: role feasibility, candidate/evaluation/search, final assignment, Hard Fearless series
- `com.lolfm.simulator`: resolver eligibility/duplicate/fallthrough, reward integrity, determinism, timeline integration
- `com.lolfm.controller`: champion/match API response and validation

Focused correctness test는 큰 seed sample이나 분포 목표가 아니라 formula boundary, state transition, duplicate protection, Random non-consumption, structured event를 검증해야 한다.

### Career competition lifecycle V1 targeted hardening

최종 변경 영역은 기존 test class 네 개로 검증했다.

```bash
cd backend
./gradlew test \
  --tests com.lolfm.career.CareerCompetitionRulesTest \
  --tests com.lolfm.controller.CareerApiV1ControllerTest \
  --tests com.lolfm.league.CareerModePersistenceTest \
  --tests com.lolfm.league.LeagueRelationalPersistenceAndJobTest \
  --console=plain
```

결과는 4 suites / 24 tests / failures 0 / errors 0 / skipped 0, aggregate XML 99.935초,
Gradle wall 2분 18초다. Canonical instance/cycle V2 tamper, V1→V2 증명 후 승격, legacy pending,
GET read-only, due/overdue LCK Cup gate, 첫 시즌 bootstrap, 두 번째 시즌 sealed ranking, selector origin,
KeSPA source gap과 V8 migration count를 포함한다.

Frontend는 `npm run career:verify` 17 claims와 세 contract marker, `npm run build` 153 modules /
7.24초가 통과했다. 1280×720 실제 브라우저에서 pending 자동 재시도 12 POST가 같은 UUID를 유지했고,
수동 확인은 GET-first 1회 뒤 같은 UUID POST 1회로 terminal/current view를 복구했다. Overflow와 console
error는 0이었다.

Complete backend `./gradlew test --console=plain`은 269 suites / 2,093 tests / failures 114 /
errors 0 / skipped 2, aggregate XML 2,153.563초, Gradle wall 36분 35초로 실패했다. 변경 영역 5건은
교정 후 위 focused lane에서 통과했다. 잔여 109건은 누락된 과거 `build/reports` 진단 산출물 의존
108건과 full-load League background GET 500 1건이며, 후자는 단독 1 suite / 1 test가 3분 17초에
통과했다. 전자는 대형 과거 diagnostic pipeline을 요구해 이 작업의 명시적 비범위이고, 동일 환경의
complete command는 반복하지 않았다. Clean complete regression은 확보하지 못했다.

### AI League frontend completion recovery

Player Series 완료 반영의 production 상태기는 다음 frontend lane으로 검증한다.

```bash
cd frontend
npm run league:verify
npm run player-draft:verify
npm run series:verify
npm run reference:check
npm run reference:verify
npm run bundle:verify
npm run build
```

`league:verify`는 strict League DTO/10팀·18라운드·90경기 계약에 더해 production
`LeagueCompletionReconciler`를 직접 실행한다. completion-ready POST/poll/apply, response loss의
GET-first 확인, TIMEOUT/503 same UUID, polling 소진 뒤 수동 재개, non-retryable stop, pending
fallthrough, 자동/수동 single in-flight, unmount abort, remount POST 0, advertised command가 사라진
applied fixture 복구, stale binding 분리, 다음 fixture, NOT_FOUND 경계를 deterministic 14개
시나리오로 검증한다. 테스트 전용으로 복제한 reconciliation 알고리즘은 사용하지 않는다.

Controlled Playwright 검증은 실제 `LeaguePage`에 transport interception만 적용했다. 첫 503은 같은
UUID의 POST 2회/GET 1회, commit 뒤 response loss는 POST 1회/GET 1회, applied command remount는
POST 0회/GET 1회였고 모두 authoritative refresh 뒤 command와 오류가 정리됐다. 별도 clean page의
1440×900·1280×720 horizontal overflow, console/page error와 keyboard focus도 확인했다. 이는 mock-only
LIVE E2E 또는 90경기/Player BO3 장시간 완주로 부르지 않는다.

이 milestone은 frontend production source만 변경하므로 backend full regression을 반복하지 않고
기존 clean `259 suites / 2,336 tests / failures 0 / errors 0 / skipped 2`를 재사용한다. Build가 만드는
tracked historical output 두 파일은 검증 뒤 삭제 상태로 돌려놓고 그 후 build를 다시 실행하지 않는다.

### AI League V1 domain, schedule and standings

Batch 1 pure domain은 다음 focused lane으로 검증한다.

```bash
cd backend
gradlew.bat test \
  --tests com.lolfm.league.LeagueV1ProductDecisionsTest \
  --tests com.lolfm.league.LeagueScheduleGeneratorTest \
  --tests com.lolfm.league.LeagueSeasonAggregateTest \
  --tests com.lolfm.league.LeagueStandingsTest \
  --console=plain --no-daemon
```

- code-owned 9-decision canonical SHA, exact operational defaults와 actual LCK 10팀/50명 envelope
- 18 rounds/90 fixtures, round/pair cardinality, mirrored leg side, single-round design과 unordered input canonicalization
- Hybrid 18 player/72 auto, Spectator 90 auto와 execution-mode-independent fixture/root/game seed
- managed snapshot mismatch, invalid membership/game/history boundary fail-closed
- exact completion replay mutation 0, same-fixture receipt conflict와 cross-fixture hash reuse 거부
- Series wins/game differential/game wins/mini-league/Season-seed draw와 explicit tie-break trace

최종 focused 결과는 4 suites / 15 tests / failures 0 / errors 0 / skipped 0이다. Final production tree의 complete backend regression은 243 suites / 2,297 tests / failures 0 / errors 0 / skipped 2, aggregate XML 895.260초, Gradle wall 15분 11초로 첫 실행에서 통과했다. 대규모 League simulation이나 분포 진단은 실행하지 않았다.

### AI League V1 automated series runner

Batch 2 runner의 직접 계약, 실제 Production V9과 fresh-JVM 결정성은 다음 focused lane으로 검증한다.

```bash
cd backend
gradlew.bat test \
  --tests com.lolfm.league.LeagueAutomatedSeriesRunnerTest \
  --tests com.lolfm.league.LeagueAutomatedSeriesRunnerProductionV9Test \
  --tests com.lolfm.league.LeagueAutomatedSeriesRunnerCrossJvmDeterminismTest \
  --console=plain --no-daemon
```

- BO3 2:0/2:1, required-wins early stop, game별 side/fixture seed와 Hard Fearless transition
- `PLAYER_CONTROLLED`, pool exhaustion, no-decisive result의 completion/standings mutation 0
- frozen product/roster/resource/runtime drift의 Draft 전 fail-closed
- ordered Draft/final assignment/history/Production/replay/timeline/Random evidence와 compact canonical receipt
- fake hash와 receipt field tamper, omission/duplicate/reorder, cross-fixture/cross-Season 거부
- diagnostics ON/OFF 및 서로 다른 두 fresh JVM의 canonical receipt bytes/hash exact equality
- 실제 GEN–T1 frozen fixture의 Production Auto Draft 20턴과 Match Engine V9

최종 affected bundle은 Batch 1의 4 classes와 위 3 classes에 `AutoDraftSelectorTest`,
`AutoDraftVarietyV1ProductionIntegrationTest`, `DraftAvailabilityJointPoolTest`,
`MatchEngineV1ContractTest`, `MatchEngineV1CrossJvmDeterminismTest`,
`RealDraftMatchOrchestratorTest`, `SeriesLifecycleHardeningTest`,
`SeriesLifecycleServiceTest`, `SeriesProductionV9SmokeTest`,
`PlayerControlledDraftMatchInputBoundaryTest`를 더해 17 suites / 96 tests로 실행했다.
최초 bundle의 95개는 통과했고 constructor에서 이미 거부되는 side tamper를 verifier에서
거부될 것으로 기대한 test-only 분류 1건을 교정한 뒤 runner suite 7/7이 통과했다.
Production 실행 코드는 이 교정으로 바뀌지 않았다.

Final executable production tree의 complete backend regression은
246 suites / 2,306 tests / failures 0 / errors 0 / skipped 2, aggregate XML 962.313초,
Gradle wall 16분 14초로 첫 실행에서 통과했다. 이후 문서만 변경했으므로 full을
반복하지 않았다. 90-fixture Season run, balance/performance population과 frontend/
Playwright는 이 backend-only milestone에서 실행하지 않았다.

### AI League V1 Player Series handoff

Batch 3의 canonical binding, process-local start/resume, 실제 Player Production V9 completion과 fresh-JVM proof는 다음 lane으로 검증한다.

```bash
cd backend
gradlew.bat test \
  --tests com.lolfm.league.LeaguePlayerSeriesHandoffServiceTest \
  --tests com.lolfm.league.LeaguePlayerSeriesHandoffProductionV9Test \
  --tests com.lolfm.league.LeaguePlayerSeriesHandoffCrossJvmDeterminismTest \
  --console=plain --no-daemon
```

- managed team BLUE/RED fixture와 server-created private binding, exact command replay/payload conflict
- spectator/FULL_AUTO/non-managed/stale/snapshot drift의 Draft/Match/Random 실행 0
- standalone public create의 League origin/binding 주입 필드 부재와 League sibling seed/double derivation 0
- 기존 mixed-authority 20-turn Draft, game별 10 final assignments, fixture-scoped Hard Fearless +10 picks
- actual Production V9 BO3 early stop, diagnostics ON/OFF game receipt exact와 다른 fixture completion으로 증가한 Season revision 허용
- completed server Series evidence에서만 V2 unified receipt 생성, Auto/Player Draft authority와 binding hash 결속
- binding/League/Season/fixture/side/root/seed/history/Draft/assignment/resource/output/replay/timeline/Random, omission/duplicate/reorder 변조 거부
- incomplete/cancel/pool exhaustion/invalid completion의 score/history/standings 0, invalid pending은 `BLOCKED`
- first standings application +1, exact duplicate receipt bytes exact/engine 0/standings +0
- 두 fresh JVM의 canonical binding과 Player V2 receipt byte/hash exact

Batch 2 Auto parity는 같은 final tree에서 `LeagueAutomatedSeriesRunnerTest`, `LeagueAutomatedSeriesRunnerProductionV9Test`, `LeagueAutomatedSeriesRunnerCrossJvmDeterminismTest`로 V1 core와 FULL_AUTO authority V2 envelope를 함께 확인한다. Existing affected regression은 Batch 1 schedule/standings, Series lifecycle/repository/API/replay, Player Draft engine/boundary/session, Match Engine V1/Production V9와 Real Match API를 포함한다.

Batch 3 final executable production tree의 complete backend regression은 첫 실행에서 249 suites /
2,315 tests / failures 0 / errors 0 / skipped 2, aggregate XML 1,901.498초, Gradle wall 16분 53초로
통과했다. 두 skip은 explicit 대형 diagnostic이다. Batch 3 당시 League API/frontend가 없었으므로
frontend build/Playwright는 실행하지 않았고 90-fixture Season, balance/performance population도
제외했다. Current API 결과는 아래 Batch 5 section을 따른다.

### AI League V1 persistence and jobs

Batch 4는 다음 focused lane을 사용한다.

```bash
cd backend
gradlew.bat test \
  --tests com.lolfm.league.LeagueRelationalPersistenceAndJobTest \
  --tests com.lolfm.application.LeagueBoundSeriesCheckpointRecoveryTest \
  --tests com.lolfm.league.LeaguePlayerSeriesHandoffServiceTest \
  --tests com.lolfm.league.LeagueAutomatedSeriesRunnerProductionV9Test \
  --tests com.lolfm.league.LeaguePlayerSeriesHandoffProductionV9Test \
  --console=plain --no-daemon
```

- empty DB→V1→V2, repeat no-op, temporary file DB close/reopen과 canonical tamper rejection
- JDBC binding 20-request concurrent create-or-load와 one owner/one row/payload conflict
- Season READY/pause/resume/cancel, Player auto-dispatch 0, default 2/hard 4 boundary
- exact 15-minute lease, 15-second heartbeat, fencing stale result 0, attempt 1→2와 30-day retention
- binding-before-checkpoint startup reconciliation과 reservation process-loss release
- actual Production Auto Draft/V9 background job, receipt/outbox producer proof와 standings exactly-once
- duplicate delivery 및 standings-commit/outbox-ack-loss redelivery mutation 0
- actual Player Production V9의 mid-Draft, Draft-complete, Game boundary와 completed checkpoint reload
- concurrent completion owner 1, false `BLOCKED` 0, persisted Player receipt/outbox 1

최종 핵심 focused 묶음은 위 persistence/checkpoint/concurrency/actual V9에 transport configuration과
Auto/Player fresh-JVM byte parity를 더한 8 suites / 16 tests로, failures/errors/skipped 0,
Gradle wall 3분 3초에 통과했다. 첫 complete regression은 251 suites / 2,319 tests 중 test
resource가 main compression 설정을 가린 1건과 unordered Draft capability 합산이 raw trace를
흔든 fresh-JVM 2건을 발견했다. 설정 상속과 enum canonical order를 수정하고 affected focused를
통과한 뒤, 최종 executable tree의 두 번째 complete regression은 251 suites / 2,319 tests /
failures 0 / errors 0 / skipped 2, aggregate XML 1,150.816초, Gradle wall 19분 16초로 clean
pass했다. 두 skip은 기존 explicit 대형 diagnostic이다. Frontend source는 변경하지 않았고
frontend build/Playwright, 90-fixture run과 대형 balance/performance diagnostic은 실행하지 않았다.

90-fixture official run, 대형 balance/performance population, frontend/Playwright는 실행하지 않았다.
동일한 최종 수치는 [Project Status](../project-status.md)에 기록했다.

### AI League V1 API with job-boundary hardening

Batch 5는 API 공개 전에 restart/failure/global lease/cancel 경계를 검증하고, 같은 실행에서 public
HTTP와 기존 API parity를 확인한다.

```bash
cd backend
gradlew.bat test \
  --tests com.lolfm.league.LeagueScheduleGeneratorTest \
  --tests com.lolfm.league.LeagueSeasonAggregateTest \
  --tests com.lolfm.league.LeagueStandingsTest \
  --tests com.lolfm.league.LeagueV1ProductDecisionsTest \
  --tests com.lolfm.league.LeagueAutomatedSeriesRunnerTest \
  --tests com.lolfm.league.LeagueAutomatedSeriesRunnerProductionV9Test \
  --tests com.lolfm.league.LeaguePlayerSeriesHandoffServiceTest \
  --tests com.lolfm.league.LeagueRelationalPersistenceAndJobTest \
  --tests com.lolfm.league.LeagueJobFailureClassifierTest \
  --tests com.lolfm.controller.LeagueApiV1ControllerTest \
  --tests com.lolfm.controller.LeagueApiV1ErrorBoundaryTest \
  --tests com.lolfm.controller.LeagueApiV1TransportIntegrationTest \
  --tests com.lolfm.controller.SeriesApiV1ControllerTest \
  --tests com.lolfm.controller.PlayerDraftApiV1ControllerTest \
  --tests com.lolfm.controller.RealMatchApiV1ControllerTest \
  --tests com.lolfm.controller.RealMatchTransportCompressionV1IntegrationTest \
  --console=plain --no-daemon
```

- file-backed H2 V1→V3와 unexpired prior-incarnation attempt 1/2 recovery, old fencing mutation 0
- typed Spring/SQL/worker transient와 deterministic mismatch, message-based `TIMEOUT` 판정 0
- 두 Season 20-way concurrent lease exact 4, duplicate 0, slot reuse와 stale token mutation 0
- cancel transaction rollback, cancel/lease/dispatch race와 post-cancel new execution 0
- Hybrid/Spectator strict create, 10팀/18 rounds/90 fixtures, Hybrid 72 Auto/18 Player
- durable create/action command replay, payload conflict, stale lifecycle revision와 20-way command replay
- HTTP 202 dispatch/polling, pause/resume/cancel, Player job 0, lease/fence 비노출
- actual Player Production V9 completion/outbox/standings +1과 exact replay +0
- controller-scoped 400/404/409/422/503/500, SQL/path/stack 비노출
- actual HTTP CORS와 identity/gzip 90-fixture JSON byte equality
- 기존 Series, Player Draft, Real Match API와 Real Match gzip parity

Affected lane은 16 suites / 62 tests / failures 0 / errors 0 / skipped 0, Gradle wall 2분 25초로
통과했다. Job completion을 token/fence/incarnation/attempt 조건부 update로 먼저 잠그도록 보강한 뒤
직접 영향 5 suites / 20 tests도 1분 22초에 재통과했다. 실제 Production smoke는 Auto 1 fixture와
Player 1 fixture 이내로 제한했다. 첫 full 뒤 수동 감사에서 explicit run과 runtime-expired lease
recovery 사이 연결 누락을 찾아 background pump에 recovery-before-lease 순서를 추가했고,
`LeagueBackgroundJobExecutorTest`를 포함한 3 suites / 12 tests가 47초에 통과했다.

Final executable tree에서 실행한 complete backend regression 명령은 다음과 같다.

```bash
cd backend
gradlew.bat test --console=plain --no-daemon
```

첫 complete regression 255 suites / 2,332 tests는 clean pass했지만 위 실제 운영 연결 누락을
수정했으므로 재사용하지 않았다. 최종 executable tree의 두 번째 결과는 256 suites / 2,333 tests /
failures 0 / errors 0 / skipped 2, aggregate JUnit XML 1,139.817초, Gradle wall 19분 13초,
`BUILD SUCCESSFUL`이다. 이후 production Java/resource/Gradle/shared fixture를 바꾸지 않고 문서만
갱신했다. 두 skip은 기존 explicit 대형 diagnostic이다.
Frontend source 변경이 없어 frontend build/Playwright를 실행하지 않았고 90-fixture official run,
balance/calibration/holdout와 대형 statistical diagnostic도 실행하지 않았다.

### AI League V1 frontend delivery

Batch 6는 Phase A delivery behavior와 frontend 계약을 다음 최소 lane으로 검증한다.

```text
cd backend
gradlew.bat test \
  --tests com.lolfm.controller.LeagueApiV1BackgroundExecutionIntegrationTest \
  --tests com.lolfm.controller.LeagueApiV1BackgroundAvailabilityTest \
  --tests com.lolfm.league.LeagueBackgroundJobExecutorTest \
  --tests com.lolfm.league.LeagueRelationalPersistenceAndJobTest \
  --tests com.lolfm.league.LeagueApiV1ResponseMapperTest \
  --tests com.lolfm.league.LeaguePlayerSeriesHandoffServiceTest \
  --console=plain

cd frontend
npm run league:verify
npm run player-draft:verify
npm run series:verify
npm run reference:check
npm run reference:verify
npm run bundle:verify
VITE_REAL_MATCH_API_BASE_URL=http://localhost:8086 npm run build
```

Backend focused lane은 submit false 503, exact replay pump re-kick, duplicate job/receipt/outbox/standings 0,
background enabled public 202→5 terminal jobs, Player child 상태별 command projection과 기존 persistence/
fencing을 검증했다. Frontend League contract는 Hybrid/Spectator, exact 90 fixture/18 round, schema/scope/
counter/duplicate/impossible command rejection, stable logical UUID, revision ordering과 pointer
not-found/temporary-failure behavior를 모두 통과해
`AI_VS_AI_LEAGUE_FRONTEND_CONTRACT_VERIFICATION_PASSED`를 출력했다.

기존 Player Draft, Series, reference checksum/semantics와 lazy bundle도 clean pass했다. Build는 132
modules, initial graph 496,300 bytes, lazy reference 423,581 bytes였다. 최종 executable backend tree는
`gradlew.bat test --console=plain`을 한 번 실행해 259 suites / 2,336 tests / failures 0 / errors 0 /
skipped 2, aggregate XML 1,122.363초, `BUILD SUCCESSFUL`, Gradle wall 18분 47초로 통과했다.
그 뒤 production Java/resource/Gradle/shared fixture는 변경하지 않고 문서만 갱신했다.

Actual Playwright는 isolated H2/background enabled runtime에서 Hybrid 10/18/90 및 18 Player/72 Auto,
public 202→Production V9 terminal jobs와 standings/reload, managed Player Series handoff/reload/return,
별도 Spectator pause/resume/cancel을 확인했다. 1440×900·1280×720 page overflow 0, modal focus trap/
Escape/return, reduced-motion spinner none, clean console/page/runtime validation error 0이었다. 90-fixture
official full run과 대형 balance/calibration/holdout은 실행하지 않았다. 상세 결과는
[AI League Frontend V1](ai-vs-ai-league-simulation-v1-frontend.md)에 있다.

### Player-controlled Draft API V1

혼합 Draft의 final authoritative-input boundary와 기존 session hardening은 다음 focused lane으로 검증한다.

```bash
cd backend
gradlew.bat test \
  --tests com.lolfm.application.PlayerControlledDraftMatchInputBoundaryTest \
  --tests com.lolfm.draft.PlayerControlledDraftEngineTest \
  --tests com.lolfm.application.PlayerDraftSimulationHardeningTest \
  --tests com.lolfm.controller.PlayerDraftApiV1ControllerTest \
  --tests com.lolfm.application.MatchEngineV1ContractTest \
  --console=plain
```

- domain: BLUE/RED 완주, authority/evidence binding, advisory 밖 legal choice, flex 유지, illegal action mutation 0, AI production parity, transcript replay와 final-assignment 재구성
- session: injected Clock TTL/lazy eviction, concurrent create exact capacity, collision/cleanup permit leak 0, repository instance isolation과 per-session atomic mutation
- simulation ownership: full output/timeline 비보관, 16KiB 이하 compact receipt, first/repeat deterministic execution, mismatch/failure rollback과 same-session concurrent serialization
- input boundary: caller-provided `Team` 없이 team code를 `LckTeamAssembler` authoritative stable-`PlayerId` roster로 해석, unknown/same/mismatched team context 거부, display label 비의존, selectable-set/control hash, authority/action, authoritative AI trace, state/final role/player assignment와 seed 변조 거부, public raw factory 부재
- active resource binding: completed result의 Draft Meta version 및 required/actual legal-role hash를 현재 `DraftResourceSet.meta()`와 exact 비교하고 세 독립 변조를 Match Engine/seeded gameplay Random 전에 거부
- HTTP: strict start/action/simulate parsing, revision/idempotency conflict, 동시 제출 단일 성공, cancel/not-found, 완료와 simulation 분리, V9 policy/provenance와 반복 simulate equality

기존 완전 자동 semantics 회귀는 `MatchEngineV1ContractTest`, `RealMatchApiV1ControllerTest`, `AutoDraftVarietyV1ProductionIntegrationTest`를 함께 실행한다. 대규모 seed population, balance diagnostic, frontend build는 이 backend-only 기능의 focused lane에 포함하지 않는다.

Final boundary tree의 위 focused lane은 5 suites / 36 tests / failures 0 / errors 0 / skipped 0으로 통과했다. 최종 executable production tree의 새 complete backend regression은 226 suites / 2,232 tests / failures 0 / errors 0 / skipped 0, aggregate XML 1,034.299초, Gradle wall 17분 29초로 첫 실행에서 통과했다. 기존 226 suites / 2,230 tests 결과를 재사용하지 않았다. 이번 backend-only milestone에서는 frontend, live HTTP smoke, 대형 diagnostic, calibration/holdout과 historical artifact 재생성을 실행하지 않았다.

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

전체 회귀의 실행 시점, clean pass 재사용, 변경 후 재실행 조건과 실행 예산은 [AGENTS.md의 Full regression budget](../../AGENTS.md#full-regression-budget)을 따른다. 변경 표면별 검증 선택은 [LoL Manager Verification](../../.agents/skills/lolmanager-verification/SKILL.md#verification-router)을 참고한다. 현재 실행 수치는 [Project Status](../project-status.md)를 따른다.

### Matchup/Composition production activation

`PRODUCTION_MATCHUP_COMPOSITION_V1` 활성화는 대형 통계 task 없이 다음 correctness lane으로 검증했다.

- profile/policy/Match Engine/Real Draft/API/structured reachability 10 suites / 58 tests, 2분 56초
- combat/FARM/reward/structure/Random 직접 영향 invariant 9 suites / 101 tests, 7초
- 첫 full에서 드러난 historical/current-default expectation 보정 4 suites / 7 tests, 1분 11초
- 최종 complete backend 219 suites / 2,201 tests / failures 0 / errors 0 / skipped 0, aggregate XML 1,821.280초, Gradle wall 16분 25초
- frontend production build 87 modules, 약 8초
- LIVE options/simulate gzip와 실제 Draft → playback → result 브라우저 smoke

첫 full은 2,201 tests 중 4건이 실패했다. Historical Final 13G의 5-profile audit/inspector와 현재 transport/performance test가 암묵적 default를 계속 baseline으로 가정한 것이 원인이었다. Historical audit은 새 production alias를 의도적으로 제외하고 historical baseline inspector가 현재 production oracle이 되지 않도록 고정했다. Current transport/performance test는 새 policy/profile/configuration identity를 검증한다. Affected focused pass 뒤 두 번째이자 최종 full을 수행했으며 historical artifact/output hash는 갱신하지 않았다.

Activation correctness는 Matchup/Composition non-zero structured application, baseline exact OFF/rollback, Jungle Economy/Tempo exact zero, same-seed/cross-JVM determinism, diagnostics ON/OFF timeline·Random parity와 public request profile selector 부재를 포함한다. `PreJungleTempoParityAuditTest`는 historical diagnostic tag로 default test에서 제외되며, 이번 activation에서 별도 parity artifact를 생성하거나 대형 calibration/holdout을 실행하지 않았다.

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

Base siege strategic coherence hardening은 production 변경 전에 `BaseSiegeStrategicCoherenceTest.previousTickNexusEmergencyStopsActiveLowerTierSiegeBeforeMutation`을 추가해 기존 active lower-tier siege가 자기 Nexus 노출 다음 tick에도 실제 HP를 깎는 실패를 확인했다. 수정 뒤 이 suite의 8 tests는 다음을 고정한다.

- 이전 tick부터 존재한 `NEXUS_THREAT`에서 Baron 여부와 무관하게 OUTER/INNER/INHIBITOR_TURRET/INHIBITOR continuation 중단
- damage/gold/plate/attack sequence/wave/structure slot 추가 변이 0, 공통 activity release와 FARM expiry 비연장, duplicate call 멱등성
- BLUE/RED mirror와 동일 tick 신규 위협의 소급 취소 금지, 다음 tick 양측 동일 반영
- 안전한 base-race 계약 부재 시 Nexus continuation fail-closed, 안전한 자기 기지의 no-kill Baron Nexus finish 보존
- diagnostics ON/OFF 및 반복 실행의 structured decision exact equality, Nexus finish 이후 추가 structure event/mutation 0

영향 범위 검증은 새 suite와 `StructureEngineRedesignTest`, `LateGameStructureBranchTest`, structure slot/causality/target, `MatchSimulatorSmokeTest`, runtime profile, Match Engine V1 contract, Jungle candidate provenance, same-seed와 cross-JVM 결정성의 13 suites / 115 tests를 실행해 failures/errors/skipped 0으로 통과했다. 공통 gameplay 의미를 구분하기 위해 active rules는 Pre-Jungle/Jungle Economy/Jungle Tempo 각각 V4/V4/V3으로 올렸고 production policy hash는 `3afaa399f7c2b20a940c7cfd7510f6c7962ba43eec575cc75ed55218d53f0ce9`로 재계산했다. Profile activation, configuration hash, engine V9, tuning 값은 바꾸지 않았다.

분포 관측은 기본 `test`에 넣지 않고 다음 전용 task로 실행한다.

```bash
cd backend
./gradlew runStructureRealismDiagnostics -PstructureDiagnosticSeeds=200
```

이 diagnostic은 첫 구조물 피해/첫 포탑/기지 개방/경기 종료 분포, 넥서스 포탑 철거 뒤 넥서스 연결, kill 사이 구조물 연속 철거와 duplicate event ID, 비정상 HP, source 누락, 보호 중 넥서스 파괴, 종료 뒤 mutation을 structured field만으로 집계한다. Event message나 frontend text는 읽지 않으며 production tuning의 assertion oracle로 사용하지 않는다.

최종 executable production tree의 complete backend regression은 첫 실행에서 239 suites / 2,282 tests / failures 0 / errors 0 / skipped 2, aggregate JUnit XML 2,041.953초, Gradle wall 18분 28초로 통과했다. 이번 hardening을 위한 full regression은 이 한 번뿐이며, 이후에는 Markdown만 갱신했다. Balance 수치를 바꾸지 않았고 correctness가 focused/integration에서 충분히 증명됐으므로 200-seed structure diagnostic, 대규모 calibration/holdout과 historical artifact 재생성은 실행하지 않았다.

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

### Player-controlled Draft Frontend V1

Frontend-only milestone은 다음 lane으로 검증했다.

```text
cd frontend
npm run build
npm run player-draft:verify
npm run reference:check
npm run reference:verify
LOLMANAGER_REAL_MATCH_OPTIONS_PATH=output/verification-live/options.json \
LOLMANAGER_REAL_MATCH_RESPONSE_PATH=output/verification-live/response.json \
  npm run live:verify
npm run bundle:verify
```

Production build와 Player Draft deterministic contract가 통과했다. Contract script는 valid ACTIVE/COMPLETED/SIMULATION 수용, wrong schema와 invalid status/current turn, PLAYER/AI evidence, duplicate champion, recommendation/selectable, final assignment, control/integrity 불일치 거부, error DTO와 unavailable reason 한국어 mapping 전체를 검사한다. Reference check/verify와 bundle check도 통과해 기존 AUTO/REFERENCE adapter 의미와 lazy reference chunk를 보존했다.

기본 `npm run live:verify`가 읽는 로컬 `backend/build/reports/real-match-api-v1`은 historical V8 handoff이며 현재 V9 strict validator가 요구하는 activation decision과 Draft selection policy 필드보다 오래됐다. 해당 사용자 산출물을 덮어쓰거나 checked-in reference를 재생성하지 않고, 실제 V9 `/options`와 GEN/T1 seed 73 응답을 ignored `output/verification-live`에 분리했다. 위 환경 경로를 사용한 `live:verify`는 teams 10, players 50, decisions 20, assignments 10, events 376, snapshots 233, RED/2,320초와 invalid mutation 10종을 통과했다. Player Draft session/action/simulation 응답도 아래 LIVE browser smoke에서 현재 strict validator를 통과했다.

Playwright LIVE smoke는 seed 73과 실제 GEN/T1 options를 사용했다. BLUE-controlled flow는 player action 10회 뒤 decisions 20개와 final assignment 10개를 표시했고, `/simulate` logical request 1회가 HTTP 200 및 gzip으로 완료된 뒤 Playback→Result→mixed-authority Draft review를 확인했다. RED-controlled 별도 session은 생성 응답에 BLUE AI turn이 포함되고 현재 RED player turn인지, action 1회 뒤 revision/decision 증가와 DELETE 204를 확인했다. AUTO flow는 기존 `/api/v1/real-matches/simulate`만 1회 사용하고 `/player-drafts/*` 호출 0회, 읽기 전용 자동 Draft 20/20과 console error 0을 확인했다. 1440×900과 1280×720에서 Draft 화면과 수평 overflow를 확인했다.

Backend production Java/resource/API/Gradle을 변경하지 않았으므로 complete backend regression은 실행하지 않았다. 이는 frontend-only 변경에 full regression을 실행하지 않는 검증 budget 원칙을 따른다.

### Player-controlled Draft LIVE E2E and Accessibility

Contract, browser와 backend lane을 분리해 실행했다.

```text
cd frontend
npm run player-draft:verify
npm run build
npm run reference:check
npm run reference:verify
LOLMANAGER_REAL_MATCH_OPTIONS_PATH=frontend/output/player-draft-live-e2e/options-v9.json \
LOLMANAGER_REAL_MATCH_RESPONSE_PATH=frontend/output/player-draft-live-e2e/response-v9.json \
  npm run live:verify
npm run bundle:verify

cd ../backend
gradlew.bat test --tests com.lolfm.controller.PlayerDraftApiV1ControllerTest \
  --console=plain --no-daemon
gradlew.bat test --console=plain --no-daemon
```

Player Draft contract script는 33개 시나리오를 통과했다. 기존 session/authority/control 검증에 CANCELLED terminal projection, unknown actor, killer/victim champion binding, assistant length/duplicate, final team kills/gold, final player KDA/CS/gold/XP/level, final assignment/presentation mismatch와 session ordering/response-loss receipt 검사를 추가했다. Auto와 Player Draft envelope는 구분하고 common match semantic helper만 공유한다.

Terminal mapper focused API는 1 suite / 5 tests / failures 0 / errors 0 / skipped 0이다. Incomplete 취소가 CANCELLED/null currentTurn/빈 action projection이고 DELETE 204 empty, action/simulate 거부가 유지되는지 확인한다. Production Java 변경 뒤 final default backend regression은 226 suites / 2,232 tests / failures 0 / errors 0 / skipped 0, aggregate XML 1,377.624초, Gradle wall 23분 16초로 한 번에 통과했다.

Production build는 100 modules, main JS 353.95kB(gzip 113.76kB), CSS 122.49kB(gzip 21.37kB)다. Reference check는 794,907 bytes/SHA-256 `977c7d6e015f4ebd5ecba8e24e7b95a0a6313fef2e1e69a2c396b4fab36ac15e`, reference verify는 teams 10, players 50, decisions 20, events 287/517, snapshots 59/344를 확인했다. Bundle verify는 initial 373,627 bytes, lazy reference 423,581 bytes다.

명시적 current V9 `live:verify` 입력은 ignored `frontend/output/player-draft-live-e2e/`에 두었다. Options/response SHA-256은 각각 `eb82bdf43b7698802e192be5c8aa2f2d39f11a80fc81f4077eb91709b480f1f9`, `8edadba7b8e2cc08cb0b731a53c362c69fb2143bdfe6b54636aee24b7d78a7f4`다. 검증은 current policy/profile/configuration/engine, teams 10, players 50, decisions 20, assignments 10, events 376, snapshots 233, RED/2,320초와 mutation 10종을 통과했다. 기본 historical V8 artifact는 수정하거나 V9 증거로 승격하지 않았다.

Playwright actual LIVE 결과는 다음과 같다.

- BLUE: create 1/action 10/simulate 1, 모두 200, revision 10, decisions 20, PLAYER/AI 각 10, final assignment 10, Playback/Result/mixed review 통과
- RED: 최초 BLUE AI decision 뒤 RED turn 2, create 1/action 10/simulate 1, 모두 200, final 20 decisions와 Result 통과
- AUTO: real-matches simulate 1, player-drafts 0, Auto Draft 20/20
- response loss: server commit 뒤 response만 abort, GET 200으로 revision 1/decisions 2와 다음 focus 복구
- cancel: delayed DELETE pending 중 dialog focus/aria-busy 유지와 Escape 억제, DELETE 1회 204 뒤 설정 복귀
- viewport: 1440×900과 1280×720 모두 horizontal overflow 0, champion 173개/grid Tab stop 1개
- clean BLUE/RED/AUTO page/console/runtime validation/reference fallback error 0; Result ban portrait 10/10, broken/fallback 0

Gzip은 완료된 RED session receipt 재조회에서 HTTP 200, `Content-Encoding: gzip`과 `Vary: ... accept-encoding`으로 확인했다. Browser fault interception은 transport response만 유실했고 domain payload를 만들지 않았다. 실제 30분 expiry sleep과 대형 population은 실행하지 않았다. 상세 focus trace, 제한과 artifact 정책은 [LIVE E2E/accessibility 문서](player-controlled-draft-live-e2e-and-accessibility.md)에 있다.

### Player Draft interactive and simulation latency profiling V1

이 profiling은 correctness test에 대형 통계를 넣지 않고 explicit diagnostic lane으로만 실행한다.

```text
cd backend
gradlew.bat test \
  --tests com.lolfm.application.PlayerDraftLatencyProfilingV1HarnessTest \
  --console=plain --no-daemon

set LOLMANAGER_RUN_PLAYER_DRAFT_LATENCY_PROFILE_V1=1
set LOLMANAGER_PROFILE_HEAD=<current-head>
set LOLMANAGER_PROFILE_SOURCE_IDENTITY=<owned-source-identity>
gradlew.bat test \
  --tests com.lolfm.application.PlayerDraftLatencyProfilingV1DiagnosticTest \
  --console=plain --no-daemon --rerun-tasks

cd ../frontend
npm run build
```

Focused parity는 session ID를 제외한 20개 decision/authority/champion/final assignment, selection/control evidence, Match Engine input, production profile/configuration, replay/resource provenance, simulator/structured timeline, Random fingerprint, output hash, winner/duration/events/snapshots를 profiling ON/OFF exact 비교한다. Diagnostic test는 기본 input 위치의 actual Chromium JSON을 읽고 GEN/T1 seed 73의 BLUE direct cold 1회, BLUE/RED warm 각 2회, first/exact retry와 small JFR을 사용한다. Gradle은 환경 변수와 report directory 이동을 task input으로 보지 않으므로 공식 재생성에는 `--rerun-tasks`를 명시한다. Actual Chromium은 side별 fresh `bootRun`, Vite LIVE provider, disabled cache로 10 action + explicit simulate를 별도 측정하며 REFERENCE fallback을 허용하지 않는다.

공식 artifact는 `backend/build/reports/player-draft-interactive-simulation-latency-profiling-v1/`에 생성된다. Action 50 rows, AI turn 48 rows, simulation 10 rows, browser 22 rows와 canonical JSON/analysis/SHA manifest를 검증한다. Backend 세부 phase는 영구 production timer의 nested 합계가 아니라 exact semantic replay이므로 service total에 더하지 않는다. Timing percentile은 작은 deterministic 표본의 기술 통계이고 fresh JVM byte-equality gate가 아니다.

최종 focused parity와 artifact diagnostic, frontend production build, actual BLUE/RED LIVE flow가 모두 통과했다. Backend production Java/resource/Gradle/runtime/API는 변경하지 않아 complete backend regression은 실행하지 않았다. Frontend production TypeScript의 기본 OFF observer는 build와 LIVE flow로 검증했다. 상세 수치와 해석은 [Player Draft latency profiling V1](player-draft-interactive-and-simulation-latency-profiling-v1.md)에 있다.

## Generated Reports

### Series Lifecycle V1 backend

Series correctness는 대형 balance population이 아니라 identity/state/revision/atomicity와 bounded actual Production wiring으로 검증한다.

```text
gradlew.bat test \
  --tests com.lolfm.application.SeriesRepositoryTest \
  --tests com.lolfm.application.SeriesLifecycleServiceTest \
  --tests com.lolfm.application.SeriesLifecycleHardeningTest \
  --tests com.lolfm.draft.DraftAvailabilityJointPoolTest \
  --tests com.lolfm.controller.SeriesApiV1ControllerTest \
  --console=plain

gradlew.bat test \
  --tests com.lolfm.draft.PlayerControlledDraftEngineTest \
  --tests com.lolfm.application.PlayerControlledDraftMatchInputBoundaryTest \
  --tests com.lolfm.controller.PlayerDraftApiV1ControllerTest \
  --tests com.lolfm.application.MatchEngineV1ContractTest \
  --tests com.lolfm.controller.RealMatchApiV1ControllerTest \
  --tests com.lolfm.draft.DraftCoreDomainTest \
  --tests com.lolfm.draft.DraftEngineIntegrationTest \
  --tests com.lolfm.draft.AutoDraftSelectorTest \
  --console=plain

gradlew.bat test \
  --tests com.lolfm.application.SeriesProductionV9SmokeTest \
  --console=plain
gradlew.bat test --console=plain
```

`SeriesProductionV9SmokeTest`는 GEN/T1, root seed 73, managed GEN, Game 1 BLUE GEN의 BO3와 BO5를 실제 Production V9으로 실행한다. 각 player turn은 response의 첫 legal selectable champion을 제출하고 required wins까지 최대 3/5게임을 진행한다. Side/controlled side/seed/history, game별 20 decisions/10 assignments, committed picks 10개씩의 Hard Fearless 누적, policy/profile/engine identity, compact receipt와 committed game full replay를 확인한다. 현재 bounded 결과는 BO3 GEN 2–1 T1/30 picks, BO5 T1 3–2 GEN/50 picks이며 승자와 score를 balance 판단에 사용하지 않는다.

Frontend-readiness hardening의 focused lane은 다음과 같다.

```text
gradlew.bat test \
  --tests com.lolfm.application.SeriesRepositoryTest \
  --tests com.lolfm.application.SeriesLifecycleServiceTest \
  --tests com.lolfm.application.SeriesLifecycleHardeningTest \
  --tests com.lolfm.application.SeriesFrontendReadinessContractTest \
  --tests com.lolfm.controller.SeriesApiV1ControllerTest \
  --console=plain --no-daemon
```

이 묶음은 failed receipt의 historical code/HTTP/retryable과 public current aggregate state 분리, replay executor/mutation 0, payload conflict 우선순위, 255→256 off-by-one, 상태별 capacity-aware `allowedCommands`와 service eligibility 일치를 검증한다. 5 suites / 27 tests가 failures/errors/skipped 0으로 통과했고 최종 assertion 강화 뒤 핵심 2 suites / 15 tests도 다시 통과했다.

Repository/hardening test는 64개 concurrent create의 exact capacity 32, stale cleanup 대 성공 mutation의 latch race, create index, Series별 atomic compute, instance isolation, parent/child/lease 경계를 injected Clock으로 검증한다. Controllable executor는 success/failure/in-progress exact replay, same/different concurrent command, cancel 중 late success/failure, lease expiry→retry→old late success/integrity failure, receipt 255/256, full aggregate replay transition, BO3 2–0/2–1과 BO5 3–0/3–2를 고정한다. Joint-pool test는 independent five-role check가 공유 챔피언을 중복 사용할 수 있는 synthetic 반례와 정확한 열 champion 경계를 검증한다. 이 focused 묶음은 25 tests, failure/error/skip 0이다. 기존 Player Draft/Match Engine V1/Real Match/Draft 호환성 묶음은 8 suites / 58 tests, failure/error/skip 0이다.

이 backend milestone에서는 frontend build, Playwright, large-seed distribution, calibration/holdout과 historical artifact regeneration을 실행하지 않았다. 현재 실제 결과와 제한은 [Series backend hardening](series-lifecycle-v1-backend-hardening.md)에 기록한다.

최초 implementation executable tree의 complete backend regression은 231 suites / 2,246 tests / failures 0 / errors 0 / skipped 0이었다. 이번 hardening final production executable tree는 첫 실행에서 233 suites / 2,261 tests / failures 0 / errors 0 / skipped 0으로 통과했다. Aggregate JUnit XML은 1,781.459초, Gradle wall은 29분 50초다. Clean full 뒤 isolated lease late-integrity assertion-only test를 강화해 affected focused만 통과했고 production source는 바뀌지 않았으므로 full을 반복하지 않았다.

후속 frontend-readiness final executable tree의 complete backend regression은 첫 실행에서 234 suites / 2,266 tests / failures 0 / errors 0 / skipped 0으로 통과했다. Aggregate JUnit XML은 1,931.403초, Gradle wall은 17분 12초다. Clean full 뒤에는 문서만 갱신했으므로 complete regression을 반복하지 않았다. 상세 계약은 [Series frontend readiness hardening](series-lifecycle-v1-frontend-readiness-hardening.md)에 기록한다.

### Series Frontend V1

Series frontend의 deterministic contract lane은 다음과 같다.

```text
npm run series:verify
npm run player-draft:verify
npm run build
npm run reference:check
npm run reference:verify
npm run bundle:verify
```

`series:verify`는 arbitrary DK/HLE BO3/BO5 fixture로 exact create/current game/child/action/simulate 200·202/replay/commit/completion schema, Game 2+ binding, team-code score, cumulative Hard Fearless와 production identity를 검증한다. 202의 null match/reservation, stale revision ordering, pointer round trip과 malformed mutation 거부도 포함한다. 기존 Player Draft 33 scenarios, reference source/raw checksum, reference semantics와 lazy bundle boundary는 별도 명령으로 그대로 통과했다.

실제 LIVE browser smoke는 BFX–BRO BO3에서 관리 BFX/Game 1 BLUE/root 73으로 3 games, score 2–1, Hard Fearless 0→10→20과 final winner를 확인했다. DK–HLE BO5에서는 관리 DK/Game 1 RED, Game 1 commit, Game 2 BLUE child, previous picks의 structured unavailable reason, child/Series 각각 DELETE 204를 확인했다. Standalone Player Draft는 자체 session/action 10/simulate endpoint만, AUTO는 real-matches simulate endpoint만 호출했다. Series pointer reload는 같은 ID/revision/status를 GET으로 복구했다. 1440×900과 1280×720에서 horizontal overflow는 0이고 browser console error도 0이었다.

Live Production V9 simulation은 모두 HTTP 200으로 끝나 actual 202를 강제하지 않았다. 202 polling, compact commit replay와 response-loss reconciliation은 synthetic contract에서 검증했고, 실제 delayed worker/response-loss와 전체 keyboard/screen-reader journey는 `SERIES_LIVE_E2E_AND_ACCESSIBILITY`에 남긴다. Frontend-only 작업이므로 이번 milestone을 위한 backend full regression은 실행하지 않았다. 상세 결과는 [Series Frontend V1](series-frontend-v1.md)에 있다.

### Series LIVE E2E and Accessibility

Series transport/accessibility hardening은 위 여섯 frontend 명령을 그대로 final lane으로 사용한다. `series:verify`는 committed winner tally와 score exact equality, ACTIVE/COMPLETED required-win/status, result/receipt pairing, pointer error classification, Series/child cancel logical command binding과 double-submit request count를 추가로 검증한다.

Actual Playwright는 DK–HLE 정상 Game 1과 BFX–BRO complete BO3, 동일 simulate command의 실제 reservation 202, simulate/child cancel/Series cancel 응답 손실 뒤 GET reconciliation, active pointer reload/network 유지/실제 not-found 제거를 확인했다. 1280×720·1440×900 horizontal overflow 0, modal viewport/focus, reduced-motion `0.00001s`, live region role/name을 DOM으로 검증했다. AUTO는 `/api/v1/real-matches/simulate`, standalone 직접 Draft는 `/api/v1/player-drafts/sessions` create/action/cancel만 사용해 endpoint 격리도 재확인했다.

의도적으로 response CORS를 차단한 요청의 browser error는 fault evidence로 따로 기록하고 clean 정상/202 flow의 console/page/runtime validation error 0과 섞지 않는다. 실제 backend Java/resource/Gradle/runtime은 변경하지 않았으므로 backend full regression은 실행하지 않는다. 상세 ID, status, request count와 제한은 [Series LIVE E2E and Accessibility](series-live-e2e-and-accessibility.md)에 있다.

다음은 검증 결과 또는 일시 artifact이며 correctness input이나 source of truth가 아니다.

- `backend/build/`, `backend/bin/`
- `backend/*.log`, `backend/*.csv`, generated JSON
- `frontend/dist/`, `frontend/node_modules/`, `*.tsbuildinfo`
- repository `output/`

기존 report를 baseline 참고로 읽을 수는 있지만 architecture/resource contract는 production source와 active JSON에서 복원한다. expected result를 바꾸기 전에는 intended behavior, Random order, eligibility, priority, duplicate mutation, event classification 중 원인을 구분한다.

### Match Engine V9 production acceptance sanity

T1/GEN 고정 Draft product-sanity population은 공통 `diagnostic`과 `match-engine-v9-production-acceptance` tag를 사용하므로 default `test`에서 제외된다. Normal/focused lane에는 lineup legality, static archetype threshold, 30개 authored proficiency binding, exact 1,200-cell schedule, policy/API contract와 두 fixture `BASELINE_V1` replay oracle만 남는다.

```text
gradlew.bat test \
  --tests com.lolfm.application.MatchEngineV9ProductionAcceptanceContractTest \
  --tests com.lolfm.application.MatchEngineV9ProductionAcceptanceRollbackOracleTest \
  --tests com.lolfm.application.MatchEngineV1ContractTest \
  --tests com.lolfm.controller.RealMatchApiV1ControllerTest --console=plain
gradlew.bat finalizeMatchEngineV9ProductionAcceptanceCandidate --console=plain
gradlew.bat test --console=plain
gradlew.bat verifyAndPromoteMatchEngineV9ProductionAcceptance --console=plain
```

Candidate task만 2 scenarios × 4 orientations × 3 profiles × 50 seeds = 1,200 core simulation을 실행한다. Replay 4회, instrumentation 전용 2회, rollback oracle 4회는 core와 분리해 총 추가 10회로 보고한다. Clean full 뒤 A/B finalizer는 같은 raw CSV/JSON만 읽어 검증·집계하며 gameplay simulation과 Random 소비는 0이다. A/B output 전체가 byte-identical일 때만 `build/reports/match-engine-v9-production-acceptance/`로 승격한다. 이 population은 calibration, blind holdout, balance 승인 또는 현실 LCK 승률 표본이 아니다.

최종 acceptance tree는 focused 4 suites / 22 tests를 1분 26초에 통과했고, candidate diagnostic은 약 3분에 1,200+10회를 완료했다. Frontend build는 87 modules / Vite 1.47초, complete backend regression은 첫 실행 221 suites / 2,203 tests / failures 0 / errors 0 / skipped 0, aggregate JUnit XML 859.986초 / Gradle wall 14분 29초였다. Full 뒤 raw-only A/B finalizer와 promotion은 약 4초에 byte-identical output과 official manifest 12/12를 확인했다. 이후 변경은 문서뿐이므로 full regression을 반복하지 않았다.

## Test Memory

`backend/build.gradle`의 `test` task는 `maxHeapSize = '2g'`를 설정한다. 이는 test JVM 전용 heap 상한이며 Spring Boot production JVM 또는 frontend process의 memory 설정이 아니다.

### Match Engine V9 fresh Auto Draft 재검증

Focused/preflight와 official lifecycle은 별도 explicit task로 분리한다. Default `test`는 대형 worker/finalizer를 실행하지 않는다.

```text
gradlew.bat verifyMatchEngineV9FreshDraftProbe --console=plain --no-daemon
gradlew.bat freezeMatchEngineV9FreshRequalification --console=plain --no-daemon
gradlew.bat smokeMatchEngineV9FreshRequalification --console=plain --no-daemon
gradlew.bat test --console=plain
gradlew.bat recordMatchEngineV9FreshFullRegressionReceipt --console=plain
gradlew.bat runMatchEngineV9FreshCalibrationWorkers --console=plain --no-daemon
gradlew.bat finalizeMatchEngineV9FreshCalibration --console=plain --no-daemon
```

최종 source-bound full은 217 suites / 2,194 tests / failures 0 / errors 0 / skipped 0, aggregate XML 721.031초와 Gradle wall 12분 6초로 통과했다. Calibration은 네 explicit JVM shard에서 100 checkpoint, 400 production Auto Drafts, 1,200 core rows, replay 300건과 instrumentation parity 300건을 만들었다.

Fresh-JVM finalizer reload는 첫 checkpoint canonical payload digest 불일치로 실패했다. Raw checkpoint/sidecar는 100/100 일치했지만 signed payload 안 `Set.copyOf` 순서가 cross-JVM canonical order가 아니었다. Contract에 따라 holdout authorization은 생성하지 않았고 동일 calibration/finalizer를 성공할 때까지 재시도하지 않는다. 상세 결과는 [fresh 재검증 V1](match-engine-v9-auto-draft-matchup-composition-fresh-requalification-v1.md)에 있다.

### Match Engine V9 fresh Auto Draft 재검증 V2

V2는 V1의 unordered signed collection, action identity 누락, 시간 선행만으로 인정하던 간접 인과, duplicate slot/payload 혼합과 standalone finalizer 재실행 위험을 먼저 보강한다. 대형 population은 계속 default `test`에서 제외하고, preflight와 worker/finalizer를 명시적 task로만 실행한다.

```text
gradlew.bat runMatchEngineV9FreshSerializationPreflight --console=plain
gradlew.bat verifyMatchEngineV9FreshSerializationPreflight --console=plain
gradlew.bat runMatchEngineV9FreshDraftProbe --console=plain
gradlew.bat verifyMatchEngineV9FreshDraftProbe --console=plain
gradlew.bat smokeMatchEngineV9FreshRequalification --console=plain
gradlew.bat freezeMatchEngineV9FreshRequalification --console=plain
gradlew.bat test --console=plain
gradlew.bat recordMatchEngineV9FreshFullRegressionReceipt --console=plain
gradlew.bat runMatchEngineV9FreshCalibrationWorkers --console=plain
gradlew.bat finalizeMatchEngineV9FreshCalibrationV2 --console=plain
```

`finalizeMatchEngineV9FreshCalibrationV2`는 이미 존재하는 authenticated calibration checkpoint만 읽는 standalone artifact-only finalizer다. Worker task에 의존하지 않으며 checkpoint가 없거나 source/contract가 다르면 gameplay를 실행하지 않고 실패한다. 전체 자동 수명주기를 처음부터 실행하는 aggregate alias는 `runMatchEngineV9FreshRequalificationV2`지만, 이미 소비된 official population에 재사용하는 명령이 아니다.

Focused 92 tests, serialization fresh-JVM A/B, Auto Draft probe A/B, DRY_RUN smoke와 immutable baseline parity가 모두 통과했다. Serialization A/B raw SHA-256은 둘 다 `9351da98de9ed2ad6a524b835aee1eeb42da4f0e0af83f5ff792074b78cb2e1d`, Draft probe A/B는 둘 다 `dd33583045cf1f0806b44569eab80ce5881ba7428f1d92629393d46cb65f70f5`다. 최종 complete backend regression은 219 suites / 2,200 tests / failures 0 / errors 0 / skipped 0, Gradle wall 14분 43초로 한 번에 통과했다.

공식 calibration은 4개 fresh JVM에서 fixture 100, production Auto Draft 400, core rows 1,200, paired rows 800, replay 300, instrumentation parity 300을 생성했다. 네 worker identity는 모두 달랐고 checkpoint/sidecar 100/100과 receipt 4/4를 finalizer가 인증했다. Artifact-only finalizer proof는 `gameplayExecutionCountBefore=0`, `After=0`, `coreSimulationCount=0`이다.

Calibration exact integrity와 Composition causal gate는 통과했지만 Matchup 공개 divergence 400쌍 중 exact direct action cause는 1, indirect prior-state cause는 0, unresolved snapshot cause는 399였다. `UNRESOLVED_SNAPSHOT_CAUSE` exact-zero gate 때문에 상태는 `MATCH_ENGINE_V9_FRESH_REQUALIFICATION_V2_BLOCKED_HOLDOUT_NOT_CONSUMED`이다. `calibration-operational-gate-failed.json`은 `holdoutAuthorized=false`를 고정하며 authorization/start/completion과 holdout checkpoint/receipt는 생성되지 않았다. Final A/B, promotion과 recursive `SHA256SUMS.txt`도 도달하지 않았으므로 존재하지 않는다. 자세한 수치와 후속 경계는 [fresh 재검증 V2](match-engine-v9-auto-draft-matchup-composition-fresh-requalification-v2.md)에 있다.

### Player Draft interactive/simulation performance hardening V1

Player Draft 성능 검증은 동일 GEN–T1/73 BLUE/RED 10-action script의 paired direct backend와 actual Chromium을 사용한다. Completed Draft fast path, revision projection reuse, exact replay, standalone/Series 변조 거부와 cross-session 격리는 focused lane에서 먼저 검증하고, frontend contract/build와 LIVE BLUE/RED를 확인한 뒤 final executable production tree에서 complete backend regression을 한 번 실행한다.

```text
gradlew.bat test --tests com.lolfm.draft.PlayerControlledDraftEngineTest --tests com.lolfm.application.PlayerControlledDraftMatchInputBoundaryTest --tests com.lolfm.application.PlayerDraftProjectionReuseTest --tests com.lolfm.application.PlayerDraftSimulationHardeningTest --tests com.lolfm.application.PlayerDraftSessionRepositoryTest --tests com.lolfm.application.SeriesLifecycleHardeningTest --tests com.lolfm.application.SeriesProductionV9SmokeTest --console=plain
npm run player-draft:verify
npm run build
gradlew.bat test --no-daemon --console=plain
```

Paired diagnostic와 artifact 생성은 명시적으로만 실행하며 default correctness test에서 대규모 표본을 만들지 않는다. Artifact는 `backend/build/reports/player-draft-performance-hardening-v1/`에 두고 historical profiling report는 덮어쓰지 않는다. 자세한 설계와 acceptance는 [Player Draft performance hardening V1](player-draft-interactive-and-simulation-performance-hardening-v1.md)에 있다.

최종 focused 경계는 7 suites / 44 tests를 clean pass했고, frontend contract 33 scenarios와 production build 101 modules도 통과했다. Final executable tree의 complete backend regression은 첫 실행에서 238 suites / 2,274 tests / failures 0 / errors 0 / skipped 2, aggregate XML 871.674초와 Gradle wall 14분 43초로 통과했다. 두 skip은 explicit 환경 변수로만 실행하는 latency profiling/performance paired diagnostic이며 correctness 누락이 아니다. Full 뒤에는 executable production tree를 바꾸지 않았다.

### Team and Player Information API V1

Career raw SHA/count, four-catalog `PlayerId` join, deterministic HTTP와 기존 gameplay 비영향은 다음
focused lane으로 검증한다.

```text
gradlew.bat test \
  --tests com.lolfm.reference.PlayerCareerResourceLoaderTest \
  --tests com.lolfm.reference.TeamPlayerInformationCatalogTest \
  --tests com.lolfm.reference.TeamPlayerInformationCrossJvmTest \
  --tests com.lolfm.controller.TeamPlayerInformationApiV1ControllerTest \
  --tests com.lolfm.controller.TeamPlayerInformationApiV1ErrorBoundaryTest \
  --tests com.lolfm.controller.TeamPlayerInformationTransportIntegrationTest \
  --tests com.lolfm.application.TeamPlayerInformationGameplayIsolationTest \
  --tests com.lolfm.player.PlayerIdentityCatalogTest \
  --tests com.lolfm.player.PlayerRatingCatalogTest \
  --tests com.lolfm.player.ChampionProficiencyCatalogTest \
  --tests com.lolfm.player.ChampionProficiencyResourceLoaderRejectionTest \
  --tests com.lolfm.player.LckTeamAssemblerTest \
  --tests com.lolfm.controller.RealMatchApiV1ControllerTest \
  --tests com.lolfm.application.MatchEngineV1ContractTest \
  --console=plain --no-daemon
```

최종 focused 결과는 14 suites / 90 tests / failures 0 / errors 0 / skipped 0,
aggregate JUnit XML 90.608초, Gradle wall 1분 44초다. SHA/version/scope, 10팀·50명과
career 248/154/21/248, duplicate/missing/unknown/malformed ID, current binding mismatch,
rating 600개와 OVR/CA 부재, proficiency 732개/neutral 1,428개, snapshot/null/date precision,
same-process와 두 fresh JVM byte identity, 전체 endpoint/error/query/gzip, GEN/Chovy route identity,
Real Match options 10/50, Season/standings mutation 0과 GEN–T1 seed 73의 Draft/timeline/Random/output
exact parity를 포함한다.

LIVE smoke는 caller-owned 8767 port와 별도 in-memory H2를 사용한다. 이 환경의 기본 file H2 URL은
기존 `AUTO_SERVER=TRUE;DB_CLOSE_ON_EXIT=FALSE` 조합을 H2가 지원하지 않아 application context 전에
중단됐고, 설정 파일을 바꾸거나 같은 실패 명령을 반복하지 않았다. In-memory datasource override로
실제 production application을 실행한 뒤 metadata, teams, players, GEN, Chovy, unknown team/player와
기존 Real Match options를 HTTP로 확인했다. Catalog hash는
`4b5af4a49b5299b850015ea162be7e28543b1c4cb87e672120f84b26af815504`였고 Chovy detail
identity JSON은 10,596자였다. `Content-Encoding: gzip`, `Vary`의 `accept-encoding`, CORS와
gzip/identity JSON 의미 동등성이 통과했다. Smoke 종료 후 caller-owned server PID만 종료했다.

최종 executable production tree의 complete backend regression은 첫 실행에서 266 suites / 2,362 tests /
failures 0 / errors 0 / skipped 2, aggregate JUnit XML 1,285.281초, Gradle wall 21분 38초로
clean pass했다. 두 skip은 기존 explicit 대형 diagnostic이며 이번 API correctness 누락이 아니다.
이후에는 문서만 갱신했으므로 full regression을 반복하지 않았다.

Frontend source는 변경하지 않으므로 frontend build/Playwright는 실행하지 않는다. 90-fixture Season,
balance/calibration/holdout와 대형 statistical diagnostic도 실행하지 않는다.

### Career Mode V1 foundation and save/load API

Career create/load와 직접 영향 League 경계는 다음 focused lane으로 검증한다.

```text
gradlew.bat test \
  --tests com.lolfm.controller.CareerApiV1ControllerTest \
  --tests com.lolfm.league.CareerModePersistenceTest \
  --tests com.lolfm.league.LeagueRelationalPersistenceAndJobTest \
  --tests com.lolfm.controller.LeagueApiV1ControllerTest \
  --console=plain --no-daemon
```

새 test class는 2개다. 대표 GEN Career의 server-owned Hybrid Season 1개와 18 rounds/90 fixtures
(18 Player/72 Auto), caller identity/seed/mode 주입 불가, strict request, exact replay/conflict mutation
0, transaction rollback, lightweight list/detail와 조회 revision mutation 0을 확인한다. Task-owned file
H2를 close/reopen해 같은 Career/Season/Player Series binding resume가 재생성 없이 복구되고 대표
identity mismatch가 fail-closed하는 것도 확인한다. Draft/BO3/Match simulation은 반복하지 않는다.

최종 focused 결과는 4 suites / 13 tests / failures 0 / errors 0 / skipped 0, aggregate JUnit XML
46.281초, Gradle wall 1분 9초다. 첫 full 뒤 bounded Career list SQL 누락을 찾아 최대 100개 제한을
추가하고 이 lane을 재실행했다. Final executable tree의 두 번째 complete backend regression은
268 suites / 2,364 tests / failures 0 / errors 0 / skipped 2, aggregate XML 1,397.130초,
Gradle wall 23분 32초, `BUILD SUCCESSFUL`이다. 이후 production Java/resource/Gradle/shared fixture는
변경하지 않는다.

Frontend source를 변경하지 않았으므로 frontend build/Playwright는 실행하지 않는다. 90-fixture 실제
실행, fresh-JVM probe, artifact writer, JFR와 대형 population/balance/calibration/holdout도 실행하지
않는다.

### Career dashboard frontend V1 and load projection hardening

Phase A backend focused lane은 최종 production/test source에서 다음과 같이 실행했다.

```text
cd backend
gradlew.bat test \
  --tests com.lolfm.controller.CareerApiV1ControllerTest \
  --tests com.lolfm.league.CareerModePersistenceTest \
  --console=plain
```

결과는 2 suites / 3 tests / failures 0 / errors 0 / skipped 0, Gradle wall 15초,
`BUILD SUCCESSFUL`이다. 실제 검증 범위는 expired simulation reservation을 포함한 Career GET/List 전후
Career/League/fixture/binding/checkpoint/command/job/attempt/receipt/outbox/application DB snapshot exact
equality, PAUSED의 `RESUME_SEASON`, VERIFIED의 non-Player route, capacity seam 1의 최초 생성/한도 거부/
exact replay mutation 0, command schema와 target tamper fail-closed, task-owned file-H2 restart identity다.
새 backend test class나 100개의 실제 Season은 만들지 않았다.

Frontend에서는 다음 명령을 실행했다.

```text
cd frontend
npm run career:verify
npm run league:verify
npm run bundle:verify
npm run build
```

`career:verify`는 list/detail/create, strict unknown/missing/type 거부, capacity/order cross-field,
Player resume relation, logical create UUID reuse, not-found/transient/integrity pointer recovery, structured
route와 return-context 최소 저장의 8 scenarios를 모두 통과해
`CAREER_DASHBOARD_FRONTEND_V1_CONTRACT_VERIFICATION_PASSED`를 출력했다. 마지막 frontend source
변경 뒤 Career/League verifier와 build를 다시 실행했고, 최종 build 뒤 bundle verifier도 통과했다.
Bundle 결과는 initial graph
`assets/index-DnCAMRoU.js` 510,839 bytes, reference chunk
`matchSession.adapter-BFFoLE5V.js` 423,581 bytes, lazy `true`다.

기존 `league:verify`의 League contract와 completion recovery도 모두 통과했다. Production build는
151 modules를 transform했고 Career lazy chunk는 26.45 kB(gzip 9.60 kB), initial index는
480.34 kB(gzip 152.27 kB)로 `BUILD` 오류 없이 완료됐다. Series core source를 변경하지 않아
`series:verify`는 실행하지 않았다.

Playwright LIVE는 user runtime과 분리한 backend 8091, frontend 5173, task-owned file-H2에서 실행했다.
빈 Career 화면에서 실제 Team/Player API의 10팀을 확인하고 `GEN 라이브 저장`/`라이브 감독`/GEN으로
POST 201을 받은 뒤 목록/상세, Career→League→Career와 reload의 list/detail GET 복구를 확인했다.
Browser storage에는 Career ID만 남고 manager/allowedCommands는 없었다. Dialog initial focus,
Tab/Shift+Tab trap, Escape, 실제 trigger focus return은 모두 통과했다. 1440×900과 1280×720의 html/body
horizontal overflow는 모두 0, primary action visible, console errors/warnings 0이었다. Browser와 두 server를
종료하고 task-owned H2 파일을 제거했다. Mock/service worker/response rewriting, 90경기와 Player BO3는
사용하지 않았다.

최종 executable backend tree에서는 다음 complete regression을 정확히 한 번 실행했다.

```text
cd backend
gradlew.bat test --console=plain
```

결과는 268 suites / 2,365 tests / failures 0 / errors 0 / skipped 2, aggregate JUnit XML
1,297.578초, Gradle wall 21분 52초, `BUILD SUCCESSFUL`이다. 두 skip은 기존 explicit 대형 diagnostic이다.
Clean full 뒤 backend executable production source, resource, Gradle과 shared fixture는 바꾸지 않았다.
Frontend의 빈 표시 이름 validation과 Player Series 실패 시 League fallback을 보강한 뒤 위 frontend
focused/build/bundle lane을 다시 통과했으므로 backend full regression은 반복하지 않았다. Balance/calibration/
holdout, 대형 seed population, fresh-JVM proof, artifact writer, JFR, 장시간 BO3/다음 Round E2E는 실행하지
않았다.

### Career time and calendar progression V1

최종 Calendar/advance production 및 test source에서 기존 Career 테스트 두 class를 확장해 실행했다.

```text
cd backend
gradlew.bat test \
  --tests com.lolfm.league.CareerModePersistenceTest \
  --tests com.lolfm.controller.CareerApiV1ControllerTest \
  --console=plain
```

결과는 2 suites / 6 tests / failures 0 / errors 0 / skipped 0, `BUILD SUCCESSFUL`이다. 검증 범위는
2027/2028 결정적 투영과 null stage 보존, source/count/KeSPA 경계, 18 rounds/90 fixture overlay의
window/order/uniqueness 및 ID/seed 불변, advance exact replay/stale revision, 후속 진행 뒤 과거 receipt
결과 replay, file-H2 restart, V4→V5 no-backdating migration, 관리 경기 stop과 같은 날짜 Auto dispatch다.
새 backend test class는 추가하지 않았다.

Frontend에서는 다음을 실행했다.

```text
cd frontend
npm run career:verify
npm run build
```

`career:verify`의 11 scenarios가 모두 통과해
`CAREER_TIME_AND_CALENDAR_PROGRESSION_V1_CONTRACT_VERIFICATION_PASSED`를 출력했다. Strict
Calendar/advance response, unknown field fail-close, KeSPA definition 미생성, V1 create pointer의 V2
`canonicalSelectionKey` migration과 pending advance 최소 pointer를 확인했다. Production build는
152 modules를 transform하고 성공했다.

실제 isolated backend와 browser에서는 Career 생성 → Calendar GET → 다음 일정 advance → reload의
GET 복구를 실행해 `2027-01-14`/revision 1이 유지되는 것을 확인했다. 1280×720에서 horizontal
overflow와 primary action 가시성을 확인했다. Mock/service worker나 사용자 runtime DB는 사용하지
않았다.

최종 executable backend tree의 첫 complete regression은 2,368 tests 중 기존
`LeagueRelationalPersistenceAndJobTest` 한 assertion만 실패했다. V5 추가로 V1→latest migration 수가
3에서 4로 늘어난 의도된 변화였고 product state/behavior 실패가 아니었다. 기대값을 교정한 뒤 해당
단일 migration/restart 테스트가 `BUILD SUCCESSFUL`로 통과했다. 최종 complete regression 결과는
다음과 같다.

| 항목 | 결과 |
| --- | ---: |
| JUnit suites | 268 |
| Tests | 2,368 |
| Failures | 0 |
| Errors | 0 |
| Skipped | 2 |
| Aggregate JUnit XML time | 2,180.060 seconds |
| Gradle wall duration | 37m 14s |
| Build | `BUILD SUCCESSFUL` |

두 skip은 기존 explicit 대형 diagnostic이다. Clean full 뒤 executable production Java/resource,
Gradle과 shared fixture는 변경하지 않고 문서만 갱신했다. 90경기 전체 시즌, 모든 11개 대회의 E2E,
대형 seed population, balance/calibration/holdout과 fresh-JVM artifact proof는 실행하지 않았다.

### Career competition lifecycle V1 with Calendar hardening

Phase A는 competition 구현 전에 Calendar recovery/transition gate를 먼저 닫았다. 다음 focused
3개 class가 42초에 `BUILD SUCCESSFUL`로 통과했다.

```text
cd backend
gradlew.bat test \
  --tests com.lolfm.league.CareerModePersistenceTest \
  --tests com.lolfm.league.LeagueRelationalPersistenceAndJobTest \
  --tests com.lolfm.controller.CareerApiV1ControllerTest \
  --console=plain --no-daemon
```

검증 범위는 Career별 pending advance 영속/복구, Season lifecycle stop, completed command의 frozen
result/live Calendar 분리, overlay V2 provenance, Calendar→기존 Auto job/receipt/outbox/standings
exactly-once다. Phase A gate 통과 뒤에만 Phase B를 시작했다.

Phase B final focused lane은 다음 4개 suite를 실행했다.

```text
cd backend
gradlew.bat test \
  --tests com.lolfm.career.CareerCompetitionRulesTest \
  --tests com.lolfm.league.CareerModePersistenceTest \
  --tests com.lolfm.controller.CareerApiV1ControllerTest \
  --tests com.lolfm.league.LeagueRelationalPersistenceAndJobTest \
  --console=plain --no-daemon
```

결과는 4 suites / 19 tests / failures 0 / errors 0 / skipped 0, Gradle wall 33초,
`BUILD SUCCESSFUL`이다. Road와 Play-in exact routing, R3~4 40 fixture 결정성/충돌 없음/record carry,
V7 migration, R1~2 seal과 restart, completion receipt exact replay/cross-scope 거부, strict additive
Calendar/API 계약을 확인했다.

Frontend `npm run career:verify`는 15개 시나리오와 Calendar hardening/competition marker를 모두
통과했다. `npm run build`는 153 modules를 transform해 성공했고 `git diff --check`도 깨끗했다.
격리 backend는 V7 migration과 8085 startup까지 확인했지만 browser host에 Chrome이 없고 설치된
Firefox도 `libasound2t64`가 없어 Playwright process를 시작하지 못했다. 사용자 서버/DB는 변경하지
않았고 task-owned runtime은 종료했다. 따라서 실제 browser interaction은 환경 제한으로 미검증이다.

Final executable production tree의 complete backend regression은 정확히 한 번 실행했다.

| 항목 | 결과 |
| --- | ---: |
| JUnit suites | 269 |
| Tests | 2,375 |
| Failures | 0 |
| Errors | 0 |
| Skipped | 2 |
| Aggregate JUnit XML time | 1,366.527 seconds |
| Gradle wall duration | 23m |
| Build | `BUILD SUCCESSFUL` |

두 skip은 기존 explicit 대형 diagnostic이다. Clean full 뒤 executable production Java/resource,
Gradle과 shared fixture는 변경하지 않고 문서만 갱신했다. 90/130경기 전체 LIVE, Cup/LCK playoff
실행, 대형 seed population, balance/calibration/holdout, fresh-JVM proof bundle과 JFR은 실행하지
않았다. Source가 없는 edge를 임의 규칙으로 보충하는 검증도 수행하지 않았다.

### Career Competition Series execution and result transition V1

Phase A와 execution/result 위험은 기존 test class를 확장해 검증한다. 최종 focused lane은 다음과 같다.

```text
cd backend
./gradlew test \
  --tests com.lolfm.career.CareerCompetitionRulesTest \
  --tests com.lolfm.controller.CareerApiV1ControllerTest \
  --tests com.lolfm.league.CareerModePersistenceTest \
  --tests com.lolfm.controller.LeagueApiV1BackgroundExecutionIntegrationTest \
  --tests com.lolfm.league.LeagueRelationalPersistenceAndJobTest \
  --console=plain --no-daemon
```

이 lane은 due WAITING/future start fail-close, first/future seed authority, dynamic Cup stage, immutable
binding과 Player checkpoint, Auto durable job/lease/fence, verified receipt tamper/cross-scope/replay,
Cup 25+5+10 pure transition, Road/R3~4/Play-in routing, migration/restart, background snapshot 격리를
확인한다. 실제 Production V9은 대표 Auto fixture 1개만 실행하고 40개 Cup Series와 90/130경기 Season,
balance/calibration/holdout/large-seed diagnostic은 실행하지 않는다.

Frontend는 `npm run career:verify` 21 scenarios와 네 marker가 통과했고, `npm run build`는 TypeScript와
153 modules transform을 완료했다. 기존 shared Series response 계약도 `npm run series:verify`로
확인했다. Browser는 1280×720에서 관리 fixture의 기존 Series 진입/reload/return과 Auto fixture의
file-H2 restart recovery를 실행했다. Auto는 동일 UUID/job로 202 재제출된 뒤 Cup `0/40`→`1/40`,
next match `GB_B1_E2`→`GB_B3_E4`가 됐고 완료된 operation은 제거됐다.

최종 focused backend는 5 suites / 29 tests / failures 0 / errors 0 / skipped 0,
Gradle wall 3분 57초로 통과했다. 첫 complete backend run은 1,981 tests 중 과거
`build/reports/champion-pair-interaction-shape/...csv` consumer 한 메서드가 diagnostic tag 누락으로
실패했다. 해당 artifact-dependent 메서드만 격리하고 같은 class의 correctness 14 tests가 failures 0 /
errors 0으로 유지되는 것을 확인했다.

두 번째이자 최종 complete regression 결과는 다음과 같다.

| 항목 | 결과 |
| --- | ---: |
| JUnit suites | 260 |
| Tests | 1,980 |
| Failures | 0 |
| Errors | 0 |
| Skipped | 2 |
| Aggregate JUnit XML time | 2,184.812 seconds |
| Gradle wall duration | 37m 12s |
| Build | `BUILD SUCCESSFUL` |

두 skip은 기존 explicit skip이다. Clean full 뒤 executable production Java/resource/Gradle/shared
fixture는 변경하지 않고 문서만 갱신했다.

## Career 계약 시장 V1 (2026-09-06)

새 `CareerMarketEngineTest`에서 경쟁 제안 순서/게임 선호, 기간별 예산, 급여 날짜 경계,
예약 재계약/만료, 카운터/철회와 제한된 56구단 시장 주기를 확인한다. 새 테스트 클래스를
계층마다 만들지 않고 기존 `CareerModePersistenceTest`, `CareerDomesticExecutionTest`,
`LeagueAutomatedSeriesRunnerProductionV9Test`에 저장 원자성/재시작/반복 스토브/등록 자격과
실제 영입 선수 Auto BO3 검증을 확장했다. 반복 시즌 경기 완료 준비는 기존 합성 helper다.
실제 모든 해외 리그나 수년 치 경기, 대규모 분포/밸런스 진단을 실행하지 않는다.

프런트는 `npm run career:verify`, `npm run build`와 하나의 실제 브라우저 스토브→FA 협상→
영입→선발→Player Draft 흐름 및 원본 요청 응답 소실 복구를 사용한다. 변경하지 않은
Series/League/Draft 공용 계약 verifier를 중복 실행하지 않는다. 최종 전체 backend `test`는
생산 코드와 필요한 브라우저 수정을 완료한 뒤 실행하며 실제 집계/시간은
[계약 시장 검증 기록](career-contracts-stove-fa-market-ai-competition-v1.md)에 기록한다.

최종 전체 실행: `./gradlew test --console=plain --no-daemon`, **265 suites / 2,021 tests /
실패 0 / 오류 0 / 기존 skip 2**, **27분 25초**. 전체 명령은 총 세 번 시작했으며 두 번째는
환경 재시작으로 중단됐다. 집중 검증으로 미완료 전체 실행을 대체할 수 없어 JDK/실행 환경을
복구한 뒤 최종 실행을 마쳤다. 첫 실행의 유일한 실패는 새 시장 사건이 먼저 도래하는 Calendar
기대 날짜였고, 원래 Player/Auto 검증을 유지하면서 해당 테스트만 교정했다. 프런트 Career
verifier는 53건 통과, TypeScript/Vite production build도 통과했다.

## Career 시장 결함·추가 선수 V4 통합 (2026-09-06)

기존 `CareerMarketEngineTest`, `GlobalTeamRosterCatalogTest`, `CareerModePersistenceTest` 관련
범위를 확장한다. 금융 부족/정확한 한도/방출/연간 배정, 만료 직전 날짜 parameterized 경계와
실제 Calendar 저장·UUID·재시작, 신규 V4 Series 입력/기존 directory 보존을 확인한다. 원본
180명 master와 runtime의 선형 대조는 `python3 backend/scripts/verify-expanded-player-v4.py`다.
원본 패키지 체크섬 PASS를 runtime 통합 증거로 대체하지 않는다.

집중 25건 통과 후 추가 금융 경계 2건·최종 저장 1건을 통과했고 프런트 Career verifier 55건과
production build를 확인했다. 브라우저는 KT 계약 제안→공통 결정→월말 급여의 한 흐름이다.
180명별 경기나 큰 seed/다년 시장 분포 진단은 실행하지 않는다. 최종 production Java/resource
변경 전체 회귀는 **1회, 29분 6초**, **265 suites / 총 2,028 tests / 통과 2,026 / 실패 0 /
오류 0 / 기존 skip 2**, aggregate XML **2,953.651초**로 통과했다. clean full 이후 문서만
갱신했다. 상세 결과는 [통합 보고서](career-market-fixes-and-expanded-player-v4-integration-v1.md)에 기록한다.


## Career 출전 약속·유료 이적·임대 V1 (2026-09-07)

기존 CareerMarketEngineTest 30건에서 가치/기간 경계·실제 연봉 독립성, 역할 차이·관찰 유예와
무경기 기간 반복 불만 방지, 거래 거절/경쟁/단일 지급, 임대 원계약·분담·체불·반환·연도 경계,
재계약 소속 연속성과 제한 예산 AI 임대 후보를 확인한다. 새 1:1 테스트 클래스는 만들지 않는다.
CareerModePersistenceTest의 새 통합 메서드는 파일 이주·실제 Calendar 적용·동시 UUID·다른
payload 충돌·원자 롤백·frozen 입력·활성 임대 재시작을 확인한다. 실제 경기 증거는 기존
LeagueAutomatedSeriesRunnerProductionV9Test의 Auto BO3 한 흐름에 유료 영입 Life와
검증 완료의 공통 출전 반영/재시도 무변경을 연결했다. 다년/해외 리그 실제 완주는 아니다.

최종 프런트 Career verifier 63건과 production build, 브라우저 KT→T1 유료 이적 및 T1→HLE
임대 흐름, 이적 제안 응답 소실의 원본 UUID 복구를 확인했다. 임대 반환/원계약 만료/연도 경계는
작은 엔진/파일 저장 증거를 사용하며 같은 브라우저 게임 완주를 반복하지 않는다.

production/API/resource/저장 연결 변경이므로 전체 backend test가 필요하다. 집중·브라우저
수정을 마친 최종 트리에서 `./gradlew test --console=plain --no-daemon`을 총 2회 실행했다.
첫 실행은 V17 추가 전 개수를 기대한 기존 단언 1건 실패(31분 45초), 해당 집중 1건은
교정 후 59초로 통과했다. 최종 전체는 **265 suites / 총 2,042 / 통과 2,040 / 실패 0 /
오류 0 / 기존 skip 2**, **33분 24초**(외부 wall 2,004.56초, XML 누적 3,212.621초)로 통과했다.
clean full 이후 문서만 갱신했다. 명령·시간·중간 실패 분류는 [통합 보고서](career-playing-time-promises-paid-transfers-and-loans-v1.md)에 기록한다.
장기 시장 분포, 큰 seed, 선수/구단 전수 경기, 새 감사 도구는 실행하지 않는다.

## Career 선행 3건·LCK CL V1 (2026-09-07)

선행 PA 순서 편향/이름/7개 순수 경로는 기존 CareerLifecyclePolicyTest에 추가했다.
CL 새 테스트는 CareerClPolicyTest와 CareerClExecutionTest 2개이며 작은 일정/순위/관찰
경계와 한 실제 Auto BO3→성장→1군 Player 입력, 통제된 전체 대진 전이를 구분한다.
기존 CareerModePersistenceTest의 lifecycle file DB 흐름으로 다음 시즌 활성화와 생성
placeholder 이름 이주·과거 입력 보존을 확인한다. 경기 전체를 반복하는 장기 검증은 없다.

선행 17건, CL/성장/시장/파일 저장 집중 62건, 이월 준비 시점 보완 후 파일 저장 1건이
통과했다. 프런트는 Career 계약 89건과 build, 격리된 실제 Auto 저장을 사용한 브라우저
등록·결과·성장·승격·다음 Draft 및 응답 소실 재시도를 확인했다. 별도 Player 완주는
실행하지 않았다. 전체 backend `test`는 이 요청 전체를 합쳐 계획된 1회로 실행하며,
실패 수정은 해당 실패와 직접 영향 범위를 확인한다. 최종 집계·시간·첫 실패/후속 결과는
[CL 보고서](career-lck-cl-execution-and-player-pathway-v1.md)에 기록한다.


CL 작업 최종 집계: 전체 1회 **269 suites / 2,094 tests / 2,087 통과 / 5 실패 / 오류 0 /
기존 skip 2**, **38분 22초**. 실패는 revision 준비 2건, 대회 수 1건, 이주 수 2건이다.
원래 전체 집계를 보존하고 해당 5개 메서드 전체를 교정 후 재실행해 **5/5, 6분 18초**로 통과했다.
뒤이어 사용자 수정 이름 판별과 구형 훈련 요청 payload hash 복구를 보완했고, 기존 이름 정책/
file DB 메서드 3개가 **3/3, 2분 36초**로 통과했다. 영향 범위가 제한된 조건·저장 경계로
확인돼 두 번째 전체는 실행하지 않았다. 최종 트리의 clean full 결과로 표기하지 않는다.


## Career 선행 A/B·AI 1군·CL 계획 V1 (2026-09-07)

새 테스트는 `CareerSquadPlanningPolicyTest` 1개다. 기존 시장/생애주기/CL 실행/파일 DB
테스트를 확장해 약속 기회와 실제 출전 보상 분리, 연속 CL 관찰, 승격/유지/대체자 부재,
현재 기량의 PA·피로 독립, 공통 제안 상한, 동의 후 재계약, 임대 반환 전망을 확인한다.
대표 통합은 저장 능력치를 준비한 뒤 실제 Calendar → AI 승격/CL 보완 → 다음 League
고정 입력까지이며, 성장 수년이나 Player/Auto 경기 전체를 추가 실행하지 않는다.

```bash
# backend/; 사용할 JDK를 JAVA_HOME에 지정한다.
./gradlew test --tests 'com.lolfm.career.CareerSquadPlanningPolicyTest' \
  --tests 'com.lolfm.career.CareerMarketEngineTest' \
  --tests 'com.lolfm.career.CareerLifecyclePolicyTest.fullClEmptySeasonsAccumulateAndInterruptedEvidenceResets' \
  --tests 'com.lolfm.career.CareerClExecutionTest.demotedStarterRemainsInActualCaptureWithoutClPromiseOrGrowthCredit' \
  --tests 'com.lolfm.career.CareerClExecutionTest.weeklyAiPromotionUsesSavedGrowthAndNextFrozenInputAtomically' \
  --tests 'com.lolfm.league.CareerModePersistenceTest.developmentMigrationPlansDailyMarketOrderAndFileRecovery' \
  --console=plain --no-daemon
```

위는 직접 영향 범위의 재현용 선택이다. 실제 단계별 실행은 선행 2건, 시장 40건/Calendar·capture
2건 통과 및 최종 정책·파일 재시작 7건 통과로 구분하며 중복 건수를 하나로 합치지 않는다.
`frontend/`의 `npm run career:verify` 92건과 `npm run build`, 격리 Career의
운영 이력 조회·새로고침 대표 브라우저 흐름을 확인했다. 계획된 전체 1회의 원본 결과와
실패 교정/시간은 [AI 계획 보고서](career-ai-first-team-cl-squad-planning-v1.md)에 별도로 기록한다.


AI 계획 작업의 전체 결과는 **1회 / 270 suites / 2,104 tests / 통과 2,102 / 실패·오류 0 /
기존 skip 2 / 34분 50초**다. 최종 제품 코드에서 통과했으며 이후 문서만 갱신했다.
원본 로그와 XML/집계는 `/tmp/career-ai-full-evidence/`에 보존했다. 전체 후 수정이나
두 번째 전체 실행은 없었다. 기존 진단 제외와 Gradle 설정은 변경하지 않았다.


## 매치 현실성·성능 Draft V1 검증 경로 (2026-09-08)

신규 동작은 기존 realism/structure/progression/jungle 테스트와 `UpperObjectiveTest`,
`DraftAbilityTest`, `RealismPolicyCompatibilityTest`에서 검사한다. 새 입력과 구 정책 선택을
분리하고 historical fixtures는 그 정책을 명시한다. 실경기 진단은 correctness test에 넣지 않는다.
이번 제한 진단은 기존 리뷰 64경기를 재사용하여 신규 75회와 tempo 연결 수정 후 같은 16개
입력의 추가 실행으로 총 91회다. 64경기와 최종 수정 후 16경기를 같은 표본으로 합치지 않는다.

프런트 `live:verify`는 기존 V9 artifact 정책과 신규 REALISM 정책을 각각 exact preflight한다.
신규 응답 검증에는 `LOLMANAGER_REAL_MATCH_OPTIONS_PATH`, `LOLMANAGER_REAL_MATCH_RESPONSE_PATH`,
`LOLMANAGER_REAL_MATCH_SEED`를 명시한다. 과거 artifact를 최신 기본값에 맞춰 덮어쓰지 않는다.
`player-draft:verify`, `series:verify`와 build, 대표 Series 수동/AI/새로고침 복원/결과 흐름을 함께 확인한다.
전체 회귀는 `JAVA_HOME=<JDK21> bash backend/scripts/test-linux.sh` 1회 후 원인별 영향 범위만
재검증한다. [원본 전체와 후속 결과](match-engine-realism-and-ability-based-draft-improvement-v1.md).

실제 전체 1회는 client disconnection/exit 143으로 중단됐다. 원본 완료 213개 클래스·1,675건
(통과 1,640·실패 33·기존 skip 2)의 로그와 Gradle in-progress 결과를 먼저 보존했다.
현재 JUnit 기본 discovery 277개 클래스와 대조한 미완료 64개 + 실패 영향 범위를 이어서 실행했다.
별도 background 1건 통과 후 79개 클래스·594건(588 통과·6 실패, 16분 26.626초)을 완료했고,
정책 기대값을 교정한 관련 5개 클래스·15건이 통과했다(1분 52.528초).
원본 및 후속 클래스 합집합은 277/277, 미해결 실패 0이다. 단일 clean full 결과로 부르지 않으며
두 번째 전체는 실행하지 않았다. 검증 요약은 해당 보고서의 `verification.json`에 보존한다.
