package com.lolfm.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lolfm.application.MatchEngineV9RequalificationRunner.ArtifactResult;
import com.lolfm.application.MatchEngineV9RequalificationRunner.ExactIntegrity;
import com.lolfm.application.MatchEngineV9RequalificationRunner.FixtureCheckpoint;
import com.lolfm.application.MatchEngineV9RequalificationRunner.Marginal;
import com.lolfm.application.MatchEngineV9RequalificationRunner.MatchRow;
import com.lolfm.application.MatchEngineV9RequalificationRunner.Sensitivity;
import com.lolfm.player.PlayerIdentityCatalog;
import com.lolfm.simulator.SimulationRuntimeProfileId;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.ToDoubleFunction;
import java.util.stream.Collectors;

/** Deterministic consumer of authenticated V9 fixture checkpoints. */
public final class MatchEngineV9RequalificationArtifactWriter {
    private MatchEngineV9RequalificationArtifactWriter() {
    }

    public static ArtifactResult write(
            ObjectMapper canonical,
            Path sourceRoot,
            Path output,
            MatchEngineV9RequalificationRunner.Binding binding,
            List<FixtureCheckpoint> calibration,
            List<FixtureCheckpoint> holdout,
            SimulationProvenanceService provenance,
            PlayerIdentityCatalog identities
    ) throws Exception {
        if (Files.exists(output)) throw new IllegalStateException("Candidate output already exists: " + output);
        Files.createDirectories(output);
        List<MatchRow> calibrationRows = rows(calibration);
        List<MatchRow> holdoutRows = rows(holdout);
        if (calibrationRows.size() != 2_400 || holdoutRows.size() != 1_200) {
            throw new IllegalStateException("Official V9 row coverage differs from 3,600 contract");
        }
        requirePairing(calibrationRows, 8);
        requirePairing(holdoutRows, 4);

        Sensitivity calibrationSensitivity = sensitivity(calibrationRows);
        Sensitivity holdoutSensitivity = sensitivity(holdoutRows);
        ExactIntegrity integrity = exactIntegrity(concat(calibrationRows, holdoutRows));
        ProfileDecision decision = decide(holdoutRows, holdoutSensitivity, integrity);
        ArtifactResult artifactResult = new ArtifactResult(
                "MATCH_ENGINE_V9_REQUALIFICATION_ARTIFACT_RESULT_V1",
                calibrationRows.size(), holdoutRows.size(), decision.baselineStatus(),
                decision.matchupStatus(), decision.compositionStatus(), decision.recommendation(),
                decision.reason(), calibrationSensitivity, holdoutSensitivity, integrity,
                "SEE_SHA256SUMS_TXT_RAW_SHA256");

        copy(sourceRoot.resolve("contract.json"), output.resolve("contract.json"));
        copy(sourceRoot.resolve("source-resource-runtime-identity.json"),
                output.resolve("source-resource-runtime-identity.json"));
        copy(sourceRoot.resolve("frozen-schedule.json"), output.resolve("frozen-schedule.json"));
        copy(sourceRoot.resolve("frozen-schedule.csv"), output.resolve("frozen-schedule.csv"));
        copy(sourceRoot.resolve("seed-overlap-audit.json"), output.resolve("seed-overlap-audit.json"));
        copy(sourceRoot.resolve("calibration-review.json"), output.resolve("calibration-review.json"));

        write(output.resolve("fixed-draft-final-assignment.csv"), fixedDraftCsv(calibration));
        write(output.resolve("per-match-results.jsonl"), jsonl(canonical,
                concat(calibrationRows, holdoutRows)));
        write(output.resolve("paired-marginals.csv"), marginalsCsv(
                calibrationSensitivity, holdoutSensitivity));
        writeJson(canonical, output.resolve("baseline-distribution-summary.json"),
                baselineSummary(holdoutRows));
        writeJson(canonical, output.resolve("matchup-application-local-effect-summary.json"),
                matchupSummary(holdoutRows));
        writeJson(canonical, output.resolve("composition-context-local-effect-summary.json"),
                compositionSummary(holdoutRows));
        writeJson(canonical, output.resolve("v9-structure-siege-base-defense-summary.json"),
                structureSummary(holdoutRows));
        writeJson(canonical, output.resolve("full-domain-integrity.json"), integrity);
        writeJson(canonical, output.resolve("frozen-holdout-review.json"), Map.of(
                "schemaVersion", "MATCH_ENGINE_V9_REQUALIFICATION_FROZEN_HOLDOUT_REVIEW_V1",
                "contractHash", binding.contractHash(),
                "fixtureCount", 100,
                "seedsPerFixture", 4,
                "profileCount", 3,
                "matchRowCount", holdoutRows.size(),
                "sensitivity", holdoutSensitivity,
                "exactIntegrity", integrity,
                "decision", decision));
        writeJson(canonical, output.resolve("production-profile-recommendation.json"), Map.of(
                "schemaVersion", "MATCH_ENGINE_V9_PRODUCTION_PROFILE_RECOMMENDATION_V1",
                "contractHash", binding.contractHash(),
                "engineImplementationVersion", SimulationProvenanceService.ENGINE_IMPLEMENTATION_VERSION,
                "currentProductionProfile", MatchEngineV1Policy.authoritative().retainedRuntimeProfileId(),
                "recommendation", decision.recommendation(),
                "baselineStatus", decision.baselineStatus(),
                "matchupStatus", decision.matchupStatus(),
                "compositionStatus", decision.compositionStatus(),
                "reason", decision.reason(),
                "productionChanged", false));
        write(output.resolve("analysis.md"), analysis(
                artifactResult, calibrationRows, holdoutRows, identities));
        writeJson(canonical, output.resolve("artifact-result.json"), artifactResult);
        write(output.resolve("SHA256SUMS.txt"), shaManifest(output));
        return artifactResult;
    }

