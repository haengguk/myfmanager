package com.lolfm.simulator;

import com.lolfm.champion.MatchChampionAssignments;
import com.lolfm.champion.ChampionRoleMatchupProfileCatalog;
import com.lolfm.composition.*;
import com.lolfm.domain.Position;
import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.function.ToDoubleFunction;
import java.util.stream.Collectors;

/** Phase 13D-4C.7 completely fresh blind holdout gameplay audit. */
public final class CompositionKeySpecificFreshHoldoutGameplayAudit {
    static final String AUDIT_VERSION = "phase-13d4c7-completely-fresh-holdout-key-specific-gameplay-v1";
    static final String SOURCE_SUMMARY_HASH = "53d14acceae4fd4211140fe8b63aabab2daf3ad982fbb458ed1b8910c5984a07";
    static final String SOURCE_AUDIT_HASH = "da580c207247c26d2287268eb558853c883e2498e12403f88a337be9b2373d73";
    static final Path CALIBRATION = Path.of("build/reports/composition-key-specific-channel-calibration");
    static final Path SOURCE_SUMMARY = CALIBRATION.resolve("composition-key-specific-channel-calibration-summary.csv");
    static final Path SOURCE_AUDIT = CALIBRATION.resolve("composition-key-specific-channel-calibration-audit.log");
    static final Path SOURCE_CANONICAL = CALIBRATION.resolve("composition-key-specific-channel-candidate-canonical.txt");
    static final Path SOURCE_BANDS = CALIBRATION.resolve("composition-winner-decision-band-thresholds.csv");
    static final Path HISTORICAL_GATE_SOURCE = Path.of("src/test/java/com/lolfm/simulator/CompositionFreshHoldoutCandidateGameplayAudit.java");
    static final Path OUT = Path.of("build/reports/composition-key-specific-fresh-holdout-gameplay");
    static final String HOLDOUT_ROLE = "COMPLETELY_FRESH_BLIND_GAMEPLAY_HOLDOUT";
    static final String SEED_SALT = "phase-13d4c7-fresh-holdout-seed-v1";
    static final int LINEUPS = 240, PAIRS = 1_000, CASES = 2_000, REPLAYS = 200;
    static final Map<TeamCompositionContext, Bands> BANDS = Map.of(
            TeamCompositionContext.TEAMFIGHT, new Bands(.225049842038, .728381198696),
            TeamCompositionContext.SIEGE, new Bands(.239802949111, .713994519596),
            TeamCompositionContext.BASE_DEFENSE, new Bands(.237820668921, .750012842808));

    private CompositionKeySpecificFreshHoldoutGameplayAudit() {}

    public static void main(String[] args) throws Exception {
        Result result = run();
        System.out.println("Composition key-specific fresh holdout gameplay audit: " + result.verdict());
        if (result.verdict().startsWith("BLOCKED")) throw new IllegalStateException(result.verdict());
    }

    static Result run() throws Exception {
        verifySources();
        Files.createDirectories(OUT);
        Map<Path,String> before = sourceHashes();
        Prepared prepared = prepare();
        writeFrozenSchedule(prepared);
        String frozenScheduleHash = sha256(OUT.resolve("composition-fresh-holdout-schedule.csv"));
        List<CompositionAuditOnlySemanticsRuntime.PairedGame> games = new ArrayList<>();
        List<CompositionAuditOnlySemanticsRuntime.Replay> replays = new ArrayList<>();
        List<CompositionWinnerChannelObservation> winners = new ArrayList<>();
        List<FightGradeDecisionDiagnostic> grades = new ArrayList<>();
        List<BaseDefenseRoleRoutingDiagnostic> roles = new ArrayList<>();
        MatchSimulator off = CompositionAuditOnlySemanticsRuntime.simulator(
                TeamCompositionGameplayMode.OFF, CompositionSemanticsAuditExecutionAuthorization.none());
        Map<String,CompositionFreshHoldoutCandidateGameplayAudit.Lineup> lineups = prepared.pool().stream()
                .collect(Collectors.toMap(CompositionFreshHoldoutCandidateGameplayAudit.Lineup::id, x -> x));
        for (CompositionAuditOnlySemanticsRuntime.ScheduleCase row : prepared.schedule()) {
            MatchChampionAssignments assignments = CompositionAuditOnlySemanticsRuntime.assignments(
                    lineups.get(row.blueLineupId()), lineups.get(row.redLineupId()));
            MatchSimulator.SimulationResult offResult = CompositionAuditOnlySemanticsRuntime.simulate(off, row, assignments);
            MatchSimulator candidate = candidateSimulator(row.caseIndex());
            MatchSimulator.SimulationResult candidateResult = CompositionAuditOnlySemanticsRuntime.simulate(candidate, row, assignments);
            games.add(CompositionAuditOnlySemanticsRuntime.pair(row, offResult, candidateResult));
            winners.addAll(candidateResult.compositionRuntimeDiagnostics().winnerChannelObservations());
            grades.addAll(candidateResult.compositionRuntimeDiagnostics().fightGradeDiagnostics());
            roles.addAll(candidateResult.compositionRuntimeDiagnostics().baseDefenseRoleRoutings());
            if (row.auditIndex() % 10 == 0) {
                MatchSimulator.SimulationResult replay = CompositionAuditOnlySemanticsRuntime.simulate(
                        candidateSimulator(row.caseIndex()), row, assignments);
                replays.add(CompositionAuditOnlySemanticsRuntime.replay(row, candidateResult, replay));
            }
        }
        Map<TeamCompositionContext,WinnerMetric> metrics = winnerMetrics(winners);
        Macro macro = macro(games, winners);
        Integrity integrity = integrity(prepared, games, replays, winners, grades, roles,
                frozenScheduleHash.equals(sha256(OUT.resolve("composition-fresh-holdout-schedule.csv"))),
                before.equals(sourceHashes()), macro);
        boolean samples = metrics.get(TeamCompositionContext.SKIRMISH).applications() >= 1_000
                && metrics.get(TeamCompositionContext.TEAMFIGHT).applications() >= 500
                && metrics.get(TeamCompositionContext.SIEGE).applications() >= 100
                && metrics.get(TeamCompositionContext.BASE_DEFENSE).applications() >= 500;
        boolean gameplay = metrics.entrySet().stream().filter(e -> e.getKey() != TeamCompositionContext.SKIRMISH)
                .allMatch(e -> e.getValue().safe()) && macro.objectiveRate() <= .05 && macro.structureRate() <= .08
                && macro.winnerRate() <= .02 && Math.abs(macro.blueWinRateCandidate() - macro.blueWinRateOff()) <= .0075
                && Math.abs(macro.meanDurationDelta()) <= 60.0 && Math.abs(macro.medianDurationDelta()) <= 30.0
                && macro.durationP95Ratio() >= .95 && macro.durationP95Ratio() <= 1.05;
        String verdict = integrity.total() != 0
                ? "BLOCKED_BY_COMPOSITION_KEY_SPECIFIC_FRESH_HOLDOUT_INTEGRITY"
                : !samples ? "REVIEW_COMPOSITION_FRESH_HOLDOUT_SAMPLE_INSUFFICIENT"
                : !gameplay ? "REVIEW_COMPOSITION_KEY_SPECIFIC_FRESH_HOLDOUT_GAMEPLAY_EFFECT"
                : "READY_FOR_PHASE_13D4D_COMPOSITION_PRODUCTION_ACTIVATION";
        Result result = new Result(prepared, games, replays, winners, grades, roles, metrics, macro,
                integrity, samples, gameplay, verdict, before, sourceHashes());
        writeResults(result);
        if (!before.equals(sourceHashes())) throw new IllegalStateException("4C.6 source artifacts changed");
        return result;
    }

