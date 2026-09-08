# Career 시작 복구 격리 및 백엔드 회귀 비용 최적화 V2

기준 HEAD `53632d224c148f916a9e25d3e7f283de3a43091a`, 브랜치 `main`에서 구현했다. 커밋·push·배포 및 사용자 DB 변경은 하지 않는다. 연속 진행 기능은 이 작업 범위가 아니다.

## 저장 시작 복구

지원되는 directory 형식이더라도 현재 운영 명부가 없으면 국제 참가자 등록에서 `CareerMarketStore.engine`이 null 명부를 역참조했다. 실제 `CareerPersistenceStartupRecovery.recover()`를 호출하는 기존 Spring/H2 통합 클래스에 정상 저장과 누락 저장을 함께 구성해 재현했다. Cup 결과는 통제 fixture와 일치하는 hash로 준비했으며 실제 사용자 파일이나 cold-start 서버를 손상시키지 않았다.

`CareerSaveCompatibility.recoverySupported`는 초기화 전 legacy 상태와 운영 중인 저장의 필수 자료 유실을 구분한다. 알려진 DATA_MISSING / VERSION_UNSUPPORTED / ORGANIZATION_UNSUPPORTED만 해당 Career의 복구를 건너뛴다. 이 경계를 국제 정산, roster, market, development, lifecycle, CL, overseas 복구에 적용했다. Calendar 초기화 순서와 기존 Career 잠금을 유지한다. 전역 캐시나 영속적인 미지원 목록은 없다.

재현 검사는 정상 Career의 누락된 성장 상태가 실제로 복구되고 조회·하루 진행까지 되는 것을 확인한다. 문제 저장의 typed 오류와 UNSUPPORTED 목록 표시, 명부 미재생성, directory·시장·성장·lifecycle·대회·생성 receipt 보존도 검사한다. directory hash 손상은 계속 무결성 예외로 전파된다. 기존 정상 legacy 복구와 정책 identity 손상 거절 사례를 함께 실행했다.

## 줄인 실행 작업과 유지한 계약

아래는 최종 구현이다. API/G1의 실제 날짜 이동은 전체 이후 Jackson 날짜 형식 보정에서 완성했으므로, 아래 최종 준비 축소를 전부 24분 10.63초 측정에 귀속시키지 않는다.

| 대상 | 준비 변경 | 실제로 유지하는 검증 |
| --- | --- | --- |
| Career API | 두 Career의 8월→1월 반복 진행 대신 미사용 시장·성장 운영 구간의 날짜를 함께 옮겨 경기 전날 상태 준비 | 실제 생성, 최초 8월 날짜 전이, UUID replay/conflict, revision, 목록/상세/격리, 경기 당일 실제 진행, Player/Auto API |
| 두 시즌 스토브 | 첫 시장 초기화를 12월의 짧은 구간에서 시작. 이월 후 두 번째 시즌의 마지막 운영 구간 준비 | 두 번의 실제 전환, Bo 협상·소속·선발, 계약 이월, 56개 구단 연도별 승인, AI 제안, 성장 이월, 과거 snapshot 보존, 원래 전환 receipt replay, 지연 전환 날짜 역행 방지 |
| 해외 등록 복구 G1 | 7개월 시장 진행 대신 계약 기간·지급/협상 간격을 보존하여 등록 전날 운영 상태 준비 | 실제 하루 정산 후 결원 발생, 적법한 FA 제안·결정·명부 복구·등록, rollback, 중복 적용 방지, 독립 경기 gate |
| A→B 저장 호환 | 기존 12월 파일 DB 시나리오 유지 | 실제 파일 DB 닫기/재열기, A 상태 보존/B 새 저장 적용, 실제 다음 경기 handoff, 시즌 이월, hash 손상 거절 |

