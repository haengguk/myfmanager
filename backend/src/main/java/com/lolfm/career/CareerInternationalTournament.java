package com.lolfm.career;

import com.lolfm.career.CareerInternationalState.Entry;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiPredicate;

/** Pure finite bracket projection. Outcomes come from the verified receipt ledger, never from ratings. */
public final class CareerInternationalTournament {
    public record Outcome(String winner, String loser) {}
    public record Bout(String id, String stage, LocalDate date, int order, String format,
                       String first, String second, String selectionOwner, String sidePolicy, String group) {}
    public record Draw(String scope, List<String> teams, String relaxation) {
        public Draw { teams = List.copyOf(teams); }
    }
    public record Plan(List<Bout> bouts, Map<String, Integer> placements, List<Draw> draws,
                       List<String> regionalPerformance, String champion, boolean complete) {
        public Plan { bouts = List.copyOf(bouts); placements = Map.copyOf(placements);
            draws = List.copyOf(draws); regionalPerformance = List.copyOf(regionalPerformance); }
    }
    private final CareerInternationalState state;
    private final Map<String, Outcome> outcomes;
    private final Map<String, Entry> entries = new HashMap<>();
    private final List<Bout> bouts = new ArrayList<>();
    private final List<Draw> draws = new ArrayList<>();
    private final Map<String, Integer> places = new LinkedHashMap<>();
    private int order;
    private String champion;

    private CareerInternationalTournament(CareerInternationalState state, Map<String, Outcome> outcomes) {
        this.state = state; this.outcomes = Map.copyOf(outcomes);
        state.entries().forEach(e -> entries.put(e.team(), e));
    }
    public static Plan project(CareerInternationalState state, Map<String, Outcome> outcomes) {
        var graph = new CareerInternationalTournament(state, outcomes);
        switch (state.competitionId()) {
            case "FIRST_STAND" -> graph.fst();
            case "MSI" -> graph.msi();
            case "EWC_LOL" -> graph.ewc();
            case "WORLDS" -> graph.worlds();
            default -> throw new IllegalArgumentException("UNSUPPORTED_INTERNATIONAL_COMPETITION");
        }
        if (!graph.bouts.stream().map(Bout::id).collect(java.util.stream.Collectors.toSet()).containsAll(outcomes.keySet()))
            throw new IllegalArgumentException("OUTCOME_WITHOUT_VALID_BRACKET_BOUT");
        boolean complete = graph.champion != null;
        if (complete && graph.places.size() != state.entries().size()) throw new IllegalStateException("FINAL_PLACEMENT_CARDINALITY");
        return new Plan(graph.bouts, graph.places, graph.draws,
                complete ? graph.regionalPerformance() : List.of(), graph.champion, complete);
    }
    private String id(String local) { return state.competitionId() + '_' + local; }
    private Outcome result(String local) { return outcomes.get(id(local)); }
    private String w(String local) { var r = result(local); return r == null ? null : r.winner(); }
    private String l(String local) { var r = result(local); return r == null ? null : r.loser(); }
    private LocalDate date(String md) { return LocalDate.parse(state.year() + "-" + md); }
    private String drawKey(String scope, String value) {
        return CareerInternationalRules.hash(state.drawSeed() + "|" + state.careerId() + '|' + state.year() + '|'
                + state.competitionId() + '|' + CareerInternationalRules.POLICY + '|' + scope + '|' + value);
    }
    private <T> List<T> shuffled(String scope, List<T> values) {
        return values.stream().sorted(Comparator.comparing(v -> drawKey(scope, v.toString()))).toList();
    }
    private String coin(String scope, String a, String b) { return shuffled(scope, List.of(a, b)).getFirst(); }
    private String higherPool(String scope, String a, String b) {
        int comparison = Integer.compare(entries.get(a).pool(), entries.get(b).pool());
        return comparison == 0 ? coin(scope, a, b) : comparison < 0 ? a : b;
    }
    private void bout(String local, String stage, String md, String format, String a, String b, String owner, boolean rods, String group) {
        int sequence = ++order;
        if (a == null || b == null) return;
        if (a.equals(b) || !entries.containsKey(a) || !entries.containsKey(b)) throw new IllegalStateException("BRACKET_PARTICIPANT_INTEGRITY");
        var outcome = result(local);
        if (outcome != null && !Set.of(a, b).equals(Set.of(outcome.winner(), outcome.loser())))
            throw new IllegalArgumentException("BRACKET_OUTCOME_PARTICIPANT_MISMATCH");
        bouts.add(new Bout(id(local), stage, date(md), sequence, format, a, b,
                owner == null ? coin(local + ":ROFS", a, b) : owner,
                rods ? CareerInternationalRules.RODS : CareerInternationalRules.ROFS, group));
    }
    private void placeLoser(String local, int place) { if (l(local) != null) places.put(l(local), place); }
    private void finalBout(String local) {
        if (w(local) != null) { champion = w(local); places.put(champion, 1); placeLoser(local, 2); }
    }