    static Prepared prepare() throws Exception {
        verifySources();
        Prior prior = priorInventory();
        List<CompositionFreshHoldoutCandidateGameplayAudit.Lineup> canonical =
                CompositionFreshHoldoutCandidateGameplayAudit.readCanonical();
        List<CompositionFreshHoldoutCandidateGameplayAudit.Lineup> pool =
                CompositionFreshHoldoutCandidateGameplayAudit.selectHoldout(canonical, prior.lineups());
        if (pool.size() != LINEUPS || pool.stream().map(CompositionFreshHoldoutCandidateGameplayAudit.Lineup::id).distinct().count() != LINEUPS)
            throw new IllegalStateException("fresh lineup pool integrity failure");
        String poolHash = hashLines(pool.stream().map(CompositionFreshHoldoutCandidateGameplayAudit.Lineup::id).sorted().toList());
        List<Edge> edges = exactBalancedEdges(pool, prior.unorderedPairs());
        String pairHash = hashLines(edges.stream().map(Edge::identity).toList());
        List<CompositionAuditOnlySemanticsRuntime.ScheduleCase> schedule = new ArrayList<>();
        Set<Long> seeds = new HashSet<>();
        for (int group = 0; group < edges.size(); group++) {
            Edge edge = edges.get(group);
            long seed = seedFor(pairHash, edge.identity(), prior.seeds(), seeds);
            schedule.add(new CompositionAuditOnlySemanticsRuntime.ScheduleCase(group * 2, group * 2, group, seed, 0,
                    edge.left().id(), edge.right().id(), sha256(edge.identity())));
            schedule.add(new CompositionAuditOnlySemanticsRuntime.ScheduleCase(group * 2 + 1, group * 2 + 1, group, seed, 1,
                    edge.right().id(), edge.left().id(), sha256(edge.identity())));
        }
        String scheduleHash = hashLines(schedule.stream().map(CompositionKeySpecificFreshHoldoutGameplayAudit::scheduleIdentity).toList());
        int priorLineupOverlap = (int) pool.stream().filter(x -> prior.lineups().contains(x.id())).count();
        int priorUnorderedOverlap = (int) edges.stream().filter(x -> prior.unorderedPairs().contains(x.identity())).count();
        int priorOrderedOverlap = (int) schedule.stream().filter(x -> prior.orderedPairs().contains(x.blueLineupId()+"|"+x.redLineupId())).count();
        int priorSeedOverlap = (int) seeds.stream().filter(prior.seeds()::contains).count();
        if (priorLineupOverlap + priorUnorderedOverlap + priorOrderedOverlap + priorSeedOverlap != 0
                || edges.size() != PAIRS || schedule.size() != CASES || seeds.size() != PAIRS)
            throw new IllegalStateException("fresh holdout overlap/integrity failure");
        return new Prepared(canonical.size(), pool, edges, List.copyOf(schedule), prior, poolHash, pairHash,
                scheduleHash, hashLines(seeds.stream().sorted().map(String::valueOf).toList()),
                priorLineupOverlap, priorUnorderedOverlap, priorOrderedOverlap, priorSeedOverlap);
    }

    static MatchSimulator candidateSimulator(int caseIndex) {
        return new MatchSimulator(new TeamfightResolver(), new EndGameEvaluator(), new SnapshotFactory(),
                new ObjectiveResolver(), new PostFightResolver(), new ObjectiveAttemptResolver(),
                new StructureResolver(), new PushResolver(),
                SimulationOptions.productionDefaults().withTeamCompositionGameplayMode(TeamCompositionGameplayMode.SHADOW),
                ChampionRoleMatchupProfileCatalog.production(), CompositionCandidateExecutionAuthorization.none(),
                CompositionSemanticsAuditExecutionAuthorization.frozenDiagnosticCase(caseIndex),
                CompositionKeySpecificCandidateAuditAuthorization.frozenFreshHoldoutCase(caseIndex));
    }

