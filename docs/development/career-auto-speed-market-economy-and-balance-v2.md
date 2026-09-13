# Career Auto·시장·재정·밸런스 통합 V2

## 범위와 기준

2026-09-09, 시작·최종 HEAD `a23d7daa46e46cb3358d30956dab63aeb5d9e54e`, `main`의 working tree 구현이다. 커밋·push·배포하지 않는다. 기존 `career-play-speed-v1.csv`, realism review의 `matches.csv`, 미추적 리뷰 문서·prompts·선수정보 ZIP은 사용자 변경으로 보존했다. 원본 능력치·PA·예산/개인상 자료 및 사용자 DB를 수정하지 않았다. 이번 작업의 추적 파일41개 수정·새 파일18개를 working tree에 남겼다. 사용자 CSV2개를 제외한 작업 범위 `git diff --check`와 새18파일의 `git diff --no-index --check`가 통과했다. 새 CSV의 CRLF6개는 파싱 값 동일성을 확인하고 LF로 정리했으며 기존 사용자 CSV는 변경하지 않았다.

A→B→C→D→E→F 순서로 진행했다. 필수 수정은 worker 실패 backoff, 예산 적합 차순위 탐색, 새 협상 가격, 해외 성적의 경영 평가 연결이다. 성장·경기·수상 수치는 아래 작은 관측만으로 추가 변경하지 않았다. 성능 목표 미달과 장기 균형 미확인을 구현 완료와 구분한다.

## A. 같은 정책의 실행 비용

`CareerContinuousWorker`는 step 실패 뒤 영속 defer도 실패하면 다음 cycle을 1,000ms 뒤에 예약한다. 정상 due 작업은 0ms, 외부 작업 대기/빈 due는 기존 1,000ms다. due SELECT 자체 실패도 1,000ms다. 8건/250ms batch, 다른 Career의 기회, 종료, UUID·lease/fence·checkpoint·한 번 반영을 유지한다. scheduler 검사에서 연속 defer 실패 3회에도 즉시 반복하지 않고 정상 Career가 실행 기회를 얻는다. 이 오류는 정상 성능 표본의 지연 원인으로 확인된 것이 아니다.

기존 DraftComputationContext에 Draft 수명의 불변 unavailable 집합·상태 키 hash·역할 배정별 composition shape를 재사용했다. ChampionCatalog는 private immutable hash lookup을 사용한다. 후보 수·beam·정렬·동률·점수·Random·tick을 바꾸지 않았다. 캐시는 해당 Draft에서 지우며 전역 경기 상태를 도입하지 않았다.

### 동일 JVM 계산

child classloader로 기준 HEAD와 A 전용 클래스만 사용했다. 각각 1회 warm-up 후 before→after→after→before. 실제 엔진 경기는 0세트다.

| 순서 | wall ms | thread CPU ms | allocated bytes |
|---|---:|---:|---:|
| before 1 | 5290.189 | 4629.012 | 3491928400 |
| after 2 | 4367.697 | 3841.819 | 2733915656 |
| after 3 | 4446.381 | 3888.055 | 2733585320 |
| before 4 | 5089.932 | 4455.462 | 3482918080 |

평균 wall 약 15.1%, CPU 14.9%, 할당 21.6% 감소다. 중간 최적화 성과와 합산하지 않는다. Draft identity `c0e3692c1c446d5191e216d7b6b8b4a364855925435be8879e345135ff23f113`, ordered trace `a0b5a08b87153fa35279dd6eaf0836bd729f19585d56458a16bddd02634b1ae2`는 네 번 모두 같다. 초기 JFR allocation sample의 상위는 unavailable 집합, composition shape, pool health였다. JFR 표본 weight는 실제 총 할당 바이트와 다르다.

### 실제 Career 및 브라우저

WSL/Linux 12 CPU, JDK21, JVM -Xmx1g, coordinator/competition worker 각 1, `/tmp` H2 사본, Chrome headless 1440×1000. fresh JVM의 앱과 GET을 준비한 뒤 측정했으며 4회 모두 JFR profile을 켰다. Gradle/무거운 프런트 작업과 겹치지 않았다. 동일 닫힌 seed DB에서 before/after 각 2회다. 준비 helper로 Cup NS–BFX Auto를 현재 날짜, GEN–DK 관리 경기를 +2일에 옮겼고 결과는 만들지 않았다.

| 실행 | 서버 수락→정지 s | Auto 대기→반영 s | 날짜1/2 정산 s | 요청→화면 준비 s |
|---|---:|---:|---|---:|
| before 1 | 44.333 | 35.155 | 1.330 / 1.519 | 46.237 |
| before 2 | 49.556 | 38.222 | 1.734 / 1.940 | 53.960 |
| after 1 | 42.908 | 32.979 | 1.131 / 1.630 | 44.915 |
| after 2 | 43.051 | 32.961 | 1.126 / 1.521 | 46.747 |
| 평균 before→after | 46.944→42.980 | 36.689→32.970 | 두 날짜 동일 | 50.098→45.831 |

화면 대기 8.52%, 서버 8.44%, Auto 관측 구간 10.13% 감소다. 같은 3세트 기준 서버 처리량은 약 9.22% 증가한다. **권장 30% 목표 미달**이며 2회 표본으로 SLA나 모집단 성능을 주장하지 않는다. 100ms transition polling 구간은 정밀 엔진 CPU 시간이 아니다. Calendar GET은 595.4/678.8/586.4/678.7ms, 각 19,701bytes다.

