# AI vs AI League Simulation V1 Contract Sketch

상태: `AI_VS_AI_LEAGUE_SIMULATION_V1_CONTRACT_SKETCH_READY`

이 문서는 AI 팀들이 BO3/BO5를 수행하는 League/Season 기능의 설계 source of truth다. 현재 저장소의 Series, Draft, Match Engine 경계를 조사해 재사용 가능한 부분과 새로 소유해야 할 lifecycle을 분리한다. 이 문서는 설계만 고정하며 League production Java, API, worker, DB, frontend가 구현됐다는 뜻이 아니다.

결정 표시는 다음 의미로 사용한다.

- `V1_DECIDED`: V1 구현이 따라야 할 계약이다.
- `V1_RECOMMENDATION`: 현재 구조에서 가장 안전한 권장안이며 구현 전 제품 소유자가 변경할 수 있다.
- `PRODUCT_DECISION_REQUIRED`: 구현 전에 명시적 제품 결정이 필요한 항목이다.

## 현재 재사용 가능한 경계

### `V1_DECIDED`: 그대로 재사용할 domain/application 기반

- `LckTeamAssembler`의 stable team code, roster, `PlayerId`, ratings/proficiency graph를 team authority로 사용한다.
- `DraftEngine`의 seeded Production Auto Draft와 `SeriesDraftHistory`의 completed-pick Hard Fearless 의미를 재사용한다.
- `MatchEngineV1`의 immutable input, authoritative Production V9 policy, structured output/provenance, Random fingerprint와 output hash를 game execution authority로 사용한다.
- `SeriesIdentity`가 보여 준 canonical SHA-256 seed/ID discipline, `SeriesGameReceipt`의 compact receipt 원칙과 fresh deterministic replay 검증 방식을 재사용한다.
- 대형 population/league 실행은 default `test`에서 제외하고 explicit diagnostic/job task로 분리한다.

### `V1_DECIDED`: 현재 interactive Series에서 직접 재사용하지 않을 것

현재 `SeriesLifecycleService`는 managed team 한쪽을 플레이어가 제어하는 child Draft, UI command/revision, process-local TTL과 취소/recovery를 소유한다. AI League가 이를 HTTP client처럼 호출해 20턴 action을 대신 제출하면 다음 문제가 생긴다.

- AI 대 AI인데도 `managedTeamCode`와 `controlledSide`라는 플레이어 개념이 lifecycle에 남는다.
- 한 fixture를 위해 child action receipt와 UI retry 상태가 20턴씩 생성된다.
- process-local 최대 32개/TTL 120분 repository는 Season durability와 background worker lease가 될 수 없다.
- fixture 병렬 실행과 Season standings commit이 Series aggregate 밖에서 다시 조정돼 dual authority가 된다.

따라서 public interactive Series API를 League worker의 내부 RPC로 사용하지 않는다. 기존 Series API/schema/lifecycle도 League 때문에 변경하지 않는다.

| 대안 | 재사용 | 장점 | 핵심 문제 | V1 판정 |
| --- | --- | --- | --- | --- |
| Interactive Series API를 AI client가 호출 | REST/child Draft/lifecycle 전체 | 새 backend 표면이 작아 보임 | player authority, 20턴 command receipts, process-local TTL, Season dual authority | 사용하지 않음 |
| 기존 full-auto Real Match를 fixture loop로 호출 | Auto Draft/Match Engine | game 실행은 단순 | score/Hard Fearless/receipt/job commit을 caller가 재구현 | domain boundary로만 재사용 |
| League-owned automated Series runner | Draft/Match/receipt kernel | fixture 원자성, 병렬 독립성, durable job과 자연스럽게 결합 | 새 application/persistence 필요 | `V1_RECOMMENDATION` |

### `V1_RECOMMENDATION`: League-owned automated Series runner

