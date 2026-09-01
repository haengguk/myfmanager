# AI vs AI League Simulation V1 Contract Sketch

상태: `AI_VS_AI_LEAGUE_SIMULATION_V1_PRODUCT_DECISIONS_FROZEN_AND_HYBRID_SEASON_CONTRACT_READY`

이 문서는 AI 팀끼리만 경기하는 기능을 넘어, 한 관리 팀의 경기는 플레이어가 직접 Draft하고 나머지 경기는 AI가 자동 진행하는 Hybrid Season V1의 구현 계약을 고정한다. 제품 결정의 canonical 목록은 [AI vs AI League Simulation V1 Product Decisions](ai-vs-ai-league-simulation-v1-product-decisions.md)에 있으며, 이 문서는 그 결정을 aggregate, 상태 기계, persistence, API와 frontend handoff로 구체화한다.

이 상태는 설계와 구현 인계가 준비됐다는 뜻이다. League/Season production Java, API, worker, DB, migration과 frontend가 구현됐다는 뜻은 아니다.

## 현재 코드 감사와 경계 판정

### 안전하게 재사용할 수 있는 것

- 현재 Series의 team-code score, BO3/BO5 required wins와 game별 BLUE/RED 교대 규칙
- `SeriesIdentity`의 canonical SHA-256 identity와 seeded determinism 규율
- decisive game의 양 팀 completed picks 10개만 누적하는 fixture-scoped Hard Fearless 의미
- `PlayerControlledDraftEngine`과 기존 Player Draft workspace의 PLAYER/AI mixed-authority Draft
- `MatchEngineV1`의 Production V9 실행, production policy/profile/configuration/rules/engine 및 resource provenance 검증
- `SeriesGameReceipt`의 compact game receipt 원칙과 replay 검증 경계
- reservation, compare-and-commit, revision과 command idempotency가 보여 준 stale/duplicate 방어 원칙
- Series frontend의 server-authoritative `allowedCommands`, ID pointer 복구, Draft/Playback/Result 상태 전달

### 직접 재사용하면 안 되는 것

현재 `SeriesAggregate`는 `managedTeamCode`가 두 참가 팀 중 하나라고 강제한다. `SeriesLifecycleService`와 `SeriesRepository`는 한 관리 팀의 interactive child Draft, process-local TTL, 최대 보관량과 in-memory command receipt를 소유한다. `PlayerDraftCompletionBinding`도 현재 process 안의 trusted object identity에 결속한다.

따라서 다음은 V1 계약이 아니다.

- public `/api/v1/series`를 AI worker의 내부 RPC처럼 호출해 20턴 Draft action을 대신 제출하는 방식
- process-local Series repository를 durable Season authority로 승격하는 방식
- 브라우저가 Series winner, score나 receipt를 League에 다시 제출하는 방식
- in-memory `PlayerDraftCompletionBinding` 객체를 재시작 뒤에도 유효한 League certificate로 간주하는 방식

기존 standalone Series API와 lifecycle은 그대로 유지한다. League는 Draft/Match/receipt kernel과 검증 원칙을 재사용하되, durable Season/Fixture/Job/Binding/Completion 권위는 새 League 경계에서 소유한다.

## V1 제품 방향

### Season mode

`LeagueSeasonAggregate`는 생성 시 다음 mode 중 하나를 고정하며 이후 바꿀 수 없다.

| `seasonMode` | `managedTeamCode` | fixture 실행 mode |
|---|---|---|
| `HYBRID_MANAGER` | frozen Season team set에 포함된 정확히 한 팀 | 관리 팀이 포함된 fixture는 `PLAYER_CONTROLLED`, 나머지는 `FULL_AUTO` |
| `SPECTATOR_FULL_AUTO` | `null` | 모든 fixture가 `FULL_AUTO` |

`HYBRID_MANAGER`는 non-null `managedTeamCode`와 그 팀의 exact `managedTeamSnapshotIdentity`를 요구한다. `SPECTATOR_FULL_AUTO`는 두 값을 모두 null로 둔다. mode와 맞지 않는 null/non-null 조합, frozen team set에 없는 team code와 snapshot mismatch는 Season 생성/freeze 전에 거부한다.

