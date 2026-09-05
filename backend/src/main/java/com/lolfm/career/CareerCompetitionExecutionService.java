package com.lolfm.career;

import com.lolfm.application.CareerCompetitionPlayerSeriesKernel;
import com.lolfm.application.SeriesStatus;
import com.lolfm.league.CareerCompetitionAutomatedSeriesKernel;
import com.lolfm.league.LeagueProductionSnapshotProvider;
import com.lolfm.league.LeagueSeasonFrozenSnapshot;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Durable command boundary for the current Career competition fixture. */
@Service
public final class CareerCompetitionExecutionService {
    public static final String START_SCHEMA =
            "CAREER_COMPETITION_START_OR_RESUME_COMMAND_V1";
    public static final String RECONCILE_SCHEMA =
            "CAREER_COMPETITION_RECONCILE_COMMAND_V1";
    private final CareerCompetitionRelationalStore store;
    private final CareerCompetitionAutomatedSeriesKernel auto;
    private final CareerCompetitionPlayerSeriesKernel player;
    private final LeagueProductionSnapshotProvider production;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;
    private final Clock clock;
    private final CareerCompetitionJobLease leases;

    public CareerCompetitionExecutionService(
            CareerCompetitionRelationalStore store,
            CareerCompetitionAutomatedSeriesKernel auto,
            CareerCompetitionPlayerSeriesKernel player,
            LeagueProductionSnapshotProvider production,
            JdbcTemplate jdbc,
            PlatformTransactionManager transactionManager,
            Clock clock,
            CareerCompetitionJobLease leases
    ) {
        this.store = Objects.requireNonNull(store, "store");
        this.auto = Objects.requireNonNull(auto, "auto");
        this.player = Objects.requireNonNull(player, "player");
        this.production = Objects.requireNonNull(production, "production");
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
        this.transactions = new TransactionTemplate(Objects.requireNonNull(
                transactionManager, "transactionManager"));
        this.clock = Objects.requireNonNull(clock, "clock");
        this.leases = Objects.requireNonNull(leases, "leases");
    }

    public ExecutionResult startOrResume(
            CareerRelationalStore.CareerRow career,
            int seasonYear,
            java.time.LocalDate currentDate,
            long expectedRevision,
            String clientCommandId
    ) {
        requireCommand(clientCommandId);
        String commandPayload = commandPayload(career.careerId(), seasonYear,
                expectedRevision, clientCommandId);
        CommandRow priorCommand = priorCommand(career.careerId(), seasonYear,
                clientCommandId);
        if (priorCommand != null) {
            if (!priorCommand.payloadHash().equals(commandPayload)) {
                throw new IllegalStateException("COMPETITION_COMMAND_ID_CONFLICT");
            }
            return replayCommand(career.careerId(), seasonYear, priorCommand.bindingHash(), expectedRevision,
                    clientCommandId);
        }
        CareerCompetitionRelationalStore.CycleView cycle = store.load(
                career.careerId(), seasonYear);
        if (cycle.revision() != expectedRevision) throw new IllegalStateException(
                "CAREER_COMPETITION_STALE_REVISION");
        CareerCompetitionRelationalStore.FixtureRow fixture = cycle.fixtures().stream()
                .filter(value -> !"COMPLETED".equals(value.lifecycleStatus()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "CAREER_COMPETITION_NO_PENDING_FIXTURE"));
        if (!"READY".equals(fixture.lifecycleStatus())) throw new IllegalStateException(
                "CAREER_COMPETITION_FIXTURE_NOT_READY");
        if (fixture.date().isAfter(currentDate)) {
            throw new IllegalStateException("CAREER_COMPETITION_FIXTURE_NOT_DUE");
        }
        String resourceProvenanceHash = production.currentResourceProvenanceHash();
        LeagueSeasonFrozenSnapshot productionSnapshot = production.currentSnapshot(
                production.currentTeamCodes());
        if (!resourceProvenanceHash.equals(
                production.currentResourceProvenanceHash())) {
            throw new IllegalStateException(
                    "COMPETITION_PRODUCTION_AUTHORITY_CHANGED_DURING_BIND");
        }
        CareerCompetitionSeriesBindingV1 binding = store.bindFixture(
                career.careerId(), seasonYear, fixture.competitionId(), fixture.matchId(), productionSnapshot,
                resourceProvenanceHash);
        registerCommand(binding, clientCommandId, commandPayload);
        if ("PLAYER_CONTROLLED".equals(binding.executionMode())) {
            CareerCompetitionPlayerSeriesKernel.Reference reference =
                    player.start(binding);
            jdbc.update("""
                    UPDATE career_competition_series_binding
                    SET lifecycle_status = ?, updated_at = ? WHERE binding_hash = ?
                    """, reference.status() == SeriesStatus.COMPLETED
                    ? "SERIES_COMPLETED" : "PLAYER_ACTIVE", now(),
                    binding.bindingHash());
            return new ExecutionResult("PLAYER_CONTROLLED", binding.fixtureId(),
                    binding.matchId(), binding.boundSeriesId(), binding.bindingHash(),
                    null, reference.status().name(), reference.replayed(), null);
        }
        return queueAuto(binding, expectedRevision, clientCommandId);
    }

