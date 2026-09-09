# Career 실제 플레이 속도 V1

## 범위와 기준

시작 HEAD는 `98baa05d4d36b04f316b27ae655af0fd5d9d3b86`, 브랜치는 `main`이다.
최종 HEAD와 브랜치도 같으며 구현은 working tree에 남겼다.
기존 `matches.csv`, `prompts/`, `선수정보.zip`은 이번 변경에 포함하지 않는다.
커밋·push·배포, 실제 사용자 DB 변경은 하지 않는다.

기존 DraftComputationContext, revision-bound Player Draft projection/completion binding,
immutableDefinition 재사용과 Linux Gradle 출력은 이미 적용된 기준이다. 과거 전체 테스트
44→24분이나 25분 39.89초를 실제 플레이 성능으로 사용하지 않는다.

## 선행 결함

G1: Calendar의 일반 실행 버튼이 `focusFixture != nextFixture`이면 비활성화됐다.
분석에서 관리 경기 A를 선택한 뒤 먼저 필요한 Auto B도 실행할 수 없었다. 준비 표시는
일반 서버 허용 행동과 분리했다. 일반 버튼은 현재 B의 유형·대진을 표시하며 기존
command/revision/UUID/mutation gate를 사용한다. 준비 표시는 직접 닫을 수 있고,
활성 시즌·대회 revision 변경 뒤 원래 관리 대상이 종료/교체/진행 중이면 정리한다.
동일 대상의 일반 새로고침은 표시를 유지한다. 늦거나 취소된 조회는 적용하지 않는다.
대상 A를 시작한다고 표시하면서 B를 실행하는 별도 단축 버튼은 만들지 않았다.

G2: 비교 조회는 통합 `LCK_REGULAR` scope만 검색했지만 match writer는 POG/POM을
`LCK_REGULAR_R1_R2` 또는 `LCK_REGULAR_R3_R4`에 저장한다. 기록과 비교가 하나의
`awardScopeFilter`를 사용하도록 했다. R1 조회는 R1 경기상과 통합 기간상을 포함하고
R3 경기상은 제외한다. R3도 대칭이며 전체 조회는 모두 포함한다. 통합 기간상 버튼에는
`정규시즌 전체`를 표시한다. 기존 시상 계산·저장·alias·instance·확정 및 hash 검증은 유지한다.

## 실측 시나리오와 환경

- 닫힌 초기 DB: `/tmp/career-speed/seed.mv.db`. 각 실행은 별도 파일 복사본이다.
- Career: `career_4ae6de38073aa8a2e266153e2bc3c38e32c3ce447ca615ed1933d28ef1a325f6`.
- 운영 날짜 2026-08-24→2026-08-26, 활성 시즌 2027, 관리 팀 GEN.
- 준비만 변경: 기존 READY Cup Auto NS–BFX를 시작 날짜로, GEN–DK 관리 경기를 이틀 뒤로 이동하고 기존 instance/cycle hash를 갱신했다. 원래 일정은 2027-01-14였다.
- 실제 측정: `NEXT_MANAGED_MATCH` 시작, NS–BFX 실제 Auto BO3, 두 날짜의 최초 실제 정산, `PLAYER_MATCH` 정지. 관리 Series는 시작하지 않는다.
- Auto root seed `8446205062685963256`, 결과 NS 1–2 BFX, 3세트 2,350/4,020/2,620초(합계 8,990초). 단축 결과나 합성 승리로 대체하지 않았다.
- 실제 NS 입력: Kingen/Sponge/Scout/Diable/Lehends. BFX: Clear/Raptor/VicLa/Taeyoon/Kellin. 닫힌 등록 입력을 그대로 사용했다.
- WSL2 Linux `6.18.33.2-microsoft-standard-WSL2`, 12 logical CPUs, Temurin 21.0.12.1+1, `-Xmx1g`, coordinator 1/competition worker 1.
- DB·Java 출력은 `/tmp`의 ext4 계열, 소스는 `/mnt/c`의 v9fs. 비교 양쪽 모두 같은 환경이다. 환경 이동을 새 개선 성과로 계산하지 않는다.
- 각 표본은 fresh JVM이며 앱/준비 GET을 거친 상태, 첫 실제 Draft 실행이다. 완전히 warm한 장기 실행 JVM의 처리량은 측정하지 않았다. Chromium headless 1440×1000, Vite, 동일 localhost:5173 흐름을 사용했다.
- 측정 중 Gradle/빌드/다른 게임 실행은 겹치지 않았다. 관측 도구는 100ms 주기로 작은 Continuous 상태를 읽고 H2 SQL 누적 통계와 짧은 JFR을 수집한다.

