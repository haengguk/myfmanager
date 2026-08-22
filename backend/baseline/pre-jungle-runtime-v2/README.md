# Pre-Jungle Runtime Baseline V2

이 디렉터리는 Jungle Clear gameplay production 연결 전의 공식 runtime/replay oracle이다. V1 raw artifact는 보존하지만 cross-JVM timeline oracle은 이 V2가 대체한다.

## Why V2 supersedes V1

V1 생성 뒤 별도 JVM에서 같은 source/resource/seed를 재생성했을 때 두 production ordering 결함이 드러났다.

- `Set.copyOf(EnumSet)` iteration이 player skill별 seeded realization draw의 배정을 바꿨다.
- `ChampionPowerProfile.tags`의 unordered iteration이 4-tag summary와 timeline serialization을 바꿨다.

V1 JSON의 checksum은 유효하며 당시 한 실행의 immutable 기록이다. 그러나 cross-process replay oracle로 사용하지 않는다. V2 active rules는 `MATCH_SIMULATOR_PRE_JUNGLE_RULES_V2`이고 PlayerSkill 처리와 timeline enum set을 declaration order로 고정한다.

## Generation gate

생성 순서는 다음과 같다.

1. production/test hardening과 focused tests를 완료한다.
2. 별도 JVM 후보 두 번이 문서와 9개 canonical timeline에서 exact equality인지 확인한다.
3. final backend full regression과 production source guard를 clean pass한다.
4. 그 뒤에만 `generatePreJungleRuntimeBaselineV2`를 실행한다.
5. 새 JVM에서 같은 명령을 다시 실행해 immutable source bytes와 exact equality를 확인한다.

Final full regression은 156 suites / 1,978 tests / failures 0 / errors 0 / skipped 0, Gradle 22분 19초로 통과했다.

## Contents and identity

- `pre-jungle-runtime-baseline-v2.json`: 세 profile의 resolved semantics, resource provenance, engine/rules identity와 9개 fixed real Draft→Match 결과
- `SHA256SUMS.txt`: JSON raw-byte SHA-256
- build report mirror: `backend/build/reports/pre-jungle-runtime-baseline-v2/`

JSON은 Git line-ending 변환을 금지한 canonical CRLF raw bytes이며 SHA-256은 `0bce126117683e47ace908c348dbe2448f21592dc5009bd9f4514bb566fadb8e`다. Production source identity는 456 files / `b7965a1d1ebb9d76f298bc65e957da79c4e7cf2a3d0df35a6eca29ebaa0ab350`이다. Source revision은 `8a55664955fad82d037ca9286be6bdb050b029fe`에 이 exact working-tree hash를 결합해 기록했다.

## Fixed schedule and contract

Profile 목록은 다음 세 개로 고정되어 future enum profile을 자동 포함하지 않는다.

- `BASELINE_V1`: Matchup `OFF`, Composition `OFF`
- `MATCHUP_ONLY_CANDIDATE_V1`: Matchup `GEOMETRIC_V2`, Composition `OFF`
- `FULL_SYSTEM_CANDIDATE_V1`: Matchup `GEOMETRIC_V2`, Composition `PRODUCTION_V2`

각 profile은 GEN–T1 Hard Fearless game 1(seed 73), 같은 series game 2(seed 74), fresh-history T1–GEN mirror game 1(seed 73)을 실행한다. 총 9개 row는 configuration/replay/timeline hash와 Random draw count/ordered trace hash를 함께 가진다.

모든 profile의 Jungle Clear contribution은 `DISABLED_NOT_INTEGRATED`이고 authored gameplay-enabled profile count는 0이다. Jungle economy/XP, clear pathing, readiness/tempo, gank/objective eligibility는 포함하지 않는다.

Existing source artifact와 새 candidate bytes가 다르면 generator는 source를 덮어쓰지 않고 실패한다. 이 artifact는 다음 Jungle milestone의 paired before snapshot이며 normal correctness test의 expected fixture는 아니다.
