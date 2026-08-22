package com.lolfm.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.lolfm.simulator.ConfiguredMatchSimulatorFactory;
import com.lolfm.simulator.SimulationInstrumentation;
import com.lolfm.simulator.SimulationRuntimeProfileId;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class RealDraftRandomObservationParityTest {
    @Autowired RealDraftMatchOrchestrator orchestrator;
    @Autowired ConfiguredMatchSimulatorFactory simulators;

    @Test
    void realLckRandomObservationIsExactPlainTimelineParity() {
        RealDraftMatchResult observed = orchestrator.orchestrate(
                "GEN", "T1", 73L, SimulationRuntimeProfileId.BASELINE_V1);
        var plain = simulators.create(
                        SimulationRuntimeProfileId.BASELINE_V1,
                        SimulationInstrumentation.enabled())
                .simulate(observed.blueTeam(), observed.redTeam(), observed.matchSeed(),
                        observed.matchChampionAssignments());

        assertThat(observed.timeline()).usingRecursiveComparison().isEqualTo(plain);
        assertThat(observed.executionProvenance().randomFingerprint().randomDrawCount())
                .isPositive();
    }
}
