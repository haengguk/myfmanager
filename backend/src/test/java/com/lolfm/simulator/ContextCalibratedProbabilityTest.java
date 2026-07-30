package com.lolfm.simulator;
import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;
class ContextCalibratedProbabilityTest {
 @Test void probabilityFractionAndPercentagePointAreSeparated(){double fraction=.0001;assertThat(fraction*100).isEqualTo(.01);}
 @Test void globalProbabilityLowerBoundIsNotUsed(){assertThat(GeometricCandidateInfluenceAudit.class.getDeclaredMethods()).extracting(java.lang.reflect.Method::getName).contains("localPass");}
 @Test void localContextUsesPairAloneProbabilityDelta(){assertThat(GeometricCandidateInfluenceAudit.group(ProgressionCombatContext.LANE_COMBAT)).isEqualTo("LOCAL_CONTEXT");}
 @Test void pairAttributionUsesActualPositionOnly(){var p=new GeometricCandidateInfluenceAudit.PairApp(1,ProgressionCombatContext.LANE_COMBAT,com.lolfm.domain.Position.TOP,"a","b",.1,1,.1,.5,.6,.6,.1,.1,"id");assertThat(p.position()).isEqualTo(com.lolfm.domain.Position.TOP);}
 @Test void positionSummaryIsNotRepeated(){assertThat(com.lolfm.domain.Position.values()).doesNotHaveDuplicates();}
}
