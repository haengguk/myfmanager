# Champion System

## Champion Identity

`ChampionId`는 lowercase kebab-case로 정규화되는 안정된 domain value다. 한 champion의 role별 identity는 `ChampionRoleKey(ChampionId, Position)`이며 `stableId()`는 `champion-id:POSITION` 형식이다.

- `primaryPosition`은 catalog/UI의 기본 분류다.
- `supportedPositions`는 실제 legal role 집합이다.
- flex champion은 둘 이상의 supported position을 가진다.
- `ChampionAssignment`는 `PlayerKey`, champion, selected position을 함께 보유하고 position 일치를 검증한다.
- display name, Riot asset name, 배열 index는 gameplay identity가 아니다.

## Resource Architecture

```text
champion-resource-manifest.json
  ├─ Champion Catalog
  ├─ Champion Power
  ├─ Champion Matchup
  ├─ Champion Composition
  └─ Champion Jungle Clear
          ↓
ChampionResourceSet
          ↓
exact cross-catalog completeness validation
```

`ChampionResourceManifest`는 `/champions/*.json` 경로만 허용한다. `ChampionResourceSet.loadDefault()`는 manifest가 선택한 resource를 전부 materialize한 뒤 `ChampionResourceCompletenessValidator`로 다음을 확인한다.

- Power champion set = Catalog champion set
- Matchup role keys = Catalog의 모든 legal role keys
- Composition role keys = Catalog의 모든 legal role keys
- Jungle Clear role keys = legal role keys 중 `Position.JUNGLE`

missing뿐 아니라 extra와 unsupported role도 실패한다. 세부 JSON 계약은 [Champion Resource Schema](../reference/champion-resource-schema.md)에 있다.

## Active Resource Snapshot

2026-08-22 working tree의 manifest가 선택한 snapshot이다. 변경 가능 수치의 기준 문서는 [Project Status](../project-status.md)다.

| 항목 | 현재 값 |
| --- | --- |
| Manifest | `full-173-resource-set-2026-08-v2` |
| Champion Catalog | `full-173-2026-08-v1` / 173 champions / 216 legal roles |
| Role counts | TOP 54, JUNGLE 51, MID 45, ADC 31, SUPPORT 35 |
| Flex champions | 36 |
| Champion Power | `full-173-power-2026-08-v1` / 173 profiles |
| Matchup | `full-173-role-matchup-profile-2026-08-v2` / 216 materialized role profiles |
| Composition | `full-173-composition-profile-2026-08-v2` / 216 materialized role profiles |
| Jungle Clear | `full-173-jungle-clear-economy-2026-08-v1` / 51 profiles / 51 enabled |

Resource가 active manifest에 포함된 것과 simulation contribution이 활성화된 것은 다르다. 현재 Spring HTTP simulator는 Champion Power만 켜고 Matchup/Composition mode는 `OFF`다. Draft engine은 active Matchup과 Composition resource를 직접 평가에 사용한다.

## Champion Power

Champion Power는 champion 자체의 시간대/아이템/상황별 baseline을 표현한다.

- `levelCurveId`: level 1/6/11/16/18 anchor와 level 사이 선형 보간
- `itemCurveId`: `ItemProgressStage`별 modifier
- `contexts`: 모든 `ProgressionCombatContext` modifier
- optional `levelAnchors`/`itemAnchors`: 공유 curve를 champion 단위로 덮어쓰기
- `tags`: 설명/API metadata

`ChampionPowerProfileEvaluator`는 level + item + context를 합산하고 player-level clamp를 적용한다. team combat에서는 실제 참가자의 값을 평균해 상대 team과의 edge를 구한다. 현재 gold, XP, level, item stage를 직접 소유하는 것은 Progression이며, Champion Power는 그 상태를 해석하는 champion별 curve만 소유한다.

## Matchup

Matchup은 “현재 승률”이나 patch tier가 아니라, 같은 position의 두 kit가 어떻게 상호작용하는지를 모델링한다. authored profile은 15개 trait를 1..20으로 가진다.

`GEOMETRIC_V2`의 핵심 흐름은 다음과 같다.

1. trait를 1..20에서 0..1로 정규화한다.
2. rule별 source capability와 opponent exposure를 geometric interaction으로 결합한다.
3. forward와 reverse의 차이를 사용해 antisymmetric edge를 만든다.
4. combat context별 rule weight/intensity와 frozen gain을 적용한다.
5. final edge를 -0.30..0.30으로 제한한다.

같은 `Position` pair만 계산하며 pairwise matchup matrix를 resource로 저장하지 않는다. `ChampionMatchupResolver`는 실제 참가자를 `PlayerKey`와 assignment로 매칭하고, context/stage/source/opponent가 같은 application을 중복 적용하지 않는다.

Spring HTTP simulator의 mode는 현재 `OFF`지만 `DraftMatchupEvaluator`는 active role profile과 `GEOMETRIC_V2`를 사용한다.

## Composition

Composition은 champion 한 명의 절대 power가 아니라 5인 team에 제공하는 기능을 표현한다.