    private void fst() {
        List<String> groupTeams = assignPools("GROUPS", state.entries(), new int[]{1,3,2,2,1,3,2,2}, 4);
        for (int group = 0; group < 2; group++) {
            List<String> teams = groupTeams.subList(group * 4, group * 4 + 4);
            gsl("G" + group, teams, true);
        }
        bout("SF1", "KNOCKOUT", "03-21", "BO5", w("G0_U"), w("G1_F"), w("G0_U"), false, null);
        bout("SF2", "KNOCKOUT", "03-21", "BO5", w("G1_U"), w("G0_F"), w("G1_U"), false, null);
        bout("F", "FINAL", "03-22", "BO5", w("SF1"), w("SF2"), null, false, null);
        placeLoser("SF1", 3); placeLoser("SF2", 3); finalBout("F");
    }
    private void gsl(String prefix, List<String> teams, boolean fst) {
        String group = prefix;
        String open = fst ? "03-16" : "07-15", upper = fst ? "03-18" : "07-16";
        String lower = fst ? "03-19" : "07-15", last = fst ? "03-20" : "07-16";
        String openingFormat = fst ? "BO5" : "BO1", eliminationFormat = fst ? "BO5" : "BO3";
        bout(prefix + "_O1", "GROUP", open, openingFormat, teams.get(0), teams.get(1), higherPool(prefix+"O1",teams.get(0),teams.get(1)), false, group);
        bout(prefix + "_O2", "GROUP", open, openingFormat, teams.get(2), teams.get(3), higherPool(prefix+"O2",teams.get(2),teams.get(3)), false, group);
        bout(prefix + "_U", "GROUP", upper, openingFormat, w(prefix+"_O1"), w(prefix+"_O2"), null, false, group);
        bout(prefix + "_D", "GROUP", lower, eliminationFormat, l(prefix+"_O1"), l(prefix+"_O2"), null, false, group);
        bout(prefix + "_F", "GROUP", last, eliminationFormat, l(prefix+"_U"), w(prefix+"_D"), fst ? null : l(prefix+"_U"), false, group);
        placeLoser(prefix+"_D", fst ? 7 : 13); placeLoser(prefix+"_F", fst ? 5 : 9);
    }
    private String playIn(String month, int lastPlace) {
        List<String> teams = shuffled("PLAY_IN", state.entries().stream().filter(e -> e.phase().equals("PLAY_IN")).map(Entry::team).toList());
        if (teams.size() != 4) throw new IllegalStateException("PLAY_IN_CARDINALITY");
        draws.add(new Draw("PLAY_IN", teams, null));
        String[] days = month.equals("MSI") ? new String[]{"06-28","06-28","06-29","06-30","06-30","07-01"}
                : new String[]{"10-15","10-15","10-16","10-17","10-17","10-18"};
        bout("PI_O1", "PLAY_IN", days[0], "BO5", teams.get(0), teams.get(1), seedOwner("PI_O1", teams.get(0),teams.get(1)), false, null);
        bout("PI_O2", "PLAY_IN", days[1], "BO5", teams.get(2), teams.get(3), seedOwner("PI_O2", teams.get(2),teams.get(3)), false, null);
        bout("PI_U", "PLAY_IN", days[2], "BO5", w("PI_O1"), w("PI_O2"), null, false, null);
        bout("PI_D", "PLAY_IN", days[3], "BO5", l("PI_O1"), l("PI_O2"), null, false, null);
        bout("PI_L", "PLAY_IN", days[4], "BO5", l("PI_U"), w("PI_D"), null, false, null);
        bout("PI_F", "PLAY_IN", days[5], "BO5", w("PI_U"), w("PI_L"), w("PI_U"), true, null);
        placeLoser("PI_D", lastPlace); placeLoser("PI_L", lastPlace-1); placeLoser("PI_F", lastPlace-2);
        return w("PI_F");
    }
    private String seedOwner(String scope, String a, String b) {
        int first = entries.get(a).regionalSeed(), second = entries.get(b).regionalSeed();
        return first == second ? coin(scope, a, b) : first < second ? a : b;
    }
    private void msi() {
        String winner = playIn("MSI", 11); if (winner == null) return;
        var main = new ArrayList<>(state.entries().stream().filter(e -> e.phase().equals("MAIN")).toList());
        main.add(entries.get(winner).withPool(4)); main.forEach(e -> entries.put(e.team(), e));
        List<String> teams = assignPools("MAIN_HALVES", main, new int[]{1,4,2,3,1,4,2,3}, 4);
        for (int i=0; i<4; i++) bout("U1_"+i,"MAIN_UPPER","07-0"+(3+i/2),"BO5", teams.get(i*2),teams.get(i*2+1),teams.get(i*2),false,null);
        for (int i=0; i<2; i++) {
            bout("U2_"+i,"MAIN_UPPER","07-05","BO5",w("U1_"+(i*2)),w("U1_"+(i*2+1)),null,false,null);
            bout("L1_"+i,"MAIN_LOWER","07-06","BO5",l("U1_"+(i*2)),l("U1_"+(i*2+1)),null,false,null);
            // Official crossing: the upper-round-two loser enters the opposite lower half.
            bout("L2_"+i,"MAIN_LOWER","07-07","BO5",w("L1_"+i),l("U2_"+(1-i)),null,false,null);
            placeLoser("L1_"+i,7); placeLoser("L2_"+i,5);
        }
        bout("U3","MAIN_UPPER","07-09","BO5",w("U2_0"),w("U2_1"),null,false,null);
        bout("L3","MAIN_LOWER","07-10","BO5",w("L2_0"),w("L2_1"),null,false,null);
        bout("L4","MAIN_LOWER","07-11","BO5",w("L3"),l("U3"),null,false,null);
        bout("F","FINAL","07-12","BO5",w("U3"),w("L4"),w("U3"),true,null);
        placeLoser("L3",4); placeLoser("L4",3); finalBout("F");
    }
    private void ewc() {
        List<String> groupTeams = assignPools("GROUPS",state.entries(),new int[]{1,4,2,3,1,4,2,3,1,4,2,3,1,4,2,3},4);
        for (int group=0; group<4; group++) gsl("G"+group,groupTeams.subList(group*4,group*4+4),false);
        List<String> winners = new ArrayList<>(), runners = new ArrayList<>();
        for (int i=0; i<4; i++) { if (w("G"+i+"_U")==null || w("G"+i+"_F")==null) return;
            winners.add(w("G"+i+"_U")); runners.add(w("G"+i+"_F")); }
        winners = shuffled("KO_WINNERS", winners);
        Map<String,Integer> group = new HashMap<>();
        for(int i=0;i<4;i++){group.put(w("G"+i+"_U"),i);group.put(w("G"+i+"_F"),i);}
        List<String> orderedWinners = winners;
        List<String> assigned = assign("KO_RUNNERS", runners, (slot,team) -> {
            int ownWinner = orderedWinners.indexOf(w("G"+group.get(team)+"_U"));
            return slot/2 != ownWinner/2;
        });
        draws.add(new Draw("KO_WINNERS", winners, null)); draws.add(new Draw("KO_RUNNERS",assigned,null));
        for(int i=0;i<4;i++) { bout("QF"+i,"QUARTERFINAL","07-17","BO3",winners.get(i),assigned.get(i),winners.get(i),false,null); placeLoser("QF"+i,5); }
        for(int i=0;i<2;i++) bout("SF"+i,"SEMIFINAL","07-18","BO3",w("QF"+(i*2)),w("QF"+(i*2+1)),null,false,null);
        bout("THIRD","THIRD_PLACE","07-19","BO3",l("SF0"),l("SF1"),null,false,null);
        if(w("THIRD")!=null){places.put(w("THIRD"),3);placeLoser("THIRD",4);}
        bout("F","FINAL","07-19","BO5",w("SF0"),w("SF1"),null,false,null);
        // The third-place match is part of completion, even if a caller supplies the final first.
        if(w("THIRD")!=null) finalBout("F");
    }
    private void worlds() {
        String winner = playIn("WORLDS",19); if(winner==null)return;
        List<Entry> swiss = new ArrayList<>(state.entries().stream().filter(e->e.phase().equals("MAIN")).toList());
        swiss.add(entries.get(winner).withPool(4)); swiss.forEach(e->entries.put(e.team(),e));
        if(swiss.size()!=16)throw new IllegalStateException("SWISS_CARDINALITY");
        Map<String,Integer> wins = new HashMap<>(), losses = new HashMap<>(); Set<String> previous = new HashSet<>();
        swiss.forEach(e->{wins.put(e.team(),0);losses.put(e.team(),0);});
        List<String> qualified = new ArrayList<>(); Map<String,Integer> qualifyingLosses = new HashMap<>();
        for(int round=1;round<=5;round++) {
            final int currentRound=round;
            List<String> active = wins.keySet().stream().filter(t->wins.get(t)<3 && losses.get(t)<3).sorted().toList();
            Map<String,List<String>> buckets = new java.util.TreeMap<>();
            for(String team:active) buckets.computeIfAbsent(wins.get(team)+":"+losses.get(team),ignored->new ArrayList<>()).add(team);
            int match=0; boolean finished=true;
            for(var bucket:buckets.entrySet()) {
                String scope="SWISS_R"+round+"_"+bucket.getKey();
                BiPredicate<String,String> constraint = round==1
                        ? (a,b)->entries.get(a).pool()+entries.get(b).pool()==5 && !entries.get(a).region().equals(entries.get(b).region())
                        : (a,b)->!previous.contains(pairKey(a,b));
                List<String> pairs = pairing(scope,bucket.getValue(),constraint);
                String relaxation=null;
                if(pairs==null) {
                    relaxation=round==1?"IMPOSSIBLE_INITIAL_REGION_CONSTRAINT":"IMPOSSIBLE_NO_REMATCH_CONSTRAINT";
                    pairs=pairing(scope,bucket.getValue(),round==1?(a,b)->entries.get(a).pool()+entries.get(b).pool()==5:(a,b)->true);
                    if(pairs==null)throw new IllegalStateException("SWISS_POOL_OR_RECORD_CARDINALITY");
                }
                draws.add(new Draw(scope,pairs,relaxation));
                // All matches in a round are drawn before processing any of its outcomes.
                for(int i=0;i<pairs.size();i+=2) {
                    String a=pairs.get(i),b=pairs.get(i+1),local="SWISS_R"+round+"_"+(match++);
                    String md=switch(round){case 1->"10-23";case 2->"10-24";case 3->"10-25";case 4->"10-28";default->"10-30";};
                    boolean deciding=wins.get(a)==2 || losses.get(a)==2;
                    bout(local,"SWISS_R"+round,md,deciding?"BO3":"BO1",a,b,seedOwner(local,a,b),false,null);
                    if(result(local)==null)finished=false;
                }
            }
            if(!finished)return;
            List<Bout> current = bouts.stream().filter(b->b.stage().equals("SWISS_R"+currentRound)).toList();
            for(Bout b:current){var r=outcomes.get(b.id());previous.add(pairKey(b.first(),b.second()));
                wins.merge(r.winner(),1,Integer::sum); losses.merge(r.loser(),1,Integer::sum);
                if(wins.get(r.winner())==3){qualified.add(r.winner());qualifyingLosses.put(r.winner(),losses.get(r.winner()));}
                if(losses.get(r.loser())==3)places.put(r.loser(),wins.get(r.loser())==2?9:wins.get(r.loser())==1?12:15);
            }
        }
        if(qualified.size()!=8 || places.size()!=11)throw new IllegalStateException("SWISS_ADVANCEMENT_CARDINALITY");
        var unbeaten=shuffled("KO_UNBEATEN",qualified.stream().filter(t->qualifyingLosses.get(t)==0).toList());
        if(unbeaten.size()!=2)throw new IllegalStateException("SWISS_UNBEATEN_CARDINALITY");
        var threeTwo=shuffled("KO_THREE_TWO",qualified.stream().filter(t->qualifyingLosses.get(t)==2).toList());
        List<String> knockout=new ArrayList<>(java.util.Collections.nCopies(8,null));
        knockout.set(0,unbeaten.get(0));knockout.set(4,unbeaten.get(1));knockout.set(1,threeTwo.get(0));knockout.set(5,threeTwo.get(1));
        var remaining=shuffled("KO_REMAINING",qualified.stream().filter(t->!knockout.contains(t)).toList());
        for(int i=0,k=0;i<8;i++)if(knockout.get(i)==null)knockout.set(i,remaining.get(k++));
        draws.add(new Draw("KNOCKOUT",knockout,null));
        for(int i=0;i<4;i++) {String a=knockout.get(i*2),b=knockout.get(i*2+1);
            String owner=qualifyingLosses.get(a).equals(qualifyingLosses.get(b))?coin("QF"+i,a,b):qualifyingLosses.get(a)<qualifyingLosses.get(b)?a:b;
            bout("QF"+i,"QUARTERFINAL","11-0"+(3+i),"BO5",a,b,owner,false,null);placeLoser("QF"+i,5);}
        for(int i=0;i<2;i++){bout("SF"+i,"SEMIFINAL","11-0"+(7+i),"BO5",w("QF"+(i*2)),w("QF"+(i*2+1)),null,false,null);placeLoser("SF"+i,3);}
        bout("F","FINAL","11-14","BO5",w("SF0"),w("SF1"),null,false,null);finalBout("F");
    }
    private static String pairKey(String a,String b){return a.compareTo(b)<0?a+'|'+b:b+'|'+a;}

