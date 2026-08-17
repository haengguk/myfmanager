package com.lolfm.composition;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** One frozen Phase 13D-4B.2 margin classifier for all test/audit consumers. */
final class CanonicalCompositionMarginClassifier {
    static final String VERSION = "composition-canonical-margin-classifier-v1";
    static final String SOURCE_DATASET = "POLICY_DEVELOPMENT_SET";
    static final String SOURCE_ARTIFACT_HASH = "c61ea7c3b9f705a44b7b0b8e080e5a123f8743630a285fe59e081571879a2ef1";

    enum Band { CLOSE, MEDIUM, HIGH }
    record Bounds(double closeMax, double highMin) {}

    private static final Map<String, Bounds> BOUNDS;
    static {
        Map<String, Bounds> bounds = new LinkedHashMap<>();
        bounds.put("SKIRMISH|SKIRMISH|SKIRMISH_COMBAT_SCORE", new Bounds(5.104214137066, 109.230158346492));
        bounds.put("TEAMFIGHT|TEAMFIGHT|TEAMFIGHT_COMBAT_SCORE", new Bounds(0.820000000000, 31.960000000000));
        bounds.put("SIEGE|SIEGE_COMBAT|SIEGE_PUSH_SCORE", new Bounds(0.820000000000, 22.820000000000));
        bounds.put("BASE_DEFENSE|BASE_DEFENSE|BASE_DEFENSE_SCORE", new Bounds(0.820000000000, 27.820000000000));
        BOUNDS = Map.copyOf(bounds);
    }

    private CanonicalCompositionMarginClassifier() {}

    static Set<String> keys() { return BOUNDS.keySet(); }

    static Bounds bounds(String applicationKey) {
        Bounds bounds = BOUNDS.get(applicationKey);
        if (bounds == null) throw new IllegalArgumentException("foreign application key: " + applicationKey);
        return bounds;
    }

    static Band classify(String applicationKey, double baselineGap) {
        if (!Double.isFinite(baselineGap)) throw new IllegalArgumentException("baselineGap must be finite");
        Bounds bounds = bounds(applicationKey);
        double magnitude = Math.abs(baselineGap);
        if (magnitude <= bounds.closeMax()) return Band.CLOSE;
        if (magnitude < bounds.highMin()) return Band.MEDIUM;
        return Band.HIGH;
    }

    /**
     * Rehydrates the canonical result stored before Phase 4C.1 rounded gaps to 12 decimals.
     * The persisted band is consulted only when serialization collapsed a value onto an
     * inclusive frozen boundary; every non-boundary row is classified from the gap alone.
     */
    static Band classifySerialized(String applicationKey, double serializedBaselineGap, String persistedBand) {
        Band calculated = classify(applicationKey, serializedBaselineGap);
        Band persisted;
        try {
            persisted = Band.valueOf(persistedBand);
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("unknown persisted margin band: " + persistedBand, e);
        }
        if (calculated == persisted) return calculated;
        Bounds bounds = bounds(applicationKey);
        double magnitude = Math.abs(serializedBaselineGap);
        boolean roundedCloseBoundary = Double.compare(magnitude, bounds.closeMax()) == 0
                && calculated == Band.CLOSE && persisted == Band.MEDIUM;
        boolean roundedHighBoundary = Double.compare(magnitude, bounds.highMin()) == 0
                && calculated == Band.HIGH && persisted == Band.MEDIUM;
        if (roundedCloseBoundary || roundedHighBoundary) return persisted;
        throw new IllegalArgumentException("persisted margin band conflicts away from a frozen boundary");
    }
}
