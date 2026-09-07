# Career 성장·훈련·숙련도·피로 V1

구현 및 필요한 검증 완료. 시작·최종 HEAD 모두 `834c5ce7cf80330ae5d8360c1c3cd5440691255f`, main. 변경은 working tree에 있으며 commit/push/배포는 수행하지 않았다.

## 초기 정책 선택 (구현 전 계산)

정상 훈련 10, 실제 게임 60 내부 단위. 1 능력치=1,000. 20세 이하 계수1, 21~24세0.65, 25~27세0.30, 28세 이상0.08; 생일은 게임 날짜, 생일 미확인은23세 계수. PA 여유12,000 미만에서 남은 내부총량/12,000 비례 감속. 표시 CA는 기존 `round(1 + (정수12개 합계−12)×199/228)` 공식이다. PA의 내부총량 상한은 `12,000 + floor((PA−1)×228,000/199)`이며 정수 CA 표시 반올림과 구분한다. 최초 상태가 이미 이를 넘으면 보존하고 추가 능력치 성장을 중단한다.

모두 시작12×15(실제 표시 CA148), 충분한 PA190, 365일·경기일100일 각1게임이라는 순수 계산 후보: 19세 주전+8,650 내부/CA155, 후보+3,650/CA150,23세+5,622/CA152,26세+2,595/CA149,29세+692/CA148. PA149 근처+803/CA148, PA없음0. 이 후보 계산을 바탕으로 아래의 생일/피로/집중 배분 모델을 검증했다. 단위 오류와 전 항목 정체를 피하고 요청 참고 범위에 대체로 맞아 이 계수를 채택한다. 실제 경기 엔진·시장 상수는 변경하지 않는다.

피로 내부0~1000: 경기당90, 정상훈련40, 강훈련150, 가벼운훈련15, 일일회복120, 회복계획 추가80. 정상 BO3주1회 순변화−330,주2회−100, 경기 없는 강훈련+210/주. 28일 순수 계산 주1/2BO3 최고270 종료0; 강훈련 종료840. 낮은 피로에서 정상운영이 가능하고 과도한 강훈련은 효율을 잃도록 선택했다. 아래 최종 계산의 최고점은 경기와 훈련 직후를 모두 포함한다. 경기력 직접 감점0.

## 저장과 정산 구현

V19는 기존 디렉터리의 초기화 버전 표시와 Career별 고정소수 상태/명령/경기 보상 바인딩/시즌 마감 상태를 저장한다. 기존 디렉터리 JSON과 hash는 수정하지 않는다. 명부 조회 경계에서 불변 데이터와 현재 상태를 합성한다. 명령·이주·완료·날짜는 Calendar 행 잠금 아래 실행하며 GET은 상태를 쓰지 않는다. 시작된 경기의 canonical 및 hash에 성장·피로 필드를 추가하지 않는다.

훈련은 D+1 예약을 현재 계획과 별도로 보관하고 D 마감→D+1 시장 처리 순서다. 임대의 운영 소속을 훈련 주체로 사용하며 소속이 다른 예약은 적용하지 않는다. 후보·육성도 훈련하며 실제로 실행하지 않은 경기에 XP는 없다. FA/미확인 소속은 LIGHT와 자율효율50%를 결합해 정상 팀 훈련의25%를 받는다. 육성 자동 보너스 없음.

능력치 예산: 기본XP × 나이계수 × 강도 × 피로효율 × PA여유계수. 각 계수는1,000 기준, 분모10^12의 나머지를 영속화한다. 내림은 최종 내부 단위 지급 시 한 번이다. PA여유계수=min(1, 남은엄격상한/12,000). 부족한 PA/상한도달 시 양수 예산을 축적하지 않는다. 항목20은 건너뛰고 나머지 항목에 재분배한다. BALANCED는 enum 순서의1,000단위 블록을 순환한다. COMMON/ROLE은 장기70:30, SPECIFIC은13개 목표블록과11개 타항목블록(약54:46)을 교차한다. 채널별 커서와 진행량은 저장되므로 계획 변경·재시작으로 우선순위를 초기화하지 않는다.

