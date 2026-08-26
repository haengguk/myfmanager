# Auto Draft Variety V1

## 결과

`AUTO_DRAFT_VARIETY_V1`은 `ACCEPTED`다. 기존 Draft 평가식과 탐색 범위는 유지하면서, 매 턴 최고점과 2.0점 이내인 상위 3개 후보 안에서 match seed에 결속된 결정적 선택을 수행한다.

```text
이전: 같은 팀/series state라면 seed가 달라도 항상 같은 Draft
이후: 고품질 상위 후보 범위 안에서 seed에 따라 Draft가 달라질 수 있음
```

같은 팀, 같은 roster, 같은 series history, 같은 seed에서는 Draft 20턴, 최종 role assignment, Match Engine 입력과 전체 timeline이 정확히 재현된다. 다른 seed가 반드시 다른 결과를 만든다는 보장은 없으며, 품질 제한을 통과한 후보가 하나뿐이면 기존처럼 1위를 선택한다.

## 원인과 변경 경계

기존 production 경로는 `RealDraftMatchOrchestrator`가 `DraftEngine`에 match seed를 전달하지 않았고, `ShallowDraftSearch`가 평가·정렬한 후보의 첫 항목을 즉시 선택했다. 따라서 seed는 Draft가 끝난 뒤 Match Simulator에서만 사용됐다.

이번 변경은 후보 평가와 선택을 분리했다.

```text
PreDraftPlanner / candidate generation
  → 기존 evaluator와 shallow continuation으로 모든 root 후보를 한 번씩 평가
  → score 내림차순, ChampionId 오름차순으로 canonical rank 부여
  → 최고점 대비 2.0점 이내인 상위 3개까지만 selection pool 구성
  → canonical DraftSelectionContext + policy hash를 SHA-256으로 결속
  → 정수 weight bucket으로 한 후보 선택
  → 기존 DraftState 적용 및 다음 턴 replan
```

후보 수, shortlist, continuation depth/beam, candidate generation, evaluator 공식, component weight와 Draft Meta는 바꾸지 않았다. 감사용 exact-best 경로는 `draftDeterministicBest(...)`로 명시적으로 남겼고 production seeded 경로와 혼동되는 기존 3-argument `draft(...)` overload는 두지 않았다.

## 동결 정책

| 항목 | 값 |
| --- | --- |
| Policy ID | `AUTO_DRAFT_VARIETY_V1` |
| Mode | `SEEDED_BOUNDED_RANK_WEIGHTED_V1` |
| 최대 pool | 3 |
| 최고점 대비 허용 손실 | 2.0 (`2_000_000` fixed-point) |
| BAN rank weight | 55 / 30 / 15 |
| PICK rank weight | 70 / 22 / 8 |
| score 변환 | `DECIMAL_DOUBLE_TO_FIXED_1E6_HALF_UP_V1` |
| context hash | `SHA256_UTF8_EXPLICIT_ORDERED_DRAFT_SELECTION_CONTEXT_LINES_TRAILING_NEWLINE_V1` |
| draw | `SHA256_FULL_UNSIGNED_BIG_INTEGER_MOD_TOTAL_WEIGHT_V1` |
| Policy SHA-256 | `b4645a9897329b6b0d50405a22ef788885a40ecede4b0fedd04e168211cf75cc` |

Pool이 1개면 draw bucket을 만들지 않고 `ONLY_ONE_WITHIN_WINDOW`를 기록한다. Pool이 2~3개면 정수 weight 합계 안의 bucket을 선택하고 `SEEDED_WEIGHTED_SELECTION`을 기록한다. 비유한값 score와 중복 ChampionId는 fail-fast한다.

## 선택 identity와 Random 격리

`DraftSelectionContext`는 다음 structured identity를 명시적 순서로 canonicalize한다.

- match seed
- BLUE/RED stable team code와 stable `PlayerId` roster identity hash
- series game number와 Draft 이전 history hash
- turn, side, action type
- 현재 ordered bans/picks와 정렬된 exclusion
- selection policy hash
- eligible canonical pool의 ChampionId와 rank

