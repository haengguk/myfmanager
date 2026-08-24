package com.lolfm.simulator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.lolfm.domain.ObjectiveSecureDecisionData;
import com.lolfm.domain.Position;
import java.util.Random;
import org.junit.jupiter.api.Test;

class ObjectiveSecureResolverTest {
    private final ObjectiveSecureResolver resolver = new ObjectiveSecureResolver();

    @Test
    void eligibleContestConsumesExactlyOneDoubleAndUsesStrictLessBoundary() {
        GameState equal = ObjectivePlayerSkillTestSupport.detailedDragonState();
        CountingRandom equalRandom = new CountingRandom(.04);
        ObjectiveSecureDecisionData equalDecision = resolver.resolve(
                equal, ObjectiveType.DRAGON, TeamSide.BLUE, equalRandom);

        assertThat(equalDecision.eligible()).isTrue();
        assertThat(equalDecision.finalStealChance()).isEqualTo(.04);
        assertThat(equalDecision.rollExecuted()).isTrue();
        assertThat(equalRandom.doubleCalls).isOne();
        assertThat(equalDecision.secureWon()).isFalse();
        assertThat(equalDecision.selectedCaptureSide()).isEqualTo(TeamSide.BLUE);
        assertThat(equalDecision.captureSucceeded()).isNull();
        assertThat(equalDecision.actualSteal()).isFalse();

        GameState below = ObjectivePlayerSkillTestSupport.detailedDragonState();
        CountingRandom belowRandom = new CountingRandom(.039999);
        ObjectiveSecureDecisionData belowDecision = resolver.resolve(
                below, ObjectiveType.DRAGON, TeamSide.BLUE, belowRandom);
        assertThat(belowRandom.doubleCalls).isOne();
        assertThat(belowDecision.secureWon()).isTrue();
        assertThat(belowDecision.selectedCaptureSide()).isEqualTo(TeamSide.RED);
        assertThat(belowDecision.captureSucceeded()).isNull();
        assertThat(belowDecision.actualSteal()).isFalse();
        assertThat(below.getObjectiveState().isDragonAlive()).isTrue();
        assertThat(below.getBlueTeamState().getDragons()).isZero();
        assertThat(below.getRedTeamState().getDragons()).isZero();
    }

    @Test
    void missingProfileAndDeadChallengingJunglerConsumeNoRandom() {
        GameState legacy = ObjectiveDecisionTestSupport.dragonState(true);
        CountingRandom legacyRandom = new CountingRandom(0);
        ObjectiveSecureDecisionData legacyDecision = resolver.resolve(
                legacy, ObjectiveType.DRAGON, TeamSide.BLUE, legacyRandom);
        assertThat(legacyDecision.eligible()).isFalse();
        assertThat(legacyDecision.ineligibleReason())
                .isEqualTo(ObjectiveSecureIneligibleReason.WINNING_JUNGLER_PROFILE_UNAVAILABLE);
        assertThat(legacyDecision.selectedCaptureSide()).isEqualTo(TeamSide.BLUE);
        assertThat(legacyRandom.doubleCalls).isZero();

        GameState missingChallenger = ObjectivePlayerSkillTestSupport.dragonState(
                ObjectivePlayerSkillTestSupport.team(
                        "BLUE", TeamSide.BLUE, true, 14, 14, true, 14, 14),
                ObjectivePlayerSkillTestSupport.team(
                        "RED", TeamSide.RED, false, 14, 14, true, 14, 14));
        CountingRandom challengerRandom = new CountingRandom(0);
        ObjectiveSecureDecisionData challengerDecision = resolver.resolve(
                missingChallenger, ObjectiveType.DRAGON, TeamSide.BLUE, challengerRandom);
        assertThat(challengerDecision.eligible()).isFalse();
        assertThat(challengerDecision.ineligibleReason())
                .isEqualTo(ObjectiveSecureIneligibleReason.CHALLENGING_JUNGLER_PROFILE_UNAVAILABLE);
        assertThat(challengerRandom.doubleCalls).isZero();

        GameState dead = ObjectivePlayerSkillTestSupport.detailedDragonState();
        dead.getRedTeamState().playerAt(Position.JUNGLE)
                .markDead(dead.getCurrentTimeSeconds(), 100);
        int blueGold = dead.getBlueTeamState().getGold();
        int redGold = dead.getRedTeamState().getGold();
        CountingRandom deadRandom = new CountingRandom(0);
        ObjectiveSecureDecisionData deadDecision = resolver.resolve(
                dead, ObjectiveType.DRAGON, TeamSide.BLUE, deadRandom);
        assertThat(deadDecision.eligible()).isFalse();
        assertThat(deadDecision.ineligibleReason())
                .isEqualTo(ObjectiveSecureIneligibleReason.CHALLENGING_JUNGLER_UNAVAILABLE);
        assertThat(deadRandom.doubleCalls).isZero();
        assertThat(dead.getBlueTeamState().getGold()).isEqualTo(blueGold);
        assertThat(dead.getRedTeamState().getGold()).isEqualTo(redGold);
        assertThat(dead.getObjectiveState().isDragonAlive()).isTrue();
    }

