# Player Data Schema

## Identity Types

### PlayerId

`PlayerId`는 선수 개인의 immutable identity다.

- JSON form: canonical string, 예: `"player-chovy"`
- accepted format: `player-[a-z0-9]+(?:-[a-z0-9]+)*`
- blank, uppercase, underscore, malformed slug는 거부한다.
- runtime nickname/team/position derivation과 random UUID 생성은 없다.
- team/position/nickname이 바뀌어도 authored ID 자체는 유지하는 계약이다.

### PlayerRatingKey

`PlayerRatingKey(teamCode, Position)`은 current authored roster snapshot의 slot identity다.

- `teamCode`는 trim 후 uppercase로 정규화한다.
- `position`은 TOP/JUNGLE/MID/ADC/SUPPORT 중 하나다.
- `stableId()`는 `TEAM:POSITION`이다.
- nickname은 display consistency metadata이며 lookup key가 아니다.

### PlayerKey

`PlayerKey(TeamSide, Position)`은 한 match 안의 slot identity다. `MatchChampionAssignments`와 `TeamState` lookup에서 사용하며 `PlayerId`나 `PlayerRatingKey`를 대체하지 않는다.

## Player Identity Resource

Production resource:

- path: `backend/src/main/resources/players/lck-player-identities-2026-08-21-v1.json`
- version: `lck-player-identities-2026-08-21-v1`
- required rating version: `lck-player-ratings-2026-08-19-v1`
- SHA-256: `badbbaa3ae7fbe5eaaf83ee8e97a93134476493a45167ec3d1637c7243909018`

Record schema:

```json
{
  "playerId": "player-chovy",
  "team": "GEN",
  "position": "MID",
  "nickname": "Chovy"
}
```

Loader invariants:

- records 50, unique `PlayerId` 50, unique `PlayerRatingKey` 50
- teams 10, team별 five positions exact
- rating subject set exact equality
- rating nickname exact display consistency
- duplicate/missing/unknown/malformed identity fail-fast
- raw SHA 검증 후 semantic parse

`PlayerIdentityCatalog`은 한 record list에서 `PlayerId`와 `PlayerRatingKey` read-only index를 파생한다.

## Player Ratings Resource

Production resource:

- path: `backend/src/main/resources/players/lck-player-ratings-2026-08-19-v1.json`
- version: `lck-player-ratings-2026-08-19-v1`
- SHA-256: `2312a8bc7d222fd63b57d1255210fb25104432a90a954d854b2090cc2acb28e0`
- scope: LCK 10 teams × 5 starters, substitutes false
- authored values: 50 × 12 = 600

Player record는 `team`, `nickname`, `position`, position별 exact 12-key `ratings`를 가진다. 공통 key 6개와 role-specific key 6개, 값 1..20을 검증한다.

`PlayerRatingCatalog` lookup:

- 기존 `find/get/ratings(PlayerRatingKey)` 유지
- `find/get/ratings(PlayerId)` additive 제공
- `playerId(PlayerRatingKey)`와 `currentRatingKey(PlayerId)` 제공
- unknown subject는 empty `Optional` 또는 명시적 exception이며 silent neutral rating fallback은 없다.

## Champion Proficiency Resource

Production resource:

- path: `backend/src/main/resources/players/lck-champion-proficiency-2026-08-21-v1.json`
- version: `lck-champion-proficiency-2026-08-21-v1`
- research as of: `2026-08-21`
- SHA-256: `2c36b8a109aba9dfe84c1da319fe02708a72a1341d334dc6d5e3f605b0023aad`
- required rating version: `lck-player-ratings-2026-08-19-v1`
- required champion pool: `full-173-2026-08-v1`
- required legal role count: 216

Source player record:

```json
{
  "team": "GEN",
  "nickname": "Chovy",
  "position": "MID",
  "proficiencies": [
    { "championId": "azir", "position": "MID", "value": 20 }
  ]
}
```

