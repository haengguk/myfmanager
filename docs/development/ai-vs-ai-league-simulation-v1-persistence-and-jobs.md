# AI League V1 Persistence and Jobs

## Status

`AI_VS_AI_LEAGUE_SIMULATION_V1_PERSISTENCE_AND_JOBS_IMPLEMENTED_READY_FOR_API`

이 문서는 Batch 4의 local single-node reference 구현을 설명한다. H2를 다른 관계형 DB의
production-ready adapter로 간주하지 않으며, public League HTTP API와 frontend는 아직 없다.

## 감사 결과와 소유권

Batch 1의 `LeagueSeasonAggregate`, schedule, fixture와 standings는 immutable gameplay/domain
identity만 소유했다. Batch 2 runner는 frozen fixture 하나를 동기 실행하고 actual Production
Auto Draft/V9 evidence로 opaque verified completion을 만들었다. Batch 3 Player handoff의
binding/CAS와 `SeriesRepository`는 process-local이었고 lifecycle, job, lease, outbox와 restart
authority는 없었다.

Batch 4는 domain identity를 바꾸지 않고 operational state만 관계형 저장소에 추가한다.

- immutable Season snapshot/schedule identity와 90 fixture rows
- Season/round/fixture lifecycle revision과 standings revision의 분리
- canonical Player binding 및 command idempotency index
- League-bound Series compact checkpoint
- FULL_AUTO job/attempt/lease/fencing/heartbeat
- canonical V2 receipt, outbox와 standings application ledger

`SeriesAggregate` checkpoint는 JSON schema/hash로 저장한다. Java native serialization,
planner cache, interactive computation context, full Match timeline/event/snapshot은 저장하지
않는다. Draft projection/context는 persisted progress와 frozen resources에서 다시 계산한다.

## Reference stack과 migration

- Spring JDBC transaction/JdbcTemplate
- Flyway `V1__league_schema_baseline.sql` → `V2__league_persistence_and_jobs.sql`
- local runtime: file-backed H2
- test: in-memory 또는 temporary file H2

기본 DB는 `${user.home}/.lolmanager/runtime/league-db/lolmanager-league-v1`에 생성된다.
`LOLMANAGER_RUNTIME_DIR` 또는 전체 `LOLMANAGER_LEAGUE_DB_URL`로 바꿀 수 있다. Repository
내 override를 쓸 때 생성될 수 있는 `/backend/runtime/league-db/`와 `/runtime/league-db/`도
정확히 ignore한다. Migration 오류나 DB 오류에서 in-memory adapter로 fallback하지 않는다.

주요 table은 `league_registry`, `league_season`, `league_round`, `league_fixture`,
`league_standing`, `league_player_binding`, `league_player_binding_command`,
`league_player_series_checkpoint`, `league_job`, `league_job_attempt`,
`league_completion_receipt`, `league_outbox`, `league_standings_application`이다.
PK/FK/unique/check constraint가 fixture/binding/receipt/attempt/application 중복을 DB에서도
차단한다.

## Internal application boundary

`LeagueSeasonApplicationService`는 frozen creation, READY, pause/resume/cancel과 immutable view를
제공한다. `LeagueSimulationApplicationPort`는 round/fixture dispatch, lease, heartbeat,
bounded execution, startup recovery, attempt retention과 job view를 제공한다. 둘 다 internal
application command/view이며 controller나 public DTO가 아니다.

Season lifecycle revision은 pause/cancel/worker 상태에 사용하고 standings revision은 valid
receipt application 횟수에만 사용한다. 다른 fixture가 완료돼 Season standings revision이
증가해도 기존 frozen fixture/binding의 receipt는 fixture identity로 commit할 수 있다.

## FULL_AUTO worker

Dispatch는 `FULL_AUTO`만 받는다. Hybrid round에서 managed `PLAYER_CONTROLLED` fixture는 job을
0개 만들고 auto fixture 완료 뒤 `WAITING_FOR_PLAYER`가 된다. Default parallelism은 2,
hard max는 4다. BO3 내부 games는 runner가 순차 실행하고 fixture들만 병렬화한다.

Worker 순서는 다음과 같다.

1. 짧은 transaction에서 job과 exact frozen-input hash를 잠근다.
2. attempt, owner, 15분 lease token과 monotonically increasing fencing number를 commit한다.
3. DB lock 밖에서 existing Production Auto Draft/V9 runner를 실행한다.
4. 15초 heartbeat가 같은 token/fence만 15분으로 갱신한다.
5. live token/fence/attempt/frozen hash를 다시 확인한다.
6. verified V2 receipt와 outbox를 같은 transaction에 저장하고 job을 완료한다.

Expired/foreign fencing result는 receipt/outbox/standings를 0건 만든다. Transient failure만
동일 frozen input으로 최초 포함 최대 2회 시도한다. Retry는 seed나 Draft history를 만들지
않고 같은 fixture root/snapshot/product hash를 사용한다. Deterministic mismatch와 2회 소진은
fixture/Season을 `BLOCKED`로 둔다.

## Player restart checkpoint

