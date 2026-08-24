package com.lolfm.simulator;

import static org.assertj.core.api.Assertions.assertThat;

import com.lolfm.domain.ObjectiveDecisionWeightBreakdown;
import com.lolfm.domain.Position;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

class ObjectivePlayerSkillDecisionTest {
    private final ObjectiveDecisionResolver resolver = new ObjectiveDecisionResolver();

    @Test
    void highDecisionSkillFavorsTakeWhenContextIsFavorableAndResetWhenUnfavorable() {
        GameState favorable = stateWithBlueDecision(20);
        favorable.getBlueTeamState().addGold(5_000);
        ObjectiveDecisionContext favorableContext = resolver.buildContext(
                favorable, ObjectiveType.DRAGON, TeamSide.BLUE, 100);
        ObjectiveDecisionWeightBreakdown favorableTake = action(
                resolver.initiativeWeights(favorable, favorableContext), ObjectiveDecisionAction.TAKE);
        ObjectiveDecisionWeightBreakdown favorableReset = action(
                resolver.initiativeWeights(favorable, favorableContext), ObjectiveDecisionAction.RESET);

        assertThat(favorableTake.objectiveDecisionScore()).isGreaterThan(14);
        assertThat(favorableTake.objectiveFavorability()).isPositive();
        assertThat(favorableTake.objectiveDecisionContribution()).isPositive();
        assertThat(favorableReset.objectiveDecisionContribution())
                .isEqualTo(-favorableTake.objectiveDecisionContribution());

        GameState unfavorable = stateWithBlueDecision(20);
        unfavorable.getRedTeamState().addGold(5_000);
        ObjectiveDecisionContext unfavorableContext = resolver.buildContext(
                unfavorable, ObjectiveType.DRAGON, TeamSide.BLUE, -100);
        ObjectiveDecisionWeightBreakdown unfavorableTake = action(
                resolver.initiativeWeights(unfavorable, unfavorableContext), ObjectiveDecisionAction.TAKE);
        ObjectiveDecisionWeightBreakdown unfavorableReset = action(
                resolver.initiativeWeights(unfavorable, unfavorableContext), ObjectiveDecisionAction.RESET);

        assertThat(unfavorableTake.objectiveFavorability()).isNegative();
        assertThat(unfavorableTake.objectiveDecisionContribution()).isNegative();
        assertThat(unfavorableReset.objectiveDecisionContribution()).isPositive();
    }

    @Test
    void lowDecisionSkillMovesInTheOppositeDirectionAndNeutralContextAddsNothing() {
        GameState low = stateWithBlueDecision(5);
        low.getBlueTeamState().addGold(5_000);
        ObjectiveDecisionContext favorable = resolver.buildContext(
                low, ObjectiveType.DRAGON, TeamSide.BLUE, 100);
        assertThat(action(resolver.initiativeWeights(low, favorable), ObjectiveDecisionAction.TAKE)
                .objectiveDecisionContribution()).isNegative();

        GameState neutral = stateWithBlueDecision(20);
        ObjectiveDecisionContext neutralContext = resolver.buildContext(
                neutral, ObjectiveType.DRAGON, TeamSide.BLUE, 0);
        assertThat(action(resolver.initiativeWeights(neutral, neutralContext), ObjectiveDecisionAction.TAKE)
                .objectiveDecisionContribution()).isZero();
    }

    @Test
    void responderUsesItsOwnDecisionProfileAndPerspective() {
        TeamState blue = ObjectivePlayerSkillTestSupport.team(
                "BLUE", TeamSide.BLUE, true, 14, 14, true, 14, 14);
        TeamState red = ObjectivePlayerSkillTestSupport.team(
                "RED", TeamSide.RED, true, 20, 14, true, 14, 14);
        GameState state = ObjectivePlayerSkillTestSupport.dragonState(blue, red);
        state.getRedTeamState().addGold(5_000);
        ObjectiveDecisionContext context = resolver.buildContext(
                state, ObjectiveType.DRAGON, TeamSide.BLUE, -100);

        ObjectiveDecisionWeightBreakdown contest = action(
                resolver.responderWeights(state, context), ObjectiveDecisionAction.CONTEST);
        ObjectiveDecisionWeightBreakdown give = action(
                resolver.responderWeights(state, context), ObjectiveDecisionAction.GIVE);
        ObjectiveDecisionWeightBreakdown trade = action(
                resolver.responderWeights(state, context), ObjectiveDecisionAction.TRADE_STRUCTURE);

        assertThat(contest.objectiveFavorability()).isPositive();
        assertThat(contest.objectiveDecisionContribution()).isPositive();
        assertThat(give.objectiveDecisionContribution()).isNegative();
        assertThat(trade.objectiveDecisionContribution()).isZero();
    }

