# Career 원화·구단 재정·시즌 목표 V1 구현 기록

2026-09-08. 시작 HEAD `086f13c9a854ce0d8efeed16038589be37d4bc9b`, 브랜치 `main`.
원본 `prompts/`, `상금,예산/`, `선수정보.zip` 및 작성 선수 자료를 보존한다.
커밋·push·배포를 실행하지 않는다. 현재 요청과 AGENTS.md 및 `lolmanager-verification`을 적용했다.
팩의 과거 인계 문서에 있는 분석/승인 대기 문구는 이번 구현 승인을 제한하지 않는다.

## 구현과 선행 결함

`CareerSquadPlanner`가 현재 계약의 안전성과 현재 전력의 충분함을 구분한다.
1군의 명확한 보강을 먼저 실제 계약/이적/임대 지출 검사까지 확인하고, 즉시 주전으로 쓸
후보에게 STARTER를 제안한다. 1군 보강이 없거나 감당할 수 없으면 안전한 1군을 유지하면서
CL 필요까지 확인한다. 비싼 1군 후보가 CL의 합법적인 보강을 영구 차단하지 않는다.
포지션당 공통 제안 하나, 후보 수·가격·동의·대체자·관리 구단 제외 규칙은 유지한다.

재현 상태는 현재 12개 능력치 합으로 1군 180, CL 120을 준비했다. 외부 240은 STARTER,
외부 168은 DEVELOPMENT 용도로 제안한다. 실제 선수의 역사적 평가나 실제 영입 결과가
아닌 구조적 playerId 기반 정책 테스트다. 더 강한 후보의 금액을 감당하지 못하면 저렴한
CL 후보로 진행하는 별도 경계도 확인했다.

CL에는 월요일 정상 선발 갱신을 추가했다. 현재 선발보다 능력치 합이 **12 초과** 높을 때만
교체하며 동률·작은 차이는 유지한다. 선발 대기 키는 `team|position|FIRST_TEAM` 및
`team|position|DEVELOPMENT`, 영입 대기는 기존 `team|position`으로 분리한다.
28일 대기, 진행 중 경기·같은 날 선수단 간 출전·국제전 고정 등록 제한을 사용하며,
기존 적용 이력으로 이전 저장의 대기를 보수적으로 복구한다. 등록 갱신은 기존 CL prepare
경로를 사용하고 이미 고정된 Series 입력은 바꾸지 않는다.

## 자료 채택과 통화

원본 예산·육성·비활성·모델 정책·명부 범위, 상금 36항목·대회 대응표·비상금 수입·개인상과
지정된 설명 문서를 읽었다. 원본 `SHA256SUMS.txt` 검사도 통과했다.
별도 `backend/src/main/resources/career/finance-reference-2026-v1.json`에 원본 파일 해시,
2026 참조 연도, low/base/high와 실제 미확인 값, 근거 등급을 유지하며 런타임에는 base만 채택한다.
원본 `runtimeReady`, `enabled`, 근거를 수정하지 않았다.

활성 56구단, 모구단에 포함된 육성 조직 24개, 비활성 OMG·UP 2개의 구조적 ID를 확인했다.
입력의 437명/9명/14명은 조사 범위다. 이후 Career의 선수 수를 이 숫자로 강제하지 않는다.
`actuals`의 실제 현금·부채·승인 예산은 null이다. LOW/VERY_LOW 비용 추정과 게임 초기 자금은
실제 은행 잔액·공시 연봉·계약으로 표시하지 않는다.

현재 금액은 정수 KRW다. 구 크레딧 저장의 전환에만 **1 credit = 1,000원**을 적용한다.
신규 자료 금액에 이 배율을 다시 곱하지 않는다. `GAME_FIXED_FX_V1`은 다음 게임 설정이다.

| 원통화 1단위 | 원 |
|---|---:|
| KRW | 1 |
| CNY | 200 |
| EUR | 1,600 |
| USD | 1,400 |
| BRL | 260 |
| CAD | 1,020 |
| VND | 0.055 |

