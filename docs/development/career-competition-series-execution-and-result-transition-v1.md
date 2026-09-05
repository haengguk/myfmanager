# Career Competition Series Execution and Result Transition V1

## 상태와 범위

현재 상태는
`CAREER_COMPETITION_SERIES_EXECUTION_AND_RESULT_TRANSITION_V1_ACCEPTED`이다.
검토 기준과 실제 시작 HEAD는 `8ce3ddc789e0fee54fffbb081bc1f907611d2487`이다.

이번 변경은 source-complete인 `LCK_CUP` 40경기, `LCK_ROAD_TO_MSI` 5경기,
`LCK_REGULAR_R3_R4` 40경기, `LCK_PLAY_IN` 3경기를 기존 BO3/BO5 Series, Player/Auto Draft,
Hard Fearless와 Production V9에 연결한다. R1~2 90경기는 기존 League authority를 유지한다.
KeSPA Cup, source-incomplete LCK Playoffs, 실제 국제대회 상대/roster/fixture는 만들지 않는다.

## 선행 경계

- due/overdue 첫 non-terminal fixture는 `READY`가 아니어도 Calendar를 막는다. predecessor, seed,
  opponent choice가 미결정이면 원인을 구조화한 blocker를 반환한다.
- 첫 playable cycle만 official 2026 bootstrap을 사용한다. 미래 cycle은 같은 Career의 직전 sealed LCK
  total order를 DB에서 검증한 opaque ranking으로만 초기화한다.
- LCK Cup stage는 고정 문구가 아니라 persisted next fixture의 `GROUP_BATTLE`, `CUP_PLAY_IN`,
  `CUP_PLAYOFFS` 또는 `COMPLETED`를 투영한다.
- 과거 `build/reports`를 요구하는 대형 calibration/audit consumer는 diagnostic tag/task로 격리하고
  default correctness test에서 제외했다. correctness invariant는 제외하지 않았다.
- full-load background 조회는 Season aggregate와 job snapshot의 서로 다른 시점을 섞지 않도록 한
  read snapshot에서 projection하며 focused background integration으로 재현한다.

## 실행 binding과 소유권

`CAREER_COMPETITION_SERIES_BINDING_V1`은 Career/year/competition, rule/resource/game policy,
instance hash/revision, fixture/match/stage/order, original selectors와 resolved teams, BO3/BO5,
Hard Fearless, execution mode, side policy, root seed/algorithm, bound Series, initialization/materialization
receipt와 production snapshot/resource identity를 explicit-order canonical UTF-8 hash에 묶는다.
브라우저는 schema, expected revision, logical UUID만 제출한다.

관리 팀 fixture는 `COMPETITION_BOUND` Series checkpoint를 만들고 기존 Player Series 화면으로 연다.
상대 Draft turn은 기존 Auto Draft이며 각 game은 기존 Production V9을 사용한다. 비관리 fixture는
fixture 하나가 durable Auto job/lease/fence 단위이고 game은 같은 frozen binding 안에서 순차 실행된다.
Competition adapter가 public Series HTTP를 내부 client처럼 호출하거나 평행 simulation engine을 만들지
않는다.

## Verified completion과 exactly-once

Auto/Player kernel의 completed evidence는 completion verifier를 거쳐야 한다. Verifier는 binding과
Career scope, Series/teams/side/root/game seed, ordered Draft decisions/final assignments, Hard Fearless
history, Production profile/policy/configuration/engine/resource, output/replay/timeline/Random fingerprint,
decisive final score와 winner를 compact game receipt에서 다시 확인한다. 성공한 경우에만 외부에서
생성할 수 없는 verified value가 만들어진다.

적용 transaction은 fixture result, detail/application ledger, dependent selector, instance/cycle hash와
revision, Cup standings/seed/opponent choice/qualification output, outbox 상태를 함께 갱신한다. 동일
receipt나 command replay는 새 gameplay와 mutation이 0이고, cross-fixture/Career/season evidence는
거부된다. Auto lease를 잃은 late worker는 fence에서 결과 적용 전에 거부된다.

## LCK Cup 결과 전이

Group Battle은 일반 BO3 승리 1점, Super Week BO5 승리 2점을 resource의 structured point 값으로
적용한다. 25경기가 모두 verified completion일 때만 Baron/Elder point와 individual standings를
봉인한다. match/game record, strength of victory, win time와 tie-break trace/hash를 저장하며 공식
추가 tiebreak가 필요한 동률은 `LCK_CUP_TIEBREAKER_REQUIRED`로 멈춘다.

