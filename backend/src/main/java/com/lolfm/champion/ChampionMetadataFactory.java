package com.lolfm.champion;

import com.lolfm.domain.Position;
import com.lolfm.simulator.PlayerKey;
import com.lolfm.simulator.TeamSide;

public final class ChampionMetadataFactory {
    private ChampionMetadataFactory() { }
    public static ChampionMatchMetadata create(ChampionCatalog catalog, MatchChampionAssignments assignments) {
        return new ChampionMatchMetadata(catalog.championPoolVersion(), catalog.championBalanceVersion(),
                catalog.riotDataVersion(), assignments.selectionMode(), lineup(catalog, assignments, TeamSide.BLUE),
                lineup(catalog, assignments, TeamSide.RED));
    }
    private static ChampionLineupSnapshot lineup(ChampionCatalog catalog, MatchChampionAssignments assignments, TeamSide side) {
        return new ChampionLineupSnapshot(champion(catalog, assignments, side, Position.TOP),
                champion(catalog, assignments, side, Position.JUNGLE), champion(catalog, assignments, side, Position.MID),
                champion(catalog, assignments, side, Position.ADC), champion(catalog, assignments, side, Position.SUPPORT));
    }
    private static ChampionSnapshot champion(ChampionCatalog catalog, MatchChampionAssignments assignments,
                                             TeamSide side, Position position) {
        return ChampionSnapshot.from(catalog.get(assignments.get(new PlayerKey(side, position)).championId()));
    }
}