모두 root seed 8446205062685963256, NS 1–2 BFX, 3세트(2350/4020/2620초), 정산 2일, PLAYER_MATCH 정지였다. 완료 receipt hash `eff8e881f007b10d481547bbc0e60bf4b145910cb3f12314bad4be53416ad012`가 같다. receipt에 묶인 순서 있는 Draft·배정·input/output/timeline/Random hash로 비교했으며 raw timeline 전체 직접 비교라고 하지 않는다. Draft/series/award/development/lifecycle 값은 같다. after2의 appearance/market JSON 일부는 기존 팀·선수 컬렉션 순서만 달라 playerId 정규화 후 같은 값을 확인했다. 이를 raw JSON 동일이라고 표기하지 않는다.

## B. AI·가격·해외 경영

### 예산에 맞는 차순위

기존 상위 3명과 재계약 후보를 유지하고, 실제 선발 공백/만료 위험에서만 최대 24개 후보까지 보완한다. 새 후보에 대한 상태 복제·공통 승인 검사는 포지션/선수단 탐색당 최대 8회다. 각 복제에는 같은 검토에서 먼저 고른 동일 구단 제안을 최대 3건까지 공통 submit으로 재생해 예약 의무를 반영한다. 따라서 모든 개별 submit 호출을 합쳐 8회라는 의미는 아니다. 현금−예약, 체불, 시작일의 기존 지급 의무와 승인 급여 한도를 값싼 필요조건으로 먼저 확인한다. 임대는 차입 구단의 실제 분담 급여만 더한다. 미래 의무·선수/구단 동의·명부·대체 선발·등록의 최종 권위는 기존 공통 승인이다.

`CAREER_SQUAD_PLANNING_V2`는 기존 V1 저장을 읽고 기존 상위3명, 구단당 신규4건·거래2건의 주간 제한, 28일 재검토, strict 능력치 합 차이 >12를 유지한다. NO_CANDIDATE / FINANCE_BLOCKED / COMMON_APPROVAL_REJECTED / SEARCH_LIMIT / NEGOTIATION_WAIT를 결정 사유에 구분한다.

작은 BRO TOP 시장에서 상위 3명은 능력치 각20, 4위는 각10으로 준비했다. 기존 연봉 의무에 160,000 GAME_CREDITS의 여유만 주었을 때 이전 코드는 제안 0건으로 재현됐다. V2는 네 번째 STARTER 제안을 만들고 실제 decision date 처리에서 ACCEPTED 및 계약을 확인했다. 여유 0이면 제안 없이 FINANCE_BLOCKED다. 실제 후보 ID는 1jiang/369/adizai/bin, 총 한도1,784,000, Bin 제안 연봉150,000·계약금12,000이었다. 정확한 원문은 `career-market-v2-policy-evidence.txt`의 FOURTH_CANDIDATE 행이다. 이 통제 검사는 legacy credit fixture이며 KRW 가격 비교와 섞지 않는다. 기존 임대/재계약/제안 한도 검사를 재사용했다.

전체 회귀에서 LEC:LR의 실제42일 영입을 확인하던 중 같은 검토 내 예약 누락도 발견했다. 먼저 고른 MID 임대의 의무를 SUP Moham 검사에 반영하지 않아 검사상 통과 뒤 실제 submit에서 현금 부족으로 거절했다. 먼저 선택한 제안의 현금·겹치는 급여와 공통 미래 의무를 후속 후보 검사에 반영했다. 수정 뒤 LR은 369/Gury/Rookie/Jeskla/Nyxus로5역할을 확보했고 OMG·UP도5역할을 유지했다. 별도 작은 BRO TOP/JUNGLE 반례는 현금26,000에서 먼저 고른 TOP 뒤 비싼 JUNGLE을 건너뛰고 싼 후보를 접수하며 총 예약이 현금을 넘지 않는 것을 확인한다. 이 검사도 legacy credit 단위다.

### 현재 기량에 따른 새 가격

`CAREER_NEGOTIATION_PRICE_V2`의 입력은 **현재 정수 능력치 12개 합 S**다. CA 표시/PA/생성 여부/명목상 선발·후보 토글을 입력으로 삼지 않는다. `finance-reference-2026-v1`의 지역 developmentUnit(D), reserveUnit(R), 구단 starterCompensation.base/5의 지역 중앙값(T), 기존 GAME_FIXED_FX_V1을 소비한다. 현실 연봉 추정치가 아니라 자료를 기초로 한 보수적 게임 가격 정책이다.

| 합 S | 원 통화 가격 |
|---|---|
| S≤120 | D×S/120 |
| 120<S≤156 | D+(R−D)×(S−120)/36 |
| 156<S≤180 | R+(T−R)×(S−156)/24 |
| S>180 | T×S²/180² |

기존 정수 ratio 후 고정 환율을 적용해 원 단위로 반올림한다. 개발/후보/선발은 연속 가격 곡선의 자료 anchor이며 UI 선수단 역할을 순간 가격으로 사용하지 않는다.

