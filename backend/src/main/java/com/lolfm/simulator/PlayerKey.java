package com.lolfm.simulator;

import com.lolfm.domain.Position;
import java.util.Objects;

/** Match-scoped slot identity: side plus position, distinct from stable person identity. */
public record PlayerKey(TeamSide side, Position position) {
    public PlayerKey {
        Objects.requireNonNull(side, "side");
        Objects.requireNonNull(position, "position");
    }

    public String stableId() { return side.name() + ":" + position.name(); }
}
