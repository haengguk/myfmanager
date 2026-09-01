# AI League V1 API and Job Boundary Hardening

## Status and integrity

`AI_VS_AI_LEAGUE_SIMULATION_V1_API_ACCEPTED`

기준 HEAD는 `b85b7eec214ca2ca4d3e9c3db4bcfec10c29a309`이다. Frozen product decision
hash `81a4755760fb513c5803d55dd4855c03fda487114bb7c89b431c959a00a0fb14`,
Production Auto Draft, Match Engine V9, Matchup/Composition ON, Jungle Economy/Tempo OFF와
gameplay Random 소비 순서는 바꾸지 않았다. 기존 Series, Player Draft, Real Match API도 그대로다.

## 사용 흐름

1. `HYBRID_MANAGER` 또는 `SPECTATOR_FULL_AUTO` Season을 생성한다.
2. Season view에서 18 rounds/90 BO3 fixture, standings와 `allowedCommands`를 읽는다.
3. `run-current-round`를 호출하면 `FULL_AUTO` fixture만 durable job으로 등록되고 HTTP는 202로
   즉시 반환된다.
4. Season/fixture/job GET을 polling해 진행을 확인한다.
5. Hybrid의 관리 팀 fixture는 `player-series`로 server-owned Series를 만들고 기존
   `/api/v1/series/{seriesId}` 흐름에서 Draft와 경기를 진행한다.
6. completion command는 winner나 score를 받지 않는다. 서버가 completed Series proof를 다시
   검증하고 receipt/outbox/ledger를 통해 standings를 한 번만 갱신한다.

## Phase A hardening

| 경계 | Before | Current contract |
|---|---|---|
| restart | lease 만료 전 재시작이면 이전 `LEASED/RUNNING` job이 15분까지 멈출 수 있음 | startup은 이전 process incarnation lease를 즉시 process loss로 회수; runtime은 만료 lease만 회수 |
| failure | reason 문자열의 `TIMEOUT`/prefix로 retry를 추측 | Spring/SQL/explicit worker typed failure만 transient; deterministic mismatch는 즉시 `BLOCKED` |
| hard max | active count와 lease 사이 check-then-act 경쟁 | DB singleton scheduler row 아래 global count+lease를 원자화; 여러 Season을 합쳐 exact hard max 4 |
| cancel | Season/job/fixture가 별도 commit될 수 있음 | global lease lock+Season row lock 아래 lifecycle CAS/job/fixture 취소를 한 transaction으로 commit |

Process incarnation과 fencing token은 gameplay seed가 아니다. Attempt 1 process loss는 같은 frozen
input으로 `RETRY_PENDING`, attempt 2 loss는 `BLOCKED`다. Late heartbeat/finish는 job row의
token/fence/incarnation/attempt를 원자 검증한 뒤에만 receipt를 기록하므로 mutation은 0이다.
Startup recovery는 reconciliation만 수행하고 gameplay를 자동 실행하지 않는다.

## Endpoint contract

```text
POST   /api/v1/leagues
GET    /api/v1/leagues/{leagueId}/seasons/{seasonId}
GET    /api/v1/leagues/{leagueId}/seasons/{seasonId}/standings
GET    /api/v1/leagues/{leagueId}/seasons/{seasonId}/fixtures
GET    /api/v1/leagues/{leagueId}/seasons/{seasonId}/fixtures/{fixtureId}

POST   /api/v1/leagues/{leagueId}/seasons/{seasonId}/commands/run-current-round
POST   /api/v1/leagues/{leagueId}/seasons/{seasonId}/commands/pause
POST   /api/v1/leagues/{leagueId}/seasons/{seasonId}/commands/resume
DELETE /api/v1/leagues/{leagueId}/seasons/{seasonId}

GET    /api/v1/leagues/{leagueId}/seasons/{seasonId}/jobs/{jobId}

POST   /api/v1/leagues/{leagueId}/seasons/{seasonId}/fixtures/{fixtureId}/player-series
GET    /api/v1/leagues/{leagueId}/seasons/{seasonId}/fixtures/{fixtureId}/player-series
POST   /api/v1/leagues/{leagueId}/seasons/{seasonId}/fixtures/{fixtureId}/player-series/completion
GET    /api/v1/leagues/{leagueId}/seasons/{seasonId}/fixtures/{fixtureId}/completion-status
```

