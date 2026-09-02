package com.lolfm.league;

import com.lolfm.dto.LeagueApiV1Dtos;
import com.lolfm.application.SeriesStatus;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

/** Authoritative API projection. It parses no display text and mutates no state. */
@Component
final class LeagueApiV1ResponseMapper {
    private final LeagueRelationalStore store;
    private final LeagueSeasonApplicationService seasons;
    private final LeaguePlayerSeriesBindingPort bindings;
    private final LeaguePlayerSeriesKernelPort playerSeries;

    LeagueApiV1ResponseMapper(
            LeagueRelationalStore store,
            LeagueSeasonApplicationService seasons,
            LeaguePlayerSeriesBindingPort bindings,
            LeaguePlayerSeriesKernelPort playerSeries
    ) {
        this.store = store;
        this.seasons = seasons;
        this.bindings = bindings;
        this.playerSeries = playerSeries;
    }

    LeagueApiV1Dtos.SeasonView season(String leagueId, String seasonId) {
        LeagueSeasonAggregate aggregate = requireSeason(leagueId, seasonId);
        LeagueSeasonApplicationService.SeasonView lifecycle = seasons.view(seasonId);
        List<FixtureRow> fixtureRows = fixtureRows(seasonId);
        int currentRound = fixtureRows.stream()
                .filter(row -> !terminal(row.status()))
                .mapToInt(FixtureRow::roundNumber).min().orElse(18);
        List<LeagueApiV1Dtos.FixtureView> current = fixtureRows.stream()
                .filter(row -> row.roundNumber() == currentRound)
                .map(row -> fixture(aggregate, row)).toList();
        LeagueApiV1Dtos.FixtureView playable = current.stream()
                .filter(row -> "PLAYER_CONTROLLED".equals(row.executionMode()))
                .filter(row -> !terminal(row.lifecycleStatus()))
                .findFirst().orElse(null);
        LinkedHashSet<String> allowed = new LinkedHashSet<>();
        allowed.add("VIEW_STANDINGS");
        if (lifecycle.status() == LeaguePersistenceState.SeasonStatus.READY
                || lifecycle.status() == LeaguePersistenceState.SeasonStatus.RUNNING
                || lifecycle.status() == LeaguePersistenceState.SeasonStatus
                .WAITING_FOR_PLAYER) {
            if (current.stream().anyMatch(value -> "FULL_AUTO".equals(value.executionMode())
                    && !terminal(value.lifecycleStatus())
                    && !"BLOCKED".equals(value.lifecycleStatus()))) {
                allowed.add("RUN_CURRENT_ROUND_AUTO_FIXTURES");
            }
            if (lifecycle.status() != LeaguePersistenceState.SeasonStatus.READY) {
                allowed.add("PAUSE_SEASON");
            }
            allowed.add("CANCEL_SEASON");
        } else if (lifecycle.status() == LeaguePersistenceState.SeasonStatus.PAUSED) {
            allowed.add("RESUME_SEASON");
            allowed.add("CANCEL_SEASON");
        }
        if (playable != null && lifecycle.status() != LeaguePersistenceState.SeasonStatus.PAUSED) {
            allowed.addAll(playable.allowedCommands());
        }
        return new LeagueApiV1Dtos.SeasonView(aggregate.leagueId(), aggregate.seasonId(),
                lifecycle.status().name(), lifecycle.lifecycleRevision(),
                aggregate.revision(), aggregate.seasonMode().name(),
                aggregate.managedTeamCode(), Long.toString(aggregate.seasonRootSeed()),
                aggregate.schedule().scheduleIdentity(),
                aggregate.frozenSnapshot().snapshotIdentity(),
                aggregate.productDecisionHash(),
                aggregate.frozenSnapshot().productionRuntimeIdentity(), currentRound,
                counters(fixtureRows), standingsRows(aggregate), playable,
                List.copyOf(allowed), lifecycle.updatedAt());
    }

