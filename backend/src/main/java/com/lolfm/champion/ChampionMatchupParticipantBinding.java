package com.lolfm.champion;

import com.lolfm.domain.Position;
import com.lolfm.player.PlayerId;
import com.lolfm.simulator.PlayerKey;
import java.util.Objects;

/** Stable match/person/champion identity for one side of an applied Matchup pair. */
public record ChampionMatchupParticipantBinding(
        PlayerKey playerKey,
        Position position,
        PlayerId playerId,
        ChampionId championId
) {
    public ChampionMatchupParticipantBinding {
        Objects.requireNonNull(playerKey, "playerKey");
        Objects.requireNonNull(position, "position");
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(championId, "championId");
        if (playerKey.position() != position) {
            throw new IllegalArgumentException("Matchup participant position mismatch");
        }
    }
}
