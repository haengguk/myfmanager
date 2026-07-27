package com.lolfm.simulator;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lolfm.champion.*;
import com.lolfm.domain.Position;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;

public final class ChampionMatchupRuleEngineAudit {
    private static final Path OUTPUT =
            Path.of("build/reports/champion-matchup-rule-engine");
    private static final String AUDIT_VERSION =
            "phase-13c-2-rule-engine-prototype-v1";
    private static final int SEEDS = 200;

    private ChampionMatchupRuleEngineAudit() {
    }

    public static void main(String[] args) throws Exception {
        Files.createDirectories(OUTPUT);
        ChampionCatalog champions = new ChampionCatalog(new ObjectMapper());
        ChampionRoleMatchupProfileCatalog profiles =
                ChampionRoleMatchupProfileCatalog.prototype();
        ChampionMatchupRuleCatalog rules = new ChampionMatchupRuleCatalog();
        var build = GeneratedChampionMatchupCatalogFactory.prototype(champions);
        ChampionMatchupRuleEngineStaticWriter.write(OUTPUT, profiles, rules, build);

        MatchData matches = runMatches();
        ChampionMatchupRuleEngineCsv.records(
                OUTPUT.resolve("champion-matchup-full-match.csv"), matches.full());
        ChampionMatchupRuleEngineCsv.records(
                OUTPUT.resolve("champion-matchup-full-match-paired.csv"), matches.paired());
        MirrorResult mirror = writeMirror(matches);

        LinkedHashMap<String, Object> summary =
                ChampionMatchupRuleEngineVerdictEvaluator.evaluate(
                        AUDIT_VERSION, profiles, rules, build, matches.full(),
                        matches.paired(), mirror.warnings());
        ChampionMatchupRuleEngineCsv.summary(
                OUTPUT.resolve("champion-matchup-rule-engine-summary.csv"), summary);
        String log = summary.entrySet().stream()
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .collect(java.util.stream.Collectors.joining(System.lineSeparator()));
        Files.writeString(OUTPUT.resolve("champion-matchup-rule-engine-audit.log"),
                "PROTOTYPE_NOT_PRODUCTION" + System.lineSeparator() + log
                        + System.lineSeparator());
        System.out.println("Champion matchup rule-engine audit: "
                + summary.get("verdict"));
        System.out.println("Report: " + OUTPUT.toAbsolutePath());
    }

    static MatchData runMatches() {
        List<Job> jobs = new ArrayList<>();
        for (String skill : List.of("S0", "S1", "S3", "S5")) {
            for (SideOrientationFixture fixture :
                    SideOrientationFixtureFactory.focused(skill)) {
                for (SideOrientationFixture.Orientation direction :
                        SideOrientationFixture.Orientation.values()) {
                    for (int seed = 1; seed <= SEEDS; seed++) {
                        jobs.add(new Job(fixture, skill, direction, seed));
                    }
                }
            }
        }
        ChampionMatchupRuleEngineFullMatchExecutor executor =
                new ChampionMatchupRuleEngineFullMatchExecutor();
        List<ChampionMatchupRuleEngineFullMatchExecutor.PairResult> pairs =
                jobs.parallelStream().map(job -> executor.runPair(
                                job.fixture(), job.direction(), job.skill(), job.seed()))
                        .sorted(Comparator
                                .comparing((ChampionMatchupRuleEngineFullMatchExecutor.PairResult value) ->
                                        value.paired().lineupId())
                                .thenComparing(value -> value.paired().skillProfile())
                                .thenComparing(value -> value.paired().direction().name())
                                .thenComparingInt(value -> value.paired().seed()))
                        .toList();
        List<ChampionMatchupRuleEngineFullMatchRow> full = new ArrayList<>();
        List<ChampionMatchupRuleEnginePairedRow> paired = new ArrayList<>();
        for (var pair : pairs) {
            full.add(pair.off());
            full.add(pair.on());
            paired.add(pair.paired());
        }
        return new MatchData(full, paired);
    }

    private static MirrorResult writeMirror(MatchData matches) throws Exception {
        List<String[]> rows = new ArrayList<>();
        int warnings = 0;
        for (Position position : Position.values()) {
            String lineup = position.name();
            for (String skill : List.of("S0", "S1", "S3", "S5")) {
                double offOriginal = logicalTeamAWinRate(matches, lineup, skill,
                        ChampionMatchupMode.OFF, SideOrientationFixture.Orientation.ORIGINAL);
                double offMirror = logicalTeamAWinRate(matches, lineup, skill,
                        ChampionMatchupMode.OFF, SideOrientationFixture.Orientation.MIRRORED);
                double onOriginal = logicalTeamAWinRate(matches, lineup, skill,
                        ChampionMatchupMode.ON, SideOrientationFixture.Orientation.ORIGINAL);
                double onMirror = logicalTeamAWinRate(matches, lineup, skill,
                        ChampionMatchupMode.ON, SideOrientationFixture.Orientation.MIRRORED);
                double added = Math.abs(onOriginal - onMirror)
                        - Math.abs(offOriginal - offMirror);
                if (added > .015) warnings++;
                rows.add(new String[]{lineup, skill, String.valueOf(offOriginal),
                        String.valueOf(offMirror), String.valueOf(onOriginal),
                        String.valueOf(onMirror), String.valueOf(added),
                        String.valueOf(added > .015)});
            }
        }
        ChampionMatchupRuleEngineCsv.lines(
                OUTPUT.resolve("champion-matchup-mirror.csv"),
                new String[]{"pair", "skillProfile", "offOriginalLogicalWinRate",
                        "offMirrorLogicalWinRate", "onOriginalLogicalWinRate",
                        "onMirrorLogicalWinRate", "addedOrientationDifference",
                        "warning"}, rows);
        return new MirrorResult(warnings);
    }

    private static double logicalTeamAWinRate(
            MatchData matches, String lineup, String skill,
            ChampionMatchupMode mode, SideOrientationFixture.Orientation direction
    ) {
        List<ChampionMatchupRuleEngineFullMatchRow> rows = matches.full().stream()
                .filter(row -> row.lineupId().equals(lineup)
                        && row.skillProfile().equals(skill)
                        && row.matchupMode() == mode && row.direction() == direction)
                .toList();
        return rows.stream().filter(row ->
                row.winnerSide() == row.targetFavoredChampionSide()).count()
                / (double) rows.size();
    }

    private record Job(
            SideOrientationFixture fixture,
            String skill,
            SideOrientationFixture.Orientation direction,
            int seed
    ) {
    }
    record MatchData(
            List<ChampionMatchupRuleEngineFullMatchRow> full,
            List<ChampionMatchupRuleEnginePairedRow> paired
    ) {
    }
    private record MirrorResult(int warnings) { }
}