새 `AutomatedSeriesRunner` application boundary가 fixture의 immutable input을 받아 BO3/BO5를 한 번에 진행하도록 한다. Runner는 game마다 full-auto `DraftEngine`과 `MatchEngineV1`을 호출하고, decisive commit 뒤에만 fixture-scoped Hard Fearless history와 score를 갱신한다.

공통 규칙이 중복되기 시작하면 interactive `SeriesLifecycleService`에서 side/score/history/decisive-commit invariant만 stateless `SeriesRulesKernel`로 추출할 수 있다. 그러나 repository, child Draft, UI command receipt와 cancellation state는 공유하지 않는다. 추출은 League Batch B의 focused parity test가 준비된 뒤 수행하며 이 문서 단계에서는 production refactor를 하지 않는다.

## League와 Season aggregate ownership

### `V1_RECOMMENDATION`: 얇은 League, authoritative Season

`LeagueAggregate`는 장기 container identity를 소유한다.

| 필드 | 계약 |
| --- | --- |
| `leagueId` | stable non-display identity |
| `revision` | optimistic concurrency revision |
| `displayName` | presentation metadata |
| `memberTeamCodes` | 현재 membership의 canonical team-code set |
| `seasonIds` | 생성 순서가 고정된 Season identity 목록 |
| `status` | `ACTIVE`, `ARCHIVED` |
| `createdAt`, `updatedAt` | 운영 metadata; gameplay seed 입력이 아님 |

`LeagueSeasonAggregate`가 실제 competition authority다.

| 필드 | 계약 |
| --- | --- |
| `seasonId`, `leagueId`, `revision` | aggregate identity와 optimistic revision |
| `teamSnapshot` | Season 시작 시 동결한 canonical team code/roster-resource identity |
| `schedulePolicy` | type, rounds, legs, fixture format, canonical schedule hash |
| `rootSeed`, `seedAlgorithm` | canonical signed-long root와 versioned algorithm |
| `standingsPolicyId` | 순위/동률 규칙 version |
| `status`, `terminalReason` | Season state와 structured reason |
| `rounds`, `fixtures` | ordered immutable projections와 current state |
| `standings` | completed fixture receipt에서만 파생·검증된 snapshot |
| `activeJobId`, `allowedCommands` | background execution authority |
| `createdAt`, `lastActivityAt` | 운영 metadata |

Season 생성 뒤 team snapshot, schedule, root seed, format, standings policy를 수정하지 않는다. 변경이 필요하면 새 Season을 만든다. Standings와 fixture score를 frontend나 worker-local cache가 별도 authority로 소유하지 않는다.

### `PRODUCT_DECISION_REQUIRED`: membership과 roster 시점

V1 권장은 Season 생성 순간의 team code와 active roster/resource identity를 동결하는 것이다. 장기 Career에서 이적/패치가 fixture 날짜별로 달라져야 하는지는 별도 제품 결정이다. V1 도중 roster를 live lookup으로 바꾸면 동일 seed replay가 달라지므로 허용하지 않는다.

## 상태 기계

### `V1_DECIDED`: League 상태

League container 자체는 simulation progress를 소유하지 않는다. `ACTIVE -> ARCHIVED`만 허용하며 archived League는 기존 Season 조회는 가능하지만 새 Season을 만들 수 없다. Running/paused/completed는 반드시 Season 상태다.

### `V1_DECIDED`: Season 상태

```text
DRAFT -> READY -> RUNNING <-> PAUSED -> COMPLETED
                    |             \
                    +-> BLOCKED    +-> CANCELLED
```

- `DRAFT`: 생성됐지만 schedule freeze 전이다.
- `READY`: schedule/seed/team/resource identity 검증 완료 상태다.
- `RUNNING`: 하나 이상의 fixture job을 dispatch할 수 있다.
- `PAUSED`: 새 lease를 만들지 않으며 이미 commit 중인 원자 작업은 완료할 수 있다.
- `BLOCKED`: correctness, replay identity 또는 unresolved fixture 때문에 진행을 멈춘다.
- `COMPLETED`: 모든 scheduled fixture가 decisive completion receipt를 가진다.
- `CANCELLED`: 사용자 취소. completed fixture 증거와 standings는 보존한다.

