package com.lolfm.domain;

public record ObjectiveFightSkillImpactData(
        double blueAreaSetupScore,
        double redAreaSetupScore,
        double blueVisionControlScore,
        double redVisionControlScore,
        double blueSetupContribution,
        double redSetupContribution,
        double setupEdgeContribution
) {
    public ObjectiveFightSkillImpactData {
        double[] values = {blueAreaSetupScore, redAreaSetupScore, blueVisionControlScore,
                redVisionControlScore, blueSetupContribution, redSetupContribution,
                setupEdgeContribution};
        for (double value : values) {
            if (!Double.isFinite(value)) {
                throw new IllegalArgumentException("objective fight skill impact must be finite");
            }
        }
        if (Math.abs(setupEdgeContribution
                - (blueSetupContribution - redSetupContribution)) > 1e-9) {
            throw new IllegalArgumentException("objective fight setup edge mismatch");
        }
    }
}
