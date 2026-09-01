package com.lolfm.league;

import com.lolfm.simulator.SimulationInstrumentation;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

/** Internal application boundary for the later API batch; this is not a public HTTP API. */
public interface LeagueSimulationApplicationPort {
    DispatchResult dispatchFullAutoFixture(String seasonId, String fixtureId);
    DispatchBatch dispatchRound(String seasonId, int roundNumber);
    Optional<Lease> leaseNext(String ownerId);
    boolean heartbeat(Lease lease);
    ExecutionResult execute(Lease lease, SimulationInstrumentation instrumentation);
    List<ExecutionResult> executeQueued(
            String ownerId, int parallelism, SimulationInstrumentation instrumentation);
    default List<ExecutionResult> executeQueued(
            String ownerId, SimulationInstrumentation instrumentation
    ) {
        return executeQueued(ownerId,
                LeagueV1OperationalConfiguration.defaults().defaultMaxParallelFixtures(),
                instrumentation);
    }
    RecoveryResult recover();
    int purgeExpiredAttemptLogs();
    Optional<JobView> findJob(String seasonId, String fixtureId);

    record DispatchResult(String jobId, boolean replayed) {}
    record DispatchBatch(int queued, int replayed, int playerFixturesExcluded) {}
    record Lease(
            String jobId, String seasonId, String fixtureId, String leaseToken,
            long fencingNumber, int attemptNumber, String frozenInputHash,
            OffsetDateTime expiresAt
    ) {}
    record ExecutionResult(
            String jobId, Status status, int attemptNumber, String receiptHash,
            String failureCode
    ) {}
    record RecoveryResult(
            int autoJobsRetried, int autoJobsBlocked,
            int playerStartsRequiringRestart, int outboxEventsDelivered
    ) {}
    record JobView(
            String jobId, String seasonId, String fixtureId, String status,
            long revision, int attemptNumber, long fencingNumber,
            String leaseOwner, OffsetDateTime leaseExpiresAt,
            String frozenInputHash, String failureClass, String failureCode
    ) {}
    enum Status { COMPLETED, RETRY_PENDING, BLOCKED, STALE_RESULT_REJECTED }
}
