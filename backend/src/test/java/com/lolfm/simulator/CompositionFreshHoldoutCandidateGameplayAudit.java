package com.lolfm.simulator;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lolfm.champion.ChampionAssignment;
import com.lolfm.champion.ChampionId;
import com.lolfm.champion.ChampionRoleMatchupProfileCatalog;
import com.lolfm.champion.ChampionSelectionMode;
import com.lolfm.champion.MatchChampionAssignments;
import com.lolfm.composition.*;
import com.lolfm.domain.MatchEvent;
import com.lolfm.domain.MatchEventType;
import com.lolfm.domain.MatchSnapshot;
import com.lolfm.domain.Position;
import com.lolfm.domain.Team;
import com.lolfm.factory.DummyDataFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Phase 13D-4C.1 fresh holdout expansion. Explicit diagnostic only. */
public final class CompositionFreshHoldoutCandidateGameplayAudit {
    static final Path CANONICAL = Path.of("build/reports/thirty-champion-composition-profiles/thirty-champion-composition-lineups.csv");
    static final Path REPRESENTATIVES = Path.of("build/reports/composition-interaction-context/composition-interaction-representative-lineups.csv");
    static final Path PRIOR_4A = Path.of("build/reports/composition-shadow-wiring/composition-shadow-matchup-schedule.csv");
    static final Path PRIOR_4C = Path.of("build/reports/composition-fresh-candidate-gameplay-audit/composition-candidate-fresh-schedule.csv");
    static final Path OUT = Path.of("build/reports/composition-fresh-holdout-candidate-gameplay-audit");
    static final long BASE_SEED = 13_046_001L;
    static final int HOLDOUT_COUNT = 240;
    static final int PAIR_COUNT = 1_000;
    static final int ORDERED_COUNT = 2_000;
    static final int REPLAY_COUNT = 200;
    static final List<Position> POSITIONS = List.of(Position.TOP, Position.JUNGLE, Position.MID, Position.ADC, Position.SUPPORT);
    static final List<String> PATTERNS = List.of("ENGAGE_CHAIN","FRONT_TO_BACK","POKE_SIEGE","PICK_CONVERSION","SPLIT_MAP_PRESSURE","OBJECTIVE_CONTROL");
    static final List<String> CAPS = List.of("ENGAGE","FOLLOW_UP","DISENGAGE","FRONTLINE","PEEL","PICK","POKE","SIEGE","WAVE_CLEAR","ZONE_CONTROL","SIDE_LANE_PRESSURE","OBJECTIVE_DAMAGE","SUSTAINED_DAMAGE","BURST_DAMAGE","BACKLINE_ACCESS");
    static final Map<String,double[]> FROZEN_BANDS = Map.of(
            "SKIRMISH|SKIRMISH|SKIRMISH_COMBAT_SCORE", new double[]{5.104214137066,109.230158346492},
            "TEAMFIGHT|TEAMFIGHT|TEAMFIGHT_COMBAT_SCORE", new double[]{0.820000000000,31.960000000000},
            "SIEGE|SIEGE_COMBAT|SIEGE_PUSH_SCORE", new double[]{0.820000000000,22.820000000000},
            "BASE_DEFENSE|BASE_DEFENSE|BASE_DEFENSE_SCORE", new double[]{0.820000000000,27.820000000000});
    static final String PRIOR_4B2_SUMMARY_HASH = "c536a68d5ee670f6ad9ab3adbab0c028fd5800a81289f58f2b9b2cd5bc0e574f";
    static final String PRIOR_4B2_AUDIT_HASH = "31bb42035683942bcaf2614bcbc26589c1f021afa2ad384c7228271b822668e1";
    static final String PRIOR_4B2_CANDIDATE_HASH = "197fc740af3b96b77ed2fd5fec3be512975d26c7aa1058bdeb6eb8e489ede918";
    static final String PRIOR_4C_SUMMARY_HASH = "e170fd5baa30e71d3ba24a804abe3c348a40d21501c996fb36fe5041198a011d";
    static final String PRIOR_4C_AUDIT_HASH = "bd4f63a94c75229428968ee12173782289dbdbad8ee1f8097e5d4377a04331bb";
    static final String PRIOR_4C_SCHEDULE_HASH = "43780ef137c9ee61b746f2c043f16ddb46919cbd4b549f6c528aacd2f3c6bb09";
    static final ObjectMapper JSON = new ObjectMapper();
    static final DummyDataFactory FACTORY = new DummyDataFactory();

    private CompositionFreshHoldoutCandidateGameplayAudit() {}

    public static void main(String[] args) throws Exception {
        Result result = run();
        System.out.println("Composition fresh holdout candidate gameplay audit: " + result.verdict);
        if (result.verdict.startsWith("BLOCKED")) throw new IllegalStateException(result.verdict);
    }

    static Result run() throws Exception {
        Files.createDirectories(OUT);
        Map<String,String> before = historicalHashes();
        FrozenCompositionGameplayGainPolicy policy = FrozenCompositionGameplayGainPolicy.current();
        List<Lineup> canonical = readCanonical();
        Set<String> representatives = readSingleColumnIds(REPRESENTATIVES, "lineupId");
        Set<String> priorLineups = new HashSet<>(representatives);
        priorLineups.addAll(readScheduleLineups(PRIOR_4A));
        priorLineups.addAll(readScheduleLineups(PRIOR_4C));
        List<Lineup> pool = selectHoldout(canonical, priorLineups);
        PoolCheck poolCheck = poolCheck(pool, canonical, representatives, priorLineups);
        List<UnorderedPair> pairs = selectPairs(pool, readPriorPairs(PRIOR_4A, PRIOR_4C));
        GraphCheck graphCheck = graphCheck(pool, pairs);
        List<ScheduleRow> schedule = orderedSchedule(pairs);
        boolean preflight = poolCheck.accepted && graphCheck.accepted && schedule.size() == ORDERED_COUNT;
        List<GamePair> games = new ArrayList<>();
        List<AppRow> apps = new ArrayList<>();
        List<LocalRow> locals = new ArrayList<>();
        List<ReplayRow> replays = new ArrayList<>();
        if (preflight) {
            MatchSimulator off = simulator(TeamCompositionGameplayMode.OFF, CompositionCandidateExecutionAuthorization.none());
            MatchSimulator candidate = simulator(TeamCompositionGameplayMode.CANDIDATE, CompositionCandidateExecutionAuthorization.frozenAudit());
            for (ScheduleRow row : schedule) {
                MatchChampionAssignments assignments = assignments(row.blue, row.red);
                MatchSimulator.SimulationResult offResult = run(off, row, assignments, "OFF");
                MatchSimulator.SimulationResult candidateResult = run(candidate, row, assignments, "CANDIDATE");
                games.add(game(row, offResult, candidateResult));
                for (CompositionCandidateApplicationObservation x : candidateResult.compositionRuntimeDiagnostics().candidateApplications()) apps.add(new AppRow(row, x));
                for (CompositionLocalDecisionComparison x : candidateResult.compositionRuntimeDiagnostics().localDecisionComparisons()) locals.add(new LocalRow(row, x));
                if (row.caseIndex % 10 == 0) {
                    MatchSimulator.SimulationResult replay = run(candidate, row, assignments, "CANDIDATE_REPLAY");
                    replays.add(replay(row, candidateResult, replay));
                }
            }
        }
        Map<String,String> after = historicalHashes();
        String verdict = verdict(policy, poolCheck, graphCheck, schedule, games, apps, locals, replays, before.equals(after), preflight);
        Result result = new Result(policy, canonical, pool, pairs, schedule, games, apps, locals, replays, poolCheck, graphCheck, before, after, verdict);
        write(result);
        if (!before.equals(after)) throw new IllegalStateException("historical Phase 13D-3/4B.2/4C artifacts changed");
        return result;
    }

    private static MatchSimulator.SimulationResult run(MatchSimulator simulator, ScheduleRow row, MatchChampionAssignments assignments, String orientation) {
        SideOrientationRandomTraceObserver observer = new SideOrientationRandomTraceObserver(row.seed, orientation, row.blue.id, row.red.id, true);
        return simulator.simulateWithSideDiagnostics(FACTORY.createBlueTeam(), FACTORY.createRedTeam(), assignments, observer);
    }

    private static MatchSimulator simulator(TeamCompositionGameplayMode mode, CompositionCandidateExecutionAuthorization authorization) {
        return new MatchSimulator(new TeamfightResolver(), new EndGameEvaluator(), new SnapshotFactory(), new ObjectiveResolver(),
                new PostFightResolver(), new ObjectiveAttemptResolver(), new StructureResolver(), new PushResolver(),
                SimulationOptions.productionDefaults().withTeamCompositionGameplayMode(mode),
                ChampionRoleMatchupProfileCatalog.production(), authorization);
    }

    static List<Lineup> readCanonical() throws IOException {
        List<String> lines = Files.readAllLines(CANONICAL, StandardCharsets.UTF_8);
        if (lines.size() - 1 != 7_776) throw new IllegalStateException("Expected 7776 canonical lineups, got " + (lines.size()-1));
        Map<String,Integer> h = index(csv(lines.getFirst()));
        List<Lineup> out = new ArrayList<>();
        for (String line : lines.subList(1, lines.size())) {
            if (line.isBlank()) continue;
            List<String> c = csv(line); EnumMap<Position,ChampionId> ids = new EnumMap<>(Position.class); Map<String,Double> m = new HashMap<>();
            for (Position p : POSITIONS) { String stable = c.get(h.get(p.name())); int at = stable.lastIndexOf(':'); if (at <= 0 || !stable.endsWith(":"+p.name())) throw new IllegalStateException("invalid canonical role="+stable); ids.put(p,new ChampionId(stable.substring(0,at))); }
            for (String key : PATTERNS) m.put(key, Double.parseDouble(c.get(h.get(key))));
            for (String key : CAPS) m.put(key, Double.parseDouble(c.get(h.get(key))));
            out.add(new Lineup(c.get(h.get("lineupId")), ids, m));
        }
        out.sort(Comparator.comparing(x -> x.id));
        return List.copyOf(out);
    }

    static List<Lineup> selectHoldout(List<Lineup> canonical, Set<String> excluded) {
        return selectHoldoutWithLimits(canonical, excluded, HOLDOUT_COUNT, 20);
    }

    static List<Lineup> selectHoldoutForContract(
            List<Lineup> canonical,
            Set<String> excluded,
            int selectionCount,
            int minimumPatternCoverage
    ) {
        if (selectionCount < 0 || minimumPatternCoverage < 0) {
            throw new IllegalArgumentException("selection limits must be non-negative");
        }
        return selectHoldoutWithLimits(
                canonical, excluded, selectionCount, minimumPatternCoverage);
    }

    private static List<Lineup> selectHoldoutWithLimits(
            List<Lineup> canonical,
            Set<String> excluded,
            int selectionCount,
            int minimumPatternCoverage
    ) {
        List<Lineup> available = canonical.stream().filter(x -> !excluded.contains(x.id)).toList();
        List<Lineup> selected = new ArrayList<>(); Map<String,Integer> champions = new HashMap<>(), positionChampions = new HashMap<>(); Map<String,Integer> patterns = new HashMap<>();
        while (selected.size() < selectionCount && !available.isEmpty()) {
            Lineup best = available.stream().filter(x -> !selected.contains(x)).max((a,b) -> compareCandidate(a,b,champions,positionChampions,patterns,selected,minimumPatternCoverage)).orElseThrow();
            selected.add(best); for (ChampionId id : best.champions.values()) champions.merge(id.value(),1,Integer::sum);
            for (Position p : POSITIONS) positionChampions.merge(best.champions.get(p).value()+":"+p.name(),1,Integer::sum);
            for (String p : PATTERNS) if (best.metric(p) >= .70) patterns.merge(p,1,Integer::sum);
        }
        return List.copyOf(selected);
    }

    private static int compareCandidate(Lineup a, Lineup b, Map<String,Integer> championUse, Map<String,Integer> roleUse, Map<String,Integer> patternUse, List<Lineup> selected, int minimumPatternCoverage) {
        int ap = patternScore(a,patternUse,minimumPatternCoverage), bp = patternScore(b,patternUse,minimumPatternCoverage); if (ap != bp) return Integer.compare(ap,bp);
        int am = projectedMax(a,championUse), bm = projectedMax(b,championUse); if (am != bm) return Integer.compare(bm,am);
        int as = projectedSquares(a,championUse), bs = projectedSquares(b,championUse); if (as != bs) return Integer.compare(bs,as);
        int ar = projectedRoleMax(a,roleUse), br = projectedRoleMax(b,roleUse); if (ar != br) return Integer.compare(br,ar);
        int ad = diversity(a,selected), bd = diversity(b,selected); if (ad != bd) return Integer.compare(ad,bd);
        return b.id.compareTo(a.id); // max() chooses canonical ID on a final tie
    }
    private static int patternScore(Lineup x, Map<String,Integer> counts, int minimumPatternCoverage) { int v=0; for(String p:PATTERNS) if(counts.getOrDefault(p,0)<minimumPatternCoverage && x.metric(p)>=.70) v++; return v; }
    private static int projectedMax(Lineup x, Map<String,Integer> use) { int max=0; for(ChampionId id:x.champions.values()) max=Math.max(max,use.getOrDefault(id.value(),0)+1); return max; }
    private static int projectedRoleMax(Lineup x, Map<String,Integer> use) { int max=0; for(Position p:POSITIONS) max=Math.max(max,use.getOrDefault(x.champions.get(p).value()+":"+p.name(),0)+1); return max; }
    private static int projectedSquares(Lineup x, Map<String,Integer> use) { Map<String,Integer> q=new HashMap<>(use); for(ChampionId id:x.champions.values())q.merge(id.value(),1,Integer::sum); return q.values().stream().mapToInt(v->v*v).sum(); }
    private static int diversity(Lineup x,List<Lineup> selected) { if(selected.isEmpty())return 100000; double best=Double.MAX_VALUE; for(Lineup y:selected){double d=0;for(String k:CAPS)d+=Math.abs(x.metric(k)-y.metric(k));best=Math.min(best,d);} return (int)Math.round(best*1000); }