브라우저는 상대 분석→준비 A→일반 Auto B 활성 상태→Continuous의 실제 Auto 처리→관리 경기 이동 버튼 활성화를 확인한다. 추가 UI 확인을 위해 별도의 경기 완주를 만들지 않았다. 일반 B 버튼의 실제 callback과 busy 차단은 기존 TSX verifier에서 실행한다.

## 측정으로 선택한 변경

1. Draft 역할 풀 검사: 기존 코드는 각 미완성 역할마다 동일 챔피언의 정의와 지원 역할을 다시 읽었다. 후보 풀에서 챔피언을 한 번씩 읽어 역할별 **정수 개수**와 flex 개수를 만든다. 이후 double 합산은 원래 Position 순서와 식을 유지한다. 후보 풀·역할 매칭 순서·탐색 폭·점수·Random은 같다. 상태를 저장하는 캐시는 추가하지 않았다.
2. Continuous 고정 대기: 기존에는 즉시 실행 가능한 다음 단계에도 worker의 1초 지연이 붙었다. 최대 8 Career/250ms 확인 후 executor에 제어를 돌려주고, 실제 due 작업이 있으면 다음 bounded turn을 즉시 예약한다. 외부 job의 기존 2초 backoff, lease/fence와 트랜잭션은 유지한다. due 시각 순서로 다른 Career를 먼저 처리하며 경기 실행은 원래 별도 worker가 맡는다.
3. 읽기 잠금 충돌: 변경 전 실제 실행에서 소식 GET이 날짜 commit과 겹쳐 H2 40001을 반환했다. repeatable-read 스냅샷을 연 후 호환성 검사에서 `FOR UPDATE`로 승격하는 경로였다. 이미 활성화된 read-only repeatable-read/serializable 스냅샷에서는 같은 검사를 그 스냅샷으로 실행한다. 일반 명령의 잠금은 유지한다. 검증 생략이나 읽기 실패 무시는 아니다.

초기 JFR의 competition worker 표본에서 pool-health 프레임은 263→56,
ChampionCatalog 조회 프레임은 238→29였다. 이는 표본 관측이며 정확한 실행 횟수나
그대로의 wall-time 비율은 아니다. 같은 스레드 전체 표본은 2,228→1,917이다.
초기 누적 SQL에서 fixture/seed/result 관련 쿼리별 약 4,020회가 관측됐다. 이 통계는
시작 화면 GET까지 포함하므로 순수 Continuous SQL 감소율로 사용하지 않는다.

## 원시 시간과 판정

**실제 대기 시간 단축은 입증하지 못했다. 최종 두 표본 평균은 서버 수락 후 12.93%,
요청부터 사용 가능한 화면까지 10.60% 느렸다. 30% 권장 목표는 미달이다.**
기능 정확성 검증은 통과했고, 이 성능 판정과 구분한다.

모든 행의 처리량은 최초 날짜 정산 2일, 실제 Auto Series 1개/3세트이며 정지 이유는
`PLAYER_MATCH`다. [작은 원시 실행 기록](career-play-speed-v1.csv)에 6개 실행을 모두 보존한다.

