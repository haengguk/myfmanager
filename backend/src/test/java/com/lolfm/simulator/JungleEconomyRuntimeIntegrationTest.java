package com.lolfm.simulator;

import static com.lolfm.testing.CompleteTimelineAssertions.assertCompleteObservedMatchEquals;
import static org.assertj.core.api.Assertions.assertThat;

import com.lolfm.champion.ChampionResourceSet;
import com.lolfm.champion.ChampionSelectionValidator;
import com.lolfm.factory.DummyDataFactory;
import org.junit.jupiter.api.Test;

class JungleEconomyRuntimeIntegrationTest {
    private static final long SEED = 2026082201L;
    private static final ChampionResourceSet RESOURCES = ChampionResourceSet.loadDefault();

    @Test
    void candidateIsSameSeedDeterministicAndDiagnosticsAreObservational() {
        DummyDataFactory teams = new DummyDataFactory();
        var assignments = new ChampionSelectionValidator(RESOURCES.catalog()).resolve(null);
        MatchSimulator diagnosticsOn = simulator(
                SimulationRuntimeProfileId.FULL_SYSTEM_WITH_JUNGLE_ECONOMY_CANDIDATE_V1,
                SimulationInstrumentation.enabled());
        MatchSimulator diagnosticsOff = simulator(
                SimulationRuntimeProfileId.FULL_SYSTEM_WITH_JUNGLE_ECONOMY_CANDIDATE_V1,
                SimulationInstrumentation.disabled());

        ObservedMatchSimulation first = diagnosticsOn.simulateObserved(
                teams.createBlueTeam(), teams.createRedTeam(), SEED, assignments);
        ObservedMatchSimulation replay = diagnosticsOn.simulateObserved(
                teams.createBlueTeam(), teams.createRedTeam(), SEED, assignments);
        ObservedMatchSimulation withoutDiagnostics = diagnosticsOff.simulateObserved(
                teams.createBlueTeam(), teams.createRedTeam(), SEED, assignments);

        assertCompleteObservedMatchEquals(replay, first);
        assertCompleteObservedMatchEquals(withoutDiagnostics, first);
    }

    @Test
    void tempoCandidateIsSameSeedDeterministicAndDiagnosticsAreObservational() {
        DummyDataFactory teams = new DummyDataFactory();
        var assignments = new ChampionSelectionValidator(RESOURCES.catalog()).resolve(null);
        MatchSimulator diagnosticsOn = simulator(
                SimulationRuntimeProfileId.FULL_SYSTEM_WITH_JUNGLE_TEMPO_CANDIDATE_V1,
                SimulationInstrumentation.enabled());
        MatchSimulator diagnosticsOff = simulator(
                SimulationRuntimeProfileId.FULL_SYSTEM_WITH_JUNGLE_TEMPO_CANDIDATE_V1,
                SimulationInstrumentation.disabled());

        ObservedMatchSimulation first = diagnosticsOn.simulateObserved(
                teams.createBlueTeam(), teams.createRedTeam(), SEED, assignments);
        ObservedMatchSimulation replay = diagnosticsOn.simulateObserved(
                teams.createBlueTeam(), teams.createRedTeam(), SEED, assignments);
        ObservedMatchSimulation withoutDiagnostics = diagnosticsOff.simulateObserved(
                teams.createBlueTeam(), teams.createRedTeam(), SEED, assignments);

        assertCompleteObservedMatchEquals(replay, first);
        assertCompleteObservedMatchEquals(withoutDiagnostics, first);
    }

