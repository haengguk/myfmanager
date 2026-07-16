package com.lolfm.simulator;

import static org.assertj.core.api.Assertions.assertThat;

import com.lolfm.domain.MatchEvent;
import com.lolfm.domain.ObjectiveDecisionWeightBreakdown;
import com.lolfm.domain.Position;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ObjectiveDecisionTradeTest {
    private final ObjectiveDecisionResolver resolver = new ObjectiveDecisionResolver();

    @Test
    void successfulTradeCapturesObjectiveAndDestroysExactlyOneTower() {
        GameState state = ObjectiveDecisionTestSupport.dragonState(true);
        List<MatchEvent> events = new ArrayList<>();
        ObjectiveDecisionTestSupport.SequenceRandom random = new ObjectiveDecisionTestSupport.SequenceRandom(0, .999, 0);

        MatchEvent capture = resolver.resolve(state, ObjectiveType.DRAGON, TeamSide.BLUE, 0, random,
                new ObjectiveResolver(), new StructureResolver(), events, null).orElseThrow();

        assertThat(capture.getObjectiveDecision().result()).isEqualTo(ObjectiveDecisionResult.TRADE_SUCCEEDED);
        assertThat(capture.getObjectiveDecision().tradeTargetLane()).isEqualTo(Lane.TOP);
        assertThat(state.getMapState().getLaneState(TeamSide.BLUE, Lane.TOP).destroyedTowerCount()).isOne();
        assertThat(state.getMapState().getLaneState(TeamSide.BLUE, Lane.MID).destroyedTowerCount()).isZero();
        assertThat(state.wasStructureActionPerformedThisTick(TeamSide.RED)).isTrue();
        assertThat(events.stream().filter(event -> event.getStructureActionSource() == StructureActionSource.OBJECTIVE_TRADE)).hasSize(1);
    }

    @Test
    void failedTradeDoesNotMutateStructuresOrRewards() {
        GameState state = ObjectiveDecisionTestSupport.dragonState(true);
        int towers = state.getRedTeamState().getTowersDestroyed();
        ObjectiveDecisionTestSupport.SequenceRandom random = new ObjectiveDecisionTestSupport.SequenceRandom(0, .999, .999);

        MatchEvent capture = resolver.resolve(state, ObjectiveType.DRAGON, TeamSide.BLUE, 0, random,
                new ObjectiveResolver(), new StructureResolver(), new ArrayList<>(), null).orElseThrow();

        assertThat(capture.getObjectiveDecision().result()).isEqualTo(ObjectiveDecisionResult.TRADE_FAILED);
        assertThat(state.getMapState().getLaneState(TeamSide.BLUE, Lane.TOP).destroyedTowerCount()).isZero();
        assertThat(state.getRedTeamState().getTowersDestroyed()).isEqualTo(towers);
        assertThat(state.wasStructureActionAttemptedThisTick(TeamSide.RED)).isTrue();
        assertThat(state.wasStructureMutationPerformedThisTick(TeamSide.RED)).isFalse();
    }

    @Test
    void targetOrderIsTopForDragonAndBotForBaronAndSkipsCombatPusher() {
        GameState dragon = ObjectiveDecisionTestSupport.dragonState(true);
        ObjectiveDecisionContext dragonContext = resolver.buildContext(dragon, ObjectiveType.DRAGON, TeamSide.BLUE, 0);
        assertThat(dragonContext.tradeTarget(TeamSide.RED).lane()).isEqualTo(Lane.TOP);

        GameState baron = new GameState(ObjectiveDecisionTestSupport.team("BLUE"), ObjectiveDecisionTestSupport.team("RED"));
        baron.advanceTimeSeconds(1_200);
        new ObjectiveResolver().updateSpawnState(baron);
        ObjectiveDecisionContext baronContext = resolver.buildContext(baron, ObjectiveType.BARON, TeamSide.BLUE, 0);
        assertThat(baronContext.tradeTarget(TeamSide.RED).lane()).isEqualTo(Lane.BOT);

        baron.markMajorCombatParticipant(baron.getRedTeamState().playerAt(Position.ADC));
        ObjectiveDecisionContext skipped = resolver.buildContext(baron, ObjectiveType.BARON, TeamSide.BLUE, 0);
        assertThat(skipped.tradeTarget(TeamSide.RED).lane()).isEqualTo(Lane.MID);
    }

    @Test
    void structureRegistryRemovesTradeFromEligibleCandidates() {
        GameState state = ObjectiveDecisionTestSupport.dragonState(true);
        state.markStructureActionPerformed(TeamSide.RED);
        ObjectiveDecisionContext context = resolver.buildContext(state, ObjectiveType.DRAGON, TeamSide.BLUE, 0);
        ObjectiveDecisionWeightBreakdown trade = resolver.responderWeights(state, context).stream()
                .filter(weight -> weight.action() == ObjectiveDecisionAction.TRADE_STRUCTURE).findFirst().orElseThrow();
        assertThat(trade.eligible()).isFalse();
        assertThat(trade.reason()).isEqualTo(ObjectiveDecisionIneligibleReason.STRUCTURE_ACTION_ALREADY_USED);
    }
}
