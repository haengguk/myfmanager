package com.lolfm.simulator;

/** Version-one jungle economy rules; shared economy values remain single-owned aliases. */
public final class JungleEconomyRuleConfig {
    public static final int STANDARD_TICK_SECONDS = 10;
    public static final double BASE_CS_PER_MINUTE =
            PositionEconomyRuleConfig.JUNGLE_BASE_CS_PER_MINUTE;
    public static final int GOLD_PER_CS = PositionEconomyRuleConfig.CS_GOLD;
    public static final int BASE_XP_PER_STANDARD_TICK = ProgressionRuleConfig.JUNGLE_XP_PER_TICK;

    private JungleEconomyRuleConfig() {
    }
}
