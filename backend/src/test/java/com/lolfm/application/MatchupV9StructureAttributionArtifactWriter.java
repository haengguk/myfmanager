package com.lolfm.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lolfm.application.MatchupV9StructureAttributionClassifier.FinalState;
import com.lolfm.application.MatchupV9StructureAttributionClassifier.Severity;
import com.lolfm.application.MatchupV9StructureAttributionRunner.FinalizationResult;
import com.lolfm.application.MatchupV9StructureAttributionRunner.PairRow;
import com.lolfm.application.MatchupV9StructureAttributionRunner.RunSummary;
import com.lolfm.application.MatchupV9StructureAttributionRunner.StructureTimeline;
import com.lolfm.simulator.Lane;
import com.lolfm.simulator.SimulationRuntimeProfileId;
import com.lolfm.simulator.TeamSide;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Deterministic report projector for the attribution-only paired sample. */
public final class MatchupV9StructureAttributionArtifactWriter {
    public static final String RECOMMENDATION =
            "MATCHUP_V9_STRUCTURE_ATTRIBUTION_BLOCKED_BY_DIAGNOSTIC_GAP";

    private MatchupV9StructureAttributionArtifactWriter() {
    }

    public static FinalizationResult write(
            ObjectMapper mapper,
            Path backendRoot,
            Path output,
            MatchupV9StructureAttributionRunner.Binding binding,
            List<PairRow> pairs
    ) throws Exception {
        requirePairs(pairs);
        LegacyAnalysis legacy = legacyAnalysis(mapper, backendRoot);
        Map<String, Object> severity = severitySummary(pairs);
        Map<String, Object> hpOnly = hpOnlySummary(pairs);
        Map<String, Object> progression = progressionSummary(pairs);
        Map<String, Object> timing = timingSummary(pairs);
        Map<String, Object> symmetry = symmetrySummary(pairs);
        Map<String, Object> local = localAttributionSummary(pairs);
        Map<String, Object> macro = winnerObjectiveDurationSummary(pairs);
        Map<String, Object> acceptance = proposedAcceptance(pairs, severity, macro);
        Map<String, Object> recommendation = recommendation(pairs, acceptance);

        write(output.resolve("paired-structure-components.jsonl"), jsonl(mapper, pairs));
        writeJson(mapper, output.resolve("structure-severity-summary.json"), severity);
        write(output.resolve("structure-severity-summary.csv"), severityCsv(pairs));
        writeJson(mapper, output.resolve("structure-hp-only-summary.json"), hpOnly);
        writeJson(mapper, output.resolve("structure-progression-summary.json"), progression);
        writeJson(mapper, output.resolve("structure-timing-source-lane-summary.json"), timing);
        writeJson(mapper, output.resolve("side-orientation-symmetry-summary.json"), symmetry);
        writeJson(mapper, output.resolve("matchup-local-to-structure-attribution-summary.json"), local);
        writeJson(mapper, output.resolve("paired-winner-objective-duration-summary.json"), macro);
        writeJson(mapper, output.resolve("legacy-consumed-holdout-read-only-analysis.json"), legacy);
        writeJson(mapper, output.resolve("proposed-v9-structure-acceptance-contract.json"), acceptance);
        writeJson(mapper, output.resolve("recommendation.json"), recommendation);
        write(output.resolve("analysis.md"), analysis(binding, pairs, legacy, severity,
                progression, timing, symmetry, local, macro));
        write(output.resolve("SHA256SUMS.txt"), shaManifest(output));
        String manifestHash = MatchupV9StructureAttributionRunner.fileHash(
                output.resolve("SHA256SUMS.txt"));
        verifyManifest(output);
        return new FinalizationResult(
                "MATCHUP_V9_STRUCTURE_ATTRIBUTION_FINALIZATION_RESULT_V1",
                pairs.size() * 2, pairs.size(), RECOMMENDATION, false, manifestHash);
    }

    private static void requirePairs(List<PairRow> pairs) {
        if (pairs.size() != 400 || pairs.stream().map(PairRow::pairKey).distinct().count() != 400
                || pairs.stream().anyMatch(value -> !value.inputIdentityExact()
                || !value.correctness().pass())) {
            throw new IllegalStateException("Attribution pair/integrity coverage mismatch");
        }
        long replayChecks = pairs.stream().filter(value -> value.verification().replayChecked()).count();
        long instrumentation = pairs.stream().mapToLong(value ->
                value.verification().instrumentationProfilesChecked()).sum();
        if (replayChecks != 100 || instrumentation != 200
                || pairs.stream().anyMatch(value -> !value.verification().replayExact()
                || !value.verification().instrumentationTimelineRandomExact())) {
            throw new IllegalStateException("Attribution determinism/instrumentation coverage mismatch");
        }
    }

    private static Map<String, Object> severitySummary(List<PairRow> pairs) {
        EnumMap<Severity, Long> counts = new EnumMap<>(Severity.class);
        EnumMap<Severity, Double> rates = new EnumMap<>(Severity.class);
        for (Severity value : Severity.values()) {
            long count = pairs.stream().filter(row ->
                    row.finalStructureComponents().primarySeverity() == value).count();
            counts.put(value, count);
            rates.put(value, 100.0 * count / pairs.size());
        }
        long any = pairs.stream().filter(row -> !row.finalStructureComponents()
                .finalStructureStateExactEquality()).count();
        long multiLabel = pairs.stream().filter(row -> row.finalStructureComponents()
                .severityLabels().size() > 1).count();
        return Map.ofEntries(
                Map.entry("schemaVersion", "MATCHUP_V9_STRUCTURE_SEVERITY_SUMMARY_V1"),
                Map.entry("pairedComparisonCount", pairs.size()),
                Map.entry("legacyAnyFinalCanonicalStructureStateChangedCount", any),
                Map.entry("legacyAnyFinalCanonicalStructureStateChangedRatePercent",
                        100.0 * any / pairs.size()),
                Map.entry("primaryBucketCounts", counts),
                Map.entry("primaryBucketRatesPercent", rates),
                Map.entry("primaryBucketsMutuallyExclusive", counts.values().stream()
                        .mapToLong(Long::longValue).sum() == pairs.size()),
                Map.entry("multiLabelPairCount", multiLabel),
                Map.entry("classificationBasis", "FINAL_STRUCTURED_STRUCTURE_STATE_COMPONENTS"));
    }

