# Project Status

이 문서는 2026-09-02 working tree의 production source, active resources, 최종 backend regression과 직접 생성한 structured evidence를 기준으로 한 현재 snapshot이다. 과거 build output이나 현재 HEAD보다 앞선 report는 baseline으로 간주하지 않는다.

## AI vs AI League Simulation V1 frontend

상태는 `AI_VS_AI_LEAGUE_FRONTEND_RECOVERY_AND_SOURCE_HYGIENE_HARDENED_READY_FOR_LONG_RUNNING_E2E`다.

기존 AppShell의 competition 진입점에 실제 League workspace를 연결했다. Hybrid/Spectator 생성,
authoritative 10 teams, 18 rounds/90 BO3 fixtures, standings/current Round, job 상태, 일정 filter와 fixture
inspector를 제공한다. Hybrid 관리 경기는 server-issued bound Series ID로 기존 Player Series/Draft/
Production V9 화면에 들어가며 League/round/matchup 문맥을 reload 뒤에도 복원한다. Winner, score,
standings delta, seed/history/receipt는 frontend가 계산하거나 제출하지 않는다.

Frontend recovery hardening은 기존 one-shot fixture ref를 제거하고 `IDLE`, candidate, reconcile,
poll, retry-wait, applied/terminal/aborted 상태를 가진 단일 operation owner를 연결했다. TIMEOUT,
network, retryable 503와 response loss는 League/Season/fixture/binding/revision에 결속한 같은 completion
UUID를 보존하고 authoritative GET을 먼저 확인한다. 자동/수동 trigger는 같은 in-flight 작업을
공유하며 pending 해제, 다음 fixture와 remount를 재평가한다. 서버가 이미 적용해 reconcile command가
사라진 fixture도 저장된 fixture/binding identity로 POST 없이 정리한다. 성공 표시는
`standingsApplied=true`와 authoritative Season/fixture refresh 뒤에만 이루어진다.

Phase A는 durable run command 최초/exact replay의 worker pump wake-up, submit false retryable 503과
authoritative child Series 상태별 resume/reconcile command projection만 보강했다. Background enabled
integration은 public 202 뒤 Round 1의 다섯 job이 attempt 1 `COMPLETED`, standings revision 5가 되는
것을 확인했다. 기존 startup no-auto-gameplay, fencing/exactly-once와 frozen product/Production V9/
gameplay Random 의미는 유지했다.

League/Player Draft/Series/reference/bundle frontend 계약, completion recovery 14 scenarios와 133-module
production build가 clean pass했다. Controlled browser에서는 503 same UUID retry, commit 뒤 response
loss의 추가 POST 0, applied command remount의 POST 0을 확인했고 성공 뒤 command/error 정리도 확인했다.
Isolated LIVE browser에서 Hybrid 10/18/90, 18 Player/72 Auto, 실제 Auto terminal/standings/reload,
managed GEN–HLE Series handoff/reload/return, 별도 Spectator pause/resume/cancel을 확인했다. 1440×900과
1280×720 page overflow 0, modal keyboard focus, reduced-motion과 clean console/page/runtime validation
error 0을 확인했다. 최종 backend full은 259 suites / 2,336 tests / failures 0 / errors 0 / skipped 2,
aggregate XML 1,122.363초, `BUILD SUCCESSFUL`, Gradle wall 18분 47초다. 상세 내용은
[AI League Frontend V1](development/ai-vs-ai-league-simulation-v1-frontend.md)에 있다.

Historical tracked build state였던 `frontend/dist/index.html`과
`frontend/tsconfig.app.tsbuildinfo`는 기존 ignore 규칙을 유지하고 deletion diff로 source tracking에서
제거한다. Backend production/API/gameplay/Random은 변경하지 않았으며 이번 browser 검증은 90경기
전체 Season이나 Player BO3 장시간 LIVE 완주가 아니다. 다음 단계는
`AI_VS_AI_LEAGUE_PLAYER_BO3_TO_NEXT_ROUND_LONG_RUNNING_LIVE_E2E`다.

## AI vs AI League Simulation V1 API and job-boundary hardening

상태는 `AI_VS_AI_LEAGUE_SIMULATION_V1_API_ACCEPTED`다.

Batch 5는 additive `/api/v1/leagues`에서 Hybrid/Spectator Season 생성, 90-fixture/standings 조회,
current-round Auto dispatch와 job polling, pause/resume/cancel, 관리 팀 Player Series start/resume,
server-side completion reconciliation을 제공한다. Mutation은 strict versioned schema, durable
`clientCommandId`와 lifecycle revision을 사용한다. 같은 command/payload replay는 mutation 0이고
다른 payload는 409다. Winner, score, seed, side, profile, policy, receipt와 lease identity는 요청자가
주입할 수 없다.

API 공개 전에 이전 process incarnation의 unexpired lease startup recovery, typed failure 분류,
DB singleton lock 기반 global hard max 4와 원자적 Season cancel을 보강했다. `run-current-round`는
durable dispatch 뒤 202를 반환하며 bounded queue/default workers 2로 실행되고, startup은 gameplay를
자동 실행하지 않는다. Player completion은 completed server Series proof와 V2 receipt/outbox/ledger만
신뢰해 standings를 한 번만 반영한다.

Affected API/Phase A/기존 API 호환성은 16 suites / 62 tests를 2분 25초에 통과했고, 원자 fencing
완료 순서 보강 뒤 직접 영향 5 suites / 20 tests도 1분 22초에 재통과했다. 첫 full 뒤 수동 감사에서
explicit run의 runtime-expired lease recovery 연결 누락을 찾아 수정했고 3 suites / 12 tests가
47초에 통과했다. Final executable tree의 두 번째 complete regression은 256 suites / 2,333 tests /
failures 0 / errors 0 / skipped 2, aggregate XML 1,139.817초, Gradle wall 19분 13초로 clean pass했다. Frozen product hash,
Production V9/gameplay/Random과 기존 Series/Player Draft/Real Match API는 유지했다. 이 historical API
milestone 뒤의 frontend delivery 결과는 위 최신 section을 따른다. 상세 내용은
[AI League V1 API](development/ai-vs-ai-league-simulation-v1-api.md)에 있다.

## AI vs AI League Simulation V1 persistence and jobs

상태는 `AI_VS_AI_LEAGUE_SIMULATION_V1_PERSISTENCE_AND_JOBS_IMPLEMENTED_READY_FOR_API`다.

Batch 4는 frozen League domain/seed/receipt hash를 바꾸지 않고 local single-node H2/Flyway
relational authority를 추가했다. Season/round/fixture/standings, FULL_AUTO job/attempt/15분 lease/
15초 heartbeat/fencing, Player binding/command/League-bound Series checkpoint, V2 receipt/outbox와
standings application ledger가 process restart 뒤 복구된다. Default fixture parallelism 2,
hard max 4, transient 총 2회와 attempt log 30일은 기존 frozen operational configuration을
그대로 사용한다.

Player start/complete 경쟁은 atomic create-or-load와 completion claim으로 바뀌어 exact 또는
different-command/same-fixture 요청이 binding/Series/verification 하나를 공유하며 정상 경쟁을
`BLOCKED`로 오인하지 않는다. Standalone Series/Player Draft/Real Match API와 frontend는 변경하지
않았다. League API/controller/frontend, auth, external broker/multi-node, official 90경기 실행과
대형 balance/performance 진단은 후속 범위다. 상세 구현은
[AI League V1 Persistence and Jobs](development/ai-vs-ai-league-simulation-v1-persistence-and-jobs.md)에
있다.

최종 핵심 focused는 8 suites / 16 tests를 3분 3초에 통과했다. 첫 complete regression에서
test resource의 compression 설정 누락 1건과 unordered Draft capability 합산에 따른 fresh-JVM
raw trace/receipt 차이 2건을 발견해 원인을 수정했다. 최종 executable tree의 두 번째 complete
regression은 251 suites / 2,319 tests / failures 0 / errors 0 / skipped 2, aggregate XML
1,150.816초, Gradle wall 19분 16초로 clean pass했다. Frozen product-decision hash와 Production
V9 policy/profile/tuning, gameplay Random 소비는 유지했고 frontend source는 변경하지 않았다.

## Current Production Snapshot

### Matchup/Composition Production V9 acceptance

공개 Real Match V9의 authoritative runtime은 `PRODUCTION_MATCHUP_COMPOSITION_V1`이다. Matchup `GEOMETRIC_V2`와 Composition `PRODUCTION_V2`를 실제 simulation에 적용하고 Jungle Clear contribution은 `DISABLED_NOT_INTEGRATED`, Jungle Economy/Tempo candidate activation은 모두 `false`다. 공통 13 gameplay system은 전부 ON이며 active rules는 `MATCH_SIMULATOR_PRE_JUNGLE_RULES_V4`, engine은 `MATCH_SIMULATOR_ENGINE_IMPLEMENTATION_V9`다.

| 항목 | 현재 값 |
| --- | --- |
| Policy schema / ID | `MATCH_ENGINE_V1_PRODUCTION_POLICY_V3` / `MATCH_ENGINE_V1_MATCHUP_COMPOSITION_ACCEPTED_PRODUCTION_POLICY` |
| Policy SHA-256 | `3afaa399f7c2b20a940c7cfd7510f6c7962ba43eec575cc75ed55218d53f0ce9` |
| Runtime profile | `PRODUCTION_MATCHUP_COMPOSITION_V1` |
| Configuration SHA-256 | `caaf76274dc148040b0a95eae1ed5181790b2fc840f45af9b109ea7951c1fd5d` |
| Product decision | `PRODUCT_DECISION_ACCEPT_WITH_KNOWN_DIAGNOSTIC_LIMITATION` |
| Acceptance | `PRODUCT_ACCEPTED_WITH_KNOWN_LIMITATIONS_NOT_STATISTICAL_HOLDOUT` |
| Ordered risk 1 | `MATCHUP_CAUSAL_LINEAGE_UNRESOLVED_399_OF_400_CALIBRATION_PUBLIC_DIVERGENCES` |
| Ordered risk 2 | `COMPOSITION_NEXUS_ENDING_SENSITIVITY_9_25_PERCENT_EXCEEDS_PROPOSED_7_5_PERCENT_TOLERANCE` |
| Statistical holdout approved | `false` |
| Rollback | `BASELINE_V1` / `EXPLICIT_VERSIONED_POLICY_CHANGE_ONLY` / automatic fallback `false` |

이 전환은 fresh requalification이나 statistical holdout 통과 선언이 아니다. V2 calibration public divergence 400건 중 Matchup direct binding은 1건, unresolved lineage는 399건이었고 holdout은 승인·시작·소비되지 않았다. Composition direct causal binding은 기존 V6/V2 계약에서 확인됐다. 제품 소유자가 알려진 한계와 민감도를 수용하고 Draft의 Matchup/Composition 의미를 실제 gameplay에 연결한 결정이다.

Calibration 관측에서 Matchup−Baseline은 Blue WR +1.5%p, winner change 4.5%, structure progression 10.25%, Nexus/ending 5.5%, 평균 duration +1.1초였다. Full−Matchup은 각각 -1.0%p, 8.5%, 14.75%, 9.25%, +6.3초였다. Composition Nexus/ending 9.25%는 pair의 final signature 차이이지 넥서스 파괴 증가율이 아니며, 7.5%는 직전 proposed tolerance다. `objective detailed signature`는 내부 decision detail 차이를 포함하므로 실제 objective outcome 변화율로 해석하지 않는다.

`BASELINE_V1`은 configuration hash `c8cc557bd721228c473e30d31b7258510f9608a18098578bc1da36e603536215`인 explicit rollback/비교 profile로 보존한다. Runtime 오류 시 자동 fallback은 없고 rollback은 향후 명시적 versioned policy 변경으로만 수행한다. 현재 정책·검증·LIVE smoke는 [Match Engine V9 Production Acceptance](development/match-engine-v9-production-acceptance.md)에 있다.

T1/GEN fixed-Draft acceptance sanity는 POKE/ENGAGE/COUNTER 세 lineup, 네 team/side orientation, 세 profile, 50 seeds의 1,200 core simulation과 replay/instrumentation/rollback 추가 10회를 실행했다. 30 authored proficiency binding은 모두 정확하고 neutral fallback은 0이다. Production 400/400 pair에서 Composition이 도달해 scalar 4,885회와 전체 structured effect 5,339회를 기록했다. Timeout/domain/structure/Nexus/Random/Jungle/replay/instrumentation/rollback 오류는 모두 0이다. 이 값은 product sanity이지 calibration, holdout, balance 승인이나 현실 LCK 승률이 아니다. 상세 결과는 [Match Engine V9 Production Acceptance](development/match-engine-v9-production-acceptance.md)에 있다.

### Champion resources

| 항목 | 현재 값 |
| --- | --- |
| Active manifest | `full-173-resource-set-2026-08-v2` |
| Active manifest raw SHA-256 | `7c2ba352e4fd5cb2e849504ebf644479aa1b367e250b46ceac5f089922169721` |
| Champion pool | `full-173-2026-08-v1` |
| Champions | 173 |
| Legal `ChampionRoleKey` | 216 |
| Role counts | TOP 54 / JUNGLE 51 / MID 45 / ADC 31 / SUPPORT 35 |
| Flex champions | 36 |
| Power | 173 profiles, `full-173-power-2026-08-v1` |
| Matchup | 216 materialized roles, `full-173-role-matchup-profile-2026-08-v2` |
| Composition | 216 materialized roles, `full-173-composition-profile-2026-08-v2` |
| Composition full-profile hash | `23d616cab6abea69d5ad783f405b0b4518a14608b0be4eac3d53f669acab6877` |
| Jungle Clear | 51 JUNGLE profiles, `full-173-jungle-clear-economy-2026-08-v1` |
| Jungle Clear gameplay enabled | 51 in active resource; runtime contribution is profile-controlled |
| Jungle Clear raw SHA-256 | `daf3757c6598056c9d01c3fcb3899e2b6dd970ffbda94f7b755f9d40cd2e967b` |

Champion Power resource에는 8 level curves와 8 item curves가 있다. Cross-catalog completeness는 `ChampionResourceSet` 생성 시 exact set equality로 검증된다. Jungle V1-A는 historical disabled clear file을 변경하지 않고 새 versioned economy resource와 manifest V2를 추가했다. V1-B는 이 authored data를 다시 수정하지 않고 V1-A의 successful `JungleEconomyOutcome`만 bounded gank-tempo credit input으로 사용한다.

### Player identity, ratings, and proficiency

| 항목 | 현재 값 |
| --- | --- |
| Player Identity version | `lck-player-identities-2026-08-21-v1` |
| Player Identity snapshotAt | `2026-08-21` |
| Player Identity SHA-256 | `badbbaa3ae7fbe5eaaf83ee8e97a93134476493a45167ec3d1637c7243909018` |
| Stable identities | 50 unique `PlayerId`, 50 unique `PlayerRatingKey` |
| Player Ratings version | `lck-player-ratings-2026-08-19-v1` |
| Player Ratings SHA-256 | `2312a8bc7d222fd63b57d1255210fb25104432a90a954d854b2090cc2acb28e0` |
| Authored rating values | 50 players × 12 = 600, unchanged |
| Proficiency version | `lck-champion-proficiency-2026-08-21-v1` |
| Proficiency SHA-256 | `2c36b8a109aba9dfe84c1da319fe02708a72a1341d334dc6d5e3f605b0023aad` |
| Authored proficiency overrides | 732 sparse entries |
| Potential / fallback keys | 2,160 / 1,428 |
| Score distribution | 15=35 / 16=160 / 17=228 / 18=210 / 19=81 / 20=18 |
| High / elite / benchmark | 17+=537 / 19+=99 / 20=18 |
| Scope-inexpressible review evidence | 11, legal-role expansion 0 |

`PlayerId`는 사람, `PlayerRatingKey(teamCode, Position)`는 current roster snapshot, `PlayerKey(TeamSide, Position)`는 match slot identity다. nickname은 display metadata이며 gameplay lookup key가 아니다.

`LckTeamAssembler`는 같은 identity instance를 사용하는 ratings와, 같은 ratings/champion instances를 사용하는 proficiency catalog를 하나의 immutable graph로 결합해 10개 팀 × 정확히 5명의 real LCK roster를 deterministic하게 조립한다. 각 player는 explicit stable ID, 실제 12 ratings, 실제 sparse proficiency profile을 가진다. 작성되지 않은 legal same-position key만 neutral 14이며 unknown/illegal/cross-position subject는 fail-fast한다.

Identity/proficiency companion JSON은 exact SHA의 local runnable working tree에는 존재하지만 의도적으로 untracked다. 기준 commit만으로는 이 runtime data가 포함되지 않으며, 이 상태를 authored resource 결함으로 분류하지 않는다.

### Draft and reachability

| 항목 | 현재 값 |
| --- | --- |
| Draft Meta | `draft-meta-full-173-216-role-2026-08-18-v3` |
| Draft Meta role profiles | 216 |
| Rule sequence | professional 5 bans + 5 picks per team, 20 turns |
| Series rule | completed picks를 양 팀 공통 Hard Fearless exclusion으로 누적 |
| Selection policy | `AUTO_DRAFT_VARIETY_V1` / `SEEDED_BOUNDED_RANK_WEIGHTED_V1` |
| Selection policy SHA-256 | `b4645a9897329b6b0d50405a22ef788885a40ecede4b0fedd04e168211cf75cc` |
| Determinism | match seed + canonical context SHA-256; simulator Random 소비 없음 |
| API/frontend exposure | additive policy/trace/integrity fields와 LIVE strict validation |

보정된 `RealProficiencyCandidateReachabilityGate`를 real `DraftTeamContext`와 실제 17+ proficiency 537개에 key당 3개의 bounded scenario로 실행했다. ChampionId presence와 target-position-fixed `ChampionRoleKey` completion을 별도 metric으로 기록한다.

| Reachability metric | 값 |
| --- | ---: |
| Bounded scenarios | 1,611 |
| Champion-level legal scenarios | 1,349 |
| Role-specific legal scenarios | 1,319 |
| Champion appearances | 261 |
| Champion-present high keys | 150 / 537 (0.2793296089) |
| Role-key reachable scenarios | 260 |
| Role-key reachable high keys | 150 / 537 (0.2793296089) |
| Champion present / target role infeasible | 1 (`player-kiin/varus:TOP`) |
| Champion present / role completion impossible | 0 |
| Role-key unreachable high keys | 387 |
| No role-specific legal-scenario keys | 0 |

387건은 blocker가 아니라 `REVIEW_REAL_PROFICIENCY_ROLE_KEY_UNREACHABLE` review signal이다. Key count는 이 schedule에서 양쪽 모두 150이지만 scenario-level champion presence 261 중 1건은 target role이 불가능해 role-key reachability가 260이다. 이 Gate는 bounded feasibility를 증명할 뿐 proficiency가 shortlist inclusion의 원인임을 증명하지 않는다. Draft weight, candidate generator, shortlist size, search bound, Draft Meta와 proficiency 값은 변경하지 않았다.

전체 population은 기본 `test`에서 제외되며 `phase13gRealProficiencyAudit` 전용 task에서만 실행한다. 이번 명시적 실행은 1 suite / 1 test, failures/errors/skipped 0, JUnit 7.444초, Gradle 약 11초였다.

Generated report:

- `backend/build/reports/phase13g-real-proficiency-reachability/phase13g-real-proficiency-reachability-summary.json`
- `backend/build/reports/phase13g-real-proficiency-reachability/phase13g-real-proficiency-reachability-keys.csv`
- `backend/build/reports/phase13g-real-proficiency-reachability/phase13g-real-proficiency-reachability-SHA256SUMS.txt`

Report SHA-256: summary `4f3a13ca19cb00abc8da463173578b222fa55e389bb34d1a96abe6f2b10bfde3`, key CSV `936346bc52dfbfab430634d1fcb44781bb765c82f3558f60565e1c36541b1f61`.

### Player-controlled Draft API V1

상태는 `PLAYER_CONTROLLED_DRAFT_API_V1_FINAL_BOUNDARY_HARDENED`다.

