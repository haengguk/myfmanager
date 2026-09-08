package com.lolfm.simulator;

/** Match-scoped activity that temporarily removes a living player from normal combat selection. */
public enum PlayerActivityType {
    DEFAULT_ROLE,
    ROAMING,
    RETURNING_TO_BASE,
    DEFENDING_BASE,
    UPPER_OBJECTIVE_RETURN,
    SIEGING
}
