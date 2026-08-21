# ADR-003: Player Rating vs Champion Proficiency

Status: Accepted

## Context

선수의 일반 능력과 특정 champion 수행 능력은 변화 주기와 lookup identity가 다르다. 둘을 하나의 rating vector에 합치면 champion을 바꿀 때 decision/farming 같은 일반 능력까지 중복 보정되고, Draft Player Fit과 match execution의 의미가 불명확해진다.

## Decision

- Player Ratings는 `PlayerRatingKey(teamCode, Position)`로 관리하고 공통 6 + role-specific 6 능력을 표현한다.
- Champion Proficiency는 player roster position이 특정 `ChampionRoleKey`를 수행하는 별도 1..20 값이다.
- `PlayerRatingCatalog.createPlayer`는 proficiency object를 명시적으로 요구한다.
- match realization은 base ratings를 immutable하게 유지하고 match-scoped `PlayerMatchPerformance`를 만든다.
- proficiency 보정은 champion-tool execution 성격의 `execution(...)` read에만 적용한다.
- Draft의 player fit은 현재 proficiency를 사용하며 Player Ratings를 대신 읽지 않는다.

## Consequences

- proficiency resource가 없어도 neutral 14 fallback으로 domain flow를 실행할 수 있다.
- 그러나 neutral fallback을 authored player-champion data로 간주할 수 없다.
- decision, map awareness, farming과 execution score의 책임이 분리된다.
- 향후 proficiency resource에는 roster identity와 role coverage 정책을 별도로 설계해야 한다.

## Invariants

- nickname은 Player Rating identity가 아니다.
- proficiency를 Player Rating JSON에 미리 합산하지 않는다.
- cognitive/economy rating read에 proficiency adjustment를 적용하지 않는다.
- Champion Power, Matchup, Composition resource에 player-specific proficiency를 넣지 않는다.
- base Player Ratings는 match 결과로 mutation하지 않는다.
