package com.lolfm.simulator;

import static com.lolfm.testing.CompleteTimelineAssertions.assertCompleteTimelineEquals;
import static org.assertj.core.api.Assertions.assertThat;

import com.lolfm.domain.MatchEventType;
import com.lolfm.domain.MatchTimeline;
import com.lolfm.factory.DummyDataFactory;
import java.util.List;
import org.junit.jupiter.api.Test;

class ProgressionIsolationTest {
    private MatchSimulator simulator(SimulationOptions options) {
        return new MatchSimulator(
                new TeamfightResolver(),
                new EndGameEvaluator(),
                new SnapshotFactory(),
                new ObjectiveResolver(),
                new PostFightResolver(),
                new ObjectiveAttemptResolver(),
                new StructureResolver(),
                new PushResolver(),
                options);
    }

    private MatchTimeline run(SimulationOptions options, int seed) {
        DummyDataFactory teams = new DummyDataFactory();
        return simulator(options).simulate(teams.createBlueTeam(), teams.createRedTeam(), seed);
    }

    private List<String> gameplay(MatchTimeline timeline) {
        return timeline.getEvents().stream()
                .filter(event -> event.getType() != MatchEventType.LEVEL_UP
                        && event.getType() != MatchEventType.ITEM_STAGE_REACHED)
                .map(event -> event.getTimeSeconds() + "|" + event.getType() + "|" + event.getMessage())
                .toList();
    }

    @Test
    void stateOnlyPreservesLegacyGameplayRandomPath() {
        var progressionOff = run(
                SimulationOptions.productionDefaults().withProgressionEnabled(false), 19);
        var stateOnly = run(
                SimulationOptions.productionDefaults().withProgressionPowerEnabled(false), 19);

        assertThat(gameplay(stateOnly)).containsExactlyElementsOf(gameplay(progressionOff));
        assertThat(stateOnly.getWinner()).isEqualTo(progressionOff.getWinner());
        assertThat(stateOnly.getDurationSeconds()).isEqualTo(progressionOff.getDurationSeconds());
    }

    @Test
    void diagnosticsInstrumentationDoesNotChangeTimeline() {
        var disabled = run(
                SimulationOptions.productionDefaults().withDiagnosticsEnabled(false), 23);
        var enabled = run(
                SimulationOptions.productionDefaults().withDiagnosticsEnabled(true), 23);

        assertCompleteTimelineEquals(disabled, enabled);
    }

    @Test
    void progressionOffEmitsNoProgressionEventsAndNeutralSnapshots() {
        var timeline = run(
                SimulationOptions.productionDefaults().withProgressionEnabled(false), 7);

        assertThat(timeline.getEvents()).noneMatch(event -> event.getProgressionEvent() != null);
        assertThat(timeline.getSnapshots()).allMatch(snapshot -> !snapshot.getProgression().enabled()
                && snapshot.getPlayerSnapshots().stream().allMatch(player -> player.getLevel() == 1
                        && player.getTotalExperience() == 0
                        && player.getItemStage() == ItemProgressStage.STARTING));
    }

    @Test
    void productionDefaultsEnableStateAndPower() {
        assertThat(SimulationOptions.productionDefaults().progressionEnabled()).isTrue();
        assertThat(SimulationOptions.productionDefaults().progressionPowerEnabled()).isTrue();
    }
}
