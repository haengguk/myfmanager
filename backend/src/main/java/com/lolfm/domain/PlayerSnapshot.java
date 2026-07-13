package com.lolfm.domain;

public class PlayerSnapshot {

    private final String playerName;
    private final String teamName;
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

    public PlayerSnapshot(
            String playerName,
            String teamName,
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
            double bountyProgress
    ) {
        this.playerName = playerName;
        this.teamName = teamName;
        this.position = position;
        this.kills = kills;
        this.deaths = deaths;
        this.assists = assists;
        this.cs = cs;
        this.gold = gold;
        this.alive = alive;
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
    }

    public String getPlayerName() { return playerName; }
    public String getTeamName() { return teamName; }
    public Position getPosition() { return position; }
    public int getKills() { return kills; }
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
}
