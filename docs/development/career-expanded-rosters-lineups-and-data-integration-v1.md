# Career 확장 명부·선발·육성팀 연결 V1

## 범위와 기준

실제 시작 HEAD는 `5e418e5e5ead8fbf0f5371629f29a55d95b6be53`이다. 시작 시 사용자 소유 `prompts/`만 미추적 상태였다. 이번 작업은 시즌 전환 화면 경합, 확장 선수 자료의 배포용 연결, Career 소속·선발 변경과 경기/다음 시즌 적용을 함께 구현한다. 사용자 원본 `선수정보/`, 기존 280명 resource 및 프롬프트는 변경하지 않는다. 자동 커밋·푸시·배포는 수행하지 않는다.

검증 스킬의 변경 위험별 최소 검증 원칙을 적용했다. 기존 catalog, Career persistence, 실제 Series/League 테스트와 기존 프런트 verifier를 확장한다. 대규모 통계, 능력치 감사, 새 CrossJvm probe는 실행하지 않는다.

## 데이터와 모델

- 전체 명부 460명 = 기존 선발 280명 + 연구 자료의 추가 180명. 선수 ID가 identity이며 동일 포지션의 여러 선수를 허용한다.
- 경쟁 팀은 기존 56팀 그대로다. 조직은 79개로, 경쟁 구단 56개, LCK 연계 육성 배치 10개, 명시적 연계 육성 조직 11개, 이번 경쟁 팀 집합 밖의 LPL 조직 UP/OMG 2개다. 연계 관계는 이번 게임의 이동 허용 정책이며 실제 법적 소유 관계를 주장하지 않는다.
- 추가 선수의 초기 분류는 1군 명부 76명, 육성팀 97명, 소속 미확인 3명, 무소속 4명이다. 1군이라는 이유로 자동 선발하지 않는다. 경쟁 팀이 없는 조직 소속 선수도 명부에는 남고 출전 불가 이유를 가진다.
- 기본 선발은 56팀의 기존 5명이다. 해외 임시 대표 선정은 실제 선택된 5명만 평가한다. 후보·육성팀 규모는 대표 선정 점수를 증가시키지 않는다.
- 무소속 Bo/BeryL/FoFo/FATE의 조회와 실제 영입 가능 여부를 구분한다. Armao/Empyros/Renye의 소속 불확실성을 실제 1군 소속으로 확정하지 않는다. Ghost의 원본 충돌·임시 포지션도 보존한다.

`players.json`을 추가 자료 통합 원본으로 사용했고, 별도 ratings/proficiencies 파일의 ID·수치·추정 근거와 일치함을 확인했다. 하나의 production resource `backend/src/main/resources/rosters/expanded-player-directory-v1.json`에 정규화했다. 서버가 gitignored 연구 폴더의 절대 경로를 읽지 않는다.

원본 등록 조직과 팀 코드가 어긋난 12건을 `normalizationChanges`에 기록했다. Hang은 WBG, Missing/Croco는 LNG로 연결했다. Ultra Prime 4명과 Oh My God 5명은 각각 UP/OMG 조직으로 연결하며 PNG/기존 WBG 선수가 되지 않는다. 표시 이름을 실행 중 파싱하는 코드 대신 정규화된 조직/경쟁 팀 ID를 사용한다.

역할별 12개 스킬과 레거시 파생값을 원본 수치대로 소비한다. 추가 선수의 허용 챔피언/포지션은 기본 숙련도 14와 개별 override를 사용한다. 기존 280명의 개별값·기본값 형식을 그대로 보존한다. 임시 능력치는 공식 측정값이 아니며 화면에서 추정 상태를 표시한다. 생년월일·계약 날짜 null, 부분 경력, 빈 수상 기록, 출처 기준일·추정 근거는 보존한다. 빈 기록은 경력/수상이 없다는 뜻이 아니다. 자료의 나이·계약 잔여 일수는 조사 당시 값이며 게임 날짜 기준으로 자동 갱신하지 않는다. `detailsJson`의 조사 당시 `dataQuality.runtimeIntegrated=false` 같은 메타데이터는 원본 조사 상태를 보존한 값이며 현재 실행 연결 여부를 뜻하지 않는다. 현재 연결은 확장 directory 버전과 Career roster/binding으로 표현한다.

