package com.lolfm.simulator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class ObjectiveDecisionStateTest {
    @Test
    void newMatchOwnsFreshDecisionState() {
        GameState first = ObjectiveDecisionTestSupport.dragonState(true);
        GameState second = ObjectiveDecisionTestSupport.dragonState(true);

        assertThat(first.getObjectiveDecisionState().getDecisionSequence()).isZero();
        assertThat(first.getObjectiveDecisionState().getHistory()).isEmpty();
        assertThat(first.getObjectiveDecisionState()).isNotSameAs(second.getObjectiveDecisionState());
    }

    @Test
    void structuredDecisionKeyIsReservedAtMostOnce() {
        ObjectiveDecisionState state = ObjectiveDecisionTestSupport.dragonState(true).getObjectiveDecisionState();
        ObjectiveDecisionKey key = new ObjectiveDecisionKey(ObjectiveType.DRAGON, 300, 340, TeamSide.BLUE);

        assertThat(state.reserve(key)).isTrue();
        assertThat(state.reserve(key)).isFalse();
        assertThat(state.getResolvedDecisionKeys()).containsExactly(key);
        assertThatThrownBy(() -> state.getResolvedDecisionKeys().clear())
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void mutableSpawnStateCannotCreateASecondIdentityForTheSameEvaluation() {
        ObjectiveDecisionState state = ObjectiveDecisionTestSupport.dragonState(true)
                .getObjectiveDecisionState();
        ObjectiveDecisionKey beforeCapture = new ObjectiveDecisionKey(
                ObjectiveType.DRAGON, 300, 340, TeamSide.BLUE);
        ObjectiveDecisionKey afterCapture = new ObjectiveDecisionKey(
                ObjectiveType.DRAGON, -1, 340, TeamSide.BLUE);

        assertThat(state.reserve(beforeCapture)).isTrue();
        assertThat(state.reserve(afterCapture)).isFalse();
        assertThat(state.getResolvedDecisionKeys()).containsExactly(beforeCapture);
    }

    @Test
    void disabledStateDoesNotReserveOrExposeRecords() {
        ObjectiveDecisionState state = ObjectiveDecisionTestSupport.dragonState(false).getObjectiveDecisionState();
        assertThat(state.isEnabled()).isFalse();
        assertThat(state.reserve(new ObjectiveDecisionKey(ObjectiveType.DRAGON, 300, 340, TeamSide.BLUE))).isFalse();
        assertThat(state.snapshot().enabled()).isFalse();
        assertThat(state.snapshot().latestOverall()).isNull();
    }
}
