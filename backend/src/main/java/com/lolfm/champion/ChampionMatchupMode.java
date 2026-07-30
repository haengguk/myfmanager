package com.lolfm.champion;

public enum ChampionMatchupMode {
    OFF,
    GEOMETRIC_V2,
    /** Retained only for historical diagnostics that inject a generated pair catalog. */
    @Deprecated
    ON
}
