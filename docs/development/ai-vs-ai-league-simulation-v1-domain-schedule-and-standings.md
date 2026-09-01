# AI vs AI League Simulation V1 Domain, Schedule and Standings

상태: `AI_VS_AI_LEAGUE_SIMULATION_V1_DOMAIN_SCHEDULE_AND_STANDINGS_IMPLEMENTED`

이 milestone은 [Hybrid Season 계약](../architecture/ai-vs-ai-league-simulation-v1-contract-sketch.md)의 Batch 1을 구현한다. 아직 경기 실행, persistence, API와 frontend는 없으며, 이후 모든 실행 경로가 공유할 immutable Season/schedule/standings domain만 추가한다.

## 구현 범위

`com.lolfm.league`에 다음 production 경계를 추가했다.

- `LeagueV1ProductDecisions`: 9개 frozen decision의 ordered canonical value와 code-owned SHA-256
- `LeagueV1OperationalConfiguration`: 10팀, 90 fixtures, 병렬 2/4, lease/heartbeat/retry/retention exact V1 값
- `LeagueSeasonFrozenSnapshot`: team별 roster snapshot과 player/champion/Draft/Matchup/Composition/runtime identity 결속
- `LeagueSeasonAggregate`: mode/managed snapshot/schedule/standings/revision의 immutable aggregate
- `LeagueScheduleGenerator`: canonical circle method의 10팀 single/double round robin
- `LeagueFixture`: frozen execution mode, Game 1 side, fixture root seed, bound Series identity와 game side/seed
- `LeagueStandings`: verified completion만 받는 exactly-once receipt ledger와 immutable counters
- `LeagueRanking`: Series wins, game differential, game wins, mini-league와 seeded draw trace

## Frozen identity

Product decision schema는 `AI_LEAGUE_V1_PRODUCT_DECISIONS_CANONICAL_SHA256_V1`이고 current code-owned hash는 다음과 같다.

```text
81a4755760fb513c5803d55dd4855c03fda487114bb7c89b431c959a00a0fb14
```

Markdown 문장이 아니라 `LeagueV1ProductDecisions`의 9개 ordered `decisionId=canonicalValue`가 runtime identity다. 마지막 LF를 넣지 않는 규칙과 golden hash를 focused test가 고정한다. 같은 operational configuration ID로 값을 바꿀 수 없으며 변경에는 새 version이 필요하다.

## Schedule와 execution mode

Production default는 BO3 double round robin이다.

- canonical sorted 10 team codes
- 18 rounds, round당 5 fixtures, 총 90 fixtures
- 각 team은 round당 정확히 한 fixture
- 45 pair가 정확히 두 번 만나며 second leg의 Game 1 side는 first leg와 반대
- `HYBRID_MANAGER`의 managed team fixture 18개만 `PLAYER_CONTROLLED`
- 나머지 72개는 `FULL_AUTO`
- `SPECTATOR_FULL_AUTO`는 90개 모두 `FULL_AUTO`

Schedule/fixture/seed identity는 `executionMode`와 `managedTeamCode`를 입력으로 사용하지 않는다. 따라서 같은 Season/schedule/root seed의 Hybrid와 Spectator fixture는 같은 fixture ID/root seed/bound Series ID/game seed를 갖고 execution authority만 다르다.

## Seed algorithm 관계

Fixture root는 `AI_LEAGUE_FIXTURE_ROOT_SEED_SHA256_FIRST_8_BYTES_BIG_ENDIAN_SIGNED_LONG_V1`을 사용한다. League-bound game은 기존 standalone Series V1을 변경하지 않고 sibling algorithm `AI_LEAGUE_BOUND_SERIES_GAME_SEED_SHA256_FIRST_8_BYTES_BIG_ENDIAN_SIGNED_LONG_V1`을 사용한다.

League-bound algorithm은 기존 Series의 mode-dependent `managedTeamCode` salt 대신 pair의 canonical first team을 `seedAnchorTeamCode`로 결속한다. `fixtureRootSeed`는 bound Series root로 그대로 한 번 전달되고 다시 fixture seed로 파생되지 않는다. Batch 2 AI runner와 Batch 3 Player Series가 이 같은 `LeagueFixture` game seed를 사용해야 한다.

## Standings와 duplicate protection

`VerifiedLeagueFixtureCompletion`은 frozen fixture team/format과 일치하고 BO3 required wins를 만족해야 한다. 적용 ledger는 fixture ID와 canonical receipt hash를 모두 보관한다.

- exact same completion replay: 같은 aggregate, revision 증가 0
- same fixture/different receipt: 거부
- same receipt/different fixture: 거부
- wrong teams/incomplete score: 거부
- valid first completion: 두 team counter와 Season revision을 한 번만 증가

Ranking은 primary three-step 비교 뒤 그 primary tie group의 mini-league Series wins/game differential을 계산한다. 그래도 동률이면 sorted team set과 Season root seed의 candidate SHA-256 lexical order를 사용하며 입력 hash, candidate hash와 resolved order를 `tieBreakTrace`에 남긴다. Gameplay `Random`은 소비하지 않는다.

## 검증

Focused command는 다음 4 suites다.

```text
gradlew.bat test --tests com.lolfm.league.LeagueV1ProductDecisionsTest --tests com.lolfm.league.LeagueScheduleGeneratorTest --tests com.lolfm.league.LeagueSeasonAggregateTest --tests com.lolfm.league.LeagueStandingsTest --console=plain --no-daemon
```

최종 focused 결과는 4 suites / 15 tests / failures 0 / errors 0 / skipped 0이다. 실제 `LckTeamAssembler`의 10팀/50명 envelope, reversed input order, 90-fixture pair/round/side, Hybrid/Spectator seed parity, invalid mode/snapshot/game boundary, receipt duplicate/conflict, six-step ranking과 seeded trace를 검증한다.

Production Java가 추가된 final tree의 complete backend regression은 첫 실행에서 243 suites / 2,297 tests / failures 0 / errors 0 / skipped 2, aggregate JUnit XML 895.260초, Gradle wall 15분 11초로 clean pass했다. 두 skip은 기존 explicit diagnostic이며 이번 변경에서 추가되지 않았다.

## 비범위와 다음 단계

이번 batch는 `AutomatedSeriesRunner`, Production Auto Draft/Match V9 실행, Hard Fearless game commit, unified fixture receipt 본문, DB/job/lease/outbox, API와 frontend를 구현하지 않는다. Standings의 `VerifiedLeagueFixtureCompletion`은 Batch 2 canonical receipt verifier가 성공한 뒤에만 만들 application input이다.

다음 task는 `AI_VS_AI_LEAGUE_SIMULATION_V1_AUTOMATED_SERIES_RUNNER`다.
