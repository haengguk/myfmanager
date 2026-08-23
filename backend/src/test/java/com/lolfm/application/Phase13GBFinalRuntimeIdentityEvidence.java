package com.lolfm.application;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Canonical, standalone-readable evidence produced only after inspecting the production registry
 * and actual application wiring. The frozen hashes prevent a synthesis caller from substituting
 * self-signed runtime values.
 */
public final class Phase13GBFinalRuntimeIdentityEvidence {
    public static final String SCHEMA = "FINAL_13G_B_RUNTIME_IDENTITY_V1";
    public static final String EVIDENCE_FILE =
            "final-13g-b-runtime-identity-evidence.properties";
    public static final String MANIFEST_FILE = "SHA256SUMS.txt";
    public static final String HASH_ALGORITHM =
            "SHA256_UTF8_EXPLICIT_ORDERED_RUNTIME_IDENTITY_LINES_TRAILING_NEWLINE_V1";

    // Frozen only after the production registry/wiring inspector has generated canonical evidence.
    public static final String EXPECTED_RUNTIME_IDENTITY_HASH =
            "bcb3d2bdf009a8b53d6f99db69ad3f129a7c3c2f29570bcdf12ee0c0655ba675";
    public static final String EXPECTED_EVIDENCE_RAW_SHA256 =
            "7e54d89df8d3364e845703181a3214367818e7a34a122714299c65b480d0e109";

    private static final List<String> ORDERED_IDENTITY_KEYS = List.of(
            "schemaVersion",
            "evidenceStatus",
            "retainedRuntimeProfileId",
            "retainedConfigurationHash",
            "configurationHashAlgorithm",
            "gameplayConfigurationSchema",
            "laneCombatEnabled",
            "farmRecoveryEnabled",
            "jungleGankEnabled",
            "counterGankEnabled",
            "roamEnabled",
            "objectivePriorityEnabled",
            "lanePhaseEnabled",
            "midGameMacroEnabled",
            "objectiveDecisionEnabled",
            "lateGameMacroEnabled",
            "progressionEnabled",
            "progressionPowerEnabled",
            "championPowerEnabled",
            "championMatchupMode",
            "teamCompositionGameplayMode",
            "jungleClearContribution",
            "diagnosticsInstrumentationSeparated",
            "activeGameplayRulesVersion",
            "engineImplementationVersion",
            "productionSourceTreeHashAlgorithm",
            "productionSourceTreeHash",
            "productionSourceTreeFileCount",
            "resourceProvenanceHash",
            "draftRuleSetIdentity",
            "draftRuleSetHash",
            "draftScoringPolicyHash",
            "realDraftDefaultResolvedProfileId",
            "realDraftDefaultAuthoritativeApplicationRuntimeDefault",
            "realDraftDefaultParityVerified",
            "realDraftDefaultRole",
            "realDraftExplicitBaselineResolvedProfileId",
            "realDraftExplicitBaselineAuthoritativeApplicationRuntimeDefault",
            "realDraftExplicitBaselineParityVerified",
            "realDraftDefaultVsExplicitReplayIdentityExact",
            "realDraftDefaultVsExplicitTimelineExact",
            "realDraftExplicitBaselineRole",
            "springAutowiredResolvedProfileId",
            "springAutowiredAuthoritativeApplicationRuntimeDefault",
            "springAutowiredParityVerified",
            "springAutowiredTimelineExact",
            "springAutowiredRole",
            "httpResolvedProfileId",
            "httpAuthoritativeApplicationRuntimeDefault",
            "httpParityVerified",
            "httpInjectedAutowiredSimulatorExact",
            "httpInputRosterSource",
            "httpRealDraftTransitionPerformed",
            "httpRole",
            "lowLevelProductionDefaultsIdentity",
            "lowLevelProductionDefaultsAuthoritativeApplicationRuntimeDefault",
            "lowLevelProductionDefaultsConfigurationHash",
            "lowLevelProductionDefaultsChampionMatchupMode",
            "lowLevelProductionDefaultsTeamCompositionGameplayMode",
            "lowLevelProductionDefaultsJungleClearContribution",
            "lowLevelProductionDefaultsRole",
            "jungleEconomyCandidateActivation",
            "jungleTempoCandidateActivation",
            "productionGameplayChanged",
            "automaticTuningPerformed",
            "holdoutRerunPerformed",
            "runtimeIdentityHashAlgorithm");