| 현재 합180, 같은 시장의 원본/생성 공통 | 새 연간 요구액 KRW |
|---|---:|
| LCK | 730,000,000 |
| LPL | 480,000,000 |
| LEC | 288,000,000 |
| LCS | 224,000,000 |
| LCP | 84,000,000 |
| CBLOL | 62,400,000 |

LCP 생성 선수 합156→180이면 새 요구액 33,600,000→84,000,000원이다. 원본 Zkai의 보존 초기 기준/계약은 14,307,891원이고 능력치를 같은180으로 통제하면 새 요구액은 생성 선수와 같은84,000,000원이다. 기존 계약을 소급 인상하지 않았다.

지역 우선순위는 실제 활성 임대의 운영 구단→현재 활성 계약 시장→실제 종료한 직전 계약 시장→structured leagueContext→초기 소유/조직→미확인 LCK fallback이다. 임대 부모 계약 급여는 유지하며 공개 협상 가격은 실제 운영 시장을 반영한다. 구매 구단의 단순 조회는 가격을 바꾸지 않는다. 구단/역할/기간/보수별 수락 판단은 공개 요구액과 별도다.

새 root 제안/거래는 Quote(policy/date/region/ability/source/FX)를 저장한다. 수정 제안·응답은 같은 quote를 이어 쓰므로 일일 성장으로 접수 제안을 재가격화하지 않는다. 견적·선수 수락·FA/재계약·거래/AI·스카우팅은 같은 공개 정책을 쓴다. 기존 quote 없는 제안은 기존 V1 평가 규칙을 유지하고 원 JSON에 불필요한 null 필드를 추가하지 않는다. 기존 계약/합의 이적료/원 UUID의 명령 receipt는 보존된다. 확정 임대의 부모 급여·분담·복귀도 기존 계약 경로를 따른다.

### 해외 5리그 평가

`CAREER_SPORTING_FINANCE_V2`는 시작 시 구단 현재 선발 기량 순위와 승인 연봉 예산 순위의 평균 구간을 고정한다. 해외 tier는 참가 수의 ceil(25%)/ceil(60%)/전체이며 LCK 기존3/6/10은 유지한다. 결과를 보고 목표를 다시 설정하지 않는다.

| 지역 | 대표 결과 | 참가/목표 tier | 이유와 경계 |
|---|---|---|---|
| LPL | SPLIT_3 + SPLIT_2 탈락2팀 | 14 / 4·9·14 | 마지막 split12팀과 이전 탈락 공동13–14위를 함께 보존 |
| LEC | SUMMER | 10 / 3·6·10 | 정규 파트너 평가, spring LR/KCB 게스트 제외 |
| LCS | SUMMER | 8 / 2·5·8 | 최종 국내 경쟁 결과 |
| LCP | SPLIT_3 | 8 / 2·5·8 | 마지막 split 결과 |
| CBLOL | ETAPA_2 | 8 / 2·5·8 | 마지막 etapa 결과 |

공동 rank가 목표 경계를 걸치면 달성, 구간 전체가 목표보다 위여야 초과다. 우승은 초과다. 해외에 근거 없는 Worlds 필수 진출 목표를 새로 만들지 않았으며 실제 Worlds 성적은 별도로 표시한다. LCK의 기존 Worlds 목표는 유지한다.

봉인된 실제 event graph 결과/hash와 참가자를 읽는다. Worlds 역시 실제 대회 결과에서 읽으며 상금 원장이 없어도 성적을 읽는다. COLLECTED/NOT_QUALIFIED/NOT_COLLECTED는 상금 지급 보류와 독립이다. 저장 검사에서 해외48구단, LPL공동13–14위, LEC게스트 제외, Worlds19팀과 hash변조 거부를 확인했다. 승패 준비는 기존 작은 tournament helper이며 실제 시즌 완주라고 하지 않는다.

성적 MET/EXCEEDED 보너스는 고정 sponsor의 기존5/10%다. 기존 상금과 별도 entry ID로 한 번만 지급한다. 다음 연간 반복 수입은 기존 ±5%, 초기 수입의80~120% 상한을 유지한다. 급여 승인은 반복 수입에서 운영비를 뺀 값과 여유 현금/최대 계약 기간을 포함하므로 반복 수입 상한과 동일하지 않다. 미수 상금은 지급 전 현금에 넣지 않는다. 체불·예약·기존 의무는 공통 승인에 남는다.

기존 중립 해외 목표/확정 평가/승인은 교체하지 않는다. 해당 현재 시즌을 뒤늦게 벌주지 않고 다음 온전한 시즌부터 V2 scope를 설정한다. 신규 Career는 처음 목표부터 V2다. 기존 진행 중 LCK 목표도 재설정하지 않는다.

## C. 성장·진입·현금

제품 RookieFactory/DevelopmentPolicy/LifecyclePolicy를 그대로 호출한 2seed(41/73), 각 신인 FIRST_TEAM·CL·BENCH·FA + 원본 Kiin 모형이다. 2027–2031 최대5년, 중도 은퇴 종료로 총49 profile-years. 신인 TOP은 각각 같은 정의를 복사해 기회만 달리했다. 1군 연128세트, CL64세트, 후보/FA0은 **합성 일정**이며 실제 승인 출전/수상 원장에 쓰지 않았다. 만족60, FA 외 다음 계약 있음 등 고정 모형 조건이며 실제 AI 고용을 보장하지 않는다. 출생/PA는 기존 자료와 제품 정책을 사용하고 미확인 PA는 null이다.

