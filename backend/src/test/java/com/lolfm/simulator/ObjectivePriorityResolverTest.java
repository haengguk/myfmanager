package com.lolfm.simulator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.lolfm.domain.MatchEvent;
import com.lolfm.domain.MatchTimeline;
import com.lolfm.domain.PlayerAttributes;
import com.lolfm.domain.Position;
import com.lolfm.factory.DummyDataFactory;
import java.util.List;
import org.junit.jupiter.api.Test;

class ObjectivePriorityResolverTest {
    private static final double DELTA = 0.00001;
    private final ObjectivePriorityResolver resolver = new ObjectivePriorityResolver();

    @Test
    void newMatchStartsAtZeroAndDoesNotSharePriorityState() {
        GameState first = state("Blue One", "Red One");
        GameState second = state("Blue Two", "Red Two");

        assertEquals(0, first.getObjectivePriorityState().getDragonRecentControl(), DELTA);
        assertEquals(0, first.getObjectivePriorityState().getBaronRecentControl(), DELTA);
        resolver.applyJungleGankKill(first, 100, Lane.BOT, TeamSide.BLUE);

        assertNotSame(first.getObjectivePriorityState(), second.getObjectivePriorityState());
        assertEquals(0, second.getObjectivePriorityState().getDragonRecentControl(), DELTA);
        assertEquals(0, second.getObjectivePriorityState().getBaronRecentControl(), DELTA);
    }

    @Test
    void resolverHasNoMatchScopedFields() {
        assertEquals(0, ObjectivePriorityResolver.class.getDeclaredFields().length);
    }

    @Test
    void recentControlDecaysTowardZeroWithoutCrossingAndSameTimeIsIdempotent() {
        GameState state = state();
        resolver.applyJungleGankKill(state, 0, Lane.BOT, TeamSide.BLUE);
        resolver.decayRecentControl(state, 20);
        assertEquals(8, state.getObjectivePriorityState().getDragonRecentControl(), DELTA);
        resolver.decayRecentControl(state, 20);
        assertEquals(8, state.getObjectivePriorityState().getDragonRecentControl(), DELTA);

        GameState negative = state();
        resolver.applyJungleGankKill(negative, 0, Lane.BOT, TeamSide.RED);
        resolver.decayRecentControl(negative, 20);
        assertEquals(-8, negative.getObjectivePriorityState().getDragonRecentControl(), DELTA);
        resolver.decayRecentControl(negative, 200);
        assertEquals(0, negative.getObjectivePriorityState().getDragonRecentControl(), DELTA);
    }

    @Test
    void decayRejectsPastTimeAndReadsDoNotMutateState() {
        GameState state = state();
        resolver.applyJungleGankKill(state, 0, Lane.BOT, TeamSide.BLUE);
        resolver.decayRecentControl(state, 10);
        double before = state.getObjectivePriorityState().getDragonRecentControl();

        state.getObjectivePriorityState().getDragonRecentControl();
        resolver.dragonSignedPriority(state);

        assertEquals(before, state.getObjectivePriorityState().getDragonRecentControl(), DELTA);
        assertThrows(IllegalArgumentException.class, () -> resolver.decayRecentControl(state, 9));
    }

    @Test
    void pressureScoresUseOnlyConfiguredObjectiveLanesAndPreserveSign() {
        GameState state = state();
        state.laneState(Lane.TOP).setPressure(100);
        state.laneState(Lane.MID).setPressure(20);
        state.laneState(Lane.BOT).setPressure(40);

        assertEquals(31, resolver.dragonLanePressureScore(state), DELTA);
        assertEquals(64, resolver.baronLanePressureScore(state), DELTA);
        state.laneState(Lane.TOP).setPressure(-100);
        assertEquals(31, resolver.dragonLanePressureScore(state), DELTA);
        state.laneState(Lane.BOT).setPressure(-100);
        assertEquals(-46, resolver.baronLanePressureScore(state), DELTA);

        state.laneState(Lane.TOP).setPressure(-20);
        state.laneState(Lane.MID).setPressure(-20);
        state.laneState(Lane.BOT).setPressure(-40);
        assertEquals(-31, resolver.dragonLanePressureScore(state), DELTA);
        assertEquals(-20, resolver.baronLanePressureScore(state), DELTA);
    }

