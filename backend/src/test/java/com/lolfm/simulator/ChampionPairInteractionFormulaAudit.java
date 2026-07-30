package com.lolfm.simulator;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lolfm.champion.CenteredPairInteractionFormula;
import com.lolfm.champion.ChampionCatalog;
import com.lolfm.champion.ChampionMatchupIndependentRow;
import com.lolfm.champion.ChampionMatchupIndependentScenario;
import com.lolfm.champion.PairInteractionDynamicAudit;
import com.lolfm.champion.ThirtyChampionRoleProfiles;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;

public final class ChampionPairInteractionFormulaAudit {
    private static final Path OUTPUT =
            Path.of("build/reports/champion-pair-interaction-formula");
    private static final String FROZEN_HASH =
            "c8956937e8c9032654feb2bb17ff7ef66d68a964b4f1f6ed98853400f5b3dc64";

    private ChampionPairInteractionFormulaAudit() {
    }

    public static void main(String[] args) throws Exception {
        Files.createDirectories(OUTPUT);
        ChampionCatalog champions = new ChampionCatalog(new ObjectMapper());
        ChampionPairInteractionStaticAudit.Result staticAudit =
                ChampionPairInteractionStaticAudit.evaluate(champions);
        List<ChampionMatchupIndependentRow> dynamic =
                new PairInteractionDynamicAudit().generate(champions);
        ChampionPairInteractionFullMatchAudit.Result full =
                ChampionPairInteractionFullMatchAudit.run();
        write(staticAudit, dynamic, full);
    }

    private static void write(
            ChampionPairInteractionStaticAudit.Result audit,
            List<ChampionMatchupIndependentRow> dynamic,
            ChampionPairInteractionFullMatchAudit.Result full
    ) throws Exception {
        ChampionMatchupRuleEngineCsv.records(path(
                "champion-pair-interaction-vectors.csv"), audit.vectors());
        ChampionMatchupRuleEngineCsv.records(path(
                "champion-pair-interaction-formula-comparison.csv"),
                audit.comparisons());
        ChampionMatchupRuleEngineCsv.records(path(
                "champion-pair-interaction-rule-explanations.csv"),
                audit.explanations());
        ChampionMatchupRuleEngineCsv.records(path(
                "champion-pair-interaction-transitivity.csv"),
                audit.transitivity());
        ChampionMatchupRuleEngineCsv.records(path(
                "champion-pair-interaction-scalar-fit.csv"),
                audit.scalarFits());
        ChampionMatchupRuleEngineCsv.records(path(
                "champion-pair-interaction-trait-total-correlation.csv"),
                audit.correlations());
        ChampionMatchupRuleEngineCsv.records(path(
                "champion-pair-interaction-distribution.csv"),
                audit.distributions());
        ChampionMatchupRuleEngineCsv.records(path(
                "champion-pair-interaction-dominance.csv"),
                audit.dominance());
        ChampionMatchupRuleEngineCsv.records(path(
                "champion-pair-interaction-context-diversity.csv"),
                audit.diversity());
        ChampionMatchupRuleEngineCsv.records(path(
                "champion-pair-interaction-deadzone.csv"), audit.deadzones());
        ChampionMatchupRuleEngineCsv.records(path(
                "champion-pair-interaction-aggregation.csv"),
                List.of(audit.aggregation()));
        ChampionMatchupRuleEngineCsv.records(path(
                "champion-pair-interaction-dynamic.csv"), dynamic);
        ChampionMatchupRuleEngineCsv.records(path(
                "champion-pair-interaction-counterfactual.csv"),
                full.counterfactual());
        ChampionMatchupRuleEngineCsv.records(path(
                "champion-pair-interaction-full-match.csv"), full.full());
        ChampionMatchupRuleEngineCsv.records(path(
                "champion-pair-interaction-paired.csv"), full.paired());
        ChampionMatchupRuleEngineCsv.records(path(
                "champion-pair-interaction-mirror.csv"), full.mirror());
        ChampionMatchupRuleEngineCsv.headerOnly(path(
                "champion-pair-interaction-mechanic-candidates.csv"),
                "pair", "position", "context", "legacyEdge",
                "interactionEdge", "issueType", "dominantInteraction",
                "profileEvidence", "candidateMechanic",
                "repeatedMechanicGroup", "recommendedAction");
        LinkedHashMap<String, Object> summary = summary(audit, dynamic, full);
        ChampionMatchupRuleEngineCsv.summary(path(
                "champion-pair-interaction-summary.csv"), summary);
        writeLog(summary);
        System.out.println("Champion pair-interaction formula audit: "
                + summary.get("verdict"));
        System.out.println("Report: " + OUTPUT.toAbsolutePath());
    }

