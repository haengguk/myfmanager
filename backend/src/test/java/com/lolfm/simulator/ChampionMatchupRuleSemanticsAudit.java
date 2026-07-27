package com.lolfm.simulator;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lolfm.champion.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.stream.Collectors;

public final class ChampionMatchupRuleSemanticsAudit {
    private static final Path OUTPUT =
            Path.of("build/reports/champion-matchup-rule-semantics");

    private ChampionMatchupRuleSemanticsAudit() {
    }

    public static void main(String[] args) throws Exception {
        Files.createDirectories(OUTPUT);
        ChampionCatalog champions = new ChampionCatalog(new ObjectMapper());
        ChampionMatchupRuleCatalog rules = new ChampionMatchupRuleCatalog();
        var build = GeneratedChampionMatchupCatalogFactory.prototype(champions);
        ChampionMatchupRuleSemanticsStaticWriter.write(
                OUTPUT, rules, build);

        List<ChampionMatchupIndependentRow> independent =
                new ChampionMatchupIndependentOverrideAudit().generate();
        ChampionMatchupRuleEngineCsv.records(
                OUTPUT.resolve("champion-matchup-independent-override.csv"),
                independent);
        List<ChampionMatchupIndependentRow> flips = independent.stream()
                .filter(ChampionMatchupRuleSemanticsAudit::isLayerFlip)
                .toList();
        if (flips.isEmpty()) {
            ChampionMatchupRuleEngineCsv.headerOnly(
                    OUTPUT.resolve("champion-matchup-layer-flips.csv"),
                    "pairId", "position", "context", "state", "direction",
                    "scenarioGroup", "skillGap", "growthPackage",
                    "championPowerInducedFlip", "matchupInducedFlip",
                    "baseFormulaSkillHardLock", "baseFormulaGrowthHardLock",
                    "championPowerHardLock", "strongMatchupHardLock");
        } else {
            ChampionMatchupRuleEngineCsv.records(
                    OUTPUT.resolve("champion-matchup-layer-flips.csv"), flips);
        }

        ChampionMatchupRuleEngineAudit.MatchData matches =
                ChampionMatchupRuleEngineAudit.runMatches();
        ChampionMatchupRuleEngineCsv.records(
                OUTPUT.resolve("champion-matchup-rule-semantics-full-match.csv"),
                matches.full());
        ChampionMatchupRuleEngineCsv.records(
                OUTPUT.resolve("champion-matchup-rule-semantics-paired.csv"),
                matches.paired());
        List<ChampionMatchupRuleSemanticsMirrorAudit.MirrorRow> mirror =
                ChampionMatchupRuleSemanticsMirrorAudit.calculate(matches);
        ChampionMatchupRuleEngineCsv.records(
                OUTPUT.resolve("champion-matchup-rule-semantics-mirror.csv"),
                mirror);

        LinkedHashMap<String, Object> summary =
                ChampionMatchupRuleSemanticsVerdictEvaluator.evaluate(
                        independent, matches, mirror);
        ChampionMatchupRuleEngineCsv.summary(
                OUTPUT.resolve("champion-matchup-rule-semantics-summary.csv"),
                summary);
        String log = summary.entrySet().stream()
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .collect(Collectors.joining(System.lineSeparator()));
        Files.writeString(
                OUTPUT.resolve("champion-matchup-rule-semantics-audit.log"),
                "PROTOTYPE_DIAGNOSTICS_ONLY" + System.lineSeparator()
                        + log + System.lineSeparator());
        System.out.println("Champion matchup rule semantics audit: "
                + summary.get("verdict"));
        System.out.println("Report: " + OUTPUT.toAbsolutePath());
    }

    private static boolean isLayerFlip(ChampionMatchupIndependentRow row) {
        return row.championPowerInducedFlip()
                || row.matchupInducedFlip()
                || row.baseFormulaSkillHardLock()
                || row.baseFormulaGrowthHardLock()
                || row.championPowerHardLock()
                || row.strongMatchupHardLock();
    }
}