원통화 정수와 십진 환율을 BigDecimal로 곱해 최종 원에서 HALF_UP한다.
예를 들어 10 VND는 1원, 123,456,789,012 VND는 6,790,123,396원이다.
분배는 BigInteger 정수 비율과 안정적인 playerId 순 나머지를 사용한다.
명령의 개별 급여·계약금·거래 금액 상한은 100,000,000,000원이며 long/JS 안전 정수 범위를 확인한다.
급여는 기존 누적 일할 차감 방식으로 윤년과 인접 지급 기간의 합계를 유지한다.

## 신규 초기 급여·자금·가격

생성 시 AI 이동 전에 기존 기본 선발/소속으로 STARTER·RESERVE·DEVELOPMENT 그룹을 고정한다.
각 그룹의 원화 보상액을 현재 능력치 합으로 나눈다. 적격 명부가 조사 인원보다 작으면 인원
비율만 배분하고 미배분 추정액을 따로 보존한다. 비활성·자격 미확인 선수에게 계약을 만들지 않는다.
이후 승격/이적/임대 때문에 초기 급여를 다시 배분하지 않는다.

신규 계정은 source의 `annualWageBudget`과 `initialCash`를 각각 승인 한도와 현금으로 사용한다.
종전 연봉×160%, 현금×2 및 전역 최소 예산으로 덮어쓰지 않는다.
`protectedOperatingCash`와 `transferAllowanceIncludedInCash`는 초기 현금에 이미 포함된다.
육성 예산, 예비비, 미충당 추정 차이도 추가 지급이나 확정 채무로 만들지 않는다.

다음 수치는 동일 초기 명부를 2027-01-05에 준비한 정책 사례다. 지급 여유는 실제 계약 종료일과
월말 수입/운영비/급여 일정을 반영하므로 날짜·협상에 따라 변한다.

| 구단 | 초기 현금 | 연봉 한도 | 실제 초기 급여 합 | 비급여 연비용 | 보장 연수입 | 추가 지급 여유 |
|---|---:|---:|---:|---:|---:|---:|
| T1 | 7,520,526,000 | 15,246,000,000 | 13,860,000,000 | 3,180,350,000 | 17,040,350,000 | 7,394,596,189 |
| BRO | 786,920,400 | 1,237,500,000 | 1,125,000,000 | 748,390,000 | 1,873,390,000 | 737,605,332 |
| CBLOL RED | 314,510,560 | 396,968,000 | 360,880,000 | 412,516,000 | 773,396,000 | 312,171,307 |

단위는 모두 원. T1/BRO는 LOW·HISTORICAL_TEAM_ANCHOR, RED는
VERY_LOW·WEAK_REGIONAL_PROXY다. T1에서 빈 급여 자리를 만든 27억 원 제안과 작은 해외 구단의
원래 후보 급여 기준 제안을 승인하고, 감당하지 못할 제안은 기존 예산 검사에서 거절했다.

가격은 합의 급여와 분리한 `Price(referenceSalary, referenceStrength, region, role, policy)`를 사용한다.
현재 요구액 = 고정 기준 급여 × 현재 능력치 합 / 고정 기준 능력치 합이다.
FA·무소속은 지역/역할 모델 기준, 지역 미확인은 명시적 LCK 후보 기준을 사용한다.
생성 선수는 저장에 고정된 해당 지역 육성 기준의 중앙값을 사용한다. 기준이 없으면 LCK 육성
45,000,000원/strength 180을 적용한다. 합의 연봉을 두 배로 바꿔도 가격 기준은 그대로인 경계를 검증했다.
기존 협상 비율·남은 계약기간 가치·임대료 계수는 재사용하며 PA/PA-CA/피로 프리미엄은 없다.

## 기존 저장과 원본 요청

