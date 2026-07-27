package com.lolfm.simulator;

import com.lolfm.domain.Position;
import java.util.List;

final class SideOrientationFixtureFactory {
    private SideOrientationFixtureFactory() {
    }

    static List<SideOrientationFixture> neutralFixtures() {
        return List.of(
                fixture(
                        "N1",
                        new String[]{"ornn", "sejuani", "orianna", "ezreal", "braum"},
                        new String[]{"gwen", "maokai", "viktor", "jinx", "lulu"},
                        null,
                        14
                ),
                fixture(
                        "N2",
                        new String[]{"renekton", "lee-sin", "leblanc", "lucian", "nautilus"},
                        new String[]{"jax", "viego", "azir", "aphelios", "rakan"},
                        null,
                        14
                )
        );
    }

    static List<SideOrientationFixture> focused(String skillProfile) {
        int skill = switch (skillProfile) {
            case "S0" -> 14;
            case "S1" -> 15;
            case "S3" -> 17;
            case "S5" -> 19;
            default -> throw new IllegalArgumentException("Unsupported profile " + skillProfile);
        };
        return List.of(
                fixture("TOP",
                        new String[]{"renekton", "sejuani", "orianna", "ezreal", "braum"},
                        new String[]{"jax", "maokai", "viktor", "jinx", "lulu"}, Position.TOP, skill),
                fixture("JUNGLE",
                        new String[]{"ornn", "lee-sin", "orianna", "ezreal", "braum"},
                        new String[]{"gwen", "viego", "viktor", "jinx", "lulu"}, Position.JUNGLE, skill),
                fixture("MID",
                        new String[]{"ornn", "sejuani", "leblanc", "ezreal", "braum"},
                        new String[]{"gwen", "maokai", "viktor", "jinx", "lulu"}, Position.MID, skill),
                fixture("ADC",
                        new String[]{"ornn", "sejuani", "orianna", "lucian", "braum"},
                        new String[]{"gwen", "maokai", "viktor", "jinx", "lulu"}, Position.ADC, skill),
                fixture("SUPPORT",
                        new String[]{"ornn", "sejuani", "orianna", "ezreal", "nautilus"},
                        new String[]{"gwen", "maokai", "viktor", "jinx", "lulu"}, Position.SUPPORT, skill)
        );
    }

    private static SideOrientationFixture fixture(
            String id,
            String[] teamAChampions,
            String[] teamBChampions,
            Position teamBTarget,
            int targetSkill
    ) {
        int[] teamA = equalAttributes(14);
        int[] teamB = equalAttributes(14);
        if (teamBTarget != null) teamB[teamBTarget.ordinal()] = targetSkill;
        return new SideOrientationFixture(
                id,
                new SideOrientationFixture.LogicalLineup(id + "-TEAM_A", teamA, teamAChampions),
                new SideOrientationFixture.LogicalLineup(id + "-TEAM_B", teamB, teamBChampions)
        );
    }

    private static int[] equalAttributes(int value) {
        return new int[]{value, value, value, value, value};
    }
}
