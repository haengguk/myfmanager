package com.lolfm.champion;

public record ChampionMatchMetadata(String championPoolVersion, String championBalanceVersion,
                                    String championPowerProfileVersion,boolean championPowerEnabled,String riotDataVersion, ChampionSelectionMode selectionMode,
                                    ChampionLineupSnapshot blue, ChampionLineupSnapshot red) { }