    private static Map<String, Object> hpOnlySummary(List<PairRow> pairs) {
        long hpOnly = pairs.stream().filter(row -> row.finalStructureComponents()
                .hpOnlyDifference()).count();
        TreeMap<String, Double> absoluteHealthDelta = new TreeMap<>();
        TreeMap<String, Long> healthChangedPairs = new TreeMap<>();
        for (String kind : List.of("OUTER_TOWER", "INNER_TOWER", "INHIBITOR_TURRET",
                "INHIBITOR", "NEXUS_TURRET", "NEXUS")) {
            absoluteHealthDelta.put(kind, 0.0);
            healthChangedPairs.put(kind, 0L);
        }
        for (PairRow row : pairs) {
            TreeMap<String, Double> pair = healthDelta(
                    row.baseline().finalStructureState(), row.matchupCandidate().finalStructureState());
            pair.forEach((kind, delta) -> {
                absoluteHealthDelta.merge(kind, delta, Double::sum);
                if (delta > 0.0) healthChangedPairs.merge(kind, 1L, Long::sum);
            });
        }
        return Map.ofEntries(
                Map.entry("schemaVersion", "MATCHUP_V9_STRUCTURE_HP_ONLY_SUMMARY_V1"),
                Map.entry("pairedComparisonCount", pairs.size()),
                Map.entry("hpOnlyPairCount", hpOnly),
                Map.entry("hpOnlyPairRatePercent", 100.0 * hpOnly / pairs.size()),
                Map.entry("healthChangedPairCountByComponent", healthChangedPairs),
                Map.entry("sumAbsoluteFinalHealthDeltaByComponent", absoluteHealthDelta),
                Map.entry("maximumHealthDifferencePairCount", pairs.stream().filter(row ->
                        row.finalStructureComponents().anyMaximumHealthDifference()).count()),
                Map.entry("meaning", "HP_ONLY_REQUIRES_EQUAL_ALIVE_PROGRESSION_COMPONENTS"));
    }

    private static TreeMap<String, Double> healthDelta(FinalState before, FinalState after) {
        TreeMap<String, Double> result = new TreeMap<>();
        for (TeamSide side : TeamSide.values()) {
            var a = before.teams().get(side);
            var b = after.teams().get(side);
            merge(result, "NEXUS", Math.abs(a.nexusCurrentHealth() - b.nexusCurrentHealth()));
            int nexusCount = Math.min(a.nexusTurretCurrentHealth().size(),
                    b.nexusTurretCurrentHealth().size());
            for (int index = 0; index < nexusCount; index++) {
                merge(result, "NEXUS_TURRET", Math.abs(a.nexusTurretCurrentHealth().get(index)
                        - b.nexusTurretCurrentHealth().get(index)));
            }
            for (Lane lane : Lane.values()) {
                var al = a.lanes().get(lane);
                var bl = b.lanes().get(lane);
                merge(result, "OUTER_TOWER", Math.abs(al.outerTower().current() - bl.outerTower().current()));
                merge(result, "INNER_TOWER", Math.abs(al.innerTower().current() - bl.innerTower().current()));
                merge(result, "INHIBITOR_TURRET", Math.abs(al.inhibitorTower().current() - bl.inhibitorTower().current()));
                merge(result, "INHIBITOR", Math.abs(al.inhibitor().current() - bl.inhibitor().current()));
            }
        }
        return result;
    }

    private static void merge(Map<String, Double> target, String key, double value) {
        target.merge(key, value, Double::sum);
    }

    private static Map<String, Object> progressionSummary(List<PairRow> pairs) {
        LinkedHashMap<String, Long> counts = new LinkedHashMap<>();
        counts.put("laneOuterTowerAliveDifference", count(pairs,
                row -> row.finalStructureComponents().laneOuterTowerAliveDifference()));
        counts.put("laneInnerTowerAliveDifference", count(pairs,
                row -> row.finalStructureComponents().laneInnerTowerAliveDifference()));
        counts.put("inhibitorTurretAliveDifference", count(pairs,
                row -> row.finalStructureComponents().inhibitorTurretAliveDifference()));
        counts.put("inhibitorAliveDifference", count(pairs,
                row -> row.finalStructureComponents().inhibitorAliveDifference()));
        counts.put("individualNexusTurretAliveDifference", count(pairs,
                row -> row.finalStructureComponents().individualNexusTurretAliveDifference()));
        counts.put("individualNexusTurretHpDifference", count(pairs,
                row -> row.finalStructureComponents().individualNexusTurretHpDifference()));
        counts.put("nexusAliveDifference", count(pairs,
                row -> row.finalStructureComponents().nexusAliveDifference()));
        counts.put("towersDestroyedCountDifference", count(pairs,
                row -> row.finalStructureComponents().towersDestroyedCountDifference()));
        counts.put("inhibitorsRemainingDifference", count(pairs,
                row -> row.finalStructureComponents().inhibitorsRemainingDifference()));
        counts.put("nexusTurretsRemainingDifference", count(pairs,
                row -> row.finalStructureComponents().nexusTurretsRemainingDifference()));
        long macro = pairs.stream().filter(row -> switch (
                row.finalStructureComponents().primarySeverity()) {
            case LANE_TOWER_PROGRESSION, INHIBITOR_PROGRESSION,
                    NEXUS_TURRET_PROGRESSION, NEXUS_OR_ENDING -> true;
            default -> false;
        }).count();
        return Map.ofEntries(
                Map.entry("schemaVersion", "MATCHUP_V9_STRUCTURE_PROGRESSION_SUMMARY_V1"),
                Map.entry("pairedComparisonCount", pairs.size()),
                Map.entry("componentDifferenceCounts", counts),
                Map.entry("anyDestructionOrProgressionDifferenceCount", macro),
                Map.entry("anyDestructionOrProgressionDifferenceRatePercent", 100.0 * macro / pairs.size()),
                Map.entry("componentCountsAreMultiLabel", true));
    }