V22의 `career_finance_transition`은 변환 전 현재 시장 JSON·해시·도입일·정책·참조 해시를 보관한다.
현재 현금/한도, 계약/제안/역제안, 거래·임대료와 평가용 금액, 장부·체불을 같은 transaction에서
1,000배 변환하고 새 상태 해시를 저장한다. 점수·분담률·식별자·과거 영수증은 변환하지 않는다.
자료 기반 초기 현금을 추가 지급하거나 합의 급여를 새 개인 배분으로 바꾸지 않는다.
계약 기능 자체가 없던 정상 저장의 복구도 종전 초기 계약을 보호한 원화 도입 경로를 사용한다.

봉인된 과거 시즌 JSON/해시·원본 명령 payload/receipt·경기 입력은 그대로다.
현재 화면의 과거 거래는 변환된 현재 원화 장부이며, 봉인된 구시즌 화면은 GAME_CREDITS로 표시한다.
GET은 변환이나 정산을 하지 않는다. 시작 시 복구가 marker를 확인하고 재시작에도 두 번 곱하지 않는다.

새 요청은 `CAREER_MARKET_COMMAND_KRW_V1` / `CAREER_TRADE_COMMAND_KRW_V1`으로 현재 시즌·revision과
원화 정책에 결속한다. 구 UUID와 원본 payload의 receipt 조회를 통화 검사보다 먼저 수행한다.
이미 처리된 요청은 원본 receipt를 반환한다. 미실행 구 요청은
`CAREER_MONEY_POLICY_REFRESH_REQUIRED`로 거절한다. 브라우저는 원본을 유지하고 미실행 확인 후에만
원본 보관/최신 원화 조건 재작성을 허용한다. 단위를 자동 승격해 재전송하지 않는다.

기존 file DB 사례에 임대료 12,345 credit→12,345,000원, 체불 1,234 credit→1,234,000원,
열린 제안 계약금 2,000 credit→2,000,000원을 결합했다. 정책 사례에서는 열린 역제안의
요구 연봉 220,000 credit→220,000,000원도 보존한다. 전환 기록 삽입 후 현재 상태 저장을
제약 조건으로 실패시켜 전체 롤백을 확인했다. 기존 UUID 원본 receipt, 시작된 경기 명단,
다른 Career, GET/복구 재실행 및 파일 재시작을 함께 확인했다.

## 반복 정산·상금

`GAME_RECURRING_FINANCE_V1`의 보장 연수입은 기준 선수 보상 + 비급여 기준 비용이다.
구단 지원 70%, 가상 기본 후원 30%로 분리해 월말 누적 일할 지급한다.
비급여는 staff + facilities/travel/administration + developmentNonWage + employerProvision이다.
총운영비와 선수 보상 추정을 실제 급여에 다시 더해 차감하지 않는다. 고용비용 충당은 게임 비용이다.
예비비는 계획이며, 초기 보호 현금을 영구 잠가 정상 급여 지급을 막지 않는다.
새 계약이 비싸져도 지원이 즉시 늘지 않는다. GRP·미확인 실제 후원·개인상·현물은 현금으로 넣지 않는다.

현금 부족 시 비급여 체불을 저장하며 실제 급여는 기존 급여/체불 경로를 사용한다.
새 계약은 급여·비급여 체불, 계약금·거래 예약, 계약·임대 의무와 승인된 반복 수입을 함께 검사한다.
이미 승인한 미래 한도는 효력일별로 검사한다. 상금 미수와 한도 증액을 현금에 포함하지 않는다.
구 저장의 종전 연간 현금 지급은 도입 시즌에 유지하고 다음 시즌 승인 효력부터 새 월별 정책으로 전환한다.

