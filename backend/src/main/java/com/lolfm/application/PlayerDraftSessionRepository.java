package com.lolfm.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import org.springframework.stereotype.Repository;

/** Bounded, process-local, thread-safe V1 repository with injected time. */
@Repository
final class PlayerDraftSessionRepository {
    static final int DEFAULT_MAX_SESSIONS = 128;
    static final Duration DEFAULT_TTL = Duration.ofMinutes(30);

    private final ConcurrentHashMap<String, PlayerDraftSession> sessions =
            new ConcurrentHashMap<>();
    private final Clock clock;
    private final int maximumSessions;
    private final Duration ttl;
    private final Object capacityBoundary = new Object();

    @org.springframework.beans.factory.annotation.Autowired
    PlayerDraftSessionRepository(Clock clock) {
        this(clock, DEFAULT_MAX_SESSIONS, DEFAULT_TTL);
    }

    PlayerDraftSessionRepository(Clock clock, int maximumSessions, Duration ttl) {
        this.clock = Objects.requireNonNull(clock, "clock");
        if (maximumSessions < 1) throw new IllegalArgumentException("maximumSessions");
        this.maximumSessions = maximumSessions;
        this.ttl = Objects.requireNonNull(ttl, "ttl");
    }

    Instant now() {
        return clock.instant();
    }

    Instant expiresAt(Instant createdAt) {
        return createdAt.plus(ttl);
    }

    PlayerDraftSession create(PlayerDraftSession session) {
        Objects.requireNonNull(session, "session");
        synchronized (capacityBoundary) {
            cleanupTerminalAndExpired();
            if (sessions.size() >= maximumSessions) {
                throw new RepositoryFailure("PLAYER_DRAFT_SESSION_CAPACITY_REACHED");
            }
            if (sessions.putIfAbsent(session.sessionId(), session) != null) {
                throw new RepositoryFailure("PLAYER_DRAFT_SESSION_ID_COLLISION");
            }
        }
        return session;
    }

    PlayerDraftSession get(String sessionId) {
        AtomicReference<PlayerDraftSession> found = new AtomicReference<>();
        sessions.computeIfPresent(sessionId, (key, current) -> {
            PlayerDraftSession value = expire(current);
            found.set(value);
            return value;
        });
        PlayerDraftSession result = found.get();
        if (result == null) throw new RepositoryFailure("PLAYER_DRAFT_SESSION_NOT_FOUND");
        return result;
    }

    <T> T mutate(String sessionId, Function<PlayerDraftSession, Mutation<T>> operation) {
        AtomicReference<T> result = new AtomicReference<>();
        AtomicReference<RuntimeException> failure = new AtomicReference<>();
        sessions.compute(sessionId, (key, current) -> {
            if (current == null) {
                failure.set(new RepositoryFailure("PLAYER_DRAFT_SESSION_NOT_FOUND"));
                return null;
            }
            PlayerDraftSession live = expire(current);
            if (live.status() == PlayerDraftSessionStatus.EXPIRED) {
                failure.set(new RepositoryFailure("PLAYER_DRAFT_SESSION_EXPIRED"));
                return live;
            }
            try {
                Mutation<T> mutation = operation.apply(live);
                result.set(mutation.result());
                return mutation.session();
            } catch (RuntimeException error) {
                failure.set(error);
                return live;
            }
        });
        if (failure.get() != null) throw failure.get();
        return result.get();
    }

    private PlayerDraftSession expire(PlayerDraftSession session) {
        if (session.status() != PlayerDraftSessionStatus.CANCELLED
                && session.status() != PlayerDraftSessionStatus.EXPIRED
                && !clock.instant().isBefore(session.expiresAt())) {
            return session.withStatus(PlayerDraftSessionStatus.EXPIRED);
        }
        return session;
    }

    private void cleanupTerminalAndExpired() {
        Instant now = clock.instant();
        ArrayList<String> removable = new ArrayList<>();
        sessions.forEach((id, session) -> {
            if (session.status() == PlayerDraftSessionStatus.CANCELLED
                    || session.status() == PlayerDraftSessionStatus.EXPIRED
                    || !now.isBefore(session.expiresAt())) {
                removable.add(id);
            }
        });
        removable.forEach(id -> sessions.computeIfPresent(id, (key, current) ->
                current.status() == PlayerDraftSessionStatus.CANCELLED
                        || current.status() == PlayerDraftSessionStatus.EXPIRED
                        || !now.isBefore(current.expiresAt()) ? null : current));
    }

    int storedSessionCount() {
        return sessions.size();
    }

    record Mutation<T>(PlayerDraftSession session, T result) {
    }

    static final class RepositoryFailure extends RuntimeException {
        RepositoryFailure(String code) {
            super(code);
        }
    }
}
