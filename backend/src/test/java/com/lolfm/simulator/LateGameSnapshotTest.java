package com.lolfm.simulator;import static org.junit.jupiter.api.Assertions.*;import org.junit.jupiter.api.Test;
class LateGameSnapshotTest{
 @Test void pastSnapshotKeepsImmutableBaseThreat(){GameState s=LateGameTestSupport.state();LateGameTestSupport.midGameAt(s,1800);new LateGameMacroResolver().transitionIfDue(s,new MidGameMacroResolver());SnapshotFactory f=new SnapshotFactory();var before=f.create(s);LateGameTestSupport.destroyThroughInhibitorTower(s,TeamSide.RED,Lane.MID);assertEquals(BaseThreatLevel.NONE,before.getLateGame().redBaseThreat().overallLevel());assertEquals(BaseThreatLevel.INHIBITOR_THREAT,f.create(s).getLateGame().redBaseThreat().overallLevel());}
}
