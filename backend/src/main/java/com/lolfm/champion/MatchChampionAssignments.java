package com.lolfm.champion;

import com.lolfm.domain.Position;
import com.lolfm.simulator.PlayerKey;
import com.lolfm.simulator.TeamSide;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public final class MatchChampionAssignments {
    private final Map<PlayerKey, ChampionAssignment> assignments;
    private final ChampionSelectionMode selectionMode;

    public MatchChampionAssignments(List<ChampionAssignment> values, ChampionSelectionMode selectionMode) {
        LinkedHashMap<PlayerKey, ChampionAssignment> copy = new LinkedHashMap<>();
        for (ChampionAssignment value : values) {
            if (copy.put(value.playerKey(), value) != null) throw new IllegalArgumentException("Duplicate PlayerKey: " + value.playerKey());
        }
        if (copy.size() != TeamSide.values().length * Position.values().length) throw new IllegalArgumentException("Expected 10 champion assignments");
        Set<ChampionId> unique = copy.values().stream().map(ChampionAssignment::championId).collect(Collectors.toSet());
        if (unique.size() != copy.size()) throw new IllegalArgumentException("Duplicate champion assignment");
        this.assignments = Map.copyOf(copy);
        this.selectionMode = selectionMode;
    }

    public ChampionAssignment get(PlayerKey key) {
        ChampionAssignment value = assignments.get(key);
        if (value == null) throw new IllegalArgumentException("Missing champion assignment: " + key);
        return value;
    }
    public Map<PlayerKey, ChampionAssignment> asMap() { return assignments; }
    public ChampionSelectionMode selectionMode() { return selectionMode; }
}
