package com.lolfm.simulator;
import static org.assertj.core.api.Assertions.*;
import com.lolfm.domain.Position;
import java.util.*;
import org.junit.jupiter.api.Test;
class CombatProgressionIntegrationTest {
 private GameState state(){return LateGameTestSupport.state();}
 private PlayerState blue(GameState s,Position p){return s.getBlueTeamState().playerAt(p);}private PlayerState red(GameState s,Position p){return s.getRedTeamState().playerAt(p);}
 private void grow(PlayerState p){p.getProgressionState().awardExperience(ExperienceSource.KILL,7300,1);p.getProgressionState().awardEarnedGold(7000,GoldSource.KILL,1);}
 @Test void actualParticipantAverageExcludesNonparticipants(){GameState s=state();grow(blue(s,Position.TOP));grow(blue(s,Position.MID));var x=new CombatProgressionEvaluator().evaluate(s,ProgressionCombatContext.TEAMFIGHT,List.of(blue(s,Position.TOP)),List.of(red(s,Position.TOP)));assertThat(x.ownParticipantCount()).isOne();assertThat(x.ownAveragePower()).isCloseTo(2.85,within(1e-9));}
 @Test void deadParticipantIsExcluded(){GameState s=state();PlayerState top=blue(s,Position.TOP);grow(top);top.markDead(0,30);var x=new CombatProgressionEvaluator().evaluate(s,ProgressionCombatContext.TEAMFIGHT,List.of(top,blue(s,Position.MID)),List.of(red(s,Position.TOP)));assertThat(x.ownParticipantCount()).isOne();assertThat(x.ownAveragePower()).isZero();}
 @Test void duplicateParticipantIsCountedOnce(){GameState s=state();PlayerState top=blue(s,Position.TOP);grow(top);var x=new CombatProgressionEvaluator().evaluate(s,ProgressionCombatContext.TEAMFIGHT,List.of(top,top),List.of(red(s,Position.TOP)));assertThat(x.ownParticipantCount()).isOne();}
 @Test void blueRedEdgeIsSymmetric(){GameState s=state();grow(blue(s,Position.TOP));CombatProgressionEvaluator e=new CombatProgressionEvaluator();double a=e.contribution(s,ProgressionCombatContext.TEAMFIGHT,List.of(blue(s,Position.TOP)),List.of(red(s,Position.TOP)));double b=e.contribution(s,ProgressionCombatContext.TEAMFIGHT,List.of(red(s,Position.TOP)),List.of(blue(s,Position.TOP)));assertThat(a).isEqualTo(-b);}
 @Test void everyCombatContextUsesConfiguredMultiplier(){GameState s=state();grow(blue(s,Position.TOP));for(ProgressionCombatContext c:ProgressionCombatContext.values()){var x=new CombatProgressionEvaluator().evaluate(s,c,List.of(blue(s,Position.TOP)),List.of(red(s,Position.TOP)));assertThat(x.finalContribution()).isCloseTo(x.progressionEdge()*ProgressionRuleConfig.contextMultiplier(c),within(1e-9));}}
 @Test void powerOffKeepsStateAndReturnsZero(){GameState s=state();grow(blue(s,Position.TOP));s.configureProgression(true,false);var x=new CombatProgressionEvaluator().evaluate(s,ProgressionCombatContext.TEAMFIGHT,List.of(blue(s,Position.TOP)),List.of(red(s,Position.TOP)));assertThat(blue(s,Position.TOP).getProgressionState().getLevel()).isEqualTo(11);assertThat(x.finalContribution()).isZero();}
 @Test void progressionAdvantageIncreasesCombatScoreAdditively(){GameState s=state();grow(blue(s,Position.TOP));double base=10;double score=base+new CombatProgressionEvaluator().contribution(s,ProgressionCombatContext.LANE_COMBAT,List.of(blue(s,Position.TOP)),List.of(red(s,Position.TOP)));assertThat(score).isGreaterThan(base);}
 @Test void maximumPowerIsClamped(){GameState s=state();PlayerState p=blue(s,Position.TOP);p.getProgressionState().awardExperience(ExperienceSource.KILL,100000,1);p.getProgressionState().awardEarnedGold(100000,GoldSource.KILL,1);assertThat(new PlayerProgressionPowerEvaluator().evaluate(p,ProgressionCombatContext.TEAMFIGHT,true).clampedTotalPower()).isLessThanOrEqualTo(7);}
}
