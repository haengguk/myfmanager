package com.lolfm.application;

import com.lolfm.champion.ChampionMatchupMode;
import com.lolfm.composition.TeamCompositionGameplayMode;
import com.lolfm.simulator.JungleClearContribution;
import com.lolfm.simulator.ResolvedSimulationRuntimeProfile;
import com.lolfm.simulator.SimulationGameplayConfiguration;
import com.lolfm.simulator.SimulationOptions;
import com.lolfm.simulator.SimulationRuntimeProfileId;
import com.lolfm.simulator.SimulationRuntimeProfiles;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

/** Authoritative, code-owned production runtime policy for the frozen Match Engine V1 boundary. */
public final class MatchEngineV1Policy {
    public static final String CONTRACT_SCHEMA = "MATCH_ENGINE_CONTRACT_V1";
    public static final String POLICY_SCHEMA = "MATCH_ENGINE_V1_PRODUCTION_POLICY_V1";
    public static final String POLICY_ID = "MATCH_ENGINE_V1_BASELINE_PRODUCTION_POLICY";
    public static final String FINAL_13G_B_MANIFEST_SHA256 =
            "bd9a9cf3b089cfc76fceb0311094c1b70232278404f5675c42d89849d927bc98";
    public static final String FINAL_13G_B_APPROVED_SOURCE_TREE_SHA256 =
            "68edbcb7393c9a54c0888a4f27a4e286774306675dce48991554fd22dcb2ddac";
    public static final String APPROVED_RESOURCE_PROVENANCE_SHA256 =
            "64ab1be3fdfe8d6660648ac634b52a86a5693d264bfbe707153dac9c17d39b4f";
    public static final String DRAFT_RULE_SET_IDENTITY =
            "PROFESSIONAL_5_BAN_5_PICK_HARD_FEARLESS_V1";
    public static final String DRAFT_RULE_SET_SHA256 =
            "b22cd42b20e7ebc4faba8b2089db21abadaca90aa8294ead54155ee5a9377cd0";
    public static final String DRAFT_SCORING_POLICY_SHA256 =
            "4bc9f8b1db17ff2803fce80b2616e2fd0afffa278749a80b91231e342caeec18";
    public static final String POLICY_HASH_ALGORITHM =
            "SHA256_UTF8_EXPLICIT_ORDERED_POLICY_LINES_TRAILING_NEWLINE_V1";
    public static final String LOW_LEVEL_DEFAULT_IDENTITY =
            "LOW_LEVEL_SIMULATION_OPTIONS_PRODUCTION_DEFAULTS";

    private static final ResolvedSimulationRuntimeProfile PROFILE =
            SimulationRuntimeProfiles.resolve(SimulationRuntimeProfileId.BASELINE_V1);
    private static final Snapshot SNAPSHOT = createAndVerify();

    private MatchEngineV1Policy() {
    }

    public static Snapshot authoritative() {
        return SNAPSHOT;
    }

    public static ResolvedSimulationRuntimeProfile resolvedRuntimeProfile() {
        return PROFILE;
    }

    /** Rejects caller-authored policy/profile combinations before a simulator is created. */
    public static void requireAuthoritative(Requirement requirement) {
        Objects.requireNonNull(requirement, "productionPolicyRequirement");
        if (!requirement.policyId().equals(POLICY_ID)
                || requirement.runtimeProfileId() != SimulationRuntimeProfileId.BASELINE_V1
                || !requirement.configurationHash().equals(PROFILE.configurationHash())
                || requirement.economyCandidateActivation()
                || requirement.tempoCandidateActivation()) {
            throw new IllegalArgumentException("MATCH_ENGINE_V1_PRODUCTION_POLICY_MISMATCH");
        }
    }

    public static Requirement requirement() {
        return new Requirement(POLICY_ID, SimulationRuntimeProfileId.BASELINE_V1,
                PROFILE.configurationHash(), false, false);
    }

    public static boolean isLowLevelProductionDefaultsAuthoritative() {
        SimulationOptions lowLevel = SimulationOptions.productionDefaults();
        return lowLevel.championMatchupMode() == PROFILE.gameplayConfiguration().championMatchupMode()
                && lowLevel.teamCompositionGameplayMode()
                == PROFILE.gameplayConfiguration().teamCompositionGameplayMode()
                && lowLevel.jungleClearContribution()
                == PROFILE.gameplayConfiguration().jungleClearContribution();
    }

