package com.lolfm.champion;

import java.util.List;

public record ChampionCatalogResponse(String championPoolVersion, String championBalanceVersion,
                                      String championPowerProfileVersion,String riotDataVersion, ChampionSelectionRequest defaultSelection,
                                      List<ChampionDefinition> champions) {
    public ChampionCatalogResponse { champions = List.copyOf(champions); }
}
