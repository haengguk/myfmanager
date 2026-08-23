package com.lolfm.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.lolfm.application.Phase13GB3FrozenHoldoutContract.ExactBehaviorGate;
import com.lolfm.application.Phase13GB3FrozenHoldoutContract.FrozenContract;
import com.lolfm.application.Phase13GB3FrozenHoldoutContract.HoldoutJob;
import com.lolfm.application.Phase13GB3FrozenHoldoutContract.NumericGate;
import com.lolfm.application.Phase13GB3FrozenHoldoutContract.RunGuard;
import com.lolfm.application.Phase13GB3HoldoutModel.FixtureCheckpoint;
import com.lolfm.application.Phase13GB3HoldoutModel.MatchRow;
import com.lolfm.simulator.GameEndReason;
import com.lolfm.simulator.SimulationRuntimeProfileId;
import com.lolfm.simulator.TeamSide;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Artifact-only B3 finalizer; official output requires receipt-bound evidence. */
public final class Phase13GB3ArtifactWriter {
    public static final String JOB_MANIFEST_FILE = "phase13g-b3-holdout-job-manifest.csv";
    public static final String FIXED_DRAFTS_FILE = "phase13g-b3-fixed-drafts.csv";
    public static final String REPLAYS_FILE = "phase13g-b3-determinism-replays.csv";
    public static final String RECEIPTS_FILE =
            "phase13g-b3-checkpoint-receipt-manifest.json";
    public static final String MATCHES_JSONL_FILE = "phase13g-b3-matches.jsonl";
    public static final String MATCHES_CSV_FILE = "phase13g-b3-matches.csv";
    public static final String JUNGLE_OBSERVATIONS_FILE =
            "phase13g-b3-jungle-observations.csv";
    public static final String PLAYER_STATES_FILE =
            "phase13g-b3-final-player-states.csv";
    public static final String PAIRED_MARGINALS_FILE =
            "phase13g-b3-paired-marginals.csv";
    public static final String MARGINAL_SUMMARY_FILE =
            "phase13g-b3-marginal-summary.csv";
    public static final String PROFILE_SUMMARY_FILE =
            "phase13g-b3-profile-summary.csv";
    public static final String TEAM_SIDE_SUMMARY_FILE =
            "phase13g-b3-team-side-summary.csv";
    public static final String JUNGLER_CHAMPION_SUMMARY_FILE =
            "phase13g-b3-jungler-champion-summary.csv";
    public static final String INTEGRITY_FILE = "phase13g-b3-full-domain-integrity.json";
    public static final String GATE_EVALUATION_FILE =
            "phase13g-b3-frozen-gate-evaluation.json";
    public static final String REVIEW_FILE = "phase13g-b3-final-review.json";
    public static final String SHA_FILE = "SHA256SUMS.txt";
    private static final List<String> HASHED_FILES = List.of(
            Phase13GB3CheckpointStore.CONTRACT_FILE,
            Phase13GB3CheckpointStore.CONTRACT_HASH_FILE,
            JOB_MANIFEST_FILE,
            FIXED_DRAFTS_FILE,
            REPLAYS_FILE,
            RECEIPTS_FILE,
            MATCHES_JSONL_FILE,
            MATCHES_CSV_FILE,
            JUNGLE_OBSERVATIONS_FILE,
            PLAYER_STATES_FILE,
            PAIRED_MARGINALS_FILE,
            MARGINAL_SUMMARY_FILE,
            PROFILE_SUMMARY_FILE,
            TEAM_SIDE_SUMMARY_FILE,
            JUNGLER_CHAMPION_SUMMARY_FILE,
            INTEGRITY_FILE,
            GATE_EVALUATION_FILE,
            REVIEW_FILE);

    private Phase13GB3ArtifactWriter() {
    }

    static SyntheticArtifactSet writeSyntheticValidation(
            ObjectMapper sourceMapper,
            Path output,
            List<MatchRow> rows
    ) throws IOException {
        Files.createDirectories(output);
        ObjectMapper mapper = Phase13GB3CheckpointStore.canonicalMapper(sourceMapper);
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("schemaVersion", "PHASE_13G_B3_SYNTHETIC_VALIDATION_V1");
        report.put("status", "SYNTHETIC_VALIDATION_ONLY");
        report.put("officialHoldoutEvidence", false);
        report.put("rowCount", rows.size());
        report.put("sampleLanes", rows.stream().map(MatchRow::sampleLane).distinct().toList());
        report.put("note", "Synthetic and dry-run rows cannot enter the official finalizer");
        Path path = output.resolve("phase13g-b3-synthetic-validation.json");
        writeJson(mapper, path, report);
        return new SyntheticArtifactSet(path, "SYNTHETIC_VALIDATION_ONLY", false);
    }

