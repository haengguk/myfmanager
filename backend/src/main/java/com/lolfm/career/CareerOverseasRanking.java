package com.lolfm.career;

import java.util.*;
import static com.lolfm.career.CareerOverseasTournament.*;
import static com.lolfm.career.CareerOverseasRules.*;

/** Group-aware comparators keep multi-team head-to-head ties transitive. */
final class CareerOverseasRanking {
    record Record(String team,int wins,int losses,int gameWins,int gameLosses,int strengthOfVictory){}
    record Decision(List<String> order,List<List<String>> unresolved,Map<String,Record> records){}
    static Decision rank(Event event,List<String> teams,List<Bout> fixtures,Map<String,Score> scores,Map<String,Integer> previous,long seed){
        var records=records(teams,fixtures,scores);var tied=partition(teams,Comparator.comparingInt((String t)->records.get(t).wins()).reversed());
        boolean spring=event==Event.LEC_SPRING,summer=event==Event.LEC_SUMMER;
        if(!spring&&!summer)tied=refine(tied,g->Comparator.comparingInt((String t)->records.get(t).gameWins()-records.get(t).gameLosses()).reversed());
        tied=refine(tied,g->{var h=records(g,fixtures,scores);return Comparator.comparingInt((String t)->h.get(t).wins()).reversed();});
        if(spring||summer)tied=refine(tied,g->{var h=records(g,fixtures,scores);return (a,b)->percentage(h.get(b),h.get(a));});
        else tied=refine(tied,g->{var h=records(g,fixtures,scores);return Comparator.comparingInt((String t)->h.get(t).gameWins()-h.get(t).gameLosses()).reversed();});
        if(summer)tied=refine(tied,g->(a,b)->percentage(records.get(b),records.get(a)));
        if(spring||summer){tied=refine(tied,g->Comparator.comparingInt((String t)->records.get(t).strengthOfVictory()).reversed());tied=refine(tied,g->Comparator.comparingInt(t->previous.getOrDefault(t,Integer.MAX_VALUE)));}
        var unresolved=tied.stream().filter(g->g.size()>1).map(List::copyOf).toList();var order=new ArrayList<String>();
        for(var g:tied){var sorted=new ArrayList<>(g);sorted.sort(Comparator.comparing(t->CareerRosterStore.hash(seed+"|RANK_LOT|"+t)));order.addAll(sorted);}
        return new Decision(List.copyOf(order),unresolved,records);
    }
    private static int percentage(Record a,Record b){return Long.compare((long)a.gameWins()*Math.max(1,b.gameWins()+b.gameLosses()),(long)b.gameWins()*Math.max(1,a.gameWins()+a.gameLosses()));}
    static Map<String,Record> records(List<String> teams,List<Bout> fixtures,Map<String,Score> scores){
        var totals=new TreeMap<String,int[]>();teams.forEach(t->totals.put(t,new int[5]));
        for(var f:fixtures){var s=scores.get(f.id());if(s==null||!totals.containsKey(f.first())||!totals.containsKey(f.second()))continue;
            var a=totals.get(f.first());var b=totals.get(f.second());a[s.first()>s.second()?0:1]++;b[s.second()>s.first()?0:1]++;a[2]+=s.first();a[3]+=s.second();b[2]+=s.second();b[3]+=s.first();}
        for(var f:fixtures){var s=scores.get(f.id());if(s!=null&&totals.containsKey(f.first())&&totals.containsKey(f.second()))totals.get(s.first()>s.second()?f.first():f.second())[4]+=totals.get(s.first()>s.second()?f.second():f.first())[0];}
        var result=new TreeMap<String,Record>();totals.forEach((t,v)->result.put(t,new Record(t,v[0],v[1],v[2],v[3],v[4])));return Map.copyOf(result);
    }
    private static List<List<String>> refine(List<List<String>> groups,java.util.function.Function<List<String>,Comparator<String>> comparison){var result=new ArrayList<List<String>>();for(var g:groups)result.addAll(partition(g,comparison.apply(g)));return result;}
    private static List<List<String>> partition(List<String> source,Comparator<String> comparator){var ordered=new ArrayList<>(source);ordered.sort(comparator.thenComparing(t->t));var result=new ArrayList<List<String>>();for(String t:ordered){if(result.isEmpty()||comparator.compare(result.getLast().getFirst(),t)!=0)result.add(new ArrayList<>());result.getLast().add(t);}return result;}
}