    private ExecutionResult replayCommand(
            String careerId, int seasonYear, String bindingHash, long expectedRevision, String clientCommandId
    ) {
        CareerCompetitionSeriesBindingV1 binding = bindingByHash(bindingHash);
        requireScope(binding, bindingHash, careerId, seasonYear);
        if (store.hasAppliedCompletion(binding)) return completedReplay(binding);
        if ("PLAYER_CONTROLLED".equals(binding.executionMode())) {
            CareerCompetitionPlayerSeriesKernel.Reference reference =
                    player.start(binding);
            return new ExecutionResult("PLAYER_CONTROLLED", binding.fixtureId(),
                    binding.matchId(), binding.boundSeriesId(), binding.bindingHash(),
                    null, reference.status().name(), true, null);
        }
        List<JobRow> jobs = jobs(bindingHash);
        if (jobs.isEmpty()) return queueAuto(binding, expectedRevision,
                clientCommandId);
        JobRow job = jobs.getFirst();
        return new ExecutionResult("FULL_AUTO", binding.fixtureId(),
                binding.matchId(), binding.boundSeriesId(), binding.bindingHash(),
                job.jobId(), job.status(), true, job.failureCode());
    }

    public ExecutionResult reconcile(
            String careerId, int seasonYear, long expectedRevision,
            String clientCommandId
    ) {
        requireCommand(clientCommandId);
        CommandRow command = priorCommand(careerId, seasonYear, clientCommandId);
        if (command == null) return new ExecutionResult("NONE", null, null, null,
                null, null, "NOT_STARTED", true, null);
        String expectedPayload = commandPayload(careerId, seasonYear,
                expectedRevision, clientCommandId);
        if (!command.payloadHash().equals(expectedPayload)) {
            throw new IllegalStateException("COMPETITION_COMMAND_ID_CONFLICT");
        }
        CareerCompetitionSeriesBindingV1 binding = bindingByHash(
                command.bindingHash());
        requireScope(binding, command.bindingHash(), careerId, seasonYear);
        if (store.hasAppliedCompletion(binding)) return completedReplay(binding);
        if ("FULL_AUTO".equals(binding.executionMode())) {
            List<JobRow> jobs = jobs(binding.bindingHash());
            if (jobs.isEmpty()) return queueAuto(binding, expectedRevision,
                    clientCommandId);
            JobRow job = jobs.getFirst();
            return new ExecutionResult("FULL_AUTO", binding.fixtureId(),
                    binding.matchId(), binding.boundSeriesId(), binding.bindingHash(),
                    job.jobId(), job.status(), true, job.failureCode());
        }
        CareerCompetitionPlayerSeriesKernel.Reference reference = player.resume(binding);
        if (reference.status() == SeriesStatus.COMPLETED) {
            VerifiedCompetitionFixtureCompletion verified =
                    CareerCompetitionFixtureCompletionReceiptV1.verifyPlayer(binding,
                            player.completedEvidence(binding));
            CareerCompetitionRelationalStore.CompletionResult applied =
                    store.applyVerifiedCompletion(verified);
            return new ExecutionResult("PLAYER_CONTROLLED", binding.fixtureId(),
                    binding.matchId(), binding.boundSeriesId(), binding.bindingHash(),
                    null, "COMPLETED", applied.replayed(), null);
        }
        return new ExecutionResult("PLAYER_CONTROLLED", binding.fixtureId(),
                binding.matchId(), binding.boundSeriesId(), binding.bindingHash(),
                null, reference.status().name(), true, null);
    }

