# Match Simulation

## Entry Points

HTTP simulation은 `com.lolfm.controller.MatchController`의 `POST /api/matches/simulate`에서 시작한다.

1. request의 `seed`를 사용한다. 없으면 controller가 `System.currentTimeMillis()`로 새 seed를 만들고 response에 반환한다.
2. `ChampionSelectionValidator`가 explicit selection 또는 catalog의 `defaultSelection`을 10개의 `ChampionAssignment`로 변환한다.
3. `DummyDataFactory`가 현재 API용 blue/red team을 만든다.
4. `MatchSimulator.simulate(...)`가 `MatchTimeline`을 생성한다.
5. response에는 seed, 두 team, timeline, champion metadata가 포함된다.

현재 `DummyDataFactory` team은 legacy `PlayerAttributes`를 사용한다. production Player Ratings resource는 Spring catalog로 로드되지만 이 endpoint의 roster 생성에는 아직 사용되지 않는다. 자세한 내용은 [Player System](player-system.md)에 있다.

별도의 backend application entry point인 `RealDraftMatchOrchestrator`는 explicit LCK team code 두 개, caller-owned `SeriesDraftHistory`, match seed를 받는다. 이 path는 `LckTeamAssembler`가 만든 실제 Team으로 Draft를 실행하고 `FinalDraftResult.matchChampionAssignments()`를 그대로 같은 Team과 함께 simulator에 전달한다. Controller를 호출하거나 `DummyDataFactory`를 사용하지 않으며 아직 HTTP에 노출되지 않았다.

기존 overload는 `BASELINE_V1`을 명시적으로 resolve한다. Additive overload는 임의 boolean 묶음이 아니라 `SimulationRuntimeProfileId`만 받는다. `ConfiguredMatchSimulatorFactory`의 public boundary도 profile ID와 별도 `SimulationInstrumentation`만 받아 closed registry를 내부에서 resolve한다. Caller-fabricated `ResolvedSimulationRuntimeProfile`은 실행/provenance 경계에서 허용하지 않는다.

## Explicit Runtime Profiles

다섯 profile의 공통 gameplay flag는 Lane Combat, FARM Recovery, Jungle Gank, Counter Gank, Roam, Objective Priority, Lane Phase, Mid/Late Macro, Objective Decision, Progression/Progression Power, Champion Power 모두 ON이다. 기존 세 profile은 Jungle Clear contribution이 `DISABLED_NOT_INTEGRATED`이고, 네 번째 candidate는 `ECONOMY_V1`, 다섯 번째 candidate는 `ECONOMY_AND_GANK_TEMPO_V1`이다.

| Profile | Matchup | Composition | Jungle Clear | Configuration hash |
| --- | --- | --- | --- | --- |
| `BASELINE_V1` | `OFF` | `OFF` | `DISABLED_NOT_INTEGRATED` | `c8cc557bd721228c473e30d31b7258510f9608a18098578bc1da36e603536215` |
| `MATCHUP_ONLY_CANDIDATE_V1` | `GEOMETRIC_V2` | `OFF` | `DISABLED_NOT_INTEGRATED` | `58714464c19a2cffd108d47a93a0909126513c8bb10cb0e19bbd87f8e78532ec` |
| `FULL_SYSTEM_CANDIDATE_V1` | `GEOMETRIC_V2` | `PRODUCTION_V2` | `DISABLED_NOT_INTEGRATED` | `caaf76274dc148040b0a95eae1ed5181790b2fc840f45af9b109ea7951c1fd5d` |
| `FULL_SYSTEM_WITH_JUNGLE_ECONOMY_CANDIDATE_V1` | `GEOMETRIC_V2` | `PRODUCTION_V2` | `ECONOMY_V1` | `e04869bca5281f7f416c8191d7bf1b5be04b3129f33f6dfd4de83e8d8e92743b` |
| `FULL_SYSTEM_WITH_JUNGLE_TEMPO_CANDIDATE_V1` | `GEOMETRIC_V2` | `PRODUCTION_V2` | `ECONOMY_AND_GANK_TEMPO_V1` | `c835280cbaa1244f4fecb099b19f71111c6d77aa1aeb1b7110a6e86e6381451c` |