### `V1_DECIDED`: Round 상태

`PENDING -> RUNNING -> COMPLETED`가 정상 흐름이다. Fixture가 영구 차단되면 `BLOCKED`, Season 취소 시 `CANCELLED`가 된다. Round는 fixture status에서 검증 가능한 projection이며 독립 worker authority가 아니다.

### `V1_DECIDED`: Fixture 상태

```text
SCHEDULED -> QUEUED -> RUNNING -> COMPLETED
                         |  \
                         |   +-> BLOCKED
                         +-> FAILED_RETRYABLE -> QUEUED
SCHEDULED/QUEUED/FAILED_RETRYABLE -> CANCELLED (Season cancel only)
```

한 fixture는 정확히 한 AI BO3/BO5다. `COMPLETED`만 standings를 변경한다. no-decisive game, receipt mismatch, Hard Fearless pool exhaustion은 fixture를 `BLOCKED`로 만들고 standings를 변경하지 않는다. Runtime transport/worker loss는 lease expiry 뒤 `FAILED_RETRYABLE`이 될 수 있다.

### `V1_DECIDED`: Job/lease 상태

`QUEUED -> LEASED -> RUNNING -> SUCCEEDED`가 정상 흐름이다. Lease 만료나 transient worker failure는 `FAILED_RETRYABLE`, deterministic contract/integrity failure는 `FAILED_TERMINAL`, Season 취소는 `CANCELLED`다. `(seasonId, fixtureId, attemptNumber)`와 lease token을 structured identity로 사용하고 display text를 해석하지 않는다.

## Schedule, 경기 수와 side

### `V1_DECIDED`: 지원 schedule

| Schedule | Fixture 수 | 계약 |
| --- | ---: | --- |
| `SINGLE_ROUND_ROBIN` | `N*(N-1)/2` | 모든 unordered team pair가 정확히 1회 |
| `DOUBLE_ROUND_ROBIN` | `N*(N-1)` | 모든 pair가 두 leg, leg 2의 Game 1 side는 leg 1과 반대 |
| `CUSTOM` | request의 fixture 수 | server가 round/pair/leg/Game 1 BLUE와 중복·충돌을 exact 검증 |

Canonical team-code 오름차순을 입력으로 circle method를 사용한다. 홀수 팀이면 내부 `BYE` slot을 쓰되 public fixture를 만들지 않는다. 한 team은 같은 round에서 최대 한 fixture만 가진다. Fixture ID는 array index가 아니라 frozen schedule identity에서 파생한다.

### `V1_DECIDED`: Series format과 실제 game 수

Season은 기본 `BO3` 또는 `BO5` 하나를 동결한다. Fixture는 필요한 승수에 도달하면 종료하므로 실제 game 수는 BO3 2~3, BO5 3~5다. Fixture 수가 `F`일 때 Season minimum/maximum game 수는 BO3 `2F/3F`, BO5 `3F/5F`다. Schedule의 “경기 수”는 fixture 수와 possible/minimum/actual game 수를 별도 필드로 노출한다. Early finish를 가상의 game으로 채우지 않는다.

### `V1_RECOMMENDATION`: deterministic side balance

- Double round robin은 같은 pair의 두 leg에서 Game 1 BLUE를 반전해 planned fixture-level side를 exact balance한다.
- Single round robin은 team별 planned Game 1 BLUE/RED 차이를 가능한 범위에서 1 이하로 최소화한다. 동률 선택은 schedule seed의 canonical hash로 결정한다.
- 각 BO3/BO5 내부에서는 Game 1 mapping에서 매 game BLUE/RED를 교대한다.
- 조기 종료 때문에 실제 game side 수는 exact 50:50이 아닐 수 있으므로 `plannedGame1SideCounts`와 `actualGameSideCounts`를 구분해 보고한다.
- Custom schedule은 명시적 `game1BlueTeamCode`를 요구하고 planned imbalance를 응답에 노출한다.

