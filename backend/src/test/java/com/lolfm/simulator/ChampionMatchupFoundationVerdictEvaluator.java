package com.lolfm.simulator;

final class ChampionMatchupFoundationVerdictEvaluator {
    private ChampionMatchupFoundationVerdictEvaluator() {
    }

    static String verdict(int integrityErrors, int warningCount) {
        if (integrityErrors > 0) return "BLOCKED_BY_MATCHUP_FOUNDATION_INTEGRITY";
        if (warningCount > 0) return "REVIEW_CHAMPION_MATCHUP_FOUNDATION";
        return "READY_FOR_PHASE_13C2";
    }
}
