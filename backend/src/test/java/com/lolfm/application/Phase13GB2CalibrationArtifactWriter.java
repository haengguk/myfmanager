package com.lolfm.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.lolfm.application.Phase13GB2CalibrationModel.FixtureCheckpoint;
import com.lolfm.application.Phase13GB2CalibrationModel.JungleObservation;
import com.lolfm.application.Phase13GB2CalibrationModel.MarginalRow;
import com.lolfm.application.Phase13GB2CalibrationModel.MatchRow;
import com.lolfm.application.Phase13GB2CalibrationModel.RunGuard;
import com.lolfm.simulator.GameEndReason;
import com.lolfm.simulator.SimulationRuntimeProfileId;
import com.lolfm.simulator.TeamSide;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Artifact-only B2 finalizer. It never starts gameplay or opens the holdout lane. */
public final class Phase13GB2CalibrationArtifactWriter {
    public static final String CONTRACT_FILE = "phase13g-b2-calibration-contract.json";
    public static final String JOB_MANIFEST_FILE = "phase13g-b2-job-manifest.csv";
    public static final String FIXED_DRAFTS_FILE = "phase13g-b2-fixed-drafts.csv";
    public static final String DETERMINISM_REPLAYS_FILE =
            "phase13g-b2-determinism-replays.csv";
    public static final String CHECKPOINT_RECEIPT_MANIFEST_FILE =
            "phase13g-b2-checkpoint-receipt-manifest.json";
    public static final String MATCHES_JSONL_FILE = "phase13g-b2-matches.jsonl";
    public static final String MATCHES_CSV_FILE = "phase13g-b2-matches.csv";
    public static final String JUNGLE_CHECKPOINTS_FILE =
            "phase13g-b2-jungle-checkpoints.csv";
    public static final String PAIRED_MARGINALS_FILE =
            "phase13g-b2-paired-marginals.csv";
    public static final String MARGINAL_SUMMARY_FILE =
            "phase13g-b2-marginal-summary.csv";
    public static final String PAIR_SUMMARY_FILE =
            "phase13g-b2-unordered-pair-summary.csv";
    public static final String PROFILE_SUMMARY_FILE =
            "phase13g-b2-profile-summary.csv";
    public static final String TEAM_SIDE_SUMMARY_FILE =
            "phase13g-b2-team-side-summary.csv";
    public static final String JUNGLER_CHAMPION_SUMMARY_FILE =
            "phase13g-b2-jungler-champion-summary.csv";
    public static final String INTEGRITY_FILE = "phase13g-b2-integrity.json";
    public static final String REVIEW_FILE = "phase13g-b2-review.json";
    public static final String SHA_FILE = "SHA256SUMS.txt";
    private static final List<String> HASHED_FILES = List.of(
            CONTRACT_FILE,
            JOB_MANIFEST_FILE,
            FIXED_DRAFTS_FILE,
            DETERMINISM_REPLAYS_FILE,
            CHECKPOINT_RECEIPT_MANIFEST_FILE,
            MATCHES_JSONL_FILE,
            MATCHES_CSV_FILE,
            JUNGLE_CHECKPOINTS_FILE,
            PAIRED_MARGINALS_FILE,
            MARGINAL_SUMMARY_FILE,
            PAIR_SUMMARY_FILE,
            PROFILE_SUMMARY_FILE,
            TEAM_SIDE_SUMMARY_FILE,
            JUNGLER_CHAMPION_SUMMARY_FILE,
            INTEGRITY_FILE,
            REVIEW_FILE);

    private Phase13GB2CalibrationArtifactWriter() {
    }

    static SmokeArtifactSet writeSmoke(
            ObjectMapper sourceMapper,
            Path output,
            RunGuard guard,
            Phase13GB2CalibrationModel.FixedDraftRow fixedDraft,
            List<MatchRow> rows
    ) throws IOException {
        rows = List.copyOf(rows);
        if (rows.size() != Phase13GB2CalibrationContract.EXPECTED_PROFILES_PER_SEED
                || rows.stream().map(MatchRow::profileId).toList()
                        .equals(Phase13GB1RealMatchHarness.AUDIT_PROFILES) == false
                || rows.stream().map(MatchRow::fixtureId).distinct().count() != 1
                || rows.stream().map(MatchRow::seed).distinct().count() != 1
                || rows.stream().anyMatch(row -> !row.integrityClean())) {
            throw new IllegalArgumentException("B2 smoke artifact requires one clean paired seed");
        }
        Map<SimulationRuntimeProfileId, MatchRow> byProfile = rows.stream().collect(
                Collectors.toMap(
                        MatchRow::profileId,
                        Function.identity(),
                        (first, second) -> {
                            throw new IllegalArgumentException("Duplicate B2 smoke profile");
                        },
                        LinkedHashMap::new));
        List<MarginalRow> marginals = Phase13GB2CalibrationContract.MARGINAL_COMPARISONS
                .stream().map(comparison -> MarginalRow.from(
                        comparison,
                        byProfile.get(comparison.fromProfile()),
                        byProfile.get(comparison.toProfile())))
                .toList();
        Files.createDirectories(output);
        ObjectMapper mapper = Phase13GB2CheckpointStore.canonicalMapper(sourceMapper);
        writeJson(mapper, output.resolve("phase13g-b2-smoke-guard.json"), guard);
        writeJson(mapper, output.resolve("phase13g-b2-smoke-fixed-draft.json"), fixedDraft);
        writeJsonLines(mapper, output.resolve("phase13g-b2-smoke-matches.jsonl"), rows);
        Phase13GB2CheckpointStore.writeUtf8(
                output.resolve("phase13g-b2-smoke-matches.csv"), matchesCsv(rows));
        Phase13GB2CheckpointStore.writeUtf8(
                output.resolve("phase13g-b2-smoke-jungle-checkpoints.csv"),
                jungleCheckpoints(rows));
        Phase13GB2CheckpointStore.writeUtf8(
                output.resolve("phase13g-b2-smoke-marginals.csv"),
                marginalsCsv(marginals));
        Phase13GB2CheckpointStore.writeUtf8(
                output.resolve("phase13g-b2-smoke-marginal-summary.csv"),
                marginalSummaryCsv(marginals, false));
        Phase13GB2CheckpointStore.writeUtf8(
                output.resolve("phase13g-b2-smoke-profile-summary.csv"),
                profileSummaryCsv(rows));
        Phase13GB2CheckpointStore.writeUtf8(
                output.resolve("phase13g-b2-smoke-team-side-summary.csv"),
                teamSideSummaryCsv(rows));
        Phase13GB2CheckpointStore.writeUtf8(
                output.resolve("phase13g-b2-smoke-jungler-champion-summary.csv"),
                junglerChampionSummaryCsv(rows));
        return new SmokeArtifactSet(output, rows.size(), marginals.size());
    }

