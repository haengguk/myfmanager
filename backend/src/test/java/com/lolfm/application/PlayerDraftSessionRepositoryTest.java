package com.lolfm.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import com.lolfm.draft.PlayerControlledDraftEngine;
import com.lolfm.simulator.GameEndReason;
import com.lolfm.simulator.SimulationRandomFingerprint;
import com.lolfm.simulator.TeamSide;
import java.lang.reflect.RecordComponent;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class PlayerDraftSessionRepositoryTest {
    @Test
    void sessionOwnsOnlyCompactBoundedReceiptAndNoFullMatchGraph() {
        assertThat(java.util.Arrays.stream(PlayerDraftSession.class.getRecordComponents())
                .map(RecordComponent::getType))
                .doesNotContain(MatchEngineV1Output.class, MatchEngineV1Output.TimelineV1.class);
        assertThat(java.util.Arrays.stream(SimulationReceipt.class.getRecordComponents())
                .map(RecordComponent::getType))
                .allMatch(type -> type.isPrimitive() || type.isEnum() || type == String.class);

        SimulationReceipt receipt = receipt('a');
        assertThat(receipt.canonicalBytes().length)
                .isPositive().isLessThanOrEqualTo(SimulationReceipt.MAX_CANONICAL_BYTES);
        assertThat(receipt.canonicalText()).startsWith(
                "receiptSchema=PLAYER_DRAFT_SIMULATION_RECEIPT_V1\n");
    }

    @Test
    void injectedClockExpiresSessionsAndCapacityStaysBounded() {
        MutableClock clock = new MutableClock(Instant.parse("2026-08-27T00:00:00Z"));
        PlayerDraftSessionRepository repository = new PlayerDraftSessionRepository(
                clock, 1, Duration.ofMinutes(5));
        repository.create(session(repository, "one"));

        assertThatThrownBy(() -> repository.create(session(repository, "two")))
                .isInstanceOf(PlayerDraftSessionRepository.RepositoryFailure.class)
                .hasMessage("PLAYER_DRAFT_SESSION_CAPACITY_REACHED");

        clock.advance(Duration.ofMinutes(5));
        assertThat(repository.get("one").status()).isEqualTo(PlayerDraftSessionStatus.EXPIRED);
        assertThat(repository.create(session(repository, "two")).sessionId()).isEqualTo("two");
    }

    @Test
    void computeSerializesConcurrentMutationsWithoutStaticOrCrossSessionState() throws Exception {
        MutableClock clock = new MutableClock(Instant.parse("2026-08-27T00:00:00Z"));
        PlayerDraftSessionRepository repository = new PlayerDraftSessionRepository(
                clock, 4, Duration.ofMinutes(5));
        repository.create(session(repository, "one"));
        repository.create(session(repository, "two"));
        try (var executor = Executors.newFixedThreadPool(8)) {
            java.util.List<Callable<Long>> calls = java.util.stream.IntStream.range(0, 20)
                    .mapToObj(index -> (Callable<Long>) () -> repository.mutate("one", current -> {
                        PlayerDraftSession updated = new PlayerDraftSession(
                                current.sessionId(), current.revision() + 1, current.status(),
                                current.blueTeamCode(), current.redTeamCode(),
                                current.controlledSide(), current.matchSeed(), current.createdAt(),
                                current.expiresAt(), current.progress(), current.actionReceipts(),
                                current.simulationReceipt());
                        return new PlayerDraftSessionRepository.Mutation<>(
                                updated, updated.revision());
                    })).toList();
            executor.invokeAll(calls).forEach(future -> {
                try {
                    assertThat(future.get()).isBetween(1L, 20L);
                } catch (Exception error) {
                    throw new AssertionError(error);
                }
            });
        }

        assertThat(repository.get("one").revision()).isEqualTo(20);
        assertThat(repository.get("two").revision()).isZero();
    }

    @Test
    void concurrentCreateNeverExceedsExactCapacity() throws Exception {
        MutableClock clock = new MutableClock(Instant.parse("2026-08-27T00:00:00Z"));
        assertConcurrentCapacity(clock, 1, 64);
        assertConcurrentCapacity(clock, 5, 100);
    }

    @Test
    void collisionAndTerminalCleanupDoNotLeakCapacityAndInstancesAreIsolated() {
        MutableClock clock = new MutableClock(Instant.parse("2026-08-27T00:00:00Z"));
        PlayerDraftSessionRepository first = new PlayerDraftSessionRepository(
                clock, 2, Duration.ofMinutes(5));
        first.create(session(first, "same"));
        assertThatThrownBy(() -> first.create(session(first, "same")))
                .isInstanceOf(PlayerDraftSessionRepository.RepositoryFailure.class)
                .hasMessage("PLAYER_DRAFT_SESSION_ID_COLLISION");
        first.create(session(first, "other"));
        assertThat(first.storedSessionCount()).isEqualTo(2);

        first.mutate("same", current -> new PlayerDraftSessionRepository.Mutation<>(
                current.withStatus(PlayerDraftSessionStatus.CANCELLED), null));
        first.create(session(first, "replacement"));
        assertThat(first.storedSessionCount()).isEqualTo(2);

        PlayerDraftSessionRepository second = new PlayerDraftSessionRepository(
                clock, 1, Duration.ofMinutes(5));
        second.create(session(second, "independent"));
        assertThat(second.storedSessionCount()).isOne();
        assertThat(first.storedSessionCount()).isEqualTo(2);
    }

    @Test
    void expiredCleanupRacingWithCreatesStillHonorsMaximum() throws Exception {
        MutableClock clock = new MutableClock(Instant.parse("2026-08-27T00:00:00Z"));
        PlayerDraftSessionRepository repository = new PlayerDraftSessionRepository(
                clock, 3, Duration.ofMinutes(1));
        repository.create(session(repository, "expired"));
        clock.advance(Duration.ofMinutes(1));

        RaceResult result = raceCreates(repository, 80);
        assertThat(result.successes()).isEqualTo(3);
        assertThat(result.capacityFailures()).isEqualTo(77);
        assertThat(result.maximumObserved()).isLessThanOrEqualTo(3);
        assertThat(repository.storedSessionCount()).isEqualTo(3);
    }

    private static void assertConcurrentCapacity(
            MutableClock clock, int maximumSessions, int requests
    ) throws Exception {
        PlayerDraftSessionRepository repository = new PlayerDraftSessionRepository(
                clock, maximumSessions, Duration.ofMinutes(5));
        RaceResult result = raceCreates(repository, requests);
        assertThat(result.successes()).isEqualTo(maximumSessions);
        assertThat(result.capacityFailures()).isEqualTo(requests - maximumSessions);
        assertThat(result.maximumObserved()).isLessThanOrEqualTo(maximumSessions);
        assertThat(repository.storedSessionCount()).isEqualTo(maximumSessions);
    }

    private static RaceResult raceCreates(
            PlayerDraftSessionRepository repository, int requests
    ) throws Exception {
        CountDownLatch ready = new CountDownLatch(requests);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger successes = new AtomicInteger();
        AtomicInteger capacityFailures = new AtomicInteger();
        AtomicInteger maximumObserved = new AtomicInteger();
        try (var executor = Executors.newFixedThreadPool(requests)) {
            var futures = IntStream.range(0, requests).mapToObj(index ->
                    executor.submit(() -> {
                        ready.countDown();
                        start.await();
                        try {
                            repository.create(session(repository, "concurrent-" + index));
                            successes.incrementAndGet();
                        } catch (PlayerDraftSessionRepository.RepositoryFailure error) {
                            if (!"PLAYER_DRAFT_SESSION_CAPACITY_REACHED".equals(
                                    error.getMessage())) throw error;
                            capacityFailures.incrementAndGet();
                        } finally {
                            maximumObserved.accumulateAndGet(
                                    repository.storedSessionCount(), Math::max);
                        }
                        return null;
                    })).toList();
            ready.await();
            start.countDown();
            for (var future : futures) future.get();
        }
        return new RaceResult(successes.get(), capacityFailures.get(), maximumObserved.get());
    }

    private static PlayerDraftSession session(
            PlayerDraftSessionRepository repository, String id
    ) {
        Instant created = repository.now();
        return new PlayerDraftSession(
                id, 0, PlayerDraftSessionStatus.ACTIVE, "GEN", "T1", TeamSide.BLUE,
                73L, created, repository.expiresAt(created),
                mock(PlayerControlledDraftEngine.Progress.class), Map.of(), null);
    }

    private static SimulationReceipt receipt(char hashCharacter) {
        String hash = String.valueOf(hashCharacter).repeat(64);
        return new SimulationReceipt(
                SimulationReceipt.SCHEMA, "PLAYER_CONTROLLED_DRAFT:GEN:T1:GAME:1:SEED:73",
                "policy", hash, "PRODUCTION_MATCHUP_COMPOSITION_V1", hash,
                "MATCH_SIMULATOR_ENGINE_IMPLEMENTATION_V9",
                "MATCH_SIMULATOR_PRE_JUNGLE_RULES_V3", hash, hash, hash, hash, hash,
                hash, "PLAYER_CONTROLLED_DRAFT_V1", hash, hash, hash, hash, hash,
                SimulationRandomFingerprint.SCHEMA, 100L, hash,
                SimulationRandomFingerprint.TRACE_HASH_ALGORITHM,
                TeamSide.BLUE, 1800, GameEndReason.NEXUS_DESTROYED);
    }

    private record RaceResult(
            int successes, int capacityFailures, int maximumObserved
    ) {
    }

    private static final class MutableClock extends Clock {
        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        void advance(Duration duration) {
            instant = instant.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
