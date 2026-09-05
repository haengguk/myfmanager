# Backend 회귀 테스트 비용 점검 — 2026-09-05

검토 기준: 현재 HEAD `1daa0c6`의 backend는 `aa9a0247`과 동일하다. 기존 `backend/build/test-results/test/TEST-*.xml` 263개와 실행 로그, 느린 클래스의 테스트·probe 구현, Gradle 설정을 읽었다. 전체 회귀·진단·성능 벤치마크를 새로 실행하지 않았다. 이 문서는 제안이며 테스트·Gradle·AGENTS 정책을 변경하지 않는다.

## 측정된 비용

- 기존 전체 실행: 2,011 tests, failures/errors 0, skipped 2, Gradle 36분 51초.
- 클래스 시간 합계: 2,151.751초(35분 51.8초). Gradle 전체 시간과 약 59.2초 차이. 설정 캐시·daemon 개선만으로 본체의 35분을 해결할 수 없다.
- 느린 상위 10개 클래스: 18분 20.5초, 클래스 시간의 51.1%.
- 상위 20개 클래스: 26분 56.4초, 75.1%.
- 클래스 시간이 1초 미만인 200개 클래스: 1,402 tests를 합쳐 34.936초. 테스트 개수를 일괄 줄이는 것은 효과가 작다.
- 클래스 시간에는 Spring 초기화와 `@BeforeAll`도 포함된다. 클래스 시간에서 메서드 시간 합을 뺀 값을 전부 Spring 비용으로 해석해서는 안 된다.

| 클래스 | 기존 실행 초 | 테스트 수 |
| --- | ---: | ---: |
| LeaguePlayerSeriesHandoffCrossJvmDeterminismTest | 156.994 | 1 |
| LeagueAutomatedSeriesRunnerCrossJvmDeterminismTest | 142.283 | 1 |
| MatchEngineV1CrossJvmDeterminismTest | 139.781 | 1 |
| CareerDomesticExecutionTest | 118.091 | 1 |
| AutoDraftVarietyV1ProductionIntegrationTest | 116.908 | 5 |
| TeamPlayerInformationCrossJvmTest | 103.295 | 1 |
| Phase13GA2StructuralIntegratedAuditTest | 87.007 | 50 |
| LeagueApiV1BackgroundExecutionIntegrationTest | 82.933 | 1 |
| PlayerControlledDraftEngineTest | 77.434 | 7 |
| Phase13GAStructuralIntegratedAuditTest | 75.757 | 22 |

## 우선 적용할 실행 비용 개선

### 1. 새 JVM 검증의 불필요한 서버 초기화 축소

네 CrossJvm 클래스가 합계 542.353초(9분 2.4초, 25.2%)다. 각각 새 JVM 두 개를 순차 실행하고, 자식마다 `SpringApplicationBuilder(LolfmApplication.class)`로 전체 애플리케이션을 시작한다.

가장 명확한 대상은 `backend/src/test/java/com/lolfm/reference/TeamPlayerInformationCrossJvmProbe.java:24`다. JSON 네 개의 안정성을 확인하는데 DB·League·Career 등을 포함한 전체 애플리케이션을 두 번 시작한다. catalog와 응답 mapper, 실제와 같은 Jackson 설정만 구성하는 probe로 바꾸고, 기존 두 프로세스·네 payload의 byte 비교는 보존할 수 있다. catalog 단위 테스트 8건은 현재 합계 0.778초지만 이를 새 probe의 확정 소요 시간으로 사용하지는 않는다.

나머지 세 CrossJvm 검증은 Match 출력, Auto receipt, Player binding/receipt라는 서로 다른 경계를 검증한다. 느리다는 이유로 삭제하지 않는다. 필요한 bean만 구성하거나 대표 실행 경로의 공통 초기화를 통합할 수 있는지 검토하되, fresh-process 차이·Player/Auto 구분·동일 seed 재실행을 유지한다. 네 테스트의 9분 전체가 제거 가능한 시간이라는 뜻은 아니다.

### 2. GA/GA2에 복제된 현행 엔진 검증 통합

`backend/src/test/java/com/lolfm/draft/Phase13GAStructuralIntegratedAuditTest.java`와 `Phase13GA2StructuralIntegratedAuditTest.java`에는 공백을 제외한 본문이 같은 테스트 메서드 20개가 있다. 두 클래스는 같은 resource, SyntheticContextFactory, DraftEngine을 사용한다. 다만 schedule 자체는 서로 다른 버전이므로 본문이 같다는 이유만으로 전부 삭제해서는 안 된다.

확정적인 실행 중복은 다음 두 메서드다.

- `hardFearlessNeverReusesPriorCompletedPick`: 양쪽 모두 neutral/high-baseline으로 5세트 실행. GA 24.949초, GA2 26.224초.
- `allFiveFearlessGamesRemainCompletable`: 양쪽 모두 flex-wide/flex-narrow로 5세트 실행. GA 26.617초, GA2 28.033초.

각 시나리오를 한곳에서 유지하면 GA 쪽 반복 작업 51.566초를 제거한다. 나머지 공통 draft 법칙도 한곳으로 모으고 과거 버전의 schedule·산출물 고유 계약은 남긴다. 20개 동일 본문 메서드의 GA 쪽 시간은 69.395초이며, 이것은 전부 삭제 가능한 시간으로 확정한 값이 아니다.

### 3. 테스트 목적에 맞는 초기화와 Spring context 재사용

