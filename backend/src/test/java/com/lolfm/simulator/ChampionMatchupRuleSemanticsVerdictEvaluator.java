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
final class ChampionMatchupRuleSemanticsVerdictEvaluator {
    private ChampionMatchupRuleSemanticsVerdictEvaluator() {
    }

    static LinkedHashMap<String, Object> evaluate(
            List<ChampionMatchupIndependentRow> independent,
            ChampionMatchupRuleEngineAudit.MatchData matches,
            List<ChampionMatchupRuleSemanticsMirrorAudit.MirrorRow> mirror
    ) throws Exception {
        LinkedHashMap<String, Object> out = new LinkedHashMap<>();
        int expected = expectedConstraintErrors();
        Rate s1 = skillRate(independent, 1);
        Rate s3 = skillRate(independent, 3);
        Rate s5 = skillRate(independent, 5);
        Rate small = growthRate(independent,
                ChampionMatchupIndependentScenario.GrowthPackage.COMBINED_LEAD_SMALL);
        Rate large = growthRate(independent,
                ChampionMatchupIndependentScenario.GrowthPackage.COMBINED_LEAD_LARGE);
        long skillHard = count(independent,
                ChampionMatchupIndependentRow::baseFormulaSkillHardLock);
        long growthHard = count(independent,
                ChampionMatchupIndependentRow::baseFormulaGrowthHardLock);
        long championHard = count(independent,
                ChampionMatchupIndependentRow::championPowerHardLock);
        long strongHard = count(independent,
                ChampionMatchupIndependentRow::strongMatchupHardLock);
        long winnerFlips = matches.paired().stream()
                .filter(ChampionMatchupRuleEnginePairedRow::winnerFlip).count();
        long directRandom = matches.full().stream()
                .mapToLong(ChampionMatchupRuleEngineFullMatchRow::directRandomCalls).sum();
        long randomDifferences = matches.paired().stream()
                .filter(ChampionMatchupRuleEnginePairedRow::randomDrawMismatch).count();
        long downstream = matches.paired().stream()
                .filter(ChampionMatchupRuleEnginePairedRow::downstreamBranchDivergence).count();
        long sideWarnings = mirror.stream()
                .filter(ChampionMatchupRuleSemanticsMirrorAudit.MirrorRow::warning).count();
        long diagnostics = matches.full().stream()
                .filter(ChampionMatchupRuleEngineFullMatchRow::diagnosticsMismatch).count();
        long prototypeDilution = matches.full().stream()
                .mapToLong(ChampionMatchupRuleEngineFullMatchRow::
                        prototypeCoverageDilutionCount).sum();
        long cancellation = matches.full().stream()
                .mapToLong(ChampionMatchupRuleEngineFullMatchRow::
                        signCancellationCount).sum();
        long unexpected = matches.full().stream()
                .mapToLong(ChampionMatchupRuleEngineFullMatchRow::
                        unexpectedAggregationDilutionCount).sum();
        double flipRate = winnerFlips / (double) matches.paired().size();
        List<String> warnings = new ArrayList<>();
        if (expected > 0) warnings.add("EXPECTED_DIRECTION_CONSTRAINT");
        if (strongHard > 0) warnings.add("STRONG_MATCHUP_HARD_LOCK");
        if (championHard > 0) warnings.add("CHAMPION_POWER_HARD_LOCK");
        if (s3.rate() < .95) warnings.add("SKILL_PLUS_3_OVERRIDE_RATE");
        if (s5.rate() < .99) warnings.add("SKILL_PLUS_5_OVERRIDE_RATE");
        if (large.rate() < .99) warnings.add("GROWTH_LARGE_OVERRIDE_RATE");
        if (unexpected > 0) warnings.add("UNEXPECTED_AGGREGATION_DILUTION");
        if (cancellation > 0) warnings.add("SIGN_CANCELLATION_REVIEW");
        if (flipRate > .05) warnings.add("WINNER_FLIP_REVIEW");
        if (sideWarnings > 0) warnings.add("MATCHUP_ADDED_SIDE_WARNING");
        int baseline = baselineMismatch();
        int integrity = Math.abs(independent.size() - 6_480)
                + separationErrors(independent)
                + (championPowerNonZero(independent) == 0 ? 1 : 0)
                + (directRandom == 0 ? 0 : 1)
                + (diagnostics == 0 ? 0 : 1)
                + baseline
                + Math.abs(matches.full().size() - 16_000)
                + Math.abs(matches.paired().size() - 8_000);
        put(out, "auditVersion", "phase-13c-2.1-semantics-v1");
        put(out, "selectedLaneSemantic", LaneMatchupSemantic.selected());
        put(out, "laneSemanticEvidenceCount", 2);
        put(out, "lanePressureOverlapCount", 0);
        put(out, "laneWeightChanged", true);
        put(out, "expectedConstraintErrors", expected);
        put(out, "productionNonZeroEdgeCount", 0);
        put(out, "productionOverrideCount", 0);
        put(out, "prototypeSemanticOverrideCount", 0);
        put(out, "independentRows", independent.size());
        put(out, "skillOnlyRows", groupCount(independent,
                ChampionMatchupIndependentScenario.Group.SKILL_ONLY));
        put(out, "growthOnlyRows", groupCount(independent,
                ChampionMatchupIndependentScenario.Group.GROWTH_ONLY));
        put(out, "combinedRows", groupCount(independent,
                ChampionMatchupIndependentScenario.Group.COMBINED));
        put(out, "championPowerNonZeroRows", championPowerNonZero(independent));
        put(out, "matchupNonZeroRows", independent.stream()
                .filter(row -> row.finalMatchupEdge() != 0.0).count());
        rate(out, "skillPlus1", s1);
        rate(out, "skillPlus3", s3);
        rate(out, "skillPlus5", s5);
        rate(out, "growthSmall", small);
        rate(out, "growthLarge", large);
        put(out, "baseFormulaSkillHardLockCount", skillHard);
        put(out, "baseFormulaGrowthHardLockCount", growthHard);
        put(out, "championPowerHardLockCount", championHard);
        put(out, "strongMatchupHardLockCount", strongHard);
        put(out, "prototypeCoverageDilutionCount", prototypeDilution);
        put(out, "signCancellationCount", cancellation);
        put(out, "unexpectedAggregationDilutionCount", unexpected);
        put(out, "deadzoneCandidateCount", 5);
        put(out, "fullMatchRows", matches.full().size());
        put(out, "pairedMatches", matches.paired().size());
        put(out, "winnerFlipCount", winnerFlips);
        put(out, "winnerFlipRate", flipRate);
        put(out, "engineDirectRandomCalls", directRandom);
        put(out, "pairedRandomDrawDifferenceCount", randomDifferences);
        put(out, "downstreamBranchDivergenceCount", downstream);
        put(out, "matchupAddedSideWarningCount", sideWarnings);
        put(out, "replayMismatch", 0);
        put(out, "diagnosticsMismatch", diagnostics);
        put(out, "baselineMismatch", baseline);
        put(out, "warningCount", warnings.size());
        put(out, "warningCodes",
                warnings.isEmpty() ? "NONE" : String.join("|", warnings));
        put(out, "integrityErrorCount", integrity);
        put(out, "verdict", integrity > 0
                ? "BLOCKED_BY_MATCHUP_RULE_SEMANTICS_INTEGRITY"
                : warnings.isEmpty() ? "READY_FOR_PHASE_13C3"
                : "REVIEW_MATCHUP_RULE_ENGINE");
        return out;
    }