`DELEGATE_MANAGED_FIXTURE_TO_AI`와 Season 도중 관리 팀 변경은 V1 범위 밖이다. `FULL_AUTO` batch와 job lease는 어떤 경우에도 `PLAYER_CONTROLLED` fixture를 선택할 수 없다.

### Hybrid 진행 의미

- AI fixture는 같은 round의 관리 팀 경기를 기다리지 않고 완료될 수 있다.
- Round는 `FULL_AUTO`와 `PLAYER_CONTROLLED` fixture가 모두 완료돼야 완료된다.
- `HYBRID_MANAGER`의 `RUN_ALL`은 현재 round의 `FULL_AUTO` fixture만 dispatch한 뒤 관리 팀 경기를 기다린다. 다음 round를 자동으로 건너뛰지 않는다.
- `SPECTATOR_FULL_AUTO`의 `RUN_ALL`은 round barrier를 지키며 모든 round를 자동 진행할 수 있다.
- `WAITING_FOR_PLAYER`는 실패나 `BLOCKED`가 아니다.

## Aggregate와 authority

### `LeagueAggregate`

League는 장기 container identity와 다음 항목만 소유한다.

- `leagueId`, display metadata, `ACTIVE | ARCHIVED`
- Season ID의 ordered collection
- 생성/보관 정책 identity

Running, paused, completed와 standings는 League가 아니라 Season 상태다.

### `LeagueSeasonAggregate`

Season은 competition의 유일한 authority다.

- `seasonId`, `revision`, `status`, `seasonMode`, nullable `managedTeamCode`
- immutable team set과 `managedTeamSnapshotIdentity`
- team별 frozen roster, PlayerId, rating/proficiency snapshot identity
- Champion/Draft/Matchup/Composition resource identity
- production policy/profile/configuration/gameplay rules/engine identity
- schedule policy, standings policy와 root seed identity
- ordered rounds/fixtures와 fixture별 frozen `executionMode`
- standings projection과 canonical receipt application ledger
- current playable managed fixture projection
- AI job에서 제외돼야 하는 managed fixture identity set
- `allowedCommands`와 Hybrid progression counters

Season freeze 뒤 membership, roster, resource, policy/profile/configuration/rules/engine과 schedule을 수정할 수 없다. 현재 LCK V1은 authoritative resource에 실제 존재하는 정확히 10개 팀과 팀당 5명, 총 50명만 허용한다. 누락 데이터를 문서나 runtime에서 발명하지 않는다.

### `LeagueFixtureAggregate`

Fixture는 다음 frozen input과 lifecycle을 소유한다.

- fixture/round/leg identity, 두 team code, BO3/BO5와 Game 1 side
- `fixtureRootSeed`와 seed algorithm version
- `FULL_AUTO | PLAYER_CONTROLLED`
- initial Hard Fearless history hash
- roster/resource/policy/profile/configuration/rules/engine identities
- job/lease 또는 player-series binding/progress
- exactly one canonical `LeagueFixtureCompletionReceiptV1`
- standings application status와 idempotency identity

하나의 canonical fixture receipt만 winner, score와 standings의 근거다. frontend projection, worker log와 optional full replay cache는 권위가 아니다.

## Schedule, fixture 수와 side

V1 production default는 정확히 10개 팀의 double round robin, 18 rounds, 90 fixtures, BO3다. 각 팀은 round마다 최대 한 fixture만 가진다.

- 같은 두 팀의 1·2차전은 Game 1 side를 서로 mirror한다.
- 한 fixture 안에서는 Game 1 side부터 game마다 BLUE/RED를 교대한다.
- 조기 2승 또는 3승에서 Series를 끝내므로 실제 game 수는 BO3 2~3개, BO5 3~5개다.
- single round robin은 schedule policy로 설계할 수 있지만 V1 production default가 아니다.
- `CUSTOM` schedule과 `allowSideImbalance`는 V1 production 범위 밖이다.

Schedule 생성은 canonical team-code order와 versioned rotation algorithm을 사용한다. DB 반환 순서, display name과 unordered collection iteration은 fixture identity, side 또는 seed에 영향을 주지 않는다.

## 상태 기계

### Season

