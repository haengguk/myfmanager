# Career Mode V1 Foundation and Save/Load API

## 목적과 경계

`CAREER_MODE_V1_FOUNDATION_AND_SAVE_LOAD_API`는 사용자가 선택한 LCK 팀, 감독 이름,
저장 이름을 durable Career 슬롯으로 만들고 그 슬롯을 기존 Hybrid League Season에 결속한다.
Career는 장기 플레이 문맥과 durable aggregate의 연결만 소유한다. 경기 결과나 리그 진행의 새
authority가 아니다.

| Career가 소유하는 값 | 기존 League/Series authority가 계속 소유하는 값 |
| --- | --- |
| Career ID, 표시 이름, 관리 팀, 게임 날짜 | 18라운드/90 fixtures와 standings |
| linked League/Season ID | League lifecycle, current round, jobs와 leases |
| Career root seed와 알고리즘 ID | Draft, Hard Fearless, Series score와 checkpoints |
| frozen/product/reference identity와 binding hash | receipts, outbox/application ledger, Match output와 timeline |
| Career schema/lifecycle/revision, 운영 timestamp | 실제 command eligibility와 경기 실행 |

Career V1은 정확히 하나의 LCK `HYBRID_MANAGER` Season과 연결한다. 수동 snapshot 파일이나
Career JSON blob, `POST /save`, 날짜 진행, 다음 시즌, 이적·훈련·재정, 인증·삭제·복제·archive는
포함하지 않는다.

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

같은 `clientCommandId`와 같은 정규화 payload는 기존 Career를 `replayed=true`, HTTP 200으로
반환하고 새 row나 fixture를 만들지 않는다. 같은 command의 다른 payload는 HTTP 409와
`CAREER_COMMAND_CONFLICT`이며 mutation은 0이다. 다른 command는 같은 팀과 같은 표시 이름을
사용해도 독립 Career/Season을 만든다.

## Save, load와 resume

Career 생성 자체가 durable save 슬롯 생성이다. 이후 League/Series/Draft 진행은 기존 transaction,
checkpoint, receipt와 outbox 경로가 즉시 저장한다. Career GET은 linked Season/fixture/Series를
기존 relational authority에서 읽을 뿐 복사하거나 새로 만들지 않는다.

load 때 Career ID/League ID/Season ID/root seed/binding hash와 current reference/frozen/product/team
identity를 다시 검증한다. linked Season이 없거나 identity가 다르면 자동 재생성·재결속하지 않고
`CAREER_LINKED_SEASON_INTEGRITY_FAILURE` 또는 `CAREER_RESOURCE_INTEGRITY_FAILURE`로 fail-closed한다.
보장 범위는 동일 application/schema version의 process restart recovery다.

상세 응답의 resume projection은 조회 시 기존 League projection으로부터 계산하며 Career row에
저장하지 않는다.

| kind | 판정 |
| --- | --- |
| `PLAYER_SERIES` | terminal이 아닌 League-bound Player Series가 존재 |
| `LEAGUE_DASHBOARD` | 정상 진행 Season이고 active Player Series가 없음 |
| `SEASON_COMPLETE` | Season lifecycle이 `COMPLETED` |
| `ATTENTION_REQUIRED` | Season/fixture/binding이 blocked, cancelled 또는 restart-required |

projection은 `leagueId`, `seasonId`, nullable `fixtureId`/`seriesId`, Season lifecycle status,
current round, lifecycle revision, standings revision을 제공한다. 실제 command 허용 여부는 계속
League API가 소유한다. 목록은 `updatedAt DESC, careerId ASC`로 정렬된 최대 100개 summary만
반환하며 Season graph나 timeline을 포함하지 않는다.

## HTTP 계약

| Endpoint | 성공 | Schema |
| --- | ---: | --- |
| `POST /api/v1/careers` | 최초 201 / exact replay 200 | `CAREER_CREATE_RESPONSE_V1` |
| `GET /api/v1/careers` | 200 | `CAREER_LIST_V1` |
| `GET /api/v1/careers/{careerId}` | 200 | `CAREER_VIEW_V1` |

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
| 409 | `CAREER_COMMAND_CONFLICT` |
| 422 | `CAREER_MANAGED_TEAM_NOT_FOUND` |
| 500 | `CAREER_LINKED_SEASON_INTEGRITY_FAILURE`, `CAREER_RESOURCE_INTEGRITY_FAILURE` |
| 503 | `CAREER_TEMPORARILY_UNAVAILABLE` |

오류 boundary는 Career controller에만 적용되며 SQL, stack trace, raw exception message와 로컬 경로를
응답에 노출하지 않는다. 기존 League/Series/Player Draft/Real Match/Team Information API 의미와
Production V9, Draft, gameplay Random 소비 순서는 변경하지 않는다. Career 생성은 fixture 실행이나
Match simulation을 시작하지 않는다.

## 검증과 다음 단계

Focused 검증은 대표 GEN Career의 18 rounds/90 fixtures(18 Player/72 Auto), exact replay/conflict,
주입·입력 거부, rollback, lightweight list/detail, 조회 mutation 0, task-owned file H2 close/reopen,
동일 linked Season과 최소 Player Series binding resume, identity mismatch fail-closed를 확인한다.
90경기 실제 실행, 대형 population/diagnostic, frontend build와 Playwright는 수행하지 않는다.

다음 단계는 이 structured API만 소비하는 `CAREER_DASHBOARD_FRONTEND_V1`이다.