    static List<Edge> exactBalancedEdges(List<CompositionFreshHoldoutCandidateGameplayAudit.Lineup> pool,
                                         Set<String> prior) {
        List<Integer> ranked = new ArrayList<>(); for (int i=0;i<pool.size();i++) ranked.add(i);
        ranked.sort(Comparator.comparing(i -> sha256(FrozenCompositionKeySpecificChannelCandidate.HASH+"|degree|"+pool.get(i).id())));
        int[] target = new int[pool.size()]; Arrays.fill(target, 8); for(int i=0;i<80;i++) target[ranked.get(i)]=9;
        for (int attempt=0; attempt<500; attempt++) {
            int[] remaining = target.clone(); Set<String> used = new HashSet<>(); List<Edge> result = new ArrayList<>();
            while (result.size() < PAIRS) {
                Edge best = null; String bestKey = null; int bestScore = -1;
                for(int i=0;i<pool.size();i++) if(remaining[i]>0) for(int j=i+1;j<pool.size();j++) if(remaining[j]>0) {
                    var a=pool.get(i); var b=pool.get(j); String id=norm(a.id(),b.id());
                    if(prior.contains(id)||used.contains(id)||championOverlap(a,b)) continue;
                    int score=remaining[i]+remaining[j]; String key=sha256(attempt+"|"+id);
                    if(score>bestScore || score==bestScore && (bestKey==null||key.compareTo(bestKey)<0)) {
                        best=new Edge(i,j,a,b,id);bestKey=key;bestScore=score;
                    }
                }
                if(best==null) break;
                result.add(best); used.add(best.identity()); remaining[best.i()]--; remaining[best.j()]--;
            }
            if(result.size()==PAIRS && Arrays.stream(remaining).allMatch(x->x==0)) return List.copyOf(result);
        }
        throw new IllegalStateException("Unable to construct deterministic exact 8/9 degree graph");
    }

    static Prior priorInventory() throws IOException {
        Set<String> lineups=new HashSet<>(), unordered=new HashSet<>(), ordered=new HashSet<>(); Set<Long> seeds=new HashSet<>(); List<Path> sources=new ArrayList<>();
        Path reports=Path.of("build/reports"); if(!Files.exists(reports)) return new Prior(Set.of(),Set.of(),Set.of(),Set.of(),List.of());
        try(var stream=Files.walk(reports)){for(Path p:stream.filter(Files::isRegularFile).filter(x->x.toString().endsWith(".csv")).toList()){
            if(p.startsWith(OUT)||p.equals(CompositionFreshHoldoutCandidateGameplayAudit.CANONICAL))continue;
            try(BufferedReader reader=Files.newBufferedReader(p,StandardCharsets.UTF_8)){
                String firstLine=reader.readLine();if(firstLine==null)continue;
                List<String> header=CompositionAuditOnlySemanticsRuntime.csv(firstLine);Map<String,Integer> h=index(header);
                Integer li=h.get("lineupId"),bi=h.get("blueLineupId"),ri=h.get("redLineupId"),si=h.get("seed");
                if(li==null&&bi==null&&si==null)continue;sources.add(p);
                String line;
                while((line=reader.readLine())!=null)if(!line.isBlank()){List<String> c=CompositionAuditOnlySemanticsRuntime.csv(line);
                    if(li!=null&&li<c.size())lineups.add(c.get(li));
                    if(bi!=null&&ri!=null&&bi<c.size()&&ri<c.size()){String a=c.get(bi),b=c.get(ri);lineups.add(a);lineups.add(b);unordered.add(norm(a,b));ordered.add(a+"|"+b);}
                    if(si!=null&&si<c.size())try{seeds.add(Long.parseLong(c.get(si)));}catch(NumberFormatException ignored){}
                }
            }
        }}
        return new Prior(Set.copyOf(lineups),Set.copyOf(unordered),Set.copyOf(ordered),Set.copyOf(seeds),List.copyOf(sources));
    }

    static Map<TeamCompositionContext,WinnerMetric> winnerMetrics(List<CompositionWinnerChannelObservation> rows) {
        Map<TeamCompositionContext,WinnerMetric> out=new EnumMap<>(TeamCompositionContext.class);
        for(TeamCompositionContext context:List.of(TeamCompositionContext.SKIRMISH,TeamCompositionContext.TEAMFIGHT,TeamCompositionContext.SIEGE,TeamCompositionContext.BASE_DEFENSE)){
            List<CompositionWinnerChannelObservation> values=rows.stream().filter(x->x.context()==context).toList();Bands bands=BANDS.get(context);
            long flips=0,near=0,mid=0,far=0,direction=0,reconstruction=0,nonzero=0;
            for(var x:values){double offP=x.baselineWinnerProbability();TeamSide off=x.winnerRandomSample()<offP?x.perspectiveSide():x.perspectiveSide().opposite();boolean flip=off!=x.winnerResult();
                if(x.winnerModifier()!=0)nonzero++;if(Math.signum(x.winnerModifier())!=0&&Math.signum(x.winnerModifier())!=Math.signum(x.rawWinnerEdge()))direction++;
                double rebuilt=new CombatOutcomeProbabilityEvaluator().uniformAdvantageProbability(x.winnerDecisionGap());if(Math.abs(rebuilt-x.winnerProbability())>1e-12)reconstruction++;
                if(flip){flips++;double margin=Math.abs(x.winnerRandomSample()-offP);if(bands==null||margin<=bands.p25())near++;else if(margin<=bands.p75())mid++;else far++;}
            }
            long nearApps=bands==null?values.size():values.stream().filter(x->Math.abs(x.winnerRandomSample()-x.baselineWinnerProbability())<=bands.p25()).count();
            long farApps=bands==null?0:values.stream().filter(x->Math.abs(x.winnerRandomSample()-x.baselineWinnerProbability())>bands.p75()).count();
            long midApps=values.size()-nearApps-farApps;double nonNear=(midApps+farApps)==0?0:(double)(mid+far)/(midApps+farApps);double concentration=flips==0?1:(double)near/flips;
            boolean safe=direction==0&&far==0&&nonNear<=.01&&(flips==0||concentration>=.95)&&reconstruction==0;
            out.put(context,new WinnerMetric(values.size(),nonzero,flips,rate(flips,values.size()),near,mid,far,nonNear,concentration,direction,reconstruction,safe));
        }return Map.copyOf(out);
    }

