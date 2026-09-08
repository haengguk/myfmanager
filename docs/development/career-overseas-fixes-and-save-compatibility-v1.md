# Career 해외 실행 선행 수정과 저장 호환 V1

기준/시작 HEAD는 `915c7281a660f99d7a5a3389498c4bd799b2ed99`, 브랜치는 `main`이다.
사용자 `prompts/`, `선수정보.zip`과 선수·일정·능력치·연봉·상금/예산 원본은 보존한다. 커밋·push·배포는 하지 않는다.

## 사용자 동작과 선행 결함

| 경계 | 기존 원인 | 수정된 동작 |
| --- | --- | --- |
| 국제 등록 전 명부 공백 | `ROSTER_REPAIR_REQUIRED:LPL:AL` 문자열과 null currentCompetition 때문에 복구 날짜 진행을 찾지 못함 | typed wait의 code/competition/requiredEvent/team/owner/responsibility를 조회한다. fixture가 없어도 현재/지난 국제 등록 대기를 확인한다. |
| 해외 단계 날짜 | 공통 knockout이 모두 event.post를 시작점으로 사용 | runtime stage별 시작 하한과 정규/Swiss/동률전/선행 단계 확정을 함께 적용한다. |
| LPL 최종 순위 | `max(PO 인원+1, 정규순위)` fallback이 서로 다른 탈락 구간을 겹치게 함 | 실제 Knights 탈락 단계와 미진출 그룹으로 구간을 배정하고, 공동 순위를 확장한 1..N 완전성을 검사한다. |

G1은 일반 시장의 예산·급여·후보·계약 효력 검사를 그대로 사용한다. 한 명령은 다음 시장 사건 또는
최대 다음 하루까지만 진행한다. 정상 시장 날짜 처리 뒤 같은 국제 인스턴스를 reconcile하며 등록을 한 번 확정한다.
미완료 독립 경기, 사용자 대회 선택, 진행 중 명령, 미정산 출전, 시즌 lifecycle 차단을 유지한다.
GET은 진출·명부 조건만 읽으며 영입·선발·날짜·등록을 저장하지 않는다.
이미 명부 복구를 마친 경우에는 과거 대기 문자열 대신 현재 등록 준비 완료를 확인하여 다음 명령을 허용한다.
그 명령이 같은 인스턴스를 등록하며, 별개 미완료 경기나 대기 명령이 있으면 계속 차단한다.
UI는 AI 구단/관리 구단을 구분하며 후보 부재, 미지급 급여, 현금/급여 한도 부족, 선발 자격 확인을 표시한다.

통제 FST 등록 경계에서 AL의 TOP을 정상 방출한 뒤, 합법 예산으로 FA 제안을 제출하고 실제 Calendar 명령으로
7일을 진행했다. AL은 **Breathe / Tarzan / Shanks / Hope / Kael**로 등록됐다.
이는 선행 지역 대회를 실제 시즌 전체로 실행한 증거가 아니다. 지역 진출 결과만 통제했고 시장 반응·계약 시작·명부 복구·등록은 제품 경로다.
같은 명령 UUID 재요청은 재처리되지 않으며, 별개 LEC 미완료 경기를 넣으면 날짜 진행은 계속 차단된다.

## 단계 규칙과 순위 의미

`CareerOverseasRules.stageStart`가 실행 하한을 소유한다. `overseas-execution-corrections-v2.json`은
17개 이벤트의 원본 stage ID와 날짜, runtime mapping, 근거/게임 보완 구분을 기록하는 채택 자료다.
기존 `overseas-rules-2026-v1.json`과 원본 일정 팩은 수정하지 않는다.

- LPL Split 1 PO: 2월 24일 이후. Knights 2월 9일 이후와 분리한다.
- LPL Split 2 PO: 5월 29일 이후. Split 3 PO: 8월 29일 이후.
- LCP Split 3 PO: 8월 29일 이후. Swiss/Final Seeding과 분리한다.
- CBLOL Copa PO: 2월 7일 이후. Play-in과 분리한다.
- REGULAR/SWISS는 기존 시작일, TIEBREAKER는 정규 종료 다음 날을 게임 하한으로 사용한다.
- 나머지 PO/Knights/Play-in/Last Chance/Final Seeding/Regional Finals는 채택 자료와 기존 event.post 매핑을 따른다.
  공식 상세 날짜가 없는 LCP/LCS 단계는 기존 게임 날짜를 사용하며 공식 확정일로 표시하지 않는다.

