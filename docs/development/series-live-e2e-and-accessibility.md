# Series LIVE E2E and Accessibility

상태: `SERIES_LIVE_E2E_AND_ACCESSIBILITY_ACCEPTED`

## 범위와 결과

기존 Series 화면을 다시 설계하지 않고 LIVE BO3/BO5의 불확실한 전송 경계와 접근성을 보강했다. Series 전체 취소, Series-owned child Draft 취소와 경기 실행은 한 번의 논리 명령을 revision과 대상 identity에 결속한다. 응답이 `NETWORK`, `TIMEOUT`, `CANCELLED`로 불명확하면 새 명령 ID를 만들지 않고 먼저 authoritative Series GET으로 결과를 조정한다. 같은 상태라면 같은 ID를 유지해 사용자의 명시적 재시도만 허용하고, 이미 진행된 상태라면 최신 view 또는 committed replay로 이동한다.

실행 중 모달은 비파괴 동작에 초기 포커스를 두고 Tab/Shift+Tab을 내부에 가둔다. Pending 동안 버튼이 비활성화돼도 포커스는 dialog에 남고 Escape는 무시된다. 오류는 같은 모달 안의 assertive alert로 알리며, 정상 닫기 뒤에는 원래 trigger로 복귀한다. Series context에는 현재 game, status, 관리 팀/side, 서버 점수와 Hard Fearless 제외 수를 한 문장으로 제공하는 polite live region을 추가했다.

## 엄격한 계약

- `COMMITTED` game은 result와 receipt가 모두 있어야 하고 result winner가 null이면 안 된다.
- 다른 진행 상태가 result/receipt를 임의로 포함하면 거부한다. `BLOCKED`의 no-decisive result는 winner가 null이어야 한다.
- team별 committed winner 합계는 response score와 정확히 같아야 한다.
- score는 형식의 required wins를 넘을 수 없다.
- `ACTIVE`는 어느 팀도 required wins에 도달할 수 없고, `COMPLETED`는 정확히 한 winner가 required wins와 committed tally를 모두 만족해야 한다.
- 저장된 Series pointer의 실제 `SERIES_NOT_FOUND`/`SERIES_EXPIRED`는 제거한다. 일시 네트워크 실패는 유지하고, contract/JSON version 오류는 local view로 대체하지 않는다.

## 실제 LIVE 브라우저 증거

정상 흐름은 GEN/T1이 아닌 팀으로 실행했다. DK–HLE BO3 Game 1은 관리 DK/RED에서 직접 Draft 20턴, Production V9 commit, score `DK 0 : HLE 1`, Game 2 DK/BLUE, Hard Fearless 제외 10개, Playback/Result/hub 이동을 통과했다. 별도 BFX–BRO BO3는 세 game을 완료해 `BFX 2 : BRO 1`, Game 3 시작 시 Hard Fearless 20개, 최종 누적 30개와 `COMPLETED`를 확인했다.

실제 HTTP 202는 투명한 동시 요청 harness로 만들었다. 브라우저 command `b82d5227-0bbf-4b9e-a1c4-cdd795c0d6f7`과 동일한 payload/ID를 실제 backend reservation 중 재호출해 202를 수신했다. 브라우저는 GET polling 2회 뒤 commit을 확인하고 replay POST 1회로 full timeline을 복구했다. Backend worker gameplay command는 1회였고 추가 simulate/commit은 없었다.

응답 손실은 실제 backend mutation/response를 완료한 뒤 CORS 수신 경계만 차단했다. 이는 mock 성공 응답이 아니다.

- Game 3 simulate `0b42ae59-ac28-46ff-8286-2d1dd88c29ba`: 실제 POST 200 1회, Series GET 200 1회, replay POST 200 1회, child GET 200 1회. 중복 gameplay 명령과 score/revision 중복 증가 0.
- Child cancel `ce328a59-b6fe-4ed6-b49d-5fd06fdb85d8`: 실제 DELETE 204 1회 뒤 Series GET 200 1회, `DRAFT_CANCELLED`로 hub 복귀.
- Series cancel `2b811440-e347-4b21-b2be-acd92f435201`: 실제 DELETE 204 1회 뒤 Series GET 200 1회, `CANCELLED` terminal 도달.

활성 pointer는 실제 reload에서 같은 Series ID와 authoritative 상태를 복구했다. Series API 요청을 브라우저 네트워크 계층에서 중단했을 때 pointer는 유지됐고 local score로 대체하지 않았다. 유효 형식이지만 존재하지 않는 `series_ffff…`는 실제 backend 404/`SERIES_NOT_FOUND` 뒤 pointer를 제거하고 Series 준비 화면으로 이동했다. 운영 TTL 120분을 바꾸거나 기다리지 않았으므로 실제 wall-clock EXPIRED는 실행하지 않았고, 구조화된 `SERIES_EXPIRED` 분기는 deterministic contract로 확인했다.

## 접근성·반응형

키보드만으로 Series 준비, child 생성, 챔피언 검색/roving grid, 첫 밴 확정, modal 열기/닫기를 수행했다. 취소 modal의 초기 포커스, 양방향 trap, pending Escape 차단, pending focus 유지, stale 409 오류의 assertive alert와 trigger 복귀를 DOM/focus assertion으로 확인했다. 화면 낭독기 프로그램의 실제 음성 출력은 주장하지 않으며, accessibility tree의 role/name과 polite/assertive live-region DOM만 검증했다.

1280×720과 1440×900 모두 document horizontal overflow는 0이었다. 각 해상도에서 score/current game/managed team live text가 존재했고 Draft workspace와 검색이 사용 가능했으며 modal rect는 viewport 안에 있었다. 1280 modal은 `(405, 246.75)–(875, 473.25)`, 1440 modal은 `(485, 336.75)–(955, 563.25)`였다. `prefers-reduced-motion: reduce`에서는 animation/transition duration이 모두 `0.00001s`였고 포커스와 취소 동작을 막지 않았다.

Clean 정상/202 흐름의 page·console·runtime validation error는 0이었다. 의도적으로 CORS response-loss를 만든 요청만 예상된 브라우저 CORS 오류를 남겼으며 제품 오류와 분리해 기록했다.

## 기존 단판과 endpoint 격리

AUTO BFX–BRO seed 73은 실제 `POST /api/v1/real-matches/simulate` 200 한 번으로 20/20 자동 Draft 결과에 도달했다. Standalone 직접 Draft는 `POST /api/v1/player-drafts/sessions` 200, action 200, cancel 204를 사용했다. 이 focused 흐름에서는 Series endpoint와 단판 endpoint가 섞이지 않았다. Player action 성공 뒤 불필요한 GET도 추가하지 않았다.

## 검증 명령

```text
npm run series:verify
npm run player-draft:verify
npm run build
npm run reference:check
npm run reference:verify
npm run bundle:verify
```

이번 변경은 frontend source, deterministic script, 문서와 외부 `/tmp` test harness만 대상으로 했다. Backend Java, resource, schema, runtime wiring과 Gradle은 변경하지 않았으므로 backend full regression은 실행하지 않았다. Historical reference JSON과 기존 artifact는 덮어쓰지 않았다.

## 남은 제한

- Series 저장소는 process-local single-node이며 backend restart 복구, DB/save-load, auth/ownership과 multi-node coordination이 없다.
- 202는 bounded GET polling이며 background progress/WebSocket은 없다.
- 실제 EXPIRED 확인에는 운영 TTL 120분 경과가 필요하다.
- 실제 screen reader별 음성 품질과 Windows/macOS 보조기기 조합은 별도 수동 검증 대상이다.
