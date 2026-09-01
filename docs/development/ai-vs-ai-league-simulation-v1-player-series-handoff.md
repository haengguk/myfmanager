# AI League V1 Player Series Handoff

상태: `AI_VS_AI_LEAGUE_SIMULATION_V1_PLAYER_SERIES_HANDOFF_IMPLEMENTED_READY_FOR_PERSISTENCE_AND_JOBS`

기준 review HEAD는 `cc3512b5d6a1b873fe5192df9b0f664e0a24a1f5`이고 frozen product-decision canonical SHA-256은 `81a4755760fb513c5803d55dd4855c03fda487114bb7c89b431c959a00a0fb14`로 유지했다. Gameplay tuning, Draft scoring/search/selection, Matchup/Composition, structure 규칙과 Random 소비 순서는 변경하지 않았다.

## 제품 흐름

Hybrid Season의 관리 팀 fixture를 시작하면 브라우저가 일반 Series setup을 다시 작성하지 않는다. 내부 handoff가 frozen Season/fixture에서 binding을 만들고 기존 Series/Player Draft kernel에 연결한다. Player는 매 game에서 관리 팀 side의 선택만 제출하고 상대 turn은 기존 Production Auto Draft가 진행한다. 두 팀, BO3, Game 1 side, fixture root, game seed, Hard Fearless history와 Production V9 identity는 server가 소유한다.

```text
frozen PLAYER_CONTROLLED fixture
  -> server-created LeagueFixtureSeriesBindingV1
  -> LeaguePlayerSeriesBindingPort CREATED/ACTIVE
  -> existing Series + mixed-authority Player Draft + Production V9
  -> completed Series authoritative replay evidence
  -> V1 fixture body + V2 unified authority envelope
  -> VerifiedLeagueFixtureCompletion
  -> LeagueSeasonAggregate standings exactly-once application
```

Series가 아직 끝나지 않았거나 child Draft가 취소됐거나 증거가 맞지 않으면 receipt와 standings delta를 만들지 않는다. Binding/Series가 남아 있으면 같은 command로 재개하며, 복구할 수 없는 불일치는 새 Series를 조용히 만들지 않고 `PLAYER_SERIES_RESTART_REQUIRED` 또는 `BLOCKED`로 닫는다.

## 구현 전 경계 감사

- `LeagueSeasonAggregate`는 frozen schedule/snapshot/managed-team identity와 standings revision을 소유하지만 Batch 2까지 explicit `leagueId` 필드가 없었다. Display text나 Season ID 파싱 대신 structured `leagueId` ownership을 추가했다.
- `PLAYER_CONTROLLED` fixture는 이미 bound Series ID, fixture root, Game 1 BLUE/RED와 history-before 기반 League sibling game-seed algorithm을 고정하고 있었다.
- Batch 2 receipt는 game/fixture evidence가 강했지만 explicit League ownership, Player binding과 mixed Draft authority를 한 공통 envelope에 담지 못했다. V1의 schema/hash 의미를 바꾸지 않고 V2 wrapper를 추가했다.
- `SeriesAggregate`, `SeriesRepository`, child binding, TTL/capacity/revision/command receipt는 process-local이다. 이를 durable Season authority로 선언하지 않았다.
- Standalone Series seed는 `managedTeamCode` salt를 사용하지만 League sibling seed는 canonical pair-first anchor를 사용한다. 두 schema를 origin으로 분리했다.
- 기존 Series-owned child Draft, joint Hard Fearless pool preflight, Production V9 reservation/commit/replay 경계를 그대로 재사용했다.
- Public Series create는 opponent/format/managed team/Game 1 side/root를 caller에게 받지만 League origin/binding/fixture를 받는 필드는 없다. League start는 public HTTP self-call 없이 내부 port를 사용한다.

## Canonical binding과 port

`LeagueFixtureSeriesBindingV1`은 private constructor와 package-owned factory를 사용한다. Explicit ordered UTF-8 payload에 schema/hash algorithm, League/Season/fixture/reservation, bound Series, teams/managed team/format/side, fixture root/seed anchor, empty initial history, schedule/product/snapshot/resource와 policy/profile/configuration/rules/engine identity, binding revision/status를 넣고 versioned SHA-256으로 결속한다. 최대 크기는 32 KiB다.

`LeaguePlayerSeriesBindingPort`는 create/load/CAS transition, exact command replay, completed receipt 저장 경계를 분리한다. 현재 `InMemoryLeaguePlayerSeriesBindingAdapter`는 fixture uniqueness, command ID/payload conflict와 `CREATED -> ACTIVE -> COMPLETION_PENDING_VERIFICATION -> VERIFIED` 전이를 process 안에서만 보장한다. Value에는 mutable aggregate, callback, thread/Future나 object identity를 넣지 않았다. Relational adapter가 이 port를 교체할 수 있지만 현재 JVM restart durability는 없다.

## Start, resume와 frozen context

Start command는 `leagueId`, Season aggregate, fixture ID, expected Season revision과 command ID만 받는다. Opponent, format, side, root/game seed, managed team, profile, history, winner/score/receipt 필드는 없다. 다음 조건을 Draft/Match/Random 실행 전에 fail-closed한다.

- exact League/Season ownership과 current start revision
- `HYBRID_MANAGER`, non-null managed team과 managed snapshot
- schedule에 존재하는 managed `PLAYER_CONTROLLED` BO3 fixture
- 미반영 standings completion
- current product/resource/runtime frozen identity
- initial Hard Fearless joint-pool completion 가능성

