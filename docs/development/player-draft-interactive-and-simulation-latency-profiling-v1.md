# Player Draft Interactive and Simulation Latency Profiling V1

## Status

`PLAYER_DRAFT_INTERACTIVE_AND_SIMULATION_LATENCY_PROFILE_CAPTURED`

이 milestone은 Player-controlled Draft의 체감 지연을 current source에서 분해한 profiling이다. 최적화, cache 변경, payload 축소, async job, Draft/gameplay tuning은 적용하지 않았다. 따라서 아래 시간은 원인 증거이지 개선된 응답 시간이나 모든 PC·배포 환경에 대한 성능 보장이 아니다.

## 질문과 결론

기존 LIVE 기록의 "챔피언 확정 뒤 2~3초"와 "밴픽 종료 뒤 약 10초"는 여러 경계가 섞인 시작 가설이었다. 이번 측정에서는 마지막 player action과 명시적 `/simulate`를 별도 요청으로 고정했다.

```text
Player action 체감 지연
→ current-source cold Chromium 20건: 38.3~1,440.3ms, 중앙값 502.25ms
→ 가장 큰 backend phase: ACTIVE response projection 중앙값 149.894ms
→ 근접한 보조 phase: AI follow-up 중앙값 137.195ms
→ 브라우저 parse/strict validation은 action당 대체로 1ms 미만

완료 화면의 경기 실행 체감 지연
→ current-source cold Chromium: BLUE 5,092.9ms, RED 6,814.5ms
→ 가장 큰 backend phase: completed Draft/input validation 중앙값 3,480.430ms
→ Production V9 자체 중앙값 576.383ms
→ 큰 응답은 body 수신과 parse에도 별도 비용을 만든다
```

기존 action 2~3초와 first simulate 약 10초는 이번 schedule에서 재현되지 않았다. 이것은 해당 관측이 잘못됐거나 다시 발생하지 않는다는 뜻이 아니다. 과거 수치는 당시 서버/JIT 상태, action turn 구조, AI 후속 수, Draft 결과에 따른 event/snapshot 및 payload 크기, HTTP와 화면 반영이 합쳐진 값이다. 이번 결과는 그 범위를 current source의 작은 deterministic schedule에서 더 좁게 분해한다.

## Source와 측정 환경

- review HEAD: `b284a12ca82824ee543d12a99785660e87fab1fc`
- owned executable/verification diff를 포함한 source-tree identity: `12d7ba34bbeb952397e04592f1be2ff747da2cdc285199e87350fd895dc20d22`
- fixture: GEN vs T1, signed seed 73, BLUE/RED controlled
- Java: Eclipse Adoptium 21.0.9, normal tiered compilation, 2GiB harness max heap
- host: Windows 10 amd64, logical processor 12
- direct harness: sequential Spring Boot test context, BLUE cold 1회와 BLUE/RED warm 각 2회
- browser: controlled side별 fresh `bootRun`, Vite dev LIVE provider, Chromium cache disabled
- 측정 전 별도 대형 Gradle/diagnostic/backend/Vite workload가 없는 상태를 확인했다. Codex와 사용자 소유 idle 프로세스는 종료하지 않았다.
- runtime identity: Production V9, `PRODUCTION_MATCHUP_COMPOSITION_V1`; Matchup/Composition ON, Jungle Economy/Tempo OFF

Direct harness의 BLUE script와 RED script는 각 side 안에서 고정하고 SHA-256으로 결속했다. 실제 브라우저는 두 side 모두 `garen, galio, gangplank, gragas, graves, nami, gwen, gnar, nilah, diana` 순서를 사용했고 script hash는 `3546272f10c63feb56fab19d1c64bd794f495d800facaa572a4c3a5958322954f`다. Backend가 반환한 selectable set을 벗어나는 synthetic action은 사용하지 않았다.

