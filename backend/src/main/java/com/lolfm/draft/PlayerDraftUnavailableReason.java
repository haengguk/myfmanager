package com.lolfm.draft;

/** Structured reason a catalog champion cannot be selected on the current player turn. */
public enum PlayerDraftUnavailableReason {
    HARD_FEARLESS_EXCLUDED,
    ALREADY_BANNED,
    ALREADY_PICKED,
    PARTIAL_ROLE_ASSIGNMENT_INFEASIBLE,
    FUTURE_ROLE_COMPLETION_INFEASIBLE,
    BAN_WOULD_BREAK_FUTURE_COMPLETION
}
