# Career Mode V1 Foundation and Save/Load API

## 목적과 경계

`CAREER_MODE_V1_FOUNDATION_AND_SAVE_LOAD_API`는 사용자가 선택한 LCK 팀, 감독 이름,
저장 이름을 durable Career 슬롯으로 만들고 그 슬롯을 기존 Hybrid League Season에 결속한다.
Career는 장기 플레이 문맥과 durable aggregate의 연결만 소유한다. 경기 결과나 리그 진행의 새
authority가 아니다.

| Career가 소유하는 값 | 기존 League/Series authority가 계속 소유하는 값 |
| --- | --- |
| Career ID, 표시 이름, 관리 팀, 시작/현재 게임 날짜 | 18라운드/90 fixtures와 standings |
| linked League/Season ID | League lifecycle, current round, jobs와 leases |
| Career root seed와 알고리즘 ID | Draft, Hard Fearless, Series score와 checkpoints |
| frozen/product/reference identity와 binding hash | receipts, outbox/application ledger, Match output와 timeline |
| Career schema/lifecycle/revision, 운영 timestamp | 실제 command eligibility와 경기 실행 |

Career V1은 정확히 하나의 LCK `HYBRID_MANAGER` Season과 연결한다. 수동 snapshot 파일이나
Career JSON blob, `POST /save`, 다음 시즌 자동 생성, 이적·훈련·재정, 인증·삭제·복제·archive는
포함하지 않는다. 후속 `CAREER_TIME_AND_CALENDAR_PROGRESSION_V1`은 이 foundation을 변경하지 않고
Career-owned Calendar aggregate와 날짜 진행 API를 additive하게 연결한다. 상세 계약은
[Career Time and Calendar Progression V1 API](career-time-and-calendar-progression-v1-api.md)를 따른다.

## 생성 identity, seed와 날짜

생성 요청은 다음 다섯 필드만 허용한다.

```json
{
  "schemaVersion": "CAREER_CREATE_REQUEST_V1",
  "saveName": "GEN 첫 커리어",
  "managerName": "감독",
  "managedTeamCode": "GEN",
  "clientCommandId": "00000000-0000-0000-0000-000000000001"
}
```

`managedTeamCode`는 현재 catalog의 exact uppercase code여야 한다. 이름은 NFC 정규화와 양끝
공백 제거 뒤 1~80 code points이며 제어문자를 허용하지 않는다. JSON parser는 unknown/duplicate/
누락 필드, 비문자열 값, trailing token과 잘못된 UUID를 거부한다.

서버는 canonical UUID에서 `career_` + lowercase SHA-256 Career ID를 만들고, 그 ID에서 전용
League/Season ID와 root seed를 파생한다. seed 알고리즘은
`CAREER_ROOT_SEED_SHA256_FIRST_8_BYTES_BIG_ENDIAN_SIGNED_LONG_V1`이며 SHA-256 첫 8 bytes를
big-endian signed long으로 해석한다. `saveName`, `managerName`, display team name, wall clock은
ID나 gameplay seed 입력이 아니다.

시작 날짜는 `TeamPlayerInformationCatalog`의 `PLAYER_CAREER` provenance `snapshotAt`에서 읽는다.
현재는 `2026-08-24`이고 `currentDate=startDate`다. 운영용 `createdAt`/`updatedAt`만 wall clock을
사용하며 seed와 binding hash에는 포함하지 않는다.

## 관계형 소유권과 원자적 생성

Flyway `V4__career_mode_foundation.sql`은 기존 V1~V3를 변경하지 않고 다음 작은 구조를 추가한다.

- `career_save`: Career-owned 값과 `(league_id, season_id)` foreign key/unique binding
- `career_create_command`: canonical UUID, request payload hash, 결과 Career ID를 가진 완료 receipt
- `career_operation_lock`: create command의 단일 DB 직렬화 지점
- `career_schema_version`: `CAREER_MODE_FOUNDATION_V1` migration identity

생성 transaction은 operation lock과 기존 receipt를 먼저 확인하고, 내부 provisioning port로 현재
production snapshot의 전용 `HYBRID_MANAGER` Season을 생성한 뒤 `READY`로 전이한다. 그 다음
Career binding과 command receipt를 같은 transaction에 저장한다. public `LeagueApiV1Facade`나
HTTP/공개 DTO를 재호출하지 않는다. 어느 단계든 실패하면 Career, Season, rounds, fixtures,
standings와 command receipt가 함께 rollback된다.

Career binding `CAREER_LEAGUE_BINDING_V1`은 Career/team/date/linked IDs/root seed/frozen snapshot/
product decision/reference catalog identity를 canonical SHA-256으로 결속한다. Career row에는 schedule,
standings, Series 또는 Match graph를 복사하지 않는다.

