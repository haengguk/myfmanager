package com.lolfm.simulator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.lolfm.champion.ChampionId;
import com.lolfm.champion.ChampionResourceSet;
import com.lolfm.champion.ChampionRoleKey;
import com.lolfm.champion.ChampionSelectionValidator;
import com.lolfm.champion.MatchChampionAssignments;
import com.lolfm.domain.MatchEvent;
import com.lolfm.domain.Position;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

class JungleTempoGankIntegrationTest {
    private static final ChampionResourceSet RESOURCES = ChampionResourceSet.loadDefault();

    @Test
    void notReadySidesConsumeNoRandomOrActionStateAndFallThrough() {
        GameState state = at180(tempoState());
        CountingRandom random = new CountingRandom(0.0);

        assertThat(new JungleGankResolver(false).resolve(
                state, random, new ArrayList<>())).isFalse();

        assertThat(random.calls).isZero();
        assertThat(state.jungleActionState(TeamSide.BLUE).getLastJungleActionAtSeconds())
                .isEqualTo(-1);
        assertThat(state.jungleTempoState(TeamSide.BLUE).snapshot().actualActionCount())
                .isZero();
        assertThat(state.getCombatExecutionStats().snapshot().jungleGankNoEligibleSides())
                .isEqualTo(1);
        assertThat(state.getCombatExecutionStats().snapshot().jungleGankFallthroughs())
                .isEqualTo(1);
        assertThat(state.getJungleTempoExecutionStats().snapshot(
                state.getJungleTempoStates()).gankReadinessByStatus())
                .containsEntry(JungleTempoReadinessStatus.NO_CURRENT_ECONOMY_OUTCOME, 2);
    }

    @Test
    void currentButInsufficientCreditConsumesNoRandomAndAllowsLaneCombat() {
        GameState state = tempoState();
        creditInsufficientAt180(state, TeamSide.BLUE);
        creditInsufficientAt180(state, TeamSide.RED);
        at180(state);
        CountingRandom random = new CountingRandom(0.0);

        assertThat(new JungleGankResolver(false).resolve(
                state, random, new ArrayList<>())).isFalse();
        assertThat(random.calls).isZero();

        List<MatchEvent> laneEvents = new ArrayList<>();
        assertThat(new LaneCombatResolver().resolve(
                state, new SequenceRandom(0, 1, 1, .5, 0, .99), laneEvents)).isTrue();
        assertThat(state.getCombatExecutionStats().snapshot().laneCombatAttempts())
                .isEqualTo(1);
        assertThat(state.getJungleTempoExecutionStats().snapshot(
                state.getJungleTempoStates()).gankReadinessByStatus())
                .containsEntry(JungleTempoReadinessStatus.INSUFFICIENT_CREDIT, 2);
    }

    @Test
    void failedTriggersKeepCreditAndConsumeOnlyTheTwoEligibleRolls() {
        GameState state = readyAt180();
        CountingRandom random = new CountingRandom(0.99);

        assertThat(new JungleGankResolver(false).resolve(
                state, random, new ArrayList<>())).isFalse();

        assertThat(random.calls).isEqualTo(2);
        assertThat(state.jungleTempoState(TeamSide.BLUE).snapshot().creditSeconds())
                .isEqualTo(180.0);
        assertThat(state.jungleTempoState(TeamSide.RED).snapshot().creditSeconds())
                .isEqualTo(180.0);
        assertThat(state.getCombatExecutionStats().snapshot().jungleGankAllTriggersFailed())
                .isEqualTo(1);
        assertThat(state.getJungleTempoExecutionStats().snapshot(
                state.getJungleTempoStates()).actualConsumptions().values())
                .containsOnly(0);
    }

    @Test
    void actualNoKillGankConsumesSelectedSideExactlyOnceAndDuplicateIsIdempotent() {
        GameState state = readyAt180();
        SequenceRandom random = new SequenceRandom(0, .99, 0, .99);
        List<MatchEvent> events = new ArrayList<>();
        JungleGankResolver resolver = new JungleGankResolver(false);

        assertThat(resolver.resolve(state, random, events)).isTrue();
        int drawsAfterAttempt = random.index;
        assertThat(state.jungleTempoState(TeamSide.BLUE).snapshot().creditSeconds())
                .isEqualTo(30.0);
        assertThat(state.jungleTempoState(TeamSide.RED).snapshot().creditSeconds())
                .isEqualTo(180.0);
        assertThat(state.jungleTempoState(TeamSide.BLUE).snapshot().actualActionCount())
                .isEqualTo(1);
        assertThat(state.getJungleTempoExecutionStats().snapshot(
                state.getJungleTempoStates()).actualConsumptions())
                .containsEntry(JungleTempoActionType.GANK, 1)
                .containsEntry(JungleTempoActionType.COUNTER_GANK, 0);

        assertThat(resolver.resolve(state, random, events)).isFalse();
        assertThat(random.index).isEqualTo(drawsAfterAttempt);
        assertThat(state.jungleTempoState(TeamSide.BLUE).snapshot().actualActionCount())
                .isEqualTo(1);
    }

