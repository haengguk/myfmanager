package com.lolfm.league;

import com.lolfm.simulator.SimulationInstrumentation;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/** Bounded local worker coordinator with database leases, fencing and deterministic retry. */
@Service
public final class LeagueJobCoordinator implements LeagueSimulationApplicationPort {
    private final LeagueRelationalStore store;
    private final LeagueAutomatedSeriesRunner runner;
    private final LeagueV1OperationalConfiguration limits;
    private final LeagueSeasonApplicationService seasons;
    private final LeagueProcessIncarnation processIncarnation;
    private final Clock clock;

    public LeagueJobCoordinator(
            LeagueRelationalStore store,
            LeagueAutomatedSeriesRunner runner,
            Clock clock,
            LeagueSeasonApplicationService seasons
    ) {
        this(store, runner, clock, seasons, new LeagueProcessIncarnation());
    }

    @Autowired
    public LeagueJobCoordinator(
            LeagueRelationalStore store,
            LeagueAutomatedSeriesRunner runner,
            Clock clock,
            LeagueSeasonApplicationService seasons,
            LeagueProcessIncarnation processIncarnation
    ) {
        this.store = Objects.requireNonNull(store, "store");
        this.runner = Objects.requireNonNull(runner, "runner");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.seasons = Objects.requireNonNull(seasons, "seasons");
        this.processIncarnation = Objects.requireNonNull(
                processIncarnation, "processIncarnation");
        this.limits = LeagueV1OperationalConfiguration.defaults();
    }

    @Override
    public DispatchResult dispatchFullAutoFixture(String seasonId, String fixtureId) {
        seasons.requireDispatchable(seasonId);
        LeagueSeasonAggregate season = store.loadSeason(seasonId);
        LeagueFixture fixture = season.schedule().fixture(fixtureId);
        if (fixture.executionMode() != LeagueFixtureExecutionMode.FULL_AUTO) {
            throw new IllegalArgumentException("PLAYER_FIXTURE_EXCLUDED_FROM_AUTO_DISPATCH");
        }
        String frozenHash = frozenInputHash(season, fixture);
        String jobId = "job_" + LeagueIdentity.sha256(
                "jobSchema=AI_LEAGUE_FULL_AUTO_JOB_V1\n"
                        + "seasonId=" + seasonId + '\n'
                        + "fixtureId=" + fixtureId + '\n');
        return store.transactions().execute(ignored -> {
            seasons.lockSeason(seasonId);
            seasons.requireDispatchable(seasonId);
            lockFixture(seasonId, fixtureId);
            Optional<JobView> existing = findJob(seasonId, fixtureId);
            if (existing.isPresent()) {
                if (!existing.get().jobId().equals(jobId)
                        || !existing.get().frozenInputHash().equals(frozenHash)) {
                    throw new IllegalStateException("LEAGUE_JOB_FROZEN_INPUT_CONFLICT");
                }
                return new DispatchResult(jobId, true);
            }
            OffsetDateTime now = now();
            store.jdbc().update("""
                    INSERT INTO league_job(
                      job_id, season_id, fixture_id, lifecycle_status, revision,
                      attempt_number, fencing_number, frozen_input_hash,
                      created_at, updated_at)
                    VALUES (?, ?, ?, 'QUEUED', 0, 0, 0, ?, ?, ?)
                    """, jobId, seasonId, fixtureId, frozenHash, now, now);
            store.jdbc().update("""
                    UPDATE league_fixture SET lifecycle_status = 'QUEUED',
                      revision = revision + 1 WHERE season_id = ? AND fixture_id = ?
                    """, seasonId, fixtureId);
            seasons.markDispatching(seasonId, false);
            return new DispatchResult(jobId, false);
        });
    }

