package com.lolfm.champion;

public record ChampionMatchMetadata(String championPoolVersion, String championBalanceVersion,
                                    String riotDataVersion, ChampionSelectionMode selectionMode,
                                    ChampionLineupSnapshot blue, ChampionLineupSnapshot red) { }
