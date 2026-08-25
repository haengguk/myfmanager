# Real Match Frontend V1-B

## 상태와 경계

`REAL_MATCH_FRONTEND_V1_B`는 완료된 live API integration milestone이다. 기본 공급자는 `LIVE`이며 경기 준비에서 실제 LCK 10팀/50명 options를 읽고, 서로 다른 팀과 canonical signed Java long seed를 `POST /api/v1/real-matches/simulate`에 보낸다. 성공 응답은 strict runtime validation 뒤 하나의 normalized session으로 변환되어 자동 Draft, timeline playback, 경기 결과가 같은 identity와 final state를 사용한다.

REFERENCE는 회귀 확인을 위한 명시적 build-time 모드다. LIVE 오류·timeout·취소 때 reference fixture로 자동 fallback하지 않는다. 기존 legacy 경기 화면과 `POST /api/matches/simulate` 호출은 삭제하거나 변경하지 않았다. Backend production source와 계약도 이 milestone에서 변경하지 않았다.

## 환경 설정과 동시 실행

기본값은 다음과 같다.

```dotenv
VITE_REAL_MATCH_DATA_SOURCE=live
VITE_REAL_MATCH_API_BASE_URL=http://localhost:8080
VITE_REAL_MATCH_OPTIONS_TIMEOUT_MS=30000
VITE_REAL_MATCH_SIMULATE_TIMEOUT_MS=300000
```

Backend와 frontend를 서로 다른 terminal에서 실행한다.

```text
backend> gradlew.bat bootRun
frontend> npm.cmd run dev -- --host 0.0.0.0
```

브라우저는 `http://localhost:5173`, API는 `http://localhost:8080`을 사용한다. REFERENCE 확인은 process 시작 전에 `VITE_REAL_MATCH_DATA_SOURCE=reference`를 명시한다. `.env.example`에는 secret이 없으며 API URL과 timeout만 담는다.

## 요청과 상태 전이

- Options는 strict `REAL_MATCH_OPTIONS_V1`, 정확히 10팀/50 unique stable player, canonical position 순서를 검증한다.
- Simulation request는 schema/team/team/seed 네 필드만 만들며 `+73`, `073`, `-0`, 범위 밖 signed long과 같은 팀을 client에서 먼저 거부한다.
- 응답은 `REAL_MATCH_RESPONSE_V1`, 요청 identity, Draft 20개/assignment 10개, result, 모든 event/snapshot, final consistency와 integrity를 검증한다.
- Loading은 연결/다운로드/JSON parsing/contract validation/normalization 단계를 표시한다. 사용자는 simulation을 취소하거나 실패한 동일 선택을 다시 시도할 수 있다.
- 각 options/simulation sequence는 AbortController와 sequence identity를 가진다. 취소되거나 늦게 도착한 응답은 현재 화면을 덮지 않으며 double click은 하나의 POST만 만든다.
- 첫 번째 경기 결과 클릭은 끝 시점과 전체 event log로 이동하고, 두 번째 클릭이 결과 modal을 연다. 결과에서 재생으로 돌아갈 때 새 simulation 요청은 없다.

## E2E 결과

2026-08-24 local backend/frontend 동시 실행에서 다음을 확인했다.

| 시나리오 | 실제 결과 |
| --- | --- |
| 고정 `GEN` BLUE / `T1` RED / `73` | GEN/BLUE 승, 25–20, 3,430초, event 517, snapshot 344 |
| 비고정 `HLE` BLUE / `DK` RED / `-73` | DK/RED 승, 28–26, 2,840초, event 519 |
| 취소 후 late response | 첫 요청 abort, 설정 HLE/DK/-73 유지, stale 화면 전환 없음 |
| 명시적 retry | 두 번째 POST 성공, Draft→Playback→Result 일관성 유지 |
| Options network 오류 | 한국어 오류와 retry 노출, 시작 비활성, reference fallback 없음 |
| Backend same-team 400 | `SAME_TEAM_NOT_ALLOWED`, field `redTeamCode` 표시 계약 확인 |

