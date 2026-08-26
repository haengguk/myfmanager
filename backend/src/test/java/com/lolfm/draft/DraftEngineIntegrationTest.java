package com.lolfm.draft;

import static com.lolfm.testing.CompleteTimelineAssertions.assertCompleteTimelineEquals;
import static org.assertj.core.api.Assertions.assertThat;

import com.lolfm.domain.MatchTimeline;
import com.lolfm.factory.DummyDataFactory;
import com.lolfm.simulator.EndGameEvaluator;
import com.lolfm.simulator.MatchSimulator;
import com.lolfm.simulator.ObjectiveAttemptResolver;
import com.lolfm.simulator.ObjectiveResolver;
import com.lolfm.simulator.PostFightResolver;
import com.lolfm.simulator.PushResolver;
import com.lolfm.simulator.SnapshotFactory;
import com.lolfm.simulator.StructureResolver;
import com.lolfm.simulator.TeamfightResolver;
import java.util.HashSet;
import org.junit.jupiter.api.Test;

class DraftEngineIntegrationTest {
    private final DraftEngine engine = new DraftEngine(DraftTestSupport.RESOURCES);

    @Test
    void fullDraftIsLegalExplainedAndExactlyDeterministic() {
        SeriesDraftHistory history = new SeriesDraftHistory();
        FinalDraftResult first = engine.draftDeterministicBest(DraftTestSupport.NEUTRAL, DraftTestSupport.NEUTRAL, history);
        FinalDraftResult replay = engine.draftDeterministicBest(DraftTestSupport.NEUTRAL, DraftTestSupport.NEUTRAL, history);
        assertThat(first.decisions()).hasSize(20).isEqualTo(replay.decisions());
        assertThat(first.blueBans()).hasSize(5); assertThat(first.redBans()).hasSize(5);
        assertThat(first.bluePicks()).hasSize(5); assertThat(first.redPicks()).hasSize(5);
        HashSet<com.lolfm.champion.ChampionId> unique = new HashSet<>();
        unique.addAll(first.blueBans()); unique.addAll(first.redBans()); unique.addAll(first.bluePicks()); unique.addAll(first.redPicks());
        assertThat(unique).hasSize(20);
        assertThat(first.blueFinalRoleAssignments().values()).containsExactlyInAnyOrder(com.lolfm.domain.Position.values());
        assertThat(first.redFinalRoleAssignments().values()).containsExactlyInAnyOrder(com.lolfm.domain.Position.values());
        assertThat(first.decisions()).allSatisfy(decision -> {
            assertThat(decision.componentBreakdown()).isNotEmpty();
            assertThat(decision.topAlternatives()).isNotEmpty();
            assertThat(decision.finalSearchScore())
                    .isEqualTo(decision.immediateScore() + decision.continuationScore());
        });
        assertThat(first.draftIdentity()).isEqualTo(replay.draftIdentity());
        assertThat(first.matchChampionAssignments().asMap()).isEqualTo(replay.matchChampionAssignments().asMap());
    }

    @Test
    void hardFearlessConsumesOnlyCompletedPicksOnceAndFreshSeriesDoesNotLeak() {
        SeriesDraftHistory series = new SeriesDraftHistory();
        FinalDraftResult gameOne = engine.draftDeterministicBest(DraftTestSupport.NEUTRAL, DraftTestSupport.NEUTRAL, series);
        series.commitCompleted(gameOne);
        series.commitCompleted(gameOne);
        assertThat(series.committedGameCount()).isOne();
        assertThat(series.consumedPicks()).containsExactlyInAnyOrderElementsOf(
                java.util.stream.Stream.concat(gameOne.bluePicks().stream(), gameOne.redPicks().stream()).toList());
        assertThat(series.consumedPicks()).doesNotContainAnyElementsOf(gameOne.blueBans());
        assertThat(series.consumedPicks()).doesNotContainAnyElementsOf(gameOne.redBans());
        FinalDraftResult gameTwo = engine.draftDeterministicBest(DraftTestSupport.NEUTRAL, DraftTestSupport.NEUTRAL, series);
        assertThat(gameTwo.hardFearlessExclusions()).containsAll(series.consumedPicks());
        assertThat(gameTwo.bluePicks()).doesNotContainAnyElementsOf(series.consumedPicks());
        assertThat(gameTwo.redPicks()).doesNotContainAnyElementsOf(series.consumedPicks());
        assertThat(new SeriesDraftHistory().consumedPicks()).isEmpty();
    }

    @Test
    void resultingAssignmentsRunThroughTheExistingMatchSimulatorDeterministically() {
        FinalDraftResult result = engine.draftDeterministicBest(DraftTestSupport.NEUTRAL, DraftTestSupport.NEUTRAL, new SeriesDraftHistory());
        DummyDataFactory teams = new DummyDataFactory();
        MatchSimulator simulator = new MatchSimulator(new TeamfightResolver(), new EndGameEvaluator(), new SnapshotFactory(),
                new ObjectiveResolver(), new PostFightResolver(), new ObjectiveAttemptResolver(), new StructureResolver(), new PushResolver());
        MatchTimeline first = simulator.simulate(teams.createBlueTeam(), teams.createRedTeam(), 73L, result.matchChampionAssignments());
        MatchTimeline replay = simulator.simulate(teams.createBlueTeam(), teams.createRedTeam(), 73L, result.matchChampionAssignments());
        assertThat(first.getWinner()).isEqualTo(replay.getWinner());
        assertThat(first.getDurationSeconds()).isEqualTo(replay.getDurationSeconds());
        assertThat(first.getEvents()).isNotEmpty();
        assertThat(first.getSnapshots()).isNotEmpty();
        assertCompleteTimelineEquals(first, replay);
    }

    @Test
    void fullDraftLatencyMeasurementDoesNotMutateSeriesBehavior() {
        SeriesDraftHistory history = new SeriesDraftHistory();
        long gameOneStart = System.nanoTime();
        FinalDraftResult gameOne = engine.draftDeterministicBest(
                DraftTestSupport.NEUTRAL, DraftTestSupport.NEUTRAL, history);
        long gameOneMillis = (System.nanoTime() - gameOneStart) / 1_000_000;
        assertThat(history.committedGameCount()).isZero();
        history.commitCompleted(gameOne);
        long laterStart = System.nanoTime();
        FinalDraftResult later = engine.draftDeterministicBest(
                DraftTestSupport.NEUTRAL, DraftTestSupport.NEUTRAL, history);
        long laterMillis = (System.nanoTime() - laterStart) / 1_000_000;
        assertThat(history.committedGameCount()).isOne();
        assertThat(later.hardFearlessExclusions()).containsExactlyInAnyOrderElementsOf(history.consumedPicks());
        System.out.printf("PHASE13F_LATENCY_GAME1_MS=%d%n", gameOneMillis);
        System.out.printf("PHASE13F_LATENCY_LATER_HARD_FEARLESS_MS=%d%n", laterMillis);
    }
}
