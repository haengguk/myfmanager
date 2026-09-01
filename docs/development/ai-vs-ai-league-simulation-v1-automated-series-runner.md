# AI vs AI League Simulation V1 Automated Series Runner

상태: `AI_VS_AI_LEAGUE_SIMULATION_V1_AUTOMATED_SERIES_RUNNER_IMPLEMENTED_READY_FOR_PLAYER_HANDOFF`

이 milestone은 AI League V1 Batch 2다. 하나의 frozen `FULL_AUTO` fixture를
Production Auto Draft와 Match Engine V9으로 끝까지 실행하고, 그 실제 실행 증거가
검증된 경우에만 standings에 적용 가능한 opaque completion을 만든다. 90경기 Season
batch, durable worker/job, DB/outbox, API와 frontend는 아직 구현하지 않았다.

## 시작 경계 감사

Batch 1의 `LeagueSeasonAggregate`는 immutable Season/schedule/standings/revision을,
`LeagueFixture`는 frozen execution mode, side, fixture root seed, bound Series ID와
history-bound game seed를 소유했다. `LeagueStandings`는 fixture ID와 receipt hash를
함께 보관해 exact replay를 no-op으로 만들었지만, 당시 public record였던
`VerifiedLeagueFixtureCompletion`에는 임의의 64자리 SHA가 실제 경기에서 왔음을
증명하는 경계가 없었다.

기존 production 실행 경계는 `RealDraftMatchOrchestrator.orchestrateV1`이었다. 이
경로는 authoritative LCK roster, Production Auto Draft 20턴, current policy/profile의
`MatchEngineV1`과 output integrity를 사용하지만 성공 직후 caller-owned
`SeriesDraftHistory`를 commit한다. 따라서 decisive 검증 전 commit을 금지하는 League
runner를 위해 같은 내부 흐름을 `PreparedAutoDraftMatch`까지 additive하게 분리했다.
Standalone overload는 이 준비 결과를 즉시 기존 방식으로 commit하므로 기존 Real
Match/Series API와 seed/hash 의미는 유지된다.

## Runner input과 ownership

`LeagueAutomatedSeriesRunnerInput`은 immutable Season aggregate, frozen fixture와 frozen
product-decision hash를 결속한다. Runner는 stateless Spring component이며 score,
ordered game receipt와 mutable `SeriesDraftHistory`는 한 번의 `run` 호출 안에서만
생성된다.

Draft search 전에 다음을 fail-closed한다.

- fixture가 exact frozen schedule member인지
- `executionMode == FULL_AUTO`인지
- Season/schedule/product-decision identity가 current frozen value인지
- current authoritative 10-team roster/player/resource/runtime snapshot이 Season
  snapshot과 exact equality인지
- BO3/BO5 closed format인지
- current Hard Fearless pool로 양 팀 5-role Draft를 완주할 수 있는지

`PLAYER_CONTROLLED`, frozen identity drift와 pool exhaustion은 game execution 0으로
`BLOCKED`를 반환한다. Runner는 wall clock, unseeded `Random`, global/static/ThreadLocal
mutable state를 사용하지 않는다.

## Auto Draft → Production V9 → commit

각 game은 frozen fixture가 계산한 BLUE/RED와 history-before-bound game seed를 사용한다.
Production executor는 기존 Auto Draft와 Match Engine V9을 commit 전까지 실행하고,
input/output/policy/profile/configuration/rules/engine, roster, Draft, replay/timeline과
Random fingerprint를 검증해 compact game evidence를 만든다.

Winner가 없는 safety-timeout result는 score와 Hard Fearless를 변경하지 않고 전체
fixture를 `BLOCKED`로 끝낸다. Decisive result만 양 팀 pick 10개를 기존
`SeriesDraftHistory.commitCompleted` common path에 한 번 반영한다. 다음 game의
history-before hash와 exclusion은 이 committed state에서 파생한다. Required wins에
도달하면 다음 Draft/game을 만들지 않는다.

V1 production schedule은 계속 BO3다. Runner loop와 receipt는 closed `SeriesFormat`의
BO3/BO5 wins-required/maximum-games를 처리하지만 Batch 1 schedule policy가 BO3만
freeze하므로 production schedule을 BO5로 바꾸지는 않았다.

## Canonical game/fixture receipt

`LeagueFixtureGameReceiptV1`은 full timeline JSON 대신 다음 compact identity를 보관한다.

- game number, structured BLUE/RED, fixture-derived seed와 match identity
- ordered 20 Draft decisions, ordered picks/bans와 stable player final assignments 10개
- Hard Fearless history-before/history-after hash와 canonical champion list
- Draft rule/scoring/selection/meta/legal-role identity
- roster, policy/profile/configuration/rules/engine/resource identity
- input/output, replay provenance, simulator/structured timeline identity
- Random fingerprint schema/draw count/trace hash와 decisive winner/end identity

