# Series Lifecycle V1 Contract Sketch

상태: `SERIES_LIFECYCLE_V1_CONTRACT_SKETCH_READY`

이 문서는 후속 `SERIES_LIFECYCLE_V1_BACKEND`와 `SERIES_FRONTEND_V1`의 설계 source of truth다. 여기서 `CURRENT`는 기준 커밋 `d2118cb6dae53e5c9301d306df1c8a6582067371`에 실제로 존재하는 동작이고, `V1 CONTRACT`는 아직 구현되지 않은 계약 제안이다. `PRODUCT_DECISION_REQUIRED`는 구현 전에 제품 결정을 승인해야 하는 항목이다.

## 현재 가능한 것과 현재 제한

### CURRENT: 이미 가능한 기반

- [Draft System](draft-system.md)의 `DraftRuleSet.professional()`은 양 팀 각각 5 ban/5 pick, 총 20턴의 `PROFESSIONAL_5_BAN_5_PICK_HARD_FEARLESS_V1`을 제공한다.
- `DraftState`는 한 Draft의 pick/ban과 inherited Hard Fearless exclusions를 immutable value로 보유하고, 현재 Draft 또는 이전 committed picks에 포함된 `ChampionId` 재사용을 거부한다.
- `SeriesDraftHistory`는 caller가 소유하는 series-scoped mutable object다. 완료된 full-auto `FinalDraftResult`의 BLUE/RED picks만 누적하며 동일 `draftIdentity` commit은 idempotent다. Bans는 누적하지 않는다.
- `RealDraftMatchOrchestrator.orchestrateV1(..., SeriesDraftHistory, ...)`는 현재 history에서 game number와 exclusions를 가져와 Draft와 Production Match Engine V9을 실행하고, simulation/output 생성이 성공한 뒤 completed picks를 commit한다.
- `MatchEngineV1Input`은 positive `seriesGameNumber`, canonical Hard Fearless exclusions, `seriesHistoryBeforeHash`, roster, match seed와 authoritative policy를 input/replay identity에 결속한다.
- [Player-controlled Draft API V1](player-controlled-draft-api-v1.md)은 한쪽 `TeamSide`의 10개 선택을 플레이어가 맡고 반대쪽은 Production Auto Draft가 진행하는 process-local session을 제공한다. Team code는 `LckTeamAssembler`의 stable-`PlayerId` roster로 해석되고 completed result의 active Draft Meta identity도 검증된다.
- Player Draft simulation은 전체 output/timeline을 session에 보관하지 않고 compact `SimulationReceipt`만 저장하며, retry는 fresh deterministic execution과 receipt exact equality로 검증한다.
- [Match Engine V1](match-engine-v1.md)의 현재 authoritative runtime은 `PRODUCTION_MATCHUP_COMPOSITION_V1` / `MATCH_SIMULATOR_ENGINE_IMPLEMENTATION_V9`이다. Champion Matchup과 Team Composition은 ON, Jungle Economy/Tempo는 OFF다. Public caller는 profile이나 candidate flag를 선택할 수 없다.

### CURRENT: 아직 없는 것

- 공개 Player Draft는 `seriesGameNumber == 1`, 빈 Hard Fearless history만 허용한다. `PlayerControlledDraftEngine.start`, mixed result validator/input projector와 output validator 모두 이 제한을 명시적으로 갖는다.
- `SeriesDraftHistory`는 score, 참가 team identity, side mapping, winner, active game, child Draft session, revision, command idempotency와 lifecycle status를 소유하지 않는다. `draftIdentity` 중복 방지만 있으므로 `(seriesId, gameNumber)` 단위 commit authority를 대신할 수 없다.
- BO3/BO5 aggregate, series repository, REST endpoint, frontend, DB/Save/Load, restart recovery, auth와 multi-node coordination은 없다.
- [Real Match API V1](real-match-api-v1.md)은 요청마다 fresh history를 만드는 독립 Game 1 계약이다. Caller-owned history overload는 Java application 기반일 뿐 공개 series API가 아니다.
- Safety timeout은 정상 winner 없이 종료될 수 있다. 이를 series 무승부나 score로 처리하는 제품 규칙은 없다.

## 목표와 non-goals

### 목표

`V1 CONTRACT`는 BO3/BO5 series의 team/side/game/score/history/child Draft/result commit을 backend authoritative aggregate가 소유하도록 고정한다. Frontend는 backend view와 `allowedCommands`를 표시·전달할 뿐 score, 다음 game, side, winner 또는 Hard Fearless history를 계산하지 않는다.

Series lifecycle은 다음을 보장해야 한다.

1. 한 series의 mutable state는 하나의 series-scoped aggregate/repository가 소유한다.
2. 성공적으로 검증된 completed Draft와 decisive Production V9 result만 score/history에 정확히 한 번 반영한다.
3. 실패, 취소, expiry, stale revision, duplicate command, invalid output과 no-result는 score/history를 변경하지 않는다.
4. team/player/champion/side identity는 team code, stable `PlayerId`, `ChampionId`, `TeamSide`로만 표현한다.
5. lifecycle orchestration과 diagnostics는 gameplay Random을 소비하지 않는다.
6. 기존 standalone Player Draft 다섯 endpoint와 Real Match API는 byte/semantic compatibility를 유지한다.

### Non-goals

이 sketch는 Java/REST/frontend/DB 구현, 실제 BO3/BO5 실행, gameplay/Draft tuning, runtime profile 변경, large-seed balance audit와 generated artifact를 포함하지 않는다. 설계 문서가 준비됐다는 사실은 현재 플레이어가 BO3/BO5를 실행할 수 있다는 뜻이 아니다.

## Aggregate ownership

### Series aggregate

`V1 CONTRACT`의 `SeriesAggregate`는 다음 immutable snapshot을 소유한다. Repository mutation은 새 snapshot을 원자적으로 교체한다.

| 필드 | 계약 |
| --- | --- |
| `schemaVersion` | `SERIES_AGGREGATE_V1` exact |
| `seriesId` | server가 발급하고 canonicalize한 stable non-display identity |
| `revision` | 0부터 시작하는 monotonic non-negative integer |
| `format` | `BO3` 또는 `BO5` |
| `winsRequired` | BO3=2, BO5=3; client 값이 아니라 format에서 파생 |
| `teamCodes` | 정확히 두 개의 서로 다른 authoritative team code |
| `managedTeamCode` | 두 참가 team 중 플레이어가 제어하는 stable team code |
| `createdAt`, `lastActivityAt`, `expiresAt` | 운영 수명 정보이며 gameplay/hash identity에서 제외 |
| `rootSeriesSeed` | canonical signed-long string으로 보존되는 immutable root seed |
| `seedDerivationAlgorithm` | versioned algorithm ID |
| `currentGameNumber` | 1부터 시작하며 committed games + 1 또는 completed series의 마지막 game |
| `scoreByTeamCode` | 참가 team code 두 개만 key로 갖는 immutable map |
| `games` | game number 오름차순의 contiguous immutable records |
| `cumulativeHardFearlessExclusions` | canonical `ChampionId` 순서의 committed picks union |
| `hardFearlessHistoryHash` | committed count와 exact exclusions의 canonical hash |
| `activeChildDraftSessionId` | active game에 최대 하나; 없으면 null |
| `allowedCommands` | 현재 상태/revision에서 backend가 승인한 enum set |
| `status` | `ACTIVE`, `BLOCKED`, `COMPLETED`, `CANCELLED`, `EXPIRED` |
| `winnerTeamCode` | `COMPLETED`일 때만 참가 team 중 하나 |
| `terminalReason` | blocked/cancelled/expired reason enum; 일반 message가 아님 |
| `productionIdentity` | policy/profile/configuration/rules/engine 및 Draft resource identity |