    public static ArtifactSet writeOfficial(
            ObjectMapper sourceMapper,
            Path output,
            Phase13GB1AuditSchedule.Schedule schedule,
            FrozenContract contract,
            RunGuard guard,
            Phase13GB3CheckpointStore.VerifiedOfficialEvidence evidence
    ) throws IOException {
        schedule = Phase13GB1AuditSchedule.requireFrozen(schedule);
        Objects.requireNonNull(evidence, "evidence");
        ObjectMapper mapper = Phase13GB3CheckpointStore.canonicalMapper(sourceMapper);
        Validation validation = validate(schedule, contract, guard, evidence);
        Files.createDirectories(output);
        Phase13GB3CheckpointStore.writeUtf8(
                output.resolve(JOB_MANIFEST_FILE), jobsCsv(validation.jobs()));
        Phase13GB3CheckpointStore.writeUtf8(
                output.resolve(FIXED_DRAFTS_FILE), fixedDraftsCsv(evidence.checkpoints()));
        Phase13GB3CheckpointStore.writeUtf8(
                output.resolve(REPLAYS_FILE), replaysCsv(evidence.checkpoints()));
        writeJson(mapper, output.resolve(RECEIPTS_FILE), evidence.receiptManifest());
        writeJsonLines(mapper, output.resolve(MATCHES_JSONL_FILE), validation.rows());
        Phase13GB3CheckpointStore.writeUtf8(
                output.resolve(MATCHES_CSV_FILE), matchesCsv(validation.rows()));
        Phase13GB3CheckpointStore.writeUtf8(
                output.resolve(JUNGLE_OBSERVATIONS_FILE),
                jungleObservationsCsv(validation.rows()));
        Phase13GB3CheckpointStore.writeUtf8(
                output.resolve(PLAYER_STATES_FILE), playerStatesCsv(validation.rows()));
        Phase13GB3CheckpointStore.writeUtf8(
                output.resolve(PAIRED_MARGINALS_FILE), marginalsCsv(validation.marginals()));
        Phase13GB3CheckpointStore.writeUtf8(
                output.resolve(MARGINAL_SUMMARY_FILE),
                marginalSummaryCsv(validation.marginals()));
        Phase13GB3CheckpointStore.writeUtf8(
                output.resolve(PROFILE_SUMMARY_FILE), profileSummaryCsv(validation.rows()));
        Phase13GB3CheckpointStore.writeUtf8(
                output.resolve(TEAM_SIDE_SUMMARY_FILE), teamSideSummaryCsv(validation.rows()));
        Phase13GB3CheckpointStore.writeUtf8(
                output.resolve(JUNGLER_CHAMPION_SUMMARY_FILE),
                junglerChampionSummaryCsv(validation.rows()));
        writeJson(mapper, output.resolve(INTEGRITY_FILE), validation.integrity());
        writeJson(mapper, output.resolve(GATE_EVALUATION_FILE), validation.gates());
        writeJson(mapper, output.resolve(REVIEW_FILE), validation.review());
        writeManifest(output);
        return new ArtifactSet(
                output,
                validation.review().get("evidenceStatus").toString(),
                validation.review().get("economyCandidateVerdict").toString(),
                validation.review().get("tempoCandidateVerdict").toString(),
                validation.rows().size(),
                Phase13GB3CheckpointStore.sha256(
                        Files.readAllBytes(output.resolve(REVIEW_FILE))),
                Phase13GB3CheckpointStore.sha256(
                        Files.readAllBytes(output.resolve(SHA_FILE))));
    }

