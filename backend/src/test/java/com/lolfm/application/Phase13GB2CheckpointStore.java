package com.lolfm.application;

import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.lolfm.application.Phase13GB2CalibrationModel.CheckpointExecutionEvidence;
import com.lolfm.application.Phase13GB2CalibrationModel.CheckpointPayloadReceipt;
import com.lolfm.application.Phase13GB2CalibrationModel.CheckpointReceiptManifest;
import com.lolfm.application.Phase13GB2CalibrationModel.DeterminismReplayEvidence;
import com.lolfm.application.Phase13GB2CalibrationModel.FixedDraftRow;
import com.lolfm.application.Phase13GB2CalibrationModel.FixtureCheckpoint;
import com.lolfm.application.Phase13GB2CalibrationModel.MatchExecutionEvidence;
import com.lolfm.application.Phase13GB2CalibrationModel.MatchRow;
import com.lolfm.application.Phase13GB2CalibrationModel.RunGuard;
import com.lolfm.application.Phase13GB2CalibrationModel.WorkerReceipt;
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
import java.util.Arrays;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Fixture-atomic persistence with execution evidence and worker-receipt validation. */
public final class Phase13GB2CheckpointStore {
    static final String CHECKPOINT_DIRECTORY_NAME = "checkpoints-authenticated-v2";
    static final String RECEIPT_DIRECTORY_NAME = "worker-receipts-v1";
    static final int OFFICIAL_SHARD_COUNT = 4;

    private final ObjectMapper mapper;

