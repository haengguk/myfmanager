# Series Lifecycle V1 Frontend Readiness Hardening

상태: `SERIES_LIFECYCLE_V1_FRONTEND_READY`

## 목적

이번 milestone은 Series 화면을 구현하지 않고, frontend가 backend의 error current state와 `allowedCommands`를 그대로 신뢰할 수 있도록 두 API 계약 불일치를 닫았다. BO3/BO5 규칙, Draft, Production V9 gameplay, resource와 tuning은 변경하지 않았다.

## Failed receipt와 public current state

Failed command receipt는 최초 실패의 다음 historical evidence를 계속 보존한다.

- stable error code
- HTTP status
- retryable 여부
- result identity
- failure가 확정됐을 당시 resulting Series revision/status

Exact replay는 이 historical code/HTTP/retryable 의미를 복원하지만, public `SERIES_API_ERROR_V1.currentRevision/currentStatus`는 replay를 처리하는 시점의 authoritative aggregate에서 가져온다. 따라서 retryable failure 뒤 새 command가 다음 game을 commit하거나 Series를 완료한 경우, blocked failure 뒤 Series를 취소한 경우, lease expiry 뒤 새 command가 성공한 경우에도 과거 current state를 반환하지 않는다.

같은 command ID의 다른 type/payload는 여전히 failed replay보다 먼저 `SERIES_COMMAND_ID_PAYLOAD_CONFLICT`로 거부된다. Exact replay는 Draft/Match executor를 다시 실행하지 않고 aggregate, score, history, revision, child, reservation과 receipt를 변경하지 않는다.

## Capacity-aware allowedCommands

Receipt 한도 256과 no-eviction/fail-closed 정책은 유지한다. `SeriesLifecycleConfiguration`이 한도 값과 slot 판정을 함께 소유하고, service preflight와 response projection이 그 판정을 공유한다.

- 255개에서는 현재 상태상 가능한 신규 mutation을 계속 광고하고 승인한다.
- 256번째 receipt가 생긴 뒤에는 `allowedCommands`에서 신규 Draft/action/simulate/cancel을 모두 제거하고 `GET`만 남긴다.
- 특정 기존 command ID의 exact replay는 새 receipt를 만들지 않으므로 256개에서도 계속 허용한다.
- Exact replay 가능성을 일반적인 신규 mutation 허용으로 표시하지 않는다.
- Eviction, compaction 또는 terminal cancel 예외는 추가하지 않았다.

상태 경계는 `DRAFT_PENDING`, `DRAFT_ACTIVE`, `DRAFT_COMPLETED`, `SIMULATION_FAILED_RETRYABLE`, `BLOCKED`와 active simulation reservation을 포함한다. Service 거부와 public DTO를 같은 aggregate에서 함께 검증했다.

## 검증

변경 전 focused 재현에서는 failed replay current-state 시나리오 4개와 capacity projection 시나리오 2개가 예상대로 실패했다.

최종 Series repository/service/hardening/frontend-readiness/controller 묶음은 5 suites / 27 tests, failures/errors/skipped 0으로 통과했다. ACTIVE/BLOCKED/CANCELLED/COMPLETED current state와 capacity 경계 assertion을 최종 강화한 핵심 2 suites / 15 tests도 다시 통과했다.

Final executable tree의 complete backend Gradle `test`는 첫 실행에서 통과했다.

- suites: 234
- tests: 2,266
- failures: 0
- errors: 0
- skipped: 0
- aggregate JUnit XML: 1,931.403초
- Gradle wall: 17분 12초

Default full에는 actual Production V9 BO3/BO5 smoke 2 tests도 포함됐고 clean pass했다. 별도 중간 smoke는 반복하지 않았다. Clean full 뒤에는 문서만 갱신했으므로 full regression을 다시 실행하지 않았다.

Frontend build, Playwright, balance/calibration/holdout, historical artifact generator와 대규모 seed population은 실행하지 않았다.

## Source integrity와 제한

시작 HEAD는 `ece9f4d2654aae30138a817939e1e9177ede15af`였고 추적 working tree는 clean이었다. 사용자 소유 `prompts/series-lifecycle-v1-frontend-readiness-hardening.txt`는 그대로 보존했다. Frontend, gameplay resource/tuning/profile, Draft search/scoring, Gradle 설정과 baseline/report artifact는 변경하지 않았다. Git add/commit/push를 수행하지 않았다.

현재 V1은 여전히 process-local single-node다. Restart recovery, persistence/save-load, auth/ownership, multi-node coordination, background job/recovery와 receipt compaction은 없다. 256개에 도달하면 신규 cancel도 fail-closed한다.

다음 단계는 `SERIES_FRONTEND_V1`, 그 다음은 `SERIES_LIVE_E2E_AND_ACCESSIBILITY`다.
