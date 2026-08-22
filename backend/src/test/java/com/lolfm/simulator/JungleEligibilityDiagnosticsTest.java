package com.lolfm.simulator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.lolfm.champion.ChampionResourceSet;
import com.lolfm.champion.ChampionSelectionValidator;
import com.lolfm.domain.MatchEvent;
import com.lolfm.domain.Position;
import java.util.ArrayList;
import java.util.Random;
import org.junit.jupiter.api.Test;

class JungleEligibilityDiagnosticsTest {
    private static final ChampionResourceSet RESOURCES = ChampionResourceSet.loadDefault();

    @Test
    void gankBaseAndTempoIneligibilityAreStructuredBeforeTriggerRandom() {
        GameState unavailable = standardState();
        for (TeamSide side : TeamSide.values()) {
            unavailable.getTeamState(side).playerAt(Position.JUNGLE).markDead(170, 20);
        }
        assertGankIneligibility(unavailable, JungleGankIneligibility.JUNGLER_UNAVAILABLE);

        GameState cooldown = standardState();
        for (TeamSide side : TeamSide.values()) {
            cooldown.jungleActionState(side).recordGankAttempt(120, Lane.TOP);
        }
        assertGankIneligibility(cooldown, JungleGankIneligibility.JUNGLE_ACTION_COOLDOWN);

        GameState noLane = standardState();
        noLane.getRedTeamState().playerAt(Position.TOP).markDead(170, 20);
        noLane.getRedTeamState().playerAt(Position.MID).markDead(170, 20);
        noLane.getRedTeamState().playerAt(Position.ADC).markDead(170, 20);
        assertGankIneligibility(noLane, JungleGankIneligibility.NO_ELIGIBLE_LANE);

        assertGankIneligibility(
                tempoState(), JungleGankIneligibility.JUNGLER_NOT_TEMPO_READY);
    }

    @Test
    void eligibleGankSidesHaveOneStructuredDecisionPerTriggerRoll() {
        GameState state = at(standardState(), 180);
        CountingRandom random = new CountingRandom(0.99);

        assertThat(new JungleGankResolver(false).resolve(
                state, random, new ArrayList<>())).isFalse();

        CombatExecutionStatsSnapshot snapshot = state.getCombatExecutionStats().snapshot();
        assertThat(random.calls).isEqualTo(2);
        assertThat(snapshot.jungleGankEligibilityByReason())
                .containsEntry(JungleGankIneligibility.NONE, 2);
        assertThat(snapshot.latestJungleGankEligibilityBySide())
                .containsEntry(TeamSide.BLUE, JungleGankIneligibility.NONE)
                .containsEntry(TeamSide.RED, JungleGankIneligibility.NONE);
        assertThat(snapshot.jungleGankTriggerRolls())
                .isEqualTo(snapshot.jungleGankEligibilityByReason()
                        .get(JungleGankIneligibility.NONE));
    }

    @Test
    void counterGankIneligibilityIsStructuredAndConsumesNoResponseRandom() {
        GameState outsideWindow = at(standardState(), 170);
        assertCounterIneligibility(
                outsideWindow, CounterGankIneligibility.OUTSIDE_WINDOW);

        GameState defenderDead = standardState();
        defenderDead.getRedTeamState().playerAt(Position.JUNGLE).markDead(170, 20);
        assertCounterIneligibility(
                defenderDead, CounterGankIneligibility.DEFENDING_JUNGLER_DEAD);

        GameState defenderCooldown = standardState();
        defenderCooldown.jungleActionState(TeamSide.RED)
                .recordCounterGankAttempt(120, Lane.TOP);
        assertCounterIneligibility(
                defenderCooldown, CounterGankIneligibility.DEFENDING_JUNGLER_COOLDOWN);

        GameState laneParticipantDead = standardState();
        laneParticipantDead.getRedTeamState().playerAt(Position.TOP).markDead(170, 20);
        assertCounterIneligibility(
                laneParticipantDead, CounterGankIneligibility.LANE_PARTICIPANT_DEAD);

        assertCounterIneligibility(
                tempoState(), CounterGankIneligibility.DEFENDING_JUNGLER_NOT_TEMPO_READY);
    }