    LeagueApiV1Dtos.StandingsResponse standings(String leagueId, String seasonId) {
        LeagueSeasonAggregate season = requireSeason(leagueId, seasonId);
        return new LeagueApiV1Dtos.StandingsResponse(
                LeagueApiV1Dtos.STANDINGS_SCHEMA, leagueId, seasonId,
                season.revision(), LeagueStandings.STANDINGS_POLICY_ID,
                standingsRows(season));
    }

    LeagueApiV1Dtos.FixturesResponse fixtures(String leagueId, String seasonId) {
        LeagueSeasonAggregate season = requireSeason(leagueId, seasonId);
        LeagueSeasonApplicationService.SeasonView lifecycle = seasons.view(seasonId);
        return new LeagueApiV1Dtos.FixturesResponse(LeagueApiV1Dtos.FIXTURES_SCHEMA,
                leagueId, seasonId, lifecycle.lifecycleRevision(), season.revision(),
                fixtureRows(seasonId).stream().map(row -> fixture(season, row)).toList());
    }

    LeagueApiV1Dtos.FixtureView fixture(
            String leagueId,
            String seasonId,
            String fixtureId
    ) {
        LeagueSeasonAggregate season = requireSeason(leagueId, seasonId);
        FixtureRow row = fixtureRows(seasonId).stream()
                .filter(value -> value.fixtureId().equals(fixtureId))
                .findFirst().orElseThrow(() -> new Missing("LEAGUE_FIXTURE_NOT_FOUND"));
        return fixture(season, row);
    }

    LeagueApiV1Dtos.JobView job(String leagueId, String seasonId, String jobId) {
        requireSeason(leagueId, seasonId);
        List<LeagueApiV1Dtos.JobView> rows = store.jdbc().query("""
                SELECT job_id, fixture_id, lifecycle_status, revision, attempt_number,
                       failure_class, failure_code, updated_at
                FROM league_job WHERE season_id = ? AND job_id = ?
                """, (result, row) -> job(result), seasonId, jobId);
        if (rows.isEmpty()) throw new Missing("LEAGUE_JOB_NOT_FOUND");
        return rows.getFirst();
    }

    List<LeagueApiV1Dtos.JobView> currentRoundJobs(
            String leagueId,
            String seasonId,
            int roundNumber
    ) {
        requireSeason(leagueId, seasonId);
        return store.jdbc().query("""
                SELECT j.job_id, j.fixture_id, j.lifecycle_status, j.revision,
                       j.attempt_number, j.failure_class, j.failure_code, j.updated_at
                FROM league_job j JOIN league_fixture f
                  ON f.season_id = j.season_id AND f.fixture_id = j.fixture_id
                WHERE j.season_id = ? AND f.round_number = ?
                ORDER BY j.fixture_id
                """, (result, row) -> job(result), seasonId, roundNumber);
    }

    LeagueApiV1Dtos.PlayerSeriesView playerSeries(
            String leagueId,
            String seasonId,
            String fixtureId
    ) {
        LeagueFixture fixture = requireFixture(leagueId, seasonId, fixtureId);
        if (fixture.executionMode() != LeagueFixtureExecutionMode.PLAYER_CONTROLLED) {
            throw new Invalid("PLAYER_SERIES_REQUIRES_PLAYER_FIXTURE");
        }
        LeaguePlayerSeriesBindingPort.State state = bindings.findByFixture(
                seasonId, fixtureId).orElseThrow(() ->
                new Missing("LEAGUE_PLAYER_SERIES_NOT_FOUND"));
        String receipt = state.completionReceipt() == null ? null
                : state.completionReceipt().canonicalFixtureReceiptHash();
        return new LeagueApiV1Dtos.PlayerSeriesView(leagueId, seasonId, fixtureId,
                state.binding().bindingHash(), state.revision(), state.status().name(),
                state.binding().boundSeriesId(), receipt, playerAllowed(state));
    }

