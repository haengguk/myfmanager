package com.lolfm.career;

import static com.lolfm.career.CareerRosterStore.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/** Short repeatable-read snapshot; all pages carry the same fact revision, without a worker write lock. */
@Service
public final class CareerRecordsQuery {
    private final JdbcTemplate db;private final TransactionTemplate read;
    public CareerRecordsQuery(JdbcTemplate db,PlatformTransactionManager manager){this.db=db;read=new TransactionTemplate(manager);read.setReadOnly(true);read.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);}
    public record Entity(String id,String name,String team,String status) {}
    public record Directory(String careerId,List<Integer> seasons,List<Entity> players,List<Entity> teams,List<CareerAwardPolicy.Definition> definitions,long revision) {}
    public Directory directory(String career) {return read.execute(t->{
        require(career);var source=baseDirectory(db,career);var roster=saved(db,career,activeYear(db,career));var players=new TreeMap<String,Entity>();var lifecycle=CareerLifecycleStore.load(db,career);
        for(var p:source.players().values()){var member=roster==null?null:roster.state().members().get(p.playerId());players.put(p.playerId(),new Entity(p.playerId(),p.nickname(),member==null?null:member.ownerTeam(),lifecycle!=null&&lifecycle.players().containsKey(p.playerId())&&lifecycle.players().get(p.playerId()).status()==CareerLifecycleState.Status.RETIRED?"RETIRED":member==null||member.ownerTeam()==null?"FA":"REGISTERED"));}
        db.query("SELECT DISTINCT player_id,player_name,team_id FROM career_record_player WHERE career_id=? ORDER BY player_id",(org.springframework.jdbc.core.RowCallbackHandler)r->players.putIfAbsent(r.getString(1),new Entity(r.getString(1),r.getString(2),r.getString(3),"HISTORICAL")),career);
        var teams=new TreeMap<String,Entity>();for(var organization:source.organizations().values())if(organization.competitiveTeam()!=null)teams.put(organization.competitiveTeam(),new Entity(organization.competitiveTeam(),organization.competitiveTeam(),null,"ACTIVE"));
        if(roster!=null)for(var m:roster.state().members().values())if(m.ownerTeam()!=null&&m.ownerTeam().startsWith("LCK:"))teams.put(m.ownerTeam()+":CL",new Entity(m.ownerTeam()+":CL",m.ownerTeam()+" CL",m.ownerTeam(),"DEVELOPMENT"));
        db.query("SELECT DISTINCT team_id FROM career_record_player WHERE career_id=?",(org.springframework.jdbc.core.RowCallbackHandler)r->teams.putIfAbsent(r.getString(1),new Entity(r.getString(1),r.getString(1),null,"HISTORICAL")),career);
        return new Directory(career,db.query("SELECT season_year FROM career_season WHERE career_id=? ORDER BY season_year DESC",(r,n)->r.getInt(1),career),List.copyOf(players.values()),List.copyOf(teams.values()),CareerAwardPolicy.DEFINITIONS,CareerRecordsStore.revision(db,career));
    });}
    public record View(String careerId,String kind,String entity,Integer seasonYear,long asOf,long nextCursor,String factsCoverage,String statisticsCoverage,
                       List<Map<String,Object>> totals,List<Map<String,Object>> champions,List<CareerRecordsStore.Series> matches,List<CareerAwardsStore.Award> awards,
                       List<Map<String,Object>> observations,String collectionNote,boolean organization,long nextAwardCursor,Map<String,Long> awardCounts,String competition,Map<String,Object> organizationTotals,List<Map<String,Object>> historicalRoster,String awardsCoverage,String historyCoverage,long observationAsOf,long nextObservationCursor,List<Map<String,Object>> growthObservations,int growthSeason) {}
    public View view(String career,String kind,String entity,Integer year,Long asOf,long cursor,String competition) {return view(career,kind,entity,year,asOf,cursor,competition,false,0);}
    public View view(String career,String kind,String entity,Integer year,Long asOf,long cursor,String competition,boolean organization,long awardCursor) {return view(career,kind,entity,year,asOf,cursor,competition,organization,awardCursor,null,0);}
    public View view(String career,String kind,String entity,Integer year,Long asOf,long cursor,String competition,boolean organization,long awardCursor,Long observationAsOf,long observationCursor) {return read.execute(t->{
        require(career);if(!Set.of("PLAYER","TEAM","ALL").contains(kind)||cursor<0||awardCursor<0||observationCursor<0)throw CareerException.invalid("kind","기록 조회 조건이 올바르지 않습니다.");
        long revision=asOf==null?CareerRecordsStore.revision(db,career):Math.min(asOf,CareerRecordsStore.revision(db,career));if(revision<0)throw CareerException.invalid("asOf","조회 기준이 올바르지 않습니다.");
        String filter="s.career_id=? AND s.record_revision<=?";var args=new ArrayList<Object>(List.of(career,revision));
        if(year!=null){filter+=" AND s.season_year=?";args.add(year);}if(competition!=null&&!competition.isBlank()){filter+=" AND s.competition_id=?";args.add(competition);}
        var teamIds=teams(entity,organization);String teamMarks=String.join(",",Collections.nCopies(teamIds.size(),"?"));
        String playerFilter=filter;if(kind.equals("PLAYER")){playerFilter+=" AND p.player_id=?";}else if(kind.equals("TEAM")){playerFilter+=" AND p.team_id IN ("+teamMarks+")";}
        var playerArgs=new ArrayList<>(args);if(kind.equals("PLAYER"))playerArgs.add(entity);else if(kind.equals("TEAM"))playerArgs.addAll(teamIds);
        String observed=kind.equals("PLAYER")?"p.cs IS NOT NULL":"(SELECT COUNT(p2.cs) FROM career_record_player p2 WHERE p2.record_id=p.record_id AND p2.game_number=p.game_number AND p2.team_id=p.team_id)=5";
        String csFields="SUM(CASE WHEN "+observed+" THEN p.cs END) AS observedCs,SUM(CASE WHEN "+observed+" THEN p.seconds END)"+(kind.equals("PLAYER")?"":"/5")+" AS csObservedSeconds,SUM(CASE WHEN "+observed+" THEN 1 ELSE 0 END)"+(kind.equals("PLAYER")?"":"/5")+" AS csObservedUnits,";
        var totals=db.queryForList("SELECT p.season_year AS seasonYear,s.competition_id AS competition,p.team_id AS team,COUNT(DISTINCT p.record_id) AS series,"+(kind.equals("PLAYER")?"COUNT(*)":"COUNT(DISTINCT p.record_id || '/' || p.game_number)")+" AS sets,"+(kind.equals("PLAYER")?"SUM(CASE WHEN p.won THEN 1 ELSE 0 END)":"COUNT(DISTINCT CASE WHEN p.won THEN p.record_id || '/' || p.game_number END)")+" AS setWins,COUNT(DISTINCT CASE WHEN s.winner_team=p.team_id THEN p.record_id END) AS seriesWins,SUM(p.kills) AS kills,SUM(p.deaths) AS deaths,SUM(p.assists) AS assists,SUM(p.cs) AS cs,"+(kind.equals("PLAYER")?"SUM(p.seconds)":"SUM(CASE WHEN p.position='TOP' THEN p.seconds ELSE 0 END)")+" AS seconds,"+csFields+"COUNT(*) AS playerUnits,COUNT(p.kills) AS kdaObservedUnits,COUNT(p.rating) AS ratingObservedUnits,AVG(p.rating) AS rating FROM career_record_player p JOIN career_record_series s ON s.record_id=p.record_id WHERE "+playerFilter+" GROUP BY p.season_year,s.competition_id,p.team_id ORDER BY p.season_year DESC,s.competition_id,p.team_id",playerArgs.toArray());
        var organizationTotals=organization&&kind.equals("TEAM")?normalize(List.of(db.queryForMap("SELECT COUNT(DISTINCT p.record_id) AS series,COUNT(DISTINCT p.record_id || '/' || p.game_number) AS sets,COUNT(DISTINCT CASE WHEN s.winner_team=p.team_id THEN p.record_id END) AS seriesWins FROM career_record_player p JOIN career_record_series s ON s.record_id=p.record_id WHERE "+playerFilter,playerArgs.toArray()))).getFirst():Map.<String,Object>of();
        var historicalRoster=kind.equals("TEAM")?normalize(db.queryForList("SELECT p.player_id AS playerId,p.player_name AS name,p.team_id AS team,COUNT(DISTINCT p.record_id) AS series,COUNT(*) AS sets FROM career_record_player p JOIN career_record_series s ON s.record_id=p.record_id WHERE "+playerFilter+" GROUP BY p.player_id,p.player_name,p.team_id ORDER BY sets DESC,p.player_id LIMIT 200",playerArgs.toArray())):List.<Map<String,Object>>of();
        var champions=db.queryForList("SELECT p.champion_id AS champion,p.position AS position,COUNT(*) AS sets,SUM(CASE WHEN p.won THEN 1 ELSE 0 END) AS wins,MAX(s.played_date) AS lastUsed FROM career_record_player p JOIN career_record_series s ON s.record_id=p.record_id WHERE "+playerFilter+" GROUP BY p.champion_id,p.position ORDER BY sets DESC,p.champion_id,p.position LIMIT 100",playerArgs.toArray());
        String matchFilter=filter;var matchArgs=new ArrayList<>(args);
        if(kind.equals("PLAYER")){matchFilter+=" AND EXISTS (SELECT 1 FROM career_record_player p WHERE p.record_id=s.record_id AND p.player_id=?)";matchArgs.add(entity);}
        if(kind.equals("TEAM")){matchFilter+=" AND (s.first_team IN ("+teamMarks+") OR s.second_team IN ("+teamMarks+"))";matchArgs.addAll(teamIds);matchArgs.addAll(teamIds);}
        matchArgs.add(cursor);
        var rows=db.query("SELECT s.record_json,s.record_hash,s.record_revision FROM career_record_series s WHERE "+matchFilter+" AND s.record_revision>? ORDER BY s.record_revision LIMIT 26",(r,n)->Map.entry(r.getLong(3),CareerRecordsStore.readSeries(r.getString(1),r.getString(2))),matchArgs.toArray());
        boolean more=rows.size()>25;var matches=rows.stream().limit(25).map(Map.Entry::getValue).toList();
        var awardPage=awardPage(career,year,revision,kind,entity,competition,organization,awardCursor);var awards=awardPage.awards();
        var history=observations(career,year,kind,entity,revision,organization,observationAsOf,observationCursor);var observations=new ArrayList<Map<String,Object>>(history.growth());observations.addAll(history.rows());
        int missing=db.queryForObject("SELECT COUNT(*) FROM career_record_series s WHERE "+filter+" AND s.coverage<>'COMPLETE'",Integer.class,args.toArray());
        return new View(career,kind,entity,year,revision,more?rows.get(24).getKey():-1,coverage(career,year,false,revision),missing>0?(db.queryForObject("SELECT COUNT(*) FROM career_record_series s WHERE "+filter+" AND s.coverage='COMPLETE'",Integer.class,args.toArray())==0?"NOT_COLLECTED":"PARTIAL"):coverage(career,year,true,revision),statistics(totals,kind.equals("PLAYER")),normalize(champions),matches,awards,observations,"수집된 승인 경기 기준입니다. 과거 미수집 기간은 0경기·무관·0성장으로 간주하지 않습니다. 현실 수상 이력은 원본 선수 프로필에서 별도로 확인합니다. 운영 이력은 대상별 50건씩 이어 읽습니다. 성장 관측은 선택 시즌(통산에서는 최신 시즌)에서 별도로 조회합니다.",organization,awardPage.nextCursor(),awardPage.counts(),competition==null?"":competition,organizationTotals,historicalRoster,awards.stream().anyMatch(a->a.status().equals("INPUT_INCOMPLETE"))?"PARTIAL":awards.isEmpty()?"NOT_COLLECTED":"OBSERVED",observations.isEmpty()?(coverage(career,year,false,revision).equals("NOT_COLLECTED")?"NOT_COLLECTED":"NO_RELATED_RECORDS"):"OBSERVED",history.asOf(),history.nextCursor(),history.growth(),history.growthYear());
    });}
    private String coverage(String career,Integer year,boolean statistics,long revision) {
        var seasons=db.query("SELECT season_year,lifecycle_status FROM career_season WHERE career_id=?"+(year==null?"":" AND season_year="+year),(r,n)->Map.entry(r.getInt(1),r.getString(2)),career);
        boolean started=!seasons.isEmpty(),closed=true;for(var season:seasons) {
            var marker=db.query("SELECT observation_json FROM career_record_observation WHERE career_id=? AND season_year=? AND observation_key='COLLECTION'",(r,n)->read(r.getString(1),com.fasterxml.jackson.databind.JsonNode.class),career,season.getKey());
            if(marker.isEmpty()||!marker.getFirst().path("completeFromStart").asBoolean())started=false;
            closed&=season.getValue().equals("CLOSED");
        }
        if(started)return closed?"COMPLETE":"IN_PROGRESS";
        int observed=db.queryForObject("SELECT COUNT(*) FROM career_record_series WHERE career_id=? AND record_revision<=?"+(year==null?"":" AND season_year="+year)+(statistics?" AND coverage='COMPLETE'":""),Integer.class,career,revision);
        return observed==0?"NOT_COLLECTED":"PARTIAL";
    }
    public com.fasterxml.jackson.databind.JsonNode reference(String career,String player) {return read.execute(t->{require(career);var definition=baseDirectory(db,career).players().get(player);if(definition==null)throw CareerException.notFound();return read(definition.detailsJson(),com.fasterxml.jackson.databind.JsonNode.class);});}
    public CareerRecordsStore.Series match(String career,String record){return read.execute(t->{require(career);var rows=db.query("SELECT record_json,record_hash FROM career_record_series WHERE career_id=? AND record_id=?",(r,n)->CareerRecordsStore.readSeries(r.getString(1),r.getString(2)),career,record);if(rows.isEmpty())throw CareerException.notFound();return rows.getFirst();});}
    static List<String> teams(String team,boolean organization) {
        if(!organization)return List.of(team);
        if(team.startsWith("LCK:")){String first=team.replace(":CL","");return List.of(first,first+":CL");}
        if(Set.of("LEC:KC","LEC:KCB").contains(team))return List.of("LEC:KC","LEC:KCB");
        return List.of(team);
    }
    public record AwardPage(List<CareerAwardsStore.Award> awards,long nextCursor,Map<String,Long> counts) {}
    public List<CareerAwardsStore.Award> awards(String career,Integer year,long revision){return awardPage(career,year,revision,"ALL","",null,false,0).awards();}
    public List<CareerAwardsStore.Award> matchAwards(String career,String record){return read.execute(t->{require(career);return db.query("SELECT a.award_json,a.award_hash FROM career_record_award a JOIN career_record_award_input i ON a.instance_id=i.instance_id WHERE a.career_id=? AND i.record_id=? ORDER BY a.definition_id,a.instance_id",(r,n)->award(r.getString(1),r.getString(2)),career,record);});}
    public CareerAwardsStore.Award awardDetail(String career,String instance){return read.execute(t->{require(career);var rows=db.query("SELECT award_json,award_hash FROM career_record_award WHERE career_id=? AND instance_id=?",(r,n)->award(r.getString(1),r.getString(2)),career,instance);if(rows.isEmpty())throw CareerException.notFound();return rows.getFirst();});}
    private static CareerAwardsStore.Award award(String json,String digest){if(!hash(json).equals(digest))throw new IllegalStateException("AWARD_INTEGRITY");return read(json,CareerAwardsStore.Award.class);}
    private AwardPage awardPage(String career,Integer year,long revision,String kind,String entity,String competition,boolean organization,long cursor) {
        String filter="a.career_id=? AND a.cutoff_revision<=?";var args=new ArrayList<Object>(List.of(career,revision));
        if(year!=null){filter+=" AND a.season_year=?";args.add(year);}
        if(competition!=null&&!competition.isBlank()){filter+=" AND (a.scope_id=? OR a.scope_id=?)";args.add(competition);args.add(CareerAwardPolicy.scope(competition));}
        String candidate="";var teamIds=teams(entity,organization);
        if(kind.equals("PLAYER")){candidate="c.player_id=?";args.add(entity);}
        else if(kind.equals("TEAM")){candidate="c.team_id IN ("+String.join(",",Collections.nCopies(teamIds.size(),"?"))+")";args.addAll(teamIds);}
        if(!candidate.isEmpty())filter+=" AND EXISTS (SELECT 1 FROM career_record_award_candidate c WHERE c.instance_id=a.instance_id AND "+candidate+")";
        var rows=db.query("SELECT a.award_json,a.award_hash FROM career_record_award a WHERE "+filter+" ORDER BY a.cutoff_revision DESC,a.instance_id LIMIT 51 OFFSET "+cursor,(r,n)->award(r.getString(1),r.getString(2)),args.toArray());
        var counts=new TreeMap<String,Long>();String wonFilter=filter;
        if(!candidate.isEmpty())wonFilter=filter.substring(0,filter.length()-1)+" AND c.winner=TRUE)";
        db.query("SELECT a.definition_id,COUNT(*) FROM career_record_award a WHERE "+wonFilter+" AND a.status='FINALIZED' GROUP BY a.definition_id",(org.springframework.jdbc.core.RowCallbackHandler)r->counts.put(r.getString(1),r.getLong(2)),args.toArray());
        return new AwardPage(rows.stream().limit(50).toList(),rows.size()>50?cursor+50:-1,counts);
    }
    record ObservationPage(List<Map<String,Object>> rows,long asOf,long nextCursor,List<Map<String,Object>> growth,int growthYear) {}
    private ObservationPage observations(String career,Integer year,String kind,String entity,long revision,boolean organization,Long asOf,long cursor){
        long maximum=db.queryForObject("SELECT COALESCE(MAX(sequence),0) FROM career_observation_index WHERE career_id=?",Long.class,career);
        long upper=asOf==null?maximum:Math.min(asOf,maximum);if(upper<0)throw CareerException.invalid("observationAsOf","조회 기준이 올바르지 않습니다.");
        String filter="i.career_id=? AND i.sequence<=? AND i.fact_revision<=?";var args=new ArrayList<Object>(List.of(career,upper,revision));
        if(!kind.equals("ALL")){var ids=kind.equals("TEAM")?teams(entity,organization):List.of(entity);filter+=" AND EXISTS (SELECT 1 FROM career_observation_subject s WHERE s.sequence=i.sequence AND s.kind=? AND s.entity IN ("+String.join(",",Collections.nCopies(ids.size(),"?"))+"))";args.add(kind);args.addAll(ids);}
        String join=" FROM career_observation_index i JOIN career_record_observation o ON o.career_id=i.career_id AND o.season_year=i.season_year AND o.observation_key=i.observation_key WHERE ";
        int growthYear=year==null?db.queryForObject("SELECT COALESCE(MAX(season_year),0) FROM career_season WHERE career_id=?",Integer.class,career):year;
        var growthArgs=new ArrayList<>(args);growthArgs.add(growthYear);
        var growth=kind.equals("PLAYER")?db.query("SELECT o.season_year,o.observation_key,o.observation_json,o.observation_hash,i.sequence"+join+filter+" AND i.category='GROWTH' AND i.season_year=? ORDER BY o.observed_date,i.sequence LIMIT 14",(r,n)->observation(r,entity),growthArgs.toArray()):List.<Map<String,Object>>of();
        if(year!=null){filter+=" AND i.season_year=?";args.add(year);}args.add(cursor);
        var rows=db.query("SELECT o.season_year,o.observation_key,o.observation_json,o.observation_hash,i.sequence"+join+filter+" AND i.category='HISTORY' AND i.sequence>? ORDER BY i.sequence LIMIT 51",(r,n)->observation(r,null),args.toArray());
        return new ObservationPage(rows.stream().limit(50).toList(),upper,rows.size()>50?((Number)rows.get(49).get("sequence")).longValue():-1,growth,growthYear);
    }
    private Map<String,Object> observation(java.sql.ResultSet r,String player)throws java.sql.SQLException {
        if(!hash(r.getString(3)).equals(r.getString(4)))throw new IllegalStateException("OBSERVATION_INTEGRITY");
        var tree=read(r.getString(3),com.fasterxml.jackson.databind.JsonNode.class);
        return Map.of("seasonYear",r.getInt(1),"kind",r.getString(2),"value",player==null?tree:tree.path("players").path(player),"sequence",r.getLong(5));
    }
    static List<Map<String,Object>> statistics(List<Map<String,Object>> rows,boolean player) {
        var normalized=normalize(rows);for(var row:normalized){var seconds=(Number)row.get("csobservedseconds");var cs=(Number)row.get("observedcs");
            row.put("observedcspm",cs==null||seconds==null||seconds.longValue()==0?null:cs.doubleValue()*60/seconds.doubleValue());
            row.put("csobservationunit",player?"PLAYER_SET":"COMPLETE_TEAM_SET");
        }return normalized;
    }
    static List<Map<String,Object>> normalize(List<Map<String,Object>> rows){return rows.stream().map(row->{var value=new TreeMap<String,Object>();row.forEach((k,v)->value.put(k.toLowerCase(Locale.ROOT),v));return (Map<String,Object>)value;}).toList();}
    private void require(String career){if(db.queryForObject("SELECT COUNT(*) FROM career_save WHERE career_id=?",Integer.class,career)==0)throw CareerException.notFound();}
}
