# Career Time and Calendar Progression V1

## 결과

Career 저장은 이제 `2026-08-24`에서 시작해 서버가 소유한 날짜를 하루씩 또는 다음 일정까지
진행한다. 현재/다음 대회와 stage, 공식·투영·pending 상태, R1~2의 다음 경기와 진행 정지 이유를
Career Dashboard에서 확인할 수 있다. Reload는 Calendar GET으로 같은 날짜와 revision을 복구한다.

신규 Career와 Calendar row는 Hybrid Season 및 90 fixtures와 한 transaction에서 생성된다. Flyway
V5 이전 Career는 기존 `current_game_date`를 backdate하지 않고 frozen template identity를 채운 뒤,
Career V1 binding이 안전할 때만 `MIGRATION_PENDING`에서 `ACTIVE`로 전환한다. 현재 snapshot 날짜
`2026-08-24`의 첫 full cycle anchor는 2027년이다.

## Source와 투영

무시된 raw source `일정/lck일정/`은 runtime에 직접 읽지 않는다. classpath의 정규화 resource는
15 source, 11 competition definition, qualification edge 6개, derived rest window 7개,
pending official field 6개를 loader에서 검증한다. Template canonical body hash는
`34a837ad384c49518093cc045d054540b889292002f9b71c01d53c20e1382e38`이다.

2026 reference 값과 source status는 그대로 보존한다. 미래 연도는 같은 현지 월·일을 결정적으로
투영하며 event ID에는 year scope를 넣는다. exact date가 없는 LCK Cup play-in, Asian Games main
event 등의 값은 채우지 않는다. KeSPA Cup도 source가 없으므로 note만 표시한다.

## R1~2 overlay와 진행

기존 Season의 fixture ID, team pairing, round, execution mode와 root seed를 입력으로 받아
`04-01`~`05-31` inclusive window에 18개의 결정적 round slot을 만들고 각 round 5경기, 전체
90경기를 배치한다. 이 날짜는 `GAME_DERIVED_SCHEDULE_POLICY`이며 공식 fixture date가 아니다.

날짜 진행은 이동 전 current-date gate와 target-date gate를 모두 확인한다. Auto fixture는 기존
durable dispatch/receipt/outbox/standings 경로만 사용하고, 관리 fixture는 기존 Player Series로
사용자를 돌려보낸다. Unsupported competition에서는 날짜와 표시만 진행하며 가짜 참가 팀,
qualification 결과, bracket 또는 경기 결과를 만들지 않는다.

Advance receipt는 UUID/payload/revision/result identity를 저장한다. 같은 UUID retry, HTTP 202 polling,
process restart에서 같은 command를 이어 가며 새 UUID가 기존 pending command를 추월하지 못한다.
Browser storage에는 Career ID와 pending advance의 Career ID/revision/mode/UUID만 둔다. Calendar,
fixture, standings view는 저장하지 않는다.

## Dashboard 보완

- Create dialog의 첫 실제 팀을 기본 선택하고 기본 저장 이름을 `{teamCode} 장기 저장`으로 만들었다.
- 사용자가 저장 이름을 편집한 뒤 팀을 바꿔도 입력을 덮어쓰지 않으며 감독 이름은 빈 값으로 시작한다.
- V1 pending create pointer의 `fingerprint`를 암호학적 hash로 오해하지 않도록 V2
  `canonicalSelectionKey`로 migrate하고, create 성공/replay 뒤 오래된 오류 banner를 즉시 지운다.

Calendar UI는 기존 carbon-black workbench에 compact rail/list로 추가했다. 1280×720에서는 calendar가
상세 아래로 stack되고 내부 scroll로 primary advance action과 stop reason을 계속 볼 수 있다.

## 검증과 제한

Focused backend는 calendar resource/count, 2027/2028 결정적 투영, null 보존, 90 overlay,
fixture/seed identity, advance exact replay/stale revision, file-H2 재시작과 V4→V5 migration,
관리 경기 stop과 같은 날 Auto dispatch를 확인한다. Frontend verifier는 strict Calendar/advance
schema, pointer migration과 KeSPA 미생성을 확인한다.

실제 isolated backend/browser에서는 Career 생성 → Calendar 확인 → 다음 일정 진행 → reload 후
같은 `2027-01-14`/revision 1 복구를 확인했고 1280×720에서 primary action과 overflow를 점검했다.
90경기 전체 시즌, 대형 seed population, balance/calibration/holdout은 실행하지 않았다.

다음 단계 `CAREER_COMPETITION_LIFECYCLE_V1`에서 현재 표시 전용인 competition과 qualification/
bracket을 각각 실제 Series authority에 연결한다.