준비용 `CareerOperatingDateFixture`는 Calendar transaction 안에서 날짜와 event cursor, revision, command receipt 및 운영 상태 hash를 함께 맞춘다. 단순 날짜 컬럼 수정이나 완료 예상 결과 주입을 실제 실행의 증거로 사용하지 않는다. 준비에서 생략한 성장·AI 활동을 시뮬레이션했다고 주장하지 않는다. 이월 fixture는 첫 전환에서 열린 협상을 실제 일자 정산으로 마무리한다. 기존 계약/금액/성장/receipt를 보존하고, 실제 급여 및 재정 월 경계를 정산하며, 미처리 계약 경계를 건너뛸 수 없도록 검사한다. API/G1 fixture는 아직 사용자 운영 명령이 없는 상태에서 시장·성장 snapshot의 날짜를 같은 간격으로 이동한다. 계약 기간, 상대 지급/협상 기한, 가격·자금·소속을 보존하고 source directory와 기존 command receipt는 수정하지 않는다.

일별/기간별 급여 합산과 연말 계약 활성화·선발 보존은 `CareerMarketEngineTest.salaryRoundingIsIdenticalAcrossAdjacentRanges`, `reservedRenewalStartsAfterExpiryWithoutLosingTheManagedSelectionOrDoublePaying`, `boundedFiftySixClubCycleAndDailyVersusJumpRecoveryAgree`에 남는다. 성장의 일별 예산·잔여값·월/연도·재시작 경계는 기존 CareerDevelopmentPolicyTest가 담당한다. 기존 고유 assertion이나 correctness suite를 제거하지 않는다.

## 성장 명부의 불변 투영 재사용

Linux 출력의 파일 DB 집중 실행에서도 316.56초 경과/주 스레드 CPU 282.71초인 표본에서 `completeInternationalPhase → bindFixture → registeredPair → directory → Starter`의 전체 숙련도 복사/중복 검사가 관측되었다. 이 표본만으로 함수별 CPU 비율을 산출하지는 않는다.

`CareerDevelopmentEngine.directory()`는 해당 작업의 base definition과 현재 정수 능력치·숙련도가 모두 같을 때 이미 검증된 불변 definition을 재사용한다. 소수 단위 성장값은 state에 그대로 남고 정수 경계를 넘거나 새 챔피언 숙련도가 생기면 기존 구성 경로로 즉시 갱신한다. ID/닉네임/포지션 및 항목 수도 같아야 한다. source hash 검증, 초기 state 검증, 변경된 profile의 기존 검증은 생략하지 않는다. 전역/다른 Career 캐시, 날짜 정산 순서나 Random 변경은 없다.

기존 `CareerDevelopmentPolicyTest`에 소수 누적→정수 상승/신규 숙련도→직렬화 재시작과 원본 불변성을 확인하는 작은 사례 1개를 추가했다. 실제 플레이의 HTTP/진행 시간을 별도 측정한 것은 아니므로 테스트 전체 개선율을 플레이 속도로 환산하지 않는다.

## 실행 경로

원본은 WSL의 Windows `/mnt/c`(9p), Linux 출력은 `/tmp`(ext4)다. 12 logical CPU, 메모리 약 7.2GiB, Temurin 21.0.12.1 / Gradle 9.5.1에서 확인했다. 기본 최대 2 worker, 각 heap 1,536MiB, 클래스 수준 병렬은 그대로다. 독립 JVM 자식 프로세스가 있어 worker를 더 늘리지 않았다.

```bash
cd /mnt/c/users/guddn/dev/lolmanager/backend
JAVA_HOME=/tmp/career-development-jdk /usr/bin/time -p bash scripts/test-linux.sh
# 필요한 focused 검사만 선택할 때
JAVA_HOME=/tmp/career-development-jdk bash scripts/test-linux.sh \
  --tests 'fully.qualified.TestClass.method'
```

설치된 JDK 21의 경로로 `JAVA_HOME`을 지정하면 된다. 스크립트는 현재 checkout을 읽고 출력만 `/tmp/lolfm-backend-<uid>-<checkout-key>/build`에 둔다. `LOLFM_TEST_BUILD_DIR=/absolute/ext4/path`로 변경할 수 있다. 소스 복사/동기화가 없어 미커밋·새 파일도 Gradle 입력에 포함된다. 동일 출력의 스크립트 실행은 파일 잠금으로 겹치지 않는다. 기존 상대 resource 및 `build/reports` artifact 경로를 위해 작업 디렉터리는 원래 backend로 유지한다. Gradle XML/HTML 경로는 선택한 build 디렉터리다.