봉인된 순위는 승자 그룹 상위 2팀과 패자 그룹 1팀의 Playoff 직행, 승자 그룹 3~5위와 패자 그룹
2~4위의 Play-in 6 seed를 만든다. Play-in 5경기 뒤 Playoff seed 4~6을 채우고, 10경기 bracket은
predecessor winner/loser, lowest available seed, remaining winner와 higher/lower seed loser selector를
순서대로 해석한다. 세 상대 선택은 owner, eligible seed order, chosen opponent와 policy hash receipt를
남긴다. Final은 `FIRST_STAND_LCK_SEED_1/2`만 exactly once 생성한다.

Pure transition test는 40개 result/application을 빠르게 적용해 25+5+10 graph와 세 choice receipt,
First Stand seed를 검증한다. 40개 실제 Production V9 Series는 실행하지 않으며 대표 Auto fixture
하나만 actual engine smoke로 실행한다.

## API와 사용자 흐름

Calendar의 additive Competition view는 current competition/stage, next fixture, selector/team,
binding/job/application, group standings/points, seed/output, blocker와 `allowedCommands`를 제공한다.
`start-or-resume`와 `reconcile`은 최소 command identity만 받고 Auto는 202 job 상태, Player는 기존
Series ID를 반환한다. 미래 날짜 fixture는 버튼이 없고 직접 호출도 거부된다.

Career Dashboard는 관리 fixture에서 기존 Series/Draft로 이동하고 완료 후 Career return context로
돌아와 verified result를 reconcile한다. Auto fixture는 실제 job 상태를 polling한다. Calendar GET의
`activePendingCommand`가 원래 UUID를 브라우저에 다시 결속하므로 브라우저 프로세스나 서버를 다시
시작해도 같은 job/Series/command를 사용한다. completion으로 Competition revision과 next fixture가
바뀐 뒤에만 operation을 제거한다.

## 검증과 브라우저에서 발견한 결함

구현 중 Playwright 대표 흐름은 다음 실제 결함을 발견해 수정하게 했다.

- fixture 날짜 전에도 실행 버튼이 노출되던 문제: Calendar current date를 command boundary에 전달해
  allowed command와 직접 호출을 모두 fail-closed했다.
- H2의 `DATEADD` parameter type 추론으로 Auto lease claim이 실패하던 문제: injected `Clock`에서
  claim/renew/failure timestamp와 5분 expiry를 계산해 저장한다.
- Competition fixture ID를 기존 runner 값 객체가 거부하던 문제: `fixture_<sha256>`와
  `competition_fixture_<sha256>`를 구조화된 두 schema로 명시적으로 허용했다.
- 브라우저 프로세스 재시작 후 fresh UUID로 reconcile하던 문제: server-owned
  `activePendingCommand`로 원래 UUID를 복원하며 Player active command도 같은 projection을 사용한다.

실제 브라우저에서 GEN 관리 fixture가 server-issued Series로 열리고 reload 뒤 같은 Series/Game 1/
0:0으로 복구되는 것을 확인했다. 별도 T1 Career의 GEN-DK Auto fixture는 파일 H2에 PENDING으로 남긴
뒤 backend/browser를 재시작했고, 원래 UUID로 202 재제출한 결과 Cup progress가 `0/40`에서 `1/40`,
next fixture가 `GB_B1_E2`에서 `GB_B3_E4`로 바뀌었다. 이전 operation은 완료 뒤 제거됐다.

## 남은 제한

KeSPA 2026 규칙/외부 참가자 roster, LCK Playoffs bracket source, 국제대회 실제 상대와 roster,
Asian Games gameplay 효과, 다음 시즌 rollover, 이적·훈련·피로·부상·재정은 여전히 차단된다.
Auto worker는 local single-node다. Durable binding/result/application은 중복되지 않지만 crash가 실제
game 계산 도중 발생하면 같은 frozen input으로 CPU 계산을 다시 할 수 있다. Multi-node coordination과
authentication/ownership은 후속 범위다.

## 검증 결과

최종 focused backend는 5 suites / 29 tests / failures 0 / errors 0 / skipped 0,
Gradle wall 3분 57초로 통과했다. Career frontend contract 21 scenarios와 네 marker,
shared Series verifier, frontend production build 153 modules도 성공했다. 대표 browser
Player/Auto/restart flow가 실제 backend/file-H2에서 통과했다.

첫 complete backend run은 1,981 tests 중 과거
`build/reports/champion-pair-interaction-shape/...csv`를 읽는 `GeometricCandidateTest` 한 메서드만
실패했다. 제품 회귀가 아니라 historical artifact consumer의 diagnostic tag 누락으로 분류했고,
해당 메서드 하나만 격리했다. 같은 class의 나머지 correctness 14 tests는 failures/errors 0으로
통과했다. 두 번째이자 최종 complete regression은 260 suites / 1,980 tests / failures 0 / errors 0 /
skipped 2, aggregate JUnit XML 2,184.812초, Gradle wall 37분 12초,
`BUILD SUCCESSFUL`이다. 이후 executable production Java/resource/Gradle/shared fixture는 변경하지 않았다.
