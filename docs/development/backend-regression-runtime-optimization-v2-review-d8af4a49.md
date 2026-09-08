# d8af4a49 — 저장 시작 복구·회귀 비용 V2 결과 분석 및 리뷰

검토 커밋: `d8af4a49a83939810719b7967f795a0699e7ea8f`.
부모 커밋: `53632d224c148f916a9e25d3e7f283de3a43091a`.

판정: 원래 운영 명부 누락 NPE는 수정됐고, 전체 회귀의 실제 실행 시간이 보고된 기준보다 45.32% 감소했다. 검증 삭제로 시간을 줄인 변경은 발견하지 않았다. 다만 **운영 중 저장의 시즌 고정 선수 입력 누락을 시작 복구가 허용하는 경계 1건 [P2]**을 추가로 재현했다. 성능 목표 50%·20분 미달과 이 정확성 경계는 별도로 판단해야 한다.

**1. 리뷰 발견 사항 [P2] — 조회에서 DATA_MISSING인 저장에 시작 복구가 쓰기를 수행한다**

위치: `backend/src/main/java/com/lolfm/career/CareerSaveCompatibility.java:39`~44.

새 `recoverySupported()`는 운영 시장과 현재 선수단이 있는지 확인하지만, `career_season.roster_json`이 null이면 RowMapper가 반환한 false를 검사하지 않고 true를 반환한다. 조회용 `inspect()`는 같은 null 입력을 `SAVE_COMPATIBILITY_DATA_MISSING`으로 거절한다. 주석의 “초기 legacy는 고정 명부가 없을 수 있다” 예외가 이미 운영 중인 저장에도 적용되는 것이 문제다.

재현은 현재 커밋의 `CareerSaveCompatibility`를 별도 임시 출력에 컴파일하고, 전체 측정 때 생성된 동일 제품 코드의 클래스와 함께 격리 메모리 H2에서 실행했다. 새 정상 Career 2개를 만들고 하나의 현재 시즌 `roster_json/roster_hash`를 null로 만들었다. 두 저장의 성장 상태/초기화 marker를 제거하여 복구 대기를 준비했다. 실제 사용자 DB는 사용하지 않았다.

```text
BEFORE_STATUS=SUPPORTED
MISSING_GET=SAVE_COMPATIBILITY_DATA_MISSING
HAS_OPERATING_MARKET=true
HAS_OPERATING_ROSTER=true
RECOVERY_SUPPORTED=true
BROKEN_GROWTH_BEFORE=0
STARTUP=COMPLETED
BROKEN_GROWTH_AFTER=1
HEALTHY_GROWTH_AFTER=1
HEALTHY_GET=SUPPORTED
AFTER_GET=SAVE_COMPATIBILITY_DATA_MISSING
```

의미: 정상 저장은 복구되지만, 지원 불가로 분류된 문제 저장에도 성장 상태 생성이 수행된다. 이 재현에서 서버 시작 복구가 중단되지는 않았다. 원래 NPE가 그대로 남았다고 주장하는 것이 아니라, 새로 정의한 “알려진 자료 누락 저장을 건너뛴다”는 경계가 한 경우에 적용되지 않는다는 발견이다.

수정안: 시즌 고정 명부 쿼리의 행 수와 유효성 결과를 소비하고, 이미 운영 중인 저장은 고정 입력이 없으면 typed DATA_MISSING으로 분류한다. 아직 초기화되지 않은 정상 legacy에는 필요한 예외를 유지한다. 조회와 복구가 공유할 수 있는 읽기 전용 필수 자료 판정을 사용하되, 초기화 가능성이라는 두 경로의 차이는 명시한다. hash 손상 예외를 광범위하게 잡아 숨기면 안 된다.

검증은 기존 startup recovery 테스트에 이 경우를 확장하고, 정상 legacy 복구와 누락 저장 무변경을 함께 확인하는 범위면 충분하다. 현재 재현만으로 추가 전체 회귀나 별도 대형 복구 감사가 필요하지는 않다.

재현 코드·출력: `/tmp/career-opt-v2-review-d8af4a49/RecoveryBoundaryProbe.java`, 같은 폴더 `run.log`. 실제 Java 실행은 14.25초, exit 0이다. exit 0은 진단 실행의 정상 종료이며 제품 경계 통과라는 의미가 아니다. 파일 DB cold-start는 실행하지 않았다.

**2. 이번 커밋이 한 일**

| 변경 묶음 | 실제 효용 |
| --- | --- |
| 시작 복구의 지원 가능성 판정 | 지원 형식이지만 현재 운영 명부가 빠진 Career를 국제 정산 및 후속 복구에서 건너뛴다 |
| 테스트 날짜 준비 | API·등록·두 시즌 스토브의 직접 검증 전에 불필요하게 수개월을 진행하던 비용을 줄인다 |
| 성장 명부의 불변 투영 재사용 | 현재 정수 능력치·숙련도가 원본 투영과 같을 때 이미 검증된 불변 객체를 재사용한다 |
| Linux build 출력과 프로젝트 캐시 | 소스는 현재 checkout에서 읽고 class/resource 출력은 ext4에 두어 Windows 마운트의 반복 조회 비용을 줄인다 |