| seed/프로필 | 초기CA→마지막CA | 관측 |
|---|---|---|
| 41 신인 PA149 / 1군·CL·후보·FA | 134→146 /145 /143 /139 | PA에 반드시 도달하지 않음, 기회 차이 유지 |
| 73 신인 PA145 / 1군·CL·후보·FA | 113→142 /136 /126 /120 | 젊은 성장과 FA50% 자율훈련 차이 |
| 원본 Kiin /41 | 187→173 | 2030년31세, 은퇴 확률46%, draw3754 |
| 원본 Kiin /73 | 187→168 | 2031년32세, 확률63%, draw3525 |

seed73 1군 신인의 새 LCP 요구액은 2027년29,787,800→2031년71,400,000원이다. 이것은 **새 견적**이며 과거 계약 지급액을 다시 계산한 값이 아니다. 두 신인 모두5년 내 LCK 최상위 기존 주전 수준에 자동 도달하지 않았다. 생성 공급 수가 곧 계약/CL 출전/승격/선발 유지 수라는 결론을 내리지 않는다. 엄격한 교체 합차이13, CL 대체자·등록·임대 복귀가 진입을 제약한다. B의 싼 초기 가격 고착과 후보 탐색부터 교정했고 성장률/PA/은퇴/공급/선발 문턱을 동시에 높이지 않았다.

재정 함수 검사에서 T1 초기 현금7,520,526,000원/급여13,860,000,000원, BRO786,920,400원/1,125,000,000원, CBLOL RED314,510,560원/360,880,000원을 분리했다. 육성 조직에 별도 경쟁 구단 계좌를 만들지 않아 인원·돈을 중복 집계하지 않는다. 월 지급과 체불 회복의 추가 결과는 최종 검증 절에 기록한다. 낮은 신인 계약으로 현금이 쌓일 가능성은 새 협상의 재가격화로 완화하지만 이미 확정한 싼 계약은 존중하므로 즉시 사라지는 현상은 아니다. 이49 profile-years는 세계 장기 현금/공백 안정성을 증명하지 않는다.

## D. 정글과 Draft

제품 캠프 작업량30, tick10초, 완료시 초과 작업량0의 경계를 확인했다.

| 효율 | 완료tick / 수행초 | 버린 작업량 | 완료 tempo credit |
|---|---|---:|---:|
| .99 | 4 /40 | 9.6 | 39.6 |
| 1.00 | 3 /30 | 0 | 30 |
| 1.20 | 3 /30 | 6 | 34.5 |
| 1.49 | 3 /30 | 14.7 | 34.5 |
| 1.50 | 2 /20 | 0 | 23 |

tempo는 .85~1.15 clamp와30초 grace가 있고 300초 공백 뒤 새 작업10초만 기록한다. 실제 resolver는 이동·사망·활동·FARM 제한을 확인한 뒤 작업한다. 캠프 초기120/반복150초 재생성과 갱 기회비용도 있다. 이 계단만으로 실제 경기 CS/보상이 같은 비율이라는 결론을 내리지 않는다. 놓친 과거 FARM을 지급하거나 시간/Random 규칙을 변경하지 않았다.

LCK GEN–T1, CBLOL RED–PNG 각각 G1 seed73/G2 seed74, 실제 Hard Fearless로4세트를 실행했다. 경기 시간1750/1600/2240/1510초로 전부20분까지 유효하다. 각 checkpoint 분모는4세트·역할별8명이다.

| 역할 | 10분 CS/골드/XP | 15분 | 20분 |
|---|---|---|---|
| TOP | 70.25 /3368.75 /3804.38 | 108.25 /5007.50 /5838.88 | 147.12 /6724.75 /8093.25 |
| JUNGLE | 60.50 /3002.50 /2784 | 95 /4473.75 /4382.75 | 127.50 /6187.88 /5954 |
| MID | 73.50 /3295 /3636 | 113.75 /4855 /5655.38 | 150.88 /6535.38 /7520.38 |
| ADC | 81.12 /3586.25 /3056.25 | 125.12 /5291.75 /4729.50 | 168.62 /7234.12 /6505.25 |
| SUPPORT | 0 /2289.75 /2931.25 | 0 /3331 /4342 | 0 /4503.88 /5551.50 |

서로 다른 지역 G1의 첫 선택5개가 Varus/Alistar/Wukong/Azir/Caitlyn으로 같았다. G2는 G1 사용10개를 제외하고 다른 선택이 나왔다. CSV에는80턴의 사용 가능·역할 완성 가능 후보 수, 즉시/continuation/최종 점수, 항목과 top alternatives를 남겼다. `legal_before_search`는 beam/근접 후보 분모가 아니라 현재 규칙의 전체 합법 후보다. raw legal ID와 입력 명부·배정은 `/tmp/career-v2/matches`에 보존했다.

