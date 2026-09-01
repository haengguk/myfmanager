package com.lolfm.league;

/** Structured outcome; blocked runs never carry a standings-applicable token. */
public record LeagueAutomatedSeriesRunResult(
        Status status,
        String failureReason,
        int gameExecutionCount,
        LeagueFixtureCompletionReceiptV1 receipt,
        VerifiedLeagueFixtureCompletion verifiedCompletion
) {
    public enum Status { COMPLETED, BLOCKED }

    public LeagueAutomatedSeriesRunResult {
        if (gameExecutionCount < 0) throw new IllegalArgumentException("gameExecutionCount");
        if (status == Status.COMPLETED) {
            if (failureReason != null || receipt == null || verifiedCompletion == null) {
                throw new IllegalArgumentException("Completed runner result invariant");
            }
        } else if (failureReason == null || failureReason.isBlank()
                || receipt != null || verifiedCompletion != null) {
            throw new IllegalArgumentException("Blocked runner result invariant");
        }
    }

    static LeagueAutomatedSeriesRunResult completed(
            int executions,
            LeagueFixtureCompletionReceiptV1 receipt,
            VerifiedLeagueFixtureCompletion completion
    ) {
        return new LeagueAutomatedSeriesRunResult(Status.COMPLETED, null, executions,
                receipt, completion);
    }

    static LeagueAutomatedSeriesRunResult blocked(String reason, int executions) {
        return new LeagueAutomatedSeriesRunResult(Status.BLOCKED, reason, executions,
                null, null);
    }
}
