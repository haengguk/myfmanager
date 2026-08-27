# Match Simulation

## Entry Points

HTTP simulation은 `com.lolfm.controller.MatchController`의 `POST /api/matches/simulate`에서 시작한다.

1. request의 `seed`를 사용한다. 없으면 controller가 `System.currentTimeMillis()`로 새 seed를 만들고 response에 반환한다.
2. `ChampionSelectionValidator`가 explicit selection 또는 catalog의 `defaultSelection`을 10개의 `ChampionAssignment`로 변환한다.
3. `DummyDataFactory`가 현재 API용 blue/red team을 만든다.
4. `MatchSimulator.simulate(...)`가 `MatchTimeline`을 생성한다.
5. response에는 seed, 두 team, timeline, champion metadata가 포함된다.

현재 `DummyDataFactory` team은 legacy `PlayerAttributes`를 사용한다. production Player Ratings resource는 Spring catalog로 로드되지만 이 endpoint의 roster 생성에는 아직 사용되지 않는다. 자세한 내용은 [Player System](player-system.md)에 있다.

별도의 backend application entry point인 `RealDraftMatchOrchestrator`는 explicit LCK team code 두 개, caller-owned `SeriesDraftHistory`, match seed를 받는다. 이 path는 `LckTeamAssembler`가 만든 실제 Team으로 Draft를 실행하고 `FinalDraftResult.matchChampionAssignments()`를 그대로 같은 Team과 함께 simulator에 전달한다. `GET /api/v1/real-matches/options`와 `POST /api/v1/real-matches/simulate`가 이 경로를 additive하게 노출한다. 기존 `MatchController`나 `DummyDataFactory`를 거치지 않으며 세부 HTTP 계약은 [Real Match API V1](real-match-api-v1.md)에 있다.

Real Draft의 `AUTO_DRAFT_VARIETY_V1`은 match seed와 structured Draft context를 SHA-256으로 결속해 최고점 대비 2.0 이내 상위 3개 후보에서 deterministic weighted selection을 수행한다. 이 선택은 `Random` 객체를 사용하지 않으며 아래 simulator gameplay stream을 소비하지 않는다. 따라서 Draft selection trace와 simulator `randomFingerprint`는 서로 다른 provenance 축이다. Draft scoring/search 의미는 그대로이고, 공개 Real Match runtime은 `PRODUCTION_MATCHUP_COMPOSITION_V1`에서 Matchup `GEOMETRIC_V2`와 Composition `PRODUCTION_V2`를 실제 simulation에 적용한다.

Draft scoring/search 자체는 Random을 사용하지 않는다. 선수별 champion proficiency는 Draft evaluator 입력이지만 general player ratings는 아직 Draft scoring 입력이 아니며, final assignment 이후 Match Engine의 ability realization에 사용된다. Draft 단계의 Matchup/Composition 평가는 match runtime contribution 활성화와 별개다. Fresh Matchup/Composition requalification은 별도 milestone에서 비중첩 seed와 policy contract로 수행해야 한다.

동결된 application boundary인 `MatchEngineV1`은 완성된 roster/final Draft/seed를 immutable input으로 받아 immutable summary/timeline/provenance를 반환한다. `RealDraftMatchOrchestrator.orchestrateV1`이 이 경계에 additive하게 연결되며, 성공한 output까지 검증된 뒤에만 series history를 commit한다. Real Match API service는 fresh-history 단판 overload만 호출하고 policy/provenance/output hash를 검증한 뒤 immutable transport DTO를 반환한다. 정책, 입출력, hash와 호환성의 전체 계약은 [Match Engine V1 Contract](match-engine-v1.md)에 있다.

명시적 profile 인자가 없는 `RealDraftMatchOrchestrator` overload는 `MatchEngineV1Policy.authoritative()`에서 현재 production profile을 얻는다. Additive explicit-profile overload는 임의 boolean 묶음이 아니라 `SimulationRuntimeProfileId`만 받으며 `BASELINE_V1`을 명시적으로 선택해 롤백/비교할 수 있다. `ConfiguredMatchSimulatorFactory`의 public boundary도 profile ID와 별도 `SimulationInstrumentation`만 받아 closed registry를 내부에서 resolve한다. Caller-fabricated `ResolvedSimulationRuntimeProfile`은 실행/provenance 경계에서 허용하지 않는다.

## Explicit Runtime Profiles

여섯 profile의 공통 gameplay flag는 Lane Combat, FARM Recovery, Jungle Gank, Counter Gank, Roam, Objective Priority, Lane Phase, Mid/Late Macro, Objective Decision, Progression/Progression Power, Champion Power 모두 ON이다. Baseline, Matchup-only, Full candidate와 production profile은 Jungle Clear contribution이 `DISABLED_NOT_INTEGRATED`이고, 두 Jungle candidate만 각각 `ECONOMY_V1`, `ECONOMY_AND_GANK_TEMPO_V1`이다.