    private ExecutionResult queueAuto(
            CareerCompetitionSeriesBindingV1 binding,
            long expectedRevision,
            String commandId
    ) {
        String payloadHash = CareerCompetitionRules.sha256((
                "schema=" + START_SCHEMA + '\n'
                        + "bindingHash=" + binding.bindingHash() + '\n'
                        + "expectedRevision=" + expectedRevision + '\n').getBytes(
                StandardCharsets.UTF_8));
        String jobId = "competition_job_" + CareerCompetitionRules.sha256((
                "bindingHash=" + binding.bindingHash() + "\ncommandId=" + commandId
                        + '\n').getBytes(StandardCharsets.UTF_8));
        JobRow job = transactions.execute(ignored -> createOrLoadJob(jobId, binding,
                commandId, payloadHash));
        return new ExecutionResult("FULL_AUTO", binding.fixtureId(),
                binding.matchId(), binding.boundSeriesId(), binding.bindingHash(),
                job.jobId(), job.status(), job.attempt() > 0, job.failureCode());
    }

    /** Claims and executes one previously persisted Auto fixture job. */
    public ExecutionResult executeAutoJob(String jobId) {
        JobBinding work = jobBinding(jobId);
        CareerCompetitionSeriesBindingV1 binding = work.binding();
        JobRow job = work.job();
        if ("COMPLETED".equals(job.status()) || "BLOCKED".equals(job.status())) {
            return new ExecutionResult("FULL_AUTO", binding.fixtureId(),
                    binding.matchId(), binding.boundSeriesId(), binding.bindingHash(),
                    job.jobId(), job.status(), true, job.failureCode());
        }
        if ("RUNNING".equals(job.status())) {
            jdbc.update("""
                    UPDATE career_competition_job
                    SET lifecycle_status = 'PENDING', lease_token = NULL,
                        lease_expires_at = NULL, updated_at = ?
                    WHERE job_id = ? AND lifecycle_status = 'RUNNING'
                      AND lease_expires_at <= ?
                    """, now(), jobId, now());
            work = jobBinding(jobId);
            job = work.job();
            if ("RUNNING".equals(job.status())) {
                return new ExecutionResult("FULL_AUTO", binding.fixtureId(),
                        binding.matchId(), binding.boundSeriesId(),
                        binding.bindingHash(), job.jobId(), "RUNNING", true, null);
            }
        }
        String lease = CareerCompetitionRules.sha256(("jobId=" + jobId
                + "\nattempt=" + (job.attempt() + 1) + "\nbindingHash="
                + binding.bindingHash() + '\n').getBytes(StandardCharsets.UTF_8));
        OffsetDateTime claimedAt = now();
        int claimed = jdbc.update("""
                UPDATE career_competition_job
                SET lifecycle_status = 'RUNNING', attempt_number = attempt_number + 1,
                    lease_token = ?, lease_expires_at = ?,
                    updated_at = ?
                WHERE job_id = ? AND lifecycle_status = 'PENDING'
                """, lease, claimedAt.plus(leases.duration()), claimedAt, jobId);
        if (claimed != 1) {
            JobRow current = jobs(binding.bindingHash()).getFirst();
            return new ExecutionResult("FULL_AUTO", binding.fixtureId(),
                    binding.matchId(), binding.boundSeriesId(), binding.bindingHash(),
                    jobId, current.status(), true, current.failureCode());
        }
        try (CareerCompetitionJobLease.Guard guard = leases.maintain(
                () -> renewLease(jobId, lease))) {
            CareerCompetitionAutomatedSeriesKernel.CompletedSeriesEvidence evidence =
                    auto.run(binding);
            guard.requireOwned();
            VerifiedCompetitionFixtureCompletion verified =
                    CareerCompetitionFixtureCompletionReceiptV1.verifyAutomated(
                            binding, evidence);
            guard.requireOwned();
            CareerCompetitionRelationalStore.CompletionResult applied =
                    guard.complete(() -> store.applyAutoVerifiedCompletion(
                            verified, jobId, lease, leases.duration()));
            return new ExecutionResult("FULL_AUTO", binding.fixtureId(),
                    binding.matchId(), binding.boundSeriesId(), binding.bindingHash(),
                    jobId, "COMPLETED", applied.replayed(), null);
        } catch (RuntimeException failure) {
            String code = failure.getMessage() == null
                    ? "COMPETITION_AUTO_EXECUTION_FAILED" : failure.getMessage();
            if ("COMPETITION_AUTO_JOB_FENCE_REJECTED".equals(code)) {
                JobRow current = jobs(binding.bindingHash()).getFirst();
                return new ExecutionResult("FULL_AUTO", binding.fixtureId(),
                        binding.matchId(), binding.boundSeriesId(),
                        binding.bindingHash(), jobId, current.status(), true,
                        current.failureCode());
            }
            OffsetDateTime failedAt = now();
            int blocked = jdbc.update("""
                    UPDATE career_competition_job
                    SET lifecycle_status = 'BLOCKED', failure_code = ?,
                        lease_token = NULL, lease_expires_at = NULL, updated_at = ?
                    WHERE job_id = ? AND lifecycle_status = 'RUNNING'
                      AND lease_token = ? AND lease_expires_at > ?
                    """, code, failedAt, jobId, lease, failedAt);
            if (blocked != 1) {
                JobRow current = jobs(binding.bindingHash()).getFirst();
                return new ExecutionResult("FULL_AUTO", binding.fixtureId(),
                        binding.matchId(), binding.boundSeriesId(),
                        binding.bindingHash(), jobId, current.status(), true,
                        current.failureCode());
            }
            return new ExecutionResult("FULL_AUTO", binding.fixtureId(),
                    binding.matchId(), binding.boundSeriesId(), binding.bindingHash(),
                    jobId, "BLOCKED", false, code);
        }
    }

