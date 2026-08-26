package com.lolfm.application;

import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Shared test-side checkpoint gate for paired diagnostics. It authenticates execution evidence;
 * it never participates in gameplay or owns match state.
 */
public final class PairedDiagnosticAuditGate {
    public static final String CONTRACT_SCHEMA = "PAIRED_DIAGNOSTIC_AUDIT_CONTRACT_V1";
    public static final String CHECKPOINT_SCHEMA = "PAIRED_DIAGNOSTIC_AUDIT_CHECKPOINT_V1";
    public static final String WORKER_RECEIPT_SCHEMA = "PAIRED_DIAGNOSTIC_WORKER_RECEIPT_V1";
    public static final String RECEIPT_MANIFEST_SCHEMA =
            "PAIRED_DIAGNOSTIC_CHECKPOINT_RECEIPT_MANIFEST_V1";
    public static final String FOCUSED_PROOF_STATUS = "FOCUSED_CONTRACT_TEST";
    public static final String CHECKPOINT_DIRECTORY = "checkpoints-authenticated-v2";
    public static final String RECEIPT_DIRECTORY = "worker-receipts-v2";

    private static final Pattern HASH = Pattern.compile("[0-9a-f]{64}");
    private final ObjectMapper mapper;

    public PairedDiagnosticAuditGate(ObjectMapper source) {
        mapper = Objects.requireNonNull(source, "source").copy().findAndRegisterModules()
                .enable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
                .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
                .disable(SerializationFeature.INDENT_OUTPUT);
    }

    public WorkerReceipt writeShard(
            Path output,
            Contract contract,
            int shardIndex,
            Path sourceCheckpoint,
            List<RowEnvelope> rows
    ) throws IOException {
        return writeShard(output, contract, shardIndex, sourceCheckpoint, rows,
                currentProcessIdentity(), environmentIdentityHash());
    }

    WorkerReceipt writeShard(
            Path output,
            Contract contract,
            int shardIndex,
            Path sourceCheckpoint,
            List<RowEnvelope> rows,
            WorkerProcessIdentity processIdentity,
            String environmentIdentityHash
    ) throws IOException {
        requireContract(contract);
        requireShard(shardIndex, contract.shardCount());
        byte[] sourcePayload = Files.readAllBytes(sourceCheckpoint);
        String sourcePayloadHash = sha256(sourcePayload);
        Path sourceSidecar = sourceCheckpoint.resolveSibling(
                sourceCheckpoint.getFileName() + ".sha256");
        if (!sourcePayloadHash.equals(firstHash(sourceSidecar))) {
            throw new IllegalStateException("Source checkpoint raw byte/sidecar mismatch");
        }
        List<RowEnvelope> immutableRows = List.copyOf(rows);
        Checkpoint checkpoint = new Checkpoint(
                CHECKPOINT_SCHEMA, contract.diagnosticId(), contract.contractHash(),
                contract.scheduleHash(), contract.harnessSourceHash(), shardIndex,
                contract.shardCount(), processIdentity, environmentIdentityHash,
                sourceCheckpoint.getFileName().toString(), sourcePayloadHash,
                coverageHash(immutableRows), executionEvidenceHash(immutableRows), immutableRows);
        validateCheckpoint(checkpoint, contract, expectedByKey(contract), shardIndex);

        Path checkpointPath = checkpointPath(output, shardIndex);
        byte[] checkpointPayload = canonicalBytes(checkpoint);
        writeAtomic(checkpointPath, checkpointPayload);
        writeAtomic(sidecar(checkpointPath), sidecarBytes(checkpointPath, checkpointPayload));

        WorkerReceipt receipt = new WorkerReceipt(
                WORKER_RECEIPT_SCHEMA, contract.diagnosticId(), contract.contractHash(),
                contract.scheduleHash(), contract.harnessSourceHash(), shardIndex,
                contract.shardCount(), processIdentity, environmentIdentityHash,
                sourceCheckpoint.getFileName().toString(), sourcePayloadHash,
                checkpointPath.getFileName().toString(), sha256(checkpointPayload),
                immutableRows.size(), checkpoint.coverageHash(),
                checkpoint.executionEvidenceHash());
        writeAtomic(receiptPath(output, shardIndex), canonicalBytes(receipt));
        return receipt;
    }

