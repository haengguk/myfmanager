package com.lolfm.simulator;

import java.util.List;
import java.util.Map;

record SideOrientationAuditResult(
        List<SideOrientationCellStatistics> statistics,
        Map<String, SideOrientationVerdictEvaluator.CellEvidence> evidence,
        int primaryGames,
        int secondaryScreeningGames,
        int secondaryEscalationGames,
        int escalatedCellCount,
        int integrityErrorCount,
        String verdict,
        SideOrientationCsvWriter.Counts artifactCounts
) {
    int totalGames() {
        return primaryGames + secondaryScreeningGames + secondaryEscalationGames;
    }
}
