# Draft Engine Performance Hardening V1

## 결과

`DRAFT_ENGINE_PERFORMANCE_HARDENING_V1`의 공식 status는 `DRAFT_ENGINE_PERFORMANCE_HARDENED`다. Frozen 12-fixture schedule에서 full automatic Draft median은 11.173초에서 4.032초로 63.911%, p90은 13.420초에서 4.314초로 67.852% 줄었다. 요구 gate인 median 40%, p90 30% 단축을 모두 통과했다.

| 지표 | frozen before | official after | 감소율 |
| --- | ---: | ---: | ---: |
| median | 11.173초 | 4.032초 | 63.911% |
| p90 | 13.420초 | 4.314초 | 67.852% |
| max | 15.412초 | 5.854초 | 62.016% |

Timing은 Windows 10 / Temurin OpenJDK 21.0.9 / Gradle 9.5.1 / max heap 3 GiB의 sequential fresh test JVM 관측값이다. 직렬 batch projection은 parallel throughput 보장이 아니고 JFR은 sampling evidence다.

## 변경 경계

한 번의 `DraftEngine.draft()`가 새 `DraftComputationContext`를 만들고 다음 반복 계산을 해당 Draft 수명 동안만 재사용한다.

- canonical champion combination별 feasible role assignments
- own/enemy candidate와 picked champion의 feasible positions
- structured Draft state/side/candidate/target-position별 completion
- structured Draft state/side/candidate별 pool health
- 한 `PreDraftPlanner.build()`의 archetype/champion candidate score

Cache key는 structured `ChampionId`, `Position`, `TeamSide`, Draft turn/picks/bans/Fearless exclusion을 사용한다. Display name, 배열 index, message/description을 identity로 사용하지 않는다. Static/global/resolver-owned/`ThreadLocal`/cross-match cache는 없고 새 Draft와 실패 뒤 다음 Draft는 항상 fresh context에서 시작한다.

Search depth, beam width, candidate limit, scoring/tuning, 후보 정렬, 각 점수 내부 floating-point 연산 순서, gameplay Random, API schema, production resources와 frontend는 변경하지 않았다. Planner 최적화는 동일한 완성 double 값을 comparator가 다시 사용할 뿐 산술식을 재배열하지 않는다.

## Exact parity

Global warmup 1회 뒤 12 fixtures를 각각 두 번 cached 측정하고, 각 fixture를 uncached primitive로 한 번 더 실행했다.

- upstream final identity: 24/24 exact
- 동일 JVM uncached reference: 480/480 turn decision/component/alternative/root score/counter exact
- Real Match API GEN–T1/73, HLE–DK/-73: 2/2 response/output/replay/simulator timeline/structured timeline/Random exact
- fresh JVM Draft probe A/B: byte exact, 공통 SHA-256 `abc0bf8ffd57d87c6bded2d4a58cb8bacac6c12ee9bb4defdf8e361738273511`

Frozen timing CSV의 prior JVM double은 438/480 exact였다. 차이는 unordered immutable collection의 JVM별 iteration이 비선택 후보의 floating reduction bit에 영향을 준 기존 특성이다. Production iteration이나 floating-point 순서를 바꿔 과거 artifact에 맞추지 않았다. 대신 prompt가 허용한 uncached primitive를 같은 JVM의 semantic acceptance oracle로 사용했고 prior timing CSV 비교는 observational field로 분리했다.

## Physical work

24 Draft aggregate counter는 다음과 같다.

| 계산 | uncached reference | cached | 감소율 |
| --- | ---: | ---: | ---: |
| role assignment | 24,172,180 | 591,296 | 97.554% |
| planner candidate | 184,948,920 | 16,269,480 | 91.203% |
| completion | 2,609,860 | 2,196,552 | 15.836% |
| pool health | 1,075,962 | 957,308 | 11.028% |

## 검증과 artifact

```text
gradlew.bat test \
  --tests com.lolfm.draft.DraftComputationContextTest \
  --tests com.lolfm.controller.DraftEnginePerformanceHardeningV1ArtifactsTest \
  --console=plain --no-daemon
gradlew.bat verifyDraftEnginePerformanceCrossJvmV1 --console=plain --no-daemon
gradlew.bat runDraftEnginePerformanceCandidateV1 --console=plain --no-daemon
gradlew.bat test --console=plain --no-daemon
gradlew.bat generateRealMatchApiV1HandoffRefreshOfficialV1 --console=plain --no-daemon
gradlew.bat runDraftEnginePerformanceHardeningV1 --console=plain --no-daemon
```

Focused Draft 묶음은 58 tests, full backend regression은 첫 실행에서 203 suites / 2,117 tests / failures 0 / errors 0 / skipped 0, Gradle wall 11분 33초로 통과했다. Full pass 뒤 executable source는 바꾸지 않았다.

공식 디렉터리는 `backend/build/reports/draft-engine-performance-hardening-v1/`이며 contract, fixture/turn CSV, cache statistics, before/after JFR hotspot, summary, analysis와 manifest를 포함한다. `SHA256SUMS.txt`는 7/7 통과했고 raw SHA-256은 `ae11f4eb368a8b796a113b32963048a764509b0bb98e27ebce313b7ec645d694`다. 이후 transport compression final source에 결속한 현재 Real Match API handoff manifest도 6/6 통과했고 raw SHA-256은 `9767356ce01243ff67441354a24d2d54df86fd30ed69cb57397ed36629876fad`다.
