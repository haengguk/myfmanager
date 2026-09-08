# 매치엔진 현실성·성능 기반 Draft V1 구현 기록

시작 HEAD: `d8af4a49a83939810719b7967f795a0699e7ea8f`, main. 선행 최적화가 완료된 작업 트리에서 진행했다. 기존 리뷰 matches.csv 수정, prompts/, 선수정보.zip과 별도 V2 리뷰 파일은 보존했다. A–F는 새 경기의 실제 실행 경로에 연결했으며 검증 결과와 한계는 아래에 구분한다. 통계 승인 보고서는 아니다.

## 버전 적용 설계

| 입력 경계 | 적용할 정책 |
| --- | --- |
| 새 Career / 기존 Career의 아직 시작하지 않은 Series | 현재 적격 명부를 고정하고 REALISM_V1 runtime + ABILITY_BASED_V1 Draft 선택 |
| 이미 시작한 Series / Player Draft | 저장된 정책과 입력을 그대로 사용. legacy 형식의 정책 부재는 검증된 V9만 명시적으로 복구 |
| 완료 input/checkpoint/receipt | 원래 hash와 정책 유지. 새 기본값으로 재작성하지 않음 |
| historical baseline / candidate | 기존 명시적 profile과 Draft policy 유지, 과거 artifact 재생성 없음 |

새 runtime은 공통 MatchEngine boundary에서 닫힌 정책 레지스트리로 선택한다. 저수준 옵션의 legacy 생성자는 V9 의미를 유지한다. 새 정책은 개막 접촉·수비 위치·공유 경제·기존 정글 템포·상체 오브젝트를 함께 결속하며 개별 candidate flag를 API에 노출하지 않는다. Draft scoring/selection도 구버전을 보존한다. 저장 검증은 현재 기본값과의 동등 비교 대신 저장된 버전의 정확한 정책으로 검증하도록 연결한다.

구현 순서: A/B 재현 및 경계 수정 → C/D/E 운영 → F 성능 평가 → 저장·실제 실행 연결 → 집중/75경기 개발 진단 → tempo 수정과 같은 16경기 확인 → 프런트/계획된 전체 1회. 선행 최적화의 전체는 재실행하지 않았다. 이번 전체는 클라이언트 연결 해제로 중단되어 완료 증거를 보존하고 미완료 클래스 및 실패 원인의 영향 범위만 이어서 검증했다.

| 버전 경계 | 값 |
| --- | --- |
| 새 engine policy | `MATCH_ENGINE_REALISM_ABILITY_V1` |
| policy hash | `1e9672acf5055e2d5c2175b2ccddd7dca907e991de16b495cd42fb6a3db058f6` |
| runtime / rules | `PRODUCTION_REALISM_V1` / `MATCH_SIMULATOR_REALISM_RULES_V1` |
| configuration hash | `eb241ac2fe57095e1410e39120d276f6ed39ef4ff25f2ea116dcf51953baf93f` |
| Draft scoring / selection | `DRAFT_ABILITY_SCORING_V1` / `AUTO_DRAFT_ABILITY_V1` |
| Player control | `PLAYER_CONTROLLED_DRAFT_ABILITY_V1` |
| engine implementation | `MATCH_SIMULATOR_ENGINE_IMPLEMENTATION_V9` 유지; 새 의미는 별도 runtime/rules/hash로 결속 |

구 `PRODUCTION_MATCHUP_COMPOSITION_V1`의 configuration hash `caaf76274dc148040b0a95eae1ed5181790b2fc840f45af9b109ea7951c1fd5d`와 기존 선택 정책/공식 artifact를 보존했다. 새 정책의 정확한 전체 값은 [policy.json](match-engine-realism-and-ability-based-draft-v1-results/policy.json)에 있다.

## 구현된 동작과 경계