Source ownership은 `PlayerRatingKey × ChampionRoleKey`다. Loader가 `PlayerIdentityCatalog`를 통해 runtime canonical ownership `PlayerId × ChampionRoleKey`로 변환한다. 원본 JSON에는 playerId를 추가하거나 재직렬화하지 않는다.

Measured v1 snapshot:

| Metric | Value |
| --- | ---: |
| Teams / players | 10 / 50 |
| Legal role keys | 216 |
| Potential player-role keys | 2,160 |
| Authored overrides | 732 |
| Neutral fallback keys | 1,428 |
| 15 / 16 / 17 / 18 / 19 / 20 | 35 / 160 / 228 / 210 / 81 / 18 |
| 17+ / 19+ / 20 | 537 / 99 / 18 |

Semantic rejection:

- wrong version/research/prerequisite/legal-role count
- rating/identity/proficiency subject set mismatch
- duplicate `PlayerRatingKey` 또는 duplicate subject-role key
- unknown/illegal `ChampionRoleKey`
- subject position과 role position 불일치
- 값 1..20 밖, 또는 v1 authored band 15..20 밖
- declared summary와 measured entry counts/distribution 불일치

## Sparse Lookup Contract

`ChampionProficiencyCatalog.value(playerId, roleKey)`는 순서를 지켜 검증한다.

1. known `PlayerId`
2. current roster position과 `ChampionRoleKey.position` 일치
3. active Champion Catalog가 role key를 지원
4. authored override가 있으면 15..20 값 반환
5. 위 조건을 모두 만족한 omitted legal key만 `ChampionProficiencies.NEUTRAL` 14 반환

따라서 unknown player, illegal role, cross-position role은 14가 아니라 exception이다. Profile은 authored entry만 저장하며 dense 2,160-key materialization을 하지 않는다.

## Production Binding Contract

`LckTeamAssembler.assemblePlayer(runtimePlayerId, ratingKey, proficiencyOwnerId)`는 실제 catalog object를 통해 다음을 검사한다.

- `ratingKey → PlayerId`가 runtime `PlayerId`와 같지 않으면 `PLAYER_ID_RATING_KEY_MISMATCH`
- runtime `PlayerId`와 proficiency owner가 다르면 `PROFICIENCY_BINDING_MISMATCH`
- subject position과 requested role position이 다르면 `INVALID_SUBJECT_ROLE_BINDING`

Matching binding 뒤 `Player`는 stable ID, display name, 12 ratings, sparse proficiency profile을 가진다.

## Runtime/Event Schema

`PlayerState`는 다음을 분리해 보유한다.

- `PlayerKey`: match slot
- `PlayerId`: stable person
- `playerName`: display
- `Position`, realized performance, mutable match stats

`TeamState.player(PlayerKey)`와 `playerAt(Position)`이 gameplay lookup이다. production main source에는 `getPlayerState(String)` name lookup이 없다.

`MatchEvent`는 기존 display fields를 유지하면서 다음 additive fields를 제공한다.

- `killerPlayerId`
- `victimPlayerId`
- `assistPlayerIds`

Lane/gank/counter-gank/roam structured data의 `*PlayerId`도 동일한 stable ID를 사용한다. Legacy isolated test state만 explicit stable ID와 `PlayerKey`가 모두 없을 때 이름이 아닌 `LEGACY:POSITION` diagnostic identity를 사용한다. 실제 LCK와 HTTP dummy match path는 explicit ID를 가진다.

## Layer Separation

- Player Ratings를 proficiency로 계산하지 않는다.
- proficiency로 Player Ratings, `FARMING`, `DECISION_MAKING`을 덮어쓰지 않는다.
- proficiency는 champion-tool execution read에만 적용한다.
- Champion Power/Matchup/Composition/Draft Meta에 player data를 넣지 않는다.
- reachability report는 diagnostics이며 Draft weight나 data를 변경하지 않는다.

Runtime wiring은 [Player System](../architecture/player-system.md)을 참고한다.
