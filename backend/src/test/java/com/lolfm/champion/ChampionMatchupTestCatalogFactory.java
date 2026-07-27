package com.lolfm.champion;

import com.lolfm.domain.Position;
import com.lolfm.simulator.ProgressionCombatContext;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class ChampionMatchupTestCatalogFactory {
    private ChampionMatchupTestCatalogFactory() {
    }

    public static ChampionMatchupCatalog focused(ChampionCatalog champions) {
        List<ChampionMatchupProfile> profiles = new ArrayList<>();
        profiles.add(profile(champions, "renekton", "jax", ProgressionCombatContext.LANE_COMBAT));
        profiles.add(profile(champions, "lee-sin", "viego", ProgressionCombatContext.JUNGLE_GANK));
        profiles.add(profile(champions, "leblanc", "viktor", ProgressionCombatContext.ROAM));
        profiles.add(profile(champions, "lucian", "jinx", ProgressionCombatContext.TEAMFIGHT));
        profiles.add(profile(champions, "nautilus", "lulu", ProgressionCombatContext.OBJECTIVE_FIGHT));
        return ChampionMatchupCatalog.testCatalog(champions, profiles);
    }

    public static List<FocusedPair> pairs() {
        return List.of(
                new FocusedPair(Position.TOP, "renekton", "jax", ProgressionCombatContext.LANE_COMBAT),
                new FocusedPair(Position.JUNGLE, "lee-sin", "viego", ProgressionCombatContext.JUNGLE_GANK),
                new FocusedPair(Position.MID, "leblanc", "viktor", ProgressionCombatContext.ROAM),
                new FocusedPair(Position.ADC, "lucian", "jinx", ProgressionCombatContext.TEAMFIGHT),
                new FocusedPair(Position.SUPPORT, "nautilus", "lulu", ProgressionCombatContext.OBJECTIVE_FIGHT));
    }

    private static ChampionMatchupProfile profile(
            ChampionCatalog champions,
            String first,
            String second,
            ProgressionCombatContext context
    ) {
        ChampionDefinition left = champions.get(new ChampionId(first));
        ChampionDefinition right = champions.get(new ChampionId(second));
        ChampionMatchupPair pair = ChampionMatchupPair.of(left, right);
        double canonicalEdge = pair.first().equals(left.id()) ? .25 : -.25;
        return new ChampionMatchupProfile(pair, Map.of(context, canonicalEdge));
    }

    public record FocusedPair(
            Position position,
            String forwardChampion,
            String reverseChampion,
            ProgressionCombatContext focusContext
    ) {
    }
}
