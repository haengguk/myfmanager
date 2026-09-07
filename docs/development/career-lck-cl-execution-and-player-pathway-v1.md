# Career 선행 수정 및 LCK CL 실행 V1

2026-09-07 구현·검증 기록. 시작 HEAD는 `1656f4de0cb43fba8e1255ec0f927671f143429e`, 브랜치는 `main`이다.
기존 untracked `prompts/`, `선수정보.zip`과 원본 선수정보/능력치/일정은 보존했다. commit/push/배포하지 않는다.
코드·집중 검증·브라우저 흐름과 계획된 백엔드 전체 회귀 **1회**를 마쳤다.
전체에서 발견한 테스트 준비/기대값 5건은 교정 후 해당 5개 메서드가 모두 통과했다.
마지막 이름/훈련 요청 호환 조건의 직접 영향 3개 메서드도 모두 통과했다. 요청한 구현과 필요한 검증을 완료했다.

## 선행 A/B/C

A의 원인은 포지션 순회 중 상위 PA 한도를 먼저 소비하는 구조였다. 이제 클래스 후보를 모두 만든 뒤
`CAREER_ROOKIE_CLASS_V2 | seed | intakeYear | persistent playerId | BAND_PRIORITY`의 결정적 hash 순서로
190~194 및 180~189 후보를 선택한다. 작은 정방향/역방향 후보 사례에서 뒤 후보가 우선순위로 선택된다.
전설 클래스의 단일 5% 추첨, PA 190 이상 총 1명, PA 180~189 최대 3명, 탈락 후보의 기존 fallback,
정상/긴급 인원·포지션 정책은 유지했다. 저장된 클래스는 재생성하지 않는다.

B의 순수 모델은 같은 날짜 `day → game` 때문에 경기일 훈련까지 받았다. 이를 `game → day`로 교정했다.
경기 경로는 연 100게임을 첫 300일에 3일마다 1게임씩 배치하고, 같은 날 경기 뒤 별도 훈련을 생략한다.
기존 나이·PA·훈련 강도·노쇠화 조건은 유지했으며 제품 성장량은 조정하지 않았다.
각 해 1월 1일부터 **365일 고정**으로 계산하므로 윤년의 12월 31일은 계산하지 않는다.
실제 12개 달력 시즌이나 전경기·시장 균형 검증이 아니다.

아래 배열은 시작 시점 이후 각 12개 연말 CA다. 전체 7경로 표와 비교 조건은
[교정된 생애주기 보고서](career-player-lifecycle-aging-retirement-and-rookie-supply-v1.md)에 있다.

| 시작 조건 | 수정 전 | 수정 후 |
|---|---|---|
| 17세 CA165/PA190 주전 | 173,182,186,188,189,189,190,190,190,189,188,186 | 172,180,185,187,188,189,189,190,190,189,188,186 |
| 19세 CA155/PA175 주전 | 162,169,171,173,174,174,174,173,173,171,169,164 | 162,169,170,172,173,174,174,173,172,171,169,163 |
| 18세 CA132/PA155 주전 | 140,148,151,153,154,154,155,155,154,153,151,149 | 139,146,150,152,153,154,154,154,153,153,151,149 |

17세 경로의 CA190 첫 도달은 23세에서 24세로 늦어졌다. 후보/FA 4경로도 재계산했으며 숫자는 동일했다.
이 교정은 과거 실제 Auto 경기의 관찰 결과를 수정하거나 무효화하지 않는다.

C는 영속 ID·닉네임·가상 본명을 분리한다. `CAREER_GENERATED_NAMES_V1`의 이름 전용 목적 hash와
작은 지역별 이름 풀을 사용하며 경기 Random·PA/능력치 draw와 독립이다. 닉네임은 기존 실존 선수와
현재 Career 생성 이름에 대해 대소문자 구분 없이 충돌 검사한다. 32개 조합 후보 이후 제한된 5자리
suffix 후보를 포함해 최대 64번 시도한다. 본명은 `details.personal.legalName`에 저장하고 생성 출처를 표시한다.
대표 사례는 `player-ng-e9d85fd0fa46-2028-001` / **Avenra** / 가상 본명 **서민준** / **South Korea**다.