Backend는 한쪽 `TeamSide`의 10개 ban/pick turn을 플레이어가 직접 선택하고 반대쪽은 기존 `AUTO_DRAFT_VARIETY_V1`이 현재 상태에 반응하는 Game 1 session API를 제공한다. 제어 정책은 `PLAYER_CONTROLLED_DRAFT_V1`, SHA-256은 `8f6488f07c44a6529e88bd022fff3124458a8237cc919bd7dd3e140eaa4a0752`다. AI selection/search/scoring/tuning과 기존 Real Match `/options`·`/simulate` 계약은 변경하지 않았다.

Manual choice는 advisory top 3에 제한되지 않고 current availability, partial role feasibility와 future completion을 통과한 전체 champion pool을 사용한다. Flex role은 pick 순간 고정하지 않고 20턴 완료 후 기존 resolver가 확정한다. 모든 turn은 `AI`/`PLAYER` authority와 state-before/after identity를 남기며 AI는 원래 Auto trace, PLAYER는 selectable-set/legality/client-action evidence를 사용한다. Session/request/revision/time identity는 gameplay hash에서 제외한다.

새 endpoint는 `/api/v1/player-drafts/sessions` 아래 create/get/action/simulate/delete 다섯 개다. Process-local repository는 injected Clock, 기본 TTL 30분과 최대 128 sessions, revision/client-action idempotency와 atomic AI follow-up을 제공한다. Repository-owned create lock이 cleanup/count/collision/register를 묶어 concurrent create에서도 exact capacity를 보장한다. Expired/cancelled entry의 물리 제거는 다음 create에서 수행하는 lazy eviction이며 서로 다른 repository instance는 state를 공유하지 않는다.

Draft 완료는 경기를 자동 실행하지 않으며 explicit simulate만 현재 `PRODUCTION_MATCHUP_COMPOSITION_V1` / engine V9를 fresh state에서 실행한다. Session은 더 이상 `MatchEngineV1Output`, timeline/event/snapshot 또는 full match DTO를 보관하지 않고 16KiB 이하 canonical 계약의 compact simulation receipt만 보관한다. 반복 simulate는 immutable completed Draft에서 Match Engine을 결정적으로 다시 실행해 stored receipt와 input/replay/resource/Draft/control/timeline/Random/output/result identity를 exact 비교한다. Mismatch와 실행 실패는 stable receipt/status/revision을 덮어쓰지 않는다. 이는 retry CPU를 다시 지불하는 대신 session별 대형 result object graph 누적을 제거하는 선택이다.

Mixed transcript에서 Match Engine input까지는 public `PlayerControlledDraftMatchInputBoundary.validateAndCreateInput` 하나가 소유한다. 이 public 경계는 caller가 만든 `Team`이나 display name을 받지 않고 BLUE/RED team code, seed와 completed result만 받아 `LckTeamAssembler`의 authoritative five-position stable-`PlayerId` roster를 내부에서 조립한다. Full manual selectable-set, authority/action, authoritative AI search trace, state hashes, final role/player assignment와 roster/team/seed/Game 1 context를 재구성하고, completed result의 Draft Meta version 및 required/actual legal-role hash를 현재 active `DraftResourceSet.meta()`와 exact 비교해야만 private-constructor validated token이 발급된다. 이 불일치는 Match Engine input 및 seeded gameplay Random 전에 차단된다. Unchecked mixed projector는 token만 받는 package-private method이며 public raw factory는 없다. 기존 full-auto `fromRealDraft` semantics는 변경하지 않았다.

현재 제한은 frontend 미구현, restart persistence/auth/database/background result storage/WebSocket/multi-node coordination 부재, Game 1/빈 Hard Fearless only다. 상세 계약은 [Player-controlled Draft API V1](architecture/player-controlled-draft-api-v1.md)에 있다.

Final boundary focused는 Player Draft engine/boundary/session simulation/API와 Match Engine contract 5 suites / 36 tests / failures 0 / errors 0 / skipped 0으로 통과했다. 최종 executable production tree의 새 default backend regression은 첫 실행에서 226 suites / 2,232 tests / failures 0 / errors 0 / skipped 0, aggregate XML 1,034.299초, Gradle wall 17분 29초로 통과했다. 기존 session memory/capacity/receipt/retry, API schema, full-auto Real Match, Production V9/profile/gameplay semantics는 full regression으로 보존했고 frontend, 대형 diagnostic, calibration/holdout과 historical artifact 재생성은 실행하지 않았다.

직전 session hardening에서 실행한 별도 `localhost:8098` bootRun smoke는 RED-controlled session 생성, turn 1 AI→turn 2 PLAYER 시작, player action 10회와 20 decisions 완료, first/repeat Production V9 simulate를 확인했다. 두 응답의 final Draft/assignment, input/replay, simulator/structured timeline, Random draw/hash, output와 winner/duration identity는 exact였고 output hash는 `857f6b371ffd01f4ad5bd80f3032e3c84bdd86add99a57b1eb00d080976ea0ee`, Random draw count 4,676, BLUE 승리, 2,230초였다. DELETE 204 뒤 임시 session과 서버를 종료했다. 이번 final boundary milestone에서는 live smoke, frontend build, 대형 diagnostic, calibration/holdout과 historical artifact 재생성을 실행하지 않았다.

### Series Lifecycle V1 backend

상태는 `SERIES_LIFECYCLE_V1_FRONTEND_READY`다.

새 additive `/api/v1/series` backend가 BO3/BO5의 team-code score, current game, fixed managed team, game별 alternating side, deterministic root-to-game seed, Series-owned Player Draft, cumulative Hard Fearless history와 Production V9 result commit을 authoritative하게 소유한다. Client는 winner, score, history, game seed, profile 또는 result를 제출할 수 없다.

Repository는 immutable aggregate snapshot을 Series ID별 `ConcurrentHashMap.compute` 경계에서 교체한다. Maximum retained 32, parent sliding TTL 120분, child idle TTL 30분, simulation lease 5분, command receipts 256은 dedicated lifecycle configuration이 소유한다. TTL cleanup은 current snapshot의 expiry 판정과 제거를 같은 key 원자 경계에서 수행해 성공 mutation을 stale 판단으로 삭제하지 않는다. Long-running Production V9 execution은 repository lock 밖에서 수행하고 exact Series/game/child/reservation/frozen input을 다시 비교한 뒤 decisive game, score, picks 10개와 next game/completion을 한 번에 commit한다.

Command receipt는 진행 중/성공/실패 의미와 exact game/child/generation을 고정한다. 같은 command/payload는 engine/mutation 0으로 최초 code/HTTP/retryable을 재현하고, public error의 `currentRevision/currentStatus`는 replay 시점의 authoritative aggregate를 반환한다. Receipt가 보존하는 historical resulting revision/status는 이 public current 의미와 분리된다. 다른 payload는 deterministic conflict다. Capacity는 실행/상태 변경 전에 검사하며 256개 no-eviction을 넘지 않는다. Service와 response projection은 같은 lifecycle configuration의 capacity 판정을 사용하므로 256개에서는 신규 mutation을 `allowedCommands`에 표시하지 않고 `GET`만 남긴다. 기존 exact command replay는 새 receipt가 없어 계속 허용한다. Cancel/lease expiry 뒤 late success/failure는 더 새 상태를 commit하지 못한다. Committed aggregate는 20-turn Draft/evidence, final assignments, compact result/receipt만 보관하고 full output/event/snapshot timeline은 보관하지 않는다. Explicit replay endpoint는 stored frozen context로 current Production V9을 fresh deterministic execution해 exact receipt가 같은 경우에만 full response를 반환하고, 전후 aggregate 전체 identity가 바뀌면 stale view 대신 fail-closed한다.

Shared unavailable pool의 양 팀 열 role slot을 joint matching하는 preflight를 추가했다. Deterministic state machine은 BO3 2–0/2–1, BO5 3–0/3–2, Game 5 history 40→50과 Game 4/6 미생성을 통과했다. 실제 GEN/T1 root seed 73 smoke는 BO3 3 games/GEN 2–1 T1/30 picks, BO5 5 games/T1 3–2 GEN/50 picks로 완료했다. 매 game은 decisions 20/final assignments 10이고 policy/profile/engine은 current production identity와 exact였다. 이는 wiring fixture이며 balance 증거가 아니다. Frontend-readiness focused는 5 suites/27 tests, 최종 핵심 재확인은 2 suites/15 tests로 모두 clean했다. Final complete backend regression은 첫 실행에서 234 suites/2,266 tests, failures/errors/skipped 0, aggregate JUnit XML 1,931.403초, Gradle wall 17분 12초로 통과했다. 상세 수치는 [frontend readiness 결과](development/series-lifecycle-v1-frontend-readiness-hardening.md)에 기록한다.

현재 backend는 process-local single-node이며 restart recovery, persistence/save-load, auth/ownership과 multi-node coordination이 없다. Receipt는 256개 no-eviction이라 한도 도달 뒤 신규 cancel도 fail-closed하며 public `allowedCommands`도 이를 그대로 반영한다. Standalone `/api/v1/player-drafts/sessions`는 계속 Game 1 + 빈 history이고 `/api/v1/real-matches`도 독립 Game 1 semantics를 유지한다. Series frontend 구현 결과는 아래 milestone과 [Series Lifecycle V1](architecture/series-lifecycle-v1.md)에 있다.

### Series Frontend V1

상태는 `SERIES_FRONTEND_V1_ACCEPTED`다.

LIVE 경기 준비에서 임의의 두 팀, 관리 팀, BO3/BO5, Game 1 side와 signed-long root seed로 Series를 만들 수 있다. 고정 score board, current game rail, controlled side/derived seed, cumulative Hard Fearless와 `allowedCommands`는 authoritative Series response만 사용한다. 각 game은 기존 Player Draft workspace를 Series-owned child transport로 재사용하고, simulate 200은 Playback으로 직행하며 202는 bounded polling 후 committed game replay로 full timeline을 복구한다. `sessionStorage`에는 active Series ID만 저장하고 reload에서 GET으로 score/current game/child state를 재구성한다.

Deterministic Series contract, 기존 Player Draft 33 scenarios, production build, reference check/verify와 bundle boundary가 통과했다. 실제 BFX–BRO BO3는 3 games/2–1/Hard Fearless 30 picks/최종 BFX 승리까지 완료했고, DK–HLE BO5는 Game 1 RED commit→Game 2 BLUE child와 이전 pick 비활성→child/Series cancel 204를 확인했다. Standalone Player Draft와 AUTO의 endpoint 격리, pointer reload, 1440×900·1280×720 overflow 0, console error 0도 확인했다. Live simulation은 모두 200이라 202/response-loss는 contract로 검증했고 actual delayed worker journey는 다음 accessibility milestone에 남겼다.

Backend production/test는 이번 frontend 작업에서 변경하지 않았고 backend full regression도 실행하지 않았다. 상세 구현과 제한은 [Series Frontend V1](development/series-frontend-v1.md)에 있다.

### Series LIVE E2E and Accessibility

상태는 `SERIES_LIVE_E2E_AND_ACCESSIBILITY_ACCEPTED`이며 최종 frontend 계약은 `SERIES_FINAL_CONTRACT_FIXED`다.

Series/child cancel과 simulate response-loss는 revision/대상에 결속한 하나의 논리 command ID를 유지하고, 불명확한 응답 뒤 authoritative GET으로 먼저 조정한다. Committed winner tally와 score, required wins/status, result/receipt를 exact validation하며, pointer not-found/expired와 retryable network/version 오류를 분리한다. 취소 modal의 초기 포커스·trap·pending Escape 차단·오류 alert/restore와 Series context polite live region도 보강했다.

실제 LIVE에서 DK–HLE Game 1 정상 commit, BFX–BRO BO3 2–1/Hard Fearless 30, reservation 중 동일 simulate command의 HTTP 202→GET polling→replay, simulate/child/Series cancel response-loss 뒤 중복 mutation 0을 확인했다. Active reload, network pointer 유지, 실제 404 pointer 제거, keyboard-only 주요 Draft action, 1280×720·1440×900 overflow 0와 reduced-motion을 통과했다. AUTO 200과 standalone Player Draft create/action/cancel도 전용 endpoint만 사용했다.

Frontend contract/build/reference/bundle lane을 통과했고 backend production/test는 변경하지 않아 backend full regression은 실행하지 않았다. 실제 wall-clock EXPIRED, screen reader 제품별 음성, backend persistence/auth/multi-node는 남은 제한이다. 상세 증거는 [Series LIVE E2E and Accessibility](development/series-live-e2e-and-accessibility.md)에 있다.

최종 보강은 backend가 보존하는 `CANCELLED Series / DRAFT_CANCELLED game / winner-null result+receipt`를 exact 허용하고 decisive/ACTIVE/non-current 조합은 거부한다. Simulate response-loss 뒤 GET에서 `COMMITTED`를 확인하면 복구 identity를 replay-only phase에 유지하므로 첫 replay가 다시 유실돼도 새 simulate command를 만들지 않는다. Production 화면이 사용하는 상태기 focused test는 simulate 1회, GET 1회, 동일 command ID의 replay 2회를 확인했다. Series/Player Draft contract, 118-module build, reference check/verify와 bundle verify가 clean했고 backend Java/API/schema는 변경하지 않았다.

### AI vs AI League Simulation V1 product decisions and Hybrid handoff

상태는 `AI_VS_AI_LEAGUE_SIMULATION_V1_PRODUCT_DECISIONS_FROZEN_AND_HYBRID_SEASON_CONTRACT_READY`다.

Season mode는 정확히 한 관리 팀을 고정하는 `HYBRID_MANAGER`와 전 fixture 자동 실행인 `SPECTATOR_FULL_AUTO`로 동결했다. Hybrid에서 관리 팀 fixture는 항상 `PLAYER_CONTROLLED`이고 AI batch/lease에서 제외되며, 나머지만 League-owned `AutomatedSeriesRunner`의 `FULL_AUTO` 대상이다. 10팀 double round robin 90 fixtures BO3, mirrored leg Game 1 side, game별 side 교대, 승 1/패 0과 six-step tie-break, fail-closed `BLOCKED`, 병렬/lease/heartbeat/retry/retention 기본값도 [Product Decisions](architecture/ai-vs-ai-league-simulation-v1-product-decisions.md)에 고정했다.

Player fixture는 server-owned durable `LeagueFixtureSeriesBinding`으로 기존 Series/Player Draft 화면에 들어가며 opponent, format, side와 seed를 다시 선택하지 않는다. AI와 Player 완료는 동일한 `LeagueFixtureCompletionReceiptV1`을 사용하고, transactional outbox와 idempotent League consumer가 standings를 최대 한 번 반영한다. Relational persistence는 Season/Fixture/Job/Lease/Binding/Receipt/outbox/application ledger를 소유하며 현재 process-local Series repository를 durable authority로 승격하지 않는다. 전체 aggregate/state/API/frontend/restart 계약과 correctness matrix는 [Hybrid Season Contract](architecture/ai-vs-ai-league-simulation-v1-contract-sketch.md)에 있다.

이 제품 결정 freeze milestone 자체는 문서 전용이었다. 당시에는 League/Season production Java, API, worker, DB/migration과 frontend를 구현하지 않았고 build/test/full regression/diagnostic도 실행하지 않았다. 이후 Batch 1 pure domain과 Batch 2 automated runner 구현은 아래 milestone에서 완료했다.

### AI vs AI League Simulation V1 domain, schedule and standings

상태는 `AI_VS_AI_LEAGUE_SIMULATION_V1_DOMAIN_SCHEDULE_AND_STANDINGS_IMPLEMENTED`다.

새 `com.lolfm.league` pure domain은 frozen `HYBRID_MANAGER | SPECTATOR_FULL_AUTO`, managed team snapshot consistency, fixture별 `PLAYER_CONTROLLED | FULL_AUTO`, current LCK 10팀/50명 identity envelope와 code-owned product decision hash를 소유한다. Current `productDecisionHash`는 `81a4755760fb513c5803d55dd4855c03fda487114bb7c89b431c959a00a0fb14`다.

Canonical circle schedule은 BO3 double round robin 18 rounds/90 fixtures, round당 team 1경기, pair별 mirrored Game 1 side를 생성한다. Hybrid managed team fixture 18개는 player mode, 나머지 72개는 full-auto이고 Spectator는 90개 모두 full-auto다. Execution mode는 fixture/seed identity에 들어가지 않는다. Fixture root와 League-bound game은 각각 versioned SHA-256 first-eight signed-long algorithm을 사용하며 standalone Series seed 의미는 변경하지 않았다.

Immutable standings는 verified completion의 frozen fixture/team/format을 검사하고 fixture ID와 canonical receipt hash를 함께 ledger에 보관한다. Exact replay는 revision/standings mutation 0, same-fixture conflict와 cross-fixture receipt reuse는 거부한다. 순위는 Series wins → game differential → game wins → mini-league Series wins → mini-league game differential → Season-seed draw이며 draw input/hash/order를 trace로 남긴다.

Focused 4 suites / 15 tests와 final backend regression 243 suites / 2,297 tests / failures 0 / errors 0 / skipped 2를 통과했다. Aggregate XML은 895.260초, Gradle wall은 15분 11초다. Gameplay/Random, resource, API, frontend, Gradle과 historical artifact는 변경하지 않았고 대규모 simulation/diagnostic은 실행하지 않았다. 상세 내용은 [AI League V1 Domain, Schedule and Standings](development/ai-vs-ai-league-simulation-v1-domain-schedule-and-standings.md)에 있다. 후속 Batch 2 automated runner는 아래 milestone에서 완료했다.

### AI vs AI League Simulation V1 automated series runner

상태는 `AI_VS_AI_LEAGUE_SIMULATION_V1_AUTOMATED_SERIES_RUNNER_IMPLEMENTED_READY_FOR_PLAYER_HANDOFF`다.

Stateless synchronous `LeagueAutomatedSeriesRunner`는 frozen schedule의 exact `FULL_AUTO` fixture 하나를 기존 Production Auto Draft와 Match Engine V9으로 끝까지 실행한다. Draft 전에 Season/schedule/product-decision/current LCK roster와 production resource/runtime identity, execution mode와 Hard Fearless pool을 fail-closed 검증한다. Game별 BLUE/RED와 seed는 fixture root 및 history-before에 결속하며, 승자가 있는 game만 기존 common history commit 경로로 양 팀 pick 10개를 반영하고 required wins에서 즉시 종료한다. `PLAYER_CONTROLLED`, identity drift, pool exhaustion과 no-decisive safety timeout은 completion과 standings mutation 없이 `BLOCKED`다.

Compact `LeagueFixtureGameReceiptV1`은 ordered 20-turn Draft, final assignment, picks/bans, Hard Fearless transition, production/resource/input/output/replay/timeline/Random/winner identity를 구조화해 보관한다. `LeagueFixtureCompletionReceiptV1`은 Season/fixture/bound Series/mode/team/format/root·game seed algorithm, ordered games, final score와 winner를 128 KiB 이하 canonical ordered payload로 결속한다. `VerifiedLeagueFixtureCompletion`의 public constructor를 제거하고 actual frozen input/current production snapshot/game evidence와 receipt hash를 재대조하는 package verifier만 opaque completion을 만들 수 있게 했다. Fake 64-char hash, field tamper, omission/duplicate/reorder, cross-fixture/cross-Season receipt는 거부되며 기존 standings exact replay no-op 의미를 유지한다.

Focused affected bundle은 17 suites / 96 tests였다. 최초 실행의 95개는 통과했고 constructor에서 이미 차단된 side tamper를 verifier 단계에서 차단될 것으로 기대한 test-only 분류 1건을 교정한 뒤 runner suite 7/7이 통과했다. 실제 GEN–T1 frozen fixture에서 Production Auto Draft 20턴과 V9을 실행했고 diagnostics ON/OFF 및 두 fresh JVM의 canonical receipt bytes/hash exact equality도 확인했다. Final executable production tree의 complete backend regression은 첫 실행에서 246 suites / 2,306 tests / failures 0 / errors 0 / skipped 2, aggregate XML 962.313초, Gradle wall 16분 14초로 clean pass했다. 이후 문서만 변경해 full regression은 반복하지 않았다.

