package com.lolfm.domain;

public record ObjectiveSelectionWeightBreakdown(
        double aliveContribution,
        double goldContribution,
        double killContribution,
        double recentBigWinContribution,
        double recentAceContribution,
        double otherContribution,
        double totalExistingWeight,
        double objectiveSecureContribution,
        double areaSetupContribution,
        double visionControlContribution
) {
    public ObjectiveSelectionWeightBreakdown(
            double aliveContribution, double goldContribution, double killContribution,
            double recentBigWinContribution, double recentAceContribution,
            double otherContribution, double totalExistingWeight) {
        this(aliveContribution, goldContribution, killContribution,
                recentBigWinContribution, recentAceContribution, otherContribution,
                totalExistingWeight, 0, 0, 0);
    }

    public static ObjectiveSelectionWeightBreakdown zero() {
        return new ObjectiveSelectionWeightBreakdown(0, 0, 0, 0, 0, 0, 0);
    }
}