`BASELINE_V1`은 이름만 OFF 묶음이 아니라 현재 Spring `@Autowired MatchSimulator`의 13 gameplay booleans와 두 mode를 모두 snapshot한 profile이다. Fixed-seed complete timeline parity test가 기존 constructor path와 exact equality를 검증한다. `ChampionMatchupMode.ON`, Composition `SHADOW`/`CANDIDATE` 같은 historical/internal audit path는 application profile로 선택할 수 없다.

Diagnostics는 gameplay configuration 밖의 instrumentation이다. ON/OFF가 `SimulationOptions.diagnosticsEnabled`만 바꾸며 configuration/replay hash와 timeline을 바꾸지 않는 exact equality test가 있다.

기존 세 profile은 계속 `activeGameplayRulesVersion=MATCH_SIMULATOR_PRE_JUNGLE_RULES_V2`를 공유한다. Pure-JRM Jungle Economy candidate는 `MATCH_SIMULATOR_JUNGLE_ECONOMY_RULES_V2`, Jungle Tempo candidate는 `MATCH_SIMULATOR_JUNGLE_TEMPO_RULES_V1`을 사용한다. 이 version은 profile의 configuration hash와 별개로, configuration 밖의 공통 production rule semantics를 식별한다.

## Configuration and Replay Provenance

`RealDraftMatchResult.executionProvenance`는 새 orchestration 결과에서 non-null이며 다음 identity를 분리한다. 기존 직접 constructor 호환 경로에서는 nullable이다.

- `configurationHash`: profile ID와 diagnostics를 제외한 field-complete gameplay configuration만 SHA-256으로 고정한다.
- `resourceProvenanceHash`: Champion manifest/catalog/Power/Matchup/Composition/Jungle Clear, Player Identity/Ratings/Proficiency, Draft Meta의 version/path/raw SHA와 semantic hashes를 고정한다.
- `engineImplementationVersion`: simulator 구현 계열을 식별한다. 현재 `MATCH_SIMULATOR_ENGINE_IMPLEMENTATION_V4`다. Batch C는 gameplay tuning이나 rules contract를 바꾸지 않고 structured eligibility diagnostics와 그 invariant gate를 추가했으므로 profile별 active rules version은 그대로다.
- `activeGameplayRulesVersion`: 선택한 profile이 사용하는 공통 gameplay rule semantics를 식별한다. 기존 세 profile은 `MATCH_SIMULATOR_PRE_JUNGLE_RULES_V2`, pure-JRM Jungle Economy candidate는 `MATCH_SIMULATOR_JUNGLE_ECONOMY_RULES_V2`, Jungle Tempo candidate는 `MATCH_SIMULATOR_JUNGLE_TEMPO_RULES_V1`이다.
- `replayProvenanceHash`: configuration, engine implementation, active gameplay rules, resource snapshot, side/team/roster, seed, series-history-before, Draft rules/scoring policy, ordered draft decision, final draft와 final assignment를 고정한다. Profile alias와 instrumentation은 제외한다.
- `timelineHash`: sorted-property/map-key canonical JSON으로 complete events/snapshots/winner/duration output을 고정한다.
- `randomFingerprint`: match의 seeded `Random.next(bits)` draw count와 resolver context/value의 ordered SHA-256을 기록한다. Gameplay input이 아닌 observational output이므로 configuration/replay hash에는 넣지 않는다.

기존 `FinalDraftResult.draftIdentity()`는 series commit idempotency용 ordered decision identity로 유지한다. Provenance의 final draft/assignment hash가 그보다 넓은 replay 범위를 additive하게 담당한다.

`MatchSimulator.simulateObserved(...)`는 기존 simulator flow에 같은 seeded `Random`을 위임하는 observer를 전달하고 timeline과 Random fingerprint를 함께 반환한다. Observer는 스스로 draw를 요청하지 않고 trace capture 여부도 gameplay sequence를 바꾸지 않는다. Plain `simulate(...)`와 observed path의 complete timeline parity, diagnostics ON/OFF의 timeline+fingerprint equality를 테스트한다.

## Match Initialization

`MatchSimulator.runSimulation`은 매 호출마다 다음 상태를 새로 만든다.

