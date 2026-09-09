package com.lolfm.career;

import static com.lolfm.career.CareerRosterStore.*;
import com.lolfm.league.LeagueFixtureGameReceiptV1;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;

/** Approved receipt adjunct: no timeline, selection trace, or new gameplay input. */
public final class CareerDraftEvidence {
    public record Pick(String playerId,String position,String championId,String team) {}
    public record Evidence(int gameNumber,String blue,String red,List<String> blueBans,List<String> redBans,List<Pick> picks,String firstPickTeam,String firstPick,String rule,List<String> unavailableBefore,String engine) {}
    static Evidence from(LeagueFixtureGameReceiptV1 r,String competition) {
        var first=r.orderedDraftDecisions().stream().filter(d->d.actionType().name().equals("PICK")).findFirst().orElse(null);
        String blue=CareerRecordsStore.team(r.blueTeamCode(),competition),red=CareerRecordsStore.team(r.redTeamCode(),competition);
        return new Evidence(r.gameNumber(),blue,red,r.blueBans().stream().map(c->c.value()).toList(),r.redBans().stream().map(c->c.value()).toList(),r.orderedFinalAssignments().stream().map(a->new Pick(a.playerId().value(),a.position().name(),a.championId().value(),a.teamSide().name().equals("BLUE")?blue:red)).toList(),first==null?null:first.side().name().equals("BLUE")?blue:red,first==null?null:first.championId().value(),r.draftRuleSetIdentity(),r.historyBeforePicks().stream().map(c->c.value()).toList(),r.engineImplementationVersion());
    }
    static void save(JdbcTemplate db,String record,String competition,List<LeagueFixtureGameReceiptV1> receipts) {
        for(var r:receipts){String json=write(from(r,competition)),digest=hash(json);var old=db.query("SELECT evidence_hash FROM career_record_draft WHERE record_id=? AND game_number=?",(s,n)->s.getString(1),record,r.gameNumber());
            if(!old.isEmpty()){if(!old.getFirst().equals(digest))throw new IllegalStateException("DRAFT_EVIDENCE_CONFLICT");continue;}
            db.update("INSERT INTO career_record_draft VALUES (?,?,?,?)",record,r.gameNumber(),json,digest);
        }
    }
    static Evidence readEvidence(String json,String digest){if(!hash(json).equals(digest))throw new IllegalStateException("DRAFT_EVIDENCE_INTEGRITY");return read(json,Evidence.class);}
    // Existing explicit restore command only. Bound by 10 original approved Series per call.
    static int restore(JdbcTemplate db,String career) {
        var rows=db.query("SELECT record_id,competition_id,series_id FROM career_record_series s WHERE career_id=? AND NOT EXISTS(SELECT 1 FROM career_record_draft d WHERE d.record_id=s.record_id) AND (EXISTS(SELECT 1 FROM career_competition_completion_receipt r JOIN career_competition_series_binding b ON b.binding_hash=r.binding_hash WHERE b.series_id=s.series_id) OR EXISTS(SELECT 1 FROM league_fixture f WHERE f.bound_series_id=s.series_id AND f.completion_receipt_hash IS NOT NULL)) ORDER BY record_revision LIMIT 10",(r,n)->List.of(r.getString(1),r.getString(2),r.getString(3)),career);
        for(var row:rows){var comp=db.query("SELECT r.receipt_json FROM career_competition_completion_receipt r JOIN career_competition_series_binding b ON b.binding_hash=r.binding_hash WHERE b.series_id=?",(r,n)->read(r.getString(1),CareerCompetitionFixtureCompletionReceiptV1.class),row.get(2));
            if(!comp.isEmpty())save(db,row.get(0),row.get(1),comp.getFirst().orderedGames());
            else db.query("SELECT r.receipt_json FROM league_completion_receipt r JOIN league_fixture f ON f.completion_receipt_hash=r.receipt_hash WHERE f.bound_series_id=?",(org.springframework.jdbc.core.RowCallbackHandler) r->save(db,row.get(0),row.get(1),read(r.getString(1),com.lolfm.league.LeagueFixtureCompletionReceiptV2.class).orderedGameReceipts()),row.get(2));
        }return rows.size();
    }
    private CareerDraftEvidence() {}
}
