package com.lolfm.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.lolfm.draft.SeriesDraftHistory;
import com.lolfm.simulator.JungleClearContribution;
import com.lolfm.simulator.ResolvedSimulationRuntimeProfile;
import com.lolfm.simulator.SimulationRuntimeProfileId;
import com.lolfm.simulator.SimulationRuntimeProfiles;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/** Exact four-profile oracle captured before Jungle Tempo production code existed. */
@SpringBootTest
@Tag("diagnostic")
@Tag("jungle-tempo-pre-profile-parity")
class PreJungleTempoParityAuditTest {
    private static final Path BASELINE = Path.of(
            "baseline", "pre-jungle-tempo-runtime-v1",
            "pre-jungle-tempo-runtime-baseline-v1.json");
    private static final String BASELINE_SHA256 =
            "17f703a48949b63bf4ca25f4b32be2bc22fac87a439cdd8cb7c18aadc7f82074";
    private static final List<SimulationRuntimeProfileId> PROFILES = List.of(
            SimulationRuntimeProfileId.BASELINE_V1,
            SimulationRuntimeProfileId.MATCHUP_ONLY_CANDIDATE_V1,
            SimulationRuntimeProfileId.FULL_SYSTEM_CANDIDATE_V1,
            SimulationRuntimeProfileId.FULL_SYSTEM_WITH_JUNGLE_ECONOMY_CANDIDATE_V1);

    @Autowired RealDraftMatchOrchestrator orchestrator;
    @Autowired ObjectMapper objectMapper;

    @Test
    void allTwelvePreTempoMatchesRetainExactGameplayAndRandomConsumption() throws Exception {
        byte[] baselineBytes = Files.readAllBytes(BASELINE);
        assertThat(sha256(baselineBytes)).isEqualTo(BASELINE_SHA256);
        JsonNode document = objectMapper.readTree(baselineBytes);
        assertThat(document.path("baselineId").asText())
                .isEqualTo("PRE_JUNGLE_TEMPO_RUNTIME_BASELINE_V1");
        assertThat(document.path("engineImplementationVersion").asText())
                .isEqualTo("MATCH_SIMULATOR_ENGINE_IMPLEMENTATION_V2");
        assertThat(document.path("fullRegressionStatus").asText()).isEqualTo("CLEAN_PASS");
        assertThat(document.path("profiles")).hasSize(4);

        Map<String, JsonNode> expectedByCase = new LinkedHashMap<>();
        document.path("matches").forEach(match -> expectedByCase.put(key(
                match.path("runtimeProfileId").asText(), match.path("caseId").asText()), match));
        assertThat(expectedByCase).hasSize(12);

        ArrayList<ParityRow> rows = new ArrayList<>();
        for (SimulationRuntimeProfileId profileId : PROFILES) {
            ResolvedSimulationRuntimeProfile profile = SimulationRuntimeProfiles.resolve(profileId);
            JungleClearContribution expectedContribution = profileId
                    == SimulationRuntimeProfileId.FULL_SYSTEM_WITH_JUNGLE_ECONOMY_CANDIDATE_V1
                    ? JungleClearContribution.ECONOMY_V1
                    : JungleClearContribution.DISABLED_NOT_INTEGRATED;
            assertThat(profile.gameplayConfiguration().jungleClearContribution())
                    .isEqualTo(expectedContribution);

            SeriesDraftHistory history = new SeriesDraftHistory();
            List<RealDraftMatchResult> actualResults = List.of(
                    orchestrator.orchestrate("GEN", "T1", history, 73L, profileId),
                    orchestrator.orchestrate("GEN", "T1", history, 74L, profileId),
                    orchestrator.orchestrate(
                            "T1", "GEN", new SeriesDraftHistory(), 73L, profileId));
            String[] caseIds = {
                    "GEN_T1_SERIES_GAME_1",
                    "GEN_T1_SERIES_GAME_2",
                    "T1_GEN_MIRROR_GAME_1"
            };

            for (int index = 0; index < actualResults.size(); index++) {
                RealDraftMatchResult actual = actualResults.get(index);
                SimulationExecutionProvenance provenance = actual.executionProvenance();
                JsonNode expected = expectedByCase.get(key(profileId.name(), caseIds[index]));
                assertThat(expected).as("pre-tempo case %s/%s", profileId, caseIds[index])
                        .isNotNull();

                assertThat(provenance.runtimeProfileId()).isEqualTo(profileId);
                assertThat(provenance.configurationHash())
                        .isEqualTo(expected.path("configurationHash").asText())
                        .isEqualTo(profile.configurationHash());
                assertThat(provenance.activeGameplayRulesVersion())
                        .isEqualTo(profile.activeGameplayRulesVersion());
                assertThat(provenance.draftDecisionHash())
                        .isEqualTo(expected.path("draftDecisionHash").asText());
                assertThat(provenance.finalDraftHash())
                        .isEqualTo(expected.path("finalDraftHash").asText());
                assertThat(provenance.finalAssignmentHash())
                        .isEqualTo(expected.path("finalAssignmentHash").asText());
                assertThat(provenance.seriesHistoryBeforeHash())
                        .isEqualTo(expected.path("seriesHistoryBeforeHash").asText());
                assertThat(provenance.timelineHash())
                        .isEqualTo(expected.path("timelineHash").asText());
                assertThat(provenance.randomFingerprint().randomDrawCount())
                        .isEqualTo(expected.path("randomFingerprint")
                                .path("randomDrawCount").asLong());
                assertThat(provenance.randomFingerprint().randomTraceHash())
                        .isEqualTo(expected.path("randomFingerprint")
                                .path("randomTraceHash").asText());
                assertThat(provenance.randomFingerprint().schemaVersion())
                        .isEqualTo(expected.path("randomFingerprint")
                                .path("schemaVersion").asText());
                assertThat(provenance.randomFingerprint().randomTraceHashAlgorithm())
                        .isEqualTo(expected.path("randomFingerprint")
                                .path("randomTraceHashAlgorithm").asText());
                assertThat(actual.timeline().getWinner())
                        .isEqualTo(expected.path("winner").asText());
                assertThat(actual.timeline().getDurationSeconds())
                        .isEqualTo(expected.path("durationSeconds").asInt());
                assertThat(actual.timeline().getEvents())
                        .hasSize(expected.path("eventCount").asInt());
                assertThat(actual.timeline().getSnapshots())
                        .hasSize(expected.path("snapshotCount").asInt());

                assertThat(provenance.engineImplementationVersion())
                        .isEqualTo("MATCH_SIMULATOR_ENGINE_IMPLEMENTATION_V6");
                assertThat(provenance.replayProvenanceHash())
                        .isNotEqualTo(expected.path("replayProvenanceHash").asText());
                rows.add(new ParityRow(
                        profileId, caseIds[index], provenance.configurationHash(),
                        provenance.timelineHash(), provenance.randomFingerprint().randomDrawCount(),
                        provenance.randomFingerprint().randomTraceHash(),
                        expected.path("replayProvenanceHash").asText(),
                        provenance.replayProvenanceHash(), "EXACT_GAMEPLAY_PARITY"));
            }
        }

        assertThat(rows).hasSize(12);
        writeReport(rows);
    }