    public VerifiedBundle verify(Path output, Contract contract) throws IOException {
        requireContract(contract);
        Map<String, ExpectedPair> expected = expectedByKey(contract);
        Map<String, RowEnvelope> rowsByKey = new LinkedHashMap<>();
        ArrayList<WorkerReceipt> receipts = new ArrayList<>();
        Set<String> processIdentities = new HashSet<>();
        Set<Long> processIds = new HashSet<>();
        for (int shard = 0; shard < contract.shardCount(); shard++) {
            Path receiptPath = receiptPath(output, shard);
            if (!Files.isRegularFile(receiptPath)) {
                throw new IllegalStateException("Missing worker receipt for shard " + shard);
            }
            WorkerReceipt receipt = mapper.readValue(receiptPath.toFile(), WorkerReceipt.class);
            validateReceipt(receipt, contract, shard);
            if (!processIdentities.add(receipt.processIdentity().processIdentityHash())
                    || !processIds.add(receipt.processIdentity().processId())) {
                throw new IllegalStateException("Worker receipts are not distinct JVM processes");
            }

            Path sourceCheckpoint = output.resolve("checkpoints")
                    .resolve(receipt.sourceCheckpointFile());
            byte[] sourcePayload = Files.readAllBytes(sourceCheckpoint);
            if (!sha256(sourcePayload).equals(receipt.sourceCheckpointPayloadSha256())
                    || !sha256(sourcePayload).equals(firstHash(sidecar(sourceCheckpoint)))) {
                throw new IllegalStateException("Source checkpoint payload/receipt mismatch");
            }

            Path checkpointPath = checkpointPath(output, shard);
            byte[] payload = Files.readAllBytes(checkpointPath);
            if (!sha256(payload).equals(receipt.auditCheckpointPayloadSha256())
                    || !sha256(payload).equals(firstHash(sidecar(checkpointPath)))) {
                throw new IllegalStateException("Audit checkpoint payload/receipt mismatch");
            }
            Checkpoint checkpoint = mapper.readValue(payload, Checkpoint.class);
            validateCheckpoint(checkpoint, contract, expected, shard);
            if (!checkpoint.sourceCheckpointPayloadSha256()
                    .equals(receipt.sourceCheckpointPayloadSha256())
                    || !checkpoint.coverageHash().equals(receipt.coverageHash())
                    || !checkpoint.executionEvidenceHash()
                    .equals(receipt.executionEvidenceHash())
                    || checkpoint.rows().size() != receipt.rowCount()) {
                throw new IllegalStateException("Checkpoint differs from worker receipt");
            }
            for (RowEnvelope row : checkpoint.rows()) {
                if (rowsByKey.put(row.row().pairKey(), row) != null) {
                    throw new IllegalStateException("Duplicate pair evidence: "
                            + row.row().pairKey());
                }
            }
            receipts.add(receipt);
        }
        if (!rowsByKey.keySet().equals(expected.keySet())) {
            throw new IllegalStateException("Frozen pair/fixture coverage mismatch");
        }
        List<WorkerReceipt> orderedReceipts = receipts.stream()
                .sorted(Comparator.comparingInt(WorkerReceipt::shardIndex)).toList();
        ReceiptManifest manifest = new ReceiptManifest(
                RECEIPT_MANIFEST_SCHEMA, contract.diagnosticId(), contract.shardCount(),
                receipts.size(), processIdentities.size(), rowsByKey.size(),
                receiptManifestHash(orderedReceipts), orderedReceipts);
        return new VerifiedBundle(Map.copyOf(rowsByKey), manifest);
    }

    public String canonicalHash(Object value) {
        try {
            return sha256(mapper.writeValueAsBytes(value));
        } catch (IOException error) {
            throw new IllegalStateException("Cannot hash diagnostic evidence", error);
        }
    }

    public RowEnvelope envelope(AuditRow row) {
        return new RowEnvelope(row, canonicalHash(row));
    }

