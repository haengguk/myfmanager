package com.lolfm.simulator;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lolfm.champion.ChampionCatalog;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class ChampionPairInteractionFullMatchAudit {
    private ChampionPairInteractionFullMatchAudit() {
    }

    static Result run() {
        ChampionCatalog champions = new ChampionCatalog(new ObjectMapper());
        ChampionPairInteractionFullMatchExecutor executor =
                new ChampionPairInteractionFullMatchExecutor();
        List<Job> screeningJobs = jobs(champions, 1, 200);
        List<ChampionPairInteractionFullMatchExecutor.TripleResult> screening =
                execute(screeningJobs, executor);
        List<CellDecision> decisions = decisions(screening);
        List<String> escalatedCells = decisions.stream()
                .filter(CellDecision::escalated).map(CellDecision::cellId).toList();
        List<Job> escalationJobs = jobs(champions, 201, 500).stream()
                .filter(job -> escalatedCells.contains(job.cellId())).toList();
        List<ChampionPairInteractionFullMatchExecutor.TripleResult> escalation =
                execute(escalationJobs, executor);
        List<ChampionPairInteractionFullMatchExecutor.TripleResult> all =
                new ArrayList<>(screening);
        all.addAll(escalation);
        var sample = GeneratedMatchupRoundRobinLineupFactory
                .create(champions, "S0").getFirst();
        var replayA = executor.replay(sample,
                SideOrientationFixture.Orientation.ORIGINAL, 1,
                ChampionPairInteractionFullMatchExecutor.FormulaMode
                        .PAIR_INTERACTION_CANDIDATE);
        var replayB = executor.replay(sample,
                SideOrientationFixture.Orientation.ORIGINAL, 1,
                ChampionPairInteractionFullMatchExecutor.FormulaMode
                        .PAIR_INTERACTION_CANDIDATE);
        if (!replayA.equals(replayB)) {
            throw new IllegalStateException("Interaction replay mismatch");
        }
        return new Result(full(all), paired(all), counterfactual(all),
                mirror(full(all)), decisions, screening.size() * 3,
                screening.size() * 3, escalation.size() * 3,
                escalation.size() * 3);
    }

    private static List<Job> jobs(ChampionCatalog champions,
                                  int firstSeed, int lastSeed) {
        List<Job> jobs = new ArrayList<>();
        for (String skill : List.of("S0", "S3")) {
            for (var lineup :
                    GeneratedMatchupRoundRobinLineupFactory.create(champions, skill)) {
                for (var orientation :
                        SideOrientationFixture.Orientation.values()) {
                    for (int seed = firstSeed; seed <= lastSeed; seed++) {
                        jobs.add(new Job(lineup, orientation, seed));
                    }
                }
            }
        }
        return List.copyOf(jobs);
    }

    private static List<ChampionPairInteractionFullMatchExecutor.TripleResult> execute(
            List<Job> jobs, ChampionPairInteractionFullMatchExecutor executor
    ) {
        return jobs.parallelStream().map(job -> executor.run(
                        job.lineup(), job.orientation(), job.seed()))
                .sorted(Comparator.comparing(
                                (ChampionPairInteractionFullMatchExecutor.TripleResult value) ->
                                        value.off().lineupId())
                        .thenComparing(value -> value.off().skillProfile())
                        .thenComparing(value -> value.off().orientation().name())
                        .thenComparingInt(value -> value.off().seed()))
                .toList();
    }

    private static List<ChampionPairInteractionFullMatchExecutor.FullRow> full(
            List<ChampionPairInteractionFullMatchExecutor.TripleResult> triples
    ) {
        List<ChampionPairInteractionFullMatchExecutor.FullRow> rows =
                new ArrayList<>(triples.size() * 3);
        for (var triple : triples) {
            rows.add(triple.off());
            rows.add(triple.legacy());
            rows.add(triple.interaction());
        }
        return List.copyOf(rows);
    }

    private static List<ChampionPairInteractionFullMatchExecutor.PairedRow> paired(
            List<ChampionPairInteractionFullMatchExecutor.TripleResult> triples
    ) {
        List<ChampionPairInteractionFullMatchExecutor.PairedRow> rows =
                new ArrayList<>(triples.size() * 3);
        for (var triple : triples) {
            rows.add(triple.offVsLegacy());
            rows.add(triple.offVsInteraction());
            rows.add(triple.legacyVsInteraction());
        }
        return List.copyOf(rows);
    }

    private static List<CounterfactualRow> counterfactual(
            List<ChampionPairInteractionFullMatchExecutor.TripleResult> triples
    ) {
        return triples.stream().map(triple -> new CounterfactualRow(
                triple.interaction().lineupId(),
                triple.interaction().skillProfile(),
                triple.interaction().orientation(),
                triple.interaction().seed(),
                triple.interaction().actualCombatAttempts(),
                triple.interaction().matchupApplications(),
                triple.interaction().generatedEdgeMean(),
                triple.interaction().actualGeneratedEdgeP50(),
                triple.interaction().actualGeneratedEdgeP90(),
                triple.interaction().actualGeneratedEdgeP95(),
                triple.offVsInteraction().winnerFlip() ? 1 : 0,
                false, false, 0)).toList();
    }

    private static List<MirrorRow> mirror(
            List<ChampionPairInteractionFullMatchExecutor.FullRow> full
    ) {
        List<MirrorRow> rows = new ArrayList<>();
        for (var mode :
                ChampionPairInteractionFullMatchExecutor.FormulaMode.values()) {
            for (String lineup : full.stream().map(row -> row.lineupId())
                    .distinct().sorted().toList()) {
                for (String skill : List.of("S0", "S3")) {
                    double original = logicalRate(full, mode, lineup, skill,
                            SideOrientationFixture.Orientation.ORIGINAL);
                    double mirrored = logicalRate(full, mode, lineup, skill,
                            SideOrientationFixture.Orientation.MIRRORED);
                    double offOriginal = logicalRate(full,
                            ChampionPairInteractionFullMatchExecutor.FormulaMode
                                    .MATCHUP_OFF, lineup, skill,
                            SideOrientationFixture.Orientation.ORIGINAL);
                    double offMirrored = logicalRate(full,
                            ChampionPairInteractionFullMatchExecutor.FormulaMode
                                    .MATCHUP_OFF, lineup, skill,
                            SideOrientationFixture.Orientation.MIRRORED);
                    double difference = Math.abs(original - mirrored);
                    double added = difference
                            - Math.abs(offOriginal - offMirrored);
                    rows.add(new MirrorRow(mode, lineup, skill, original,
                            mirrored, difference, added, added > .015));
                }
            }
        }
        return List.copyOf(rows);
    }

    private static double logicalRate(
            List<ChampionPairInteractionFullMatchExecutor.FullRow> full,
            ChampionPairInteractionFullMatchExecutor.FormulaMode mode,
            String lineup, String skill,
            SideOrientationFixture.Orientation orientation
    ) {
        List<ChampionPairInteractionFullMatchExecutor.FullRow> selected =
                full.stream().filter(row -> row.formulaMode() == mode
                        && row.lineupId().equals(lineup)
                        && row.skillProfile().equals(skill)
                        && row.orientation() == orientation).toList();
        return selected.stream().filter(row -> row.winnerLogicalTeam()
                == SideOrientationFixture.LogicalTeamId.TEAM_A).count()
                / (double) selected.size();
    }

    private static List<CellDecision> decisions(
            List<ChampionPairInteractionFullMatchExecutor.TripleResult> triples
    ) {
        Map<String, List<ChampionPairInteractionFullMatchExecutor.TripleResult>> groups =
                triples.stream().collect(java.util.stream.Collectors.groupingBy(
                        value -> value.off().lineupId() + "/"
                                + value.off().skillProfile(),
                        LinkedHashMap::new,
                        java.util.stream.Collectors.toList()));
        return groups.entrySet().stream().map(entry -> {
            var values = entry.getValue();
            var pairs = values.stream().map(value ->
                    value.offVsInteraction()).toList();
            double flipRate = pairs.stream().filter(row ->
                    row.winnerFlip()).count() / (double) pairs.size();
            double duration = pairs.stream().mapToInt(row ->
                    row.durationDelta()).average().orElse(0);
            double offDuration = values.stream().mapToInt(value ->
                    value.off().durationSeconds()).average().orElse(1);
            double p95 = values.stream().mapToDouble(value ->
                    value.interaction().actualGeneratedEdgeP95()).max().orElse(0);
            boolean mismatch = pairs.stream().anyMatch(row ->
                    row.replayMismatch() || row.diagnosticsMismatch());
            long blueRed = pairs.stream().filter(row ->
                    "BLUE_TO_RED".equals(row.flipDirection())).count();
            long redBlue = pairs.stream().filter(row ->
                    "RED_TO_BLUE".equals(row.flipDirection())).count();
            List<String> reasons = new ArrayList<>();
            if (flipRate > .03) reasons.add("WINNER_FLIP_RATE");
            if (Math.abs(duration) / offDuration > .05) {
                reasons.add("DURATION_DIFFERENCE");
            }
            if (p95 > .10) reasons.add("INTERACTION_P95");
            if (mismatch) reasons.add("REPLAY_OR_DIAGNOSTICS");
            if (blueRed >= 5 && redBlue == 0
                    || redBlue >= 5 && blueRed == 0) {
                reasons.add("ONE_SIDED_WINNER_FLIPS");
            }
            return new CellDecision(entry.getKey(), !reasons.isEmpty(),
                    String.join("|", reasons), reasons.isEmpty() ? 0 : 300);
        }).toList();
    }

    private record Job(GeneratedMatchupRoundRobinLineupFactory.Lineup lineup,
                       SideOrientationFixture.Orientation orientation,
                       int seed) {
        String cellId() {
            return lineup.lineupId() + "/" + lineup.skillProfile();
        }
    }
    record Result(List<ChampionPairInteractionFullMatchExecutor.FullRow> full,
            List<ChampionPairInteractionFullMatchExecutor.PairedRow> paired,
            List<CounterfactualRow> counterfactual, List<MirrorRow> mirror,
            List<CellDecision> decisions, int screeningFullRows,
            int screeningPairedRows, int escalationFullRows,
            int escalationPairedRows) { }
    record CellDecision(String cellId, boolean escalated,
                        String reasons, int addedSeeds) { }
    record CounterfactualRow(String lineupId, String skillProfile,
            SideOrientationFixture.Orientation orientation, int seed,
            int actualCombatAttempts, int interactionApplicationCount,
            double interactionEdgeMean, double interactionEdgeP50,
            double interactionEdgeP90, double interactionEdgeP95,
            int counterfactualOutcomeFlipCount,
            boolean additionalRandomConsumed, boolean gameplayMutated,
            int majorCombatSlotConsumedByEvaluation) { }
    record MirrorRow(ChampionPairInteractionFullMatchExecutor.FormulaMode mode,
            String lineupId, String skillProfile,
            double originalLogicalTeamAWinRate,
            double mirrorLogicalTeamAWinRate, double orientationDifference,
            double addedOrientationDifference, boolean addedSideWarning) { }
}
