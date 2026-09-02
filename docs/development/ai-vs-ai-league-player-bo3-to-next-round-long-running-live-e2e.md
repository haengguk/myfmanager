# AI League Player BO3 to Next Round Long-running LIVE E2E

## Status

`AI_VS_AI_LEAGUE_PLAYER_BO3_TO_NEXT_ROUND_LONG_RUNNING_LIVE_E2E_ACCEPTED`

기준 HEAD는 `918964206813c926ac95126b0b6fbf8613342456`이다. 이 검증은 fresh Hybrid
Season의 Round 1에서 실제 Production V9 Auto fixture 4개와 관리 팀 Player BO3 1개를 모두
완료하고, Player 결과를 receipt/outbox/application ledger를 거쳐 한 번만 standings에 반영한 뒤
`currentRound=2`까지 전환했다. Round 2 경기는 실행하지 않았다.

최종 executable/test source identity는 변경된 production·focused test 5개 파일의 정렬된
SHA-256 manifest 기준
`13a3df24f45b6d4bf00b20cbba083fea2f040e3c75ed9b4257a28d9641c79640`이다.

## Isolated LIVE environment

- Backend: `localhost:8091`, task-owned Java process. Game 1 뒤 mandatory restart는 PID
  `317366`에서 `322133`으로 교체했고 같은 file-backed H2를 다시 열었다. 발견 결함 수정본의
  최종 확인 process는 PID `335497`이었다.
- Frontend: `localhost:5173`, production build를 task-owned static server로 제공했다.
- Database: `backend/build/live-e2e-918964-20260902-0105/runtime/league-live` 아래의 전용 H2.
- Browser: task-owned Chromium, 1440×900. 실제 UI에서 Season 생성, Round 실행, Player Draft,
  V9 실행, Series 복귀와 completion을 수행했다.
- Evidence: `output/playwright/ai-league-live-e2e-918964/.playwright-cli/`. 필수 화면은
  `page-2026-09-02T04-05-22-407Z.png`, `page-2026-09-02T04-13-48-461Z.png`,
  `page-2026-09-02T04-57-18-222Z.png`이다.

League ID는
`league_2ac350316e47442c93f5f9b4130e80e1131f5b063f436e268421bae7c68d8d76`, Season ID는
`season_ae3abc94e2f910ed5b4dc152e6e669ce2eaa8a05bfddf089ddf2de21192caa78`였다.
`HYBRID_MANAGER`, managed team `GEN`, canonical root seed `73`, 18 rounds/90 BO3,
18 Player/72 Auto가 authoritative API와 DOM에서 일치했다.

## Cross-target completion Promise defect

변경 전 `LeagueCompletionReconciler`는 target A가 active이면 fixture/binding/revision이 다른
target B에도 A의 Promise를 반환했다. Production reconciler를 직접 호출한 focused test에서 B의
GET/POST가 실행되지 않고 A의 결과를 공유하는 실패를 먼저 재현했다.

변경 후 같은 operation key의 AUTO/MANUAL만 Promise와 POST 하나를 공유한다. 다른 target은
작은 serial queue에서 자기 dependency와 logical command로 authoritative하게 재평가한다. A의
성공, retry exhausted, terminal failure와 abort 뒤에도 B가 누락되지 않으며 abort는 queued work도
정리한다. `frontend/scripts/verify-league-completion-recovery.mjs`의 20 scenarios가 통과했다.

## Round 1 Auto fixtures

UI의 현재 라운드 Auto 실행을 한 번 눌렀다. Player fixture는 worker 대상에서 제외됐고 Auto 4개는
모두 attempt 1에서 완료됐다.

| Match | Job | Final | Receipt | Outbox |
| --- | --- | --- | --- | --- |
| BRO–NS | `job_c7ab457667ddce712db6fa80d335b9872d2320994ad2ad15b64fd6b6f29938f1` | attempt 1 `COMPLETED` | `197035274c8fc5d0f0ee5fe9414a7ed0656409199a98b585f0515f1fb49eb922` | `DELIVERED` |
| BFX–T1 | `job_0ab0a3addf4db3f45854bb64e725edc41ec9f6460be169232b487c35c68b19d7` | attempt 1 `COMPLETED` | `617de8dd8553b2ae4c0880ef3c49fd29201ac1d0e5e1d5631e61904bc3bd3cd6` | `DELIVERED` |
| DK–KT | `job_c3f2edb8eeef5f83acfe381ada804dc275cd0bb2cc26d8ed8b49052fb12b483c` | attempt 1 `COMPLETED` | `e0b8f34198f574a57b56eaa547e05a7411b3cb6a71d3093142318929700b2d31` | `DELIVERED` |
| DNS–KRX | `job_1ccda9e8d65ba87209351fe2609b530a22876890b99d937b1e5242d575822d3f` | attempt 1 `COMPLETED` | `5ec7438383643b4fd8d037459810bf765d284d31d05cbc7bbb7a61df8cd2a65b` | `DELIVERED` |