V1 production schedule은 계속 BO3다. Runner/receipt는 closed BO3/BO5 format의 wins-required와 maximum-games를 처리하지만 production BO5 schedule은 freeze하거나 실행하지 않았다. 90-fixture worker/job, durable DB/outbox/exactly-once recovery, Player Series handoff, API/frontend, balance/performance population과 historical artifact 재생성도 Batch 2 범위가 아니었다. 상세 내용은 [AI League V1 Automated Series Runner](development/ai-vs-ai-league-simulation-v1-automated-series-runner.md)에 있다. 후속 Batch 3 Player handoff 결과는 바로 아래에 기록한다.

### AI vs AI League Simulation V1 Player Series handoff

상태는 `AI_VS_AI_LEAGUE_SIMULATION_V1_PLAYER_SERIES_HANDOFF_IMPLEMENTED_READY_FOR_PERSISTENCE_AND_JOBS`다.

Hybrid 관리 fixture는 이제 server-created canonical `LeagueFixtureSeriesBindingV1`으로 기존 Series/Player Draft/Production V9 kernel에 연결된다. Public standalone Series create에는 League origin/binding/fixture 필드가 없고 내부 start command도 League/Season/fixture/revision/command ID만 받는다. Opponent, BO3, Game 1 side, fixture root/game seed, managed team, Hard Fearless history와 production profile은 frozen binding에서만 온다. Exact start replay는 같은 binding/Series를 재개하며 payload conflict, spectator/FULL_AUTO/non-managed, stale revision과 snapshot/resource drift는 Draft/Match/Random 실행 전에 차단한다.

Series는 `STANDALONE | LEAGUE_BOUND` origin을 구분한다. Standalone ID와 managed-team-salted seed는 그대로이고 League-bound는 fixture root를 Series root로 한 번만 사용하며 canonical pair-first anchor의 League sibling game seed를 사용한다. 실제 GEN–T1 Player BO3는 기존 mixed-authority 20-turn Draft와 Production V9으로 2~3 games를 완료했고 committed game마다 picks 10개만 Hard Fearless에 누적했다. Child cancel/Series interruption과 invalid completion은 score/history/standings를 바꾸지 않고 restart-required 또는 blocked로 남는다.

Batch 2의 `LeagueFixtureCompletionReceiptV1` core를 변경하지 않고 `LeagueFixtureCompletionReceiptV2` envelope를 추가했다. V2는 explicit `leagueId`, nullable Player binding hash와 game별 Auto/Player Draft authority를 결속한다. Player completion은 frontend DTO가 아니라 completed server Series의 stored receipt를 Production V9으로 다시 검증한 evidence에서만 생성한다. Binding/Season/fixture/team/side/seed/history/Draft/assignment/policy/resource/output/replay/timeline/Random/score를 대조한 뒤에만 opaque completion을 발급한다. 첫 standings application만 +1이고 exact replay는 receipt bytes exact, Match Engine 0, standings +0이다.

Batch 3 당시 `LeaguePlayerSeriesBindingPort`와 kernel port는 후속 relational adapter가 교체할
경계만 제공했고 adapter와 Series repository는 process-local이었다. 당시에는 DB/migration,
job/worker/lease/heartbeat/retry, transactional outbox/consumer, restart recovery,
League API/controller와 frontend가 없었다. 이 문단은 historical Batch 3 범위이며 current 상태는
문서 상단의 Batch 5를 따른다.

Player/Auto actual Production V9, diagnostics parity, tamper/cross-boundary, cancel/recovery, Season revision 진행, 두 fresh JVM canonical byte equality와 affected regression이 clean pass했다. Final executable tree의 complete backend regression은 첫 실행에서 249 suites / 2,315 tests / failures 0 / errors 0 / skipped 2, aggregate XML 1,901.498초, Gradle wall 16분 53초로 통과했다. Product decision hash `81a4755760fb513c5803d55dd4855c03fda487114bb7c89b431c959a00a0fb14`와 current Production V9 tuning/profile은 유지했다. 상세 내용은 [AI League V1 Player Series Handoff](development/ai-vs-ai-league-simulation-v1-player-series-handoff.md)에 있다. 다음 task는 `AI_VS_AI_LEAGUE_SIMULATION_V1_PERSISTENCE_AND_JOBS`다.

### Player-controlled Draft Frontend V1

상태는 `PLAYER_CONTROLLED_DRAFT_FRONTEND_V1_ACCEPTED`다. LIVE 경기 설정에 기존 AUTO와 분리된 직접 밴픽 mode와 BLUE/RED controlled side를 추가했다. 직접 밴픽은 전용 strict DTO/validator/client 경계로 실제 session을 만들고, backend legal champion 전체·advisory top 3·구조화된 unavailable reason을 사용해 player action과 AI follow-up을 20턴까지 진행한다. Revision과 logical `clientActionId`, stale refresh, 동일 session retry, explicit cancel 204 의미를 보존한다.

완료 화면은 pick order를 position으로 추론하지 않고 backend `finalAssignments` 10개를 사용한다. 명시적 simulate 뒤 Player Draft 전용 payload를 검증하고 common playback/result view model로 정규화하며, controlled side·20턴 PLAYER/AI authority·control evidence·final assignment를 Draft 다시 보기에 유지한다. 기존 AUTO `/api/v1/real-matches/simulate`, 읽기 전용 자동 Draft, REFERENCE lazy import와 GEN/T1/73 의미는 그대로다.

Frontend production build, Player Draft deterministic contract, reference check/verify와 bundle 경계를 통과했다. 실제 LIVE smoke는 BLUE complete Draft→gzip simulation→Playback→Result→mixed-authority review, RED first-turn/action/cancel 204, AUTO의 Player Draft endpoint 0회를 확인했다. Backend production Java/resource/Gradle과 gameplay/profile/policy, existing reference JSON은 변경하지 않았고 frontend-only 범위이므로 backend full regression은 실행하지 않았다. 상세 구현과 제한은 [Player-controlled Draft Frontend V1](development/player-controlled-draft-frontend-v1.md)에 있다.

현재 제한은 full-page reload resume, Draft timer/WebSocket/DB/auth, BO3/BO5와 Game 2+다. Keyboard/reconnect transport/expiry contract/late-response/race audit은 아래 LIVE E2E/accessibility milestone에서 완료했다.

### Player-controlled Draft LIVE E2E and Accessibility

상태는 `PLAYER_CONTROLLED_DRAFT_LIVE_E2E_AND_ACCESSIBILITY_ACCEPTED`다.

Auto와 Player Draft envelope를 분리한 채 presentation/result/timeline/event/final snapshot 의미를 canonical common validator로 공유한다. Unknown participant, player/champion/side/position binding, assistant pair, final team kills/gold와 player KDA/CS/gold/XP/level, Player Draft final assignment/presentation 변조는 Playback/Result 전에 거부된다. `ACTIVE`만 current turn과 action projection을 가지며 COMPLETED/SIMULATED/CANCELLED/EXPIRED는 null currentTurn/빈 projection이라는 terminal wire 의미로 backend/frontend/test/docs를 통일했다.

Champion pool 173개는 roving tabindex 1개와 Arrow/Home/End를 사용하며 focus, selected와 confirmed를 분리한다. Turn/AI/reconciliation/완료는 polite status, 오류는 assertive alert로 구분한다. 취소 modal은 non-destructive initial focus, Tab trap, pending dialog focus/aria-busy, pending Escape 억제와 정상 restore/fallback을 제공한다. React StrictMode extra cleanup으로 valid response가 폐기되던 lifecycle guard 회귀도 actual E2E에서 발견해 setup마다 re-arm하도록 수정했다.

Actual LIVE Playwright는 GEN 대 T1 seed 73으로 BLUE와 RED 각각 create 1/action 10/simulate 1, revision 10, decisions 20, PLAYER/AI 각 10, final assignment 10, Playback/Result를 통과했다. BLUE는 mixed-authority review와 SIMULATED back의 DELETE 0 증가를, RED는 최초 BLUE AI decision/RED turn 2와 Result ban portrait 10/10을 확인했다. AUTO는 기존 real-matches simulate 1/player-drafts 0과 읽기 전용 20/20을 유지했다. Response-only loss는 GET 200으로 revision 1/decisions 2를 복구했고 delayed cancel은 single DELETE 204로 종료했다. Clean flow의 page/console/runtime validation/reference fallback error는 0, 1440×900과 1280×720 horizontal overflow는 0, broken/fallback portrait는 0이다.

Base siege hardening 전 V9 `live:verify`는 options/response raw SHA를 기록하고 당시 policy `78c3bb1cffe2cd90a1f7acab6923a1813fea40acd135186ff522eabf95d38493`, profile `PRODUCTION_MATCHUP_COMPOSITION_V1`, configuration `caaf76274dc148040b0a95eae1ed5181790b2fc840f45af9b109ea7951c1fd5d`, engine V9를 preflight했다. 현재 backend policy는 rules V4와 hash `3afaa399f7c2b20a940c7cfd7510f6c7962ba43eec575cc75ed55218d53f0ce9`다. Frontend가 이번 범위에서 제외돼 `frontend/scripts/verify-real-match-live-contract.mjs`의 고정 expected hash 갱신과 LIVE 재검증은 후속 작업으로 남겼다. Historical V8 handoff와 reference는 덮어쓰지 않았다.

Frontend contract 33개, focused backend API 1 suite/5 tests, build/reference/live/bundle 검증이 통과했다. Production mapper 변경 뒤 final complete backend regression은 226 suites / 2,232 tests / failures 0 / errors 0 / skipped 0, Gradle wall 23분 16초로 한 번에 통과했다. 상세 결과는 [Player Controlled Draft LIVE E2E and Accessibility](development/player-controlled-draft-live-e2e-and-accessibility.md)에 있다.

이 milestone 당시 다음 순서는 `SERIES_LIVE_E2E_AND_ACCESSIBILITY`였으며, 해당 작업과 최종 Series 복구 계약은 현재 모두 완료됐다.

### Player Draft interactive and simulation latency profiling V1

상태는 `PLAYER_DRAFT_INTERACTIVE_AND_SIMULATION_LATENCY_PROFILE_CAPTURED`다. 기존 action 2~3초와 simulate 약 10초 기록을 원인으로 가정하지 않고, GEN/T1 seed 73의 BLUE/RED controlled flow를 backend phase, HTTP/CDP, browser parse/validation/normalization/React/DOM으로 분해했다. Last Draft action과 explicit simulate는 별도 요청이고, cold와 warm도 분리했다.

Current-source cold Chromium action 20건은 38.3~1,440.3ms, 중앙값 502.25ms였다. Direct 50건에서 ACTIVE response projection 중앙값 149.894ms가 가장 컸고 AI follow-up 137.195ms가 근접한 보조 병목이었다. AI follow-up 0/1/2개 service 중앙값은 148.900/337.972/470.112ms이고 repository exact replay lookup/lock/idempotency 근사는 0.033ms였다. Projection은 다음 PLAYER turn의 full legal pool/unavailable/advisory를 다시 계산한다. Action browser parse/validation은 주 병목이 아니었다.

Cold Chromium simulate click→Playback은 BLUE 5,092.9ms, RED 6,814.5ms였다. Direct first service 중앙값은 4,301.066ms이며 completed Draft/input validation 3,480.430ms가 Production V9 576.383ms보다 컸다. 입력 경계는 20턴의 PLAYER legal pool과 AI authoritative trace를 처음부터 재생하고 final role/assignment를 다시 검증한다. Exact retry도 compact output cache가 아니라 V9을 fresh 재실행하고 receipt equality를 확인해 중앙값 4,114.124ms였다. Gzip body는 browser run별 479,655/2,220,596 bytes, decoded JSON은 5,655,202/26,712,924 bytes였고, 서로 다른 Draft/timeline 때문에 side 효과로 일반화하지 않는다.

Profiling ON/OFF의 Draft/gameplay/input/timeline/Random/output identity는 exact했고 public API timing field, tuning, cache, gameplay와 UI 동작은 바꾸지 않았다. Backend production Java/resource/Gradle/runtime을 건드리지 않았으므로 focused parity와 diagnostic만 실행했고, frontend 기본-OFF observational hook은 production build와 actual LIVE로 검증했다. 공식 artifact는 action 50, AI 48, simulation 10, browser 22 rows이며 manifest raw SHA-256은 `3009861416762f3ee61a56624d3cd1762bac0ab889d2f6a8092404c40e820d46`다.

다음 우선순위는 `PLAYER_DRAFT_SIMULATION_INPUT_VALIDATION_PERFORMANCE_HARDENING_V1`과 `PLAYER_DRAFT_SESSION_PROJECTION_PERFORMANCE_HARDENING_V1`이다. 그 뒤 AI turn, compact payload/Web Worker, async progress를 각각 별도 계약으로 검토한다. 이번 milestone은 profiling만 수행했으므로 latency 자체는 줄이지 않았다. Standalone과 Series-owned child Draft가 completed validation과 selector/Match Engine 경계를 공유하지만 Series end-to-end 수치는 아직 측정하지 않았다. 상세 내용은 [Player Draft latency profiling V1](development/player-draft-interactive-and-simulation-latency-profiling-v1.md)에 있다.

### Series Lifecycle V1 contract sketch

상태는 `SERIES_LIFECYCLE_V1_CONTRACT_SKETCH_READY`다. [Series Lifecycle V1 Contract Sketch](architecture/series-lifecycle-v1-contract-sketch.md)는 현재 caller-owned `SeriesDraftHistory`, Match Engine V1의 game number/history/input binding과 standalone Player Draft 기반을 확인하고, 향후 backend-owned BO3/BO5 aggregate의 team-code score, game별 side/seed/history, parent-bound child Draft, Production V9 simulate-and-commit, compact receipt, revision/idempotency/concurrency/expiry와 additive Series API/frontend view를 설계했다.

이 sketch 작성 당시에는 문서 계약만 준비됐고 BO3/BO5 backend/frontend가 구현되지 않았다. 이후 aggregate/repository/controller/DTO, Game 2+ Series-owned Player Draft, side/root seed/lifetime/no-decisive/explicit replay와 frontend가 위의 Series milestones에서 구현·검증됐다. DB/Save/Load, auth/ownership과 multi-node persistence는 여전히 없다. 이 historical sketch 작업 자체는 production policy/profile/gameplay와 기존 Player Draft/Real Match API를 바꾸지 않은 문서 전용 작업이었다.

권장 구현 순서는 domain/repository → Series-owned Draft integration → Production V9 match commit/API → backend hardening → Series frontend다. 다만 즉시 다음 제품 작업은 기존 계획대로 standalone `PLAYER_CONTROLLED_DRAFT_FRONTEND_V1`일 수 있으며, 해당 화면 component가 explicit game/side/exclusion context를 받도록 설계하면 이후 Series wrapper가 재사용할 수 있다.

### Auto Draft Variety V1

기존 production Draft가 정렬된 평가 결과의 1위만 선택해 seed가 달라도 같은 Draft를 만들던 경계를 교정했다. 후보 생성, Draft Meta, 선수 proficiency, 상대 threat, matchup/composition, flex/denial, future/role feasibility와 shallow search는 그대로 유지하고 최고점 대비 2.0 이내 상위 3개 후보에만 deterministic seeded selection을 적용한다. BAN weight는 55/30/15, PICK은 70/22/8이다.

같은 팀/roster/series state/seed는 20-turn trace, final assignment, Match Engine input과 timeline이 exact다. 선택은 SHA-256 bucket이며 Match Simulator `Random`을 소비하지 않는다. `draftIdentity()`는 ordered decisions로 유지하고 selection policy/trace hash를 Match Engine V1 input, V3 execution/replay provenance와 Real Match API `draft`/`integrity`에 additive하게 결속했다. 현재 Match Engine V1 policy hash는 `3afaa399f7c2b20a940c7cfd7510f6c7962ba43eec575cc75ed55218d53f0ce9`다. 과거 activation smoke의 GEN–T1 seed 73 output hash는 당시 V2 policy까지 결속한 historical 값이며 현재 V3 output oracle로 재사용하지 않는다.

10 fixture × 8 seed의 80-Draft diagnostic은 complete identity variety 10/10, final pick tuple variety 10/10, correctness error 0으로 `AUTO_DRAFT_VARIETY_V1_ACCEPTED`를 기록했다. 1,600턴 선택은 rank 1/2/3이 1,126/358/116회였고 score-loss max는 BAN 1.983034, PICK 1.988794였다. Fresh-JVM A/B도 33,808 bytes와 SHA-256 `0291df0f03da4ff0067a620f0d4b8e1dc317577defd2a47166cfa9b579e097c2`로 exact다. 최종 backend regression은 214 suites / 2,182 tests / failures 0 / errors 0 / skipped 0, aggregate 815.104초, Gradle wall 13분 50초로 통과했고 frontend production build도 통과했다. 세부 정책·fixture별 분포와 한계는 [Auto Draft Variety V1](development/auto-draft-variety-v1.md)에 있다.

### Real Draft→Match backend application flow

`RealDraftMatchOrchestrator`는 Spring application component로서 다음 deterministic 경계를 연결한다.

```text
explicit LCK team codes
  → LckTeamAssembler real Teams
  → DraftTeamContext
  → DraftEngine / FinalDraftResult
  → FinalDraftResult.matchChampionAssignments()
  → same real Teams + seeded MatchSimulator
  → MatchTimeline
```

`FinalDraftResult`의 final role assignment가 match champion assignment의 유일한 source다. Application preflight는 explicit team code 기반 current `PlayerRatingKey`/`PlayerId`, 정확한 five-position roster, Draft context identity/proficiency, legal `ChampionRoleKey`, final-role/assignment equality, match-wide duplicate stable ID, caller-owned Hard Fearless exclusions를 검증한다. Team/player display name과 array index는 identity로 사용하지 않는다.

Series overload는 호출자가 소유한 `SeriesDraftHistory`만 변경한다. Draft와 Match가 모두 성공한 뒤 양 팀 completed picks를 commit하고, 다음 game은 그 exact set을 exclusion으로 받는다. Static/global series state와 BO3/BO5 scheduling은 추가하지 않았다.

GEN 대 T1 representative flow는 game 1과 Hard Fearless game 2, fresh-history replay를 실행해 flex final role mapping, 실제 LCK `PlayerId`의 KILL/assist participant 보존, complete timeline replay를 검증했다. Fresh Game 1 경로는 Real Match API와 LIVE frontend에 노출되며, 지속되는 BO3/BO5 series lifecycle은 아직 노출하지 않는다.

### Explicit simulation runtime profiles and provenance

`RealDraftMatchOrchestrator`의 profile 인자 없는 overload는 authoritative `MatchEngineV1Policy`에서 `PRODUCTION_MATCHUP_COMPOSITION_V1`을 resolve하며, additive explicit overload는 다음 closed-set profile ID를 받는다. `ConfiguredMatchSimulatorFactory`의 유일한 public `create` 경계도 profile ID와 instrumentation만 받아 registry를 다시 resolve한다. Caller가 직접 만든 `ResolvedSimulationRuntimeProfile`은 provenance 경계에서 exact registry membership을 재검증하므로 임의 gameplay boolean 또는 internal audit mode를 application runtime에 주입할 수 없다.

