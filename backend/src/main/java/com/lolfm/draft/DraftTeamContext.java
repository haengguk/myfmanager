package com.lolfm.draft;

import com.lolfm.champion.ChampionRoleKey;
import com.lolfm.domain.ChampionProficiencies;
import com.lolfm.domain.Player;
import com.lolfm.domain.Position;
import com.lolfm.domain.Team;
import java.util.EnumMap;
import java.util.Map;

/** Player identity for draft decisions is the structured roster position, never display text. */
public final class DraftTeamContext {
    private final Map<Position, ChampionProficiencies> proficiencies;

    public DraftTeamContext(Map<Position, ChampionProficiencies> values) {
        EnumMap<Position, ChampionProficiencies> copy = new EnumMap<>(Position.class);
        copy.putAll(values);
        for (Position position : Position.values()) copy.putIfAbsent(position, ChampionProficiencies.neutral());
        proficiencies = Map.copyOf(copy);
    }
    public static DraftTeamContext from(Team team) {
        EnumMap<Position, ChampionProficiencies> values = new EnumMap<>(Position.class);
        for (Player player : team.getPlayers()) {
            if (values.put(player.getPosition(), player.getChampionProficiencies()) != null) {
                throw new IllegalArgumentException("Duplicate roster position: " + player.getPosition());
            }
        }
        if (values.size() != Position.values().length) {
            throw new IllegalArgumentException("Production draft roster must contain exactly TOP, JUNGLE, MID, ADC, SUPPORT");
        }
        return new DraftTeamContext(values);
    }
    public int proficiency(ChampionRoleKey key) { return proficiencies.get(key.position()).get(key); }
    public Map<Position, ChampionProficiencies> proficiencies() { return proficiencies; }
}
