package com.lolfm.application;

import static com.lolfm.testing.CompleteTimelineAssertions.assertCompleteTimelineEquals;
import static org.assertj.core.api.Assertions.assertThat;

import com.lolfm.simulator.SimulationRuntimeProfileId;
import com.lolfm.simulator.SimulationRuntimeProfiles;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/** Focused real-data smoke for Draft -> final assignments -> Jungle Economy candidate runtime. */
@SpringBootTest
class RealJungleEconomyCandidateIntegrationTest {
    private static final long MATCH_SEED = 73L;

    @Autowired RealDraftMatchOrchestrator orchestrator;

    @Test
    void realGenT1CandidateIsReachableDeterministicAndDistinctFromJungleOffFull() {
        RealDraftMatchResult jungleOff = orchestrator.orchestrate(
                "GEN", "T1", MATCH_SEED,
                SimulationRuntimeProfileId.FULL_SYSTEM_CANDIDATE_V1);
        RealDraftMatchResult candidate = orchestrator.orchestrate(
                "GEN", "T1", MATCH_SEED,
                SimulationRuntimeProfileId.FULL_SYSTEM_WITH_JUNGLE_ECONOMY_CANDIDATE_V1);
        RealDraftMatchResult replay = orchestrator.orchestrate(
                "GEN", "T1", MATCH_SEED,
                SimulationRuntimeProfileId.FULL_SYSTEM_WITH_JUNGLE_ECONOMY_CANDIDATE_V1);

        assertThat(candidate.executionProvenance().runtimeProfileId())
                .isEqualTo(SimulationRuntimeProfileId
                        .FULL_SYSTEM_WITH_JUNGLE_ECONOMY_CANDIDATE_V1);
        assertThat(candidate.executionProvenance().configurationHash())
                .isEqualTo("e04869bca5281f7f416c8191d7bf1b5be04b3129f33f6dfd4de83e8d8e92743b");
        assertThat(candidate.executionProvenance().activeGameplayRulesVersion())
                .isEqualTo(SimulationRuntimeProfiles
                        .JUNGLE_ECONOMY_ACTIVE_GAMEPLAY_RULES_VERSION)
                .isEqualTo("MATCH_SIMULATOR_JUNGLE_ECONOMY_RULES_V4");
        assertThat(candidate.executionProvenance().resourceProvenance()
                .jungleClearGameplayEnabledProfileCount()).isEqualTo(51);
        assertThat(candidate.playerIdsByMatchSlot()).hasSize(10);
        assertThat(candidate.playerIdsByMatchSlot().values()).doesNotHaveDuplicates();

        assertThat(candidate.draftResult().draftIdentity())
                .isEqualTo(jungleOff.draftResult().draftIdentity());
        assertThat(candidate.matchChampionAssignments().asMap())
                .isEqualTo(jungleOff.matchChampionAssignments().asMap());
        assertThat(candidate.executionProvenance().timelineHash())
                .isNotEqualTo(jungleOff.executionProvenance().timelineHash());

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
