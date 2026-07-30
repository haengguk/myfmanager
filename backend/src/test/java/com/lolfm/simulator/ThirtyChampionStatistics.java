package com.lolfm.simulator;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.ToDoubleFunction;

final class ThirtyChampionStatistics {
    private ThirtyChampionStatistics() {
    }

    static Summary summarize(List<Double> samples) {
        if (samples.isEmpty()) {
            return new Summary(0, 0, 0, 0, 0, 0, 0, 0, 0);
        }
        List<Double> ordered = samples.stream().sorted().toList();
        double mean = ordered.stream().mapToDouble(Double::doubleValue)
                .average().orElseThrow();
        return new Summary(ordered.size(), mean, quantile(ordered, .50),
                quantile(ordered, .75), quantile(ordered, .90),
                quantile(ordered, .95), quantile(ordered, .99),
                ordered.getFirst(), ordered.getLast());
    }

    static double quantile(List<Double> samples, double probability) {
        if (samples.isEmpty()) throw new IllegalArgumentException("samples required");
        if (probability < 0 || probability > 1) {
            throw new IllegalArgumentException("probability must be in [0,1]");
        }
        List<Double> ordered = new ArrayList<>(samples);
        ordered.sort(Comparator.naturalOrder());
        double index = probability * (ordered.size() - 1);
        int low = (int) Math.floor(index);
        int high = (int) Math.ceil(index);
        if (low == high) return ordered.get(low);
        double fraction = index - low;
        return ordered.get(low) * (1 - fraction) + ordered.get(high) * fraction;
    }

    static double standardDeviation(List<Double> samples) {
        if (samples.isEmpty()) return 0;
        double mean = samples.stream().mapToDouble(Double::doubleValue)
                .average().orElseThrow();
        return Math.sqrt(samples.stream().mapToDouble(value ->
                Math.pow(value - mean, 2)).average().orElse(0));
    }

    static double pearson(List<Double> left, List<Double> right) {
        if (left.size() != right.size() || left.size() < 2) return 0;
        double lm = left.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        double rm = right.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        double numerator = 0, ld = 0, rd = 0;
        for (int i = 0; i < left.size(); i++) {
            double l = left.get(i) - lm, r = right.get(i) - rm;
            numerator += l * r;
            ld += l * l;
            rd += r * r;
        }
        return ld == 0 || rd == 0 ? 0 : numerator / Math.sqrt(ld * rd);
    }

    static double spearman(List<Double> left, List<Double> right) {
        return pearson(ranks(left), ranks(right));
    }

    private static List<Double> ranks(List<Double> values) {
        List<Integer> order = java.util.stream.IntStream.range(0, values.size())
                .boxed().sorted(Comparator.comparingDouble(values::get)).toList();
        double[] ranks = new double[values.size()];
        for (int start = 0; start < order.size();) {
            int end = start + 1;
            while (end < order.size()
                    && Double.compare(values.get(order.get(start)),
                    values.get(order.get(end))) == 0) end++;
            double rank = (start + end - 1) / 2.0 + 1;
            for (int i = start; i < end; i++) ranks[order.get(i)] = rank;
            start = end;
        }
        return java.util.Arrays.stream(ranks).boxed().toList();
    }

    static <T, K> Map<K, List<T>> group(
            List<T> values, Function<T, K> key
    ) {
        Map<K, List<T>> result = new HashMap<>();
        for (T value : values) {
            result.computeIfAbsent(key.apply(value), ignored -> new ArrayList<>())
                    .add(value);
        }
        return result;
    }

    static <T> List<Double> values(
            List<T> source, ToDoubleFunction<T> getter
    ) {
        return source.stream().mapToDouble(getter).boxed().toList();
    }

    record Summary(int count, double mean, double p50, double p75,
                   double p90, double p95, double p99,
                   double min, double max) {
    }
}