| 런타임 완료 | 원본 eventId | 적용 |
|---|---|---|
| LCK_CUP | lck_cup_2026 | 0원, 부분 미분류는 1~10위 범위를 유지 |
| LCK_PLAYOFFS | lck_season_2026 | 단일 시즌 최종 분류, 0원; R1/R2·R3/R4·Play-in마다 중복 없음 |
| LCK_CL | lck_cl_season_2026 | PO 결승·준결승·준준결승 결과와 비PO 분류 |
| FIRST_STAND | first_stand_2026 | 게임 최종 분류, SECONDARY_CORROBORATED |
| MSI | msi_2026 | 게임 최종 분류, SECONDARY_CORROBORATED |
| EWC_LOL | ewc_lol_2026 | 게임 최종 분류, 공식 근거 |
| WORLDS | worlds_2026 | 게임 최종 분류, SECONDARY_REPORTED_PRE_EVENT |

CL은 우승 40,000,000원, 준우승 25,000,000원, 공동 3~4위 각 11,250,000원이다.
이는 원표 15,000,000+7,500,000을 나눈 `GAME_SHARED_PLACEMENT_POOL`이며 공식 단독 3/4위 주장이 아니다.
정규리그 시드로 PO 최종 순위를 대신하지 않는다. 5위 이하는 원표에 따른 0원이다.

FST 우승 250,000 USD→350,000,000원, EWC 우승 600,000 USD→840,000,000원,
EWC 5~8위는 **각** 90,000 USD→126,000,000원이다. 최하위 상금을 참가비로 더하지 않는다.
MSI 9/10/11위처럼 서로 다른 원금을 하나의 공동 순위로 제공하면 지급을 보류한다.
UNKNOWN/POOL_ONLY/NOT_APPLICABLE이나 불완전 분류를 임의 0원/균등 분배로 대체하지 않는다.

권리는 실제 완료 적용 transaction에서 기록한다. 일반 대회 완료 논리일+7일, EWC+42일에
Calendar 시장 정산이 실제 입금한다. EWC 자료의 최소 35일을 공식 마감일로 표시하지 않는다.
수취인은 Career의 검증된 결과다. 원금·원통화·근거·FX·원화액·권리일·효력일·결과/참조 해시를 보존한다.
권리 키는 Career/시즌/대회 instance/팀/FINAL_PLACEMENT이며 금액·정책 버전은 키에 넣지 않는다.
원본과 다른 결과는 충돌로 감지한다. 이미 완료된 대회는 도입 시 제외 목록에 고정하고,
진행 중이던 대회는 도입 뒤 완료할 때 포함한다. 미수는 시즌이 넘어가도 유지된다.

## 목표와 다음 예산

목표는 초기 현재 선발 전력 순위와 승인 예산 순위의 구간으로 고정한다.
LCK 3/6/10위 이내 목표, 상위 구간은 Worlds 8위 이내를 사용한다.
해외 미실행 리그는 경기 평가 불가·중립이며, 중간 도입 시즌도 경기 평가·성과 보너스를 소급하지 않는다.
재정은 도입 뒤 신규 약정의 한도 준수, 실제 체불, 기말 현금·확정 의무의 지급 여유를 평가한다.
승리 목표를 이유로 사용자 선수단을 AI가 바꾸지 않는다.

경기 충족은 시즌 시작에 고정한 기본 후원 총액의 5%, 초과는 10% 한 번 지급한다.
중복 적립하지 않는다. 다음 승인 반복 수입은 이전 승인 대비 -5/0/+5%, 평가 불가는 0%이며
원래 구단 기준의 80~120%로 제한한다. 체불 등 재정 미달도 감액 판정에 포함한다.
다음 연봉 한도는 반복 수입-비급여 비용과 예약·체불·2개월 계획 유보를 제외한 현금의 3년 분산분이다.
현재 현금에 이미 입금된 상금/보너스를 별도로 또 더하지 않는다.
기존 미래 계약은 유지하고 새 한도를 넘는 확정 약정을 표시한다. 한도 승인에는 현금 입금이 없다.

