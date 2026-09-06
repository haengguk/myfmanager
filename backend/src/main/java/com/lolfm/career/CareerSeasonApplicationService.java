package com.lolfm.career;

import java.util.ArrayList;
import java.util.List;
import java.time.OffsetDateTime;
import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** One transactional season closure, receipt and activation; reads never advance the Career. */
@Service
public final class CareerSeasonApplicationService {
    public static final String REQUEST_SCHEMA = "CAREER_SEASON_TRANSITION_REQUEST_V1";
    public static final List<String> REQUIRED = List.of("LCK_CUP","LCK_REGULAR_R1_R2","LCK_ROAD_TO_MSI",
            "LCK_REGULAR_R3_R4","LCK_PLAY_IN","LCK_PLAYOFFS","FIRST_STAND","MSI","EWC_LOL","WORLDS");
    private final CareerRelationalStore careers;
    private final CareerCalendarRelationalStore calendars;
    private final CareerCompetitionRelationalStore competitions;
    private final CareerApplicationService.SeasonProvisioningPort provisioning;
    private final CareerInternationalParticipants participants;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;

    public CareerSeasonApplicationService(CareerRelationalStore careers, CareerCalendarRelationalStore calendars,
            CareerCompetitionRelationalStore competitions, CareerApplicationService.SeasonProvisioningPort provisioning,
            CareerInternationalParticipants participants, JdbcTemplate jdbc, PlatformTransactionManager manager) {
        this.careers=careers;this.calendars=calendars;this.competitions=competitions;this.provisioning=provisioning;
        this.participants=participants;this.jdbc=jdbc;this.transactions=new TransactionTemplate(manager);
    }
    public record Request(String schemaVersion, int sourceYear, long expectedCalendarRevision, String clientCommandId) {}
    public record Receipt(String clientCommandId, String careerId, int sourceYear, int destinationYear,
                          String destinationSeasonId, long resultingRevision, String resultHash, OffsetDateTime completedAt) {}
    public record Transition(boolean replayed, Receipt receipt, SeasonList seasons) {}
    public record SeasonSummary(int year, int ordinal, String leagueId, String seasonId, String status, String rosterHash) {}
    public record SeasonList(String schemaVersion, String careerId, int activeYear, long calendarRevision,
                             List<SeasonSummary> seasons, List<String> blockers, List<String> allowedCommands) {}
    public record HistoricalFixture(String competitionId, String matchId, String firstTeam, String secondTeam,
                                    String winner, String seriesId, String status, boolean replayAvailable) {}
    public record SeasonDetail(String schemaVersion, String careerId, SeasonSummary season, boolean readOnly,
                               CareerCompetitionRelationalStore.FinalRankingView domestic,
                               List<CareerCompetitionRelationalStore.InternationalView> international,
                               List<HistoricalFixture> fixtures) {}

