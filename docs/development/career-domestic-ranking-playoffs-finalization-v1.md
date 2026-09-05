# Career 국내 순위·시즌 말 PO·최종 순위 V1

2026-09-05. 시작/현재 HEAD `87df2df344d41029567dd164ad886c5398477280`.
첫 playable cycle은 **2027**, 규칙 reference는 **2026**이다. 현실 경기 결과를 주입하지 않는다.
초기 untracked 개발 프롬프트는 수정하지 않았다. 작업 중 추가된 해외 로스터 관련 변경은 별도 작업의 변경으로 보존했다.
자동 commit/push/배포는 수행하지 않는다.

## 구현과 근거

공식 근거는 [LCK 2026 규정집 §2.7–2.8, §3.2](https://cmsassets.rgpub.io/sanity/files/dsfx7636/news_live/b5e4819daa2793425433910ca02b1c479b2f15a3.pdf?accountingTag=lol_esports)와
[기존 source closure](career-competition-rule-source-closure-v1.md)를 따른다. 국제대회 전체 조사는 반복하지 않았다.
새 리소스는 `lck-career-competition-rules-2026-v3`, game policy는 `CAREER_COMPETITION_GAME_POLICY_V3`다.
리소스 SHA-256은 `3cc24980f76293d06e202a299d544a58e16c72965b82a5054e6e61fef952ba2a`.
V2 리소스의 바이트와 해시 검사는 보존했다.

- Cup: 매치 승/득실 → 해당 범위의 H2H 제외 → 공식 SoV → 동률 상대전 승리 세트 평균 → 전체 상대전 평균.
  SoV는 5.0부터 0.5까지의 배수를 **2배 정수**로 계산한다. 공동 순위 다음은 점유 팀 수만큼 건너뛴다.
  Cup 그룹 비교는 상대가 속한 그룹의 1–5위 표, 통합 Play-in 비교는 10팀 통합 표를 사용한다.
  이는 교차 그룹만 경기하는 Cup에서 상대의 그룹 내 위치를 배수로 쓰는 해석이며, 통합 시드 범위와 분리했다.
  진출 자격은 승자 그룹 3–5/패자 그룹 2–4, 6팀 시드는 통합 성적순이다.
- 승리 시간: verified receipt의 **세트 승자와 duration**만 사용하며 패한 Series의 세트승도 포함한다.
  분자/분모를 보존하고 정수 교차 곱으로 비교한다. 표본 없는 팀이 있으면 그 동률 묶음의 해당 평균 비교를
  `NO_COMPARABLE_SAMPLE`로 건너뛰는 게임 정책이다. 0초 승리로 처리하지 않는다.
  기존 정수 표시 열은 호환용 절삭 projection이며, exact SoV/세트시간 합/표본 수와 판정 원장이 별도로 결속된다.
- 정규시즌: League의 **V2 완료 receipt 봉투 안 V1 세트 원장**과 standings application/fixture 관계를 읽는다.
  Career/활성 시즌/League/schedule/fixture/Series/receipt canonical/hash를 확인하고 동일 경기를 중복 집계하지 않는다.
  R1/R2 90경기, R3/R4 추가 40경기의 원장을 사용한다. `gameWins` 대체 정렬을 제거하고 2/3팀 H2H 및 다자간 규칙을 적용한다.
  R3/R4 SoV 배수는 Legend1–5/Rise6–10이며, H2H까지 해소된 선행 위치를 반영한다.
- `CareerDomesticRanking`은 순수 판정, `CareerDomesticTiebreak`는 2–10팀 재개 가능한 대진,
  `CareerDomesticEvidence`는 기존 원장 읽기, `CareerDomesticCompetition`은 기존 Store transaction 안의 전이를 담당한다.
  별도 League 실행자나 결과 원장을 만들지 않았다.

## 추가 경기와 게임 정책

`career_domestic_ranking_decision`이 scope, 원본 input hash, 적용 기준, 동률 팀, exact 지표,
대기 match ID/참가 팀, 확정 순서와 정책을 저장한다. JSON map key와 SQL 목록 순서는 고정이다.
판정이 SEALED된 뒤 다른 내용으로 갱신할 수 없다.

2팀 1경기, 3팀 stepladder, 4팀 승자/패자 순위전, 5–10팀 예선/bye/하위 H2H 재평가를 지원한다.
같은 판정 단계의 두 그룹까지 합산한 필요 경기 수에 따라 1경기 BO5, 2경기 BO3, 3경기 이상 BO1이다.
BO1은 Competition에서 사용하며 standalone create는 기존 BO3/BO5 범위를 유지한다.

진출·상금에 영향을 주지 않는 하위 순위전은 생략한다. R1/R2의 상위6과 Rise의 상위3을 가르는 경기는 실행한다.
완전히 영향이 없는 묶음(R1/R2 하위4, Rise 하위2)은 스포츠 승패를 만들지 않고 기존 시드 기준과 seeded draw로
저장용 고유 위치만 배정한다. 원래 동률 묶음은 판정 evidence에 남는다.
`SHARED_NON_QUALIFYING_PLACES_USE_SEEDED_ORDER_WITHOUT_MATCH_RESULT_V1`는 **게임 저장 정책**이며,
생략한 경기의 승자를 공식 결과로 주장하지 않는다. 따라서 요구된 최종 1–10 위치는 유일하되,
이 경우 9/10 구분의 근거는 경기 승리가 아니라 해당 배정 정책이다.

추첨은 Career/year/competition/scope/root seed와 입력 해시의 SHA-256으로 고정한다.
`LCK_DOMESTIC_RANKING_AND_SEEDED_TIE_DRAW_V1`은 경기 Random을 소비하지 않는다.
추가 날짜는 Cup2/1, R1/R2 5/31, R3/R4 8/23의 마지막 예정일과 현재 Calendar 날짜 중 늦은 날이다.
`LCK_TIE_SAME_DUE_DAY_ORDERED_BEFORE_DEPENDENTS_V1`: 같은 날짜의 안정된 match order로 실행하며,
미결 동률 fixture를 후속 대진보다 먼저 노출한다. due/overdue와 기존 실행 command를 재사용한다.

Cup **그룹** 동률은 부모 점수판 아래 BO1 자식 경기를 한 세트씩 만든다.
홀수 세트는 각 그룹5위, 짝수는1위이며 각 자식의 두 팀·로스터·Series binding은 고정이다.
부모가 전 세트 pick을 다음 자식의 initial exclusions로 넘긴다. 각 자식의 game count는 다시1부터 시작하고
Hard Fearless exclusion 집합은 부모 전체에 걸쳐 이어진다. 부모 3승이면 종료한다.

## 시즌 말 LCK_PLAYOFFS

Legend1–4와 Play-in 첫/최종 경기 승자가 s1–6을 만든다. `PO_U1A/U1B/L1/U2A/U2B/L2/U3/L3/L4/F`
10경기는 모두 BO5/Hard Fearless이며 reset 결승은 없다.
s3가 s5/6, s1이 U1 승자 중 선택할 때 `LCK_PLAYOFFS_LOWEST_AVAILABLE_SEED_OPPONENT_SELECTION_V1`로
가장 낮은 eligible seed를 자동 선택한다. 공식 선택권자·eligible 집합과 선택 receipt를 유지한다.
L2는 U2 패자 중 낮은 시드, L3는 높은 시드를 소비한다. Cup 선택 key와 PO 선택 key는 대회 범위로 분리된다.

첫 세트 RoFS: U1=s3/s4, U2=s1/s2, L1/U3=seeded coin, L2/L3=U2패자,
L4=U3패자, F=U3승자(픽·진영 모두). 이후 세트는 직전 패자가 소유한다.
자동 결정은 `LCK_ROFS_FIRST_PICK_OTHER_TEAM_RED_LOSER_ROFS_V1`:
권리자가 FIRST_PICK을 선택하고 상대가 RED를 선택하여 첫 픽 팀이 BLUE가 된다.
결승은 `LCK_FINAL_UPPER_WINNER_BLUE_FIRST_PICK_LOSER_ROFS_V1`: 상위조 승자가 BLUE와 FIRST_PICK을 모두 선택한다.
**두 선택의 소유권은 다르며**, API `competitionContext`에서 첫 픽 팀·진영 선택 팀·선택 진영·후속 패자 권리를 분리한다.
기존 blue-first Draft를 재사용하도록 고정한 게임 정책이고 자유로운 사람 선택 UI는 추가하지 않았다.

관리 경기 Player Series, 나머지 durable Auto job, Production V9와 기존 검증 receipt/heartbeat/fence를 사용한다.
R3/R4 및 Road materialization에서 누락됐던 실행 side policy도 연결했다.

## 최종 순위와 기존 저장

V11은 새 판정 표와 nullable metadata/exact metric 열만 추가한다. 기존 migration은 변경하지 않는다.
최종 결과와 같은 transaction에서 기존 `career_lck_final_ranking_snapshot/row`에 다음을 봉인한다.

- PO 결과1–6, Play-in 탈락7–8, Rise 하위9–10. 다른 10팀이 각 위치를 정확히 한 번 차지해야 한다.
- source Career/연도/시즌, champion/runner-up, rule/policy, 결과 receipt/qualification 근거 hash 및 authority hash.
- 기존 승패/세트 열은 **R1/R2+R3/R4 정규시즌만** 집계한다. Cup/추가 경기/PO 성적을 섞지 않는다.

마지막 결과 replay는 기존 receipt/application을 확인하고 같은 봉인을 읽으며 추가 mutation을 하지 않는다.
봉인 metadata와 판정 원장 연결도 검증한다. 기존 `initializeFuture(career, nextYear)` 소비자가 같은 Career의
직전 SEALED 순위를 검증하여 Cup 초기 seed를 읽는다. 실제 rollover나 연도 증가는 구현하지 않았다.

아직 실행·binding·R1/R2 import·Cup standings·최종 순위가 없는 V2 cycle만 기존 hash 검증 후 V3로 전환한다.
사용한 V2 cycle은 기존 규칙/결과/binding/hash를 보존하고 `PRESERVED_PREVIOUS_RULES`로 표시한다.
기존 pending binding/command의 재개·완료 replay는 유지하며, 새 fixture 실행은
`DOMESTIC_RULE_VERSION_REQUIRES_NEW_CYCLE`로 차단한다. 자동 삭제나 재정렬은 없다.

국내 최종 순위와 source/evidence를 Worlds 입력으로 제공하되 지역 슬롯과 MSI 우승 특례는
`PENDING_IN_GAME_INTERNATIONAL_EVIDENCE`다. 요구 evidence 종류를 구조화했고 임의 슬롯 수·현실 우승팀을 넣지 않았다.

## 검증

AGENTS와 `lolmanager-verification` 적용. 새 backend 테스트 클래스는 순위 규칙과 실제 엔진 연결의 **2개**이며,
전이·재시작·기존 저장 검증은 기존 `CareerModePersistenceTest`를 확장했다. 기존 synthetic Draft/receipt helper를 재사용했다.
전체 Cup/90·130 정규시즌/PO를 실제 엔진으로 반복 실행하지 않았다.

- 집중 7 suites/51 tests: 순위·2–10팀 동률·리소스·영속 전이·기존 저장·공통 Series 계약, 실패0.
  실제 Auto BO3와 그 pick을 상속하는 Player BO1, 완료 verifier와 durable checkpoint 포함. Gradle5분10초.
- 추가 최종 전이 집중 검증: 2 suites/18 tests, 실패0, Gradle1분40초. League envelope 소유권과
  SEALED 판정 변경 거부, 낮은/높은 PO seed routing 및 결승 선택권 반영 후 통과.
- Career verifier: 25 PASS, 기존 사례와 새 BO1 판정/봉인10팀/중복팀거부/새버전필드 필수 사례 통과.
  Series verifier: 기존 사례와 inherited exclusions/BO1 계약 및 근거 없는 상속 거부 통과.
- `npm run build`: TypeScript 및 Vite153 modules 통과.
- Playwright Chromium: 기존 Calendar 컴포넌트와 verifier fixture로 동률전 관리 행동 → 시즌 말 PO Auto 행동 →
  최종 순위10행/우승·준우승/Worlds 대기 문구 확인. live 연간 경기 완주 검증이 아니며, 제외된 생성 UI 결함은 재검증하지 않았다.
  임시 entry/fixture generator, 서버와 브라우저를 정리했다.
- 최종 전체 backend Gradle `test`: **1회, 263 suites/2,011 tests, failures0/errors0/skipped2**,
  Gradle36분51초, exit0. skipped2는 기존 latency/profiling diagnostic 두 항목이다.
  이번 전체 실행 중 production/resource/test/Gradle 입력 변경은 없었다. 이전 안정화 결과를 재사용하지 않았다.
- `git diff --check` 통과. 실제 HEAD는 시작과 동일하며 자동 commit/push/배포는 하지 않았다.

집중 실행 대상(`com.lolfm.` 생략):

| 클래스 | 테스트 |
| --- | ---: |
| `career.CareerDomesticRankingTest` | 16 |
| `career.CareerCompetitionRulesTest` | 7 |
| `league.CareerModePersistenceTest` | 21 |
| `application.CareerDomesticExecutionTest` | 1 |
| `application.SeriesLifecycleServiceTest` | 1 |
| `application.LeagueBoundSeriesCheckpointRecoveryTest` | 1 |
| `controller.SeriesApiV1ControllerTest` | 4 |

Java21에서 `./gradlew test --tests <위 클래스> --console=plain --no-daemon`으로 실행했다.
전체 회귀 명령은 `./gradlew test --console=plain --no-daemon`이다.
frontend 명령은 `npm run career:verify`, `npm run series:verify`, `npm run build`다.

초기 집중 실패는 이전 PO source-incomplete 기대값, 합계 standings만 준비한 구형 테스트 fixture,
R3/R4 실행 side policy 누락을 분리해 수정했다. 실제 세트 원장 검증을 약화하지 않았다.

## 남는 범위

국제대회/아시안게임/KeSPA Calendar gate는 유지한다. 이번 결과는 국내 결과 경로의 완성이지
Calendar에서 연간 시즌을 중단 없이 완주할 수 있다는 뜻이 아니다. 국제 경기 증거 생산과 Worlds 실행,
rollover, 경제·선수 운영, 자유로운 상대·진영/픽 선택 UI는 범위 밖이다.
현재 Match receipt에는 기권·몰수 결과 생성 모델이 없으며 해당 승리 시간을 추정하지 않는다.