| Profile | 공통 gameplay | Matchup | Composition | Jungle Clear | Configuration hash |
| --- | --- | --- | --- | --- | --- |
| `BASELINE_V1` | 전부 현재 Spring snapshot과 동일하게 ON | `OFF` | `OFF` | `DISABLED_NOT_INTEGRATED` | `c8cc557bd721228c473e30d31b7258510f9608a18098578bc1da36e603536215` |
| `MATCHUP_ONLY_CANDIDATE_V1` | 동일 | `GEOMETRIC_V2` | `OFF` | `DISABLED_NOT_INTEGRATED` | `58714464c19a2cffd108d47a93a0909126513c8bb10cb0e19bbd87f8e78532ec` |
| `FULL_SYSTEM_CANDIDATE_V1` | 동일 | `GEOMETRIC_V2` | `PRODUCTION_V2` | `DISABLED_NOT_INTEGRATED` | `caaf76274dc148040b0a95eae1ed5181790b2fc840f45af9b109ea7951c1fd5d` |
| `PRODUCTION_MATCHUP_COMPOSITION_V1` | 동일 | `GEOMETRIC_V2` | `PRODUCTION_V2` | `DISABLED_NOT_INTEGRATED` | `caaf76274dc148040b0a95eae1ed5181790b2fc840f45af9b109ea7951c1fd5d` |
| `FULL_SYSTEM_WITH_JUNGLE_ECONOMY_CANDIDATE_V1` | 동일 | `GEOMETRIC_V2` | `PRODUCTION_V2` | `ECONOMY_V1` | `e04869bca5281f7f416c8191d7bf1b5be04b3129f33f6dfd4de83e8d8e92743b` |
| `FULL_SYSTEM_WITH_JUNGLE_TEMPO_CANDIDATE_V1` | 동일 | `GEOMETRIC_V2` | `PRODUCTION_V2` | `ECONOMY_AND_GANK_TEMPO_V1` | `c835280cbaa1244f4fecb099b19f71111c6d77aa1aeb1b7110a6e86e6381451c` |

공통 ON snapshot은 Lane Combat/FARM Recovery/Jungle Gank/Counter Gank/Roam/Objective Priority/Lane Phase/Mid Game Macro/Objective Decision/Late Game Macro/Progression/Progression Power/Champion Power 전부다. Jungle Clear contribution도 canonical configuration field다. Economy candidate는 `ECONOMY_V1`, Tempo candidate는 `ECONOMY_AND_GANK_TEMPO_V1`이며 별도 `SimulationOptions` boolean은 추가하지 않았다.

`configurationHash`는 이 field-complete gameplay configuration만 고정한다. Diagnostics ON/OFF는 별도 `SimulationInstrumentation`이며 configuration/replay hash에서 제외되고 complete timeline과 Random fingerprint도 바꾸지 않는다.

실행 이력 V6는 `engineImplementationVersion=MATCH_SIMULATOR_ENGINE_IMPLEMENTATION_V6`와 profile별 active rules를 분리했다. 기존 세 profile은 `MATCH_SIMULATOR_PRE_JUNGLE_RULES_V2`, pure-JRM Jungle Economy candidate는 `MATCH_SIMULATOR_JUNGLE_ECONOMY_RULES_V2`, Tempo candidate는 `MATCH_SIMULATOR_JUNGLE_TEMPO_RULES_V1`이다. Batch C V4의 structured eligibility diagnostics 뒤 V5에서 death/activity reason과 one-major-combat gate를 강화했고, V6는 Champion Power 참가자 평균과 diagnostic map을 명시적 `PlayerKey(TeamSide, Position)` 순서로 canonicalize해 JVM별 부동소수점 마지막 비트 차이를 제거했다. Historical B1/B2/B3/Final 13G-B와 Match Engine freeze evidence는 이 V6 identity를 그대로 보존한다.

현재 production은 `MATCH_SIMULATOR_ENGINE_IMPLEMENTATION_V9`이다. V8의 authoritative player ratings와 `PLAYER_ABILITY_PROFILE_V1` 투영을 유지하면서, V9은 전체 구조물 HP/부분 피해, match-scoped 지속 공성·wave, explicit target/idempotency, local defense/backdoor, 개별 넥서스 포탑 180초·40% 재생성과 structured action/snapshot을 통합했다. 공통 의미 변경을 식별하기 위해 Baseline/Matchup/Full/production profile은 `MATCH_SIMULATOR_PRE_JUNGLE_RULES_V4`, Jungle Economy candidate는 `MATCH_SIMULATOR_JUNGLE_ECONOMY_RULES_V4`, Tempo candidate는 `MATCH_SIMULATOR_JUNGLE_TEMPO_RULES_V3`를 사용한다. 현재 production은 Matchup/Composition을 활성화했고 Jungle contribution과 Economy/Tempo candidate는 계속 비활성화다. `replayProvenanceHash`는 engine/rules version, configuration, raw resource/version snapshot, side/team/roster, series history, Draft rules/scoring policy/final draft/final assignment, seed와 Match Engine input을 묶는다.

### Structure engine V9

현재 상태는 `MATCH_ENGINE_V9_BASE_SIEGE_STRATEGIC_COHERENCE_HARDENED`다. Active siege continuation은 due인 양 진영의 결정을 동일한 tick-start structured state에서 먼저 고정하고, 모든 중단을 적용한 뒤 허용된 공격만 실행한다. 이전 tick부터 자기 Nexus가 노출됐거나 직접 위협받는 팀의 lower-value persistent siege는 `OWN_BASE_EMERGENCY` / `LOWER_VALUE_SIEGE_ABORTED_FOR_BASE_DEFENSE`로 구조물 변이 전에 종료된다. 같은 tick에서 새로 생긴 위협은 소급 적용하지 않고 다음 tick부터 반영하므로 BLUE/RED 순회 순서가 eligibility를 바꾸지 않는다.

구조물 target은 `StructureTargetId`로 side/lane/tier/nexus turret index를 명시하며 base target을 `Lane.MID`로 우회하지 않는다. 동일 `(time, side, siege action ID, attack sequence)` mutation은 match-scoped registry로 멱등 처리되고, game finish 뒤 target mutation과 3인 미만 base fallthrough는 거부된다. Resolver는 team display name을 gameplay identity로 사용하지 않으며 최종 participant eligibility 전 Random을 소비하지 않는다.

외곽/내부/억제기 포탑, 억제기, 개별 넥서스 포탑과 넥서스는 공통 HP/부분 피해를 사용한다. Wave, backdoor 보호, 생존 공격 참가자와 local defender가 공격 eligibility/피해에 들어가고, `BaseSiegeState`가 수비 복귀·공격자 사망·wave 소멸·기간 만료까지 다음 tick 공성을 이어간다. 구조물 이벤트는 source와 lifecycle phase/HP/참가자/wave/stop reason에 더해 `ownBaseThreatLevelAtDecision`, `strategicContinuationDecision`, `strategicallyAllowed`를 제공하고, snapshot은 lane/base HP·남은 넥서스 포탑 수·active siege를 제공한다. 안전한 explicit base-race commit 증거는 아직 없어 자기 기지 emergency 중 Nexus continuation은 V1에서 fail-closed지만, 자기 기지가 안전한 no-kill Baron Nexus finish는 유지된다. Frontend LIVE adapter는 기존 구조화 필드 표시를 유지하며 새 전략 판정 필드를 별도 UI로 시각화하는 작업은 이번 범위가 아니다.

200-seed 진단은 평균/중앙 경기 2,026.3/2,040초, 첫 포탑 중앙 1,060초, 기지 개방 중앙 1,690초였다. Base clear 218건 중 215건이 넥서스로 이어져 follow rate 98.6%였고 연결 시간은 모두 10초였다. 교전 없는 9포탑 전부 철거, duplicate event ID, 비정상 HP, source 누락, 넥서스 포탑이 남은 상태의 넥서스 파괴와 종료 뒤 mutation은 모두 0건이었다. 이 값은 correctness oracle이 아니라 현재 초기 tuning의 observational 결과다.

새 strategic coherence suite 8 tests와 구조물/V9 contract/provenance/same-seed/cross-JVM 영향 묶음 13 suites / 115 tests는 failures/errors/skipped 0으로 통과했다. 최종 complete backend regression은 239 suites / 2,282 tests / failures 0 / errors 0 / skipped 2, aggregate JUnit XML 2,041.953초, Gradle wall 18분 28초로 첫 실행에서 통과했다. 그 뒤 executable source/resource/Gradle/shared fixture는 바꾸지 않고 문서만 갱신했다. 200-seed diagnostic과 calibration/holdout은 correctness와 무관하고 tuning 변경도 없어 실행하지 않았다. 상세 계약은 [Base Siege Strategic Coherence Hardening V1](development/match-engine-v9-base-siege-strategic-coherence-hardening-v1.md)에 있다.

`BASELINE_V1`과 기존 autowired simulator의 same teams/assignments/seed complete timeline은 exact parity다. 기존 Spring `MatchController`는 계속 direct autowired simulator와 DummyDataFactory를 사용하고 HTTP profile 선택은 추가하지 않았다.

### Matchup V9 structure-effect attribution

선행 V9 requalification의 `structure changed 31.5%`는 consumed holdout 400쌍 중 최종 canonical structure signature가 다른 126쌍을 뜻한다. 잔여 HP 한 곳의 차이도 포함하므로 구조물이 31.5% 더 파괴됐거나 버그가 31.5% 발생했다는 의미가 아니다. 선행 manifest 18/18, 200 checkpoint/sidecar, canonical raw 3,600행, consumed holdout 1,200행과 fresh-JVM evidence를 read-only로 재검증했고 `RECOMMEND_BASELINE_V1`을 보존했다.

새 attribution-only contract는 90 G1 + 10 Hard Fearless G2 fixture, fixture당 별도 diagnostic seed 4개, `BASELINE_V1`/`MATCHUP_ONLY_CANDIDATE_V1` 두 profile을 고정했다. Core는 800 match rows / 400 pairs이며 seed는 historical Final 13G-B, V9 calibration과 consumed holdout에 overlap 0인 `CONSUMED_AS_DIAGNOSTIC_NOT_HOLDOUT`이다. Fixture별 frozen Draft를 profile/seed 전체가 공유한다. 4개 JVM shard에서 core 800, baseline replay 100, diagnostics OFF parity 200으로 총 1,100 simulations을 실행했고 paired identity 400/400, replay 100/100, instrumentation timeline·Random 200/200이 exact였다.

| Final-state primary severity | Pair | 비율 |
| --- | ---: | ---: |
| EXACT | 269 | 67.25% |
| HP_ONLY | 104 | 26.00% |
| LANE_TOWER_PROGRESSION | 1 | 0.25% |
| INHIBITOR_PROGRESSION | 15 | 3.75% |
| NEXUS_TURRET_PROGRESSION | 1 | 0.25% |
| NEXUS_OR_ENDING | 10 | 2.50% |

Any final state difference는 131/400(32.75%)였지만 실제 파괴·진행 component difference는 27/400(6.75%)였다. First tower 차이는 21쌍, first inhibitor/base-open과 first Nexus turret 및 Nexus destruction 차이는 각각 28쌍이었다. Aggregate structure damage/destruction은 `12,350→12,392`/`5,550→5,558`, persistent siege start/stop은 `6,499→6,530`/`8,131→8,158`이었다. Winner changed 10/400은 BLUE→RED 5와 RED→BLUE 5, objective changed 28/400, Blue WR delta 0.0pp, 평균/P95 duration delta `+5.825s`/`0s`였다. Defending-side progression component는 BLUE 27, RED 26으로 관측됐다.

Invalid HP/state, duplicate structured action, impossible respawn, Nexus ordering, post-finish mutation, ineligible/duplicate Random, display identity, Matchup direct Random/OFF contribution/perspective mismatch는 모두 0이다. Matchup non-zero application 132,135회와 이후 pressure/combat/economy/structure divergence는 관측했지만 application의 시각·position·context가 남지 않아 시간 순서를 인과로 승격하지 않았다. 최종 판정은 `MATCHUP_V9_STRUCTURE_ATTRIBUTION_BLOCKED_BY_DIAGNOSTIC_GAP`이며 production/tuning은 바꾸지 않았다.

새 acceptance proposal은 correctness exact gate, observational sensitivity, gameplay-critical macro safety, causal/reachability를 분리한다. Phase 13D Composition fallback 12%를 V9 hard gate로 복사하지 않고, macro 수치는 `THRESHOLD_REQUIRES_PRODUCT_DECISION`으로 남겼다. 공식 eligibility는 local application provenance와 제품 tolerance를 먼저 정의한 별도 contract 및 non-overlapping fresh seed에서만 판단한다. 세부 결과는 [Matchup V9 구조물 영향 Attribution](development/matchup-v9-structure-effect-attribution-v1.md)을 따른다.

### Composition V9 application and causality hardening

선행 V9의 `approved gameplay application=0`은 실제 gameplay zero가 아니라 production accounting/provenance 결함이었다. Frozen `PRODUCTION_V2` scalar modifier는 이미 skirmish와 teamfight/siege/base-defense winner consumer에 도달했지만 historical `CANDIDATE` 전용 counter/list가 이를 관측하지 못했다. 팀파이트 점수의 기존 support-tool Composition 기여와 그 점수를 재사용한 objective fight도 scalar-only counterfactual에 포함돼 local cause가 누락됐다.

Match-scoped provenance는 actual attempt → routing/eligibility → frozen scalar 또는 existing non-scalar input → winner/fight grade → structured public action을 같은 `GameplayAttemptId`로 결속한다. `OBJECTIVE_SETUP`은 계속 scalar `DISABLED_NOT_APPROVED`이며 새 gain을 추가하지 않았다. 실제 objective fight의 기존 support-tool 소비만 별도 `SCALAR_DISABLED_EXISTING_NON_SCALAR_EFFECT_CONSUMED`로 기록한다. Diagnostic/application schema는 각각 `COMPOSITION_RUNTIME_DIAGNOSTICS_V4` / `COMPOSITION_APPLICATION_CAUSAL_PROVENANCE_V4`다.

Final V5는 100 fixture × fresh 4 seeds, 두 profile의 800 core rows / 400 pairs와 replay 100, instrumentation 200으로 총 1,100 simulations을 4개 distinct JVM에서 실행했다. Actual attempts 22,219, approved scalar calculated/applied/consumed/non-zero는 모두 4,481, existing non-scalar consumed 1,743, total effect applications 4,967, local changed/unchanged 65/4,902였다. Public divergence 59쌍은 local cause 59/59에 결속돼 coverage 100%, unexplained 0이었다. Correctness/replay/instrumentation/Composition Random 오류도 모두 0이다.

Verdict는 `COMPOSITION_APPLICATION_CAUSALITY_HARDENED_READY_FOR_FRESH_REQUALIFICATION_DESIGN`이다. 이는 production eligibility가 아니다. 이 작업 당시에는 `BASELINE_V1`, Matchup/Composition/Jungle OFF production policy, engine V9/rules V3, frozen gain/formula/resource/profile/API/frontend를 유지했다. V5 artifact manifest raw SHA-256은 `cc5d02b4c97e636cf927b07275ffcaff8eb4ec0badaaa307883d5391a5b45af9`다. 당시 final complete backend regression은 215 suites / 2,163 tests / failures 0 / errors 0 / skipped 0, Gradle wall 21분 46초로 통과했다. 세부 근거는 [Composition V9 application and causality hardening](development/composition-v9-application-causality-hardening-v1.md)을 따른다.

후속 `COMPOSITION_V9_CAUSALITY_AUDIT_HARDENING_AND_V5_ARTIFACT_REPAIR`는 V5 seed/schedule을 새 eligibility로 재해석하지 않고 같은 400 seeds를 evidence repair로 재실행했다. 대형 freeze/worker/finalize는 기본 `test`의 `diagnostic` 제외를 적용받아 forbidden 5 classes 실행 0건이었고, exact focused JUnit proof receipt, 23-entry explicit dependency manifest, 4 distinct worker PID, nested receipt/checkpoint/sidecar를 포함한 recursive root manifest를 추가했다. Public divergence 59/59는 non-null actionId와 complete structured event identity로 exact direct cause였고 indirect/unresolved/unexplained는 0이다. Objective 486건은 BLUE/RED routing 249/237, 양방향 flip 1/2, unchanged 483, orientation mismatch 0이며 SKIRMISH 3,224건의 explicit non-scalar/clamp component는 exact zero다. Pure counterfactual은 Progression/Champion Power/Matchup stats를 변경하지 않고 actual runtime만 1회 기록한다.

Authoritative verdict는 `COMPOSITION_V9_CAUSALITY_AUDIT_HARDENED_AND_V5_EVIDENCE_REPAIRED`다. 이는 activation이나 fresh eligibility가 아니며 그 작업 당시 production은 `BASELINE_V1`이었다. V6 artifact는 `backend/build/reports/composition-v9-application-causality-hardening-v6/`, V5 관계는 `EVIDENCE_REPAIR_REUSES_V5_SEEDS_NOT_FRESH_ELIGIBILITY`, recursive manifest는 39 files / 20 nested / raw SHA-256 `79f957365e72fb201ca113ce19463e5298a51c102f9dc8f82af8851af870422f`다. 당시 final backend regression은 212 suites / 2,169 tests / failures 0 / errors 0 / skipped 0, Gradle wall 12분 18초였다. 다음 단계는 `AUTO_DRAFT_VARIETY_V1`, 그 이후 별도 fresh requalification이었다.

### Final 13G-B1 audit contract and real-match harness

`PHASE_13G_B1_AUDIT_CONTRACT_AND_REAL_MATCH_HARNESS`는 gameplay를 바꾸지 않는 test-side 감사 기반이다. 실제 10개 LCK 팀의 모든 unordered pair 45개를 양 진영으로 뒤집은 G1 90 fixtures를 주 감사 축으로 고정하고, SHA 기반 deterministic pairing으로 모든 팀을 한 번씩 포함한 Hard Fearless G2 5 pairs × 양 진영 10 fixtures를 보조 민감도 축으로 고정했다. 각 fixture의 calibration 24 seeds와 holdout 8 seeds는 서로 분리해 미리 예약했고 schedule hash `3bb5e81241a3be2a1509e67528e577ae8f48fca94dec5fc15f93ec8ac78052ef`로 전체 계약을 고정한다.

한 fixture는 production `RealDraftMatchOrchestrator`로 series target game까지 한 번 준비한 뒤 그 `FinalDraftResult`, 실제 양 팀 roster와 final assignment를 profile loop 밖에서 고정한다. `PreparedFixture`는 이 controlled 경로만 생성할 수 있고, writer는 schedule content hash를 재계산한 뒤 canonical frozen schedule과 exact equality를 확인한다. Match 실행만 다섯 closed profile로 바꾸므로 paired comparison 사이에 re-Draft나 champion assignment drift가 없다. 각 결과에는 configuration/rules, resource/roster/series/Draft/final assignment/replay provenance, complete timeline hash, Random fingerprint, 최종 팀·정글러 checkpoint, selected diagnostics, 전체 structured diagnostics hash와 domain별 integrity가 기록된다. 이 bridge는 test source에만 존재하며 production simulator API, Spring runtime, REST와 frontend는 변경하지 않았다.

B1 bounded dry-run은 `GEN` Blue–`T1` Red G1 fixture와 calibration/holdout에 속하지 않는 별도 seed 하나로 다섯 profile을 한 번씩 실행하고 `BASELINE_V1`을 한 번 재실행했다. 다섯 실행의 Draft/final assignment/resource/roster identity가 exact equality였고 BASELINE same-seed replay의 replay provenance, timeline, Random fingerprint와 simulator의 전체 diagnostic snapshot/history가 exact equality였다. Champion Power/Matchup, Composition, Combat Outcome, Objective Priority, Structure, Lane Phase, Mid Game Macro, Progression과 Jungle Economy의 명시적 구조 오류는 모두 0이다. 세 pre-Jungle profile은 Jungle Economy/Tempo 실행 0, Economy candidate는 CS/gold/XP 경로 reachability, Tempo candidate는 READY 관찰 23회와 Gank actual consumption 1회를 보였다. 이 단일 fixture 관찰의 winner, 경기 시간이나 profile 차이는 balance 근거가 아니다.

P1 선행 gate는 같은 B1 fixture를 두 fresh JVM에서 각각 실행해 7개 artifact 전체를 byte-for-byte 비교한다. Phase-specific source/build guard로 재고정한 V6 tree에서 두 manifest SHA가 `dc8f63a117bbd15dc05ca533ae8c98a3707ae70fa9af810f6f14539d8ee9b9cd`로 같았고, 직후 canonical B1 manifest SHA도 exact equality였다. 이 연결이 다르면 B2 worker는 시작하지 않는다.

