# Career 계약 시장 결함 수정·추가 선수 V4 통합 V1

시작 HEAD: `7c6f232861fd0486ea0f8021f35ba15e2ab89e5d`. 시작 작업 트리는 기존 사용자 `prompts/`만 미추적이었다. 원본 선수정보·능력치·일정·prompts와 기존 280명 resource는 수정하지 않는다. 이번 작업은 이미 구현된 Career에 두 결함 수정과 승인된 V4 자료를 적용한다.

## 금융 결함과 최소 복구 정책

기존 검사는 계약금 예약과 현재 현금, 기간별 연봉 상한만 비교했다. 현금을 전부 계약금으로 써도 계약이 성립해 이후 월말 급여와 방출 비용을 지급할 수 없고 Calendar transaction이 반복 롤백될 수 있었다.

`PAYROLL_CASH_FLOW_AND_ARREARS_RECOVERY_V2`는 연봉 상한과 별도로 현금 흐름을 검사한다. 현재·예약 계약과 열린 제안의 시작/종료일, 이미 발생한 미정산 급여, 월말/만료 정산, 확정된 매년 1월 1일 예산 배정을 날짜 순서로 투영한다. 열린 계약금은 현재 현금에서 한 번 예약하며 미래 연간 배정으로 아직 없는 계약금을 미리 약속하지 않는 보수적 정책이다. 급여는 지급 사건에 반영하므로 계약금과 이중 예약하지 않는다. 투영 구간의 최소 잔액이 음수인 제안·수정·수락은 사용자/AI 공통 경로에서 거절한다. 방출도 해당 선수의 정산·해지 비용을 지급한 뒤 남은 선수의 급여를 확보해야 한다.

이미 지급 불능이 된 저장은 삭제하거나 숨은 현금을 넣지 않는다. 기존 V16 journal에 `SALARY_ACCRUED`(미지급 발생액)와 `SALARY_ARREARS_PAYMENT`(실제 상환 지출)를 추가한다. 발생액은 현금 수입이 아니다. 급여 기간을 인식한 날짜와 실제 지급 완료 `paidThrough`를 구분하며, 미지급 상태에서 같은 기간을 재청구하지 않는다. 연간 배정 직후 오래된 미지급 급여부터 계약 ID로 동률을 정해 정산한다. 미지급금이 남으면 추가 계약 지출과 유료 방출을 제한하지만 Calendar는 진행한다. 신규 계약/현금 재원 검사를 저장 구조의 무결성 검사와 분리하여 복구 가능한 부족 상태를 저장 손상으로 거절하지 않는다.

기존 JSON 구조·ledger 확장을 활용하므로 별도 SQL migration은 필요하지 않다. 기존 payload/receipt/계약/과거 지급은 재작성하지 않는다. `Finance`에 `fundingPolicy`, `salaryArrears`, `paymentHeadroom`을 추가하고 기존 현금·계약금 예약·연봉 필드의 의미를 유지한다. 프런트는 구형 재정 응답과 명시적 V2 복구 응답을 모두 읽으며 부족 상태도 화면에서 설명한다. 게임 초기 예산·연봉·능력치를 결함 회피용으로 낮추거나 높이지 않았다.

## 만료 직전 협상 날짜

기존 2027-01-05 제출 / 기존 계약 01-07 종료 / 새 시작 01-08 / 결정 01-10 조합은 접수 후 수락 단계에서 실패했다. 시작 가능일을 `max(기존 종료 다음 날, 공통 결정일)`로 정해 새 조건은 01-10부터 효력이 생긴다. 기존 계약은 01-07까지 유지되고 01-08~09는 실제 FA 공백이며 해당 기간 급여를 소급 생성하지 않는다.

화면 시작 가능일·서버 제출/수정·AI 제안이 같은 계산을 사용한다. 이미 열린 제안에 합류하거나 수정해도 공통 결정일은 연장하지 않는다. 결정일이 종료일보다 이른 정상 예약 재계약은 기존 종료 다음 날을 유지한다. 이전의 잘못된 열린 제안은 원본 조건을 보존한 채 명시적 수정으로 대체할 수 있고 화면은 수정 폼의 과거 시작일을 새 시작 가능일로 안내한다. 수정하지 못하고 결정일을 넘긴 제안은 이유를 남기고 거절한 뒤 새 협상을 할 수 있다. 이미 완료된 결정·계약·경기는 다시 계산하지 않는다.

## V4 입력과 실제 변경

입력은 사용자 폴더 `선수정보/fa,2군,후보/`의 `extended-player-master-2026-09-06-v4.json`, 런타임 후보, 관련 README/MIGRATION/handoff/작성·검증·감사/변경 ledger/체크섬이다. 27개 원본 체크섬을 확인했다. 패키지 자체 PASS를 서버 검증으로 재사용하지 않고 master→production을 별도로 선형 대조한다.

- 전체 460명 = 기존 280명 + 추가 180명. 경쟁 팀 56개 유지.
- 최종 조직 82개 = 경쟁 조직 56개 + 추가 조직 26개. 기존 순증 23개에서 BLG Junior·RED Academy·Keyd Academy 3개가 추가됐다. 후보 resource에 중복 기재되지 않은 기존 경쟁 조직은 기존 280명 카탈로그에서 그대로 결합한다.
- 현재 production 대비 능력치 변경: 177명, 1,959개 값. 작성 숙련도 차이: 162명, 668개의 실제 합법 챔피언 값(삭제된 override의 기본값 14 복귀 포함).
- 숙련도 비수치 메타데이터는 180명 변경이며 그중 sourceUrl 문자열 변경은 5명이다. 이를 668개의 실제 수치 변경과 혼동하지 않는다.
- 현재 조직 ID 변경 40명, 연계 경쟁 팀 변경 12명. 원문 squad 변경 115명, 운영상 DEVELOPMENT 정규화 후 배치 변화 39명.
- V4의 V3 대비 2,160개 능력치/1,243개 작성 숙련도 유지 주장은 패키지의 비교 기록이다. production 대비 변경과 별개이며 없는 V3 파일을 재구성하지 않았다.

