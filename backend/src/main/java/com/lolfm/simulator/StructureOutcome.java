package com.lolfm.simulator;

public record StructureOutcome(
        TeamSide attackingSide,
        TeamSide defendingSide,
        StructureKind structureKind,
        Lane lane,
        TowerTier towerTier,
        int occurredAtSeconds,
        PushReason reason,
        boolean gameEnded
) {
    public StructureOutcome {
        if (structureKind == StructureKind.TOWER && (lane == null || towerTier == null)) {
            throw new IllegalArgumentException("Tower outcome requires lane and tier.");
        }
        if (structureKind == StructureKind.INHIBITOR && lane == null) {
            throw new IllegalArgumentException("Inhibitor outcome requires lane.");
        }
        if (structureKind == StructureKind.NEXUS && !gameEnded) {
            throw new IllegalArgumentException("Nexus destruction must end the game.");
        }
    }
}
