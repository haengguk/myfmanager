package com.lolfm.simulator;

import static org.assertj.core.api.Assertions.assertThat;

import com.lolfm.domain.MatchEvent;
import java.util.ArrayList;
import java.util.Optional;
import java.util.Random;
import org.junit.jupiter.api.Test;

class StructureActionAttemptAuditTest {
    private final StructureResolver structures = new StructureResolver();
    private final PushResolver pushes = new PushResolver();

    @Test void failedObjectiveTradeDoesNotConsumeStructureSlot() {
        GameState state = ObjectiveDecisionTestSupport.dragonState(true);
        MatchEvent capture = new ObjectiveDecisionResolver().resolve(state, ObjectiveType.DRAGON, TeamSide.BLUE, 0,
                new ObjectiveDecisionTestSupport.SequenceRandom(0, .999, .999), new ObjectiveResolver(), structures,
                new ArrayList<>(), null).orElseThrow();
        assertThat(capture.getObjectiveDecision().tradeRollExecuted()).isTrue();
        assertThat(capture.getObjectiveDecision().tradeSucceeded()).isFalse();
        assertThat(state.wasStructureActionAttemptedThisTick(TeamSide.RED)).isFalse();
        assertThat(state.wasStructureMutationPerformedThisTick(TeamSide.RED)).isFalse();
    }

    @Test void failedLateGameSiegeBlocksSameSideGenericPush() { assertFailedAttemptBlocksGeneric(); }
    @Test void failedNexusFinishBlocksSameSideGenericPush() { assertFailedAttemptBlocksGeneric(); }

    @Test void evaluationOnlyDoesNotBlockLaterStructureAttempt() {
        GameState state = macroState();
        assertThat(new BaseThreatEvaluator().evaluate(state, TeamSide.RED)).isNotNull();
        state.getMapState().markPushAttempted(TeamSide.RED, state.getCurrentTimeSeconds(), 10_000);
        double before = state.getMapState().getLaneState(TeamSide.RED, Lane.TOP)
                .getTowerCurrentHealth(TowerTier.OUTER);
        assertThat(pushes.maybeResolveMacroPush(state, new CountingRandom(0), structures)).isEmpty();
        assertThat(state.getMapState().getLaneState(TeamSide.RED, Lane.TOP)
                .getTowerCurrentHealth(TowerTier.OUTER)).isLessThan(before);
        assertThat(state.wasStructureMutationPerformedThisTick(TeamSide.BLUE)).isTrue();
    }

    @Test void ineligibleActionDoesNotBlockLaterStructureAttempt() {
        GameState state = macroState();
        assertThat(structures.destroyTarget(state, TeamSide.BLUE, Lane.TOP,
                LateGameStructureTarget.NEXUS, PushReason.NEXUS_FINISH)).isEmpty();
        assertThat(state.wasStructureActionAttemptedThisTick(TeamSide.BLUE)).isFalse();
        state.getMapState().markPushAttempted(TeamSide.RED, state.getCurrentTimeSeconds(), 10_000);
        double before = state.getMapState().getLaneState(TeamSide.RED, Lane.TOP)
                .getTowerCurrentHealth(TowerTier.OUTER);
        assertThat(pushes.maybeResolveMacroPush(state, new CountingRandom(0), structures)).isEmpty();
        assertThat(state.getMapState().getLaneState(TeamSide.RED, Lane.TOP)
                .getTowerCurrentHealth(TowerTier.OUTER)).isLessThan(before);
    }

    @Test void failedBlueAttemptDoesNotBlockRedStructureAttempt() {
        GameState state = macroState();
        state.markStructureActionAttempted(TeamSide.BLUE);
        Optional<StructureOutcome> red = pushes.maybeResolveMacroPush(state, new CountingRandom(0), structures);
        assertThat(red).isEmpty();
        assertThat(state.wasStructureMutationPerformedThisTick(TeamSide.BLUE)).isFalse();
        assertThat(state.wasStructureMutationPerformedThisTick(TeamSide.RED)).isTrue();
    }

