# Team and Player Information API V1

## 목적과 범위

`TEAM_AND_PLAYER_INFORMATION_API_V1`은 현재 승인된 LCK 10팀·주전 50명의 stable
`PlayerId`, authored rating, sparse champion proficiency, 개인 정보·계약·커리어·주요 수상
경력을 하나의 immutable read-only catalog로 결합한다. V1은 LCK current starters만 다루며
다른 리그, 교체 선수, 선수 생성/수정, 이적·훈련·재정, DB persistence와 frontend 화면은 포함하지
않는다.

이 catalog는 presentation/reference read model이다. Match, Season, standings, save state를
보유하거나 변경하지 않고 Match Engine configuration/resource/replay/output hash 및 seeded Random
소비 순서에 참여하지 않는다.

## Source authority와 runtime packaging

| 역할 | Version / snapshot | Raw SHA-256 |
| --- | --- | --- |
| Player identity | `lck-player-identities-2026-08-21-v1` / `2026-08-21` | `badbbaa3ae7fbe5eaaf83ee8e97a93134476493a45167ec3d1637c7243909018` |
| Player rating | `lck-player-ratings-2026-08-19-v1` / `2026-08-19T02:57:00+09:00`, cutoff `2026-08-16` | `2312a8bc7d222fd63b57d1255210fb25104432a90a954d854b2090cc2acb28e0` |
| Champion proficiency | `lck-champion-proficiency-2026-08-21-v1` / research `2026-08-21` | `2c36b8a109aba9dfe84c1da319fe02708a72a1341d334dc6d5e3f605b0023aad` |
| Career/contract/honors | `lck-player-career-contract-honors-2026-08-24-v1` / `2026-08-24` | `4e4f01fe72f68aca7dcb93afb72b43273201ce0daa7d63613f628597ff41ff19` |

루트 `능력치/`와 `선수정보/`는 `.gitignore`로 보호되는 사용자 소유 authoring/reference
자료다. API runtime은 이 경로를 열지 않는다. 권위 LCK career aggregate JSON만 원본 바이트를
재직렬화하지 않고
`backend/src/main/resources/players/lck-player-career-contract-honors-2026-08-24-v1.json`에
패키징한다. Loader는 classpath resource raw bytes의 SHA를 semantic parse보다 먼저 검사한다.

원본 `SHA256SUMS.txt` 전체와 validation report를 별도로 검증했고, report의 50 players,
10 teams, 248 team-history rows, 154 team-achievement rows, 21 individual awards, 248 source rows와
모든 필수 coverage가 실제 aggregate 측정값과 일치한다. CSV, XLSX와 팀별 JSON은 경쟁 runtime
authority가 아니다.

## Join과 immutable catalog

실행 흐름은 다음과 같다.

```text
versioned classpath resources
→ raw SHA/version/scope/semantic validation
→ PlayerId exact subject-set validation
→ current team/position/nickname consistency validation
→ TeamPlayerInformationCatalog singleton materialization
→ deterministic HTTP projection
```

Identity/rating/proficiency/career 결합의 유일한 person key는 `PlayerId`다. Rating과
proficiency 원본의 current slot key는 기존 `PlayerIdentityCatalog`를 통해 `PlayerId`로 변환한다.
Nickname, display team name, 배열 index, career history의 과거 팀명은 join key가 아니다.

Catalog 생성은 다음 조건을 fail-closed한다.

- 네 subject set exact equality와 unique `PlayerId` 50
- team code exact set 10, 팀별 `TOP/JUNGLE/MID/ADC/SUPPORT` 각 1명
- current team/position/nickname의 identity exact equality
- rating 50 × 12, canonical key set, 정수 1..20
- proficiency owner/current position/legal champion-role binding과 732 authored entries
- career scope/semantics/snapshot, age와 contract-day snapshot 계산, 248/154/21/248 counts
- duplicate/missing/unknown/malformed `PlayerId`와 declared/measured count drift

Catalog order는 team code 오름차순 뒤 `Position` 선언 순서다. Team history, honors와 sources는
원본 순서를 보존한다. Authored champion proficiency는 value 내림차순, 동률은 `ChampionId`
오름차순이다. Champion 한글/영문 표시명과 portrait는 기존 `ChampionCatalog` metadata에서
가져온다.

Catalog identity schema는 `TEAM_AND_PLAYER_INFORMATION_CATALOG_V1`, version은
`lck-team-and-player-information-2026-08-24-v1`, hash algorithm은 SHA-256이다. 네 raw resource
identity, active champion pool identity, ordered player/rating/proficiency presentation binding을
canonicalize한 현재 catalog hash는
`4b5af4a49b5299b850015ea162be7e28543b1c4cb87e672120f84b26af815504`다.

## HTTP API

Base namespace는 `/api/v1/reference/leagues`다.

