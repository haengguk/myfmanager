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

- Full response는 20–34MB이며 local simulation까지 포함한 응답에 35–54초가 걸렸다. 압축, projection endpoint, streaming/worker parsing과 정확한 progress는 별도 hardening 대상이다.
- Ban entry에는 display name/portrait가 없어 frontend가 structured ChampionId에서 asset을 보완한다. API champion presentation/catalog를 추가하면 fallback과 영문 표시를 제거할 수 있다.
- 현재 API는 fresh single Game 1이다. BO3/BO5와 누적 Hard Fearless는 `SERIES_LIFECYCLE_V1` 범위다.
- Save/Load, Career/Season persistence는 아직 없다.
- CORS 개발 origin은 `http://localhost:5173` 경계를 따른다.

다음 제품 단계는 payload 전달/파싱 hardening과 `SERIES_LIFECYCLE_V1` 설계를 분리해 진행한다. Gameplay engine, balance와 backend 응답 의미를 frontend 최적화 과정에서 재정의하지 않는다.
