# Career 계약·스토브·FA 시장·56개 구단 경쟁 V1

2026-09-06 구현. 시작 HEAD: `41e278ec1819fb29e45c35e7134e0cf66434d4e6`.
원본 `선수정보/`, `능력치/`, 일정 자료, `prompts/`와 460명/79조직/56경쟁 팀을 보존한다. 아래 수치는 **게임 정책**이며 현실 연봉·계약 규정·선수 성격의 확인 결과가 아니다.

## 사용 흐름

Career 저장을 선택하면 기존 선수 명부 아래에서 계약과 FA 시장을 연다. 명부에서도 게임 계약 종료일·협상 가능 여부를 확인하고 해당 선수의 계약 관리로 이동한다. 시장은 FA/관리 구단/만료 예정/전체 검색을 제공한다.

1. 선수의 현재 조건·영입 가능일·게임 의향을 확인한다.
2. 시작일, 1–3년 기간, 연봉, 계약금, 주전/후보/육성 역할을 제안한다. API의 허용 기간은 6개월–3년이다.
3. Calendar로 응답일에 진행해 수정 요구·거절·실제 경쟁 제안을 확인한다. 원본 제안 수정·철회가 가능하다.
4. 결정일에 선수가 유효한 제안들을 비교한다. 계약금은 한 번 지급되고, 미래 계약은 예정 입단으로 남는다.
5. 현재 입단한 선수는 명부의 후보에 들어온다. 사용자가 직접 선발을 선택해야 한다.
6. 국내 새 Series에는 현재 계약·선발을 적용한다. 국제대회는 현재 자격에 더해 등록 후보 또는 적법한 보충등록을 요구한다.
7. 필수 대회를 끝내고 `대회 마감 · 스토브 진입`을 누르면 마감 명단/시장/결과를 보존한다. 오프시즌 명부와 시장을 운영하고 12월 31일부터 다음 시즌에 진입한다.

방출 화면은 계약, 해지 비용, 미정산 급여 지급, 선발 공백/대체 선수와 진행 중 경기 보존을 표시한다. 자연 만료에는 해지 비용이 없다. 사용자 팀은 AI가 대신 영입·재계약·방출하거나 선발하지 않는다.

## 초기화와 계약 정책

`CareerMarketPolicy.VERSION = CAREER_CONTRACT_MARKET_GAME_POLICY_V1`, 금액 단위는 정수 `GAME_CREDITS`다.

- 공개 조사 `detailsJson.contract`는 그대로 보존한다. 일치하는 소속의 사용 가능한 공개 종료일은 첫 Career 시즌과 조사 기준 2026년의 차이만큼 이동한다. 예를 들어 APA의 2028년 종료는 첫 시즌 2027 Career에서 2029년이 된다.
- 미확인·충돌 정보에는 게임 초기 계약을 부여한다. 현재 날짜에서 최소 60일의 보호가 없으면 현재/다음 해 11월 30일로 보호한다. 이미 지난 자료를 소급 집행하여 현재 선수단을 해체하지 않는다.
- 게임 연봉 기준은 기존 12개 능력치 합 × 1,000이다. 능력치나 숙련도를 수정하지 않는다.
- 팀 예산은 초기 연봉 합의 160%, 최소 1,200,000이다. 초기 잔액은 연간 예산의 2배, 이후 매년 1월 1일 같은 연간 예산을 한 번 배정한다.
- 선수 보유 상한은 최소 10명 또는 기존 보유 인원+2다. 계약/제안 효력이 겹치는 모든 시작 경계에서 상한과 연봉 예산을 검사한다.
- Bo/BeryL/FoFo/FATE는 이 Career에서 계약 가능한 초기 FA다. 자료의 현실 영입 가능 미확인 표시는 유지한다. UP/OMG 같은 비경쟁 조직 소속과 소속 미확인 3명은 자동 FA가 아니다.
- 현재 계약 종료 60일 전부터 재계약/미래 FA 협상을 허용한다. 새 계약은 기존 종료 다음 날부터 시작한다. 이미 확정된 미래 계약을 덮어쓰는 재협상·중도 해지는 V1에서 거절한다.
- 승격·강등은 동일 고용 관계의 배치 변경이며 계약금/급여를 새로 만들지 않는다.