숙련도는 일반훈련0, CHAMPION_FOCUS 하루40×강도×피로효율의 공동예산을1/2개에 먼저 나눈다. 이때 일반 능력치 훈련예산은50%. 실제게임80. 숙련도 감쇠=min(1,max(0.1,(20,000−현재)/6,000))이며 고정소수 나머지를 보관한다. 14에서 게임당80,19에서 약13.28,19.4이상에서8 내부단위/게임. 미작성 합법키14, 불법 훈련키는 거절한다. PA와 별개이고 rust는 없다.

피로는 경기완료 시 게임당90을 더한 뒤0~1,000 제한. 날짜마감은 현재 피로로 훈련효율을 계산하고 실제 출전일이면 훈련을 생략하며 훈련부하→상한→일일회복120→회복계획 추가80 순서다. 효율은피로300이하100%, 그위에서선형감소해1,000에서25%. 회복은훈련XP0이다. 경기 시간계수1,국제/국내XP동일. 경기력·Random·안정성·선발 변경효과0.

후속 피로 경기력 적용 버전은 필수 조건값과 hash를 Series 실행입력·저장·receipt·재실행에 결속해야 한다. 구버전 중립 조건과 새버전 필수데이터 누락은 구분하고 누락을 피로0으로 대체하지 않는다.

팀 기본은 균형/공통/역할 초점을 제공한다. 팀의5개 포지션에 하나의 역할 능력치나 챔피언을 강제로 적용하지 않도록 특정 능력치·챔피언 초점은 선수별 설정에서 제공한다. COMMON/ROLE의70:30은10개의1점 블록마다7:3으로 교차하고 각 그룹 안에서 항목을 순환한다. 따라서 보조 그룹 배분이 수십 점 뒤로 밀리지 않는다. 같은 챔피언만 완료 게임에서 사용한 순수 숙련도 계산상14→15는14게임,18→19는52게임,19→20은114게임이다.

## 대표 순수 계산 결과

모두 시작 능력치12×15, CA148. 2027-01-01부터365일이며 게임이 있는 사례는 첫300일 동안3일마다1게임, 총100게임이다. 실제 경기 엔진은 호출하지 않는다. 생일은1월1일로 고정해 해중 연령 구간을 바꾸지 않았다. 동일 챔피언 반복 사용은 숙련도 속도 관찰용 합성 입력이며 실제 Hard Fearless 시즌의 챔피언 분포를 뜻하지 않는다.

| 프로필 | PA | 종료 CA | 내부 능력치 증가 | 종료 상태 |
|---|---:|---:|---:|---|
|19세·100게임|190|155|8,650|앞8개 항목16, 라인관리15.650, 나머지15|
|19세·후보·0게임|190|150|3,650|앞3개 항목16, 위치선정15.650, 나머지15|
|23세·100게임|190|152|5,622|앞5개 항목16, 안정성15.622, 나머지15|
|26세·100게임|190|149|2,595|조작/판단16, 전장인식15.595, 나머지15|
|29세·100게임|190|148|692|조작15.692, 나머지15|
|19세·PA 근처|149|148|803|조작15.803, 나머지15|
|19세·PA 없음|없음|148|0|원래 능력치 유지|

100게임 사례의 대상 숙련도는14.000→18.430, 출전 없는 후보는14.000 유지다. 모두 종료 피로0. 이는 참고 계산이며 실제 선수별 보장량이나 장기 전체 밸런스 검증이 아니다.

28일 피로 계산: 정상 BO3주1회/2회는 각각 주간 순변화−330/−100, 최고270, 종료0. 경기 없는 강훈련은 주간+210, 훈련 직후 최고960, 종료840이다. 3일 연속5게임의 밀집 일정은 최고1,000/마지막 경기일 마감880; 이후4일 회복으로80, 정상훈련 하루 후0이 된다. 정상 일정의 안정성과 밀집 일정 회복을 구분해 확인했다.

## 집중 검증 기록