    LeagueApiV1Dtos.CompletionStatusView completionStatus(
            String leagueId,
            String seasonId,
            String fixtureId
    ) {
        LeagueSeasonAggregate season = requireSeason(leagueId, seasonId);
        FixtureRow fixture = fixtureRows(seasonId).stream()
                .filter(value -> value.fixtureId().equals(fixtureId)).findFirst()
                .orElseThrow(() -> new Missing("LEAGUE_FIXTURE_NOT_FOUND"));
        Optional<LeaguePlayerSeriesBindingPort.State> binding = bindings.findByFixture(
                seasonId, fixtureId);
        String receipt = fixture.completionReceiptHash();
        List<String> outbox = receipt == null ? List.of() : store.jdbc().query("""
                SELECT lifecycle_status FROM league_outbox WHERE receipt_hash = ?
                """, (result, row) -> result.getString(1), receipt);
        Integer applied = receipt == null ? 0 : store.jdbc().queryForObject("""
                SELECT COUNT(*) FROM league_standings_application WHERE receipt_hash = ?
                """, Integer.class, receipt);
        LinkedHashSet<String> allowed = new LinkedHashSet<>();
        allowed.add("VIEW_FIXTURE");
        if (binding.isPresent()) allowed.addAll(playerAllowed(binding.get()));
        return new LeagueApiV1Dtos.CompletionStatusView(leagueId, seasonId, fixtureId,
                fixture.status(), binding.map(value -> value.status().name()).orElse(null),
                receipt, outbox.isEmpty() ? "NOT_CREATED" : outbox.getFirst(),
                applied != null && applied == 1, season.revision(), List.copyOf(allowed));
    }

    LeagueSeasonAggregate requireSeason(String leagueId, String seasonId) {
        LeagueSeasonAggregate season = store.findSeason(seasonId)
                .orElseThrow(() -> new Missing("LEAGUE_SEASON_NOT_FOUND"));
        if (!season.leagueId().equals(leagueId)) {
            throw new Missing("LEAGUE_SEASON_NOT_FOUND");
        }
        return season;
    }

    LeagueFixture requireFixture(String leagueId, String seasonId, String fixtureId) {
        LeagueSeasonAggregate season = requireSeason(leagueId, seasonId);
        return season.schedule().fixtures().stream()
                .filter(value -> value.fixtureId().equals(fixtureId)).findFirst()
                .orElseThrow(() -> new Missing("LEAGUE_FIXTURE_NOT_FOUND"));
    }

    private LeagueApiV1Dtos.FixtureView fixture(
            LeagueSeasonAggregate season,
            FixtureRow row
    ) {
        LeagueFixture fixture = season.schedule().fixture(row.fixtureId());
        Optional<LeaguePlayerSeriesBindingPort.State> binding = bindings.findByFixture(
                season.seasonId(), fixture.fixtureId());
        ArrayList<String> allowed = new ArrayList<>();
        allowed.add("VIEW_FIXTURE");
        if (fixture.executionMode() == LeagueFixtureExecutionMode.PLAYER_CONTROLLED
                && !terminal(row.status()) && !"BLOCKED".equals(row.status())) {
            if (binding.isEmpty()) allowed.add("START_PLAYER_SERIES");
            else allowed.addAll(playerAllowed(binding.get()));
        }
        return new LeagueApiV1Dtos.FixtureView(fixture.fixtureId(),
                fixture.roundNumber(), row.status(), row.revision(),
                fixture.executionMode().name(), fixture.firstTeamCode(),
                fixture.secondTeamCode(), fixture.game1BlueTeamCode(),
                fixture.game1RedTeamCode(), fixture.seriesFormat().name(),
                Long.toString(fixture.fixtureRootSeed()), fixture.boundSeriesId(),
                binding.map(value -> value.binding().bindingHash()).orElse(null),
                binding.map(value -> value.status().name()).orElse(null),
                completionStatus(row), row.jobId(), row.jobStatus(), List.copyOf(allowed));
    }

    private List<String> playerAllowed(LeaguePlayerSeriesBindingPort.State state) {
        SeriesStatus childStatus = null;
        if (state.status() == LeaguePlayerSeriesBindingPort.Status.ACTIVE) {
            childStatus = playerSeries.inspect(state.binding()).status();
        }
        return playerAllowed(state.status(), childStatus);
    }