    static SyntheticArtifactSet writeSyntheticValidation(
            ObjectMapper sourceMapper,
            Path output,
            RunGuard guard,
            Phase13GB2CalibrationModel.FixedDraftRow fixedDraft,
            List<MatchRow> rows
    ) throws IOException {
        Files.createDirectories(output);
        ObjectMapper mapper = Phase13GB2CheckpointStore.canonicalMapper(sourceMapper);
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("schemaVersion", "PHASE_13G_B2_SYNTHETIC_VALIDATION_V1");
        report.put("status", "SYNTHETIC_VALIDATION_ONLY");
        report.put("officialCalibrationEvidence", false);
        report.put("runGuard", guard);
        report.put("fixedDraft", fixedDraft);
        report.put("rowCount", rows.size());
        report.put("note", "Synthetic rows cannot enter the official finalizer path");
        Path reportPath = output.resolve("phase13g-b2-synthetic-validation.json");
        writeJson(mapper, reportPath, report);
        return new SyntheticArtifactSet(
                reportPath, "SYNTHETIC_VALIDATION_ONLY", rows.size(), false);
    }

    public static ArtifactSet writeOfficial(
            ObjectMapper sourceMapper,
            Path output,
            Phase13GB1AuditSchedule.Schedule schedule,
            RunGuard guard,
            Phase13GB2CheckpointStore.VerifiedOfficialEvidence officialEvidence
    ) throws IOException {
        schedule = Phase13GB1AuditSchedule.requireFrozen(schedule);
        Objects.requireNonNull(officialEvidence, "officialEvidence");
        List<FixtureCheckpoint> checkpoints = officialEvidence.checkpoints();
        if (checkpoints.isEmpty()) {
            throw new IllegalStateException("B2 finalization requires fixture checkpoints");
        }
        Phase13GB2CheckpointStore checkpointStore =
                new Phase13GB2CheckpointStore(sourceMapper);
        List<Phase13GB1AuditSchedule.Fixture> fixtures = schedule.allFixtures();
        if (checkpoints.size() != fixtures.size()) {
            throw new IllegalStateException("B2 checkpoint count differs from schedule");
        }
        for (int index = 0; index < fixtures.size(); index++) {
            checkpointStore.validate(checkpoints.get(index), guard, fixtures.get(index));
        }
        ObjectMapper mapper = Phase13GB2CheckpointStore.canonicalMapper(sourceMapper);
        Validation validation = validate(
                schedule, guard, checkpoints, officialEvidence.receiptManifest());
        Files.createDirectories(output);

        writeJson(mapper, output.resolve(CONTRACT_FILE), contract(guard, validation));
        Phase13GB2CheckpointStore.writeUtf8(
                output.resolve(JOB_MANIFEST_FILE), jobManifest(validation.expectedJobs()));
        Phase13GB2CheckpointStore.writeUtf8(
                output.resolve(FIXED_DRAFTS_FILE), fixedDrafts(checkpoints));
        Phase13GB2CheckpointStore.writeUtf8(
                output.resolve(DETERMINISM_REPLAYS_FILE),
                determinismReplays(checkpoints));
        writeJson(
                mapper,
                output.resolve(CHECKPOINT_RECEIPT_MANIFEST_FILE),
                officialEvidence.receiptManifest());
        writeJsonLines(mapper, output.resolve(MATCHES_JSONL_FILE), validation.rows());
        Phase13GB2CheckpointStore.writeUtf8(
                output.resolve(MATCHES_CSV_FILE), matchesCsv(validation.rows()));
        Phase13GB2CheckpointStore.writeUtf8(
                output.resolve(JUNGLE_CHECKPOINTS_FILE),
                jungleCheckpoints(validation.rows()));
        Phase13GB2CheckpointStore.writeUtf8(
                output.resolve(PAIRED_MARGINALS_FILE), marginalsCsv(validation.marginals()));
        Phase13GB2CheckpointStore.writeUtf8(
                output.resolve(MARGINAL_SUMMARY_FILE),
                marginalSummaryCsv(validation.marginals(), false));
        Phase13GB2CheckpointStore.writeUtf8(
                output.resolve(PAIR_SUMMARY_FILE),
                marginalSummaryCsv(validation.marginals(), true));
        Phase13GB2CheckpointStore.writeUtf8(
                output.resolve(PROFILE_SUMMARY_FILE), profileSummaryCsv(validation.rows()));
        Phase13GB2CheckpointStore.writeUtf8(
                output.resolve(TEAM_SIDE_SUMMARY_FILE), teamSideSummaryCsv(validation.rows()));
        Phase13GB2CheckpointStore.writeUtf8(
                output.resolve(JUNGLER_CHAMPION_SUMMARY_FILE),
                junglerChampionSummaryCsv(validation.rows()));
        writeJson(mapper, output.resolve(INTEGRITY_FILE), validation.integrity());
        writeJson(mapper, output.resolve(REVIEW_FILE), review(validation));
        writeManifest(output);
        return new ArtifactSet(
                output,
                validation.integrity().status(),
                validation.rows().size(),
                validation.marginals().size(),
                Phase13GB2CheckpointStore.sha256(
                        Files.readAllBytes(output.resolve(REVIEW_FILE))),
                Phase13GB2CheckpointStore.sha256(
                        Files.readAllBytes(output.resolve(SHA_FILE))));
    }