startup 이주는 GENERATED 출처, 정책 표지 부재, 기본 placeholder와 일치하는 이름을 구조적으로 확인한다.
사용자 변경 이름이나 실존 선수는 건드리지 않는다. 현재 정의 JSON과 그 hash만 원자적으로 바꾸고,
ID·국적·생일·PA·능력치·성장·계약 및 과거 고정 binding/canonical/receipt는 유지한다.
이름 결정성·충돌·재시도는 정책 테스트, 기존 정의 이주는 실제 file DB 복구 테스트로 확인했다.

## CL 정책과 실제 연결

`LCK_CL_GAME_POLICY_V1`은 **공식 CL 규정 전체 재현이 아닌 게임 정책**이다.
LCK 10개 구단의 기존 DEVELOPMENT 조직을 사용하며 구단 ID, 현재 운영 소속과 원계약 의미를 유지한다.

- 정규시즌 90 BO3, 팀당 18 Series, 4월 첫 월요일부터 18주간 매주 5경기. 2027년은 4월 5일~8월 2일.
- 동일 대진의 두 번째 경기에서는 첫 게임 선택권 보유 구단을 뒤집는다. 기존 ROFS/상대 RED,
  다음 게임 패자 선택권과 Hard Fearless를 그대로 사용한다.
- 상위 6팀 PO는 단일 탈락 5 BO5다. 3–6/4–5 승자는 각각 2위/1위와 SF를 치르고 결승으로 이어진다.
  QF/SF/결승 기본 날짜는 9월 1일/4일/7일이며, 지연 완료 시 현재 논리 날짜보다 과거로 생성하지 않는다.
- 정규 순위는 Series 승률 → 게임 득실 → 동률 집단 H2H → 총 게임 승리 수다.
  H2H로 집단이 분리되면 남은 집단을 다시 평가한다.
- 상위 6개 진출·시드의 완전 동률은 기존 유한한 `CareerDomesticTiebreak` BO1 대진을 재사용한다.
  2팀은 1경기, 3팀은 하위 2팀 경기 후 상위 bye 팀과 경기, 4팀은 준결승과 상·하위 결정전이다.
  5·9팀은 예선으로 4·8팀에 맞추고, 6·7·8·10팀은 기존 상·하위 재귀 대진으로 축소한다.
  대진 seed는 기존 H2H 게임 득실·승리 게임 시간 및 결정적 draw 규약을 사용한다(전력 가중치는 0).
  재귀 중 남은 2~3팀의 재분류도 기존 H2H 규약을 따른다. 완전 동률이면 다음 BO1로 진행한다.
  hash 자체로 경기 승자를 정하지 않고 실제 경기 승자는 검증된 결과로 결정한다.
  남은 7위 이하에 진출·시드 차이가 없으면 공동 순위로 두고 추가 경기를 만들지 않는다.

초기 정책은 구현 전에 이 보고서에 기록한 후 진행했다. 새 독립 CL instance/fixture는 competition·season으로
범위를 나누며 기존 1군 정규리그 90경기 완료 검사나 국제대회 규칙 리소스 hash를 바꾸지 않는다.
기존 Calendar 순서에 CL 날짜를 삽입하고 정규 결과 → 동률전 → QF → SF → 결승을 완료 영수증으로 확장한다.
활성 CL과 미적용 완료는 시즌 마감의 필수 검사다. 비활성 과거 CL이나 해외 육성 경기를 요구하지 않는다.

## 명부·계약·등록 준비

원본 LCK 육성 배치 집계는 54명이다. 이는 계약·현재 배치를 적용한 등록 인원과 다르다.
실제 대표 신규 T1 Career의 개막 전 준비는 다음과 같았다.

