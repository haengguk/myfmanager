package com.lolfm.career;

import static com.lolfm.career.CareerRosterStore.*;
import java.time.LocalDate;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;

/** Effective observations only. Historical absence is never inferred from today's roster. */
final class CareerHistoryStore {
    record Registration(String playerId,boolean complete,Integer firstLckYear,boolean foreignRegistered,Set<Integer> candidateYears) {}
    record GrowthPlayer(Map<com.lolfm.domain.PlayerSkill,Integer> internalRatings,Map<String,Integer> internalProficiencies,int fatigue) {}
    record Growth(String kind,LocalDate observedOn,Map<String,GrowthPlayer> players) {}
    record Title(String competition,String winner,String finalSeries,LocalDate date,String coverage,List<String> registered,List<String> contributors,List<String> bench,long revision) {}
    record Operating(String eventId,LocalDate date,String kind,String playerId,String name,String team,String referenceId,String reason) {}
    static void observe(JdbcTemplate db,String career,int year,String key,LocalDate date,Object value,boolean replace) {
        String json=write(value),digest=hash(json);
        var prior=db.query("SELECT observation_hash FROM career_record_observation WHERE career_id=? AND season_year=? AND observation_key=?",(r,n)->r.getString(1),career,year,key);
        if(prior.isEmpty())db.update("INSERT INTO career_record_observation VALUES (?,?,?,?,?,?)",career,year,key,date,json,digest);
        else if(replace&&!prior.getFirst().equals(digest))db.update("UPDATE career_record_observation SET observed_date=?,observation_json=?,observation_hash=? WHERE career_id=? AND season_year=? AND observation_key=?",date,json,digest,career,year,key);
        if(prior.isEmpty()||replace)CareerObservationIndex.index(db,career,year,key,json,digest);
        if(prior.isEmpty())CareerInboxStore.observation(db,career,year,key,date,read(json,com.fasterxml.jackson.databind.JsonNode.class));
    }
    static void growth(JdbcTemplate db,String career,int year,String kind,LocalDate date,CareerDevelopmentState state){if(state==null)return;var observed=new TreeMap<String,GrowthPlayer>();state.players().forEach((id,p)->observed.put(id,new GrowthPlayer(p.internalRatings(),p.internalProficiencies(),p.fatigue())));observe(db,career,year,kind,date,new Growth(kind,date,observed),kind.equals("CLOSING_FINAL"));}
    static void operating(JdbcTemplate db,String career,int year,CareerMarketState before,CareerMarketEngine engine,State oldRoster) {
        var previous=new HashSet<String>();before.events().forEach(e->previous.add(e.eventId()));
        for(var e:engine.state().events())if(!previous.contains(e.eventId())) {
            var p=e.playerId()==null?null:engine.directory.players().get(e.playerId());
            observe(db,career,year,"EVENT:"+hash(e.eventId()),e.date(),new Operating(e.eventId(),e.date(),e.kind(),e.playerId(),p==null?e.playerId():p.nickname(),e.team(),e.referenceId(),e.reason()),false);
            if(e.referenceId()!=null&&engine.state().management()!=null){var management=engine.state().management();var trade=management.trades().get(e.referenceId());var loan=management.loans().get(e.referenceId());
                if(trade!=null)CareerObservationIndex.related(db,career,year,"EVENT:"+hash(e.eventId()),List.of(trade.terms().seller(),trade.terms().buyer()));
                if(loan!=null)CareerObservationIndex.related(db,career,year,"EVENT:"+hash(e.eventId()),List.of(loan.parentTeam(),loan.borrowingTeam()));}

        }
        if(!oldRoster.members().equals(engine.members))registrations(db,career,year,engine);
    }
    static void registrations(JdbcTemplate db,String career,int year,CareerMarketEngine engine) {
        var history=new HashMap<String,Registration>();db.query("SELECT observation_json FROM career_record_observation WHERE career_id=? AND observation_key LIKE 'ROOKIE:%'",(org.springframework.jdbc.core.RowCallbackHandler)r->{var v=read(r.getString(1),Registration.class);history.put(v.playerId(),v);},career);
        for(var member:engine.members.values()) {
            var old=history.get(member.playerId());var life=engine.lifecycle==null?null:engine.lifecycle.people.get(member.playerId());
            boolean known=old!=null?old.complete():life!=null&&life.source().equals("GENERATED");
            boolean lck=member.ownerTeam()!=null&&member.ownerTeam().startsWith("LCK:")&&member.squad().equals("FIRST_TEAM");boolean foreign=member.ownerTeam()!=null&&!member.ownerTeam().startsWith("LCK:");
            var next=new Registration(member.playerId(),known,old!=null&&old.firstLckYear()!=null?old.firstLckYear():lck?year:null,foreign||old!=null&&old.foreignRegistered(),old==null?Set.of():old.candidateYears());
            if(!next.equals(old))observe(db,career,0,"ROOKIE:"+member.playerId(),engine.state().processedThrough(),next,true);
        }
    }
    static void directRegistration(JdbcTemplate db,String career,int year,Membership member,LocalDate date) {
        var rows=db.query("SELECT observation_json FROM career_record_observation WHERE career_id=? AND season_year=0 AND observation_key=?",(r,n)->read(r.getString(1),Registration.class),career,"ROOKIE:"+member.playerId());
        var old=rows.isEmpty()?new Registration(member.playerId(),false,null,false,Set.of()):rows.getFirst();
        boolean lck=member.ownerTeam()!=null&&member.ownerTeam().startsWith("LCK:")&&member.squad().equals("FIRST_TEAM");
        Integer firstYear=old.firstLckYear();if(firstYear==null&&lck)firstYear=year;
        observe(db,career,0,"ROOKIE:"+member.playerId(),date,new Registration(member.playerId(),old.complete(),firstYear,old.foreignRegistered(),old.candidateYears()),true);
    }
    static void candidateHistory(JdbcTemplate db,String career,int year,List<CareerAwardsStore.Candidate> candidates,LocalDate date) {
        for(var p:candidates)if(p.eligibility().equals("TRUE")) {
            var rows=db.query("SELECT observation_json FROM career_record_observation WHERE career_id=? AND season_year=0 AND observation_key=?",(r,n)->read(r.getString(1),Registration.class),career,"ROOKIE:"+p.playerId());
            if(rows.isEmpty())continue;var old=rows.getFirst();var years=new TreeSet<>(old.candidateYears());years.add(year);observe(db,career,0,"ROOKIE:"+p.playerId(),date,new Registration(p.playerId(),old.complete(),old.firstLckYear(),old.foreignRegistered(),years),true);
        }
    }
    static void championship(JdbcTemplate db,String career,int year,String event,CareerAwardClosure.Fixture finalFixture,List<CareerRecordsStore.Series> series,long revision) {
        var last=series.stream().filter(s->s.seriesId().equals(finalFixture.series())).findFirst();if(last.isEmpty())return;
        var finalRecord=last.get();String winner=finalRecord.winner();
        var roster=saved(db,career,year);var registered=new TreeSet<String>();
        if(CareerInternationalRules.COMPETITIONS.contains(event))db.query("SELECT pool_json,pool_hash FROM career_registered_player_pool WHERE career_id=? AND season_year=? AND competition_id=?",(org.springframework.jdbc.core.RowCallbackHandler)r->registered.addAll(pool(r.getString(1),r.getString(2)).getOrDefault(winner,List.of())),career,year,event);
        else if(event.equals("LCK_CL"))registered.addAll(CareerClStore.load(db,career,year).registered().getOrDefault(winner.replace(":CL",""),List.of()));
        else if(roster!=null)for(var member:roster.state().members().values())if((winner.equals(CareerRecordsStore.team(member.ownerTeam(),event))||winner.equals("LEC:KCB")&&"LEC:KC".equals(member.ownerTeam())&&member.squad().equals("DEVELOPMENT"))&&("LCK_CL".equals(event)||winner.equals("LEC:KCB")?member.squad().equals("DEVELOPMENT"):member.squad().equals("FIRST_TEAM")))registered.add(member.playerId());
        // Registration alone is insufficient after a transfer: valid employment must still match at the championship boundary.
        if(roster!=null)registered.removeIf(id->{var m=roster.state().members().get(id);return m==null||!(winner.equals(CareerRecordsStore.team(m.ownerTeam(),event))||winner.equals("LEC:KCB")&&"LEC:KC".equals(m.ownerTeam())&&m.squad().equals("DEVELOPMENT"));});
        var contributing=new ArrayList<>(series);if(event.equals("LCK_PLAYOFFS"))contributing.addAll(CareerRecordsStore.period(db,career,year,"LCK_REGULAR",revision));
        var played=new TreeSet<String>();for(var s:contributing)for(var g:s.games())for(var p:g.players())if(p.team().equals(winner))played.add(p.playerId());
        var contributors=registered.stream().filter(played::contains).toList();var bench=registered.stream().filter(p->!played.contains(p)).toList();
        observe(db,career,year,"TITLE:"+event,finalFixture.date(),new Title(event,winner,finalRecord.seriesId(),finalFixture.date(),registered.isEmpty()?"NOT_COLLECTED":CareerAwardsStore.complete(series)?"COMPLETE":"PARTIAL",List.copyOf(registered),contributors,bench,revision),false);
    }
    record Placement(String competition,String team,String kind,int from,int through,String coverage,String sourceHash,long revision) {}
    static void results(CareerCompetitionRelationalStore store,String career,int year,String event) {
        var db=store.jdbc;long revision=CareerRecordsStore.revision(db,career);LocalDate date=CareerMarketStore.date(db,career);
        var ranks=new TreeMap<String,Integer>();String source="";boolean complete="COMPLETED".equals(store.instance(career,year,event).lifecycleStatus());
        if(CareerOverseasRules.isOverseas(event)) {
            var state=CareerOverseasStore.load(db,career,year,CareerOverseasRules.Event.valueOf(event));if(state==null)return;source=hash(write(state.plan()));
            for(int i=0;i<state.plan().regularRanking().size();i++){String team=state.plan().regularRanking().get(i);observe(db,career,year,"PLACEMENT:"+event+":REGULAR:"+team,date,new Placement(event,team,"REGULAR_RANK",i+1,i+1,"COMPLETE",source,revision),false);}
            if(complete&&state.plan().complete())ranks.putAll(state.plan().placements());
        }else if(CareerInternationalRules.COMPETITIONS.contains(event)) {
            var state=CareerInternationalCompetition.load(store,career,year,event);if(state==null)return;source=hash(write(state.plan()));if(complete&&state.plan().complete())ranks.putAll(state.plan().placements());
        }else if(complete&&event.equals("LCK_PLAYOFFS")) {
            var finalRank=store.finalRanking(career,year);source=finalRank.stateHash();finalRank.ranking().forEach(r->ranks.put("LCK:"+r.teamCode(),r.seed()));
        }else if(event.equals("LCK_CL")) {
            var state=CareerClStore.load(db,career,year);source=hash(write(state));for(int i=0;i<state.ranking().size();i++){String team="LCK:"+state.ranking().get(i)+":CL";observe(db,career,year,"PLACEMENT:"+event+":REGULAR:"+team,date,new Placement(event,team,"REGULAR_RANK",i+1,i+1,"COMPLETE",source,revision),false);}
            if(complete){for(int i=6;i<state.ranking().size();i++)ranks.put("LCK:"+state.ranking().get(i)+":CL",i+1);
                db.query("SELECT match_id,winner_team_code,loser_team_code FROM career_competition_fixture WHERE career_id=? AND calendar_season_year=? AND competition_id='LCK_CL' AND lifecycle_status='COMPLETED'",(org.springframework.jdbc.core.RowCallbackHandler)r->{String match=r.getString(1);if(match.equals("CL_FINAL")){ranks.put("LCK:"+r.getString(2)+":CL",1);ranks.put("LCK:"+r.getString(3)+":CL",2);}else if(Set.of("CL_SF_1","CL_SF_2").contains(match))ranks.put("LCK:"+r.getString(3)+":CL",3);else if(Set.of("CL_QF_1","CL_QF_2").contains(match))ranks.put("LCK:"+r.getString(3)+":CL",5);},career,year);}
        }else if(complete&&event.equals("LCK_REGULAR_R3_R4")) {
            for(var decision:store.domesticDecisions(career,year))if(decision.competitionId().equals(event)&&decision.status().equals("SEALED")&&decision.detail().has("ranking")) {
                var ranking=decision.detail().path("ranking");String group=decision.detail().path("scope").asText();
                for(int i=0;i<ranking.size();i++){String team="LCK:"+ranking.get(i).asText();observe(db,career,year,"PLACEMENT:"+event+":"+group+":"+team,date,new Placement(event,team,"REGULAR_GROUP_RANK:"+group,i+1,i+1,"COMPLETE",decision.inputHash(),revision),false);}
            }
        }else if(complete&&event.equals("LCK_CUP")) {
            var outcomes=new TreeMap<String,List<String>>();var teams=new TreeSet<String>();
            db.query("SELECT match_id,winner_team_code,loser_team_code,completion_receipt_hash FROM career_competition_fixture WHERE career_id=? AND calendar_season_year=? AND competition_id=? AND lifecycle_status='COMPLETED'",(org.springframework.jdbc.core.RowCallbackHandler)row->{outcomes.put(row.getString(1),List.of(row.getString(2),row.getString(3),Objects.toString(row.getString(4),"UNAVAILABLE")));teams.add(row.getString(2));teams.add(row.getString(3));},career,year,event);
            source=hash(write(outcomes));cupPlacements(outcomes,teams).forEach((team,rank)->ranks.put("LCK:"+team,rank));
        }

        var counts=new HashMap<Integer,Integer>();ranks.values().forEach(rank->counts.merge(rank,1,Integer::sum));
        for(var rank:ranks.entrySet())observe(db,career,year,"PLACEMENT:"+event+":FINAL:"+rank.getKey(),date,new Placement(event,rank.getKey(),event.equals("LPL_REGIONAL_FINALS")?"QUALIFICATION_RESULT":"FINAL_PLACEMENT",rank.getValue(),rank.getValue()+counts.get(rank.getValue())-1,"COMPLETE",source,revision),false);
    }
    /** Elimination positions from the existing Cup bracket, including shared play-in places. */
    static Map<String,Integer> cupPlacements(Map<String,List<String>> outcomes,Set<String> teams) {
        var ranks=new TreeMap<String,Integer>();var finalResult=outcomes.get("PO_FINAL");if(finalResult==null)return ranks;
        ranks.put(finalResult.getFirst(),1);ranks.put(finalResult.get(1),2);
        var losses=Map.of("PO_LOWER_FINAL",3,"PO_LBR3",4,"PO_LBR2",5,"PO_LBR1",6,"PI_FINAL",7,"PI_R1_M1",8,"PI_R1_M2",8);
        losses.forEach((match,rank)->{var result=outcomes.get(match);if(result!=null)ranks.put(result.get(1),rank);});
        if(ranks.size()==9&&teams.size()==10)for(String team:teams)ranks.putIfAbsent(team,10);
        return ranks;
    }
    static void regularRanking(JdbcTemplate db,String career,int year,String event,String source,List<CareerCompetitionAggregate.SeededTeam> ranking) {
        var date=CareerMarketStore.date(db,career);long revision=CareerRecordsStore.revision(db,career);
        for(var row:ranking){String team="LCK:"+row.teamCode();observe(db,career,year,"PLACEMENT:"+event+":REGULAR:"+team,date,new Placement(event,team,"REGULAR_RANK",row.seed(),row.seed(),"COMPLETE",source,revision),false);}
    }
    private CareerHistoryStore() {}
}
