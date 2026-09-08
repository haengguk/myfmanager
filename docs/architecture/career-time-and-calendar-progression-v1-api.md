# Career Time and Calendar Progression V1 API

## 기능과 authority 경계

Career Calendar는 저장별 `currentDate`, 활성 calendar year, event cursor, calendar revision과
state hash를 소유한다. 경기 결과, standings, fixture lifecycle, job/lease, Player Series와 Match는
기존 League/Series authority가 계속 소유한다. Calendar는 그 상태를 구조적으로 조회하고 날짜 이동을
허용할지 결정할 뿐 경기 결과를 직접 만들지 않는다.

2026년 자료의 일정·포맷은 reference fact다. 2027년 이후 날짜는
`SAME_LOCAL_MONTH_DAY_FROM_2026_REFERENCE_V1` 게임 정책으로 같은 현지 월·일에 투영한 값이며 공식
발표가 아니다. exact date가 없는 stage와 pending field는 미래 연도에도 null/pending이다.

Targeted hardening 이후 `GET /calendar`는 완전한 read-only boundary다. Competition cycle 생성,
V1→V2 승격, R1~2 seal은 각각 새 Career 생성, startup recovery, 명시적 advance transaction이
소유한다. GET 반복이나 reload는 revision/hash/updatedAt/fixture/job/Series를 변경하지 않는다.

## 조회 계약

`GET /api/v1/careers/{careerId}/calendar`는 `CAREER_CALENDAR_VIEW_V1`을 반환한다.

- 저장 상태: `currentDate`, `activeCalendarSeasonYear`, `calendarRevision`, lifecycle/blocking reason,
  `calendarStateHash`
- provenance: reference year 2026, source as-of `2026-08-23`, catalog snapshot `2026-08-24`, template
  version/hash와 세 projection/allocation policy
- 일정: current/next event와 stage, bounded upcoming events/fixtures, 다음 관리 경기
- 상태 구분: official status, future-year projection status, execution status, schedule status
- 구조화 자료: qualification edge, pending official field, KeSPA Cup reference/source-gap note
- 복구 자료: Career별 `activePendingAdvance`의 UUID/mode/original revision/status/timestamp
- 대회 자료: additive `competition`의 current/next competition·실제 stage, lifecycle revision/hash,
  next fixture/Series, selector/resolved team, managed 여부, binding/job/application 상태, group standings,
  seed, qualification output과 source/external blocker
- 대회 복구: `activePendingCommand`의 server-owned UUID/competition/match/status와
  authoritative `allowedCommands`

응답은 12개 정의를 표시한다: LCK Cup, First Stand, LCK 정규 R1~2, LCK Road to MSI, MSI,
EWC LoL, LCK 정규 R3~4, LCK 플레이인, LCK 플레이오프, 아시안게임 LoL 국가대표 차출 창,
Worlds, KeSPA Cup. KeSPA는 실행 definition이 아니라 `REFERENCE_TEMPLATE_ONLY` instance다.
`sourceDataNote`는 `subject=KESPA_CUP`, `sourceReferenceYear=2025`,
`ruleVersion=KESPA_CUP_REFERENCE_TEMPLATE_2025`,
`status=REFERENCE_TEMPLATE_NOT_OFFICIAL_FOR_2026_OR_FUTURE`와 두 blocker
`KESPA_CUP_2026_RULE_SOURCE_INCOMPLETE`, `EXTERNAL_PARTICIPANT_ROSTER_AUTHORITY_MISSING`를 반환한다.

Source-complete 국내 대회의 event `executionStatus`는
`LINKED_COMPETITION_SERIES_EXECUTION`, 기존 R1~2는 `LINKED_EXISTING_LEAGUE_FIXTURES`, 국제/source-gap
대회는 `FORMAT_DEFINED_EXECUTION_NOT_IMPLEMENTED` 등 기존 제한 상태를 사용한다. Frontend는 이 값과
`stageId`를 표시 문자열에서 재구성하지 않는다.

## 국내 대회 Series command 계약