    @Override
    public DispatchBatch dispatchRound(String seasonId, int roundNumber) {
        LeagueSeasonAggregate season = store.loadSeason(seasonId);
        LeagueRound round = season.schedule().rounds().stream()
                .filter(value -> value.roundNumber() == roundNumber)
                .findFirst().orElseThrow(() -> new IllegalArgumentException(
                        "LEAGUE_ROUND_NOT_FOUND"));
        int queued = 0;
        int replayed = 0;
        int excluded = 0;
        for (LeagueFixture fixture : round.fixtures().stream()
                .sorted(java.util.Comparator.comparing(LeagueFixture::fixtureId)).toList()) {
            if (fixture.executionMode() == LeagueFixtureExecutionMode.PLAYER_CONTROLLED) {
                excluded++;
                continue;
            }
            DispatchResult result = dispatchFullAutoFixture(seasonId, fixture.fixtureId());
            if (result.replayed()) replayed++; else queued++;
        }
        seasons.markDispatching(seasonId, excluded > 0);
        return new DispatchBatch(queued, replayed, excluded);
    }

    @Override
    public Optional<Lease> leaseNext(String ownerId) {
        requireOwner(ownerId);
        return store.transactions().execute(ignored -> {
            store.lockGlobalFixtureLeases();
            store.registerProcessIncarnation(processIncarnation.value());
            int active = store.jdbc().queryForObject("""
                    SELECT COUNT(*) FROM league_job
                    WHERE lifecycle_status IN ('LEASED', 'RUNNING')
                      AND lease_expires_at > ?
                    """, Integer.class, now());
            if (active >= limits.hardMaxParallelFixtures()) return Optional.empty();
            List<JobView> jobs = store.jdbc().query("""
                    SELECT j.job_id, j.season_id, j.fixture_id, j.lifecycle_status,
                           j.revision, j.attempt_number, j.fencing_number, j.lease_owner,
                           j.lease_expires_at, j.frozen_input_hash, j.failure_class,
                           j.failure_code
                    FROM league_job j
                    JOIN league_season s ON s.season_id = j.season_id
                    WHERE j.lifecycle_status IN ('QUEUED', 'RETRY_PENDING')
                      AND j.attempt_number < ?
                      AND s.lifecycle_status IN ('READY', 'RUNNING', 'WAITING_FOR_PLAYER')
                    ORDER BY j.created_at, j.fixture_id LIMIT 1 FOR UPDATE
                    """, (result, row) -> jobView(result), limits.transientTotalAttempts());
            if (jobs.isEmpty()) return Optional.empty();
            JobView job = jobs.getFirst();
            int attempt = job.attemptNumber() + 1;
            long fence = job.fencingNumber() + 1;
            OffsetDateTime leasedAt = now();
            OffsetDateTime expires = leasedAt.plus(limits.fixtureLease());
            String token = LeagueIdentity.sha256(
                    "leaseSchema=AI_LEAGUE_FIXTURE_LEASE_V1\n"
                            + "jobId=" + job.jobId() + '\n'
                            + "fencingNumber=" + fence + '\n'
                            + "ownerId=" + ownerId + '\n'
                            + "processIncarnationId=" + processIncarnation.value() + '\n'
                            + "leasedAt=" + leasedAt + '\n');
            int updated = store.jdbc().update("""
                    UPDATE league_job SET lifecycle_status = 'LEASED', revision = revision + 1,
                      attempt_number = ?, fencing_number = ?, lease_token = ?, lease_owner = ?,
                      lease_expires_at = ?, last_heartbeat_at = ?, lease_incarnation_id = ?,
                      failure_class = NULL, failure_code = NULL, updated_at = ?
                    WHERE job_id = ? AND revision = ?
                      AND lifecycle_status IN ('QUEUED', 'RETRY_PENDING')
                    """, attempt, fence, token, ownerId, expires, leasedAt,
                    processIncarnation.value(), leasedAt, job.jobId(), job.revision());
            if (updated != 1) return Optional.empty();
            store.jdbc().update("""
                    INSERT INTO league_job_attempt(
                      season_id, fixture_id, attempt_number, fencing_number,
                      lifecycle_status, owner_id, started_at)
                    VALUES (?, ?, ?, ?, 'LEASED', ?, ?)
                    """, job.seasonId(), job.fixtureId(), attempt, fence, ownerId, leasedAt);
            store.jdbc().update("""
                    UPDATE league_fixture SET lifecycle_status = 'LEASED',
                      revision = revision + 1 WHERE season_id = ? AND fixture_id = ?
                    """, job.seasonId(), job.fixtureId());
            return Optional.of(new Lease(job.jobId(), job.seasonId(), job.fixtureId(),
                    token, fence, attempt, job.frozenInputHash(), expires,
                    processIncarnation.value()));
        });
    }

