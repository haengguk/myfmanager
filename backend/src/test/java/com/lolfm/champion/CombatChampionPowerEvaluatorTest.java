package com.lolfm.champion;

import static org.assertj.core.api.Assertions.*;
import com.lolfm.domain.PlayerAttributes;
import com.lolfm.domain.Position;
import com.lolfm.simulator.*;
import java.util.List;
import org.junit.jupiter.api.Test;

class CombatChampionPowerEvaluatorTest {
    @Test void actualParticipantsAreAveragedAndSwapReversesSignedEdge() {
        var f = new ChampionPowerTestFixture(true); var evaluator = new CombatChampionPowerEvaluator();
        PlayerState blue = f.blue.playerAt(Position.TOP), red = f.red.playerAt(Position.TOP);
        ChampionPowerTestFixture.grow(blue,1720,3500); ChampionPowerTestFixture.grow(red,1720,3500);
        var forward = evaluator.evaluate(f.state,List.of(blue),List.of(red),ProgressionCombatContext.LANE_COMBAT,ProgressionApplicationStage.COMBAT_SCORE);
        var reverse = evaluator.evaluate(f.state,List.of(red),List.of(blue),ProgressionCombatContext.LANE_COMBAT,ProgressionApplicationStage.COMBAT_SCORE);
        assertThat(forward.ownParticipantCount()).isOne(); assertThat(forward.enemyParticipantCount()).isOne();
        assertThat(reverse.finalChampionEdge()).isEqualTo(-forward.finalChampionEdge());
    }
    @Test void deadDuplicateAndNonMatchParticipantsAreExcludedBeforeIdentityLookup() {
        var f = new ChampionPowerTestFixture(true); PlayerState alive=f.blue.playerAt(Position.TOP), dead=f.blue.playerAt(Position.MID);
        dead.markDead(0,60); PlayerState outside=new PlayerState("OUTSIDE",Position.TOP,new PlayerAttributes(20,20,20,20),500);
        var value = new CombatChampionPowerEvaluator().evaluate(f.state,List.of(alive,alive,dead,outside),List.of(f.red.playerAt(Position.TOP)),ProgressionCombatContext.TEAMFIGHT,ProgressionApplicationStage.FIGHT_GRADE);
        assertThat(value.ownParticipantCount()).isOne(); assertThat(value.ownParticipants()).containsOnlyKeys(new PlayerKey(TeamSide.BLUE,Position.TOP));
    }
    @Test void teamEdgeIsClampedAndFeatureOffReturnsZeroWithoutHidingRawMetadata() {
        var on = new ChampionPowerTestFixture(true); PlayerState renekton=on.blue.playerAt(Position.TOP),jinx=on.red.playerAt(Position.ADC);
        ChampionPowerTestFixture.grow(renekton,1720,3500); ChampionPowerTestFixture.grow(jinx,1720,3500);
        var clamped = new CombatChampionPowerEvaluator().evaluate(on.state,List.of(renekton),List.of(jinx),ProgressionCombatContext.LANE_COMBAT,ProgressionApplicationStage.COMBAT_SCORE);
        assertThat(clamped.rawChampionEdge()).isGreaterThan(ChampionPowerRuleConfig.MAX_ABS_TEAM_CHAMPION_EDGE);
        assertThat(clamped.finalChampionEdge()).isEqualTo(ChampionPowerRuleConfig.MAX_ABS_TEAM_CHAMPION_EDGE); assertThat(clamped.teamEdgeClampApplied()).isTrue();
        var off = new ChampionPowerTestFixture(false); var neutral = new CombatChampionPowerEvaluator().evaluate(off.state,List.of(off.blue.playerAt(Position.TOP)),List.of(off.red.playerAt(Position.TOP)),ProgressionCombatContext.LANE_COMBAT,ProgressionApplicationStage.COMBAT_SCORE);
        assertThat(neutral.finalContribution()).isZero(); assertThat(neutral.ownParticipants()).isNotEmpty(); assertThat(neutral.championPowerEnabled()).isFalse();
    }
}
