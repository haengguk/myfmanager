package com.lolfm.draft;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class Phase13GRealProficiencyReportWriterTest {
    @TempDir Path tempDir;

    @Test
    void smallRoleSpecificReportIsDeterministicAndSelfHashed() throws Exception {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("highProficiencyAuthoredCount", 1);
        summary.put("championCandidatePresentKeyCount", 1);
        summary.put("roleKeyReachableKeyCount", 0);
        summary.put("reviewCodes", List.of("REVIEW_REAL_PROFICIENCY_ROLE_KEY_UNREACHABLE"));
        Phase13GRealProficiencyReachabilityAudit.KeyResult row =
                new Phase13GRealProficiencyReachabilityAudit.KeyResult(
                        "player-test", "TST:SUPPORT", "Display, Name", "TST", "SUPPORT",
                        "poppy:SUPPORT", "poppy", true, 18, 1, 1, 0, 1, 0,
                        1, 0, true, false, false,
                        "CHAMPION_PRESENT_BUT_TARGET_ROLE_INFEASIBLE");

        var first = Phase13GRealProficiencyReachabilityAudit.writeReportArtifacts(
                tempDir.resolve("first"), summary, List.of(row));
        var second = Phase13GRealProficiencyReachabilityAudit.writeReportArtifacts(
                tempDir.resolve("second"), summary, List.of(row));

        assertThat(Files.readAllBytes(first.summaryPath()))
                .containsExactly(Files.readAllBytes(second.summaryPath()));
        assertThat(Files.readAllBytes(first.keyResultsPath()))
                .containsExactly(Files.readAllBytes(second.keyResultsPath()));
        assertThat(Files.readString(first.shaPath()))
                .isEqualTo(Files.readString(second.shaPath()))
                .contains(Phase13GRealProficiencyReachabilityAudit.SUMMARY_FILE)
                .contains(Phase13GRealProficiencyReachabilityAudit.KEY_RESULTS_FILE);
        assertThat(Files.readString(first.keyResultsPath()))
                .contains("championLevelLegalScenarioCount")
                .contains("roleSpecificLegalScenarioCount")
                .contains("roleKeyReachable")
                .contains("\"Display, Name\"");
        JsonNode json = new ObjectMapper().readTree(first.summaryPath().toFile());
        assertThat(json.path("roleKeyReachableKeyCount").asInt()).isZero();
    }
}
