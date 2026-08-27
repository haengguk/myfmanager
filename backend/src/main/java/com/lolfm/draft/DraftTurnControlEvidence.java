package com.lolfm.draft;

import com.lolfm.champion.ChampionId;
import com.lolfm.simulator.TeamSide;
import java.util.Objects;

/** One ordered turn binding authority, action and the states immediately around it. */
public record DraftTurnControlEvidence(
        int turn,
        TeamSide side,
        DraftActionType actionType,
        ChampionId championId,
        DraftDecisionAuthority authority,
        String stateBeforeHash,
        String stateAfterHash,
        DraftSelectionTrace autoSelectionTrace,
        PlayerManualSelectionEvidence playerSelectionEvidence
) {
    public DraftTurnControlEvidence {
        if (turn < 1) throw new IllegalArgumentException("turn must be positive");
        Objects.requireNonNull(side, "side");
        Objects.requireNonNull(actionType, "actionType");
        Objects.requireNonNull(championId, "championId");
        Objects.requireNonNull(authority, "authority");
        requireHash(stateBeforeHash, "stateBeforeHash");
        requireHash(stateAfterHash, "stateAfterHash");
        if ((authority == DraftDecisionAuthority.AI
                && (autoSelectionTrace == null || playerSelectionEvidence != null))
                || (authority == DraftDecisionAuthority.PLAYER
                && (playerSelectionEvidence == null || autoSelectionTrace != null))) {
            throw new IllegalArgumentException("Turn evidence authority payload mismatch");
        }
    }

    void appendGameplayCanonical(StringBuilder canonical) {
        canonical.append("turn=").append(turn).append('\n')
                .append("side=").append(side).append('\n')
                .append("actionType=").append(actionType).append('\n')
                .append("championId=").append(championId.value()).append('\n')
                .append("authority=").append(authority).append('\n')
                .append("stateBeforeHash=").append(stateBeforeHash).append('\n')
                .append("stateAfterHash=").append(stateAfterHash).append('\n');
        if (autoSelectionTrace != null) {
            canonical.append("autoSelectionTraceHash=")
                    .append(DraftSelectionTraceHasher.traceHash(autoSelectionTrace)).append('\n');
        } else {
            playerSelectionEvidence.appendGameplayCanonical(canonical);
        }
    }

    private static void requireHash(String value, String field) {
        if (value == null || !value.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException(field + " must be lowercase SHA-256");
        }
    }
}
