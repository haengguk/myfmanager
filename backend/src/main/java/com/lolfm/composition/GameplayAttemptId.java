package com.lolfm.composition;

/** Match-scoped monotonic identity. It is issued only when an actual attempt begins. */
public record GameplayAttemptId(long sequence) {
    public GameplayAttemptId {
        if (sequence <= 0) throw new IllegalArgumentException("Attempt sequence must be positive");
    }
}