성공 경로에서 console error는 0이었다. 1440×900과 1280×720에서 설정, Draft, playback, result의 가로 overflow와 핵심 조작 영역을 확인했다. Draft ban/pick portrait, playback event 시간 필터, 결과 ban portrait와 gold graph도 실제 응답 identity를 사용한다. Backend가 ban champion presentation을 제공하지 않는 현재 계약에서는 structured ChampionId로 Data Dragon asset path를 만든다.

## 성능 관측

측정값은 Chromium local E2E의 JSON performance log이며 환경에 따라 달라질 수 있다.

| 항목 | GEN–T1/73 | HLE–DK/-73 |
| --- | ---: | ---: |
| 수신 payload | 33,617,921 B | 20,315,047 B |
| 요청+다운로드 | 54,442.9 ms | 35,530.0 ms |
| JSON parse | 140.4 ms | 82.3 ms |
| runtime validation | 14.5 ms | 13.6 ms |
| normalization | 4.0 ms | 2.1 ms |
| 화면 전환→첫 playback paint | 31.2 ms | 15.2 ms |
| 정규화 후 JS heap used/total | 11,240,800 / 14,060,148 B | 11,976,304 / 14,192,488 B |

Checked-in reference 응답 artifact는 개행 차이를 포함해 33,617,922 B다. LIVE session은 정규화 뒤 raw response text/DTO를 보존하지 않지만, fetch text와 JSON parse가 동시에 존재하는 순간의 transient peak는 브라우저 heap 수치에 완전히 반영되지 않을 수 있다. Production bundle에서 reference fixture는 초기 chunk가 아닌 별도 dynamic chunk다.

### Backend phase baseline 후속 측정

같은 두 fixture를 Windows 10 / OpenJDK 21.0.9 / Gradle 9.5.1 환경의 한 fresh JVM에서 fixture별 warmup 1회 + measured 3회로 다시 측정했다. Test-side phase decomposition과 실제 Spring random-port HTTP replay는 모든 반복에서 result/output/replay/timeline/Random이 exact였다.

| 항목 (measured min / median / max) | GEN–T1/73 | HLE–DK/-73 |
| --- | ---: | ---: |
| 앱 경계 | 14,182.029 / 14,226.442 / 17,936.049 ms | 9,704.642 / 9,879.504 / 10,019.691 ms |
| roster + Draft + engine input 준비 | 12,630.534 / 12,704.045 / 14,844.768 ms | 8,871.728 / 8,980.335 / 9,243.848 ms |
| MatchEngine execution | 1,012.078 / 1,078.131 / 1,678.933 ms | 521.332 / 547.898 / 594.714 ms |
| output integrity validation | 375.230 / 444.368 / 1,331.823 ms | 215.980 / 248.830 / 256.986 ms |
| response mapping | 68.291 / 79.443 / 94.220 ms | 35.446 / 37.934 / 46.727 ms |
| JSON serialization | 273.764 / 291.712 / 329.451 ms | 103.395 / 118.587 / 121.649 ms |
| 실제 local HTTP end-to-end | 14,085.725 / 14,555.977 / 15,431.567 ms | 9,816.261 / 10,197.495 / 10,209.176 ms |

가장 큰 관찰 phase는 roster/Draft/input 준비 묶음으로 앱 경계 median의 89.299%와 90.899%였다. 이 묶음 내부를 더 쪼개지 않았으므로 roster lookup, Draft scoring, input projection 중 어느 하나가 단독 원인이라고 해석하지 않는다. Actual HTTP raw body는 기존 E2E와 같은 33,617,921 B / 20,315,047 B이고 거의 전부가 timeline 독립 직렬화 크기 33,595,402 B / 20,291,837 B다. Offline gzip은 2,789,989 B(8.299%) / 1,875,877 B(9.234%)였지만 압축은 활성화하지 않았다.

