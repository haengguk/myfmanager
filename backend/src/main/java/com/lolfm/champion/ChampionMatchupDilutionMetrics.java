package com.lolfm.champion;

import java.util.List;

/** Observational aggregation metrics; never changes the arithmetic matchup edge. */
public record ChampionMatchupDilutionMetrics(
        int eligiblePairCount,
        int nonZeroPairCount,
        Double coverageRatio,
        double allEligibleAverage,
        Double nonZeroAverage,
        Double absoluteNonZeroMean,
        Double netDirectionalRetention,
        Double coverageAttenuation,
        Double expectedCoverageAttenuation,
        Double coverageAttenuationError,
        Classification classification
) {
    private static final double EPSILON = 1e-12;

    public static ChampionMatchupDilutionMetrics calculate(
            int eligiblePairCount,
            List<Double> pairEdges
    ) {
        if (eligiblePairCount < 0 || pairEdges.size() != eligiblePairCount) {
            throw new IllegalArgumentException("Eligible count must match pair edges");
        }
        List<Double> nonZero = pairEdges.stream()
                .filter(value -> Math.abs(value) >= EPSILON).toList();
        int count = nonZero.size();
        double sum = pairEdges.stream().mapToDouble(Double::doubleValue).sum();
        double absoluteSum = nonZero.stream()
                .mapToDouble(value -> Math.abs(value)).sum();
        Double coverage = eligiblePairCount == 0
                ? null : count / (double) eligiblePairCount;
        double allAverage = eligiblePairCount == 0 ? 0.0 : sum / eligiblePairCount;
        Double nonZeroAverage = count == 0 ? null : sum / count;
        Double absoluteMean = count == 0 ? null : absoluteSum / count;
        Double retention = count == 0 || absoluteSum < EPSILON
                ? null : Math.abs(sum) / absoluteSum;
        Double attenuation = nonZeroAverage == null
                || Math.abs(nonZeroAverage) < EPSILON
                ? null : Math.abs(allAverage) / Math.abs(nonZeroAverage);
        Double error = attenuation == null || coverage == null
                ? null : Math.abs(attenuation - coverage);
        Classification classification = classify(
                eligiblePairCount, count, retention, error, allAverage);
        return new ChampionMatchupDilutionMetrics(
                eligiblePairCount, count, coverage, allAverage, nonZeroAverage,
                absoluteMean, retention, attenuation, coverage, error, classification);
    }

    private static Classification classify(
            int eligible,
            int nonZero,
            Double retention,
            Double error,
            double average
    ) {
        if (error != null && error > .05) {
            return Classification.UNEXPECTED_AGGREGATION_DILUTION;
        }
        if (nonZero >= 2 && retention != null && retention < .50) {
            return Classification.SIGN_CANCELLATION;
        }
        if (nonZero < eligible && retention != null && retention >= .80) {
            return Classification.PROTOTYPE_COVERAGE_DILUTION;
        }
        if (Math.abs(average) < .01) {
            return Classification.LOW_MAGNITUDE_EDGE;
        }
        return Classification.NONE;
    }

    public enum Classification {
        NONE,
        PROTOTYPE_COVERAGE_DILUTION,
        SIGN_CANCELLATION,
        LOW_MAGNITUDE_EDGE,
        UNEXPECTED_AGGREGATION_DILUTION
    }
}