    private static Map<String, Object> timingSummary(List<PairRow> pairs) {
        LinkedHashMap<String, Object> differences = new LinkedHashMap<>();
        differences.put("firstTower", milestoneStats(pairs,
                row -> row.timingAndEventDifferences().firstTower()));
        differences.put("firstInhibitor", milestoneStats(pairs,
                row -> row.timingAndEventDifferences().firstInhibitor()));
        differences.put("baseOpen", milestoneStats(pairs,
                row -> row.timingAndEventDifferences().baseOpen()));
        differences.put("firstNexusTurret", milestoneStats(pairs,
                row -> row.timingAndEventDifferences().firstNexusTurret()));
        differences.put("nexusDestruction", milestoneStats(pairs,
                row -> row.timingAndEventDifferences().nexusDestruction()));
        return Map.ofEntries(
                Map.entry("schemaVersion", "MATCHUP_V9_STRUCTURE_TIMING_SOURCE_LANE_SUMMARY_V1"),
                Map.entry("pairedComparisonCount", pairs.size()),
                Map.entry("milestoneDifferences", differences),
                Map.entry("structureDamageEventCountDifferencePairs", count(pairs, row ->
                        row.timingAndEventDifferences().structureDamageEventCountDifference())),
                Map.entry("structureDestroyedEventCountDifferencePairs", count(pairs, row ->
                        row.timingAndEventDifferences().structureDestroyedEventCountDifference())),
                Map.entry("persistentSiegeStartCountDifferencePairs", count(pairs, row ->
                        row.timingAndEventDifferences().persistentSiegeStartCountDifference())),
                Map.entry("persistentSiegeStopCountDifferencePairs", count(pairs, row ->
                        row.timingAndEventDifferences().persistentSiegeStopCountDifference())),
                Map.entry("persistentSiegeDurationDifferencePairs", count(pairs, row ->
                        row.timingAndEventDifferences().persistentSiegeDurationDifference())),
                Map.entry("inhibitorRespawnHistoryDifferencePairs", count(pairs, row ->
                        row.timingAndEventDifferences().inhibitorRespawnHistoryDifference())),
                Map.entry("nexusTurretRespawnCountDifferencePairs", count(pairs, row ->
                        row.timingAndEventDifferences().nexusTurretRespawnCountDifference())),
                Map.entry("sourceLaneDistributionDifferencePairs", count(pairs, row ->
                        row.timingAndEventDifferences().sourceLaneDistributionDifference())),
                Map.entry("baselineAggregate", aggregateTimeline(pairs, PairRow::baseline)),
                Map.entry("matchupCandidateAggregate", aggregateTimeline(pairs, PairRow::matchupCandidate)));
    }

    private static Map<String, Object> milestoneStats(List<PairRow> pairs,
            Function<PairRow, MatchupV9StructureAttributionRunner.MilestoneDifference> getter) {
        long any = pairs.stream().filter(row -> getter.apply(row).anyDifference()).count();
        long time = pairs.stream().filter(row -> getter.apply(row).timeDifference()).count();
        long lane = pairs.stream().filter(row -> getter.apply(row).laneDifference()).count();
        long source = pairs.stream().filter(row -> getter.apply(row).sourceDifference()).count();
        return Map.of("anyDifferencePairs", any, "timeDifferencePairs", time,
                "laneDifferencePairs", lane, "sourceDifferencePairs", source);
    }

    private static Map<String, Object> aggregateTimeline(
            List<PairRow> pairs, Function<PairRow, RunSummary> getter) {
        TreeMap<String, Long> sourceLane = new TreeMap<>();
        for (PairRow pair : pairs) getter.apply(pair).structureTimeline().sourceLaneDistribution()
                .forEach((key, value) -> sourceLane.merge(key, value.longValue(), Long::sum));
        return Map.ofEntries(
                Map.entry("structureDamageEvents", pairs.stream().map(getter)
                        .mapToLong(value -> value.structureTimeline().structureDamageEvents()).sum()),
                Map.entry("structureDestroyedEvents", pairs.stream().map(getter)
                        .mapToLong(value -> value.structureTimeline().structureDestroyedEvents()).sum()),
                Map.entry("persistentSiegeStarted", pairs.stream().map(getter)
                        .mapToLong(value -> value.structureTimeline().persistentSiegeStarted()).sum()),
                Map.entry("persistentSiegeStopped", pairs.stream().map(getter)
                        .mapToLong(value -> value.structureTimeline().persistentSiegeStopped()).sum()),
                Map.entry("persistentSiegeDurationSeconds", pairs.stream().map(getter)
                        .mapToLong(value -> value.structureTimeline().persistentSiegeDurationSeconds()).sum()),
                Map.entry("nexusTurretRespawns", pairs.stream().map(getter)
                        .mapToLong(value -> value.structureTimeline().nexusTurretRespawns()).sum()),
                Map.entry("inhibitorRespawns", pairs.stream().map(getter)
                        .mapToLong(value -> value.structureTimeline().inhibitorRespawns().size()).sum()),
                Map.entry("sourceLaneDistribution", sourceLane));
    }

