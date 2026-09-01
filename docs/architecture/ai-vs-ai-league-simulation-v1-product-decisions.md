# AI vs AI League Simulation V1 Product Decisions

상태: `AI_VS_AI_LEAGUE_SIMULATION_V1_PRODUCT_DECISIONS_FROZEN`

이 문서는 Hybrid Season V1 구현 전에 필요한 제품 결정을 canonical 순서로 동결한다. Aggregate, 상태 기계, API/frontend handoff와 구현 순서는 [AI vs AI League Simulation V1 Contract Sketch](ai-vs-ai-league-simulation-v1-contract-sketch.md)를 따른다.

문서 값은 구현 입력이며 runtime authority가 아니다. Batch 1에서 versioned code-owned policy, canonical serialization과 hash를 구현한 뒤 실제 Season snapshot이 그 identity를 소유한다.

## Frozen decision table

| Decision ID | Frozen V1 value | 이유 | Trade-off | V2 escape hatch |
|---|---|---|---|---|
| `AI_LEAGUE_V1_SEASON_MODE` | `HYBRID_MANAGER` 또는 `SPECTATOR_FULL_AUTO`. Hybrid는 정확히 한 immutable managed team, Spectator는 `managedTeamCode=null` | 한 Season에서 player authority와 full-auto authority를 명확히 분리한다 | Season 도중 관리 팀을 바꿀 수 없다 | 새 Season 생성 또는 versioned multi-manager mode |
| `AI_LEAGUE_V1_MANAGED_FIXTURE_POLICY` | 관리 팀 포함 fixture는 전부 `PLAYER_CONTROLLED`, 나머지는 `FULL_AUTO`. AI batch/lease가 managed fixture를 실행하는 기능은 없음 | player 경기의 seed/Draft/결과 권위를 AI batch가 우회하지 못하게 한다 | player가 진행하지 않으면 round가 기다린다 | 별도 동의·감사 가능한 `DELEGATE_MANAGED_FIXTURE_TO_AI_V2` |
| `AI_LEAGUE_V1_ROSTER_SNAPSHOT` | Season freeze 시 team set, roster, PlayerId, rating/proficiency, Champion/Draft/Matchup/Composition resource와 policy/profile/config/rules/engine identity를 고정. 현재 LCK V1은 정확히 10팀/50명 | 장기 Season 도중 authored resource 변경이 과거/미래 fixture 의미를 바꾸지 않게 한다 | snapshot 저장량과 migration 비용이 생긴다 | transfer window를 별도 snapshot epoch로 모델링 |
| `AI_LEAGUE_V1_SCHEDULE_FORMAT` | production default는 10팀 double round robin 90 fixtures BO3. 같은 pair의 두 leg는 Game 1 side mirror, game마다 side 교대. Single round robin은 설계 가능, `CUSTOM`/side imbalance는 제외 | 균형, 예측 가능한 workload와 deterministic fixture identity를 확보한다 | 사용자 정의 대진을 지원하지 않는다 | versioned custom schedule validator와 explicit imbalance acceptance |
| `AI_LEAGUE_V1_STANDINGS_POLICY` | 승 1점/패 0점/무승부 없음. tie: Series wins → game differential → game wins → mini-league Series wins → mini-league game differential → Season-seed draw | receipt만으로 재계산 가능한 closed policy를 만든다 | 스포츠별 승점제나 별도 결정전을 지원하지 않는다 | versioned standings policy 및 playoff/tie-break fixture |
| `AI_LEAGUE_V1_BLOCKED_FIXTURE_POLICY` | 검증 실패 시 fixture와 Season `BLOCKED`, 기존 결과 보존. 자동 승점/몰수/무승부/규칙 완화/tuning/reseed 없음 | 무결성 실패를 결과로 위장하지 않는다 | 수동 운영 판단 전 Season이 멈춘다 | audited admin resolution command와 새 policy version |
| `AI_LEAGUE_V1_EXECUTION_LIMITS` | 기본 병렬 2, hard max 4, lease 15분, heartbeat 15초, transient attempt 최초 포함 최대 2회. Hybrid RUN_ALL은 current-round auto만 dispatch | 로컬 production workload를 제한하고 retry 폭주를 막는다 | throughput 상한이 낮다 | load evidence 기반 versioned limits/profile |
| `AI_LEAGUE_V1_PERSISTENCE_POLICY` | relational persistence, durable binding/receipt/outbox/application ledger. Season/receipt는 Season 삭제까지, attempt log 30일, optional gzip replay cache 24시간 non-authoritative | restart recovery와 exactly-once standings를 보장한다 | DB와 retention 운영 비용이 생긴다 | archival storage, compaction과 configurable retention class |
| `AI_LEAGUE_V1_PLAYER_SERIES_HANDOFF` | League가 frozen binding과 bound Player Series를 만들고, server receipt + transactional outbox + idempotent consumer가 standings를 반영. frontend는 관찰만 함 | browser/network failure와 duplicate completion에서도 authority를 server에 둔다 | completion 직후 짧은 reconciliation 대기가 보일 수 있다 | same-transaction deployment 또는 generalized workflow engine |

