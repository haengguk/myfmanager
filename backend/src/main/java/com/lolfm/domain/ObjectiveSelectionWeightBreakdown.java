package com.lolfm.domain;

public record ObjectiveSelectionWeightBreakdown(
        double aliveContribution,
        double goldContribution,
        double killContribution,
        double recentBigWinContribution,
        double recentAceContribution,
        double otherContribution,
        double totalExistingWeight
) {
    public static ObjectiveSelectionWeightBreakdown zero() {
        return new ObjectiveSelectionWeightBreakdown(0, 0, 0, 0, 0, 0, 0);
    }
}
