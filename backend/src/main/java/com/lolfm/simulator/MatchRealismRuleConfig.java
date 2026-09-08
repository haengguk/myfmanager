package com.lolfm.simulator;

/** Versioned abstract movement/decision rules; no champion, team or seed exceptions. */
public final class MatchRealismRuleConfig {
    private MatchRealismRuleConfig() {}
    public static final String VERSION = "MATCH_REALISM_V1";
    // First wave contact after deployment: center is shorter than side lanes.
    public static final int MID_CONTACT_SECONDS = 60;
    public static final int SIDE_CONTACT_SECONDS = 70;
    public static final int BASE_RETURN_SECONDS = 30;
    public static final int NEAR_BASE_RETURN_SECONDS = 10;
    public static final int RESPAWN_BASE_PRESENCE_SECONDS = 10;
    public static final double DEFENSE_ATTEMPT_CHANCE = .35;
    public static final double DEFENSE_KILL_CHANCE = .55;
    public static final double SIEGE_POWER_ADVANTAGE = 1.25;
    public static final int NEXUS_FINISH_ATTACKS = 1;
    public static final int NEXUS_COMMIT_SECONDS = 20;
    public static final int FIRST_CAMP_SPAWN_SECONDS = 55;
    public static final int DELAYED_CAMP_SPAWN_SECONDS = 67;
    public static final int FAST_WAVES_AT_SECONDS = 840;
    public static final int LATE_WAVES_AT_SECONDS = 1800;
    public static int wavePeriod(int time) { return time >= LATE_WAVES_AT_SECONDS ? 20 : time >= FAST_WAVES_AT_SECONDS ? 25 : 30; }
    public static int waveMinions(int time) { return time >= LATE_WAVES_AT_SECONDS ? 5 : 6; }
    public static final int CAMP_CLEAR_WORK_SECONDS = 30;
    public static final int CAMP_RESPAWN_SECONDS = 135;
    public static final int BUFF_CAMP_RESPAWN_SECONDS = 300;
    public static final int CAMP_CS = 4;
    public static final int CAMP_XP = 180;
    public static final int SUPPORT_ROTATION_START_SECONDS = 480;
    public static final int SUPPORT_ROTATION_SECONDS = 30;
    public static final int SUPPORT_ROTATION_INTERVAL_SECONDS = 120;
    public static int contactAt(Lane lane) {
        return lane == Lane.MID ? MID_CONTACT_SECONDS : SIDE_CONTACT_SECONDS;
    }
}
