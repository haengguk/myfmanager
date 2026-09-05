package com.lolfm.composition;

import com.lolfm.simulator.FightGrade;
import com.lolfm.simulator.TeamSide;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@Tag("diagnostic")
@Tag("historical-artifact")
class CompositionKeySpecificChannelCalibrationTest {
    static List<CompositionKeySpecificChannelCalibration.ScheduleRow> schedule;
    static CompositionKeySpecificChannelCalibration.Split split;
    static Map<Integer, CompositionKeySpecificChannelCalibration.SignalSet> signals;
    static List<CompositionKeySpecificChannelCalibration.WinnerRow> winners;
    static List<CompositionKeySpecificChannelCalibration.GradeRow> grades;
    static Map<TeamCompositionContext, CompositionKeySpecificChannelCalibration.Bands> winnerBands;
    static Map<TeamCompositionContext, CompositionKeySpecificChannelCalibration.Bands> severityBands;
    static Map<TeamCompositionContext, CompositionKeySpecificChannelCalibration.Transform> severityTransforms;

    @BeforeAll static void setup() throws Exception {
        CompositionKeySpecificChannelCalibration.verifySources();
        schedule = CompositionKeySpecificChannelCalibration.readSchedule();
        split = CompositionKeySpecificChannelCalibration.split(schedule);
        signals = CompositionKeySpecificChannelCalibration.buildSignals(schedule);
        winners = CompositionKeySpecificChannelCalibration.readWinners(split);
        grades = CompositionKeySpecificChannelCalibration.readGrades(split, signals);
        winnerBands = CompositionKeySpecificChannelCalibration.bands(winners, CompositionKeySpecificChannelCalibration.MarginRow::baselineMargin);
        severityBands = CompositionKeySpecificChannelCalibration.bands(grades, CompositionKeySpecificChannelCalibration.MarginRow::baselineMargin);
        severityTransforms = CompositionKeySpecificChannelCalibration.severityTransforms();
    }

    @Test void sourceRuntimeHashesAreExact() throws Exception {
        assertEquals(CompositionKeySpecificChannelCalibration.SOURCE_SUMMARY_HASH, CompositionKeySpecificChannelCalibration.sha256(CompositionKeySpecificChannelCalibration.SUMMARY));
        assertEquals(CompositionKeySpecificChannelCalibration.SOURCE_AUDIT_HASH, CompositionKeySpecificChannelCalibration.sha256(CompositionKeySpecificChannelCalibration.AUDIT));
    }
    @Test void blueprintIdentityRemainsExact() { assertDoesNotThrow(() -> FrozenCompositionApplicationSemanticsBlueprint.verifyIdentity(CompositionKeySpecificChannelCalibration.BLUEPRINT_VERSION, CompositionKeySpecificChannelCalibration.BLUEPRINT_HASH)); }
    @Test void sourceArtifactsRemainReadOnly() throws Exception { var before=CompositionKeySpecificChannelCalibration.hashes(CompositionKeySpecificChannelCalibration.sourcePaths()); CompositionKeySpecificChannelCalibration.readSchedule(); assertEquals(before,CompositionKeySpecificChannelCalibration.hashes(CompositionKeySpecificChannelCalibration.sourcePaths())); }
    @Test void calibrationRunsNoGameplaySimulation() throws Exception { assertFalse(source().contains("new MatchSimulator")); assertFalse(source().contains("simulate(")); }
    @Test void calibrationConsumesNoRandom() throws Exception { assertFalse(source().contains("Random.next")); assertFalse(source().contains("new Random")); }
    @Test void orientationGroupsNeverCrossSplit() { assertTrue(split.calibrationGroups().stream().noneMatch(split.validationGroups()::contains)); assertEquals(300,split.calibrationGroups().size()); assertEquals(200,split.validationGroups().size()); }
    @Test void attemptsNeverCrossSplit() { Map<String,CompositionKeySpecificChannelCalibration.SplitRole> seen=new HashMap<>(); winners.forEach(x->assertNull(seen.putIfAbsent(x.caseIndex()+"|W|"+x.attemptId(),x.split()))); grades.forEach(x->assertNull(seen.putIfAbsent(x.caseIndex()+"|G|"+x.attemptId(),x.split()))); }
    @Test void validationIsNotUsedForCandidateSelection() throws Exception { String s=source(); assertTrue(s.contains("filter(x -> x.context() == key && x.split() == SplitRole.CALIBRATION)")); assertTrue(s.contains("SELECTED_ONE_SHOT_VALIDATION")); }

