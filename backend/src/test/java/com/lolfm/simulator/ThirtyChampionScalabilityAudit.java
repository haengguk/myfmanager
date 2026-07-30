package com.lolfm.simulator;

import java.util.LinkedHashMap;
import java.util.Map;

final class ThirtyChampionScalabilityAudit {
    private ThirtyChampionScalabilityAudit() {
    }

    static Result run() {
        long started = System.nanoTime();
        Map<String, int[]> profiles = new LinkedHashMap<>();
        for (int index = 0; index < 173; index++) {
            int[] traits = new int[15];
            java.util.Arrays.fill(traits, 10);
            traits[index % traits.length] = 11;
            profiles.put("synthetic-role-" + String.format("%03d", index), traits);
        }
        long profileLookupStarted = System.nanoTime();
        int[] selected = profiles.get("synthetic-role-086");
        long profileLookupNanos = System.nanoTime() - profileLookupStarted;
        Map<String, Double> matrix = new LinkedHashMap<>();
        var ids = profiles.keySet().stream().toList();
        for (int left = 0; left < ids.size(); left++) {
            for (int right = left + 1; right < ids.size(); right++) {
                int[] first = profiles.get(ids.get(left));
                int[] second = profiles.get(ids.get(right));
                double edge = (first[left % 15] - second[left % 15]) / 100.0;
                matrix.put(ids.get(left) + "/" + ids.get(right), edge);
            }
        }
        long buildNanos = System.nanoTime() - started;
        long memoryEstimate = profiles.size() * 15L * Integer.BYTES
                + matrix.size() * (2L * 32 + Double.BYTES);
        double lookup = matrix.get(ids.get(10) + "/" + ids.get(120));
        if (selected == null || !Double.isFinite(lookup)) {
            throw new IllegalStateException("Synthetic scalability lookup failed");
        }
        return new Result(173, matrix.size(), profileLookupNanos, buildNanos,
                memoryEstimate, 1, 0);
    }

    record Result(int profileCount, int generatedPairCount,
                  long profileLookupNanos, long matrixBuildNanos,
                  long estimatedMemoryBytes, int singleCombatLookupCount,
                  int runtimeAllPairScanCount) {
    }
}
