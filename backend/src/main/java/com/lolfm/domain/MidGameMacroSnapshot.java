package com.lolfm.domain;

import com.lolfm.simulator.MatchPhase;
import java.util.List;
import java.util.Set;

public record MidGameMacroSnapshot(
        boolean enabled,
        MatchPhase matchPhase,
        int currentTimeSeconds,
        TeamMacroSnapshot blueTeam,
        TeamMacroSnapshot redTeam,
        double dragonMacroSetupControl,
        double baronMacroSetupControl,
        List<MidGameMacroEvaluationData> evaluationHistory,
        boolean matchEnded
) {
    public MidGameMacroSnapshot {
        blueTeam = blueTeam == null ? emptyTeam() : blueTeam;
        redTeam = redTeam == null ? emptyTeam() : redTeam;
        evaluationHistory = evaluationHistory == null ? List.of() : List.copyOf(evaluationHistory);
    }

    public MidGameMacroSnapshot(
            boolean enabled, MatchPhase matchPhase, int currentTimeSeconds,
            TeamMacroSnapshot blueTeam, TeamMacroSnapshot redTeam,
            double dragonMacroSetupControl, double baronMacroSetupControl
    ) {
        this(enabled, matchPhase, currentTimeSeconds, blueTeam, redTeam,
                dragonMacroSetupControl, baronMacroSetupControl, List.of(), false);
    }

    public static MidGameMacroSnapshot disabled(int currentTimeSeconds, MatchPhase matchPhase) {
        return new MidGameMacroSnapshot(false, matchPhase, currentTimeSeconds,
                emptyTeam(), emptyTeam(), 0, 0, List.of(), false);
    }

    private static TeamMacroSnapshot emptyTeam() {
        return new TeamMacroSnapshot(null, null, null, -1, -1, -1, Set.of(),
                com.lolfm.simulator.MacroActionResult.NOT_ATTEMPTED, null, null, null,
                null, com.lolfm.simulator.MacroPlanStatus.DISABLED,
                com.lolfm.simulator.MacroPlanEndReason.FEATURE_DISABLED,
                -1, -1, "FEATURE_DISABLED", 0);
    }
}
