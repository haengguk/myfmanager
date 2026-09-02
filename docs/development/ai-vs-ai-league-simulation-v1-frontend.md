# AI vs AI League Simulation V1 Frontend

## Status

`AI_VS_AI_LEAGUE_SIMULATION_V1_FRONTEND_ACCEPTED`

기준 HEAD는 `ca7317ec09f04874dca348bc8e56f83c07d544e2`다. 기존 Inbox, AUTO Real Match,
standalone Player Draft, standalone BO3/BO5 Series와 League API의 공개 필드는 유지했다. Frozen
product decision hash, Production V9, Matchup/Composition, Jungle policy, Hard Fearless와 seeded
Random 소비 순서는 변경하지 않았다.

## OpenDesign read-only 기준

기존 lolmanager competition overview와 schedule overview를 읽기 전용 시각 기준으로 사용했다.
새 generation, project 수정, Cloud credit 또는 BYOK 사용은 없었다.

- Visual thesis: 거의 검은 운영 콘솔 위에 얇은 divider, 높은 정보 밀도와 한 가지 teal accent로
  현재 상태와 다음 행동만 선명하게 드러내는 e-sports management workspace다.
- Content plan: 고정 Season context와 현재 Round → 가장 가까운 실행/Player 행동 → 순위표 →
  전체 일정과 fixture/job inspector 순으로 스캔한다.
- Interaction thesis: 140/180ms의 짧은 hover/status 전환, 하나의 작업 spinner, Series 진입/복귀만
  사용한다. reduced-motion에서는 상태와 포커스를 유지하고 animation을 제거한다.
- Typography: repository의 local `PretendardVariable.woff2`를 재사용한다. 한글/영문 UI는
  Pretendard Variable 9–20px, 상태/숫자는 tabular numerals, seed/hash/job ID는 기존 mono fallback을
  사용한다. 외부 CDN이나 새 font는 없다.
- Tokens: 기존 `tokens.css`의 dark background/surface, foreground/muted, teal accent/focus,
  success/warning/danger와 1px border를 재사용했다. 4/7px radius, 2px focus-visible outline,
  37–39px table row와 112px navigation rail/62px global header 밀도를 유지했다.
- Patterns: AppShell navigation/header, underline tabs, divider 기반 table/list, text+shape status,
  right inspector, loading/error/empty state와 destructive confirmation dialog를 재사용했다.

마케팅 hero나 장식용 gradient/card mosaic는 추가하지 않았다. OpenDesign 정적 표현과 실제 API
상태가 충돌하는 경우 API authority를 유지하고 동일한 시각 언어 안에서 layout만 조정했다.

## Phase A delivery hardening

### Durable worker wake-up

이전에는 `run-current-round`가 DB에 command/job을 저장한 뒤 background submit false를 무시하고,
exact replay에서 pump를 다시 깨우지 않았다. 이제 최초 요청과 exact replay 모두 nonterminal
queued/retry work가 있으면 pump를 idempotent하게 깨운다. Submit 실패는 durable work를 보존한 채
`LEAGUE_BACKGROUND_EXECUTION_UNAVAILABLE`, HTTP 503, `retryable=true`로 반환한다. Replay는 pump만
재가동하며 job, receipt, outbox와 standings mutation은 0이다.

실제 Spring integration은 background enabled public POST의 202 뒤 다섯 Round job을 polling해
모두 `COMPLETED`, attempt 1, standings revision 5까지 확인했다. Startup의 no-auto-gameplay 정책과
lease fencing은 유지했다.

### Player Series commands

Response mapper가 binding 상태만 보지 않고 authoritative child Series를 read-only로 검사한다.

| 상태 | 노출하는 Player command |
| --- | --- |
| Series 생성 직후, Draft 중, Game commit 뒤 Series 미완료 | `RESUME_PLAYER_SERIES` |
| decisive BO3/BO5 완료 | `RECONCILE_PLAYER_SERIES_COMPLETION` |
| completion pending | reconciliation/polling |
| verified/applied, cancelled, blocked, restart-required, cross-scope | 없음 |

READY Season에는 run/cancel만, RUNNING/WAITING에는 pause, PAUSED에는 resume/cancel만 노출해
service eligibility와 화면 행동을 일치시켰다.

## Frontend architecture and authority

League feature는 DTO type, exact runtime validator, API client/failure mapping, backend-to-view adapter,
pointer와 command reconciliation, creation/dashboard/schedule/standings/fixture inspector/cancel dialog로
분리했다. 새 npm package는 추가하지 않았다.

- Validator는 10 teams, 18 rounds, 90 BO3 fixtures, Hybrid 18 Player/72 Auto, Spectator 90 Auto,
  standings/counter/scope/identity/command coherence와 duplicate를 fail-closed한다.
- API client는 structured error만 사용자 문구로 mapping하며 SQL, stack 또는 raw internal message를
  표시하지 않는다.
- `sessionStorage`에는 League/Season ID와 현재 logical command reference만 저장한다. Season view,
  standings, score와 fixture 결과는 저장하지 않는다.
- Mutation UUID는 operation/target/revision/payload에 결속되고 response loss에서 같은 ID를
  재사용한다. Out-of-order revision은 현재 view를 덮지 못한다.
