package com.lolfm.simulator;

/** SR 26.16 policy: 25.09 encounter times + 26.1 rewards + 26.12 true-damage correction. */
public final class UpperObjectiveRuleConfig {
    private UpperObjectiveRuleConfig() {}
    public static final String VERSION = "SR_26_16_UPPER_OBJECTIVES_V1";
    public static final int GRUB_SPAWN = 480, GRUB_DESPAWN = 885, GRUB_COUNT = 3;
    public static final int HERALD_SPAWN = 900, HERALD_DESPAWN = 1185;
    public static final int GRUB_GOLD = 30, GRUB_XP = 65, HERALD_GOLD = 100, HERALD_XP = 240;
    public static final int EYE_LIFETIME = 240;
    public static final double HERALD_FIRST_CHARGE_DAMAGE = 3000;
    // Abstract clear/return/summon windows, not per-frame Riot movement simulation.
    public static final int ATTEMPT_INTERVAL = 20, CAPTURE_FARM_COST = 20;
    public static final int SUMMON_DELAY = 20, CHARGE_DELAY = 10, MERCENARY_LIFETIME = 90;
    public static final int MAX_CHARGES = 3;
    public static final double LATER_CHARGE_MULTIPLIER = .60;
    public static final double ATTEMPT_CHANCE = .25;
    public static final double TOP_PRIORITY_WEIGHT = .65, MID_PRIORITY_WEIGHT = .35;
    public static final double GRUB_URGENCY = .10, HERALD_URGENCY = .20;
    // Ten-second structure tick folds burn and, at three stacks, mite contribution into one bounded term.
    public static double grubStructureDamage(int stacks, int attackers) {
        return switch (stacks) { case 1 -> 32; case 2 -> 96; case 3 -> 192; default -> 0; } * attackers;
    }
}