    private void writeReport(List<ParityRow> rows) throws Exception {
        Path output = Path.of("build", "reports", "jungle-tempo-v1-b");
        Files.createDirectories(output);
        PreTempoParityReport report = new PreTempoParityReport(
                "JUNGLE_TEMPO_PRE_PROFILE_PARITY_REPORT_V1", BASELINE_SHA256,
                "MATCH_SIMULATOR_ENGINE_IMPLEMENTATION_V2",
                "MATCH_SIMULATOR_ENGINE_IMPLEMENTATION_V6",
                "configuration, draft, final assignment, complete timeline hash, Random draw "
                        + "count/hash, winner, duration, event count, and snapshot count are exact; "
                        + "replay provenance changes only because the engine implementation changed",
                List.copyOf(rows), "CLEAN_PASS");
        objectMapper.copy().enable(SerializationFeature.INDENT_OUTPUT)
                .writeValue(output.resolve("pre-tempo-parity-report.json").toFile(), report);
    }

    private static String key(String profileId, String caseId) {
        return profileId + '|' + caseId;
    }

    private static String sha256(byte[] value) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
    }

    private record ParityRow(
            SimulationRuntimeProfileId profileId,
            String caseId,
            String configurationHash,
            String timelineHash,
            long randomDrawCount,
            String randomTraceHash,
            String baselineReplayProvenanceHash,
            String currentReplayProvenanceHash,
            String status
    ) {
    }

    private record PreTempoParityReport(
            String schemaVersion,
            String baselineArtifactSha256,
            String baselineEngineImplementationVersion,
            String currentEngineImplementationVersion,
            String comparisonPolicy,
            List<ParityRow> matches,
            String status
    ) {
    }
}
