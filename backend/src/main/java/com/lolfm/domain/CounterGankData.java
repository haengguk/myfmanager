package com.lolfm.domain;
import com.lolfm.simulator.*;
import java.util.List;
public record CounterGankData(TeamSide attackingSide, TeamSide defendingSide, String attackingJunglerPlayerId,
 String defendingJunglerPlayerId, Lane targetLane, boolean defenderInitiallyTriggered, double responseChance,
 CounterGankOutcome outcome, TeamSide winningSide, String killerPlayerId, String victimPlayerId,
 List<String> assistantPlayerIds, double pressureBefore, double pressureAfter, double enemyOverextension,
 int attackingJungleFarmBlockedUntilSeconds, int defendingJungleFarmBlockedUntilSeconds,
 double combatEdge, double decisiveChance, double attackingSideWinChance,
 double attackingGroupMechanics, double defendingGroupMechanics, double attackingGroupGold, double defendingGroupGold) {
 public CounterGankData { assistantPlayerIds=List.copyOf(assistantPlayerIds); }
}