| 표본 | 분류 | 수락 관측→서버 정지 관측 | 신청→서버 정지 추정 | 신청→사용 가능 DOM | 실제 클릭→DOM |
|---|---|---:|---:|---:|---:|
| before-1 | 브라우저 종료 계측 누락, 최종 비교 제외 | 70.026초 | 미수집 | 미수집 | 미수집 |
| before-2 | 기준 1 | 67.950초 | 69.305초 | 71.687초 | 미수집 |
| before-3 | 기준 2 | 56.624초 | 60.354초 | 62.827초 | 62.828초 |
| after-1 | 읽기 잠금 수정 전 중간 관측, 최종 비교 제외 | 57.301초 | 58.433초 | 60.567초 | 60.568초 |
| after-2 | 최종 1 | 73.365초 | 74.558초 | 77.818초 | 77.819초 |
| after-3 | 최종 2 | 67.314초 | 68.633초 | 70.957초 | 70.958초 |
| 기준 평균 | before-2/3 | 62.287초 | 64.829초 | 67.257초 | — |
| 최종 평균 | after-2/3 | 70.340초 | 71.596초 | 74.387초 | 74.388초 |
| 절감량 | 기준−최종 | **−8.053초(−12.93%)** | **−6.766초(−10.44%)** | **−7.130초(−10.60%)** | — |

서버의 정지 관측은 100ms 조회 간격의 운영 측정이다. `신청→서버 정지 추정`은
첫 POST 응답 소요 시간에 이 관측 구간을 더한 값이며, 정확한 서버 요청 시작 타이머가
아니다. 관측 간격·조회 처리·통신 오차를 포함한다. `신청→DOM`은 브라우저 실제 POST
호출에서 `관리 경기로 이동` 버튼이 활성화된 첫 DOM 관측까지다. before-2의 물리 클릭
시각은 수집하지 않았으므로 그 값으로 대체하지 않는다. 나머지 클릭과 POST 호출의 차이는
약 1ms였다. DOM 지표는 정지 안내와 행동 사용 가능 시점이며 모든 상세 패널 로딩의 완료를
뜻하지 않는다. 최종 정지 응답 수신 이후 DOM까지는 기준 0.987/1.853초, 최종 2.049/1.562초였다.

`before-1`은 수락 후 70.026초였으나 브라우저
완료 시각을 대기 제한으로 놓쳤다. 기준 코드/DB 사본의 `before-3`으로 그 표본을
대체했다. `after-1`은 두 첫 최적화의 중간 관측(수락 후 57.301초, 요청→사용 가능
DOM 60.567초)이며, 이후 발견된 읽기 잠금 교정 전 결과로 구분한다. 목표 수치를
맞추기 위한 반복은 하지 않는다. 5174 연결 시도는 기존 CORS로 거절돼 게임 시작
전에 5173으로 교정했다.

변경 전 `before-3`에서는 동시 소식함 GET 하나가 H2 40001로 실패했다. 본 진행은 완료됐지만
부가 읽기 부하가 완전히 같지는 않은 표본이다. 최종 두 서버 로그에는 해당 오류가 없었다.
표본은 전후 각 2회뿐이고 fresh JVM, JIT·해시 테이블 배치·호스트 부하 차이를 통제하지 못했다.
가장 빠른 중간 표본을 최종 결과로 선택하지 않았고 p95/SLA나 장기 처리량은 산출하지 않았다.

### 단계와 보조 경로

| 표본 | Auto WAITING→완료 반영 관측 | 첫 날짜 ADVANCE | 다음 무경기 날짜 ADVANCE | 나머지 coordinator 구간 | Calendar GET |
|---|---:|---:|---:|---:|---:|
| before-2 | 50.909초 | 1.432초 | 1.551초 | 14.058초 | 2,218.7ms |
| before-3 | 40.515초 | 1.017초 | 1.417초 | 13.675초 | 2,988.1ms |
| after-2 | 59.268초 | 1.730초 | 2.649초 | 9.717초 | 2,026.0ms |
| after-3 | 53.030초 | 1.421초 | 2.027초 | 10.836초 | 2,644.2ms |

Auto 구간이 기준 평균 45.712초로 가장 컸고 최종 56.149초로 늘었다. 이 구간에는 실제
Draft·시뮬레이션·receipt 반영과 외부 job 완료 관측이 모두 들어간다. 순수 엔진 CPU 시간으로
해석하지 않는다. 날짜 ADVANCE도 실제 일별 처리와 상태 관측을 포함하며 캐시된 재신청이 아니다.
나머지 coordinator 구간은 위 세 구간을 뺀 차이로, 순수 sleep만의 측정은 아니다. 평균
13.867→10.277초(3.590초/25.89% 감소)였지만 Auto 구간 증가를 상쇄하지 못했다.

