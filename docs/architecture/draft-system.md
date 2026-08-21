# Draft System

## Current Entry Point

`com.lolfm.draft.DraftEngine`이 production draft domain의 진입점이다. `DraftResourceSet.loadDefault()`는 active `ChampionResourceSet`과 고정 Draft Meta resource를 조립한다.

현재 DraftEngine은 Spring component/controller가 아니며 frontend에도 draft 화면이 없다. Java 호출자는 `DraftTeamContext` 두 개와 series-scoped `SeriesDraftHistory`를 전달해야 한다. 결과의 `MatchChampionAssignments`는 `MatchSimulator`에 직접 전달할 수 있고 이 연계는 integration test로 검증된다.

## Draft Flow

```text
DraftResourceSet + DraftRuleSet + DraftScoringPolicy
                     ↓
             fresh DraftState
                     ↓
          PreDraftPlanner portfolios
                     ↓
    Candidate Generation → Pick/Ban Evaluation
                     ↓
             ShallowDraftSearch
                     ↓
                DraftDecision
                     ↓ (20 turns)
       FinalRoleAssignmentResolver
                     ↓
 FinalDraftResult + MatchChampionAssignments
```

`DraftState`는 매 action 뒤 새 immutable value로 만들어진다. current draft의 pick/ban, series exclusions, next turn을 보유하고 같은 champion의 중복 사용을 거부한다.

## Rule Set and Availability

현재 제공되는 `DraftRuleSet.professional()`은 양 팀 각각 5 bans/5 picks, 총 20 turns의 professional sequence 하나다. identity는 `PROFESSIONAL_5_BAN_5_PICK_HARD_FEARLESS_V1`이다.

`DraftAvailability`와 `RoleAssignmentSolver`는 후보를 다음 조건으로 제한한다.

- 현재 draft에서 pick/ban되지 않음
- series의 Hard Fearless exclusion에 없음
- 현재 partial picks가 legal role assignment를 하나 이상 유지함
- 남은 pick 수로 5 positions를 완성할 수 있음

## Pre-Draft Planning

`PreDraftPlanner`는 POKE_SIEGE, FRONT_TO_BACK, PICK_CONTROL, DIVE archetype을 비교해 상위 portfolio를 유지한다. 계획은 다음을 함께 본다.

- desired capability와 structural vulnerability
- active Composition profile
- Draft Meta priority
- team의 role별 proficiency
- 현재/예상 partial role assignments
- opponent가 사용할 수 있는 structural response
- fearless/current-draft unavailable pool

계획은 고정 전략 선언이 아니라 매 turn search에서 replan되는 scoring context다.

## Flex Role Handling

pick 시 role을 즉시 확정하지 않는다.

`RoleAssignmentSolver`는 현재 picks를 champion id 기준으로 안정 정렬하고, 각 champion의 `supportedPositions` 조합을 열거한다. 후보가 하나 이상의 legal assignment에 남아 있는 동안 여러 position 가능성을 유지한다. 이 feasible set이 candidate generation, proficiency, matchup, composition, flexibility, future-completion 평가에 사용된다.

Draft가 끝나면 `FinalRoleAssignmentResolver`가 양 팀의 작은 legal permutation space를 비교한다. 각 candidate assignment의 proficiency, worst-case opponent Matchup, Composition quality를 결합하고 stable id로 deterministic tie-break하여 최종 role을 고른다. 자세한 결정 이유는 [ADR-004](../adr/ADR-004-flex-role-draft-resolution.md)에 있다.

## Pick Evaluation

`PickEvaluator`가 실제로 기록하는 component는 다음 여덟 가지다.

- `META_PRIORITY`
- `PLAYER_FIT` — 현재는 `DraftTeamContext`의 Champion Proficiency
- `MATCHUP` — feasible assignment의 robust `GEOMETRIC_V2` lane edge
- `COMPOSITION_FIT`
- `COMPOSITION_RESPONSE`
- `FLEXIBILITY`
- `DENIAL`
- `FUTURE_FEASIBILITY`

illegal 또는 완성 불가능한 candidate는 선택 가능한 점수를 받지 않는다. Draft Player Fit은 Player Ratings가 아니라 proficiency만 사용한다.

## Ban Evaluation

`BanEvaluator`의 component는 다음과 같다.

- `OPPONENT_EXPECTED_PICK_VALUE`
- `THREAT_TO_OUR_PLAN_PORTFOLIO`
- `META_PRIORITY`
- `OPPONENT_FLEX_VALUE`
- `ROLE_POOL_COMPRESSION`
- `PROTECTION_VALUE`
- `OUR_LOST_PICK_OPPORTUNITY` — own pool에서 잃는 비용으로 음의 weight

상대가 해당 champion을 제외하고도 roster를 완성할 수 없는 비정상 ban 효과를 점수로 이용하지 않도록 availability를 먼저 확인한다.

## Search

`DraftCandidateGenerator`는 전체 legal pool을 전부 깊게 탐색하지 않는다. coarse meta/proficiency/plan 값으로 shortlist를 만들되, composition repair 후보를 별도 슬롯으로 보존해 단순 상위 meta 후보만 남는 것을 막는다.

`ShallowDraftSearch`는 bounded depth/beam의 alternating max/min search를 수행한다.

- root action의 immediate evaluation을 계산한다.
- 다음 turn actor가 root team이면 최대화하고 opponent이면 최소화한다.
- continuation utility를 할인해 immediate score와 합친다.
- 최고 score가 같으면 `ChampionId.value()` lexical order로 결정한다.
- 선택 결과에 component breakdown과 top alternatives를 남긴다.

## Determinism

production draft package에는 `Random`이나 system-time 입력이 없다. 동일 resource/context/history는 동일한 decisions, assignments, `draftIdentity()`를 만든다.

## Standard and Hard Fearless

별도 `STANDARD` ruleset/mode enum은 현재 없다.

- 빈 `SeriesDraftHistory`로 실행한 game 1은 prior exclusions가 없으므로 일반 단판과 같은 champion availability를 가진다.
- 완료된 `FinalDraftResult`를 `SeriesDraftHistory.commitCompleted`하면 양 팀의 picks만 series-wide consumed set에 추가한다.
- bans는 다음 game exclusion에 포함하지 않는다.
- 같은 completed draft를 두 번 commit해도 `draftIdentity()`로 idempotent하게 한 번만 반영한다.
- 새 `SeriesDraftHistory`는 이전 series 상태를 공유하지 않는다.

따라서 현재 구현 범위는 “exclusion 없는 첫 game + completed picks를 누적하는 Hard Fearless series”다. 독립적으로 선택 가능한 Standard/Fearless API나 tournament orchestration은 없다.

## Active Draft Data

Draft Meta는 `draft-meta-full-173-216-role-2026-08-18-v3`이며 active champion pool과 216 legal role key를 1:1로 커버한다. loader는 champion-pool version, key count, canonical legal-role SHA-256, 1..20 priority scale, `hardFearlessContext: true`를 검증한다.

## Out of Scope / Not Wired

- Draft Controller, HTTP endpoint, frontend draft UI
- series scheduling, side selection, roster/substitute management
- 별도의 Standard ruleset 선택
- production Player Ratings를 draft score에 직접 반영하는 경로
- authored Champion Proficiency resource; 현재 missing proficiency는 neutral 14
- Draft가 자동으로 match endpoint를 호출하는 orchestration

현재 snapshot은 [Project Status](../project-status.md), player 경계는 [Player System](player-system.md)을 참고한다.
