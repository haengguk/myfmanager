package com.lolfm.career;

import static com.lolfm.career.CareerRosterStore.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Explicit bounded write command. No simulation, startup scan, growth, awards or payments. */
@Service
public final class CareerRecordsRestoration {
    private final JdbcTemplate db;private final TransactionTemplate tx;private final com.lolfm.application.JdbcLeagueBoundSeriesCheckpointAdapter checkpoints;
    public CareerRecordsRestoration(JdbcTemplate db,PlatformTransactionManager manager,com.lolfm.application.JdbcLeagueBoundSeriesCheckpointAdapter checkpoints){this.db=db;this.tx=new TransactionTemplate(manager);this.checkpoints=checkpoints;}
    public record Result(String careerId,int restored,boolean remaining,String note) {}
    public Result restore(String career){return tx.execute(t->{
        lockCareer(db,career);CareerContinuousGuard.requireCommand(db,career);
        var pending=db.query("SELECT r.receipt_json,f.series_id FROM career_competition_completion_receipt r JOIN career_competition_series_binding b ON b.binding_hash=r.binding_hash JOIN career_competition_fixture f ON f.career_id=b.career_id AND f.calendar_season_year=b.calendar_season_year AND f.competition_id=b.competition_id AND f.match_id=b.match_id WHERE b.career_id=? AND b.lifecycle_status='COMPLETED' AND NOT EXISTS(SELECT 1 FROM career_record_series s WHERE s.career_id=b.career_id AND s.series_id=f.series_id) ORDER BY f.series_id LIMIT 10",(r,n)->Map.entry(r.getString(2),read(r.getString(1),CareerCompetitionFixtureCompletionReceiptV1.class)),career);
        int count=0;
        for(var row:pending){var receipt=row.getValue();stageCheckpoint(row.getKey());
            db.query("SELECT stage_id,scheduled_date,fixture_id FROM career_competition_fixture WHERE career_id=? AND series_id=?",(org.springframework.jdbc.core.RowCallbackHandler)f->CareerRecordsStore.complete(db,career,receipt.seasonYear(),"COMP|"+receipt.seasonYear()+'|'+receipt.competitionId()+'|'+receipt.matchId(),receipt.competitionId(),f.getString(1),f.getString(3),receipt.seriesId(),f.getObject(2,java.time.LocalDate.class),receipt.receiptHash(),receipt.winnerTeamCode(),receipt.firstTeamCode(),receipt.secondTeamCode(),receipt.orderedGames(),false),career,row.getKey());count++;
        }
        var league=db.query("SELECT r.receipt_json,s.season_year,f.round_number FROM league_completion_receipt r JOIN league_fixture f ON f.completion_receipt_hash=r.receipt_hash JOIN career_season s ON s.season_id=f.season_id WHERE s.career_id=? AND f.lifecycle_status='COMPLETED' AND NOT EXISTS(SELECT 1 FROM career_record_series x WHERE x.career_id=s.career_id AND x.series_id=f.bound_series_id) ORDER BY f.bound_series_id LIMIT 10",(r,n)->new Object[]{read(r.getString(1),com.lolfm.league.LeagueFixtureCompletionReceiptV2.class),r.getInt(2),r.getInt(3)},career);
        for(var row:league){var receipt=(com.lolfm.league.LeagueFixtureCompletionReceiptV2)row[0];int year=(int)row[1];var old=receipt.fixtureReceipt();stageCheckpoint(receipt.boundSeriesId());
            var date=CareerRecordsStore.roundDates(year).get((int)row[2]);
            CareerRecordsStore.complete(db,career,year,"LEAGUE|"+old.seasonId()+'|'+old.fixtureId(),"LCK_REGULAR_R1_R2","R1_R2",old.fixtureId(),old.boundSeriesId(),date,receipt.canonicalFixtureReceiptHash(),old.winnerTeamCode(),old.firstTeamCode(),old.secondTeamCode(),old.orderedGameReceipts(),false);count++;
        }
        boolean remaining=pending.size()==10||league.size()==10;
        db.update("MERGE INTO career_record_restore(career_id,cursor_key,completed) KEY(career_id) VALUES (?,?,?)",career,pending.isEmpty()?league.isEmpty()?null:((com.lolfm.league.LeagueFixtureCompletionReceiptV2)league.getLast()[0]).boundSeriesId():pending.getLast().getKey(),!remaining);
        return new Result(career,count,remaining,"보존된 승인 증거만 복원했습니다. 과거 개인상·성장·계약·상금은 다시 적용하지 않습니다. 성적 없는 Auto 기록은 승패만 표시합니다.");
    });}
    private void stageCheckpoint(String series){
        CareerRecordsStore.stage(db,series,checkpoints.recoverStatistics(series));
    }
}
