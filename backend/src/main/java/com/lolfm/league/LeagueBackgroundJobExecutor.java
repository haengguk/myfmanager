package com.lolfm.league;

import com.lolfm.simulator.SimulationInstrumentation;
import jakarta.annotation.PreDestroy;
import java.util.Objects;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Value;

/** Local single-node background pump with a bounded queue and fixed worker budget. */
@Component
final class LeagueBackgroundJobExecutor implements LeagueBackgroundExecutionPort {
    private final LeagueSimulationApplicationPort jobs;
    private final LeagueRelationalStore store;
    private final ThreadPoolExecutor executor;
    private final boolean enabled;

    LeagueBackgroundJobExecutor(
            LeagueSimulationApplicationPort jobs,
            LeagueRelationalStore store,
            @Value("${lolfm.league.background.enabled:true}") boolean enabled
    ) {
        this.jobs = Objects.requireNonNull(jobs, "jobs");
        this.store = Objects.requireNonNull(store, "store");
        this.enabled = enabled;
        ThreadFactory threads = runnable -> {
            Thread thread = new Thread(runnable, "league-v1-background-worker");
            thread.setDaemon(true);
            return thread;
        };
        this.executor = new ThreadPoolExecutor(1, 1,
                0L, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(32), threads,
                new ThreadPoolExecutor.AbortPolicy());
    }

    @Override
    public boolean submit(String ownerId) {
        if (!enabled) return false;
        try {
            executor.execute(() -> drain(ownerId));
            return true;
        } catch (java.util.concurrent.RejectedExecutionException unavailable) {
            return false;
        }
    }

    private void drain(String ownerId) {
        jobs.recover();
        while (!Thread.currentThread().isInterrupted()) {
            var executed = jobs.executeQueued(ownerId,
                    LeagueV1OperationalConfiguration.defaults()
                            .defaultMaxParallelFixtures(),
                    SimulationInstrumentation.disabled());
            store.drainOutbox(100);
            if (executed.isEmpty()) return;
        }
    }

    @PreDestroy
    void close() {
        executor.shutdownNow();
    }
}