    @Test
    void legacyProfilePreservesNeutralStructuredContribution() {
        GameState state = ObjectiveDecisionTestSupport.dragonState(true);
        state.getBlueTeamState().addGold(5_000);
        ObjectiveDecisionContext context = resolver.buildContext(
                state, ObjectiveType.DRAGON, TeamSide.BLUE, 100);

        ObjectiveDecisionWeightBreakdown take = action(
                resolver.initiativeWeights(state, context), ObjectiveDecisionAction.TAKE);

        assertThat(take.objectiveDecisionScore()).isEqualTo(14);
        assertThat(take.objectiveFavorability()).isPositive();
        assertThat(take.objectiveDecisionContribution()).isZero();
    }

    @Test
    void unavailableJunglerCannotSteerObjectiveDecisionWeights() {
        GameState dead = stateWithBlueDecision(20);
        dead.getBlueTeamState().addGold(5_000);
        dead.getBlueTeamState().playerAt(Position.JUNGLE)
                .markDead(dead.getCurrentTimeSeconds(), 100);
        ObjectiveDecisionContext deadContext = resolver.buildContext(
                dead, ObjectiveType.DRAGON, TeamSide.BLUE, 100);
        ObjectiveDecisionWeightBreakdown deadTake = action(
                resolver.initiativeWeights(dead, deadContext), ObjectiveDecisionAction.TAKE);
        assertThat(deadTake.objectiveDecisionScore()).isEqualTo(14);
        assertThat(deadTake.objectiveDecisionContribution()).isZero();

        GameState roaming = stateWithBlueDecision(20);
        roaming.getBlueTeamState().addGold(5_000);
        roaming.getBlueTeamState().playerAt(Position.JUNGLE).beginRoamActivity(
                Lane.TOP, Lane.MID, roaming.getCurrentTimeSeconds());
        ObjectiveDecisionContext roamingContext = resolver.buildContext(
                roaming, ObjectiveType.DRAGON, TeamSide.BLUE, 100);
        ObjectiveDecisionWeightBreakdown roamingTake = action(
                resolver.initiativeWeights(roaming, roamingContext), ObjectiveDecisionAction.TAKE);
        assertThat(roamingTake.objectiveDecisionScore()).isEqualTo(14);
        assertThat(roamingTake.objectiveDecisionContribution()).isZero();
    }

    @Test
    void sameRollTurnsIntoTakeForHighDecisionAndResetForLowDecision() {
        GameState high = stateWithBlueDecision(20);
        GameState low = stateWithBlueDecision(5);
        high.getBlueTeamState().addGold(5_000);
        low.getBlueTeamState().addGold(5_000);
        ObjectiveDecisionContext highContext = resolver.buildContext(
                high, ObjectiveType.DRAGON, TeamSide.BLUE, 100);
        ObjectiveDecisionContext lowContext = resolver.buildContext(
                low, ObjectiveType.DRAGON, TeamSide.BLUE, 100);
        double highTake = selectionProbability(
                resolver.initiativeWeights(high, highContext), ObjectiveDecisionAction.TAKE);
        double lowTake = selectionProbability(
                resolver.initiativeWeights(low, lowContext), ObjectiveDecisionAction.TAKE);
        assertThat(highTake).isGreaterThan(lowTake);
        double sharedRoll = (highTake + lowTake) / 2.0;

        resolver.resolve(high, ObjectiveType.DRAGON, TeamSide.BLUE, 100,
                new ControlledRandom(sharedRoll, .999, .999), new ObjectiveResolver(),
                new StructureResolver(), new ArrayList<>(), null);
        resolver.resolve(low, ObjectiveType.DRAGON, TeamSide.BLUE, 100,
                new ControlledRandom(sharedRoll), new ObjectiveResolver(),
                new StructureResolver(), new ArrayList<>(), null);

        assertThat(high.getObjectiveDecisionState().getHistory().getFirst().initiativeAction())
                .isEqualTo(ObjectiveDecisionAction.TAKE);
        assertThat(low.getObjectiveDecisionState().getHistory().getFirst().initiativeAction())
                .isEqualTo(ObjectiveDecisionAction.RESET);
    }

