package com.lolfm.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lolfm.application.PairedDiagnosticAuditGate.AuditRow;
import com.lolfm.application.PairedDiagnosticAuditGate.Contract;
import com.lolfm.application.PairedDiagnosticAuditGate.CorrectnessEvidence;
import com.lolfm.application.PairedDiagnosticAuditGate.ExpectedPair;
import com.lolfm.application.PairedDiagnosticAuditGate.InvariantEvidence;
import com.lolfm.application.PairedDiagnosticAuditGate.ProfileContract;
import com.lolfm.application.PairedDiagnosticAuditGate.ProfileExecution;
import com.lolfm.application.PairedDiagnosticAuditGate.RowEnvelope;
import com.lolfm.application.PairedDiagnosticAuditGate.VerificationEvidence;
import com.lolfm.application.PairedDiagnosticAuditGate.WorkerProcessIdentity;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.UnaryOperator;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@Tag("diagnostic")
@Tag("matchup-v9-structure-attribution-audit-gate")
class MatchupV9StructureAttributionAuditGateTest {
    private static final int SHARDS = 4;
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    private final PairedDiagnosticAuditGate gate = new PairedDiagnosticAuditGate(mapper);
    @TempDir Path temporary;

    @Test void acceptsFrozenCoverageReplayReceiptsAndDistinctProcesses() throws Exception {
        Contract contract = contract();
        writeBundle(contract, false);

        var verified = gate.verify(temporary, contract);

        assertThat(verified.rowsByPairKey()).hasSize(8);
        assertThat(verified.receiptManifest().workerReceiptCount()).isEqualTo(4);
        assertThat(verified.receiptManifest().distinctWorkerProcessCount()).isEqualTo(4);
        assertThat(verified.receiptManifest().pairedRowCount()).isEqualTo(8);
    }

