package com.lolfm.league;

import com.lolfm.application.SeriesStatus;
import com.lolfm.career.CareerApplicationService;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/** Scalar-only, observational projection from durable League state to Career. */
@Service
public final class LeagueCareerSeasonReadService
        implements CareerApplicationService.SeasonReadPort {
    private final LeagueRelationalStore store;

    LeagueCareerSeasonReadService(LeagueRelationalStore store) {
        this.store = store;
    }

    @Override
    public CareerApplicationService.LinkedSeason load(
            String leagueId,
            String seasonId
    ) {
        CareerApplicationService.SeasonReference reference =
                new CareerApplicationService.SeasonReference(leagueId, seasonId);
        CareerApplicationService.LinkedSeason linked = loadAll(List.of(reference))
                .get(reference);
        if (linked == null) throw new IllegalStateException("LEAGUE_SEASON_NOT_FOUND");
        return linked;
    }

    @Override
    public Map<CareerApplicationService.SeasonReference,
            CareerApplicationService.LinkedSeason> loadAll(
            List<CareerApplicationService.SeasonReference> references
    ) {
        if (references.isEmpty()) return Map.of();
        List<String> seasonIds = references.stream()
                .map(CareerApplicationService.SeasonReference::seasonId).distinct().toList();
        Map<String, SeasonRow> seasons = seasonRows(seasonIds);
        Map<String, List<CandidateRow>> candidates = candidateRows(seasonIds);
        LinkedHashMap<CareerApplicationService.SeasonReference,
                CareerApplicationService.LinkedSeason> result = new LinkedHashMap<>();
        for (CareerApplicationService.SeasonReference reference : references) {
            SeasonRow season = seasons.get(reference.seasonId());
            if (season == null || !reference.leagueId().equals(season.leagueId())
                    || !"ACTIVE".equals(season.leagueLifecycleStatus())) {
                throw new IllegalStateException("LEAGUE_SEASON_NOT_FOUND");
            }
            CareerApplicationService.ResumeState resume = resume(season,
                    candidates.getOrDefault(reference.seasonId(), List.of()));
            result.put(reference, new CareerApplicationService.LinkedSeason(
                    season.leagueId(), season.seasonId(), season.seasonMode().name(),
                    season.managedTeamCode(), season.rootSeed(),
                    season.frozenSnapshotIdentity(), season.productDecisionIdentity(),
                    resume));
        }
        return Map.copyOf(result);
    }

    private Map<String, SeasonRow> seasonRows(List<String> seasonIds) {
        String placeholders = placeholders(seasonIds.size());
        String sql = """
                WITH selected_season AS (
                  SELECT league_id, season_id, lifecycle_status, lifecycle_revision,
                         revision, season_mode, managed_team_code, season_root_seed,
                         frozen_snapshot_hash, product_decision_hash
                  FROM league_season WHERE season_id IN (%s)
                ), fixture_rollup AS (
                  SELECT f.season_id,
                         COALESCE(MIN(CASE WHEN f.lifecycle_status NOT IN
                           ('COMPLETED', 'CANCELLED') THEN f.round_number END),
                           MAX(f.round_number), 1) AS current_round
                  FROM league_fixture f JOIN selected_season s
                    ON s.season_id = f.season_id
                  GROUP BY f.season_id
                )
                SELECT s.league_id, s.season_id, s.lifecycle_status,
                       s.lifecycle_revision, s.revision, s.season_mode,
                       s.managed_team_code, s.season_root_seed,
                       s.frozen_snapshot_hash, s.product_decision_hash,
                       l.lifecycle_status, COALESCE(r.current_round, 1),
                       CASE WHEN EXISTS (
                         SELECT 1 FROM league_fixture f
                         WHERE f.season_id = s.season_id
                           AND f.round_number = COALESCE(r.current_round, 1)
                           AND f.execution_mode = 'FULL_AUTO'
                           AND f.lifecycle_status NOT IN
                             ('COMPLETED', 'CANCELLED', 'BLOCKED')
                       ) THEN TRUE ELSE FALSE END
                FROM selected_season s
                JOIN league_registry l ON l.league_id = s.league_id
                LEFT JOIN fixture_rollup r ON r.season_id = s.season_id
                """.formatted(placeholders);
        List<SeasonRow> rows = store.jdbc().query(sql,
                (result, ignored) -> season(result), seasonIds.toArray());
        LinkedHashMap<String, SeasonRow> indexed = new LinkedHashMap<>();
        for (SeasonRow row : rows) {
            if (indexed.put(row.seasonId(), row) != null) {
                throw new IllegalStateException("DUPLICATE_LEAGUE_SEASON");
            }
        }
        return indexed;
    }

    private Map<String, List<CandidateRow>> candidateRows(List<String> seasonIds) {
        List<LeaguePersistenceState.FixtureStatus> attention =
                LeagueCommandPolicy.attentionFixtureStatuses();
        String sql = """
                SELECT f.season_id, f.fixture_id, f.round_number, f.execution_mode,
                       f.lifecycle_status, f.bound_series_id,
                       b.binding_hash, b.lifecycle_status, b.revision,
                       c.series_id, c.series_status
                FROM league_fixture f
                LEFT JOIN league_player_binding b
                  ON b.season_id = f.season_id AND b.fixture_id = f.fixture_id
                LEFT JOIN league_player_series_checkpoint c
                  ON c.binding_hash = b.binding_hash
                WHERE f.season_id IN (%s)
                  AND (b.binding_hash IS NOT NULL OR f.lifecycle_status IN (%s))
                ORDER BY f.season_id, f.round_number, f.fixture_id
                """.formatted(placeholders(seasonIds.size()),
                placeholders(attention.size()));
        ArrayList<Object> arguments = new ArrayList<>(seasonIds);
        attention.stream().map(Enum::name).forEach(arguments::add);
        List<CandidateRow> rows = store.jdbc().query(sql,
                (result, ignored) -> candidate(result), arguments.toArray());
        LinkedHashMap<String, List<CandidateRow>> grouped = new LinkedHashMap<>();
        for (CandidateRow row : rows) {
            grouped.computeIfAbsent(row.seasonId(), ignored -> new ArrayList<>()).add(row);
        }
        return grouped;
    }

    private static CareerApplicationService.ResumeState resume(
            SeasonRow season,
            List<CandidateRow> candidates
    ) {
        if (season.lifecycleStatus() == LeaguePersistenceState.SeasonStatus.COMPLETED) {
            return resume("SEASON_COMPLETE", season, null,
                    LeagueCommandPolicy.seasonCommands(season.lifecycleStatus(),
                            season.currentRoundAutoAvailable(), List.of()));
        }
        CandidateRow attention = candidates.stream()
                .filter(CandidateRow::needsAttention).findFirst().orElse(null);
        if (season.lifecycleStatus() == LeaguePersistenceState.SeasonStatus.BLOCKED
                || season.lifecycleStatus() ==
                LeaguePersistenceState.SeasonStatus.CANCELLED
                || attention != null) {
            return resume("ATTENTION_REQUIRED", season, attention,
                    LeagueCommandPolicy.seasonCommands(season.lifecycleStatus(),
                            season.currentRoundAutoAvailable(), List.of()));
        }
        if (season.lifecycleStatus() == LeaguePersistenceState.SeasonStatus.PAUSED) {
            return resume("LEAGUE_DASHBOARD", season, null,
                    LeagueCommandPolicy.seasonCommands(season.lifecycleStatus(),
                            season.currentRoundAutoAvailable(), List.of()));
        }
        for (CandidateRow candidate : candidates) {
            if (candidate.roundNumber() != season.currentRound()
                    || candidate.executionMode()
                    != LeagueFixtureExecutionMode.PLAYER_CONTROLLED
                    || candidate.bindingStatus() == null) {
                continue;
            }
            candidate.requireSeriesIdentity();
            List<String> commands = LeagueCommandPolicy.playerSeriesCommands(
                    candidate.bindingStatus(), candidate.childStatus());
            if (commands.contains("RESUME_PLAYER_SERIES")
                    || commands.contains("RECONCILE_PLAYER_SERIES_COMPLETION")) {
                return resume("PLAYER_SERIES", season, candidate, commands);
            }
        }
        return resume("LEAGUE_DASHBOARD", season, null,
                LeagueCommandPolicy.seasonCommands(season.lifecycleStatus(),
                        season.currentRoundAutoAvailable(), List.of()));
    }

    private static CareerApplicationService.ResumeState resume(
            String kind,
            SeasonRow season,
            CandidateRow context,
            List<String> allowedCommands
    ) {
        return new CareerApplicationService.ResumeState(kind, season.leagueId(),
                season.seasonId(), context == null ? null : context.fixtureId(),
                context == null || context.bindingStatus() == null ? null
                        : context.boundSeriesId(),
                season.lifecycleStatus().name(), season.currentRound(),
                season.lifecycleRevision(), season.standingsRevision(), allowedCommands);
    }

    private static SeasonRow season(ResultSet result) throws SQLException {
        return new SeasonRow(result.getString(1), result.getString(2),
                LeaguePersistenceState.SeasonStatus.valueOf(result.getString(3)),
                result.getLong(4), result.getLong(5),
                LeagueSeasonMode.valueOf(result.getString(6)), result.getString(7),
                result.getLong(8), result.getString(9), result.getString(10),
                result.getString(11), result.getInt(12), result.getBoolean(13));
    }

    private static CandidateRow candidate(ResultSet result) throws SQLException {
        String binding = result.getString(7);
        return new CandidateRow(result.getString(1), result.getString(2),
                result.getInt(3), LeagueFixtureExecutionMode.valueOf(result.getString(4)),
                LeaguePersistenceState.FixtureStatus.valueOf(result.getString(5)),
                result.getString(6), binding,
                binding == null ? null : LeaguePlayerSeriesBindingPort.Status.valueOf(
                        result.getString(8)), result.getLong(9), result.getString(10),
                result.getString(11) == null ? null
                        : SeriesStatus.valueOf(result.getString(11)));
    }

    private static String placeholders(int size) {
        if (size < 1) throw new IllegalArgumentException("placeholder size");
        return String.join(",", java.util.Collections.nCopies(size, "?"));
    }

    private record SeasonRow(
            String leagueId,
            String seasonId,
            LeaguePersistenceState.SeasonStatus lifecycleStatus,
            long lifecycleRevision,
            long standingsRevision,
            LeagueSeasonMode seasonMode,
            String managedTeamCode,
            long rootSeed,
            String frozenSnapshotIdentity,
            String productDecisionIdentity,
            String leagueLifecycleStatus,
            int currentRound,
            boolean currentRoundAutoAvailable
    ) {}

    private record CandidateRow(
            String seasonId,
            String fixtureId,
            int roundNumber,
            LeagueFixtureExecutionMode executionMode,
            LeaguePersistenceState.FixtureStatus fixtureStatus,
            String boundSeriesId,
            String bindingHash,
            LeaguePlayerSeriesBindingPort.Status bindingStatus,
            long bindingRevision,
            String checkpointSeriesId,
            SeriesStatus childStatus
    ) {
        boolean needsAttention() {
            return LeagueCommandPolicy.needsAttention(fixtureStatus)
                    || LeagueCommandPolicy.needsAttention(bindingStatus);
        }

        void requireSeriesIdentity() {
            if (bindingHash == null || boundSeriesId == null
                    || checkpointSeriesId != null
                    && !boundSeriesId.equals(checkpointSeriesId)) {
                throw new IllegalStateException("PLAYER_SERIES_IDENTITY_MISMATCH");
            }
        }
    }
}