    @Test
    void sameResponderRollTurnsIntoContestForHighDecisionAndGiveForLowDecision() {
        GameState high = stateWithRedDecision(20);
        GameState low = stateWithRedDecision(5);
        high.getRedTeamState().addGold(5_000);
        low.getRedTeamState().addGold(5_000);
        ObjectiveDecisionContext highContext = resolver.buildContext(
                high, ObjectiveType.DRAGON, TeamSide.BLUE, -100);
        ObjectiveDecisionContext lowContext = resolver.buildContext(
                low, ObjectiveType.DRAGON, TeamSide.BLUE, -100);
        List<ObjectiveDecisionWeightBreakdown> highWeights = resolver.responderWeights(
                high, highContext);
        List<ObjectiveDecisionWeightBreakdown> lowWeights = resolver.responderWeights(
                low, lowContext);
        double highContest = selectionProbability(highWeights, ObjectiveDecisionAction.CONTEST);
        double lowContest = selectionProbability(lowWeights, ObjectiveDecisionAction.CONTEST);
        double lowGive = selectionProbability(lowWeights, ObjectiveDecisionAction.GIVE);
        assertThat(highContest).isGreaterThan(lowContest);
        double sharedRoll = (highContest + lowContest) / 2.0;
        assertThat(sharedRoll).isLessThan(lowContest + lowGive);

        resolver.resolve(high, ObjectiveType.DRAGON, TeamSide.BLUE, -100,
                new ControlledRandom(0, sharedRoll, .5, .5, .5, .5),
                new ObjectiveResolver(), new StructureResolver(), new ArrayList<>(), null);
        resolver.resolve(low, ObjectiveType.DRAGON, TeamSide.BLUE, -100,
                new ControlledRandom(0, sharedRoll),
                new ObjectiveResolver(), new StructureResolver(), new ArrayList<>(), null);

        assertThat(high.getObjectiveDecisionState().getHistory().getFirst().responderAction())
                .isEqualTo(ObjectiveDecisionAction.CONTEST);
        assertThat(low.getObjectiveDecisionState().getHistory().getFirst().responderAction())
                .isEqualTo(ObjectiveDecisionAction.GIVE);
    }

    private GameState stateWithBlueDecision(int decision) {
        TeamState blue = ObjectivePlayerSkillTestSupport.team(
                "BLUE", TeamSide.BLUE, true, decision, 14, true, 14, 14);
        TeamState red = ObjectivePlayerSkillTestSupport.team(
                "RED", TeamSide.RED, true, 14, 14, true, 14, 14);
        return ObjectivePlayerSkillTestSupport.dragonState(blue, red);
    }

    private GameState stateWithRedDecision(int decision) {
        TeamState blue = ObjectivePlayerSkillTestSupport.team(
                "BLUE", TeamSide.BLUE, true, 14, 14, true, 14, 14);
        TeamState red = ObjectivePlayerSkillTestSupport.team(
                "RED", TeamSide.RED, true, decision, 14, true, 14, 14);
        return ObjectivePlayerSkillTestSupport.dragonState(blue, red);
    }

    private double selectionProbability(
            List<ObjectiveDecisionWeightBreakdown> weights, ObjectiveDecisionAction action) {
        double total = weights.stream().filter(ObjectiveDecisionWeightBreakdown::eligible)
                .mapToDouble(ObjectiveDecisionWeightBreakdown::finalWeight).sum();
        return action(weights, action).finalWeight() / total;
    }

    private ObjectiveDecisionWeightBreakdown action(
            List<ObjectiveDecisionWeightBreakdown> weights, ObjectiveDecisionAction action) {
        return weights.stream().filter(weight -> weight.action() == action).findFirst().orElseThrow();
    }

    private static final class ControlledRandom extends Random {
        private final double[] values;
        private int index;
        private ControlledRandom(double... values) { this.values = values; }
        @Override public double nextDouble() {
            return values[Math.min(index++, values.length - 1)];
        }
        @Override public int nextInt(int bound) { return 0; }
        @Override public boolean nextBoolean() { return false; }
    }
}