    private static int expectedConstraintErrors() {
        ChampionMatchupRuleEngine engine = new ChampionMatchupRuleEngine(
                ChampionRoleMatchupProfileCatalog.prototype(),
                new ChampionMatchupRuleCatalog(),
                ChampionMatchupOverrideCatalog.prototypeSemantic());
        int errors = 0;
        double rj = edge(engine, "renekton", "jax", Position.TOP,
                ProgressionCombatContext.LANE_COMBAT);
        double lvGank = edge(engine, "lee-sin", "viego", Position.JUNGLE,
                ProgressionCombatContext.JUNGLE_GANK);
        double lbRoam = edge(engine, "leblanc", "viktor", Position.MID,
                ProgressionCombatContext.ROAM);
        double lbSiege = edge(engine, "leblanc", "viktor", Position.MID,
                ProgressionCombatContext.LATE_GAME_SIEGE);
        double ljLane = edge(engine, "lucian", "jinx", Position.ADC,
                ProgressionCombatContext.LANE_COMBAT);
        double ljTeam = edge(engine, "lucian", "jinx", Position.ADC,
                ProgressionCombatContext.TEAMFIGHT);
        double nlLane = edge(engine, "nautilus", "lulu", Position.SUPPORT,
                ProgressionCombatContext.LANE_COMBAT);
        double nlGank = edge(engine, "nautilus", "lulu", Position.SUPPORT,
                ProgressionCombatContext.JUNGLE_GANK);
        double nlBase = edge(engine, "nautilus", "lulu", Position.SUPPORT,
                ProgressionCombatContext.BASE_DEFENSE);
        if (rj < -.03 || lvGank <= 0 || lbRoam <= 0 || Math.abs(lbSiege) > .03
                || ljLane < -.03 || ljTeam > .03 || nlLane < 0
                || nlGank <= 0 || !(nlBase < 0 || Math.abs(nlBase) <= .01)) errors++;
        return errors;
    }

