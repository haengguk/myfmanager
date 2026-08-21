# Player Data Schema

## PlayerRatingKey

`PlayerRatingKey(teamCode, Position)`이 authored roster의 stable identity다.

- `teamCode`는 trim 후 uppercase로 정규화하며 공백일 수 없다.
- `position`은 TOP/JUNGLE/MID/ADC/SUPPORT 중 하나다.
- `stableId()`는 `TEAM:POSITION`이다.
- nickname은 표시 metadata이며 identity에 포함되지 않는다.

## Player Ratings Resource

현재 production resource는 `backend/src/main/resources/players/lck-player-ratings-2026-08-19-v1.json` 하나다.

### Envelope

| Field | 필수 | 현재 loader contract |
| --- | --- | --- |
| `version` | 예 | `lck-player-ratings-2026-08-19-v1`과 exact match |
| `snapshotAt` | resource에 존재 | metadata; 현재 loader validation/return value에는 포함되지 않음 |
| `dataCutoff` | resource에 존재 | metadata; 현재 loader validation/return value에는 포함되지 않음 |
| `scale.min/max` | 예 | 정확히 1/20 |
| `scope.league` | 예 | `LCK` |
| `scope.teams` | 예 | 10 |
| `scope.startersPerTeam` | 예 | 5 |
| `scope.players` | 예 | 50 |
| `scope.substitutesIncluded` | 예 | false |
| `semantics` | 예 | common 6, role-specific 6, active 12, proficiency separate, Display CA runtime 제외 |
| `players` | 예 | 정확히 50 records |

Loader는 raw resource bytes의 SHA-256도 pinned constant와 비교한다. 값이나 formatting까지 바뀌면 explicit version/hash update 없이 startup이 실패한다.

### Player record

| Field | 필수 | 의미 / validation |
| --- | --- | --- |
| `team` | 예 | `PlayerRatingKey.teamCode` |
| `nickname` | 예 | 공백이 아닌 표시명 |
| `position` | 예 | structured role identity |
| `ratings` | 예 | 해당 position에 적용되는 key 12개와 정확히 일치, 값 1..20 |

공통 JSON keys는 `mechanics`, `decisionMaking`, `mapAwareness`, `positioning`, `combatExecution`, `consistency`다.

| Role group | Role-specific JSON keys |
| --- | --- |
| TOP/MID/ADC | `csAcquisition`, `trading`, `waveManagement`, `lanePressure`, `initiativeConversion`, `sideLaneManagement` |
| JUNGLE | `pathing`, `jungleResourceManagement`, `enemyJungleTracking`, `laneIntervention`, `objectiveDecision`, `objectiveSecuring` |
| SUPPORT | `visionControl`, `laneSupport`, `roamCoordination`, `engage`, `allyProtection`, `zoneSetup` |

작은 player record 예시:

```json
{
  "team": "EXM",
  "nickname": "Example",
  "position": "MID",
  "ratings": {
    "mechanics": 16,
    "decisionMaking": 15,
    "mapAwareness": 15,
    "positioning": 16,
    "combatExecution": 16,
    "consistency": 15,
    "csAcquisition": 17,
    "trading": 16,
    "waveManagement": 15,
    "lanePressure": 16,
    "initiativeConversion": 15,
    "sideLaneManagement": 14
  }
}
```

같은 team-position이 두 번 나오거나 한 team에 five-position starter set이 완성되지 않으면 실패한다. 같은 nickname 자체는 identity validation 기준이 아니다.

## Champion Proficiency Resource

현재 production Champion Proficiency JSON resource와 loader는 없다. authored count는 0이다.

구현된 domain contract는 `ChampionProficiencies`의 immutable `Map<ChampionRoleKey, Integer>`다.

- key: `ChampionId + Position`
- value: 1..20
- missing key: neutral 14
- `PlayerRatingCatalog.createPlayer` 호출 시 proficiency object를 명시적으로 전달해야 함

외부 resource의 version, roster key 연결 방식, completeness 정책은 아직 정의되지 않았으므로 이 문서는 존재하지 않는 JSON schema를 가정하지 않는다.

## Lookup Contract

### Player Ratings

- `PlayerRatingCatalog.find(key)`: unknown key면 empty `Optional`
- `PlayerRatingCatalog.get(key)` / `ratings(key)`: unknown key면 `IllegalArgumentException`
- loader 단계에서는 exact 50-player roster가 아니면 전체 resource load 실패
- silent neutral Player Rating fallback은 catalog lookup에 없다

### Champion Proficiency

- `ChampionProficiencies.get(ChampionRoleKey)`: missing이면 14
- 이 fallback은 “authored data가 있다”는 뜻이 아니라 pending data에 대한 명시적 neutral behavior다.
- MatchSimulator는 selected assignment의 role key로 proficiency를 조회한다.
- DraftTeamContext도 position별 proficiency map을 사용하며 missing position/map entry를 neutral로 채운다.

## Separation Contract

- Player Rating은 `PlayerRatingKey`로 찾고 player의 일반 능력을 나타낸다.
- Champion Proficiency는 `ChampionRoleKey`별 player-specific 값이다.
- proficiency를 rating resource의 `mechanics`, `decisionMaking`, `FARMING`에 미리 합산하지 않는다.
- runtime에서 proficiency는 champion-tool execution read를 통해서만 보정한다.
- Draft의 `PLAYER_FIT`은 현재 proficiency이며 Player Ratings가 아니다.
- Champion Power/Matchup/Composition resource에 player data를 넣지 않는다.

Runtime 사용 위치와 현재 wiring은 [Player System](../architecture/player-system.md)을 참고한다.