Auto 적용 뒤 standings revision은 4였고 Round 1은 4/5에서 멈췄다. duplicate fixture receipt,
stale/late mutation과 Player fixture Auto 실행은 0이었다.

## Managed GEN–HLE Player BO3

Player fixture는
`fixture_4c682f5996d28fd90935548139b86965aa6b554cadb3bca57232cc9dc3cff94f`, bound Series는
`series_fd9f340bb2e1e81da2d688f244e3bf2dad78de143ada3282cddecc0c381205c2`, fixture seed는
`-1598576963650861399`였다. 매 PLAYER turn에는 화면이 제공한 legal stable champion ID의
오름차순 첫 항목을 골랐고 상대 turn은 Production Auto Draft가 수행했다.

| Game | BLUE / RED | Control | Seed | Draft | Winner / score | HF before→after |
| --- | --- | --- | ---: | --- | --- | --- |
| 1 | GEN / HLE | BLUE | `3222386668377446947` | 20 decisions / 10 assignments | HLE / 0–1 | 0→10 |
| 2 | HLE / GEN | RED | `6056728533026012906` | 20 decisions / 10 assignments | GEN / 1–1 | 10→20 |
| 3 | GEN / HLE | BLUE | `637243265471169495` | 20 decisions / 10 assignments | HLE / 1–2 | 20→30 |

PLAYER 선택은 다음과 같았다.

- Game 1: `aatrox`, `ahri`, `akali`, `akshan`, `alistar`, `ambessa`, `amumu`, `anivia`,
  `aphelios`, `belveth`
- Game 2: `aatrox`, `ahri`, `akali`, `amumu`, `anivia`, `annie`, `ashe`, `aurelion-sol`,
  `bard`, `caitlyn`
- Game 3: `aatrox`, `ahri`, `akali`, `ashe`, `aurelion-sol`, `aurora`, `azir`,
  `blitzcrank`, `brand`, `braum`

각 Game receipt는 policy
`MATCH_ENGINE_V1_MATCHUP_COMPOSITION_ACCEPTED_PRODUCTION_POLICY`, runtime
`PRODUCTION_MATCHUP_COMPOSITION_V1`, engine `MATCH_SIMULATOR_ENGINE_IMPLEMENTATION_V9`였다.

| Game | Input / output | Replay / simulator timeline / structured timeline | Random draws / trace |
| --- | --- | --- | --- |
| 1 | `70de01966c71fcfeafb47edae9bdc38678ef3ba5892a2b835625ac7ef307fe89` / `13c0216c8071e9d926a25560cf2f6404abc3b979070bb4db9a34ac3b43a4529e` | `e4f5ef7ca3bd0643039f1284375ce375e2e2a1ad79c38f3091cb8085b7571170` / `6ba285aa7d8bbd8238375a8d930e2ec121017c2fced751608b54302ce4c4d506` / `0e2719a0652881110785526d37b64c74f9f4fed009bfac98ac580e5c54a0ec3f` | 4,352 / `50c4ddd8256c0c47537f2ba092e6f68063cd1219fa209f2702a953ba6e93f89b` |
| 2 | `051ace68b57d16fb62eb78d7551f4b99e2222b54e73f60df0370960f8eadad07` / `d4f88d331deafc51ebc386fe47ecff206ab1840223c62ba31d43b38b6a0d6b98` | `8fcbe6a38944241d1a0956a7f53748a480fc51aedd53613a2ba7995888b63631` / `65048aada8f51f5b9c7e76d89c638603e26eb056172c8b40ae97ece8bd599f5d` / `1f70634b012ecd6b96aaac055481f1545cb291a9bb91c33f09e347c578cf4829` | 3,020 / `9ed382b3fd26f72198ddf1a226503720a42e2efa1bfb8f085fcff6e86ae488a8` |
| 3 | `3f91c067fe2887fb143479e19b6b66aff7309355bf09154f6f666fe63022ea2d` / `ca2598e3b439f9ed200ab92e21fcede68f119647d1680203a73b8688e1ce71e4` | `f04b823cbac4f1e1f70097efbb2da23c67b5b48f8ab222baab72c3a4daea7919` / `a1f5dfbed5bacf4f46f8783f1361e9a43d37078e12981c26919478ce59bfdc3b` / `655fdc40020397849a53467ffa0ccfa302d481c79104363e90bfc4016058063b` | 5,166 / `68ad264f01f61e77084b071ac26bed7fc047ee641e473f5a22472aa080fa4dca` |

## Backend restart recovery

Game 1 commit 뒤 Game 2 Draft 시작 전에 backend를 정상 종료하고 같은 H2로 재시작했다. 재시작
전후 League/Season/fixture/binding/Series ID, Series revision `12`, score 0–1, Game 1 receipt,
committed Hard Fearless picks 10개, Game 2 side RED와 seed
`6056728533026012906`가 동일했다. 새 Season/Series와 Game 1 gameplay 재실행은 0이었고 Player
standings는 아직 미반영이었다.

