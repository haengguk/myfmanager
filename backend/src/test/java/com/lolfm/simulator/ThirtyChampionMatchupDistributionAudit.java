package com.lolfm.simulator;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lolfm.champion.ChampionCatalog;
import com.lolfm.champion.ChampionMatchupIndependentRow;
import com.lolfm.champion.ThirtyChampionDynamicOverrideAudit;
import com.lolfm.champion.ThirtyChampionGeneratedCatalog;
import com.lolfm.champion.ThirtyChampionRoleProfiles;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;

public final class ThirtyChampionMatchupDistributionAudit {
    private static final Path OUTPUT =
            Path.of("build/reports/thirty-champion-matchup-distribution");

    private ThirtyChampionMatchupDistributionAudit() {
    }

    public static void main(String[] args) throws Exception {
        Files.createDirectories(OUTPUT);
        ChampionCatalog champions = new ChampionCatalog(new ObjectMapper());
        var profile = ThirtyChampionProfileAudit.evaluate(champions);
        var build = ThirtyChampionGeneratedCatalog.build(champions);
        var matrix = ThirtyChampionMatrixAudit.evaluate(build);
        var power = ThirtyChampionPowerDuplicationAudit.evaluate(build);
        List<ChampionMatchupIndependentRow> dynamic =
                new ThirtyChampionDynamicOverrideAudit().generate(champions);
        var full = ThirtyChampionFullMatchAudit.run();
        writeArtifacts(profile, build, matrix, power, dynamic, full, champions);
    }

    private static void writeArtifacts(
            ThirtyChampionProfileAudit.Result profile,
            ThirtyChampionGeneratedCatalog.BuildResult build,
            ThirtyChampionMatrixAudit.Result matrix,
            List<ThirtyChampionPowerDuplicationAudit.Row> power,
            List<ChampionMatchupIndependentRow> dynamic,
            ThirtyChampionFullMatchAudit.Result full,
            ChampionCatalog champions
    ) throws Exception {
        ThirtyChampionMatchupCsvWriter.profiles(
                path("thirty-champion-role-profiles.csv"), profile.entries());
        ThirtyChampionMatchupCsvWriter.rationales(
                path("thirty-champion-profile-rationale.csv"), profile.entries());
        ChampionMatchupRuleEngineCsv.headerOnly(
                path("thirty-champion-profile-changes.csv"),
                "champion", "position", "trait", "before", "after",
                "kitBasedReason");
        ChampionMatchupRuleEngineCsv.records(
                path("thirty-champion-position-trait-distribution.csv"),
                profile.positionTraitRows());
        ThirtyChampionMatchupCsvWriter.matrix(
                path("thirty-champion-generated-matrix.csv"), build.rows());
        ChampionMatchupRuleEngineCsv.records(
                path("thirty-champion-edge-distribution.csv"),
                matrix.distributions());
        ChampionMatchupRuleEngineCsv.records(
                path("thirty-champion-champion-summary.csv"),
                matrix.championSummaries());
        ChampionMatchupRuleEngineCsv.records(
                path("thirty-champion-pair-context-diversity.csv"),
                matrix.pairDiversity());
        ChampionMatchupRuleEngineCsv.records(
                path("thirty-champion-rule-dominance.csv"),
                matrix.ruleDominance());
        ChampionMatchupRuleEngineCsv.records(
                path("thirty-champion-power-duplication.csv"), power);
        ChampionMatchupRuleEngineCsv.records(
                path("thirty-champion-deadzone-candidates.csv"),
                matrix.deadzones());
        ChampionMatchupRuleEngineCsv.records(
                path("thirty-champion-dilution.csv"), List.of(matrix.dilution()));
        ChampionMatchupRuleEngineCsv.records(
                path("thirty-champion-dynamic-override.csv"), dynamic);
        ThirtyChampionMatchupCsvWriter.lineups(
                path("thirty-champion-round-robin-lineups.csv"),
                GeneratedMatchupRoundRobinLineupFactory.create(champions, "S0"));
        ChampionMatchupRuleEngineCsv.records(
                path("thirty-champion-full-match.csv"), full.full());
        ChampionMatchupRuleEngineCsv.records(
                path("thirty-champion-full-match-paired.csv"), full.paired());
        ThirtyChampionMatchupCsvWriter.mirror(
                path("thirty-champion-mirror.csv"), full.full());
        if (matrix.overrides().isEmpty()) {
            ChampionMatchupRuleEngineCsv.headerOnly(
                    path("thirty-champion-override-candidates.csv"),
                    "pairId", "position", "context", "generatedEdge",
                    "dominantRule", "dominantRuleShare", "candidateReason",
                    "recommendedAction");
        } else {
            ChampionMatchupRuleEngineCsv.records(
                    path("thirty-champion-override-candidates.csv"),
                    matrix.overrides());
        }
        LinkedHashMap<String, Object> summary = summary(
                profile, build, matrix, power, dynamic, full);
        ChampionMatchupRuleEngineCsv.summary(
                path("thirty-champion-matchup-summary.csv"), summary);
        writeLog(summary);
        System.out.println("Thirty champion matchup distribution audit: "
                + summary.get("verdict"));
        System.out.println("Report: " + OUTPUT.toAbsolutePath());
    }

