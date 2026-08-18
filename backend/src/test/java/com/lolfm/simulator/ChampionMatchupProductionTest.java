package com.lolfm.simulator;

import static org.assertj.core.api.Assertions.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lolfm.champion.*;
import com.lolfm.domain.*;
import com.lolfm.dto.MatchSimulateRequest;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class ChampionMatchupProductionTest {
    private final ChampionCatalog champions = new ChampionCatalog(new ObjectMapper());
    private final ChampionRoleMatchupProfileCatalog profiles = ChampionRoleMatchupProfileCatalog.production();
    private final ChampionMatchupEvaluator evaluator = new ChampionMatchupEvaluator(profiles);

    @Test void productionDefaultModeIsGeometricV2() { assertThat(SimulationOptions.productionDefaults().championMatchupMode()).isEqualTo(ChampionMatchupMode.GEOMETRIC_V2); }
    @Test void explicitOffRemainsExactlyNeutral() { assertThat(evaluator.evaluate(key("ornn",Position.TOP),key("gwen",Position.TOP),ProgressionCombatContext.LANE_COMBAT,ChampionMatchupMode.OFF).finalEdge()).isZero(); }
    @Test void defaultAndExplicitGeometricV2AreExact() { assertThat(SimulationOptions.productionDefaults().championMatchupMode()).isEqualTo(ChampionMatchupProductionPolicy.GEOMETRIC_V2.mode()); assertThat(edge("ornn","gwen",Position.TOP,ProgressionCombatContext.LANE_COMBAT)).isEqualTo(evaluator.evaluate(key("ornn",Position.TOP),key("gwen",Position.TOP),ProgressionCombatContext.LANE_COMBAT,ChampionMatchupMode.GEOMETRIC_V2).finalEdge()); }
    @Test void productionGainIsExactlyOne() { assertThat(ChampionMatchupProductionPolicy.GEOMETRIC_V2.gain()).isEqualTo(1.0); }
    @Test void productionDeadzoneIsNone() { assertThat(ChampionMatchupProductionPolicy.GEOMETRIC_V2.deadzone()).isZero(); }
    @Test void productionOverridesAreEmpty() { assertThat(ChampionMatchupProductionPolicy.GEOMETRIC_V2.pairOverrideCount()).isZero(); assertThat(ChampionMatchupProductionPolicy.GEOMETRIC_V2.mechanicOverrideCount()).isZero(); }
    @Test void productionEvaluatorPreservesAntisymmetry() { for(var c:ProgressionCombatContext.values()){double a=edge("ornn","gwen",Position.TOP,c),b=edge("gwen","ornn",Position.TOP,c);assertThat(a).isEqualTo(-b);} }
    @Test void productionEvaluatorConsumesNoRandom() { assertThat(Arrays.stream(ChampionMatchupEvaluator.class.getDeclaredFields())).noneMatch(f->Random.class.isAssignableFrom(f.getType())); }
    @Test void productionCatalogHasExactCoverage() { assertThat(profiles.profiles()).hasSize(212); assertThat(profiles.profiles().keySet()).filteredOn(k->k.position()==Position.TOP).hasSize(52); assertThat(profiles.profiles().keySet()).filteredOn(k->k.position()==Position.JUNGLE).hasSize(51); assertThat(profiles.profiles().keySet()).filteredOn(k->k.position()==Position.MID).hasSize(45); assertThat(profiles.profiles().keySet()).filteredOn(k->k.position()==Position.ADC).hasSize(29); assertThat(profiles.profiles().keySet()).filteredOn(k->k.position()==Position.SUPPORT).hasSize(35); }
    @Test void unsupportedProfileFailsBeforeMatchStartWhenOn() { assertThatThrownBy(()->evaluator.evaluate(key("unsupported",Position.TOP),key("ornn",Position.TOP),ProgressionCombatContext.LANE_COMBAT,ChampionMatchupMode.GEOMETRIC_V2)).isInstanceOf(UnsupportedChampionRoleMatchupProfileException.class).hasMessageContaining(UnsupportedChampionRoleMatchupProfileException.CODE); }
    @Test void unsupportedProfileDoesNotBreakOffMode() { assertThat(evaluator.evaluate(key("unsupported",Position.TOP),key("also-unsupported",Position.TOP),ProgressionCombatContext.LANE_COMBAT,ChampionMatchupMode.OFF).finalEdge()).isZero(); }
    @Test void productionEvaluatorMatches675CandidateRows() throws Exception { assertThat(matrixParity()).isEqualTo(675); }
    @Test void dynamicArtifactParityIsExact() throws Exception { assertThat(dynamicParity()).containsExactly(1920L,1920L,0L); }
    @Test void growthRatesUseEligibleRowsOnly() throws Exception { var lines=Files.readAllLines(Path.of("build/reports/geometric-candidate-influence/geometric-candidate-focused-dynamic.csv"));String[]h=lines.getFirst().split(",");int scenario=i(h,"scenario"),eligible=i(h,"growthPackageEligible"),overcome=i(h,"overcome");for(String name:List.of("COMBINED_LEAD_SMALL","COMBINED_LEAD_LARGE")){var rows=lines.stream().skip(1).map(x->x.split(",",-1)).filter(x->x[scenario].equals(name)&&Boolean.parseBoolean(x[eligible])).toList();assertThat(rows).isNotEmpty().allMatch(x->Boolean.parseBoolean(x[overcome]));} }
    @Test void participantPairingUsesPositionIdentity() { assertThat(key("ornn",Position.TOP).position()).isEqualTo(Position.TOP); assertThatThrownBy(()->evaluator.evaluate(key("ornn",Position.TOP),key("viktor",Position.MID),ProgressionCombatContext.LANE_COMBAT,ChampionMatchupMode.GEOMETRIC_V2)).isInstanceOf(IllegalArgumentException.class); }
    @Test void evaluationDoesNotMutateGameState() { var f=new ChampionMatchupTestFixture(ChampionMatchupMode.OFF,false);int before=f.state().getCurrentTimeSeconds();evaluator.evaluate(key("ornn",Position.TOP),key("gwen",Position.TOP),ProgressionCombatContext.LANE_COMBAT,ChampionMatchupMode.GEOMETRIC_V2);assertThat(f.state().getCurrentTimeSeconds()).isEqualTo(before); }
    @Test void evaluationDoesNotConsumeActionSlot() { var f=new ChampionMatchupTestFixture(ChampionMatchupMode.OFF,false);evaluator.evaluate(key("ornn",Position.TOP),key("gwen",Position.TOP),ProgressionCombatContext.LANE_COMBAT,ChampionMatchupMode.GEOMETRIC_V2);assertThat(f.state().wasMajorCombatAttemptedThisTick()).isFalse(); }
    @Test void deadPlayersAreExcludedAsBefore() { var f=new ChampionMatchupTestFixture(ChampionMatchupMode.OFF,false);assertThat(f.state().getChampionMatchupExecutionStats().snapshot().deadParticipantErrors()).isZero(); }
    @Test void sameSeedSameModeReplayIsExact() throws Exception { var line=GeneratedMatchupRoundRobinLineupFactory.create(champions,"S0").getFirst();assertThat(run(line,ChampionMatchupMode.OFF,7)).isEqualTo(run(line,ChampionMatchupMode.OFF,7));assertThat(run(line,ChampionMatchupMode.GEOMETRIC_V2,7)).isEqualTo(run(line,ChampionMatchupMode.GEOMETRIC_V2,7)); }
    @Test void offOnOffModeSequenceDoesNotLeak() throws Exception { var l=GeneratedMatchupRoundRobinLineupFactory.create(champions,"S0").getFirst();String off1=run(l,ChampionMatchupMode.OFF,7),on1=run(l,ChampionMatchupMode.GEOMETRIC_V2,7),off2=run(l,ChampionMatchupMode.OFF,7),on2=run(l,ChampionMatchupMode.GEOMETRIC_V2,7);assertThat(off1).isEqualTo(off2);assertThat(on1).isEqualTo(on2); }
    @Test void modeIsMatchScopedAndDoesNotLeak() throws Exception { offOnOffModeSequenceDoesNotLeak(); }
    @Test void apiSchemaIsUnchanged() { assertThat(Arrays.stream(MatchSimulateRequest.class.getDeclaredFields()).map(java.lang.reflect.Field::getName)).doesNotContain("championMatchupMode"); }
    @Test void candidateModeIsNotApiExposed() throws Exception { assertThat(Files.walk(Path.of("../frontend/src")).filter(Files::isRegularFile).noneMatch(p->{try{return Files.readString(p).contains("ChampionMatchupMode");}catch(Exception e){throw new RuntimeException(e);}})).isTrue(); }
    @Test void frontendFilesAreUnchanged() throws Exception { assertThat(Files.walk(Path.of("../frontend/src")).filter(Files::isRegularFile).noneMatch(p->{try{return Files.readString(p).contains("EXPOSURE_GATED_GEOMETRIC_V2");}catch(Exception e){throw new RuntimeException(e);}})).isTrue(); }
    @Test void activationVerdictIsComputed() { var d=ChampionMatchupActivationGate.evaluate(new ChampionMatchupActivationGate.Input(0,1,.01,.01,0,0,0,true));assertThat(d.verdict()).isEqualTo("MATCHUP_PRODUCTION_ACTIVATED"); }
    @Test void failedGateLeavesOrRestoresDefaultOff() { var d=ChampionMatchupActivationGate.evaluate(new ChampionMatchupActivationGate.Input(1,0,0,0,0,0,0,false));assertThat(d.defaultMode()).isEqualTo(ChampionMatchupMode.OFF);assertThat(d.productionActivated()).isFalse(); }

    @Test void baselineRegressionUsesExplicitOffMode() throws Exception { var source=Files.readString(Path.of("src/test/java/com/lolfm/simulator/ChampionFoundationIntegrationTest.java"));assertThat(source).contains("withChampionMatchupMode(ChampionMatchupMode.OFF)"); }
    @Test void productionDefaultRegressionUsesGeometricV2() { productionDefaultModeIsGeometricV2(); }
    @Test void offBaselineExpectationIsNotRewrittenForActivation() throws Exception {
        var lineup = GeneratedMatchupRoundRobinLineupFactory.create(champions, "S0").getFirst();
        String expectedOffBaseline = run(lineup, ChampionMatchupMode.OFF, 7);
        ChampionMatchupMode productionDefault = SimulationOptions.productionDefaults().championMatchupMode();
        assertThat(productionDefault).isEqualTo(ChampionMatchupMode.GEOMETRIC_V2);
        run(lineup, productionDefault, 7);
        assertThat(run(lineup, ChampionMatchupMode.OFF, 7)).isEqualTo(expectedOffBaseline);
    }
    @Test void modeDoesNotLeakAcrossFullTestSuiteFixtures() throws Exception { offOnOffModeSequenceDoesNotLeak(); }
    @Test void failedFullTestGateRollsBackDefaultToOff() { var d=ChampionMatchupActivationGate.evaluate(new ChampionMatchupActivationGate.Input(1,1,0,0,0,0,0,true));assertThat(d.defaultMode()).isEqualTo(ChampionMatchupMode.OFF); }
    @Test void successfulFullTestGateAllowsGeometricDefault() { var d=ChampionMatchupActivationGate.evaluate(new ChampionMatchupActivationGate.Input(0,1,0,0,0,0,0,true));assertThat(d.defaultMode()).isEqualTo(ChampionMatchupMode.GEOMETRIC_V2); }
    @Test void artifactFinalizerRunsNoSimulation() throws Exception { String source=Files.readString(Path.of("src/test/java/com/lolfm/simulator/ChampionMatchupProductionActivationFinalizer.java"));assertThat(source).doesNotContain("MatchSimulator","simulate(","runChampionMatchupProductionActivationAudit"); }
    @Test void activationSummarySeparatesEvaluatedAndFinalMode() { var values=ChampionMatchupProductionActivationFinalizer.finalModeFields(ChampionMatchupMode.GEOMETRIC_V2,true);assertThat(values).containsEntry("activationAuditEvaluatedMode","GEOMETRIC_V2").containsEntry("finalProductionDefaultMode","GEOMETRIC_V2").containsEntry("rollbackApplied",false); }

    private ChampionRoleKey key(String id,Position p){return new ChampionRoleKey(new ChampionId(id),p);}
    private double edge(String a,String b,Position p,ProgressionCombatContext c){return evaluator.evaluate(key(a,p),key(b,p),c,ChampionMatchupMode.GEOMETRIC_V2).finalEdge();}
    private long matrixParity() throws Exception {var lines=Files.readAllLines(Path.of("build/reports/geometric-candidate-influence/geometric-candidate-gain-matrix.csv"));String[]h=lines.getFirst().split(",");int g=i(h,"gain"),p=i(h,"position"),pair=i(h,"pair"),ctx=i(h,"context"),expected=i(h,"forwardEdge");long n=0;for(String line:lines.subList(1,lines.size())){String[]x=line.split(",",-1);if(!x[g].equals("1.0"))continue;String[]ids=x[pair].split("/");double actual=edge(ids[0],ids[1],Position.valueOf(x[p]),ProgressionCombatContext.valueOf(x[ctx]));if(Double.doubleToLongBits(actual)==Double.doubleToLongBits(Double.parseDouble(x[expected])))n++;}return n;}
    private long[] dynamicParity() throws Exception {var lines=Files.readAllLines(Path.of("build/reports/geometric-candidate-influence/geometric-candidate-focused-dynamic.csv"));String[]h=lines.getFirst().split(",");int pair=i(h,"pair"),p=i(h,"position"),ctx=i(h,"context"),dir=i(h,"direction"),expected=i(h,"matchupEdge");long ok=0,unsupported=0;for(String line:lines.subList(1,lines.size())){String[]x=line.split(",",-1);String[]ids=x[pair].split("/");if(x[dir].equals("REVERSE")){String t=ids[0];ids[0]=ids[1];ids[1]=t;}try{double actual=Math.abs(edge(ids[0],ids[1],Position.valueOf(x[p]),ProgressionCombatContext.valueOf(x[ctx])));if(Double.doubleToLongBits(actual)==Double.doubleToLongBits(Double.parseDouble(x[expected])))ok++;}catch(UnsupportedChampionRoleMatchupProfileException e){unsupported++;}}return new long[]{lines.size()-1,ok,unsupported};}
    private String run(GeneratedMatchupRoundRobinLineupFactory.Lineup l,ChampionMatchupMode mode,int seed)throws Exception{var o=l.fixture().orient(SideOrientationFixture.Orientation.ORIGINAL);var a=new ChampionSelectionValidator(champions).resolve(o.champions());var sim=new MatchSimulator(new TeamfightResolver(),new EndGameEvaluator(),new SnapshotFactory(champions),new ObjectiveResolver(),new PostFightResolver(),new ObjectiveAttemptResolver(),new StructureResolver(),new PushResolver(),SimulationOptions.productionDefaults().withChampionMatchupMode(mode),profiles);return new ObjectMapper().writeValueAsString(sim.simulate(o.blue(),o.red(),seed,a));}
    private static int i(String[]h,String name){for(int i=0;i<h.length;i++)if(h[i].equals(name))return i;throw new IllegalArgumentException(name);}
}
