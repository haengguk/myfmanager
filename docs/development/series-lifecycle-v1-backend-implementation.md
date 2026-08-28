# Series Lifecycle V1 Backend Implementation

상태: `SERIES_LIFECYCLE_V1_BACKEND_IMPLEMENTED_READY_FOR_HARDENING`

## 구현 결과

BO3/BO5를 독립 Game 요청의 반복이 아니라 하나의 backend-owned lifecycle로 실행할 수 있게 했다. Aggregate가 team-code score, current game, side/seed, Hard Fearless history, child Draft와 Production V9 commit을 소유한다. 기존 standalone Player Draft와 Real Match는 변경된 Series 의미를 받지 않는다.

구현 범위는 다음과 같다.

- deterministic Series/game/child/reservation identity와 root-to-game seed
- immutable aggregate, Series별 atomic process-local repository, capacity/TTL/lease/receipt 한도
- parent-bound Game 1..N player-controlled Draft와 active resource revalidation
- committed picks only cumulative Hard Fearless와 next-pool preflight
- lock 밖 Production V9 실행, reservation compare-and-commit, failure/no-result rollback
- compact result/game receipt와 no-commit deterministic full replay
- strict additive `/api/v1/series` 10 endpoint와 structured error

Production gameplay configuration, champion/Draft resources, tuning constants, frontend와 generated historical evidence는 변경하지 않았다.

## 검증 결과

구현 중 확인한 focused 결과는 다음과 같다.

| Lane | 결과 |
| --- | --- |
| Series identity/repository/API initial | 12 tests, failure/error/skip 0 |
| Expanded repository/API + actual Production smoke | 10 tests, failure/error/skip 0, Gradle 1분 44초 |
| Existing affected standalone regression 8 suites | 58 tests, failure/error/skip 0, Gradle 7분 15초 |
| Cancel/reservation invalidation + repository/API | 10 tests, failure/error/skip 0, Gradle 26초 |
| Complete backend regression | 231 suites / 2,246 tests / failure 0 / error 0 / skip 0 |

Actual Production smoke는 GEN 대 T1, canonical root seed 73, BO3, managed GEN, Game 1 BLUE GEN으로 실행했다. 매 player turn은 server가 제공한 첫 legal champion을 제출했다.

- committed games: 3
- final score: GEN 2, T1 1
- cumulative Hard Fearless picks: 30
- each completed Draft: decisions 20, final assignments 10
- policy: `MATCH_ENGINE_V1_MATCHUP_COMPOSITION_ACCEPTED_PRODUCTION_POLICY`
- runtime: `PRODUCTION_MATCHUP_COMPOSITION_V1`
- engine: `MATCH_SIMULATOR_ENGINE_IMPLEMENTATION_V9`
- committed game replay: full deterministic equality, score/history/revision mutation 0
- aggregate/full timeline reference: 0

이 winner/score는 balance 또는 승률 증거가 아니라 bounded wiring/correctness fixture 결과다.

## Idempotency와 concurrency evidence

- 64개 concurrent create에서 success 32, capacity rejection 32, maximum retained 32
- 같은 create command + 같은 payload replay 1건은 기존 Series를 반환
- 같은 command + 다른 payload conflict는 aggregate/index/capacity mutation 0
- 같은 Series 20개 concurrent mutation은 revision 1..20을 잃지 않고 직렬화
- 별도 Series revision은 0으로 유지되고 repository instance도 격리
- 119분 GET은 activity/expiry를 연장하지 않고 120분 parent expiry 확인
- 30분 child idle expiry는 parent score/history/revision을 변경하지 않음
- 5분 simulation lease expiry는 reservation을 해제하고 retryable game 상태로 전환
- Series cancel은 child/reservation을 invalidate하고 exact command replay mutation 0

Milestone은 핵심 correctness와 bounded concurrency를 고정한다. 더 강한 adversarial race, executor blocking/late-result injection, receipt-cap saturation과 process lifecycle stress는 다음 `SERIES_LIFECYCLE_V1_BACKEND_HARDENING` 범위다.

## Final verification receipt

Final executable tree의 complete backend Gradle `test`는 첫 실행에서 통과했다.

- suites: 231
- tests: 2,246
- failures: 0
- errors: 0
- skipped: 0
- aggregate JUnit XML: 1,668.336초
- Gradle wall: 27분 56초

Clean full 뒤에는 문서와 final report wording만 갱신했으므로 전체 회귀를 반복하지 않았다.

Frontend build, Playwright, large statistical diagnostic, calibration/holdout과 historical artifact regeneration은 이 backend-only milestone에서 실행하지 않았다.

## Source integrity

Task 시작 기준 HEAD는 `ba973d02ccfaba031de6c0f4d66dbe9e1176be8c`였다. 기존 사용자 untracked prompt 세 개는 보존했다. Git add/commit/push를 수행하지 않았고 frontend/resources/gameplay tuning은 변경하지 않았다.