| Profile | Matchup | Composition | Jungle Clear | Configuration hash |
| --- | --- | --- | --- | --- |
| `BASELINE_V1` | `OFF` | `OFF` | `DISABLED_NOT_INTEGRATED` | `c8cc557bd721228c473e30d31b7258510f9608a18098578bc1da36e603536215` |
| `MATCHUP_ONLY_CANDIDATE_V1` | `GEOMETRIC_V2` | `OFF` | `DISABLED_NOT_INTEGRATED` | `58714464c19a2cffd108d47a93a0909126513c8bb10cb0e19bbd87f8e78532ec` |
| `FULL_SYSTEM_CANDIDATE_V1` | `GEOMETRIC_V2` | `PRODUCTION_V2` | `DISABLED_NOT_INTEGRATED` | `caaf76274dc148040b0a95eae1ed5181790b2fc840f45af9b109ea7951c1fd5d` |
| `PRODUCTION_MATCHUP_COMPOSITION_V1` | `GEOMETRIC_V2` | `PRODUCTION_V2` | `DISABLED_NOT_INTEGRATED` | `caaf76274dc148040b0a95eae1ed5181790b2fc840f45af9b109ea7951c1fd5d` |
| `FULL_SYSTEM_WITH_JUNGLE_ECONOMY_CANDIDATE_V1` | `GEOMETRIC_V2` | `PRODUCTION_V2` | `ECONOMY_V1` | `e04869bca5281f7f416c8191d7bf1b5be04b3129f33f6dfd4de83e8d8e92743b` |
| `FULL_SYSTEM_WITH_JUNGLE_TEMPO_CANDIDATE_V1` | `GEOMETRIC_V2` | `PRODUCTION_V2` | `ECONOMY_AND_GANK_TEMPO_V1` | `c835280cbaa1244f4fecb099b19f71111c6d77aa1aeb1b7110a6e86e6381451c` |

`BASELINE_V1`은 이름만 OFF 묶음이 아니라 legacy Spring `@Autowired MatchSimulator`의 13 gameplay booleans와 두 mode를 모두 snapshot한 명시적 롤백 profile이다. Acceptance-time fixed fixture는 current tree에서 explicit baseline을 fresh state로 두 번 실행해 complete timeline/Random/output equality를 검증한다. Matching V9 pre-activation immutable output oracle이 없으므로 이를 cross-commit byte parity로 해석하지 않는다. Production과 Full candidate의 configuration hash가 같은 것은 profile ID가 아니라 gameplay configuration만 hash하는 계약에 따른 의도된 alias다. 둘은 runtime profile ID, production policy와 Match Engine V1 replay binding으로 구분된다. `ChampionMatchupMode.ON`, Composition `SHADOW`/`CANDIDATE` 같은 historical/internal audit path는 application profile로 선택할 수 없다.

Diagnostics는 gameplay configuration 밖의 instrumentation이다. ON/OFF가 `SimulationOptions.diagnosticsEnabled`만 바꾸며 configuration/replay hash와 timeline을 바꾸지 않는 exact equality test가 있다.

Baseline, Matchup-only, Full candidate와 production profile은 `activeGameplayRulesVersion=MATCH_SIMULATOR_PRE_JUNGLE_RULES_V3`를 공유한다. Pure-JRM Jungle Economy candidate는 `MATCH_SIMULATOR_JUNGLE_ECONOMY_RULES_V3`, Jungle Tempo candidate는 `MATCH_SIMULATOR_JUNGLE_TEMPO_RULES_V2`를 사용한다. V3/V2 갱신은 V9 구조물 내구도·지속 공성 규칙이 모든 profile에 공통으로 적용된 사실을 식별한다. 이 version은 profile의 configuration hash와 별개로, configuration 밖의 공통 production rule semantics를 식별한다.

## Match Engine V1 Policy Boundary

Match Engine V1의 authoritative application policy는 `PRODUCTION_MATCHUP_COMPOSITION_V1` 하나이며 Matchup `GEOMETRIC_V2`, Composition `PRODUCTION_V2`, Economy/Tempo candidate activation `false`를 고정한다. 제품 상태는 `PRODUCT_ACCEPTED_WITH_KNOWN_LIMITATIONS_NOT_STATISTICAL_HOLDOUT`이고 statistical holdout approval은 `false`다. Matchup causal lineage 399/400 unresolved와 Composition Nexus/ending 9.25% sensitivity를 순서가 고정된 risk set으로 노출한다. Caller는 V1 facade에 profile ID나 gameplay boolean을 전달할 수 없고 오류 시 `BASELINE_V1`로 자동 fallback하지 않는다. `SimulationOptions.productionDefaults()`는 값이 일치해도 별도의 저수준 constructor default일 뿐 제품 authority로 해석하지 않는다.

V1은 invalid roster/position/player/final assignment/Draft/policy와 illegal champion-role을 simulator 및 seeded `Random` 생성 전에 거부한다. 성공 경로에서는 existing simulator와 common gameplay rules를 그대로 실행한 뒤 structured winner/end reason, final snapshot, stable participant identity, full provenance를 immutable output으로 투영한다. 기존 simulator와 Real Draft overload는 제거하거나 의미를 바꾸지 않았다.

