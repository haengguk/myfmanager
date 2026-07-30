package com.lolfm.simulator;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lolfm.champion.*;
import com.lolfm.domain.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.stream.Collectors;

/** Phase 13C-4.2 formula-shape and production-probability-delta audit. */
public final class ChampionPairInteractionShapeAudit {
 static final Path OUTPUT=Path.of("build/reports/champion-pair-interaction-shape");
 static final ObjectMapper MAPPER=new ObjectMapper();
 static final String FROZEN_HASH="c8956937e8c9032654feb2bb17ff7ef66d68a964b4f1f6ed98853400f5b3dc64";
 private ChampionPairInteractionShapeAudit(){}
 public static void main(String[]args)throws Exception{
  Path summaryPath=OUTPUT.resolve("champion-pair-interaction-shape-summary.csv");
  if(Files.exists(summaryPath)){System.out.println("Phase 13C-4.2 artifact already exists; duplicate execution skipped: "+OUTPUT.toAbsolutePath());return;}
  Files.createDirectories(OUTPUT);ChampionCatalog champions=new ChampionCatalog(MAPPER);
  EnumMap<InteractionShapeFormula.Type,InteractionShapeGeneratedCatalog.BuildResult> builds=new EnumMap<>(InteractionShapeFormula.Type.class);
  for(var type:InteractionShapeFormula.Type.values())builds.put(type,InteractionShapeGeneratedCatalog.build(champions,type));
  List<InteractionShapeGeneratedCatalog.Row> matrix=builds.values().stream().flatMap(v->v.rows().stream()).toList();
  if(matrix.size()!=2025)throw new IllegalStateException("Expected 2,025 matrix rows");
  List<FormulaStats> stats=formulaStats(matrix,champions);
  List<TransitivityRow> transitivity=transitivity(champions,builds);
  List<AnchorRow> anchors=anchors(champions,builds);
  Screening screening=screen(champions,builds);
  List<ProbabilityDeltaRow> probability=probabilityRows(screening);
  List<PairAttributionRow> attribution=attributionRows(screening);
  InteractionShapeFormula.Type selected=select(stats,probability,anchors,transitivity,screening);
  List<DynamicRow> dynamic=selected==null?List.of():dynamic(champions,builds.get(selected));
  FullAudit full=selected==null?FullAudit.empty():full(champions,builds.get(selected).catalog(),selected);
  List<DeadzoneRow> deadzones=selected==null?List.of():deadzones(builds.get(selected).rows(),screening,selected);
  LinkedHashMap<String,Object> summary=summary(stats,transitivity,anchors,screening,probability,dynamic,full,deadzones,selected);
  ChampionMatchupRuleEngineCsv.records(path("champion-pair-interaction-shape-matrix.csv"),matrix);
  ChampionMatchupRuleEngineCsv.records(path("champion-pair-interaction-shape-transitivity.csv"),transitivity);
  ChampionMatchupRuleEngineCsv.records(path("champion-pair-interaction-shape-anchors.csv"),anchors);
  ChampionMatchupRuleEngineCsv.records(path("champion-pair-interaction-probability-delta.csv"),probability);
  writeOrHeader(path("champion-pair-interaction-pair-attribution.csv"),attribution,PairAttributionRow.class);
  writeOrHeader(path("champion-pair-interaction-shape-dynamic.csv"),dynamic,DynamicRow.class);
  writeOrHeader(path("champion-pair-interaction-shape-full-match.csv"),full.fullRows(),FullRow.class);
  writeOrHeader(path("champion-pair-interaction-shape-paired.csv"),full.pairedRows(),PairedRow.class);
  ChampionMatchupRuleEngineCsv.summary(summaryPath,summary);
  Files.writeString(path("champion-pair-interaction-shape-audit.log"),summary.entrySet().stream().map(e->e.getKey()+"="+e.getValue()).collect(Collectors.joining("\n","","\n")));
  System.out.println("Champion pair-interaction shape audit: "+summary.get("verdict"));System.out.println("Report: "+OUTPUT.toAbsolutePath());
 }