정책 층의 통제 예: T1 2027-01-05 시작, LCK/Worlds 우승 분류를 준비하면 고정 후원
5,056,081,932원에 대한 초과 보너스 505,608,193원, 다음 연수입
17,040,350,000→17,892,367,500원, 다음 연봉 한도 16,440,709,453원이다.
기존 미래 약정 10,987,226,277원을 취소하지 않았다. 평가와 보너스를 반복 적용해도 상태는 같다.
이는 순수 정책 사례이며 실제로 그 날짜에 대회를 완주했다는 증거가 아니다.
2027-12-29 Worlds 권리는 2028-01-05에 지급하는 연도 경계도 확인했다.
기존 두 시즌 통합 사례에서는 실제 openStove/Calendar/rollover·재실행·과거 snapshot 보존 경로를 사용했다.

## API·화면·브라우저 증거

기존 시장 View의 `clubFinance`에 관리 구단의 기준, 반복 수입/비급여, 체불, 상금 권리,
목표와 다음 승인만 추가한다. 새 조회로 상대 AI의 구매 상한·미확정 조건을 제공하지 않는다.
기존 계약·선수 약속·이적/임대의 금액 표시도 현재 KRW/봉인 과거 크레딧으로 구분한다.
`newerMarket`은 같은 시즌의 뒤늦은 크레딧/낮은 revision 응답이 KRW 화면을 덮어쓰지 못하게 한다.
기존 Calendar 잠금·조회 generation·UUID 복구를 유지한다.

브라우저는 `playwright` 스킬과 기존 Chromium 1243을 사용했다. 1237 경로가 없다는 초기 실행
오류를 기존 설치 경로로 교정했다. `/tmp/career-finance-browser/db`와 18188/5173 서버는 사용자
저장과 분리했으며, 직접 시작한 브라우저와 두 서버를 종료했다.

기존 CL 통제 대진 테스트의 결과를 fixture로 사용했다. 실제 경기 엔진 완주는 추가하지 않았다.
통제 경기 결과를 기존 verified-completion 적용 경로에 넣어 CL 권리를 생성한 뒤,
실제 UI의 계약 제안·철회와 하루 진행으로 정산했다. 경기 결과 준비와 런타임 실행 구간을 구분한다.

- 시작: 2027 시즌의 논리일 2026-08-24, T1 CL 1위 미수 40,000,000원.
- Bo 요구 기준 102,777,777원으로 UI 제안, 계약금 1,000,000원 예약 표시 후 즉시 철회.
  사용자 테스트 입력이며 AI의 실제 선수단 영입 판단 사례로 주장하지 않는다.
- UI 하루 진행 7회로 2026-08-31. CL 입금 1건, 예약 0원, 체불 0원.
- 현금 7,520,526,000→7,560,525,998원. 같은 날 지원·후원·비급여·개별 급여 일할 합의
  차이 -2원을 포함하며, 상금 자체는 정확히 40,000,000원이다.
- 지급 여유 7,394,596,189→7,434,596,189원. 미수 단계에서는 증가하지 않는다.
- 새로고침 후 현금·상금 지급일·목표 보존. 화면: `output/playwright/career-finance-krw-v1.png`.
- 상세 확인 파일 `/tmp/career-finance-ui-*.txt`, `/tmp/career-finance-browser-market.json`.

## 검증 기록

Gradle은 JDK `/tmp/career-development-jdk`로 순차 실행했다. 새 검증 클래스는 정책 1개이며
저장·완료·시즌 사례는 기존 테스트를 확장했다. 새 통합 클래스/진단 프레임워크/분포 실험은 없다.

1. 선행 AI 정책 11건, 1분 39초 통과.
2. 최초 정책 10건 중 1건은 actuals의 상태 문자열까지 null 금액으로 본 테스트 오류.
   금액 필드 검사로 교정 후 10건 통과. 테스트 파일 생성 경로를 잘못 지정한
   `No tests found` 실행도 별도 도구 실패로 기록하며 검증 통과로 세지 않는다.
3. 정책+file DB 11건 중 파일 사례 하나는 철회한 선수의 재접촉 대기 때문에 실패.
   이주 확인용 선수를 분리했다. 같은 선수의 대기를 제거하거나 규칙을 완화하지 않았다.
