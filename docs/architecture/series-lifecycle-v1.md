# Series Lifecycle V1

상태: `SERIES_FRONTEND_V1_ACCEPTED`

이 문서는 [계약 스케치](series-lifecycle-v1-contract-sketch.md)에서 제안했던 Series backend 중 현재 실제 구현된 V1을 설명한다. 구현은 additive `/api/v1/series` 경계이며 기존 standalone Player Draft와 Real Match API의 의미를 바꾸지 않는다.

## 제품 의미

Backend가 한 BO3/BO5의 참가 팀, 점수, game 순서, side, seed, 누적 Hard Fearless history, child Draft, simulation reservation과 result commit을 하나의 authoritative aggregate로 소유한다. Frontend는 LIVE 팀 중 관리 팀과 상대, BO3/BO5, Game 1 side와 signed-long root seed를 받아 이 aggregate를 만들고, server가 내려 준 score/current game/side/history/`allowedCommands`를 그대로 표시한다. Client가 winner, score, game seed, history, production profile 또는 completed result를 제출할 수 없다.

한 game의 정상 흐름은 다음과 같다.

```text
Series create
  -> frozen game(side/seed/history)
  -> parent-bound Player Draft 20 turns
  -> short atomic reservation
  -> lock 밖 Production V9 execution
  -> reservation/input/output compare
  -> game + score + 10 picks atomic commit
  -> next frozen game 또는 Series completion
```

## Aggregate와 repository

`SeriesAggregate`는 immutable snapshot이고 `SeriesRepository`만 process-local map을 소유한다. Mutation은 `ConcurrentHashMap.compute`의 Series ID별 경계에서 snapshot을 교체하므로 서로 다른 Series 실행을 global lock으로 직렬화하지 않는다. Global synchronization은 create cleanup, command index와 정확한 capacity 32를 함께 관리할 때만 사용한다.

기본 운영 한계는 `SeriesLifecycleConfiguration` 한 곳에 있다.

| 항목 | V1 값 |
| --- | ---: |
| retained Series | 32 |
| parent sliding TTL | 120분 |
| child idle TTL | 30분, parent expiry 이내 |
| simulation lease | 5분 |
| command receipts | Series당 256 |

성공한 mutation만 parent activity를 연장한다. GET, stale/rejected request와 exact command replay는 keepalive가 아니다. Cleanup은 각 Series key의 현재 snapshot을 `computeIfPresent` 안에서 다시 expiry 판정하고, 그 같은 원자 경계에서 terminal snapshot만 제거한다. 오래된 snapshot의 conditional replace 실패 뒤 새 snapshot을 지우는 경로는 없다. Create command index도 대응 Series가 실제로 제거되는 그 경계에서만 제거된다. Cancelled entry와 expired entry/create index는 다음 create에서 함께 정리한다. 유효한 simulation lease는 parent TTL 제거보다 우선한다. 완료·차단 Series도 parent TTL 뒤 EXPIRED가 되어 정리될 수 있다. Repository instance 간 상태 공유는 없다.

이 저장소는 database가 아니다. Process restart 시 Series, child, reservation과 receipts가 모두 사라지고 multi-node coordination이나 restart recovery를 제공하지 않는다. 이 제한은 API view의 `processLocalRestartLoss=true`로도 노출한다.

## BO3/BO5, side와 seed

- BO3는 2선승/최대 3게임, BO5는 3선승/최대 5게임이다.
- 점수는 BLUE/RED가 아니라 `{teamCode -> wins}`다.
- Game 1 BLUE는 create request의 참가 team code로 고정하고 이후 game은 BLUE/RED를 단순 교대한다.
- `managedTeamCode`는 Series 전체에서 고정하며 `controlledSide`는 각 game의 frozen mapping에서 server가 계산한다.
- Root seed는 canonical signed-long decimal string만 받는다. `+73`, `073`, `-0`, 공백, JSON number와 범위 초과를 거부한다.
- Series/game/child ID는 versioned canonical SHA-256으로 결정적으로 파생한다. UUID와 wall clock은 gameplay identity에 들어가지 않는다.
- Game seed는 `SERIES_GAME_SEED_SHA256_FIRST_8_BYTES_BIG_ENDIAN_SIGNED_LONG_V1`으로 파생한다. 입력에는 Series ID, root seed, game number, side/team mapping, managed team과 history-before hash가 포함된다.

