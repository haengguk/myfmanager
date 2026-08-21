package com.lolfm.draft;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class RolePoolCompressionGateProbeTest {
    @Test
    void reportProbeUsesLegalCompletableStateAndProductionBanEvaluator() {
        RolePoolCompressionGateProbe.Result result =
                new RolePoolCompressionGateProbe(DraftResourceSet.loadDefault()).run();
        assertThat(result.stateLegal()).isTrue();
        assertThat(result.stateCompletable()).isTrue();
        assertThat(result.depletedRoleCount()).isEqualTo(28);
        assertThat(result.evaluatorClass()).isEqualTo(BanEvaluator.class.getName());
        assertThat(result.positiveCandidates()).isNotEmpty();
        assertThat(result.positiveCandidates()).allSatisfy(value ->
                assertThat(value.componentValue()).isEqualTo(value.evaluation().components()
                        .get(BanScoreComponent.ROLE_POOL_COMPRESSION)));
    }

    @Test
    void directRolePressureExceedsUnrelatedHealthyRolePressure() {
        RolePoolCompressionGateProbe.Result result =
                new RolePoolCompressionGateProbe(DraftResourceSet.loadDefault()).run();

        assertThat(result.directRolePressureCandidate()).isNotNull();
        assertThat(result.unrelatedHealthyRoleCandidate()).isNotNull();
        assertThat(result.directRolePressureCandidate().componentValue()).isGreaterThan(0.0);
        assertThat(result.directRolePressureCandidate().componentValue())
                .isGreaterThan(result.unrelatedHealthyRoleCandidate().componentValue());
        assertThat(result.directional()).isTrue();
    }

    @Test
    void rolePoolCompressionProbeDoesNotModifyProductionSearchBoundsOrWeights() {
        DraftScoringPolicy policy = DraftScoringPolicy.standard();

        assertThat(policy.candidateLimit()).isEqualTo(12);
        assertThat(policy.structuralRepairSlots()).isEqualTo(4);
        assertThat(policy.searchDepth()).isEqualTo(3);
        assertThat(policy.beamWidth()).isEqualTo(2);
        assertThat(policy.banWeights().get(BanScoreComponent.ROLE_POOL_COMPRESSION)).isEqualTo(0.45);
    }
}