    static Sensitivity sensitivity(List<MatchRow> rows) {
        return new Sensitivity(
                marginal(rows, SimulationRuntimeProfileId.BASELINE_V1,
                        SimulationRuntimeProfileId.MATCHUP_ONLY_CANDIDATE_V1),
                marginal(rows, SimulationRuntimeProfileId.MATCHUP_ONLY_CANDIDATE_V1,
                        SimulationRuntimeProfileId.FULL_SYSTEM_CANDIDATE_V1));
    }

    static ExactIntegrity exactIntegrity(List<MatchRow> rows) {
        long timeout = rows.stream().filter(value -> value.endReason()
                == com.lolfm.simulator.GameEndReason.SIMULATION_TIMEOUT).count();
        long errors = rows.stream().mapToLong(MatchRow::integrityErrorCount).sum();
        long invalid = rows.stream().mapToLong(MatchRow::invalidStructureState).sum();
        long nexus = rows.stream().mapToLong(MatchRow::nexusDestroyedWithTurretAlive).sum();
        long post = rows.stream().mapToLong(MatchRow::postFinishMutationOrEvent).sum();
        long support = rows.stream().mapToLong(MatchRow::supportFarmCsErrors).sum();
        long matchupRandom = rows.stream().mapToLong(MatchRow::matchupDirectRandomCalls).sum();
        long compositionRandom = rows.stream().mapToLong(MatchRow::compositionDirectRandomCalls).sum();
        long compositionDraws = rows.stream().mapToLong(MatchRow::compositionRandomDraws).sum();
        boolean pass = timeout == 0 && errors == 0 && invalid == 0 && nexus == 0
                && post == 0 && support == 0 && matchupRandom == 0
                && compositionRandom == 0 && compositionDraws == 0;
        return new ExactIntegrity(rows.size(), timeout, errors, invalid, nexus, post,
                support, matchupRandom, compositionRandom, compositionDraws, pass);
    }

