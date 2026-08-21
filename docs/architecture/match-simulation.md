# Match Simulation

## Entry Point

HTTP simulation은 `com.lolfm.controller.MatchController`의 `POST /api/matches/simulate`에서 시작한다.

1. request의 `seed`를 사용한다. 없으면 controller가 `System.currentTimeMillis()`로 새 seed를 만들고 response에 반환한다.
2. `ChampionSelectionValidator`가 explicit selection 또는 catalog의 `defaultSelection`을 10개의 `ChampionAssignment`로 변환한다.
3. `DummyDataFactory`가 현재 API용 blue/red team을 만든다.
4. `MatchSimulator.simulate(...)`가 `MatchTimeline`을 생성한다.
5. response에는 seed, 두 team, timeline, champion metadata가 포함된다.

현재 `DummyDataFactory` team은 legacy `PlayerAttributes`를 사용한다. production Player Ratings resource는 Spring catalog로 로드되지만 이 endpoint의 roster 생성에는 아직 사용되지 않는다. 자세한 내용은 [Player System](player-system.md)에 있다.

## Match Initialization

`MatchSimulator.runSimulation`은 매 호출마다 다음 상태를 새로 만든다.

- `MatchChampionAssignments`: `PlayerKey(TeamSide, Position)`별 champion과 selected position
- `GameState`: 시간, objective/map/lane/macro state, per-tick registries, diagnostics counters
- `TeamState`: team gold/kills/objective/buff state와 5개의 `PlayerState`
- `PlayerState`: KDA, gold/CS, death/respawn, FARM/activity restrictions, progression
- `CompositionRuntimeState`: 해당 match의 lineup analysis, attempt identity, observations
- `Random(seed)`: resolver gameplay의 주 Random stream

`Player`가 explicit ratings profile이면 `PlayerMatchPerformance.realize`가 match seed, side, position에서 파생된 deterministic seed로 일관성 변동과 proficiency를 materialize한다. legacy profile이면 기존 네 가지 `PlayerAttributes` 경로를 유지한다.

초기화 시 두 lineup이 TOP/JUNGLE/MID/ADC/SUPPORT를 모두 갖는지 확인하고, Champion Power와 Matchup catalog를 `GameState`에 연결한다. Composition mode가 `OFF`가 아니면 assignment로 두 5인 lineup을 만들고 active Composition profile을 한 번 분석한다.

### 현재 mode wiring

| 구성 경로 | Champion Power | Matchup | Composition |
| --- | --- | --- | --- |
| Spring `@Autowired MatchSimulator` (`MatchController`) | ON | `OFF` | `OFF` |
| 명시적 `SimulationOptions.productionDefaults()` | ON | `GEOMETRIC_V2` | `PRODUCTION_V2` |

이 표는 “기능이 구현되어 있는가”와 “현재 HTTP simulation이 그 기능을 적용하는가”를 분리한다.

## Timeline / Tick Flow

한 tick은 10초다. `MatchSimulator`의 실제 순서는 다음과 같다.

1. 시간을 증가시키고 Baron buff, player activity, mid/late macro plan을 만료한다.
2. major-combat participant와 structure-action per-tick registry를 비우고 recent objective control을 감쇠한다.
3. 양 팀 passive gold를 지급한다.
4. lane pressure를 갱신한다.
5. BLUE, RED 순서로 position economy/FARM을 처리하고 progression economy event를 배출한다.
6. 첫 combat priority를 실행한다: Jungle Gank → Roam → Lane Combat.
7. objective spawn state를 갱신한다.
8. 앞선 actual attempt가 없으면 generic Skirmish를, 그마저 없으면 scheduled Teamfight를 평가하고 teamfight 승자의 objective control을 갱신한다.
9. lane-phase outer siege를 처리한다.
10. Teamfight outcome이 있으면 post-fight objective와 post-fight push window를 순서대로 처리한다.
11. match가 끝나지 않았고 post-fight objective가 없으면 일반 objective attempt를 평가한다. responder가 `CONTEST`할 수 있는 것은 이 tick에 major combat이 아직 없을 때뿐이다.
12. lane-to-mid-game 및 late-game transition을 평가한다.
13. mid-game macro, late-game macro/siege, 남은 macro push를 처리한다.
14. Nexus 또는 safety timeout 종료를 평가하고 progression event를 drain한 뒤 snapshot을 추가한다.

Safety timeout은 5,400초다. 정상 승리는 structure path가 Nexus를 파괴해 `GameState`를 종료할 때 결정되며, timeout은 winner 없이 종료 reason을 남긴다.

## Central Combat Priority

Jungle Gank, Roam, Lane Combat의 `resolve` 반환값은 단순 “평가됨”이 아니라 actual attempt가 시작되었는지를 나타낸다. 따라서 다음 규칙이 성립한다.

- gank trigger가 모두 실패하면 Roam을 평가한다.
- Roam이 actual attempt를 만들지 않으면 Lane Combat을 평가한다.
- 앞선 세 경로가 actual attempt를 만들지 않아야 generic Skirmish를 시도한다.
- 그 뒤에도 actual combat이 없을 때만 scheduled Teamfight를 시도한다.
- objective contest는 `GameState.wasMajorCombatAttemptedThisTick()`가 false일 때만 eligible하다.

