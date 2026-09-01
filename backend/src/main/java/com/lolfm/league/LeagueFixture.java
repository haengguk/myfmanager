package com.lolfm.league;

import com.lolfm.application.SeriesFormat;
import java.util.Objects;
import java.util.Set;

/** Immutable scheduled Series input. Execution mode never participates in seed derivation. */
public record LeagueFixture(
        String fixtureId,
        String pairId,
        int roundNumber,
        int legNumber,
        String firstTeamCode,
        String secondTeamCode,
        String game1BlueTeamCode,
        String game1RedTeamCode,
        SeriesFormat seriesFormat,
        LeagueFixtureExecutionMode executionMode,
        long fixtureRootSeed,
        String boundSeriesId,
        String seedAnchorTeamCode
) {
    public LeagueFixture {
        if (fixtureId == null || !fixtureId.matches("fixture_[0-9a-f]{64}")) {
            throw new IllegalArgumentException("Invalid fixture ID");
        }
        if (pairId == null || !pairId.matches("pair_[A-Z0-9]{2,8}_[A-Z0-9]{2,8}")) {
            throw new IllegalArgumentException("Invalid pair ID");
        }
        if (roundNumber < 1 || legNumber < 1 || legNumber > 2) {
            throw new IllegalArgumentException("Invalid fixture round/leg");
        }
        LeagueIdentity.requireTeamCode(firstTeamCode);
        LeagueIdentity.requireTeamCode(secondTeamCode);
        LeagueIdentity.requireTeamCode(game1BlueTeamCode);
        LeagueIdentity.requireTeamCode(game1RedTeamCode);
        if (firstTeamCode.compareTo(secondTeamCode) >= 0
                || !pairId.equals("pair_" + firstTeamCode + "_" + secondTeamCode)
                || !Set.of(firstTeamCode, secondTeamCode)
                .equals(Set.of(game1BlueTeamCode, game1RedTeamCode))) {
            throw new IllegalArgumentException("Fixture team identity invariant");
        }
        Objects.requireNonNull(seriesFormat, "seriesFormat");
        Objects.requireNonNull(executionMode, "executionMode");
        if (boundSeriesId == null || !boundSeriesId.matches("series_[0-9a-f]{64}")) {
            throw new IllegalArgumentException("Invalid bound Series ID");
        }
        if (!firstTeamCode.equals(seedAnchorTeamCode)) {
            throw new IllegalArgumentException(
                    "Seed anchor must be canonical and execution-mode independent");
        }
    }

    public Set<String> teamCodes() {
        return Set.of(firstTeamCode, secondTeamCode);
    }

    public boolean containsTeam(String teamCode) {
        return firstTeamCode.equals(teamCode) || secondTeamCode.equals(teamCode);
    }

    public String blueTeamCode(int gameNumber) {
        requireGameNumber(gameNumber);
        return gameNumber % 2 == 1 ? game1BlueTeamCode : game1RedTeamCode;
    }

    public String redTeamCode(int gameNumber) {
        requireGameNumber(gameNumber);
        return gameNumber % 2 == 1 ? game1RedTeamCode : game1BlueTeamCode;
    }

    public long gameSeed(int gameNumber, String historyBeforeHash) {
        requireGameNumber(gameNumber);
        LeagueSeasonFrozenSnapshot.requireSha256(historyBeforeHash, "historyBeforeHash");
        return LeagueIdentity.gameSeed(boundSeriesId, fixtureRootSeed, gameNumber,
                blueTeamCode(gameNumber), redTeamCode(gameNumber), seedAnchorTeamCode,
                historyBeforeHash);
    }

    private void requireGameNumber(int gameNumber) {
        if (gameNumber < 1 || gameNumber > seriesFormat.maximumGames()) {
            throw new IllegalArgumentException("Game number outside Series format");
        }
    }
}
