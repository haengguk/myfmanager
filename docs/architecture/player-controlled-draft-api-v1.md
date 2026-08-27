# Player-controlled Draft API V1

상태: `PLAYER_CONTROLLED_DRAFT_API_V1_ACCEPTED`

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

저장소는 Spring이 주입한 `Clock`을 사용하는 process-local `ConcurrentHashMap`이며 기본 최대 128 sessions, TTL 30분이다. Static mutable gameplay state는 없다. 서버 재시작 복구, database, auth와 multi-node coordination은 V1 범위 밖이다.

플레이어 action과 이어지는 AI advance는 map의 단일 atomic mutation이다. 같은 `clientActionId`와 같은 revision/champion payload를 재시도하면 최초 logical response를 돌려준다. 같은 ID의 다른 payload, stale revision, 완료/취소 session action은 409다. 같은 revision의 concurrent action은 하나만 성공한다. 잘못된 action은 revision/state/evidence를 바꾸지 않는다.

## Match execution and evidence

20턴 완료 시 `FinalRoleAssignmentResolver`가 flex role을 확정하지만 simulation을 자동 시작하지 않는다. `/simulate`는 turn 1부터 다음을 새로 검증한다.

1. rules/side/action/champion/authority와 before/after state hash
2. PLAYER turn의 현재 full selectable-set identity와 accepted legality
3. AI turn의 authoritative Auto policy/pool/weight/context/bucket trace
4. final ban/pick state, legal role permutation과 exact player assignment

검증 뒤 `MatchEngineV1InputFactory`가 nullable `DraftControlEvidence`를 포함한 input을 만들고 authoritative `PRODUCTION_MATCHUP_COMPOSITION_V1`, engine `MATCH_SIMULATOR_ENGINE_IMPLEMENTATION_V9`를 fresh match state에서 실행한다. `finalDraftHash`, input/replay/output identity는 control evidence를 결속한다. 응답 `PLAYER_DRAFT_MATCH_RESPONSE_V1`은 공통 team/result/timeline을 재사용하지만 Draft authority를 `REAL_MATCH_RESPONSE_V1`인 것처럼 표시하지 않는다.

## Errors and limitations

오류 schema는 `PLAYER_DRAFT_API_ERROR_V1`이다. Parsing/unknown identity는 400, missing session은 404, revision/idempotency/status conflict는 409, expired session은 410, domain legality/preflight failure는 422, 예상하지 못한 내부 실패는 sanitized 500이다. Stack trace, class name과 local path는 응답에 포함하지 않는다.

V1에는 frontend, BO3/BO5, 지속 Hard Fearless series, save/resume, authentication, database, WebSocket과 multi-node session routing이 없다.
