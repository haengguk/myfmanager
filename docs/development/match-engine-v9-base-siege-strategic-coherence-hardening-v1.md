# Match Engine V9 Base Siege Strategic Coherence Hardening V1

## 상태와 범위

최종 상태는 `MATCH_ENGINE_V9_BASE_SIEGE_STRATEGIC_COHERENCE_HARDENED`다. 이 작업은 이미 시작된 persistent siege의 다음 공격이 자기 기지의 terminal emergency를 무시하던 V9 결함만 수정한다. 구조물 HP/DPS/보상/respawn 수치, Matchup/Composition tuning, Jungle candidate 활성화, Draft, Series, frontend는 변경하지 않았다.

## 선행 재현과 root cause

Production 변경 전에 match-scoped state로 다음 fixture를 만들었다.

1. BLUE의 Baron cross-map OUTER siege를 active로 시작한다.
2. 같은 팀의 반대편 lane을 억제기와 두 Nexus turret까지 제거해 Nexus를 노출한다.
3. 10초를 진행해 기존 siege continuation을 due로 만든다.
4. `resolveActiveSieges()`를 호출해 상대 OUTER HP를 비교한다.

기존 production에서는 `BaseSiegeStrategicCoherenceTest.previousTickNexusEmergencyStopsActiveLowerTierSiegeBeforeMutation`이 HP 불변 assertion에서 실패했다. 즉 timeline 정렬이나 표시만의 문제가 아니라 `applyDamage`가 실제 `LaneStructureState` HP를 변경하고, 공격 sequence/wave/보상 경로까지 실행될 수 있는 gameplay 결함이었다.

조사 결과는 다음과 같다.

1. Tick은 시간/만료와 registry 정리, economy/combat, active siege, lane structure, post-fight/objective, mid/late macro, 일반 macro push 순서다.
2. 기존 `resolveActiveSieges()`는 `TeamSide.values()`를 한 번 순회하며 각 active siege의 stop reason을 읽은 직후 곧바로 공격을 변이했다.
3. `LateGameMacroResolver`는 심각한 base threat에서 새 `CROSS_MAP_PUSH` 선택은 막지만 이전 tick에 시작된 `BaseSiegeState` continuation에는 그 guard가 없었다.
4. 기존 `continuationStopReason()`은 match finish, expiry, attack budget, wave, target attackability, attacker 생존/참가 가능성, 최소 인원, defender return을 확인했지만 own-base Nexus emergency는 확인하지 않았다.
5. Late-game 신규 action용 base threat는 active siege 처리 뒤 state에서 계산됐고, active continuation 자체는 매 side 실행 직전 live state를 읽었다.
6. 따라서 한 tick 안에서 마지막 Nexus turret가 새로 파괴된 경우와 이전 tick부터 Nexus가 노출된 경우가 구분되지 않았고, 단순 inline guard는 BLUE-first 순회 비대칭을 만들 위험이 있었다.
7. 재현 fixture에서 실제 구조물 HP가 줄었으므로 root cause는 presentation이 아니라 active siege gameplay continuation이었다.

## V1 계약

### Tick-start 양진영 판정

Due인 양 팀 active siege를 mutation 전에 전부 평가한다. 각 평가는 `BaseThreatEvaluator`가 만든 자기 기지 `BaseThreatSnapshot`과 상대 active siege의 structured Nexus target을 사용한다. 평가 결과와 기존 stop reason을 immutable method-local record로 고정한 뒤, 모든 tick-start stop을 먼저 적용하고 허용된 continuation만 실행한다.

같은 tick에 한쪽의 마지막 Nexus turret가 파괴돼 새 threat가 생겨도 반대편의 tick-start 합법 행동을 소급 취소하지 않는다. 새 threat는 다음 tick continuation에 적용한다. 두 side를 뒤집은 mirror fixture가 같은 결과를 내므로 enum declaration order는 eligibility가 아니다.

### 자기 기지 emergency 중단

Tick 시작에 다음 structured condition 중 하나가 참이면 continuation을 중단한다.

- `BaseThreatEvaluator`가 자기 기지를 `NEXUS_THREAT`로 평가한다.
- 상대 active siege의 현재 `StructureTargetId.kind`가 자기 `NEXUS`를 가리킨다.

OUTER, INNER, INHIBITOR_TURRET, INHIBITOR target은 모두 lower-value로 분류한다. Baron buff나 기존 persistent 상태는 emergency 무시 권한이 아니다. 중단은 `attemptSiege()` 전에 일어나므로 구조물 HP/파괴, gold/plate, wave attack, attack sequence, structure action slot과 Random 소비가 추가되지 않는다. 기존 `releaseSiegeActivities()`를 그대로 사용해 participant activity를 끝내고 FARM restriction을 새로 연장하지 않는다. Duplicate 호출은 이미 inactive인 siege를 다시 처리하지 않는다.

### Base race와 정상 Nexus finish

현재 state에는 최소 참가자·wave·생존·attackability 외에 양 팀의 의도와 안전한 commit을 증명하는 별도 `BASE_RACE_COMMIT` 계약이 없다. 그래서 V1은 자기 기지 emergency 중 상대 Nexus continuation도 `BASE_RACE_REJECTED_FAIL_CLOSED`로 중단한다. Lower-tier target은 base race로 승격하지 않는다.

