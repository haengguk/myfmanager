package com.lolfm.simulator;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

record SideOrientationCellStatistics(
        String auditGroup,
        String fixture,
        String mode,
        String skillProfile,
        int gamesPerOrientation,
        int blueWins,
        double blueWinRate,
        double blueWilsonLow,
        double blueWilsonHigh,
        double logicalTeamAOriginalWinRate,
        double logicalTeamAMirroredWinRate,
        double orientationDifference,
        int teamAWinsBoth,
        int teamBWinsBoth,
        int originalOnly,
        int mirroredOnly,
        int pairedDiscordant,
        double discordanceRate,
        double rawPValue,
        double holmAdjustedPValue,
        double effectSizePercentagePoint,
        String classification
) {
    static SideOrientationCellStatistics calculate(List<SideOrientationMatchRow> rows) {
        if (rows.isEmpty()) throw new IllegalArgumentException("Empty cell");
        SideOrientationMatchRow first = rows.getFirst();
        Map<Integer, SideOrientationMatchRow> original = bySeed(rows,
                SideOrientationFixture.Orientation.ORIGINAL);
        Map<Integer, SideOrientationMatchRow> mirrored = bySeed(rows,
                SideOrientationFixture.Orientation.MIRRORED);
        if (!original.keySet().equals(mirrored.keySet())) {
            throw new IllegalStateException("Orientation seed sets differ");
        }
        int teamAWinsBoth = 0;
        int teamBWinsBoth = 0;
        int originalOnly = 0;
        int mirroredOnly = 0;
        int originalTeamAWins = 0;
        int mirroredTeamAWins = 0;
        for (int seed : original.keySet()) {
            boolean aOriginal = original.get(seed).winnerLogicalTeam()
                    == SideOrientationFixture.LogicalTeamId.TEAM_A;
            boolean aMirrored = mirrored.get(seed).winnerLogicalTeam()
                    == SideOrientationFixture.LogicalTeamId.TEAM_A;
            if (aOriginal) originalTeamAWins++;
            if (aMirrored) mirroredTeamAWins++;
            if (aOriginal && aMirrored) teamAWinsBoth++;
            else if (!aOriginal && !aMirrored) teamBWinsBoth++;
            else if (aOriginal) originalOnly++;
            else mirroredOnly++;
        }
        int gamesPerOrientation = original.size();
        int blueWins = (int) rows.stream().filter(r -> r.winnerSide() == TeamSide.BLUE).count();
        var interval = SideOrientationStatistics.wilson(blueWins, rows.size());
        double originalRate = originalTeamAWins / (double) gamesPerOrientation;
        double mirroredRate = mirroredTeamAWins / (double) gamesPerOrientation;
        int discordant = originalOnly + mirroredOnly;
        double difference = Math.abs(originalRate - mirroredRate);
        return new SideOrientationCellStatistics(
                first.auditGroup(), first.fixtureId(), first.mode(), first.skillProfile(),
                gamesPerOrientation, blueWins, blueWins / (double) rows.size(),
                interval.low(), interval.high(), originalRate, mirroredRate, difference,
                teamAWinsBoth, teamBWinsBoth, originalOnly, mirroredOnly, discordant,
                discordant / (double) gamesPerOrientation,
                SideOrientationStatistics.mcnemarExact(originalOnly, mirroredOnly),
                Double.NaN, difference * 100.0, "UNCLASSIFIED");
    }

    SideOrientationCellStatistics withAdjusted(double adjusted, String value) {
        return new SideOrientationCellStatistics(
                auditGroup, fixture, mode, skillProfile, gamesPerOrientation, blueWins,
                blueWinRate, blueWilsonLow, blueWilsonHigh, logicalTeamAOriginalWinRate,
                logicalTeamAMirroredWinRate, orientationDifference, teamAWinsBoth,
                teamBWinsBoth, originalOnly, mirroredOnly, pairedDiscordant, discordanceRate,
                rawPValue, adjusted, effectSizePercentagePoint, value);
    }

    private static Map<Integer, SideOrientationMatchRow> bySeed(
            List<SideOrientationMatchRow> rows,
            SideOrientationFixture.Orientation orientation
    ) {
        Map<Integer, SideOrientationMatchRow> result = new HashMap<>();
        rows.stream().filter(row -> row.orientation() == orientation)
                .forEach(row -> {
                    if (result.put(row.seed(), row) != null) {
                        throw new IllegalStateException("Duplicate orientation seed");
                    }
                });
        return result;
    }
}
