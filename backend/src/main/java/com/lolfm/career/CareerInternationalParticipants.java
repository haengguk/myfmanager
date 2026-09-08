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
    record Qualification(String team,int regionalSeed,String path,String competitionId,String resultHash){}
    record Selection(String policy, String evidence, Map<String, List<CompetitionRosterSnapshot.Roster>> rankings,
                     Map<String,List<Qualification>> qualifications,java.util.Set<String> playoffEligible) {
        public Selection(String policy,String evidence,Map<String,List<CompetitionRosterSnapshot.Roster>> rankings){this(policy,evidence,rankings,Map.of(),java.util.Set.of());}
        public Selection { rankings = rankings.entrySet().stream().collect(java.util.stream.Collectors.toUnmodifiableMap(Map.Entry::getKey, e->List.copyOf(e.getValue())));qualifications=qualifications==null?Map.of():Map.copyOf(qualifications);playoffEligible=playoffEligible==null?java.util.Set.of():java.util.Set.copyOf(playoffEligible); }
    }
}
