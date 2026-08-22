package com.lolfm.draft;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("diagnostic")
@Tag("phase13g-real-proficiency")
class Phase13GRealProficiencyReachabilityAuditTest {
    @Test
    void executesRoleSpecificGateForEveryRealHighProficiencyKey() throws Exception {
        Path output = Path.of(Phase13GRealProficiencyReachabilityAudit.OUTPUT_DIRECTORY);
        Phase13GRealProficiencyReachabilityAudit.AuditResult result =
                Phase13GRealProficiencyReachabilityAudit.run(output);

        assertThat(result.highProficiencyAuthoredCount()).isEqualTo(537);
        assertThat(result.boundedScenarioCount()).isEqualTo(1_611);
        assertThat(result.keyResults()).hasSize(result.highProficiencyAuthoredCount());
        assertThat(result.keyResults()).allSatisfy(row -> {
            assertThat(row.playerId()).startsWith("player-");
            assertThat(row.playerRatingKey()).contains(":");
            assertThat(row.proficiency()).isGreaterThanOrEqualTo(17);
            assertThat(row.scenarioCount()).isEqualTo(3);
            assertThat(row.roleKeyReachableScenarioCount())
                    .isLessThanOrEqualTo(row.championCandidateAppearanceCount());
            assertThat(row.roleSpecificLegalScenarioCount())
                    .isLessThanOrEqualTo(row.championLevelLegalScenarioCount());
        });
        assertThat(result.championLevelLegalScenarioCount()).isPositive();
        assertThat(result.roleSpecificLegalScenarioCount()).isPositive();
        assertThat(result.roleKeyReachableKeyCount())
                .isLessThanOrEqualTo(result.championCandidatePresentKeyCount());
        assertThat(Files.isRegularFile(result.summaryPath())).isTrue();
        assertThat(Files.isRegularFile(result.keyResultsPath())).isTrue();
        assertThat(Files.isRegularFile(result.shaPath())).isTrue();

        JsonNode summary = new ObjectMapper().readTree(result.summaryPath().toFile());
        assertThat(summary.path("highProficiencyAuthoredCount").asInt()).isEqualTo(537);
        assertThat(summary.path("championCandidatePresentKeyCount").isInt()).isTrue();
        assertThat(summary.path("roleKeyReachableKeyCount").isInt()).isTrue();
        assertThat(summary.path("roleSpecificLegalScenarioCount").isInt()).isTrue();
        assertThat(summary.path("flexChampionFalsePositiveConcentration").isArray()).isTrue();
        assertThat(summary.path("productionWeightsChanged").asBoolean()).isFalse();
        assertThat(summary.path("candidateGeneratorChanged").asBoolean()).isFalse();
        assertThat(summary.path("searchBoundsChanged").asBoolean()).isFalse();
        assertThat(summary.path("scopeGapPromotedToLegalRole").asBoolean()).isFalse();
    }
}
