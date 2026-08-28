# Player Controlled Draft LIVE E2E and Accessibility

상태: `PLAYER_CONTROLLED_DRAFT_LIVE_E2E_AND_ACCESSIBILITY_ACCEPTED`

검증일: 2026-08-28

## 목표와 범위

이 milestone은 새 Draft gameplay나 AI tuning을 추가하지 않는다. 실제 Production V9 backend와 실제 브라우저에서 경기 설정 → 직접 Draft → 20턴 완료 → 경기 실행 → Playback → Result → mixed-authority review 경계를 검증하고, 공통 응답 계약·terminal projection·키보드 접근성·transport failure reconciliation을 production 수준으로 닫는다.

BO3/BO5, Game 2+, 누적 Hard Fearless, persistence, auth, WebSocket/background job과 Draft timer는 범위 밖이다. Matchup/Composition과 Jungle Economy/Tempo 정책, Auto Draft scoring/search, seeded gameplay Random은 변경하지 않았다.

## Contract hardening

`REAL_MATCH_RESPONSE_V1`과 `PLAYER_DRAFT_MATCH_RESPONSE_V1` envelope는 계속 별도 validator를 사용한다. 두 envelope가 공유하는 presentation/result/timeline/event/final snapshot 의미만 `commonMatchSemantic.validation.ts`에서 한 번 검증한다. Player Draft payload를 Auto payload로 cast하지 않고, 어느 경로도 검증 강도를 낮추지 않는다.

공통 경계는 다음을 Playback/Result 정규화 전에 거부한다.

- presentation 밖 actor/killer/victim/assistant
- player ID와 champion ID, side, position의 불일치
- assistant player/champion 길이 불일치와 중복 pair
- final snapshot team kills/gold와 result 불일치
- final player identity, K/D/A, CS, gold, XP, level과 result 불일치
- Player Draft final assignment와 match presentation 불일치

`npm run player-draft:verify`는 valid ACTIVE/COMPLETED/CANCELLED/SIMULATION과 mutation rejection을 포함한 33개 시나리오를 통과했다.

## Terminal session wire contract

Canonical projection은 다음과 같다.

```text
ACTIVE
  currentTurn != null
  selectable/unavailable/recommendation projection 가능

COMPLETED / SIMULATED / CANCELLED / EXPIRED
  currentTurn == null
  action selection projection 없음
```

Backend mapper는 `status == ACTIVE`인 경우에만 current turn을 직렬화한다. Incomplete ACTIVE session을 취소한 focused API test는 CANCELLED, null currentTurn, 빈 selectable/unavailable/recommendation, null selectable-set identity를 확인한다. DELETE는 204와 empty body를 유지하고 취소 뒤 action/simulate 거부 의미도 보존한다.

## Keyboard와 screen reader 의미

Champion pool 173개는 roving tabindex를 사용한다. Grid 전체의 Tab stop은 항상 1개이며 Arrow/Home/End로 이동한다. focus, `aria-pressed` selection과 별도 확정 button을 분리하므로 tile의 Enter/Space 한 번이 서버 POST를 만들지 않는다. Turn 변경 뒤 첫 legal champion에 focus가 이동하고 unavailable tile은 `aria-disabled`와 구조화된 한국어 사유를 함께 제공한다.

검색, role filter, 결과 수, 선택 preview, AI pending, reconciliation, 완료와 오류는 native control, heading/landmark, polite status 또는 assertive alert로 구분한다. BLUE/RED는 색뿐 아니라 side/team 문구로 표시한다. 전역 token의 `prefers-reduced-motion: reduce` 규칙이 turn/reveal/dialog animation과 transition을 사실상 제거한다.

취소 modal은 non-destructive control에 초기 focus를 두고 Tab/Shift+Tab을 내부에서 순환한다. Pending 전환은 lifecycle cleanup이나 focus restore를 실행하지 않으며 dialog container에 focus와 `aria-busy=true`를 유지한다. Pending Escape는 modal을 닫거나 DELETE를 추가 실행하지 않는다. 일반 Escape는 닫기만 하고 원래 back control로 복귀한다. React StrictMode의 extra effect cleanup 뒤 lifecycle guard가 false로 남던 실제 E2E 회귀는 effect setup마다 guard를 다시 활성화해 해결했다.