### `PRODUCT_DECISION_REQUIRED`: 불균형 custom schedule

Custom schedule의 team별 planned Game 1 side 차이가 1을 넘을 때 hard reject할지, explicit `allowSideImbalance=true` 권한으로 허용할지 결정해야 한다. V1 권장은 기본 reject이고 별도 운영 권한이 생긴 뒤 waiver를 추가하는 것이다.

## AI BO3/BO5와 Hard Fearless

### `V1_DECIDED`

- 양 팀 모두 `AUTO_DRAFT_VARIETY_V1` Production Auto Draft를 사용한다. Player-controlled turn이나 managed team은 없다.
- Fixture마다 fresh `SeriesDraftHistory`를 만든다. Completed decisive game의 BLUE/RED pick 10개만 다음 game exclusion에 누적한다.
- Bans, failed Draft, no-decisive simulation과 uncommitted output은 history에 들어가지 않는다.
- Hard Fearless history는 fixture 경계를 넘지 않는다. Season 전체 champion ban처럼 사용하지 않는다.
- Game N의 Draft와 simulation은 같은 derived game seed와 frozen history-before hash에 결속된다.
- Pool exhaustion은 자동으로 규칙을 완화하지 않고 fixture/Season을 `BLOCKED`로 만든다.

### `PRODUCT_DECISION_REQUIRED`: blocked fixture 처리

Forfeit, 재경기, 규칙 완화 또는 무승부 포인트는 현재 domain에 없다. V1 권장은 standings mutation 없이 Season을 `BLOCKED`하고 운영자가 versioned resolution command를 선택할 때까지 멈추는 것이다. 실제 resolution 종류와 점수는 구현 전에 결정해야 한다.

## Versioned seed chain과 병렬 독립성

### `V1_DECIDED`

Algorithm ID는 `AI_LEAGUE_SEED_CHAIN_SHA256_FIRST_8_BYTES_BIG_ENDIAN_SIGNED_LONG_V1`이다. 각 단계는 UTF-8 canonical text의 SHA-256 앞 8바이트를 signed big-endian long으로 해석한다.

```text
seasonScheduleSeed = H(rootSeed, leagueId, seasonId, teamSnapshotHash, schedulePolicyHash)
fixtureRootSeed     = H(seasonScheduleSeed, fixtureId, roundNumber, legNumber,
                        teamACode, teamBCode, game1BlueTeamCode, seriesFormat)
gameSeed            = H(fixtureRootSeed, gameNumber, blueTeamCode, redTeamCode,
                        hardFearlessHistoryBeforeHash)
```

Canonical text에는 schema/domain line과 trailing newline가 필수다. Team, round, fixture, receipt list는 canonical order로 직렬화하고 unordered Set/Map iteration을 입력으로 사용하지 않는다.

Fixture seed는 실행 순서, worker ID, wall clock, retry count와 병렬도에 의존하지 않는다. 한 fixture의 games는 history dependency 때문에 순차 실행하고, 서로 다른 fixtures만 병렬 실행한다. 1 worker와 N workers가 같은 complete fixture/standings/receipt hash를 만들어야 한다.

## Standings와 tie-break

### `V1_DECIDED`: authoritative counters

각 team row는 `seriesPlayed`, `seriesWins`, `seriesLosses`, `gameWins`, `gameLosses`, `gameDifferential`, `points`, `rank`, `tieBreakTrace`를 갖는다. Completed fixture의 immutable result만 정확히 한 번 반영한다. `sum(seriesWins) == sum(seriesLosses) == completedFixtureCount`와 전체 game win/loss 대칭을 매 commit 검증한다.

### `V1_RECOMMENDATION`: 순위 정책

