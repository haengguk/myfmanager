package com.lolfm.career;

import java.util.*;
import static com.lolfm.career.CareerOverseasRules.*;
import static com.lolfm.career.CareerRosterStore.*;

/** Actual qualification paths and current legal rosters are separate inputs to sealed registration. */
final class CareerOverseasQualification {
    static final String POLICY="ACTUAL_OVERSEAS_RESULTS_AND_CP_V1";
    static final class Waiting extends RuntimeException{Waiting(String message){super(message);}}
    static List<Event> required(String competition){return switch(competition){
        case "FIRST_STAND"->List.of(Event.LPL_SPLIT_1,Event.LEC_VERSUS,Event.LCP_SPLIT_1,Event.CBLOL_COPA,Event.LCS_LOCK_IN);
        case "MSI","EWC_LOL"->List.of(Event.LPL_SPLIT_2,Event.LEC_SPRING,Event.LCP_SPLIT_2,Event.CBLOL_ETAPA_1,Event.LCS_SPRING);
        case "WORLDS"->List.of(Event.LPL_REGIONAL_FINALS,Event.LEC_SUMMER,Event.LCP_SPLIT_3,Event.CBLOL_ETAPA_2,Event.LCS_SUMMER);default->throw new IllegalArgumentException("OVERSEAS_QUALIFICATION_EVENT");};}
    record Order(List<String> teams,Map<String,String> paths,Set<String> playoffEligible){}
    static Order order(String competition,CareerOverseasStore.State latest,CareerOverseasStore.State split3,Map<String,Integer> cp){
        var rank=new ArrayList<>(latest.plan().ranking());var paths=new TreeMap<String,String>();var eligible=new TreeSet<>(latest.plan().playoffTeams());String path="ACTUAL_"+latest.input().event()+"_PLACEMENT";rank.forEach(t->paths.put(t,path));
        if(latest.input().event()==Event.LPL_REGIONAL_FINALS){if(split3==null||!split3.plan().complete())throw new Waiting("OVERSEAS_RESULT_REQUIRED:LPL_SPLIT_3");var cpRank=CareerOverseasStore.cpOrder(split3,cp);String champion=split3.plan().ranking().getFirst();cpRank.remove(champion);String pointSeed=cpRank.removeFirst();var ordered=new ArrayList<>(List.of(champion,pointSeed));ordered.addAll(rank);cpRank.stream().filter(t->!ordered.contains(t)).forEach(ordered::add);rank=ordered;paths.put(champion,"ACTUAL_LPL_SPLIT_3_CHAMPION");paths.put(pointSeed,"ACTUAL_LPL_HIGHEST_CP_EXCLUDING_CHAMPION");for(String t:cpRank)paths.putIfAbsent(t,"ACTUAL_LPL_CP_SUCCESSION");eligible=new TreeSet<>(split3.plan().playoffTeams());
        }else if(latest.input().event()==Event.LCP_SPLIT_2||latest.input().event()==Event.LCP_SPLIT_3){int direct=latest.input().event()==Event.LCP_SPLIT_3?2:1;var selected=new ArrayList<>(rank.subList(0,direct));var cpRank=CareerOverseasStore.cpOrder(latest,cp);cpRank.removeAll(selected);String pointSeed=cpRank.removeFirst();selected.add(pointSeed);selected.addAll(cpRank);rank=selected;paths.put(pointSeed,"ACTUAL_LCP_HIGHEST_CP_EXCLUDING_DIRECT_QUALIFIERS");}
        return new Order(List.copyOf(rank),paths,eligible);
    }
    static CareerInternationalParticipants.Selection select(CareerCompetitionRelationalStore store,String career,int year,String competition){
        var rankings=new TreeMap<String,List<CompetitionRosterSnapshot.Roster>>();var qualifications=new TreeMap<String,List<CareerInternationalParticipants.Qualification>>();var eligible=new TreeSet<String>();var evidence=new TreeMap<String,String>();
        var market=CareerMarketStore.engine(store.jdbc,career,year,CareerMarketStore.load(store.jdbc,career));
        var msi=competition.equals("WORLDS")?CareerInternationalCompetition.load(store,career,year,"MSI"):null;
        String champion=msi==null?null:msi.plan().champion();String championRegion=msi==null?null:msi.entries().stream().filter(e->e.team().equals(champion)).findFirst().orElseThrow().region();String otherBonus=msi==null?null:msi.plan().regionalPerformance().stream().filter(r->!r.equals(championRegion)).findFirst().orElseThrow();
        for(Event event:required(competition)){
            var state=CareerOverseasStore.load(store.jdbc,career,year,event);if(state==null||!state.plan().complete())throw new Waiting("OVERSEAS_RESULT_REQUIRED:"+event.name());
            var split=event==Event.LPL_REGIONAL_FINALS?CareerOverseasStore.load(store.jdbc,career,year,Event.LPL_SPLIT_3):null;var cp=new TreeMap<>(state.input().priorPoints());state.plan().points().forEach((t,p)->cp.merge(t,p,Integer::sum));var order=order(competition,state,split,cp);eligible.addAll(order.playoffEligible());
            String resultHash=hash(write(state));evidence.put(event.name(),resultHash);var rosters=new ArrayList<CompetitionRosterSnapshot.Roster>();var qualified=new ArrayList<CareerInternationalParticipants.Qualification>();
            int required=competition.equals("WORLDS")?(event.league.equals("CBLOL")?2:3)+(event.league.equals(championRegion)||event.league.equals(otherBonus)?1:0):competition.equals("FIRST_STAND")?(event.league.equals("LPL")?2:1):competition.equals("MSI")?(event.league.equals("CBLOL")?1:2):Set.of("LPL","LEC").contains(event.league)?3:2;
            for(int i=0;i<order.teams().size();i++){String team=order.teams().get(i);CompetitionRosterSnapshot.Roster roster;
                try{roster=CareerOverseasRoster.roster(market,team);}catch(CareerException missing){if(i<required||team.equals(champion)&&eligible.contains(team))throw new Waiting("ROSTER_REPAIR_REQUIRED:"+team);else continue;}
                rosters.add(roster);qualified.add(new CareerInternationalParticipants.Qualification(team,i+1,order.paths().get(team),event.name(),resultHash));
            }
            rankings.put(event.league,List.copyOf(rosters));qualifications.put(event.league,List.copyOf(qualified));
        }
        // LCK playoff eligibility is derived from its actual materialized bracket, never a universal top-six rule.
        store.jdbc.query("SELECT DISTINCT first_team_code,second_team_code FROM career_competition_fixture WHERE career_id=? AND calendar_season_year=? AND competition_id='LCK_PLAYOFFS'",(org.springframework.jdbc.core.RowCallbackHandler)r->{eligible.add("LCK:"+r.getString(1));eligible.add("LCK:"+r.getString(2));},career,year);
        return new CareerInternationalParticipants.Selection(POLICY,hash(write(evidence)),rankings,qualifications,eligible);
    }
}
