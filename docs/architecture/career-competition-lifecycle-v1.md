# Career Competition Lifecycle V1

## 현재 상태

이 버전은 `CAREER_COMPETITION_LIFECYCLE_V1_PARTIALLY_IMPLEMENTED_RULE_SOURCE_GAP`이다.
R1~2 결과 봉인, Road to MSI의 M1~M5, Legend/Rise 분할과 40경기, LCK Play-in의
M1~M3는 structured rule과 영속 graph로 구현했다. LCK Cup과 LCK Playoffs는 확인 가능한
입력·날짜까지만 보존하며 부족한 edge를 추측하지 않는다.

## Authority

Career Calendar는 날짜와 current/next event를 소유한다. 별도 Career Competition aggregate는
Career/year/competition/match identity, frozen 참가 seed, routing selector, Series format,
qualification output, lifecycle revision/state hash와 completion application ledger를 소유한다.

기존 authority는 그대로다.

- League Season: R1~2의 18 rounds/90 fixtures, standings, job/outbox/receipt
- Series: score, Game별 Draft와 한 Series 안의 Hard Fearless history
- Match: Production V9 실행과 seeded gameplay Random
- Team/Player resource: PlayerId, roster와 능력치

Competition은 winner나 score를 계산하지 않는다. 영속 transition 메서드는 server-side Series
identity와 compact receipt hash에 결속된 decisive completion만 적용하고, 같은 receipt replay는
mutation 0이다. 현재 HTTP request에는 winner, team, seed, routing edge를 받는 API가 없다.

## Executable rule resource

runtime은 로컬 `일정/lck일정`을 읽지 않는다. classpath resource
`competition/lck-career-competition-rules-2026-v1.json`만 읽으며 byte SHA-256
`64acfab316162ca7f17c898c434b7ecce496f085370ff45012a83332d445b770`을 검증한다.
resource는 raw source 네 파일의 SHA, source IDs, rule completeness, participant selector,
winner/loser dependency, output slot, Bo3/Bo5, Hard Fearless와 month-day를 구조화한다. 한국어
설명 문자열과 배열 index는 production routing 입력이 아니다.

2026 official fact와 미래 연도 날짜는 분리한다. Road/Play-in의 source month-day는 기존
`SAME_LOCAL_MONTH_DAY_FROM_2026_REFERENCE_V1`로 투영한다. R3~4의 exact fixture date는 source에
없으므로 별도 `LCK_R3_R4_TEN_MATCHDAYS_LINEAR_INCLUSIVE_WINDOW_V1` game policy가 07-29~08-23
안에 10 matchday를 결정적으로 만든다. 기존 R1~2 allocation policy와 hash는 변경하지 않는다.

## Materialization과 routing

정상 경로에서 R1~2의 90 fixture가 모두 완료되고 Season이 `COMPLETED`일 때만 final ranking을
schedule identity, standings revision, series/game record와 함께 한 번 봉인한다. 같은 입력의
재실행은 같은 import hash이며 mutation 0이고, 다른 입력은 충돌이다.

봉인 후 다음 graph가 생성된다.

- Road to MSI: M1 `5 vs 6`, M2 `4 vs M1 winner`, M3 `1 vs 2`, M4
  `3 vs M2 winner`, M5 `M3 loser vs M4 winner`; M3/M5 winner가 MSI LCK 1/2 seed다.
- R3~4: R1~2 1~5위 Legend, 6~10위 Rise. 그룹별 5팀 double round robin 20경기,
  합계 40 Bo3다. R1~2 record는 seed row에 원본 counter와 import hash로 보존한다.
- Play-in: M1 `seed 1 vs 2`, M2 `seed 3 vs 4`, M3 `M1 loser vs M2 winner`.
  output은 playoff seed 5/6과 season place 7/8이다.

각 새 fixture/Series ID와 root seed는 Career root seed와 year/competition/match structured identity로
결정한다. worker 순서, wall clock, display name과 무관하다. 관리 팀이 participant면
`PLAYER_CONTROLLED`, 아니면 `FULL_AUTO`; 모든 국내 fixture는 Series마다 Hard Fearless history를
새로 시작해야 한다.

## Persistence와 recovery

Flyway V7은 cycle, instance, seed, fixture, output, completion application과 transition lock을
추가한다. R1~2 import, fixture participant resolution, qualification output과 receipt application은
transaction/CAS로 갱신된다. receipt hash와 match에 unique identity가 있어 duplicate delivery는
추가 transition을 만들지 않는다. cross-Career/year/competition/Series replay는 거부한다.

Calendar view의 additive `competition` 필드는 rule provenance, current/next competition와 stage,
진행 수, 다음 fixture, managed 여부, output slot, 외부대회 제한을 bounded하게 반환한다.
Competition fixture date도 `ADVANCE_TO_NEXT_EVENT` 후보라서 Road M2~M5를 건너뛰지 않는다.

## Fail-closed 범위

- 첫 cycle Cup: prior champion/runner-up이 없어
  `INITIAL_CYCLE_PRIOR_SEASON_RESULT_REQUIRED`다.
- Cup play-in: 1 seed의 opponent choice product policy가 없다.
- Cup playoff: 10개 full winner/loser routing edge가 source에 없다.
- LCK Playoffs: 10개 날짜는 보존하지만 seed별 10경기 routing graph가 없어
  `LCK_PLAYOFF_BRACKET_RULE_SOURCE_INCOMPLETE`다.
- First Stand/MSI/EWC/Worlds: LCK output slot만 저장할 수 있고 외부 roster authority가 없어
  `EXTERNAL_COMPETITION_EXECUTION_NOT_IMPLEMENTED`다.
- Asian Games: `NATIONAL_TEAM_RELEASE_WINDOW_ONLY`이며 구단 fixture를 만들지 않는다.

현재 safe graph에는 competition용 Production V9 Auto worker와 Player Series binding endpoint가
아직 연결되지 않았다. Calendar는 실행 가능한 fixture 날짜에서
`AUTO_COMPETITION_FIXTURE_REQUIRED` 또는 `MANAGED_COMPETITION_FIXTURE_REQUIRED`로 멈추며 가짜
결과를 만들지 않는다. 이 실행 adapter와 R3~4 final standings→Play-in materialization은 source
decision closure와 함께 다음 작업에서 닫아야 한다.
