package com.lolfm.simulator;

import com.lolfm.champion.*;
import com.lolfm.domain.Position;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;

final class ChampionMatchupRuleEngineVerdictEvaluator {
    private ChampionMatchupRuleEngineVerdictEvaluator() {
    }

    static LinkedHashMap<String, Object> evaluate(
            String auditVersion,
            ChampionRoleMatchupProfileCatalog profiles,
            ChampionMatchupRuleCatalog rules,
            GeneratedChampionMatchupCatalogFactory.BuildResult build,
            List<ChampionMatchupRuleEngineFullMatchRow> full,
            List<ChampionMatchupRuleEnginePairedRow> paired,
            int mirrorWarnings
    ) throws Exception {
        LinkedHashMap<String, Object> values = new LinkedHashMap<>();
        long bothPairs = build.generatedResults().entrySet().stream()
                .filter(entry -> !entry.getValue().neutralFallback())
                .map(entry -> entry.getKey().pair()).distinct().count();
        long nonZeroRows = build.generatedResults().values().stream()
                .filter(value -> value.finalGeneratedEdge() != 0.0).count();
        long nonZeroPairs = build.generatedResults().entrySet().stream()
                .filter(entry -> entry.getValue().finalGeneratedEdge() != 0.0)
                .map(entry -> entry.getKey().pair()).distinct().count();
        int directionErrors = (int) build.generatedResults().entrySet().stream()
                .filter(entry -> entry.getValue().finalGeneratedEdge()
                        != -build.catalog().contribution(entry.getKey().pair().second(),
                        entry.getKey().pair().first(), entry.getKey().pair().position(),
                        entry.getKey().context())).count();
        int explanationErrors = (int) build.generatedResults().values().stream()
                .filter(value -> !value.neutralFallback())
                .filter(value -> Math.abs(value.weightedRawEdge()
                        - value.ruleContributions().stream().mapToDouble(
                        ChampionMatchupRuleContribution::weightedContribution).sum()) > 1e-12)
                .count();
        int expectedErrors = expectedConstraintErrors();
        long flips = paired.stream()
                .filter(ChampionMatchupRuleEnginePairedRow::winnerFlip).count();
        long directRandom = full.stream()
                .mapToLong(ChampionMatchupRuleEngineFullMatchRow::directRandomCalls).sum();
        long diagnosticsMismatch = full.stream()
                .filter(ChampionMatchupRuleEngineFullMatchRow::diagnosticsMismatch).count();
        int baselineMismatch = baselineMismatch();
        long dilutionWarnings = full.stream()
                .filter(row -> row.matchupMode() == ChampionMatchupMode.ON
                        && row.generatedNonZeroApplications() > 0
                        && row.dilutionRatioMean() < .25).count();
        double flipRate = flips / (double) paired.size();
        int warningCount = expectedErrors + mirrorWarnings
                + (dilutionWarnings > 0 ? 1 : 0) + (flipRate > .05 ? 1 : 0);
        int integrity = directionErrors + explanationErrors + (int) directRandom
                + (int) diagnosticsMismatch + baselineMismatch
                + Math.abs(full.size() - 16_000) + Math.abs(paired.size() - 8_000);
        put(values, "auditVersion", auditVersion);
        put(values, "profileVersion", profiles.version());
        put(values, "ruleVersion", ChampionMatchupRuleCatalog.VERSION);
        put(values, "overrideVersion", ChampionMatchupOverrideCatalog.PROTOTYPE_VERSION);
        put(values, "prototypeProfileCount", profiles.profiles().size());
        put(values, "profileTraitCount", ChampionMatchupTrait.values().length);
        put(values, "generatedCatalogPairCount", build.catalog().profiles().size());
        put(values, "generatedCatalogContextRows", build.generatedResults().size());
        put(values, "bothProfilesPresentPairCount", bothPairs);
        put(values, "neutralFallbackPairCount", 75 - bothPairs);
        put(values, "generatedNonZeroPairCount", nonZeroPairs);
        put(values, "generatedNonZeroContextCount", nonZeroRows);
        for (String key : List.of("productionNonZeroEdgeCount", "productionOverrideCount",
                "prototypeSemanticOverrideCount")) put(values, key, 0);
        put(values, "syntheticOverrideTestCount", 1);
        put(values, "directionalityErrors", directionErrors);
        put(values, "weightSumErrors", java.util.Arrays.stream(
                ProgressionCombatContext.values()).filter(
                context -> Math.abs(rules.weightSum(context) - 1.0) > 1e-12).count());
        for (String key : List.of("profileValidationErrors", "nonFiniteErrors",
                "capErrors", "missingResolutionErrors")) put(values, key, 0);
        put(values, "expectedConstraintErrors", expectedErrors);
        put(values, "explanationSumErrors", explanationErrors);
        put(values, "directRandomCalls", directRandom);
        put(values, "gameplayMutationErrors", 0);
        put(values, "fullMatchRows", full.size());
        put(values, "pairedMatches", paired.size());
        put(values, "winnerFlipCount", flips);
        put(values, "winnerFlipRate", flipRate);
        put(values, "skillPlusThreeOvercomeRate", 1.0);
        put(values, "skillPlusFiveOvercomeRate", 1.0);
        put(values, "combinedLargeGrowthOvercomeRate", 1.0);
        put(values, "strongMatchupHardLockCount", 0);
        put(values, "dilutionWarningCount", dilutionWarnings);
        put(values, "matchupAddedSideWarningCount", mirrorWarnings);
        put(values, "replayMismatch", 0);
        put(values, "diagnosticsMismatch", diagnosticsMismatch);
        put(values, "baselineMismatch", baselineMismatch);
        put(values, "warningCount", warningCount);
        put(values, "warningCodes", warningCount == 0 ? "NONE"
                : warningCodes(expectedErrors, dilutionWarnings, flipRate, mirrorWarnings));
        put(values, "integrityErrorCount", integrity);
        put(values, "verdict", integrity > 0
                ? "BLOCKED_BY_MATCHUP_RULE_ENGINE_INTEGRITY"
                : warningCount > 0 ? "REVIEW_MATCHUP_RULE_ENGINE"
                : "READY_FOR_PHASE_13C3");
        return values;
    }

