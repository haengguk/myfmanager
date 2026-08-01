package com.lolfm.composition;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Stateless, order-independent aggregation for opponent response signals. */
public final class OppositionAggregationPolicy {
    private OppositionAggregationPolicy() {}

    public static double aggregate(List<Double> values, OppositionAggregation aggregation) {
        Objects.requireNonNull(values, "values");
        Objects.requireNonNull(aggregation, "aggregation");
        int expected = switch (aggregation) {
            case SINGLE -> 1;
            case COMPLEMENTARY_TWO -> 2;
            case COMPLEMENTARY_THREE -> 3;
        };
        if (values.size() != expected) {
            throw new IllegalArgumentException("Expected " + expected + " opposition values");
        }
        List<Double> sorted = new ArrayList<>(values.size());
        for (Double value : values) {
            if (value == null || !Double.isFinite(value) || value < 0.0 || value > 1.0) {
                throw new IllegalArgumentException("Opposition values must be finite and within [0,1]");
            }
            sorted.add(normalizeZero(value));
        }
        sorted.sort(java.util.Comparator.reverseOrder());
        double result = switch (aggregation) {
            case SINGLE -> sorted.get(0);
            case COMPLEMENTARY_TWO -> 0.65 * sorted.get(0) + 0.35 * sorted.get(1);
            case COMPLEMENTARY_THREE -> 0.55 * sorted.get(0) + 0.30 * sorted.get(1) + 0.15 * sorted.get(2);
        };
        if (!Double.isFinite(result) || result < 0.0 || result > 1.0) {
            throw new IllegalStateException("Invalid opposition aggregation result");
        }
        return normalizeZero(result);
    }

    private static double normalizeZero(double value) {
        return value == 0.0 ? 0.0 : value;
    }
}
