package com.lolfm.career;

import java.time.LocalDate;
import java.util.List;

/** Narrow structured boundary from Career time to existing durable League state. */
public interface CareerCalendarLeaguePort {
    SeasonProjection load(String leagueId, String seasonId);

    GateResult gateAndDispatch(String seasonId, List<String> fixtureIds);

    boolean wakeBackground(String commandId);

    record SeasonProjection(
            String leagueId, String seasonId, String scheduleIdentity,
            String seasonLifecycleStatus, boolean allFixturesCompleted,
            long standingsRevision,
            List<RankedTeamProjection> ranking,
            List<FixtureProjection> fixtures
    ) {
        public SeasonProjection {
            ranking = List.copyOf(ranking);
            fixtures = List.copyOf(fixtures);
        }
    }

    record RankedTeamProjection(
            int rank, String teamCode, int seriesWins, int seriesLosses,
            int gameWins, int gameLosses
    ) {}

    record FixtureProjection(
            String fixtureId, int roundNumber, String executionMode,
            String fixtureStatus, String firstTeamCode, String secondTeamCode,
            long fixtureRootSeed, String boundSeriesId, String jobStatus,
            boolean pendingOutbox, String bindingStatus, String childSeriesStatus
    ) {}

    record GateResult(
            String stopReason, boolean pending, boolean backgroundRequired,
            String fixtureId, String seriesId
    ) {
        public static GateResult clear() {
            return new GateResult(null, false, false, null, null);
        }
    }
}