## Standalone과 League-bound origin

`SeriesAggregate`는 `STANDALONE | LEAGUE_BOUND` origin을 구조적으로 구분한다. Public `POST /api/v1/series`는 계속 caller가 고른 standalone 설정만 받고 League binding, fixture, origin을 주입할 필드가 없다. 기존 standalone ID와 `managedTeamCode` salt를 포함한 game-seed schema도 그대로다.

League-bound Series는 별도 내부 `LeaguePlayerSeriesKernelPort`만 생성한다. Server-created `LeagueFixtureSeriesBindingV1`의 bound Series ID, 두 팀, managed team, BO3, Game 1 side, fixture root와 empty Hard Fearless history를 그대로 사용한다. Fixture root는 Series root로 한 번만 전달하고 Game 1부터 `AI_LEAGUE_BOUND_SERIES_GAME_SEED_SHA256_FIRST_8_BYTES_BIG_ENDIAN_SIGNED_LONG_V1`과 canonical pair-first anchor로 seed를 파생한다. 따라서 standalone salt나 execution mode가 League game seed를 바꾸거나 root가 이중 파생되지 않는다.

League-bound child Draft와 simulation은 이 문서의 기존 20-turn mixed-authority Draft, joint pool preflight, reservation, Production V9 commit과 compact receipt 경로를 재사용한다. Child Draft/Series cancel이나 retryable execution failure는 standalone cancel로 해석하지 않고 score/history를 보존한 채 `PLAYER_SERIES_RESTART_REQUIRED`로 차단한다. Response의 `allowedCommands`에서도 League-bound `CANCEL_SERIES`를 Season cancel처럼 광고하지 않는다. Completed evidence read는 stored game마다 current Production V9을 결정 재생해 `SeriesGameReceipt`를 exact 비교하고 aggregate mutation 0을 확인한 뒤에만 League verifier로 전달한다.

이 구분은 persistence를 추가하지 않는다. 기존 `SeriesRepository`와 현재 League binding adapter는 모두 process-local이고, restart 뒤 League fixture를 복구하는 권위는 후속 persistence/jobs adapter가 소유한다. 자세한 handoff 경계는 [AI League V1 Player Series Handoff](../development/ai-vs-ai-league-simulation-v1-player-series-handoff.md)에 있다.

## Series-owned Player Draft와 Hard Fearless

Series child는 standalone session map을 재사용하지 않고 parent aggregate 안에 보관된다. Binding은 Series/game ID, actual game number, BLUE/RED team code, controlled side, derived seed, sorted prior exclusions와 history hash를 포함한다. Parent revision과 child revision을 모두 검사한다.

`PlayerControlledDraftEngine.startSeries`와 series-aware completion validator는 Game 2 이상과 기존 committed history를 명시적으로 받는다. AI turn은 기존 Production Auto Draft search/selector를 그대로 사용하고, player turn은 server가 제공한 legal selectable set만 허용한다. 완료 시 active Draft Meta version과 required/actual legal-role hash, 20-turn authority/evidence, final assignments를 다시 검증한다.

Decisive game commit 때 양 팀 picks 10개만 누적한다. Bans, 실패, 취소, expiry, stale command와 no-result는 history를 바꾸지 않는다. 다음 game은 직전 committed picks union을 frozen exclusion으로 받는다. Pool completion preflight는 양 팀을 독립적으로 재사용 가능한 풀로 보지 않고, shared unavailable pool에서 BLUE/RED의 열 개 role slot에 서로 다른 챔피언을 배정할 수 있는지 bounded matching으로 확인한다. 실패하면 직전 commit은 보존하고 Series/next game을 `BLOCKED/HARD_FEARLESS_LEGAL_POOL_EXHAUSTED`로 둔다.

Standalone `PlayerControlledDraftEngine.start`/`validateCompleted`와 `/api/v1/player-drafts/sessions`는 계속 Game 1 + 빈 history만 허용한다. `/api/v1/real-matches`도 독립 Game 1 계약을 유지한다.

## Production V9 reservation과 commit

