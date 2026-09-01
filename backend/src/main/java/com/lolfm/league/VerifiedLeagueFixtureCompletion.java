package com.lolfm.league;

/**
 * Structurally verified completion input for standings. Batch 2 will construct this only
 * after validating the canonical unified fixture receipt.
 */
public record VerifiedLeagueFixtureCompletion(
        String fixtureId,
        String canonicalFixtureReceiptHash,
        String winnerTeamCode,
        String loserTeamCode,
        int winnerGameWins,
        int loserGameWins
) {
    public VerifiedLeagueFixtureCompletion {
        if (fixtureId == null || !fixtureId.matches("fixture_[0-9a-f]{64}")) {
            throw new IllegalArgumentException("Invalid fixture completion identity");
        }
        LeagueSeasonFrozenSnapshot.requireSha256(canonicalFixtureReceiptHash,
                "canonicalFixtureReceiptHash");
        LeagueIdentity.requireTeamCode(winnerTeamCode);
        LeagueIdentity.requireTeamCode(loserTeamCode);
        if (winnerTeamCode.equals(loserTeamCode)
                || winnerGameWins < 1 || loserGameWins < 0) {
            throw new IllegalArgumentException("Invalid fixture completion score");
        }
    }
}