    private static Map<String, Object> symmetrySummary(List<PairRow> pairs) {
        TreeMap<String, Concentration> teams = concentrations(pairs,
                row -> List.of(row.blueTeamCode(), row.redTeamCode()));
        TreeMap<String, Concentration> champions = concentrations(pairs,
                row -> row.fixedInput().championsBySidePosition().values().stream().distinct().toList());
        TreeMap<String, Concentration> players = concentrations(pairs,
                row -> row.fixedInput().playerIdsBySidePosition().values().stream().distinct().toList());
        TreeMap<String, Object> orientations = new TreeMap<>();
        pairs.stream().collect(Collectors.groupingBy(PairRow::unorderedTeamPairId,
                        TreeMap::new, Collectors.toList()))
                .forEach((pairId, rows) -> orientations.put(pairId,
                        rows.stream().collect(Collectors.groupingBy(
                                row -> row.blueTeamCode() + "_BLUE__" + row.redTeamCode() + "_RED",
                                TreeMap::new, Collectors.collectingAndThen(Collectors.toList(),
                                        MatchupV9StructureAttributionArtifactWriter::concentration)))));
        EnumMap<TeamSide, Long> progressionByDefendingSide = new EnumMap<>(TeamSide.class);
        EnumMap<TeamSide, Long> anyByDefendingSide = new EnumMap<>(TeamSide.class);
        for (TeamSide side : TeamSide.values()) {
            progressionByDefendingSide.put(side, pairs.stream().filter(row ->
                    sideProgressionChanged(row, side)).count());
            anyByDefendingSide.put(side, pairs.stream().filter(row ->
                    sideFinalChanged(row, side)).count());
        }
        TreeMap<String, Long> winnerFlips = new TreeMap<>();
        for (PairRow row : pairs) {
            if (row.winnerChanged()) winnerFlips.merge(
                    row.baseline().winnerSide() + "_TO_" + row.matchupCandidate().winnerSide(),
                    1L, Long::sum);
        }
        return Map.ofEntries(
                Map.entry("schemaVersion", "MATCHUP_V9_SIDE_ORIENTATION_SYMMETRY_SUMMARY_V1"),
                Map.entry("pairedComparisonCount", pairs.size()),
                Map.entry("finalStateChangedByDefendingSide", anyByDefendingSide),
                Map.entry("progressionChangedByDefendingSide", progressionByDefendingSide),
                Map.entry("winnerFlipDirections", winnerFlips),
                Map.entry("unorderedTeamPairOrientations", orientations),
                Map.entry("teamExposureConcentration", teams),
                Map.entry("championExposureConcentration", champions),
                Map.entry("stablePlayerExposureConcentration", players),
                Map.entry("matchupPerspectiveMismatchCount", pairs.stream().mapToLong(row ->
                        row.correctness().matchupPerspectiveMismatchCount()).sum()),
                Map.entry("concentrationIsCausalConclusion", false));
    }

    private static boolean sideFinalChanged(PairRow row, TeamSide side) {
        return !row.baseline().finalStructureState().teams().get(side).equals(
                row.matchupCandidate().finalStructureState().teams().get(side));
    }

    private static boolean sideProgressionChanged(PairRow row, TeamSide side) {
        var a = row.baseline().finalStructureState().teams().get(side);
        var b = row.matchupCandidate().finalStructureState().teams().get(side);
        if (a.nexusAlive() != b.nexusAlive()
                || a.nexusTurretsRemaining() != b.nexusTurretsRemaining()) return true;
        for (Lane lane : Lane.values()) {
            var al = a.lanes().get(lane);
            var bl = b.lanes().get(lane);
            if (al.outerTower().alive() != bl.outerTower().alive()
                    || al.innerTower().alive() != bl.innerTower().alive()
                    || al.inhibitorTower().alive() != bl.inhibitorTower().alive()
                    || al.inhibitor().alive() != bl.inhibitor().alive()) return true;
        }
        return false;
    }

    private static TreeMap<String, Concentration> concentrations(
            List<PairRow> pairs, Function<PairRow, List<String>> keys) {
        TreeMap<String, MutableConcentration> raw = new TreeMap<>();
        for (PairRow row : pairs) {
            for (String key : keys.apply(row)) {
                MutableConcentration value = raw.computeIfAbsent(key,
                        ignored -> new MutableConcentration());
                value.exposure++;
                if (!row.finalStructureComponents().finalStructureStateExactEquality()) value.changed++;
                if (isMacro(row)) value.macro++;
            }
        }
        TreeMap<String, Concentration> result = new TreeMap<>();
        raw.forEach((key, value) -> result.put(key, new Concentration(
                value.exposure, value.changed, value.macro,
                100.0 * value.changed / value.exposure,
                100.0 * value.macro / value.exposure)));
        return result;
    }

    private static Concentration concentration(List<PairRow> rows) {
        long changed = rows.stream().filter(row -> !row.finalStructureComponents()
                .finalStructureStateExactEquality()).count();
        long macro = rows.stream().filter(MatchupV9StructureAttributionArtifactWriter::isMacro).count();
        return new Concentration(rows.size(), changed, macro,
                100.0 * changed / rows.size(), 100.0 * macro / rows.size());
    }

    private static boolean isMacro(PairRow row) {
        return switch (row.finalStructureComponents().primarySeverity()) {
            case LANE_TOWER_PROGRESSION, INHIBITOR_PROGRESSION,
                    NEXUS_TURRET_PROGRESSION, NEXUS_OR_ENDING -> true;
            default -> false;
        };
    }

