# Series Lifecycle V1 Backend Hardening

상태: `SERIES_LIFECYCLE_V1_FRONTEND_READY`

이 문서의 수치와 source integrity는 backend hardening milestone 당시 기록이다. 후속 [Frontend Readiness Hardening](series-lifecycle-v1-frontend-readiness-hardening.md)이 failed replay의 public current-state 의미와 capacity-aware `allowedCommands`를 완결했다.

## 목적과 최초 implementation과의 구분

최초 backend implementation은 authoritative BO3/BO5 aggregate, parent-bound Draft, Production V9 reservation/commit, compact receipt/replay와 additive Series API를 만들었다. 이번 milestone은 기능 폭을 넓히지 않고 production frontend가 재전송과 경합 상황에서도 그 lifecycle을 신뢰할 수 있도록 원자성, exact replay, failure receipt, capacity, late result, replay consistency와 BO5 경계를 완결했다.

Frontend, gameplay/Draft tuning, champion/player resource, persistence, auth와 distributed coordination은 변경하지 않았다.

## 실제 수정한 결함

### TTL cleanup

이전 cleanup은 오래된 snapshot을 EXPIRED로 판단한 뒤 conditional replace 실패를 무시하고 현재 snapshot까지 제거할 수 있었다. 이제 expiry 판정과 제거는 해당 key의 current snapshot을 대상으로 한 `computeIfPresent` 한 번 안에서 수행한다. Create index도 Series가 실제 terminal 상태로 제거될 때만 같은 경계에서 제거한다. Cleanup은 서로 다른 Series mutation을 global serialization하지 않고, 유효한 simulation lease는 parent cleanup에서 보호된다.

Latch와 mutable clock을 사용한 결정론적 테스트는 TTL 직전 mutation을 멈추고, clock을 기존 expiry까지 옮기고, concurrent create cleanup을 대기시킨 뒤 새 revision/activity/expiry를 먼저 commit한다. 최종 Series와 원래 create index가 모두 보존되며 `Thread.sleep`을 사용하지 않는다.

### Exact command replay

Child application result가 exact `SeriesGame`과 `SeriesChildDraft`를 함께 반환한다. Facade는 더 이상 `aggregate.currentGame()`으로 child binding을 재구성하지 않는다. Command receipt는 game ID/number, child ID/generation/revision과 필요한 immutable child snapshot을 보관한다. 따라서 Game 1 command를 Game 2에서 재전송하거나 같은 game에서 child를 재생성해도 현재 game/generation을 과거 결과처럼 반환하지 않는다. Snapshot/identity가 representable하지 않으면 `SERIES_COMMAND_REPLAY_IDENTITY_UNAVAILABLE`로 fail-closed한다.

### 성공·실패·진행 중 receipt

Receipt completion은 `IN_PROGRESS`, `SUCCEEDED`, `FAILED`다. Simulation reservation과 `IN_PROGRESS` receipt는 한 원자 mutation에서 생성되고, commit/runtime failure/integrity failure/no-result/lease expiry/cancel invalidation은 그 같은 receipt slot을 terminal 상태로 교체한다. Hard Fearless pool exhaustion도 blocked state와 failed receipt를 함께 기록한다.

같은 command ID와 canonical payload는 최초 성공/실패/진행 중 의미를 재현하고 엔진 실행과 mutation을 추가하지 않는다. 다른 type/payload reuse는 capacity/status보다 먼저 `SERIES_COMMAND_ID_PAYLOAD_CONFLICT`다. 실제 retryable failure 재시도는 새 command ID와 최신 revision을 사용한다.

후속 frontend-readiness 계약에서 receipt가 보존하는 historical resulting revision/status와 public error의 `currentRevision/currentStatus`를 분리했다. 따라서 최초 code/HTTP/retryable은 그대로 재현하면서 public current fields는 replay 시점 aggregate를 나타낸다.

### Receipt capacity

V1 한도 256과 no-eviction 결정을 유지한다. 새 receipt가 필요한 모든 state-changing command는 Draft/Match 실행과 mutation 전에 capacity를 검사한다. 255개 상태의 simulation은 256번째 `IN_PROGRESS` receipt를 만들 수 있고 terminal 결과는 같은 slot을 갱신한다. 256개 상태에서도 exact replay는 허용하지만 신규 Draft/action/simulate/cancel은 mutation 0, executor 0으로 `SERIES_COMMAND_RECEIPT_CAPACITY_REACHED`를 반환한다.

No-eviction은 단순하고 결정론적이지만 terminal/cancel UX도 한도에서 fail-closed할 수 있다. 장기 보존과 compaction은 persistence를 도입하는 V2 과제다.

후속 frontend-readiness projection은 같은 lifecycle configuration의 capacity 판정을 사용한다. 256개에서는 `allowedCommands`가 신규 mutation을 더 이상 광고하지 않고 `GET`만 남기며, 특정 기존 command ID의 exact replay는 계속 허용한다.

### Reservation, lease, cancel과 late output

Commit은 frozen reservation과 현재 Series/game 전체 snapshot, revision/status, game/child/generation, command/payload/token/lease, side/seed/history, completed Draft/revision/input binding을 exact 비교한다. Production executor는 policy/profile/configuration/rules/engine, resource/replay/final Draft/final assignment/output/Random identity를 계속 검증한다.

- 같은 simulation command 동시 요청: worker 1, reservation 1, duplicate 202
- 다른 command: 첫 reservation만 실행, 나머지 structured conflict
- cancel 중 late success와 late runtime failure: 둘 다 `SERIES_SIMULATION_RESERVATION_INVALIDATED`, score/history commit 0
- 정확히 5분 lease 경계: old receipt를 retryable `SERIES_SIMULATION_LEASE_EXPIRED`로 한 번 전환
- 새 command/latest revision retry: 새 commit 1, old late result 영향 0

