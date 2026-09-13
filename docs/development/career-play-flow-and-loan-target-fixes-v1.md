# Career 플레이 동선·임대 동의·목표 대상 교정 V1

2026-09-09 시작 HEAD `35ba6aa9910b2b52445709c7d5d22493071298fa`, `main`. 기존 CSV2개, untracked V2 구현 보고서·리뷰2개·prompts·선수정보 ZIP을 보존한다. 사용자 파일 SHA-256은 `/tmp/career-flow-v1/user-files.json`에 시작 시 기록했다. commit/stage/push/배포는 하지 않는다.

## 동선 확인과 변경 범위

| 요청 | 이미 구현되어 유지 | 이번 교정 |
|---|---|---|
| A 현재 상황 | CareerDecisions 기반 현재 업무, Calendar 허용 명령, Continuous 진행/대기/정지 구분 | 보조 League Round/이어하기를 주 행동과 분리하고 선택 시즌·현재 캘린더를 앞에 표시 |
| B 정확한 이동 | 원본 offer/trade/player/fixture ID, 현재 업무 재확인, 준비 포커스 수명, 과거 기록 필터 | 중단 안내의 이동이 경기 시작을 실행하던 경로 제거, 처리 화면의 현재 업무 복귀와 키보드 포커스 |
| C 결과와 다음 행동 | 구단/선수/효력일 거래 상태, 승인 기록·수상 상세, Series 왕복 | 요청 접수 안내와 계약 성립 구분, 돌아온 정확한 Series의 승인 기록 요약 |
| D 복구와 설명 | 원본 UUID 저장/재요청, mutation gate, revision/generation, 읽음과 실행 분리 | 내부 용어를 저장·시즌 중심으로 정리, 같은 Career 새로고침 때 패널 전체 제거 방지, 늦은 Series 열기 응답 무효화 |

## G1

임대 terms는 원계약 총연봉 유지·계약금0을 강제했지만, 선수 금전 점수와75% 하한은 새 협상 요구액을 사용했다. 새 고용과 임대 동의를 분리했다. 임대 금전 기준은 유지되는 원계약 연봉이며 같은 공통 평가의 역할·기회·기간·전력·이동·관계 항목을 보존한다. 분담률은 지급 구단의 부담이고 임대료는 구단 간 비용이다. 새 고용 FA/재계약/유료 이적의 보수 하한은 유지한다.

`CAREER_LOAN_RETAINED_PAY_CONSENT_V2`는 새 임대 및 아직 선수 평가가 확정되지 않은 임대의 다음 실제 판단에 적용한다. 확정 Evaluation에 nullable `loanCompensation`으로 정책·평가일·보장 연봉을 기록한다. terms/quote/구단 동의/UUID를 바꾸지 않는다. 기존 필드 없는 평가 JSON을 읽으며 이미 결정된 거절·AGREED/COMPLETED 결과와 원본 명령 receipt를 재작성하지 않는다.

## G2

`CAREER_SPORTING_FINANCE_V3`의 참가 집합은 기존 규칙의 LCK10/LPL14/LEC10/LCS8/LCP8/CBLOL8팀이다. 순위 비교·목표 생성·최종 결과 소비가 같은 집합을 사용한다. LEC:LR은 계좌·계약·Versus를 유지하되 Summer 목표0/경기평가 중립이다. 일반 재정 평가와 다음 승인은 유지하며 KCB에 별도 계좌를 만들지 않는다.

새 Career는 시장 재정 생성→해외 계좌·원계약 채택→합법 선발 준비 뒤 목표를 고정한다. 참가 구단 계좌가 아직 부족한 지역은 부분 모집단으로 봉인하지 않는다. 빈 명부는 기량0인 참가자로 남는다. 기존 목표가 있으면 재계산하지 않으므로 V2 및 이전 중립 목표/확정 결산·보너스·승인을 보존한다. 다음 시즌에는 기존 전환 명령에서 운영 조직 준비 뒤 새 목표를 생성한다. 과거 migration은 수정하지 않았다.