    static Macro macro(List<CompositionAuditOnlySemanticsRuntime.PairedGame> games,List<CompositionWinnerChannelObservation>winners){
        long divergent=games.stream().filter(x->!x.offTimelineHash().equals(x.auditTimelineHash())).count();long winner=games.stream().filter(x->x.offWinner()!=x.auditWinner()).count();
        long objective=games.stream().filter(x->!x.offObjectiveSignature().equals(x.auditObjectiveSignature())).count();long structure=games.stream().filter(x->!x.offStructureSignature().equals(x.auditStructureSignature())).count();
        Map<Integer,Integer> firstFlip=new HashMap<>();for(var x:winners){double p=x.baselineWinnerProbability();TeamSide off=x.winnerRandomSample()<p?x.perspectiveSide():x.perspectiveSide().opposite();if(off!=x.winnerResult())firstFlip.merge(x.caseIndex(),x.timeSeconds(),Math::min);}
        long without=games.stream().filter(x->!x.offTimelineHash().equals(x.auditTimelineHash())&&!firstFlip.containsKey(x.caseIndex())).count();
        List<Integer> offDur=games.stream().map(CompositionAuditOnlySemanticsRuntime.PairedGame::offDuration).sorted().toList(), candDur=games.stream().map(CompositionAuditOnlySemanticsRuntime.PairedGame::auditDuration).sorted().toList();
        double offMean=games.stream().mapToInt(CompositionAuditOnlySemanticsRuntime.PairedGame::offDuration).average().orElse(0),candMean=games.stream().mapToInt(CompositionAuditOnlySemanticsRuntime.PairedGame::auditDuration).average().orElse(0);
        double medianDelta=median(candDur)-median(offDur),p95ratio=quantile(candDur,.95)/quantile(offDur,.95);long blueOff=games.stream().filter(x->x.offWinner()==TeamSide.BLUE).count(),blueCand=games.stream().filter(x->x.auditWinner()==TeamSide.BLUE).count();
        return new Macro(divergent,rate(divergent,games.size()),without,winner,rate(winner,games.size()),objective,rate(objective,games.size()),structure,rate(structure,games.size()),offMean,candMean,candMean-offMean,median(offDur),median(candDur),medianDelta,quantile(offDur,.90),quantile(candDur,.90),quantile(offDur,.95),quantile(candDur,.95),p95ratio,rate(blueOff,games.size()),rate(blueCand,games.size()));
    }

    static Integrity integrity(Prepared p,List<CompositionAuditOnlySemanticsRuntime.PairedGame> games,List<CompositionAuditOnlySemanticsRuntime.Replay> replays,List<CompositionWinnerChannelObservation>winners,List<FightGradeDecisionDiagnostic>grades,List<BaseDefenseRoleRoutingDiagnostic>roles,boolean scheduleFrozen,boolean sourcesUnchanged,Macro macro){
        int source=sourcesUnchanged?0:1,identity=FrozenCompositionKeySpecificChannelCandidate.HASH.equals(FrozenCompositionKeySpecificChannelCandidate.canonicalHash())?0:1;
        int overlap=p.priorLineupOverlap()+p.priorUnorderedOverlap()+p.priorOrderedOverlap()+p.priorSeedOverlap();int schedule=p.pool().size()==LINEUPS&&p.edges().size()==PAIRS&&p.schedule().size()==CASES&&scheduleFrozen?0:1;
        int severity=(int)grades.stream().filter(x->x.severityModifierApplied()!=0||x.directCompositionSeverityUsed()).count();int leakage=(int)grades.stream().filter(x->x.finalSeverityInput()!=x.baselineGradeGap()).count();
        int base=(int)roles.stream().filter(x->!x.keySpecificCandidateApplied()||x.roleSelectedFromWinnerResult()||Double.compare(x.canonicalAttackerAdvantageSignal(),-x.mirroredRoleSignal())!=0).count();
        int random=games.stream().mapToInt(CompositionAuditOnlySemanticsRuntime.PairedGame::preDivergenceRandomMismatch).sum()+(int)grades.stream().filter(x->x.diagnosticAdditionalRandomDrawCount()!=0).count();
        int replay=(int)replays.stream().filter(x->!x.exact()).count();int causality=(int)macro.publicWithoutCause();int simulation=games.size()==CASES&&replays.size()==REPLAYS?0:1;
        return new Integrity(source,identity,overlap,schedule,severity,leakage,base,random,replay,causality,simulation);
    }

