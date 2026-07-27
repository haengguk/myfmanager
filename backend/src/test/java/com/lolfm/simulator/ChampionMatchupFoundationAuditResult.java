package com.lolfm.simulator;

import com.lolfm.champion.ChampionMatchupCatalog;
import java.util.List;
import java.util.Map;

record ChampionMatchupFoundationAuditResult(
        ChampionMatchupCatalog catalog,
        List<ChampionMatchupApplicationRow> applications,
        List<ChampionMatchupFullMatchRow> fullMatches,
        List<ChampionMatchupPairedRow> pairedMatches,
        List<ChampionMatchupMirrorRow> mirrorRows,
        Map<String, String> summary
) {
    ChampionMatchupFoundationAuditResult {
        applications = List.copyOf(applications);
        fullMatches = List.copyOf(fullMatches);
        pairedMatches = List.copyOf(pairedMatches);
        mirrorRows = List.copyOf(mirrorRows);
        summary = java.util.Collections.unmodifiableMap(
                new java.util.LinkedHashMap<>(summary));
    }
}
