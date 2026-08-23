package com.lolfm.simulator;

import java.util.List;

public record TeamfightOutcome(
        TeamSide winningSide,
        FightGrade grade,
        int winningTeamKills,
        int losingTeamKills,
        int endedAtSeconds,
        List<String> deadPlayerNames,
        String actionId
) {
    public TeamfightOutcome(TeamSide winningSide, FightGrade grade, int winningTeamKills,
                            int losingTeamKills, int endedAtSeconds, List<String> deadPlayerNames) {
        this(winningSide, grade, winningTeamKills, losingTeamKills, endedAtSeconds,
                deadPlayerNames, null);
    }
    public TeamfightOutcome {
        deadPlayerNames = List.copyOf(deadPlayerNames);
    }

    public boolean isBigWinOrBetter() {
        return grade == FightGrade.BIG_WIN || grade == FightGrade.ACE;
    }

    public boolean isAce() {
        return grade == FightGrade.ACE;
    }
}