실제 실행은 `./gradlew --project-cache-dir <output>/.gradle-project-cache test --rerun --no-build-cache --console=plain --no-daemon -PbackendBuildDir=<output>`다. `test`만 강제 재실행하고 compile/resources의 유효한 증분 결과는 재사용한다. 기존 diagnostic 제외와 skip은 유지하며 failFast는 사용하지 않는다. 이 옵션은 개발용 선택 경로이며 기존 `./gradlew test`도 유지한다. 새 출력 경로의 첫 사용에는 컴파일/리소스 준비 비용이 한 번 발생한다. 이번 전체 측정은 앞선 focused에서 만든 유효한 출력을 재사용했으며 test 자체는 재실행했다.

## 측정 결과

계획된 전체는 **1회**, `/usr/bin/time -p` real **1,450.63초(24분 10.63초)**로 완료했다. Gradle은 24분 10초를 표시했다. **274 classes / 2,164 tests / 2,162 통과 / 실패 0 / 오류 0 / 기존 skip 2**다. 두 skip의 클래스/메서드도 과거 결과와 동일하다. 기존 테스트를 제거하지 않고 복구 재현과 성장 투영 경계 2건을 추가했다.

- 절감: `2,653 - 1,450.63 = 1,202.37초` = **20분 2.37초**.
- 비율: `(2,653 - 1,450.63) / 2,653 × 100 = 45.32%`.
- **50%/22분 6초 목표 미달**(명시 시간 기준 124.63초 초과), **20분 권장 목표 미달**(250.63초 초과).
- class time 합계: **2,762.439초**. 전체 wall이나 CPU가 아니다. 이번 time 출력의 user 4,577.69초 / sys 114.40초와도 구분한다.
- 원본 전체 로그·XML·HTML·환경·집계: `/tmp/career-opt-v2-evidence/full/`. 실행 시작 HEAD는 기준 HEAD와 같았으며 다른 Java 실행은 없었다. 메모리 available 약 5.5GiB에서 시작했다.

| XML 대상 | 과거 full (초) | 이번 full (초) |
| --- | ---: | ---: |
| CareerModePersistenceTest 클래스 | 1,187.306 | 638.762 |
| CareerApiV1ControllerTest 클래스 | 330.812 | 133.462 |
| CareerOverseasExecutionTest 클래스 | 303.911 | 260.884 |
| 두 시즌 stove 메서드 | 675.582 | 239.626 |
| 파일 DB A→B 메서드 | 198.217 | 180.750 |
| API create/list/replay 메서드 | 287.274 | 126.940 |
| 해외 등록 G1 메서드 | 162.872 | 158.618 |
| PlayerSeriesHandoff CrossJvm 클래스 | 194.622 | 63.515 |
| AutomatedSeriesRunner CrossJvm 클래스 | 188.962 | 42.118 |
| MatchEngineV1 CrossJvm 클래스 | 163.532 | 38.704 |

이번 신규 시작 복구 사례는 full XML 20.985초였다. 해외 클래스에는 이 신규 검사가 포함되므로 과거 클래스와 건수가 다르다. G1은 과거 full 이후 현재 기준 HEAD에 추가한 gate 검증도 포함한다. API/G1의 새 UUID 및 환경 부하 차이도 있어 각 변경의 독립 기여율로 해석하지 않는다.

남은 가장 큰 클래스는 Persistence 638.762초, OverseasExecution 260.884초, 실제 AutomatedSeriesRunnerProductionV9 207.754초다. 끝 무렵에는 단일 worker의 실제 `LeaguePlayerSeriesHandoffProductionV9Test`에서 `DraftAvailability.computePoolHealth → ShallowDraftSearch → 실제 Draft/Series` CPU 계산이 관측되었다. 이 클래스 전체는 34.024초이므로 표본 하나를 전체 병목 비중으로 확대하지 않는다. 저장 명부/대회 binding 구성과 실제 Draft 검색의 남은 CPU 작업을 더 줄여야 하며, 실제 엔진을 가짜 결과로 대체하지 않았다. worker RSS가 약 2.2GiB까지 올라 추가 worker와 자식 JVM을 함께 수용할 메모리는 별도 검증이 필요하다. WSL 설정은 변경하지 않았다.