- `MatchChampionAssignments`: `PlayerKey(TeamSide, Position)`별 champion과 selected position
- `GameState`: 시간, objective/map/lane/macro state, per-side Jungle Economy/Tempo state, per-tick registries, diagnostics counters
- `TeamState`: team gold/kills/objective/buff state와 5개의 `PlayerState`
- `PlayerState`: KDA, gold/CS, death/respawn, FARM/activity restrictions, progression
- `CompositionRuntimeState`: 해당 match의 lineup analysis, attempt identity, observations
- `Random(seed)`: resolver gameplay의 주 Random stream

`Player`가 explicit ratings profile이면 `PlayerMatchPerformance.realize`가 match seed, side, position에서 파생된 deterministic seed로 일관성 변동과 proficiency를 materialize한다. Skill별 seeded draw 배정은 `PlayerSkill` enum declaration order로 고정하며 unordered `Set` iteration에 의존하지 않는다. legacy profile이면 기존 네 가지 `PlayerAttributes` 경로를 유지한다.

초기화 시 두 lineup이 TOP/JUNGLE/MID/ADC/SUPPORT를 모두 갖는지 확인하고, Champion Power, Matchup, Jungle Clear catalog를 `GameState`에 연결한다. Jungle Economy가 활성화된 `ECONOMY_V1`과 `ECONOMY_AND_GANK_TEMPO_V1`에서는 양 팀의 selected JUNGLE assignment가 gameplay-enabled clear profile인지 fail-fast한다. Composition mode가 `OFF`가 아니면 assignment로 두 5인 lineup을 만들고 active Composition profile을 한 번 분석한다.

### 현재 mode wiring

| 구성 경로 | Champion Power | Matchup | Composition | Jungle Clear |
| --- | --- | --- | --- | --- |
| Spring `@Autowired MatchSimulator` (`MatchController`) | ON | `OFF` | `OFF` | OFF |
| 명시적 `SimulationOptions.productionDefaults()` | ON | `GEOMETRIC_V2` | `PRODUCTION_V2` | OFF |
| `RealDraftMatchOrchestrator` 기존 overload / `BASELINE_V1` | ON | `OFF` | `OFF` | OFF |
| `MATCHUP_ONLY_CANDIDATE_V1` | ON | `GEOMETRIC_V2` | `OFF` | OFF |
| `FULL_SYSTEM_CANDIDATE_V1` | ON | `GEOMETRIC_V2` | `PRODUCTION_V2` | OFF |
| `FULL_SYSTEM_WITH_JUNGLE_ECONOMY_CANDIDATE_V1` | ON | `GEOMETRIC_V2` | `PRODUCTION_V2` | `ECONOMY_V1` |
| `FULL_SYSTEM_WITH_JUNGLE_TEMPO_CANDIDATE_V1` | ON | `GEOMETRIC_V2` | `PRODUCTION_V2` | `ECONOMY_AND_GANK_TEMPO_V1` |

이 표는 “기능이 구현되어 있는가”와 “현재 HTTP simulation이 그 기능을 적용하는가”를 분리한다.

## Timeline / Tick Flow

한 tick은 10초다. `MatchSimulator`의 실제 순서는 다음과 같다.

1. 시간을 증가시키고 Baron buff, player activity, mid/late macro plan을 만료한다.
2. major-combat participant와 structure-action per-tick registry를 비우고 recent objective control을 감쇠한다.
3. 양 팀 passive gold를 지급한다.
4. lane pressure를 갱신한다.
5. BLUE, RED 순서로 position economy/FARM을 처리하고 progression economy event를 배출한다. Jungle Economy ON에서는 기존 player iteration 위치에서 JUNGLE만 unified resolver가 CS/gold/XP를 함께 처리한다. Jungle Tempo candidate에서는 이 단계의 actual successful outcome만 같은 side의 tempo credit을 갱신한다.
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
| Jungle Economy V1-A | `Champion Clear × pure Jungle Resource Management`로 JUNGLE CS expected value와 XP를 만들고 actual CS에서 FARM gold를 지급; 기존 OFF 80/20 blend를 보존하며 economy-only profile에서는 행동 readiness와 미연결 |
| Jungle Tempo V1-B | actual successful Jungle Economy outcome으로 bounded credit을 쌓고 gank/counter-gank readiness를 trigger Random 전에 확인; actual attempt만 credit을 소비하며 objective eligibility에는 직접 연결하지 않음 |
| Progression | FARM/kill XP, level, gold-derived item stage와 combat contribution |
| Objective | Dragon/Baron/Elder spawn, priority, initiator/responder decision, contest/trade/capture |
| Structure | lane siege, post-fight push, macro push, tower/inhibitor/base/Nexus mutation |
| Mid/Late Macro | phase transition, plan lifecycle, objective setup, siege/base-defense decisions |
| End Game | Nexus destruction 또는 simulation safety timeout |

