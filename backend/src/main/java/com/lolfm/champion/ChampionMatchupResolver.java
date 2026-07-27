package com.lolfm.champion;

import com.lolfm.domain.Position;
import com.lolfm.simulator.GameState;
import com.lolfm.simulator.PlayerKey;
import com.lolfm.simulator.PlayerState;
import com.lolfm.simulator.ProgressionApplicationStage;
import com.lolfm.simulator.ProgressionCombatContext;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class ChampionMatchupResolver {
    public ChampionMatchupResult evaluate(
            GameState state,
            List<PlayerState> sourceParticipants,
            List<PlayerState> opponentParticipants,
            ProgressionCombatContext context,
            ProgressionApplicationStage stage
    ) {
        ChampionMatchupExecutionStats stats = state.getChampionMatchupExecutionStats();
        if (state.getChampionMatchupMode() == ChampionMatchupMode.OFF) {
            stats.recordDisabledEvaluation();
            return ChampionMatchupResult.disabled();
        }
        Collected source = collect(state, sourceParticipants);
        Collected opponent = collect(state, opponentParticipants);
        Counters counters = new Counters(source, opponent);
        List<ChampionMatchupPairContribution> contributions = new ArrayList<>();
        Set<ApplicationIdentity> applied = new HashSet<>();
        ChampionMatchupCatalog catalog = state.getChampionMatchupCatalog().orElseThrow();
        MatchChampionAssignments assignments = state.getChampionAssignments().orElse(null);
        for (Position position : Position.values()) {
            Participant left = source.byPosition().get(position);
            Participant right = opponent.byPosition().get(position);
            if (left == null || right == null) {
                if (left != null || right != null) counters.crossPosition++;
                continue;
            }
            if (left.key().side() == right.key().side()) {
                counters.sameTeam++;
                continue;
            }
            if (assignments == null) {
                counters.missingAssignments += 2;
                continue;
            }
            ChampionAssignment leftAssignment;
            ChampionAssignment rightAssignment;
            try {
                leftAssignment = assignments.get(left.key());
                rightAssignment = assignments.get(right.key());
            } catch (IllegalArgumentException missing) {
                counters.missingAssignments++;
                continue;
            }
            var pair = catalog.findPair(
                    leftAssignment.championId(), rightAssignment.championId(), position);
            if (pair.isEmpty()) continue;
            ApplicationIdentity identity = new ApplicationIdentity(
                    pair.get(), context, stage, left.key(), right.key());
            if (!applied.add(identity)) {
                counters.duplicates++;
                continue;
            }
            double edge = catalog.contribution(
                    leftAssignment.championId(), rightAssignment.championId(), position, context);
            contributions.add(new ChampionMatchupPairContribution(
                    pair.get(), left.key(), right.key(), context, edge));
        }
        double total = contributions.stream()
                .mapToDouble(ChampionMatchupPairContribution::edge).sum();
        double average = contributions.isEmpty() ? 0.0 : total / contributions.size();
        ChampionMatchupResult result = new ChampionMatchupResult(
                true, contributions.size(), total, average, contributions,
                counters.missingAssignments, counters.dead, counters.crossPosition,
                counters.nonParticipant, counters.sameTeam, counters.duplicates);
        stats.recordEnabledEvaluation(result);
        return result;
    }

    private Collected collect(GameState state, List<PlayerState> players) {
        Map<Position, Participant> byPosition = new EnumMap<>(Position.class);
        Set<PlayerKey> seen = new HashSet<>();
        int dead = 0;
        int outside = 0;
        int duplicates = 0;
        for (PlayerState player : players) {
            if (!player.isAlive(state.getCurrentTimeSeconds())) {
                dead++;
                continue;
            }
            var key = state.playerKeyOf(player);
            if (key.isEmpty()) {
                outside++;
                continue;
            }
            if (!seen.add(key.get())
                    || byPosition.putIfAbsent(key.get().position(),
                    new Participant(key.get(), player)) != null) duplicates++;
        }
        return new Collected(Map.copyOf(byPosition), dead, outside, duplicates);
    }

    private record Participant(PlayerKey key, PlayerState state) { }
    private record Collected(
            Map<Position, Participant> byPosition,
            int dead,
            int nonParticipant,
            int duplicates
    ) { }
    private record ApplicationIdentity(
            ChampionMatchupPair pair,
            ProgressionCombatContext context,
            ProgressionApplicationStage stage,
            PlayerKey source,
            PlayerKey opponent
    ) { }

    private static final class Counters {
        int missingAssignments;
        int crossPosition;
        int sameTeam;
        int duplicates;
        final int dead;
        final int nonParticipant;

        Counters(Collected source, Collected opponent) {
            dead = source.dead() + opponent.dead();
            nonParticipant = source.nonParticipant() + opponent.nonParticipant();
            duplicates = source.duplicates() + opponent.duplicates();
        }
    }
}