두 additive endpoint가 같은 exact request field를 받는다.

| Method/path | 의미 |
| --- | --- |
| `POST /api/v1/careers/{careerId}/competition/start-or-resume` | 현재 due fixture binding 생성 후 Player Series 시작/재개 또는 Auto job 생성·제출 |
| `POST /api/v1/careers/{careerId}/competition/reconcile` | server가 투영한 기존 command UUID로 Player completion 적용 또는 Auto job 재제출/상태 확인 |

```json
{
  "schemaVersion": "CAREER_COMPETITION_COMMAND_REQUEST_V1",
  "expectedCompetitionRevision": 0,
  "clientCommandId": "00000000-0000-4000-8000-000000000001"
}
```

응답 `CAREER_COMPETITION_COMMAND_RESPONSE_V1`은 execution mode, fixture/match/Series/binding/job ID,
status, replay/background accepted/failure code만 반환한다. Player/Auto 팀, side, seed, winner, score,
receipt와 output을 request로 받지 않는다. 미래 날짜 fixture는 allowed command가 없고 직접 호출도
`CAREER_COMPETITION_FIXTURE_NOT_DUE` 계열 409로 거부한다.

## 날짜 진행 계약

`POST /api/v1/careers/{careerId}/advance` 요청은 다음 exact 필드만 허용한다.

```json
{
  "schemaVersion": "CAREER_CALENDAR_ADVANCE_REQUEST_V1",
  "expectedCalendarRevision": 0,
  "mode": "ADVANCE_ONE_DAY",
  "clientCommandId": "00000000-0000-0000-0000-000000000001"
}
```

지원 모드는 `ADVANCE_ONE_DAY`, `ADVANCE_TO_NEXT_EVENT`뿐이다. 다음 일정은 competition start와
R1~2 fixture date 중 가장 가까운 날을 선택하므로 경기일을 뛰어넘지 않는다. 성공 응답 schema는
`CAREER_CALENDAR_ADVANCE_RESPONSE_V1`이며 `replayed`, `pending`, `stopReason`,
`backgroundAccepted`, receipt에 동결된 `commandResult`, 최신 Calendar view를 포함한다.

`commandResult`는 original mode/expected revision/result date/revision/hash/stop reason과 receipt
timestamp를 보존한다. replay 시 이 값은 변하지 않으며 `calendar`만 현재 authoritative state를
보여 준다. historical command state와 live fixture projection을 한 view로 혼합하지 않는다.

같은 UUID와 같은 canonical payload는 exact replay이며 mutation 0이다. 같은 UUID의 다른 payload,
stale revision, 다른 pending command는 409다. Calendar row와 receipt는 DB global advance lock 아래
한 transaction으로 갱신되며 날짜·cursor는 단조 증가한다. 재시작 뒤에도 pending/completed receipt와
state hash를 검증해 같은 command를 정확히 한 번만 이어 간다.

V5 legacy `PENDING` row에 `request_mode`/`request_expected_revision` 증거가 없으면 payload hash나
현재 revision에서 값을 추측하지 않는다. Startup recovery는 row를 보존하고 Calendar GET을
`advanceRecoveryStatus=LEGACY_PENDING_RECONCILIATION_REQUIRED`와 같은 blocker로 정상 반환한다.
명시적 cancel/reconcile 정책 전까지 새 advance는 차단된다.

R1~2 경기일에는 기존 18라운드/90 fixture만 gate에 참여한다. 같은 날짜의 미완료 FULL_AUTO fixture는
기존 durable League job 경로로 dispatch하고 202 `AUTO_FIXTURES_PENDING`에서 멈춘다. 관리 경기는
`MANAGED_FIXTURE_REQUIRED`, blocked/cancelled/restart-required 상태는 `ATTENTION_REQUIRED`로 멈춘다.
pending job/outbox나 미완료 관리 경기를 지나 currentDate를 commit하지 않는다. 마지막 정의 이후에는
`SEASON_ROLLOVER_REQUIRED`가 된다. Calendar advance는 다음 시즌을 자동 생성하지 않으며,
아래 명시적 시즌 전환 명령이 마감 검증과 생성을 담당한다.

