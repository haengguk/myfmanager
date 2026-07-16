package com.lolfm.domain;

public class PlayerSnapshot {

    private final String playerName;
    private final String teamName;
    private final com.lolfm.simulator.TeamSide teamSide;
    private final Position position;
    private final int kills;
    private final int deaths;
    private final int assists;
    private final int cs;
    private final int gold;
    private final boolean alive;
    private final int respawnAtSeconds;
    private final int respawnRemainingSeconds;
    private final boolean canFarm;
    private final int farmResumeAtSeconds;
    private final int farmReturnSecondsRemaining;
    private final boolean hasElderBuff;
    private final int elderBuffRemainingSeconds;
    private final int shutdownBountyGold;
    private final boolean hasShutdownBounty;
    private final int totalShutdownGoldEarned;
    private final int totalShutdownGoldGiven;
    private final double bountyProgress;
    private final com.lolfm.simulator.PlayerActivityType activityType;
    private final com.lolfm.simulator.Lane activityOriginLane;
    private final com.lolfm.simulator.Lane activityTargetLane;
    private final int activityUntilSeconds;
    private final int activitySecondsRemaining;
    private final int roamFarmBlockedUntilSeconds;
    private final PlayerProgressionSnapshot progression;

    public PlayerSnapshot(
            String playerName,
            String teamName,
            com.lolfm.simulator.TeamSide teamSide,
            Position position,
            int kills,
            int deaths,
            int assists,
            int cs,
            int gold,
            boolean alive,
            int respawnAtSeconds,
            int respawnRemainingSeconds,
            boolean canFarm,
            int farmResumeAtSeconds,
            int farmReturnSecondsRemaining,
            boolean hasElderBuff,
            int elderBuffRemainingSeconds,
            int shutdownBountyGold,
            boolean hasShutdownBounty,
            int totalShutdownGoldEarned,
            int totalShutdownGoldGiven,
            double bountyProgress,
            com.lolfm.simulator.PlayerActivityType activityType,
            com.lolfm.simulator.Lane activityOriginLane,
            com.lolfm.simulator.Lane activityTargetLane,
            int activityUntilSeconds,
            int activitySecondsRemaining,
            int roamFarmBlockedUntilSeconds,
            PlayerProgressionSnapshot progression
    ) {
        this.playerName = playerName;
        this.teamName = teamName;
        this.position = position;
        this.kills = kills;
        this.teamSide = teamSide;
        this.deaths = deaths;
        this.assists = assists;
        this.cs = cs;
        this.gold = gold;
        this.alive = alive;
        this.activityType = activityType;
        this.activityOriginLane = activityOriginLane;
        this.activityTargetLane = activityTargetLane;
        this.activityUntilSeconds = activityUntilSeconds;
        this.activitySecondsRemaining = activitySecondsRemaining;
        this.roamFarmBlockedUntilSeconds = roamFarmBlockedUntilSeconds;
        this.respawnAtSeconds = respawnAtSeconds;
        this.respawnRemainingSeconds = respawnRemainingSeconds;
        this.canFarm = canFarm;
        this.farmResumeAtSeconds = farmResumeAtSeconds;
        this.farmReturnSecondsRemaining = farmReturnSecondsRemaining;
        this.hasElderBuff = hasElderBuff;
        this.elderBuffRemainingSeconds = elderBuffRemainingSeconds;
        this.shutdownBountyGold = shutdownBountyGold;
        this.hasShutdownBounty = hasShutdownBounty;
        this.totalShutdownGoldEarned = totalShutdownGoldEarned;
        this.totalShutdownGoldGiven = totalShutdownGoldGiven;
        this.bountyProgress = bountyProgress;
        this.progression = progression;
    }

    public String getPlayerName() { return playerName; }
    public String getTeamName() { return teamName; }
    public Position getPosition() { return position; }
    public int getKills() { return kills; }
    public com.lolfm.simulator.TeamSide getTeamSide() { return teamSide; }
    public com.lolfm.simulator.PlayerActivityType getActivityType() { return activityType; }
    public com.lolfm.simulator.Lane getActivityOriginLane() { return activityOriginLane; }
    public com.lolfm.simulator.Lane getActivityTargetLane() { return activityTargetLane; }
    public int getActivityUntilSeconds() { return activityUntilSeconds; }
    public int getActivitySecondsRemaining() { return activitySecondsRemaining; }
    public int getRoamFarmBlockedUntilSeconds() { return roamFarmBlockedUntilSeconds; }
    public int getDeaths() { return deaths; }
    public int getAssists() { return assists; }
    public int getCs() { return cs; }
    public int getGold() { return gold; }
    public boolean isAlive() { return alive; }
    public int getRespawnAtSeconds() { return respawnAtSeconds; }
    public int getRespawnRemainingSeconds() { return respawnRemainingSeconds; }
    public boolean isCanFarm() { return canFarm; }
    public int getFarmResumeAtSeconds() { return farmResumeAtSeconds; }
    public int getFarmReturnSecondsRemaining() { return farmReturnSecondsRemaining; }
    public boolean isHasElderBuff() { return hasElderBuff; }
    public int getElderBuffRemainingSeconds() { return elderBuffRemainingSeconds; }
    public int getShutdownBountyGold() { return shutdownBountyGold; }
    public boolean isHasShutdownBounty() { return hasShutdownBounty; }
    public int getTotalShutdownGoldEarned() { return totalShutdownGoldEarned; }
    public int getTotalShutdownGoldGiven() { return totalShutdownGoldGiven; }
    public double getBountyProgress() { return bountyProgress; }
    public PlayerProgressionSnapshot getProgression() { return progression; }
    public int getLevel() { return progression.level(); }
    public int getTotalExperience() { return progression.totalExperience(); }
    public int getCurrentLevelStartExperience() { return progression.currentLevelStartExperience(); }
    public int getNextLevelTotalExperience() { return progression.nextLevelTotalExperience(); }
    public double getLevelProgressRatio() { return progression.levelProgressRatio(); }
    public com.lolfm.simulator.ItemProgressStage getItemStage() { return progression.itemStage(); }
    public int getProgressionEarnedGold() { return progression.progressionEarnedGold(); }
    public int getNextItemStageGold() { return progression.nextItemStageGold(); }
    public double getItemProgressRatio() { return progression.itemProgressRatio(); }
    public ProgressionPowerSnapshot getProgressionPower() { return progression.progressionPower(); }
}
