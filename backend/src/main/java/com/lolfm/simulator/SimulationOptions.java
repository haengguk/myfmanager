package com.lolfm.simulator;

/** Per-simulator feature and observational-instrumentation options. */
public record SimulationOptions(
        boolean laneCombatEnabled,
        boolean farmRecoveryEnabled,
        boolean jungleGankEnabled,
        boolean counterGankEnabled,
        boolean roamEnabled,
        boolean diagnosticsEnabled,
        boolean objectivePriorityEnabled,
        boolean lanePhaseEnabled
) {
    public static SimulationOptions productionDefaults() {
        return new SimulationOptions(true, true, true, true, true, true, true, true);
    }

    public SimulationOptions withRoamEnabled(boolean enabled) {
        return new SimulationOptions(laneCombatEnabled, farmRecoveryEnabled, jungleGankEnabled,
                counterGankEnabled, enabled, diagnosticsEnabled, objectivePriorityEnabled, lanePhaseEnabled);
    }

    public SimulationOptions withDiagnosticsEnabled(boolean enabled) {
        return new SimulationOptions(laneCombatEnabled, farmRecoveryEnabled, jungleGankEnabled,
                counterGankEnabled, roamEnabled, enabled, objectivePriorityEnabled, lanePhaseEnabled);
    }

    public SimulationOptions withObjectivePriorityEnabled(boolean enabled) {
        return new SimulationOptions(laneCombatEnabled, farmRecoveryEnabled, jungleGankEnabled,
                counterGankEnabled, roamEnabled, diagnosticsEnabled, enabled, lanePhaseEnabled);
    }

    public SimulationOptions withLanePhaseEnabled(boolean enabled) {
        return new SimulationOptions(laneCombatEnabled, farmRecoveryEnabled, jungleGankEnabled,
                counterGankEnabled, roamEnabled, diagnosticsEnabled, objectivePriorityEnabled, enabled);
    }
}
