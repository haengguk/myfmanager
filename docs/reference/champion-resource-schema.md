# Champion Resource Schema

이 문서는 active JSON과 loader/record validation을 함께 설명한다. JSON 파일 자체보다 `ChampionResourceManifest`, 각 catalog loader, `ChampionResourceCompletenessValidator`의 동작이 최종 runtime contract다.

## Manifest

기본 bootstrap은 `backend/src/main/resources/champions/champion-resource-manifest.json`이다.

| Field | 필수 | 의미 / validation |
| --- | --- | --- |
| `manifestVersion` | 예 | 공백 불가. coherent resource set의 identity |
| `catalog` | 예 | `/champions/`로 시작하고 `.json`으로 끝나는 classpath path |
| `power` | 예 | 동일 path 규칙 |
| `matchup` | 예 | 동일 path 규칙 |
| `composition` | 예 | 동일 path 규칙 |
| `jungleClear` | 예 | 동일 path 규칙 |

```json
{
  "manifestVersion": "example-resource-set-v1",
  "catalog": "/champions/champion-pool-example-v1.json",
  "power": "/champions/champion-power-example-v1.json",
  "matchup": "/champions/champion-matchup-example-v1.json",
  "composition": "/champions/champion-composition-example-v1.json",
  "jungleClear": "/champions/champion-jungle-clear-example-v1.json"
}
```

Manifest는 파일 존재 여부와 path 형식을 검증하고, 각 resource의 내부 version/coverage 일관성은 loader와 `ChampionResourceSet`이 검증한다.

## Champion Catalog

### Envelope

| Field | 필수 | 의미 / validation |
| --- | --- | --- |
| `championPoolVersion` | 예 | 다른 champion/draft resource가 요구하는 pool identity |
| `championBalanceVersion` | 예 | catalog response에 노출되는 balance identity |
| `riotDataVersion` | 예 | Data Dragon portrait URL 조립에 사용 |
| `defaultSelection` | 아니오 | request가 champion selection을 생략할 때 쓰는 양 팀 5-role selection |
| `champions` | 예 | 비어 있을 수 없음 |

### Champion record

| Field | 필수 | 의미 / validation |
| --- | --- | --- |
| `id` | 예 | `ChampionId`; trim/lowercase 후 `[a-z0-9]+(-[a-z0-9]+)*` |
| `displayNameKo`, `displayNameEn` | 예 | 표시명, 공백 불가 |
| `riotAssetId` | 예 | portrait asset identity, catalog 내 unique |
| `primaryPosition` | 예 | `Position`; 반드시 supported set에 포함 |
| `supportedPositions` | 예 | 비어 있지 않고 champion 내부 중복 없음 |

ChampionId와 riotAssetId는 catalog 내 unique다. legal role은 모든 `(id, supportedPosition)` 조합이다.

```json
{
  "championPoolVersion": "example-pool-v1",
  "championBalanceVersion": "example-power-v1",
  "riotDataVersion": "16.15.1",
  "champions": [
    {
      "id": "example-champion",
      "displayNameKo": "예시 챔피언",
      "displayNameEn": "Example Champion",
      "riotAssetId": "ExampleChampion",
      "primaryPosition": "MID",
      "supportedPositions": ["MID", "SUPPORT"]
    }
  ]
}
```

## Champion Power

### Envelope

| Field | 필수 | 의미 / validation |
| --- | --- | --- |
| `profileVersion` | 예 | power profile identity |
| `requiredChampionPoolVersion` | 예 | Catalog `championPoolVersion`과 정확히 같아야 함 |
| `levelCurves` | 예 | named curve map; 각 curve는 level 1/6/11/16/18 anchor를 정확히 보유 |
| `itemCurves` | 예 | named curve map; 모든 `ItemProgressStage` 값을 보유 |
| `championProfiles` | 예 | Catalog champion과 1:1 |

### Champion profile

| Field | 필수 | 의미 / validation |
| --- | --- | --- |
| `championId` | 예 | Catalog에 존재해야 하고 중복 불가 |
| `levelCurveId` | 예 | `levelCurves`의 key |
| `itemCurveId` | 예 | `itemCurves`의 key |
| `contexts` | 예 | 모든 `ProgressionCombatContext`를 정확히 한 번 포함 |
| `tags` | 예 | `ChampionTag` set |
| `levelAnchors` | 아니오 | 공유 level curve의 일부 anchor override |
| `itemAnchors` | 아니오 | 공유 item curve의 일부 stage override |