## Configuration and Replay Provenance

`RealDraftMatchResult.executionProvenance`는 새 orchestration 결과에서 non-null이며 다음 identity를 분리한다. 기존 직접 constructor 호환 경로에서는 nullable이다.

- `configurationHash`: profile ID와 diagnostics를 제외한 field-complete gameplay configuration만 SHA-256으로 고정한다.
- `resourceProvenanceHash`: Champion manifest/catalog/Power/Matchup/Composition/Jungle Clear, Player Identity/Ratings/Proficiency, Draft Meta의 version/path/raw SHA와 semantic hashes를 고정한다.
- `engineImplementationVersion`: simulator 구현 계열을 식별한다. 현재 `MATCH_SIMULATOR_ENGINE_IMPLEMENTATION_V9`다. V8은 authoritative player rating/proficiency 실행을 추가했고, V9은 명시적 구조물 target, 전체 구조물 HP, match-scoped 지속 공성/웨이브, 구조화된 중복 방어와 넥서스 포탑 재생성을 통합했다.
- `activeGameplayRulesVersion`: 선택한 profile이 사용하는 공통 gameplay rule semantics를 식별한다. Baseline, Matchup-only, Full candidate와 production profile은 `MATCH_SIMULATOR_PRE_JUNGLE_RULES_V3`, pure-JRM Jungle Economy candidate는 `MATCH_SIMULATOR_JUNGLE_ECONOMY_RULES_V3`, Jungle Tempo candidate는 `MATCH_SIMULATOR_JUNGLE_TEMPO_RULES_V2`다.
- `replayProvenanceHash`: configuration, engine implementation, active gameplay rules, resource snapshot, side/team/roster, seed, series-history-before, Draft rules/scoring/selection policy, selection trace hash, ordered draft decision, final draft와 final assignment를 고정한다. Profile alias와 instrumentation은 제외한다. Match Engine V1은 이 legacy identity와 전체 `MatchEngineV1Input.inputHash`를 별도 V1 replay binding으로 다시 묶어 명시적 rating/proficiency snapshot도 재현 입력에 포함한다.
- `timelineHash`: sorted-property/map-key canonical JSON으로 complete events/snapshots/winner/duration output을 고정한다.
- `randomFingerprint`: match의 seeded `Random.next(bits)` draw count와 resolver context/value의 ordered SHA-256을 기록한다. Gameplay input이 아닌 observational output이므로 configuration/replay hash에는 넣지 않는다.

기존 `FinalDraftResult.draftIdentity()`는 series commit idempotency용 ordered decision identity로 유지한다. Draft selection policy/trace hash와 provenance의 final draft/assignment hash가 그보다 넓은 replay 범위를 additive하게 담당한다.

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
| Real Match API V1 (`RealDraftMatchOrchestrator.orchestrateV1`) | ON | `GEOMETRIC_V2` | `PRODUCTION_V2` | OFF |
| 명시적 `SimulationOptions.productionDefaults()` | ON | `GEOMETRIC_V2` | `PRODUCTION_V2` | OFF |
| `RealDraftMatchOrchestrator` 암묵적 overload | ON | `GEOMETRIC_V2` | `PRODUCTION_V2` | OFF |
| explicit `BASELINE_V1` 롤백/비교 경로 | ON | `OFF` | `OFF` | OFF |
| `MATCHUP_ONLY_CANDIDATE_V1` | ON | `GEOMETRIC_V2` | `OFF` | OFF |
| `FULL_SYSTEM_CANDIDATE_V1` | ON | `GEOMETRIC_V2` | `PRODUCTION_V2` | OFF |
| `PRODUCTION_MATCHUP_COMPOSITION_V1` | ON | `GEOMETRIC_V2` | `PRODUCTION_V2` | OFF |
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

### Structure V9

구조물 판정은 stateless `StructureResolver` 하나가 담당하고 mutable 상태는 현재 match의 `GameState`, `MapState`, `LaneStructureState`, `BaseState`, `BaseSiegeState`, `LaneWaveState`에만 둔다. Base 목표를 `Lane.MID` 같은 sentinel로 표현하지 않고 `StructureTargetId`의 defending side, lane, tier, nexus turret index로 식별한다. 동일 행동은 `(simulation time, attacking side, siege action ID, attack sequence)`로 예약·commit하므로 중복 호출이 다음 구조물로 retarget되거나 보상·이벤트·Random을 두 번 만들 수 없다. 종료된 match의 모든 구조물 mutation은 거부한다.

| 구조물 | 최대 체력 |
| --- | ---: |
| 외곽 포탑 | 9,000 |
| 내부 포탑 | 5,000 |
| 억제기 포탑 | 4,750 |
| 억제기 | 4,000 |
| 넥서스 포탑 | 3,500 |
| 넥서스 | 5,500 |

