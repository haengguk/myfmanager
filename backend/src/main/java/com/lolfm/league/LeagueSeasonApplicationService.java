package com.lolfm.league;

import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Service;

/** Internal lifecycle command/view boundary reserved for the later public API batch. */
@Service
public final class LeagueSeasonApplicationService {
    private final LeagueRelationalStore store;

    public LeagueSeasonApplicationService(LeagueRelationalStore store) {
        this.store = Objects.requireNonNull(store, "store");
    }

    public SeasonView createFrozen(LeagueSeasonAggregate season) {
        store.freeze(season);
        return view(season.seasonId());
    }

    public SeasonView ready(String seasonId, long expectedLifecycleRevision) {
        return transition(seasonId, expectedLifecycleRevision,
                List.of(LeaguePersistenceState.SeasonStatus.FROZEN),
                LeaguePersistenceState.SeasonStatus.READY);
    }

    public SeasonView pause(String seasonId, long expectedLifecycleRevision) {
        return transition(seasonId, expectedLifecycleRevision,
                List.of(LeaguePersistenceState.SeasonStatus.RUNNING,
                        LeaguePersistenceState.SeasonStatus.WAITING_FOR_PLAYER),
                LeaguePersistenceState.SeasonStatus.PAUSED);
    }

    public SeasonView resume(String seasonId, long expectedLifecycleRevision) {
        LeaguePersistenceState.SeasonStatus next = currentRoundWaitsForPlayer(seasonId)
                ? LeaguePersistenceState.SeasonStatus.WAITING_FOR_PLAYER
                : LeaguePersistenceState.SeasonStatus.RUNNING;
        return transition(seasonId, expectedLifecycleRevision,
                List.of(LeaguePersistenceState.SeasonStatus.PAUSED), next);
    }

    public SeasonView cancel(String seasonId, long expectedLifecycleRevision) {
        SeasonView cancelled = transition(seasonId, expectedLifecycleRevision,
                List.of(LeaguePersistenceState.SeasonStatus.READY,
                        LeaguePersistenceState.SeasonStatus.RUNNING,
                        LeaguePersistenceState.SeasonStatus.PAUSED,
                        LeaguePersistenceState.SeasonStatus.WAITING_FOR_PLAYER),
                LeaguePersistenceState.SeasonStatus.CANCELLED);
        store.jdbc().update("""
                UPDATE league_job SET lifecycle_status = 'CANCELLED',
                  revision = revision + 1, updated_at = ?
                WHERE season_id = ? AND lifecycle_status IN ('QUEUED', 'RETRY_PENDING')
                """, store.now(), seasonId);
        store.jdbc().update("""
                UPDATE league_fixture SET lifecycle_status = 'CANCELLED',
                  revision = revision + 1
                WHERE season_id = ? AND lifecycle_status IN ('SCHEDULED', 'QUEUED',
                  'RETRY_PENDING', 'AWAITING_PLAYER')
                """, seasonId);
        return cancelled;
    }

    public SeasonView view(String seasonId) {
        List<SeasonView> rows = store.jdbc().query("""
                SELECT season_id, lifecycle_status, lifecycle_revision, revision,
                       season_mode, managed_team_code, schedule_identity,
                       frozen_snapshot_hash, product_decision_hash
                FROM league_season WHERE season_id = ?
                """, (result, row) -> new SeasonView(result.getString(1),
                LeaguePersistenceState.SeasonStatus.valueOf(result.getString(2)),
                result.getLong(3), result.getLong(4),
                LeagueSeasonMode.valueOf(result.getString(5)), result.getString(6),
                result.getString(7), result.getString(8), result.getString(9)), seasonId);
        if (rows.isEmpty()) throw new IllegalStateException("LEAGUE_SEASON_NOT_FOUND");
        return rows.getFirst();
    }

    void markDispatching(String seasonId, boolean waitingForPlayer) {
        LeaguePersistenceState.SeasonStatus next = waitingForPlayer
                ? LeaguePersistenceState.SeasonStatus.WAITING_FOR_PLAYER
                : LeaguePersistenceState.SeasonStatus.RUNNING;
        int updated = store.jdbc().update("""
                UPDATE league_season SET lifecycle_status = ?,
                  lifecycle_revision = lifecycle_revision + 1, updated_at = ?
                WHERE season_id = ? AND lifecycle_status IN ('READY', 'RUNNING',
                  'WAITING_FOR_PLAYER')
                """, next.name(), store.now(), seasonId);
        if (updated != 1) {
            throw new IllegalStateException("LEAGUE_SEASON_NOT_DISPATCHABLE");
        }
    }

    void requireDispatchable(String seasonId) {
        LeaguePersistenceState.SeasonStatus status = view(seasonId).status();
        if (status != LeaguePersistenceState.SeasonStatus.READY
                && status != LeaguePersistenceState.SeasonStatus.RUNNING
                && status != LeaguePersistenceState.SeasonStatus.WAITING_FOR_PLAYER) {
            throw new IllegalStateException("LEAGUE_SEASON_NOT_DISPATCHABLE");
        }
    }

    private SeasonView transition(
            String seasonId,
            long expectedRevision,
            List<LeaguePersistenceState.SeasonStatus> expected,
            LeaguePersistenceState.SeasonStatus next
    ) {
        int updated = store.jdbc().update("""
                UPDATE league_season SET lifecycle_status = ?,
                  lifecycle_revision = lifecycle_revision + 1, updated_at = ?
                WHERE season_id = ? AND lifecycle_revision = ?
                  AND lifecycle_status IN (%s)
                """.formatted(expected.stream().map(value -> "'" + value.name() + "'")
                .collect(java.util.stream.Collectors.joining(","))),
                next.name(), store.now(), seasonId, expectedRevision);
        if (updated != 1) {
            throw new IllegalStateException("LEAGUE_SEASON_STALE_OR_ILLEGAL_TRANSITION");
        }
        return view(seasonId);
    }

    private boolean currentRoundWaitsForPlayer(String seasonId) {
        Integer waiting = store.jdbc().queryForObject("""
                SELECT COUNT(*) FROM league_fixture
                WHERE season_id = ?
                  AND round_number = (SELECT MIN(round_number) FROM league_fixture
                    WHERE season_id = ? AND lifecycle_status <> 'COMPLETED')
                  AND execution_mode = 'PLAYER_CONTROLLED'
                  AND lifecycle_status <> 'COMPLETED'
                """, Integer.class, seasonId, seasonId);
        Integer auto = store.jdbc().queryForObject("""
                SELECT COUNT(*) FROM league_fixture
                WHERE season_id = ?
                  AND round_number = (SELECT MIN(round_number) FROM league_fixture
                    WHERE season_id = ? AND lifecycle_status <> 'COMPLETED')
                  AND execution_mode = 'FULL_AUTO'
                  AND lifecycle_status <> 'COMPLETED'
                """, Integer.class, seasonId, seasonId);
        return waiting != null && waiting > 0 && auto != null && auto == 0;
    }

    public record SeasonView(
            String seasonId,
            LeaguePersistenceState.SeasonStatus status,
            long lifecycleRevision,
            long standingsRevision,
            LeagueSeasonMode seasonMode,
            String managedTeamCode,
            String scheduleIdentity,
            String frozenSnapshotIdentity,
            String productDecisionHash
    ) {}
}
