package com.lolfm.simulator;

import static com.lolfm.testing.CompleteTimelineAssertions.assertCompleteTimelineEquals;
import static org.assertj.core.api.Assertions.assertThat;

import com.lolfm.domain.Position;
import com.lolfm.factory.DummyDataFactory;
import org.junit.jupiter.api.Test;

class ProgressionReproducibilityTest {
    private MatchSimulator simulator() {
        return new MatchSimulator(
                new TeamfightResolver(),
                new EndGameEvaluator(),
                new SnapshotFactory(),
                new ObjectiveResolver(),
                new PostFightResolver(),
                new ObjectiveAttemptResolver(),
                new StructureResolver(),
                new PushResolver(),
                SimulationOptions.productionDefaults());
    }

    @Test
    void sameSeedProducesSameCompleteResponse() {
        DummyDataFactory teams = new DummyDataFactory();
        var first = simulator().simulate(teams.createBlueTeam(), teams.createRedTeam(), 77);
        teams = new DummyDataFactory();
        var replay = simulator().simulate(teams.createBlueTeam(), teams.createRedTeam(), 77);

        assertCompleteTimelineEquals(first, replay);
    }

    @Test
    void directRewardsUseNoRandom() {
        PlayerState player = new PlayerState("p", Position.TOP, 500);
        player.configureProgression(TeamSide.BLUE, true, new ProgressionExecutionStats());
        player.getProgressionState().awardExperience(ExperienceSource.KILL, 180, 1);
        player.addGold(300, GoldSource.KILL, 1);

        assertThat(player.getProgressionState().getTotalExperience()).isEqualTo(180);
    }
}