Calendar 보조 GET은 같은 준비 저장에서 각 1회 실제 응답을 읽었다. 평균 2,603.4→2,335.1ms,
응답 본문은 모두 같은 19,701 UTF-8 bytes다. before-2의 최초 수집기는 문자열 길이 18,480을
기록했으므로 동일 본문의 UTF-8 길이로 보정했다. Resource Timing의 cross-origin transferSize=0을
실제 0 bytes로 해석하지 않는다. 작은 표본의 GET 차이도 별도 최적화 성과로 단정하지 않는다.

최종 JFR competition worker의 전체 표본은 기준 2,238/1,573, 최종 2,360/2,301이다.
그중 pool-health 프레임 포함 표본은 178/88→86/65였다. 반복 정의 조회를 줄였다는 근거는
있지만 해당 프레임 감소율이 전체 속도 개선율은 아니다. 실제 worker 활동 구간 길이는
49.765/38.071→56.469/52.421초였다. 같은 구간의 JFR machine CPU 평균은 약
37.0%/30.3%→38.2%/37.5%로 달랐다. 표본 상단에는 String hash, Map/Set 조회·구성이
계속 나타나지만 이 관측만으로 늘어난 시간의 원인을 특정하지 않는다. 남은 우선 조사 대상은
실제 Auto 단계의 Draft 역할 가능성 탐색 및 컬렉션 비용이다. 다음 작은 작업은 같은 제한된
입력에서 이 단계의 CPU/할당 비용을 분리하는 것이며 검색 폭·점수·경기 정책 조정은 별도다.

이번 실측 비용은 총 실제 BO3 6개/18세트/12일 정산, 수락 후 진행 시간 합계 392.581초다.
JVM·브라우저 시작/준비/증거 추출 및 사람의 조작 대기는 별도이며 테스트 실행 시간과 합산하지 않는다.

## 정확성 및 검증

작은 실제 BO3 전후의 **전체 완료 receipt JSON**이 일치했다. 여기에는 순서 있는
Draft 선택·역할, 각 game의 structured/simulator timeline hash, output hash,
Random draw count/trace hash, winner와 duration이 포함된다. 경기·Draft·개인상
JSON과 성장·생애 state JSON도 일치했다. 출전 관측 배열의 순서는 기존 별도 JVM
간에도 다르므로 player/team별 정렬로 의미를 비교하며 raw hash 일치로 보고하지 않는다.
같은 정렬을 출전 snapshot에만 적용하면 시장·재정 전체 상태도 일치한다.

비교는 before-2 대 before-3/after-2/after-3에서 수행했다. 원 receipt hash는
`eff8e881f007b10d481547bbc0e60bf4b145910cb3f12314bad4be53416ad012`,
입력 binding hash는 `fd98a6b320114e0a718ef02785da2739ef2adb387a0ec77635bbecdabc3f5cc1`다.
완료 receipt·출전 binding·출전 performance·Series 기록은 각각 1행, 날짜 명령 2행,
Draft 근거 3행, 공식 POG 3개/POM 1개였다. 분석 배지 포함 award 원장은 10행이다.
원본 receipt 전체를 비교했으며 별도 raw timeline 배열을 직접 나란히 비교했다는 주장은 하지 않는다.
실제 브라우저에서는 최종 관리 행동 활성화와 준비 표시 닫기를 확인했고, B의 일반 버튼 callback은
컴포넌트 verifier로 확인했다. 브라우저에서 수동 B 클릭으로 추가 BO3를 돌리지 않았다.

신규 테스트 클래스는 없다. 기존 storage 테스트의 실제 `AwardsStore.match` 출력과
작은 기간상으로 R1/R3/전체/다른 Career·연도를 확인한다. 기존 Draft 테스트는 희소
역할 풀, flex 중복 계수, 불가능한 역할, 완성된 다섯 역할과 double bit를 검사한다.
기존 Continuous 파일 DB 테스트에 실제 날짜 commit과 읽기 스냅샷의 동시 경계를
추가했다. 이 스냅샷에서도 손상된 directory hash는 거절한다. 기존 pause/원본 UUID
재개/child commit 후 복구/stale fence/손상 Career 격리 검사를 재사용한다.

