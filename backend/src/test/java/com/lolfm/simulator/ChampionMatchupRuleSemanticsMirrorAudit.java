package com.lolfm.simulator;

import com.lolfm.champion.ChampionMatchupMode;
import com.lolfm.domain.Position;
import java.util.ArrayList;
import java.util.List;

final class ChampionMatchupRuleSemanticsMirrorAudit {
    private ChampionMatchupRuleSemanticsMirrorAudit() {
    }

    static List<MirrorRow> calculate(
            ChampionMatchupRuleEngineAudit.MatchData matches
    ) {
        List<MirrorRow> rows = new ArrayList<>(20);
        for (Position position : Position.values()) {
            for (String skill : List.of("S0", "S1", "S3", "S5")) {
                double offOriginal = rate(matches, position.name(), skill,
                        ChampionMatchupMode.OFF,
                        SideOrientationFixture.Orientation.ORIGINAL);
                double offMirror = rate(matches, position.name(), skill,
                        ChampionMatchupMode.OFF,
                        SideOrientationFixture.Orientation.MIRRORED);
                double onOriginal = rate(matches, position.name(), skill,
                        ChampionMatchupMode.ON,
                        SideOrientationFixture.Orientation.ORIGINAL);
                double onMirror = rate(matches, position.name(), skill,
                        ChampionMatchupMode.ON,
                        SideOrientationFixture.Orientation.MIRRORED);
                double offDifference = Math.abs(offOriginal - offMirror);
                double onDifference = Math.abs(onOriginal - onMirror);
                double added = onDifference - offDifference;
                rows.add(new MirrorRow(
                        position.name(), skill, offOriginal, offMirror,
                        onOriginal, onMirror, offDifference, onDifference,
                        added, added > .015,
                        "PAIRED_WITH_PHASE_13B6_READY"));
            }
        }
        return List.copyOf(rows);
    }

    private static double rate(
            ChampionMatchupRuleEngineAudit.MatchData matches,
            String lineup,
            String skill,
            ChampionMatchupMode mode,
            SideOrientationFixture.Orientation orientation
    ) {
        List<ChampionMatchupRuleEngineFullMatchRow> rows =
                matches.full().stream().filter(row ->
                        row.lineupId().equals(lineup)
                                && row.skillProfile().equals(skill)
                                && row.matchupMode() == mode
                                && row.direction() == orientation).toList();
        return rows.stream().filter(row ->
                row.winnerSide() == row.targetFavoredChampionSide()).count()
                / (double) rows.size();
    }

    record MirrorRow(
            String pair,
            String skillProfile,
            double offOriginalLogicalWinRate,
            double offMirrorLogicalWinRate,
            double onOriginalLogicalWinRate,
            double onMirrorLogicalWinRate,
            double offOrientationDifference,
            double onOrientationDifference,
            double addedOrientationDifference,
            boolean warning,
            String sideOrientationReference
    ) {
    }
}