재고정한 generated artifact는 `backend/build/reports/phase13g-b1/`의 contract/profile/schedule JSON, 3,200-row reserved schedule CSV, 5-row dry-run JSON/CSV와 `SHA256SUMS.txt`다. Summary SHA-256은 `37ce2c275d5dc4837328c7e8c9fb7b100f62ffcdee4ee740217140d8c751f5c0`, manifest SHA-256은 `dc8f63a117bbd15dc05ca533ae8c98a3707ae70fa9af810f6f14539d8ee9b9cd`다. Report status는 `HARNESS_HARDENED_READY_FOR_CALIBRATION`이며 `calibrationExecuted=false`, `holdoutExecuted=false`, `productionDecision=NOT_EVALUATED`를 명시한다. Canonical production guard는 472 files / `68edbcb7393c9a54c0888a4f27a4e286774306675dce48991554fd22dcb2ddac`, B1 audit harness guard는 9 files / `20c6d834dd01d90688c377da1f68afd28abb20dff34e7cf02bb0253a22271b74`이다. Build report는 증거이지 baseline 또는 correctness input이 아니다.

### Final 13G-B2 real-data calibration

`PHASE_13G_B2_REAL_DATA_CALIBRATION`은 B1이 예약한 calibration lane만 열었다. 100 fixtures × 24 seeds × 5 fixed profiles로 정확히 12,000경기를 실행했고 G1 10,800 / Hard Fearless G2 1,200으로 분리했다. Fixture별 production Draft는 profile/seed loop 밖에서 한 번 고정하며 G1 1회, G2는 series history를 포함해 2회씩 총 110 orchestration을 사용한다. 이 준비 실행과 fixture별 BASELINE 결정성 replay 100회는 12,000 calibration 표본에서 제외된다. Holdout 실행은 0이다.

네 shard class는 `forkEvery=1`로 각각 별도 fresh JVM에서 실행되며, 동시 실행 수가 환경의 Gradle worker 수에 제한되더라도 JVM 재사용은 허용하지 않는다. 각 fixture의 120행은 job/fixture/roster/Draft/profile/seed에서 replay provenance를 다시 계산하고, 결과·진단·정글 관측 전체 row payload와 fixed Draft/replay evidence의 canonical digest를 확인한 뒤 임시 파일에서 atomic move로 승격한다. Shard 완료 시 checkpoint raw-byte SHA를 담은 worker receipt를 남기며 finalizer는 서로 다른 JVM identity 4개, fixture ownership과 payload digest 100개를 다시 검증한다. 이 receipt 경계를 통과하지 않은 synthetic output은 `SYNTHETIC_VALIDATION_ONLY`이고 공식 READY를 만들 수 없다. V2 공식 재실행은 checkpoint fixture 100, resume 0, `seed 24/24` 100을 로그에 남겼고 12,000 replay provenance가 모두 고유했다. 매 fixture의 첫 BASELINE replay 100/100이 exact였고 12,000/12,000 match의 Champion Power/Matchup, Composition, Combat Outcome, Objective Priority, Structure, Lane Phase, Mid Game Macro, Progression, Economy 구조 오류 합계는 모두 0이다.

고정 관측 시각은 600/900/1,200/1,500/1,800초다. Structure push가 게임 시계를 tick 경계 너머로 전진시킬 수 있으므로 상태를 보간하지 않고 요청 시각 이상인 첫 실제 snapshot을 사용하며 requested/actual time을 함께 기록한다. 종료 전에 도달하지 못한 시각에는 final state를 복제하지 않는다. Fixed side observations 101,890개 중 14,778개가 요청 시각보다 늦었고 최대 지연은 54초였다.

Calibration의 paired winner identity 변화는 다음과 같다. 이는 같은 seed에서 profile이 action eligibility와 이후 Random 소비 순서를 바꾼 결과까지 포함하는 민감도 지표이며, 곧바로 승률 인과효과나 production 채택 판정으로 해석하지 않는다.

| Paired comparison | G1 flips | G2 flips | 합계 |
| --- | ---: | ---: | ---: |
| Matchup − Baseline | 39 / 2,160 | 5 / 240 | 44 / 2,400 (1.83%) |
| Full − Matchup | 74 / 2,160 | 9 / 240 | 83 / 2,400 (3.46%) |
| Economy − Full | 12 / 2,160 | 6 / 240 | 18 / 2,400 (0.75%) |
| Tempo − Economy | 732 / 2,160 | 79 / 240 | 811 / 2,400 (33.79%) |
| Tempo − Baseline | 755 / 2,160 | 87 / 240 | 842 / 2,400 (35.08%) |

Economy − Full은 G1에서 Blue/Red 정글 최종 CS가 평균 +3.382/+4.230, XP가 +644.8/+691.5였고 G2에서는 CS +6.233/+2.096, XP +814.3/+579.0이었다. Tempo − Economy는 G1에서 경기 시간 평균 +7.79초, 정글 CS +1.137/+0.644, XP +80.4/+30.7이고 G2에서는 +33.42초, CS +2.925/+4.063, XP +167.6/+272.7이었다. Tempo profile 2,400경기에서 Gank readiness/actual consumption은 47,495/5,175회, Counter-gank는 4,447/660회로 실제 경로가 충분히 도달했다. Tempo − Economy flip 방향은 Blue→Red 386, Red→Blue 425로 한 방향 독점은 아니지만, 33.79%의 높은 paired 민감도는 B3 gate 동결 시 별도 product review 대상으로 보존했다. 모든 profile의 timeout은 0이었다.

Artifact는 `backend/build/reports/phase13g-b2/`의 contract/job/fixed-Draft/replay/checkpoint receipt manifest/match/checkpoint/marginal 및 profile/team/jungler summary, integrity/review JSON과 `SHA256SUMS.txt`다. Phase-specific source/build guard 도입으로 stale checkpoint를 재사용하지 않고 V3 경로에서 12,000경기를 다시 고정했다. 16/16 raw SHA가 통과했고 review SHA-256은 `32c1770b6971179c0cb7033e853882e7e9fb06c6980285eb2dba23210993fbee`, manifest SHA-256은 `71ac3a26cc4df6c49794c2daeb2efc75bd2667b39237b89af4a1a6bda963d7e4`다. Status는 `CALIBRATION_EVIDENCE_READY_FOR_REVIEW`, run guard는 `281ecd7b4985695f0c3168fd699b6be0c525387aea14f701a23d2f0614485e30`, checkpoint payload manifest는 `f1945f8333733a4c3ecefdfcaa30276129a98a3b58ae9e609e45d247de79df58`다. B1/B2 combined guard는 18 files / `040800b178865ebec1a41e9dbbd78a2b7166c52b5e94a14df17fad7d6dea4a24`이며 calibration behavior는 이전 B2와 exact equality다. 자동 tuning과 `PRODUCTION_V1` 결정은 수행하지 않았다.

### Final 13G-B3 frozen holdout

`PHASE_13G_B3_FROZEN_HOLDOUT`은 B2를 다시 실행한 최종 executable tree에서 candidate와 acceptance gate를 먼저 canonical JSON으로 고정한 뒤, B1에 예약된 `HOLDOUT` seed만 한 번 materialize했다. Contract 생성 시 holdout execution count는 0이었고, contract SHA-256 `7b7de51ac242e0bd260efe7f2130db17c538cef49b897c02bfe3579484aa8fa4`, candidate identity `6a920353b5436d7053fa2863de361a1201b09d88a08ef0c25ecaca20051ea81f`, acceptance gate identity `b05a346777cce78f0c31f7916de24d062a6e55bb4d17bf67eafe92eb225a00dd`를 official runner가 exact match로 요구한다. Economy의 주 판정은 `ECONOMY_MINUS_FULL`, Tempo는 `TEMPO_MINUS_ECONOMY`이며 나머지 비교는 regression/context evidence다.

수치 gate 67개는 B2 point estimate와 calibration/holdout 표본 수를 사용한 99% two-sample normal prediction interval로 고정했다. 연속값은 `mean ± z·s·sqrt(1/nCalibration + 1/nHoldout)`, 비율은 `p ± z·sqrt(p(1-p)·(1/nCalibration + 1/nHoldout))`, `z=2.5758293035489004`, 소수 12자리 바깥쪽 반올림, 양 끝 inclusive다. G1/G2/전체 winner flip, 경기 시간, Blue gold edge, Blue/Red 정글 CS·XP, gank/counter-gank attempt drift와 side gap을 분리했고 Tempo flip 방향도 별도 gate로 고정했다. Award reachability, pre-Jungle/Tempo contribution 0, Tempo readiness/consumption reachability 및 actual attempt binding, SUPPORT FARM CS 0, timeout 0은 7개 exact behavior gate다. 명시적 product tolerance가 없는 Tempo 33.79%는 수치 일관성이 있어도 자동 PASS로 만들지 않고 `REVIEW_REQUIRED`로 남긴다.

공식 holdout은 G1 3,600 + Hard Fearless G2 400 = 4,000 matches, 4,000 unique jobs/provenance와 paired marginals를 만들었다. Economy − Full winner flip은 G1 10/720(1.389%), G2 1/80(1.25%), 전체 11/800(1.375%)였다. G1 상한 1.379455%를 1건에 해당하는 0.00943%p만 넘겨 수치 gate 67개 중 이 gate 하나만 실패했고 Economy verdict는 `FAIL`이다. 나머지 Economy 축은 범위 안이었으며 G1/G2 Blue/Red 정글 CS 변화는 +3.606/+4.226 및 +6.438/+2.050, XP 변화는 +644.2/+705.7 및 +785.3/+549.3이었다.

Tempo − Economy winner flip은 G1 249/720(34.58%), G2 23/80(28.75%), 전체 272/800(34.00%)로 B2 전체 33.79%의 높은 민감도를 재현했다. 방향은 Blue→Red 135, Red→Blue 137로 한 방향 독점이 아니며, Gank readiness/consumption 15,858/1,715와 Counter-gank 1,489/189가 모두 도달했고 consumption과 actual attempt 차이는 0이다. Tempo 수치 gate는 모두 통과했지만 높은 민감도에 대한 product tolerance가 없으므로 verdict는 `REVIEW_REQUIRED`다. Exact behavior gate는 7/7, numeric gate는 66/67이며 evidence 자체는 `HOLDOUT_EVIDENCE_READY_FOR_FINAL_REVIEW`, `productionDecision=NOT_EVALUATED`다.

각 shard는 자기 authorization 파일을 atomic move해 한 번만 시작할 수 있고 `forkEvery=1`인 네 독립 Test task가 4개 distinct JVM receipt를 남긴다. Finalizer는 authenticated checkpoint 100/100, raw payload digest 100/100, BASELINE replay 100/100 exact, calibration 실행 0, domain error 0, SUPPORT FARM CS 0, timeout 0을 재검증했다. Artifact는 `backend/build/reports/phase13g-b3/`에 있으며 final review SHA-256은 `60df0caa7b0193a4b67fe0380bc326d9e7efdae34f794515679131c3f305badf`, integrity SHA-256은 `909d2abb5869e68d7102fa7245410e535d8792dafe68051b200a352664b73bf7`, gate evaluation SHA-256은 `c1513e6adc633a1a4692cbe6a2b62a05a0977335fc6b143d4a82a522026b9ba1`, 18-entry manifest SHA-256은 `460d74573ffb971e9e2ff491a039d2002fc6e02a4f37067a9c18da73f0b70c9d`다. 이 holdout은 소비됐으며 결과가 불리해도 재실행하거나 gate/tuning을 바꾸는 입력으로 재사용하지 않는다.

### Historical Final 13G-B synthesis and Production V1 decision

`FINAL_13G_B_SYNTHESIS_AND_PRODUCTION_V1_DECISION`은 당시 새 match를 실행하지 않고 B2 12,000경기와 B3 4,000경기의 raw SHA manifest, review 상호 참조와 6,400개 주 비교 paired row만 읽었다. Final evidence는 `FINAL_EVIDENCE_VALID`, 새 simulation execution은 0이었다. 당시 결정은 `KEEP_CURRENT_RUNTIME_DEFAULT`: `FULL_SYSTEM_WITH_JUNGLE_ECONOMY_CANDIDATE_V1`과 `FULL_SYSTEM_WITH_JUNGLE_TEMPO_CANDIDATE_V1`을 Production V1 기본값으로 활성화하지 않고 application default인 `BASELINE_V1`을 유지하는 것이었다. Runtime identity는 `EXACT`, freeze readiness는 `READY_FOR_MATCH_ENGINE_V1_FREEZE`였다. 이 artifact는 historical evidence이며 현재 V9 Matchup/Composition activation policy를 승인한 holdout으로 재해석하지 않는다.

Final 13G-B 결정 당시 retained configuration hash는 `c8cc557bd721228c473e30d31b7258510f9608a18098578bc1da36e603536215`, active rules는 `MATCH_SIMULATOR_PRE_JUNGLE_RULES_V2`, engine은 `MATCH_SIMULATOR_ENGINE_IMPLEMENTATION_V6`였다. Matchup/Composition/Jungle contribution은 각각 `OFF`/`OFF`/`DISABLED_NOT_INTEGRATED`이며 diagnostics는 configuration identity와 분리한다. 당시 production source identity는 472 files / `68edbcb7393c9a54c0888a4f27a4e286774306675dce48991554fd22dcb2ddac`, resource provenance는 `64ab1be3fdfe8d6660648ac634b52a86a5693d264bfbe707153dac9c17d39b4f`다. Runtime identity hash `bcb3d2bdf009a8b53d6f99db69ad3f129a7c3c2f29570bcdf12ee0c0655ba675`도 historical decision evidence로 보존한다. 현재 production engine V9의 실행 증거는 Structure engine V9와 Real Match API 절에 별도로 기록한다.

당시 wiring fixed-seed 검증에서 `RealDraftMatchOrchestrator` 기본 overload와 explicit `BASELINE_V1`은 execution provenance/replay identity/complete timeline이 exact였고, Spring autowired `MatchSimulator`도 configured `BASELINE_V1`과 complete timeline parity였다. 현재 기본 overload는 production policy를 따르지만 explicit baseline rollback parity와 legacy `POST /api/matches/simulate`의 autowired simulator/`DummyDataFactory` 경로는 보존한다. `SimulationOptions.productionDefaults()`는 Matchup `GEOMETRIC_V2`, Composition `PRODUCTION_V2`, Jungle contribution OFF인 저수준 constructor default이며 configuration hash `caaf76274dc148040b0a95eae1ed5181790b2fc840f45af9b109ea7951c1fd5d`다. 값이 현재 production과 같아도 authoritative application policy는 아니다.

Economy의 frozen `FAIL`은 그대로 보존한다. G1 실제 1.388889%와 상한 1.379455%의 차이는 0.009434%p, 즉 이 표본 크기에서는 winner flip 한 건 경계다. 이를 구조적 결함이나 대규모 balance 붕괴로 확대 해석하지 않지만, 결과가 근소하다는 이유로 holdout을 다시 돌리거나 gate를 완화해 승인하지도 않는다. B2→B3 segment flip-rate 상관은 champion -0.394, player/team 0.104, player×champion -0.182로 안정된 특정 segment 패턴을 재현하지 않았다.

Tempo의 10개 공통 champion bucket별 민감도 순서는 calibration과 holdout에서 unweighted Pearson 0.906으로 재현됐다. 이 계산은 pre-registered decision gate가 아니고 player, team, fixture, matchup을 통제하지 않았으므로 champion의 독립 효과나 인과관계를 확립하지 않는다. Player×champion 0.588, fixture 0.422, player/team 0.219이고 현재 snapshot은 팀당 정글러가 한 명이라 player와 team 효과도 분리할 수 없다. 33.79%→34.00% winner flip은 같은 seed에서 action eligibility와 이후 Random trajectory가 달라진 민감도이지 직접적인 승률 인과효과가 아니다. 따라서 Tempo 미채택과 `DEFER_TO_V2_PRODUCT_TOLERANCE_AND_TRAJECTORY_ISOLATION_REVIEW`는 그대로다.

Machine-readable artifact는 `backend/build/reports/final-13g-b/`의 evidence binding, retained runtime identity, segmented sensitivity, flipped pairs, synthesis, Production Decision V2와 `SHA256SUMS.txt`다. 이전 5-entry manifest `f21a3bd1eaf34d9361c87c299199bce2432e6edb62585088cbc251a6e0145542`는 historical reference다. Hardened 6-entry manifest SHA-256은 `bd9a9cf3b089cfc76fceb0311094c1b70232278404f5675c42d89849d927bc98`이며 6/6 raw SHA가 통과했다. 이 결정은 gameplay source, resource, tuning, Gradle wiring, HTTP/frontend를 변경하지 않는다.

### Historical Match Engine V1 freeze

Final 13G-B가 승인한 당시 runtime을 `MatchEngineV1` application facade로 동결했다. 당시 V1 policy는 `BASELINE_V1`과 configuration `c8cc557bd721228c473e30d31b7258510f9608a18098578bc1da36e603536215`만 허용했고 Economy/Tempo candidate activation은 둘 다 `false`였다. Historical policy hash는 `61ec36e4ec36a3693a7fd34f9acbd018f615115dda45b558580f1ee7ff1a02a5`다. 현재 policy/profile/hash는 이 historical freeze를 덮어쓰지 않고 상단 activation snapshot처럼 별도 version으로 전환됐다.

새 immutable input은 정확한 5×5 stable player/position/rating/proficiency roster, final champion assignment, ordered Draft/Hard Fearless/series context, seed와 policy requirement를 포함한다. V1 replay provenance는 기존 resource/Draft/profile/seed identity와 전체 input hash를 다시 결속해 동적 rating/proficiency snapshot도 빠짐없이 구분한다. 새 immutable output은 final-snapshot 기반 summary, stable participant/champion identity가 있는 structured timeline, final Draft와 mandatory execution provenance 및 input/structured timeline/output hash를 포함한다. `hasValidOutputHash`는 실제 timeline hash를 먼저 재계산한 뒤 output envelope를 확인하며, display name과 event message는 gameplay identity에서 제외한다.

`RealDraftMatchOrchestrator.orchestrateV1`은 Draft를 한 번만 만들고 V1 input으로 변환한다. Simulation/projection/output validation까지 성공한 뒤에만 series history를 commit하며 실패 경로는 history를 변경하지 않는다. 기존 simulator/Real Draft overload와 HTTP response는 유지한다. 세부 계약은 [Match Engine V1 Contract](architecture/match-engine-v1.md)에 있다.

Freeze artifact는 `backend/build/reports/match-engine-v1-freeze/`에 있으며 JSON 7개 manifest 7/7이 통과했다. Manifest SHA-256은 `1f5bc20c347d25d833e822325de1fa294dc61d38c55da121ea30d15ab70a0728`, production source identity는 480 files / `5bb14af3eab33ceb5c9b6fed6f88d7bfa421b212663ac94e99067f05fdcafac6`다. Fresh JVM A/B의 canonical output/summary/provenance/Random은 exact equality다. 실제 legacy Real Draft↔V1은 complete gameplay timeline, Random fingerprint와 replay hash/algorithm을 제외한 provenance field가 exact이며, V1 replay hash는 전체 input snapshot 결속 때문에 의도적으로 별도 값이다. Final 13G-B historical source identity와 evidence manifest는 audit 기록으로 그대로 보존한다.

### Real Match API V1

`GET /api/v1/real-matches/options`는 authoritative `LckTeamAssembler`와 player resources에서 실제 LCK 10팀, 팀당 5명, 총 50개의 unique stable `PlayerId`를 canonical 순서로 제공한다. `POST /api/v1/real-matches/simulate`는 exact schema, 서로 다른 두 team code와 canonical signed-long JSON string seed만 받으며 unknown field, profile/candidate flag, client-authored Draft와 series history를 Real Match V1 경계에서 거부한다.