Score는 `blueScore`/`redScore`로 저장하지 않는다. BLUE/RED는 game-scoped mapping이고 series score는 `{teamCode -> wins}`다. View가 표시 편의를 위해 current BLUE/RED score를 투영할 수는 있지만 authoritative 저장/판정은 team code 기준이다.

### Series Game record

각 `SeriesGame`은 다음 identity와 compact evidence를 갖는다.

| 필드 | 계약 |
| --- | --- |
| `schemaVersion` | `SERIES_GAME_V1` |
| `seriesId`, `seriesGameNumber` | parent와 exact, 1부터 contiguous |
| `gameId` | `(seriesId, seriesGameNumber)` canonical identity에서 파생된 stable ID |
| `blueTeamCode`, `redTeamCode` | 서로 다른 두 참가 team; game 시작 뒤 immutable |
| `controlledSide` | `managedTeamCode`가 현재 mapping에서 차지한 `TeamSide` |
| `matchSeed` | root seed와 frozen game context에서 versioned derivation한 signed long |
| `seriesHistoryBeforeHash` | Draft 시작 전 committed history exact hash |
| `hardFearlessExclusionsBeforeDraft` | `ChampionId` canonical ordered snapshot |
| `childDraftSessionId` | 해당 game 전용 parent-bound Player Draft session ID |
| `completedDraftSnapshot` | 20턴 authority/evidence, final assignments와 resource identity의 compact immutable snapshot 또는 그 repository reference |
| `draftDecisionHash`, `controlEvidenceHash`, `finalDraftHash` | completed Draft identity |
| `inputHash` | authoritative Match Engine V1 input identity |
| `replayProvenanceHash`, `resourceProvenanceHash` | execution provenance |
| `simulatorTimelineHash`, `structuredTimelineHash` | timeline identity; timeline body는 저장하지 않음 |
| `randomFingerprint` | schema/draw count/trace hash/algorithm |
| `outputHash` | mandatory output envelope identity |
| `resultSummary` | winner team code/side, end reason, duration과 compact team/player result projection |
| `gameReceipt` | commit key/command/payload와 위 identity를 묶는 canonical compact receipt |
| `status` | game lifecycle enum |
| `failureReason` | retryable/blocked failure enum과 sanitized metadata |

`SeriesAggregate`는 20~34MB의 full `MatchEngineV1Output`, event/snapshot timeline, decoded HTTP DTO graph나 simulator mutable state를 보관하지 않는다. 20-turn completed Draft snapshot과 compact result/receipt는 보존할 수 있다. `SeriesGameReceipt`는 기존 `SimulationReceipt` identity를 중복 해석하지 않고 series/game/commit binding을 추가하며 canonical form을 기존 16KiB receipt 원칙 안에서 bounded하게 유지한다.

### Series-owned Player Draft session

Standalone Game 1 session과 Series child session은 같은 Draft domain/search/selector와 response projection을 재사용할 수 있지만 lifecycle authority는 분리한다.

- 기존 `/api/v1/player-drafts/sessions` 다섯 endpoint는 standalone Game 1/빈 history 의미를 그대로 유지한다.
- Series child는 `SERIES_PLAYER_DRAFT_BINDING_V1`을 갖고 `seriesId`, game number/ID, BLUE/RED team code, controlled side, derived seed, history hash/exclusions와 parent revision에 결속된다.
- Series child는 standalone start endpoint에서 만들거나 standalone simulate endpoint로 실행하지 않는다. Series-scoped service만 생성·action·cancel·simulate를 조정한다.
- Child session ID를 다른 series/game에 제출하면 stable cross-context error로 거부한다.
- Managed team이 현재 game에서 RED이면 `controlledSide=RED`이고, RED의 10개 turn만 PLAYER authority가 된다. 상대 BLUE turn은 기존 Production Auto Draft다.
- Series integration은 기존 Game 1 validator를 우회하거나 Game 2를 Game 1처럼 표시하지 않는다. 후속 backend batch에서 game number/history를 받는 additive series-aware start/validation/input boundary를 추가하고 standalone entry point는 그대로 둔다.
- Child Draft가 20턴 완료되고 active resource/team/seed/side/history evidence가 재검증된 경우에만 simulate-and-commit 경계에 들어간다.

V1 권장안은 기존 endpoint를 파괴적으로 확장하는 대신 새로운 `/api/v1/series/...` endpoint가 child session을 소유하는 additive 방식이다. 기존 Player Draft UI component는 series envelope 안에서도 재사용하되 network authority는 Series API에 둔다.

## Canonical identities

Display name, nickname, array index, event message/description과 frontend formatting은 다음 identity의 입력이 아니다.

| 의미 | Canonical identity |
| --- | --- |
| Series | server-issued `seriesId` |
| 참가 팀 | normalized authoritative team code |
| Game | `seriesId + seriesGameNumber` |
| Side mapping | `BLUE -> teamCode`, `RED -> teamCode` structured pair |
| 선수 | stable `PlayerId` |
| 챔피언 | `ChampionId` |
| Draft session | child session ID + exact parent binding |
| Command | `(seriesId, gameNumber, commandType, clientCommandId)` |
| Commit | `(seriesId, gameNumber)` + completed Draft/input/output receipt identity |
| History | committed game count + sorted consumed `ChampionId` lines |
| Runtime | code-owned policy/profile/configuration/rules/engine/resource hashes |

Canonical lists는 `ChampionId.value()` 오름차순, games는 game number 오름차순, score map은 participant team code 오름차순으로 직렬화한다. Sets/maps의 런타임 iteration order를 hash 또는 seed의 암묵적 입력으로 사용하지 않는다.

## BO3/BO5 rules

| Format | `winsRequired` | 최대 committed games | 완료 조건 |
| --- | ---: | ---: | --- |
| `BO3` | 2 | 3 | 어느 team code든 2승에 먼저 도달 |
| `BO5` | 3 | 5 | 어느 team code든 3승에 먼저 도달 |

Canonical invariants는 다음과 같다.

