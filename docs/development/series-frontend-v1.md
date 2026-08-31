# Series Frontend V1

상태: `SERIES_FRONTEND_V1_ACCEPTED`

## 이번 작업의 제품 의미

이전에는 `/api/v1/series`가 BO3/BO5의 상태를 소유해도 사용자가 브라우저에서 Series를 만들고 끝까지 진행할 화면이 없었다. 이제 LIVE 경기 준비에서 임의의 두 팀, 관리 팀, BO3/BO5, Game 1 관리 팀 side와 signed-long root seed를 선택해 Series를 시작할 수 있다. 고정 score board와 game rail에서 현재 game과 누적 점수를 확인하고, 기존 직접 밴픽 workspace에서 매 game을 진행한 뒤 Playback/Result를 거쳐 다음 game 또는 최종 우승 화면으로 이동한다.

점수, 현재 game, BLUE/RED 매핑, derived game seed, Hard Fearless history, winner와 실행 가능 명령은 모두 backend response가 기준이다. Frontend는 display name, 배열 위치나 결과 문구에서 gameplay identity를 추론하지 않는다. Series 생성 선택지는 LIVE `/options`가 제공한 팀과 roster를 사용하므로 GEN/T1 또는 팀 수 10개를 전제하지 않는다.

## 사용자 흐름

1. 경기 준비에서 `시리즈`를 선택하고 LIVE 팀, 관리 팀, 형식, Game 1 side와 root seed를 입력한다.
2. `POST /api/v1/series` 결과의 current game과 `allowedCommands`로 Series hub를 구성한다.
3. `CREATE_DRAFT_SESSION`이 허용되면 Series-owned child를 만들고, 기존 Player Draft UI에서 server legal pool과 PLAYER/AI turn을 따라 20턴을 진행한다.
4. 완료 검토 뒤 `SIMULATE_CURRENT_GAME`을 실행한다. HTTP 200이면 즉시 Playback으로 이동하고, 202이면 같은 command를 재실행하지 않은 채 GET polling으로 commit을 확인한다.
5. Playback/Result에서 Series context와 score를 유지한다. 다음 game은 교대된 controlled side와 누적 Hard Fearless 제외 목록을 반영한다.
6. required wins에 도달하면 server winner와 final score를 표시한다. 취소는 확인 dialog를 거쳐 child 또는 Series endpoint에 한 번만 전송한다.

Game 2 이상도 같은 Draft workspace를 사용하지만 header의 game number와 BO3/BO5 문맥은 Series binding에서 투영한다. 이미 이전 game에서 양 팀이 pick한 챔피언은 목록에서 제거하지 않고 server가 준 unavailable reason으로 비활성 표시한다.

## 계약과 상태 복구

Frontend에는 Series 전용 exact DTO validator와 client가 있다. Schema/version, canonical ID, revision, status, team-code score, frozen game binding, seed, cumulative history, child generation/revision, compact receipt와 Production identity를 검증한다. Full match는 기존 Player Draft match semantic validator를 공유하면서 Series/game/seed/history identity를 추가로 교차 확인한다. Unknown field나 불일치는 Playback 전에 fail-closed한다.

사용 endpoint는 다음과 같다.

| 동작 | Endpoint | 성공 의미 |
| --- | --- | --- |
| Series 생성 | `POST /api/v1/series` | 201, exact replay 200 |
| 복구/재조정 | `GET /api/v1/series/{seriesId}` | authoritative current view |
| child 생성/조회 | `POST .../games/current/draft-session`, `GET .../games/{n}/draft-session` | parent-bound Draft |
| player action | `POST .../draft-session/actions` | parent/child revision 진행 |
| game 실행 | `POST .../simulate` | full match 200, in-progress 202 |
| committed replay | `POST .../games/{n}/replay` | deterministic full timeline |
| child/Series 취소 | `DELETE .../draft-session`, `DELETE /api/v1/series/{seriesId}` | 204 empty |

`sessionStorage`에는 `lolmanager.activeSeries.v1` pointer만 저장한다. Reload는 pointer의 Series를 GET하고 server view로 화면을 다시 만든다. Local score, transcript, seed 또는 winner는 저장하지 않는다. 더 오래된 revision response는 무시한다. 일시 오류는 pointer를 삭제하지 않아 다시 복구할 수 있다.

