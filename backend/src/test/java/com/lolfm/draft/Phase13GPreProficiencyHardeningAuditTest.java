package com.lolfm.draft;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class Phase13GPreProficiencyHardeningAuditTest {
    @Test
    void missingRegressionDirectoryBlocksCompletion(@TempDir Path temp) throws Exception {
        Map<String, Object> summary = runAndReadSummary(temp, temp.resolve("missing-test-results"), null);

        assertThat(summary.get("backendRegressionPresent")).isEqualTo(false);
        assertThat((List<String>) summary.get("blockerCodes"))
                .contains("BLOCKED_BY_MISSING_BACKEND_REGRESSION");
        assertThat(summary.get("verdict"))
                .isEqualTo("PRE_REAL_CHAMPION_PROFICIENCY_GATE_HARDENING_BLOCKED");
    }

    @Test
    void successfulAuditUsesMeasuredXmlCountAndPreservesPendingBoundary(@TempDir Path temp) throws Exception {
        Path results = temp.resolve("test-results");
        Files.createDirectories(results);
        Files.writeString(results.resolve("TEST-hardening.xml"), """
                <testsuite tests="7" failures="0" errors="0" skipped="1">
                  <testcase name="one"/>
                </testsuite>
                """, StandardCharsets.UTF_8);

        Map<String, Object> summary = runAndReadSummary(temp, results, "valid");

        assertThat(summary.get("backendRegressionPresent")).isEqualTo(true);
        assertThat(summary.get("backendXmlFileCount")).isEqualTo(1);
        assertThat(summary.get("backendTests")).isEqualTo(7);
        assertThat(summary.get("backendFailures")).isEqualTo(0);
        assertThat(summary.get("backendErrors")).isEqualTo(0);
        assertThat(summary.get("backendSkipped")).isEqualTo(1);
        assertThat(summary.get("realProficiencyExecutionStatus"))
                .isEqualTo("PENDING_REAL_CHAMPION_PROFICIENCY_RESOURCE");
        assertThat(summary.get("playerRatingResourceExpectedSha"))
                .isEqualTo(summary.get("playerRatingResourceActualSha"));
        assertThat(summary.get("frozenDraftMetaExpectedHash"))
                .isEqualTo(summary.get("frozenDraftMetaActualHash"));
        assertThat(summary.get("missingAttributeCount")).isEqualTo(0);
        assertThat(summary.get("missingAttributeValidation"))
                .isEqualTo("LOADER_FAIL_FAST_EXACT_ATTRIBUTE_SET");
        assertThat(summary.get("nonApplicableAttributeCount")).isEqualTo(0);
        assertThat(summary.get("nonApplicableAttributeValidation"))
                .isEqualTo("ROLE_APPLICABILITY_EXACT_SET");
        assertThat(summary.get("rolePoolCompressionDirectional")).isEqualTo(true);
        assertThat(summary.get("backendTests")).isNotEqualTo(1892);
    }

    private Map<String, Object> runAndReadSummary(Path temp, Path results, String outputName) throws Exception {
        Path output = temp.resolve(outputName == null ? "missing-output" : outputName);
        Phase13GPreProficiencyHardeningAudit.run(output, results);
        return new ObjectMapper().readValue(
                output.resolve("phase13g-pre-proficiency-hardening-summary.json").toFile(),
                new TypeReference<>() { });
    }
}