이 fail-closed 경계는 kill count를 읽지 않는다. 자기 기지가 안전하고 유효한 Baron wave와 공격 참가자가 있으면 양 팀 kill이 0이어도 기존 Nexus finish는 그대로 완료된다.

## 구현과 structured provenance

- `SiegeStopReason.OWN_BASE_EMERGENCY`를 추가했다.
- `SiegeContinuationDecisionReason`은 `CONTINUATION_ALLOWED`, `LOWER_VALUE_SIEGE_ABORTED_FOR_BASE_DEFENSE`, `BASE_RACE_REJECTED_FAIL_CLOSED`를 구분한다.
- `StructureActionData`에 nullable additive field `ownBaseThreatLevelAtDecision`, `strategicContinuationDecision`, `strategicallyAllowed`를 추가했다.
- Allowed continuation damage/destruction event와 stop event가 모두 같은 pre-mutation decision을 기록한다. Initial attack과 respawn처럼 continuation evaluation이 아닌 event는 새 필드가 `null`이다.
- Strategic stop을 permitted attack보다 먼저 적용해 어느 side가 Nexus를 마무리하더라도 반대편 emergency abort가 동일하게 기록된다. Match finish 뒤의 추가 active-siege structure event/mutation은 만들지 않는다.
- Resolver에는 match mutable state를 저장하지 않는다. `BaseThreatEvaluator`는 stateless read-only helper이고 결정 map/record는 호출 범위의 immutable data다.

공통 gameplay semantics 변경을 replay identity에서 구분하기 위해 active rules는 다음처럼 갱신했다.

| Profile 계열 | Active rules |
| --- | --- |
| Baseline / Matchup / Full / Production | `MATCH_SIMULATOR_PRE_JUNGLE_RULES_V4` |
| Jungle Economy candidate | `MATCH_SIMULATOR_JUNGLE_ECONOMY_RULES_V4` |
| Jungle Tempo candidate | `MATCH_SIMULATOR_JUNGLE_TEMPO_RULES_V3` |

Production engine은 계속 `MATCH_SIMULATOR_ENGINE_IMPLEMENTATION_V9`, profile은 `PRODUCTION_MATCHUP_COMPOSITION_V1`, configuration hash는 `caaf76274dc148040b0a95eae1ed5181790b2fc840f45af9b109ea7951c1fd5d`다. Policy canonical hash만 새 rules identity를 반영해 `3afaa399f7c2b20a940c7cfd7510f6c7962ba43eec575cc75ed55218d53f0ce9`로 바뀌었다.

## 검증

검증은 `lolmanager-verification`의 production/runtime-sensitive 경로를 적용했다.

- Pre-fix reproduction: 새 focused test가 기존 production에서 실패해 재현 gate 통과
- Post-fix focused: `BaseSiegeStrategicCoherenceTest` 8 tests, failures/errors/skipped 0
- Affected: structure lifecycle/slot/causality/target, simulator smoke, profile/provenance, Match Engine V1 contract, Jungle candidate provenance, same-seed/cross-JVM 13 suites / 115 tests, failures/errors/skipped 0
- Final backend regression: 239 suites / 2,282 tests / failures 0 / errors 0 / skipped 2, aggregate JUnit XML 2,041.953초, Gradle wall 18분 28초, `BUILD SUCCESSFUL`

Focused 검증은 emergency pre-mutation 중단, 네 lower-value target, Baron, activity/FARM release, duplicate, BLUE/RED mirror, same-tick/next-tick, base-race rejection, no-kill Nexus finish, diagnostics parity, terminal post-finish guard를 포함한다. 기존 `StructureEngineRedesignTest`가 defenders returned, wave lost, attacker killed와 attack budget stop을 계속 검증한다.

이번 수정은 production tuning을 바꾸지 않았고 correctness를 deterministic fixture로 증명할 수 있으므로 200-seed distribution diagnostic, calibration/holdout, historical acceptance artifact 재생성은 실행하지 않았다.

## 남은 제한과 다음 단계

- Explicit `BASE_RACE_COMMIT`을 허용하는 안전한 match-scoped eligibility는 아직 없다. 실제 base-race 지원은 참가자·wave/backdoor·생존·상대 finish ETA와 commit provenance를 별도 V2 계약으로 설계해야 한다.
- 별도의 recall/teleport/귀환 이동 simulation은 추가하지 않았다. 현재 중단은 가짜 수비 성공을 만들지 않고 participant siege activity만 공통 경로로 해제한다.
- 새 structured decision field는 API serialization에 additive하게 포함될 수 있지만 frontend 전용 시각화는 이번 범위가 아니다.
- Frontend 변경 금지 범위를 지켜 고정 policy hash를 검사하는 `frontend/scripts/verify-real-match-live-contract.mjs`는 이전 hash를 유지한다. Backend schema는 깨지지 않지만 해당 검증 스크립트의 expected hash 갱신과 current LIVE 재검증은 별도 frontend 후속 작업이다.
- 이번 deterministic 최소 재현은 관측된 실제 경기와 동등한 structured state를 직접 만들었다. 특정 관측 seed를 역추적한 balance 표본은 아니다.