    private boolean renewLease(String jobId, String lease) {
        OffsetDateTime at = now();
        return jdbc.update("""
                UPDATE career_competition_job
                SET lease_expires_at = ?, updated_at = ?
                WHERE job_id = ? AND lifecycle_status = 'RUNNING'
                  AND lease_token = ? AND lease_expires_at > ?
                """, at.plus(leases.duration()), at, jobId, lease, at) == 1;
    }

    private static void requireScope(CareerCompetitionSeriesBindingV1 binding,
                                     String hash, String careerId, int year) {
        if (!hash.equals(binding.bindingHash()) || !careerId.equals(binding.careerId())
                || year != binding.seasonYear()) {
            throw new IllegalStateException("COMPETITION_COMMAND_BINDING_SCOPE_MISMATCH");
        }
    }

    private ExecutionResult completedReplay(CareerCompetitionSeriesBindingV1 binding) {
        List<JobRow> values = jobs(binding.bindingHash());
        String jobId = values.isEmpty() ? null : values.getFirst().jobId();
        return new ExecutionResult(binding.executionMode(), binding.fixtureId(),
                binding.matchId(), binding.boundSeriesId(), binding.bindingHash(),
                jobId, "COMPLETED", true, null);
    }

    private JobBinding jobBinding(String jobId) {
        List<JobBinding> values = jdbc.query("""
                SELECT j.job_id, j.client_command_id, j.payload_hash,
                       j.lifecycle_status, j.attempt_number, j.failure_code,
                       b.binding_canonical
                FROM career_competition_job j
                JOIN career_competition_series_binding b
                  ON b.binding_hash = j.binding_hash
                WHERE j.job_id = ?
                """, (result, row) -> new JobBinding(new JobRow(
                result.getString(1), result.getString(2), result.getString(3),
                result.getString(4), result.getInt(5), result.getString(6)),
                CareerCompetitionSeriesBindingV1.restoreCanonical(
                        result.getString(7))), jobId);
        if (values.size() != 1) throw new IllegalStateException(
                "COMPETITION_AUTO_JOB_NOT_FOUND");
        return values.getFirst();
    }

