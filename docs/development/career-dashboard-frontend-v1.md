# Career Dashboard Frontend V1

## 결과와 제품 경계

`CAREER_DASHBOARD_FRONTEND_V1`은 durable Career 저장을 실제 앱에서 만들고, 목록과 상세를 확인한
뒤 기존 League 또는 Player Series 흐름으로 이어서 플레이하는 작업면이다. 화면은 보강된 structured
Career API만 사용한다. Career가 League/Series 데이터를 복사하거나 새로운 gameplay authority가 되지
않는다.

```text
커리어 메뉴
→ 저장 목록 또는 새 커리어
→ 실제 LCK 팀 + 감독 이름 + 저장 이름
→ server-owned Career와 Hybrid Season 생성
→ 상세와 authoritative resume 확인
→ 기존 League / Player Series
→ League에서 Career로 복귀
```

Career가 저장하는 값은 Career ID, 저장/감독 이름, 관리 팀 code, 시작/현재 게임 날짜, root seed와
algorithm, linked League/Season ID, frozen/product/reference/binding identity, Career lifecycle/revision,
`createdAt`/`updatedAt`이다. Fixtures/current round/standings, jobs/leases/retries, Player Series/Draft,
Hard Fearless, receipts/outbox와 Match 결과/timeline은 계속 League/Series가 소유한다.

후속 `CAREER_TIME_AND_CALENDAR_PROGRESSION_V1`에서 `currentDate`와 Calendar view/advance를
additive하게 연결했다. 다음 시즌 자동 생성, 이적, 훈련, 재정, 감독 능력치, 구단 예산, Career
삭제/archive는 여전히 구현하지 않았다. Calendar 계약은
[Career Time and Calendar Progression V1](career-time-and-calendar-progression-v1.md)을 따른다.

## 시각·콘텐츠·상호작용 기준

- Visual thesis: 기존 어두운 구단 운영실 안에서 저장 슬롯과 현재 시즌 문맥을 한눈에 읽는
  divider 기반 고밀도 화면이다.
- Content plan: 저장 선택 → Career identity → 시즌 현재 상태 → authoritative 이어하기 순으로 읽는다.
- Interaction thesis: 140ms 안팎의 목록 선택 전환, 생성 성공 후 상세 제목로 focus 이동, 서버가 준
  resume/allowedCommands에 따른 단일 primary 전환을 사용한다.

Repository의 Pretendard Variable, carbon-black surface, 112px rail, 62px header, 얇은 divider, teal
primary, 4px/7px radius를 그대로 따른다. OpenDesign의 competition/squad overview와 brand spec은 정보
위계, 목록/상세 비율, typography와 spacing만 읽기 전용으로 참고했다. 새 generation, cloud credit와
원본 변경은 없었다.

왼쪽은 검색 가능한 저장 슬롯 목록, 중앙은 선택한 Career identity와 linked Season 상태, 오른쪽은
resume 문맥과 다음 행동이다. 거대한 카드 grid, marketing hero, 장식 통계, 가짜 FM 데이터와 미구현
기능 placeholder는 두지 않는다. `updatedAt`은 “운영 저장 시각”으로 표시해 마지막 경기 시각으로
오해시키지 않는다.

## API와 fail-closed validation

Feature boundary는 `frontend/src/features/career` 아래 API types, strict validator, failure mapper,
client, pointer, route adapter와 UI로 나뉜다. 다음 실제 API를 사용한다.

- `GET /api/v1/careers`
- `GET /api/v1/careers/{careerId}`
- `POST /api/v1/careers`
- Team/Player Information의 LCK team list
- 기존 League/Player Series GET과 command API

Validator는 unknown/missing/wrong-type field, schema, canonical identity, timestamp/date, signed-long seed,
hash, list ordering과 다음 cross-field를 fail-closed한다.

- `maximumCount == 100`
- `0 <= currentCount <= maximumCount`
- `remainingCount == maximumCount - currentCount`
- list length와 `currentCount`의 일치
- resume kind와 nullable fixture/series scope의 일치
- resume kind와 `allowedCommands`의 일치

Team options와 Career의 team code/reference catalog version/hash도 실제 Team/Player API 결과에 맞아야
한다. Backend의 raw stack, SQL, filesystem path는 화면에 전달하지 않는다. Request generation과
`AbortController`로 이전 선택의 늦은 응답이 최신 상세를 덮지 못하게 한다.

