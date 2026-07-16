package com.lolfm.simulator;

import static org.assertj.core.api.Assertions.assertThat;

import com.lolfm.domain.MatchEvent;
import com.lolfm.domain.MatchSnapshot;
import com.lolfm.domain.ObjectiveDecisionSnapshot;
import java.util.ArrayList;
import org.junit.jupiter.api.Test;

class ObjectiveDecisionSnapshotTest {
    @Test
    void snapshotIndexesLatestDecisionAndPastSnapshotDoesNotChange() {
        GameState state = ObjectiveDecisionTestSupport.dragonState(true);
        ObjectiveDecisionResolver resolver = new ObjectiveDecisionResolver();
        resolver.resolve(state, ObjectiveType.DRAGON, TeamSide.BLUE, 0,
                new ObjectiveDecisionTestSupport.SequenceRandom(.999), new ObjectiveResolver(),
                new StructureResolver(), new ArrayList<MatchEvent>(), null);

        MatchSnapshot first = new SnapshotFactory().create(state);
        ObjectiveDecisionSnapshot firstDecision = first.getObjectiveDecision();
        assertThat(firstDecision.enabled()).isTrue();
        assertThat(firstDecision.latestOverall().decisionSequence()).isOne();
        assertThat(firstDecision.latestDragon()).isEqualTo(firstDecision.latestOverall());
        assertThat(firstDecision.latestBlue()).isEqualTo(firstDecision.latestOverall());
        assertThat(firstDecision.latestBaron()).isNull();

        state.advanceTimeSeconds(60);
        resolver.resolve(state, ObjectiveType.DRAGON, TeamSide.RED, 0,
                new ObjectiveDecisionTestSupport.SequenceRandom(.999), new ObjectiveResolver(),
                new StructureResolver(), new ArrayList<MatchEvent>(), null);
        MatchSnapshot second = new SnapshotFactory().create(state);

        assertThat(second.getObjectiveDecision().latestOverall().decisionSequence()).isEqualTo(2);
        assertThat(firstDecision.latestOverall().decisionSequence()).isOne();
    }

    @Test
    void snapshotReadDoesNotEvaluateOrConsumeDecisionState() {
        GameState state = ObjectiveDecisionTestSupport.dragonState(true);
        int beforeSequence = state.getObjectiveDecisionState().getDecisionSequence();
        int beforeDue = state.getObjectiveState().getNextDragonAttemptSeconds();

        MatchSnapshot snapshot = new SnapshotFactory().create(state);

        assertThat(snapshot.getObjectiveDecision().latestOverall()).isNull();
        assertThat(state.getObjectiveDecisionState().getDecisionSequence()).isEqualTo(beforeSequence);
        assertThat(state.getObjectiveState().getNextDragonAttemptSeconds()).isEqualTo(beforeDue);
        assertThat(state.getObjectiveState().isDragonAlive()).isTrue();
    }

    @Test
    void disabledSnapshotIsAdditiveAndNullable() {
        MatchSnapshot snapshot = new SnapshotFactory().create(ObjectiveDecisionTestSupport.dragonState(false));
        assertThat(snapshot.getObjectivePriority()).isNotNull();
        assertThat(snapshot.getLanePhase()).isNotNull();
        assertThat(snapshot.getMidGameMacro()).isNotNull();
        assertThat(snapshot.getObjectiveDecision().enabled()).isFalse();
        assertThat(snapshot.getObjectiveDecision().latestOverall()).isNull();
    }
}