Season lifecycle은 gate 전에 검사한다. PAUSED/BLOCKED/CANCELLED/DRAFT/FROZEN은 각각 structured
reason으로 dispatch와 날짜 이동을 0으로 만든다. COMPLETED는 90개 fixture 완료가 확인된 경우에만
Competition authority가 R1~2 standings를 봉인하고 이후 날짜를 진행한다. Road/R3~4의 새 fixture
date도 next-event 후보이며 미완료 fixture를 건너뛰지 않는다.

## 오류와 호환성

기존 `CAREER_API_ERROR_V1` boundary를 유지한다. 잘못된 요청은 400, Career/Calendar 없음은 404,
command conflict·stale revision·pending conflict는 409, migration/integrity는 fail-closed 500,
background wake 실패는 503이다. 기존 create/list/detail, League/Series/Match API와 gameplay Random,
fixture ID/root seed/standings 의미는 변경하지 않는다.

Competition lifecycle의 현재 구현과 source-gap은
[Career Competition Lifecycle V1](career-competition-lifecycle-v1.md)을 따른다. Road/R3~4/Play-in
graph와 LCK Cup 40경기는 Competition Series adapter와 verified result transition으로 실행 가능하다.
현재 날짜 이전의 첫 non-terminal fixture는 READY/WAITING과 무관하게 overdue gate로 다시 잡고,
predecessor/seed/choice가 해결되지 않으면 구조화 blocker에서 멈춘다. LCK Playoffs source closure,
외부 리그/KeSPA roster authority는 계속 추측하지 않는다.


## 시즌 전환·과거 시즌 조회 V1 (2026-09-05)

V14 `career_season`의 활성 연도가 Calendar와 연결된다. 최초 `career_save`의 생성 binding은
보존하며, Calendar R1/R2 overlay와 현재 Career DTO의 League/Season은 활성 시즌을 참조한다.

| 경로 | 계약 |
| --- | --- |
| `GET /api/v1/careers/{careerId}/seasons` | `CAREER_SEASONS_V1`: activeYear, calendarRevision, seasons, blockers, allowedCommands |
| `GET /api/v1/careers/{careerId}/seasons/{year}` | `CAREER_SEASON_DETAIL_V1`: season, readOnly, domestic, international, fixtures |
| `POST /api/v1/careers/{careerId}/seasons/transition` | `replayed`, 원본 `receipt`, 현재 `seasons` projection |

전환 요청은 다음 네 필드만 허용한다.

```json
{
  "schemaVersion": "CAREER_SEASON_TRANSITION_REQUEST_V1",
  "sourceYear": 2027,
  "expectedCalendarRevision": 30,
  "clientCommandId": "00000000-0000-0000-0000-000000000001"
}
```

서버의 `START_NEXT_SEASON`이 있을 때 새 요청을 만든다. 같은 UUID의 같은 payload는 원본
sourceYear/destinationYear/destinationSeasonId/resultingRevision/resultHash/completedAt receipt를
재생한다. 현재 시즌 projection은 그보다 여러 해 뒤일 수 있다. UUID에 다른 payload를 넣으면
충돌, 새 UUID에 이전 연도/revision을 넣으면 stale이다. 프런트는 원본 요청 전체를 저장하며
응답 소실 뒤 UUID나 revision을 바꾸지 않고 확인한다.

마감은 국내 6개와 FST/MSI/EWC/Worlds 완료, 봉인 결과, fixture/application, Player/Auto,
lease/outbox/pending command를 확인한다. KeSPA만 비활성 참고 대회 정책으로 필수 집합에서 제외한다.
Calendar 명령 잠금→Calendar 행→cycle 순서로 잠그고 마감·새 League/90경기·Cup·로스터·활성
참조·receipt를 함께 commit한다. 실패하면 rollback한다. 날짜는 다음 1월 1일로 이동하며
Calendar revision은 초기화하지 않고 증가한다.

