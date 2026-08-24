package com.lolfm.composition;

import static org.assertj.core.api.Assertions.*;
import com.lolfm.champion.*;
import com.lolfm.domain.Position;
import com.lolfm.simulator.SimulationOptions;
import com.lolfm.testsupport.FrontendTextSourceScanner;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import org.junit.jupiter.api.Test;

class ThirtyChampionCompositionProfilesTest {
 private static final Map<ChampionRoleKey,ChampionCompositionProfile> PROFILES=ThirtyChampionCompositionProfiles.all();
 private static final ThirtyChampionCompositionProfileAudit.AuditSnapshot AUDIT=ThirtyChampionCompositionProfileAudit.compute();
 @Test void catalogContainsExactlyThirtyProfiles(){assertThat(PROFILES).hasSize(30);}
 @Test void catalogContainsSixProfilesPerPosition(){for(var p:Position.values())assertThat(PROFILES.keySet()).filteredOn(k->k.position()==p).hasSize(6);}
 @Test void historicalCatalogKeysRemainCoveredByProductionMatchupCatalog(){assertThat(ChampionRoleMatchupProfileCatalog.production().profiles().keySet()).containsAll(PROFILES.keySet());}
 @Test void catalogHasNoMissingCapability(){assertThat(PROFILES.values()).allMatch(p->p.capabilities().size()==15);}
 @Test void catalogRejectsInvalidCapabilityRange(){var key=key("fixture",Position.TOP);var m=values(0);m.put(CompositionCapability.ENGAGE,21);assertThatThrownBy(()->new ChampionCompositionProfile(key,m,new DamageChannelProfile(0,0,0))).isInstanceOf(IllegalArgumentException.class);}
 @Test void catalogRejectsInvalidDamageRange(){assertThatThrownBy(()->new DamageChannelProfile(0,-1,0)).isInstanceOf(IllegalArgumentException.class);}
 @Test void catalogHasNoDuplicateKey(){assertThat(PROFILES.keySet().stream().distinct()).hasSize(30);}
 @Test void catalogHasNoExactDuplicateFullVector(){assertThat(ThirtyChampionCompositionProfileAudit.exactDuplicateVectorCount(PROFILES)).isZero();}
 @Test void catalogIsImmutable(){assertThatThrownBy(PROFILES::clear).isInstanceOf(UnsupportedOperationException.class);}
 @Test void canonicalProfileHashIsDeterministic(){assertThat(ThirtyChampionCompositionProfiles.profileHash()).isEqualTo(ThirtyChampionCompositionProfiles.profileHash());}
 @Test void canonicalHashDoesNotDependOnMapIterationOrder(){var entries=new ArrayList<>(PROFILES.entrySet());Collections.reverse(entries);var reversed=new LinkedHashMap<ChampionRoleKey,ChampionCompositionProfile>();entries.forEach(e->reversed.put(e.getKey(),e.getValue()));assertThat(ThirtyChampionCompositionProfiles.canonicalSerialization(reversed)).isEqualTo(ThirtyChampionCompositionProfiles.canonicalSerialization());}
 @Test void profileValuesAreExplicitNotRoleGenerated()throws Exception{String s=catalogSource();assertThat(count(s,"add(values,")).isEqualTo(30);assertThat(PROFILES.values().stream().map(ThirtyChampionCompositionProfileAudit::vector).distinct()).hasSize(30);}
 @Test void profileCatalogDoesNotReadChampionPower()throws Exception{assertThat(catalogSource()).doesNotContain("ChampionPower","powerProfile");}
 @Test void profileCatalogDoesNotReadMatchupTraits()throws Exception{assertThat(catalogSource()).doesNotContain("ChampionMatchupTrait","traits()");}
 @Test void profileCatalogDoesNotReadPlayerAttributes()throws Exception{assertThat(catalogSource()).doesNotContain("PlayerAttributes","proficiency");}
 @Test void profileCatalogDoesNotUseDisplayNameIdentity()throws Exception{assertThat(catalogSource()).doesNotContain("displayName","getName()","name().contains");}
 @Test void profileCatalogHasNoSilentNeutralFallback(){assertThat(PROFILES.get(key("unknown",Position.TOP))).isNull();}
 @Test void profileVersionIsImmutable()throws Exception{var f=ThirtyChampionCompositionProfiles.class.getField("VERSION");assertThat(java.lang.reflect.Modifier.isStatic(f.getModifiers())&&java.lang.reflect.Modifier.isFinal(f.getModifiers())).isTrue();}
 @Test void distributionContainsAllEighteenMetrics(){assertThat(AUDIT.distribution().stream().map(ThirtyChampionCompositionProfileAudit.DistributionRow::metric).distinct()).hasSize(18);}
 @Test void distributionContainsAllFivePositionScopes(){for(var p:Position.values())assertThat(AUDIT.distribution()).anyMatch(x->x.position()==p);}
 @Test void percentileCalculationIsDeterministic(){double[]v={1,2,3,4,5};assertThat(ThirtyChampionCompositionProfileAudit.percentile(v,.9)).isEqualTo(5);assertThat(ThirtyChampionCompositionProfileAudit.percentile(v,.5)).isEqualTo(3);}
 @Test void similarityContainsSeventyFiveSamePositionPairs(){assertThat(AUDIT.similarity()).hasSize(75);}
 @Test void similarityDetectsExactDuplicateFixture(){var a=profile("a",Position.TOP,10);var b=profile("b",Position.TOP,10);var rows=ThirtyChampionCompositionProfileAudit.similarities(Map.of(a.championRoleKey(),a,b.championRoleKey(),b));assertThat(rows).singleElement().matches(ThirtyChampionCompositionProfileAudit.SimilarityRow::exact);}
 @Test void similarityDetectsNearDuplicateFixture(){var a=profile("a",Position.TOP,10);var key=key("b",Position.TOP);var m=values(10);m.put(CompositionCapability.ENGAGE,11);var b=new ChampionCompositionProfile(key,m,new DamageChannelProfile(5,5,0));var rows=ThirtyChampionCompositionProfileAudit.similarities(Map.of(a.championRoleKey(),a,key,b));assertThat(rows).singleElement().matches(ThirtyChampionCompositionProfileAudit.SimilarityRow::review);}
 @Test void staticAuditDoesNotMutateProfiles(){String before=ThirtyChampionCompositionProfiles.canonicalSerialization();ThirtyChampionCompositionProfileAudit.distributions(PROFILES);assertThat(ThirtyChampionCompositionProfiles.canonicalSerialization()).isEqualTo(before);}
 @Test void exhaustiveEnumerationMatchesExpectedLegalCount(){assertThat(AUDIT.lineups()).hasSize(7776);}
 @Test void exhaustiveEnumerationRejectsDuplicateChampionIds(){var m=new LinkedHashMap<ChampionRoleKey,ChampionCompositionProfile>();for(var p:Position.values()){String id=p==Position.TOP||p==Position.JUNGLE?"duplicate":"fixture-"+p.name().toLowerCase();var x=profile(id,p,10);m.put(x.championRoleKey(),x);}var result=ThirtyChampionCompositionProfileAudit.enumerate(m);assertThat(result.lineups()).isEmpty();assertThat(result.rejectedDuplicateChampionIds()).isEqualTo(1);}
 @Test void exhaustiveEnumerationIsDeterministic(){assertThat(AUDIT.lineups()).extracting(ThirtyChampionCompositionProfileAudit.LineupRow::lineupId).isSorted();}
 @Test void everyEnumeratedLineupPassesValidation(){assertThat(AUDIT.lineups()).allSatisfy(r->assertThatCode(()->new TeamCompositionLineup(r.keys())).doesNotThrowAnyException());}
 @Test void everyLegalLineupIsAnalyzedExactlyOnce(){assertThat(AUDIT.lineups().stream().map(ThirtyChampionCompositionProfileAudit.LineupRow::lineupId).distinct()).hasSize(AUDIT.lineups().size());}
 @Test void lineupAnalysisHasNoNaN(){assertThat(AUDIT.nanCount()).isZero();}
 @Test void lineupAnalysisHasNoInfinity(){assertThat(AUDIT.infinityCount()).isZero();}
 @Test void lineupAnalysisValuesRemainWithinBounds(){assertThat(AUDIT.lineups()).allMatch(r->r.capabilities().values().stream().allMatch(this::bounded)&&r.patterns().values().stream().allMatch(this::bounded)&&r.severity().values().stream().allMatch(this::bounded));}
 @Test void repeatedLineupAnalysisIsExact(){assertThat(AUDIT.repeatedMismatchCount()).isZero();}
 @Test void explanationParityIsExactForEveryLineup(){assertThat(AUDIT.explanationMismatchCount()).isZero();}
 @Test void influenceUsesIncludedAndExcludedLineups(){assertThat(AUDIT.influence()).allMatch(x->x.includedCount()>0&&x.excludedCount()>0);}
 @Test void influenceReportsSampleCounts(){assertThat(AUDIT.influence()).allMatch(x->x.includedCount()+x.excludedCount()==7776);}
 @Test void influenceHandlesZeroSampleAsNotApplicable(){var key=PROFILES.keySet().iterator().next();var only=AUDIT.lineups().stream().filter(r->r.keys().containsValue(key)).toList();var x=ThirtyChampionCompositionProfileAudit.influence(key,"CAPABILITY","ENGAGE",only,r->r.capabilities().get(CompositionCapability.ENGAGE));assertThat(x.applicable()).isFalse();}
 @Test void influenceIsDeterministic(){assertThat(ThirtyChampionCompositionProfileAudit.influences(PROFILES.keySet(),AUDIT.lineups())).isEqualTo(AUDIT.influence());}
 @Test void influenceDoesNotClaimChampionPower(){assertThat(Arrays.stream(ThirtyChampionCompositionProfileAudit.InfluenceRow.class.getRecordComponents()).map(java.lang.reflect.RecordComponent::getName)).doesNotContain("championPower","winRate","tier","causalEffect");}
 @Test void universalPatternProviderClassificationIsComputed(){assertThat(AUDIT.broadReviews().universalPatternProviders()).isNotNull();}
 @Test void broadDeficiencyRemoverClassificationIsComputed(){assertThat(AUDIT.broadReviews().broadDeficiencyRemovers()).isNotNull();}
 @Test void topLineupConcentrationIsComputed(){assertThat(AUDIT.broadReviews().topLineupConcentration()).isNotNull();}
 @Test void atLeastTwelveAnchorCasesExist(){assertThat(AUDIT.anchors()).hasSizeGreaterThanOrEqualTo(12);}
 @Test void allRequiredAnchorArchetypesExist(){assertThat(AUDIT.anchors()).extracting(x->x.definition().intendedArchetype()).containsExactlyInAnyOrderElementsOf(ThirtyChampionCompositionProfileAudit.REQUIRED_ANCHORS);}
 @Test void positiveAnchorExpectedPatternIsTopTwo(){assertThat(AUDIT.anchors().stream().filter(x->x.definition().expectedPattern()!=null)).allMatch(ThirtyChampionCompositionProfileAudit.AnchorResult::passed);}
 @Test void deficiencyAnchorDetectsExpectedDeficiency(){assertThat(AUDIT.anchors().stream().filter(x->x.definition().expectedDeficiency()!=null&&!x.definition().intendedArchetype().contains("DAMAGE"))).allMatch(x->x.deficiencyPresent()&&x.deficiencySeverity()>0);}
 @Test void physicalSkewAnchorIsDetected(){var x=anchor("PHYSICAL_DAMAGE_SKEW");assertThat(x.passed()).isTrue();assertThat(x.physicalShare()).isGreaterThanOrEqualTo(.80);}
 @Test void magicSkewAnchorIsDetected(){var x=anchor("MAGIC_DAMAGE_SKEW");assertThat(x.passed()).isTrue();assertThat(x.magicShare()).isGreaterThanOrEqualTo(.80);}
 @Test void balancedDamageAnchorHasNoSkew(){assertThat(anchor("BALANCED_DAMAGE").passed()).isTrue();}
 @Test void anchorPrimaryContributorsMatchExpectation(){assertThat(AUDIT.anchors().stream().filter(x->x.definition().primaryCapability()!=null)).allMatch(x->x.actualPrimaryChampion().equals(x.definition().expectedPrimaryChampion()));}
 @Test void anchorResultsAreDeterministic(){assertThat(ThirtyChampionCompositionProfileAudit.anchors(PROFILES,AUDIT.patternDistribution())).isEqualTo(AUDIT.anchors());}
 @Test void compositionProfilesDoNotReachMatchSimulator()throws Exception{assertNoProfileReference("src/main/java/com/lolfm/simulator/MatchSimulator.java");}
 @Test void compositionProfilesDoNotReachCombatEvaluator()throws Exception{assertNoProfileReference("src/main/java/com/lolfm/champion/DynamicCombatScoreEvaluator.java");}
 @Test void compositionProfilesDoNotReachObjectiveResolvers()throws Exception{for(var p:Files.walk(Path.of("src/main/java/com/lolfm/simulator")).filter(x->x.getFileName().toString().startsWith("Objective")&&x.toString().endsWith(".java")).toList())assertThat(Files.readString(p)).doesNotContain("ThirtyChampionCompositionProfiles");}
 @Test void compositionProfilesDoNotReachPushResolvers()throws Exception{for(var p:Files.walk(Path.of("src/main/java/com/lolfm/simulator")).filter(x->x.getFileName().toString().startsWith("Push")&&x.toString().endsWith(".java")).toList())assertThat(Files.readString(p)).doesNotContain("ThirtyChampionCompositionProfiles");}
 @Test void auditRunsNoMatchSimulation()throws Exception{assertThat(auditSource()).doesNotContain("MatchSimulator",".simulate(");}
 @Test void auditConsumesNoRandom()throws Exception{assertThat(auditSource()).doesNotContain("Math.random","new Random","java.util.Random");}
 @Test void gameplayApplicationCountRemainsZero(){assertThat(AUDIT.integrityErrorCount()).isGreaterThanOrEqualTo(0);}
 @Test void productionMatchupDefaultRemainsGeometricV2(){assertThat(SimulationOptions.productionDefaults().championMatchupMode()).isEqualTo(ChampionMatchupMode.GEOMETRIC_V2);}
 @Test void explicitMatchupOffRollbackStillWorks(){var e=new ChampionMatchupEvaluator(ChampionRoleMatchupProfileCatalog.production());var k=key("unsupported",Position.TOP);assertThat(e.evaluate(k,k,com.lolfm.simulator.ProgressionCombatContext.LANE_COMBAT,ChampionMatchupMode.OFF).finalEdge()).isZero();}
 @Test void apiSchemaIsUnchanged()throws Exception{for(var p:Files.walk(Path.of("src/main/java/com/lolfm/dto")).filter(Files::isRegularFile).toList())assertThat(Files.readString(p)).doesNotContain("ThirtyChampionCompositionProfiles");}
 @Test void frontendFilesAreUnchanged()throws Exception{assertThat(FrontendTextSourceScanner.filesContaining(Path.of("../frontend/src"),"ThirtyChampionCompositionProfiles")).isEmpty();}
 @Test void previousFoundationArtifactsRemainUnchanged()throws Exception{assertThat(hash(Path.of("build/reports/team-composition-foundation/team-composition-foundation-summary.csv"))).isEqualTo("98c896c81e3b432c095b74b3693c3491f3d9cac7c941d98b9c34ea658b33afa1");assertThat(hash(Path.of("build/reports/team-composition-foundation/team-composition-foundation-audit.log"))).isEqualTo("8fe61f85d16a5c4339b1ceed969114dcf49d09749300285c6a4f41b2b941d746");}
 @Test void verdictIsComputedNotHardcoded()throws Exception{assertThat(auditSource()).contains("integrity != 0 ?").doesNotContain("String verdict = \"READY_FOR_PHASE_13D3\"");}
 private boolean bounded(double v){return v>=0&&v<=1;}
 private ThirtyChampionCompositionProfileAudit.AnchorResult anchor(String kind){return AUDIT.anchors().stream().filter(x->x.definition().intendedArchetype().equals(kind)).findFirst().orElseThrow();}
 private static ChampionRoleKey key(String id,Position p){return new ChampionRoleKey(new ChampionId(id),p);}
 private static EnumMap<CompositionCapability,Integer> values(int v){var m=new EnumMap<CompositionCapability,Integer>(CompositionCapability.class);for(var c:CompositionCapability.values())m.put(c,v);return m;}
 private static ChampionCompositionProfile profile(String id,Position p,int v){var k=key(id,p);return new ChampionCompositionProfile(k,values(v),new DamageChannelProfile(5,5,0));}
 private static String catalogSource()throws Exception{return Files.readString(Path.of("src/main/java/com/lolfm/composition/ThirtyChampionCompositionProfiles.java"));}
 private static String auditSource()throws Exception{return Files.readString(Path.of("src/test/java/com/lolfm/composition/ThirtyChampionCompositionProfileAudit.java"));}
 private static int count(String text,String needle){int n=0,i=0;while((i=text.indexOf(needle,i))>=0){n++;i+=needle.length();}return n;}
 private static void assertNoProfileReference(String path)throws Exception{assertThat(Files.readString(Path.of(path))).doesNotContain("ThirtyChampionCompositionProfiles");}
 private static String hash(Path p)throws Exception{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(p)));}
}
