package com.lolfm.simulator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.lolfm.domain.ObjectiveFightSkillImpactData;
import com.lolfm.domain.Position;
import java.util.ArrayList;
import java.util.Random;
import org.junit.jupiter.api.Test;

class ObjectiveFightSkillImpactTest {
    private final ObjectiveFightResolver resolver = new ObjectiveFightResolver();

    @Test
    void detailedAreaSetupAndVisionAddAVisiblePerspectiveSymmetricFightEdge() {
        TeamState blue = ObjectivePlayerSkillTestSupport.team(
                "BLUE", TeamSide.BLUE, true, 14, 14, true, 20, 20);
        TeamState red = ObjectivePlayerSkillTestSupport.team(
                "RED", TeamSide.RED, true, 14, 14, true, 14, 14);
        GameState blueStrong = ObjectivePlayerSkillTestSupport.dragonState(blue, red);

        ObjectiveFightSkillImpactData blueImpact = resolver.objectiveSkillImpact(blueStrong);

        assertThat(blueImpact.blueAreaSetupScore()).isGreaterThan(14);
        assertThat(blueImpact.blueVisionControlScore()).isGreaterThan(14);
        assertThat(blueImpact.blueSetupContribution()).isPositive();
        assertThat(blueImpact.redSetupContribution()).isZero();
        assertThat(blueImpact.setupEdgeContribution()).isEqualTo(blueImpact.blueSetupContribution());

        TeamState swappedBlue = ObjectivePlayerSkillTestSupport.team(
                "BLUE", TeamSide.BLUE, true, 14, 14, true, 14, 14);
        TeamState swappedRed = ObjectivePlayerSkillTestSupport.team(
                "RED", TeamSide.RED, true, 14, 14, true, 20, 20);
        ObjectiveFightSkillImpactData redImpact = resolver.objectiveSkillImpact(
                ObjectivePlayerSkillTestSupport.dragonState(swappedBlue, swappedRed));
        assertThat(redImpact.setupEdgeContribution()).isEqualTo(-blueImpact.setupEdgeContribution());
    }

    @Test
    void deadOrUnavailableSupportContributesNothing() {
        GameState dead = highBlueSupportState();
        dead.getBlueTeamState().playerAt(Position.SUPPORT).markDead(dead.getCurrentTimeSeconds(), 100);
        ObjectiveFightSkillImpactData deadImpact = resolver.objectiveSkillImpact(dead);
        assertThat(deadImpact.blueSetupContribution()).isZero();
        assertThat(deadImpact.blueAreaSetupScore()).isEqualTo(14);
        assertThat(deadImpact.blueVisionControlScore()).isEqualTo(14);

        GameState unavailable = highBlueSupportState();
        unavailable.getBlueTeamState().playerAt(Position.SUPPORT).beginRoamActivity(
                Lane.BOT, Lane.MID, unavailable.getCurrentTimeSeconds());
        ObjectiveFightSkillImpactData unavailableImpact = resolver.objectiveSkillImpact(unavailable);
        assertThat(unavailableImpact.blueSetupContribution()).isZero();
        assertThat(unavailableImpact.blueAreaSetupScore()).isEqualTo(14);
        assertThat(unavailableImpact.blueVisionControlScore()).isEqualTo(14);
    }

    @Test
    void legacySupportPreservesTheExistingNeutralFightScore() {
        GameState state = ObjectivePlayerSkillTestSupport.dragonState(
                ObjectivePlayerSkillTestSupport.team(
                        "BLUE", TeamSide.BLUE, true, 14, 14, false, 14, 14),
                ObjectivePlayerSkillTestSupport.team(
                        "RED", TeamSide.RED, true, 14, 14, false, 14, 14));
        ObjectiveFightSkillImpactData impact = resolver.objectiveSkillImpact(state);
        assertThat(impact.blueSetupContribution()).isZero();
        assertThat(impact.redSetupContribution()).isZero();
        assertThat(impact.setupEdgeContribution()).isZero();
    }

    @Test
    void sameFightNoiseLetsSetupAndVisionEdgeFlipTheActualWinner() {
        GameState blueStrong = highBlueSupportState();
        GameState redStrong = ObjectivePlayerSkillTestSupport.dragonState(
                ObjectivePlayerSkillTestSupport.team(
                        "BLUE", TeamSide.BLUE, true, 14, 14, true, 14, 14),
                ObjectivePlayerSkillTestSupport.team(
                        "RED", TeamSide.RED, true, 14, 14, true, 20, 20));

        ObjectiveFightOutcome blueOutcome = resolver.resolve(
                blueStrong, new FixedRandom(.5), new ArrayList<>(), "TEST:BLUE");
        ObjectiveFightOutcome redOutcome = resolver.resolve(
                redStrong, new FixedRandom(.5), new ArrayList<>(), "TEST:RED");

        assertThat(blueOutcome.winningSide()).isEqualTo(TeamSide.BLUE);
        assertThat(redOutcome.winningSide()).isEqualTo(TeamSide.RED);
    }

    @Test
    void structuredSkillImpactRejectsNonFiniteOrMismatchedEdge() {
        assertThatThrownBy(() -> new ObjectiveFightSkillImpactData(
                Double.NaN, 14, 14, 14, 0, 0, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ObjectiveFightSkillImpactData(
                14, 14, 14, 14, 2, 1, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private GameState highBlueSupportState() {
        return ObjectivePlayerSkillTestSupport.dragonState(
                ObjectivePlayerSkillTestSupport.team(
                        "BLUE", TeamSide.BLUE, true, 14, 14, true, 20, 20),
                ObjectivePlayerSkillTestSupport.team(
                        "RED", TeamSide.RED, true, 14, 14, true, 14, 14));
    }

    private static final class FixedRandom extends Random {
        private final double value;
        private FixedRandom(double value) { this.value = value; }
        @Override public double nextDouble() { return value; }
        @Override public int nextInt(int bound) { return 0; }
        @Override public boolean nextBoolean() { return false; }
    }
}