```text
DRAFT
  -> FROZEN
  -> READY
  -> RUNNING <-> PAUSED
  -> WAITING_FOR_PLAYER -> RUNNING
  -> COMPLETED

READY | RUNNING | PAUSED | WAITING_FOR_PLAYER
  -> BLOCKED
  -> CANCELLED
```

`COMPLETED`, `CANCELLED`는 terminal이다. `BLOCKED`는 자동 진행에 대해서는 terminal이며, 명시적 Season cancel만 `CANCELLED`로 바꿀 수 있다. Season cancel은 새 dispatch와 새 player-series 시작을 막는다. 이미 standings transaction commit 단계에 들어간 completion은 끝날 수 있으며 idempotent application ledger가 중복 반영을 막는다.

### Round와 진행 counter

Round projection은 최소 다음 counter를 제공한다.

- `scheduledAuto`
- `runningAuto`
- `completedAuto`
- `awaitingPlayer`
- `activePlayerSeries`
- `completedPlayer`
- `blocked`

모든 fixture가 `COMPLETED`일 때만 Round가 `COMPLETED`다. 관리 fixture만 남아 있으면 Season은 `WAITING_FOR_PLAYER`가 될 수 있다.

### FULL_AUTO fixture와 job

```text
SCHEDULED -> QUEUED -> LEASED -> RUNNING
  -> COMPLETION_PENDING_VERIFICATION -> COMPLETED
  -> RETRY_PENDING -> QUEUED
  -> BLOCKED
  -> CANCELLED
```

Job lease는 frozen `executionMode=FULL_AUTO`를 다시 검증한 뒤에만 발급한다. stale lease의 late result, 다른 fixture receipt, 다른 frozen identity와 seed 결과는 commit할 수 없다.

### PLAYER_CONTROLLED fixture

```text
SCHEDULED
  -> AWAITING_PLAYER
  -> PLAYER_SERIES_RESERVED
  -> PLAYER_SERIES_ACTIVE
  -> COMPLETION_PENDING_VERIFICATION
  -> COMPLETED

PLAYER_SERIES_RESERVED | PLAYER_SERIES_ACTIVE
  -> PLAYER_SERIES_RESTART_REQUIRED
  -> BLOCKED

SCHEDULED | AWAITING_PLAYER | PLAYER_SERIES_RESERVED | PLAYER_SERIES_ACTIVE
  | PLAYER_SERIES_RESTART_REQUIRED | BLOCKED
  -> CANCELLED  (Season cancel only)
```

브라우저 종료나 네트워크 단절은 fixture 취소가 아니다. durable binding과 server progress가 있으면 `RESUME_PLAYER_SERIES`로 복구한다. 복구할 수 없는 binding/receipt corruption 또는 frozen resource 불일치만 `PLAYER_SERIES_RESTART_REQUIRED`나 `BLOCKED`가 된다.

## FULL_AUTO `AutomatedSeriesRunner`

League-owned `AutomatedSeriesRunner`는 하나의 immutable fixture input을 받아 Series를 끝까지 실행한다.

1. frozen binding과 Production identity를 검증한다.
2. fixture-scoped fresh Hard Fearless history에서 현재 exclusions를 계산한다.
3. 양 팀 모두 Production Auto Draft로 20턴 Draft를 완료한다.
4. 같은 frozen side, game seed와 Production V9 identity로 `MatchEngineV1`을 실행한다.
5. decisive game의 verified receipt와 completed picks 10개만 fixture history/score에 commit한다.
6. required wins에 도달하면 unified fixture completion receipt를 만든다.

Runner는 resolver/gameplay state를 전역에 보관하지 않으며 fixture끼리 Random, Draft history, score와 mutable cache를 공유하지 않는다. 병렬성은 완료 순서만 바꿀 수 있고 각 fixture output을 바꿀 수 없다.

## Player Controlled Series handoff

### 시작과 재개

`PLAY_MANAGED_FIXTURE` 또는 `START_PLAYER_SERIES`는 다음 순서를 따른다.