Level curve anchor는 finite여야 하며 level 사이 값은 선형 보간된다. Item/context modifier와 item override는 -0.50..0.50 범위다. 최종 player Champion Power와 team edge에는 별도 clamp가 적용된다.

```json
{
  "profileVersion": "example-power-v1",
  "requiredChampionPoolVersion": "example-pool-v1",
  "levelCurves": {
    "BALANCED": {"1": 0.0, "6": 0.04, "11": 0.08, "16": 0.08, "18": 0.06}
  },
  "itemCurves": {
    "BALANCED": {
      "STARTING": 0.0,
      "COMPONENT": 0.0,
      "FIRST_CORE": 0.02,
      "SECOND_CORE": 0.04,
      "THIRD_CORE": 0.04,
      "FOURTH_CORE": 0.02,
      "FULL_BUILD": 0.0
    }
  },
  "championProfiles": [
    {
      "championId": "example-champion",
      "levelCurveId": "BALANCED",
      "itemCurveId": "BALANCED",
      "contexts": {
        "LANE_COMBAT": 0.05,
        "JUNGLE_GANK": 0.0,
        "COUNTER_GANK": 0.0,
        "ROAM": 0.02,
        "GENERIC_SKIRMISH": 0.03,
        "TEAMFIGHT": 0.04,
        "OBJECTIVE_FIGHT": 0.03,
        "LATE_GAME_SIEGE": 0.02,
        "BASE_DEFENSE": 0.02
      },
      "tags": ["TEAMFIGHT"]
    }
  ]
}
```

## Matchup

### Envelope and profile

| Field | 필수 | 의미 / validation |
| --- | --- | --- |
| `profileVersion` | 예 | materialized catalog version |
| `requiredChampionPoolVersion` | 예 | Catalog pool version과 일치 |
| `championProfiles` | 예 | 모든 catalog champion을 정확히 한 번 작성 |
| `championId` | 예 | known champion, 중복 불가 |
| `baseTraits` | 예 | 15 `ChampionMatchupTrait`, 각각 1..20 |
| `roleOverrides` | 아니오 | supported role만 허용; role 중복 불가; `traits`는 base의 일부를 덮어쓸 수 있음 |
| `primaryStrengthTraits`, `primaryWeaknessTraits` | 아니오 | authoring rationale metadata; materialized runtime profile에는 보존되지 않음 |
| `kitInteractionSummary`, `profileSource` | 아니오 | authoring metadata; runtime edge 계산에 사용되지 않음 |

15 traits는 range/poke/burst/sustained damage, mobility/gap close, CC/engage/disengage, sustain/durability/wave control, pick/anti-dive/anti-tank다. Loader는 각 supported position에 base + override를 적용한 뒤 모든 trait가 1..20인지 다시 확인한다.

```json
{
  "profileVersion": "example-matchup-v1",
  "requiredChampionPoolVersion": "example-pool-v1",
  "championProfiles": [
    {
      "championId": "example-champion",
      "baseTraits": {
        "RANGE_CONTROL": 12,
        "POKE": 13,
        "BURST": 14,
        "SUSTAINED_DAMAGE": 10,
        "MOBILITY": 15,
        "GAP_CLOSE": 11,
        "CROWD_CONTROL": 12,
        "ENGAGE": 9,
        "DISENGAGE": 14,
        "SUSTAIN": 8,
        "DURABILITY": 7,
        "WAVE_CONTROL": 15,
        "PICK": 13,
        "ANTI_DIVE": 10,
        "ANTI_TANK": 6
      },
      "roleOverrides": [
        {"position": "SUPPORT", "traits": {"CROWD_CONTROL": 15, "ENGAGE": 13}}
      ]
    }
  ]
}
```

Matchup은 pairwise matrix나 win-rate table을 요구하지 않는다. `GEOMETRIC_V2`가 두 role profile을 runtime에 조합한다.

## Composition

### Envelope and profile

| Field | 필수 | 의미 / validation |
| --- | --- | --- |
| `profileVersion` | 예 | materialized catalog version |
| `requiredChampionPoolVersion` | 예 | Catalog pool version과 일치 |
| `championProfiles` | 예 | 모든 catalog champion을 정확히 한 번 작성 |
| `championId` | 예 | known champion, 중복 불가 |
| `baseCapabilities` | 예 | 15 capabilities, authored value 각각 1..20 |
| `damageProfile` | 예 | physical/magic/true threat 각각 0..20 |
| `roleOverrides` | 아니오 | supported position만 허용; capability 일부와/or damage profile override |

