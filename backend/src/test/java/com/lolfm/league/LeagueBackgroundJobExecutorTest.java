package com.lolfm.league;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

class LeagueBackgroundJobExecutorTest {
    @Test
    void explicitBackgroundRunRecoversExpiredRuntimeLeasesBeforeLeasingWork()
            throws Exception {
        LeagueSimulationApplicationPort jobs = mock(LeagueSimulationApplicationPort.class);
        LeagueRelationalStore store = mock(LeagueRelationalStore.class);
        CountDownLatch executed = new CountDownLatch(1);
        when(jobs.recover()).thenReturn(new LeagueSimulationApplicationPort.RecoveryResult(
                1, 0, 0, 0));
        when(jobs.executeQueued(eq("api-owner"), eq(2), any()))
                .thenAnswer(ignored -> {
                    executed.countDown();
                    return List.of();
                });
        when(store.drainOutbox(anyInt())).thenReturn(0);
        LeagueBackgroundJobExecutor executor = new LeagueBackgroundJobExecutor(
                jobs, store, true);

        try {
            assertThat(executor.submit("api-owner")).isTrue();
            assertThat(executed.await(5, TimeUnit.SECONDS)).isTrue();
            InOrder order = inOrder(jobs);
            order.verify(jobs).recover();
            order.verify(jobs).executeQueued(eq("api-owner"), eq(2), any());
        } finally {
            executor.close();
        }
    }
}
