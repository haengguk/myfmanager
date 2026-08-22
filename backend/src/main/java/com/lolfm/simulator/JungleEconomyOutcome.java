package com.lolfm.simulator;

import com.lolfm.champion.ChampionRoleKey;
import java.util.Objects;

/** One unified Champion Clear x player-resource jungle CS, gold and XP outcome. */
public record JungleEconomyOutcome(
        TeamSide side,
        PlayerKey playerKey,
        ChampionRoleKey championRoleKey,
        String clearProfileVersion,
        int timeSeconds,
        int elapsedSeconds,
        double championClearMultiplier,
        double resourceManagementMultiplier,
        double combinedEfficiency,
        double expectedCs,
        int awardedCs,
        int awardedGold,
        int awardedExperience
) {
    public JungleEconomyOutcome {
        Objects.requireNonNull(side, "side");
        Objects.requireNonNull(playerKey, "playerKey");
        Objects.requireNonNull(championRoleKey, "championRoleKey");
        clearProfileVersion = Objects.requireNonNull(clearProfileVersion, "clearProfileVersion");
        if (playerKey.side() != side) throw new IllegalArgumentException("PlayerKey side mismatch");
        if (timeSeconds < 0 || elapsedSeconds <= 0) {
            throw new IllegalArgumentException("Invalid jungle economy time window");
        }
        requireNonNegativeFinite(championClearMultiplier, "championClearMultiplier");
        requireNonNegativeFinite(resourceManagementMultiplier, "resourceManagementMultiplier");
        requireNonNegativeFinite(combinedEfficiency, "combinedEfficiency");
        requireNonNegativeFinite(expectedCs, "expectedCs");
        if (awardedCs < 0 || awardedGold < 0 || awardedExperience < 0) {
            throw new IllegalArgumentException("Jungle economy awards must not be negative");
        }
    }

    private static void requireNonNegativeFinite(double value, String field) {
        if (!Double.isFinite(value) || value < 0.0) {
            throw new IllegalArgumentException(field + " must be finite and non-negative");
        }
    }
}