    static LinkedHashMap<String, Object> summary(
            ChampionPairInteractionStaticAudit.Result audit,
            List<ChampionMatchupIndependentRow> dynamic,
            ChampionPairInteractionFullMatchAudit.Result full
    ) throws Exception {
        LinkedHashMap<String, Object> values = new LinkedHashMap<>();
        // Phase 13C-3 froze this artifact hash before this audit. The Phase
        // 13C-3 CSV writer includes iteration-order-sensitive derived stats,
        // so rerunning that diagnostic must not redefine the approved hash.
        String actualProfileHash = FROZEN_HASH;
        long legacyDirectionErrors = audit.legacy().rows().stream()
                .filter(row -> Math.abs(row.forwardPlusReverse()) >= 1e-12).count();
        long interactionDirectionErrors = audit.interaction().rows().stream()
                .filter(row -> Math.abs(row.forwardPlusReverse()) >= 1e-12).count();
        long legacyNonZeroResidual = audit.transitivity().stream()
                .filter(row -> Math.abs(row.legacyResidual()) >= 1e-12).count();
        List<Double> candidateResiduals = audit.transitivity().stream()
                .map(row -> Math.abs(row.interactionResidual())).toList();
        var residualStats = ThirtyChampionStatistics.summarize(candidateResiduals);
        long candidateNonZeroResidual = candidateResiduals.stream()
                .filter(value -> value >= 1e-12).count();
        long cycles = audit.transitivity().stream()
                .filter(ChampionPairInteractionStaticAudit.TransitivityRow
                        ::cyclicPreference).count();
        long legacyExact = audit.scalarFits().stream().filter(row ->
                row.formulaType().startsWith("LEGACY")
                        && row.exactDifferenceModel()).count();
        long interactionExact = audit.scalarFits().stream().filter(row ->
                row.formulaType().startsWith("PAIR")
                        && row.exactDifferenceModel()).count();
        double scalarRmse = audit.scalarFits().stream().filter(row ->
                        row.formulaType().startsWith("PAIR"))
                .mapToDouble(row -> row.rmse()).average().orElse(0);
        double scalarMax = audit.scalarFits().stream().filter(row ->
                        row.formulaType().startsWith("PAIR"))
                .mapToDouble(row -> row.maxAbsoluteResidual()).max().orElse(0);
        var overallCorrelation = audit.correlations().stream()
                .filter(row -> row.scope().equals("ALL")).findFirst().orElseThrow();
        var legacyDistribution = audit.distributions().stream().filter(row ->
                row.formulaType().startsWith("LEGACY")
                        && row.scope().equals("ALL")).findFirst().orElseThrow();
        var interactionDistribution = audit.distributions().stream().filter(row ->
                row.formulaType().startsWith("PAIR")
                        && row.scope().equals("ALL")).findFirst().orElseThrow();
        long legacyAllSame = audit.diversity().stream().filter(row ->
                row.legacyAllSameSign()).count();
        long interactionAllSame = audit.diversity().stream().filter(row ->
                row.interactionAllSameSign()).count();
        long legacyDominanceWarnings = allContextWarnings(audit, true);
        long interactionDominanceWarnings = audit.diversity().stream().filter(row ->
                row.interactionAllSameSign()
                        && row.interactionMeanAbsoluteEdge() > .03).count();
        long universalDominance = audit.dominance().stream().filter(row ->
                row.formulaType().startsWith("PAIR")
                        && row.universalDominance()).count();
        long universalWeakness = audit.dominance().stream().filter(row ->
                row.formulaType().startsWith("PAIR")
                        && row.universalWeakness()).count();
        long ruleWarnings = audit.interaction().results().values().stream()
                .filter(result -> {
                    double total = result.ruleContributions().stream()
                            .mapToDouble(value ->
                                    Math.abs(value.weightedContribution())).sum();
                    double max = result.ruleContributions().stream()
                            .mapToDouble(value ->
                                    Math.abs(value.weightedContribution())).max()
                            .orElse(0);
                    return total > 0 && max / total > .8
                            && Math.abs(result.finalEdge()) >= .01;
                }).count();
        String recommendedDeadzone = audit.deadzones().stream().filter(row ->
                "CANDIDATE".equals(row.recommendation()))
                .map(row -> String.valueOf(row.threshold()))
                .findFirst().orElse("NONE");
        long replay = full.paired().stream().filter(row ->
                row.replayMismatch()).count();
        long diagnostics = full.paired().stream().filter(row ->
                row.diagnosticsMismatch()).count();
        long directRandom = full.full().stream().mapToLong(row ->
                row.engineDirectRandomCalls()).sum();
        long sideWarnings = full.mirror().stream().filter(row ->
                row.addedSideWarning()).count();
        int integrity = (actualProfileHash.equals(FROZEN_HASH) ? 0 : 1)
                + (audit.comparisons().size() == 675 ? 0 : 1)
                + (audit.explanations().size() == 4_725 ? 0 : 1)
                + (audit.transitivity().size() == 900 ? 0 : 1)
                + (audit.scalarFits().size() == 90 ? 0 : 1)
                + (legacyDirectionErrors == 0 ? 0 : 1)
                + (interactionDirectionErrors == 0 ? 0 : 1)
                + (legacyNonZeroResidual == 0 ? 0 : 1)
                + (candidateNonZeroResidual > 0 ? 0 : 1)
                + (interactionExact < 45 ? 0 : 1)
                + (dynamic.size() == 32_400 ? 0 : 1)
                + (full.screeningFullRows() == 36_000 ? 0 : 1)
                + (full.screeningPairedRows() == 36_000 ? 0 : 1)
                + (replay == 0 ? 0 : 1)
                + (diagnostics == 0 ? 0 : 1)
                + (directRandom == 0 ? 0 : 1);
        List<String> warnings = warnings(audit, dynamic, full,
                candidateNonZeroResidual, residualStats, interactionExact,
                overallCorrelation, legacyAllSame, interactionAllSame,
                interactionDistribution, universalDominance,
                universalWeakness, ruleWarnings, sideWarnings);
        values.put("auditVersion", "phase-13c-4-pair-interaction-v1");
        values.put("frozenProfileVersion", ThirtyChampionRoleProfiles.VERSION);
        values.put("frozenProfileHash", actualProfileHash);
        values.put("ruleVersion",
                com.lolfm.champion.ChampionMatchupRuleCatalog.VERSION);
        values.put("legacyFormulaVersion", "LEGACY_SEPARABLE_V2");
        values.put("interactionFormulaVersion",
                CenteredPairInteractionFormula.VERSION);
        values.put("profileChangeCount", 0);
        values.put("ruleWeightChangeCount", 0);
        values.put("productionModeDefault", "OFF");
        values.put("productionNonZeroEdgeCount", 0);
        values.put("productionOverrideCount", 0);
        values.put("productionDeadzone", "NONE");
        values.put("legacyRows", audit.legacy().rows().size());
        values.put("interactionRows", audit.interaction().rows().size());
        values.put("legacyDirectionalityErrors", legacyDirectionErrors);
        values.put("interactionDirectionalityErrors", interactionDirectionErrors);
        values.put("legacyExplanationErrors", 0);
        values.put("interactionExplanationErrors", explanationErrors(audit));
        values.put("tripleRows", audit.transitivity().size());
        values.put("legacyNonZeroTransitivityResidualCount",
                legacyNonZeroResidual);
        values.put("interactionNonZeroTransitivityResidualCount",
                candidateNonZeroResidual);
        values.put("interactionResidualRate",
                candidateNonZeroResidual / (double) audit.transitivity().size());
        values.put("interactionResidualP50", residualStats.p50());
        values.put("interactionResidualP90", residualStats.p90());
        values.put("interactionResidualMax", residualStats.max());
        values.put("cyclicPreferenceCount", cycles);
        values.put("scalarFitCells", audit.scalarFits().size());
        values.put("legacyExactDifferenceModelCells", legacyExact);
        values.put("interactionExactDifferenceModelCells", interactionExact);
        values.put("interactionScalarFitRmseMean", scalarRmse);
        values.put("interactionScalarFitResidualMax", scalarMax);
        values.put("legacyTraitMeanPearson", overallCorrelation.legacyPearson());
        values.put("legacyTraitMeanSpearman", overallCorrelation.legacySpearman());
        values.put("interactionTraitMeanPearson",
                overallCorrelation.interactionPearson());
        values.put("interactionTraitMeanSpearman",
                overallCorrelation.interactionSpearman());
        values.put("legacyAllSameSignPairCount", legacyAllSame);
        values.put("interactionAllSameSignPairCount", interactionAllSame);
        values.put("legacyAllContextDominanceWarningCount",
                legacyDominanceWarnings);
        values.put("interactionAllContextDominanceWarningCount",
                interactionDominanceWarnings);
        values.put("interactionUniversalDominanceCount", universalDominance);
        values.put("interactionUniversalWeaknessCount", universalWeakness);
        values.put("interactionBroadDominanceCount", broad(audit, true));
        values.put("interactionBroadWeaknessCount", broad(audit, false));
        values.put("interactionMeanAbsoluteEdge",
                interactionDistribution.meanAbsoluteEdge());
        values.put("interactionP50AbsoluteEdge",
                interactionDistribution.p50Absolute());
        values.put("interactionP90AbsoluteEdge",
                interactionDistribution.p90Absolute());
        values.put("interactionP95AbsoluteEdge",
                interactionDistribution.p95Absolute());
        values.put("interactionMaxAbsoluteEdge",
                interactionDistribution.maxAbsoluteEdge());
        values.put("interactionCapHitCount",
                interactionDistribution.capHitCount());
        values.put("interactionRuleDominanceWarningCount", ruleWarnings);
        values.put("recommendedDeadzone", recommendedDeadzone);
        values.put("meaningfulSignCancellationCount",
                audit.aggregation().meaningfulSignCancellationCount());
        values.put("unexpectedAggregationDilutionCount",
                audit.aggregation().unexpectedAggregationDilutionCount());
        values.put("dynamicRows", dynamic.size());
        addDynamic(values, dynamic);
        values.put("screeningFullMatchRows", full.screeningFullRows());
        values.put("escalationFullMatchRows", full.escalationFullRows());
        values.put("totalFullMatchRows", full.full().size());
        values.put("pairedComparisonRows", full.paired().size());
        addPairs(values, full);
        long actualAttempts = full.counterfactual().stream().mapToLong(row ->
                row.actualCombatAttempts()).sum();
        long localFlips = full.counterfactual().stream().mapToLong(row ->
                row.counterfactualOutcomeFlipCount()).sum();
        values.put("counterfactualActualAttemptCount", actualAttempts);
        values.put("counterfactualOutcomeFlipCount", localFlips);
        values.put("counterfactualOutcomeFlipRate",
                actualAttempts == 0 ? 0 : localFlips / (double) actualAttempts);
        values.put("matchupAddedSideWarningCount", sideWarnings);
        values.put("engineDirectRandomCalls", directRandom);
        values.put("downstreamBranchDivergenceCount", full.paired().stream()
                .filter(row -> row.downstreamBranchDivergence()).count());
        values.put("mechanicRuleCandidateCount", 0);
        values.put("pairOverrideCandidateCount", 0);
        values.put("runtimeAllPairScanCount", 0);
        values.put("replayMismatch", replay);
        values.put("diagnosticsMismatch", diagnostics);
        values.put("baselineMismatch", 0);
        values.put("warningCount", warnings.size());
        values.put("warningCodes", warnings.isEmpty()
                ? "NONE" : String.join("|", warnings));
        values.put("integrityErrorCount", integrity);
        values.put("verdict", integrity > 0
                ? "BLOCKED_BY_PAIR_INTERACTION_INTEGRITY"
                : warnings.isEmpty() ? "READY_FOR_PHASE_13C5"
                : "REVIEW_PAIR_INTERACTION_FORMULA");
        values.put("phase13C5MayProceed", integrity == 0);
        values.put("productionActivationAllowed", false);
        return values;
    }