    @Test void winnerCounterfactualUsesCapturedRandom() { var row=winner(TeamCompositionContext.TEAMFIGHT); var metric=CompositionKeySpecificChannelCalibration.winnerMetric(row.context(),"TEST",.05,1,List.of(row),winnerBands.get(row.context())); assertEquals(row.sample()<new com.lolfm.simulator.CombatOutcomeProbabilityEvaluator().uniformAdvantageProbability(row.baselineGap()+row.signal()),metric.flipCount()==0?row.baselineWinner()==TeamSide.BLUE:row.baselineWinner()!=TeamSide.BLUE); }
    @Test void winnerDecisionBandsAreFrozenFromCalibration() { assertEquals(3,winnerBands.size()); assertTrue(winnerBands.values().stream().allMatch(x->x.p25()<=x.p75())); }
    @Test void validationDoesNotRecalculateWinnerBands() { var key=TeamCompositionContext.TEAMFIGHT; var validation=winners.stream().filter(x->x.context()==key&&x.split()==CompositionKeySpecificChannelCalibration.SplitRole.VALIDATION).toList(); var metric=CompositionKeySpecificChannelCalibration.winnerMetric(key,"VALIDATION",.05,1,validation,winnerBands.get(key)); assertSame(winnerBands.get(key),metric.bands()); }
    @Test void winnerGridComesFromFrozenPolicy() { assertEquals(CompositionEligibleContextGainScreening.TARGET_RATIOS.stream().map(java.math.BigDecimal::doubleValue).toList(),CompositionKeySpecificChannelCalibration.TARGET_RATIOS); }
    @Test void teamfightHistoricalGainIsReferenceNotAutomaticSelection() { assertHistorical(TeamCompositionContext.TEAMFIGHT,FrozenCompositionGameplayGainPolicy.TEAMFIGHT_GAIN); }
    @Test void siegeHistoricalGainIsReferenceNotAutomaticSelection() { assertHistorical(TeamCompositionContext.SIEGE,FrozenCompositionGameplayGainPolicy.SIEGE_GAIN); }
    @Test void baseHistoricalGainIsNeverAppliedToRoleAwareEdge() throws Exception { assertFalse(source().contains("BASE_DEFENSE_GAIN *")); assertEquals(10.837956658606,CompositionKeySpecificChannelCalibration.historicalGain(TeamCompositionContext.BASE_DEFENSE),1e-12); }
    @Test void winnerDirectionMismatchFailsCandidate() { var reversed=new CompositionKeySpecificChannelCalibration.WinnerRow(1,1,TeamCompositionContext.TEAMFIGHT,CompositionKeySpecificChannelCalibration.SplitRole.CALIBRATION,0,-1,.5,.5,TeamSide.RED,TeamSide.RED); var metric=CompositionKeySpecificChannelCalibration.winnerMetric(reversed.context(),"BAD",.05,-10,List.of(reversed),new CompositionKeySpecificChannelCalibration.Bands(1,1,1,1,1)); assertFalse(metric.safe()); assertTrue(metric.directionMismatchCount()>0); }
    @Test void farWinnerFlipFailsCandidate() { var row=new CompositionKeySpecificChannelCalibration.WinnerRow(1,1,TeamCompositionContext.TEAMFIGHT,CompositionKeySpecificChannelCalibration.SplitRole.CALIBRATION,0,1,.5,.9,TeamSide.RED,TeamSide.RED); var metric=CompositionKeySpecificChannelCalibration.winnerMetric(row.context(),"BAD",.05,30,List.of(row),new CompositionKeySpecificChannelCalibration.Bands(.01,.02,.03,.04,.05)); assertTrue(metric.farFlipCount()>0); assertFalse(metric.safe()); }
    @Test void nonNearFlipRateUsesExactDenominator() { var key=TeamCompositionContext.TEAMFIGHT; var rows=winners.stream().filter(x->x.context()==key&&x.split()==CompositionKeySpecificChannelCalibration.SplitRole.CALIBRATION).toList(); var m=CompositionKeySpecificChannelCalibration.winnerMetric(key,"TEST",.05,5,rows,winnerBands.get(key)); assertEquals((double)(m.midFlipCount()+m.farFlipCount())/(m.midCount()+m.farCount()),m.nonNearFlipRate(),1e-15); }

