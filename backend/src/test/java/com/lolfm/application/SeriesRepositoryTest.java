package com.lolfm.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import com.lolfm.draft.PlayerControlledDraftEngine;
import com.lolfm.simulator.TeamSide;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class SeriesRepositoryTest {
    private static final Instant START = Instant.parse("2026-08-28T00:00:00Z");

    @Test
    void concurrentCreateHonorsExactCapacity32AndRepositoryInstancesAreIsolated()
            throws Exception {
        MutableClock clock = new MutableClock(START);
        SeriesRepository repository = new SeriesRepository(
                clock, new SeriesLifecycleConfiguration());
        CountDownLatch ready = new CountDownLatch(64);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger created = new AtomicInteger();
        AtomicInteger rejected = new AtomicInteger();

        try (var executor = Executors.newFixedThreadPool(64)) {
            var calls = IntStream.range(0, 64).mapToObj(index -> executor.submit(() -> {
                ready.countDown();
                start.await();
                try {
                    repository.create("command-" + index, "payload-" + index,
                            aggregate(repository, "series-" + index));
                    created.incrementAndGet();
                } catch (SeriesRepository.RepositoryFailure error) {
                    assertThat(error).hasMessage("SERIES_CAPACITY_REACHED");
                    rejected.incrementAndGet();
                }
                return null;
            })).toList();
            ready.await();
            start.countDown();
            for (var call : calls) call.get();
        }

        assertThat(created).hasValue(32);
        assertThat(rejected).hasValue(32);
        assertThat(repository.storedSeriesCount()).isEqualTo(32);

        SeriesRepository isolated = new SeriesRepository(
                clock, new SeriesLifecycleConfiguration());
        isolated.create("command", "payload", aggregate(isolated, "independent"));
        assertThat(isolated.storedSeriesCount()).isOne();
        assertThat(repository.storedSeriesCount()).isEqualTo(32);
    }

    @Test
    void sameCreateCommandReplaysAndPayloadConflictDoesNotLeakCapacityOrIndex() {
        MutableClock clock = new MutableClock(START);
        SeriesRepository repository = repository(clock, 2);
        SeriesAggregate first = aggregate(repository, "first");

        assertThat(repository.create("same", "payload", first).replayed()).isFalse();
        assertThat(repository.create("same", "payload",
                aggregate(repository, "unused")).replayed()).isTrue();
        assertThatThrownBy(() -> repository.create("same", "different",
                aggregate(repository, "collision")))
                .isInstanceOf(SeriesRepository.RepositoryFailure.class)
                .hasMessage("SERIES_COMMAND_ID_PAYLOAD_CONFLICT");
        repository.create("second", "payload-2", aggregate(repository, "second"));

        assertThat(repository.storedSeriesCount()).isEqualTo(2);
    }

    @Test
    void getDoesNotKeepAliveAndParentExpiryReleasesAggregateAndCreateIndex() {
        MutableClock clock = new MutableClock(START);
        SeriesRepository repository = repository(clock, 2);
        SeriesAggregate first = aggregate(repository, "first");
        repository.create("reusable-command", "payload", first);

        clock.advance(Duration.ofMinutes(119));
        SeriesAggregate read = repository.get(first.seriesId());
        assertThat(read.lastActivityAt()).isEqualTo(START);
        assertThat(read.expiresAt()).isEqualTo(START.plus(Duration.ofMinutes(120)));

        clock.advance(Duration.ofMinutes(1));
        assertThat(repository.get(first.seriesId()).status()).isEqualTo(SeriesStatus.EXPIRED);
        repository.create("replacement-command", "replacement-payload",
                aggregate(repository, "replacement"));
        assertThat(repository.storedSeriesCount()).isOne();

        SeriesRepository.CreateResult reused = repository.create(
                "reusable-command", "payload", aggregate(repository, "reused"));
        assertThat(reused.replayed()).isFalse();
    }

    @Test
    void cancelledCleanupReleasesCapacityAndOnlyItsCreateIndex() {
        MutableClock clock = new MutableClock(START);
        SeriesRepository repository = repository(clock, 3);
        SeriesAggregate cancelledBase = aggregate(repository, "cancelled");
        SeriesAggregate cancelled = cancelledBase.copy(cancelledBase.revision(),
                SeriesStatus.CANCELLED, "CANCELLED_BY_CLIENT", cancelledBase.score(),
                cancelledBase.games(), cancelledBase.consumedPicks(),
                cancelledBase.historyHash(), cancelledBase.winnerTeamCode(),
                cancelledBase.lastActivityAt(), cancelledBase.expiresAt(),
                cancelledBase.commandReceipts());
        SeriesAggregate live = aggregate(repository, "live");
        repository.create("cancelled-command", "cancelled-payload", cancelled);
        repository.create("live-command", "live-payload", live);

        repository.create("replacement-command", "replacement-payload",
                aggregate(repository, "replacement"));

        assertThat(repository.storedSeriesCount()).isEqualTo(2);
        assertThat(repository.get(live.seriesId())).isEqualTo(live);
        assertThat(repository.create("live-command", "live-payload",
                aggregate(repository, "unused-live")).replayed()).isTrue();
        assertThat(repository.create("cancelled-command", "cancelled-payload",
                aggregate(repository, "reused-cancelled")).replayed()).isFalse();
    }

    @Test
    void childIdleExpiryAndSimulationLeaseExpiryAreMatchScopedAndRevisionNeutral() {
        MutableClock childClock = new MutableClock(START);
        SeriesRepository childRepository = repository(childClock, 2);
        SeriesAggregate childAggregate = withActiveChild(
                aggregate(childRepository, "child"), childRepository);
        childRepository.create("child-command", "child-payload", childAggregate);

        childClock.advance(Duration.ofMinutes(30));
        SeriesAggregate childExpired = childRepository.get(childAggregate.seriesId());
        assertThat(childExpired.status()).isEqualTo(SeriesStatus.ACTIVE);
        assertThat(childExpired.revision()).isZero();
        assertThat(childExpired.currentGame().status())
                .isEqualTo(SeriesGameStatus.DRAFT_EXPIRED);
        assertThat(childExpired.currentGame().childDraft().status())
                .isEqualTo(PlayerDraftSessionStatus.EXPIRED);

        MutableClock leaseClock = new MutableClock(START);
        SeriesRepository leaseRepository = repository(leaseClock, 2);
        SeriesAggregate reserved = withReservation(
                aggregate(leaseRepository, "lease"), leaseRepository);
        leaseRepository.create("lease-command", "lease-payload", reserved);

        leaseClock.advance(Duration.ofMinutes(5).minusSeconds(1));
        SeriesAggregate beforeLeaseBoundary = leaseRepository.get(reserved.seriesId());
        assertThat(beforeLeaseBoundary.currentGame().reservation()).isNotNull();
        assertThat(beforeLeaseBoundary.commandReceipts().get("simulate").completion())
                .isEqualTo(SeriesCommandCompletion.IN_PROGRESS);
        leaseClock.advance(Duration.ofSeconds(1));
        SeriesAggregate leaseExpired = leaseRepository.get(reserved.seriesId());
        assertThat(leaseExpired.status()).isEqualTo(SeriesStatus.ACTIVE);
        assertThat(leaseExpired.revision()).isZero();
        assertThat(leaseExpired.currentGame().reservation()).isNull();
        assertThat(leaseExpired.currentGame().status())
                .isEqualTo(SeriesGameStatus.SIMULATION_FAILED_RETRYABLE);
        assertThat(leaseExpired.currentGame().reason())
                .isEqualTo("SIMULATION_LEASE_EXPIRED");
        assertThat(leaseExpired.commandReceipts().get("simulate").completion())
                .isEqualTo(SeriesCommandCompletion.FAILED);
        assertThat(leaseExpired.commandReceipts().get("simulate").errorCode())
                .isEqualTo("SERIES_SIMULATION_LEASE_EXPIRED");
        assertThat(leaseExpired.commandReceipts().get("simulate").retryable()).isTrue();
        leaseClock.advance(Duration.ofSeconds(1));
        assertThat(leaseRepository.get(reserved.seriesId())).isEqualTo(leaseExpired);

        MutableClock protectionClock = new MutableClock(START);
        SeriesRepository protectionRepository = repository(protectionClock, 2);
        SeriesAggregate protectedByLease = withReservation(
                aggregate(protectionRepository, "protected"), protectionRepository);
        protectedByLease = protectedByLease.copy(protectedByLease.revision(),
                protectedByLease.status(), protectedByLease.terminalReason(),
                protectedByLease.score(), protectedByLease.games(),
                protectedByLease.consumedPicks(), protectedByLease.historyHash(),
                protectedByLease.winnerTeamCode(), protectedByLease.lastActivityAt(),
                START.plus(Duration.ofMinutes(1)), protectedByLease.commandReceipts());
        protectionRepository.create("protected-command", "protected-payload",
                protectedByLease);
        protectionClock.advance(Duration.ofMinutes(1));
        SeriesAggregate protectedRead = protectionRepository.get(
                protectedByLease.seriesId());
        assertThat(protectedRead.status()).isEqualTo(SeriesStatus.ACTIVE);
        assertThat(protectedRead.currentGame().reservation()).isNotNull();
    }

    @Test
    void differentSeriesMutationsRemainIsolatedAndSameSeriesMutationsAreAtomic()
            throws Exception {
        MutableClock clock = new MutableClock(START);
        SeriesRepository repository = repository(clock, 4);
        SeriesAggregate first = aggregate(repository, "first");
        SeriesAggregate second = aggregate(repository, "second");
        repository.create("first-command", "first-payload", first);
        repository.create("second-command", "second-payload", second);

        try (var executor = Executors.newFixedThreadPool(8)) {
            var calls = IntStream.range(0, 20).mapToObj(index -> executor.submit(() ->
                    repository.mutate(first.seriesId(), current -> {
                        SeriesAggregate updated = current.copy(current.revision() + 1,
                                current.status(), current.terminalReason(), current.score(),
                                current.games(), current.consumedPicks(), current.historyHash(),
                                current.winnerTeamCode(), current.lastActivityAt(),
                                current.expiresAt(), current.commandReceipts());
                        return new SeriesRepository.Mutation<>(updated, updated.revision());
                    }))).toList();
            for (var call : calls) call.get();
        }

        assertThat(repository.get(first.seriesId()).revision()).isEqualTo(20);
        assertThat(repository.get(second.seriesId()).revision()).isZero();
    }

    @Test
    void cleanupCannotDeleteAConcurrentMutationThatExtendedTheParentTtl() throws Exception {
        MutableClock clock = new MutableClock(START);
        CountDownLatch cleanupReached = new CountDownLatch(1);
        SeriesRepository repository = new SeriesRepository(
                clock, new SeriesLifecycleConfiguration(
                2, Duration.ofMinutes(120), Duration.ofMinutes(30),
                Duration.ofMinutes(5), 256),
                seriesId -> {
                    if (seriesId.equals("series-original")) cleanupReached.countDown();
                });
        SeriesAggregate original = aggregate(repository, "original");
        repository.create("original-command", "original-payload", original);
        CountDownLatch mutationInsideAtomicBoundary = new CountDownLatch(1);
        CountDownLatch allowMutationCommit = new CountDownLatch(1);

        try (var executor = Executors.newFixedThreadPool(2)) {
            var mutation = executor.submit(() -> repository.mutate(
                    original.seriesId(), current -> {
                        mutationInsideAtomicBoundary.countDown();
                        await(allowMutationCommit);
                        Instant activity = START.plus(Duration.ofMinutes(119));
                        SeriesAggregate updated = current.copy(current.revision() + 1,
                                current.status(), current.terminalReason(), current.score(),
                                current.games(), current.consumedPicks(), current.historyHash(),
                                current.winnerTeamCode(), activity,
                                repository.parentExpiresAt(activity),
                                current.commandReceipts());
                        return new SeriesRepository.Mutation<>(updated, updated);
                    }));
            assertThat(mutationInsideAtomicBoundary.await(5, TimeUnit.SECONDS)).isTrue();
            clock.advance(Duration.ofMinutes(120));
            var create = executor.submit(() -> repository.create(
                    "replacement-command", "replacement-payload",
                    aggregate(repository, "replacement")));
            assertThat(cleanupReached.await(5, TimeUnit.SECONDS)).isTrue();
            allowMutationCommit.countDown();

            assertThat(mutation.get(5, TimeUnit.SECONDS).revision()).isOne();
            assertThat(create.get(5, TimeUnit.SECONDS).replayed()).isFalse();
        }

        SeriesAggregate live = repository.get(original.seriesId());
        assertThat(live.revision()).isOne();
        assertThat(live.lastActivityAt()).isEqualTo(START.plus(Duration.ofMinutes(119)));
        assertThat(live.expiresAt()).isEqualTo(START.plus(Duration.ofMinutes(239)));
        assertThat(repository.create("original-command", "original-payload",
                aggregate(repository, "unused-replay")).aggregate()).isEqualTo(live);
    }

    private static SeriesRepository repository(MutableClock clock, int maximum) {
        return new SeriesRepository(clock, new SeriesLifecycleConfiguration(
                maximum, Duration.ofMinutes(120), Duration.ofMinutes(30),
                Duration.ofMinutes(5), 256));
    }

    private static SeriesAggregate aggregate(SeriesRepository repository, String identity) {
        Instant now = repository.now();
        String historyHash = SeriesIdentity.historyHash(0, Set.of());
        SeriesGame game = new SeriesGame(
                "game-" + identity, 1, "GEN", "T1", TeamSide.BLUE, 73L,
                List.of(), historyHash, SeriesGameStatus.DRAFT_PENDING, null,
                0, null, null, null, null, null);
        LinkedHashMap<String, Integer> score = new LinkedHashMap<>();
        score.put("GEN", 0);
        score.put("T1", 0);
        return new SeriesAggregate(
                "series-" + identity, 0, SeriesStatus.ACTIVE, null, SeriesFormat.BO3,
                "GEN", "T1", "GEN", "GEN", "73", 73L, score, List.of(game),
                Set.of(), historyHash, null, now, now, repository.parentExpiresAt(now),
                Map.of());
    }

    private static SeriesAggregate withActiveChild(
            SeriesAggregate aggregate, SeriesRepository repository
    ) {
        Instant now = repository.now();
        SeriesGame game = aggregate.currentGame();
        SeriesChildDraft child = new SeriesChildDraft(
                "draft-child", 1, 0, PlayerDraftSessionStatus.ACTIVE,
                now, now, repository.childExpiresAt(now, aggregate.expiresAt()),
                mock(PlayerControlledDraftEngine.Progress.class));
        SeriesGame changed = new SeriesGame(
                game.gameId(), game.gameNumber(), game.blueTeamCode(), game.redTeamCode(),
                game.controlledSide(), game.matchSeed(), game.historyBefore(),
                game.historyBeforeHash(), SeriesGameStatus.DRAFT_ACTIVE, null, 1,
                child, null, null, null, null);
        return aggregate.replaceCurrentGame(changed, aggregate.revision(),
                aggregate.lastActivityAt(), aggregate.expiresAt(), Map.of());
    }

    private static SeriesAggregate withReservation(
            SeriesAggregate aggregate, SeriesRepository repository
    ) {
        Instant now = repository.now();
        SeriesGame game = aggregate.currentGame();
        SeriesSimulationReservation reservation = new SeriesSimulationReservation(
                "token", "simulate", "payload", aggregate.revision(), 0,
                now, repository.reservationExpiresAt(now), "input-binding");
        SeriesGame changed = new SeriesGame(
                game.gameId(), game.gameNumber(), game.blueTeamCode(), game.redTeamCode(),
                game.controlledSide(), game.matchSeed(), game.historyBefore(),
                game.historyBeforeHash(), SeriesGameStatus.SIMULATION_IN_PROGRESS,
                null, 0, null, reservation, null, null, null);
        SeriesCommandReceipt command = new SeriesCommandReceipt(
                "simulate", "SIMULATE", "payload", SeriesCommandCompletion.IN_PROGRESS,
                aggregate.revision(), SeriesStatus.ACTIVE, game.gameNumber(), game.gameId(),
                SeriesGameStatus.SIMULATION_IN_PROGRESS, null, null, null, null,
                "token", null, null, false);
        return aggregate.replaceCurrentGame(changed, aggregate.revision(),
                aggregate.lastActivityAt(), aggregate.expiresAt(),
                Map.of(command.commandId(), command));
    }

    private static void await(CountDownLatch latch) {
        try {
            assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new AssertionError(error);
        }
    }

    private static final class MutableClock extends Clock {
        private volatile Instant instant;

        private MutableClock(Instant instant) { this.instant = instant; }

        void advance(Duration duration) { instant = instant.plus(duration); }

        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return instant; }
    }
}
