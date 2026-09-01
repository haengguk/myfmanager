package com.lolfm.league;

import java.util.Objects;

/** Immutable authority supplied to one synchronous fixture invocation. */
public record LeagueAutomatedSeriesRunnerInput(
        LeagueSeasonAggregate season,
        LeagueFixture fixture,
        String frozenProductDecisionHash
) {
    public LeagueAutomatedSeriesRunnerInput {
        Objects.requireNonNull(season, "season");
        Objects.requireNonNull(fixture, "fixture");
        LeagueSeasonFrozenSnapshot.requireSha256(
                frozenProductDecisionHash, "frozenProductDecisionHash");
    }
}
