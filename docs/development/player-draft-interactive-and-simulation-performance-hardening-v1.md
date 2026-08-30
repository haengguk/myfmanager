# Player Draft Interactive and Simulation Performance Hardening V1

## 상태

`PLAYER_DRAFT_PERFORMANCE_PARTIALLY_HARDENED`

이번 milestone은 Player-controlled Draft의 ACTIVE 응답 projection과 completed Draft 검증에서 반복되던 계산을 제거했다. Production V9 공식, Draft search 폭·후보·scoring·정렬, Random 소비, public API schema는 바꾸지 않았다. Backend와 실제 Chromium의 주요 사용자 경로는 목표를 통과했지만, projection을 제외한 `PlayerDraftApiV1Service.action` 단독 중앙값 개선율이 13.609%로 25% 목표에 미달했으므로 전체 성공으로 승격하지 않는다.

## 사용자 경로 Before/After

```text
이전 action:
player 선택 → legal pool 재계산 → AI → 다음 legal pool 재계산 → 응답

이후 action:
revision-bound authoritative projection 재사용
→ 필요한 새 state 계산만 수행
→ 동일한 응답을 더 빠르게 반환

이전 simulate:
completed Draft 20턴 전체 search/view 재생
→ Production V9

이후 simulate:
server-owned immutable completion binding 경량 검증
→ 동일한 Production V9 input/output
```

검증을 삭제하거나 외부 입력을 신뢰한 것이 아니다. ACTIVE action은 현재 revision에 결속되어 직전 응답에 사용된 authoritative `SelectionView`만 재사용하고, transition 뒤에는 새 revision projection을 한 번 계산한다. Completed Draft는 정상 서버 transition이 만든 opaque binding과 동일한 결과 객체, session/Series identity, team·side·seed·game·history, active rule/meta/resource, control/evidence/final assignment hash와 Production V9 policy를 경량 검증한다. 하나라도 다르거나 binding이 없으면 fast path로 승격하지 않고 기존 untrusted full-validation 경계 또는 명시적 거부를 따른다.

## 소유권과 수명

- `PlayerDraftSession`은 현재 revision의 immutable projection, completed binding과 match-scoped `DraftComputationContext`를 소유한다.
- exact action replay receipt는 해당 action이 만든 결과 projection을 함께 소유하므로 executor나 AI를 다시 호출하지 않는다.
- `SeriesAggregate`의 child Draft도 같은 completion/projection binding과 game number, history, side, seed context를 소유한다.
- completed, simulated, cancelled, expired 상태에서는 ACTIVE projection과 computation context를 비우며 cancel/expire는 completion binding도 폐기한다.
- static/global/`ThreadLocal` 또는 display-name keyed cache는 추가하지 않았다. Session repository의 기존 최대 128개 경계 안에서만 살아 있으므로 cross-session 공유가 없다.

`SelectionView` 전체를 revision마다 누적 저장하지 않고 현재 revision과 action receipt에 필요한 immutable projection만 둔다. 이는 legal/unavailable/recommendation 재계산을 피하면서도 모든 과거 state의 큰 object graph를 무제한 보관하지 않는 절충이다. `DraftComputationContext`도 한 session/game 안에서만 재사용하고 종료 시 clear한다.

## Paired backend 결과

동일한 GEN–T1, seed 73, BLUE/RED controlled fixture와 10-action script를 before/after에 사용했다. Script는 `garen, galio, gangplank, gragas, graves, nami, gwen, gnar, nilah, diana`, SHA-256은 `3546272f10c63feb56ab19d1c64bd794f495d800facaa572a4c3a5958322954f`다.

| 측정 경계 | Before median | After median | 감소율 | 목표 | 결과 |
| --- | ---: | ---: | ---: | ---: | --- |
| completed Draft/input validation | 3,422.554ms | 1.564ms | 99.954% | 70% | 통과 |
| first simulate service | 4,172.466ms | 592.023ms | 85.811% | 45% | 통과 |
| exact retry service | 4,123.774ms | 583.799ms | 85.843% | 35% | 통과 |
| ACTIVE response projection | 136.714ms | 0.289ms | 99.789% | 40% | 통과 |
| `actionService` 단독 | 275.045ms | 237.614ms | 13.609% | 25% | 미달 |

Action route 전체는 service와 projection을 합친 경계에서 약 39.9% 감소했다. 마지막 Draft action은 binding 생성 때문에 baseline 대비 10% 이상 악화되지 않았다. Exact retry는 full Match output cache가 아니며, 같은 validation fast path 뒤 Production V9을 fresh 실행하고 compact receipt equality를 다시 확인한다.

## Actual Chromium 결과

Before와 after 모두 controlled side마다 fresh backend, 동일 frontend, Chromium cache disabled 조건으로 ACTION 10개와 SIMULATION 1개를 실행했다. p90은 20개 action의 작은 표본 nearest-rank 기술 통계이며 모집단 보장이 아니다.

