package com.lolfm.simulator;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lolfm.champion.*;
import com.lolfm.domain.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Phase 13C-4.1 diagnostics-only global interaction-gain audit. */
public final class ChampionPairInteractionGainAudit {
    static final double[] GAINS = {1, 4, 8, 12};
    static final String FROZEN_PROFILE_HASH = "c8956937e8c9032654feb2bb17ff7ef66d68a964b4f1f6ed98853400f5b3dc64";
    static final Path OUTPUT = Path.of("build/reports/champion-pair-interaction-gain");
    static final ObjectMapper MAPPER = new ObjectMapper();

    private ChampionPairInteractionGainAudit() { }

    public static void main(String[] args) throws Exception {
        if (Files.exists(OUTPUT.resolve("champion-pair-interaction-gain-summary.csv"))) {
            System.out.println("Phase 13C-4.1 artifact already exists; duplicate execution skipped: " + OUTPUT.toAbsolutePath());
            return;
        }
        Files.createDirectories(OUTPUT);
        ChampionCatalog champions = new ChampionCatalog(MAPPER);
        var base = PairInteractionGeneratedCatalog.build(champions);
        List<MatrixRow> matrix = matrix(base);
        List<GainDistribution> distributions = distributions(matrix);
        Screening screening = screen(champions, base.catalog());
        double selected = select(distributions, screening);
        List<DynamicRow> dynamic = selected == 0 ? List.of() : dynamic(champions, base, selected);
        FullAudit full = selected == 0 ? FullAudit.empty() : full(champions, selected);
        List<RequiredGainRow> required = requiredRows(screening);
        List<LocalInfluenceRow> influence = influence(screening, full, selected);
        List<DeadzoneRow> deadzones = deadzones(matrix, screening, selected);
        LinkedHashMap<String,Object> summary = summary(distributions, screening, dynamic, full, deadzones, selected);
        ChampionMatchupRuleEngineCsv.records(path("champion-pair-interaction-gain-matrix.csv"), matrix);
        ChampionMatchupRuleEngineCsv.records(path("champion-pair-interaction-required-gain.csv"), required);
        ChampionMatchupRuleEngineCsv.records(path("champion-pair-interaction-local-influence.csv"), influence);
        writeRecordsOrHeader(path("champion-pair-interaction-selected-dynamic.csv"), dynamic, DynamicRow.class);
        writeRecordsOrHeader(path("champion-pair-interaction-gain-full-match.csv"), full.fullRows(), FullRow.class);
        writeRecordsOrHeader(path("champion-pair-interaction-gain-paired.csv"), full.pairedRows(), PairedRow.class);
        ChampionMatchupRuleEngineCsv.summary(path("champion-pair-interaction-gain-summary.csv"), summary);
        Files.writeString(path("champion-pair-interaction-gain-audit.log"), summary.entrySet().stream()
                .map(e -> e.getKey() + "=" + e.getValue()).collect(Collectors.joining("\n", "", "\n")));
        System.out.println("Champion pair-interaction gain audit: " + summary.get("verdict"));
        System.out.println("Report: " + OUTPUT.toAbsolutePath());
    }

    static List<MatrixRow> matrix(PairInteractionGeneratedCatalog.BuildResult base) {
        List<MatrixRow> rows = new ArrayList<>(2700);
        for (var row : base.rows()) for (double gain : GAINS) {
            double baseEdge = row.interactionEdge();
            double gained = clamp(baseEdge * gain);
            rows.add(new MatrixRow(row.pairId(), row.position(), row.context(), gain,
                    baseEdge, gained, Math.abs(gained), sign(gained),
                    Math.abs(baseEdge * gain) > .30, row.dominantRule(),
                    Math.abs(gained + clamp(row.reverseEdge() * gain)) < 1e-12));
        }
        if (rows.size() != 2700) throw new IllegalStateException("Expected 2,700 gain rows");
        return List.copyOf(rows);
    }

