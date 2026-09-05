# Career Competition Lifecycle V1

## 현재 상태

국내 실행은 규칙 resource V3와 V11 저장을 사용한다. Cup SoV·승리 세트 평균·통합 Play-in seed,
R1/R2와 R3/R4의 H2H·다자간 동률, 실제 추가 경기, 시즌 말 LCK_PLAYOFFS 10경기,
우승팀·최종1–10위 봉인을 기존 Competition/Series/League 경계에 연결했다.
2026-09-05 안정화의 V10 scoped choice key, heartbeat, 원자적 완료 fence 및 durable replay를 유지한다.
BO1 동률전은 기존 BO3/BO5 실행 계층의 additive 확장이며 Cup 그룹 동률은 부모 아래 고정 두 팀 BO1 자식들로 표현한다.
KeSPA Cup, 국제대회와 아시안게임 gate, 실제 rollover는 여전히 미구현 범위다.
[국내 순위·PO·최종 순위 보고서](../development/career-domestic-ranking-playoffs-finalization-v1.md)에
공식 근거, 게임 정책, 기존 저장 처리 및 실제 검증 결과를 기록한다.

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
V10은 opponent-choice PK를 `(career_id, calendar_season_year, competition_id, match_id)`로
바꾼다. `choice_hash`는 선택 내용의 무결성 값으로 그대로 유지하며 동일 내용의 다른 Career/시즌
저장을 허용한다. 기존 row·조회 범위·canonical bytes를 바꾸지 않는다.

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

`CareerCompetitionJobLease`는 개별 game과 verifier 밖의 독립 scheduler로 lease를 갱신한다.
설정은 `lolfm.career.competition.lease.duration-millis`(기본300000),
`lolfm.career.competition.lease.heartbeat-millis`(기본60000)이며 후자는 전자보다 작아야 한다.
갱신 실패는 worker 수명 동안 유지되고, 완료/실패의 guard close와 application 종료 시 scheduler를 정리한다.
최종 적용은 job row의 token/status/expiry 조건부 갱신으로 소유권을 확인·잠근 transaction 안에서
fixture/receipt/graph와 job COMPLETED를 함께 commit한다. 마지막 만료 검사 실패도 전체 rollback한다.
짧은 commit과 heartbeat callback은 같은 guard로 직렬화해 갱신 실패와 적용 사이의 간격을 닫는다.

이미 적용된 command는 payload·Career/year·binding·receipt canonical/hash·application·result detail·
fixture와 graph 관계를 확인한 뒤 durable COMPLETED로 반환한다. Player start/resume/completedEvidence와
engine replay를 호출하지 않는다. 최초 completion verifier는 그대로 유지한다.

## LCK Cup과 다른 국내 대회 전이

LCK Cup Group Battle 25경기의 verified 세트 원장을 읽어 SoV와 승리 시간 평균으로 그룹 순위를 판정한다.
개인/통합 시드 동률과 그룹 포인트·득실 동률은 필요한 추가 fixture를 먼저 실행한다.
그룹 동률 부모 BO5의 홀수/짝수 세트는 각각 그룹5위/1위 팀이 참가하고, 각 BO1 자식은 고유한 팀·로스터·binding을 갖는다.
전 세트 picks를 다음 자식의 initial history에 결속하여 Hard Fearless를 공유한다.
진출6팀은 통합 성적순으로 시드를 받고 기존 Cup 5개 Play-in과 10개 Playoff graph를 진행한다.
Cup Final은 First Stand LCK seed1/2를 만들며 국제 결과를 만들지 않는다.

`LCK_REGULAR_R1_R2` 90경기 실행·원장은 계속 League가 소유한다. Competition은 같은 Career/year의
V2 완료 봉투·세트 원장·standings application을 읽어 선행 H2H 순위를 교정한다.
그 순위로 Road5경기와 Legend/Rise40경기를 만들며, 합계130경기 원장으로 후속 그룹 순위를 결정한다.
Play-in3경기와 Legend상위4에서 PO s1–6이 확정되면 시즌 말 `LCK_PLAYOFFS` 10 BO5를 materialize한다.
선택권자는 s3/s1, 자동 상대 선택은 eligible 최하위 시드이며 scoped receipt로 저장한다.
U2패자 중 낮은/높은 시드는 각각 L2/L3으로 전달한다. 단일 결승이며 reset은 없다.

