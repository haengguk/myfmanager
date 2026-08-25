package com.lolfm.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lolfm.application.MatchEngineV1;
import com.lolfm.application.MatchEngineV1Canonicalizer;
import com.lolfm.application.MatchEngineV1Input;
import com.lolfm.application.MatchEngineV1InputFactory;
import com.lolfm.application.RealDraftMatchOrchestrator;
import com.lolfm.application.RealDraftMatchPreflightValidator;
import com.lolfm.application.RealMatchApiV1ResponseMapper;
import com.lolfm.application.RealMatchApiV1Service;
import com.lolfm.application.RealMatchPerformanceBaselineV1Harness;
import com.lolfm.domain.Team;
import com.lolfm.draft.AutoDraftObservationHarnessV1;
import com.lolfm.draft.DraftEngine;
import com.lolfm.draft.DraftTeamContext;
import com.lolfm.draft.FinalDraftResult;
import com.lolfm.draft.SeriesDraftHistory;
import com.lolfm.dto.RealMatchApiV1Dtos;
import com.lolfm.player.LckTeamAssembler;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/** Observational current-tree phase decomposition; never affects gameplay identity. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {"spring.main.banner-mode=off", "logging.level.root=ERROR"})
@Tag("diagnostic")
@Tag("real-match-transport-compression-v1-phase")
class RealMatchTransportCompressionV1PhaseDiagnosticTest {
    private static final List<Fixture> FIXTURES = List.of(
            new Fixture("FIXTURE_A_GEN_T1_SEED_73", "GEN", "T1", "73",
                    "bdc597af083aa4f081cf4fe7a242d0e36eec7744b186d998d6f83b717648e874"),
            new Fixture("FIXTURE_B_HLE_DK_SEED_NEGATIVE_73", "HLE", "DK", "-73",
                    "fef2dfd3c522a69f7393bf46196ac9319cb4b6981e9131c694a01239d7aaabb0"));

    @Autowired ObjectMapper mapper;
    @Autowired RealMatchApiV1RequestParser requests;
    @Autowired RealMatchApiV1Service service;
    @Autowired RealDraftMatchOrchestrator orchestrator;
    @Autowired LckTeamAssembler teams;
    @Autowired RealDraftMatchPreflightValidator preflight;
    @Autowired MatchEngineV1InputFactory inputs;
    @Autowired MatchEngineV1 engine;
    @Autowired RealMatchApiV1ResponseMapper responses;
    @Autowired MatchEngineV1Canonicalizer canonicalizer;

    @Test
    void captureCurrentExecutablePhaseDecomposition() throws Exception {
        Path backendRoot = Path.of("").toAbsolutePath().normalize();
        Path output = Path.of(System.getProperty("transportPhaseOutput",
                backendRoot.resolve("build/reports/real-match-transport-compression-v1-inputs/"
                        + "post-compression-phase-runs.csv").toString()))
                .toAbsolutePath().normalize();
        Path requiredRoot = backendRoot.resolve(
                "build/reports/real-match-transport-compression-v1-inputs");
        assertThat(output.startsWith(requiredRoot)).isTrue();
        Files.createDirectories(output.getParent());

        RealMatchPerformanceBaselineV1Harness harness =
                new RealMatchPerformanceBaselineV1Harness(
                        mapper, requests, service, orchestrator, teams, preflight,
                        inputs, engine, responses, canonicalizer);
        DraftEngine production = field(orchestrator, "drafts", DraftEngine.class);
        AutoDraftObservationHarnessV1 observer =
                new AutoDraftObservationHarnessV1(production);
        harness.simulate(request(FIXTURES.getFirst()), false);

        StringBuilder csv = new StringBuilder(
                "fixtureId,ordinal,rosterAssemblyNanos,draftTeamContextNanos,"
                        + "freshHistoryNanos,fullDraftNanos,inputProjectionNanos,"
                        + "rosterDraftInputPreparationNanos,requestValidationAndPreflightNanos,"
                        + "matchEngineExecutionNanos,outputIntegrityValidationNanos,"
                        + "responseMappingNanos,jsonSerializationNanos,applicationBoundaryTotalNanos,"
                        + "decodedPayloadBytes,offlineGzipBytes,winner,durationSeconds,eventCount,"
                        + "snapshotCount,outputHash,replayProvenanceHash,simulatorTimelineHash,"
                        + "structuredTimelineHash,randomDrawCount,randomTraceHash,"
                        + "responseCanonicalHash,draftIdentity,finalDraftHash,finalAssignmentHash,"
                        + "inputHash,semanticExact\n");
        for (Fixture fixture : FIXTURES) {
            for (int ordinal = 1; ordinal <= 2; ordinal++) {
                DetailedDraft detailed = detailedDraft(observer, fixture);
                var execution = harness.simulate(request(fixture), true);
                RealMatchApiV1Dtos.Response response = execution.response();
                RealMatchApiV1Dtos.Integrity integrity = response.integrity();
                boolean exact = response.draft().finalDraftHash()
                                .equals(detailed.input().finalDraft().finalDraftHash())
                        && response.draft().finalAssignmentHash()
                                .equals(detailed.input().finalDraft().finalAssignmentHash())
                        && integrity.outputHash().equals(fixture.expectedOutputHash())
                        && detailed.input().finalDraft().finalDraftHash()
                                .equals(response.draft().finalDraftHash());
                assertThat(exact).as("phase semantic identity %s/%s",
                        fixture.id(), ordinal).isTrue();
                var timings = execution.timings();
                csv.append(fixture.id()).append(',').append(ordinal).append(',')
                        .append(detailed.rosterAssemblyNanos()).append(',')
                        .append(detailed.contextNanos()).append(',')
                        .append(detailed.historyNanos()).append(',')
                        .append(detailed.observation().fullDraftNanos()).append(',')
                        .append(detailed.inputNanos()).append(',')
                        .append(timings.rosterDraftInputPreparationNanos()).append(',')
                        .append(timings.requestValidationAndPreflightNanos()).append(',')
                        .append(timings.matchEngineExecutionNanos()).append(',')
                        .append(timings.outputIntegrityValidationNanos()).append(',')
                        .append(timings.responseMappingNanos()).append(',')
                        .append(timings.jsonSerializationNanos()).append(',')
                        .append(timings.applicationBoundaryTotalNanos()).append(',')
                        .append(execution.serializedResponse().length).append(',')
                        .append(execution.offlineGzipResponse().length).append(',')
                        .append(response.result().winner()).append(',')
                        .append(response.result().durationSeconds()).append(',')
                        .append(response.timeline().events().size()).append(',')
                        .append(response.timeline().snapshots().size()).append(',')
                        .append(integrity.outputHash()).append(',')
                        .append(integrity.replayProvenanceHash()).append(',')
                        .append(integrity.simulatorTimelineHash()).append(',')
                        .append(integrity.structuredTimelineHash()).append(',')
                        .append(integrity.randomFingerprint().randomDrawCount()).append(',')
                        .append(integrity.randomFingerprint().randomTraceHash()).append(',')
                        .append(execution.responseCanonicalHash()).append(',')
                        .append(detailed.result().draftIdentity()).append(',')
                        .append(detailed.input().finalDraft().finalDraftHash()).append(',')
                        .append(detailed.input().finalDraft().finalAssignmentHash()).append(',')
                        .append(detailed.input().inputHash()).append(',')
                        .append(exact).append('\n');
            }
        }
        Files.writeString(output, csv.toString(), StandardCharsets.UTF_8);
        System.out.println("REAL_MATCH_TRANSPORT_COMPRESSION_V1_PHASES_CAPTURED " + output);
    }

    private DetailedDraft detailedDraft(
            AutoDraftObservationHarnessV1 observer, Fixture fixture
    ) {
        long start = System.nanoTime();
        Team blueTeam = teams.assemble(fixture.blue());
        Team redTeam = teams.assemble(fixture.red());
        long rosterNanos = elapsed(start);
        start = System.nanoTime();
        DraftTeamContext blue = DraftTeamContext.from(blueTeam);
        DraftTeamContext red = DraftTeamContext.from(redTeam);
        long contextNanos = elapsed(start);
        start = System.nanoTime();
        SeriesDraftHistory history = new SeriesDraftHistory();
        long historyNanos = elapsed(start);
        AutoDraftObservationHarnessV1.Observation observation =
                observer.observe(blue, red, history);
        FinalDraftResult result = observation.result();
        start = System.nanoTime();
        MatchEngineV1Input input = inputs.fromRealDraft(
                fixture.blue(), blueTeam, fixture.red(), redTeam,
                Long.parseLong(fixture.seed()), 1, Set.of(), result);
        long inputNanos = elapsed(start);
        return new DetailedDraft(rosterNanos, contextNanos, historyNanos,
                inputNanos, observation, result, input);
    }

    private JsonNode request(Fixture fixture) {
        return mapper.createObjectNode()
                .put("schemaVersion", RealMatchApiV1Dtos.REQUEST_SCHEMA)
                .put("blueTeamCode", fixture.blue())
                .put("redTeamCode", fixture.red())
                .put("seed", fixture.seed());
    }

    private static long elapsed(long start) {
        return System.nanoTime() - start;
    }

    private static <T> T field(Object owner, String name, Class<T> type) {
        try {
            Field field = owner.getClass().getDeclaredField(name);
            field.setAccessible(true);
            return type.cast(field.get(owner));
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException("Missing production field " + name, error);
        }
    }

    private record Fixture(
            String id, String blue, String red, String seed, String expectedOutputHash
    ) {
    }

    private record DetailedDraft(
            long rosterAssemblyNanos, long contextNanos, long historyNanos, long inputNanos,
            AutoDraftObservationHarnessV1.Observation observation,
            FinalDraftResult result, MatchEngineV1Input input
    ) {
    }
}