| 구단 | 원본 육성 배치 | 준비 후 현재 육성 배치 | 적격 CL 등록 | CL 선발 |
|---|---:|---:|---:|---:|
| BFX | 6 | 6 | 6 | 5 |
| BRO | 5 | 5 | 5 | 5 |
| DK | 6 | 6 | 6 | 5 |
| DNS | 5 | 5 | 5 | 5 |
| GEN | 6 | 6 | 6 | 5 |
| HLE | 6 | 6 | 5 | 5 |
| KRX | 3 | 5 | 5 | 5 |
| KT | 4 | 5 | 5 | 5 |
| NS | 5 | 5 | 5 | 5 |
| T1 | 8 | 8 | 8 | 5 |

이 Career는 전체 정의 및 생애주기 ACTIVE 460명, 생성 선수 0명이며 등록 56명/육성 배치 57명이다.
AI KRX는 기존 FIRST_TEAM **후보** Rich(TOP)·LazyFeel(ADC), KT는 후보 Jiwoo(ADC)를 배치했다.
이적/신규 영입을 만들어낸 사례가 아니며 기존 1군 주전 5명은 유지했다. HLE Valiant는 기존
`V4_REGISTERED_ROLE_REVIEW_REQUIRED`를 유지해 제외하고 현재 계약/자격을 통과한 5명만 등록한다. 관리 구단 T1은 Haetae/Painter/Guti/Cypher/Cloud를 사용자 확정 경로로 저장했다.
이 준비 결과는 해당 시작 자료의 사례이며 이후 모든 Career의 계약 상태를 보장하는 수치가 아니다.

현재 운영 구단·DEVELOPMENT 배치·유효 계약·은퇴/임대·포지션을 검사한다. CL 등록은 최대 15명,
Series 고정 선발은 각 포지션 1명씩 5명이며 기존 1군 lineups와 별도로 저장한다.
AI는 자신의 합법적인 1군 후보만 이동하고, 부족하면 기존 FA 협상으로 DEVELOPMENT 계약을 제안한다.
기존 재정/총명부 상한/선수 동의를 적용한다. 실제 포지션 공급 부족만 시즌당 한 번 기존 긴급 공급
규칙으로 보완하고 생성 선수도 FA로 시작한다. 재정 부족이나 협상 실패를 새 선수 공급 사유로 삼지 않는다.
기존 육성 수집의 2명 기준은 활성 LCK CL에는 적용하지 않으며 해외 56구단 전체를 강제 충원하지 않는다.

## 화면과 적용 시점

Career의 **LCK CL**에서 부족 포지션을 확인하고 선수 명부/시장으로 이동한 뒤 별도 CL 선발 5명을 확정한다.
Calendar에서 비관리 경기는 기존 durable Auto, 관리 경기는 기존 Player 또는 명시적 Auto 선택으로 실행한다.
CL 일정·결과에서 Auto도 실제 세트별 승자·시간·선수·챔피언을 볼 수 있다. Player checkpoint가 있을 때만
기존 Series 화면 열기를 제공한다. Auto에 존재하지 않는 Player 재생 경로를 노출하지 않는다.

선수 명부 상세에는 시즌·당시 구단·1군/CL별 Series 수, 완료 세트 수, 사용 챔피언과 성장 합계를 표시한다.
기존 훈련 화면은 1군/CL 기본 계획을 따로 저장하며 개인 override와 D+1 적용을 유지한다.
생애주기 화면은 실제 관찰 선수단·시작일·전체/부분/미관찰 구분과 생성 가상 본명을 보여준다.

승격/강등은 기존 MOVE_SQUAD, 1군 선발은 SELECT_STARTER를 사용한다. 승격된 CL 선발의 자리는 보완 상태로
남으며 자동으로 사용자 1군/CL 선발을 바꾸지 않는다. 새 경기만 현재 능력/숙련도·합법 5명을 고정한다.
이미 시작한 Series는 전 세트 같은 입력을 유지하고 국제 등록 pool·과거 binding/receipt는 다시 쓰지 않는다.

