package com.lolfm.league;

/** Explicit relational lifecycle values. They are operational state, never gameplay input. */
public final class LeaguePersistenceState {
    private LeaguePersistenceState() {}

    public enum SeasonStatus {
        DRAFT, FROZEN, READY, RUNNING, PAUSED, WAITING_FOR_PLAYER,
        COMPLETED, BLOCKED, CANCELLED
    }

    public enum FixtureStatus {
        SCHEDULED, QUEUED, LEASED, RUNNING, AWAITING_PLAYER,
        PLAYER_SERIES_RESERVED, PLAYER_SERIES_ACTIVE,
        COMPLETION_PENDING_VERIFICATION, RETRY_PENDING,
        PLAYER_SERIES_RESTART_REQUIRED, COMPLETED, BLOCKED, CANCELLED
    }

    public enum JobStatus {
        QUEUED, LEASED, RUNNING, RETRY_PENDING,
        COMPLETION_PENDING_VERIFICATION, COMPLETED, BLOCKED, CANCELLED
    }

    public enum FailureClass { TRANSIENT, DETERMINISTIC }

    public enum OutboxStatus { PENDING, DELIVERED }
}