Exact command/payload는 같은 binding과 bound Series를 재개한다. 같은 command ID의 다른 payload는 conflict다. Spectator, FULL_AUTO/non-managed, stale revision과 snapshot drift는 kernel 실행 0이다. 다른 fixture가 완료되어 Season revision이 시작 시점보다 증가해도 frozen schedule/binding이 같으면 해당 Player Series completion은 허용한다.

## Series, Draft, side/seed와 Hard Fearless

`SeriesOrigin.STANDALONE | LEAGUE_BOUND`를 추가했다. Standalone create/API/ID/seed 의미는 그대로이고 League-bound Series만 binding hash와 seed anchor를 요구한다. Fixture root는 bound Series root로 그대로 한 번 사용한다. 각 game seed는 fixture의 BLUE/RED, game number, anchor와 exact history-before hash로 `LeagueIdentity.gameSeed`를 한 번 계산한다.

Game 1 history는 비어 있다. 기존 professional 20-turn Draft에서 controlled side의 turn만 PLAYER이고 반대 side는 AI다. Decisive committed game만 picks 10개를 history에 추가하며 ban은 추가하지 않는다. Game 2/3은 앞선 picks를 legal pool에서 제외한다. Failure, no-decisive, stale/duplicate와 cancel은 score/history를 바꾸지 않는다. League-bound child cancel과 Series interruption은 Season cancel이 아니라 restart-required 의미다.

## Completed Series와 unified receipt

Completion command에는 League/Season/fixture/binding hash만 있고 winner, score와 receipt input이 없다. Kernel은 completed server Series의 committed games를 stored Draft/completion binding으로 Production V9에 다시 실행하고 `SeriesGameReceipt` exact equality와 aggregate mutation 0을 확인한다.

그 authoritative evidence로 다음을 만든다.

- 기존 `LeagueFixtureCompletionReceiptV1`: frozen fixture body와 ordered game receipts
- `LeagueFixtureDraftAuthorityReceiptV1`: Auto는 control fields가 모두 null, Player는 game별 controlled side와 control policy/evidence hash
- `LeagueFixtureCompletionReceiptV2`: explicit `leagueId`, nullable Player binding hash, V1 body와 ordered authority receipts의 공통 Auto/Player envelope

Player verifier는 stored binding, current frozen snapshot/resource, completed Series evidence, 독립 재구성한 actual game/authority receipts와 V2 canonical hash를 대조한다. Team/side/root/game seed/history, Draft decisions/final assignments, policy/profile/config/rules/engine, input/output/replay/timeline/Random, score/winner/early termination이 모두 맞아야 opaque completion을 만든다. Receipt 내부 목록을 actual evidence로 되돌려 자기 자신을 증명하지 않는다.

첫 verified completion만 standings/revision을 1 올리고 exact replay는 0이다. Verified binding의 duplicate completion은 stored V2 bytes를 반환하며 Match Engine 실행도 0이다. Omission/duplicate/reorder, binding/League/Season/fixture/resource/authority/game evidence 변조는 거부한다. 검증 실패는 binding을 pending에 고착시키지 않고 `BLOCKED`로 전이하며 standings는 그대로다.

## 검증 결과

실행한 주요 lane은 다음과 같다.

- Player handoff unit/actual Production V9: managed team BLUE/RED, exact replay/conflict, rejected-context kernel 0, pool exhaustion, incomplete/invalid completion, cancel/restart, actual GEN–T1 BO3, 20 decisions/10 assignments, 2~3 games, Hard Fearless +10/game, diagnostics ON/OFF exact, V2 tamper와 exactly-once
- Auto parity: 기존 synchronous runner가 V1 core와 FULL_AUTO authority의 V2 envelope를 함께 생성하고 기존 V1 compact body도 유지
- Fresh JVM: 두 독립 JVM의 Player binding+V2 receipt와 Auto V2 receipt canonical bytes/hash exact
- Affected regression: Batch 1 schedule/standings, Batch 2 runner, Series lifecycle/repository/API/replay, Player Draft engine/boundary/session, Match Engine V1/Production V9와 Real Match API

최종 executable tree의 complete backend regression은 첫 실행에서 성공했다.

| 항목 | 결과 |
| --- | ---: |
| JUnit suites | 249 |
| Tests | 2,315 |
| Failures | 0 |
| Errors | 0 |
| Skipped | 2 |
| Aggregate JUnit XML time | 1,901.498초 |
| Gradle wall duration | 16분 53초 |
| Build | `BUILD SUCCESSFUL` |

두 skip은 기존 explicit 대형 diagnostic이며 이번 correctness 누락이 아니다. League API/frontend가 아직 없어 frontend build와 Playwright는 실행하지 않았다. 90-fixture Season, balance/performance population과 historical artifact 재생성도 실행하지 않았다.

## 남은 제한과 Batch 4 인계

현재 binding/Series/receipt 상태는 process-local이고 server restart 뒤 복구되지 않는다. Relational DB/migration, job/queue/worker, lease/heartbeat/retry scheduler, transactional outbox/consumer, durable standings application ledger와 multi-node coordination은 `AI_VS_AI_LEAGUE_SIMULATION_V1_PERSISTENCE_AND_JOBS`가 구현해야 한다.

League REST API/controller/DTO와 dashboard도 아직 없다. 현재 handoff는 backend 내부 application boundary이며 브라우저가 직접 호출할 public League journey가 아니다. Batch 4는 canonical binding/V2 receipt/port 의미를 유지한 채 persistence adapter와 crash recovery를 추가하고, Batch 5가 additive API를 연결해야 한다.