이 커밋은 새 Career 연속 진행 기능이나 매치엔진·밴픽 현실성 개선 구현이 아니다. 커밋의 24개 파일 중 5개는 앞서 별도로 작성된 매치엔진 리뷰 보고서·관찰 자료다. 이 문서들이 함께 커밋됐다는 이유로 관련 엔진 수정이 완료됐다고 해석하면 안 된다.

**3. 원래 NPE 수정은 근거가 있다**

보존된 `recovery-before` XML에서 신규 테스트의 실패는 `CareerRosterStore.saved(...).state()`의 실제 NullPointerException이었다. `recovery-after`에서는 같은 테스트와 두 legacy 관련 검사가 모두 통과했다. 전체 결과에도 해당 신규 사례가 포함되어 있다.

코드에서 국제 정산, dormant 대회 복구, 명부, 시장, 성장, lifecycle, CL, 해외 복구에 새 경계가 적용됐음을 확인했다. 알려진 compatibility 예외만 건너뛰고, directory hash 손상 등 무결성 실패는 계속 전파한다. 정상 저장을 진행할 수 있게 하면서 원래 소유권·선발을 카탈로그로 재생성하지 않는 방향은 적절하다.

직접 테스트는 실제 `CareerPersistenceStartupRecovery.recover()` 체인을 호출하고, 정상 저장의 성장 초기화와 다음 날 진행 및 문제 저장의 데이터 보존을 확인한다. 손상된 파일 DB로 서버 전체를 새로 띄우는 cold-start의 증거는 아니다. 위 P2 발견 때문에 모든 DATA_MISSING 저장의 격리를 완성했다고 확대해서는 안 된다.

**4. 시간·건수의 원본 대조**

| 지표 | 확인 결과 |
| --- | --- |
| 기준 전체 시간 | 2,653초 = 44분 13초 |
| 이번 전체 시간 | 1,450.63초 = 24분 10.63초 |
| 절감 | 1,202.37초 = 20분 2.37초 |
| 단축률 | 45.32% |
| 전체 XML | 274 suites / 2,164 tests / failures 0 / errors 0 / skipped 2 |
| 전체 실제 통과 수 | 2,162 |
| 전체 XML 시간 합계 | 2,762.439초 |
| 최종 후속 XML | 2 suites / 2 tests / failures 0 / errors 0 / skipped 0 |

리뷰에서 `/tmp/career-opt-v2-evidence/full/xml/`의 274개 XML을 직접 합산했고 보고서와 일치했다. 원본 로그는 `> Task :test`, `BUILD SUCCESSFUL in 24m 10s`, `real 1450.63`을 담고 있다. compile/resources만 UP-TO-DATE였으며 test 자체를 건너뛴 결과가 아니다.

과거 XML의 2,162개 test identity와 비교하면 새 복구 테스트·성장 투영 테스트 2개가 추가됐다. 기존 테스트의 실질적인 제거는 없었고, provenance hash를 이름에 포함하는 검증 1개의 이름만 새 source hash에 따라 달라졌다. skip 2개도 기존 Player Draft 성능 진단이었다. Gradle의 테스트 제외·worker·skip 정책 변경은 커밋에 없다.

50%에 해당하는 정확한 시간은 22분 6.5초이며, 프롬프트의 명시 목표 22분 6초를 기준으로 이번 측정은 124.63초 초과다. 권장 20분도 250.63초 초과했다. 목표 미달을 성공으로 포장하지 않은 보고는 정확하다.

단축률의 기준은 부모 작업 중 전체 실행이다. 같은 입력·부하로 동일 HEAD에서 수행한 A/B benchmark가 아니므로 Linux 출력, fixture 축소, 객체 재사용 각각의 순수 기여율을 분리할 수 없다. 전체 실행은 앞선 집중 검증의 유효한 컴파일 출력을 재사용했다. 새 출력 경로에서 처음 실행할 때는 준비 시간이 추가된다.

**5. 검증 축소의 성격**

테스트의 최종 계약을 단순히 삭제한 변경과 준비 비용을 줄인 변경을 구분했다.

- API 검사는 실제 생성·첫 날짜 진행·UUID replay/conflict·revision·격리·경기 당일 API를 유지한다. 먼 날짜까지 도달하는 반복 요청은 준비용 helper로 교체됐다.
- 해외 등록 G1은 실제 결원 처리·시장 제안·명부 복구·국제 등록을 유지한다. 이전 수개월 운영은 직접 검증 대상에서 제외된 준비 구간이다.
- 두 시즌 스토브는 실제 두 번의 rollover, 계약·선발, 연도별 56개 구단 재정 승인, 성장 이월과 과거 snapshot·receipt 검사를 유지한다. 생략한 기간의 AI·성장이 실행된 것은 아니다.
- 파일 DB A→B의 실제 닫기/재열기, 저장 보존·새 저장 적용, 다음 경기 handoff·이월·손상 거절은 유지됐다.

