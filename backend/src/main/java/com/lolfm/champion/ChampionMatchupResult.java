package com.lolfm.champion;

import java.util.List;

public record ChampionMatchupResult(
        boolean enabled,
        int eligiblePairCount,
        double totalBeforeAverage,
        double matchupEdge,
        List<ChampionMatchupPairContribution> pairContributions,
        int missingAssignmentCount,
        int deadParticipantSkipped,
        int crossPositionSkipped,
        int nonParticipantSkipped,
        int sameTeamSkipped,
        int duplicateApplicationCount
) {
    public ChampionMatchupResult {
        pairContributions = List.copyOf(pairContributions);
        totalBeforeAverage = totalBeforeAverage == 0.0 ? 0.0 : totalBeforeAverage;
        matchupEdge = matchupEdge == 0.0 ? 0.0 : matchupEdge;
    }

    public static ChampionMatchupResult disabled() {
        return new ChampionMatchupResult(
                false, 0, 0.0, 0.0, List.of(), 0, 0, 0, 0, 0, 0);
    }

    public double generatedMatchupBaseEdge() { return matchupEdge; }
    public double matchupOverrideAdjustment() { return 0.0; }
    public double finalChampionMatchupEdge() { return matchupEdge; }
    public int matchupEligiblePairCount() { return eligiblePairCount; }
    public int matchupNonZeroPairCount() {
        return (int) pairContributions.stream().filter(value -> value.edge() != 0.0).count();
    }
    public double matchupRawEdgeSum() { return totalBeforeAverage; }
    public double matchupNonZeroEdgeSum() {
        return pairContributions.stream().mapToDouble(ChampionMatchupPairContribution::edge)
                .filter(value -> value != 0.0).sum();
    }
    public double matchupAverageEdge() { return matchupEdge; }
    public double matchupNonZeroAverageEdge() {
        int count = matchupNonZeroPairCount();
        return count == 0 ? 0.0 : matchupNonZeroEdgeSum() / count;
    }
    public ChampionMatchupDilutionMetrics matchupDilutionMetrics() {
        return ChampionMatchupDilutionMetrics.calculate(
                eligiblePairCount,
                pairContributions.stream().map(ChampionMatchupPairContribution::edge).toList());
    }
    /** @deprecated use the explicit coverage/cancellation metrics instead. */
    @Deprecated
    public double matchupDilutionRatio() {
        Double attenuation = matchupDilutionMetrics().coverageAttenuation();
        return attenuation == null ? 0.0 : attenuation;
    }
    public double matchupCoverageRatio() {
        Double value = matchupDilutionMetrics().coverageRatio();
        return value == null ? 0.0 : value;
    }
    public double matchupNetDirectionalRetention() {
        Double value = matchupDilutionMetrics().netDirectionalRetention();
        return value == null ? 0.0 : value;
    }
}
