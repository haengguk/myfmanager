package com.lolfm.simulator;

import com.lolfm.champion.ChampionCatalog;
import com.lolfm.champion.ChampionDefinition;
import com.lolfm.domain.Position;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;

final class GeneratedMatchupRoundRobinLineupFactory {
    private GeneratedMatchupRoundRobinLineupFactory() {
    }

    static List<Lineup> create(ChampionCatalog champions, String skillProfile) {
        int skill = switch (skillProfile) {
            case "S0" -> 14;
            case "S3" -> 17;
            default -> throw new IllegalArgumentException(
                    "Unsupported skill profile " + skillProfile);
        };
        EnumMap<Position, List<Pair>> pairs = new EnumMap<>(Position.class);
        for (Position position : Position.values()) {
            List<String> ids = champions.forPosition(position).stream()
                    .map(ChampionDefinition::id).map(value -> value.value())
                    .sorted().toList();
            List<Pair> values = new ArrayList<>();
            for (int left = 0; left < ids.size(); left++) {
                for (int right = left + 1; right < ids.size(); right++) {
                    values.add(new Pair(ids.get(left), ids.get(right)));
                }
            }
            pairs.put(position, List.copyOf(values));
        }
        List<Lineup> result = new ArrayList<>();
        for (int index = 0; index < 15; index++) {
            String[] teamA = new String[5];
            String[] teamB = new String[5];
            List<String> coverage = new ArrayList<>();
            for (Position position : Position.values()) {
                Pair pair = pairs.get(position).get(index);
                boolean swap = (index + position.name().hashCode()) % 2 != 0;
                teamA[slot(position)] = swap ? pair.second : pair.first;
                teamB[slot(position)] = swap ? pair.first : pair.second;
                coverage.add(position + ":" + pair.id());
            }
            int[] attributesA = equal(14);
            int[] attributesB = equal(14);
            if ("S3".equals(skillProfile)) {
                for (int slot = 0; slot < attributesB.length; slot++) {
                    attributesB[slot] = skill;
                }
            }
            String id = "RR-" + String.format("%02d", index + 1);
            SideOrientationFixture fixture = new SideOrientationFixture(
                    id,
                    new SideOrientationFixture.LogicalLineup(
                            id + "-TEAM_A", attributesA, teamA),
                    new SideOrientationFixture.LogicalLineup(
                            id + "-TEAM_B", attributesB, teamB));
            result.add(new Lineup(id, index + 1, skillProfile, fixture,
                    String.join("|", coverage)));
        }
        return List.copyOf(result);
    }

    private static int slot(Position position) {
        return switch (position) {
            case TOP -> 0;
            case JUNGLE -> 1;
            case MID -> 2;
            case ADC -> 3;
            case SUPPORT -> 4;
        };
    }

    private static int[] equal(int value) {
        return new int[]{value, value, value, value, value};
    }

    private record Pair(String first, String second) {
        String id() { return first + "/" + second; }
    }

    record Lineup(String lineupId, int scheduleIndex, String skillProfile,
                  SideOrientationFixture fixture, String coveredPairs) {
    }
}
