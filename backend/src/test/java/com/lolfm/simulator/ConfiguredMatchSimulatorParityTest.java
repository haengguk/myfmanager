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
        ResolvedSimulationRuntimeProfile baseline = SimulationRuntimeProfiles.resolve(
                SimulationRuntimeProfileId.BASELINE_V1);

        MatchTimeline existing = existingAutowiredSimulator.simulate(
                blue, red, SEED, assignments);
        MatchTimeline explicit = configuredFactory.create(
                        baseline, SimulationInstrumentation.enabled())
                .simulate(blue, red, SEED, assignments);

        assertThat(explicit).usingRecursiveComparison().isEqualTo(existing);
    }

    @Test
    void instrumentationToggleDoesNotChangeTimeline() {
        DummyDataFactory teams = new DummyDataFactory();
        var blue = teams.createBlueTeam();
        var red = teams.createRedTeam();
        var assignments = new ChampionSelectionValidator(champions).resolve(null);
        ResolvedSimulationRuntimeProfile baseline = SimulationRuntimeProfiles.resolve(
                SimulationRuntimeProfileId.BASELINE_V1);

        MatchTimeline enabled = configuredFactory.create(
                        baseline, SimulationInstrumentation.enabled())
                .simulate(blue, red, SEED, assignments);
        MatchTimeline disabled = configuredFactory.create(
                        baseline, SimulationInstrumentation.disabled())
                .simulate(blue, red, SEED, assignments);

        assertThat(disabled).usingRecursiveComparison().isEqualTo(enabled);
    }
}