## 검증 기록

집중/브라우저/프런트/전체 결과를 아래에 구분한다. 이 요청의 계획 전체 회귀는 구현과 집중 확인 뒤1회이며, 이전 V2 전체 결과와 합치지 않는다. 새 실제 경기·장기 관측은 이번 완료 조건이 아니다.

### 집중 검사 중 드러난 경계

- 초기 컴파일: 새 Finance 검사에서 내부 `Approval` 이름 한정 누락을 수정했다.
- 지역 목표 fixture가 중립 LR(목표0)을 최상위 참가자로 선택하던 검사 선택을 참가 대상(목표>0)으로 고쳤다.
- 해외42일 기존 검사의 무작위 생성 UUID가 AI 시장의 seed를 바꾸고 있었다. 임대 평가 변경 뒤 LR 영입 결과가 실행마다 달라져 해당 사례의 생성 UUID를 테스트 이름 기반으로 고정했다. 예산 증액 실험은 효과가 없어 모두 제거했다. 실제 정책·선수 능력·예산은 바꾸지 않았으며 기존 3구단의 합법5인·정상 협상 검사를 유지한다.
- 해외 비활성 기존 저장의 LPL12계좌는 14팀 경기 목표를 만들 수 없다. 그렇다고 일반 재정 승인까지 빠지지 않도록 결산 때 아직 없는 목표에 중립 재정 평가를 생성한다. 이미 있는 목표/결산은 변경하지 않는다. 실제 두 번의 시즌 전환에서 56계좌의 다음 승인을 확인한다.
- 파일 저장 검사의 기존 28일 후보 임대는 과거 새 요구액보다 높은 계약 급여의 금전 점수에 기대고 있었다. 새 정책에서 금전80/기회50/안정성0으로 거절됨을 분리했다. 파일·원본 replay 검사는 보수 유지 선호를 고정한 작은 fixture로 바꾸고 원계약·분담·명부·고정 경기 입력·receipt 보존 단언을 유지했다. 실제 비금전 수락/거절은 NegotiationPolicy의 별도 결정적 사례에서 증명한다.
- 실제 브라우저에서 Career 복귀 후 남은 자동 Series 포인터가 새로고침을 다시 경기로 이동시켰다. 복귀 시 이 포인터만 지우고 승인 결과의 Career/Series 문맥과 서버 Series/Draft를 유지한다.

### 브라우저 준비와 실제 명령 구분

기존 검증 저장 `/tmp/career-v2/final/game.mv.db`를 `/tmp/career-flow-v1/browser/game.mv.db`로 복사했다. 기존 NS–BFX 승인 완료 BO3 2세트와 GEN–DK의 이미 생성된 Player Series를 재사용한다. 이 저장에서 Lumos의 GEN→T1 임대 제안 1건(원연봉44,307,693원, 새 요구액205,000,000원, 임대료45,223,560원, 분담50%, 2026-09-10~2027-09-10)을 기존 submit/persist로 준비했다. Jiwoo 보내기/고액 Ruler 후보는 기존 자격·견적 조건에서 거절돼 적용하지 않았다.

실제 브라우저에서는 Lumos 현재 업무의 원본 링크를 열어 거절 버튼을 눌렀다. 처리 필요2→1, 거래 REJECTED/예약 해제와 소식 갱신을 확인했다. 관리 경기 업무 이동은 기존 GEN–DK Game1 화면을 열었고 새 Draft/경기를 실행하지 않았다. 승인 결과 요약 확인에는 NS–BFX의 기존 승인 자료와 브라우저 복귀 문맥 fixture를 사용한다. 이번 실제 경기 완주로 보고하지 않는다.

