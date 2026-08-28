package com.lolfm.application;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import org.springframework.stereotype.Repository;

/** Bounded process-local repository with per-Series atomic mutations and create-index ownership. */
@Repository
final class SeriesRepository {
    private final ConcurrentHashMap<String, SeriesAggregate> series = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, CreateIndex> createCommands =
            new ConcurrentHashMap<>();
    private final Clock clock;
    private final SeriesLifecycleConfiguration configuration;
    private final CleanupObserver cleanupObserver;
    private final Object capacityBoundary = new Object();

    @org.springframework.beans.factory.annotation.Autowired
    SeriesRepository(Clock clock, SeriesLifecycleConfiguration configuration) {
        this(clock, configuration, CleanupObserver.NONE);
    }

    SeriesRepository(
            Clock clock,
            SeriesLifecycleConfiguration configuration,
            CleanupObserver cleanupObserver
    ) {
        this.clock = Objects.requireNonNull(clock, "clock");
        this.configuration = Objects.requireNonNull(configuration, "configuration");
        this.cleanupObserver = Objects.requireNonNull(cleanupObserver, "cleanupObserver");
    }

    Instant now() { return clock.instant(); }
    Instant parentExpiresAt(Instant activity) {
        return activity.plus(configuration.parentTtl());
    }
    Instant childExpiresAt(Instant activity, Instant parentExpiry) {
        Instant child = activity.plus(configuration.childIdleTtl());
        return child.isBefore(parentExpiry) ? child : parentExpiry;
    }
    Instant reservationExpiresAt(Instant created) {
        return created.plus(configuration.simulationLease());
    }
    int maximumCommandReceipts() { return configuration.maximumCommandReceipts(); }

    CreateResult create(String commandId, String payloadHash, SeriesAggregate aggregate) {
        synchronized (capacityBoundary) {
            cleanupExpiredAndTerminal();
            CreateIndex prior = createCommands.get(commandId);
            if (prior != null) {
                if (!prior.payloadHash().equals(payloadHash)) {
                    throw new RepositoryFailure("SERIES_COMMAND_ID_PAYLOAD_CONFLICT");
                }
                SeriesAggregate existing = series.get(prior.seriesId());
                if (existing == null) throw new RepositoryFailure("SERIES_CREATE_INDEX_CORRUPT");
                return new CreateResult(existing, true);
            }
            if (series.size() >= configuration.maximumSeries()) {
                throw new RepositoryFailure("SERIES_CAPACITY_REACHED");
            }
            if (series.putIfAbsent(aggregate.seriesId(), aggregate) != null) {
                throw new RepositoryFailure("SERIES_ID_COLLISION");
            }
            CreateIndex index = new CreateIndex(payloadHash, aggregate.seriesId());
            if (createCommands.putIfAbsent(commandId, index) != null) {
                series.remove(aggregate.seriesId(), aggregate);
                throw new RepositoryFailure("SERIES_CREATE_INDEX_COLLISION");
            }
            return new CreateResult(aggregate, false);
        }
    }

    SeriesAggregate get(String seriesId) {
        AtomicReference<SeriesAggregate> found = new AtomicReference<>();
        series.computeIfPresent(seriesId, (id, current) -> {
            SeriesAggregate live = expire(current, clock.instant());
            found.set(live);
            return live;
        });
        if (found.get() == null) throw new RepositoryFailure("SERIES_NOT_FOUND");
        return found.get();
    }

    <T> T mutate(String seriesId, Function<SeriesAggregate, Mutation<T>> operation) {
        AtomicReference<T> result = new AtomicReference<>();
        AtomicReference<RuntimeException> failure = new AtomicReference<>();
        series.compute(seriesId, (id, current) -> {
            if (current == null) {
                failure.set(new RepositoryFailure("SERIES_NOT_FOUND"));
                return null;
            }
            SeriesAggregate live = expire(current, clock.instant());
            try {
                Mutation<T> mutation = operation.apply(live);
                result.set(mutation.result());
                return mutation.aggregate();
            } catch (RuntimeException error) {
                failure.set(error);
                return live;
            }
        });
        if (failure.get() != null) throw failure.get();
        return result.get();
    }

    int storedSeriesCount() { return series.size(); }

