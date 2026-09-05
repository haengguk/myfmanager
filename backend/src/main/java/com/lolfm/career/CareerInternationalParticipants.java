package com.lolfm.career;

import com.lolfm.player.GlobalTeamRosterCatalog.TeamKey;
import java.util.List;
import java.util.Map;

/** Replace this input provider with overseas league results; sealed registrations never call it again. */
public interface CareerInternationalParticipants {
    Selection overseas(String careerId, int year, String competitionId);
    CompetitionRosterSnapshot.Roster roster(TeamKey team);
    record Selection(String policy, String evidence, Map<String, List<CompetitionRosterSnapshot.Roster>> rankings) {
        public Selection { rankings = rankings.entrySet().stream().collect(java.util.stream.Collectors.toUnmodifiableMap(Map.Entry::getKey, e->List.copyOf(e.getValue()))); }
    }
}
