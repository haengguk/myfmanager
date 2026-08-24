package com.lolfm.simulator;

import com.lolfm.domain.PlayerSkill;
import com.lolfm.domain.PlayerRatings;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/** Immutable terminal copy of the exact match-scoped ratings used by gameplay. */
public record PlayerMatchPerformanceSnapshot(
        PlayerKey playerKey,
        Map<PlayerSkill, Double> realizedRatings,
        int championProficiency
) {
    public PlayerMatchPerformanceSnapshot {
        Objects.requireNonNull(playerKey, "playerKey");
        Objects.requireNonNull(realizedRatings, "realizedRatings");
        EnumMap<PlayerSkill, Double> copy = new EnumMap<>(PlayerSkill.class);
        realizedRatings.forEach((skill, value) -> {
            Objects.requireNonNull(skill, "skill");
            if (value == null || !Double.isFinite(value)
                    || value < PlayerRatings.MIN || value > PlayerRatings.MAX) {
                throw new IllegalArgumentException(
                        "realized rating must be finite and within rating bounds");
            }
            copy.put(skill, value);
        });
        if (!copy.keySet().equals(PlayerSkill.forPosition(playerKey.position()))) {
            throw new IllegalArgumentException("match performance skill coverage mismatch");
        }
        if (championProficiency < 1 || championProficiency > 20) {
            throw new IllegalArgumentException("championProficiency");
        }
        realizedRatings = Map.copyOf(copy);
    }
}
