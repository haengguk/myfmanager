# Adding a Champion

이 절차는 active manifest가 선택하는 full resource set에 champion을 추가할 때 사용한다. Catalog만 수정하면 completeness validation과 Draft Meta identity가 실패하도록 설계되어 있다.

## 1. Catalog Identity 추가

Active Catalog JSON에 다음을 작성한다.

- stable lowercase kebab-case `id`
- Korean/English display names
- unique `riotAssetId`
- `primaryPosition`
- 검증된 `supportedPositions`

`primaryPosition`은 반드시 supported set에 포함해야 한다. `supportedPositions`는 단순 solo-queue pocket pick 목록이 아니라 이 project에서 production legal role로 지원할 범위다.

Catalog pool version과 필요하면 balance/Riot data version을 명시적으로 갱신한다. 기본 10-champion selection을 바꿀 의도가 없다면 `defaultSelection`을 불필요하게 수정하지 않는다.

## 2. Champion Power 작성

Active Power resource의 `championProfiles`에 정확히 한 profile을 추가한다.

- 기존 `levelCurveId`/`itemCurveId`를 재사용하거나 공유 curve를 명시적으로 추가
- 모든 `ProgressionCombatContext` 값 작성
- `ChampionTag` 작성
- champion만의 차이가 필요할 때만 `levelAnchors`/`itemAnchors` 사용

Champion Power는 champion 자체의 baseline/progression curve다. Player Rating, 현재 win rate, 상대 champion 우열, team composition을 이 값에 섞지 않는다.

`profileVersion`과 `requiredChampionPoolVersion`을 새 Catalog와 일치시킨다.

## 3. Matchup Profile 작성

Active Matchup resource에 champion-level authoring profile을 하나 추가한다.

- 15개 `baseTraits`를 모두 1..20로 작성
- supported role마다 kit 의미가 달라지는 값만 `roleOverrides`로 덮어쓰기
- strength/weakness/summary/source metadata는 authoring rationale로 작성 가능

Matchup은 structural interaction input이다. patch의 현재 win rate나 pairwise counter table을 옮기지 않는다. `GEOMETRIC_V2`가 runtime에 두 profile을 결합하므로 champion pair별 matrix를 생성하지 않는다.

## 4. Composition Profile 작성

Active Composition resource에 다음을 추가한다.

- 15개 `baseCapabilities` 모두 1..20
- `physicalThreat`, `magicThreat`, `trueDamageThreat` 각각 0..20
- flex role에서 5인 team 기여가 달라지는 capability/damage만 `roleOverrides`로 수정

Composition은 5인 lineup 기능이다. lane Matchup의 range/duel 우열이나 Champion Power의 growth curve를 재작성하지 않는다.

## 5. Jungle Clear 조건부 작성

`supportedPositions`에 `JUNGLE`이 있으면 active Jungle Clear resource에 정확히 한 profile을 추가한다.

- `position: JUNGLE`
- `early`, `mid`, `late`: finite 0..2
- active resource의 activation policy와 일치하는 `gameplayEnabled`; 현재 economy-v1 resource에서는 `true`

`true`로 추가할 때는 phase 경계, selected-assignment validation, OFF parity, ineligible Random 0회와 CS/gold/XP integrity focused test를 함께 갱신한다. 새 champion 수치의 balance calibration은 별도 diagnostic에서 수행한다.

JUNGLE을 지원하지 않으면 profile을 추가하지 않는다. extra Jungle Clear profile도 completeness failure다.

## 6. Draft Meta Legal Role 갱신

새 champion의 각 supported position마다 Draft Meta profile을 추가한다.

- priority 1..20
- `requiredChampionPoolVersion` 갱신
- `requiredLegalRoleKeyCount` 갱신
- `requiredLegalRoleKeyHash` 재계산
- versioned filename을 바꾸면 `DraftMetaCatalog.RESOURCE`와 `VERSION`도 함께 갱신

Legal-role hash의 canonical input은 모든 `ChampionRoleKey.stableId()`를 정렬해 줄바꿈으로 연결하고 마지막 trailing newline을 붙인 UTF-8 문자열이다. SHA-256 결과를 사용한다.

## 7. Version과 Manifest 갱신

Catalog, Power, Matchup, Composition, Jungle Clear의 `requiredChampionPoolVersion`이 동일한 새 pool version을 가리키는지 확인한다. versioned resource file을 새로 만들었다면 active `champion-resource-manifest.json`의 다섯 path와 `manifestVersion`을 한 coherent set으로 갱신한다.

파일명, 내부 version, manifest path 중 하나만 바꾸지 않는다.

## 8. Completeness Validation

다음 집합이 정확히 일치해야 한다.

- Catalog champion ids = Power ids
- Catalog legal roles = materialized Matchup roles
- Catalog legal roles = materialized Composition roles
- Catalog JUNGLE roles = Jungle Clear roles
- Catalog legal roles = Draft Meta roles/hash

Flex champion은 base + role override가 모든 supported position으로 materialize되는지 확인한다. display name을 key로 사용하거나 frontend text에서 position을 추론하지 않는다.

## 9. Historical Regression 보존

다음 initial resource는 확장용 historical oracle이므로 신규 champion을 넣기 위해 수정하지 않는다.

- `champion-pool-initial-30-v1.json`
- `champion-power-initial-30-v1.json`
- `champion-matchup-role-profiles-initial-30-v1.json`
- `champion-composition-role-profiles-initial-30-v1.json`
- `champion-jungle-clear-initial-30-neutral-v1.json`

새 champion은 active full resource와 새 full identity에만 추가한다. Historical hash expected value를 full resource 확장 때문에 덮어쓰지 않는다.

## 10. Verification

먼저 focused resource/integration test를 실행한다.

```bash
cd backend
./gradlew test --tests 'com.lolfm.champion.ChampionFullPopulationIntegrationTest'
./gradlew test --tests 'com.lolfm.champion.ChampionResourceHardeningTest'
./gradlew test --tests 'com.lolfm.draft.DraftMetaCatalogTest'
```

변경 범위에 flex/draft/runtime이 포함되면 해당 RoleAssignment/Draft/Match simulation focused test도 추가한다. 의도된 expected change와 regression을 분리한 뒤 normal full regression을 실행한다.

```bash
./gradlew test
```

대규모 distribution diagnostic은 balance 검토가 명시적으로 필요할 때만 별도 task로 실행한다.

## 하지 말아야 할 것

- historical frozen resource를 active population 확장용으로 수정
- display name, JSON 배열 index, event message로 champion/role identity 추론
- 검증되지 않은 pocket pick을 `supportedPositions`에 추가
- 현재 win rate나 tier를 Matchup trait로 복사
- Matchup과 Composition에 같은 의미를 중복 작성
- pairwise matchup matrix 생성
- calibration 없이 Jungle Clear를 enable
- completeness failure를 neutral fallback으로 숨김
- diagnostic 분포를 맞추려고 production tuning constant를 자동 조정