    @Test
    void notReadyDefenderCannotRollCounterGankOrConsumeCredit() {
        GameState state = tempoState();
        credit(state, TeamSide.BLUE, 18, 1.0);
        creditInsufficientAt180(state, TeamSide.RED);
        at180(state);
        SequenceRandom random = new SequenceRandom(0, 0, .99);

        assertThat(new JungleGankResolver(true).resolve(
                state, random, new ArrayList<>())).isTrue();

        assertThat(random.index).isEqualTo(3);
        assertThat(state.getCombatExecutionStats().snapshot().counterGankAttempts())
                .isZero();
        assertThat(state.jungleTempoState(TeamSide.RED).snapshot().actualActionCount())
                .isZero();
        assertThat(state.getJungleTempoExecutionStats().snapshot(
                state.getJungleTempoStates()).counterGankReadinessByStatus())
                .containsEntry(JungleTempoReadinessStatus.INSUFFICIENT_CREDIT, 1);
    }

    @Test
    void actualCounterGankConsumesBothStructuredActionsExactlyOnce() {
        GameState state = readyAt180();
        SequenceRandom random = new SequenceRandom(0, .99, 0, 0, .99);

        assertThat(new JungleGankResolver(true).resolve(
                state, random, new ArrayList<>())).isTrue();

        assertThat(random.index).isEqualTo(5);
        assertThat(state.jungleTempoState(TeamSide.BLUE).snapshot().creditSeconds())
                .isEqualTo(30.0);
        assertThat(state.jungleTempoState(TeamSide.RED).snapshot().creditSeconds())
                .isEqualTo(30.0);
        assertThat(state.getCombatExecutionStats().snapshot().jungleGankAttempts())
                .isEqualTo(1);
        assertThat(state.getCombatExecutionStats().snapshot().counterGankAttempts())
                .isEqualTo(1);
        assertThat(state.getJungleTempoExecutionStats().snapshot(
                state.getJungleTempoStates()).actualConsumptions())
                .containsEntry(JungleTempoActionType.GANK, 1)
                .containsEntry(JungleTempoActionType.COUNTER_GANK, 1);
    }

    @Test
    void diagnosticSnapshotMapsAreCanonicalAndImmutable() {
        GameState state = readyAt180();
        new JungleGankResolver(false).resolve(
                state, new SequenceRandom(.99, .99), new ArrayList<>());
        JungleTempoExecutionStatsSnapshot snapshot =
                state.getJungleTempoExecutionStats().snapshot(state.getJungleTempoStates());

        assertThat(snapshot.gankReadinessByStatus().keySet())
                .containsExactly(JungleTempoReadinessStatus.values());
        assertThat(snapshot.actualConsumptions().keySet())
                .containsExactly(JungleTempoActionType.values());
        assertThat(snapshot.stateBySide().keySet()).containsExactly(TeamSide.values());
        assertThatThrownBy(() -> snapshot.actualConsumptions().put(
                JungleTempoActionType.GANK, 99))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> state.getJungleTempoStates().put(
                TeamSide.BLUE, new JungleTempoState()))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    private GameState readyAt180() {
        GameState state = tempoState();
        creditBoth(state, 18, 1.0);
        return at180(state);
    }

    private void creditBoth(GameState state, int ticks, double efficiency) {
        credit(state, TeamSide.BLUE, ticks, efficiency);
        credit(state, TeamSide.RED, ticks, efficiency);
    }

    private void credit(GameState state, TeamSide side, int ticks, double efficiency) {
        for (int tick = 1; tick <= ticks; tick++) {
            state.jungleTempoState(side).recordEconomyOutcome(
                    outcome(side, tick * 10, efficiency));
        }
    }

    private void creditInsufficientAt180(GameState state, TeamSide side) {
        credit(state, side, 12, 0.85);
        state.jungleTempoState(side).recordEconomyOutcome(
                outcome(side, 180, 0.85));
    }

    private JungleEconomyOutcome outcome(
            TeamSide side,
            int timeSeconds,
            double efficiency
    ) {
        return new JungleEconomyOutcome(
                side, new PlayerKey(side, Position.JUNGLE),
                new ChampionRoleKey(new ChampionId("belveth"), Position.JUNGLE),
                "tempo-test-v1", timeSeconds, 10, 1.0, efficiency,
                efficiency, 0.0, 0, 0, 0);
    }

    private GameState tempoState() {
        MatchChampionAssignments assignments = new ChampionSelectionValidator(
                RESOURCES.catalog()).resolve(null);
        GameState state = new GameState(
                LateGameTestSupport.team("BLUE"), LateGameTestSupport.team("RED"),
                true, true, true, true, true, true, assignments);
        state.configureJungleEconomy(
                RESOURCES.jungleClear(),
                JungleClearContribution.ECONOMY_AND_GANK_TEMPO_V1);
        return state;
    }

    private GameState at180(GameState state) {
        state.advanceTimeSeconds(180);
        return state;
    }

    private static final class CountingRandom extends Random {
        private final double value;
        private int calls;

        private CountingRandom(double value) {
            this.value = value;
        }

        @Override
        public double nextDouble() {
            calls++;
            return value;
        }
    }

    private static final class SequenceRandom extends Random {
        private final double[] values;
        private int index;

        private SequenceRandom(double... values) {
            this.values = values;
        }

        @Override
        public double nextDouble() {
            return index < values.length ? values[index++] : values[values.length - 1];
        }
    }
}
