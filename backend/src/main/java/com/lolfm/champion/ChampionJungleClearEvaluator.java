package com.lolfm.champion;

import java.util.Objects;

/** No-Random evaluator for the disabled phase-based jungle-clear foundation. */
public final class ChampionJungleClearEvaluator {
    public double evaluate(ChampionJungleClearProfile profile, int timeSeconds) {
        Objects.requireNonNull(profile, "profile");
        if (!profile.gameplayEnabled()) return 1.0;
        if (timeSeconds < 900) return profile.early();
        if (timeSeconds < 1_800) return profile.mid();
        return profile.late();
    }
}