동일 playerId가 반대 선수단의 미적용 Series에 있거나 같은 논리 날짜에 반대 선수단으로 출전했으면
새 고정을 거부한다. 같은 선수단의 기존 국내 타이브레이커 당일 출전 규칙은 바꾸지 않는다.
일자 진행은 D일 경기 완료 → D일 훈련/회복 마감 → D+1 임대 반환·계약/이동·시장 순서다.
미적용 출전 binding이 있으면 명부 보완용 날짜 진행으로 이를 우회하지 못한다.

## 성장·약속·생애주기와 시즌 이월

선수 ID당 성장 상태는 하나다. CL 경기 계수는 1.0이며 게임당 기본 능력 60·챔피언 숙련도 80단위에
기존 나이·PA 감속을 적용한다. PA 상한 `12000 + floor((PA−1)×228000/199)`과 소수 나머지 누적을 유지한다.
CA가 PA와 같아도 내부 상한까지 성장할 수 있고, 초기 초과 선수는 보존하면서 추가 성장만 막는다.
PA는 AI 영입/가격/선발/임대 평가에 추가하지 않았다. 피로는 훈련 효율·회복에만 사용하며 직접 경기력 감점은 0이다.

실제 orderedGames의 선수 ID·챔피언·게임 수로 보상하고 동일 완료의 재적용을 막는다.
경기일 별도 훈련을 생략하고 임대 운영 구단의 훈련 및 자동 만료 반환, 다음 시즌의 공통 성장 상태를 유지한다.
CL 출전/기회는 1군 STARTER/RESERVE 약속의 분모·이행 수에 들어가지 않는다.
DEVELOPMENT는 계속 육성 배치 약속이며 새 CL 출전 보장 비율을 도입하지 않았다.

활성 LCK CL의 등록된 적격 후보에 실제 기회가 있으면 생애주기 관찰에 포함한다.
18주 정규 일정의 첫/마지막 날짜 차이는 119일이고 양 끝 날짜를 포함하면 120일이다.
CL의 최소 12기회/120일은 이 포함 일수로 판정한다. 기존 1군 기간 정의는 유지한다.
시즌 중 입단·임대·승격은 부분 관찰, 해외/미실행 육성은 중립이며 근거 없는 무출전 불이익을 만들지 않는다.

V21은 활성 시즌·도입일, 시즌별 등록/선발과 원본 UUID 명령 영수증, 실제 경기 성장 요약을 저장한다.
신규 Career는 첫 시즌에 CL을 만들고, 정상 로드 가능한 기존 Career는 다음 시즌부터 활성화한다.
현재 시즌을 소급 생성하지 않는다. 다음 시즌 CL 준비와 관찰 시작은 이월된 시장 날짜에 실행한다.
기존 file DB의 2027 비활성 → 2028 일정 90개 생성 → 재시작 보존을 확인했다.
원본 디렉터리 재import나 GET 재생성은 없으며 전역 카탈로그 불일치로 원래 로드 불가능한 저장 복구는 범위 밖이다.

## 실제 경기·브라우저 관찰

최종 대표 집중 실행과 브라우저는 같은 실제 Auto 결과를 사용했다.
Career는 `career_5485510065d93ae419cc46638441d1dd0da0932854ff22b01ddf8ee7847e4cba`,
CL Series는 `series_5f8c09879d6b108b4172f22f5bde3cce10a49c6db1b27e438f53fc0780bd742d`다.
`CL_R01_M1`의 **BFX CL 2–0 T1 CL**, Game 1 31분 10초/Game 2 33분 20초를 기존 production Auto로 완료했다.
대표 선수를 빠르게 확인하기 위해 기존 경기의 날짜만 격리 테스트 슬롯 2026-08-24로 앞당겼다.
이는 실제 2027 CL 시즌 전체를 실행했다는 주장이 아니다.

Haetae(`player-haetae`)의 전후:

| 항목 | 경기 전 | 2세트 완료 직후 |
|---|---:|---:|
| MECHANICS 내부 단위 | 15000 | 15078 |
| MECHANICS 정수/소수 값 | 15 / 15.000 | 15 / 15.078 |
| 다른 11개 능력치 | 기존 값 | 동일 |
| 능력치 내부 합계 | 172000 | 172078 |
| 표시 CA / PA | 141 / 164 | 141 / 164 |
| gnar TOP 숙련도 | 16000 | 16053 |
| varus TOP 숙련도 | 15000 | 15066 |
| 피로(내부 / 화면) | 0 / 0.0 | 180 / 18.0 |

실제 1 Series·2세트, gnar/varus 각 1회, 능력 성장 합계 +0.078점/숙련도 +0.119점이다.
정수 CA나 능력치가 올라갔다고 표현하지 않는다. BFX 1승/T1 1패와 세트 2–0이 CL 순위에 반영됐다.
원본 Auto 명령과 작업을 재전송해도 출전/성장은 추가되지 않았고 1군 약속은 동일했다.

같은 날 승격 후 1군 경기 고정은 거절됐으며 D+1의 새 Player Series
`series_419d951706b6bdaa1bed58a871cf60e285beb89ca098634c505a5734f85b9787`는 현재 선수 입력을 받았다.
브라우저에서도 CL 선발 재확정 → 세트 결과 → Haetae 출전·성장 → 1군 배치/선발 → 하루 진행 →
T1–KT Game 1 Draft의 Haetae TOP까지 확인했다. Player 전체 경기 완주는 추가 실행하지 않았다.

응답 복구 대표 사례는 CL POST가 서버에서 저장된 뒤 응답만 1회 차단했다.
새로고침 후 원본 CL 요청 버튼으로 같은 UUID를 재확인했고, 재저장 없이 요청이 정리되고 등록 상태가 유지됐다.
Playwright의 실제 화면 근거는 `output/playwright/career-cl-promoted-player-draft.png`와 임시 snapshot에 남겼다.
브라우저용 DB는 `/tmp/career-cl-browser/db`, 서버는 18187/5173이며 사용자 저장과 분리했다.
자동 승인 검토가 최초 승격 동작을 거절했으나, 첨부 15번의 명시적 허용과 격리 DB 근거를 제시한 동일 동작은
승인돼 완료됐다. 우회 경로나 사용자 데이터 변경을 사용하지 않았다.

대회 전이는 별도 **통제 결과** 90 정규 + 13 동률 + 5 PO = 108 Series로 종료했다.
정규의 역대진 양쪽이 첫 기회를 각각 이기도록 입력해 완전 동률을 만들었다.
최종 집중 사례의 상위 시드는 GEN, DK, KT, BFX, KRX, BRO였고 나머지는 공동 순위 표시다.
이 테스트는 appearance binding을 만들지 않았으며 실제 엔진 108경기·성장 증거로 보고하지 않는다.
초기 집중 개발 중에는 다른 Career seed의 실제 3세트 사례도 실행됐지만 최종 대표 수치는 위 2세트 사례로 통일한다.

## 검증 결과와 비용

새 테스트 파일은 `CareerClPolicyTest`와 `CareerClExecutionTest` 2개다. 나머지는 기존 생애주기·file DB·프런트 verifier를 확장했다.
모든 Gradle은 backend/에서 `--console=plain --no-daemon`, JDK `/tmp/career-development-jdk`로 순차 실행했다.

| 실행 | 결과 | 시간 |
|---|---|---|
| `test --tests 'com.lolfm.career.CareerLifecyclePolicyTest'` 첫 실행 | 17건 중 16 통과, 이름 fixture 1 실패 | 1분 19초 |
| 같은 선행 집중 재확인 | 이름 fixture의 새 nameSource 잔재 교정 후 17/17 | 32초 |
| CL 정책/실행 첫 집중 | 5/5 통과 | 3분 4초 |
| CL* + DevelopmentPolicy + MarketEngine + 기존 lifecycle file DB 대표 메서드 | 62/62 통과 | 4분 35초 |
| 다음 시즌 준비 시점 보완 후 lifecycle file DB 대표 메서드 + bootJar | 1/1 통과 | 3분 20초 |
| `npm run career:verify` | 89/89 통과 | 짧은 계약 verifier |
| `npm run build` 최종 | TypeScript와 Vite 통과 | Vite 8.19초 |
| Playwright 대표 흐름 및 응답 복구 | 위 실제 저장을 사용해 완료 | 단일 흐름, 별도 Auto 재실행 없음 |
| `./gradlew test --console=plain --no-daemon` | 269 suites / 2,094건: 2,087 통과·5 실패·오류 0·기존 skip 2 | 38분 22초 |