    @Test
    void unavailableObjectiveConsumesNoRandom() {
        GameState state = ObjectivePlayerSkillTestSupport.detailedDragonState();
        state.getObjectiveState().captureDragon(TeamSide.BLUE, state.getCurrentTimeSeconds());
        CountingRandom random = new CountingRandom(0);

        ObjectiveSecureDecisionData decision = resolver.resolve(
                state, ObjectiveType.DRAGON, TeamSide.BLUE, random);

        assertThat(decision.eligible()).isFalse();
        assertThat(decision.ineligibleReason())
                .isEqualTo(ObjectiveSecureIneligibleReason.OBJECTIVE_UNAVAILABLE);
        assertThat(decision.selectedCaptureSide()).isEqualTo(TeamSide.BLUE);
        assertThat(random.doubleCalls).isZero();
    }

    @Test
    void unavailableWinningOrRoamingChallengingJunglerConsumesNoRandom() {
        GameState winningDead = ObjectivePlayerSkillTestSupport.detailedDragonState();
        winningDead.getBlueTeamState().playerAt(Position.JUNGLE)
                .markDead(winningDead.getCurrentTimeSeconds(), 100);
        CountingRandom deadRandom = new CountingRandom(0);
        ObjectiveSecureDecisionData deadDecision = resolver.resolve(
                winningDead, ObjectiveType.DRAGON, TeamSide.BLUE, deadRandom);
        assertThat(deadDecision.ineligibleReason())
                .isEqualTo(ObjectiveSecureIneligibleReason.WINNING_JUNGLER_UNAVAILABLE);
        assertThat(deadRandom.doubleCalls).isZero();

        GameState challengerRoaming = ObjectivePlayerSkillTestSupport.detailedDragonState();
        challengerRoaming.getRedTeamState().playerAt(Position.JUNGLE).beginRoamActivity(
                Lane.TOP, Lane.MID, challengerRoaming.getCurrentTimeSeconds());
        CountingRandom roamingRandom = new CountingRandom(0);
        ObjectiveSecureDecisionData roamingDecision = resolver.resolve(
                challengerRoaming, ObjectiveType.DRAGON, TeamSide.BLUE, roamingRandom);
        assertThat(roamingDecision.ineligibleReason())
                .isEqualTo(ObjectiveSecureIneligibleReason.CHALLENGING_JUNGLER_UNAVAILABLE);
        assertThat(roamingRandom.doubleCalls).isZero();
    }

    @Test
    void unavailableSupportFallsBackToNeutralSetupWithoutSuppressingSecureRoll() {
        GameState state = ObjectivePlayerSkillTestSupport.dragonState(
                ObjectivePlayerSkillTestSupport.team(
                        "BLUE", TeamSide.BLUE, true, 14, 14, true, 20, 20),
                ObjectivePlayerSkillTestSupport.team(
                        "RED", TeamSide.RED, true, 14, 14, true, 14, 14));
        state.getBlueTeamState().playerAt(Position.SUPPORT).beginRoamActivity(
                Lane.BOT, Lane.MID, state.getCurrentTimeSeconds());
        CountingRandom random = new CountingRandom(.50);

        ObjectiveSecureDecisionData decision = resolver.resolve(
                state, ObjectiveType.DRAGON, TeamSide.BLUE, random);

        assertThat(decision.eligible()).isTrue();
        assertThat(decision.winningSetupControlScore()).isEqualTo(14);
        assertThat(random.doubleCalls).isOne();
    }

