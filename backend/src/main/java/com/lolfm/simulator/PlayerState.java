package com.lolfm.simulator;

import com.lolfm.domain.Position;
import com.lolfm.domain.PlayerAttributes;
import com.lolfm.domain.PlayerSkill;
import com.lolfm.player.PlayerId;
import java.util.Objects;

public class PlayerState {

    private final PlayerKey playerKey;
    private final PlayerId playerId;
    private final String playerName;
    private final Position position;
    private final int mechanics;
    private final int aggression;
    private final int farming;
    private final int teamfighting;
    private final PlayerMatchPerformance matchPerformance;
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
    private final PlayerProgressionState progressionState;

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
        this(null, null, playerName, position, attributes, null, startingGold, farmRecoveryEnabled);
    }

    PlayerState(String playerName, Position position, PlayerAttributes attributes,
                PlayerMatchPerformance matchPerformance, int startingGold,
                boolean farmRecoveryEnabled) {
        this(null, null, playerName, position, attributes, matchPerformance,
                startingGold, farmRecoveryEnabled);
    }

    public PlayerState(PlayerKey playerKey, PlayerId playerId, String playerName, Position position,
                PlayerAttributes attributes, PlayerMatchPerformance matchPerformance,
                int startingGold, boolean farmRecoveryEnabled) {
        this.playerKey = playerKey;
        this.playerId = playerId;
        this.playerName = Objects.requireNonNull(playerName, "playerName").trim();
        if (this.playerName.isBlank()) throw new IllegalArgumentException("playerName is required");
        this.position = Objects.requireNonNull(position, "position");
        if (playerKey != null && playerKey.position() != position) {
            throw new IllegalArgumentException("PlayerKey position mismatch");
        }
        this.matchPerformance = matchPerformance;
        this.mechanics = PlayerImpactRuleConfig.normalize(attributes.getMechanics());
        this.aggression = PlayerImpactRuleConfig.normalize(attributes.getAggression());
        this.farming = PlayerImpactRuleConfig.normalize(attributes.getFarming());
        this.teamfighting = PlayerImpactRuleConfig.normalize(attributes.getTeamfighting());
        this.gold = startingGold;
        this.respawnAtSeconds = 0;
        this.farmResumeAtSeconds = 0;
        this.farmRecoveryEnabled = farmRecoveryEnabled;
        this.progressionState = new PlayerProgressionState(position);
    }

    public PlayerKey getPlayerKey() { return playerKey; }
    public PlayerId getPlayerId() { return playerId; }
    public boolean hasStablePlayerId() { return playerId != null; }
    public PlayerId requirePlayerId() {
        if (playerId == null) throw new IllegalStateException("PlayerState has no stable PlayerId: " + playerName);
        return playerId;
    }
    /** Structured event identity; legacy fixtures fall back to their match slot, never display text. */
    public String getStructuredPlayerId() {
        if (playerId != null) return playerId.value();
        if (playerKey != null) return playerKey.stableId();
        return "LEGACY:" + position.name();
    }
    public String getPlayerName() { return playerName; }
    public Position getPosition() { return position; }

    public int getMechanics() { return rounded(execution(PlayerSkill.MECHANICS, mechanics)); }
    public int getAggression() { return rounded(rating(PlayerSkill.DECISION_MAKING, aggression)); }
    public int getFarming() {
        PlayerSkill skill = position == Position.JUNGLE
                ? PlayerSkill.JUNGLE_RESOURCE_MANAGEMENT
                : position == Position.SUPPORT ? PlayerSkill.LANE_SUPPORT : PlayerSkill.FARMING;
        return rounded(rating(skill, farming));
    }
    public int getTeamfighting() { return rounded(execution(PlayerSkill.COMBAT_EXECUTION, teamfighting)); }

    public double rating(PlayerSkill skill) {
        return rating(skill, PlayerImpactRuleConfig.BASELINE_ATTRIBUTE);
    }

    public double execution(PlayerSkill skill) {
        return execution(skill, PlayerImpactRuleConfig.BASELINE_ATTRIBUTE);
    }

    public int getChampionProficiency() {
        return matchPerformance == null
                ? com.lolfm.domain.ChampionProficiencies.NEUTRAL
                : matchPerformance.championProficiency();
    }

    public boolean hasMatchPerformance() { return matchPerformance != null; }
    public PlayerMatchPerformance getMatchPerformance() { return matchPerformance; }

    private double rating(PlayerSkill skill, int legacyFallback) {
        if (matchPerformance != null) return matchPerformance.rating(skill);
        if (!skill.appliesTo(position)) throw new IllegalArgumentException(skill + " does not apply to " + position);
        return switch (skill) {
            case MECHANICS -> mechanics;
            case DECISION_MAKING -> aggression;
            case COMBAT_EXECUTION -> teamfighting;
            case FARMING, JUNGLE_RESOURCE_MANAGEMENT, LANE_SUPPORT -> farming;
            case TRADING, LANE_PRESSURE, LANE_INTERVENTION, ENGAGE_EXECUTION -> aggression;
            default -> legacyFallback;
        };
    }

    private double execution(PlayerSkill skill, int legacyFallback) {
        if (matchPerformance != null) return matchPerformance.execution(skill);
        return rating(skill, legacyFallback);
    }

    private int rounded(double value) { return (int) Math.round(value); }

    public int getKills() { return kills; }
    public int getDeaths() { return deaths; }
    public int getAssists() { return assists; }
    public int getCs() { return cs; }
    public int getGold() { return gold; }
    public int getRespawnAtSeconds() { return respawnAtSeconds; }
    public int getLastDeathAtSeconds() { return lastDeathAtSeconds; }
    public int getFarmResumeAtSeconds() { return farmResumeAtSeconds; }
    public int getElderBuffExpiresAtSeconds() { return elderBuffExpiresAtSeconds; }
    public void grantElderBuff(int currentTimeSeconds, int durationSeconds) {
        elderBuffExpiresAtSeconds = currentTimeSeconds + durationSeconds;
    }
    public boolean hasActiveElderBuff(int currentTimeSeconds) {
        return isAlive(currentTimeSeconds) && currentTimeSeconds < elderBuffExpiresAtSeconds;
    }
    public int getElderBuffRemainingSeconds(int currentTimeSeconds) {
        return hasActiveElderBuff(currentTimeSeconds) ? elderBuffExpiresAtSeconds - currentTimeSeconds : 0;
    }
    public void removeElderBuff() { elderBuffExpiresAtSeconds = -1; }

    public boolean isAlive(int currentTimeSeconds) { return currentTimeSeconds >= respawnAtSeconds; }
    public boolean canFarmAt(int currentTimeSeconds) {
        return isAlive(currentTimeSeconds) && currentTimeSeconds >= farmResumeAtSeconds;
    }
    public void blockFarmUntil(int untilSeconds) {
        farmResumeAtSeconds = Math.max(farmResumeAtSeconds, untilSeconds);
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
    public void beginSiegeActivity(Lane routeLane, String actionId,
                                   int currentTimeSeconds, int untilSeconds) {
        activityState.beginSiege(routeLane, actionId, currentTimeSeconds, untilSeconds);
        blockFarmUntil(untilSeconds);
    }
    public void extendSiegeActivity(String actionId, int untilSeconds) {
        activityState.extendSiege(actionId, untilSeconds);
        if (activityState.isSiegingAction(actionId)) blockFarmUntil(untilSeconds);
    }
    public void endSiegeActivity(String actionId) { activityState.clearSiege(actionId); }
    public int farmReturnSecondsRemaining(int currentTimeSeconds) {
        return Math.max(0, farmResumeAtSeconds - currentTimeSeconds);
    }

    public void addKill() { kills++; }
    public void addDeath() { deaths++; }

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

    public void respawn() { respawnAtSeconds = 0; }
    public void addAssist() { assists++; }
    public void addCs(int amount) { cs += amount; }
    public void addGold(int amount) { addGold(amount, GoldSource.OTHER, 0); }
    public void addGold(int amount, GoldSource source, int timeSeconds) {
        gold += amount;
        progressionState.awardEarnedGold(amount, source, timeSeconds);
    }
    public PlayerProgressionState getProgressionState() { return progressionState; }
    void configureProgression(TeamSide side, boolean enabled, ProgressionExecutionStats stats) {
        progressionState.configure(side, enabled, stats);
    }

    public double getBountyProgress() { return bountyProgress; }
    public double getPendingCombatBountyProgress() { return pendingCombatBountyProgress; }
    public int getLastVisibleShutdownGold() { return lastVisibleShutdownGold; }
    public int getTotalShutdownGoldEarned() { return totalShutdownGoldEarned; }
    public int getTotalShutdownGoldGiven() { return totalShutdownGoldGiven; }
    public void addImmediateBountyProgress(double amount) {
        bountyProgress = Math.max(0.0, bountyProgress + amount);
    }
    public void addPendingCombatBountyProgress(double amount) {
        pendingCombatBountyProgress = Math.max(0.0, pendingCombatBountyProgress + amount);
    }
    public void commitPendingCombatBountyProgress() {
        addImmediateBountyProgress(pendingCombatBountyProgress);
        pendingCombatBountyProgress = 0.0;
    }
    public void clearPendingCombatBountyProgress() { pendingCombatBountyProgress = 0.0; }
    public void reduceBountyProgress(double amount) {
        bountyProgress = Math.max(0.0, bountyProgress - Math.max(0.0, amount));
    }
    public double getRawPositiveBounty() {
        return Math.max(0.0, bountyProgress - BountyRuleConfig.BOUNTY_FREE_BUFFER);
    }
    public void setLastVisibleShutdownGold(int amount) {
        lastVisibleShutdownGold = Math.max(0, amount);
    }

    /** A payout consumes at most 700 raw bounty and retains only the excess for the next life. */
    public void consumeShutdownBounty() {
        double carryOverRaw = Math.max(0.0,
                getRawPositiveBounty() - BountyRuleConfig.MAX_SHUTDOWN_PAYOUT);
        bountyProgress = carryOverRaw > 0.0
                ? BountyRuleConfig.BOUNTY_FREE_BUFFER + carryOverRaw : 0.0;
        lastVisibleShutdownGold = 0;
    }

    public void addShutdownGoldEarned(int amount) { totalShutdownGoldEarned += Math.max(0, amount); }
    public void addShutdownGoldGiven(int amount) { totalShutdownGoldGiven += Math.max(0, amount); }
    public void setRespawnAtSeconds(int respawnAtSeconds) { this.respawnAtSeconds = respawnAtSeconds; }
}