    private static Snapshot createAndVerify() {
        SimulationGameplayConfiguration configuration = PROFILE.gameplayConfiguration();
        if (!PROFILE.configurationHash().equals(
                "c8cc557bd721228c473e30d31b7258510f9608a18098578bc1da36e603536215")
                || !PROFILE.activeGameplayRulesVersion().equals(
                SimulationRuntimeProfiles.PRE_JUNGLE_ACTIVE_GAMEPLAY_RULES_VERSION)
                || configuration.championMatchupMode() != ChampionMatchupMode.OFF
                || configuration.teamCompositionGameplayMode() != TeamCompositionGameplayMode.OFF
                || configuration.jungleClearContribution()
                != JungleClearContribution.DISABLED_NOT_INTEGRATED) {
            throw new IllegalStateException("MATCH_ENGINE_V1_APPROVED_RUNTIME_DRIFT");
        }
        String canonical = "policySchema=" + POLICY_SCHEMA + '\n'
                + "policyId=" + POLICY_ID + '\n'
                + "contractSchema=" + CONTRACT_SCHEMA + '\n'
                + "runtimeProfileId=" + PROFILE.profileId().name() + '\n'
                + "configurationHash=" + PROFILE.configurationHash() + '\n'
                + "activeGameplayRulesVersion=" + PROFILE.activeGameplayRulesVersion() + '\n'
                + "engineImplementationVersion="
                + SimulationProvenanceService.ENGINE_IMPLEMENTATION_VERSION + '\n'
                + "championMatchupMode=" + configuration.championMatchupMode().name() + '\n'
                + "teamCompositionGameplayMode="
                + configuration.teamCompositionGameplayMode().name() + '\n'
                + "jungleClearContribution=" + configuration.jungleClearContribution().name() + '\n'
                + "economyCandidateActivation=false\n"
                + "tempoCandidateActivation=false\n"
                + "diagnosticsExcludedFromGameplayIdentity=true\n";
        return new Snapshot(
                POLICY_SCHEMA, POLICY_ID, CONTRACT_SCHEMA, PROFILE.profileId(),
                PROFILE.configurationHash(), SimulationRuntimeProfiles.CONFIGURATION_HASH_ALGORITHM,
                configuration, PROFILE.activeGameplayRulesVersion(),
                SimulationProvenanceService.ENGINE_IMPLEMENTATION_VERSION,
                false, false, true, sha256(canonical), POLICY_HASH_ALGORITHM,
                LOW_LEVEL_DEFAULT_IDENTITY, false);
    }

    private static String sha256(String canonical) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(
                    canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException(error);
        }
    }

    public record Requirement(
            String policyId,
            SimulationRuntimeProfileId runtimeProfileId,
            String configurationHash,
            boolean economyCandidateActivation,
            boolean tempoCandidateActivation
    ) {
        public Requirement {
            policyId = required(policyId, "policyId");
            Objects.requireNonNull(runtimeProfileId, "runtimeProfileId");
            configurationHash = requiredHash(configurationHash, "configurationHash");
        }
    }

    public record Snapshot(
            String schemaVersion,
            String policyId,
            String contractSchemaVersion,
            SimulationRuntimeProfileId retainedRuntimeProfileId,
            String configurationHash,
            String configurationHashAlgorithm,
            SimulationGameplayConfiguration gameplayConfiguration,
            String activeGameplayRulesVersion,
            String engineImplementationVersion,
            boolean economyCandidateActivation,
            boolean tempoCandidateActivation,
            boolean diagnosticsExcludedFromGameplayIdentity,
            String policyHash,
            String policyHashAlgorithm,
            String lowLevelProductionDefaultsIdentity,
            boolean lowLevelProductionDefaultsAuthoritativeApplicationDefault
    ) {
        public Snapshot {
            schemaVersion = required(schemaVersion, "schemaVersion");
            policyId = required(policyId, "policyId");
            contractSchemaVersion = required(contractSchemaVersion, "contractSchemaVersion");
            Objects.requireNonNull(retainedRuntimeProfileId, "retainedRuntimeProfileId");
            configurationHash = requiredHash(configurationHash, "configurationHash");
            configurationHashAlgorithm = required(
                    configurationHashAlgorithm, "configurationHashAlgorithm");
            Objects.requireNonNull(gameplayConfiguration, "gameplayConfiguration");
            activeGameplayRulesVersion = required(
                    activeGameplayRulesVersion, "activeGameplayRulesVersion");
            engineImplementationVersion = required(
                    engineImplementationVersion, "engineImplementationVersion");
            policyHash = requiredHash(policyHash, "policyHash");
            policyHashAlgorithm = required(policyHashAlgorithm, "policyHashAlgorithm");
            lowLevelProductionDefaultsIdentity = required(
                    lowLevelProductionDefaultsIdentity, "lowLevelProductionDefaultsIdentity");
        }
    }

    static String required(String value, String field) {
        String normalized = Objects.requireNonNull(value, field).trim();
        if (normalized.isEmpty()) throw new IllegalArgumentException(field + " is required");
        return normalized;
    }

    static String requiredHash(String value, String field) {
        String hash = required(value, field);
        if (!hash.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException(field + " must be lowercase SHA-256");
        }
        return hash;
    }
}