## 컴포넌트 구조

- `SeriesSetupPage`: arbitrary LIVE team과 BO3/BO5 입력, 관리 팀 의미와 root seed validation
- `SeriesScoreboard`, `SeriesGameRail`, `SeriesContextBar`: score/current game/side/fearless 문맥
- `SeriesHardFearless`: committed pick 누적과 현재 제외 수 표시
- `SeriesHubPage`: `allowedCommands` 기반 create/resume/simulate/replay/next/final action
- `SeriesDraftRoomPage`, `SeriesDraftReviewPage`: Series transport를 주입한 공통 Player Draft workspace
- `SeriesCancelDialog`: non-destructive initial focus, focus trap, pending/Escape 처리
- `seriesApi.client/validation`, `series.adapter`, `series.pointer`: HTTP, strict wire contract, view model과 resume pointer 분리

AUTO는 계속 `/api/v1/real-matches/simulate`, standalone 직접 밴픽은 계속 `/api/v1/player-drafts/sessions`를 사용한다. Series 화면은 이 두 흐름의 endpoint나 상태를 변경하지 않는다.

## 검증 결과

정적·결정적 검증은 다음 명령으로 통과했다.

```text
npm run series:verify
npm run player-draft:verify
npm run build
npm run reference:check
npm run reference:verify
npm run bundle:verify
```

`series:verify`는 arbitrary DK/HLE의 BO3/BO5, Game 2+ binding, cumulative Hard Fearless, 200/202/replay/completion, malformed response 거부, stale ordering과 pointer 복구를 확인한다. Player Draft contract 33개 시나리오, reference check/verify와 lazy bundle 경계도 그대로 통과했다.

실제 backend와 브라우저에서는 다음을 확인했다.

- BFX–BRO BO3, 관리 BFX/Game 1 BLUE/root 73: 세 game의 직접 밴픽과 Production V9 실행, score 2–1, Hard Fearless 0→10→20, 최종 BFX 승리
- DK–HLE BO5, 관리 DK/Game 1 RED: Game 1 commit, Game 2 BLUE child 생성과 이전 pick 비활성 사유, child 204 취소와 Series 204 취소
- standalone 직접 밴픽: session/action 10/simulate가 기존 endpoint만 사용하고 Playback까지 진행
- AUTO: `/api/v1/real-matches/simulate`만 사용하고 Series/Player Draft endpoint를 호출하지 않음
- reload resume: 같은 Series ID와 authoritative revision/status/score를 GET으로 복구
- 1440×900과 1280×720: horizontal overflow 0, score/context/Draft/취소 dialog 주요 레이아웃 확인
- 실제 simulation 응답의 gzip 전송과 console error 0 확인

후속 `SERIES_LIVE_E2E_AND_ACCESSIBILITY`에서 실제 backend reservation 중 동일 command replay로 HTTP 202를 만들고, browser GET polling과 committed replay를 확인했다. 실제 cancel/simulate response-loss 뒤에도 authoritative GET과 replay만 수행하며 새 논리 command를 자동 생성하지 않도록 보강했다. 점수/결과 strict validation, pointer not-found/expired 분기, modal pending focus와 live region도 함께 승인했다. 상세 증거는 [Series LIVE E2E and Accessibility](series-live-e2e-and-accessibility.md)에 있다.

이번 작업은 frontend source, scripts와 문서만 대상으로 했다. Backend production/test 변경은 하지 않았고, 따라서 이번 작업을 위한 backend full regression은 실행하지 않았다. 저장소에 이미 존재하던 다른 backend 변경도 보존했다.

## 남은 제한

- Backend process restart recovery, DB/save-load, auth/ownership과 multi-node coordination은 없다.
- Background progress/WebSocket은 없으며 202는 bounded GET polling으로 재조정한다.
- Long-running Series의 command receipt 256 no-eviction 제한은 backend 의미를 그대로 따른다.
- 실제 screen reader별 음성 품질, 운영 TTL 120분을 기다리는 wall-clock EXPIRED와 process restart/persistence/auth는 후속 수동·backend 범위다.
