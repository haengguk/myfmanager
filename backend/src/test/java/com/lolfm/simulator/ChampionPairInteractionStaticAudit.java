package com.lolfm.simulator;

import com.lolfm.champion.CenteredPairInteractionFormula;
import com.lolfm.champion.ChampionCatalog;
import com.lolfm.champion.ChampionMatchupInteractionVector;
import com.lolfm.champion.ChampionMatchupRuleType;
import com.lolfm.champion.PairInteractionGeneratedCatalog;
import com.lolfm.champion.ThirtyChampionGeneratedCatalog;
import com.lolfm.champion.ThirtyChampionRoleProfiles;
import com.lolfm.domain.Position;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class ChampionPairInteractionStaticAudit {
    private static final double EPSILON = 1e-12;

    private ChampionPairInteractionStaticAudit() {
    }

    static Result evaluate(ChampionCatalog champions) {
        var legacy = ThirtyChampionGeneratedCatalog.build(champions);
        var interaction = PairInteractionGeneratedCatalog.build(champions);
        List<VectorRow> vectors = vectors();
        List<ComparisonRow> comparisons = comparisons(legacy, interaction);
        List<ExplanationRow> explanations = explanations(interaction);
        List<TransitivityRow> transitivity =
                transitivity(champions, legacy, interaction);
        List<ScalarFitRow> scalarFits =
                scalarFits(champions, legacy, interaction);
        List<CorrelationRow> correlations = correlations(comparisons);
        List<DistributionRow> distributions = distributions(comparisons);
        List<DominanceRow> dominance = dominance(comparisons);
        List<ContextDiversityRow> diversity = diversity(comparisons);
        List<DeadzoneRow> deadzones = deadzones(comparisons);
        AggregationRow aggregation = aggregation(comparisons);
        return new Result(legacy, interaction, vectors, comparisons,
                explanations, transitivity, scalarFits, correlations,
                distributions, dominance, diversity, deadzones, aggregation);
    }

    private static List<VectorRow> vectors() {
        List<VectorRow> rows = new ArrayList<>(450);
        for (var entry : ThirtyChampionRoleProfiles.entries()) {
            var vector = ChampionMatchupInteractionVector.from(entry.profile());
            for (var trait : com.lolfm.champion.ChampionMatchupTrait.values()) {
                var value = vector.trait(trait);
                rows.add(new VectorRow(
                        vector.roleKey().championId().value(),
                        vector.roleKey().position(), trait, value.raw(),
                        vector.profileMean(), value.centered(),
                        value.interactionStrength(),
                        value.interactionVulnerability()));
            }
        }
        return List.copyOf(rows);
    }

    private static List<ComparisonRow> comparisons(
            ThirtyChampionGeneratedCatalog.BuildResult legacy,
            PairInteractionGeneratedCatalog.BuildResult interaction
    ) {
        Map<String, PairInteractionGeneratedCatalog.Row> indexed =
                interaction.rows().stream().collect(java.util.stream.Collectors
                        .toMap(row -> row.pairId() + "/" + row.context(),
                                row -> row));
        return legacy.rows().stream().map(row -> {
            var candidate = indexed.get(row.pairId() + "/" + row.context());
            int legacySign = sign(row.generatedBaseEdge());
            int interactionSign = sign(candidate.interactionEdge());
            return new ComparisonRow(row.position(),
                    row.canonicalFirstChampion(),
                    row.canonicalSecondChampion(), row.context(),
                    row.generatedBaseEdge(), candidate.interactionEdge(),
                    candidate.interactionEdge() - row.generatedBaseEdge(),
                    legacySign, interactionSign,
                    legacySign != interactionSign,
                    row.absoluteEdge(),
                    Math.abs(candidate.interactionEdge()),
                    row.dominantRule(), candidate.dominantRule(),
                    candidate.dominantRuleShare(),
                    Math.abs(candidate.forwardPlusReverse()) < EPSILON, true);
        }).toList();
    }

    private static List<ExplanationRow> explanations(
            PairInteractionGeneratedCatalog.BuildResult build
    ) {
        List<ExplanationRow> rows = new ArrayList<>(4_725);
        build.results().forEach((key, result) -> {
            for (var contribution : result.ruleContributions()) {
                rows.add(new ExplanationRow(key.pair().stableId(),
                        key.pair().position(), key.context(),
                        contribution.ruleType(),
                        contribution.sourceCapability(),
                        contribution.opponentVulnerabilityOrDependency(),
                        contribution.directionalSourceToOpponent(),
                        contribution.opponentCapability(),
                        contribution.sourceVulnerabilityOrDependency(),
                        contribution.directionalOpponentToSource(),
                        contribution.antisymmetricRuleEdge(),
                        contribution.contextWeight(),
                        contribution.weightedContribution(),
                        result.finalEdge()));
            }
        });
        return List.copyOf(rows);
    }

    private static List<TransitivityRow> transitivity(
            ChampionCatalog champions,
            ThirtyChampionGeneratedCatalog.BuildResult legacy,
            PairInteractionGeneratedCatalog.BuildResult interaction
    ) {
        List<TransitivityRow> rows = new ArrayList<>(900);
        for (Position position : Position.values()) {
            List<String> ids = champions.forPosition(position).stream()
                    .map(value -> value.id().value()).sorted().toList();
            for (var context : ProgressionCombatContext.values()) {
                for (int a = 0; a < ids.size(); a++) {
                    for (int b = a + 1; b < ids.size(); b++) {
                        for (int c = b + 1; c < ids.size(); c++) {
                            double lab = edge(legacy.rows(), ids.get(a), ids.get(b),
                                    context);
                            double lbc = edge(legacy.rows(), ids.get(b), ids.get(c),
                                    context);
                            double lac = edge(legacy.rows(), ids.get(a), ids.get(c),
                                    context);
                            double iab = edgeInteraction(interaction.rows(),
                                    ids.get(a), ids.get(b), context);
                            double ibc = edgeInteraction(interaction.rows(),
                                    ids.get(b), ids.get(c), context);
                            double iac = edgeInteraction(interaction.rows(),
                                    ids.get(a), ids.get(c), context);
                            rows.add(new TransitivityRow(position, context,
                                    ids.get(a), ids.get(b), ids.get(c),
                                    lab, lbc, lac, zero(lab + lbc - lac),
                                    iab, ibc, iac, zero(iab + ibc - iac),
                                    cycle(iab, ibc, -iac)));
                        }
                    }
                }
            }
        }
        return List.copyOf(rows);
    }

    private static List<ScalarFitRow> scalarFits(
            ChampionCatalog champions,
            ThirtyChampionGeneratedCatalog.BuildResult legacy,
            PairInteractionGeneratedCatalog.BuildResult interaction
    ) {
        List<ScalarFitRow> rows = new ArrayList<>(90);
        for (String formula : List.of("LEGACY_SEPARABLE_V2",
                "PAIR_INTERACTION_V1")) {
            for (Position position : Position.values()) {
                List<String> ids = champions.forPosition(position).stream()
                        .map(value -> value.id().value()).sorted().toList();
                for (var context : ProgressionCombatContext.values()) {
                    Map<String, Double> scores = new LinkedHashMap<>();
                    for (String id : ids) {
                        double sum = 0;
                        for (String opponent : ids) {
                            if (!id.equals(opponent)) {
                                sum += formula.startsWith("LEGACY")
                                        ? edge(legacy.rows(), id, opponent, context)
                                        : edgeInteraction(interaction.rows(), id,
                                        opponent, context);
                            }
                        }
                        scores.put(id, sum / ids.size());
                    }
                    List<Double> actual = new ArrayList<>();
                    List<Double> residuals = new ArrayList<>();
                    for (int i = 0; i < ids.size(); i++) {
                        for (int j = i + 1; j < ids.size(); j++) {
                            double value = formula.startsWith("LEGACY")
                                    ? edge(legacy.rows(), ids.get(i), ids.get(j), context)
                                    : edgeInteraction(interaction.rows(), ids.get(i),
                                    ids.get(j), context);
                            actual.add(value);
                            residuals.add(value
                                    - (scores.get(ids.get(i)) - scores.get(ids.get(j))));
                        }
                    }
                    double rmse = Math.sqrt(residuals.stream().mapToDouble(value ->
                            value * value).average().orElse(0));
                    double max = residuals.stream().mapToDouble(Math::abs)
                            .max().orElse(0);
                    double variance = variance(actual);
                    rows.add(new ScalarFitRow(formula, position, context, 15,
                            scores.toString(), rmse, max,
                            variance == 0 ? 1 : 1 - rmse * rmse / variance,
                            max <= 1e-10));
                }
            }
        }
        return List.copyOf(rows);
    }

    private static List<CorrelationRow> correlations(
            List<ComparisonRow> comparisons
    ) {
        List<CorrelationRow> rows = new ArrayList<>();
        for (String scope : java.util.stream.Stream.concat(
                java.util.stream.Stream.of("ALL"),
                java.util.Arrays.stream(Position.values()).map(Enum::name)).toList()) {
            List<ComparisonRow> selected = comparisons.stream().filter(row ->
                    scope.equals("ALL") || row.position().name().equals(scope)).toList();
            List<Double> traitDifference = selected.stream().map(row ->
                    profileMean(row.firstChampion())
                            - profileMean(row.secondChampion())).toList();
            List<Double> legacy = selected.stream().map(
                    ComparisonRow::legacyEdge).toList();
            List<Double> candidate = selected.stream().map(
                    ComparisonRow::interactionEdge).toList();
            rows.add(new CorrelationRow(scope, selected.size(),
                    ThirtyChampionStatistics.pearson(legacy, traitDifference),
                    ThirtyChampionStatistics.spearman(legacy, traitDifference),
                    ThirtyChampionStatistics.pearson(candidate, traitDifference),
                    ThirtyChampionStatistics.spearman(candidate, traitDifference)));
        }
        return List.copyOf(rows);
    }

    private static List<DistributionRow> distributions(
            List<ComparisonRow> comparisons
    ) {
        List<DistributionRow> rows = new ArrayList<>();
        for (String formula : List.of("LEGACY_SEPARABLE_V2",
                "PAIR_INTERACTION_V1")) {
            List<String> scopes = new ArrayList<>();
            scopes.add("ALL");
            java.util.Arrays.stream(ProgressionCombatContext.values())
                    .map(value -> "CONTEXT:" + value.name()).forEach(scopes::add);
            java.util.Arrays.stream(Position.values())
                    .map(value -> "POSITION:" + value.name()).forEach(scopes::add);
            for (String scope : scopes) {
                List<Double> values = comparisons.stream().filter(row ->
                                scope.equals("ALL")
                                        || scope.equals("CONTEXT:" + row.context().name())
                                        || scope.equals("POSITION:" + row.position().name()))
                        .map(row -> Math.abs(formula.startsWith("LEGACY")
                                ? row.legacyEdge() : row.interactionEdge())).toList();
                var stats = ThirtyChampionStatistics.summarize(values);
                rows.add(new DistributionRow(formula, scope, values.size(),
                        values.stream().filter(value -> value >= EPSILON).count(),
                        values.stream().filter(value -> value < EPSILON).count(),
                        stats.mean(), stats.p50(), stats.p75(), stats.p90(),
                        stats.p95(), stats.p99(), stats.max(),
                        values.stream().filter(value -> value >= .30).count()));
            }
        }
        return List.copyOf(rows);
    }

    private static List<DominanceRow> dominance(List<ComparisonRow> values) {
        List<DominanceRow> rows = new ArrayList<>();
        for (String formula : List.of("LEGACY_SEPARABLE_V2",
                "PAIR_INTERACTION_V1")) {
            for (var context : ProgressionCombatContext.values()) {
                Map<String, List<Double>> edges = directional(values, formula, context);
                for (var entry : edges.entrySet()) {
                    double mean = entry.getValue().stream().mapToDouble(
                            Double::doubleValue).average().orElse(0);
                    rows.add(new DominanceRow(formula, entry.getKey(), context,
                            entry.getValue().stream().filter(v -> v > EPSILON).count(),
                            entry.getValue().stream().filter(v ->
                                    Math.abs(v) < EPSILON).count(),
                            entry.getValue().stream().filter(v -> v < -EPSILON).count(),
                            mean, entry.getValue().stream().allMatch(v -> v > EPSILON)
                                    && mean > .03,
                            entry.getValue().stream().allMatch(v -> v < -EPSILON)
                                    && mean < -.03));
                }
            }
        }
        return List.copyOf(rows);
    }

    private static List<ContextDiversityRow> diversity(
            List<ComparisonRow> comparisons
    ) {
        return comparisons.stream().collect(java.util.stream.Collectors.groupingBy(
                row -> row.firstChampion() + "/" + row.secondChampion(),
                LinkedHashMap::new, java.util.stream.Collectors.toList()))
                .entrySet().stream().map(entry -> {
                    List<ComparisonRow> rows = entry.getValue();
                    List<Integer> legacySigns = rows.stream().map(row ->
                            sign(row.legacyEdge())).toList();
                    List<Integer> candidateSigns = rows.stream().map(row ->
                            sign(row.interactionEdge())).toList();
                    return new ContextDiversityRow(entry.getKey(),
                            rows.getFirst().position(),
                            legacySigns.stream().filter(v -> v > 0).count(),
                            legacySigns.stream().filter(v -> v == 0).count(),
                            legacySigns.stream().filter(v -> v < 0).count(),
                            allSame(legacySigns), range(rows, true),
                            signChanges(legacySigns),
                            candidateSigns.stream().filter(v -> v > 0).count(),
                            candidateSigns.stream().filter(v -> v == 0).count(),
                            candidateSigns.stream().filter(v -> v < 0).count(),
                            allSame(candidateSigns), range(rows, false),
                            signChanges(candidateSigns),
                            rows.stream().mapToDouble(row ->
                                    Math.abs(row.interactionEdge())).average().orElse(0));
                }).toList();
    }

    private static List<DeadzoneRow> deadzones(List<ComparisonRow> rows) {
        return java.util.stream.DoubleStream.of(0, .001, .0025, .005, .01)
                .mapToObj(threshold -> {
                    List<ComparisonRow> removed = rows.stream().filter(row ->
                            Math.abs(row.interactionEdge()) < threshold
                                    && Math.abs(row.interactionEdge()) >= EPSILON).toList();
                    return new DeadzoneRow(threshold, removed.size(),
                            removed.size() / (double) rows.size(),
                            removed.stream().map(row -> row.firstChampion() + "/"
                                    + row.secondChampion()).distinct().count(),
                            removed.stream().map(ComparisonRow::context).distinct().count(),
                            removed.stream().filter(row ->
                                    row.interactionEdge() > 0).count(),
                            removed.stream().filter(row ->
                                    row.interactionEdge() < 0).count(),
                            0, 0, 0, 0,
                            removed.stream().mapToDouble(row ->
                                    Math.abs(row.interactionEdge())).max().orElse(0),
                            removed.stream().mapToDouble(row ->
                                    Math.abs(row.interactionEdge())).average().orElse(0),
                            removed.size() >= rows.size() * .1
                                    && removed.size() <= rows.size() * .4
                                    ? "CANDIDATE" : "NOT_RECOMMENDED");
                }).toList();
    }

    private static AggregationRow aggregation(List<ComparisonRow> rows) {
        long nonZero = rows.stream().filter(row ->
                Math.abs(row.interactionEdge()) >= EPSILON).count();
        double all = rows.stream().mapToDouble(row ->
                Math.abs(row.interactionEdge())).average().orElse(0);
        double active = rows.stream().filter(row ->
                        Math.abs(row.interactionEdge()) >= EPSILON)
                .mapToDouble(row -> Math.abs(row.interactionEdge()))
                .average().orElse(0);
        return new AggregationRow(rows.size(), nonZero,
                nonZero / (double) rows.size(), all, active, active,
                Math.abs(rows.stream().mapToDouble(
                        ComparisonRow::interactionEdge).sum())
                        / rows.stream().mapToDouble(row ->
                        Math.abs(row.interactionEdge())).sum(),
                "EXPECTED_CROSS_POSITION_SIGN_CANCELLATION", 0, 0);
    }

    private static Map<String, List<Double>> directional(
            List<ComparisonRow> rows, String formula,
            ProgressionCombatContext context) {
        Map<String, List<Double>> result = new LinkedHashMap<>();
        rows.stream().filter(row -> row.context() == context).forEach(row -> {
            double edge = formula.startsWith("LEGACY")
                    ? row.legacyEdge() : row.interactionEdge();
            result.computeIfAbsent(row.firstChampion(), ignored ->
                    new ArrayList<>()).add(edge);
            result.computeIfAbsent(row.secondChampion(), ignored ->
                    new ArrayList<>()).add(-edge);
        });
        return result;
    }

    private static double edge(List<ThirtyChampionGeneratedCatalog.MatrixRow> rows,
                               String source, String opponent,
                               ProgressionCombatContext context) {
        var row = rows.stream().filter(value -> value.context() == context
                && (value.canonicalFirstChampion().equals(source)
                && value.canonicalSecondChampion().equals(opponent)
                || value.canonicalFirstChampion().equals(opponent)
                && value.canonicalSecondChampion().equals(source)))
                .findFirst().orElseThrow();
        return row.canonicalFirstChampion().equals(source)
                ? row.generatedBaseEdge() : -row.generatedBaseEdge();
    }
    private static double edgeInteraction(
            List<PairInteractionGeneratedCatalog.Row> rows,
            String source, String opponent, ProgressionCombatContext context) {
        var row = rows.stream().filter(value -> value.context() == context
                && (value.firstChampion().equals(source)
                && value.secondChampion().equals(opponent)
                || value.firstChampion().equals(opponent)
                && value.secondChampion().equals(source)))
                .findFirst().orElseThrow();
        return row.firstChampion().equals(source)
                ? row.interactionEdge() : -row.interactionEdge();
    }
    private static double profileMean(String id) {
        return ThirtyChampionRoleProfiles.entries().stream()
                .filter(entry -> entry.profile().roleKey().championId()
                        .value().equals(id)).findFirst().orElseThrow()
                .profile().traits().values().stream().mapToInt(Integer::intValue)
                .average().orElseThrow();
    }
    private static boolean cycle(double ab, double bc, double ca) {
        return ab > EPSILON && bc > EPSILON && ca > EPSILON
                || ab < -EPSILON && bc < -EPSILON && ca < -EPSILON;
    }
    private static int sign(double value) {
        return Math.abs(value) < EPSILON ? 0 : value > 0 ? 1 : -1;
    }
    private static double zero(double value) {
        return Math.abs(value) < EPSILON ? 0 : value;
    }
    private static double variance(List<Double> values) {
        double mean = values.stream().mapToDouble(Double::doubleValue)
                .average().orElse(0);
        return values.stream().mapToDouble(value -> (value - mean)
                * (value - mean)).average().orElse(0);
    }
    private static boolean allSame(List<Integer> signs) {
        return signs.stream().allMatch(sign -> sign.equals(signs.getFirst()));
    }
    private static int signChanges(List<Integer> signs) {
        int changes = 0;
        for (int i = 1; i < signs.size(); i++) {
            if (!signs.get(i).equals(signs.get(i - 1))) changes++;
        }
        return changes;
    }
    private static double range(List<ComparisonRow> rows, boolean legacy) {
        var stats = ThirtyChampionStatistics.summarize(rows.stream().map(row ->
                legacy ? row.legacyEdge() : row.interactionEdge()).toList());
        return stats.max() - stats.min();
    }

    record Result(ThirtyChampionGeneratedCatalog.BuildResult legacy,
            PairInteractionGeneratedCatalog.BuildResult interaction,
            List<VectorRow> vectors, List<ComparisonRow> comparisons,
            List<ExplanationRow> explanations,
            List<TransitivityRow> transitivity,
            List<ScalarFitRow> scalarFits,
            List<CorrelationRow> correlations,
            List<DistributionRow> distributions,
            List<DominanceRow> dominance,
            List<ContextDiversityRow> diversity,
            List<DeadzoneRow> deadzones, AggregationRow aggregation) { }
    record VectorRow(String champion, Position position,
            com.lolfm.champion.ChampionMatchupTrait trait, double raw,
            double profileMean, double centered, double interactionStrength,
            double interactionVulnerability) { }
    record ComparisonRow(Position position, String firstChampion,
            String secondChampion, ProgressionCombatContext context,
            double legacyEdge, double interactionEdge, double edgeDelta,
            int legacySign, int interactionSign, boolean signChanged,
            double legacyAbsoluteEdge, double interactionAbsoluteEdge,
            ChampionMatchupRuleType legacyDominantRule,
            ChampionMatchupRuleType interactionDominantRule,
            double interactionDominantRuleShare,
            boolean directionalityValid, boolean candidateOnly) { }
    record ExplanationRow(String pair, Position position,
            ProgressionCombatContext context, ChampionMatchupRuleType ruleType,
            double sourceCapability, double opponentVulnerabilityOrDependency,
            double directionalSourceToOpponent, double opponentCapability,
            double sourceVulnerabilityOrDependency,
            double directionalOpponentToSource, double antisymmetricRuleEdge,
            double contextWeight, double weightedContribution, double finalEdge) { }
    record TransitivityRow(Position position, ProgressionCombatContext context,
            String championA, String championB, String championC,
            double legacyEdgeAB, double legacyEdgeBC, double legacyEdgeAC,
            double legacyResidual, double interactionEdgeAB,
            double interactionEdgeBC, double interactionEdgeAC,
            double interactionResidual, boolean cyclicPreference) { }
    record ScalarFitRow(String formulaType, Position position,
            ProgressionCombatContext context, int pairCount,
            String fittedChampionScores, double rmse,
            double maxAbsoluteResidual, double explainedVariance,
            boolean exactDifferenceModel) { }
    record CorrelationRow(String scope, int rowCount,
            double legacyPearson, double legacySpearman,
            double interactionPearson, double interactionSpearman) { }
    record DistributionRow(String formulaType, String scope, int rowCount,
            long nonZeroCount, long exactZeroCount, double meanAbsoluteEdge,
            double p50Absolute, double p75Absolute, double p90Absolute,
            double p95Absolute, double p99Absolute, double maxAbsoluteEdge,
            long capHitCount) { }
    record DominanceRow(String formulaType, String champion,
            ProgressionCombatContext context, long positiveOpponentCount,
            long neutralOpponentCount, long negativeOpponentCount,
            double meanEdge, boolean universalDominance,
            boolean universalWeakness) { }
    record ContextDiversityRow(String pair, Position position,
            long legacyPositive, long legacyNeutral, long legacyNegative,
            boolean legacyAllSameSign, double legacyContextRange,
            int legacySignChanges, long interactionPositive,
            long interactionNeutral, long interactionNegative,
            boolean interactionAllSameSign, double interactionContextRange,
            int interactionSignChanges, double interactionMeanAbsoluteEdge) { }
    record DeadzoneRow(double threshold, long neutralizedRows,
            double percentNeutralized, long affectedPairs, long affectedContexts,
            long positiveToNeutral, long negativeToNeutral,
            long reverseSymmetryErrors, long allSameSignPairChanges,
            long dominanceWarningChanges, long expectedFocusedConstraintChanges,
            double strongestRemovedEdge, double meanRemovedAbsoluteEdge,
            String recommendation) { }
    record AggregationRow(long eligiblePairCount, long nonZeroPairCount,
            double coverageRatio, double allEligibleAverage,
            double nonZeroAverage, double absoluteNonZeroMean,
            double netDirectionalRetention, String classification,
            long meaningfulSignCancellationCount,
            long unexpectedAggregationDilutionCount) { }
}
