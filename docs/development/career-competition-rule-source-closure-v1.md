# Career competition rule source closure V1

조사일: **2026-09-05 (KST)**. 시작 HEAD: `bab58776b94acade3237c1a0303c12bd9705ee49`.
기능 검토 기준: `829916e8a1c1b13bbde890748adebf0f1635748f`, 부모:
`8ce3ddc789e0fee54fffbb081bc1f907611d2487`. 현재 트리에서 조사했으며 checkout/reset하지 않았다.

조사 범위는 완료했다. 아래에서 확정한 규칙과 남은 자료·제품 선택을 구분한다.
**전체 SOURCE_CLOSED 또는 시즌 완주 가능 판정은 아니다.** 실행 안정화 결과는
[별도 검증 보고서](career-competition-execution-stabilization-v1.md)에 기록한다.
조사 자체는 production 규칙 JSON, resource hash, readiness, Calendar gate를 변경하지 않았다.
이후 국내 V3와 [국제대회 실행 V1](career-international-fst-msi-ewc-worlds-execution-v1.md)이
아래 실행 공백을 해소했다. 공식 근거와 명시적 게임 정책은 계속 구분한다.

## 연도와 증거의 적용 경계

- 첫 playable cycle anchor는 **2027**, 규칙·달력 reference year는 **2026**이다.
  catalog snapshot `2026-08-24` 이후 최초 전체 cycle을 선택하는 기존 정책이다.
- `SAME_LOCAL_MONTH_DAY_FROM_2026_REFERENCE_V1`은 미래 일정 투영 정책이다. 2027 공식 일정이 아니다.
  새 자료는 기존 Career의 frozen resource/binding에 자동 소급하지 않는다.
- LCK Cup 첫 시즌의 2026 그룹 bootstrap과
  `LCK_CUP_LOWEST_AVAILABLE_SEED_OPPONENT_SELECTION_V1`은 기존 승인 정책이다.
  공식 상대 선택권과 게임이 실제로 선택하는 상대는 별개다. 사람 선택 UI, 새로운 자동 선택,
  국제대회 seed bootstrap 또는 새로운 일정 배정은 별도 제품 선택이다.
- 실제 대회 참가·우승 결과는 출처 판독에만 사용했다. Career 결과/진출권에 주입하지 않는다.
- 아시안게임은 조사·구현·후속 우선순위에서 제외했다. 기존 이벤트와 gate는 보존했다.

## 직접 확인한 출처

PDF 페이지는 표지를 포함한 **1-based PDF page**다. 아래 C 번호는 이 문서의 인용 키이며
production JSON의 S 번호를 변경하지 않는다. 게시일과 시행일은 구분한다.