정확성 검증과 성능 목표는 별도다. 전체 통과와 실행 비용 개선은 확인했으나 요청한 최소 절반 단축은 달성하지 못했다. 목표 미달만으로 전체를 추가 실행하지 않았다.

기존 44분 13초(2,653초)는 부모 HEAD 작업 중의 전체 결과이며 현재 시작 HEAD의 clean baseline이 아니다. 274 classes/2,162 tests, 실패 1, 기존 skip 2였고 후속 수정이 현재 HEAD에 포함되어 있다. XML class time 합계 4,580.292초는 두 worker의 겹치는 실행 시간 합계이며 전체 wall/CPU 시간이 아니다. 따라서 변경별 독립 기여율을 이 전후 수치만으로 산출하지 않는다.

9p focused 실행 중 단일 스택 표본에서 test worker가 82.08초 경과/주 스레드 CPU 13.64초에 Mockito 초기화의 `UnixFileSystem.getBooleanAttributes0 → File.exists → URLClassPath`에서 관측되었다. classpath 파일 조회 지연의 직접 관측이며 전체 I/O 비중 측정은 아니다. 실행 도중 다른 작업의 MatchDraftReviewProbe JVM도 관측되었고 중지하지 않았다. 최종 전체 시점의 부하는 별도로 기록한다.

## 집중 실행 기록

각 단계는 `test --tests`이며 전체 회귀가 아니다. 시간은 초기 3회는 Gradle 표시 wall, 이후는 `/usr/bin/time -p`의 real이다. 모든 focused 실행의 errors/skip은 0이다.

| 단계 | 선택 | tests / 실패 | 시간 |
| --- | --- | --- | --- |
| 원인 재현 | OverseasExecution의 새 startupRecovery 사례 | 1 / 1 (기존 NPE) | 2분 40초 |
| 복구 수정 | 위 사례 + Persistence의 expandedRosterMigration / provenLegacyCompetition | 3 / 0 | 3분 12초 |
| 준비 변경 첫 검사, 9p | API createListGet… / stoveTwoCycles / Overseas registration… | 3 / 3 | 3분 17초 |
| Linux 첫 출력 | 위 3개 + MatchEngineV1CrossJvmDeterminismTest | 4 / 3 | 242.02초 |
| 날짜 준비 보정 | API / stove / Overseas registration / 파일 A→B | 4 / 1 | 462.41초 |
| 성장 재사용 및 복구 호출자 | CareerDevelopmentPolicyTest 전체 + startupRecovery + Persistence의 stove / 파일 A→B / expandedRosterMigration / provenLegacyCompetition | 22 / 1 | 345.86초 |
| 완료된 거래 기록 구분 후 | stoveTwoCycles만 | 1 / 0 | 217.23초 |

준비 변경 중의 실패는 신규 fixture의 해외 확장 팀 재정 초기화, 아직 고정 명부가 없는 legacy 복구 경계, 계약 만료일 및 완료된 거래 기록의 과도한 거절이었다. 기대값·계약 기간·예산을 바꾸어 실패를 숨기지 않고 준비 경계를 수정했다. Linux 첫 출력에서 독립 JVM 검사는 통과(37.219초)했다. 날짜 준비 보정에서 API 130.073초, 해외 등록 158.823초, 파일 A→B 156.930초가 통과했다. 성장 재사용 뒤 파일 A→B는 181.630초로 통과했다. 이 차이로 해당 코드 변경의 독립적인 속도 향상을 주장하지 않는다.

Focused 원본 XML은 `/tmp/career-opt-v2-evidence/{recovery-before,recovery-after,preparation-first,linux-first,preparation-fixed,final-focused}/`에 보존했다. 마지막 stove 집중은 1건 통과, real 217.23초/XML 201.375초였다. `/tmp/career-opt-v2-evidence/stove-final/`에 보존했다. 전체 전 focused 실행은 총 7회이며 관찰한 실패는 모두 관련 후속 focused에서 해결했다. 전체 결과는 위 측정 결과에 기록했다.

## 전체 이후 한정 보정

