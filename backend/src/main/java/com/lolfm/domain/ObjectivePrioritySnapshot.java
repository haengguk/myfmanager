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
        double redBaronPriority
) { }
