package com.lolfm.champion;

import java.util.Objects;

/** Deterministic phase foundation; production profiles remain disabled until calibrated. */
public record ChampionJungleClearProfile(
        ChampionRoleKey roleKey,
        double early,
        double mid,
        double late,
        boolean gameplayEnabled
) {
    public ChampionJungleClearProfile {
        Objects.requireNonNull(roleKey, "roleKey");
        validate(early);
        validate(mid);
        validate(late);
    }

    private static void validate(double value) {
        if (!Double.isFinite(value) || value < 0.0 || value > 2.0) {
            throw new IllegalArgumentException("Jungle clear value must be 0..2");
        }
    }
}