연도 투영은 기존 `SAME_LOCAL_MONTH_DAY_FROM_2026_REFERENCE_V1`이다. 선행 결과, 같은 팀의 하루 별개 Series 충돌,
현재 Career 날짜 및 국내·국제 일정 조정을 거친다. 종료일+7일 안에 넣지 못하면 기존 유한 실패를 유지한다.
fixture ID와 seed는 날짜가 아닌 기존 구조적 match ID로 유도한다.

LPL Split 1은 Knights 최종 탈락 두 팀 공동 9–10위, 앞 단계 탈락 두 팀 공동 11–12위,
나머지 13/14위를 배정한다. Split 2는 Knights 탈락 네 팀 공동 9–12위 후 13/14위,
Split 3은 탈락 두 팀 공동 9–10위 후 11/12위다. PO 공동 5–6위/7–8위는 유지한다.
`regularRanking`은 정규 순위, `placements`는 최종 공동 구간의 시작 순위다. `ranking`은 최종 구간 순서이며
동일 구간 내 표시 순서는 정규 순위(없으면 입력 참가 순서)를 쓰며 공동 순위 값은 바꾸지 않는다. CP와 상금은 최종 placements, 국제 진출은 해당 최종 결과/CP를 사용한다.
하위 구간 상금이 0원이었던 사례에서 현금 오지급이 확인됐다고 주장하지 않는다.

## 기존 이벤트 적용 시점

| 상태 | G1 | G2/G3 |
| --- | --- | --- |
| 등록 전 현재 교착 | 현재 저장에서 구조적 재평가와 정상 시장 명령으로 복구 | 해당 해외 이벤트 사용 여부 정책을 따름 |
| 미사용 이벤트 | 정상 등록 경로 | binding/application 없음, 모든 fixture READY일 때 V2 채택 |
| 시작된 이벤트 | 미확정 국제 등록 공백은 정상 복구 | V1 투영 유지. 고정 fixture/입력/receipt와 충돌하는 재작성 없음 |
| 완료 이벤트 | 닫힌 등록 보존 | 저장된 plan/최종 순위/지급·자격 근거 보존 |
| 다음 적용 가능 시즌/새 이벤트 | 정상 경로 | `CAREER_OVERSEAS_STAGE_AND_PLACEMENT_V2` |

원래 activation rule hash는 당시 근거이며 변경하지 않는다. 이벤트 State의 별도 policyVersion으로 실행 교정 버전을 구분한다.
과거 기록을 다시 쓰거나 소급 지급하는 도구는 없다.

## 저장 호환 정책

| 대상 | 선수 데이터 authority |
| --- | --- |
| 새 Career | 생성 시 최신 authored + 기존 글로벌 편집 snapshot |
| 기존 Career | 저장된 directory와 시즌 명부, 계약/임대·성장·생애주기 상태 |
| 기존 Career의 다음 시즌 | 해당 Career에서 진행한 상태 이월; 기본 능력치/PA 재import 없음 |
| 시작한 Series/Draft/checkpoint | 기존 고정 roster/능력치/hash/receipt |
| 미시작 경기 | 해당 Career의 현재 적법한 명단과 성장 기량으로 입력 고정 |
| 닫힌 국제 등록 | 기존 등록 roster/후보 범위 유지; 기존 정상 보충등록 정책만 허용 |

`CareerSaveCompatibility`는 저장 소유 선수/조직 ID, directory hash, 현재 시즌 명부 및 시즌 roster hash를 검증한다.
원래 reference version/hash는 생성 provenance로 남기며 bindingHash를 재서명하지 않는다.
Career/seed/binding/linked season/frozen/product/명령 receipt 무결성 검사는 유지한다.
자료 갱신과 실제 내부 손상은 다른 상태다. 지원 상태는 목록/상세의 additive `compatibility` 필드로 전달한다.
현재 카탈로그에서 관리 팀이 없어도 저장된 `LCK:<teamCode>` CLUB 정의가 있으면 그 저장을 사용한다.
목록의 지원 불가 저장 때문에 지원 가능한 다른 저장을 일괄 차단하지 않는다.
지원하지 않는 directory 버전은 startup 선수/시장/성장/생애주기/CL/해외 복구에서 제외하며 JSON을 현재 구조로 해석하지 않는다.
완료된 생성 명령은 관리 팀이 설치 카탈로그에서 사라져도 원래 UUID와 receipt로 재요청할 수 있다.

