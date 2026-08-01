package com.lolfm.composition;

import com.lolfm.simulator.TeamSide;
import java.util.Objects;

/** Structured match lineup identity; display names are intentionally absent. */
public record CompositionTeamLineup(TeamSide side, TeamCompositionLineup lineup) {
    public CompositionTeamLineup {
        Objects.requireNonNull(side, "side");
        Objects.requireNonNull(lineup, "lineup");
    }
}