    private static Validation validate(
            Phase13GB1AuditSchedule.Schedule schedule,
            FrozenContract contract,
            RunGuard guard,
            Phase13GB3CheckpointStore.VerifiedOfficialEvidence evidence
    ) {
        List<HoldoutJob> jobs = Phase13GB3FrozenHoldoutContract.jobs(schedule);
        List<FixtureCheckpoint> checkpoints = evidence.checkpoints();
        ArrayList<MatchRow> rows = new ArrayList<>();
        checkpoints.forEach(checkpoint -> rows.addAll(checkpoint.rows()));
        List<String> expectedJobIds = jobs.stream().map(HoldoutJob::jobId).toList();
        List<String> actualJobIds = rows.stream().map(MatchRow::jobId).toList();
        long distinctProvenance = rows.stream().map(MatchRow::replayProvenanceHash)
                .distinct().count();
        long integrityErrors = rows.stream().mapToLong(MatchRow::integrityErrorCount).sum();
        long timeouts = rows.stream().filter(row ->
                row.endReason() == GameEndReason.SIMULATION_TIMEOUT).count();
        long supportCs = rows.stream().mapToLong(row ->
                row.blueSupportCs() + row.redSupportCs()).sum();
        long nonHoldout = rows.stream().filter(row -> row.sampleLane()
                != Phase13GB1AuditSchedule.SampleLane.HOLDOUT).count();
        long nonExactReplay = checkpoints.stream().filter(checkpoint ->
                !checkpoint.determinismReplay().exact()
                        || !checkpoint.determinismReplay()
                                .fullStructuredDiagnosticsExact()).count();
        long profileContractErrors = rows.stream().filter(row -> !profileContract(row)).count();
        boolean receiptsExact = evidence.receiptManifest().workerReceiptCount() == 4
                && evidence.receiptManifest().distinctFreshJvmCount() == 4
                && evidence.receiptManifest().checkpointCount() == 100;
        boolean hardPass = checkpoints.size() == 100
                && rows.size() == 4_000
                && actualJobIds.equals(expectedJobIds)
                && actualJobIds.stream().distinct().count() == 4_000
                && distinctProvenance == 4_000
                && nonHoldout == 0
                && integrityErrors == 0
                && timeouts == 0
                && supportCs == 0
                && nonExactReplay == 0
                && profileContractErrors == 0
                && receiptsExact
                && guard.calibrationMatchExecutionCount() == 0
                && contract.holdoutExecutionCountAtFreeze() == 0;
        String evidenceStatus = hardPass
                ? "HOLDOUT_EVIDENCE_READY_FOR_FINAL_REVIEW"
                : "HOLDOUT_EVIDENCE_INVALID";
        List<MarginalRow> marginals = marginals(rows, schedule);
        GateEvaluation gateEvaluation = evaluateGates(contract, rows, marginals);
        boolean economyExact = gateEvaluation.exactResults().stream()
                .filter(result -> result.candidate().equals("ECONOMY")
                        || result.candidate().equals("BOTH"))
                .allMatch(ExactGateResult::passed);
        boolean tempoExact = gateEvaluation.exactResults().stream()
                .filter(result -> result.candidate().equals("TEMPO")
                        || result.candidate().equals("BOTH"))
                .allMatch(ExactGateResult::passed);
        boolean economyNumeric = gateEvaluation.numericResults().stream()
                .filter(result -> result.candidate().equals("ECONOMY"))
                .allMatch(NumericGateResult::passed);
        boolean tempoNumeric = gateEvaluation.numericResults().stream()
                .filter(result -> result.candidate().equals("TEMPO"))
                .allMatch(NumericGateResult::passed);
        String economyVerdict = !hardPass || !economyExact || !economyNumeric
                ? "FAIL" : "PASS";
        String tempoVerdict;
        if (!hardPass || !tempoExact) tempoVerdict = "FAIL";
        else if (!tempoNumeric) tempoVerdict = "DEFER_TO_V2";
        else tempoVerdict = "REVIEW_REQUIRED";

        Map<String, Object> integrity = new LinkedHashMap<>();
        integrity.put("schemaVersion", "PHASE_13G_B3_FULL_DOMAIN_INTEGRITY_V1");
        integrity.put("status", evidenceStatus);
        integrity.put("expectedFixtureCount", 100);
        integrity.put("checkpointCount", checkpoints.size());
        integrity.put("expectedMatchCount", 4_000);
        integrity.put("matchCount", rows.size());
        integrity.put("distinctJobCount", actualJobIds.stream().distinct().count());
        integrity.put("distinctReplayProvenanceCount", distinctProvenance);
        integrity.put("calibrationMatchExecutionCount", 0);
        integrity.put("holdoutExecutionCountAtFreeze", 0);
        integrity.put("nonHoldoutRowCount", nonHoldout);
        integrity.put("nonExactReplayCount", nonExactReplay);
        integrity.put("totalDomainIntegrityErrorCount", integrityErrors);
        integrity.put("timeoutCount", timeouts);
        integrity.put("supportFarmCs", supportCs);
        integrity.put("profileContractErrorCount", profileContractErrors);
        integrity.put("workerReceiptCount", evidence.receiptManifest().workerReceiptCount());
        integrity.put("distinctFreshJvmCount",
                evidence.receiptManifest().distinctFreshJvmCount());
        integrity.put("checkpointPayloadDigestExact", receiptsExact);
        integrity.put("checkpointPayloadManifestHash",
                evidence.receiptManifest().checkpointPayloadManifestHash());
        integrity.put("runGuard", guard);

        Map<String, Object> review = new LinkedHashMap<>();
        review.put("schemaVersion", "PHASE_13G_B3_FINAL_REVIEW_V1");
        review.put("phase", Phase13GB3FrozenHoldoutContract.PHASE);
        review.put("evidenceStatus", evidenceStatus);
        review.put("economyCandidateVerdict", economyVerdict);
        review.put("tempoCandidateVerdict", tempoVerdict);
        review.put("productionDecision", "NOT_EVALUATED");
        review.put("frozenContractHash", guard.frozenContractHash());
        review.put("candidateFreezeIdentityHash", contract.candidateFreezeIdentityHash());
        review.put("acceptanceGateIdentityHash", contract.acceptanceGateIdentityHash());
        review.put("holdoutMatchExecutionCount", rows.size());
        review.put("calibrationMatchExecutionCount", 0);
        review.put("primaryMatchCount", rows.stream().filter(row -> row.fixtureLane()
                == Phase13GB1AuditSchedule.FixtureLane.PRIMARY_LEAGUE_G1).count());
        review.put("secondaryMatchCount", rows.stream().filter(row -> row.fixtureLane()
                == Phase13GB1AuditSchedule.FixtureLane.SECONDARY_HARD_FEARLESS_G2).count());
        review.put("pairedMarginalCount", marginals.size());
        review.put("b2Evidence", contract.b2Evidence());
        review.put("gateSummary", Map.of(
                "numericPassed", gateEvaluation.numericResults().stream()
                        .filter(NumericGateResult::passed).count(),
                "numericTotal", gateEvaluation.numericResults().size(),
                "exactPassed", gateEvaluation.exactResults().stream()
                        .filter(ExactGateResult::passed).count(),
                "exactTotal", gateEvaluation.exactResults().size()));
        review.put("automaticTuningPerformed", false);
        review.put("postHoldoutGateChangesPerformed", false);
        review.put("nextStep", "FINAL_13G_B_SYNTHESIS_AND_PRODUCTION_V1_DECISION");
        return new Validation(
                jobs, List.copyOf(rows), marginals, integrity, gateEvaluation, review);
    }

    private static boolean profileContract(MatchRow row) {
        if (row.profileIndex() < 3) {
            return row.jungleEconomyEvaluations() == 0
                    && row.tempoEconomyUpdates() == 0
                    && row.tempoGankConsumptions() == 0
                    && row.tempoCounterGankConsumptions() == 0;
        }
        if (row.profileIndex() == 3) {
            return row.jungleEconomyEvaluations() > 0
                    && row.tempoEconomyUpdates() == 0
                    && row.tempoGankConsumptions() == 0
                    && row.tempoCounterGankConsumptions() == 0;
        }
        return row.jungleEconomyEvaluations() > 0
                && row.tempoEconomyUpdates() > 0
                && row.tempoGankConsumptions() == row.jungleGankAttempts()
                && row.tempoCounterGankConsumptions() == row.counterGankAttempts();
    }

    private static List<MarginalRow> marginals(
            List<MatchRow> rows,
            Phase13GB1AuditSchedule.Schedule schedule
    ) {
        Map<String, MatchRow> byProfile = new LinkedHashMap<>();
        rows.forEach(row -> {
            String key = key(row.fixtureId(), row.seedIndex(), row.profileId());
            if (byProfile.put(key, row) != null) {
                throw new IllegalStateException("Duplicate B3 paired row");
            }
        });
        ArrayList<MarginalRow> result = new ArrayList<>(4_000);
        for (var fixture : schedule.allFixtures()) {
            for (int seedIndex = 0; seedIndex < 8; seedIndex++) {
                for (var comparison : Phase13GB3FrozenHoldoutContract.MARGINAL_COMPARISONS) {
                    result.add(MarginalRow.from(
                            comparison.comparisonId(),
                            byProfile.get(key(fixture.fixtureId(), seedIndex,
                                    comparison.fromProfile())),
                            byProfile.get(key(fixture.fixtureId(), seedIndex,
                                    comparison.toProfile()))));
                }
            }
        }
        if (result.size() != 4_000) throw new IllegalStateException();
        return List.copyOf(result);
    }