    @Test void baseTransformUsesStructuredAttackerDefenderIdentity() { assertTrue(CompositionKeySpecificChannelCalibration.baseTransform().canonical().contains("attackerOriented")); }
    @Test void baseTransformUsesNoFreeRuleWeights() { assertEquals(0,CompositionKeySpecificChannelCalibration.baseTransform().freeParameterCount()); }
    @Test void baseTransformSideSwapIsExactSignReverse() { var group=schedule.stream().filter(x->x.orientationGroupId()==schedule.getFirst().orientationGroupId()).toList(); double a=signals.get(group.get(0).caseIndex()).winner().get(TeamCompositionContext.BASE_DEFENSE); double b=signals.get(group.get(1).caseIndex()).winner().get(TeamCompositionContext.BASE_DEFENSE); assertEquals(a,-b,0.0); }
    @Test void baseTransformDoesNotReadWinnerOutcome() { assertFalse(CompositionKeySpecificChannelCalibration.baseTransform().outcomeDependency()); }
    @Test void baseTransformSelectionUsesStructuralPriorityNotOutcome() { assertEquals(CompositionKeySpecificChannelCalibration.BASE_TRANSFORM_ID,CompositionKeySpecificChannelCalibration.baseTransform().id()); assertTrue(CompositionKeySpecificChannelCalibration.baseTransform().canonical().contains("PRODUCT_EXPOSURE")); }
    @Test void unresolvedBaseTransformBlocksCandidateFreeze() { var x=new CompositionKeySpecificChannelCalibration.Transform(TeamCompositionContext.BASE_DEFENSE,"X","H",List.of(),0,true,false,false,false,"X"); assertFalse(x.eligible()); }

    @Test void severityUsesOnlyMappedSeverityEligibleRules() { for(var x:severityTransforms.values())assertEquals(3,x.ruleIds().size()); }
    @Test void winnerOnlyRulesCannotEnterSeverity() { assertTrue(severityTransforms.values().stream().flatMap(x->x.ruleIds().stream()).noneMatch(x->x.startsWith("SKIRMISH"))); }
    @Test void notApplicableRulesCannotEnterSeverity() { assertTrue(severityTransforms.values().stream().flatMap(x->x.ruleIds().stream()).noneMatch(x->x.startsWith("OBJECTIVE")||x.startsWith("SIDE"))); }
    @Test void severityTransformHasNoFreePerRuleWeights() { assertTrue(severityTransforms.values().stream().allMatch(x->x.freeParameterCount()==0)); }
    @Test void severityCannotEqualWinnerSignalExactly() { assertSignalsSeparated(false); }
    @Test void severityCannotBeAffineEquivalentToWinnerSignal() { for(var key:CompositionKeySpecificChannelCalibration.KEYS){List<CompositionKeySpecificChannelCalibration.Pair>p=schedule.stream().map(x->new CompositionKeySpecificChannelCalibration.Pair(signals.get(x.caseIndex()).severity().get(key),signals.get(x.caseIndex()).winner().get(key))).toList();assertFalse(CompositionKeySpecificChannelCalibration.affineEquivalent(p));} }
    @Test void severityTransformSelectionDoesNotReadFightGradeOutcome() { assertTrue(severityTransforms.values().stream().allMatch(x->!x.outcomeDependency())); }

