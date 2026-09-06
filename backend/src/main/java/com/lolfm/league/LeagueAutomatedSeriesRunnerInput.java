package com.lolfm.league;

import java.util.Objects;

/** Immutable authority supplied to one synchronous fixture invocation. */
public record LeagueAutomatedSeriesRunnerInput(
        LeagueSeasonAggregate season,
        LeagueFixture fixture,
        String frozenProductDecisionHash,
        com.lolfm.career.CompetitionRosterSnapshot frozenRosters
) {
    public LeagueAutomatedSeriesRunnerInput(LeagueSeasonAggregate season,LeagueFixture fixture,String product) { this(season,fixture,product,null); }
    public LeagueAutomatedSeriesRunnerInput {
        Objects.requireNonNull(season, "season");
        Objects.requireNonNull(fixture, "fixture");
        LeagueSeasonFrozenSnapshot.requireSha256(
                frozenProductDecisionHash, "frozenProductDecisionHash");
    }
}
