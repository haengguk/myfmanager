package com.lolfm.player;

import com.lolfm.domain.Position;
import java.util.Locale;
import java.util.Objects;

/** Stable authored-roster identity; nickname is deliberately not part of the key. */
public record PlayerRatingKey(String teamCode, Position position) {
    public PlayerRatingKey {
        teamCode = Objects.requireNonNull(teamCode, "teamCode").trim().toUpperCase(Locale.ROOT);
        if (teamCode.isBlank()) throw new IllegalArgumentException("teamCode is required");
        Objects.requireNonNull(position, "position");
    }

    public String stableId() {
        return teamCode + ":" + position.name();
    }
}
