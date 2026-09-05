package com.lolfm.career;

import java.util.List;

/** Test-only bridge for legacy aggregate transition fixtures without Series evidence. */
public final class CareerCompetitionTestSupport {
    private CareerCompetitionTestSupport() {}

    public static CareerCompetitionRelationalStore.CompletionResult applyCompletion(
            CareerCompetitionRelationalStore store,
            String careerId,
            int seasonYear,
            String competitionId,
            String matchId,
            String seriesId,
            String firstTeamCode,
            String secondTeamCode,
            String winnerTeamCode,
            String receiptHash
    ) {
        return store.applyCompletionForTesting(careerId, seasonYear, competitionId,
                matchId, seriesId, firstTeamCode, secondTeamCode, winnerTeamCode,
                receiptHash);
    }

    public static CareerCompetitionRelationalStore.CompletionResult
            applySyntheticVerifiedCompletion(
            CareerCompetitionRelationalStore store,
            CareerCompetitionSeriesBindingV1 binding,
            String winnerTeamCode
    ) {
        return store.applyVerifiedCompletion(syntheticCompletion(binding, winnerTeamCode));
    }

    private static VerifiedCompetitionFixtureCompletion syntheticCompletion(
            CareerCompetitionSeriesBindingV1 binding, String winnerTeamCode) {
        int wins = binding.seriesFormat().winsRequired();
        boolean firstWon = binding.firstTeamCode().equals(winnerTeamCode);
        String loser = firstWon ? binding.secondTeamCode() : binding.firstTeamCode();
        CareerCompetitionFixtureCompletionReceiptV1 receipt =
                new CareerCompetitionFixtureCompletionReceiptV1(
                        CareerCompetitionFixtureCompletionReceiptV1.SCHEMA,
                        binding.bindingHash(), binding.careerId(), binding.seasonYear(),
                        binding.competitionId(), binding.fixtureId(), binding.matchId(),
                        binding.boundSeriesId(), binding.firstTeamCode(),
                        binding.secondTeamCode(), firstWon ? wins : 0,
                        firstWon ? 0 : wins, winnerTeamCode, loser, 1800,
                        List.of(), null);
        return new VerifiedCompetitionFixtureCompletion(receipt);
    }
    /** Isolates durable orchestration from the already separately tested engine verifier. */
    public static void withSyntheticVerification(Runnable duringVerification,
            java.util.function.Consumer<java.util.concurrent.atomic.AtomicInteger> checks) {
        var calls = new java.util.concurrent.atomic.AtomicInteger();
        try (var verifier = org.mockito.Mockito.mockStatic(
                CareerCompetitionFixtureCompletionReceiptV1.class, invocation -> {
                    if (invocation.getMethod().getName().equals("verifyAutomated")
                            || invocation.getMethod().getName().equals("verifyPlayer")) {
                        calls.incrementAndGet();
                        duringVerification.run();
                        CareerCompetitionSeriesBindingV1 binding = invocation.getArgument(0);
                        return syntheticCompletion(binding, binding.firstTeamCode());
                    }
                    return invocation.callRealMethod();
                })) {
            checks.accept(calls);
        }
    }

    public static ControlledLease controlledLease() { return new ControlledLease(); }

    public static final class ControlledLease implements AutoCloseable {
        public final java.util.concurrent.ScheduledExecutorService scheduler =
                org.mockito.Mockito.mock(java.util.concurrent.ScheduledExecutorService.class);
        public final java.util.List<java.util.concurrent.ScheduledFuture<?>> tasks = new java.util.ArrayList<>();
        private Runnable heartbeat;
        public final CareerCompetitionJobLease leases;

        private ControlledLease() {
            org.mockito.Mockito.when(scheduler.scheduleWithFixedDelay(
                    org.mockito.ArgumentMatchers.any(Runnable.class),
                    org.mockito.ArgumentMatchers.eq(60000L), org.mockito.ArgumentMatchers.eq(60000L),
                    org.mockito.ArgumentMatchers.eq(java.util.concurrent.TimeUnit.MILLISECONDS)))
                    .thenAnswer(invocation -> {
                        heartbeat = invocation.getArgument(0);
                        var task = org.mockito.Mockito.mock(java.util.concurrent.ScheduledFuture.class);
                        tasks.add(task);
                        return task;
                    });
            leases = new CareerCompetitionJobLease(300000, 60000, scheduler);
        }
        public void pulse() { heartbeat.run(); }
        @Override public void close() {
            leases.close();
            tasks.forEach(task -> org.mockito.Mockito.verify(task).cancel(false));
        }
    }
}