```json
{
  "profileVersion": "example-composition-v1",
  "requiredChampionPoolVersion": "example-pool-v1",
  "championProfiles": [
    {
      "championId": "example-champion",
      "baseCapabilities": {
        "ENGAGE": 9,
        "FOLLOW_UP": 13,
        "DISENGAGE": 14,
        "FRONTLINE": 6,
        "PEEL": 12,
        "PICK": 14,
        "POKE": 13,
        "SIEGE": 12,
        "WAVE_CLEAR": 15,
        "ZONE_CONTROL": 14,
        "SIDE_LANE_PRESSURE": 9,
        "OBJECTIVE_DAMAGE": 10,
        "SUSTAINED_DAMAGE": 11,
        "BURST_DAMAGE": 15,
        "BACKLINE_ACCESS": 12
      },
      "damageProfile": {"physicalThreat": 2, "magicThreat": 18, "trueDamageThreat": 0},
      "roleOverrides": [
        {"position": "SUPPORT", "capabilities": {"PEEL": 16, "ENGAGE": 12}}
      ]
    }
  ]
}
```

### External vs internal range

- Active JSON loader의 authored capability range는 1..20이다.
- `ChampionCompositionProfile` domain constructor는 synthetic/internal neutral fixture를 위해 0..20을 허용한다.
- `DamageChannelProfile`은 external/internal 모두 0..20이다.
- `TeamCompositionAggregator`는 raw capability를 20으로 나누고 aggregation policy를 적용해 0..1 coverage를 만든다.

따라서 internal 0..1 coverage를 JSON의 1..20 authored 값으로 다시 저장하거나, synthetic 0 capability를 production authoring 규칙으로 오해하면 안 된다.

## Jungle Clear

| Field | 필수 | 의미 / validation |
| --- | --- | --- |
| `profileVersion` | 예 | non-blank catalog identity로 보존 |
| `requiredChampionPoolVersion` | 예 | Catalog pool version과 일치 |
| `profiles` | 예 | legal JUNGLE `ChampionRoleKey`와 정확히 일치 |
| `championId` | 예 | JUNGLE을 support하는 known champion |
| `position` | 예 | 반드시 `JUNGLE` |
| `early`, `mid`, `late` | 예 | finite 0..2 |
| `gameplayEnabled` | 예 | false이면 evaluator result는 authored 값과 무관하게 1.0; `ECONOMY_V1` selected profile은 true여야 함 |

```json
{
  "profileVersion": "example-jungle-clear-v1",
  "requiredChampionPoolVersion": "example-pool-v1",
  "profiles": [
    {
      "championId": "example-jungler",
      "position": "JUNGLE",
      "early": 1.04,
      "mid": 1.06,
      "late": 1.02,
      "gameplayEnabled": true
    }
  ]
}
```

## Version Contract

- Catalog의 `championPoolVersion`이 cross-resource parent identity다.
- Power, Matchup, Composition, Jungle Clear의 `requiredChampionPoolVersion`은 parent와 정확히 같아야 한다.
- Manifest는 함께 배포할 파일을 선택한다. 파일명만 바꾸고 내부 required version을 그대로 두는 것은 충분하지 않다.
- Draft Meta는 manifest 밖의 별도 resource지만 동일 pool version, legal-role count와 canonical role-key hash를 검증한다.
- historical 30-profile hash와 active full resource version은 scope가 다르다. [Champion System](../architecture/champion-system.md#historical-frozen-resources)을 참고한다.

## Completeness Contract

`ChampionResourceCompletenessValidator`는 set equality를 사용한다.

- missing profile: 실패
- extra profile: 실패
- unknown champion: 각 loader에서 실패
- unsupported role override: 실패
- duplicate champion/role: 실패
- incomplete trait/capability/context/item vector: 실패
- JUNGLE을 지원하지 않는 Jungle Clear profile: 실패

silent neutral fallback은 Jungle Clear의 명시적 `gameplayEnabled: false` 평가 결과에만 존재한다. resource 누락을 neutral로 대체하지 않는다.

현재 active economy resource는 51개 JUNGLE profile을 모두 enable한다. 이것만으로 모든 runtime이 clear 값을 읽는 것은 아니며, `SimulationGameplayConfiguration.jungleClearContribution=ECONOMY_V1`인 explicit profile만 unified CS/gold/XP path를 실행한다.