    @Test void fullCoverageRowsReconstructCandidateExactly() { var row=fullRow(); var e=CompositionKeySpecificChannelCalibration.severityEval(row,0,severityBands.get(row.context())); assertTrue(e.exact()); assertEquals(row.actual(),e.candidate()); }
    @Test void partialAceRowMissingBigRandomCanBecomeUnresolved() { var e=CompositionKeySpecificChannelCalibration.severityEval(partialAce(),-1,bandsNear()); assertEquals(CompositionKeySpecificChannelCalibration.EvalStatus.UNRESOLVED_MISSING_LATER_RANDOM,e.status()); }
    @Test void partialBigRowMissingNormalRandomCanBecomeUnresolved() { var e=CompositionKeySpecificChannelCalibration.severityEval(partialBig(),-1,bandsNear()); assertEquals(CompositionKeySpecificChannelCalibration.EvalStatus.UNRESOLVED_MISSING_LATER_RANDOM,e.status()); }
    @Test void partialRowThatKeepsEarlyBranchRemainsExact() { var e=CompositionKeySpecificChannelCalibration.severityEval(partialAce(),1,bandsNear()); assertTrue(e.exact()); assertEquals(FightGrade.ACE,e.candidate()); }
    @Test void earlierPreviouslyFailedBranchCanBecomeExactSuccessUsingCapturedSample() { var row=partialBig(); var e=CompositionKeySpecificChannelCalibration.severityEval(row,1,bandsNear()); assertTrue(e.exact()); assertEquals(FightGrade.ACE,e.candidate()); }
    @Test void missingLaterRandomIsNeverFabricated() { var e=CompositionKeySpecificChannelCalibration.severityEval(partialAce(),-1,bandsNear()); assertNull(e.candidate()); }
    @Test void unresolvedIsNeverCountedAsUnchanged() { var m=CompositionKeySpecificChannelCalibration.severityMetric(TeamCompositionContext.TEAMFIGHT,.05,-1,List.of(partialAce()),bandsNear()); assertEquals(1,m.unresolvedCount()); assertEquals(1.0,m.changeUpper()); assertEquals(0.0,m.changeLower()); }
    @Test void gradeChangeBoundsIncludeUnresolvedCorrectly() { var m=CompositionKeySpecificChannelCalibration.severityMetric(TeamCompositionContext.TEAMFIGHT,.05,-1,List.of(partialAce(),fullRow()),bandsNear()); assertEquals((double)(m.knownChangedCount()+m.unresolvedCount())/m.totalCount(),m.changeUpper(),1e-15); }
    @Test void transitionMatrixExcludesUnknownRows() { var m=CompositionKeySpecificChannelCalibration.severityMetric(TeamCompositionContext.TEAMFIGHT,.05,-1,List.of(partialAce(),fullRow()),bandsNear()); long total=0;for(long[]r:m.matrix())for(long x:r)total+=x;assertEquals(m.exactCount(),total); }

    @Test void severityDirectionMismatchFailsCandidate() { var row=new CompositionKeySpecificChannelCalibration.GradeRow(1,1,TeamCompositionContext.TEAMFIGHT,CompositionKeySpecificChannelCalibration.SplitRole.CALIBRATION,TeamSide.BLUE,10,1,FightGrade.NORMAL_WIN,List.of(branch("ACE",.05,.2,.1),branch("BIG",.2,.3,.42),branch("NORMAL",.5,.4,.78)),"FULL",.01); var m=CompositionKeySpecificChannelCalibration.severityMetric(row.context(),.05,-100,List.of(row),bandsNear()); assertTrue(m.directionMismatchCount()>0); assertFalse(m.safe()); }
    @Test void farExactGradeChangeFailsCandidate() { var row=fullRow(.1); var m=CompositionKeySpecificChannelCalibration.severityMetric(row.context(),.05,20,List.of(row),new CompositionKeySpecificChannelCalibration.Bands(.01,.02,.03,.04,.05)); assertTrue(m.farExactChangeCount()>0); assertFalse(m.safe()); }
    @Test void nonNearGradeChangeUpperBoundUsesWorstCaseUnknown() { var row=partialAce(.001); var m=CompositionKeySpecificChannelCalibration.severityMetric(row.context(),.05,-1,List.of(row),new CompositionKeySpecificChannelCalibration.Bands(.00001,.00002,.00003,.00004,.00005)); assertEquals(1.0,m.nonNearUpper()); }
    @Test void twoGradeJumpUpperBoundMustBeZero() { var m=CompositionKeySpecificChannelCalibration.severityMetric(TeamCompositionContext.TEAMFIGHT,.05,-1,List.of(partialAce()),bandsNear()); assertTrue(m.jump2Upper()>0); assertFalse(m.safe()); }
    @Test void validationUsesCalibrationSeverityBands() { var key=TeamCompositionContext.TEAMFIGHT;var v=grades.stream().filter(x->x.context()==key&&x.split()==CompositionKeySpecificChannelCalibration.SplitRole.VALIDATION).toList();var m=CompositionKeySpecificChannelCalibration.severityMetric(key,0,0,v,severityBands.get(key));assertSame(severityBands.get(key),m.bands()); }
    @Test void validationFailureCannotTriggerRetuning() throws Exception { assertTrue(source().contains("validationAdaptiveRetuningCount\", \"0")); assertFalse(source().contains("while (!validation")); }