- 최초 집중: 정책11건·저장1건 통과, Auto1건은 숙련도20에도 상승을 요구한 테스트 단언으로 실패. 강제상한을 유지하도록 단언을 교정했다.
- 이적/임대·이주 및 Auto 집중: 저장2건, 실제 Auto1건, 파일 이주1건 통과. 시즌 테스트1건은 합성 receipt의 `player-test-*` 참가자를 실제 고정 선수로 잘못 취급해 실패했다. 합성 도우미에 고정 선수 ID와 합법 챔피언을 연결했고 제품의 입력 결속 검사는 유지했다.
- 시즌 이월 재검증: 정책11건과 기존 두 시즌 이월1건 통과, Gradle9분48초(이월 테스트533.304초). 실제 경기 엔진으로 두 시즌을 완주한 결과가 아니다.
- 최종 집중: 정책12건(4.665초), 저장/날짜1건(34.599초), 실제 Auto1건(93.192초), 모두 통과. Gradle2분36초. 양방향 초기화 표시 손상 검사를 추가한 저장1건도 통과, Gradle1분37초.
- 프런트: 기존 Career verifier에8건 추가해74건 통과, production build9.69초 통과.

실제 Auto 관찰은 별도 시험 Career의 KT–T1 Series다. 사전에 순수 정책으로 준비한 Life 성장 상태를 새 고정 입력에 넣고 실제 Production V9으로3게임을 완료했다. Life 위치선정 내부값14,600→14,654(+54), 정수는14 유지. 고정 입력의 합계175와 새 기준연봉175,000이 일치하고, 기존 합의 이적료는 유지된다. 게임별 실제 playerId/championId/position을 검사했고 숙련도20은20을 유지하며 나머지 실제 사용 키만 성장한다. 미출전 Fenrir는 완료 보상으로 변하지 않는다. 완료 쓰기 직후 강제 예외의 전체 롤백, 같은 receipt 재시도, 내용이 다른 완료 충돌도 확인했다.

Player 시작 경계 추가 중 기존 저장 도우미가 `PlayerDataStore`를 생략해 Peyz PA가 미입력인 문제가 드러났다. 합성 예산을 늘려도 정책대로 성장이0이었고 두 집중 실행은 실패했다(1분26초/1분25초). 해당 사례의 새 Career 준비만 실제 PA 저장 경로로 연결했다. PA 미입력 성장 중단과 제품 수치는 변경하지 않았다. 교정 후 같은 저장/Player 경계1건이 통과했다(Gradle1분21초, 실패·오류·skip0).

파일 DB에서는 이주 반복, 초기화 직후 원래 프로필 전체 동일성, 이주 이전 시작 경기의 보상 제외, D+1 계획 예약,8일 일괄/하루씩 진행의 성장·AI 시장 상태 일치, 날짜 원본 UUID 재시도, 다른 Career 분리, 저장 후 재시작과 원본 훈련 receipt 복구를 확인했다. 초기화 표시 또는 상태행만 사라진 경우 모두 손상으로 거절한다.

## 실제 브라우저 관찰

임시 파일 DB와 별도 포트18185/5173에서 새 T1 Career를 만들었다. 2026-08-24의 Peyz(CA191/PA198)에 강훈련·균형을 예약했고 적용일은8월25일이었다. 서버 저장 후 응답을 한 번 소실시킨 다음 새로고침해 원본 UUID `a5d50ea6-e2b7-4563-81a6-6703de81812e`를 재전송했다. 서버는 `replayed=true`, 같은 revision1·적용일을 반환했다.

화면의 하루 진행을 두 번 사용했다. 8월25일에는 기존 정상훈련 정산으로 판단력18.006·피로0, 26일에는 강훈련 정산으로18.014·피로3.0/100이었다. 표시 능력치는18, CA191/PA198은 유지됐다. 다시 새로고침한 뒤에도 강훈련 계획·+0.014·피로3.0을 확인했다. 실제 날짜 blocker 우회나 가짜 경기 완료는 사용하지 않았다. 의도한 응답 소실의 네트워크 오류1건 외 추가 브라우저 오류는 없었다. 화면 기록은 로컬 `output/playwright/career-development-training.png`, 관찰 로그는 `/tmp/career-development-browser-result.txt`다.