- Decisive game commit 직후 필요한 승수에 도달하면 같은 atomic mutation에서 series를 `COMPLETED`로 만든다.
- 완료 뒤 Draft create/action/simulate/commit과 다음 game 생성은 모두 거부한다.
- `sum(scoreByTeamCode.values()) == committedGameCount`다.
- Winner는 두 참가 team code 중 하나이며 Match Engine `TeamSide winner`를 해당 game side mapping으로 변환해 결정한다.
- BO3 3게임, BO5 5게임을 초과할 수 없다. Max game에서 필요한 승수가 없는 상태는 정상적으로 생성될 수 없는 integrity failure다.
- Timeout/no-winner output은 committed game 수나 score에 포함하지 않는다.

## State machine 및 transition table

### 상태 enum 후보

Series와 Game/Draft 상태를 한 enum으로 합치지 않는다.

- `SeriesStatus`: `ACTIVE`, `BLOCKED`, `COMPLETED`, `CANCELLED`, `EXPIRED`
- `SeriesGameStatus`: `DRAFT_PENDING`, `DRAFT_ACTIVE`, `DRAFT_COMPLETED`, `SIMULATION_IN_PROGRESS`, `SIMULATION_FAILED_RETRYABLE`, `BLOCKED`, `COMMITTED`, `DRAFT_CANCELLED`, `DRAFT_EXPIRED`
- 기존 child `PlayerDraftSessionStatus`: `ACTIVE`, `COMPLETED`, `SIMULATED`, `CANCELLED`, `EXPIRED`를 standalone과 동일 의미로 유지하되 parent binding을 추가한다.

`SeriesAllowedCommand` 후보는 `CREATE_DRAFT_SESSION`, `SUBMIT_DRAFT_ACTION`, `CANCEL_DRAFT_SESSION`, `SIMULATE_GAME`, `REPLAY_GAME`, `CANCEL_SERIES`다. Backend가 현재 Series/Game/child/reservation 상태에서 계산하며 client가 임의 제출하지 않는다.

### 정상 흐름

```text
Series 생성
  → Game N frozen context(side/seed/history) 준비
  → Series-owned child Draft 생성
  → PLAYER/AI 20-turn Draft
  → completed Draft 재검증
  → simulation reservation
  → Production V9 execution
  → output/provenance/hash 검증
  → Game result + score + Hard Fearless history atomic commit
  → next Game frozen context 준비 또는 Series 완료
```

### Transition table

| 현재 Series / Game | Command 또는 event | Eligibility | 다음 상태와 mutation |
| --- | --- | --- | --- |
| 없음 | `CREATE_SERIES` | BO3/BO5, 서로 다른 known teams, managed team membership, canonical root seed | `ACTIVE / DRAFT_PENDING`, revision 0, score 0:0, Game 1 frozen context |
| `ACTIVE / DRAFT_PENDING` | `CREATE_DRAFT_SESSION` | expected revision, no active child, Hard Fearless preflight pass | `ACTIVE / DRAFT_ACTIVE`, child binding 저장, revision +1 |
| `ACTIVE / DRAFT_ACTIVE` | `SUBMIT_DRAFT_ACTION` | parent/child binding, both revisions, current legal PLAYER turn | child progress와 parent activity/revision 갱신; score/history 불변 |
| `ACTIVE / DRAFT_ACTIVE` | child completes | 20 turns + final assignment/resource evidence | `ACTIVE / DRAFT_COMPLETED` |
| `ACTIVE / DRAFT_COMPLETED` | `SIMULATE_GAME` reserve | exact revisions/command/payload, no reservation | `ACTIVE / SIMULATION_IN_PROGRESS`; reservation token 저장 |
| `ACTIVE / SIMULATION_IN_PROGRESS` | valid decisive output | reservation still current, all identity checks pass | Result/receipt, score와 picks history를 준비하고 같은 atomic commit에서 아래 세 branch 중 하나 선택 |
| decisive commit branch | required wins reached | score exact | `COMPLETED / COMMITTED`, winnerTeamCode 고정, allowed mutation commands 없음 |
| decisive commit branch | series continues + next pool feasible | below required wins/max games | Current game `COMMITTED`; 새 contiguous `ACTIVE / DRAFT_PENDING` game context를 same commit에 준비 |
| decisive commit branch | next pool infeasible | successful current game은 commit 가능 | Current game `COMMITTED`, next game과 Series `BLOCKED`, reason `HARD_FEARLESS_LEGAL_POOL_EXHAUSTED` |
| `DRAFT_ACTIVE` | `CANCEL_DRAFT_SESSION` | exact binding/revision | game `DRAFT_CANCELLED`, child invalidated, score/history 불변; 같은 frozen game context에서 새 child 생성 가능 |
| `DRAFT_ACTIVE` | child TTL expires | parent live | game `DRAFT_EXPIRED`, score/history 불변; 새 child 생성 가능 |
| non-terminal | `CANCEL_SERIES` | expected revision/idempotency | Series `CANCELLED`, child/reservation invalidated, late output commit 거부 |
| non-terminal | parent TTL expires | no protected live execution lease | Series `EXPIRED`, child invalidated, score/history frozen |
| `ACTIVE / SIMULATION_IN_PROGRESS` | execution/runtime failure | current reservation token | `ACTIVE / SIMULATION_FAILED_RETRYABLE`, reservation 해제, failure receipt, score/history 불변 |
| `ACTIVE / SIMULATION_IN_PROGRESS` | output integrity failure | current reservation token | `BLOCKED / BLOCKED`, fail-closed, score/history 불변 |
| `ACTIVE / SIMULATION_IN_PROGRESS` | valid no-winner timeout | winner null/end reason timeout | `BLOCKED / BLOCKED` + `NO_DECISIVE_RESULT`, 무승부 score를 만들지 않음 |

Stale revision, wrong game/side/history, command payload conflict와 cross-series child는 현재 snapshot을 그대로 반환하거나 structured error로 실패하며 mutation은 0이다.

Process restart 시 V1 process-local repository와 in-flight reservation은 사라진다. 외부 score/history commit이 없으므로 부분 commit은 남지 않지만 기존 `seriesId`는 복구할 수 없고 이후 조회는 `SERIES_NOT_FOUND`다. 이를 DB 복구처럼 표현하지 않는다.

## Side policy 대안과 권장안

현재 repository에는 BO3/BO5 side-selection product rule이 없다. 다음 비교는 설계 대안이며 기존 규칙 설명이 아니다.

