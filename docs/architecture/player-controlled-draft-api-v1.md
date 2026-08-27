# Player-controlled Draft API V1

상태: `PLAYER_CONTROLLED_DRAFT_API_V1_FINAL_BOUNDARY_HARDENED`

## Scope

`PLAYER_CONTROLLED_DRAFT_API_V1`은 실제 두 LCK roster 중 한쪽 `TeamSide`의 모든 Draft 턴을 플레이어가 선택하고, 반대쪽은 기존 production Auto Draft가 반응하도록 하는 additive backend 계약이다. Game 1, 빈 Hard Fearless history, professional 5-ban/5-pick sequence만 지원한다. 20턴 완료와 경기 simulation은 별도 명령이다.

기존 `GET /api/v1/real-matches/options`와 `POST /api/v1/real-matches/simulate`는 완전 자동 계약과 응답을 그대로 유지한다. Player Draft 응답은 player action에 가짜 Auto rank/score trace를 붙이지 않기 위해 별도 schema를 사용한다.

## Endpoints

| Method | Path | Meaning |
| --- | --- | --- |
| POST | `/api/v1/player-drafts/sessions` | Game 1 session 생성 후 첫 player turn까지 AI 자동 진행 |
| GET | `/api/v1/player-drafts/sessions/{sessionId}` | 현재 immutable Draft projection 조회 |
| POST | `/api/v1/player-drafts/sessions/{sessionId}/actions` | player champion ban/pick 하나 제출하고 다음 player turn까지 AI 진행 |
| POST | `/api/v1/player-drafts/sessions/{sessionId}/simulate` | 완료된 Draft를 현재 Production V9에서 명시적으로 실행 |
| DELETE | `/api/v1/player-drafts/sessions/{sessionId}` | session 취소 |

Start request는 `PLAYER_DRAFT_START_REQUEST_V1`과 `blueTeamCode`, `redTeamCode`, `controlledSide`, signed-int64 decimal string `seed`를 받는다. Action request는 `PLAYER_DRAFT_ACTION_REQUEST_V1`, non-negative `expectedRevision`, `clientActionId`, `championId`를 받는다. Simulate request는 `PLAYER_DRAFT_SIMULATE_REQUEST_V1` schema만 허용한다. 모든 parser는 알 수 없는 field와 비정규 seed/session/action identity를 거부한다.

## Selection and response

플레이어는 advisory top 3 밖의 champion도 선택할 수 있다. `selectableChampions`가 현재 domain-legal 전체 집합이며 PICK은 현재 feasible roles와 future completion을, BAN은 양 팀 future completion을 유지한다. `advisoryRecommendations`는 기존 search score를 보여 주지만 선택 권한을 제한하지 않는다. 이미 사용된 champion, Hard Fearless exclusion, partial/future role failure와 위험한 ban은 `unavailableChampions.reason`으로 구조화한다.

`PLAYER_DRAFT_SESSION_V1`은 다음을 포함한다.

- session/revision/status, 두 stable team identity, controlled side, seed와 Game 1
- Draft rules/scoring, `AUTO_DRAFT_VARIETY_V1`, `PLAYER_CONTROLLED_DRAFT_V1` identity
- current turn, ordered bans/picks, state hash
- turn별 action과 `AI`/`PLAYER` authority, before/after state hash
- AI 턴의 원래 `DraftSelectionTrace`; PLAYER 턴의 selectable-set/legality/client-action evidence
- 전체 selectable/unavailable champion presentation, feasible role과 advisory recommendation
- 완료 후 final role/player assignment, Draft identity와 control evidence hash

제어 정책 SHA-256은 `8f6488f07c44a6529e88bd022fff3124458a8237cc919bd7dd3e140eaa4a0752`다. Gameplay evidence hash는 `clientActionId`, session ID, revision과 wall-clock 시간을 제외한다. 따라서 동일 gameplay transcript는 HTTP retry identity가 달라도 같은 Draft/control/input/replay identity를 만든다.

## Session and concurrency

저장소는 Spring이 주입한 `Clock`을 사용하는 process-local `ConcurrentHashMap`이며 기본 최대 128 sessions, TTL 30분이다. Repository-owned private capacity lock이 terminal/expired cleanup, 현재 entry 수 확인, ID collision 확인과 등록을 하나의 경계로 묶으므로 concurrent create 중에도 128개를 초과하지 않는다. Lock은 session 생성에만 사용하며 서로 다른 session의 action/simulation mutation을 전역 직렬화하지 않는다. Static mutable gameplay state는 없다. 서버 재시작 복구, database, auth와 multi-node coordination은 V1 범위 밖이다.

플레이어 action과 이어지는 AI advance는 map의 단일 atomic mutation이다. 같은 `clientActionId`와 같은 revision/champion payload를 재시도하면 최초 logical response를 돌려준다. 같은 ID의 다른 payload, stale revision, 완료/취소 session action은 409다. 같은 revision의 concurrent action은 하나만 성공한다. 잘못된 action은 revision/state/evidence를 바꾸지 않는다.

Expiry와 cancelled entry의 물리 제거는 scheduler 기반 eager deletion이 아니라 다음 session create 시 수행하는 lazy eviction이다. `get`/`mutate`는 현재 `Clock`으로 expiry를 판정하며, lazy tombstone에는 full match result가 아니라 작은 Draft/session data와 아래 compact receipt만 남을 수 있다. Cleanup 뒤 capacity는 즉시 재사용된다.

## Match execution and evidence

20턴 완료 시 `FinalRoleAssignmentResolver`가 flex role을 확정하지만 simulation을 자동 시작하지 않는다. `/simulate`는 turn 1부터 다음을 새로 검증한다.

