package com.lolfm.champion;

import static org.assertj.core.api.Assertions.*;
import com.lolfm.domain.Position;
import com.lolfm.simulator.*;
import java.util.List;
import org.junit.jupiter.api.Test;

class ChampionPowerIntegrationTest {
    @Test void commonAndChampionContributionsRemainSeparateAndAreAppliedExactlyOnce() {
        var f=new ChampionPowerTestFixture(true); PlayerState own=f.blue.playerAt(Position.TOP),enemy=f.red.playerAt(Position.TOP);
        ChampionPowerTestFixture.grow(own,1720,3500); ChampionPowerTestFixture.grow(enemy,1720,3500);
        int before=f.state.getChampionPowerExecutionStats().snapshot().samples().size();
        var value=new CombatProgressionEvaluator().evaluate(f.state,ProgressionCombatContext.LANE_COMBAT,List.of(own),List.of(enemy),12.5,1.25,ProgressionApplicationStage.COMBAT_SCORE);
        assertThat(value.finalContribution()).isEqualTo(value.commonProgressionContribution()+value.championContribution());
        assertThat(value.championContribution()).isEqualTo(value.championBreakdown().finalContribution());
        assertThat(f.state.getChampionPowerExecutionStats().snapshot().samples()).hasSize(before+1);
        var sample=f.state.getChampionPowerExecutionStats().snapshot().samples().getLast();
        assertThat(sample.existingScoreBeforeProgression()).isEqualTo(12.5); assertThat(sample.goldContribution()).isEqualTo(1.25);
    }
    @Test void allNineContextsPreserveOnlyTheConfiguredContextDifference() {
        var f=new ChampionPowerTestFixture(true); ChampionPowerProfileEvaluator evaluator=new ChampionPowerProfileEvaluator(f.profiles);
        ChampionId id=new ChampionId("azir"); double base=evaluator.evaluate(id,11,ItemProgressStage.SECOND_CORE,ProgressionCombatContext.LANE_COMBAT).rawPlayerChampionPower()-f.profiles.get(id).contextModifiers().get(ProgressionCombatContext.LANE_COMBAT);
        for(ProgressionCombatContext context:ProgressionCombatContext.values()) assertThat(evaluator.evaluate(id,11,ItemProgressStage.SECOND_CORE,context).rawPlayerChampionPower()-f.profiles.get(id).contextModifiers().get(context)).isCloseTo(base,within(1e-12));
    }
}
