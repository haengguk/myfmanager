# Match Engine V9 Matchup/Composition Production Activation

## 상태

`MATCH_ENGINE_V9_MATCHUP_COMPOSITION_PRODUCTION_ACTIVATED`

2026-08-27 기준 공개 Real Match V9 runtime을 `BASELINE_V1`에서 `PRODUCTION_MATCHUP_COMPOSITION_V1`로 전환했다. Champion Matchup `GEOMETRIC_V2`와 Team Composition `PRODUCTION_V2`가 실제 경기 simulation에 적용되며 Jungle Economy/Tempo candidate는 계속 비활성화다.

Machine-readable 제품 결정은 다음과 같다.

- decision: `PRODUCT_DECISION_ACCEPT_WITH_KNOWN_DIAGNOSTIC_LIMITATION`
- known limitation: `MATCHUP_CAUSAL_LINEAGE_UNRESOLVED_399_OF_400_CALIBRATION_PUBLIC_DIVERGENCES`
- statistical holdout approved: `false`

이 결정은 `FRESH_REQUALIFICATION_PASSED`, `STATISTICALLY_ACCEPTED`, `HOLDOUT_PASSED`를 뜻하지 않는다. 직전 V2 calibration의 canonical evidence는 정상이었지만 Matchup causal lineage gate가 완결되지 않아 새 holdout은 승인·시작·소비되지 않았다. 제품 소유자가 이 한계와 관측 민감도를 인지하고 Draft의 Matchup/Composition 의미를 실제 gameplay에 연결하기 위해 위험을 수용한 결정이다.

## 이전과 이후

| 경계 | 이전 | 이후 |
| --- | --- | --- |
| 공개 Real Match profile | `BASELINE_V1` | `PRODUCTION_MATCHUP_COMPOSITION_V1` |
| Matchup runtime | `OFF` | `GEOMETRIC_V2` |
| Composition runtime | `OFF` | `PRODUCTION_V2` |
| Jungle Clear contribution | `DISABLED_NOT_INTEGRATED` | `DISABLED_NOT_INTEGRATED` |
| Jungle Economy/Tempo candidate | `false` / `false` | `false` / `false` |
| Rollback | baseline이 암묵적 기본값 | `BASELINE_V1`을 explicit policy 변경으로 선택 가능 |

기존 `POST /api/matches/simulate`의 Dummy roster/autowired simulator 호환 경계는 변경하지 않았다. 전환 대상은 실제 roster, Auto Draft와 Match Engine V1을 사용하는 공개 Real Match API 및 profile 인자가 없는 application orchestration 기본 경로다.

## Production profile exact semantics

`PRODUCTION_MATCHUP_COMPOSITION_V1`은 closed registry에 별도 production identity로 등록했다. Gameplay configuration은 historical comparison profile인 `FULL_SYSTEM_CANDIDATE_V1`의 현재 snapshot과 exact하다.

| Gameplay field | 값 |
| --- | --- |
| Lane Combat | ON |
| FARM Recovery | ON |
| Jungle Gank | ON |
| Counter Gank | ON |
| Roam | ON |
| Objective Priority | ON |
| Lane Phase | ON |
| Mid-game Macro | ON |
| Objective Decision | ON |
| Late-game Macro | ON |
| Progression | ON |
| Progression Power | ON |
| Champion Power | ON |
| Champion Matchup | `GEOMETRIC_V2` |
| Team Composition | `PRODUCTION_V2` |
| Jungle Clear contribution | `DISABLED_NOT_INTEGRATED` |
| Economy candidate activation | `false` |
| Tempo candidate activation | `false` |
| Active gameplay rules | `MATCH_SIMULATOR_PRE_JUNGLE_RULES_V3` |

Configuration hash는 `caaf76274dc148040b0a95eae1ed5181790b2fc840f45af9b109ea7951c1fd5d`다. 이 hash는 gameplay configuration만 나타내므로 exact clone인 Full candidate와 같은 것이 의도된 계약이다. Candidate와 production은 runtime profile ID, production policy, replay provenance로 구분한다. 기존 여섯 profile 중 나머지 다섯 profile의 semantics/hash는 변경하지 않았다.

## Policy, provenance, hash

Authoritative product authority는 계속 `MatchEngineV1Policy` 한 곳에 있다.

| 항목 | 값 |
| --- | --- |
| Contract | `MATCH_ENGINE_CONTRACT_V1` |
| Policy schema | `MATCH_ENGINE_V1_PRODUCTION_POLICY_V2` |
| Policy ID | `MATCH_ENGINE_V1_MATCHUP_COMPOSITION_PRODUCTION_POLICY` |
| Policy SHA-256 | `c700fdbbec5a6ed1b750578eeed49e17818eee9dfbda00a1d534c9bf42be19b5` |
| Runtime profile | `PRODUCTION_MATCHUP_COMPOSITION_V1` |
| Configuration SHA-256 | `caaf76274dc148040b0a95eae1ed5181790b2fc840f45af9b109ea7951c1fd5d` |
| Engine | `MATCH_SIMULATOR_ENGINE_IMPLEMENTATION_V9` |
| Rules | `MATCH_SIMULATOR_PRE_JUNGLE_RULES_V3` |

