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
        return syntheticCompletion(binding, winnerTeamCode, 0);
    }
    public static CareerCompetitionRelationalStore.CompletionResult applySyntheticScore(CareerCompetitionRelationalStore store, CareerCompetitionSeriesBindingV1 binding, String winner, int loserWins) {
        return store.applyVerifiedCompletion(syntheticCompletion(binding, winner, loserWins));
    }
    private static VerifiedCompetitionFixtureCompletion syntheticCompletion(CareerCompetitionSeriesBindingV1 binding, String winnerTeamCode, int loserWins) {
        int wins = binding.seriesFormat().winsRequired();
        boolean firstWon = binding.firstTeamCode().equals(winnerTeamCode);
        String loser = firstWon ? binding.secondTeamCode() : binding.firstTeamCode();
        var games = new java.util.ArrayList<com.lolfm.league.LeagueFixtureGameReceiptV1>();
        var history = new com.lolfm.draft.SeriesDraftHistory(binding.initialHistoryPicks());
        String blue = binding.game1BlueTeamCode(), red = binding.game1RedTeamCode();
        for (int number = 1; number <= wins + loserWins; number++) {
            games.add(com.lolfm.league.LeagueAutomatedSeriesRunnerTest.syntheticGame(
                    binding.boundSeriesId() + ":" + number, number, blue, red, 100 + number, history, number <= loserWins ? loser : winnerTeamCode));
            blue = binding.loserChoosesNextSide() ? loser : red;
            red = blue.equals(binding.firstTeamCode()) ? binding.secondTeamCode() : binding.firstTeamCode();
        }
        CareerCompetitionFixtureCompletionReceiptV1 receipt =
                new CareerCompetitionFixtureCompletionReceiptV1(
                        CareerCompetitionFixtureCompletionReceiptV1.SCHEMA,
                        binding.bindingHash(), binding.careerId(), binding.seasonYear(),
                        binding.competitionId(), binding.fixtureId(), binding.matchId(),
                        binding.boundSeriesId(), binding.firstTeamCode(),
                        binding.secondTeamCode(), firstWon ? wins : loserWins,
                        firstWon ? loserWins : wins, winnerTeamCode, loser, 1800 * (wins + loserWins),
                        games, null);
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

    public static CareerCompetitionFixtureCompletionReceiptV1 verifyAuto(CareerCompetitionSeriesBindingV1 binding,
            com.lolfm.league.CareerCompetitionAutomatedSeriesKernel.CompletedSeriesEvidence evidence) {
        return CareerCompetitionFixtureCompletionReceiptV1.verifyAutomated(binding, evidence).receipt();
    }
    public static CareerCompetitionFixtureCompletionReceiptV1 verifyPlayer(CareerCompetitionSeriesBindingV1 binding,
            com.lolfm.league.LeaguePlayerSeriesKernelPort.CompletedSeriesEvidence evidence) {
        return CareerCompetitionFixtureCompletionReceiptV1.verifyPlayer(binding, evidence).receipt();
    }
    /** Prepares one representative engine fixture; no season simulation or production bypass API. */
    public static CareerCompetitionSeriesBindingV1 engineBinding(CareerCompetitionRelationalStore store, String career,
            String match, String format, String first, String second, String managed,
            com.lolfm.league.LeagueSeasonFrozenSnapshot snapshot, String provenance,
            java.util.Set<com.lolfm.champion.ChampionId> inherited) {
        store.jdbc.update("UPDATE career_competition_fixture SET series_format = ?, first_team_code = ?, second_team_code = ?, execution_mode = ?, selection_right_owner = ?, side_selection_policy = ? WHERE career_id = ? AND calendar_season_year = 2027 AND competition_id = 'LCK_CUP' AND match_id = ?",
                format, first, second, managed.equals(first) || managed.equals(second) ? "PLAYER_CONTROLLED" : "FULL_AUTO", first, CareerDomesticCompetition.SIDE_POLICY, career, match);
        store.refreshInstanceHash(career, 2027, "LCK_CUP"); store.refreshCycleHash(career, 2027);
        var cycle = store.load(career, 2027);
        var fixture = cycle.fixtures().stream().filter(f -> f.competitionId().equals("LCK_CUP") && f.matchId().equals(match)).findFirst().orElseThrow();
        var instance = cycle.competitions().stream().filter(c -> c.competitionId().equals("LCK_CUP")).findFirst().orElseThrow();
        var binding = CareerCompetitionSeriesBindingV1.create(cycle, instance, fixture, managed,
                CareerCompetitionRules.RESOURCE_HASH, snapshot, provenance, inherited);
        store.jdbc.update("INSERT INTO career_competition_series_binding(binding_hash, career_id, calendar_season_year, competition_id, match_id, fixture_id, series_id, execution_mode, binding_schema, binding_canonical, lifecycle_status, created_at, updated_at) VALUES (?, ?, 2027, 'LCK_CUP', ?, ?, ?, ?, ?, ?, 'CREATED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                binding.bindingHash(), career, match, binding.fixtureId(), binding.boundSeriesId(), binding.executionMode(), CareerCompetitionSeriesBindingV1.SCHEMA, binding.canonicalText());
        return binding;
    }

    /** Recreates a V2 cycle identity without changing its already materialized Cup graph. */
    public static void restorePreviousRuleIdentity(CareerCompetitionRelationalStore store, String career, int year) {
        store.jdbc.update("UPDATE career_competition_cycle SET rule_version = ?, rule_resource_hash = ?, game_policy_version = ? WHERE career_id = ? AND calendar_season_year = ?",
                CareerCompetitionRules.PREVIOUS_VERSION, CareerCompetitionRules.PREVIOUS_RESOURCE_HASH,
                CareerCompetitionRules.PREVIOUS_POLICY, career, year);
        var previous = store.rules.rule("LCK_PLAYOFFS", CareerCompetitionRules.PREVIOUS_VERSION);
        store.jdbc.update("UPDATE career_competition_instance SET rule_status = ?, lifecycle_status = 'BLOCKED', blocking_reason = ? WHERE career_id = ? AND calendar_season_year = ? AND competition_id = 'LCK_PLAYOFFS'",
                previous.ruleStatus(), previous.blockingReason(), career, year);
        for (var instance : store.rules.competitions()) store.refreshInstanceHash(career, year, instance.competitionId());
        store.refreshCycleHash(career, year);
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