집중 첫 실행 43건 중 1건 실패는 새 수상 비교 fixture의 고정 cutoff였다. rollback은
H2 sequence를 되돌리지 않아 R3 기록이 revision 2 뒤에 생성됐다. 최신 cutoff를
읽도록 교정한 뒤 해당 사례와 날짜 진행 사례 2건이 통과했다. 검증 대상을 제거하거나
기대 수상을 줄여 해결하지 않았다.

| 검증 | 실제 실행 결과 | wall time |
|---|---|---:|
| 첫 집중: RecordsStorage, Scouting, ContinuousPlanner/Recovery, DraftJointPool/Computation | 6 suites/43건, 42 통과·새 fixture 1 실패 | 157.53초 |
| cutoff 교정 + Continuous 날짜 재신청/충돌/pause | 2건 통과 | 38.01초 |
| 읽기 스냅샷 충돌 및 Scouting | 2 suites/5건 통과 | 69.79초 |
| 손상 hash 거절 추가 + Inbox 읽기/계약/lease 독립 | 2건 통과 | 41.07초 |
| 최종 frontend career:verify | 123 PASS, 기존 검사에 2개 시나리오 그룹 추가 | 별도 wall 미계측 |
| 최종 frontend build | TypeScript/Vite 성공 | Vite 15.09초 |
| 계획 backend 전체 회귀 1회 | 284 suites/2,248건, 2,246 통과·실패/오류 0·기존 skip 2 | **1,391.76초(23분 11.76초)** |

집중 실행은 총 4회/52건 시도, wall 합계 306.40초이며 처음 실패와 수정 후 통과를 합쳐
모두 최초 성공처럼 표기하지 않는다. 3차 실행에 지정한 존재하지 않는 단일 method 필터는
실행 건수에 포함하지 않았다. 실제 Inbox 계약/lease 경계는 4차의 올바른 기존 method로 확인했다.
프런트의 첫 명령은 루트에서 실행해 package.json 없음으로 실패했고 `--prefix frontend`로
교정했다. 성공한 career:verify/build는 구현 중 각각 3회이며 build는 16.14/14.91/15.09초였다.
위 표는 최종 소스 결과다. 공유 Series/
Player Draft UI를 변경하지 않아 해당 전용 verifier는 추가 실행하지 않았다.

집중 명령은 아래 selector들을 각 행의 한 호출에 전달했다. 패키지는 Career의 경우
`com.lolfm.career`, Draft의 경우 `com.lolfm.draft`다.

1. `CareerRecordsStorageTest`, `CareerScoutingTest`, `CareerContinuousPlannerTest`,
   `CareerContinuousRecoveryTest`, `DraftAvailabilityJointPoolTest`, `DraftComputationContextTest`.
2. `CareerRecordsStorageTest.canonicalFactsAwardsAndFiveSetTotalsAreAtomicImmutableAndSurviveFileReopen`,
   `CareerContinuousExecutionTest.dailyTargetReplayConflictPauseAndManualBusy`.
3. `CareerContinuousRecoveryTest`, `CareerScoutingTest`.
4. `CareerContinuousRecoveryTest.readSnapshotDoesNotUpgradeCalendarLockAfterAnActualDateCommit`,
   `CareerContinuousExecutionTest.inboxReadsDoNotResolveCounteroffersOrAlterTheContinuousLease`.

원본 집중 결과는 `/tmp/career-speed/focused-original/`, `contention-original/` 및
`focused.log`, `focused-correction.log`, `read-contention-focused.log`, `contention-boundary.log`에
보존했다. 전체는 같은 출력에서 위 검사를 동시에 돌리지 않고 default `test` 선언을 사용한다
(class worker 2개, 각 1,536MB, 기존 diagnostic tag 제외). 테스트 제외나 skip을 추가하지 않았다.