    static List<GainDistribution> distributions(List<MatrixRow> matrix) {
        List<GainDistribution> result = new ArrayList<>();
        for (double gain : GAINS) {
            List<MatrixRow> rows = matrix.stream().filter(r -> r.gain() == gain).toList();
            var stats = ThirtyChampionStatistics.summarize(rows.stream().map(MatrixRow::absoluteEdge).toList());
            Map<String,List<MatrixRow>> pairs = rows.stream().collect(Collectors.groupingBy(MatrixRow::pair));
            long same = pairs.values().stream().filter(v -> v.stream().mapToInt(r -> sign(r.gainedEdge())).filter(i -> i != 0).distinct().count() <= 1).count();
            long universal = dominance(rows, 9), broad = dominance(rows, 7);
            long ruleWarnings = rows.stream().filter(r -> r.absoluteEdge() >= .01).collect(Collectors.groupingBy(MatrixRow::dominantRule, Collectors.counting())).values().stream().filter(v -> v > rows.size() * .8).count();
            result.add(new GainDistribution(gain, stats.mean(), stats.p50(), quantile(rows,.75), stats.p90(), stats.p95(), stats.max(),
                    rows.stream().filter(r -> r.gainedEdge() == 0).count(), rows.stream().filter(r -> r.gainedEdge() != 0).count(),
                    rows.stream().filter(MatrixRow::clamped).count(), same, universal, broad, ruleWarnings,
                    stats.p50() < .0025, stats.p95() > .05 || stats.max() > .12 || rows.stream().anyMatch(MatrixRow::clamped)));
        }
        return List.copyOf(result);
    }

    private static long dominance(List<MatrixRow> rows, int minimumContexts) {
        return rows.stream().collect(Collectors.groupingBy(MatrixRow::pair)).values().stream().filter(v -> {
            long positive = v.stream().filter(r -> r.gainedEdge() > 0).count();
            long negative = v.stream().filter(r -> r.gainedEdge() < 0).count();
            return Math.max(positive, negative) >= minimumContexts && v.stream().mapToDouble(MatrixRow::absoluteEdge).average().orElse(0) > .03;
        }).count();
    }

    static Screening screen(ChampionCatalog champions, ChampionMatchupCatalog baseCatalog) {
        ChampionSelectionValidator selector = new ChampionSelectionValidator(champions);
        MatchSimulator simulator = simulator(champions, ChampionMatchupMode.OFF, baseCatalog);
        List<ScreenSample> samples = Collections.synchronizedList(new ArrayList<>());
        List<ScreenGame> games = new ArrayList<>(3000);
        for (String skill : List.of("S0", "S3")) for (var lineup : GeneratedMatchupRoundRobinLineupFactory.create(champions, skill))
            for (var orientation : SideOrientationFixture.Orientation.values()) for (int seed=1; seed<=50; seed++)
                games.add(new ScreenGame(lineup, orientation, seed));
        games.parallelStream().forEach(job -> {
            var oriented = job.lineup().fixture().orient(job.orientation());
            var assignments = selector.resolve(oriented.champions());
            var random = new SideOrientationRandomTraceObserver(job.seed(), job.orientation().name(), oriented.blueLogicalTeam().name(), oriented.redLogicalTeam().name(), false);
            var result = simulator.simulateWithSideDiagnostics(oriented.blue(), oriented.red(), assignments, random);
            Map<String, ChampionPowerCombatSample> actual = new LinkedHashMap<>();
            for (var sample : result.championPowerExecutionStats().samples()) {
                if (sample.applicationStage() != ProgressionApplicationStage.COMBAT_SCORE) continue;
                String key = sample.context()+"/"+sample.timeSeconds()+"/"+sample.ownSide();
                actual.putIfAbsent(key, sample);
            }
            for (var sample : actual.values()) {
                double edge = aggregateEdge(baseCatalog, assignments, sample.context(), sample.ownSide());
                double score = sample.existingScoreBeforeProgression() + sample.finalScoreContribution();
                double required = edge == 0 ? Double.NaN : Math.abs(score / edge);
                EnumMap<Position,Double> positionEdges = new EnumMap<>(Position.class);
                for (Position p : Position.values()) positionEdges.put(p, positionEdge(baseCatalog, assignments, p, sample.context(), sample.ownSide()));
                int[] flips = new int[GAINS.length];
                for (int i=0;i<GAINS.length;i++) flips[i] = orderingFlip(score, edge, GAINS[i]) ? 1 : 0;
                samples.add(new ScreenSample(job.lineup().lineupId(), job.lineup().skillProfile(), job.orientation(), job.seed(), sample.context(), sample.ownSide(),
                        sample.ownParticipantCount()+sample.enemyParticipantCount(), score, edge, required, flips, positionEdges));
            }
        });
        samples.sort(Comparator.comparing(ScreenSample::lineupId).thenComparing(ScreenSample::skill).thenComparing(s -> s.orientation().name()).thenComparingInt(ScreenSample::seed).thenComparing(s -> s.context().name()));
        return new Screening(3000, List.copyOf(samples), 0, false);
    }

    static double select(List<GainDistribution> distributions, Screening screening) {
        for (var d : distributions) {
            long flips = screening.samples().stream().mapToLong(s -> s.flips()[gainIndex(d.gain())]).sum();
            double rate = screening.samples().isEmpty() ? 0 : flips/(double)screening.samples().size();
            if (!d.tooWeak() && !d.tooStrong() && d.capHitCount()==0 && d.universalDominance()==0 && d.broadDominance()==0 && flips>0 && rate>=.0001 && rate<=.02) return d.gain();
        }
        return 0;
    }