Simulation eligibility가 모두 통과하면 짧은 per-Series mutation에서 command/payload, frozen child/input binding, reserved revision, token과 5분 lease, `IN_PROGRESS` command receipt를 함께 기록한다. 그 뒤 repository lock 없이 실제 `PRODUCTION_MATCHUP_COMPOSITION_V1` / `MATCH_SIMULATOR_ENGINE_IMPLEMENTATION_V9`을 실행한다. 같은 command/payload 재전송은 새 worker를 만들지 않고 기존 202 의미를 반환한다.

Commit은 현재 Series status/revision, exact game snapshot, game/child/generation, reservation token/command/payload/lease, child revision/Draft identity와 frozen side/seed/history/input binding을 다시 비교한다. Production executor는 authoritative roster, Draft/control/final assignment, input, policy/profile/configuration/rules/engine, resource/replay provenance, simulator/structured timeline, Random fingerprint와 output hash를 검증한다. 모두 맞는 decisive result만 한 snapshot에서 game `COMMITTED`, team-code score +1, picks 10개 누적, 다음 game 생성 또는 Series `COMPLETED`를 함께 반영한다.

Runtime failure는 reservation을 해제하고 `SIMULATION_FAILED_RETRYABLE`, integrity mismatch는 `BLOCKED`, valid timeout/no winner는 `BLOCKED/NO_DECISIVE_RESULT`다. Lease 만료는 `SERIES_SIMULATION_LEASE_EXPIRED` retryable failure receipt로 고정한다. 이 경로들은 score/history를 commit하지 않으며 최초 stable code/HTTP/retryable 의미를 같은 command 재전송에서 재현한다. Failed receipt의 historical resulting revision/status도 내부 evidence로 그대로 보존한다. 다만 public error의 `currentRevision/currentStatus`는 exact replay를 처리하는 시점의 현재 aggregate에서 가져오므로 이후 retry 성공, 다음 game, completion 또는 cancel을 과거 상태로 되돌려 표시하지 않는다. 실제 retry는 새 command ID와 최신 revision을 사용한다. Series cancel은 active child를 CANCELLED로 바꾸고 reservation을 제거하며 진행 중 receipt도 invalidated failure로 바꾸므로 late success와 late failure 모두 더 새 상태를 덮어쓸 수 없다.

## Compact result와 replay

Aggregate에는 completed 20-turn Draft/evidence, final assignments, compact result summary, `SeriesGameReceipt`와 bounded command receipt만 남긴다. Command receipt는 `IN_PROGRESS/SUCCEEDED/FAILED`, stable result/error, exact game ID와 필요한 child ID/generation/revision의 bounded snapshot을 보관한다. 이 때문에 과거 Game 1 또는 취소 전 generation command를 재전송해도 현재 game/child와 섞지 않는다. `MatchEngineV1Output`, event/snapshot timeline, HTTP DTO graph, simulator state, future/thread/exception은 보관하지 않는다. Canonical game receipt는 16KiB 이하로 검증한다.

Committed game replay는 stored frozen Draft/context로 Production V9을 새로 결정 실행하고 stored receipt를 exact 비교한 경우에만 full match response를 반환한다. 사전 검사는 policy ID/hash, runtime profile, configuration, rules와 engine을 포함한다. Replay 전후 authoritative aggregate 전체 구조를 비교하므로 revision-neutral child expiry, lease expiry, reservation/receipt/status/terminal reason 변화도 stale view로 반환하지 않고 fail-closed한다. Current runtime/resource가 stored identity를 재현할 수 없으면 `SERIES_GAME_REPLAY_IDENTITY_UNAVAILABLE`, 실행 결과 receipt가 다르면 `SERIES_GAME_RECEIPT_MISMATCH`로 구분한다.

동일한 completed simulate command 재전송은 engine을 다시 실행하지 않고 authoritative compact terminal Series/Game view를 반환한다. Full timeline이 필요한 명시적 replay endpoint와 command idempotency를 분리한 결과이며, duplicate simulation response의 `match`는 null일 수 있다.

## REST API

Base path는 `/api/v1/series`다.

