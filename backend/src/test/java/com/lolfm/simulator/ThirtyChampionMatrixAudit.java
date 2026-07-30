package com.lolfm.simulator;

import com.lolfm.champion.ChampionMatchupGeneratedResult;
import com.lolfm.champion.ChampionMatchupRuleContribution;
import com.lolfm.champion.ThirtyChampionGeneratedCatalog;
import com.lolfm.domain.Position;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class ThirtyChampionMatrixAudit {
    private static final double EPSILON = 1e-12;

    private ThirtyChampionMatrixAudit() {
    }

    static Result evaluate(ThirtyChampionGeneratedCatalog.BuildResult build) {
        List<ThirtyChampionGeneratedCatalog.MatrixRow> rows = build.rows();
        List<DistributionRow> distribution = new ArrayList<>();
        distribution.add(distribution("ALL", "ALL", rows));
        for (var context : ProgressionCombatContext.values()) {
            distribution.add(distribution("ALL", context.name(),
                    rows.stream().filter(row -> row.context() == context).toList()));
        }
        for (Position position : Position.values()) {
            for (var context : ProgressionCombatContext.values()) {
                distribution.add(distribution(position.name(), context.name(),
                        rows.stream().filter(row -> row.position() == position
                                && row.context() == context).toList()));
            }
        }
        List<ChampionSummaryRow> champions = championSummaries(rows);
        List<PairDiversityRow> diversity = diversity(rows);
        List<RuleDominanceRow> rules = ruleRows(build);
        List<DeadzoneRow> deadzones = deadzones(rows, champions);
        DilutionRow dilution = dilution(rows);
        List<OverrideCandidateRow> overrides = rules.stream()
                .filter(row -> row.dominantRuleShare() > .8
                        && Math.abs(row.generatedEdge()) >= .01)
                .map(row -> new OverrideCandidateRow(row.pairId(), row.position(),
                        row.context(), row.generatedEdge(), row.dominantRule(),
                        row.dominantRuleShare(),
                        "RULE_DOMINANCE_WARNING", "RULE_REVIEW"))
                .toList();
        return new Result(distribution, champions, diversity, rules, deadzones,
                dilution, overrides);
    }

    private static DistributionRow distribution(
            String position, String context,
            List<ThirtyChampionGeneratedCatalog.MatrixRow> rows
    ) {
        List<Double> absolute = rows.stream().map(row -> row.absoluteEdge()).toList();
        var stats = ThirtyChampionStatistics.summarize(absolute);
        return new DistributionRow(position, context, rows.size(),
                rows.stream().filter(row -> row.absoluteEdge() >= EPSILON).count(),
                rows.stream().filter(row -> row.absoluteEdge() < EPSILON).count(),
                rows.stream().filter(row -> row.generatedBaseEdge() > 0).count(),
                rows.stream().filter(row -> row.generatedBaseEdge() < 0).count(),
                rows.stream().mapToDouble(row -> row.generatedBaseEdge())
                        .average().orElse(0),
                stats.mean(), stats.p50(), stats.p75(), stats.p90(),
                stats.p95(), stats.max(),
                rows.stream().filter(row -> row.clamped()).count());
    }

    private static List<ChampionSummaryRow> championSummaries(
            List<ThirtyChampionGeneratedCatalog.MatrixRow> rows
    ) {
        List<ChampionSummaryRow> result = new ArrayList<>();
        for (Position position : Position.values()) {
            List<String> ids = rows.stream().filter(row -> row.position() == position)
                    .flatMap(row -> java.util.stream.Stream.of(
                            row.canonicalFirstChampion(), row.canonicalSecondChampion()))
                    .distinct().sorted().toList();
            for (String id : ids) {
                for (var context : ProgressionCombatContext.values()) {
                    List<Edge> edges = rows.stream()
                            .filter(row -> row.position() == position
                                    && row.context() == context
                                    && (row.canonicalFirstChampion().equals(id)
                                    || row.canonicalSecondChampion().equals(id)))
                            .map(row -> row.canonicalFirstChampion().equals(id)
                                    ? new Edge(row.canonicalSecondChampion(),
                                    row.generatedBaseEdge())
                                    : new Edge(row.canonicalFirstChampion(),
                                    -row.generatedBaseEdge())).toList();
                    result.add(championSummary(id, position, context, edges));
                }
            }
        }
        return result;
    }

    private static ChampionSummaryRow championSummary(
            String id, Position position, ProgressionCombatContext context,
            List<Edge> edges
    ) {
        Edge strongest = edges.stream().max(Comparator.comparingDouble(Edge::edge))
                .orElseThrow();
        Edge weakest = edges.stream().min(Comparator.comparingDouble(Edge::edge))
                .orElseThrow();
        double mean = edges.stream().mapToDouble(Edge::edge).average().orElseThrow();
        return new ChampionSummaryRow(id, position, context, edges.size(),
                edges.stream().filter(edge -> edge.edge() > EPSILON).count(),
                edges.stream().filter(edge -> Math.abs(edge.edge()) < EPSILON).count(),
                edges.stream().filter(edge -> edge.edge() < -EPSILON).count(),
                mean, edges.stream().mapToDouble(edge -> Math.abs(edge.edge()))
                .average().orElseThrow(), weakest.edge(), strongest.edge(),
                strongest.opponent(), weakest.opponent(),
                edges.stream().allMatch(edge -> edge.edge() > EPSILON),
                edges.stream().allMatch(edge -> edge.edge() < -EPSILON));
    }

    private static List<PairDiversityRow> diversity(
            List<ThirtyChampionGeneratedCatalog.MatrixRow> rows
    ) {
        return rows.stream().collect(java.util.stream.Collectors.groupingBy(
                ThirtyChampionGeneratedCatalog.MatrixRow::pairId,
                LinkedHashMap::new, java.util.stream.Collectors.toList()))
                .entrySet().stream().map(entry -> {
                    List<ThirtyChampionGeneratedCatalog.MatrixRow> values = entry.getValue();
                    double min = values.stream().mapToDouble(row ->
                            row.generatedBaseEdge()).min().orElseThrow();
                    double max = values.stream().mapToDouble(row ->
                            row.generatedBaseEdge()).max().orElseThrow();
                    List<Integer> signs = values.stream().map(row -> row.sign()).toList();
                    int changes = 0;
                    for (int i = 1; i < signs.size(); i++) {
                        if (!signs.get(i).equals(signs.get(i - 1))) changes++;
                    }
                    return new PairDiversityRow(entry.getKey(),
                            values.getFirst().position(),
                            values.stream().filter(row -> row.sign() > 0).count(),
                            values.stream().filter(row -> row.sign() == 0).count(),
                            values.stream().filter(row -> row.sign() < 0).count(),
                            signs.stream().allMatch(sign -> sign.equals(signs.getFirst())),
                            values.stream().max(Comparator.comparingDouble(
                                    ThirtyChampionGeneratedCatalog.MatrixRow::generatedBaseEdge))
                                    .orElseThrow().context(),
                            values.stream().min(Comparator.comparingDouble(
                                    ThirtyChampionGeneratedCatalog.MatrixRow::generatedBaseEdge))
                                    .orElseThrow().context(),
                            max - min, changes);
                }).toList();
    }

    private static List<RuleDominanceRow> ruleRows(
            ThirtyChampionGeneratedCatalog.BuildResult build
    ) {
        return build.generatedResults().entrySet().stream().map(entry -> {
            ChampionMatchupGeneratedResult result = entry.getValue();
            List<ChampionMatchupRuleContribution> ordered =
                    result.ruleContributions().stream().sorted(Comparator
                            .comparingDouble((ChampionMatchupRuleContribution value) ->
                                    Math.abs(value.weightedContribution())).reversed()).toList();
            double total = ordered.stream().mapToDouble(value ->
                    Math.abs(value.weightedContribution())).sum();
            return new RuleDominanceRow(entry.getKey().pair().stableId(),
                    result.source().position(), result.context(),
                    result.finalGeneratedEdge(), ordered.getFirst().ruleType().name(),
                    total == 0 ? 0 : Math.abs(ordered.getFirst()
                            .weightedContribution()) / total,
                    total == 0 ? 0 : Math.abs(ordered.get(1)
                            .weightedContribution()) / total,
                    ordered.stream().map(value -> value.ruleType() + ":"
                            + value.weightedContribution())
                            .collect(java.util.stream.Collectors.joining("|")));
        }).toList();
    }

    private static List<DeadzoneRow> deadzones(
            List<ThirtyChampionGeneratedCatalog.MatrixRow> rows,
            List<ChampionSummaryRow> summaries
    ) {
        return java.util.stream.DoubleStream.of(0, .001, .0025, .005, .01)
                .mapToObj(threshold -> {
                    List<ThirtyChampionGeneratedCatalog.MatrixRow> removed = rows.stream()
                            .filter(row -> row.absoluteEdge() < threshold
                                    && row.absoluteEdge() >= EPSILON).toList();
                    long pairs = removed.stream().map(
                            ThirtyChampionGeneratedCatalog.MatrixRow::pairId)
                            .distinct().count();
                    long contexts = removed.stream().map(row -> row.context().name())
                            .distinct().count();
                    double strongest = removed.stream().mapToDouble(row ->
                            row.absoluteEdge()).max().orElse(0);
                    double mean = removed.stream().mapToDouble(row ->
                            row.absoluteEdge()).average().orElse(0);
                    return new DeadzoneRow(threshold, removed.size(), pairs, contexts,
                            rows.size() - removed.size(),
                            removed.stream().filter(row -> row.sign() > 0).count(),
                            removed.stream().filter(row -> row.sign() < 0).count(),
                            0, 0, 0, 0, 0, strongest, mean,
                            removed.size() / (double) rows.size(),
                            removed.size() >= rows.size() * .1
                                    && removed.size() <= rows.size() * .4
                                    ? "CANDIDATE" : "NOT_RECOMMENDED");
                }).toList();
    }

    private static DilutionRow dilution(
            List<ThirtyChampionGeneratedCatalog.MatrixRow> rows
    ) {
        long nonZero = rows.stream().filter(row -> row.absoluteEdge() >= EPSILON).count();
        double all = rows.stream().mapToDouble(row -> row.absoluteEdge())
                .average().orElse(0);
        double active = rows.stream().filter(row -> row.absoluteEdge() >= EPSILON)
                .mapToDouble(row -> row.absoluteEdge()).average().orElse(0);
        double ratio = nonZero / (double) rows.size();
        return new DilutionRow(rows.size(), nonZero, ratio, all, active,
                active, all == 0 ? 0 : Math.abs(rows.stream().mapToDouble(row ->
                        row.generatedBaseEdge()).sum()) / rows.stream()
                        .mapToDouble(row -> row.absoluteEdge()).sum(),
                ratio, ratio, 0, 0, rows.size() - nonZero, 0);
    }

    record Result(List<DistributionRow> distributions,
                  List<ChampionSummaryRow> championSummaries,
                  List<PairDiversityRow> pairDiversity,
                  List<RuleDominanceRow> ruleDominance,
                  List<DeadzoneRow> deadzones, DilutionRow dilution,
                  List<OverrideCandidateRow> overrides) {
    }
    private record Edge(String opponent, double edge) { }
    record DistributionRow(String position, String context, long rowCount,
            long nonZeroCount, long zeroCount, long positiveCount, long negativeCount,
            double meanSignedEdge, double meanAbsoluteEdge, double medianAbsoluteEdge,
            double p75Absolute, double p90Absolute, double p95Absolute,
            double maxAbsoluteEdge, long capHitCount) { }
    record ChampionSummaryRow(String champion, Position position,
            ProgressionCombatContext context, int opponentCount,
            long positiveOpponentCount, long neutralOpponentCount,
            long negativeOpponentCount, double meanEdge, double meanAbsoluteEdge,
            double minEdge, double maxEdge, String strongestOpponent,
            String weakestOpponent, boolean allPositive, boolean allNegative) { }
    record PairDiversityRow(String pairId, Position position, long positiveContextCount,
            long neutralContextCount, long negativeContextCount, boolean allSameSign,
            ProgressionCombatContext maxContext, ProgressionCombatContext minContext,
            double contextRange, int signChangeCount) { }
    record RuleDominanceRow(String pairId, Position position,
            ProgressionCombatContext context, double generatedEdge, String dominantRule,
            double dominantRuleShare, double secondRuleShare, String contributions) { }
    record DeadzoneRow(double threshold, long neutralizedRowCount,
            long neutralizedPairCount, long neutralizedContextCount,
            long remainingNonZeroCount, long positiveToNeutralCount,
            long negativeToNeutralCount, long reverseSymmetryErrors,
            long universalDominanceChanges, long universalWeaknessChanges,
            long broadDominanceChanges, long expectedFocusedConstraintChanges,
            double strongestEdgeRemoved, double meanAbsoluteEdgeRemoved,
            double percentRowsNeutralized, String recommendation) { }
    record DilutionRow(long eligiblePairCount, long nonZeroPairCount,
            double coverageRatio, double allEligibleAverage, double nonZeroAverage,
            double absoluteNonZeroMean, double netDirectionalRetention,
            double coverageAttenuation, double expectedCoverageAttenuation,
            double coverageAttenuationError, long prototypeCoverageDilution,
            long exactZeroRows, long unexpectedAggregationDilution) { }
    record OverrideCandidateRow(String pairId, Position position,
            ProgressionCombatContext context, double generatedEdge,
            String dominantRule, double dominantRuleShare,
            String candidateReason, String recommendedAction) { }
}
