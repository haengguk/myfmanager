package com.lolfm.domain;

import com.lolfm.simulator.MacroPlanEndReason;
import com.lolfm.simulator.TeamMacroPlan;
import com.lolfm.simulator.TeamSide;

/** One immutable lifecycle record identified by team side and match-scoped plan sequence. */
public record MacroPlanLifecycleData(
        TeamSide teamSide,
        int planSequence,
        TeamMacroPlan plan,
        int startedAtSeconds,
        int activeUntilSeconds,
        Integer endTimeSeconds,
        MacroPlanEndReason endReason,
        int endRecordCount
) {
    public boolean setupPlan() {
        return plan == TeamMacroPlan.OBJECTIVE_SETUP_DRAGON
                || plan == TeamMacroPlan.OBJECTIVE_SETUP_BARON;
    }
}
