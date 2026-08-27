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
        MatchEngineV1Policy.Snapshot policy = MatchEngineV1Policy.authoritative();
        assertThat(observed.response().integrity().runtimeProfileId())
                .isEqualTo("PRODUCTION_MATCHUP_COMPOSITION_V1");
        assertThat(observed.response().result().runtimeProfileId())
                .isEqualTo(observed.response().integrity().runtimeProfileId());
        assertThat(observed.response().integrity().configurationHash())
                .isEqualTo(policy.configurationHash());
        assertThat(observed.response().integrity().policyHash()).isEqualTo(policy.policyHash());
        assertThat(policy.gameplayConfiguration().championMatchupMode().name())
                .isEqualTo("GEOMETRIC_V2");
        assertThat(policy.gameplayConfiguration().teamCompositionGameplayMode().name())
                .isEqualTo("PRODUCTION_V2");
        assertThat(policy.gameplayConfiguration().jungleClearContribution().name())
                .isEqualTo("DISABLED_NOT_INTEGRATED");
        assertThat(policy.activationDecisionCode())
                .isEqualTo("PRODUCT_DECISION_ACCEPT_WITH_KNOWN_DIAGNOSTIC_LIMITATION");
        assertThat(policy.statisticalHoldoutApproved()).isFalse();
        assertThat(observed.response().integrity().outputHash())
                .isEqualTo(unobserved.response().integrity().outputHash())
                .matches("[0-9a-f]{64}");
        assertThat(observed.response().result().winner()).isNotNull();
        assertThat(observed.response().result().durationSeconds()).isPositive();
        assertThat(observed.response().timeline().events()).isNotEmpty();
        assertThat(observed.response().timeline().snapshots()).isNotEmpty();
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
