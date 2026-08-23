package com.lolfm.application;

import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.lolfm.application.Phase13GB3FrozenHoldoutContract.FrozenContract;
import com.lolfm.application.Phase13GB3FrozenHoldoutContract.HoldoutJob;
import com.lolfm.application.Phase13GB3FrozenHoldoutContract.RunGuard;
import com.lolfm.application.Phase13GB3HoldoutModel.CheckpointExecutionEvidence;
import com.lolfm.application.Phase13GB3HoldoutModel.CheckpointPayloadReceipt;
import com.lolfm.application.Phase13GB3HoldoutModel.CheckpointReceiptManifest;
import com.lolfm.application.Phase13GB3HoldoutModel.DeterminismReplayEvidence;
import com.lolfm.application.Phase13GB3HoldoutModel.FixtureCheckpoint;
import com.lolfm.application.Phase13GB3HoldoutModel.MatchExecutionEvidence;
import com.lolfm.application.Phase13GB3HoldoutModel.MatchRow;
import com.lolfm.application.Phase13GB3HoldoutModel.WorkerReceipt;
import com.lolfm.simulator.SimulationRuntimeProfileId;
import com.lolfm.simulator.SimulationRuntimeProfiles;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Authenticated one-time B3 checkpoint, receipt, and finalizer boundary. */
public final class Phase13GB3CheckpointStore {
    static final String CHECKPOINT_DIRECTORY_NAME = "checkpoints-authenticated-v1";
    static final String RECEIPT_DIRECTORY_NAME = "worker-receipts-v1";
    static final String AUTHORIZATION_DIRECTORY_NAME = "holdout-authorization-v1";
    static final String CONTRACT_FILE = "phase13g-b3-frozen-contract.json";
    static final String CONTRACT_HASH_FILE = "phase13g-b3-frozen-contract.sha256";
    static final int OFFICIAL_SHARD_COUNT = 4;

    private final ObjectMapper mapper;

    public Phase13GB3CheckpointStore(ObjectMapper sourceMapper) {
        mapper = canonicalMapper(sourceMapper);
    }

    public FrozenContract readFrozenContract(Path output) throws IOException {
        Path contractPath = output.resolve(CONTRACT_FILE);
        Path hashPath = output.resolve(CONTRACT_HASH_FILE);
        if (!Files.isRegularFile(contractPath) || !Files.isRegularFile(hashPath)) {
            throw new IllegalStateException(
                    "B3 official runner requires a pre-existing frozen contract hash");
        }
        String[] fields = Files.readString(hashPath, StandardCharsets.UTF_8)
                .strip().split("  ", 2);
        String actual = sha256(Files.readAllBytes(contractPath));
        if (fields.length != 2 || !fields[1].equals(CONTRACT_FILE)
                || !fields[0].equals(actual)) {
            throw new IllegalStateException("B3 frozen contract bytes were modified");
        }
        return mapper.readValue(contractPath.toFile(), FrozenContract.class);
    }

    public String frozenContractHash(Path output) throws IOException {
        readFrozenContract(output);
        return sha256(Files.readAllBytes(output.resolve(CONTRACT_FILE)));
    }

    public String guardHash(RunGuard guard) {
        return canonicalHash(guard);
    }

    public Path checkpointDirectory(Path output) {
        return output.resolve(CHECKPOINT_DIRECTORY_NAME);
    }

    public Path checkpointPath(
            Path checkpointDirectory,
            int fixtureIndex,
            Phase13GB1AuditSchedule.Fixture fixture
    ) {
        return checkpointDirectory.resolve(String.format(
                java.util.Locale.ROOT, "%03d-%s.json", fixtureIndex, fixture.fixtureId()));
    }

