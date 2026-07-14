package com.lolfm.simulator;

import com.lolfm.domain.Position;
import com.lolfm.domain.PlayerAttributes;
import java.util.Objects;

public class PlayerState {

    private final String playerName;
    private final Position position;
    private final int mechanics;
    private final int aggression;
    private final int farming;
    private final int teamfighting;
    private int kills;
    private int deaths;
    private int assists;
    private int cs;
    private int gold;
    private int lastDeathAtSeconds = -1;
    private int respawnAtSeconds;
    private int farmResumeAtSeconds;
    private final boolean farmRecoveryEnabled;
    private int elderBuffExpiresAtSeconds = -1;
    private double bountyProgress;
    private double pendingCombatBountyProgress;
    private int lastVisibleShutdownGold;
    private int totalShutdownGoldEarned;
    private int totalShutdownGoldGiven;

    private final PlayerActivityState activityState = new PlayerActivityState();
    private final RoamActionState roamActionState = new RoamActionState();
    public PlayerState(String playerName, Position position, int startingGold) {
        this(playerName, position, new PlayerAttributes(
                PlayerImpactRuleConfig.BASELINE_ATTRIBUTE,
                PlayerImpactRuleConfig.BASELINE_ATTRIBUTE,
                PlayerImpactRuleConfig.BASELINE_ATTRIBUTE,
                PlayerImpactRuleConfig.BASELINE_ATTRIBUTE
        ), startingGold);
    }

    public PlayerState(String playerName, Position position, PlayerAttributes attributes, int startingGold) {
        this(playerName, position, attributes, startingGold, true);
    }

    PlayerState(String playerName, Position position, PlayerAttributes attributes, int startingGold,
                boolean farmRecoveryEnabled) {
        this.playerName = playerName;
        this.position = Objects.requireNonNull(position, "position");
        this.mechanics = PlayerImpactRuleConfig.normalize(attributes.getMechanics());
        this.aggression = PlayerImpactRuleConfig.normalize(attributes.getAggression());
        this.farming = PlayerImpactRuleConfig.normalize(attributes.getFarming());
        this.teamfighting = PlayerImpactRuleConfig.normalize(attributes.getTeamfighting());
        this.gold = startingGold;
        this.respawnAtSeconds = 0;
        this.farmResumeAtSeconds = 0;
        this.farmRecoveryEnabled = farmRecoveryEnabled;
    }

    public String getPlayerName() {
        return playerName;
    }

    public Position getPosition() {
        return position;
    }

    public int getMechanics() { return mechanics; }
    public int getAggression() { return aggression; }
    public int getFarming() { return farming; }
    public int getTeamfighting() { return teamfighting; }

    public int getKills() {
        return kills;
    }

    public int getDeaths() {
        return deaths;
    }

    public int getAssists() {
        return assists;
    }

    public int getCs() {
        return cs;
    }

    public int getGold() {
        return gold;
    }

    public int getRespawnAtSeconds() { return respawnAtSeconds; }
    public int getLastDeathAtSeconds() { return lastDeathAtSeconds; }
    public int getFarmResumeAtSeconds() { return farmResumeAtSeconds; }
    public int getElderBuffExpiresAtSeconds() { return elderBuffExpiresAtSeconds; }
    public void grantElderBuff(int currentTimeSeconds, int durationSeconds) { elderBuffExpiresAtSeconds = currentTimeSeconds + durationSeconds; }
    public boolean hasActiveElderBuff(int currentTimeSeconds) { return isAlive(currentTimeSeconds) && currentTimeSeconds < elderBuffExpiresAtSeconds; }
    public int getElderBuffRemainingSeconds(int currentTimeSeconds) { return hasActiveElderBuff(currentTimeSeconds) ? elderBuffExpiresAtSeconds - currentTimeSeconds : 0; }
    public void removeElderBuff() { elderBuffExpiresAtSeconds = -1; }

    public boolean isAlive(int currentTimeSeconds) {
        return currentTimeSeconds >= respawnAtSeconds;
    }

    public boolean canFarmAt(int currentTimeSeconds) {
        return isAlive(currentTimeSeconds) && currentTimeSeconds >= farmResumeAtSeconds;
    }

