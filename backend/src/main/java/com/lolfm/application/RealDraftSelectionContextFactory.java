package com.lolfm.application;

import com.lolfm.champion.ChampionId;
import com.lolfm.domain.Team;
import com.lolfm.draft.DraftSelectionContext;
import java.util.Objects;
import java.util.Set;

/** Canonical production factory shared by both Real Draft orchestration paths. */
public final class RealDraftSelectionContextFactory {
    private RealDraftSelectionContextFactory() {
    }

    public static DraftSelectionContext create(
            long matchSeed,
            String blueTeamCode,
            Team blueTeam,
            String redTeamCode,
            Team redTeam,
            int seriesGameNumber,
            Set<ChampionId> exclusionsBeforeDraft
    ) {
        Objects.requireNonNull(exclusionsBeforeDraft, "exclusionsBeforeDraft");
        return new DraftSelectionContext(matchSeed, blueTeamCode, redTeamCode,
                SimulationProvenanceService.rosterIdentityHash(
                        blueTeamCode, blueTeam, redTeamCode, redTeam),
                seriesGameNumber,
                SimulationProvenanceService.seriesHistoryHash(
                        seriesGameNumber - 1, exclusionsBeforeDraft));
    }
}
