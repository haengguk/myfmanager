package com.lolfm.simulator;

public final class LanePressureRuleConfig {
    public static final int PRESSURE_UPDATE_INTERVAL_SECONDS = 30;
    public static final double PRESSURE_MIN = -100.0;
    public static final double PRESSURE_MAX = 100.0;
    public static final double PRESSURE_RETENTION = 0.75;
    public static final double ATTRIBUTE_DIFFERENCE_FACTOR = 2.0;
    public static final double GOLD_DIFFERENCE_DIVISOR = 500.0;
    public static final double GOLD_MODIFIER_MIN = -4.0;
    public static final double GOLD_MODIFIER_MAX = 4.0;
    public static final double RANDOM_VARIATION_MIN = -2.5;
    public static final double RANDOM_VARIATION_MAX = 2.5;
    public static final double PRIORITY_THRESHOLD = 20.0;
    public static final double STRONG_PRESSURE_THRESHOLD = 50.0;
    public static final double DOMINANT_PRESSURE_THRESHOLD = 75.0;
    public static final double MAX_LANE_CS_MODIFIER = 0.05;
    public static final double BOT_ADC_CONTRIBUTION = 0.60;
    public static final double BOT_SUPPORT_CONTRIBUTION = 0.40;
    private LanePressureRuleConfig() { }
}