초기 policy ID는 `AI_LEAGUE_STANDINGS_MATCH_WINS_V1`을 권장한다.

1. Series wins 내림차순
2. Game differential 내림차순
3. Game wins 내림차순
4. 동률 팀 mini-league의 Series wins
5. 동률 팀 mini-league의 Game differential
6. Season seed에서 파생한 stable tie-break draw

마지막 draw는 재실행해도 같아야 하고 team code 사전순을 스포츠 결과처럼 위장하지 않는다. `tieBreakTrace`에는 적용된 단계와 비교 값만 structured field로 남긴다.

### `PRODUCT_DECISION_REQUIRED`: points와 최종 동률

Series 승/패에 몇 점을 줄지, final playoff seed가 걸린 완전 동률을 seeded draw로 끝낼지 tiebreak fixture를 추가할지 결정해야 한다. 권장은 승 1/패 0이고 정규 Season 표시는 deterministic draw, 우승/진출 결정은 별도 tiebreak fixture다.

## Background execution과 작업 단위

### `V1_RECOMMENDATION`: fixture-level job

Season 전체를 한 job으로 실행하면 실패 복구와 진행률이 거칠고, game-level job은 Hard Fearless history/score commit을 분산시킨다. 따라서 fixture 하나를 최소 durable job 단위로 한다.

- Dispatcher는 round dependency와 `maxParallelFixtures` 안에서 `SCHEDULED` fixture를 queue한다.
- Worker는 lease를 획득하고 `AutomatedSeriesRunner`로 fixture games를 순차 실행한다.
- Game decisive commit마다 fixture checkpoint와 compact receipt를 원자 저장할 수 있지만 standings는 fixture `COMPLETED`에서 한 번만 반영한다.
- 같은 lease/token의 duplicate completion은 idempotent다. 다른 token의 late completion은 거부한다.
- Retry는 기존 fixture root/game seed와 committed game checkpoint를 사용하고 새 seed를 만들지 않는다.
- Diagnostics, progress와 logging은 gameplay/Random/order에 영향을 주지 않는다.

### `PRODUCT_DECISION_REQUIRED`: 운영 한도

Season/team/fixture 최대 수, 기본 `maxParallelFixtures`, lease/heartbeat/attempt 한도와 취소 grace period는 운영 측정 뒤 정해야 한다. 숫자는 production resolver에 흩어 쓰지 않고 전용 configuration에 둔다.

## Compact result와 replay

### `V1_DECIDED`

Fixture는 다음 compact evidence만 장기 보존한다.

- series format, team/side mapping, final score, winner team code
- game별 final Draft hash, Hard Fearless history-before hash
- compact winner/end reason/duration/team kills/team gold
- input/resource/replay provenance, timeline, output, Random fingerprint hash
- canonical game receipt와 fixture aggregate receipt

20~34MB full timeline DTO, event/snapshot graph와 simulator mutable state는 Season aggregate에 저장하지 않는다. Explicit replay는 frozen input/Draft/resource identity로 fresh Match Engine execution을 수행하고 receipt exact equality 뒤 full payload를 반환한다. 필요한 historical resource를 사용할 수 없으면 `AI_LEAGUE_REPLAY_IDENTITY_UNAVAILABLE`, equality가 깨지면 `AI_LEAGUE_REPLAY_RECEIPT_MISMATCH`로 fail closed한다.

### `V1_RECOMMENDATION`: optional replay cache

자주 보는 경기의 gzip full replay를 object storage에 TTL cache할 수 있지만 authority는 receipt다. Cache miss는 deterministic replay로 복구하고 cache bytes 자체를 standings/결과 source of truth로 사용하지 않는다.

## Persistence 대안