Targeted competition hardening에서는 새 Career 생성 transaction이 첫 full cycle의
`OFFICIAL_2026_INITIAL_BOOTSTRAP` Competition V2 graph도 함께 초기화한다. 기존 저장은 일반 GET에서
read-repair하지 않고 startup recovery가 V1 hash를 먼저 검증한 뒤 V2로 승격한다. 두 번째
competition season부터는 별도 season rollover command가 직전 게임 내 LCK final ranking을
`SEALED` authority로 제공해야 하며, 생성/조회 경로가 2026 bootstrap으로 조용히 fallback하지 않는다.

같은 `clientCommandId`와 같은 정규화 payload는 기존 Career를 `replayed=true`, HTTP 200으로
반환하고 새 row나 fixture를 만들지 않는다. Replay에서는 저장된 command schema, payload hash,
command에서 결정적으로 파생되는 Career ID, 실제 target Career의 존재와 전체 binding 무결성을 다시
검증한다. schema 또는 target이 변조되면 receipt를 고치거나 다른 Career를 반환하지 않고
`CAREER_COMMAND_RECEIPT_INTEGRITY_FAILURE`로 실패한다. 같은 command의 다른 payload는 HTTP 409와
`CAREER_COMMAND_CONFLICT`이며 mutation은 0이다. 다른 command는 같은 팀과 같은 표시 이름을
사용해도 독립 Career/Season을 만든다.

V1 저장 개수는 durable hard capacity 100이다. Exact replay를 capacity보다 먼저 판정하므로 100개가
찬 상태에서도 기존 command replay는 성공한다. 새로운 command는 Season provisioning 전에
`CAREER_CAPACITY_REACHED`로 거부되며 Career/League/Season/fixture/receipt mutation은 0이다. 삭제나
archive가 아직 없으므로 사용자는 한도에 도달하면 새 저장을 만들 수 없다.

## Save, load와 resume

Career 생성 자체가 durable save 슬롯 생성이다. 이후 League/Series/Draft 진행은 기존 transaction,
checkpoint, receipt와 outbox 경로가 즉시 저장한다. Career GET은 linked Season/fixture/Series를
기존 relational authority에서 읽을 뿐 복사하거나 새로 만들지 않는다.

load 때 Career ID/League ID/Season ID/root seed/binding hash와 current reference/frozen/product/team
identity를 다시 검증한다. linked Season이 없거나 identity가 다르면 자동 재생성·재결속하지 않고
`CAREER_LINKED_SEASON_INTEGRITY_FAILURE` 또는 `CAREER_RESOURCE_INTEGRITY_FAILURE`로 fail-closed한다.
보장 범위는 동일 application/schema version의 process restart recovery다.

Career GET/List는 public `LeagueApiV1ResponseMapper`나 process-local Series repository를 통하지 않는다.
Career row 조회와 별도로, 최대 100개 linked Season을 대상으로 한 scalar Season query 하나와 resume
candidate query 하나를 실행한다. 전체 fixture DTO, standings graph, frozen snapshot JSON을 만들거나
Career별 binding을 반복 조회하지 않는다. Detail도 같은 batch read path에 단일 reference를 전달한다.
따라서 Career 수나 각 Season의 90 fixtures 수에 비례하는 mapper/inspect N+1이 없다.

조회 시 durable typed Season/fixture/binding/checkpoint scalar만 읽고 Series `inspect`/`resume`, reservation
expiry 처리, lease recovery, command reconciliation을 호출하지 않는다. Career GET/List 전후의 Career,
Season, fixture, Player binding/checkpoint/reservation, command/job/attempt/receipt/outbox/application ledger는
exact equality여야 한다. 실제 recovery와 mutation은 계속 League/Series command 경계가 소유한다.

상세 응답의 resume는 조회 시 계산하는 read-only navigation projection이며 Career row에 저장하지
않고 command authority도 아니다. `LeagueCommandPolicy` pure policy를 public League mapper와 Career
read adapter가 함께 사용해 같은 durable 상태의 `allowedCommands`를 산출한다. Frontend는 이 배열과
기존 League/Series GET/command API를 다시 검증해 이동하며 `kind`만으로 실행 가능성을 추측하지 않는다.

| kind | 판정 |
| --- | --- |
| `PLAYER_SERIES` | 현재 Round Player binding에 `RESUME_PLAYER_SERIES` 또는 `RECONCILE_PLAYER_SERIES_COMPLETION`이 실제 허용됨 |
| `LEAGUE_DASHBOARD` | 정상/일시정지 Season이거나 `VERIFIED` binding의 League reconciliation만 남음 |
| `SEASON_COMPLETE` | Season lifecycle이 `COMPLETED` |
| `ATTENTION_REQUIRED` | Season/fixture/binding이 blocked, cancelled 또는 restart-required |