    public void authorizeShard(Path output, String contractHash, int shardIndex)
            throws IOException {
        Path directory = output.resolve(AUTHORIZATION_DIRECTORY_NAME);
        Path token = directory.resolve("shard-" + shardIndex + ".authorized");
        Path started = directory.resolve("shard-" + shardIndex + ".started");
        Path receipt = workerReceiptPath(output, shardIndex);
        if (Files.isRegularFile(receipt)) {
            throw new IllegalStateException(
                    "B3 holdout shard was already completed and cannot be rerun: "
                            + shardIndex);
        }
        if (Files.isRegularFile(token)) {
            requireAuthorizationBytes(token, contractHash);
            Files.move(token, started, StandardCopyOption.ATOMIC_MOVE);
        } else if (Files.isRegularFile(started)) {
            requireAuthorizationBytes(started, contractHash);
        } else {
            throw new IllegalStateException("Missing one-time B3 holdout authorization");
        }
    }

    private void requireAuthorizationBytes(Path path, String contractHash) throws IOException {
        String expected = "schema=PHASE_13G_B3_ONE_TIME_SHARD_AUTHORIZATION_V1\n"
                + "frozenContractHash=" + contractHash + "\n"
                + "calibrationMatchExecutionCount=0\n"
                + "holdoutMatchExecutionCountAtFreeze=0\n";
        if (!Files.readString(path, StandardCharsets.UTF_8).equals(expected)) {
            throw new IllegalStateException("B3 holdout authorization was modified");
        }
    }

    public FixtureCheckpoint createCheckpoint(
            String frozenContractHash,
            String runGuardHash,
            RunGuard runGuard,
            Phase13GB2CalibrationModel.FixedDraftRow fixedDraft,
            DeterminismReplayEvidence replay,
            List<MatchRow> rows
    ) {
        List<MatchRow> immutable = List.copyOf(rows);
        return new FixtureCheckpoint(
                Phase13GB3HoldoutModel.CHECKPOINT_SCHEMA,
                frozenContractHash,
                runGuardHash,
                runGuard,
                fixedDraft,
                replay,
                executionEvidence(fixedDraft, replay, immutable),
                immutable);
    }

    public FixtureCheckpoint readAndValidate(
            Path path,
            FrozenContract contract,
            RunGuard guard,
            Phase13GB1AuditSchedule.Fixture fixture
    ) throws IOException {
        FixtureCheckpoint checkpoint = mapper.readValue(
                Files.readAllBytes(path), FixtureCheckpoint.class);
        validate(checkpoint, contract, guard, fixture);
        return checkpoint;
    }

    public CheckpointPayloadReceipt writeAtomic(
            Path path,
            int fixtureIndex,
            FixtureCheckpoint checkpoint,
            FrozenContract contract,
            RunGuard guard,
            Phase13GB1AuditSchedule.Fixture fixture
    ) throws IOException {
        validate(checkpoint, contract, guard, fixture);
        byte[] payload = canonicalBytesWithNewline(checkpoint);
        writeAtomicBytes(path, payload);
        return payloadReceipt(path, fixtureIndex, fixture, payload);
    }

    public CheckpointPayloadReceipt readPayloadReceipt(
            Path path,
            int fixtureIndex,
            Phase13GB1AuditSchedule.Fixture fixture
    ) throws IOException {
        return payloadReceipt(path, fixtureIndex, fixture, Files.readAllBytes(path));
    }

    public WorkerReceipt writeWorkerReceipt(
            Path output,
            String frozenContractHash,
            String runGuardHash,
            int shardIndex,
            List<String> ownedFixtureIds,
            List<CheckpointPayloadReceipt> checkpoints
    ) throws IOException {
        WorkerReceipt receipt = new WorkerReceipt(
                Phase13GB3HoldoutModel.WORKER_RECEIPT_SCHEMA,
                frozenContractHash,
                runGuardHash,
                shardIndex,
                OFFICIAL_SHARD_COUNT,
                workerJvmIdentityHash(),
                ownedFixtureIds,
                checkpoints);
        Path path = workerReceiptPath(output, shardIndex);
        writeAtomicBytes(path, canonicalBytesWithNewline(receipt));
        return receipt;
    }