Competition start 요청의 additive `sourceYear`는 후속 시즌부터 필수다. 서비스도 잠금 아래
활성 연도와 기존 UUID의 연도 범위를 확인한다. 과거 명령이 새 fixture에 적용되지 않는다.
과거 기록 fixture는 competitionId/matchId/teams/winner/seriesId/status/replayAvailable을 제공한다.
실제 완료 Player checkpoint만 replayAvailable이며 Auto 결과는 조회 가능하되 Player 재생을
광고하지 않는다. 과거 선택 중 Calendar 실행 UI와 이어하기·전환은 비활성화한다.

후속 정책, 기존 저장 이월 경계와 검증은
[선택권 수정·시즌 전환 보고서](../development/career-selection-fixes-and-season-rollover-v1.md)를 따른다.

## Career 선수단 V1 확장 (2026-09-06)

`GET /api/v1/careers/{careerId}/roster/{year}`는 `CAREER_ROSTER_VIEW_V1`을 반환한다.
전체 directory(460명·79조직), Career/연도별 membership과 lineups, roster revision,
관리 구단의 국제 등록 후보 `registeredPlayers`, 실제 시작한 경기의 `activeSeriesPlayers`,
읽기 전용 여부 및 허용 명령을 포함한다. GET은 명부 이주나 재import를 하지 않는다.

`POST /api/v1/careers/{careerId}/roster`의 strict body:

```json
{
  "schemaVersion": "CAREER_ROSTER_COMMAND_V1",
  "sourceYear": 2027,
  "team": "LCK:KT",
  "playerId": "player-jiwoo",
  "action": "SELECT_STARTER",
  "targetOrganizationId": null,
  "replacementPlayerId": null,
  "expectedRevision": 0,
  "clientCommandId": "11111111-1111-4111-8111-111111111111"
}
```

`MOVE_SQUAD`는 연계 조직 ID를 `targetOrganizationId`로 지정한다. 선발 선수를 육성팀으로
내릴 때에는 같은 포지션의 1군 대체 선수 ID도 필요하다. 서버는 관리 구단/연도/소유권과
revision을 검증하고, UUID 재시도는 원래 receipt를 반환한다. 다른 payload의 UUID 재사용과
stale revision은 409, 허용되지 않은 선수/조직/명령은 400이다. 응답은 `replayed`, 원래
`receipt`(sourceYear/resultingRevision/stateHash/applicationPolicy), 최신 `roster`를 포함한다.

Calendar 행 잠금을 경기 시작·등록·명부 변경·시즌 전환의 공통 원자적 경계로 사용한다.
화면에서도 동기적 공유 변경 gate를 사용하며 원본 전환/로스터 UUID는 응답 소실 후 보존한다.
조회 generation과 변경 요청 소유권/종료를 분리한다. 과거 시즌 화면은 변경할 수 없다.
자세한 정책·이주·검증은 [확장 명부 V1](../development/career-expanded-rosters-lineups-and-data-integration-v1.md)을 따른다.

## 계약 시장과 스토브 확장 (2026-09-06)

V16의 Career 계약 시장은 다음 계약 시작·만료·월말 급여·연간 배정·제안 응답·선수 결정·기한과 월요일 AI 사건을 기존 다음 일정 후보에 추가한다. 날짜 점프는 중간 날짜를 게임 정책 순서대로 소비한다. GET은 시장을 진행하지 않는다.

`ROSTER_REPAIR_REQUIRED`는 필요한 선발 또는 국제 등록 자격이 부족한 상태다. 경기 시작은 막고 계약 사건 날짜 진행은 허용하여 영입/승격/선발/적법한 보충등록으로 복구한다. `SEASON_ROLLOVER_REQUIRED`인 이주 저장도 연말 전에는 서버의 `allowedAdvanceModes`가 시장 진행을 허용할 수 있다. 프런트는 이 두 경우와 기존 pending 원본 복구 경계를 구분한다.

