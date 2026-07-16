package com.lolfm.simulator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.lolfm.domain.MatchEvent;
import com.lolfm.domain.MatchEventType;
import com.lolfm.domain.MatchTimeline;
import com.lolfm.domain.ObjectivePrioritySnapshot;
import com.lolfm.domain.Player;
import com.lolfm.domain.PlayerAttributes;
import com.lolfm.domain.Team;
import com.lolfm.factory.DummyDataFactory;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

class MidGameMacroTest {
    @Test
    void evaluationAndPlanBoundariesAreExact() {
        TeamMacroTeamState team = new TeamMacroTeamState();
        team.scheduleFirstEvaluation(840);
        assertFalse(team.isDueAt(869));
        assertTrue(team.isDueAt(870));
        assertTrue(team.isDueAt(871));

        team.advanceScheduleAfterEvaluation(870, 1);
        team.beginPlan(TeamMacroPlan.SIDE_LANE_BOT, Lane.BOT, null,
                EnumSet.of(com.lolfm.domain.Position.ADC), 870);
        assertTrue(team.isActiveAt(929));
        assertFalse(team.isActiveAt(930));
        team.expireIfNeeded(930);
        assertEquals(TeamMacroPlan.SIDE_LANE_BOT, team.getPreviousPlan());
        assertEquals(930, team.getNextEvaluationAtSeconds());
    }

    @Test
    void farmBlocksKeepTheLatestEffectiveRestriction() {
        PlayerState player = new PlayerState("adc", com.lolfm.domain.Position.ADC, 500);
        player.blockFarmUntil(890);
        player.blockFarmUntil(870);
        assertEquals(890, player.getFarmResumeAtSeconds());
        assertFalse(player.canFarmAt(889));
        assertTrue(player.canFarmAt(890));
    }

    @Test
    void featureOffDoesNotConsumeRandomOrExposeMacroSnapshot() {
        GameState state = new GameState(teamState("BLUE"), teamState("RED"), true, true, true, false);
        CountingRandom random = new CountingRandom();
        new MidGameMacroResolver().resolveDueEvaluation(state, random, new ArrayList<>(), new StructureResolver());
        assertEquals(0, random.calls);
        assertFalse(new SnapshotFactory().create(state).getMidGameMacro().enabled());
    }

    @Test
    void duplicateEvaluationAtTheSameTimeIsIdempotent() {
        GameState state = new GameState(teamState("BLUE"), teamState("RED"), true, true, true, true);
        state.advanceTimeSeconds(840);
        new LanePhaseResolver().transitionIfDue(state).orElseThrow();
        MidGameMacroResolver resolver = new MidGameMacroResolver();
        resolver.onPhaseTransition(state);
        assertEquals(870, state.getMidGameMacroState().teamState(TeamSide.BLUE).getNextEvaluationAtSeconds());
        state.advanceTimeSeconds(30);
        CountingRandom random = new CountingRandom();
        List<MatchEvent> events = new ArrayList<>();
        resolver.resolveDueEvaluation(state, random, events, new StructureResolver());
        int calls = random.calls;
        int eventCount = events.size();
        resolver.resolveDueEvaluation(state, random, events, new StructureResolver());
        assertEquals(calls, random.calls);
        assertEquals(eventCount, events.size());
        assertEquals(870, state.getMidGameMacroState().getLastEvaluationAtSeconds());
        assertEquals(2, state.getMidGameMacroState().getExecutionStats().snapshot().blueEvaluations()
                + state.getMidGameMacroState().getExecutionStats().snapshot().redEvaluations());
    }

