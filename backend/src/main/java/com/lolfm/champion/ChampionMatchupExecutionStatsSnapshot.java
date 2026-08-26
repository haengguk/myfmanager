package com.lolfm.champion;

public record ChampionMatchupExecutionStatsSnapshot(
        int evaluations,
        int enabledEvaluations,
        int disabledEvaluations,
        int eligiblePairEvaluations,
        int noEligiblePairEvaluations,
        int totalPairApplications,
        int zeroContributionApplications,
        int nonZeroContributionApplications,
        int missingAssignmentErrors,
        int deadParticipantErrors,
        int nonParticipantErrors,
        int sameTeamPairErrors,
        int crossPositionErrors,
        int duplicateApplicationErrors,
        int staleStateErrors,
        int directRandomCalls,
        int exactZeroApplications,
        int neutralOnMismatch,
        int featureOffMismatch,
        int mirrorMismatch,
        int deadParticipantSkipped,
        int nonParticipantSkipped,
        int crossPositionSkipped,
        int sameTeamSkipped,
        double generatedBaseEdgeSum,
        double overrideAdjustmentSum,
        double finalMatchupEdgeSum,
        long eligiblePairCountTotal,
        long nonZeroPairCountTotal,
        double dilutionRatioSum,
        int dilutionSamples,
        double coverageRatioSum,
        double netDirectionalRetentionSum,
        int prototypeCoverageDilutionCount,
        int signCancellationCount,
        int unexpectedAggregationDilutionCount,
        java.util.List<Double> applicationEdges,
        int consumedApplicationCount,
        int nonZeroConsumedApplicationCount,
        int idempotentDuplicateConsumedApplicationCount,
        int duplicateConsumedApplicationErrors,
        int applicationBindingErrors,
        int staleAssignmentParticipantErrors,
        java.util.List<ChampionMatchupApplicationProvenance> applicationProvenance,
        java.util.List<ChampionMatchupStateConsumerProvenance> stateConsumerProvenance
) {
    public ChampionMatchupExecutionStatsSnapshot {
        applicationEdges = java.util.List.copyOf(applicationEdges);
        applicationProvenance = java.util.List.copyOf(applicationProvenance);
        stateConsumerProvenance = java.util.List.copyOf(stateConsumerProvenance);
    }
    public static ChampionMatchupExecutionStatsSnapshot empty() {
        return new ChampionMatchupExecutionStatsSnapshot(
                0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                0.0, 0.0, 0.0, 0L, 0L, 0.0, 0,
                0.0, 0.0, 0, 0, 0, java.util.List.of(),
                0, 0, 0, 0, 0, 0, java.util.List.of(), java.util.List.of());
    }
}