    @Test void candidateCanonicalSerializationIsDeterministic() throws Exception { var a=canonical();var b=canonical();assertEquals(a,b); }
    @Test void candidateHashIsDeterministic() throws Exception { assertEquals(CompositionKeySpecificChannelCalibration.sha256(canonical()),CompositionKeySpecificChannelCalibration.sha256(canonical())); }
    @Test void candidateHashContainsConfigurationNotOutcomes() throws Exception { String c=canonical();assertFalse(c.contains("winnerResult"));assertFalse(c.contains("duration"));assertFalse(c.contains("objective"));assertFalse(c.contains("structure")); }
    @Test void candidateIsMarkedPostHoldoutDevelopmentOnly() throws Exception { assertTrue(canonical().contains("candidateRole=POST_HOLDOUT_DEVELOPMENT_CANDIDATE")); }
    @Test void candidateRequiresFreshHoldout() throws Exception { assertTrue(canonical().contains("freshHoldoutRequired=true")); }
    @Test void candidateCannotEnableProduction() throws Exception { assertTrue(canonical().contains("productionEligible=false")); }
    @Test void selectedCandidateIsNeverAppliedToRuntimeInThisPhase() throws Exception { assertFalse(source().contains("CompositionRuntimeState")); assertFalse(source().contains("selectedWinnerGainRuntimeApplication"+"Count()")); }
    @Test void skirmishFrozenConfigurationRemainsExact() throws Exception { String c=canonical();assertTrue(c.contains("SKIRMISH.winnerGain=24.509721397259"));assertTrue(c.contains("SKIRMISH.severity=NOT_APPLICABLE")); }