브라우저는 Player 경기를 실행하지 않았다. Player 검증은 기존 시작 입력 보존과 새 PLAYER_CONTROLLED 경기의 현재 프로필 고정 경계로 한정한다. 실제 경기 완료 보상은 위 Production Auto3게임에서 확인했다.

## 연결 경계와 주요 파일

- `CareerDevelopmentPolicy` / `CareerDevelopmentState`: 수치·배분·나머지·예약·피로의 순수 정책과 저장 모델. 나이와 성장 상한에만 PA를 사용하며 AI의 모집/가격/훈련 선호에는 PA를 사용하지 않는다.
- `CareerDevelopmentEngine` / `CareerDevelopmentStore` / `V19__career_player_development.sql`: 일자 정산, 현재 프로필 합성, 초기화, 원본 명령, 완료 중복 방지와 시즌 보관.
- `CareerRosterStore` / `CareerMarketStore` / `CareerMarketEngine`: 새 명부와 AI 비교·현재 견적에 같은 합성 능력치를 공급한다. 다음 날 거래/임대/만료보다 전날의 운영 소속으로 훈련을 먼저 정산한다. 확정된 과거 계약 조건과 거래 영수증은 재산정하지 않는다.
- `CareerAppearanceStore` / `LeagueRelationalStore` / `CareerCompetitionRelationalStore`: 기존 검증 완료 경로의 실제 게임 증거를 소비한다. 등록 선수 자격과 이미 고정한 Series 프로필을 재작성하지 않는다.
- `CareerSeasonApplicationService`: 스토브 진입 시 임시 마감 상태를 보관하고, 아직 활성인 시즌의 마지막 날까지 정산한 뒤 최종 마감한다. 시즌 전환 후 과거 상태는 고정하며 선수 성장·숙련도·피로는 초기화하지 않는다.
- `CareerDevelopmentApiV1Controller` / `CareerApiV1RequestParser`: 조회와 엄격한 훈련 요청 형식, 기존 오류/UUID/revision 계약.
- `CareerTrainingPanel` / `careerDevelopment.contract.ts` / `careerApi.client.ts`: 현재 정수와 내부 진행도, 예약·피로·숙련도·최근/월간 요약, 관리 권한과 원본 요청 복구. Career 변경 시 기존 generation·요청 잠금 규칙을 따른다.

이주 전 시작 경기에는 성장 바인딩을 만들지 않아 소급 보상을 제외한다. 이주 시 원래 프로필×1,000과 중립 피로를 한 번 저장한다. 초기화된 저장에서 상태 또는 버전 표시가 유실되면 오류로 다루며 원래 능력치로 조용히 초기화하지 않는다. 기존 저장에 원래 존재하던 카탈로그 provenance 제약을 우회하지 않는다.

## 남은 범위

실제 수년 전체 일정·전체 선수 반복 경기·대규모 seed 진단은 실행하지 않았다. 표의 작은 모델은 결정적인 수치 관찰이며 장기 밸런스 검증이나 PA 도달 보장이 아니다. 해외/CL의 실행되지 않은 경기를 만들지 않고 훈련만 지급한다. 경기 내 피로 페널티·부상·노쇠·영구 감소·은퇴·숙련도 rust·자동 선발 교체는 V1에 없다. 챔피언 사용 보상은 실제 검증된 완료 증거에만 적용하며 KDA·승패·경기 길이 보너스가 없다.

## 검증 명령

JDK21 환경에서 backend의 집중 실행은 같은 `./gradlew test --console=plain --no-daemon`에 아래 선택자를 필요한 조합으로 붙였다. 상세 결과는 위 기록을 따른다.

- `--tests com.lolfm.career.CareerDevelopmentPolicyTest`
- `--tests com.lolfm.league.CareerModePersistenceTest.developmentMigrationPlansDailyMarketOrderAndFileRecovery`
- `--tests com.lolfm.league.CareerModePersistenceTest.paidTradeAndLoanUseCalendarAtomicityOriginalCommandsAndFileRecovery`
- `--tests com.lolfm.league.CareerModePersistenceTest.marketOffseasonTwoCyclesPreserveSealedRosterAndRepeatBudgetAndNegotiationEvents`
- `--tests com.lolfm.league.LeagueAutomatedSeriesRunnerProductionV9Test.selectedReserveRunsThroughActualLeagueAutoAndFrozenReceiptValidation`
- 기존 `LeagueRelationalPersistenceAndJobTest`의 파일 DB 이주 대표 사례.