    private static Map<String, Object> localAttributionSummary(List<PairRow> pairs) {
        return Map.ofEntries(
                Map.entry("schemaVersion", "MATCHUP_V9_LOCAL_TO_STRUCTURE_ATTRIBUTION_SUMMARY_V1"),
                Map.entry("baselineOffContributionCount", pairs.stream().mapToLong(row ->
                        row.localAttribution().baselineMatchupApplications()).sum()),
                Map.entry("candidateNonZeroApplicationCount", pairs.stream().mapToLong(row ->
                        row.localAttribution().candidateMatchupApplications()).sum()),
                Map.entry("candidateEdgeSum", pairs.stream().mapToDouble(row ->
                        row.localAttribution().candidateMatchupEdgeSum()).sum()),
                Map.entry("directRandomCalls", pairs.stream().mapToLong(row ->
                        row.localAttribution().directRandomCalls()).sum()),
                Map.entry("perspectiveMismatchErrors", pairs.stream().mapToLong(row ->
                        row.localAttribution().perspectiveMismatchErrors()).sum()),
                Map.entry("applicationByPosition", "CAUSAL_PROVENANCE_UNAVAILABLE"),
                Map.entry("applicationByContext", "CAUSAL_PROVENANCE_UNAVAILABLE"),
                Map.entry("firstLocalMatchupCauseTime", "CAUSAL_PROVENANCE_UNAVAILABLE"),
                Map.entry("firstPublicTimelineDivergencePairCount", divergenceCount(pairs,
                        row -> row.divergence().firstPublicTimelineDivergenceSeconds())),
                Map.entry("firstCombatDivergencePairCount", divergenceCount(pairs,
                        row -> row.divergence().firstCombatDivergenceSeconds())),
                Map.entry("firstPressureDivergencePairCount", divergenceCount(pairs,
                        row -> row.divergence().firstPressureDivergenceSeconds())),
                Map.entry("firstEconomyDivergencePairCount", divergenceCount(pairs,
                        row -> row.divergence().firstEconomyDivergenceSeconds())),
                Map.entry("firstStructureDivergencePairCount", divergenceCount(pairs,
                        row -> row.divergence().firstStructureDivergenceSeconds())),
                Map.entry("temporalOrderIsCausalProof", false),
                Map.entry("causalProvenanceStatus", "CAUSAL_PROVENANCE_UNAVAILABLE"));
    }

    private static long divergenceCount(List<PairRow> pairs,
            java.util.function.ToIntFunction<PairRow> value) {
        return pairs.stream().filter(row -> value.applyAsInt(row) >= 0).count();
    }

    private static Map<String, Object> winnerObjectiveDurationSummary(List<PairRow> pairs) {
        long beforeBlue = pairs.stream().filter(row -> row.baseline().winnerSide() == TeamSide.BLUE).count();
        long afterBlue = pairs.stream().filter(row -> row.matchupCandidate().winnerSide() == TeamSide.BLUE).count();
        double[] beforeDurations = pairs.stream().mapToDouble(row -> row.baseline().durationSeconds()).toArray();
        double[] afterDurations = pairs.stream().mapToDouble(row -> row.matchupCandidate().durationSeconds()).toArray();
        double[] pairedDeltas = pairs.stream().mapToDouble(PairRow::durationDeltaSeconds).toArray();
        return Map.ofEntries(
                Map.entry("schemaVersion", "MATCHUP_V9_PAIRED_WINNER_OBJECTIVE_DURATION_SUMMARY_V1"),
                Map.entry("pairedComparisonCount", pairs.size()),
                Map.entry("blueWinRateDeltaPercentagePoints", 100.0 * (afterBlue - beforeBlue) / pairs.size()),
                Map.entry("winnerChangedCount", pairs.stream().filter(PairRow::winnerChanged).count()),
                Map.entry("winnerChangedRatePercent", 100.0 * pairs.stream().filter(PairRow::winnerChanged).count() / pairs.size()),
                Map.entry("objectiveChangedCount", pairs.stream().filter(PairRow::objectiveChanged).count()),
                Map.entry("objectiveChangedRatePercent", 100.0 * pairs.stream().filter(PairRow::objectiveChanged).count() / pairs.size()),
                Map.entry("meanDurationDeltaSeconds", Arrays.stream(pairedDeltas).average().orElse(0.0)),
                Map.entry("p95DurationDeltaSeconds", percentile(afterDurations, .95) - percentile(beforeDurations, .95)),
                Map.entry("pairedDeltaP95Seconds", percentile(pairedDeltas, .95)),
                Map.entry("pairedDeltaMinSeconds", Arrays.stream(pairedDeltas).min().orElse(0.0)),
                Map.entry("pairedDeltaMaxSeconds", Arrays.stream(pairedDeltas).max().orElse(0.0)));
    }