전체 실행 중 날짜 이동 fixture의 월별 요약 검토에서 월 bucket 재구성이 필요하다고 판단했다. 실행 중인 소스는 그대로 유지하고 전체 결과를 먼저 보존했다. 첫 후속 focused에서 API의 월별 요약 단언이 실패하면서 더 근본적인 준비 오류가 확인됐다. 저장 mapper는 LocalDate를 **배열**로 쓰는데 이동 함수가 문자열만 처리하여 시장·성장 날짜가 실제로 이동하지 않았다. 실제 API 날짜 진행은 이 밀린 수개월을 정상 정산했으므로 기존 API/등록 assertion은 통과했지만, 의도한 일별 준비 비용 축소는 full 시점에 완성되지 않았다.

최종 fixture는 typed state를 날짜 문자열 형태로 정규화해 이동한 뒤 원래 저장 형식으로 직렬화한다. 시장 processedThrough와 성장 nextSettlement가 목표 날짜와 같아야 준비가 성공한다. 보존된 첫 주의 일별 gain으로 이동 후 월별 key/월초/합계를 다시 구성하며, 실제 다음 날 처리 후 월초와 선수별 요약 중복을 검사한다. 처음부터 일별 이력이 전부 남아 있는 첫 운영 주에만 이 준비를 허용한다. **제품 코드와 authored 자료는 전체 이후 변경하지 않았다.** Fixture의 운영 날짜와 월별 요약을 교정했으며 능력치·급여·자금 값은 보존했다.

따라서 full의 45.32%는 Linux 출력, stove 기간 단축, 중복 조회 축소 및 불변 투영 재사용이 포함된 당시 tree의 관측값이다. 최종 API/G1 날짜 정규화의 추가 절감을 이 full 수치에 더하거나 50% 목표를 달성했다고 추정하지 않는다.

직접 호출자는 API create/list/replay 사례(첫 주 이력)와 해외 등록 G1(빈 초기 이력) 두 개뿐이다. 이 두 메서드의 focused 검증으로 영향 범위를 덮을 수 있으므로 두 번째 full은 필요하지 않다. 원본 full 통과와 이 후속 결과를 구분하며, 보정 후 최종 테스트 tree에 또 full을 실행했다고 표시하지 않는다.

후속 1차: 2건 중 1건 통과/1건 실패, errors/skip 0, real 168.61초. 원본 `/tmp/career-opt-v2-evidence/post-full-first/`. 날짜 형식 보정 후 같은 직접 호출자 **2건 모두 통과**, errors/skip 0, real **99.63초(1분 39.63초)**. 최종 XML 메서드 시간은 API **49.609초**, 해외 등록 G1 **77.914초**다. 원본은 `/tmp/career-opt-v2-evidence/post-full-final/`에 보존했다. 이 추가 절감은 focused 관측이며 final tree 전체 시간을 재측정하지 않았다.

전체 전 focused 7회 약 30분 16.52초, 전체 후 focused 2회 4분 28.24초로, **focused 총 9회 / 42건의 실행 시도 / 약 34분 44.76초**였다. 준비 경계 보정과 의도한 원인 재현의 중간 실패 10건도 기록에 포함하며 최종 미해결 실패는 없다. 계획된 full은 위 1회뿐이다. 프런트 변경, 프런트 build/verifier, 브라우저 및 별도 cold-start 서버 검사는 이번 범위에서 실행하지 않았다.

대표 최종 focused 명령(backend에서):

```bash
JAVA_HOME=/tmp/career-development-jdk bash scripts/test-linux.sh \
  --tests 'com.lolfm.controller.CareerApiV1ControllerTest.createListGetReplayConflictAndStrictErrorsPreserveLeagueState' \
  --tests 'com.lolfm.career.CareerOverseasExecutionTest.registrationWithoutFixturesRepairsThroughMarketDatesAndKeepsIndependentGates'
```

시작/최종 HEAD는 `53632d224c148f916a9e25d3e7f283de3a43091a`, 브랜치는 main이며 변경은 미커밋으로 남겼다. `prompts/`, `선수정보.zip` 및 다른 작업이 생성한 `match-engine-and-auto-draft-realism-review-2026-09-08.md`/동명 자료 폴더를 보존했다. commit·push·배포, 사용자 DB 초기화/수정, 원본 폴더 이동, WSL 재시작은 하지 않았다.
