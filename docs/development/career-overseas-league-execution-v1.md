# Career 해외 리그 실행 V1

해외 runtime·국제 자격·조회 화면과 대표 실제 브라우저 흐름을 연결했다. 선행 게이트, 계획된 전체 회귀 1회와 발견된 회귀의 후속 집중 검증을 완료했다.
시작 기준: `c43ba9cc6f9435488f688925289128f5f51db509`.

## 선행 게이트

| 게이트 | 재현 원인 | 수정 |
|---|---|---|
| G1 | 이미 존재하는 `career_market_season_close` 때문에 재정 close가 생략되어 legacy의 다음 해 승인 누락 | legacy 이주·복구·스토브 재진입에서 누락 승인만 생성. 봉인 snapshot은 보존. 평가 완료 목표에서도 누락 승인 복구 가능 |
| G2 | 완료 receipt 확인 이후에도 구형 요청이 통화 검사 전에 revision 검사에서 탈락 | 원본 UUID/payload receipt 우선, 다음 통화 정책, 마지막 현재 연도/revision 검사. 화면은 원본을 보존하고 최신 KRW 조회 성공 후 명시적 재작성 허용 |
| G3 | CL 최강 후보 선택 후 자격을 검사하여 차선 적격 후보 차단 | 다음 경기 자격으로 필터한 뒤 기량·개선 폭·대기 기간 판정 |

재현: 선택한 4건 중 3건 실패(세 결함), 작은 기량 차이 경계 1건 통과.
수정 후 첫 집중: `CareerSquadPlanningPolicyTest` 14건, `CareerFinancePolicyTest` 13건,
`CareerModePersistenceTest`의 시장 원본 복구/마감 legacy 전환 2건, 합계 29건 통과(Gradle 2분 30초).
원본 로그 `/tmp/career-overseas-gates-before.log`, `/tmp/career-overseas-gates-after.log`,
XML 보존 `/tmp/career-overseas-gates-first-pass/`.
추가 계약·이적 원본 복구 1건 통과(1분 38초). 마감 저장의 파일 재시작·실제 rollover는 해외 activation과 결합한 추가 검증에서도 통과했다. 게이트별 전체 회귀는 실행하지 않았다. 전체 실행 결과는 마지막 절에 별도로 기록한다.

## 규칙 근거

`일정/lpl,lcp,lec,lcs,cblol일정`의 manifest 24개 파일 해시 일치.
제공된 검증 스크립트를 원본을 변경하지 않고 실행해 PASS:
5개 리그, 20개 event, 39개 stage, 36개 고정 경기 수, 근거 44건.
승강전 3개를 제외한 17개 event가 구현 대상이다.
참가자/현실 결과는 매 시즌 고정 입력으로 복사하지 않는다.