| 대안 | UX | 재현성/API | AI 대 AI·Season 확장 | 악용/retry 위험 |
| --- | --- | --- | --- | --- |
| Game 1 mapping 고정 후 매 game 교대 | 단순하고 예측 가능; 양쪽 side 경험 | 생성 시 mapping 하나만 고정하면 deterministic | AI 대 AI에도 그대로 적용; schedule가 Game 1 mapping 제공 가능 | game 시작 뒤 변경 지점이 없어 가장 낮음 |
| 이전 game 패자가 다음 side 선택 | 실제 대회형 선택 UX 가능 | 선택 command/timeout/default, 추가 revision과 대기 상태 필요 | AI 선택 정책과 schedule 연동 추가 필요 | duplicate 선택, 지연, winner 확정 전 요청과 retry 충돌 가능 |
| schedule/career가 game별 mapping 전체 제공 | Season 재현성과 운영 통제가 가장 강함 | direct-play API가 schedule dependency 또는 전체 mapping 입력 필요 | 장기적으로 가장 자연스러움 | client-authored schedule을 신뢰하면 위조 가능; server schedule authority 필요 |

`V1 RECOMMENDATION`: 생성 시 server가 검증·고정한 Game 1 BLUE/RED mapping을 기준으로 game마다 단순 교대한다. Direct series request는 두 참가 team과 `game1BlueTeamCode`를 제출할 수 있지만 server가 membership을 확인한 뒤 aggregate가 authority를 소유한다. 향후 Schedule/Career는 같은 필드를 server-owned schedule에서 제공한다.

이 정책은 기존 production에 고정된 사실이 아니므로 구현 전에 `PRODUCT_DECISION_REQUIRED: SERIES_V1_SIDE_POLICY` 승인이 필요하다. 승인과 무관하게 다음은 고정한다.

- Team identity와 `TeamSide`는 분리한다.
- `managedTeamCode`는 series 전체에서 안정적이다.
- Game별 mapping은 server authoritative이며 child Draft 생성 전 고정된다.
- Game 시작 뒤 mapping은 변경할 수 없다.
- Mapping은 game ID, seed derivation, Draft selection context, Match Engine input/replay provenance와 child binding에 포함된다.

## Seed contract 대안과 권장안

| 대안 | 장점 | 문제 |
| --- | --- | --- |
| Client가 game마다 seed 제출 | 사용자가 각 game을 직접 제어 | retry/next-game race에서 다른 seed 제출 가능; aggregate authority 약화 |
| Server가 game마다 임의 생성 | client가 조작하기 어려움 | restart/save/replay에 별도 persisted entropy 필요; 동일 command 재현성 설명이 복잡 |
| Root series seed에서 versioned derivation | 한 입력만 보존, retry/save/restore와 game 분리가 명확 | algorithm과 canonical fields를 계약으로 동결해야 함 |

`V1 RECOMMENDATION`은 root seed deterministic derivation이다.

### Root seed

- Series create request는 기존 Real/Player Draft와 같은 canonical signed 64-bit decimal JSON string `rootSeed`를 받는다. `+73`, `073`, `-0`, whitespace, number token과 범위 초과는 거부한다.
- Parser가 Java signed `long`으로 검증한 뒤 canonical decimal string과 long 값을 aggregate에 immutable하게 보존한다.
- 생성 이후 client는 game별 seed를 제출하거나 변경할 수 없다.

### Game seed derivation

- Algorithm ID 후보: `SERIES_GAME_SEED_SHA256_FIRST_8_BYTES_BIG_ENDIAN_SIGNED_LONG_V1`
- Domain: `LOL_MANAGER_SERIES_GAME_DRAFT_AND_MATCH_V1`
- Canonical UTF-8 input은 다음 line order와 trailing newline을 사용한다.

```text
seedSchema=SERIES_GAME_SEED_V1
domain=LOL_MANAGER_SERIES_GAME_DRAFT_AND_MATCH_V1
seriesId=<canonical seriesId>
rootSeed=<canonical signed decimal>
seriesGameNumber=<positive decimal>
blueTeamCode=<authoritative code>
redTeamCode=<authoritative code>
managedTeamCode=<authoritative code>
seriesHistoryBeforeHash=<lowercase SHA-256>
```

SHA-256 digest의 첫 8 bytes를 big-endian 64-bit bit pattern으로 읽고 Java two's-complement signed `long`으로 재해석한다. 별도 modulo/absolute-value를 적용하지 않는다. Side mapping은 Draft selection과 player-controlled side를 바꾸는 gameplay context이므로 seed identity에 포함한다. History hash도 같은 game number를 다른 consumed pool과 재사용하지 못하게 포함한다.

같은 series/game retry는 exact seed를 얻고, 다른 game number 또는 mapping/history는 다른 derivation input을 갖는다. 하나의 frozen `matchSeed`를 `DraftSelectionContext`와 `MatchEngineV1Input`에 모두 전달한다. Lifecycle은 SHA-256 derivation 외 gameplay `Random`을 만들거나 소비하지 않는다.

Root seed를 사용자에게 노출할지 server-generated로 숨길지는 UX 제품 결정이지만, V1 backend contract는 한 번 정한 root seed와 위 derivation이 immutable하다는 점을 바꾸지 않는다.

## Hard Fearless commit semantics

### CURRENT semantics에서 그대로 유지할 것

- Game N exclusions는 성공적으로 committed된 Games 1..N-1의 BLUE/RED picks 합집합이다.
- Bans는 consumed picks에 추가하지 않는다.
- 한 team이 pick한 champion도 이후 양 팀 모두 pick할 수 없다.
- Draft state는 inherited exclusions와 current pick/ban의 중복을 `ChampionId`로 거부한다.
- Current hash schema는 committed game count와 sorted consumed picks를 결속한다.

### V1 aggregate commit 계약

1. Child Draft 생성 전에 aggregate의 committed games에서 expected exclusions와 history hash를 다시 계산한다.
2. Child binding, Draft state exclusions, selection context, completed result, Match Engine input의 ordered exclusions/hash가 모두 exact인지 확인한다.
3. Decisive valid output 뒤 game의 정확히 10 picks를 기존 history에 합친다.
4. `(seriesId, gameNumber)` commit key가 이미 있으면 기존 `GameReceipt` exact replay만 반환하고 picks를 다시 추가하지 않는다.
5. 같은 key에 다른 Draft/input/output/command payload가 오면 fail-closed한다.
6. Aborted/cancelled/expired Draft, simulation failure, invalid/no-winner output은 history를 변경하지 않는다.
7. History는 monotonic하다. 이전 champion 제거, arbitrary client exclusions 제출, 다른 series history/receipt 재사용을 거부한다.
8. `committedGameCount == committed game records size`이고, non-terminal series의 `currentGameNumber == committedGameCount + 1`이다.
9. 각 committed game의 BLUE/RED picks union이 그 commit의 history 증가분과 exact해야 한다.

Snapshot/API는 `ChampionId.value()`로 정렬한 exclusions와 hash를 함께 제공한다. Frontend가 이전 Draft 화면이나 champion display text에서 history를 복원하지 않는다.

### Legal pool exhaustion

BO5 후반에는 cumulative picks 때문에 현재 champion/role pool로 20턴 legal Draft를 완성하지 못할 수 있다. V1은 Hard Fearless를 조용히 완화하거나 champion을 재사용하지 않는다.

