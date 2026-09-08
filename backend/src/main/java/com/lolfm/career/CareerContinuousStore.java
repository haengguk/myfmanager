package com.lolfm.career;

import static com.lolfm.career.CareerContinuousProgress.*;
import static com.lolfm.career.CareerRosterStore.hash;
import static com.lolfm.career.CareerRosterStore.read;
import static com.lolfm.career.CareerRosterStore.write;
import static com.lolfm.career.CareerRosterStore.lockCareer;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Repository
public final class CareerContinuousStore {
    final JdbcTemplate jdbc;
    final TransactionTemplate tx;
    final Clock clock;
    static final Duration LEASE=Duration.ofSeconds(90);
    public CareerContinuousStore(JdbcTemplate jdbc,PlatformTransactionManager manager,Clock clock) {
        this.jdbc=jdbc;this.tx=new TransactionTemplate(manager);this.clock=clock;
    }
    OffsetDateTime now(){return clock.instant().atOffset(ZoneOffset.UTC);}
    void lock(String career) {
        // Existing Calendar lock order, shared with manual advance and rollover.
        jdbc.queryForObject("SELECT lock_name FROM career_calendar_operation_lock WHERE lock_name='ADVANCE_COMMANDS' FOR UPDATE",String.class);
        lockCareer(jdbc,career);
    }
    Run load(String career) {
        return jdbc.query("SELECT run_id,lifecycle_status,revision,state_json,state_hash FROM career_continuous_run WHERE career_id=?",(r,n)->{
            String json=r.getString(4);if(!hash(json).equals(r.getString(5)))throw new IllegalStateException("CONTINUOUS_STATE_INTEGRITY");
            Run run=read(json,Run.class);
            if(!run.careerId.equals(career)||!run.runId.equals(r.getString(1))||!run.status.name().equals(r.getString(2))||run.revision!=r.getLong(3))throw new IllegalStateException("CONTINUOUS_STATE_IDENTITY");
            return run;
        },career).stream().findFirst().orElse(null);
    }
    void save(Run run) {
        String json=write(run);
        if(jdbc.update("UPDATE career_continuous_run SET lifecycle_status=?,revision=?,state_json=?,state_hash=? WHERE career_id=? AND run_id=?",
                run.status.name(),run.revision,json,hash(json),run.careerId,run.runId)!=1)throw new IllegalStateException("CONTINUOUS_RUN_REPLACED");
    }
    void insert(Run run) {
        long fence=jdbc.query("SELECT lease_fence FROM career_continuous_run WHERE career_id=?",(r,n)->r.getLong(1),run.careerId).stream().findFirst().orElse(0L);
        jdbc.update("DELETE FROM career_continuous_run WHERE career_id=?",run.careerId);
        String json=write(run);
        jdbc.update("INSERT INTO career_continuous_run(career_id,run_id,lifecycle_status,revision,state_json,state_hash,next_wake,lease_fence) VALUES (?,?,?,?,?,?,?,?)",
                run.careerId,run.runId,run.status.name(),run.revision,json,hash(json),now(),fence+1);
    }
    List<String> due() {
        return jdbc.query("SELECT career_id FROM career_continuous_run WHERE lifecycle_status IN ('RUNNING','WAITING','PAUSE_REQUESTED') AND next_wake<=? AND (lease_owner IS NULL OR lease_until<=?) ORDER BY next_wake,career_id LIMIT 8",
                (r,n)->r.getString(1),now(),now());
    }
    long claim(String career,String owner) {
        int updated=jdbc.update("UPDATE career_continuous_run SET lease_owner=?,lease_fence=lease_fence+1,lease_until=? WHERE career_id=? AND lifecycle_status IN ('RUNNING','WAITING','PAUSE_REQUESTED') AND (lease_owner IS NULL OR lease_until<=?)",
                owner,now().plus(LEASE),career,now());
        return updated==0?-1:jdbc.queryForObject("SELECT lease_fence FROM career_continuous_run WHERE career_id=?",Long.class,career);
    }
    boolean owns(String career,String owner,long fence) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM career_continuous_run WHERE career_id=? AND lease_owner=? AND lease_fence=? AND lease_until>?",Integer.class,career,owner,fence,now())==1;
    }
    void release(String career,String owner,long fence,int delaySeconds) {
        jdbc.update("UPDATE career_continuous_run SET lease_owner=NULL,lease_until=NULL,next_wake=? WHERE career_id=? AND lease_owner=? AND lease_fence=?",
                now().plusSeconds(delaySeconds),career,owner,fence);
    }
    void defer(String career){jdbc.update("UPDATE career_continuous_run SET next_wake=? WHERE career_id=?",now().plusSeconds(30),career);}
    int activeYear(String career){return jdbc.queryForObject("SELECT active_calendar_season_year FROM career_calendar_state WHERE career_id=?",Integer.class,career);}
    void wake(String career){jdbc.update("UPDATE career_continuous_run SET next_wake=? WHERE career_id=?",now(),career);}
    LocalDate date(String career){return jdbc.queryForObject("SELECT current_game_date FROM career_calendar_state WHERE career_id=?",LocalDate.class,career);}
    int[] totals(String career,int year) {
        var league=jdbc.queryForObject("SELECT COALESCE(SUM(s.series_wins),0),COALESCE(SUM(s.game_wins),0) FROM league_standing s JOIN career_season c ON c.season_id=s.season_id WHERE c.career_id=? AND c.season_year=?",(r,n)->new int[]{r.getInt(1),r.getInt(2)},career,year);
        var competition=jdbc.queryForObject("SELECT COUNT(*),COALESCE(SUM(first_score+second_score),0) FROM career_competition_result_detail WHERE career_id=? AND calendar_season_year=?",(r,n)->new int[]{r.getInt(1),r.getInt(2)},career,year);
        return new int[]{league[0]+competition[0],league[1]+competition[1]};
    }
    boolean childAccepted(Intent intent) {
        if(intent==null||intent.action()==Action.REFRESH)return false;
        if(intent.jobId()!=null)return true;
        String table=intent.action()==Action.ADVANCE?"career_calendar_advance_command":"career_competition_command";
        return jdbc.queryForObject("SELECT COUNT(*) FROM "+table+" WHERE client_command_id=?",Integer.class,intent.commandId())>0;
    }
    boolean childNeedsWake(String job) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM career_competition_job WHERE job_id=? AND (lifecycle_status='PENDING' OR (lifecycle_status='RUNNING' AND lease_expires_at<=?))",Integer.class,job,now())==1;
    }
    Receipt replay(String career,Command request) {
        String json=write(request);
        return jdbc.query("SELECT career_id,payload_json,payload_hash,receipt_json,receipt_hash FROM career_continuous_command WHERE client_command_id=?",(r,n)->{
            if(!hash(r.getString(2)).equals(r.getString(3))||!hash(r.getString(4)).equals(r.getString(5)))throw new IllegalStateException("CONTINUOUS_COMMAND_INTEGRITY");
            if(!career.equals(r.getString(1))||!json.equals(r.getString(2)))throw CareerException.calendarCommandConflict();
            return read(r.getString(4),Receipt.class);
        },request.clientCommandId()).stream().findFirst().orElse(null);
    }
    void record(String career,Command command,Receipt receipt) {
        String request=write(command),result=write(receipt);
        jdbc.update("INSERT INTO career_continuous_command VALUES (?,?,?,?,?,?)",command.clientCommandId(),career,request,hash(request),result,hash(result));
    }
}
