# Player System

## Identity 경계

Player subsystem은 서로 대체할 수 없는 세 identity를 사용한다.

| Identity | 답하는 질문 | 구성과 scope |
| --- | --- | --- |
| `PlayerId` | 누구인가 | explicit authored canonical slug, 선수 개인의 영구 identity |
| `PlayerRatingKey` | 현재 roster snapshot의 어느 자리인가 | normalized `teamCode + Position` |
| `PlayerKey` | 이번 match의 어느 slot인가 | `TeamSide + Position` |

`PlayerId`는 immutable/non-blank value object이며 JSON에서는 문자열로 안정적으로 직렬화된다. 현재 값은 별도 identity resource에 명시되어 있고 runtime에서 nickname, team, position으로 생성하지 않는다. nickname과 `Player.getName()`은 display metadata일 뿐 lookup key가 아니다.

현재 mapping source:

- `backend/src/main/resources/players/lck-player-identities-2026-08-21-v1.json`
- version `lck-player-identities-2026-08-21-v1`
- SHA-256 `badbbaa3ae7fbe5eaaf83ee8e97a93134476493a45167ec3d1637c7243909018`
- 10 teams × five positions = 50 unique `PlayerId` / 50 unique `PlayerRatingKey`

`PlayerIdentityCatalog`은 하나의 immutable record set에서 `PlayerId`와 `PlayerRatingKey` 양방향 index를 만든다. identity, rating, proficiency의 subject set과 nickname display consistency가 다르면 load를 거부한다.

Legacy `Player` constructor는 호환용으로 남아 있지만 PlayerId를 nickname에서 추론하지 않는다. `DummyDataFactory`는 코드에 명시된 `player-fixture-*` ID를 사용한다. 실제 LCK production-capable path는 항상 explicit `PlayerId`를 전달한다.

## Player Ratings

`PlayerRatings`는 1..20 범위의 일반 능력과 role-specific 능력을 표현한다. 한 player는 정확히 12개 rating을 가진다.

- 공통 6개: mechanics, decision making, map awareness, positioning, combat execution, consistency
- laner 6개: farming, trading, wave management, lane pressure, priority conversion, side lane
- jungle 6개: pathing, jungle resources, enemy tracking, lane intervention, objective decision/secure
- support 6개: vision, lane support, rotation, engage, ally protection, area setup

책임은 다음처럼 분리한다.

- `DECISION_MAKING`은 take/reset/contest, conversion, rotation 같은 판단 quality다.
- mechanics/combat/trading/engage/secure 등은 execution 능력이다.
- `FARMING`은 TOP/MID/ADC의 자원 수급이며 JUNGLE은 `JUNGLE_RESOURCE_MANAGEMENT`를 쓴다.
- `CONSISTENCY`는 match-scoped realization 변동 폭을 제한하며 별도 combat bonus가 아니다.

`PlayerRatingCatalog`의 기존 `PlayerRatingKey` lookup은 유지된다. `PlayerIdentityCatalog`를 통해 `PlayerId` rating lookup과 양방향 current-roster resolution도 제공한다. 기존 resource의 600개 rating 값은 이 milestone에서 변경하지 않았다.

## Champion Proficiency

Authoritative source는 다음 sparse resource다.

- `backend/src/main/resources/players/lck-champion-proficiency-2026-08-21-v1.json`
- version `lck-champion-proficiency-2026-08-21-v1`
- SHA-256 `2c36b8a109aba9dfe84c1da319fe02708a72a1341d334dc6d5e3f605b0023aad`
- source identity: `PlayerRatingKey × ChampionRoleKey`
- runtime identity: `PlayerId × ChampionRoleKey`

`ChampionProficiencyResourceLoader`는 semantic parse 전에 raw bytes SHA를 확인한 뒤 rating version, champion pool version, 216 legal role keys, 50 subject equality, role legality와 subject position을 검증한다. `ChampionProficiencyCatalog`는 identity mapping을 거쳐 canonical `Map<PlayerId, ChampionProficiencies>`를 노출한다.

현재 loaded 결과는 resource에서 측정한 값이다.

- authored overrides 732
- potential same-position legal keys 2,160
- neutral fallback keys 1,428
- score distribution: 15=35, 16=160, 17=228, 18=210, 19=81, 20=18
- 17+=537, 19+=99, 20=18