    @Test
    void laneCombatKillUsesOnlyItsConfiguredObjectiveLaneImpact() {
        GameState top = state();
        resolver.applyLaneCombatKill(top, 100, Lane.TOP, TeamSide.BLUE);
        assertEquals(0, top.getObjectivePriorityState().getDragonRecentControl(), DELTA);
        assertEquals(8, top.getObjectivePriorityState().getBaronRecentControl(), DELTA);

        GameState bot = state();
        resolver.applyLaneCombatKill(bot, 100, Lane.BOT, TeamSide.RED);
        assertEquals(-8, bot.getObjectivePriorityState().getDragonRecentControl(), DELTA);
        assertEquals(0, bot.getObjectivePriorityState().getBaronRecentControl(), DELTA);

        GameState mid = state();
        resolver.applyLaneCombatKill(mid, 100, Lane.MID, TeamSide.BLUE);
        assertEquals(6.4, mid.getObjectivePriorityState().getDragonRecentControl(), DELTA);
        assertEquals(6.4, mid.getObjectivePriorityState().getBaronRecentControl(), DELTA);
    }

    @Test
    void gankCounterGankAndRoamUseActualWinningSideAndTargetLane() {
        GameState state = state();
        resolver.applyJungleGankKill(state, 100, Lane.BOT, TeamSide.RED);
        resolver.applyCounterGankKill(state, 110, Lane.TOP, TeamSide.BLUE);
        resolver.applyRoamKill(state, 120, Lane.BOT, TeamSide.RED);

        assertEquals(-22, state.getObjectivePriorityState().getDragonRecentControl(), DELTA);
        assertEquals(12, state.getObjectivePriorityState().getBaronRecentControl(), DELTA);
    }

    @Test
    void noKillDoesNotApplyImpactAndTeamfightGradesHaveExpectedSignedImpact() {
        GameState noKill = state();
        assertFalse(resolver.applyLaneCombatOutcome(noKill, 100, Lane.MID, TeamSide.BLUE, LaneCombatOutcome.NO_KILL));
        assertFalse(resolver.applyJungleGankOutcome(noKill, 101, Lane.BOT, TeamSide.BLUE, JungleGankOutcome.NO_KILL));
        assertFalse(resolver.applyCounterGankOutcome(noKill, 102, Lane.TOP, TeamSide.BLUE, CounterGankOutcome.NO_KILL));
        assertFalse(resolver.applyRoamOutcome(noKill, 103, Lane.BOT, TeamSide.BLUE, RoamOutcome.NO_KILL));
        assertEquals(0, noKill.getObjectivePriorityState().getDragonRecentControl(), DELTA);

        for (FightGrade grade : FightGrade.values()) {
            GameState blue = state();
            GameState red = state();
            resolver.applyTeamfightWin(blue, 100, outcome(TeamSide.BLUE, grade));
            resolver.applyTeamfightWin(red, 100, outcome(TeamSide.RED, grade));
            double expected = switch (grade) {
                case SMALL_WIN -> 6;
                case NORMAL_WIN -> 10;
                case BIG_WIN -> 16;
                case ACE -> 24;
            };
            assertEquals(expected, blue.getObjectivePriorityState().getDragonRecentControl(), DELTA);
            assertEquals(expected, blue.getObjectivePriorityState().getBaronRecentControl(), DELTA);
            assertEquals(-expected, red.getObjectivePriorityState().getDragonRecentControl(), DELTA);
            assertEquals(-expected, red.getObjectivePriorityState().getBaronRecentControl(), DELTA);
        }
    }

    @Test
    void recentControlClampsAndStructuredImpactKeyPreventsDuplicateApplication() {
        GameState state = state();
        for (int time = 1; time <= 5; time++) resolver.applyJungleGankKill(state, time, Lane.BOT, TeamSide.BLUE);
        assertEquals(40, state.getObjectivePriorityState().getDragonRecentControl(), DELTA);
        assertTrue(resolver.applyLaneCombatKill(state, 100, Lane.BOT, TeamSide.RED));
        assertFalse(resolver.applyLaneCombatKill(state, 100, Lane.BOT, TeamSide.RED));
        assertEquals(32, state.getObjectivePriorityState().getDragonRecentControl(), DELTA);

        GameState negative = state();
        for (int time = 1; time <= 5; time++) resolver.applyJungleGankKill(negative, time, Lane.BOT, TeamSide.RED);
        assertEquals(-40, negative.getObjectivePriorityState().getDragonRecentControl(), DELTA);
    }