Team/player display name, event 문구, frontend text, unordered collection 순서, wall clock과 runtime profile alias는 선택 identity가 아니다. Hard Fearless Game 2+는 game number와 이전 completed picks에 결속된다.

선택기는 stateless이며 `Random`, `Math.random()`, `ThreadLocalRandom` 또는 match 밖 mutable state를 사용하지 않는다. SHA-256 결과를 unsigned 정수로 해석해 bucket을 고르므로 Draft 선택은 Match Simulator가 소유한 seeded `Random`을 생성하거나 소비하지 않는다. 따라서 같은 final Draft와 seed로 실행한 reference simulator timeline과 Random fingerprint는 exact다.

## 기존 선수·조합 평가의 작동

다양성은 기존 평가를 대체하지 않고 평가가 허용한 고품질 후보 안에서만 작동한다.

1. `PreDraftPlanner`가 현재 roster와 legal role을 바탕으로 포트폴리오를 만든다.
2. Pick/Ban evaluator가 Draft Meta, 우리 선수의 champion proficiency, 상대 proficiency/threat, matchup, composition fit/response, flex, denial, future feasibility와 role feasibility를 합산한다.
3. Shallow search가 이후 턴의 continuation value를 반영해 모든 root 후보를 평가한다.
4. 그 최종 점수의 최고점 대비 2.0 이내 후보만 seeded selection 대상으로 들어간다.
5. 선택 뒤 기존 planner가 다음 턴 상태에서 다시 계산한다.

따라서 proficiency와 composition은 계속 후보의 품질·순위·pool 진입을 결정하지만, Match Simulator runtime의 Matchup/Composition mode를 활성화하지 않는다. `BASELINE_V1`의 runtime configuration hash와 gameplay Random 순서는 그대로다.

여기서 선수별 champion proficiency는 Draft 입력이지만 general player ratings 12종은 아직 Draft scoring 입력이 아니다. Ratings는 final assignment 이후 Match Engine의 player ability realization에 사용된다. Draft 단계의 Matchup/Composition 평가와 match runtime contribution 활성화는 서로 다른 정책 경계다.

## Trace, API와 provenance

20개 Draft decision마다 `DraftSelectionTrace`가 생성된다. Trace는 policy ID/mode/hash, context hash, turn/side/action, pool과 rank/score/weight, bucket, 선택 ChampionId, 선택 rank/score loss와 reason을 structured field로 보존한다.

- `FinalDraftResult.draftIdentity()`는 series commit용 ordered selected decision identity를 그대로 유지한다.
- selection trace 전체는 별도 `draftSelectionTraceHash`로 고정한다.
- Match Engine input, V3 execution provenance, replay provenance와 output policy는 selection policy ID/hash/trace hash를 결속한다.
- Real Match API의 `draft`는 policy ID/hash, trace hash와 20개 trace를 additive하게 노출한다.
- `integrity`와 options policy snapshot도 selection policy identity를 노출한다.
- LIVE frontend validator는 field cardinality, decision/trace 일치와 integrity 일치를 엄격히 검사한다.

기존 Draft scoring policy SHA-256은 `4bc9f8b1db17ff2803fce80b2616e2fd0afffa278749a80b91231e342caeec18`, runtime configuration SHA-256은 `c8cc557bd721228c473e30d31b7258510f9608a18098578bc1da36e603536215`로 유지됐다. Selection policy를 포함한 현재 Match Engine V1 production policy hash는 `b2975b2f3ced0b1864e7730abc7794dcbf4bafe7a031ef098811f62daa796d94`다.

## 고정 진단

전용 diagnostic은 LCK 10개 순환 fixture × signed-long 경계와 대표값 8 seeds로 production Draft 80개를 실행했다. 별도 same-seed 검사는 fixture마다 1개씩 10개이며 Match Simulator 실행은 0이다.

