# Player Controlled Draft Frontend V1

## 구현 thesis

### Visual thesis

현재 lolmanager의 어두운 경기 운영실 화면 안에서 BLUE와 RED가 대치하고, 중앙 챔피언 풀과 현재 turn 하나에 시선이 집중되는 정교한 Draft desk를 만든다.

### Content plan

경기 설정 → 현재 turn과 양 팀 Draft 상태 → champion 탐색과 선택 → 완료된 final assignment → 경기 실행 순서로 정보를 배치한다.

### Interaction thesis

turn 변경 시 현재 행동 영역을 짧게 강조하고, AI 응답으로 추가된 결정을 한 번만 절제해 드러내며, champion 선택에서 확정 bar로 이어지는 상태 전환만 사용한다. `prefers-reduced-motion`에서는 이 motion을 제거하고 즉시 전환한다.

## 목표와 범위

이 문서는 `PLAYER_CONTROLLED_DRAFT_FRONTEND_V1` 구현 및 검증 결과를 기록한다. Backend Player Draft API를 변경하지 않고 LIVE 경기 설정에서 직접 밴픽 세션을 시작하여, 완료된 Draft를 Production V9 경기 재생과 결과 화면까지 연결한다.

## Before / After

변경 전에는 경기 설정에서 `/api/v1/real-matches/simulate`를 호출해 자동 Draft와 경기를 한 번에 계산하고, 사용자는 완성된 자동 Draft를 읽기 전용으로만 볼 수 있었다. 변경 후에는 LIVE 경기 설정에서 `자동 밴픽`과 `직접 밴픽`을 선택할 수 있다. 직접 밴픽은 BLUE 또는 RED 한쪽을 명시적으로 제어하며, 반대쪽 AI가 현재 authoritative Draft 상태에 반응한다.

```text
LIVE 경기 설정
→ 직접 밴픽 / 제어 진영 선택
→ Player Draft session 생성
→ legal champion 선택과 명시적 확정
→ 상대 AI 자동 응답
→ 20턴 완료와 최종 포지션 확인
→ Production V9 경기 실행
→ 기존 재생 / 결과 / mixed-authority Draft 다시 보기
```

AUTO와 REFERENCE의 기존 경로는 분리돼 있다. AUTO는 계속 `REAL_MATCH_SIMULATE_REQUEST_V1`을 사용하고 기존 읽기 전용 Draft 화면으로 이동한다. REFERENCE에서는 직접 밴픽을 비활성화하며 reference payload를 Player Draft 응답으로 변환하지 않는다.

## API와 runtime validation 경계

`player-draft/` 아래에 전용 DTO, strict validator, HTTP client, adapter와 view model을 분리했다. create/get/action/simulate/delete 다섯 endpoint는 각각의 timeout과 `AbortSignal`을 사용한다. 브라우저 표준 gzip 협상을 사용하며 수동 압축 해제는 하지 않는다.

모든 정상 응답과 오류 응답은 `unknown`에서 검증한다. Session schema·UUID·revision·status·team/roster·seed·Game 1·policy/hash, turn/decision 순서와 PLAYER/AI evidence, selectable/unavailable/recommendation projection, completed final assignment, simulation의 session/Draft/control/integrity 결속을 확인한다. Player Draft match payload는 기존 `REAL_MATCH_RESPONSE_V1`로 cast하지 않는다. Envelope 검증 뒤 Auto와 공유하는 presentation participant/champion binding, event actor/killer/victim/assistant, final snapshot/result 의미는 canonical common semantic validator를 통과한 뒤에만 common adapter로 전달한다.

## Session, idempotency와 retry

화면은 backend session projection을 그대로 authority로 사용한다. revision, 다음 turn, legal champion, AI 결정과 완료 여부를 로컬에서 계산하지 않는다. 한 번의 logical confirm에 `crypto.randomUUID()`로 만든 `clientActionId`를 하나만 부여하고, 응답 유실이나 timeout 뒤 같은 선택을 재시도할 때 같은 revision·champion·ID를 유지한다. 선택이나 turn이 바뀌면 새 logical action을 만든다. 제출 중에는 중복 confirm과 simulation double click을 막는다.

`STALE_DRAFT_REVISION`은 GET refresh로 reconciliation하고, 422는 낙관적 변경 없이 현재 projection을 유지한다. 404와 410은 terminal 상태로 전환한다. payload conflict나 receipt/internal mismatch는 자동 재시도·reference fallback·새 session 생성 없이 구조화된 한국어 오류로 노출한다. simulation retry도 동일 session의 `/simulate`만 다시 호출한다.

## Draft UI semantics

양쪽 패널은 authoritative roster와 ordered ban/pick 슬롯을 분리한다. 진행 중 pick order를 선수 position으로 추론하지 않으며, 완료 전에는 선수와 챔피언을 임의로 결속하지 않는다. 중앙 작업 영역은 한글 이름 우선 champion pool, 한글/영문/ID 검색, feasible-role 필터, advisory top 3, 선택 preview와 명시적 BAN/PICK 확정으로 구성한다.

