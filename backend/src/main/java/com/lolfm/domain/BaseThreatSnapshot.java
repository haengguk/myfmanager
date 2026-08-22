package com.lolfm.domain;
import com.lolfm.simulator.*;import java.util.Set;
public record BaseThreatSnapshot(TeamSide defendingSide,BaseThreatLevel overallLevel,Set<Lane> threatenedLanes,Lane deepestThreatLane,LateGameStructureTarget nextThreatenedStructure,int destroyedInhibitorCount,int remainingNexusTurrets,boolean nexusExposed,boolean nexusAlive){public BaseThreatSnapshot{threatenedLanes=DeterministicEnumSet.copyOfNullable(Lane.class,threatenedLanes);}}