    private static Map<String, Object> proposedAcceptance(
            List<PairRow> pairs, Map<String, Object> severity, Map<String, Object> macro) {
        long correctnessErrors = pairs.stream().mapToLong(row -> row.correctness().pass() ? 0 : 1).sum();
        return Map.ofEntries(
                Map.entry("schemaVersion", "MATCHUP_V9_PROPOSED_STRUCTURE_ACCEPTANCE_CONTRACT_V1"),
                Map.entry("status", "PROPOSED_FROM_ATTRIBUTION_CALIBRATION_NOT_OFFICIAL_ELIGIBILITY"),
                Map.entry("calibrationSampleConsumed", true),
                Map.entry("futureOfficialDecisionRequires", "SEPARATE_FRESH_CONTRACT_AND_NON_OVERLAPPING_SEEDS"),
                Map.entry("legacyTwelvePercentGate", Map.of(
                        "retainedAsReference", true,
                        "copiedAsV9HardGate", false,
                        "reason", "PHASE_13D_COMPOSITION_SANITY_FALLBACK_NOT_CANONICAL_V9_PRODUCT_TOLERANCE")),
                Map.entry("correctnessExactGate", Map.of(
                        "requiredValue", 0, "observedPairFailures", correctnessErrors,
                        "metrics", List.of("invalidHealth", "duplicateStructuredAction",
                                "nexusOrdering", "postFinishMutation", "impossibleRespawn",
                                "displayIdentity", "ineligibleDuplicateRandomConsumption"))),
                Map.entry("observationalSensitivity", Map.of(
                        "hardCeiling", "NONE_WITHOUT_PRODUCT_RATIONALE",
                        "metrics", List.of("anyFinalStateChanged", "hpOnlyChanged",
                                "eventTimingChanged", "sourceLaneChanged"),
                        "observed", severity)),
                Map.entry("gameplayCriticalMacroSafety", Map.of(
                        "metrics", List.of("laneTowerProgression", "inhibitorProgression",
                                "nexusTurretProgression", "nexusEnding", "winner", "objective",
                                "sideAsymmetry", "durationMeanP95"),
                        "numericThresholds", "THRESHOLD_REQUIRES_PRODUCT_DECISION",
                        "observed", macro)),
                Map.entry("causalReachabilityGate", Map.of(
                        "matchupApplicationGtZero", pairs.stream().mapToLong(row ->
                                row.matchupCandidate().matchupApplications()).sum() > 0,
                        "offContributionZero", pairs.stream().mapToLong(row ->
                                row.baseline().matchupApplications()).sum() == 0,
                        "directRandomZero", pairs.stream().mapToLong(row ->
                                row.correctness().matchupDirectRandomCallCount()).sum() == 0,
                        "perspectiveMismatchZero", pairs.stream().mapToLong(row ->
                                row.correctness().matchupPerspectiveMismatchCount()).sum() == 0,
                        "localCauseAvailable", false,
                        "status", "CAUSAL_PROVENANCE_UNAVAILABLE")),
                Map.entry("productionActivation", false));
    }

    private static Map<String, Object> recommendation(
            List<PairRow> pairs, Map<String, Object> acceptance) {
        return Map.ofEntries(
                Map.entry("schemaVersion", "MATCHUP_V9_STRUCTURE_ATTRIBUTION_RECOMMENDATION_V1"),
                Map.entry("recommendation", RECOMMENDATION),
                Map.entry("reason", "FINAL_STRUCTURE_SEVERITY_IS_OBSERVABLE_BUT_MATCHUP_APPLICATION_TIME_POSITION_CONTEXT_PROVENANCE_IS_NOT_RETAINED"),
                Map.entry("correctnessExactPass", pairs.stream().allMatch(row -> row.correctness().pass())),
                Map.entry("candidateReachabilityPass", pairs.stream().mapToLong(row ->
                        row.matchupCandidate().matchupApplications()).sum() > 0),
                Map.entry("causalProvenanceAvailable", false),
                Map.entry("acceptanceContract", acceptance),
                Map.entry("predecessorRecommendationPreserved", "RECOMMEND_BASELINE_V1"),
                Map.entry("matchupV9EligibleDeclared", false),
                Map.entry("productionChanged", false),
                Map.entry("gameplayTuningPerformed", false));
    }