1. BLUE/RED team code를 authoritative LCK five-position stable-`PlayerId` roster로 해석하고 unknown/same team을 거부
2. completed result의 Draft Meta version과 required/actual legal-role hash를 current active resource와 exact 비교
3. rules/side/action/champion/authority와 before/after state hash
4. PLAYER turn의 현재 full selectable-set identity와 accepted legality
5. AI turn의 authoritative Auto policy/pool/weight/context/bucket trace
6. final ban/pick state, legal role permutation과 exact player assignment

Raw `PlayerControlledDraftResult`에서 Match Engine input으로 가는 public production 경계는 `PlayerControlledDraftMatchInputBoundary.validateAndCreateInput` 하나다. Public method는 BLUE/RED team code, match seed와 completed result만 받으며 caller-provided `Team`, `Team.getName()`이나 player/team display name을 identity 증거로 받지 않는다. 경계 내부의 `LckTeamAssembler`가 real five-position roster와 position별 stable `PlayerId`를 결정한다. 이 roster로 team/seed/Game 1 context, 20턴 state와 full manual selectable-set, authoritative AI search trace, final role/player assignment를 재구성하고, result의 Draft Meta version과 두 legal-role hash가 active `DraftResourceSet.meta()`와 exact일 때만 private-constructor validated token을 만든다. `MatchEngineV1InputFactory`의 unchecked mixed projection은 이 token만 받고 public raw factory는 제공하지 않는다.

세 Meta identity 중 하나라도 stale/forged이면 형식이 유효한 SHA-256이어도 deterministic preflight error로 거부된다. 이 시점에는 Match Engine input이나 simulator가 생성되지 않으므로 gameplay state mutation과 seeded gameplay Random 소비는 0이다. 정상 transcript의 Draft/final assignment/control/input/replay/resource/timeline/Random/output/result identity는 변경하지 않는다.

검증 뒤 authoritative `PRODUCTION_MATCHUP_COMPOSITION_V1`, engine `MATCH_SIMULATOR_ENGINE_IMPLEMENTATION_V9`를 fresh match state에서 실행한다. `finalDraftHash`, input/replay/output identity는 control evidence를 결속한다. 응답 `PLAYER_DRAFT_MATCH_RESPONSE_V1`은 공통 team/result/timeline을 재사용하지만 Draft authority를 `REAL_MATCH_RESPONSE_V1`인 것처럼 표시하지 않는다.

## Compact simulation receipt

Session/repository는 `MatchEngineV1Output`, timeline, event/snapshot 목록, full HTTP match DTO, decoded JSON bytes나 simulator mutable state를 저장하지 않는다. 첫 `/simulate`의 전체 output은 input preflight, fresh V9 실행과 output integrity 검증을 거쳐 현재 controller response를 만드는 동안에만 존재한다. Session에는 명시적 순서의 `PLAYER_DRAFT_SIMULATION_RECEIPT_V1`만 저장한다.

Receipt는 match/policy/profile/configuration/engine/rules, input/replay/resource, Draft decision/final Draft/final assignment/control evidence, simulator/structured timeline/output, Random draw/hash/algorithm과 winner/duration/end reason identity만 포함한다. Collection과 domain object를 포함하지 않으며 canonical UTF-8 표현은 16KiB 상한을 검증한다. Session ID, revision, action ID와 wall-clock은 receipt/gameplay identity에 넣지 않는다.

반복 `/simulate`는 cached full output을 반환하지 않는다. 완료된 immutable Draft에서 Match Engine을 결정적으로 다시 실행하고 새 receipt를 기존 receipt와 exact 비교한 뒤에만 response를 반환한다. Mismatch 또는 실행 실패 시 기존 status/receipt/revision을 유지하고 sanitized 500으로 실패한다. 같은 session의 concurrent simulate는 repository의 per-session atomic mutation 안에서 순차 실행되어 각각 exact response를 받으며, 다른 session을 전역으로 잠그지 않는다.

이 계약은 session마다 20~34MB급 result object graph를 보관하지 않는 heap safety를 retry CPU 절약보다 우선한다. 반복 요청은 Draft를 다시 수행하지 않지만 Match Engine 실행 비용을 다시 지불한다. Background job이나 durable result retrieval이 필요하면 total-byte quota를 가진 별도 storage milestone로 설계하며 compressed full response를 session cache로 보관하지 않는다.

## Verification

Final boundary focused lane은 engine/boundary/session simulation/API/Match Engine contract 5 suites / 36 tests / failures 0 / errors 0 / skipped 0으로 통과했다. 최종 executable production tree의 새 complete backend regression은 226 suites / 2,232 tests / failures 0 / errors 0 / skipped 0, aggregate XML 1,034.299초, Gradle wall 17분 29초로 첫 실행에서 통과했다. 기존 전체 회귀 수치는 재사용하지 않았다. API schema, session memory/capacity/receipt/retry, Production V9/profile/gameplay는 바꾸지 않았고 frontend 및 대형 diagnostic은 실행하지 않았다.

## Errors and limitations

오류 schema는 `PLAYER_DRAFT_API_ERROR_V1`이다. Parsing/unknown identity는 400, missing session은 404, revision/idempotency/status conflict는 409, expired session은 410, domain legality/preflight failure는 422, 예상하지 못한 내부 실패는 sanitized 500이다. Stack trace, class name과 local path는 응답에 포함하지 않는다.

V1에는 frontend, BO3/BO5, 지속 Hard Fearless series, save/resume, authentication, database, background result job/storage, WebSocket과 multi-node session routing이 없다.