## Network, idempotency와 race

화면은 현재 session projection만 authority로 사용한다. Session ID가 다르거나 revision이 낮은 response, terminal status를 이전 상태로 되돌리는 response는 적용하지 않는다. 한 mutation 동안 action/simulation/cancel/refresh 중복 진입을 ref로 동기 차단한다.

- 같은 logical action retry는 같은 revision/champion/`clientActionId`를 유지한다.
- NETWORK/TIMEOUT/CANCELLED 수신 실패는 GET을 한 번 수행하고 original `clientActionId`가 decisions에 있을 때만 선택 완료를 확정한다.
- `STALE_DRAFT_REVISION`은 GET 뒤 최신 turn을 표시한다. conflict/illegal selection은 자동 재시도하지 않는다.
- Simulation abort는 수신 중단일 뿐 backend rollback이 아님을 표시하며 같은 completed session으로만 retry한다.
- ACTIVE/COMPLETED back은 destructive modal을 사용한다. SIMULATED review back은 DELETE 없이 Playback/Result로 돌아간다.
- Unmount는 controller를 abort하지만 자동 DELETE하지 않는다.

실제 browser interception에서 action은 backend에 도달하도록 `route.fetch()`한 뒤 response만 abort했다. UI는 GET 200으로 revision 1/decisions 2를 복구했고, “서버에서 선택 반영을 확인했습니다”와 다음 legal champion focus를 표시했다. Payload나 gameplay state는 fabrication하지 않았다.

## LIVE browser 결과

실행 환경은 `http://localhost:8080` Production V9 backend와 최신 Vite source를 제공하는 task-local `http://localhost:5174` proxy였다. Proxy는 same-origin browser transport만 제공했고 gameplay payload를 바꾸지 않았다.

| Flow | 실제 endpoint | 결과 |
| --- | --- | --- |
| BLUE controlled | create 1, action 10, simulate 1 | 모두 HTTP 200, revision 10, decisions 20, PLAYER 10/AI 10, final assignments 10, Playback/Result/mixed review 통과 |
| RED controlled | create 1, action 10, simulate 1 | 최초 response가 BLUE AI turn 1을 포함해 decisions 1/RED turn 2로 시작, 이후 revision 10/decisions 20, 모두 HTTP 200, Playback/Result 통과 |
| AUTO | real-matches simulate 1, player-drafts 0 | HTTP 200, 읽기 전용 Auto Draft 20/20, Draft origin AUTO 유지 |
| Response loss | action server commit 1, browser response abort 1, GET 1 | GET 200 뒤 decisions 2로 reconciliation, stale overwrite 0 |
| Cancel | 지연된 DELETE 1 | pending focus dialog 내부, Escape 후 dialog 유지, HTTP 204 뒤 설정 복귀 |

RED의 10 action 응답은 69~2,500ms, first simulate는 약 10,300ms였다. AUTO simulate는 약 10,761ms였다. 완료된 RED session의 deterministic receipt 재조회에서 `HTTP/1.1 200`, `Content-Encoding: gzip`, `Vary: ... accept-encoding`을 확인했다. 이는 본 RED full flow의 logical simulate 1회와 구분한 transport header 진단이다.

Clean BLUE/RED/AUTO 구간의 page error, console error, runtime validation error와 reference fallback은 각각 0이었다. Recovery 구간에는 의도적으로 abort한 request의 browser network error만 있었고 제품 contract error나 fallback은 없었다. Result의 ban slot 10개는 모두 portrait image였고 fallback/broken image는 0개였다.

