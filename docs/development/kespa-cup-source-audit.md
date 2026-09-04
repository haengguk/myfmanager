# LoL KeSPA Cup source audit and integration status

## 결론

상태는 `KESPA_CUP_2026_RULE_SOURCE_INCOMPLETE`다. 2026 대회 존재는 공식 source로 확인했지만
2026 규정/대진/참가자/exact 일정은 확인하지 못했다. 최신 완전 공식 규칙인 2025 문서는
`KESPA_CUP_REFERENCE_TEMPLATE_2025`로만 보존하며 2026 또는 미래 Career 실행 규칙으로 승격하지
않는다.

## 채택 source

| ID | 발행처 | 문서 | reference year | 용도 |
| --- | --- | --- | ---: | --- |
| S17 | 한국e스포츠협회 | [2025 LoL KeSPA CUP 규정 공시](https://www.a-esc.com/user2/board/view/board_cd/notice/wr_no/33) | 2025 | 일정·stage·roster 규모 참고 |
| S18 | 한국e스포츠협회 | [2025 KeSPA CUP 공식 규정집 V1.1](https://www.a-esc.com/site_data/file/notice/2025/12/1765502569_2ALOHemk_28EAB5ADEB.pdf) | 2025 | 14 slot, group/LCQ/final 구조 참고 |
| S19 | 학교 이스포츠 / KeSPA | [2026 LoL KeSPA CUP 개막일 맞히기 이벤트](https://school.e-sports.or.kr/notice/view/1065) | 2026 | 2026 대회 존재만 확인 |

S18 raw SHA-256은
`574ee7d8993b07a073e3ecc18f96227bbff48dc886ce4b336a61f5d8f4f8542a`다. S17/S18은
`REFERENCE_TEMPLATE_ONLY`, S19의 상세 규칙 범위는 `SOURCE_INCOMPLETE`다.

## 참고 템플릿과 실행 경계

2025 공식 템플릿에는 14팀 group stage 26 Bo1, 3팀 LCQ 3 Bo3, 4팀 final stage 6경기 double
elimination과 Bo3/Bo5 구분이 있다. 이는 화면 설명과 향후 source 비교에는 사용할 수 있다.

현재 2026 roster authority가 없으므로 LCK slot 10개도 참가 확정으로 resolve하지 않는다. 해외
올스타 2 slot과 외국 팀 2 slot 역시 stable team identity가 없다. 따라서
`EXTERNAL_PARTICIPANT_AUTHORITY_MISSING`이며 unresolved slot 14, executable fixture 0, resolved team
0, imported result 0이다. 가짜 팀 code, 평균 능력치, LCK 대체 팀 또는 Random 배정은 없다.

Calendar/API는 event별 `sourceReferenceYear=2025`,
`REFERENCE_TEMPLATE_NOT_OFFICIAL_FOR_2026_OR_FUTURE`, template ID와 다음 blocker를 additive하게
반환한다.

- `KESPA_CUP_2026_RULE_SOURCE_INCOMPLETE`
- `EXTERNAL_PARTICIPANT_ROSTER_AUTHORITY_MISSING`

공식 exact 날짜가 없으므로 LCK/EWC/Asian Games window와의 실제 충돌 여부를 확정하지 않는다.
날짜를 조용히 이동하거나 derived policy로 해결하지 않으며, 충돌이 확인되면 별도
`COMPETITION_SCHEDULE_CONFLICT_REQUIRES_PRODUCT_POLICY` 결정이 필요하다.

## 지금 가능한 것 / 차단된 것

가능: 공식 source provenance 표시, 2025 참고 stage 열람, 2026 source gap과 external roster gap의
machine-readable 노출, 이후 source 교체를 위한 versioned resource 검증.

차단: 2026 참가 팀 확정, fixture materialization, Series/Match 실행, qualification/result 생성,
미래 year 자동 투영. 이 범위는 2026 공식 규정과 roster authority를 확보한 뒤
`CAREER_COMPETITION_RULE_SOURCE_CLOSURE_V1`에서 재검토한다.
