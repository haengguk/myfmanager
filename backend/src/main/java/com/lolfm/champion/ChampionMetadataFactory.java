package com.lolfm.champion;

import com.lolfm.domain.Position;
import com.lolfm.simulator.ItemProgressStage;
import com.lolfm.simulator.PlayerKey;
import com.lolfm.simulator.TeamSide;

public final class ChampionMetadataFactory {
    private ChampionMetadataFactory() { }
    public static ChampionMatchMetadata create(ChampionCatalog catalog,ChampionPowerProfileCatalog profiles,boolean enabled, MatchChampionAssignments assignments) {
        return new ChampionMatchMetadata(catalog.championPoolVersion(), catalog.championBalanceVersion(),
                profiles.profileVersion(), enabled, catalog.riotDataVersion(), assignments.selectionMode(),
                lineup(catalog, profiles, enabled, assignments, TeamSide.BLUE),
                lineup(catalog, profiles, enabled, assignments, TeamSide.RED));
    }
    private static ChampionLineupSnapshot lineup(ChampionCatalog catalog, ChampionPowerProfileCatalog profiles,
                                                  boolean enabled, MatchChampionAssignments assignments, TeamSide side) {
        return new ChampionLineupSnapshot(champion(catalog, profiles, enabled, assignments, side, Position.TOP),
                champion(catalog, profiles, enabled, assignments, side, Position.JUNGLE),
                champion(catalog, profiles, enabled, assignments, side, Position.MID),
                champion(catalog, profiles, enabled, assignments, side, Position.ADC),
                champion(catalog, profiles, enabled, assignments, side, Position.SUPPORT));
    }
    private static ChampionSnapshot champion(ChampionCatalog catalog, ChampionPowerProfileCatalog profiles,
                                             boolean enabled, MatchChampionAssignments assignments,
                                             TeamSide side, Position position) {
        ChampionId id = assignments.get(new PlayerKey(side, position)).championId();
        return ChampionSnapshot.from(catalog.get(id), profiles.get(id), 1, ItemProgressStage.STARTING, enabled);
    }
}
