package com.lolfm.career;

import java.util.LinkedHashMap;
import java.util.List;
import com.lolfm.player.GlobalTeamRosterCatalog.TeamKey;

/** Immutable season input; only the first capture may consult the authored catalog. */
final class CareerSeasonRosters {
    private CareerSeasonRosters() {}
    static CompetitionRosterSnapshot load(CareerCompetitionRelationalStore store, String career, int year) {
        var values = store.jdbc.query("SELECT roster_json, roster_hash FROM career_season WHERE career_id = ? AND season_year = ?",
                (r,n) -> {
                    String value = r.getString(1);
                    if (value == null) return null;
                    var roster = CompetitionRosterSnapshot.decode(value);
                    if (!roster.identity().equals(r.getString(2))) throw new IllegalStateException("SEASON_ROSTER_INTEGRITY");
                    return roster;
                },career,year);
        return values.isEmpty() ? null : values.getFirst();
    }
    static CompetitionRosterSnapshot freezeInitial(CareerCompetitionRelationalStore store, CareerInternationalParticipants provider,
                                                    String career, int year) {
        var existing = load(store,career,year); if (existing != null) return existing;
        CareerSaveCompatibility.requireOriginalReference(store.jdbc,career);
        if (store.findCycle(career,year,false).getFirst().seasonOrdinal() != 1)
            throw new IllegalStateException("CARRIED_SEASON_ROSTER_REQUIRED");
        var frozen = new LinkedHashMap<String,CompetitionRosterSnapshot.Roster>();
        var selection = provider.overseas(career,year,"FIRST_STAND");
        selection.rankings().values().stream().flatMap(List::stream).forEach(r -> frozen.put(CompetitionRosterSnapshot.token(r.team()),r));
        var teams = store.jdbc.query("SELECT DISTINCT team_code FROM career_competition_seed WHERE career_id = ? AND calendar_season_year = ? AND competition_id = 'LCK_CUP' ORDER BY team_code",
                (r,n)->r.getString(1),career,year);
        for (String team : teams) { var roster = provider.roster(new TeamKey("LCK",team)); frozen.put(CompetitionRosterSnapshot.token(roster.team()),roster); }
        // Existing registrations are the authority for previously used rosters, including overseas ranking candidates.
        for (String competition : CareerInternationalRules.COMPETITIONS) {
            var state = CareerInternationalCompetition.load(store,career,year,competition);
            if (state == null) continue;
            var evidence = CareerDomesticEvidence.read(store.json,state.inputEvidence(),com.fasterxml.jackson.databind.JsonNode.class);
            if (evidence.isArray() && !evidence.isEmpty()) {
                var prior = store.json.convertValue(evidence.get(0),CareerInternationalParticipants.Selection.class);
                prior.rankings().values().stream().flatMap(List::stream).forEach(r -> frozen.put(CompetitionRosterSnapshot.token(r.team()),r));
            }
            frozen.putAll(state.rosters().teams());
        }
        var snapshot = new CompetitionRosterSnapshot(frozen);
        var league = store.jdbc.queryForObject("SELECT l.frozen_snapshot_json FROM league_season l JOIN career_season s ON s.season_id=l.season_id WHERE s.career_id=? AND s.season_year=?",String.class,career,year);
        var source = CareerDomesticEvidence.read(store.json,league,com.lolfm.league.LeagueSeasonFrozenSnapshot.class);
        for (String team : teams) if (!source.teamSnapshotIdentity(team).equals(com.lolfm.league.LeagueProductionSnapshotProvider.teamSnapshotIdentity(team,
                snapshot.roster("LCK:"+team).assemble()))) throw new IllegalStateException("INITIAL_CAREER_ROSTER_RESOURCE_CHANGED");
        if (teams.size() != 10 || frozen.size() != 56) throw new IllegalStateException("SEASON_ROSTER_CARDINALITY");
        if (store.jdbc.update("UPDATE career_season SET roster_json = ?, roster_hash = ? WHERE career_id = ? AND season_year = ? AND roster_json IS NULL",
                snapshot.encoded(),snapshot.identity(),career,year) != 1) throw new IllegalStateException("SEASON_ROSTER_FREEZE_CONFLICT");
        return snapshot;
    }
}