    private static String key(
            String fixtureId,
            int seedIndex,
            SimulationRuntimeProfileId profile
    ) {
        return fixtureId + '|' + seedIndex + '|' + profile;
    }

    private static GateEvaluation evaluateGates(
            FrozenContract contract,
            List<MatchRow> rows,
            List<MarginalRow> marginals
    ) {
        ArrayList<NumericGateResult> numeric = new ArrayList<>();
        for (NumericGate gate : contract.acceptance().numericGates()) {
            double actual;
            int count;
            if (gate.metric().equals("BLUE_TO_RED_SHARE_OF_FLIPS")) {
                List<MarginalRow> selected = select(marginals, gate).stream()
                        .filter(MarginalRow::winnerFlipped).toList();
                count = selected.size();
                actual = selected.isEmpty() ? Double.NaN
                        : selected.stream().filter(row -> row.fromWinner() == TeamSide.BLUE
                                && row.toWinner() == TeamSide.RED).count()
                                / (double) selected.size();
            } else {
                List<MarginalRow> selected = select(marginals, gate);
                count = selected.size();
                actual = selected.stream().mapToDouble(row -> metric(row, gate.metric()))
                        .average().orElse(Double.NaN);
            }
            boolean passed = !Double.isNaN(actual)
                    && actual >= gate.lowerInclusive()
                    && actual <= gate.upperInclusive();
            numeric.add(new NumericGateResult(
                    gate.gateId(), gate.candidate(), gate.comparisonId(),
                    gate.fixtureLane(), gate.metric(), count, actual,
                    gate.lowerInclusive(), gate.upperInclusive(), passed));
        }

        long economyCs = rows.stream().filter(row -> row.profileIndex() == 3)
                .mapToLong(MatchRow::jungleEconomyAwardedCs).sum();
        long economyGold = rows.stream().filter(row -> row.profileIndex() == 3)
                .mapToLong(MatchRow::jungleEconomyAwardedGold).sum();
        long economyXp = rows.stream().filter(row -> row.profileIndex() == 3)
                .mapToLong(MatchRow::jungleEconomyAwardedExperience).sum();
        long preContribution = rows.stream().filter(row -> row.profileIndex() < 3)
                .mapToLong(row -> row.jungleEconomyAwardedCs()
                        + row.jungleEconomyAwardedGold()
                        + row.jungleEconomyAwardedExperience()
                        + row.tempoGankConsumptions()
                        + row.tempoCounterGankConsumptions()).sum();
        long economyTempo = rows.stream().filter(row -> row.profileIndex() == 3)
                .mapToLong(row -> row.tempoEconomyUpdates()
                        + row.tempoGankConsumptions()
                        + row.tempoCounterGankConsumptions()).sum();
        long tempoReadyGank = rows.stream().filter(row -> row.profileIndex() == 4)
                .mapToLong(MatchRow::tempoGankReadyObservations).sum();
        long tempoConsumedGank = rows.stream().filter(row -> row.profileIndex() == 4)
                .mapToLong(MatchRow::tempoGankConsumptions).sum();
        long tempoReadyCounter = rows.stream().filter(row -> row.profileIndex() == 4)
                .mapToLong(MatchRow::tempoCounterGankReadyObservations).sum();
        long tempoConsumedCounter = rows.stream().filter(row -> row.profileIndex() == 4)
                .mapToLong(MatchRow::tempoCounterGankConsumptions).sum();
        long consumptionDifference = rows.stream().filter(row -> row.profileIndex() == 4)
                .mapToLong(row -> Math.abs(row.tempoGankConsumptions()
                                - row.jungleGankAttempts())
                        + Math.abs(row.tempoCounterGankConsumptions()
                                - row.counterGankAttempts())).sum();
        long supportCs = rows.stream().mapToLong(row ->
                row.blueSupportCs() + row.redSupportCs()).sum();
        long timeout = rows.stream().filter(row ->
                row.endReason() == GameEndReason.SIMULATION_TIMEOUT).count();
        Map<String, ExactActual> actuals = Map.of(
                "ECONOMY_AWARD_REACHABILITY",
                new ExactActual(economyCs + "/" + economyGold + "/" + economyXp,
                        economyCs > 0 && economyGold > 0 && economyXp > 0),
                "PRE_JUNGLE_CONTRIBUTION_ZERO",
                new ExactActual(Long.toString(preContribution), preContribution == 0),
                "ECONOMY_TEMPO_CONTRIBUTION_ZERO",
                new ExactActual(Long.toString(economyTempo), economyTempo == 0),
                "TEMPO_REACHABILITY",
                new ExactActual(tempoReadyGank + "/" + tempoConsumedGank + "/"
                        + tempoReadyCounter + "/" + tempoConsumedCounter,
                        tempoReadyGank > 0 && tempoConsumedGank > 0
                                && tempoReadyCounter > 0 && tempoConsumedCounter > 0),
                "TEMPO_CONSUMPTION_ATTEMPT_BINDING",
                new ExactActual(Long.toString(consumptionDifference),
                        consumptionDifference == 0),
                "SUPPORT_FARM_CS_INVARIANT",
                new ExactActual(Long.toString(supportCs), supportCs == 0),
                "TIMEOUTS", new ExactActual(Long.toString(timeout), timeout == 0));
        List<ExactGateResult> exact = contract.acceptance().exactBehaviorGates().stream()
                .map(gate -> {
                    ExactActual actual = actuals.get(gate.gateId());
                    return new ExactGateResult(
                            gate.gateId(), gate.candidate(), gate.metric(),
                            gate.comparator(), gate.expected(), actual.value(), actual.passed());
                }).toList();
        return new GateEvaluation(
                "PHASE_13G_B3_FROZEN_GATE_EVALUATION_V1",
                contract.acceptanceGateIdentityHash(),
                "ALL_BOUNDARIES_INCLUSIVE",
                List.copyOf(numeric),
                exact);
    }

