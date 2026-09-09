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

Career 최초 생성은 하나의 LCK `HYBRID_MANAGER` Season과 연결한다. V14 이후 명시적 시즌 전환은
같은 Career 아래 연도별 Season을 추가하며 최초 생성 binding/receipt는 보존한다. 현재 목록·상세의
League/Season과 resume은 `career_season`의 활성 참조를 사용한다. 수동 snapshot 파일이나
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

load 때 Career ID/League ID/Season ID/root seed/binding hash와 저장된 reference/frozen/product/team
identity를 다시 검증한다. 현재 설치 reference와의 차이는 아래 저장 호환 정책으로 별도 판단한다. linked Season이 없거나 identity가 다르면 자동 재생성·재결속하지 않고
`CAREER_LINKED_SEASON_INTEGRITY_FAILURE` 또는 `CAREER_RESOURCE_INTEGRITY_FAILURE`로 fail-closed한다.
선수 참고 데이터 변경에 대한 지원 범위는 아래 저장 호환 V1을 따른다. 미래 엔진/규칙 전체의 호환을 뜻하지 않는다.

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

## 계약 시장 API 확장 (2026-09-06)

`GET /api/v1/careers/{careerId}/market/{year}`는 `CAREER_MARKET_VIEW_V1`으로 게임 계약/예약 계약,
제안/선수 응답, 56팀 예산과 부담, 지급/결정/등록 보충 및 선발 공백을 반환한다. 과거 시즌은
마감 snapshot의 읽기 전용 뷰다. 공개 조사 계약과 현재 게임 계약은 별도 자료다.

`POST /api/v1/careers/{careerId}/market`는 `CAREER_MARKET_COMMAND_V1`의 엄격한 JSON 요청을
받는다. `sourceYear`, `expectedRevision`, `action`, `playerId`, `offerId`, `terms`,
`replacementPlayerId`, `competitionId`, `clientCommandId`를 명시하며 불필요한 선택 필드는
null이다. `terms`는 `startDate`, `endDate`, `annualSalary`, `signingBonus`, `role`이다.
액션은 SUBMIT/REVISE/WITHDRAW/RELEASE/SUPPLEMENT/OPEN_STOVE다. 관리 팀은 서버가 결정한다.

잘못된 날짜/역할/금액/조건은 400, stale revision과 원본 UUID payload 충돌은 기존 Career
409 계약을 사용한다. 같은 UUID/원본 payload는 원래 receipt를 반환한다. 시장 상태/소속/
금액/영수증은 하나의 transaction이며 GET/원본 replay는 저장 시각을 갱신하지 않는다.
create/startup은 V16 시장과 운영 roster V2를 초기화한다. 구형 roster V1 및 이미 시작한
경기의 고정 입력 의미는 보존한다. [게임 정책과 실행 검증](../development/career-contracts-stove-fa-market-ai-competition-v1.md)을 참고한다.

## V4 자료·지급 재원 호환 확장 (2026-09-06)

시장 Finance에 `fundingPolicy`, `salaryArrears`, `paymentHeadroom`을 추가한다. 후자는 현재 약정의
급여를 확보한 뒤 남는 지급 여유이며 새 제안의 연봉까지 보장하는 확정 계약금 한도가 아니다.
`PAYROLL_CASH_FLOW_AND_ARREARS_RECOVERY_V2`는 기존 현금/예약/연봉 필드 의미를 유지하며 구형
지급 불능 상태도 조회할 수 있다. V16 ledger의 `SALARY_ACCRUED`는 현금 수입이 아닌 미지급
발생액, `SALARY_ARREARS_PAYMENT`는 실제 상환 지출이다. 별도 SQL 이주나 과거 receipt 재작성은 없다.

V4 전역 정의는 새 Career directory에만 적용한다. 기존 directory/hash, 계약·선발·등록·경기
고정 입력과 시즌 이월은 저장 자료를 유지한다. 운영상 육성 배치를 정규화하되 조사 원문과
역할 검토 사유를 보존한다. [정책·검증](../development/career-market-fixes-and-expanded-player-v4-integration-v1.md)을 따른다.


## 거래 명령 및 기존 저장 호환 V1 (2026-09-07)

`POST /api/v1/careers/{careerId}/market/trades`는 `CAREER_TRADE_COMMAND_V1`의
sourceYear, expectedRevision, action, tradeId, terms, replacementPlayerId,
clientCommandId를 받는다. action은 SUBMIT/COUNTER/ACCEPT/REJECT/WITHDRAW다.
기존 market Request 구조는 바꾸지 않아 과거 UUID payload hash를 보존한다.
새 TradeRequest도 기존 durable market command/receipt 저장소와 Calendar 잠금으로 처리한다.

