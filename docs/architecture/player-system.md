# Player System

## Player Identity

Player subsystem에는 목적이 다른 두 구조화된 key가 있다.

| Key | Scope | 구성 |
| --- | --- | --- |
| `PlayerRatingKey` | authored roster/resource | normalized `teamCode + Position` |
| `PlayerKey` | 한 match | `TeamSide + Position` |

`PlayerRatingKey`는 nickname을 의도적으로 포함하지 않는다. nickname과 `Player.getName()`은 표시 및 현재 legacy lookup에 쓰이지만 authored rating identity는 아니다. 한 team은 TOP/JUNGLE/MID/ADC/SUPPORT 각각 정확히 한 starter를 가져야 한다.

## Player Ratings

`PlayerRatings`는 1..20 범위의 일반 능력과 role-specific 능력을 표현한다. 한 player는 정확히 12개 rating을 가진다.

- 공통 6개: mechanics, decision making, map awareness, positioning, combat execution, consistency
- laner 6개: farming, trading, wave management, lane pressure, priority conversion, side lane
- jungle 6개: pathing, jungle resources, enemy tracking, lane intervention, objective decision/secure
- support 6개: vision, lane support, rotation, engage, ally protection, area setup

책임을 다음처럼 구분한다.

- `DECISION_MAKING`은 take/reset/contest, conversion, rotation 같은 판단 quality다.
- mechanics/combat/trading/engage/secure 등 execution 성격의 rating은 실제 수행 능력이다.
- `FARMING`은 TOP/MID/ADC의 자원 수급이며, JUNGLE은 `JUNGLE_RESOURCE_MANAGEMENT`를 사용한다.
- `CONSISTENCY`는 match-scoped realization의 변동 폭을 제한하며 별도 전투 bonus로 더해지지 않는다.

`PlayerSkillEvaluator`는 각 gameplay score에 하나의 primary rating과 제한된 supporting rating만 조합해 이 의미가 서로 대체되지 않게 한다.

## Champion Proficiency

`ChampionProficiencies`는 특정 roster position이 특정 `ChampionRoleKey`를 수행하는 숙련도다. 값 범위는 1..20이고, key가 없으면 neutral 14를 반환한다.

Proficiency는 Player Ratings와 분리된다.

- Player Ratings: champion을 바꿔도 유지되는 일반/role 능력
- Champion Proficiency: player × champion × selected role 적합도
- Champion Power: player와 무관한 champion kit/time curve

match runtime에서는 proficiency가 `PlayerMatchPerformance.execution(...)` 경로에만 보정된다. decision quality, map awareness, farming 같은 pure cognitive/economy read에는 proficiency를 더하지 않는다. 이 결정은 [ADR-003](../adr/ADR-003-player-rating-vs-champion-proficiency.md)에 정리되어 있다.

## Runtime Integration

### Match simulation

Explicit rating 기반 `Player`는 match 초기화 때 다음 흐름을 거친다.

```text
PlayerRatings + selected ChampionRoleKey proficiency
  → PlayerMatchPerformance.realize(seed, side, position)
  → PlayerState
  → PlayerSkillEvaluator
```

- consistency가 match별 rating realization spread를 조절한다.
- FARM은 `PositionEconomyResolver`에서 `FARMING` 또는 jungle resource rating을 사용한다.
- lane/gank/roam/combat은 execution, trading, pressure, intervention 등의 분리된 evaluator를 사용한다.
- objective/macro는 objective decision, secure, vision, side-lane, rotation 같은 rating을 사용한다.

현재 `MatchController`는 `DummyDataFactory`의 legacy `PlayerAttributes` team을 사용한다. 따라서 active LCK Player Ratings resource와 explicit proficiency는 HTTP simulation에 적용되지 않는다.

### Draft

`DraftTeamContext`는 roster의 position별 `ChampionProficiencies`만 추출한다. `RoleAssignmentSolver`, Pick/Ban evaluation, PreDraft planning이 이를 player fit으로 사용한다. 현재 DraftEngine은 Player Ratings를 읽지 않는다.

### Catalog

`PlayerRatingCatalog`는 Spring `@Component`이며 approved resource를 startup에 로드하고 immutable lookup을 제공한다. `createPlayer`는 proficiency를 명시적으로 전달해야 하므로 rating과 proficiency를 암묵적으로 합치지 않는다.

## Data Source

현재 production player rating source는 다음 하나다.

- `backend/src/main/resources/players/lck-player-ratings-2026-08-19-v1.json`
- version `lck-player-ratings-2026-08-19-v1`
- 10 teams × 5 starters = 50 player profiles
- player당 공통 6 + role-specific 6 = 12 ratings, 총 600 authored values
- substitutes 미포함
- loader에 pinned SHA-256 적용

세부 field와 lookup contract는 [Player Data Schema](../reference/player-data-schema.md)를 참고한다.

## Missing / Pending Data

- Champion Proficiency의 domain/runtime/draft fallback은 구현되어 있으나 production JSON resource와 loader는 없다.
- 따라서 authored proficiency entry는 0이고 현재 lookup은 모두 neutral 14다.
- Player Rating catalog는 구현·검증되지만 현재 MatchController의 roster source가 아니다.
- Draft/API/frontend에는 production roster 선택과 player data 노출 경로가 없다.

이 항목들은 구현 완료로 오해하지 않도록 [Project Status](../project-status.md)의 Partial/Pending에도 기록한다.