## 생성 idempotency와 작은 browser pointer

Create form은 trim/NFC 정규화한 저장 이름, 감독 이름, exact team code로 논리 작업 fingerprint를
만든다. 동일 payload의 TIMEOUT/network/503/response-loss 재시도는 같은 `clientCommandId`를 사용한다.
Payload가 바뀌면 새 logical operation과 UUID를 사용한다. 성공과 명확한 conflict/capacity rejection
뒤 pending operation을 지우며 HTTP 200 `replayed=true`도 정상 성공이다.

Session storage에는 다음 navigation 정보만 둔다.

- active Career ID
- pending create의 정규화 payload fingerprint와 UUID
- Career에서 League/Series로 들어간 return context

Career list/detail, standings, fixtures, resume/allowedCommands, Series 상태와 server timestamp는 저장하지
않는다. Reload는 active ID를 Career GET으로 다시 검증한다. Not found이면 pointer를 제거하고 목록으로
돌아가며 network/503이면 유지하고 retry를 제공한다. Integrity 500은 pointer를 유지하지만 정상처럼
cached view를 표시하지 않고 지원이 필요한 상태로 보여 준다.

## Authoritative handoff와 복귀

Resume adapter는 structured `kind`, IDs와 `allowedCommands`만 사용한다.

| Resume kind | 동작 |
| --- | --- |
| `LEAGUE_DASHBOARD` | linked League/Season pointer를 설정하고 기존 League GET으로 재확인 |
| `PLAYER_SERIES` | fixture/series/command를 검증하고 기존 League fixture와 Series restore pipeline 사용 |
| `SEASON_COMPLETE` | 완료 상태의 기존 League dashboard만 열기 |
| `ATTENTION_REQUIRED` | 기존 League dashboard에서 blocker와 authoritative command 확인 |

Career에서 새 League/Series를 생성하지 않는다. Player handoff에서도 public standalone Series create를
호출하지 않고 Hard Fearless, side, seed, score를 frontend에서 재계산하지 않는다. Career-bound League는
`새 시즌`을 숨기고 `Career로 돌아가기`를 제공한다. Series의 기존 `AI 리그로 돌아가기`는 보존돼
Series → League → Career 순으로 돌아온다. Standalone League는 기존 생성/복원 흐름을 유지한다.

## 상태, 접근성, 반응형

UI는 empty/list loading/detail loading/create pending, validation/network/load error, retry, capacity full,
Season complete와 attention required를 별도 상태로 표현한다. 목록/상세/form은 semantic element와 label을
사용하고 선택 row에는 `aria-current`, loading에는 polite live region, 오류에는 alert semantics가 있다.

Create dialog는 초기 focus, Tab/Shift+Tab trap, Escape close와 실제로 눌렀던 trigger focus 복귀를
지원한다. Pending 중 중복 제출을 막고 모든 interactive element에 2px focus outline을 유지한다.
Reduced-motion에서도 기능과 focus 순서는 동일하다.

1440×900과 1280×720에서는 body horizontal overflow 없이 primary action과 오류가 보인다. 좁은 폭에서는
3-column workspace가 읽을 수 있는 stack/내부 scroll 구조로 바뀐다.

## 검증 결과와 보존 사항

Backend focused test는 expired reservation 전후 전체 DB snapshot equality, PAUSED/VERIFIED resume,
capacity 1의 create/replay/rejection, receipt schema/target tamper와 file-H2 restart를 통과했다. Career
frontend verifier 8개, 기존 League handoff verifier, lazy bundle과 production build도 통과했다.

실제 isolated file-H2/browser에서는 빈 화면에서 GEN Career를 HTTP 201로 만든 뒤 목록/상세,
Career→League→Career, reload 시 같은 Career GET 복구를 확인했다. Dialog keyboard, 두 viewport의 overflow,
console/runtime validation도 clean이었다. Mock/service-worker/response rewriting과 사용자 runtime DB는
사용하지 않았다.

Production V9, Matchup/Composition, Jungle Economy/Tempo 상태, Draft/Random, League schedule/seed/
standings, Hard Fearless/Series lifecycle, 기존 Real Match/Player Draft/Series/Team Information API와
Career identity/binding hash는 변경하지 않았다. 90경기 전체 Season, Player BO3, balance/holdout와
대형 diagnostic은 실행하지 않았다.

다음 단계는 `CAREER_COMPETITION_LIFECYCLE_V1`이다.
