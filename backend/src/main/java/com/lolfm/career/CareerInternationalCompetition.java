package com.lolfm.career;

import com.lolfm.player.GlobalTeamRosterCatalog.TeamKey;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.RowCallbackHandler;

/** Transaction participant using the existing graph, binding, receipt and durable job tables. */
final class CareerInternationalCompetition {
    private final CareerCompetitionRelationalStore store;
    private final CareerInternationalParticipants participants;
    CareerInternationalCompetition(CareerCompetitionRelationalStore store, CareerInternationalParticipants participants) {
        this.store=store;this.participants=participants;
    }
    void reconcile(String career,int year){
        if(participants==null || !store.isCurrentRules(career,year))return;
        boolean changed=false;
        String asian="ASIAN_GAMES_LOL_RELEASE";
        var excluded=store.instance(career,year,asian);
        if(!"EXCLUDED_BY_GAME_POLICY".equals(excluded.lifecycleStatus())){
            store.jdbc.update("UPDATE career_competition_instance SET rule_status = 'GAME_POLICY_DEFINED', lifecycle_status = 'EXCLUDED_BY_GAME_POLICY', blocking_reason = NULL, materialization_policy_id = ?, materialization_receipt_hash = ?, revision = revision + 1 WHERE career_id = ? AND calendar_season_year = ? AND competition_id = ?",
                    CareerInternationalRules.POLICY,CareerInternationalRules.RESOURCE_HASH,career,year,asian);
            store.refreshInstanceHash(career,year,asian);changed=true;
        }
        for(String competition:CareerInternationalRules.COMPETITIONS){
            var state=load(store,career,year,competition);
            if(state==null){
                var instance=store.instance(career,year,competition);
                Integer history=store.jdbc.queryForObject("SELECT COUNT(*) FROM career_competition_fixture WHERE career_id = ? AND calendar_season_year = ? AND competition_id = ?",Integer.class,career,year,competition);
                if(history!=0) {
                    if (!"INTERNATIONAL_EXTENSION_HISTORY_CONFLICT".equals(instance.blockingReason())) {
                        store.jdbc.update("UPDATE career_competition_instance SET lifecycle_status = 'BLOCKED', blocking_reason = 'INTERNATIONAL_EXTENSION_HISTORY_CONFLICT', revision = revision + 1 WHERE career_id = ? AND calendar_season_year = ? AND competition_id = ?", career, year, competition);
                        store.refreshInstanceHash(career, year, competition); changed = true;
                    }
                    continue;
                }
                state=register(career,year,competition);
                if(state==null){
                    if(!"WAITING_FOR_QUALIFICATION".equals(instance.lifecycleStatus())){
                        store.jdbc.update("UPDATE career_competition_instance SET rule_status = 'GAME_POLICY_DEFINED', lifecycle_status = 'WAITING_FOR_QUALIFICATION', blocking_reason = 'INTERNATIONAL_QUALIFICATION_REQUIRED', materialization_policy_id = ?, materialization_receipt_hash = ?, revision = revision + 1 WHERE career_id = ? AND calendar_season_year = ? AND competition_id = ?",CareerInternationalRules.POLICY,CareerInternationalRules.RESOURCE_HASH,career,year,competition);
                        store.refreshInstanceHash(career,year,competition);changed=true;
                    }
                    continue;
                }
                String registrationHash=CareerInternationalRules.hash(write(state));
                store.jdbc.update("INSERT INTO career_international_state(career_id, calendar_season_year, competition_id, state_json, state_hash) VALUES (?, ?, ?, ?, ?)",career,year,competition,write(state),registrationHash);
                store.jdbc.update("UPDATE career_competition_instance SET rule_status = 'GAME_POLICY_DEFINED', source_input_hash = ?, materialization_policy_id = ?, materialization_receipt_hash = ?, revision = revision + 1 WHERE career_id = ? AND calendar_season_year = ? AND competition_id = ?",
                        registrationHash,CareerInternationalRules.POLICY,registrationHash,career,year,competition);
                changed=true;
            }
            var outcomes=outcomes(store,career,year,competition);
            var next=state.withPlan(CareerInternationalTournament.project(state,outcomes));
            if(!write(next).equals(write(state))){
                String serialized=write(next);
                store.jdbc.update("UPDATE career_international_state SET state_json = ?, state_hash = ? WHERE career_id = ? AND calendar_season_year = ? AND competition_id = ?",serialized,CareerInternationalRules.hash(serialized),career,year,competition);
                changed=true;
            }
            var binding=store.careerBinding(career);
            String managed=CompetitionRosterSnapshot.managedToken(binding.managedTeamCode());
            var existing=store.jdbc.query("SELECT match_id FROM career_competition_fixture WHERE career_id = ? AND calendar_season_year = ? AND competition_id = ?",(r,n)->r.getString(1),career,year,competition);
            for(var bout:next.plan().bouts())if(!existing.contains(bout.id())){
                String identity=CareerInternationalRules.hash(career+'|'+year+'|'+competition+'|'+bout.id());
                var fixture=new CareerCompetitionAggregate.Fixture(bout.id(),"competition_fixture_"+identity,bout.date(),bout.format(),true,
                        new CareerCompetitionRules.ParticipantSelector("REGISTERED_TEAM",bout.first()),new CareerCompetitionRules.ParticipantSelector("REGISTERED_TEAM",bout.second()),
                        bout.first(),bout.second(),"READY",List.of(bout.first(),bout.second()).contains(managed)?"PLAYER_CONTROLLED":"FULL_AUTO",
                        CareerCompetitionAggregate.deriveSeed(binding.rootSeed(),year,competition,bout.id()),"series_"+identity,List.of(),List.of(),null,null,null);
                store.insertFixture(career,year,competition,fixture,bout.stage(),bout.order(),bout.group(),null,bout.selectionOwner(),null,bout.sidePolicy(),"GAME_DERIVED_SCHEDULE_POLICY");changed=true;
            }
            var instance=store.instance(career,year,competition);
            String status=next.plan().complete()?"COMPLETED":"MATERIALIZED";
            if(!status.equals(instance.lifecycleStatus())||instance.blockingReason()!=null){
                store.jdbc.update("UPDATE career_competition_instance SET lifecycle_status = ?, blocking_reason = NULL WHERE career_id = ? AND calendar_season_year = ? AND competition_id = ?",status,career,year,competition);changed=true;
            }
            store.refreshInstanceHash(career,year,competition);
        }
        if(changed)store.refreshCycleHash(career,year);
    }
    private CareerInternationalState register(String career,int year,String competition){
        // Year rollover is outside V1; never reuse the 2026 initial reference as a future played result.
        if(store.findCycle(career,year,false).getFirst().seasonOrdinal()!=1)return null;
        String dependency=switch(competition){case "FIRST_STAND"->"LCK_CUP";case "MSI"->"LCK_ROAD_TO_MSI";case "EWC_LOL"->"MSI";default->"LCK_PLAYOFFS";};
        if(!"COMPLETED".equals(store.instance(career,year,dependency).lifecycleStatus()))return null;
        var fst=load(store,career,year,"FIRST_STAND");var msi=load(store,career,year,"MSI");
        if(competition.equals("MSI")&&(fst==null||!fst.plan().complete()))return null;
        if((competition.equals("WORLDS")||competition.equals("EWC_LOL"))&&(msi==null||!msi.plan().complete()))return null;
        List<String> domestic=new ArrayList<>();
        String source=competition.equals("FIRST_STAND")?"LCK_CUP":"LCK_ROAD_TO_MSI";
        Map<String,String> outputs=new LinkedHashMap<>();
        store.jdbc.query("SELECT output_id, team_code FROM career_competition_output WHERE career_id = ? AND calendar_season_year = ? AND competition_id = ? ORDER BY output_id",
                (RowCallbackHandler)r->outputs.put(r.getString(1),r.getString(2)),career,year,source);
        if(competition.equals("WORLDS")){
            var finalRanking=store.finalRanking(career,year);if(finalRanking==null)return null;
            domestic.addAll(finalRanking.ranking().stream().map(CareerCompetitionAggregate.SeededTeam::teamCode).toList());
        }else{
            String prefix=competition.equals("FIRST_STAND")?"FIRST_STAND_LCK_SEED_":"MSI_LCK_SEED_";
            for(int i=1;i<=2;i++){String team=outputs.get(prefix+i);if(team==null)return null;domestic.add(team);}
            if(competition.equals("EWC_LOL")){
                var ranking=store.jdbc.query("SELECT decision_json FROM career_domestic_ranking_decision WHERE career_id = ? AND calendar_season_year = ? AND competition_id = 'LCK_REGULAR_R1_R2' AND decision_id = 'R1_R2' AND lifecycle_status = 'SEALED'",
                        (r,n)->CareerDomesticEvidence.read(store.json,r.getString(1),CareerDomesticCompetition.RankingState.class),career,year);
                if(ranking.size()!=1)return null;
                for(String team:ranking.getFirst().ranking())if(!domestic.contains(team))domestic.add(team);
            }
        }
        var selection=participants.overseas(career,year,competition);
        List<String> regions=competition.equals("FIRST_STAND")?CareerInternationalRules.REFERENCE_REGIONS:
                competition.equals("MSI")?fst.plan().regionalPerformance():msi.plan().regionalPerformance();
        // Capture all ranking inputs (including nonselected foreign candidates), source domestic hash and prior game performance.
        String evidence=write(List.of(selection,store.instance(career,year,dependency).stateHash(),outputs,domestic,regions,
                msi==null?"NO_MSI_INPUT":msi.plan()));
        return CareerInternationalRegistration.create(career,year,competition,store.careerBinding(career).rootSeed(),selection,
                domestic.stream().map(t->participants.roster(new TeamKey("LCK",t))).toList(),evidence,regions,msi);
    }
    static CareerInternationalState load(CareerCompetitionRelationalStore store,String career,int year,String competition){
        var values=store.jdbc.query("SELECT state_json, state_hash FROM career_international_state WHERE career_id = ? AND calendar_season_year = ? AND competition_id = ?",(r,n)->{
            String json=r.getString(1);if(!CareerInternationalRules.hash(json).equals(r.getString(2)))throw new IllegalStateException("INTERNATIONAL_STATE_INTEGRITY");
            var state=CareerDomesticEvidence.read(store.json,json,CareerInternationalState.class);
            if(!state.careerId().equals(career)||state.year()!=year||!state.competitionId().equals(competition))throw new IllegalStateException("INTERNATIONAL_STATE_SCOPE");
            return state;},career,year,competition);
        return values.isEmpty()?null:values.getFirst();
    }
    static Map<String,CareerInternationalTournament.Outcome> outcomes(CareerCompetitionRelationalStore store,String career,int year,String competition){
        Map<String,CareerInternationalTournament.Outcome> outcomes=new LinkedHashMap<>();
        store.jdbc.query("SELECT f.match_id, f.winner_team_code, f.loser_team_code FROM career_competition_fixture f JOIN career_competition_application a ON a.receipt_hash = f.completion_receipt_hash AND a.career_id = f.career_id AND a.calendar_season_year = f.calendar_season_year AND a.competition_id = f.competition_id AND a.match_id = f.match_id WHERE f.career_id = ? AND f.calendar_season_year = ? AND f.competition_id = ? AND f.lifecycle_status = 'COMPLETED' ORDER BY f.match_order",
                (RowCallbackHandler)r->outcomes.put(r.getString(1),new CareerInternationalTournament.Outcome(r.getString(2),r.getString(3))),career,year,competition);
        return outcomes;
    }
    private String write(Object value){try{return store.json.copy().enable(com.fasterxml.jackson.databind.SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS).writeValueAsString(value);}catch(java.io.IOException e){throw new IllegalStateException(e);}}
}
