package com.lolfm.simulator;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lolfm.champion.ChampionCatalog;
import com.lolfm.champion.ChampionMatchupMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class ThirtyChampionFullMatchAudit {
    private ThirtyChampionFullMatchAudit() {
    }

    static Result run() {
        ChampionCatalog champions = new ChampionCatalog(new ObjectMapper());
        List<Job> jobs = jobs(champions, 1, 100, "SCREENING");
        ThirtyChampionFullMatchExecutor executor =
                new ThirtyChampionFullMatchExecutor();
        List<ThirtyChampionFullMatchExecutor.PairResult> screening =
                execute(jobs, executor);
        List<CellDecision> decisions = decisions(screening);
        List<String> triggered = decisions.stream().filter(CellDecision::escalated)
                .map(CellDecision::cellId).toList();
        List<Job> escalationJobs = jobs(champions, 101, 500, "ESCALATION")
                .stream().filter(job -> triggered.contains(job.cellId())).toList();
        List<ThirtyChampionFullMatchExecutor.PairResult> escalation =
                execute(escalationJobs, executor);
        List<ThirtyChampionFullMatchExecutor.PairResult> all =
                new ArrayList<>(screening);
        all.addAll(escalation);
        var sample = GeneratedMatchupRoundRobinLineupFactory
                .create(champions, "S0").getFirst();
        var first = executor.replay(sample,
                SideOrientationFixture.Orientation.ORIGINAL, 1,
                ChampionMatchupMode.ON);
        var second = executor.replay(sample,
                SideOrientationFixture.Orientation.ORIGINAL, 1,
                ChampionMatchupMode.ON);
        if (!first.equals(second)) {
            throw new IllegalStateException("Same-mode same-seed replay mismatch");
        }
        return new Result(flatten(all), all.stream()
                .map(ThirtyChampionFullMatchExecutor.PairResult::paired).toList(),
                decisions, screening.size() * 2, screening.size(),
                escalation.size() * 2, escalation.size());
    }

    private static List<Job> jobs(ChampionCatalog champions, int firstSeed,
                                  int lastSeed, String phase) {
        List<Job> jobs = new ArrayList<>();
        for (String skill : List.of("S0", "S3")) {
            for (var lineup :
                    GeneratedMatchupRoundRobinLineupFactory.create(champions, skill)) {
                for (var orientation :
                        SideOrientationFixture.Orientation.values()) {
                    for (int seed = firstSeed; seed <= lastSeed; seed++) {
                        jobs.add(new Job(lineup, orientation, seed, phase));
                    }
                }
            }
        }
        return jobs;
    }

    private static List<ThirtyChampionFullMatchExecutor.PairResult> execute(
            List<Job> jobs, ThirtyChampionFullMatchExecutor executor
    ) {
        return jobs.parallelStream().map(job -> executor.runPair(
                        job.lineup(), job.orientation(), job.seed()))
                .sorted(Comparator.comparing(
                                (ThirtyChampionFullMatchExecutor.PairResult value) ->
                                        value.paired().lineupId())
                        .thenComparing(value -> value.paired().skillProfile())
                        .thenComparing(value ->
                                value.paired().orientation().name())
                        .thenComparingInt(value -> value.paired().seed()))
                .toList();
    }

    private static List<ThirtyChampionFullMatchExecutor.FullRow> flatten(
            List<ThirtyChampionFullMatchExecutor.PairResult> pairs
    ) {
        List<ThirtyChampionFullMatchExecutor.FullRow> result = new ArrayList<>();
        for (var pair : pairs) {
            result.add(pair.off());
            result.add(pair.on());
        }
        return List.copyOf(result);
    }

    private static List<CellDecision> decisions(
            List<ThirtyChampionFullMatchExecutor.PairResult> pairs
    ) {
        Map<String, List<ThirtyChampionFullMatchExecutor.PairResult>> groups =
                pairs.stream().collect(java.util.stream.Collectors.groupingBy(
                        value -> value.paired().lineupId() + "/"
                                + value.paired().skillProfile(),
                        LinkedHashMap::new,
                        java.util.stream.Collectors.toList()));
        return groups.entrySet().stream().map(entry -> {
            var values = entry.getValue();
            double flipRate = values.stream().filter(value ->
                    value.paired().winnerFlip()).count() / (double) values.size();
            double offDuration = values.stream().mapToInt(value ->
                    value.off().durationSeconds()).average().orElse(0);
            double durationDifference = values.stream().mapToInt(value ->
                    value.on().durationSeconds()
                            - value.off().durationSeconds()).average().orElse(0);
            double meanEdge = values.stream().mapToDouble(value ->
                    Math.abs(value.on().generatedMatchupEdgeMean()))
                    .average().orElse(0);
            double cancellationRate = values.stream().filter(value ->
                    value.on().signCancellationCount() > 0).count()
                    / (double) values.size();
            boolean mismatch = values.stream().anyMatch(value ->
                    value.paired().replayMismatch()
                            || value.paired().diagnosticsMismatch());
            long flips = values.stream().filter(value ->
                    value.paired().winnerFlip()).count();
            boolean oneSided = flips >= 5 && (values.stream().filter(value ->
                    "BLUE_TO_RED".equals(value.paired().flipDirection())).count() == 0
                    || values.stream().filter(value ->
                    "RED_TO_BLUE".equals(value.paired().flipDirection())).count() == 0);
            List<String> reasons = new ArrayList<>();
            if (flipRate > .03) reasons.add("WINNER_FLIP_RATE");
            if (offDuration > 0
                    && Math.abs(durationDifference) / offDuration > .05) {
                reasons.add("DURATION_DIFFERENCE");
            }
            if (meanEdge > .08) reasons.add("AVERAGE_EDGE");
            if (cancellationRate > .5) reasons.add("SIGN_CANCELLATION");
            if (mismatch) reasons.add("REPLAY_OR_DIAGNOSTICS");
            if (oneSided) reasons.add("ONE_SIDED_FLIPS");
            return new CellDecision(entry.getKey(), flipRate,
                    durationDifference, meanEdge, cancellationRate,
                    !reasons.isEmpty(), String.join("|", reasons),
                    reasons.isEmpty() ? 0 : 400);
        }).toList();
    }

    private record Job(GeneratedMatchupRoundRobinLineupFactory.Lineup lineup,
                       SideOrientationFixture.Orientation orientation,
                       int seed, String phase) {
        String cellId() {
            return lineup.lineupId() + "/" + lineup.skillProfile();
        }
    }

    record Result(List<ThirtyChampionFullMatchExecutor.FullRow> full,
                  List<ThirtyChampionFullMatchExecutor.PairedRow> paired,
                  List<CellDecision> decisions, int screeningFullRows,
                  int screeningPairs, int escalationFullRows,
                  int escalationPairs) {
    }
    record CellDecision(String cellId, double flipRate,
            double averageDurationDifference, double averageAbsoluteMatchupEdge,
            double signCancellationRate, boolean escalated,
            String reasons, int addedSeeds) { }
}
