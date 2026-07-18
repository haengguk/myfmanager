package com.lolfm.champion;

import com.lolfm.domain.Position;
import com.lolfm.simulator.PlayerKey;
import java.util.Objects;

public record ChampionAssignment(PlayerKey playerKey, ChampionId championId, Position selectedPosition) {
    public ChampionAssignment {
        Objects.requireNonNull(playerKey, "playerKey");
        Objects.requireNonNull(championId, "championId");
        Objects.requireNonNull(selectedPosition, "selectedPosition");
        if (playerKey.position() != selectedPosition) throw new IllegalArgumentException("PlayerKey position mismatch");
    }
}