LCK G1 Varus는 PLAYER_FIT19.166, DENIAL6.614, COMPOSITION_FIT4.919, FLEX4, FUTURE10이고 JUNGLE_CLEAR0이다. Wukong clear 원항목은0.408, Fiddlesticks0.5125이고 weight0.5 적용 기여는0.204/0.25625다. 같은 Varus PLAYER_FIT의 weight0.8 기여는15.333이다. CSV components는 weight 전 원항목이며 최종 immediate는 제품 weight 합이다. 연속 clear 예상에는 계단을 그대로 반영하지 않는 근사가 있지만 이 표본은 그것이 선택/보상을 유의미하게 과대평가하는 원인임을 입증하지 못했다. 챔피언 이름별 감점이나 초과분/예측 두 경로를 동시에 보정하지 않았다. 같은 두 대진의4세트는 모집단 픽률 또는 인과적 강함 검증이 아니다.

경기 정책 `MATCH_ENGINE_REALISM_ABILITY_V2`, Draft `AUTO_DRAFT_ABILITY_V2`, runtime `PRODUCTION_REALISM_V2`, configuration hash `40c6a4ca252963d9bfed55767f87773285ec7f0af032bb6835348c21d6a4a099`를 유지했다. 이미 시작/입력 고정한 job·Series·checkpoint를 새 ID로 재해석하지 않는다.

## E. 평점과 기간상

D의 같은40명 성적을 재사용했다. 모델에 없는 시야·CC·보호량을 추가하지 않았다.

| 역할(8명) | 전투 /경제 /생존 평균 | 최종 평균 |
|---|---|---:|
| TOP |43.895 /50 /54.291 |47.286 |
| JUNGLE |29.368 /50 /50 |36.589 |
| MID |38.588 /50 /49.033 |43.008 |
| ADC |51.495 /50 /50.010 |50.899 |
| SUPPORT |21.917 /50 /50 |30.342 |

경제50은 같은 역할 양 진영 상대 비교의 대칭 평균이며 모든 선수 경제가50이라는 뜻이 아니다. 전투·죽음 부담과 역할 baseline을 분리했다. 지원 기여가 직접 계측되지 않는 서포터와 저킬 경기의 낮은 평균은 한계로 남긴다. 포지션별20% 수상률을 맞추거나 이4경기의 평균만으로 역할 가중치를 보정하지 않았다.

같은 관측 평균80, 완전 참여 모형을 제품 period 함수에 넣었다.

| 경로 | 보정평균 | 일관성 | 팀성과 | All-Pro /정규MVP /대회MVP |
|---|---:|---:|---:|---|
| 4Series·4승·각3세트 |62 |60 |100 |63.7 /65.6 /65.8 |
| 8Series·6승·각3세트 |67.142857 |65 |75 |67.321428 /67.714286 /67.928571 |
| 4Series·4승·각5세트 |62 |60 |100 |63.7 /65.6 /65.8 |
| 8Series·8승 |67.142857 |65 |100 |68.571428 /70.214286 /70.428571 |
| 2Series·2승 |57.5 |56 |100 |59.475 /61.6 /61.75 |

사전50점×6Series에 의한 신뢰 보정이며 최종점수 전체와 같지 않다. Series 길이는 동일 참여에서 중립이다. 긴8/6 경로의 대회MVP가 짧은4/4보다 약2.13 높아 경로 편향/우회 유인의 가능성은 남는다. 이는 동일 평균을 유지한다는 조건의 점수 반례이며 일부러 패배한 실제 tournament 경로의 생존·자격·최종 우승 확률까지 통제한 효용 비교는 아니다. 사전 강도만 임의로 낮추면 표본 신뢰의 다른 오류를 만든다. 이번에는 `CAREER_PERFORMANCE_V1`·기존 수상 정책을 유지하고 해결했다고 하지 않는다. 공식 참고/게임 채택/분석/보류 구분, 기존 확정 수상자, 동일 범위 rating version 완전성 검사와 G2 라운드/통합 범위는 그대로다.

## F 및 최종 검증

### 계약→실제 경기→브라우저

최종 DB 역시 seed의 폐기 가능한 사본이다. 기존 lifecycle review helper를 일찍 호출해 2028 intake11명을 준비하고 NS의 TOP 계약을 공통 release로 종료해 공백을 만들었다. 2026년8월에 이듬해 intake를 준비한 것은 **검사 준비**이며 정상 달력의 신인 공급 시점 관측이 아니다. 추가 현금·강제 수락·합성 승패를 넣지 않았다. 첫 공백 준비 전 연봉3배 제안은 실제 공통 예산 거부(11.54초)로 종료되었다. 최종 준비는24.65초다.

`player-ng-ac3a73bfa57f-2028-001`은 현재 합182, structured LPL 시장, 공개 요구액490,725,800원이었다. 요구액125%인613,407,250원/2년/STARTER를 명시적으로 제안한 뒤 실제 Calendar 하루 명령7회를 처리했다. 8월29일 실제 ACCEPTED·ACTIVE 계약이 성립했고 AI repair가 같은 날 TOP SELECT/APPLIED를 기록했다. helper의 최종 select는 이를 확인해 유지했다. 전체 AI가 이 신인을 스스로 최초 발굴한 사례라고 하지 않는다. 최초 후보 탐색은 B의 실제 planner 검사에서, 최종 공통 동의·선발·실제 입력은 여기서 확인했다.