초기화는 create/startup recovery에서만 실행한다. GET은 초기화·AI 제안·결정·지급을 하지 않는다. 기존 활성 저장만 명시적으로 이주하며 재실행은 진행 중 제안과 사용자 선발을 보존한다.

## 제안 비교와 선수 판단

선수별 게임 선호는 Career seed와 player ID에서 결정적으로 만들고 저장한다. 가중치는 보수/기회/전력/안정성/익숙함 순서로 다음과 같다.

| 게임 의향 | 가중치 |
| --- | --- |
| 여러 제안 비교 | 40 / 25 / 20 / 10 / 5 |
| 출전 기회 우선 | 30 / 35 / 20 / 10 / 5 |
| 재계약 우선 검토 | 30 / 25 / 20 / 10 / 15 |

보수 점수는 요구 기준의 100%에서 약 80점, 125%에서 100점으로 포화한다. 계약금도 기간에 맞춰 반영한다. 주전 기회는 약속 외에 동일 포지션의 유효 경쟁 선수 수와 능력치를 반영한다. 후보는 50, 육성은 35점이다. 팀 전력은 **현재 선택 선발 능력치 평균**의 대체 지표이며 미구현 해외 순위나 출전 기록을 만들어 쓰지 않는다. 안정성은 최대 3년 기간, 익숙함은 현재 고용 관계를 사용한다. 초기 소속 지역과 다른 지역에는 선수별 게임 이동 부담 3–10점을 적용하며 국적/현실 성격으로 추론하지 않는다.

응답은 제출 2일 후, 결정은 기본 5일 후다. 같은 선수의 열린 결정 기간에 들어온 제안은 같은 결정일에 비교한다. 연봉이 요구의 75% 미만이면 조기 거절한다. 100% 미만이거나 평가가 기준에 못 미치면 105% 수준의 보수 수정을 요구한다. 제안은 최초 제출을 포함해 최대 3회차다. 수정 요구에 응답하지 않은 조건은 자동 동의로 바뀌지 않으며 결정 다음 날 만료된다. 수락 기준은 6,200이다. 거절되어도 현재 계약은 유지된다.

점수 차가 10 이내인 유사 조건에만 0–10의 작은 결정적 동률 값을 더한다. 값은 seed/선수/결정일/팀에서 도출한다. 경기 Random, 현실 시간, UUID, 화면 조회 횟수로 재추첨하지 않는다. 평가와 실제 비교 제안, 결과, 정책 버전을 저장한다.

## AI 수요와 재정

관리 팀 외 55개 경쟁 팀은 월요일에 같은 시장 상태에서 포지션별 부족·만료 예정·현재 대비 개선을 평가한다. 보유 선수/육성 활용을 먼저 고려하고 포지션별 후보를 최대 3명으로 제한한다. 460명×56팀을 매 GET이나 경기 tick마다 평가하지 않는다. 포지션이 안전한 팀은 보유 2명 미만과 능력치 합 12점 초과 개선 조건에서만 검토한다.

AI 기본 연봉은 요구의 105–125%, 최대 카운터 예산은 135%, 기간 2년, 계약금은 요구 연봉의 10%다. 실제 선수 평가에서 거절될 수 있다. 실패한 팀은 이후 사건에서 다른 후보를 찾는다. 모든 팀의 제안·수락은 같은 계약금 잔액, 기간별 연봉 예산, 보유 상한 검사를 거친다. AI에 무료 계약이나 실패 시 임의 현금 보충은 없다.

열린 제안은 계약금과 해당 기간 연봉을 예약한다. 철회·거절·만료는 예약을 해제하며 수락 시 실제 계약/계약금으로 바뀐다. 같은 선수의 충돌 제안은 함께 종료된다. 현재 계약과 예약 재계약은 겹치지 않는 기간으로 계산한다. 급여는 월말에 지급하고 만료/방출 시 미정산분을 지급한다. 각 연도의 365/366일 기준 누적 정수 몫 차이를 사용하므로 하루씩 정산한 합과 같은 기간 정산이 일치한다. 해지 비용은 남은 일할 급여의 25%다.

## 저장과 날짜 경계

V16 migration은 Career 시장 상태, 원본 시장 명령 영수증, 시즌 시장 마감, 등록 보충 네 테이블을 추가한다. 계약/제안/재정/결정은 Career 소유 JSON snapshot이며 상태/영수증 hash와 revision을 검사한다. 거래 작업은 단기 `CareerMarketEngine`에서 수행하며 resolver/global mutable state를 사용하지 않는다.