    public static WorkerProcessIdentity currentProcessIdentity() {
        var runtime = ManagementFactory.getRuntimeMXBean();
        long pid = ProcessHandle.current().pid();
        long start = runtime.getStartTime();
        String runtimeName = runtime.getName();
        return new WorkerProcessIdentity(runtimeName, start, pid,
                processIdentityHash(runtimeName, start, pid));
    }

    public static String environmentIdentityHash() {
        String canonical = "java.vendor=" + System.getProperty("java.vendor") + '\n'
                + "java.version=" + System.getProperty("java.version") + '\n'
                + "java.vm.name=" + System.getProperty("java.vm.name") + '\n'
                + "os.name=" + System.getProperty("os.name") + '\n'
                + "os.arch=" + System.getProperty("os.arch") + '\n';
        return sha256(canonical.getBytes(StandardCharsets.UTF_8));
    }

    public static String processIdentityHash(String runtimeName, long startTime, long processId) {
        String canonical = "runtimeName=" + runtimeName + '\n'
                + "runtimeStartTime=" + startTime + '\n'
                + "processId=" + processId + '\n';
        return sha256(canonical.getBytes(StandardCharsets.UTF_8));
    }

    private void requireContract(Contract contract) {
        Objects.requireNonNull(contract, "contract");
        if (!CONTRACT_SCHEMA.equals(contract.schemaVersion())
                || contract.shardCount() < 1
                || contract.expectedPairs().isEmpty()
                || contract.expectedMatchRowCount() != contract.expectedPairs().size()
                        * contract.profiles().size()
                || contract.expectedPairCount() != contract.expectedPairs().size()) {
            throw new IllegalArgumentException("Invalid paired diagnostic audit contract");
        }
        requireHash(contract.contractHash(), "contractHash");
        requireHash(contract.scheduleHash(), "scheduleHash");
        requireHash(contract.harnessSourceHash(), "harnessSourceHash");
        requireHash(contract.resourceProvenanceHash(), "resourceProvenanceHash");
        requireHash(contract.draftRuleSetHash(), "draftRuleSetHash");
        requireHash(contract.draftScoringPolicyHash(), "draftScoringPolicyHash");
        if (!FOCUSED_PROOF_STATUS.equals(
                contract.invariantEvidence().displayNameIdentity().status())
                || !FOCUSED_PROOF_STATUS.equals(contract.invariantEvidence()
                .ineligibleDuplicateRandomConsumption().status())) {
            throw new IllegalStateException(
                    "Natural population cannot claim unobserved focused invariants as zero");
        }
    }

    private Map<String, ExpectedPair> expectedByKey(Contract contract) {
        LinkedHashMap<String, ExpectedPair> expected = new LinkedHashMap<>();
        Set<String> fixtures = new HashSet<>();
        for (ExpectedPair pair : contract.expectedPairs()) {
            requireShard(pair.shardIndex(), contract.shardCount());
            if (pair.fixtureIndex() % contract.shardCount() != pair.shardIndex()
                    || !pair.pairKey().equals(pair.fixtureId() + "|" + pair.seedIndex()
                    + "|" + pair.seed())
                    || expected.put(pair.pairKey(), pair) != null) {
                throw new IllegalStateException("Invalid frozen expected pair identity");
            }
            fixtures.add(pair.fixtureIndex() + "|" + pair.fixtureId());
        }
        if (fixtures.size() != contract.expectedFixtureCount()) {
            throw new IllegalStateException("Frozen fixture cardinality mismatch");
        }
        return expected;
    }

    private void validateReceipt(WorkerReceipt receipt, Contract contract, int shard) {
        if (!WORKER_RECEIPT_SCHEMA.equals(receipt.schemaVersion())
                || !receipt.diagnosticId().equals(contract.diagnosticId())
                || !receipt.contractHash().equals(contract.contractHash())
                || !receipt.scheduleHash().equals(contract.scheduleHash())
                || !receipt.harnessSourceHash().equals(contract.harnessSourceHash())
                || receipt.shardIndex() != shard
                || receipt.shardCount() != contract.shardCount()
                || !receipt.environmentIdentityHash().equals(environmentIdentityHash())
                || !validProcessIdentity(receipt.processIdentity())) {
            throw new IllegalStateException("Worker receipt binding differs");
        }
        requireHash(receipt.sourceCheckpointPayloadSha256(),
                "sourceCheckpointPayloadSha256");
        requireHash(receipt.auditCheckpointPayloadSha256(),
                "auditCheckpointPayloadSha256");
        requireHash(receipt.coverageHash(), "coverageHash");
        requireHash(receipt.executionEvidenceHash(), "executionEvidenceHash");
    }