최종 실제 Auto NS–BFX는2–0, 2세트 총3440초였다. 생성 선수 ID가 appearance binding·performance·완료 receipt의 실제 배정에 포함됐다. 8월31일 급여5,041,704원이 실제 장부에서 지급됐고9월2일 NS현금2,227,682,123원, 급여 체불0이었다. 같은 주 AI는 더 강한 Flandre 임대 제안도 만들었으므로 신인의 장기 주전 유지를 보장하지 않는다.

브라우저는 실제 Bo 공개 요구액/이적 견적에 V2·기준일을 표시하는 것을 확인했다. 이어 Continuous 시작→Auto2세트→2일 정산→9월2일 PLAYER_MATCH 정지→기존 GEN–DK `BO3 · Game 1` 화면/직접 Draft 시작 버튼까지 이동했다. PLAYER_MATCH의 `MANAGED_COMPETITION_FIXTURE_REQUIRED` 안내는 정상 중단 사유이며 서버 연결 오류가 아니다. 브라우저37.397초, 서버 첫 수락 관측→정지35.028초, Auto 대기→반영22.831초다. **새 명부/2세트이므로 A의3세트 성능 비교에 합산하지 않는다.**

완료 hash는 `b21ab3b9be3f160c7ae03032a86b22fe6f013b0bf35c4cbfb4e6d28be5d2a35c`다. 작은 계약/입력/정지/장부 근거는 `career-market-v2-final-flow.json`에 보존한다. 두 번 시즌 전환·거래/임대 파일 재시작은 기존 CareerModePersistenceTest 두 메서드로 확인했다(2통과,341.67초). 시즌 승패는 기존 finishSeason helper로 준비했으며 실제 OPEN_STOVE 결산→시장 명령→다음 시즌 transition·중복 UUID·동결 명부/성장·연간56승인 보존은 제품 경로다.

추가 저현금 함수 검사에서 현금을0으로 준비하고 급여를 먼저 인식했다. T1미지급1,025,260,270원은 월 수입/운영비 정산 후 전액 상환, 현금3원이다. BRO83,219,179원은 현금0/체불1원이 남았다. 수입 원천과 개별 계약의 반올림 차이를 삭제하지 않고 정확한 식으로 검증했으며 다음 실제 월 수입으로 남은1원이 상환됐다. 이는 준비한0현금 모형이고 정상 구단 현금이3원이라는 뜻이 아니다.

### 집중·프런트·전체 비용

아래 backend 명령은 공통 prefix `JAVA_HOME=/tmp/career-development-jdk /usr/bin/time -p bash backend/scripts/test-linux.sh`다. 각 selector는 실제 클래스 경로다. 동시 Gradle 실행은 없었다. 신규 backend 테스트 클래스는 NegotiationPolicy/FinanceResultsStorage 두 개이고 나머지는 기존 클래스 확장이다.

| 구간 / --tests selector | 건수/결과 | wall s |
|---|---|---:|
| `com.lolfm.draft.DraftComputationContextTest`, `com.lolfm.draft.DraftAvailabilityJointPoolTest`, `com.lolfm.career.CareerContinuousRecoveryTest` |14통과 |147.81 |
| `com.lolfm.draft.DraftComputationContextTest` shape 추가 |9통과 |54.95 |
| `com.lolfm.career.CareerSquadPlanningPolicyTest.coverageSearchReachesFourthAffordableCandidateThroughCommonApproval` 최초 반례 |1실패, 기존 top3 결함 재현 |20.55 |
| `com.lolfm.career.CareerMarketEngineTest`, `CareerFinancePolicyTest`, `CareerSquadPlanningPolicyTest` (동일 career package) |68중1실패, FA 준비의 옛 선발 ID 제거 누락 |185.63 |
| `com.lolfm.career.CareerNegotiationPolicyTest`, `CareerFinancePolicyTest`, `CareerSquadPlanningPolicyTest` |38중1실패, CBLOL에 top tier 실제 구단이 반드시 있다는 fixture 가정 |69.25 |
| `com.lolfm.career.CareerNegotiationPolicyTest`, `CareerFinancePolicyTest`, `CareerFinanceResultsStorageTest` |23통과 |36.21 |
| `com.lolfm.league.CareerModePersistenceTest.marketOffseasonTwoCyclesPreserveSealedRosterAndRepeatBudgetAndNegotiationEvents`, `.paidTradeAndLoanUseCalendarAtomicityOriginalCommandsAndFileRecovery` |2통과 |341.67 |
| `com.lolfm.career.CareerFinancePolicyTest.actualMonthlyFundingRepaysLowCashWagesBeforeNewCommitments` 최초 |2중1실패, 개별 원 단위 반올림 가정 |16.61 |
| 같은 월 지급2사례 |2통과, 잔액식·다음 수입 상환 |18.80 |

전체 전 집중9회/159건 시도, 실패4건(요청된 선행 반례1 + 새 fixture 가정3)이다. fixture 수정은 제품 assertion을 약화하지 않고 실제 계약/지역순위/수입−운영비−급여 관계를 검증하도록 바꿨다. 위 집중 단계의 실패는 후속 해당 검사에서 통과했다. 프런트 `npm --prefix frontend run career:verify` 1회126 PASS, `npm --prefix frontend run build` 1회성공(Vite15.18초). shell 총wall은 별도 측정하지 않았으므로 Vite 시간을 전체 build wall로 쓰지 않는다. 인접 verifier 코드는 바꾸지 않아 별도 실행하지 않았다.