    @Test
    void setupControlIsStructuredAndCaptureCancelsOnlyMatchingPlan() {
        GameState state = new GameState(teamState("BLUE"), teamState("RED"), true, true, true, true);
        state.advanceTimeSeconds(840);
        new LanePhaseResolver().transitionIfDue(state).orElseThrow();
        state.getMidGameMacroState().teamState(TeamSide.BLUE).beginPlan(
                TeamMacroPlan.OBJECTIVE_SETUP_DRAGON, null, ObjectiveType.DRAGON,
                EnumSet.of(com.lolfm.domain.Position.JUNGLE, com.lolfm.domain.Position.MID,
                        com.lolfm.domain.Position.ADC), 840);
        ObjectivePrioritySnapshot before = new ObjectivePriorityResolver().snapshot(state);
        assertEquals(12.0, before.dragonMacroSetupControl());
        assertEquals(0.0, before.baronMacroSetupControl());
        new MidGameMacroResolver().cancelSetupForObjective(state, ObjectiveType.DRAGON);
        ObjectivePrioritySnapshot after = new ObjectivePriorityResolver().snapshot(state);
        assertEquals(0.0, after.dragonMacroSetupControl());
        assertNotEquals(TeamMacroPlan.OBJECTIVE_SETUP_DRAGON,
                state.getMidGameMacroState().teamState(TeamSide.BLUE).getCurrentPlan());
    }

    @Test
    void diagnosticsToggleDoesNotChangeMacroTimeline() {
        DummyDataFactory factory = new DummyDataFactory();
        MatchSimulator on = simulator(SimulationOptions.productionDefaults().withDiagnosticsEnabled(true));
        MatchSimulator off = simulator(SimulationOptions.productionDefaults().withDiagnosticsEnabled(false));
        MatchTimeline first = on.simulate(factory.createBlueTeam(), factory.createRedTeam(), 91234L);
        MatchTimeline second = off.simulate(factory.createBlueTeam(), factory.createRedTeam(), 91234L);
        assertEquals(signature(first), signature(second));
    }

    @Test
    void dueEvaluationAlwaysAdvancesNextEvaluation() {
        GameState state = midGameStateAt(870);
        MidGameMacroResolver resolver = new MidGameMacroResolver();
        resolver.resolveDueEvaluation(state, new CountingRandom(), new ArrayList<>(), new StructureResolver());
        assertTrue(state.getMidGameMacroState().teamState(TeamSide.BLUE).getNextEvaluationAtSeconds() > 870);
        assertTrue(state.getMidGameMacroState().teamState(TeamSide.RED).getNextEvaluationAtSeconds() > 870);
        assertEquals(930, state.getMidGameMacroState().teamState(TeamSide.BLUE).getNextEvaluationAtSeconds());
    }

    @Test
    void resetSelectionAdvancesScheduleWithoutActionEvent() {
        GameState state = midGameStateAt(870);
        killAll(state.getBlueTeamState(), 870);
        killAll(state.getRedTeamState(), 870);
        CountingRandom random = new CountingRandom();
        List<MatchEvent> events = new ArrayList<>();
        new MidGameMacroResolver().resolveDueEvaluation(state, random, events, new StructureResolver());
        assertTrue(events.stream().noneMatch(event -> event.getType() == MatchEventType.MACRO_ACTION));
        assertEquals(TeamMacroPlan.RESET_AND_FARM,
                state.getMidGameMacroState().teamState(TeamSide.BLUE).getCurrentPlan());
        assertEquals(930, state.getMidGameMacroState().teamState(TeamSide.BLUE).getNextEvaluationAtSeconds());
        assertEquals(0, state.getMidGameMacroState().getEvaluationHistory().getFirst()
                .selectionRandomConsumptionCount());
    }

    @Test
    void evaluationIsNotMissedWhenCurrentTimePassesDueBoundary() {
        GameState state = midGameStateAt(875);
        CountingRandom random = new CountingRandom();
        new MidGameMacroResolver().resolveDueEvaluation(state, random, new ArrayList<>(), new StructureResolver());
        var audit = state.getMidGameMacroState().getEvaluationHistory().getFirst();
        assertEquals(870, audit.dueAtSeconds());
        assertEquals(875, audit.actualEvaluationAtSeconds());
        assertEquals(930, audit.blueNextEvaluationAtSeconds());
        assertTrue(audit.blueNextEvaluationAtSeconds() > state.getCurrentTimeSeconds());
    }

