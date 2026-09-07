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
        var saved=CareerMarketStore.load(jdbc,career);var engine=CareerMarketStore.engine(jdbc,career,year,saved);LocalDate date=saved.state().processedThrough();
        var registration=competition==null?List.<LocalDate>of():jdbc.query("SELECT registered_date FROM career_opportunity_registration WHERE career_id=? AND season_year=? AND competition_id=?",(r,n)->r.getObject(1,LocalDate.class),career,year,competition);
        Map<String,List<String>> registered=new HashMap<>();
        if(competition!=null)jdbc.query("SELECT pool_json,pool_hash FROM career_registered_player_pool WHERE career_id=? AND season_year=? AND competition_id=?",
                (org.springframework.jdbc.core.RowCallbackHandler) r->registered.putAll(pool(r.getString(1),r.getString(2))),career,year,competition);
        String squad=CareerClPolicy.isCl(competition)?"DEVELOPMENT":"FIRST_TEAM";
        requireNoCrossSquad(jdbc,career,identity,date,squad,frozen.teams().values().stream().flatMap(t->t.players().stream()).map(CompetitionRosterSnapshot.Starter::playerId).collect(java.util.stream.Collectors.toSet()));
        var facts=new ArrayList<Opportunity>();
        for(var roster:frozen.teams().values()) {
            String team=CompetitionRosterSnapshot.token(roster.team());var selected=new HashSet<>(roster.players().stream().map(CompetitionRosterSnapshot.Starter::playerId).toList());
            for(var member:engine.members.values())if(team.equals(member.ownerTeam())&&squad.equals(member.squad())) {
                var promise=engine.promise(member.playerId(),team,date);if(promise==null&&!selected.contains(member.playerId()))continue;
                boolean eligible=engine.eligible(member.playerId(),team,date);String reason=eligible?"CONTRACT_AT_SERIES_START":"OBJECTIVE_CONTRACT_OR_ROLE_INELIGIBILITY";
                if(competition!=null&&CareerInternationalRules.COMPETITIONS.contains(competition)) {
                    // Bench/non-registration by club choice cannot erase a promise. Joining after registration can.
                    if(registration.isEmpty()&&!selected.contains(member.playerId())){eligible=false;reason="PRE_MIGRATION_REGISTRATION_EVIDENCE_UNKNOWN";}
                    else if(!registration.isEmpty()&&!selected.contains(member.playerId())&&!registered.getOrDefault(team,List.of()).contains(member.playerId())&&engine.promiseEngine.continuousSince(member.playerId(),team,date).isAfter(registration.getFirst())){eligible=false;reason="JOINED_AFTER_REGISTRATION";}
                }
                if("DEVELOPMENT".equals(squad)&&!CareerClStore.load(jdbc,career,year).registered().getOrDefault(team,List.of()).contains(member.playerId())){eligible=false;reason="CL_NOT_REGISTERED_AT_START";}
                facts.add(new Opportunity(member.playerId(),team,engine.player(member.playerId()).position(),promise==null?null:promise.promiseId(),eligible,selected.contains(member.playerId()),reason));
            }
        }
        var snapshot=new Appearance("PENDING",identity,series,year,date,0,facts,squad,competition);String json=write(snapshot);
        jdbc.update("INSERT INTO career_appearance_binding VALUES (?,?,?,?,NULL)",career,identity,json,hash(json));
        CareerDevelopmentStore.capture(jdbc,career,identity);
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
        engine.applyAppearance(new Appearance(receipt,a.fixtureId(),a.seriesId(),a.seasonYear(),a.date(),sets,a.opportunities(),a.squad(),a.competitionId()));
        if(engine.lifecycle!=null)for(var opportunity:a.opportunities())if(opportunity.selected())engine.lifecycle.people.computeIfPresent(opportunity.playerId(),(id,p)->p.appeared());
        CareerMarketStore.persist(jdbc,career,year,old,engine);
        jdbc.update("UPDATE career_appearance_binding SET applied_receipt=? WHERE career_id=? AND fixture_identity=?",receipt,career,identity);
    }
    public static void leagueCompleted(JdbcTemplate jdbc,String season,String fixture,String receipt,int sets) {
        var ids=jdbc.query("SELECT career_id FROM career_season WHERE season_id=?",(r,n)->r.getString(1),season);
        if(!ids.isEmpty())complete(jdbc,ids.getFirst(),"LEAGUE|"+season+'|'+fixture,receipt,sets);
    }
    public static void complete(JdbcTemplate jdbc,String career,String identity,String receipt,java.util.List<com.lolfm.league.LeagueFixtureGameReceiptV1> games) {
        lockCareer(jdbc,career);
        CareerDevelopmentStore.complete(jdbc,career,identity,receipt,games);
        complete(jdbc,career,identity,receipt,games.size());
    }
    public static void leagueCompleted(JdbcTemplate jdbc,String season,String fixture,String receipt,java.util.List<com.lolfm.league.LeagueFixtureGameReceiptV1> games) {
        var ids=jdbc.query("SELECT career_id FROM career_season WHERE season_id=?",(r,n)->r.getString(1),season);
        if(!ids.isEmpty())complete(jdbc,ids.getFirst(),"LEAGUE|"+season+'|'+fixture,receipt,games);
    }
    static void requireNoCrossSquad(JdbcTemplate jdbc,String career,String identity,LocalDate date,String squad,Set<String> players){
        jdbc.query("SELECT fixture_identity,snapshot_json,snapshot_hash,applied_receipt FROM career_appearance_binding WHERE career_id=?",(org.springframework.jdbc.core.RowCallbackHandler)r->{
            if(identity.equals(r.getString(1)))return;if(!hash(r.getString(2)).equals(r.getString(3)))throw new IllegalStateException("APPEARANCE_SNAPSHOT_INTEGRITY");
            var old=read(r.getString(2),Appearance.class);if(!squad.equals(old.squad())&&(r.getString(4)==null||date.equals(old.date()))&&old.opportunities().stream().anyMatch(o->o.selected()&&players.contains(o.playerId())))throw CareerException.invalid("lineup","같은 날짜의 1군·CL 중복 출전 또는 진행 중인 다른 선수단 Series가 있습니다. 선발을 보완하거나 경기 완료 후 다음 날 진행하세요.");
        },career);
    }
    public record Performance(int seasonYear,LocalDate date,String seriesId,String playerId,String team,String squad,String competitionId,int sets,Map<String,Integer> champions,int internalGain,int proficiencyGain){}
    public static List<Performance> performance(JdbcTemplate jdbc,String career,int year,String player){
        var result=new ArrayList<Performance>();jdbc.query("SELECT performance_json,performance_hash FROM career_appearance_performance WHERE career_id=? AND season_year=?",(org.springframework.jdbc.core.RowCallbackHandler)r->{if(!hash(r.getString(1)).equals(r.getString(2)))throw new IllegalStateException("APPEARANCE_PERFORMANCE_INTEGRITY");for(var p:read(r.getString(1),Performance[].class))if(p.seasonYear()==year&&p.playerId().equals(player))result.add(p);},career,year);
        return result.stream().sorted(Comparator.comparing(Performance::date).thenComparing(Performance::seriesId)).toList();
    }
    private record Captured(Appearance snapshot,String receipt) {}
}