| 키 | 직접 URL / 판독 위치 | 발행·개정·적용 범위 | 판정 |
|---|---|---|---|
| C1 | [2026 LCK 대회별 규정집](https://cmsassets.rgpub.io/sanity/files/dsfx7636/news_live/b5e4819daa2793425433910ca02b1c479b2f15a3.pdf?accountingTag=lol_esports), §2.7–2.8 pp.8–14, §3 pp.14–19, §4 pp.20–25 | 개정 2026-06-29, 시행 2026-07-29; Riot 라이브러리 게시 07-24 | 공식 원문. 기존 S03과 동일 자산. 시즌 말 규칙은 현재 확인 가능. 이미 지난 Cup에 대한 역사적 소급 여부와 게임의 reference 채택은 구분 |
| C2 | [2026 LCK 진행 방식](https://lolesports.com/ko-KR/news/2026-lck-format-explainer), Cup/정규시즌 플레이오프 설명 | 2025-11-06, 2026 대회 | 공식 사전 발표. Cup 10경기와 시즌 말 10경기를 구분하는 교차 근거 |
| C3 | [Riot Competitive Operations Library](https://competitiveops.riotgames.com/en-US/library?SPIEL=lol) | 조사일 공개 목록 | 공식 규정집의 제목·게시 시각·PDF URL을 공개 HTML 데이터에서 확인 |
| C4 | [First Stand 2026 v1.0](https://cdn.sanity.io/files/dsfx7636/news_live/2893a4fb548f9253d1c99965718c69312ce3bc6b.pdf), §2.2 p.4, §3 p.5, §4 pp.7–10 | C3 게시 2026-03-04 | 공식 대회별 규정. [Primer](https://lolesports.com/ko-KR/news/fst-2026-primer)(03-02)와 [Handbook](https://lolesports.com/en-US/season/115547545029543948/handbook/115548016979679444)도 본문 확인 |
| C5 | [MSI 2026 v1.0](https://cdn.sanity.io/files/dsfx7636/news_live/7487705ee4449ee59d163b4f92577127d6dbc365.pdf), §2.2 p.4, §4 pp.7–12, Appendix A/B pp.17–20 | C3 게시 2026-06-15 | 공식 대회별 규정. §4.2의 지역 배치는 현실 FST 결과에 따라 이미 구체화된 값 |
| C6 | [MSI 및 Worlds 업데이트](https://lolesports.com/ko-KR/news/msi-and-worlds-updates), 두 대회 참가·진출 형식 | 2026-03-22 | 공식 일반 규칙. [MSI Primer](https://lolesports.com/ko-KR/news/msi-2026-primer)도 확인 |
| C7 | [Worlds 2026 v1.0](https://cdn.sanity.io/files/dsfx7636/news_live/faa5ce974e58615911fbee931c6123e2785a8b46.pdf), §2.2 p.4, §4 pp.7–13 | C3 게시 2026-08-31 | 공식 대회별 규정. 이전 08-23 조사보다 늦게 게시. 지역별 최종 슬롯·pool은 현실 결과가 반영된 2026 값 |
| C8 | [EWC 공식 포털](https://resources.esportsworldcup.com/en/competitive-ops/rulebooks/lol) → [2026 LoL Rulebook](https://cdn.esportsworldcup.com/resources/uploads/EWC_26_League_of_Legends_Rulebook_a31ae07a83.pdf), §2 pp.4–6, §3 pp.7–9, §5.6 | 표지 2026; 명시적 개정일 미확인 | 공식 원문. 아래 LCP 슬롯 표기 문제는 미결로 보존 |
| C9 | [2025 KeSPA 공시](https://www.a-esc.com/user2/board/view/board_cd/notice/wr_no/33) → [규정집 V1.1](https://www.a-esc.com/site_data/file/notice/2025/12/1765502569_2ALOHemk_28EAB5ADEB.pdf), §2.3 p.13, §5 pp.27–33 | 규정 2025-11-12, 재공시 12-12 | 공식 기관 재공시. **2025 전용** |
| C10 | [KeSPA 2026 개최 발표](https://www.e-sports.or.kr/news/media_view?brd_id=3BT9UGFR0001), 대회 구성·일정 문단 | 2026-06-30 | 협회 공식 원문. 초기 발견 경로는 [협회 제공 보도자료 재게시](https://gamechosun.co.kr/webzine/article/view.php?no=223101); 최종 근거는 협회 원문 |
| C11 | [KeSPA 2026 개막 안내](https://www.e-sports.or.kr/news/media_view?brd_id=3BT9UH2J0001), roster·그룹·stage 일정 문단 | 2026-07-16 | 협회 공식 원문. C10의 예선 설명을 cross-group으로 구체화 |
| C12 | [KeSPA 파이널 스테이지 2 안내](https://www.e-sports.or.kr/news/media_view?brd_id=3BTO400G0001), 1R→2R→결승 문단 | 2026-08-11 | 협회 공식 원문. 실제 팀 이름으로 설명한 경로를 확인하되 게임 결과로 가져오지 않음 |
| C13 | [기존 S19 링크](https://school.e-sports.or.kr/notice/view/1065) | 2026-07-02 | 실제 페이지 제목은 `2026 MSI에 KeSPA 부스가 찾아옵니다!`. 페이지의 MSI 부스 기간을 KeSPA 대회 날짜로 사용하면 안 됨 |
| C14 | [국제대회 공통 규정 v1.1](https://cdn.sanity.io/files/dsfx7636/news_live/d29e446e0a0d0813a8caaae1d2817dd82cfe79c6.pdf), §4.5 p.18 | C3 게시 2026-07-27 | Draft 및 Fearless 오류 처리 규정. 대회별 모든 단계의 Hard Fearless 채택을 이 조항만으로 추정하지 않음 |
| C15 | [2026 시즌 시작](https://lolesports.com/ko-KR/news/season-start-2026-lol-esports), First Stand·첫 번째 선택권 문단 | 2026-01-08 | 공식 First Stand BO5/Fearless 발표 및 side/pick 선택 분리 |

접근 기록: 웹 판독기는 C3 목록 일부만 추출했고 C5/C7·KeSPA 상세 URL에 safe-open 오류를 냈다.
공개 HTML의 asset URL/게시판 GET 링크를 확인한 뒤 일반 HTTPS GET으로 PDF와 본문을 읽어 해결했다.
KeSPA 공지 검색에는 2025 규정 공시가 있고, 조사한 2026 게시글에는 전체 규정집 첨부가 없었다.
협회 Instagram은 429, DICA 사이트는 TLS 인증서 검증 실패로 읽지 못했다. 이를 규칙의 부재 증명으로
사용하지 않는다. 남은 질문은 아래에 명시한다. 인증·접근 제한 우회는 하지 않았다.

## 코드·blocker·후속 작업 지도

공통 위치:
[규칙 resource](../../backend/src/main/resources/competition/lck-career-competition-rules-2026-v2.json),
[Rules](../../backend/src/main/java/com/lolfm/career/CareerCompetitionRules.java),
[RelationalStore](../../backend/src/main/java/com/lolfm/career/CareerCompetitionRelationalStore.java),
[CompetitionApplicationService](../../backend/src/main/java/com/lolfm/career/CareerCompetitionApplicationService.java),
[CalendarApplicationService](../../backend/src/main/java/com/lolfm/career/CareerCalendarApplicationService.java).

아래 표와 직후 gate 설명은 **최초 조사 시점의 상태**다. 최신 구현 상태는 문서 끝의 반영 항목을 따른다.

| 대회/단계 | 조사 당시 구현 | 부족했던 근거 | 당시 미구현 코드·실행 데이터 | 당시 blocker / 의존 작업 |
|---|---|---|---|---|
| LCK Cup 동률·Play-in seed | C1 §2.7–2.8, §3.2.1. `sealCupGroupStandings`는 25 receipt 집계, 기존 Playoff 10경기 graph는 구현됨 | 규칙 순서 확인됨. 평균의 표본이 없는 경우 등 명시되지 않은 입력은 운영 해석 필요 | 공식 SoV/세트별 승리시간·통합 6 seed 정렬과 재경기 경로. 아래 코드 차이 참조 | `LCK_CUP_TIEBREAKER_REQUIRED` → 증거 보강/규칙 교정 → 필요 시 추가 fixture → 재봉인 |
| LCK R3/R4 | C1 §2.7–2.8, §4.1. Store가 R1/R2 누적값과 40경기를 합산 | 기본 규칙 확인됨 | H2H 원장, 추가 경기/승리시간, 동률 경로와 durable 재개 | `LCK_R3_R4_TIEBREAKER_REQUIRED` → 증거 보강 → 동률 해결 → Play-in/PO seed 봉인 |
| 시즌 말 LCK Play-in/Playoffs | C1 §4.2–4.4, C2. Play-in 3경기 구현; `LCK_PLAYOFFS.matches=[]` | 아래 10경기 routing은 확인 완료 | PO seed 1–6 소비, 10 fixture/choice/side/result graph, 1–10위 및 Worlds output | `LCK_PLAYOFF_BRACKET_RULE_SOURCE_INCOMPLETE`는 코드에 남아 있음. 근거 문서만으로 해제 불가. 동률 해결 → PO graph → qualification |
| First Stand | C4. Cup은 LCK seed 1/2 output만 생성; 외부 instance `EXTERNAL_ONLY` | 미래 cycle pool/bootstrap의 적용 기준 | 6개 외부 슬롯의 team/roster/snapshot, draw·13 BO5·결과·MSI 지역 성과 output | `EXTERNAL_COMPETITION_EXECUTION_NOT_IMPLEMENTED` → 외부 authority + seed 정책 → 실행 |
| MSI | C5/C6. Road to MSI의 국내 진출 output만 존재 | 게임 내 FST 성과를 일반화한 지역 ranking/tie 기준 | 외부 참가자·pool·Play-in 6+본선 14경기·Worlds 보너스 output | 동일 외부 blocker → FST 결과/외부 authority → MSI |
| EWC | C8. 혼합 BO 형식 metadata만 존재 | LCP#2 표기, qualifier별 선발 규정/대체 슬롯·첫 cycle 전년도 우승 슬롯 | 16팀 authority, 지역 qualifier input, draw, BO1/BO3/BO5 graph | 동일 외부 blocker → 슬롯 해석/qualifier authority → 실행 |
| Worlds | C6/C7. 단계 metadata만 존재 | counterfactual MSI 결과일 때 slot/pool 일반화, 불가능한 Swiss draw의 운영 재량을 게임으로 표현할 정책 | 외부 참가자, 조건부 seed, Play-in/Swiss/KO·재대결 금지·최종 순위 | 동일 외부 blocker → 국내 PO+MSI outputs → Worlds |
| KeSPA 2026 | C10–C12. 현재 `kespaCupReferenceTemplate`은 C9의 2025 14-slot 구조, fixture 0 | 2026 전체 규정·stage1 모든 edge/동률/side/Fearless/공식 등록 roster | 2026 전용 정의·날짜·10팀/최대10인 roster authority·stage graph; 기존 저장 승격 | `KESPA_CUP_2026_RULE_SOURCE_INCOMPLETE`, `EXTERNAL_PARTICIPANT_ROSTER_AUTHORITY_MISSING` → 규정/roster → 연도·일정·저장 호환 정책 → 실행 |

Calendar의 `gate`는 **현재 날짜의 대회 상태를 먼저**, 이어 가장 이른 overdue 미완료 fixture를 확인한다.
현재 대회가 차단돼 있으면 advance 명령을 제공하지 않는다. Cup을 완료해도 First Stand 기간에
외부 실행 blocker를 만나므로 이 수정으로 정규시즌까지 자동 완주하지 않는다. MSI/EWC/Worlds도
각 window에서 같은 원리가 적용된다. LCK 동률 blocker는 관련 seed/다음 대진 생성을 막는다.

KeSPA는 현재 Calendar definition 자체가 없고 `sourceDataNotes`와 별도 source-gap instance로
표시된다. 따라서 현재 달력에서 특정 KeSPA 날짜에 멈춘다고 주장할 수 없다. 2026 날짜를 추가하면
기존 EWC/R3/R4 window와 참가자·일정 충돌을 검증해야 하며, source note만 지우는 변경으로
진행 가능 상태가 되지 않는다. 기존 아시안게임 gate 역시 남아 있다는 점만 관찰한다.

## 시즌 말 LCK: 실행 가능한 경로 명세

아래 ID는 **후속 구현용 설명 ID**다. 현존 Cup match ID를 바꾸거나 복제하라는 뜻이 아니다.
`W(x)`/`L(x)`는 승자/패자, `s1..s6`은 **플레이오프 시드**다. [C1 §4 pp.20–25](https://cmsassets.rgpub.io/sanity/files/dsfx7636/news_live/b5e4819daa2793425433910ca02b1c479b2f15a3.pdf?accountingTag=lol_esports).

| 단계 | 입력/출력 |
|---|---|
| R3/R4 | R1/R2 누적; Legend1–4→s1–4, Legend5·Rise1–3→Play-in, Rise4/5→최종9/10 |
| P1 | Legend5–Rise1; W→s5 |
| P2 | Rise2–Rise3; L→8위 |
| P3 | L(P1)–W(P2); W→s6, L→7위 |
| U1A | s3–choose(s5,s6) |
| U1B | s4–remaining(s5,s6) |
| L1 | L(U1A)–L(U1B); L→6위 |
| U2A | s1–choose(W(U1A),W(U1B)) |
| U2B | s2–remaining(W(U1A),W(U1B)) |
| L2 | W(L1)–lowerSeed(L(U2A),L(U2B)); L→5위 |
| U3 | W(U2A)–W(U2B) |
| L3 | W(L2)–higherSeed(L(U2A),L(U2B)); L→4위 |
| L4 | L(U3)–W(L3); L→3위 |
| F | W(U3)–W(L4); W/L→1/2위 |

R3/R4는 BO3/Fearless, Play-in과 PO는 BO5/Fearless다. PO는 10경기이며 reset 결승을 추가하지 않는다.
첫 세트 RoFS: U1=s3/s4, U2=s1/s2, L1/U3=coin, L2/L3=U2패자,
L4=U3패자, F=U3승자(픽·진영 모두). 이후 세트는 직전 패자.
공식 선택권은 s3/s1 소유이며, 자동 선택 정책 채택은 별도다.

Worlds: PO1/2/3→지역1/2/3시드; 네 슬롯이면 PO4→4시드.
MSI 우승팀은 국내 PO 진출 조건부 최소4시드; PO5/6이면 통상 PO4를 대체하고,
PO 미진출이면 특례를 상실한다. 슬롯 수와 우승팀은 **Career 내 국제대회 증거**에서 와야 한다.

## 동률 규칙과 현재 코드의 차이

확인된 순서: [C1 §2.7–2.8 pp.8–14 및 §3.2.1 p.16](https://cmsassets.rgpub.io/sanity/files/dsfx7636/news_live/b5e4819daa2793425433910ca02b1c479b2f15a3.pdf?accountingTag=lol_esports).

| 범위 | 결정 순서 |
|---|---|
| Cup 개인/통합 Play-in seed | 매치승→세트득실→H2H(동일그룹·통합Play-in에서는 제외)→SoV→승리시간→재경기 |
| Cup 그룹 | 포인트→합산득실→혼합BO5: 홀수세트=각5위, 짝수세트=각1위; Fearless |
| 정규 2팀 | 매치승→득실→H2H승률→H2H득실→재경기 |
| 정규 3팀 | H2H로 전순위 결정/단독상하위 고정+2팀규칙/전원동률이면 하위2팀 경기→승자–상위시드 |
| 4팀 | 무작위2경기→승자끼리/패자끼리 순위전 |
| 5팀 | 하위2팀전→패자최하위; 나머지4팀규칙 |
| 6/7팀 | 하위4/6팀 무작위예선→패자2/3팀규칙, 승자+bye는4팀규칙 |
| 8/9/10팀 | 8=무작위4예선→승자4/패자4; 9=하위2예선→8; 10=하위4예선→승자+6은8/패자2 |

SoV=`Σ매치승×(5.5−0.5×상대순위)`; 공동순위 다음은 점유수만큼 건너뛴다.
Cup 그룹내 비교는5위표, 통합시드는10위표; R3/R4는 Legend1–5/Rise6–10.
승리시간=동률상대전 **승리세트 평균**(패한매치의 세트승 포함)→동일시 전체상대 평균;
몰수승20분/몰수패최하위. 부분동률은 적용한 기준을 재시작하지 않는다.

다자간 재경기 seed/RoFS: H2H(3팀예외)→H2H득실→SoV→시간→coin.
진출·상금 무관 재경기는 생략; 필요한 총1/2/3+경기는 각각 BO5/BO3/BO1.

**V2 당시 미해결 차이**(아래 목록은 조사 시점의 기록):

1. `sealCupGroupStandings`의 `strength`는 이긴 상대의 `matchWins` 합이다. 순위별 배수 표가 아니다.
2. `winTime`은 승리 **Series 전체** `durationSeconds` 합이다. receipt의 ordered game evidence를
   활용할 수 있지만 현재 집계에는 세트 승리/동률 상대/평균 분모가 반영되지 않는다.
3. `CUP_PLAY_IN_SEED`를 승자그룹3위·패자그룹2위·승자그룹4위·패자그룹3위·승자그룹5위·패자그룹4위로
   고정 교차 배치한다. 공식 통합 성적 정렬과 다르며 동률 외 입력에서도 seed가 달라질 수 있다.
4. R3/R4는 `seriesWins > gameDifferential > gameWins`로 정렬한다. 마지막 `gameWins`는
   공식 H2H 대체 근거가 없다. 세 값까지 동일할 때만 현재 blocker가 생긴다.
5. 따라서 blocker에 도달한 경우만 고치면 충분하지 않다. 현행 정렬이 결론을 냈지만 공식 순서로는
   결론이 달라지거나 추가 증거가 필요한 입력도 후속 회귀 사례로 다뤄야 한다.

이후 [국내 구현 V1](career-domestic-ranking-playoffs-finalization-v1.md)에서 아래 국내 항목을 연결했다.
V3는 SoV/평균/H2H/실제 동률 fixture/10경기 PO/최종1–10위를 구현하며, V2 결과는 보존한다.
표본 없는 평균과 진출·상금 무관 위치 배정, 날짜·추첨·자동 선택의 게임 정책은 해당 보고서를 따른다.

당시 제시한 해결 순서:

1. R1/R2 import의 합계 순위뿐 아니라 scope/hash로 인증된 H2H 경기·세트 원장을 확보한다.
   Cup/R3R4 receipt의 game winner와 duration을 읽고, 몰수 등 미지원 상태는 추정하지 않는다.
2. `tieGroup`, 적용 완료 기준, 증거 범위, 미결 이유를 구조화한 순위 판정을 도입한다.
   특히 승리세트 표본이 없는 경우와 Cup 그룹간 SoV 평가 모집단은 빈 평균을 0으로 만들지 말고
   규정 해석/명시 정책을 먼저 고정한다.
3. 해소 가능한 동률은 자료로 해결하고, 추가 경기 필요 시 별도 stable fixture와 command를 만든다.
   Cup 그룹 BO5는 세트마다 **팀 자체가 바뀌므로** 현행 고정 두 팀 Series binding으로 표현할 수 없다.
   팀·로스터 범위, Fearless history 범위 및 receipt 계약을 별도로 설계해야 한다.
4. 경기 결과를 현재 verified completion/fence 경계에 연결하고, 모든 필요한 증거가 모인 뒤
   standings/seed/output을 원자적으로 봉인한다. 재시작/중복 제출은 같은 tie-resolution identity를 사용한다.
5. 예전 방식으로 봉인된 Career를 조용히 재정렬하지 않는다. rule/policy version 및 저장 전환 기준을
   정하고, 영향을 받는 downstream binding이 이미 있으면 별도 호환 처리한다.

## First Stand · MSI · Worlds

V1 구현의 보충 정책·저장 계약은 [국제대회 실행 기록](career-international-fst-msi-ewc-worlds-execution-v1.md)을
참조한다. 아래의 원문과 현실 2026 배정은 근거이며, 구현 상태는 해당 기록의 최종 검증 상태를 따른다.

### First Stand

[C4 §2.2/§4](https://cdn.sanity.io/files/dsfx7636/news_live/2893a4fb548f9253d1c99965718c69312ce3bc6b.pdf):
LCK2/LPL2/LEC1/LCS1/LCP1/CBLOL1, 총8팀. Pool1=LCK1/LPL1,
Pool2=나머지 네 지역1, Pool3=LCK2/LPL2. 그룹별 지역 중복 금지, 시작 대진은 Pool1–3와 Pool2–2.
각4팀 그룹의 두 개막전→승자전·패자전→승자전패자–패자전승자; 승자전승자와 최종전승자가 각1/2시드.
A1–B2, B1–A2 준결승→결승, 전부 BO5(그룹10+KO3=13경기).
RoFS는 그룹R1 상위pool(동일pool 추첨), R2/R3 coin, 준결승 그룹성적, 결승 coin; 이후 직전세트패자.
§3은 5–7인 등록 roster와 game별5인을 요구한다.
그룹3/16–20, KO3/21–22. Fearless 채택은
[C15](https://lolesports.com/ko-KR/news/season-start-2026-lol-esports)에서도 확인된다.

FST에서 얻는 MSI 지역 혜택은 [C6](https://lolesports.com/ko-KR/news/msi-and-worlds-updates)의
대회 간 자격 규칙과 함께 적용해야 한다. 첫 Career에서 LCK/LPL 두 슬롯을 쓰는 것과
미래 FST pool을 계속 고정하는 것은 같은 결정이 아니다. 현실 참가팀 명단은 슬롯 자격의 대체물이 아니다.

### MSI

[C5 §2.2/§4 pp.4,7–11](https://cdn.sanity.io/files/dsfx7636/news_live/7487705ee4449ee59d163b4f92577127d6dbc365.pdf):
5지역 각2+CBLOL1=11팀. Play-in은4팀/6 BO5로 한 팀 선발;
`O1,O2 → U=W(O1)–W(O2), D=L(O1)–L(O2) → L(U)–W(D) → W(U)와 최종전`.
본선은8팀/14 BO5. U1 네 경기의 같은 half 승자/패자가 각각 U2/L1로 간다.
**U2-1 패자→L2-2, U2-2 패자→L2-1**로 교차한다.
L2승자끼리 L3→L3승자–U3패자(L4)→L4승자–U3승자(결승).
하위 탈락 순위는 L1=7/8, L2=5/6, L3=4, L4=3, 결승패자=2.
Play-in 최종전·본선 결승 첫 세트는 상위조 팀 RoDS; 본선U1 상위pool RoFS, 나머지 coin;
이후 직전세트패자. 본선 첫 대진은 Tier1–4와 Tier2–3, 각 half에 두 형태 하나씩/동일지역 중복 금지.
2026 일정은 Play-in6/28–7/1, 본선7/3–12다(§4.4).

현실 2026 전용 §4.2 값은 다음과 같다. **일반적인 Career 지역 배정 알고리즘으로 복사하지 않는다.**

| 구분 | 2026 원문에 이미 배정된 값 |
|---|---|
| Play-in | LEC2–LCP2, LCK2–LCS2 |
| Tier1 / Tier2 | LPL1·LEC1 / LCK1·LCS1 |
| Tier3 / Tier4 | LCP1·CBLOL1 / LPL2·Play-in승자 |

[C6](https://lolesports.com/en-US/news/msi-and-worlds-updates)은 각 지역1시드와 **두 슬롯을 가진 지역 중
FST 성적이 가장 높은 지역의 2시드**가 본선 직행한다고 설명한다. FST 우승 지역이 CBLOL인 경우도
추가 직행 지역은 두 슬롯을 가진 지역 중에서 정한다. 게임 내 FST가 다른 결과라면 **지역 성과 순위의 동률 처리,
두 대표팀이 있는 지역의 평가 기준, pool 재배정**이 필요하다. 이 부분은 국제대회 실행 V1에서
지역별 최종 순위 벡터와 scope에 결속한 동률 추첨 정책으로 정했다. MSI 우승·차상위 지역 성과는 Worlds 추가 슬롯의 입력이 된다.

### Worlds

[C6](https://lolesports.com/ko-KR/news/msi-and-worlds-updates)의 일반 자격은 5지역×3+CBLOL2+
MSI 우승팀 보너스1+차상위 지역 보너스1=19. 우승팀의 국내 PO 조건을 유지한다.
[C7 §2.2/§4](https://cdn.sanity.io/files/dsfx7636/news_live/faa5ce974e58615911fbee931c6123e2785a8b46.pdf)는
2026 현실 배정을 LCK4/LPL4/LEC3/LCS3/LCP3/CBLOL2로 명시한다.
Play-in=CBLOL2/LCS3/LEC3/LCP3, 무제한 무작위 draw, 4팀6 BO5→한 팀 Swiss 진출.
나머지15팀은 Swiss 직행한다. Play-in 내부 연결은 위 MSI 4팀6경기와 같고 reset 경기는 없다.

| 2026 Swiss pool | 지역 시드 |
|---|---|
| 1 | LCK1, LPL1, LCS1, LEC1 |
| 2 | LCP1, CBLOL1, LCK2, LPL2 |
| 3 | LCS2, LEC2, LCK3, LPL3 |
| 4 | LCP2, LPL4, LCK4, Play-in승자 |

R1은 Pool1–4/2–3, 동일지역 금지. R2–5는 같은 매치전적끼리 추첨하고 재대결 금지;
현재/미래 invalid draw를 피하도록 다음 슬롯으로 옮긴다. 가능한 추첨이 없으면 운영진 면제 가능.
3승 진출/3패 탈락; 진출·탈락 결정전 BO3, 나머지 BO1.
라운드 경기 수는8/8/8/6/3. KO는8팀 BO5 단일탈락:
3–0팀 두 팀을 서로 다른 half의3–2 상대에 배치하고 나머지4팀 추첨; 별도 지역 제한 없음.
RoFS는 Swiss 상위지역시드(동일시 coin), 8강 Swiss성적(동일시 추첨순서), 4강/결승 coin;
이후 직전세트패자. [C7 §4 pp.7–13](https://cdn.sanity.io/files/dsfx7636/news_live/faa5ce974e58615911fbee931c6123e2785a8b46.pdf).

2026 날짜: Play-in10/15–18, Swiss10/23–26·28–31, 8강11/3–6, 4강11/7–8, 결승11/14.
규정 원문에는 소개의 MSI 명칭, 우승 연도 `2025`, Play-in 최종 승자의 `Bracket Stage` 표현 같은
편집 잔재가 있다. stage 정의·표지·날짜와 일치하는 조항을 연결했으며, 이 잔재를 새로운 대회 경로로
해석하지 않는다. counterfactual 국제 성과의 지역 슬롯/pool과 Swiss 불가능 추첨의 결정적 처리,
시리즈 단위 Hard Fearless 및 side/pick 선택은 국제대회 실행 V1의 별도 게임 정책으로 채택했다.
공식 확정 사실과 이 보충 정책은 해당 구현 기록에서 구분한다.

## EWC

[C8 §3 pp.7–9](https://cdn.esportsworldcup.com/resources/uploads/EWC_26_League_of_Legends_Rulebook_a31ae07a83.pdf):
16팀→4개 GSL그룹→그룹별2팀→8강. 그룹 첫 경기·승자전 BO1,
탈락 결정전 BO3; 8강/4강/3위전 BO3, 결승 BO5.
그룹 경로는 First Stand의4팀5경기 형태다. 8강은 그룹1위–2위 무작위,
같은 그룹 팀은 결승 전 재회 불가. 잘못된 추첨은 마지막 팀을 다음 유효 슬롯으로 이동한다.
§4 일정은 그룹7/15–16, 8강7/17, 4강7/18, 3위전·결승7/19다.
§5.6.8(p.13)은 같은 Match에서 양 팀이 이미 선택한 챔피언의 재선택을 금지한다.
RoFS는 첫 경기 상위seed, 이후 직전세트패자다. 그룹R2는 추첨, R3는 승자조 출신,
8강은 그룹1위, 4강·3위전·결승은 추첨으로 상위seed를 정한다(§3.2.3/§5.3).

| pool | 공식 표의 슬롯 |
|---|---|
| 1 | LCK#1, **LCP#2**, LEC#1, LPL#1 |
| 2 | 중국예선#1, 한국예선#1, LCS#1, 전년도 우승팀 |
| 3 | APAC예선#1, 중국예선#2, 유럽예선#1, 한국예선#2 |
| 4 | CBLOL#1, 유럽예선#2, 북미예선#1, 남미예선#1 |

그룹당 pool별1팀·동일지역1팀. 전년도 우승팀이 LCK1도 획득하면 높은 seed를 받고
LCK2가 우승팀 슬롯을 승계한다. §2.5에는 대체 참가자 규정도 있다.
§2.2는5인+선택1인 roster, qualifier 당시 선발 다수·코칭스태프 유지 조건을 둔다.

**원문 확인 및 V1 정책:** 현재 공식 PDF에도 pool1은 `LCP#2`다. 관행상 `LCP#1`로 고치지 않고
V1에서 그대로 적용한다. §2.5.4의 타이틀 방어팀 부재 시 최상위 미진출 LCK Road 팀 대체 조항을
초기 슬롯 정책의 근거로 사용한다. 예선은 등록 팀과 국내 성적 입력을 이용한 명시적 임시 선정이며
공식 예선 결과라는 의미가 아니다. `LCP#2`를 바꿀 공식 정정이 확보되면 별도 규칙 버전으로
다뤄야 한다. 실제 qualifier 전체 및 선수 교체 자격 규정은 후속 범위다.
EWC 결과는 Riot Worlds 진출 보너스로 연결하지 않는다.

## KeSPA: 2025 템플릿과 2026 원문을 분리

| 항목 | 2025 template 근거 C9 | 2026 공식 발표 C10–C12 |
|---|---|---|
| 참가 | 14슬롯: LCK10+해외올스타2+해외클럽2 | LCK10; 최대10인 통합 roster. 실제 등록 선수명단은 별도 |
| 예선 | 5/5/4팀 그룹 내 단일RR, 26 BO1; 각1위 직행/2위 LCQ | 5팀×2그룹 **상대 그룹과** 단일RR, 25경기; 각상위4팀 진출 |
| 중간 단계 | 3팀 LCQ 단일RR 3 BO3→1팀 | stage1 8팀, 같은 예선순위끼리 시작하는 변형 Swiss, 4라운드→4팀 |
| 최종 단계 | 4팀6경기 DE; 첫2+하위첫경기 BO3, 나머지 BO5 | stage2 3 BO5; 첫경기승자→상위 진출팀과2R→그 승자와 직행팀 결승 |
| 날짜 | 2025 대회 전용 | 예선7/20·21·27; stage1 7/28–8/10; stage2 8/11·17·18 |

2026 그룹은 A=`GEN,DK,NS,DNS,KRX`, B=`HLE,T1,BRO,BFX,KT`로 발표됐다.
이 표는 발표의 팀 identity를 로컬 stable code에 대응한 문서 표기이며 새 bootstrap 승인이 아니다.
6월 발표의 단순 “싱글 라운드 로빈”을 그룹 **내** 경기로 추정하지 않았다. 7월 원문이 cross-group을
명확히 한다. [C11](https://www.e-sports.or.kr/news/media_view?brd_id=3BT9UH2J0001).

8월 발표는 실제 4팀을 사용해 stage2의 계단식 경로를 보여준다.
그러나 **stage1의 각 경기 승자·패자 이동과 seed 산출을 모든 가능한 결과에 대해 정의한 규정**은
그 발표에 없다. 실제 대진/우승 결과에서 일반 규칙을 역산하지 않는다.
[C12](https://www.e-sports.or.kr/news/media_view?brd_id=3BTO400G0001).

후속에 필요한 정확한 자료:

- 2026 규정집의 버전/시행일, stage1 1R–4R 전체 도표, 승자·패자의 진출/탈락·seed 관계.
- 예선/stage1의 BO·Fearless·동률 기준과 재경기·RoFS 규정. 2025 규정을 대용하지 않는다.
- 10팀의 실제 등록 roster·로스터 잠금/교체 시점·선수별 stable ID. LCK 선발5인 catalog만으로
  최대10인 참가 명단과 선발 결정을 증명할 수 없다.
- 2026을 reference로 새 정의를 만들 때 2025 source-gap instance를 보존/승격하는 조건 및
  진행 중 Career의 LCK/R3R4 일정과 겹치는 경기 처리 정책.

현재 `EXTERNAL_PARTICIPANT_ROSTER_AUTHORITY_MISSING`는 **2025 14-slot template**의 상태다.
2026 대회도 해외4팀이 필요하다는 뜻으로 재사용하면 안 된다. 반대로 LCK10팀이라는 발표만으로
현재 로스터 authority와 실행 gate가 충족되는 것도 아니다. S19의 existence-only 메모는 새 C10–C12보다
좁은 과거 조사 결과이며, 최신 현실의 규칙 미발표를 의미하지 않는다.

## 외부 실행 데이터와 제품 선택

외부 팀명·지역 슬롯·선수 catalog·실행 snapshot은 각각 다른 authority다.
국제 실행 V1은 아래 경계로 구현했으며 실제 해외 리그 결과를 만들었다고 표시하지 않는다.

| 층 | V1 반영 | 남은 범위 |
|---|---|---|
| 참가 자격 | 실제 국내 결과 + 교체 가능한 해외 주전 능력치 순위·정책/입력 저장 | 실제 해외 리그/예선 결과 공급자 |
| 팀·선수 identity | 보유 6지역 56팀/280명, 구조화된 TeamKey/stable player ID | 후보·교체 선수 등록 |
| 대회 roster·능력치 | 대회별 주전5인·rating/proficiency 전체를 고정 | 공식 등록/교체 자격 전체 |
| frozen 실행 입력 | 기존 Production V9 Player/Auto binding·checkpoint·receipt에 고정 두 팀 입력 | 엔진 버전 간 이력 마이그레이션 |
| 진출 output | FST/MSI 게임 성과, Worlds 보너스와 실제 국내 PO 조건 | 다음 시즌 생성 |

최초 pool, counterfactual 지역 성과, 유한 추첨·불가능 제약 완화, 대회 창 내 경기 날짜,
EWC 첫 cycle 슬롯은 버전 있는 게임 정책으로 채택했다. KeSPA의 reference 전환은 후속 범위이며,
아시안게임은 사용자 요청에 따라 명시적으로 제외했다. 일반 미구현 대회 skip 스위치는 없다.

## 구현 반영과 남은 작업

1. **국내 순위 authority 교정 — 구현 반영**: V3에서 Cup SoV/승리시간/통합 seed, R1/R2·R3/R4 H2H,
   다자간 추가 경기·resume·저장 버전 처리를 연결했다.
2. **시즌 말 LCK Playoffs — 구현 반영**: 10경기 graph·선택권·별도 픽/진영 정책·최종1–10위 봉인과
   국제 증거 대기 Worlds 입력을 연결했다. 검증 결과는 [구현 보고서](career-domestic-ranking-playoffs-finalization-v1.md)를 따른다.
3. **First Stand — 국제 V1 반영**: 고정 참가/로스터·13 BO5·지역 성과와 Calendar 실행을 연결했다.
4. **MSI → Worlds — 국제 V1 반영**: FST 성과를 MSI 직행/pool에, MSI 성과와 국내 PO를 Worlds
   슬롯에 연결했다. MSI 20 BO5와 Worlds Play-in/Swiss/KO·최종 성적을 저장한다.
5. **EWC — 국제 V1 반영**: 원문 LCP#2·일반 슬롯 우선의 첫 cycle 타이틀 대체 정책,
   혼합 BO·3위전 포함 28시리즈와 독립 결과를 연결했다.
6. **KeSPA 2026 — 후속**: 미결 규정/등록 roster → 연도별 resource·일정/저장 정책 → 실행.

국제대회 구현과 검증의 정확한 범위는 [국제 V1 보고서](career-international-fst-msi-ewc-worlds-execution-v1.md)를
따른다. 이번 구현은 같은 시즌 Worlds까지이며 rollover·다년 운영 완료를 의미하지 않는다.