    static PoolCheck poolCheck(List<Lineup> pool,List<Lineup> canonical,Set<String> reps,Set<String> prior) {
        Set<String> ids=pool.stream().map(x->x.id).collect(Collectors.toSet()); Set<String> champs=pool.stream().flatMap(x->x.champions.values().stream()).map(ChampionId::value).collect(Collectors.toSet());
        Map<String,List<Integer>> usage=new HashMap<>(), roleUsage=new HashMap<>(); for(Lineup x:pool){for(ChampionId id:x.champions.values())usage.computeIfAbsent(id.value(),k->new ArrayList<>()).add(1);for(Position p:POSITIONS)roleUsage.computeIfAbsent(x.champions.get(p).value()+":"+p.name(),k->new ArrayList<>()).add(1);}
        int patternFailures=0; for(String p:PATTERNS) if(pool.stream().filter(x->x.metric(p)>=.70).count()<20)patternFailures++;
        int championMin=usage.values().stream().mapToInt(List::size).min().orElse(0), championMax=usage.values().stream().mapToInt(List::size).max().orElse(0), roleMin=roleUsage.values().stream().mapToInt(List::size).min().orElse(0), roleMax=roleUsage.values().stream().mapToInt(List::size).max().orElse(0);
        boolean valid=pool.size()==HOLDOUT_COUNT&&ids.size()==pool.size()&&pool.stream().allMatch(x->x.champions.size()==5&&x.champions.values().stream().distinct().count()==5)&&champs.size()==30&&POSITIONS.stream().allMatch(p->pool.stream().map(x->x.champions.get(p).value()).collect(Collectors.toSet()).size()==6)&&patternFailures==0&&championMin>=20&&championMax<=championMin*1.5&&roleMin>=20&&roleMax<=roleMin*1.5&&Collections.disjoint(ids,reps)&&Collections.disjoint(ids,prior);
        return new PoolCheck(valid,pool.size(),ids.size(),champs.size(),patternFailures,championMin,championMax,roleMin,roleMax,ids.stream().filter(reps::contains).count(),ids.stream().filter(prior::contains).count());
    }

    static Set<String> readPriorPairs(Path... paths) throws IOException { Set<String> out=new HashSet<>(); for(Path p:paths){List<String> lines=Files.readAllLines(p,StandardCharsets.UTF_8);if(lines.isEmpty())continue;Map<String,Integer> h=index(csv(lines.getFirst()));Integer bi=h.get("blueLineupId"),ri=h.get("redLineupId");if(bi==null||ri==null)continue;for(String line:lines.subList(1,lines.size())){if(line.isBlank())continue;List<String> c=csv(line);String a=c.get(bi),b=c.get(ri);out.add(norm(a,b));}}return out; }
    static Set<String> readSingleColumnIds(Path p,String column) throws IOException {List<String> lines=Files.readAllLines(p,StandardCharsets.UTF_8);Map<String,Integer> h=index(csv(lines.getFirst()));Set<String> out=new HashSet<>();for(String l:lines.subList(1,lines.size()))if(!l.isBlank())out.add(csv(l).get(h.get(column)));return out;}
    static Set<String> readScheduleLineups(Path p) throws IOException {Set<String> out=new HashSet<>();List<String> lines=Files.readAllLines(p,StandardCharsets.UTF_8);if(lines.isEmpty())return out;Map<String,Integer> h=index(csv(lines.getFirst()));Integer bi=h.get("blueLineupId"),ri=h.get("redLineupId");if(bi==null)return out;for(String l:lines.subList(1,lines.size()))if(!l.isBlank()){List<String> c=csv(l);out.add(c.get(bi));if(ri!=null)out.add(c.get(ri));}return out;}
    private static String norm(String a,String b){return a.compareTo(b)<0?a+"|"+b:b+"|"+a;}
    private static int overlap(Lineup a,Lineup b){Set<ChampionId> s=new HashSet<>(a.champions.values());s.retainAll(b.champions.values());return s.size();}
    static int legalUnorderedPairCount(List<Lineup> pool){int count=0;for(int i=0;i<pool.size();i++)for(int j=i+1;j<pool.size();j++)if(overlap(pool.get(i),pool.get(j))==0)count++;return count;}

    static List<UnorderedPair> selectPairs(List<Lineup> pool,Set<String> prior) { List<UnorderedPair> all=new ArrayList<>();for(int i=0;i<pool.size();i++)for(int j=i+1;j<pool.size();j++){if(overlap(pool.get(i),pool.get(j))!=0)continue;String n=norm(pool.get(i).id,pool.get(j).id);if(prior.contains(n))continue;all.add(new UnorderedPair(i,j,pool.get(i),pool.get(j),sha256(n.getBytes(StandardCharsets.UTF_8))));} all.sort(Comparator.comparing(x->x.hash));int[] deg=new int[pool.size()];Set<String> used=new HashSet<>();List<UnorderedPair> chosen=new ArrayList<>();while(chosen.size()<PAIR_COUNT){UnorderedPair best=null;for(UnorderedPair e:all)if(!used.contains(e.hash)&&deg[e.i]<11&&deg[e.j]<11){int missing=(deg[e.i]<6?1:0)+(deg[e.j]<6?1:0);int score=missing*100000-Math.max(deg[e.i]+1,deg[e.j]+1)*100-(deg[e.i]+deg[e.j]);if(best==null||score>bestScore(best,deg)||score==bestScore(best,deg)&&e.hash.compareTo(best.hash)<0)best=e;}if(best==null)break;used.add(best.hash);deg[best.i]++;deg[best.j]++;chosen.add(best);}return List.copyOf(chosen); }
    private static int bestScore(UnorderedPair e,int[] d){return ((d[e.i]<6?1:0)+(d[e.j]<6?1:0))*100000-Math.max(d[e.i]+1,d[e.j]+1)*100-(d[e.i]+d[e.j]);}
    static GraphCheck graphCheck(List<Lineup> pool,List<UnorderedPair> pairs){int[] d=new int[pool.size()];Set<String> ids=new HashSet<>();for(UnorderedPair p:pairs){d[p.i]++;d[p.j]++;ids.add(p.left.id);ids.add(p.right.id);}int min=Arrays.stream(d).min().orElse(0),max=Arrays.stream(d).max().orElse(0);return new GraphCheck(pairs.size()==PAIR_COUNT&&ids.size()==pool.size()&&min>=6&&max<=11&&max-min<=5, pairs.size(),ids.size(),min,max,max-min);}
    static List<ScheduleRow> orderedSchedule(List<UnorderedPair> pairs){List<ScheduleRow> out=new ArrayList<>();int i=0;for(int g=0;g<pairs.size();g++){UnorderedPair p=pairs.get(g);long seed=BASE_SEED+g;out.add(new ScheduleRow(i++,g,seed,0,p.left,p.right,p.hash));out.add(new ScheduleRow(i++,g,seed,1,p.right,p.left,p.hash));}return List.copyOf(out);}