    public VerifiedOfficialEvidence readOfficialEvidence(
            Path output,
            FrozenContract contract,
            RunGuard expectedGuard,
            Phase13GB1AuditSchedule.Schedule schedule
    ) throws IOException {
        schedule = Phase13GB1AuditSchedule.requireFrozen(schedule);
        String contractHash = frozenContractHash(output);
        String runGuardHash = guardHash(expectedGuard);
        if (!contractHash.equals(expectedGuard.frozenContractHash())) {
            throw new IllegalStateException("B3 contract and run guard hashes differ");
        }
        ArrayList<WorkerReceipt> receipts = new ArrayList<>();
        Set<String> jvms = new HashSet<>();
        for (int shard = 0; shard < OFFICIAL_SHARD_COUNT; shard++) {
            Path path = workerReceiptPath(output, shard);
            if (!Files.isRegularFile(path)) {
                throw new IllegalStateException("Missing B3 worker receipt for shard " + shard);
            }
            WorkerReceipt receipt = mapper.readValue(path.toFile(), WorkerReceipt.class);
            List<String> expectedOwned = ownedFixtureIds(schedule, shard);
            if (!receipt.frozenContractHash().equals(contractHash)
                    || !receipt.runGuardHash().equals(runGuardHash)
                    || receipt.shardIndex() != shard
                    || receipt.shardCount() != OFFICIAL_SHARD_COUNT
                    || !receipt.ownedFixtureIds().equals(expectedOwned)
                    || receipt.checkpoints().size() != expectedOwned.size()) {
                throw new IllegalStateException("B3 worker receipt binding differs");
            }
            receipts.add(receipt);
            jvms.add(receipt.workerJvmIdentityHash());
        }
        if (jvms.size() != OFFICIAL_SHARD_COUNT) {
            throw new IllegalStateException("B3 requires four distinct fresh worker JVMs");
        }

        List<Phase13GB1AuditSchedule.Fixture> fixtures = schedule.allFixtures();
        CheckpointPayloadReceipt[] ordered = new CheckpointPayloadReceipt[fixtures.size()];
        for (WorkerReceipt receipt : receipts) {
            for (CheckpointPayloadReceipt value : receipt.checkpoints()) {
                int index = value.fixtureIndex();
                if (index < 0 || index >= fixtures.size()
                        || index % OFFICIAL_SHARD_COUNT != receipt.shardIndex()
                        || ordered[index] != null) {
                    throw new IllegalStateException("B3 modulo shard ownership differs");
                }
                ordered[index] = value;
            }
        }

        ArrayList<FixtureCheckpoint> checkpoints = new ArrayList<>();
        ArrayList<CheckpointPayloadReceipt> normalized = new ArrayList<>();
        for (int index = 0; index < fixtures.size(); index++) {
            var fixture = fixtures.get(index);
            Path path = checkpointPath(checkpointDirectory(output), index, fixture);
            CheckpointPayloadReceipt receipt = ordered[index];
            if (receipt == null || !receipt.fixtureId().equals(fixture.fixtureId())
                    || !receipt.fileName().equals(path.getFileName().toString())
                    || !Files.isRegularFile(path)) {
                throw new IllegalStateException("B3 checkpoint coverage differs");
            }
            byte[] payload = Files.readAllBytes(path);
            validatePayloadReceipt(payload, receipt);
            FixtureCheckpoint checkpoint = mapper.readValue(payload, FixtureCheckpoint.class);
            validate(checkpoint, contract, expectedGuard, fixture);
            checkpoints.add(checkpoint);
            normalized.add(receipt);
        }
        String payloadManifestHash = checkpointPayloadManifestHash(normalized);
        CheckpointReceiptManifest manifest = new CheckpointReceiptManifest(
                Phase13GB3HoldoutModel.RECEIPT_MANIFEST_SCHEMA,
                contractHash,
                runGuardHash,
                OFFICIAL_SHARD_COUNT,
                receipts.size(),
                jvms.size(),
                normalized.size(),
                payloadManifestHash,
                normalized);
        return new VerifiedOfficialEvidence(List.copyOf(checkpoints), manifest);
    }

