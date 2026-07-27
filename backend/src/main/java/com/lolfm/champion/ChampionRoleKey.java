package com.lolfm.champion;

import com.lolfm.domain.Position;
import java.util.Objects;

/** Stable matchup identity for one champion in one supported role. */
public record ChampionRoleKey(ChampionId championId, Position position) {
    public ChampionRoleKey {
        Objects.requireNonNull(championId, "championId");
        Objects.requireNonNull(position, "position");
    }

    public String stableId() {
        return championId.value() + ":" + position.name();
    }
}