    private SeriesAggregate expire(SeriesAggregate aggregate, Instant now) {
        SeriesGame game = aggregate.currentGame();
        if (aggregate.status() == SeriesStatus.ACTIVE && game.reservation() != null
                && !now.isBefore(game.reservation().leaseExpiresAt())) {
            SeriesSimulationReservation expiredReservation = game.reservation();
            SeriesGame released = new SeriesGame(
                    game.gameId(), game.gameNumber(), game.blueTeamCode(), game.redTeamCode(),
                    game.controlledSide(), game.matchSeed(), game.historyBefore(),
                    game.historyBeforeHash(), SeriesGameStatus.SIMULATION_FAILED_RETRYABLE,
                    "SIMULATION_LEASE_EXPIRED", game.childGeneration(), game.childDraft(),
                    null, game.completedDraft(), game.resultSummary(), game.receipt());
            LinkedHashMap<String, SeriesCommandReceipt> receipts = new LinkedHashMap<>(
                    aggregate.commandReceipts());
            SeriesCommandReceipt command = receipts.get(expiredReservation.commandId());
            if (command != null && command.completion() == SeriesCommandCompletion.IN_PROGRESS
                    && command.payloadHash().equals(expiredReservation.payloadHash())) {
                receipts.put(command.commandId(), command.completed(
                        SeriesCommandCompletion.FAILED, aggregate.revision(),
                        aggregate.status(), released.status(), "SIMULATION_LEASE_EXPIRED",
                        "SERIES_SIMULATION_LEASE_EXPIRED", 409, true));
            }
            aggregate = aggregate.copy(aggregate.revision(), aggregate.status(),
                    aggregate.terminalReason(), aggregate.score(), replaceLast(
                    aggregate.games(), released), aggregate.consumedPicks(),
                    aggregate.historyHash(), aggregate.winnerTeamCode(),
                    aggregate.lastActivityAt(), aggregate.expiresAt(),
                    receipts);
            game = released;
        }
        boolean reservationValid = game.reservation() != null
                && now.isBefore(game.reservation().leaseExpiresAt());
        boolean parentCanExpire = aggregate.status() == SeriesStatus.ACTIVE
                || aggregate.status() == SeriesStatus.BLOCKED
                || aggregate.status() == SeriesStatus.COMPLETED;
        if (parentCanExpire && !reservationValid
                && !now.isBefore(aggregate.expiresAt())) {
            SeriesGame expiredGame = new SeriesGame(
                    game.gameId(), game.gameNumber(), game.blueTeamCode(), game.redTeamCode(),
                    game.controlledSide(), game.matchSeed(), game.historyBefore(),
                    game.historyBeforeHash(), game.status(), game.reason(),
                    game.childGeneration(), game.childDraft(), null, game.completedDraft(),
                    game.resultSummary(), game.receipt());
            return aggregate.copy(aggregate.revision(), SeriesStatus.EXPIRED,
                    "PARENT_TTL_EXPIRED", aggregate.score(), replaceLast(
                    aggregate.games(), expiredGame), aggregate.consumedPicks(),
                    aggregate.historyHash(), aggregate.winnerTeamCode(),
                    aggregate.lastActivityAt(), aggregate.expiresAt(),
                    aggregate.commandReceipts());
        }
        SeriesChildDraft child = game.childDraft();
        if (aggregate.status() == SeriesStatus.ACTIVE && child != null
                && child.status() == PlayerDraftSessionStatus.ACTIVE
                && !now.isBefore(child.expiresAt())) {
            SeriesChildDraft expired = new SeriesChildDraft(
                    child.childId(), child.generation(), child.revision(),
                    PlayerDraftSessionStatus.EXPIRED, child.createdAt(),
                    child.lastActivityAt(), child.expiresAt(), child.progress());
            SeriesGame expiredGame = new SeriesGame(
                    game.gameId(), game.gameNumber(), game.blueTeamCode(), game.redTeamCode(),
                    game.controlledSide(), game.matchSeed(), game.historyBefore(),
                    game.historyBeforeHash(), SeriesGameStatus.DRAFT_EXPIRED,
                    "CHILD_IDLE_TTL_EXPIRED", game.childGeneration(), expired,
                    game.reservation(), game.completedDraft(), game.resultSummary(),
                    game.receipt());
            return aggregate.copy(aggregate.revision(), aggregate.status(),
                    aggregate.terminalReason(), aggregate.score(), replaceLast(
                    aggregate.games(), expiredGame), aggregate.consumedPicks(),
                    aggregate.historyHash(), aggregate.winnerTeamCode(),
                    aggregate.lastActivityAt(), aggregate.expiresAt(),
                    aggregate.commandReceipts());
        }
        return aggregate;
    }

    private void cleanupExpiredAndTerminal() {
        Instant now = clock.instant();
        series.forEach((id, ignored) -> {
            cleanupObserver.beforeAtomicCleanup(id);
            series.computeIfPresent(id, (key, current) -> {
                SeriesAggregate live = expire(current, now);
                if (live.status() != SeriesStatus.EXPIRED
                        && live.status() != SeriesStatus.CANCELLED) {
                    return live;
                }
                createCommands.entrySet().removeIf(
                        entry -> entry.getValue().seriesId().equals(id));
                return null;
            });
        });
    }

    private static java.util.List<SeriesGame> replaceLast(
            java.util.List<SeriesGame> games, SeriesGame replacement
    ) {
        ArrayList<SeriesGame> values = new ArrayList<>(games);
        values.set(values.size() - 1, replacement);
        return values;
    }

    record Mutation<T>(SeriesAggregate aggregate, T result) {}
    record CreateResult(SeriesAggregate aggregate, boolean replayed) {}
    private record CreateIndex(String payloadHash, String seriesId) {}

    @FunctionalInterface
    interface CleanupObserver {
        CleanupObserver NONE = seriesId -> { };
        void beforeAtomicCleanup(String seriesId);
    }

    static final class RepositoryFailure extends RuntimeException {
        RepositoryFailure(String code) { super(code); }
    }
}
