# Backend 전체 회귀 실행 비용 개선 V1

## 범위와 비교 기준

2026-09-05, 시작 HEAD `1daa0c662c318066d52faf335c52ef284fa3b2f4`.
Production Java·리소스·게임 규칙·API 및 기존 diagnostic 제외 범위는 변경하지 않는다.
동시에 보이는 선수 사진/frontend/프롬프트 변경은 다른 작업 소유이며 이 변경에 포함하지 않는다.
시작 시 다른 backend 작업은 실행 중이 아니어서 기존 작업 위치를 사용했다.

기존 전체 XML 263개 / 2,011 tests / 실패 0 / 오류 0 / skip 2가 남아 있었고,
집중 실행 전에 `/tmp/backend-runtime-optimization/baseline-xml-summary.json`에 클래스별 수·시간·timestamp를 보존했다.
클래스 시간 합계 2,151.751초로 점검 보고서와 일치한다. 기존 Gradle wall time은 36분 51초(2,211초).
기준 측정만을 위한 전체 재실행은 하지 않았다. XML에는 실행 HEAD가 내장되어 있지 않으므로,
위 HEAD는 이번 시작·점검 기준이며 과거 실행 HEAD 자체를 XML로 증명하는 값은 아니다.
점검 보고서가 확인한 backend 내용은 `aa9a0247`과 같은 기준이다.

환경은 WSL Linux, 논리 CPU 12개, 메모리 7,346MiB, swap 2,048MiB,
Temurin 21.0.12.1(`/tmp/lolmanager-temurin21`), Gradle 9.5.1이다.
기존 worker heap 2GiB·1 fork를 1,536MiB·최대 2 forks로 바꾼다.
CrossJvm 자식은 기존대로 catalog/Match 512MiB, Auto/Player 768MiB,
metaspace 192/256MiB, SerialGC이며 각 부모 안에서는 두 자식을 순서대로 실행한다.
따라서 최대 두 worker와 두 자식이 공존한다. 메서드 병렬화·forkEvery 변경은 없다.
프로세스별 JVM/Spring 캐시와 JIT, 파일 캐시, concurrent 작업 및 부하가 달라질 수 있어
한 번의 전후 측정으로 각 변경의 순수 인과 효과를 따로 산정하지 않는다.
클래스 시간과 메서드 시간의 차이를 전부 Spring 초기화 비용으로 간주하지 않는다.

## 변경

- Catalog probe: 실제 catalog/응답 mapper 생성자와 Boot `JacksonAutoConfiguration`만 import한다.
  현재 production에 별도 Jackson customizer가 없고, 동일 test/application properties와 Boot 모듈 설정을 사용한다.
  fresh JVM 두 개, 실제 리소스 로딩, 네 JSON의 byte 비교, catalog hash/팀·선수 수 검증은 그대로다.
- Match/Auto/Player probe: 전체 production bean 정의와 실제 경로를 유지하면서 lazy initialization을 적용한다.
  receipt/Player transaction 등 필요한 그래프는 실제 생성되며, 무관한 eager bean 초기화를 줄인다.
  더 큰 bean graph 재구성이나 production 리팩터링은 하지 않았다.
- GA 공통 현행 엔진 검증 19개를 GA2에 통합했다. 같은 본문이라도 서로 다른 schedule을 검사하는
  `mirroredScheduleContainsBothSideOrientations`는 양쪽에 유지했다. GA 고유 schedule·resource 상수 계약도 남겼다.
- GA는 더 이상 정적 schedule/resource 계약 전에 실제 draft를 생성하지 않는다.
  GA2의 neutral draft는 같은 엔진·neutral/neutral context로 생성하는 기존 `auditSingle` 결과를 사용한다.
  해당 클래스 인스턴스에서 필요한 순간 한 번 만들며, 별도의 실제 replay와 두 가지 5세트 Fearless 시나리오는 유지한다.
  전역 cache나 테스트 클래스 간 gameplay 상태 공유는 추가하지 않았다.
