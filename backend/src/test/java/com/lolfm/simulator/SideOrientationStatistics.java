package com.lolfm.simulator;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

final class SideOrientationStatistics {
    private SideOrientationStatistics() {
    }

    static Interval wilson(int successes, int total) {
        if (total == 0) return new Interval(Double.NaN, Double.NaN);
        double z = 1.959963984540054;
        double p = successes / (double) total;
        double denominator = 1.0 + z * z / total;
        double center = (p + z * z / (2.0 * total)) / denominator;
        double margin = z / denominator
                * Math.sqrt(p * (1.0 - p) / total + z * z / (4.0 * total * total));
        return new Interval(Math.max(0.0, center - margin), Math.min(1.0, center + margin));
    }

    static double mcnemarExact(int originalOnly, int mirroredOnly) {
        int discordant = originalOnly + mirroredOnly;
        if (discordant == 0) return 1.0;
        int tail = Math.min(originalOnly, mirroredOnly);
        double logTerm = -discordant * Math.log(2.0);
        double logCumulative = Double.NEGATIVE_INFINITY;
        for (int k = 0; k <= tail; k++) {
            logCumulative = logAdd(logCumulative, logTerm);
            if (k < tail) {
                logTerm += Math.log(discordant - k) - Math.log(k + 1.0);
            }
        }
        return Math.min(1.0, 2.0 * Math.exp(logCumulative));
    }

    private static double logAdd(double left, double right) {
        if (left == Double.NEGATIVE_INFINITY) return right;
        double high = Math.max(left, right);
        double low = Math.min(left, right);
        return high + Math.log1p(Math.exp(low - high));
    }

    static double[] holm(double[] raw) {
        List<IndexedP> ordered = new ArrayList<>();
        for (int i = 0; i < raw.length; i++) ordered.add(new IndexedP(i, raw[i]));
        ordered.sort(Comparator.comparingDouble(IndexedP::p).thenComparingInt(IndexedP::index));
        double[] adjusted = new double[raw.length];
        double previous = 0.0;
        for (int rank = 0; rank < ordered.size(); rank++) {
            IndexedP value = ordered.get(rank);
            double corrected = Math.min(1.0, value.p * (ordered.size() - rank));
            previous = Math.max(previous, corrected);
            adjusted[value.index] = previous;
        }
        return adjusted;
    }

    record Interval(double low, double high) {
        boolean contains(double value) {
            return low <= value && value <= high;
        }
    }

    private record IndexedP(int index, double p) {
    }
}