    static List<DynamicRow> dynamic(ChampionCatalog champions, PairInteractionGeneratedCatalog.BuildResult base, double gain) {
        Map<Position,List<PairInteractionGeneratedCatalog.Row>> focused = base.rows().stream().collect(Collectors.groupingBy(PairInteractionGeneratedCatalog.Row::position));
        List<DynamicRow> rows = new ArrayList<>();
        var profiles = ThirtyChampionRoleProfiles.catalog();
        var formula = new CenteredPairInteractionFormula(new ChampionMatchupRuleCatalog());
        var score = new DynamicCombatScoreEvaluator(ChampionPowerProfileCatalog.loadDefault());
        var factory = new ChampionMatchupAuditPlayerFactory();
        for (Position position : Position.values()) {
            List<String> pairs = focused.get(position).stream().collect(Collectors.groupingBy(PairInteractionGeneratedCatalog.Row::pairId,
                    Collectors.maxBy(Comparator.comparingDouble(r -> Math.abs(r.interactionEdge()))))).values().stream().flatMap(Optional::stream)
                    .sorted(Comparator.comparingDouble((PairInteractionGeneratedCatalog.Row r)->Math.abs(r.interactionEdge())).reversed()).limit(2).map(PairInteractionGeneratedCatalog.Row::pairId).toList();
            for (String pair : pairs) {
                String[] ids=pair.split("/");
                for (ProgressionCombatContext context : contextsFor(base, pair)) for (var state : ChampionMatchupAuditPlayerFactory.AuditState.values())
                    for (boolean reverse : List.of(false,true)) for (DynScenario scenario : DynScenario.values()) {
                        ChampionId source=new ChampionId(reverse?ids[1]:ids[0]), opponent=new ChampionId(reverse?ids[0]:ids[1]);
                        double edge=Math.abs(clamp(formula.evaluate(profiles.find(new ChampionRoleKey(source,position)).orElseThrow(),profiles.find(new ChampionRoleKey(opponent,position)).orElseThrow(),context).finalEdge()*gain));
                        var favored=factory.create(position,state,0); var challenger=factory.create(position,state,scenario.skill);
                        var growth=factory.applyGrowth(challenger,scenario.growth);
                        double before=score.evaluate(favored.player(),source,context).finalCombatScore()-score.evaluate(challenger.player(),opponent,context).finalCombatScore();
                        double after=before+edge;
                        boolean overcome=after<=.01;
                        rows.add(new DynamicRow(pair,position,context,state.name(),reverse?"REVERSE":"FORWARD",scenario.name(),scenario.skill,scenario.growth,
                                edge,before,after,overcome,scenario==DynScenario.BASELINE && before<-.01 && after>.01,
                                scenario!=DynScenario.BASELINE && before<-.01 && after>.01,growth.eligibleForRequestedPackageRate()));
                    }
            }
        }
        if (rows.size()>2000) throw new IllegalStateException("Focused dynamic budget exceeded");
        return List.copyOf(rows);
    }

    private static List<ProgressionCombatContext> contextsFor(PairInteractionGeneratedCatalog.BuildResult base,String pair) {
        var max=base.rows().stream().filter(r->r.pairId().equals(pair)).max(Comparator.comparingDouble(r->Math.abs(r.interactionEdge()))).orElseThrow().context();
        return java.util.stream.Stream.of(max,ProgressionCombatContext.LANE_COMBAT,ProgressionCombatContext.TEAMFIGHT,ProgressionCombatContext.OBJECTIVE_FIGHT).distinct().toList();
    }

    static FullAudit full(ChampionCatalog champions,double gain) {
        GainExecutor executor=new GainExecutor(champions,gain);
        List<FullJob> jobs=new ArrayList<>(1200);
        for(String skill:List.of("S0","S3")) for(var lineup:GeneratedMatchupRoundRobinLineupFactory.create(champions,skill))
            for(var orientation:SideOrientationFixture.Orientation.values()) for(int seed=1;seed<=20;seed++) jobs.add(new FullJob(lineup,orientation,seed));
        List<PairRun> pairs=jobs.parallelStream().map(executor::run).sorted(Comparator.comparing((PairRun p)->p.off().lineupId()).thenComparing(p->p.off().skill()).thenComparing(p->p.off().orientation().name()).thenComparingInt(p->p.off().seed())).toList();
        List<FullRow> full=new ArrayList<>(2400); List<PairedRow> paired=new ArrayList<>(1200);
        for(var p:pairs){full.add(p.off());full.add(p.on());paired.add(compare(p));}
        return new FullAudit(List.copyOf(full),List.copyOf(paired),1200,0);
    }