계획 전체1회는 F 뒤 시작했다. 전체 실행 중 최종 source 검토에서 같은 날 활동 시장 변경의 가격 날짜 경계를 발견했다. `processedThrough`는 하루 처리 마지막에 이동하므로 AI/거래 견적은 처리일을 명시하는 `demand(id,date)`를 사용하도록 보완했다. root quote와 기존 offer의 고정 가격 경계는 그대로다. 해외 확장 원본 선수의 최초 계약 채택도 새 협상과 구분해 기존 V1 참고 급여를 명시적으로 사용하게 했다. 이미 존재한 계약은 원래 그대로 보존된다. 전체의 컴파일된 입력은 이 두 적용 경계 보완 전이며, 완료 후 원본 로그/XML을 보존하고 새 날짜 경계·초기 해외 계약 채택·직접 호출자를 집중 검증했다. 후속 검사에서 발견한 같은 검토 내 제안 예약 누락도 수정했다. 전체 재실행 여부는 공유 동작의 실제 미검증 범위로 판단하며 단순 source 변경을 이유로 반복하지 않았다.

계획 전체1회는286 suites /2,265건, 통과2,259·실패4·오류0·기존skip2, wall1,406.06초(23분26.06초)다. 로그/XML/요약은 `/tmp/career-v2/full-original`에 후속 실행 전에 복사했다. 실패를 숨기거나 test 제외/skip을 추가하지 않았다.

- 해외17이벤트/42일 영입 검사: OMG·UP는5명 확보, LEC:LR의 선발 공백으로 실패. 같은 검토의 선행 제안 예약 누락을 수정해 후속에서 세 구단 모두5명 조건이 통과했다.
- League Production V9 실제 입력2검사:3배/150% 제안의 공통 budget 거부로 실제 경기 전 준비 단계에서 실패. 생성 선수125%, Bo/Life125%와 기존 Support 공통 해지로 합법적인 준비를 사용한다. 실제 frozen roster/수상·성장/rollback/중복 completion assertion은 유지한다. 성장 후 현재 견적 assertion도 과거 V1 식 대신 V2 현재 능력치/quote와 기존 합의fee 보존을 확인한다.
- 원본 UUID/파일 복구 검사: FATE 공개가격으로 두 요청이 모두 예산 거부되어 revision race의 성공 수가0이었다. 이 제출 동시성 입력만100,000원으로 준비했다. 소액 제안의 접수는 가능하지만 실제 선수 수락은 별도이며 이후 정상 거절될 수 있다. 한 UUID/revision만 성공하는 조건은 그대로다.

후속 집중은 다음처럼 원본 결과와 분리한다. 아래 selector는 `com.lolfm.career` package를 기본으로 하며 League V9와 파일 복구는 `com.lolfm.league`다. 상세 결과는 `/tmp/career-v2`의 각 log/XML에 보존한다.

| 후속 실행 | 건수/결과 | wall s |
|---|---|---:|
| 가격4·planner16·시장40·해외42일1·League V9 실패2·파일 복구2 (`post-full-focused.log`) |65중64통과·LR1실패. 원래 나머지3실패 및 처리일/초기 급여 경계는 통과 |175.71 |
| 예약 수정 뒤 planner17·해외42일1 (`coverage-reservations-focused.log`) |18중17통과. LR 포함 기존 검사는 통과, 새 작은 예약 fixture의 현재연도 급여 준비1실패 |57.42 |
| `CareerSquadPlanningPolicyTest.laterCoverageCandidateAccountsForEarlierProposalsInTheSameReview` (`reservation-unit-fixed.log`) |1통과. 기존 급여가 지급된 연말/다음 연도 시작으로 준비를 바로잡고 원래 예약 assertion 유지 |19.34 |
| `CareerMarketEngineTest`, `CareerSquadPlanningPolicyTest` (`final-market-focused.log`) |57통과, 실패/오류/skip0 |59.59 |

후속 집중4회/141건 시도에서 남았던 LR 실패와 새 예약 fixture 실패를 각각 수정했다. 마지막 시장40·planner17은 모두 통과했다. 전체 전후 집중은 합13회/300건 시도, wall 합1,203.54초(20분3.54초)다. 모든 관측된 정확성 실패는 후속 관련 검사에서 해소했다. 마지막 제품 수정은 한 번의 planner 검토 안에서 후보 의무를 반영하는 범위다. 해외42일의 실제 시장 진행과 시장 공통 승인40건·planner17건이 이를 검증했고, 처리일 가격·최초 계약·원 UUID/파일 복구·실제 Auto의 실패도 앞선 후속에서 확인했다. 전역 상태·Random·suite 순서의 미확인 공유 상호작용은 발견되지 않아 전체를 반복하지 않았다. **추가 전체0회이며 최종 트리의 clean full 통과를 주장하지 않는다.**

### 관측 예산

새 실제 관측은 **19세트**다: A4×3=12, D첫 실행 후 helper의 raw input Map key 직렬화 실패1, D정상 재실행4, F2. 실패한1세트도 포함한다. 직렬화 실패는 `ChampionRoleKey` Map를 정렬 JSON으로 쓰려던 진단 출력 문제였으며 엔진 실패가 아니다. compact input/hash/roster/assignment 출력으로 수정했다. correctness tests 내부 실제 경기는 이 관측 수와 별도이며 skip으로 감추지 않았다.