    private static Marginal marginal(
            List<MatchRow> rows,
            SimulationRuntimeProfileId beforeProfile,
            SimulationRuntimeProfileId afterProfile
    ) {
        Map<String, MatchRow> before = byPair(rows, beforeProfile);
        Map<String, MatchRow> after = byPair(rows, afterProfile);
        if (!before.keySet().equals(after.keySet())) throw new IllegalStateException("Paired row keys differ");
        int count = before.size();
        long beforeBlueWins = before.values().stream().filter(value -> value.winnerSide() == com.lolfm.simulator.TeamSide.BLUE).count();
        long afterBlueWins = after.values().stream().filter(value -> value.winnerSide() == com.lolfm.simulator.TeamSide.BLUE).count();
        long winnerChanged = 0;
        long objectiveChanged = 0;
        long structureChanged = 0;
        double durationDelta = 0.0;
        for (String key : before.keySet()) {
            MatchRow a = before.get(key);
            MatchRow b = after.get(key);
            if (a.winnerSide() != b.winnerSide()) winnerChanged++;
            if (!a.objectiveSignature().equals(b.objectiveSignature())) objectiveChanged++;
            if (!a.structureSignature().equals(b.structureSignature())) structureChanged++;
            durationDelta += b.durationSeconds() - a.durationSeconds();
        }
        double blueDelta = 100.0 * (afterBlueWins - beforeBlueWins) / count;
        double winnerRate = 100.0 * winnerChanged / count;
        double objectiveRate = 100.0 * objectiveChanged / count;
        double structureRate = 100.0 * structureChanged / count;
        double meanDelta = durationDelta / count;
        double p95Delta = percentile(after.values().stream().mapToDouble(MatchRow::durationSeconds).toArray(), 0.95)
                - percentile(before.values().stream().mapToDouble(MatchRow::durationSeconds).toArray(), 0.95);
        var gates = MatchEngineV9RequalificationContract.GATES;
        boolean pass = Math.abs(blueDelta) <= gates.absoluteBlueWinRateDeltaPercentagePoints()
                && winnerRate <= gates.pairedWinnerChangedRatePercent()
                && objectiveRate <= gates.objectiveChangedRatePercent()
                && structureRate <= gates.structureChangedRatePercent()
                && Math.abs(meanDelta) <= gates.absoluteMeanDurationDeltaSeconds()
                && Math.abs(p95Delta) <= gates.absoluteP95DurationDeltaSeconds();
        return new Marginal(count, blueDelta, winnerRate, objectiveRate,
                structureRate, meanDelta, p95Delta, pass);
    }

    private static ProfileDecision decide(
            List<MatchRow> holdout, Sensitivity sensitivity, ExactIntegrity allIntegrity) {
        List<MatchRow> baseline = profile(holdout, SimulationRuntimeProfileId.BASELINE_V1);
        List<MatchRow> matchup = profile(holdout, SimulationRuntimeProfileId.MATCHUP_ONLY_CANDIDATE_V1);
        List<MatchRow> full = profile(holdout, SimulationRuntimeProfileId.FULL_SYSTEM_CANDIDATE_V1);
        boolean baselineExact = exactIntegrity(baseline).pass()
                && baseline.stream().allMatch(value -> value.matchupApplications() == 0
                        && value.compositionApplications() == 0
                        && value.compositionNonZeroModifiers() == 0);
        if (!baselineExact) {
            return new ProfileDecision("V9_BASELINE_BLOCKED", "NOT_EVALUATED",
                    "NOT_EVALUATED", "NO_PRODUCTION_RECOMMENDATION_BLOCKED",
                    "BASELINE_V1 exact/integrity/stability gate failed");
        }
        boolean matchupEligible = exactIntegrity(matchup).pass()
                && sensitivity.matchupMinusBaseline().macroSafetyPass()
                && matchup.stream().mapToLong(MatchRow::matchupApplications).sum() > 0
                && matchup.stream().allMatch(value -> value.compositionApplications() == 0
                        && value.compositionNonZeroModifiers() == 0
                        && value.matchupDirectRandomCalls() == 0);
        boolean compositionEligible = matchupEligible && exactIntegrity(full).pass()
                && sensitivity.fullMinusMatchup().macroSafetyPass()
                && full.stream().mapToLong(MatchRow::compositionApplications).sum() > 0
                && full.stream().mapToLong(MatchRow::localCauseViolations).sum() == 0
                && full.stream().mapToLong(MatchRow::compositionDirectRandomCalls).sum() == 0
                && full.stream().mapToLong(MatchRow::compositionRandomDraws).sum() == 0;
        if (compositionEligible) {
            return new ProfileDecision("V9_BASELINE_STABLE", "MATCHUP_V9_ELIGIBLE",
                    "COMPOSITION_V9_ELIGIBLE", "RECOMMEND_FULL_SYSTEM_CANDIDATE_V1",
                    "Baseline exact gates and both frozen paired marginal gates passed");
        }
        if (matchupEligible) {
            return new ProfileDecision("V9_BASELINE_STABLE", "MATCHUP_V9_ELIGIBLE",
                    "COMPOSITION_V9_NOT_ELIGIBLE", "RECOMMEND_MATCHUP_ONLY_CANDIDATE_V1",
                    "Matchup passed; FULL_MINUS_MATCHUP composition gate did not pass");
        }
        return new ProfileDecision("V9_BASELINE_STABLE", "MATCHUP_V9_NOT_ELIGIBLE",
                "COMPOSITION_V9_NOT_ELIGIBLE", "RECOMMEND_BASELINE_V1",
                "Matchup candidate did not pass the frozen exact/macro eligibility gates");
    }

