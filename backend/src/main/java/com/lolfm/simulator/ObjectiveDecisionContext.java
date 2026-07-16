package com.lolfm.simulator;

import java.util.List;

/** Immutable decision pre-state shared by initiative and responder selection. */
record ObjectiveDecisionContext(
        int evaluationTimeSeconds,
        ObjectiveType objectiveType,
        TeamSide initiativeSide,
        TeamSide responderSide,
        boolean objectiveAvailable,
        int blueAliveCount,
        int redAliveCount,
        int blueGold,
        int redGold,
        int blueKills,
        int redKills,
        double blueAverageTeamfighting,
        double redAverageTeamfighting,
        double blueRelevantFarming,
        double redRelevantFarming,
        int blueDragonStacks,
        int redDragonStacks,
        TeamSide soulOwner,
        boolean blueBaronBuff,
        boolean redBaronBuff,
        double signedObjectivePriority,
        boolean priorityAvailable,
        boolean majorCombatAvailable,
        boolean blueStructureActionAvailable,
        boolean redStructureActionAvailable,
        TradeTarget blueTradeTarget,
        TradeTarget redTradeTarget
) {
    int alive(TeamSide side) { return side == TeamSide.BLUE ? blueAliveCount : redAliveCount; }
    int gold(TeamSide side) { return side == TeamSide.BLUE ? blueGold : redGold; }
    double teamfighting(TeamSide side) { return side == TeamSide.BLUE ? blueAverageTeamfighting : redAverageTeamfighting; }
    double farming(TeamSide side) { return side == TeamSide.BLUE ? blueRelevantFarming : redRelevantFarming; }
    int dragonStacks(TeamSide side) { return side == TeamSide.BLUE ? blueDragonStacks : redDragonStacks; }
    boolean hasBaron(TeamSide side) { return side == TeamSide.BLUE ? blueBaronBuff : redBaronBuff; }
    boolean structureAvailable(TeamSide side) { return side == TeamSide.BLUE ? blueStructureActionAvailable : redStructureActionAvailable; }
    TradeTarget tradeTarget(TeamSide side) { return side == TeamSide.BLUE ? blueTradeTarget : redTradeTarget; }
    record TradeTarget(Lane lane, TowerTier towerTier, PlayerState primaryPusher) { }
}
