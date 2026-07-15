package com.lolfm.simulator;

/** Structured lifecycle state for one team's macro schedule. */
public enum MacroPlanStatus {
    DISABLED,
    NOT_STARTED,
    WAITING_FOR_EVALUATION,
    ACTIVE,
    EXPIRED,
    CANCELLED,
    MATCH_ENDED
}