    private static List<MarginalRow> select(List<MarginalRow> rows, NumericGate gate) {
        return rows.stream().filter(row -> row.comparisonId().equals(gate.comparisonId()))
                .filter(row -> gate.fixtureLane().equals("ALL")
                        || row.fixtureLane().name().equals(gate.fixtureLane())).toList();
    }

    private static double metric(MarginalRow row, String metric) {
        return switch (metric) {
            case "WINNER_FLIP_RATE" -> row.winnerFlipped() ? 1.0 : 0.0;
            case "DURATION_SECONDS" -> row.durationDelta();
            case "BLUE_GOLD_EDGE" -> row.blueGoldEdgeDelta();
            case "BLUE_JUNGLE_CS" -> row.blueJungleCsDelta();
            case "RED_JUNGLE_CS" -> row.redJungleCsDelta();
            case "BLUE_JUNGLE_EXPERIENCE" -> row.blueJungleExperienceDelta();
            case "RED_JUNGLE_EXPERIENCE" -> row.redJungleExperienceDelta();
            case "JUNGLE_GANK_ATTEMPTS" -> row.jungleGankAttemptsDelta();
            case "COUNTER_GANK_ATTEMPTS" -> row.counterGankAttemptsDelta();
            case "JUNGLE_CS_SIDE_GAP" ->
                    row.blueJungleCsDelta() - row.redJungleCsDelta();
            case "JUNGLE_EXPERIENCE_SIDE_GAP" ->
                    row.blueJungleExperienceDelta() - row.redJungleExperienceDelta();
            default -> throw new IllegalArgumentException("Unknown frozen B3 metric " + metric);
        };
    }

    private static String jobsCsv(List<HoldoutJob> jobs) {
        StringBuilder out = new StringBuilder();
        csv(out, "jobId", "fixtureId", "fixtureLane", "pairId", "blueTeamCode",
                "redTeamCode", "seriesGameNumber", "sampleLane", "seedIndex", "seed",
                "profileIndex", "profileId");
        jobs.forEach(job -> csv(out, job.jobId(), job.fixtureId(), job.fixtureLane(),
                job.pairId(), job.blueTeamCode(), job.redTeamCode(), job.seriesGameNumber(),
                job.sampleLane(), job.seedIndex(), job.seed(), job.profileIndex(),
                job.profileId()));
        return out.toString();
    }

    private static String fixedDraftsCsv(List<FixtureCheckpoint> checkpoints) {
        StringBuilder out = new StringBuilder();
        csv(out, "fixtureId", "fixtureLane", "pairId", "blueTeamCode", "redTeamCode",
                "seriesGameNumber", "productionOrchestrationCount", "rosterIdentityHash",
                "seriesHistoryBeforeHash", "draftDecisionHash", "finalDraftHash",
                "finalAssignmentHash", "canonicalAssignments");
        checkpoints.forEach(checkpoint -> {
            var row = checkpoint.fixedDraft();
            csv(out, row.fixtureId(), row.fixtureLane(), row.pairId(), row.blueTeamCode(),
                    row.redTeamCode(), row.seriesGameNumber(),
                    row.productionOrchestrationCount(), row.rosterIdentityHash(),
                    row.seriesHistoryBeforeHash(), row.draftDecisionHash(),
                    row.finalDraftHash(), row.finalAssignmentHash(),
                    row.canonicalAssignments());
        });
        return out.toString();
    }

    private static String replaysCsv(List<FixtureCheckpoint> checkpoints) {
        StringBuilder out = new StringBuilder();
        csv(out, "fixtureId", "seedIndex", "seed", "profileId",
                "replayProvenanceHash", "timelineHash", "structuredDiagnosticsHash",
                "randomDrawCount", "randomTraceHash", "fullStructuredDiagnosticsExact",
                "exact");
        checkpoints.forEach(checkpoint -> {
            var row = checkpoint.determinismReplay();
            csv(out, row.fixtureId(), row.seedIndex(), row.seed(), row.profileId(),
                    row.replayProvenanceHash(), row.timelineHash(),
                    row.structuredDiagnosticsHash(), row.randomDrawCount(),
                    row.randomTraceHash(), row.fullStructuredDiagnosticsExact(), row.exact());
        });
        return out.toString();
    }

    private static String matchesCsv(List<MatchRow> rows) {
        StringBuilder out = new StringBuilder();
        csv(out, "jobId", "fixtureId", "fixtureLane", "pairId", "seedIndex", "seed",
                "profileId", "configurationHash", "replayProvenanceHash", "timelineHash",
                "structuredDiagnosticsHash", "randomDrawCount", "randomTraceHash",
                "winnerSide", "endReason", "durationSeconds", "blueKills", "redKills",
                "blueGold", "redGold", "blueDragons", "redDragons", "blueTowers",
                "redTowers", "blueJungleCs", "blueJungleGold", "blueJungleXp",
                "redJungleCs", "redJungleGold", "redJungleXp", "blueSupportCs",
                "redSupportCs", "jungleEconomyAwardedCs", "jungleEconomyAwardedGold",
                "jungleEconomyAwardedExperience", "tempoGankReadyObservations",
                "tempoGankConsumptions", "tempoCounterGankReadyObservations",
                "tempoCounterGankConsumptions", "jungleGankAttempts",
                "counterGankAttempts", "integrityErrorCount", "integrityClean");
        rows.forEach(row -> csv(out, row.jobId(), row.fixtureId(), row.fixtureLane(),
                row.pairId(), row.seedIndex(), row.seed(), row.profileId(),
                row.configurationHash(), row.replayProvenanceHash(), row.timelineHash(),
                row.structuredDiagnosticsHash(), row.randomDrawCount(), row.randomTraceHash(),
                row.winnerSide(), row.endReason(), row.durationSeconds(), row.blueKills(),
                row.redKills(), row.blueGold(), row.redGold(), row.blueDragons(),
                row.redDragons(), row.blueTowers(), row.redTowers(),
                row.blueJungleFinal().cs(), row.blueJungleFinal().gold(),
                row.blueJungleFinal().totalExperience(), row.redJungleFinal().cs(),
                row.redJungleFinal().gold(), row.redJungleFinal().totalExperience(),
                row.blueSupportCs(), row.redSupportCs(), row.jungleEconomyAwardedCs(),
                row.jungleEconomyAwardedGold(), row.jungleEconomyAwardedExperience(),
                row.tempoGankReadyObservations(), row.tempoGankConsumptions(),
                row.tempoCounterGankReadyObservations(),
                row.tempoCounterGankConsumptions(), row.jungleGankAttempts(),
                row.counterGankAttempts(), row.integrityErrorCount(), row.integrityClean()));
        return out.toString();
    }