최소 지원 형식은 기준 커밋의 정상 V23 저장과 이후 생성 저장, `EXPANDED_PLAYER_DIRECTORY_V1` 선수 디렉터리다.
이미 저장된 전체 directory, 시즌 frozen snapshot, 개별 성장/계약/생애주기 테이블을 재사용한다.
새 SQL migration과 중복 전체 스냅샷 저장은 추가하지 않았다. 기존 migration은 변경하지 않았다.
필수 명부가 없는 옛 저장은 원래 reference가 일치할 때 기존 startup 경로만 사용한다.
새 기본 데이터를 옛 자료처럼 가져오지 않는다. 조회 때마다 import하거나 사용자 상태를 초기화하지 않는다.

| 상태/오류 | 의미와 안내 |
| --- | --- |
| SUPPORTED / SAVED_CAREER | 저장된 자료로 진행 가능. sourceChanged는 현재 설치 자료와 생성 provenance 차이 안내 |
| SUPPORTED / LEGACY_MATCHING_REFERENCE | 아직 directory가 없는 구형 저장. 일치하는 원본으로 기존 startup 복구 필요 |
| CAREER_SAVE_COMPATIBILITY_DATA_MISSING (409) | 옛 선수/현재 시즌 명부/고정 입력 없음. 생성 당시 자료 또는 보존된 원본 저장 필요 |
| CAREER_SAVE_COMPATIBILITY_VERSION_UNSUPPORTED (409) | 저장 directory 형식을 지원하지 않음. 해당 형식을 지원하는 버전 필요 |
| CAREER_SAVE_COMPATIBILITY_ORGANIZATION_UNSUPPORTED (409) | 관리 구단의 구조적 조직 정의 없음. 원래 조직 정의가 보존된 저장 필요 |
| CAREER_RESOURCE_INTEGRITY_FAILURE / CAREER_LINKED_SEASON_INTEGRITY_FAILURE | 내부 hash/binding/연결 손상. 데이터 갱신 허용으로 우회하지 않음 |

모든 미래 경기 엔진, 챔피언·아이템, 대회 구조의 무조건 호환을 보장하지 않는다.
특히 `MatchEngineV1Policy.APPROVED_RESOURCE_PROVENANCE_SHA256`에 포함된 승인 엔진 원본
(`PlayerRatingCatalog`의 고정 50인 팩 포함)을 교체하는 작업은 Career authored/PA/전역 편집 갱신과 다르다.
그 경우 기존 엔진·League/Series authority 검사를 그대로 유지하며 별도 엔진 버전/호환 작업이 필요하다.
이번 B는 Career 입력 snapshot과 참고 provenance의 교체이며 승인 엔진 팩은 변경하지 않는다.
다음 경기 입력은 실제 League handoff의 production authority 검사를 통과해 저장된 선수로 전달되는 경계까지 확인한다.
선수 이름/표시 순서로 ID를 추정하지 않으며, 정상 게임 내 성장·이적·은퇴·신인 공급은 계속 진행한다.

## 구현 경로

- `CareerRegistrationWait`, `CareerOverseasQualification`, `CareerInternationalCompetition`: 구조적 등록 대기와 책임.
- `CareerCompetitionApplicationService`, `CareerCalendarApplicationService`: 등록 전 조회, 독립 게이트, 시장 날짜 처리 후 등록 재개.
- `CareerOverseasRules`, `CareerOverseasTournament`, `CareerOverseasStore`: 단계 하한, 최종 순위 구간, 버전별 적용 경계.
- `CareerSaveCompatibility`, `CareerApplicationService`, `CareerRelationalStore`: 생성 provenance와 저장 무결성 분리, 지원 상태.
- `CareerRosterStore`, `CareerSeasonRosters`, `CareerCompetitionRelationalStore`: 저장 authority 우선과 원본 불명 상태의 재import 방지.
- `CareerApiV1Dtos`, mapper/exception handler: additive 상태/409 오류.
- Career dashboard/calendar 및 API validator: 저장 진입 안내, 지원 불가 이유와 구단 복구 문맥.