따라서 기존 Chromium `요청+다운로드` 54.4초/35.5초는 backend local HTTP median만의 값이 아니며, 반대로 MatchEngine execution만으로도 설명되지 않는다. 두 측정은 서로 다른 관찰 경계를 가진다. Backend 상세 raw run과 환경은 `backend/build/reports/real-match-performance-baseline-v1/`에 있고 status는 `REAL_MATCH_PERFORMANCE_BASELINE_CAPTURED`다.

### Runtime hardening 및 Auto Draft 분해

후속 감사에서 과거 개발용 `bootRun`이 Spring Boot optimized launch의 `-XX:TieredStopAtLevel=1` 때문에 C1-only로 실행됐음을 실제 JVM flag로 확인했다. `bootRun.optimizedLaunch=false`로 개발 실행 경로를 정상 tiered/C2 경계로 되돌린 뒤, fixture별 fresh JVM의 결과는 다음과 같다.

| 실행 경계 | GEN–T1/73 first / warm | HLE–DK/-73 first / warm |
| --- | ---: | ---: |
| hardened `bootRun` | 15.750 / 13.933초 | 12.166 / 10.612초 |
| packaged JAR | 16.878 / 13.682초 | 12.554 / 9.655초 |

네 JVM 모두 `TieredStopAtLevel=4 {default}`와 C2용 profiled/non-profiled code heap을 노출했고, HTTP response/output/replay/timeline/Random identity는 기존 frozen 결과와 exact였다. 따라서 층위는 다음처럼 구분한다.

1. 과거 C1-only optimized-launch `bootRun`
2. 현재 hardened `bootRun` first/warm
3. 일반 packaged JAR first/warm
4. 기존 performance baseline test JVM
5. 브라우저 다운로드/JSON parse/runtime validation/normalization/render
6. 정상 JVM의 automatic Draft 자체

정상 JVM 12-fixture×2 측정에서 automatic Draft median/p90/max는 11.173/13.420/15.412초였고, roster/context/fresh history/Draft/input 준비 구간의 run별 Draft 비중 median은 99.9901%였다. BAN/PICK turn median은 733.136/487.298ms다. 즉 backend의 남은 주 CPU 비용은 자동 Draft이며 frontend parse/validation은 별도 경계다. JFR은 `DraftAvailability`, `PreDraftPlanner`, `RoleAssignmentSolver` 경로를 hotspot으로 가리키지만 sampling evidence이므로 절대 비율로 해석하지 않는다.

이 runtime hardening은 gameplay나 화면 기능을 바꾸지 않는다. 세부 raw evidence는 `backend/build/reports/real-match-runtime-auto-draft-scalability-v1/`에 있고, 아래 Draft Engine hardening의 frozen 기준선으로 사용했다. Payload 전달/파싱 hardening은 Draft 최적화와 분리한다.

### Draft Engine performance hardening V1

최종 backend tree는 한 `DraftEngine.draft()` 호출 안에서 role assignment, role feasibility, completion/pool-health와 planner candidate score의 동일 계산만 재사용한다. Search/scoring/tuning/후보 순서, gameplay/Random, API schema, production resource와 frontend 코드는 바꾸지 않았다.

동일한 12 fixtures × 2 공식 측정에서 full automatic Draft median/p90/max는 4.032/4.314/5.854초였다. frozen 기준선 11.173/13.420/15.412초 대비 median 63.911%, p90 67.852% 감소해 status `DRAFT_ENGINE_PERFORMANCE_HARDENED`를 받았다. 24/24 final Draft와 480/480 turn은 동일 JVM uncached reference에 exact였고 GEN–T1/73 및 HLE–DK/-73 API response/output/replay/timeline/Random도 exact였다. 이는 backend Draft phase 측정이며 과거 Chromium 요청+다운로드 54.4/35.5초나 20–34MB payload 전송 시간을 다시 측정한 값은 아니다.

공식 evidence는 `backend/build/reports/draft-engine-performance-hardening-v1/`에 있고 manifest 7/7 raw SHA-256은 `ae11f4eb368a8b796a113b32963048a764509b0bb98e27ebce313b7ec645d694`다. Transport compression final source에 결속한 Real Match API handoff도 fresh candidate A/B byte equality 뒤 갱신했으며 manifest 6/6 raw SHA-256은 `9767356ce01243ff67441354a24d2d54df86fd30ed69cb57397ed36629876fad`다.