시장·명부·등록·경기 시작은 Calendar row를 먼저 잠근다. 기존 Calendar/시즌 전환은 기존 전역 명령 잠금 → Calendar row 순서를 유지한다. 시장 명령의 스토브 진입은 Calendar row 이후 전역 잠금을 다시 획득하지 않는다. 계약/소속/금액/영수증은 같은 transaction에서 저장한다. 명령 재전송은 Career+UUID와 원본 payload를 확인하고 원래 receipt를 반환한다. 다른 payload는 충돌이다. 새 운영 명령만 `career_save.updated_at`을 갱신하며 GET/원본 결과 재조회는 갱신하지 않는다.

날짜 처리 순서는 연간 배정 → 기존 계약 종료/급여 정산 → 예약 계약 활성화 → 월말 급여 → 만료 예고 → 선수 응답/AI 수정 → 월요일 AI 제안 → 선수 결정 → 기한 만료 → AI 선발이다. 종료일 당일까지 유효하고 다음 날 만료된다. 같은 팀 예약 재계약의 기존 사용자 선발은 원자적 종료/활성화 중 유지한다. 타 팀 계약은 새 시작일까지 소속을 바꾸지 않는다.

Calendar 다음 사건은 기존 경기/대회 날짜와 시장 날짜 중 가장 이른 날짜다. 날짜 점프는 사이의 사건을 순서대로 한 번 처리한다. 선발이나 등록이 부족하면 `ROSTER_REPAIR_REQUIRED`로 경기 시작을 막고 시장 날짜 진행과 복구 명령은 허용한다. 참가 등록 자체가 부족해 대진이 아직 없을 때도 같은 복구 경로를 제공한다.

스토브 시작 시 마감 snapshot을 남기고 현재 운영 명단은 계속 변경한다. 다음 해 1월 1일 사건을 처리한 뒤 새 시즌에 현재 운영 상태를 이월하며, 과거 시즌 명부는 스토브 진입 때 봉인한 명부로 보존한다. provisioning의 기존 불변 자료와 실제 경기의 현재 명단은 책임을 분리한다. 두 번째 시즌에도 새 연도 예산/협상 identity가 사용된다.

## 출전과 API/UI

운영 명단 `CAREER_OPERATING_ROSTER_V2` / `CAREER_ROSTER_VIEW_V2`는 0–5개의 서로 다른 포지션 선발을 허용한다. 구형 V1의 5명 의미는 바꾸지 않는다. 실제 새 경기는 해당 두 팀의 유효한 5명만 요구한다. 다른 팀의 공백 때문에 전체 시장/명부 조회를 실패시키지 않는다.

국제 등록 후보와 원래 5명 fallback 모두 현재 계약·소속 자격을 검사한다. 일반 영입은 다음 등록부터 적용한다. 해당 포지션의 유효 등록 선수가 모두 없어진 경우에만 현재 적법한 동일 포지션 1군 선수를 명시적 `SUPPLEMENT`로 추가한다. 원본 등록은 보존하고 사유/날짜/revision을 별도 기록한다. AI도 같은 조건으로 보충한다. 실제 대회 규정이 아닌 진행 복구 게임 정책이다.

이미 binding이 있는 Player/Auto는 기존 선수·능력치·Draft·Fearless·seed·checkpoint·receipt를 유지한다. 새 국내 League/Career와 국제 경기는 현재 자격을 확인한 뒤 불변 입력을 만든다. 시장은 경기 Random이나 엔진 밸런스를 변경하지 않는다.

- `GET /api/v1/careers/{careerId}/market/{year}`: `CAREER_MARKET_VIEW_V1`, 현재/과거 계약, 제안, 56팀 재정, 결과, 공백, 등록 보충, 허용 명령.
- `POST /api/v1/careers/{careerId}/market`: `CAREER_MARKET_COMMAND_V1`, `sourceYear`, `expectedRevision`, `action`, `playerId`, `offerId`, `terms`, `replacementPlayerId`, `competitionId`, `clientCommandId`. 관리 팀은 서버가 결정한다.
- 액션: `SUBMIT`, `REVISE`, `WITHDRAW`, `RELEASE`, `SUPPLEMENT`, `OPEN_STOVE`.
- 프런트는 기존 공유 mutation gate와 Career/연도 범위를 사용한다. 모호한 응답은 sessionStorage의 원본 요청을 보존하고 새 UUID 대신 같은 요청을 재조회한다.