    private static LegacyAnalysis legacyAnalysis(ObjectMapper mapper, Path backendRoot)
            throws Exception {
        Path root = backendRoot.resolve(MatchupV9StructureAttributionEvidence.PREDECESSOR);
        ArrayList<MatchEngineV9RequalificationRunner.MatchRow> holdout = new ArrayList<>();
        for (String line : Files.readAllLines(root.resolve("per-match-results.jsonl"),
                StandardCharsets.UTF_8)) {
            if (line.isBlank()) continue;
            var row = mapper.readValue(line, MatchEngineV9RequalificationRunner.MatchRow.class);
            if (row.sampleLane() == MatchEngineV9RequalificationContract.SampleLane.HOLDOUT
                    && (row.profileId() == SimulationRuntimeProfileId.BASELINE_V1
                    || row.profileId() == SimulationRuntimeProfileId.MATCHUP_ONLY_CANDIDATE_V1)) {
                holdout.add(row);
            }
        }
        Map<String, MatchEngineV9RequalificationRunner.MatchRow> before = legacyByPair(
                holdout, SimulationRuntimeProfileId.BASELINE_V1);
        Map<String, MatchEngineV9RequalificationRunner.MatchRow> after = legacyByPair(
                holdout, SimulationRuntimeProfileId.MATCHUP_ONLY_CANDIDATE_V1);
        ArrayList<LegacyChangedPair> changed = new ArrayList<>();
        for (String key : before.keySet()) {
            var a = before.get(key);
            var b = after.get(key);
            if (!a.structureSignature().equals(b.structureSignature())) {
                changed.add(new LegacyChangedPair(
                        key, a.fixtureId(), a.fixtureLane(), a.pairId(), a.blueTeamCode(),
                        a.redTeamCode(), a.seedIndex(), a.seed(), a.winnerSide() != b.winnerSide(),
                        !a.objectiveSignature().equals(b.objectiveSignature()),
                        b.durationSeconds() - a.durationSeconds(),
                        b.structureDamageEvents() - a.structureDamageEvents(),
                        b.structureDestroyedEvents() - a.structureDestroyedEvents(),
                        b.siegeStarted() - a.siegeStarted(), b.siegeStopped() - a.siegeStopped(),
                        a.firstTowerSeconds() != b.firstTowerSeconds()
                                || !a.firstTowerLane().equals(b.firstTowerLane())
                                || !a.firstTowerSource().equals(b.firstTowerSource()),
                        a.firstTowerSeconds(), b.firstTowerSeconds(),
                        a.firstTowerLane(), b.firstTowerLane(),
                        a.firstTowerSource(), b.firstTowerSource()));
            }
        }
        if (changed.size() != 126) {
            throw new IllegalStateException("Legacy structure-changed recovery differs from 126/400");
        }
        TreeMap<String, Long> fixture = distribution(changed, LegacyChangedPair::fixtureId);
        TreeMap<String, Long> team = new TreeMap<>();
        for (LegacyChangedPair row : changed) {
            team.merge(row.blueTeamCode(), 1L, Long::sum);
            team.merge(row.redTeamCode(), 1L, Long::sum);
        }
        TreeMap<String, Long> lane = distribution(changed, LegacyChangedPair::baselineFirstTowerLane);
        return new LegacyAnalysis(
                "MATCH_ENGINE_V9_CONSUMED_HOLDOUT_STRUCTURE_CHANGED_READ_ONLY_ANALYSIS_V1",
                "READ_ONLY_PREDECESSOR_EVIDENCE_NOT_REEXECUTED_OR_RELABELED",
                400, changed.size(), 100.0 * changed.size() / 400.0,
                "PAIRED_MATCHES_WHOSE_FINAL_CANONICAL_STRUCTURE_STATE_SIGNATURE_DIFFERS",
                changed.stream().filter(LegacyChangedPair::winnerChanged).count(),
                changed.stream().filter(LegacyChangedPair::objectiveChanged).count(),
                changed.stream().mapToInt(LegacyChangedPair::durationDeltaSeconds).average().orElse(0.0),
                changed.stream().mapToLong(LegacyChangedPair::damageEventDelta).sum(),
                changed.stream().mapToLong(LegacyChangedPair::destroyedEventDelta).sum(),
                changed.stream().mapToLong(LegacyChangedPair::siegeStartedDelta).sum(),
                changed.stream().mapToLong(LegacyChangedPair::siegeStoppedDelta).sum(),
                changed.stream().filter(LegacyChangedPair::firstTowerChanged).count(),
                fixture, team, lane, List.copyOf(changed),
                List.of("FINAL_COMPONENT_HP_AND_ALIVE_STATE_NOT_RETAINED",
                        "FIRST_INHIBITOR_BASE_OPEN_NEXUS_TIMING_NOT_RETAINED",
                        "MATCHUP_LOCAL_APPLICATION_TIME_POSITION_CONTEXT_NOT_RETAINED",
                        "STABLE_PLAYER_IDS_NOT_RETAINED_IN_RAW_MATCH_ROWS"));
    }

    private static Map<String, MatchEngineV9RequalificationRunner.MatchRow> legacyByPair(
            List<MatchEngineV9RequalificationRunner.MatchRow> rows,
            SimulationRuntimeProfileId profile) {
        return rows.stream().filter(value -> value.profileId() == profile)
                .collect(Collectors.toMap(
                        value -> value.fixtureId() + "|" + value.seedIndex() + "|" + value.seed(),
                        value -> value, (a, b) -> { throw new IllegalStateException("Duplicate legacy pair"); },
                        TreeMap::new));
    }

    private static <T> TreeMap<String, Long> distribution(
            List<T> rows, Function<T, String> value) {
        return rows.stream().collect(Collectors.groupingBy(value, TreeMap::new, Collectors.counting()));
    }

    private static String analysis(
            MatchupV9StructureAttributionRunner.Binding binding,
            List<PairRow> pairs,
            LegacyAnalysis legacy,
            Map<String, Object> severity,
            Map<String, Object> progression,
            Map<String, Object> timing,
            Map<String, Object> symmetry,
            Map<String, Object> local,
            Map<String, Object> macro
    ) {
        return "# Matchup V9 구조물 영향 attribution\n\n"
                + "- Source HEAD: `" + binding.sourceIdentity().gitHead() + "`\n"
                + "- Engine/rules: `" + binding.sourceIdentity().engineImplementationVersion()
                + "` / `" + com.lolfm.simulator.SimulationRuntimeProfiles.PRE_JUNGLE_ACTIVE_GAMEPLAY_RULES_VERSION + "`\n"
                + "- 선행 consumed holdout: manifest/checkpoint/raw binding 검증 완료, verdict `RECOMMEND_BASELINE_V1` 보존\n"
                + "- 기존 31.5%의 의미: paired match 400개 중 최종 canonical structure state가 달랐던 126개\n"
                + "- 새 attribution: 100 fixtures × 4 diagnostic seeds × 2 profiles = "
                + (pairs.size() * 2) + " match rows / " + pairs.size() + " pairs\n"
                + "- 새 seed 상태: `CONSUMED_AS_DIAGNOSTIC_NOT_HOLDOUT`\n"
                + "- Severity: `" + severity + "`\n"
                + "- Progression: `" + progression + "`\n"
                + "- Timing/source/lane: `" + timing + "`\n"
                + "- Side/orientation concentration: `" + symmetry + "`\n"
                + "- Winner/objective/duration: `" + macro + "`\n"
                + "- Local attribution: `" + local + "`\n"
                + "- Correctness exact gates: `" + pairs.stream().allMatch(row -> row.correctness().pass()) + "`\n"
                + "- Final recommendation: `" + RECOMMENDATION + "`\n\n"
                + "개별 Matchup contribution의 적용 시각·position·context가 structured diagnostics에 보존되지 않으므로, "
                + "combat/pressure/economy와 structure divergence의 시간 순서를 인과로 해석하지 않았다. "
                + "새 V9 수치 threshold는 이 calibration 결과에 맞춰 발명하지 않았으며 product decision과 별도 fresh holdout이 필요하다.\n";
    }