    private void validateCheckpoint(
            Checkpoint checkpoint,
            Contract contract,
            Map<String, ExpectedPair> expected,
            int shard
    ) {
        if (!CHECKPOINT_SCHEMA.equals(checkpoint.schemaVersion())
                || !checkpoint.diagnosticId().equals(contract.diagnosticId())
                || !checkpoint.contractHash().equals(contract.contractHash())
                || !checkpoint.scheduleHash().equals(contract.scheduleHash())
                || !checkpoint.harnessSourceHash().equals(contract.harnessSourceHash())
                || checkpoint.shardIndex() != shard
                || checkpoint.shardCount() != contract.shardCount()
                || !checkpoint.environmentIdentityHash().equals(environmentIdentityHash())
                || !validProcessIdentity(checkpoint.processIdentity())
                || !checkpoint.coverageHash().equals(coverageHash(checkpoint.rows()))
                || !checkpoint.executionEvidenceHash()
                .equals(executionEvidenceHash(checkpoint.rows()))) {
            throw new IllegalStateException("Audit checkpoint binding/digest differs");
        }
        int expectedRows = 0;
        for (ExpectedPair pair : expected.values()) {
            if (pair.shardIndex() == shard) expectedRows++;
        }
        if (checkpoint.rows().size() != expectedRows) {
            throw new IllegalStateException("Audit checkpoint shard coverage differs");
        }
        Set<String> seen = new HashSet<>();
        int replay = 0;
        int instrumentation = 0;
        for (RowEnvelope envelope : checkpoint.rows()) {
            if (!canonicalHash(envelope.row()).equals(envelope.rowPayloadSha256())) {
                throw new IllegalStateException("Pair outcome/observation payload changed");
            }
            AuditRow row = envelope.row();
            ExpectedPair pair = expected.get(row.pairKey());
            if (pair == null || pair.shardIndex() != shard || !metadataMatches(row, pair)) {
                throw new IllegalStateException("Fixture/seed/pair/shard relabel detected");
            }
            if (!seen.add(row.pairKey())) {
                throw new IllegalStateException("Duplicate pair evidence: " + row.pairKey());
            }
            validateProfiles(row, contract);
            validateCorrectness(row.correctness());
            requireHash(row.sourcePairPayloadSha256(), "sourcePairPayloadSha256");
            boolean shouldVerify = pair.seedIndex() == 0;
            if (row.verification().replayChecked() != shouldVerify
                    || !row.verification().replayExact()
                    || row.verification().instrumentationProfilesChecked()
                    != (shouldVerify ? contract.profiles().size() : 0)
                    || !row.verification().instrumentationTimelineRandomExact()) {
                throw new IllegalStateException("Replay/instrumentation coverage differs");
            }
            if (shouldVerify) replay++;
            instrumentation += row.verification().instrumentationProfilesChecked();
        }
        long expectedReplay = expected.values().stream().filter(value ->
                value.shardIndex() == shard && value.seedIndex() == 0).count();
        if (replay != expectedReplay
                || instrumentation != expectedReplay * contract.profiles().size()) {
            throw new IllegalStateException("Replay/instrumentation counts differ");
        }
    }

    private boolean metadataMatches(AuditRow row, ExpectedPair expected) {
        return row.fixtureIndex() == expected.fixtureIndex()
                && row.fixtureId().equals(expected.fixtureId())
                && row.fixtureLane().equals(expected.fixtureLane())
                && row.unorderedTeamPairId().equals(expected.unorderedTeamPairId())
                && row.blueTeamCode().equals(expected.blueTeamCode())
                && row.redTeamCode().equals(expected.redTeamCode())
                && row.seriesGameNumber() == expected.seriesGameNumber()
                && row.seedIndex() == expected.seedIndex()
                && row.seed() == expected.seed()
                && row.pairKey().equals(expected.pairKey())
                && row.inputIdentityExact();
    }