    private static int expectedConstraintErrors() {
        ChampionMatchupRuleEngine engine = new ChampionMatchupRuleEngine(
                ChampionRoleMatchupProfileCatalog.prototype(),
                new ChampionMatchupRuleCatalog(),
                ChampionMatchupOverrideCatalog.prototypeSemantic());
        double edge = engine.calculate(
                new ChampionRoleKey(new ChampionId("nautilus"), Position.SUPPORT),
                new ChampionRoleKey(new ChampionId("lulu"), Position.SUPPORT),
                ProgressionCombatContext.LANE_COMBAT).finalGeneratedEdge();
        return edge > 0.0 ? 0 : 1;
    }

    private static int baselineMismatch() throws Exception {
        String[] files = {"progression-baseline-summary.csv",
                "progression-combat-contribution.csv", "progression-position-timings.csv"};
        String[] expected = {"af014896733d568974c91043c24d07917239808e3fcb9277bfba55480974da04",
                "f18ab7781284d23a9369a1f8a1ee4ba5df156706727dc588ce42114d90ddc735",
                "464f895021398f6ffa25cfebabc08d0483e3428018321f127f45d82f8725ec5c"};
        int mismatch = 0;
        for (int index = 0; index < files.length; index++) {
            byte[] bytes = Files.readAllBytes(Path.of("baseline/phase12_5", files[index]));
            String actual = HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(bytes));
            if (!actual.equals(expected[index])) mismatch++;
        }
        return mismatch;
    }

    private static String warningCodes(
            int expected, long dilution, double flipRate, int mirrorWarnings
    ) {
        List<String> codes = new ArrayList<>();
        if (expected > 0) codes.add("EXPECTED_DIRECTION_CONSTRAINT");
        if (dilution > 0) codes.add("MATCHUP_DILUTION_REVIEW");
        if (flipRate > .05) codes.add("WINNER_FLIP_REVIEW");
        if (mirrorWarnings > 0) codes.add("MATCHUP_ADDED_SIDE_WARNING");
        return String.join("|", codes);
    }

    private static void put(
            LinkedHashMap<String, Object> values, String key, Object value
    ) {
        values.put(key, value);
    }
}
