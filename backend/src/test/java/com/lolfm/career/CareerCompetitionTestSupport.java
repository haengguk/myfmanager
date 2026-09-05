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
        return store.applyVerifiedCompletion(
                new VerifiedCompetitionFixtureCompletion(receipt));
    }
}