    private void validateProfiles(AuditRow row, Contract contract) {
        if (row.profiles().size() != contract.profiles().size()) {
            throw new IllegalStateException("Profile coverage differs");
        }
        Set<String> maximumHealth = new HashSet<>();
        for (int index = 0; index < contract.profiles().size(); index++) {
            ProfileContract expected = contract.profiles().get(index);
            ProfileExecution actual = row.profiles().get(index);
            String replay = SimulationProvenanceService.replayProvenanceHash(
                    contract.engineImplementationVersion(), expected.activeGameplayRulesVersion(),
                    expected.configurationHash(), contract.resourceProvenanceHash(),
                    row.blueTeamCode(), row.redTeamCode(), row.rosterIdentityHash(), row.seed(),
                    row.seriesGameNumber(), row.seriesHistoryBeforeHash(),
                    contract.draftRuleSetIdentity(), contract.draftRuleSetHash(),
                    contract.draftScoringPolicyHash(), row.draftDecisionHash(),
                    row.finalDraftHash(), row.finalAssignmentHash());
            if (!actual.profileId().equals(expected.profileId())
                    || !actual.configurationHash().equals(expected.configurationHash())
                    || !actual.activeGameplayRulesVersion()
                    .equals(expected.activeGameplayRulesVersion())
                    || !actual.engineImplementationVersion()
                    .equals(contract.engineImplementationVersion())
                    || !actual.resourceProvenanceHash()
                    .equals(contract.resourceProvenanceHash())
                    || !actual.replayProvenanceHash().equals(replay)) {
                throw new IllegalStateException(
                        "Profile/configuration/replay provenance differs from frozen input");
            }
            requireHash(actual.outcomePayloadSha256(), "outcomePayloadSha256");
            requireHash(actual.structureObservationPayloadSha256(),
                    "structureObservationPayloadSha256");
            requireHash(actual.maximumStructureHealthHash(),
                    "maximumStructureHealthHash");
            requireHash(actual.structuredDiagnosticsHash(), "structuredDiagnosticsHash");
            maximumHealth.add(actual.maximumStructureHealthHash());
        }
        if (maximumHealth.size() != 1 || row.correctness().maximumHealthDifferenceCount() != 0) {
            throw new IllegalStateException(
                    "Profile changed maximum structure HP exact correctness gate");
        }
    }

    private static void validateCorrectness(CorrectnessEvidence value) {
        long errors = value.timeoutCount() + value.gameplayIntegrityErrorCount()
                + value.invalidStructureHealthCount()
                + value.duplicateStructuredStructureActionCount()
                + value.nexusDestroyedWithTurretAliveCount()
                + value.postFinishStructureMutationEventCount()
                + value.impossibleRespawnStateTransitionCount()
                + value.maximumHealthDifferenceCount()
                + value.directRandomCallCount() + value.perspectiveMismatchCount()
                + value.offContributionCount();
        if (errors != 0 || !value.pass()) {
            throw new IllegalStateException("Correctness pass flag/count evidence differs");
        }
    }

    private boolean validProcessIdentity(WorkerProcessIdentity value) {
        return value != null && value.processId() > 0 && value.runtimeStartTime() > 0
                && value.processIdentityHash().equals(processIdentityHash(
                value.runtimeName(), value.runtimeStartTime(), value.processId()));
    }

    private String coverageHash(List<RowEnvelope> rows) {
        StringBuilder value = new StringBuilder("schema=PAIR_COVERAGE_V1\n");
        rows.stream().map(RowEnvelope::row).sorted(Comparator
                .comparingInt(AuditRow::fixtureIndex).thenComparingInt(AuditRow::seedIndex))
                .forEach(row -> value.append(row.fixtureIndex()).append('|')
                        .append(row.fixtureId()).append('|').append(row.seedIndex()).append('|')
                        .append(row.seed()).append('|').append(row.pairKey()).append('\n'));
        return sha256(value.toString().getBytes(StandardCharsets.UTF_8));
    }