1. League가 현재 Season revision, current round, fixture status와 `executionMode=PLAYER_CONTROLLED`를 검증한다.
2. server가 `LeagueFixtureSeriesBinding`을 만들고 fixture reservation과 함께 durable하게 기록한다.
3. League-bound Player Series를 만들고 기존 Player Draft/Series 화면을 frozen context로 연다.
4. player는 자기 side의 Draft 선택만 제출한다. opponent AI, side, seed, history와 Production identity는 server가 소유한다.
5. decisive Series completion을 server가 검증해 `LeagueFixtureCompletionReceiptV1`을 생성한다.
6. durable outbox/idempotent command가 fixture completion과 standings 반영을 연결한다.

League 진입에서는 standalone `SeriesSetupPage`를 거치지 않는다. player는 opponent, format, Game 1 side, root seed, profile 또는 fixture를 다시 고를 수 없다.

### `LeagueFixtureSeriesBinding`

최소 필드는 다음과 같다.

- schema/hash algorithm version과 `bindingHash`
- `leagueId`, `seasonId`, `fixtureId`, expected Season revision와 reservation identity
- bound `seriesId`
- 두 frozen team code, format, Game 1 side
- `fixtureRootSeed`이자 bound Series root seed
- schedule/standings policy identity
- roster/resource/policy/profile/configuration/rules/engine identities
- initial Hard Fearless history와 hash
- created/reconciled status와 durable revision

public client는 이 필드를 생성하거나 다시 제출해 덮어쓸 수 없다. 현재 in-memory `PlayerDraftCompletionBinding`의 검증 원칙은 참고하되, League binding은 serialized canonical value와 stored receipt identity로 재시작 뒤에도 검증 가능해야 한다.

### 생성, 취소와 복구 권위

1. Player fixture Series는 현재 Season/fixture를 소유한 League application command만 생성할 수 있다.
2. team/format/Game 1 side/root seed와 frozen production identity는 Schedule/Season authority가 고정한다.
3. 기존 일반 `/api/v1/series` create는 League binding을 만들 수 없다. 별도 League fixture endpoint가 `LEAGUE_BOUND` identity의 Series를 생성하며 exact command replay는 같은 Series를 반환하고 payload conflict는 거부한다.
4. 결과는 frontend 제출이 아니라 completed Series aggregate와 stored binding에서 server가 만든 receipt로 전달한다.
5. Series completion과 League commit 사이 crash는 durable receipt/outbox의 미처리 상태를 재개해 복구한다.
6. outbox event/command replay는 receipt application ledger의 idempotency key로 standings를 다시 더하지 않는다.
7. League-bound child/Series 취소는 fixture를 `PLAYER_SERIES_RESTART_REQUIRED`로 두고 standings를 바꾸지 않는다. bound Series 화면에는 standalone `CANCEL_SERIES`를 Season cancel처럼 노출하지 않는다. Season cancel만 fixture를 `CANCELLED`로 만든다. 기존 standalone Series cancel 의미는 유지한다.
8. Process restart 중이던 Player fixture는 durable binding, Draft/game progress와 receipt에서 같은 bound Series를 복구한다. 복구 불가능한 불일치는 새 Series를 조용히 만들지 않고 `PLAYER_SERIES_RESTART_REQUIRED` 후 `BLOCKED`로 간다.

## Unified completion receipt와 standings atomicity

AI와 Player fixture는 동일한 server-created `LeagueFixtureCompletionReceiptV1`을 사용한다.

### 최소 receipt 내용

- League/Season/Fixture와 `LeagueFixtureSeriesBinding` identity
- `FULL_AUTO | PLAYER_CONTROLLED`
- ordered game receipts
- final team-code score, winner, 실제 game count
- game별 side mapping, seed, Draft identity, history-before/after hash
- roster/resource/policy/profile/configuration/rules/engine identity
- replay/timeline/output/Random fingerprint identity
- canonical fixture receipt hash와 schema/hash algorithm version

Frontend는 receipt를 만들거나 winner/score/standings와 함께 제출하지 않는다.

### 선택한 commit 모델

V1은 **server-side durable completion receipt + transactional outbox + idempotent League consumer**를 사용한다.

검토한 선택지는 다음 세 가지다.

