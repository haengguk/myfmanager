package com.lolfm.champion;

import java.util.Objects;

/** One structured same-position edge consumed by a runtime Matchup application. */
public record ChampionMatchupPairApplication(
        ChampionMatchupParticipantBinding source,
        ChampionMatchupParticipantBinding opponent,
        double edge
) {
    public ChampionMatchupPairApplication {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(opponent, "opponent");
        if (!Double.isFinite(edge) || source.playerKey().side() == opponent.playerKey().side()
                || source.position() != opponent.position()) {
            throw new IllegalArgumentException("Invalid Matchup pair application");
        }
    }
}