    private static Validation validate(
            Phase13GB1AuditSchedule.Schedule schedule,
            RunGuard guard,
            List<FixtureCheckpoint> checkpoints,
            Phase13GB2CalibrationModel.CheckpointReceiptManifest receiptManifest
    ) {
        Objects.requireNonNull(guard, "guard");
        Objects.requireNonNull(receiptManifest, "receiptManifest");
        List<Phase13GB2CalibrationContract.CalibrationJob> expectedJobs =
                Phase13GB2CalibrationContract.jobs(schedule);
        ArrayList<MatchRow> rows = new ArrayList<>(Phase13GB2CalibrationContract.EXPECTED_MATCHES);
        ArrayList<String> fixtureIds = new ArrayList<>();
        for (FixtureCheckpoint checkpoint : checkpoints) {
            if (!checkpoint.runGuard().equals(guard)
                    || !checkpoint.runGuardHash()
                            .equals(checkpoints.getFirst().runGuardHash())) {
                throw new IllegalStateException("B2 checkpoint run guard mismatch");
            }
            fixtureIds.add(checkpoint.fixedDraft().fixtureId());
            rows.addAll(checkpoint.rows());
        }
        List<String> expectedFixtureIds = schedule.allFixtures().stream()
                .map(Phase13GB1AuditSchedule.Fixture::fixtureId).toList();
        List<String> expectedJobIds = expectedJobs.stream()
                .map(Phase13GB2CalibrationContract.CalibrationJob::jobId).toList();
        List<String> actualJobIds = rows.stream().map(MatchRow::jobId).toList();
        long distinctJobs = actualJobIds.stream().distinct().count();
        long dirtyRows = rows.stream().filter(row -> !row.integrityClean()).count();
        long integrityErrors = rows.stream().mapToLong(MatchRow::integrityErrorCount).sum();
        long holdoutRows = rows.stream().filter(row -> row.sampleLane()
                == Phase13GB1AuditSchedule.SampleLane.HOLDOUT).count();
        long nonExactReplays = checkpoints.stream()
                .filter(checkpoint -> !checkpoint.determinismReplay().exact()
                        || !checkpoint.determinismReplay()
                                .fullStructuredDiagnosticsExact())
                .count();
        boolean replayCoverageExact = checkpoints.size()
                == Phase13GB2CalibrationContract.EXPECTED_FIXTURES
                && nonExactReplays == 0
                && checkpoints.stream().map(checkpoint -> checkpoint.determinismReplay()
                        .fixtureId()).distinct().count()
                        == Phase13GB2CalibrationContract.EXPECTED_FIXTURES;
        boolean jobManifestExact = fixtureIds.equals(expectedFixtureIds)
                && actualJobIds.equals(expectedJobIds)
                && distinctJobs == expectedJobIds.size();
        boolean receiptEvidenceExact = receiptManifest.expectedWorkerShardCount()
                        == Phase13GB2CheckpointStore.OFFICIAL_SHARD_COUNT
                && receiptManifest.workerReceiptCount()
                        == Phase13GB2CheckpointStore.OFFICIAL_SHARD_COUNT
                && receiptManifest.distinctFreshJvmCount()
                        == Phase13GB2CheckpointStore.OFFICIAL_SHARD_COUNT
                && receiptManifest.checkpointCount()
                        == Phase13GB2CalibrationContract.EXPECTED_FIXTURES
                && receiptManifest.checkpoints().size()
                        == Phase13GB2CalibrationContract.EXPECTED_FIXTURES;
        String status = jobManifestExact
                && receiptEvidenceExact
                && dirtyRows == 0
                && integrityErrors == 0
                && holdoutRows == 0
                && replayCoverageExact
                && rows.size() == Phase13GB2CalibrationContract.EXPECTED_MATCHES
                ? "CALIBRATION_EVIDENCE_READY_FOR_REVIEW"
                : "BLOCKED_BY_CALIBRATION_INTEGRITY";
        IntegrityReport integrity = new IntegrityReport(
                "PHASE_13G_B2_CALIBRATION_INTEGRITY_V2",
                status,
                Phase13GB2CalibrationContract.EXPECTED_FIXTURES,
                checkpoints.size(),
                Phase13GB2CalibrationContract.EXPECTED_MATCHES,
                rows.size(),
                distinctJobs,
                jobManifestExact,
                holdoutRows,
                Phase13GB2CalibrationContract.EXPECTED_FIXTURES,
                checkpoints.size(),
                nonExactReplays,
                replayCoverageExact,
                dirtyRows,
                integrityErrors,
                rows.stream().mapToLong(MatchRow::economyIntegrityErrors).sum(),
                rows.stream().mapToLong(MatchRow::progressionIntegrityErrors).sum(),
                rows.stream().mapToLong(MatchRow::championPowerIntegrityErrors).sum(),
                rows.stream().mapToLong(MatchRow::championMatchupIntegrityErrors).sum(),
                rows.stream().mapToLong(MatchRow::compositionIntegrityErrors).sum(),
                rows.stream().mapToLong(MatchRow::combatOutcomeIntegrityErrors).sum(),
                rows.stream().mapToLong(MatchRow::objectivePriorityIntegrityErrors).sum(),
                rows.stream().mapToLong(MatchRow::structureIntegrityErrors).sum(),
                rows.stream().mapToLong(MatchRow::lanePhaseIntegrityErrors).sum(),
                rows.stream().mapToLong(MatchRow::midGameMacroIntegrityErrors).sum(),
                receiptManifest.expectedWorkerShardCount(),
                receiptManifest.workerReceiptCount(),
                receiptManifest.distinctFreshJvmCount(),
                receiptManifest.checkpointCount(),
                receiptEvidenceExact,
                receiptManifest.checkpointPayloadManifestHash(),
                guard);
        if (!jobManifestExact) {
            throw new IllegalStateException("B2 job manifest is missing, duplicated, or reordered");
        }
        List<MarginalRow> marginals = marginals(rows, schedule);
        return new Validation(
                expectedJobs,
                List.copyOf(rows),
                marginals,
                integrity,
                checkpoints.getFirst().runGuardHash(),
                receiptManifest,
                checkpoints.stream().mapToInt(checkpoint -> checkpoint.fixedDraft()
                        .productionOrchestrationCount()).sum());
    }

    private static List<MarginalRow> marginals(
            List<MatchRow> rows,
            Phase13GB1AuditSchedule.Schedule schedule
    ) {
        Map<String, MatchRow> byProfile = Phase13GB2CalibrationModel.rowsByProfile(rows);
        ArrayList<MarginalRow> result = new ArrayList<>(
                Phase13GB2CalibrationContract.EXPECTED_MATCHES);
        for (var fixture : schedule.allFixtures()) {
            for (int seedIndex = 0;
                    seedIndex < Phase13GB2CalibrationContract.EXPECTED_SEEDS_PER_FIXTURE;
                    seedIndex++) {
                for (var comparison : Phase13GB2CalibrationContract.MARGINAL_COMPARISONS) {
                    MatchRow from = byProfile.get(key(
                            fixture.fixtureId(), seedIndex, comparison.fromProfile()));
                    MatchRow to = byProfile.get(key(
                            fixture.fixtureId(), seedIndex, comparison.toProfile()));
                    result.add(MarginalRow.from(comparison, from, to));
                }
            }
        }
        if (result.size() != Phase13GB2CalibrationContract.EXPECTED_MATCHES) {
            throw new IllegalStateException("B2 paired marginal count differs from contract");
        }
        return List.copyOf(result);
    }

    private static String key(
            String fixtureId,
            int seedIndex,
            SimulationRuntimeProfileId profileId
    ) {
        return fixtureId + '|' + seedIndex + '|' + profileId;
    }

    private static Map<String, Object> contract(RunGuard guard, Validation validation) {
        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        result.put("schemaVersion", Phase13GB2CalibrationContract.SCHEMA);
        result.put("phase", "PHASE_13G_B2_REAL_DATA_CALIBRATION");
        result.put("sampleLane", "CALIBRATION");
        result.put("runGuardHash", validation.runGuardHash());
        result.put("runGuard", guard);
        result.put("profileOrder", Phase13GB1RealMatchHarness.AUDIT_PROFILES);
        result.put("fixedCheckpointSeconds",
                Phase13GB2CalibrationContract.FIXED_CHECKPOINT_SECONDS);
        result.put("fixedCheckpointSamplingPolicy",
                "FIRST_RECORDED_SNAPSHOT_AT_OR_AFTER_REQUESTED_TIME_NO_INTERPOLATION");
        result.put("marginalComparisons",
                Phase13GB2CalibrationContract.MARGINAL_COMPARISONS);
        result.put("fixtureAtomicCheckpointRows",
                Phase13GB2CalibrationContract.EXPECTED_ROWS_PER_FIXTURE);
        result.put("checkpointAuthenticityPolicy",
                "RECOMPUTED_REPLAY_PROVENANCE_ROW_PAYLOAD_DIGEST_AND_WORKER_RECEIPT_V1");
        result.put("workerShardCount",
                validation.receiptManifest().workerReceiptCount());
        result.put("distinctFreshJvmCount",
                validation.receiptManifest().distinctFreshJvmCount());
        result.put("checkpointPayloadManifestHash",
                validation.receiptManifest().checkpointPayloadManifestHash());
        result.put("fixturePreparationOrchestrationCount",
                validation.fixturePreparationOrchestrationCount());
        result.put("fixturePreparationMatchesExcludedFromCalibrationSample", true);
        result.put("calibrationMatchExecutionCount", validation.rows().size());
        result.put("determinismReplayExecutionCount",
                Phase13GB2CalibrationContract.EXPECTED_FIXTURES);
        result.put("determinismReplaysExcludedFromCalibrationSample", true);
        result.put("holdoutExecuted", false);
        result.put("holdoutMatchExecutionCount", 0);
        result.put("automaticTuning", false);
        result.put("productionDecision", "NOT_EVALUATED");
        result.put("earlyFinishedMatchCheckpointPolicy",
                "DO_NOT_CARRY_FINAL_FORWARD_TO_UNREACHED_FIXED_CHECKPOINTS");
        return result;
    }

