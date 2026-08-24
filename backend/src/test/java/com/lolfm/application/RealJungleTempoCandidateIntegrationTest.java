package com.lolfm.application;

import static com.lolfm.testing.CompleteTimelineAssertions.assertCompleteTimelineEquals;
import static org.assertj.core.api.Assertions.assertThat;

import com.lolfm.simulator.SimulationRuntimeProfileId;
import com.lolfm.simulator.SimulationRuntimeProfiles;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/** Real-data smoke for Draft -> Jungle Economy -> bounded gank-tempo runtime. */
@SpringBootTest
class RealJungleTempoCandidateIntegrationTest {
    private static final long MATCH_SEED = 73L;

    @Autowired RealDraftMatchOrchestrator orchestrator;

    @Test
    void realGenT1TempoCandidateIsReachableDeterministicAndDistinctFromEconomyOnly() {
        RealDraftMatchResult economyOnly = orchestrator.orchestrate(
                "GEN", "T1", MATCH_SEED,
                SimulationRuntimeProfileId.FULL_SYSTEM_WITH_JUNGLE_ECONOMY_CANDIDATE_V1);
        RealDraftMatchResult candidate = orchestrator.orchestrate(
                "GEN", "T1", MATCH_SEED,
                SimulationRuntimeProfileId.FULL_SYSTEM_WITH_JUNGLE_TEMPO_CANDIDATE_V1);
        RealDraftMatchResult replay = orchestrator.orchestrate(
                "GEN", "T1", MATCH_SEED,
                SimulationRuntimeProfileId.FULL_SYSTEM_WITH_JUNGLE_TEMPO_CANDIDATE_V1);

        assertThat(candidate.executionProvenance().runtimeProfileId())
                .isEqualTo(SimulationRuntimeProfileId
                        .FULL_SYSTEM_WITH_JUNGLE_TEMPO_CANDIDATE_V1);
        assertThat(candidate.executionProvenance().configurationHash())
                .isEqualTo("c835280cbaa1244f4fecb099b19f71111c6d77aa1aeb1b7110a6e86e6381451c");
        assertThat(candidate.executionProvenance().activeGameplayRulesVersion())
                .isEqualTo(SimulationRuntimeProfiles
                        .JUNGLE_TEMPO_ACTIVE_GAMEPLAY_RULES_VERSION)
                .isEqualTo("MATCH_SIMULATOR_JUNGLE_TEMPO_RULES_V1");
        assertThat(candidate.executionProvenance().engineImplementationVersion())
                .isEqualTo("MATCH_SIMULATOR_ENGINE_IMPLEMENTATION_V8");
        assertThat(candidate.executionProvenance().resourceProvenance()
                .jungleClearGameplayEnabledProfileCount()).isEqualTo(51);

        assertThat(candidate.draftResult().draftIdentity())
                .isEqualTo(economyOnly.draftResult().draftIdentity());
        assertThat(candidate.matchChampionAssignments().asMap())
                .isEqualTo(economyOnly.matchChampionAssignments().asMap());
        assertThat(candidate.playerIdsByMatchSlot())
                .isEqualTo(economyOnly.playerIdsByMatchSlot());
        assertThat(candidate.executionProvenance().timelineHash())
                .isNotEqualTo(economyOnly.executionProvenance().timelineHash());

        assertThat(replay.draftResult().draftIdentity())
                .isEqualTo(candidate.draftResult().draftIdentity());
        assertThat(replay.draftResult().decisions())
                .containsExactlyElementsOf(candidate.draftResult().decisions());
        assertThat(replay.draftResult().blueFinalRoleAssignments())
                .isEqualTo(candidate.draftResult().blueFinalRoleAssignments());
        assertThat(replay.draftResult().redFinalRoleAssignments())
                .isEqualTo(candidate.draftResult().redFinalRoleAssignments());
        assertThat(replay.matchChampionAssignments().asMap())
                .isEqualTo(candidate.matchChampionAssignments().asMap());
        assertThat(replay.playerIdsByMatchSlot())
                .isEqualTo(candidate.playerIdsByMatchSlot());
        assertThat(replay.executionProvenance())
                .isEqualTo(candidate.executionProvenance());
        assertCompleteTimelineEquals(candidate.timeline(), replay.timeline());
    }
}