런타임 후보의 `OBJECTIVE_SECURING`, `ROAM_COORDINATION`, `ENGAGE`, `ZONE_SETUP`은 현재 enum의 `OBJECTIVE_SECURE`, `ROTATION_PLANNING`, `ENGAGE_EXECUTION`, `AREA_SETUP`으로 매핑했다. 값 자체는 바꾸지 않았다. 180개 ID·능력치 2,160개·숙련도 override 1,243개가 master와 대응한다. 후보가 생략한 출처·개인/계약 해석·작성 근거는 master에서 상세 정보로 보존했다.

## 소속·역할·FA의 실행 의미

CHALLENGERS·ACADEMY·DEVELOPMENT 124명은 운영상 DEVELOPMENT이며 원문은 상세의 roster에 보존한다. 조직 연결·확인된 현재 소속·작성 포지션/등록 역할이 일치할 때만 명시적 선발·연계 육성 이동을 허용한다. 실제 게임 계약도 기존 공통 출전 경로에서 검사한다. 카탈로그 로딩은 선발 명령이 아니며 초기 280명 선발은 전혀 변경하지 않는다. AI는 이후 기존 운영 사건에서 같은 자격 검사를 통과한 후보만 사용한다.

| 선수 | 이전 production | V4·실제 적용 |
| --- | --- | --- |
| Jiwoo | KT 1군 후보, 조작 16·Ezreal 17 | KT 후보 유지, 능력치 12개 모두 15·Ezreal 16. 유효 계약 후 사용자 선발 가능 |
| Wenbo | BLG 1군, 조작 16·Yorick 15 | BLG Junior 육성·BLG 연계, 조작 14·Yorick 17. 승격 전에는 1군 선발 불가 |
| MISSING | LNG, 시야 17·Nautilus 15 | LNG 유지(이적 아님), 시야 17·Nautilus 18. 작성 SUPPORT/등록 SUPPORT 일치 |
| Jwei | IG, 교전 수행 16·Nautilus 16 | WBG, 교전 수행 15·Nautilus 15. 새 Career 소속·계약에 적용 |
| RenYe | GZ Academy 표시, 운영상 소속 미확인 | GZ Academy 육성·GZ 연계 확인. 계약 조건은 미확인이므로 보호된 게임 초기 계약 |
| Bo | 게임 FA, 능력치 12개 14 | 게임 FA 정책 유지, 조작 16·동선 16 등 V4 작성값. 현실 영입 가능 여부는 미확인 |

소속 미확인 14명 전체를 게임 FA로 만들지 않는다. 명시적 기존 게임 정책의 Bo/BeryL/FoFo/FATE 4명만 초기 FA이며 나머지 미확인 선수와 UP/OMG 비경쟁 조직은 구분한다. Valiant(MID 작성/SUPPORT 등록)와 Vincenzo(JUNGLE 작성/복수 등록)는 `V4_REGISTERED_ROLE_REVIEW_REQUIRED`로 유지하고 임의 역할 변환·선발·AI 협상을 막는다. 다른 정상 선수는 이 사유로 차단하지 않는다.

추가 180명의 공식 데뷔일은 미확인이다. 이전 production도 공식 데뷔일은 0명이었으며, V4에서 최초 확인 소속 기록일 75명이 별도 필드로 추가된다. V3의 날짜 이동 설명을 production의 데뷔일 75개 삭제라고 보고하지 않는다. 화면은 공식 경기 데뷔일/최초 확인 소속일과 조사 당시 나이를 구분하며 게임 현재 나이나 게임 내 경력으로 바꾸지 않는다.

## 적용 경계와 저장 호환

| 범위 | 새 Career | 기존 Career |
| --- | --- | --- |
| V4 작성 수치·소속·상세·조직 | 새 초기 directory에 적용 | 저장된 directory와 hash 그대로 유지 |
| 초기 선발 | 기존 280명 유지 | 사용자 선발 유지 |
| 계약·제안·지급 | 새 자료로 게임 초기 계약/예산 생성 | 기존 계약·예약·지급·receipt 유지 |
| 금융/협상 날짜 결함 수정 | 적용 | 적용, 미지급금은 이후 지급 사건에서 명시적 기록 |
| 다음 시즌 | 현재 게임 상태 이월 | 전역 V4 재import 없이 현재 저장 이월 |
| 기존 등록·시작한 Series | 기존 고정 입력 유지 | canonical/receipt/Draft/checkpoint 유지 |

`CareerRosterStore.directory`는 저장 JSON의 기존 hash를 검사한 뒤 저장된 정의를 읽는다. ExpandedPlayerCatalog는 새 directory 생성 때만 사용한다. 기존 경기 canonical의 resource identity는 변경하지 않은 280명 카탈로그와 저장된 roster snapshot을 사용하며 검사 제거·hash 일괄 치환은 하지 않았다.

## 검증 기록