- Career/RealDraft/Match 계약은 같은 NONE/lazy Spring 설정을 사용한다. Career의 DB는 기본 `${random.uuid}`
  메모리 DB이며 Career/command identity 독립성, 실제 Auto/Player/DB/checkpoint 흐름을 유지한다.
  RealDraft의 5회 실행은 series history·fresh replay·diagnostics off·baseline이라는 서로 다른 경계이므로 유지했다.
  Match 계약의 독립 engine replay와 orchestrated V1 parity 역시 유지했다.
- Transport는 RANDOM_PORT 실제 HTTP/gzip/CORS/DB 통합을 유지하고 bean을 지연 초기화한다.
  BackgroundAvailability는 실제 durable queue/replay와 mock submit(false/true) 경계를 유지한다.
  background enabled 실제 worker 테스트를 mock/off 설정에 합치지 않았다.
- 두 PlayerDraft 진단은 같은 환경변수/값을 `@EnabledIfEnvironmentVariable`로 클래스에서 검사한다.
  기존 명시적 실행 명령은 그대로 유효하다. 대형 opt-in 진단은 이번에 실행하지 않는다.
- 기본 `test`만 최대 2 workers로 설정하며 `-PtestForks=1` override를 제공한다.
  기존 기본 테스트/관찰 harness/CrossJvm을 제외하거나 별도 task로 이동하지 않았다.

파일 출력과 DB/포트 점검: CrossJvm 결과와 file-H2 restart는 기존 JUnit `@TempDir`,
Spring DB는 process-local 메모리 DB(기본 UUID), HTTP는 RANDOM_PORT를 쓴다.
기본 테스트의 기존 artifact 생성은 고유 임시 경로를 사용하며, 고정 대형 report 생성은 기존 opt-in이다.
working directory를 바꾸거나 각 worker가 같은 report를 덮어쓰는 새 경로는 도입하지 않았다.

## 중복 통합 대응표

아래는 GA에서 제거하고 GA2의 동명 메서드에 유지한 검증이다.
양쪽 setup의 `DraftResourceSet.loadDefault`, `Phase13GASyntheticContextFactory.create`,
`new DraftEngine(resources)`와 메서드별 context/seed/새 history 및 assertion이 같음을 비교했다.
GA/GA2 schedule 객체와 산출물 버전을 사용하는 검증은 이 통합에서 제외했다.