우선순위는 Season completed, attention-required, paused dashboard, 실제 Player command, 일반 League
dashboard 순이다. PAUSED는 Player Series를 직접 열지 않고 `RESUME_SEASON`을 제시하며, `VERIFIED`는
Player command가 없으므로 `PLAYER_SERIES`로 보내지 않는다. Projection은 `leagueId`, `seasonId`,
nullable `fixtureId`/`seriesId`, Season lifecycle status, current round, lifecycle revision, standings
revision과 `allowedCommands`를 제공한다. 목록은 `updatedAt DESC, careerId ASC`의 모든 최대 100개
summary와 `currentCount`, `maximumCount=100`, `remainingCount`를 반환하며 Season graph나 timeline을
포함하지 않는다.

## HTTP 계약

| Endpoint | 성공 | Schema |
| --- | ---: | --- |
| `POST /api/v1/careers` | 최초 201 / exact replay 200 | `CAREER_CREATE_RESPONSE_V1` |
| `GET /api/v1/careers` | 200 | `CAREER_LIST_V1` |
| `GET /api/v1/careers/{careerId}` | 200 | `CAREER_VIEW_V1` |
| `GET /api/v1/careers/{careerId}/calendar` | 200 | `CAREER_CALENDAR_VIEW_V1` |
| `POST /api/v1/careers/{careerId}/advance` | 완료 200 / auto pending 202 | `CAREER_CALENDAR_ADVANCE_RESPONSE_V1` |

상세 응답은 Career/display/team/date, linked IDs, root seed algorithm/value, frozen/product/reference
identity, binding/schema/revision, resume와 운영 timestamp를 immutable DTO로 투영한다. Reference hash는
표시 provenance이며 Match Engine gameplay identity나 Random 입력이 아니다. 기존 portrait 정보의
presentation 한계도 Career가 보완하거나 gameplay identity로 승격하지 않는다.

오류 schema는 `CAREER_API_ERROR_V1`이고 `schemaVersion`, stable `code`, nullable `field`, 안전한
`message`만 노출한다.

| HTTP | Code |
| ---: | --- |
| 400 | `CAREER_REQUEST_INVALID` |
| 404 | `CAREER_NOT_FOUND` |
| 409 | `CAREER_COMMAND_CONFLICT`, `CAREER_CAPACITY_REACHED` |
| 422 | `CAREER_MANAGED_TEAM_NOT_FOUND` |
| 500 | `CAREER_LINKED_SEASON_INTEGRITY_FAILURE`, `CAREER_RESOURCE_INTEGRITY_FAILURE`, `CAREER_COMMAND_RECEIPT_INTEGRITY_FAILURE` |
| 503 | `CAREER_TEMPORARILY_UNAVAILABLE` |

오류 boundary는 Career controller에만 적용되며 SQL, stack trace, raw exception message와 로컬 경로를
응답에 노출하지 않는다. 기존 League/Series/Player Draft/Real Match/Team Information API 의미와
Production V9, Draft, gameplay Random 소비 순서는 변경하지 않는다. Career 생성은 fixture 실행이나
Match simulation을 시작하지 않는다.

## Dashboard 소비 경계와 다음 단계

Career dashboard는 list/detail 전체 응답이나 standings/fixtures/Series 상태를 browser storage에 저장하지
않는다. 저장 가능한 것은 active Career ID, 불명확한 create 응답을 동일 UUID로 재시도하기 위한
정규화 payload fingerprint, Career return context뿐이다. Reload는 반드시 Career GET으로 pointer를
재검증한다. 팀 선택은 Team/Player Information API의 실제 LCK 10팀을 사용한다.

`LEAGUE_DASHBOARD`는 응답의 linked ID로 기존 League pointer와 GET 경계를 사용하고 새 Season을 만들지
않는다. `PLAYER_SERIES`는 구조화된 fixture/Series ID와 allowed command를 확인한 뒤 기존 League
fixture/Series restore 흐름을 재사용하며 side, seed, score, Hard Fearless를 재계산하지 않는다.
Career-bound League에서는 standalone 새 시즌을 숨기고 Career 복귀 문맥을 제공한다.

Focused 검증은 expired reservation을 포함한 GET/List DB snapshot mutation 0, PAUSED/VERIFIED command
경계, configurable capacity 1의 create/replay/rejection, receipt schema/target 변조, file-H2 restart를
확인했다. Frontend strict contract 8 scenarios, production build/lazy bundle, 실제 Career→League→Career
브라우저 흐름과 한 번의 최종 backend regression도 통과했다. 90경기 실행, Player BO3 완주와 대형
population/diagnostic은 수행하지 않았다.

Calendar progression까지 연결된 현재 다음 단계는 구조화된 대회 정의를 실제 Series authority와
qualification/bracket lifecycle에 연결하는 `CAREER_COMPETITION_LIFECYCLE_V1`이다.
