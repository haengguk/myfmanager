package com.lolfm.champion;

import java.util.ArrayList;
import java.util.List;

public record ChampionMatchupIndependentScenario(
        Group group,
        int skillGap,
        GrowthPackage growthPackage
) {
    public static List<ChampionMatchupIndependentScenario> all() {
        List<ChampionMatchupIndependentScenario> values = new ArrayList<>();
        for (int skill : new int[]{0, 1, 3, 5}) {
            values.add(new ChampionMatchupIndependentScenario(
                    Group.SKILL_ONLY, skill, GrowthPackage.NONE));
        }
        for (GrowthPackage growth : GrowthPackage.values()) {
            if (growth != GrowthPackage.NONE) {
                values.add(new ChampionMatchupIndependentScenario(
                        Group.GROWTH_ONLY, 0, growth));
            }
        }
        for (int skill : new int[]{1, 3, 5}) {
            for (GrowthPackage growth : List.of(
                    GrowthPackage.COMBINED_LEAD_SMALL,
                    GrowthPackage.COMBINED_LEAD_LARGE)) {
                values.add(new ChampionMatchupIndependentScenario(
                        Group.COMBINED, skill, growth));
            }
        }
        if (values.size() != 18) {
            throw new IllegalStateException("Expected 18 independent scenarios");
        }
        return List.copyOf(values);
    }

    public enum Group {
        SKILL_ONLY,
        GROWTH_ONLY,
        COMBINED
    }

    public enum GrowthPackage {
        NONE(0, 0, 0),
        KILL_LEAD_1(1, 0, 0),
        KILL_LEAD_2(2, 0, 0),
        LEVEL_LEAD_1(0, 1, 0),
        LEVEL_LEAD_2(0, 2, 0),
        ITEM_STAGE_LEAD_1(0, 0, 1),
        ITEM_STAGE_LEAD_2(0, 0, 2),
        COMBINED_LEAD_SMALL(1, 1, 1),
        COMBINED_LEAD_LARGE(2, 2, 2);

        private final int kills;
        private final int levels;
        private final int itemStages;

        GrowthPackage(int kills, int levels, int itemStages) {
            this.kills = kills;
            this.levels = levels;
            this.itemStages = itemStages;
        }

        public int kills() { return kills; }
        public int levels() { return levels; }
        public int itemStages() { return itemStages; }
    }
}