    @Override
    public boolean heartbeat(Lease lease) {
        Objects.requireNonNull(lease, "lease");
        OffsetDateTime heartbeat = now();
        return store.jdbc().update("""
                UPDATE league_job SET last_heartbeat_at = ?, lease_expires_at = ?,
                  updated_at = ?, revision = revision + 1
                WHERE job_id = ? AND lease_token = ? AND fencing_number = ?
                  AND lease_incarnation_id = ?
                  AND lifecycle_status IN ('LEASED', 'RUNNING')
                  AND lease_expires_at > ?
                """, heartbeat, heartbeat.plus(limits.fixtureLease()), heartbeat,
                lease.jobId(), lease.leaseToken(), lease.fencingNumber(),
                lease.processIncarnationId(), heartbeat) == 1;
    }

    @Override
    public ExecutionResult execute(
            Lease lease,
            SimulationInstrumentation instrumentation
    ) {
        Objects.requireNonNull(lease, "lease");
        Objects.requireNonNull(instrumentation, "instrumentation");
        if (!markRunning(lease)) {
            return stale(lease);
        }
        ScheduledExecutorService heartbeat = Executors.newSingleThreadScheduledExecutor();
        heartbeat.scheduleAtFixedRate(() -> heartbeat(lease),
                limits.heartbeatInterval().toSeconds(),
                limits.heartbeatInterval().toSeconds(), TimeUnit.SECONDS);
        LeagueAutomatedSeriesRunResult result;
        try {
            LeagueSeasonAggregate season = store.loadSeason(lease.seasonId());
            LeagueFixture fixture = season.schedule().fixture(lease.fixtureId());
            if (!frozenInputHash(season, fixture).equals(lease.frozenInputHash())) {
                return finishFailure(lease,
                        LeaguePersistenceState.FailureClass.DETERMINISTIC,
                        "FROZEN_JOB_INPUT_MISMATCH");
            }
            result = runner.run(new LeagueAutomatedSeriesRunnerInput(
                    season, fixture, season.productDecisionHash()), instrumentation);
        } catch (RuntimeException error) {
            LeagueJobFailureClassifier.Failure failure =
                    LeagueJobFailureClassifier.classify(error);
            return finishFailure(lease, failure.failureClass(), failure.failureCode());
        } finally {
            heartbeat.shutdownNow();
        }
        if (result.status() == LeagueAutomatedSeriesRunResult.Status.COMPLETED) {
            return finishCompleted(lease, result);
        }
        LeagueJobFailureClassifier.Failure failure =
                LeagueJobFailureClassifier.deterministicResult(result.failureReason());
        return finishFailure(lease, failure.failureClass(), failure.failureCode());
    }

    @Override
    public List<ExecutionResult> executeQueued(
            String ownerId,
            int parallelism,
            SimulationInstrumentation instrumentation
    ) {
        requireOwner(ownerId);
        if (parallelism < 1 || parallelism > limits.hardMaxParallelFixtures()) {
            throw new IllegalArgumentException("parallelism must be between 1 and "
                    + limits.hardMaxParallelFixtures());
        }
        ArrayList<Lease> leases = new ArrayList<>();
        while (leases.size() < parallelism) {
            Optional<Lease> next = leaseNext(ownerId);
            if (next.isEmpty()) break;
            leases.add(next.get());
        }
        if (leases.isEmpty()) return List.of();
        var executor = Executors.newFixedThreadPool(parallelism);
        try {
            List<Future<ExecutionResult>> futures = leases.stream()
                    .map(lease -> executor.submit(() -> execute(lease, instrumentation)))
                    .toList();
            ArrayList<ExecutionResult> results = new ArrayList<>();
            for (Future<ExecutionResult> future : futures) {
                try {
                    results.add(future.get());
                } catch (Exception error) {
                    throw new IllegalStateException("LEAGUE_WORKER_EXECUTION_FAILED", error);
                }
            }
            return List.copyOf(results);
        } finally {
            executor.shutdownNow();
        }
    }

    @Override
    public RecoveryResult recover() {
        return recoverLeases(false);
    }