    private static Map<String, Object> baselineSummary(List<MatchRow> holdout) {
        List<MatchRow> values = profile(holdout, SimulationRuntimeProfileId.BASELINE_V1);
        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        result.put("schemaVersion", "MATCH_ENGINE_V9_BASELINE_DISTRIBUTION_SUMMARY_V1");
        result.put("matchCount", values.size());
        result.put("blueWinRatePercent", 100.0 * values.stream()
                .filter(value -> value.winnerSide() == com.lolfm.simulator.TeamSide.BLUE).count() / values.size());
        result.put("meanDurationSeconds", mean(values, MatchRow::durationSeconds));
        result.put("p95DurationSeconds", percentile(values.stream().mapToDouble(MatchRow::durationSeconds).toArray(), 0.95));
        result.put("meanFirstTowerSeconds", mean(values.stream()
                .filter(value -> value.firstTowerSeconds() >= 0).toList(), MatchRow::firstTowerSeconds));
        result.put("firstTowerSourceDistribution", distribution(values, MatchRow::firstTowerSource));
        result.put("firstTowerLaneDistribution", distribution(values, MatchRow::firstTowerLane));
        result.put("structureDamageEvents", values.stream().mapToLong(MatchRow::structureDamageEvents).sum());
        result.put("structureDestroyedEvents", values.stream().mapToLong(MatchRow::structureDestroyedEvents).sum());
        result.put("siegeStarted", values.stream().mapToLong(MatchRow::siegeStarted).sum());
        result.put("siegeStopped", values.stream().mapToLong(MatchRow::siegeStopped).sum());
        return result;
    }

    private static Map<String, Object> matchupSummary(List<MatchRow> holdout) {
        List<MatchRow> off = profile(holdout, SimulationRuntimeProfileId.BASELINE_V1);
        List<MatchRow> on = profile(holdout, SimulationRuntimeProfileId.MATCHUP_ONLY_CANDIDATE_V1);
        return Map.of(
                "schemaVersion", "MATCH_ENGINE_V9_MATCHUP_APPLICATION_LOCAL_EFFECT_SUMMARY_V1",
                "mode", "GEOMETRIC_V2",
                "offProfileApplications", off.stream().mapToLong(MatchRow::matchupApplications).sum(),
                "candidateApplications", on.stream().mapToLong(MatchRow::matchupApplications).sum(),
                "candidateEdgeSum", on.stream().mapToDouble(MatchRow::matchupEdgeSum).sum(),
                "directRandomCalls", on.stream().mapToLong(MatchRow::matchupDirectRandomCalls).sum(),
                "reachabilityPass", on.stream().mapToLong(MatchRow::matchupApplications).sum() > 0);
    }