| Endpoint | Success schema | 의미 |
| --- | --- | --- |
| `GET /LCK` | `TEAM_PLAYER_INFORMATION_METADATA_V1` | provenance, counts, semantics, limitations |
| `GET /LCK/teams` | `TEAM_PLAYER_INFORMATION_TEAMS_V1` | team code 순 10팀과 주전 lineup |
| `GET /LCK/teams/{teamCode}` | `TEAM_PLAYER_INFORMATION_TEAM_V1` | exact team code의 Position 순 주전 5명 |
| `GET /LCK/players` | `TEAM_PLAYER_INFORMATION_PLAYERS_V1` | 50명 summary, optional exact `teamCode`/`position` |
| `GET /LCK/players/{playerId}` | `TEAM_PLAYER_INFORMATION_PLAYER_V1` | stable `PlayerId` full detail |

모든 성공 응답은 `schemaVersion`, `leagueCode=LCK`와 공통 `catalog` block을 가진다. 공통 block은
catalog version/hash, champion pool version과 네 source version/raw hash/snapshot/research/cutoff를
제공한다. Team에는 추측한 display name이나 logo를 넣지 않고 authored `teamCode`만 사용한다.

Player summary는 `playerId`, nickname, current team code, position, nationality, birth date,
contract end date/status를 제공한다. Player detail은 다음을 additive하게 제공한다.

- legal name, birth date, `ageAtSnapshot`, nationality
- contract end/status/source와 `daysRemainingAtSnapshot`
- debut, `yearsActiveAtSnapshot`, ordered team history와 date precision
- major team achievements, individual awards, source citations와 data-quality metadata
- approximate public tournament prize money의 USD 값과 제한된 의미
- 공통 6개 뒤 role-specific 6개인 authored rating 12개와 1..20 scale
- authored sparse champion proficiency, 1..20 scale, neutral fallback 14와 sparse semantics

`OVR`, `CA`, salary, contract start, buyout와 market value는 계산하거나 노출하지 않는다. Age와
contract days는 현재 날짜로 재계산하지 않고 2026-08-24 snapshot 값을 명시적으로 투영한다.
Unknown/null source 값은 0, 빈 문자열 또는 가짜 날짜로 채우지 않는다.

Players query는 `teamCode`, `position`만 각각 한 번 허용한다. 값은 case-sensitive exact match다.
Unknown field, duplicate query, lowercase/unknown team 또는 position은
`REFERENCE_QUERY_INVALID`로 거부한다. Pagination은 bounded 50명인 V1에 포함하지 않는다.

## Error contract

오류 schema는 `TEAM_PLAYER_INFORMATION_API_ERROR_V1`이며 `schemaVersion`, stable `code`, nullable
`field`, 안전한 `message`를 제공한다.

| HTTP | Code |
| ---: | --- |
| 400 | `REFERENCE_QUERY_INVALID` |
| 404 | `REFERENCE_LEAGUE_NOT_FOUND`, `REFERENCE_TEAM_NOT_FOUND`, `REFERENCE_PLAYER_NOT_FOUND` |
| 500 | `PLAYER_INFORMATION_RESOURCE_INTEGRITY_FAILURE` |

Malformed/unknown player path는 nickname fallback 없이 stable player 404로 처리한다. 오류 boundary는
이 reference controller에만 적용되며 stack trace, exception message, SQL 또는 로컬 절대 경로를
응답에 노출하지 않는다. Resource가 무결성 검사를 통과하지 못하면 application catalog 생성 자체가
fail-closed한다.

## 결정성과 비영향 계약

DTO는 mutable domain graph를 직접 반환하지 않고 field-by-field immutable projection을 만든다.
동일 process의 반복 요청과 두 fresh JVM의 metadata/team/player JSON 및 catalog hash가 byte-identical해야
한다. 8KB 이상 player detail은 기존 server compression 설정을 사용하며 gzip과 identity 응답은 같은
JSON 의미를 가져야 한다.

Focused verification은 information 호출 전후 같은 GEN–T1 seed 73의 input/output/final Draft,
structured timeline, Random fingerprint와 gameplay resource provenance exact equality를 확인한다.
Career SHA나 information catalog hash는 Match Engine provenance에 들어가지 않는다. 별도 Season과
standings를 생성한 뒤 모든 information route를 호출해도 두 state JSON은 변하지 않는다.

## 제한과 다음 단계

V1은 current LCK starters-only snapshot이다. Substitute, 다른 league, historical roster selection,
시간 진행에 따른 age/contract 변화, portrait/team logo authoring, mutation API, persistence와 인증은
포함하지 않는다. 다음 단계는 이 API의 structured field만 소비하는
`TEAM_AND_PLAYER_INFORMATION_FRONTEND_V1`이다.

