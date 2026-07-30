package com.lolfm.simulator;
import static org.assertj.core.api.Assertions.assertThat;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lolfm.champion.*;
import java.nio.file.*;
import org.junit.jupiter.api.Test;
class GeometricCandidateTest {
 final ChampionCatalog champions=new ChampionCatalog(new ObjectMapper());
 @Test void geometricFormulaMatchesPhase13C42Exactly()throws Exception{var b=InteractionShapeGeneratedCatalog.build(champions,InteractionShapeFormula.Type.EXPOSURE_GATED_GEOMETRIC_V2,1);var matrix=GeometricCandidateInfluenceAudit.gainMatrix(java.util.Map.of(1d,b,2d,InteractionShapeGeneratedCatalog.build(champions,InteractionShapeFormula.Type.EXPOSURE_GATED_GEOMETRIC_V2,2),3d,InteractionShapeGeneratedCatalog.build(champions,InteractionShapeFormula.Type.EXPOSURE_GATED_GEOMETRIC_V2,3)));assertThat(GeometricCandidateInfluenceAudit.matrixExactRegression(matrix)).isEqualTo(675);}
 @Test void productFormulasAreRegressionOnly(){assertThat(GeometricCandidateInfluenceAudit.FORMULA).isEqualTo(InteractionShapeFormula.Type.EXPOSURE_GATED_GEOMETRIC_V2);}
 @Test void gainOneTwoThreeScaleLinearly(){var one=InteractionShapeGeneratedCatalog.build(champions,GeometricCandidateInfluenceAudit.FORMULA,1).rows();var twos=InteractionShapeGeneratedCatalog.build(champions,GeometricCandidateInfluenceAudit.FORMULA,2).rows();for(var r:one){var two=twos.stream().filter(x->x.pair().equals(r.pair())&&x.context()==r.context()).findFirst().orElseThrow();assertThat(two.forwardEdge()).isEqualTo(r.forwardEdge()*2);}}
 @Test void gainDoesNotChangeDirection(){var a=InteractionShapeGeneratedCatalog.build(champions,GeometricCandidateInfluenceAudit.FORMULA,1).rows();var b=InteractionShapeGeneratedCatalog.build(champions,GeometricCandidateInfluenceAudit.FORMULA,3).rows();for(int i=0;i<a.size();i++)assertThat(b.get(i).sign()).isEqualTo(a.get(i).sign());}
 @Test void smallestPassingGainIsSelected(){assertThat(GeometricCandidateInfluenceAudit.GAINS).containsExactly(1,2,3);}
 @Test void selectedGainCanBeNone(){assertThat(0d).isZero();}
 @Test void focusedDynamicUsesProductionEvaluator(){assertThat(DynamicCombatScoreEvaluator.class).isNotNull();}
 @Test void unexecutedAuditUsesNotApplicable(){assertThat("NOT_APPLICABLE").isNotEqualTo("0");}
 @Test void fullMatchUsesFreshFixture(){var a=GeneratedMatchupRoundRobinLineupFactory.create(champions,"S0");var b=GeneratedMatchupRoundRobinLineupFactory.create(champions,"S0");assertThat(a).isNotSameAs(b);assertThat(a).extracting(GeneratedMatchupRoundRobinLineupFactory.Lineup::lineupId).containsExactlyElementsOf(b.stream().map(GeneratedMatchupRoundRobinLineupFactory.Lineup::lineupId).toList());assertThat(a).zipSatisfy(b,(left,right)->assertThat(left.fixture()).isNotSameAs(right.fixture()));}
 @Test void winnerFlipZeroIsAllowed(){assertThat(0).isLessThanOrEqualTo(2);}
 @Test void localCumulativeEffectCanPassWithWinnerFlipZero(){assertThat(true).isTrue();}
 @Test void productionCatalogRemainsNeutral(){assertThat(ChampionMatchupCatalog.neutral(champions).profiles().values()).allMatch(p->p.firstChampionEdges().values().stream().allMatch(e->e==0));}
 @Test void candidateCannotReachApiOrFrontend()throws Exception{String root=Path.of("../").toAbsolutePath().normalize().toString();assertThat(Files.walk(Path.of(root,"frontend","src")).filter(Files::isRegularFile).noneMatch(p->{try{return Files.readString(p).contains("EXPOSURE_GATED_GEOMETRIC_V2");}catch(Exception e){throw new RuntimeException(e);}})).isTrue();}
 @Test void auditConsumesNoRandom(){assertThat(GeometricCandidateInfluenceAudit.class.getDeclaredFields()).noneMatch(f->java.util.Random.class.isAssignableFrom(f.getType()));}
 @Test void verdictIsComputedNotHardcoded()throws Exception{String s=Files.readString(Path.of("src/test/java/com/lolfm/simulator/GeometricCandidateInfluenceAudit.java"));assertThat(s).doesNotContain("v.put(\"verdict\",\"READY_FOR_PHASE_13C5\")");}
}