    @Test
    void chanceIsClampedAndSameSeedReplaysTheCompleteDecision() {
        assertThat(resolver.stealChance(-100, -100))
                .isEqualTo(ObjectivePlayerSkillRuleConfig.MIN_STEAL_CHANCE);
        assertThat(resolver.stealChance(100, 100))
                .isEqualTo(ObjectivePlayerSkillRuleConfig.MAX_STEAL_CHANCE);

        ObjectiveSecureDecisionData first = resolver.resolve(
                ObjectivePlayerSkillTestSupport.detailedDragonState(),
                ObjectiveType.DRAGON, TeamSide.RED, new Random(9281));
        ObjectiveSecureDecisionData second = resolver.resolve(
                ObjectivePlayerSkillTestSupport.detailedDragonState(),
                ObjectiveType.DRAGON, TeamSide.RED, new Random(9281));
        assertThat(second).isEqualTo(first);
    }

    @Test
    void swappingActualJunglerSecureRatingsChangesFinalChanceMonotonically() {
        GameState strongChallenger = ObjectivePlayerSkillTestSupport.dragonState(
                ObjectivePlayerSkillTestSupport.team(
                        "BLUE", TeamSide.BLUE, true, 14, 5, true, 14, 14),
                ObjectivePlayerSkillTestSupport.team(
                        "RED", TeamSide.RED, true, 14, 20, true, 14, 14));
        GameState strongWinner = ObjectivePlayerSkillTestSupport.dragonState(
                ObjectivePlayerSkillTestSupport.team(
                        "BLUE", TeamSide.BLUE, true, 14, 20, true, 14, 14),
                ObjectivePlayerSkillTestSupport.team(
                        "RED", TeamSide.RED, true, 14, 5, true, 14, 14));

        ObjectiveSecureDecisionData challengerDecision = resolver.resolve(
                strongChallenger, ObjectiveType.DRAGON, TeamSide.BLUE,
                new CountingRandom(.50));
        ObjectiveSecureDecisionData winnerDecision = resolver.resolve(
                strongWinner, ObjectiveType.DRAGON, TeamSide.BLUE,
                new CountingRandom(.50));

        assertThat(challengerDecision.challengingObjectiveSecureScore())
                .isGreaterThan(challengerDecision.winningObjectiveSecureScore());
        assertThat(challengerDecision.finalStealChance())
                .isGreaterThan(winnerDecision.finalStealChance());
    }

    @Test
    void structuredDecisionRejectsContradictoryEligibilityAndCaptureFacts() {
        assertThatThrownBy(() -> new ObjectiveSecureDecisionData(
                true, null, "winner", "challenger", TeamSide.BLUE, TeamSide.RED,
                14, 14, 14, 14, .04, 0, 0, .04,
                true, .01, TeamSide.BLUE, true, null, false))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ObjectiveSecureDecisionData(
                false, ObjectiveSecureIneligibleReason.CHALLENGING_JUNGLER_UNAVAILABLE,
                "winner", "challenger", TeamSide.BLUE, TeamSide.RED,
                0, 0, 0, 0, .04, 0, 0, 0,
                true, .01, TeamSide.BLUE, false, null, false))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ObjectiveSecureDecisionData(
                true, null, "same", "same", TeamSide.BLUE, TeamSide.RED,
                14, 14, 14, 14, .04, 0, 0, .04,
                true, .01, TeamSide.RED, true, null, false))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ObjectiveSecureDecisionData(
                true, null, "winner", "challenger", TeamSide.BLUE, TeamSide.RED,
                Double.NaN, 14, 14, 14, .04, 0, 0, .04,
                true, .01, TeamSide.RED, true, null, false))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static final class CountingRandom extends Random {
        private final double value;
        private int doubleCalls;

        private CountingRandom(double value) { this.value = value; }

        @Override
        public double nextDouble() {
            doubleCalls++;
            return value;
        }
    }
}
