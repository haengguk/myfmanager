# Career 국제대회 선택권 수정과 반복 시즌 진행 V1

## 결과와 적용 범위

실제 시작 HEAD는 `c485fab59b8ca15d223a22cfc946bd41bbf784c6`다. 해당 HEAD에서 reset 없이
작업했으며, 시작부터 있던 사용자 소유 `prompts/`와 원본 자료는 수정하지 않았다.
MSI/Worlds 선택권 두 결함을 수정하고, 같은 Career의 시즌 마감 → 다음 시즌 생성 → 국내 및
FST·MSI·EWC·Worlds 재진행 → 그다음 시즌 초기화를 연결했다. 자동 commit/push/배포는 하지 않는다.

기존 Calendar는 연말 `SEASON_ROLLOVER_REQUIRED`에서 멈췄다. 이제 필수 결과가 모두 반영되면
Career 화면의 **시즌 마감 · 다음 시즌 시작**으로 다음 해 1월 1일에 진입한다. 다음 일정으로
이동하면 직전 최종 순위로 구성된 Cup을 진행하고, 새 League R1/R2와 후속 국내·국제대회를
같은 경로로 이어 간다. GET이나 새로고침은 시즌을 생성하지 않는다.

## 선택권 교정과 규칙 버전

| 경계 | 종전 결함 | 수정 기준 |
| --- | --- | --- |
| MSI Play-in 개막전 | 모두 지역 2시드라 지역 시드 비교가 동률 coin이 됨 | 금년 FST 지역 성과로 별도 `playInSeed`를 저장. 1–4, 2–3 매칭과 상위 대회 시드 RoFS |
| Worlds Play-in 개막전 | 지역 2시드가 3시드보다 자동 우선 | 지역 시드와 독립적인 저장된 결정적 추첨 |
| Worlds 동일 Swiss 전적 8강 | 대진 추첨과 별도의 coin으로 권한 결정 | 저장된 KNOCKOUT 추첨 벡터에서 먼저 뽑힌 팀 RoFS |