| Viewport | page scroll width | client width | horizontal overflow | champion cards / Tab stops |
| --- | ---: | ---: | ---: | ---: |
| 1440×900 | 1440 | 1440 | 0 | 173 / 1 |
| 1280×720 | 1280 | 1280 | 0 | 173 / 1 |

핵심 focus trace는 설정 control → first legal champion → selected tile → explicit confirm → 다음 legal champion, modal back → 계속 진행 → Shift+Tab destructive control → Escape back restore, result button → modal close/result confirm 순서로 통과했다.

## Current V9 handoff 재현성

기본 historical handoff는 V8/BASELINE일 수 있으므로 current V9 증거로 승격하지 않았다. `live:verify`는 명시적 directory 또는 options/response path를 받고, V9 policy/profile/configuration/engine preflight와 raw SHA를 먼저 출력한다. Stale 입력은 giant data-URL stack 대신 expected/actual identity와 경로 지정 방법을 안내한다.

이번 실행의 ignored local 입력은 다음과 같다.

- `frontend/output/player-draft-live-e2e/options-v9.json`: 6,283 bytes, SHA-256 `eb82bdf43b7698802e192be5c8aa2f2d39f11a80fc81f4077eb91709b480f1f9`
- `frontend/output/player-draft-live-e2e/response-v9.json`: 19,711,855 bytes, SHA-256 `8edadba7b8e2cc08cb0b731a53c362c69fb2143bdfe6b54636aee24b7d78a7f4`

명시적 검증은 policy `78c3bb1cffe2cd90a1f7acab6923a1813fea40acd135186ff522eabf95d38493`, profile `PRODUCTION_MATCHUP_COMPOSITION_V1`, configuration `caaf76274dc148040b0a95eae1ed5181790b2fc840f45af9b109ea7951c1fd5d`, engine `MATCH_SIMULATOR_ENGINE_IMPLEMENTATION_V9`를 확인했다. 결과는 teams 10, players 50, decisions 20, assignments 10, events 376, snapshots 233, RED 승리/2,320초와 mutation 10종 통과다.

`frontend/output/`은 `.gitignore` 대상이다. Historical official artifact와 checked-in reference는 덮어쓰지 않았다. Screenshot은 별도 생성하지 않았으며 acceptance는 실제 DOM/API/browser trace를 사용했다.

## Verification summary

- Player Draft frontend contract: 33 scenarios, failure 0
- Focused backend API: 1 suite / 5 tests / failures 0 / errors 0 / skipped 0
- Frontend production build: 100 modules, JS 353.95kB (gzip 113.76kB), CSS 122.49kB (gzip 21.37kB)
- Reference check: SHA-256 `977c7d6e015f4ebd5ecba8e24e7b95a0a6313fef2e1e69a2c396b4fab36ac15e`, 794,907 bytes
- Reference verify: teams 10, players 50, decisions 20, events 287/517, snapshots 59/344
- Bundle verify: initial 373,627 bytes, reference chunk 423,581 bytes, lazy true
- Complete backend regression: 226 suites / 2,232 tests / failures 0 / errors 0 / skipped 0, aggregate XML 1,377.624초, Gradle wall 23분 16초

실제 30분 expiry sleep, 대형 seed population, calibration/holdout, BO3/BO5와 screenshot-only 비교는 수행하지 않았다. Expiry/terminal correctness는 focused backend clock/API test로 검증했다.

## 현재 가능한 흐름과 다음 단계

사용자는 마우스 없이 BLUE 또는 RED를 제어해 10개 결정을 명시적으로 확정하고, AI와 합쳐진 20턴 Draft/final assignment로 Production V9 경기를 실행한 뒤 Playback, Result와 mixed-authority review를 오갈 수 있다. Transport response loss와 stale state는 서버 projection으로 조정되고 취소는 명시적 modal/204 경계로만 확정된다.

다음 제품 순서는 다음과 같다.

```text
SERIES_LIFECYCLE_V1_BACKEND_IMPLEMENTATION
→ SERIES_LIFECYCLE_V1_HARDENING
→ SERIES_FRONTEND_V1
```
