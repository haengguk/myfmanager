package com.lolfm.simulator;

import com.lolfm.domain.Position;

/** Economy-only delay between respawn and FARM eligibility during laning. */
public final class FarmRecoveryRuleConfig {
    public static final int LANING_RETURN_DELAY_END_SECONDS = 840;
    public static final int TOP_FARM_RETURN_DELAY_SECONDS = 20;
    public static final int JUNGLE_FARM_RETURN_DELAY_SECONDS = 10;
    public static final int MID_FARM_RETURN_DELAY_SECONDS = 10;
    public static final int ADC_FARM_RETURN_DELAY_SECONDS = 20;
    public static final int SUPPORT_FARM_RETURN_DELAY_SECONDS = 0;

    private FarmRecoveryRuleConfig() {}

    public static int returnDelaySeconds(Position position, int deathTimeSeconds) {
        if (deathTimeSeconds > LANING_RETURN_DELAY_END_SECONDS) return 0;
        return switch (position) {
            case TOP -> TOP_FARM_RETURN_DELAY_SECONDS;
            case JUNGLE -> JUNGLE_FARM_RETURN_DELAY_SECONDS;
            case MID -> MID_FARM_RETURN_DELAY_SECONDS;
            case ADC -> ADC_FARM_RETURN_DELAY_SECONDS;
            case SUPPORT -> SUPPORT_FARM_RETURN_DELAY_SECONDS;
        };
    }
}