- 다음 child Draft를 열기 전에 server가 현재 active champion/legal-role resource와 exact history로 양 팀 5-role completion feasibility를 preflight한다.
- 불가능하면 next game을 `BLOCKED`, series를 `BLOCKED`, reason을 `HARD_FEARLESS_LEGAL_POOL_EXHAUSTED`로 기록한다.
- 성공적으로 끝난 직전 game의 score/history commit은 보존한다.
- 허용 command는 조회와 series 취소만 남긴다. Resource/rule migration이나 rematch는 V1 자동 동작이 아니다.

## Simulate-and-commit 경계

Client는 winner, score, history, arbitrary `MatchEngineV1Output` 또는 profile을 제출하지 않는다. Series service가 child Draft부터 game result commit까지 소유한다.

```text
Series service
  → parent-bound completed child Draft 재검증
  → team code로 authoritative roster 조립
  → frozen game number/side/seed/history로 MatchEngineV1Input 생성
  → authoritative PRODUCTION_MATCHUP_COMPOSITION_V1 / V9 실행
  → output/provenance/hash/decisive winner 검증
  → compact SeriesGameReceipt 생성
  → game/score/history/next-state atomic commit
```

### Mutation order

1. Series/game/child Draft 상태, expected revisions, command ID/payload, side/seed/history와 policy eligibility를 모두 확인한다.
2. Per-series atomic boundary에서 `SimulationReservation(seriesId, gameNumber, commandId, payloadHash, reservationToken)`을 기록한다.
3. Aggregate lock을 놓고 long-running Match Engine을 실행한다. 실행 동안 aggregate lock을 유지하지 않는다.
4. Output의 policy/configuration/rules/engine, team/side/seed/game/history, Draft/control/input/resource/replay/timeline/Random/output identity를 검증한다.
5. Per-series boundary를 다시 열고 reservation token, series status/revision과 game identity가 여전히 유효한지 compare한다.
6. Game result/receipt, team-code score, Hard Fearless history, 다음 game context 또는 final winner를 하나의 atomic snapshot으로 commit한다.
7. Commit된 view/response를 반환한다.

Simulation/runtime failure 시 현재 reservation만 안전하게 해제하고 game을 `SIMULATION_FAILED_RETRYABLE`로 만든다. Score/history/committed games는 그대로다. Retry는 새 `clientCommandId`와 최신 revision을 사용하지만 frozen Draft/side/seed/history/input은 바뀌지 않는다.

### Idempotency와 concurrency

- `expectedRevision`과 `clientCommandId`는 create를 제외한 모든 mutating Series command에 필수다. Draft action은 `expectedSeriesRevision`과 `expectedDraftRevision`을 모두 사용한다.
- Command payload는 canonical hash로 receipt에 저장한다. 같은 ID/같은 payload replay는 최초 성공·실패·in-progress 의미를 돌려주고 mutation/Random 소비는 0이다.
- 같은 ID를 다른 payload에 사용하면 `SERIES_COMMAND_ID_PAYLOAD_CONFLICT`다.
- 동시에 두 simulation command가 오면 첫 valid reservation만 실행한다. 동일 command replay는 `202 Accepted`와 현재 reservation view, 다른 command는 `SERIES_SIMULATION_ALREADY_IN_PROGRESS`다.
- 동시에 두 next-game Draft create가 오면 첫 command만 child를 만든다. 동일 command는 같은 child를 반환하고 다른 command는 `SERIES_ACTIVE_DRAFT_SESSION_EXISTS`다.
- Completed/cancelled/expired series 또는 current game이 아닌 late result는 commit할 수 없다.
- Receipt mismatch는 기존 commit을 덮어쓰지 않고 fail-closed한다.

현재 safety timeout처럼 winner가 null인 valid output은 무승부 score로 바꾸지 않는다. Frozen input의 deterministic retry는 같은 no-result를 만들므로 game/series를 `BLOCKED`와 `NO_DECISIVE_RESULT`로 두는 것이 V1 권장안이다. Re-seed/rematch는 game identity를 바꾸는 별도 future contract다.

### Full output 비보관과 결과 재조회

- `GET game`과 `GET series`는 stored compact result summary/receipt를 반환하므로 score/result 화면 재조회에는 re-execution이 필요 없다.
- 최초 simulate 응답은 현재 request 동안 존재하는 full structured match response를 반환할 수 있지만 aggregate에는 저장하지 않는다.
- Full timeline playback을 다시 요청하는 canonical route는 stored completed Draft snapshot과 frozen context로 Production V9을 deterministic re-execute하고 stored receipt와 exact 비교한 뒤 output을 반환한다. 이 replay는 score/history/series revision을 절대 commit하지 않는다.
- Current resource/policy identity가 stored receipt와 달라 exact replay가 불가능하면 historical output을 현재 runtime으로 가장하지 않고 `SERIES_GAME_REPLAY_IDENTITY_UNAVAILABLE`로 fail-closed한다.

## API endpoint/request/response sketch

Canonical base path는 `/api/v1/series`다. 기존 `/api/v1/player-drafts/sessions`와 `/api/v1/real-matches`는 제거·rename·확장하지 않는다. Series child action도 standalone endpoint를 우회해 series-scoped route로 보낸다.

