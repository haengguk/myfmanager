package com.lolfm.simulator;

import java.nio.file.Path;

record ChampionMatchupFoundationAuditConfig(int seeds, Path outputDirectory) {
    static final String AUDIT_VERSION = "phase-13c1";

    static ChampionMatchupFoundationAuditConfig full() {
        return new ChampionMatchupFoundationAuditConfig(
                200, Path.of("build/reports/champion-matchup-foundation"));
    }

    ChampionMatchupFoundationAuditConfig {
        if (seeds < 1) throw new IllegalArgumentException("seeds must be positive");
    }
}