## 저장 및 실행 경계

V15에서 다음 책임을 분리했다.

| 저장 | 책임 |
| --- | --- |
| `career_player_directory` | Career 생성/이주 당시 전체 선수 정의·능력치·출처의 불변 snapshot |
| `career_roster_state` | Career/연도별 현재 소속, 1군/육성 배치, 구단 선발 5명, roster revision |
| `career_roster_command` | 원본 UUID·payload hash·적용 연도·결과 revision·receipt |
| `career_registered_player_pool` | 새 국제 등록 시 허용된 1군 후보 집합 |
| `career_league_fixture_roster` | R1/R2 개별 경기 시작 시 선수·능력치·숙련도 snapshot 및 roster revision |

국내/국제 대회 Series는 기존 `CompetitionRosterSnapshot`과 binding의 고정 roster를 사용한다. League binding/통합 receipt에는 `LEAGUE_SERIES_FROZEN_LINEUP_V1` 정책의 선택적 roster identity를 추가했다. 기존 시즌/스케줄/resource identity의 의미는 유지한다. 확장이 없는 구 binding/receipt의 canonical과 hash는 바뀌지 않는다. 고정 roster가 있으면 Player/Auto 팀 생성과 Draft, checkpoint, 완료 receipt가 같은 값을 사용하고 실제 경기 profile hash와 선수 ID도 확인한다. 매 경기 fresh Player/Team 상태를 조립하며 mutable 경기 상태를 전역 명부에 저장하지 않는다.

관리 구단의 같은 포지션 1군 후보를 선발로 선택하면 기존 선발은 후보가 된다. 예: KT ADC `player-fenrir` → `player-jiwoo`. 연계 육성 조직의 선수는 승격 후 후보가 되며 자동 선발되지 않는다. 선발을 육성팀으로 내릴 때에는 같은 포지션의 출전 가능한 1군 대체 선수를 함께 지정해야 한다. 다른 구단/무소속/소속 미확인 선수의 선발 및 임의 소속 이전은 거부한다.

변경 명령은 Calendar 행을 먼저 잠근 뒤 현재 연도·roster revision·소유 팀·선수·배치를 확인하고 5명 전체를 원자적으로 교체한다. 경기 binding/job 생성, 국제 등록, 시즌 전환도 같은 Career 잠금 순서를 사용한다. 명단 변경과 경기 시작이 겹치면 변경 전/후의 완전한 5명 중 하나만 고정된다. UUID는 Career·연도·팀·선수·목표·revision payload에 결속하고 동일 UUID의 다른 payload는 충돌한다. 재시도는 원래 receipt와 최신 읽기 상태를 반환한다.

## 변경 적용 시점

1. 현재 명부·선발 선택: 변경 저장이 완료되면 즉시 표시한다.
2. 시작하지 않은 국내/League Series: 시작 시 최신 선발 5명과 능력치·숙련도를 고정한다. 최초 season snapshot의 5명을 90경기 모두 강제하지 않는다.
3. 이미 시작된 Series: 기존 binding/job/checkpoint와 고정 선수로 끝난다. 세트 사이 교체는 없다.
4. 구 국제 등록: 확정된 기존 5명만 허용한다. 나중에 추가된 후보는 자동 편입하지 않는다.
5. 새 국제 등록: 등록 시점의 1군 명부를 후보 집합으로 저장하고 그 집합 내에서 Series 전 선발 변경을 허용한다. 등록 밖의 새 선발은 해당 포지션의 최초 등록 선발로 대체되며 다음 등록부터 적용된다. 후보 수/시리즈 교체 허용 범위는 이번 게임 정책이고 공식 제한의 확정을 주장하지 않는다.

화면은 구단의 현재 선발, 대회별 등록 선수, 진행 중 Series의 고정 선수와 등록 밖 변경의 적용 지연을 구분해 표시한다. 해외 및 무소속 선수도 전체 명부·상세에서 조회할 수 있다.

## 이주와 시즌 이월