| Method/path | 목적 | 주요 request fields | 주요 response fields | Revision / idempotency | 정상 status | 대표 오류 / transition | Standalone API 관계 |
| --- | --- | --- | --- | --- | ---: | --- | --- |
| `POST /api/v1/series` | Series 생성 | schema, `format`, 두 team code, managed team, root seed, Game 1 BLUE team, `clientCommandId` | complete `SeriesView` | create command ID; expected revision 없음 | 201, exact replay 200 | unsupported format/team/seed; → `ACTIVE/DRAFT_PENDING` | 기존 Player Draft/Real Match 생성 계약과 독립 |
| `GET /api/v1/series/{seriesId}` | Authoritative current view | path ID | `SeriesView` | 없음/read-only | 200 | not found/expired | 기존 endpoint/schema 변경 없음 |
| `POST /api/v1/series/{seriesId}/games/current/draft-session` | Current game child 생성/기존 exact child 반환 | schema, expected series revision, command ID | SeriesView + child `PlayerDraftSessionView` | 둘 다 필수 | 201, replay 200 | pool exhausted/active child/stale; → `DRAFT_ACTIVE` | Draft domain/projection은 재사용하지만 standalone start는 호출하지 않음 |
| `GET /api/v1/series/{seriesId}/games/{gameNumber}/draft-session` | Child 조회 | path IDs | parent binding + child view | read-only | 200 | wrong game/not found/expired | Standalone GET으로 series child를 조회하지 않음 |
| `POST /api/v1/series/{seriesId}/games/{gameNumber}/draft-session/actions` | Player ban/pick 제출 | schema, expected series revision, expected Draft revision, command ID, `ChampionId` | SeriesView + updated child view | 모두 필수 | 200 | wrong side/stale/illegal action; child progress | 기존 action semantics/component를 재사용하되 parent binding 추가 |
| `DELETE /api/v1/series/{seriesId}/games/{gameNumber}/draft-session` | 진행 중 child만 취소 | expected series revision, command ID | 없음 또는 updated SeriesView | 필수 | 204 | completed/committed/wrong child; → `DRAFT_CANCELLED` | Standalone cancel과 별도 parent-owned cleanup |
| `POST /api/v1/series/{seriesId}/games/{gameNumber}/simulate` | Completed child를 Production V9으로 실행하고 atomic commit | schema, expected series revision, expected Draft revision, command ID; winner/score/profile/history 없음 | full current match response + committed SeriesView/GameView | 필수 | 200; same in-progress replay 202 | Draft incomplete/in-progress/integrity/no-result; → commit/blocked | 기존 Production V9 의미를 재사용하지만 standalone simulate는 score/history를 commit하지 않으므로 호출하지 않음 |
| `GET /api/v1/series/{seriesId}/games/{gameNumber}` | Compact committed/active game 조회 | path IDs | `SeriesGameView`, compact summary/receipt | read-only | 200 | wrong/not found game | 신규 series projection; Real Match response 변경 없음 |
| `POST /api/v1/series/{seriesId}/games/{gameNumber}/replay` | Full timeline deterministic 재생성, no commit | schema, command ID 또는 request correlation; no gameplay fields | full match response + stored receipt equality | Series revision은 변경하지 않음 | 200 | not committed/resource drift/receipt mismatch | Match output projection 재사용 가능; standalone session 상태는 변경하지 않음 |
| `DELETE /api/v1/series/{seriesId}` | Series 취소 | expected revision, command ID | 없음 | 필수 | 204 | completed/cancelled/expired/stale; → `CANCELLED` | Series child만 정리하며 독립 standalone session에는 영향 없음 |

모든 mutating request는 exact schema와 unknown-field rejection을 사용한다. Runtime profile, candidate flag, client-authored score/winner/history/exclusions/side-after-start와 arbitrary result/output field는 허용하지 않는다.

Series response는 기존 standalone DTO를 다른 의미로 재라벨링하지 않는다. Child Draft 부분은 기존 presentation shape를 reuse할 수 있지만 `seriesBinding`이 additive envelope에 명시되고 standalone schema/version은 그대로다.

## Error/status matrix

Human-readable `message`는 표시용이다. Client와 backend transition은 stable `code`, HTTP status, structured `field`, `retryable`, current revision/status로 판단한다.

| HTTP | Stable code | 의미 / mutation |
| ---: | --- | --- |
| 400 | `SERIES_UNSUPPORTED_FORMAT` | BO3/BO5 외 값; 생성 없음 |
| 400 | `SERIES_UNKNOWN_TEAM` | assembler authority에 없는 team code; 생성 없음 |
| 400 | `SERIES_SAME_TEAM_NOT_ALLOWED` | 두 team identity 동일; 생성 없음 |
| 400 | `SERIES_INVALID_MANAGED_TEAM` | managed team이 참가 team이 아님 |
| 400 | `SERIES_INVALID_ROOT_SEED` | canonical signed-long 위반 |
| 404 | `SERIES_NOT_FOUND` | series 없음 또는 restart loss |
| 404 | `SERIES_GAME_NOT_FOUND` | game number가 current/committed records에 없음 |
| 409 | `SERIES_STALE_REVISION` | expected/current mismatch; mutation 0 |
| 409 | `SERIES_COMMAND_ID_PAYLOAD_CONFLICT` | 동일 command ID의 payload 불일치; mutation 0 |
| 409 | `SERIES_WRONG_GAME_NUMBER` | current가 아닌 Draft/simulate command |
| 409 | `SERIES_WRONG_SIDE_CONTEXT` | managed team/controlled side/mapping mismatch |
| 409 | `SERIES_CROSS_CONTEXT_DRAFT_SESSION` | 다른 series/game child 재사용 |
| 409 | `SERIES_DRAFT_NOT_COMPLETE` | 20턴 미완료; simulation 없음 |
| 410 | `SERIES_DRAFT_SESSION_EXPIRED` | child expiry; score/history 불변, recreate 가능 |
| 410 | `SERIES_EXPIRED` | parent expired; terminal |
| 409 | `SERIES_HARD_FEARLESS_HISTORY_MISMATCH` | actual ordered exclusions/hash가 aggregate와 다름; fail-closed |
| 422 | `SERIES_HARD_FEARLESS_POOL_EXHAUSTED` | 다음 legal Draft 불가; series/game `BLOCKED` |
| 409 | `SERIES_SIMULATION_ALREADY_IN_PROGRESS` | 다른 command reservation 존재; mutation 0 |
| 500 | `SERIES_ENGINE_OUTPUT_INTEGRITY_FAILED` | output/provenance/hash mismatch; commit 0, fail-closed |
| 500 | `SERIES_GAME_RECEIPT_MISMATCH` | stored/new receipt mismatch; 기존 commit 보존 |
| 422 | `SERIES_GAME_NO_DECISIVE_RESULT` | winner 없는 valid timeout; score/history 0, blocked |
| 409 | `SERIES_GAME_ALREADY_COMMITTED` | 다른 payload/command의 중복 commit; 기존 receipt 보존 |
| 409 | `SERIES_ALREADY_COMPLETED` | winner 확정 뒤 mutation 거부 |
| 409 | `SERIES_CANCELLED` | cancelled series mutation 거부 |
| 409 | `SERIES_BLOCKED` | blocked reason 해소 전 mutation 거부 |
| 500 | `SERIES_SIMULATION_FAILED` | transient/internal execution failure; retryable 여부 명시, score/history 0 |
| 409 | `SERIES_GAME_REPLAY_IDENTITY_UNAVAILABLE` | current runtime/resource로 exact replay 불가 |

Output/history/receipt mismatch는 client가 고칠 validation error로 축소하지 않는다. Server integrity failure로 fail-closed하고 internal detail/path/stack trace를 응답에 노출하지 않는다.

## Frontend handoff view

`SeriesView`는 다음 structured fields를 최소 제공한다.

- `schemaVersion`, `seriesId`, `revision`, `status`, `terminalReason`
- `format`, `winsRequired`
- `managedTeamCode`, `opponentTeamCode`와 별도 display metadata
- team-code keyed `score`
- `currentGameNumber`
- current `blueTeamCode`, `redTeamCode`, `controlledSide`
- root seed presentation policy와 `seedDerivationAlgorithm`; current derived seed는 canonical string
- cumulative `excludedChampionIds`, `seriesHistoryBeforeHash`
- game number 순서의 compact summaries와 receipt/integrity projection
- active child Draft session ID/status/revision 및 parent binding
- `allowedCommands`
- `winnerTeamCode`
- created/last activity/expiry와 cancellation/blocked reason
- current policy/profile/configuration/rules/engine/Draft resource metadata