Historical baseline, runtime Auto Draft, Draft hardening, transport artifact의 manifest는 각각 raw SHA-256 `c9b4659c4d602fb33c7295885cdc2685a4991469cc4cc0b097ca2d1a20cb26ee`, `751cb19ccf55b34cc0bf4a410a292ba66df4e84d566dd1e217b4a68712d3be8b`, `ae11f4eb368a8b796a113b32963048a764509b0bb98e27ebce313b7ec645d694`, `860f6cea4e8dfc42e1a38148dc5c2763331bcd899d784670af4e3222d89a068f`로 읽기 검증했다. 비교 기준으로만 사용했고 재생성하거나 수정하지 않았다.

## 관측 방식과 격리

Backend production Java, resource, Gradle, API DTO에는 timing을 추가하지 않았다. Test-side harness가 production 객체를 호출하고, 동일 의미를 단계별로 한 번 더 재생해 service total과 다음 구간을 관찰했다.

- action: player legality/apply, AI follow-up 및 AI decision, 가장 가까운 uncontended repository lookup/lock/idempotency, response projection, Jackson, offline gzip
- simulation: lookup/completed 확인, authoritative input validation, Production V9, output integrity/provenance, receipt, response projection, Jackson, offline gzip, first service, exact retry
- browser: confirm/click, fetch, CDP headers와 `loadingFinished`, body, JSON parse, strict/common validation, adapter normalization, React state, stable DOM

Backend 세부 phase는 production service 안에 영구 timer를 삽입한 nested 합계가 아니라, 같은 입력과 결과 identity를 exact 비교하는 별도 production-equivalent semantic replay다. 따라서 service total과 세부 phase를 더해 합계로 해석하지 않고 상대 크기와 hotspot 귀속에 사용한다. HTTP body write 완료는 CDP `loadingFinished`, request upload 완료는 CDP `requestWillBeSent`가 가장 가까운 외부 경계다.

Frontend production에는 기본 OFF인 additive observational hook만 추가했다. observer가 없으면 즉시 no-op하며 `performance.now()`도 호출하지 않는다. Public request/response body, action ID, revision, 화면 동작에는 영향을 주지 않는다. Playwright init script만 hook을 활성화했다.

## Interactive Player Action

### Backend 분포

Direct action 50건과 AI turn 48건의 단위는 ms다. p90은 의도적으로 작은 표본의 nearest-rank 기술 통계일 뿐 모집단 percentile이 아니다.

| Phase | Count | Min | Median | p90 | Max |
| --- | ---: | ---: | ---: | ---: | ---: |
| service total | 50 | 3.439 | 297.389 | 731.380 | 896.703 |
| AI follow-up total | 50 | 0.001 | 137.195 | 384.504 | 586.819 |
| AI decision 하나 | 48 | 2.646 | 149.547 | 382.889 | 586.789 |
| ACTIVE response projection | 50 | 0.267 | 149.894 | 298.280 | 566.233 |
| Jackson serialization | 50 | 0.245 | 0.538 | 2.622 | 50.811 |
| exact replay lookup/lock/idempotency 근사 | 50 | - | 0.033 | - | - |

`PlayerDraftApiV1Service.action`은 세션 lock 안에서 player action을 검증·적용하고 0/1/2개의 AI turn을 진행한다. 이후 controller response mapper가 ACTIVE session의 전체 selectable/unavailable champion과 advisory를 만들기 위해 `PlayerControlledDraftEngine.view`를 다시 호출한다. 이 두 계산은 현재 독립 경계다. 동시 요청 없는 exact replay에서 repository lookup/lock/idempotency는 중앙값 약 0.033ms라 주 병목 근거가 없다.

AI decision은 매번 후보 12개를 평가했지만 물리 계산량은 turn에 따라 크게 달랐다. 예를 들어 exact counter에서 planner computation은 AI turn별 232~50,432회, completion computation은 61~6,779회였고 role/pool 계산도 Draft 상태에 따라 달랐다. 그러므로 "AI 한 번"이라는 단일 비용으로 일반화하면 안 된다.

### AI 후속 수, action type과 Draft stage