Sparse 의미는 엄격하다.

- 작성되지 않은 legal same-position key만 neutral 14다.
- unknown `PlayerId`/`PlayerRatingKey`, illegal role, cross-position binding은 예외다.
- 2,160개 dense profile을 만들지 않는다.
- review 문서의 11개 scope gap을 legal role로 승격하지 않는다.

Proficiency는 Player Ratings와 분리된다.

- Player Ratings: champion을 바꿔도 유지되는 일반/role 능력
- Champion Proficiency: player × champion × selected role execution 적합도
- Champion Power: player와 무관한 champion kit/time curve

match runtime에서 proficiency는 `PlayerMatchPerformance.execution(...)` 경로에만 보정된다. decision quality와 FARMING을 바꾸지 않는다. 이 결정은 [ADR-003](../adr/ADR-003-player-rating-vs-champion-proficiency.md)에 정리되어 있다.

## Team Assembly

`LckTeamAssembler`는 다음 immutable catalog를 결합한다.

```text
PlayerIdentityCatalog
  + PlayerRatingCatalog
  + ChampionProficiencyCatalog
  → current LCK Team (TOP/JUNGLE/MID/ADC/SUPPORT)
```

10개 팀을 각각 5명으로 deterministic하게 조립하며 `DummyDataFactory`에 의존하지 않는다. 각 `Player`에는 stable `PlayerId`, 실제 12 ratings, 실제 sparse proficiency profile이 들어간다. production binding은 runtime PlayerId, current `PlayerRatingKey`, proficiency owner가 일치하지 않으면 fail-fast한다.

## Runtime Integration

Explicit real player는 match 초기화 때 다음 흐름을 거친다.

```text
PlayerId + PlayerRatingKey + PlayerRatings + ChampionProficiencies
  → LckTeamAssembler
  → Player + selected ChampionRoleKey
  → PlayerMatchPerformance.realize(seed, side, position)
  → PlayerState(PlayerKey, PlayerId, display name)
```

- match state lookup은 `PlayerKey`/position index를 사용한다.
- person-level proficiency는 `PlayerId`를 사용한다.
- `TeamfightResolver`는 display name으로 `PlayerState`를 찾지 않는다.
- `MatchEvent`의 기존 `killer`/`victim`/`assists`는 display 호환 필드로 유지하고, additive `killerPlayerId`/`victimPlayerId`/`assistPlayerIds`에는 stable ID를 기록한다.
- `LaneCombatData`, `JungleGankData`, `CounterGankData`, `RoamData`의 `*PlayerId` 필드도 stable identity를 담는다.

GEN 대 T1 real-team smoke는 explicit legal assignments와 seed 73 하나로 simulator 종료 및 exact replay를 검증한다.

## Draft와 Reachability

`DraftTeamContext.from(realTeam)`은 position별 proficiency와 stable `PlayerId`를 함께 보유한다. Draft scoring은 여전히 Player Ratings를 읽지 않고 proficiency를 `PLAYER_FIT`에 사용한다.

기존 `RealProficiencyCandidateReachabilityGate`를 실제 537개 authored 17+ key에 실행한 결과:

- bounded scenarios: 1,611 (key당 3)
- legal scenarios: 1,349
- candidate appearances: 261
- candidate-present high keys: 150 / 537 (0.2793296089)
- no-legal-scenario keys: 0
- candidate-unreachable high keys: 387

387건은 `REVIEW_REAL_PROFICIENCY_CANDIDATE_UNREACHABLE` review signal이다. 데이터, Draft weight, shortlist size, search bound는 자동 변경하지 않았다. 보고서는 `backend/build/reports/phase13g-real-proficiency-reachability/`에 생성된다.

## HTTP 경계

현재 `MatchController`는 계속 `DummyDataFactory` legacy/demo team을 사용한다. `LckTeamAssembler`와 real proficiency catalog는 production-capable backend 경계까지 구현됐지만 HTTP default path, Draft API, frontend flow는 바꾸지 않았다.

다음 integration은 `REAL_DRAFT_TO_MATCH_BACKEND_ORCHESTRATION`이다. 세부 field와 rejection contract는 [Player Data Schema](../reference/player-data-schema.md)를 참고한다.