### Transport compression 및 live E2E 재측정

Backend는 Spring Boot 표준 gzip 협상을 사용하며 frontend는 기존 `fetch`/`response.text()` 경계를 그대로 쓴다. 수동 gunzip, 압축 전용 DTO, reference fallback은 없다. 실제 Chrome의 wire byte는 Blob size가 아니라 CDP `Network.loadingFinished.encodedDataLength`로 측정했다.

| 항목 | GEN–T1/73 first / warm | HLE–DK/-73 first / warm |
| --- | ---: | ---: |
| decoded JSON | 33,617,921 B | 20,315,047 B |
| 외부 HTTP gzip body | 2,789,995 B (8.299%) | 1,875,883 B (9.234%) |
| Chromium encoded bytes | 2,828,788 B | 1,902,063 B |
| request+download | 9,819.5 / 7,033.5 ms | 8,653.8 / 5,471.0 ms |
| JSON parse | 115.2 / 127.8 ms | 78.4 / 69.2 ms |
| runtime validation | 13.3 / 11.4 ms | 12.0 / 9.5 ms |
| normalization | 2.8 / 1.8 ms | 3.8 / 1.3 ms |
| request→Draft | 10,153 / 7,503 ms | 8,999 / 5,950 ms |
| playback first paint | 26.5 / 21.1 ms | 23.3 / 13.2 ms |

두 fixture 모두 fresh backend의 first와 같은 backend의 warm 흐름에서 설정→Draft→Playback→Result를 완료했다. HTTP 200, `Content-Encoding: gzip`, LIVE source, console/page error 0, result 화면 visible을 확인했다. 압축은 실제 wire transfer를 약 91% 줄였지만 decoded JSON, parse/validation, transient heap 비용을 없애지 않는다. Localhost request wall time은 gzip CPU, JVM/JIT와 측정 환경 영향을 받으므로 correctness threshold가 아니다.

## 검증 명령

```text
npm run reference:check
npm run reference:verify
npm run live:verify
npx tsc -b
npm run build
npm run bundle:verify
```

`live:verify`는 현재 TypeScript validator/adapter를 실제로 bundle/import한 뒤 valid/invalid signed-long 경계, options와 33MB full response, 10팀/50명, Draft/result/timeline/integrity 정규화 및 훼손 payload 거부를 검사한다. `bundle:verify`는 `dist/index.html`의 module entry에서 static import graph 전체를 순회해 reference fixture payload가 initial graph에 포함되지 않았는지와 lazy chunk 경계를 확인한다. 이 frontend-only 변경에는 backend full regression을 실행하지 않았다.

## 남은 제한과 다음 단계

- Gzip 적용 뒤 wire body는 약 1.88–2.79MB지만 decoded response는 여전히 20–34MB다. Compact projection/streaming과 worker parsing은 JSON parse·validation·heap 비용을 다루는 별도 hardening 대상이다.
- Ban entry에는 display name/portrait가 없어 frontend가 structured ChampionId에서 asset을 보완한다. API champion presentation/catalog를 추가하면 fallback과 영문 표시를 제거할 수 있다.
- 현재 API는 fresh single Game 1이다. BO3/BO5와 누적 Hard Fearless는 `SERIES_LIFECYCLE_V1` 범위다.
- Save/Load, Career/Season persistence는 아직 없다.
- CORS 개발 origin은 `http://localhost:5173` 경계를 따른다.

`DRAFT_ENGINE_PERFORMANCE_HARDENING_V1`과 `REAL_MATCH_TRANSPORT_COMPRESSION_V1`은 완료됐다. 다음 후보인 decoded payload/파싱 hardening과 `SERIES_LIFECYCLE_V1`은 서로 분리해 진행한다. Gameplay engine, balance와 backend 응답 의미를 frontend 최적화 과정에서 재정의하지 않는다.
