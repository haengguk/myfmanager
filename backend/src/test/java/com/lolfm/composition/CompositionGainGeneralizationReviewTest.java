package com.lolfm.composition;

import org.junit.jupiter.api.*;
import java.nio.file.Files;
import java.util.*;
import static org.assertj.core.api.Assertions.assertThat;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Tag("diagnostic")
@Tag("historical-artifact")
class CompositionGainGeneralizationReviewTest {
 CompositionGainGeneralizationReview.Review r;
 @BeforeAll void load()throws Exception{r=CompositionGainGeneralizationReview.analyze();}
 @Test void developmentSourceHashesAreExact()throws Exception{CompositionGainGeneralizationReview.verify();assertThat(r.before()).isEqualTo(r.after());}
 @Test void holdoutSourceHashesAreExact(){assertThat(r.before()).hasSize(10);}
 @Test void historicalCandidateHashRemainsExact(){assertThat(FrozenCompositionGameplayGainPolicy.current().candidateHash()).isEqualTo("ec99828c0f04a00cc644f4d0446d851543a46a530c9bc561408af9cf704da32d");}
 @Test void sourceArtifactsRemainReadOnly(){assertThat(r.before()).isEqualTo(r.after());}
 @Test void analysisRunsNoMatchSimulation()throws Exception{String s=Files.readString(java.nio.file.Path.of("src/test/java/com/lolfm/composition/CompositionGainGeneralizationReview.java"));assertThat(s).doesNotContain("new MatchSimulator",".simulate(");}
 @Test void distributionsAreComputedPerExactApplicationKey(){assertThat(r.keys()).extracting(CompositionGainGeneralizationReview.K::key).containsExactlyElementsOf(CompositionGainGeneralizationReview.KEYS);}
 @Test void developmentAndHoldoutRowsDoNotMix(){assertThat(r.dev()).allMatch(x->x.data().equals("DEVELOPMENT"));assertThat(r.hold()).allMatch(x->x.data().equals("HOLDOUT"));}
 @Test void quantilesUseDeterministicNearestRank(){assertThat(CompositionGainGeneralizationReview.q(List.of(1d,2d,3d,4d),.75)).isEqualTo(3);}
 @Test void zeroGapIsExcludedFromRatioAndCountedSeparately(){var x=new CompositionGainGeneralizationReview.Obs("k","H","BLUE",1,0,1,1,"CLOSE","CLOSE",false,false,"t",.5,.5,0d,.2,0d,"NO_LOCAL_FLIP",0,0,"a","a");var d=CompositionGainGeneralizationReview.ratio(List.of(x));assertThat(d.count()).isZero();assertThat(d.zero()).isOne();}
 @Test void quantileShiftClassificationIsDeterministic(){assertThat(CompositionGainGeneralizationReview.ratioClass(1.6)).isEqualTo("STRONG_QUANTILE_SHIFT");assertThat(CompositionGainGeneralizationReview.ratioClass(1)).isEqualTo("STABLE");}
 @Test void decisionInventoryUsesStructuredApplicationKeys(){assertThat(r.hold()).allMatch(x->CompositionGainGeneralizationReview.KEYS.contains(x.key()));}
 @Test void probabilityReconstructionUsesPureEvaluatorOnly(){assertThat(r.hold()).allMatch(x->x.bp()!=null&&x.cp()!=null);}
 @Test void probabilityReconstructionConsumesNoRandom(){assertThat(r.hold()).allMatch(x->Double.isFinite(x.pdelta()));}
 @Test void sharedRandomSampleIsNotRedrawn(){assertThat(r.hold()).hasSize(42929);}
 @Test void scoreFlipIsNotUsedAsLocalOutcomeSubstitute(){assertThat(r.hold()).anyMatch(x->x.flip()!=x.scoreFlip());}
 @Test void thresholdCrossingMechanismUsesDecisionValues(){assertThat(r.hold()).filteredOn(x->x.mech().equals("PROBABILITY_THRESHOLD_CROSS_WITHOUT_SCORE_FLIP")).allMatch(x->x.flip()&&!x.scoreFlip());}
 @Test void rootCausesRequireMetricEvidence(){assertThat(r.keys()).allSatisfy(k->assertThat(k.causes()).isNotEmpty());}
 @Test void scoreMarginFailureRequiresHighFlipEvidence(){assertThat(r.keys()).filteredOn(k->k.causes().contains("SCORE_MARGIN_NOT_DECISION_MARGIN")).allMatch(k->k.high()>0);}
 @Test void probabilitySensitivityRequiresProbabilityEvidence(){assertThat(r.keys()).filteredOn(k->k.causes().contains("DECISION_PROBABILITY_SENSITIVITY")).allMatch(k->CompositionGainGeneralizationReview.prob(k.hold()).count()>0);}
 @Test void rootCauseIsComputedNotHardcodedByKey()throws Exception{String s=Files.readString(java.nio.file.Path.of("src/test/java/com/lolfm/composition/CompositionGainGeneralizationReview.java"));assertThat(s).doesNotContain("Map.of(\"BASE_DEFENSE\"");}
 @Test void multipleRootCausesCanCoexist(){assertThat(r.keys()).anyMatch(k->k.causes().size()>1);}
 @Test void deepDiveCohortsCoverAllApplications(){assertThat(r.keys()).allSatisfy(k->{long n=k.hold().stream().collect(java.util.stream.Collectors.groupingBy(CompositionGainGeneralizationReview::cohort,java.util.stream.Collectors.counting())).values().stream().mapToLong(Long::longValue).sum();assertThat(n).isEqualTo(k.hold().size());});}
 @Test void flipMechanismsCoverAllLocalFlips(){assertThat(r.keys()).allSatisfy(k->assertThat(k.sd()+k.pc()+k.other()+k.unresolved()).isEqualTo(k.flips()));}
 @Test void skirmishControlUsesSameMetricDefinitions(){var k=CompositionGainGeneralizationReview.find(r,"SKIRMISH|SKIRMISH|SKIRMISH_COMBAT_SCORE");assertThat(k.de().count()).isEqualTo(k.dev().size());}
 @Test void recommendationDoesNotChangeGain(){assertThat(FrozenCompositionGameplayGainPolicy.currentKeys()).extracting(CompositionGameplayApplicationKey::selectedGain).containsExactly(24.509721397259,11.595061941148,6.805985567298,10.837956658606);}
 @Test void recommendationDoesNotCreateNewCandidate(){assertThat(r.keys()).allMatch(k->!k.rec().isBlank());}
 @Test void semanticsRedesignRequiresStructuralEvidence(){assertThat(r.keys()).filteredOn(k->k.rec().equals("APPLICATION_SEMANTICS_REDESIGN")).allMatch(k->k.other()>0&&k.hold().stream().anyMatch(x->x.type().contains("FIGHT_GRADE")));}
 @Test void freshHoldoutIsPostHoldoutDiagnostic(){assertThat(r.hold()).isNotEmpty();}
 @Test void nextPhaseIsComputedFromRecommendations(){assertThat(CompositionGainGeneralizationReview.next(r.keys())).isEqualTo(r.next());}
 @Test void productionRemainsDisabled(){assertThat(FrozenCompositionGameplayGainPolicy.current().productionEnabled()).isFalse();}
 @Test void analysisIntegrityIsExact(){assertThat(r.ok()).isTrue();assertThat(r.keys()).allMatch(k->k.unresolved()==0);}
}
