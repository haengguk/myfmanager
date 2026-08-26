package com.lolfm.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lolfm.controller.RealMatchApiV1RequestParser;
import com.lolfm.player.LckTeamAssembler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class RealMatchPerformanceBaselineV1HarnessTest {
    private static final String FIXTURE_A_OUTPUT_HASH =
            "40c8786ebece2d9abc71d95c304d39ef8f63f2b3277237d1aeaf0a3cf1d76c34";

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
    void timingObserverIsOutputReplayTimelineAndRandomNeutral() throws Exception {
        RealMatchPerformanceBaselineV1Harness harness = harness();
        JsonNode request = mapper.readTree("""
                {"schemaVersion":"REAL_MATCH_SIMULATE_REQUEST_V1",
                 "blueTeamCode":"GEN","redTeamCode":"T1","seed":"73"}
                """);

        var observed = harness.simulate(request, true);
        var unobserved = harness.simulate(request, false);

        assertThat(canonicalizer.canonicalJson(observed.response()))
                .isEqualTo(canonicalizer.canonicalJson(unobserved.response()));
        assertThat(observed.response().integrity().outputHash())
                .isEqualTo(FIXTURE_A_OUTPUT_HASH);
        assertThat(observed.response().result().winner()).hasToString("RED");
        assertThat(observed.response().result().durationSeconds()).isEqualTo(1_750);
        assertThat(observed.response().timeline().events()).hasSize(350);
        assertThat(observed.response().timeline().snapshots()).hasSize(176);
        assertThat(observed.response().integrity().replayProvenanceHash())
                .isEqualTo(unobserved.response().integrity().replayProvenanceHash());
        assertThat(observed.response().integrity().simulatorTimelineHash())
                .isEqualTo(unobserved.response().integrity().simulatorTimelineHash());
        assertThat(observed.response().integrity().structuredTimelineHash())
                .isEqualTo(unobserved.response().integrity().structuredTimelineHash());
        assertThat(observed.response().integrity().randomFingerprint())
                .isEqualTo(unobserved.response().integrity().randomFingerprint());
        assertThat(observed.responseCanonicalHash()).isEqualTo(
                unobserved.responseCanonicalHash());
        assertThat(observed.resultCanonicalHash()).isEqualTo(
                unobserved.resultCanonicalHash());
        assertThat(observed.timelineCanonicalHash()).isEqualTo(
                unobserved.timelineCanonicalHash());

        var timings = observed.timings();
        assertThat(timings.applicationBoundaryTotalNanos()).isPositive();
        assertThat(timings.matchEngineExecutionNanos()).isPositive();
        assertThat(timings.applicationPhaseSumNanos())
                .isLessThanOrEqualTo(timings.applicationBoundaryTotalNanos());
        assertThat(timings.unattributedApplicationOverheadNanos()).isNotNegative();
        assertThat(timings.instrumentedRequestToJsonTotalNanos())
                .isGreaterThanOrEqualTo(timings.applicationBoundaryTotalNanos());
        assertThat(unobserved.timings().applicationBoundaryTotalNanos()).isZero();
        assertThat(observed.offlineGzipResponse().length)
                .isLessThan(observed.serializedResponse().length);
        assertThat(observed.independentlySerializedSectionBytes())
                .containsOnlyKeys("teams", "draft", "result", "timeline", "integrity");
    }

    private RealMatchPerformanceBaselineV1Harness harness() {
        return new RealMatchPerformanceBaselineV1Harness(
                mapper, requests, service, orchestrator, teams, preflight,
                inputs, engine, responses, canonicalizer);
    }
}