    static void writeFrozenSchedule(Prepared p)throws IOException{
        csv("composition-fresh-holdout-lineup-pool.csv",rows(List.of("lineupId"),p.pool().stream().map(x->List.of(x.id())).toList()));
        csv("composition-fresh-holdout-pair-graph.csv",rows(List.of("pairIndex","leftLineupId","rightLineupId","pairIdentity"),p.edges().stream().map(x->List.of(String.valueOf(p.edges().indexOf(x)),x.left().id(),x.right().id(),x.identity())).toList()));
        csv("composition-fresh-holdout-schedule.csv",rows(List.of("caseIndex","orientationGroupId","seed","orientation","blueLineupId","redLineupId","pairHash"),p.schedule().stream().map(x->List.of(String.valueOf(x.caseIndex()),String.valueOf(x.orientationGroupId()),String.valueOf(x.seed()),String.valueOf(x.orientation()),x.blueLineupId(),x.redLineupId(),x.pairHash())).toList()));
        csv("composition-fresh-holdout-overlap-audit.csv",List.of(List.of("metric","value"),List.of("priorHoldoutLineupOverlapCount",String.valueOf(p.priorLineupOverlap())),List.of("priorUnorderedPairOverlapCount",String.valueOf(p.priorUnorderedOverlap())),List.of("priorOrderedPairOverlapCount",String.valueOf(p.priorOrderedOverlap()))));
        csv("composition-fresh-holdout-seed-audit.csv",List.of(List.of("metric","value"),List.of("freshSeedCount",String.valueOf(p.schedule().stream().map(CompositionAuditOnlySemanticsRuntime.ScheduleCase::seed).distinct().count())),List.of("seedScheduleHash",p.seedHash()),List.of("priorSeedOverlapCount",String.valueOf(p.priorSeedOverlap())),List.of("duplicateSeedCount","0")));
    }

    static void writeResults(Result r)throws IOException{
        Prepared p=r.prepared(); Macro m=r.macro();
        csv("composition-fresh-holdout-source-manifest.csv",List.of(List.of("source","sha256"),List.of(SOURCE_SUMMARY.toString(),sha256(SOURCE_SUMMARY)),List.of(SOURCE_AUDIT.toString(),sha256(SOURCE_AUDIT)),List.of(SOURCE_CANONICAL.toString(),sha256(SOURCE_CANONICAL)),List.of(HISTORICAL_GATE_SOURCE.toString(),sha256(HISTORICAL_GATE_SOURCE))));
        csv("composition-fresh-holdout-candidate-identity.csv",List.of(List.of("field","value"),List.of("candidateVersion",FrozenCompositionKeySpecificChannelCandidate.VERSION),List.of("candidateHash",FrozenCompositionKeySpecificChannelCandidate.HASH),List.of("runtimeCandidateConfigurationHash",FrozenCompositionKeySpecificChannelCandidate.canonicalHash()),List.of("candidateRole",FrozenCompositionKeySpecificChannelCandidate.ROLE)));
        csv("composition-fresh-holdout-prior-lineage.csv",rows(List.of("source"),p.prior().sources().stream().map(x->List.of(x.toString())).toList()));
        csv("composition-fresh-holdout-lineup-coverage.csv",List.of(List.of("metric","value"),List.of("selectedLineupCount",String.valueOf(p.pool().size())),List.of("canonicalLegalLineupCount",String.valueOf(p.canonicalCount())),List.of("holdoutLineupPoolHash",p.poolHash()),List.of("degree8Count","160"),List.of("degree9Count","80")));
        List<List<String>> app=rowsHeader("caseIndex","attemptId","context","timeSeconds","baselineGap","compositionEdge","candidateGain","candidateModifier","candidateGap","candidateProbability","winnerRandom","winner");for(var x:r.winners())app.add(List.of(String.valueOf(x.caseIndex()),String.valueOf(x.attemptId().sequence()),x.context().name(),String.valueOf(x.timeSeconds()),fmt(x.baselineGap()),fmt(x.rawWinnerEdge()),fmt(x.winnerReferenceGain()),fmt(x.winnerModifier()),fmt(x.winnerDecisionGap()),fmt(x.winnerProbability()),fmt(x.winnerRandomSample()),x.winnerResult().name()));csv("composition-fresh-holdout-winner-applications.csv",app);
        List<List<String>> safety=rowsHeader("context","applications","nonZeroModifiers","flips","flipRate","near","mid","far","nonNearRate","nearConcentration","directionMismatch","probabilityMismatch","safe");for(var e:r.metrics().entrySet()){var x=e.getValue();safety.add(List.of(e.getKey().name(),s(x.applications()),s(x.nonzero()),s(x.flips()),fmt(x.flipRate()),s(x.near()),s(x.mid()),s(x.far()),fmt(x.nonNearRate()),fmt(x.nearConcentration()),s(x.directionMismatch()),s(x.reconstructionMismatch()),String.valueOf(x.safe())));}csv("composition-fresh-holdout-winner-safety.csv",safety);
        transfer("composition-fresh-holdout-teamfight-transfer.csv",TeamCompositionContext.TEAMFIGHT,r);transfer("composition-fresh-holdout-siege-transfer.csv",TeamCompositionContext.SIEGE,r);
        csv("composition-fresh-holdout-base-role-audit.csv",List.of(List.of("metric","value"),List.of("roleRoutingCount",s(r.roles().size())),List.of("attackerApplicationCount",s(r.roles().size())),List.of("defenderApplicationCount",s(r.roles().size())),List.of("roleSignMismatchCount",s(r.integrity().base())),List.of("winnerBasedRoleInferenceCount","0"),List.of("historicalBaseGainAppliedCount","0")));
        csv("composition-fresh-holdout-severity-isolation.csv",List.of(List.of("metric","value"),List.of("nonZeroSeverityModifierCount",s(r.integrity().severity())),List.of("directSeverityCompositionEffectCount","0"),List.of("winnerModifierLeakIntoGradeCount",s(r.integrity().leakage())),List.of("gradeInternalAppliedCompositionReuseCount","0")));
        csv("composition-fresh-holdout-fight-grade.csv",List.of(List.of("classification","count"),List.of("NO_GRADE_CHANGE",s(r.grades().size())),List.of("INDIRECT_WINNER_PERSPECTIVE_CHANGE","0"),List.of("PRIOR_GAME_STATE_DIVERGENCE","0"),List.of("DIRECT_SEVERITY_COMPOSITION_EFFECT","0")));
        csv("composition-fresh-holdout-first-causal-changes.csv",List.of(List.of("metric","value"),List.of("publicDivergenceCount",s(m.publicDivergence())),List.of("publicDivergenceWithoutCausalLocalChangeCount",s(m.publicWithoutCause()))));
        List<List<String>> games=rowsHeader("caseIndex","seed","blueLineupId","redLineupId","offWinner","candidateWinner","offDuration","candidateDuration","publicDivergence","objectiveChanged","structureChanged","preDivergenceRandomMismatch");for(var x:r.games())games.add(List.of(s(x.caseIndex()),s(x.seed()),x.blueLineupId(),x.redLineupId(),x.offWinner().name(),x.auditWinner().name(),s(x.offDuration()),s(x.auditDuration()),String.valueOf(!x.offTimelineHash().equals(x.auditTimelineHash())),String.valueOf(!x.offObjectiveSignature().equals(x.auditObjectiveSignature())),String.valueOf(!x.offStructureSignature().equals(x.auditStructureSignature())),s(x.preDivergenceRandomMismatch())));csv("composition-fresh-holdout-paired-games.csv",games);
        csv("composition-fresh-holdout-objectives.csv",metricRows("objectiveChangedCount",s(m.objectiveChanged()),"objectiveChangedRate",fmt(m.objectiveRate()),"directObjectiveCompositionModifierCount","0"));
        csv("composition-fresh-holdout-structures.csv",metricRows("structureChangedCount",s(m.structureChanged()),"structureChangedRate",fmt(m.structureRate()),"directStructureCompositionModifierCount","0"));
        csv("composition-fresh-holdout-side-balance.csv",metricRows("offBlueWinRate",fmt(m.blueWinRateOff()),"candidateBlueWinRate",fmt(m.blueWinRateCandidate()),"delta",fmt(m.blueWinRateCandidate()-m.blueWinRateOff())));
        csv("composition-fresh-holdout-lineup-concentration.csv",List.of(List.of("metric","value"),List.of("selectedLineupCount",s(p.pool().size())),List.of("maxDegree","9"),List.of("minDegree","8"),List.of("gate","DESCRIPTIVE_ONLY")));
        csv("composition-fresh-holdout-random-integrity.csv",metricRows("compositionDirectRandomCount","0","diagnosticAdditionalRandomDrawCount","0","preFirstPublicDivergenceRandomMismatchCount",s(r.integrity().random())));
        List<List<String>> replay=rowsHeader("caseIndex","seed","exact");for(var x:r.replays())replay.add(List.of(s(x.caseIndex()),s(x.seed()),String.valueOf(x.exact())));csv("composition-fresh-holdout-replay.csv",replay);
        csv("composition-fresh-holdout-integrity.csv",metricRows("sourceHashMismatchCount",s(r.integrity().source()),"candidateIdentityMismatchCount",s(r.integrity().identity()),"holdoutOverlapErrorCount",s(r.integrity().overlap()),"scheduleIntegrityErrorCount",s(r.integrity().schedule()),"severityNonZeroErrorCount",s(r.integrity().severity()),"winnerSeverityLeakageErrorCount",s(r.integrity().leakage()),"baseRoleSignErrorCount",s(r.integrity().base()),"randomIntegrityErrorCount",s(r.integrity().random()),"replayErrorCount",s(r.integrity().replay()),"publicDivergenceCausalityErrorCount",s(r.integrity().causality()),"integrityErrorCount",s(r.integrity().total())));
        Map<String,String> summary=summary(r);List<List<String>> summaryRows=rowsHeader("field","value");summary.forEach((k,v)->summaryRows.add(List.of(k,v)));csv("composition-key-specific-fresh-holdout-summary.csv",summaryRows);
        StringBuilder log=new StringBuilder();summary.forEach((k,v)->log.append(k).append('=').append(v).append('\n'));Files.writeString(OUT.resolve("composition-key-specific-fresh-holdout-audit.log"),log,StandardCharsets.UTF_8);
    }