- 15개 `CompositionCapability`: engage, follow-up, disengage, frontline, peel, pick, poke, siege, wave clear, zone control, side lane, objective damage, sustained/burst damage, backline access
- `DamageChannelProfile`: physical/magic/true-damage threat
- `TeamCompositionAggregator`: contributor 순위와 capability별 aggregation policy로 0..1 coverage 생성
- `TeamCompositionAnalyzer`: pattern과 deficiency, contributor explanation 생성
- `CompositionInteractionEvaluator`: 두 lineup의 context별 signed edge 생성

`PRODUCTION_V2`에서는 frozen policy identity를 검증한 뒤 composition edge를 Skirmish, Teamfight, Siege, Base Defense의 winner-decision channel에 적용한다. severity gain은 별도이며 현재 frozen production candidate에서는 0이다. Champion Power나 Matchup 점수에 composition capability를 다시 더하지 않는다.

현재 active profile 전체를 분석하지만 일부 frozen policy hash는 역사적 30-profile oracle의 identity를 나타낸다. 둘의 범위는 [Historical Frozen Resources](#historical-frozen-resources)에서 구분한다.

## Flex Materialization

Matchup과 Composition resource는 champion마다 공통 `baseTraits`/`baseCapabilities`를 작성하고 `roleOverrides`로 position별 차이만 덮어쓴다.

1. Catalog의 `supportedPositions`를 정렬한다.
2. 각 position에 base vector를 복사한다.
3. 해당 role override의 일부 또는 전체 field를 덮어쓴다.
4. 완전한 `ChampionRoleKey` profile을 생성한다.
5. 최종 key 집합이 catalog legal-role set과 정확히 같은지 확인한다.

따라서 flex role을 추가하면 Catalog만 바꾸는 것으로 끝나지 않는다. Matchup, Composition, 조건부 Jungle Clear, Draft Meta의 legal-role coverage도 함께 갱신해야 한다.

## Jungle Clear

Jungle Clear는 Power, gank/pathing, Player Ratings와 분리된 phase foundation이다. JUNGLE role마다 `early`, `mid`, `late` 값(0..2)과 `gameplayEnabled`를 가진다.

현재 versioned active resource의 51개 profile은 모두 `gameplayEnabled: true`다. `ChampionJungleClearProfileCatalog`는 `profileVersion`을 identity로 보존하고, `ChampionJungleClearEvaluator`는 900초/1,800초 경계에서 early/mid/late 값을 결정론적으로 선택한다. 과거 `champion-jungle-clear-full-173-v1.json`은 disabled historical resource로 보존했다.

Resource activation과 runtime activation은 별개다. 기존 세 profile, Spring HTTP path, `SimulationOptions.productionDefaults()`는 `DISABLED_NOT_INTEGRATED`라서 clear data를 gameplay branch나 Random consumption에 사용하지 않는다. `FULL_SYSTEM_WITH_JUNGLE_ECONOMY_CANDIDATE_V1`만 `ECONOMY_V1`을 선택하며, 기존 position-economy player 순서에서 JUNGLE draw를 unified resolver로 넘긴다.

V1-A 계산은 `Champion Clear × pure Jungle Resource Management`다. Candidate 전용 player multiplier는 `PATHING`을 읽지 않으며, 기존 Jungle-OFF position-economy의 `JRM 80% + PATHING 20%` realization은 parity를 위해 그대로 보존한다. 결과는 하나의 `JungleEconomyOutcome`으로 CS, actual-CS FARM gold, XP를 함께 소유한다. 사망, FARM recovery/macro block, non-default activity, gank/counter-gank FARM block은 outcome과 Random을 만들지 않는다. Clear는 아직 readiness/tempo, gank 또는 objective eligibility에 연결하지 않았다.

## Historical Frozen Resources

`initial-30` resource들은 active manifest가 선택하는 dataset이 아니라 expansion regression용 historical oracle이다.

- `ChampionFullPopulationIntegrationTest`는 initial 30의 catalog/power/matchup/composition과 historical jungle subset이 active full resource에서 그대로 보존되는지 확인한다.
- `ChampionMatchupProductionPolicy.PROFILE_VERSION/PROFILE_HASH`는 initial-30 frozen candidate의 semantic identity다. active Matchup catalog version `full-173-...` 전체의 hash를 뜻하지 않는다.
- `FrozenCompositionInteractionRuntimePolicy.PROFILE_HASH`도 historical 30-profile canonical identity다.
- active full Composition은 별도 canonical serialization/hash를 가지며 현재 216 role 전체를 대상으로 한다.

따라서 historical hash가 유지되었다고 해서 새로 추가된 143 champions/추가 flex roles 전체가 동일하다는 뜻은 아니다. active full identity는 manifest version, catalog version, exact coverage, full Composition hash 같은 별도 contract로 검증한다. 이 구분의 결정 기록은 [ADR-002](../adr/ADR-002-historical-frozen-vs-active-resource-identity.md)에 있다.