Frontend는 다음을 하지 않는다.

- Timeline/event를 세어 score나 winner를 다시 계산하지 않는다.
- Games array length로 next game number를 추론하지 않는다.
- 화면 좌우 위치나 display name으로 `TeamSide`/team identity를 추론하지 않는다.
- 이전 화면 text에서 Hard Fearless exclusions를 복원하지 않는다.
- Local optimistic state를 authoritative commit으로 간주하지 않는다.
- `allowedCommands`에 없거나 revision이 stale한 mutation을 제출하지 않는다.

화면 흐름은 다음과 같다.

```text
Series 설정
  → Game N server mapping/score/history 확인
  → 기존 Player Draft component(series binding 전달)
  → Draft 완료 확인
  → 경기 실행/대기
  → Game N 결과 + authoritative score
  → 다음 Game 또는 Series 최종 결과
```

Standalone `PLAYER_CONTROLLED_DRAFT_FRONTEND_V1`은 Game 1 setup과 기존 다섯 endpoint로 먼저 구현할 수 있다. Draft board component는 explicit `teamCodes`, `controlledSide`, `gameNumber`, exclusions, session/revision과 command callbacks를 받도록 만들면 이후 Series wrapper가 같은 view component를 재사용할 수 있다.

## Concurrency, idempotency와 expiry

### Locking과 reservation

- Series repository는 series ID별 짧은 atomic mutation boundary를 제공한다. Static/global gameplay state를 만들지 않는다.
- Draft action과 lifecycle mutation은 per-series serialization이 가능하지만 global repository lock으로 모든 series를 막지 않는다.
- Long-running Match Engine 실행 동안 series lock을 유지하지 않는다. Reservation → external execution → compare-and-commit을 사용한다.
- Reservation 자체도 series/game/command/payload/revision/token에 구조적으로 결속한다. Event message나 request arrival order를 identity로 사용하지 않는다.
- Cancel/expiry가 reservation 중 승인되면 reservation을 invalidate한다. 늦게 도착한 valid output도 compare-and-commit에서 거부하고 score/history는 바뀌지 않는다.

### Command receipts

각 mutating command는 canonical payload hash, expected revision, resulting revision/status, result identity와 completion state를 작은 receipt로 기록한다. Bounded repository이므로 terminal/expired series cleanup과 함께 receipt도 제거한다. 동일 command replay는 ordinary stale check보다 먼저 receipt를 확인하되 payload exact equality가 필수다.

### Parent/child lifetime

현재 standalone Player Draft의 fixed 30-minute TTL은 그대로 둔다. Series child에 그 값을 무조건 복사하면 parent가 살아 있는데 child가 먼저 사라지거나, 반대로 expired parent 뒤 child가 남을 수 있다.

`V1 RECOMMENDATION`:

- Parent series가 lifetime/cleanup authority를 가진다.
- Series-scoped 성공 command와 child Draft action은 `lastActivityAt`을 갱신하고 configurable sliding expiry를 연장한다. Rejected/stale command와 polling GET은 keepalive가 아니다.
- Child `expiresAt`은 parent expiry를 넘을 수 없고 parent cancel/expiry cleanup이 child를 함께 invalidation한다.
- Child만 만료되면 score/history와 frozen game context는 유지하고 새 child를 만들 수 있다.
- Valid simulation reservation은 configured execution lease 동안 ordinary TTL cleanup에서 보호한다. Lease가 끝나면 reservation을 실패 처리하고 late result를 거부한다.
- Exact series TTL, maximum series 수, child idle TTL과 simulation lease 값은 config ownership에 두며 구현 전 product/operations 결정이 필요하다.

Process-local V1에서 cleanup은 parent와 child를 같은 ownership boundary에서 수행해야 capacity leak과 orphan child를 막을 수 있다. 서로 다른 repository를 쓰더라도 외부 caller가 child repository를 직접 mutate하지 못하게 하고 lock order를 고정한다.

## Persistence-ready boundary

V1은 process-local ephemeral이지만 aggregate가 미래 persistence를 막지 않아야 한다.

- `SERIES_AGGREGATE_SNAPSHOT_V1`, `SERIES_GAME_V1`, `SERIES_GAME_RECEIPT_V1`, `SERIES_COMMAND_RECEIPT_V1`처럼 schema-versioned immutable snapshot을 사용한다.
- Stable team/player/champion/side ID와 canonical ordered list/map을 저장하고 display metadata는 별도 projection 또는 재조회 대상으로 둔다.
- Wall-clock fields와 gameplay identity/hash material을 분리한다.
- Root seed, derivation algorithm, game mapping/history, completed Draft snapshot, receipt/provenance를 보존한다.
- Repository interface는 create/get/atomic mutate/reserve/compare-and-commit/cleanup 의미를 노출하되 storage 구현 세부를 domain에 누출하지 않는다.
- Live Java locks, thread/future, raw exception, full output/timeline과 unordered collections는 persistence snapshot에 넣지 않는다.
- Future DB 전환은 series aggregate와 game commit을 한 transaction으로 저장해야 한다. V1 process-local semantics보다 약한 eventual score/history commit을 허용하지 않는다.

구체적인 DB table, migration, Save/Load UI와 restart recovery는 이 문서 범위가 아니다.

## Machine-verifiable invariants

1. 한 series에 current/non-terminal active game은 최대 1개다.
2. 한 game에 non-terminal child Player Draft session은 최대 1개다.
3. Game number는 1부터 시작하고 records/commits가 contiguous하다.
4. BO3는 최대 3 games/2 wins, BO5는 최대 5 games/3 wins다.
5. `sum(scoreByTeamCode) == committedGameCount`다.
6. `COMPLETED` 뒤 score/games/history/winner는 immutable이다.
7. Committed game winner team code는 두 참가 team 중 하나다.
8. Game BLUE/RED는 서로 다른 authoritative team code이고 참가 set과 exact다.
9. Managed team의 `controlledSide`는 game mapping에서 파생한 값과 exact다.
10. Game N exclusions는 committed Games 1..N-1의 BLUE/RED picks union이다.
11. Committed game의 10 unique picks는 history에 정확히 한 번 추가된다.
12. Bans, aborted Draft와 failed/invalid/no-result Match는 history에 추가되지 않는다.
13. Failed/aborted game은 score/history/committed count mutation이 0이다.
14. Duplicate command와 stale revision의 추가 mutation 및 gameplay Random 소비는 0이다.
15. 같은 series/game/frozen input retry는 Draft, Match, receipt identity가 exact다.
16. 다른 series/game의 child Draft/result/receipt/history 재사용은 거부된다.
17. `seriesHistoryBeforeHash`와 canonical ordered exclusions는 서로 exact다.
18. History는 monotonic하며 이전 `ChampionId`를 제거할 수 없다.
19. Game commit key `(seriesId, gameNumber)`는 한 번만 성공한다.
20. Match Engine policy/profile/configuration/rules/engine/resource identity mismatch는 commit 전에 거부된다.
21. Production은 계속 Matchup/Composition ON, Jungle Economy/Tempo OFF다.
22. Lifecycle, logging, diagnostics와 seed derivation은 Match Simulator gameplay Random stream을 추가 소비하지 않는다.
23. Full output/timeline은 aggregate/repository 기본 저장 대상이 아니다.
24. Allowed commands는 status/revision/active child/reservation에서 server가 계산하며 client가 제출하지 않는다.