    @Test
    void candidateExecutesUnifiedEconomyWhileFrozenFullProfileDoesNot() {
        DummyDataFactory teams = new DummyDataFactory();
        var assignments = new ChampionSelectionValidator(RESOURCES.catalog()).resolve(null);

        MatchSimulator.SimulationResult candidate = simulator(
                SimulationRuntimeProfileId.FULL_SYSTEM_WITH_JUNGLE_ECONOMY_CANDIDATE_V1,
                SimulationInstrumentation.enabled()).simulateWithDiagnostics(
                        teams.createBlueTeam(), teams.createRedTeam(), SEED, assignments);
        MatchSimulator.SimulationResult frozen = simulator(
                SimulationRuntimeProfileId.FULL_SYSTEM_CANDIDATE_V1,
                SimulationInstrumentation.enabled()).simulateWithDiagnostics(
                        teams.createBlueTeam(), teams.createRedTeam(), SEED, assignments);

        assertThat(candidate.jungleEconomyExecutionStats().evaluations()).isPositive();
        assertThat(candidate.jungleEconomyExecutionStats().eligibleOutcomes()).isPositive();
        assertThat(candidate.jungleEconomyExecutionStats().awardedCs()).isPositive();
        assertThat(candidate.jungleEconomyExecutionStats().awardedGold())
                .isEqualTo(candidate.jungleEconomyExecutionStats().awardedCs()
                        * JungleEconomyRuleConfig.GOLD_PER_CS);
        assertThat(candidate.jungleEconomyExecutionStats().awardedExperience()).isPositive();
        assertThat(candidate.jungleEconomyExecutionStats().duplicateCalls()).isZero();
        assertThat(frozen.jungleEconomyExecutionStats().evaluations()).isZero();
        assertThat(frozen.jungleEconomyExecutionStats().eligibleOutcomes()).isZero();
    }

    @Test
    void tempoCandidateBuildsCreditAndConsumesItOnlyForActualJungleActions() {
        DummyDataFactory teams = new DummyDataFactory();
        var assignments = new ChampionSelectionValidator(RESOURCES.catalog()).resolve(null);

        MatchSimulator.SimulationResult tempo = simulator(
                SimulationRuntimeProfileId.FULL_SYSTEM_WITH_JUNGLE_TEMPO_CANDIDATE_V1,
                SimulationInstrumentation.enabled()).simulateWithDiagnostics(
                        teams.createBlueTeam(), teams.createRedTeam(), SEED, assignments);
        MatchSimulator.SimulationResult economyOnly = simulator(
                SimulationRuntimeProfileId.FULL_SYSTEM_WITH_JUNGLE_ECONOMY_CANDIDATE_V1,
                SimulationInstrumentation.enabled()).simulateWithDiagnostics(
                        teams.createBlueTeam(), teams.createRedTeam(), SEED, assignments);

        assertThat(tempo.jungleTempoExecutionStats().economyUpdates()).isPositive();
        assertThat(tempo.jungleTempoExecutionStats().actualConsumptions().values())
                .anyMatch(count -> count > 0);
        assertThat(tempo.jungleTempoExecutionStats().actualConsumptions()
                .get(JungleTempoActionType.GANK))
                .isEqualTo(tempo.combatExecutionStats().jungleGankAttempts());
        assertThat(tempo.jungleTempoExecutionStats().actualConsumptions()
                .get(JungleTempoActionType.COUNTER_GANK))
                .isEqualTo(tempo.combatExecutionStats().counterGankAttempts());
        assertThat(economyOnly.jungleTempoExecutionStats().economyUpdates()).isZero();
        assertThat(economyOnly.jungleTempoExecutionStats().actualConsumptions().values())
                .containsOnly(0);
    }

    private MatchSimulator simulator(
            SimulationRuntimeProfileId profileId,
            SimulationInstrumentation instrumentation
    ) {
        SimulationOptions options = SimulationRuntimeProfiles.resolve(profileId)
                .gameplayConfiguration().toSimulationOptions(instrumentation);
        return new MatchSimulator(
                new TeamfightResolver(), new EndGameEvaluator(), new SnapshotFactory(),
                new ObjectiveResolver(), new PostFightResolver(),
                new ObjectiveAttemptResolver(), new StructureResolver(), new PushResolver(),
                options, RESOURCES.matchup());
    }
}
