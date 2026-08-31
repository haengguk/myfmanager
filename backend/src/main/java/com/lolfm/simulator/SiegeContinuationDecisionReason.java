package com.lolfm.simulator;

/** Structured strategic decision made before an active siege mutates the map. */
public enum SiegeContinuationDecisionReason {
    CONTINUATION_ALLOWED,
    LOWER_VALUE_SIEGE_ABORTED_FOR_BASE_DEFENSE,
    BASE_RACE_REJECTED_FAIL_CLOSED
}
