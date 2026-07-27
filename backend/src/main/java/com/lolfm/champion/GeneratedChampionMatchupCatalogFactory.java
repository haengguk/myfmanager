package com.lolfm.champion;

import com.lolfm.domain.Position;
import com.lolfm.simulator.ProgressionCombatContext;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Builds the complete diagnostics matrix once; runtime only performs pair lookup. */
public final class GeneratedChampionMatchupCatalogFactory {
    public static final String CATALOG_VERSION =
            "diagnostics-generated-focused-10-v1";

    private GeneratedChampionMatchupCatalogFactory() {
    }

    public static BuildResult prototype(ChampionCatalog champions) {
        Objects.requireNonNull(champions, "champions");
        ChampionMatchupRuleEngine engine = new ChampionMatchupRuleEngine(
                ChampionRoleMatchupProfileCatalog.prototype(),
                new ChampionMatchupRuleCatalog(),
                ChampionMatchupOverrideCatalog.prototypeSemantic());
        List<ChampionMatchupProfile> matchupProfiles = new ArrayList<>();
        LinkedHashMap<MatrixKey, ChampionMatchupGeneratedResult> generated =
                new LinkedHashMap<>();
        for (Position position : Position.values()) {
            List<ChampionDefinition> pool = champions.forPosition(position);
            for (ChampionDefinition first : pool) {
                for (ChampionDefinition second : pool) {
                    if (first.id().value().compareTo(second.id().value()) >= 0) {
                        continue;
                    }
                    ChampionMatchupPair pair = ChampionMatchupPair.of(first, second);
                    LinkedHashMap<ProgressionCombatContext, Double> edges =
                            new LinkedHashMap<>();
                    for (ProgressionCombatContext context :
                            ProgressionCombatContext.values()) {
                        ChampionMatchupGeneratedResult result = engine.calculate(
                                new ChampionRoleKey(pair.first(), position),
                                new ChampionRoleKey(pair.second(), position), context);
                        edges.put(context, result.finalGeneratedEdge());
                        generated.put(new MatrixKey(pair, context), result);
                    }
                    matchupProfiles.add(new ChampionMatchupProfile(pair, edges));
                }
            }
        }
        ChampionMatchupCatalog catalog =
                ChampionMatchupCatalog.generatedDiagnosticsCatalog(
                        CATALOG_VERSION, champions, matchupProfiles);
        return new BuildResult(catalog, generated);
    }

    public record MatrixKey(
            ChampionMatchupPair pair,
            ProgressionCombatContext context
    ) {
    }

    public record BuildResult(
            ChampionMatchupCatalog catalog,
            Map<MatrixKey, ChampionMatchupGeneratedResult> generatedResults
    ) {
        public BuildResult {
            generatedResults = Map.copyOf(generatedResults);
        }
    }
}