| GA에서 제거한 메서드 | 남은 검증 |
| --- | --- |
| `allFiveFearlessGamesRemainCompletable` | `Phase13GA2StructuralIntegratedAuditTest.allFiveFearlessGamesRemainCompletable` |
| `candidatePoolNeverBecomesEmpty` | `Phase13GA2StructuralIntegratedAuditTest.candidatePoolNeverBecomesEmpty` |
| `componentDistributionContainsNoNaNOrInfinity` | `Phase13GA2StructuralIntegratedAuditTest.componentDistributionContainsNoNaNOrInfinity` |
| `currentImpossibleFlexRoleDoesNotAffectIntegratedDecision` | `Phase13GA2StructuralIntegratedAuditTest.currentImpossibleFlexRoleDoesNotAffectIntegratedDecision` |
| `endToEndDraftAssignmentsReachMatchSimulator` | `Phase13GA2StructuralIntegratedAuditTest.endToEndDraftAssignmentsReachMatchSimulator` |
| `everyFinalDraftHasFiveUniqueLegalRolesPerTeam` | `Phase13GA2StructuralIntegratedAuditTest.everyFinalDraftHasFiveUniqueLegalRolesPerTeam` |
| `everyGameOneDraftCompletesTwentyActions` | `Phase13GA2StructuralIntegratedAuditTest.everyGameOneDraftCompletesTwentyActions` |
| `exactDraftReplayIsDeterministic` | `Phase13GA2StructuralIntegratedAuditTest.exactDraftReplayIsDeterministic` |
| `finalMatchAssignmentsAreExplicitAndStructured` | `Phase13GA2StructuralIntegratedAuditTest.finalMatchAssignmentsAreExplicitAndStructured` |
| `freshSeriesHasNoPriorFearlessHistory` | `Phase13GA2StructuralIntegratedAuditTest.freshSeriesHasNoPriorFearlessHistory` |
| `hardFearlessBanDoesNotConsumeChampion` | `Phase13GA2StructuralIntegratedAuditTest.hardFearlessBanDoesNotConsumeChampion` |
| `hardFearlessNeverReusesPriorCompletedPick` | `Phase13GA2StructuralIntegratedAuditTest.hardFearlessNeverReusesPriorCompletedPick` |
| `multiRoleChampionCanResolveToMultipleLegalRolesAcrossControlledContexts` | `Phase13GA2StructuralIntegratedAuditTest.multiRoleChampionCanResolveToMultipleLegalRolesAcrossControlledContexts` |
| `planPortfolioCanPivotUnderLegalBans` | `Phase13GA2StructuralIntegratedAuditTest.planPortfolioCanPivotUnderLegalBans` |
| `protectionControlledCaseIsPositiveOnlyForSameRoleThreat` | `Phase13GA2StructuralIntegratedAuditTest.protectionControlledCaseIsPositiveOnlyForSameRoleThreat` |
| `sameDraftSameSeedTimelineReplayIsExact` | `Phase13GA2StructuralIntegratedAuditTest.sameDraftSameSeedTimelineReplayIsExact` |
| `syntheticContextContainsNoRealPlayerIdentity` | `Phase13GA2StructuralIntegratedAuditTest.syntheticContextContainsNoRealPlayerIdentity` |
| `syntheticContextGenerationIsExactAndDeterministic` | `Phase13GA2StructuralIntegratedAuditTest.syntheticContextGenerationIsExactAndDeterministic` |
| `syntheticContextUsesAll216LegalRoleKeys` | `Phase13GA2StructuralIntegratedAuditTest.syntheticContextUsesAll216LegalRoleKeys` |

`hardFearlessNeverReusesPriorCompletedPick`의 neutral/high-baseline 5세트와
`allFiveFearlessGamesRemainCompletable`의 flex-wide/flex-narrow 5세트는 서로 다른 시나리오로 모두 남는다.
총 test 수의 예상 감소는 19건이며 검증 제외가 아닌 동일 fixture/assertion 통합이다.

## 실행과 결과

1차 집중 실행은 변경한 13개 클래스만 선택했다. 90 tests / 실패 0 / 오류 0 / skip 2,
Gradle wall 9분 26초, 클래스 시간 합계 854.136초로 통과했다.
두 진단 skip 클래스는 각각 0.001초였다. catalog CrossJvm은 19.588초, GA는 0.172초,
GA2는 63.711초였다. 병렬 실행의 wall과 클래스 시간 합계를 혼동하지 않는다.
이 focused 실행에서 Match/Auto/Player CrossJvm은 각각 139.398/161.343/168.458초로,
이전 전체의 클래스 시간보다 일관되게 줄지는 않았다. 실제 엔진·드래프트와
fresh JVM 시작 비용이 남으며, 병렬 경합과 실행 순서가 달라 개별 순절감으로 해석하지 않는다.