모든 공격 경로는 살아 있음/파괴됨 boolean 대신 공통 부분 피해를 사용한다. 포탑은 영구 plate threshold와 local plate gold, first-turret local bonus, global tower gold를 공통 보상 경로에서 한 번만 지급한다. 넥서스 포탑 두 개는 개별 HP와 파괴 시각을 가지며 정확히 180초 뒤 40% HP로 재생성된다. 억제기는 300초 뒤 full HP로 복구된다.

공성은 eligibility를 모두 통과한 뒤에만 시작하고 그 전에는 Random, activity, FARM block, 구조물 slot을 소비하지 않는다. 일반/Baron/post-fight 공성은 해당 lane의 준비된 wave와 실제 생존 참가자를 요구하며 local defender 수에 따라 피해가 감소한다. Base 목표는 최소 3명이 필요하고, 미달이면 일반 lane 경로로 fallthrough하지 않는다. Wave가 없는 backdoor는 별도 mode로 10% 피해와 한 번의 공격 기회만 허용한다.

`BaseSiegeState`는 한타 승리당 구조물 하나로 끊지 않고 10초 간격의 다음 공격으로 이어진다. 공격자 사망, 수비자 복귀, wave 소멸, 공격 기회/기간 만료, 보호된 target 또는 match 종료가 공성을 중단한다. 마지막 넥서스 포탑을 파괴한 wave에는 제한된 nexus commit 기회가 생겨 현실적인 `억제기 → 쌍둥이 → 넥서스` 전환을 허용한다. 한 tick에는 한 구조물 mutation만 발생한다.

Counter-gank는 독립적인 parallel combat이 아니라 selected Jungle Gank attempt 안에서 resolver response로 실행된다. Objective fight와 late-game siege도 기존 teamfight/common kill path를 재사용한다.

### Jungle Tempo V1-B

Tempo candidate는 Jungle Economy V1-A의 `combinedEfficiency`를 별도 재계산하지 않고 actual successful outcome에서만 받는다. 사망, FARM recovery/macro block, non-default activity, gank/counter-gank FARM block으로 economy outcome이 없으면 credit과 Jungle Economy Random도 발생하지 않는다.

현재 V1 rules는 efficiency를 `0.85..1.15`로 clamp한 뒤 `elapsedSeconds × boundedEfficiency`를 credit으로 더한다. 첫 actual action readiness는 180초, 반복 readiness와 actual action cost는 150초, bank cap은 240초다. 마지막 successful economy outcome과 다음 outcome의 간격이 30초를 초과하면 기존 bank를 reset하고 현재 tick의 credit부터 다시 쌓는다. Readiness는 평가 시각과 같은 tick의 economy outcome도 요구한다.

Gank와 counter-gank는 기존 alive/cooldown/lane participant eligibility를 먼저 통과한 뒤 readiness를 확인하고, ready side만 trigger/response Random을 소비한다. Failed trigger, failed counter response, duplicate call과 ineligible path는 credit/action state를 소비하지 않는다. 실제 `NO_KILL`을 포함한 gank는 공격 side credit을 한 번 소비하고, successful counter response가 actual attempt를 시작하면 수비 side credit도 한 번 소비한다. Not-ready gank가 actual attempt를 만들지 않으면 Roam과 Lane Combat priority fallthrough는 유지된다.

Tempo는 objective attempt eligibility, probability 또는 reward에 직접 연결되지 않는다. 다만 actual gank/counter-gank kill이 기존 objective-priority state를 변경하는 간접 경로는 그대로 존재한다. `PATHING`은 credit 계산에 들어가지 않고 readiness 이후의 기존 gank trigger chance에만 사용되며, champion proficiency도 clear/JRM economy multiplier를 보정하지 않는다.

### Jungle V1 focused hardening

Batch C는 새 정글 행동이나 밸런스 수치를 추가하지 않는다. 기존 gank side 평가는 `NONE`, jungler unavailable, shared jungle-action cooldown, eligible lane 부재, Tempo not-ready로 구조화하고, 기존 counter-gank ineligibility도 실제 defender response 평가마다 함께 기록한다. V5에서는 `isAlive`와 non-default `PlayerActivityType`을 별도로 확인해 살아 있는 roaming participant를 `DEAD`로 집계하지 않는다. Match-scoped diagnostics는 reason별 canonical enum map과 side별 latest decision을 immutable snapshot으로 노출한다. Gameplay resolver는 이 통계를 읽지 않으며 reason 기록 전후의 eligibility 순서와 Random 호출 순서는 같다. V6는 이 gameplay 의미를 바꾸지 않고 Champion Power diagnostic 집계의 process-level 결정성만 고정한다.

Focused gate는 다음 교차 계약을 한 묶음으로 검증한다.