| Fixture | complete Draft identities | final pick tuples | BLUE bans | RED bans | BLUE picks | RED picks |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| BFX BLUE / BRO RED | 8 | 8 | 14 | 15 | 16 | 12 |
| BRO BLUE / DK RED | 8 | 8 | 13 | 15 | 14 | 14 |
| DK BLUE / DNS RED | 8 | 8 | 15 | 15 | 11 | 16 |
| DNS BLUE / GEN RED | 8 | 8 | 11 | 13 | 12 | 14 |
| GEN BLUE / HLE RED | 8 | 7 | 11 | 11 | 13 | 12 |
| HLE BLUE / KRX RED | 8 | 8 | 12 | 17 | 13 | 14 |
| KRX BLUE / KT RED | 8 | 8 | 14 | 14 | 13 | 13 |
| KT BLUE / NS RED | 8 | 8 | 16 | 16 | 15 | 14 |
| NS BLUE / T1 RED | 8 | 8 | 17 | 17 | 20 | 16 |
| T1 BLUE / BFX RED | 8 | 7 | 11 | 15 | 13 | 16 |

모든 fixture가 complete Draft identity 2개 이상, final pick tuple 2개 이상 gate를 통과했다. 전체 1,600턴의 선택 rank는 1위 1,126회, 2위 358회, 3위 116회였고 pool cardinality는 1개 355회, 2개 349회, 3개 896회였다. BAN score loss의 median/P95/max는 `0 / 1.544369 / 1.983034`, PICK은 `0 / 1.416323 / 1.988794`였다.

illegal Draft, 중복 ban/pick, Hard Fearless 위반, final assignment 오류, same-seed replay 불일치, cross-JVM 불일치, context/trace/policy hash 불일치, rank 3 초과와 score-loss window 초과는 모두 0건이다. 이 분포는 고정 정책의 reachability·integrity 진단이며 밸런스나 승률 oracle이 아니다.

Artifact는 `backend/build/reports/auto-draft-variety-v1/`에 생성된다. Manifest raw SHA-256은 `772d1b5c55cb254cb3eb06149098e56730daca302fe0c04a88d13ed46afccd51`이고 payload 4개가 `SHA256SUMS.txt`와 exact다.

## 검증과 제한

Focused 검증은 selector 경계/중복/비유한값, same-seed, 다른 seed variety, Hard Fearless, production/reference simulator parity, Draft scoring/search regression, Real Draft application, Match Engine V1/API/controller, candidate profile과 Random observation을 포함했다. Fresh-JVM A/B probe는 각각 33,808 bytes와 SHA-256 `0291df0f03da4ff0067a620f0d4b8e1dc317577defd2a47166cfa9b579e097c2`로 byte-exact였다.

최종 backend full regression은 214 suites / 2,182 tests / failures 0 / errors 0 / skipped 0, aggregate JUnit XML 815.104초, Gradle wall 13분 50초로 통과했다. 첫 full에서 현재 LIVE fixed-output 기대값 2개가 additive identity 변경 전 hash를 요구해 실패했고, gameplay 결과가 동일함을 확인한 뒤 두 test expectation만 현재 hash로 갱신했다. 영향 focused test와 두 번째 final full이 clean pass했다. Frontend production build도 통과했다.

현재 제한은 다음과 같다.

- 품질 window 안 후보가 하나면 seed가 달라도 같은 선택이다.
- 다른 seed도 같은 bucket에 들어가면 같은 Draft가 나올 수 있다.
- Real Match API는 아직 fresh Game 1 단판이며 지속 BO3/BO5 lifecycle은 제공하지 않는다.
- coach/team style/rating 같은 새 Draft 요인은 추가하지 않았다.
- Matchup/Composition simulator runtime activation이나 production tuning은 이번 범위가 아니다.
- Fresh Matchup/Composition requalification은 별도 후속 milestone과 비중첩 seed contract가 필요하다.
- 이 80-Draft diagnostic은 consumed holdout이나 balance qualification으로 재해석하지 않는다.

Historical V8 frontend reference와 기존 V8/V9 diagnostic artifact는 재생성하거나 덮어쓰지 않았다. 현재 LIVE identity만 additive Draft selection policy/trace를 반영한다.