장시간 흐름은 추가로 completion 직전 `PLAYER_DRAFT_COMPLETION_BINDING_MISMATCH`를 발견했다.
file-backed checkpoint는 동일한 immutable Draft evidence를 새 Java object로 복구하지만 trusted
boundary가 object identity를 요구하고 있었다. 수정 전 focused test는 구조적으로 동일한 recovered
result를 거부했다. 수정 후 object identity 대신 이미 봉인된 draft/control/rule/meta/legal-role
hash와 projected input/roster/decision/assignment/final-draft/policy identity를 authority로 사용한다.
Gameplay, Draft 판단, Random 소비와 tuning은 변경하지 않았다.

## Background job polling consistency

최종 full regression의 첫 실행은 2,336 tests 중
`LeagueApiV1BackgroundExecutionIntegrationTest` 1건에서 job polling GET이 일시적으로 HTTP 500을
반환해 실패했다. 동일 class만 다시 실행해도 같은 line 68 실패가 재현됐다. Job view가 scope 확인만
필요한데도 여러 statement로 Season aggregate 전체를 복구했고, 그 사이 outbox standings commit이
완료되면 서로 다른 시점의 season revision과 application ledger를 섞어 읽을 수 있었다.

Job 단건/current-round 조회는 이제 `league_season`의 league/season scope를 단일 statement로 확인한
뒤 job row를 투영한다. 쓰기, standings 적용, job lifecycle과 retry 정책은 바꾸지 않았다. 변경 전
동일 focused는 2분 17초에 실패했고 변경 후 2분 15초에 통과했으며, API/mapper focused도 1분 45초에
통과했다.

## Completion, exactly-once, and Round 2

최초와 동일한 completion UUID
`a4d301d5-0ef0-4a7f-b27e-c312a04359ff`, expected lifecycle revision `10`, binding hash
`3b406f6e8da1e8aaf27cd3fde5bf3e9c853cb3e04cb2462295a0824af5bdbdfe`를 사용했다.
성공 응답은 Player receipt
`c6beb2bc6659fe59592d0d56c18cb14f39c55a2921406bfd9648d25de5bbe7a7`, binding `VERIFIED`,
outbox `DELIVERED`, standings applied `true`, standings revision `5`였다.

같은 POST를 exact replay했을 때 HTTP 200과 `replayed=true`를 받았다. 그 뒤 Season lifecycle
revision `11`, standings revision `5`, completed fixture 5, current Round 2와 receipt hash가 그대로였다.
DB에는 Player receipt 1건, outbox event 1건(delivery attempts 1), standings application 1건만 있었다.
추가 engine 실행, Hard Fearless commit과 revision 증가도 0이었다.

Round 1 종료 시 10개 팀은 모두 series played 1이었다. series wins/losses 합은 5/5,
game wins/losses 합은 13/13, game differential 합은 0이었다. Round 2는 Player `DNS–GEN`과 Auto
4개를 표시했고 0/5, job 0건이었다. 새로고침 뒤에도 같은 Round 2와 standings가 복구됐다.

## Verification

- Frontend recovery focused: 20 scenarios passed. `league:verify`, `series:verify`,
  `player-draft:verify`, production build, reference check/verify와 bundle verify passed.
- Backend focused: `PlayerControlledDraftMatchInputBoundaryTest` 10 tests passed after the
  change-before failure was captured. Player handoff/API focused는 2분 35초, background job focused는
  2분 15초, API/mapper focused는 1분 45초에 passed.
- 첫 full regression은 실제 job polling race 1건을 발견했다. 수정 후 final full은 259 suites /
  2,336 tests / failures 0 / errors 0 / skipped 2, aggregate XML 1,829.817초,
  `BUILD SUCCESSFUL`, Gradle wall 31분 7초였다.
- Browser final clean context: console errors/warnings 0, page/runtime validation error 0,
  1440×900 document overflow 0. Round 전환 순간 이전 Round job poll 1건은
  AbortController에 의해 `net::ERR_ABORTED`로 취소됐고 server error나 unhandled rejection은 아니었다;
  전환 후 reload의 authoritative API 응답은 모두 200이었다.

No default test에 장시간 E2E를 추가하지 않았고 90경기 전체 Season, load/balance/holdout은 실행하지
않았다. Task-owned backend/frontend/browser process, 전용 H2/runtime directory와 임시 JDK/profile은
정리했고 `.playwright-cli`의 화면·trace 증거만 보존했다.

## Remaining limits

Backend는 local single-node H2와 bounded polling 기반이다. Auth, external broker, multi-node worker,
full 90-fixture release/load acceptance는 별도 범위다. 이 milestone 뒤 제품 개발 단계는
`TEAM_AND_PLAYER_INFORMATION_API_V1`이다.