- 매 Jungle Gank evaluation은 BLUE/RED 각각 한 개의 structured decision을 만들고 `NONE` 수는 trigger roll 수와 같다.
- 실제 gank마다 counter-gank eligibility가 한 번 평가되며 actual gank/counter-gank 수와 Tempo consumption 수가 각각 같다.
- 180초 FARM 뒤 시작한 actual gank는 이미 받은 그 tick의 CS/gold/XP를 취소하지 않고 190/200초의 future FARM만 놓친다. Passive gold는 계속 지급되고 210초 경계에서 catch-up 없이 재개한다.
- death/FARM recovery와 action block이 겹치면 가장 늦은 boundary까지 유지한다. Macro FARM block도 CS/FARM gold/XP/Tempo credit/Jungle Economy Random을 모두 막되 passive gold는 유지한다.
- duplicate/ineligible path, priority fallthrough, 한 tick 한 major combat, common reward/death path, structured summary/KILL 연결, fresh-match state와 same-seed replay를 함께 검증한다. Major-combat gate는 종류 집합이 아니라 actual summary marker의 multiplicity를 유지하므로 같은 tick의 같은 종류 중복도 실패하며, 연결된 `KILL`은 별도 attempt로 세지 않는다.

당시 다섯 profile의 configuration hash와 `MATCH_SIMULATOR_JUNGLE_TEMPO_RULES_V1`은 바뀌지 않았다. Engine implementation은 Batch C V4/V5 뒤 determinism V6로 올라가 replay provenance hash는 의도적으로 달라지지만, 기존 네 profile 12경기의 complete timeline과 Random fingerprint는 Pre-Tempo oracle과 exact parity다. B1 artifact도 두 fresh JVM에서 7/7 byte-identical하다. 이 gate는 correctness hardening이며 calibration이나 `PRODUCTION_V1` 채택 결정이 아니다.

### Final 13G-B real-data audit boundary

B1 audit harness는 production match engine 바깥의 test-side consumer다. Production `RealDraftMatchOrchestrator`가 실제 LCK roster, Draft rules/policy, Hard Fearless history와 `FinalDraftResult`를 준비하면 harness가 그 결과를 fixture로 고정한다. 이후 profile별 match는 같은 `Team`, `MatchChampionAssignments`, series history identity와 seed를 사용한다. 따라서 비교 단위는 “profile마다 다시 뽑은 Draft”가 아니라 “같은 Draft를 서로 다른 gameplay configuration으로 실행한 결과”다.

```text
실제 team codes + series game
  → production orchestration으로 series target까지 준비(G1=1회, G2=2회)
  → 고정 roster / final Draft / final assignment
  → 같은 fixture + 같은 seed
      ├─ BASELINE
      ├─ MATCHUP ONLY
      ├─ FULL
      ├─ FULL + JUNGLE ECONOMY
      └─ FULL + JUNGLE TEMPO
  → profile별 provenance / timeline / Random / structured diagnostics
```

Schedule identity는 10-team G1 양방향 전수 90 fixtures, all-team Hard Fearless G2 양방향 10 fixtures, calibration/holdout seed split 전체를 SHA-256으로 고정한다. Dry-run/calibration/holdout lane은 seed derivation domain부터 분리한다. Writer는 전달된 schedule content로 hash를 재계산하고 canonical frozen instance와 exact equality를 확인하므로 기존 hash 문자열을 붙인 변경 fixture를 거부한다. Prepared fixture도 public constructor가 없고 production orchestration을 완료한 harness만 생성할 수 있다. Report writer에는 실행 시각이나 wall duration을 넣지 않고 canonical JSON, stable CSV ordering과 SHA manifest만 기록한다. Generated report는 관찰 증거이며 gameplay input이나 baseline이 아니다.

Test-side bridge는 package-private simulator result의 combat/roam/objective/lane/macro/structure/progression/Jungle/Champion Power/Matchup/Composition/Combat Outcome diagnostic snapshot과 history를 모두 보존하지만 production visibility를 넓히지 않는다. Same-seed replay는 이 전체 record의 exact equality를 확인한다. Artifact에는 structured map key까지 명시적으로 정렬하는 canonical SHA-256과 domain별 명시적 오류 카운터를 기록하고, Random raw trace는 별도 fingerprint가 담당한다. 정상적인 rejection/ineligible 관찰은 structural error로 세지 않는다. 진단은 gameplay state와 Random의 입력이 아니며 profile configuration hash에도 포함되지 않는다. B1은 runtime/API/frontend와 tuning을 변경하지 않고, calibration·holdout·`PRODUCTION_V1` 판단도 수행하지 않는다.

#### B2 calibration execution boundary

B2는 B1 schedule의 `CALIBRATION` seed만 job으로 materialize한다. 각 fixture에서 production orchestration은 series target game까지 한 번만 준비되고, 그 fixed Draft/assignment를 24 seeds × 5 profiles에 재사용한다. 네 shard class는 `forkEvery=1`로 JVM 재사용을 금지하고 fixture index modulo 4로 소유권을 나누므로 mutable match state나 output path를 공유하지 않는다. 실제 동시 실행 수가 Gradle worker 제한으로 줄어도 네 shard는 각각 fresh JVM이다. 한 fixture의 120행과 BASELINE replay가 모두 current guard를 통과한 뒤에만 임시 파일을 atomic move해 checkpoint로 승격한다.

