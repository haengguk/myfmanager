package com.lolfm.simulator;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;

class CombatProgressionEvaluatorPureTest {
    @Test
    void counterfactualEvaluationLeavesAllExecutionStatsAndGameplaySnapshotExact() throws Exception {
        GameState state = LateGameTestSupport.state();
        state.advanceTimeSeconds(900);
        List<PlayerState> blue = state.getBlueTeamState().getPlayers();
        List<PlayerState> red = state.getRedTeamState().getPlayers();
        CombatProgressionEvaluator evaluator = new CombatProgressionEvaluator();
        var progressionBefore = state.getProgressionExecutionStats().snapshot();
        var championPowerBefore = state.getChampionPowerExecutionStats().snapshot();
        var matchupBefore = state.getChampionMatchupExecutionStats().snapshot();
        String gameplayBefore = snapshotJson(state);

        CombatProgressionBreakdown pure = evaluator.evaluatePure(state,
                ProgressionCombatContext.OBJECTIVE_FIGHT, blue, red,
                3.25, 1.5, ProgressionApplicationStage.COMBAT_SCORE);

        assertThat(state.getProgressionExecutionStats().snapshot()).isEqualTo(progressionBefore);
        assertThat(state.getChampionPowerExecutionStats().snapshot()).isEqualTo(championPowerBefore);
        assertThat(state.getChampionMatchupExecutionStats().snapshot()).isEqualTo(matchupBefore);
        assertThat(snapshotJson(state)).isEqualTo(gameplayBefore);

        CombatProgressionBreakdown actual = evaluator.evaluateAndRecord(state,
                ProgressionCombatContext.OBJECTIVE_FIGHT, blue, red,
                3.25, 1.5, ProgressionApplicationStage.COMBAT_SCORE);
        var progressionAfter = state.getProgressionExecutionStats().snapshot();
        var championPowerAfter = state.getChampionPowerExecutionStats().snapshot();
        var matchupAfter = state.getChampionMatchupExecutionStats().snapshot();

        assertThat(actual).isEqualTo(pure);
        assertThat(progressionAfter.combatSamples())
                .hasSize(progressionBefore.combatSamples().size() + 1);
        assertThat(progressionAfter.powerApplications())
                .isEqualTo(progressionBefore.powerApplications() + 1);
        assertThat(championPowerAfter.samples())
                .hasSize(championPowerBefore.samples().size() + 1);
        assertThat(matchupAfter.evaluations()).isEqualTo(matchupBefore.evaluations() + 1);
        assertThat(snapshotJson(state)).isEqualTo(gameplayBefore);
    }

    private static String snapshotJson(GameState state) throws Exception {
        return new ObjectMapper().findAndRegisterModules()
                .writeValueAsString(new SnapshotFactory().create(state));
    }
}
