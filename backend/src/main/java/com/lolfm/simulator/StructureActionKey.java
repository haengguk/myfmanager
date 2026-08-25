package com.lolfm.simulator;

import java.util.Objects;

/** Match-scoped idempotency key for one actual structure hit. */
public record StructureActionKey(
        int timeSeconds,
        TeamSide attackingSide,
        String siegeActionId,
        int attackSequence
) {
    public StructureActionKey {
        if (timeSeconds < 0) throw new IllegalArgumentException("timeSeconds");
        Objects.requireNonNull(attackingSide, "attackingSide");
        if (siegeActionId == null || siegeActionId.isBlank()) throw new IllegalArgumentException("siegeActionId");
        if (attackSequence < 0) throw new IllegalArgumentException("attackSequence");
    }
}