```text
fresh JVM A/B B1 artifact exact equality
  → canonical B1 manifest와 gate manifest binding
  → fixture preparation / fixed Draft
  → 24 calibration seeds × 5 profiles
  → fixture BASELINE replay exact equality
  → replay provenance 재계산 + row/fixed-Draft/replay payload digest
  → atomic 120-row authenticated checkpoint
  → shard worker receipt(checkpoint raw SHA + JVM identity)
  → 4 distinct fresh JVM / 100 payload digest / frozen job order 재검증
  → artifact-only summaries / integrity / SHA manifest
```

Run guard는 schedule/configuration/resource/engine/Draft rule set/Draft scoring policy/production source/B1+B2 harness source identity를 포함한다. Validator는 각 row의 job/fixture/roster/Draft/profile/seed로 production replay provenance를 재계산한다. Checkpoint row는 timeline, full structured diagnostics와 Random fingerprint hash, final result, domain별 integrity, 정글러 final state와 고정 시각 관측을 보존하고 이 전체 serialized payload를 canonical digest에 묶는다. Shard 완료 receipt는 checkpoint raw bytes를 다시 SHA-256으로 묶으며 finalizer만 네 receipt를 검증해 공식 artifact writer를 열 수 있다. Synthetic validation은 별도 non-official status이고, guard가 달라진 기존 checkpoint는 merge하거나 덮어 해석하지 않고 거부한다.

Simulator는 보통 10초 tick으로 snapshot을 만들지만 structure push가 respawn/공격 완료 시각으로 clock을 전진시켜 600/900/1,200/1,500/1,800초를 정확히 건너뛸 수 있다. B2는 없는 상태를 보간하지 않는다. 요청 시각 이상인 첫 recorded snapshot을 선택하고 두 시각을 함께 기록하며, match가 요청 시각 전에 끝났다면 final snapshot을 해당 checkpoint로 복제하지 않는다.

B2 status `CALIBRATION_EVIDENCE_READY_FOR_REVIEW`는 job/replay/checkpoint payload/worker receipt/structural integrity가 깨끗하다는 뜻이다. Winner flip, gold/CS/XP/level, duration, action count 분포는 review-only balance signal이며 correctness pass나 `PRODUCTION_V1` 승인을 뜻하지 않는다. Candidate와 acceptance gate를 calibration 결과로 먼저 freeze한 뒤에만 별도 holdout lane을 열 수 있다.

#### B3 frozen holdout execution boundary

B3는 B2 artifact identity, schedule/configuration/resource/engine/Draft identity와 production/B1/B2/B3 source identity를 하나의 immutable contract에 묶는다. Source guard는 production과 phase별 harness/Gradle block을 별도로 canonicalize한다. 따라서 B3 전용 task의 단순 추가는 B2 의미를 오염시키지 않지만, B1/B2 worker의 `forkEvery`, shard/JVM 또는 task 설정 변경은 해당 guard를 바꾼다. Contract hash 없이 official runner를 시작할 수 없고 runner는 contract를 재생성하거나 threshold를 수정하지 않는다.

```text
B2 calibration evidence(holdout 0)
  → Economy=ECONOMY_MINUS_FULL / Tempo=TEMPO_MINUS_ECONOMY 범위 고정
  → 7 exact behavior gates + 67 numeric gates 고정
  → canonical contract/hash + shard별 one-time authorization
  → 8 holdout seeds × 5 fixed profiles = fixture당 40 rows
  → atomic authenticated checkpoint + worker receipt
  → 4 distinct fresh JVM / 100 checkpoints / 4,000 rows 재검증
  → evidence verdict와 Economy/Tempo verdict를 독립 기록
```

연속값과 비율의 numeric gate는 B2와 B3 표본 수를 함께 반영한 99% two-sample normal prediction interval이며 소수 12자리에서 바깥쪽으로 반올림하고 양 끝을 포함한다. G1, G2, 전체를 분리하고 side/orientation gap도 독립 gate로 평가한다. 이는 구조적 correctness gate와 분리된다. 명시적 product tolerance가 없는 Tempo winner sensitivity는 interval 통과만으로 자동 승인하지 않고 `REVIEW_REQUIRED`다.

각 checkpoint는 complete match row, outcome, full structured diagnostics, Random fingerprint, timeline/replay provenance, fixed-time Jungle observations, 10명 final player state, team/Jungle result, row/fixed-Draft/replay/combined digest를 보존한다. 각 worker는 자기 `.authorized` 파일을 atomic move해 `.started`로 소비하므로 완료 receipt가 있는 같은 official holdout을 다시 실행할 수 없다. 같은 frozen contract/run guard의 도구 장애만 authenticated checkpoint resume가 가능하다. Finalizer는 receipt/fixture modulo ownership/JVM identity/checkpoint raw SHA를 다시 결합하며 synthetic path는 공식 READY status를 만들 수 없다.

