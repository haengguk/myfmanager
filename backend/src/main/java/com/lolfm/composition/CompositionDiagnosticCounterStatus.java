package com.lolfm.composition;

/** Distinguishes missing instrumentation from an observed zero. */
public enum CompositionDiagnosticCounterStatus {
    NOT_INSTRUMENTED,
    INSTRUMENTED_ZERO,
    INSTRUMENTED_NONZERO;

    static CompositionDiagnosticCounterStatus from(boolean instrumented, int count) {
        if (!instrumented) return NOT_INSTRUMENTED;
        return count == 0 ? INSTRUMENTED_ZERO : INSTRUMENTED_NONZERO;
    }
}
