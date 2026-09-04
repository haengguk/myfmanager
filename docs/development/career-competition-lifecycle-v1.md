# Career Competition Lifecycle V1 targeted hardening

## 결과

기준 HEAD는 `921c07ae97375ef1fe6ffa9602a6e823f788a6bf`이고 현재 상태는
`CAREER_COMPETITION_LIFECYCLE_V1_TARGETED_HARDENED_KESPA_SOURCE_GAP`이다. 기존 Calendar/Competition
작업을 폐기하지 않고 V8, rule resource V2, canonical hash V2, startup recovery, LCK Cup graph와
KeSPA source note를 additive하게 연결했다.

## Phase A Before / After

| 결함 | Before | After |
| --- | --- | --- |
| instance hash | 저장된 hash 문자열을 graph 인증으로 충분히 재계산하지 못함 | seed/fixture/result/output/application 전체를 canonical V2로 매 load 검증 |
| cycle hash | child의 실제 state hash 결속이 약함 | ordered 12개 child ID+hash와 transition identity 결속 |
| competition gate | 일부 ID 중심 예외로 incomplete 대회를 건너뛸 위험 | lifecycle/rule/blocker 기반 generic gate와 due/overdue earliest-fixture 차단 |
| legacy pending | mode/revision null row가 GET canonical 검증을 깨뜨릴 수 있음 | 증거 없는 row는 explicit recovery blocker, GET은 정상 open |
| Calendar GET | reconciliation 과정에서 graph 생성/seal 가능 | 순수 load/view; startup/command/advance만 mutation |
| selector provenance | resolve 후 `FIXED_TEAM`처럼 source origin 상실 가능 | original selector와 resolved stable team을 별도 영속 |
| frontend retry | retry 소진 뒤 error-only/비활성 UI dead end | current pending view와 `상태 다시 확인`, 같은 UUID 유지 |

Tamper focused test는 selector/resolved team, date/match order, Bo3/Bo5, Hard Fearless,
winner/receipt/output, child hash, fixture duplicate/delete를 각각 저장 hash를 고치지 않은 채 변경하고
load 이전 `COMPETITION_INSTANCE_INTEGRITY_FAILURE`를 확인했다. Child hash를 정상 graph와 다른 값으로
바꾼 경우에도 instance에서, child가 갱신된 뒤 이전 cycle hash를 넣은 경우에는
`CAREER_COMPETITION_CYCLE_INTEGRITY_FAILURE`로 거부됐다.

## LCK Cup source package

Runtime resource는
`backend/src/main/resources/competition/lck-career-competition-rules-2026-v2.json`이며 byte SHA-256은
`f544319c2bb126fb26304a856a612edd8f75a77db551f9a4e923b69ad1ae2468`이다. Loader는 semantic parse
전에 이 값을 검증한다. 주요 공식 1차 출처는 다음과 같다.

- Riot Games / LoL Esports, `2026 LCK 진행 방식 안내`, 2025-11-06
- Riot Games / LCK, `2026 LCK 대회별 규정집`, 2026-06-29,
  raw SHA-256 `e4c52706feb67dfbac23669ce8cc0a01d5a780be71772b149849d41e9698a670`
- LoL Esports LCK 공식 일정, 2026
- LCK 공식 `2026 LCK CUP 드래프트`, 2026-01-06
- Riot Games / LoL Esports, `2026 LCK CUP 결승 안내`, 2026-01-29

`OFFICIAL_SOURCE_FACT`는 대회 구조, format, points, tie-break, routing, choice right와 First Stand
output에만 쓴다. `OFFICIAL_2026_INITIAL_BOOTSTRAP`은 첫 시즌 그룹/seed에만 쓴다.
`GAME_PRODUCT_POLICY`는 미래 group selection과 사람이 고르지 않는 opponent choice에,
`GAME_DERIVED_SCHEDULE_POLICY`는 exact 공식 과거 fixture date를 재현할 수 없는 ordered slot에 쓴다.

첫 playable Career 시즌은 현실 2026 시드/그룹 bootstrap만 사용한다. 현실 2026 경기 결과는
가져오지 않는다. 두 번째 시즌부터는 직전 게임 내 LCK 최종 순위가 유일한 seeding source다.
직전 `T01..T10` 예시는 Baron `T01,T03,T06,T07,T10`, Elder
`T02,T04,T05,T08,T09`이며 restart 뒤에도 같은 input/state hash다.

40경기는 25개 cross-group pair를 정확히 한 번 포함하고, 그중 동일 seed 5개를 Super Week로
표시한다. 일반 group match는 Bo3/1점, Super Week는 Bo5/2점이다. 이어서 Play-in 5경기와
6팀/10 Bo5 full double-elimination Playoff graph를 source selector로 보존한다. Choice right는
Play-in R2 seed 1, Playoff upper round 1 seed 3, upper round 2 seed 1이다. V1 게임 선택 정책은
eligible 상대 중 가장 낮은 seed, 동률 시 stable team code canonical order다.

## KeSPA source audit

2026-09-03에 공식 사이트를 다시 조사했다. 채택한 자료는 다음과 같다.