공식 B3는 G1 3,600, G2 400으로 총 4,000 holdout matches를 한 번 실행했다. Replay 100/100 exact, unique job/provenance 4,000/4,000, distinct JVM 4/4, calibration execution 0, structural/domain error 0, SUPPORT FARM CS 0, timeout 0이다. Evidence status는 `HOLDOUT_EVIDENCE_READY_FOR_FINAL_REVIEW`; Economy는 frozen G1 winner-flip gate 하나를 근소하게 넘겨 `FAIL`, Tempo는 모든 structural/numeric gate를 통과했지만 높은 민감도의 product tolerance가 없어 `REVIEW_REQUIRED`, production decision은 `NOT_EVALUATED`다. 이 결과는 Final 13G-B의 입력이며 runtime default나 gameplay tuning을 직접 바꾸지 않는다.

#### Final synthesis and Production V1 boundary

Final 13G-B는 simulator나 resolver가 아니라 B2/B3 artifact의 test-side read-only consumer다. B2/B3 raw manifest와 review cross-reference가 exact일 때만 결정을 만들며 새 seed, `Random`, Draft orchestration 또는 match state를 생성하지 않는다. Paired rows는 `fixtureId`, team code, stable player ID, champion ID와 side로 결합한다. Display nickname, event message, description과 array index는 gameplay attribution이나 segment key로 사용하지 않는다.

Final 13G-B 당시 retained runtime evidence는 별도 test-side inspector가 actual production objects에서 만들었다. Closed registry의 `BASELINE_V1`을 resolve하고 당시 RealDraft 기본 overload와 explicit BASELINE, Spring autowired simulator, `POST /api/matches/simulate` controller injection을 fixed seed로 실행해 parity를 확인했다. Production source tree는 main Java/resources/settings와 B1/B2/B3 block을 제외한 production build contract에서 다시 계산하고, resource/Draft/rules/engine identity는 실제 RealDraft execution provenance에서 읽었다. Standalone synthesis는 frozen evidence raw SHA와 내부 ordered-lines runtime identity hash를 모두 다시 검증하므로 caller가 임의 profile/hash/wiring 값을 self-sign해 READY를 만들 수 없었다.

```text
B2 manifest/review + B3 manifest/review/frozen gate
  → raw SHA와 B2↔B3 binding 검증
production registry/actual wiring
  → canonical RuntimeIdentityEvidence + frozen raw/internal SHA
  → Economy−Full / Tempo−Economy paired population만 선택
  → fixture/team/side/player/champion/player×champion/matchup 분해
  → calibration↔holdout segment 재현성 + 방향성 합성
  → Production Decision V2 (candidate activation false, runtime identity EXACT)
```

당시 결정은 `KEEP_CURRENT_RUNTIME_DEFAULT`이며 retained application runtime은 `BASELINE_V1`이었다. Configuration hash는 `c8cc557bd721228c473e30d31b7258510f9608a18098578bc1da36e603536215`, active rules는 `MATCH_SIMULATOR_PRE_JUNGLE_RULES_V2`, engine은 `MATCH_SIMULATOR_ENGINE_IMPLEMENTATION_V6`였다. Final evidence와 runtime identity가 모두 exact일 때만 `READY_FOR_MATCH_ENGINE_V1_FREEZE`였으며, runtime evidence가 없거나 frozen identity와 다르면 `BLOCK_MATCH_ENGINE_V1_FREEZE_RUNTIME_IDENTITY_UNBOUND`였다. 이 historical evidence는 현재 V9 activation의 승인 근거로 재해석하거나 다시 쓰지 않는다.

Economy의 frozen `FAIL`은 한 discrete G1 flip 경계라는 설명을 붙이되 승인으로 다시 이름 붙이지 않는다. Tempo의 10개 공통 champion bucket 민감도 순서는 calibration↔holdout unweighted Pearson 0.906으로 재현됐다. 이 분석은 pre-registered decision gate가 아니며 player/team/fixture/matchup confounder를 격리하지 않았으므로 champion의 독립 효과나 인과관계를 뜻하지 않는다. Winner flip 자체도 Random trajectory sensitivity이며 product tolerance가 정의되지 않았다. 따라서 Economy/Tempo candidate configuration은 계속 explicit audit profile로 존재하되 Production V1 기본 runtime에는 들어가지 않는다.