Counter-gank는 독립적인 parallel combat이 아니라 selected Jungle Gank attempt 안에서 resolver response로 실행된다. Objective fight와 late-game siege도 기존 teamfight/common kill path를 재사용한다.

### Jungle Tempo V1-B

Tempo candidate는 Jungle Economy V1-A의 `combinedEfficiency`를 별도 재계산하지 않고 actual successful outcome에서만 받는다. 사망, FARM recovery/macro block, non-default activity, gank/counter-gank FARM block으로 economy outcome이 없으면 credit과 Jungle Economy Random도 발생하지 않는다.

현재 V1 rules는 efficiency를 `0.85..1.15`로 clamp한 뒤 `elapsedSeconds × boundedEfficiency`를 credit으로 더한다. 첫 actual action readiness는 180초, 반복 readiness와 actual action cost는 150초, bank cap은 240초다. 마지막 successful economy outcome과 다음 outcome의 간격이 30초를 초과하면 기존 bank를 reset하고 현재 tick의 credit부터 다시 쌓는다. Readiness는 평가 시각과 같은 tick의 economy outcome도 요구한다.

Gank와 counter-gank는 기존 alive/cooldown/lane participant eligibility를 먼저 통과한 뒤 readiness를 확인하고, ready side만 trigger/response Random을 소비한다. Failed trigger, failed counter response, duplicate call과 ineligible path는 credit/action state를 소비하지 않는다. 실제 `NO_KILL`을 포함한 gank는 공격 side credit을 한 번 소비하고, successful counter response가 actual attempt를 시작하면 수비 side credit도 한 번 소비한다. Not-ready gank가 actual attempt를 만들지 않으면 Roam과 Lane Combat priority fallthrough는 유지된다.

Tempo는 objective attempt eligibility, probability 또는 reward에 직접 연결되지 않는다. 다만 actual gank/counter-gank kill이 기존 objective-priority state를 변경하는 간접 경로는 그대로 존재한다. `PATHING`은 credit 계산에 들어가지 않고 readiness 이후의 기존 gank trigger chance에만 사용되며, champion proficiency도 clear/JRM economy multiplier를 보정하지 않는다.

### Jungle V1 focused hardening

Batch C는 새 정글 행동이나 밸런스 수치를 추가하지 않는다. 기존 gank side 평가는 `NONE`, jungler unavailable, shared jungle-action cooldown, eligible lane 부재, Tempo not-ready로 구조화하고, 기존 counter-gank ineligibility도 실제 defender response 평가마다 함께 기록한다. Match-scoped diagnostics는 reason별 canonical enum map과 side별 latest decision을 immutable snapshot으로 노출한다. Gameplay resolver는 이 통계를 읽지 않으며 reason 기록 전후의 eligibility 순서와 Random 호출 순서는 같다.

Focused gate는 다음 교차 계약을 한 묶음으로 검증한다.

- 매 Jungle Gank evaluation은 BLUE/RED 각각 한 개의 structured decision을 만들고 `NONE` 수는 trigger roll 수와 같다.
- 실제 gank마다 counter-gank eligibility가 한 번 평가되며 actual gank/counter-gank 수와 Tempo consumption 수가 각각 같다.
- 180초 FARM 뒤 시작한 actual gank는 이미 받은 그 tick의 CS/gold/XP를 취소하지 않고 190/200초의 future FARM만 놓친다. Passive gold는 계속 지급되고 210초 경계에서 catch-up 없이 재개한다.
- death/FARM recovery와 action block이 겹치면 가장 늦은 boundary까지 유지한다. Macro FARM block도 CS/FARM gold/XP/Tempo credit/Jungle Economy Random을 모두 막되 passive gold는 유지한다.
- duplicate/ineligible path, priority fallthrough, 한 tick 한 major combat, common reward/death path, structured summary/KILL 연결, fresh-match state와 same-seed replay를 함께 검증한다.