| 구분 | Count | service median |
| --- | ---: | ---: |
| AI follow-up 0개 | 12 | 148.900ms |
| AI follow-up 1개 | 28 | 337.972ms |
| AI follow-up 2개 | 10 | 470.112ms |
| BAN | 25 | 365.770ms |
| PICK | 25 | 158.314ms |
| early turn 1~7 | 18 | 580.135ms |
| mid turn 8~14 | 17 | 305.278ms |
| late turn 15~20 | 15 | 86.907ms |

BAN이 항상 느리고 PICK이 항상 빠르다는 제품 규칙은 아니다. 이번 script에서는 early BAN과 넓은 후보/pool 계산이 겹쳤다. BLUE/RED warm action service 중앙값은 각각 339.260ms와 240.361ms였지만 controlled turn 위치와 AI 0/1/2 구조가 다르므로 side 자체의 인과 효과로 판단하지 않는다.

### Actual Chromium

Fresh backend를 side별로 시작한 20개 action의 confirm-to-stable-DOM은 최소 38.3ms, 중앙값 502.25ms, p90 1,221.3ms, 최대 1,440.3ms였다. BLUE 첫 action은 1,440.3ms, RED 첫 action은 1,299.4ms였고 마지막 완료 action은 각각 52.2ms와 38.3ms였다. 현재 source에서는 기존 2~3초가 재현되지 않았고, cold와 넓은 early Draft 계산이 가장 큰 action을 만들었다.

Action 응답의 application JSON parse/strict validation은 대체로 1ms 미만이고 React state 뒤 DOM은 보통 약 30~40ms였다. 그래서 이번 표본의 action 체감은 browser validation보다 server response 대기가 지배한다. 마지막 Draft action은 explicit simulate와 합산하지 않았다.

## Completed Draft Simulation

### Backend 분해

Direct first simulation 5건의 단위는 ms다.

| Phase | Median | p90 / max | 해석 |
| --- | ---: | ---: | --- |
| first service total | 4,301.066 | 4,668.857 | 실제 service 호출 |
| completed Draft/input validation | 3,480.430 | 4,055.022 | 가장 큰 별도 semantic phase |
| Production V9 | 576.383 | 1,506.085 | cold BLUE에서 JIT/startup 영향이 큼 |
| output integrity/provenance | 242.398 | 275.802 | output/hash 검증 |
| response projection | 39.485 | 71.746 | timeline/snapshot DTO 포함 |
| Jackson serialization | 117.755 | 193.497 | decoded JSON 생성 |
| exact retry service | 4,114.124 | 4,967.442 | V9 fresh 재실행 포함 |

가장 큰 `PlayerControlledDraftMatchInputBoundary.validateAndCreateInput`은 team code로 authoritative roster를 다시 조립하고 completed result의 active Draft Meta/rule/series context를 검사한다. 이어 20개 evidence를 처음 상태부터 순서대로 재생한다. AI turn은 search/selector와 authoritative trace를 다시 계산하고 PLAYER turn은 full `view`를 다시 만들어 legal champion 및 selectable-set identity를 확인한다. 마지막에는 final role과 10명 match assignment를 다시 resolve한다. 이 fail-closed 검증은 standalone과 Series-owned child Draft가 공유하는 `validateCompletedSeries` 핵심 경계다.

Compact receipt는 output cache가 아니다. 동일 completed session의 exact retry도 Production V9을 fresh 재실행하고 새 receipt가 기존 compact receipt와 exact한지 확인한 뒤 full output을 다시 projection한다. 따라서 retry가 즉시 끝나지 않는 것은 현재 계약과 일치한다.

### Actual Chromium과 HTTP/body