대회 마감은 시장의 `OPEN_STOVE`, 새 시즌은 기존 전환 API다. 마감 snapshot 이후 현재 명단은 운영 가능하다. 12월 31일 전에는 `OFFSEASON_OPERATIONS_UNTIL_DECEMBER_31`로 다음 시즌 진입을 막는다. 새해 사건은 시즌 이월 transaction에 포함한다. 캘린더 명령 영수증 시각은 실제 시계의 역방향 보정에도 생성/이전 시각보다 작아지지 않으며 게임 날짜와 seed 계산에는 사용하지 않는다.

시장 API, 초기화/재정/협상 정책과 검증은 [계약 시장 V1](../development/career-contracts-stove-fa-market-ai-competition-v1.md)을 따른다.

## 지급 불능 복구·공통 협상 시작일 (2026-09-06)

급여 부족은 명시적 미지급금으로 기록하여 Calendar를 롤백시키지 않는다. 미지급 상태에서는
추가 지출을 제한하며 확정된 1월 1일 배정 직후 오래된 미지급 급여부터 우선 정산한다.
이미 인식한 급여 기간과 실제 지급 완료일을 구분하여 같은 날짜·UUID·재시작에 중복 청구하지 않는다.

만료 직전 협상 시작일은 `max(기존 종료 다음 날, 공통 결정일)`이다. 수정·경쟁 제안도 기존 공통
결정일을 유지하며 결정 전 자연 만료로 생긴 실제 FA 공백에는 급여를 소급하지 않는다. 기존
잘못된 열린 제안은 명시적 수정/재협상으로 처리하고 완료된 계약과 과거 경기 입력은 보존한다.
[상세 정책·경계 검증](../development/career-market-fixes-and-expanded-player-v4-integration-v1.md)을 따른다.


## 출전 약속·유료 이적·임대 V1 (2026-09-07)

V17은 Series 시작 시점의 기회 스냅샷과 국제 등록 날짜를 추가한다. 검증 completion 적용과
시장/명부 저장은 같은 Calendar 잠금과 트랜잭션을 사용한다. League outbox는 Calendar를
먼저 잠근 후 대상 outbox 상태를 다시 확인한다. 원본 receipt/Series 입력을 수정하지 않는다.

시장 사건에 구단 응답/선수 공통 결정/거래 적용, 임대 종료 다음 날 반환, 14일 약속 평가를
추가한다. 하루 진행과 이벤트 점프는 같은 엔진 순서를 사용한다. 새 경기 근거가 없는 기간의
출전 불이행은 추가 누적하지 않는다. 급여 체불은 별도의 검증 가능한 의무로 평가한다.

날짜 내 순서는 연간 배정·체불 정산 → 임대 반환 → 만료 → 예약 활성화 → 거래 적용 →
월말 급여 → 약속 평가 → 시장 응답·월요일 제안·결정 → AI 선발이다. 거래 시작 전날까지
이전 지급 구조를 정산하고 당일부터 새 구조로 지급하므로 같은 날 급여가 중복되지 않는다.
임대는 원계약 team과 운영 Membership.ownerTeam을 구분하며, 원소속은 반환 공간을
유지한다. 반환은 기존 사용자 주전을 자동 교체하지 않는다.

정책 수치·원자성·출전/금융 검증은 [통합 구현 보고서](../development/career-playing-time-promises-paid-transfers-and-loans-v1.md)를 따른다.

## 훈련·성장 일자 경계 V1

`CareerMarketEngine.advance`는 하루마다 D 성장/회복 마감 → 현재 프로필 갱신 → 기존 D+1 시장
(예산, 임대 반환, 만료, 입단/거래, 급여, 약속, AI 협상·선발) 순서로 진행한다. 일자별 소속과
평가를 유지하며 메모리에서 정산한 뒤 같은 Calendar 트랜잭션으로 저장한다. 기존 blocker는 유지된다.
훈련은 `/api/v1/careers/{careerId}/development`의 revision+원본 UUID 명령으로 D+1 예약한다.
GET `/development/{year}`는 정산을 수행하지 않는다. 실제 출전일은 Series 완료 시 부하/보상을
먼저 적용하고 일일 훈련을 생략한다. 출전하지 않은 후보는 정상적으로 훈련한다.
기존 완료 검증 경계가 게임별 최종 playerId/championId/position을 전달하며, 출전 기록과 성장 쓰기가
동시에 커밋/롤백된다. 이주 전 시작 경기에는 성장 바인딩을 소급 만들지 않는다.