- Not-found만 pointer를 제거하며 network/503은 유지한다. Reload는 항상 GET으로 복구한다.
- 202 polling은 bounded delay, terminal stop, document hidden/AbortController cleanup을 사용한다.
  저장된 run command는 stalled durable job의 exact replay에만 사용한다.

## User journey

Sidebar의 기존 competition 위치에서 AI 리그로 진입한다. Pointer가 없으면 Hybrid/Spectator,
authoritative 10 teams, League/Season key와 signed-long root seed를 선택한다. 생성 뒤 첫 viewport는
Season 상태/현재 Round, Round 5 fixtures와 CTA, 10-row standings, 선택 fixture inspector를 한 작업면에
표시한다. 전체 일정은 Round/team/mode/status 필터, 90-row table와 고정 right inspector를 제공한다.

Hybrid의 playable fixture만 Player action을 제공한다. Start/resume은 server-issued Series ID로 기존
Series frontend를 열고 `AI 리그로 돌아가기`와 Round/matchup context를 유지한다. Reload 뒤에도 작은
League-Series context pointer와 authoritative Series GET으로 복구한다. Standalone Series 설정이나
클라이언트가 만든 score/history로 우회하지 않는다. Completion은 server command가 준비됐을 때만
같은 logical ID로 reconcile하고 `standingsApplied=true` 뒤에만 League view를 갱신한다.

Pause/resume/cancel도 `allowedCommands`에 있을 때만 보인다. Cancel dialog는 안전한 버튼에 초기
focus를 두고 Tab/Shift+Tab trap, Escape, trigger focus return과 pending 중 종료/중복 제출 차단을
구현한다.

## LIVE browser verification

사용자 runtime DB와 분리한 in-memory H2, background executor enabled backend를 사용했다.

- Hybrid GEN Season: 10 standings rows, 18 rounds, 90 fixtures, 18 Player/72 Auto.
- Round 1: Auto 4 + Player 1. Public run은 HTTP 202였고 실제 Production V9 Auto 4 jobs가 terminal,
  Round 4/5, standings update와 reload recovery를 확인했다.
- Managed GEN–HLE fixture는 existing BO3 Series/Game 1 화면으로 진입했다. Series context는 reload 뒤
  복원되고 League로 돌아온 뒤 `RESUME_PLAYER_SERIES` 의미가 유지됐다.
- 별도 Spectator Season은 90 Auto를 표시하고 actual round jobs를 완료했다. Authoritative refresh 뒤
  pause→resume, cancel dialog 흐름을 확인했다.
- Schedule은 all 90, managed GEN 18, Player 18을 실제 DOM에서 확인했다.
- Clean production preview에서 console errors/warnings, page errors, runtime validation errors와
  reference fallback은 모두 0이었다.

전체 90경기 Season 완주와 대표 Player BO3 완주는 실행하지 않았다. 실제 Auto와 Player handoff에
필요한 최소 fixture만 사용했으며 이는 balance/performance population 증거가 아니다.

## Responsive and accessibility verification

- 1440×900, 1280×720 모두 document/body horizontal overflow 0.
- 두 크기에서 current Round, CTA, standings와 inspector를 사용할 수 있고 long seed/ID는 핵심
  column을 깨지 않고 ellipsis/상세 영역에 머문다.
- 1280×720의 standings 내부는 매우 좁은 폭에서 36px의 안전한 table scroll 여유가 남지만,
  page overflow나 CTA/마지막 column 잘림은 없다.
- Accessibility tree에서 navigation current state, headings, table row/column semantics,
  alertdialog title/description과 live regions를 확인했다.
- Dialog keyboard evidence: initial `돌아가기`, Shift+Tab→`시즌 취소`, Tab→`돌아가기`,
  Escape close 뒤 trigger `시즌 취소`로 focus return.
- `prefers-reduced-motion: reduce`에서 media query true, League spinner animation name `none`.

이는 DOM/accessibility tree/focus behavior 검증이며 특정 screen reader 제품의 실제 음성 품질을
검증했다는 의미는 아니다.

## Verification commands

```text
cd frontend
npm run league:verify
npm run player-draft:verify
npm run series:verify
npm run reference:check
npm run reference:verify
npm run bundle:verify
VITE_REAL_MATCH_API_BASE_URL=http://localhost:8086 npm run build

cd backend
gradlew.bat test --tests <Phase A focused classes> --console=plain
gradlew.bat test --console=plain
```

League validator/reconciliation marker는
`AI_VS_AI_LEAGUE_FRONTEND_CONTRACT_VERIFICATION_PASSED`다. Production build는 132 modules,
initial bundle 496,300 bytes, lazy reference bundle 423,581 bytes였고 backend full은
259 suites / 2,336 tests / failures 0 / errors 0 / skipped 2, aggregate XML 1,122.363초,
`BUILD SUCCESSFUL`, Gradle wall 18분 47초로 통과했다.

## Remaining limits

Backend는 local single-node H2 reference다. Auth/ownership, multi-node database/worker consensus,
external broker, distributed capacity control, production load/long-running restart evidence와 90-fixture
official full Season은 없다. Current Round 단위 실행만 제공하며 웹 push/WebSocket 대신 bounded
polling을 사용한다. Completion-ready Player BO3의 실제 끝까지 진행하는 LIVE 증거는 후속 E2E로
확장할 수 있다.