다섯 profile의 configuration hash와 `MATCH_SIMULATOR_JUNGLE_TEMPO_RULES_V1`은 바뀌지 않았다. Engine implementation만 V3에서 V4로 올라가 replay provenance hash는 의도적으로 달라지지만, 기존 네 profile 12경기의 complete timeline과 Random fingerprint는 Pre-Tempo oracle과 exact parity다. Tempo candidate의 12 fixed-seed report도 Batch C 전후 byte-identical하다. 이 gate는 correctness hardening이며 calibration이나 `PRODUCTION_V1` 채택 결정이 아니다.

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
- explicit player rating의 match realization은 match seed에서 구조적으로 파생되고 skill enum declaration order로 draw를 배정한다.
- real orchestration replay는 동일 team codes, fresh/equivalent series history, authored resources, match seed에서 Draft decisions, final roles, assignment, events, snapshots, winner와 duration이 모두 같다.
- ineligible action, duplicate evaluation, 실행되지 않는 branch, diagnostics는 불필요한 Random을 소비하면 안 된다.
- API/timeline에 노출되는 enum set은 immutable `EnumSet` declaration order로 canonicalize한다. Hash/summary/serialization은 `Set.copyOf`의 JVM별 iteration order에 의존하지 않는다.
- request에 seed가 없으면 매 요청마다 wall-clock seed가 선택되므로 자동으로 같은 결과가 나오지 않는다. response seed를 다시 보내야 replay할 수 있다.
- structured timeline 전체—participants, outcome, rewards, objectives, structures, winner—가 same-seed 회귀의 대상이다.
- replay provenance는 동일 input snapshot을 식별하고 timeline hash와 Random fingerprint는 그 input에서 나온 complete output/consumption을 별도로 식별한다.

공식 Pre-Jungle cross-process oracle은 `backend/baseline/pre-jungle-runtime-v2/`다. `verifyJungleEconomyOffParity`는 기존 세 OFF profile × 세 case에서 configuration, complete timeline hash, Random draw count/trace hash가 이 artifact와 exact equality인지 검사한다. Engine/resource snapshot 변경으로 달라지는 replay provenance hash는 gameplay equality와 분리한다.

Jungle Tempo production code 직전의 별도 oracle은 `backend/baseline/pre-jungle-tempo-runtime-v1/`이다. 당시 네 profile × 세 real-match case의 12경기를 고정했고, V1-B 이후 `verifyPreJungleTempoParity`가 configuration/Draft/final assignment/complete timeline/Random fingerprint/result의 12/12 exact equality를 확인했다. 새 Tempo profile의 소수 fixed-seed diagnostic은 readiness와 actual consumption 구조 확인용이며 calibration 또는 production activation gate가 아니다.

## Important Invariants

- resolver에는 match 간 mutable state를 두지 않는다.
- gameplay state mutation은 eligibility 확인과 actual-attempt 확정 뒤에 시작한다.
- 한 tick에 actual major-combat attempt는 최대 하나다.
- duplicate identity와 tick registry는 display text가 아니라 simulation time/action/side/lane/player 같은 구조화된 값으로 구성한다.
- actual kill은 `KillRewardResolver`를 통과해 kill/assist gold, bounty, death, respawn, XP를 한 번만 처리한다.
- overlapping FARM restriction은 가장 늦은 expiry까지 유지하고 과거 CS/gold를 직접 차감하거나 복구하지 않는다.
- Jungle Tempo는 actual successful economy outcome만 credit으로 바꾸고 actual gank/counter-gank attempt만 side별 credit을 한 번 소비한다.
- Jungle gank/counter-gank eligibility reason은 match-scoped structured diagnostics로 기록하되 gameplay decision, state 또는 Random의 입력으로 사용하지 않는다.
- Not-ready/ineligible/duplicate Jungle path는 credit, action state와 Random을 소비하지 않는다. Trigger evaluation은 documented Random을 소비할 수 있지만 실패하면 credit/action state를 소비하지 않으며, gank actual attempt가 시작되지 않은 경우 lower-priority action fallthrough를 막지 않는다.
- diagnostics mode가 같은 gameplay configuration의 decision이나 Random consumption을 바꾸면 안 된다.

Champion별 입력은 [Champion System](champion-system.md), player 입력은 [Player System](player-system.md)을 참고한다.
