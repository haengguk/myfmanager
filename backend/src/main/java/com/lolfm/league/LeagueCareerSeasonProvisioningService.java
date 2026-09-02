package com.lolfm.league;

import com.lolfm.career.CareerApplicationService;
import java.util.Set;
import org.springframework.stereotype.Service;

/** Internal Career provisioning kernel; it never calls the public League HTTP facade. */
@Service
public final class LeagueCareerSeasonProvisioningService
        implements CareerApplicationService.SeasonProvisioningPort {
    private final LeagueProductionSnapshotProvider snapshots;
    private final LeagueSeasonApplicationService seasons;
    private final LeagueRelationalStore store;

    LeagueCareerSeasonProvisioningService(
            LeagueProductionSnapshotProvider snapshots,
            LeagueSeasonApplicationService seasons,
            LeagueRelationalStore store
    ) {
        this.snapshots = snapshots;
        this.seasons = seasons;
        this.store = store;
    }

    @Override
    public CareerApplicationService.ProvisionedSeason provision(
            String leagueId,
            String seasonId,
            String managedTeamCode,
            long rootSeed
    ) {
        Set<String> teamCodes = snapshots.currentTeamCodes();
        if (!teamCodes.contains(managedTeamCode)) {
            throw new IllegalArgumentException("CAREER_MANAGED_TEAM_NOT_CURRENT");
        }
        if (store.findSeason(seasonId).isPresent()) {
            throw new IllegalStateException("CAREER_DERIVED_SEASON_ALREADY_EXISTS");
        }
        LeagueSeasonFrozenSnapshot snapshot = snapshots.currentSnapshot(teamCodes);
        LeagueSeasonAggregate season = LeagueSeasonAggregate.create(
                leagueId, seasonId, LeagueSeasonMode.HYBRID_MANAGER, managedTeamCode,
                snapshot.teamSnapshotIdentity(managedTeamCode), snapshot, rootSeed,
                LeagueSchedulePolicy.productionDefault());
        seasons.createFrozen(season);
        LeagueSeasonApplicationService.SeasonView ready = seasons.ready(seasonId, 0);
        return new CareerApplicationService.ProvisionedSeason(leagueId, seasonId,
                managedTeamCode, rootSeed, ready.status().name(),
                snapshot.snapshotIdentity(), season.productDecisionHash());
    }
}
