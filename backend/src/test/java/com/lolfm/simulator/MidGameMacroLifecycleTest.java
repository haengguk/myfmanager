package com.lolfm.simulator;

import static org.junit.jupiter.api.Assertions.*;

import com.lolfm.domain.MacroPlanLifecycleData;
import com.lolfm.domain.MatchSnapshot;
import com.lolfm.domain.MatchTimeline;
import com.lolfm.domain.PlayerAttributes;
import com.lolfm.domain.Position;
import com.lolfm.factory.DummyDataFactory;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class MidGameMacroLifecycleTest {
    @Test
    void eachSetupLifecycleStartsAndEndsAtMostOnce() {
        TeamMacroTeamState team = new TeamMacroTeamState(TeamSide.BLUE);
        team.beginPlan(TeamMacroPlan.OBJECTIVE_SETUP_DRAGON, null, ObjectiveType.DRAGON,
                EnumSet.of(Position.JUNGLE, Position.MID), 870);
        assertEquals(1, team.getLifecycleHistory().size());
        assertEquals(0, team.getLifecycleHistory().getFirst().endRecordCount());
        team.expireIfNeeded(930);
        team.expireIfNeeded(940);
        MacroPlanLifecycleData lifecycle = team.getLifecycleHistory().getFirst();
        assertEquals(1, lifecycle.endRecordCount());
        assertEquals(MacroPlanEndReason.EXPIRED, lifecycle.endReason());
        assertEquals(930, lifecycle.endTimeSeconds());
    }

    @Test
    void setupLifecycleCountsBalanceAtGameEnd() {
        MatchTimeline timeline = simulator().simulate(
                new DummyDataFactory().createBlueTeam(), new DummyDataFactory().createRedTeam(), 7L);
        List<MacroPlanLifecycleData> setups = timeline.getSnapshots().getLast().getMidGameMacro()
                .planLifecycleHistory().stream().filter(MacroPlanLifecycleData::setupPlan).toList();
        assertEquals(setups.size(), setups.stream().filter(x -> x.endRecordCount() == 1).count());
        assertEquals(0, setups.stream().filter(x -> x.endReason() == null).count());
        assertTrue(setups.stream().allMatch(x -> Set.of(MacroPlanEndReason.EXPIRED,
                MacroPlanEndReason.OBJECTIVE_CAPTURED, MacroPlanEndReason.REPLACED,
                MacroPlanEndReason.MATCH_ENDED, MacroPlanEndReason.LATE_GAME_TRANSITION, MacroPlanEndReason.FEATURE_DISABLED).contains(x.endReason())));
    }

    @Test
    void snapshotDoesNotRepeatSetupEndAccounting() {
        GameState state = midGameStateAt(870);
        TeamMacroTeamState blue = state.getMidGameMacroState().teamState(TeamSide.BLUE);
        blue.beginPlan(TeamMacroPlan.OBJECTIVE_SETUP_BARON, null, ObjectiveType.BARON,
                EnumSet.of(Position.TOP, Position.JUNGLE), 870);
        SnapshotFactory snapshots = new SnapshotFactory();
        List<MatchSnapshot> samples = new ArrayList<>();
        samples.add(snapshots.create(state));
        state.advanceTimeSeconds(60);
        new MidGameMacroResolver().expirePlans(state);
        samples.add(snapshots.create(state));
        samples.add(snapshots.create(state));
        List<MacroPlanLifecycleData> finalHistory = samples.getLast().getMidGameMacro().planLifecycleHistory();
        assertEquals(1, finalHistory.size());
        assertEquals(1, finalHistory.getFirst().endRecordCount());
        assertEquals(1, finalHistory.stream().map(x -> x.teamSide() + ":" + x.planSequence()).distinct().count());
    }

    @Test
    void captureCancellationCannotBeFollowedByExpiryForSamePlan() {
        GameState state = midGameStateAt(870);
        TeamMacroTeamState blue = state.getMidGameMacroState().teamState(TeamSide.BLUE);
        blue.beginPlan(TeamMacroPlan.OBJECTIVE_SETUP_DRAGON, null, ObjectiveType.DRAGON,
                EnumSet.of(Position.JUNGLE, Position.MID), 870);
        new MidGameMacroResolver().cancelSetupForObjective(state, ObjectiveType.DRAGON);
        state.advanceTimeSeconds(60);
        new MidGameMacroResolver().expirePlans(state);
        MacroPlanLifecycleData lifecycle = blue.getLifecycleHistory().getFirst();
        assertEquals(MacroPlanEndReason.OBJECTIVE_CAPTURED, lifecycle.endReason());
        assertEquals(1, lifecycle.endRecordCount());
        assertEquals(1, blue.getLifecycleHistory().size());
    }

    @Test
    void gameFinishedClosesActiveSetupExactlyOnce() {
        GameState state = midGameStateAt(870);
        TeamMacroTeamState blue = state.getMidGameMacroState().teamState(TeamSide.BLUE);
        blue.beginPlan(TeamMacroPlan.OBJECTIVE_SETUP_BARON, null, ObjectiveType.BARON,
                EnumSet.of(Position.TOP, Position.JUNGLE), 870);
        MidGameMacroResolver resolver = new MidGameMacroResolver();
        resolver.onMatchFinished(state);
        resolver.onMatchFinished(state);
        MacroPlanLifecycleData lifecycle = blue.getLifecycleHistory().getFirst();
        assertEquals(MacroPlanEndReason.MATCH_ENDED, lifecycle.endReason());
        assertEquals(1, lifecycle.endRecordCount());
        assertEquals(0.0, new ObjectivePriorityResolver().baronMacroSetupControl(state));
    }

    private MatchSimulator simulator() {
        return new MatchSimulator(new TeamfightResolver(), new EndGameEvaluator(), new SnapshotFactory(),
                new ObjectiveResolver(), new PostFightResolver(), new ObjectiveAttemptResolver(),
                new StructureResolver(), new PushResolver(), SimulationOptions.productionDefaults());
    }

    private GameState midGameStateAt(int timeSeconds) {
        GameState state = new GameState(team("BLUE"), team("RED"), true, true, true, true);
        state.advanceTimeSeconds(840);
        new LanePhaseResolver().transitionIfDue(state).orElseThrow();
        new MidGameMacroResolver().onPhaseTransition(state);
        state.advanceTimeSeconds(timeSeconds - 840);
        state.getObjectiveState().updateSpawnState(timeSeconds);
        return state;
    }

    private TeamState team(String name) {
        List<PlayerState> players = new ArrayList<>();
        for (Position position : Position.values()) players.add(new PlayerState(
                name + "-" + position, position, new PlayerAttributes(14, 14, 14, 14), 500));
        return new TeamState(name, players);
    }
}
