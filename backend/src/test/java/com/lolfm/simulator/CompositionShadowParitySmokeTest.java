package com.lolfm.simulator;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lolfm.champion.ChampionCatalog;
import com.lolfm.champion.ChampionSelectionValidator;
import com.lolfm.composition.CompositionGameplayConfigurationException;
import com.lolfm.composition.TeamCompositionGameplayMode;
import com.lolfm.domain.MatchTimeline;
import com.lolfm.factory.DummyDataFactory;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CompositionShadowParitySmokeTest {
    @Test
    void explicitOffAndShadowUseTheSameGameplayTimeline() throws Exception {
        DummyDataFactory factory = new DummyDataFactory();
        var assignments = new ChampionSelectionValidator(new ChampionCatalog(new ObjectMapper())).resolve(null);
        MatchSimulator off = simulator(SimulationOptions.productionDefaults()
                .withTeamCompositionGameplayMode(TeamCompositionGameplayMode.OFF));
        MatchSimulator shadow = simulator(SimulationOptions.productionDefaults()
                .withTeamCompositionGameplayMode(TeamCompositionGameplayMode.SHADOW));

        MatchSimulator.SimulationResult offResult = off.simulateWithDiagnostics(
                factory.createBlueTeam(), factory.createRedTeam(), 7L, assignments);
        MatchSimulator.SimulationResult shadowResult = shadow.simulateWithDiagnostics(
                factory.createBlueTeam(), factory.createRedTeam(), 7L, assignments);

        assertTimelineParity(shadowResult.timeline(), offResult.timeline());
        assertThat(shadowResult.randomDrawCount()).isEqualTo(offResult.randomDrawCount());
        assertThat(shadowResult.compositionRuntimeDiagnostics().lineupBuildCount()).isEqualTo(2);
        assertThat(shadowResult.compositionRuntimeDiagnostics().teamCompositionAnalysisCount()).isEqualTo(2);
        assertThat(shadowResult.compositionRuntimeDiagnostics().interactionAnalysisCount()).isEqualTo(1);
        assertThat(shadowResult.compositionRuntimeDiagnostics().contextEdgeCount()).isEqualTo(6);
        assertThat(shadowResult.compositionRuntimeDiagnostics().gameplayApplicationCount()).isZero();
        assertThat(shadowResult.compositionRuntimeDiagnostics().nonZeroModifierCount()).isZero();
    }

    @Test
    void candidateModeIsRejectedBeforeSimulationAndDoesNotFallback() {
        DummyDataFactory factory = new DummyDataFactory();
        var assignments = new ChampionSelectionValidator(new ChampionCatalog(new ObjectMapper())).resolve(null);
        MatchSimulator candidate = simulator(SimulationOptions.productionDefaults()
                .withTeamCompositionGameplayMode(TeamCompositionGameplayMode.CANDIDATE));

        assertThatThrownBy(() -> candidate.simulateWithDiagnostics(
                factory.createBlueTeam(), factory.createRedTeam(), 7L, assignments))
                .isInstanceOf(CompositionGameplayConfigurationException.class)
                .satisfies(error -> assertThat(((CompositionGameplayConfigurationException) error).code())
                        .isEqualTo("CANDIDATE_CONTEXT_GAINS_NOT_APPROVED"));
    }

    private static MatchSimulator simulator(SimulationOptions options) {
        return new MatchSimulator(new TeamfightResolver(), new EndGameEvaluator(), new SnapshotFactory(),
                new ObjectiveResolver(), new PostFightResolver(), new ObjectiveAttemptResolver(),
                new StructureResolver(), new PushResolver(), options);
    }

    private static void assertTimelineParity(MatchTimeline actual, MatchTimeline expected) throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        assertThat(actual.getDurationSeconds()).isEqualTo(expected.getDurationSeconds());
        assertThat(actual.getWinner()).isEqualTo(expected.getWinner());
        assertThat(actual.getEvents()).hasSameSizeAs(expected.getEvents());
        for (int index = 0; index < actual.getEvents().size(); index++) {
            assertThat(mapper.writeValueAsString(actual.getEvents().get(index)))
                    .as("event[%s]", index)
                    .isEqualTo(mapper.writeValueAsString(expected.getEvents().get(index)));
        }
        assertThat(actual.getSnapshots()).hasSameSizeAs(expected.getSnapshots());
        for (int index = 0; index < actual.getSnapshots().size(); index++) {
            assertThat(mapper.writeValueAsString(actual.getSnapshots().get(index)))
                    .as("snapshot[%s]", index)
                    .isEqualTo(mapper.writeValueAsString(expected.getSnapshots().get(index)));
        }
    }
}
