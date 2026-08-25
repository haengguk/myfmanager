# Real Match API V1

## 목적과 범위

Real Match API V1은 실제 LCK roster, Professional Draft와 동결된 `MatchEngineV1`을 외부 HTTP client가 사용할 수 있게 하는 additive backend 경계다. 기존 `POST /api/matches/simulate`의 Dummy roster, champion selection과 timeline 응답은 그대로 유지한다. Frontend V1-A의 compact reference 경계 위에 V1-B가 strict live HTTP provider를 추가했으며, 기본 진입 모드는 LIVE다.

한 요청은 항상 독립된 단판 Game 1이다. `RealDraftMatchOrchestrator.orchestrateV1(blueTeamCode, redTeamCode, seed)`가 fresh `SeriesDraftHistory`를 만들므로 HTTP 요청 사이에 Hard Fearless exclusion이나 mutable match state가 공유되지 않는다. BO3/BO5와 지속되는 series lifecycle은 이 계약에 포함되지 않는다.

## Endpoints

### `GET /api/v1/real-matches/options`

응답 schema는 `REAL_MATCH_OPTIONS_V1`이다. `LckTeamAssembler`와 기존 player/resource catalog에서 현재 지원하는 LCK 10팀, 팀당 5명, 총 50개의 unique stable `PlayerId`를 투영한다. 팀은 명시적 team code 순서, 선수는 `TOP`, `JUNGLE`, `MID`, `ADC`, `SUPPORT` 순서다. `teamCode`는 assembler가 받은 structured code를 유지하고 `displayName`은 `Team.getName()`에서 별도로 가져오므로 표시명을 바꿔도 API identity가 바뀌지 않는다.

응답에는 다음이 포함된다.

- `matchEngineContract`와 현재 production policy/configuration/rules/engine identity
- seed가 필수 signed Java long decimal string이라는 정책
- 표시용 team code/name, player ID/nickname/position
- 코드에서 확인한 resource version과 provenance hash

원본 ratings와 champion proficiency profile 전체는 노출하지 않는다. 표시 label은 gameplay identity나 engine output hash의 입력이 아니다.

### `POST /api/v1/real-matches/simulate`

요청은 정확히 다음 네 필드만 허용한다.

```json
{
  "schemaVersion": "REAL_MATCH_SIMULATE_REQUEST_V1",
  "blueTeamCode": "GEN",
  "redTeamCode": "T1",
  "seed": "73"
}
```

`schemaVersion`은 exact match여야 한다. Team code는 입력 가장자리의 공백을 제거하고 대문자로 canonicalize하므로 `" gen "`도 `GEN`으로 처리한다. 같은 팀이나 지원하지 않는 팀은 거부한다.

`seed`는 JSON number가 아니라 JSON string이며 Java signed `long` 범위의 canonical decimal이어야 한다. `0`, `73`, `-73`은 허용하지만 `+73`, `073`, `-0`, 공백 포함 값과 범위 초과 값은 거부한다. 암묵적인 seed나 `System.currentTimeMillis()` fallback은 없다.

`runtimeProfile`, candidate 활성화 flag, diagnostics flag, ratings/proficiency, Draft와 series history를 포함한 추가 필드는 Real Match V1 전용 parser가 `UNSUPPORTED_REQUEST_FIELD`로 거부한다. 전역 Jackson unknown-field 정책은 변경하지 않아 기존 endpoint의 호환성을 보존한다.

## 실행 흐름

```text
RealMatchApiV1Controller
→ RealMatchApiV1RequestParser
→ RealMatchApiV1Service
→ RealDraftMatchOrchestrator.orchestrateV1(...)
→ frozen MatchEngineV1Output 검증
→ RealMatchApiV1ResponseMapper
→ immutable REAL_MATCH_RESPONSE_V1
```

Controller는 Draft나 simulator를 직접 조립하지 않는다. Service는 실제 team code membership을 확인하고 기존 orchestrator의 fresh single-game overload만 호출한다. Orchestrator가 실제 10명 roster 조립, Professional Draft, final assignment와 Match Engine V1 실행을 소유한다.

응답 매핑 전에 다음을 모두 검증한다.