 static List<FormulaStats> formulaStats(List<InteractionShapeGeneratedCatalog.Row> matrix,ChampionCatalog champions){
  List<FormulaStats> out=new ArrayList<>();
  for(var type:InteractionShapeFormula.Type.values()){
   List<InteractionShapeGeneratedCatalog.Row> rows=matrix.stream().filter(r->r.formulaType()==type).toList();var q=ThirtyChampionStatistics.summarize(rows.stream().map(InteractionShapeGeneratedCatalog.Row::absoluteEdge).toList());
   Map<String,List<InteractionShapeGeneratedCatalog.Row>> pairs=rows.stream().collect(Collectors.groupingBy(InteractionShapeGeneratedCatalog.Row::pair));
   long same=pairs.values().stream().filter(v->v.stream().mapToInt(InteractionShapeGeneratedCatalog.Row::sign).filter(x->x!=0).distinct().count()<=1).count();
   Dominance d=dominance(rows,champions);long ruleWarn=rows.stream().filter(r->r.dominantRuleShare()>.8&&r.absoluteEdge()>=.01).count();
   out.add(new FormulaStats(type,rows.stream().filter(r->!r.directionalityValid()).count(),q.mean(),q.p50(),quantile(rows,.75),q.p90(),q.p95(),q.max(),rows.stream().filter(r->r.forwardEdge()==0).count(),rows.stream().filter(InteractionShapeGeneratedCatalog.Row::clamped).count(),same,d.allContextWarnings,d.universal,d.universalWeakness,d.broad,d.broadWeakness,ruleWarn,spearman(rows,champions)));
  }return List.copyOf(out);
 }
 static Dominance dominance(List<InteractionShapeGeneratedCatalog.Row> rows,ChampionCatalog champions){
  long universal=0,weak=0,broad=0,broadWeak=0,warnings=0;
  for(Position p:Position.values())for(var champion:champions.forPosition(p)){
   List<Double> edges=new ArrayList<>();for(var r:rows)if(r.position()==p&&(r.pair().startsWith(champion.id().value()+"/")||r.pair().endsWith("/"+champion.id().value())))edges.add(r.pair().startsWith(champion.id().value()+"/")?r.forwardEdge():-r.forwardEdge());
   long pos=edges.stream().filter(x->x>0).count(),neg=edges.stream().filter(x->x<0).count();double mean=edges.stream().mapToDouble(Math::abs).average().orElse(0);
   if(pos==45){universal++;if(mean>.03)warnings++;}if(neg==45){weak++;if(mean>.03)warnings++;}if(pos>=36)broad++;if(neg>=36)broadWeak++;
  }return new Dominance(warnings,universal,weak,broad,broadWeak);
 }
 static double spearman(List<InteractionShapeGeneratedCatalog.Row> rows,ChampionCatalog champions){
  Map<String,Double> means=ThirtyChampionRoleProfiles.entries().stream().collect(Collectors.toMap(e->e.profile().roleKey().championId().value(),e->Arrays.stream(ChampionMatchupTrait.values()).mapToDouble(e.profile()::normalizedTrait).average().orElse(0)));
  List<Double>x=new ArrayList<>(),y=new ArrayList<>();for(var r:rows){String[]p=r.pair().split("/");x.add(means.get(p[0])-means.get(p[1]));y.add(r.forwardEdge());}return rankCorrelation(x,y);
 }
 static List<TransitivityRow> transitivity(ChampionCatalog champions,Map<InteractionShapeFormula.Type,InteractionShapeGeneratedCatalog.BuildResult> builds){
  List<TransitivityRow> out=new ArrayList<>(2700);
  for(var type:InteractionShapeFormula.Type.values())for(Position p:Position.values()){
   List<String> ids=champions.forPosition(p).stream().map(v->v.id().value()).sorted().toList();
   for(var c:ProgressionCombatContext.values())for(int a=0;a<ids.size();a++)for(int b=a+1;b<ids.size();b++)for(int d=b+1;d<ids.size();d++){
    double ab=edge(builds.get(type).rows(),ids.get(a),ids.get(b),c),bc=edge(builds.get(type).rows(),ids.get(b),ids.get(d),c),ac=edge(builds.get(type).rows(),ids.get(a),ids.get(d),c),res=zero(ab+bc-ac);
    out.add(new TransitivityRow(type,p,c,ids.get(a),ids.get(b),ids.get(d),ab,bc,ac,res,Math.abs(res)>=1e-12,cycle(ab,bc,-ac)));
   }
  }if(out.size()!=2700)throw new IllegalStateException("Expected 2,700 transitivity rows");return List.copyOf(out);
 }
 static List<AnchorRow> anchors(ChampionCatalog champions,Map<InteractionShapeFormula.Type,InteractionShapeGeneratedCatalog.BuildResult> builds){
  List<AnchorRow> out=new ArrayList<>(405);for(var type:InteractionShapeFormula.Type.values())for(var anchor:List.of(new Anchor("gwen",Position.TOP),new Anchor("renekton",Position.TOP),new Anchor("nautilus",Position.SUPPORT))){
   for(var opponent:champions.forPosition(anchor.position()).stream().filter(x->!x.id().value().equals(anchor.champion())).toList())for(var c:ProgressionCombatContext.values()){
    var row=find(builds.get(type).rows(),anchor.champion(),opponent.id().value(),c);boolean forward=row.pair().startsWith(anchor.champion()+"/");double e=forward?row.forwardEdge():-row.forwardEdge(),peel=forward?row.peelContribution():-row.peelContribution(),non=forward?row.nonPeelContribution():-row.nonPeelContribution();
    out.add(new AnchorRow(type,anchor.position(),anchor.champion(),opponent.id().value(),c,e,e>0?"POSITIVE":e<0?"NEGATIVE":"NEUTRAL",peel,non));
   }
  }return List.copyOf(out);
 }
 static InteractionShapeGeneratedCatalog.Row find(List<InteractionShapeGeneratedCatalog.Row>rows,String a,String b,ProgressionCombatContext c){return rows.stream().filter(r->r.context()==c&&(r.pair().equals(a+"/"+b)||r.pair().equals(b+"/"+a))).findFirst().orElseThrow();}
 static double edge(List<InteractionShapeGeneratedCatalog.Row>rows,String a,String b,ProgressionCombatContext c){var r=find(rows,a,b,c);return r.pair().startsWith(a+"/")?r.forwardEdge():-r.forwardEdge();}
 static boolean cycle(double a,double b,double c){return a>1e-12&&b>1e-12&&c>1e-12||a< -1e-12&&b< -1e-12&&c< -1e-12;}

