package com.lolfm.composition;

import java.util.List;

/** Immutable snapshot of match-scoped composition diagnostics for tests/audits only. */
public record CompositionRuntimeDiagnostics(
        TeamCompositionGameplayMode mode,
        boolean initialized,
        long matchSeed,
        int lineupBuildCount,
        int teamCompositionAnalysisCount,
        int interactionAnalysisCount,
        int contextEdgeCount,
        int runtimeInteractionRecalculationCount,
        int resolverEvaluationCount,
        int triggerSuccessCount,
        int actualAttemptCount,
        int mappedActualAttemptCount,
        int unmappedActualAttemptCount,
        int shadowObservationCount,
        int evaluationOnlyObservationCount,
        int duplicateObservationCount,
        int multiContextAttemptCount,
        int conflictingPerspectiveCount,
        int duplicateApplicationPointCount,
        int gameplayApplicationCount,
        int nonZeroModifierCount,
        int directRandomCallCount,
        int compositionRandomDrawCount,
        List<CompositionShadowObservation> observations,
        List<CompositionContextRouting> routings,
        List<CompositionCandidateApplicationObservation> candidateApplications,
        List<CompositionLocalDecisionComparison> localDecisionComparisons,
        int deferredCandidateApplicationCount
) {
    public CompositionRuntimeDiagnostics {
        observations = List.copyOf(observations);
        routings = List.copyOf(routings);
        candidateApplications = List.copyOf(candidateApplications);
        localDecisionComparisons = List.copyOf(localDecisionComparisons);
    }
}