    private static Map<String, Object> review(Validation validation) {
        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        result.put("schemaVersion", "PHASE_13G_B2_CALIBRATION_REVIEW_V2");
        result.put("phase", "PHASE_13G_B2_REAL_DATA_CALIBRATION");
        result.put("status", validation.integrity().status());
        result.put("structuralIntegrityClean",
                validation.integrity().totalIntegrityErrorCount() == 0);
        result.put("calibrationExecuted", true);
        result.put("calibrationMatchExecutionCount", validation.rows().size());
        result.put("pairedMarginalCount", validation.marginals().size());
        result.put("determinismReplayExecutionCount",
                validation.integrity().actualDeterminismReplayCount());
        result.put("determinismReplayExact",
                validation.integrity().determinismReplayCoverageExact());
        result.put("checkpointPayloadDigestExact",
                validation.integrity().checkpointPayloadDigestExact());
        result.put("workerReceiptCount",
                validation.integrity().workerReceiptCount());
        result.put("distinctFreshJvmCount",
                validation.integrity().distinctFreshJvmCount());
        result.put("checkpointPayloadManifestHash",
                validation.integrity().checkpointPayloadManifestHash());
        result.put("holdoutExecuted", false);
        result.put("holdoutMatchExecutionCount", 0);
        result.put("balanceSignalsAreReviewOnly", true);
        result.put("automaticTuningPerformed", false);
        result.put("candidateFrozen", false);
        result.put("productionDecision", "NOT_EVALUATED");
        result.put("marginalOverviews", marginalOverviews(validation.marginals()));
        result.put("profileMechanicOverviews", profileMechanicOverviews(validation.rows()));
        result.put("checkpointSampling", checkpointSampling(validation.rows()));
        result.put("nextStep",
                "HUMAN_REVIEW_THEN_FREEZE_CANDIDATE_AND_GATES_BEFORE_PHASE_13G_B3");
        return result;
    }

