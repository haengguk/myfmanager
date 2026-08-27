package com.lolfm.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.lolfm.champion.ChampionCatalog;
import com.lolfm.composition.ChampionCompositionProfileCatalog;
import com.lolfm.player.ChampionProficiencyCatalog;
import com.lolfm.player.LckTeamAssembler;
import com.lolfm.simulator.SimulationRuntimeProfileId;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class MatchEngineV9ProductionAcceptanceContractTest {
    @Autowired ChampionCatalog champions;
    @Autowired LckTeamAssembler teams;
    @Autowired ChampionProficiencyCatalog proficiencies;

    @Test
    void fixedLineupsArchetypesProficienciesAndScheduleAreFrozenAndClean() {
        var preflight = MatchEngineV9ProductionAcceptanceContract.preflight(
                champions, teams, proficiencies,
                ChampionCompositionProfileCatalog.loadDefault());

        assertThat(preflight.clean()).isTrue();
        assertThat(preflight.illegalRoleCount()).isZero();
        assertThat(preflight.duplicatePickErrorCount()).isZero();
        assertThat(preflight.authoredProficiencyBindingCount()).isEqualTo(30);
        assertThat(preflight.neutralFallbackCount()).isZero();
        assertThat(preflight.identityMismatchCount()).isZero();
        assertThat(preflight.bindings()).hasSize(30).allMatch(value -> value.authored()
                && value.proficiency() != null);
        assertThat(preflight.archetypeScores().pokeSiegeReadiness()).isGreaterThanOrEqualTo(0.85);
        assertThat(preflight.archetypeScores().engageChainReadiness()).isGreaterThanOrEqualTo(0.90);
        assertThat(preflight.archetypeScores().counterDisengageCoverage())
                .isGreaterThanOrEqualTo(0.95);
        assertThat(preflight.archetypeScores().counterPeelCoverage())
                .isGreaterThanOrEqualTo(0.95);
        assertThat(preflight.archetypeScores().counterFrontlineCoverage())
                .isGreaterThanOrEqualTo(0.90);
        assertThat(preflight.archetypeScores().counterWaveClearCoverage())
                .isGreaterThanOrEqualTo(0.90);

        var schedule = MatchEngineV9ProductionAcceptanceContract.schedule();
        assertThat(schedule).hasSize(1_200);
        assertThat(schedule).extracting(value -> value.profileId()).containsOnly(
                SimulationRuntimeProfileId.BASELINE_V1,
                SimulationRuntimeProfileId.MATCHUP_ONLY_CANDIDATE_V1,
                SimulationRuntimeProfileId.PRODUCTION_MATCHUP_COMPOSITION_V1);
        assertThat(schedule.stream().map(value -> value.seed()).distinct()).hasSize(50);
        assertThat(MatchEngineV9ProductionAcceptanceContract.seed(0)).isEqualTo(9_270_001L);
        assertThat(MatchEngineV9ProductionAcceptanceContract.seed(49))
                .isEqualTo(9_270_001L + 104_729L * 49);
        assertThat(MatchEngineV9ProductionAcceptanceContract.ORIENTATIONS)
                .extracting(value -> value.id()).containsExactly(
                        "T1_ARCHETYPE_BLUE__GEN_COUNTER_RED",
                        "GEN_COUNTER_BLUE__T1_ARCHETYPE_RED",
                        "GEN_ARCHETYPE_BLUE__T1_COUNTER_RED",
                        "T1_COUNTER_BLUE__GEN_ARCHETYPE_RED");
    }
}