    public boolean canParticipateInMajorCombatAt(int currentTimeSeconds) {
        return isAlive(currentTimeSeconds) && activityState.getActivityType() == PlayerActivityType.DEFAULT_ROLE;
    }
    public PlayerActivityState getActivityState() { return activityState; }
    public RoamActionState getRoamActionState() { return roamActionState; }
    public void expireActivityIfNeeded(int currentTimeSeconds) { activityState.expireIfNeeded(currentTimeSeconds); }
    public void beginRoamActivity(Lane originLane, Lane targetLane, int currentTimeSeconds) {
        activityState.beginRoam(originLane, targetLane, currentTimeSeconds,
                currentTimeSeconds + RoamRuleConfig.ROAM_ACTIVITY_SECONDS);
    }
    public int farmReturnSecondsRemaining(int currentTimeSeconds) {
        return Math.max(0, farmResumeAtSeconds - currentTimeSeconds);
    }

    public void addKill() {
        kills++;
    }

    public void addDeath() {
        deaths++;
    }

    public void markDead(int currentTimeSeconds, int respawnDelaySeconds) {
        if (!isAlive(currentTimeSeconds)) return;
        addDeath();
        activityState.clear();
        removeElderBuff();
        lastDeathAtSeconds = currentTimeSeconds;
        respawnAtSeconds = currentTimeSeconds + respawnDelaySeconds;
        int returnDelay = farmRecoveryEnabled
                ? FarmRecoveryRuleConfig.returnDelaySeconds(position, currentTimeSeconds)
                : 0;
        farmResumeAtSeconds = Math.max(farmResumeAtSeconds, respawnAtSeconds + returnDelay);
    }

    public void respawn() {
        respawnAtSeconds = 0;
    }

    public void addAssist() {
        assists++;
    }

    public void addCs(int amount) {
        cs += amount;
    }

    public void addGold(int amount) {
        gold += amount;
    }

    public double getBountyProgress() { return bountyProgress; }
    public double getPendingCombatBountyProgress() { return pendingCombatBountyProgress; }
    public int getLastVisibleShutdownGold() { return lastVisibleShutdownGold; }
    public int getTotalShutdownGoldEarned() { return totalShutdownGoldEarned; }
    public int getTotalShutdownGoldGiven() { return totalShutdownGoldGiven; }
    public void addImmediateBountyProgress(double amount) { bountyProgress = Math.max(0.0, bountyProgress + amount); }
    public void addPendingCombatBountyProgress(double amount) { pendingCombatBountyProgress = Math.max(0.0, pendingCombatBountyProgress + amount); }
    public void commitPendingCombatBountyProgress() { addImmediateBountyProgress(pendingCombatBountyProgress); pendingCombatBountyProgress = 0.0; }
    public void clearPendingCombatBountyProgress() { pendingCombatBountyProgress = 0.0; }
    public void reduceBountyProgress(double amount) { bountyProgress = Math.max(0.0, bountyProgress - Math.max(0.0, amount)); }
    public double getRawPositiveBounty() { return Math.max(0.0, bountyProgress - BountyRuleConfig.BOUNTY_FREE_BUFFER); }
    public void setLastVisibleShutdownGold(int amount) { lastVisibleShutdownGold = Math.max(0, amount); }

    /** A payout consumes at most 700 raw bounty and retains only the excess for the next life. */
    public void consumeShutdownBounty() {
        double carryOverRaw = Math.max(0.0, getRawPositiveBounty() - BountyRuleConfig.MAX_SHUTDOWN_PAYOUT);
        bountyProgress = carryOverRaw > 0.0 ? BountyRuleConfig.BOUNTY_FREE_BUFFER + carryOverRaw : 0.0;
        lastVisibleShutdownGold = 0;
    }

    public void addShutdownGoldEarned(int amount) { totalShutdownGoldEarned += Math.max(0, amount); }
    public void addShutdownGoldGiven(int amount) { totalShutdownGoldGiven += Math.max(0, amount); }

    public void setRespawnAtSeconds(int respawnAtSeconds) {
        this.respawnAtSeconds = respawnAtSeconds;
    }
}