    static List<RequiredGainRow> requiredRows(Screening screening) {
        List<RequiredGainRow> rows=new ArrayList<>();
        for(Position p:Position.values()) for(ProgressionCombatContext c:ProgressionCombatContext.values()) for(double gain:GAINS){
            List<ScreenSample> s=screening.samples().stream().filter(v->v.context()==c && v.positionEdges().get(p)!=0).toList();
            List<Double> req=s.stream().map(ScreenSample::requiredGain).filter(Double::isFinite).toList();
            rows.add(new RequiredGainRow(p,c,gain,s.size(),req.size(),s.size()-req.size(),q(req,.10),q(req,.25),q(req,.50),q(req,.75),q(req,.90),min(req),max(req),
                    s.stream().filter(v->v.flips()[gainIndex(gain)]>0).count(),s.stream().filter(v->v.flips()[gainIndex(gain)]>0).count(),
                    s.isEmpty()?0:s.stream().filter(v->v.flips()[gainIndex(gain)]>0).count()/(double)s.size(),0));
        }
        return List.copyOf(rows);
    }

    static List<LocalInfluenceRow> influence(Screening screening, FullAudit full,double selected) {
        List<LocalInfluenceRow> rows=new ArrayList<>();
        for(double gain:GAINS) for(ProgressionCombatContext c:ProgressionCombatContext.values()) for(Position p:Position.values()){
            List<ScreenSample>s=screening.samples().stream().filter(v->v.context()==c&&v.positionEdges().get(p)!=0).toList();
            List<Double>edges=s.stream().map(v->Math.abs(clamp(v.positionEdges().get(p)*gain))).toList(); long flips=s.stream().filter(v->v.flips()[gainIndex(gain)]>0).count();
            PositionDelta d=gain==selected?positionDelta(full,p):PositionDelta.ZERO;
            rows.add(new LocalInfluenceRow(gain,c,p,s.size(),s.size(),mean(edges),q(edges,.5),q(edges,.9),q(edges,.95),flips,flips,s.isEmpty()?0:flips/(double)s.size(),d.kills,d.deaths,d.assists,d.gold,d.level,d.pressure,d.objectives,d.structures));
        }
        return List.copyOf(rows);
    }

    static List<DeadzoneRow> deadzones(List<MatrixRow> matrix,Screening screening,double selected){
        if(selected==0)return List.of(); List<Double> thresholds=List.of(0d,.001,.0025,.005,.010); List<MatrixRow> rows=matrix.stream().filter(r->r.gain()==selected).toList();
        return thresholds.stream().map(t->{long neutral=rows.stream().filter(r->r.absoluteEdge()<t).count();long removed=screening.samples().stream().filter(s->Math.abs(clamp(s.baseEdge()*selected))<t&&s.flips()[gainIndex(selected)]>0).count();double strongest=rows.stream().filter(r->r.absoluteEdge()<t).mapToDouble(MatrixRow::absoluteEdge).max().orElse(0);return new DeadzoneRow(t,neutral/(double)rows.size(),0,removed,strongest,t==0?"KEEP_NONE":"INFORMATIONAL_ONLY");}).toList();
    }

