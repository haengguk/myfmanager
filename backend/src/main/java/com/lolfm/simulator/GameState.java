package com.lolfm.simulator;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public class GameState {

    private int currentTimeSeconds;
    private final TeamState blueTeamState;
    private final TeamState redTeamState;
    private final ObjectiveState objectiveState;
    private final MapState mapState;
    private final EnumMap<Lane, LaneState> laneStates = new EnumMap<>(Lane.class);
    private int lastLanePressureResolvedAtSeconds = -1;
    private int duplicateLanePressureResolutionCount;
    private int lastLaneCombatResolvedAtSeconds = -1;
    private int lastJungleGankResolvedAtSeconds = -1;
    private final EnumMap<TeamSide, JungleActionState> jungleActionStates = new EnumMap<>(TeamSide.class);
    private final CombatExecutionStats combatExecutionStats = new CombatExecutionStats();
    private boolean finished;
    private TeamSide winnerSide;
    private GameEndReason endReason;
    private int endedAtSeconds = -1;
    private int pushAttemptCount;
    private int pushSuccessCount;
    private final EnumMap<PushFailureReason, Integer> pushFailureCounts = new EnumMap<>(PushFailureReason.class);
    private final List<Integer> dragonCaptureTimes = new ArrayList<>();
    private final List<Integer> dragonSpawnAliveSeconds = new ArrayList<>();
    private final List<DragonCaptureRecord> dragonCaptures = new ArrayList<>();
    private int generalDragonAttemptCount;
    private int generalDragonCaptureCount;
    private int postFightDragonCaptureCount;
    private int pushWindowCount;
    private int pushWindowStructureCount;
    private int aceWindowNexusEndCount;
    private TeamSide lastBigWinSide;
    private int lastBigWinTimeSeconds;
    private TeamSide lastAceSide;
    private int lastAceTimeSeconds;

    public GameState(TeamState blueTeamState, TeamState redTeamState) {
        this.currentTimeSeconds = 0;
        this.blueTeamState = blueTeamState;
        this.redTeamState = redTeamState;
        this.objectiveState = new ObjectiveState();
        this.mapState = new MapState();
        for (Lane lane : Lane.values()) laneStates.put(lane, new LaneState(lane));
        for (TeamSide side : TeamSide.values()) jungleActionStates.put(side, new JungleActionState());
        this.lastBigWinTimeSeconds = -1;
        this.lastAceTimeSeconds = -1;
    }

    public int getCurrentTimeSeconds() {
        return currentTimeSeconds;
    }

    public TeamState getBlueTeamState() {
        return blueTeamState;
    }

    public TeamState getRedTeamState() {
        return redTeamState;
    }

    public ObjectiveState getObjectiveState() {
        return objectiveState;
    }

    public MapState getMapState() {
        return mapState;
    }

    public LaneState laneState(Lane lane) { return laneStates.get(lane); }
    public Map<Lane, LaneState> getLaneStates() { return Map.copyOf(laneStates); }
    public int getLastLanePressureResolvedAtSeconds() { return lastLanePressureResolvedAtSeconds; }
    public int getDuplicateLanePressureResolutionCount() { return duplicateLanePressureResolutionCount; }
    public boolean shouldResolveLaneCombatAt(int time) { if(time < lastLaneCombatResolvedAtSeconds) throw new IllegalArgumentException("Lane combat time cannot move backwards"); if(time==lastLaneCombatResolvedAtSeconds) return false; return time >= LaneCombatRuleConfig.LANE_COMBAT_START_SECONDS && time <= LaneCombatRuleConfig.LANE_COMBAT_END_SECONDS && time % LaneCombatRuleConfig.LANE_COMBAT_INTERVAL_SECONDS==0; }
    public void markLaneCombatResolvedAt(int time) { lastLaneCombatResolvedAtSeconds=time; }
    public int getLastLaneCombatResolvedAtSeconds() { return lastLaneCombatResolvedAtSeconds; }
    public JungleActionState jungleActionState(TeamSide side) { return jungleActionStates.get(side); }
    public Map<TeamSide, JungleActionState> getJungleActionStates() { return Map.copyOf(jungleActionStates); }
    public CombatExecutionStats getCombatExecutionStats() { return combatExecutionStats; }
    public int getLastJungleGankResolvedAtSeconds() { return lastJungleGankResolvedAtSeconds; }
    public boolean shouldResolveJungleGankAt(int time) {
        if (time < lastJungleGankResolvedAtSeconds) throw new IllegalArgumentException("Jungle gank time cannot move backwards");
        if (time == lastJungleGankResolvedAtSeconds) return false;
        return time >= JungleGankRuleConfig.GANK_START_SECONDS
                && time <= JungleGankRuleConfig.GANK_END_SECONDS
                && time % JungleGankRuleConfig.GANK_EVALUATION_INTERVAL_SECONDS == 0;
    }
    public void markJungleGankResolvedAt(int time) {
        if (time <= lastJungleGankResolvedAtSeconds) throw new IllegalStateException("Jungle gank time was not advanced");
        lastJungleGankResolvedAtSeconds = time;
    }

    public boolean shouldResolveLanePressureAt(int currentTimeSeconds) {
        if (currentTimeSeconds < lastLanePressureResolvedAtSeconds) throw new IllegalArgumentException("Lane pressure time cannot move backwards");
        if (currentTimeSeconds == lastLanePressureResolvedAtSeconds) { duplicateLanePressureResolutionCount++; return false; }
        return currentTimeSeconds > 0 && currentTimeSeconds % LanePressureRuleConfig.PRESSURE_UPDATE_INTERVAL_SECONDS == 0;
    }

    public void markLanePressureResolvedAt(int currentTimeSeconds) {
        if (currentTimeSeconds <= lastLanePressureResolvedAtSeconds) throw new IllegalStateException("Lane pressure time was not advanced");
        lastLanePressureResolvedAtSeconds = currentTimeSeconds;
    }

    public void expireBaronBuffsIfNeeded() {
        blueTeamState.expireBaronBuffIfNeeded(currentTimeSeconds);
        redTeamState.expireBaronBuffIfNeeded(currentTimeSeconds);
    }

    public String getLastBigWinTeamName() {
        return lastBigWinSide == null ? null : getTeamState(lastBigWinSide).getTeamName();
    }

    public int getLastBigWinTimeSeconds() {
        return lastBigWinTimeSeconds;
    }

    public String getLastAceTeamName() {
        return lastAceSide == null ? null : getTeamState(lastAceSide).getTeamName();
    }

    public int getLastAceTimeSeconds() {
        return lastAceTimeSeconds;
    }

    public String getLastBaronTeamName() {
        TeamSide side = objectiveState.getLastBaronSide();
        return side == null ? null : getTeamState(side).getTeamName();
    }

    public int getLastBaronTimeSeconds() {
        return objectiveState.getLastBaronTimeSeconds();
    }

    public boolean isFinished() { return finished; }
    public TeamSide getWinnerSide() { return winnerSide; }
    public GameEndReason getEndReason() { return endReason; }
    public int getEndedAtSeconds() { return endedAtSeconds; }
    public void finish(TeamSide winningSide, GameEndReason reason) {
        if (finished) return;
        finished = true;
        winnerSide = winningSide;
        endReason = reason;
        endedAtSeconds = currentTimeSeconds;
    }

    public void timeout() {
        if (finished) return;
        finished = true;
        winnerSide = null;
        endReason = GameEndReason.SIMULATION_TIMEOUT;
        endedAtSeconds = currentTimeSeconds;
    }

    public void advanceTimeSeconds(int amount) {
        currentTimeSeconds += amount;
    }

    public TeamState getTeamState(TeamSide side) {
        return side == TeamSide.BLUE ? blueTeamState : redTeamState;
    }

    public TeamState getTeamState(String teamName) {
        if (blueTeamState.getTeamName().equals(teamName)) {
            return blueTeamState;
        }
        if (redTeamState.getTeamName().equals(teamName)) {
            return redTeamState;
        }
        throw new IllegalArgumentException("Unknown team state: " + teamName);
    }

    public TeamState getOpposingTeamState(String teamName) {
        return getTeamState(getTeamSide(teamName).opposite());
    }

    public TeamSide getTeamSide(String teamName) {
        if (blueTeamState.getTeamName().equals(teamName)) {
            return TeamSide.BLUE;
        }
        if (redTeamState.getTeamName().equals(teamName)) {
            return TeamSide.RED;
        }
        throw new IllegalArgumentException("Unknown team state: " + teamName);
    }

    public int getGoldDifference() {
        return Math.abs(blueTeamState.getGold() - redTeamState.getGold());
    }

    public int getKillDifference() {
        return Math.abs(blueTeamState.getKills() - redTeamState.getKills());
    }

    public int getPushAttemptCount() { return pushAttemptCount; }
    public int getPushSuccessCount() { return pushSuccessCount; }
    public void recordPushAttempt() { pushAttemptCount++; }
    public void recordPushSuccess() { pushSuccessCount++; }
    public void recordPushFailure(PushFailureReason reason) {
        pushFailureCounts.merge(reason, 1, Integer::sum);
    }
    public Map<PushFailureReason, Integer> getPushFailureCounts() {
        return Map.copyOf(pushFailureCounts);
    }

    public void recordGeneralDragonAttempt() { generalDragonAttemptCount++; }
    public void recordDragonCapture(TeamSide side, DragonCaptureSource source, int captureTimeSeconds, int aliveSeconds) {
        int safeAliveSeconds = Math.max(0, aliveSeconds);
        dragonCaptureTimes.add(captureTimeSeconds);
        dragonSpawnAliveSeconds.add(safeAliveSeconds);
        dragonCaptures.add(new DragonCaptureRecord(side, captureTimeSeconds, safeAliveSeconds, source));
        if (source == DragonCaptureSource.GENERAL) generalDragonCaptureCount++;
        else postFightDragonCaptureCount++;
    }
    public List<Integer> getDragonCaptureTimes() { return List.copyOf(dragonCaptureTimes); }
    public List<Integer> getDragonSpawnAliveSeconds() { return List.copyOf(dragonSpawnAliveSeconds); }
    public List<DragonCaptureRecord> getDragonCaptures() { return List.copyOf(dragonCaptures); }
    public int getGeneralDragonAttemptCount() { return generalDragonAttemptCount; }
    public int getGeneralDragonCaptureCount() { return generalDragonCaptureCount; }
    public int getPostFightDragonCaptureCount() { return postFightDragonCaptureCount; }

    public void recordPushWindow(int structureCount, boolean aceEndedNexus) {
        pushWindowCount++;
        pushWindowStructureCount += structureCount;
        if (aceEndedNexus) aceWindowNexusEndCount++;
    }
    public int getPushWindowCount() { return pushWindowCount; }
    public int getPushWindowStructureCount() { return pushWindowStructureCount; }
    public int getAceWindowNexusEndCount() { return aceWindowNexusEndCount; }

    public void recordBigWin(TeamSide side) {
        lastBigWinSide = side;
        lastBigWinTimeSeconds = currentTimeSeconds;
    }

    public void recordAce(TeamSide side) {
        lastAceSide = side;
        lastAceTimeSeconds = currentTimeSeconds;
    }

    public boolean hasRecentBigWin(String teamName, int windowSeconds) {
        return isRecentEvent(lastBigWinSide, lastBigWinTimeSeconds, teamName, windowSeconds);
    }

    public boolean hasRecentAce(String teamName, int windowSeconds) {
        return isRecentEvent(lastAceSide, lastAceTimeSeconds, teamName, windowSeconds);
    }

    public boolean hasRecentBaron(String teamName, int windowSeconds) {
        return isRecentEvent(
                objectiveState.getLastBaronSide(),
                objectiveState.getLastBaronTimeSeconds(),
                teamName,
                windowSeconds
        );
    }

    private boolean isRecentEvent(TeamSide side, int eventTimeSeconds, String teamName, int windowSeconds) {
        return side != null
                && getTeamState(side).getTeamName().equals(teamName)
                && eventTimeSeconds >= 0
                && currentTimeSeconds >= eventTimeSeconds
                && currentTimeSeconds - eventTimeSeconds <= windowSeconds;
    }
}
