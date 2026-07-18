package com.lolfm.champion;

public record ChampionLineupSnapshot(ChampionSnapshot top, ChampionSnapshot jgl, ChampionSnapshot mid,
                                     ChampionSnapshot adc, ChampionSnapshot sup) { }
