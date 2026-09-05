# Career Competition Lifecycle V1

## 현재 상태

현재 상태는
`CAREER_COMPETITION_SERIES_EXECUTION_AND_RESULT_TRANSITION_V1_ACCEPTED`이다.
source-complete 국내 대회 graph는 기존 BO3/BO5, Player/Auto Draft, Hard Fearless와 Production V9에
연결됐고 verified Series 결과만 다음 대진과 qualification output을 바꾼다. KeSPA Cup과
source-incomplete LCK Playoffs, 실제 국제대회 실행은 계속 명시적으로 차단한다.

## Authority와 mutation 경계

- Career Calendar: 현재 날짜, event cursor, advance command/receipt, Calendar revision/hash
- Competition cycle: 시즌 ordinal, 초기화 정책/input hash, ordered child instance hash
- Competition instance: seed, fixture graph, selector provenance, 결과/receipt/output, lifecycle
- League Season: 기존 R1~2 18 rounds/90 fixtures, standings, job/outbox/receipt
- Series/Match: score, Draft/Hard Fearless history, Production V9와 seeded gameplay Random
- Competition execution: immutable binding, Player checkpoint 또는 Auto job/lease/fence, command recovery
- Completion application: verified compact receipt, result detail/application ledger, graph transition/outbox

`GET /calendar`와 Career 조회는 read-only다. cycle 생성, V1→V2 migration, R1~2 결과 봉인과
competition transition은 새 Career 생성 transaction, startup recovery 또는 명시적 advance/command
transaction만 수행한다. 조회 반복은 revision/hash/updatedAt/fixture/job/Series를 바꾸지 않는다.

## Canonical hash V2

instance는 `CAREER_COMPETITION_INSTANCE_SHA256_CANONICAL_V2`, cycle은
`CAREER_COMPETITION_CYCLE_SHA256_CANONICAL_V2`를 사용한다. canonical bytes는 UTF-8이고 각 값은
`fieldName=length:value\n`으로 기록한다. 목록은 명시된 SQL order로 정렬하며 trailing newline을
포함한다. 표시 이름과 설명 문자열은 identity에 들어가지 않는다.

Instance hash는 Career/year/competition, rule version/resource hash/game policy, lifecycle/blocker,
revision, materialization policy/receipt, source input, ordered seeds, fixture ID/stage/order/date/schedule
status, original selectors, resolved stable team, format/Hard Fearless/side policy, Series identity,
fixture result/receipt/revision, qualification output과 application ledger를 결속한다. Cycle hash는
season ordinal과 initialization identity, transition state, R1~2 import identity, ordered
`competitionId|instanceStateHash`를 결속한다. 매 load와 transition 전에 DB graph로 다시 계산하여
stored hash와 exact 비교한다.

V1 저장은 startup recovery가 V1 canonical hash와 11개 instance를 먼저 검증한 경우에만 한
transaction에서 V2로 승격한다. 증명할 수 없는 V1은 덮어쓰지 않고
`COMPETITION_STATE_MIGRATION_REQUIRED` 또는 legacy integrity error로 차단한다. V8은 새 field만
additive하게 추가한다. V9은 binding, command, Auto job, result detail/application, Cup standings/seed,
opponent-choice와 outbox table을 추가한다. 기존 migration은 수정하지 않는다.

## Selector와 stable identity

`participantSelectorType/value`는 선발 근거이고 `firstTeamCode/secondTeamCode`는 현재 resolve된
stable identity다. R1~2 순위로 선발된 R3~4 팀도 selector를 `R1_R2_RANK`로 보존한다. Cup은
`CUP_GROUP_SEED`, `CUP_PLAY_IN_SEED`, `CUP_PLAYOFF_SEED`, predecessor winner/loser 및 choice 전용
selector를 closed vocabulary로 검증한다. display name, 배열 index 또는 message parsing은 없다.

## LCK Cup source와 정책

다음 분류를 섞지 않는다.

- `OFFICIAL_SOURCE_FACT`: 10팀/2그룹, cross-group 25 Series, 일반 Bo3·승점 1, Super Week 동일
  seed 5경기 Bo5·승점 2, Hard Fearless, 그룹/개인 tie-break, Play-in/Playoff routing과 공식
  choice right, 상위 2팀 First Stand 진출
- `OFFICIAL_2026_INITIAL_BOOTSTRAP`: 첫 playable Career 시즌 ordinal 1의 현실 2026 그룹/seed
- `GAME_PRODUCT_POLICY`: 미래 그룹 draft에서 champion이 첫 선택, 그리고 사람 선택 UI가 없는 동안
  공식 eligible 상대 중 가장 낮은 seed를 고르는 정책
