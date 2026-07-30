package com.lolfm.champion;

import com.lolfm.domain.Position;
import java.util.Objects;

public record ChampionMatchupPair(ChampionId first, ChampionId second, Position position) {
    public ChampionMatchupPair {
        Objects.requireNonNull(first, "first");
        Objects.requireNonNull(second, "second");
        Objects.requireNonNull(position, "position");
        if (first.equals(second)) throw new IllegalArgumentException("Self matchup is not a pair");
        if (first.value().compareTo(second.value()) >= 0) {
            throw new IllegalArgumentException("Pair must use stable ChampionId canonical order");
        }
    }

    public static ChampionMatchupPair of(ChampionId left, ChampionId right, Position position) {
        Objects.requireNonNull(left, "left");
        Objects.requireNonNull(right, "right");
        Objects.requireNonNull(position, "position");
        return left.value().compareTo(right.value()) < 0
                ? new ChampionMatchupPair(left, right, position)
                : new ChampionMatchupPair(right, left, position);
    }

    public static ChampionMatchupPair of(ChampionDefinition left, ChampionDefinition right) {
        Objects.requireNonNull(left, "left");
        Objects.requireNonNull(right, "right");
        if (left.primaryPosition() != right.primaryPosition()) {
            throw new IllegalArgumentException("Cross-position matchup pair");
        }
        return left.id().value().compareTo(right.id().value()) < 0
                ? new ChampionMatchupPair(left.id(), right.id(), left.primaryPosition())
                : new ChampionMatchupPair(right.id(), left.id(), left.primaryPosition());
    }

    public String stableId() {
        return position + ":" + first.value() + ":" + second.value();
    }
}