## Canonical decision identity

Decision identity schema는 `AI_LEAGUE_V1_PRODUCT_DECISIONS_CANONICAL_SHA256_V1`이다.

### Ordered Decision ID list

1. `AI_LEAGUE_V1_SEASON_MODE`
2. `AI_LEAGUE_V1_MANAGED_FIXTURE_POLICY`
3. `AI_LEAGUE_V1_ROSTER_SNAPSHOT`
4. `AI_LEAGUE_V1_SCHEDULE_FORMAT`
5. `AI_LEAGUE_V1_STANDINGS_POLICY`
6. `AI_LEAGUE_V1_BLOCKED_FIXTURE_POLICY`
7. `AI_LEAGUE_V1_EXECUTION_LIMITS`
8. `AI_LEAGUE_V1_PERSISTENCE_POLICY`
9. `AI_LEAGUE_V1_PLAYER_SERIES_HANDOFF`

### Serialization and hash algorithm

Batch 1 구현은 다음 절차를 exact test로 고정한다.

1. 위 ordered list 순서대로 각 decision의 code-owned canonical value를 가져온다.
2. 각 항목을 UTF-8 `decisionId + "=" + canonicalValue`로 직렬화한다.
3. 항목 사이에는 LF(`0x0A`) 하나를 사용하고 마지막 항목 뒤에는 LF를 넣지 않는다.
4. enum은 위 문서의 uppercase token, 정수는 leading zero 없는 base-10, duration은 정수 seconds, 목록은 해당 policy가 정한 canonical order를 사용한다.
5. 이 byte sequence의 SHA-256 lowercase hex를 `productDecisionHash`로 사용한다.

Markdown 문장, 표의 이유/Trade-off/V2 설명, 공백과 줄바꿈은 hash 입력이 아니다. 실제 canonical value 문자열은 Batch 1의 immutable policy class와 golden test가 소유한다. 문서와 code-owned policy가 다르면 implementation은 fail-closed하고 상태 문서를 갱신하기 전 제품 결정을 다시 검토한다.

## 변경 규칙

V1 구현이 시작된 뒤 Frozen value를 바꾸려면 다음을 모두 수행한다.

- 기존 Decision ID의 의미를 조용히 바꾸지 않고 새 policy/schema version을 만든다.
- 기존 Season snapshot과 receipt가 이전 hash로 계속 검증되게 한다.
- schedule, seed, standings, binding/receipt 호환성 영향을 문서화한다.
- focused migration/parity test와 필요한 full regression 범위를 별도 milestone에서 정한다.

다음 구현 task는 `AI_VS_AI_LEAGUE_SIMULATION_V1_DOMAIN_SCHEDULE_AND_STANDINGS`다.
