package com.lolfm.champion;

public final class ChampionMatchupExecutionStats {
    private int evaluations;
    private int enabledEvaluations;
    private int disabledEvaluations;
    private int eligiblePairEvaluations;
    private int noEligiblePairEvaluations;
    private int totalPairApplications;
    private int zeroContributionApplications;
    private int nonZeroContributionApplications;
    private int missingAssignmentErrors;
    private int deadParticipantErrors;
    private int nonParticipantErrors;
    private int sameTeamPairErrors;
    private int crossPositionErrors;
    private int duplicateApplicationErrors;
    private int staleStateErrors;
    private int directRandomCalls;
    private int exactZeroApplications;
    private int neutralOnMismatch;
    private int featureOffMismatch;
    private int mirrorMismatch;
    private int deadParticipantSkipped;
    private int nonParticipantSkipped;
    private int crossPositionSkipped;
    private int sameTeamSkipped;
    private double generatedBaseEdgeSum;
    private double overrideAdjustmentSum;
    private double finalMatchupEdgeSum;
    private long eligiblePairCountTotal;
    private long nonZeroPairCountTotal;
    private double dilutionRatioSum;
    private int dilutionSamples;
    private double coverageRatioSum;
    private double netDirectionalRetentionSum;
    private int prototypeCoverageDilutionCount;
    private int signCancellationCount;
    private int unexpectedAggregationDilutionCount;

    public void recordDisabledEvaluation() {
        evaluations++;
        disabledEvaluations++;
    }

    public void recordEnabledEvaluation(ChampionMatchupResult result) {
        evaluations++;
        enabledEvaluations++;
        if (result.eligiblePairCount() == 0) noEligiblePairEvaluations++;
        else eligiblePairEvaluations++;
        totalPairApplications += result.eligiblePairCount();
        for (ChampionMatchupPairContribution value : result.pairContributions()) {
            if (value.edge() == 0.0) {
                zeroContributionApplications++;
                exactZeroApplications++;
            } else {
                nonZeroContributionApplications++;
            }
        }
        missingAssignmentErrors += result.missingAssignmentCount();
        deadParticipantSkipped += result.deadParticipantSkipped();
        nonParticipantSkipped += result.nonParticipantSkipped();
        crossPositionSkipped += result.crossPositionSkipped();
        sameTeamSkipped += result.sameTeamSkipped();
        duplicateApplicationErrors += result.duplicateApplicationCount();
        generatedBaseEdgeSum += result.generatedMatchupBaseEdge();
        overrideAdjustmentSum += result.matchupOverrideAdjustment();
        finalMatchupEdgeSum += result.finalChampionMatchupEdge();
        eligiblePairCountTotal += result.matchupEligiblePairCount();
        nonZeroPairCountTotal += result.matchupNonZeroPairCount();
        if (result.matchupNonZeroPairCount() > 0) {
            ChampionMatchupDilutionMetrics metrics = result.matchupDilutionMetrics();
            dilutionRatioSum += result.matchupDilutionRatio();
            coverageRatioSum += metrics.coverageRatio();
            netDirectionalRetentionSum += metrics.netDirectionalRetention();
            dilutionSamples++;
            switch (metrics.classification()) {
                case PROTOTYPE_COVERAGE_DILUTION -> prototypeCoverageDilutionCount++;
                case SIGN_CANCELLATION -> signCancellationCount++;
                case UNEXPECTED_AGGREGATION_DILUTION -> unexpectedAggregationDilutionCount++;
                default -> { }
            }
        }
    }

    public void deadParticipantAppliedError() { deadParticipantErrors++; }
    public void nonParticipantAppliedError() { nonParticipantErrors++; }
    public void sameTeamPairAppliedError() { sameTeamPairErrors++; }
    public void crossPositionAppliedError() { crossPositionErrors++; }
    public void staleStateError() { staleStateErrors++; }
    public void directRandomCall() { directRandomCalls++; }
    public void neutralOnMismatch() { neutralOnMismatch++; }
    public void featureOffMismatch() { featureOffMismatch++; }
    public void mirrorMismatch() { mirrorMismatch++; }

    public ChampionMatchupExecutionStatsSnapshot snapshot() {
        return new ChampionMatchupExecutionStatsSnapshot(
                evaluations, enabledEvaluations, disabledEvaluations,
                eligiblePairEvaluations, noEligiblePairEvaluations, totalPairApplications,
                zeroContributionApplications, nonZeroContributionApplications,
                missingAssignmentErrors, deadParticipantErrors, nonParticipantErrors,
                sameTeamPairErrors, crossPositionErrors, duplicateApplicationErrors,
                staleStateErrors, directRandomCalls, exactZeroApplications,
                neutralOnMismatch, featureOffMismatch, mirrorMismatch,
                deadParticipantSkipped, nonParticipantSkipped, crossPositionSkipped,
                sameTeamSkipped, generatedBaseEdgeSum, overrideAdjustmentSum,
                finalMatchupEdgeSum, eligiblePairCountTotal, nonZeroPairCountTotal,
                dilutionRatioSum, dilutionSamples, coverageRatioSum,
                netDirectionalRetentionSum, prototypeCoverageDilutionCount,
                signCancellationCount, unexpectedAggregationDilutionCount);
    }
}
