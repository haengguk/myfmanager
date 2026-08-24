package com.lolfm.simulator;

import static org.assertj.core.api.Assertions.assertThat;

import com.lolfm.domain.ObjectiveSelectionWeightBreakdown;
import com.lolfm.domain.Position;
import org.junit.jupiter.api.Test;

class ObjectivePlayerSkillAttemptTest {
    private final ObjectiveAttemptResolver resolver = new ObjectiveAttemptResolver();

    @Test
    void legacyJunglerDoesNotSuppressDetailedSupportContributions() {
        TeamState blue = ObjectivePlayerSkillTestSupport.team(
                "BLUE", TeamSide.BLUE, false, 14, 14, true, 20, 20);
        TeamState red = ObjectivePlayerSkillTestSupport.team(
                "RED", TeamSide.RED, false, 14, 14, false, 14, 14);
        GameState state = ObjectivePlayerSkillTestSupport.dragonState(blue, red);

        ObjectiveSelectionWeightBreakdown weight = resolver.objectiveWeightBreakdown(state, TeamSide.BLUE);

        assertThat(weight.objectiveSecureContribution()).isZero();
        assertThat(weight.areaSetupContribution()).isPositive();
        assertThat(weight.visionControlContribution()).isPositive();
        assertThat(weight.otherContribution()).isEqualTo(
                weight.areaSetupContribution() + weight.visionControlContribution());
        assertThat(weight.totalExistingWeight()).isEqualTo(
                weight.aliveContribution() + weight.goldContribution() + weight.killContribution()
                        + weight.recentBigWinContribution() + weight.recentAceContribution()
                        + weight.otherContribution());
    }

    @Test
    void detailedJunglerAndLegacySupportRemainIndependentlyAttributed() {
        TeamState blue = ObjectivePlayerSkillTestSupport.team(
                "BLUE", TeamSide.BLUE, true, 14, 20, false, 14, 14);
        TeamState red = ObjectivePlayerSkillTestSupport.team(
                "RED", TeamSide.RED, false, 14, 14, false, 14, 14);
        GameState state = ObjectivePlayerSkillTestSupport.dragonState(blue, red);

        ObjectiveSelectionWeightBreakdown weight = resolver.objectiveWeightBreakdown(state, TeamSide.BLUE);

        assertThat(weight.objectiveSecureContribution()).isPositive();
        assertThat(weight.areaSetupContribution()).isZero();
        assertThat(weight.visionControlContribution()).isZero();
        assertThat(weight.otherContribution()).isEqualTo(weight.objectiveSecureContribution());
    }

    @Test
    void deadJunglerAndRoamingSupportDoNotContributeToAttemptWeight() {
        TeamState blue = ObjectivePlayerSkillTestSupport.team(
                "BLUE", TeamSide.BLUE, true, 14, 20, true, 20, 20);
        TeamState red = ObjectivePlayerSkillTestSupport.team(
                "RED", TeamSide.RED, true, 14, 14, true, 14, 14);
        GameState state = ObjectivePlayerSkillTestSupport.dragonState(blue, red);
        int time = state.getCurrentTimeSeconds();
        blue.playerAt(Position.JUNGLE).markDead(time, 100);
        blue.playerAt(Position.SUPPORT).beginRoamActivity(Lane.BOT, Lane.MID, time);

        ObjectiveSelectionWeightBreakdown weight = resolver.objectiveWeightBreakdown(
                state, TeamSide.BLUE);

        assertThat(weight.objectiveSecureContribution()).isZero();
        assertThat(weight.areaSetupContribution()).isZero();
        assertThat(weight.visionControlContribution()).isZero();
        assertThat(weight.otherContribution()).isZero();
    }
}