    @Test void rejectsFixtureSeedPairAndShardRelabeling() throws Exception {
        Contract contract = contract();
        List<RowEnvelope> original = rows(contract, 0);

        assertRejected(contract, 0, replace(original, 0, row -> copy(row,
                row.fixtureIndex(), "RELABELLED", row.seedIndex(), row.seed(), row.pairKey(),
                row.profiles(), row.correctness(), row.verification())),
                "Fixture/seed/pair/shard relabel");
        assertRejected(contract, 0, replace(original, 0, row -> copy(row,
                row.fixtureIndex(), row.fixtureId(), 7, row.seed(), row.pairKey(),
                row.profiles(), row.correctness(), row.verification())),
                "Fixture/seed/pair/shard relabel");
        assertRejected(contract, 0, replace(original, 0, row -> copy(row,
                row.fixtureIndex(), row.fixtureId(), row.seedIndex(), row.seed(), "wrong|key",
                row.profiles(), row.correctness(), row.verification())),
                "Fixture/seed/pair/shard relabel");
        assertThatThrownBy(() -> gate.writeShard(temporary, contract, 0,
                sourceCheckpoint(0), rows(contract, 1), process(0),
                PairedDiagnosticAuditGate.environmentIdentityHash()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("relabel");
    }

    @Test void rejectsProfileConfigurationAndReplayProvenanceMutation() throws Exception {
        Contract contract = contract();
        List<RowEnvelope> original = rows(contract, 0);
        ProfileExecution first = original.getFirst().row().profiles().getFirst();

        ProfileExecution wrongConfiguration = profile(first, first.profileId(), hash("wrong"),
                first.replayProvenanceHash(), first.outcomePayloadSha256(),
                first.structureObservationPayloadSha256(), first.maximumStructureHealthHash());
        assertRejected(contract, 0, replaceProfile(original, wrongConfiguration),
                "Profile/configuration/replay provenance");

        ProfileExecution wrongReplay = profile(first, first.profileId(),
                first.configurationHash(), hash("wrong replay"), first.outcomePayloadSha256(),
                first.structureObservationPayloadSha256(), first.maximumStructureHealthHash());
        assertRejected(contract, 0, replaceProfile(original, wrongReplay),
                "Profile/configuration/replay provenance");
    }

    @Test void rejectsOutcomeStructureAndCorrectnessFlagMutation() throws Exception {
        Contract contract = contract();
        List<RowEnvelope> original = rows(contract, 0);
        AuditRow row = original.getFirst().row();
        ProfileExecution first = row.profiles().getFirst();

        AuditRow outcomeChanged = withFirstProfile(row, profile(first, first.profileId(),
                first.configurationHash(), first.replayProvenanceHash(), hash("winner changed"),
                first.structureObservationPayloadSha256(), first.maximumStructureHealthHash()));
        assertRejectedEnvelope(contract, 0,
                replaceEnvelopeKeepingDigest(original, outcomeChanged), "payload changed");

        AuditRow structureChanged = withFirstProfile(row, profile(first, first.profileId(),
                first.configurationHash(), first.replayProvenanceHash(),
                first.outcomePayloadSha256(), hash("structure changed"),
                first.maximumStructureHealthHash()));
        assertRejectedEnvelope(contract, 0,
                replaceEnvelopeKeepingDigest(original, structureChanged), "payload changed");

        CorrectnessEvidence falseFlag = correctness(false, 0);
        assertRejected(contract, 0, replace(original, 0, value -> copy(value,
                value.fixtureIndex(), value.fixtureId(), value.seedIndex(), value.seed(),
                value.pairKey(), value.profiles(), falseFlag, value.verification())),
                "Correctness pass flag");
    }

    @Test void rejectsMaximumHealthAndReplayInstrumentationCoverageMutation() throws Exception {
        Contract contract = contract();
        List<RowEnvelope> original = rows(contract, 0);
        AuditRow row = original.getFirst().row();
        ProfileExecution second = row.profiles().get(1);
        ProfileExecution changedMaximum = profile(second, second.profileId(),
                second.configurationHash(), second.replayProvenanceHash(),
                second.outcomePayloadSha256(), second.structureObservationPayloadSha256(),
                hash("mutated maximum"));
        assertRejected(contract, 0, replace(original, 0, value -> withSecondProfile(
                value, changedMaximum)), "maximum structure HP");

        VerificationEvidence wrongCoverage = new VerificationEvidence(false, true, 0, true);
        assertRejected(contract, 0, replace(original, 0, value -> copy(value,
                value.fixtureIndex(), value.fixtureId(), value.seedIndex(), value.seed(),
                value.pairKey(), value.profiles(), value.correctness(), wrongCoverage)),
                "Replay/instrumentation coverage");
    }

    @Test void rejectsDuplicateMissingFixturePayloadReceiptAndNonDistinctWorkers() throws Exception {
        Contract contract = contract();
        List<RowEnvelope> original = rows(contract, 0);
        assertRejectedEnvelope(contract, 0, List.of(original.getFirst(), original.getFirst()),
                "Duplicate pair");
        assertRejectedEnvelope(contract, 0, List.of(original.getFirst()), "shard coverage");

        writeBundle(contract, false);
        Files.writeString(sourceCheckpoint(0), "tampered\n", StandardCharsets.UTF_8);
        assertThatThrownBy(() -> gate.verify(temporary, contract))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("payload/receipt mismatch");

        Path second = temporary.resolve("same-process");
        writeBundle(second, contract, true);
        assertThatThrownBy(() -> gate.verify(second, contract))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not distinct JVM processes");
    }

    @Test void rejectsNestedReceiptCheckpointAndPostVerificationReceiptMutation() throws Exception {
        Contract contract = contract();

        Path receiptRoot = temporary.resolve("receipt-byte-mutation");
        writeBundle(receiptRoot, contract, false);
        Path receipt = receiptRoot.resolve(PairedDiagnosticAuditGate.RECEIPT_DIRECTORY)
                .resolve("shard-0.json");
        Files.write(receipt, appendByte(Files.readAllBytes(receipt), (byte) ' '));
        assertThatThrownBy(() -> gate.verify(receiptRoot, contract))
                .isInstanceOf(IllegalStateException.class);

        Path checkpointRoot = temporary.resolve("checkpoint-byte-mutation");
        writeBundle(checkpointRoot, contract, false);
        Path checkpoint = checkpointRoot.resolve(PairedDiagnosticAuditGate.CHECKPOINT_DIRECTORY)
                .resolve("shard-0.json");
        Files.write(checkpoint, appendByte(Files.readAllBytes(checkpoint), (byte) ' '));
        assertThatThrownBy(() -> gate.verify(checkpointRoot, contract))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("checkpoint payload/receipt mismatch");

        Path afterVerifyRoot = temporary.resolve("post-verify-replacement");
        writeBundle(afterVerifyRoot, contract, false);
        PairedDiagnosticAuditGate.VerifiedBundle verified = gate.verify(afterVerifyRoot, contract);
        Path verifiedReceipt = afterVerifyRoot.resolve(PairedDiagnosticAuditGate.RECEIPT_DIRECTORY)
                .resolve("shard-1.json");
        Files.write(verifiedReceipt, appendByte(Files.readAllBytes(verifiedReceipt), (byte) ' '));
        assertThatThrownBy(() -> gate.verifyReceiptManifestExact(
                afterVerifyRoot, verified.receiptManifest()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("nested receipt bytes");
    }

    @Test void requiresFocusedProofInsteadOfInventedPopulationZero() {
        Contract source = contract();
        var proof = source.invariantEvidence().displayNameIdentity();
        var relabeled = new FocusedInvariantProofReceipt.Receipt(
                proof.schemaVersion(), proof.testClass(), proof.testMethod(), proof.exactSelector(),
                proof.testSourceLogicalPath(), proof.testSourceSha256(), proof.gradleTask(),
                proof.gradleSelector(), proof.resultsLogicalPath(), proof.gradleTaskIdentityHash(),
                proof.productionGuardHash(), proof.tests(), proof.failures(), proof.errors(),
                proof.skipped(), "FAIL", proof.canonicalJunitEvidenceHash(),
                proof.rawJunitXmlSetSha256(), proof.proofReceiptPayloadSha256());
        Contract invalid = new Contract(
                source.schemaVersion(), source.diagnosticId(), source.contractHash(),
                source.scheduleHash(), source.harnessSourceHash(),
                source.productionSourceHash(), source.dependencyManifest(),
                source.engineImplementationVersion(), source.resourceProvenanceHash(),
                source.draftRuleSetIdentity(), source.draftRuleSetHash(),
                source.draftScoringPolicyHash(), source.shardCount(),
                source.expectedFixtureCount(), source.expectedMatchRowCount(),
                source.expectedPairCount(), source.profiles(), source.expectedPairs(),
                new InvariantEvidence(relabeled,
                        source.invariantEvidence().ineligibleDuplicateRandomConsumption()));

        assertThatThrownBy(() -> gate.writeShard(temporary, invalid, 0,
                sourceCheckpoint(0), rows(source, 0), process(0),
                PairedDiagnosticAuditGate.environmentIdentityHash()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Focused invariant proof receipt mismatch");
    }

    @Test void standaloneFinalizerHasNoWorkerDependency() throws Exception {
        String build = Files.readString(Path.of("build.gradle"), StandardCharsets.UTF_8);
        int start = build.indexOf("// MATCHUP_V9_STRUCTURE_ATTRIBUTION_BUILD_CONTRACT_START");
        int end = build.indexOf("// MATCHUP_V9_STRUCTURE_ATTRIBUTION_BUILD_CONTRACT_END");
        String section = build.substring(start, end);

        assertThat(section).contains("mustRunAfter(\"runMatchupV9StructureAttributionWorkers\")");
        assertThat(section).doesNotContain("tasks.named(\"finalizeMatchupV9StructureAttribution\") {\n    dependsOn");
        assertThat(section).contains("tasks.register(\"runMatchupV9StructureAttribution\")");
    }

    private void assertRejected(
            Contract contract, int shard, List<RowEnvelope> rows, String message
    ) throws Exception {
        writeSourceCheckpoint(temporary, shard);
        assertThatThrownBy(() -> gate.writeShard(temporary, contract, shard,
                sourceCheckpoint(shard), rows, process(shard),
                PairedDiagnosticAuditGate.environmentIdentityHash()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(message);
    }

    private void assertRejectedEnvelope(
            Contract contract, int shard, List<RowEnvelope> rows, String message
    ) throws Exception {
        assertRejected(contract, shard, rows, message);
    }

    private void writeBundle(Contract contract, boolean sameProcess) throws Exception {
        writeBundle(temporary, contract, sameProcess);
    }

    private void writeBundle(Path root, Contract contract, boolean sameProcess) throws Exception {
        for (int shard = 0; shard < SHARDS; shard++) {
            writeSourceCheckpoint(root, shard);
            gate.writeShard(root, contract, shard,
                    root.resolve("checkpoints").resolve("shard-" + shard + ".json"),
                    rows(contract, shard), process(sameProcess ? 0 : shard),
                    PairedDiagnosticAuditGate.environmentIdentityHash());
        }
    }

    private void writeSourceCheckpoint(Path root, int shard) throws IOException {
        Path path = root.resolve("checkpoints").resolve("shard-" + shard + ".json");
        Files.createDirectories(path.getParent());
        byte[] payload = ("{\"syntheticShard\":" + shard + "}\n")
                .getBytes(StandardCharsets.UTF_8);
        Files.write(path, payload);
        Files.writeString(path.resolveSibling(path.getFileName() + ".sha256"),
                PairedDiagnosticAuditGate.sha256(payload) + "  " + path.getFileName() + "\n",
                StandardCharsets.UTF_8);
    }

    private Path sourceCheckpoint(int shard) {
        try {
            writeSourceCheckpoint(temporary, shard);
        } catch (IOException error) {
            throw new IllegalStateException(error);
        }
        return temporary.resolve("checkpoints").resolve("shard-" + shard + ".json");
    }

    private Contract contract() {
        DiagnosticDependencyManifest.Manifest dependencies = syntheticDependencies();
        String productionHash = hash("production source");
        String proofSource = "synthetic/FocusedProof.java";
        String proofSourceSha = DiagnosticDependencyManifest.requireDependency(
                dependencies, proofSource).rawSha256();
        List<ProfileContract> profiles = List.of(
                new ProfileContract("BASELINE", hash("configuration baseline"), "RULES_A"),
                new ProfileContract("CANDIDATE", hash("configuration candidate"), "RULES_B"));
        ArrayList<ExpectedPair> expected = new ArrayList<>();
        for (int fixture = 0; fixture < SHARDS; fixture++) {
            for (int seedIndex = 0; seedIndex < 2; seedIndex++) {
                long seed = 10_000L + fixture * 10L + seedIndex;
                String fixtureId = "FIXTURE_" + fixture;
                expected.add(new ExpectedPair(fixture, fixtureId,
                        fixture < 3 ? "G1_PRIMARY" : "G2_HARD_FEARLESS",
                        "PAIR_" + fixture, "BLUE_" + fixture, "RED_" + fixture,
                        fixture < 3 ? 1 : 2, fixture, seedIndex, seed,
                        fixtureId + "|" + seedIndex + "|" + seed));
            }
        }
        return new Contract(PairedDiagnosticAuditGate.CONTRACT_SCHEMA,
                "SYNTHETIC_ATTRIBUTION_AUDIT_GATE", hash("contract"), hash("schedule"),
                dependencies.harnessSourceHash(), productionHash, dependencies,
                "ENGINE", hash("resources"), "DRAFT_RULES",
                hash("draft rules"), hash("draft scoring"), SHARDS, SHARDS,
                expected.size() * profiles.size(), expected.size(), profiles, expected,
                new InvariantEvidence(
                        FocusedInvariantProofReceipt.syntheticPassing(
                                "synthetic.FocusedProof", "displayIdentity", proofSource,
                                proofSourceSha, "syntheticFocusedProof", productionHash),
                        FocusedInvariantProofReceipt.syntheticPassing(
                                "synthetic.FocusedProof", "ineligibleDuplicateRandom", proofSource,
                                proofSourceSha, "syntheticFocusedProof", productionHash)));
    }

    private DiagnosticDependencyManifest.Manifest syntheticDependencies() {
        try {
            Path source = temporary.resolve("synthetic/FocusedProof.java");
            Files.createDirectories(source.getParent());
            if (!Files.exists(source)) {
                Files.writeString(source, "class FocusedProof {}\n", StandardCharsets.UTF_8);
            }
            return DiagnosticDependencyManifest.create(temporary,
                    "SYNTHETIC_DEPENDENCIES", "EXPLICIT_SYNTHETIC_TEST_DEPENDENCIES",
                    List.of(DiagnosticDependencyManifest.DependencySpec.file(
                            "synthetic/FocusedProof.java")));
        } catch (IOException error) {
            throw new IllegalStateException(error);
        }
    }

    private List<RowEnvelope> rows(Contract contract, int shard) {
        return contract.expectedPairs().stream().filter(value -> value.shardIndex() == shard)
                .map(value -> gate.envelope(row(contract, value))).toList();
    }

    private AuditRow row(Contract contract, ExpectedPair expected) {
        String roster = hash("roster " + expected.fixtureId());
        String history = hash("history " + expected.fixtureId());
        String decision = hash("decision " + expected.fixtureId());
        String draft = hash("draft " + expected.fixtureId());
        String assignment = hash("assignment " + expected.fixtureId());
        ArrayList<ProfileExecution> profiles = new ArrayList<>();
        for (ProfileContract profile : contract.profiles()) {
            String replay = SimulationProvenanceService.replayProvenanceHash(
                    contract.engineImplementationVersion(), profile.activeGameplayRulesVersion(),
                    profile.configurationHash(), contract.resourceProvenanceHash(),
                    expected.blueTeamCode(), expected.redTeamCode(), roster, expected.seed(),
                    expected.seriesGameNumber(), history, contract.draftRuleSetIdentity(),
                    contract.draftRuleSetHash(), contract.draftScoringPolicyHash(), decision,
                    draft, assignment);
            profiles.add(new ProfileExecution(profile.profileId(), profile.configurationHash(),
                    profile.activeGameplayRulesVersion(), contract.engineImplementationVersion(),
                    contract.resourceProvenanceHash(), replay,
                    hash("outcome " + expected.pairKey() + profile.profileId()),
                    hash("structure " + expected.pairKey() + profile.profileId()),
                    hash("maximum structure health"),
                    hash("diagnostics " + expected.pairKey() + profile.profileId())));
        }
        boolean verify = expected.seedIndex() == 0;
        return new AuditRow(expected.fixtureIndex(), expected.fixtureId(),
                expected.fixtureLane(), expected.unorderedTeamPairId(),
                expected.blueTeamCode(), expected.redTeamCode(), expected.seriesGameNumber(),
                expected.seedIndex(), expected.seed(), expected.pairKey(), roster, history,
                decision, draft, assignment, true, hash("source " + expected.pairKey()),
                profiles, correctness(true, 0),
                new VerificationEvidence(verify, true,
                        verify ? contract.profiles().size() : 0, true));
    }

    private List<RowEnvelope> replace(
            List<RowEnvelope> source, int index, UnaryOperator<AuditRow> mutation
    ) {
        ArrayList<RowEnvelope> result = new ArrayList<>(source);
        result.set(index, gate.envelope(mutation.apply(source.get(index).row())));
        return List.copyOf(result);
    }

    private List<RowEnvelope> replaceEnvelopeKeepingDigest(
            List<RowEnvelope> source, AuditRow changed
    ) {
        ArrayList<RowEnvelope> result = new ArrayList<>(source);
        result.set(0, new RowEnvelope(changed, source.getFirst().rowPayloadSha256()));
        return List.copyOf(result);
    }

    private List<RowEnvelope> replaceProfile(
            List<RowEnvelope> source, ProfileExecution profile
    ) {
        return replace(source, 0, row -> withFirstProfile(row, profile));
    }

    private AuditRow withFirstProfile(AuditRow row, ProfileExecution first) {
        return copy(row, row.fixtureIndex(), row.fixtureId(), row.seedIndex(), row.seed(),
                row.pairKey(), List.of(first, row.profiles().get(1)), row.correctness(),
                row.verification());
    }

    private AuditRow withSecondProfile(AuditRow row, ProfileExecution second) {
        return copy(row, row.fixtureIndex(), row.fixtureId(), row.seedIndex(), row.seed(),
                row.pairKey(), List.of(row.profiles().getFirst(), second), row.correctness(),
                row.verification());
    }

    private AuditRow copy(
            AuditRow source,
            int fixtureIndex,
            String fixtureId,
            int seedIndex,
            long seed,
            String pairKey,
            List<ProfileExecution> profiles,
            CorrectnessEvidence correctness,
            VerificationEvidence verification
    ) {
        return new AuditRow(fixtureIndex, fixtureId, source.fixtureLane(),
                source.unorderedTeamPairId(), source.blueTeamCode(), source.redTeamCode(),
                source.seriesGameNumber(), seedIndex, seed, pairKey,
                source.rosterIdentityHash(), source.seriesHistoryBeforeHash(),
                source.draftDecisionHash(), source.finalDraftHash(),
                source.finalAssignmentHash(), source.inputIdentityExact(),
                source.sourcePairPayloadSha256(), profiles, correctness, verification);
    }

    private ProfileExecution profile(
            ProfileExecution source,
            String profileId,
            String configuration,
            String replay,
            String outcome,
            String structure,
            String maximum
    ) {
        return new ProfileExecution(profileId, configuration,
                source.activeGameplayRulesVersion(), source.engineImplementationVersion(),
                source.resourceProvenanceHash(), replay, outcome, structure, maximum,
                source.structuredDiagnosticsHash());
    }

    private CorrectnessEvidence correctness(boolean pass, long maximumDifference) {
        return new CorrectnessEvidence(0, 0, 0, 0, 0, 0, 0,
                maximumDifference, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, pass);
    }

    private WorkerProcessIdentity process(int shard) {
        String name = "synthetic-worker-" + shard;
        long start = 1_000_000L + shard;
        long pid = 10_000L + shard;
        return new WorkerProcessIdentity(name, start, pid,
                PairedDiagnosticAuditGate.processIdentityHash(name, start, pid));
    }

    private static String hash(String value) {
        return PairedDiagnosticAuditGate.sha256(value.getBytes(StandardCharsets.UTF_8));
    }

    private static byte[] appendByte(byte[] source, byte value) {
        byte[] result = java.util.Arrays.copyOf(source, source.length + 1);
        result[source.length] = value;
        return result;
    }
}
