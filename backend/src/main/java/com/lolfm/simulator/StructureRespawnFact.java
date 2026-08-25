package com.lolfm.simulator;

public record StructureRespawnFact(
        int timeSeconds,
        TeamSide defendingSide,
        StructureTargetId target,
        double currentHealth,
        double maxHealth
) { }