주요 소스: `CareerMarketPolicy`(수치), `CareerMarketState`(저장 모델), `CareerMarketEngine`(판단/재정/사건), `CareerMarketStore`(원자 저장/읽기/권한), `CareerRosterStore`(운영/출전), Calendar/Season/International(날짜/마감/등록), `CareerMarketApiV1Controller`(HTTP), `CareerMarketPanel`과 기존 Career 패널/API contract(화면).

## 검증 기록

기존 2,008건의 회귀 결과를 이번 검증으로 재사용하지 않는다. 아래 기록은 이번 작업에서 실제 실행한 범위다.

확인된 수정 사항: 해외 등록 원본 fallback의 이탈 선수 차단, 요청 안의 계약 자격 읽기 재사용, 응답 없는 counter 만료, 동일 팀 예약 재계약 선발 유지, 기간 전체 선수 상한, 시즌 종료/등록 복구 중 시장 진행 프런트 검증, 실제 시계가 뒤로 보정되어도 캘린더 영수증 완료 시각이 생성 시각보다 작아지지 않는 처리.

## 제한

현실 선수 의사·연봉·계약법/등록 규정을 확정하지 않는다. 유료 이적·임대·바이아웃·미래 확정 계약 재협상·상세 구단 재정·해외 전체 리그·능력치 재평가·장기 불만/은퇴 등은 구현하지 않았다. 초기 예산/선호/AI 빈도는 검증된 밸런스가 아니다. 반복 시즌 준비에는 기존 합성 결과 helper를 사용하며 수년 치 모든 실제 경기를 완주했다고 주장하지 않는다.

### 실제 협상 사례

격리된 단위 fixture에서 원본 HLE 소속 Zeus를 정상 방출한 후 T1·GEN·BLG가 실제 공통 `submit` 경로로 제안했다. 요구 기준 215,000에 T1은 연봉 301,000/계약금 30,100, GEN·BLG는 268,750/26,875, 모두 2년/주전 조건이다. 평가 점수는 T1 8,280, GEN 7,290, BLG 6,600으로 T1이 선택됐다. 보수는 모두 포화했지만 기회 점수 80/50/40과 이동 부담 등의 차이가 반영됐다. 같은 제안 집합을 역순으로 제출해도 결정/계약이 같았다. T1이 요구의 85%만 제안하고 counter에 응답하지 않은 별도 사례에서는 GEN이 영입했고 T1 예약금은 해제됐다. 원본 선수 자료의 Zeus 소속/계약은 바꾸지 않았다.

사용자 미참여 fixture에서는 HLE·GEN의 TOP 공백을 정상 방출로 준비했다. 같은 30일 주기에 2027-01-16 GEN이 Kiin을 선택받았고, 패한 HLE는 다음 후보 Zeus와 01-23 계약했다. 두 팀은 선발 5명을 복구했다. 56개 계정/명부를 같은 시장에 연결했으며 LPL/LEC/LCS/LCP/CBLOL 제안도 실제 비교 기록에 포함됐다. 날짜 점프와 하루 진행의 최종 상태가 같았다.

예약 재계약 단위 fixture는 HLE Zeus의 기존 계약에 5일이 남도록 준비하고 요구 연봉의 140%·주전 조건을 제출한다. 5일 뒤 수락해도 종료일 당일에는 이전 계약이 ACTIVE이고 새 계약은 예약 상태다. 6일째에 새 계약으로 바뀌며 FA가 되거나 기존 사용자 선발에서 빠지지 않는다. 이전 계약의 급여는 기존 종료일까지 한 번 정산하고 같은 날짜 처리를 반복해도 계약/재정 상태가 같다. 이는 격리된 게임 계약 fixture이며 공개 조사 계약을 수정한 사례가 아니다.

실제 브라우저 저장은 기존 합성 경기 완료 helper로 2027 필수 대회 결과를 준비한 뒤 운영했다. 화면의 스토브 진입과 시장/Calendar/명부/시즌 명령은 실제 HTTP였다.