```bash
./gradlew test \
  --tests com.lolfm.reference.TeamPlayerInformationCrossJvmTest \
  --tests com.lolfm.application.MatchEngineV1CrossJvmDeterminismTest \
  --tests com.lolfm.league.LeagueAutomatedSeriesRunnerCrossJvmDeterminismTest \
  --tests com.lolfm.league.LeaguePlayerSeriesHandoffCrossJvmDeterminismTest \
  --tests com.lolfm.draft.Phase13GAStructuralIntegratedAuditTest \
  --tests com.lolfm.draft.Phase13GA2StructuralIntegratedAuditTest \
  --tests com.lolfm.application.CareerDomesticExecutionTest \
  --tests com.lolfm.application.RealDraftMatchOrchestratorTest \
  --tests com.lolfm.application.MatchEngineV1ContractTest \
  --tests com.lolfm.controller.LeagueApiV1TransportIntegrationTest \
  --tests com.lolfm.controller.LeagueApiV1BackgroundAvailabilityTest \
  --tests com.lolfm.application.PlayerDraftLatencyProfilingV1DiagnosticTest \
  --tests com.lolfm.application.PlayerDraftPerformanceHardeningV1DiagnosticTest \
  --console=plain --no-daemon

./gradlew test \
  --tests com.lolfm.controller.LeagueApiV1BackgroundExecutionIntegrationTest \
  --tests com.lolfm.league.LeagueRelationalPersistenceAndJobTest \
  --console=plain --no-daemon
```

2차 대표 병렬 확인은 2 suites / 7 tests / 실패·오류·skip 0,
Gradle wall 2분 16초, 클래스 시간 합계 144.324초로 통과했다.
최종 테스트 코드·Gradle 설정을 확정한 뒤 아래 전체 회귀를 한 번 실행했다.
첫 실행 요청의 자동 승인 검토가 시간 초과됐지만 허용된 한 차례 재시도가 승인되어
테스트를 실제 실행했다. 테스트 실패나 full regression 재실행에 해당하지 않는다.
두 worker/자식 동시 실행을 실제 프로세스 목록에서 확인했고, 1차 집중 실행 중 관찰한
시스템 사용 메모리는 약 4.3–4.5GiB, swap은 0이었다(peak RSS의 정밀 측정은 아님).

기본 개발 명령은 다음과 같다.

```bash
cd backend
./gradlew test --console=plain --no-daemon
# 제한된 메모리 환경
./gradlew test -PtestForks=1 --console=plain --no-daemon
```

진단 opt-in 조건은 `LOLMANAGER_RUN_PLAYER_DRAFT_LATENCY_PROFILE_V1=1`과
`LOLMANAGER_PLAYER_DRAFT_PERFORMANCE_HARDENING_PHASE=before|after`로 유지된다.
두 클래스는 기존 `--tests` 명시적 선택으로 실행할 수 있다.

## 최종 전체 회귀 실측

`backend/`에서 `JAVA_HOME=/tmp/lolmanager-temurin21 ./gradlew test --console=plain --no-daemon`.
`/usr/bin/time -p`는 시간 관찰용이며 Gradle의 task/선택/실행 의미를 변경하지 않는다.
`> Task :test`가 실제 실행됐고, compile/resources만 UP-TO-DATE였다. 테스트 자체는
UP-TO-DATE/FROM-CACHE가 아니다. clean이나 설정별 전체 재측정은 하지 않았다.

| 지표 | 기존 전체 | 개선 후 전체 |
| --- | ---: | ---: |
| Gradle wall time | 36분 51초 (2,211초) | **21분 43초 (1,303초)** |
| 클래스 시간 합계 | 2,151.751초 | 2,345.156초 |
| Suites | 263 | 263 |
| Tests | 2,011 | 1,992 |
| Failures / errors | 0 / 0 | 0 / 0 |
| Skipped | 2 | 2 |
| 기본 worker 수 | 1 | 최대 2 |
| worker heap 상한 | 2GiB | 1,536MiB |

**Wall time 908초(15분 8초), 41.1% 감소**. 외부 time 실측은 real 1,303.55초,
user 2,429.00초, sys 154.25초다. 역사적 실행의 CPU time은 없어 CPU 총비용 감소를 주장하지 않는다.
클래스 시간 합계는 오히려 193.405초(약 9.0%) 늘었다. 병렬 실행에 따른 중첩과 자원 경합,
worker별 Spring/JIT 캐시 차이가 있으므로 클래스 합계를 wall time 또는 CPU time으로 해석하지 않는다.
목표인 20분 안팎에 가까워졌지만 20분 미만은 아니다. 성공한 전체 실행을 목표 재도전으로 반복하지 않았다.