    @Test
    void eligibleCounterGankRecordsTheDefendingSideBeforeOneResponseRoll() {
        GameState state = at(standardState(), 180);
        CountingRandom random = new CountingRandom(0.99);

        CounterGankResolver.ResponseDecision decision = new CounterGankResolver().tryResolve(
                state, TeamSide.BLUE, Lane.TOP, false, 0.0,
                random, new ArrayList<MatchEvent>());

        CombatExecutionStatsSnapshot snapshot = state.getCombatExecutionStats().snapshot();
        assertThat(decision.eligible()).isTrue();
        assertThat(decision.responseRolled()).isTrue();
        assertThat(decision.responseSucceeded()).isFalse();
        assertThat(random.calls).isOne();
        assertThat(snapshot.counterGankEligibilityByReason())
                .containsEntry(CounterGankIneligibility.NONE, 1);
        assertThat(snapshot.latestCounterGankEligibilityByDefendingSide())
                .containsEntry(TeamSide.RED, CounterGankIneligibility.NONE);
    }

    @Test
    void eligibilityDiagnosticMapsAreCanonicalImmutableAndMatchScoped() {
        GameState first = at(standardState(), 180);
        new JungleGankResolver(false).resolve(
                first, new CountingRandom(0.99), new ArrayList<>());
        CombatExecutionStatsSnapshot snapshot = first.getCombatExecutionStats().snapshot();

        assertThat(snapshot.jungleGankEligibilityByReason().keySet())
                .containsExactly(JungleGankIneligibility.values());
        assertThat(snapshot.counterGankEligibilityByReason().keySet())
                .containsExactly(CounterGankIneligibility.values());
        assertThat(snapshot.latestJungleGankEligibilityBySide().keySet())
                .containsExactly(TeamSide.values());
        assertThatThrownBy(() -> snapshot.jungleGankEligibilityByReason().put(
                JungleGankIneligibility.NONE, 99))
                .isInstanceOf(UnsupportedOperationException.class);

        CombatExecutionStatsSnapshot fresh = standardState()
                .getCombatExecutionStats().snapshot();
        assertThat(fresh.jungleGankEligibilityByReason().values()).containsOnly(0);
        assertThat(fresh.counterGankEligibilityByReason().values()).containsOnly(0);
        assertThat(fresh.latestJungleGankEligibilityBySide()).isEmpty();
        assertThat(fresh.latestCounterGankEligibilityByDefendingSide()).isEmpty();
    }

    private void assertGankIneligibility(
            GameState state,
            JungleGankIneligibility expected
    ) {
        at(state, 180);
        CountingRandom random = new CountingRandom(0.0);

        assertThat(new JungleGankResolver(false).resolve(
                state, random, new ArrayList<>())).isFalse();

        CombatExecutionStatsSnapshot snapshot = state.getCombatExecutionStats().snapshot();
        assertThat(random.calls).isZero();
        assertThat(snapshot.jungleGankNoEligibleSides()).isOne();
        assertThat(snapshot.jungleGankFallthroughs()).isOne();
        assertThat(snapshot.jungleGankEligibilityByReason()).containsEntry(expected, 2);
        assertThat(snapshot.latestJungleGankEligibilityBySide())
                .containsEntry(TeamSide.BLUE, expected)
                .containsEntry(TeamSide.RED, expected);
    }

    private void assertCounterIneligibility(
            GameState state,
            CounterGankIneligibility expected
    ) {
        if (state.getCurrentTimeSeconds() == 0) at(state, 180);
        CountingRandom random = new CountingRandom(0.0);

        CounterGankResolver.ResponseDecision decision = new CounterGankResolver().tryResolve(
                state, TeamSide.BLUE, Lane.TOP, false, 0.0,
                random, new ArrayList<MatchEvent>());

        CombatExecutionStatsSnapshot snapshot = state.getCombatExecutionStats().snapshot();
        assertThat(decision.eligible()).isFalse();
        assertThat(decision.ineligibility()).isEqualTo(expected);
        assertThat(decision.responseRolled()).isFalse();
        assertThat(random.calls).isZero();
        assertThat(snapshot.counterGankEligibilityByReason()).containsEntry(expected, 1);
        assertThat(snapshot.latestCounterGankEligibilityByDefendingSide())
                .containsEntry(TeamSide.RED, expected);
    }

    private GameState standardState() {
        return new GameState(
                LateGameTestSupport.team("BLUE"), LateGameTestSupport.team("RED"));
    }

    private GameState tempoState() {
        GameState state = new GameState(
                LateGameTestSupport.team("BLUE"), LateGameTestSupport.team("RED"),
                true, true, true, true, true, true,
                new ChampionSelectionValidator(RESOURCES.catalog()).resolve(null));
        state.configureJungleEconomy(
                RESOURCES.jungleClear(),
                JungleClearContribution.ECONOMY_AND_GANK_TEMPO_V1);
        return state;
    }

    private GameState at(GameState state, int timeSeconds) {
        int delta = timeSeconds - state.getCurrentTimeSeconds();
        if (delta > 0) state.advanceTimeSeconds(delta);
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
}