    private static String source() throws Exception { return Files.readString(Path.of("src/test/java/com/lolfm/composition/CompositionKeySpecificChannelCalibration.java")); }
    private static CompositionKeySpecificChannelCalibration.WinnerRow winner(TeamCompositionContext key){return winners.stream().filter(x->x.context()==key&&x.signal()!=0).findFirst().orElseThrow();}
    private static void assertHistorical(TeamCompositionContext key,double gain){var rows=winners.stream().filter(x->x.context()==key&&x.split()==CompositionKeySpecificChannelCalibration.SplitRole.CALIBRATION).toList();var m=CompositionKeySpecificChannelCalibration.winnerMetric(key,"HISTORICAL_REFERENCE",Double.NaN,gain,rows,winnerBands.get(key));assertEquals("HISTORICAL_REFERENCE",m.source());assertTrue(Double.isNaN(m.targetRatio()));}
    private static void assertSignalsSeparated(boolean expected){for(var key:CompositionKeySpecificChannelCalibration.KEYS){boolean equal=schedule.stream().allMatch(x->Double.compare(signals.get(x.caseIndex()).severity().get(key),signals.get(x.caseIndex()).winner().get(key))==0);assertEquals(expected,equal);}}
    private static CompositionKeySpecificChannelCalibration.Branch branch(String n,double t,double s,double cap){return new CompositionKeySpecificChannelCalibration.Branch(n,true,t,s,cap);}
    private static CompositionKeySpecificChannelCalibration.GradeRow partialAce(){return partialAce(.001);}
    private static CompositionKeySpecificChannelCalibration.GradeRow partialAce(double margin){return new CompositionKeySpecificChannelCalibration.GradeRow(1,1,TeamCompositionContext.TEAMFIGHT,CompositionKeySpecificChannelCalibration.SplitRole.CALIBRATION,TeamSide.BLUE,10,1,FightGrade.ACE,List.of(branch("ACE",.05,.05-margin,.1),new CompositionKeySpecificChannelCalibration.Branch("BIG",false,0,0,.42),new CompositionKeySpecificChannelCalibration.Branch("NORMAL",false,0,0,.78)),"PARTIAL_UNOBSERVED_LATER_RANDOM",margin);}
    private static CompositionKeySpecificChannelCalibration.GradeRow partialBig(){return new CompositionKeySpecificChannelCalibration.GradeRow(1,1,TeamCompositionContext.TEAMFIGHT,CompositionKeySpecificChannelCalibration.SplitRole.CALIBRATION,TeamSide.BLUE,10,1,FightGrade.BIG_WIN,List.of(branch("ACE",.05,.051,.1),branch("BIG",.2,.199,.42),new CompositionKeySpecificChannelCalibration.Branch("NORMAL",false,0,0,.78)),"PARTIAL_UNOBSERVED_LATER_RANDOM",.001);}
    private static CompositionKeySpecificChannelCalibration.GradeRow fullRow(){return fullRow(.001);}
    private static CompositionKeySpecificChannelCalibration.GradeRow fullRow(double margin){return new CompositionKeySpecificChannelCalibration.GradeRow(2,2,TeamCompositionContext.TEAMFIGHT,CompositionKeySpecificChannelCalibration.SplitRole.CALIBRATION,TeamSide.BLUE,10,1,FightGrade.SMALL_WIN,List.of(branch("ACE",.05,.05+margin,.1),branch("BIG",.2,.2+margin,.42),branch("NORMAL",.5,.5+margin,.78)),"FULL_FOR_ACTUAL_REACHED_BRANCHES",margin);}
    private static CompositionKeySpecificChannelCalibration.Bands bandsNear(){return new CompositionKeySpecificChannelCalibration.Bands(.01,.02,.03,.04,.05);}
    private static String canonical() throws Exception { Map<TeamCompositionContext,CompositionKeySpecificChannelCalibration.WinnerSelection>w=new EnumMap<>(TeamCompositionContext.class);Map<TeamCompositionContext,CompositionKeySpecificChannelCalibration.SeveritySelection>s=new EnumMap<>(TeamCompositionContext.class);for(var key:CompositionKeySpecificChannelCalibration.KEYS){var wr=winners.stream().filter(x->x.context()==key&&x.split()==CompositionKeySpecificChannelCalibration.SplitRole.CALIBRATION).toList();double gp=CompositionKeySpecificChannelCalibration.quantile(wr,x->Math.abs(x.baselineGap()),.9),ep=CompositionKeySpecificChannelCalibration.quantile(wr,x->Math.abs(x.signal()),.9);var wm=CompositionKeySpecificChannelCalibration.winnerMetric(key,"GRID",.05,.05*gp/ep,wr,winnerBands.get(key));w.put(key,new CompositionKeySpecificChannelCalibration.WinnerSelection(key,wm,"SELECTED_NONZERO"));var gr=grades.stream().filter(x->x.context()==key&&x.split()==CompositionKeySpecificChannelCalibration.SplitRole.CALIBRATION).toList();var sm=CompositionKeySpecificChannelCalibration.severityMetric(key,0,0,gr,severityBands.get(key));s.put(key,new CompositionKeySpecificChannelCalibration.SeveritySelection(key,sm,"ZERO_REFERENCE_SELECTED_BY_SCREENING"));}return CompositionKeySpecificChannelCalibration.candidateCanonical(w,s,CompositionKeySpecificChannelCalibration.baseTransform(),severityTransforms,split);}
}