요청마다 `RealDraftMatchOrchestrator.orchestrateV1` fresh-history overload를 호출해 단판 Game 1 Professional Draft와 Match Engine V1 current implementation을 실행한다. Service는 authoritative `PRODUCTION_MATCHUP_COMPOSITION_V1` policy/configuration, 현재 요청의 BLUE/RED/seed, fresh Game 1/history, mandatory provenance와 실제 structured timeline output hash가 모두 exact일 때만 immutable `REAL_MATCH_RESPONSE_V1`을 반환한다. Typed preflight 실패만 422이며 엔진/오케스트레이션 내부 실패는 500과 구분된다. Team code는 display name과 별도 identity로 유지한다. 응답은 presentation roster, ordered Draft/final assignment, final result, 선수별 base/realized rating과 champion proficiency ability profile, 모든 structured event/snapshot과 policy/input/resource/replay/timeline/output hash 및 Random fingerprint를 포함한다. Options policy와 simulate integrity에는 acceptance status, ordered known risk 2개, holdout false, rollback profile과 automatic fallback false가 additive하게 노출된다. Error는 controller-scoped `REAL_MATCH_API_ERROR_V1`이며 legacy endpoint와 Champion API error 의미는 그대로다. 세부 계약은 [Real Match API V1](architecture/real-match-api-v1.md)에 있다.

Base siege hardening 전 Acceptance LIVE smoke의 fixed `GEN` 대 `T1`, seed `"73"`는 T1(RED) 승리, `NEXUS_DESTROYED`, 2,320초(38분 40초), event 376개, snapshot 233개이며 output hash는 `4774cec14fcd0606c00421b7fce29f8159f4950a77814212cd3417b30a2d617b`다. 이 값은 당시 policy hash `78c3bb1cffe2cd90a1f7acab6923a1813fea40acd135186ff522eabf95d38493`를 결속한 historical smoke다. 당시 Options/simulate HTTP 200, actual gzip, ordered risk와 rollback metadata, Draft 20 decisions/10 assignments, browser Draft→Playback→x30/종료 seek→Result와 233-snapshot 골드 차트를 확인했다. 현재 rules V4 policy에서 이 output hash를 oracle로 재사용하지 않으며, 이 한 경기는 balance 표본도 아니다.

`backend/build/reports/real-match-api-v1/`의 기존 frontend handoff와 performance baseline은 V8 당시의 historical artifact다. V9의 gameplay/provenance/output hash oracle로 재사용하거나 자동 승격하지 않는다. Frontend 기본 공급자는 현재 LIVE API이며 reference artifact는 명시적 reference 모드에서만 사용한다. V9 공식 handoff를 새로 배포해야 할 때는 current source binding, semantic audit와 fresh-JVM candidate A/B byte equality를 다시 거쳐 별도 승격해야 한다. Historical Match Engine freeze와 V8 manifest/SHA는 과거 감사 기록으로 그대로 보존한다.

Frontend V1-A reference extractor는 계속 33,617,922-byte handoff에서 deterministic presentation projection을 검증한다. Frontend V1-B는 LIVE를 기본 공급자로 만들고 실제 `/api/v1/real-matches/options`와 `/simulate`를 strict validation/normalization 경계로 연결했다. 임의의 서로 다른 두 팀과 signed-long seed, loading/cancel/retry/stale-response 격리, Draft→Playback→Result 일관성을 제공하며 오류 때 reference로 자동 fallback하지 않는다. Fixed GEN/T1/73과 non-reference HLE/DK/-73 실제 E2E, 1440×900/1280×720, full payload timing/heap과 lazy reference chunk를 검증했다. 세부 근거는 [Real Match Frontend V1-B](development/real-match-frontend-v1-b.md)에 있다.

### Real Match Performance Baseline V1

`REAL_MATCH_PERFORMANCE_BASELINE_V1`은 현재 Real Match V1을 바꾸지 않고 실제 지연과 payload를 phase별로 관찰한 measurement-only milestone이다. Windows 10, OpenJDK 21.0.9, Gradle 9.5.1, 12 logical processors, test JVM max heap 3 GiB의 한 fresh JVM에서 fixture마다 warmup 1회 뒤 measured 3회를 순차 실행했다. 각 iteration은 test-side production-equivalent decomposition과 실제 Spring random-port HTTP replay의 response/result/output/replay/timeline/Random exact parity를 요구한다. Timing은 gameplay와 hash에 들어가지 않으며 default `test`에서는 공식 diagnostic을 제외한다.

| Fixture | 결과 | 앱 경계 median | 실제 local HTTP median | 최대 phase median | raw HTTP JSON | offline gzip |
| --- | --- | ---: | ---: | ---: | ---: | ---: |
| GEN–T1 / `73` | BLUE, 3,430초, event 517, snapshot 344 | 14,226.442 ms | 14,555.977 ms | roster/Draft/input 12,704.045 ms (89.299%) | 33,617,921 B | 2,789,989 B (8.299%) |
| HLE–DK / `-73` | RED, 2,840초, event 519, snapshot 285 | 9,879.504 ms | 10,197.495 ms | roster/Draft/input 8,980.335 ms (90.899%) | 20,315,047 B | 1,875,877 B (9.234%) |

Fixture A는 V8 handoff output hash `bdc597af083aa4f081cf4fe7a242d0e36eec7744b186d998d6f83b717648e874`와 exact다. Fixture B의 첫 measured 결과는 output hash `fef2dfd3c522a69f7393bf46196ac9319cb4b6981e9131c694a01239d7aaabb0`인 authoritative 반복 기준이 되었고 모든 반복이 exact였다. MatchEngine execution median은 각각 1,078.131 ms와 547.898 ms, JSON serialization median은 291.712 ms와 118.587 ms다. roster/Draft/input phase 안에서 roster assembly, Draft scoring, input projection 중 어느 하나를 더 세분한 값은 아니므로 그 내부 원인을 단정하지 않는다.

공식 local artifact는 `backend/build/reports/real-match-performance-baseline-v1/`의 contract, raw runs CSV, summary, analysis와 `SHA256SUMS.txt`다. Manifest 4/4 raw SHA가 통과했고 manifest raw SHA-256은 `c9b4659c4d602fb33c7295885cdc2685a4991469cc4cc0b097ca2d1a20cb26ee`다. 이 milestone은 gameplay/tuning/schema/frontend/async/streaming/compression activation을 변경하지 않았고 기존 Real Match handoff도 재생성하지 않았다. 다음 권장 단계는 가장 큰 roster/Draft/input 준비 묶음을 더 세분하는 별도 최적화 계약과, 20–34MB 중 거의 전부를 차지하는 timeline payload 전달 계약을 분리해 설계하는 것이다.

### Real Match runtime hardening and Auto Draft scalability audit V1

기존 Spring Boot Gradle `bootRun`의 optimized launch는 실제 JVM에 `-XX:TieredStopAtLevel=1`을 넣어 CPU 집약적인 Real Match Draft를 C1-only로 실행했다. `bootRun.optimizedLaunch=false`를 명시해 이 개발 실행 경로만 정상 tiered compilation으로 되돌렸다. Packaged JAR과 test JVM 설정은 바꾸지 않았다. 변경 뒤 네 fresh JVM의 `TieredStopAtLevel`은 모두 `4 {default}`였고 profiled/non-profiled nmethods code heap이 존재했다.

| 실행 경계 | GEN–T1/73 first / warm | HLE–DK/-73 first / warm |
| --- | ---: | ---: |
| hardened `bootRun` | 15.750 / 13.933초 | 12.166 / 10.612초 |
| packaged JAR | 16.878 / 13.682초 | 12.554 / 9.655초 |

모든 first/warm 응답은 HTTP 200, 무압축 body와 frozen winner/duration/event/snapshot을 유지했다. GEN–T1 output `bdc597af083aa4f081cf4fe7a242d0e36eec7744b186d998d6f83b717648e874`, HLE–DK output `fef2dfd3c522a69f7393bf46196ac9319cb4b6981e9131c694a01239d7aaabb0`뿐 아니라 response/output/replay/simulator timeline/structured timeline/Random fingerprint가 각 fixture의 first/warm에서 exact였다. 따라서 이전 Chromium 54.4초/35.5초는 정상화된 backend 실행시간의 일반값이 아니다. 옛 C1-only `bootRun`, hardened `bootRun`, JAR, 기존 baseline test JVM, 브라우저 다운로드/parse/validation/render는 서로 다른 관찰 경계다.

정상 JVM의 12-fixture performance coverage는 모든 실제 10팀을 BLUE/RED에 각각 포함하고, global warmup 1회 뒤 fresh Game 1을 fixture당 2회씩 순차 측정했다. Schedule hash는 `8888526d5085a5bfcc75b1495223e4babeba0c69fa63dd8c9a8adda9e2315b00`이다. Test-side decomposition 24/24는 production `DraftEngine.draft()`의 20 decisions, 점수/alternatives, bans/picks, final role와 Match assignment, final Draft/assignment/input identity에 exact였다.

| Auto Draft 관측 | median | p90 | max |
| --- | ---: | ---: | ---: |
| full automatic Draft | 11.173초 | 13.420초 | 15.412초 |
| roster + context + fresh history + Draft + input | 11.177초 | 13.422초 | 15.413초 |
| 전체 turn | 549.954ms | 973.463ms | 1,858.374ms |
| BAN turn | 733.136ms | 1,017.033ms | 1,858.374ms |
| PICK turn | 487.298ms | 665.639ms | 1,054.154ms |

Run별 Draft 비중 median은 준비 구간의 99.9901%였다. Roster assembly median 0.192ms, `DraftTeamContext` 0.022ms, Match Engine input projection 0.943ms로 관찰돼 기존 묶음의 주 비용은 자동 Draft로 좁혀졌다. 초반 BAN 1~4턴 평균은 949~995ms였고 마지막 PICK 19/20턴은 62.167/5.624ms였다. Draft당 exact counter는 initial plan 2, replan 1,362, candidate generation 680회/8,160개, action evaluation 1,560회, continuation node 1,560개다.

10ms JFR sampling의 CPU 상위 경로는 `DraftAvailability.poolHealth/available`, `PreDraftPlanner.candidatePlanValue`, `RoleAssignmentSolver.enumerate/feasibleAssignments`였다. Allocation sample도 `candidatePlanValue`, `poolHealth`, role assignment enumeration이 상위였다. 이는 profiler overhead가 있는 sample evidence이며 exact 인과관계나 실제 할당 byte 총량으로 해석하지 않는다. Exact한 것은 별도 deterministic counter다.

직렬 projection은 median/p90 기준 100게임 18.62/22.37분, 250게임 46.55/55.92분, 1,000게임 3.10/3.73시간이다. worker 수로 단순 나눈 병렬 보장은 아니며 CPU contention, GC, 메모리, scheduling overhead를 포함하지 않는다. 다음 별도 milestone `DRAFT_ENGINE_PERFORMANCE_HARDENING_V1`은 위 planner/availability/role-feasibility 반복 경로를 우선 대상으로 삼는다. 이번 작업은 search depth/beam/candidate/scoring/order, cache, gameplay, Random, API, frontend를 바꾸거나 Draft를 최적화하지 않았다.

공식 artifact는 `backend/build/reports/real-match-runtime-auto-draft-scalability-v1/`의 contract, runtime/fixture/turn raw CSV, hotspot/summary JSON, analysis와 `SHA256SUMS.txt`다. Status는 `REAL_MATCH_RUNTIME_HARDENED_AND_AUTO_DRAFT_SCALABILITY_AUDIT_CAPTURED`, 7/7 entry가 통과했으며 manifest raw SHA-256은 `751cb19ccf55b34cc0bf4a410a292ba66df4e84d566dd1e217b4a68712d3be8b`다. 기존 performance manifest `c9b4659c4d602fb33c7295885cdc2685a4991469cc4cc0b097ca2d1a20cb26ee`와 4/4 entry는 재생성 없이 다시 검증했다.

### Draft Engine performance hardening V1

`DRAFT_ENGINE_PERFORMANCE_HARDENING_V1`은 search depth, beam width, candidate limit, scoring/tuning, 후보 정렬과 각 점수 내부의 floating-point 연산 순서를 유지한 채 한 번의 `DraftEngine.draft()` 안에서만 반복 계산을 재사용한다. `DraftComputationContext`가 champion 조합별 role assignment, candidate/picked role, completion과 pool-health를 소유하고, `PreDraftPlanner`는 한 planner build 안에서 같은 archetype/champion 점수를 한 번만 계산한다. Context는 매 Draft 호출마다 새로 만들며 static/global/resolver-owned/`ThreadLocal`/cross-match cache는 없다.

동일 JVM의 uncached primitive를 reference로 사용해 12 fixtures × 2 measured Draft의 24/24 final identity와 480/480 turn decisions, component/alternative/root candidate scores와 semantic counter가 exact임을 확인했다. 기존 frozen timing CSV는 다른 JVM의 unordered immutable collection iteration 때문에 일부 비선택 후보의 double bit가 달라 438/480만 exact였고, 이는 prior-JVM timing artifact에 대한 observational 값이지 acceptance gate가 아니다. GEN–T1/73과 HLE–DK/-73 Real Match API response/output/replay/simulator timeline/structured timeline/Random은 2/2 exact였고 fresh JVM A/B Draft identity 파일도 byte-for-byte 같았다.

| full automatic Draft | 기준선 | 공식 hardening | 감소율 |
| --- | ---: | ---: | ---: |
| median | 11.173초 | 4.032초 | 63.911% |
| p90 | 13.420초 | 4.314초 | 67.852% |
| max | 15.412초 | 5.854초 | 62.016% |

24 Draft 합계 physical computation은 role assignment 24,172,180→591,296(97.554% 감소), planner candidate 184,948,920→16,269,480(91.203% 감소), completion 2,609,860→2,196,552(15.836% 감소), pool-health 1,075,962→957,308(11.028% 감소)였다. 이 counter는 deterministic work evidence이고 JFR CPU/allocation은 sampling evidence다. 공식 status는 `DRAFT_ENGINE_PERFORMANCE_HARDENED`이며 요구한 median 40%와 p90 30% 단축 gate를 모두 통과했다.

최종 executable tree의 focused Draft 묶음은 58 tests, Real Match focused 묶음과 fresh JVM A/B도 clean pass했다. Complete backend regression은 첫 실행에서 203 suites / 2,117 tests / failures 0 / errors 0 / skipped 0, aggregate JUnit XML 679.103초, Gradle wall 11분 33초였다. 공식 artifact는 `backend/build/reports/draft-engine-performance-hardening-v1/`의 contract, fixture/turn CSV, cache/JFR/summary JSON, analysis와 `SHA256SUMS.txt`이며 manifest 7/7 raw SHA가 통과했다. Manifest raw SHA-256은 `ae11f4eb368a8b796a113b32963048a764509b0bb98e27ebce313b7ec645d694`다. 자세한 계약과 명령은 [Draft Engine Performance Hardening V1](development/draft-engine-performance-hardening-v1.md)에 있다.

### Real Match transport compression and live E2E V1

Real Match `application/json`에는 Spring Boot 표준 HTTP compression을 사용한다. Production 설정은 compression 활성화, MIME type `application/json`, 최소 크기 `8KB`이며 `Accept-Encoding` 협상을 따른다. Controller/DTO/API schema와 frontend에는 수동 gzip/gunzip 경로가 없다. `identity`와 무헤더 요청은 기존 JSON을 그대로 받고 gzip 해제 결과는 동일한 `REAL_MATCH_RESPONSE_V1`이다.

| Fixture | decoded JSON | 외부 HTTP gzip body | 압축률 / 감소율 | 공식 HTTP first / warm | Chromium encoded bytes | Chromium request+download first / warm |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| GEN–T1 / `73` | 33,617,921 B | 2,789,995 B | 8.299% / 91.701% | 9.600 / 6.399초 | 2,828,788 B | 9,819.5 / 7,033.5 ms |
| HLE–DK / `-73` | 20,315,047 B | 1,875,883 B | 9.234% / 90.766% | 8.036 / 5.283초 | 1,902,063 B | 8,653.8 / 5,471.0 ms |

두 fixture 모두 bootRun/JAR의 gzip first/warm, identity, 무헤더 응답이 HTTP 200과 exact output/replay/simulator timeline/structured timeline/Random fingerprint를 유지했다. 실제 Chrome은 설정→자동 Draft→재생→결과를 first/warm으로 완료했고 `Content-Encoding: gzip`, LIVE source, console/page error 0, reference fallback 0이었다. Parse/validation/normalization은 GEN first 115.2/13.3/2.8ms, HLE first 78.4/12.0/3.8ms였다. gzip은 wire 전송량만 줄이며 decoded JSON 20~34MB, parse/validation/heap 비용은 남는다. Localhost wall time도 gzip CPU/JIT 영향 때문에 correctness gate로 사용하지 않는다.

최종 backend full regression은 첫 실행에서 204 suites / 2,118 tests / failures 0 / errors 0 / skipped 0, aggregate JUnit XML 622.904초, Gradle wall 10분 37초로 통과했다. 공식 status는 `REAL_MATCH_TRANSPORT_COMPRESSION_AND_LIVE_E2E_ACCEPTED`, artifact manifest 5/5 raw SHA-256은 `860f6cea4e8dfc42e1a38148dc5c2763331bcd899d784670af4e3222d89a068f`다. 자세한 계약과 수치는 [Real Match Transport Compression V1](development/real-match-transport-compression-v1.md)에 있다.

### Pre-Jungle baseline

공식 oracle은 `PRE_JUNGLE_RUNTIME_BASELINE_V2`다. Focused tests와 final full regression이 clean pass하고 production source guard가 동일한 tree에서만 `generatePreJungleRuntimeBaselineV2`를 실행했다. 새 JVM 재생성도 source artifact와 byte-identical하게 성공했다.

| 항목 | 값 |
| --- | --- |
| Baseline ID | `PRE_JUNGLE_RUNTIME_BASELINE_V2` |
| Fixed schedule | 3 profiles × GEN–T1 G1/G2 + T1–GEN mirror G1 = 9 matches |
| JSON | `backend/baseline/pre-jungle-runtime-v2/pre-jungle-runtime-baseline-v2.json` |
| JSON SHA-256 | `0bce126117683e47ace908c348dbe2448f21592dc5009bd9f4514bb566fadb8e` |
| Resource provenance hash | `3affaf03ce588e0d1054e35ab1839f04e262d8ba3c512188374707ce3c1b8a4e` |
| Production source tree | 456 files / `b7965a1d1ebb9d76f298bc65e957da79c4e7cf2a3d0df35a6eca29ebaa0ab350` |
| Source revision | `8a55664955fad82d037ca9286be6bdb050b029fe` + exact working-tree hash |
| Rules / engine | `MATCH_SIMULATOR_PRE_JUNGLE_RULES_V2` / `MATCH_SIMULATOR_ENGINE_IMPLEMENTATION_V1` |
| Random identity | per-match draw count + ordered `next(bits)` trace SHA-256 |
| Jungle Clear invariant | artifact 당시 contribution disabled, authored gameplay-enabled profile count 0 |

Artifact와 build report mirror는 canonical CRLF로 byte-identical하고 `SHA256SUMS.txt` 검증을 통과했다. Baseline JSON은 `.gitattributes`의 `-text` 규칙으로 Git line-ending 변환을 금지하며 generator도 host OS 대신 CRLF를 명시한다. Generator는 V2 profile 목록을 세 개로 고정하고 future enum 값을 자동 포함하지 않으며 existing source bytes가 다르면 overwrite를 거부한다. V1 task도 재생성을 거부한다.

현재 active resource와 engine identity는 artifact 생성 시점과 달라졌으므로 replay provenance hash는 의도적으로 달라진다. `verifyJungleEconomyOffParity`는 이 차이를 제외하고 기존 세 OFF profile의 9개 configuration hash, Draft/final assignment identity, complete timeline hash, Random draw count/trace hash, winner/duration/event/snapshot count가 공식 V2와 exact equality인지 검증하며 9/9 clean pass했다.

