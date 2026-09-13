package com.lolfm.career;
import org.springframework.jdbc.core.JdbcTemplate;
/** Season-scoped adoption, independent of immutable match and award identities. */
final class CareerEvaluationVersion {
    static void begin(JdbcTemplate db,String career,int year,boolean fresh) {
        if(db.queryForObject("SELECT COUNT(*) FROM career_evaluation_policy WHERE career_id=? AND season_year=?",Integer.class,career,year)==0)
            db.update("INSERT INTO career_evaluation_policy VALUES (?,?,?)",career,year,fresh?CareerPerformanceV2.VERSION:CareerPerformancePolicy.VERSION);
    }
    static String get(JdbcTemplate db,String career,int year) {
        var rows=db.query("SELECT evaluation_version FROM career_evaluation_policy WHERE career_id=? AND season_year=?",(r,n)->r.getString(1),career,year);
        return rows.isEmpty()?CareerPerformancePolicy.VERSION:rows.getFirst();
    }
    private CareerEvaluationVersion() {}
}