`CareerOperatingDateFixture`는 테스트에서만 사용하는 120행 helper다. 운영 명령이 없는 첫 주의 날짜 이동과, 이월 후 미처리 계약 경계를 건너뛰지 않는 준비를 구분한다. 일반 게임의 날짜 건너뛰기 API로 재사용해서는 안 된다. 새 시장/성장 상태가 추가될 때 이 helper의 적용 범위를 함께 점검해야 하는 유지보수 비용은 생긴다.

**6. 성장 투영 재사용의 타당성**

`CareerDevelopmentEngine.directory()`의 새 빠른 경로는 선수 identity, nickname·position, 정수 능력치의 항목 수·값, 숙련도 항목 수·값을 비교한다. 값이 같으면 원래 immutable Definition을 반환하고, 정수 경계 또는 새로운 숙련도가 생기면 기존 구성·검증 경로를 사용한다.

`CompetitionRosterSnapshot.Starter`는 Map/List를 방어적으로 복사한다. 이번 변경은 전역 mutable 캐시가 아니며 상태의 소수 진행도를 삭제하지도 않는다. 새 테스트는 소수 증가 시 투영 동일성, 정수 증가와 새 챔피언 숙련도 반영, 직렬화 재시작과 원본 불변성을 확인한다. 이 변경에서 구체적인 성장 누락 회귀는 발견하지 않았다.

실제 게임 조회에도 재사용 효과가 있을 수 있지만 HTTP 지연을 측정한 자료가 없으므로 플레이가 45% 빨라졌다고 말할 수 없다. 이번 정량 성과는 테스트 실행 비용이다.

**7. 전체 이후 보정과 추가 전체를 생략한 판단**

전체 통과 뒤 API/G1 날짜 이동이 Jackson 날짜 배열 때문에 실제로 적용되지 않았다는 준비 문제가 확인됐다. 최종 helper는 날짜를 정규화해 이동하고 월별 요약을 재구성하며 목표 시계 일치도 검사한다.

최종 직접 호출자 2개의 XML은 모두 통과한다. API는 49.609초, G1은 77.914초다. 이 두 메서드 합계와 집중 실행의 wall time 99.63초는 서로 다른 지표다.

제품 코드가 전체 이후 바뀌지 않고 수정 fixture의 직접 호출자가 두 곳으로 한정돼 있어, 해당 두 검사의 후속 성공으로 마무리한 판단은 적절하다. 최종 테스트 트리의 전체 시간이 더 짧아졌을 가능성은 있지만 측정하지 않았으므로 새로운 전체 시간·50% 달성을 계산해서 제시하면 안 된다.

추가로 리뷰에서 발견한 P2는 이 원본 전체/후속 테스트에서 미해결로 관측된 테스트 실패가 아니라, 별도 누락 입력 조건에서 확인한 검증 공백이다.

**8. 실무적 효용과 다음 작업 판단**

전체 1회당 약 20분의 절감은 체감할 만한 개선이다. 같은 실행 조건을 가정하면 5회에서 약 100분의 대기 시간이 줄어든다. 보고된 집중+전체 실행 비용은 약 58분 55초로, 이 실행 비용만 비교하면 약 3회 전체 실행의 절감에 해당한다. 구현·분석 시간은 이 계산에 포함하지 않는다.

큰 잔여 비용은 Persistence 638.762초, OverseasExecution 260.884초, 실제 AutomatedSeriesRunnerProductionV9 207.754초다. 병렬 클래스 시간의 합을 wall time에서 그대로 빼거나, 후속 두 테스트의 절감을 전체 단축률에 더할 수는 없다.

다음에는 P2 복구 판정 경계를 작은 선행 수정으로 보완한 뒤 예정된 매치엔진·밴픽 개선으로 진행하는 것이 적절하다. 20분 목표를 달성하기 위한 추가 최적화를 다시 큰 선행 작업으로 만들 필요는 없다. 이후에도 빠른 스크립트를 실제로 선택해야 Linux 출력 효과를 활용할 수 있다.

**9. 산출물과 이번 리뷰 범위**

- 구현·측정 보고서: [backend-regression-runtime-optimization-v2.md](backend-regression-runtime-optimization-v2.md)
- 실행 방법: [testing.md](testing.md)
- 실행 스크립트: [test-linux.sh](../../backend/scripts/test-linux.sh)
- 복구 판정: [CareerSaveCompatibility.java](../../backend/src/main/java/com/lolfm/career/CareerSaveCompatibility.java)
- 성장 투영: [CareerDevelopmentEngine.java](../../backend/src/main/java/com/lolfm/career/CareerDevelopmentEngine.java)
- 테스트 준비: [CareerOperatingDateFixture.java](../../backend/src/test/java/com/lolfm/career/CareerOperatingDateFixture.java)

이번 리뷰는 커밋 diff·소스·보존된 원본 검증 결과 확인과 격리된 14.25초 경계 재현을 수행했다. 전체 Gradle 회귀·브라우저·실제 경기 진단을 다시 실행하지 않았다. `bash -n backend/scripts/test-linux.sh`와 커밋 범위 `git diff --check`는 통과했다. 제품 코드는 수정하지 않았고, 기존 작업 트리의 리뷰 CSV 변경·prompts/·원본 ZIP은 보존했다.