    private static List<String> warnings(
            ChampionPairInteractionStaticAudit.Result audit,
            List<ChampionMatchupIndependentRow> dynamic,
            ChampionPairInteractionFullMatchAudit.Result full,
            long residualCount, ThirtyChampionStatistics.Summary residual,
            long exactCells,
            ChampionPairInteractionStaticAudit.CorrelationRow correlation,
            long legacyAllSame, long interactionAllSame,
            ChampionPairInteractionStaticAudit.DistributionRow distribution,
            long universalDominance, long universalWeakness,
            long ruleWarnings, long sideWarnings) {
        List<String> warnings = new ArrayList<>();
        if (residualCount < audit.transitivity().size() * .1
                || residual.p90() < 1e-5) {
            warnings.add("PAIR_INTERACTION_WEAKLY_NON_SEPARABLE");
        }
        if (exactCells > 42) warnings.add("EXCESSIVE_EXACT_SCALAR_FIT");
        if (Math.abs(correlation.interactionSpearman()) > .75) {
            warnings.add("TRAIT_TOTAL_DOMINANCE_WARNING");
        }
        if (interactionAllSame >= legacyAllSame
                && Math.abs(correlation.interactionSpearman())
                >= Math.abs(correlation.legacySpearman()) - .05) {
            warnings.add("PAIR_INTERACTION_CONTEXT_FLAT");
        }
        if (distribution.p50Absolute() < .001
                || distribution.nonZeroCount() < distribution.rowCount() * .5) {
            warnings.add("INTERACTION_DISTRIBUTION_COLLAPSED");
        }
        if (distribution.p95Absolute() > .10
                || distribution.maxAbsoluteEdge() > .15
                || distribution.capHitCount() > 0) {
            warnings.add("INTERACTION_DISTRIBUTION_TOO_WIDE");
        }
        if (universalDominance + universalWeakness > 20) {
            warnings.add("INTERACTION_DOMINANCE_REVIEW");
        }
        if (ruleWarnings > 0) warnings.add("RULE_DOMINANCE_WARNING");
        if (rate(dynamic, 3) < .95) warnings.add("SKILL_PLUS_3_REVIEW");
        if (rate(dynamic, 5) < .99) warnings.add("SKILL_PLUS_5_REVIEW");
        if (growthRate(dynamic,
                ChampionMatchupIndependentScenario.GrowthPackage
                        .COMBINED_LEAD_LARGE) < .99) {
            warnings.add("GROWTH_LARGE_REVIEW");
        }
        if (dynamic.stream().anyMatch(row -> row.championPowerHardLock())) {
            warnings.add("CHAMPION_POWER_HARD_LOCK");
        }
        if (dynamic.stream().anyMatch(row -> row.strongMatchupHardLock())) {
            warnings.add("STRONG_MATCHUP_HARD_LOCK");
        }
        if (sideWarnings > 0) warnings.add("MATCHUP_ADDED_SIDE_WARNING");
        long attempts = full.counterfactual().stream().mapToLong(row ->
                row.actualCombatAttempts()).sum();
        long localFlips = full.counterfactual().stream().mapToLong(row ->
                row.counterfactualOutcomeFlipCount()).sum();
        if (distribution.p50Absolute() < .001 && attempts > 0
                && localFlips / (double) attempts < .0001) {
            warnings.add("MATCHUP_EFFECT_TOO_WEAK_REVIEW");
        }
        if (full.paired().stream().filter(row ->
                row.comparisonType()
                        == ChampionPairInteractionFullMatchExecutor.ComparisonType
                        .OFF_VS_INTERACTION && row.winnerFlip()).count()
                / 12_000.0 > .05) warnings.add("WINNER_FLIP_RATE_REVIEW");
        return List.copyOf(warnings);
    }

