# Real Match Transport Compression V1

## 상태와 범위

최종 상태는 `REAL_MATCH_TRANSPORT_COMPRESSION_AND_LIVE_E2E_ACCEPTED`다.

이 문서의 HTTP byte, wall time, Chrome E2E와 두 fixture 결과는 V8 당시의 historical 측정이다. 현재 production은 V9이며 압축 설정과 negotiation 계약은 유지되지만 V8 payload 크기·winner·duration·output hash를 현재 gameplay 기준으로 재사용하지 않는다. V9의 focused transport 계약은 gzip/identity/무헤더가 동일한 current response를 전달하는지 별도로 검증한다.

이 작업은 20~34MB Real Match JSON의 HTTP wire transfer를 gzip으로 줄인다. Match Engine, Draft scoring/search, gameplay, response schema와 frontend 화면 의미는 바꾸지 않는다. 동일 요청의 Draft 20 decisions, final assignment, winner/duration, 모든 event/snapshot, output/replay/simulator timeline/structured timeline/Random fingerprint가 압축 전 identity, 압축 후 identity, gzip 해제 결과에서 exact다.

## Production 설정

`backend/src/main/resources/application.properties`는 Spring Boot 표준 server compression을 사용한다.

```properties
server.compression.enabled=true
server.compression.mime-types=application/json
server.compression.min-response-size=8KB
```

`Accept-Encoding: gzip`은 gzip을 협상하고 `identity`와 무헤더 요청은 uncompressed JSON을 유지한다. Controller가 DTO를 byte array로 직접 압축하지 않고 frontend도 수동 해제하지 않는다. 전송 설정은 gameplay configuration, replay provenance와 output hash에 포함되지 않는다.

8KB는 Content-Length가 알려진 작은 응답의 하한이다. 현재 Tomcat MVC의 negotiated streaming/unknown-length 응답은 작은 options/error도 gzip될 수 있다. 이를 강제로 되돌리기 위한 controller 전용 압축 로직은 추가하지 않았다. Identity와 무헤더 클라이언트 호환성은 유지된다.

## HTTP 실측

Windows 10, OpenJDK 21.0.9, 12 logical processors의 localhost에서 fixture별 fresh JVM first와 같은 JVM warm 요청을 측정했다. 외부 probe는 raw gzip body를 직접 세고 해제된 JSON과 frozen identity를 검증했다.

| Fixture | 단계 | 압축 전 HTTP | 압축 후 HTTP | decoded | raw gzip wire | 압축률 | 감소율 |
| --- | --- | ---: | ---: | ---: | ---: | ---: | ---: |
| GEN–T1 / `73` | first | 9.240초 | 9.600초 | 33,617,921 B | 2,789,995 B | 8.299% | 91.701% |
| GEN–T1 / `73` | warm | 6.779초 | 6.399초 | 33,617,921 B | 2,789,995 B | 8.299% | 91.701% |
| HLE–DK / `-73` | first | 7.677초 | 8.036초 | 20,315,047 B | 1,875,883 B | 9.234% | 90.766% |
| HLE–DK / `-73` | warm | 4.843초 | 5.283초 | 20,315,047 B | 1,875,883 B | 9.234% | 90.766% |

두 fixture 모두 85% 최소 감소 조건을 통과했다. Localhost first/warm wall time은 gzip CPU와 JVM/JIT 변동 때문에 항상 단축되지 않으며 correctness gate가 아니다.

Test-side phase 관측 median은 다음과 같다. `Draft`와 `roster/Draft/input`은 독립 계측이므로 합산하지 않는다.

| Phase median | GEN–T1/73 | HLE–DK/-73 |
| --- | ---: | ---: |
| Draft | 4,766.271 ms | 3,692.688 ms |
| roster/Draft/input preparation | 4,806.777 ms | 3,776.633 ms |
| Match Engine | 1,014.952 ms | 522.988 ms |
| output integrity | 372.971 ms | 204.545 ms |
| response mapping | 64.796 ms | 34.658 ms |
| JSON serialization | 182.786 ms | 113.790 ms |
| application boundary | 6,260.212 ms | 4,539.359 ms |

## Chromium live E2E

기존 frontend와 최종 packaged backend를 실행하고 별도 Chrome session에서 설정→자동 Draft→경기 재생→결과 화면을 first/warm으로 수행했다. Wire byte는 CDP `Network.loadingFinished.encodedDataLength`를 사용했다. `response.text()`의 Blob size는 decoded bytes이므로 wire evidence로 사용하지 않았다.

