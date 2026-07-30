package com.lolfm.simulator;
import static org.assertj.core.api.Assertions.assertThat;
import java.util.Random;
import org.junit.jupiter.api.Test;
class ChampionMatchupProbabilityDeltaTest {
 @Test void probabilityEvaluationConsumesNoRandom(){var r=new CountingRandom();var e=new CombatOutcomeProbabilityEvaluator();double p=e.uniformAdvantageProbability(3);assertThat(p).isBetween(0d,1d);assertThat(r.calls).isZero();}
 @Test void productionUniformResolutionConsumesExactlyOneDraw(){var r=new CountingRandom();new CombatOutcomeProbabilityEvaluator().resolveUniformAdvantageScore(0,r);assertThat(r.calls).isOne();}
 @Test void weightedSelectionUsesProductionWeights(){assertThat(new CombatOutcomeProbabilityEvaluator().weightedSelectionProbability(3,1)).isEqualTo(.75);}
 static final class CountingRandom extends Random{int calls;public double nextDouble(){calls++;return .5;}}
}