| 대안 | 장점 | 한계 | 판정 |
| --- | --- | --- | --- |
| process-local map | 구현이 작고 interactive Series와 유사 | restart loss, multi-worker/lease/Season durability 불가 | League V1 부적합 |
| relational aggregate + job tables | optimistic revision, lease, 조회/standings, compact receipt에 적합 | schema/migration과 transaction 설계 필요 | `V1_RECOMMENDATION` |
| event sourcing | 완전한 audit/rebuild | event versioning, replay/read model 운영 복잡도 큼 | 후속 검토 |

### `V1_RECOMMENDATION`

관계형 persistence를 사용한다. League/Season, Round/Fixture, Job/Lease, immutable game receipt를 분리하고 fixture completion과 standings delta를 한 transaction에 넣는다. JSON column은 versioned compact evidence에만 사용하고 searchable identity/status/revision은 typed column으로 둔다. Full replay는 DB에 넣지 않는다.

### `PRODUCT_DECISION_REQUIRED`: retention

Completed Season, game receipt, job attempt log와 optional replay cache의 보존 기간/삭제 정책은 Save/Career 요구와 개인정보·용량 정책이 정해진 뒤 결정한다.

## Additive API handoff

### `V1_RECOMMENDATION`: endpoint

기존 `/api/v1/series`, `/api/v1/player-drafts`, `/api/v1/real-matches`는 변경하지 않고 아래 namespace를 추가한다.

| Method | Endpoint | 성공 |
| --- | --- | --- |
| `POST` | `/api/v1/ai-leagues` | `201/200` League create/idempotent replay |
| `GET` | `/api/v1/ai-leagues/{leagueId}` | League view |
| `POST` | `/api/v1/ai-leagues/{leagueId}/seasons` | `201/200` frozen Season/schedule |
| `GET` | `/api/v1/ai-leagues/{leagueId}/seasons/{seasonId}` | Season, rounds, standings summary |
| `POST` | `/api/v1/ai-leagues/{leagueId}/seasons/{seasonId}/simulation-jobs` | `202/200` start/resume job |
| `GET` | `/api/v1/ai-leagues/{leagueId}/seasons/{seasonId}/simulation-jobs/{jobId}` | progress/error view |
| `DELETE` | `/api/v1/ai-leagues/{leagueId}/seasons/{seasonId}/simulation-jobs/{jobId}` | `204` cancel dispatch |
| `GET` | `/api/v1/ai-leagues/{leagueId}/seasons/{seasonId}/fixtures/{fixtureId}` | compact fixture/game receipts |
| `POST` | `/api/v1/ai-leagues/{leagueId}/seasons/{seasonId}/fixtures/{fixtureId}/games/{gameNumber}/replay` | validated full match replay |

### `V1_DECIDED`: request schema 핵심

`AI_LEAGUE_CREATE_REQUEST_V1`은 `schemaVersion`, `displayName`, `memberTeamCodes`, `clientCommandId`만 받는다.

`AI_LEAGUE_SEASON_CREATE_REQUEST_V1`은 다음 exact fields를 받는다.

```text
schemaVersion
expectedLeagueRevision
seasonLabel
teamCodes
seriesFormat                 // BO3 | BO5
scheduleType                 // SINGLE_ROUND_ROBIN | DOUBLE_ROUND_ROBIN | CUSTOM
customFixtures               // CUSTOM이 아니면 []
rootSeed                     // canonical signed-long string
standingsPolicyId
clientCommandId
```

Custom fixture는 `fixtureKey`, `roundNumber`, `legNumber`, `teamACode`, `teamBCode`, `game1BlueTeamCode` exact fields를 갖는다.

`AI_LEAGUE_SIMULATION_JOB_CREATE_REQUEST_V1`은 `schemaVersion`, `expectedSeasonRevision`, `mode`(`RUN_ALL` 또는 `RUN_NEXT_ROUND`), `clientCommandId`를 받는다. Worker count나 seed를 public request로 받지 않는다.

### `V1_DECIDED`: response schema 핵심