순위 판정은 `career_domestic_ranking_decision`에 대상·기준·exact 지표·input hash·pending fixture·정책을 보존한다.
2–10팀 추가 대진은 seeded deterministic 순서로 재개하며, 같은 단계의 필요 경기 수로 BO5/BO3/BO1을 정한다.
진출·상금 무관 하위 묶음의 경기 생략과 고유 저장 위치 배정은 보고서에 명시한 게임 정책이다.
Calendar는 미결 동률 fixture를 후속 대진보다 먼저 노출한다. 화면 API는 판정·최종 순위·규칙 호환 상태를 additive하게 제공한다.

PO 결과1–6, Play-in탈락7–8, Rise하위9–10을 기존 `career_lck_final_ranking_snapshot/row`에 봉인한다.
마지막 검증 결과 적용과 봉인은 같은 transaction이며, 10팀·10위치의 유일성 및 source/rule/policy/result hash를 검증한다.
승패/세트 열은 정규시즌130경기만 집계한다. 같은 Career의 다음 cycle 초기화 소비자는 직전 SEALED 순위를 읽는다.
Worlds 지역 슬롯·MSI 특례는 게임 내 국제 증거 대기로 남기며 국내 봉인을 막지 않는다.

## KeSPA Cup source gap

KeSPA 분류는 다음과 같다.

- `REFERENCE_TEMPLATE_ONLY`: 2025 공식 공시와 규정집 V1.1
- `SOURCE_INCOMPLETE`: 현행 resource의 2026 근거가 부족한 상태. 최신 현실에서는 2026-06-30,
  07-16, 08-11 협회 발표로 LCK10팀·최대10인 roster·7/20–8/18 일정·단계 일부를 확인했다.
  전체 규정과 등록 roster, stage1 전체 routing은 여전히 미결이다.
- `EXTERNAL_PARTICIPANT_AUTHORITY_MISSING`: 해외 올스타 2, 해외 팀 2를 포함한 14 slot의 현재 roster
  authority가 없음

2025 템플릿은 group 14팀/26 Bo1, LCQ 3팀/3 Bo3, final 4팀/6경기 double elimination
(Bo3/Bo5)을 구조화하지만 `REFERENCE_TEMPLATE_NOT_OFFICIAL_FOR_2026_OR_FUTURE`다. 14 slot 전부
unresolved이고 executable fixture/team/result는 0이다. 이 14-slot 상태는 2025 template에 해당하며
2026에도 해외4팀이 필요하다는 의미가 아니다. Calendar에는 event별
`sourceReferenceYear=2025`, template ID와 두 blocker를 정보로 노출한다. 확인된 2026 날짜의 runtime
반영과 LCK/EWC window 충돌 처리는 별도 규칙·저장 전환 작업이며 이번에는 일정을 이동하지 않았다.

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

Career 선택/재조회와 unmount는 화면 요청을 abort하고 generation을 무효화한다. 각 비동기 응답은
현재 Career ID·generation·controller identity를 확인한 뒤 Calendar/pending/error/알림/navigation을
바꾼다. A→B→A에서도 예전 A 응답은 무효다. 화면 취소는 서버 Auto job 취소가 아니며 Career별
durable command UUID를 보존하고, 복귀 시 서버 pending command로 같은 작업을 확인한다.

## 남은 제한

KeSPA 2026 세부 규칙/등록 roster, 시즌 말 LCK Playoffs 실행, First Stand/MSI/EWC/Worlds 실제 상대·roster·fixture,
Asian Games 차출 효과, Career season rollover, 이적·훈련·피로·부상·재정은 아직 없다. Auto job은
durable result/application exactly-once를 제공하지만 single-node local worker이며 crash 시 미완료
게임의 CPU 계산 자체는 같은 frozen input으로 다시 수행할 수 있다.
2026-09-05 source 조사에서 LCK Playoffs routing과 국제 형식을 확인했지만 production readiness는
바꾸지 않았다. 규칙 근거 문서의 미결 질문·제품 선택·의존 순서를 다음 작업 기준으로 사용한다.

## V3 규칙과 V11 저장 호환

V2 canonical carrier는 유지하고 새 판정/exact metric 행이 있는 경우에만 이를 해시에 추가한다.
V2 resource는 기존 바이트와 해시를 계속 검증한다. startup은 결과·binding·R1/R2 import·Cup standings·최종 순위가
없는 V2만 V3로 전환한다. 사용한 V2는 기존 결과/진행 binding을 보존하고 신규 실행을 차단한다.
기존 pending command 재개와 완료 replay는 해당 저장 규칙을 따른다.
V11은 판정 표, 최종 snapshot의 rule/policy/champion/runner/result authority, Cup exact metric 열을 추가한다.
Series checkpoint의 optional competition side policy와 initial exclusions는 기존 빈 history 저장의 의미를 유지한다.
첫 픽/진영 선택의 주체·결과는 `SeriesView.competitionContext`로 분리하여 제공한다.
