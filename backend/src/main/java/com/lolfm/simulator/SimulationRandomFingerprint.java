package com.lolfm.simulator;

import java.util.Objects;

/** Observational identity of the seeded gameplay Random stream consumed by one match. */
public record SimulationRandomFingerprint(
        String schemaVersion,
        long randomDrawCount,
        String randomTraceHash,
        String randomTraceHashAlgorithm
) {
    public static final String SCHEMA = "SIMULATION_RANDOM_FINGERPRINT_V1";
    public static final String TRACE_HASH_ALGORITHM =
            "SHA256_UTF8_ORDERED_NEXT_BITS_CONTEXT_LINES_TRAILING_NEWLINE_V1";

    public SimulationRandomFingerprint {
        schemaVersion = Objects.requireNonNull(schemaVersion, "schemaVersion");
        if (!SCHEMA.equals(schemaVersion)) {
            throw new IllegalArgumentException("Unsupported simulation Random fingerprint schema");
        }
        if (randomDrawCount < 0) {
            throw new IllegalArgumentException("randomDrawCount must not be negative");
        }
        randomTraceHash = Objects.requireNonNull(randomTraceHash, "randomTraceHash");
        if (!randomTraceHash.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("randomTraceHash must be lowercase SHA-256");
        }
        randomTraceHashAlgorithm = Objects.requireNonNull(
                randomTraceHashAlgorithm, "randomTraceHashAlgorithm");
        if (!TRACE_HASH_ALGORITHM.equals(randomTraceHashAlgorithm)) {
            throw new IllegalArgumentException("Unsupported simulation Random trace hash algorithm");
        }
    }
}