4. 기존 저장/거래/시즌/CL 4건, 9분 15초 중 2건 통과, 2건 실패.
   CL의 관리 팀 0원 결과를 실제 유상금 결과 준비로 바꾸고, 같은 날 월말 현금 흐름을 별도 검사했다.
   시즌 fixture의 구 160% 예산 전제 일괄 150% 재계약을 실제 감당할 현재 요구 연봉/주전 약속으로 바꿨다.
5. 정책 13 + CL 1 + 두 시즌 1 = **15건, 10분 49초 통과**.
6. 마지막 정책 13 + AI 12 + 기존 시장 40 + 임대/체불 file DB 1 = **66건, 2분 51초 통과**.
   `/tmp/career-finance-final-focused-passed/`, `/tmp/career-finance-integrated-passed/`에 XML 보존.
7. `npm --prefix frontend run career:verify`: **93건 통과**.
   `npm --prefix frontend run build`: TypeScript/Vite 통과, 마지막 억·만 표기 보완 후 Vite 10.61초.
   첫 빌드의 nullable market 표시 오류를 optional access로 고쳤다.
   마지막 표기 함수의 통화 인자도 기존 View 타입과 정렬한 뒤 build를 통과했다.
   `careerMoney`는 억·만과 나머지 원까지 표시해 반올림 없이 정수 금액을 보존한다.
   같은 검증 DB를 다시 열어 최종 화면을 확인하고 스크린샷을 갱신한 뒤 서버/브라우저를 닫았다.
8. 계획된 전체 **1회, 36분 27초**: **271 suites / 총 2,123건 / 2,115 통과 / 실패 6 / 오류 0 / 기존 skip 2**.
   `/tmp/career-finance-full-evidence/full.log`, `summary.txt`, `failures.txt`, `xml/`에 원본 보존.
   최종 tree의 clean full 통과로 표시하지 않는다.
9. 실패 여섯 건 교정 후 집중 검증: **2분 32초, 5 통과/1 실패**.
   `/tmp/career-finance-post-full-fixes.log`, `/tmp/career-finance-post-full-first/` XML 보존.
   신인 Auto 준비의 요구 연봉 3배가 작은 구단의 실제 한도를 넘었다. 예산 검사를 변경하지 않고
   이 경기 입력 검증의 대상만 현재 급여 여유가 가장 큰 적격 Auto 구단으로 선택했다.
   저예산 영입 가능/거절은 별도 기존 재정 정책 사례에서 이미 확인했다.
10. 마지막 해당 Auto **1건, 3분 19초 통과**, `/tmp/career-finance-post-full-auto-fix.log`.
    `/tmp/career-finance-post-full-auto-passed/`에 XML 보존. 최초 전체의 여섯 실패는 모두
    교정 후 검증했다. 추가 전체 실행은 없으며 미해결 실패는 없다.
    마지막 기존 Auto fixture는 신인 공급/영입과 이전 일정을 통제 준비한 뒤 실제 Calendar가
    2027-03-31→04-01에 입력을 고정하고 Production Auto **3게임**을 완료했다.
    출전/성장 반영, 시작/완료 롤백, 원본 UUID·outbox 재처리의 중복 방지를 확인했다.
    완료 receipt는 `0a243662021fa5450c22d1ab1ce3049d0791383bb909acba6a3a1c8f5120d56d`다.