    private static String jungleObservationsCsv(List<MatchRow> rows) {
        StringBuilder out = new StringBuilder();
        csv(out, "jobId", "checkpointKind", "requestedTimeSeconds",
                "actualTimeSeconds", "side", "playerId", "championId", "kills",
                "deaths", "assists", "cs", "gold", "totalExperience", "level",
                "itemStage", "alive", "canFarm");
        rows.forEach(row -> row.jungleObservations().forEach(value -> csv(out,
                row.jobId(), value.checkpointKind(), value.requestedTimeSeconds(),
                value.actualTimeSeconds(), value.side(), value.playerId(), value.championId(),
                value.kills(), value.deaths(), value.assists(), value.cs(), value.gold(),
                value.totalExperience(), value.level(), value.itemStage(), value.alive(),
                value.canFarm())));
        return out.toString();
    }

    private static String playerStatesCsv(List<MatchRow> rows) {
        StringBuilder out = new StringBuilder();
        csv(out, "jobId", "side", "position", "playerId", "championId", "kills",
                "deaths", "assists", "cs", "gold", "totalExperience", "level",
                "itemStage", "alive", "canFarm", "respawnAtSeconds",
                "farmResumeAtSeconds");
        rows.forEach(row -> row.finalPlayerStates().forEach(value -> csv(out,
                row.jobId(), value.side(), value.position(), value.playerId(),
                value.championId(), value.kills(), value.deaths(), value.assists(), value.cs(),
                value.gold(), value.totalExperience(), value.level(), value.itemStage(),
                value.alive(), value.canFarm(), value.respawnAtSeconds(),
                value.farmResumeAtSeconds())));
        return out.toString();
    }

    private static String marginalsCsv(List<MarginalRow> rows) {
        StringBuilder out = new StringBuilder();
        csv(out, "comparisonId", "fixtureId", "fixtureLane", "pairId", "seedIndex",
                "seed", "fromProfile", "toProfile", "fromWinner", "toWinner",
                "winnerFlipped", "durationDelta", "blueGoldEdgeDelta",
                "blueJungleCsDelta", "redJungleCsDelta", "blueJungleExperienceDelta",
                "redJungleExperienceDelta", "jungleGankAttemptsDelta",
                "counterGankAttemptsDelta");
        rows.forEach(row -> csv(out, row.comparisonId(), row.fixtureId(), row.fixtureLane(),
                row.pairId(), row.seedIndex(), row.seed(), row.fromProfile(), row.toProfile(),
                row.fromWinner(), row.toWinner(), row.winnerFlipped(), row.durationDelta(),
                row.blueGoldEdgeDelta(), row.blueJungleCsDelta(), row.redJungleCsDelta(),
                row.blueJungleExperienceDelta(), row.redJungleExperienceDelta(),
                row.jungleGankAttemptsDelta(), row.counterGankAttemptsDelta()));
        return out.toString();
    }

    private static String marginalSummaryCsv(List<MarginalRow> rows) {
        record Key(Phase13GB1AuditSchedule.FixtureLane lane, String comparison)
                implements Comparable<Key> {
            public int compareTo(Key other) {
                int value = lane.compareTo(other.lane);
                return value != 0 ? value : comparison.compareTo(other.comparison);
            }
        }
        Map<Key, List<MarginalRow>> groups = rows.stream().collect(Collectors.groupingBy(
                row -> new Key(row.fixtureLane(), row.comparisonId()),
                TreeMap::new, Collectors.toList()));
        StringBuilder out = new StringBuilder();
        csv(out, "fixtureLane", "comparisonId", "count", "winnerFlipCount",
                "winnerFlipRate", "blueToRedFlipCount", "redToBlueFlipCount",
                "meanDurationDelta", "meanBlueGoldEdgeDelta", "meanBlueJungleCsDelta",
                "meanRedJungleCsDelta", "meanBlueJungleExperienceDelta",
                "meanRedJungleExperienceDelta", "meanJungleGankAttemptsDelta",
                "meanCounterGankAttemptsDelta");
        groups.forEach((key, values) -> csv(out, key.lane(), key.comparison(), values.size(),
                values.stream().filter(MarginalRow::winnerFlipped).count(),
                mean(values, row -> row.winnerFlipped() ? 1 : 0),
                values.stream().filter(row -> row.fromWinner() == TeamSide.BLUE
                        && row.toWinner() == TeamSide.RED).count(),
                values.stream().filter(row -> row.fromWinner() == TeamSide.RED
                        && row.toWinner() == TeamSide.BLUE).count(),
                mean(values, MarginalRow::durationDelta),
                mean(values, MarginalRow::blueGoldEdgeDelta),
                mean(values, MarginalRow::blueJungleCsDelta),
                mean(values, MarginalRow::redJungleCsDelta),
                mean(values, MarginalRow::blueJungleExperienceDelta),
                mean(values, MarginalRow::redJungleExperienceDelta),
                mean(values, MarginalRow::jungleGankAttemptsDelta),
                mean(values, MarginalRow::counterGankAttemptsDelta)));
        return out.toString();
    }

