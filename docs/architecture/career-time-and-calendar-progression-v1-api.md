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