- `python3 backend/scripts/verify-expanded-player-v4.py`: 선형 대조 통과. 원본·runtime을 변경하지 않는 검증이다.
- 첫 집중: `./gradlew test --tests 'com.lolfm.career.CareerMarketEngineTest' --tests 'com.lolfm.player.GlobalTeamRosterCatalogTest' --console=plain --no-daemon`: 22건 중 날짜 fixture 2건 실패, 1분 36초. 테스트 helper가 진행된 제출일 대신 1월 5일을 사용한 원인을 교정했다. 수치/정책을 통과용으로 바꾸지 않았다.
- 교정 후 집중: 위 두 클래스 + `--tests 'com.lolfm.league.CareerModePersistenceTest.expandedRosterMigration*' --tests 'com.lolfm.league.CareerModePersistenceTest.marketCommands*'`: **25건 통과**, **2분 2초**. 엔진 17/카탈로그 6/저장 2, 실패·오류·skip 0.
- 지급 여유 경계 assertion 강화 후 해당 parameterized 메서드: **2건 통과, 35초**. 정확한 한도 제안이 실제 수락되고 월말까지 미지급 0인 것과 방출 뒤 남은 선수 급여 재원을 확인했다.
- 최종 조회 사유 교정 후 `--tests 'com.lolfm.league.CareerModePersistenceTest.marketCommands*'`: **1건 통과, 1분 12초**, 실패·오류·skip 0.
- `npm run career:verify`: **55건 통과**. 기존 53건에 명시적 지급 불능 응답 표시/음수 미지급금 거부 2건 추가.
- `npm run build`: TypeScript/Vite production build 통과, 최종 Vite **7.84초**.

금융 단위 경계는 사용자/AI 공통 제출 경로에서 현금 전액 계약금 거절, 정확한 지급 여유 한도, 초과 1 크레딧, 수정 실패 원본 유지, 철회 예약 해제, 중복 제안 지출 제한, 지급 불능 journal/새해 우선 상환/같은 날짜 재실행을 확인한다. 날짜 경계는 만료 직전·당일·다음 날, 기존 결정 기간 합류, 수정·이전 잘못된 시작일의 명시적 수정, 정상 예약 재계약을 확인한다.

저장 검증은 실제 Calendar transaction으로 월말 미지급금 발생·원본 UUID 재요청과 파일 DB 재시작을 확인한다. 이전 directory 사례는 Jiwoo 조작 16인 대표 저장 fixture를 준비하고 V4 15가 소급되지 않음을 검사한다. 새 Career는 Jiwoo의 V4 능력치·Ezreal 숙련도까지 다음 Series에 고정하며 Hwichan의 정규화된 육성 승격도 검사한다. 기존 binding canonical과 다음 변경 후 고정 입력을 보존한다. 이 검증은 새 엔진 경기 완주가 아닌 실제 운영 명령과 경기 입력 고정 검증이다.

격리된 실제 서버/H2 DB와 Chromium에서 대표 브라우저 흐름 하나를 실행했다. KT 새 Career의 2026-08-24 현금 6,521,600 전액을 Bo 계약금으로 제안했을 때 급여 재원 부족 400과 화면 이유를 확인했고 현금/예약은 바뀌지 않았다. 연봉 252,000·계약금 10,000·2년·STARTER로 명시적 재제출 후 08-26 응답, 08-29 공통 결정/실제 수락을 확인했다. 08-31 Calendar 월말 급여 후 현금 6,464,859, 예약 0, 미지급 급여 0이었다. Jiwoo 상세에서 공식 경기 데뷔일 미확인과 최초 확인 소속일 2022-01-23을 별개로 표시한다. 새로고침 후 계약 상태도 유지됐다.

브라우저는 실제 협상 명령·Calendar·조회까지 실행했고 실제 경기를 시작하거나 완주하지 않았다. Jiwoo 선발→다음 Series 입력과 Hwichan 승격은 위 기존 통합 테스트로 확인했으며 브라우저에서 중복 경기 증명을 하지 않았다. 과거 지급 불능과 구형 Jiwoo 정의는 통합 테스트에서 의도적으로 준비한 fixture이고 실제 사용자 저장을 수정한 것이 아니다. 후속 시즌 이월은 기존 경로/회귀 검증에 포함되며 다년 실제 경기 완주를 새로 수행하지 않았다.

증거는 ignored `backend/build/reports/career-v4/`의 focused/funding-boundary/final-focused/frontend/full 로그와 browser-current/browser-payroll snapshot, `.playwright-cli/` 기록에 보관한다. `output/playwright/market-payroll-v4.png`는 Calendar 날짜 viewport이며 금융 수치는 DOM snapshot에서 확인했다. 의도적인 최초 제안 400과 Vite 재시작 연결 로그를 기능 오류로 혼동하지 않는다. 최종 새로고침 이후 수집한 browser console은 오류/경고 0이었다. 검증 브라우저와 서버/Vite를 종료했다. 브라우저 실행에 빠진 Linux 라이브러리는 /tmp에 추출해 사용했으며 시스템 설치나 사용자 DB 변경은 하지 않았다.

최종 production tree에서 `./gradlew test --console=plain --no-daemon`을 **한 번** 실행하여 **BUILD SUCCESSFUL**, **265 suites / 총 2,028 tests / 통과 2,026 / 실패 0 / 오류 0 / 기존 skip 2**, **29분 6초**를 확인했다. aggregate JUnit XML time은 **2,953.651초**다(병렬 실행 시간 합계이므로 wall과 다르다). 두 skip은 기존 `PlayerDraftLatencyProfilingV1DiagnosticTest.captureOfficialPlayerDraftInteractiveAndSimulationLatencyProfile`과 `PlayerDraftPerformanceHardeningV1DiagnosticTest.capturePairedBackendProbe`다. skip이나 기대 수치를 일괄 변경하지 않았다. 현재 변경 클래스 전체는 엔진 17건, 카탈로그 6건, 저장 통합 27건 모두 통과했다. 최종 집계는 `backend/build/reports/career-v4/full-summary.json`이다. 이전 작업의 2,021건 결과는 재사용하지 않았다.

