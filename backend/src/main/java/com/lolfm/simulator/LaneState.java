package com.lolfm.simulator;

public final class LaneState {
    private final Lane lane;
    private double pressure;
    private int lastCombatAttemptAtSeconds = -1;

    public LaneState(Lane lane) { this.lane = lane; }
    public Lane getLane() { return lane; }
    public double getPressure() { return pressure; }
    void setPressure(double pressure) { this.pressure = pressure; }
    public int getLastCombatAttemptAtSeconds() { return lastCombatAttemptAtSeconds; }
    void markCombatAttemptAt(int time) { lastCombatAttemptAtSeconds = time; }
    public LanePriority getPriority() {
        if (pressure >= LanePressureRuleConfig.PRIORITY_THRESHOLD) return LanePriority.BLUE;
        if (pressure <= -LanePressureRuleConfig.PRIORITY_THRESHOLD) return LanePriority.RED;
        return LanePriority.NEUTRAL;
    }
}