Champion은 모두 native button이며 selectable과 unavailable을 색뿐 아니라 문구·상태·한국어 사유로 구분한다. Pool은 roving tabindex와 Arrow/Home/End 이동을 사용해 173개 tile을 모두 Tab으로 지나지 않으며 focus, selection과 explicit confirm을 분리한다. role filter의 `feasibleRoles`는 탐색 표시일 뿐 legality나 최종 position 계산에 사용하지 않는다. Turn history는 순서, side, action, champion과 `PLAYER`/`AI` authority를 구조화된 필드로 표시한다. 현재 turn·결정 수·네트워크 상태는 중복을 제한한 polite live region으로, 오류는 assertive alert로 알린다. Modal은 focus trap, pending dialog focus, Escape/restore/fallback 의미를 갖는다.

Motion은 turn 강조, 새 결정 reveal, 선택→확정 전환으로 제한하며 `prefers-reduced-motion`에서 제거한다. 1440×900에서는 양 팀/중앙 workspace/기록을 함께 보이고, 1280×720과 좁은 화면에서는 workspace를 우선한 세로 fallback으로 수평 page overflow를 만들지 않는다.

## 완료, 재생과 결과 연결

20번째 결정 뒤 champion pool은 완료 summary로 전환한다. 양 팀 ban/pick, 20-turn mixed-authority history와 `completedDraft.finalAssignments`의 정확한 10개 선수·position·champion 결속을 보여준다. 경기 실행은 현재 completed session에 `PLAYER_DRAFT_SIMULATE_REQUEST_V1`을 명시적으로 한 번 전송한다.

검증된 match payload는 raw 응답을 보관하지 않고 기존 `MatchSessionViewModel`로 정규화한다. `draftOrigin` discriminant가 AUTO와 PLAYER_CONTROLLED를 분리하고, 직접 Draft의 controlled side·authority·control evidence·final assignment를 재생/결과까지 유지한다. 재생과 결과에서 Draft 다시 보기는 자동 Draft board가 아니라 완료된 Player Draft review로 돌아가며 API를 다시 호출하지 않는다.

## 오류, 만료와 취소

화면은 options/session 생성, action pending, stale refresh, illegal selection, not found, expired/cancelled, contract mismatch, backend unavailable, simulation 단계와 retry를 별도 한국어 상태로 제공한다. ACTIVE/COMPLETED session에서 뒤로 갈 때는 계속 진행 또는 명시적 취소 modal을 보여주고, DELETE 204를 확인한 뒤에만 설정으로 이동한다. DELETE 결과가 불명확하면 성공으로 가장하지 않으며 tab close/unmount에서 자동 DELETE하지 않는다.

## 검증

- `npm run build`: 통과, TypeScript build와 Vite production bundle 생성
- `npm run player-draft:verify`: ACTIVE/COMPLETED/SIMULATION 정상 수용과 schema/status/turn/authority/champion/recommendation/assignment/control/error 변형 거부, unavailable reason 한국어 mapping 통과
- `npm run reference:check`, `npm run reference:verify`, `npm run bundle:verify`: 통과; checked-in reference와 lazy bundle 경계 보존
- 현재 V9 LIVE options/GEN-T1 seed 73 응답 경로를 지정한 `npm run live:verify`: teams 10, players 50, decisions 20, assignments 10, events 376, snapshots 233, RED/2,320초와 invalid mutation 10종 검증 통과
- LIVE Flow A: BLUE-controlled 10회 player action, ordered 20 decisions, final assignment 10개, `/simulate` 1회 200+gzip, Playback→Result→mixed-authority Draft review 확인
- LIVE Flow B: RED session 생성 직후 BLUE AI 결정과 RED player turn, player action 1회 뒤 revision/decision 증가, 명시적 DELETE 204 확인
- LIVE Flow C: AUTO가 기존 `/api/v1/real-matches/simulate`만 호출하고 Player Draft endpoint 0회, 읽기 전용 자동 Draft 20/20과 console error 0 확인

Backend production Java/resource/Gradle은 변경하지 않았으므로 complete backend regression은 실행하지 않았다. 로컬의 historical `backend/build/reports/real-match-api-v1` V8 handoff는 현재 V9 validator보다 오래돼 이를 덮어쓰지 않고, 실제 V9 options/response를 ignored `output/verification-live`에 분리해 환경 경로로 `live:verify`를 통과시켰다. 기존 reference JSON도 재생성하지 않았다.

## 현재 제한

- full page reload 뒤 session resume와 local persistence는 없다.
- Draft timer, WebSocket/progress streaming, 자동 penalty, 인증/권한, DB 저장은 없다.
- Game 1 단판만 지원하며 BO3/BO5, Game 2+, 누적 Hard Fearless와 series score는 후속 범위다.
- Full page reload resume와 durable persistence는 여전히 없지만 keyboard journey, response loss/stale/late guard와 cancel pending audit은 [LIVE E2E/accessibility](player-controlled-draft-live-e2e-and-accessibility.md)에서 완료했다.