    private Phase13GBFinalRuntimeIdentityEvidence() {
    }

    static List<String> orderedIdentityKeysForTest() {
        return ORDERED_IDENTITY_KEYS;
    }

    static Evidence create(Map<String, String> suppliedValues) {
        Objects.requireNonNull(suppliedValues, "suppliedValues");
        LinkedHashMap<String, String> ordered = new LinkedHashMap<>();
        for (String key : ORDERED_IDENTITY_KEYS) {
            String value = suppliedValues.get(key);
            if (value == null || value.isBlank()) {
                throw new IllegalArgumentException("Missing runtime identity field " + key);
            }
            if (value.indexOf('\n') >= 0 || value.indexOf('\r') >= 0) {
                throw new IllegalArgumentException("Runtime identity field contains newline " + key);
            }
            ordered.put(key, value);
        }
        if (suppliedValues.size() != ORDERED_IDENTITY_KEYS.size()) {
            ArrayList<String> unexpected = new ArrayList<>(suppliedValues.keySet());
            unexpected.removeAll(ORDERED_IDENTITY_KEYS);
            throw new IllegalArgumentException("Unexpected runtime identity fields " + unexpected);
        }
        validateSemantics(ordered);
        String canonicalIdentity = canonicalLines(ordered);
        String identityHash = sha256(canonicalIdentity.getBytes(StandardCharsets.UTF_8));
        return new Evidence(Map.copyOf(ordered), identityHash, canonicalIdentity);
    }

    static Evidence readBundle(Path directory, boolean requireFrozenIdentity) throws IOException {
        Objects.requireNonNull(directory, "directory");
        Path evidenceFile = directory.resolve(EVIDENCE_FILE);
        Path manifestFile = directory.resolve(MANIFEST_FILE);
        if (!Files.isRegularFile(evidenceFile) || !Files.isRegularFile(manifestFile)) {
            throw new IllegalStateException("Runtime identity evidence bundle is incomplete");
        }
        String rawManifest = Files.readString(manifestFile, StandardCharsets.UTF_8);
        String actualEvidenceSha = sha256(evidenceFile);
        String expectedManifest = actualEvidenceSha + "  " + EVIDENCE_FILE + '\n';
        if (!rawManifest.equals(expectedManifest)) {
            throw new IllegalStateException("Runtime identity raw SHA manifest mismatch");
        }
        if (requireFrozenIdentity && !actualEvidenceSha.equals(EXPECTED_EVIDENCE_RAW_SHA256)) {
            throw new IllegalStateException("Runtime identity evidence raw SHA is not frozen");
        }
        String rawEvidence = Files.readString(evidenceFile, StandardCharsets.UTF_8);
        ParsedEvidence parsed = parseCanonicalEvidence(rawEvidence);
        Evidence evidence = create(parsed.identityValues());
        if (!evidence.runtimeIdentityHash().equals(parsed.runtimeIdentityHash())) {
            throw new IllegalStateException("Runtime identity internal hash mismatch");
        }
        if (!rawEvidence.equals(evidence.canonicalEvidence())) {
            throw new IllegalStateException("Runtime identity evidence is not canonical");
        }
        if (requireFrozenIdentity
                && !evidence.runtimeIdentityHash().equals(EXPECTED_RUNTIME_IDENTITY_HASH)) {
            throw new IllegalStateException("Runtime identity hash is not the frozen identity");
        }
        return evidence;
    }

    static String writeBundle(Path directory, Evidence evidence) throws IOException {
        Objects.requireNonNull(directory, "directory");
        Objects.requireNonNull(evidence, "evidence");
        Files.createDirectories(directory);
        Path evidenceFile = directory.resolve(EVIDENCE_FILE);
        writeUtf8(evidenceFile, evidence.canonicalEvidence());
        String evidenceSha = sha256(evidenceFile);
        writeUtf8(directory.resolve(MANIFEST_FILE),
                evidenceSha + "  " + EVIDENCE_FILE + '\n');
        return evidenceSha;
    }