| Phase | BLUE cold | RED cold |
| --- | ---: | ---: |
| simulate click → Playback stable DOM | 5,092.9ms | 6,814.5ms |
| fetch → response headers | 4,804.2ms | 5,885.2ms |
| CDP request → headers | 4,803.6ms | 5,884.7ms |
| headers → body complete | 231.8ms | 724.1ms |
| CDP headers → loading finished | 162.4ms | 602.1ms |
| `Response.json()` | 12.4ms | 80.0ms |
| strict Player Draft validation | 4.6ms | 7.6ms |
| 그중 common semantic validation | 0.6ms | 1.0ms |
| normalization | 2.1ms | 2.7ms |
| React state | 2.1ms | 2.7ms |
| state → Playback DOM | 16.6ms | 19.7ms |
| gzip/CDP encoded bytes | 479,655 | 2,220,596 |
| decoded JSON bytes | 5,655,202 | 26,712,924 |

두 응답 모두 HTTP 200과 `Content-Encoding: gzip`이었다. BLUE/RED의 final Draft와 경기 timeline이 달라 payload도 크게 달랐으므로 side가 payload를 직접 결정한다고 일반화하지 않는다. Direct 고정 script에서도 BLUE는 events 439/snapshots 255/decoded 약 22.85MB, RED는 events 400/snapshots 242/decoded 약 17.29MB였다. Browser script 결과는 다시 다른 timeline이라 크기도 달랐다.

Gzip은 wire bytes를 줄였지만 decoded JSON의 projection, parse, validation과 heap 비용을 없애지 않는다. 다만 이번 actual Chromium 두 건에서는 server TTFB가 압도적으로 컸고 parse/strict validation/normalization/render는 BLUE 약 35ms, RED 약 113ms 범위였다. 작은 표본이고 payload 차이가 크므로 frontend 비용도 해결됐다고 판단하지 않는다.

## JFR hotspot 후보

10ms CPU sampling과 allocation sampling을 작은 별도 run에서 수행했다. 관련 execution sample 811개, allocation sample 58,401개였다.

주요 CPU sample 후보는 `DraftAvailability.computePoolHealth` 내부 lambda 182, `MatchEngineV1Canonicalizer.canonicalJson` 76, `PreDraftPlanner.candidatePlanValue` 49, `DraftState.unavailableChampions` 39, `PreDraftPlanner.build` 24였다. Allocation sample weight는 `PlayerControlledDraftEngine.view`, pool health, canonical JSON, unavailable champion, opponent threat 순으로 컸다.

이 수는 CPU percentage나 정확한 allocation byte 총량이 아니다. JFR sampling weight를 wall-clock phase와 exact Draft counter에 결합했을 때, 반복되는 availability/pool/planner/role 계산과 canonical output/hash 작업이 우선 조사 후보라는 의미만 가진다.

## Exact parity

Profiling ON/OFF pair에서 session ID를 제외한 다음을 exact 비교했다.

- 20개 Draft decision, revision, PLAYER/AI authority, champion, alternatives와 final assignment 10개
- selection trace/evidence, Draft identity와 control evidence
- Match Engine input, production policy/profile/configuration, replay/resource provenance
- simulator/structured timeline, Random draw count/fingerprint, output hash
- winner, duration, events, snapshots와 final result

Browser hook은 public API body에 timing을 넣지 않고 관측 결과를 외부 collector로만 보냈다. Gameplay/Draft Random 소비, selection order, response 의미, UI/접근성 동작은 바뀌지 않았다.

## Standalone과 Series 공유 경계

Standalone action/session envelope와 Series aggregate/revision/allowed command envelope는 서로 다르다. 하지만 AI selector/planner/availability, completed transcript의 `validateCompletedSeries`, final role assignment, Match Engine input factory, Production V9, common match projection은 공유된다. 따라서 이번 확인 중 AI calculation과 completed Draft validation hotspot은 Series-owned child Draft에도 적용될 가능성이 높다. Series 전체 API/화면 latency는 직접 측정하지 않았으므로 Series end-to-end 수치로 승격하지 않는다.

## 판정과 다음 작업

### Player action

