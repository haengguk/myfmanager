package com.lolfm.career;

import jakarta.annotation.PreDestroy;
import java.util.Objects;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Local bounded worker; the durable job remains authoritative across restarts. */
@Component
final class CareerCompetitionBackgroundJobExecutor
        implements CareerCompetitionBackgroundExecutionPort {
    private final CareerCompetitionExecutionService execution;
    private final ThreadPoolExecutor worker;
    private final boolean enabled;

    CareerCompetitionBackgroundJobExecutor(
            CareerCompetitionExecutionService execution,
            @Value("${lolfm.career.competition.background.enabled:true}")
            boolean enabled
    ) {
        this.execution = Objects.requireNonNull(execution, "execution");
        this.enabled = enabled;
        ThreadFactory threads = runnable -> {
            Thread thread = new Thread(runnable,
                    "career-competition-background-worker");
            thread.setDaemon(true);
            return thread;
        };
        this.worker = new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(32), threads,
                new ThreadPoolExecutor.AbortPolicy());
    }

    @Override
    public boolean submit(String jobId) {
        if (!enabled) return false;
        try {
            worker.execute(() -> execution.executeAutoJob(jobId));
            return true;
        } catch (java.util.concurrent.RejectedExecutionException unavailable) {
            return false;
        }
    }

    @PreDestroy
    void close() {
        worker.shutdownNow();
    }
}
