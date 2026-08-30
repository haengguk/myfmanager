package com.lolfm.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lolfm.draft.PlayerControlledDraftEngine;
import com.lolfm.player.LckTeamAssembler;
import com.lolfm.simulator.TeamSide;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {"spring.main.banner-mode=off", "logging.level.root=ERROR"})
class PlayerDraftLatencyProfilingV1HarnessTest {
    @Autowired ObjectMapper mapper;
    @Autowired LckTeamAssembler teams;
    @Autowired PlayerControlledDraftEngine drafts;
    @Autowired PlayerDraftApiV1Service service;
    @Autowired PlayerDraftSessionRepository sessions;
    @Autowired PlayerControlledDraftMatchInputBoundary inputs;
    @Autowired MatchEngineV1 matches;
    @Autowired PlayerDraftMatchSimulationExecutor simulations;
    @Autowired PlayerDraftApiV1ResponseMapper responses;
    @Autowired MatchEngineV1Canonicalizer canonicalizer;

    @Test
    void profilingOnOffPreservesDraftInputTimelineRandomAndOutputExactly() {
        PlayerDraftLatencyProfilingV1Harness harness = harness();
        var observed = harness.run("parity-on", "GEN", "T1", 73L, TeamSide.BLUE,
                List.of(), true, PlayerDraftLatencyProfilingV1Harness.RunKind.PARITY_ON);
        var plain = harness.run("parity-off", "GEN", "T1", 73L, TeamSide.BLUE,
                observed.actionScript(), false,
                PlayerDraftLatencyProfilingV1Harness.RunKind.PARITY_OFF);

        assertThat(plain.actionScript()).isEqualTo(observed.actionScript()).hasSize(10);
        assertThat(plain.finalProgress().result().draftIdentity())
                .isEqualTo(observed.finalProgress().result().draftIdentity());
        assertThat(plain.finalProgress().result().controlEvidence().controlEvidenceHash())
                .isEqualTo(observed.finalProgress().result().controlEvidence()
                        .controlEvidenceHash());
        assertThat(plain.finalProgress().result().matchChampionAssignments().asMap())
                .isEqualTo(observed.finalProgress().result()
                        .matchChampionAssignments().asMap());
        assertThat(projectAi(plain)).isEqualTo(projectAi(observed));

        var on = observed.simulation();
        var off = plain.simulation();
        assertThat(off.inputHash()).isEqualTo(on.inputHash());
        assertThat(off.replayProvenanceHash()).isEqualTo(on.replayProvenanceHash());
        assertThat(off.simulatorTimelineHash()).isEqualTo(on.simulatorTimelineHash());
        assertThat(off.structuredTimelineHash()).isEqualTo(on.structuredTimelineHash());
        assertThat(off.outputHash()).isEqualTo(on.outputHash());
        assertThat(off.randomDrawCount()).isEqualTo(on.randomDrawCount());
        assertThat(off.randomTraceHash()).isEqualTo(on.randomTraceHash());
        assertThat(off.winner()).isEqualTo(on.winner());
        assertThat(off.durationSeconds()).isEqualTo(on.durationSeconds());
        assertThat(off.eventCount()).isEqualTo(on.eventCount());
        assertThat(off.snapshotCount()).isEqualTo(on.snapshotCount());

        assertThat(observed.actions()).allSatisfy(action -> {
            assertThat(action.backendServiceTotalNanos()).isPositive();
            assertThat(action.responseProjectionNanos()).isPositive();
            assertThat(action.jsonSerializationNanos()).isPositive();
            assertThat(action.decodedJsonBytes()).isPositive();
            assertThat(action.offlineGzipBytes()).isLessThan(action.decodedJsonBytes());
        });
        assertThat(plain.actions()).allSatisfy(action -> {
            assertThat(action.backendServiceTotalNanos()).isZero();
            assertThat(action.playerLegalityViewNanos()).isZero();
            assertThat(action.aiFollowUpTotalNanos()).isZero();
            assertThat(action.responseProjectionNanos()).isZero();
            assertThat(action.jsonSerializationNanos()).isZero();
        });
        assertThat(on.serviceTotalNanos()).isPositive();
        assertThat(off.serviceTotalNanos()).isZero();
    }

    private static List<String> projectAi(
            PlayerDraftLatencyProfilingV1Harness.FlowObservation flow
    ) {
        return flow.aiTurns().stream().map(value -> value.aiTurn() + "|"
                + value.actionType() + "|" + value.championId()).toList();
    }

    private PlayerDraftLatencyProfilingV1Harness harness() {
        return new PlayerDraftLatencyProfilingV1Harness(
                mapper, teams, drafts, service, sessions, inputs, matches, simulations,
                responses, canonicalizer);
    }
}
