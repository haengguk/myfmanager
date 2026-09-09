package com.lolfm.career;

import static com.lolfm.career.CareerRosterStore.*;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;

/** Rebuildable search projection. Canonical evidence and hashes are never rewritten. */
public final class CareerObservationIndex {
    public static void backfill(JdbcTemplate db) {
        db.query("SELECT o.career_id,o.season_year,o.observation_key,o.observation_json,o.observation_hash FROM career_record_observation o WHERE NOT EXISTS (SELECT 1 FROM career_observation_index i WHERE i.career_id=o.career_id AND i.season_year=o.season_year AND i.observation_key=o.observation_key) ORDER BY o.career_id,o.season_year,o.observed_date,o.observation_key",
            (org.springframework.jdbc.core.RowCallbackHandler) r -> index(db,r.getString(1),r.getInt(2),r.getString(3),r.getString(4),r.getString(5)));
    }
    static void index(JdbcTemplate db,String career,int year,String key,String json,String digest) {
        if(!hash(json).equals(digest))throw new IllegalStateException("OBSERVATION_INTEGRITY");
        JsonNode value=read(json,JsonNode.class);
        String category=key.equals("OPENING")||key.equals("CLOSING_FINAL")||key.startsWith("MONTH:")?"GROWTH":key.startsWith("EVENT:")||key.startsWith("TITLE:")||key.startsWith("PLACEMENT:")?"HISTORY":"INTERNAL";
        db.update("MERGE INTO career_observation_index(career_id,season_year,observation_key,category,fact_revision) KEY(career_id,season_year,observation_key) VALUES (?,?,?,?,?)",career,year,key,category,value.path("revision").asLong());
        long seq=db.queryForObject("SELECT sequence FROM career_observation_index WHERE career_id=? AND season_year=? AND observation_key=?",Long.class,career,year,key);
        db.update("DELETE FROM career_observation_subject WHERE sequence=?",seq);
        var subjects=new TreeSet<String>();
        if(category.equals("GROWTH"))value.path("players").fieldNames().forEachRemaining(id->subjects.add("PLAYER|"+id));
        else {
            if(value.hasNonNull("playerId"))subjects.add("PLAYER|"+value.path("playerId").asText());
            if(value.hasNonNull("team"))subjects.add("TEAM|"+value.path("team").asText());
            if(value.hasNonNull("winner"))subjects.add("TEAM|"+value.path("winner").asText());
            value.path("registered").forEach(id->subjects.add("PLAYER|"+id.asText()));
        }
        for(String subject:subjects){int split=subject.indexOf('|');db.update("INSERT INTO career_observation_subject VALUES (?,?,?)",seq,subject.substring(0,split),subject.substring(split+1));}
    }
    static void related(JdbcTemplate db,String career,int year,String key,Collection<String> teams) {
        var sequences=db.query("SELECT sequence FROM career_observation_index WHERE career_id=? AND season_year=? AND observation_key=?",(r,n)->r.getLong(1),career,year,key);
        if(sequences.isEmpty())return;
        for(String team:teams)if(team!=null)db.update("MERGE INTO career_observation_subject(sequence,kind,entity) KEY(kind,entity,sequence) VALUES (?,'TEAM',?)",sequences.getFirst(),team);
    }
    /** Recover both original trading clubs from retained structured terms, never today's membership. */
    public static void backfillTradingClubs(JdbcTemplate db) {
        db.query("SELECT career_id,state_json,state_hash FROM career_market_state",(org.springframework.jdbc.core.RowCallbackHandler) r->{
            String career=r.getString(1),json=r.getString(2);if(!hash(json).equals(r.getString(3)))throw new IllegalStateException("MARKET_STATE_INTEGRITY");
            var management=read(json,JsonNode.class).path("management");var relations=new HashMap<String,List<String>>();
            management.path("trades").fields().forEachRemaining(e->{var terms=e.getValue().path("terms");relations.put(e.getKey(),List.of(terms.path("seller").asText(),terms.path("buyer").asText()));});
            management.path("loans").fields().forEachRemaining(e->relations.put(e.getKey(),List.of(e.getValue().path("parentTeam").asText(),e.getValue().path("borrowingTeam").asText())));
            if(relations.isEmpty())return;
            db.query("SELECT season_year,observation_key,observation_json,observation_hash FROM career_record_observation WHERE career_id=? AND observation_key LIKE 'EVENT:%'",(org.springframework.jdbc.core.RowCallbackHandler) o->{
                if(!hash(o.getString(3)).equals(o.getString(4)))throw new IllegalStateException("OBSERVATION_INTEGRITY");
                String reference=read(o.getString(3),JsonNode.class).path("referenceId").asText();
                if(relations.containsKey(reference))related(db,career,o.getInt(1),o.getString(2),relations.get(reference));
            },career);
        });
    }
    private CareerObservationIndex() {}
}
