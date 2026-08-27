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
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;

class PlayerDraftSessionRepositoryTest {
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
                                current.simulation());
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

    private static PlayerDraftSession session(
            PlayerDraftSessionRepository repository, String id
    ) {
        Instant created = repository.now();
        return new PlayerDraftSession(
                id, 0, PlayerDraftSessionStatus.ACTIVE, "GEN", "T1", TeamSide.BLUE,
                73L, created, repository.expiresAt(created),
                mock(PlayerControlledDraftEngine.Progress.class), Map.of(), null);
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
