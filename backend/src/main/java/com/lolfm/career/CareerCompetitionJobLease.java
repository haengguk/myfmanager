package com.lolfm.career;

import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Heartbeats run independently of individual games and completion verification. */
@Component
public final class CareerCompetitionJobLease {
    static final String FENCE_REJECTED = "COMPETITION_AUTO_JOB_FENCE_REJECTED";
    private final Duration duration;
    private final long heartbeatMillis;
    private final ScheduledExecutorService scheduler;

    @Autowired
    public CareerCompetitionJobLease(
            @Value("${lolfm.career.competition.lease.duration-millis:300000}") long durationMillis,
            @Value("${lolfm.career.competition.lease.heartbeat-millis:60000}") long heartbeatMillis
    ) {
        this(durationMillis, heartbeatMillis, Executors.newSingleThreadScheduledExecutor(task -> {
            Thread thread = new Thread(task, "career-competition-lease-heartbeat");
            thread.setDaemon(true);
            return thread;
        }));
    }

    CareerCompetitionJobLease(long durationMillis, long heartbeatMillis,
                              ScheduledExecutorService scheduler) {
        if (heartbeatMillis <= 0 || durationMillis <= heartbeatMillis) {
            throw new IllegalArgumentException("Competition heartbeat must precede lease expiry");
        }
        this.duration = Duration.ofMillis(durationMillis);
        this.heartbeatMillis = heartbeatMillis;
        this.scheduler = scheduler;
    }

    Duration duration() { return duration; }

    Guard maintain(BooleanSupplier renew) {
        return new Guard(renew);
    }

    final class Guard implements AutoCloseable {
        private final AtomicBoolean owned = new AtomicBoolean(true);
        private final ScheduledFuture<?> future;

        private Guard(BooleanSupplier renew) {
            future = scheduler.scheduleWithFixedDelay(() -> {
                synchronized (this) {
                    if (!owned.get()) return;
                    try {
                        if (!renew.getAsBoolean()) owned.set(false);
                    } catch (RuntimeException failure) {
                        // A failed renewal is sticky: this worker cannot regain authority.
                        owned.set(false);
                    }
                }
            }, heartbeatMillis, heartbeatMillis, TimeUnit.MILLISECONDS);
        }

        synchronized void requireOwned() {
            if (!owned.get() || scheduler.isShutdown() || Thread.currentThread().isInterrupted()) {
                throw new IllegalStateException(FENCE_REJECTED);
            }
        }

        /** Serialize the short fenced DB commit with renewal success/failure. */
        synchronized <T> T complete(java.util.function.Supplier<T> commit) {
            requireOwned();
            return commit.get();
        }

        @Override public synchronized void close() {
            owned.set(false);
            future.cancel(false);
        }
    }

    @PreDestroy
    void close() { scheduler.shutdownNow(); }
}
