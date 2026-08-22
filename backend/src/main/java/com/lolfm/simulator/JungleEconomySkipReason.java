package com.lolfm.simulator;

/** Structured reason why a scheduled jungle economy tick produced no FARM outcome. */
public enum JungleEconomySkipReason {
    MATCH_FINISHED,
    DEAD,
    MACRO_FARM_BLOCK,
    FARM_RECOVERY,
    NON_DEFAULT_ACTIVITY,
    JUNGLE_ACTION_FARM_BLOCK
}