- mandatory execution provenance가 존재한다.
- output의 production policy와 configuration이 authoritative V1 policy와 exact다.
- execution provenance의 BLUE/RED team code와 match seed가 현재 HTTP 요청과 exact다.
- execution provenance와 final Draft가 모두 fresh Game 1이고 이전 Hard Fearless history/exclusion이 비어 있다.
- runtime profile, gameplay configuration, rules/engine, Draft/final assignment와 result provenance가 서로 exact다.
- `MatchEngineV1Output.hasValidOutputHash(...)`가 실제 structured timeline과 output envelope에 대해 `true`다.

검증에 실패하면 부분적인 Draft/result/timeline을 반환하지 않고 structured 500 오류를 반환한다.

## Response contract

성공 응답 schema는 `REAL_MATCH_RESPONSE_V1`이며 top-level은 다음과 같다.

```text
REAL_MATCH_RESPONSE_V1
├── schemaVersion
├── matchIdentity
├── seed
├── teams
├── draft
├── result
├── timeline
└── integrity
```

- `seed`, `PlayerId`, `ChampionId`는 JSON string이다.
- `TeamSide`, `Position`, Draft action, end reason, event type, lane, combat/structure/activity 값은 enum string이다.
- `teams`는 최종 champion assignment와 catalog 기반 display name/portrait를 함께 제공한다.
- `draft`는 rules identity/hash, Game 1, Draft 전 exclusion, ordered ban/pick decision, 양 팀 ban/pick, final player-position-champion assignment와 final hash를 제공한다.
- `result`는 `MATCH_RESULT_SUMMARY_V1`의 winner/end reason/duration, 팀 최종 상태, 10명 KDA/CS/gold/XP/level과 V8의 `PLAYER_ABILITY_PROFILE_V1`을 그대로 투영한다. 선수별 profile은 12개 base/realized/delta rating과 선택 champion proficiency 및 실행 보정을 stable `PlayerId`/position/champion assignment에 결속한다.
- `timeline`은 모든 immutable structured event와 snapshot을 투영한다. Stable participant ID, champion ID, `CombatSource`, objective/structure field, structured data와 player progression을 보존한다. `displayMessage`는 표시용이다.
- `integrity`는 contract/policy/configuration/rules/engine identity, input/resource/replay/timeline/output hash와 Random fingerprint를 제공한다.

Safety timeout에서는 `result.winner`와 `timeline.winner`가 `null`일 수 있다. Event의 actor, lane, participant, combat/structure field도 해당 event 의미에 없으면 `null`이다. 전체 enum 값과 nullable field 목록은 generated contract와 handoff artifact에 고정한다.

HTTP DTO는 `Team`, legacy `MatchTimeline`, mutable `MatchEvent`/`MatchSnapshot`, `RealDraftMatchResult` 또는 package-private simulator diagnostic 객체를 raw graph로 반환하지 않는다. Mapper는 frozen output과 catalog identity를 field-by-field 투영하며 winner, 결과, Draft, participant나 hash를 display text에서 재계산하지 않는다.

## Production policy와 무결성

V1은 다음 runtime만 허용한다.

| 항목 | 값 |
| --- | --- |
| Contract | `MATCH_ENGINE_CONTRACT_V1` |
| Policy | `MATCH_ENGINE_V1_BASELINE_PRODUCTION_POLICY` |
| Policy hash | `cd5512dcfeae92ff3222367d4aa8a9fc352cc1bf57f9e3ea20382e6a5959bf0a` |
| Runtime profile | `BASELINE_V1` |
| Configuration hash | `c8cc557bd721228c473e30d31b7258510f9608a18098578bc1da36e603536215` |
| Gameplay rules | `MATCH_SIMULATOR_PRE_JUNGLE_RULES_V2` |
| Engine | `MATCH_SIMULATOR_ENGINE_IMPLEMENTATION_V8` |
| Matchup / Composition | `OFF` / `OFF` |
| Jungle contribution | `DISABLED_NOT_INTEGRATED` |
| Economy / Tempo candidate | `false` / `false` |