MSI 근거는 [2026 MSI 규정 §4.1.3.2·§4.2](https://cdn.sanity.io/files/dsfx7636/news_live/7487705ee4449ee59d163b4f92577127d6dbc365.pdf),
Worlds 근거는 [2026 Worlds 규정 §4.1.4.2~§4.1.4.4.1](https://cdn.sanity.io/files/dsfx7636/news_live/faa5ce974e58615911fbee931c6123e2785a8b46.pdf)다.
기존 작업에서 확보한 공식 PDF 원문 텍스트를 확인했다. 이번 웹 도구의 PDF 열기 실패를
근거 확인 성공으로 기록하지 않는다. Swiss 첫 세트의 지역 시드 비교, 다른 Swiss 전적의 상위 전적
우선권, Play-in 후속 추첨과 승자조 진출팀 결승 RoDS, 이후 세트 패자의 RoFS는 유지한다.
3–0 팀 반대 half 배치와 3–2 상대, 4강·결승 추첨도 기존 규칙을 유지한다.

`international-rules-v2.json` / `CAREER_INTERNATIONAL_GAME_POLICY_V2`를 추가했다.
V1 원본 리소스·해시·추첨 hash domain은 보존한다. 완료된 V1 국제대회는 V1로 계속 읽는다.
진행 중 V1은 V13 `career_international_selection_archive`에 원본 JSON과 해시를 보존한 뒤
V2로 전환한다. 이미 binding이 있거나 시작한 경기의 원래 대진·선택권·binding을 잠근다.
Play-in이 시작됐다면 그 대회의 기존 Play-in 매칭도 유지한다. 실행 전이고 binding이 없는
fixture만 수정된 대진·선택권·실행 모드와 일치하도록 바꾼다. 반복 reconcile은 추가 변경을 만들지 않는다.
신규 등록과 후속 시즌은 V2다. 이미 실행한 잘못된 선택권을 소급해 다시 판정하지 않는다.

집중 테스트에서 CBLOL이 높은 지역 성과를 얻으면 존재하지 않는 지역 3시드를 전제한 기존
Worlds pool 구성 때문에 등록이 실패하는 문제도 발견했다. V2는 기존 pool·지역 시드 우선순위를
유지하면서 MAIN을 4/4/4/3으로 채운 뒤 Play-in 승자를 pool 4에 넣는다. 일반적인 배정은 유지하며
게임 결과에 따른 지역 순서 변화도 실행할 수 있다.

## 시즌 소유권, 마감, 복구

V14 `career_season`은 연도/ordinal, 별도 League/Season ID와 seed, 이월 로스터,
ACTIVE/CLOSED 상태, 마감 당시 Calendar와 봉인된 성적 해시를 저장한다.
`career_save`의 최초 League/Season binding, Career root seed, 생성 receipt는 변경하지 않는다.
현재 Career 목록·상세·Calendar와 국내 순위 조회는 활성 시즌 참조를 사용하고,
과거 조회는 명시한 Career/연도의 시즌 참조를 사용한다.

`CareerSeasonApplicationService`가 다음 절차를 하나의 DB transaction으로 실행한다.

1. Calendar 명령 잠금 → 해당 Calendar 행 → 대회 cycle 순서로 잠근다. 원본 UUID receipt가
   있으면 새 시즌 생성 없이 그 receipt와 현재 시즌 목록을 반환한다.
2. 원본 연도와 예상 Calendar revision을 확인한다. 필수 국내 6개(Cup, R1/R2, Road, R3/R4,
   Play-in, Playoffs)와 국제 4개 완료, 국내 최종 순위 봉인, 국제 최종 결과를 확인한다.
3. 미완료 League/대회 fixture, 미반영 application, outbox, Player binding/checkpoint,
   Auto job/lease와 pending Calendar command가 있으면 원인을 반환하고 전환을 막는다.
   KeSPA는 명시된 비활성 참고 대회만 제외한다. 미완료 경기를 완료로 바꾸지 않는다.
4. 이전 League의 durable ledger와 frozen snapshot을 검증하고, 새 League/Season의
   18라운드·90경기를 기존 생성기로 만든다. 다음 Cup은 같은 Career의 정확한 직전 연도
   SEALED 최종 순위를 `initializeFuture`로 읽는다.
5. 이전 결과를 보존해 CLOSED로 기록하고 새 시즌을 ACTIVE로 만든다. Calendar 날짜는 다음
   1월 1일로 이동하고 revision은 증가한다. 커서와 시즌 내 진행은 새로 시작한다.
6. 원본 payload hash와 완료 receipt를 저장한다. 중간 실패는 생성된 League·시즌 행까지 rollback한다.

동일 Career/원본 연도의 유일 제약과 잠금으로 동시 전환 중 하나만 생성한다. 같은 UUID의 다른
payload는 충돌이고, 별도 UUID의 오래된 연도/revision은 stale이다. Y→Y+1 요청의 replay는
현재가 Y+2여도 원래 목적지 Y+1 receipt를 반환한다. 현재 projection은 별도로 Y+2를 표시한다.
새 대회 시작 명령에는 `sourceYear`를 추가했고 후속 시즌에서는 필수다. Calendar revision은
시즌마다 0으로 되돌리지 않는다. 오래된 UUID를 새 시즌 revision으로 바꿔 재사용하지 않는다.

## 로스터 이월과 대회별 입력

새 Career는 생성 transaction에서 56팀의 주전 로스터를 고정한다. 기존 저장은 최초 이월 시
이미 저장된 국제 등록과 선정 후보를 우선 보존하고, LCK 로스터가 최초 League snapshot과
동일한지 검사한다. 불일치하면 `INITIAL_CAREER_ROSTER_RESOURCE_CHANGED`로 막는다.
이월 이후 참가팀 선정과 대회 binding은 이 고정 로스터를 사용하며 최신 catalog로 능력치를
다시 채우지 않는다. 점수·Draft·Fearless history·경기 중 mutable state는 이월하지 않는다.

| 대회 | 후속 시즌에서 소비하는 입력 |
| --- | --- |
| Cup·국내 시즌 | 직전 연도 SEALED 국내 최종 순위 → 새 Cup. 금년 새 League R1/R2 → Road → R3/R4 → Play-in/PO → 금년 최종 순위 |
| FST | 금년 Cup 1·2위 + 금년 해외 임시 공급자. 직전 같은 Career Worlds 지역 성과로 첫 시드 pool 우선순위 결정. LCK/LPL 2장과 기타 지역 1장 배정은 별도 유지 |
| MSI | 금년 FST 지역 성과와 금년 Road 결과. Play-in 대회 시드도 이 금년 성과로 저장 |
| EWC | 직전 같은 Career 실제 EWC 우승 자격 + 금년 이월 로스터. 금년 Road/R1/R2·해외 공급자와 MSI 완료 조건 |
| Worlds | 금년 MSI 성과·보너스 + 금년 SEALED 국내 최종 순위와 해외 공급자. 전년도 MSI 보너스를 이월하지 않음 |

후속 EWC는 첫 cycle의 LCK 타이틀 대체를 반복하지 않는다. 전년도 우승팀이 일반 진출 슬롯에
없으면 그 팀을 선정하고, 이미 직접 진출했다면 같은 지역의 최상위 미선정 팀이 승계한다.
현재 입력에서 우승팀이 참가 불가능하면 같은 지역 후보, 그마저 없으면 전년도 EWC 지역 성과
순서의 후보로 승계한다. 자격 사유를 등록에 저장하며 전년도 경기 roster/Series를 복제하지 않는다.
비LCK 우승, 직접 진출 중복, 금년 순위가 내려간 우승팀의 독립 자격, 참가 불가 승계를 순수 테스트로 확인한다.

미래 규정과 pool·승계는 `FUTURE_SEASON_POLICY_V1`이라는 게임 정책이다. 실제 미발표 공식 규정이
아니다. 일정은 2026 월·일을 투영하며 화면에 “게임 내 투영 일정”으로 표시한다.
해외 공급자는 현재도 능력치 합산 임시 선정이며, `CareerInternationalParticipants`의 연도/대회
경계에서 실제 해외 리그 결과 공급자로 교체할 수 있다.

## 화면과 API

기존 Career 화면에 전환 버튼, 처리 중·원본 요청 재확인, 서버 마감 대기 사유와 연도 선택을
추가했다. 원본 연도/revision/UUID 전체를 sessionStorage에 보존한다. 전환 시작 시 이전 화면
요청 generation을 무효화하고, 복구 성공 후 활성 Career/Calendar를 다시 읽는다.

- `GET /api/v1/careers/{careerId}/seasons`: 활성 연도, 연도별 상태/ID/roster hash, blockers와 allowedCommands.
- `GET /api/v1/careers/{careerId}/seasons/{year}`: 국내 최종 1~10위, 국제 우승·참가·순위와 저장 경기.
- `POST /api/v1/careers/{careerId}/seasons/transition`: 원본 시즌/revision/UUID로 전환, 원래 receipt와 현재 projection 반환.

과거 연도를 선택하면 활성 Calendar 실행 영역을 숨기고 이어하기·전환을 비활성화한다.
과거 성적의 hash를 검증하며 국내 R1/R2 90경기도 기록에 포함한다. 실제 완료된 Player Series
checkpoint가 있는 경기만 `replayAvailable`로 링크를 제공한다. Auto 결과는 보존하되 존재하지 않는
Player checkpoint를 가리키는 버튼은 만들지 않는다. 과거 조회 자체는 활성 시즌을 변경하지 않는다.

## 검증 결과와 비용

새 테스트 클래스는 만들지 않고 기존 순수/DB 테스트와 빠른 결과 입력 helper를 확장했다.
대규모 통계, 전 선수 감사, CrossJvm probe, 별도 SHA 패키지는 실행·생성하지 않았다.

- `CareerInternationalTournamentTest`: 총 8개. 대회별 선택권, 저장 추첨 순서, V1 JSON 호환과
  후속 EWC 승계 사례를 포함한다. 최초 pool cardinality 실패는 위 V2 배정 문제 수정 후 통과했다.
- `CareerModePersistenceTest`의 새 다년 통합 사례: file-H2에서 2027 마감→2028 국내·국제
  결과 전이 완료→2029 초기화, 같은/다른 UUID 동시 요청, payload 충돌, 생성 중 rollback,
  DB 재개방, 원래 receipt replay, Career 간 분리, 최초 binding/과거 결과/로스터 보존을 확인했다.
  최신 기록 API 반영 뒤 해당 테스트 1개도 실패·오류·skip 0(테스트 본체 72.162초)으로 통과했다.
- V1 선택권 전환 DB 사례: 원본 JSON archive와 이미 bound된 MSI 권한을 보존하고, 미실행 fixture와
  새 binding의 권한을 일치시킨다. 반복 reconcile의 불변성도 확인한다.
- 관련 4개 클래스 집중 실행은 34개 중 1개 실패했다. V4 저장 생성 overload에서 V14 표에 접근한
  호환 결함이 원인이었다. 구버전 테스트 경로를 분리한 뒤 해당 migration 테스트가 1분 3초에 통과했다.
- 빠른 League 결과 helper는 처음에 실제 restore가 요구하는 binding/producer 증거를 갖추지 못해
  실패했다. production 검증을 완화하지 않고 verified persisted receipt와 job/outbox/application을
  갖추도록 기존 helper를 교정했다. 다년 전이+당시 순수 7개는 총 8개, 2분 45초에 통과했다.
- 기존 `CareerDomesticExecutionTest`의 실제 Player/Auto kernel 경계 2개도 통과했다.
  국내 BO3 Auto/BO1 Player, 해외 BO1 Auto/Player 실행을 재사용하며 대회별 장시간 완주를 복제하지 않았다.
- 프런트 `npm run career:verify` 36개, `npm run series:verify` 50개(함께 실행되는 기존
  Player Draft 계약 33개도 통과), `npm run build` 통과.
  마지막 build는 157 modules, Vite 8.67초다.

브라우저는 임시 file-H2 서버와 Chromium을 사용했다. 준비 단계의 2027 성적은 합성 verified
receipt와 실제 production 전이 로직으로 만들었다. **실제 엔진 다년 완주 증거가 아니다.**
서버에 전환 POST를 실제 적용한 뒤 응답만 끊고 새로고침했다. UI가 같은 원본 UUID로 다시
확인해 2028/2027 두 시즌만 남았고, 2029를 잘못 생성하지 않았다. 2027 국내 최종 순위와 네
국제대회 우승 기록을 확인하고 2028 Cup BFX–DNS Player Series에 진입했다. 새 Series는 0–0,
Game 1, Fearless 제외 0개였고 실제 Draft 화면까지 열렸다. 게임 전체를 브라우저로 완주하지 않았다.
최종 기록 API 확인 중 WSL Vite watcher가 최신 validator를 제공하지 않는 환경 문제가 있었다.
동일 응답은 디스크의 최신 validator에서 통과했다. 임시 개발 서버를 재시작한 최종 브라우저에서도
298개 과거 경기 기록과 국내 정규리그 결과, 읽기 전용 화면을 오류 없이 확인했다. 합성 준비
결과에 실재하지 않는 replay 버튼은 0개였다. 현재 2028 시즌 복귀 후에는 진행 중인 관리 경기의
정상 완료 대기 사유가 표시됐고 과거 기록 화면은 닫혔다. “대회 결과 확인”을 누르면 동일한
Series의 “진행 중인 Draft 계속”으로 복귀했다. 임시 Spring/Vite 서버와 브라우저를 종료했다.

최종 전체 회귀는 2026-09-06(KST)에 **첫 실행에서 통과**했다. 운영 코드·리소스·공유 fixture를
고정한 뒤 기존 2-worker로 아래 명령을 한 번 실행했다. **264 suites / 2,004 tests / 실패 0 /
오류 0 / 기존 skip 2, Gradle wall 22분 52초**, JUnit XML 테스트 시간 합계 2,392.119초다.
병렬 worker의 테스트 시간 합계와 실제 경과 시간을 구분한다. `test`는 실제 실행됐으며
compile/resource 준비 작업 4개만 UP-TO-DATE였다. 추가 full 재실행은 없었다.

두 skip은 기존 `PlayerDraftLatencyProfilingV1DiagnosticTest`와
`PlayerDraftPerformanceHardeningV1DiagnosticTest`다. 각각 명시적 환경변수로 실행하는
지연 프로파일·paired 성능 진단이며 이번에 추가/제외한 검증이 아니다. 기존 기본 task의
`diagnostic` tag 제외 설정과 고유 회귀 범위도 유지했다. 통과 후 실행 입력은 바꾸지 않고
문서 결과만 갱신했다. `git diff --check`도 통과했다.

```bash
JAVA_HOME=/tmp/lolmanager-temurin21 ./gradlew test --console=plain --no-daemon
```

## 저장 호환성과 남은 제한

- V1 국제 원본, 실행한 binding/receipt/추첨, 최초 Career 생성 증거와 이전 시즌 기록을 보존한다.
  구버전 국내 V2 저장의 기존 실행 제한도 이번 작업에서 임의로 해제하지 않는다.
- 미완료·취소·차단된 Player/Auto, pending 명령·결과, 실제 무결성 오류는 먼저 해결해야 한다.
  KeSPA를 가짜 완료로 만드는 전환 우회는 없다.
- League R1/R2는 이전 frozen snapshot을 유지하며 기존 production resource 일치 검증을 계속한다.
  저장 이후 엔진/catalog 자원이 달라진 경우 기존 무결성 경계에서 실행이 차단될 수 있다.
  자동 저장 포맷/엔진 버전 이주는 이번 범위가 아니다.
- KeSPA는 세부 규칙/참가 근거 확정 후 구현할 비활성 참고 대회다. 아시안게임 제외를 유지한다.
- 해외 리그, 이적·계약 만료·은퇴·성장·피로·재정은 구현하지 않았다. 로스터 이월 반복 시즌은 가능하다.

다음 우선순위는 해외 실제 시즌 결과 공급자를 현재 입력 경계에 연결하는 일, 로스터 변경과
엔진 버전 변경에 대한 명시적 저장 이주 정책, 근거가 확정된 KeSPA 실행이다.
구단 운영 변화는 현 불변 roster carry 계약을 대체하는 별도 정책으로 설계해야 한다.

제안 커밋 메시지: `국제대회 선택권을 교정하고 커리어 시즌 전환과 후속 대회를 연결`