환경 시행착오: 기본 Chrome 경로 부재, Chromium 공유 라이브러리 경로 누락, 127.0.0.1 출처의 CORS 거절을 기존 설치 Chromium/로컬 라이브러리/localhost로 해결했다. WSL의 Windows 파일 감시 캐시 때문에 개발 서버가 이전 코드를 제공하여 최신 생산 빌드 preview로 바꿨다. 기본8080 빌드와 격리8089 서버 주소 불일치는 검증용 build 환경값으로 교정했다. 이 오류들은 제품 테스트 통과로 집계하지 않는다.

### 핵심 수치

Jiwoo 원연봉101,694,916원/새 요구액730,000,000원에서, 기존5900점(기준5800점 통과)이75% 하한547,500,000원으로 거절되던 임대는 교정 후7970점/AGREED다. 기회 선호100인 불리한 후보 임대는5000점/REJECTED이고 보수 미달 신규 유료 이적도 거절된다. 임대 Evaluation에 보장 연봉과 평가일·정책 버전을 저장한다.

고전력·고예산 LR 추가 전후 LEC 목표는 모두 같다: FNC3/G2 3/GX6/KC3/MKOI3/NAVI10/SHFT10/SK10/TH10/VIT6. LR은 목표0, 경기 NOT_EVALUABLE, 일반 재정 MET, 성과보너스0이며 다음 시즌 승인이 있다. LPL14(OMG/UP 포함)의 목표와 Split2 탈락 공동13–14위는 같은 시즌 범위를 사용한다. 기존 V2/중립 목표 JSON을 보존하는 검사도 유지했다.

### 실행 명령과 비용

모든 backend 실행은 저장소 루트에서 다음 접두사를 사용했다. Gradle은 같은 Linux 출력에서 병렬 실행하지 않았다.

```bash
JAVA_HOME=/tmp/career-development-jdk /usr/bin/time -p bash backend/scripts/test-linux.sh
```

집중 실행은 이 명령 뒤에 아래 선택자별 `--tests`를 붙였다. `N/F/R/O/M/P`는 각각 `com.lolfm.career.CareerNegotiationPolicyTest`, `CareerFinancePolicyTest`, `CareerFinanceResultsStorageTest`, `CareerOverseasExecutionTest.newCareerActivatesSeventeenEventsAndKeepsReadOnlyViews`, `CareerMarketEngineTest`, `com.lolfm.league.CareerModePersistenceTest`이다. `Q`는 `com.lolfm.career.CareerRecordsStorageTest`이다. 앞 패키지를 생략한 클래스는 모두 `com.lolfm.career` 소속이다.

| 실행 로그(`/tmp/career-flow-v1/`) | 선택자 | 결과 | wall초 |
|---|---|---|---:|
| g-focused.log | N/F/R/O | 새 테스트 컴파일 실패, 0건 |23.14|
| g-focused-fixed.log | N/F/R/O |31건 중29통과·2실패|52.95|
| targets-records-focused.log | F/Q/O |26건 중25통과·1실패|55.10|
| g-callers-focused.log | M/F/O |63건 중62통과·1실패|112.65|
| overseas-boundary.log | O |1통과|45.90|
| persistence-focused.log | F/P의 아래2메서드 |24건 중22통과·2실패|277.86|
| boundaries-fixed.log | F/O/P.marketOffseasonTwoCyclesPreserveSealedRosterAndRepeatBudgetAndNegotiationEvents |24통과|277.93|
| loan-file-fixed.log | P.paidTradeAndLoanUseCalendarAtomicityOriginalCommandsAndFileRecovery |1실패, 짧은 임대 비금전 조건 확인|52.82|
| loan-file-final.log | 같은 P 파일 복구 검사 |1통과|61.65|

집중은 컴파일 실패 포함9회, 실제171건 시도(반복 포함), wall960.00초다. 실패7건은 위 원인별로 분류하고 직접 영향 검사에서 해소했다. 새 backend 테스트 클래스0개, 기존 파일의 검사만 확장했다. P의 파일 검사에는 동시 원본 UUID, 원자 rollback, 고정 명부/기록, 재시작 후 원본 receipt가 포함된다. 두 시즌 전환 검사는 준비된 마감 결과를 사용하며 세계2시즌 실제 완주가 아니다.