1. Series completion과 League standings를 같은 relational transaction으로 묶는 방식은 가장 짧지만 두 aggregate의 저장/배포 경계를 강하게 결합한다.
2. Series completion이 durable receipt와 outbox를 기록하고 League가 idempotent하게 소비하는 방식은 짧은 reconciliation 지연 대신 crash/retry와 독립 배포 경계를 안전하게 다룬다. V1 선택이다.
3. Frontend가 completion을 League에 다시 제출하는 방식은 browser/network를 권위 전달 경로로 만들기 때문에 사용하지 않는다.

선택한 처리 순서는 다음과 같다.

1. Series 또는 automated runner completion transaction이 immutable canonical receipt와 outbox event를 기록한다.
2. League consumer는 `seasonId + fixtureId + canonicalFixtureReceiptHash`를 idempotency key로 검증한다.
3. fixture가 아직 같은 binding으로 미반영 상태일 때만 `COMPLETED`, standings delta와 application ledger를 한 transaction에서 commit한다.
4. crash, duplicate delivery, stale Season revision과 repeated frontend reconciliation은 최대 한 번의 standings delta만 만든다.

Series completion 자체를 frontend timeout 때문에 rollback하지 않는다. Outbox delivery가 지연되면 fixture는 `COMPLETION_PENDING_VERIFICATION`으로 남고 frontend는 completion status를 조회한다. cross-fixture receipt, 다른 binding/seed/identity와 이미 다른 hash로 완료된 fixture는 fail-closed한다.

저장된 canonical receipt가 검증되면 이후 authored resource가 바뀌어 full replay가 불가능해져도 기존 standings는 유지된다. 이 경우 `receiptAuthority=VERIFIED`, `replayAvailability=UNAVAILABLE_RESOURCE_DRIFT`처럼 권위와 재생 가능성을 분리한다.

## Side, seed와 Hard Fearless parity

### Seed chain

```text
Season root seed
  -> fixtureRootSeed
  -> bound Series root seed (같은 값, 재파생 금지)
  -> game seed
```

- fixture root는 `AI_LEAGUE_FIXTURE_ROOT_SEED_SHA256_FIRST_8_BYTES_BIG_ENDIAN_SIGNED_LONG_V1`으로 canonical Season/fixture identity에서 파생한다.
- Player fixture에서는 `fixtureRootSeed`를 bound Series의 root seed로 그대로 전달한다. League와 Series가 각각 한 번씩 fixture seed를 파생하는 double derivation을 금지한다.
- 기존 standalone Series V1의 mode-dependent seed schema는 변경하지 않는다. League-bound AI/Player Series는 `AI_LEAGUE_BOUND_SERIES_GAME_SEED_SHA256_FIRST_8_BYTES_BIG_ENDIAN_SIGNED_LONG_V1` sibling schema와 canonical pair-first `seedAnchorTeamCode`를 공통 사용해 execution mode가 game seed를 바꾸지 않게 한다.
- FULL_AUTO runner도 같은 bound Series root와 game-seed algorithm을 사용한다.
- player는 새 seed를 선택할 수 없다.
- retry는 frozen seed/checkpoint를 그대로 사용하며 reseed하지 않는다.

AI와 Player 경기는 wins-required, side alternation, Hard Fearless 누적, receipt 필드와 Production V9 identity에서 exact parity를 가져야 한다. 실제 Draft decision은 player 입력과 AI 정책 차이 때문에 같을 필요가 없다.

### Hard Fearless

- history scope는 Season이나 round가 아니라 fixture다.
- decisive committed game의 양 팀 picks 10개만 다음 game의 exclusions가 된다.
- ban, failed/cancelled game, no-result와 rejected duplicate는 history를 바꾸지 않는다.
- Player/AI fixture 모두 같은 pool completion preflight와 fail-closed 의미를 사용한다.
- fixture가 끝나면 다음 fixture는 mode와 무관하게 fresh empty history에서 시작한다.

## Standings와 tie-break

Completed fixture만 standings에 반영한다.

- Series win: 1 point, Series loss: 0 point
- draw 없음
- `BLOCKED`, `IN_PROGRESS`, `COMPLETION_PENDING_VERIFICATION`, `CANCELLED`는 point와 game counter를 만들지 않음
- canonical receipt에서 series wins/losses, game wins/losses와 game differential을 함께 파생