전체 여섯 실패를 네 원인으로 분류하고 후속 집중 검증한다. 신규 V22의 이주 개수는
신규 22개/V4 이후 18개/V1 이후 21개로 기대값을 갱신했다. 기존 Auto 입력 준비 helper는
구 크레딧 요구액 대신 현재 시장의 원화 요구액을 사용하도록 교정했고, 성장 뒤 가격 단언은
고정 strength×1,000 대신 저장에 고정한 기준 급여/기량과 현재 기량의 관계를 검사한다.
선수단 이주 fixture도 원화 도입 시 운영 정책이 저장되어 증가한 실제 revision에서 시작하도록
맞췄다. 이후 일곱 변경, 구 revision 거절, 원본 UUID와 고정 입력/재시작 검사는 유지한다.
이는 실제 계약·경기·영수증 검사를 제거하는 변경이 아니다. 백엔드 제품 수정은 없으며,
변경된 test helper의 유일한 호출자와 실패 여섯 메서드를 모두 후속 실행에 포함했다.
범위가 테스트 준비/기대값에 한정되고 이 사례들이 모두 통과했으므로 추가 전체 실행은 필요하지 않았다.

재현 명령의 작업 디렉터리는 `backend/`다.

```bash
JAVA_HOME=/tmp/career-development-jdk ./gradlew test \
  --tests 'com.lolfm.career.CareerFinancePolicyTest' \
  --tests 'com.lolfm.career.CareerSquadPlanningPolicyTest' \
  --tests 'com.lolfm.career.CareerMarketEngineTest' \
  --tests 'com.lolfm.league.CareerModePersistenceTest.marketCommandsPersistMembershipMoneyAndOriginalReceiptsWithoutChangingFrozenSeries' \
  --console=plain --no-daemon
JAVA_HOME=/tmp/career-development-jdk ./gradlew test --console=plain --no-daemon
```

후속 집중 재현 명령도 `backend/`에서 실행한다.

```bash
JAVA_HOME=/tmp/career-development-jdk ./gradlew test \
  --tests 'com.lolfm.league.CareerModePersistenceTest.atomicProvisionReplayPlayerResumeAndFileRestartReuseExistingAuthority' \
  --tests 'com.lolfm.league.CareerModePersistenceTest.v4CareerMigratesToFrozenV5CalendarWithoutBackdatingFoundationBinding' \
  --tests 'com.lolfm.league.CareerModePersistenceTest.expandedRosterMigrationCommandsFreezeAndCareerIsolation' \
  --tests 'com.lolfm.league.LeagueRelationalPersistenceAndJobTest.migratesEmptyAndPreviousSchemaThenRestartsFromSameFile' \
  --tests 'com.lolfm.league.LeagueAutomatedSeriesRunnerProductionV9Test.selectedReserveRunsThroughActualLeagueAutoAndFrozenReceiptValidation' \
  --tests 'com.lolfm.league.LeagueAutomatedSeriesRunnerProductionV9Test.calendarDateAdvanceCapturesSettledStartAndAppliesActualAutoExactlyOnce' \
  --console=plain --no-daemon
```

## 한계와 마무리

단일 base와 고정 환율, 제한된 목표·후원 정책이다. 실제 개인 연봉, 법정 세무, 구단의 실제
현금/모기업 지원·후원 계약, 선수 상금 분배를 재현하지 않는다. 해외 리그·새 대회를 추가하지 않았다.
적은 수치 사례와 두 시즌 검증은 현실 재무 정확성이나 장기 경제 균형의 보증이 아니다.
불완전 최종 분류는 상금 보류로 표시하며 새 결과 편집/소급 지급 시스템은 없다.

최종 HEAD는 시작과 같은 `086f13c9a854ce0d8efeed16038589be37d4bc9b`, 브랜치는 `main`이다.
이번 작업은 추적 파일 29개 수정·신규 파일 12개이며 커밋하지 않은 working tree에 남긴다.
원래 미추적 `prompts/`, `상금,예산/`, `선수정보.zip`은 유지했다. `git diff --check`는 통과했다.
직접 시작한 두 차례의 격리 백엔드/Vite와 `career-finance` 브라우저를 모두 종료했고 PID 종료를
확인했다. commit/push/배포는 하지 않았다. 전체 결과와 후속 집중 결과는 위에 구분해 보존했다.
한글 커밋 메시지 제안: `커리어 원화 재정·상금·시즌 목표 연결 및 AI 선수단 판단 보완`.
