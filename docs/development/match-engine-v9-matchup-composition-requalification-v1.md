# Match Engine V9 Matchup/Composition 재검증 V1

이 문서는 `MATCH_ENGINE_V9_BASELINE_AND_MATCHUP_COMPOSITION_REQUALIFICATION`의 fresh evidence를 요약한다. 실제 production policy, HTTP 기본 profile, API schema, frontend runtime은 변경하지 않았다.

## 실행 identity와 contract

- Git HEAD: `c2814b63b6fa40487f893c510fbe5868e508724a`
- Engine: `MATCH_SIMULATOR_ENGINE_IMPLEMENTATION_V9`
- Active rules: `MATCH_SIMULATOR_PRE_JUNGLE_RULES_V3`
- Production profile: `BASELINE_V1`
- Production configuration: `c8cc557bd721228c473e30d31b7258510f9608a18098578bc1da36e603536215`
- Resource provenance: `64ab1be3fdfe8d6660648ac634b52a86a5693d264bfbe707153dac9c17d39b4f`
- Contract: `3b1a63d1a735e46884c5c3e51927cadb22be628d7935b2e963e16be5e3ffc7af`
- Schedule: `7636add6500c06a2d641cc6f51092e8f3dbdab35311195bd72d8da7bbe050607`

V2 schedule은 failed preflight에서 발견한 calibration-seed smoke 오염 가능성을 제거하기 위해 별도 `DRY_RUN` namespace를 분리했다. 이후 공식 calibration과 holdout은 새 seed namespace에서 실행했고, 기존 Final 13G-B B2/B3 seed와 overlap은 0이다. Acceptance gate 수치는 변경하지 않았다.

90개 G1과 10개 Hard Fearless G2 fixture에서 production Draft와 final role assignment를 fixture당 한 번 고정했다. Calibration은 `100 × 8 × 3 = 2,400`, frozen holdout은 `100 × 4 × 3 = 1,200`, 합계 3,600 official rows다.

## 결과

`BASELINE_V1` holdout 400경기는 Blue 승률 52.75%, 평균 1,952.3초, P95 2,600초, 평균 first tower 765.65초였다. timeout, structured/domain integrity, invalid structure HP/state, Nexus-turret ordering, post-finish mutation/event, direct Random, SUPPORT FARM CS 오류는 모두 0이다.

| Holdout paired marginal | Blue WR Δ | Winner changed | Objective changed | Structure changed | Mean duration Δ | P95 duration Δ | 판정 |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | --- |
| Matchup − Baseline | +2.00pp | 4.00% | 7.50% | 31.50% | +1.025s | +10s | FAIL |
| Full − Matchup | -1.75pp | 6.25% | 11.50% | 14.00% | -1.625s | -80s | FAIL |

Matchup `GEOMETRIC_V2`는 holdout에서 non-zero application 128,257회와 direct Random 0회를 보였지만 Blue WR 1.5pp bound와 structure changed 12% bound를 넘어서 `MATCHUP_V9_NOT_ELIGIBLE`이다.

Composition `PRODUCTION_V2`는 initialized/attempt path에는 도달했지만 승인된 gameplay application과 non-zero modifier가 0회였다. 그럼에도 Matchup-only 대비 public divergence 이전에 대응하는 recorded local change가 없는 사례가 60회였고, objective/structure/Blue WR incremental bound도 넘었다. 따라서 V9 persistent siege/base-defense와 결속된 적용 의미를 증명하지 못해 `COMPOSITION_V9_NOT_ELIGIBLE`이다. Production 활성화 전에 application accounting과 local-cause provenance를 먼저 진단해야 한다.

V9 structure 관측에서 holdout baseline은 structure damage 12,221회, destruction 5,429회, persistent siege start 6,408회, stop 7,962회였다. 세 profile 모두 invalid structure state는 0이었지만, profile별 최종 structure signature가 높은 비율로 바뀌어 Matchup macro safety gate가 실패했다.

## 판정과 검증

최종 machine-readable recommendation은 `RECOMMEND_BASELINE_V1`이다. 이는 evidence 권고일 뿐 production 적용이 아니다. `MatchEngineV1Policy`, 공개 Real Match V1 wiring, production Java/resource와 frontend는 변경하지 않았다.

- Focused contract: 3 tests, failures/errors/skipped 0
- G1/G2 three-profile smoke: replay/instrumentation/fixed-Draft/integrity clean
- Complete backend regression: 206 suites / 2,135 tests / failures 0 / errors 0 / skipped 0, Gradle wall 15분 11초
- Calibration: 100 authenticated fixture checkpoints / 2,400 rows
- Frozen holdout: 100 authenticated fixture checkpoints / 1,200 rows
- Fresh JVM A/B: 19/19 artifact files byte-identical
- Official SHA manifest raw hash: `0cdcfa002882c57eaa13b5f5cee160eccc0d2ae49aa773267b81ef37ac2a6b5f`

공식 산출물은 `backend/build/reports/match-engine-v9-matchup-composition-requalification-v1/`에 있다.

## 다음 단계

1. Matchup이 V9 structure signature를 31.5% 바꾸는 원인을 structure source/lane/HP 단계별 paired trace로 분해한다.
2. Composition의 실제 application 0회와 local-cause 위반 60회를 application-point accounting과 winner-decision provenance 기준으로 진단한다.
3. 위 원인을 수정하더라도 이번 holdout seed를 재사용하지 않고 새 versioned contract/calibration/holdout을 만든다.

