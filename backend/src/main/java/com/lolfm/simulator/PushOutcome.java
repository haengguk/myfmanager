package com.lolfm.simulator;

public record PushOutcome(
        TeamSide attackingSide,
        TeamSide defendingSide,
        Lane lane,
        TowerTier destroyedTowerTier,
        int occurredAtSeconds,
        PushReason reason
) {
}
