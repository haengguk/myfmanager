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
}
