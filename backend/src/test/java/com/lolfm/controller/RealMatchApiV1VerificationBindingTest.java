package com.lolfm.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

/** Emits current source identities into JUnit XML so artifact evidence cannot reuse stale XML. */
class RealMatchApiV1VerificationBindingTest {
    @TestFactory
    List<DynamicTest> fullRegressionBindsCurrentProductionAndApiVerificationSources()
            throws Exception {
        Path backendRoot = Path.of("").toAbsolutePath().normalize();
        RealMatchApiV1ArtifactWriter.SourceTreeIdentity production =
                RealMatchApiV1ArtifactWriter.productionSourceTree(backendRoot);
        RealMatchApiV1ArtifactWriter.SourceTreeIdentity verification =
                RealMatchApiV1ArtifactWriter.verificationSourceTree(backendRoot);
        String binding = RealMatchApiV1ArtifactWriter.sourceBindingTestName(
                production, verification);
        return List.of(DynamicTest.dynamicTest(binding, () -> {
            assertThat(production.fileCount()).isPositive();
            assertThat(verification.fileCount()).isGreaterThanOrEqualTo(8);
            assertThat(production.hash()).matches("[0-9a-f]{64}");
            assertThat(verification.hash()).matches("[0-9a-f]{64}");
        }));
    }
}
