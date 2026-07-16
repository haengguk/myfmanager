package com.lolfm.simulator;

import static org.assertj.core.api.Assertions.assertThat;

import com.lolfm.domain.MatchEvent;
import com.lolfm.domain.MatchTimeline;
import com.lolfm.factory.DummyDataFactory;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ObjectiveDecisionIntegrationTest {
    @Test
    void productionDefaultIsOnAndExplicitOffUsesLegacyGeneralPath() {
        assertThat(SimulationOptions.productionDefaults().objectiveDecisionEnabled()).isTrue();
        assertThat(SimulationOptions.productionDefaults().withObjectiveDecisionEnabled(false).objectiveDecisionEnabled()).isFalse();
        GameState state = ObjectiveDecisionTestSupport.dragonState(false);
        ObjectiveDecisionTestSupport.SequenceRandom random = new ObjectiveDecisionTestSupport.SequenceRandom(0, 0);
        List<MatchEvent> events = new ArrayList<>();

        MatchEvent capture = new ObjectiveAttemptResolver().maybeAttemptObjective(state, random,
                new ObjectiveResolver(), new StructureResolver(), events).orElseThrow();

        assertThat(capture.getObjectiveDecision()).isNull();
        assertThat(state.getObjectiveDecisionState().getHistory()).isEmpty();
        assertThat(events).isEmpty();
        assertThat(random.doubleCalls()).isEqualTo(2);
    }

    @Test
    void failedLegacyAttemptDoesNotEnterDecisionLayer() {
        GameState state = ObjectiveDecisionTestSupport.dragonState(true);
        ObjectiveDecisionTestSupport.SequenceRandom random = new ObjectiveDecisionTestSupport.SequenceRandom(.999);
        assertThat(new ObjectiveAttemptResolver().maybeAttemptObjective(state, random,
                new ObjectiveResolver(), new StructureResolver(), new ArrayList<>())).isEmpty();
        assertThat(state.getObjectiveDecisionState().getHistory()).isEmpty();
        assertThat(random.doubleCalls()).isOne();
    }

    @Test
    void postFightCaptureBypassesDecisionStateAndRandom() {
        GameState state = ObjectiveDecisionTestSupport.dragonState(true);
        ObjectiveDecisionTestSupport.SequenceRandom random = new ObjectiveDecisionTestSupport.SequenceRandom(0);
        MatchEvent event = new PostFightResolver().resolve(state,
                new TeamfightOutcome(TeamSide.BLUE, FightGrade.BIG_WIN, 3, 0, 340, List.of()),
                random, new ObjectiveResolver()).orElseThrow();

        assertThat(event.getObjectiveDecision()).isNull();
        assertThat(state.getObjectiveDecisionState().getHistory()).isEmpty();
        assertThat(random.doubleCalls()).isOne();
        assertThat(state.wasAnyStructureActionPerformedThisTick()).isFalse();
    }

    @Test
    void sameSeedAndOptionsReproduceCompleteDecisionTimeline() {
        DummyDataFactory factory = new DummyDataFactory();
        MatchSimulator simulator = simulator(SimulationOptions.productionDefaults());
        MatchTimeline first = simulator.simulate(factory.createBlueTeam(), factory.createRedTeam(), 91234L);
        MatchTimeline second = simulator.simulate(factory.createBlueTeam(), factory.createRedTeam(), 91234L);

        assertThat(signatures(first)).isEqualTo(signatures(second));
        assertThat(first.getSnapshots().size()).isEqualTo(second.getSnapshots().size());
    }

    private List<String> signatures(MatchTimeline timeline) {
        return timeline.getEvents().stream().map(event -> event.getTimeSeconds() + "|" + event.getType()
                + "|" + event.getCombatSource() + "|" + event.getObjectiveDecision()).toList();
    }

    private MatchSimulator simulator(SimulationOptions options) {
        return new MatchSimulator(new TeamfightResolver(), new EndGameEvaluator(), new SnapshotFactory(),
                new ObjectiveResolver(), new PostFightResolver(), new ObjectiveAttemptResolver(),
                new StructureResolver(), new PushResolver(), options);
    }
}