    private static Map<String, Object> compositionSummary(List<MatchRow> holdout) {
        List<MatchRow> off = holdout.stream().filter(value -> value.profileId()
                != SimulationRuntimeProfileId.FULL_SYSTEM_CANDIDATE_V1).toList();
        List<MatchRow> on = profile(holdout, SimulationRuntimeProfileId.FULL_SYSTEM_CANDIDATE_V1);
        TreeMap<String, Long> contexts = new TreeMap<>();
        for (MatchRow row : on) row.compositionApplicationsByContext().forEach(
                (key, value) -> contexts.merge(key, value.longValue(), Long::sum));
        return Map.ofEntries(
                Map.entry("schemaVersion", "MATCH_ENGINE_V9_COMPOSITION_CONTEXT_LOCAL_EFFECT_SUMMARY_V1"),
                Map.entry("mode", "PRODUCTION_V2"),
                Map.entry("offAndMatchupOnlyApplications", off.stream().mapToLong(MatchRow::compositionApplications).sum()),
                Map.entry("candidateApplications", on.stream().mapToLong(MatchRow::compositionApplications).sum()),
                Map.entry("candidateNonZeroModifiers", on.stream().mapToLong(MatchRow::compositionNonZeroModifiers).sum()),
                Map.entry("applicationsByContext", contexts),
                Map.entry("localDecisionChanges", on.stream().mapToLong(MatchRow::compositionLocalChangeCount).sum()),
                Map.entry("localCauseViolations", on.stream().mapToLong(MatchRow::localCauseViolations).sum()),
                Map.entry("directRandomCalls", on.stream().mapToLong(MatchRow::compositionDirectRandomCalls).sum()),
                Map.entry("compositionRandomDraws", on.stream().mapToLong(MatchRow::compositionRandomDraws).sum()),
                Map.entry("reachabilityPass", on.stream().mapToLong(MatchRow::compositionApplications).sum() > 0));
    }

    private static Map<String, Object> structureSummary(List<MatchRow> holdout) {
        EnumMap<SimulationRuntimeProfileId, Object> profiles = new EnumMap<>(SimulationRuntimeProfileId.class);
        for (SimulationRuntimeProfileId profile : MatchEngineV9RequalificationContract.PROFILES) {
            List<MatchRow> values = profile(holdout, profile);
            TreeMap<String, Long> contexts = new TreeMap<>();
            for (MatchRow row : values) row.compositionApplicationsByContext().forEach(
                    (key, value) -> contexts.merge(key, value.longValue(), Long::sum));
            profiles.put(profile, Map.of(
                    "matchCount", values.size(),
                    "structureDamageEvents", values.stream().mapToLong(MatchRow::structureDamageEvents).sum(),
                    "structureDestroyedEvents", values.stream().mapToLong(MatchRow::structureDestroyedEvents).sum(),
                    "persistentSiegeStarted", values.stream().mapToLong(MatchRow::siegeStarted).sum(),
                    "persistentSiegeStopped", values.stream().mapToLong(MatchRow::siegeStopped).sum(),
                    "siegeCompositionApplications", contexts.getOrDefault("SIEGE", 0L),
                    "baseDefenseCompositionApplications", contexts.getOrDefault("BASE_DEFENSE", 0L),
                    "invalidStructureStates", values.stream().mapToLong(MatchRow::invalidStructureState).sum()));
        }
        return Map.of(
                "schemaVersion", "MATCH_ENGINE_V9_STRUCTURE_SIEGE_BASE_DEFENSE_INTERACTION_SUMMARY_V1",
                "profiles", profiles,
                "compositionDoesNotDirectlyMutateStructures", true,
                "evidenceBasis", "STRUCTURED_STRUCTURE_EVENTS_SNAPSHOTS_AND_COMPOSITION_CONTEXTS");
    }

    private static String fixedDraftCsv(List<FixtureCheckpoint> checkpoints) {
        StringBuilder value = new StringBuilder("fixture_id,series_game,roster_hash,draft_decision_hash,final_draft_hash,final_assignment_hash,blue_bans,red_bans,blue_picks,red_picks,blue_assignments,red_assignments,hard_fearless_exclusions_before_draft\n");
        checkpoints.stream().sorted(Comparator.comparingInt(FixtureCheckpoint::fixtureIndex))
                .map(FixtureCheckpoint::fixedDraft).forEach(row -> value
                        .append(csv(row.fixtureId())).append(',').append(row.seriesGameNumber()).append(',')
                        .append(row.rosterIdentityHash()).append(',').append(row.draftDecisionHash()).append(',')
                        .append(row.finalDraftHash()).append(',').append(row.finalAssignmentHash()).append(',')
                        .append(csv(String.join("|", row.blueBans()))).append(',')
                        .append(csv(String.join("|", row.redBans()))).append(',')
                        .append(csv(String.join("|", row.bluePicks()))).append(',')
                        .append(csv(String.join("|", row.redPicks()))).append(',')
                        .append(csv(String.join("|", row.blueAssignments()))).append(',')
                        .append(csv(String.join("|", row.redAssignments()))).append(',')
                        .append(csv(String.join("|", row.hardFearlessExclusionsBeforeDraft()))).append('\n'));
        return value.toString();
    }

