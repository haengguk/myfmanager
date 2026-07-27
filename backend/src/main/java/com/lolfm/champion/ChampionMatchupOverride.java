package com.lolfm.champion;

import com.lolfm.domain.Position;
import com.lolfm.simulator.ProgressionCombatContext;
import java.util.Objects;

public record ChampionMatchupOverride(
        ChampionMatchupPair pair,
        Position position,
        ProgressionCombatContext context,
        double canonicalFirstAdjustment,
        MatchupOverrideReason reason,
        String note,
        String version
) {
    public ChampionMatchupOverride {
        Objects.requireNonNull(pair, "pair");
        Objects.requireNonNull(position, "position");
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(reason, "reason");
        if (pair.position() != position) {
            throw new IllegalArgumentException("Cross-position matchup override");
        }
        if (!Double.isFinite(canonicalFirstAdjustment)) {
            throw new IllegalArgumentException("Non-finite override adjustment");
        }
        canonicalFirstAdjustment =
                canonicalFirstAdjustment == 0.0 ? 0.0 : canonicalFirstAdjustment;
        note = note == null ? "" : note;
        if (version == null || version.isBlank()) {
            throw new IllegalArgumentException("version must not be blank");
        }
    }
}