    static Map<String, Object> outputDocument(Evidence evidence, String inputRawSha256) {
        Map<String, String> values = evidence.identityValues();
        LinkedHashMap<String, Object> output = new LinkedHashMap<>();
        output.put("activeGameplayRulesVersion", value(values, "activeGameplayRulesVersion"));
        output.put("automaticTuningPerformed", bool(values, "automaticTuningPerformed"));
        output.put("configurationHashAlgorithm", value(values, "configurationHashAlgorithm"));
        output.put("draftRuleSetHash", value(values, "draftRuleSetHash"));
        output.put("draftRuleSetIdentity", value(values, "draftRuleSetIdentity"));
        output.put("draftScoringPolicyHash", value(values, "draftScoringPolicyHash"));
        output.put("engineImplementationVersion", value(values, "engineImplementationVersion"));
        output.put("evidenceInputRawSha256", inputRawSha256);
        output.put("evidenceStatus", value(values, "evidenceStatus"));
        output.put("holdoutRerunPerformed", bool(values, "holdoutRerunPerformed"));
        output.put("productionCandidateActivation", Map.of(
                "FULL_SYSTEM_WITH_JUNGLE_ECONOMY_CANDIDATE_V1",
                bool(values, "jungleEconomyCandidateActivation"),
                "FULL_SYSTEM_WITH_JUNGLE_TEMPO_CANDIDATE_V1",
                bool(values, "jungleTempoCandidateActivation")));
        output.put("productionGameplayChanged", bool(values, "productionGameplayChanged"));
        output.put("productionSourceTree", Map.of(
                "fileCount", integer(values, "productionSourceTreeFileCount"),
                "hash", value(values, "productionSourceTreeHash"),
                "hashAlgorithm", value(values, "productionSourceTreeHashAlgorithm")));
        output.put("resourceProvenanceHash", value(values, "resourceProvenanceHash"));
        output.put("retainedConfigurationHash", value(values, "retainedConfigurationHash"));
        output.put("retainedGameplayConfiguration", gameplayConfiguration(values));
        output.put("retainedRuntimeProfileId", value(values, "retainedRuntimeProfileId"));
        output.put("runtimeIdentityHash", evidence.runtimeIdentityHash());
        output.put("runtimeIdentityHashAlgorithm", value(values, "runtimeIdentityHashAlgorithm"));
        output.put("runtimeIdentityStatus", "EXACT");
        output.put("schemaVersion", SCHEMA);
        output.put("wiring", wiring(values));
        return output;
    }

    private static Map<String, Object> gameplayConfiguration(Map<String, String> values) {
        LinkedHashMap<String, Object> configuration = new LinkedHashMap<>();
        configuration.put("schemaVersion", value(values, "gameplayConfigurationSchema"));
        for (String key : List.of(
                "laneCombatEnabled", "farmRecoveryEnabled", "jungleGankEnabled",
                "counterGankEnabled", "roamEnabled", "objectivePriorityEnabled",
                "lanePhaseEnabled", "midGameMacroEnabled", "objectiveDecisionEnabled",
                "lateGameMacroEnabled", "progressionEnabled", "progressionPowerEnabled",
                "championPowerEnabled")) {
            configuration.put(key, bool(values, key));
        }
        configuration.put("championMatchupMode", value(values, "championMatchupMode"));
        configuration.put("teamCompositionGameplayMode",
                value(values, "teamCompositionGameplayMode"));
        configuration.put("jungleClearContribution",
                value(values, "jungleClearContribution"));
        configuration.put("diagnosticsInstrumentationSeparated",
                bool(values, "diagnosticsInstrumentationSeparated"));
        return configuration;
    }