새 Career 생성과 명시적 서버 startup recovery에서 확장 명부를 붙인다. GET은 재import하지 않는다. 이주 여부는 directory/state 행과 버전으로 추적하며 재실행 시 기존 선택을 초기화하지 않는다. 저장된 이전 roster snapshot이 있으면 그 선수 정의와 선발을 우선 보존한다. 이전에 만들어진 League binding/job에는 새 fixture roster를 소급 부여하지 않는다. 구 국제 등록에는 후보 집합을 소급 생성하지 않는다.

시즌 전환은 현재 roster 상태의 소속·squad·선발·revision을 다음 연도로 복사한다. 전역 최신 reference를 재import하지 않고 Career 소유 directory를 재사용한다. 과거 roster 상태와 기존 대회·경기·receipt는 그대로 보존한다. 기존에 비어 있던 시즌 roster snapshot은 마감 트랜잭션에서 기록하되 이미 확정된 snapshot은 덮어쓰지 않는다. 이후 Cup/League와 국제 등록은 새 연도의 선택을 소비한다.

## 화면 경합 수정

기존 문제는 전환 요청의 완료/종료 판정을 조회 generation에 함께 묶은 데 있었다. Calendar 재조회가 generation을 변경하면 전환 응답 적용과 finally의 pending 해제가 모두 무시될 수 있었다.

공유 `CareerMutationGate`는 이벤트 핸들러 진입 시 동기적으로 소유권을 잡는다. 전환·날짜 진행·경기 시작·로스터 변경 버튼과 핸들러가 이를 함께 확인하므로 다음 React 렌더 전에 연속 클릭해도 두 변경 요청이 시작되지 않는다. 조회 generation은 오래된 조회 응답 제거에 사용하고, 변경 요청은 별도 소유권으로 종료한다. 종료/언마운트 시 소유한 잠금을 해제하며 이전 종료 콜백이 새 요청의 잠금을 해제하지 않는다.

전환 및 roster 변경의 원본 연도/revision/UUID는 sessionStorage에 남긴다. 응답 소실·새로고침 뒤 새 UUID를 만드는 대신 원본 요청을 재전송한다. 명확한 invalid/stale 거부일 때만 원본 작업을 해제하고 최신 상태를 조회한다. Career 이동 시 이전 요청은 중단하고 이전 화면의 응답을 적용하지 않는다. 과거 시즌 조회에서는 변경 명령을 숨기거나 비활성화한다.

브라우저 검증에서 기존 Series Draft의 허브 복귀 버튼이 취소 확인으로 이어지는 동작을 확인했다. Series-owned Draft에는 보존 복귀를 명시적으로 전달하고, 실제 취소는 별도의 `현재 Draft 취소` 버튼으로 분리했다. 진행 중 응답이 있으면 복귀를 막고 원래 요청을 먼저 확인한다. 공통 Player Draft 소비 코드가 바뀌었으므로 기존 Player Draft/Series verifier도 실행했다.

## 검증 기록

전체 회귀는 총 두 번 실행했다. 1차는 23분 20초, 읽기 표시 범위 두 곳을 교정한 최종 2차는 23분에 통과했다. 두 실행 모두 264 suites / 2,008 tests / 실패·오류 0 / 기존 skip 2다. 기존 작업의 264 suites / 2,004 tests 결과를 이번 결과로 재사용하지 않았다.

### 집중·프런트 검증

backend 명령은 Temurin 21에서 `./gradlew test --tests '<선택자>' --console=plain --no-daemon`을 사용했다. 다음 범위를 나누어 실행하고 발견한 원인을 교정했다.