    /** Exhausts every valid matching before returning null; no arbitrary retry cap. */
    List<String> pairing(String scope,List<String> teams,BiPredicate<String,String> allowed) {
        if(teams.size()%2!=0)throw new IllegalStateException("ODD_SWISS_RECORD_GROUP");
        return matchPairs(shuffled(scope,teams), allowed);
    }
    static List<String> matchPairs(List<String> ordered, BiPredicate<String,String> allowed) {
        if (ordered.size() % 2 != 0) throw new IllegalStateException("ODD_SWISS_RECORD_GROUP");
        var remaining = new ArrayList<>(ordered); var chosen = new ArrayList<String>();
        return pairSearch(remaining, chosen, allowed) ? List.copyOf(chosen) : null;
    }
    private static boolean pairSearch(List<String> remaining,List<String> chosen,BiPredicate<String,String> allowed){
        if(remaining.isEmpty())return true;
        String first=remaining.removeFirst();
        for(int i=0;i<remaining.size();i++) {String other=remaining.get(i);if(!allowed.test(first,other))continue;
            remaining.remove(i);chosen.add(first);chosen.add(other);
            if(pairSearch(remaining,chosen,allowed))return true;
            chosen.removeLast();chosen.removeLast();remaining.add(i,other);
        }
        remaining.addFirst(first);return false;
    }
    private List<String> assignPools(String scope,List<Entry> contestants,int[] pools,int groupSize){
        if(contestants.size()!=pools.length)throw new IllegalStateException("POOL_CARDINALITY");
        Map<String,Entry> byTeam=new HashMap<>();contestants.forEach(e->byTeam.put(e.team(),e));
        var remaining=new ArrayList<>(shuffled(scope,contestants.stream().map(Entry::team).toList()));
        var chosen=new ArrayList<String>();
        if(!poolSearch(remaining,chosen,pools,groupSize,byTeam))throw new IllegalStateException("IMPOSSIBLE_INTERNATIONAL_POOL_DRAW");
        draws.add(new Draw(scope,chosen,null));return List.copyOf(chosen);
    }
    private boolean poolSearch(List<String> remaining,List<String> chosen,int[] pools,int size,Map<String,Entry> byTeam){
        if(remaining.isEmpty())return true;int slot=chosen.size();
        for(int i=0;i<remaining.size();i++){String team=remaining.get(i);Entry entry=byTeam.get(team);
            if(entry.pool()!=pools[slot])continue;
            if(chosen.subList(slot/size*size,slot).stream().anyMatch(t->byTeam.get(t).region().equals(entry.region())))continue;
            remaining.remove(i);chosen.add(team);if(poolSearch(remaining,chosen,pools,size,byTeam))return true;
            chosen.removeLast();remaining.add(i,team);
        }return false;
    }
    private List<String> assign(String scope,List<String> teams,BiPredicate<Integer,String> allowed){
        var remaining=new ArrayList<>(shuffled(scope,teams));var chosen=new ArrayList<String>();
        if(!assignmentSearch(remaining,chosen,allowed))throw new IllegalStateException("IMPOSSIBLE_KNOCKOUT_DRAW");return List.copyOf(chosen);
    }
    private boolean assignmentSearch(List<String> remaining,List<String> chosen,BiPredicate<Integer,String> allowed){
        if(remaining.isEmpty())return true;
        for(int i=0;i<remaining.size();i++){String team=remaining.get(i);if(!allowed.test(chosen.size(),team))continue;
            remaining.remove(i);chosen.add(team);if(assignmentSearch(remaining,chosen,allowed))return true;chosen.removeLast();remaining.add(i,team);}
        return false;
    }
    private List<String> regionalPerformance(){
        Map<String,List<Integer>> results=new HashMap<>();state.entries().forEach(e->results.computeIfAbsent(e.region(),k->new ArrayList<>()).add(places.get(e.team())));
        results.values().forEach(v->v.sort(Integer::compareTo));
        return results.keySet().stream().sorted((a,b)->{var first=results.get(a);var second=results.get(b);
            for(int i=0;i<Math.max(first.size(),second.size());i++){int c=Integer.compare(i<first.size()?first.get(i):100,i<second.size()?second.get(i):100);if(c!=0)return c;}
            return drawKey("REGIONAL_TIE",a).compareTo(drawKey("REGIONAL_TIE",b));}).toList();
    }
}