    private String executionEvidenceHash(List<RowEnvelope> rows) {
        StringBuilder value = new StringBuilder("schema=PAIR_EXECUTION_EVIDENCE_V1\n");
        rows.stream().sorted(Comparator.comparing(envelope -> envelope.row().pairKey()))
                .forEach(row -> value.append(row.row().pairKey()).append('|')
                        .append(row.rowPayloadSha256()).append('\n'));
        return sha256(value.toString().getBytes(StandardCharsets.UTF_8));
    }

    private String receiptManifestHash(List<WorkerReceipt> receipts) {
        StringBuilder value = new StringBuilder("schema=").append(RECEIPT_MANIFEST_SCHEMA)
                .append('\n');
        receipts.forEach(receipt -> value.append(receipt.shardIndex()).append('|')
                .append(receipt.processIdentity().processIdentityHash()).append('|')
                .append(receipt.sourceCheckpointPayloadSha256()).append('|')
                .append(receipt.auditCheckpointPayloadSha256()).append('|')
                .append(receipt.coverageHash()).append('|')
                .append(receipt.executionEvidenceHash()).append('\n'));
        return sha256(value.toString().getBytes(StandardCharsets.UTF_8));
    }

    private byte[] canonicalBytes(Object value) throws IOException {
        byte[] bytes = mapper.writeValueAsBytes(value);
        byte[] result = Arrays.copyOf(bytes, bytes.length + 1);
        result[bytes.length] = '\n';
        return result;
    }