순위는 다음 canonical 순서다.

1. Series wins/points 내림차순
2. game differential 내림차순
3. game wins 내림차순
4. 동률 팀끼리 mini-league Series wins 내림차순
5. 동률 팀끼리 mini-league game differential 내림차순
6. Season seed 기반 deterministic draw order

V1에는 별도 tie-break fixture나 playoff가 없다. 최종 seed draw는 sorted tied team code와 Season root seed를 versioned SHA-256 입력으로 사용하며 실행/DB 순서에 의존하지 않는다. Standings view/receipt에는 적용된 policy ID와 마지막 draw 입력/hash/order를 `tieBreakTrace`로 남긴다. 이 순서는 V1 게임 제품 규칙이며 실제 공식 LCK 규칙이라고 주장하지 않는다.

## BLOCKED와 retry 정책

Fixture가 gameplay/integrity/resource/receipt 검증을 통과하지 못하면 해당 fixture와 Season을 `BLOCKED`로 둔다. 이미 완료된 다른 fixture와 standings는 보존한다.

`BLOCKED`를 해소하려고 다음을 자동 적용하지 않는다.

- 승점, 몰수패, 무승부 또는 임의 winner 부여
- Hard Fearless/side/roster/resource 규칙 완화
- gameplay tuning 변경
- seed 변경 또는 새 seed 재실행

자동 retry는 transport, worker crash, lease loss처럼 결과가 commit되지 않은 transient 운영 실패만 허용한다. 동일 frozen seed/checkpoint로 최초 실행을 포함해 최대 2회다. 두 번째 실패 또는 deterministic product/integrity failure는 `BLOCKED`다.

## Persistence와 restart recovery

League V1은 다음을 relational persistence에 저장한다.

- League, Season, immutable snapshot identities
- Round, Fixture, frozen execution mode와 lifecycle revision
- Job, attempt, lease, heartbeat와 cancellation state
- `LeagueFixtureSeriesBinding`
- League-bound Player Series의 Draft/game progress와 compact receipts
- unified fixture completion receipt, outbox와 standings application ledger
- authoritative standings projection

구현은 repository port/adapter로 domain과 DB 선택을 분리한다. DB 제품, dependency, migration schema와 deployment topology는 persistence batch에서 정한다. 이 문서 작업에서는 dependency나 schema를 추가하지 않는다.

Process restart 뒤에는 lease를 만료/재조정하고, 같은 frozen identity와 checkpoint에서 job을 재개하거나 retry한다. Player Series는 durable binding/progress로 재개한다. 브라우저 pointer는 편의 정보일 뿐 복구 authority가 아니다.

## 운영 한계와 retention

모든 값은 구현 시 dedicated versioned operational configuration 한 곳에서 소유한다.

| 항목 | Frozen V1 값 |
|---|---:|
| production team count | 정확히 10 |
| default schedule | double round robin, 90 fixtures |
| default `maxParallelFixtures` | 2 |
| hard maximum parallel fixtures | 4 |
| job lease | 15분 |
| heartbeat | 15초 |
| transient attempts | 최초 포함 최대 2회 |
| completed Season/canonical receipt | Season 삭제 전까지 보존 |
| job attempt log | 30일 |
| optional gzip full replay cache | 24시간, non-authoritative |

Cancel은 새 dispatch를 즉시 멈추지만 이미 transactional commit에 진입한 completion은 끝날 수 있다. Parallel limit은 성능/운영 설정이며 fixture Random이나 output 의미를 바꿀 수 없다.

## Additive API contract

정확한 DTO schema는 API batch에서 동결한다. 현재 Series/Player Draft/Real Match endpoint의 의미를 변경하지 않고 `/api/v1/leagues` 아래 additive boundary를 둔다.

### Season/fixture view

Season create/view에는 최소 다음을 포함한다.

- `seasonMode`, nullable `managedTeamCode`, snapshot/policy identities
- fixture별 frozen `executionMode`
- current round counters와 standings
- current playable managed fixture
- active/recoverable player-series binding projection
- completion/reconciliation status
- backend-authoritative `allowedCommands`

### Player fixture handoff 예시