## AI 1군·CL 공통 운영 계획 V1 (2026-09-07)

`CareerSquadPlanner`가 독립 FA/육성/CL/이적 제안 생성과 일일 최강 선발을 통합한다.
D 훈련 마감과 D+1 계약·임대·은퇴·급여·약속·기존 협상 응답 이후 월요일 계획을 만들고,
기존 공통 선수 결정 뒤 적격 선발의 실제 공백만 보완한다. 관리 구단은 새 AI 판단에서 제외한다.
활성 LCK는 1군과 CL에 같은 선수를 중복 자원으로 쓰지 않으며, 진행 중 경기·동일 날짜
반대 선수단 출전·현재 국제 등록·다음 경기 계약 효력을 함께 검사한다.

계획 상태는 기존 `CareerMarketState.squadPlanning`의 nullable 확장으로 정책 버전,
마지막 검토 날짜·구단/포지션 대기·최근 1,000건의 결정만 저장한다. 기존 저장은 null로
로드하고 다음 정상 판단부터 적용한다. 별도 이주·계약 시장·예약 행동 실행 큐는 없다.
Calendar 잠금과 시장 revision/transaction 안에서 실제 명부·계약을 함께 반영한다.
시작된 경기 입력과 과거 시즌 봉인은 유지하며 새 날짜/연도에 현재 상태로 다시 판단한다.

시장 조회의 선택적 `squadOperations`는 실제 적용·유지·협상·보류 상태를 표시한다.
상대의 미확정 후보·조건은 공개하지 않고 GET은 계획을 생성하지 않는다. 현재 12개 능력치
합·숙련도 동률 판정·28일 대기·60일 만료 전망과 검증 범위는
[AI 선수단 계획 보고서](../development/career-ai-first-team-cl-squad-planning-v1.md)를 따른다.


## 원화 재정·상금·시즌 목표 V1 (2026-09-08)

`CareerMarketState.finance`가 Career에 고정한 레퍼런스/환율/정책 버전, 구단 비용·가격
기준, 반복 지원 승인, 상금 권리와 운영 체불, 시즌 목표·평가를 소유한다. 계산용
`CareerFinanceEngine`은 현재 시장 작업 공간에만 속하며 Calendar 잠금과 기존 transaction
아래 저장한다. GET은 정산·권리 생성·목표 결정·이주를 실행하지 않는다.

신규 생성은 명시적 `initializeNew`에서 2026 base의 구단별 자금·한도와 초기 그룹별 게임
연봉을 적용한다. 기존 저장의 V22 전환은 현재 금전 상태를 정수 KRW로 한 번 환산하고
`career_finance_transition`에 원본 JSON/hash와 정책 경계를 보존한다. 봉인된 시즌 snapshot,
기존 명령 payload/receipt 및 경기 입력을 재작성하지 않는다. 신규 금전 명령은
`CAREER_MARKET_COMMAND_KRW_V1` / `CAREER_TRADE_COMMAND_KRW_V1`을 사용한다. 원래
UUID 결과 조회가 버전 검사보다 우선하며, 미적용 구형 명령은 명시적 갱신 오류를 반환한다.

월말은 지원 70%·가상 후원 30%를 각각 누적 일할 계산하고 비급여 운영비와 기존 실제 급여를
한 번 정산한다. 연봉 한도 증액은 현금 지급이 아니다. 미래 지급 여력 검사도 같은 확정
수입/운영비·급여·거래·체불을 사용하며 미수 상금은 입금 전 재원에 포함하지 않는다.