    @Override
    public RecoveryResult recoverStartup() {
        return recoverLeases(true);
    }

    private RecoveryResult recoverLeases(boolean startup) {
        OffsetDateTime recoveryTime = now();
        int[] auto = store.transactions().execute(ignored -> {
            if (startup) {
                store.registerProcessIncarnation(processIncarnation.value());
            }
            String predicate = startup
                    ? "(lease_incarnation_id IS NULL OR lease_incarnation_id <> ? "
                            + "OR lease_expires_at <= ?)"
                    : "lease_expires_at <= ?";
            Object[] parameters = startup
                    ? new Object[]{processIncarnation.value(), recoveryTime}
                    : new Object[]{recoveryTime};
            List<JobView> expired = store.jdbc().query("""
                    SELECT job_id, season_id, fixture_id, lifecycle_status, revision,
                           attempt_number, fencing_number, lease_owner, lease_expires_at,
                           frozen_input_hash, failure_class, failure_code
                    FROM league_job
                    WHERE lifecycle_status IN ('LEASED', 'RUNNING')
                      AND %s FOR UPDATE
                    """.formatted(predicate), (result, row) -> jobView(result), parameters);
            int retried = 0;
            int blocked = 0;
            for (JobView job : expired) {
                boolean retry = job.attemptNumber() < limits.transientTotalAttempts();
                String status = retry ? "RETRY_PENDING" : "BLOCKED";
                store.jdbc().update("""
                        UPDATE league_job SET lifecycle_status = ?, revision = revision + 1,
                          lease_token = NULL, lease_owner = NULL, lease_expires_at = NULL,
                          last_heartbeat_at = NULL, lease_incarnation_id = NULL,
                          failure_class = 'TRANSIENT',
                          failure_code = 'PROCESS_LOST_OR_LEASE_EXPIRED', updated_at = ?
                        WHERE job_id = ? AND revision = ?
                        """, status, recoveryTime, job.jobId(), job.revision());
                finishAttempt(job.seasonId(), job.fixtureId(), job.attemptNumber(),
                        status, "TRANSIENT", "PROCESS_LOST_OR_LEASE_EXPIRED");
                updateFixtureFailure(job.seasonId(), job.fixtureId(), status,
                        "PROCESS_LOST_OR_LEASE_EXPIRED");
                if (retry) retried++; else blocked++;
            }
            return new int[]{retried, blocked};
        });
        int player = store.jdbc().update("""
                UPDATE league_player_binding b SET lifecycle_status =
                  'PLAYER_SERIES_RESTART_REQUIRED', revision = revision + 1,
                  reason = 'PROCESS_LOST_BEFORE_SERIES_CHECKPOINT', updated_at = ?
                WHERE b.lifecycle_status = 'CREATED'
                  AND NOT EXISTS (SELECT 1 FROM league_player_series_checkpoint c
                                  WHERE c.binding_hash = b.binding_hash)
                """, recoveryTime);
        store.jdbc().update("""
                UPDATE league_fixture f SET lifecycle_status =
                  'PLAYER_SERIES_RESTART_REQUIRED', revision = revision + 1,
                  failure_code = 'PROCESS_LOST_BEFORE_SERIES_CHECKPOINT'
                WHERE EXISTS (SELECT 1 FROM league_player_binding b
                              WHERE b.season_id = f.season_id
                                AND b.fixture_id = f.fixture_id
                                AND b.lifecycle_status =
                                  'PLAYER_SERIES_RESTART_REQUIRED')
                  AND f.lifecycle_status = 'PLAYER_SERIES_RESERVED'
                """);
        int delivered = store.drainOutbox(10_000);
        return new RecoveryResult(auto[0], auto[1], player, delivered);
    }

    @Override
    public int purgeExpiredAttemptLogs() {
        OffsetDateTime cutoff = now().minus(limits.jobAttemptLogRetention());
        return store.jdbc().update("""
                DELETE FROM league_job_attempt
                WHERE finished_at IS NOT NULL AND finished_at < ?
                """, cutoff);
    }

