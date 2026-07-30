package com.lolfm.simulator;

import com.lolfm.champion.ChampionMatchupTrait;
import com.lolfm.champion.ThirtyChampionGeneratedCatalog;
import com.lolfm.champion.ThirtyChampionRoleProfiles;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

final class ThirtyChampionMatchupCsvWriter {
    private ThirtyChampionMatchupCsvWriter() {
    }

    static void profiles(Path output,
                         List<ThirtyChampionRoleProfiles.Entry> entries)
            throws IOException {
        List<String[]> rows = new ArrayList<>();
        for (var entry : entries) {
            List<String> values = new ArrayList<>();
            values.add(entry.profile().roleKey().championId().value());
            values.add(entry.profile().roleKey().position().name());
            for (ChampionMatchupTrait trait : ChampionMatchupTrait.values()) {
                values.add(String.valueOf(entry.profile().trait(trait)));
            }
            var samples = entry.profile().traits().values().stream()
                    .map(Number::doubleValue).toList();
            values.add(String.valueOf(samples.stream().mapToDouble(Double::doubleValue).sum()));
            values.add(String.valueOf(samples.stream().mapToDouble(Double::doubleValue).average().orElseThrow()));
            values.add(String.valueOf(ThirtyChampionStatistics.quantile(samples, .5)));
            values.add(String.valueOf(samples.stream().mapToDouble(Double::doubleValue).min().orElseThrow()));
            values.add(String.valueOf(samples.stream().mapToDouble(Double::doubleValue).max().orElseThrow()));
            values.add(String.valueOf(ThirtyChampionStatistics.standardDeviation(samples)));
            values.add(String.valueOf(samples.stream().filter(value -> value >= 17).count()));
            values.add(String.valueOf(samples.stream().filter(value -> value <= 4).count()));
            values.add(String.valueOf(samples.stream().filter(value -> value >= 9 && value <= 12).count()));
            values.add(entry.profileSource().name());
            values.add(String.valueOf(entry.candidateOnly()));
            rows.add(values.toArray(String[]::new));
        }
        List<String> header = new ArrayList<>(List.of("champion", "position"));
        for (ChampionMatchupTrait trait : ChampionMatchupTrait.values()) {
            header.add(trait.name());
        }
        header.addAll(List.of("traitSum", "traitMean", "traitMedian",
                "traitMin", "traitMax", "traitStandardDeviation",
                "countAtLeast17", "countAtMost4", "countBetween9And12"));
        header.add("profileSource");
        header.add("candidateOnly");
        ChampionMatchupRuleEngineCsv.lines(output,
                header.toArray(String[]::new), rows);
    }

    static void rationales(Path output,
                           List<ThirtyChampionRoleProfiles.Entry> entries)
            throws IOException {
        List<String[]> rows = entries.stream().map(entry -> new String[]{
                entry.profile().roleKey().championId().value(),
                entry.profile().roleKey().position().name(),
                entry.primaryStrengthTraits().toString(),
                entry.primaryWeaknessTraits().toString(),
                entry.kitInteractionSummary(), entry.profileSource().name(),
                String.valueOf(entry.candidateOnly())
        }).toList();
        ChampionMatchupRuleEngineCsv.lines(output, new String[]{
                "champion", "position", "primaryStrengthTraits",
                "primaryWeaknessTraits", "kitInteractionSummary",
                "profileSource", "candidateOnly"}, rows);
    }

    static void lineups(Path output,
                        List<GeneratedMatchupRoundRobinLineupFactory.Lineup> lineups)
            throws IOException {
        ChampionMatchupRuleEngineCsv.lines(output,
                new String[]{"lineupId", "scheduleIndex", "coveredPairs",
                        "pairCount", "fullTeamMirrorSupported"},
                lineups.stream().map(lineup -> new String[]{
                        lineup.lineupId(), String.valueOf(lineup.scheduleIndex()),
                        lineup.coveredPairs(), "5", "true"}).toList());
    }

    static void matrix(Path output,
                       List<ThirtyChampionGeneratedCatalog.MatrixRow> rows)
            throws IOException {
        ChampionMatchupRuleEngineCsv.records(output, rows);
    }

    static void mirror(Path output,
                       List<ThirtyChampionFullMatchExecutor.FullRow> full)
            throws IOException {
        List<String[]> rows = new ArrayList<>();
        for (String lineup : full.stream().map(row -> row.lineupId())
                .distinct().sorted().toList()) {
            for (String skill : List.of("S0", "S3")) {
                double offOriginal = logicalRate(full, lineup, skill,
                        com.lolfm.champion.ChampionMatchupMode.OFF,
                        SideOrientationFixture.Orientation.ORIGINAL);
                double offMirror = logicalRate(full, lineup, skill,
                        com.lolfm.champion.ChampionMatchupMode.OFF,
                        SideOrientationFixture.Orientation.MIRRORED);
                double onOriginal = logicalRate(full, lineup, skill,
                        com.lolfm.champion.ChampionMatchupMode.ON,
                        SideOrientationFixture.Orientation.ORIGINAL);
                double onMirror = logicalRate(full, lineup, skill,
                        com.lolfm.champion.ChampionMatchupMode.ON,
                        SideOrientationFixture.Orientation.MIRRORED);
                double added = Math.abs(onOriginal - onMirror)
                        - Math.abs(offOriginal - offMirror);
                rows.add(new String[]{lineup, skill,
                        String.valueOf(offOriginal), String.valueOf(offMirror),
                        String.valueOf(onOriginal), String.valueOf(onMirror),
                        String.valueOf(added), String.valueOf(added > .015)});
            }
        }
        ChampionMatchupRuleEngineCsv.lines(output, new String[]{
                "lineupId", "skillProfile", "offOriginalLogicalTeamAWinRate",
                "offMirrorLogicalTeamAWinRate", "onOriginalLogicalTeamAWinRate",
                "onMirrorLogicalTeamAWinRate", "addedOrientationDifference",
                "matchupAddedSideWarning"}, rows);
    }

    private static double logicalRate(
            List<ThirtyChampionFullMatchExecutor.FullRow> full,
            String lineup, String skill,
            com.lolfm.champion.ChampionMatchupMode mode,
            SideOrientationFixture.Orientation orientation
    ) {
        List<ThirtyChampionFullMatchExecutor.FullRow> rows = full.stream()
                .filter(row -> row.lineupId().equals(lineup)
                        && row.skillProfile().equals(skill)
                        && row.matchupMode() == mode
                        && row.orientation() == orientation).toList();
        return rows.stream().filter(row ->
                row.winnerLogicalTeam()
                        == SideOrientationFixture.LogicalTeamId.TEAM_A).count()
                / (double) rows.size();
    }
}