    private static Map<String, Object> wiring(Map<String, String> values) {
        LinkedHashMap<String, Object> wiring = new LinkedHashMap<>();
        wiring.put("REAL_DRAFT_DEFAULT_OVERLOAD", Map.of(
                "authoritativeApplicationRuntimeDefault",
                bool(values, "realDraftDefaultAuthoritativeApplicationRuntimeDefault"),
                "parityVerified", bool(values, "realDraftDefaultParityVerified"),
                "resolvedProfileId", value(values, "realDraftDefaultResolvedProfileId"),
                "role", value(values, "realDraftDefaultRole")));
        wiring.put("REAL_DRAFT_EXPLICIT_BASELINE", Map.of(
                "authoritativeApplicationRuntimeDefault",
                bool(values, "realDraftExplicitBaselineAuthoritativeApplicationRuntimeDefault"),
                "defaultVsExplicitReplayIdentityExact",
                bool(values, "realDraftDefaultVsExplicitReplayIdentityExact"),
                "defaultVsExplicitTimelineExact",
                bool(values, "realDraftDefaultVsExplicitTimelineExact"),
                "parityVerified", bool(values, "realDraftExplicitBaselineParityVerified"),
                "resolvedProfileId",
                value(values, "realDraftExplicitBaselineResolvedProfileId"),
                "role", value(values, "realDraftExplicitBaselineRole")));
        wiring.put("SPRING_AUTOWIRED_MATCH_SIMULATOR", Map.of(
                "authoritativeApplicationRuntimeDefault",
                bool(values, "springAutowiredAuthoritativeApplicationRuntimeDefault"),
                "parityVerified", bool(values, "springAutowiredParityVerified"),
                "resolvedProfileId", value(values, "springAutowiredResolvedProfileId"),
                "role", value(values, "springAutowiredRole"),
                "timelineExact", bool(values, "springAutowiredTimelineExact")));
        wiring.put("HTTP_MATCH_SIMULATE", Map.of(
                "authoritativeApplicationRuntimeDefault",
                bool(values, "httpAuthoritativeApplicationRuntimeDefault"),
                "injectedAutowiredSimulatorExact",
                bool(values, "httpInjectedAutowiredSimulatorExact"),
                "inputRosterSource", value(values, "httpInputRosterSource"),
                "parityVerified", bool(values, "httpParityVerified"),
                "realDraftTransitionPerformed", bool(values, "httpRealDraftTransitionPerformed"),
                "resolvedProfileId", value(values, "httpResolvedProfileId"),
                "role", value(values, "httpRole")));
        wiring.put("LOW_LEVEL_SIMULATION_OPTIONS_PRODUCTION_DEFAULTS", Map.of(
                "authoritativeApplicationRuntimeDefault",
                bool(values, "lowLevelProductionDefaultsAuthoritativeApplicationRuntimeDefault"),
                "championMatchupMode",
                value(values, "lowLevelProductionDefaultsChampionMatchupMode"),
                "configurationHash",
                value(values, "lowLevelProductionDefaultsConfigurationHash"),
                "identity", value(values, "lowLevelProductionDefaultsIdentity"),
                "jungleClearContribution",
                value(values, "lowLevelProductionDefaultsJungleClearContribution"),
                "role", value(values, "lowLevelProductionDefaultsRole"),
                "teamCompositionGameplayMode",
                value(values, "lowLevelProductionDefaultsTeamCompositionGameplayMode")));
        return wiring;
    }

    private static void validateSemantics(Map<String, String> values) {
        require(values, "schemaVersion", SCHEMA);
        require(values, "evidenceStatus", "VERIFIED_FROM_PRODUCTION_REGISTRY_AND_WIRING");
        require(values, "runtimeIdentityHashAlgorithm", HASH_ALGORITHM);
        for (String hashField : List.of(
                "retainedConfigurationHash", "productionSourceTreeHash",
                "resourceProvenanceHash", "draftRuleSetHash", "draftScoringPolicyHash",
                "lowLevelProductionDefaultsConfigurationHash")) {
            if (!value(values, hashField).matches("[0-9a-f]{64}")) {
                throw new IllegalArgumentException(hashField + " must be lowercase SHA-256");
            }
        }
        if (integer(values, "productionSourceTreeFileCount") < 1) {
            throw new IllegalArgumentException("productionSourceTreeFileCount must be positive");
        }
    }