Jungle Tempo production code를 추가하기 직전에는 V1-A clean full을 재사용한 동일 production tree에서 `PRE_JUNGLE_TEMPO_RUNTIME_BASELINE_V1`을 먼저 생성했다. Schedule은 기존 네 profile × GEN–T1 G1/G2 + T1–GEN mirror = 12 matches다. Artifact canonical CRLF raw SHA-256은 `17f703a48949b63bf4ca25f4b32be2bc22fac87a439cdd8cb7c18aadc7f82074`, 당시 canonical production guard는 464 files / `674b3148d782e98753bfd9e79f8b5f78b12cc3ecea8a85bc34ff8fbcaa2def0d`, engine은 V2다. JSON bytes는 동봉된 `SHA256SUMS.txt`로 고정한다.

V1-B production tree에서 `verifyPreJungleTempoParity`를 실행한 결과 12/12 configuration/Draft/final assignment/complete timeline/Random fingerprint/winner/duration/event/snapshot exact parity가 통과했다. Batch C engine V4/V5와 P1 determinism V6 tree에서도 같은 12/12 exact gameplay parity를 다시 통과했다. Baseline engine V2와 해당 report 생성 당시 engine V6가 다르므로 replay provenance hash만 의도적으로 달랐으며, historical report SHA-256은 `a38b16811a3b74f5fe8958bce5eb5b8e1310ba18795af46ea016f705bbec22c9`다.

V1 JSON의 raw SHA `2dcf67a3501200f0bce3de6239dcfbed3b27bafdc9287940f3f56171223a1d71`은 그대로 유효하다. 다만 V1 생성 뒤 별도 JVM 재생성에서 `Set.copyOf(EnumSet)` iteration이 player skill별 seeded draw 배정을 바꾸고 Champion Power tag order가 snapshot summary를 바꾸는 두 cross-process 비결정성을 발견했다. 따라서 V1은 당시 실행의 immutable 기록으로 보존하되 공식 replay/timeline oracle로 사용하지 않고 V2가 이를 대체한다. V2는 canonical enum order를 production contract로 고정하고 두 별도 JVM에서 문서와 9개 timeline이 모두 동일함을 확인했다.

### HTTP match runtime

Spring `MatchController`에 실제 주입되는 기본 roster는 계속 `DummyDataFactory` legacy/demo team이다. Fixed-seed HTTP test는 실제 KILL event가 기존 display fields와 additive `player-fixture-*` ID fields를 함께 직렬화함을 검증한다. Match preflight는 양 팀 전체의 duplicate stable `PlayerId`를 gameplay Random 전에 거부한다. 별도 `RealDraftMatchOrchestrator`가 real backend flow를 제공하지만 HTTP default path로 전환하지 않았다.

Legacy `POST /api/matches/simulate`의 autowired simulator path는 lane/gank/roam/objective/macro/progression/Champion Power를 활성화하지만 `ChampionMatchupMode.OFF`와 `TeamCompositionGameplayMode.OFF`를 사용한다. 이 exact snapshot은 `BASELINE_V1`이다. Real Match API의 `PRODUCTION_MATCHUP_COMPOSITION_V1`, `SimulationOptions.productionDefaults()` 및 `FULL_SYSTEM_CANDIDATE_V1`의 Matchup `GEOMETRIC_V2`와 Composition `PRODUCTION_V2`와 구분해야 한다.

## Implemented

- strict additive League HTTP commands/views, durable idempotency, 202 background execution,
  Player reconciliation과 restart/typed-failure/global-lease/cancel hardening을 제공하는 AI League V1 Batch 5
- local single-node H2/Flyway migration, durable League lifecycle/job/lease/checkpoint,
  V2 receipt/outbox/application ledger와 restart recovery를 제공하는 AI League V1 Batch 4
- backend-valid no-decisive cancel evidence를 허용하고 committed 관측 뒤 replay-only identity를 유지하는 Series frontend 최종 계약
- frozen Hybrid/Spectator mode, 10팀 90-fixture schedule/side/seed, exactly-once receipt ledger와 six-step standings를 제공하는 AI League V1 pure domain
- Hybrid/Spectator Season mode, managed fixture batch exclusion, durable Player Series handoff, unified completion receipt, standings atomicity와 restart recovery까지 제품 결정을 동결한 AI League V1 구현 계약
- managed `PLAYER_CONTROLLED` fixture를 frozen canonical binding으로 기존 Series/Player Draft/Production V9에 연결하고 Auto/Player V2 receipt와 process-local exactly-once completion을 제공하는 AI League V1 Batch 3 handoff
- versioned Champion Catalog/Power/Matchup/Composition/Jungle resources와 coherent manifest loading
- `PlayerId` value object와 explicit 50-record identity resource/catalog
- `PlayerRatingCatalog`의 기존 roster-key lookup 및 additive PlayerId dual lookup
- exact-SHA proficiency resource loader, semantic/cross-catalog validation, sparse PlayerId catalog
- stable `PlayerId`를 보유한 `Player`/`PlayerState`, match-scoped `PlayerKey`, structured `TeamState` lookup
- KILL/action event의 stable participant IDs와 기존 display fields의 additive 분리
- 10개 실제 LCK 팀의 deterministic assembly 및 GEN 대 T1 same-seed simulator smoke
- ChampionId presence와 target-role completion을 분리한 537-key reachability diagnostic와 JSON/CSV/SHA report
- coherent default player catalog graph, exact resource semantic envelope, match-wide stable PlayerId invariant
- real LCK Team→DraftEngine→final role→MatchChampionAssignments→seeded MatchSimulator application orchestration
- field-complete closed-set runtime profiles, instrumentation isolation, configuration/replay/timeline hashing
- ID-only factory boundary와 exact registry-membership 재검증으로 막은 fabricated resolved-profile 우회
- 10개 raw resource identity와 roster/series/Draft/final assignment, engine/rules version을 포함한 structured execution provenance(역사적 V6/V8 evidence와 current V9)
- gameplay와 격리된 per-match Random draw count/trace hash와 observed/plain exact timeline parity
- canonical PlayerSkill realization 순서와 enum-set timeline serialization
- clean full regression 뒤 생성하고 별도 JVM에서 재검증한 9-match Pre-Jungle V2 baseline
- match-scoped `JungleEconomyState`, unified `JungleEconomyOutcome`, structured execution stats와 `Champion Clear × pure Jungle Resource Management` CS/gold/XP V1-A candidate
- candidate pure-JRM/PATHING orthogonality, complete skip-reason Random non-consumption, progression-OFF CS/gold, canonical diagnostic map order와 real GEN–T1 candidate replay smoke
- historical OFF branch의 player iteration/Random 순서를 보존하고 공식 Pre-Jungle V2 9경기와 exact parity를 검증하는 전용 diagnostic
- Tempo production 수정 전에 고정한 four-profile/12-match `PRE_JUNGLE_TEMPO_RUNTIME_BASELINE_V1`과 V1-B 이후 12/12 exact parity diagnostic
- match-scoped `JungleTempoState`, bounded efficiency/credit/readiness/action-cost rules, structured readiness/consumption diagnostics
- Economy V1-A의 actual successful outcome만 credit으로 연결하고, gank/counter-gank base eligibility 뒤·trigger Random 전에 readiness를 적용하는 V1-B candidate
- actual gank/counter attempt만 side별 credit을 소비한다. Tempo-not-ready/ineligible/duplicate path는 state와 trigger Random을 보존하고, eligible failed-trigger fallthrough는 trigger Random만 소비하면서 credit, action state와 downstream Random을 보존한다.
- Jungle Gank의 unavailable/cooldown/no-lane/not-ready와 Counter Gank의 death/cooldown/activity/not-ready를 reason별·side별 immutable match diagnostic으로 노출하는 structured eligibility
- action/death/recovery/macro FARM block의 CS/FARM gold/XP/passive/Random 경계, priority/fallthrough/one-major-combat, reward/event integrity, fresh state와 same-seed replay를 묶고 same-kind duplicate attempt까지 검출하는 `JUNGLE_V1_FOCUSED_HARDENING` gate
- 다섯 번째 closed profile `FULL_SYSTEM_WITH_JUNGLE_TEMPO_CANDIDATE_V1`, rules V1, 고정 configuration hash와 historical engine provenance V6
- 실제 10개 LCK 팀의 90 G1 + 10 Hard Fearless G2 fixture, calibration/holdout seed split, frozen input rejection, full diagnostics integrity/hash와 fixed-Draft 5-profile JSON/CSV/SHA 출력을 고정한 hardened Final 13G-B1 test-side audit harness
- 명시적 PlayerKey 순서의 Champion Power 평균, fresh-JVM 2회 B1 artifact 7/7 byte equality와 canonical B1 manifest binding gate
- calibration-only 12,000경기, fixture-atomic resume, 100 replay exact, fixed-time Jungle 관측과 paired marginal/SHA artifact를 갖춘 Final 13G-B2 audit pipeline
- 사전 동결 contract, one-time authorization, 4 fresh-JVM receipt, 100 authenticated checkpoint와 4,000-row paired evidence를 갖춘 Final 13G-B3 holdout pipeline
- B2/B3 manifest를 다시 검증하고 player/champion/team/fixture 민감도를 분리해 당시 baseline runtime 유지와 Match Engine V1 freeze readiness를 고정한 historical Final 13G-B production decision
- historical `BASELINE_V1` policy에서 시작해 complete immutable roster/Draft input, final-snapshot summary, immutable structured timeline, mandatory provenance와 cross-JVM hash를 하나의 additive application facade로 고정한 Match Engine V1
- 실제 LCK 10팀/50명 options, explicit seed, automatic Draft, frozen result/timeline/provenance와 strict 오류 계약을 제공하는 additive Real Match API V1
- C1-only 개발 실행을 제거한 hardened `bootRun`과 production-equivalent Auto Draft/JFR/counter scalability 기준선
- 표준 gzip 협상, identity fallback, 실제 wire byte/CDP 측정과 live Chrome first/warm E2E를 통과한 Real Match transport compression V1
- explicit team-code/roster/rating/final-role/assignment/Hard Fearless preflight와 caller-owned series commit
- seed 기반 Match Simulation, event/snapshot timeline, common kill/reward/death path
- lane pressure/combat, gank/counter-gank, roam, position economy, progression
- Dragon/Baron/Elder, objective decision/contest/trade, structure/Nexus end game
- Champion Power, `GEOMETRIC_V2` Matchup, full Composition analysis와 `PRODUCTION_V2` decision channel
- current V9에서 90 G1 + 10 Hard Fearless G2, fresh 8 calibration/4 holdout seed, 고정 Draft와 세 profile 3,600-row paired evidence를 생성한 Matchup/Composition 재검증 pipeline
- V9 Matchup final structure 차이를 HP-only와 lane/inhibitor/Nexus progression으로 분리하고 side/timing/source/local reachability 및 four-layer acceptance proposal을 생성한 800-row attribution diagnostic
- 알려진 causal-lineage limitation과 민감도를 명시적으로 수용하고 `PRODUCTION_MATCHUP_COMPOSITION_V1`을 Real Match/API/default orchestration에 적용한 V9 Matchup/Composition production activation
- Draft planning, candidate generation, bounded search, final flex-role assignment와 Hard Fearless history
- champion catalog/match simulation API와 React timeline UI

## Partial / Disabled

- AI League V1 Batch 1~5의 domain, Auto/Player runner/handoff, local single-node DB durability,
  bounded worker/job, outbox/restart recovery와 League API는 구현됐다. Frontend, auth/ownership,
  external broker와 multi-node worker/DB consensus는 아직 없다.
- Real LCK Draft→Match flow는 Frontend V1-B의 기본 LIVE 공급자와 연결됐다. Reference는 명시적 회귀 모드로만 남고 자동 fallback하지 않는다.
- Full response의 decoded JSON은 현재도 20–34MB지만 gzip wire body는 공식 외부 HTTP에서 약 1.88–2.79MB로 줄었다. JSON projection/streaming, parse·validation·heap을 분리하는 worker, 정확한 progress는 별도 후속 범위다.
- Ban API entry에는 presentation metadata가 없어 frontend가 structured ChampionId에서 portrait asset을 보완한다.
- 기존 `POST /api/matches/simulate`는 호환성을 위해 Dummy roster와 legacy timeline 경로를 계속 사용한다.
- Real Match API와 profile 인자 없는 application orchestration은 Matchup `GEOMETRIC_V2`, Composition `PRODUCTION_V2`다. Legacy `POST /api/matches/simulate`만 호환성을 위해 Matchup/Composition `OFF`인 Dummy roster/autowired simulator를 유지한다.
- 이전 V9 fresh holdout에서 Matchup은 Blue WR +2.0pp와 legacy structure changed 31.5%로 당시 fallback macro gate를 넘었고, Composition은 승인 application 0회/local-cause 위반 60회 및 incremental macro gate 실패로 당시 production 부적격 판정을 받았다. 후속 Matchup attribution은 새 표본에서 any final state 32.75% 중 HP-only 26.0%, 실제 destruction/progression 6.75%로 분해했지만 local application provenance가 없어 `MATCHUP_V9_STRUCTURE_ATTRIBUTION_BLOCKED_BY_DIAGNOSTIC_GAP`이었다. 이 historical 권장은 현재 product-owner activation decision과 구분해 보존한다.
- Fresh Auto Draft 기반 V9 재검증 V1은 Draft post-review hardening과 Matchup local application provenance를 구현하고 calibration 100 checkpoint/400 Draft/1,200 core rows를 생성했다. Raw sidecar는 100/100 정상이지만 fresh-JVM checkpoint reload가 unordered Set canonical digest를 거부해 `MATCH_ENGINE_V9_AUTO_DRAFT_FRESH_REQUALIFICATION_BLOCKED_HOLDOUT_NOT_CONSUMED`으로 종료됐다. 당시 holdout과 새 candidate eligibility verdict는 0이었다.
- V2는 signed payload canonical List, typed/raw-tree fresh-JVM byte equality, actual combat action identity, lane-pressure mutation/version lineage, logical duplicate slot과 payload 분리, standalone finalizer zero-simulation 경계를 구현했다. Calibration은 checkpoint/sidecar 100/100, Draft 400, core rows 1,200, replay/instrumentation 각 300을 완성했고 exact integrity와 Composition causal gate를 통과했다. 그러나 Matchup public divergence 400쌍 중 direct 1, indirect 0, unresolved 399여서 `MATCH_ENGINE_V9_FRESH_REQUALIFICATION_V2_BLOCKED_HOLDOUT_NOT_CONSUMED`으로 종료됐다. Holdout authorization/start/consumption은 0이며 현재 activation은 이 limitation을 수용한 별도 제품 결정이다.
- Explicit runtime profiles는 backend orchestration input이며 아직 HTTP/frontend profile selector로 노출하지 않았다.
- Final 13G-B는 Economy `FAIL`과 Tempo `REVIEW_REQUIRED`를 보존한 채 Production V1을 `KEEP_CURRENT_RUNTIME_DEFAULT`로 결정했다. 두 candidate profile은 감사/향후 설계용으로 남지만 runtime 기본값에는 활성화하지 않는다.
- Jungle Economy V1-A는 economy-only candidate profile에서 계속 CS/gold/XP에만 연결된다. V1-B Tempo candidate는 별도 profile에서 bounded readiness를 gank와 counter-gank eligibility에만 연결한다. Objective eligibility/확률/reward에는 직접 연결하지 않았고 Production V1 tuning은 수행하지 않았다.
- 완전 자동 `DraftEngine`은 계속 application component 내부 pure domain dependency다. 별도 `PlayerControlledDraftEngine`만 V1 session API용 Spring bean으로 조립된다.
- 첫 game은 exclusion이 없어 단판처럼 동작하지만 별도 Standard ruleset 선택 기능은 없다.

## Pending

1. Player Draft completed transcript의 fail-closed authority/legal-pool/AI-trace/final-role 의미를 보존하면서 simulation input validation의 반복 계산을 줄이는 `PLAYER_DRAFT_SIMULATION_INPUT_VALIDATION_PERFORMANCE_HARDENING_V1`을 설계한다.
2. ACTIVE session response의 full legal pool/advisory 재계산을 줄이는 `PLAYER_DRAFT_SESSION_PROJECTION_PERFORMANCE_HARDENING_V1`을 우선 검토하고, 이후 `PLAYER_DRAFT_AI_TURN_PERFORMANCE_HARDENING_V1`을 별도 진행한다.
3. Activated production의 side별 winner, structure/Nexus progression, 경기 시간과 runtime integrity/validation 오류를 structured field로 관찰하고 Composition Nexus/ending 민감도를 검토한다.
4. Wire gzip 이후에도 남은 20–34MB decoded JSON과 parse/validation/heap 비용을 줄이려면 compact projection, streaming 또는 worker parsing을 별도 additive 계약으로 설계한다.
5. AI League V1 Batch 1~5는 완료됐다. 다음 `AI_VS_AI_LEAGUE_SIMULATION_V1_FRONTEND`에서
   dashboard, standings, Auto progress와 frozen Player Series handoff/reconciliation UI를 구현한다.
6. Ban champion presentation/catalog를 additive API field로 제공해 frontend asset fallback을 제거한다.
7. Economy를 변경하거나 Tempo V2를 설계한다면 이미 소비한 seed를 새 candidate의 검증 표본으로 재사용하지 말고 새 contract/calibration/holdout을 만든다.
8. Objective eligibility/reward 직접 연결은 별도 설계·검증 전까지 보류한다.
9. Matchup lane-pressure mutation version이 downstream action eligibility/score/selection에서 실제 소비될 때 exact consumer action ID까지 이어지는 lineage를 완성한다. 다음 official 실행은 새 versioned contract와 비중첩 fresh seed로 수행한다. `UNRESOLVED_SNAPSHOT_CAUSE` exact-zero gate를 완화하거나 실패 finalizer를 성공할 때까지 재시도하지 않는다.

## Test Snapshot

Final command:

```text
gradlew.bat test --console=plain --no-daemon
```

AI League V1 Batch 5 API and job-boundary hardening final executable tree의 current complete backend
regression 결과는 다음과 같다.

| 항목 | 결과 |
| --- | ---: |
| JUnit suites | 256 |
| Tests | 2,333 |
| Failures | 0 |
| Errors | 0 |
| Skipped | 2 |
| Aggregate JUnit XML time | 1,139.817 seconds |
| Gradle wall duration | 19m 13s |
| Build | `BUILD SUCCESSFUL` |

Affected focused lane은 Phase A 네 결함, strict League API/HTTP/gzip, Batch 1 domain/standings,
Auto Production V9 1 fixture, Player Production V9 1 fixture, Series/Player Draft/Real Match API와
transport parity를 포함해 16 suites / 62 tests를 2분 25초에 통과했다. 원자 fencing completion
직접 영향 5 suites / 20 tests도 1분 22초에 재통과했다. 첫 clean full 뒤 발견한 runtime recovery
연결 누락은 3 suites / 12 tests로 확인했고, production 변경 때문에 두 번째 full을 실행했다.
이 두 번째 full 이후에는 문서만 갱신했으므로 반복하지 않았다. 두 skip은 기존
explicit 대형 diagnostic이다. Frontend build/Playwright, 90-fixture official run과 대형
balance/calibration/holdout diagnostic은 실행하지 않았다.

아래 수치는 각 historical milestone 당시의 snapshot이다.

기본 `diagnostic` tag 제외 정책을 유지한 최종 production/runtime tree의 complete backend regression이다.

| 항목 | 결과 |
| --- | ---: |
| JUnit suites | 226 |
| Tests | 2,232 |
| Failures | 0 |
| Errors | 0 |
| Skipped | 0 |
| Aggregate JUnit XML time | 1,034.299 seconds |
| Gradle wall duration | 17m 29s |
| Build | `BUILD SUCCESSFUL` |

Player-controlled Draft API V1 final boundary hardening이 포함된 final executable production tree의 새 complete regression이다. 첫 실행에서 226 suites / 2,232 tests가 clean pass했다. 직전 226 suites / 2,230 tests 결과를 재사용하지 않았다. Public mixed boundary가 team code만 받아 authoritative roster를 내부 조립하고 active Draft Meta identity 세 필드를 exact 검증하는 focused lane은 5 suites / 36 tests가 clean했다. Session memory/capacity/receipt/retry와 API schema는 변경하지 않았고 full-auto factory, profile, gameplay/Random semantics도 변경하지 않았다. Full pass 뒤에는 executable production Java/resource/Gradle/shared fixture를 바꾸지 않고 문서만 갱신했다. Frontend, 대형 statistical diagnostic, calibration/holdout과 historical artifact 재생성은 실행하지 않았다.

