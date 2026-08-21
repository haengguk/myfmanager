# Pre-Jungle Runtime Baseline V1

이 디렉터리는 explicit simulation runtime profile/provenance 구현을 마친 뒤, Jungle Clear gameplay를 simulator에 연결하기 전에 생성한 reference artifact다.

## Generation gate

생성 순서는 다음과 같이 고정했다.

1. profile/provenance production source와 focused tests를 완성한다.
2. final backend full regression을 1회 실행해 clean pass를 확인한다.
3. production source/resource/build guard hash가 full regression 전후 같은지 확인한다.
4. 그 뒤에만 `generatePreJungleRuntimeBaseline`을 실행한다.

이번 artifact의 full regression은 151 suites / 1,965 tests / failures 0 / errors 0 / skipped 0으로 통과했다. Baseline JSON의 `fullRegressionStatus`와 `verificationSequence`가 이 생성 전제를 기록한다.

## Contents

- `pre-jungle-runtime-baseline-v1.json`: 3 profiles의 resolved semantics, resource provenance와 9개 fixed real Draft→Match 결과
- `SHA256SUMS.txt`: JSON raw-byte SHA-256
- build report mirror: `backend/build/reports/pre-jungle-runtime-baseline/`

JSON SHA-256은 `2dcf67a3501200f0bce3de6239dcfbed3b27bafdc9287940f3f56171223a1d71`이다. Production source tree identity는 453 files, `7373bd094d8c638853988e809447718ec65ee8630b9d6168a5f57b9c07564d76`이며 source revision은 `68a5f38802256e37ded082c1333a78216948576d`다. Revision은 uncommitted Batch A 변경 전체를 표현하지 않으므로 production tree hash가 exact local source identity를 보완한다.

## Fixed schedule

각 profile은 같은 세 case를 실행한다.

- GEN–T1 Hard Fearless game 1, seed 73
- 같은 caller-owned series의 GEN–T1 game 2, seed 74
- fresh-history T1–GEN mirror game 1, seed 73

총 3 profiles × 3 cases = 9 matches다. 각 row에는 configuration, series-history-before, draft decision/final draft/final assignment, replay provenance와 complete timeline hash가 있다.

## Scope

이 baseline에서 Jungle Clear contribution은 모든 profile에 대해 `DISABLED_NOT_INTEGRATED`이고 authored `gameplayEnabled` profile count도 0이다. Jungle economy/XP, clear pathing, readiness/tempo, gank/objective eligibility는 포함하지 않는다.

이 artifact는 다음 Jungle milestone과의 비교 기준이며 현재 normal correctness test의 expected fixture는 아니다. 내용을 갱신하려면 원인을 설명하는 새 version과 동일한 focused → full regression → generation 순서가 필요하다.
