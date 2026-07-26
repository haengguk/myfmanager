package com.lolfm.simulator;

import java.nio.file.Path;
import java.util.Set;

record SideOrientationAuditConfig(
        int primarySeeds,
        int screeningSeeds,
        int escalationSeeds,
        Path outputDirectory
) {
    static final String AUDIT_VERSION = "phase-13b6";
    static final Set<Integer> FIXED_TRACE_SEEDS = Set.of(1, 2, 3, 7, 42, 100);

    static SideOrientationAuditConfig full() {
        return new SideOrientationAuditConfig(5_000, 500, 3_000, Path.of("."));
    }

    SideOrientationAuditConfig {
        if (primarySeeds < 1 || screeningSeeds < 1 || escalationSeeds < screeningSeeds) {
            throw new IllegalArgumentException("Invalid audit seed bounds");
        }
    }
}