`AI_LEAGUE_SEASON_VIEW_V1`은 `leagueId`, `seasonId`, `revision`, `status`, `terminalReason`, frozen team/resource/schedule/seed/standings policy identity, `rounds`, compact `fixtures`, `standings`, `activeJob`, `progress`, `allowedCommands`, timestamps를 제공한다.

`AI_LEAGUE_JOB_VIEW_V1`은 `jobId`, `seasonId`, `status`, `mode`, fixture totals(`scheduled`, `queued`, `running`, `completed`, `blocked`, `failedRetryable`), `attemptCount`, `createdAt`, `startedAt`, `lastHeartbeatAt`, `completedAt`, structured `failure`를 제공한다. Progress는 fixture 기준이며 gameplay 예상 완료 시간을 가장하지 않는다.

`AI_LEAGUE_FIXTURE_VIEW_V1`은 schedule identity, fixture status, BO3/BO5 score/winner, ordered game compact result/receipt, fixture receipt와 replay availability를 제공한다.

### `V1_DECIDED`: structured errors

모든 오류는 `AI_LEAGUE_API_ERROR_V1`의 `code`, `field`, `message`, `retryable`, `currentLeagueRevision`, `currentSeasonRevision`, `currentStatus`를 사용한다.

| HTTP | 대표 code |
| --- | --- |
| `400` | `AI_LEAGUE_INVALID_SCHEMA`, `AI_LEAGUE_INVALID_TEAM_SET`, `AI_LEAGUE_INVALID_SCHEDULE`, `AI_LEAGUE_INVALID_ROOT_SEED` |
| `404` | `AI_LEAGUE_NOT_FOUND`, `AI_LEAGUE_SEASON_NOT_FOUND`, `AI_LEAGUE_FIXTURE_NOT_FOUND`, `AI_LEAGUE_JOB_NOT_FOUND` |
| `409` | `AI_LEAGUE_STALE_REVISION`, `AI_LEAGUE_COMMAND_ID_PAYLOAD_CONFLICT`, `AI_LEAGUE_JOB_ALREADY_RUNNING`, `AI_LEAGUE_FIXTURE_LEASE_CONFLICT` |
| `422` | `AI_LEAGUE_SIDE_BALANCE_VIOLATION`, `AI_LEAGUE_FIXTURE_BLOCKED`, `AI_LEAGUE_HARD_FEARLESS_POOL_EXHAUSTED` |
| `500` | `AI_LEAGUE_REPLAY_RECEIPT_MISMATCH`, `AI_LEAGUE_STANDINGS_INTEGRITY_FAILED` |
| `503` | `AI_LEAGUE_CAPACITY_REACHED` |

## Frontend handoff

### `V1_RECOMMENDATION`

- Season setup은 team set, format, schedule, root seed와 standings policy를 제출하고 server-frozen schedule preview를 다시 표시한다.
- Dashboard는 backend `standings`, round/fixture status, job progress와 `allowedCommands`만 사용한다. 순위, score, Game 1 side와 다음 fixture를 재계산하지 않는다.
- “전체 실행”은 job POST 한 번이고 페이지 reload는 Season/job ID로 GET 복구한다. Frontend가 fixture별 simulate loop를 돌리지 않는다.
- Fixture detail은 compact evidence를 즉시 표시하고 사용자가 선택한 game만 explicit replay한다.
- Strict runtime validator는 schema/enum/identity/standings algebra를 검증하고 unknown field/version은 fail closed한다.
- Network/timeout 뒤에는 same `clientCommandId`와 authoritative GET으로 reconcile한다. Job이 이미 존재하면 새 job을 만들지 않는다.
- `BLOCKED`, `FAILED_RETRYABLE`, `PAUSED`, `CANCELLED`를 서로 다른 actionable 상태로 노출하고 일반 오류 문구 하나로 합치지 않는다.

## Correctness matrix

