package com.lolfm.career;

import com.lolfm.player.GlobalTeamRosterCatalog;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public final class RatedCareerInternationalParticipants implements CareerInternationalParticipants {
    public static final String POLICY = "TEMPORARY_OVERSEAS_FIVE_STARTER_RATING_SUM_LEXICAL_TEAM_V1";
    private final GlobalTeamRosterCatalog catalog;
    public RatedCareerInternationalParticipants(GlobalTeamRosterCatalog catalog) { this.catalog = catalog; }
    public CompetitionRosterSnapshot.Roster roster(GlobalTeamRosterCatalog.TeamKey team) {
        return CompetitionRosterSnapshot.capture(catalog.snapshot(team));
    }
    public Selection overseas(String careerId, int year, String competitionId) {
        var rankings = new LinkedHashMap<String, List<CompetitionRosterSnapshot.Roster>>();
        for(String region : catalog.leagueCodes().stream().sorted().toList()) {
            if(region.equals("LCK"))continue;
            rankings.put(region, catalog.league(region).ratings().teamCodes().stream()
                    .map(team -> roster(new GlobalTeamRosterCatalog.TeamKey(region,team)))
                    .sorted(Comparator.comparingInt(CompetitionRosterSnapshot.Roster::strength).reversed()
                            .thenComparing(r->CompetitionRosterSnapshot.token(r.team()))).toList());
        }
        return new Selection(POLICY, "REGISTERED_GAMEPLAY_RATINGS_WITHOUT_OVERSEAS_MATCH_RESULTS", rankings);
    }
}
