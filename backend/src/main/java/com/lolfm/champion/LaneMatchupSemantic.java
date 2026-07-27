package com.lolfm.champion;

/**
 * Diagnostic semantic assigned to the LANE_COMBAT matchup context.
 *
 * <p>The production lane-pressure resolver owns ambient lane state, while the
 * lane-combat resolver evaluates an actual major-combat attempt. Matchup rules
 * therefore model the committed exchange rather than passive lane control.</p>
 */
public enum LaneMatchupSemantic {
    BROAD_LANE_EXCHANGE,
    COMMITTED_LANE_COMBAT;

    public static LaneMatchupSemantic selected() {
        return COMMITTED_LANE_COMBAT;
    }
}
