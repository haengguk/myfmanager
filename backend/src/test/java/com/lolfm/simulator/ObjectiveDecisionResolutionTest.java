package com.lolfm.simulator;

import static org.assertj.core.api.Assertions.assertThat;

import com.lolfm.domain.CombatSource;
import com.lolfm.domain.MatchEvent;
import com.lolfm.domain.MatchEventType;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ObjectiveDecisionResolutionTest {
    private final ObjectiveDecisionResolver resolver = new ObjectiveDecisionResolver();
    private final ObjectiveResolver objectives = new ObjectiveResolver();
    private final StructureResolver structures = new StructureResolver();

    @Test
    void resetConsumesOnlyInitiativeRollAndCreatesNoGameplayAction() {
        GameState state = ObjectiveDecisionTestSupport.dragonState(true);
        state.getObjectiveState().markDragonAttempted(340);
        int nextDue = state.getObjectiveState().getNextDragonAttemptSeconds();
        ObjectiveDecisionTestSupport.SequenceRandom random = new ObjectiveDecisionTestSupport.SequenceRandom(.999);
        List<MatchEvent> events = new ArrayList<>();

        assertThat(resolve(state, random, events)).isEmpty();
        assertThat(events).isEmpty();
        assertThat(state.getObjectiveState().isDragonAlive()).isTrue();
        assertThat(state.wasMajorCombatAttemptedThisTick()).isFalse();
        assertThat(state.wasAnyStructureActionPerformedThisTick()).isFalse();
        assertThat(state.getObjectiveState().getNextDragonAttemptSeconds()).isEqualTo(nextDue).isGreaterThan(340);
        assertThat(random.doubleCalls()).isEqualTo(1);
        assertThat(state.getObjectiveDecisionState().getHistory().getFirst().result())
                .isEqualTo(ObjectiveDecisionResult.INITIATOR_RESET);

        resolve(state, new ObjectiveDecisionTestSupport.SequenceRandom(0), events);
        assertThat(state.getObjectiveDecisionState().getHistory()).hasSize(1);
    }

    @Test
    void giveUsesExistingUncontestedCaptureAndNoMajorCombat() {
        GameState state = ObjectiveDecisionTestSupport.dragonState(true);
        int initialGold = state.getBlueTeamState().getGold();
        ObjectiveDecisionTestSupport.SequenceRandom random = new ObjectiveDecisionTestSupport.SequenceRandom(0, .50);
        List<MatchEvent> events = new ArrayList<>();

        MatchEvent event = resolve(state, random, events).orElseThrow();

        assertThat(event.getType()).isEqualTo(MatchEventType.DRAGON);
        assertThat(event.getObjectiveDecision().result()).isEqualTo(ObjectiveDecisionResult.UNCONTESTED_CAPTURE);
        assertThat(state.getBlueTeamState().getDragons()).isOne();
        assertThat(state.getBlueTeamState().getGold()).isGreaterThan(initialGold);
        assertThat(state.wasMajorCombatAttemptedThisTick()).isFalse();
        assertThat(events.stream().filter(candidate -> candidate.getType() == MatchEventType.DRAGON)).hasSize(1);
    }

    @Test
    void contestConsumesMajorCombatAndUsesObjectiveFightKillSource() {
        GameState state = ObjectiveDecisionTestSupport.dragonState(true);
        ObjectiveDecisionTestSupport.SequenceRandom random = new ObjectiveDecisionTestSupport.SequenceRandom(0, 0, 0, 0, 0, 0);
        List<MatchEvent> events = new ArrayList<>();

        MatchEvent capture = resolve(state, random, events).orElseThrow();

        assertThat(capture.getObjectiveDecision().result()).isEqualTo(ObjectiveDecisionResult.CONTEST_FIGHT);
        assertThat(capture.getObjectiveDecision().captureSide()).isEqualTo(capture.getObjectiveDecision().fightWinner());
        assertThat(state.wasMajorCombatAttemptedThisTick()).isTrue();
        assertThat(events.stream().filter(event -> event.getType() == MatchEventType.KILL)
                .map(MatchEvent::getCombatSource)).containsOnly(CombatSource.OBJECTIVE_FIGHT);
        assertThat(events.stream().filter(event -> event.getType() == MatchEventType.DRAGON)).hasSize(1);
    }

    private java.util.Optional<MatchEvent> resolve(GameState state,
                                                    ObjectiveDecisionTestSupport.SequenceRandom random,
                                                    List<MatchEvent> events) {
        return resolver.resolve(state, ObjectiveType.DRAGON, TeamSide.BLUE, 0, random,
                objectives, structures, events, null);
    }
}