프런트 명령은 `npm run career:verify`, `npm run build`다. build는170 modules를 처리했다. Playwright CLI의 실제 Chromium으로 위 대표 흐름 하나만 확인했다. 백엔드 전체는 선택자 없는 `./gradlew test --console=plain --no-daemon`을 실행하며 집중 결과와 별도로 집계한다.

## 전체 회귀와 후속 검증

이번 작업의 전체 backend 실행은 **1회**다. `./gradlew test --console=plain --no-daemon`은36분25초에 종료했으며 **266 suites / 총2,062건 / 통과2,059 / 실패1 / 오류0 / 기존 skip2**다. 전체 실행에서 새 정책·성장 저장·실제 Auto·Player 시작·이적/임대·시즌 이월을 포함한 나머지 테스트는 통과했다.

실패는 `CareerModePersistenceTest.v4CareerMigratesToFrozenV5CalendarWithoutBackdatingFoundationBinding`의 이주 개수 단언이다. V4 이후 V19까지15개 이주가 실행됐는데 V18/V19 반영 전 값13을 기대했다. 이를15로 교정했고 데이터·제품 로직·이주 파일·나머지 단언·test 제외 설정은 변경하지 않았다. 원본 전체 집계와 실패 내용은 `backend/build/reports/career-development/full-summary.json`, 실행 로그는 같은 폴더의 `full.log`에 보존했다.

기존 skip2는 `PlayerDraftLatencyProfilingV1DiagnosticTest.captureOfficialPlayerDraftInteractiveAndSimulationLatencyProfile`, `PlayerDraftPerformanceHardeningV1DiagnosticTest.capturePairedBackendProbe`이며 이번 작업에서 새로 제외한 테스트는 없다. 이주 개수의 단언만 바꾼 후속 수정은 실패한 파일 DB 이주 사례로 검증할 수 있다. 독립 하위 시스템의 공통 동작이나 Random이 바뀌지 않아 전체를 다시 실행할 검증 공백이 없다. 최종 코드에 대한 두 번째 전체 성공을 주장하지 않는다.

구현 중 현재 프로필 합성의 숙련도 키 정렬이 원래 배열 순서를 바꾸는 문제를 교정해 초기 디렉터리 전체 동일성을 지켰다. 버전 표시/상태행의 한쪽만 유실된 저장을 양방향으로 거절하도록 보완했다. 공통/역할 집중의 보조 그룹 배분이 지나치게 뒤로 밀리지 않도록 첫10개 블록부터7:3으로 교차했다. 이 변경들은 전체 실행 전에 집중 검증을 마쳤다.

후속 실행 `./gradlew test --tests com.lolfm.league.CareerModePersistenceTest.v4CareerMigratesToFrozenV5CalendarWithoutBackdatingFoundationBinding --console=plain --no-daemon`은 **1건 통과 / 실패·오류·skip0 / Gradle1분41초**다. 같은 보고 폴더의 `post-full-summary.json`과 `post-full-migration.log`에 보존했다. 미해결 실패는 없으며 전체 실행 횟수는1회다. 원본 전체 실패와 후속 집중 성공을 구분한다.

## 완료·정리

시작·최종 HEAD는 동일하며 원본 선수 리소스/PA, 글로벌 편집 데이터, 프롬프트·AGENTS·skills와 선행 사용자 파일(`prompts/`, `선수정보.zip`)은 변경하지 않았다. 현재 프로필 합성은 Career별 상태만 사용한다. 임시 Chromium 세션을 닫고 별도 backend18185 및 Vite5173 프로세스를 종료했다. 기존 사용자 DB 대신 `/tmp`의 별도 DB를 사용했다. `git diff --check`와 작업 diff를 확인했다.

한글 커밋 메시지 제안: `Career 성장·훈련·챔피언 숙련도·피로 기능 통합`
