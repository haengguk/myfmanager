package com.lolfm.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DiagnosticEvidenceContractTest {
    @TempDir Path temporary;

    @Test
    void largeCompositionPopulationIsDiagnosticTaggedWhileFocusedContractsRemainDefaultTests()
            throws Exception {
        assertThat(tags(CompositionV9ApplicationCausalityDiagnosticTest.class))
                .contains("diagnostic");
        assertThat(tags(CompositionV9ApplicationCausalityShard0Test.class)).contains("diagnostic");
        assertThat(tags(CompositionV9ApplicationCausalityShard1Test.class)).contains("diagnostic");
        assertThat(tags(CompositionV9ApplicationCausalityShard2Test.class)).contains("diagnostic");
        assertThat(tags(CompositionV9ApplicationCausalityShard3Test.class)).contains("diagnostic");
        assertThat(tags(CompositionV9ApplicationCausalityContractTest.class))
                .doesNotContain("diagnostic");

        String build = Files.readString(Path.of("build.gradle"), StandardCharsets.UTF_8)
                .replace("\r\n", "\n");
        assertThat(build).contains("tasks.named('test')")
                .contains("excludeTags 'diagnostic'")
                .contains("verifyCompositionV9CausalityFocusedProof");
    }

    @Test
    void compositionDependencyManifestCoversExplicitSharedExecutionAndProofSources() throws Exception {
        var manifest = CompositionV9ApplicationCausalityRunner.dependencyManifest(Path.of("."));
        assertThat(manifest.dependencies()).extracting(
                DiagnosticDependencyManifest.Entry::logicalPath).contains(
                "src/test/java/com/lolfm/application/CompositionV9ApplicationCausalityRunner.java",
                "src/test/java/com/lolfm/application/PairedDiagnosticAuditGate.java",
                "src/test/java/com/lolfm/application/Phase13GB1RealMatchHarness.java",
                "src/test/java/com/lolfm/simulator/Phase13GB1SimulationExecutor.java",
                "src/test/java/com/lolfm/simulator/MatchEngineV9InstrumentationExecutor.java",
                "src/test/java/com/lolfm/application/MatchupV9StructureAttributionClassifier.java",
                "src/test/java/com/lolfm/composition/CompositionProductionApplicationProvenanceTest.java",
                "build.gradle#COMPOSITION_V9_APPLICATION_CAUSALITY_BUILD_CONTRACT");
        DiagnosticDependencyManifest.verify(manifest);

        var matchup = MatchupV9StructureAttributionRunner.attributionDependencyManifest(Path.of("."));
        assertThat(matchup.dependencies()).extracting(
                DiagnosticDependencyManifest.Entry::logicalPath).contains(
                "src/test/java/com/lolfm/application/PairedDiagnosticAuditGate.java",
                "src/test/java/com/lolfm/application/DiagnosticDependencyManifest.java",
                "build.gradle#MATCHUP_V9_STRUCTURE_ATTRIBUTION_BUILD_CONTRACT");
    }

    @Test
    void dependencyMutationCannotKeepHarnessHashAndDuplicateOrMissingPathsAreRejected()
            throws Exception {
        Path source = temporary.resolve("Proof.java");
        Files.writeString(source, "class Proof {}\n", StandardCharsets.UTF_8);
        var manifest = DiagnosticDependencyManifest.create(temporary, "TEST", "EXPLICIT",
                List.of(DiagnosticDependencyManifest.DependencySpec.file("Proof.java")));
        var entry = manifest.dependencies().getFirst();
        var mutated = new DiagnosticDependencyManifest.Manifest(manifest.schemaVersion(),
                manifest.manifestId(), manifest.inclusionRule(),
                List.of(new DiagnosticDependencyManifest.Entry(entry.logicalPath(), entry.sourcePath(),
                        hash("changed source"), entry.canonicalSha256(), entry.canonicalization())),
                manifest.harnessSourceHash());
        assertThatThrownBy(() -> DiagnosticDependencyManifest.verify(mutated))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("harness hash");
        assertThatThrownBy(() -> DiagnosticDependencyManifest.create(temporary, "TEST", "EXPLICIT",
                List.of(DiagnosticDependencyManifest.DependencySpec.file("Proof.java"),
                        DiagnosticDependencyManifest.DependencySpec.file("Proof.java"))))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Duplicate");
        assertThatThrownBy(() -> DiagnosticDependencyManifest.create(temporary, "TEST", "EXPLICIT",
                List.of(DiagnosticDependencyManifest.DependencySpec.file("Missing.java"))))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Missing");
    }

    @Test
    void proofReceiptRejectsRelabelSourceProductionTaskAndPayloadMutation() throws Exception {
        Path source = temporary.resolve("Proof.java");
        Files.writeString(source, "class Proof { void passes() {} }\n", StandardCharsets.UTF_8);
        var manifest = DiagnosticDependencyManifest.create(temporary, "TEST", "EXPLICIT",
                List.of(DiagnosticDependencyManifest.DependencySpec.file("Proof.java")));
        String sourceSha = manifest.dependencies().getFirst().rawSha256();
        String production = hash("production");
        var proof = FocusedInvariantProofReceipt.syntheticPassing(
                "Proof", "passes", "Proof.java", sourceSha, "focusedProof", production);
        FocusedInvariantProofReceipt.verify(proof, production, manifest);

        assertRejected(copy(proof, proof.testClass(), proof.testMethod(), proof.testSourceSha256(),
                proof.gradleTask(), proof.resultsLogicalPath(), proof.productionGuardHash(),
                1, 1, 0, 0, "PASS", proof.proofReceiptPayloadSha256()), production, manifest);
        assertRejected(copy(proof, proof.testClass(), proof.testMethod(), hash("other source"),
                proof.gradleTask(), proof.resultsLogicalPath(), proof.productionGuardHash(),
                1, 0, 0, 0, "PASS", proof.proofReceiptPayloadSha256()), production, manifest);
        assertRejected(copy(proof, proof.testClass(), proof.testMethod(), proof.testSourceSha256(),
                "otherTask", proof.resultsLogicalPath(), proof.productionGuardHash(),
                1, 0, 0, 0, "PASS", proof.proofReceiptPayloadSha256()), production, manifest);
        assertRejected(FocusedInvariantProofReceipt.syntheticPassing(
                "Proof", "passes", "Proof.java", sourceSha, "otherTask", production),
                production, manifest);
        assertRejected(copy(proof, proof.testClass(), proof.testMethod(), proof.testSourceSha256(),
                proof.gradleTask(), proof.resultsLogicalPath(), hash("other production"),
                1, 0, 0, 0, "PASS", proof.proofReceiptPayloadSha256()), production, manifest);
        assertRejected(copy(proof, proof.testClass(), proof.testMethod(), proof.testSourceSha256(),
                proof.gradleTask(), proof.resultsLogicalPath(), proof.productionGuardHash(),
                1, 0, 0, 0, "PASS", hash("tampered payload")), production, manifest);
    }

    @Test
    void nonexistentFocusedClassOrMethodIsRejectedBeforeXmlCanBeReused() throws Exception {
        Files.writeString(temporary.resolve("Proof.java"),
                "class Proof { void passes() {} }\n", StandardCharsets.UTF_8);
        assertThatThrownBy(() -> FocusedInvariantProofReceipt.capture(temporary,
                "focusedProof", "missing.Proof#passes", "Proof.java", hash("production")))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("does not exist");
        assertThatThrownBy(() -> FocusedInvariantProofReceipt.capture(temporary,
                "focusedProof", "Proof#missing", "Proof.java", hash("production")))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("does not exist");
    }

    @Test
    void recursiveManifestRejectsNestedReceiptCheckpointAndUnlistedReplacement() throws Exception {
        Files.createDirectories(temporary.resolve("worker-receipts-v3"));
        Files.createDirectories(temporary.resolve("checkpoints-authenticated-v3"));
        Files.writeString(temporary.resolve("top.json"), "{}\n", StandardCharsets.UTF_8);
        Path receipt = temporary.resolve("worker-receipts-v3/shard-0.json");
        Path checkpoint = temporary.resolve("checkpoints-authenticated-v3/shard-0.json");
        Files.writeString(receipt, "{\"receipt\":0}\n", StandardCharsets.UTF_8);
        Files.writeString(checkpoint, "{\"checkpoint\":0}\n", StandardCharsets.UTF_8);
        RecursiveArtifactManifest.write(temporary);
        var verification = RecursiveArtifactManifest.verify(temporary);
        assertThat(verification.nestedFileCount()).isEqualTo(2);

        Files.writeString(receipt, "{\"receipt\":1}\n", StandardCharsets.UTF_8);
        assertThatThrownBy(() -> RecursiveArtifactManifest.verify(temporary))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("manifest");
        RecursiveArtifactManifest.write(temporary);
        Files.writeString(checkpoint, "{\"checkpoint\":1}\n", StandardCharsets.UTF_8);
        assertThatThrownBy(() -> RecursiveArtifactManifest.verify(temporary))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("manifest");
    }

    private static List<String> tags(Class<?> type) {
        return java.util.Arrays.stream(type.getAnnotationsByType(Tag.class))
                .map(Tag::value).toList();
    }

    private void assertRejected(FocusedInvariantProofReceipt.Receipt proof, String production,
                                DiagnosticDependencyManifest.Manifest manifest) {
        assertThatThrownBy(() -> FocusedInvariantProofReceipt.verify(proof, production, manifest))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static FocusedInvariantProofReceipt.Receipt copy(
            FocusedInvariantProofReceipt.Receipt value, String testClass, String method,
            String sourceSha, String task, String results, String production,
            int tests, int failures, int errors, int skipped, String normalized,
            String payloadHash) {
        return new FocusedInvariantProofReceipt.Receipt(value.schemaVersion(), testClass, method,
                testClass + "#" + method, value.testSourceLogicalPath(), sourceSha, task,
                value.gradleSelector(), results, value.gradleTaskIdentityHash(), production,
                tests, failures, errors, skipped, normalized, value.canonicalJunitEvidenceHash(),
                value.rawJunitXmlSetSha256(), payloadHash);
    }

    private static String hash(String value) {
        return DiagnosticDependencyManifest.sha256(value.getBytes(StandardCharsets.UTF_8));
    }
}
