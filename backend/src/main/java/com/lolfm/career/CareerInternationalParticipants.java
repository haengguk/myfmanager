package com.lolfm.career;

import com.lolfm.player.GlobalTeamRosterCatalog.TeamKey;
import java.util.List;
import java.util.Map;

/** Replace this input provider with overseas league results; sealed registrations never call it again. */
public interface CareerInternationalParticipants {
    Selection overseas(String careerId, int year, String competitionId);
    CompetitionRosterSnapshot.Roster roster(TeamKey team);
    default Selection overseas(String careerId, int year, String competitionId, CompetitionRosterSnapshot seasonRosters) {
        var selected = overseas(careerId,year,competitionId);
        var rankings = new java.util.LinkedHashMap<String,List<CompetitionRosterSnapshot.Roster>>();
        selected.rankings().forEach((region,teams) -> rankings.put(region,teams.stream()
                .map(r -> seasonRosters.roster(CompetitionRosterSnapshot.token(r.team()))).toList()));
        return new Selection(selected.policy(),selected.evidence()+";SEASON_ROSTER="+seasonRosters.identity(),rankings);
    }
    record Selection(String policy, String evidence, Map<String, List<CompetitionRosterSnapshot.Roster>> rankings) {
        public Selection { rankings = rankings.entrySet().stream().collect(java.util.stream.Collectors.toUnmodifiableMap(Map.Entry::getKey, e->List.copyOf(e.getValue()))); }
    }
}
