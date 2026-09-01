package com.lolfm.league;

import com.lolfm.simulator.TeamSide;

/** Unified per-game Draft authority evidence for Auto and Player fixture receipts. */
public record LeagueFixtureDraftAuthorityReceiptV1(
        int gameNumber,
        LeagueFixtureExecutionMode executionMode,
        TeamSide controlledSide,
        String controlPolicyId,
        String controlPolicyHash,
        String controlEvidenceHash
) {
    public LeagueFixtureDraftAuthorityReceiptV1 {
        if (gameNumber < 1) throw new IllegalArgumentException("gameNumber");
        if (executionMode == LeagueFixtureExecutionMode.FULL_AUTO) {
            if (controlledSide != null || controlPolicyId != null
                    || controlPolicyHash != null || controlEvidenceHash != null) {
                throw new IllegalArgumentException("FULL_AUTO Draft authority invariant");
            }
        } else {
            if (controlledSide == null || controlPolicyId == null || controlPolicyId.isBlank()
                    || controlPolicyHash == null
                    || !controlPolicyHash.matches("[0-9a-f]{64}")
                    || controlEvidenceHash == null
                    || !controlEvidenceHash.matches("[0-9a-f]{64}")) {
                throw new IllegalArgumentException("PLAYER_CONTROLLED Draft authority invariant");
            }
        }
    }

    static LeagueFixtureDraftAuthorityReceiptV1 fullAuto(int gameNumber) {
        return new LeagueFixtureDraftAuthorityReceiptV1(gameNumber,
                LeagueFixtureExecutionMode.FULL_AUTO, null, null, null, null);
    }

    static LeagueFixtureDraftAuthorityReceiptV1 player(
            int gameNumber,
            TeamSide controlledSide,
            String controlPolicyId,
            String controlPolicyHash,
            String controlEvidenceHash
    ) {
        return new LeagueFixtureDraftAuthorityReceiptV1(gameNumber,
                LeagueFixtureExecutionMode.PLAYER_CONTROLLED, controlledSide,
                controlPolicyId, controlPolicyHash, controlEvidenceHash);
    }

    String canonicalText() {
        return "draftAuthorityGameNumber=" + gameNumber + '\n'
                + "draftAuthorityExecutionMode=" + executionMode + '\n'
                + "draftAuthorityControlledSide="
                + (controlledSide == null ? "NONE" : controlledSide) + '\n'
                + "draftAuthorityControlPolicyId="
                + (controlPolicyId == null ? "NONE" : controlPolicyId) + '\n'
                + "draftAuthorityControlPolicyHash="
                + (controlPolicyHash == null ? "NONE" : controlPolicyHash) + '\n'
                + "draftAuthorityControlEvidenceHash="
                + (controlEvidenceHash == null ? "NONE" : controlEvidenceHash) + '\n';
    }
}