    void validate(
            FixtureCheckpoint checkpoint,
            FrozenContract contract,
            RunGuard expectedGuard,
            Phase13GB1AuditSchedule.Fixture fixture
    ) {
        String expectedContractHash = expectedGuard.frozenContractHash();
        String expectedGuardHash = guardHash(expectedGuard);
        List<HoldoutJob> jobs = Phase13GB3FrozenHoldoutContract.jobs(fixture);
        var fixed = checkpoint.fixedDraft();
        var replay = checkpoint.determinismReplay();
        boolean rowsExact = checkpoint.rows().size() == jobs.size();
        for (int index = 0; rowsExact && index < jobs.size(); index++) {
            MatchRow row = checkpoint.rows().get(index);
            if (!rowMatchesJob(row, jobs.get(index), expectedGuard, fixed)) {
                rowsExact = false;
            } else {
                validateReplayProvenance(row, expectedGuard);
            }
        }
        CheckpointExecutionEvidence evidence = executionEvidence(
                fixed, replay, checkpoint.rows());
        MatchRow baseline = checkpoint.rows().isEmpty()
                ? null : checkpoint.rows().getFirst();
        if (!checkpoint.frozenContractHash().equals(expectedContractHash)
                || !checkpoint.runGuardHash().equals(expectedGuardHash)
                || !checkpoint.runGuard().equals(expectedGuard)
                || !contract.scheduleHash().equals(expectedGuard.scheduleHash())
                || !fixed.fixtureId().equals(fixture.fixtureId())
                || fixed.fixtureLane() != fixture.fixtureLane()
                || !fixed.pairId().equals(fixture.pairId())
                || !fixed.blueTeamCode().equals(fixture.blueTeamCode())
                || !fixed.redTeamCode().equals(fixture.redTeamCode())
                || fixed.seriesGameNumber() != fixture.seriesGameNumber()
                || fixed.productionOrchestrationCount() != fixture.seriesGameNumber()
                || !replay.fixtureId().equals(fixture.fixtureId())
                || replay.seedIndex() != 0
                || replay.seed() != fixture.holdoutSeeds().getFirst()
                || replay.profileId() != SimulationRuntimeProfileId.BASELINE_V1
                || !replay.exact() || !replay.fullStructuredDiagnosticsExact()
                || baseline == null
                || !replay.replayProvenanceHash().equals(baseline.replayProvenanceHash())
                || !replay.timelineHash().equals(baseline.timelineHash())
                || !replay.structuredDiagnosticsHash().equals(
                        baseline.structuredDiagnosticsHash())
                || replay.randomDrawCount() != baseline.randomDrawCount()
                || !replay.randomTraceHash().equals(baseline.randomTraceHash())
                || !checkpoint.executionEvidence().equals(evidence)
                || !rowsExact
                || !checkpoint.rows().stream().map(MatchRow::jobId).toList().equals(
                        jobs.stream().map(HoldoutJob::jobId).toList())) {
            throw new IllegalStateException(
                    "B3 checkpoint differs from authenticated execution evidence: "
                            + fixture.fixtureId());
        }
    }

    MatchExecutionEvidence rowEvidence(MatchRow row) {
        return new MatchExecutionEvidence(
                Phase13GB3HoldoutModel.ROW_EVIDENCE_SCHEMA,
                row.jobId(), row.replayProvenanceHash(), row.timelineHash(),
                row.structuredDiagnosticsHash(), canonicalHash(row));
    }

    void validateRowEvidence(MatchRow row, MatchExecutionEvidence evidence) {
        if (!rowEvidence(row).equals(evidence)) {
            throw new IllegalStateException("B3 outcome, diagnostics, or observations changed");
        }
    }