- `GAME_DERIVED_SCHEDULE_POLICY`: 공식 exact fixture date를 재현할 수 없는 stage의 결정적 slot 배정

첫 playable Career 시즌은 현실 2026 시드/그룹 bootstrap만 사용한다. Baron은
`GEN(1), T1(2), NS(3), DNS(4), BRO(5)`, Elder는
`HLE(1), DK(2), KT(3), BFX(4), KRX(5)`다. 공식 source의 `DRX`는 명시적 alias로 현재 stable
`KRX`에 매핑한다. 현실 2026 경기 결과, 승패, 승점과 우승팀은 가져오지 않으며 40경기는 Career
안에서 모두 새 상태로 시작한다.

두 번째 시즌부터는 직전 게임 내 LCK 최종 순위가 유일한 seeding source다. 같은 Career, 바로
직전 year, `SEALED`, exact 1~10 total order와 source state hash가 필요하다. champion/runner-up이
captain이 되고 공식 snake turn을 적용한다. 예를 들어 직전 순위가 `T01..T10`이면 Baron은
`T01,T03,T06,T07,T10`, Elder는 `T02,T04,T05,T08,T09`가 된다. 이 input은 2026 bootstrap과
서로 다른 policy/input/materialization receipt hash를 가진다.

Choice right 자체는 공식 사실이다. V1의 선택 결과는
`LCK_CUP_LOWEST_AVAILABLE_SEED_OPPONENT_SELECTION_V1`이 결정한다. eligible set을 seed 내림차순,
stable team code로 canonicalize하고 가장 낮은 seed를 고르며 owner 자신, 중복, 범위 밖 입력을
거부한다. canonical eligibility, chosen team, policy hash는 deterministic receipt에 결속된다.

Cup resource는 25 group match, Play-in 5 match, Playoff 10 match의 40개 ordered graph를 가진다.
미확정 predecessor는 임의 팀으로 채우지 않는다. 일반 20개 group slot과 중간 bracket slot은
`GAME_DERIVED_SCHEDULE_POLICY`, 공식 schedule/finals로 확인된 Super Week·결승 weekend는
`OFFICIAL_PROJECTED_DATE`다. 두 값 모두 source year 2026의 month/day를 미래 Calendar에
`SAME_LOCAL_MONTH_DAY_FROM_2026_REFERENCE_V1`로 투영하며 “공식 미래 일정”이라고 부르지 않는다.

## Series 실행과 완료 권위

Fixture start 요청은 schema, expected Competition revision, logical UUID만 받는다. 팀, 상대, side,
format, seed, score, winner, receipt와 qualification output은 서버가 persisted graph와 production
snapshot에서 결정한다. Canonical binding은 Career/year/competition, rule/resource/game-policy,
instance hash/revision, fixture/match/stage/order, original selector와 resolved team, BO3/BO5,
Hard Fearless, execution mode/side policy/root seed, bound Series와 production resource identity를
explicit order UTF-8로 결속한다.

관리 팀 fixture는 `COMPETITION_BOUND` Series checkpoint를 만들고 기존 Player Draft/Series UI와
Production V9을 그대로 사용한다. 비관리 fixture는 fixture 하나를 durable job/lease/fence 단위로
삼고, 내부 game은 같은 frozen binding과 seed로 순차 실행한다. 두 경로 모두 current production
resource가 binding과 다르면 실행하거나 자동 치유하지 않는다.

Completion verifier는 ordered game compact receipt에서 side/team/game seed, Hard Fearless history,
Draft 결정/final assignment, policy/profile/configuration/engine/resource, output/replay/timeline/Random
fingerprint, decisive score와 winner를 다시 확인한다. 이 verifier만 private-constructor opaque verified
value를 만들 수 있고 store의 raw apply 경계는 외부 application flow에 공개하지 않는다. 적용
transaction은 fixture/result/application ledger, dependent selector, instance/cycle revision/hash,
standings/seed/output과 outbox를 함께 갱신한다. 같은 receipt replay는 mutation 0이며 cross-fixture,
cross-Career, cross-season receipt 재사용은 거부한다.

## LCK Cup과 다른 국내 대회 전이

