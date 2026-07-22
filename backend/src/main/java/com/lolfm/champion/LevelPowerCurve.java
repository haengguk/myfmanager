package com.lolfm.champion;

import java.util.Map;

public record LevelPowerCurve(String id, Map<Integer, Double> anchors) {
    private static final int[] LEVELS = {1, 6, 11, 16, 18};
    public LevelPowerCurve {
        anchors = Map.copyOf(anchors);
        if (anchors.size() != LEVELS.length) throw new IllegalArgumentException("Expected level anchors 1/6/11/16/18: " + id);
        for (int level : LEVELS) if (!anchors.containsKey(level) || !Double.isFinite(anchors.get(level))) throw new IllegalArgumentException("Invalid level anchor " + level + ": " + id);
    }
    public double valueAt(int requestedLevel) {
        int level = Math.max(1, Math.min(18, requestedLevel));
        if (anchors.containsKey(level)) return anchors.get(level);
        for (int i = 1; i < LEVELS.length; i++) if (level < LEVELS[i]) {
            int low = LEVELS[i - 1], high = LEVELS[i];
            double ratio = (level - low) / (double) (high - low);
            return anchors.get(low) + (anchors.get(high) - anchors.get(low)) * ratio;
        }
        return anchors.get(18);
    }
}
