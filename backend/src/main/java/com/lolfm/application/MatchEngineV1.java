package com.lolfm.application;

import com.lolfm.champion.ChampionCatalog;
import com.lolfm.champion.ChampionRoleKey;
import com.lolfm.domain.MatchTimeline;
import com.lolfm.domain.Team;
import com.lolfm.simulator.ConfiguredMatchSimulatorFactory;
import com.lolfm.simulator.MatchSimulator;
import com.lolfm.simulator.SimulationInstrumentation;
import com.lolfm.simulator.StructuredMatchSimulationOutcome;
import java.util.Objects;
import org.springframework.stereotype.Component;

/** Authoritative production facade for the frozen Match Engine V1 application boundary. */
@Component
public final class MatchEngineV1 {
    private final ConfiguredMatchSimulatorFactory simulators;
    private final SimulationProvenanceService provenance;
    private final MatchEngineV1Projector projector;
    private final ChampionCatalog champions;

    public MatchEngineV1(
            ConfiguredMatchSimulatorFactory simulators,
            SimulationProvenanceService provenance,
            MatchEngineV1Projector projector,
            ChampionCatalog champions
    ) {
        this.simulators = Objects.requireNonNull(simulators, "simulators");
        this.provenance = Objects.requireNonNull(provenance, "provenance");
        this.projector = Objects.requireNonNull(projector, "projector");
        this.champions = Objects.requireNonNull(champions, "champions");
    }

    public MatchEngineV1Output execute(MatchEngineV1Input input) {
        return execute(input, SimulationInstrumentation.enabled());
    }

    /** Instrumentation is observational and cannot select a gameplay profile or candidate. */
    public MatchEngineV1Output execute(
            MatchEngineV1Input input,
            SimulationInstrumentation instrumentation
    ) {
        return executeDetailed(input, instrumentation).output();
    }

    MatchEngineV1Execution executeDetailed(
            MatchEngineV1Input input,
        SimulationInstrumentation instrumentation
    ) {
        validateBeforeRandom(input);
        validateChampionAssignmentsBeforeRandom(input);
        Objects.requireNonNull(instrumentation, "instrumentation");
        Team blueTeam = input.domainBlueTeam();
        Team redTeam = input.domainRedTeam();
        MatchSimulator simulator = simulators.create(
                MatchEngineV1Policy.authoritative().retainedRuntimeProfileId(), instrumentation);
        StructuredMatchSimulationOutcome outcome = simulator.simulateStructuredObserved(
                blueTeam, redTeam, input.matchSeed(), input.domainChampionAssignments());
        SimulationExecutionProvenance executionProvenance = provenance.createV1(
                input, instrumentation, outcome.timeline(), outcome.randomFingerprint());
        MatchEngineV1Output output = projector.project(input, outcome, executionProvenance);
        return new MatchEngineV1Execution(output, outcome.timeline(), executionProvenance);
    }

    private static void validateBeforeRandom(MatchEngineV1Input input) {
        Objects.requireNonNull(input, "input");
        MatchEngineV1Policy.requireAuthoritative(input.productionPolicy());
        if (!input.productionPolicy().configurationHash().equals(
                MatchEngineV1Policy.authoritative().configurationHash())
                || input.inputHash().isBlank()) {
            throw new IllegalArgumentException("MATCH_ENGINE_V1_INPUT_POLICY_MISMATCH");
        }
    }

    private void validateChampionAssignmentsBeforeRandom(MatchEngineV1Input input) {
        input.championAssignments().forEach(assignment -> {
            if (!champions.supports(new ChampionRoleKey(
                    assignment.championId(), assignment.position()))) {
                throw new IllegalArgumentException(
                        "MATCH_ENGINE_V1_ILLEGAL_CHAMPION_ASSIGNMENT: "
                                + assignment.teamSide() + ":" + assignment.position());
            }
        });
    }

    record MatchEngineV1Execution(
            MatchEngineV1Output output,
            MatchTimeline legacyTimeline,
            SimulationExecutionProvenance executionProvenance
    ) {
        MatchEngineV1Execution {
            Objects.requireNonNull(output, "output");
            Objects.requireNonNull(legacyTimeline, "legacyTimeline");
            Objects.requireNonNull(executionProvenance, "executionProvenance");
        }
    }
}
