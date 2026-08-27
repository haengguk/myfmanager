package com.lolfm.draft;

import com.lolfm.simulator.TeamSide;
import java.util.List;
import java.util.Objects;

/** Canonical mixed-authority transcript bound into Draft and replay provenance. */
public record DraftControlEvidence(
        String schemaVersion,
        String policyId,
        String policyHash,
        TeamSide controlledSide,
        List<DraftTurnControlEvidence> turns,
        String controlEvidenceHash
) {
    public DraftControlEvidence {
        schemaVersion = required(schemaVersion, "schemaVersion");
        policyId = required(policyId, "policyId");
        requireHash(policyHash, "policyHash");
        Objects.requireNonNull(controlledSide, "controlledSide");
        turns = List.copyOf(turns);
        requireHash(controlEvidenceHash, "controlEvidenceHash");
        if (!PlayerDraftControlPolicy.EVIDENCE_SCHEMA.equals(schemaVersion)
                || !PlayerDraftControlPolicy.POLICY_ID.equals(policyId)
                || !PlayerDraftControlPolicy.POLICY_HASH.equals(policyHash)
                || !controlEvidenceHash.equals(hash(controlledSide, turns))) {
            throw new IllegalArgumentException("Player Draft control evidence identity mismatch");
        }
    }

    public static DraftControlEvidence create(
            TeamSide controlledSide, List<DraftTurnControlEvidence> turns
    ) {
        return new DraftControlEvidence(
                PlayerDraftControlPolicy.EVIDENCE_SCHEMA,
                PlayerDraftControlPolicy.POLICY_ID,
                PlayerDraftControlPolicy.POLICY_HASH,
                controlledSide,
                turns,
                hash(controlledSide, turns));
    }

    public static String hash(
            TeamSide controlledSide, List<DraftTurnControlEvidence> turns
    ) {
        StringBuilder canonical = new StringBuilder()
                .append("evidenceSchema=").append(PlayerDraftControlPolicy.EVIDENCE_SCHEMA)
                .append('\n')
                .append("policyId=").append(PlayerDraftControlPolicy.POLICY_ID).append('\n')
                .append("policyHash=").append(PlayerDraftControlPolicy.POLICY_HASH).append('\n')
                .append("controlledSide=").append(controlledSide).append('\n');
        for (DraftTurnControlEvidence turn : turns) turn.appendGameplayCanonical(canonical);
        return PlayerDraftControlPolicy.hash(canonical.toString());
    }

    public List<DraftSelectionTrace> autoSelectionTraces() {
        return turns.stream().filter(value -> value.authority() == DraftDecisionAuthority.AI)
                .map(DraftTurnControlEvidence::autoSelectionTrace).toList();
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