    private static String marginalsCsv(Sensitivity calibration, Sensitivity holdout) {
        StringBuilder value = new StringBuilder("sample_lane,comparison,pair_count,blue_wr_delta_pp,winner_changed_pct,objective_changed_pct,structure_changed_pct,mean_duration_delta_seconds,p95_duration_delta_seconds,macro_safety_pass\n");
        appendMarginal(value, "CALIBRATION", "MATCHUP_MINUS_BASELINE", calibration.matchupMinusBaseline());
        appendMarginal(value, "CALIBRATION", "FULL_MINUS_MATCHUP", calibration.fullMinusMatchup());
        appendMarginal(value, "HOLDOUT", "MATCHUP_MINUS_BASELINE", holdout.matchupMinusBaseline());
        appendMarginal(value, "HOLDOUT", "FULL_MINUS_MATCHUP", holdout.fullMinusMatchup());
        return value.toString();
    }

    private static void appendMarginal(StringBuilder target, String lane, String comparison, Marginal value) {
        target.append(lane).append(',').append(comparison).append(',').append(value.pairCount()).append(',')
                .append(format(value.blueWinRateDeltaPercentagePoints())).append(',')
                .append(format(value.pairedWinnerChangedRatePercent())).append(',')
                .append(format(value.objectiveChangedRatePercent())).append(',')
                .append(format(value.structureChangedRatePercent())).append(',')
                .append(format(value.meanDurationDeltaSeconds())).append(',')
                .append(format(value.p95DurationDeltaSeconds())).append(',')
                .append(value.macroSafetyPass()).append('\n');
    }

    private static String analysis(
            ArtifactResult result, List<MatchRow> calibration,
            List<MatchRow> holdout, PlayerIdentityCatalog identities) {
        return "# Match Engine V9 baseline and Matchup/Composition requalification\n\n"
                + "- Engine: `" + SimulationProvenanceService.ENGINE_IMPLEMENTATION_VERSION + "`\n"
                + "- Current production profile: `BASELINE_V1` (unchanged)\n"
                + "- LCK identity: 10 teams / " + identities.all().size() + " stable player IDs\n"
                + "- Calibration: 100 fixtures × 8 seeds × 3 profiles = " + calibration.size() + " rows\n"
                + "- Frozen holdout: 100 fixtures × 4 seeds × 3 profiles = " + holdout.size() + " rows\n"
                + "- Baseline status: `" + result.baselineStatus() + "`\n"
                + "- Matchup status: `" + result.matchupStatus() + "`\n"
                + "- Composition status: `" + result.compositionStatus() + "`\n"
                + "- Recommendation: `" + result.recommendation() + "`\n"
                + "- Production activation performed: `false`\n\n"
                + "The recommendation is evidence only. Public HTTP policy and frontend runtime were not changed.\n";
    }

    private static String jsonl(ObjectMapper mapper, List<MatchRow> rows) throws IOException {
        StringBuilder value = new StringBuilder();
        for (MatchRow row : rows) value.append(mapper.writeValueAsString(row)).append('\n');
        return value.toString();
    }

    private static String shaManifest(Path output) throws IOException {
        List<Path> files;
        try (var walk = Files.walk(output)) {
            files = walk.filter(Files::isRegularFile)
                    .filter(path -> !path.getFileName().toString().equals("SHA256SUMS.txt"))
                    .sorted(Comparator.comparing(path -> output.relativize(path).toString()))
                    .toList();
        }
        StringBuilder value = new StringBuilder();
        for (Path file : files) value.append(MatchEngineV9RequalificationRunner.fileHash(file))
                .append("  ").append(output.relativize(file).toString().replace('\\', '/')).append('\n');
        return value.toString();
    }

