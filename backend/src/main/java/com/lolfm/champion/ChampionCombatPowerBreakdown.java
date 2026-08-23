package com.lolfm.champion;

import com.lolfm.domain.Position;
import com.lolfm.simulator.PlayerKey;
import com.lolfm.simulator.ProgressionApplicationStage;
import com.lolfm.simulator.ProgressionCombatContext;
import com.lolfm.simulator.TeamSide;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.ToDoubleFunction;

public record ChampionCombatPowerBreakdown(
        ProgressionCombatContext context,
        ProgressionApplicationStage applicationStage,
        int ownParticipantCount,
        int enemyParticipantCount,
        double ownAverageChampionPower,
        double enemyAverageChampionPower,
        double rawChampionEdge,
        double finalChampionEdge,
        boolean teamEdgeClampApplied,
        Map<PlayerKey, ChampionPowerBreakdown> ownParticipants,
        Map<PlayerKey, ChampionPowerBreakdown> enemyParticipants,
        boolean championPowerEnabled
) {
    private static final List<PlayerKey> PLAYER_KEY_ORDER = List.of(
            new PlayerKey(TeamSide.BLUE, Position.TOP),
            new PlayerKey(TeamSide.BLUE, Position.JUNGLE),
            new PlayerKey(TeamSide.BLUE, Position.MID),
            new PlayerKey(TeamSide.BLUE, Position.ADC),
            new PlayerKey(TeamSide.BLUE, Position.SUPPORT),
            new PlayerKey(TeamSide.RED, Position.TOP),
            new PlayerKey(TeamSide.RED, Position.JUNGLE),
            new PlayerKey(TeamSide.RED, Position.MID),
            new PlayerKey(TeamSide.RED, Position.ADC),
            new PlayerKey(TeamSide.RED, Position.SUPPORT));

    public ChampionCombatPowerBreakdown {
        ownParticipants = immutableCanonicalParticipants(ownParticipants);
        enemyParticipants = immutableCanonicalParticipants(enemyParticipants);
    }

    public double finalContribution() {
        return championPowerEnabled ? finalChampionEdge : 0;
    }

    public double levelContribution() {
        return championPowerEnabled
                ? average(ownParticipants, ChampionPowerBreakdown::levelModifier)
                        - average(enemyParticipants, ChampionPowerBreakdown::levelModifier)
                : 0;
    }

    public double itemContribution() {
        return championPowerEnabled
                ? average(ownParticipants, ChampionPowerBreakdown::itemModifier)
                        - average(enemyParticipants, ChampionPowerBreakdown::itemModifier)
                : 0;
    }

    public double contextContribution() {
        return finalContribution() - levelContribution() - itemContribution();
    }

    static double averageChampionPower(Map<PlayerKey, ChampionPowerBreakdown> values) {
        return average(values, ChampionPowerBreakdown::clampedPlayerChampionPower);
    }

    private static double average(
            Map<PlayerKey, ChampionPowerBreakdown> values,
            ToDoubleFunction<ChampionPowerBreakdown> value
    ) {
        if (values.isEmpty()) return 0;
        double sum = 0;
        int count = 0;
        for (PlayerKey key : PLAYER_KEY_ORDER) {
            ChampionPowerBreakdown breakdown = values.get(key);
            if (breakdown == null) continue;
            sum += value.applyAsDouble(breakdown);
            count++;
        }
        return sum / count;
    }

    private static Map<PlayerKey, ChampionPowerBreakdown> immutableCanonicalParticipants(
            Map<PlayerKey, ChampionPowerBreakdown> source
    ) {
        Objects.requireNonNull(source, "source");
        LinkedHashMap<PlayerKey, ChampionPowerBreakdown> result = new LinkedHashMap<>();
        for (PlayerKey key : PLAYER_KEY_ORDER) {
            ChampionPowerBreakdown value = source.get(key);
            if (value != null) result.put(key, value);
        }
        if (result.size() != source.size()) {
            throw new IllegalArgumentException("Champion participant map contains an invalid entry");
        }
        return Collections.unmodifiableMap(result);
    }

}
