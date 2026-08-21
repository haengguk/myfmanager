package com.lolfm.draft;

import com.lolfm.champion.ChampionRoleKey;
import com.lolfm.domain.ChampionProficiencies;
import com.lolfm.domain.Player;
import com.lolfm.domain.Position;
import com.lolfm.domain.Team;
import com.lolfm.player.PlayerId;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Draft slots stay position-based while production contexts also expose stable person identity. */
public final class DraftTeamContext {
    private final Map<Position, ChampionProficiencies> proficiencies;
    private final Map<Position, PlayerId> playerIds;

    public DraftTeamContext(Map<Position, ChampionProficiencies> values) {
        this(values, Map.of());
    }

    public DraftTeamContext(Map<Position, ChampionProficiencies> values,
                            Map<Position, PlayerId> playerIds) {
        EnumMap<Position, ChampionProficiencies> proficiencyCopy = new EnumMap<>(Position.class);
        proficiencyCopy.putAll(values);
        for (Position position : Position.values()) {
            proficiencyCopy.putIfAbsent(position, ChampionProficiencies.neutral());
        }
        proficiencies = Map.copyOf(proficiencyCopy);
        EnumMap<Position, PlayerId> identityCopy = new EnumMap<>(Position.class);
        identityCopy.putAll(playerIds);
        this.playerIds = Map.copyOf(identityCopy);
    }

    public static DraftTeamContext from(Team team) {
        EnumMap<Position, ChampionProficiencies> values = new EnumMap<>(Position.class);
        EnumMap<Position, PlayerId> identities = new EnumMap<>(Position.class);
        Set<PlayerId> uniqueIds = new HashSet<>();
        int stableIdentityCount = 0;
        for (Player player : team.getPlayers()) {
            if (values.put(player.getPosition(), player.getChampionProficiencies()) != null) {
                throw new IllegalArgumentException("Duplicate roster position: " + player.getPosition());
            }
            if (player.hasStablePlayerId()) {
                PlayerId playerId = player.requirePlayerId();
                if (!uniqueIds.add(playerId)) {
                    throw new IllegalArgumentException("Duplicate roster PlayerId: " + playerId);
                }
                identities.put(player.getPosition(), playerId);
                stableIdentityCount++;
            }
        }
        if (values.size() != Position.values().length) {
            throw new IllegalArgumentException(
                    "Production draft roster must contain exactly TOP, JUNGLE, MID, ADC, SUPPORT");
        }
        if (stableIdentityCount != 0 && stableIdentityCount != Position.values().length) {
            throw new IllegalArgumentException("Draft roster mixes stable and legacy player identity");
        }
        return new DraftTeamContext(values, identities);
    }

    public int proficiency(ChampionRoleKey key) {
        return proficiencies.get(key.position()).get(key);
    }
    public Map<Position, ChampionProficiencies> proficiencies() { return proficiencies; }
    public Optional<PlayerId> playerId(Position position) {
        return Optional.ofNullable(playerIds.get(position));
    }
    public Map<Position, PlayerId> playerIds() { return playerIds; }
    public boolean hasStablePlayerIdentities() {
        return playerIds.size() == Position.values().length;
    }
}
