package com.lolfm.career;

import static com.lolfm.career.CareerRosterStore.*;
import java.time.LocalDate;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;

/** Caller owns the existing Career/Series transaction and completion fence. GET never invokes this writer. */
public final class CareerRecordsStore {
    public static final String VERSION="CAREER_RECORDS_V1";
    public record Person(String playerId,String name,String matchTeam,String registeredTeam,String employer) {}
    public record Binding(String seriesId,LocalDate date,Map<String,Person> people) {}
    public record PlayerGame(int gameNumber,String playerId,String name,String team,String registeredTeam,String employer,
                             CareerGameStatistics.Player statistics,CareerPerformancePolicy.Rating evaluation,boolean won) {}
    public record Game(int gameNumber,String outputHash,int seconds,String winner,String endReason,String coverage,List<PlayerGame> players) {}
    public record Series(String recordId,String careerId,int seasonYear,String origin,String competition,String stage,String fixtureId,String seriesId,
                         LocalDate date,String receiptHash,String winner,String firstTeam,String secondTeam,String coverage,List<Game> games) {}
    public static void beginSeason(JdbcTemplate db,String career,int year,LocalDate date,boolean completeFromStart) {
        CareerHistoryStore.observe(db,career,year,"COLLECTION",date,Map.of("introducedOn",date,"completeFromStart",completeFromStart,"recordsVersion",VERSION,"awardVersion",CareerAwardPolicy.VERSION),false);
    }
    public static void capture(JdbcTemplate db,String career,int year,String identity,String series,String competition,CompetitionRosterSnapshot frozen) {
        beginSeason(db,career,year,CareerMarketStore.date(db,career),false);
        if(frozen==null)return;
        var people=new TreeMap<String,Person>();var market=CareerMarketStore.load(db,career);
        var loans=market==null||market.state().management()==null?Map.<String,CareerManagementState.Loan>of():market.state().management().loans();
        for(var t:frozen.teams().values())for(var p:t.players()) {
            String team=CompetitionRosterSnapshot.token(t.team()),employer=CareerOverseasRoster.owner(team);
            for(var loan:loans.values())if(loan.playerId().equals(p.playerId())&&loan.borrowingTeam().equals(employer)&&"ACTIVE".equals(loan.status()))employer=loan.parentTeam();
            people.put(p.playerId(),new Person(p.playerId(),p.nickname(),team,team,employer));
        }
        String key="BIND:"+hash(identity),json=write(new Binding(series,CareerMarketStore.date(db,career),people));
        var prior=db.query("SELECT observation_json FROM career_record_observation WHERE career_id=? AND season_year=? AND observation_key=?",(r,n)->r.getString(1),career,year,key);
        if(prior.isEmpty())db.update("INSERT INTO career_record_observation VALUES (?,?,?,?,?,?)",career,year,key,CareerMarketStore.date(db,career),json,hash(json));
    }
    public static void stage(JdbcTemplate db,String series,List<CareerGameStatistics> games) {
        for(var g:games) {
            String json=write(g),h=hash(json);
            var prior=db.query("SELECT statistics_hash FROM career_game_statistics WHERE series_id=? AND game_number=?",(r,n)->r.getString(1),series,g.gameNumber());
            if(!prior.isEmpty()){if(!prior.getFirst().equals(h))throw new IllegalStateException("CAREER_STATISTICS_CONFLICT");continue;}
            db.update("INSERT INTO career_game_statistics VALUES (?,?,?,?,?)",series,g.gameNumber(),g.outputHash(),json,h);
        }
    }
    public static void complete(JdbcTemplate db,String career,int year,String origin,String competition,String stage,String fixture,String series,
                                LocalDate date,String receipt,String winner,String first,String second,List<com.lolfm.league.LeagueFixtureGameReceiptV1> receipts) {
        complete(db,career,year,origin,competition,stage,fixture,series,date,receipt,winner,first,second,receipts,true);
    }
    static void complete(JdbcTemplate db,String career,int year,String origin,String competition,String stage,String fixture,String series,
                                LocalDate date,String receipt,String winner,String first,String second,List<com.lolfm.league.LeagueFixtureGameReceiptV1> receipts,boolean award) {
        String id=hash(write(List.of(VERSION,career,year,origin)));
        var captured=db.query("SELECT observation_json,observation_hash FROM career_record_observation WHERE career_id=? AND season_year=? AND observation_key=?",(r,n)->{
            if(!hash(r.getString(1)).equals(r.getString(2)))throw new IllegalStateException("RECORD_BINDING_INTEGRITY");return read(r.getString(1),Binding.class);
        },career,year,"BIND:"+hash(origin));
        Binding binding=captured.isEmpty()?null:captured.getFirst();
        var stats=new TreeMap<Integer,CareerGameStatistics>();db.query("SELECT statistics_json,statistics_hash FROM career_game_statistics WHERE series_id=?",(org.springframework.jdbc.core.RowCallbackHandler)r->{
            if(!hash(r.getString(1)).equals(r.getString(2)))throw new IllegalStateException("STATISTICS_INTEGRITY");var g=read(r.getString(1),CareerGameStatistics.class);stats.put(g.gameNumber(),g);
        },series);
        var games=new ArrayList<Game>();
        for(var r:receipts) {
            var g=stats.get(r.gameNumber());var players=new ArrayList<PlayerGame>();
            if(g!=null) {
                if(!g.outputHash().equals(r.outputHash())||g.seconds()!=r.durationSeconds()||!Objects.equals(g.winner(),r.winnerTeamCode())||!Objects.equals(g.endReason(),r.endReason().name())||g.players().size()!=10||g.players().stream().map(CareerGameStatistics.Player::playerId).distinct().count()!=10)throw new IllegalStateException("STATISTICS_RECEIPT_MISMATCH");
                for(var p:g.players()) {
                    if(r.orderedFinalAssignments().stream().noneMatch(a->a.playerId().value().equals(p.playerId())&&a.position()==p.position()&&a.teamSide()==p.side()&&a.championId().value().equals(p.championId())))throw new IllegalStateException("STATISTICS_ASSIGNMENT_MISMATCH");
                    var person=binding==null?null:binding.people().get(p.playerId());
                    String team=team(p.team(),competition);
                    players.add(new PlayerGame(g.gameNumber(),p.playerId(),person==null?p.playerId():person.name(),team,person==null?null:person.registeredTeam(),person==null?null:person.employer(),p,CareerPerformancePolicy.rate(g,p),p.team().equals(g.winner())));
                }
            }
            if(g==null)for(var a:r.orderedFinalAssignments()) {
                var person=binding==null?null:binding.people().get(a.playerId().value());String token=a.teamSide()==com.lolfm.simulator.TeamSide.BLUE?r.blueTeamCode():r.redTeamCode();
                players.add(new PlayerGame(r.gameNumber(),a.playerId().value(),person==null?a.playerId().value():person.name(),team(token,competition),person==null?null:person.registeredTeam(),person==null?null:person.employer(),null,CareerPerformancePolicy.Rating.missing(),token.equals(r.winnerTeamCode())));
            }
            games.add(new Game(r.gameNumber(),r.outputHash(),r.durationSeconds(),team(r.winnerTeamCode(),competition),r.endReason().name(),g==null?"NOT_COLLECTED":"COMPLETE",players));
        }
        String coverage=games.stream().allMatch(g->g.coverage().equals("COMPLETE"))?"COMPLETE":games.stream().anyMatch(g->g.coverage().equals("COMPLETE"))?"PARTIAL":"NOT_COLLECTED";
        var record=new Series(id,career,year,origin,competition,stage,fixture,series,binding==null?date:binding.date(),receipt,team(winner,competition),team(first,competition),team(second,competition),coverage,games);
        String json=write(record),digest=hash(json);
        var prior=db.query("SELECT record_hash FROM career_record_series WHERE record_id=?",(r,n)->r.getString(1),id);
        if(!prior.isEmpty()){if(!prior.getFirst().equals(digest))throw new IllegalStateException("CAREER_RECORD_CONFLICT");CareerDraftEvidence.save(db,id,competition,receipts);return;}
        db.update("INSERT INTO career_record_series(record_id,career_id,season_year,origin_identity,competition_id,stage_id,fixture_id,series_id,played_date,receipt_hash,winner_team,first_team,second_team,game_count,coverage,record_json,record_hash) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",id,career,year,origin,competition,stage,fixture,series,record.date(),receipt,record.winner(),record.firstTeam(),record.secondTeam(),games.size(),coverage,json,digest);
        for(var g:games)for(var p:g.players()) {
            var stat=p.statistics();var assignment=receipts.get(g.gameNumber()-1).orderedFinalAssignments().stream().filter(v->v.playerId().value().equals(p.playerId())).findFirst().orElseThrow();
            db.update("INSERT INTO career_record_player VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",id,g.gameNumber(),p.playerId(),career,year,p.team(),p.name(),write(p),assignment.championId().value(),assignment.position().name(),stat==null?null:stat.kills(),stat==null?null:stat.deaths(),stat==null?null:stat.assists(),stat==null?null:stat.cs(),stat==null?null:stat.gold(),stat==null?null:stat.experience(),g.seconds(),p.won(),p.evaluation().rating());
        }
        CareerDraftEvidence.save(db,id,competition,receipts);
        if(award)CareerAwardsStore.match(db,record);
        CareerInboxStore.series(db,record);
    }
    public static void leagueComplete(JdbcTemplate db,String season,String fixture,String hash,com.lolfm.league.LeagueFixtureCompletionReceiptV2 receipt) {
        db.query("SELECT career_id,season_year FROM career_season WHERE season_id=?",(org.springframework.jdbc.core.RowCallbackHandler)r->{
            String career=r.getString(1);int year=r.getInt(2);
            int round=db.queryForObject("SELECT round_number FROM league_fixture WHERE season_id=? AND fixture_id=?",Integer.class,season,fixture);
            LocalDate date=roundDates(year).get(round);
            complete(db,career,year,"LEAGUE|"+season+'|'+fixture,"LCK_REGULAR_R1_R2","R1_R2",fixture,receipt.boundSeriesId(),date,hash,receipt.fixtureReceipt().winnerTeamCode(),receipt.firstTeamCode(),receipt.secondTeamCode(),receipt.orderedGameReceipts());
        },season);
    }
    public static void competitionComplete(JdbcTemplate db,CareerCompetitionFixtureCompletionReceiptV1 r) {
        db.query("SELECT stage_id,scheduled_date,fixture_id FROM career_competition_fixture WHERE career_id=? AND calendar_season_year=? AND competition_id=? AND match_id=?",(org.springframework.jdbc.core.RowCallbackHandler)f->
            complete(db,r.careerId(),r.seasonYear(),"COMP|"+r.seasonYear()+'|'+r.competitionId()+'|'+r.matchId(),r.competitionId(),f.getString(1),f.getString(3),r.seriesId(),f.getObject(2,LocalDate.class),r.receiptHash(),r.winnerTeamCode(),r.firstTeamCode(),r.secondTeamCode(),r.orderedGames()),r.careerId(),r.seasonYear(),r.competitionId(),r.matchId());
    }
    static String team(String token,String competition){if(token==null)return null;String t=token.contains(":")?token:"LCK:"+token;return "LCK_CL".equals(competition)?t+":CL":t;}
    static Series readSeries(String json,String digest){if(!hash(json).equals(digest))throw new IllegalStateException("CAREER_RECORD_INTEGRITY");return read(json,Series.class);}
    public static List<Series> period(JdbcTemplate db,String career,int year,String scope,long revision) {
        return db.query("SELECT record_json,record_hash FROM career_record_series WHERE career_id=? AND season_year=? AND record_revision<=? AND (competition_id=? OR (?='LCK_REGULAR' AND competition_id IN ('LCK_REGULAR_R1_R2','LCK_REGULAR_R3_R4'))) ORDER BY played_date,record_revision",(r,n)->readSeries(r.getString(1),r.getString(2)),career,year,revision,scope,scope).stream().filter(s->CareerAwardPolicy.inScope(scope,s.competition(),s.stage())).toList();
    }
    static long revision(JdbcTemplate db,String career){return db.queryForObject("SELECT COALESCE(MAX(record_revision),0) FROM career_record_series WHERE career_id=?",Long.class,career);}
    // Read-only rule template, not Career/match state. Avoid parsing and introspecting the same resource per fixture.
    private static final class CalendarRules {
        private static final CareerCalendarTemplate TEMPLATE=new CareerCalendarTemplate(new com.fasterxml.jackson.databind.ObjectMapper().findAndRegisterModules());
    }
    static Map<Integer,LocalDate> roundDates(int year){return CalendarRules.TEMPLATE.leagueRoundDates(year);}
    private CareerRecordsStore() {}
}