이번 API는 engine input/output, gameplay 공식, Draft, resource와 Random 순서를 변경하지 않는다. Presentation metadata도 동결된 engine output hash를 재정의하지 않는다.

## Error contract

오류 응답 schema는 `REAL_MATCH_API_ERROR_V1`이다.

```json
{
  "schemaVersion": "REAL_MATCH_API_ERROR_V1",
  "code": "UNKNOWN_TEAM",
  "field": "blueTeamCode",
  "message": "지원하지 않는 BLUE 팀 코드입니다."
}
```

| HTTP | Code |
| ---: | --- |
| 400 | `MALFORMED_REQUEST`, `INVALID_REQUEST_SCHEMA`, `BLUE_TEAM_REQUIRED`, `RED_TEAM_REQUIRED`, `UNKNOWN_TEAM`, `SAME_TEAM_NOT_ALLOWED`, `INVALID_SEED`, `UNSUPPORTED_REQUEST_FIELD` |
| 422 | `REAL_MATCH_PREFLIGHT_FAILED` |
| 500 | `ENGINE_OUTPUT_INTEGRITY_FAILED`, `REAL_MATCH_INTERNAL_ERROR` |

오류 boundary는 `RealMatchApiV1Controller`에만 적용된다. `RealDraftMatchPreflightException`으로 식별된 roster/Draft 사전 검증 실패만 422로 변환하며, 엔진·입력 생성·오케스트레이션에서 발생한 일반 `IllegalArgumentException`을 preflight로 오인하지 않고 500 `REAL_MATCH_INTERNAL_ERROR`로 처리한다. Stack trace, exception 원문과 local resource path를 노출하지 않으며 기존 Champion selection과 legacy endpoint의 오류 의미를 바꾸지 않는다.

## 검증과 frontend handoff

Frontend source scanner 하드닝 focused 검증은 새 scanner 계약과 기존 영향 5개 class를 묶어 6 suites / 214 tests, failures 0 / errors 0 / skipped 0으로 통과했다. 명시적 text extension만 UTF-8로 읽고 `.woff2` 등 binary, `node_modules`, `dist`, `build`와 source root 밖을 제외한다. 허용 text의 encoding/I/O 오류와 `.ts`/`.tsx` 금지 문자열은 계속 fail-fast한다.

Real Match API focused 검증은 V8 `PlayerAbilityProfileContractTest`를 포함한 9 suites / 55 tests, failures 0 / errors 0 / skipped 0으로 통과했다. 요청 parser, typed preflight/internal error 분리, 요청 team/seed와 output provenance 결속, Game 2/inherited history 거부, display name과 team code 분리, HTTP options/success/error/serialization, same-request exact replay와 Game 1 isolation, direct orchestrator projection parity, 기존 Real Draft/Champion API/Match Engine V1 계약, ability profile projection과 source-binding test를 포함한다.

최종 complete backend regression은 메모리 한도에 맞춘 일회성 JVM/worker 제한 아래 default `test` 전체를 첫 실행에서 수행했고 196 suites / 2,091 tests, failures 0 / errors 0 / skipped 0으로 통과했다. Aggregate JUnit XML은 810.092초, Gradle wall time은 13분 43초다. 테스트 선택이나 default diagnostic 제외 경계는 바꾸지 않았으며, 이후 executable production source, resource, Gradle과 shared fixture는 변경하지 않았다.

Frontend handoff artifact는 `backend/build/reports/real-match-api-v1/`에 있다. Contract, options example, 고정 `GEN` 대 `T1` seed `"73"` 요청/전체 응답, error contract, handoff 6개 JSON과 `SHA256SUMS.txt`를 제공한다. 현재 V8 고정 결과는 GEN(BLUE) 승리, `NEXUS_DESTROYED`, 3,430초(57분 10초), output hash `bdc597af083aa4f081cf4fe7a242d0e36eec7744b186d998d6f83b717648e874`다. Transport compression final source에 결속한 manifest는 6/6 raw SHA가 통과했고 raw SHA-256은 `9767356ce01243ff67441354a24d2d54df86fd30ed69cb57397ed36629876fad`다.