    private static ParsedEvidence parseCanonicalEvidence(String rawEvidence) {
        if (!rawEvidence.endsWith("\n")) {
            throw new IllegalStateException("Runtime identity evidence requires trailing newline");
        }
        String[] lines = rawEvidence.substring(0, rawEvidence.length() - 1).split("\n", -1);
        if (lines.length != ORDERED_IDENTITY_KEYS.size() + 1) {
            throw new IllegalStateException("Runtime identity evidence field count mismatch");
        }
        LinkedHashMap<String, String> values = new LinkedHashMap<>();
        for (int index = 0; index < ORDERED_IDENTITY_KEYS.size(); index++) {
            String expectedKey = ORDERED_IDENTITY_KEYS.get(index);
            String prefix = expectedKey + '=';
            if (!lines[index].startsWith(prefix)) {
                throw new IllegalStateException("Runtime identity evidence order mismatch at "
                        + expectedKey);
            }
            String value = lines[index].substring(prefix.length());
            if (value.isBlank() || values.put(expectedKey, value) != null) {
                throw new IllegalStateException("Invalid runtime identity field " + expectedKey);
            }
        }
        String hashPrefix = "runtimeIdentityHash=";
        if (!lines[lines.length - 1].startsWith(hashPrefix)) {
            throw new IllegalStateException("Missing runtimeIdentityHash");
        }
        String runtimeHash = lines[lines.length - 1].substring(hashPrefix.length());
        if (!runtimeHash.matches("[0-9a-f]{64}")) {
            throw new IllegalStateException("runtimeIdentityHash must be lowercase SHA-256");
        }
        return new ParsedEvidence(Map.copyOf(values), runtimeHash);
    }

    private static String canonicalLines(Map<String, String> values) {
        StringBuilder canonical = new StringBuilder();
        ORDERED_IDENTITY_KEYS.forEach(key -> canonical.append(key).append('=')
                .append(value(values, key)).append('\n'));
        return canonical.toString();
    }

    private static String value(Map<String, String> values, String key) {
        return Objects.requireNonNull(values.get(key), key);
    }

    private static boolean bool(Map<String, String> values, String key) {
        String value = value(values, key);
        if (!value.equals("true") && !value.equals("false")) {
            throw new IllegalArgumentException(key + " must be true or false");
        }
        return Boolean.parseBoolean(value);
    }

    private static int integer(Map<String, String> values, String key) {
        return Integer.parseInt(value(values, key));
    }

    private static void require(Map<String, String> values, String key, String expected) {
        String actual = value(values, key);
        if (!actual.equals(expected)) {
            throw new IllegalArgumentException(key + " expected=" + expected + " actual=" + actual);
        }
    }

    private static void writeUtf8(Path output, String content) throws IOException {
        Files.createDirectories(output.getParent());
        Path temporary = Files.createTempFile(output.getParent(), output.getFileName().toString(),
                ".tmp");
        Files.writeString(temporary, content, StandardCharsets.UTF_8);
        try {
            Files.move(temporary, output, StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(temporary, output, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    static String sha256(Path file) throws IOException {
        return sha256(Files.readAllBytes(file));
    }

    private static String sha256(byte[] bytes) {
        try {
            return java.util.HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException(error);
        }
    }

    record Evidence(
            Map<String, String> identityValues,
            String runtimeIdentityHash,
            String canonicalIdentity
    ) {
        Evidence {
            identityValues = Map.copyOf(identityValues);
            Objects.requireNonNull(runtimeIdentityHash, "runtimeIdentityHash");
            Objects.requireNonNull(canonicalIdentity, "canonicalIdentity");
        }

        String canonicalEvidence() {
            return canonicalIdentity + "runtimeIdentityHash=" + runtimeIdentityHash + '\n';
        }
    }

    private record ParsedEvidence(Map<String, String> identityValues, String runtimeIdentityHash) {
    }
}