| Fixture | 단계 | CDP encoded | decoded | request+download | parse | validation | normalization | request→Draft | first playback paint |
| --- | --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| GEN–T1/73 | first | 2,828,788 B | 33,617,921 B | 9,819.5 ms | 115.2 ms | 13.3 ms | 2.8 ms | 10,153 ms | 26.5 ms |
| GEN–T1/73 | warm | 2,828,788 B | 33,617,921 B | 7,033.5 ms | 127.8 ms | 11.4 ms | 1.8 ms | 7,503 ms | 21.1 ms |
| HLE–DK/-73 | first | 1,902,063 B | 20,315,047 B | 8,653.8 ms | 78.4 ms | 12.0 ms | 3.8 ms | 8,999 ms | 23.3 ms |
| HLE–DK/-73 | warm | 1,902,063 B | 20,315,047 B | 5,471.0 ms | 69.2 ms | 9.5 ms | 1.3 ms | 5,950 ms | 13.2 ms |

모든 실행은 HTTP 200, `Content-Encoding: gzip`, LIVE source, 결과 화면 visible, console error 0, page error 0, runtime validation error 0, reference fallback 0이었다.

## 결과 무결성과 검증

V8 측정 당시 GEN–T1/73은 BLUE 승, 3,430초, output `bdc597af083aa4f081cf4fe7a242d0e36eec7744b186d998d6f83b717648e874`였고 HLE–DK/-73은 RED 승, 2,840초였다. Matchup/Composition activation 전 V9 GEN–T1/73 계약은 RED 승, 1,750초, output `40c8786ebece2d9abc71d95c304d39ef8f63f2b3277237d1aeaf0a3cf1d76c34`였다. 해당 hash는 seeded Draft selection policy와 trace의 additive identity를 포함한다. 아래 BootRun/JAR/Chrome 수치는 V8 실행 기록이며 현재 V9 외부 성능 실측으로 해석하지 않는다. 활성화 뒤 smoke 값은 [production activation](match-engine-v9-matchup-composition-production-activation.md)에 별도로 기록한다.

Backend focused는 compression negotiation, fixed API, service/orchestration, Draft parity, same-seed Random과 Draft hardening artifact를 포함한다. Candidate가 clean한 뒤 production/runtime tree를 동결했고 complete backend regression을 한 번 실행했다.

| 검증 | 결과 |
| --- | --- |
| Backend full regression | 204 suites / 2,118 tests / failures 0 / errors 0 / skipped 0 |
| Full Gradle wall | 10분 37초 |
| Frontend production build | 87 modules, clean |
| Reference/UI/live/bundle | 모두 clean |
| External bootRun/JAR probes | 4/4 fixture-runtime combinations clean |
| Chromium | 4/4 first/warm flows clean |

Production source identity는 503 files / `eb54c41b515703d571eed744bbe06975ca0a71c1bd48d819da1c3afa1e24985a`, resource identity는 17 files / `5e2e0c0b14932399fb1d92de482ba695aac9bac9b95f54b7ce379f99bdab1b35`, runtime configuration identity는 `6aa94ed14349259133e9cee3c713cccdb6c725cbd8bd6b6b04f6e66d79369f87`다.

Compression resource binding 때문에 Real Match API handoff를 공식 workflow로 갱신했다. 새 manifest raw SHA-256은 `9767356ce01243ff67441354a24d2d54df86fd30ed69cb57397ed36629876fad`이며 fixed response 의미는 이전 handoff와 exact다. Historical Match Engine, 13G-B/B2/B3와 performance/Draft artifact는 재생성하지 않았다.

## Artifact

공식 경로는 `backend/build/reports/real-match-transport-compression-v1/`이다.

- `real-match-transport-compression-v1-contract.json`
- `real-match-transport-compression-v1-http-runs.csv`
- `real-match-transport-compression-v1-browser-runs.csv`
- `real-match-transport-compression-v1-summary.json`
- `real-match-transport-compression-v1-analysis.md`
- `SHA256SUMS.txt`

Manifest 5/5가 통과했고 raw SHA-256은 `860f6cea4e8dfc42e1a38148dc5c2763331bcd899d784670af4e3222d89a068f`다. Upstream performance manifest는 `c9b4659c4d602fb33c7295885cdc2685a4991469cc4cc0b097ca2d1a20cb26ee`, Draft hardening manifest는 `ae11f4eb368a8b796a113b32963048a764509b0bb98e27ebce313b7ec645d694`로 읽기 검증했다.

## 해결한 것과 남은 제한

해결한 것은 HTTP wire transfer다. Decoded JSON은 여전히 20~34MB이며 JSON parse, runtime validation, normalization과 transient browser heap 비용은 남는다. 더 줄이려면 compact projection, streaming, Web Worker 또는 async job을 각각 별도 additive API/runtime 계약으로 설계해야 한다. 이번 V1은 그 작업을 포함하지 않는다.