actual attempt는 `NO_KILL` 결과여도 action state, FARM block, pressure cost 또는 major-combat slot을 소비할 수 있다. 반대로 단순 평가나 failed trigger는 gameplay summary event를 만들지 않는다. diagnostic counter는 evaluation/trigger/attempt/outcome을 별도로 기록한다.

## Major Gameplay Systems

| 시스템 | 현재 역할 |
| --- | --- |
| Lane Pressure / Lane Combat | lane별 pressure와 economy modifier, same-lane combat opportunity와 structured outcome |
| Jungle Gank / Counter-gank | jungler eligibility, target lane, actual gank, defender response, common kill path |
| Roam | MID/SUPPORT 이동, origin opportunity cost, target combat, activity/FARM restriction |
| Position Economy | passive gold와 분리된 CS/FARM 처리; blocked player는 FARM Random도 소비하지 않음 |
| Progression | FARM/kill XP, level, gold-derived item stage와 combat contribution |
| Objective | Dragon/Baron/Elder spawn, priority, initiator/responder decision, contest/trade/capture |
| Structure | lane siege, post-fight push, macro push, tower/inhibitor/base/Nexus mutation |
| Mid/Late Macro | phase transition, plan lifecycle, objective setup, siege/base-defense decisions |
| End Game | Nexus destruction 또는 simulation safety timeout |

Counter-gank는 독립적인 parallel combat이 아니라 selected Jungle Gank attempt 안에서 resolver response로 실행된다. Objective fight와 late-game siege도 기존 teamfight/common kill path를 재사용한다.

## Combat Strength Inputs

전투 점수는 한 거대한 profile로 합쳐 저장하지 않는다.

1. resolver가 Player Ratings 또는 legacy attributes, gold, lane/objective context로 기존 baseline을 만든다.
2. `CombatProgressionEvaluator`가 실제 level/item progression edge를 더한다.
3. `CombatChampionPowerEvaluator`가 champion별 level/item/context curve의 team edge를 더한다.
4. `ChampionMatchupResolver`가 같은 position의 실제 참가자 pair에 role Matchup edge를 더한다.
5. Composition mode가 활성화된 경우 `CompositionRuntimeState`가 5인 lineup context edge를 action의 winner-decision channel에 적용한다. 이를 개인 power나 pair matchup 값으로 중복 합산하지 않는다.

Player proficiency는 `PlayerMatchPerformance.execution(...)`을 호출하는 champion-tool execution 성격의 skill check에만 보정으로 들어간다. `DECISION_MAKING`, map awareness, pure farming 같은 cognitive/economy rating에는 proficiency를 더하지 않는다.

## Events and Snapshots

`MatchTimeline`은 duration, winner, `MatchEvent[]`, `MatchSnapshot[]`을 보유한다.

- summary action event(`JUNGLE_GANK`, `ROAM`, `LANE_COMBAT`, `TEAMFIGHT` 등)는 attempt/outcome을 설명한다.
- actual kill은 별도 `KILL` event와 정확한 `CombatSource`를 갖는다.
- objective, structure, macro, progression 의미는 nullable structured data field로 노출된다.
- `message`, killer/victim display name은 표시용이며 gameplay identity를 복원하는 source가 아니다.

Frontend는 snapshot을 현재 playback time 이하에서 선택하고 event를 시간순으로 표시한다. API field를 변경할 때 기존 timeline, snapshot, speed control을 보존해야 한다.

## Determinism

- 명시적 seed는 같은 team, assignment, options에서 replay key다.
- main gameplay `Random`은 한 번 생성되고 `MatchSimulator`의 고정 resolver 순서로 전달된다.
- explicit player rating의 match realization은 match seed에서 구조적으로 파생되어 재현된다.
- ineligible action, duplicate evaluation, 실행되지 않는 branch, diagnostics는 불필요한 Random을 소비하면 안 된다.
- request에 seed가 없으면 매 요청마다 wall-clock seed가 선택되므로 자동으로 같은 결과가 나오지 않는다. response seed를 다시 보내야 replay할 수 있다.
- structured timeline 전체—participants, outcome, rewards, objectives, structures, winner—가 same-seed 회귀의 대상이다.

## Important Invariants

- resolver에는 match 간 mutable state를 두지 않는다.
- gameplay state mutation은 eligibility 확인과 actual-attempt 확정 뒤에 시작한다.
- 한 tick에 actual major-combat attempt는 최대 하나다.
- duplicate identity와 tick registry는 display text가 아니라 simulation time/action/side/lane/player 같은 구조화된 값으로 구성한다.
- actual kill은 `KillRewardResolver`를 통과해 kill/assist gold, bounty, death, respawn, XP를 한 번만 처리한다.
- overlapping FARM restriction은 가장 늦은 expiry까지 유지하고 과거 CS/gold를 직접 차감하거나 복구하지 않는다.
- diagnostics mode가 같은 gameplay configuration의 decision이나 Random consumption을 바꾸면 안 된다.

Champion별 입력은 [Champion System](champion-system.md), player 입력은 [Player System](player-system.md)을 참고한다.
