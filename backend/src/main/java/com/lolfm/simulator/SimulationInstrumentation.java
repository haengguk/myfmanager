package com.lolfm.simulator;

/** Observational instrumentation, intentionally excluded from gameplay identity. */
public record SimulationInstrumentation(boolean diagnosticsEnabled) {
    private static final SimulationInstrumentation ENABLED = new SimulationInstrumentation(true);
    private static final SimulationInstrumentation DISABLED = new SimulationInstrumentation(false);

    public static SimulationInstrumentation enabled() {
        return ENABLED;
    }

    public static SimulationInstrumentation disabled() {
        return DISABLED;
    }
}