## Trade-offs

### 서버 권위 vs client 편의

Client winner/score/history 제출은 단순하지만 위조와 race에 취약하다. V1은 server simulate-and-commit을 선택한다. 대가로 long-running reservation, progress/timeout UX와 retry contract가 필요하다.

### Full output 저장 vs compact receipt

Full output 저장은 즉시 playback이 가능하지만 session마다 수십 MB heap을 누적한다. V1은 compact summary/receipt와 필요 시 deterministic re-execution을 선택한다. 결과 목록은 빠르게 재조회되지만 full playback 재요청은 CPU를 다시 지불하며 resource drift 시 fail-closed한다.

### Process-local V1 vs DB

Process-local repository는 현재 session 구조와 잘 맞고 구현이 작지만 restart loss, multi-node routing과 durable history가 없다. V1은 이 제한을 응답/문서에 명시하며 DB처럼 가장하지 않는다. Snapshot/repository boundary는 이후 storage 교체를 준비한다.

### Series lock vs reservation

Match 전체 동안 lock을 유지하면 다른 조회/취소를 막고 capacity를 장시간 점유한다. V1은 짧은 reserve/compare-and-commit을 선택한다. 대가로 reservation token, late result rejection과 failure cleanup을 정확히 구현해야 한다.

### Hard Fearless pool exhaustion

조용한 rule 완화는 UX를 이어가지만 history/rule/input provenance를 깨뜨린다. V1은 next-game preflight와 structured `BLOCKED`를 선택한다. Authored pool 확장이나 rule V2는 별도 versioned product change다.

### Side alternation vs competitive choice

단순 교대는 V1 재현성과 API를 단순화하지만 패자 side 선택 같은 실제 대회 UX를 제공하지 않는다. 후자는 V2 state/timeout/AI policy와 함께 추가하는 편이 안전하다.

## V1 결정사항

다음 항목은 이 sketch의 구현 기준이다. `PRODUCT_DECISION_REQUIRED`로 분리한 값은 승인 전 production rule이 아니다.

- Backend-owned `SeriesAggregate`와 team-code keyed score
- BO3 first-to-2, BO5 first-to-3, required wins 즉시 종료
- Series/Game/child Draft 상태 enum 분리
- Additive `/api/v1/series` route와 standalone API compatibility
- Parent-bound child Draft; arbitrary client team/side/seed/history/result 거부
- Root seed에서 versioned game seed derivation; game별 client seed 금지
- Committed picks만 양 팀 공통 Hard Fearless exclusions로 누적
- Server-side legal pool preflight와 fail-closed blocked status
- Production V9 simulate-and-commit, decisive valid output만 atomic score/history commit
- Per-series revision, command receipt, structured commit key와 reservation
- Compact result/receipt 저장, full timeline 기본 비보관
- Process-local bounded single-node V1, restart loss 명시
- Current policy/profile/gameplay와 lifecycle Random 0 소비 유지

## PRODUCT_DECISION_REQUIRED

| ID | 권장안 | 결정이 필요한 이유 |
| --- | --- | --- |
| `SERIES_V1_SIDE_POLICY` | Game 1 server-validated mapping + game별 단순 교대 | 현재 production에 authoritative BO3/BO5 side rule이 없음 |
| `SERIES_V1_ROOT_SEED_UX` | User가 create에서 canonical root seed 제출 | 사용자 재현성 UX와 숨겨진 server seed 중 제품 선택 필요 |
| `SERIES_V1_LIFETIME_LIMITS` | Parent-owned sliding TTL + bounded capacity + simulation lease | 정확한 TTL/capacity/lease 값은 운영 예산과 UX가 필요 |
| `SERIES_V1_NO_DECISIVE_RESULT` | 무승부 처리 없이 series blocked | Re-seed/rematch를 허용하면 game identity와 score rule이 추가로 필요 |
| `SERIES_V1_FULL_REPLAY_SCOPE` | Compact summary는 V1 필수, full replay는 deterministic endpoint로 제공 | CPU 비용과 frontend playback 요구의 제품 우선순위 필요 |

결정 전에는 권장안을 기존 동작 또는 확정 production rule로 문서화하지 않는다.

## V2 backlog

- DB persistence, Save/Load와 restart recovery
- Authentication/authorization, ownership과 rate limiting
- Multi-node session routing, distributed lock와 transactional commit
- Background match job/queue, WebSocket/progress streaming
- Previous-game loser side choice와 timeout/default/AI choice policy
- Career/Season schedule-owned full side mapping
- Historical full replay/object storage와 resource-version replay runtime
- Explicit rematch/voided-game contract와 no-decisive-result 운영 처리
- AI 대 AI league-wide batch series
- BO5 pool exhaustion을 줄이기 위한 별도 authored pool/rule version 검토

## 구현 batch 제안

1. `SERIES_LIFECYCLE_V1_DOMAIN_AND_REPOSITORY`
   - Immutable aggregate/snapshot, status/invariants와 canonical identities
   - Bounded process-local repository, revision/idempotency/command receipts
   - Side/seed/product decision을 versioned configuration으로 고정
2. `SERIES_LIFECYCLE_V1_DRAFT_INTEGRATION`
   - Series-owned child Player Draft와 additive series-aware engine/input boundary
   - Game number/side/seed/history binding, managed-side 전환
   - Hard Fearless history reconstruction과 legal completion preflight
3. `SERIES_LIFECYCLE_V1_MATCH_COMMIT_AND_API`
   - Production V9 reservation → execution → compare-and-commit
   - Compact game receipt/summary, score/next game/series completion
   - Additive REST API와 deterministic full replay 선택 경계
4. `SERIES_LIFECYCLE_V1_BACKEND_HARDENING`
   - Concurrent Draft/simulation/next-game/cancel, stale/duplicate/expiry/late result
   - Focused boundary/integration/determinism tests와 final backend full regression
5. `SERIES_FRONTEND_V1`
   - Series 설정/score/game progression/final result
   - Existing Player Draft component 재사용과 revision/allowedCommands UX

현재 제품 순서상 즉시 다음 작업은 standalone `PLAYER_CONTROLLED_DRAFT_FRONTEND_V1`일 수 있다. 이 contract sketch는 그 작업을 막지 않는다. 단판 Draft component를 explicit context와 backend-provided view로 만들면 이후 Series wrapper가 재사용할 수 있다.
