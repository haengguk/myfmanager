package com.lolfm.draft;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class Phase13GA2FinalizerFallbackTest {
    @Test
    void missingJUnitCountAttributesUseTheirOwnElementCounts() throws Exception {
        Path directory = Files.createTempDirectory("phase13g-finalizer-fallback-");
        try {
            Files.writeString(directory.resolve("TEST-fallback.xml"), """
                    <testsuite>
                      <testcase name="one"/>
                      <testcase name="two"><failure/><skipped/></testcase>
                      <testcase name="three"><error/></testcase>
                    </testsuite>
                    """, StandardCharsets.UTF_8);
            Phase13GA2Finalizer.RegressionCounts counts =
                    Phase13GA2Finalizer.aggregateXml(directory);
            assertThat(counts.tests()).isEqualTo(3);
            assertThat(counts.failures()).isEqualTo(1);
            assertThat(counts.errors()).isEqualTo(1);
            assertThat(counts.skipped()).isEqualTo(1);
        } finally {
            Files.deleteIfExists(directory.resolve("TEST-fallback.xml"));
            Files.deleteIfExists(directory);
        }
    }

    @Test
    void regressionPresenceRequiresXmlAndNonZeroTests() {
        assertThat(Phase13GA2Finalizer.regressionPresent(
                new Phase13GA2Finalizer.RegressionCounts(0, 0, 0, 0, 0))).isFalse();
        assertThat(Phase13GA2Finalizer.regressionPresent(
                new Phase13GA2Finalizer.RegressionCounts(0, 0, 0, 0, 2))).isFalse();
        assertThat(Phase13GA2Finalizer.regressionPresent(
                new Phase13GA2Finalizer.RegressionCounts(2, 0, 0, 0, 0))).isFalse();
        assertThat(Phase13GA2Finalizer.regressionPresent(
                new Phase13GA2Finalizer.RegressionCounts(2, 0, 0, 0, 1))).isTrue();
    }
}
