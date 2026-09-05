package com.lolfm.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lolfm.draft.PlayerControlledDraftEngine;
import com.lolfm.player.LckTeamAssembler;
import com.lolfm.simulator.TeamSide;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/** Explicit paired probe for the Player Draft performance-hardening milestone. */
@EnabledIfEnvironmentVariable(named = "LOLMANAGER_PLAYER_DRAFT_PERFORMANCE_HARDENING_PHASE", matches = "before|after")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {"spring.main.banner-mode=off", "logging.level.root=ERROR"})
class PlayerDraftPerformanceHardeningV1DiagnosticTest {
    private static final String PHASE =
            "LOLMANAGER_PLAYER_DRAFT_PERFORMANCE_HARDENING_PHASE";
    private static final List<String> SCRIPT = List.of(
            "garen", "galio", "gangplank", "gragas", "graves",
            "nami", "gwen", "gnar", "nilah", "diana");

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
    void capturePairedBackendProbe() throws Exception {
        String phase = System.getenv(PHASE);
        PlayerDraftLatencyProfilingV1Harness harness = new PlayerDraftLatencyProfilingV1Harness(
                mapper, teams, drafts, service, sessions, inputs, matches, simulations,
                responses, canonicalizer);

        harness.run(phase + "-warmup", "GEN", "T1", 73L, TeamSide.BLUE,
                SCRIPT, false, PlayerDraftLatencyProfilingV1Harness.RunKind.WARM);
        ArrayList<PlayerDraftLatencyProfilingV1Harness.FlowObservation> flows =
                new ArrayList<>();
        for (TeamSide side : TeamSide.values()) {
            for (int run = 1; run <= 2; run++) {
                flows.add(harness.run(phase + "-" + side.name().toLowerCase()
                                + "-" + run,
                        "GEN", "T1", 73L, side, SCRIPT, true,
                        PlayerDraftLatencyProfilingV1Harness.RunKind.WARM));
            }
        }
        Path output = Path.of(System.getProperty("java.io.tmpdir"),
                "player-draft-performance-hardening-v1-" + phase + ".csv");
        Files.writeString(output, csv(phase, flows));
        System.out.println("PLAYER_DRAFT_PERFORMANCE_HARDENING_V1_"
                + phase.toUpperCase() + "=" + output.toAbsolutePath());
    }

    private static String csv(
            String phase,
            List<PlayerDraftLatencyProfilingV1Harness.FlowObservation> flows
    ) {
        StringBuilder csv = new StringBuilder("phase,runId,side,kind,index,nanos,"
                + "inputHash,outputHash,randomDrawCount,randomTraceHash,responseHash\n");
        for (var flow : flows) {
            for (var action : flow.actions()) {
                row(csv, phase, flow.runId(), flow.controlledSide(), "actionService",
                        action.playerActionIndex(), action.backendServiceTotalNanos(), "", "",
                        "", "", "");
                row(csv, phase, flow.runId(), flow.controlledSide(), "actionReplay",
                        action.playerActionIndex(),
                        action.exactReplayRepositoryBoundaryNanos(), "", "", "", "", "");
                row(csv, phase, flow.runId(), flow.controlledSide(), "projection",
                        action.playerActionIndex(), action.responseProjectionNanos(), "", "",
                        "", "", "");
            }
            var simulation = flow.simulation();
            row(csv, phase, flow.runId(), flow.controlledSide(), "inputValidation", 0,
                    simulation.inputValidationNanos(), simulation.inputHash(),
                    simulation.outputHash(), Long.toString(simulation.randomDrawCount()),
                    simulation.randomTraceHash(), simulation.responseCanonicalHash());
            row(csv, phase, flow.runId(), flow.controlledSide(), "simulateFirst", 0,
                    simulation.serviceTotalNanos(), simulation.inputHash(),
                    simulation.outputHash(), Long.toString(simulation.randomDrawCount()),
                    simulation.randomTraceHash(), simulation.responseCanonicalHash());
            row(csv, phase, flow.runId(), flow.controlledSide(), "simulateRetry", 0,
                    simulation.exactRetryNanos(), simulation.inputHash(),
                    simulation.outputHash(), Long.toString(simulation.randomDrawCount()),
                    simulation.randomTraceHash(), simulation.responseCanonicalHash());
        }
        return csv.toString();
    }

    private static void row(
            StringBuilder csv, String phase, String runId, TeamSide side, String kind,
            int index, long nanos, String inputHash, String outputHash,
            String draws, String traceHash, String responseHash
    ) {
        csv.append(phase).append(',').append(runId).append(',').append(side).append(',')
                .append(kind).append(',').append(index).append(',').append(nanos).append(',')
                .append(inputHash).append(',').append(outputHash).append(',').append(draws)
                .append(',').append(traceHash).append(',').append(responseHash).append('\n');
    }
}
