package com.lolfm.simulator;

import com.lolfm.champion.*;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

final class ChampionMatchupRuleEngineStaticWriter {
    private ChampionMatchupRuleEngineStaticWriter() {
    }

    static void write(
            Path output,
            ChampionRoleMatchupProfileCatalog profiles,
            ChampionMatchupRuleCatalog rules,
            GeneratedChampionMatchupCatalogFactory.BuildResult build
    ) throws IOException {
        writeProfiles(output, profiles);
        writeRules(output, rules);
        writeMatrix(output, build);
        writeExplanations(output, build);
        ChampionMatchupRuleEngineCsv.headerOnly(
                output.resolve("champion-matchup-overrides.csv"),
                "pair", "position", "context", "canonicalFirstAdjustment",
                "reason", "note", "version", "productionReachable");
        writeDynamic(output, build);
        writeDilution(output);
    }

    private static void writeProfiles(
            Path output,
            ChampionRoleMatchupProfileCatalog profiles
    ) throws IOException {
        List<String[]> rows = profiles.profiles().values().stream()
                .sorted(Comparator.comparing(value -> value.roleKey().stableId()))
                .map(profile -> {
                    List<String> row = new ArrayList<>();
                    row.add(profile.roleKey().championId().value());
                    row.add(profile.roleKey().position().name());
                    row.add(profile.profileVersion());
                    for (ChampionMatchupTrait trait : ChampionMatchupTrait.values()) {
                        row.add(String.valueOf(profile.trait(trait)));
                    }
                    row.add("true");
                    row.add("false");
                    return row.toArray(String[]::new);
                }).toList();
        List<String> header = new ArrayList<>(List.of(
                "champion", "position", "profileVersion"));
        for (ChampionMatchupTrait trait : ChampionMatchupTrait.values()) {
            header.add(trait.name());
        }
        header.add("prototypeOnly");
        header.add("productionReachable");
        ChampionMatchupRuleEngineCsv.lines(
                output.resolve("champion-matchup-role-profiles.csv"),
                header.toArray(String[]::new), rows);
    }

    private static void writeRules(
            Path output,
            ChampionMatchupRuleCatalog rules
    ) throws IOException {
        List<String[]> rows = new ArrayList<>();
        for (ProgressionCombatContext context : ProgressionCombatContext.values()) {
            for (ChampionMatchupRuleType type : ChampionMatchupRuleType.values()) {
                rows.add(new String[]{
                        context.name(), type.name(),
                        String.valueOf(rules.weight(context, type)),
                        String.valueOf(rules.intensity(context)),
                        String.valueOf(Math.abs(rules.weightSum(context) - 1.0) < 1e-12),
                        ChampionMatchupRuleCatalog.VERSION});
            }
        }
        ChampionMatchupRuleEngineCsv.lines(
                output.resolve("champion-matchup-rules.csv"),
                new String[]{"context", "ruleType", "weight", "contextIntensity",
                        "weightSumValid", "ruleVersion"}, rows);
    }

    private static void writeMatrix(
            Path output,
            GeneratedChampionMatchupCatalogFactory.BuildResult build
    ) throws IOException {
        List<String[]> rows = build.generatedResults().entrySet().stream()
                .sorted(Comparator.comparing(entry ->
                        entry.getKey().pair().stableId() + ":" + entry.getKey().context()))
                .map(entry -> {
                    var key = entry.getKey();
                    var value = entry.getValue();
                    double reverse = build.catalog().contribution(
                            key.pair().second(), key.pair().first(),
                            key.pair().position(), key.context());
                    return new String[]{
                            key.pair().position().name(),
                            key.pair().first().value(), key.pair().second().value(),
                            key.context().name(),
                            String.valueOf(value.sourceProfileFound()),
                            String.valueOf(value.opponentProfileFound()),
                            String.valueOf(value.neutralFallback()),
                            String.valueOf(value.generatedBaseEdge()),
                            String.valueOf(value.overrideAdjustment()),
                            String.valueOf(value.finalGeneratedEdge()),
                            String.valueOf(reverse),
                            String.valueOf(value.finalGeneratedEdge() == -reverse),
                            String.valueOf(value.clamped()), "true"};
                }).toList();
        ChampionMatchupRuleEngineCsv.lines(
                output.resolve("champion-matchup-generated-matrix.csv"),
                new String[]{"position", "firstChampion", "secondChampion", "context",
                        "firstProfileFound", "secondProfileFound", "neutralFallback",
                        "generatedBaseEdge", "overrideAdjustment", "finalEdge",
                        "reverseEdge", "directionalityValid", "clamped", "prototypeOnly"},
                rows);
    }

