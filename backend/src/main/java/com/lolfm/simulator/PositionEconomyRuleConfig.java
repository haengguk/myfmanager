package com.lolfm.simulator;

/** Constants for FARM income only; passive income remains in the existing impact rules. */
public final class PositionEconomyRuleConfig {
    public static final double TOP_BASE_CS_PER_MINUTE = 7.0;
    public static final double MID_BASE_CS_PER_MINUTE = 7.1;
    public static final double ADC_BASE_CS_PER_MINUTE = 7.2;
    public static final double JUNGLE_BASE_CS_PER_MINUTE = 5.8;
    public static final double SUPPORT_BASE_CS_PER_MINUTE = 0.0;
    public static final int FARMING_BASELINE = 14;
    public static final double FARMING_MULTIPLIER_PER_POINT = 0.02;
    public static final double MIN_FARMING_MULTIPLIER = 0.74;
    public static final double MAX_FARMING_MULTIPLIER = 1.12;
    public static final int CS_GOLD = 20;
    public static final int PASSIVE_GOLD_PER_TICK = 14;

    private PositionEconomyRuleConfig() {
    }
}
