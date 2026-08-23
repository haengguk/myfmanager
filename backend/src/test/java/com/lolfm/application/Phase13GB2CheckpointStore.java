package com.lolfm.application;

import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.lolfm.application.Phase13GB2CalibrationModel.FixtureCheckpoint;
import com.lolfm.application.Phase13GB2CalibrationModel.RunGuard;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;

/** Fixture-atomic persistence; a changed guard is rejected instead of merged. */
public final class Phase13GB2CheckpointStore {
    private final ObjectMapper mapper;

    public Phase13GB2CheckpointStore(ObjectMapper sourceMapper) {
        mapper = canonicalMapper(sourceMapper);
    }

    public String guardHash(RunGuard guard) {
        try {
            return sha256(mapper.copy().disable(SerializationFeature.INDENT_OUTPUT)
                    .writeValueAsBytes(guard));
        } catch (IOException error) {
            throw new IllegalStateException("Cannot hash B2 run guard", error);
        }
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

    public FixtureCheckpoint readAndValidate(
            Path path,
            RunGuard expectedGuard,
            Phase13GB1AuditSchedule.Fixture expectedFixture
    ) throws IOException {
        FixtureCheckpoint checkpoint = mapper.readValue(
                path.toFile(), FixtureCheckpoint.class);
        validate(checkpoint, expectedGuard, expectedFixture);
        return checkpoint;
    }

    public void writeAtomic(
            Path path,
            FixtureCheckpoint checkpoint,
            RunGuard expectedGuard,
            Phase13GB1AuditSchedule.Fixture expectedFixture
    ) throws IOException {
        validate(checkpoint, expectedGuard, expectedFixture);
        Files.createDirectories(path.getParent());
        Path temporary = path.resolveSibling(path.getFileName() + ".tmp");
        byte[] json = mapper.writeValueAsBytes(checkpoint);
        byte[] withNewline = Arrays.copyOf(json, json.length + 1);
        withNewline[json.length] = '\n';
        Files.write(temporary, withNewline);
        Files.move(
                temporary,
                path,
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING);
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
        var replay = checkpoint.determinismReplay();
        var baseline = checkpoint.rows().isEmpty() ? null : checkpoint.rows().getFirst();
        boolean rowsMatchJobs = checkpoint.rows().size() == expectedJobs.size();
        for (int index = 0; rowsMatchJobs && index < expectedJobs.size(); index++) {
            var row = checkpoint.rows().get(index);
            var job = expectedJobs.get(index);
            if (!row.jobId().equals(job.jobId())
                    || !row.fixtureId().equals(job.fixtureId())
                    || row.fixtureLane() != job.fixtureLane()
                    || !row.pairId().equals(job.pairId())
                    || !row.blueTeamCode().equals(job.blueTeamCode())
                    || !row.redTeamCode().equals(job.redTeamCode())
                    || row.seriesGameNumber() != job.seriesGameNumber()
                    || row.sampleLane() != job.sampleLane()
                    || row.seedIndex() != job.seedIndex()
                    || row.seed() != job.seed()
                    || row.profileIndex() != job.profileIndex()
                    || row.profileId() != job.profileId()
                    || !row.engineImplementationVersion()
                            .equals(expectedGuard.engineImplementationVersion())
                    || !row.configurationHash().equals(
                            expectedGuard.configurationHashes().get(row.profileId()))
                    || !row.resourceProvenanceHash()
                            .equals(expectedGuard.resourceProvenanceHash())
                    || !row.seriesHistoryBeforeHash()
                            .equals(checkpoint.fixedDraft().seriesHistoryBeforeHash())
                    || !row.draftDecisionHash()
                            .equals(checkpoint.fixedDraft().draftDecisionHash())
                    || !row.finalDraftHash()
                            .equals(checkpoint.fixedDraft().finalDraftHash())
                    || !row.finalAssignmentHash()
                            .equals(checkpoint.fixedDraft().finalAssignmentHash())) {
                rowsMatchJobs = false;
                break;
            }
        }
        if (!checkpoint.runGuard().equals(expectedGuard)
                || !checkpoint.runGuardHash().equals(expectedHash)
                || !checkpoint.fixedDraft().fixtureId().equals(expectedFixture.fixtureId())
                || checkpoint.fixedDraft().fixtureLane() != expectedFixture.fixtureLane()
                || !checkpoint.fixedDraft().pairId().equals(expectedFixture.pairId())
                || !checkpoint.fixedDraft().blueTeamCode()
                        .equals(expectedFixture.blueTeamCode())
                || !checkpoint.fixedDraft().redTeamCode()
                        .equals(expectedFixture.redTeamCode())
                || checkpoint.fixedDraft().seriesGameNumber()
                        != expectedFixture.seriesGameNumber()
                || checkpoint.fixedDraft().productionOrchestrationCount()
                        != expectedFixture.seriesGameNumber()
                || !replay.fixtureId().equals(expectedFixture.fixtureId())
                || replay.seedIndex() != 0
                || replay.seed() != expectedFixture.calibrationSeeds().getFirst()
                || replay.profileId()
                        != com.lolfm.simulator.SimulationRuntimeProfileId.BASELINE_V1
                || !replay.exact()
                || !replay.fullStructuredDiagnosticsExact()
                || baseline == null
                || !replay.replayProvenanceHash().equals(baseline.replayProvenanceHash())
                || !replay.timelineHash().equals(baseline.timelineHash())
                || !replay.structuredDiagnosticsHash()
                        .equals(baseline.structuredDiagnosticsHash())
                || replay.randomDrawCount() != baseline.randomDrawCount()
                || !replay.randomTraceHash().equals(baseline.randomTraceHash())
                || !rowsMatchJobs
                || !checkpoint.rows().stream()
                        .map(Phase13GB2CalibrationModel.MatchRow::jobId)
                        .toList()
                        .equals(expectedJobs.stream()
                                .map(Phase13GB2CalibrationContract.CalibrationJob::jobId)
                                .toList())) {
            throw new IllegalStateException(
                    "B2 checkpoint differs from the current frozen tree: "
                            + expectedFixture.fixtureId());
        }
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
}
