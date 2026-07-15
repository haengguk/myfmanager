package com.lolfm.simulator;

import com.lolfm.domain.MidGameMacroEvaluationData;
import com.lolfm.domain.Position;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** Match-owned mutable state for the two independent team macro plans. */
public final class MidGameMacroState {
    private final boolean enabled;
    private final EnumMap<TeamSide, TeamMacroTeamState> teamStates = new EnumMap<>(TeamSide.class);
    private final EnumMap<TeamSide, EnumMap<Position, Integer>> macroFarmBlockedUntil = new EnumMap<>(TeamSide.class);
    private final List<MidGameMacroEvaluationData> evaluationHistory = new ArrayList<>();
    private final MidGameMacroExecutionStats executionStats;
    private int lastEvaluationAtSeconds = -1;
    private boolean matchEnded;

    public MidGameMacroState(boolean enabled, boolean diagnosticsEnabled) {
        this.enabled = enabled;
        this.executionStats = new MidGameMacroExecutionStats(diagnosticsEnabled);
        for (TeamSide side : TeamSide.values()) {
            teamStates.put(side, new TeamMacroTeamState());
            EnumMap<Position, Integer> blocked = new EnumMap<>(Position.class);
            for (Position position : Position.values()) blocked.put(position, -1);
            macroFarmBlockedUntil.put(side, blocked);
        }
    }

    public boolean isEnabled() { return enabled; }
    public TeamMacroTeamState teamState(TeamSide side) { return teamStates.get(side); }
    public Map<TeamSide, TeamMacroTeamState> getTeamStates() { return Map.copyOf(teamStates); }
    public MidGameMacroExecutionStats getExecutionStats() { return executionStats; }
    public int getLastEvaluationAtSeconds() { return lastEvaluationAtSeconds; }
    public List<MidGameMacroEvaluationData> getEvaluationHistory() { return List.copyOf(evaluationHistory); }
    public boolean isMatchEnded() { return matchEnded; }

    public void onMidGameStarted(int startedAtSeconds) {
        if (!enabled) return;
        for (TeamMacroTeamState state : teamStates.values()) state.scheduleFirstEvaluation(startedAtSeconds);
    }

    public void expirePlansIfNeeded(int currentTimeSeconds) {
        if (!enabled) return;
        for (TeamMacroTeamState state : teamStates.values()) state.expireIfNeeded(currentTimeSeconds);
    }

    boolean dueAt(TeamSide side, int currentTimeSeconds) {
        return enabled && !matchEnded && teamStates.get(side).isDueAt(currentTimeSeconds);
    }

    void markEvaluationAt(int currentTimeSeconds) { lastEvaluationAtSeconds = currentTimeSeconds; }

    void recordEvaluation(MidGameMacroEvaluationData evaluation) {
        evaluationHistory.add(evaluation);
    }

    void registerFarmBlock(TeamSide side, Position position, int untilSeconds) {
        macroFarmBlockedUntil.get(side).merge(position, untilSeconds, Math::max);
    }

    boolean isFarmBlockedByMacro(TeamSide side, Position position, int timeSeconds) {
        return timeSeconds < macroFarmBlockedUntil.get(side).getOrDefault(position, -1);
    }

    public void finishMatch(int currentTimeSeconds) {
        if (!enabled || matchEnded) return;
        int due = Integer.MAX_VALUE;
        boolean overdue = false;
        for (TeamSide side : TeamSide.values()) {
            TeamMacroTeamState team = teamStates.get(side);
            if (team.isDueAt(currentTimeSeconds)) {
                due = Math.min(due, team.getNextEvaluationAtSeconds());
                overdue = true;
            }
        }
        if (overdue) {
            evaluationHistory.add(new MidGameMacroEvaluationData(
                    due, currentTimeSeconds, null, null,
                    teamStates.get(TeamSide.BLUE).getPreviousPlan(),
                    teamStates.get(TeamSide.RED).getPreviousPlan(),
                    teamStates.get(TeamSide.BLUE).getEndReason(),
                    teamStates.get(TeamSide.RED).getEndReason(),
                    -1, -1, "GAME_FINISHED", 0));
        }
        for (TeamMacroTeamState team : teamStates.values()) team.finishMatch(currentTimeSeconds);
        matchEnded = true;
    }
}