    void validateReplayProvenance(MatchRow row, RunGuard guard) {
        String expected = SimulationProvenanceService.replayProvenanceHash(
                row.engineImplementationVersion(), row.activeGameplayRulesVersion(),
                row.configurationHash(), row.resourceProvenanceHash(),
                row.blueTeamCode(), row.redTeamCode(), row.rosterIdentityHash(), row.seed(),
                row.seriesGameNumber(), row.seriesHistoryBeforeHash(),
                guard.draftRuleSetIdentity(), guard.draftRuleSetHash(),
                guard.draftScoringPolicyHash(), row.draftDecisionHash(),
                row.finalDraftHash(), row.finalAssignmentHash());
        if (!expected.equals(row.replayProvenanceHash())) {
            throw new IllegalStateException("B3 replay provenance differs from inputs");
        }
    }

    void validatePayloadReceipt(byte[] payload, CheckpointPayloadReceipt receipt) {
        if (!sha256(payload).equals(receipt.checkpointPayloadSha256())) {
            throw new IllegalStateException("B3 checkpoint raw-byte digest differs");
        }
    }

    private boolean rowMatchesJob(
            MatchRow row,
            HoldoutJob job,
            RunGuard guard,
            Phase13GB2CalibrationModel.FixedDraftRow fixed
    ) {
        var profile = SimulationRuntimeProfiles.resolve(row.profileId());
        return row.schemaVersion().equals(Phase13GB3HoldoutModel.MATCH_ROW_SCHEMA)
                && row.jobId().equals(job.jobId())
                && row.fixtureId().equals(job.fixtureId())
                && row.fixtureLane() == job.fixtureLane()
                && row.pairId().equals(job.pairId())
                && row.blueTeamCode().equals(job.blueTeamCode())
                && row.redTeamCode().equals(job.redTeamCode())
                && row.seriesGameNumber() == job.seriesGameNumber()
                && row.sampleLane() == Phase13GB1AuditSchedule.SampleLane.HOLDOUT
                && row.seedIndex() == job.seedIndex() && row.seed() == job.seed()
                && row.profileIndex() == job.profileIndex()
                && row.profileId() == job.profileId()
                && row.engineImplementationVersion().equals(
                        guard.engineImplementationVersion())
                && row.activeGameplayRulesVersion().equals(
                        profile.activeGameplayRulesVersion())
                && row.configurationHash().equals(
                        guard.configurationHashes().get(row.profileId()))
                && row.resourceProvenanceHash().equals(guard.resourceProvenanceHash())
                && row.rosterIdentityHash().equals(fixed.rosterIdentityHash())
                && row.seriesHistoryBeforeHash().equals(fixed.seriesHistoryBeforeHash())
                && row.draftDecisionHash().equals(fixed.draftDecisionHash())
                && row.finalDraftHash().equals(fixed.finalDraftHash())
                && row.finalAssignmentHash().equals(fixed.finalAssignmentHash());
    }

    private CheckpointExecutionEvidence executionEvidence(
            Phase13GB2CalibrationModel.FixedDraftRow fixed,
            DeterminismReplayEvidence replay,
            List<MatchRow> rows
    ) {
        String fixedHash = canonicalHash(fixed);
        String replayHash = canonicalHash(replay);
        List<MatchExecutionEvidence> rowEvidence = rows.stream()
                .map(this::rowEvidence).toList();
        StringBuilder combined = new StringBuilder()
                .append("schema=").append(
                        Phase13GB3HoldoutModel.EXECUTION_EVIDENCE_SCHEMA).append('\n')
                .append("fixedDraftPayloadSha256=").append(fixedHash).append('\n')
                .append("determinismReplayPayloadSha256=").append(replayHash).append('\n');
        rowEvidence.forEach(value -> combined.append("row=")
                .append(value.jobId()).append('|')
                .append(value.rowPayloadSha256()).append('\n'));
        return new CheckpointExecutionEvidence(
                Phase13GB3HoldoutModel.EXECUTION_EVIDENCE_SCHEMA,
                fixedHash, replayHash, rowEvidence,
                sha256(combined.toString().getBytes(StandardCharsets.UTF_8)));
    }