- 한국e스포츠협회, `2025 LoL KeSPA CUP 규정 공시`, 2025-12-12
- 한국e스포츠협회, `2025 KeSPA CUP 공식 규정집 V1.1`, 2025-11-12,
  raw SHA-256 `574ee7d8993b07a073e3ecc18f96227bbff48dc886ce4b336a61f5d8f4f8542a`
- 학교 이스포츠 / KeSPA, `2026 LoL KeSPA CUP 개막일 맞히기 이벤트`, 2026-07-02

최신 완전 자료는 2025이므로 `KESPA_CUP_REFERENCE_TEMPLATE_2025`, reference year 2025로만
저장했다. 2026 source는 대회 존재 외에 참가자/format/routing/exact schedule을 증명하지 못한다.
LCK 10 slot과 외부 4 slot 모두 현재 참가 확정 authority가 없고 외국/올스타 identity를 만들 수
없으므로 14 slot unresolved, fixtures/teams/results 0이다. Calendar에는 reference status와
`KESPA_CUP_2026_RULE_SOURCE_INCOMPLETE`, `EXTERNAL_PARTICIPANT_ROSTER_AUTHORITY_MISSING`를
machine-readable하게 노출한다. exact date가 없으므로 충돌을 임의 날짜 이동으로 해결하지 않았다.

## Verification

구현 중 focused lane은 기존 테스트 class만 확장했다. 첫 재실행은 새 top-level Cup format
`MIXED_BO3_BO5` 허용 vocabulary와 기존 API의 competition count 11 assertion을 발견해 각각
production vocabulary와 test-local expected count를 교정했다. 전체 회귀가 추가로 드러낸 V8 migration
count와 기존 R1~2 Calendar test setup도 교정했고, 현재 날짜 이전의 미완료 Cup까지 잡는 overdue gate를
production에 보강했다. 최종 affected lane은 4 suites / 24 tests / failures 0 / errors 0 / skipped 0,
aggregate XML 99.935초, Gradle wall 2분 18초다.

Frontend `career:verify`는 17 claims와 세 marker가 통과했고 production build는 Vite 5.4.21,
153 modules, 7.24초로 통과했다. 실제 브라우저 대표 흐름은 1280×720에서 자동 retry 소진까지 POST
12회가 모두 같은 UUID를 사용했고, 활성 `상태 다시 확인`을 누르면 GET 1회가 먼저 실행된 뒤 같은
UUID의 POST 1회로 terminal 상태를 복구했다. pointer와 버튼이 정리되고 Calendar가 다시 표시됐으며
horizontal overflow와 console error는 0이었다.

Complete backend `test`는 269 suites / 2,093 tests / failures 114 / errors 0 / skipped 2,
aggregate XML 2,153.563초, Gradle wall 36분 35초로 종료됐다. 이 중 변경 영역 5건은 위 overdue gate/
test setup/V8 count 수정 후 affected lane에서 통과했다. 나머지는 체크아웃에 존재하지 않는 과거
`build/reports/...` 진단 산출물 의존 108건과 full-load에서만 발생한 기존 League background GET 500
1건이었다. Background test는 단독 재실행에서 1 suite / 1 test, Gradle wall 3분 17초로 통과했다.
누락 산출물을 만들려면 명시적 비범위인 대형 과거 diagnostic pipeline을 재실행해야 하므로, 같은
환경에서 실패가 확정된 complete command는 반복하지 않았다. 따라서 clean complete regression은
미확보이며 이를 성공으로 표기하지 않는다.

90/130경기 LIVE, 모든 Cup/KeSPA Match, 대형 seed population, balance/calibration/holdout,
fresh-JVM matrix, JFR은 변경 위험에 비례하지 않고 명시적 비범위라 실행하지 않았다.

## Source integrity와 사용자 자료

`/일정/lck일정/`, `/능력치/`, `/선수정보/`는 기존 `.gitignore` 규칙을 확인했으며 중복 규칙을
추가하지 않았다. 해당 원본과 `prompts/`는 수정·이동·삭제하지 않는다. Runtime은 인터넷이나 루트
로컬 자료를 읽지 않고 검토 가능한 compact classpath resource만 사용한다. Match Engine,
Production V9, Draft, Matchup/Composition, Jungle policy와 gameplay Random에는 production diff가 없다.

초기/종료 감사에서 `/일정/lck일정/` 네 파일의 SHA-256은 각각 동일했다:
`README_lol_esports_2026.txt` `853851cb...bab26`, calendar formats `b47a6819...f7e01`, official
report `86b16a27...12b0a`, source ledger `0dd2a281...09b6`. 새 runtime resource byte SHA는
`f544319c2bb126fb26304a856a612edd8f75a77db551f9a4e923b69ad1ae2468`이며 loader가 parse 전에
검증한다. 작업용 DB/browser profile/download/JDK는 `/tmp`에서 정리했고 사용자 5173 서버는 종료하지
않았다.

## 다음 작업

우선 `CAREER_COMPETITION_SERIES_EXECUTION_AND_RESULT_TRANSITION_V1`에서 기존 Series/Match authority에
competition fixture와 choice receipt를 연결한다. 병행 source 과제는
`CAREER_COMPETITION_RULE_SOURCE_CLOSURE_V1`, 그 다음은
`CAREER_SEASON_ROLLOVER_AND_ROSTER_SNAPSHOT_V1`이다.