`CareerDomesticExecutionTest`는 클래스 118.091초 중 메서드 46.258초, `LeagueApiV1TransportIntegrationTest`는 28.843초 중 메서드 합 0.866초다. 반면 `RealDraftMatchOrchestratorTest`의 메서드 외 43.419초에는 `@BeforeAll`에서 실행하는 실제 경기 다섯 번이 포함된다. 초기화와 경기 생성을 각각 줄여야 한다.

DB·worker가 필요 없는 응답/validation 검증은 서비스 직접 구성 또는 작은 Spring context를 사용한다. 실제 DB, job/lease/fence, 트랜잭션, Player 완료, 압축 transport를 검증하는 대표 테스트는 유지한다. 같은 환경을 쓰는 Spring 테스트의 properties·mock 선언은 의도적으로 통일하되, background on/off 같은 의미 있는 차이나 DB 상태 격리를 제거하지 않는다.

[Spring 공식 문서](https://docs.spring.io/spring-framework/reference/testing/testcontext-framework/ctx-management/caching.html)에 따르면 context 구성·properties·bean override 등이 캐시 키를 구성하고 캐시는 프로세스 안에서만 공유된다. 현재 전체의 cache miss 수와 각 bean 초기화 시간은 별도 측정하지 않았다.

### 4. 제한적인 테스트 프로세스 병렬 실행

`backend/build.gradle:33`의 기본 test에는 parallel fork 설정이 없다. [Gradle 기본값](https://docs.gradle.org/current/userguide/java_testing.html#sec:test_execution)은 `maxParallelForks=1`이다. 읽은 환경은 논리 CPU 12개, 메모리 약 7.2GiB이고 기본 test heap 상한은 프로세스당 2GiB다. CrossJvm 자식 heap도 별도로 필요하다.

출력 경로·DB·임시 파일 격리 후 최대 2개 프로세스부터 검토한다. 메서드 단위 병렬 실행이나 4~12 forks를 즉시 켜지 않는다. Spring 캐시 중복·메모리·자식 JVM·드래프트 내부 동시 실행 때문에 정확히 절반으로 줄어든다고 보장할 수 없다. `--parallel`만 추가하는 것은 이 설정을 대신하지 않는다.

### 5. 실행하지 않을 진단을 Spring 초기화 전에 제외

`backend/src/test/java/com/lolfm/application/PlayerDraftLatencyProfilingV1DiagnosticTest.java:39`는 메서드 안의 `Assumptions.assumeTrue`로 환경변수를 검사한다. 따라서 Spring 의존성 주입이 먼저 일어나며, 이번 실행에서는 26.296초를 쓰고 skip됐다. `PlayerDraftPerformanceHardeningV1DiagnosticTest`도 같은 방식이며 이번에는 context를 재사용해 0.034초였다.

기존 opt-in 조건을 클래스 수준에서 먼저 검사하거나, `diagnostic` tag와 명시적인 실행 task로 옮긴다. 현재도 기본 실행에서 검증하지 않는 항목이므로 검증 범위를 줄이는 변경은 아니다. 다만 해당 context를 다음 테스트가 필요로 하면 초기화 비용이 이동할 수 있어 26초 전부를 순절감으로 보장하지 않는다.

## 기본 검증 범위를 바꾸는 별도 선택안

- `AutoDraftVarietyV1ProductionIntegrationTest.fixedSeedSetReachesMoreThanOneHighQualityDraftWithoutChangingSearchBounds`는 8개 seed의 실제 draft를 실행해 61.214초를 쓴다. selector의 경계·가중치·재현성 테스트 10건은 0.009초다. 다양성 관찰은 명시적인 draft 진단으로 옮기고 기본 실행에는 대표 production 합법성·재현성·Fearless 통합을 남길 수 있다. 이는 현재 기본 검증 범위 변경으로 분류한다.
- `AutoDraftObservationHarnessV1Test` 50.425초, `PlayerDraftLatencyProfilingV1HarnessTest` 35.967초, `RealMatchPerformanceBaselineV1HarnessTest` 19.556초는 테스트용 관찰·성능 도구의 정확성도 실제 draft/경기로 검증한다. 도구와 관련 production 경로 변경 시의 별도 검증 대상으로 분리할 수 있다. 진단 비간섭은 AGENTS의 중요한 불변식이므로 기본 검증에서 필요한 대표 parity를 제거하지 않는다.
- 빠른 기본 test와 기존 전체를 포괄하는 fullRegression 명령을 분리하면 일상 대기는 줄지만 전체 실행 자체가 빨라지는 것은 아니다. 이 안을 선택하면 AGENTS/스킬의 최종 full regression 정의도 함께 맞춰야 하며, 일부만 실행하고 전체 통과라고 기록해서는 안 된다.

## 권장 순서와 예상의 한계

먼저 검증 범위를 유지하는 1~5를 묶어 개선한다. 동일 시나리오의 중복 검증 통합, catalog probe 축소, 불필요한 초기화 제거, 마지막으로 2 forks 순서다. 새 검증 프레임워크나 성능 감사 파이프라인은 만들 필요가 없다.

기존 36분 51초를 20분 안팎으로 줄이는 것을 1차 목표로 삼을 수 있으나 아직 측정 결과가 아니다. 확실히 확인한 것은 실행 중복과 비용 집중이다. 적용 후 변경한 테스트만 집중 확인하고, Gradle/공유 fixture까지 최종 정리한 다음 전체 회귀 한 번으로 누락·격리·최종 시간을 확인한다.

빠른 도메인 테스트, Random 비소비·same-seed·중복 보상 방지, durable job/lease/fence, DB 재시작, 결과 중복 반영 방지는 유지한다. 읽기 쉬운 assertion 몇 줄이나 빠른 테스트 수백 개 삭제를 우선하지 않는다.
