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
- 대회 자료: additive `competition`의 current/next competition·stage, lifecycle revision/hash,
  next fixture/Series, managed 여부, qualification output과 source/external blocker

응답은 12개 정의를 표시한다: LCK Cup, First Stand, LCK 정규 R1~2, LCK Road to MSI, MSI,
EWC LoL, LCK 정규 R3~4, LCK 플레이인, LCK 플레이오프, 아시안게임 LoL 국가대표 차출 창,
Worlds, KeSPA Cup. KeSPA는 실행 definition이 아니라 `REFERENCE_TEMPLATE_ONLY` instance다.
`sourceDataNote`는 `subject=KESPA_CUP`, `sourceReferenceYear=2025`,
`ruleVersion=KESPA_CUP_REFERENCE_TEMPLATE_2025`,
`status=REFERENCE_TEMPLATE_NOT_OFFICIAL_FOR_2026_OR_FUTURE`와 두 blocker
`KESPA_CUP_2026_RULE_SOURCE_INCOMPLETE`, `EXTERNAL_PARTICIPANT_ROSTER_AUTHORITY_MISSING`를 반환한다.

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
`SEASON_ROLLOVER_REQUIRED`가 되며 다음 시즌을 추측해 만들지 않는다.

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
graph는 구조화됐지만 competition 전용 Series adapter가 아직 없어 해당 fixture에서 fail-closed한다.
LCK Cup 첫 시즌 40경기 graph는 준비됐고 미완료 fixture를 건너뛰지 않는다. 실제 Cup result→후속
selector transition, LCK Playoff source closure, 외부 리그/KeSPA roster authority는 추측하지 않는다.
현재 날짜 이전의 미완료 Cup fixture도 overdue gate로 다시 잡으므로 기존 저장의 날짜가 이미
R1~2 이후여도 미완료 대회를 조용히 통과시키지 않는다.
