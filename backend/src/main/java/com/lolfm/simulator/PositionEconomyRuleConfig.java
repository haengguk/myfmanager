package com.lolfm.simulator;

import com.lolfm.domain.Position;

/** Constants for FARM income only; passive income remains in the existing impact rules. */
public final class PositionEconomyRuleConfig {
    public static final double TOP_BASE_CS_PER_MINUTE = 8.1;
    public static final double MID_BASE_CS_PER_MINUTE = 8.6;
    public static final double ADC_BASE_CS_PER_MINUTE = 9.2;
    public static final double JUNGLE_BASE_CS_PER_MINUTE = 6.6;
    public static final double SUPPORT_BASE_CS_PER_MINUTE = 0.0;
    public static final int FARMING_BASELINE = 14;
    public static final double FARMING_MULTIPLIER_PER_POINT = 0.02;
    public static final double MIN_FARMING_MULTIPLIER = 0.74;
    public static final double MAX_FARMING_MULTIPLIER = 1.12;
    public static final int EXPLICIT_FARMING_REALIZATION_START_SECONDS = 180;
    public static final int EXPLICIT_FARMING_REALIZATION_FULL_SECONDS = 900;
    public static final int CS_GOLD = 20;
    public static final int ECONOMY_START_SECONDS = 70;
    public static final int PASSIVE_GOLD_PER_TICK = 20;
    public static final int SUPPORT_QUEST_GOLD_PER_TICK = 9;
    public static final int ROLE_QUEST_ACTIVATION_SECONDS = 840;
    public static final int BOT_QUEST_GOLD_PER_CS = 2;

    private PositionEconomyRuleConfig() {
    }

    public static int passiveGoldPerTick(Position position) {
        return PASSIVE_GOLD_PER_TICK
                + (position == Position.SUPPORT ? SUPPORT_QUEST_GOLD_PER_TICK : 0);
    }

    public static int farmGoldPerCs(Position position, int currentTimeSeconds) {
        return CS_GOLD + (position == Position.ADC
                && currentTimeSeconds >= ROLE_QUEST_ACTIVATION_SECONDS ? BOT_QUEST_GOLD_PER_CS : 0);
    }
}
