package com.lolfm.simulator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.lolfm.champion.ChampionId;
import com.lolfm.champion.ChampionRoleKey;
import com.lolfm.domain.Position;
import org.junit.jupiter.api.Test;

class JungleTempoStateTest {
    @Test
    void neutralClearReachesFirstReadinessAndActualActionConsumesBoundedCredit() {
        JungleTempoState state = new JungleTempoState();
        for (int time = 10; time <= 180; time += 10) {
            state.recordEconomyOutcome(outcome(time, 1.0));
        }

        JungleTempoState.Readiness first = state.readinessAt(180);
        assertThat(first.status()).isEqualTo(JungleTempoReadinessStatus.READY);
        assertThat(first.creditSeconds()).isEqualTo(180.0);
        assertThat(first.requiredCreditSeconds()).isEqualTo(180.0);

        JungleTempoState.Consumption consumed = state.consumeActualActionAt(180);
        assertThat(consumed.creditBeforeSeconds()).isEqualTo(180.0);
        assertThat(consumed.creditAfterSeconds()).isEqualTo(30.0);
        assertThat(consumed.actualActionCount()).isEqualTo(1);
        assertThat(state.snapshot().lastActualActionAtSeconds()).isEqualTo(180);
        assertThatThrownBy(() -> state.consumeActualActionAt(180))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("advance exactly once");
    }

    @Test
    void repeatReadinessUsesActionCostThresholdAndNeverBanksBeyondCap() {
        JungleTempoState state = new JungleTempoState();
        for (int time = 10; time <= 180; time += 10) {
            state.recordEconomyOutcome(outcome(time, 2.0));
        }
        assertThat(state.snapshot().creditSeconds()).isEqualTo(207.0);
        state.consumeActualActionAt(180);

        for (int time = 190; time <= 300; time += 10) {
            state.recordEconomyOutcome(outcome(time, 2.0));
        }
        assertThat(state.readinessAt(300).status())
                .isEqualTo(JungleTempoReadinessStatus.READY);
        assertThat(state.readinessAt(300).requiredCreditSeconds()).isEqualTo(150.0);
        state.consumeActualActionAt(300);

        for (int time = 310; time <= 700; time += 10) {
            state.recordEconomyOutcome(outcome(time, 2.0));
        }
        assertThat(state.snapshot().creditSeconds())
                .isEqualTo(JungleTempoRuleConfig.MAX_BANKED_CREDIT_SECONDS);
    }

    @Test
    void creditEfficiencyIsClampedAtBothBoundaries() {
        JungleTempoState low = new JungleTempoState();
        JungleTempoState high = new JungleTempoState();

        JungleTempoState.CreditUpdate lowUpdate =
                low.recordEconomyOutcome(outcome(10, 0.10));
        JungleTempoState.CreditUpdate highUpdate =
                high.recordEconomyOutcome(outcome(10, 1.90));

        assertThat(lowUpdate.boundedEfficiency()).isEqualTo(0.85);
        assertThat(lowUpdate.addedCreditSeconds()).isEqualTo(8.5);
        assertThat(highUpdate.boundedEfficiency()).isEqualTo(1.15);
        assertThat(highUpdate.addedCreditSeconds()).isEqualTo(11.5);
    }

    @Test
    void expectedActionGapPreservesContinuityButLongerGapResetsOnNextOutcome() {
        JungleTempoState state = new JungleTempoState();
        state.recordEconomyOutcome(outcome(10, 1.0));
        JungleTempoState.CreditUpdate withinGrace =
                state.recordEconomyOutcome(outcome(40, 1.0));
        JungleTempoState.CreditUpdate reset =
                state.recordEconomyOutcome(outcome(80, 1.0));

        assertThat(withinGrace.continuityReset()).isFalse();
        assertThat(withinGrace.creditAfterSeconds()).isEqualTo(20.0);
        assertThat(reset.continuityReset()).isTrue();
        assertThat(reset.creditAfterSeconds()).isEqualTo(10.0);
        assertThat(state.snapshot().continuityResetCount()).isEqualTo(1);
    }

    @Test
    void readinessRequiresCurrentOutcomeAndRejectsBackwardsOrDuplicateTime() {
        JungleTempoState state = new JungleTempoState();
        state.recordEconomyOutcome(outcome(10, 1.0));

        assertThat(state.readinessAt(20).status())
                .isEqualTo(JungleTempoReadinessStatus.NO_CURRENT_ECONOMY_OUTCOME);
        assertThatThrownBy(() -> state.readinessAt(9))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("backwards");
        assertThatThrownBy(() -> state.recordEconomyOutcome(outcome(10, 1.0)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("advance exactly once");
        assertThatThrownBy(() -> state.consumeActualActionAt(20))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not ready");
    }

    private JungleEconomyOutcome outcome(int timeSeconds, double efficiency) {
        return new JungleEconomyOutcome(
                TeamSide.BLUE, new PlayerKey(TeamSide.BLUE, Position.JUNGLE),
                new ChampionRoleKey(new ChampionId("belveth"), Position.JUNGLE),
                "tempo-test-v1", timeSeconds, 10, 1.0, efficiency,
                efficiency, 0.0, 0, 0, 0);
    }
}
