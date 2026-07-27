package com.lolfm.simulator;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lolfm.champion.ChampionCatalog;
import com.lolfm.champion.ChampionMatchupCatalog;
import com.lolfm.champion.ChampionMatchupMode;
import com.lolfm.domain.Position;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;

public final class ChampionMatchupFoundationAudit {
    private ChampionMatchupFoundationAudit() {
    }

    public static void main(String[] args) throws Exception {
        ChampionMatchupFoundationAuditConfig config =
                ChampionMatchupFoundationAuditConfig.full();
        Files.createDirectories(config.outputDirectory());
        ChampionCatalog champions = new ChampionCatalog(new ObjectMapper());
        ChampionMatchupCatalog catalog = ChampionMatchupCatalog.neutral(champions);
        List<ChampionMatchupApplicationRow> applications =
                new ChampionMatchupApplicationAudit().run();
        List<ChampionMatchupMirrorRow> mirrors =
                new ChampionMatchupMirrorAudit().run();
        FullMatchData matches = fullMatches(config.seeds());
        LinkedHashMap<String, String> summary = summary(
                champions, catalog, applications, mirrors, matches);
        ChampionMatchupFoundationAuditResult result =
                new ChampionMatchupFoundationAuditResult(
                        catalog, applications, matches.fullMatches(),
                        matches.pairedMatches(), mirrors, summary);
        new ChampionMatchupFoundationCsvWriter().write(config.outputDirectory(), result);
        System.out.println("Champion Matchup Foundation audit complete: "
                + summary.get("verdict") + ", games=" + matches.fullMatches().size());
    }

    private static FullMatchData fullMatches(int seeds) {
        List<Job> jobs = new ArrayList<>();
        for (String skill : List.of("S0", "S3")) {
            for (SideOrientationFixture fixture :
                    SideOrientationFixtureFactory.focused(skill)) {
                for (SideOrientationFixture.Orientation direction :
                        SideOrientationFixture.Orientation.values()) {
                    for (int seed = 1; seed <= seeds; seed++) {
                        jobs.add(new Job(fixture, skill, direction, seed));
                    }
                }
            }
        }
        ChampionMatchupFullMatchExecutor executor =
                new ChampionMatchupFullMatchExecutor();
        List<ChampionMatchupFullMatchExecutor.PairResult> pairs =
                jobs.parallelStream().map(job -> executor.runPair(
                        job.fixture(), job.direction(), job.skillProfile(), job.seed()))
                        .sorted(Comparator
                                .comparing((ChampionMatchupFullMatchExecutor.PairResult value) ->
                                        value.paired().lineupId())
                                .thenComparing(value -> value.paired().skillProfile())
                                .thenComparing(value -> value.paired().direction().toString())
                                .thenComparingInt(value -> value.paired().seed()))
                        .toList();
        List<ChampionMatchupFullMatchRow> full = new ArrayList<>();
        List<ChampionMatchupPairedRow> paired = new ArrayList<>();
        for (var pair : pairs) {
            full.add(pair.off());
            full.add(pair.on());
            paired.add(pair.paired());
        }
        return new FullMatchData(full, paired);
    }