- A: generic skirmish는 첫 라인 접촉(MID 60초, 사이드 70초)과 해당 라인의 실제 참가자를 검사한다. TOP/MID/바텀 인원이 무관한 개막 전투에 합류하지 않는다. 부적격/동일 tick은 Random을 소비하지 않으며 하위 전투 기회를 막지 않는다. 모든 시스템의 킬을 일정 시각까지 금지하는 정책은 아니다.
- B: 본진 접근 공성을 감지하면 선수별 RETURNING_TO_BASE → DEFENDING_BASE를 거친다. 기본 근거리 10초, 공성 중 원거리 30초, 부활 직후 본진은 즉시 수비한다. 도착한 수비진과 실제 공성 참가자만 비교한다. 공유 progression/item/champion evaluator의 현재 전력, 웨이브, 넥서스 잔여 HP와 예상 타격을 사용한다. 바론은 피해 보정이며 재평가 면제권이 아니다. 실제 수비 교전은 중앙 major-combat 흐름의 첫 슬롯과 공통 킬 보상을 사용한다. 전멸을 승리 조건으로 강제하지 않는다.
- C: 라인별 제한된 웨이브 CS와 살아서 현장에 있는 선수의 XP를 분리했다. 바텀 동시 현장 체류는 공유 XP, 서포터 이탈 시 ADC 단독 XP다. 기본 활동·사망/라인 복귀·로밍·공성·본진 복귀·상체 오브젝트 복귀가 수급 기회를 결정한다. CS 차단 중이라도 실제 라인에 있으면 XP가 가능하며, 부활 후 이동 경계는 별도로 보존한다. SUPPORT CS 0, PASSIVE gold, TOP 역할 퀘스트 및 최대 20레벨 정책은 유지한다.
- D: 기존 ECONOMY_AND_GANK_TEMPO_V1 경로에 6개 자기 진영 캠프 상태를 연결했다. 챔피언 clear profile × 실제 JRM 효율로 작업량을 채워 캠프를 소비하고 재생성한다. 획득 때만 CS 4·XP 180·기존 CS당 골드를 지급하고, tempo는 실제 클리어 작업 tick에 누적한다. 기존 tick XP/CS를 중복 지급하지 않는다. 이동·갱·사망 중 작업은 보충하지 않는다.
- E: 유충 3개에 각 match ID와 부분 팀 소유를 부여하고, 전령의 획득자·눈 보유·소환·만료·후속 돌진을 상태로 소유한다. 상체 참가자와 압박, 용의 soul point/바텀 압박을 비교한다. 기존 objective fight/secure와 공통 StructureResolver를 사용한다. 보호된 포탑, 위조 action ID, 동일 돌진의 재호출은 보상을 만들지 않는다. structured event/snapshot을 타임라인·스코어보드·결과 카운터가 소비한다.
- F: GEOMETRIC_V2 상성과 authored composition은 기존 기능이다. 여기에 실경기 ChampionPowerProfileEvaluator, 현재 선발 선수의 포지션별 12개 능력치와 숙련도, 실제 jungle clear/JRM 식을 추가했다. CA·PA·미래 경기 난수·Player 미래 클릭은 읽지 않는다. 상대 모델은 상대의 비공개 seeded 전략을 읽지 않는다. AI 선택 근거만 짧게 UI에 표시한다.

| 범위 | 이전 원인과 체감 변화 | 주요 구현 파일 |
| --- | --- | --- |
| A | 전역 생존·참가 가능 여부만으로 개막 추첨에 도달 → 라인 접촉과 현장 참가자가 있어야 일반 교전 시작 | `TeamfightResolver`, `CombatRealismRuleConfig` |
| B | 전역 생존자와 바론/commit 유예가 실제 복귀보다 우선 → 이동 후 도착한 수비진·부활·웨이브를 매번 재평가 | `BaseDefenseResolver`, `BaseSiegeState`, `StructureResolver`, `PlayerActivityState` |
| C | 기본 활동 상태면 ADC/SUPPORT에 계속 같은 XP → 동행 공유·단독 수급·이탈 기회와 유한 CS 풀 | `LaneResourceState`, `LaneResourceResolver`, `ProgressionEconomyResolver`, `PositionEconomyResolver` |
| D | 기존 candidate가 새 기본 경기에서 비활성 → 실제 캠프 소비/재생성과 갱 작업 템포가 성장·활동 비용을 결정 | `JungleCampState`, `JungleEconomyResolver`, `JungleTempoState` |
| E | 용/바론/장로만 실행 → 유충 분할 획득과 전령 소환·돌진이 경기·구조물·화면에 반영 | `UpperObjectiveState`, `UpperObjectiveResolver`, `UpperObjectiveRuleConfig`, `SnapshotFactory` |
| F | 메타/상성/숙련도 중심의 고정 선호 → 현재 선수·성장 곡선·대체재·실제 순서에 따른 평가와 설명 | `DraftAbilityEvaluator`, `PickEvaluator`, `BanEvaluator`, `PreDraftPlanner`, `DraftScoringPolicy` |

경기 파일은 `backend/src/main/java/com/lolfm/simulator/`, Draft 파일은 `backend/src/main/java/com/lolfm/draft/`에 있다. 정책과 저장 연결은 `application/MatchEngineV1Policy`, `SeriesAggregate`, `PlayerDraftApiV1Service` 및 `career/CareerCompetitionSeriesBindingV1`이 소유한다.

### 규칙 근거와 모델 값

목표는 프로젝트 작성 자료의 2026-08-18 시점과 맞춘 일반 협곡 26.16이다. Swiftplay의 오브젝트 제거 규칙을 일반 협곡에 적용하지 않았다.

