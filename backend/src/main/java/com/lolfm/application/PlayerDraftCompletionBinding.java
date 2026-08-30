package com.lolfm.application;

import com.lolfm.champion.ChampionId;
import com.lolfm.draft.PlayerControlledDraftResult;
import com.lolfm.simulator.TeamSide;
import java.util.Objects;
import java.util.Set;

/** Server-owned, match-scoped certificate for one exact completed Draft result. */
record PlayerDraftCompletionBinding(
        String scope,
        String ownerId,
        int generation,
        long completionRevision,
        String blueTeamCode,
        String redTeamCode,
        TeamSide controlledSide,
        long matchSeed,
        int seriesGameNumber,
        Set<ChampionId> hardFearlessExclusions,
        String historyBeforeHash,
        PlayerControlledDraftResult trustedResult,
        String draftIdentity,
        String controlEvidenceHash,
        String draftRuleIdentity,
        String draftMetaVersion,
        String requiredLegalRoleKeyHash,
        String actualLegalRoleKeyHash,
        String inputHash,
        String rosterIdentityHash,
        String draftDecisionHash,
        String finalAssignmentHash,
        String finalDraftHash,
        MatchEngineV1Policy.Requirement productionPolicy
) {
    static final String STANDALONE = "STANDALONE_PLAYER_DRAFT_V1";
    static final String SERIES = "SERIES_PLAYER_DRAFT_V1";

    PlayerDraftCompletionBinding {
        scope = required(scope, "scope");
        ownerId = required(ownerId, "ownerId");
        if (generation < 1 || completionRevision < 0 || seriesGameNumber < 1) {
            throw new IllegalArgumentException("PLAYER_DRAFT_COMPLETION_BINDING_IDENTITY");
        }
        blueTeamCode = required(blueTeamCode, "blueTeamCode");
        redTeamCode = required(redTeamCode, "redTeamCode");
        Objects.requireNonNull(controlledSide, "controlledSide");
        hardFearlessExclusions = Set.copyOf(hardFearlessExclusions);
        Objects.requireNonNull(trustedResult, "trustedResult");
        historyBeforeHash = hash(historyBeforeHash, "historyBeforeHash");
        draftIdentity = hash(draftIdentity, "draftIdentity");
        controlEvidenceHash = hash(controlEvidenceHash, "controlEvidenceHash");
        draftRuleIdentity = required(draftRuleIdentity, "draftRuleIdentity");
        draftMetaVersion = required(draftMetaVersion, "draftMetaVersion");
        requiredLegalRoleKeyHash = hash(requiredLegalRoleKeyHash,
                "requiredLegalRoleKeyHash");
        actualLegalRoleKeyHash = hash(actualLegalRoleKeyHash,
                "actualLegalRoleKeyHash");
        inputHash = hash(inputHash, "inputHash");
        rosterIdentityHash = hash(rosterIdentityHash, "rosterIdentityHash");
        draftDecisionHash = hash(draftDecisionHash, "draftDecisionHash");
        finalAssignmentHash = hash(finalAssignmentHash, "finalAssignmentHash");
        finalDraftHash = hash(finalDraftHash, "finalDraftHash");
        Objects.requireNonNull(productionPolicy, "productionPolicy");
    }

    private static String hash(String value, String field) {
        value = required(value, field);
        if (!value.matches("[0-9a-f]{64}")) throw new IllegalArgumentException(field);
        return value;
    }

    private static String required(String value, String field) {
        String normalized = Objects.requireNonNull(value, field).trim();
        if (normalized.isEmpty() || !normalized.equals(value)) {
            throw new IllegalArgumentException(field);
        }
        return normalized;
    }
}