기존/최종 XML의 클래스 이름 집합이 정확히 같고, 건수 차이는
`Phase13GAStructuralIntegratedAuditTest`의 22→3 한 곳뿐이다.
위 19개 동명 GA2 검증은 그대로 실행되었다. 두 skip도 기존 PlayerDraft opt-in 진단이며
클래스 시간은 각각 0.001초/0초다. 메서드 병렬화, 기본 검증 제외, expectation 완화는 없다.
실제 DB·worker·lease/fence·재시작·gzip·Player completion·fresh JVM 독립 실행을 모두 포함했다.

실행 시작/종료 HEAD는 `1daa0c662c318066d52faf335c52ef284fa3b2f4`로 동일하다.
전체 실행 도중 backend production/test/resource/Gradle 입력의 mtime 변경은 없었다.
관찰한 전체 실행 중 시스템 사용 메모리는 최대 약 5.5GiB, swap 0이며 OOM/격리 충돌은 없었다.
이는 주기적 관찰값이며 정밀 peak 메모리 측정은 아니다.

## 남은 병목과 해석

| 대표 클래스 | 기존 전체 초 | 개선 후 전체 초 |
| --- | ---: | ---: |
| `TeamPlayerInformationCrossJvmTest` | 103.295 | 17.451 |
| `Phase13GAStructuralIntegratedAuditTest` | 75.757 | 0.119 |
| `Phase13GA2StructuralIntegratedAuditTest` | 87.007 | 87.543 |
| `CareerDomesticExecutionTest` | 118.091 | 90.772 |
| `RealDraftMatchOrchestratorTest` | 44.124 | 37.423 |
| `MatchEngineV1ContractTest` | 68.944 | 25.649 |
| `LeaguePlayerSeriesHandoffCrossJvmDeterminismTest` | 156.994 | 171.093 |
| `LeagueAutomatedSeriesRunnerCrossJvmDeterminismTest` | 142.283 | 170.01 |
| `MatchEngineV1CrossJvmDeterminismTest` | 139.781 | 148.427 |
| `AutoDraftVarietyV1ProductionIntegrationTest` | 116.908 | 115.092 |
| `LeagueApiV1BackgroundExecutionIntegrationTest` | 82.933 | 102.597 |
| `PlayerDraftLatencyProfilingV1HarnessTest` | 35.967 | 98.541 |

Catalog의 무관한 전체 서버 초기화와 GA의 반복 draft 실행은 제거했지만,
실제 Match/Auto/Player fresh JVM 세 경계는 여전히 가장 느린 클래스다.
각각 독립 두 번 실행하는 엔진·드래프트·receipt 재현성을 유지했으며,
lazy initialization만으로 해당 클래스 시간이 줄었다고 주장할 수 없다.
AutoDraftVariety의 8 seed 관찰, PlayerDraft/관찰 harness, 실제 background 실행도 비용이 남는다.
이들을 기본 test 밖으로 이동하는 변경은 하지 않았다.

최종 로그·클래스 요약은 `/tmp/backend-runtime-optimization/full.log`, `full-summary.json`,
`full-time.txt`에, 이전 전체와 집중 결과 요약도 같은 임시 디렉터리에 보존했다.
임시 분석용 목록 파일은 정리했으며, Gradle/자식 JVM은 정상 종료했고
별도 서버·브라우저·벤치마크 runner를 만들지 않았다. 전체 통과 뒤에는 검증 문서만 갱신했다.
`git diff --check` 통과. 자동 commit/push/배포는 하지 않았다.

제안 커밋 메시지: `백엔드 회귀 중복 실행과 초기화 비용을 줄이고 2개 테스트 프로세스 적용`
