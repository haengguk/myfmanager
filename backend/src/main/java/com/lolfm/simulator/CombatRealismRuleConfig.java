package com.lolfm.simulator;

import com.lolfm.domain.Position;

/** Production tuning for professional-match combat frequency and attribution. */
public final class CombatRealismRuleConfig {
    public static final double EARLY_GENERIC_SKIRMISH_CHANCE = 0.030;
    public static final double LATE_GENERIC_SKIRMISH_CHANCE = 0.040;
    public static final double MIN_GENERIC_SKIRMISH_CHANCE = 0.015;
    public static final double MAX_GENERIC_SKIRMISH_CHANCE = 0.065;
    public static final double STANDARD_TEAMFIGHT_TRIGGER_CHANCE = 0.025;

    public static final double TEAMFIGHT_GOLD_EDGE_DIVISOR = 1_200.0;
    public static final double TEAMFIGHT_KILL_EDGE_WEIGHT = 1.50;
    public static final double MAX_TEAMFIGHT_KILL_EDGE = 9.0;
    public static final double MAX_TEAMFIGHT_DECISION_EDGE = 12.0;
    public static final int MIN_ALIVE_PLAYERS_FOR_STANDARD_TEAMFIGHT = 3;

    private CombatRealismRuleConfig() {
    }

    public static double killerRoleMultiplier(Position position) {
        return switch (position) {
            case ADC -> 1.25;
            case MID -> 1.15;
            case JUNGLE -> 1.00;
            case TOP -> 0.95;
            case SUPPORT -> 0.28;
        };
    }
}