    static LinkedHashMap<String,Object> summary(List<GainDistribution>d,Screening s,List<DynamicRow>dynamic,FullAudit full,List<DeadzoneRow>dead,double selected)throws Exception{
        LinkedHashMap<String,Object>v=new LinkedHashMap<>();
        v.put("auditVersion","phase-13c-4.1-interaction-gain-v1");v.put("frozenProfileVersion",ThirtyChampionRoleProfiles.VERSION);v.put("frozenProfileHash",FROZEN_PROFILE_HASH);
        v.put("ruleVersion",ChampionMatchupRuleCatalog.VERSION);v.put("formulaVersion",CenteredPairInteractionFormula.VERSION);v.put("profileChangeCount",0);v.put("ruleWeightChangeCount",0);v.put("formulaChangeCount",0);
        v.put("productionModeDefault","OFF");v.put("productionNonZeroEdgeCount",0);v.put("productionOverrideCount",0);v.put("productionDeadzone","NONE");v.put("productionGain","NONE");v.put("candidateApiFrontendExposureCount",0);
        for(var x:d){String p="gain"+(int)x.gain();v.put(p+"MeanAbsolute",x.meanAbsolute());v.put(p+"P50",x.p50());v.put(p+"P75",x.p75());v.put(p+"P90",x.p90());v.put(p+"P95",x.p95());v.put(p+"Max",x.max());v.put(p+"CapHits",x.capHitCount());v.put(p+"OutcomeFlips",s.samples().stream().mapToLong(z->z.flips()[gainIndex(x.gain())]).sum());}
        List<Double> req=s.samples().stream().map(ScreenSample::requiredGain).filter(Double::isFinite).toList();
        v.put("screeningGames",s.games());v.put("actualAttemptCount",s.samples().size());v.put("usableAttemptCount",req.size());v.put("zeroBaseEdgeAttemptCount",s.samples().size()-req.size());v.put("requiredGainP10",q(req,.1));v.put("requiredGainP25",q(req,.25));v.put("requiredGainP50",q(req,.5));v.put("requiredGainP75",q(req,.75));v.put("requiredGainP90",q(req,.9));v.put("requiredGainMin",min(req));v.put("requiredGainMax",max(req));
        v.put("selectedGain",selected==0?"NONE":selected);v.put("selectedDynamicRows",dynamic.size());
        long hard=dynamic.stream().filter(DynamicRow::strongMatchupHardLock).count();v.put("strongMatchupHardLockCount",hard);v.put("championPowerHardLockCount",dynamic.stream().filter(DynamicRow::championPowerHardLock).count());
        v.put("skillPlus3OvercomeRate",overcome(dynamic,DynScenario.SKILL_PLUS_3));v.put("skillPlus5OvercomeRate",overcome(dynamic,DynScenario.SKILL_PLUS_5));v.put("combinedLargeOvercomeRate",overcome(dynamic,DynScenario.COMBINED_LEAD_LARGE));
        v.put("baseFullMatchGames",full.fullRows().size());v.put("pairedGames",full.pairedRows().size());v.put("escalationGames",full.escalationGames());v.put("winnerFlipCount",full.pairedRows().stream().filter(PairedRow::winnerFlip).count());v.put("winnerFlipRate",full.pairedRows().isEmpty()?0:full.pairedRows().stream().filter(PairedRow::winnerFlip).count()/(double)full.pairedRows().size());
        v.put("randomDrawDifferenceCount",full.pairedRows().stream().filter(PairedRow::randomDrawDifference).count());v.put("directRandomCallCount",full.fullRows().stream().mapToInt(FullRow::directRandomCalls).sum());v.put("replayMismatchCount",full.fullRows().stream().filter(FullRow::replayMismatch).count());v.put("diagnosticsMismatchCount",full.fullRows().stream().filter(FullRow::diagnosticsMismatch).count());
        v.put("deadzoneRecommendation",dead.stream().filter(x->x.threshold()==0).map(DeadzoneRow::recommendation).findFirst().orElse("NONE"));
        v.put("phase125SummaryHash","af014896733d568974c91043c24d07917239808e3fcb9277bfba55480974da04");v.put("phase125CombatHash","f18ab7781284d23a9369a1f8a1ee4ba5df156706727dc588ce42114d90ddc735");v.put("phase125PositionHash","464f895021398f6ffa25cfebabc08d0483e3428018321f127f45d82f8725ec5c");v.put("phase13c4Verdict","REVIEW_PAIR_INTERACTION_FORMULA");v.put("phase13c4IntegrityErrorCount",0);v.put("phase13c4ProductionActivationAllowed",false);
        List<String>w=new ArrayList<>();if(selected==0)w.add("MATCHUP_EFFECT_STILL_TOO_WEAK");if(d.stream().anyMatch(GainDistribution::tooWeak))w.add("GAIN_DISTRIBUTION_TOO_WEAK");if(d.stream().anyMatch(GainDistribution::tooStrong))w.add("GAIN_DISTRIBUTION_TOO_STRONG");double flipRate=selected==0?0:s.samples().stream().filter(z->z.flips()[gainIndex(selected)]>0).count()/(double)Math.max(1,s.samples().size());if(flipRate>0.05||(full.pairedRows().size()>0&&full.pairedRows().stream().filter(PairedRow::winnerFlip).count()/(double)full.pairedRows().size()>.02)||hard>0)w.add("MATCHUP_EFFECT_TOO_STRONG");
        int integrity=(d.size()==4?0:1)+(s.games()<=3000?0:1)+(full.fullRows().size()<=4800?0:1)+(full.fullRows().stream().anyMatch(r->r.directRandomCalls()!=0)?1:0)+(full.fullRows().stream().anyMatch(r->r.replayMismatch()||r.diagnosticsMismatch())?1:0);
        v.put("warningCodes",String.join("|",w));v.put("integrityErrorCount",integrity);String verdict=integrity>0?"BLOCKED_BY_INTERACTION_GAIN_INTEGRITY":selected==0||!w.isEmpty()&&w.stream().anyMatch(x->!x.equals("GAIN_DISTRIBUTION_TOO_WEAK"))?"REVIEW_INTERACTION_GAIN":"READY_FOR_PHASE_13C5";v.put("verdict",verdict);v.put("phase13c5Allowed",verdict.equals("READY_FOR_PHASE_13C5"));v.put("productionActivationAllowed",false);return v;
    }