    public SeasonList list(String careerId) {
        var career = requireCareer(careerId); var state = calendars.loadReady(career);
        var summaries = jdbc.query("SELECT * FROM career_season WHERE career_id = ? ORDER BY season_year DESC",
                (r,n)->new SeasonSummary(r.getInt("season_year"),r.getInt("season_ordinal"),r.getString("league_id"),
                        r.getString("season_id"),r.getString("lifecycle_status"),r.getString("roster_hash")),careerId);
        List<String> reasons;
        try { reasons = blockers(career,state.seasonYear()); }
        catch (RuntimeException integrity) { reasons = List.of("SEASON_INTEGRITY:" + integrity.getMessage()); }
        return new SeasonList("CAREER_SEASONS_V1",careerId,state.seasonYear(),state.calendarRevision(),summaries,reasons,
                reasons.isEmpty()?List.of("START_NEXT_SEASON"):List.of());
    }
    public SeasonDetail detail(String careerId, int year) {
        var list = list(careerId);
        var season = list.seasons().stream().filter(s->s.year()==year).findFirst().orElseThrow(CareerException::notFound);
        var cycle = competitions.load(careerId,year);
        var domestic = competitions.finalRanking(careerId,year);
        var international = competitions.internationalViews(careerId,year);
        if (season.status().equals("CLOSED")) {
            String stored = jdbc.queryForObject("SELECT closed_result_hash FROM career_season WHERE career_id = ? AND season_year = ?",String.class,careerId,year);
            if (!resultHash(domestic,international).equals(stored)) throw new IllegalStateException("HISTORICAL_SEASON_RESULT_INTEGRITY");
        }
        var replayIds = jdbc.query("""
            SELECT c.series_id FROM career_competition_series_checkpoint c JOIN career_competition_series_binding b ON b.binding_hash=c.binding_hash
              WHERE b.career_id=? AND b.calendar_season_year=? AND c.series_status='COMPLETED'
            UNION SELECT c.series_id FROM league_player_series_checkpoint c JOIN league_player_binding b ON b.binding_hash=c.binding_hash
              WHERE b.season_id=? AND c.series_status='COMPLETED'
            """,(r,n)->r.getString(1),careerId,year,season.seasonId());
        var fixtures = new ArrayList<>(cycle.fixtures().stream().map(f -> new HistoricalFixture(f.competitionId(),f.matchId(),f.firstTeamCode(),
                f.secondTeamCode(),f.winnerTeamCode(),f.seriesId(),f.lifecycleStatus(),replayIds.contains(f.seriesId()))).toList());
        fixtures.addAll(jdbc.query("""
            SELECT f.*,r.receipt_json FROM league_fixture f LEFT JOIN league_completion_receipt r ON r.season_id=f.season_id AND r.fixture_id=f.fixture_id
            WHERE f.season_id=? ORDER BY f.round_number,f.fixture_id
            """,(r,n)-> {
                String receipt=r.getString("receipt_json");
                String winner=receipt==null?null:CareerDomesticEvidence.read(competitions.json,receipt,com.lolfm.league.LeagueFixtureCompletionReceiptV2.class).winnerTeamCode();
                return new HistoricalFixture("LCK_REGULAR_R1_R2",r.getString("fixture_id"),r.getString("first_team_code"),r.getString("second_team_code"),winner,
                        r.getString("bound_series_id"),r.getString("lifecycle_status"),replayIds.contains(r.getString("bound_series_id")));
            },season.seasonId()));
        return new SeasonDetail("CAREER_SEASON_DETAIL_V1",careerId,season,year!=list.activeYear(),domestic,international,List.copyOf(fixtures));
    }
    public Transition transition(String careerId, Request request) {
        if (request == null || !REQUEST_SCHEMA.equals(request.schemaVersion()) || request.sourceYear()<2026
                || request.expectedCalendarRevision()<0) throw CareerException.invalid(null,"시즌 전환 요청을 확인해 주세요.");
        String command;
        try { command = CareerIdentity.canonicalCommandId(request.clientCommandId()); }
        catch (IllegalArgumentException invalid) { throw CareerException.invalid("clientCommandId","UUID가 필요합니다."); }
        String payload = CareerInternationalRules.hash(careerId+"|"+request.schemaVersion()+"|"+request.sourceYear()+"|"+request.expectedCalendarRevision());
        return transactions.execute(ignored -> {
            // Same lock order as Calendar advance; competition starts also lock the Calendar row before the cycle.
            jdbc.queryForObject("SELECT lock_name FROM career_calendar_operation_lock WHERE lock_name = 'ADVANCE_COMMANDS' FOR UPDATE",String.class);
            var career = requireCareer(careerId);
            jdbc.queryForObject("SELECT career_id FROM career_calendar_state WHERE career_id = ? FOR UPDATE",String.class,careerId);
            var prior = jdbc.query("SELECT * FROM career_season_transition WHERE client_command_id = ?",(r,n)-> {
                if (!payload.equals(r.getString("payload_hash"))) throw CareerException.calendarCommandConflict();
                return new Receipt(command,r.getString("career_id"),r.getInt("source_year"),r.getInt("destination_year"),
                        r.getString("destination_season_id"),r.getLong("resulting_revision"),r.getString("result_hash"),r.getObject("completed_at",OffsetDateTime.class));
            },command);
            if (!prior.isEmpty()) return new Transition(true,prior.getFirst(),list(careerId));
            var calendar = calendars.loadReady(career);
            if (calendar.seasonYear()!=request.sourceYear() || calendar.calendarRevision()!=request.expectedCalendarRevision())
                throw CareerException.calendarStaleRevision();
            competitions.lockCycle(careerId,request.sourceYear());
            var blocked = blockers(career,request.sourceYear());
            if (!blocked.isEmpty()) throw CareerException.invalid("sourceYear","시즌 마감 불가: "+String.join(", ",blocked));
            var current = careers.activeSeason(career);
            var rosters = CareerSeasonRosters.load(competitions,careerId,current.year());
            if (rosters == null) rosters = CareerSeasonRosters.freezeInitial(competitions,participants,careerId,current.year());
            int nextYear=current.year()+1, ordinal=current.ordinal()+1;
            String identity=CareerInternationalRules.hash(careerId+"|SEASON_ROLLOVER_V1|"+nextYear+"|"+ordinal);
            String league="league_"+identity, season="season_"+identity;
            long seed=CareerCompetitionAggregate.deriveSeed(career.rootSeed(),nextYear,"CAREER_SEASON","ORDINAL:"+ordinal);
            var created=provisioning.provisionNext(current.seasonId(),league,season,career.managedTeamCode(),seed);
            if (!created.leagueId().equals(league) || !created.seasonId().equals(season) || created.rootSeed()!=seed
                    || !created.frozenSnapshotIdentity().equals(current.frozenSnapshotHash())
                    || !created.productDecisionIdentity().equals(current.productDecisionHash()) || !created.lifecycleStatus().equals("READY"))
                throw new IllegalStateException("CARRIED_SEASON_PROVISIONING_INTEGRITY");
            String result=resultHash(competitions.finalRanking(careerId,current.year()),competitions.internationalViews(careerId,current.year()));
            jdbc.update("UPDATE career_season SET lifecycle_status = 'CLOSED', closed_calendar_json = ?, closed_result_hash = ? WHERE career_id = ? AND season_year = ?",
                    write(calendar),result,careerId,current.year());
            jdbc.update("""
                INSERT INTO career_season(career_id,season_year,season_ordinal,league_id,season_id,season_root_seed,
                    frozen_snapshot_hash,product_decision_hash,lifecycle_status,roster_json,roster_hash)
                VALUES (?,?,?,?,?,?,?,?,'ACTIVE',?,?)
                """,careerId,nextYear,ordinal,league,season,seed,created.frozenSnapshotIdentity(),created.productDecisionIdentity(),rosters.encoded(),rosters.identity());
            competitions.initializeFuture(careerId,nextYear);
            var next=calendars.rollover(career,current.year(),request.expectedCalendarRevision());
            jdbc.update("""
                INSERT INTO career_season_transition VALUES (?,?,?,?,?,?,?,?,?,CURRENT_TIMESTAMP)
                """,command,careerId,current.year(),request.expectedCalendarRevision(),payload,nextYear,season,next.calendarRevision(),result);
            // The receipt is the source transition; active projection can be several seasons newer on replay.
            var receipt=jdbc.queryForObject("SELECT completed_at FROM career_season_transition WHERE client_command_id = ?",OffsetDateTime.class,command);
            return new Transition(false,new Receipt(command,careerId,current.year(),nextYear,season,next.calendarRevision(),result,receipt),list(careerId));
        });
    }
    private List<String> blockers(CareerRelationalStore.CareerRow career,int year) {
        String id=career.careerId();var active=careers.activeSeason(career);
        var reasons=new ArrayList<String>();
        var cycle=competitions.load(id,year);
        for(String competition:REQUIRED) {
            var instance=cycle.competitions().stream().filter(c->c.competitionId().equals(competition)).findFirst();
            if(instance.isEmpty() || !instance.get().lifecycleStatus().equals("COMPLETED")) reasons.add("INCOMPLETE:"+competition);
        }
        if(competitions.finalRanking(id,year)==null)reasons.add("SEALED_DOMESTIC_FINAL_REQUIRED");
        var international=competitions.internationalViews(id,year);
        if(international.size()!=4 || international.stream().anyMatch(v->!v.bracket().complete()))reasons.add("INTERNATIONAL_FINAL_RESULTS_REQUIRED");
        if (count("SELECT COUNT(*) FROM league_fixture WHERE season_id = ? AND lifecycle_status <> 'COMPLETED'",active.seasonId())>0)reasons.add("LEAGUE_FIXTURES_PENDING");
        if (count("SELECT COUNT(*) FROM league_standings_application WHERE season_id = ?",active.seasonId())!=90)reasons.add("LEAGUE_RESULTS_PENDING");
        if(count("SELECT COUNT(*) FROM league_outbox WHERE season_id = ? AND lifecycle_status <> 'DELIVERED'",active.seasonId())>0)reasons.add("LEAGUE_OUTBOX_PENDING");
        if(count("SELECT COUNT(*) FROM league_job WHERE season_id = ? AND (lifecycle_status <> 'COMPLETED' OR lease_token IS NOT NULL)",active.seasonId())>0)reasons.add("LEAGUE_JOB_PENDING");
        if(count("SELECT COUNT(*) FROM league_player_binding WHERE season_id = ? AND lifecycle_status <> 'VERIFIED'",active.seasonId())>0)reasons.add("LEAGUE_PLAYER_SERIES_PENDING");
        if(count("SELECT COUNT(*) FROM league_player_series_checkpoint c JOIN league_player_binding b ON b.binding_hash=c.binding_hash WHERE b.season_id=? AND c.series_status <> 'COMPLETED'",active.seasonId())>0)reasons.add("LEAGUE_PLAYER_CHECKPOINT_PENDING");
        if(count("SELECT COUNT(*) FROM career_competition_series_checkpoint c JOIN career_competition_series_binding b ON b.binding_hash=c.binding_hash WHERE b.career_id=? AND b.calendar_season_year=? AND c.series_status <> 'COMPLETED'",id,year)>0)reasons.add("COMPETITION_PLAYER_CHECKPOINT_PENDING");
        if(count("SELECT COUNT(*) FROM career_calendar_advance_command WHERE career_id = ? AND command_status = 'PENDING'",id)>0)reasons.add("CALENDAR_COMMAND_PENDING");
        if(count("SELECT COUNT(*) FROM career_competition_series_binding WHERE career_id = ? AND calendar_season_year = ? AND lifecycle_status <> 'COMPLETED'",id,year)>0)reasons.add("COMPETITION_RESULT_PENDING");
        if(count("""
            SELECT COUNT(*) FROM career_competition_job j JOIN career_competition_series_binding b ON b.binding_hash = j.binding_hash
            WHERE b.career_id = ? AND b.calendar_season_year = ? AND (j.lifecycle_status <> 'COMPLETED' OR j.lease_token IS NOT NULL)
            """,id,year)>0)reasons.add("COMPETITION_JOB_PENDING");
        if(count("""
            SELECT COUNT(*) FROM career_competition_fixture f LEFT JOIN career_competition_application a
            ON a.career_id=f.career_id AND a.calendar_season_year=f.calendar_season_year AND a.competition_id=f.competition_id AND a.match_id=f.match_id
            WHERE f.career_id=? AND f.calendar_season_year=? AND (f.lifecycle_status <> 'COMPLETED' OR a.receipt_hash IS NULL)
            """,id,year)>0)reasons.add("COMPETITION_FIXTURE_APPLICATION_PENDING");
        return List.copyOf(reasons);
    }
    private CareerRelationalStore.CareerRow requireCareer(String id) { return careers.find(id).orElseThrow(CareerException::notFound); }
    private int count(String sql,Object...args){return jdbc.queryForObject(sql,Integer.class,args);}
    private String resultHash(Object domestic,Object international){return CareerInternationalRules.hash(write(List.of(domestic,international)));}
    private String write(Object value){try{return competitions.json.copy().enable(com.fasterxml.jackson.databind.SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS).writeValueAsString(value);}catch(java.io.IOException failure){throw new IllegalStateException(failure);}}
}
