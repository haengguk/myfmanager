package com.lolfm.player;

import com.lolfm.champion.ChampionRoleKey;
import java.util.Objects;

/** One authored sparse proficiency override after stable-person binding. */
public record ChampionProficiencyEntry(
        PlayerId playerId,
        PlayerRatingKey sourceRatingKey,
        ChampionRoleKey championRoleKey,
        int value
) {
    public ChampionProficiencyEntry {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(sourceRatingKey, "sourceRatingKey");
        Objects.requireNonNull(championRoleKey, "championRoleKey");
        if (value < 1 || value > 20) throw new IllegalArgumentException("Proficiency must be 1..20");
    }
}
