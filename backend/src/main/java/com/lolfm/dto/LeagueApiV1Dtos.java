package com.lolfm.dto;

import java.time.OffsetDateTime;
import java.util.List;

/** Additive wire contract for /api/v1/leagues. */
public final class LeagueApiV1Dtos {
    public static final String CREATE_REQUEST_SCHEMA = "AI_LEAGUE_CREATE_REQUEST_V1";
    public static final String LIFECYCLE_COMMAND_SCHEMA =
            "AI_LEAGUE_LIFECYCLE_COMMAND_V1";
    public static final String RUN_COMMAND_SCHEMA = "AI_LEAGUE_RUN_ROUND_COMMAND_V1";
    public static final String PLAYER_SERIES_COMMAND_SCHEMA =
            "AI_LEAGUE_PLAYER_SERIES_COMMAND_V1";
    public static final String PLAYER_COMPLETION_COMMAND_SCHEMA =
            "AI_LEAGUE_PLAYER_COMPLETION_COMMAND_V1";
    public static final String SEASON_SCHEMA = "AI_LEAGUE_SEASON_VIEW_V1";
    public static final String FIXTURE_SCHEMA = "AI_LEAGUE_FIXTURE_VIEW_V1";
    public static final String FIXTURES_SCHEMA = "AI_LEAGUE_FIXTURE_LIST_V1";
    public static final String STANDINGS_SCHEMA = "AI_LEAGUE_STANDINGS_VIEW_V1";
    public static final String JOB_SCHEMA = "AI_LEAGUE_JOB_VIEW_V1";
    public static final String RUN_RESPONSE_SCHEMA = "AI_LEAGUE_RUN_RESPONSE_V1";
    public static final String PLAYER_SERIES_SCHEMA = "AI_LEAGUE_PLAYER_SERIES_VIEW_V1";
    public static final String COMPLETION_STATUS_SCHEMA =
            "AI_LEAGUE_COMPLETION_STATUS_VIEW_V1";
    public static final String ERROR_SCHEMA = "AI_LEAGUE_API_ERROR_V1";

    private LeagueApiV1Dtos() {}

    public record CreateRequest(
            String schemaVersion,
            String leagueKey,
            String seasonKey,
            String seasonMode,
            String managedTeamCode,
            String seasonRootSeed,
            String clientCommandId
    ) {}

    public record LifecycleCommandRequest(
            String schemaVersion,
            long expectedLifecycleRevision,
            String clientCommandId
    ) {}

    public record RunCurrentRoundRequest(
            String schemaVersion,
            long expectedLifecycleRevision,
            String clientCommandId
    ) {}

    public record PlayerSeriesCommandRequest(
            String schemaVersion,
            long expectedLifecycleRevision,
            String clientCommandId
    ) {}

    public record PlayerCompletionCommandRequest(
            String schemaVersion,
            long expectedLifecycleRevision,
            String clientCommandId,
            String bindingHash
    ) {}

    public record SeasonResponse(String schemaVersion, boolean replayed, SeasonView season) {}

    public record SeasonView(
            String leagueId,
            String seasonId,
            String lifecycleStatus,
            long lifecycleRevision,
            long standingsRevision,
            String seasonMode,
            String managedTeamCode,
            String seasonRootSeed,
            String scheduleIdentity,
            String frozenSnapshotIdentity,
            String productDecisionHash,
            String productionRuntimeIdentity,
            int currentRound,
            FixtureCounters fixtureCounters,
            List<StandingRow> standings,
            FixtureView playableManagedFixture,
            List<String> allowedCommands,
            OffsetDateTime updatedAt
    ) {
        public SeasonView {
            standings = List.copyOf(standings);
            allowedCommands = List.copyOf(allowedCommands);
        }
    }

    public record FixtureCounters(
            int total,
            int completed,
            int inProgress,
            int waiting,
            int blocked,
            int cancelled
    ) {}

    public record StandingRow(
            int position,
            String teamCode,
            int seriesWins,
            int seriesLosses,
            int gameWins,
            int gameLosses,
            int gameDifferential,
            String deterministicDrawHash
    ) {}

    public record FixturesResponse(
            String schemaVersion,
            String leagueId,
            String seasonId,
            long lifecycleRevision,
            long standingsRevision,
            List<FixtureView> fixtures
    ) {
        public FixturesResponse { fixtures = List.copyOf(fixtures); }
    }

    public record FixtureResponse(String schemaVersion, FixtureView fixture) {}

    public record FixtureView(
            String fixtureId,
            int roundNumber,
            String lifecycleStatus,
            long revision,
            String executionMode,
            String firstTeamCode,
            String secondTeamCode,
            String game1BlueTeamCode,
            String game1RedTeamCode,
            String seriesFormat,
            String fixtureRootSeed,
            String boundSeriesId,
            String bindingHash,
            String playerSeriesStatus,
            String completionStatus,
            String jobId,
            String jobStatus,
            List<String> allowedCommands
    ) {
        public FixtureView { allowedCommands = List.copyOf(allowedCommands); }
    }

    public record StandingsResponse(
            String schemaVersion,
            String leagueId,
            String seasonId,
            long standingsRevision,
            String standingsPolicyId,
            List<StandingRow> rows
    ) {
        public StandingsResponse { rows = List.copyOf(rows); }
    }

    public record JobResponse(String schemaVersion, JobView job) {}

    public record JobView(
            String jobId,
            String fixtureId,
            String lifecycleStatus,
            long revision,
            int attemptNumber,
            String failureClass,
            String failureCode,
            boolean retryable,
            OffsetDateTime updatedAt
    ) {}

    public record RunResponse(
            String schemaVersion,
            boolean replayed,
            int queued,
            int existing,
            int playerFixturesExcluded,
            SeasonView season,
            List<JobView> jobs
    ) {
        public RunResponse { jobs = List.copyOf(jobs); }
    }

    public record PlayerSeriesResponse(
            String schemaVersion,
            boolean replayed,
            PlayerSeriesView playerSeries
    ) {}

    public record PlayerSeriesView(
            String leagueId,
            String seasonId,
            String fixtureId,
            String bindingHash,
            long bindingRevision,
            String lifecycleStatus,
            String boundSeriesId,
            String completionReceiptHash,
            List<String> allowedCommands
    ) {
        public PlayerSeriesView { allowedCommands = List.copyOf(allowedCommands); }
    }

    public record CompletionStatusResponse(
            String schemaVersion,
            boolean replayed,
            CompletionStatusView completion
    ) {}

    public record CompletionStatusView(
            String leagueId,
            String seasonId,
            String fixtureId,
            String fixtureStatus,
            String bindingStatus,
            String receiptHash,
            String outboxStatus,
            boolean standingsApplied,
            long standingsRevision,
            List<String> allowedCommands
    ) {
        public CompletionStatusView { allowedCommands = List.copyOf(allowedCommands); }
    }

    public record ErrorResponse(
            String schemaVersion,
            String code,
            String field,
            String message,
            boolean retryable,
            Long currentLifecycleRevision,
            String currentLifecycleStatus
    ) {}
}
