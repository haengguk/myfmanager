package com.lolfm.application;

import java.util.Objects;

/** Raw-byte identity for one versioned runtime input resource. */
public record VersionedResourceIdentity(
        String role,
        String classpathResource,
        String version,
        String sha256
) {
    public VersionedResourceIdentity {
        role = required(role, "role");
        classpathResource = required(classpathResource, "classpathResource");
        version = required(version, "version");
        sha256 = required(sha256, "sha256");
        if (!classpathResource.startsWith("/")) {
            throw new IllegalArgumentException("classpathResource must be absolute");
        }
        if (!sha256.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("sha256 must be lowercase SHA-256");
        }
    }

    private static String required(String value, String field) {
        String normalized = Objects.requireNonNull(value, field).trim();
        if (normalized.isEmpty()) throw new IllegalArgumentException(field + " is required");
        return normalized;
    }
}
