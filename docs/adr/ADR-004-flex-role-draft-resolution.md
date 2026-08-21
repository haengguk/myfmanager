# ADR-004: Flex-role Draft Resolution

Status: Accepted

## Context

Flex champion의 role을 pick 순간에 고정하면 이후 pick으로 가능한 assignment, player proficiency, matchup response, composition repair를 조기에 잃는다. 반대로 role을 display text나 heuristic으로 늦게 추론하면 legal lineup 보장이 약해지고 동일 입력의 결과가 흔들릴 수 있다.

## Decision

- Catalog의 `supportedPositions`를 legal source로 사용한다.
- partial draft마다 `RoleAssignmentSolver`가 가능한 champion-to-position assignment를 전부 열거한다.
- candidate는 현재 picks에 추가해도 하나 이상의 feasible assignment와 final completion path가 남을 때만 legal하다.
- Pick/Ban evaluation과 search는 feasible position/assignment 전체에서 robust score, proficiency, matchup, composition, flexibility를 계산한다.
- 5 picks 완료 뒤 `FinalRoleAssignmentResolver`가 legal permutation space에서 worst-case opponent utility를 고려해 최종 role을 고른다.
- 동점은 structured stable id로 결정한다.

## Consequences

- Draft 중 champion role은 ambiguity를 유지할 수 있다.
- candidate/search 비용은 증가하지만 champion 수가 아니라 한 team의 최대 5 picks에 대한 작은 permutation space로 제한된다.
- final `MatchChampionAssignments`는 simulator가 요구하는 10개의 exact `PlayerKey` assignment로 materialize된다.
- 모든 flex role에는 Matchup, Composition, Draft Meta coverage가 필요하다.

## Invariants

- pick 시 role을 임의로 고정하지 않는다.
- unsupported position을 proficiency가 높다는 이유로 허용하지 않는다.
- final assignment는 각 Position을 정확히 한 번 사용한다.
- role feasibility와 tie-break는 display name/iteration order가 아니라 structured identity와 stable ordering을 사용한다.
- 같은 draft inputs는 같은 final role assignment를 만든다.
