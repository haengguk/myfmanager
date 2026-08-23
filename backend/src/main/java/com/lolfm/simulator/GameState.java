package com.lolfm.simulator;

import com.lolfm.champion.MatchChampionAssignments;
import com.lolfm.champion.ChampionMatchupCatalog;
import com.lolfm.champion.ChampionMatchupExecutionStats;
import com.lolfm.champion.ChampionMatchupMode;
import com.lolfm.champion.ChampionJungleClearProfileCatalog;
import com.lolfm.champion.ChampionRoleKey;
import com.lolfm.champion.ChampionRoleMatchupProfileCatalog;
import com.lolfm.champion.ChampionPowerExecutionStats;
import com.lolfm.champion.ChampionPowerProfileCatalog;
import com.lolfm.composition.CompositionRuntimeState;
import com.lolfm.domain.MatchEvent;
import com.lolfm.domain.MatchEventType;
import com.lolfm.domain.ProgressionEventData;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class GameState {

    private MatchChampionAssignments championAssignments;
    private ChampionPowerProfileCatalog championPowerProfileCatalog;
    private boolean championPowerEnabled;
    private final ChampionPowerExecutionStats championPowerExecutionStats = new ChampionPowerExecutionStats();
    private ChampionMatchupCatalog championMatchupCatalog;
    private ChampionRoleMatchupProfileCatalog championRoleMatchupProfileCatalog;
    private ChampionMatchupMode championMatchupMode = ChampionMatchupMode.OFF;
    private final ChampionMatchupExecutionStats championMatchupExecutionStats =
            new ChampionMatchupExecutionStats();
    private CompositionRuntimeState compositionRuntimeState = CompositionRuntimeState.off(0L);
    private ChampionJungleClearProfileCatalog jungleClearProfileCatalog;
    private JungleClearContribution jungleClearContribution =
            JungleClearContribution.DISABLED_NOT_INTEGRATED;
    private final EnumMap<TeamSide, JungleEconomyState> jungleEconomyStates =
            new EnumMap<>(TeamSide.class);
    private final JungleEconomyExecutionStats jungleEconomyExecutionStats =
            new JungleEconomyExecutionStats();
    private final EnumMap<TeamSide, JungleTempoState> jungleTempoStates =
            new EnumMap<>(TeamSide.class);
    private final JungleTempoExecutionStats jungleTempoExecutionStats =
            new JungleTempoExecutionStats();

    private int currentTimeSeconds;
    private final TeamState blueTeamState;
    private final TeamState redTeamState;
    private final ObjectiveState objectiveState;
    private final ObjectivePriorityExecutionStats objectivePriorityExecutionStats;
    private final ObjectivePriorityState objectivePriorityState;
    private final LanePhaseExecutionStats lanePhaseExecutionStats;
    private final LanePhaseState lanePhaseState;
    private final MidGameMacroState midGameMacroState;
    private final ObjectiveDecisionState objectiveDecisionState;
    private final LateGameState lateGameState;
    private final Set<PlayerState> majorCombatParticipantsThisTick = Collections.newSetFromMap(new IdentityHashMap<>());
    private final MapState mapState;
    private final EnumMap<TeamSide, Boolean> structureActionAttemptedThisTick = new EnumMap<>(TeamSide.class);
    private final EnumMap<TeamSide, Boolean> structureMutationPerformedThisTick = new EnumMap<>(TeamSide.class);
    private final EnumMap<TeamSide, Boolean> duplicateStructureAttemptPendingBySide = new EnumMap<>(TeamSide.class);
    private final StructureActionExecutionStats structureActionExecutionStats = new StructureActionExecutionStats();
    private final ProgressionExecutionStats progressionExecutionStats = new ProgressionExecutionStats();
    private boolean progressionEnabled = true;
    private boolean progressionPowerEnabled = true;
    private final EnumMap<Lane, LaneState> laneStates = new EnumMap<>(Lane.class);
    private int lastLanePressureResolvedAtSeconds = -1;
    private int duplicateLanePressureResolutionCount;
    private int lastLaneCombatResolvedAtSeconds = -1;
    private int lastJungleGankResolvedAtSeconds = -1;
    private int lastRoamEvaluationAtSeconds = -1;
    private final EnumMap<TeamSide, JungleActionState> jungleActionStates = new EnumMap<>(TeamSide.class);
    private final CombatExecutionStats combatExecutionStats = new CombatExecutionStats();
    private final CombatOutcomeExecutionStats combatOutcomeExecutionStats = new CombatOutcomeExecutionStats();
    private final RoamExecutionStats roamExecutionStats;
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
        this(blueTeamState, redTeamState, true, true, true);
    }

    public GameState(TeamState blueTeamState, TeamState redTeamState, boolean diagnosticsEnabled) {
        this(blueTeamState, redTeamState, diagnosticsEnabled, true, true);
    }

    public GameState(TeamState blueTeamState, TeamState redTeamState, boolean diagnosticsEnabled,
                     boolean objectivePriorityEnabled) {
        this(blueTeamState, redTeamState, diagnosticsEnabled, objectivePriorityEnabled, true);
    }

    public GameState(TeamState blueTeamState, TeamState redTeamState, boolean diagnosticsEnabled,
                     boolean objectivePriorityEnabled, boolean lanePhaseEnabled) {
        this(blueTeamState, redTeamState, diagnosticsEnabled, objectivePriorityEnabled, lanePhaseEnabled, true);
    }

    public GameState(TeamState blueTeamState, TeamState redTeamState, boolean diagnosticsEnabled,
                     boolean objectivePriorityEnabled, boolean lanePhaseEnabled, boolean midGameMacroEnabled) {
        this(blueTeamState, redTeamState, diagnosticsEnabled, objectivePriorityEnabled, lanePhaseEnabled, midGameMacroEnabled, true, true);
    }

    public GameState(TeamState blueTeamState, TeamState redTeamState, boolean diagnosticsEnabled,
                     boolean objectivePriorityEnabled, boolean lanePhaseEnabled, boolean midGameMacroEnabled,
                     boolean objectiveDecisionEnabled) {
        this(blueTeamState, redTeamState, diagnosticsEnabled, objectivePriorityEnabled, lanePhaseEnabled, midGameMacroEnabled, objectiveDecisionEnabled, true);
    }

    public GameState(TeamState blueTeamState, TeamState redTeamState, boolean diagnosticsEnabled,
                     boolean objectivePriorityEnabled, boolean lanePhaseEnabled, boolean midGameMacroEnabled,
                     boolean objectiveDecisionEnabled, boolean lateGameEnabled) {
        this.currentTimeSeconds = 0;
        this.blueTeamState = blueTeamState;
        this.redTeamState = redTeamState;
        this.objectiveState = new ObjectiveState();
        this.objectivePriorityExecutionStats = new ObjectivePriorityExecutionStats();
        this.objectivePriorityState = new ObjectivePriorityState(objectivePriorityEnabled, objectivePriorityExecutionStats);
        this.lanePhaseExecutionStats = new LanePhaseExecutionStats(lanePhaseEnabled);
        this.lanePhaseState = new LanePhaseState(lanePhaseEnabled, lanePhaseExecutionStats);
        this.midGameMacroState = new MidGameMacroState(midGameMacroEnabled, diagnosticsEnabled);
        this.objectiveDecisionState = new ObjectiveDecisionState(objectiveDecisionEnabled, diagnosticsEnabled);
        this.lateGameState = new LateGameState(lateGameEnabled);
        this.mapState = new MapState();
        this.roamExecutionStats = new RoamExecutionStats(diagnosticsEnabled);
        for (TeamSide side : TeamSide.values()) {
            structureActionAttemptedThisTick.put(side, false);
            structureMutationPerformedThisTick.put(side, false);
            duplicateStructureAttemptPendingBySide.put(side, false);
        }
        for (Lane lane : Lane.values()) laneStates.put(lane, new LaneState(lane));
        for (TeamSide side : TeamSide.values()) {
            jungleActionStates.put(side, new JungleActionState());
            jungleEconomyStates.put(side, new JungleEconomyState());
            jungleTempoStates.put(side, new JungleTempoState());
        }
        this.lastBigWinTimeSeconds = -1;
        this.lastAceTimeSeconds = -1;
        configureProgression(true, true);
    }

    public GameState(TeamState blueTeamState, TeamState redTeamState, boolean diagnosticsEnabled,
                     boolean objectivePriorityEnabled, boolean lanePhaseEnabled, boolean midGameMacroEnabled,
                     boolean objectiveDecisionEnabled, boolean lateGameEnabled,
                     MatchChampionAssignments championAssignments) {
        this(blueTeamState, redTeamState, diagnosticsEnabled, objectivePriorityEnabled, lanePhaseEnabled,
                midGameMacroEnabled, objectiveDecisionEnabled, lateGameEnabled);
        this.championAssignments = java.util.Objects.requireNonNull(championAssignments, "championAssignments");
    }

    public java.util.Optional<MatchChampionAssignments> getChampionAssignments() {
        return java.util.Optional.ofNullable(championAssignments);
    }
    public void configureCompositionRuntime(CompositionRuntimeState runtimeState) {
        compositionRuntimeState = java.util.Objects.requireNonNull(runtimeState, "runtimeState");
    }
    public CompositionRuntimeState getCompositionRuntimeState() { return compositionRuntimeState; }
    public void configureJungleEconomy(
            ChampionJungleClearProfileCatalog catalog,
            JungleClearContribution contribution
    ) {
        jungleClearProfileCatalog = java.util.Objects.requireNonNull(catalog, "catalog");
        jungleClearContribution = java.util.Objects.requireNonNull(contribution, "contribution");
        if (!contribution.economyEnabled()) return;
        MatchChampionAssignments assignments = getChampionAssignments().orElseThrow(() ->
                new IllegalStateException("Jungle economy requires champion assignments"));
        for (TeamSide side : TeamSide.values()) {
            PlayerKey playerKey = new PlayerKey(side, com.lolfm.domain.Position.JUNGLE);
            ChampionRoleKey roleKey = new ChampionRoleKey(
                    assignments.get(playerKey).championId(), com.lolfm.domain.Position.JUNGLE);
            if (!catalog.get(roleKey).gameplayEnabled()) {
                throw new IllegalStateException(
                        "Jungle economy requires a gameplay-enabled clear profile: " + roleKey);
            }
        }
    }
    public java.util.Optional<ChampionJungleClearProfileCatalog> getJungleClearProfileCatalog() {
        return java.util.Optional.ofNullable(jungleClearProfileCatalog);
    }
    public JungleClearContribution getJungleClearContribution() { return jungleClearContribution; }
    public boolean isJungleEconomyEnabled() { return jungleClearContribution.economyEnabled(); }
    public boolean isJungleGankTempoEnabled() {
        return jungleClearContribution.gankTempoEnabled();
    }
    public JungleEconomyState jungleEconomyState(TeamSide side) {
        return jungleEconomyStates.get(java.util.Objects.requireNonNull(side, "side"));
    }
    public Map<TeamSide, JungleEconomyState> getJungleEconomyStates() {
        return java.util.Collections.unmodifiableMap(new EnumMap<>(jungleEconomyStates));
    }
    public JungleEconomyExecutionStats getJungleEconomyExecutionStats() {
        return jungleEconomyExecutionStats;
    }
    public JungleTempoState jungleTempoState(TeamSide side) {
        return jungleTempoStates.get(java.util.Objects.requireNonNull(side, "side"));
    }
    public Map<TeamSide, JungleTempoState> getJungleTempoStates() {
        return java.util.Collections.unmodifiableMap(new EnumMap<>(jungleTempoStates));
    }
    public JungleTempoExecutionStats getJungleTempoExecutionStats() {
        return jungleTempoExecutionStats;
    }
    public void configureChampionPower(ChampionPowerProfileCatalog catalog,boolean enabled){championPowerProfileCatalog=java.util.Objects.requireNonNull(catalog);championPowerEnabled=enabled;}
    public java.util.Optional<ChampionPowerProfileCatalog> getChampionPowerProfileCatalog(){return java.util.Optional.ofNullable(championPowerProfileCatalog);}
    public boolean isChampionPowerEnabled(){return championPowerEnabled;}
    public ChampionPowerExecutionStats getChampionPowerExecutionStats(){return championPowerExecutionStats;}
    public void configureChampionMatchup(ChampionMatchupCatalog catalog, ChampionMatchupMode mode) {
        championMatchupCatalog = java.util.Objects.requireNonNull(catalog, "catalog");
        championRoleMatchupProfileCatalog = null;
        championMatchupMode = java.util.Objects.requireNonNull(mode, "mode");
    }
    public void configureChampionMatchup(ChampionRoleMatchupProfileCatalog catalog, ChampionMatchupMode mode) {
        championRoleMatchupProfileCatalog = java.util.Objects.requireNonNull(catalog, "catalog");
        championMatchupCatalog = null;
        championMatchupMode = java.util.Objects.requireNonNull(mode, "mode");
    }
    public java.util.Optional<ChampionMatchupCatalog> getChampionMatchupCatalog() {
        return java.util.Optional.ofNullable(championMatchupCatalog);
    }
    public java.util.Optional<ChampionRoleMatchupProfileCatalog> getChampionRoleMatchupProfileCatalog() {
        return java.util.Optional.ofNullable(championRoleMatchupProfileCatalog);
    }
    public ChampionMatchupMode getChampionMatchupMode() { return championMatchupMode; }
    public ChampionMatchupExecutionStats getChampionMatchupExecutionStats() {
        return championMatchupExecutionStats;
    }
    public java.util.Optional<PlayerKey> playerKeyOf(PlayerState player){if(blueTeamState.getPlayers().contains(player))return java.util.Optional.of(new PlayerKey(TeamSide.BLUE,player.getPosition()));if(redTeamState.getPlayers().contains(player))return java.util.Optional.of(new PlayerKey(TeamSide.RED,player.getPosition()));return java.util.Optional.empty();}

    public void configureProgression(boolean enabled, boolean powerEnabled) {
        progressionEnabled = enabled; progressionPowerEnabled = enabled && powerEnabled;
        for (PlayerState player : blueTeamState.getPlayers()) player.configureProgression(TeamSide.BLUE, enabled, progressionExecutionStats);
        for (PlayerState player : redTeamState.getPlayers()) player.configureProgression(TeamSide.RED, enabled, progressionExecutionStats);
    }
    public boolean isProgressionEnabled() { return progressionEnabled; }
    public boolean isProgressionPowerEnabled() { return progressionPowerEnabled; }
    public ProgressionExecutionStats getProgressionExecutionStats() { return progressionExecutionStats; }
    public void drainProgressionEvents(List<MatchEvent> events) {
        if (!progressionEnabled) return;
        for (TeamSide side : TeamSide.values()) for (PlayerState player : getTeamState(side).getPlayers()) {
            for (ProgressionEventData data : player.getProgressionState().drainEvents()) {
                MatchEventType type = data.type() == ProgressionEventType.LEVEL_UP ? MatchEventType.LEVEL_UP : MatchEventType.ITEM_STAGE_REACHED;
                MatchEvent event = new MatchEvent(Math.min(data.timeSeconds(), currentTimeSeconds), type, type == MatchEventType.LEVEL_UP ? "Level up" : "Item stage reached", null, null, List.of());
                event.setProgressionEvent(data); events.add(event);
            }
        }
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

    public ObjectivePriorityState getObjectivePriorityState() {
        return objectivePriorityState;
    }

    public ObjectivePriorityExecutionStats getObjectivePriorityExecutionStats() {
        return objectivePriorityExecutionStats;
    }

    public LanePhaseState getLanePhaseState() { return lanePhaseState; }
    public LanePhaseExecutionStats getLanePhaseExecutionStats() { return lanePhaseExecutionStats; }
    public MidGameMacroState getMidGameMacroState() { return midGameMacroState; }
    public ObjectiveDecisionState getObjectiveDecisionState() { return objectiveDecisionState; }
    public LateGameState getLateGameState() { return lateGameState; }
    public boolean isLateGameEnabled() { return lateGameState.isEnabled(); }
    public boolean isObjectiveDecisionEnabled() { return objectiveDecisionState.isEnabled(); }
    public boolean isMidGameMacroEnabled() { return midGameMacroState.isEnabled(); }
    public boolean isLanePhaseEnabled() { return lanePhaseState.isEnabled(); }
    public boolean isLaneLaning(Lane lane) { return lanePhaseState.isLaning(lane); }
    public void clearMajorCombatParticipantsThisTick() { majorCombatParticipantsThisTick.clear(); }
    public void markMajorCombatParticipant(PlayerState player) { majorCombatParticipantsThisTick.add(player); }
    public boolean wasMajorCombatParticipantThisTick(PlayerState player) { return majorCombatParticipantsThisTick.contains(player); }
    public boolean wasMajorCombatAttemptedThisTick() { return !majorCombatParticipantsThisTick.isEmpty(); }

    public MapState getMapState() {
        return mapState;
    }

    public LaneState laneState(Lane lane) { return laneStates.get(lane); }
    public Map<Lane, LaneState> getLaneStates() { return Map.copyOf(laneStates); }
    public void clearStructureActionRegistryThisTick() {
        for (TeamSide side : TeamSide.values()) {
            structureActionAttemptedThisTick.put(side, false);
            structureMutationPerformedThisTick.put(side, false);
            duplicateStructureAttemptPendingBySide.put(side, false);
        }
    }
    public boolean markStructureActionAttempted(TeamSide side) {
        if (wasStructureActionAttemptedThisTick(side)) {
            duplicateStructureAttemptPendingBySide.put(side, true);
            structureActionExecutionStats.recordSameSideMultipleAttemptError();
            return false;
        }
        structureActionAttemptedThisTick.put(side, true);
        structureActionExecutionStats.recordAttempt();
        return true;
    }
    public void markStructureMutationPerformed(TeamSide side) {
        if (!wasStructureActionAttemptedThisTick(side)) markStructureActionAttempted(side);
        if (wasStructureMutationPerformedThisTick(side)) {
            if (duplicateStructureAttemptPendingBySide.getOrDefault(side, false)) {
                structureActionExecutionStats.recordSameSideMultipleMutationError();
                duplicateStructureAttemptPendingBySide.put(side, false);
            }
            return;
        }
        structureMutationPerformedThisTick.put(side, true);
        structureActionExecutionStats.recordMutation();
    }
    public void markStructureActionPerformed(TeamSide side) {
        if (!wasStructureActionAttemptedThisTick(side)) markStructureActionAttempted(side);
        markStructureMutationPerformed(side);
    }
    public boolean wasStructureActionAttemptedThisTick(TeamSide side) {
        return structureActionAttemptedThisTick.getOrDefault(side, false);
    }
    public boolean wasStructureMutationPerformedThisTick(TeamSide side) {
        return structureMutationPerformedThisTick.getOrDefault(side, false);
    }
    /** Compatibility alias: a consumed structure slot now means an actual attempt, not only mutation. */
    public boolean wasStructureActionPerformedThisTick(TeamSide side) { return wasStructureActionAttemptedThisTick(side); }
    public boolean wasAnyStructureActionPerformedThisTick() {
        return structureActionAttemptedThisTick.values().stream().anyMatch(Boolean::booleanValue);
    }
    public void recordLaterStructureResolverBlockedByAttempt() {
        structureActionExecutionStats.recordLaterResolverBlockedByAttempt();
    }
    public void recordPostFightStructureWindow(int mutations) { structureActionExecutionStats.recordPostFightWindow(mutations); }
    public void recordPostFightInternalBlockError() { structureActionExecutionStats.recordPostFightInternalBlockError(); }
    public StructureActionExecutionStats getStructureActionExecutionStats() { return structureActionExecutionStats; }
    public int getLastLanePressureResolvedAtSeconds() { return lastLanePressureResolvedAtSeconds; }
    public int getDuplicateLanePressureResolutionCount() { return duplicateLanePressureResolutionCount; }
    public boolean shouldResolveLaneCombatAt(int time) { if(time < lastLaneCombatResolvedAtSeconds) throw new IllegalArgumentException("Lane combat time cannot move backwards"); if(time==lastLaneCombatResolvedAtSeconds) return false; return time >= LaneCombatRuleConfig.LANE_COMBAT_START_SECONDS && time <= LaneCombatRuleConfig.LANE_COMBAT_END_SECONDS && time % LaneCombatRuleConfig.LANE_COMBAT_INTERVAL_SECONDS==0; }
    public void markLaneCombatResolvedAt(int time) { lastLaneCombatResolvedAtSeconds=time; }
    public int getLastLaneCombatResolvedAtSeconds() { return lastLaneCombatResolvedAtSeconds; }
    public JungleActionState jungleActionState(TeamSide side) { return jungleActionStates.get(side); }
    public Map<TeamSide, JungleActionState> getJungleActionStates() { return Map.copyOf(jungleActionStates); }
    public CombatExecutionStats getCombatExecutionStats() { return combatExecutionStats; }
    public CombatOutcomeExecutionStats getCombatOutcomeExecutionStats() { return combatOutcomeExecutionStats; }
    public RoamExecutionStats getRoamExecutionStats() { return roamExecutionStats; }
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
        expireActivities(blueTeamState);
        expireActivities(redTeamState);
    }
    private void expireActivities(TeamState team) {
        for (PlayerState player : team.getPlayers()) {
            boolean roaming = player.getActivityState().getActivityType() == PlayerActivityType.ROAMING;
            player.expireActivityIfNeeded(currentTimeSeconds);
            if (roaming && player.getActivityState().getActivityType() == PlayerActivityType.DEFAULT_ROLE) {
                roamExecutionStats.recordActivityReturned();
            }
        }
    }
    public int getLastRoamEvaluationAtSeconds() { return lastRoamEvaluationAtSeconds; }
    public boolean shouldResolveRoamAt(int time) {
        if (time < lastRoamEvaluationAtSeconds) throw new IllegalArgumentException("Roam time cannot move backwards");
        if (time == lastRoamEvaluationAtSeconds) return false;
        return time >= RoamRuleConfig.MID_ROAM_START_SECONDS && time <= RoamRuleConfig.ROAM_END_SECONDS && time % RoamRuleConfig.ROAM_EVALUATION_INTERVAL_SECONDS == 0;
    }
    public void markRoamEvaluatedAt(int time) { if (time <= lastRoamEvaluationAtSeconds) throw new IllegalStateException("Roam time was not advanced"); lastRoamEvaluationAtSeconds = time; }

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
        if (amount < 0) throw new IllegalArgumentException("Simulation time cannot move backwards");
        if (finished) return;
        currentTimeSeconds += amount;
        mapState.refreshAt(currentTimeSeconds);
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

    public boolean hasRecentBigWin(TeamSide side, int windowSeconds) {
        return isRecentEvent(lastBigWinSide, lastBigWinTimeSeconds, side, windowSeconds);
    }

    public boolean hasRecentBigWin(String teamName, int windowSeconds) {
        return isRecentEvent(lastBigWinSide, lastBigWinTimeSeconds, teamName, windowSeconds);
    }

    public boolean hasRecentAce(TeamSide side, int windowSeconds) {
        return isRecentEvent(lastAceSide, lastAceTimeSeconds, side, windowSeconds);
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

    private boolean isRecentEvent(TeamSide eventSide, int eventTimeSeconds, TeamSide requestedSide, int windowSeconds) {
        return eventSide == requestedSide
                && eventTimeSeconds >= 0
                && currentTimeSeconds >= eventTimeSeconds
                && currentTimeSeconds - eventTimeSeconds <= windowSeconds;
    }

    private boolean isRecentEvent(TeamSide side, int eventTimeSeconds, String teamName, int windowSeconds) {
        return side != null
                && getTeamState(side).getTeamName().equals(teamName)
                && eventTimeSeconds >= 0
                && currentTimeSeconds >= eventTimeSeconds
                && currentTimeSeconds - eventTimeSeconds <= windowSeconds;
    }
}