## 검증 기록

새 테스트 클래스는 0개다. 기존 순수 projection/자격, 공유 실행, 파일 DB, API 테스트를 확장한다.
마지막 30건 집중 명령은 [testing.md](testing.md)의 이번 작업 절에 기록했다. G1과 파일 DB의 별도 집중 선택자는 다음과 같다.

```bash
# backend/에서 각각 순차 실행
JAVA_HOME=/tmp/career-development-jdk ./gradlew test --tests 'com.lolfm.career.CareerOverseasExecutionTest.registrationWithoutFixturesRepairsThroughMarketDatesAndKeepsIndependentGates' --console=plain --no-daemon
JAVA_HOME=/tmp/career-development-jdk ./gradlew test --tests 'com.lolfm.league.CareerModePersistenceTest.savedPlayerAuthoritySurvivesCatalogReplacementAndFileRestart' --console=plain --no-daemon
# 전체 회귀
JAVA_HOME=/tmp/career-development-jdk ./gradlew test --console=plain --no-daemon
# frontend/에서
npm run career:verify
npm run build
```
G2/G3 최초 작은 재현은 4건 중 2실패/2통과(33초), 수정 후 Tournament/Qualification 집중 28건은 통과(1분 19초)했다.
G1 실제 시장 날짜/등록 경계 1건은 통과(4분 52초)했다.
프런트 `npm run career:verify` 106건과 `npm run build`(Vite 7.61초)가 통과했다.
브라우저는 격리 API fixture로 현재 B 카탈로그에서 GEN이 제외된 상태에서 저장된 GEN의 상세 진입/보존 안내/새로고침을 확인한다.
Vite HMR WebSocket은 브라우저 로컬 네트워크 정책으로 연결되지 않았으나 HTTP 로드와 기능 확인에는 영향이 없었다.
상세 보조 패널의 404는 이 화면 fixture에서 응답을 제공하지 않은 범위이며 실제 API 저장 검증을 대체하지 않는다.

집중 실행의 테스트 준비 오류(접근자/오류 상수, 포지션별 능력치 필드)와 additive API 필드 기대값은 제품 assertion을 약화하지 않고 수정했다.
통제 완료 helper의 기본 참가자가 이미 고정한 선수와 달라 `DEVELOPMENT_ASSIGNMENT_BINDING`으로 거절됐다.
해당 두 fixture에 저장된 참가자를 전달했다. 경기 입력 고정 후 날짜를 먼저 진행했던 준비도
`DEVELOPMENT_COMPLETION_SCOPE`로 거절되어, 훈련 후 입력을 고정하고 같은 논리 날짜에 완료하도록 순서를 교정했다.
한 집중 실행은 과도한 장기 시장 준비 비용을 확인해 중단했다. 통과로 집계하지 않는다.
최종 fixture는 2027-12-20을 명시적으로 준비한 뒤 시장/훈련 8일, B 재시작 및 12월 31일→2028년 전환을 확인한다.
Calendar 등록 복구 자체의 실제 허용/명령 경로는 별도 G1 사례가 검증한다.
마지막 결합 집중은 4 suites/30건 통과(4분 15초)다. 그 뒤 같은 파일 DB 사례에 실제 League handoff와
가격 동일성 단언을 보강했고, 해당 1건이 4분 7초에 통과했다(테스트 본체 206.541초).
새 테스트 사례는 총 7건이며 새 클래스는 0개다. 기존 API 계약 사례는 additive 필드만 함께 확인하도록 확장했다.

대표 선수는 KT의 `player-cuzz`다. 저장 A의 정수 기량은 MECHANICS 17, LANE_INTERVENTION 19,
나머지 정글 능력치 17~18, 표시 CA 176 / PA 188이다. 소수 성장과 피로가 실제로 생긴 후 저장했다.
B는 정글 12개 기량을 모두 5(표시 CA 43), PA를 60으로 바꾼 입력이며 현재 참고 카탈로그에서 KT도 제거했다.
재시작 후 기존 저장의 directory/시장(계약 포함)/성장/선발 JSON과 생성 bindingHash가 동일했고,
선발·훈련·생성 UUID 재요청도 원래 receipt를 반환했다. 시장 askingSalary는 A와 동일한 양수였다.
다음 경기 입력은 A 계열 기량이며 실제 production authority를 검사하는 League handoff에서
kernel로 전달되는 frozen roster까지 확인했다. kernel은 입력 검증용 대역이므로 실제 경기 완주 주장은 하지 않는다.
2028년 이월 후에도 PA 188과 Career 기량을 사용하고 원본 directory/앞서 고정한 경기 입력은 그대로다.
새 Career의 Cuzz는 B 정의 전체와 일치했다. 같은 파일에 directory hash 손상과 미지원 버전을 각각 주입해
실제 손상 거절, 미지원 저장만 목록에 구분 표시, 지원 저장의 정상 진입과 복구 제외를 확인했다.