환경상 최초 집중 명령은 Gradle cache lock 쓰기 권한이 없어 테스트 시작 전에 실패했다. 기존 cache를 사용할 권한으로 실행해 해결했으며 전체 회귀의 중단/재실행은 없다. 브라우저 누락 라이브러리도 /tmp 추출 후 해결했다. clean full 이후 문서만 갱신하고 전체 테스트를 반복하지 않았다.

## 최종 변경·정리

`git diff --check` 통과. HEAD는 시작과 같은 `7c6f232861fd0486ea0f8021f35ba15e2ab89e5d`다. 사용자 원본 27개 checksum과 기존 280명 resource를 보존했고 기존 미추적 `prompts/`도 수정하지 않았다. 검증 브라우저 `career-v4`를 닫고 Spring/Vite를 종료했으며 18185/5173 포트가 닫혔음을 확인했다. 검증용 DB·runtime·브라우저 의존 파일은 사용자 저장과 분리된 /tmp에 두고 로그/증거만 ignored build 경로에 보존한다.

commit·push·배포는 하지 않았다. 제안 커밋 메시지: `fix: Career 계약 지급·협상 날짜 결함 수정 및 추가 선수 V4 통합`.

## 제한

사용자가 승인한 V4 작성값을 적용했으며 현실 사실 전수 재조사나 실력/밸런스 정확성 검증은 하지 않았다. 미확인 소속·계약·역할은 그대로 남긴다. 유료 이적·임대·성장/훈련·부상·해외/CL 전체 경기·상세 재정 시스템은 추가하지 않았다. 시장 복구는 무이자 미지급 급여 기록과 확정 예산 정산이며 대출·파산 시뮬레이션이 아니다.

## 선수별 production 대비 변경 요약

능력치는 변경된 12개 필드 수, 숙련도는 합법 챔피언의 실제 값이 달라진 수다. 원문 배치는 DEVELOPMENT 정규화 전 분류다. 원본 V3→V4 ledger와 구분한다.