    static double overcome(List<DynamicRow>rows,DynScenario scenario){var x=rows.stream().filter(r->r.scenario().equals(scenario.name())).toList();return x.isEmpty()?0:x.stream().filter(DynamicRow::overcome).count()/(double)x.size();}
    static MatchSimulator simulator(ChampionCatalog champions,ChampionMatchupMode mode,ChampionMatchupCatalog catalog){return new MatchSimulator(new TeamfightResolver(),new EndGameEvaluator(),new SnapshotFactory(champions),new ObjectiveResolver(),new PostFightResolver(),new ObjectiveAttemptResolver(),new StructureResolver(),new PushResolver(),SimulationOptions.productionDefaults().withDiagnosticsEnabled(true).withChampionMatchupMode(mode),catalog);}
    static double aggregateEdge(ChampionMatchupCatalog c,MatchChampionAssignments a,ProgressionCombatContext context,TeamSide side){return Arrays.stream(Position.values()).mapToDouble(p->positionEdge(c,a,p,context,side)).average().orElse(0);}
    static double positionEdge(ChampionMatchupCatalog c,MatchChampionAssignments a,Position p,ProgressionCombatContext context,TeamSide side){var own=a.get(new PlayerKey(side,p)).championId();var enemy=a.get(new PlayerKey(side.opposite(),p)).championId();return c.contribution(own,enemy,p,context);}
    static boolean orderingFlip(double score,double edge,double gain){return sign(score)!=sign(score+clamp(edge*gain))&&sign(score)!=0&&sign(score+clamp(edge*gain))!=0;}
    static int sign(double v){return Math.abs(v)<1e-12?0:v>0?1:-1;}static double clamp(double v){double x=Math.max(-.30,Math.min(.30,v));return x==0?0:x;}
    static int gainIndex(double gain){for(int i=0;i<GAINS.length;i++)if(GAINS[i]==gain)return i;throw new IllegalArgumentException("gain");}
    static double quantile(List<MatrixRow>r,double q){return ThirtyChampionStatistics.quantile(r.stream().map(MatrixRow::absoluteEdge).toList(),q);}static double q(List<Double>x,double q){return x.isEmpty()?0:ThirtyChampionStatistics.quantile(x,q);}static double min(List<Double>x){return x.stream().mapToDouble(Double::doubleValue).min().orElse(0);}static double max(List<Double>x){return x.stream().mapToDouble(Double::doubleValue).max().orElse(0);}static double mean(List<Double>x){return x.stream().mapToDouble(Double::doubleValue).average().orElse(0);}
    static void writeRecordsOrHeader(Path path, List<? extends Record> rows, Class<? extends Record> type) throws Exception { if (rows.isEmpty()) ChampionMatchupRuleEngineCsv.headerOnly(path, Arrays.stream(type.getRecordComponents()).map(java.lang.reflect.RecordComponent::getName).toArray(String[]::new)); else ChampionMatchupRuleEngineCsv.records(path, rows); }
    static Path path(String name){return OUTPUT.resolve(name);}
    static String hash(Object v){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(MAPPER.writeValueAsBytes(v)));}catch(Exception e){throw new IllegalStateException(e);}}

    static PairedRow compare(PairRun p){FullRow a=p.off(),b=p.on();return new PairedRow(a.lineupId(),a.skill(),a.orientation(),a.seed(),a.winner(),b.winner(),a.winner()!=b.winner(),b.duration()-a.duration(),b.blueKills()-a.blueKills(),b.redKills()-a.redKills(),b.blueGold()-a.blueGold(),b.redGold()-a.redGold(),b.objectives()-a.objectives(),b.structures()-a.structures(),a.randomDraws()!=b.randomDraws(),a.replayMismatch()||b.replayMismatch(),a.diagnosticsMismatch()||b.diagnosticsMismatch());}
    static PositionDelta positionDelta(FullAudit audit,Position p){if(audit.pairedRows().isEmpty())return PositionDelta.ZERO;double k=0,d=0,a=0,g=0,l=0,pr=0;int n=0;for(int i=0;i<audit.fullRows().size();i+=2){FullRow off=audit.fullRows().get(i),on=audit.fullRows().get(i+1);PlayerMetrics x=off.positions().get(p),y=on.positions().get(p);k+=y.kills-x.kills;d+=y.deaths-x.deaths;a+=y.assists-x.assists;g+=y.gold-x.gold;l+=y.level-x.level;pr+=y.pressure-x.pressure;n++;}return new PositionDelta(k/n,d/n,a/n,g/n,l/n,pr/n,audit.pairedRows().stream().mapToInt(PairedRow::objectiveDelta).average().orElse(0),audit.pairedRows().stream().mapToInt(PairedRow::structureDelta).average().orElse(0));}

    static final class GainExecutor {final ChampionCatalog champions;final ChampionSelectionValidator selector;final ThreadLocal<MatchSimulator>off,on;GainExecutor(ChampionCatalog c,double gain){champions=c;selector=new ChampionSelectionValidator(c);off=ThreadLocal.withInitial(()->simulator(c,ChampionMatchupMode.OFF,ChampionMatchupCatalog.neutral(c)));on=ThreadLocal.withInitial(()->simulator(c,ChampionMatchupMode.ON,PairInteractionGeneratedCatalog.build(c,gain).catalog()));}PairRun run(FullJob j){return new PairRun(one(j,false),one(j,true));}FullRow one(FullJob j,boolean enabled){var o=j.lineup().fixture().orient(j.orientation());var as=selector.resolve(o.champions());var random=new SideOrientationRandomTraceObserver(j.seed(),j.orientation().name(),o.blueLogicalTeam().name(),o.redLogicalTeam().name(),false);var result=(enabled?on.get():off.get()).simulateWithSideDiagnostics(o.blue(),o.red(),as,random);var snap=result.timeline().getSnapshots().getLast();EnumMap<Position,PlayerMetrics>pm=new EnumMap<>(Position.class);for(Position p:Position.values()){var players=snap.getPlayerSnapshots().stream().filter(x->x.getPosition()==p).toList();double pressure=pressure(snap,p);pm.put(p,new PlayerMetrics(players.stream().mapToInt(PlayerSnapshot::getKills).sum(),players.stream().mapToInt(PlayerSnapshot::getDeaths).sum(),players.stream().mapToInt(PlayerSnapshot::getAssists).sum(),players.stream().mapToInt(PlayerSnapshot::getGold).sum(),players.stream().mapToInt(PlayerSnapshot::getLevel).average().orElse(0),pressure));}var stats=result.championMatchupExecutionStats();boolean diag=stats.missingAssignmentErrors()!=0||stats.deadParticipantErrors()!=0||stats.nonParticipantErrors()!=0||stats.sameTeamPairErrors()!=0||stats.crossPositionErrors()!=0||stats.duplicateApplicationErrors()!=0||stats.staleStateErrors()!=0;return new FullRow(j.lineup().lineupId(),j.lineup().skillProfile(),j.orientation(),j.seed(),enabled?"SELECTED_GAIN_CANDIDATE":"MATCHUP_OFF",result.winnerSide(),result.timeline().getDurationSeconds(),snap.getBlueKills(),snap.getRedKills(),snap.getBlueGold(),snap.getRedGold(),snap.getBlueDragons()+snap.getRedDragons(),snap.getBlueTowersDestroyed()+snap.getRedTowersDestroyed(),stats.totalPairApplications(),stats.applicationEdges().stream().mapToDouble(Math::abs).average().orElse(0),stats.directRandomCalls(),result.randomDrawCount(),hash(result.timeline().getEvents()),hash(result.timeline().getSnapshots()),false,diag,Map.copyOf(pm));}double pressure(MatchSnapshot s,Position p){Lane lane=switch(p){case TOP->Lane.TOP;case MID->Lane.MID;case ADC,SUPPORT->Lane.BOT;case JUNGLE->null;};return lane==null?0:s.getLaneSnapshots().stream().filter(x->x.lane()==lane).mapToDouble(com.lolfm.domain.LaneSnapshot::pressure).findFirst().orElse(0);}}

    enum DynScenario{BASELINE(0,ChampionMatchupIndependentScenario.GrowthPackage.NONE),SKILL_PLUS_1(1,ChampionMatchupIndependentScenario.GrowthPackage.NONE),SKILL_PLUS_3(3,ChampionMatchupIndependentScenario.GrowthPackage.NONE),SKILL_PLUS_5(5,ChampionMatchupIndependentScenario.GrowthPackage.NONE),COMBINED_LEAD_SMALL(0,ChampionMatchupIndependentScenario.GrowthPackage.COMBINED_LEAD_SMALL),COMBINED_LEAD_LARGE(0,ChampionMatchupIndependentScenario.GrowthPackage.COMBINED_LEAD_LARGE);final int skill;final ChampionMatchupIndependentScenario.GrowthPackage growth;DynScenario(int s,ChampionMatchupIndependentScenario.GrowthPackage g){skill=s;growth=g;}}
    record MatrixRow(String pair,Position position,ProgressionCombatContext context,double gain,double baseEdge,double gainedEdge,double absoluteEdge,int sign,boolean clamped,ChampionMatchupRuleType dominantRule,boolean directionalityValid){}
    record GainDistribution(double gain,double meanAbsolute,double p50,double p75,double p90,double p95,double max,long exactZeroCount,long nonZeroCount,long capHitCount,long allSameSignPairCount,long universalDominance,long broadDominance,long ruleDominanceWarningCount,boolean tooWeak,boolean tooStrong){}
    record ScreenGame(GeneratedMatchupRoundRobinLineupFactory.Lineup lineup,SideOrientationFixture.Orientation orientation,int seed){}
    record ScreenSample(String lineupId,String skill,SideOrientationFixture.Orientation orientation,int seed,ProgressionCombatContext context,TeamSide logicalDirection,int participantCount,double scoreWithoutMatchup,double baseEdge,double requiredGain,int[] flips,Map<Position,Double>positionEdges){ScreenSample{flips=Arrays.copyOf(flips,flips.length);positionEdges=Map.copyOf(positionEdges);}}
    record Screening(int games,List<ScreenSample>samples,int additionalRandomDraws,boolean gameplayMutated){}
    record RequiredGainRow(Position position,ProgressionCombatContext context,double gain,long actualAttemptCount,long usableAttemptCount,long zeroBaseEdgeAttemptCount,double requiredGainP10,double requiredGainP25,double requiredGainP50,double requiredGainP75,double requiredGainP90,double requiredGainMin,double requiredGainMax,long deterministicScoreOrderingFlip,long counterfactualOutcomeFlip,double outcomeFlipRate,int additionalRandomDraws){}
    record LocalInfluenceRow(double gain,ProgressionCombatContext context,Position position,long actualAttemptCount,long interactionApplicationCount,double meanAbsoluteEdge,double p50Edge,double p90Edge,double p95Edge,long scoreOrderingFlipCount,long counterfactualOutcomeFlipCount,double counterfactualOutcomeFlipRate,double targetPositionKillDelta,double targetPositionDeathDelta,double targetPositionAssistDelta,double targetPositionGoldDelta,double targetPositionLevelDelta,double targetPositionPressureDelta,double objectiveCaptureDelta,double structureDelta){}
    record DynamicRow(String pair,Position position,ProgressionCombatContext context,String state,String direction,String scenario,int skillGap,ChampionMatchupIndependentScenario.GrowthPackage growth,double matchupEdge,double scoreBeforeMatchup,double scoreAfterMatchup,boolean overcome,boolean championPowerHardLock,boolean strongMatchupHardLock,boolean growthPackageEligible){}
    record DeadzoneRow(double threshold,double neutralizedPercent,long directionalityError,long meaningfulLocalFlipRemovedCount,double strongestRemovedEdge,String recommendation){}
    record FullJob(GeneratedMatchupRoundRobinLineupFactory.Lineup lineup,SideOrientationFixture.Orientation orientation,int seed){}
    record PlayerMetrics(double kills,double deaths,double assists,double gold,double level,double pressure){}
    record FullRow(String lineupId,String skill,SideOrientationFixture.Orientation orientation,int seed,String mode,TeamSide winner,int duration,int blueKills,int redKills,int blueGold,int redGold,int objectives,int structures,int interactionApplications,double meanAbsoluteEdge,int directRandomCalls,long randomDraws,String replayHash,String diagnosticsHash,boolean replayMismatch,boolean diagnosticsMismatch,Map<Position,PlayerMetrics>positions){FullRow{positions=Map.copyOf(positions);}}
    record PairRun(FullRow off,FullRow on){}
    record PairedRow(String lineupId,String skill,SideOrientationFixture.Orientation orientation,int seed,TeamSide offWinner,TeamSide selectedWinner,boolean winnerFlip,int durationDelta,int blueKillDelta,int redKillDelta,int blueGoldDelta,int redGoldDelta,int objectiveDelta,int structureDelta,boolean randomDrawDifference,boolean replayMismatch,boolean diagnosticsMismatch){}
    record FullAudit(List<FullRow>fullRows,List<PairedRow>pairedRows,int screeningPairs,int escalationGames){static FullAudit empty(){return new FullAudit(List.of(),List.of(),0,0);}}
    record PositionDelta(double kills,double deaths,double assists,double gold,double level,double pressure,double objectives,double structures){static final PositionDelta ZERO=new PositionDelta(0,0,0,0,0,0,0,0);}
}
