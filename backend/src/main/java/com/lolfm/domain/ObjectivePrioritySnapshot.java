package com.lolfm.domain;

public record ObjectivePrioritySnapshot(
        boolean enabled,
        double dragonLanePressureScore,
        double dragonRecentControl,
        double dragonSignedPriority,
        double blueDragonPriority,
        double redDragonPriority,
        double baronLanePressureScore,
        double baronRecentControl,
        double baronSignedPriority,
        double blueBaronPriority,
        double redBaronPriority,
        double dragonMacroSetupControl,
        double baronMacroSetupControl
) {
    public ObjectivePrioritySnapshot(boolean enabled,
                                     double dragonLanePressureScore,
                                     double dragonRecentControl,
                                     double dragonSignedPriority,
                                     double blueDragonPriority,
                                     double redDragonPriority,
                                     double baronLanePressureScore,
                                     double baronRecentControl,
                                     double baronSignedPriority,
                                     double blueBaronPriority,
                                     double redBaronPriority) {
        this(enabled, dragonLanePressureScore, dragonRecentControl, dragonSignedPriority,
                blueDragonPriority, redDragonPriority, baronLanePressureScore, baronRecentControl,
                baronSignedPriority, blueBaronPriority, redBaronPriority, 0, 0);
    }
}
