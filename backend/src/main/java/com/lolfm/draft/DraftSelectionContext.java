package com.lolfm.draft;

import java.util.Objects;

/** Immutable match-root identity used by the stateless per-turn Draft selector. */
public record DraftSelectionContext(
        long matchSeed,
        String blueTeamIdentity,
        String redTeamIdentity,
        String rosterIdentityHash,
        int seriesGameNumber,
        String seriesHistoryBeforeHash
) {
    public DraftSelectionContext {
        blueTeamIdentity = required(blueTeamIdentity, "blueTeamIdentity");
        redTeamIdentity = required(redTeamIdentity, "redTeamIdentity");
        if (blueTeamIdentity.equals(redTeamIdentity)) {
            throw new IllegalArgumentException("Draft selection team identities must differ");
        }
        rosterIdentityHash = requiredHash(rosterIdentityHash, "rosterIdentityHash");
        if (seriesGameNumber < 1) {
            throw new IllegalArgumentException("seriesGameNumber must be positive");
        }
        seriesHistoryBeforeHash = requiredHash(
                seriesHistoryBeforeHash, "seriesHistoryBeforeHash");
    }

    private static String required(String value, String field) {
        String normalized = Objects.requireNonNull(value, field).trim();
        if (normalized.isEmpty()) throw new IllegalArgumentException(field + " is required");
        return normalized;
    }

    private static String requiredHash(String value, String field) {
        String hash = required(value, field);
        if (!hash.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException(field + " must be lowercase SHA-256");
        }
        return hash;
    }
}