### Replay consistency

Full replay preflight는 stored policy ID/hash, runtime profile, configuration, active rules와 engine identity를 current production과 비교한다. Fresh output receipt는 stored receipt와 exact 비교한다. 실행 전후 authoritative `SeriesAggregate.equals`를 사용하므로 status, terminal reason, games/current child/generation/reservation/receipts, score/history/winner/revision/activity/expiry를 포함한 revision-neutral transition도 검출한다. Identity drift는 `SERIES_GAME_REPLAY_IDENTITY_UNAVAILABLE`, deterministic output mismatch는 `SERIES_GAME_RECEIPT_MISMATCH`, 동시 lifecycle 변화는 `SERIES_GAME_REPLAY_MUTATION_DETECTED`로 fail-closed한다.

## BO3/BO5와 Hard Fearless

빠른 controllable executor는 다음 네 경로를 고정했다.

| Format | Winner sequence | Committed games | Final history |
| --- | --- | ---: | ---: |
| BO3 | GEN, GEN | 2 | 20 |
| BO3 | GEN, T1, GEN | 3 | 30 |
| BO5 | GEN, GEN, GEN | 3 | 30 |
| BO5 | GEN, T1, GEN, T1, GEN | 5 | 50 |

Required wins에서 즉시 종료하므로 Game 4/6은 생성되지 않는다. Side는 매 game 교대하고 managed GEN의 controlled side도 frozen mapping과 일치한다. Game seed는 root/context에서 결정적이며 game별로 구분된다. Full-distance BO5의 Game 5 history-before는 40, commit 뒤 exclusions는 50이다.

Series pool preflight는 BLUE/RED를 각각 확인해 같은 챔피언을 두 번 쓸 수 있다고 가정하지 않는다. Shared unavailable pool에서 양 팀 열 role slot에 서로 다른 챔피언을 매칭한다. 다섯 role champion만 있는 synthetic 경계는 independent check가 둘 다 PASS여도 joint check는 FAIL하고, role당 두 명인 열 champion 경계는 PASS한다. Active production resource의 실제 BO5는 Game 5 legal Draft까지 완료했다.

## Actual Production V9 bounded smoke

GEN 대 T1, root seed 73, managed GEN, Game 1 BLUE GEN에서 server 제공 첫 legal selection으로 실행했다.

| Format | Games | Final score | Exclusions |
| --- | ---: | --- | ---: |
| BO3 | 3 | GEN 2–1 T1 | 30 |
| BO5 | 5 | T1 3–2 GEN | 50 |

각 game은 Draft decisions 20과 final assignments 10을 만들었다. BO5 Game 5 시작 전 history는 40이었고 commit 뒤 50이었다. Side/controlled side/seed/history가 exact였으며 identity는 다음과 같다.

- policy: `MATCH_ENGINE_V1_MATCHUP_COMPOSITION_ACCEPTED_PRODUCTION_POLICY`
- profile: `PRODUCTION_MATCHUP_COMPOSITION_V1`
- engine: `MATCH_SIMULATOR_ENGINE_IMPLEMENTATION_V9`
- Matchup/Composition: ON
- Jungle Economy/Tempo: OFF

Committed game full replay는 stored receipt/output/timeline/Random과 exact하며 score/history/revision mutation은 0이다. Aggregate/command receipt에는 full match output 또는 timeline을 보관하지 않는다. 이 승자와 score는 balance evidence가 아니다.

## 검증

Focused repository/lifecycle/Draft joint-pool/strict Series API 묶음은 25 tests, failure/error/skip 0으로 통과했다. 기존 Player Draft/Match Engine V1/Real Match/Draft 호환성 8 suites / 58 tests도 failure/error/skip 0으로 통과했다. Actual Production V9 BO3/BO5 smoke는 2 tests, failure/error/skip 0, Gradle wall 3분 30초로 통과했다.

Final executable tree의 complete backend Gradle `test`는 첫 실행에서 통과했다.

- suites: 233
- tests: 2,261
- failures: 0
- errors: 0
- skipped: 0
- aggregate JUnit XML: 1,781.459초
- Gradle wall: 29분 50초

Clean full 뒤에는 lease 만료 뒤 old late-integrity 결과를 명시한 isolated assertion-only test를 강화해 affected focused test만 재확인했고, 그 밖에는 문서의 검증 수치와 최종 보고 문구만 갱신했다. Production 실행 코드가 바뀌지 않았으므로 complete regression을 반복하지 않았다. `git diff --check`도 통과했다.

실행하지 않은 항목은 frontend build, Playwright, large statistical diagnostic, calibration/holdout, historical artifact regeneration과 balance tuning이다.

## Source integrity

시작 기준 HEAD `d9f56219c34ebec9cea5969b1d302432e52ee453`를 확인했다. 사용자 소유 untracked prompt 네 개를 보존했고 task 밖 frontend/backend 변경과 충돌은 없었다. Production frontend, champion/player/Draft resource, baseline/report artifact, gameplay tuning과 Gradle 설정은 변경하지 않았다. Git add/commit/push를 수행하지 않았다.

## 제한과 다음 단계

현재 backend는 process-local, single-node이며 restart 시 aggregate/child/reservation/receipt가 사라진다. Save/Load/persistence, auth/ownership, multi-node lock/lease, background job/recovery, rate limit과 Series frontend는 없다. Full replay는 current production/resource identity가 stored identity를 재현할 수 있을 때만 가능하다.

다음 순서는 다음과 같다.

```text
SERIES_FRONTEND_V1
-> SERIES_LIVE_E2E_AND_ACCESSIBILITY
```
