package com.lolfm.simulator;

import static org.assertj.core.api.Assertions.assertThat;

import com.lolfm.champion.ChampionCatalog;
import com.lolfm.champion.ChampionSelectionValidator;
import com.lolfm.domain.MatchTimeline;
import com.lolfm.factory.DummyDataFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class ConfiguredMatchSimulatorParityTest {
    private static final long SEED = 20260822L;

    @Autowired MatchSimulator existingAutowiredSimulator;
    @Autowired ConfiguredMatchSimulatorFactory configuredFactory;
    @Autowired ChampionCatalog champions;

    @Test
    void baselineProfileIsExactTimelineParityWithExistingAutowiredSimulator() {
        DummyDataFactory teams = new DummyDataFactory();
        var blue = teams.createBlueTeam();
        var red = teams.createRedTeam();
        var assignments = new ChampionSelectionValidator(champions).resolve(null);
        MatchTimeline existing = existingAutowiredSimulator.simulate(
                blue, red, SEED, assignments);
        ObservedMatchSimulation explicit = configuredFactory.create(
                        SimulationRuntimeProfileId.BASELINE_V1,
                        SimulationInstrumentation.enabled())
                .simulateObserved(blue, red, SEED, assignments);

        assertThat(explicit.timeline()).usingRecursiveComparison().isEqualTo(existing);
        assertThat(explicit.randomFingerprint().randomDrawCount()).isPositive();
        assertThat(explicit.randomFingerprint().randomTraceHash()).matches("[0-9a-f]{64}");
    }

    @Test
    void instrumentationToggleDoesNotChangeTimeline() {
        DummyDataFactory teams = new DummyDataFactory();
        var blue = teams.createBlueTeam();
        var red = teams.createRedTeam();
        var assignments = new ChampionSelectionValidator(champions).resolve(null);
        ObservedMatchSimulation enabled = configuredFactory.create(
                        SimulationRuntimeProfileId.BASELINE_V1,
                        SimulationInstrumentation.enabled())
                .simulateObserved(blue, red, SEED, assignments);
        ObservedMatchSimulation disabled = configuredFactory.create(
                        SimulationRuntimeProfileId.BASELINE_V1,
                        SimulationInstrumentation.disabled())
                .simulateObserved(blue, red, SEED, assignments);

        assertThat(disabled).usingRecursiveComparison().isEqualTo(enabled);
    }
}