| PlayerId | 능력치 변경 수 | 숙련도 값 변경 수 | 이전 조직·배치 → V4 원문 |
| --- | ---: | ---: | --- |
| player-13 | 12 | 4 | LEC:MKOIFENIX · DEVELOPMENT → LEC:MKOIFENIX · ACADEMY |
| player-131 | 12 | 0 | LCP:GZACADEMY · DEVELOPMENT → LCP:GZACADEMY · ACADEMY |
| player-2274 | 12 | 5 | LCP:CFOACADEMY · DEVELOPMENT → LCP:CFOACADEMY · ACADEMY |
| player-369 | 11 | 7 | LPL:TES · FIRST_TEAM → LPL:TES · FIRST_TEAM |
| player-adizai | 12 | 0 | LCP:GZACADEMY · DEVELOPMENT → LCP:GZACADEMY · ACADEMY |
| player-akaje | 12 | 6 | LCK:KRX:DEVELOPMENT · DEVELOPMENT → LCK:KRX:DEVELOPMENT · CHALLENGERS |
| player-angel | 12 | 8 | LPL:EDG · FIRST_TEAM → 미확인 · UNAFFILIATED |
| player-apa | 9 | 5 | LCS:C9 · FIRST_TEAM → LCS:C9 · FIRST_TEAM |
| player-armao | 12 | 2 | LCS:LYON · UNCONFIRMED → 미확인 · UNAFFILIATED |
| player-attila | 12 | 4 | LEC:GXITERO · DEVELOPMENT → LEC:GXITERO · DEVELOPMENT |
| player-batuuu | 12 | 8 | LEC:THACADEMY · DEVELOPMENT → LEC:THACADEMY · ACADEMY |
| player-beryl | 12 | 28 | 미확인 · UNAFFILIATED → 미확인 · UNAFFILIATED |
| player-bluffing | 12 | 7 | LCK:HLE · FIRST_TEAM → LCK:HLE:DEVELOPMENT · CHALLENGERS |
| player-bo | 12 | 10 | 미확인 · UNAFFILIATED → 미확인 · UNAFFILIATED |
| player-boukada | 9 | 2 | LEC:SHFT · FIRST_TEAM → LEC:SHFT · FIRST_TEAM |
| player-buero | 12 | 3 | CBLOL:VKS · FIRST_TEAM → CBLOL:VKSACADEMY · ACADEMY |
| player-calix | 0 | 0 | LCK:NS · FIRST_TEAM → LCK:NS · FIRST_TEAM |
| player-callian | 12 | 2 | LCP:MVKACADEMY · DEVELOPMENT → LCP:MVKACADEMY · ACADEMY |
| player-ceo | 12 | 5 | CBLOL:VKS · FIRST_TEAM → CBLOL:VKS · FIRST_TEAM |
| player-chain | 12 | 0 | LCP:GZACADEMY · DEVELOPMENT → LCP:GZACADEMY · ACADEMY |
| player-climber | 12 | 1 | LPL:UP · FIRST_TEAM → LPL:UP · FIRST_TEAM |
| player-cloud | 12 | 4 | LCK:T1 · FIRST_TEAM → LCK:T1:DEVELOPMENT · CHALLENGERS |
| player-courage | 12 | 4 | LCK:GEN:DEVELOPMENT · DEVELOPMENT → LCK:GEN:DEVELOPMENT · CHALLENGERS |
| player-cracker | 12 | 3 | LCK:HLE · FIRST_TEAM → LCK:HLE:DEVELOPMENT · CHALLENGERS |
| player-croco | 12 | 5 | LPL:LNG · FIRST_TEAM → 미확인 · UNAFFILIATED |
| player-curty | 12 | 1 | CBLOL:FXW7 · FIRST_TEAM → CBLOL:FXW7 · FIRST_TEAM |
| player-cypher | 12 | 6 | LCK:T1:DEVELOPMENT · DEVELOPMENT → LCK:T1:DEVELOPMENT · CHALLENGERS |
| player-czajek | 12 | 5 | LEC:VITB · DEVELOPMENT → LEC:VITB · DEVELOPMENT |
| player-damocles | 12 | 2 | LCP:DFMACADEMY · DEVELOPMENT → LCP:DFMACADEMY · ACADEMY |
| player-dardoch | 4 | 6 | LCS:DIG · FIRST_TEAM → LCS:DIG · FIRST_TEAM |
| player-dawciu | 12 | 3 | LEC:VITB · DEVELOPMENT → LEC:VITB · DEVELOPMENT |
| player-daystar | 9 | 1 | LCK:BFX · FIRST_TEAM → LCK:BFX · FIRST_TEAM |
| player-ddahyuk | 12 | 1 | LCK:BRO:DEVELOPMENT · DEVELOPMENT → LCK:BRO:DEVELOPMENT · CHALLENGERS |
| player-ddoiv | 12 | 2 | LCK:DNS:DEVELOPMENT · DEVELOPMENT → LCK:DNS:DEVELOPMENT · CHALLENGERS |
| player-dekap | 12 | 4 | LEC:VITB · DEVELOPMENT → LEC:VITB · DEVELOPMENT |
| player-dinai | 12 | 1 | LCK:BRO · FIRST_TEAM → LCK:BRO:DEVELOPMENT · CHALLENGERS |
| player-dorian | 12 | 0 | LPL:WE · FIRST_TEAM → 미확인 · UNAFFILIATED |
| player-dyenn | 12 | 2 | LEC:GXITERO · DEVELOPMENT → LEC:GXITERO · DEVELOPMENT |
| player-eclipse | 1 | 1 | LCK:T1 · FIRST_TEAM → LCK:T1:DEVELOPMENT · CHALLENGERS |
| player-emoking | 12 | 0 | LCP:GZACADEMY · DEVELOPMENT → LCP:GZACADEMY · ACADEMY |
| player-empyros | 10 | 3 | LEC:NAVI · UNCONFIRMED → 미확인 · UNAFFILIATED |
| player-enosh | 11 | 2 | LCK:DNS:DEVELOPMENT · DEVELOPMENT → LCK:DNS:DEVELOPMENT · CHALLENGERS |
| player-fate | 12 | 16 | 미확인 · UNAFFILIATED → 미확인 · UNAFFILIATED |
| player-flandre | 10 | 8 | LPL:BLG · FIRST_TEAM → LPL:BLG · FIRST_TEAM |
| player-flip | 12 | 4 | LCK:DNS:DEVELOPMENT · DEVELOPMENT → LCK:DNS:DEVELOPMENT · CHALLENGERS |
| player-fofo | 12 | 27 | 미확인 · UNAFFILIATED → 미확인 · UNAFFILIATED |
| player-fresskowy | 12 | 11 | LEC:MKOIFENIX · DEVELOPMENT → LEC:MKOIFENIX · ACADEMY |
| player-frosty | 12 | 7 | CBLOL:RED · FIRST_TEAM → CBLOL:RED · FIRST_TEAM |
| player-garden | 12 | 5 | LCK:DK:DEVELOPMENT · DEVELOPMENT → LCK:DK:DEVELOPMENT · CHALLENGERS |
| player-gatovisck | 12 | 6 | CBLOL:PNGACADEMY · DEVELOPMENT → CBLOL:PNGACADEMY · ACADEMY |
| player-ghost | 8 | 2 | LCK:KT:DEVELOPMENT · DEVELOPMENT → LCK:KT:DEVELOPMENT · CHALLENGERS |
| player-grit | 12 | 2 | LCP:DFMACADEMY · DEVELOPMENT → LCP:DFMACADEMY · ACADEMY |
| player-grizzly | 12 | 3 | LCK:BFX:DEVELOPMENT · DEVELOPMENT → LCK:BFX:DEVELOPMENT · CHALLENGERS |
| player-guardian | 0 | 1 | LCK:T1:DEVELOPMENT · DEVELOPMENT → LCK:T1:DEVELOPMENT · CHALLENGERS |
| player-guti | 12 | 11 | LCK:T1:DEVELOPMENT · DEVELOPMENT → LCK:T1:DEVELOPMENT · CHALLENGERS |
| player-haetae | 12 | 4 | LCK:T1:DEVELOPMENT · DEVELOPMENT → LCK:T1:DEVELOPMENT · CHALLENGERS |
| player-haichao | 2 | 3 | LPL:OMG · FIRST_TEAM → LPL:OMG · FIRST_TEAM |
| player-hang | 12 | 2 | LPL:WBG · FIRST_TEAM → 미확인 · UNAFFILIATED |
| player-hauz | 12 | 1 | CBLOL:FXW7 · FIRST_TEAM → CBLOL:FXW7 · FIRST_TEAM |
| player-hazel | 12 | 3 | LEC:KCB · DEVELOPMENT → LEC:KCB · DEVELOPMENT |
| player-hery | 4 | 3 | LPL:OMG · FIRST_TEAM → LPL:OMG · FIRST_TEAM |
| player-hwichan | 12 | 5 | LCK:KT:DEVELOPMENT · DEVELOPMENT → LCK:KT:DEVELOPMENT · CHALLENGERS |
| player-hy | 12 | 1 | LCP:MVKACADEMY · DEVELOPMENT → LCP:MVKACADEMY · ACADEMY |
| player-jackal | 12 | 1 | LCK:HLE · FIRST_TEAM → LCK:HLE:DEVELOPMENT · CHALLENGERS |
| player-jaehyuk | 12 | 4 | LCK:DK:DEVELOPMENT · DEVELOPMENT → LCK:DK:DEVELOPMENT · CHALLENGERS |
| player-janus | 12 | 2 | LCK:NS:DEVELOPMENT · DEVELOPMENT → LCK:NS:DEVELOPMENT · CHALLENGERS |
| player-jinbeom | 12 | 2 | LCK:T1:DEVELOPMENT · DEVELOPMENT → LCK:T1:DEVELOPMENT · CHALLENGERS |
| player-jiwoo | 11 | 2 | LCK:KT · FIRST_TEAM → LCK:KT · FIRST_TEAM |
| player-jmz | 12 | 1 | CBLOL:RED · FIRST_TEAM → CBLOL:REDACADEMY · ACADEMY |
| player-juhan | 7 | 3 | LPL:OMG · FIRST_TEAM → LPL:OMG · FIRST_TEAM |
| player-jwei | 6 | 4 | LPL:IG · FIRST_TEAM → LPL:WBG · FIRST_TEAM |
| player-kamiloo | 12 | 5 | LEC:KCB · DEVELOPMENT → LEC:KCB · DEVELOPMENT |
| player-kangin | 12 | 2 | LCK:BFX:DEVELOPMENT · DEVELOPMENT → LCK:BFX:DEVELOPMENT · CHALLENGERS |
| player-kemish | 12 | 2 | LCK:GEN · FIRST_TEAM → LCK:GEN:DEVELOPMENT · CHALLENGERS |
| player-kurahuto | 12 | 1 | LCP:DFMACADEMY · DEVELOPMENT → LCP:DFMACADEMY · ACADEMY |
| player-kuri | 12 | 2 | CBLOL:PNG · FIRST_TEAM → 미확인 · UNAFFILIATED |
| player-lancer | 12 | 5 | LCK:DNS:DEVELOPMENT · DEVELOPMENT → LCK:DNS:DEVELOPMENT · CHALLENGERS |
| player-larssen | 12 | 4 | LEC:NAVI · FIRST_TEAM → LEC:NAVI · FIRST_TEAM |
| player-lazyfeel | 12 | 2 | LCK:KRX:DEVELOPMENT · DEVELOPMENT → LCK:KRX · FIRST_TEAM |
| player-liangchen | 8 | 1 | LPL:UP · FIRST_TEAM → LPL:BLGJ · DEVELOPMENT |
| player-lider | 3 | 5 | LEC:SK · FIRST_TEAM → LEC:SK · FIRST_TEAM |
| player-life | 7 | 4 | LCK:DNS · FIRST_TEAM → LCK:DNS · FIRST_TEAM |
| player-loopy | 12 | 3 | LCK:DK:DEVELOPMENT · DEVELOPMENT → LCK:DK:DEVELOPMENT · CHALLENGERS |
| player-lot | 12 | 3 | LEC:GX · FIRST_TEAM → LEC:GX · FIRST_TEAM |
| player-lucy | 12 | 4 | LCK:NS · FIRST_TEAM → LCK:NS:DEVELOPMENT · CHALLENGERS |
| player-lumos | 12 | 0 | LCK:GEN:DEVELOPMENT · DEVELOPMENT → LCK:GEN:DEVELOPMENT · CHALLENGERS |
| player-luon | 12 | 5 | LCK:BFX:DEVELOPMENT · DEVELOPMENT → LCK:BFX:DEVELOPMENT · CHALLENGERS |
| player-lure | 12 | 7 | LEC:THACADEMY · DEVELOPMENT → LEC:THACADEMY · ACADEMY |
| player-lurox | 12 | 8 | LEC:THACADEMY · DEVELOPMENT → LEC:THACADEMY · ACADEMY |
| player-mago | 12 | 1 | CBLOL:LOUD · FIRST_TEAM → 미확인 · UNAFFILIATED |
| player-markoon | 7 | 7 | LEC:G2NORD · DEVELOPMENT → LEC:G2NORD · DEVELOPMENT |
| player-marvin | 12 | 3 | CBLOL:PNG · FIRST_TEAM → CBLOL:PNGACADEMY · ACADEMY |
| player-mercy9 | 12 | 8 | LEC:THACADEMY · DEVELOPMENT → LEC:THACADEMY · ACADEMY |
| player-mg | 12 | 7 | LCK:BFX:DEVELOPMENT · DEVELOPMENT → LCK:BFX:DEVELOPMENT · CHALLENGERS |
| player-mihawk | 12 | 5 | LCK:NS:DEVELOPMENT · DEVELOPMENT → LCK:NS:DEVELOPMENT · CHALLENGERS |
| player-minous | 12 | 4 | LCK:KRX:DEVELOPMENT · DEVELOPMENT → LCK:KRX:DEVELOPMENT · CHALLENGERS |
| player-missing | 8 | 10 | LPL:LNG · FIRST_TEAM → LPL:LNG · FIRST_TEAM |
| player-mudai | 12 | 1 | LCK:GEN · FIRST_TEAM → LCK:GEN:DEVELOPMENT · CHALLENGERS |
| player-myrtus | 12 | 8 | LEC:MKOIFENIX · DEVELOPMENT → LEC:MKOIFENIX · ACADEMY |
| player-namiru | 12 | 5 | CBLOL:PNG · FIRST_TEAM → CBLOL:PNG · FIRST_TEAM |
| player-nanashi | 12 | 1 | CBLOL:RED · FIRST_TEAM → CBLOL:REDACADEMY · ACADEMY |
| player-nevid | 0 | 0 | LCK:DK:DEVELOPMENT · DEVELOPMENT → LCK:DK:DEVELOPMENT · CHALLENGERS |
| player-nightslayer | 12 | 6 | LEC:MKOI · FIRST_TEAM → LEC:MKOIFENIX · DEVELOPMENT |
| player-noah | 2 | 4 | LEC:GX · FIRST_TEAM → LEC:GX · FIRST_TEAM |
| player-npc | 12 | 2 | LCP:MVKACADEMY · DEVELOPMENT → LCP:MVKACADEMY · ACADEMY |
| player-nukenin | 12 | 2 | CBLOL:RED · FIRST_TEAM → CBLOL:REDACADEMY · ACADEMY |
| player-nuna | 12 | 3 | LCP:MVKACADEMY · DEVELOPMENT → LCP:MVKACADEMY · ACADEMY |
| player-oddeye | 12 | 5 | LCK:BRO · FIRST_TEAM → LCK:BRO:DEVELOPMENT · CHALLENGERS |
| player-painter | 12 | 5 | LCK:T1:DEVELOPMENT · DEVELOPMENT → LCK:T1:DEVELOPMENT · CHALLENGERS |
| player-panther | 12 | 4 | LCK:HLE:DEVELOPMENT · DEVELOPMENT → LCK:HLE:DEVELOPMENT · CHALLENGERS |
| player-papiteero | 12 | 6 | LEC:THACADEMY · DEVELOPMENT → LEC:THACADEMY · ACADEMY |
| player-peanutcoco | 12 | 2 | LCP:GZACADEMY · DEVELOPMENT → LCP:GZACADEMY · ACADEMY |
| player-planb | 12 | 4 | LCK:BRO · FIRST_TEAM → LCK:BRO:DEVELOPMENT · CHALLENGERS |
| player-pleata | 12 | 4 | LCK:NS:DEVELOPMENT · DEVELOPMENT → LCK:NS:DEVELOPMENT · CHALLENGERS |
| player-pollu | 6 | 3 | LCK:KT · FIRST_TEAM → LCK:KT · FIRST_TEAM |
| player-potent | 12 | 2 | LEC:VITB · DEVELOPMENT → LEC:VITB · DEVELOPMENT |
| player-potential | 12 | 0 | LCP:DFMACADEMY · DEVELOPMENT → LCP:DFMACADEMY · ACADEMY |
| player-prime | 12 | 2 | LEC:KCB · DEVELOPMENT → LEC:KCB · DEVELOPMENT |
| player-prodelta | 12 | 3 | CBLOL:FXW7 · FIRST_TEAM → CBLOL:FXW7 · FIRST_TEAM |
| player-pungyeon | 12 | 2 | LCK:BRO · FIRST_TEAM → LCK:BRO · FIRST_TEAM |
| player-pyeonsik | 12 | 3 | LCK:HLE:DEVELOPMENT · DEVELOPMENT → LCK:HLE:DEVELOPMENT · CHALLENGERS |
| player-pyosik | 3 | 7 | LCK:DNS · FIRST_TEAM → LCK:DNS · FIRST_TEAM |
| player-qats | 12 | 4 | CBLOL:VKS · FIRST_TEAM → CBLOL:VKSACADEMY · ACADEMY |
| player-quantum | 11 | 5 | LCK:DNS · FIRST_TEAM → LCK:DNS:DEVELOPMENT · CHALLENGERS |
| player-ravvy | 12 | 2 | LCP:DFMACADEMY · DEVELOPMENT → LCP:DFMACADEMY · ACADEMY |
| player-rayito | 12 | 1 | LEC:GXITERO · DEVELOPMENT → LEC:GXITERO · DEVELOPMENT |
| player-re0 | 4 | 2 | LPL:OMG · FIRST_TEAM → LPL:OMG · FIRST_TEAM |
| player-renard | 9 | 2 | LPL:IG · FIRST_TEAM → LPL:IG · FIRST_TEAM |
| player-renye | 12 | 3 | LCP:GZACADEMY · UNCONFIRMED → LCP:GZACADEMY · ACADEMY |
| player-rich | 7 | 2 | LCK:KRX · FIRST_TEAM → LCK:KRX · FIRST_TEAM |
| player-rin | 12 | 7 | LEC:G2NORD · DEVELOPMENT → LEC:G2NORD · DEVELOPMENT |
| player-ripple | 12 | 2 | LCK:GEN:DEVELOPMENT · DEVELOPMENT → LCK:GEN:DEVELOPMENT · CHALLENGERS |
| player-ryan3 | 10 | 0 | LPL:TT · FIRST_TEAM → LPL:TT · FIRST_TEAM |
| player-saber | 12 | 3 | LPL:UP · FIRST_TEAM → LPL:UP · FIRST_TEAM |
| player-sabisu | 12 | 0 | LCP:GZACADEMY · DEVELOPMENT → LCP:GZACADEMY · ACADEMY |
| player-samkz | 12 | 2 | CBLOL:PNG · FIRST_TEAM → CBLOL:PNGACADEMY · ACADEMY |
| player-sansan | 12 | 2 | LCP:MVK · FIRST_TEAM → LCP:MVK · FIRST_TEAM |
| player-sarolu | 12 | 4 | CBLOL:VKS · FIRST_TEAM → CBLOL:VKSACADEMY · ACADEMY |
| player-sasi | 12 | 4 | LPL:LGD · FIRST_TEAM → LPL:UP · FIRST_TEAM |
| player-seany | 12 | 3 | LCP:MVKACADEMY · DEVELOPMENT → LCP:MVKACADEMY · ACADEMY |
| player-sero | 12 | 1 | LCK:KT:DEVELOPMENT · DEVELOPMENT → LCK:KT:DEVELOPMENT · CHALLENGERS |
| player-setab | 12 | 7 | LCK:NS:DEVELOPMENT · DEVELOPMENT → LCK:NS:DEVELOPMENT · CHALLENGERS |
| player-shelfmade | 12 | 4 | LEC:G2NORD · DEVELOPMENT → LEC:G2NORD · DEVELOPMENT |
| player-siriuss | 12 | 0 | LCK:GEN:DEVELOPMENT · DEVELOPMENT → LCK:GEN:DEVELOPMENT · CHALLENGERS |
| player-slayer | 12 | 4 | LCK:BFX:DEVELOPMENT · DEVELOPMENT → LCK:BFX:DEVELOPMENT · CHALLENGERS |
| player-smooth | 12 | 0 | CBLOL:RED · FIRST_TEAM → CBLOL:REDACADEMY · ACADEMY |
| player-solid | 12 | 3 | LCK:DK · FIRST_TEAM → LCK:DK:DEVELOPMENT · CHALLENGERS |
| player-srantonio | 12 | 0 | LEC:MKOI · FIRST_TEAM → 미확인 · UNAFFILIATED |
| player-starry | 5 | 2 | LPL:OMG · FIRST_TEAM → LPL:OMG · FIRST_TEAM |
| player-steller | 12 | 4 | LCP:MVKACADEMY · DEVELOPMENT → LCP:MVKACADEMY · ACADEMY |
| player-supercleber | 12 | 3 | CBLOL:VKS · FIRST_TEAM → CBLOL:VKSACADEMY · ACADEMY |
| player-sylvie | 12 | 2 | LCK:KT:DEVELOPMENT · DEVELOPMENT → LCK:KT:DEVELOPMENT · CHALLENGERS |
| player-tao | 12 | 1 | LEC:KCB · DEVELOPMENT → LEC:KCB · DEVELOPMENT |
| player-tempester | 12 | 2 | LCK:BRO:DEVELOPMENT · DEVELOPMENT → LCK:BRO:DEVELOPMENT · CHALLENGERS |
| player-th3antonio | 12 | 1 | LEC:GXITERO · DEVELOPMENT → LEC:GXITERO · DEVELOPMENT |
| player-time | 12 | 4 | LEC:GXITERO · DEVELOPMENT → LEC:GXITERO · ACADEMY |
| player-toasty | 12 | 11 | LEC:G2NORD · DEVELOPMENT → LEC:G2NORD · DEVELOPMENT |
| player-tockimo | 12 | 7 | LEC:G2NORD · DEVELOPMENT → LEC:G2NORD · DEVELOPMENT |
| player-tracy | 12 | 5 | LCP:CFOACADEMY · DEVELOPMENT → LCP:CFOACADEMY · ACADEMY |
| player-trigger | 12 | 4 | CBLOL:PNG · FIRST_TEAM → 미확인 · UNAFFILIATED |
| player-trymbi | 4 | 7 | LEC:SHFT · FIRST_TEAM → LEC:SHFT · FIRST_TEAM |
| player-umi | 12 | 1 | LCP:MVKACADEMY · DEVELOPMENT → LCP:MVKACADEMY · ACADEMY |
| player-valiant | 6 | 0 | LCK:HLE:DEVELOPMENT · DEVELOPMENT → LCK:HLE:DEVELOPMENT · CHALLENGERS |
| player-vincenzo | 12 | 0 | LCK:KRX · FIRST_TEAM → LCK:KRX · FIRST_TEAM |
| player-vizzpers | 12 | 1 | LEC:VITB · DEVELOPMENT → LEC:VITB · DEVELOPMENT |
| player-wayne | 12 | 4 | LCK:DK:DEVELOPMENT · DEVELOPMENT → LCK:DK:DEVELOPMENT · CHALLENGERS |
| player-wenbo | 12 | 3 | LPL:BLG · FIRST_TEAM → LPL:BLGJ · DEVELOPMENT |
| player-winner | 12 | 1 | LCK:KRX:DEVELOPMENT · DEVELOPMENT → LCK:KRX:DEVELOPMENT · CHALLENGERS |
| player-xiaoxia | 12 | 2 | LPL:UP · FIRST_TEAM → LPL:UP · FIRST_TEAM |
| player-xiaoxiang | 12 | 0 | LCP:DCG · FIRST_TEAM → LCP:DCG · FIRST_TEAM |
| player-xin | 12 | 0 | LCP:CFOACADEMY · DEVELOPMENT → LCP:CFOACADEMY · ACADEMY |
| player-xns | 12 | 5 | LEC:MKOIFENIX · DEVELOPMENT → LEC:MKOIFENIX · ACADEMY |
| player-yakkey | 12 | 5 | LEC:VITB · DEVELOPMENT → LEC:VITB · DEVELOPMENT |
| player-yuhe | 12 | 2 | LCP:CFOACADEMY · DEVELOPMENT → LCP:CFOACADEMY · ACADEMY |
| player-yukino | 12 | 4 | LEC:KCB · DEVELOPMENT → LEC:KCB · DEVELOPMENT |
| player-zay | 12 | 6 | CBLOL:VKS · FIRST_TEAM → CBLOL:VKSACADEMY · ACADEMY |
| player-zephyr | 12 | 2 | LCK:BFX:DEVELOPMENT · DEVELOPMENT → LCK:BFX:DEVELOPMENT · CHALLENGERS |
| player-zkai | 12 | 3 | LCP:CFOACADEMY · DEVELOPMENT → LCP:CFOACADEMY · ACADEMY |
| player-zoen | 12 | 4 | CBLOL:PNGACADEMY · DEVELOPMENT → CBLOL:PNGACADEMY · ACADEMY |
| player-zven | 7 | 6 | LCS:C9 · FIRST_TEAM → LCS:C9 · FIRST_TEAM |