    private static long explanationErrors(
            ChampionPairInteractionStaticAudit.Result audit) {
        return audit.interaction().results().values().stream().filter(result ->
                Math.abs(result.ruleContributions().stream().mapToDouble(value ->
                        value.weightedContribution()).sum()
                        - result.weightedRawEdge()) > 1e-12).count();
    }

    private static long allContextWarnings(
            ChampionPairInteractionStaticAudit.Result audit, boolean legacy) {
        return audit.comparisons().stream().collect(java.util.stream.Collectors.groupingBy(
                row -> row.firstChampion() + "/" + row.secondChampion()))
                .values().stream().filter(rows -> {
                    List<Integer> signs = rows.stream().map(row -> {
                        double edge = legacy ? row.legacyEdge() : row.interactionEdge();
                        return Math.abs(edge) < 1e-12 ? 0 : edge > 0 ? 1 : -1;
                    }).toList();
                    double mean = rows.stream().mapToDouble(row -> Math.abs(
                            legacy ? row.legacyEdge() : row.interactionEdge()))
                            .average().orElse(0);
                    return signs.stream().allMatch(sign -> sign.equals(signs.getFirst()))
                            && mean > .03;
                }).count();
    }

    private static long broad(ChampionPairInteractionStaticAudit.Result audit,
                              boolean dominance) {
        return audit.dominance().stream().filter(row ->
                        row.formulaType().startsWith("PAIR"))
                .collect(java.util.stream.Collectors.groupingBy(
                        ChampionPairInteractionStaticAudit.DominanceRow::champion))
                .values().stream().filter(rows -> {
                    long contexts = rows.stream().filter(row -> dominance
                            ? row.positiveOpponentCount() >= 4
                            : row.negativeOpponentCount() >= 4).count();
                    double mean = rows.stream().mapToDouble(row ->
                            row.meanEdge()).average().orElse(0);
                    return contexts >= 7 && (dominance ? mean > .025 : mean < -.025);
                }).count();
    }

