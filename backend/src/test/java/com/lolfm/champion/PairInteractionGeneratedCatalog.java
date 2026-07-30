package com.lolfm.champion;

import com.lolfm.domain.Position;
import com.lolfm.simulator.ProgressionCombatContext;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class PairInteractionGeneratedCatalog {
    public static final String VERSION =
            "diagnostics-pair-interaction-initial-30-v1";

    private PairInteractionGeneratedCatalog() {
    }

    public static BuildResult build(ChampionCatalog champions) {
        return build(champions, 1.0);
    }

    /** Builds a diagnostics-only catalog using one global post-formula gain. */
    public static BuildResult build(ChampionCatalog champions, double gain) {
        if (!Double.isFinite(gain) || gain <= 0) {
            throw new IllegalArgumentException("Interaction gain must be finite and positive");
        }
        var profiles = ThirtyChampionRoleProfiles.catalog();
        var formula = new CenteredPairInteractionFormula(
                new ChampionMatchupRuleCatalog());
        List<ChampionMatchupProfile> catalogProfiles = new ArrayList<>();
        List<Row> rows = new ArrayList<>(675);
        Map<Key, CenteredPairInteractionFormula.Result> results =
                new LinkedHashMap<>();
        for (Position position : Position.values()) {
            List<ChampionDefinition> pool = champions.forPosition(position);
            for (int left = 0; left < pool.size(); left++) {
                for (int right = left + 1; right < pool.size(); right++) {
                    ChampionMatchupPair pair =
                            ChampionMatchupPair.of(pool.get(left), pool.get(right));
                    ChampionRoleMatchupProfile first = profiles.find(
                            new ChampionRoleKey(pair.first(), position)).orElseThrow();
                    ChampionRoleMatchupProfile second = profiles.find(
                            new ChampionRoleKey(pair.second(), position)).orElseThrow();
                    LinkedHashMap<ProgressionCombatContext, Double> edges =
                            new LinkedHashMap<>();
                    for (ProgressionCombatContext context :
                            ProgressionCombatContext.values()) {
                        var forward = formula.evaluate(first, second, context);
                        var reverse = formula.evaluate(second, first, context);
                        double absoluteSum = forward.ruleContributions().stream()
                                .mapToDouble(value ->
                                        Math.abs(value.weightedContribution())).sum();
                        var dominant = forward.ruleContributions().stream()
                                .max(java.util.Comparator.comparingDouble(value ->
                                        Math.abs(value.weightedContribution())))
                                .orElseThrow();
                        double gainedEdge = clamp(forward.finalEdge() * gain);
                        double reverseEdge = clamp(reverse.finalEdge() * gain);
                        edges.put(context, gainedEdge);
                        results.put(new Key(pair, context), forward);
                        rows.add(new Row(position, pair.first().value(),
                                pair.second().value(), context, gainedEdge,
                                reverseEdge, gainedEdge + reverseEdge,
                                dominant.ruleType(),
                                absoluteSum == 0 ? 0 : Math.abs(
                                        dominant.weightedContribution()) / absoluteSum,
                                forward.clamped(), true));
                    }
                    catalogProfiles.add(new ChampionMatchupProfile(pair, edges));
                }
            }
        }
        return new BuildResult(
                ChampionMatchupCatalog.generatedDiagnosticsCatalog(
                        VERSION + (gain == 1.0 ? "" : "-gain-" + gain), champions, catalogProfiles),
                rows, results);
    }

    private static double clamp(double value) {
        double result = Math.max(-CenteredPairInteractionFormula.MAX_ABSOLUTE_EDGE,
                Math.min(CenteredPairInteractionFormula.MAX_ABSOLUTE_EDGE, value));
        return result == 0.0 ? 0.0 : result;
    }

    public record Key(ChampionMatchupPair pair,
                      ProgressionCombatContext context) { }
    public record Row(Position position, String firstChampion,
            String secondChampion, ProgressionCombatContext context,
            double interactionEdge, double reverseEdge,
            double forwardPlusReverse, ChampionMatchupRuleType dominantRule,
            double dominantRuleShare, boolean clamped,
            boolean candidateOnly) {
        public String pairId() { return firstChampion + "/" + secondChampion; }
    }
    public record BuildResult(ChampionMatchupCatalog catalog, List<Row> rows,
            Map<Key, CenteredPairInteractionFormula.Result> results) {
        public BuildResult {
            rows = List.copyOf(rows);
            results = Map.copyOf(results);
        }
    }
}