```bash
npm --prefix frontend run career:verify
npm --prefix frontend run series:verify
npm --prefix frontend run build
# 격리 브라우저용 빌드만 API주소 지정
VITE_REAL_MATCH_API_BASE_URL=http://localhost:8089 npm --prefix frontend run build
```

career verifier는4회(133/133/134/134 PASS), 기존126건에8개 의미 있는 시나리오를 추가했다. 마지막 wall1.50초다. Series verifier는공유 Root 연결 변경 때문에4회87 PASS, 마지막wall0.55초다. player-draft/league 등 무관한 verifier는 추가 실행하지 않았다. build는8회 시도(초기 type 오류1회, 성공7회): 성공 Vite9.84/11.87/8.73/9.83/9.81/13.23/12.21초다. 최종 기본주소 빌드는wall23.61초/Vite12.21초, 마지막 격리 표시 빌드는wall31.36초/Vite13.23초다. 앞의 초기 실행 일부는wall을 측정하지 않아 시간 합계를 추정하지 않는다.

프런트 실패는 receipt의 `entityId` 대신 실제 `referenceId` 사용과 기존 타입의 중복 `decisionDate` 선언 제거로 해결했다. 늦은 응답·과거 시즌·원본 UUID는 실제 callback/effect를 실행하는 기존 verifier와 통합 검사로 검증했다. 모든 오류를 브라우저 서버 재시작으로 반복하지 않았다.

### 브라우저 완료 근거

원본 명령은 `TRADE_REJECT`, tradeId `market_0de0f4ea68aef7cbac768c1bdbd07e35c65b0244f7f0ca209a133cae0243167b`, UUID `bff2ba0d-9f75-4d8b-95ea-67365ca44ddf`다. 요청 목록에는 이 POST1회만 있고 이동·복귀·승인/기록 확인은 GET이다. 신규 경기 시작/세트 실행0회(브라우저 경로 기준)다.

NS–BFX 승인 요약은 `2027 · LCK_CUP · 2026-08-31`, `LCK:NS 2–0 LCK:BFX`이고 기존 상세의2세트·POG 선정 근거로 연결됐다. 기존 GEN–DK Player는 BO3 Game1 진행 중으로 표시됐으며 복귀 화면은 미승인 상태를 명시했다. 새로고침은 Career를 유지한다. 해당 결과 재조회는 import/정산/읽음 명령을 보내지 않았다.

격리 저장의 전후 비교에서 일정310행, Series binding2행, checkpoint1행, completion receipt1행, 승인 Series1행, 수상7행, 후보50행, 수상 입력7행, Draft근거2행, 출전 binding2행, 성장 binding2행이 동일하다. 시장 명령은 위 원본1행이다. 근거는 `/tmp/career-flow-v1/browser/{requests.log,trade-command.log,preservation.log,return-success.yml,approved.yml,record-detail.yml,approved-final.png}`이다. 사용자 DB는 열지 않았다.

현재 판정에 필요한 작은 합성 fixture와 과거 검증의 승인 기록을 사용했다. 새 Player BO3/세계 시즌 완주, Auto 성능 개선, 다년 분포나 전수 접근성은 이번 검증의 주장이 아니다. 고정 입력의 해외42일은 대표 연결 검사이며 모든 seed에서42일 내 영입 성공을 보장하는 분포 진단이 아니다. 이 문서의wall은 검증 비용이며 제품 속도 개선 수치가 아니다. 읽기 중심 API는 기존 저장된 승인 기록이 아직 없으면 이를 명시하며 자동 복원하지 않는다.

### 전체 회귀