    public Phase13GB2CheckpointStore(ObjectMapper sourceMapper) {
        mapper = canonicalMapper(sourceMapper);
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
                java.util.Locale.ROOT,
                "%03d-%s.json",
                fixtureIndex,
                fixture.fixtureId()));
    }

    public FixtureCheckpoint createCheckpoint(
            String runGuardHash,
            RunGuard runGuard,
            FixedDraftRow fixedDraft,
            DeterminismReplayEvidence replay,
            List<MatchRow> rows
    ) {
        List<MatchRow> immutableRows = List.copyOf(rows);
        return new FixtureCheckpoint(
                Phase13GB2CalibrationModel.CHECKPOINT_SCHEMA,
                runGuardHash,
                runGuard,
                fixedDraft,
                replay,
                executionEvidence(fixedDraft, replay, immutableRows),
                immutableRows);
    }

    public FixtureCheckpoint readAndValidate(
            Path path,
            RunGuard expectedGuard,
            Phase13GB1AuditSchedule.Fixture expectedFixture
    ) throws IOException {
        byte[] payload = Files.readAllBytes(path);
        FixtureCheckpoint checkpoint = mapper.readValue(payload, FixtureCheckpoint.class);
        validate(checkpoint, expectedGuard, expectedFixture);
        return checkpoint;
    }

    public CheckpointPayloadReceipt writeAtomic(
            Path path,
            int fixtureIndex,
            FixtureCheckpoint checkpoint,
            RunGuard expectedGuard,
            Phase13GB1AuditSchedule.Fixture expectedFixture
    ) throws IOException {
        validate(checkpoint, expectedGuard, expectedFixture);
        Files.createDirectories(path.getParent());
        byte[] payload = canonicalBytesWithNewline(checkpoint);
        writeAtomicBytes(path, payload);
        return payloadReceipt(path, fixtureIndex, expectedFixture, checkpoint, payload);
    }

    public CheckpointPayloadReceipt readPayloadReceipt(
            Path path,
            int fixtureIndex,
            Phase13GB1AuditSchedule.Fixture expectedFixture,
            FixtureCheckpoint checkpoint
    ) throws IOException {
        return payloadReceipt(
                path,
                fixtureIndex,
                expectedFixture,
                checkpoint,
                Files.readAllBytes(path));
    }

    public WorkerReceipt writeWorkerReceipt(
            Path output,
            String runGuardHash,
            int shardIndex,
            int shardCount,
            List<CheckpointPayloadReceipt> checkpoints
    ) throws IOException {
        WorkerReceipt receipt = new WorkerReceipt(
                Phase13GB2CalibrationModel.WORKER_RECEIPT_SCHEMA,
                runGuardHash,
                shardIndex,
                shardCount,
                workerJvmIdentityHash(),
                checkpoints);
        Path receiptPath = workerReceiptPath(output, shardIndex);
        Files.createDirectories(receiptPath.getParent());
        writeAtomicBytes(receiptPath, canonicalBytesWithNewline(receipt));
        return receipt;
    }

    public VerifiedOfficialEvidence readOfficialEvidence(
            Path output,
            RunGuard expectedGuard,
            Phase13GB1AuditSchedule.Schedule schedule
    ) throws IOException {
        schedule = Phase13GB1AuditSchedule.requireFrozen(schedule);
        String expectedGuardHash = guardHash(expectedGuard);
        ArrayList<WorkerReceipt> workerReceipts = new ArrayList<>(OFFICIAL_SHARD_COUNT);
        Set<String> distinctJvmIdentities = new HashSet<>();
        for (int shardIndex = 0; shardIndex < OFFICIAL_SHARD_COUNT; shardIndex++) {
            Path path = workerReceiptPath(output, shardIndex);
            if (!Files.isRegularFile(path)) {
                throw new IllegalStateException("Missing B2 worker receipt for shard "
                        + shardIndex);
            }
            WorkerReceipt receipt = mapper.readValue(path.toFile(), WorkerReceipt.class);
            if (!receipt.runGuardHash().equals(expectedGuardHash)
                    || receipt.shardIndex() != shardIndex
                    || receipt.shardCount() != OFFICIAL_SHARD_COUNT) {
                throw new IllegalStateException("B2 worker receipt differs from run guard");
            }
            workerReceipts.add(receipt);
            distinctJvmIdentities.add(receipt.workerJvmIdentityHash());
        }
        if (distinctJvmIdentities.size() != OFFICIAL_SHARD_COUNT) {
            throw new IllegalStateException(
                    "B2 official evidence requires four distinct worker JVMs");
        }

        List<Phase13GB1AuditSchedule.Fixture> fixtures = schedule.allFixtures();
        CheckpointPayloadReceipt[] orderedReceipts =
                new CheckpointPayloadReceipt[fixtures.size()];
        for (WorkerReceipt receipt : workerReceipts) {
            int expectedShardFixtures = 0;
            for (int fixtureIndex = receipt.shardIndex();
                    fixtureIndex < fixtures.size();
                    fixtureIndex += OFFICIAL_SHARD_COUNT) {
                expectedShardFixtures++;
            }
            if (receipt.checkpoints().size() != expectedShardFixtures) {
                throw new IllegalStateException("B2 worker receipt fixture count differs");
            }
            for (CheckpointPayloadReceipt payloadReceipt : receipt.checkpoints()) {
                int fixtureIndex = payloadReceipt.fixtureIndex();
                if (fixtureIndex >= fixtures.size()
                        || fixtureIndex % OFFICIAL_SHARD_COUNT != receipt.shardIndex()
                        || orderedReceipts[fixtureIndex] != null) {
                    throw new IllegalStateException(
                            "B2 worker receipt fixture assignment differs");
                }
                orderedReceipts[fixtureIndex] = payloadReceipt;
            }
        }

        ArrayList<FixtureCheckpoint> checkpoints = new ArrayList<>(fixtures.size());
        ArrayList<CheckpointPayloadReceipt> normalizedReceipts =
                new ArrayList<>(fixtures.size());
        Path checkpointDirectory = checkpointDirectory(output);
        for (int fixtureIndex = 0; fixtureIndex < fixtures.size(); fixtureIndex++) {
            var fixture = fixtures.get(fixtureIndex);
            CheckpointPayloadReceipt receipt = orderedReceipts[fixtureIndex];
            Path checkpointPath = checkpointPath(checkpointDirectory, fixtureIndex, fixture);
            if (receipt == null
                    || !receipt.fixtureId().equals(fixture.fixtureId())
                    || !receipt.fileName().equals(checkpointPath.getFileName().toString())
                    || !Files.isRegularFile(checkpointPath)) {
                throw new IllegalStateException(
                        "B2 checkpoint receipt coverage differs for " + fixture.fixtureId());
            }
            byte[] payload = Files.readAllBytes(checkpointPath);
            validatePayloadReceipt(payload, receipt);
            FixtureCheckpoint checkpoint = mapper.readValue(payload, FixtureCheckpoint.class);
            validate(checkpoint, expectedGuard, fixture);
            checkpoints.add(checkpoint);
            normalizedReceipts.add(receipt);
        }

        String manifestHash = checkpointPayloadManifestHash(normalizedReceipts);
        CheckpointReceiptManifest manifest = new CheckpointReceiptManifest(
                Phase13GB2CalibrationModel.RECEIPT_MANIFEST_SCHEMA,
                OFFICIAL_SHARD_COUNT,
                workerReceipts.size(),
                distinctJvmIdentities.size(),
                normalizedReceipts.size(),
                manifestHash,
                normalizedReceipts);
        return new VerifiedOfficialEvidence(checkpoints, manifest);
    }

    void validate(
            FixtureCheckpoint checkpoint,
            RunGuard expectedGuard,
            Phase13GB1AuditSchedule.Fixture expectedFixture
    ) {
        Objects.requireNonNull(checkpoint, "checkpoint");
        Objects.requireNonNull(expectedGuard, "expectedGuard");
        Objects.requireNonNull(expectedFixture, "expectedFixture");
        String expectedHash = guardHash(expectedGuard);
        List<Phase13GB2CalibrationContract.CalibrationJob> expectedJobs =
                Phase13GB2CalibrationContract.jobs(expectedFixture);
        FixedDraftRow fixedDraft = checkpoint.fixedDraft();
        var replay = checkpoint.determinismReplay();
        var baseline = checkpoint.rows().isEmpty() ? null : checkpoint.rows().getFirst();
        boolean rowsMatchJobs = checkpoint.rows().size() == expectedJobs.size();
        for (int index = 0; rowsMatchJobs && index < expectedJobs.size(); index++) {
            MatchRow row = checkpoint.rows().get(index);
            var job = expectedJobs.get(index);
            if (!rowMatchesJob(row, job, expectedGuard, fixedDraft)) {
                rowsMatchJobs = false;
                break;
            }
            validateReplayProvenance(row, expectedGuard);
        }
        CheckpointExecutionEvidence expectedEvidence = executionEvidence(
                fixedDraft, replay, checkpoint.rows());
        if (!checkpoint.runGuard().equals(expectedGuard)
                || !checkpoint.runGuardHash().equals(expectedHash)
                || !fixedDraft.fixtureId().equals(expectedFixture.fixtureId())
                || fixedDraft.fixtureLane() != expectedFixture.fixtureLane()
                || !fixedDraft.pairId().equals(expectedFixture.pairId())
                || !fixedDraft.blueTeamCode().equals(expectedFixture.blueTeamCode())
                || !fixedDraft.redTeamCode().equals(expectedFixture.redTeamCode())
                || fixedDraft.seriesGameNumber() != expectedFixture.seriesGameNumber()
                || fixedDraft.productionOrchestrationCount()
                        != expectedFixture.seriesGameNumber()
                || !replay.fixtureId().equals(expectedFixture.fixtureId())
                || replay.seedIndex() != 0
                || replay.seed() != expectedFixture.calibrationSeeds().getFirst()
                || replay.profileId() != SimulationRuntimeProfileId.BASELINE_V1
                || !replay.exact()
                || !replay.fullStructuredDiagnosticsExact()
                || baseline == null
                || !replay.replayProvenanceHash().equals(baseline.replayProvenanceHash())
                || !replay.timelineHash().equals(baseline.timelineHash())
                || !replay.structuredDiagnosticsHash()
                        .equals(baseline.structuredDiagnosticsHash())
                || replay.randomDrawCount() != baseline.randomDrawCount()
                || !replay.randomTraceHash().equals(baseline.randomTraceHash())
                || !checkpoint.executionEvidence().equals(expectedEvidence)
                || !rowsMatchJobs
                || !checkpoint.rows().stream().map(MatchRow::jobId).toList()
                        .equals(expectedJobs.stream()
                                .map(Phase13GB2CalibrationContract.CalibrationJob::jobId)
                                .toList())) {
            throw new IllegalStateException(
                    "B2 checkpoint differs from authenticated execution evidence: "
                            + expectedFixture.fixtureId());
        }
    }

    MatchExecutionEvidence rowEvidence(MatchRow row) {
        return new MatchExecutionEvidence(
                Phase13GB2CalibrationModel.ROW_EVIDENCE_SCHEMA,
                row.jobId(),
                row.replayProvenanceHash(),
                row.timelineHash(),
                row.structuredDiagnosticsHash(),
                canonicalHash(row));
    }

    void validateRowEvidence(MatchRow row, MatchExecutionEvidence expected) {
        if (!rowEvidence(row).equals(expected)) {
            throw new IllegalStateException("B2 match outcome or observation payload changed");
        }
    }

    void validateReplayProvenance(MatchRow row, RunGuard guard) {
        String expected = SimulationProvenanceService.replayProvenanceHash(
                row.engineImplementationVersion(),
                row.activeGameplayRulesVersion(),
                row.configurationHash(),
                row.resourceProvenanceHash(),
                row.blueTeamCode(),
                row.redTeamCode(),
                row.rosterIdentityHash(),
                row.seed(),
                row.seriesGameNumber(),
                row.seriesHistoryBeforeHash(),
                guard.draftRuleSetIdentity(),
                guard.draftRuleSetHash(),
                guard.draftScoringPolicyHash(),
                row.draftDecisionHash(),
                row.finalDraftHash(),
                row.finalAssignmentHash());
        if (!expected.equals(row.replayProvenanceHash())) {
            throw new IllegalStateException(
                    "B2 replay provenance does not match frozen execution inputs: "
                            + row.jobId());
        }
    }

    void validatePayloadReceipt(byte[] payload, CheckpointPayloadReceipt receipt) {
        if (!sha256(payload).equals(receipt.checkpointPayloadSha256())) {
            throw new IllegalStateException("B2 checkpoint payload digest differs from receipt");
        }
    }

    private boolean rowMatchesJob(
            MatchRow row,
            Phase13GB2CalibrationContract.CalibrationJob job,
            RunGuard guard,
            FixedDraftRow fixedDraft
    ) {
        var profile = SimulationRuntimeProfiles.resolve(row.profileId());
        return row.jobId().equals(job.jobId())
                && row.fixtureId().equals(job.fixtureId())
                && row.fixtureLane() == job.fixtureLane()
                && row.pairId().equals(job.pairId())
                && row.blueTeamCode().equals(job.blueTeamCode())
                && row.redTeamCode().equals(job.redTeamCode())
                && row.seriesGameNumber() == job.seriesGameNumber()
                && row.sampleLane() == job.sampleLane()
                && row.seedIndex() == job.seedIndex()
                && row.seed() == job.seed()
                && row.profileIndex() == job.profileIndex()
                && row.profileId() == job.profileId()
                && row.engineImplementationVersion().equals(guard.engineImplementationVersion())
                && row.activeGameplayRulesVersion()
                        .equals(profile.activeGameplayRulesVersion())
                && row.configurationHash().equals(
                        guard.configurationHashes().get(row.profileId()))
                && row.resourceProvenanceHash().equals(guard.resourceProvenanceHash())
                && row.rosterIdentityHash().equals(fixedDraft.rosterIdentityHash())
                && row.seriesHistoryBeforeHash().equals(fixedDraft.seriesHistoryBeforeHash())
                && row.draftDecisionHash().equals(fixedDraft.draftDecisionHash())
                && row.finalDraftHash().equals(fixedDraft.finalDraftHash())
                && row.finalAssignmentHash().equals(fixedDraft.finalAssignmentHash());
    }

    private CheckpointExecutionEvidence executionEvidence(
            FixedDraftRow fixedDraft,
            DeterminismReplayEvidence replay,
            List<MatchRow> rows
    ) {
        String fixedDraftHash = canonicalHash(fixedDraft);
        String replayHash = canonicalHash(replay);
        List<MatchExecutionEvidence> rowEvidence = rows.stream()
                .map(this::rowEvidence).toList();
        StringBuilder combined = new StringBuilder()
                .append("schema=")
                .append(Phase13GB2CalibrationModel.EXECUTION_EVIDENCE_SCHEMA)
                .append('\n')
                .append("fixedDraftPayloadSha256=").append(fixedDraftHash).append('\n')
                .append("determinismReplayPayloadSha256=").append(replayHash).append('\n');
        rowEvidence.forEach(value -> combined.append("row=")
                .append(value.jobId()).append('|')
                .append(value.rowPayloadSha256()).append('\n'));
        return new CheckpointExecutionEvidence(
                Phase13GB2CalibrationModel.EXECUTION_EVIDENCE_SCHEMA,
                fixedDraftHash,
                replayHash,
                rowEvidence,
                sha256(combined.toString().getBytes(StandardCharsets.UTF_8)));
    }

    private CheckpointPayloadReceipt payloadReceipt(
            Path path,
            int fixtureIndex,
            Phase13GB1AuditSchedule.Fixture fixture,
            FixtureCheckpoint checkpoint,
            byte[] payload
    ) {
        return new CheckpointPayloadReceipt(
                fixtureIndex,
                fixture.fixtureId(),
                path.getFileName().toString(),
                checkpoint.rows().size(),
                sha256(payload));
    }

    private Path workerReceiptPath(Path output, int shardIndex) {
        return output.resolve(RECEIPT_DIRECTORY_NAME).resolve(String.format(
                java.util.Locale.ROOT, "shard-%d.json", shardIndex));
    }

    private String checkpointPayloadManifestHash(List<CheckpointPayloadReceipt> receipts) {
        StringBuilder canonical = new StringBuilder()
                .append("schema=")
                .append(Phase13GB2CalibrationModel.RECEIPT_MANIFEST_SCHEMA)
                .append('\n');
        receipts.forEach(receipt -> canonical.append("checkpoint=")
                .append(receipt.fixtureIndex()).append('|')
                .append(receipt.fixtureId()).append('|')
                .append(receipt.fileName()).append('|')
                .append(receipt.rowCount()).append('|')
                .append(receipt.checkpointPayloadSha256()).append('\n'));
        return sha256(canonical.toString().getBytes(StandardCharsets.UTF_8));
    }

    private String canonicalHash(Object value) {
        try {
            return sha256(mapper.copy().disable(SerializationFeature.INDENT_OUTPUT)
                    .writeValueAsBytes(value));
        } catch (IOException error) {
            throw new IllegalStateException("Cannot hash B2 canonical payload", error);
        }
    }

    private byte[] canonicalBytesWithNewline(Object value) throws IOException {
        byte[] json = mapper.writeValueAsBytes(value);
        byte[] withNewline = Arrays.copyOf(json, json.length + 1);
        withNewline[json.length] = '\n';
        return withNewline;
    }

    private static void writeAtomicBytes(Path path, byte[] payload) throws IOException {
        Path temporary = path.resolveSibling(path.getFileName() + ".tmp");
        Files.write(temporary, payload);
        Files.move(
                temporary,
                path,
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING);
    }

    static String workerJvmIdentityHash() {
        var runtime = ManagementFactory.getRuntimeMXBean();
        String identity = runtime.getName() + '|' + runtime.getStartTime();
        return sha256(identity.getBytes(StandardCharsets.UTF_8));
    }

    static ObjectMapper canonicalMapper(ObjectMapper sourceMapper) {
        return Objects.requireNonNull(sourceMapper, "sourceMapper").copy()
                .findAndRegisterModules()
                .enable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
                .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
                .enable(SerializationFeature.INDENT_OUTPUT);
    }

    static String sha256(byte[] value) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(value));
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException(error);
        }
    }

    static void writeUtf8(Path output, String value) throws IOException {
        Files.writeString(output, value, StandardCharsets.UTF_8);
    }

    record VerifiedOfficialEvidence(
            List<FixtureCheckpoint> checkpoints,
            CheckpointReceiptManifest receiptManifest
    ) {
        VerifiedOfficialEvidence {
            checkpoints = List.copyOf(checkpoints);
            Objects.requireNonNull(receiptManifest, "receiptManifest");
        }
    }
}
