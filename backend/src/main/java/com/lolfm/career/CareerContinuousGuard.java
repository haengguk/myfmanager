package com.lolfm.career;

import java.util.function.Supplier;
import org.springframework.jdbc.core.JdbcTemplate;

/** Checked while holding the existing Career Calendar lock. This is command ownership, not match state. */
public final class CareerContinuousGuard {
    private record Permit(String career,String owner,long fence,CareerContinuousStore store) {}
    private static final ThreadLocal<Permit> CURRENT=new ThreadLocal<>();
    static <T>T execute(String career,String owner,long fence,CareerContinuousStore store,Supplier<T> work) {
        if(CURRENT.get()!=null)throw new IllegalStateException("NESTED_CONTINUOUS_PERMIT");
        CURRENT.set(new Permit(career,owner,fence,store));
        try {
            if(!store.owns(career,owner,fence))throw new IllegalStateException("STALE_CONTINUOUS_WORKER");
            T result=work.get();
            // The child runs in the caller's transaction: expiry during work must roll it back.
            if(!store.owns(career,owner,fence))throw new IllegalStateException("STALE_CONTINUOUS_WORKER");
            return result;
        } finally {CURRENT.remove();}
    }
    public static void requireCommand(JdbcTemplate jdbc,String career) {
        Permit p=CURRENT.get();
        if(p!=null) {
            if(!p.career.equals(career)||!p.store.owns(career,p.owner,p.fence))throw new IllegalStateException("STALE_CONTINUOUS_WORKER");
            return;
        }
        if(jdbc.queryForObject("SELECT COUNT(*) FROM career_continuous_run WHERE career_id=? AND lifecycle_status IN ('RUNNING','WAITING','PAUSE_REQUESTED')",Integer.class,career)>0)
            throw CareerException.continuousBusy();
    }
    public static void requireSeasonCommand(JdbcTemplate jdbc,String season) {
        if(season==null)return;
        for(String career:jdbc.query("SELECT career_id FROM career_season WHERE season_id=?",(r,n)->r.getString(1),season)) {
            CareerRosterStore.lockCareer(jdbc,career);requireCommand(jdbc,career);
        }
    }
    private CareerContinuousGuard() {}
}
