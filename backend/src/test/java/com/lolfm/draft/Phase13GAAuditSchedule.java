package com.lolfm.draft;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Frozen, outcome-independent case schedule for Phase 13G-A. */
public final class Phase13GAAuditSchedule {
    private Phase13GAAuditSchedule() { }

    public record GameOneCase(String caseId, String blueContextId, String redContextId) {
        public GameOneCase {
            Objects.requireNonNull(caseId, "caseId");
            Objects.requireNonNull(blueContextId, "blueContextId");
            Objects.requireNonNull(redContextId, "redContextId");
        }
    }

    public record FearlessSeriesCase(String seriesId, String blueContextId, String redContextId) {
        public FearlessSeriesCase {
            Objects.requireNonNull(seriesId, "seriesId");
            Objects.requireNonNull(blueContextId, "blueContextId");
            Objects.requireNonNull(redContextId, "redContextId");
        }
    }

    public record Schedule(List<GameOneCase> gameOneCases, List<FearlessSeriesCase> fearlessSeries) {
        public Schedule {
            gameOneCases = gameOneCases.stream().sorted(Comparator.comparing(GameOneCase::caseId)).toList();
            fearlessSeries = fearlessSeries.stream().sorted(Comparator.comparing(FearlessSeriesCase::seriesId)).toList();
            if (gameOneCases.size() < 96) throw new IllegalArgumentException("Game-1 schedule is below minimum");
            if (fearlessSeries.size() < 12) throw new IllegalArgumentException("Fearless schedule is below minimum");
        }

        public List<GameOneCase> first(int count) {
            return gameOneCases.stream().limit(count).toList();
        }
    }

    public static Schedule freeze(List<Phase13GASyntheticContextFactory.SyntheticContext> contexts) {
        List<String> ids = contexts.stream().map(Phase13GASyntheticContextFactory.SyntheticContext::id)
                .sorted().toList();
        if (ids.size() < 24) throw new IllegalArgumentException("At least 24 contexts are required");

        List<int[]> pairs = new ArrayList<>();
        pairs.add(new int[]{0, 0});
        for (int index = 1; index < ids.size(); index++) pairs.add(new int[]{0, index});
        for (int index = 1; index + 1 < ids.size(); index += 2) pairs.add(new int[]{index, index + 1});
        int[][] cross = {
                {1, 12}, {2, 13}, {3, 14}, {4, 15}, {5, 16}, {6, 17},
                {7, 18}, {8, 19}, {9, 20}, {10, 21}, {11, 22}, {12, 23},
                {1, 23}
        };
        for (int[] pair : cross) pairs.add(pair);
        if (pairs.size() != 48) throw new IllegalStateException("Frozen pair count changed: " + pairs.size());

        List<GameOneCase> gameOne = new ArrayList<>();
        int ordinal = 1;
        for (int[] pair : pairs) {
            String a = ids.get(pair[0]);
            String b = ids.get(pair[1]);
            gameOne.add(new GameOneCase("g1-%03d-%s-blue-vs-%s-red".formatted(ordinal++, a, b), a, b));
            gameOne.add(new GameOneCase("g1-%03d-%s-blue-vs-%s-red".formatted(ordinal++, b, a), b, a));
        }

        List<FearlessSeriesCase> series = new ArrayList<>();
        for (int index = 0; index < 12; index++) {
            GameOneCase seed = gameOne.get(index);
            series.add(new FearlessSeriesCase("series-%02d-%s-vs-%s".formatted(
                    index + 1, seed.blueContextId(), seed.redContextId()),
                    seed.blueContextId(), seed.redContextId()));
        }
        return new Schedule(gameOne, series);
    }

    public static Map<String, String> contextKinds(
            List<Phase13GASyntheticContextFactory.SyntheticContext> contexts) {
        Map<String, String> result = new HashMap<>();
        contexts.forEach(context -> result.put(context.id(), context.kind()));
        return Map.copyOf(result);
    }
}