Activation/profile/policy/API/reachability focused는 10 suites / 58 tests, 2분 56초에 통과했다. 직접 영향 gameplay invariant는 9 suites / 101 tests, 7초에 통과했다. Frontend production build는 87 modules, 약 8초에 성공했고 LIVE options/simulate gzip 및 Draft→Playback→Result 브라우저 smoke도 통과했다. 대형 calibration/holdout/B2/B3/13G-B/population task와 activation artifact bundle은 실행·생성하지 않았다.

Jungle V1-B는 18개 default correctness test를 추가했고, affected focused regression은 기존 gank/counter/lane/FARM/simulator/Random/real Draft 경로까지 포함해 1분 52초에 clean pass했다. 새 경계는 first/repeat readiness, efficiency clamp, bank cap, continuity reset, duplicate/backward time, ineligible Random 0회, failed-trigger의 eligible trigger Random 소비와 credit/action-state 보존, lane fallthrough, no-kill actual consumption, counter-gank side별 consumption, diagnostics ON/OFF complete timeline equality와 real GEN–T1 replay를 포함한다.

Jungle V1 focused hardening은 새 2개 class의 11개 cross-system test를 포함한다. Expanded focused regression은 17개 class / 168 tests이며 structured gank/counter eligibility, death/activity 분리, Random 0회, trigger/attempt/consumption algebra, action/death/recovery/macro FARM opportunity cost, passive gold, reward/event integrity, multiplicity-sensitive one-major-combat, match isolation과 same-seed replay를 포함한다.

Batch C의 `verifyPreJungleTempoParity`는 기존 네 profile 12/12 exact pass했고 V4/V5 report SHA-256은 각각 `6ba6d0c33332fa4a6eef343a9030fd3f031f6cef3a6d0363c6877db25fb5878f` / `87577b6c26073fb748ab6d9b9d2bda719437c60381ba99f71b07482bcdeca927`다. V6도 12/12 exact pass했고 current report SHA-256은 `a38b16811a3b74f5fe8958bce5eb5b8e1310ba18795af46ea016f705bbec22c9`다. `runJungleTempoCandidateDiagnostic`의 historical 고정 12-seed 관찰에서는 Economy-only 15 gank/3 counter-gank, Tempo 15 gank/2 counter-gank가 발생했고 Tempo의 structured consumption은 각각 15/2로 actual action과 exact equality였다. 이는 calibration이나 production gate가 아니며 Batch C 전후와 V5 report SHA-256은 모두 `3f94b100464a48181fccf5a04a5e16f62dea3e17a05b1398215f65afddab1199`로 byte-identical하다.

Batch C V4 final full 전후 canonical production source/resource/build guard는 모두 472 files / `54b53ea30453e39791dd8aa0197e95ed190697ce1b83c010baff1df540c833d9`로 동일했다. V5 follow-up은 2개 경계 테스트와 activity reason 분리를 반영한 뒤 final full을 첫 실행에서 166 suites / 1,953 tests clean pass했다. Full 뒤에는 production/shared fixture를 바꾸지 않고 one-major-combat marker 분류만 assertion-only로 좁혔고 affected 5 tests가 clean pass했으므로 full 결과를 재사용했다. 직전 V1-B guard는 471 files / `143112d499c9731e25de80edf2883621c7bb9c3c948907f4a0c8d0146093a260`이었다. Baseline byte-integrity correction과 V5 follow-up 모두 immutable baseline을 재생성하지 않았고 세 baseline raw SHA는 기존 `SHA256SUMS.txt`와 exact equality다.

Final 13G-B1 hardening의 schedule/profile focused verification은 2 suites / 7 tests, failures/errors/skipped 0으로 통과했다. `runPhase13GB1DryRun`은 1 suite / 1 test, failures/errors/skipped 0, JUnit suite 18.445초, Gradle wall 33초에 통과했고 generated SHA manifest 6/6을 검증했다. Schedule hash 위조와 임의 prepared fixture 생성 경로를 닫고, 누락됐던 전체 simulator diagnostics replay equality와 domain별 structural integrity, Tempo positive reachability를 고정했다. 이어 B1의 Gradle/test-harness 변경까지 포함한 complete backend full을 첫 실행에서 168 suites / 1,960 tests / failures 0 / errors 0 / skipped 0, aggregate JUnit XML 540.965초, Gradle wall 9분 15초로 clean pass했다. Canonical main source/resource/build guard는 472 files / `692cc941664ccecfd551a7b08c63f724d055fbfcdbfa721f7aca9ea8cbfd8458`로 B1 전과 같고, hardened audit harness guard는 9 files / `679ce37d608f83a4ffe7a8f708dbab882d8f20044817c98eeeed09716f03b130`이다.

Checkpoint authenticity/fresh-JVM hardening final tree의 default full regression은 첫 실행에서 169 suites / 1,964 tests / failures 0 / errors 0 / skipped 0, aggregate JUnit XML 497.860초, Gradle wall 8분 28초로 clean pass했다. Production replay hash canonical 함수의 package 경계, B1 harness provenance context, Gradle `forkEvery=1`까지 모두 반영한 final executable tree에서 수행했으며 이후에는 generated artifact와 문서만 갱신했으므로 full을 반복하지 않았다. 최종 smoke는 1 suite / 1 test, JUnit 15.121초로 재라벨링·outcome·observation·checkpoint bytes 변조 거부와 synthetic non-official status를 확인했다. Cross-JVM probes는 각 1 suite / 1 test와 17.020/16.786초, canonical B1 dry-run은 17.361초로 모두 clean했다.

Phase-specific guard로 재고정한 공식 `runPhase13GB2Calibration`은 P1 cross-JVM → B1 dry-run → `forkEvery=1`인 4 fresh-JVM calibration workers → receipt-bound artifact finalizer 순으로 20분 4초에 clean pass했다. 서로 다른 JVM identity 4/4, authenticated fixture checkpoint 100/100, checkpoint payload digest 100/100, 12,000 calibration rows, 12,000 paired marginals, 100 exact replay, holdout 0과 SHA manifest 16/16을 확인했고 기존 B2 balance signal과 exact equality였다.

Final 13G-B3 final executable tree에서는 B1/B2/B3 contract focused tests가 clean pass했고 `runPhase13GB3Smoke`는 reserved holdout seed 없이 1 suite / 1 test, failures/errors/skipped 0, JUnit 14.262초, Gradle wall 26초로 통과했다. 이어 default full regression은 첫 실행에서 170 suites / 1,969 tests / failures 0 / errors 0 / skipped 0, aggregate JUnit XML 488.240초, Gradle wall 8분 23초로 clean pass했다. Phase-specific guard에 맞춘 B2 재고정은 20분 4초, frozen contract 생성은 16초, one-time `runPhase13GB3FrozenHoldout`은 22분 32초에 성공했다. 이후 executable source/resource/Gradle은 바꾸지 않고 artifact 검증과 문서만 갱신했으므로 full을 반복하지 않았다.

Final 13G-B hardening은 3 suites / 14 focused tests, failures/errors/skipped 0으로 통과했다. Actual registry/RealDraft/Spring/HTTP wiring의 fixed-seed parity와 synthetic 6,400-row full-write/negative E2E를 확인했다. 독립 Java 17 compile/run은 두 output directory에서 같은 manifest `bd9a9cf3b089cfc76fceb0311094c1b70232278404f5675c42d89849d927bc98`와 7/7 byte-identical files를 만들었고 final manifest의 6/6 raw SHA가 통과했다. 이 후속 작업은 production Java/resource/Gradle/shared fixture를 바꾸지 않은 isolated test-side consumer와 문서 변경이므로 위 B3 clean full을 재사용했다.

Match Engine V1 focused/cross-JVM/affected regression은 8 suites / 42 tests, failures/errors/skipped 0으로 통과했고 최종 보강 뒤 핵심 2 suites / 12 tests도 다시 통과했다. Production final tree의 complete backend regression은 첫 실행에서 175 suites / 1,995 tests / failures 0 / errors 0 / skipped 0, aggregate JUnit XML 628.861초, Gradle wall 10분 39초로 clean pass했다. Artifact writer는 historical Final manifest 6/6, 실제 legacy/V1 gameplay/common provenance parity, V1 replay input 결속과 fresh-JVM A/B equality를 다시 확인하고 manifest 7/7을 생성했다. 이후 executable production/resource/Gradle/shared fixture는 바꾸지 않고 artifact와 문서만 갱신했으므로 full을 반복하지 않았다.

Historical Real Match API V1 최초 hardening은 8 suites / 50 tests와 complete backend 180 suites / 2,016 tests를 clean pass해 당시 V6 handoff를 만들었다. 이후 production이 V7과 player-rating runtime V8로 진화했지만 generated local handoff와 문서가 자동 갱신되지 않아 V7/V6 evidence가 남아 있었다.

당시 V8 handoff refresh에서는 binary font를 UTF-8 text로 읽던 backend test scanner를 명시적 text-source 경계로 고쳐 영향 focused 6 suites / 214 tests를 clean pass했다. API focused는 V8 ability profile projection을 포함해 9 suites / 55 tests, failures/errors/skipped 0으로 통과했다. Final executable tree의 complete backend regression은 첫 실행에서 196 suites / 2,091 tests / failures 0 / errors 0 / skipped 0, aggregate JUnit XML 810.092초, Gradle wall 13분 43초로 clean pass했다. Clean XML과 당시 source binding을 검증한 두 fresh JVM candidate는 7/7 byte-identical이었고 V8 semantic audit 및 official manifest 6/6을 통과했다. 이후에는 generated artifact와 문서만 갱신했으므로 full을 반복하지 않았다. Frontend/npm/Playwright, B2/B3/Final 13G-B, 대규모 diagnostics와 baseline generator는 범위 밖이라 실행하지 않았다.

Real Match runtime hardening final executable tree는 focused Draft decomposition/JFR ON-OFF/same-JVM counter, 20-turn/BAN-PICK schedule, 기존 performance manifest 4/4, artifact tamper, bootRun contract와 두 fresh JVM cross-process identity를 통과했다. Complete backend regression은 단 한 번 실행해 201 suites / 2,106 tests / failures 0 / errors 0 / skipped 0, aggregate JUnit XML 1,125.077초, Gradle wall 19분 2초로 clean pass했다. 그 뒤 executable source를 바꾸지 않고 네 fresh runtime HTTP observation과 12-fixture×2 measured Draft audit만 생성했다.

Match Engine V9 Matchup/Composition 재검증은 focused contract 3 tests와 G1/G2 three-profile smoke를 clean pass한 뒤, build/test configuration이 추가된 final executable tree에서 complete backend regression을 206 suites / 2,135 tests / failures 0 / errors 0 / skipped 0, Gradle wall 15분 11초로 한 번 실행했다. 4 fresh-JVM shard의 calibration 2,400 rows와 one-time frozen holdout 1,200 rows는 각각 authenticated checkpoint 100/100과 receipt 4/4를 만들었다. 3,600 rows 전체 exact integrity는 timeout/structure/Nexus/post-finish/Random/SUPPORT CS 오류 0이다. Fresh JVM A/B가 생성한 19개 official file은 byte-identical이고 manifest raw SHA는 `0cdcfa002882c57eaa13b5f5cee160eccc0d2ae49aa773267b81ef37ac2a6b5f`다. 최종 권장은 `RECOMMEND_BASELINE_V1`이며 executable production Java/resource/API/frontend는 바꾸지 않았다. 자세한 결과는 [V9 Matchup/Composition 재검증](development/match-engine-v9-matchup-composition-requalification-v1.md)을 따른다.

Matchup V9 structure-effect attribution은 contract/classifier/source/checkpoint/artifact focused 6 tests와 Structure/Match Engine/Matchup affected focused regression을 clean pass했다. 최종 current-source 4-JVM aggregate task는 13분 17초에 core 800 rows, baseline replay 100, diagnostics parity 200을 clean pass했고 400 paired input exact, Random/timeline parity, correctness exact-zero gate를 최종화했다. Contract/harness SHA는 `84d96de3bbcf0460481593eae6d7909b4ebd8fc0ae8ccf4b44a63d6240c4b477` / `6db556133c8102e1905d3d0645af0a7ec61d68fa810fa7f42c905aa627f48e4a`, artifact manifest raw SHA는 `5ee0cec10b464117421ce347235ec651b94daaaadb8e6d94b6ca39daf660410c`다. Build/test configuration과 shared source-identity helper가 추가된 최종 executable tree의 complete backend regression은 첫 실행에서 207 suites / 2,140 tests / failures 0 / errors 0 / skipped 0, aggregate JUnit XML 754.243초, Gradle wall 13분 8초로 clean pass했다. 이후에는 diagnostic 재현과 문서 수치만 갱신했으므로 full을 반복하지 않았다.

Match Engine V9 fresh Auto Draft 재검증은 final source-bound backend regression 217 suites / 2,194 tests / failures 0 / errors 0 / skipped 0, aggregate XML 721.031초, Gradle wall 12분 6초와 frontend build를 clean pass했다. Official calibration 네 shard는 100 authenticated checkpoints, production Auto Draft 400개, core rows 1,200개, replay/instrumentation 각 300개를 만들었다. Artifact-only fresh-JVM finalizer가 첫 checkpoint의 deserialize/reserialize payload digest를 거부해 holdout authorization은 생성되지 않았다. Raw checkpoint sidecar는 100/100 exact였고 실패 원인은 signed `divergenceActionIds` unordered Set의 JVM별 순회 순서다. Production profile은 `BASELINE_V1`이고 candidate verdict는 `BLOCKED_BY_CALIBRATION_EVIDENCE_INTEGRITY`다. 상세 내용은 [fresh 재검증 V1](development/match-engine-v9-auto-draft-matchup-composition-fresh-requalification-v1.md)을 따른다.

V2 causality/evidence hardening의 focused 92 tests, serialization fresh-JVM A/B, Auto Draft probe A/B, DRY_RUN smoke와 baseline parity는 clean pass했다. 당시 최종 backend regression은 219 suites / 2,200 tests / failures 0 / errors 0 / skipped 0, Gradle wall 14분 43초였다. Official calibration은 4개 서로 다른 worker JVM, authenticated checkpoint/sidecar 100/100과 receipt 4/4, Draft 400, core rows 1,200, paired rows 800, replay/instrumentation 각 300을 만들었다. Artifact-only calibration finalizer는 gameplay execution 0을 증명했지만 Matchup direct/indirect/unresolved/unexplained가 1/0/399/0이어서 holdout을 승인하지 않았다. 상태는 `MATCH_ENGINE_V9_FRESH_REQUALIFICATION_V2_BLOCKED_HOLDOUT_NOT_CONSUMED`였고 당시 runtime은 `BASELINE_V1`이었다. 상세 내용은 [fresh 재검증 V2](development/match-engine-v9-auto-draft-matchup-composition-fresh-requalification-v2.md)을 따른다.

Matchup/Composition production activation은 위 V2 limitation을 숨기거나 holdout 통과로 재명명하지 않고 `PRODUCT_DECISION_ACCEPT_WITH_KNOWN_DIAGNOSTIC_LIMITATION`을 policy/API에 결속했다. Final executable tree는 219 suites / 2,201 tests / failures 0 / errors 0 / skipped 0, aggregate XML 1,821.280초, Gradle wall 16분 25초로 clean pass했다. LIVE GEN–T1 seed 73 smoke는 production profile과 gzip, 20-turn Draft, playback/result, output hash `b74cd4a509134fc5a1b8cb9aa458e6a86233f407ce59084856aa370a80a33481`을 확인했다. 별도 activation artifact와 대형 statistical task는 실행하지 않았다.

Player Draft interactive/simulation performance hardening V1은 revision-bound authoritative projection과 server-owned immutable completion binding으로 ACTIVE 응답과 completed Draft의 중복 계산을 제거했다. Paired backend에서 validation 99.954%, first simulate 85.811%, retry 85.843%, projection 99.789%가 감소했고 actual Chromium에서 action DOM 중앙값 37.241%, BLUE/RED simulate DOM 75.671%/60.805%가 감소했다. Draft/API/Production V9/Random exact parity와 standalone/Series fail-closed 경계는 유지했다. Focused 7 suites / 44 tests, frontend contract 33 scenarios, production build 101 modules를 통과했고 final executable tree의 complete backend regression은 첫 실행에서 238 suites / 2,274 tests / failures 0 / errors 0 / skipped 2, Gradle wall 14분 43초로 clean pass했다. 두 skip은 explicit paired diagnostic이다. 다만 projection을 제외한 `actionService` 단독 개선율은 13.609%로 25% gate에 미달해 상태는 `PLAYER_DRAFT_PERFORMANCE_PARTIALLY_HARDENED`다. 상세 내용은 [Player Draft performance hardening V1](development/player-draft-interactive-and-simulation-performance-hardening-v1.md)을 따른다.

Match Engine V9 production acceptance는 V3 policy에 acceptance status, 두 ordered risk와 explicit BASELINE rollback identity를 additive하게 결속했다. T1/GEN fixed Draft product sanity는 core 1,200회와 replay/instrumentation/rollback 추가 10회를 실행해 Production Composition scalar 4,885회, structured effect 5,339회와 direct binding 5,339 / indirect 0 / unresolved 0을 관측했고 correctness/Random/Jungle/replay/instrumentation/rollback 오류는 0이었다. Focused 4 suites / 22 tests와 frontend 87-module build가 통과했고, final executable tree의 첫 complete backend regression은 221 suites / 2,203 tests / failures 0 / errors 0 / skipped 0, aggregate XML 859.986초, Gradle wall 14분 29초로 clean pass했다. Raw-only finalizer A/B는 byte-identical이었고 official artifact manifest 12/12, raw SHA-256 `1b3094dc2f59b0c4fb32130c2e5b6bac8060d73c99f4c7c9c696561d59c316e1`을 확인했다. 이 acceptance는 statistical holdout이나 balance proof가 아니다.

Pre-Jungle V2 determinism hardening에서는 세 번째 full regression이 필요했다. 첫 clean full 뒤 별도 JVM artifact oracle이 unordered `PlayerSkill` set iteration으로 seeded realization draw-to-skill 배정이 달라지는 production 결함을 발견했고, 이를 고친 두 번째 clean full 뒤 다시 별도 JVM에서 Champion Power tag summary ordering이 timeline hash를 바꾸는 독립 production 결함을 발견했다. 두 문제 모두 single-JVM focused/full suite만으로 검출할 수 없었으므로 canonical ordering 수정과 cross-JVM 후보 2회 exact equality를 먼저 확정한 뒤 당시 final full을 실행했다.

Pre-Jungle V2 생성 당시 production source/resource/build guard는 456 files / `b7965a1d1ebb9d76f298bc65e957da79c4e7cf2a3d0df35a6eca29ebaa0ab350`으로 동일했다. 당시 공식 V2를 새 JVM에서 같은 SHA로 재생성한 기록은 historical baseline provenance로 계속 보존한다.

Series final frontend contract는 `npm run series:verify`, `npm run player-draft:verify`, `npm run build`, `npm run reference:check`, `npm run reference:verify`, `npm run bundle:verify`를 clean pass했다. Build는 118 modules였고 reference raw SHA-256은 `977c7d6e015f4ebd5ecba8e24e7b95a0a6313fef2e1e69a2c396b4fab36ac15e`다. Backend executable source/resource/schema/Gradle을 바꾸지 않았고 Phase B는 문서 전용이므로 backend full regression과 large diagnostic은 실행하지 않았다.

테스트/diagnostic 실행 경계는 [Testing](development/testing.md), player contract는 [Player System](architecture/player-system.md)과 [Player Data Schema](reference/player-data-schema.md)를 참고한다.

## Last Updated

2026-09-02 (Asia/Seoul)