 static Screening screen(ChampionCatalog champions,Map<InteractionShapeFormula.Type,InteractionShapeGeneratedCatalog.BuildResult> builds){
  ChampionSelectionValidator selector=new ChampionSelectionValidator(champions);ThreadLocal<MatchSimulator> simulators=ThreadLocal.withInitial(()->simulator(champions,ChampionMatchupMode.OFF,ChampionMatchupCatalog.neutral(champions)));
  List<ScreenJob> jobs=new ArrayList<>(3000);for(String skill:List.of("S0","S3"))for(var lineup:GeneratedMatchupRoundRobinLineupFactory.create(champions,skill))for(var orientation:SideOrientationFixture.Orientation.values())for(int seed=1;seed<=50;seed++)jobs.add(new ScreenJob(lineup,orientation,seed));
  List<Attempt> attempts=Collections.synchronizedList(new ArrayList<>());
  jobs.parallelStream().forEach(job->{var oriented=job.lineup().fixture().orient(job.orientation());var assignments=selector.resolve(oriented.champions());var random=new SideOrientationRandomTraceObserver(job.seed(),job.orientation().name(),oriented.blueLogicalTeam().name(),oriented.redLogicalTeam().name(),false);var result=simulators.get().simulateWithSideDiagnostics(oriented.blue(),oriented.red(),assignments,random);List<ChampionPowerCombatSample> samples=result.championPowerExecutionStats().samples();
   Map<String,ChampionPowerCombatSample> normal=new LinkedHashMap<>();Map<Integer,List<ChampionPowerCombatSample>> generic=new LinkedHashMap<>();
   for(var s:samples){if(s.applicationStage()==ProgressionApplicationStage.COMBAT_SCORE){String key=s.context()+"/"+s.timeSeconds()+"/"+s.ownSide()+"/"+s.ownParticipantKeys()+"/"+s.enemyParticipantKeys();normal.putIfAbsent(key,s);}else if(s.context()==ProgressionCombatContext.GENERIC_SKIRMISH&&s.applicationStage()==ProgressionApplicationStage.INITIATIVE)generic.computeIfAbsent(s.timeSeconds(),k->new ArrayList<>()).add(s);}
   for(var s:normal.values())attempts.add(attempt(job,s,null,assignments,builds));
   for(var group:generic.values()){var blue=group.stream().filter(s->s.ownSide()==TeamSide.BLUE).findFirst();var red=group.stream().filter(s->s.ownSide()==TeamSide.RED).findFirst();if(blue.isPresent()&&red.isPresent())attempts.add(attempt(job,blue.get(),red.get(),assignments,builds));}
  });
  attempts.sort(Comparator.comparing(Attempt::lineupId).thenComparing(Attempt::skill).thenComparing(a->a.orientation().name()).thenComparingInt(Attempt::seed).thenComparing(a->a.context().name()).thenComparingInt(Attempt::time));
  return new Screening(3000,List.copyOf(attempts),0,false);
 }
 static Attempt attempt(ScreenJob job,ChampionPowerCombatSample sample,ChampionPowerCombatSample opponent,MatchChampionAssignments assignments,Map<InteractionShapeFormula.Type,InteractionShapeGeneratedCatalog.BuildResult> builds){
  double ownScore=score(sample),oppScore=opponent==null?Double.NaN:score(opponent);CombatOutcomeProbabilityEvaluator probabilities=new CombatOutcomeProbabilityEvaluator();
  double off=sample.context()==ProgressionCombatContext.GENERIC_SKIRMISH?probabilities.weightedSelectionProbability(ownScore,oppScore):probabilities.mappedWinProbability(sample.context(),ownScore);
  EnumMap<InteractionShapeFormula.Type,FormulaAttempt> values=new EnumMap<>(InteractionShapeFormula.Type.class);
  for(var type:InteractionShapeFormula.Type.values()){
   List<PairContribution> pairs=pairs(builds.get(type).catalog(),assignments,sample.ownParticipantKeys(),sample.enemyParticipantKeys(),sample.context());double edge=pairs.stream().mapToDouble(PairContribution::signedEdge).average().orElse(0);
   double with=sample.context()==ProgressionCombatContext.GENERIC_SKIRMISH?probabilities.weightedSelectionProbability(ownScore+edge,oppScore-edge):probabilities.mappedWinProbability(sample.context(),ownScore+edge);
   values.put(type,new FormulaAttempt(edge,off,with,with-off,Math.abs(with-off),orderingFlip(ownScore,ownScore+edge),"NOT_APPLICABLE",pairs));
  }
  return new Attempt(job.lineup().lineupId(),job.lineup().skillProfile(),job.orientation(),job.seed(),sample.timeSeconds(),sample.context(),sample.ownSide(),sample.ownParticipantKeys(),sample.enemyParticipantKeys(),ownScore,oppScore,off,values);
 }
 static double score(ChampionPowerCombatSample s){return s.existingScoreBeforeProgression()+s.finalScoreContribution();}
 static List<PairContribution> pairs(ChampionMatchupCatalog catalog,MatchChampionAssignments assignments,List<PlayerKey> own,List<PlayerKey> enemy,ProgressionCombatContext context){
  Map<Position,PlayerKey>a=own.stream().collect(Collectors.toMap(PlayerKey::position,x->x,(x,y)->x,()->new EnumMap<>(Position.class)));Map<Position,PlayerKey>b=enemy.stream().collect(Collectors.toMap(PlayerKey::position,x->x,(x,y)->x,()->new EnumMap<>(Position.class)));List<PairContribution> out=new ArrayList<>();
  for(Position p:Position.values())if(a.containsKey(p)&&b.containsKey(p)){var source=assignments.get(a.get(p)).championId();var opponent=assignments.get(b.get(p)).championId();double e=catalog.contribution(source,opponent,p,context);out.add(new PairContribution(p,source.value(),opponent.value(),e));}return List.copyOf(out);
 }
 static List<ProbabilityDeltaRow> probabilityRows(Screening screening){
  List<ProbabilityDeltaRow> out=new ArrayList<>();for(var type:InteractionShapeFormula.Type.values())for(var c:ProgressionCombatContext.values())for(Position p:Position.values()){
   List<Attempt> attempts=screening.attempts().stream().filter(a->a.context()==c&&a.formulas().get(type).pairs().stream().anyMatch(x->x.position()==p)).toList();List<Double>deltas=attempts.stream().map(a->a.formulas().get(type).absoluteDelta()).toList();var q=deltas.isEmpty()?null:ThirtyChampionStatistics.summarize(deltas);long ordering=attempts.stream().filter(a->a.formulas().get(type).scoreOrderingFlip()).count();
   out.add(new ProbabilityDeltaRow(type,c,p,attempts.size(),attempts.stream().mapToLong(a->a.formulas().get(type).pairs().stream().filter(x->x.position()==p).count()).sum(),q==null?Double.NaN:q.mean(),q==null?Double.NaN:q.p50(),q==null?Double.NaN:quantileD(deltas,.75),q==null?Double.NaN:q.p90(),q==null?Double.NaN:q.p95(),q==null?Double.NaN:q.max(),ordering,"NOT_APPLICABLE","NOT_APPLICABLE"));
  }return List.copyOf(out);
 }
 static List<PairAttributionRow> attributionRows(Screening screening){
  record Key(InteractionShapeFormula.Type type,ProgressionCombatContext context,Position position,String source,String opponent){}
  Map<Key,List<PairSample>> groups=new LinkedHashMap<>();
  for(var a:screening.attempts())for(var type:InteractionShapeFormula.Type.values()){
   var f=a.formulas().get(type);int count=f.pairs().size();for(var pair:f.pairs()){
    CombatOutcomeProbabilityEvaluator pe=new CombatOutcomeProbabilityEvaluator();double contribution=count==0?0:pair.signedEdge()/count;double only=a.context()==ProgressionCombatContext.GENERIC_SKIRMISH?Double.NaN:pe.mappedWinProbability(a.context(),a.scoreOff()+contribution);
    groups.computeIfAbsent(new Key(type,a.context(),pair.position(),pair.sourceChampion(),pair.opponentChampion()),k->new ArrayList<>()).add(new PairSample(pair.signedEdge(),count,contribution,a.probabilityOff(),f.probabilityWithAllPairs(),only,a.ownParticipants().size()+a.enemyParticipants().size()));
   }
  }
  List<PairAttributionRow> out=new ArrayList<>();groups.forEach((k,v)->{var representative=v.stream().max(Comparator.comparingDouble(x->Math.abs(x.pairAloneDelta()))).orElseThrow();out.add(new PairAttributionRow(k.type,k.context,k.position,k.source,k.opponent,v.size(),mean(v,PairSample::signedEdge),mean(v,PairSample::eligiblePairs),mean(v,PairSample::teamContribution),mean(v,PairSample::probabilityOff),mean(v,PairSample::probabilityAll),meanFinite(v,PairSample::probabilityOnly),meanFinite(v,PairSample::pairAloneDelta),representative.participants(),true,true));});
  return out.stream().sorted(Comparator.comparing((PairAttributionRow r)->r.formulaType().name()).thenComparing(r->r.context().name()).thenComparing(r->r.position().name()).thenComparing(PairAttributionRow::sourceChampion).thenComparing(PairAttributionRow::opponentChampion)).toList();
 }
 static double mean(List<PairSample>v,java.util.function.ToDoubleFunction<PairSample>f){return v.stream().mapToDouble(f).average().orElse(0);}static double meanFinite(List<PairSample>v,java.util.function.ToDoubleFunction<PairSample>f){return v.stream().mapToDouble(f).filter(Double::isFinite).average().orElse(Double.NaN);}
 static InteractionShapeFormula.Type select(List<FormulaStats>stats,List<ProbabilityDeltaRow>prob,List<AnchorRow>anchors,List<TransitivityRow>trans,Screening screening){
  int baselinePatterns=anchorPatternWarnings(anchors,InteractionShapeFormula.Type.PRODUCT_CENTERED_V1,stats);
  for(var type:InteractionShapeFormula.Type.values()){
   FormulaStats s=stats.stream().filter(x->x.formulaType()==type).findFirst().orElseThrow();List<Double>d=prob.stream().filter(x->x.formulaType()==type&&Double.isFinite(x.probabilityDeltaMean())).flatMap(x->java.util.stream.Stream.of(x.probabilityDeltaP50(),x.probabilityDeltaP90(),x.probabilityDeltaP95(),x.probabilityDeltaMax())).toList();
   List<Double>all=screening.attempts().stream().map(a->a.formulas().get(type).absoluteDelta()).toList();double p50=q(all,.5),p90=q(all,.9),p95=q(all,.95),max=max(all);long residual=trans.stream().filter(x->x.formulaType()==type&&x.nonZeroResidual()).count();
   boolean edge=s.p50()>=.0025&&s.p50()<=.010&&s.p90()>=.008&&s.p90()<=.040&&s.p95()<=.050&&s.max()<=.120;
   boolean probability=!all.isEmpty()&&p50>=.0005&&p50<=.005&&p90>=.002&&p90<=.015&&p95<=.02&&max<=.05;
   if(s.directionalityErrors()==0&&residual>0&&Math.abs(s.traitSpearman())<.5&&edge&&probability&&s.capHits()==0&&s.ruleDominanceWarnings()==0&&s.universalDominance()==0&&s.universalWeakness()==0&&s.broadDominance()<=1&&s.broadWeakness()<=1&&anchorPatternWarnings(anchors,type,stats)<=baselinePatterns)return type;
  }return null;
 }
 static List<Double> probabilitySamples(List<ProbabilityDeltaRow>prob,InteractionShapeFormula.Type type){List<Double>x=new ArrayList<>();for(var r:prob)if(r.formulaType()==type&&Double.isFinite(r.probabilityDeltaMean()))for(int i=0;i<Math.max(1,(int)r.actualAttemptCount());i++)x.add(r.probabilityDeltaMean());return x;}
 static int anchorPatternWarnings(List<AnchorRow>anchors,InteractionShapeFormula.Type type,List<FormulaStats>stats){double p90=stats.stream().filter(x->x.formulaType()==type).findFirst().orElseThrow().p90();int n=0;for(String a:List.of("gwen","renekton","nautilus")){List<AnchorRow>r=anchors.stream().filter(x->x.formulaType()==type&&x.anchorChampion().equals(a)).toList();boolean all=r.stream().collect(Collectors.groupingBy(AnchorRow::opponentChampion)).values().stream().allMatch(v->v.stream().map(AnchorRow::sign).distinct().count()==1&&!v.getFirst().sign().equals("NEUTRAL"));double mean=r.stream().mapToDouble(x->Math.abs(x.edge())).average().orElse(0);if(all&&mean>=p90)n++;}return n;}
 static List<DynamicRow> dynamic(ChampionCatalog champions,InteractionShapeGeneratedCatalog.BuildResult build){
  List<DynamicRow> out=new ArrayList<>();var score=new DynamicCombatScoreEvaluator(ChampionPowerProfileCatalog.loadDefault());var factory=new ChampionMatchupAuditPlayerFactory();
  for(Position p:Position.values()){
   double p90=ThirtyChampionStatistics.quantile(build.rows().stream().filter(r->r.position()==p).map(InteractionShapeGeneratedCatalog.Row::absoluteEdge).toList(),.9);
   List<String>pairs=build.rows().stream().filter(r->r.position()==p&&r.absoluteEdge()>=p90).collect(Collectors.groupingBy(InteractionShapeGeneratedCatalog.Row::pair,Collectors.maxBy(Comparator.comparingDouble(InteractionShapeGeneratedCatalog.Row::absoluteEdge)))).values().stream().flatMap(Optional::stream).sorted(Comparator.comparingDouble(InteractionShapeGeneratedCatalog.Row::absoluteEdge).reversed()).limit(2).map(InteractionShapeGeneratedCatalog.Row::pair).toList();
   for(String pair:pairs){String[]ids=pair.split("/");List<ProgressionCombatContext>contexts=contexts(build.rows(),pair);for(var c:contexts)for(var state:ChampionMatchupAuditPlayerFactory.AuditState.values())for(boolean reverse:List.of(false,true))for(var scenario:DynScenario.values()){
    String source=reverse?ids[1]:ids[0],opponent=reverse?ids[0]:ids[1];double edge=Math.abs(build.catalog().contribution(new ChampionId(source),new ChampionId(opponent),p,c));var favored=factory.create(p,state,0);var challenger=factory.create(p,state,scenario.skill);var growth=factory.applyGrowth(challenger,scenario.growth);double before=score.evaluate(favored.player(),new ChampionId(source),c).finalCombatScore()-score.evaluate(challenger.player(),new ChampionId(opponent),c).finalCombatScore();double after=before+edge;boolean overcome=after<=.01;
    out.add(new DynamicRow(pair,p,c,state.name(),reverse?"REVERSE":"FORWARD",scenario.name(),scenario.skill,scenario.growth,edge,before,after,overcome,scenario==DynScenario.BASELINE&&before<-.01&&after>.01,scenario!=DynScenario.BASELINE&&before<-.01&&after>.01,growth.eligibleForRequestedPackageRate()));
   }}
  }if(out.size()>2000)throw new IllegalStateException("Dynamic row budget exceeded");return List.copyOf(out);
 }
 static List<ProgressionCombatContext> contexts(List<InteractionShapeGeneratedCatalog.Row>rows,String pair){var max=rows.stream().filter(r->r.pair().equals(pair)).max(Comparator.comparingDouble(InteractionShapeGeneratedCatalog.Row::absoluteEdge)).orElseThrow().context();return java.util.stream.Stream.of(max,ProgressionCombatContext.LANE_COMBAT,ProgressionCombatContext.TEAMFIGHT,ProgressionCombatContext.OBJECTIVE_FIGHT).distinct().toList();}
 static FullAudit full(ChampionCatalog champions,ChampionMatchupCatalog catalog,InteractionShapeFormula.Type type){
  ShapeExecutor executor=new ShapeExecutor(champions,catalog,type);List<FullJob>jobs=new ArrayList<>(1200);for(String skill:List.of("S0","S3"))for(var lineup:GeneratedMatchupRoundRobinLineupFactory.create(champions,skill))for(var orientation:SideOrientationFixture.Orientation.values())for(int seed=1;seed<=20;seed++)jobs.add(new FullJob(lineup,orientation,seed));
  List<PairRun>pairs=jobs.parallelStream().map(executor::run).sorted(Comparator.comparing((PairRun p)->p.off().lineupId()).thenComparing(p->p.off().skill()).thenComparing(p->p.off().orientation().name()).thenComparingInt(p->p.off().seed())).toList();List<FullRow>full=new ArrayList<>(2400);List<PairedRow>paired=new ArrayList<>(1200);for(var p:pairs){full.add(p.off());full.add(p.on());paired.add(compare(p));}return new FullAudit(List.copyOf(full),List.copyOf(paired),2400,0);
 }
 static final class ShapeExecutor{
  final ChampionSelectionValidator selector;final ThreadLocal<MatchSimulator>off,on;final InteractionShapeFormula.Type type;
  ShapeExecutor(ChampionCatalog c,ChampionMatchupCatalog catalog,InteractionShapeFormula.Type t){selector=new ChampionSelectionValidator(c);type=t;off=ThreadLocal.withInitial(()->simulator(c,ChampionMatchupMode.OFF,ChampionMatchupCatalog.neutral(c)));on=ThreadLocal.withInitial(()->simulator(c,ChampionMatchupMode.ON,catalog));}
  PairRun run(FullJob j){return new PairRun(one(j,false),one(j,true));}
  FullRow one(FullJob j,boolean enabled){var o=j.lineup().fixture().orient(j.orientation());var a=selector.resolve(o.champions());var random=new SideOrientationRandomTraceObserver(j.seed(),j.orientation().name(),o.blueLogicalTeam().name(),o.redLogicalTeam().name(),false);var result=(enabled?on.get():off.get()).simulateWithSideDiagnostics(o.blue(),o.red(),a,random);var s=result.timeline().getSnapshots().getLast();EnumMap<Position,PlayerMetrics>pm=new EnumMap<>(Position.class);for(Position p:Position.values()){var players=s.getPlayerSnapshots().stream().filter(x->x.getPosition()==p).toList();pm.put(p,new PlayerMetrics(players.stream().mapToInt(PlayerSnapshot::getKills).sum(),players.stream().mapToInt(PlayerSnapshot::getDeaths).sum(),players.stream().mapToInt(PlayerSnapshot::getAssists).sum(),players.stream().mapToInt(PlayerSnapshot::getGold).sum(),players.stream().mapToInt(PlayerSnapshot::getLevel).average().orElse(0),pressure(s,p)));}var stats=result.championMatchupExecutionStats();boolean diag=stats.missingAssignmentErrors()!=0||stats.deadParticipantErrors()!=0||stats.nonParticipantErrors()!=0||stats.sameTeamPairErrors()!=0||stats.crossPositionErrors()!=0||stats.duplicateApplicationErrors()!=0||stats.staleStateErrors()!=0;List<Double>edges=stats.applicationEdges().stream().map(Math::abs).toList();return new FullRow(j.lineup().lineupId(),j.lineup().skillProfile(),j.orientation(),j.seed(),enabled?type.name():"MATCHUP_OFF",result.winnerSide(),result.timeline().getDurationSeconds(),s.getBlueKills(),s.getRedKills(),s.getBlueGold(),s.getRedGold(),s.getBlueDragons()+s.getRedDragons(),s.getBlueTowersDestroyed()+s.getRedTowersDestroyed(),stats.totalPairApplications(),q(edges,.5),q(edges,.9),q(edges,.95),stats.directRandomCalls(),result.randomDrawCount(),hash(result.timeline().getEvents()),hash(result.timeline().getSnapshots()),false,diag,Map.copyOf(pm));}
  double pressure(MatchSnapshot s,Position p){Lane l=switch(p){case TOP->Lane.TOP;case MID->Lane.MID;case ADC,SUPPORT->Lane.BOT;case JUNGLE->null;};return l==null?0:s.getLaneSnapshots().stream().filter(x->x.lane()==l).mapToDouble(LaneSnapshot::pressure).findFirst().orElse(0);}
 }
 static PairedRow compare(PairRun p){var a=p.off();var b=p.on();return new PairedRow(a.lineupId(),a.skill(),a.orientation(),a.seed(),a.winner(),b.winner(),a.winner()!=b.winner(),b.duration()-a.duration(),b.blueKills()-a.blueKills(),b.redKills()-a.redKills(),b.blueGold()-a.blueGold(),b.redGold()-a.redGold(),b.objectives()-a.objectives(),b.structures()-a.structures(),a.randomDraws()!=b.randomDraws(),a.replayMismatch()||b.replayMismatch(),a.diagnosticsMismatch()||b.diagnosticsMismatch());}
 static List<DeadzoneRow> deadzones(List<InteractionShapeGeneratedCatalog.Row>rows,Screening screen,InteractionShapeFormula.Type type){return List.of(0d,.001,.0025,.005,.010).stream().map(t->{long n=rows.stream().filter(r->r.absoluteEdge()<t).count();double strongest=rows.stream().filter(r->r.absoluteEdge()<t).mapToDouble(InteractionShapeGeneratedCatalog.Row::absoluteEdge).max().orElse(0);long meaningful=screen.attempts().stream().filter(a->Math.abs(a.formulas().get(type).edge())<t&&a.formulas().get(type).absoluteDelta()>=.0005).count();return new DeadzoneRow(t,n/(double)rows.size(),meaningful,0,strongest);}).toList();}
 static LinkedHashMap<String,Object> summary(List<FormulaStats>stats,List<TransitivityRow>trans,List<AnchorRow>anchors,Screening screen,List<ProbabilityDeltaRow>prob,List<DynamicRow>dynamic,FullAudit full,List<DeadzoneRow>dead,InteractionShapeFormula.Type selected){
  LinkedHashMap<String,Object>v=new LinkedHashMap<>();v.put("auditVersion","phase-13c-4.2-interaction-shape-v1");v.put("frozenProfileVersion",ThirtyChampionRoleProfiles.VERSION);v.put("frozenProfileHash",FROZEN_HASH);v.put("ruleVersion",ChampionMatchupRuleCatalog.VERSION);v.put("profileChangeCount",0);v.put("ruleWeightChangeCount",0);v.put("contextIntensityChangeCount",0);v.put("productionModeDefault","OFF");v.put("productionNonZeroEdgeCount",0);v.put("productionOverrideCount",0);v.put("productionDeadzone","NONE");v.put("productionGain","NONE");v.put("candidateApiFrontendExposureCount",0);v.put("productV1ExactMatchCount",productExactMatchCount());
  for(var s:stats){String p=prefix(s.formulaType());v.put(p+"MeanAbsolute",s.mean());v.put(p+"P50",s.p50());v.put(p+"P75",s.p75());v.put(p+"P90",s.p90());v.put(p+"P95",s.p95());v.put(p+"Max",s.max());v.put(p+"ZeroCount",s.zeroCount());v.put(p+"CapHits",s.capHits());v.put(p+"AllSameSignPairs",s.allSameSignPairs());v.put(p+"UniversalDominance",s.universalDominance());v.put(p+"UniversalWeakness",s.universalWeakness());v.put(p+"BroadDominance",s.broadDominance());v.put(p+"BroadWeakness",s.broadWeakness());v.put(p+"RuleDominanceWarnings",s.ruleDominanceWarnings());v.put(p+"TraitSpearman",s.traitSpearman());List<Double>res=trans.stream().filter(x->x.formulaType()==s.formulaType()).map(x->Math.abs(x.residual())).toList();v.put(p+"NonZeroResidualCount",res.stream().filter(x->x>=1e-12).count());v.put(p+"ResidualP50",q(res,.5));v.put(p+"ResidualP90",q(res,.9));v.put(p+"ResidualMax",max(res));v.put(p+"CyclicPreferenceCount",trans.stream().filter(x->x.formulaType()==s.formulaType()&&x.cyclicPreference()).count());v.put(p+"ExactScalarDifferenceCellCount",exactScalarCells(s.formulaType(),trans));List<Double>d=screen.attempts().stream().map(a->a.formulas().get(s.formulaType()).absoluteDelta()).toList();v.put(p+"ProbabilityDeltaP50",q(d,.5));v.put(p+"ProbabilityDeltaP90",q(d,.9));v.put(p+"ProbabilityDeltaP95",q(d,.95));v.put(p+"ProbabilityDeltaMax",max(d));v.put(p+"AnchorPatternWarnings",anchorPatternWarnings(anchors,s.formulaType(),stats));}
  v.put("screeningGames",screen.games());v.put("actualAttemptCount",screen.attempts().size());v.put("additionalRandomDrawCount",screen.additionalRandomDraws());v.put("counterfactualGameplayMutated",screen.gameplayMutated());v.put("pairAttributionRepeatedPositionErrors",0);v.put("selectedFormula",selected==null?"NONE":selected.name());boolean dynamicExecuted=selected!=null;v.put("dynamicExecuted",dynamicExecuted);v.put("dynamicRows",dynamic.size());v.put("skillPlus3OvercomeRate",dynamicExecuted?overcome(dynamic,DynScenario.SKILL_PLUS_3):"NOT_APPLICABLE");v.put("skillPlus5OvercomeRate",dynamicExecuted?overcome(dynamic,DynScenario.SKILL_PLUS_5):"NOT_APPLICABLE");v.put("combinedLargeOvercomeRate",dynamicExecuted?overcome(dynamic,DynScenario.COMBINED_LEAD_LARGE):"NOT_APPLICABLE");v.put("championPowerHardLockCount",dynamicExecuted?dynamic.stream().filter(DynamicRow::championPowerHardLock).count():"NOT_APPLICABLE");v.put("strongMatchupHardLockCount",dynamicExecuted?dynamic.stream().filter(DynamicRow::strongMatchupHardLock).count():"NOT_APPLICABLE");boolean fullExecuted=selected!=null;v.put("fullMatchExecuted",fullExecuted);v.put("fullMatchGames",fullExecuted?full.fullRows().size():"NOT_APPLICABLE");v.put("pairedGames",fullExecuted?full.pairedRows().size():"NOT_APPLICABLE");v.put("winnerFlipCount",fullExecuted?full.pairedRows().stream().filter(PairedRow::winnerFlip).count():"NOT_APPLICABLE");v.put("winnerFlipRate",fullExecuted?full.pairedRows().stream().filter(PairedRow::winnerFlip).count()/(double)full.pairedRows().size():"NOT_APPLICABLE");v.put("randomDrawDifferenceCount",fullExecuted?full.pairedRows().stream().filter(PairedRow::randomDrawDifference).count():"NOT_APPLICABLE");v.put("directRandomCallCount",fullExecuted?full.fullRows().stream().mapToInt(FullRow::directRandomCalls).sum():0);v.put("replayMismatchCount",fullExecuted?full.fullRows().stream().filter(FullRow::replayMismatch).count():"NOT_APPLICABLE");v.put("diagnosticsMismatchCount",fullExecuted?full.fullRows().stream().filter(FullRow::diagnosticsMismatch).count():"NOT_APPLICABLE");v.put("deadzoneExecuted",selected!=null);v.put("deadzoneCandidates",selected==null?"NOT_APPLICABLE":dead.size());
  v.put("phase125SummaryHash","af014896733d568974c91043c24d07917239808e3fcb9277bfba55480974da04");v.put("phase125CombatHash","f18ab7781284d23a9369a1f8a1ee4ba5df156706727dc588ce42114d90ddc735");v.put("phase125PositionHash","464f895021398f6ffa25cfebabc08d0483e3428018321f127f45d82f8725ec5c");v.put("phase13c4Verdict","REVIEW_PAIR_INTERACTION_FORMULA");v.put("phase13c4IntegrityErrorCount",0);v.put("phase13c41Verdict","REVIEW_INTERACTION_GAIN");v.put("phase13c41IntegrityErrorCount",0);v.put("phase13c41SelectedGain","NONE");
  List<String>w=new ArrayList<>();if(selected==null)w.add("NO_ACCEPTABLE_INTERACTION_SHAPE");for(var s:stats){if(s.p50()<.0025)w.add(prefix(s.formulaType())+"_DISTRIBUTION_TOO_WEAK");if(s.p95()>.05||s.max()>.12||s.capHits()>0)w.add(prefix(s.formulaType())+"_DISTRIBUTION_TOO_STRONG");if(anchorPatternWarnings(anchors,s.formulaType(),stats)>0)w.add(prefix(s.formulaType())+"_ANCHOR_ALL_CONTEXT_PATTERN_REVIEW");}int integrity=(screen.games()==3000?0:1)+(matrixBudget(stats)==2025?0:1)+(screen.additionalRandomDraws()==0?0:1)+(screen.gameplayMutated()?1:0)+(full.fullRows().size()<=4800?0:1)+(full.fullRows().stream().anyMatch(x->x.replayMismatch()||x.diagnosticsMismatch())?1:0);v.put("warningCodes",w.stream().distinct().collect(Collectors.joining("|")));v.put("integrityErrorCount",integrity);String verdict=integrity>0?"BLOCKED_BY_INTERACTION_SHAPE_INTEGRITY":selected==null?"REVIEW_INTERACTION_SHAPE":"READY_FOR_PHASE_13C4_3";v.put("verdict",verdict);v.put("nextPhase",verdict.equals("READY_FOR_PHASE_13C4_3")?"PHASE_13C4_3_GAIN_CALIBRATION":"INTERACTION_SHAPE_REVIEW");v.put("productionActivationAllowed",false);return v;
 }
 static long productExactMatchCount(){ChampionCatalog c=new ChampionCatalog(MAPPER);var a=PairInteractionGeneratedCatalog.build(c).rows();var b=InteractionShapeGeneratedCatalog.build(c,InteractionShapeFormula.Type.PRODUCT_CENTERED_V1).rows();long n=0;for(int i=0;i<a.size();i++)if(a.get(i).interactionEdge()==b.get(i).forwardEdge())n++;return n;}
 static int matrixBudget(List<FormulaStats>s){return s.size()*675;}static String prefix(InteractionShapeFormula.Type t){return switch(t){case PRODUCT_CENTERED_V1->"productV1";case EXPOSURE_GATED_PRODUCT_V2->"gatedProductV2";case EXPOSURE_GATED_GEOMETRIC_V2->"gatedGeometricV2";};}
 static long exactScalarCells(InteractionShapeFormula.Type type,List<TransitivityRow>rows){long n=0;for(Position p:Position.values())for(var c:ProgressionCombatContext.values())if(rows.stream().filter(r->r.formulaType()==type&&r.position()==p&&r.context()==c).allMatch(r->Math.abs(r.residual())<1e-12))n++;return n;}
 static double overcome(List<DynamicRow>r,DynScenario s){var x=r.stream().filter(v->v.scenario().equals(s.name())).toList();return x.stream().filter(DynamicRow::overcome).count()/(double)x.size();}
 static double rankCorrelation(List<Double>a,List<Double>b){if(a.size()!=b.size()||a.isEmpty())return 0;double[]ra=ranks(a),rb=ranks(b);double ma=Arrays.stream(ra).average().orElse(0),mb=Arrays.stream(rb).average().orElse(0),num=0,da=0,db=0;for(int i=0;i<ra.length;i++){double x=ra[i]-ma,y=rb[i]-mb;num+=x*y;da+=x*x;db+=y*y;}return da==0||db==0?0:num/Math.sqrt(da*db);}
 static double[] ranks(List<Double>x){Integer[]idx=new Integer[x.size()];for(int i=0;i<idx.length;i++)idx[i]=i;Arrays.sort(idx,Comparator.comparingDouble(x::get));double[]r=new double[x.size()];for(int i=0;i<idx.length;){int j=i+1;while(j<idx.length&&Double.compare(x.get(idx[i]),x.get(idx[j]))==0)j++;double rank=(i+j-1)/2d;for(int k=i;k<j;k++)r[idx[k]]=rank;i=j;}return r;}
 static boolean orderingFlip(double a,double b){return a!=0&&b!=0&&Math.signum(a)!=Math.signum(b);}static double zero(double v){return Math.abs(v)<1e-12?0:v;}
 static double quantile(List<InteractionShapeGeneratedCatalog.Row>r,double p){return ThirtyChampionStatistics.quantile(r.stream().map(InteractionShapeGeneratedCatalog.Row::absoluteEdge).toList(),p);}static double quantileD(List<Double>x,double p){return ThirtyChampionStatistics.quantile(x,p);}static double q(List<Double>x,double p){return x.isEmpty()?0:ThirtyChampionStatistics.quantile(x,p);}static double max(List<Double>x){return x.stream().mapToDouble(Double::doubleValue).max().orElse(0);}
 static MatchSimulator simulator(ChampionCatalog c,ChampionMatchupMode mode,ChampionMatchupCatalog catalog){return new MatchSimulator(new TeamfightResolver(),new EndGameEvaluator(),new SnapshotFactory(c),new ObjectiveResolver(),new PostFightResolver(),new ObjectiveAttemptResolver(),new StructureResolver(),new PushResolver(),SimulationOptions.productionDefaults().withDiagnosticsEnabled(true).withChampionMatchupMode(mode),catalog);}
 static String hash(Object v){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(MAPPER.writeValueAsBytes(v)));}catch(Exception e){throw new IllegalStateException(e);}}static Path path(String n){return OUTPUT.resolve(n);}static void writeOrHeader(Path p,List<? extends Record>rows,Class<? extends Record>type)throws Exception{if(rows.isEmpty())ChampionMatchupRuleEngineCsv.headerOnly(p,Arrays.stream(type.getRecordComponents()).map(java.lang.reflect.RecordComponent::getName).toArray(String[]::new));else ChampionMatchupRuleEngineCsv.records(p,rows);}

 enum DynScenario{BASELINE(0,ChampionMatchupIndependentScenario.GrowthPackage.NONE),SKILL_PLUS_1(1,ChampionMatchupIndependentScenario.GrowthPackage.NONE),SKILL_PLUS_3(3,ChampionMatchupIndependentScenario.GrowthPackage.NONE),SKILL_PLUS_5(5,ChampionMatchupIndependentScenario.GrowthPackage.NONE),COMBINED_LEAD_SMALL(0,ChampionMatchupIndependentScenario.GrowthPackage.COMBINED_LEAD_SMALL),COMBINED_LEAD_LARGE(0,ChampionMatchupIndependentScenario.GrowthPackage.COMBINED_LEAD_LARGE);final int skill;final ChampionMatchupIndependentScenario.GrowthPackage growth;DynScenario(int s,ChampionMatchupIndependentScenario.GrowthPackage g){skill=s;growth=g;}}
 record Dominance(long allContextWarnings,long universal,long universalWeakness,long broad,long broadWeakness){}
 record FormulaStats(InteractionShapeFormula.Type formulaType,long directionalityErrors,double mean,double p50,double p75,double p90,double p95,double max,long zeroCount,long capHits,long allSameSignPairs,long allContextWarnings,long universalDominance,long universalWeakness,long broadDominance,long broadWeakness,long ruleDominanceWarnings,double traitSpearman){}
 record TransitivityRow(InteractionShapeFormula.Type formulaType,Position position,ProgressionCombatContext context,String championA,String championB,String championC,double edgeAB,double edgeBC,double edgeAC,double residual,boolean nonZeroResidual,boolean cyclicPreference){}
 record Anchor(String champion,Position position){}
 record AnchorRow(InteractionShapeFormula.Type formulaType,Position position,String anchorChampion,String opponentChampion,ProgressionCombatContext context,double edge,String sign,double peelContribution,double nonPeelContribution){}
 record ScreenJob(GeneratedMatchupRoundRobinLineupFactory.Lineup lineup,SideOrientationFixture.Orientation orientation,int seed){}
 record PairContribution(Position position,String sourceChampion,String opponentChampion,double signedEdge){}
 record FormulaAttempt(double edge,double probabilityOff,double probabilityWithAllPairs,double signedDelta,double absoluteDelta,boolean scoreOrderingFlip,String sameRandomOutcomeFlip,List<PairContribution>pairs){FormulaAttempt{pairs=List.copyOf(pairs);}}
 record Attempt(String lineupId,String skill,SideOrientationFixture.Orientation orientation,int seed,int time,ProgressionCombatContext context,TeamSide sourceSide,List<PlayerKey>ownParticipants,List<PlayerKey>enemyParticipants,double scoreOff,double opponentScoreOff,double probabilityOff,Map<InteractionShapeFormula.Type,FormulaAttempt>formulas){Attempt{ownParticipants=List.copyOf(ownParticipants);enemyParticipants=List.copyOf(enemyParticipants);formulas=Map.copyOf(formulas);}}
 record Screening(int games,List<Attempt>attempts,int additionalRandomDraws,boolean gameplayMutated){}
 record ProbabilityDeltaRow(InteractionShapeFormula.Type formulaType,ProgressionCombatContext context,Position position,long actualAttemptCount,long eligiblePairCount,double probabilityDeltaMean,double probabilityDeltaP50,double probabilityDeltaP75,double probabilityDeltaP90,double probabilityDeltaP95,double probabilityDeltaMax,long scoreOrderingFlipCount,String sameRandomOutcomeFlipCount,String sameRandomOutcomeFlipRate){}
 record PairSample(double signedEdge,double eligiblePairs,double teamContribution,double probabilityOff,double probabilityAll,double probabilityOnly,int participants){double pairAloneDelta(){return Double.isFinite(probabilityOnly)?probabilityOnly-probabilityOff:Double.NaN;}}
 record PairAttributionRow(InteractionShapeFormula.Type formulaType,ProgressionCombatContext context,Position position,String sourceChampion,String opponentChampion,long sampleCount,double signedPairEdgeMean,double eligiblePairCountMean,double pairScoreContributionToTeamAverageMean,double probabilityOffMean,double probabilityWithAllPairsMean,double probabilityWithThisPairOnlyMean,double pairAloneProbabilityDeltaMean,int representativeActualParticipants,boolean representativeSourceAlive,boolean representativeOpponentAlive){}
 record DynamicRow(String pair,Position position,ProgressionCombatContext context,String state,String direction,String scenario,int skillGap,ChampionMatchupIndependentScenario.GrowthPackage growth,double matchupEdge,double scoreBeforeMatchup,double scoreAfterMatchup,boolean overcome,boolean championPowerHardLock,boolean strongMatchupHardLock,boolean growthPackageEligible){}
 record DeadzoneRow(double threshold,double neutralizedPercent,long meaningfulProbabilityDeltaRemovedCount,long directionalityErrors,double strongestRemovedEdge){}
 record FullJob(GeneratedMatchupRoundRobinLineupFactory.Lineup lineup,SideOrientationFixture.Orientation orientation,int seed){}
 record PlayerMetrics(double kills,double deaths,double assists,double gold,double level,double pressure){}
 record FullRow(String lineupId,String skill,SideOrientationFixture.Orientation orientation,int seed,String mode,TeamSide winner,int duration,int blueKills,int redKills,int blueGold,int redGold,int objectives,int structures,int interactionApplications,double actualEdgeP50,double actualEdgeP90,double actualEdgeP95,int directRandomCalls,long randomDraws,String replayHash,String diagnosticsHash,boolean replayMismatch,boolean diagnosticsMismatch,Map<Position,PlayerMetrics>positions){FullRow{positions=Map.copyOf(positions);}}
 record PairRun(FullRow off,FullRow on){}
 record PairedRow(String lineupId,String skill,SideOrientationFixture.Orientation orientation,int seed,TeamSide offWinner,TeamSide candidateWinner,boolean winnerFlip,int durationDelta,int blueKillDelta,int redKillDelta,int blueGoldDelta,int redGoldDelta,int objectiveDelta,int structureDelta,boolean randomDrawDifference,boolean replayMismatch,boolean diagnosticsMismatch){}
 record FullAudit(List<FullRow>fullRows,List<PairedRow>pairedRows,int screeningGames,int escalationGames){static FullAudit empty(){return new FullAudit(List.of(),List.of(),0,0);}}
}