    private static LinkedHashMap<String, Object> summary(
            ThirtyChampionProfileAudit.Result profile,
            ThirtyChampionGeneratedCatalog.BuildResult build,
            ThirtyChampionMatrixAudit.Result matrix,
            List<ThirtyChampionPowerDuplicationAudit.Row> power,
            List<ChampionMatchupIndependentRow> dynamic,
            ThirtyChampionFullMatchAudit.Result full
    ) {
        LinkedHashMap<String, Object> values = new LinkedHashMap<>();
        long directionErrors = build.rows().stream()
                .filter(row -> Math.abs(row.forwardPlusReverse()) > 1e-12).count();
        long fallback = build.rows().stream().filter(row ->
                !row.firstProfileFound() || !row.secondProfileFound()).count();
        long profileWarnings = profile.profileRows().stream().filter(row ->
                !row.warnings().isBlank()).count();
        long positionWarnings = profile.positionTraitRows().stream().filter(row ->
                !"NONE".equals(row.warnings())).count();
        long universalDominance = matrix.championSummaries().stream().filter(row ->
                row.allPositive() && row.meanEdge() > .03).count();
        long universalWeakness = matrix.championSummaries().stream().filter(row ->
                row.allNegative() && row.meanEdge() < -.03).count();
        long allContext = matrix.pairDiversity().stream().filter(row ->
                row.allSameSign() && row.neutralContextCount() == 0).count();
        long extreme = build.rows().stream().filter(row ->
                row.absoluteEdge() > .15).count();
        long ruleWarnings = matrix.ruleDominance().stream().filter(row ->
                row.dominantRuleShare() > .8
                        && Math.abs(row.generatedEdge()) >= .01).count();
        long hardLocks = dynamic.stream().filter(row ->
                row.championPowerHardLock() || row.strongMatchupHardLock()).count();
        long flips = full.paired().stream().filter(row -> row.winnerFlip()).count();
        long blueRed = full.paired().stream().filter(row ->
                "BLUE_TO_RED".equals(row.flipDirection())).count();
        long redBlue = full.paired().stream().filter(row ->
                "RED_TO_BLUE".equals(row.flipDirection())).count();
        long replay = full.paired().stream().filter(row ->
                row.replayMismatch()).count();
        long diagnostics = full.paired().stream().filter(row ->
                row.diagnosticsMismatch()).count();
        long directRandom = full.full().stream().mapToLong(row ->
                row.engineDirectRandomCalls()).sum();
        int integrity = (profile.catalogKeyCount() == 30 ? 0 : 1)
                + (build.rows().size() == 675 ? 0 : 1)
                + (fallback == 0 ? 0 : 1)
                + (directionErrors == 0 ? 0 : 1)
                + (dynamic.size() == 32_400 ? 0 : 1)
                + (full.screeningFullRows() == 12_000 ? 0 : 1)
                + (full.screeningPairs() == 6_000 ? 0 : 1)
                + (replay == 0 ? 0 : 1)
                + (diagnostics == 0 ? 0 : 1)
                + (directRandom == 0 ? 0 : 1);
        List<String> warnings = new ArrayList<>();
        if (profileWarnings > 0) warnings.add("PROFILE_QUALITY_REVIEW");
        if (positionWarnings > 0) warnings.add("POSITION_TRAIT_REVIEW");
        if (universalDominance + universalWeakness > 0) {
            warnings.add("UNIVERSAL_CONTEXT_REVIEW");
        }
        if (allContext > 0) warnings.add("PAIR_ALL_CONTEXT_DOMINANCE");
        if (extreme > 0) warnings.add("EXTREME_PAIR_EDGE");
        if (ruleWarnings > 0) warnings.add("RULE_DOMINANCE_WARNING");
        if (power.stream().anyMatch(row -> !"NONE".equals(row.warning()))) {
            warnings.add("ABSOLUTE_POWER_DUPLICATION_REVIEW");
        }
        if (hardLocks > 0) warnings.add("DYNAMIC_HARD_LOCK_REVIEW");
        values.put("auditVersion", "phase-13c-3-thirty-champion-v1");
        values.put("profileVersion", ThirtyChampionRoleProfiles.VERSION);
        values.put("candidateOnly", true);
        values.put("productionModeDefault", "OFF");
        values.put("productionNonZeroEdges", 0);
        values.put("productionOverrides", 0);
        values.put("profileCount", profile.entries().size());
        values.put("profilesByPosition", "TOP=6|JUNGLE=6|MID=6|ADC=6|SUPPORT=6");
        values.put("traitCount", 15);
        values.put("retainedPrototypeProfiles", 10);
        values.put("revisedPrototypeProfiles", 0);
        values.put("profileQualityWarnings", profileWarnings);
        values.put("positionTraitWarnings", positionWarnings);
        values.put("pairCount", build.rows().stream()
                .map(row -> row.pairId()).distinct().count());
        values.put("matrixRows", build.rows().size());
        values.put("bothProfilePairs", 75);
        values.put("neutralFallbackRows", fallback);
        values.put("nonZeroRows", build.rows().stream()
                .filter(row -> row.absoluteEdge() >= 1e-12).count());
        values.put("zeroRows", build.rows().stream()
                .filter(row -> row.absoluteEdge() < 1e-12).count());
        values.put("directionalityErrors", directionErrors);
        var edge = matrix.distributions().getFirst();
        values.put("edgeMeanAbsolute", edge.meanAbsoluteEdge());
        values.put("edgeP50Absolute", edge.medianAbsoluteEdge());
        values.put("edgeP90Absolute", edge.p90Absolute());
        values.put("edgeP95Absolute", edge.p95Absolute());
        values.put("edgeMaxAbsolute", edge.maxAbsoluteEdge());
        values.put("universalDominance", universalDominance);
        values.put("universalWeakness", universalWeakness);
        values.put("allContextDominance", allContext);
        values.put("extremeEdges", extreme);
        values.put("capHits", build.rows().stream()
                .filter(row -> row.clamped()).count());
        values.put("ruleDominanceWarnings", ruleWarnings);
        values.put("recommendedDeadzone", matrix.deadzones().stream()
                .filter(row -> "CANDIDATE".equals(row.recommendation()))
                .map(row -> String.valueOf(row.threshold())).findFirst().orElse("NONE"));
        values.put("coverageRatio", matrix.dilution().coverageRatio());
        values.put("coverageAttenuationError",
                matrix.dilution().coverageAttenuationError());
        values.put("dynamicRows", dynamic.size());
        addDynamic(values, dynamic);
        values.put("roundRobinLineups", 15);
        values.put("coveredPairs", 75);
        values.put("screeningFullMatchRows", full.screeningFullRows());
        values.put("escalationFullMatchRows", full.escalationFullRows());
        values.put("totalFullMatchRows", full.full().size());
        values.put("pairedRows", full.paired().size());
        values.put("winnerFlips", flips);
        values.put("winnerFlipRate", flips / (double) full.paired().size());
        values.put("blueToRedFlips", blueRed);
        values.put("redToBlueFlips", redBlue);
        values.put("engineDirectRandomCalls", directRandom);
        values.put("downstreamBranchDivergence", full.paired().stream()
                .filter(row -> row.downstreamBranchDivergence()).count());
        values.put("replayMismatch", replay);
        values.put("diagnosticsMismatch", diagnostics);
        values.put("overrideCandidates", matrix.overrides().size());
        var scale = ThirtyChampionScalabilityAudit.run();
        values.put("synthetic173ProfileCount", scale.profileCount());
        values.put("synthetic173GeneratedPairs", scale.generatedPairCount());
        values.put("synthetic173ProfileLookupNanos", scale.profileLookupNanos());
        values.put("synthetic173MatrixBuildNanos", scale.matrixBuildNanos());
        values.put("synthetic173EstimatedMemoryBytes", scale.estimatedMemoryBytes());
        values.put("singleCombatRuntimeLookupCount", scale.singleCombatLookupCount());
        values.put("runtimeAllPairScan", scale.runtimeAllPairScanCount());
        values.put("synthetic173Scalable", scale.profileCount() == 173
                && scale.runtimeAllPairScanCount() == 0);
        values.put("escalationCellCount", full.decisions().stream()
                .filter(ThirtyChampionFullMatchAudit.CellDecision::escalated).count());
        values.put("escalationCells", full.decisions().stream()
                .filter(ThirtyChampionFullMatchAudit.CellDecision::escalated)
                .map(cell -> cell.cellId() + "[" + cell.reasons() + "]")
                .collect(java.util.stream.Collectors.joining("|")));
        values.put("warningCodes", warnings.isEmpty()
                ? "NONE" : String.join("|", warnings));
        values.put("integrityErrorCount", integrity);
        values.put("verdict", integrity > 0 ? "BLOCKED"
                : warnings.isEmpty() ? "READY_FOR_PHASE_13C4"
                : "REVIEW_BEFORE_PHASE_13C4");
        values.put("phase13C4MayProceed", integrity == 0);
        return values;
    }