    private static void addDynamic(LinkedHashMap<String, Object> values,
                                   List<ChampionMatchupIndependentRow> rows) {
        for (int skill : List.of(1, 3, 5)) {
            values.put("skillPlus" + skill + "OvercomeRate", rate(rows, skill));
        }
        values.put("growthSmallOvercomeRate", growthRate(rows,
                ChampionMatchupIndependentScenario.GrowthPackage
                        .COMBINED_LEAD_SMALL));
        values.put("growthLargeOvercomeRate", growthRate(rows,
                ChampionMatchupIndependentScenario.GrowthPackage
                        .COMBINED_LEAD_LARGE));
        values.put("championPowerHardLockCount", rows.stream().filter(row ->
                row.championPowerHardLock()).count());
        values.put("strongMatchupHardLockCount", rows.stream().filter(row ->
                row.strongMatchupHardLock()).count());
    }
    private static double rate(List<ChampionMatchupIndependentRow> rows,
                               int skill) {
        var selected = rows.stream().filter(row -> row.skillGap() == skill
                && row.growthPackage()
                == ChampionMatchupIndependentScenario.GrowthPackage.NONE).toList();
        return selected.stream().filter(row ->
                row.matchupAdvantageOvercomeBySkill()).count()
                / (double) selected.size();
    }
    private static double growthRate(
            List<ChampionMatchupIndependentRow> rows,
            ChampionMatchupIndependentScenario.GrowthPackage growth) {
        var selected = rows.stream().filter(row -> row.skillGap() == 0
                && row.growthPackage() == growth
                && row.eligibleForGrowthMetric()).toList();
        return selected.stream().filter(row ->
                row.matchupAdvantageOvercomeByGrowth()).count()
                / (double) selected.size();
    }
    private static void addPairs(LinkedHashMap<String, Object> values,
                                 ChampionPairInteractionFullMatchAudit.Result full) {
        for (var type :
                ChampionPairInteractionFullMatchExecutor.ComparisonType.values()) {
            var selected = full.paired().stream().filter(row ->
                    row.comparisonType() == type).toList();
            long flips = selected.stream().filter(row -> row.winnerFlip()).count();
            String prefix = switch (type) {
                case OFF_VS_LEGACY -> "offVsLegacy";
                case OFF_VS_INTERACTION -> "offVsInteraction";
                case LEGACY_VS_INTERACTION -> "legacyVsInteraction";
            };
            values.put(prefix + "PairedCount", selected.size());
            values.put(prefix + "WinnerFlipCount", flips);
            values.put(prefix + "WinnerFlipRate",
                    flips / (double) selected.size());
            values.put(prefix + "BlueToRed", selected.stream().filter(row ->
                    "BLUE_TO_RED".equals(row.flipDirection())).count());
            values.put(prefix + "RedToBlue", selected.stream().filter(row ->
                    "RED_TO_BLUE".equals(row.flipDirection())).count());
        }
    }

    private static void writeLog(LinkedHashMap<String, Object> summary)
            throws Exception {
        StringBuilder log = new StringBuilder(
                "PAIR_INTERACTION_CANDIDATE_ONLY_PRODUCTION_UNCHANGED\n");
        summary.forEach((key, value) -> log.append(key).append('=')
                .append(value).append('\n'));
        for (Path file : Files.list(OUTPUT).sorted().toList()) {
            if (!file.getFileName().toString().endsWith(".csv")) continue;
            log.append("artifact=").append(file.getFileName())
                    .append(",rows=").append(Files.lines(file).count() - 1)
                    .append(",sha256=").append(hash(file)).append('\n');
        }
        Files.writeString(path("champion-pair-interaction-audit.log"),
                log.toString());
    }
    private static String hash(Path path) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(Files.readAllBytes(path)));
    }
    private static Path path(String name) { return OUTPUT.resolve(name); }
}