    private static String profileSummaryCsv(List<MatchRow> rows) {
        record Key(Phase13GB1AuditSchedule.FixtureLane lane,
                   SimulationRuntimeProfileId profile) implements Comparable<Key> {
            public int compareTo(Key other) {
                int value = lane.compareTo(other.lane);
                return value != 0 ? value : profile.compareTo(other.profile);
            }
        }
        Map<Key, List<MatchRow>> groups = rows.stream().collect(Collectors.groupingBy(
                row -> new Key(row.fixtureLane(), row.profileId()),
                TreeMap::new, Collectors.toList()));
        StringBuilder out = new StringBuilder();
        csv(out, "fixtureLane", "profileId", "matchCount", "blueWinRate",
                "timeoutCount", "meanDuration", "meanBlueJungleCs", "meanRedJungleCs",
                "meanBlueJungleXp", "meanRedJungleXp", "awardedCs", "awardedGold",
                "awardedXp", "gankReady", "gankConsumed", "counterReady",
                "counterConsumed", "gankAttempts", "counterAttempts", "integrityErrors");
        groups.forEach((key, values) -> csv(out, key.lane(), key.profile(), values.size(),
                mean(values, row -> row.winnerSide() == TeamSide.BLUE ? 1 : 0),
                values.stream().filter(row -> row.endReason()
                        == GameEndReason.SIMULATION_TIMEOUT).count(),
                mean(values, MatchRow::durationSeconds),
                mean(values, row -> row.blueJungleFinal().cs()),
                mean(values, row -> row.redJungleFinal().cs()),
                mean(values, row -> row.blueJungleFinal().totalExperience()),
                mean(values, row -> row.redJungleFinal().totalExperience()),
                values.stream().mapToLong(MatchRow::jungleEconomyAwardedCs).sum(),
                values.stream().mapToLong(MatchRow::jungleEconomyAwardedGold).sum(),
                values.stream().mapToLong(MatchRow::jungleEconomyAwardedExperience).sum(),
                values.stream().mapToLong(MatchRow::tempoGankReadyObservations).sum(),
                values.stream().mapToLong(MatchRow::tempoGankConsumptions).sum(),
                values.stream().mapToLong(MatchRow::tempoCounterGankReadyObservations).sum(),
                values.stream().mapToLong(MatchRow::tempoCounterGankConsumptions).sum(),
                values.stream().mapToLong(MatchRow::jungleGankAttempts).sum(),
                values.stream().mapToLong(MatchRow::counterGankAttempts).sum(),
                values.stream().mapToLong(MatchRow::integrityErrorCount).sum()));
        return out.toString();
    }

    private static String teamSideSummaryCsv(List<MatchRow> rows) {
        record Key(Phase13GB1AuditSchedule.FixtureLane lane,
                   SimulationRuntimeProfileId profile, String team, TeamSide side)
                implements Comparable<Key> {
            public int compareTo(Key other) {
                int value = lane.compareTo(other.lane);
                if (value != 0) return value;
                value = profile.compareTo(other.profile);
                if (value != 0) return value;
                value = team.compareTo(other.team);
                return value != 0 ? value : side.compareTo(other.side);
            }
        }
        record Observation(Key key, boolean won, int gold, int kills, int jungleCs,
                           int jungleXp) {}
        ArrayList<Observation> values = new ArrayList<>();
        rows.forEach(row -> {
            values.add(new Observation(new Key(row.fixtureLane(), row.profileId(),
                    row.blueTeamCode(), TeamSide.BLUE), row.winnerSide() == TeamSide.BLUE,
                    row.blueGold(), row.blueKills(), row.blueJungleFinal().cs(),
                    row.blueJungleFinal().totalExperience()));
            values.add(new Observation(new Key(row.fixtureLane(), row.profileId(),
                    row.redTeamCode(), TeamSide.RED), row.winnerSide() == TeamSide.RED,
                    row.redGold(), row.redKills(), row.redJungleFinal().cs(),
                    row.redJungleFinal().totalExperience()));
        });
        Map<Key, List<Observation>> groups = values.stream().collect(Collectors.groupingBy(
                Observation::key, TreeMap::new, Collectors.toList()));
        StringBuilder out = new StringBuilder();
        csv(out, "fixtureLane", "profileId", "teamCode", "side", "matchCount",
                "winRate", "averageGold", "averageKills", "averageJungleCs",
                "averageJungleExperience");
        groups.forEach((key, group) -> csv(out, key.lane(), key.profile(), key.team(),
                key.side(), group.size(), mean(group, value -> value.won() ? 1 : 0),
                mean(group, Observation::gold), mean(group, Observation::kills),
                mean(group, Observation::jungleCs), mean(group, Observation::jungleXp)));
        return out.toString();
    }

    private static String junglerChampionSummaryCsv(List<MatchRow> rows) {
        record Key(Phase13GB1AuditSchedule.FixtureLane lane,
                   SimulationRuntimeProfileId profile, String player, String champion,
                   TeamSide side) implements Comparable<Key> {
            public int compareTo(Key other) {
                int value = lane.compareTo(other.lane);
                if (value != 0) return value;
                value = profile.compareTo(other.profile);
                if (value != 0) return value;
                value = player.compareTo(other.player);
                if (value != 0) return value;
                value = champion.compareTo(other.champion);
                return value != 0 ? value : side.compareTo(other.side);
            }
        }
        record Observation(Key key, boolean won, int cs, int gold, int xp) {}
        ArrayList<Observation> values = new ArrayList<>();
        rows.forEach(row -> {
            var blue = row.blueJungleFinal();
            var red = row.redJungleFinal();
            values.add(new Observation(new Key(row.fixtureLane(), row.profileId(),
                    blue.playerId(), blue.championId(), TeamSide.BLUE),
                    row.winnerSide() == TeamSide.BLUE, blue.cs(), blue.gold(),
                    blue.totalExperience()));
            values.add(new Observation(new Key(row.fixtureLane(), row.profileId(),
                    red.playerId(), red.championId(), TeamSide.RED),
                    row.winnerSide() == TeamSide.RED, red.cs(), red.gold(),
                    red.totalExperience()));
        });
        Map<Key, List<Observation>> groups = values.stream().collect(Collectors.groupingBy(
                Observation::key, TreeMap::new, Collectors.toList()));
        StringBuilder out = new StringBuilder();
        csv(out, "fixtureLane", "profileId", "playerId", "championId", "side",
                "matchCount", "winRate", "averageCs", "averageGold",
                "averageExperience");
        groups.forEach((key, group) -> csv(out, key.lane(), key.profile(), key.player(),
                key.champion(), key.side(), group.size(),
                mean(group, value -> value.won() ? 1 : 0), mean(group, Observation::cs),
                mean(group, Observation::gold), mean(group, Observation::xp)));
        return out.toString();
    }