순수 모형은2seed/최대5년/49 profile-years, 정책함수 모형2.68초다. D실제4세트42.35초(앱 포함), 실패19.63초, F실제화면37.397초다. A실제화면4회합191.858초, 동일 JVM 계산 최종 측정4회19.194초(별도warm-up), 준비와 초기 비교도 분리했다. 기록된 핵심 자동 관측 wall 합은약5.22분이며 최종 서버의 준비/조회/조작 대기 포함 수명은238.43초다. 앱 대기/사람의 도구 조작까지 전 작업시간을 정밀 계측한 값은 아니다. 측정 반복은24세트/20분 기본 범위를 확대하지 않았다. 빌드·집중·계획 전체 회귀는 위 비용과 별도다.

이 작업의8089 격리 서버·5173 Vite·`career-v2` 브라우저를 종료했다. `/tmp` 근거는 재검토를 위해 남기며 사용자 DB/다른 서버는 종료하지 않았다. 커밋 제안: `Career Auto 실행 비용과 예산 후보 탐색·협상 가격·해외 성적 재정을 개선`.


## 재현 자료

- `backend/scripts/CareerPlaySpeedProbe.java`: 기존 helper를 `policies`, `matches`, `prepare-final` 모드로 확장. production/default test에 포함하지 않는다.
- `career-auto-speed-v2.csv`, `career-auto-speed-v2-draft-pair.csv`: A 원시 시간. `career-auto-speed-v2-parity.csv`는4실행·12세트의 순서 있는 완료 근거다.
- `career-market-v2-policy-evidence.txt`: 제품 함수의 가격/목표/초기 재정/차순위 제안.
- `career-balance-v2-{growth,jungle-boundary,award-paths,matches,economy,draft,ratings}.csv`: 합성 모형과 실제 경기 자료를 별도 파일로 보존.
- `/tmp/career-v2`: 격리 DB/JFR/원본 JSON/로그/XML. 사용자 DB가 아니다. 대형 자료를 저장소에 추가하지 않았다.

컴파일/실행 예(경로는 현재 Linux 출력에 맞춘다):

```bash
JAVA_HOME=/tmp/career-development-jdk /usr/bin/time -p bash backend/scripts/test-linux.sh --tests 'com.lolfm.career.CareerNegotiationPolicyTest'
npm --prefix frontend run career:verify
npm --prefix frontend run build
# 계획 전체는 위 명령에서 --tests를 생략하여 1회 실행했다.
# 최종 영향 범위(2 suites /57건)
JAVA_HOME=/tmp/career-development-jdk /usr/bin/time -p bash backend/scripts/test-linux.sh \
  --tests 'com.lolfm.career.CareerMarketEngineTest' \
  --tests 'com.lolfm.career.CareerSquadPlanningPolicyTest'
# 최종 해외42일은 아래 selector를 planner와 결합해 확인했다.
# com.lolfm.career.CareerOverseasExecutionTest.newCareerActivatesSeventeenEventsAndKeepsReadOnlyViews
# 그 전 실패/처리일/저장 경계 65건은 다음 selector들을 한 번에 사용했다.
# com.lolfm.career.CareerNegotiationPolicyTest
# com.lolfm.career.CareerSquadPlanningPolicyTest
# com.lolfm.career.CareerMarketEngineTest
# com.lolfm.career.CareerOverseasExecutionTest.newCareerActivatesSeventeenEventsAndKeepsReadOnlyViews
# com.lolfm.league.LeagueAutomatedSeriesRunnerProductionV9Test.selectedReserveRunsThroughActualLeagueAutoAndFrozenReceiptValidation
# com.lolfm.league.LeagueAutomatedSeriesRunnerProductionV9Test.calendarDateAdvanceCapturesSettledStartAndAppliesActualAutoExactlyOnce
# com.lolfm.league.CareerModePersistenceTest.marketCommandsPersistMembershipMoneyAndOriginalReceiptsWithoutChangingFrozenSeries
# com.lolfm.league.CareerModePersistenceTest.paidTradeAndLoanUseCalendarAtomicityOriginalCommandsAndFileRecovery

# 현재 환경에서 helper 별도 컴파일 및 순수 모형만 재현(실제 추가 경기 없음)
v2_classes=/tmp/lolfm-backend-1000-af663e77399dba7d/build
v2_cp="$v2_classes/classes/java/main:$v2_classes/resources/main:/tmp/career-overseas-runtime-libs/*"
mkdir -p /tmp/career-v2/reproduce-helper
/tmp/career-development-jdk/bin/javac -cp "$v2_cp" -d /tmp/career-v2/reproduce-helper backend/scripts/CareerPlaySpeedProbe.java
/tmp/career-development-jdk/bin/java -cp "/tmp/career-v2/reproduce-helper:$v2_cp" com.lolfm.career.CareerPlaySpeedProbe policies /tmp/career-v2/reproduce-policies
# policies OUT: Spring/DB 없이 제품 모형
# matches OUT --spring.datasource.url=jdbc:h2:mem:observation: 실제4세트, 고정 입력
# prepare / prepare-final OUT: 반드시 폐기 가능한 seed DB 사본만 지정
# serve OUT: 실제 브라우저가 Continuous를 시작하고 helper가 transition을 관측
```
