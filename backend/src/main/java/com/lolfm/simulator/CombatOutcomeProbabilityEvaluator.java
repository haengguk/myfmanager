package com.lolfm.simulator;

import java.util.Random;

/** Shared production probability mapping; stateless and Random-free when evaluating probability. */
public final class CombatOutcomeProbabilityEvaluator {
    public static final double UNIFORM_ADVANTAGE_SPAN = 56.0;

    public double uniformAdvantageProbability(double scoreWithoutNoise) {
        return clamp(.5 + scoreWithoutNoise / UNIFORM_ADVANTAGE_SPAN, 0, 1);
    }

    public double resolveUniformAdvantageScore(double scoreWithoutNoise, Random random) {
        return scoreWithoutNoise + (random.nextDouble() - .5) * UNIFORM_ADVANTAGE_SPAN;
    }

    public double weightedSelectionProbability(double ownWeight, double opponentWeight) {
        double total = ownWeight + opponentWeight;
        return total <= 0 ? .5 : clamp(ownWeight / total, 0, 1);
    }

    public double mappedWinProbability(ProgressionCombatContext context, double score) {
        return switch (context) {
            case LANE_COMBAT -> new LaneCombatResolver().attackerWinChance(score);
            case JUNGLE_GANK -> new JungleGankResolver().gankSuccessChance(score);
            case COUNTER_GANK -> new CounterGankResolver().attackingSideWinChance(score);
            case ROAM -> new RoamResolver().successChance(score);
            case TEAMFIGHT, OBJECTIVE_FIGHT, LATE_GAME_SIEGE, BASE_DEFENSE -> uniformAdvantageProbability(score);
            case GENERIC_SKIRMISH -> uniformAdvantageProbability(score);
        };
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