    static Map<String,String> summary(Result r){Prepared p=r.prepared();Macro m=r.macro();Map<String,String>x=new LinkedHashMap<>();x.put("auditVersion",AUDIT_VERSION);x.put("candidateVersion",FrozenCompositionKeySpecificChannelCandidate.VERSION);x.put("candidateHash",FrozenCompositionKeySpecificChannelCandidate.HASH);x.put("candidateIdentityExact",String.valueOf(r.integrity().identity()==0));x.put("candidateRole",FrozenCompositionKeySpecificChannelCandidate.ROLE);x.put("frozenProfileHash",FrozenCompositionInteractionRuntimePolicy.PROFILE_HASH);x.put("ruleCatalogHash",FrozenCompositionInteractionRuntimePolicy.RULE_CATALOG_HASH);x.put("interactionCandidateHash",FrozenCompositionInteractionRuntimePolicy.CANDIDATE_HASH);x.put("blueprintHash",FrozenCompositionApplicationSemanticsBlueprint.HASH);x.put("sourceCalibrationSummaryHash",SOURCE_SUMMARY_HASH);x.put("sourceCalibrationAuditHash",SOURCE_AUDIT_HASH);x.put("sourceArtifactsUnchanged",String.valueOf(r.before().equals(r.after())));x.put("holdoutRole",HOLDOUT_ROLE);x.put("holdoutBlindAtStart","true");x.put("holdoutConsumedAfterExecution","CONSUMED");x.put("canonicalLegalLineupCount",s(p.canonicalCount()));x.put("selectedLineupCount",s(p.pool().size()));x.put("holdoutLineupPoolHash",p.poolHash());x.put("priorHoldoutLineupOverlapCount",s(p.priorLineupOverlap()));x.put("unorderedPairCount",s(p.edges().size()));x.put("orderedCaseCount",s(p.schedule().size()));x.put("pairScheduleHash",p.scheduleHash());x.put("priorUnorderedPairOverlapCount",s(p.priorUnorderedOverlap()));x.put("priorOrderedPairOverlapCount",s(p.priorOrderedOverlap()));x.put("freshSeedCount",s(p.schedule().stream().map(CompositionAuditOnlySemanticsRuntime.ScheduleCase::seed).distinct().count()));x.put("seedScheduleHash",p.seedHash());x.put("priorSeedOverlapCount",s(p.priorSeedOverlap()));x.put("duplicateSeedCount","0");x.put("missingReverseOrientationCount","0");x.put("zeroDegreeLineupCount","0");x.put("offMatchCount",s(r.games().size()));x.put("candidateMatchCount",s(r.games().size()));x.put("replayMatchCount",s(r.replays().size()));x.put("totalSimulationCount",s(r.games().size()*2L+r.replays().size()));for(var e:r.metrics().entrySet()){String k=e.getKey().name().toLowerCase(Locale.ROOT);WinnerMetric v=e.getValue();x.put(k+"ApplicationCount",s(v.applications()));x.put(k+"WinnerFlipCount",s(v.flips()));x.put(k+"WinnerFlipRate",fmt(v.flipRate()));x.put(k+"NearFlipCount",s(v.near()));x.put(k+"MidFlipCount",s(v.mid()));x.put(k+"FarFlipCount",s(v.far()));x.put(k+"SafetyPassed",String.valueOf(v.safe()));}x.put("publicDivergenceCount",s(m.publicDivergence()));x.put("publicDivergenceRate",fmt(m.publicRate()));x.put("publicDivergenceWithoutCausalLocalChangeCount",s(m.publicWithoutCause()));x.put("winnerChangedCount",s(m.winnerChanged()));x.put("winnerChangedRate",fmt(m.winnerRate()));x.put("objectiveChangedCount",s(m.objectiveChanged()));x.put("objectiveChangedRate",fmt(m.objectiveRate()));x.put("structureChangedCount",s(m.structureChanged()));x.put("structureChangedRate",fmt(m.structureRate()));x.put("meanDurationOff",fmt(m.meanOff()));x.put("meanDurationCandidate",fmt(m.meanCandidate()));x.put("meanDurationDelta",fmt(m.meanDurationDelta()));x.put("medianDurationOff",fmt(m.medianOff()));x.put("medianDurationCandidate",fmt(m.medianCandidate()));x.put("p90DurationOff",fmt(m.p90Off()));x.put("p90DurationCandidate",fmt(m.p90Candidate()));x.put("p95DurationOff",fmt(m.p95Off()));x.put("p95DurationCandidate",fmt(m.p95Candidate()));x.put("compositionDirectRandomCount","0");x.put("diagnosticAdditionalRandomDrawCount","0");x.put("preFirstPublicDivergenceRandomMismatchCount",s(r.integrity().random()));x.put("replayMismatchCount",s(r.integrity().replay()));x.put("productionDefaultMode","OFF");x.put("candidateGameplayProductionEnabled","false");x.put("teamCompositionProductionEnabled","false");x.put("publicCandidateGuarded","true");x.put("apiSchemaChanged","false");x.put("frontendChanged","false");x.put("integrityErrorCount",s(r.integrity().total()));x.put("infoCodes","HOLDOUT_CONSUMED");x.put("reviewCodes",r.verdict().startsWith("REVIEW")?r.verdict():"NONE");x.put("warningCodes","NONE");x.put("integrityCodes",r.verdict().startsWith("BLOCKED")?r.verdict():"NONE");x.put("verdict",r.verdict());boolean ready=r.verdict().startsWith("READY");x.put("candidateFrozen","true");x.put("freshHoldoutPassed",String.valueOf(ready));x.put("jointGameplayValidated",String.valueOf(ready));x.put("productionEligible",String.valueOf(ready));x.put("phase13D4DAllowed",String.valueOf(ready));x.put("nextPhase",ready?"PHASE_13D4D_COMPOSITION_PRODUCTION_ACTIVATION_AND_OFF_ROLLBACK_AUDIT":r.verdict().contains("SAMPLE")?"COMPOSITION_FRESH_HOLDOUT_SAMPLE_REVIEW_REQUIRED":r.verdict().startsWith("REVIEW")?"PHASE_13D4C7_1_FRESH_HOLDOUT_FAILURE_ATTRIBUTION":"COMPOSITION_KEY_SPECIFIC_FRESH_HOLDOUT_INTEGRITY_REPAIR_REQUIRED");return x;}

