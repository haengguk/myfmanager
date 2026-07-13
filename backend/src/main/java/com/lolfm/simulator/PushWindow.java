package com.lolfm.simulator;

public record PushWindow(
        TeamSide attackingSide,
        TeamSide defendingSide,
        Lane selectedLane,
        int startedAtSeconds,
        int availableUntilSeconds,
        int nextStructureAttackSeconds,
        PushReason reason,
        int maximumStructures
) {
}