    @Test
    void structuredImpactDoesNotDependOnDisplayNamesMessagesOrKillEventRescans() {
        GameState renamed = state("renamed blue", "renamed red");
        GameState original = state("BLUE", "RED");
        resolver.applyRoamKill(renamed, 100, Lane.TOP, TeamSide.BLUE);
        resolver.applyRoamKill(original, 100, Lane.TOP, TeamSide.BLUE);

        assertEquals(original.getObjectivePriorityState().getDragonRecentControl(),
                renamed.getObjectivePriorityState().getDragonRecentControl(), DELTA);
        assertEquals(original.getObjectivePriorityState().getBaronRecentControl(),
                renamed.getObjectivePriorityState().getBaronRecentControl(), DELTA);
        assertFalse(resolver.applyRoamKill(renamed, 100, Lane.TOP, TeamSide.BLUE));
    }

    @Test
    void priorityTrackingPreservesSameSeedTimelineReproducibility() {
        MatchSimulator simulator = new MatchSimulator(
                new TeamfightResolver(), new EndGameEvaluator(), new SnapshotFactory(),
                new ObjectiveResolver(), new PostFightResolver(), new ObjectiveAttemptResolver(),
                new StructureResolver(), new PushResolver());
        DummyDataFactory teams = new DummyDataFactory();

        MatchTimeline first = simulator.simulate(teams.createBlueTeam(), teams.createRedTeam(), 98_765L);
        MatchTimeline second = simulator.simulate(teams.createBlueTeam(), teams.createRedTeam(), 98_765L);

        assertEquals(first.getDurationSeconds(), second.getDurationSeconds());
        assertEquals(first.getWinner(), second.getWinner());
        assertEquals(first.getSnapshots().size(), second.getSnapshots().size());
        assertEquals(eventSignatures(first.getEvents()), eventSignatures(second.getEvents()));
    }

    @Test
    void finalPriorityAddsPressureAndRecentControlThenClamps() {
        GameState state = state();
        state.laneState(Lane.TOP).setPressure(100);
        state.laneState(Lane.MID).setPressure(100);
        state.laneState(Lane.BOT).setPressure(100);
        for (int time = 1; time <= 5; time++) {
            resolver.applyJungleGankKill(state, time, Lane.BOT, TeamSide.BLUE);
            resolver.applyJungleGankKill(state, time, Lane.TOP, TeamSide.BLUE);
        }
        assertEquals(100, resolver.dragonSignedPriority(state), DELTA);
        assertEquals(100, resolver.baronSignedPriority(state), DELTA);

        state.laneState(Lane.TOP).setPressure(-100);
        state.laneState(Lane.MID).setPressure(-100);
        state.laneState(Lane.BOT).setPressure(-100);
        GameState negative = state();
        negative.laneState(Lane.TOP).setPressure(-100);
        negative.laneState(Lane.MID).setPressure(-100);
        negative.laneState(Lane.BOT).setPressure(-100);
        for (int time = 1; time <= 5; time++) {
            resolver.applyJungleGankKill(negative, time, Lane.BOT, TeamSide.RED);
            resolver.applyJungleGankKill(negative, time, Lane.TOP, TeamSide.RED);
        }
        assertEquals(-100, resolver.dragonSignedPriority(negative), DELTA);
        assertEquals(-100, resolver.baronSignedPriority(negative), DELTA);
    }

    private List<String> eventSignatures(List<MatchEvent> events) {
        return events.stream().map(event -> event.getTimeSeconds() + "|" + event.getType() + "|"
                + event.getMessage() + "|" + event.getKiller() + "|" + event.getVictim() + "|"
                + event.getAssists() + "|" + event.getCombatSource() + "|" + event.getLaneCombat()
                + "|" + event.getJungleGank() + "|" + event.getCounterGank() + "|" + event.getRoam()).toList();
    }

    private TeamfightOutcome outcome(TeamSide side, FightGrade grade) {
        return new TeamfightOutcome(side, grade, 1, 0, 100, List.of());
    }

    private GameState state() { return state("BLUE", "RED"); }

    private GameState state(String blueName, String redName) {
        return new GameState(team(blueName), team(redName));
    }

    private TeamState team(String name) {
        return new TeamState(name, List.of(
                player(name + " top", Position.TOP),
                player(name + " jungle", Position.JUNGLE),
                player(name + " mid", Position.MID),
                player(name + " adc", Position.ADC),
                player(name + " support", Position.SUPPORT)
        ));
    }

    private PlayerState player(String name, Position position) {
        return new PlayerState(name, position, new PlayerAttributes(14, 14, 14, 14), 500);
    }
}
