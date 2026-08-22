package com.lolfm.simulator;

/** Structured result of deterministic jungle-gank base and tempo eligibility. */
public enum JungleGankIneligibility {
    NONE,
    JUNGLER_UNAVAILABLE,
    JUNGLE_ACTION_COOLDOWN,
    NO_ELIGIBLE_LANE,
    JUNGLER_NOT_TEMPO_READY
}