Artifact writer는 전체 XML의 단순 개수만 신뢰하지 않는다. 필수 8개 suite와 최소 test 수, failures/errors/skipped 0을 확인하고, 전용 dynamic test가 기록한 production source 502 files / `e23f2d2149edd3a7478b5333f126e876d969b5a3c71c20b670447cc9cbd71817` 및 API verification source 9 files / `a83456b742e32a03810ee9c9584a2015b29f683b96c6d478337d2bec957eb9f9`를 현재 tree와 exact 비교한다. 생성 전에는 options/roster/Draft/result/final snapshot/structured participant/hash/Random/선수 ability profile의 V8 semantic audit와 same-request replay도 수행한다. 두 fresh JVM candidate A/B의 JSON 6개와 manifest는 7/7 byte-for-byte exact였고, 감사 통과 뒤에만 공식 local artifact로 승격했다. 따라서 이전 V6/V7 handoff, 이전 clean XML이나 고정 base commit/run-count 표시는 새 evidence로 재사용할 수 없다.

Historical Match Engine V1 freeze artifact는 V6 당시 evidence로 재생성하지 않았다. 기존 7-entry manifest SHA-256 `1f5bc20c347d25d833e822325de1fa294dc61d38c55da121ea30d15ab70a0728`을 raw SHA로 다시 검증해 현재 V8 handoff에 결속했다. Historical freeze, 현재 production implementation V8, 현재 frontend handoff는 서로 다른 evidence 범위다.

## Frontend V1-A reference와 V1-B live 경계

Frontend V1-A는 backend wire 응답을 full response 타입으로 가장하지 않는다. Node 기본 모듈 기반 extractor가 handoff manifest 6/6, manifest raw SHA, fixed request identity, engine V8와 output hash를 먼저 검증한 뒤 `REAL_MATCH_FRONTEND_REFERENCE_PROJECTION_V1`을 결정적으로 생성한다. 원본 33,617,922 bytes에서 선택한 767,407-byte projection은 실제 options 10팀/50명, ordered Draft 20개와 final assignment 10개, structured event 287/517개, 실제 snapshot 59/344개, final result/integrity와 10개 ability profile을 포함한다. Event는 `LEVEL_UP`과 `ITEM_STAGE_REACHED`를 제외한 structured gameplay event를 보존하고 snapshot은 첫/마지막과 정확한 60초 지점을 선택한다.

두 공급자는 `provider → normalized match session → Draft/Playback/Result view model` 이후 경계를 공유한다. LIVE는 `GET /options`와 `POST /simulate`의 전체 JSON을 strict runtime validator로 검증하고, structured event/snapshot/identity만 정규화한다. Event 의미는 backend enum과 structured field로만 분류하며 `displayMessage`를 gameplay identity로 파싱하지 않는다. 원본 응답 문자열과 DTO graph는 정규화 뒤 session에 보존하지 않는다.

LIVE가 기본값이다. `VITE_REAL_MATCH_DATA_SOURCE=reference`를 명시한 빌드만 checked-in fixture를 dynamic import하며, LIVE 실패 시 REFERENCE로 자동 전환하지 않는다. API base와 options/simulate timeout은 각각 `VITE_REAL_MATCH_API_BASE_URL`, `VITE_REAL_MATCH_OPTIONS_TIMEOUT_MS`, `VITE_REAL_MATCH_SIMULATE_TIMEOUT_MS`로 중앙 설정한다. UI는 options loading/retry, 요청 단계와 경과 시간, 취소, retry, stale response 격리와 중복 submit 방지를 제공한다.

V1-B는 임의의 서로 다른 두 팀과 canonical signed-long seed를 실제 API로 실행한다. V1-A reference 조합은 계속 `GEN` BLUE 대 `T1` RED, seed `"73"`, fresh Game 1 하나로 고정한다. 그 뒤 `SERIES_LIFECYCLE_V1`이 BO3/BO5와 누적 Hard Fearless history를 별도 계약으로 다룬다. Save/Load, Career/Season도 아직 구현되지 않았다. 상세 실행·성능·E2E 근거는 [Real Match Frontend V1-B](../development/real-match-frontend-v1-b.md)에 기록한다.