이 historical 결정은 production configuration enum이나 HTTP default를 변경해 “OFF”를 새로 구현한 것이 아니었다. 당시 존재하던 default를 유지하고 두 Jungle candidate를 활성화하지 않는 범위 결정이었다. 이후 Real Match API와 현재 Matchup/Composition production activation은 별도 versioned policy이며, legacy `POST /api/matches/simulate`의 autowired `MatchSimulator`와 `DummyDataFactory` 경로는 계속 보존한다. `SimulationOptions.productionDefaults()`는 Matchup `GEOMETRIC_V2`, Composition `PRODUCTION_V2`, Jungle contribution OFF인 저수준 constructor default(configuration hash `caaf76274dc148040b0a95eae1ed5181790b2fc840f45af9b109ea7951c1fd5d`)로, 값이 같아도 authoritative application policy가 아니다. 향후 Economy 수정 또는 Tempo V2는 소비된 B3 seed나 gate를 재사용하지 않고 새 candidate identity, calibration 계획, product tolerance와 fresh holdout을 가져야 한다.

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
- real orchestration replay는 동일 team codes, fresh/equivalent series history, authored resources, match seed에서 Draft selection traces/decisions, final roles, assignment, events, snapshots, winner와 duration이 모두 같다.
- ineligible action, duplicate evaluation, 실행되지 않는 branch, diagnostics는 불필요한 Random을 소비하면 안 된다.
- API/timeline에 노출되는 enum set은 immutable `EnumSet` declaration order로 canonicalize한다. Hash/summary/serialization은 `Set.copyOf`의 JVM별 iteration order에 의존하지 않는다.
- Champion Power 참가자 map과 평균은 Blue/Red × TOP/JUNGLE/MID/ADC/SUPPORT의 명시적 `PlayerKey` 순서로 canonicalize한다. `Map.copyOf().values()` iteration order로 부동소수점 합산 순서를 결정하지 않는다.
- request에 seed가 없으면 매 요청마다 wall-clock seed가 선택되므로 자동으로 같은 결과가 나오지 않는다. response seed를 다시 보내야 replay할 수 있다.
- structured timeline 전체—participants, outcome, rewards, objectives, structures, winner—가 same-seed 회귀의 대상이다.
- legacy replay provenance는 resource-backed 실행 입력을 식별하고, Match Engine V1 replay provenance는 여기에 전체 immutable input hash를 추가 결속한다. Timeline hash와 Random fingerprint는 그 입력에서 나온 complete output/consumption을 별도로 식별한다.

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

## V9 candidate application provenance와 artifact canonicalization

Matchup candidate diagnostics는 실제 consumer가 입력을 읽는 지점마다 match-scoped `ChampionMatchupApplicationProvenance`를 기록한다. Logical consumer slot은 simulation time, application point, side/perspective, position/player/champion pair, lane/context/stage와 structured action 또는 pressure mutation identity로 구성하고, pair application과 before/after/delta 같은 semantic payload는 별도로 결속한다. 같은 slot과 exact payload는 idempotent duplicate이고 같은 slot과 다른 payload는 conflicting duplicate error다. 서로 다른 action 또는 pressure mutation은 같은 tick이어도 별도 application이다. Resolver 자체는 stateless이며 이 기록은 gameplay state와 Random을 바꾸지 않는다.

Actual major-combat attempt가 확정된 뒤 생성되는 `CombatActionIdentity`만 combat Matchup consumer에 전달한다. Pure evaluation, ineligible branch 또는 attempt 이전에는 진단용 가짜 ID를 만들지 않는다. 동일 combat의 summary/KILL/ASSIST는 하나의 structured action identity를 공유한다.

Lane pressure는 match-scoped monotonic mutation version, before/after, Matchup input delta와 clamp effect를 기록한다. 이후 action eligibility/score/selection이 그 exact pressure version을 실제 읽으면 consumer action ID를 별도 provenance로 남긴다. Direct cause는 동일 action/time/context/stage의 non-zero input에만, indirect cause는 Matchup이 변경한 exact state version과 이후 consumer action이 결속될 때만 인정한다. State 차이는 있지만 consumer binding이 불완전하면 `UNRESOLVED_SNAPSHOT_CAUSE`, 어느 경로로도 설명되지 않으면 `UNEXPLAINED_PUBLIC_DIVERGENCE`다. 시간상 앞선 application만으로 indirect cause를 만들지 않는다.

Real Match/Match Engine input은 Auto Draft selection trace schema와 hash algorithm을 additive identity로 결속한다. V2 trace hash는 fixed-point evidence만 사용하고 authoritative validator가 policy/weight/draw/context/roster/history/seed를 재검증한다.

Official diagnostic payload도 gameplay invariant와 같은 canonical collection 규율을 지켜야 한다. [fresh 재검증 V1](../development/match-engine-v9-auto-draft-matchup-composition-fresh-requalification-v1.md)은 raw checkpoint 100개를 정상 기록했지만 `PairObservation.divergenceActionIds`의 `Set.copyOf` iteration order 때문에 fresh-JVM deserialize/reserialize digest가 달라져 holdout 전에 차단됐다. V2는 null/blank 거부, 중복 제거와 lexicographic immutable List, typed/raw-tree canonical digest와 fresh-JVM A/B byte equality를 적용했다.

[fresh 재검증 V2](../development/match-engine-v9-auto-draft-matchup-composition-fresh-requalification-v2.md)는 serialization과 duplicate/binding correctness를 통과했지만 Matchup 공개 divergence 400쌍 중 direct 1, indirect 0, unresolved 399를 기록해 holdout 전에 차단됐다. 이는 시간 선행을 인과로 승격하지 않은 의도된 fail-closed 결과다. 다음 버전은 pressure state/version을 실제로 읽는 downstream action의 consumer binding을 완성해야 하며, unresolved exact-zero gate나 이미 소비한 seed를 완화해서는 안 된다.