    private static String jsonl(ObjectMapper mapper, List<PairRow> pairs) throws IOException {
        StringBuilder result = new StringBuilder();
        for (PairRow row : pairs) result.append(mapper.writeValueAsString(row)).append('\n');
        return result.toString();
    }

    private static String severityCsv(List<PairRow> pairs) {
        StringBuilder result = new StringBuilder(
                "fixture_id,pair_id,blue_team,red_team,seed_index,seed,primary_severity,labels,final_exact,hp_only,winner_changed,objective_changed,duration_delta_seconds\n");
        for (PairRow row : pairs) {
            result.append(row.fixtureId()).append(',').append(row.unorderedTeamPairId()).append(',')
                    .append(row.blueTeamCode()).append(',').append(row.redTeamCode()).append(',')
                    .append(row.seedIndex()).append(',').append(row.seed()).append(',')
                    .append(row.finalStructureComponents().primarySeverity()).append(',')
                    .append(csv(row.finalStructureComponents().severityLabels().toString())).append(',')
                    .append(row.finalStructureComponents().finalStructureStateExactEquality()).append(',')
                    .append(row.finalStructureComponents().hpOnlyDifference()).append(',')
                    .append(row.winnerChanged()).append(',').append(row.objectiveChanged()).append(',')
                    .append(row.durationDeltaSeconds()).append('\n');
        }
        return result.toString();
    }

    private static String csv(String value) {
        return '"' + value.replace("\"", "\"\"") + '"';
    }

    private static long count(List<PairRow> pairs, java.util.function.Predicate<PairRow> value) {
        return pairs.stream().filter(value).count();
    }

    private static double percentile(double[] values, double percentile) {
        if (values.length == 0) return 0.0;
        Arrays.sort(values);
        int index = (int) Math.ceil(percentile * values.length) - 1;
        return values[Math.max(0, Math.min(values.length - 1, index))];
    }

    private static void writeJson(ObjectMapper mapper, Path path, Object value) throws IOException {
        write(path, mapper.writeValueAsString(value) + "\n");
    }

    private static void write(Path path, String value) throws IOException {
        Files.writeString(path, value, StandardCharsets.UTF_8);
    }

    private static String shaManifest(Path output) throws IOException {
        List<Path> files;
        try (var walk = Files.walk(output)) {
            files = walk.filter(Files::isRegularFile)
                    .filter(path -> !path.getFileName().toString().equals("SHA256SUMS.txt"))
                    .sorted(Comparator.comparing(path -> output.relativize(path).toString()))
                    .toList();
        }
        StringBuilder result = new StringBuilder();
        for (Path file : files) result.append(MatchupV9StructureAttributionRunner.fileHash(file))
                .append("  ").append(output.relativize(file).toString().replace('\\', '/')).append('\n');
        return result.toString();
    }

    static void verifyManifest(Path output) throws IOException {
        for (String line : Files.readAllLines(output.resolve("SHA256SUMS.txt"),
                StandardCharsets.UTF_8)) {
            if (line.isBlank()) continue;
            int separator = line.indexOf("  ");
            Path file = output.resolve(line.substring(separator + 2));
            if (separator != 64 || !line.substring(0, separator).equals(
                    MatchupV9StructureAttributionRunner.fileHash(file))) {
                throw new IllegalStateException("Attribution manifest mismatch: " + file);
            }
        }
    }

    private static final class MutableConcentration {
        long exposure;
        long changed;
        long macro;
    }

    public record Concentration(
            long exposurePairCount,
            long anyFinalStateChangedPairCount,
            long progressionChangedPairCount,
            double anyFinalStateChangedRatePercent,
            double progressionChangedRatePercent
    ) { }

    public record LegacyChangedPair(
            String pairKey,
            String fixtureId,
            Phase13GB1AuditSchedule.FixtureLane fixtureLane,
            String unorderedTeamPairId,
            String blueTeamCode,
            String redTeamCode,
            int seedIndex,
            long seed,
            boolean winnerChanged,
            boolean objectiveChanged,
            int durationDeltaSeconds,
            int damageEventDelta,
            int destroyedEventDelta,
            int siegeStartedDelta,
            int siegeStoppedDelta,
            boolean firstTowerChanged,
            int baselineFirstTowerSeconds,
            int matchupFirstTowerSeconds,
            String baselineFirstTowerLane,
            String matchupFirstTowerLane,
            String baselineFirstTowerSource,
            String matchupFirstTowerSource
    ) { }

    public record LegacyAnalysis(
            String schemaVersion,
            String evidenceStatus,
            int pairedComparisonCount,
            int structureChangedPairCount,
            double structureChangedRatePercent,
            String exactLegacyMeaning,
            long winnerChangedWithinStructureChanged,
            long objectiveChangedWithinStructureChanged,
            double meanDurationDeltaWithinStructureChangedSeconds,
            long damageEventDeltaWithinStructureChanged,
            long destroyedEventDeltaWithinStructureChanged,
            long siegeStartedDeltaWithinStructureChanged,
            long siegeStoppedDeltaWithinStructureChanged,
            long firstTowerChangedWithinStructureChanged,
            Map<String, Long> fixtureConcentration,
            Map<String, Long> teamExposureConcentration,
            Map<String, Long> baselineFirstTowerLaneConcentration,
            List<LegacyChangedPair> changedPairs,
            List<String> unavailableFromPredecessorRows
    ) {
        public LegacyAnalysis {
            fixtureConcentration = Map.copyOf(fixtureConcentration);
            teamExposureConcentration = Map.copyOf(teamExposureConcentration);
            baselineFirstTowerLaneConcentration = Map.copyOf(baselineFirstTowerLaneConcentration);
            changedPairs = List.copyOf(changedPairs);
            unavailableFromPredecessorRows = List.copyOf(unavailableFromPredecessorRows);
        }
    }
}
