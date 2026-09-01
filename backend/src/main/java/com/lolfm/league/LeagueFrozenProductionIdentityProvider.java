package com.lolfm.league;

import java.util.Set;

/** Internal authority for the current authored roster/resource/runtime identity. */
interface LeagueFrozenProductionIdentityProvider {
    LeagueSeasonFrozenSnapshot currentSnapshot(Set<String> expectedTeamCodes);
    String currentResourceProvenanceHash();
}