    private static void addDynamic(LinkedHashMap<String, Object> values,
                                   List<ChampionMatchupIndependentRow> rows) {
        for (int skill : List.of(1, 3, 5)) {
            List<ChampionMatchupIndependentRow> selected = rows.stream()
                    .filter(row -> row.skillGap() == skill
                            && row.growthPackage()
                            == com.lolfm.champion.ChampionMatchupIndependentScenario
                            .GrowthPackage.NONE).toList();
            long overcome = selected.stream().filter(row ->
                    row.matchupAdvantageOvercomeBySkill()).count();
            values.put("skillPlus" + skill + "Eligible", selected.size());
            values.put("skillPlus" + skill + "Overcome", overcome);
            values.put("skillPlus" + skill + "OvercomeRate",
                    overcome / (double) selected.size());
        }
        for (var growth : List.of(
                com.lolfm.champion.ChampionMatchupIndependentScenario.GrowthPackage
                        .COMBINED_LEAD_SMALL,
                com.lolfm.champion.ChampionMatchupIndependentScenario.GrowthPackage
                        .COMBINED_LEAD_LARGE)) {
            List<ChampionMatchupIndependentRow> selected = rows.stream()
                    .filter(row -> row.skillGap() == 0
                            && row.growthPackage() == growth
                            && row.eligibleForGrowthMetric()).toList();
            long overcome = selected.stream().filter(row ->
                    row.matchupAdvantageOvercomeByGrowth()).count();
            values.put(growth + "Eligible", selected.size());
            values.put(growth + "Overcome", overcome);
            values.put(growth + "OvercomeRate",
                    selected.isEmpty() ? 0 : overcome / (double) selected.size());
        }
        values.put("championPowerHardLock", rows.stream().filter(row ->
                row.championPowerHardLock()).count());
        values.put("strongMatchupHardLock", rows.stream().filter(row ->
                row.strongMatchupHardLock()).count());
    }

    private static void writeLog(LinkedHashMap<String, Object> summary)
            throws Exception {
        StringBuilder log = new StringBuilder(
                "CANDIDATE_ONLY_PRODUCTION_UNCHANGED\n");
        summary.forEach((key, value) -> log.append(key).append('=')
                .append(value).append('\n'));
        for (Path file : Files.list(OUTPUT).sorted().toList()) {
            if (!file.getFileName().toString().endsWith(".csv")) continue;
            byte[] bytes = Files.readAllBytes(file);
            log.append("artifact=").append(file.getFileName())
                    .append(",rows=").append(Files.lines(file).count() - 1)
                    .append(",sha256=").append(HexFormat.of().formatHex(
                            MessageDigest.getInstance("SHA-256").digest(bytes)))
                    .append('\n');
        }
        Files.writeString(path("thirty-champion-matchup-audit.log"),
                log.toString());
    }

    private static Path path(String name) { return OUTPUT.resolve(name); }
}
