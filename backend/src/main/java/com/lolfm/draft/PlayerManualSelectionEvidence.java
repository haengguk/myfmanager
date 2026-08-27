package com.lolfm.draft;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.lolfm.champion.ChampionId;
import com.lolfm.simulator.TeamSide;
import java.util.Objects;

/** Honest evidence for one accepted player choice, without invented AI score or rank fields. */
public record PlayerManualSelectionEvidence(
        TeamSide controlledSide,
        int turn,
        DraftActionType actionType,
        ChampionId championId,
        String stateBeforeHash,
        String selectableSetIdentity,
        PlayerSelectionLegality legalityResult,
        @JsonIgnore String clientActionId
) {
    public PlayerManualSelectionEvidence {
        Objects.requireNonNull(controlledSide, "controlledSide");
        if (turn < 1) throw new IllegalArgumentException("turn must be positive");
        Objects.requireNonNull(actionType, "actionType");
        Objects.requireNonNull(championId, "championId");
        requireHash(stateBeforeHash, "stateBeforeHash");
        requireHash(selectableSetIdentity, "selectableSetIdentity");
        Objects.requireNonNull(legalityResult, "legalityResult");
        clientActionId = required(clientActionId, "clientActionId");
    }

    void appendGameplayCanonical(StringBuilder canonical) {
        canonical.append("manualControlledSide=").append(controlledSide).append('\n')
                .append("manualTurn=").append(turn).append('\n')
                .append("manualActionType=").append(actionType).append('\n')
                .append("manualChampionId=").append(championId.value()).append('\n')
                .append("manualStateBeforeHash=").append(stateBeforeHash).append('\n')
                .append("manualSelectableSetIdentity=").append(selectableSetIdentity).append('\n')
                .append("manualLegalityResult=").append(legalityResult).append('\n');
        // clientActionId is operational idempotency data, not gameplay identity.
    }

    private static String required(String value, String field) {
        String normalized = Objects.requireNonNull(value, field).trim();
        if (normalized.isEmpty()) throw new IllegalArgumentException(field + " is required");
        return normalized;
    }

    private static void requireHash(String value, String field) {
        if (!required(value, field).matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException(field + " must be lowercase SHA-256");
        }
    }
}