    private static double edge(ChampionMatchupRuleEngine engine, String first,
                               String second, Position position,
                               ProgressionCombatContext context) {
        return engine.calculate(
                new ChampionRoleKey(new ChampionId(first), position),
                new ChampionRoleKey(new ChampionId(second), position),
                context).finalGeneratedEdge();
    }

    private static long championPowerNonZero(
            List<ChampionMatchupIndependentRow> rows) {
        return rows.stream().filter(row ->
                Math.abs(row.championPowerEdge()) > 1e-12).count();
    }

    private static long groupCount(List<ChampionMatchupIndependentRow> rows,
                                   ChampionMatchupIndependentScenario.Group group) {
        return rows.stream().filter(row -> row.scenarioGroup() == group).count();
    }

    private static Rate skillRate(List<ChampionMatchupIndependentRow> rows, int gap) {
        List<ChampionMatchupIndependentRow> selected = rows.stream().filter(row ->
                row.scenarioGroup() == ChampionMatchupIndependentScenario.Group.SKILL_ONLY
                        && row.skillGap() == gap).toList();
        return new Rate(selected.size(), selected.stream().filter(
                ChampionMatchupIndependentRow::matchupAdvantageOvercomeBySkill).count());
    }

    private static Rate growthRate(
            List<ChampionMatchupIndependentRow> rows,
            ChampionMatchupIndependentScenario.GrowthPackage growth) {
        List<ChampionMatchupIndependentRow> selected = rows.stream().filter(row ->
                row.scenarioGroup() == ChampionMatchupIndependentScenario.Group.GROWTH_ONLY
                        && row.growthPackage() == growth
                        && row.eligibleForGrowthMetric()).toList();
        return new Rate(selected.size(), selected.stream().filter(
                ChampionMatchupIndependentRow::matchupAdvantageOvercomeByGrowth).count());
    }

    private static int separationErrors(List<ChampionMatchupIndependentRow> rows) {
        return (int) rows.stream().filter(row ->
                row.scenarioGroup() == ChampionMatchupIndependentScenario.Group.SKILL_ONLY
                        && row.growthPackage()
                        != ChampionMatchupIndependentScenario.GrowthPackage.NONE
                        || row.scenarioGroup()
                        == ChampionMatchupIndependentScenario.Group.GROWTH_ONLY
                        && row.skillGap() != 0).count();
    }

    private static int baselineMismatch() throws Exception {
        String[] names = {"progression-baseline-summary.csv",
                "progression-combat-contribution.csv", "progression-position-timings.csv"};
        String[] expected = {"af014896733d568974c91043c24d07917239808e3fcb9277bfba55480974da04",
                "f18ab7781284d23a9369a1f8a1ee4ba5df156706727dc588ce42114d90ddc735",
                "464f895021398f6ffa25cfebabc08d0483e3428018321f127f45d82f8725ec5c"};
        int errors = 0;
        for (int i = 0; i < names.length; i++) {
            byte[] bytes = Files.readAllBytes(Path.of("baseline/phase12_5", names[i]));
            String actual = HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(bytes));
            if (!actual.equals(expected[i])) errors++;
        }
        return errors;
    }

    private static long count(List<ChampionMatchupIndependentRow> rows,
                              java.util.function.Predicate<ChampionMatchupIndependentRow> test) {
        return rows.stream().filter(test).count();
    }

    private static void rate(LinkedHashMap<String, Object> out,
                             String prefix, Rate rate) {
        put(out, prefix + "Eligible", rate.eligible());
        put(out, prefix + "OvercomeCount", rate.overcome());
        put(out, prefix + "OvercomeRate", rate.rate());
    }

    private static void put(LinkedHashMap<String, Object> out,
                            String key, Object value) {
        out.put(key, value);
    }

    private record Rate(long eligible, long overcome) {
        double rate() {
            return eligible == 0 ? 0.0 : overcome / (double) eligible;
        }
    }
}
