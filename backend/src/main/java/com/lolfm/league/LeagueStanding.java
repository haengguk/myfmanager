package com.lolfm.league;

/** Immutable authoritative counters derived only from applied verified fixture receipts. */
public record LeagueStanding(
        String teamCode,
        int seriesWins,
        int seriesLosses,
        int gameWins,
        int gameLosses
) {
    public LeagueStanding {
        LeagueIdentity.requireTeamCode(teamCode);
        if (seriesWins < 0 || seriesLosses < 0 || gameWins < 0 || gameLosses < 0) {
            throw new IllegalArgumentException("Standing counters cannot be negative");
        }
    }

    public int points() {
        return seriesWins;
    }

    public int gameDifferential() {
        return gameWins - gameLosses;
    }

    LeagueStanding recordWin(int wonGames, int lostGames) {
        return new LeagueStanding(teamCode, seriesWins + 1, seriesLosses,
                gameWins + wonGames, gameLosses + lostGames);
    }

    LeagueStanding recordLoss(int wonGames, int lostGames) {
        return new LeagueStanding(teamCode, seriesWins, seriesLosses + 1,
                gameWins + wonGames, gameLosses + lostGames);
    }
}
