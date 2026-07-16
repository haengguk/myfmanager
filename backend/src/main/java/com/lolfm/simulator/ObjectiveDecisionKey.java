package com.lolfm.simulator;
public record ObjectiveDecisionKey(ObjectiveType objectiveType, int spawnedAtSeconds, int evaluationTimeSeconds, TeamSide initiativeSide) { }