### Request schemas

| Schema | Exact fields |
|---|---|
| `AI_LEAGUE_CREATE_REQUEST_V1` | `schemaVersion`, `leagueKey`, `seasonKey`, `seasonMode`, nullable `managedTeamCode`, canonical signed-long string `seasonRootSeed`, `clientCommandId` |
| `AI_LEAGUE_LIFECYCLE_COMMAND_V1` | `schemaVersion`, non-negative `expectedLifecycleRevision`, `clientCommandId` |
| `AI_LEAGUE_RUN_ROUND_COMMAND_V1` | `schemaVersion`, non-negative `expectedLifecycleRevision`, `clientCommandId` |
| `AI_LEAGUE_PLAYER_SERIES_COMMAND_V1` | `schemaVersion`, non-negative `expectedLifecycleRevision`, `clientCommandId` |
| `AI_LEAGUE_PLAYER_COMPLETION_COMMAND_V1` | 위 필드와 server-issued `bindingHash` |

Unknown field, 잘못된 schema, 비정규 signed-long과 malformed JSON은 400이다. Hybrid는 현재
authoritative 10팀 중 관리 팀이 반드시 필요하고 Spectator는 관리 팀을 받지 않으며, 잘못된
mode/team 조합은 422다. Client는 winner, score, standings delta, fixture/game seed, side,
execution mode, profile, policy, receipt, output, replay provenance, lease token/fence를 제출할 수 없다.

### Response schemas

- `AI_LEAGUE_SEASON_VIEW_V1`: League/Season identity, lifecycle/standings revision, mode, managed team,
  root seed, schedule/frozen/product/runtime identity, current round, fixture counters, ordered standings,
  playable managed fixture, `allowedCommands`, `updatedAt`
- `AI_LEAGUE_FIXTURE_VIEW_V1` / `AI_LEAGUE_FIXTURE_LIST_V1`: structured team/round/side/BO3,
  execution/lifecycle, binding/job/completion projection
- `AI_LEAGUE_STANDINGS_VIEW_V1`: policy ID와 deterministic ordered rows
- `AI_LEAGUE_JOB_VIEW_V1`: public status/revision/attempt/stable failure code; lease token/fence는 없음
- `AI_LEAGUE_RUN_RESPONSE_V1`: queued/existing/player-excluded 수, Season과 job summaries
- `AI_LEAGUE_PLAYER_SERIES_VIEW_V1`: binding/Series identity와 재개·reconciliation capability
- `AI_LEAGUE_COMPLETION_STATUS_VIEW_V1`: fixture/binding/outbox/standings application 상태

Backend-authoritative `allowedCommands` closed set은
`RUN_CURRENT_ROUND_AUTO_FIXTURES`, `START_PLAYER_SERIES`, `RESUME_PLAYER_SERIES`,
`RECONCILE_PLAYER_SERIES_COMPLETION`, `VIEW_STANDINGS`, `VIEW_FIXTURE`, `PAUSE_SEASON`,
`RESUME_SEASON`, `CANCEL_SEASON`이다. 현재 상태에서 불가능한 명령은 view에서 제외한다.

## HTTP and error semantics

- 신규 Season/Player Series: 201
- exact replay 또는 완료 view: 200
- background accepted 또는 completion pending: 202
- 성공한 Season cancel: 204
- malformed/unknown/schema: 400
- unknown resource 또는 cross-scope identity: 404
- stale revision, command payload conflict, lifecycle conflict: 409
- invalid mode/team/frozen domain input: 422
- typed temporary DB availability: 503, `retryable=true`
- unexpected internal error: 500