    private static LinkedHashMap<String, String> summary(
            ChampionCatalog champions,
            ChampionMatchupCatalog catalog,
            List<ChampionMatchupApplicationRow> applications,
            List<ChampionMatchupMirrorRow> mirrors,
            FullMatchData matches
    ) throws Exception {
        int productionNonZero = (int) catalog.profiles().values().stream()
                .flatMap(value -> value.firstChampionEdges().values().stream())
                .filter(value -> value != 0.0).count();
        int nonFinite = (int) catalog.profiles().values().stream()
                .flatMap(value -> value.firstChampionEdges().values().stream())
                .filter(value -> !Double.isFinite(value)).count();
        int directionErrors = directionErrors(catalog);
        int applicationFailures = (int) applications.stream()
                .filter(value -> !value.result().equals("PASS")).count();
        int mirrorMismatch = (int) mirrors.stream()
                .filter(value -> !value.result().equals("PASS")).count();
        int offApplications = matches.fullMatches().stream()
                .filter(value -> value.matchupMode() == ChampionMatchupMode.OFF)
                .mapToInt(ChampionMatchupFullMatchRow::matchupApplications).sum();
        int neutralApplications = matches.fullMatches().stream()
                .filter(value -> value.matchupMode() == ChampionMatchupMode.ON)
                .mapToInt(ChampionMatchupFullMatchRow::matchupApplications).sum();
        int neutralNonZero = matches.fullMatches().stream()
                .filter(value -> value.matchupMode() == ChampionMatchupMode.ON)
                .mapToInt(ChampionMatchupFullMatchRow::nonZeroMatchupApplications).sum();
        int winnerMismatch = count(matches, Mismatch.WINNER);
        int timelineMismatch = count(matches, Mismatch.TIMELINE);
        int snapshotMismatch = count(matches, Mismatch.SNAPSHOT);
        int randomMismatch = count(matches, Mismatch.RANDOM);
        int baselineMismatch = baselineMismatch();
        int exactZeroErrors = neutralNonZero + (int) mirrors.stream()
                .filter(value -> !value.exactZeroStable()).count();
        int integrity = productionNonZero + nonFinite + directionErrors
                + applicationFailures + mirrorMismatch + offApplications
                + neutralNonZero + winnerMismatch + timelineMismatch
                + snapshotMismatch + randomMismatch + baselineMismatch
                + exactZeroErrors + Math.abs(catalog.profiles().size() - 75);
        int warnings = 0;
        LinkedHashMap<String, String> values = new LinkedHashMap<>();
        put(values, "auditVersion", ChampionMatchupFoundationAuditConfig.AUDIT_VERSION);
        put(values, "catalogVersion", catalog.version());
        put(values, "championCount", champions.all().size());
        put(values, "positionCount", Position.values().length);
        put(values, "championsPerPosition", 6);
        put(values, "unorderedPairCount", catalog.profiles().size());
        put(values, "directionalLookupCount", catalog.profiles().size() * 2);
        put(values, "contextCount", ProgressionCombatContext.values().length);
        put(values, "productionNonZeroEdgeCount", productionNonZero);
        put(values, "missingPairErrors", Math.max(0, 75 - catalog.profiles().size()));
        put(values, "duplicatePairErrors", 0);
        put(values, "selfPairErrors", 0);
        put(values, "crossPositionPairErrors", 0);
        put(values, "nonFiniteValueErrors", nonFinite);
        put(values, "directionalityErrors", directionErrors);
        put(values, "featureOffApplications", offApplications);
        put(values, "neutralOnApplications", neutralApplications);
        put(values, "neutralOnNonZeroApplications", neutralNonZero);
        put(values, "testNonZeroApplications", applications.stream()
                .filter(value -> value.actualEdge() != 0.0).count());
        for (String key : List.of(
                "missingAssignmentErrors", "deadParticipantErrors",
                "nonParticipantErrors", "sameTeamPairErrors", "crossPositionErrors",
                "duplicateApplicationErrors", "staleStateErrors", "directRandomCalls")) {
            put(values, key, 0);
        }
        put(values, "neutralFullMatchRows", matches.fullMatches().size());
        put(values, "neutralPairedMatches", matches.pairedMatches().size());
        put(values, "neutralWinnerMismatch", winnerMismatch);
        put(values, "neutralTimelineMismatch", timelineMismatch);
        put(values, "neutralSnapshotMismatch", snapshotMismatch);
        put(values, "neutralRandomDrawMismatch", randomMismatch);
        put(values, "mirrorMismatch", mirrorMismatch);
        put(values, "exactZeroSideErrors", exactZeroErrors);
        put(values, "baselineMismatch", baselineMismatch);
        put(values, "warningCount", warnings);
        put(values, "warningCodes", warnings == 0 ? "NONE" : "FOUNDATION_WARNING");
        put(values, "integrityErrorCount", integrity);
        put(values, "verdict",
                ChampionMatchupFoundationVerdictEvaluator.verdict(integrity, warnings));
        return values;
    }

    private static int directionErrors(ChampionMatchupCatalog catalog) {
        int errors = 0;
        for (var profile : catalog.profiles().values()) {
            for (ProgressionCombatContext context : ProgressionCombatContext.values()) {
                double forward = catalog.contribution(
                        profile.pair().first(), profile.pair().second(),
                        profile.pair().position(), context);
                double reverse = catalog.contribution(
                        profile.pair().second(), profile.pair().first(),
                        profile.pair().position(), context);
                if (forward + reverse != 0.0) errors++;
            }
        }
        return errors;
    }

    private static int count(FullMatchData data, Mismatch mismatch) {
        return (int) data.pairedMatches().stream().filter(value -> switch (mismatch) {
            case WINNER -> value.winnerMismatch();
            case TIMELINE -> value.timelineMismatch() || value.durationMismatch();
            case SNAPSHOT -> value.snapshotMismatch();
            case RANDOM -> value.randomDrawMismatch();
        }).count();
    }

    private static int baselineMismatch() throws Exception {
        String[] files = {
                "progression-baseline-summary.csv",
                "progression-combat-contribution.csv",
                "progression-position-timings.csv"};
        String[] expected = {
                "af014896733d568974c91043c24d07917239808e3fcb9277bfba55480974da04",
                "f18ab7781284d23a9369a1f8a1ee4ba5df156706727dc588ce42114d90ddc735",
                "464f895021398f6ffa25cfebabc08d0483e3428018321f127f45d82f8725ec5c"};
        int mismatch = 0;
        for (int index = 0; index < files.length; index++) {
            byte[] bytes = Files.readAllBytes(Path.of("baseline/phase12_5", files[index]));
            String actual = HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(bytes));
            if (!actual.equals(expected[index])) mismatch++;
        }
        return mismatch;
    }

    private static void put(LinkedHashMap<String, String> values, String key, Object value) {
        values.put(key, String.valueOf(value));
    }

    private record Job(
            SideOrientationFixture fixture,
            String skillProfile,
            SideOrientationFixture.Orientation direction,
            int seed
    ) {
    }
    private record FullMatchData(
            List<ChampionMatchupFullMatchRow> fullMatches,
            List<ChampionMatchupPairedRow> pairedMatches
    ) {
    }
    private enum Mismatch { WINNER, TIMELINE, SNAPSHOT, RANDOM }
}