`LeagueFixtureCompletionReceiptV1`은 Season/fixture/bound Series/execution mode, format,
root/game seed algorithms, schedule/product/snapshot identities, ordered game receipts,
final team-code score/winner/loser와 actual game count를 명시적 ordered-line payload로
묶는다. Hash algorithm은
`SHA256_UTF8_EXPLICIT_ORDERED_FIXTURE_RECEIPT_LINES_TRAILING_NEWLINE_V1`이다. Map/set은
명시적으로 sort하고 display name/message, wall clock, PID, worker 순서와 raw floating
문자열은 canonical identity에 넣지 않는다. Compact limit은 128 KiB다.

## Proof gate와 standings exactly-once

`VerifiedLeagueFixtureCompletion`은 더 이상 public record constructor가 없다. Private
constructor는 runner package의 verifier/factory가 다음을 다시 대조한 뒤에만 호출된다.

- actual frozen runner input과 current production snapshot
- fixture/Season/bound Series/mode/team/format/root seed
- ordered actual game evidence와 receipt game list의 exact equality
- game별 side, history-bound seed, history transition과 decisive outcome
- current policy/profile/configuration/rules/engine/resource identity
- canonical fixture payload hash

따라서 fake 64-char hash, Season/fixture/mode/side/seed/score/winner/Draft/assignment/
history/resource/runtime/output/replay/timeline/Random 변조와 omitted/duplicated/reordered
game, cross-fixture/cross-Season receipt는 constructor 또는 verifier에서 거부된다.
Standings는 opaque verified value만 받고, first application만 Season revision과 두 팀
counter를 +1하며 exact replay는 같은 aggregate를 반환한다. 같은 fixture의 다른
receipt와 같은 receipt의 다른 fixture 거부 의미도 유지된다.

이 경계는 in-memory synchronous exactly-once domain 의미다. DB transaction,
transactional outbox와 durable idempotent consumer는 후속 persistence/job batch 소유다.

## 결정성과 검증 결과

Focused coverage는 다음을 포함한다.

- BO3 2:0/2:1, early termination, side/fixture seed와 history 0→10→20→30
- Player fixture, pool exhaustion, no-decisive result의 completion/standings 0
- diagnostics ON/OFF canonical receipt byte equality
- fixture 실행 순서 변경과 새 fixture history isolation
- canonical JSON round trip과 compact size
- 실제 GEN–T1 frozen fixture의 Production Auto Draft 20턴과 Match Engine V9
- two fresh JVM canonical fixture receipt byte/hash exact equality
- tamper/cross-boundary rejection와 standings exact replay
- 기존 Batch 1, Auto Draft, Player input, Series lifecycle/receipt, Match Engine V1/V9
  affected regression

Final affected bundle은 17 suites / 96 tests를 실행했다. 95개는 즉시 통과했고,
receipt constructor에서 이미 거부된 side tamper를 verifier 단계 거부로 기대한 test-only
분류 1건을 바로잡은 뒤 해당 runner suite 7/7이 통과했다. 이 보정은 production 실행
코드를 바꾸지 않았다.

Final executable production tree의 complete backend regression은 첫 실행에서
246 suites / 2,306 tests / failures 0 / errors 0 / skipped 2, aggregate JUnit XML
962.313초, Gradle wall 16분 14초로 clean pass했다. 이후 executable production,
resource, Gradle/shared fixture는 변경하지 않고 문서만 갱신했다. Frontend build,
Playwright, 90-fixture Season run, balance/performance population, calibration/holdout과
historical artifact 재생성은 실행하지 않았다.

## 변경 의미와 다음 단계

이제 서버 내부에서 하나의 AI fixture를 deterministic하게 끝내고 실제 Production V9
증거에서 나온 결과만 standings에 연결할 수 있다. Hybrid manager의 관리 팀 fixture는
여전히 runner에 들어갈 수 없으며 자동 위임도 없다.

다음 task는 `AI_VS_AI_LEAGUE_SIMULATION_V1_PLAYER_SERIES_HANDOFF`다. 동일 receipt schema,
side/seed/wins/Hard Fearless/production identity를 재사용하되, durable League-bound
Player Series binding과 server-created completion handoff를 구현해야 한다. 그 뒤 별도
persistence/job batch에서 DB, lease/heartbeat/retry, outbox와 restart recovery를 닫는다.

## 아직 남은 제한

- 90 fixtures를 자동 dispatch하는 Season job/worker가 없다.
- `PLAYER_CONTROLLED` fixture의 League-bound Series start/resume이 없다.
- DB, migration, outbox, durable exactly-once/restart recovery가 없다.
- League REST API, frontend, auth/ownership과 multi-node coordination이 없다.
- V1 production schedule은 BO3이며 BO5 schedule policy는 동결하지 않았다.
- full replay는 receipt에 저장하지 않으며 optional replay cache도 아직 없다.
