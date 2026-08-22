package com.lolfm.simulator;

import java.util.Objects;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/** Builds an application simulator only from a closed-set profile ID plus instrumentation. */
@Component
public final class ConfiguredMatchSimulatorFactory {
    private final TeamfightResolver teamfightResolver;
    private final EndGameEvaluator endGameEvaluator;
    private final SnapshotFactory snapshotFactory;
    private final ObjectiveResolver objectiveResolver;
    private final PostFightResolver postFightResolver;
    private final ObjectiveAttemptResolver objectiveAttemptResolver;
    private final StructureResolver structureResolver;
    private final PushResolver pushResolver;

    @Autowired
    public ConfiguredMatchSimulatorFactory(
            TeamfightResolver teamfightResolver,
            EndGameEvaluator endGameEvaluator,
            SnapshotFactory snapshotFactory,
            ObjectiveResolver objectiveResolver,
            PostFightResolver postFightResolver,
            ObjectiveAttemptResolver objectiveAttemptResolver,
            StructureResolver structureResolver,
            PushResolver pushResolver
    ) {
        this.teamfightResolver = Objects.requireNonNull(teamfightResolver, "teamfightResolver");
        this.endGameEvaluator = Objects.requireNonNull(endGameEvaluator, "endGameEvaluator");
        this.snapshotFactory = Objects.requireNonNull(snapshotFactory, "snapshotFactory");
        this.objectiveResolver = Objects.requireNonNull(objectiveResolver, "objectiveResolver");
        this.postFightResolver = Objects.requireNonNull(postFightResolver, "postFightResolver");
        this.objectiveAttemptResolver = Objects.requireNonNull(
                objectiveAttemptResolver, "objectiveAttemptResolver");
        this.structureResolver = Objects.requireNonNull(structureResolver, "structureResolver");
        this.pushResolver = Objects.requireNonNull(pushResolver, "pushResolver");
    }

    public MatchSimulator create(
            SimulationRuntimeProfileId profileId,
            SimulationInstrumentation instrumentation
    ) {
        ResolvedSimulationRuntimeProfile profile = SimulationRuntimeProfiles.resolve(profileId);
        Objects.requireNonNull(instrumentation, "instrumentation");
        return new MatchSimulator(
                teamfightResolver, endGameEvaluator, snapshotFactory, objectiveResolver,
                postFightResolver, objectiveAttemptResolver, structureResolver, pushResolver,
                profile.gameplayConfiguration().toSimulationOptions(instrumentation));
    }
}