시장 조회에 nullable management(약속·현재 시즌 출전·거래·임대·가치)를 추가한다.
원계약 Contract.team은 법적 원소속, Membership.ownerTeam은 명부/선발의 현재 운영 팀이다.
임대의 parentTeam/borrowingTeam이 둘을 명시한다. 신규 거래 조회는 상대 내부 buyerLimit과
전체 playerScore를 노출하지 않고 고정 제안과 결정 이유를 제공한다.

V17 SQL은 기존 시장 JSON/receipt를 다시 쓰지 않는다. recover에서 management가 없는
기존 Career에 현재 날짜의 중립 관찰 상태를 추가하며 기존 directory·계약·역할·명부를
재import하지 않는다. 이미 시작된 Series에 캡처가 없으면 과거 분모를 추측하지 않는다.
시즌 이월은 동일 Career의 계약/약속/임대/장부를 유지하고 출전 조회만 연도별로 나눈다.
상세 구현/검증은 [통합 구현 보고서](../development/career-playing-time-promises-paid-transfers-and-loans-v1.md)를 따른다.

## Career 성장 상태 V1

V19의 `career_development_state`는 기존 directory JSON/hash와 분리된 Career+playerId 상태다.
`CareerRosterStore.directory`가 현재 내부 능력치·숙련도를 내림해 공통 프로필을 합성하며,
`baseDirectory`는 생성 당시 데이터와 PA를 보존한다. 글로벌 편집은 계속 새 Career에만 적용된다.
성장 이주는 명시적인 생성/시작 복구에서만 수행하고 초기화 표시가 있는데 상태가 없으면 손상으로 처리한다.
등록된 선수 집합과 과거 등록 본문은 유지하고, 새 Series에서만 등록 자격 안의 현재 프로필을 고정한다.
이미 고정한 Series는 과거 입력을 유지한다. API·정책·검증은
[성장·훈련 V1](../development/career-player-development-training-proficiency-fatigue-v1.md)을 참고한다.

## 선수 참고 데이터 변경과 저장 호환 V1 (2026-09-08)

`CareerSaveCompatibility`는 생성 reference version/hash를 provenance로 유지하고, 저장된
`EXPANDED_PLAYER_DIRECTORY_V1` directory와 조직/선수 ID, 현재 명부·시즌 roster hash를 검사한다.
정상 저장은 설치 카탈로그가 달라도 자신의 성장/계약/소속으로 읽고 다음 시즌까지 이월한다.
새 Career만 최신 authored/global editor snapshot을 사용한다. 시작 Series와 닫힌 국제 등록은 보존한다.

목록/상세에 additive `compatibility`를 제공한다. 지원 불가 directory 버전/누락 자료/조직 정의는
`CAREER_SAVE_COMPATIBILITY_{VERSION_UNSUPPORTED,DATA_MISSING,ORGANIZATION_UNSUPPORTED}` 409로 구분한다.
목록은 지원 불가 저장도 이유와 함께 반환하되, 실제 내부 hash/연결 손상을 성공으로 처리하지 않는다.
누락 옛 자료는 생성 reference가 일치하는 기존 startup 복구 경로만 사용한다. GET import는 없다.
추가 SQL migration 없이 기존 V23 정상 저장의 directory와 개별 운영 상태를 재사용한다.
상세 적용 표/검증은 [해외 수정·저장 호환 보고서](../development/career-overseas-fixes-and-save-compatibility-v1.md)를 따른다.


## 연속 진행 저장 확장 (V24)

`career_continuous_run`은 현재 실행 상태·원본 자식 intent·소유 lease/fence를,
`career_continuous_command`는 사용자 UUID/payload/receipt를 보존한다.
브라우저 연결은 실행 소유권이 아니다. 실행 중 상태는 lease 회수 후 이어가고 PAUSED는 재개하지 않는다.
기존 Career 고정 입력·Series·등록·receipt를 수정하거나 다시 import하지 않는다.
시작 복구는 운영 중 Career의 현재 시즌 고정 roster 행/JSON/hash 누락을 DATA_MISSING으로
분류해 건너뛴다. 초기화 가능한 legacy는 기존 정책으로 처리하고 hash 손상은 숨기지 않는다.


정상 legacy가 선수단을 처음 복구할 때 확보한 초기 명부는 시장 초기화 전에 시즌 입력으로 고정한다.
시장·운영 명부가 이미 존재한 뒤 유실된 고정 입력을 새 카탈로그로 재생성하는 예외는 아니다.
V24의 nullable league_job.match_policy_id가 없는 기존 job은 기준 V1 정책으로 실행하고,
새 job의 정책 ID는 frozen input hash에도 포함한다. 이전 결과·원본 job ID는 유지한다.

## 경기 성적·수상 부가 저장 (V25)