| 출처 | 채택/확인 |
| --- | --- |
| [25.09](https://www.leagueoflegends.com/en-us/news/game-updates/patch-25-09-notes/) | 유충 8분·1회 3마리, 전령 15분, 3중첩 효과 및 후속 돌진 감소 방향 |
| [26.1](https://www.leagueoflegends.com/en-us/news/game-updates/patch-26-1-notes/) | 유충 개체당 local gold 30·XP 65, 전령 gold 100·XP 240·첫 돌진 3000, 캠프 출현 55초/일부 67초, 14분 이후 웨이브 가속, TOP 역할 퀘스트 |
| [26.6](https://www.leagueoflegends.com/en-us/news/game-updates/league-of-legends-patch-26-6-notes/) | 전령 행동의 과잉성장 발동 수정. 이번 모델에 룬별 발동은 없음 |
| [26.12](https://www.leagueoflegends.com/en-us/news/game-updates/league-of-legends-patch-26-12-notes/) | 전령 돌진이 조건에 따라 마법 피해로 처리되던 버그의 고정 피해 수정 확인 |
| [26.16](https://www.leagueoflegends.com/en-us/news/game-updates/league-of-legends-patch-26-16-notes/) | 전령 운전 시 포탑 대상 Ambessa 흡혈 버그 수정. 출현·획득 보상 변경은 없음 |

유충 14:45/전령 19:45 종료는 다음 대형 오브젝트 출현 전 15초 간격을 둔 **이 모델의 고정 경계**다. 확인한 공식 노트만으로 정확한 despawn 예외까지 확정한 값이라고 주장하지 않는다. 몬스터 교전 중 소멸 유예, 전령 눈 지상 드롭/회수, 실제 좌표·체력·룬·일반 공격은 구현하지 않는다. 전령 눈 240초, 소환 후 90초/최대 3돌진/후속 0.6배, 유충 10초당 추가 구조물 피해 32/96/192 × 공격 인원, 각종 이동/획득 시간은 V1 추상화다. 공식 수치와 이 추상화는 UpperObjectiveRuleConfig에서 분리해 해석한다.

캠프는 BLUE/GROMP/WOLVES/RAPTORS/RED/KRUGS의 고정 경로이며 바위게·침입·작은 몬스터별 보상은 없다. 30초 작업량, 일반 135초/버프 300초 재생성을 사용한다. 완료 tick의 잉여 작업은 넘기지 않아 clear 성능이 10초 단위로 양자화된다. 라인 XP는 실제 미니언 개체별 죽음 대신 현장 인원에게 분배하는 10초 기회 풀이고, CS는 30/25/20초 웨이브 풀을 소비한다. 정확한 LoL 전투/경제 재현이나 전 리그 밸런스 검증을 의미하지 않는다.

### 성능 기반 Draft 계산

대표 상황은 6레벨/COMPONENT/LANE_COMBAT, 11레벨/FIRST_CORE/전략별 전투, 16레벨/THIRD_CORE/TEAMFIGHT다. 실경기와 동일한 clamped champion power를 `10 + 5 × power`로 스케일한다. 초/중/후반 가중치는 DIVE 45/35/20%, PICK_CONTROL 35/40/25%, POKE_SIEGE 25/45/30%, FRONT_TO_BACK 20/35/45%다. 같은 합계의 성장 곡선도 전략에 따라 달리 평가된다.

선수 적합도는 12개 능력치를 `1 + 관련 authored capability / 20`으로 가중 평균한 값의 65% + 해당 champion-role 숙련도의 35%다. mechanics/combat/trading은 burst, decision/map/consistency/tracking/vision은 zone, positioning/farming/JRM은 sustained, wave/lane support는 wave clear, lane/side는 side pressure, priority/pathing/intervention/rotation은 pick, objective/area setup은 objective damage, engage/ally protection은 각각 engage/peel에 대응한다. 이는 전망용 적합도이며 미래 컨디션 실현값을 예지하지 않는다.

픽 가중치: 초중후반 전력 각 1, 선수 적합도 .8, 상성 .55, 조합 기여 .65, 상대 조합 대응 .55, flex .25, denial .15, 미래 완성 .3, jungle clear .5, 자원 위험 -.5. 상성은 전후 차이 ±5, 조합은 픽 수 차이를 보정한 한 명의 기여 ±10, 대응은 중립 10에서 중심화, flex는 0~4, 미래 완성은 0~10이다. 실전 상황 power 보정은 전력 항목에 한 번, authored 조합은 부족한 역할/구조적 기여에 사용한다.

밴의 주 항목은 `max(0, 상대 후보 가치 - 합법 최선 대체 가치) × 상대 선점 가능성`이다. 선점 가능성은 실제 남은 pick 순서 거리와 가치 차이로 .05~.95 안에서 구한다. 여기에 위협 .2, flex .15, pool 압축 .15, 보호 .3, 우리 기회비용 -.8을 더한다. 우리 기회비용도 실제 다음 pick 거리와 상대 선점 시점을 반영한다. 대체재가 좋아지면 동일 후보의 밴 차익이 줄어드는 집중 검사를 추가했다.

외부 메타의 직접 최대 보정은 .25점이며 0으로 끌 수 있다. 후보 pruning과 초기 플랜도 같은 작은 스케일을 사용하고 상대 가치/우리 기회비용에 고정 메타 점수를 다시 넣지 않는다. 기존 메타 파일은 보존한다. 초기 전략에는 팀·명부·시드에 결속된 archetype별 최대 2점 선호를 사용하며 매 턴 재추첨하지 않는다. 공개 상대 픽에 따라 재계획한다. 후보 12 + 역할 완성용 보완 4, 깊이 3, beam 2의 제한 탐색과 경기별 DraftComputationContext 재사용을 사용한다. Monte Carlo 경기를 후보별로 돌리지 않는다.

### 저장 호환

현재 기본 정책은 `MATCH_ENGINE_REALISM_ABILITY_V1`, runtime `PRODUCTION_REALISM_V1`, rules `MATCH_SIMULATOR_REALISM_RULES_V1`, 선택 `AUTO_DRAFT_ABILITY_V1`이다. 저수준 생성자 및 명시적 historical profile은 구동작을 유지한다. 새 Series는 정책 ID를 저장하고 child Draft와 receipt가 같은 정책인지 검증한다. 구 Series/Progress에 없는 신규 필드는 legacy를 뜻한다. 국제대회 binding에도 optional policy ID를 추가하고 구 canonical은 그대로 유지한다.

기존 season snapshot의 production runtime identity는 당시 V9 호환 앵커를 유지한다. 실제 새 경기 정책은 Series/Draft/input/receipt가 별도로 결속한다. 이를 최신 정책으로 무조건 바꾸면 기존 모든 시즌의 frozen snapshot 검증이 실패하므로 앵커의 역사적 의미를 보존했다. 현재 정책 allowlist를 끄거나 원래 receipt hash를 다시 계산하지 않는다.

입력의 champion-role map은 기존 record 형태 JSON key를 읽는 deserializer만 추가했다. 구 JSON의 직렬화 모양을 바꾸지 않는다. 새 realism/evaluation 필드는 legacy에서 생략한다.

## 실제 경기 진단 결과

기존 리뷰 64경기를 재실행하지 않고 원시 결과를 baseline으로 재사용했다. 현재 Career 새 명부의 GEN–T1, HLE–GEN, KT–DK, T1–KRX 양 진영 × 공통 8개 개발 시드에서 새 경로 64경기, 두 대진의 5세트 깊이 피어리스 10경기, 입력 JSON 복원 재현 1경기를 실행했다. 5세트 실험은 실제 3승 종료 BO5 완주가 아니다. 이후 캠프 작업/tempo 연결 결함을 발견해 수정했고, 첫 2개 공통 시드의 같은 대진·진영 16경기만 갱신했다. 진단 실제 실행은 총 **91경기**다. 공식 holdout·이전 64경기 재생성·통계 승격은 하지 않았다.

64경기 열은 tempo 교정 **이전** 실측이다. 최종 엔진의 64경기 결과로 부르지 않는다. 마지막 16경기는 동일 입력끼리 비교하며 Draft 결과는 tempo 교정 전후 동일하다. 최종 Draft의 전략·반복 분석은 아래 64경기 그대로 유효하다.

| 지표 | 이전 64 | 신규 64·tempo 교정 전 | 이전 같은 16 | 최종 같은 16 |
| --- | ---: | ---: | ---: | ---: |
| 평균 경기 시간 | 33:37.5 | 32:53.1 | 30:16.3 | 34:30.6 |
| 평균 총 킬 | 22.97 | 23.62 | 20.94 | 27.00 |
| 첫 킬 중앙값 | 0:50 | 3:05 | 0:35 | 2:00 |
| 1분 전 첫 킬 경기 | 32 | 0 | 16 | 0 |
| 2분 전 첫 킬 경기 | 48 | 16 | 16 | 8 |
| 패배 팀 5명 생존 종료 | 34 | 23 | 11 | 3 |
| 타임아웃 | 0 | 0 | 0 | 0 |
| 최장 경기 | 50:40 | 70:40 | 41:20 | 48:20 |
| 정글 갱 실제 시도 | 129 | 0 | 28 | 12 |
| 카운터 갱 실제 시도 | 22 | 0 | 2 | 3 |
| 유충 개체 획득 | 0 | 184 | 0 | 45 |
| 전령 획득 | 0 | 61 | 0 | 15 |
| 전령 돌진 | 0 | 145 | 0 | 34 |
| 신규 본진 수비 시도 | 0 | 184 | 0 | 48 |
| 본진 복귀 이벤트 | 0 | 2396 | 0 | 547 |

수비 시도는 BASE_DEFENSE 요약, 실제 킬은 KILL.combatSource로 따로 센다. 이전 엔진에도 BASE_DEFENSE 킬 출처가 있었으므로 구버전 요약 0을 수비 킬 0으로 해석하지 않는다. 복귀 이벤트 수는 고유 선수 수가 아니며 위협 해소·재발에 따른 재평가가 포함된다. 최종 16경기의 갱 12/카운터 갱 3은 실제 캠프 작업·복귀 비용 아래 발생한 시도이며, baseline의 빈도에 맞춘 강제 발동은 없다.

**진단에서 고친 결함:** 캠프 완료 시점에만 tempo credit을 주면 연속 작업의 마지막 관측 시각이 끊겨 갱 준비가 계속 초기화됐다(75경기 묶음에서 일반 갱 0). 실제 eligible 작업 tick마다 제한된 작업 credit을 기록하고, 보상은 완료 시점만 지급하도록 분리했다. 23개 직접 검사와 같은 16경기 비교로 확인했다. 지표를 맞추려고 확률이나 캠프 보상을 조정하지 않았다.

### 15분 자원 비교

동일 16경기, 포지션별 양 팀 32명 평균. 레벨/CS/골드 순서다.

| 포지션 | 이전 | 최종 |
| --- | ---: | ---: |
| TOP | 9.31 / 111.72 / 5209 | 9.28 / 105.97 / 5262 |
| JUNGLE | 8.34 / 85.44 / 4678 | 7.94 / 94.38 / 4547 |
| MID | 9.06 / 117.78 / 4978 | 9.16 / 114.25 / 5393 |
| ADC | 8.00 / 130.38 / 5287 | 8.09 / 128.22 / 5319 |
| SUPPORT | 8.09 / 0.00 / 3462 | 7.81 / 0.00 / 3357 |

30분에 아직 진행 중인 표본은 이전 7경기·최종 13경기로 다르다. ADC/SUPPORT 평균 레벨은 이전 11.93/12.00, 최종 12.23/10.92다. 동일한 활동 상태에서 계속 같은 XP를 주던 경계를 해소했지만, 종료 표본 차이가 있어 그 차이를 순수 XP 정책의 인과 효과로 단정하지 않는다. SUPPORT CS는 전 구간 0이다. 최종 정글 10분 평균 레벨 6.03·CS 61.88은 정량 밸런스 추가 확인이 필요한 값이다. 실전 비교는 이전 리뷰가 기록한 LCK 2026 Rounds 1–2 204경기의 31:29·29킬을 방향성 참고로만 재사용했다. 이번 모델의 2026-08-18 자료/26.16 일반 협곡, 4개 대진과 양 진영, 현재 새 Career 선발, 첫 세트/5세트 깊이 조건과 실제 R1–R2의 패치·대진·선발·세트 깊이는 다르다. 정확히 패치를 맞춘 실전 비교가 아니며 해당 평균을 목표값으로 튜닝하지 않았다.

### 대표 경기 전개

**GEN–T1, seed 91008011, 최종 경로**

- 03:00 Doran(갈리오)이 TOP에서 Kiin(바루스)을 처치. 과거 같은 개발 시드의 00:30 Oner→Canyon·Doran 합류와 다르게 실제 라인 접촉 참가자끼리 발생했다. Draft도 새 정책이므로 단일 규칙만의 통제 실험은 아니다.
- 08:20/08:50/09:10 T1이 유충을 한 마리씩 획득. 각 개체 30골드·65XP이며 Oner와 현장 TOP/JG/MID가 연결된다.
- 15:30 Canyon이 전령 획득, 16:00 미드 외곽 3000 피해 → 16:10 남은 HP 1149.46 피해 → 16:20 미드 2차 1080 피해. 기록은 명목 피해가 아니라 공통 구조물 경로에서 실제 적용한 피해다.
- 25:00 GEN 바론. 25:10 T1 수비 복귀, 25:20 도착한 5명과 공격 5명이 수비 교전(공격 전력 114.47, 수비 113.40). 바론 보유만으로 수비 재평가를 생략하지 않았다.
- 27:10 공격 4명/수비 2명, 전력 94.11/45.94의 수비 시도는 NO_KILL. GEN은 유효 공성으로 넥서스를 파괴한다. T1 두 명이 생존했고 전멸을 강제하지 않았다. 27:10, 총 21킬.

**GEN–T1, seed 91008023, 최종 경로**

- 01:00 Faker가 Chovy를 MID SKIRMISH로 처치. 개막 부적격만 막았으며 2분 전 킬을 일괄 금지하지 않았다는 실제 사례다.
- 08:20 T1 유충 1개, 11:30/12:30 GEN이 나머지 2개 획득. 부분 획득과 팀별 누적이 실제로 발생했다.
- 15:40 Canyon 전령 획득. 16:10/16:20/16:30 미드 외곽·2차에 3000/360.91/1080 피해.
- 23:50 T1 복귀 시작, 24:00 도착. 26:50 GEN 바론 이후에도 27:20·28:00 공격 5/수비 5의 NO_KILL 수비 시도를 거친다(115.11/112.52). 바론 획득 직후 자동 종료되지 않는다.
- 33:30 GEN이 후속 전투 뒤 다시 바론을 확보하고 34:40 넥서스 승리, 총 25킬. 유효 전력과 후속 기회로 마무리한다.

### 밴픽 반복과 실제 판단

| 지표 | 이전 64 | 신규 64 |
| --- | ---: | ---: |
| draftIdentity 종류 | 64 | 64 |
| 챔피언+진영+최종 포지션 조합 | 57 | 64 |
| 바루스 픽 경기 | 64 | 47 |
| 바이 밴 경기 | 64 | 55 |
| 카밀 밴 경기 | 64 | 52 |
| PICK 후보 1명인 턴 / 640 | 219 | 16 |
| PICK 최고 순위 선택 / 640 | 535 | 447 |
| 최초 플랜 POKE / PICK / DIVE / FRONT_TO_BACK | 128 / 0 / 0 / 0 | 67 / 32 / 21 / 8 |
| 최종 플랜 POKE / PICK / DIVE / FRONT_TO_BACK | 37 / 90 / 0 / 1 | 46 / 41 / 20 / 21 |

PICK 후보 크기는 3명 597턴·2명 27턴·1명 16턴이다. 전체 20턴 통계와 PICK만의 통계를 혼용하지 않았다. 초기 플랜은 양 팀의 첫 결정 기록을 기준으로 비교했다. 신규 픽 상위는 바루스 47, 케이틀린 33, 리산드라 32, 나르 31, 오리아나 30이다. 바이/카밀 밴 편중은 여전히 남으며, authored 위협·대체재·제한 탐색과 작은 메타 보정의 결과다. 특정 챔피언 전용 금지·픽률 조정은 없다.

실제 동일 바이 평가 예: GEN–T1 seed 91008011의 RED 13턴은 상대 가치 25.888/대체재 26.809, 선점 확률 .328, 자기 기회비용 0, 즉시 밴 점수 .729였다. 같은 대진 seed 91008023의 BLUE 5턴은 25.607/26.228, 확률 .358, 자기 기회비용 1.074, 즉시 점수 -.017이었다. 두 경우 대체재가 더 좋아 순수 제거 차익은 0이고, 위협/기회비용과 후속 탐색이 판단을 결정했다. 메타는 두 경우 모두 .2375로 같았다. 이를 “바이가 언제나 최선 밴”이라고 해석하지 않는다. 수치들은 decision.componentBreakdown에 저장된다.

개별 power curve만 변경하는 검사, 선수 능력치/숙련도 변경 검사, 전략별 시간 가중 차이, 더 좋은 대체재가 밴 차익을 줄이는 검사, 메타 0·비공개 상대 seeded 전략 차단 검사가 통과했다. 모든 실제 Draft는 합법 역할과 미래 완성·피어리스 검사를 통과했다. 그러나 다양성 증가만으로 전투 조합 품질이 기존보다 우수하다고 증명하지 않는다. 새 가중치에서 최적화한 점수는 구 점수와 직접 비교할 수 없고, 상대 경기력/전략별 균형은 별도 통계 승격 없이 남아 있다.

### 5세트 피어리스 대체 조합

챔피언 ID를 TOP/JG/MID/ADC/SUPPORT 순으로 기록한다. 각 대진 50개 픽이 중복 없이 사용되고 제외 수는 0→10→20→30→40이다. 세트 번호마다 진영을 바꾼 이력 실험이다.

| 대진 | 깊이 | BLUE | RED |
| --- | ---: | --- | --- |
| T1–GEN | 1 | varus/zyra/lux/kalista/neeko | poppy/xin-zhao/annie/ashe/morgana |
| GEN–T1 | 2 | gnar/maokai/lissandra/caitlyn/alistar | sion/fiddlesticks/mel/jhin/seraphine |
| T1–GEN | 3 | kennen/wukong/orianna/taliyah/pyke | rumble/jarvan-iv/azir/sivir/galio |
| GEN–T1 | 4 | anivia/kindred/zoe/xayah/heimerdinger | gragas/vi/ahri/corki/leona |
| T1–GEN | 5 | ksante/skarner/sylas/yunara/thresh | jayce/olaf/cassiopeia/ziggs/braum |
| DK–KT | 1 | gnar/zyra/zoe/caitlyn/seraphine | zaahen/jarvan-iv/orianna/varus/rakan |
| KT–DK | 2 | illaoi/nocturne/annie/taliyah/thresh | rumble/wukong/lissandra/jhin/alistar |
| DK–KT | 3 | sion/morgana/galio/xayah/nautilus | quinn/brand/hwei/ashe/neeko |
| KT–DK | 4 | ksante/skarner/azir/yunara/poppy | kennen/jayce/twisted-fate/corki/lux |
| DK–KT | 5 | volibear/zac/viktor/kalista/senna | sett/qiyana/syndra/mel/karma |

실제 바루스 픽 비교도 선수 입력 차이를 보인다. seed 91008011의 BLUE 7턴에서 GEN과 T1은 모두 POKE_SIEGE로 초/중/후반 전력 2.90/5.58/3.39, 메타 .25였지만 선수 적합도는 GEN 19.166, T1 18.430이고 전체 즉시 점수는 35.642/35.142였다. 같은 챔피언의 고정 순위를 추첨하는 구조가 아니며, 이 점수에는 해당 턴의 조합 기여와 denial도 포함된다. CA·PA나 원본 명부를 수정한 결과가 아니다.

## 계산 비용과 남은 모델 한계

대표 GEN–T1 seed 91008011의 Draft-only 실행은 6.115초, 별도의 동일 범위 제한 탐색 측정은 6.180초였다. 20턴 root 후보는 총 240개였다. 역할 배정 요청 333,468건 중 307,259건(92.14%), 포지션 요청 785,045건 중 757,414건(96.48%)을 지역 캐시에서 재사용했다. 후보 수가 적어도 역할 완성·조합 전망의 반복 평가가 주요 비용이다. peak cache entry 183,567은 장기 전역 캐시가 아니라 해당 DraftComputationContext의 관측치다.

처음 비용 측정의 plannerCandidatePhysicalComputations=0은 물리 계산이 없었다는 뜻이 아니라 계측 호출 누락이었다. 관찰용 카운터를 복구했고 캐시 on/off의 동일 Draft 결과와 물리 작업 감소를 검사했다. 원 측정값을 사후 수정해 새 실측인 것처럼 만들지 않고 JSON에 이 한계를 표시했다. 나머지 role/completion 재사용 카운터와 시간은 당시 실측이다.

최초 64경기 평균 전체 실행(준비/Draft/엔진/진단 행 정리)은 5.954초, 최종 16경기는 5.793초였다. 최초 serialized input replay는 Draft를 다시 하지 않는 엔진 실행 0.613초였다. 서로 다른 JVM warmup·출력 작업·동시 프로세스 조건이므로 이 값들을 단순 합산하거나 과거 대비 속도 개선률로 환산하지 않는다. 최종 tempo 수정 이후 순수 엔진-only 시간은 별도로 측정하지 않았다.

- 공간은 라인/활동/복귀 시각 단위다. 실제 이동 경로·시야·스킬 사거리·기지 좌표를 재현하지 않으며, 본진 위협의 해소/재발에 따라 복귀 이벤트가 반복될 수 있다.
- 인베이드 별도 경로, 바위게·침입 정글, 작은 몬스터별 수급, 전령 눈 드롭/재획득, 룬과 실제 전령 일반 공격은 없다. 유충 추가 피해와 전령 후속 돌진/수명·현장 수비자의 제거는 명시적인 V1 추상화다.
- XP는 현장 기회 풀, CS는 제한된 웨이브 풀이다. 실제 미니언 사망 좌표나 라스트힛 하나하나의 전투 모델은 아니며, 정글 성장 곡선·경기당 캠프/갱 빈도와 장기 경기 종료 성능은 추가 밸런스 관측이 필요하다.
- 새 64경기는 tempo 수정 전 평균 32:53, 최장 70:40이었다. 최종 같은 16경기는 평균 34:30, 최장 48:20이다. 70분 사례가 최종에도 남는다고 단정하거나 16경기만으로 모든 장기 경기 문제가 해소됐다고 주장하지 않는다.
- 바루스 픽과 바이·카밀 밴의 높은 빈도는 남았다. 대체재 제거 차익이 0인 밴도 위협·메타·제한 탐색으로 선택될 수 있으므로 상대 모델/점수 스케일은 추가 관측 대상이다. 공식 승률 검증·챔피언 데이터 전면 재평가·통계 승인으로 승격하지 않았다.

## 직접 경로와 사용자 데이터 보존

원본 선수/능력치/숙련도/일정·CA·PA·시장 정책을 바꾸지 않았다. 새 Career 진단과 브라우저는 격리 H2 메모리 DB를 사용했다. 기존 리뷰 matches.csv와 prompts/, 선수정보.zip 및 별도 V2 리뷰 파일은 보존했다. 구 input/receipt/공식 artifact의 hash를 새 결과로 교체하지 않았다.

Player가 고른 Garen 밴을 포함한 실제 Series Draft를 새로고침 후 복구해 선택이 유지되는 것을 확인했다. 20턴 완료 후 AI 선택 이유가 일반 화면에 표시됐고, 같은 Draft로 실제 30:30 경기(10:5 GEN 승)를 실행했다. 결과·타임라인에는 유충 1:2와 전령 1:0, 수비 복귀·수비 교전이 표시됐다. 경기 실행은 1회이고 저장된 결과의 replay는 별도 확인이다. 진단 91경기에 이 브라우저 실행이나 correctness 테스트의 경기를 합쳐 통계 표본인 것처럼 보고하지 않는다.

브라우저에서 발견한 Player Draft의 구 runtime 전용 검사 때문에 서버에는 결과가 확정됐지만 화면이 오류를 표시하던 문제를 수정했다. 지원되는 구/신 runtime의 명시적 목록을 유지하며 session·정책·result·영수증 해시 일치 검사를 제거하지 않았다. 유충 카운터가 기존 스코어보드의 암묵적 두 번째 행에 가려지던 배치도 5개 열과 한 행으로 교정했다. 기존 재생·속도 버튼·전체 로그·결과 차트가 유지됐다.

검증 이미지는 `output/playwright/realism-v1-draft-restored.png`, `realism-v1-playback-final.png`, `realism-v1-final-results.png`에 있다. 검증용 8095 백엔드, 5173 프런트와 realism-v1 브라우저는 종료했다.

## 최종 검증 결과

시작·최종 HEAD는 모두 `d8af4a49a83939810719b7967f795a0699e7ea8f`다. 구현과 문서 변경은 커밋하지 않은 working tree에 있으며 commit/push/배포/reset은 하지 않았다.

| 실행 | 범위와 실제 결과 | 시간 |
| --- | --- | --- |
| 구현 중 집중 | A/B 28건, C/D 24건, E 4건, 초기 F 3건, 경계 보강 27건 모두 통과. 서로 중첩되는 실행이므로 고유 테스트 수로 합산하지 않음 | 기존 로그/XML 보존 |
| tempo 교정 | Jungle/Tempo/새 입력 replay 23건 통과 | 59초 |
| Draft 보강 | 대체재 테스트의 Mockito 준비를 교정한 1건, 캐시 on/off 동일 결과·계산 감소 1건 통과 | 각각 15초 / 26초 |
| 계획된 전체 **1회** | **중단**. 213개 완료 클래스, 완료된 leaf invocation 1,675건: 1,640 통과·33 실패·기존 skip 2 | 프로세스 실행 약 25분 44초; 보존된 완료 이벤트 구간은 23분 54.744초 |
| background 실패 별도 확인 | 기존 120초 완료 제한 유지, 1건 통과 | Gradle 1분 30초 |
| 미완료 + 실패 영향 범위 | 79개 클래스·594건: 588 통과·6 실패 | 16분 26.626초 |
| 마지막 정책 기대값 교정 | 위 6건을 포함하는 5개 클래스·15건 모두 통과 | 1분 52.528초 |
| frontend | `player-draft:verify`, `series:verify`, 실제 응답 `live:verify`, TypeScript/Vite build 통과 | Vite build 10.45초 |
| 실제 브라우저 | 수동 밴 → AI 턴 → 새로고침 복구 → 실제 경기 → 결과/replay/타임라인. 콘솔 오류·경고 0 | 경기 실행 1회, 저장된 결과 replay 별도 |

전체 실행의 Gradle daemon에는 `client disconnection detected, canceling the build`가 남았고 종료 코드는 143이었다. 정상 종료나 clean full로 처리하지 않았다. 소스 수정이나 동일 전체 재시도에 앞서 로그와 Gradle의 in-progress 결과를 `/tmp/realism-v1-evidence/full-aborted/`에 복사했다. 해당 Gradle 버전의 결과 reader로 완료 레코드만 복원하고 JUnit discovery의 기본 277개 클래스와 대조해 미완료 64개를 구했다. 매개변수 테스트의 부모/suite를 leaf 테스트로 중복 집계하지 않았다.

미완료 64개와 실패 원인에 해당하는 클래스의 합집합에서 별도 통과한 background 1개를 뺀 79개를 실행했다. 마지막 정책 기대값 6건을 교정한 뒤 관련 5개 클래스만 검증했다. **원본 완료 결과와 후속 결과의 클래스 합집합은 277/277**이며, 클래스별 가장 최근 결과를 적용하면 2,182 통과·기존 skip 2·미해결 실패 0이다. 이 수치는 여러 실행의 범위 완결성 확인이며 최종 트리의 단일 clean full 결과가 아니다.

실패는 원인별로 다음과 같이 처리했다.

- 역사적 golden/기존 정책 테스트가 새 기본 경로를 호출하던 부분에는 legacy 정책을 명시했다. 구 기대 hash를 새 값으로 덮어쓰지 않았다.
- 새 기본 경로의 API·Series·별도 JVM receipt 검사는 실제 REALISM/ABILITY 정책을 기대하도록 갱신했다. byte-identical receipt, timeline, Random fingerprint, 수동 선택, 무결성 거부 검사는 유지했다.
- Player Draft 성능 probe가 전략 결속을 빠뜨리던 테스트 준비와 Series mock의 선택 정책 ID를 보완했다. 기존 항목만 검사하는 scoring 테스트와 신규 성능 항목 검사를 구분했다.
- Career 일정 검사의 명부 준비는 실제 공개 시장에서 특정 제안이 반드시 이긴다는 가정을 제거하고 정상 계약 수락 경로로 명시적으로 준비했다. 이후 실제 Calendar/Auto/동결 명부/중복 방지 검사를 유지했다. 시장 경쟁 제품 정책은 변경하지 않았다.
- background의 전체 중 제한 시간 초과는 같은 120초 제한으로 별도 통과했다. 제한을 늘리거나 assertion을 삭제하지 않았다. 테스트 실패 시 마지막 job 상태를 제공하는 관찰 정보만 추가했다.

후속 수정은 위 테스트 계약과 준비로 범위가 한정되고, 실제 런타임·Random 공유 동작을 추가로 바꾸지 않았다. 미완료 클래스도 모두 실행했으므로 추가 전체가 증명해야 할 미확인 범위는 남지 않았다. 두 번째 전체, default 제외 변경, 신규 skip, 통계 샘플 축소를 통한 회피는 없다.

작업 범위의 `git diff --check`는 통과했다. 전체 working tree에는 시작 전부터 수정되어 있던 기존 리뷰 `match-engine-and-auto-draft-realism-review-2026-09-08/matches.csv`의 CRLF 관련 75개 trailing-whitespace 지적이 남는다. 이 사용자 변경은 정규화하거나 덮어쓰지 않았다.

근거: [검증 집계](match-engine-realism-and-ability-based-draft-v1-results/verification.json), [원본 중단 로그](match-engine-realism-and-ability-based-draft-v1-results/full-regression-aborted.log), [잔여/영향 범위 로그](match-engine-realism-and-ability-based-draft-v1-results/resumed-and-fixed.log), [최종 집중 로그](match-engine-realism-and-ability-based-draft-v1-results/final-policy-expectations.log), [경기 비교](match-engine-realism-and-ability-based-draft-v1-results/summary.json), [최종 16경기](match-engine-realism-and-ability-based-draft-v1-results/tempo-correction.csv), [대표 로그](match-engine-realism-and-ability-based-draft-v1-results/representative-logs.json), [Draft 비용](match-engine-realism-and-ability-based-draft-v1-results/draft-cost.json). 원본 XML과 세부 실패 증거는 `/tmp/realism-v1-evidence/`에 보존했다.

한글 커밋 메시지 제안: `매치 현실성과 성능 기반 밴픽을 신규 경기 정책에 통합`