| 측정 경계 | Before | After | 감소율 | 결과 |
| --- | ---: | ---: | ---: | --- |
| action confirm → stable DOM median | 588.2ms | 369.15ms | 37.241% | 20% 목표 통과 |
| action p90 | 1,277.8ms | 818.5ms | 35.945% | 비악화 및 권고 목표 통과 |
| BLUE simulate → Playback DOM | 5,529.1ms | 1,345.2ms | 75.670% | 30% 목표 통과 |
| RED simulate → Playback DOM | 7,147.9ms | 2,801.6ms | 60.805% | 30% 목표 통과 |

두 phase 모두 HTTP 200과 gzip을 유지했고 console/page/runtime validation/reference fallback error는 0이다. JSON parse/strict validation 중앙값은 0.5ms → 0.5ms, React state-to-DOM 중앙값은 43.65ms → 41.6ms로 regression 근거가 없다.

## Exact 기능 및 안전 경계

Before/after는 두 controlled side에서 같은 10개 PLAYER action, ordered 20 decisions, PLAYER/AI authority, champion과 AI alternatives/trace, final assignment, Draft/control/input/output identity, Production V9 profile, structured timeline, Random draw count/fingerprint, winner와 result를 exact 비교한다. Public response schema, gzip, snapshots와 events는 유지한다.

Focused test는 trusted standalone/Series fast path, binding 없는 untrusted 경계, team/seed/game/history/rule/meta/resource/evidence/AI trace/final assignment 변조, stale generation, 이전 revision projection, 두 session 격리, cancel/expire 폐기, exact action replay의 executor/AI 무호출, exact simulate retry, diagnostics ON/OFF parity를 검증한다. 변조는 Match Engine 및 gameplay Random 실행 전에 거부한다.

## Phase C와 남은 병목

Phase A/B 재측정 뒤 `actionService` 단독 목표가 남아 Phase C를 적용했다. 한 session의 player transition과 이어지는 AI 0/1/2턴은 동일한 bounded `DraftComputationContext`를 공유한다. Search 폭, candidate 수, scoring과 raw-double 연산 순서는 바꾸지 않았다. Context hit/miss/entry와 peak entry 근사는 diagnostic에 남기고 session 종료 시 clear한다.

실제 route와 DOM은 projection 재사용으로 충분히 빨라졌지만, projection을 뺀 순수 action 경로는 AI follow-up의 planner/availability/role feasibility 계산이 계속 지배한다. 다음 최적화는 별도 `PLAYER_DRAFT_AI_TURN_PERFORMANCE_HARDENING_V1`에서 exact 선택·Random parity를 고정한 채 구조적 계산 재사용만 더 좁게 다뤄야 한다.

## Artifact와 재현 경계

공식 결과는 `backend/build/reports/player-draft-performance-hardening-v1/`에 생성한다.

- `performance-contract.json`: source/fixture/script/collector/raw input identity와 acceptance 정의
- `backend-before-after.csv`: paired direct backend rows
- `browser-before-after.csv`: BLUE/RED의 ACTION 10개 + SIMULATION 1개
- `summary.json`: 중앙값, 작은 표본 p90, acceptance와 partial verdict
- `analysis.md`: 결과와 남은 병목
- `SHA256SUMS.txt`: 위 5개 파일의 raw SHA-256

Historical profiling artifact는 읽기 검증만 했고 덮어쓰지 않았다. 이 결과는 GEN–T1/73의 작은 deterministic paired schedule이며 balance, 대규모 population 또는 모든 배포 환경의 절대 latency 보장이 아니다.

## 검증 명령

```text
gradlew.bat test --tests com.lolfm.draft.PlayerControlledDraftEngineTest --tests com.lolfm.application.PlayerControlledDraftMatchInputBoundaryTest --tests com.lolfm.application.PlayerDraftProjectionReuseTest --tests com.lolfm.application.PlayerDraftSimulationHardeningTest --tests com.lolfm.application.PlayerDraftSessionRepositoryTest --tests com.lolfm.application.SeriesLifecycleHardeningTest --tests com.lolfm.application.SeriesProductionV9SmokeTest --console=plain
npm run player-draft:verify
npm run build
gradlew.bat test --no-daemon --console=plain
```

최종 focused 경계는 7 suites / 44 tests / failures 0 / errors 0 / skipped 0, Gradle wall 2분 16초로 통과했다. Node frontend contract는 33 scenarios, production build는 101 modules / Vite 1.54초로 통과했다. Actual Chromium BLUE/RED는 ACTION 10개 + SIMULATION 1개씩, HTTP 200/gzip과 console/page/runtime/reference fallback error 0을 확인했다.

동결된 final executable tree의 complete backend regression은 첫 실행에서 238 suites / 2,274 tests / failures 0 / errors 0 / skipped 2, aggregate XML 871.674초와 Gradle wall 14분 43초로 통과했다. Skipped 2개는 explicit 환경 변수가 없을 때 실행하지 않는 기존 latency profiling diagnostic과 이번 paired performance diagnostic이다. Full 뒤에는 production Java, resource, runtime wiring과 shared fixture를 변경하지 않았다.

대형 calibration, holdout와 distribution diagnostic은 실행하지 않는다.
