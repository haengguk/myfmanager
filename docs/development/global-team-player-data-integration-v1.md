# 해외 팀·선수 데이터 등록 V1

기준 커밋: `87df2df344d41029567dd164ad886c5398477280`.

사용자가 보유한 선수정보·능력치 원본에서 LPL, LEC, LCS, LCP, CBLOL의 주전 데이터를 등록했다. 해외 46팀·230명, 기존 LCK를 포함하면 6개 리그·56팀·280명이다. 해외 identity·rating·proficiency는 2026-08-23 snapshot, career·contract·honors는 2026-08-24 snapshot이다. 이는 해당 파일의 작성 기준이며 오늘의 실제 이적 현황을 새로 조사한 결과가 아니다.

| 리그 | 팀 | 주전 선수 |
| --- | ---: | ---: |
| LCK | 10 | 50 |
| LPL | 12 | 60 |
| LEC | 10 | 50 |
| LCS | 8 | 40 |
| LCP | 8 | 40 |
| CBLOL | 8 | 40 |

## 구현 및 사용

- `GlobalRosterDatasets`가 해외 20개 classpath JSON의 버전·날짜·원본 SHA와 모집단을 명시한다. 원본 파일은 바이트 그대로 복사했으며 `선수정보/`, `능력치/`를 변경하지 않았다.
- 기존 rating·identity·proficiency loader에 명시적인 `PlayerResourceSpec` overload를 추가했다. 기존 기본 경로의 LCK 파일·버전·SHA는 유지한다. 포지션당 12개 능력치, 합법 챔피언 역할, proficiency 15~20 / 생략된 합법 역할 14, 네 파일의 선수 집합 일치를 검사한다.
- `GlobalTeamRosterCatalog`는 리그를 명시한 `TeamKey(leagueCode, teamCode)`로 조회한다. 동시 주전 모집단에서 cross-league PlayerId 중복을 거부한다.
- `catalog.snapshot(new TeamKey("LEC", "G2"))`는 선수 ID·포지션·능력치·숙련도를 담은 불변 로스터를 반환한다. `snapshot.assemble()` 또는 `catalog.assemble(key)`는 매번 새 `Team`과 `Player`를 생성하며 기존 `DraftTeamContext.from(team)`에서 사용할 수 있다.
- `rosterSnapshotIdentity`는 schema·리그·팀·세 gameplay 리소스의 원본 SHA·champion pool version으로 계산한다. 경력·계약 자료는 경기 계산에 넣지 않는다. 이 식별자는 로스터 식별자이며 기존 Career의 binding/receipt/runtime provenance를 대체하지 않는다.
- `RosterCareerReferences`는 원본 JSON을 유지하고 조회할 때 방어적 복사를 반환한다. null, 계약 후보일, 이름 별칭, 날짜 정밀도, public profile과 요청 로스터의 충돌, 출처와 설명을 보존한다. null을 0이나 임의 날짜로 채우지 않으며 계약 만료로 선수를 자동 제거하지 않는다.

## 조회 API

읽기 전용 새 경로와 schema `GLOBAL_ROSTER_REFERENCE_V1`을 추가했다.

| GET 경로 | 결과 |
| --- | --- |
| `/api/v1/reference/rosters` | 6개 리그와 팀·선수 수 |
| `/api/v1/reference/rosters/{leagueCode}` | 팀·주전 목록, 4개 source의 버전·SHA, 경력 원본의 scope·semantics |
| `/api/v1/reference/rosters/{leagueCode}/teams/{teamCode}` | 로스터 식별자, 5명 능력치·숙련도·경력 원문 |
| `/api/v1/reference/rosters/{leagueCode}/players/{playerId}` | 해당 리그의 선수 상세 및 소속 팀 로스터 식별자 |

예: `/api/v1/reference/rosters/LPL/players/player-breathe`는 요청 snapshot 소속 AL을 유지하면서 미확인 계약일과 공개 프로필 충돌 메모를 함께 제공한다. 알 수 없는 리그·팀·선수 및 다른 리그의 선수 조회는 기존 reference error 형식의 404를 반환한다. 표시용 이름으로 선수나 소속을 추론하지 않는다.

기존 `/api/v1/reference/leagues/LCK` 계약과 팀 정보 화면은 유지한다. 해외 nullable 원문을 기존의 non-null LCK V1 DTO에 억지로 넣지 않았다. 화면에 해외 리그 선택을 추가하는 일은 후속 UI 범위다.

## 국내 작업과의 경계

국내 순위·시즌 말 PO·최종 순위 작업과 파일을 분리했다. `CareerCompetition*`, `Series*`, `LeagueProductionSnapshotProvider`, `LckTeamAssembler`, 규칙 JSON, DB migration, frontend는 이 작업에서 변경하지 않았다. 기존 LCK season snapshot/provenance 및 관리 가능한 팀의 범위는 유지한다.

국제대회 실행은 아직 연결하지 않았다. 후속 First Stand·MSI·Worlds 구현에서는 이 등록부를 사용하되 참가 슬롯·시드·pool 정책과 영속 경기 binding에 로스터를 동결하는 연결이 필요하다. 다음 시즌 이적·로스터 갱신, 후보 선수, 급여·시장가·재정 기능은 포함하지 않는다. 아시안게임은 범위 밖이다.

## 검증

- 기존 player loader/catalog/assembler 및 LCK reference API 집중 테스트: 통과.
- 신규 등록부와 확장한 reference API 테스트: 2 suites / 14 tests, failures 0 / errors 0 / skipped 0. 모집단·전역 선수 ID, LCK 격리, 새 객체 생성·안정된 snapshot, 대표 null·충돌 보존, resource/identity 거부 및 API 리그 경계를 확인했다.
- `git diff --check`: 통과.
- 별도 작업 공간의 최종 `bash ./gradlew test --offline --console=plain --no-daemon`: 261 suites / 1,991 tests, failures 0 / errors 0 / skipped 2, `BUILD SUCCESSFUL`, 24분 54초. 전체 회귀는 한 번 실행했다. 기준 커밋 + 이번 해외 데이터 변경 및 기존 로컬 전용 runtime 리소스를 대상으로 했으며, 동시에 진행 중인 국내 대회 변경을 포함한 결과는 아니다.
- frontend 변경이 없어 frontend build·브라우저·통계 진단은 실행하지 않았다.

현재 저장소에 이 작업의 33개 파일만 반영했고, 최종 회귀 후 해당 파일이 검증한 작업 공간과 모두 일치함을 확인했다. 커밋·push는 수행하지 않았다.
