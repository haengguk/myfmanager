# Career Time and Calendar Progression V1 API

## 기능과 authority 경계

Career Calendar는 저장별 `currentDate`, 활성 calendar year, event cursor, calendar revision과
state hash를 소유한다. 경기 결과, standings, fixture lifecycle, job/lease, Player Series와 Match는
기존 League/Series authority가 계속 소유한다. Calendar는 그 상태를 구조적으로 조회하고 날짜 이동을
허용할지 결정할 뿐 경기 결과를 직접 만들지 않는다.

2026년 자료의 일정·포맷은 reference fact다. 2027년 이후 날짜는
`SAME_LOCAL_MONTH_DAY_FROM_2026_REFERENCE_V1` 게임 정책으로 같은 현지 월·일에 투영한 값이며 공식
발표가 아니다. exact date가 없는 stage와 pending field는 미래 연도에도 null/pending이다.

## 조회 계약

`GET /api/v1/careers/{careerId}/calendar`는 `CAREER_CALENDAR_VIEW_V1`을 반환한다.

- 저장 상태: `currentDate`, `activeCalendarSeasonYear`, `calendarRevision`, lifecycle/blocking reason,
  `calendarStateHash`
- provenance: reference year 2026, source as-of `2026-08-23`, catalog snapshot `2026-08-24`, template
  version/hash와 세 projection/allocation policy
- 일정: current/next event와 stage, bounded upcoming events/fixtures, 다음 관리 경기
- 상태 구분: official status, future-year projection status, execution status, schedule status
- 구조화 자료: qualification edge 6개, pending official field 6개, KeSPA Cup
  `SOURCE_DATA_NOT_PRESENT` note

응답은 11개 정의를 표시한다: LCK Cup, First Stand, LCK 정규 R1~2, LCK Road to MSI, MSI,
EWC LoL, LCK 정규 R3~4, LCK 플레이인, LCK 플레이오프, 아시안게임 LoL 국가대표 차출 창,
Worlds. KeSPA Cup은 source bundle에 없으므로 competition definition을 만들지 않는다.

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
`backgroundAccepted`, 최신 Calendar view를 포함한다.

같은 UUID와 같은 canonical payload는 exact replay이며 mutation 0이다. 같은 UUID의 다른 payload,
stale revision, 다른 pending command는 409다. Calendar row와 receipt는 DB global advance lock 아래
한 transaction으로 갱신되며 날짜·cursor는 단조 증가한다. 재시작 뒤에도 pending/completed receipt와
state hash를 검증해 같은 command를 정확히 한 번만 이어 간다.

R1~2 경기일에는 기존 18라운드/90 fixture만 gate에 참여한다. 같은 날짜의 미완료 FULL_AUTO fixture는
기존 durable League job 경로로 dispatch하고 202 `AUTO_FIXTURES_PENDING`에서 멈춘다. 관리 경기는
`MANAGED_FIXTURE_REQUIRED`, blocked/cancelled/restart-required 상태는 `ATTENTION_REQUIRED`로 멈춘다.
pending job/outbox나 미완료 관리 경기를 지나 currentDate를 commit하지 않는다. 마지막 정의 이후에는
`SEASON_ROLLOVER_REQUIRED`가 되며 다음 시즌을 추측해 만들지 않는다.

## 오류와 호환성

기존 `CAREER_API_ERROR_V1` boundary를 유지한다. 잘못된 요청은 400, Career/Calendar 없음은 404,
command conflict·stale revision·pending conflict는 409, migration/integrity는 fail-closed 500,
background wake 실패는 503이다. 기존 create/list/detail, League/Series/Match API와 gameplay Random,
fixture ID/root seed/standings 의미는 변경하지 않는다.

LCK Cup, First Stand, Road to MSI, MSI, EWC, R3~4, 플레이인·플레이오프, 아시안게임과 Worlds는
표시 가능한 구조화 정의일 뿐 이번 버전에서 bracket/qualification/Series를 실행하지 않는다.
