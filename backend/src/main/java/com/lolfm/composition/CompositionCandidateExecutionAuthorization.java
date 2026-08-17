package com.lolfm.composition;

import java.util.Objects;

/** Internal-only, immutable authorization for the fresh candidate audit. */
public record CompositionCandidateExecutionAuthorization(
        String candidateVersion,
        String candidateHash,
        String policyHash,
        boolean auditOnly
) {
    public CompositionCandidateExecutionAuthorization {
        Objects.requireNonNull(candidateVersion, "candidateVersion");
        Objects.requireNonNull(candidateHash, "candidateHash");
        Objects.requireNonNull(policyHash, "policyHash");
    }

    public static CompositionCandidateExecutionAuthorization none() {
        return new CompositionCandidateExecutionAuthorization("NONE", "NONE", "NONE", false);
    }

    public static CompositionCandidateExecutionAuthorization frozenAudit() {
        return new CompositionCandidateExecutionAuthorization(
                FrozenCompositionGameplayGainPolicy.CANDIDATE_VERSION,
                FrozenCompositionGameplayGainPolicy.CANDIDATE_HASH,
                FrozenCompositionGameplayGainPolicy.POLICY_HASH,
                true);
    }

    public boolean exactFor(FrozenCompositionGameplayGainPolicy policy) {
        return auditOnly
                && policy.candidateVersion().equals(candidateVersion)
                && policy.candidateHash().equals(candidateHash)
                && policy.candidateHash().equals(policyHash);
    }
}