    @Override
    public Optional<JobView> findJob(String seasonId, String fixtureId) {
        List<JobView> rows = store.jdbc().query("""
                SELECT job_id, season_id, fixture_id, lifecycle_status, revision,
                       attempt_number, fencing_number, lease_owner, lease_expires_at,
                       frozen_input_hash, failure_class, failure_code
                FROM league_job WHERE season_id = ? AND fixture_id = ?
                """, (result, row) -> jobView(result), seasonId, fixtureId);
        return rows.stream().findFirst();
    }

    private boolean markRunning(Lease lease) {
        OffsetDateTime now = now();
        return store.transactions().execute(ignored -> {
            int updated = store.jdbc().update("""
                    UPDATE league_job SET lifecycle_status = 'RUNNING',
                      revision = revision + 1, updated_at = ?
                    WHERE job_id = ? AND lease_token = ? AND fencing_number = ?
                      AND attempt_number = ? AND lease_incarnation_id = ?
                      AND lifecycle_status = 'LEASED'
                      AND lease_expires_at > ?
                    """, now, lease.jobId(), lease.leaseToken(), lease.fencingNumber(),
                    lease.attemptNumber(), lease.processIncarnationId(), now);
            if (updated == 1) {
                store.jdbc().update("""
                        UPDATE league_job_attempt SET lifecycle_status = 'RUNNING'
                        WHERE season_id = ? AND fixture_id = ? AND attempt_number = ?
                        """, lease.seasonId(), lease.fixtureId(), lease.attemptNumber());
                store.jdbc().update("""
                        UPDATE league_fixture SET lifecycle_status = 'RUNNING',
                          revision = revision + 1 WHERE season_id = ? AND fixture_id = ?
                        """, lease.seasonId(), lease.fixtureId());
            }
            return updated == 1;
        });
    }

    private ExecutionResult finishCompleted(
            Lease lease,
            LeagueAutomatedSeriesRunResult result
    ) {
        return store.transactions().execute(ignored -> {
            OffsetDateTime now = now();
            int updated = store.jdbc().update("""
                    UPDATE league_job SET lifecycle_status = 'COMPLETED',
                      revision = revision + 1, lease_token = NULL, lease_owner = NULL,
                      lease_expires_at = NULL, last_heartbeat_at = NULL,
                      lease_incarnation_id = NULL, updated_at = ?
                    WHERE job_id = ? AND lease_token = ? AND fencing_number = ?
                      AND lease_incarnation_id = ?
                      AND attempt_number = ? AND lifecycle_status = 'RUNNING'
                      AND lease_expires_at > ?
                    """, now, lease.jobId(), lease.leaseToken(), lease.fencingNumber(),
                    lease.processIncarnationId(), lease.attemptNumber(), now);
            if (updated != 1) return stale(lease);
            store.storeVerifiedCompletion(result.unifiedReceipt(),
                    result.verifiedCompletion());
            finishAttempt(lease.seasonId(), lease.fixtureId(), lease.attemptNumber(),
                    "COMPLETED", null, null);
            return new ExecutionResult(lease.jobId(), Status.COMPLETED,
                    lease.attemptNumber(), result.unifiedReceipt()
                    .canonicalFixtureReceiptHash(), null);
        });
    }

    private ExecutionResult finishFailure(
            Lease lease,
            LeaguePersistenceState.FailureClass failureClass,
            String failureCode
    ) {
        return store.transactions().execute(ignored -> {
            boolean retry = failureClass == LeaguePersistenceState.FailureClass.TRANSIENT
                    && lease.attemptNumber() < limits.transientTotalAttempts();
            String next = retry ? "RETRY_PENDING" : "BLOCKED";
            OffsetDateTime now = now();
            int updated = store.jdbc().update("""
                    UPDATE league_job SET lifecycle_status = ?, revision = revision + 1,
                      lease_token = NULL, lease_owner = NULL, lease_expires_at = NULL,
                      last_heartbeat_at = NULL, lease_incarnation_id = NULL,
                      failure_class = ?, failure_code = ?,
                      updated_at = ?
                    WHERE job_id = ? AND lease_token = ? AND fencing_number = ?
                      AND lease_incarnation_id = ?
                      AND attempt_number = ? AND lifecycle_status = 'RUNNING'
                      AND lease_expires_at > ?
                    """, next, failureClass.name(), failureCode, now, lease.jobId(),
                    lease.leaseToken(), lease.fencingNumber(),
                    lease.processIncarnationId(), lease.attemptNumber(), now);
            if (updated != 1) return stale(lease);
            finishAttempt(lease.seasonId(), lease.fixtureId(), lease.attemptNumber(),
                    next, failureClass.name(), failureCode);
            updateFixtureFailure(lease.seasonId(), lease.fixtureId(), next, failureCode);
            return new ExecutionResult(lease.jobId(),
                    retry ? Status.RETRY_PENDING : Status.BLOCKED,
                    lease.attemptNumber(), null, failureCode);
        });
    }