| Method/path | 의미 |
| --- | --- |
| `POST /api/v1/series` | create 201, exact create replay 200 |
| `GET /api/v1/series/{seriesId}` | authoritative current view |
| `POST .../games/current/draft-session` | child create 201, exact replay 200 |
| `GET .../games/{gameNumber}/draft-session` | parent binding 포함 child view |
| `POST .../draft-session/actions` | exact parent/child revision의 player action |
| `DELETE .../draft-session` | child cancel 204 empty |
| `POST .../simulate` | committed 200, same in-progress 202 |
| `GET .../games/{gameNumber}` | compact game/result/receipt view |
| `POST .../games/{gameNumber}/replay` | no-commit deterministic full replay |
| `DELETE /api/v1/series/{seriesId}` | Series cancel 204 empty |

Request parser는 endpoint별 exact schema/field set, enum, ID, canonical seed와 revision을 검사한다. Unknown field로 score/winner/history/profile/game seed/output을 주입하면 거부한다. Error는 `SERIES_API_ERROR_V1`의 stable code, field, retryable, current revision/status로 반환하며 내부 stack/path/raw payload는 노출하지 않는다. `allowedCommands`도 같은 `SeriesLifecycleConfiguration`의 receipt capacity를 사용한다. 256개 한도에서는 일반적인 신규 mutation을 제거하고 `GET`만 광고하지만, 이미 존재하는 exact command ID replay는 새 receipt를 만들지 않으므로 계속 처리한다.

## Frontend 소비 경계

Series frontend는 기존 AUTO와 standalone Player Draft를 대체하지 않는다. 설정 화면에서 Series를 명시적으로 선택했을 때만 `/api/v1/series`를 사용한다. 각 game의 직접 밴픽은 동일한 Player Draft workspace를 재사용하지만 요청은 Series-owned child endpoint로만 보내며, standalone `/api/v1/player-drafts/sessions`와 섞지 않는다. Draft 완료 뒤 simulation의 200은 full match를 즉시 재생하고, 202는 visibility-aware bounded polling으로 authoritative Series를 다시 조회한다. Compact commit만 복구된 경우에는 해당 game replay endpoint를 명시적으로 호출해 full timeline을 얻는다.

브라우저에는 active Series ID 하나만 `sessionStorage`에 보관한다. Reload 시 GET으로 score, current game, side, history, child state와 capabilities를 다시 구성하며, score나 Draft transcript를 로컬에서 복원하지 않는다. 응답 revision이 현재보다 오래되면 화면 상태에 반영하지 않는다. UI 구조와 실제 검증 결과는 [Series Frontend V1](../development/series-frontend-v1.md)에 기록한다.

## 현재 제한과 다음 단계

V1은 process-local single-node backend다. Persistence/save-load, authentication/ownership, multi-node lease/commit과 background job recovery는 포함하지 않는다. Process restart 뒤 browser pointer만 남아 있어도 Series 자체는 복구되지 않는다. Command receipt 한도 256은 eviction하지 않는다. 이미 기록된 exact replay는 한도에서도 허용하지만 신규 action/simulate/cancel은 실행·mutation 전에 fail-closed하고 `allowedCommands`에도 나타나지 않으므로 장시간 열린 Series는 terminal/cancel command도 거부될 수 있다. V2에서는 persistence와 함께 receipt compaction/retention 정책이 필요하다. Full replay는 현재 production/resource identity를 그대로 재현할 수 있을 때만 가능하다. Frontend-readiness 보강과 검증은 [Series Lifecycle V1 Frontend Readiness Hardening](../development/series-lifecycle-v1-frontend-readiness-hardening.md)에 기록한다.

League-bound Player Series handoff는 이 process-local repository를 Season authority로 승격하지 않고 별도의 canonical binding/completion port에서 기존 Draft/Series 규칙을 재사용하도록 구현됐다. 현재 exactly-once와 resume은 process-local application 의미이며 DB transaction, outbox delivery와 restart recovery는 아직 없다. 전체 방향은 [AI vs AI League Simulation V1 Hybrid Season Contract](ai-vs-ai-league-simulation-v1-contract-sketch.md)에 있다.

League 구현의 다음 순서는 다음과 같다.

```text
AI_VS_AI_LEAGUE_SIMULATION_V1_PERSISTENCE_AND_JOBS
```
