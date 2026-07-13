package com.lolfm.simulator;

public final class LaneState {
    private final Lane lane;
    private double pressure;

    public LaneState(Lane lane) { this.lane = lane; }
    public Lane getLane() { return lane; }
    public double getPressure() { return pressure; }
    void setPressure(double pressure) { this.pressure = pressure; }
    public LanePriority getPriority() {
        if (pressure >= LanePressureRuleConfig.PRIORITY_THRESHOLD) return LanePriority.BLUE;
        if (pressure <= -LanePressureRuleConfig.PRIORITY_THRESHOLD) return LanePriority.RED;
        return LanePriority.NEUTRAL;
    }
}
