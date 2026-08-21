package com.lolfm.application;

import java.util.List;
import java.util.Objects;

/** Coherent raw-resource snapshot used to distinguish replay inputs. */
public record SimulationResourceProvenance(
        String schemaVersion,
        List<VersionedResourceIdentity> resources,
        String compositionProfileHash,
        String draftLegalRoleKeyHash,
        int jungleClearGameplayEnabledProfileCount,
        String resourceProvenanceHash
) {
    public static final String SCHEMA = "SIMULATION_RESOURCE_PROVENANCE_V1";

    public SimulationResourceProvenance {
        schemaVersion = Objects.requireNonNull(schemaVersion, "schemaVersion");
        if (!SCHEMA.equals(schemaVersion)) {
            throw new IllegalArgumentException("Unsupported resource provenance schema");
        }
        resources = List.copyOf(resources);
        if (resources.isEmpty()) throw new IllegalArgumentException("resources must not be empty");
        compositionProfileHash = requiredHash(compositionProfileHash, "compositionProfileHash");
        draftLegalRoleKeyHash = requiredHash(draftLegalRoleKeyHash, "draftLegalRoleKeyHash");
        if (jungleClearGameplayEnabledProfileCount != 0) {
            throw new IllegalArgumentException(
                    "Pre-Jungle provenance requires zero gameplay-enabled Jungle Clear profiles");
        }
        resourceProvenanceHash = requiredHash(
                resourceProvenanceHash, "resourceProvenanceHash");
    }

    private static String requiredHash(String value, String field) {
        String hash = Objects.requireNonNull(value, field);
        if (!hash.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException(field + " must be lowercase SHA-256");
        }
        return hash;
    }
}
