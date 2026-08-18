package com.lolfm.simulator;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lolfm.champion.ChampionLineupRequest;
import com.lolfm.champion.ChampionResourceSet;
import com.lolfm.champion.ChampionSelectionRequest;
import com.lolfm.champion.ChampionSelectionValidator;
import com.lolfm.champion.MatchChampionAssignments;
import com.lolfm.domain.MatchSnapshot;
import com.lolfm.factory.DummyDataFactory;
import java.util.List;
import org.junit.jupiter.api.Test;

class ChampionRoleScopeRuntimeTest {
    private static final ChampionResourceSet RESOURCES = ChampionResourceSet.loadDefault();
    private static final ChampionSelectionValidator SELECTIONS =
            new ChampionSelectionValidator(RESOURCES.catalog());

    @Test
    void eachNewRoleRunsThroughTheNormalProductionRuntime() {
        List<ChampionSelectionRequest> cases = List.of(
                request("varus", "sejuani", "azir", "jinx", "nautilus"),
                request("anivia", "sejuani", "azir", "jinx", "nautilus"),
                request("renekton", "sejuani", "azir", "cassiopeia", "nautilus"),
                request("renekton", "sejuani", "azir", "taliyah", "nautilus"));
        List<String> newRoleChampions = List.of("varus", "anivia", "cassiopeia", "taliyah");
        MatchSimulator simulator = simulator();

        for (int index = 0; index < cases.size(); index++) {
            MatchChampionAssignments assignments = SELECTIONS.resolve(cases.get(index));
            MatchSimulator.SimulationResult result = simulator.simulateWithSideDiagnostics(
                    new DummyDataFactory().createBlueTeam(),
                    new DummyDataFactory().createRedTeam(),
                    assignments,
                    new SideOrientationRandomTraceObserver(
                            13_013L + index, "ROLE_SCOPE_" + index,
                            "BLUE_LOGICAL", "RED_LOGICAL", false));

            assertThat(result.winnerSide()).isIn(TeamSide.BLUE, TeamSide.RED);
            assertThat(result.endReason()).isNotEqualTo(GameEndReason.SIMULATION_TIMEOUT);
            assertThat(result.timeline().getDurationSeconds())
                    .isBetween(1, MatchSimulator.SIMULATION_SAFETY_TIMEOUT_SECONDS);
            assertThat(result.timeline().getEvents()).isNotEmpty();
            assertThat(result.timeline().getSnapshots()).isNotEmpty();
            assertThat(result.championPowerExecutionStats().samples()).isNotEmpty();
            assertThat(result.championPowerExecutionStats().missingAssignment()).isZero();
            assertThat(result.championMatchupExecutionStats().missingAssignmentErrors()).isZero();
            assertThat(result.championMatchupExecutionStats().totalPairApplications()).isGreaterThan(0);
            assertThat(result.championMatchupExecutionStats().applicationEdges())
                    .allMatch(edge -> Double.isFinite(edge));
            assertThat(result.compositionRuntimeDiagnostics().initialized()).isTrue();
            assertThat(result.compositionRuntimeDiagnostics().lineupBuildCount()).isEqualTo(2);
            assertThat(result.compositionRuntimeDiagnostics().teamCompositionAnalysisCount()).isEqualTo(2);
            assertThat(result.compositionRuntimeDiagnostics().interactionAnalysisCount()).isEqualTo(1);
            assertThat(result.compositionRuntimeDiagnostics().directRandomCallCount()).isZero();
            assertThat(result.compositionRuntimeDiagnostics().compositionRandomDrawCount()).isZero();
            assertThat(result.randomDrawCount()).isGreaterThan(0);
            String newRoleChampion = newRoleChampions.get(index);
            assertThat(result.timeline().getSnapshots())
                    .anyMatch(snapshot -> containsChampion(snapshot, newRoleChampion));
        }
    }

    @Test
    void sameSeedWithANewRoleProducesAnExactReplay() throws Exception {
        ChampionSelectionRequest selection = request(
                "renekton", "sejuani", "azir", "taliyah", "nautilus");
        MatchChampionAssignments assignments = SELECTIONS.resolve(selection);
        MatchSimulator simulator = simulator();

        MatchSimulator.SimulationResult first = traced(simulator, assignments);
        MatchSimulator.SimulationResult second = traced(simulator, assignments);
        ObjectMapper mapper = new ObjectMapper();

        assertThat(mapper.writeValueAsString(first.timeline()))
                .isEqualTo(mapper.writeValueAsString(second.timeline()));
        assertThat(first.winnerSide()).isEqualTo(second.winnerSide());
        assertThat(first.endReason()).isEqualTo(second.endReason());
        assertThat(first.randomDrawCount()).isEqualTo(second.randomDrawCount());
        assertThat(first.randomTrace()).isEqualTo(second.randomTrace());
        assertThat(first.championPowerExecutionStats())
                .isEqualTo(second.championPowerExecutionStats());
        assertThat(first.championMatchupExecutionStats())
                .isEqualTo(second.championMatchupExecutionStats());
        assertThat(first.compositionRuntimeDiagnostics())
                .isEqualTo(second.compositionRuntimeDiagnostics());
    }

    private MatchSimulator.SimulationResult traced(
            MatchSimulator simulator, MatchChampionAssignments assignments) {
        return simulator.simulateWithSideDiagnostics(
                new DummyDataFactory().createBlueTeam(),
                new DummyDataFactory().createRedTeam(),
                assignments,
                new SideOrientationRandomTraceObserver(
                        13_099L, "ROLE_SCOPE_REPLAY", "BLUE_LOGICAL", "RED_LOGICAL", true));
    }

    private MatchSimulator simulator() {
        return new MatchSimulator(
                new TeamfightResolver(), new EndGameEvaluator(), new SnapshotFactory(),
                new ObjectiveResolver(), new PostFightResolver(), new ObjectiveAttemptResolver(),
                new StructureResolver(), new PushResolver(), SimulationOptions.productionDefaults(),
                RESOURCES.matchup());
    }

    private ChampionSelectionRequest request(
            String top, String jungle, String mid, String adc, String support) {
        ChampionLineupRequest blue = new ChampionLineupRequest(top, jungle, mid, adc, support);
        ChampionLineupRequest red = new ChampionLineupRequest(
                "jax", "lee-sin", "ahri", "kaisa", "rakan");
        return new ChampionSelectionRequest(blue, red);
    }

    private boolean containsChampion(MatchSnapshot snapshot, String championId) {
        return snapshot.getPlayerSnapshots().stream()
                .anyMatch(player -> championId.equals(player.getChampionId()));
    }
}
