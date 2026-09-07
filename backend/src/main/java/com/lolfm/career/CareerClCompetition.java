package com.lolfm.career;

import java.time.LocalDate;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import static com.lolfm.career.CareerRosterStore.*;

/** CL graph mutations share the enclosing durable Competition transaction. */
final class CareerClCompetition {
    private final CareerCompetitionRelationalStore store;private final JdbcTemplate db;
    CareerClCompetition(CareerCompetitionRelationalStore store){this.store=store;db=store.jdbc;}
    void initialize(String career,int year){
        if(!CareerClStore.active(db,career,year))return;
        if(db.queryForObject("SELECT COUNT(*) FROM career_competition_instance WHERE career_id=? AND calendar_season_year=? AND competition_id='LCK_CL'",Integer.class,career,year)>0){CareerClStore.load(db,career,year);return;}
        CareerClStore.initializeState(db,career,year);
        String input=hash(write(List.of(career,year,store.careerBinding(career).rootSeed(),CareerClPolicy.VERSION,CareerClPolicy.schedule(year))));
        db.update("""
            INSERT INTO career_competition_instance(career_id,calendar_season_year,competition_id,rule_status,lifecycle_status,
                blocking_reason,source_input_hash,revision,state_hash,created_at,updated_at,hash_algorithm,materialization_policy_id,materialization_receipt_hash)
            VALUES (?,?,'LCK_CL','GAME_POLICY_DEFINED','READY',NULL,?,0,?,?,?,?,?,?)
            """,career,year,input,"0".repeat(64),store.now(),store.now(),CareerCompetitionRelationalStore.INSTANCE_HASH_ALGORITHM,CareerClPolicy.VERSION,input);
        for(var f:CareerClPolicy.schedule(year))insert(career,year,f.id(),"CL_REGULAR",f.date(),"BO3",f.first(),f.second());
        // Activate observation and AI registration on the carried market date, including January 1.
        CareerClStore.prepare(db,career,year);
    }
    void insert(String career,int year,String id,String stage,LocalDate date,String format,String first,String second){
        if(db.queryForObject("SELECT COUNT(*) FROM career_competition_fixture WHERE career_id=? AND calendar_season_year=? AND competition_id='LCK_CL' AND match_id=?",Integer.class,career,year,id)>0)return;
        var owner=store.careerBinding(career);String identity=career+'|'+year+"|LCK_CL|"+id;
        var fixture=new CareerCompetitionAggregate.Fixture(id,"competition_fixture_"+hash(identity),date,format,true,
            new CareerCompetitionRules.ParticipantSelector("INITIAL_BOOTSTRAP_TEAM",first),new CareerCompetitionRules.ParticipantSelector("INITIAL_BOOTSTRAP_TEAM",second),
            first,second,"READY",Set.of(first,second).contains(owner.managedTeamCode())?"PLAYER_CONTROLLED":"FULL_AUTO",
            CareerCompetitionAggregate.deriveSeed(owner.rootSeed(),year,CareerClPolicy.ID,id),"series_"+hash("SERIES|"+identity),List.of(),List.of(),null,null,null);
        int order=db.queryForObject("SELECT COUNT(*)+1 FROM career_competition_fixture WHERE career_id=? AND calendar_season_year=? AND competition_id='LCK_CL'",Integer.class,career,year);
        store.insertFixture(career,year,CareerClPolicy.ID,fixture,stage,order,null,null,first,null,CareerClPolicy.SIDE,"GAME_DERIVED_SCHEDULE_POLICY");
    }
    void advance(String career,int year){
        var matches=CareerDomesticEvidence.competition(db,store.json,career,year,CareerClPolicy.ID,"CL_REGULAR");
        var outcomes=new TreeMap<String,CareerDomesticTiebreak.Outcome>();
        db.query("SELECT match_id,winner_team_code,loser_team_code FROM career_competition_fixture WHERE career_id=? AND calendar_season_year=? AND competition_id='LCK_CL' AND lifecycle_status='COMPLETED'",(org.springframework.jdbc.core.RowCallbackHandler)r->outcomes.put(r.getString(1),new CareerDomesticTiebreak.Outcome(r.getString(2),r.getString(3))),career,year);
        db.update("UPDATE career_competition_instance SET lifecycle_status=?,blocking_reason=NULL WHERE career_id=? AND calendar_season_year=? AND competition_id='LCK_CL'",outcomes.containsKey("CL_FINAL")?"COMPLETED":"RUNNING",career,year);
        if(matches.size()!=90)return;
        var decision=CareerDomesticRanking.development(CareerClPolicy.TEAMS,matches);var state=CareerClStore.load(db,career,year);
        var order=new ArrayList<String>();boolean waiting=false;int place=1;
        String identity=hash(write(List.of(career,year,CareerClPolicy.VERSION,decision,matches)));
        var strength=new TreeMap<String,Integer>();CareerClPolicy.TEAMS.forEach(t->strength.put(t,0));
        LocalDate today=CareerMarketStore.date(db,career);LocalDate due=today.isAfter(LocalDate.of(year,9,1))?today:LocalDate.of(year,9,1);
        for(var group:decision.groups()){
            if(group.teams().size()==1||place>6){order.addAll(group.teams());place+=group.teams().size();continue;}
            var seeds=CareerDomesticRanking.tieSeeds(group,matches,strength,identity);
            var progress=CareerDomesticTiebreak.advance("CL_TB_"+identity.substring(0,10)+"_"+place,seeds,outcomes,matches,strength,7-place);
            for(var f:progress.pending())insert(career,year,f.matchId(),"CL_TIEBREAKER",due,"BO1",f.first(),f.second());
            if(progress.ranking().isEmpty())waiting=true;else order.addAll(progress.ranking());place+=group.teams().size();
        }
        if(waiting)return;
        if(state.ranking().isEmpty())CareerClStore.save(db,career,year,new CareerClStore.State(state.revision()+1,state.lineups(),state.registered(),order,state.supplyIssued()));
        insert(career,year,"CL_QF_1","CL_PLAYOFFS",due,"BO5",order.get(2),order.get(5));
        insert(career,year,"CL_QF_2","CL_PLAYOFFS",due,"BO5",order.get(3),order.get(4));
        LocalDate semi=due.isAfter(LocalDate.of(year,9,4))?due:LocalDate.of(year,9,4);
        if(outcomes.containsKey("CL_QF_1"))insert(career,year,"CL_SF_1","CL_PLAYOFFS",semi,"BO5",order.get(1),outcomes.get("CL_QF_1").winner());
        if(outcomes.containsKey("CL_QF_2"))insert(career,year,"CL_SF_2","CL_PLAYOFFS",semi,"BO5",order.get(0),outcomes.get("CL_QF_2").winner());
        if(outcomes.containsKey("CL_SF_1")&&outcomes.containsKey("CL_SF_2"))insert(career,year,"CL_FINAL","CL_PLAYOFFS",due.isAfter(LocalDate.of(year,9,7))?due:LocalDate.of(year,9,7),"BO5",outcomes.get("CL_SF_1").winner(),outcomes.get("CL_SF_2").winner());
    }
}