```http
POST /api/v1/leagues/{leagueId}/seasons/{seasonId}/fixtures/{fixtureId}/player-series
GET  /api/v1/leagues/{leagueId}/seasons/{seasonId}/fixtures/{fixtureId}/player-series
GET  /api/v1/leagues/{leagueId}/seasons/{seasonId}/fixtures/{fixtureId}/completion-status
```

### Execution command 의미

- current round의 auto fixture 실행
- spectator mode의 round/Season 실행
- player-series start/resume
- pause/cancel과 authoritative refresh

V1 `allowedCommands` closed set은 다음 의미를 포함한다.

- `START_PLAYER_SERIES`
- `RESUME_PLAYER_SERIES`
- `RUN_CURRENT_ROUND_AUTO_FIXTURES`
- `VIEW_STANDINGS`
- `VIEW_FIXTURE`
- `REPLAY_GAME`
- `PAUSE_SEASON`
- `CANCEL_SEASON`

Frontend가 winner, score, standings delta, seed, side 또는 completion receipt를 제출하는 endpoint는 만들지 않는다.

## Frontend handoff

Hybrid manager journey는 다음과 같다.

```text
League dashboard
  -> round / standings / AI 진행 / 내 경기 확인
  -> frozen managed fixture 시작 또는 재개
  -> League-bound Series hub
  -> 기존 Player Draft workspace / BO3 또는 BO5
  -> server completion reconciliation
  -> League dashboard
```

League-bound Series 화면은 상단 context에 Season, round, fixture, standings, 관리 팀/상대, Series score, Hard Fearless exclusions와 League 반영 상태를 표시한다. Standalone setup의 opponent/format/Game 1 side/root seed/profile 선택은 표시하지 않는다.

Frontend는 League/Season/Fixture/Series ID pointer만 보관할 수 있다. reload 뒤 모든 score, history, binding, completion과 command capability는 server view로 재구성한다. Series가 완료됐지만 League 반영이 지연되면 로컬로 standings를 계산하지 않고 `COMPLETION_PENDING_VERIFICATION`을 보여 주며 status endpoint를 조회한다.

## Correctness matrix

이 표는 구현 batch의 최소 focused verification 인계다. Batch 1의 Hybrid mapping, schedule/seed, receipt ledger와 standings/tie 항목은 구현·focused/full regression을 완료했으며 나머지는 후속 batch에서 검증한다.

| 영역 | 필수 시나리오 | Frozen expected result |
|---|---|---|
| Hybrid mapping | 관리 팀 포함/미포함 fixture, spectator mode, null/unknown/snapshot-mismatch managed team | mode와 managed team으로 execution mode가 exact 결정되고 생성 뒤 불변; invalid 조합은 freeze 전 거부 |
| Batch exclusion | `RUN_NEXT_ROUND`/`RUN_ALL`, direct lease, 관리 경기만 남은 round | Player fixture는 queue/lease/AI attempt 0, `WAITING_FOR_PLAYER`는 failure 0, Player 완료 전 Round completion 0 |
| Player binding | start, duplicate same command, payload tamper, reload/resume, cross-fixture/Season request와 receipt | 같은 command는 같은 Series를 반환하고 하나의 durable binding만 존재; team/format/side/root seed/resource 변조와 receipt 재사용 거부 |
| Completion handoff | success, frontend winner/score 제출, cancel/blocked/no-decisive, crash before/after outbox, duplicate delivery, stale revision | verified completed server receipt만 반영; 나머지는 standings 0, canonical receipt 하나와 standings delta 최대 1회 |
| Parity | 같은 fixture input의 AI/Player execution, execution mode 변경, instrumentation/worker 순서 변경 | side/seed/HF/wins/receipt/Production identity parity, mode가 fixture seed를 바꾸지 않고 관측/스케줄링이 gameplay Random을 바꾸지 않음; Draft decision equality는 요구하지 않음 |
| BLOCKED | Hard Fearless/resource/receipt/domain/standings failure | 기존 standings 보존, 점수·winner·reseed·규칙 완화·tuning 없음 |
| Persistence | server restart during auto/player/completion, stale/late worker result | Season/bound Series/Draft/receipt 복구, dangling binding 0, stale lease가 새 revision을 덮지 못하고 duplicate standings 0 |
| Schedule/tie | 10-team DRR와 완전 동률 | 90 fixtures, mirrored leg side, canonical tie order |