계획 전체를 집중·브라우저 확인 뒤 실행했다. 첫 시도는 아래 환경 원인으로 중단됐고, 재개한 전체는 **286 suites / 2,271건 중2,269통과·실패0·오류0·기존skip2**로 정상 종료했다. wall **1,436.35초**, Gradle23분55초다. skip은 기존 `PlayerDraftLatencyProfilingV1DiagnosticTest`와 `PlayerDraftPerformanceHardeningV1DiagnosticTest`의 선택적 성능 진단이며 이번에 제외를 추가하지 않았다.

전체 실행 시도는2회(환경 중단1 + 정상 완료1)이며, 정상 전체 이후 후속 집중/추가 전체0회다. 최종 backend 제품 입력에서 전체가 통과했다. 원본은 `/tmp/career-flow-v1/full-original/{full.log,TEST-*.xml,summary.json}`, 재개 실행은 `/tmp/career-flow-v1/full-resume.log`에 보존했다. 중단된 첫 실행은 완료/통과나 테스트 건수에 포함하지 않으며 완료wall도 확보하지 못했다. 기존 V2 전체 결과를 이번 근거로 재사용하지 않았다.

### 전체 실행의 환경 중단과 재개 판단

첫 전체는2026-09-09 23:00경 시작했고 약7분 뒤 실행 채널이143(SIGTERM)을 반환했다. Gradle daemon에는 `client disconnection detected, canceling the build`와 Broken pipe가 기록됐다. OOM 흔적은 확인되지 않았고 검사 프로세스도 종료됐다. 완료 XML은 생성되지 않았으며 디렉터리에 남은 단일 XML은 이전 집중 검사 것이다. 이를 전체 완료나 통과로 세지 않는다.

`full-aborted-original/`에 원본 로그, daemon, 미완성 binary 결과를 보존했다. 중단된 generic binary는 정식 완료 보고서가 아니므로 확정된 성공/미실행 전체 집합을 신뢰성 있게 분리할 수 없다. 일부 suite만 선택하면 누락을 증명할 수 없어 동일 제품 입력의 전체 범위 재개가 필요하다. 이는 제품 수정/안심 목적의 재회귀가 아니다. 실행 채널 수명과 분리한 임시 shell의 로그·exit 파일로 결과를 보존하고 기존 Linux 출력 잠금을 유지한다. 별도 suite 제외나 assertion 축소는 하지 않는다. 추가 전체 시도1회의 사유는 이 검증 공백이다.

검증용8089 주소가 최종 산출물에 남지 않도록 브라우저 종료 후 기본 설정으로 마지막 빌드했다. 임시 브라우저와API/preview 서버는 종료했으며, 원본 사용자9파일의 SHA-256은 시작과 동일하다. 새 파일의 후행 공백/마지막 개행과 작업 범위 `git diff --check`를 확인했다. staging/commit/push/배포는 하지 않았다.

### 최종 상태와 변경 경로

최종 HEAD도 `35ba6aa9910b2b52445709c7d5d22493071298fa`, 브랜치`main`이다. 이번 변경은35파일(backend제품12, backend기존검사5, frontend코드/스타일/검증기13, 문서5)이며 미커밋 상태다. 기존 사용자 변경은 별도 보존했다. 새로 stage한 파일은 없다.

핵심 경로는 `CareerTrades`, `CareerMarketEngine/State`, `CareerSportingFinancePolicy`, `CareerFinanceEngine/Store`, `CareerOverseasRoster`, `CareerRecordsQuery/Controller`, `CareerDashboardPage`, `CareerReturnResult`, `careerPlayFlow`, `RootApp`이다. 프로젝트 상태/검증 문서와 실제 바뀐 임대·재정 정책 문서에 후속 정책을 연결했다. 새 migration, 가격/성장/경기/Draft/수상 가중치, 운영 명령 우선순위는 추가하지 않았다.

권장 한글 커밋 메시지: `Career 임대 동의·시즌 목표 대상을 교정하고 플레이 동선 완성`