    private static <T> double mean(
            List<T> values,
            java.util.function.ToDoubleFunction<T> function
    ) {
        return values.stream().mapToDouble(function).average().orElse(Double.NaN);
    }

    private static void writeJson(ObjectMapper mapper, Path path, Object value)
            throws IOException {
        byte[] bytes = mapper.writeValueAsBytes(value);
        byte[] result = java.util.Arrays.copyOf(bytes, bytes.length + 1);
        result[bytes.length] = '\n';
        Files.write(path, result);
    }

    private static void writeJsonLines(ObjectMapper mapper, Path path, List<MatchRow> rows)
            throws IOException {
        ObjectMapper compact = mapper.copy().disable(SerializationFeature.INDENT_OUTPUT);
        try (BufferedWriter writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
            for (MatchRow row : rows) {
                writer.write(compact.writeValueAsString(row));
                writer.newLine();
            }
        }
    }

    private static void writeManifest(Path output) throws IOException {
        StringBuilder manifest = new StringBuilder();
        for (String file : HASHED_FILES) {
            manifest.append(Phase13GB3CheckpointStore.sha256(
                    Files.readAllBytes(output.resolve(file))))
                    .append("  ").append(file).append('\n');
        }
        Phase13GB3CheckpointStore.writeUtf8(output.resolve(SHA_FILE), manifest.toString());
    }

    private static void csv(StringBuilder out, Object... values) {
        for (int index = 0; index < values.length; index++) {
            if (index > 0) out.append(',');
            String value = values[index] == null ? "" : values[index].toString();
            if (value.indexOf(',') >= 0 || value.indexOf('"') >= 0
                    || value.indexOf('\n') >= 0 || value.indexOf('\r') >= 0) {
                out.append('"').append(value.replace("\"", "\"\"")).append('"');
            } else out.append(value);
        }
        out.append('\n');
    }

    public record MarginalRow(
            String comparisonId,
            String fixtureId,
            Phase13GB1AuditSchedule.FixtureLane fixtureLane,
            String pairId,
            int seedIndex,
            long seed,
            SimulationRuntimeProfileId fromProfile,
            SimulationRuntimeProfileId toProfile,
            TeamSide fromWinner,
            TeamSide toWinner,
            boolean winnerFlipped,
            int durationDelta,
            int blueGoldEdgeDelta,
            int blueJungleCsDelta,
            int redJungleCsDelta,
            int blueJungleExperienceDelta,
            int redJungleExperienceDelta,
            int jungleGankAttemptsDelta,
            int counterGankAttemptsDelta
    ) {
        static MarginalRow from(String comparisonId, MatchRow from, MatchRow to) {
            if (from == null || to == null
                    || !from.fixtureId().equals(to.fixtureId())
                    || from.seed() != to.seed() || from.seedIndex() != to.seedIndex()) {
                throw new IllegalStateException("Unpaired B3 marginal rows");
            }
            return new MarginalRow(
                    comparisonId, from.fixtureId(), from.fixtureLane(), from.pairId(),
                    from.seedIndex(), from.seed(), from.profileId(), to.profileId(),
                    from.winnerSide(), to.winnerSide(),
                    !Objects.equals(from.winnerSide(), to.winnerSide()),
                    to.durationSeconds() - from.durationSeconds(),
                    (to.blueGold() - to.redGold()) - (from.blueGold() - from.redGold()),
                    to.blueJungleFinal().cs() - from.blueJungleFinal().cs(),
                    to.redJungleFinal().cs() - from.redJungleFinal().cs(),
                    to.blueJungleFinal().totalExperience()
                            - from.blueJungleFinal().totalExperience(),
                    to.redJungleFinal().totalExperience()
                            - from.redJungleFinal().totalExperience(),
                    to.jungleGankAttempts() - from.jungleGankAttempts(),
                    to.counterGankAttempts() - from.counterGankAttempts());
        }
    }

    public record NumericGateResult(
            String gateId,
            String candidate,
            String comparisonId,
            String fixtureLane,
            String metric,
            int actualCount,
            double actual,
            double lowerInclusive,
            double upperInclusive,
            boolean passed
    ) {
    }

    public record ExactGateResult(
            String gateId,
            String candidate,
            String metric,
            String comparator,
            String expected,
            String actual,
            boolean passed
    ) {
    }

    public record GateEvaluation(
            String schemaVersion,
            String acceptanceGateIdentityHash,
            String boundaryPolicy,
            List<NumericGateResult> numericResults,
            List<ExactGateResult> exactResults
    ) {
    }

    private record ExactActual(String value, boolean passed) {
    }

    private record Validation(
            List<HoldoutJob> jobs,
            List<MatchRow> rows,
            List<MarginalRow> marginals,
            Map<String, Object> integrity,
            GateEvaluation gates,
            Map<String, Object> review
    ) {
    }

    public record ArtifactSet(
            Path outputDirectory,
            String evidenceStatus,
            String economyCandidateVerdict,
            String tempoCandidateVerdict,
            int holdoutMatchCount,
            String reviewSha256,
            String shaManifestSha256
    ) {
    }

    record SyntheticArtifactSet(
            Path reportPath,
            String status,
            boolean officialHoldoutEvidence
    ) {
    }
}