## 구현 batch 인계

| 순서 / task | Production surface | Non-goals | Prerequisite | Focused verification | Full regression | 상태 |
|---|---|---|---|---|---|---|
| 1. `AI_VS_AI_LEAGUE_SIMULATION_V1_DOMAIN_SCHEDULE_AND_STANDINGS` | pure domain aggregate, frozen decisions/config, schedule, side, seed, standings/tie | runner, DB, API, UI | 이 계약과 product decisions | 10-team schedule, mode mapping, side/seed, standings/mini-league/tie, duplicate receipt ledger domain tests | 243 suites / 2,297 tests clean | 완료 |
| 2. `AI_VS_AI_LEAGUE_SIMULATION_V1_AUTOMATED_SERIES_RUNNER` | immutable runner input, Auto Draft, Production V9, HF, unified receipt | player handoff, durable jobs | Batch 1 | BO3/BO5, parity, duplicate/ineligible Random, receipt integrity, fixture isolation | 필요 | 미착수 |
| 3. `AI_VS_AI_LEAGUE_SIMULATION_V1_PLAYER_SERIES_HANDOFF` | durable binding port, League-bound Series/Draft completion | public winner/receipt submit, standalone Series migration | Batches 1~2 | start/resume, frozen context, no setup rewrite, outbox handoff, duplicate completion | 필요 | 미착수 |
| 4. `AI_VS_AI_LEAGUE_SIMULATION_V1_PERSISTENCE_AND_JOBS` | relational adapters, lease/heartbeat/retry/outbox/recovery/retention | DB tuning, multi-region | Batches 1~3 | crash boundaries, stale lease, max attempts, cancellation, exactly-once standings | 필요 | 미착수 |
| 5. `AI_VS_AI_LEAGUE_SIMULATION_V1_API` | additive League/Season/Fixture commands/views | existing API rename/removal | Batch 4 | exact schema, auth/ownership 정책 경계, stale revision/idempotency, no authority injection | 필요 | 미착수 |
| 6. `AI_VS_AI_LEAGUE_SIMULATION_V1_FRONTEND` | dashboard, standings, batch progress, frozen player-Series handoff/reconciliation | local score authority, setup reselection | Batch 5 | route/reload/recovery, allowedCommands, accessibility, responsive LIVE journey | frontend build/contract 필요; backend production 미변경이면 backend full 불필요 | 미착수 |
| 7. `AI_VS_AI_LEAGUE_SIMULATION_V1_PRODUCTION_ACCEPTANCE` | end-to-end Hybrid/Spectator, restart, load/retention evidence | gameplay tuning | Batches 1~6 | correctness matrix 전체와 bounded operational acceptance | 최종 production tree에서 필요 | 미착수 |

각 batch는 실제 변경 범위에 맞춰 [Testing Guide](../development/testing.md)와 repository verification 규칙을 따른다. 대규모 Season 통계와 성능 진단은 correctness unit test와 분리한다.

## V1 non-goals와 남은 제한

- 현재 production에는 pure League Season/schedule/standings domain만 있다. Runner, DB, worker, API와 UI는 없다.
- 현재 standalone Series는 계속 process-local이며 이 문서만으로 restart recovery가 생기지 않는다.
- auth/ownership, DB 제품/schema, deployment topology는 후속 batch에서 구현 결정을 내린다.
- custom schedule, side imbalance, playoff/tie-break fixture, managed fixture AI 위임과 Season 도중 관리 팀 변경은 V1 범위 밖이다.
- optional full replay cache는 standings authority가 아니며 resource drift 뒤 재생을 보장하지 않는다.
- 운영 한계는 동결됐지만 실제 load evidence는 Production acceptance 전까지 없다.

Batch 1 상세 결과는 [AI League V1 Domain, Schedule and Standings](../development/ai-vs-ai-league-simulation-v1-domain-schedule-and-standings.md)에 있다. 다음 구현 task는 `AI_VS_AI_LEAGUE_SIMULATION_V1_AUTOMATED_SERIES_RUNNER`다.