- 10-15 KT가 Cuzz를 방출: 해지 비용 57,791과 미정산 급여 지급, JUNGLE 공백 저장.
- Bo에게 142,800 연봉/10,000 계약금/2년/주전 조건을 제안. 10-17 선수는 176,400 수준의 보수 수정을 요구.
- KT는 252,000 연봉으로 수정. 서버가 수정안을 저장한 후 브라우저 응답만 abort했다. 새로고침 후 원본 UUID `7b712ad1-bf66-4ec6-a773-0307464cea13`를 재요청하여 `replayed=true`, 원래 resulting revision 5의 receipt를 받았다.
- Bo는 10-20 수락/입단. 계약금은 10,000 한 번, 10월 급여 8,284, 11월 20,713, 12월 21,403. 사용자가 선발을 선택한 뒤 PerfecT/Bo/Bdd/FenRir/Effort 5명이 됐다.
- 방출된 Cuzz에는 해외 AI 12팀이 실제로 제안했다. KT도 170,400/후보 조건으로 같은 결정 기간에 참여했지만 수정 요구에 응답하지 않았다. 10-23 Cuzz는 LEC:SK의 266,250 연봉/21,300 계약금/2년/후보 조건을 선택했다. SK 평가 6,510, KT 6,070이며 KT 제안은 거절되고 예약이 해제됐다. Bo에 AI 경쟁이 없었던 기간을 경쟁 중이라고 표시하지 않았다.

선발 복구가 연말을 넘겨야 하는 예외에서는 미완료 경기를 보존하면서 시장 사건을 계속 진행한다. 늦게 마감한 시즌의 새해 진입일은 이미 진행한 날짜보다 과거가 될 수 없다. 집중 검증은 두 번째 마감의 날짜를 2029-01-05로 준비해 이월 날짜와 1월 1일 지급의 중복 방지를 확인한다.


### 집중/프런트 결과

- `./gradlew test --tests 'com.lolfm.career.CareerMarketEngineTest' --tests 'com.lolfm.league.CareerModePersistenceTest.marketCommands*' --console=plain --no-daemon`: 11건 통과, 1분 48초.
- `CareerMarketEngineTest` 강화 후 10건 통과: 사용자/AI 경쟁과 AI 패배 후 대안 영입, 날짜 점프/하루 진행 동등성 포함.
- `CareerDomesticExecutionTest.oldInternational*`: 현재 계약 없는 국제 fallback 차단, 보충등록, 기존 binding 보존 통과.
- `LeagueAutomatedSeriesRunnerProductionV9Test.selectedReserve*`: 실제 Production V9 Auto BO3의 모든 게임 입력에 Jiwoo와 신규 계약 Bo 포함, FenRir/Cuzz 제외 및 receipt 검증 통과.
- `./gradlew test --tests 'com.lolfm.league.CareerModePersistenceTest.market*' --tests 'com.lolfm.application.CareerDomesticExecutionTest.oldInternational*' --console=plain --no-daemon`: 3건 통과, 4분 43초. DB 실패 후 전체 롤백, 동시 제안 한 건 성공, 다른 Career 격리, 원본 replay/재시작, 두 번의 시즌 전환 포함.
- `npm run career:verify`: 53건 통과. 기존 44건에 시장 8건과 브라우저에서 발견한 스토브/복구 Calendar 경계 1건을 추가했다.
- `npm run build`: TypeScript/162 modules 빌드 통과, 최종 Vite 12.16초.

중간 실패는 원인을 확인한 후 수정했다. 컴파일 fixture 오타와 DB 컬럼명/이주 개수 교정, 실제 시계 역보정으로 인한 캘린더 영수증 오류, 구형 Calendar 프런트 차단을 수정했다. 반복 시장의 임의 200회 guard는 사건이 매일 생길 수 있다는 정책과 맞지 않아, 고정 create seed와 매번 날짜가 증가한다는 assertion/해당 연도 일수 상한으로 교정했다. 결과/게임 정책 값을 통과 목적으로 바꾸지 않았다.