- `GlobalTeamRosterCatalogTest`: 460 ID, 280/180 관계, 56 경쟁 팀·79 조직, 스킬/합법 챔피언/소속 참조, 기존 resource 보존.
- `CareerModePersistenceTest.expandedRoster*`: 기존 저장 이주 재실행, KT 선발/승격/대체 선발을 지정한 강등, 부적격·다른 구단·stale/다른 payload 거부, 원본 UUID, Career 분리, 구 League canonical 보존, 새 R1/R2 binding의 Jiwoo, 경기 시작 대 명단 변경 경합, 파일 DB 재시작.
- `CareerModePersistenceTest.seasonRollover*`: 기존 빠른 합성 완료 준비 후 실제 2027→2028→2029 전환 명령, 실패 rollback/중복 요청/과거 기록과 최초 Career binding 보존. 마감 시 Jiwoo 선택을 다음 시즌 roster와 실제 새 Cup binding에서 확인.
- `CareerDomesticExecutionTest`: 기존 실제 국내/국제 Player/Auto/receipt/checkpoint 집중 검증을 유지하고, 구 국제 5명·새 후보 집합·등록 후 승격한 Hwichan의 기용 제한을 추가 검증.
- `LeagueAutomatedSeriesRunnerProductionV9Test.selectedReserve*`: Jiwoo를 선택한 실제 League Auto BO3를 실행하고 매 게임 최종 배치의 `player-jiwoo` 포함/`player-fenrir` 제외 및 고정 roster receipt 검증을 확인. 같은 기존 입력의 결정성/계측 동등성은 기존 유지 테스트의 책임이다.
- `LeagueAutomatedSeriesRunnerTest`, `LeaguePlayerSeriesHandoffServiceTest`, `LeagueRelationalPersistenceAndJobTest`: 직접 변경한 runner/binding/receipt/job/이주의 기존 집중 범위.

개발 중 국제 등록의 날짜 복원에 JavaTime 모듈이 빠진 오류를 수정했다. 기존 280명의 sparse 숙련도 형식을 신규 full-role 자료와 동일 개수로 비교하던 기대값, 선발 변경 후 모든 시즌 hash가 같다고 가정하던 기대값, MID Hwichan 승격의 대체 대상을 ADC로 가정하던 테스트 기대값을 각각 실제 정책과 포지션 기준으로 교정했다. 최종 해당 집중 검증은 통과했다. 전체 회귀는 이 과정에서 실행하지 않았다.

프런트는 `npm run career:verify` **44개**, `npm run series:verify` **84개**, `npm run player-draft:verify` **33개**, `npm run league:verify` **37개**, `npm run build`를 통과했다. Career verifier에 8개 사례를 추가했다. 공유 gate 2개, 명부 계약/원본 명령 5개, 보고된 generation 경합의 실제 production 컴포넌트 재현 1개다. 컴포넌트 검증은 전환 중 조회 revision을 바꾸고 완료 응답을 해제한 뒤 `onChanged=1`, `pending=false`, 원본 작업 정리 및 요청 잠금 해제를 확인한다. 사용자 `/tmp` 재현 파일에 제품이나 영구 테스트가 의존하지 않는다.

### 실제 브라우저

1280×720 Chromium과 별도 H2 DB를 사용했다. `/tmp/career-expanded-v1/BrowserFixture.java`는 기존 합성 helper로 2027 마감 조건만 준비했다. 이 준비는 실제 경기 완주가 아니다. 브라우저에서는 실제 API와 화면을 사용했다.

1. KT 명부에서 460명 조회와 기존 FenRir 선발, Jiwoo 상세의 추정·자료 기준일 표시 확인.
2. Jiwoo 선발 저장 후 새로고침. 현재 선발은 PerfecT/Cuzz/Bdd/Jiwoo/Effort이며 roster revision 1.
3. 2027 원본 전환 요청을 서버에서 적용한 뒤 응답 소실. 새로고침 후 동일 연도/revision/UUID 복원. 재시도 응답을 4초 지연하는 동안 Calendar 두 동작, FenRir 재선발, 리그 진입 비활성화를 확인. 소실 뒤 pending 해제, 원본 요청으로 최종 성공 복구, 추가 시즌 생성 없음.
4. 2028 Round 1 DK–KT Player Series의 실제 Draft 응답은 `player-siwoo`, `player-lucid`, `player-showmaker`, `player-smash`, `player-career`, `player-perfect`, `player-cuzz`, `player-bdd`, **`player-jiwoo`**, `player-effort`를 포함한다. `player-fenrir`는 없다. 실제 Player 경기 결과를 합성하거나 완주했다고 주장하지 않는다.
5. 임시 서버 종료/재시작 후 같은 Series/Draft를 복구하고 Jiwoo 표시 유지. 취소 없이 허브→League→Career 복귀 후 현재 선발과 진행 중 Series의 고정 선수에 모두 Jiwoo가 표시됨을 확인.

