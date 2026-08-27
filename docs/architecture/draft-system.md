# Draft System

## Current Entry Point

`com.lolfm.draft.DraftEngine`이 production draft domain의 진입점이다. `DraftResourceSet.loadDefault()`는 active `ChampionResourceSet`과 고정 Draft Meta resource를 조립한다.

`DraftEngine` 자체는 pure domain component이며 controller가 아니다. 직접 Java 호출자는 `DraftTeamContext` 두 개와 series-scoped `SeriesDraftHistory`를 전달할 수 있다. Spring backend에서는 `RealDraftMatchOrchestrator`가 실제 Team으로 두 context를 만들고 DraftEngine을 호출한다. 결과의 `FinalDraftResult.matchChampionAssignments()`가 유일한 match assignment source이며 재계산 없이 `MatchSimulator`에 전달된다.

Additive `PlayerControlledDraftEngine`은 같은 planner/evaluator/search와 `AUTO_DRAFT_VARIETY_V1` selector를 사용하되 한쪽 팀의 모든 턴을 `PLAYER` authority로 멈춰 세우는 stateless transition domain이다. `PlayerDraftApiV1Controller`가 이 경계를 bounded in-memory session으로 노출한다. 기존 완전 자동 `DraftEngine`과 `/api/v1/real-matches` 계약은 변경하지 않는다. Frontend draft 화면은 아직 없다.

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

`canComplete`는 flex champion의 어느 legal role이든 허용하는 champion-level 완성 검사다. 진단용 `canCompleteWithCandidateAtRole`은 candidate를 특정 `Position`에 고정한 feasible assignment만 남긴 뒤, unavailable pool을 제외한 서로 다른 champion으로 나머지 positions까지 매칭할 수 있을 때만 true다. 이 additive helper는 production scoring이나 shortlist를 변경하지 않는다.

Player Draft의 manual selection은 AI 추천 상위 3개에 제한되지 않는다. 현재 turn/side/action과 champion identity, current Draft/Hard Fearless availability를 먼저 확인한다. PICK은 partial role assignment와 남은 roster 완성을 모두 유지해야 하고, BAN은 그 champion을 제외한 뒤 BLUE/RED 양쪽 roster가 모두 완성 가능해야 한다. 거부된 action은 immutable `DraftState`, evidence, revision과 Match Simulator Random을 전혀 변경하지 않는다.

## Mixed Player/AI authority

Game 1의 `PlayerControlledDraftEngine.Progress`는 immutable `DraftState`와 ordered `DraftTurnControlEvidence`를 함께 보유한다. 매 turn은 다음을 구조적으로 결속한다.

- turn, side, action type, `ChampionId`
- `AI` 또는 `PLAYER` authority
- action 직전/직후 `DraftStateHasher` identity
- AI이면 기존 authoritative `DraftSelectionTrace`
- PLAYER이면 controlled side, selectable-set identity, legality result와 operational `clientActionId`

PLAYER evidence에는 존재하지 않는 AI rank, score-loss 또는 weighted draw를 만들지 않는다. `clientActionId`는 HTTP idempotency evidence에는 남지만 gameplay/control-evidence hash에서는 제외한다. 제어 정책은 `PLAYER_CONTROLLED_DRAFT_V1`, SHA-256은 `8f6488f07c44a6529e88bd022fff3124458a8237cc919bd7dd3e140eaa4a0752`다.

플레이어 action을 적용하면 상대 AI는 갱신된 상태에서 기존 production search/selector로 다음 플레이어 턴 또는 20턴 완료까지 진행한다. 20턴이 끝난 뒤에만 기존 `FinalRoleAssignmentResolver`가 flex role을 확정한다. Match preflight는 transcript를 turn 1부터 다시 적용하고 manual selectable set, AI trace, state hash와 final role assignment를 재계산하므로 caller가 최종 assignment만 주입할 수 없다.

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

Real-proficiency reachability diagnostic은 shortlist의 `ChampionId` presence와 `ChampionRoleKey` reachability를 구분한다. 후자는 legal PICK turn, availability, target-role assignment, target role을 고정한 roster completion, champion shortlist presence가 모두 성립해야 한다. 따라서 다른 position으로만 배치 가능한 flex champion은 target role reachable로 세지 않는다. 이 결과는 bounded reachability이지 proficiency가 shortlist 진입의 원인이라는 causal 증명이 아니다.

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
- `RealDraftMatchOrchestrator`의 series overload는 호출자가 소유한 history를 사용하며, Draft preflight와 Match가 모두 성공한 뒤 completed picks를 commit한다. 실패한 orchestration은 다음 game의 exclusion을 선반영하지 않는다.

따라서 현재 구현 범위는 “exclusion 없는 첫 game + completed picks를 누적하는 Hard Fearless series”다. 독립적으로 선택 가능한 Standard/Fearless API나 tournament orchestration은 없다.

## Active Draft Data

Draft Meta는 `draft-meta-full-173-216-role-2026-08-18-v3`이며 active champion pool과 216 legal role key를 1:1로 커버한다. loader는 champion-pool version, key count, canonical legal-role SHA-256, 1..20 priority scale, `hardFearlessContext: true`를 검증한다.

## Out of Scope / Not Wired

- player-controlled Draft frontend UI
- session persistence, authentication, multi-node coordination, restart recovery
- series scheduling, side selection, roster/substitute management
- 별도의 Standard ruleset 선택
- production Player Ratings를 draft score에 직접 반영하는 경로
- BO3/BO5 scheduling이나 match endpoint를 통한 orchestration

현재 snapshot은 [Project Status](../project-status.md), player 경계는 [Player System](player-system.md)을 참고한다.
