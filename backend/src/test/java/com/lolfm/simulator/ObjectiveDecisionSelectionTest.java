package com.lolfm.simulator;

import static org.assertj.core.api.Assertions.assertThat;

import com.lolfm.domain.ObjectiveDecisionWeightBreakdown;
import java.util.List;
import org.junit.jupiter.api.Test;

class ObjectiveDecisionSelectionTest {
    private final ObjectiveDecisionResolver resolver = new ObjectiveDecisionResolver();

    @Test
    void sideEdgesAreSymmetricAndElderHasNoPriorityEdge() {
        GameState state = ObjectiveDecisionTestSupport.dragonState(true);
        state.getBlueTeamState().addGold(5_000);
        state.getRedTeamState().getPlayers().getFirst().markDead(340, 100);

        ObjectiveDecisionContext blue = resolver.buildContext(state, ObjectiveType.DRAGON, TeamSide.BLUE, 60);
        ObjectiveDecisionContext red = resolver.buildContext(state, ObjectiveType.DRAGON, TeamSide.RED, 60);
        ObjectiveDecisionWeightBreakdown blueTake = action(resolver.initiativeWeights(blue), ObjectiveDecisionAction.TAKE);
        ObjectiveDecisionWeightBreakdown redTake = action(resolver.initiativeWeights(red), ObjectiveDecisionAction.TAKE);
        ObjectiveDecisionContext elder = resolver.buildContext(state, ObjectiveType.ELDER, TeamSide.BLUE, 60);
        ObjectiveDecisionWeightBreakdown elderTake = action(resolver.initiativeWeights(elder), ObjectiveDecisionAction.TAKE);

        assertThat(blueTake.priorityEdge()).isEqualTo(-redTake.priorityEdge());
        assertThat(blueTake.goldEdge()).isEqualTo(-redTake.goldEdge());
        assertThat(blueTake.aliveEdge()).isEqualTo(-redTake.aliveEdge());
        assertThat(elderTake.priorityEdge()).isZero();
        assertThat(elderTake.urgencyContribution()).isEqualTo(ObjectiveDecisionRuleConfig.ELDER_URGENCY_BONUS);
    }

    @Test
    void dragonUrgencyOnlyAppliesToTakeAndContest() {
        GameState state = ObjectiveDecisionTestSupport.dragonState(true);
        for (int i = 0; i < 3; i++) state.getBlueTeamState().addDragon();
        ObjectiveDecisionContext context = resolver.buildContext(state, ObjectiveType.DRAGON, TeamSide.BLUE, 0);

        List<ObjectiveDecisionWeightBreakdown> initiative = resolver.initiativeWeights(context);
        List<ObjectiveDecisionWeightBreakdown> responder = resolver.responderWeights(state, context);

        assertThat(action(initiative, ObjectiveDecisionAction.TAKE).urgencyContribution())
                .isEqualTo(ObjectiveDecisionRuleConfig.OWN_SOUL_POINT_URGENCY_BONUS);
        assertThat(action(initiative, ObjectiveDecisionAction.RESET).urgencyContribution()).isZero();
        assertThat(action(responder, ObjectiveDecisionAction.GIVE).urgencyContribution()).isZero();
        assertThat(action(responder, ObjectiveDecisionAction.TRADE_STRUCTURE).urgencyContribution()).isZero();
    }

    @Test
    void staleObjectiveExcludesTakeButResetRemainsEligible() {
        GameState state = ObjectiveDecisionTestSupport.dragonState(true);
        state.getObjectiveState().captureDragon(TeamSide.BLUE, 340);
        ObjectiveDecisionContext context = resolver.buildContext(state, ObjectiveType.DRAGON, TeamSide.BLUE, 0);

        assertThat(action(resolver.initiativeWeights(context), ObjectiveDecisionAction.TAKE).eligible()).isFalse();
        assertThat(action(resolver.initiativeWeights(context), ObjectiveDecisionAction.RESET).eligible()).isTrue();
    }

    @Test
    void weightsAreClampedToConfiguredBounds() {
        GameState state = ObjectiveDecisionTestSupport.dragonState(true);
        ObjectiveDecisionContext context = resolver.buildContext(state, ObjectiveType.DRAGON, TeamSide.BLUE, 10_000);
        assertThat(resolver.initiativeWeights(context))
                .allSatisfy(weight -> assertThat(weight.finalWeight()).isBetween(
                        ObjectiveDecisionRuleConfig.MIN_DECISION_WEIGHT,
                        ObjectiveDecisionRuleConfig.MAX_DECISION_WEIGHT));
    }

    private ObjectiveDecisionWeightBreakdown action(List<ObjectiveDecisionWeightBreakdown> weights,
                                                     ObjectiveDecisionAction action) {
        return weights.stream().filter(weight -> weight.action() == action).findFirst().orElseThrow();
    }
}