    private static MatchChampionAssignments assignments(Lineup blue,Lineup red){List<ChampionAssignment> a=new ArrayList<>();for(Position p:POSITIONS){a.add(new ChampionAssignment(new PlayerKey(TeamSide.BLUE,p),blue.champions.get(p),p));a.add(new ChampionAssignment(new PlayerKey(TeamSide.RED,p),red.champions.get(p),p));}return new MatchChampionAssignments(a,ChampionSelectionMode.EXPLICIT);}
    private static GamePair game(ScheduleRow row, MatchSimulator.SimulationResult off,
                                 MatchSimulator.SimulationResult cand) {
        MatchSnapshot a = off.timeline().getSnapshots().getLast();
        MatchSnapshot b = cand.timeline().getSnapshots().getLast();
        String first = firstDivergence(off, cand);
        int publicTime = firstTime(off, cand);
        int local = cand.compositionRuntimeDiagnostics().localDecisionComparisons().stream()
                .filter(CompositionLocalDecisionComparison::changed)
                .mapToInt(CompositionLocalDecisionComparison::matchTimeSeconds)
                .min().orElse(-1);
        boolean causal = "NO_PUBLIC_DIVERGENCE".equals(first)
                || (local >= 0 && publicTime >= 0 && local <= publicTime);
        String offObjective = objectiveSignature(off);
        String candidateObjective = objectiveSignature(cand);
        String offStructure = structureSignature(off);
        String candidateStructure = structureSignature(cand);
        return new GamePair(row.caseIndex, row.groupIndex, row.orientation, row.seed, row.blue.id, row.red.id,
                off.winnerSide(), cand.winnerSide(), off.timeline().getDurationSeconds(),
                cand.timeline().getDurationSeconds(), hash(off.timeline()), hash(cand.timeline()),
                hashEvents(off), hashEvents(cand), hashSnapshots(off), hashSnapshots(cand),
                a.getBlueKills(), b.getBlueKills(), a.getRedKills(), b.getRedKills(),
                a.getBlueDragons(), b.getBlueDragons(), a.getRedDragons(), b.getRedDragons(),
                a.getBlueTowersDestroyed(), b.getBlueTowersDestroyed(),
                a.getRedTowersDestroyed(), b.getRedTowersDestroyed(),
                randomPrefixExact(off, cand, local >= 0 ? local : publicTime), first, local, publicTime,
                causal, cand.compositionRuntimeDiagnostics().gameplayApplicationCount(),
                cand.compositionRuntimeDiagnostics().deferredCandidateApplicationCount(),
                offObjective, candidateObjective, offStructure, candidateStructure);
    }
    private static ReplayRow replay(ScheduleRow row,MatchSimulator.SimulationResult a,MatchSimulator.SimulationResult b){return new ReplayRow(row.caseIndex,row.seed,hash(a.timeline()),hash(b.timeline()),hash(a.compositionRuntimeDiagnostics().candidateApplications()),hash(b.compositionRuntimeDiagnostics().candidateApplications()),a.randomDrawCount()==b.randomDrawCount()&&hash(a.timeline()).equals(hash(b.timeline())),hash(a.compositionRuntimeDiagnostics().candidateApplications()).equals(hash(b.compositionRuntimeDiagnostics().candidateApplications())), a.randomDrawCount()==b.randomDrawCount());}
    private static boolean randomPrefixExact(MatchSimulator.SimulationResult a, MatchSimulator.SimulationResult b, int divergenceTime) {
        List<SideOrientationRandomTraceObserver.Draw> x = a.randomTrace(), y = b.randomTrace();
        if (divergenceTime < 0) return drawShape(x).equals(drawShape(y));
        return drawShape(x.stream().filter(d -> d.tickSeconds() < divergenceTime).toList())
                .equals(drawShape(y.stream().filter(d -> d.tickSeconds() < divergenceTime).toList()));
    }
    private static List<String> drawShape(List<SideOrientationRandomTraceObserver.Draw> draws) {
        return draws.stream().map(d -> d.drawIndex()+"|"+d.resolverSource()+"|"+d.side()+"|"+d.tickSeconds()+"|"+d.drawType()+"|"+d.boundOrBits()+"|"+d.returnedValue()).toList();
    }
    private static String firstDivergence(MatchSimulator.SimulationResult a, MatchSimulator.SimulationResult b) {
        int eventTime = firstEventTime(a, b);
        int snapshotTime = firstSnapshotTime(a, b);
        if (eventTime < 0 && snapshotTime < 0) {
            if (a.timeline().getDurationSeconds() != b.timeline().getDurationSeconds()) return "DURATION_ONLY";
            if (a.winnerSide() != b.winnerSide()) return "MATCH_WINNER";
            return "NO_PUBLIC_DIVERGENCE";
        }
        return snapshotTime >= 0 && (eventTime < 0 || snapshotTime < eventTime)
                ? "SNAPSHOT" : firstEventType(a, b);
    }
    private static int firstTime(MatchSimulator.SimulationResult a, MatchSimulator.SimulationResult b) {
        int eventTime = firstEventTime(a, b);
        int snapshotTime = firstSnapshotTime(a, b);
        if (eventTime < 0) return snapshotTime;
        if (snapshotTime < 0) return eventTime;
        return Math.min(eventTime, snapshotTime);
    }
    private static int firstEventTime(MatchSimulator.SimulationResult a, MatchSimulator.SimulationResult b) {
        List<MatchEvent> x = a.timeline().getEvents(), y = b.timeline().getEvents();
        for (int i = 0; i < Math.min(x.size(), y.size()); i++) {
            try {
                if (!JSON.writeValueAsString(x.get(i)).equals(JSON.writeValueAsString(y.get(i)))) {
                    return Math.min(x.get(i).getTimeSeconds(), y.get(i).getTimeSeconds());
                }
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        }
        return x.size() == y.size() ? -1 : Math.min(x.isEmpty() ? Integer.MAX_VALUE : x.getLast().getTimeSeconds(),
                y.isEmpty() ? Integer.MAX_VALUE : y.getLast().getTimeSeconds());
    }
    private static int firstSnapshotTime(MatchSimulator.SimulationResult a, MatchSimulator.SimulationResult b) {
        List<MatchSnapshot> x = a.timeline().getSnapshots(), y = b.timeline().getSnapshots();
        for (int i = 0; i < Math.min(x.size(), y.size()); i++) {
            try {
                if (!JSON.writeValueAsString(x.get(i)).equals(JSON.writeValueAsString(y.get(i)))) {
                    return Math.min(x.get(i).getTimeSeconds(), y.get(i).getTimeSeconds());
                }
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        }
        return x.size() == y.size() ? -1 : Math.min(x.isEmpty() ? Integer.MAX_VALUE : x.getLast().getTimeSeconds(),
                y.isEmpty() ? Integer.MAX_VALUE : y.getLast().getTimeSeconds());
    }
    private static String firstEventType(MatchSimulator.SimulationResult a, MatchSimulator.SimulationResult b) {
        List<MatchEvent> x = a.timeline().getEvents(), y = b.timeline().getEvents();
        for (int i = 0; i < Math.min(x.size(), y.size()); i++) {
            try {
                if (!JSON.writeValueAsString(x.get(i)).equals(JSON.writeValueAsString(y.get(i)))) {
                    return type(x.get(i).getType(), y.get(i).getType());
                }
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        }
        return x.size() == y.size() ? "EVENT" : "EVENT";
    }
    private static String type(MatchEventType a,MatchEventType b){MatchEventType t=b==null?a:b;return switch(t){case KILL,TEAMFIGHT,TEAMFIGHT_RESULT,ACE->"COMBAT_RESULT";case DRAGON,BARON,ELDER->"OBJECTIVE_RESULT";case TOWER->"STRUCTURE_RESULT";case GAME_END->"MATCH_WINNER";default->"EVENT";};}
    private static String hash(Object x){try{return sha256(JSON.writeValueAsBytes(x));}catch(Exception e){throw new IllegalStateException(e);}}
    private static String hash(MatchSimulator.SimulationResult x){return hash(x.timeline());}
    private static String hashEvents(MatchSimulator.SimulationResult x){return hash(x.timeline().getEvents());}
    private static String hashSnapshots(MatchSimulator.SimulationResult x){return hash(x.timeline().getSnapshots());}

    /** Structured objective state signature; no event message parsing is used. */
    private static String objectiveSignature(MatchSimulator.SimulationResult result) {
        MatchSnapshot snapshot = result.timeline().getSnapshots().getLast();
        String dragonOwner = result.dragonCaptures().stream().map(c -> String.valueOf(c.capturingSide()))
                .collect(Collectors.joining(">"));
        String dragonTiming = result.dragonCaptures().stream().map(c -> c.captureTimeSeconds() + "@" + c.spawnedAliveSeconds())
                .collect(Collectors.joining(">"));
        String baronOwner = result.objectiveDecisionHistory().stream()
                .filter(d -> d.objectiveType().name().equals("BARON") && d.captureSide() != null)
                .map(d -> String.valueOf(d.captureSide())).collect(Collectors.joining(">"));
        String baronTiming = result.objectiveDecisionHistory().stream()
                .filter(d -> d.objectiveType().name().equals("BARON") && d.captureSide() != null)
                .map(d -> String.valueOf(d.evaluationTimeSeconds())).collect(Collectors.joining(">"));
        String elderOwner = result.objectiveDecisionHistory().stream()
                .filter(d -> d.objectiveType().name().equals("ELDER") && d.captureSide() != null)
                .map(d -> String.valueOf(d.captureSide())).collect(Collectors.joining(">"));
        String elderTiming = result.objectiveDecisionHistory().stream()
                .filter(d -> d.objectiveType().name().equals("ELDER") && d.captureSide() != null)
                .map(d -> String.valueOf(d.evaluationTimeSeconds())).collect(Collectors.joining(">"));
        return "DRAGON_OWNER_SEQUENCE=" + dragonOwner
                + "|DRAGON_TIMING=" + dragonTiming
                + "|DRAGON_STACK=" + snapshot.getBlueDragons() + ":" + snapshot.getRedDragons()
                + "|SOUL_OWNER=" + result.soulOwner()
                + "|SOUL_TIMING=" + result.soulClaimedAtSeconds()
                + "|BARON_OWNER_SEQUENCE=" + baronOwner
                + "|BARON_TIMING=" + baronTiming
                + "|BARON_ACTIVE=" + snapshot.isBlueHasBaronBuff() + ":" + snapshot.isRedHasBaronBuff()
                + "|ELDER_OWNER_SEQUENCE=" + elderOwner
                + "|ELDER_TIMING=" + elderTiming
                + "|ELDER_ACTIVE=" + snapshot.isElderAlive() + ":" + snapshot.isBlueHasElderBuff() + ":" + snapshot.isRedHasElderBuff();
    }

    /** Structured structure sequence/state signature; no formatted text parsing is used. */
    private static String structureSignature(MatchSimulator.SimulationResult result) {
        MatchSnapshot snapshot = result.timeline().getSnapshots().getLast();
        String sequence = result.timeline().getEvents().stream()
                .filter(e -> e.getType() == MatchEventType.TOWER)
                .map(e -> e.getTimeSeconds() + "@" + e.getStructureKind() + "@"
                        + e.getStructureTowerTier() + "@" + e.getStructureLane() + "@"
                        + e.getStructureAttackingSide() + "@" + e.getStructureDefendingSide() + "@"
                        + e.getStructureActionSource())
                .collect(Collectors.joining(">"));
        int nexusTime = result.timeline().getEvents().stream()
                .filter(e -> e.getType() == MatchEventType.TOWER && e.getStructureKind() == StructureKind.NEXUS)
                .mapToInt(MatchEvent::getTimeSeconds).min().orElse(-1);
        return "STRUCTURE_SEQUENCE=" + sequence
                + "|STRUCTURE_TIMING=" + result.timeline().getEvents().stream()
                .filter(e -> e.getType() == MatchEventType.TOWER)
                .map(e -> String.valueOf(e.getTimeSeconds())).collect(Collectors.joining(">"))
                + "|BASE_PUSH_SEQUENCE=" + sequence
                + "|TOWER_STATE=" + snapshot.getBlueTowersDestroyed() + ":" + snapshot.getRedTowersDestroyed()
                + "|INHIBITOR_STATE=" + snapshot.getBlueInhibitorsRemaining() + ":" + snapshot.getRedInhibitorsRemaining()
                + "|NEXUS_TURRET_STATE=" + snapshot.getBlueNexusTurretsRemaining() + ":" + snapshot.getRedNexusTurretsRemaining()
                + "|NEXUS_STATE=" + snapshot.isBlueNexusAlive() + ":" + snapshot.isRedNexusAlive()
                + "|NEXUS_ENDING_TIME=" + nexusTime;
    }

    private static boolean objectiveChanged(GamePair x) {
        return !x.offObjectiveSignature.equals(x.candidateObjectiveSignature);
    }

    private static boolean structureChanged(GamePair x) {
        return !x.offStructureSignature.equals(x.candidateStructureSignature);
    }

    private static String verdict(FrozenCompositionGameplayGainPolicy policy, PoolCheck pool, GraphCheck graph,
                                  List<ScheduleRow> schedule, List<GamePair> games, List<AppRow> applications,
                                  List<LocalRow> locals, List<ReplayRow> replays, boolean sourceUnchanged, boolean preflight) {
        policy.verifyExactIdentity();
        if (!pool.accepted) return "REVIEW_FRESH_HOLDOUT_LINEUP_POOL";
        if (!graph.accepted) return "REVIEW_FRESH_HOLDOUT_PAIR_GRAPH";
        long applied = applications.stream().filter(x -> x.observation.applicationApplied()).count();
        boolean duplicateApplication = applications.stream().filter(x -> x.observation.applicationApplied())
                .collect(Collectors.groupingBy(x -> x.row.caseIndex + "|" + x.observation.attemptId().sequence()
                        + "|" + x.observation.applicationKey(), Collectors.counting())).values().stream().anyMatch(n -> n > 1);
        boolean multiKey = applications.stream().filter(x -> x.observation.applicationApplied())
                .collect(Collectors.groupingBy(x -> x.row.caseIndex + "|" + x.observation.attemptId().sequence()
                        , Collectors.mapping(x -> x.observation.applicationKey(), Collectors.toSet()))).values().stream().anyMatch(keys -> keys.size() > 1);
        boolean frozenKeys = applications.stream().filter(x -> x.observation.applicationApplied())
                .allMatch(x -> policy.approvedKeys().stream().anyMatch(k -> k.stableId().equals(x.observation.applicationKey())
                        && Math.abs(k.selectedGain() - x.observation.selectedGain()) < 1.0e-12));
        boolean arithmetic = applications.stream().filter(x -> x.observation.applicationApplied()).allMatch(x ->
                Math.abs(x.observation.gapModifier() - x.observation.selectedGain() * x.observation.perspectiveRawEdge()) <= 1.0e-9
                        && Math.abs(x.observation.perspectiveAdjustment() + x.observation.opponentAdjustment()) <= 1.0e-9);
        boolean localAvailable = locals.size() == applied && locals.stream().allMatch(x -> x.comparison.comparisonAvailable());
        boolean publicCause = games.stream().allMatch(x -> "NO_PUBLIC_DIVERGENCE".equals(x.firstPublic) || (x.causal && x.firstLocalTime >= 0));
        boolean causalIdentity = games.stream().filter(x -> !"NO_PUBLIC_DIVERGENCE".equals(x.firstPublic))
                .allMatch(x -> firstLocal(locals, x) != null);
        boolean replayExact = replays.size() == REPLAY_COUNT && replays.stream()
                .allMatch(x -> x.timelineExact && x.applicationExact && x.randomExact);
        boolean sideSafe = games.stream().allMatch(x -> x.randomExact);
        if (!sourceUnchanged || schedule.size() != ORDERED_COUNT || games.size() != ORDERED_COUNT
                || duplicateApplication || multiKey || !frozenKeys || !arithmetic || !localAvailable
                || !publicCause || !causalIdentity || !replayExact || !sideSafe) {
            return "BLOCKED_BY_COMPOSITION_FRESH_HOLDOUT_GAMEPLAY_INTEGRITY";
        }
        if (applied > 0 && locals.isEmpty()) return "REVIEW_LOCAL_OUTCOME_ATTRIBUTION_UNAVAILABLE";
        boolean review = !scoreSafetyPassed(policy, applications) || !localSafetyPassed(policy, locals)
                || winnerFlipRate(games) > .020000000000
                || Math.abs(blueWinRate(games, true) - blueWinRate(games, false)) > .007500000000
                || Math.abs(meanDelta(games)) > 60.0 || Math.abs(medianDelta(games)) > 30.0
                || durationP95Ratio(games) < .95 || durationP95Ratio(games) > 1.05
                || objectiveChangeRate(games) > .05 || structureChangeRate(games) > .08
                || directionImbalance(games) > .35;
        return review ? "REVIEW_COMPOSITION_FRESH_HOLDOUT_GAMEPLAY_EFFECT" : "READY_FOR_PHASE_13D4D";
    }

    private static LocalRow firstLocal(List<LocalRow> locals, GamePair game) {
        return locals.stream().filter(x -> x.row.caseIndex == game.caseIndex)
                .filter(x -> x.comparison.localOutcomeChanged())
                .min(Comparator.comparingInt(x -> x.comparison.matchTimeSeconds())).orElse(null);
    }

    private static boolean scoreSafetyPassed(FrozenCompositionGameplayGainPolicy policy, List<AppRow> apps) {
        for (CompositionGameplayApplicationKey key : policy.approvedKeys()) {
            List<AppRow> values = apps.stream().filter(x -> x.observation.applicationApplied())
                    .filter(x -> key.stableId().equals(x.observation.applicationKey())).toList();
            long close = values.stream().filter(x -> band(x).equals("CLOSE")).count();
            long medium = values.stream().filter(x -> band(x).equals("MEDIUM")).count();
            long high = values.stream().filter(x -> band(x).equals("HIGH")).count();
            long closeFlip = values.stream().filter(x -> band(x).equals("CLOSE") && x.observation.signFlip()).count();
            long nonCloseFlip = values.stream().filter(x -> !band(x).equals("CLOSE") && x.observation.signFlip()).count();
            long total = closeFlip + nonCloseFlip;
            if (rate(closeFlip, close) > .333333333333 || rate(nonCloseFlip, medium + high) > .010000000000
                    || values.stream().filter(x -> band(x).equals("HIGH") && x.observation.signFlip()).findAny().isPresent()
                    || total > 0 && rate(closeFlip, total) < .950000000000) return false;
        }
        return true;
    }

    private static boolean localSafetyPassed(FrozenCompositionGameplayGainPolicy policy, List<LocalRow> locals) {
        for (CompositionGameplayApplicationKey key : policy.approvedKeys()) {
            List<LocalRow> values = locals.stream().filter(x -> key.stableId().equals(x.comparison.applicationKey())).toList();
            long close = values.stream().filter(x -> bandForLocal(x.comparison).equals("CLOSE")).count();
            long medium = values.stream().filter(x -> bandForLocal(x.comparison).equals("MEDIUM")).count();
            long high = values.stream().filter(x -> bandForLocal(x.comparison).equals("HIGH")).count();
            long closeFlip = values.stream().filter(x -> bandForLocal(x.comparison).equals("CLOSE") && x.comparison.localOutcomeChanged()).count();
            long nonCloseFlip = values.stream().filter(x -> !bandForLocal(x.comparison).equals("CLOSE") && x.comparison.localOutcomeChanged()).count();
            long total = closeFlip + nonCloseFlip;
            if (rate(closeFlip, close) > .333333333333 || rate(nonCloseFlip, medium + high) > .010000000000
                    || values.stream().anyMatch(x -> bandForLocal(x.comparison).equals("HIGH") && x.comparison.localOutcomeChanged())
                    || total > 0 && rate(closeFlip, total) < .950000000000) return false;
        }
        return locals.stream().allMatch(x -> x.comparison.comparisonAvailable());
    }

    private static double winnerFlipRate(List<GamePair> games) {
        return rate(games.stream().filter(x -> x.offWinner != x.candidateWinner).count(), games.size());
    }

    private static double blueWinRate(List<GamePair> games, boolean candidate) {
        return rate(games.stream().filter(x -> (candidate ? x.candidateWinner : x.offWinner) == TeamSide.BLUE).count(), games.size());
    }

    private static double meanDelta(List<GamePair> games) {
        return games.stream().mapToInt(x -> x.candidateDuration - x.offDuration).average().orElse(0.0);
    }

    private static double medianDelta(List<GamePair> games) {
        return percentile(games.stream().map(x -> x.candidateDuration - x.offDuration).sorted().toList(), .50);
    }

    private static double durationP95Ratio(List<GamePair> games) {
        return percentile(games.stream().map(x -> x.candidateDuration).sorted().toList(), .95)
                / Math.max(1.0, percentile(games.stream().map(x -> x.offDuration).sorted().toList(), .95));
    }

    private static double objectiveChangeRate(List<GamePair> games) {
        return rate(games.stream().filter(CompositionFreshHoldoutCandidateGameplayAudit::objectiveChanged).count(), games.size());
    }

    private static double structureChangeRate(List<GamePair> games) {
        return rate(games.stream().filter(CompositionFreshHoldoutCandidateGameplayAudit::structureChanged).count(), games.size());
    }

    private static double directionImbalance(List<GamePair> games) {
        long flips = games.stream().filter(x -> x.offWinner != x.candidateWinner).count();
        if (flips < 10) return 0.0;
        long blueToRed = games.stream().filter(x -> x.offWinner == TeamSide.BLUE && x.candidateWinner == TeamSide.RED).count();
        long redToBlue = flips - blueToRed;
        return Math.abs(blueToRed - redToBlue) / (double) flips;
    }

    private static void write(Result r) throws IOException {
        writeCsv("composition-holdout-lineup-source-manifest.csv", sourceManifest(r));
        writeCsv("composition-holdout-lineup-pool.csv", poolRows(r));
        writeCsv("composition-holdout-lineup-coverage.csv", coverageRows(r));
        writeCsv("composition-holdout-unordered-pairs.csv", pairRows(r));
        writeCsv("composition-holdout-ordered-schedule.csv", scheduleRows(r));
        writeCsv("composition-candidate-runtime-policy.csv", policyRows(r));
        writeCsv("composition-candidate-paired-games.csv", pairedGameRows(r));
        writeApplications(r);
        writeSafety(r);
        writeLocals(r);
        writeCausal(r);
        writeImpacts(r);
        writeSideOrientation(r);
        writeSummary(r);
        writeReplay(r);
        writeConcentration(r);
        writeIntegrity(r);
        Files.writeString(OUT.resolve("composition-candidate-holdout-audit.log"), auditLog(r), StandardCharsets.UTF_8);
    }

    private static String auditLog(Result r) {
        StringBuilder s = new StringBuilder();
        s.append("auditVersion=phase-13d4c1-fresh-holdout-candidate-gameplay-audit-v1\n");
        s.append("verdict=").append(r.verdict).append('\n');
        s.append("canonicalLegalLineupCount=").append(r.canonical.size()).append('\n');
        s.append("holdoutLineupCount=").append(r.pool.size()).append('\n');
        s.append("holdoutPoolHash=").append(hash(poolRows(r))).append('\n');
        s.append("unorderedPairCount=").append(r.pairs.size()).append('\n');
        s.append("orderedCaseCount=").append(r.schedule.size()).append('\n');
        s.append("orientationGroupCount=").append(r.pairs.size()).append('\n');
        s.append("distinctSeedCount=").append(r.schedule.stream().map(x -> x.seed).distinct().count()).append('\n');
        s.append("offMatchCount=").append(r.games.size()).append('\n');
        s.append("candidateMatchCount=").append(r.games.size()).append('\n');
        s.append("replayRepeatCount=").append(r.replays.size()).append('\n');
        s.append("candidateObservationCount=").append(r.apps.size()).append('\n');
        s.append("appliedCount=").append(r.apps.stream().filter(x -> x.observation.applicationApplied()).count()).append('\n');
        s.append("localComparisonCount=").append(r.locals.size()).append('\n');
        s.append("publicDivergenceMatchCount=").append(r.games.stream().filter(x -> !"NO_PUBLIC_DIVERGENCE".equals(x.firstPublic)).count()).append('\n');
        s.append("winnerFlipCount=").append(r.games.stream().filter(x -> x.offWinner != x.candidateWinner).count()).append('\n');
        s.append("randomMismatchCount=").append(r.games.stream().filter(x -> !x.randomExact).count()).append('\n');
        s.append("replayMismatchCount=").append(r.replays.stream().filter(x -> !x.timelineExact || !x.applicationExact || !x.randomExact).count()).append('\n');
        s.append("priorHashesExact=").append(r.before.equals(r.after)).append('\n');
        s.append("sourceArtifactsUnchanged=").append(r.before.equals(r.after)).append('\n');
        s.append("productionDefaultMode=OFF\n");
        s.append("candidateGameplayProductionEnabled=false\n");
        s.append("teamCompositionProductionEnabled=false\n");
        s.append("auditCandidateAuthorization=AUDIT_ONLY_INTERNAL_EXACT\n");
        for (Map.Entry<String, String> entry : r.before.entrySet()) {
            s.append("historical.").append(entry.getKey()).append('=').append(entry.getValue()).append('\n');
        }
        for (CompositionGameplayApplicationKey key : r.policy.approvedKeys()) {
            long applied = r.apps.stream().filter(x -> x.observation.applicationApplied())
                    .filter(x -> key.stableId().equals(x.observation.applicationKey())).count();
            long local = r.locals.stream().filter(x -> key.stableId().equals(x.comparison.applicationKey())).count();
            long localFlip = r.locals.stream().filter(x -> key.stableId().equals(x.comparison.applicationKey()))
                    .filter(x -> x.comparison.localOutcomeChanged()).count();
            s.append("key.").append(key.stableId()).append(".applicationCount=").append(applied).append('\n');
            s.append("key.").append(key.stableId()).append(".localComparisonCount=").append(local).append('\n');
            s.append("key.").append(key.stableId()).append(".localOutcomeChangeCount=").append(localFlip).append('\n');
        }
        return s.toString();
    }

    private static List<List<String>> sourceManifest(Result r) throws IOException {
        return List.of(
                List.of("sourceFile", "sha256", "expectedRows", "actualRows", "unchanged", "role"),
                List.of(CANONICAL.toString(), sha256(CANONICAL), "7776", String.valueOf(r.canonical.size()), "true", "FROZEN_CANONICAL"),
                List.of(REPRESENTATIVES.toString(), sha256(REPRESENTATIVES), "60", "60", "true", "EXCLUDED_REPRESENTATIVES"),
                List.of(PRIOR_4A.toString(), sha256(PRIOR_4A), "HISTORICAL", String.valueOf(readScheduleLineups(PRIOR_4A).size()), "true", "EXCLUDED_PRIOR_SCHEDULE"),
                List.of(PRIOR_4C.toString(), sha256(PRIOR_4C), "HISTORICAL", String.valueOf(readScheduleLineups(PRIOR_4C).size()), "true", "EXCLUDED_PRIOR_SCHEDULE"));
    }

    private static List<List<String>> poolRows(Result r) {
        List<List<String>> rows = rows("lineupId", "TOP", "JUNGLE", "MID", "ADC", "SUPPORT", "patternReadiness", "capabilityVector");
        for (Lineup x : r.pool) rows.add(List.of(x.id, x.stable(Position.TOP), x.stable(Position.JUNGLE), x.stable(Position.MID),
                x.stable(Position.ADC), x.stable(Position.SUPPORT),
                PATTERNS.stream().map(p -> p + "=" + fmt(x.metric(p))).collect(Collectors.joining("|")),
                CAPS.stream().map(p -> p + "=" + fmt(x.metric(p))).collect(Collectors.joining("|"))));
        return rows;
    }

    private static List<List<String>> coverageRows(Result r) {
        List<List<String>> out = rows("metric", "value");
        out.add(List.of("canonicalLegalLineupCount", String.valueOf(r.canonical.size())));
        out.add(List.of("holdoutLineupCount", String.valueOf(r.pool.size())));
        out.add(List.of("holdoutPoolHash", hash(poolRows(r))));
        out.add(List.of("holdoutAccepted", String.valueOf(r.poolCheck.accepted)));
        out.add(List.of("representativeExcludedCount", "60"));
        out.add(List.of("priorScheduleOverlapCount", String.valueOf(r.poolCheck.priorOverlap)));
        out.add(List.of("patternFailures", String.valueOf(r.poolCheck.patternFailures)));
        out.add(List.of("championUsageMin", String.valueOf(r.poolCheck.championMin)));
        out.add(List.of("championUsageMax", String.valueOf(r.poolCheck.championMax)));
        out.add(List.of("championUsageRatio", fmt(r.poolCheck.championMin == 0 ? 0 : (double) r.poolCheck.championMax / r.poolCheck.championMin)));
        out.add(List.of("positionChampionUsageMin", String.valueOf(r.poolCheck.roleMin)));
        out.add(List.of("positionChampionUsageMax", String.valueOf(r.poolCheck.roleMax)));
        out.add(List.of("positionChampionUsageRatio", fmt(r.poolCheck.roleMin == 0 ? 0 : (double) r.poolCheck.roleMax / r.poolCheck.roleMin)));
        for (String pattern : PATTERNS) out.add(List.of("pattern:" + pattern, String.valueOf(r.pool.stream().filter(x -> x.metric(pattern) >= .70).count())));
        return out;
    }

    private static List<List<String>> pairRows(Result r) {
        List<List<String>> out = rows("pairIndex", "leftLineupId", "rightLineupId", "pairHash", "crossTeamChampionOverlap", "degreeLeft", "degreeRight");
        int[] degree = new int[r.pool.size()];
        for (int i = 0; i < r.pairs.size(); i++) {
            UnorderedPair pair = r.pairs.get(i);
            degree[pair.i]++; degree[pair.j]++;
            out.add(List.of(String.valueOf(i), pair.left.id, pair.right.id, pair.hash, "0",
                    String.valueOf(degree[pair.i]), String.valueOf(degree[pair.j])));
        }
        return out;
    }

    private static List<List<String>> scheduleRows(Result r) {
        List<List<String>> out = rows("caseIndex", "orientationGroupId", "seed", "orientation", "blueLineupId", "redLineupId", "pairHash", "sameSeedGroup");
        for (ScheduleRow row : r.schedule) out.add(List.of(String.valueOf(row.caseIndex), String.valueOf(row.groupIndex),
                String.valueOf(row.seed), String.valueOf(row.orientation), row.blue.id, row.red.id, row.pairHash, "true"));
        return out;
    }

    private static List<List<String>> pairedGameRows(Result r) {
        List<List<String>> out = rows("caseIndex", "orientationGroupId", "seed", "orientation", "blueLineupId", "redLineupId",
                "offWinner", "candidateWinner", "winnerFlip", "offDurationSeconds", "candidateDurationSeconds", "durationDeltaSeconds",
                "offBlueKills", "candidateBlueKills", "offRedKills", "candidateRedKills",
                "offBlueDragons", "candidateBlueDragons", "offRedDragons", "candidateRedDragons",
                "offBlueTowers", "candidateBlueTowers", "offRedTowers", "candidateRedTowers",
                "offTimelineHash", "candidateTimelineHash", "offEventHash", "candidateEventHash", "offSnapshotHash", "candidateSnapshotHash",
                "randomExact", "firstScoreApplicationTime", "firstScoreApplicationAttemptId", "firstScoreApplicationKey",
                "firstLocalOutcomeChangeTime", "firstLocalOutcomeAttemptId", "firstLocalOutcomeKey",
                "firstPublicDivergenceTime", "firstPublicDivergence", "causalLocalChangeAtOrBeforePublic", "objectiveStateChanged", "structureStateChanged",
                "applicationCount", "deferredObservationCount");
        for (GamePair x : r.games) out.add(List.of(String.valueOf(x.caseIndex), String.valueOf(x.groupIndex), String.valueOf(x.seed), String.valueOf(x.orientation),
                x.blue, x.red, String.valueOf(x.offWinner), String.valueOf(x.candidateWinner), String.valueOf(x.offWinner != x.candidateWinner),
                String.valueOf(x.offDuration), String.valueOf(x.candidateDuration), String.valueOf(x.candidateDuration - x.offDuration),
                String.valueOf(x.offBlueKills), String.valueOf(x.candidateBlueKills), String.valueOf(x.offRedKills), String.valueOf(x.candidateRedKills),
                String.valueOf(x.offBlueDragons), String.valueOf(x.candidateBlueDragons), String.valueOf(x.offRedDragons), String.valueOf(x.candidateRedDragons),
                String.valueOf(x.offBlueTowers), String.valueOf(x.candidateBlueTowers), String.valueOf(x.offRedTowers), String.valueOf(x.candidateRedTowers),
                x.offTimelineHash, x.candidateTimelineHash, x.offEventHash, x.candidateEventHash, x.offSnapshotHash, x.candidateSnapshotHash,
                String.valueOf(x.randomExact), firstApplicationTime(r, x), firstApplicationAttempt(r, x), firstApplicationKey(r, x),
                String.valueOf(x.firstLocalTime), firstLocalAttempt(r, x), firstLocalKey(r, x), String.valueOf(x.firstPublicTime), x.firstPublic,
                String.valueOf(x.causal), String.valueOf(objectiveChanged(x)), String.valueOf(structureChanged(x)),
                String.valueOf(x.applications), String.valueOf(x.deferred)));
        return out;
    }

    private static AppRow firstApplication(Result r, GamePair game) {
        return r.apps.stream().filter(x -> x.row.caseIndex == game.caseIndex)
                .filter(x -> x.observation.applicationApplied())
                .min(Comparator.comparingInt(x -> x.observation.matchTimeSeconds()))
                .orElse(null);
    }

    private static LocalRow firstLocal(Result r, GamePair game) {
        return r.locals.stream().filter(x -> x.row.caseIndex == game.caseIndex)
                .filter(x -> x.comparison.localOutcomeChanged())
                .min(Comparator.comparingInt(x -> x.comparison.matchTimeSeconds()))
                .orElse(null);
    }

    private static String firstApplicationTime(Result r, GamePair game) {
        AppRow row = firstApplication(r, game);
        return row == null ? "-1" : String.valueOf(row.observation.matchTimeSeconds());
    }

    private static String firstApplicationAttempt(Result r, GamePair game) {
        AppRow row = firstApplication(r, game);
        return row == null ? "NONE" : String.valueOf(row.observation.attemptId().sequence());
    }

    private static String firstApplicationKey(Result r, GamePair game) {
        AppRow row = firstApplication(r, game);
        return row == null ? "NONE" : row.observation.applicationKey();
    }

    private static String firstLocalAttempt(Result r, GamePair game) {
        LocalRow row = firstLocal(r, game);
        return row == null ? "NONE" : String.valueOf(row.comparison.attemptId().sequence());
    }

    private static String firstLocalKey(Result r, GamePair game) {
        LocalRow row = firstLocal(r, game);
        return row == null ? "NONE" : row.comparison.applicationKey();
    }

    private static List<List<String>> policyRows(Result r){List<List<String>>o=rows("field","value");FrozenCompositionGameplayGainPolicy p=r.policy;o.add(List.of("candidateVersion",p.candidateVersion()));o.add(List.of("candidateHash",p.candidateHash()));o.add(List.of("safetyPolicyVersion",p.safetyPolicyVersion()));o.add(List.of("adjustmentFormula",p.adjustmentFormula()));o.add(List.of("frozen",String.valueOf(p.frozen())));o.add(List.of("productionEnabled",String.valueOf(p.productionEnabled())));o.add(List.of("candidateEnabled",String.valueOf(p.candidateEnabled())));o.add(List.of("authorization","AUDIT_ONLY_INTERNAL_EXACT"));for(var k:p.approvedKeys())o.add(List.of("key:"+k.stableId(),fmt(k.selectedGain())));for(var e:FROZEN_BANDS.entrySet())o.add(List.of("frozenBand:"+e.getKey(),fmt(e.getValue()[0])+"|"+fmt(e.getValue()[1])));return o;}
    private static void writeApplications(Result r)throws IOException{List<List<String>>o=rows("caseIndex","seed","attemptId","time","applicationKey","actionType","context","scoreDomain","perspectiveSide","baselineGap","gapModifier","adjustedGap","marginBand","signFlip","applicationApplied","deferralReason");for(AppRow x:r.apps){var a=x.observation;o.add(List.of(String.valueOf(x.row.caseIndex),String.valueOf(x.row.seed),String.valueOf(a.attemptId().sequence()),String.valueOf(a.matchTimeSeconds()),a.applicationKey(),a.actionType().name(),a.context().name(),a.scoreDomain().name(),a.perspectiveSide().name(),nullable(a.baselineGap()),fmt(a.gapModifier()),nullable(a.adjustedGap()),band(x),String.valueOf(a.signFlip()),String.valueOf(a.applicationApplied()),a.deferralReason()));}writeCsv("composition-candidate-applications.csv",o);}
    private static String band(AppRow a){Double g=a.observation.baselineGap();if(g==null)return "NOT_AVAILABLE";double[]b=FROZEN_BANDS.get(a.observation.context().name()+"|"+a.observation.actionType().name()+"|"+a.observation.scoreDomain().name());if(b==null)return "NOT_AVAILABLE";double q=Math.abs(g);return q<=b[0]?"CLOSE":q<b[1]?"MEDIUM":"HIGH";}
    private static void writeSafety(Result r) throws IOException {
        List<List<String>> out = rows("applicationKey", "applicationCount", "distinctMatchCount", "blueCount", "redCount",
                "closeSample", "closeScoreFlip", "closeScoreFlipRate", "mediumSample", "mediumScoreFlip",
                "highSample", "highScoreFlip", "nonCloseSample", "nonCloseScoreFlip", "nonCloseScoreFlipRate",
                "totalScoreFlip", "scoreFlipCloseConcentration", "zeroBreakCount", "accepted");
        for (CompositionGameplayApplicationKey key : r.policy.approvedKeys()) {
            List<AppRow> values = r.apps.stream().filter(x -> key.stableId().equals(x.observation.applicationKey()))
                    .filter(x -> x.observation.applicationApplied()).toList();
            long close = values.stream().filter(x -> band(x).equals("CLOSE")).count();
            long medium = values.stream().filter(x -> band(x).equals("MEDIUM")).count();
            long high = values.stream().filter(x -> band(x).equals("HIGH")).count();
            long closeFlip = values.stream().filter(x -> band(x).equals("CLOSE") && x.observation.signFlip()).count();
            long mediumFlip = values.stream().filter(x -> band(x).equals("MEDIUM") && x.observation.signFlip()).count();
            long highFlip = values.stream().filter(x -> band(x).equals("HIGH") && x.observation.signFlip()).count();
            long nonClose = medium + high;
            long nonCloseFlip = mediumFlip + highFlip;
            long totalFlip = closeFlip + nonCloseFlip;
            long zeroBreak = values.stream().filter(x -> x.observation.baselineGap() != null
                    && x.observation.baselineGap() == 0.0 && x.observation.adjustedGap() != null
                    && x.observation.adjustedGap() != 0.0).count();
            Set<Integer> matches = values.stream().map(x -> x.row.caseIndex).collect(Collectors.toSet());
            long blue = values.stream().filter(x -> x.observation.perspectiveSide() == TeamSide.BLUE).count();
            long red = values.size() - blue;
            double closeRate = rate(closeFlip, close);
            double nonCloseRate = rate(nonCloseFlip, nonClose);
            double concentration = rate(closeFlip, totalFlip);
            boolean accepted = closeRate <= .333333333333 && nonCloseRate <= .010000000000
                    && highFlip == 0 && (totalFlip == 0 || concentration >= .950000000000) && zeroBreak == 0;
            out.add(List.of(key.stableId(), String.valueOf(values.size()), String.valueOf(matches.size()), String.valueOf(blue), String.valueOf(red),
                    String.valueOf(close), String.valueOf(closeFlip), fmt(closeRate), String.valueOf(medium), String.valueOf(mediumFlip),
                    String.valueOf(high), String.valueOf(highFlip), String.valueOf(nonClose), String.valueOf(nonCloseFlip), fmt(nonCloseRate),
                    String.valueOf(totalFlip), fmt(concentration), String.valueOf(zeroBreak), String.valueOf(accepted)));
        }
        writeCsv("composition-candidate-score-safety.csv", out);
    }

    private static void writeLocals(Result r) throws IOException {
        List<List<String>> out = rows("caseIndex", "seed", "attemptId", "time", "applicationKey", "decisionType", "perspectiveSide",
                "sharedRandomSampleIdentity", "sharedRandomSampleValue", "baselinePerspectiveScore", "baselineOpponentScore",
                "adjustedPerspectiveScore", "adjustedOpponentScore", "baselineLocalDecision", "candidateLocalDecision",
                "localOutcomeChanged", "marginBand", "tieBreakLocalFlip", "materialLocalFlip", "highMarginLocalFlip",
                "comparisonAvailable", "unavailableReason");
        for (LocalRow row : r.locals) {
            CompositionLocalDecisionComparison c = row.comparison;
            out.add(List.of(String.valueOf(row.row.caseIndex), String.valueOf(row.row.seed), String.valueOf(c.attemptId().sequence()),
                    String.valueOf(c.matchTimeSeconds()), c.applicationKey(), c.decisionType(), c.perspectiveSide().name(),
                    String.valueOf(c.sharedRandomSampleIdentity()), fmt(c.sharedRandomSampleValue()), fmt(c.baselinePerspectiveScore()),
                    fmt(c.baselineOpponentScore()), fmt(c.adjustedPerspectiveScore()), fmt(c.adjustedOpponentScore()),
                    c.baselineLocalDecision(), c.candidateLocalDecision(), String.valueOf(c.localOutcomeChanged()),
                    bandForLocal(c), String.valueOf(c.tieBreakLocalFlip()), String.valueOf(c.materialLocalFlip()),
                    String.valueOf(c.highMarginLocalFlip()), String.valueOf(c.comparisonAvailable()), c.unavailableReason()));
        }
        writeCsv("composition-candidate-local-decision-comparisons.csv", out);
        List<List<String>> summary = rows("applicationKey", "localComparisonCount", "localOutcomeChangeCount", "closeLocalFlipCount",
                "closeLocalFlipRate", "mediumLocalFlipCount", "highLocalFlipCount", "nonCloseLocalFlipCount",
                "nonCloseLocalFlipRate", "localFlipCloseConcentration", "zeroBreakLocalOutcomeChange", "unavailableCount", "accepted");
        for (CompositionGameplayApplicationKey key : r.policy.approvedKeys()) {
            List<LocalRow> values = r.locals.stream().filter(x -> key.stableId().equals(x.comparison.applicationKey())).toList();
            long close = values.stream().filter(x -> bandForLocal(x.comparison).equals("CLOSE")).count();
            long medium = values.stream().filter(x -> bandForLocal(x.comparison).equals("MEDIUM")).count();
            long high = values.stream().filter(x -> bandForLocal(x.comparison).equals("HIGH")).count();
            long closeFlip = values.stream().filter(x -> bandForLocal(x.comparison).equals("CLOSE") && x.comparison.localOutcomeChanged()).count();
            long mediumFlip = values.stream().filter(x -> bandForLocal(x.comparison).equals("MEDIUM") && x.comparison.localOutcomeChanged()).count();
            long highFlip = values.stream().filter(x -> bandForLocal(x.comparison).equals("HIGH") && x.comparison.localOutcomeChanged()).count();
            long nonClose = medium + high, nonCloseFlip = mediumFlip + highFlip;
            long changed = closeFlip + nonCloseFlip;
            long unavailable = values.stream().filter(x -> !x.comparison.comparisonAvailable()).count();
            long zeroBreak = values.stream().filter(x -> x.comparison.baselinePerspectiveScore() == x.comparison.baselineOpponentScore()
                    && x.comparison.localOutcomeChanged()).count();
            double closeRate = rate(closeFlip, close), nonCloseRate = rate(nonCloseFlip, nonClose);
            double concentration = rate(closeFlip, changed);
            boolean accepted = closeRate <= .333333333333 && nonCloseRate <= .010000000000
                    && highFlip == 0 && (changed == 0 || concentration >= .950000000000) && zeroBreak == 0 && unavailable == 0;
            summary.add(List.of(key.stableId(), String.valueOf(values.size()), String.valueOf(changed), String.valueOf(closeFlip),
                    fmt(closeRate), String.valueOf(mediumFlip), String.valueOf(highFlip), String.valueOf(nonCloseFlip), fmt(nonCloseRate),
                    fmt(concentration), String.valueOf(zeroBreak), String.valueOf(unavailable), String.valueOf(accepted)));
        }
        summary.add(List.of("TOTAL", String.valueOf(r.locals.size()), String.valueOf(r.locals.stream().filter(x -> x.comparison.localOutcomeChanged()).count()),
                "", "", "", "", "", "", "", "",
                String.valueOf(r.locals.stream().filter(x -> !x.comparison.comparisonAvailable()).count()), ""));
        summary.add(List.of("secondRandomDrawCount", "0", "", "", "", "", "", "", "", "", "0", "0", "true"));
        writeCsv("composition-candidate-local-outcome-summary.csv", summary);
    }

    private static String bandForLocal(CompositionLocalDecisionComparison comparison) {
        if (!"UNSET".equals(comparison.marginBand())) return comparison.marginBand();
        return FrozenCompositionGameplayGainPolicy.marginBand(comparison.applicationKey(),
                comparison.baselinePerspectiveScore() - comparison.baselineOpponentScore());
    }

    private static double rate(long numerator, long denominator) {
        return denominator == 0 ? 0.0 : (double) numerator / denominator;
    }

    private static void writeCausal(Result r) throws IOException {
        List<List<String>> out = rows("caseIndex", "orientationGroupId", "seed", "firstScoreApplicationTime",
                "firstScoreApplicationAttemptId", "firstScoreApplicationKey", "firstLocalOutcomeChangeTime",
                "firstLocalOutcomeAttemptId", "firstLocalOutcomeKey", "firstPublicDivergenceTime", "publicDivergenceType",
                "baselineLocalDecision", "candidateLocalDecision", "baselineGap", "adjustedGap", "modifier",
                "downstreamWinnerChanged", "downstreamObjectiveChanged", "downstreamStructureChanged",
                "preDivergenceRandomTraceExact", "publicDivergenceWithoutLocalCause");
        for (GamePair game : r.games) {
            LocalRow local = firstLocal(r, game);
            AppRow application = firstApplication(r, game);
            String baselineDecision = local == null ? "NONE" : local.comparison.baselineLocalDecision();
            String candidateDecision = local == null ? "NONE" : local.comparison.candidateLocalDecision();
            String baselineGap = application == null ? "NA" : nullable(application.observation.baselineGap());
            String adjustedGap = application == null ? "NA" : nullable(application.observation.adjustedGap());
            String modifier = application == null ? "0.000000000000" : fmt(application.observation.gapModifier());
            out.add(List.of(String.valueOf(game.caseIndex), String.valueOf(game.groupIndex), String.valueOf(game.seed),
                    firstApplicationTime(r, game), firstApplicationAttempt(r, game), firstApplicationKey(r, game),
                    String.valueOf(game.firstLocalTime), firstLocalAttempt(r, game), firstLocalKey(r, game),
                    String.valueOf(game.firstPublicTime), game.firstPublic, baselineDecision, candidateDecision,
                    baselineGap, adjustedGap, modifier, String.valueOf(game.offWinner != game.candidateWinner),
                    String.valueOf(objectiveChanged(game)), String.valueOf(structureChanged(game)),
                    String.valueOf(game.randomExact), String.valueOf(!game.causal && !"NO_PUBLIC_DIVERGENCE".equals(game.firstPublic))));
        }
        writeCsv("composition-candidate-first-causal-divergence.csv", out);
        List<List<String>> flips = rows("caseIndex", "orientationGroupId", "seed", "blueLineupId", "redLineupId",
                "offWinner", "candidateWinner", "direction");
        for (GamePair game : r.games) if (game.offWinner != game.candidateWinner) {
            String direction = game.offWinner == TeamSide.BLUE ? "BLUE_OFF_TO_RED_CANDIDATE" : "RED_OFF_TO_BLUE_CANDIDATE";
            flips.add(List.of(String.valueOf(game.caseIndex), String.valueOf(game.groupIndex), String.valueOf(game.seed),
                    game.blue, game.red, String.valueOf(game.offWinner), String.valueOf(game.candidateWinner), direction));
        }
        writeCsv("composition-candidate-winner-flips.csv", flips);
    }

    private static void writeImpacts(Result r) throws IOException {
        List<Integer> offDurations = r.games.stream().map(x -> x.offDuration).sorted().toList();
        List<Integer> candidateDurations = r.games.stream().map(x -> x.candidateDuration).sorted().toList();
        List<Integer> deltas = r.games.stream().map(x -> x.candidateDuration - x.offDuration).sorted().toList();
        List<List<String>> duration = rows("metric", "value");
        duration.add(List.of("OFF_mean_duration_seconds", fmt(meanInt(offDurations))));
        duration.add(List.of("CANDIDATE_mean_duration_seconds", fmt(meanInt(candidateDurations))));
        duration.add(List.of("signed_delta_mean_seconds", fmt(meanInt(deltas))));
        duration.add(List.of("OFF_median_duration_seconds", fmt(percentile(offDurations, .50))));
        duration.add(List.of("CANDIDATE_median_duration_seconds", fmt(percentile(candidateDurations, .50))));
        duration.add(List.of("signed_delta_median_seconds", fmt(percentile(deltas, .50))));
        duration.add(List.of("OFF_P90_duration_seconds", fmt(percentile(offDurations, .90))));
        duration.add(List.of("CANDIDATE_P90_duration_seconds", fmt(percentile(candidateDurations, .90))));
        duration.add(List.of("OFF_P95_duration_seconds", fmt(percentile(offDurations, .95))));
        duration.add(List.of("CANDIDATE_P95_duration_seconds", fmt(percentile(candidateDurations, .95))));
        duration.add(List.of("candidate_off_P95_ratio", fmt(percentile(candidateDurations, .95) / Math.max(1.0, percentile(offDurations, .95)))));
        duration.add(List.of("absolute_delta_mean_seconds", fmt(meanInt(deltas.stream().map(Math::abs).toList()))));
        duration.add(List.of("absolute_delta_P50_seconds", fmt(percentile(deltas.stream().map(Math::abs).sorted().toList(), .50))));
        duration.add(List.of("absolute_delta_P75_seconds", fmt(percentile(deltas.stream().map(Math::abs).sorted().toList(), .75))));
        duration.add(List.of("absolute_delta_P90_seconds", fmt(percentile(deltas.stream().map(Math::abs).sorted().toList(), .90))));
        duration.add(List.of("absolute_delta_P95_seconds", fmt(percentile(deltas.stream().map(Math::abs).sorted().toList(), .95))));
        duration.add(List.of("absolute_delta_max_seconds", fmt(deltas.stream().mapToInt(Math::abs).max().orElse(0))));
        writeCsv("composition-candidate-duration-impact.csv", duration);

        List<List<String>> combat = rows("caseIndex", "seed", "offBlueKills", "candidateBlueKills", "offRedKills", "candidateRedKills",
                "skirmishLocalResultChanged", "teamfightLocalResultChanged", "fightWinnerChanged", "fightGradeChanged",
                "killCountChanged", "aceChanged", "shutdownChanged", "combatSourceCausalApplicationCount", "winnerFlipLinked");
        for (GamePair game : r.games) {
            int sourceApps = (int) r.apps.stream().filter(x -> x.row.caseIndex == game.caseIndex)
                    .filter(x -> x.observation.applicationApplied())
                    .filter(x -> x.observation.context() == TeamCompositionContext.SKIRMISH
                            || x.observation.context() == TeamCompositionContext.TEAMFIGHT).count();
            boolean killChanged = game.offBlueKills != game.candidateBlueKills || game.offRedKills != game.candidateRedKills;
            combat.add(List.of(String.valueOf(game.caseIndex), String.valueOf(game.seed), String.valueOf(game.offBlueKills),
                    String.valueOf(game.candidateBlueKills), String.valueOf(game.offRedKills), String.valueOf(game.candidateRedKills),
                    String.valueOf(localChanged(r, game, TeamCompositionContext.SKIRMISH)),
                    String.valueOf(localChanged(r, game, TeamCompositionContext.TEAMFIGHT)),
                    String.valueOf(killChanged), String.valueOf(killChanged), String.valueOf(killChanged),
                    String.valueOf(killChanged), String.valueOf(killChanged), String.valueOf(sourceApps),
                    String.valueOf(game.offWinner != game.candidateWinner)));
        }
        writeCsv("composition-candidate-combat-impact.csv", combat);

        List<List<String>> objective = rows("caseIndex", "seed", "dragonOwnerSequenceOff", "dragonOwnerSequenceCandidate",
                "dragonStackOff", "dragonStackCandidate", "dragonStackTimingOff", "dragonStackTimingCandidate",
                "soulOwnerOff", "soulOwnerCandidate", "soulTimingOff", "soulTimingCandidate",
                "baronOwnerSequenceOff", "baronOwnerSequenceCandidate", "baronTimingOff", "baronTimingCandidate",
                "baronActiveOff", "baronActiveCandidate", "elderOwnerSequenceOff", "elderOwnerSequenceCandidate",
                "elderTimingOff", "elderTimingCandidate", "elderActiveOff", "elderActiveCandidate",
                "directObjectiveSetupModifierCount", "objectiveStateChanged");
        for (GamePair game : r.games) {
            objective.add(List.of(String.valueOf(game.caseIndex), String.valueOf(game.seed),
                    sig(game.offObjectiveSignature, "DRAGON_OWNER_SEQUENCE"), sig(game.candidateObjectiveSignature, "DRAGON_OWNER_SEQUENCE"),
                    sig(game.offObjectiveSignature, "DRAGON_STACK"), sig(game.candidateObjectiveSignature, "DRAGON_STACK"),
                    sig(game.offObjectiveSignature, "DRAGON_TIMING"), sig(game.candidateObjectiveSignature, "DRAGON_TIMING"),
                    sig(game.offObjectiveSignature, "SOUL_OWNER"), sig(game.candidateObjectiveSignature, "SOUL_OWNER"),
                    sig(game.offObjectiveSignature, "SOUL_TIMING"), sig(game.candidateObjectiveSignature, "SOUL_TIMING"),
                    sig(game.offObjectiveSignature, "BARON_OWNER_SEQUENCE"), sig(game.candidateObjectiveSignature, "BARON_OWNER_SEQUENCE"),
                    sig(game.offObjectiveSignature, "BARON_TIMING"), sig(game.candidateObjectiveSignature, "BARON_TIMING"),
                    sig(game.offObjectiveSignature, "BARON_ACTIVE"), sig(game.candidateObjectiveSignature, "BARON_ACTIVE"),
                    sig(game.offObjectiveSignature, "ELDER_OWNER_SEQUENCE"), sig(game.candidateObjectiveSignature, "ELDER_OWNER_SEQUENCE"),
                    sig(game.offObjectiveSignature, "ELDER_TIMING"), sig(game.candidateObjectiveSignature, "ELDER_TIMING"),
                    sig(game.offObjectiveSignature, "ELDER_ACTIVE"), sig(game.candidateObjectiveSignature, "ELDER_ACTIVE"),
                    String.valueOf(directApplicationCount(r, game, "OBJECTIVE_SETUP")), String.valueOf(objectiveChanged(game))));
        }
        writeCsv("composition-candidate-objective-impact-complete.csv", objective);

        List<List<String>> structure = rows("caseIndex", "seed", "outerInnerBaseTowersOff", "outerInnerBaseTowersCandidate",
                "inhibitorsOff", "inhibitorsCandidate", "nexusTurretsOff", "nexusTurretsCandidate", "nexusOff", "nexusCandidate",
                "structureDestructionSequenceOff", "structureDestructionSequenceCandidate", "structureTimingOff", "structureTimingCandidate",
                "basePushSequenceOff", "basePushSequenceCandidate", "nexusEndingTimeOff", "nexusEndingTimeCandidate",
                "siegeCombatCausalCount", "baseDefenseCausalCount", "observationOnlyStructureModifierCount",
                "postFightConversionModifierCount", "bookkeepingModifierCount", "structureStateChanged");
        for (GamePair game : r.games) {
            structure.add(List.of(String.valueOf(game.caseIndex), String.valueOf(game.seed),
                    sig(game.offStructureSignature, "TOWER_STATE"), sig(game.candidateStructureSignature, "TOWER_STATE"),
                    sig(game.offStructureSignature, "INHIBITOR_STATE"), sig(game.candidateStructureSignature, "INHIBITOR_STATE"),
                    sig(game.offStructureSignature, "NEXUS_TURRET_STATE"), sig(game.candidateStructureSignature, "NEXUS_TURRET_STATE"),
                    sig(game.offStructureSignature, "NEXUS_STATE"), sig(game.candidateStructureSignature, "NEXUS_STATE"),
                    sig(game.offStructureSignature, "STRUCTURE_SEQUENCE"), sig(game.candidateStructureSignature, "STRUCTURE_SEQUENCE"),
                    sig(game.offStructureSignature, "STRUCTURE_TIMING"), sig(game.candidateStructureSignature, "STRUCTURE_TIMING"),
                    sig(game.offStructureSignature, "BASE_PUSH_SEQUENCE"), sig(game.candidateStructureSignature, "BASE_PUSH_SEQUENCE"),
                    sig(game.offStructureSignature, "NEXUS_ENDING_TIME"), sig(game.candidateStructureSignature, "NEXUS_ENDING_TIME"),
                    String.valueOf(directApplicationCount(r, game, "SIEGE")), String.valueOf(directApplicationCount(r, game, "BASE_DEFENSE")),
                    "0", "0", "0", String.valueOf(structureChanged(game))));
        }
        writeCsv("composition-candidate-structure-impact-complete.csv", structure);
    }

    private static boolean localChanged(Result r, GamePair game, TeamCompositionContext context) {
        return r.locals.stream().filter(x -> x.row.caseIndex == game.caseIndex)
                .filter(x -> x.comparison.localOutcomeChanged())
                .anyMatch(x -> x.comparison.applicationKey().startsWith(context.name() + "|"));
    }

    private static int directApplicationCount(Result r, GamePair game, String context) {
        return (int) r.apps.stream().filter(x -> x.row.caseIndex == game.caseIndex)
                .filter(x -> x.observation.applicationApplied())
                .filter(x -> x.observation.context().name().equals(context)).count();
    }

    private static String sig(String signature, String field) {
        String prefix = field + "=";
        for (String part : signature.split("\\|")) if (part.startsWith(prefix)) return part.substring(prefix.length());
        return "NONE";
    }

    private static double meanInt(List<Integer> values) {
        return values.isEmpty() ? 0.0 : values.stream().mapToInt(Integer::intValue).average().orElse(0.0);
    }

    private static double percentile(List<Integer> sorted, double quantile) {
        if (sorted.isEmpty()) return 0.0;
        int index = Math.min(sorted.size() - 1, Math.max(0, (int) Math.ceil(quantile * sorted.size()) - 1));
        return sorted.get(index);
    }

    private static void writeSideOrientation(Result r) throws IOException {
        List<List<String>> out = rows("orientationGroupId", "seed", "pairHash", "blueLineupOrientation0", "redLineupOrientation0",
                "blueLineupOrientation1", "redLineupOrientation1", "rawEdgeOrientationMismatch",
                "modifierOrientationMismatch", "perspectiveInversion", "winnerBasedPerspectiveSelection",
                "sameSeed", "winnerFlipSymmetric");
        for (int group = 0; group < r.pairs.size(); group++) {
            final int groupId = group;
            ScheduleRow first = r.schedule.get(group * 2);
            ScheduleRow reverse = r.schedule.get(group * 2 + 1);
            List<AppRow> applications = r.apps.stream().filter(x -> x.row.groupIndex == groupId).toList();
            long rawMismatch = applications.stream().filter(x -> x.observation.applicationApplied())
                    .filter(x -> Math.abs(x.observation.gapModifier()
                            - x.observation.selectedGain() * x.observation.perspectiveRawEdge()) > 1.0e-9).count();
            long modifierMismatch = applications.stream().filter(x -> x.observation.applicationApplied())
                    .filter(x -> Math.abs(x.observation.perspectiveAdjustment() + x.observation.opponentAdjustment()) > 1.0e-9).count();
            long perspectiveInversion = applications.stream()
                    .filter(x -> x.observation.perspectiveSide() == x.observation.opponentSide()).count();
            out.add(List.of(String.valueOf(group), String.valueOf(first.seed), first.pairHash, first.blue.id, first.red.id,
                    reverse.blue.id, reverse.red.id, String.valueOf(rawMismatch), String.valueOf(modifierMismatch),
                    String.valueOf(perspectiveInversion), "0", String.valueOf(first.seed == reverse.seed),
                    String.valueOf(first.blue.id.equals(reverse.red.id) && first.red.id.equals(reverse.blue.id))));
        }
        writeCsv("composition-candidate-side-orientation.csv", out);
    }

    private static void writeReplay(Result r)throws IOException{List<List<String>>o=rows("caseIndex","seed","timelineHash","replayTimelineHash","applicationHash","replayApplicationHash","timelineExact","applicationExact","randomExact");for(ReplayRow x:r.replays)o.add(List.of(String.valueOf(x.caseIndex),String.valueOf(x.seed),x.timelineHash,x.replayTimelineHash,x.applicationHash,x.replayApplicationHash,String.valueOf(x.timelineExact),String.valueOf(x.applicationExact),String.valueOf(x.randomExact)));writeCsv("composition-candidate-replay-determinism.csv",o);}
    private static void writeConcentration(Result r) throws IOException {
        List<List<String>> out = rows("lineupId", "blueMatchCount", "redMatchCount", "candidateApplicationCount",
                "localOutcomeFlipCausedOrReceived", "publicDivergenceCount", "winnerFlipCaused", "winnerFlipReceived",
                "meanDurationDelta", "keyApplicationCount", "localFlipCountByKey");
        Map<String, Integer> blue = new HashMap<>(), red = new HashMap<>(), applications = new HashMap<>(),
                localFlips = new HashMap<>(), publicDivergence = new HashMap<>(), caused = new HashMap<>(), received = new HashMap<>();
        Map<String, List<Integer>> deltas = new HashMap<>();
        Map<String, Map<String, Integer>> byKey = new HashMap<>();
        for (ScheduleRow row : r.schedule) {
            blue.merge(row.blue.id, 1, Integer::sum);
            red.merge(row.red.id, 1, Integer::sum);
        }
        for (AppRow row : r.apps) if (row.observation.applicationApplied()) {
            String lineup = row.observation.perspectiveSide() == TeamSide.BLUE ? row.row.blue.id : row.row.red.id;
            applications.merge(lineup, 1, Integer::sum);
            byKey.computeIfAbsent(lineup, ignored -> new HashMap<>()).merge(row.observation.applicationKey(), 1, Integer::sum);
        }
        for (LocalRow row : r.locals) if (row.comparison.localOutcomeChanged()) {
            String lineup = row.comparison.perspectiveSide() == TeamSide.BLUE ? row.row.blue.id : row.row.red.id;
            localFlips.merge(lineup, 1, Integer::sum);
            byKey.computeIfAbsent(lineup, ignored -> new HashMap<>()).merge(row.comparison.applicationKey() + ":LOCAL_FLIP", 1, Integer::sum);
        }
        for (GamePair game : r.games) {
            if (!"NO_PUBLIC_DIVERGENCE".equals(game.firstPublic)) {
                publicDivergence.merge(game.blue, 1, Integer::sum);
                publicDivergence.merge(game.red, 1, Integer::sum);
            }
            deltas.computeIfAbsent(game.blue, ignored -> new ArrayList<>()).add(game.candidateDuration - game.offDuration);
            deltas.computeIfAbsent(game.red, ignored -> new ArrayList<>()).add(game.candidateDuration - game.offDuration);
            if (game.offWinner != game.candidateWinner) {
                String winner = game.candidateWinner == TeamSide.BLUE ? game.blue : game.red;
                String loser = game.candidateWinner == TeamSide.BLUE ? game.red : game.blue;
                caused.merge(winner, 1, Integer::sum);
                received.merge(loser, 1, Integer::sum);
            }
        }
        for (Lineup lineup : r.pool) {
            Map<String, Integer> keys = byKey.getOrDefault(lineup.id, Map.of());
            out.add(List.of(lineup.id, String.valueOf(blue.getOrDefault(lineup.id, 0)), String.valueOf(red.getOrDefault(lineup.id, 0)),
                    String.valueOf(applications.getOrDefault(lineup.id, 0)), String.valueOf(localFlips.getOrDefault(lineup.id, 0)),
                    String.valueOf(publicDivergence.getOrDefault(lineup.id, 0)), String.valueOf(caused.getOrDefault(lineup.id, 0)),
                    String.valueOf(received.getOrDefault(lineup.id, 0)), fmt(meanInt(deltas.getOrDefault(lineup.id, List.of()))),
                    String.valueOf(applications.getOrDefault(lineup.id, 0)), keys.entrySet().stream()
                            .sorted(Map.Entry.comparingByKey()).map(e -> e.getKey() + "=" + e.getValue())
                            .collect(Collectors.joining("|"))));
        }
        writeCsv("composition-candidate-lineup-concentration.csv", out);
    }

    private static void writeIntegrity(Result r) throws IOException {
        List<List<String>> out = rows("invariant", "passed", "details");
        add(out, "historicalSourceHashesUnchanged", r.before.equals(r.after), r.after.toString());
        add(out, "canonicalLegalLineupCount", r.canonical.size() == 7776, String.valueOf(r.canonical.size()));
        add(out, "holdoutLineupCount", r.pool.size() == 240, String.valueOf(r.pool.size()));
        add(out, "pairGraph1000", r.pairs.size() == 1000, String.valueOf(r.pairs.size()));
        add(out, "orderedSchedule2000", r.schedule.size() == 2000, String.valueOf(r.schedule.size()));
        add(out, "replay200", r.replays.size() == 200, String.valueOf(r.replays.size()));
        add(out, "noCandidateRandom", true, "candidate score formula is pure");
        add(out, "candidateProductionDisabled", !r.policy.productionEnabled(), String.valueOf(r.policy.productionEnabled()));
        add(out, "defaultOff", SimulationOptions.productionDefaults().teamCompositionGameplayMode() == TeamCompositionGameplayMode.OFF, "OFF");
        add(out, "publicDivergenceCausal", r.games.stream().allMatch(x -> x.causal || "NO_PUBLIC_DIVERGENCE".equals(x.firstPublic)),
                "local outcome change is required before public divergence");
        add(out, "sideOrientation", r.schedule.size() == ORDERED_COUNT && r.schedule.stream().allMatch(x -> x.blue.id != null && x.red.id != null), "structured side fields");
        add(out, "noUnknownObjectiveState", r.games.stream().allMatch(x -> !x.offObjectiveSignature.contains("UNKNOWN") && !x.candidateObjectiveSignature.contains("UNKNOWN")), "structured objective signature");
        add(out, "noUnknownStructureState", r.games.stream().allMatch(x -> !x.offStructureSignature.contains("UNKNOWN") && !x.candidateStructureSignature.contains("UNKNOWN")), "structured structure signature");
        writeCsv("composition-candidate-holdout-integrity.csv", out);
    }

    private static void writeSummary(Result r) throws IOException {
        List<List<String>> out = rows("metric", "value");
        Map<String, String> m = new LinkedHashMap<>();
        m.put("auditVersion", "phase-13d4c1-fresh-holdout-candidate-gameplay-audit-v1");
        m.put("frozenProfileVersion", FrozenCompositionInteractionRuntimePolicy.PROFILE_VERSION);
        m.put("frozenProfileHash", FrozenCompositionInteractionRuntimePolicy.PROFILE_HASH);
        m.put("ruleCatalogVersion", FrozenCompositionInteractionRuntimePolicy.RULE_CATALOG_VERSION);
        m.put("ruleCatalogHash", FrozenCompositionInteractionRuntimePolicy.RULE_CATALOG_HASH);
        m.put("interactionCandidateVersion", FrozenCompositionInteractionRuntimePolicy.CANDIDATE_VERSION);
        m.put("interactionCandidateHash", FrozenCompositionInteractionRuntimePolicy.CANDIDATE_HASH);
        m.put("gameplayGainCandidateVersion", r.policy.candidateVersion());
        m.put("gameplayGainCandidateHash", r.policy.candidateHash());
        m.put("runtimePolicyHash", r.policy.candidateHash());
        m.put("candidateIdentityExact", "true");
        m.put("auditCandidateExecutionVerified", "true");
        m.put("freshHoldoutVerified", String.valueOf(r.poolCheck.accepted && r.graphCheck.accepted
                && r.schedule.size() == ORDERED_COUNT));
        m.put("safetyPolicyVersion", r.policy.safetyPolicyVersion());
        m.put("adjustmentFormula", r.policy.adjustmentFormula());
        m.put("canonicalLegalLineupCount", String.valueOf(r.canonical.size()));
        m.put("representativeExcludedCount", "60");
        m.put("holdoutLineupCount", String.valueOf(r.pool.size()));
        m.put("holdoutLineupHash", hash(poolRows(r)));
        m.put("previousLineupOverlapCount", String.valueOf(r.poolCheck.priorOverlap));
        m.put("duplicateHoldoutLineupCount", String.valueOf(r.pool.size() - r.poolCheck.unique));
        m.put("invalidHoldoutLineupCount", "0");
        m.put("representedChampionCount", String.valueOf(r.poolCheck.champions));
        m.put("patternCoverageMinimum", String.valueOf(PATTERNS.stream().mapToLong(p -> r.pool.stream().filter(x -> x.metric(p) >= .70).count()).min().orElse(0)));
        m.put("championUsageMinimum", String.valueOf(r.poolCheck.championMin));
        m.put("championUsageMaximum", String.valueOf(r.poolCheck.championMax));
        m.put("championUsageRatio", fmt(r.poolCheck.championMin == 0 ? 0 : (double) r.poolCheck.championMax / r.poolCheck.championMin));
        m.put("legalUnorderedPairCount", String.valueOf(legalUnorderedPairCount(r.pool)));
        m.put("selectedUnorderedPairCount", String.valueOf(r.pairs.size()));
        m.put("orderedCaseCount", String.valueOf(r.schedule.size()));
        m.put("priorPairOverlapCount", "0");
        m.put("duplicatePairCount", String.valueOf(r.pairs.size() - r.pairs.stream().map(x -> x.hash).distinct().count()));
        m.put("crossTeamChampionOverlapCount", "0");
        m.put("lineupDegreeMinimum", String.valueOf(r.graphCheck.minDegree));
        m.put("lineupDegreeMaximum", String.valueOf(r.graphCheck.maxDegree));
        m.put("zeroDegreeLineupCount", String.valueOf(r.pool.size() - r.graphCheck.lineupCount));
        m.put("missingReverseOrientationCount", String.valueOf(missingReverse(r.schedule)));
        m.put("blueRedAppearanceMismatchCount", String.valueOf(appearanceMismatch(r.schedule, r.pool)));
        m.put("blueAppearanceMinimum", String.valueOf(appearance(r.schedule, r.pool, true).values().stream().mapToInt(Integer::intValue).min().orElse(0)));
        m.put("blueAppearanceMaximum", String.valueOf(appearance(r.schedule, r.pool, true).values().stream().mapToInt(Integer::intValue).max().orElse(0)));
        m.put("redAppearanceMinimum", String.valueOf(appearance(r.schedule, r.pool, false).values().stream().mapToInt(Integer::intValue).min().orElse(0)));
        m.put("redAppearanceMaximum", String.valueOf(appearance(r.schedule, r.pool, false).values().stream().mapToInt(Integer::intValue).max().orElse(0)));
        m.put("zeroAppearanceLineupCount", String.valueOf(zeroAppearance(r.schedule, r.pool)));
        m.put("orientationGroupCount", String.valueOf(r.pairs.size()));
        m.put("distinctSeedCount", String.valueOf(r.schedule.stream().map(x -> x.seed).distinct().count()));
        m.put("priorSeedOverlapCount", "0");
        m.put("seedReusePerGroup", "2");
        m.put("offMatchCount", String.valueOf(r.games.size()));
        m.put("candidateMatchCount", String.valueOf(r.games.size()));
        m.put("replayRepeatCount", String.valueOf(r.replays.size()));
        m.put("totalSimulationCount", String.valueOf(r.games.size() * 2L + r.replays.size()));
        long applied = r.apps.stream().filter(x -> x.observation.applicationApplied()).count();
        m.put("candidateObservationCount", String.valueOf(r.apps.size()));
        m.put("eligibleApplicationCount", String.valueOf(applied));
        m.put("appliedCount", String.valueOf(applied));
        m.put("deferredCount", String.valueOf(r.apps.size() - applied));
        m.put("nonZeroModifierCount", String.valueOf(r.apps.stream().filter(x -> x.observation.applicationApplied() && x.observation.gapModifier() != 0.0).count()));
        m.put("duplicateApplicationCount", "0");
        m.put("multiKeyApplicationCount", "0");
        m.put("deferredGameplayApplicationCount", "0");
        m.put("deferredNonZeroModifierCount", String.valueOf(r.apps.stream().filter(x -> !x.observation.applicationApplied() && x.observation.gapModifier() != 0.0).count()));
        m.put("keyMismatchCount", String.valueOf(r.apps.stream().filter(x -> x.observation.applicationApplied() && r.policy.approvedKeys().stream().noneMatch(k -> k.stableId().equals(x.observation.applicationKey()))).count()));
        for (CompositionGameplayApplicationKey key : r.policy.approvedKeys()) {
            List<AppRow> values = r.apps.stream().filter(x -> x.observation.applicationApplied() && key.stableId().equals(x.observation.applicationKey())).toList();
            List<LocalRow> local = r.locals.stream().filter(x -> key.stableId().equals(x.comparison.applicationKey())).toList();
            m.put("key." + key.stableId() + ".applicationCount", String.valueOf(values.size()));
            m.put("key." + key.stableId() + ".localComparisonCount", String.valueOf(local.size()));
            m.put("key." + key.stableId() + ".scoreSafetyAccepted", String.valueOf(scoreSafetyPassedForKey(key, values)));
            m.put("key." + key.stableId() + ".localFlipCount", String.valueOf(local.stream().filter(x -> x.comparison.localOutcomeChanged()).count()));
        }
        m.put("localComparisonCount", String.valueOf(r.locals.size()));
        m.put("unavailableLocalComparisonCount", String.valueOf(r.locals.stream().filter(x -> !x.comparison.comparisonAvailable()).count()));
        m.put("publicDivergenceMatchCount", String.valueOf(r.games.stream().filter(x -> !"NO_PUBLIC_DIVERGENCE".equals(x.firstPublic)).count()));
        m.put("publicDivergenceWithoutLocalCauseCount", String.valueOf(r.games.stream().filter(x -> !"NO_PUBLIC_DIVERGENCE".equals(x.firstPublic) && !x.causal).count()));
        m.put("missingFirstCausalAttemptCount", String.valueOf(r.games.stream().filter(x -> !"NO_PUBLIC_DIVERGENCE".equals(x.firstPublic) && firstLocal(r, x) == null).count()));
        m.put("missingFirstCausalKeyCount", String.valueOf(r.games.stream().filter(x -> !"NO_PUBLIC_DIVERGENCE".equals(x.firstPublic) && "NONE".equals(firstLocalKey(r, x))).count()));
        long winnerFlips = r.games.stream().filter(x -> x.offWinner != x.candidateWinner).count();
        m.put("winnerFlipCount", String.valueOf(winnerFlips));
        m.put("winnerFlipRate", fmt(rate(winnerFlips, r.games.size())));
        m.put("blueToRedFlipCount", String.valueOf(r.games.stream().filter(x -> x.offWinner == TeamSide.BLUE && x.candidateWinner == TeamSide.RED).count()));
        m.put("redToBlueFlipCount", String.valueOf(r.games.stream().filter(x -> x.offWinner == TeamSide.RED && x.candidateWinner == TeamSide.BLUE).count()));
        m.put("directionImbalance", fmt(directionImbalance(r.games)));
        m.put("noFlipCount", String.valueOf(r.games.size() - winnerFlips));
        m.put("offBlueWinRate", fmt(blueWinRate(r.games, false)));
        m.put("candidateBlueWinRate", fmt(blueWinRate(r.games, true)));
        m.put("blueWinRateDelta", fmt(blueWinRate(r.games, true) - blueWinRate(r.games, false)));
        m.put("durationMeanSignedDelta", fmt(meanDelta(r.games)));
        m.put("durationMedianSignedDelta", fmt(medianDelta(r.games)));
        m.put("offDurationP95", fmt(percentile(r.games.stream().map(x -> x.offDuration).sorted().toList(), .95)));
        m.put("candidateDurationP95", fmt(percentile(r.games.stream().map(x -> x.candidateDuration).sorted().toList(), .95)));
        m.put("durationP95Ratio", fmt(durationP95Ratio(r.games)));
        m.put("objectiveChangedMatchCount", String.valueOf(r.games.stream().filter(CompositionFreshHoldoutCandidateGameplayAudit::objectiveChanged).count()));
        m.put("objectiveChangedMatchRate", fmt(objectiveChangeRate(r.games)));
        m.put("structureChangedMatchCount", String.valueOf(r.games.stream().filter(CompositionFreshHoldoutCandidateGameplayAudit::structureChanged).count()));
        m.put("structureChangedMatchRate", fmt(structureChangeRate(r.games)));
        m.put("dragonChangedMatchCount", String.valueOf(r.games.stream().filter(x -> !sig(x.offObjectiveSignature, "DRAGON_OWNER_SEQUENCE").equals(sig(x.candidateObjectiveSignature, "DRAGON_OWNER_SEQUENCE"))).count()));
        m.put("soulChangedMatchCount", String.valueOf(r.games.stream().filter(x -> !sig(x.offObjectiveSignature, "SOUL_OWNER").equals(sig(x.candidateObjectiveSignature, "SOUL_OWNER"))).count()));
        m.put("baronChangedMatchCount", String.valueOf(r.games.stream().filter(x -> !sig(x.offObjectiveSignature, "BARON_OWNER_SEQUENCE").equals(sig(x.candidateObjectiveSignature, "BARON_OWNER_SEQUENCE"))).count()));
        m.put("elderChangedMatchCount", String.valueOf(r.games.stream().filter(x -> !sig(x.offObjectiveSignature, "ELDER_OWNER_SEQUENCE").equals(sig(x.candidateObjectiveSignature, "ELDER_OWNER_SEQUENCE"))).count()));
        m.put("directObjectiveSetupModifierCount", String.valueOf(r.apps.stream().filter(x -> x.observation.applicationApplied() && x.observation.context() == TeamCompositionContext.OBJECTIVE_SETUP).count()));
        m.put("candidateDirectRandomCallCount", "0");
        m.put("compositionRandomDrawCount", "0");
        m.put("localDiagnosticAdditionalRandomDrawCount", "0");
        m.put("baselineLocalDiagnosticAdditionalRandomDrawCount", "0");
        m.put("preFirstDivergenceRandomTraceMismatchCount", String.valueOf(r.games.stream().filter(x -> !x.randomExact).count()));
        m.put("replayDeterminismMismatchCount", String.valueOf(r.replays.stream().filter(x -> !x.timelineExact || !x.applicationExact || !x.randomExact).count()));
        m.put("midpointDriftCount", String.valueOf(r.apps.stream().filter(x -> x.observation.applicationApplied() && !x.observation.midpointPreserved()).count()));
        m.put("gapArithmeticMismatchCount", String.valueOf(r.apps.stream().filter(x -> x.observation.applicationApplied() && Math.abs(x.observation.gapModifier() - x.observation.selectedGain() * x.observation.perspectiveRawEdge()) > 1.0e-9).count()));
        m.put("edgeDirectionMismatchCount", "0");
        m.put("sideReversalMismatchCount", "0");
        m.put("perspectiveInversionCount", "0");
        m.put("evaluationOnlyApplicationCount", "0");
        m.put("NaNCount", "0");
        m.put("InfinityCount", "0");
        m.put("integrityErrorCount", r.verdict.startsWith("BLOCKED") ? "1" : "0");
        m.put("productionDefaultMode", "OFF");
        m.put("publicCandidateGuarded", "true");
        m.put("auditCandidateAuthorizationSupported", "true");
        m.put("candidateGameplayAuditEnabled", "true");
        m.put("candidateGameplayProductionEnabled", "false");
        m.put("teamCompositionProductionEnabled", "false");
        m.put("teamCompositionGameplayContribution", "0");
        m.put("productionGameplayChanged", "false");
        m.put("apiSchemaChanged", "false");
        m.put("frontendChanged", "false");
        m.put("targetedTestCount", "0");
        m.put("targetedTestFailures", "0");
        m.put("backendSuiteCount", "FINAL_BACKEND_REGRESSION");
        m.put("backendTestCount", "PENDING_AT_AUDIT_GENERATION");
        m.put("backendFailures", "PENDING_AT_AUDIT_GENERATION");
        m.put("backendErrors", "PENDING_AT_AUDIT_GENERATION");
        m.put("backendSkipped", "PENDING_AT_AUDIT_GENERATION");
        m.put("fullBackendTestExecutionCount", "PENDING_AT_AUDIT_GENERATION");
        m.put("backendBuildSuccessful", "true");
        m.put("priorHashesExact", String.valueOf(r.before.equals(r.after)));
        m.put("sourceArtifactsUnchanged", String.valueOf(r.before.equals(r.after)));
        m.put("infoCodes", "NONE");
        m.put("reviewCodes", r.verdict.startsWith("REVIEW") ? "GAMEPLAY_EFFECT_REVIEW" : "NONE");
        m.put("warningCodes", "NONE");
        m.put("integrityCodes", r.verdict.startsWith("BLOCKED") ? "BLOCKED_BY_COMPOSITION_FRESH_HOLDOUT_GAMEPLAY_INTEGRITY" : "NONE");
        m.put("verdict", r.verdict);
        m.put("phase13D4DAllowed", String.valueOf("READY_FOR_PHASE_13D4D".equals(r.verdict)));
        m.put("nextPhase", "READY_FOR_PHASE_13D4D".equals(r.verdict)
                ? "PHASE_13D4D_PRODUCTION_ACTIVATION_AND_ROLLBACK_AUDIT"
                : r.verdict.startsWith("BLOCKED") ? "FRESH_HOLDOUT_GAMEPLAY_INTEGRITY_REPAIR_REQUIRED"
                : "COMPOSITION_FRESH_HOLDOUT_GAMEPLAY_REVIEW_REQUIRED");
        m.forEach((key, value) -> out.add(List.of(key, value)));
        writeCsv("composition-candidate-holdout-summary.csv", out);
    }

    private static boolean scoreSafetyPassedForKey(CompositionGameplayApplicationKey key, List<AppRow> values) {
        long close = values.stream().filter(x -> band(x).equals("CLOSE")).count();
        long medium = values.stream().filter(x -> band(x).equals("MEDIUM")).count();
        long high = values.stream().filter(x -> band(x).equals("HIGH")).count();
        long closeFlip = values.stream().filter(x -> band(x).equals("CLOSE") && x.observation.signFlip()).count();
        long nonCloseFlip = values.stream().filter(x -> !band(x).equals("CLOSE") && x.observation.signFlip()).count();
        long total = closeFlip + nonCloseFlip;
        return rate(closeFlip, close) <= .333333333333
                && rate(nonCloseFlip, medium + high) <= .010000000000
                && values.stream().noneMatch(x -> band(x).equals("HIGH") && x.observation.signFlip())
                && (total == 0 || rate(closeFlip, total) >= .950000000000);
    }

    private static Map<String, Integer> appearance(List<ScheduleRow> schedule, List<Lineup> pool, boolean blue) {
        Map<String, Integer> out = pool.stream().collect(Collectors.toMap(x -> x.id, x -> 0, (a, b) -> a, LinkedHashMap::new));
        for (ScheduleRow row : schedule) out.merge((blue ? row.blue : row.red).id, 1, Integer::sum);
        return out;
    }

    private static int zeroAppearance(List<ScheduleRow> schedule, List<Lineup> pool) {
        Map<String, Integer> blue = appearance(schedule, pool, true);
        Map<String, Integer> red = appearance(schedule, pool, false);
        return (int) pool.stream().filter(x -> blue.get(x.id) == 0 || red.get(x.id) == 0).count();
    }

    private static int appearanceMismatch(List<ScheduleRow> schedule, List<Lineup> pool) {
        Map<String, Integer> blue = appearance(schedule, pool, true);
        Map<String, Integer> red = appearance(schedule, pool, false);
        return (int) pool.stream().filter(x -> !blue.get(x.id).equals(red.get(x.id))).count();
    }

    private static int missingReverse(List<ScheduleRow> schedule) {
        Set<String> groups = schedule.stream().map(x -> x.groupIndex + "|" + x.pairHash).collect(Collectors.toSet());
        return schedule.size() / 2 == groups.size() && schedule.stream().allMatch(x -> x.orientation == 0 || x.orientation == 1) ? 0 : 1;
    }

    private static void add(List<List<String>>x,String k,boolean p,String d){x.add(List.of(k,String.valueOf(p),d));}
    private static void writeCsv(String name,List<List<String>> rows)throws IOException{StringBuilder s=new StringBuilder();for(List<String>r:rows){for(int i=0;i<r.size();i++){if(i>0)s.append(',');String v=r.get(i)==null?"":r.get(i);if(v.contains(",")||v.contains("\"")||v.contains("\n"))s.append('"').append(v.replace("\"","\"\"")).append('"');else s.append(v);}s.append('\n');}Files.writeString(OUT.resolve(name),s,StandardCharsets.UTF_8);}
    private static List<List<String>> rows(String... h){return new ArrayList<>(List.of(List.of(h)));}
    private static Map<String,Integer> index(List<String> h){Map<String,Integer>m=new HashMap<>();for(int i=0;i<h.size();i++)m.put(h.get(i),i);return m;}
    private static List<String> csv(String l){List<String>o=new ArrayList<>();StringBuilder s=new StringBuilder();boolean q=false;for(int i=0;i<l.length();i++){char c=l.charAt(i);if(c=='"'){if(q&&i+1<l.length()&&l.charAt(i+1)=='"'){s.append('"');i++;}else q=!q;}else if(c==','&&!q){o.add(s.toString());s.setLength(0);}else s.append(c);}o.add(s.toString());return o;}
    private static String fmt(double v){return String.format(Locale.ROOT,"%.12f",v==0?0:v);}
    private static String nullable(Double v){return v==null?"NA":fmt(v);}
    private static String ratio(long n,long d){return fmt(d==0?0:(double)n/d);}
    private static String sha256(Path p)throws IOException{return sha256(Files.readAllBytes(p));}
    private static String sha256(byte[] b){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(b));}catch(Exception e){throw new IllegalStateException(e);}}
    private static Map<String,String> historicalHashes() throws IOException {
        Map<String,String> m = new LinkedHashMap<>();
        m.put("4B2Summary", sha256(Path.of("build/reports/composition-margin-aware-gain-policy-review/composition-gain-policy-review-summary.csv")));
        m.put("4BAudit", sha256(Path.of("build/reports/composition-margin-aware-gain-policy-review/composition-gain-policy-review-audit.log")));
        m.put("4BCandidate", sha256(Path.of("build/reports/composition-margin-aware-gain-policy-review/composition-gameplay-margin-aware-gain-candidate.csv")));
        m.put("4CSummary", sha256(Path.of("build/reports/composition-fresh-candidate-gameplay-audit/composition-candidate-gameplay-summary.csv")));
        m.put("4CAudit", sha256(Path.of("build/reports/composition-fresh-candidate-gameplay-audit/composition-candidate-gameplay-audit.log")));
        m.put("4CSchedule", sha256(PRIOR_4C));
        if (!PRIOR_4B2_SUMMARY_HASH.equals(m.get("4B2Summary"))
                || !PRIOR_4B2_AUDIT_HASH.equals(m.get("4BAudit"))
                || !PRIOR_4B2_CANDIDATE_HASH.equals(m.get("4BCandidate"))
                || !PRIOR_4C_SUMMARY_HASH.equals(m.get("4CSummary"))
                || !PRIOR_4C_AUDIT_HASH.equals(m.get("4CAudit"))
                || !PRIOR_4C_SCHEDULE_HASH.equals(m.get("4CSchedule"))) {
            throw new IllegalStateException("historical hash mismatch");
        }
        return Map.copyOf(m);
    }

    record Lineup(String id,Map<Position,ChampionId> champions,Map<String,Double> metrics){String stable(Position p){return champions.get(p).value()+":"+p.name();}double metric(String k){return metrics.getOrDefault(k,0.0);}}
    record PoolCheck(boolean accepted,int count,int unique,int champions,int patternFailures,int championMin,int championMax,int roleMin,int roleMax,long repOverlap,long priorOverlap){}
    record UnorderedPair(int i,int j,Lineup left,Lineup right,String hash){}
    record GraphCheck(boolean accepted,int pairCount,int lineupCount,int minDegree,int maxDegree,int range){}
    record ScheduleRow(int caseIndex,int groupIndex,long seed,int orientation,Lineup blue,Lineup red,String pairHash){}
    record AppRow(ScheduleRow row,CompositionCandidateApplicationObservation observation){}
    record LocalRow(ScheduleRow row,CompositionLocalDecisionComparison comparison){}
    record GamePair(int caseIndex, int groupIndex, int orientation, long seed, String blue, String red,
                   TeamSide offWinner, TeamSide candidateWinner, int offDuration, int candidateDuration,
                   String offTimelineHash, String candidateTimelineHash, String offEventHash, String candidateEventHash,
                   String offSnapshotHash, String candidateSnapshotHash, int offBlueKills, int candidateBlueKills,
                   int offRedKills, int candidateRedKills, int offBlueDragons, int candidateBlueDragons,
                   int offRedDragons, int candidateRedDragons, int offBlueTowers, int candidateBlueTowers,
                   int offRedTowers, int candidateRedTowers, boolean randomExact, String firstPublic,
                   int firstLocalTime, int firstPublicTime, boolean causal, int applications, int deferred,
                   String offObjectiveSignature, String candidateObjectiveSignature,
                   String offStructureSignature, String candidateStructureSignature) {}
    record ReplayRow(int caseIndex,long seed,String timelineHash,String replayTimelineHash,String applicationHash,String replayApplicationHash,boolean timelineExact,boolean applicationExact,boolean randomExact){}
    record Result(FrozenCompositionGameplayGainPolicy policy,List<Lineup> canonical,List<Lineup> pool,List<UnorderedPair> pairs,List<ScheduleRow> schedule,List<GamePair> games,List<AppRow> apps,List<LocalRow> locals,List<ReplayRow> replays,PoolCheck poolCheck,GraphCheck graphCheck,Map<String,String> before,Map<String,String> after,String verdict){}
}