    @Test
    void finalSnapshotDoesNotExposeOverdueNextEvaluation() {
        DummyDataFactory factory = new DummyDataFactory();
        MatchTimeline timeline = simulator(SimulationOptions.productionDefaults())
                .simulate(factory.createBlueTeam(), factory.createRedTeam(), 7L);
        var macro = timeline.getSnapshots().getLast().getMidGameMacro();
        assertTrue(macro.matchEnded());
        assertEquals(-1, macro.blueTeam().nextEvaluationAtSeconds());
        assertEquals(-1, macro.redTeam().nextEvaluationAtSeconds());
        assertEquals(MacroPlanStatus.MATCH_ENDED, macro.blueTeam().status());
        assertEquals(MacroPlanStatus.MATCH_ENDED, macro.redTeam().status());
    }

    @Test
    void seed7StopsMidGameEvaluationsAtLateGameTransition() {
        DummyDataFactory factory = new DummyDataFactory();
        MatchTimeline timeline = simulator(SimulationOptions.productionDefaults())
                .simulate(factory.createBlueTeam(), factory.createRedTeam(), 7L);
        var history = timeline.getSnapshots().getLast().getMidGameMacro().evaluationHistory();
        List<Integer> dueTimes = history.stream().map(com.lolfm.domain.MidGameMacroEvaluationData::dueAtSeconds).toList();
        int lateStarted = timeline.getSnapshots().getLast().getLateGame().lateGameStartedAtSeconds();
        assertTrue(dueTimes.stream().allMatch(due -> due < lateStarted), dueTimes.toString());
        for (var evaluation : history) {
            if (evaluation.evaluationSkippedReason() == null) {
                assertTrue(evaluation.blueNextEvaluationAtSeconds() > evaluation.actualEvaluationAtSeconds());
                assertTrue(evaluation.redNextEvaluationAtSeconds() > evaluation.actualEvaluationAtSeconds());
            }
        }
    }

    private String signature(MatchTimeline timeline) {
        StringBuilder result = new StringBuilder();
        for (MatchEvent event : timeline.getEvents()) {
            result.append(event.getTimeSeconds()).append('|').append(event.getType()).append('|')
                    .append(event.getStructureActionSource()).append('|').append(event.getCombatSource()).append('|')
                    .append(event.getMidGameMacroAction()).append(';');
        }
        for (var snapshot : timeline.getSnapshots()) result.append(snapshot.getMidGameMacro().toString()).append(';');
        return result.toString();
    }

    private GameState midGameStateAt(int timeSeconds) {
        GameState state = new GameState(teamState("BLUE"), teamState("RED"), true, true, true, true);
        state.advanceTimeSeconds(840);
        new LanePhaseResolver().transitionIfDue(state).orElseThrow();
        new MidGameMacroResolver().onPhaseTransition(state);
        state.advanceTimeSeconds(timeSeconds - 840);
        state.getObjectiveState().updateSpawnState(timeSeconds);
        return state;
    }

    private void killAll(TeamState team, int timeSeconds) {
        for (PlayerState player : team.getPlayers()) player.markDead(timeSeconds, 120);
    }

    private MatchSimulator simulator(SimulationOptions options) {
        return new MatchSimulator(new TeamfightResolver(), new EndGameEvaluator(), new SnapshotFactory(),
                new ObjectiveResolver(), new PostFightResolver(), new ObjectiveAttemptResolver(),
                new StructureResolver(), new PushResolver(), options);
    }

    private TeamState teamState(String name) {
        List<PlayerState> players = new ArrayList<>();
        players.add(new PlayerState(name + "-top", com.lolfm.domain.Position.TOP, new PlayerAttributes(14, 14, 14, 14), 500));
        players.add(new PlayerState(name + "-jungle", com.lolfm.domain.Position.JUNGLE, new PlayerAttributes(14, 14, 14, 14), 500));
        players.add(new PlayerState(name + "-mid", com.lolfm.domain.Position.MID, new PlayerAttributes(14, 14, 14, 14), 500));
        players.add(new PlayerState(name + "-adc", com.lolfm.domain.Position.ADC, new PlayerAttributes(14, 14, 14, 14), 500));
        players.add(new PlayerState(name + "-support", com.lolfm.domain.Position.SUPPORT, new PlayerAttributes(14, 14, 14, 14), 500));
        return new TeamState(name, players);
    }

    private static final class CountingRandom extends Random {
        private int calls;
        @Override public double nextDouble() { calls++; return 0.5; }
        @Override public boolean nextBoolean() { calls++; return false; }
    }
}