    static List<String> playerAllowed(
            LeaguePlayerSeriesBindingPort.Status bindingStatus,
            SeriesStatus childStatus
    ) {
        if (bindingStatus == LeaguePlayerSeriesBindingPort.Status.CREATED) {
            return List.of("RESUME_PLAYER_SERIES");
        }
        if (bindingStatus == LeaguePlayerSeriesBindingPort.Status.ACTIVE) {
            return childStatus == SeriesStatus.COMPLETED
                    ? List.of("RECONCILE_PLAYER_SERIES_COMPLETION")
                    : List.of("RESUME_PLAYER_SERIES");
        }
        if (bindingStatus == LeaguePlayerSeriesBindingPort.Status
                .COMPLETION_PENDING_VERIFICATION) {
            return List.of("RECONCILE_PLAYER_SERIES_COMPLETION");
        }
        return List.of();
    }

    private static String completionStatus(FixtureRow row) {
        if ("COMPLETED".equals(row.status())) return "APPLIED";
        if (row.completionReceiptHash() != null) return "PENDING_RECONCILIATION";
        return "NOT_CREATED";
    }

    private List<LeagueApiV1Dtos.StandingRow> standingsRows(
            LeagueSeasonAggregate season
    ) {
        return season.ranking().teams().stream().map(value -> {
            LeagueStanding row = value.standing();
            return new LeagueApiV1Dtos.StandingRow(value.position(), row.teamCode(),
                    row.seriesWins(), row.seriesLosses(), row.gameWins(), row.gameLosses(),
                    row.gameDifferential(), value.deterministicDrawHash());
        }).toList();
    }

    private static LeagueApiV1Dtos.FixtureCounters counters(List<FixtureRow> rows) {
        int completed = 0, inProgress = 0, waiting = 0, blocked = 0, cancelled = 0;
        for (FixtureRow row : rows) {
            switch (row.status()) {
                case "COMPLETED" -> completed++;
                case "QUEUED", "LEASED", "RUNNING", "PLAYER_SERIES_RESERVED",
                        "COMPLETION_PENDING_VERIFICATION" -> inProgress++;
                case "BLOCKED" -> blocked++;
                case "CANCELLED" -> cancelled++;
                default -> waiting++;
            }
        }
        return new LeagueApiV1Dtos.FixtureCounters(rows.size(), completed, inProgress,
                waiting, blocked, cancelled);
    }

    private List<FixtureRow> fixtureRows(String seasonId) {
        return store.jdbc().query("""
                SELECT f.fixture_id, f.round_number, f.lifecycle_status, f.revision,
                       f.completion_receipt_hash, j.job_id, j.lifecycle_status
                FROM league_fixture f LEFT JOIN league_job j
                  ON j.season_id = f.season_id AND j.fixture_id = f.fixture_id
                WHERE f.season_id = ? ORDER BY f.round_number, f.fixture_id
                """, (result, row) -> new FixtureRow(result.getString(1),
                result.getInt(2), result.getString(3), result.getLong(4),
                result.getString(5), result.getString(6), result.getString(7)), seasonId);
    }

    private static LeagueApiV1Dtos.JobView job(ResultSet result) throws SQLException {
        String status = result.getString(3);
        String failureClass = result.getString(6);
        if (!"TRANSIENT".equals(failureClass) && !"DETERMINISTIC".equals(failureClass)) {
            failureClass = null;
        }
        String failureCode = result.getString(7);
        if (failureCode != null && !failureCode.matches("[A-Z][A-Z0-9_]{2,159}")) {
            failureCode = "JOB_EXECUTION_FAILED";
        }
        return new LeagueApiV1Dtos.JobView(result.getString(1), result.getString(2),
                status, result.getLong(4), result.getInt(5), failureClass,
                failureCode, "RETRY_PENDING".equals(status)
                || "QUEUED".equals(status), result.getObject(8, OffsetDateTime.class));
    }

    private static boolean terminal(String status) {
        return "COMPLETED".equals(status) || "CANCELLED".equals(status);
    }

    private record FixtureRow(
            String fixtureId, int roundNumber, String status, long revision,
            String completionReceiptHash, String jobId, String jobStatus
    ) {}

    static final class Missing extends RuntimeException {
        Missing(String code) { super(code); }
    }

    static final class Invalid extends RuntimeException {
        Invalid(String code) { super(code); }
    }
}