전체 백엔드 회귀는 **1회, 44분 13초**, 274 suites/총 2,162건 중 **2,159 통과·1 실패·기존 skip 2**다.
원본 로그·XML·HTML·집계를 `/tmp/career-compat-full-evidence/`에 보존했다.
실패는 `LeagueAutomatedSeriesRunnerProductionV9Test.calendarDateAdvanceCapturesSettledStartAndAppliesActualAutoExactlyOnce`의
신인 선발 준비에서 발생했다. 신인 영입 후 수개월의 AI 시장 운영을 거쳐도 원래 구단에서 계속 선발할 수 있다고
가정했으나 `CareerMarketEngine.select`의 계약/현재 소속 자격 검사에서 거절됐다. 자동 이동을 금지하거나
예산·계약 검사를 완화하지 않고, 경기 직전 협상 기간에 영입하도록 해당 fixture의 순서를 수정했다.

전체 실행 중 코드 검토로 별도 G1 경계도 확인했다. 명부를 이미 복구했지만 등록이 아직 미확정이면,
이전 `WAITING_FOR_QUALIFICATION` 때문에 GET의 진행 허용 목록이 비어 있었다. 기존 G1 fixture의 rollback 분기에서
정상 명부/미확정 등록을 준비하고 조회 무변경·독립 경기 차단·실제 등록 명령을 확인한다.
후속 1차 집중은 2건/5분 9초다. 실제 Auto 사례는 통과했고(테스트 본체 240.373초),
추가 G1 사례는 예상한 `allowedAdvanceModes: []`로 실패하여 새 경계를 재현했다.
Auto는 2027-03-31 준비 → 04-01 입력/성장 정산 → 실제 2세트 완료·receipt/중복 정산 방지를 확인했다.
G1 제품 gate를 수정한 뒤 같은 G1과 직접 연결된 기존 Calendar/API 사례 **2건이 모두 통과했다(8분 5초)**.
명부 복구 완료 상태의 조회 무변경·독립 경기 차단·명령 등록과 기존 실제 7일 복구/중복 요청 경로가 모두 통과했다.
후속 원본과 집계는 `/tmp/career-compat-post-full-evidence/first/`, `final/`에 분리 보존했다.
공유 엔진/Random/저장 형식은 바꾸지 않으며,
위 로컬 gate와 해당 Calendar/API 호출자, 실패한 실제 Auto를 한정 검증했으므로 추가 전체 실행은 하지 않았다.
미해결 실패는 없다. 원본 전체는 1건 실패했으며 최종 tree의 clean full 통과로 보고하지 않는다.

## 남은 범위

연속 진행/AI 자동 처리 V1, 누적 기록 화면, 성장·경제 재조정, 해외 감독, 새로운 대회는 포함하지 않는다.
아시안게임 제외와 KeSPA 비활성은 유지한다. 기존 확정 역사 재작성은 제공하지 않는다.

## 최종 작업 상태

최종 HEAD도 `915c7281a660f99d7a5a3389498c4bd799b2ed99`, 브랜치는 `main`이다.
커밋·push·배포 없이 working tree에 결과를 남겼다. 사용자 `prompts/`, `선수정보.zip`과 원본 데이터는 보존했다.
대표 화면 확인에 사용한 `career-compat` Playwright 세션과 Vite 5177 서버를 종료했다.
실제 사용자 DB는 시험 수정하거나 초기화하지 않았다. 검증 로그/격리 fixture는 `/tmp` 및 기존 ignored build 경로에만 남겼다.
`git diff --check`는 통과했다.

제안 커밋 메시지: `feat: 해외 대회 실행 결함 수정 및 기존 Career 저장 호환 지원`