Policy canonical serialization은 profile/configuration, Matchup/Composition/Jungle mode, Economy/Tempo false, decision code, known limitation과 holdout false를 결속한다. Decision text는 configuration hash에 들어가지 않는다. Runtime에서 code-owned approved policy hash를 다시 계산하므로 의미가 drift하면 실행 전에 실패한다.

`SimulationOptions.productionDefaults()`는 현재 gameplay 값과 정렬되지만 authoritative application default가 아니다. Alignment와 authority를 분리했고 `lowLevelProductionDefaultsAuthoritativeApplicationDefault=false`를 유지했다. Historical Final 13G-B manifest/source hash는 audit-only 상수로 남겼으며 새 activation 승인 근거로 사용하지 않았다.

Replay/output integrity는 기존 champion resource, player identity/ratings/proficiency, Draft rule/meta/selection policy와 20-turn trace, final Draft/assignment, seed, engine/rules/resource provenance를 그대로 결속하면서 새 production profile와 policy를 반영한다. Diagnostics ON/OFF는 configuration, timeline, outcome, reward와 Random fingerprint를 바꾸지 않는다.

## Production wiring과 API

다음 암묵적 제품 경로는 모두 authoritative policy를 사용한다.

```text
POST /api/v1/real-matches/simulate
  → RealMatchApiV1Service
  → RealDraftMatchOrchestrator.orchestrateV1
  → MatchEngineV1
  → policy/input/provenance/output integrity validation
  → REAL_MATCH_RESPONSE_V1
```

Profile 인자가 없는 일반 `RealDraftMatchOrchestrator` overload도 hard-coded baseline 대신 policy에서 production profile을 얻는다. Explicit-profile overload는 진단/롤백 비교를 위해 closed registry profile을 계속 받는다. 공개 request에는 profile selector를 추가하지 않았고 `runtimeProfileId` 같은 extra field는 `UNSUPPORTED_REQUEST_FIELD`로 거부한다. Production 오류에서 baseline으로 조용히 fallback하는 경로도 없다.

API의 기존 필드는 제거·개명하지 않았다. Options/response의 production policy DTO에 activation decision schema/code, known limitation과 `statisticalHoldoutApproved=false`를 additive하게 노출했다. Frontend는 profile을 generic string으로 검증하고 LIVE 기본 공급자, strict runtime validation, reference 자동 fallback 금지를 유지한다.

## Gameplay reachability와 직접 영향 invariant

Deterministic focused fixture에서 다음을 확인했다.

- Production Matchup은 structured application provenance로 non-zero consumer까지 도달한다. Direct Matchup Random 소비는 0이다.
- Production Composition은 적용 가능한 deterministic context에서 structured causal binding과 non-zero contribution이 확인된다. Direct Composition Random 소비는 0이다.
- 같은 production 입력/seed의 diagnostics ON/OFF timeline과 Random fingerprint가 exact하다.
- Baseline에서는 Matchup/Composition이 exact OFF이고 기존 configuration/hash와 실행 parity를 유지한다.
- Production에서 Jungle Economy/Tempo execution/application 통계는 exact zero다.
- central priority/fallthrough, one-major-combat-per-tick, common kill/reward/death, duplicate protection, FARM restriction과 summary/KILL event 분류 invariant가 통과했다.

모든 real Draft가 Composition non-zero application을 만든다고 가정하지 않았으며, 특정 winner나 승률을 correctness assertion으로 고정하지 않았다.

## 알려진 민감도와 위험

직전 fresh V2 calibration 관측은 다음과 같다.

| 비교 | Blue WR | Winner change | Structure progression change | Nexus/ending change | 평균 경기 시간 |
| --- | ---: | ---: | ---: | ---: | ---: |
| Matchup − Baseline | +1.5%p | 4.5% | 10.25% | 5.5% | +1.1초 |
| Full − Matchup | -1.0%p | 8.5% | 14.75% | 9.25% | +6.3초 |

Composition의 Nexus/ending 9.25%는 직전 제안 tolerance 7.5%보다 높았다. 이것은 production에서 우선 관찰해야 할 known risk다. `objective detailed signature` 변화율은 실제 오브젝트 승패만이 아니라 내부 decision detail 차이도 포함하므로 production 효과율로 해석하지 않는다.

Matchup public divergence 400건 중 exact direct binding은 1건이고 399건은 diagnostic lineage가 unresolved였다. 이는 gameplay corruption의 증거도, causal proof 완료도 아니다. Composition은 기존 V6/V2 causality 계약에서 direct binding이 확인됐다.