추가 공식 확인(2026-09-08):
- [LCP 시즌 안내](https://lolesports.com/en-PH/news/lcp-2026-season-primer):
  Split 3 추가 15 CP는 **플레이오프 3위**이며 final seeding 순위 보너스가 아니다.
- [LCP Split 3 상세](https://lolesports.com/en-AU/news/lcp-2026-split-3-everything-you-need-to-know):
  2-0 패자와 0-2 승자의 교차 대진, 재대결 회피, 최종 시드 BO5와 3패 동률 +5 CP.
- [LCS 안내](https://lolesports.com/en-US/news/lcs-2026-address),
  [LCS 형식](https://lolesports.com/en-US/news/lcs-2026-updates): last-chance의 존재는 확인되지만 BO 길이는 본문에 없다.
  실행용 채택 정책은 공식 확정과 분리해 기록해야 한다.

## 구현한 17개 실행 경로

다음 표는 통제 점수로 대진 완결성을 검사한 결과다. 수백 경기를 실제 엔진으로 실행한 결과가 아니다.
모든 이벤트는 공통 durable Auto job, Series binding, 검증 receipt, Calendar transaction을 사용한다.

| 이벤트 | 참가 | 정규·Swiss | 후속 경로 | 자격·CP |
|---|---:|---|---|---|
| LPL Split 1 | 14 | 6/4/4조 더블 BO3 54 | Knights 6 BO5·R3 재시드 → 8팀 DE 14 BO5 | 상위 2 FST; 80/50/40/20/10/5 CP |
| LPL Split 2 | 14 | Ascend 8 더블 + Nirvana 6 싱글 BO3 71 | 상위 4 직행·Knights 4 → 8팀 DE 14 BO5 | 상위 2 MSI; 110/80/50/30/15/10 CP; Nirvana 마지막 2팀 시즌 경기 종료 |
| LPL Split 3 | 12 | Ascend 8/Nirvana 4 더블 BO3 68 | Knights 2 → 상위 2 bye·3~6위 upper·Knights 2 lower의 12 BO5 | 300/110/80/50/30/15 CP; 우승·최고 CP 직행 |
| LPL Regional Finals | 4 | 없음 | 4자리 지역은 3 BO5로 2팀; 3자리 지역은 버전 있는 3경기 사다리로 1팀 | 실제 MSI 지역 성적의 3/4자리 적용 |
| LEC Versus | 12 | 싱글 BO1 66 | 8팀 DE 14, 첫 두 라운드 BO3/이후 BO5 | 우승 FST; LR/KCB는 이 이벤트만 참가 |
| LEC Spring | 10 | 싱글 BO3 45 | 상위 4 upper·5/6 lower, 8 BO5 | 상위 2 MSI |
| LEC Summer | 10 | 싱글 BO3 45 | 6팀 8 BO5 | Worlds 지역 순위; Spring과 구분된 동률 체인 |
| LCP Split 1 | 8 | 싱글 BO3 28 | 진입전 2 + 4팀 DE 6, 전부 BO5 | 우승 FST; 게임 득실 floor + 정규 순위 + PO CP |
| LCP Split 2 | 8 | 싱글 BO3 28 | 진입전 2 + 4팀 DE 6 BO5 | 우승 + 중복 제외 누적 CP MSI; 게임 득실만 2배 |
| LCP Split 3 | 8 | 3승/3패 Swiss, 진출·탈락전 BO5 | 최종 시드·3패 동률 BO5 → 4팀 DE 6 | 상위 2 + 중복 제외 CP Worlds; PO 3위만 추가 15 CP |
| Copa CBLOL | 8 | 싱글 BO1 28 | Play-In BO3/BO3/BO5 → top 2 bye, PO 10 | 우승 FST; PO upper QF/SF BO3·나머지 BO5 |
| CBLOL Etapa 1 | 8 | 싱글 BO3 28 | top 2 bye, 10 BO5 | 우승 MSI |
| CBLOL Etapa 2 | 8 | 싱글 BO3 28 | top 2 bye, 10 BO5 | 기본 상위 2 Worlds |
| LCS Lock-In | 8 | 3라운드 Swiss 12 BO3 | 최상위 게임 전적 1-2 팀 5시드; 남은 1-2 두 팀 last-chance BO3 → PO 8 BO5 | 우승 FST |
| LCS Spring | 8 | 싱글 BO3 28 | 6팀 8 BO5 | 상위 2 MSI |
| LCS Summer | 8 | 싱글 BO3 28 | top 2 bye + lower gauntlet 10 BO5 | 기본 상위 3 Worlds |
| Americas Cup | 4 | 두 지역 첫 대회 2·3위 | 4팀 DE 6, opening BO3/이후 BO5 | 우승 부트캠프 지원 자격만 저장; MSI·현금·성장 효과 없음 |

승강전 3개는 실행하지 않는다. 다음 시즌 파트너 유지 정책이며 새 승격 구단을 생성하지 않는다.
공식 미확정 운영 세부는 `overseas-rules-2026-v1.json`의 `adoption`과 Java 규칙 버전으로 구분한다.
LCS last-chance BO3, LCP 2v2 대체 first-selection, 남은 동률 추첨/사다리,
일정의 연도 투영·최대 7일 연장, CBLOL 동시 탈락 5/6위의 정규 순위 우선은 게임 보완 정책이다.

## 저장·명부·재정

- 신규 Career: 첫 실행 시즌(현재 2027)부터 활성화. 실제 최초 운영일은 기존 Career의 2026-08-24를 유지한다.
- 기존 Career: `career_overseas_activation`에 도입 시즌·다음 온전한 활성 시즌·규칙 JSON/hash를 한 번 저장한다. GET은 이주하지 않는다.
- `career_overseas_state`는 이벤트별 참가자·조·이전 순위·이전 CP·추첨 seed·현재 대진/순위/CP를 저장한다. 다음 stage는 적용된 검증 receipt의 구조적 점수만 읽는다.
- LPL Split 2는 Split 1의 탈락팀을 포함한 14팀, Split 3만 실제 마지막 2팀을 제외한 12팀, 다음 Split 1은 다시 모든 14파트너다. 탈락으로 조직·계약·급여·훈련을 삭제하지 않는다.
- 원본 56팀 자료는 그대로다. runtime 재정 소유자는 OMG/UP/LR 추가로 59개이며, KCB 재정/고용 소유자는 KC다. KCB는 별도 DEVELOPMENT 명부로 Versus 및 획득한 국제 자격에 참가한다.
- 원본의 현재 등록 역할과 일치하는 OMG(Hery/Haichao/Juhan/re0/Starry), UP(Sasi/Climber/Saber/Xiaoxia), KCB(Tao/Yukino/Kamiloo/Hazel/Prime) 14명을 명시적으로 채택한다. KCB와 KC 선발은 분리한다.
- OMG SUPPORT 1, UP ADC 1, 카탈로그에 없는 LR 5포지션은 기존 deterministic 생성 선수 공급으로 제한된 FA 풀을 보충한다. 생성 선수는 실존 LR 명부라고 표시하지 않으며, 구단은 기존 제안·예산·선수 동의 경로로 계약한다. 현금/기량을 경기 성사를 위해 올리거나 자동 수락시키지 않는다.
- 신규 확장 3구단의 최초 재정은 해당 지역 기존 운영 예산 중앙값 구단의 기준을 사용하는 명시적 게임 추정치다. 초기 입금 id와 activation으로 중복을 막는다. 기존 56구단의 승인을 다시 만들지 않는다.
- 해외 도입이 다음 시즌이면 만료·이적·은퇴한 계약은 부활시키지 않는다. 기존 보유 선수를 선택하고 공백은 정상 시장 경로로 보완한다.
- 상금 팩의 명시적 eventId에 연결된 완전 분배표 16개만 순위 확정 시 권리/지연 입금 경로를 사용한다. LPL Regional Finals는 분배 미확정으로 보류한다. LPL 단일 시즌 상금을 더하지 않으며 부트캠프·참가분·개인상을 현금화하지 않는다. KCB 권리의 지급 주체는 KC이고 참가 팀 identity로 중복 권리를 막는다.
- 해외 성과 후원·목표 비율은 새로 만들지 않았다. 기존 재정·계약·성장·피로 수식을 재사용한다.

## 실제 자격과 조회

활성 시즌의 `CareerOverseasQualification`은 `ACTUAL_OVERSEAS_RESULTS_AND_CP_V1`로 등록을 제공한다.
FST는 첫 이벤트, MSI/EWC는 중간 이벤트, Worlds는 마지막 이벤트·CP·Regional Finals를 기다린다.
필요한 결과가 없으면 전력순을 사용하지 않는다. LCP 우승/CP 중복 제외, LPL 우승→CP→RF,
MSI 우승팀의 실제 PO 자격, 다른 지역으로 이동한 추가 slot을 구조적으로 전달한다.
기존 봉인 등록은 수정하지 않는다. EWC는 여름 완료를 요구하지 않는다.

대표 통제 자격 사례는 LCP 최종 6위 팀에 우승팀을 제외한 최고 CP를 주어, MSI 두 번째/Worlds 세 번째 자리가 단순 준우승·최종 3위와 달라짐을 확인했다.
LPL은 최종 8위가 최고 CP를 가진 경우 우승 → 해당 CP 팀 → Regional Finals 두 팀 순서를 유지했다.
MSI 우승 지역을 CBLOL, 다음 성적 지역을 LEC로 만든 통제 사례에서는 Worlds 19팀 중 LEC 4/LPL 3자리가 됐다.
국내 순위 목록 8번째에 놓인 MSI 우승팀도 실제 PO 자격이 있으면 참가하고, 자격이 없으면 CBLOL 차순위로 승계됐다.
이 사례는 순위·CP·국제 등록 정책의 통제 입력이며 실제 브라우저 BO1 결과를 시즌 우승으로 간주한 것이 아니다.

`GET /api/v1/careers/{careerId}/overseas/{year}?league=LEC&event=LEC_VERSUS`는
활성화·현재 단계·현재 확정 일정·검증 점수·정규 성적·PO·CP·봉인 국제 자격을 조회한다.
`GET /api/v1/careers/{careerId}/overseas/{year}/{event}/results/{match}`는
완료 Series의 세트별 승리·10인 픽과 리플레이 가능 여부만 반환한다.
Career 화면은 리그/이벤트를 선택하고 12경기씩 표시한다. 늦은 응답은 Career/시즌/선택/generation 경계에서 무시한다.

## 현재 검증 증거

- 게이트: 앞 절의 재현 3실패 → 29통과와 추가 원본 복구 통과.
- 순수 대진 첫 검사: 20건 통과(33초), 전 17이벤트 완결성과 일정/BO/경로 확인.
- 신규 저장 초기 연결: 1건 통과(2분 31초). 17개 instance, 59개 계정, 게스트 명부, GET·recover 불변.
- 조회/재정 연결: 대진 20 + 저장 1, 21건 통과(2분 51초).
- 첫 실제 BO matrix: 24건 중 22통과/2실패(3분 49초). BO3/BO5 실패는 모든 선수에게 양수 성장을 요구한 테스트 가정. 기존 PA 경계를 유지하고 전체 성장 상태를 기존 정책 결과와 비교하도록 수정했다.
- 결합 집중: 국제 9 + 해외 실행 4 + 자격/상금 4 + 대진 20 + 마감 저장/rollover 1 = 38건 통과(4분 26초). 실제 BO1/BO3/BO5 모두 공통 Auto runner 완료, 양 팀 10인 출전·세트·숙련·피로·후보 불변·재처리 불변 확인.
- 이 실제 검사는 해당 대표 fixture의 날짜만 초기 운영일로 앞당긴 격리 준비다. BO5는 LCP 정규 28개를 통제 점수로 준비한 뒤 실제 진입전 1개를 실행했다. 전체 시즌 실제 완주 증거와 구분한다.
- 예: KCB Tao BO1의 내부 기량 합 156000→156039, 경기 숙련 +53, 피로 0→90. 1군 대회로 기록하고 고용 구단은 KC를 유지했다.
- `npm --prefix frontend run career:verify`: 기존 검증 + 해외/G2 핵심 7사례 통과.
- `npm --prefix frontend run build`: 통과. 최종 빌드 Vite 11.40초.
- 추가 경계(5리그 완료 어댑터·중복 출전·부트캠프) 28건 통과(4분). transaction 롤백 및 다음 시즌 파일 재시작 2건 통과(2분 30초).
- 브라우저에서 완료된 Auto의 저장된 Calendar 차단 사유가 조회에 남아 `allowedAdvanceModes`와 모순되는 경계를 발견했다. 완료 후 현재 대회 gate를 따르도록 고쳤고, 실제 BO1 테스트에 날짜 명령 → Auto 완료 → 차단 해제/진행 가능 조회를 추가했다. 이 BO1 및 BO3/BO5 3건은 통과했다.
- 같은 실행의 추가 영입 단언 1건은 42일 후 합법 명부가 완성됐으나 FA 계약만 세어서 실패했다. 정상 유료 이적·임대도 허용된 공통 영입 경로이므로 계약/임대 증거를 함께 세도록 수정했다. 현금·선수 동의·기량 정책은 수정하지 않았다. 교정 후 해당 1건 통과(2분 47초). OMG/UP의 초기 4포지션, LR의 초기 0포지션에서 모두 합법 5포지션을 확보했으며 정상 신규 계약/임대 증거는 각각 1/3/5건이었다.
- 프런트 최종 `career:verify`는 **100건 통과**(이번 7건 추가). 완료 시 대회 revision 및 운영 조회 갱신을 연결하고 점수 버튼/선택 필터의 기존 테마를 적용했다.

## 브라우저 실제 흐름

로컬 `localhost:8089` 백엔드와 `localhost:5173` Vite, `/tmp/career-overseas-browser/league` H2만 사용했다.
UI에서 새 KT Career를 만들고 LEC Versus의 FNC–KCB 한 경기 날짜만 2026-08-25로 앞당겼다.
현실 승패나 완료 receipt를 준비하지 않았으며 UI 하루 진행 → Auto 실행을 통해 production BO1을 완료했다.
job 1개, attempt 1, COMPLETED, 실패 코드 없음. FNC **1–0** KCB, 정규 승패/게임 승패가 1–0과 0–1로 표시됐다.
상세의 10인 픽은 Soboro/Gnar, Razork/Lee Sin, Vladi/Cassiopeia, Upset/Varus, Lospa/Neeko,
Tao/Rumble, Yukino/Xin Zhao, Kamiloo/Orianna, Hazel/Ezreal, Prime/Bard다.
국제 자격은 아직 대회가 끝나지 않았으므로 필요한 실제 결과/등록 대기 설명을 표시했다.
서버 재시작 및 페이지 새로고침 후에도 완료 1/66과 1–0 점수가 보존됐다.

G2의 작은 별도 준비는 BeryL에 대한 **미실행** V1 원본 요청이다.
원본 `expectedRevision=0`, 연봉 25,000/계약금 100크레딧을 sessionStorage에 넣었으며 현재 시장 revision은 6이었다.
‘계약 요청 다시 확인’에서 `CAREER_MONEY_POLICY_REFRESH_REQUIRED`에 도달했고 UUID·revision·숫자가 그대로 남았다.
명시적 보관 버튼 후 `:legacy-review` 내용이 원본과 정확히 같고 pending은 비워졌다.
새 원화 양식은 최신 요구 연봉 **108,333,333원**, 계약금 0이었다. 새 UUID 자동 발급/제안 제출/실제 이적은 없었다.
계약·이적의 완료 원본, UUID 충돌, 미실행 원본과 일반 KRW stale 경계는 앞선 backend/프런트 계약 검증으로 확인했다.

Playwright CLI `career-overseas` 세션의 `select/click/snapshot/reload`로 조작했다.
확인된 DOM 참조로 경기/복구 버튼을 눌렀으며 sessionStorage 준비/원본 비교만 `eval`을 사용했다.
화면: `output/playwright/career-overseas-completed.png`; 스냅샷 `/tmp/career-overseas-browser-*.txt`.
최초 브라우저 준비 중 Chromium NSS 라이브러리와 임시 H2 옵션 문제를 해결했다. 제품 실행 옵션은 바꾸지 않았다.
서버 준비 전 연결 거절 4건과 G2의 예상 409를 제외한 정상 흐름에서 API 계약 오류는 위 Calendar 수정으로 해소됐다. 최종 새로고침 후 콘솔은 오류 0/경고 0이었다.
직접 시작한 브라우저·백엔드·Vite 서버는 종료했다.

## 전체 회귀

계획된 전체 실행은 **1회**, **274 suites / 2,155건 / 2,141 통과 / 12 실패 / 오류 0 / 기존 skip 2**다.
Gradle 결과의 실행 시간은 **38분 28초**, 프로세스 시작부터의 외부 측정은 41분 15초다.
`docs/development/testing.md`, 현재 Gradle `test` 설정, Temurin JDK 21.0.12.1을 확인했고
기본 2 workers/각 1,536MiB, diagnostic 제외를 유지했다.
전체 로그·실패 목록·XML·HTML을 후속 집중 실행 전에 `/tmp/career-overseas-full-evidence/`에 보존했다.

| 원인 | 최초 실패 | 수정 및 직접 영향 범위 |
|---|---:|---|
| 실제 회귀: 일반 리그의 null competition을 해외/국제 중복 보호에서 조회 | CL 2, 기존 League Auto 1 | null 경계를 복구. 일반 리그 출전·동결 명부·성장/승격·실제 Auto 완료 검사 |
| 실제 회귀: CL 결과 갱신 후 기존 해시로 일정 무결성 검사 | CL 2 | instance/cycle 해시를 갱신한 뒤 일정 정렬. CL 90경기/PO 봉인 및 실제 완료, 해외 BO1/3/5 공유 경로 검사 |
| 의도된 추가 리소스/V23 이주 수 | 재정 1, API 1, 파일 이주 3 | 상금 규칙 7→24, 비CL 대회 12→29, V23 이주 수. 원본 자료 수/과거 저장 보존 단언 유지 |
| 기존 테스트의 전역 첫 fixture 가정 | 국내 실행 1 | LCK Cup을 명시적으로 선택해 기존 Auto/Player BO·Fearless 검사 |
| 기존 테스트의 정규 시즌 직접 날짜 이동 | League Calendar 1 | 앞선 대회 생략 준비에 reconcile도 격리. 실제 날짜 명령·명부·job·성장·롤백 단언 유지 |

API의 기존 Cup 시나리오는 첫날의 다른 대회만 다음 날로 옮기는 테스트 준비를 사용한다.
해외 활성화나 대회를 끄지 않으며 제품의 일정 유한 경계를 완화하지 않는다.
후속 검증은 위 실패 사례와 직접 영향받는 CL/국내/해외 실행을 대상으로 진행해
**8 suites / 18건 전부 통과, 실패·오류·skip 0, 11분 26초**였다.
로그·XML·집계는 `/tmp/career-overseas-post-full-evidence/`에 별도 보존했다.
최초 실패 12건을 모두 다시 실행했고, 추가 6건은 같은 국내/해외 완료 경로의 직접 영향 검사다.
수정은 null 경계와 동일 transaction 안의 해시 갱신 순서 및 테스트 준비/기대값에 한정된다.
일반 League·LCK Cup/국제전·CL·해외 BO1/3/BO5와 API/파일 복구에서 그 계약을 확인했으므로
두 번째 전체 실행이 필요한 넓은 검증 공백은 없다. 전체는 추가 실행하지 않았다.
**미해결 테스트 실패는 없으며 최종 tree의 clean full 통과를 주장하지 않는다.**

주요 실행 명령은 아래와 같다. Gradle 실행은 모두 순차 수행했다.

```bash
JAVA_HOME=/tmp/career-development-jdk ./backend/gradlew -p backend test \
  --tests 'com.lolfm.career.CareerSquadPlanningPolicyTest' \
  --tests 'com.lolfm.career.CareerFinancePolicyTest' \
  --tests 'com.lolfm.league.CareerModePersistenceTest.marketCommandsPersistMembershipMoneyAndOriginalReceiptsWithoutChangingFrozenSeries' \
  --tests 'com.lolfm.league.CareerModePersistenceTest.alreadyClosedLegacyFinanceActivatesNextYearWithoutRewritingHistoryAfterRestart' \
  --console=plain --no-daemon
JAVA_HOME=/tmp/career-development-jdk ./backend/gradlew -p backend test \
  --tests 'com.lolfm.career.CareerOverseasTournamentTest' \
  --tests 'com.lolfm.career.CareerOverseasQualificationTest' \
  --tests 'com.lolfm.career.CareerOverseasExecutionTest' \
  --tests 'com.lolfm.career.CareerInternationalTournamentTest' \
  --tests 'com.lolfm.league.CareerModePersistenceTest.alreadyClosedLegacyFinanceActivatesNextYearWithoutRewritingHistoryAfterRestart' \
  --console=plain --no-daemon
npm --prefix frontend run career:verify
npm --prefix frontend run build
JAVA_HOME=/tmp/career-development-jdk ./backend/gradlew -p backend test --console=plain --no-daemon
```

전체 이후 수정의 선택자는 다음과 같다. 최초 전체 원본은 보존하고 이 범위만 별도로 실행한다.

```bash
JAVA_HOME=/tmp/career-development-jdk ./backend/gradlew -p backend test \
  --tests 'com.lolfm.application.CareerDomesticExecutionTest' \
  --tests 'com.lolfm.career.CareerClExecutionTest' \
  --tests 'com.lolfm.career.CareerFinancePolicyTest.sourceScopeInitialGroupsAndExplicitEconomics' \
  --tests 'com.lolfm.controller.CareerApiV1ControllerTest.createListGetReplayConflictAndStrictErrorsPreserveLeagueState' \
  --tests 'com.lolfm.league.CareerModePersistenceTest.atomicProvisionReplayPlayerResumeAndFileRestartReuseExistingAuthority' \
  --tests 'com.lolfm.league.CareerModePersistenceTest.v4CareerMigratesToFrozenV5CalendarWithoutBackdatingFoundationBinding' \
  --tests 'com.lolfm.league.LeagueRelationalPersistenceAndJobTest.migratesEmptyAndPreviousSchemaThenRestartsFromSameFile' \
  --tests 'com.lolfm.league.LeagueAutomatedSeriesRunnerProductionV9Test.selectedReserveRunsThroughActualLeagueAutoAndFrozenReceiptValidation' \
  --tests 'com.lolfm.league.LeagueAutomatedSeriesRunnerProductionV9Test.calendarDateAdvanceCapturesSettledStartAndAppliesActualAutoExactlyOnce' \
  --tests 'com.lolfm.career.CareerOverseasExecutionTest' \
  --console=plain --no-daemon
```

수천 seed·다년 경제 분포·전체 선수 감사·5개 리그 실제 시즌 완주·공식 holdout 재실행은 수행하지 않았다.

`git diff --check` 통과. 직접 시작한 브라우저·백엔드·Vite 서버는 종료했다.
commit/push/배포는 하지 않았다. 시작 전 사용자 `.gitignore` 변경과 로컬 자료를 보존했다.
공식 미확정 규칙은 앞서 명시한 게임 보완 정책으로 실행하며, 승강·부트캠프 효과·해외 감독 UI와
해외 전용 성과 예산 튜닝은 요청한 V1 범위에서 제외했다.

## 변경 책임

- `CareerFinanceStore`, `CareerMarketStore`, `CareerSquadPlanner`와 기존 정책/파일 테스트: G1~G3.
- `CareerOverseasRules/Ranking/Tournament`: 17개 대회별 참가·RR/Swiss/브래킷·동률·CP의 순수 계산.
- `CareerOverseasStore/Roster/Qualification`, `V23`: 실행 시즌 marker, 입력 봉인, 정상 명부/재정 확장, 실제 결과 기반 자격.
- 기존 Competition store/rules/binding/registration, Calendar/Season 서비스: 동일 job·receipt·Calendar·시즌 전환 경로의 해외 연결.
- 기존 Appearance/Development/Lifecycle/Market/Finance: 실제 출전·기량·피로·약속·관측·상금의 공통 반영.
- `CareerOverseasApiV1Controller`, `careerOverseas.contract`, `CareerOverseasPanel`, Dashboard/API: 필터 조회·결과 상세·완료 갱신.
- `CareerMarketPanel`, `CareerTransferPanel`: 원본 보존과 명시적 KRW 재작성. `career.css`: 새 조회 필터/점수 버튼 테마.
- 신규 테스트는 순수 클래스 2개와 공유 실행 통합 클래스 1개다. 기존 국제·AI·파일 저장 테스트를 확장했다.