    static void verifySources() throws IOException {if(!SOURCE_SUMMARY_HASH.equals(sha256(SOURCE_SUMMARY))||!SOURCE_AUDIT_HASH.equals(sha256(SOURCE_AUDIT)))throw new IllegalStateException("4C.6 source hash mismatch");FrozenCompositionKeySpecificChannelCandidate.verifyIdentity(FrozenCompositionKeySpecificChannelCandidate.VERSION,FrozenCompositionKeySpecificChannelCandidate.HASH);if(!FrozenCompositionKeySpecificChannelCandidate.HASH.equals(sha256(SOURCE_CANONICAL)))throw new IllegalStateException("candidate canonical mismatch");}
    static Map<Path,String> sourceHashes()throws IOException{Map<Path,String>x=new LinkedHashMap<>();for(Path p:List.of(SOURCE_SUMMARY,SOURCE_AUDIT,SOURCE_CANONICAL,SOURCE_BANDS))x.put(p,sha256(p));return Map.copyOf(x);}
    static long seedFor(String holdoutHash,String pair,Set<Long>prior,Set<Long>used){for(int n=0;;n++){String h=sha256(FrozenCompositionKeySpecificChannelCandidate.HASH+"|"+holdoutHash+"|"+pair+"|"+SEED_SALT+(n==0?"":"|"+n));long v=Long.parseUnsignedLong(h.substring(0,16),16);if(!prior.contains(v)&&used.add(v))return v;}}
    static boolean championOverlap(CompositionFreshHoldoutCandidateGameplayAudit.Lineup a,CompositionFreshHoldoutCandidateGameplayAudit.Lineup b){Set<?>x=new HashSet<>(a.champions().values());return b.champions().values().stream().anyMatch(x::contains);}
    static String scheduleIdentity(CompositionAuditOnlySemanticsRuntime.ScheduleCase x){return x.caseIndex()+"|"+x.orientationGroupId()+"|"+x.seed()+"|"+x.orientation()+"|"+x.blueLineupId()+"|"+x.redLineupId()+"|"+x.pairHash();}
    static Map<String,Integer> index(List<String>h){Map<String,Integer>x=new HashMap<>();for(int i=0;i<h.size();i++)x.put(h.get(i),i);return x;}
    static String norm(String a,String b){return a.compareTo(b)<0?a+"|"+b:b+"|"+a;}
    static String sha256(Path p)throws IOException{return sha256(Files.readAllBytes(p));}static String sha256(String s){return sha256(s.getBytes(StandardCharsets.UTF_8));}static String sha256(byte[]b){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(b));}catch(Exception e){throw new IllegalStateException(e);}}
    static String hashLines(List<String>x){return sha256(String.join("\n",x)+"\n");}static double rate(long n,long d){return d==0?0:(double)n/d;}static double median(List<Integer>x){return quantile(x,.5);}static double quantile(List<Integer>x,double q){if(x.isEmpty())return 0;return x.get((int)Math.floor(q*(x.size()-1)));}static String fmt(double x){return String.format(Locale.ROOT,"%.12f",x);}static String s(long x){return String.valueOf(x);}
    static List<List<String>> rowsHeader(String...h){return new ArrayList<>(List.of(List.of(h)));}static List<List<String>>rows(List<String>h,List<List<String>>v){List<List<String>>x=new ArrayList<>();x.add(h);x.addAll(v);return x;}static List<List<String>>metricRows(String...v){List<List<String>>x=rowsHeader("metric","value");for(int i=0;i<v.length;i+=2)x.add(List.of(v[i],v[i+1]));return x;}
    static void csv(String name,List<List<String>>rows)throws IOException{StringBuilder out=new StringBuilder();for(List<String>row:rows){for(int i=0;i<row.size();i++){if(i>0)out.append(',');String v=row.get(i);if(v.contains(",")||v.contains("\"")||v.contains("\n"))out.append('"').append(v.replace("\"","\"\"")).append('"');else out.append(v);}out.append('\n');}Files.writeString(OUT.resolve(name),out,StandardCharsets.UTF_8);}
    static void transfer(String file,TeamCompositionContext c,Result r)throws IOException{WinnerMetric x=r.metrics().get(c);csv(file,metricRows("context",c.name(),"applicationCount",s(x.applications()),"winnerFlipCount",s(x.flips()),"winnerFlipRate",fmt(x.flipRate()),"nearFlipCount",s(x.near()),"midFlipCount",s(x.mid()),"farFlipCount",s(x.far()),"nonNearRate",fmt(x.nonNearRate()),"nearConcentration",fmt(x.nearConcentration()),"safetyPassed",String.valueOf(x.safe())));}

    record Bands(double p25,double p75){}
    record Edge(int i,int j,CompositionFreshHoldoutCandidateGameplayAudit.Lineup left,CompositionFreshHoldoutCandidateGameplayAudit.Lineup right,String identity){}
    record Prior(Set<String>lineups,Set<String>unorderedPairs,Set<String>orderedPairs,Set<Long>seeds,List<Path>sources){}
    record Prepared(int canonicalCount,List<CompositionFreshHoldoutCandidateGameplayAudit.Lineup>pool,List<Edge>edges,List<CompositionAuditOnlySemanticsRuntime.ScheduleCase>schedule,Prior prior,String poolHash,String pairHash,String scheduleHash,String seedHash,int priorLineupOverlap,int priorUnorderedOverlap,int priorOrderedOverlap,int priorSeedOverlap){}
    record WinnerMetric(long applications,long nonzero,long flips,double flipRate,long near,long mid,long far,double nonNearRate,double nearConcentration,long directionMismatch,long reconstructionMismatch,boolean safe){}
    record Macro(long publicDivergence,double publicRate,long publicWithoutCause,long winnerChanged,double winnerRate,long objectiveChanged,double objectiveRate,long structureChanged,double structureRate,double meanOff,double meanCandidate,double meanDurationDelta,double medianOff,double medianCandidate,double medianDurationDelta,double p90Off,double p90Candidate,double p95Off,double p95Candidate,double durationP95Ratio,double blueWinRateOff,double blueWinRateCandidate){}
    record Integrity(int source,int identity,int overlap,int schedule,int severity,int leakage,int base,int random,int replay,int causality,int simulation){int total(){return source+identity+overlap+schedule+severity+leakage+base+random+replay+causality+simulation;}}
    record Result(Prepared prepared,List<CompositionAuditOnlySemanticsRuntime.PairedGame>games,List<CompositionAuditOnlySemanticsRuntime.Replay>replays,List<CompositionWinnerChannelObservation>winners,List<FightGradeDecisionDiagnostic>grades,List<BaseDefenseRoleRoutingDiagnostic>roles,Map<TeamCompositionContext,WinnerMetric>metrics,Macro macro,Integrity integrity,boolean sampleMinimums,boolean gameplayPassed,String verdict,Map<Path,String>before,Map<Path,String>after){}
}