    private static void writeAtomic(Path path, byte[] payload) throws IOException {
        Files.createDirectories(path.getParent());
        Path temporary = path.resolveSibling(path.getFileName() + ".tmp-"
                + ProcessHandle.current().pid() + '-' + Thread.currentThread().getId());
        Files.write(temporary, payload);
        try {
            Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (java.nio.file.AtomicMoveNotSupportedException error) {
            Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static byte[] sidecarBytes(Path path, byte[] payload) {
        return (sha256(payload) + "  " + path.getFileName() + "\n")
                .getBytes(StandardCharsets.UTF_8);
    }

    private static String firstHash(Path sidecar) throws IOException {
        String value = Files.readString(sidecar, StandardCharsets.UTF_8).trim();
        int separator = value.indexOf(' ');
        return separator < 0 ? value : value.substring(0, separator);
    }

    private static Path checkpointPath(Path output, int shard) {
        return output.resolve(CHECKPOINT_DIRECTORY).resolve("shard-" + shard + ".json");
    }

    private static Path receiptPath(Path output, int shard) {
        return output.resolve(RECEIPT_DIRECTORY).resolve("shard-" + shard + ".json");
    }

    private static Path sidecar(Path path) {
        return path.resolveSibling(path.getFileName() + ".sha256");
    }

    private static void requireShard(int shard, int count) {
        if (shard < 0 || shard >= count) {
            throw new IllegalArgumentException("Invalid shard ownership");
        }
    }

    private static void requireHash(String value, String field) {
        if (value == null || !HASH.matcher(value).matches()) {
            throw new IllegalArgumentException(field + " must be a SHA-256 hash");
        }
    }

    static String sha256(byte[] value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException(error);
        }
    }

    public record Contract(
            String schemaVersion,
            String diagnosticId,
            String contractHash,
            String scheduleHash,
            String harnessSourceHash,
            String engineImplementationVersion,
            String resourceProvenanceHash,
            String draftRuleSetIdentity,
            String draftRuleSetHash,
            String draftScoringPolicyHash,
            int shardCount,
            int expectedFixtureCount,
            int expectedMatchRowCount,
            int expectedPairCount,
            List<ProfileContract> profiles,
            List<ExpectedPair> expectedPairs,
            InvariantEvidence invariantEvidence
    ) {
        public Contract {
            profiles = List.copyOf(profiles);
            expectedPairs = List.copyOf(expectedPairs);
        }
    }

    public record ProfileContract(
            String profileId,
            String configurationHash,
            String activeGameplayRulesVersion
    ) { }

    public record ExpectedPair(
            int fixtureIndex,
            String fixtureId,
            String fixtureLane,
            String unorderedTeamPairId,
            String blueTeamCode,
            String redTeamCode,
            int seriesGameNumber,
            int shardIndex,
            int seedIndex,
            long seed,
            String pairKey
    ) { }

    public record InvariantEvidence(
            InvariantProof displayNameIdentity,
            InvariantProof ineligibleDuplicateRandomConsumption
    ) { }

    public record InvariantProof(String status, String evidenceId) { }

    public record WorkerProcessIdentity(
            String runtimeName,
            long runtimeStartTime,
            long processId,
            String processIdentityHash
    ) { }

    public record AuditRow(
            int fixtureIndex,
            String fixtureId,
            String fixtureLane,
            String unorderedTeamPairId,
            String blueTeamCode,
            String redTeamCode,
            int seriesGameNumber,
            int seedIndex,
            long seed,
            String pairKey,
            String rosterIdentityHash,
            String seriesHistoryBeforeHash,
            String draftDecisionHash,
            String finalDraftHash,
            String finalAssignmentHash,
            boolean inputIdentityExact,
            String sourcePairPayloadSha256,
            List<ProfileExecution> profiles,
            CorrectnessEvidence correctness,
            VerificationEvidence verification
    ) {
        public AuditRow {
            profiles = List.copyOf(profiles);
        }
    }

    public record ProfileExecution(
            String profileId,
            String configurationHash,
            String activeGameplayRulesVersion,
            String engineImplementationVersion,
            String resourceProvenanceHash,
            String replayProvenanceHash,
            String outcomePayloadSha256,
            String structureObservationPayloadSha256,
            String maximumStructureHealthHash,
            String structuredDiagnosticsHash
    ) { }

    public record CorrectnessEvidence(
            long timeoutCount,
            long gameplayIntegrityErrorCount,
            long invalidStructureHealthCount,
            long duplicateStructuredStructureActionCount,
            long nexusDestroyedWithTurretAliveCount,
            long postFinishStructureMutationEventCount,
            long impossibleRespawnStateTransitionCount,
            long maximumHealthDifferenceCount,
            long directRandomCallCount,
            long perspectiveMismatchCount,
            long offContributionCount,
            boolean pass
    ) { }

    public record VerificationEvidence(
            boolean replayChecked,
            boolean replayExact,
            int instrumentationProfilesChecked,
            boolean instrumentationTimelineRandomExact
    ) { }

    public record RowEnvelope(AuditRow row, String rowPayloadSha256) { }

    public record Checkpoint(
            String schemaVersion,
            String diagnosticId,
            String contractHash,
            String scheduleHash,
            String harnessSourceHash,
            int shardIndex,
            int shardCount,
            WorkerProcessIdentity processIdentity,
            String environmentIdentityHash,
            String sourceCheckpointFile,
            String sourceCheckpointPayloadSha256,
            String coverageHash,
            String executionEvidenceHash,
            List<RowEnvelope> rows
    ) {
        public Checkpoint {
            rows = List.copyOf(rows);
        }
    }

    public record WorkerReceipt(
            String schemaVersion,
            String diagnosticId,
            String contractHash,
            String scheduleHash,
            String harnessSourceHash,
            int shardIndex,
            int shardCount,
            WorkerProcessIdentity processIdentity,
            String environmentIdentityHash,
            String sourceCheckpointFile,
            String sourceCheckpointPayloadSha256,
            String auditCheckpointFile,
            String auditCheckpointPayloadSha256,
            int rowCount,
            String coverageHash,
            String executionEvidenceHash
    ) { }

    public record ReceiptManifest(
            String schemaVersion,
            String diagnosticId,
            int expectedWorkerShardCount,
            int workerReceiptCount,
            int distinctWorkerProcessCount,
            int pairedRowCount,
            String receiptManifestHash,
            List<WorkerReceipt> workers
    ) {
        public ReceiptManifest {
            workers = List.copyOf(workers);
        }
    }

    public record VerifiedBundle(
            Map<String, RowEnvelope> rowsByPairKey,
            ReceiptManifest receiptManifest
    ) {
        public VerifiedBundle {
            rowsByPairKey = Map.copyOf(rowsByPairKey);
        }
    }
}