    private static List<MarginalOverview> marginalOverviews(List<MarginalRow> rows) {
        Map<OverviewKey, List<MarginalRow>> groups = rows.stream().collect(
                Collectors.groupingBy(
                        row -> new OverviewKey(row.fixtureLane(), row.comparisonId()),
                        LinkedHashMap::new,
                        Collectors.toList()));
        return groups.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> {
                    List<MarginalRow> values = entry.getValue();
                    return new MarginalOverview(
                            entry.getKey().fixtureLane(),
                            entry.getKey().comparisonId(),
                            values.size(),
                            ratio(values.stream().filter(MarginalRow::winnerFlipped).count(),
                                    values.size()),
                            mean(values, value -> value.durationDelta()),
                            mean(values, value -> value.blueGoldEdgeDelta()),
                            mean(values, value -> value.blueJungleCsDelta()),
                            mean(values, value -> value.redJungleCsDelta()),
                            mean(values, value -> value.blueJungleExperienceDelta()),
                            mean(values, value -> value.redJungleExperienceDelta()),
                            mean(values, value -> value.jungleGankAttemptsDelta()),
                            mean(values, value -> value.counterGankAttemptsDelta()));
                })
                .toList();
    }

    private static List<ProfileMechanicOverview> profileMechanicOverviews(
            List<MatchRow> rows
    ) {
        Map<ProfileKey, List<MatchRow>> groups = rows.stream().collect(
                Collectors.groupingBy(
                        row -> new ProfileKey(row.fixtureLane(), row.profileId()),
                        LinkedHashMap::new,
                        Collectors.toList()));
        return groups.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> new ProfileMechanicOverview(
                        entry.getKey().fixtureLane(),
                        entry.getKey().profileId(),
                        entry.getValue().size(),
                        entry.getValue().stream()
                                .mapToLong(MatchRow::jungleEconomyAwardedCs).sum(),
                        entry.getValue().stream()
                                .mapToLong(MatchRow::jungleEconomyAwardedGold).sum(),
                        entry.getValue().stream()
                                .mapToLong(MatchRow::jungleEconomyAwardedExperience).sum(),
                        entry.getValue().stream()
                                .mapToDouble(MatchRow::tempoCreditAddedSeconds).sum(),
                        entry.getValue().stream()
                                .mapToLong(MatchRow::tempoGankReadyObservations).sum(),
                        entry.getValue().stream()
                                .mapToLong(MatchRow::tempoCounterGankReadyObservations).sum(),
                        entry.getValue().stream()
                                .mapToLong(MatchRow::tempoGankConsumptions).sum(),
                        entry.getValue().stream()
                                .mapToLong(MatchRow::tempoCounterGankConsumptions).sum()))
                .toList();
    }

    private static CheckpointSamplingOverview checkpointSampling(List<MatchRow> rows) {
        List<JungleObservation> fixed = rows.stream()
                .flatMap(row -> row.jungleObservations().stream())
                .filter(value -> value.checkpointKind().equals("FIXED"))
                .toList();
        long delayed = fixed.stream().filter(value -> value.actualTimeSeconds()
                > value.requestedTimeSeconds()).count();
        int maxLag = fixed.stream().mapToInt(value -> value.actualTimeSeconds()
                - value.requestedTimeSeconds()).max().orElse(0);
        return new CheckpointSamplingOverview(
                "FIRST_RECORDED_SNAPSHOT_AT_OR_AFTER_REQUESTED_TIME_NO_INTERPOLATION",
                fixed.size(), delayed, maxLag);
    }

    private static String jobManifest(
            List<Phase13GB2CalibrationContract.CalibrationJob> jobs
    ) {
        StringBuilder result = new StringBuilder();
        appendCsv(result, "jobId", "fixtureId", "fixtureLane", "pairId",
                "blueTeamCode", "redTeamCode", "seriesGameNumber", "sampleLane",
                "seedIndex", "seed", "profileIndex", "profileId");
        jobs.forEach(job -> appendCsv(result,
                job.jobId(), job.fixtureId(), job.fixtureLane(), job.pairId(),
                job.blueTeamCode(), job.redTeamCode(), job.seriesGameNumber(),
                job.sampleLane(), job.seedIndex(), job.seed(), job.profileIndex(),
                job.profileId()));
        return result.toString();
    }

    private static String fixedDrafts(List<FixtureCheckpoint> checkpoints) {
        StringBuilder result = new StringBuilder();
        appendCsv(result, "fixtureId", "fixtureLane", "pairId", "blueTeamCode",
                "redTeamCode", "seriesGameNumber", "productionOrchestrationCount",
                "fixtureReusePolicy", "rosterIdentityHash", "seriesHistoryBeforeHash",
                "draftDecisionHash",
                "finalDraftHash", "finalAssignmentHash", "blueBans", "redBans",
                "bluePicks", "redPicks", "canonicalAssignments");
        checkpoints.stream().map(FixtureCheckpoint::fixedDraft).forEach(value -> appendCsv(
                result, value.fixtureId(), value.fixtureLane(), value.pairId(),
                value.blueTeamCode(), value.redTeamCode(), value.seriesGameNumber(),
                value.productionOrchestrationCount(), value.fixtureReusePolicy(),
                value.rosterIdentityHash(), value.seriesHistoryBeforeHash(),
                value.draftDecisionHash(),
                value.finalDraftHash(), value.finalAssignmentHash(), value.blueBans(),
                value.redBans(), value.bluePicks(), value.redPicks(),
                value.canonicalAssignments()));
        return result.toString();
    }

    private static String determinismReplays(List<FixtureCheckpoint> checkpoints) {
        StringBuilder result = new StringBuilder();
        appendCsv(result, "fixtureId", "fixtureLane", "pairId", "seedIndex", "seed",
                "profileId", "replayProvenanceHash", "timelineHash",
                "structuredDiagnosticsHash", "randomDrawCount", "randomTraceHash",
                "fullStructuredDiagnosticsExact", "exact", "runGuardHash");
        checkpoints.forEach(checkpoint -> {
            var replay = checkpoint.determinismReplay();
            var draft = checkpoint.fixedDraft();
            appendCsv(result, replay.fixtureId(), draft.fixtureLane(), draft.pairId(),
                    replay.seedIndex(), replay.seed(), replay.profileId(),
                    replay.replayProvenanceHash(), replay.timelineHash(),
                    replay.structuredDiagnosticsHash(), replay.randomDrawCount(),
                    replay.randomTraceHash(), replay.fullStructuredDiagnosticsExact(),
                    replay.exact(), checkpoint.runGuardHash());
        });
        return result.toString();
    }

    private static String matchesCsv(List<MatchRow> rows) {
        StringBuilder result = new StringBuilder();
        appendCsv(result, "jobId", "fixtureId", "fixtureLane", "pairId", "seedIndex",
                "seed", "profileId", "configurationHash", "engineImplementationVersion",
                "replayProvenanceHash", "timelineHash", "structuredDiagnosticsHash",
                "randomDrawCount", "randomTraceHash", "winnerSide", "endReason",
                "durationSeconds", "blueKills", "redKills", "blueGold", "redGold",
                "blueDragons", "redDragons", "blueTowers", "redTowers",
                "blueJunglePlayerId", "blueJungleChampionId", "blueJungleCs",
                "blueJungleGold", "blueJungleXp", "blueJungleLevel",
                "redJunglePlayerId", "redJungleChampionId", "redJungleCs",
                "redJungleGold", "redJungleXp", "redJungleLevel",
                "jungleEconomyEvaluations", "jungleEconomyEligibleOutcomes",
                "jungleEconomySkippedOutcomes", "jungleEconomyAwardedCs",
                "jungleEconomyAwardedGold", "jungleEconomyAwardedExperience",
                "tempoEconomyUpdates", "tempoCreditAddedSeconds",
                "tempoGankReadyObservations", "tempoCounterGankReadyObservations",
                "tempoGankConsumptions", "tempoCounterGankConsumptions",
                "jungleGankEvaluations", "jungleGankTriggerSuccesses",
                "jungleGankFallthroughs", "jungleGankAttempts", "counterGankAttempts",
                "laneCombatAttempts", "integrityErrorCount", "integrityClean");
        rows.forEach(row -> appendCsv(result,
                row.jobId(), row.fixtureId(), row.fixtureLane(), row.pairId(),
                row.seedIndex(), row.seed(), row.profileId(), row.configurationHash(),
                row.engineImplementationVersion(), row.replayProvenanceHash(),
                row.timelineHash(), row.structuredDiagnosticsHash(), row.randomDrawCount(),
                row.randomTraceHash(), row.winnerSide(), row.endReason(),
                row.durationSeconds(), row.blueKills(), row.redKills(), row.blueGold(),
                row.redGold(), row.blueDragons(), row.redDragons(), row.blueTowers(),
                row.redTowers(), row.blueJungleFinal().playerId(),
                row.blueJungleFinal().championId(), row.blueJungleFinal().cs(),
                row.blueJungleFinal().gold(), row.blueJungleFinal().totalExperience(),
                row.blueJungleFinal().level(), row.redJungleFinal().playerId(),
                row.redJungleFinal().championId(), row.redJungleFinal().cs(),
                row.redJungleFinal().gold(), row.redJungleFinal().totalExperience(),
                row.redJungleFinal().level(), row.jungleEconomyEvaluations(),
                row.jungleEconomyEligibleOutcomes(), row.jungleEconomySkippedOutcomes(),
                row.jungleEconomyAwardedCs(), row.jungleEconomyAwardedGold(),
                row.jungleEconomyAwardedExperience(), row.tempoEconomyUpdates(),
                row.tempoCreditAddedSeconds(), row.tempoGankReadyObservations(),
                row.tempoCounterGankReadyObservations(), row.tempoGankConsumptions(),
                row.tempoCounterGankConsumptions(), row.jungleGankEvaluations(),
                row.jungleGankTriggerSuccesses(), row.jungleGankFallthroughs(),
                row.jungleGankAttempts(), row.counterGankAttempts(),
                row.laneCombatAttempts(), row.integrityErrorCount(), row.integrityClean()));
        return result.toString();
    }

    private static String jungleCheckpoints(List<MatchRow> rows) {
        StringBuilder result = new StringBuilder();
        appendCsv(result, "jobId", "fixtureId", "fixtureLane", "seedIndex", "profileId",
                "checkpointKind", "requestedTimeSeconds", "actualTimeSeconds", "side",
                "playerId", "championId", "kills", "deaths", "assists", "cs", "gold",
                "totalExperience", "level", "itemStage", "alive", "canFarm");
        for (MatchRow row : rows) {
            for (JungleObservation value : row.jungleObservations()) {
                appendCsv(result, row.jobId(), row.fixtureId(), row.fixtureLane(),
                        row.seedIndex(), row.profileId(), value.checkpointKind(),
                        value.requestedTimeSeconds(), value.actualTimeSeconds(), value.side(),
                        value.playerId(), value.championId(), value.kills(), value.deaths(),
                        value.assists(), value.cs(), value.gold(), value.totalExperience(),
                        value.level(), value.itemStage(), value.alive(), value.canFarm());
            }
        }
        return result.toString();
    }

    private static String marginalsCsv(List<MarginalRow> rows) {
        StringBuilder result = new StringBuilder();
        appendCsv(result, "comparisonId", "fixtureId", "fixtureLane", "pairId",
                "blueTeamCode", "redTeamCode", "seedIndex", "seed", "fromProfile",
                "toProfile", "fromWinner", "toWinner", "winnerFlipped", "durationDelta",
                "blueGoldEdgeDelta", "totalKillsDelta", "totalDragonsDelta",
                "totalTowersDelta", "blueJungleCsDelta", "blueJungleGoldDelta",
                "blueJungleExperienceDelta", "blueJungleLevelDelta", "redJungleCsDelta",
                "redJungleGoldDelta", "redJungleExperienceDelta", "redJungleLevelDelta",
                "jungleGankAttemptsDelta", "counterGankAttemptsDelta");
        rows.forEach(row -> appendCsv(result,
                row.comparisonId(), row.fixtureId(), row.fixtureLane(), row.pairId(),
                row.blueTeamCode(), row.redTeamCode(), row.seedIndex(), row.seed(),
                row.fromProfile(), row.toProfile(), row.fromWinner(), row.toWinner(),
                row.winnerFlipped(), row.durationDelta(), row.blueGoldEdgeDelta(),
                row.totalKillsDelta(), row.totalDragonsDelta(), row.totalTowersDelta(),
                row.blueJungleCsDelta(), row.blueJungleGoldDelta(),
                row.blueJungleExperienceDelta(), row.blueJungleLevelDelta(),
                row.redJungleCsDelta(), row.redJungleGoldDelta(),
                row.redJungleExperienceDelta(), row.redJungleLevelDelta(),
                row.jungleGankAttemptsDelta(), row.counterGankAttemptsDelta()));
        return result.toString();
    }

    private static String marginalSummaryCsv(List<MarginalRow> rows, boolean byPair) {
        Map<SummaryKey, List<MarginalRow>> groups = rows.stream().collect(
                Collectors.groupingBy(
                        row -> new SummaryKey(
                                row.fixtureLane(),
                                byPair ? row.pairId() : "ALL_PAIRS",
                                row.comparisonId()),
                        LinkedHashMap::new,
                        Collectors.toList()));
        StringBuilder result = new StringBuilder();
        appendCsv(result, "fixtureLane", "pairId", "comparisonId", "metric", "count",
                "mean", "median", "p10", "p90", "p95", "zeroRate", "positiveRate",
                "negativeRate", "winnerFlipCount", "winnerFlipRate",
                "blueToRedFlipCount", "redToBlueFlipCount", "otherFlipCount");
        groups.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
            long flips = entry.getValue().stream().filter(MarginalRow::winnerFlipped).count();
            long blueToRed = entry.getValue().stream().filter(row ->
                    row.fromWinner() == TeamSide.BLUE && row.toWinner() == TeamSide.RED).count();
            long redToBlue = entry.getValue().stream().filter(row ->
                    row.fromWinner() == TeamSide.RED && row.toWinner() == TeamSide.BLUE).count();
            for (MarginalMetric metric : MarginalMetric.values()) {
                Stats stats = stats(entry.getValue().stream()
                        .map(metric::value).toList());
                appendCsv(result,
                        entry.getKey().fixtureLane(), entry.getKey().pairId(),
                        entry.getKey().comparisonId(), metric, stats.count(), stats.mean(),
                        stats.median(), stats.p10(), stats.p90(), stats.p95(),
                        stats.zeroRate(), stats.positiveRate(), stats.negativeRate(), flips,
                        ratio(flips, entry.getValue().size()), blueToRed, redToBlue,
                        flips - blueToRed - redToBlue);
            }
        });
        return result.toString();
    }

    private static String profileSummaryCsv(List<MatchRow> rows) {
        Map<ProfileKey, List<MatchRow>> groups = rows.stream().collect(
                Collectors.groupingBy(
                        row -> new ProfileKey(row.fixtureLane(), row.profileId()),
                        LinkedHashMap::new,
                        Collectors.toList()));
        StringBuilder result = new StringBuilder();
        appendCsv(result, "fixtureLane", "profileId", "matchCount", "blueWinRate",
                "redWinRate", "timeoutRate", "averageDurationSeconds", "averageTotalKills",
                "averageBlueGold", "averageRedGold", "averageBlueJungleCs",
                "averageRedJungleCs", "averageBlueJungleGold", "averageRedJungleGold",
                "averageBlueJungleExperience", "averageRedJungleExperience",
                "averageJungleEconomyAwardedCs", "averageJungleEconomyAwardedGold",
                "averageJungleEconomyAwardedExperience", "averageTempoCreditAddedSeconds",
                "averageTempoGankReadyObservations",
                "averageTempoCounterGankReadyObservations",
                "averageTempoGankConsumptions", "averageTempoCounterGankConsumptions",
                "averageJungleGankAttempts", "averageCounterGankAttempts",
                "integrityErrorCount");
        groups.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
            List<MatchRow> values = entry.getValue();
            appendCsv(result, entry.getKey().fixtureLane(), entry.getKey().profileId(),
                    values.size(),
                    ratio(values.stream().filter(row -> row.winnerSide() == TeamSide.BLUE)
                            .count(), values.size()),
                    ratio(values.stream().filter(row -> row.winnerSide() == TeamSide.RED)
                            .count(), values.size()),
                    ratio(values.stream().filter(row -> row.endReason()
                            == GameEndReason.SIMULATION_TIMEOUT).count(), values.size()),
                    mean(values, MatchRow::durationSeconds),
                    mean(values, row -> row.blueKills() + row.redKills()),
                    mean(values, MatchRow::blueGold), mean(values, MatchRow::redGold),
                    mean(values, row -> row.blueJungleFinal().cs()),
                    mean(values, row -> row.redJungleFinal().cs()),
                    mean(values, row -> row.blueJungleFinal().gold()),
                    mean(values, row -> row.redJungleFinal().gold()),
                    mean(values, row -> row.blueJungleFinal().totalExperience()),
                    mean(values, row -> row.redJungleFinal().totalExperience()),
                    mean(values, MatchRow::jungleEconomyAwardedCs),
                    mean(values, MatchRow::jungleEconomyAwardedGold),
                    mean(values, MatchRow::jungleEconomyAwardedExperience),
                    mean(values, MatchRow::tempoCreditAddedSeconds),
                    mean(values, MatchRow::tempoGankReadyObservations),
                    mean(values, MatchRow::tempoCounterGankReadyObservations),
                    mean(values, MatchRow::tempoGankConsumptions),
                    mean(values, MatchRow::tempoCounterGankConsumptions),
                    mean(values, MatchRow::jungleGankAttempts),
                    mean(values, MatchRow::counterGankAttempts),
                    values.stream().mapToLong(MatchRow::integrityErrorCount).sum());
        });
        return result.toString();
    }

    private static String teamSideSummaryCsv(List<MatchRow> rows) {
        ArrayList<TeamObservation> observations = new ArrayList<>(rows.size() * 2);
        for (MatchRow row : rows) {
            observations.add(TeamObservation.from(row, TeamSide.BLUE));
            observations.add(TeamObservation.from(row, TeamSide.RED));
        }
        Map<TeamKey, List<TeamObservation>> groups = observations.stream().collect(
                Collectors.groupingBy(
                        value -> new TeamKey(
                                value.fixtureLane(), value.profileId(),
                                value.teamCode(), value.side()),
                        LinkedHashMap::new,
                        Collectors.toList()));
        StringBuilder result = new StringBuilder();
        appendCsv(result, "fixtureLane", "profileId", "teamCode", "side", "matchCount",
                "winRate", "averageGold", "averageKills", "averageDragons",
                "averageTowers", "averageJungleCs", "averageJungleGold",
                "averageJungleExperience", "averageJungleLevel");
        groups.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
            List<TeamObservation> values = entry.getValue();
            appendCsv(result, entry.getKey().fixtureLane(), entry.getKey().profileId(),
                    entry.getKey().teamCode(), entry.getKey().side(), values.size(),
                    ratio(values.stream().filter(TeamObservation::won).count(), values.size()),
                    mean(values, TeamObservation::gold), mean(values, TeamObservation::kills),
                    mean(values, TeamObservation::dragons),
                    mean(values, TeamObservation::towers),
                    mean(values, TeamObservation::jungleCs),
                    mean(values, TeamObservation::jungleGold),
                    mean(values, TeamObservation::jungleExperience),
                    mean(values, TeamObservation::jungleLevel));
        });
        return result.toString();
    }

    private static String junglerChampionSummaryCsv(List<MatchRow> rows) {
        ArrayList<JunglerObservation> observations = new ArrayList<>(rows.size() * 2);
        for (MatchRow row : rows) {
            observations.add(JunglerObservation.from(row, TeamSide.BLUE));
            observations.add(JunglerObservation.from(row, TeamSide.RED));
        }
        Map<JunglerKey, List<JunglerObservation>> groups = observations.stream().collect(
                Collectors.groupingBy(
                        value -> new JunglerKey(
                                value.fixtureLane(), value.profileId(), value.playerId(),
                                value.championId(), value.side()),
                        LinkedHashMap::new,
                        Collectors.toList()));
        StringBuilder result = new StringBuilder();
        appendCsv(result, "fixtureLane", "profileId", "playerId", "championId", "side",
                "matchCount", "winRate", "averageCs", "averageGold",
                "averageExperience", "averageLevel");
        groups.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
            List<JunglerObservation> values = entry.getValue();
            appendCsv(result, entry.getKey().fixtureLane(), entry.getKey().profileId(),
                    entry.getKey().playerId(), entry.getKey().championId(),
                    entry.getKey().side(), values.size(),
                    ratio(values.stream().filter(JunglerObservation::won).count(),
                            values.size()),
                    mean(values, JunglerObservation::cs),
                    mean(values, JunglerObservation::gold),
                    mean(values, JunglerObservation::experience),
                    mean(values, JunglerObservation::level));
        });
        return result.toString();
    }

    private static Stats stats(List<Double> source) {
        List<Double> values = source.stream().sorted().toList();
        if (values.isEmpty()) return new Stats(0, Double.NaN, Double.NaN, Double.NaN,
                Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.NaN);
        long zero = values.stream().filter(value -> value == 0).count();
        long positive = values.stream().filter(value -> value > 0).count();
        long negative = values.size() - zero - positive;
        return new Stats(
                values.size(),
                values.stream().mapToDouble(Double::doubleValue).average().orElseThrow(),
                quantile(values, 0.50),
                quantile(values, 0.10),
                quantile(values, 0.90),
                quantile(values, 0.95),
                ratio(zero, values.size()),
                ratio(positive, values.size()),
                ratio(negative, values.size()));
    }

    private static double quantile(List<Double> sorted, double probability) {
        int index = Math.max(0, Math.min(
                sorted.size() - 1,
                (int) Math.ceil(probability * sorted.size()) - 1));
        return sorted.get(index);
    }

    private static <T> double mean(List<T> values, java.util.function.ToDoubleFunction<T> value) {
        return values.stream().mapToDouble(value).average().orElse(Double.NaN);
    }

    private static double ratio(long numerator, long denominator) {
        return denominator == 0 ? Double.NaN : numerator / (double) denominator;
    }

    private static void writeJson(
            ObjectMapper mapper,
            Path output,
            Object value
    ) throws IOException {
        byte[] json = mapper.writeValueAsBytes(value);
        byte[] withNewline = java.util.Arrays.copyOf(json, json.length + 1);
        withNewline[json.length] = '\n';
        Files.write(output, withNewline);
    }

    private static void writeJsonLines(
            ObjectMapper mapper,
            Path output,
            List<MatchRow> rows
    ) throws IOException {
        ObjectMapper compact = mapper.copy().disable(SerializationFeature.INDENT_OUTPUT);
        try (BufferedWriter writer = Files.newBufferedWriter(output, StandardCharsets.UTF_8)) {
            for (MatchRow row : rows) {
                writer.write(compact.writeValueAsString(row));
                writer.newLine();
            }
        }
    }

    private static void writeManifest(Path output) throws IOException {
        StringBuilder manifest = new StringBuilder();
        for (String file : HASHED_FILES) {
            manifest.append(Phase13GB2CheckpointStore.sha256(
                    Files.readAllBytes(output.resolve(file))))
                    .append("  ").append(file).append('\n');
        }
        Phase13GB2CheckpointStore.writeUtf8(output.resolve(SHA_FILE), manifest.toString());
    }

    private static void appendCsv(StringBuilder target, Object... values) {
        for (int index = 0; index < values.length; index++) {
            if (index > 0) target.append(',');
            target.append(csv(values[index]));
        }
        target.append('\n');
    }

    private static String csv(Object value) {
        if (value == null) return "";
        String text = value instanceof Double number
                ? Double.toString(number) : value.toString();
        if (text.indexOf(',') >= 0 || text.indexOf('"') >= 0
                || text.indexOf('\n') >= 0 || text.indexOf('\r') >= 0) {
            return '"' + text.replace("\"", "\"\"") + '"';
        }
        return text;
    }

    private enum MarginalMetric {
        DURATION_SECONDS,
        BLUE_GOLD_EDGE,
        TOTAL_KILLS,
        TOTAL_DRAGONS,
        TOTAL_TOWERS,
        BLUE_JUNGLE_CS,
        BLUE_JUNGLE_GOLD,
        BLUE_JUNGLE_EXPERIENCE,
        BLUE_JUNGLE_LEVEL,
        RED_JUNGLE_CS,
        RED_JUNGLE_GOLD,
        RED_JUNGLE_EXPERIENCE,
        RED_JUNGLE_LEVEL,
        JUNGLE_GANK_ATTEMPTS,
        COUNTER_GANK_ATTEMPTS;

        double value(MarginalRow row) {
            return switch (this) {
                case DURATION_SECONDS -> row.durationDelta();
                case BLUE_GOLD_EDGE -> row.blueGoldEdgeDelta();
                case TOTAL_KILLS -> row.totalKillsDelta();
                case TOTAL_DRAGONS -> row.totalDragonsDelta();
                case TOTAL_TOWERS -> row.totalTowersDelta();
                case BLUE_JUNGLE_CS -> row.blueJungleCsDelta();
                case BLUE_JUNGLE_GOLD -> row.blueJungleGoldDelta();
                case BLUE_JUNGLE_EXPERIENCE -> row.blueJungleExperienceDelta();
                case BLUE_JUNGLE_LEVEL -> row.blueJungleLevelDelta();
                case RED_JUNGLE_CS -> row.redJungleCsDelta();
                case RED_JUNGLE_GOLD -> row.redJungleGoldDelta();
                case RED_JUNGLE_EXPERIENCE -> row.redJungleExperienceDelta();
                case RED_JUNGLE_LEVEL -> row.redJungleLevelDelta();
                case JUNGLE_GANK_ATTEMPTS -> row.jungleGankAttemptsDelta();
                case COUNTER_GANK_ATTEMPTS -> row.counterGankAttemptsDelta();
            };
        }
    }

    private record Validation(
            List<Phase13GB2CalibrationContract.CalibrationJob> expectedJobs,
            List<MatchRow> rows,
            List<MarginalRow> marginals,
            IntegrityReport integrity,
            String runGuardHash,
            Phase13GB2CalibrationModel.CheckpointReceiptManifest receiptManifest,
            int fixturePreparationOrchestrationCount
    ) {
    }

    public record IntegrityReport(
            String schemaVersion,
            String status,
            int expectedFixtureCount,
            int completedFixtureCount,
            int expectedMatchCount,
            int actualMatchCount,
            long distinctJobCount,
            boolean jobManifestExact,
            long holdoutMatchExecutionCount,
            int expectedDeterminismReplayCount,
            int actualDeterminismReplayCount,
            long nonExactDeterminismReplayCount,
            boolean determinismReplayCoverageExact,
            long dirtyMatchCount,
            long totalIntegrityErrorCount,
            long economyIntegrityErrors,
            long progressionIntegrityErrors,
            long championPowerIntegrityErrors,
            long championMatchupIntegrityErrors,
            long compositionIntegrityErrors,
            long combatOutcomeIntegrityErrors,
            long objectivePriorityIntegrityErrors,
            long structureIntegrityErrors,
            long lanePhaseIntegrityErrors,
            long midGameMacroIntegrityErrors,
            int expectedWorkerShardCount,
            int workerReceiptCount,
            int distinctFreshJvmCount,
            int checkpointPayloadCount,
            boolean checkpointPayloadDigestExact,
            String checkpointPayloadManifestHash,
            RunGuard runGuard
    ) {
    }

    private record Stats(
            int count,
            double mean,
            double median,
            double p10,
            double p90,
            double p95,
            double zeroRate,
            double positiveRate,
            double negativeRate
    ) {
    }

    private record SummaryKey(
            Phase13GB1AuditSchedule.FixtureLane fixtureLane,
            String pairId,
            String comparisonId
    ) implements Comparable<SummaryKey> {
        @Override
        public int compareTo(SummaryKey other) {
            int lane = fixtureLane.compareTo(other.fixtureLane);
            if (lane != 0) return lane;
            int pair = pairId.compareTo(other.pairId);
            return pair != 0 ? pair : comparisonId.compareTo(other.comparisonId);
        }
    }

    private record ProfileKey(
            Phase13GB1AuditSchedule.FixtureLane fixtureLane,
            SimulationRuntimeProfileId profileId
    ) implements Comparable<ProfileKey> {
        @Override
        public int compareTo(ProfileKey other) {
            int lane = fixtureLane.compareTo(other.fixtureLane);
            return lane != 0 ? lane : profileId.compareTo(other.profileId);
        }
    }

    private record TeamKey(
            Phase13GB1AuditSchedule.FixtureLane fixtureLane,
            SimulationRuntimeProfileId profileId,
            String teamCode,
            TeamSide side
    ) implements Comparable<TeamKey> {
        @Override
        public int compareTo(TeamKey other) {
            int lane = fixtureLane.compareTo(other.fixtureLane);
            if (lane != 0) return lane;
            int profile = profileId.compareTo(other.profileId);
            if (profile != 0) return profile;
            int team = teamCode.compareTo(other.teamCode);
            return team != 0 ? team : side.compareTo(other.side);
        }
    }

    private record JunglerKey(
            Phase13GB1AuditSchedule.FixtureLane fixtureLane,
            SimulationRuntimeProfileId profileId,
            String playerId,
            String championId,
            TeamSide side
    ) implements Comparable<JunglerKey> {
        @Override
        public int compareTo(JunglerKey other) {
            int lane = fixtureLane.compareTo(other.fixtureLane);
            if (lane != 0) return lane;
            int profile = profileId.compareTo(other.profileId);
            if (profile != 0) return profile;
            int player = playerId.compareTo(other.playerId);
            if (player != 0) return player;
            int champion = championId.compareTo(other.championId);
            return champion != 0 ? champion : side.compareTo(other.side);
        }
    }

    private record OverviewKey(
            Phase13GB1AuditSchedule.FixtureLane fixtureLane,
            String comparisonId
    ) implements Comparable<OverviewKey> {
        @Override
        public int compareTo(OverviewKey other) {
            int lane = fixtureLane.compareTo(other.fixtureLane);
            return lane != 0 ? lane : comparisonId.compareTo(other.comparisonId);
        }
    }

    public record MarginalOverview(
            Phase13GB1AuditSchedule.FixtureLane fixtureLane,
            String comparisonId,
            int comparisonCount,
            double winnerFlipRate,
            double meanDurationDelta,
            double meanBlueGoldEdgeDelta,
            double meanBlueJungleCsDelta,
            double meanRedJungleCsDelta,
            double meanBlueJungleExperienceDelta,
            double meanRedJungleExperienceDelta,
            double meanJungleGankAttemptsDelta,
            double meanCounterGankAttemptsDelta
    ) {
    }

    public record ProfileMechanicOverview(
            Phase13GB1AuditSchedule.FixtureLane fixtureLane,
            SimulationRuntimeProfileId profileId,
            int matchCount,
            long totalJungleEconomyAwardedCs,
            long totalJungleEconomyAwardedGold,
            long totalJungleEconomyAwardedExperience,
            double totalTempoCreditAddedSeconds,
            long totalTempoGankReadyObservations,
            long totalTempoCounterGankReadyObservations,
            long totalTempoGankConsumptions,
            long totalTempoCounterGankConsumptions
    ) {
    }

    public record CheckpointSamplingOverview(
            String policy,
            int fixedSideObservationCount,
            long delayedSideObservationCount,
            int maximumDelaySeconds
    ) {
    }

    private record TeamObservation(
            Phase13GB1AuditSchedule.FixtureLane fixtureLane,
            SimulationRuntimeProfileId profileId,
            String teamCode,
            TeamSide side,
            boolean won,
            int gold,
            int kills,
            int dragons,
            int towers,
            int jungleCs,
            int jungleGold,
            int jungleExperience,
            int jungleLevel
    ) {
        static TeamObservation from(MatchRow row, TeamSide side) {
            boolean blue = side == TeamSide.BLUE;
            var jungle = blue ? row.blueJungleFinal() : row.redJungleFinal();
            return new TeamObservation(
                    row.fixtureLane(), row.profileId(),
                    blue ? row.blueTeamCode() : row.redTeamCode(), side,
                    row.winnerSide() == side,
                    blue ? row.blueGold() : row.redGold(),
                    blue ? row.blueKills() : row.redKills(),
                    blue ? row.blueDragons() : row.redDragons(),
                    blue ? row.blueTowers() : row.redTowers(),
                    jungle.cs(), jungle.gold(), jungle.totalExperience(), jungle.level());
        }
    }

    private record JunglerObservation(
            Phase13GB1AuditSchedule.FixtureLane fixtureLane,
            SimulationRuntimeProfileId profileId,
            String playerId,
            String championId,
            TeamSide side,
            boolean won,
            int cs,
            int gold,
            int experience,
            int level
    ) {
        static JunglerObservation from(MatchRow row, TeamSide side) {
            var jungle = side == TeamSide.BLUE
                    ? row.blueJungleFinal() : row.redJungleFinal();
            return new JunglerObservation(
                    row.fixtureLane(), row.profileId(), jungle.playerId(),
                    jungle.championId(), side, row.winnerSide() == side,
                    jungle.cs(), jungle.gold(), jungle.totalExperience(), jungle.level());
        }
    }

    public record ArtifactSet(
            Path outputDirectory,
            String status,
            int calibrationMatchCount,
            int pairedMarginalCount,
            String reviewSha256,
            String shaManifestSha256
    ) {
    }

    record SmokeArtifactSet(
            Path outputDirectory,
            int matchCount,
            int marginalCount
    ) {
    }

    record SyntheticArtifactSet(
            Path reportPath,
            String status,
            int rowCount,
            boolean officialCalibrationEvidence
    ) {
    }
}
