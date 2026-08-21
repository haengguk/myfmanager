# ADR-001: Resource-driven Champion Data

Status: Accepted

## Context

Champion identity는 Catalog뿐 아니라 Power, Matchup, Composition, Jungle Clear, Draft Meta에서 공유된다. Flex role 하나가 추가되면 여러 role-key catalog의 coverage가 함께 바뀐다. 각 subsystem이 자체 hardcoded champion 목록을 가지면 같은 champion pool version에서도 missing/extra profile과 role drift가 생길 수 있다.

## Decision

Champion data는 versioned JSON resource로 작성하고 `champion-resource-manifest.json`이 coherent active set을 선택한다.

- Catalog가 `ChampionId`와 legal `ChampionRoleKey`의 기준이다.
- Power/Matchup/Composition/Jungle loader는 Catalog identity와 version을 검증한다.
- Matchup/Composition은 champion-level base + role override를 complete role profiles로 materialize한다.
- `ChampionResourceSet`은 모든 catalog를 immutable value로 묶고 exact cross-catalog coverage를 검증한다.
- Draft Meta는 manifest 밖의 별도 resource지만 champion-pool version과 canonical legal-role hash로 같은 identity에 결합된다.

## Consequences

- 신규 champion/role은 관련 resource를 모두 갱신해야 하므로 작업량이 늘어난다.
- 대신 incomplete deployment와 silent neutral fallback이 load 단계에서 차단된다.
- runtime evaluator는 display text나 source-array order 없이 structured key로 lookup할 수 있다.
- active dataset 교체는 manifest diff로 추적할 수 있다.

## Invariants

- Catalog의 champion/role set이 유일한 coverage source다.
- active resource의 `requiredChampionPoolVersion`은 Catalog version과 일치해야 한다.
- missing, extra, unknown, unsupported role은 load failure다.
- display name, Riot asset id, 배열 index를 gameplay key로 사용하지 않는다.
- resolver에 resource-derived mutable match state를 저장하지 않는다.