전체 원본 로그·284개 XML·요약은 `/tmp/career-speed/full-original/`에 보존했다.
기존 skip은 `PlayerDraftLatencyProfilingV1DiagnosticTest.captureOfficialPlayerDraftInteractiveAndSimulationLatencyProfile`
및 `PlayerDraftPerformanceHardeningV1DiagnosticTest.capturePairedBackendProbe`다. 일반 전체 회귀의
`CareerContinuousExecutionTest.realAutoBo3PausesAfterAppliedReceiptThenStopsAtPlayerAndAllowsEntry`와
`LeagueAutomatedSeriesRunnerProductionV9Test.frozenFullAutoFixtureUsesActualProductionAutoDraftAndV9WithDiagnosticsParity`
도 통과했다. 따라서 실제 Auto 완료 후 pause/관리 경기 경계 및 진단 ON/OFF의 기존 정확성 계약을
이번 전체 결과에 포함한다. 성능 측정의 별도 브라우저에서 pause/복구까지 실행했다고 주장하지 않는다.
전체 실행 이후 제품 코드 변경·후속 집중 실행·추가 전체 실행은 없다.

## 재현

최소 도구는 `backend/scripts/CareerPlaySpeedProbe.java` 하나다. production/test classpath에
자동 포함하지 않는다. 현재 backend를 `classes`로 컴파일한 후, 그 runtime classpath와
별도 출력 디렉터리를 사용한다. 이 환경의 예:

```bash
speed_cp='/tmp/lolfm-backend-1000-af663e77399dba7d/build/classes/java/main:/tmp/lolfm-backend-1000-af663e77399dba7d/build/resources/main:/tmp/career-overseas-runtime-libs/*'
/tmp/career-development-jdk/bin/javac -cp "$speed_cp" -d /tmp/career-speed/probe backend/scripts/CareerPlaySpeedProbe.java
/tmp/career-development-jdk/bin/java -Xmx1g -cp "/tmp/career-speed/probe:$speed_cp" com.lolfm.career.CareerPlaySpeedProbe prepare /tmp/career-speed --spring.datasource.url='jdbc:h2:file:/tmp/career-speed/seed;DB_CLOSE_ON_EXIT=FALSE' --lolmanager.runtime-dir=/tmp/career-speed/runtime
# prepare가 종료해 DB가 닫힌 뒤 각 표본용 디렉터리로 seed.mv.db를 복사한다.
/tmp/career-development-jdk/bin/java -Xmx1g -cp "/tmp/career-speed/probe:$speed_cp" com.lolfm.career.CareerPlaySpeedProbe serve /tmp/career-speed/run --server.port=8089 --spring.datasource.url='jdbc:h2:file:/tmp/career-speed/run/game;DB_CLOSE_ON_EXIT=FALSE' --lolmanager.runtime-dir=/tmp/career-speed/runtime
```

브라우저에서 해당 저장을 선택해 준비 동선을 거친 뒤 연속 진행을 시작한다. 도구는
`transitions.csv`, `final.json`, `sql.json`, `results.json`을 쓴다. 서버를 종료한 뒤
`evidence <출력 폴더> 'jdbc:h2:file:<DB 경로>;ACCESS_MODE_DATA=r'`로 작은 표본의
receipt/저장 의미를 비교할 원문을 추출한다. 큰 DB·JFR·로그는 `/tmp/career-speed/`에만 둔다.
변경 전 클래스/resources와 프런트는 이 경로 아래 별도 복사본으로 보존했으며 사용자
working tree를 checkout/reset/stash 하지 않았다.

정상 검증 명령은 `npm --prefix frontend run career:verify`, `npm --prefix frontend run build`,
`JAVA_HOME=/tmp/career-development-jdk bash backend/scripts/test-linux.sh --tests '<영향 테스트>'`다.
전체 회귀는 성능 측정이 모두 끝난 뒤 아래 명령으로 한 번 실행했다.

```bash
JAVA_HOME=/tmp/career-development-jdk /usr/bin/time -p bash backend/scripts/test-linux.sh
```

이번 범위의 `git diff --check`가 통과했다. 측정용 Java 서버, Vite와 Playwright 브라우저는
종료했으며 격리 DB/JFR/원본 로그는 `/tmp/career-speed/`에 재검토 증거로 남겼다.
다른 작업의 프로세스나 사용자 DB는 정리 대상으로 삼지 않았다. 커밋·push·배포는 하지 않았다.