Binding register와 completion claim은 fixture row lock/CAS로 원자화했다. Exact command와
different-command/same-fixture 경쟁은 canonical binding 하나를 공유하고 정상 경쟁을
`BLOCKED`로 바꾸지 않는다. Completion 경쟁은 owner 하나만 verification을 실행하고 나머지는
`PENDING` 또는 persisted `VERIFIED`를 읽는다.

League-bound Series의 create/mutation마다 score, games, child Draft progress/evidence,
command receipts, simulation reservation, Hard Fearless history와 compact game receipt를
checkpoint한다. Public JSON에서 숨긴 player command id는 checkpoint-only codec으로 보존한다.
`MatchChampionAssignments`는 explicit ordered JSON values로 복원한다. League-bound parent/child는
standalone process TTL로 제거하지 않으며 standalone Series의 기존 memory/TTL/API 의미는 유지한다.

DB reader는 browser pointer 없이 series ID로 current score, game/seed/history, Draft revision/
turn count, exclusions와 allowed next action을 복구한다. Lost simulation reservation은 새 seed나
winner를 만들지 않고 `SIMULATION_FAILED_RETRYABLE`로 release한다.

## Receipt, outbox와 exactly-once

Auto job 또는 Player verified binding만 completion producer proof가 된다. Producer transaction은
immutable V2 canonical text/JSON/hash와 outbox event를 함께 저장한다. Consumer는 persisted Season,
fixture, nullable Player binding, ordered game seed/history/resource identity와 canonical receipt를
재검증한 뒤 다음을 한 transaction으로 처리한다.

- fixture/round lifecycle completion
- standings delta와 Season standings revision +1
- `(season, fixture, receiptHash)` application ledger
- outbox delivered acknowledgement

Duplicate delivery는 ledger에서 mutation 0이다. Standings commit 뒤 ack가 유실돼 outbox가 다시
pending이 되어도 Season revision과 standings는 그대로이고 ack만 복구한다. Current authored
resource가 나중에 바뀌어 full replay를 못 하더라도 이미 durable verified된 frozen receipt
authority는 유지한다.

## Startup crash reconciliation

Startup runner는 gameplay를 실행하지 않고 다음 관계형 상태만 bounded reconciliation한다.

| crash boundary | recovery |
| --- | --- |
| binding commit, Series create 전 | `PLAYER_SERIES_RESTART_REQUIRED` |
| Series create 직후 | checkpoint로 ACTIVE 복구 |
| Player Draft 중간 turn | persisted progress/evidence로 다음 `DRAFT_ACTION` 복구 |
| Draft 완료, simulation 전 | completed Draft와 `SIMULATE` 복구 |
| simulation reservation 뒤 process loss | reservation release, retryable command failure |
| Game 1 commit, Game 2 전 | score/history/exclusions와 next game seed 복구 |
| Series completed, V2 receipt 전 | completed checkpoint 유지, completion verification 재호출 가능 |
| receipt/outbox 뒤 standings 전 | outbox 재전달 |
| standings commit, outbox ack 전 | ledger no-op 후 ack |

Expired Auto lease는 attempt가 남으면 `RETRY_PENDING`, 소진이면 `BLOCKED`다. Unexpired foreign
lease는 훔치지 않는다. Canonical hash/binding mismatch는 새 Series/game을 만들지 않고
fail-closed한다.

## Retention과 제한

Attempt log만 finished-at 기준 30일 뒤 정리할 수 있다. Active job/lease, receipt, outbox,
application ledger, standings와 Season은 삭제하지 않는다. V2 128 KiB compact limit은 그대로다.
24시간 replay cache와 full timeline 저장은 구현하지 않았다.

이번 batch의 범위 밖은 public League API/frontend, auth/ownership, external broker,
multi-node/region consensus, 90-fixture official run, balance/performance population, custom schedule,
playoff/BO5 production 활성화와 standalone Series 전체 DB 전환이다.

## 검증 결과

최종 핵심 focused 묶음은 migration/repository, League-bound checkpoint, concurrent handoff,
actual Auto/Player Production V9, transport configuration과 두 fresh JVM canonical byte parity의
8 suites / 16 tests를 3분 3초에 통과했다. Draft scoring 공식이나 가중치는 바꾸지 않았고,
`DraftPlan` capability를 enum 순서로 보존해 JVM별 부동소수점 합산 순서가 raw trace와 receipt를
흔들던 결함만 제거했다.

첫 complete regression은 251 suites / 2,319 tests 중 3건이 실패했다. 새 test resource가 main
compression properties를 가린 설정 회귀 1건과, 위 unordered capability 합산으로 Auto/Player
fresh-JVM receipt가 달라진 2건이었다. 원인 수정 뒤 affected focused를 통과했고, 최종 executable
tree의 두 번째 complete regression은 251 suites / 2,319 tests / failures 0 / errors 0 /
skipped 2, aggregate XML 1,150.816초, Gradle wall 19분 16초로 clean pass했다. 두 skip은 기존
explicit 대형 diagnostic이다. Frontend source를 변경하지 않아 frontend build/Playwright와
90-fixture official run, balance/performance population은 실행하지 않았다.
