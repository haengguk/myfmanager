package com.lolfm.career;

import java.time.LocalDate;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import static com.lolfm.career.CareerManagementState.*;
import static com.lolfm.career.CareerRosterStore.*;

/** Captures opportunities at legal start, records facts only from an applied verified completion. */
public final class CareerAppearanceStore {
    private CareerAppearanceStore() {}
    public static void capture(JdbcTemplate jdbc,String career,int year,String identity,String series,
            String competition,CompetitionRosterSnapshot frozen) {
        if(frozen==null||!CareerMarketStore.exists(jdbc,career))return;
        if(jdbc.queryForObject("SELECT COUNT(*) FROM career_appearance_binding WHERE career_id=? AND fixture_identity=?",Integer.class,career,identity)>0)return;
        var saved=CareerMarketStore.load(jdbc,career);var engine=CareerMarketStore.engine(jdbc,career,year,saved);LocalDate date=CareerMarketStore.date(jdbc,career);
        var registration=competition==null?List.<LocalDate>of():jdbc.query("SELECT registered_date FROM career_opportunity_registration WHERE career_id=? AND season_year=? AND competition_id=?",(r,n)->r.getObject(1,LocalDate.class),career,year,competition);
        Map<String,List<String>> registered=new HashMap<>();
        if(competition!=null)jdbc.query("SELECT pool_json,pool_hash FROM career_registered_player_pool WHERE career_id=? AND season_year=? AND competition_id=?",
                (org.springframework.jdbc.core.RowCallbackHandler) r->registered.putAll(pool(r.getString(1),r.getString(2))),career,year,competition);
        var facts=new ArrayList<Opportunity>();
        for(var roster:frozen.teams().values()) {
            String team=CompetitionRosterSnapshot.token(roster.team());var selected=new HashSet<>(roster.players().stream().map(CompetitionRosterSnapshot.Starter::playerId).toList());
            for(var member:engine.members.values())if(team.equals(member.ownerTeam())) {
                var promise=engine.promise(member.playerId(),team,date);if(promise==null)continue;
                boolean eligible=engine.eligible(member.playerId(),team,date);String reason=eligible?"CONTRACT_AT_SERIES_START":"OBJECTIVE_CONTRACT_OR_ROLE_INELIGIBILITY";
                if(competition!=null&&CareerInternationalRules.COMPETITIONS.contains(competition)) {
                    // Bench/non-registration by club choice cannot erase a promise. Joining after registration can.
                    if(registration.isEmpty()&&!selected.contains(member.playerId())){eligible=false;reason="PRE_MIGRATION_REGISTRATION_EVIDENCE_UNKNOWN";}
                    else if(!registration.isEmpty()&&!selected.contains(member.playerId())&&!registered.getOrDefault(team,List.of()).contains(member.playerId())&&engine.promiseEngine.continuousSince(member.playerId(),team,date).isAfter(registration.getFirst())){eligible=false;reason="JOINED_AFTER_REGISTRATION";}
                }
                facts.add(new Opportunity(member.playerId(),team,engine.player(member.playerId()).position(),promise.promiseId(),eligible,selected.contains(member.playerId()),reason));
            }
        }
        var snapshot=new Appearance("PENDING",identity,series,year,date,0,facts);String json=write(snapshot);
        jdbc.update("INSERT INTO career_appearance_binding VALUES (?,?,?,?,NULL)",career,identity,json,hash(json));
    }
    public static void complete(JdbcTemplate jdbc,String career,String identity,String receipt,int sets) {
        if(sets<1)throw new IllegalArgumentException("COMPLETED_GAME_COUNT_REQUIRED");
        lockCareer(jdbc,career);
        var rows=jdbc.query("SELECT snapshot_json,snapshot_hash,applied_receipt FROM career_appearance_binding WHERE career_id=? AND fixture_identity=?",(r,n)->{
            if(!hash(r.getString(1)).equals(r.getString(2)))throw new IllegalStateException("APPEARANCE_SNAPSHOT_INTEGRITY");
            return new Captured(read(r.getString(1),Appearance.class),r.getString(3));
        },career,identity);
        if(rows.isEmpty())return; // A pre-migration start has no defensible promise denominator; never reconstruct it.
        var row=rows.getFirst();if(row.receipt()!=null){if(!row.receipt().equals(receipt))throw new IllegalStateException("APPEARANCE_RECEIPT_CONFLICT");return;}
        var old=CareerMarketStore.load(jdbc,career);int year=activeYear(jdbc,career);var engine=CareerMarketStore.engine(jdbc,career,year,old);var a=row.snapshot();
        engine.applyAppearance(new Appearance(receipt,a.fixtureId(),a.seriesId(),a.seasonYear(),a.date(),sets,a.opportunities()));
        CareerMarketStore.persist(jdbc,career,year,old,engine);
        jdbc.update("UPDATE career_appearance_binding SET applied_receipt=? WHERE career_id=? AND fixture_identity=?",receipt,career,identity);
    }
    public static void leagueCompleted(JdbcTemplate jdbc,String season,String fixture,String receipt,int sets) {
        var ids=jdbc.query("SELECT career_id FROM career_season WHERE season_id=?",(r,n)->r.getString(1),season);
        if(!ids.isEmpty())complete(jdbc,ids.getFirst(),"LEAGUE|"+season+'|'+fixture,receipt,sets);
    }
    private record Captured(Appearance snapshot,String receipt) {}
}