LCK Cup Group Battle 25경기가 모두 verified completion인 경우에만 그룹 포인트와 개인 순위를
봉인한다. 일반 BO3 승리는 1점, Super Week BO5 승리는 2점이다. ordered standings와 tie-break
trace/hash를 저장하며 필요한 공식 추가 tiebreak evidence가 없으면 stable team code로 결과를
창작하지 않고 `LCK_CUP_TIEBREAKER_REQUIRED`에서 멈춘다. 봉인 결과로 Play-in 6 seed와 Playoff
직행 seed를 만들고, 5개 Play-in과 10개 Playoff selector를 predecessor/seed/choice dependency 순서로
resolve한다. 세 opponent choice는 eligible seed order, owner, chosen opponent, policy ID/hash receipt를
남긴다. Final 결과는 First Stand LCK seed 1/2만 만들며 국제 상대나 결과는 만들지 않는다.

동일 adapter는 source-complete인 `LCK_ROAD_TO_MSI` 5경기, sealed R1~2 ranking을 carry하는
`LCK_REGULAR_R3_R4` 40경기, `LCK_PLAY_IN` 3경기에 사용한다. 기존 `LCK_REGULAR_R1_R2` 90경기는
계속 League Season authority가 소유한다.

## KeSPA Cup source gap

KeSPA 분류는 다음과 같다.

- `REFERENCE_TEMPLATE_ONLY`: 2025 공식 공시와 규정집 V1.1
- `SOURCE_INCOMPLETE`: 2026 자료는 대회 존재를 알리는 공식 이벤트뿐이며 규칙/대진/일정 권위가 없음
- `EXTERNAL_PARTICIPANT_AUTHORITY_MISSING`: 해외 올스타 2, 해외 팀 2를 포함한 14 slot의 현재 roster
  authority가 없음

2025 템플릿은 group 14팀/26 Bo1, LCQ 3팀/3 Bo3, final 4팀/6경기 double elimination
(Bo3/Bo5)을 구조화하지만 `REFERENCE_TEMPLATE_NOT_OFFICIAL_FOR_2026_OR_FUTURE`다. 14 slot 전부
unresolved이고 executable fixture/team/result는 0이다. Calendar에는 event별
`sourceReferenceYear=2025`, template ID와 두 blocker를 정보로 노출한다. 공식 날짜가 확인되지
않았으므로 기존 LCK/EWC window와의 충돌도 임의로 이동하거나 해소하지 않는다.

## Calendar gate와 frontend recovery

Gate는 competition ID 목록이 아니라 lifecycle/rule status/blocker를 해석한다. `BLOCKED`,
`SOURCE_GAP`, `POLICY_REQUIRED`, `EXECUTION_REQUIRED`와 source/policy/execution/authority blocker는
날짜 진행을 멈춘다. Ready fixture는 관리 팀 포함 여부에 따라
`MANAGED_COMPETITION_FIXTURE_REQUIRED` 또는 `AUTO_COMPETITION_FIXTURE_REQUIRED`로 멈춘다. Gate는
오늘 fixture뿐 아니라 현재 날짜보다 앞선 가장 이른 미완료 fixture도 overdue로 취급하므로, V1 저장
복구나 이전 Calendar 상태가 이미 뒤 날짜여도 LCK Cup을 건너뛸 수 없다.

V5 legacy `PENDING` row에 original mode/revision 증거가 없으면 payload에서 추측하지 않는다.
Startup recovery는 해당 row를 그대로 두고 Calendar를
`LEGACY_PENDING_RECONCILIATION_REQUIRED` 상태로 열며 새 advance를 차단한다. 새 command는
mode/revision/UUID/payload/Career binding을 모두 저장한다.

Frontend pending pointer는 Career별 UUID와 logical operation을 유지한다. Calendar GET의
`activePendingCommand`가 브라우저 저장보다 우선하므로 탭뿐 아니라 브라우저 프로세스를 다시 열어도
원래 UUID를 복원한다. bounded automatic retry가 소진돼도 현재 Calendar를 표시하고 활성
`대회 결과 확인`으로 authoritative GET/reconcile을 수행한다. completion으로 Competition revision이
바뀐 뒤에만 stale pointer를 지운다. Player는 같은 Series ID로 돌아가고 Auto는 같은 job ID를 다시
submit하므로 새 Series/gameplay/application을 만들지 않는다.

## 남은 제한

KeSPA 2026 규칙/참가자, LCK Playoffs bracket source, First Stand/MSI/EWC/Worlds 실제 상대·roster·fixture,
Asian Games 차출 효과, Career season rollover, 이적·훈련·피로·부상·재정은 아직 없다. Auto job은
durable result/application exactly-once를 제공하지만 single-node local worker이며 crash 시 미완료
게임의 CPU 계산 자체는 같은 frozen input으로 다시 수행할 수 있다. 다음 source 작업은
`CAREER_COMPETITION_RULE_SOURCE_CLOSURE_V1`이다.
