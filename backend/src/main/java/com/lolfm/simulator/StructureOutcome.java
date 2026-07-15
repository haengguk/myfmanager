package com.lolfm.simulator;

import com.lolfm.domain.OuterTurretSiegeData;

public record StructureOutcome(
        TeamSide attackingSide,
        TeamSide defendingSide,
        StructureKind structureKind,
        Lane lane,
        TowerTier towerTier,
        int occurredAtSeconds,
        PushReason reason,
        boolean gameEnded,
        StructureActionSource source,
        OuterTurretSiegeData outerTurretSiege
) {
    public StructureOutcome(TeamSide attackingSide,TeamSide defendingSide,StructureKind structureKind,Lane lane,TowerTier towerTier,int occurredAtSeconds,PushReason reason,boolean gameEnded) {
        this(attackingSide,defendingSide,structureKind,lane,towerTier,occurredAtSeconds,reason,gameEnded,source(reason),null);
    }
    public StructureOutcome {
        if (structureKind == StructureKind.TOWER && (lane == null || towerTier == null)) throw new IllegalArgumentException("Tower outcome requires lane and tier.");
        if (structureKind == StructureKind.INHIBITOR && lane == null) throw new IllegalArgumentException("Inhibitor outcome requires lane.");
        if (structureKind == StructureKind.NEXUS && !gameEnded) throw new IllegalArgumentException("Nexus destruction must end the game.");
    }
    private static StructureActionSource source(PushReason reason){
        return switch(reason){case POST_FIGHT->StructureActionSource.POST_FIGHT;case BARON_PRESSURE->StructureActionSource.BARON_PRESSURE;case MACRO_PLAY->StructureActionSource.MACRO_PLAY;case MID_GAME_MACRO->StructureActionSource.MID_GAME_MACRO;};
    }
}
