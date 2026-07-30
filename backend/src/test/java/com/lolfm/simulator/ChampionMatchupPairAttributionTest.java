package com.lolfm.simulator;
import static org.assertj.core.api.Assertions.assertThat;
import com.lolfm.domain.*;
import java.util.List;
import org.junit.jupiter.api.Test;
class ChampionMatchupPairAttributionTest {
 @Test void combatSampleKeepsActualStructuredParticipants(){GameState s=state();new CombatProgressionEvaluator().evaluate(s,ProgressionCombatContext.LANE_COMBAT,List.of(s.getBlueTeamState().playerAt(Position.TOP)),List.of(s.getRedTeamState().playerAt(Position.TOP)));var x=s.getChampionPowerExecutionStats().snapshot().samples().getLast();assertThat(x.ownParticipantKeys()).containsExactly(new PlayerKey(TeamSide.BLUE,Position.TOP));assertThat(x.enemyParticipantKeys()).containsExactly(new PlayerKey(TeamSide.RED,Position.TOP));}
 private GameState state(){return new GameState(team("b"),team("r"));}private TeamState team(String n){return new TeamState(n,java.util.Arrays.stream(Position.values()).map(p->new PlayerState(n+p,p,new PlayerAttributes(14,14,14,14),500)).toList());}
}
