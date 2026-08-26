package com.lolfm.simulator;

public final class LaneState {
    private final Lane lane;
    private double pressure;
    private long pressureMutationVersion;
    private com.lolfm.champion.ChampionMatchupStateMutationLineage matchupMutationLineage;
    private int lastCombatAttemptAtSeconds = -1;

    public LaneState(Lane lane) { this.lane = lane; }
    public Lane getLane() { return lane; }
    public double getPressure() { return pressure; }
    void setPressure(double pressure) {
        this.pressure = pressure;
        pressureMutationVersion++;
        matchupMutationLineage = null;
    }
    com.lolfm.champion.ChampionMatchupStateMutationLineage applyPressureResolution(
            int timeSeconds, double nextPressure, double matchupPressureDelta,
            double clampEffect) {
        pressureMutationVersion++;
        double before = pressure;
        pressure = nextPressure;
        matchupMutationLineage = new com.lolfm.champion.ChampionMatchupStateMutationLineage(
                "LANE_PRESSURE:" + timeSeconds + ":" + lane + ":" + pressureMutationVersion,
                pressureMutationVersion, timeSeconds, lane, before, nextPressure,
                matchupPressureDelta, clampEffect);
        return matchupMutationLineage;
    }
    public long getPressureMutationVersion() { return pressureMutationVersion; }
    public com.lolfm.champion.ChampionMatchupStateMutationLineage
    getMatchupMutationLineage() { return matchupMutationLineage; }
    public int getLastCombatAttemptAtSeconds() { return lastCombatAttemptAtSeconds; }
    void markCombatAttemptAt(int time) { lastCombatAttemptAtSeconds = time; }
    public LanePriority getPriority() {
        if (pressure >= LanePressureRuleConfig.PRIORITY_THRESHOLD) return LanePriority.BLUE;
        if (pressure <= -LanePressureRuleConfig.PRIORITY_THRESHOLD) return LanePriority.RED;
        return LanePriority.NEUTRAL;
    }
}
