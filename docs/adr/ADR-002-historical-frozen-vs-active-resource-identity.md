# ADR-002: Historical Frozen vs Active Resource Identity

Status: Accepted

## Context

Repository에는 initial-30 champion resources와 active full-173 resources가 함께 있다. Matchup/Composition의 일부 frozen production policy는 initial-30 profile의 canonical hash를 포함하지만, active manifest는 173 champions/216 role profiles를 선택한다. 한 hash가 어느 population을 보증하는지 구분하지 않으면 historical regression과 active completeness를 혼동하게 된다.

## Decision

- `initial-30-*` resources와 코드의 30-profile catalogs는 historical semantic oracle로 유지한다.
- active runtime resource selection은 manifest의 full resource version을 따른다.
- full population integration test가 historical subset의 값이 active set에서 보존되는지 검증한다.
- historical frozen hash는 해당 30-profile/candidate/rule scope만 attest한다.
- active full identity는 manifest/catalog/profile versions, exact legal-role coverage, full canonical hash가 있는 catalog의 별도 hash로 검증한다.

## Consequences

- full resource에 champion을 추가해도 historical expected hash를 변경할 필요가 없다.
- active full dataset 전체가 바뀌었는지는 historical hash 하나로 판단할 수 없다.
- test는 “frozen subset 보존”과 “active full completeness/identity”를 별도로 유지해야 한다.
- 문서와 diagnostics는 hash를 제시할 때 scope를 함께 표기해야 한다.

## Invariants

- active population 확장을 위해 initial-30 resource를 수정하지 않는다.
- historical hash를 full-173 hash라고 부르지 않는다.
- historical subset 값이 의도 없이 active full resource에서 drift하면 regression이다.
- active manifest가 가리키지 않는 historical resource를 production runtime source로 설명하지 않는다.
