package com.lolfm.simulator;

import com.lolfm.domain.Position;

/** Economy-only travel/reset delay between respawn and FARM eligibility. */
public final class FarmRecoveryRuleConfig {
    public static final int TOP_FARM_RETURN_DELAY_SECONDS = 30;
    public static final int JUNGLE_FARM_RETURN_DELAY_SECONDS = 20;
    public static final int MID_FARM_RETURN_DELAY_SECONDS = 25;
    public static final int ADC_FARM_RETURN_DELAY_SECONDS = 35;
    public static final int SUPPORT_FARM_RETURN_DELAY_SECONDS = 0;

    private FarmRecoveryRuleConfig() {}

    public static int returnDelaySeconds(Position position, int deathTimeSeconds) {
        if (deathTimeSeconds < 0) throw new IllegalArgumentException("deathTimeSeconds must be non-negative");
        return switch (position) {
            case TOP -> TOP_FARM_RETURN_DELAY_SECONDS;
            case JUNGLE -> JUNGLE_FARM_RETURN_DELAY_SECONDS;
            case MID -> MID_FARM_RETURN_DELAY_SECONDS;
            case ADC -> ADC_FARM_RETURN_DELAY_SECONDS;
            case SUPPORT -> SUPPORT_FARM_RETURN_DELAY_SECONDS;
        };
    }
}