## BASELINE rollback

`BASELINE_V1`의 exact configuration/hash `c8cc557bd721228c473e30d31b7258510f9608a18098578bc1da36e603536215`를 그대로 보존했다. Explicit baseline 실행은 활성화 전 baseline path와 complete gameplay timeline, Random/result parity를 유지하며 production 실행과 profile/policy/provenance가 명확히 다르다.

롤백은 향후 `MatchEngineV1Policy`의 명시적 versioned 변경으로만 수행해야 한다. Runtime exception이나 integrity mismatch가 baseline을 자동 선택하게 만들지 않았다. V8 handoff, V9 baseline, calibration/holdout/recommendation artifact는 재작성하지 않았다.

## 검증 결과

Focused verification:

- Activation/profile/policy/API/provenance: 10 suites / 58 tests / failures 0 / errors 0 / skipped 0, Gradle wall 2분 56초. 요청 목록 중 diagnostic-tagged `PreJungleTempoParityAuditTest`는 default test exclude 정책에 따라 실행되지 않았다.
- 직접 영향 gameplay invariant: 9 suites / 101 tests / failures 0 / errors 0 / skipped 0, Gradle wall 7초.
- 첫 full에서 발견한 historical/transport expectation 보정 후 affected: 4 suites / 7 tests / failures 0 / errors 0 / skipped 0, Gradle wall 1분 11초.

첫 complete backend regression은 2,201 tests 중 4건이 실패했다. 원인은 production runtime 결함이 아니라 historical Final 13G inspector와 현재 transport/performance test가 암묵적 default를 영구 baseline으로 가정한 것이었다. Historical 5-profile audit/oracle은 새 production alias를 제외하도록 명시하고, current transport/performance 계약은 새 production identity를 확인하도록 수정했다. 과거 artifact/output hash는 덮어쓰지 않았다.

두 번째이자 최종 complete backend regression:

- 219 suites / 2,201 tests
- failures 0 / errors 0 / skipped 0
- aggregate JUnit XML 1,821.280초
- Gradle wall 16분 25초
- `BUILD SUCCESSFUL`

Frontend `npm run build`는 87 modules, 약 8초에 성공했다. Generated `frontend/dist/index.html`은 source 변경이 아니므로 빌드 뒤 기존 tracked 상태로 복원했다.

## LIVE API와 브라우저 smoke

현재 tree를 backend `localhost:8085`, frontend `localhost:5173`에서 실행했다. 기존 사용자 프로세스가 점유한 8080은 종료하지 않았다.

- Options HTTP 200, LCK 10팀과 새 policy/profile/configuration/engine/rules/decision identity 확인
- Simulate HTTP 200, 실제 gzip `Content-Encoding`, `Vary: Accept-Encoding` 확인
- GEN(BLUE) 대 T1(RED), seed `73`: Draft 20 decisions, final assignment 10명
- T1(RED) 승리, `NEXUS_DESTROYED`, 2,320초, event 376, snapshot 233
- output SHA-256 `b74cd4a509134fc5a1b8cb9aa458e6a86233f407ce59084856aa370a80a33481`
- Random draw count 4,866
- 브라우저에서 Match Setup → Draft Results → Match Playback → Result 흐름, x30 speed, 19:20 seek, 구조물/넥서스 포탑 표시와 LIVE integrity profile을 확인
- clean `localhost` session에서 reference fallback과 page/runtime validation error는 없었다.

이 한 경기는 호환성 smoke일 뿐 balance나 statistical acceptance 표본이 아니다.

## Artifact와 source integrity

별도 activation evidence bundle은 생성하지 않았다. 대형 calibration/holdout/B2/B3/13G-B/population task도 실행하지 않았다. 현재 reviewed HEAD는 `eee063775fd90036e6f0349f0553329ba3222d66`이며 production source/test/frontend/docs만 수정했다. 사용자 소유 untracked `prompts/`, local baseline JSON과 historical artifact는 보존했고 git add/commit/push를 수행하지 않았다.

## 다음 단계

1. LIVE에서 side별 winner, structure/Nexus progression, 경기 시간과 integrity/validation 오류를 structured field로 관찰한다.
2. Composition Nexus/ending 민감도가 제품 허용 범위 안인지 별도 production acceptance 기준으로 검토한다.
3. Matchup lane-pressure mutation이 downstream action eligibility/score/selection에서 소비될 때 exact consumer action ID까지 이어지는 lineage를 완성한다.
4. 다음 official requalification은 새 versioned contract와 비중첩 fresh seed를 사용한다. 이번에 시작하지 않은 holdout이나 이미 소비된 calibration seed를 재사용하지 않는다.
5. 롤백이 필요하면 자동 fallback이 아니라 `MatchEngineV1Policy`의 명시적 versioned policy 변경으로 `BASELINE_V1`을 선택한다.