    private void registerCommand(
            CareerCompetitionSeriesBindingV1 binding,
            String commandId,
            String payloadHash
    ) {
        try {
            jdbc.update("""
                    INSERT INTO career_competition_command(
                      career_id, calendar_season_year, client_command_id,
                      command_type, payload_hash, binding_hash, created_at)
                    VALUES (?, ?, ?, 'START_OR_RESUME', ?, ?, ?)
                    """, binding.careerId(), binding.seasonYear(), commandId,
                    payloadHash, binding.bindingHash(), now());
        } catch (DuplicateKeyException duplicate) {
            CommandRow prior = priorCommand(binding.careerId(), binding.seasonYear(),
                    commandId);
            if (prior == null || !prior.payloadHash().equals(payloadHash)
                    || !prior.bindingHash().equals(binding.bindingHash())) {
                throw new IllegalStateException("COMPETITION_COMMAND_ID_CONFLICT",
                        duplicate);
            }
        }
    }

    private CommandRow priorCommand(String careerId, int year, String commandId) {
        List<CommandRow> rows = jdbc.query("""
                SELECT payload_hash, binding_hash FROM career_competition_command
                WHERE career_id = ? AND calendar_season_year = ?
                  AND client_command_id = ?
                """, (result, row) -> new CommandRow(result.getString(1),
                result.getString(2)), careerId, year, commandId);
        return rows.isEmpty() ? null : rows.getFirst();
    }

    private CareerCompetitionSeriesBindingV1 bindingByHash(String bindingHash) {
        return CareerCompetitionSeriesBindingV1.restoreCanonical(
                jdbc.queryForObject("""
                        SELECT binding_canonical
                        FROM career_competition_series_binding
                        WHERE binding_hash = ?
                        """, String.class, bindingHash));
    }

    private static String commandPayload(
            String careerId, int year, long expectedRevision, String commandId
    ) {
        return CareerCompetitionRules.sha256(("schema=" + START_SCHEMA + '\n'
                + "careerId=" + careerId + '\n'
                + "calendarSeasonYear=" + year + '\n'
                + "expectedRevision=" + expectedRevision + '\n'
                + "clientCommandId=" + commandId + '\n').getBytes(
                StandardCharsets.UTF_8));
    }

    private JobRow createOrLoadJob(
            String jobId, CareerCompetitionSeriesBindingV1 binding,
            String commandId, String payloadHash
    ) {
        List<JobRow> prior = jobs(binding.bindingHash());
        if (!prior.isEmpty()) {
            JobRow value = prior.getFirst();
            if (!value.commandId().equals(commandId)
                    || !value.payloadHash().equals(payloadHash)) {
                throw new IllegalStateException(
                        "COMPETITION_FIXTURE_ALREADY_DISPATCHED");
            }
            return value;
        }
        try {
            jdbc.update("""
                    INSERT INTO career_competition_job(
                      job_id, binding_hash, client_command_id, payload_hash,
                      lifecycle_status, attempt_number, created_at, updated_at)
                    VALUES (?, ?, ?, ?, 'PENDING', 0, ?, ?)
                    """, jobId, binding.bindingHash(), commandId, payloadHash,
                    now(), now());
        } catch (DuplicateKeyException duplicate) {
            throw new IllegalStateException("COMPETITION_COMMAND_ID_CONFLICT", duplicate);
        }
        return jobs(binding.bindingHash()).getFirst();
    }

    private List<JobRow> jobs(String bindingHash) {
        return jdbc.query("""
                SELECT job_id, client_command_id, payload_hash, lifecycle_status,
                       attempt_number, failure_code
                FROM career_competition_job WHERE binding_hash = ?
                ORDER BY created_at, job_id
                """, (result, row) -> new JobRow(result.getString(1),
                result.getString(2), result.getString(3), result.getString(4),
                result.getInt(5), result.getString(6)), bindingHash);
    }

    private OffsetDateTime now() {
        return clock.instant().atOffset(ZoneOffset.UTC);
    }

    private static void requireCommand(String commandId) {
        if (commandId == null || !commandId.matches(
                "[0-9a-fA-F]{8}-[0-9a-fA-F-]{27,}")) {
            throw new IllegalArgumentException("clientCommandId");
        }
    }

    private record JobRow(
            String jobId, String commandId, String payloadHash, String status,
            int attempt, String failureCode
    ) {}
    private record JobBinding(JobRow job, CareerCompetitionSeriesBindingV1 binding) {}
    private record CommandRow(String payloadHash, String bindingHash) {}

    public record ExecutionResult(
            String executionMode,
            String fixtureId,
            String matchId,
            String seriesId,
            String bindingHash,
            String jobId,
            String status,
            boolean replayed,
            String failureCode
    ) {}
}