브라우저 준비 스크립트의 시즌 마감 날짜 가정, 지연 훅의 타이머 호출, pending 중 달라지는 버튼 문구는 검증 도구의 오류로 분리해 수정했다. 같은 UUID의 재시도로 검증했으며 새 전환을 만들지 않았다. 화면에서 발견한 두 sibling의 중복 React key와 Series Draft 복귀 동작은 제품 코드에서 수정한 후 최신 Vite 서버로 다시 확인했다. 의도적 응답 중단 및 임시 서버 재시작의 네트워크 오류와 제품 오류를 구분했다.

임시 브라우저·Spring·Vite 프로세스는 종료했다. 전체 회귀는 기존 2-worker 설정과 diagnostic 제외 범위를 유지해 `./gradlew test --console=plain --no-daemon`으로 실행했다. `clean`, 임의 제외/skip 또는 통계 진단은 수행하지 않았다.

### 최종 전체 회귀

1차: `./gradlew test --console=plain --no-daemon` — **264 suites / 2,008 tests / 실패 0 / 오류 0 / 기존 skip 2**, **23분 20초**, 통과.

통과 후 최종 정리에서 관리 팀이 참가하지 않은 구형 Auto binding에 관리 팀 선수 5명을 표시하는 fallback과, 관리 팀이 없는 신규 국제 후보 집합을 빈 등록 명단으로 노출하는 경계를 발견했다. 실제 경기 입력은 바뀌지 않았지만 roster API의 표시 의미가 잘못될 수 있어 두 경계를 함께 교정했다. 기존 `expandedRosterMigrationCommandsFreezeAndCareerIsolation`에 비관리 구단의 구형 binding 및 국제 등록 후보 집합을 추가해 해당 표시는 비어 있어야 함을 검증했다. 이 집중 명령은 **1분 41초**에 통과했다.

이 교정은 executable production Java/API 읽기 동작을 바꾸므로 AGENTS.md의 post-pass 규칙에 따라 집중 검증 후 두 번째 전체 회귀를 실행했다. 기존 2-worker 설정을 유지하며 임의 제외/skip 또는 `clean`은 추가하지 않았다.

최종 2차: 같은 전체 명령 — **264 suites / 2,008 tests / 실패 0 / 오류 0 / 기존 skip 2**, **23분**, `BUILD SUCCESSFUL`, 종료 코드 0. 종료 후 현재 JUnit XML 전체를 집계했다. 기존 skip은 `PlayerDraftLatencyProfilingV1DiagnosticTest.captureOfficialPlayerDraftInteractiveAndSimulationLatencyProfile()`과 `PlayerDraftPerformanceHardeningV1DiagnosticTest.capturePairedBackendProbe()`이며 이번 작업에서 새 skip을 추가하지 않았다. 전체 회귀 이후에는 문서 결과만 갱신했다.

## 주요 파일과 후속 범위

- `ExpandedPlayerCatalog`: 280+180의 ID·구조 검증, 정규화 resource 연결.
- `CareerRosterStore`: Career directory, 소속/선발 변경, 등록 후보 집합, League fixture roster, 명령 receipt와 이주.
- `V15__career_player_directory_and_lineups.sql`: 다섯 저장 책임의 additive migration.
- `CareerSeasonApplicationService`, `CareerInternationalCompetition`, League/Career binding·runner·receipt: 명단의 실제 실행/다음 시즌 소비.
- `CareerRosterApiV1Controller`, `careerRoster.contract.ts`, `CareerRosterPanel`: strict 요청/응답·명부 상세·선발/배치 변경.
- `career.mutation.ts`, `CareerDashboardPage`, `CareerSeasonsPanel`: 공유 변경 잠금과 원본 요청 복구.

이후 능력치를 수정할 때에는 추가 선수 resource의 `playerId` 아래 `ratings` 및 `championProficiencies`를 수정한다. 기존 Career snapshot에는 소급 적용되지 않는다. 인게임 편집기, FA 영입/방출·계약 처리, 성장·훈련·노화, CL/해외 리그 시뮬레이션, 세트 중 교체, 오프롤, KeSPA·아시안게임 실행, 사진 및 전면 UI 개편은 후속 범위다.
