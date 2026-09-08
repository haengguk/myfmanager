package com.lolfm.league;

import java.util.Objects;

/** Immutable authority supplied to one synchronous fixture invocation. */
public record LeagueAutomatedSeriesRunnerInput(
        LeagueSeasonAggregate season,
        LeagueFixture fixture,
        String frozenProductDecisionHash,
        com.lolfm.career.CompetitionRosterSnapshot frozenRosters,
        com.lolfm.application.MatchEngineV1Policy.Requirement boundPolicy
) {
    public LeagueAutomatedSeriesRunnerInput(LeagueSeasonAggregate season,LeagueFixture fixture,String product) { this(season,fixture,product,null); }
    public LeagueAutomatedSeriesRunnerInput(LeagueSeasonAggregate season,LeagueFixture fixture,String product,com.lolfm.career.CompetitionRosterSnapshot rosters) {
        this(season,fixture,product,rosters,com.lolfm.application.MatchEngineV1Policy.requirement());
    }
    public LeagueAutomatedSeriesRunnerInput {
        Objects.requireNonNull(season, "season");
        Objects.requireNonNull(boundPolicy,"boundPolicy");
        Objects.requireNonNull(fixture, "fixture");
        LeagueSeasonFrozenSnapshot.requireSha256(
                frozenProductDecisionHash, "frozenProductDecisionHash");
    }
}