`advanceResultGraph`의 검증된 완료 적용 뒤 현재 대회 상금 권리를 기록한다. CL은 실제
PO 승패에서 1·2위와 공동 3~4위를 얻고, 국제전은 봉인된 plan의 완전한 분류를 사용한다.
일반 종료일 +7일, EWC +42일이 다음 시장 사건 후보에 포함된다. 권리와 현금 입금은
각각 안정적인 키로 중복을 막으며 결과/근거 변경은 충돌로 드러낸다. 기존 저장 도입 전에
완료된 대회는 제외한다. 미수 권리는 시즌 전환을 막지 않고 다음 시즌으로 이어진다.

시즌 마감은 기존 blocker 검사 뒤 목표 평가·한 번의 성과 보너스·다음 시즌 지원/급여 한도
승인을 snapshot 전에 저장한다. 새 승인 효력은 다음 달력 연도 1월 1일이며 현재 현금을
증액하지 않는다. 확정 미래 계약은 예산이 줄어도 유지하고 초과 약정을 표시한다. 새 시즌
목표는 rollover 후 한 번 고정한다. 관리 팀의 새 `clubFinance` 조회는 자기 구단 승인과
목표만 제공하며 상대 AI의 미확정 구매 상한을 추가 공개하지 않는다.

금액·출처·초기화/이주 구분과 검증 결과는
[원화 재정·시즌 목표 구현 보고서](../development/career-finance-season-targets-krw-and-ai-followups-v1.md)를 따른다.


## 해외 리그 실행 V1 추가 조회 (2026-09-08)

`GET /api/v1/careers/{careerId}/overseas/{year}?league={LPL|LEC|LCP|CBLOL|LCS}&event={eventId}`는
해당 리그/이벤트의 activation, 일정·Series 상태/점수, 정규/Swiss 성적, PO, 최종 순위,
누적 CP, 봉인 국제 자격을 반환한다. `event` 생략 시 해당 리그만 반환하며 게임 timeline은 포함하지 않는다.
서로 다른 리그와 event 조합은 400이다. 과거 시즌은 readOnly이며 GET은 대진 생성·정산을 하지 않는다.

`GET /api/v1/careers/{careerId}/overseas/{year}/{eventId}/results/{matchId}`는
적용 완료된 검증 receipt의 세트별 결과·10인 픽과 replayAvailable을 반환한다.
현재/지난 시즌 조회는 같은 경로를 사용한다.

해외 경기 시작/재개는 기존 Calendar competition 명령·job/lease/receipt를 그대로 사용한다.
신규 저장은 첫 실행 시즌, 기존 저장은 저장된 activationYear부터 17개 해외 event를 사용한다.
상세 정책과 증거는 [해외 리그 실행 V1](../development/career-overseas-league-execution-v1.md)을 참조한다.

## 국제 등록 전 명부 복구 (2026-09-08)

국제 fixture가 없는 상태도 `registrationWait`의 구조적 code/competitionId/requiredEventId/teamId/
ownerTeam/responsibility/missingPositions/obstacles로 조회한다. 문자열 설명을 파싱하지 않는다.
현재 이벤트와 시작일이 지난 국제 등록 대기를 검사하며 AI/관리 구단의 복구 책임을 구분한다.
독립 미완료 경기·대회 선택·명령·출전 정산·시즌 제한이 없을 때 기존 시장 명령의 날짜 처리를 허용한다.
진행 transaction 안에서 시장 사건 처리 후 동일 국제 인스턴스를 reconcile한다. 하루 단위로 제한하며
예산/후보/계약 검사를 우회하지 않는다. GET은 저장을 변경하지 않는다.

명부가 이미 복구되어 국제 등록 준비가 끝났다면 과거 `WAITING_FOR_QUALIFICATION`만으로 진행을 막지 않는다.
현재 자격·명부를 읽기 전용으로 재평가하여 다음 명령을 허용하고, 해당 명령에서 같은 등록을 확정한다.
별개 미완료 fixture와 대기 명령은 이 경우에도 차단 조건이다.
