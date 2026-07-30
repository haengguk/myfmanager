package com.lolfm.champion;

import com.lolfm.domain.Position;
import com.lolfm.simulator.ProgressionCombatContext;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class ThirtyChampionGeneratedCatalog {
    public static final String VERSION =
            "diagnostics-generated-initial-30-candidate-v1";

    private ThirtyChampionGeneratedCatalog() {
    }

    public static BuildResult build(ChampionCatalog champions) {
        Objects.requireNonNull(champions, "champions");
        ChampionRoleMatchupProfileCatalog profiles =
                ThirtyChampionRoleProfiles.catalog();
        ChampionMatchupRuleEngine engine = new ChampionMatchupRuleEngine(
                profiles, new ChampionMatchupRuleCatalog(),
                ChampionMatchupOverrideCatalog.production());
        List<ChampionMatchupProfile> catalogProfiles = new ArrayList<>();
        List<MatrixRow> rows = new ArrayList<>();
        LinkedHashMap<GeneratedChampionMatchupCatalogFactory.MatrixKey,
                ChampionMatchupGeneratedResult> results = new LinkedHashMap<>();
        for (Position position : Position.values()) {
            List<ChampionDefinition> pool = champions.forPosition(position);
            for (int left = 0; left < pool.size(); left++) {
                for (int right = left + 1; right < pool.size(); right++) {
                    ChampionDefinition first = pool.get(left);
                    ChampionDefinition second = pool.get(right);
                    ChampionMatchupPair pair = ChampionMatchupPair.of(first, second);
                    LinkedHashMap<ProgressionCombatContext, Double> edges =
                            new LinkedHashMap<>();
                    for (ProgressionCombatContext context :
                            ProgressionCombatContext.values()) {
                        ChampionMatchupGeneratedResult forward = engine.calculate(
                                new ChampionRoleKey(pair.first(), position),
                                new ChampionRoleKey(pair.second(), position), context);
                        ChampionMatchupGeneratedResult reverse = engine.calculate(
                                new ChampionRoleKey(pair.second(), position),
                                new ChampionRoleKey(pair.first(), position), context);
                        edges.put(context, forward.finalGeneratedEdge());
                        results.put(new GeneratedChampionMatchupCatalogFactory.MatrixKey(
                                pair, context), forward);
                        rows.add(row(position, pair, context, forward, reverse));
                    }
                    catalogProfiles.add(new ChampionMatchupProfile(pair, edges));
                }
            }
        }
        ChampionMatchupCatalog catalog =
                ChampionMatchupCatalog.generatedDiagnosticsCatalog(
                        VERSION, champions, catalogProfiles);
        return new BuildResult(catalog, rows, results);
    }

    private static MatrixRow row(
            Position position, ChampionMatchupPair pair,
            ProgressionCombatContext context,
            ChampionMatchupGeneratedResult forward,
            ChampionMatchupGeneratedResult reverse
    ) {
        double total = forward.ruleContributions().stream()
                .mapToDouble(value -> Math.abs(value.weightedContribution())).sum();
        ChampionMatchupRuleContribution dominant =
                forward.ruleContributions().stream()
                        .max(java.util.Comparator.comparingDouble(value ->
                                Math.abs(value.weightedContribution())))
                        .orElseThrow();
        double share = total == 0 ? 0
                : Math.abs(dominant.weightedContribution()) / total;
        return new MatrixRow(position, pair.first().value(), pair.second().value(),
                context, forward.sourceProfileFound(),
                forward.opponentProfileFound(), forward.generatedBaseEdge(),
                reverse.generatedBaseEdge(),
                forward.generatedBaseEdge() + reverse.generatedBaseEdge(),
                Math.abs(forward.generatedBaseEdge()),
                Double.compare(forward.generatedBaseEdge(), 0), forward.clamped(),
                dominant.ruleType(), share, forward.ruleVersion(),
                forward.profileVersion(), true);
    }

    public record BuildResult(
            ChampionMatchupCatalog catalog,
            List<MatrixRow> rows,
            Map<GeneratedChampionMatchupCatalogFactory.MatrixKey,
                    ChampionMatchupGeneratedResult> generatedResults
    ) {
        public BuildResult {
            rows = List.copyOf(rows);
            generatedResults = Map.copyOf(generatedResults);
        }
    }

    public record MatrixRow(
            Position position, String canonicalFirstChampion,
            String canonicalSecondChampion, ProgressionCombatContext context,
            boolean firstProfileFound, boolean secondProfileFound,
            double generatedBaseEdge, double reverseEdge,
            double forwardPlusReverse, double absoluteEdge, int sign,
            boolean clamped, ChampionMatchupRuleType dominantRule,
            double dominantRuleShare, String ruleVersion,
            String profileVersion, boolean candidateOnly
    ) {
        public String pairId() {
            return canonicalFirstChampion + "/" + canonicalSecondChampion;
        }
    }
}