    private CheckpointPayloadReceipt payloadReceipt(
            Path path,
            int fixtureIndex,
            Phase13GB1AuditSchedule.Fixture fixture,
            byte[] payload
    ) {
        return new CheckpointPayloadReceipt(
                fixtureIndex, fixture.fixtureId(), path.getFileName().toString(),
                Phase13GB3FrozenHoldoutContract.EXPECTED_ROWS_PER_FIXTURE,
                sha256(payload));
    }

    private List<String> ownedFixtureIds(
            Phase13GB1AuditSchedule.Schedule schedule,
            int shard
    ) {
        ArrayList<String> result = new ArrayList<>();
        for (int index = shard; index < schedule.allFixtures().size();
                index += OFFICIAL_SHARD_COUNT) {
            result.add(schedule.allFixtures().get(index).fixtureId());
        }
        return List.copyOf(result);
    }

    private Path workerReceiptPath(Path output, int shardIndex) {
        return output.resolve(RECEIPT_DIRECTORY_NAME)
                .resolve("shard-" + shardIndex + ".json");
    }

    private String checkpointPayloadManifestHash(List<CheckpointPayloadReceipt> values) {
        StringBuilder canonical = new StringBuilder()
                .append("schema=PHASE_13G_B3_CHECKPOINT_PAYLOAD_MANIFEST_V1\n");
        values.forEach(value -> canonical.append(value.fixtureIndex()).append('|')
                .append(value.fixtureId()).append('|').append(value.fileName()).append('|')
                .append(value.rowCount()).append('|')
                .append(value.checkpointPayloadSha256()).append('\n'));
        return sha256(canonical.toString().getBytes(StandardCharsets.UTF_8));
    }

    private String canonicalHash(Object value) {
        try {
            return sha256(mapper.writeValueAsBytes(value));
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot hash B3 evidence", exception);
        }
    }

    private byte[] canonicalBytesWithNewline(Object value) throws IOException {
        byte[] bytes = mapper.writeValueAsBytes(value);
        byte[] result = java.util.Arrays.copyOf(bytes, bytes.length + 1);
        result[bytes.length] = '\n';
        return result;
    }

    private static void writeAtomicBytes(Path path, byte[] payload) throws IOException {
        Files.createDirectories(path.getParent());
        Path temporary = path.resolveSibling(path.getFileName() + ".tmp-"
                + ProcessHandle.current().pid() + '-' + Thread.currentThread().getId());
        Files.write(temporary, payload);
        try {
            Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE);
        } catch (java.nio.file.AtomicMoveNotSupportedException exception) {
            Files.move(temporary, path);
        }
    }

    static String workerJvmIdentityHash() {
        String value = ManagementFactory.getRuntimeMXBean().getName() + '|'
                + ProcessHandle.current().pid() + '|'
                + System.getProperty("java.vm.name") + '|'
                + System.getProperty("java.version");
        return sha256(value.getBytes(StandardCharsets.UTF_8));
    }

    static ObjectMapper canonicalMapper(ObjectMapper source) {
        return Objects.requireNonNull(source, "sourceMapper").copy()
                .findAndRegisterModules()
                .enable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
                .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
                .enable(SerializationFeature.INDENT_OUTPUT);
    }

    static String sha256(byte[] value) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(value));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    static void writeUtf8(Path path, String value) throws IOException {
        Files.createDirectories(path.getParent());
        Files.writeString(path, value, StandardCharsets.UTF_8);
    }

    record VerifiedOfficialEvidence(
            List<FixtureCheckpoint> checkpoints,
            CheckpointReceiptManifest receiptManifest
    ) {
    }
}