Public error는 `AI_LEAGUE_API_ERROR_V1`의 `code`, nullable `field`, 사용자 메시지,
`retryable`, nullable current lifecycle revision/status만 노출한다. SQL, path, stack과 내부 exception
message는 노출하지 않는다.

## Idempotency, response loss and polling

V3 `league_api_command`가 command ID, type, canonical payload hash, scope와 HTTP status marker를
durable하게 소유한다. Command claim, application mutation과 completion marker는 한 transaction이다.
같은 ID+payload replay는 gameplay/job/outbox/standings mutation 0이고, 같은 ID+다른 payload는
409다. GET은 revision, timestamp, outbox와 standings를 변경하지 않는다.

`run-current-round`는 현재 round의 `FULL_AUTO`만 dispatch한다. Hybrid의 `PLAYER_CONTROLLED`
fixture job은 exact 0이다. Production background executor는 bounded queue 32와 하나의 orchestration
pump를 사용하며, 각 drain은 default 2 workers만 실행한다. DB global hard max는 4다. HTTP thread는
경기 완료를 기다리지 않고 202를 반환하며 이후 Season/fixture/job GET으로 polling한다.

## Player Series reconciliation

Player start/resume은 fixture가 소유한 opponent, BO3, Game 1 side, fixture root/game seed, Hard
Fearless history와 Production identity만 사용한다. Exact replay는 같은 binding과 bound Series를
반환한다. Completion request는 binding을 식별할 뿐 winner/score/receipt를 받지 않는다. 서버가
completed Series의 stored receipt와 Production V9 evidence를 재검증하고 V2 receipt/outbox를 만든다.
Duplicate completion, outbox redelivery와 ack loss에서도 application ledger 때문에 standings
revision은 한 번만 증가한다. Cross-League/Season/fixture binding은 거부한다.

## Verification

- Phase A+API+existing League/Series/Player Draft/Real Match/gzip affected lane:
  16 suites / 62 tests / failures 0 / errors 0 / skipped 0, Gradle wall 2분 25초
- 원자 fencing completion 직접 영향 재검증:
  5 suites / 20 tests / failures 0 / errors 0, Gradle wall 1분 22초
- explicit run runtime-expiry recovery 연결 재검증:
  3 suites / 12 tests / failures 0 / errors 0, Gradle wall 47초
- Final complete backend regression:
  256 suites / 2,333 tests / failures 0 / errors 0 / skipped 2,
  aggregate JUnit XML 1,139.817초, Gradle wall 19분 13초

첫 complete regression 255 suites / 2,332 tests는 clean pass했다. 이후 수동 경계 감사에서
explicit run이 runtime-expired lease recovery를 호출하지 않는 운영 누락을 발견해 production
orchestration과 전용 테스트를 추가했고, 최종 executable tree에서 두 번째 full을 실행했다.

실제 Production V9 smoke는 대표 FULL_AUTO fixture 1개와 Player handoff 1개만 사용했다. HTTP
transport는 CORS와 90-fixture JSON/gzip byte-equivalence를 확인했다. 90-fixture official run,
balance/calibration/holdout, 대형 통계·성능 진단은 실행하지 않았다. Frontend source를 변경하지
않아 frontend build/Playwright도 실행하지 않았다.

## Remaining limits and next step

이 구현은 local single-node file-backed H2 reference다. Auth/ownership, multi-node database/worker
consensus, external broker, distributed rate/capacity control과 production load evidence는 없다.
Standalone Series repository의 기존 process-local 의미도 바꾸지 않았다. League dashboard와
사용자 journey UI는 아직 없으며 다음 단계는 `AI_VS_AI_LEAGUE_SIMULATION_V1_FRONTEND`다.
