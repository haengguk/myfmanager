# Real Match API V1

## 목적과 범위

Real Match API V1은 실제 LCK roster, Professional Draft와 동결된 `MatchEngineV1`을 외부 HTTP client가 사용할 수 있게 하는 additive backend 경계다. 기존 `POST /api/matches/simulate`의 Dummy roster, champion selection과 timeline 응답은 그대로 유지한다. 현재 frontend는 새 API를 아직 호출하지 않는다.

한 요청은 항상 독립된 단판 Game 1이다. `RealDraftMatchOrchestrator.orchestrateV1(blueTeamCode, redTeamCode, seed)`가 fresh `SeriesDraftHistory`를 만들므로 HTTP 요청 사이에 Hard Fearless exclusion이나 mutable match state가 공유되지 않는다. BO3/BO5와 지속되는 series lifecycle은 이 계약에 포함되지 않는다.

## Endpoints

### `GET /api/v1/real-matches/options`

응답 schema는 `REAL_MATCH_OPTIONS_V1`이다. `LckTeamAssembler`와 기존 player/resource catalog에서 현재 지원하는 LCK 10팀, 팀당 5명, 총 50개의 unique stable `PlayerId`를 투영한다. 팀은 canonical team code 순서, 선수는 `TOP`, `JUNGLE`, `MID`, `ADC`, `SUPPORT` 순서다.

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
- `result`는 `MATCH_RESULT_SUMMARY_V1`의 winner/end reason/duration, 팀 최종 상태, 10명 KDA/CS/gold/XP/level을 그대로 투영한다.
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
| Policy hash | `61ec36e4ec36a3693a7fd34f9acbd018f615115dda45b558580f1ee7ff1a02a5` |
| Runtime profile | `BASELINE_V1` |
| Configuration hash | `c8cc557bd721228c473e30d31b7258510f9608a18098578bc1da36e603536215` |
| Gameplay rules | `MATCH_SIMULATOR_PRE_JUNGLE_RULES_V2` |
| Engine | `MATCH_SIMULATOR_ENGINE_IMPLEMENTATION_V6` |
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

오류 boundary는 `RealMatchApiV1Controller`에만 적용된다. Stack trace, exception 원문과 local resource path를 노출하지 않으며 기존 Champion selection과 legacy endpoint의 오류 의미를 바꾸지 않는다.

## 검증과 frontend handoff

Focused 검증은 6 suites / 31 tests, failures 0 / errors 0 / skipped 0으로 통과했다. 요청 parser, invalid 요청의 orchestrator/Random 미실행, service preflight와 output hash gate, HTTP options/success/error/serialization, same-request exact replay와 Game 1 isolation, direct orchestrator projection parity, 기존 Champion API와 Match Engine V1 계약을 포함한다.

최종 complete backend regression은 `gradlew.bat test --console=plain --no-daemon` 1회에서 179 suites / 2,011 tests, failures 0 / errors 0 / skipped 0으로 통과했다. Gradle wall time은 13분 41초다. 이후 executable production source, resource, Gradle과 shared fixture는 변경하지 않았다.

Frontend handoff artifact는 `backend/build/reports/real-match-api-v1/`에 있다. Contract, options example, 고정 `GEN` 대 `T1` seed `"73"` 요청/전체 응답, error contract, handoff 6개 JSON과 `SHA256SUMS.txt`를 제공한다. Manifest는 6/6 raw SHA가 통과했고 SHA-256은 `4b4d8c0a942db477a92db7ca17e9f5f767e326cc0ed92d49fd9366d081fc23b2`다. 고정 output hash는 `41eccdae5750d80f3d8b940f65ea93a8113148063fd4017dcde062b9f9fb651b`다.

Historical Match Engine V1 freeze artifact는 재생성하지 않았다. 기존 7-entry manifest SHA-256 `1f5bc20c347d25d833e822325de1fa294dc61d38c55da121ea30d15ab70a0728`을 raw SHA로 다시 검증해 handoff에 결속했다.

다음 milestone은 `REAL_MATCH_FRONTEND_V1`이다. 그 뒤 `SERIES_LIFECYCLE_V1`이 BO3/BO5와 누적 Hard Fearless history의 소유권을 별도 계약으로 다룬다. Save/Load, Career/Season도 아직 구현되지 않았다.
