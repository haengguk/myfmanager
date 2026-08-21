package com.lolfm.draft;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class Phase13GRealProficiencyReachabilityAuditTest {
    @Test
    void executesExistingGateForEveryRealHighProficiencyKey() throws Exception {
        Path output = Path.of(Phase13GRealProficiencyReachabilityAudit.OUTPUT_DIRECTORY);
        Phase13GRealProficiencyReachabilityAudit.AuditResult result =
                Phase13GRealProficiencyReachabilityAudit.run(output);

        assertThat(result.highProficiencyAuthoredCount()).isEqualTo(537);
        assertThat(result.keyResults()).hasSize(result.highProficiencyAuthoredCount());
        assertThat(result.keyResults()).allSatisfy(row -> {
            assertThat(row.playerId()).startsWith("player-");
            assertThat(row.playerRatingKey()).contains(":");
            assertThat(row.proficiency()).isGreaterThanOrEqualTo(17);
            assertThat(row.scenarioCount()).isEqualTo(3);
        });
        assertThat(result.legalScenarioCount()).isPositive();
        assertThat(result.noLegalScenarioKeyCount()).isZero();
        assertThat(Files.isRegularFile(result.summaryPath())).isTrue();
        assertThat(Files.isRegularFile(result.keyResultsPath())).isTrue();
        assertThat(Files.isRegularFile(output.resolve(
                Phase13GRealProficiencyReachabilityAudit.SHA_FILE))).isTrue();

        JsonNode summary = new ObjectMapper().readTree(result.summaryPath().toFile());
        assertThat(summary.path("highProficiencyAuthoredCount").asInt()).isEqualTo(537);
        assertThat(summary.path("productionWeightsChanged").asBoolean()).isFalse();
        assertThat(summary.path("candidateGeneratorChanged").asBoolean()).isFalse();
        assertThat(summary.path("searchBoundsChanged").asBoolean()).isFalse();
        assertThat(summary.path("scopeGapPromotedToLegalRole").asBoolean()).isFalse();
    }
}