```text
챔피언 확정 → 다음 player turn DOM
→ 가장 큰 측정 backend phase: ACTIVE session response projection
→ 원인: full legal/selectable/unavailable/advisory view 재계산
→ 근접한 보조 병목: 0/1/2개 AI follow-up의 planner/availability/role 계산
→ repository lock과 browser parse는 이번 clean flow의 주 병목이 아님
→ 다음 최소 경계: PLAYER_DRAFT_SESSION_PROJECTION_PERFORMANCE_HARDENING_V1
→ 후속 후보: PLAYER_DRAFT_AI_TURN_PERFORMANCE_HARDENING_V1
```

### Simulation

```text
simulate 클릭 → Playback DOM
→ 가장 큰 backend phase: completed Draft/input validation
→ 원인: 20턴 authority/legal pool/AI trace/final role을 처음부터 재구성
→ Production V9 자체는 warm에서 두 번째 계산 구간
→ payload가 큰 run은 body 수신과 JSON parse에도 별도 잔여 비용
→ 다음 최소 경계: PLAYER_DRAFT_SIMULATION_INPUT_VALIDATION_PERFORMANCE_HARDENING_V1
```

공식 추천 우선순위는 `PLAYER_DRAFT_SIMULATION_INPUT_VALIDATION_PERFORMANCE_HARDENING_V1+PLAYER_DRAFT_SESSION_PROJECTION_PERFORMANCE_HARDENING_V1`다. 두 경계의 exact evidence와 fail-closed 의미를 보존하면서 중복 계산만 줄일 수 있는지 먼저 설계한다. 이후 AI turn hardening, compact response/Web Worker, async match job을 별도 계약으로 검토한다.

실제 계산을 줄이면 actual latency가 줄어든다. Async progress는 기다리는 체감을 바꾸지만 job lifecycle, cancel, recovery와 exact retry 계약이 추가된다. Compact payload는 transfer/parse를 줄일 수 있지만 timeline 중 무엇을 늦게 받거나 생략할지 정의해야 한다. 이번 표본으로 개선율을 약속하지 않는다.

## Artifact와 재현 경계

공식 output은 `backend/build/reports/player-draft-interactive-simulation-latency-profiling-v1/`에 있다.

- `profiling-contract.json`: schedule, phase, environment와 source identity
- `interactive-action-runs.csv`: 50 action rows
- `interactive-ai-turns.csv`: 48 AI decision rows
- `simulation-runs.csv`: 5 first + 5 exact retry rows
- `browser-runs.csv`: 20 action + 2 simulate rows
- `phase-summary.json`, `hotspots.json`, `recommendation.json`, `analysis.md`
- `SHA256SUMS.txt`: 위 공식 파일 9개의 raw SHA-256

Manifest raw SHA-256은 `3009861416762f3ee61a56624d3cd1762bac0ab889d2f6a8092404c40e820d46`다. Timing byte equality는 acceptance gate가 아니다. Generator는 row cardinality, non-negative phase, correlation, cold/warm, source/script/output/Draft identity와 manifest completeness를 검증한다.

## 검증과 제한

- `PlayerDraftLatencyProfilingV1HarnessTest`: profiling ON/OFF Draft/gameplay/output exact parity, 최종 focused pass
- explicit profiling diagnostic: canonical artifact 생성·검증, 최종 `BUILD SUCCESSFUL`
- actual Chromium BLUE/RED LIVE: action 10회 + explicit simulate 1회씩, fallback/console/page/runtime validation error 0
- frontend production build: 101 modules, 성공
- backend complete regression: 실행하지 않음. Backend production Java/resource/Gradle/runtime/API를 바꾸지 않았고 변경된 backend는 test/diagnostic source뿐이다. Frontend production 변경은 기본 OFF observational hook이며 production build와 actual LIVE flow로 직접 검증했다.

추가 fixture, packaged JAR 대 `bootRun`, 수백·수천 seed, Series frontend end-to-end는 측정하지 않았다. 이번 schedule은 balance audit가 아니며 cold/warm과 BLUE/RED 차이를 모집단 효과로 해석할 수 없다. 최적화는 아직 하나도 적용하지 않았다.