검증된 League outbox/competition completion transaction과 기존 lease/fence 안에서
CareerRecordsStore가 canonical 원본 경기와 선수 성적을, CareerAwardsStore가 단일 경기 시상을 저장한다.
Auto의 검증 출력 요약과 Player의 완료 checkpoint는 동일 CareerGameStatistics 계약을 전달한다.
새 통계는 기존 receipt/output canonical/hash를 재정의하지 않는다. 통계 수집은 성장 적용 자격과 독립이다.
R1/R2 import는 League 원본 identity를 유지한다. 같은 증거 no-op/다른 증거 충돌이며 실패 시 함께 롤백한다.
기간상은 실제 종료 그래프 뒤에 해당 scope만 평가하고 입력 목록/hash와 후보·정책·cutoff를 봉인한다.

기록 API는 /api/v1/careers/{careerId}/records 아래 추가 계약이다.
짧은 read-only REPEATABLE_READ와 asOf 사실 revision으로 합계/상세를 읽고 경기25/시상50 단위로 페이지 조회한다.
개인/팀 시상 projection 인덱스로 대상 조건을 LIMIT 전에 적용한다. 팀 합산은 명시적으로 선택할 때만 한다.
개인 포상금 UNPAID 권리, 현실 authored 이력, 구단 순위 상금은 분리한다.
POST restore만 작은 완료 증거 복원 배치를 실행하며 GET/startup에는 새 전 시즌 복원을 추가하지 않는다.

시즌 OPENING/새 관측 월말/CLOSING_FINAL과 유효 운영 사건을 보존한다.
closeSeason의 임시 종료는 최종 성장 이력이 아니다. 마지막 시장·생애주기 정산 후 finishSeason이
최종 closing을 갱신하고 같은 경계의 다음 opening을 남긴다.
상세 채택/정책/제한은 [기록 V1](../development/career-records-performance-awards-and-player-team-history-v1.md)을 따른다.


## Career Inbox V1 adjunct

`CareerInboxStore`는 기존 원본 transaction 안에서 작은 사실 요약과 source identity를 기록한다.
`career_inbox_item.read_flag`는 감독 확인 메타데이터이며 Calendar/gameplay revision이나
Continuous 실행 lease를 갱신하지 않는다. GET은 read-only projection이다.
`CareerDecisions`는 기존 Planner의 시장·등록 자격과 Calendar/League의 현재 경기 경계를
공유하며 처리 목록만 도출한다. 소식의 중요도/읽음과 실제 응답 의무는 별개다.

Feed는 Career/year/kind/development를 먼저 필터한 sequence keyset page와 asOf 상한을
사용한다. 모두 읽음도 그 상한/필터까지만 적용한다. 상세는 발생 당시 사실과 현재 원본 상태를
분리한다. 실제 해결은 기존 계약/명부/Series/시즌 명령의 책임이다.

V26/V27은 조회 subject와 소식 메타데이터를 추가한다. 과거 JSON/hash는 유지하며
기존 이적·임대 terms의 양 당사자를 검색에 포함한다. 기록 API의 seconds는 총 시간으로
유지하고 observedcspm/csobservedseconds/csobservedunits와 성장 별도 페이지를 추가했다.
[실제 소스 매핑·저장·복구·한계](../development/career-inbox-and-decisions-v1.md).


## 공개 스카우팅과 감독 관심 메타데이터 (2026-09-09)

`/api/v1/careers/{careerId}/scouting`은 현재 저장 디렉터리와 시장 투영을 요청당 재사용한다.
검색은 필터 후 20명/asOf 페이지, `/compare`는 2~4명의 선택 시즌·대회/당시 구단 집계,
`/opponent`는 다음 관리 fixture 및 승인 완료 Series 최근 5/10개다. 모두 읽기 transaction이며
시장 날짜, AI 정책, Random과 gameplay revision을 변경하지 않는다.
`POST /interest`만 V28의 `(career_id,player_id,revision,selected)`를 갱신한다. 삭제 행의 revision은
보존해 늦은 추가가 삭제를 되돌리지 않는다. Calendar/Continuous lease와 분리된다.

승인 완료 transaction의 `CareerRecordsStore.complete`에서 원 receipt의 작은 드래프트 근거를
`career_record_draft`에 idempotent하게 저장한다. 기존 canonical JSON/hash는 변경하지 않는다.
도입 이전 자료는 기존 명시적 restore에서 한 번에 10 Series까지 복원하며 GET/startup scan을
추가하지 않는다. 수집되지 않은 세트는 분석 분모에서 구분한다.
현재 업무/스카우팅 링크는 부모 최신 조회 완료 뒤 활성 시즌 selector와 정확한 원본 대상에
focus한다. UNSTARTED 예약 ID는 준비 화면, IN_PROGRESS만 원래 Series 이동이며 클릭은 명령이 아니다.