    @Test void postFightWindowAllowsOnlyOneMutationInTheCurrentTick() {
        GameState state = postFightWindowState();
        TeamfightOutcome fight = new TeamfightOutcome(TeamSide.BLUE, FightGrade.BIG_WIN, 4, 0, 2_700, java.util.List.of());
        java.util.List<StructureOutcome> outcomes = pushes.resolvePostFightWindow(
                state, Optional.of(fight), Optional.empty(), new CountingRandom(0), structures);
        StructureActionExecutionStatsSnapshot stats = state.getStructureActionExecutionStats().snapshot();
        assertThat(outcomes).isEmpty();
        assertThat(state.getBaseSiegeState(TeamSide.BLUE).isActive()).isTrue();
        assertThat(stats.structureAttempted()).isOne();
        assertThat(stats.structureMutationPerformed()).isOne();
        assertThat(stats.postFightMultiStructureActions()).isZero();
        assertThat(stats.postFightMultiStructureMutationCount()).isZero();
        assertThat(stats.postFightInternalBlockError()).isZero();
    }

    @Test void postFightWindowBlocksLowerPriorityResolverAfterCompletion() {
        GameState state = postFightWindowState();
        TeamfightOutcome fight = new TeamfightOutcome(TeamSide.BLUE, FightGrade.BIG_WIN, 4, 0, 2_700, java.util.List.of());
        assertThat(pushes.resolvePostFightWindow(state, Optional.of(fight), Optional.empty(),
                new CountingRandom(0), structures)).isEmpty();
        assertThat(state.getBaseSiegeState(TeamSide.BLUE).isActive()).isTrue();
        state.getMapState().markPushAttempted(TeamSide.RED, state.getCurrentTimeSeconds(), 10_000);
        CountingRandom lowerPriorityRandom = new CountingRandom(0);
        assertThat(pushes.maybeResolveMacroPush(state, lowerPriorityRandom, structures)).isEmpty();
        StructureActionExecutionStatsSnapshot stats = state.getStructureActionExecutionStats().snapshot();
        assertThat(stats.laterResolverBlockedByAttempt()).isOne();
        assertThat(stats.sameSideMultipleAttemptError()).isZero();
        assertThat(stats.sameSideMultipleMutationError()).isZero();
    }

    @Test void successfulAttemptRecordsAttemptAndMutationExactlyOnce() {
        GameState state = macroState();
        StructureOutcome outcome = structures.destroyNextTower(state, TeamSide.BLUE, Lane.MID,
                PushReason.LATE_GAME_SIEGE).orElseThrow();
        structures.createStructureEvent(state, outcome);
        StructureActionExecutionStatsSnapshot stats = state.getStructureActionExecutionStats().snapshot();
        assertThat(stats.structureAttempted()).isOne();
        assertThat(stats.structureMutationPerformed()).isOne();
        assertThat(stats.sameSideMultipleAttemptError()).isZero();
        assertThat(stats.sameSideMultipleMutationError()).isZero();
    }

    private void assertFailedAttemptBlocksGeneric() {
        GameState state = macroState();
        state.markStructureActionAttempted(TeamSide.BLUE);
        state.getMapState().markPushAttempted(TeamSide.RED, state.getCurrentTimeSeconds(), 10_000);
        CountingRandom random = new CountingRandom(.999);
        assertThat(pushes.maybeResolveMacroPush(state, random, structures)).isEmpty();
        assertThat(random.calls).isZero();
        assertThat(state.getStructureActionExecutionStats().snapshot().laterResolverBlockedByAttempt()).isOne();
    }

    private GameState postFightWindowState() {
        GameState state = LateGameTestSupport.state();
        state.advanceTimeSeconds(2_700);
        for (PlayerState player : state.getRedTeamState().getPlayers()) player.markDead(2_700, 65);
        return state;
    }

    private GameState macroState() { GameState state = LateGameTestSupport.state(); state.advanceTimeSeconds(480); return state; }

    private static final class CountingRandom extends Random {
        private final double value; private int calls;
        private CountingRandom(double value) { this.value = value; }
        @Override public double nextDouble() { calls++; return value; }
        @Override public boolean nextBoolean() { calls++; return false; }
        @Override public int nextInt(int bound) { calls++; return 0; }
    }
}