| 영역 | 최소 검증 |
| --- | --- |
| Schedule | N=2/홀수/짝수 single·double 수식, pair uniqueness, round collision, custom duplicate/unknown team |
| Side | double mirror exact, single planned delta bound, custom imbalance rejection, BO3/BO5 game alternation |
| Seed | canonical golden vectors, signed-long boundary, same input same seed, fixture/order/worker-count 독립성 |
| AI Draft | 양쪽 AUTO 20턴, frozen resource identity, no Player authority, same-seed Draft exact |
| Hard Fearless | decisive commit만 +10, failed/no-result +0, fixture 간 fresh history, BO3/BO5 pool boundary |
| Series runner | score/winner/max games, no-decisive BLOCKED, duplicate game completion idempotency, receipt integrity |
| Job | lease exclusivity, expiry/retry, late worker rejection, duplicate completion, pause/cancel boundary |
| Persistence | crash after game checkpoint/fixture commit/standings commit 각 지점의 atomic recovery |
| Parallelism | 1 worker 대 N workers complete receipt/standings hash exact equality |
| Standings | win/loss/game algebra, duplicate fixture 0 delta, mini-league tie trace, deterministic final tie |
| Replay | exact receipt equality, resource drift rejection, replay mutation 0, cache hit/miss equality |
| API | exact schema, stale revisions, idempotent command replay, payload conflict, structured error/current state |
| Frontend | reload recovery, one logical job, backend-authoritative standings, blocked/retry UX, explicit replay |
| Regression | existing Series/Player Draft/Real Match API exact compatibility와 default test의 diagnostic exclusion |

대형 round robin distribution/latency 관측은 explicit diagnostic task로 실행하고 deterministic correctness test에 승률 threshold를 넣지 않는다.

## 구현 batch

1. `AI_VS_AI_LEAGUE_SIMULATION_V1_PRODUCT_DECISION_FREEZE`
   - points/tie resolution, custom side waiver, blocked fixture resolution, roster snapshot과 운영/retention 한도를 승인한다.
2. `AI_VS_AI_LEAGUE_SIMULATION_V1_DOMAIN_AND_SCHEDULE`
   - pure League/Season/round/fixture values, schedule builder, seed chain, standings calculator와 golden tests를 구현한다.
3. `AI_VS_AI_LEAGUE_SIMULATION_V1_AUTOMATED_SERIES_RUNNER`
   - AI BO3/BO5, fixture-scoped Hard Fearless, compact receipt와 interactive Series parity-focused invariants를 구현한다.
4. `AI_VS_AI_LEAGUE_SIMULATION_V1_PERSISTENCE_AND_JOBS`
   - relational repository, optimistic revision, fixture lease/checkpoint, dispatcher/worker와 crash recovery를 구현한다.
5. `AI_VS_AI_LEAGUE_SIMULATION_V1_API`
   - additive controller/DTO/parser/error schema와 idempotent job/replay endpoint를 구현한다.
6. `AI_VS_AI_LEAGUE_SIMULATION_V1_FRONTEND`
   - setup/schedule preview/dashboard/standings/job recovery/fixture replay를 구현한다.
7. `AI_VS_AI_LEAGUE_SIMULATION_V1_PRODUCTION_ACCEPTANCE`
   - focused/full regression, cross-worker determinism, bounded performance/capacity evidence와 운영 문서를 완결한다.

## 아직 확정되지 않은 제품 결정

`PRODUCT_DECISION_REQUIRED` 항목은 다음 다섯 가지다.

1. Season roster를 생성 시 동결할지 fixture 날짜별 roster version을 허용할지
2. Custom schedule side imbalance의 reject/waiver 정책
3. Blocked fixture의 forfeit/rematch/rule relaxation/draw 처리
4. Points와 final tie를 draw/tiebreak fixture 중 무엇으로 끝낼지
5. Season/team/parallelism/lease/retry와 receipt/job/replay retention 한도

이 결정을 고정하기 전에는 persistence schema와 public API를 구현하지 않는다.
