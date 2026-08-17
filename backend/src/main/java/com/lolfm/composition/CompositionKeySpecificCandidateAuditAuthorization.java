package com.lolfm.composition;

import java.util.Objects;

/** Immutable internal authorization copied into exactly one fresh-holdout match. */
public record CompositionKeySpecificCandidateAuditAuthorization(
        String candidateVersion, String candidateHash, int holdoutCaseIndex, boolean auditOnly
) {
    public CompositionKeySpecificCandidateAuditAuthorization {
        Objects.requireNonNull(candidateVersion, "candidateVersion");
        Objects.requireNonNull(candidateHash, "candidateHash");
        if (holdoutCaseIndex < -1) throw new IllegalArgumentException("holdoutCaseIndex must be >= -1");
    }

    public static CompositionKeySpecificCandidateAuditAuthorization none() {
        return new CompositionKeySpecificCandidateAuditAuthorization("NONE", "NONE", -1, false);
    }

    public static CompositionKeySpecificCandidateAuditAuthorization frozenFreshHoldoutCase(int caseIndex) {
        if (caseIndex < 0) throw new IllegalArgumentException("caseIndex must be non-negative");
        return new CompositionKeySpecificCandidateAuditAuthorization(
                FrozenCompositionKeySpecificChannelCandidate.VERSION,
                FrozenCompositionKeySpecificChannelCandidate.HASH, caseIndex, true);
    }

    public boolean enabled() { return auditOnly; }

    public void verifyExact() {
        if (!auditOnly || holdoutCaseIndex < 0) {
            throw new CompositionGameplayConfigurationException(
                    "COMPOSITION_KEY_SPECIFIC_CANDIDATE_NOT_AUTHORIZED",
                    "Key-specific candidate execution requires explicit match-scoped audit authorization");
        }
        FrozenCompositionKeySpecificChannelCandidate.verifyIdentity(candidateVersion, candidateHash);
    }
}
