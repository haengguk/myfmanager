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
                        edges.put(context, forward.finalEdge());
                        results.put(new Key(pair, context), forward);
                        rows.add(new Row(position, pair.first().value(),
                                pair.second().value(), context, forward.finalEdge(),
                                reverse.finalEdge(),
                                forward.finalEdge() + reverse.finalEdge(),
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
                        VERSION, champions, catalogProfiles), rows, results);
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
