package com.lolfm.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lolfm.champion.ChampionCatalog;
import com.lolfm.player.ChampionProficiencyCatalog;
import com.lolfm.player.LckTeamAssembler;
import com.lolfm.simulator.ConfiguredMatchSimulatorFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class MatchEngineV9ProductionAcceptanceRollbackOracleTest {
    @Autowired ObjectMapper mapper;
    @Autowired ChampionCatalog champions;
    @Autowired LckTeamAssembler teams;
    @Autowired ChampionProficiencyCatalog proficiencies;
    @Autowired ConfiguredMatchSimulatorFactory simulators;
    @Autowired SimulationProvenanceService provenance;

    @Test
    void explicitBaselineReplaysAreExactWithoutAutomaticFallbackOrCrossCommitClaim()
            throws Exception {
        var oracle = new MatchEngineV9ProductionAcceptanceRunner(
                mapper, champions, teams, proficiencies, simulators, provenance)
                .verifyRollbackOracle();
        assertThat(oracle.clean()).isTrue();
        assertThat(oracle.automaticFallback()).isFalse();
        assertThat(oracle.rollbackMode()).isEqualTo("EXPLICIT_VERSIONED_POLICY_CHANGE_ONLY");
        assertThat(oracle.fixtures()).hasSize(2).allMatch(value -> value.exact()
                && value.profileId().equals("BASELINE_V1")
                && value.firstFullTimelineByteHash().equals(
                value.secondFullTimelineByteHash())
                && value.firstOutputHash().equals(value.secondOutputHash()));
        assertThat(oracle.crossCommitClaim())
                .isEqualTo("NO_CROSS_COMMIT_BYTE_PARITY_CLAIM_WITHOUT_MATCHING_PRE_ACTIVATION_ORACLE");
    }
}
