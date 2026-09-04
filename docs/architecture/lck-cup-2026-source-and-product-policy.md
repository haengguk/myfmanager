# LCK Cup 2026 source and product policy

## Source ledger

| ID | 분류 | 발행처 / 문서 | 날짜 | 증명 범위 |
| --- | --- | --- | --- | --- |
| S01 | `OFFICIAL_SOURCE_FACT` | Riot Games / LoL Esports, [2026 LCK 진행 방식 안내](https://lolesports.com/ko-KR/news/2026-lck-format-explainer) | 2025-11-06 | Super Week, 진출 구조, choice right, full double elimination |
| S03 | `OFFICIAL_SOURCE_FACT` | Riot Games / LCK, [2026 LCK 대회별 규정집](https://cmsassets.rgpub.io/sanity/files/dsfx7636/news_live/b5e4819daa2793425433910ca02b1c479b2f15a3.pdf?accountingTag=lol_esports) | 2026-06-29 | group/tie-break/Play-in/Playoff/side rule |
| S04 | `OFFICIAL_SOURCE_FACT` | LoL Esports, [LCK 공식 일정](https://lolesports.com/ko-KR/leagues/lck) | 2026 | stage window와 확인 가능한 match date |
| S05 | `OFFICIAL_SOURCE_FACT` | Riot Games / LoL Esports, [2026 LCK CUP 결승 안내](https://lolesports.com/ko-KR/news/2026-lck-cup-finals-guide) | 2026-01-29 | 2월 28일·3월 1일 finals weekend |
| S16 | `OFFICIAL_2026_INITIAL_BOOTSTRAP` | LCK, [2026 LCK CUP 드래프트](https://www.youtube.com/watch?v=gzq0Os3loaQ) | 2026-01-06 | captain, Baron/Elder 배정과 group seed |

S03 raw SHA-256은
`e4c52706feb67dfbac23669ce8cc0a01d5a780be71772b149849d41e9698a670`이다. 전체 compact runtime
resource SHA-256은 `f544319c2bb126fb26304a856a612edd8f75a77db551f9a4e923b69ad1ae2468`이다.

## Official rule model

10팀은 Baron/Elder 5팀씩이고 상대 그룹의 모든 팀과 한 번씩 만나므로 총 25 Series다. 일반
20경기는 Bo3/그룹 승점 1, 동일 seed 5개 Super Week는 Bo5/승점 2다. Hard Fearless를 적용한다.
그룹 승패는 그룹 승점, aggregate game differential, 공식 tie-break 구조로 결정하고 개인
순위는 resource의 ordered criteria를 따른다.

Play-in은 6팀/5경기다. R1은 3-v-6, 4-v-5, R2는 seed 1이 R1 승자 중 상대를 선택하고 seed 2가
나머지를 상대한다. 두 R2 승자는 Playoff seed 4/5, 두 패자는 Bo5 final을 치러 승자가 seed 6이
된다. Playoff는 6팀/10 Bo5 full double elimination이다. 공식 opponent-choice owner는 Play-in
R2 seed 1, Playoff upper bracket R1 seed 3, upper bracket R2 seed 1이다. 결승 우승/준우승이
First Stand LCK seed 1/2다.

## Initial bootstrap

Season ordinal 1만 `OFFICIAL_2026_INITIAL_BOOTSTRAP`을 쓴다.

| Baron | Elder |
| --- | --- |
| 1 GEN | 1 HLE |
| 2 T1 | 2 DK |
| 3 NS | 3 KT |
| 4 DNS | 4 BFX |
| 5 BRO | 5 KRX |

Source code `DRX`→stable code `KRX`는 명시적 alias다. 첫 playable Calendar year가 2027이어도
source year는 2026으로 별도 보존한다. 현실 2026 result는 하나도 import하지 않는다.

## Future policy

Season ordinal 2부터는 `LCK_CUP_PREVIOUS_IN_GAME_LCK_RANKING_GROUP_DRAFT_V1`을 쓴다. 같은 Career의
직전 year, `SEALED`, exact rank 1~10과 SHA-256 state identity가 없으면 materialization 전에
거부한다. Champion/runner-up이 captain이고 champion이 첫 selection order를 사용한다. 공식 turn
semantics에 따라 ranks 3~10을 배분한다.

사람 선택 UI가 없는 opponent-choice는 `LCK_CUP_LOWEST_AVAILABLE_SEED_OPPONENT_SELECTION_V1`이다.
공식 eligible team만 seed 내림차순과 stable identity로 canonicalize한 뒤 가장 낮은 seed를 고른다.
이는 official choice right가 아니라 `GAME_PRODUCT_POLICY`이며 input/order/chosen/policy hash를
receipt에 남긴다.

## Schedule policy

공식적으로 확인한 Super Week와 finals weekend는 `OFFICIAL_PROJECTED_DATE`다. 현재 공식 페이지에서
과거 일반 20경기의 exact schedule을 검토 가능한 완전 대진표로 재현하지 못했으므로 나머지 stage
slot은 `GAME_DERIVED_SCHEDULE_POLICY`다. 모든 미래 year 날짜는
`SAME_LOCAL_MONTH_DAY_FROM_2026_REFERENCE_V1` 투영이며 공식 미래 일정이 아니다.