- 마지막 집중 실행: `./gradlew test --tests 'com.lolfm.league.CareerModePersistenceTest.marketOffseason*' --tests 'com.lolfm.controller.CareerApiV1ControllerTest.marketTerms*' --console=plain --no-daemon`, 2건 통과, 7분. 두 시즌 이월, 2029-01-05 지연 마감 보존, 시계 역보정과 잘못된 날짜/역할/소수 금액의 HTTP 400 거부를 확인했다.
- 실제 브라우저에서 2028-01-01 전환 후 PerfecT/Bo/Bdd/FenRir/Effort 이월과 예산 배정을 확인했다. DK–KT Player Series를 시작해 Draft 화면의 Bo를 확인하고, 새로고침 후 Series 허브의 `진행 중인 Draft 계속`으로 같은 입력을 복구했다. 실제 Auto BO3는 위 통합 테스트에서 한 번 완주했으며 브라우저에서는 같은 엔진을 다시 완주하지 않았다.

Draft의 실제 GET 응답에서도 `player-bo`가 포함되고 `player-cuzz`가 제외된 10명을 확인했다. 임시 Playwright 브라우저와 Spring/Vite 서버는 최종 전체 회귀 시작 전에 종료했다.


### 전체 회귀

1차 `./gradlew test --console=plain --no-daemon`: **265 suites / 2,021 tests / 실패 1 / 오류 0 / 기존 skip 2**, 27분 34초. 실패는 `CareerApiV1ControllerTest`가 다음 사건을 바로 2027-01-14로 기대한 기존 assertion이다. 새 정책에서는 더 이른 2026-08-31 시장/급여 사건을 소비하므로 의도된 동작 차이다. 최초 날짜/원본 replay 검증을 갱신하고, 중간 시장 사건을 거쳐 원래 Cup의 Player/Auto 시작·실행·복구 검증을 그대로 수행하도록 해당 테스트만 교정했다.

교정 후 `./gradlew test --tests 'com.lolfm.controller.CareerApiV1ControllerTest.createListGetReplayConflictAndStrictErrorsPreserveLeagueState' --console=plain --no-daemon`: **1건 통과**, 3분 7초. 생산 Java/자료/정책은 첫 전체 회귀 이후 변경하지 않았다. 최종 전체 실행은 교정된 테스트를 포함해 다시 수행한다.

두 번째 전체 실행 중 환경이 재시작됐다. 재개 시 기존 Java/Gradle 프로세스와 `/tmp` 로그·JDK가 없고, 디스크에는 20:03 KST 시점의 `in-progress-results-generic.bin`만 남아 있음을 확인했다. 따라서 이 실행은 통과나 실패가 아닌 **환경 중단**으로 기록한다. 집중 테스트만으로 미완료 전체 회귀를 대신할 수 없으므로 Linux Java 21을 복구하고 변경 없는 최종 트리에서 전체 실행을 재개한다. 이는 전체 명령 세 번째 시작이며 두 번째 완료 실행을 확보하기 위한 재실행이다. 재개 로그는 재시작에도 보존되도록 `backend/build/reports/career-market-v1/full-regression-resumed.log`에 둔다. 기존 브라우저/집중 검증 기록은 유지하며 재실행하지 않는다. 이전 `/tmp`의 스크린샷·임시 DB·상세 로그는 환경 재시작으로 소실되어 이 문서의 실행 기록과 구분한다.

재개한 `JAVA_HOME=/tmp/lolmanager-temurin21 PATH=/tmp/lolmanager-temurin21/bin:$PATH ./gradlew test --console=plain --no-daemon`은 **BUILD SUCCESSFUL, 27분 25초**로 종료했다. Java는 Temurin 21.0.12.1이다. 최종 XML 집계는 **265 suites / 2,021 tests / 실패 0 / 오류 0 / 기존 skip 2**다. 기존 skip은 `PlayerDraftLatencyProfilingV1DiagnosticTest.captureOfficialPlayerDraftInteractiveAndSimulationLatencyProfile`과 `PlayerDraftPerformanceHardeningV1DiagnosticTest.capturePairedBackendProbe`이며 이번 기능 때문에 추가한 skip은 없다.

전체 실행은 총 세 번 시작했다: 1차 완료(기존 날짜 기대 1건 실패, 27분 34초), 2차 환경 중단(완료 시간/결과 없음), 복구 후 최종 완료(27분 25초, 실패·오류 0). 생산 트리 동결 이후 테스트의 의도된 Calendar 기대만 교정했으며, 최종 통과 이후에는 문서와 집계 기록만 정리했다. 최종 결과는 `backend/build/test-results/test`와 `backend/build/reports/tests/test/index.html`에서도 확인할 수 있다. 자동 commit·push·배포는 수행하지 않는다.
