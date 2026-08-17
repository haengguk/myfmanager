package com.lolfm.composition;

import java.util.Objects;

/** Immutable internal authorization copied into one match-scoped runtime state. */
public record CompositionSemanticsAuditExecutionAuthorization(
        String blueprintVersion,
        String blueprintHash,
        int diagnosticCaseIndex,
        boolean auditOnly
) {
    public CompositionSemanticsAuditExecutionAuthorization {
        Objects.requireNonNull(blueprintVersion, "blueprintVersion");
        Objects.requireNonNull(blueprintHash, "blueprintHash");
        if (diagnosticCaseIndex < -1) throw new IllegalArgumentException("diagnosticCaseIndex must be >= -1");
    }

    public static CompositionSemanticsAuditExecutionAuthorization none() {
        return new CompositionSemanticsAuditExecutionAuthorization("NONE", "NONE", -1, false);
    }

    public static CompositionSemanticsAuditExecutionAuthorization frozenDiagnosticCase(int caseIndex) {
        if (caseIndex < 0) throw new IllegalArgumentException("caseIndex must be non-negative");
        return new CompositionSemanticsAuditExecutionAuthorization(
                FrozenCompositionApplicationSemanticsBlueprint.VERSION,
                FrozenCompositionApplicationSemanticsBlueprint.HASH,
                caseIndex,
                true);
    }

    public boolean enabled() { return auditOnly; }

    public void verifyExact() {
        if (!auditOnly) {
            throw new CompositionGameplayConfigurationException(
                    "COMPOSITION_SEMANTICS_AUDIT_NOT_AUTHORIZED",
                    "Composition semantics audit execution requires an explicit match-scoped authorization");
        }
        FrozenCompositionApplicationSemanticsBlueprint.verifyIdentity(blueprintVersion, blueprintHash);
        if (diagnosticCaseIndex < 0) {
            throw new CompositionGameplayConfigurationException(
                    "COMPOSITION_SEMANTICS_AUDIT_NOT_AUTHORIZED",
                    "Composition semantics audit diagnostic case identity is missing");
        }
    }
}