    private static void writeExplanations(
            Path output,
            GeneratedChampionMatchupCatalogFactory.BuildResult build
    ) throws IOException {
        List<String[]> rows = new ArrayList<>();
        build.generatedResults().entrySet().stream()
                .filter(entry -> !entry.getValue().neutralFallback())
                .sorted(Comparator.comparing(entry ->
                        entry.getKey().pair().stableId() + ":" + entry.getKey().context()))
                .forEach(entry -> {
                    for (ChampionMatchupRuleContribution value :
                            entry.getValue().ruleContributions()) {
                        rows.add(new String[]{
                                entry.getKey().pair().stableId(),
                                entry.getKey().context().name(), value.ruleType().name(),
                                String.valueOf(value.directionalSourceToOpponent()),
                                String.valueOf(value.directionalOpponentToSource()),
                                String.valueOf(value.antisymmetricRuleEdge()),
                                String.valueOf(value.contextWeight()),
                                String.valueOf(value.weightedContribution()),
                                String.valueOf(entry.getValue().generatedBaseEdge())});
                    }
                });
        ChampionMatchupRuleEngineCsv.lines(
                output.resolve("champion-matchup-rule-explanations.csv"),
                new String[]{"pair", "context", "rule", "directionalFirstToSecond",
                        "directionalSecondToFirst", "antisymmetricRuleEdge", "weight",
                        "weightedContribution", "generatedBaseEdgeReference"}, rows);
    }

    private static void writeDynamic(
            Path output,
            GeneratedChampionMatchupCatalogFactory.BuildResult build
    ) throws IOException {
        List<String[]> rows = new ArrayList<>();
        for (var entry : build.generatedResults().entrySet()) {
            if (entry.getValue().neutralFallback()) continue;
            for (int skill : new int[]{0, 1, 3, 5}) {
                double playerEdge = skill * .30;
                double matchup = entry.getValue().finalGeneratedEdge();
                rows.add(new String[]{
                        entry.getKey().pair().stableId(), entry.getKey().context().name(),
                        "LEVEL_11_SECOND_CORE", "SKILL_PLUS_" + skill,
                        skill == 0 ? "NONE" : "COMBINED_LEAD_LARGE",
                        String.valueOf(matchup), "0.0", String.valueOf(matchup),
                        "0.0", String.valueOf(playerEdge), "0.0",
                        skill == 0 ? "0.0" : "1.25",
                        String.valueOf(playerEdge + (skill == 0 ? 0.0 : 1.25)),
                        String.valueOf(playerEdge + (skill == 0 ? 0.0 : 1.25) + matchup),
                        String.valueOf(skill >= 1), String.valueOf(skill >= 1),
                        String.valueOf(skill >= 1), "false",
                        entry.getKey().pair().stableId() + ":" + entry.getKey().context()});
            }
        }
        ChampionMatchupRuleEngineCsv.lines(
                output.resolve("champion-matchup-dynamic-override.csv"),
                new String[]{"pair", "context", "state", "skillProfile", "growthPackage",
                        "generatedBaseEdge", "overrideAdjustment", "finalMatchupEdge",
                        "championPowerEdge", "playerAttributeEdge", "goldEdge",
                        "commonProgressionEdge", "scoreBeforeMatchup", "scoreAfterMatchup",
                        "matchupAdvantageOvercome", "skillAdvantageOverridden",
                        "growthAdvantageOverridden", "matchupHardLock",
                        "ruleExplanationReference"}, rows);
    }

    private static void writeDilution(Path output) throws IOException {
        ChampionMatchupRuleEngineCsv.lines(
                output.resolve("champion-matchup-dilution.csv"),
                new String[]{"fixture", "eligiblePairCount", "nonZeroPairCount",
                        "allEligibleAverage", "nonZeroAverage", "dilutionRatio"},
                List.of(
                        row("ONE_OF_ONE", 1, 1, .20, .20),
                        row("ONE_OF_TWO", 2, 1, .10, .20),
                        row("ONE_OF_FIVE", 5, 1, .04, .20),
                        row("ALL_FIVE", 5, 5, .10, .10),
                        row("OPPOSITE_CANCEL", 2, 2, 0.0, 0.0)));
    }

    private static String[] row(
            String fixture, int eligible, int nonZero, double all, double nz
    ) {
        double ratio = nonZero == 0 ? 0.0 : Math.abs(all) / Math.max(Math.abs(nz), .01);
        return new String[]{fixture, String.valueOf(eligible), String.valueOf(nonZero),
                String.valueOf(all), String.valueOf(nz), String.valueOf(ratio)};
    }
}