    private void finishAttempt(
            String seasonId, String fixtureId, int attempt, String status,
            String failureClass, String failureCode
    ) {
        store.jdbc().update("""
                UPDATE league_job_attempt SET lifecycle_status = ?, finished_at = ?,
                  failure_class = ?, failure_code = ?
                WHERE season_id = ? AND fixture_id = ? AND attempt_number = ?
                """, status, now(), failureClass, failureCode,
                seasonId, fixtureId, attempt);
    }

    private void updateFixtureFailure(
            String seasonId, String fixtureId, String status, String failureCode
    ) {
        store.jdbc().update("""
                UPDATE league_fixture SET lifecycle_status = ?, revision = revision + 1,
                  failure_code = ? WHERE season_id = ? AND fixture_id = ?
                """, status, failureCode, seasonId, fixtureId);
        if ("BLOCKED".equals(status)) {
            store.jdbc().update("""
                    UPDATE league_season SET lifecycle_status = 'BLOCKED',
                      lifecycle_revision = lifecycle_revision + 1, updated_at = ?
                    WHERE season_id = ? AND lifecycle_status NOT IN
                      ('COMPLETED', 'CANCELLED')
                    """, now(), seasonId);
        }
    }

    private ExecutionResult stale(Lease lease) {
        return new ExecutionResult(lease.jobId(), Status.STALE_RESULT_REJECTED,
                lease.attemptNumber(), null, "STALE_OR_EXPIRED_FENCING_TOKEN");
    }

    private void lockFixture(String seasonId, String fixtureId) {
        List<String> rows = store.jdbc().query("""
                SELECT fixture_id FROM league_fixture
                WHERE season_id = ? AND fixture_id = ? FOR UPDATE
                """, (result, row) -> result.getString(1), seasonId, fixtureId);
        if (rows.isEmpty()) throw new IllegalStateException("LEAGUE_FIXTURE_NOT_PERSISTED");
    }

    private static String frozenInputHash(
            LeagueSeasonAggregate season,
            LeagueFixture fixture
    ) {
        return LeagueIdentity.sha256(
                "jobInputSchema=AI_LEAGUE_FULL_AUTO_FROZEN_INPUT_V1\n"
                        + "seasonId=" + season.seasonId() + '\n'
                        + "fixtureId=" + fixture.fixtureId() + '\n'
                        + "boundSeriesId=" + fixture.boundSeriesId() + '\n'
                        + "fixtureRootSeed=" + fixture.fixtureRootSeed() + '\n'
                        + "scheduleIdentity=" + season.schedule().scheduleIdentity() + '\n'
                        + "snapshotIdentity=" + season.frozenSnapshot().snapshotIdentity() + '\n'
                        + "productDecisionHash=" + season.productDecisionHash() + '\n');
    }

    private static JobView jobView(ResultSet result) throws SQLException {
        OffsetDateTime expiry = result.getObject(9, OffsetDateTime.class);
        return new JobView(result.getString(1), result.getString(2), result.getString(3),
                result.getString(4), result.getLong(5), result.getInt(6),
                result.getLong(7), result.getString(8), expiry, result.getString(10),
                result.getString(11), result.getString(12));
    }

    private static void requireOwner(String ownerId) {
        if (ownerId == null || ownerId.isBlank() || ownerId.length() > 160
                || ownerId.indexOf('\n') >= 0) {
            throw new IllegalArgumentException("ownerId");
        }
    }

    private OffsetDateTime now() {
        return clock.instant().atOffset(ZoneOffset.UTC);
    }
}
