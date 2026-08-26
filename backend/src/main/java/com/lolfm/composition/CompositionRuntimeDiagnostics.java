package com.lolfm.composition;

import java.util.List;

/** Immutable snapshot of match-scoped composition diagnostics for tests/audits only. */
public record CompositionRuntimeDiagnostics(
        String schemaVersion,
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
        int deferredCandidateApplicationCount,
        boolean auditSemanticsEnabled,
        String semanticsBlueprintVersion,
        String semanticsBlueprintHash,
        int diagnosticCaseIndex,
        boolean keySpecificCandidateAuditEnabled,
        String keySpecificCandidateVersion,
        String keySpecificCandidateHash,
        int freshHoldoutCaseIndex,
        List<CompositionWinnerChannelObservation> winnerChannelObservations,
        List<FightGradeDecisionDiagnostic> fightGradeDiagnostics,
        List<BaseDefenseRoleRoutingDiagnostic> baseDefenseRoleRoutings,
        List<CompositionWinnerDecisionProvenance> winnerDecisionProvenance,
        int modifierCalculatedCount,
        int modifierConsumedCount,
        int localDecisionChangedCount,
        int localDecisionUnchangedCount,
        int publicActionBindingCount,
        int existingNonScalarEffectConsumedCount,
        int totalCompositionEffectApplicationCount,
        List<CompositionApplicationProvenance> applicationProvenance
) {
    public static final String SCHEMA_VERSION = "COMPOSITION_RUNTIME_DIAGNOSTICS_V4";

    public CompositionRuntimeDiagnostics {
        if (!SCHEMA_VERSION.equals(schemaVersion)) {
            throw new IllegalArgumentException("Unsupported composition diagnostic schema: " + schemaVersion);
        }
        observations = List.copyOf(observations);
        routings = List.copyOf(routings);
        candidateApplications = List.copyOf(candidateApplications);
        localDecisionComparisons = List.copyOf(localDecisionComparisons);
        winnerChannelObservations = List.copyOf(winnerChannelObservations);
        fightGradeDiagnostics = List.copyOf(fightGradeDiagnostics);
        baseDefenseRoleRoutings = List.copyOf(baseDefenseRoleRoutings);
        winnerDecisionProvenance = List.copyOf(winnerDecisionProvenance);
        applicationProvenance = List.copyOf(applicationProvenance);
    }
}
