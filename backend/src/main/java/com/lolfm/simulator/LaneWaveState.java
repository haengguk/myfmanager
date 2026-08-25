package com.lolfm.simulator;

/** One side's match-scoped abstract minion-wave state for a lane. */
public final class LaneWaveState {
    private long waveSequence;
    private long activeWaveId = -1;
    private int availableUntilSeconds = -1;
    private int nextWaveAtSeconds;
    private int attacksRemaining;

    public boolean canPrepareAt(int timeSeconds) {
        return !hasActiveWaveAt(timeSeconds) && timeSeconds >= nextWaveAtSeconds;
    }

    public long prepareAt(int timeSeconds, int attackOpportunities) {
        if (!canPrepareAt(timeSeconds)) throw new IllegalStateException("Wave is not ready");
        if (attackOpportunities <= 0) throw new IllegalArgumentException("attackOpportunities");
        activeWaveId = ++waveSequence;
        availableUntilSeconds = timeSeconds + Math.max(
                StructureRuleConfig.WAVE_ACTIVE_SECONDS,
                (attackOpportunities - 1) * StructureRuleConfig.STRUCTURE_ATTACK_INTERVAL_SECONDS
                        + StructureRuleConfig.WAVE_ATTACK_EXPIRY_GRACE_SECONDS);
        nextWaveAtSeconds = timeSeconds + StructureRuleConfig.NEXT_WAVE_SECONDS;
        attacksRemaining = attackOpportunities;
        return activeWaveId;
    }

    public void ensureAttackOpportunities(int minimumAttacks, int timeSeconds) {
        if (minimumAttacks <= 0) throw new IllegalArgumentException("minimumAttacks");
        if (activeWaveId < 0) throw new IllegalStateException("No active wave");
        attacksRemaining = Math.max(attacksRemaining, minimumAttacks);
        availableUntilSeconds = Math.max(availableUntilSeconds,
                timeSeconds + minimumAttacks
                        * StructureRuleConfig.STRUCTURE_ATTACK_INTERVAL_SECONDS
                        + StructureRuleConfig.WAVE_ATTACK_EXPIRY_GRACE_SECONDS);
    }

    public boolean hasActiveWaveAt(int timeSeconds) {
        return activeWaveId >= 0 && timeSeconds < availableUntilSeconds && attacksRemaining > 0;
    }

    public boolean consumeAttack(int timeSeconds) {
        if (!hasActiveWaveAt(timeSeconds)) return false;
        attacksRemaining--;
        return true;
    }

    public long getActiveWaveId() { return activeWaveId; }
    public int getAvailableUntilSeconds() { return availableUntilSeconds; }
    public int getNextWaveAtSeconds() { return nextWaveAtSeconds; }
    public int getAttacksRemaining() { return attacksRemaining; }
}