중간 컴파일 실패는 import 누락, 테스트의 비공개 메서드 호출/변수명 충돌, TeamSide 패키지 참조를 교정했다.
각 실패 단계는 전체 회귀나 실제 경기 성공으로 집계하지 않는다. Vite 최초 실행은 sandbox 포트 제한으로
실패했고, 허용된 임시 localhost 서버 권한으로 재실행해 브라우저 검증을 완료했다.
전체 로그는 `/tmp/career-cl-full-regression.log`, 최종 집중은 `/tmp/career-cl-focused-6.log`,
실제 경기 XML은 `/tmp/career-cl-execution-evidence-final.xml`에 보존한다. 전체 결과는 집중 재실행 전에 별도 보존한다.

## 적용 한계

CL 운영의 실무 흐름과 동일 선수의 성장/승격 연결을 제공한다. 해외 육성 리그, 모든 해외 1군 실행,
임시 콜업·조기 임대 복귀·새 계약 역할·코치 위임 모드·경기력 점수는 추가하지 않았다.
일일 표시/실행 모드가 승패를 바꾸는 별도 로직은 없지만 수동 Draft와 AI Draft의 서로 다른 선택까지 같다고 주장하지 않는다.
CL 계수 1.0과 장기 FA 공급·재정·은퇴 균형은 대규모 장기 시뮬레이션으로 검증하지 않았다.
원본 460명을 영구 인구 상한으로 취급하지 않는다. 과거 미수집 경기별 성장 요약을 소급 생성하지 않는다.
새로운 모든 구단/포지션/기기별 E2E, CL 3시즌·Career 10시즌·생애주기 20시즌 완주는 실행하지 않았다.


전체 결과의 실패 원문과 suite 집계는 `/tmp/career-cl-full-evidence/`에 보존했다.
실패 원인은 다음과 같으며 제품 런타임을 변경하거나 검증을 제외하지 않았다.

- CareerDomesticExecutionTest와 LeagueAutomatedSeriesRunnerProductionV9Test: 초기 명부 revision을
  0으로 고정했다. CL의 합법적인 AI 후보 배치가 revision을 올리므로 각 명령에서 현재 revision을 읽는다.
- CareerApiV1ControllerTest: 전체 대회 12개 기대값을 기존 대회 12개 + CL 1개/90경기로 분리했다.
- LeagueRelationalPersistenceAndJobTest: V1 이후 최신 이주 수를 19에서 20으로 교정했다.
- CareerModePersistenceTest: V4 이후 최신 이주 수를 16에서 17로 교정했다.

후속 실행은 이 다섯 실패 메서드 전체다. 국제 고정/후보 pool·실제 League Auto/성장 입력·
동시 시작/동일 파일 재시작·과거 foundation binding/날짜 보존에 대한 원래 단언을 유지한다.
최초 전체 실행에서 조기 실패로 도달하지 못한 부분까지 이 집중 실행으로 확인한다.
위 다섯 수정은 테스트 내부 준비/개수 단언에 한정됐고 공유 런타임·Random·실행 순서 변경이 없어
해당 메서드 전체를 통과한 뒤 두 번째 전체 실행은 하지 않았다. 최종 트리 전체 통과를 주장하지 않는다.


전체 실패 후속 5개 메서드는 **5/5 통과, Gradle 6분 18초**였다.
그 뒤 다음 두 호환 조건을 추가 보완했다.