    private static void requirePairing(List<MatchRow> rows, int seedsPerFixture) {
        Map<String, Long> counts = rows.stream().collect(Collectors.groupingBy(
                MatchEngineV9RequalificationArtifactWriter::pairKey, Collectors.counting()));
        if (counts.size() != 100 * seedsPerFixture
                || counts.values().stream().anyMatch(value -> value != 3L)) {
            throw new IllegalStateException("Official rows do not form exact three-profile pairs");
        }
        for (var group : rows.stream().collect(Collectors.groupingBy(
                MatchEngineV9RequalificationArtifactWriter::pairKey)).values()) {
            if (group.stream().map(MatchRow::profileId).distinct().count() != 3
                    || group.stream().map(MatchRow::rosterIdentityHash).distinct().count() != 1
                    || group.stream().map(MatchRow::fixedDraftHash).distinct().count() != 1
                    || group.stream().map(MatchRow::finalAssignmentHash).distinct().count() != 1
                    || group.stream().map(MatchRow::seed).distinct().count() != 1) {
                throw new IllegalStateException("Roster/Draft/assignment/seed pairing mismatch");
            }
        }
    }

    private static Map<String, MatchRow> byPair(List<MatchRow> rows, SimulationRuntimeProfileId profile) {
        return rows.stream().filter(value -> value.profileId() == profile)
                .collect(Collectors.toMap(MatchEngineV9RequalificationArtifactWriter::pairKey,
                        value -> value, (a, b) -> { throw new IllegalStateException("Duplicate pair row"); },
                        TreeMap::new));
    }

    private static String pairKey(MatchRow row) {
        return row.fixtureId() + "|" + row.sampleLane() + "|" + row.seedIndex() + "|" + row.seed();
    }

    private static List<MatchRow> profile(List<MatchRow> rows, SimulationRuntimeProfileId profile) {
        return rows.stream().filter(value -> value.profileId() == profile).toList();
    }

    private static List<MatchRow> rows(List<FixtureCheckpoint> checkpoints) {
        return checkpoints.stream().sorted(Comparator.comparingInt(FixtureCheckpoint::fixtureIndex))
                .flatMap(value -> value.rows().stream())
                .sorted(Comparator.comparing(MatchRow::fixtureId)
                        .thenComparingInt(MatchRow::seedIndex)
                        .thenComparingInt(MatchRow::profileIndex)).toList();
    }

    private static List<MatchRow> concat(List<MatchRow> first, List<MatchRow> second) {
        ArrayList<MatchRow> result = new ArrayList<>(first.size() + second.size());
        result.addAll(first);
        result.addAll(second);
        return List.copyOf(result);
    }

    private static double mean(List<MatchRow> rows, ToDoubleFunction<MatchRow> value) {
        return rows.stream().mapToDouble(value).average().orElse(0.0);
    }

    private static double percentile(double[] raw, double percentile) {
        if (raw.length == 0) return 0.0;
        java.util.Arrays.sort(raw);
        int index = (int) Math.ceil(percentile * raw.length) - 1;
        return raw[Math.max(0, Math.min(raw.length - 1, index))];
    }

    private static Map<String, Long> distribution(
            List<MatchRow> rows, java.util.function.Function<MatchRow, String> value) {
        return rows.stream().collect(Collectors.groupingBy(value, TreeMap::new, Collectors.counting()));
    }

    private static String csv(String value) {
        return '"' + value.replace("\"", "\"\"") + '"';
    }

    private static String format(double value) {
        return String.format(Locale.ROOT, "%.9f", value);
    }

    private static void writeJson(ObjectMapper mapper, Path path, Object value) throws IOException {
        write(path, mapper.writeValueAsString(value) + "\n");
    }

    private static void write(Path path, String value) throws IOException {
        Files.writeString(path, value, StandardCharsets.UTF_8);
    }

    private static void copy(Path source, Path target) throws IOException {
        Files.copy(source, target, StandardCopyOption.COPY_ATTRIBUTES);
    }

    public record ProfileDecision(
            String baselineStatus, String matchupStatus, String compositionStatus,
            String recommendation, String reason) { }
}