1. 생성 이름의 기본 placeholder 판별에 상세 `name`도 포함했다. `name`이나 `nickname` 중 하나만
   사용자가 수정했어도 자동 이름 이주를 하지 않는다. 현재 능력치/계약/고정 경기 입력 변경은 없다.
2. CL 도입 전 훈련 V1 요청에는 `squad`가 없었다. 기존 1군 요청의 원본 payload hash도 확인해
   과거 영수증을 그대로 재확인할 수 있게 했다. 같은 UUID를 DEVELOPMENT 요청으로 바꾸면 계속
   충돌로 거절한다. 기존/새 receipt JSON과 hash를 다시 쓰지 않는다.

추가 검증은 기존 이름 정책 1개 메서드와 생성 이름/훈련의 file DB 메서드 2개다.
이 조건은 표시 메타데이터의 이주 대상과 훈련 명령의 기존 영수증 조회에만 영향을 주며,
경기 Random·계산·대회 전이나 일반 시장 코드 변경이 아니다. 해당 단위·저장·재시작 경계로
영향 범위를 확인하며 전체 재실행 사유로 확대하지 않는다.


호환 보완 후 최종 집중은 **3/3 통과, Gradle 2분 36초**다.
전체 회귀는 총 **1회**이며 38분 22초의 최초 결과는 실패 5건으로 보존한다.
그 뒤 실패 메서드 5건과 호환 보완 메서드 3건이 각각 통과했으며, 최종 트리 전체 재실행 성공으로 표기하지 않는다.

재현할 집중 selector는 다음과 같다. 각 명령은 backend/에서 `./gradlew test` 뒤에 사용했고
끝에 `--console=plain --no-daemon`을 붙였다.

```text
# CL/성장/시장/저장 집중 62건
--tests 'com.lolfm.career.CareerCl*Test'
--tests 'com.lolfm.career.CareerDevelopmentPolicyTest'
--tests 'com.lolfm.career.CareerMarketEngineTest'
--tests 'com.lolfm.league.CareerModePersistenceTest.lifecycleReviewIsAtomicGeneratedPopulationSurvivesLateRolloverAndFileRestart'

# 전체 실패 후속 5건
--tests 'com.lolfm.application.CareerDomesticExecutionTest.oldInternationalFiveAndNewRegistrationPoolHaveDifferentEligibilityBoundaries'
--tests 'com.lolfm.controller.CareerApiV1ControllerTest.createListGetReplayConflictAndStrictErrorsPreserveLeagueState'
--tests 'com.lolfm.league.CareerModePersistenceTest.v4CareerMigratesToFrozenV5CalendarWithoutBackdatingFoundationBinding'
--tests 'com.lolfm.league.LeagueAutomatedSeriesRunnerProductionV9Test.selectedReserveRunsThroughActualLeagueAutoAndFrozenReceiptValidation'
--tests 'com.lolfm.league.LeagueRelationalPersistenceAndJobTest.migratesEmptyAndPreviousSchemaThenRestartsFromSameFile'

# 마지막 이름/훈련 호환 보완 3건
--tests 'com.lolfm.career.CareerLifecyclePolicyTest.generatedNamesAreStableResolveCollisionsAndPreserveGameplay'
--tests 'com.lolfm.league.CareerModePersistenceTest.lifecycleReviewIsAtomicGeneratedPopulationSurvivesLateRolloverAndFileRestart'
--tests 'com.lolfm.league.CareerModePersistenceTest.developmentMigrationPlansDailyMarketOrderAndFileRecovery'
```

최종 HEAD도 `1656f4de0cb43fba8e1255ec0f927671f143429e`, 브랜치 `main`이며 이번 변경은 미커밋 상태다.
임시 Playwright와 18187/5173 서버를 종료했다. `git diff --check`는 통과했다.
알려진 미해결 결함은 이번 검증 범위에 없으며, 위에 명시한 기능 범위와 장기 균형 미검증은 그대로 남긴다.
한글 커밋 메시지 제안: `feat: Career LCK CL 실행과 성장·승격 경로 통합`.
