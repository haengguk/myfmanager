package com.lolfm.application;

import com.lolfm.champion.ChampionMatchupMode;
import com.lolfm.composition.TeamCompositionGameplayMode;
import com.lolfm.draft.AutoDraftSelectionPolicy;
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
import java.util.List;
import java.util.Objects;

/** Authoritative, code-owned production runtime policy for the Match Engine V1 boundary. */
public final class MatchEngineV1Policy {
    public static final String CONTRACT_SCHEMA = "MATCH_ENGINE_CONTRACT_V1";
    public static final String POLICY_SCHEMA = "MATCH_ENGINE_V1_PRODUCTION_POLICY_V3";
    public static final String POLICY_ID =
            "MATCH_ENGINE_V1_MATCHUP_COMPOSITION_ACCEPTED_PRODUCTION_POLICY";
    public static final String ACTIVATION_DECISION_SCHEMA =
            "MATCH_ENGINE_V9_MATCHUP_COMPOSITION_PRODUCTION_ACTIVATION_DECISION_V1";
    public static final String ACTIVATION_DECISION_CODE =
            "PRODUCT_DECISION_ACCEPT_WITH_KNOWN_DIAGNOSTIC_LIMITATION";
    public static final String KNOWN_DIAGNOSTIC_LIMITATION =
            "MATCHUP_CAUSAL_LINEAGE_UNRESOLVED_399_OF_400_CALIBRATION_PUBLIC_DIVERGENCES";
    public static final String COMPOSITION_NEXUS_ENDING_SENSITIVITY_LIMITATION =
            "COMPOSITION_NEXUS_ENDING_SENSITIVITY_9_25_PERCENT_EXCEEDS_PROPOSED_7_5_PERCENT_TOLERANCE";
    public static final List<String> KNOWN_DIAGNOSTIC_LIMITATIONS = List.of(
            KNOWN_DIAGNOSTIC_LIMITATION,
            COMPOSITION_NEXUS_ENDING_SENSITIVITY_LIMITATION);
    public static final String ACCEPTANCE_STATUS =
            "PRODUCT_ACCEPTED_WITH_KNOWN_LIMITATIONS_NOT_STATISTICAL_HOLDOUT";
    public static final SimulationRuntimeProfileId ROLLBACK_PROFILE_ID =
            SimulationRuntimeProfileId.BASELINE_V1;
    public static final String ROLLBACK_MODE = "EXPLICIT_VERSIONED_POLICY_CHANGE_ONLY";
    public static final boolean AUTOMATIC_FALLBACK = false;
    public static final String APPROVED_POLICY_SHA256 =
            "78c3bb1cffe2cd90a1f7acab6923a1813fea40acd135186ff522eabf95d38493";
    /** Historical Final 13G evidence identity; retained for audit compatibility only. */
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
    public static final String DRAFT_SELECTION_POLICY_ID =
            AutoDraftSelectionPolicy.POLICY_ID;
    public static final String DRAFT_SELECTION_POLICY_SHA256 =
            AutoDraftSelectionPolicy.APPROVED_POLICY_SHA256;
    public static final String POLICY_HASH_ALGORITHM =
            "SHA256_UTF8_EXPLICIT_ORDERED_POLICY_LINES_TRAILING_NEWLINE_V1";
    public static final String LOW_LEVEL_DEFAULT_IDENTITY =
            "LOW_LEVEL_SIMULATION_OPTIONS_PRODUCTION_DEFAULTS";

    private static final ResolvedSimulationRuntimeProfile PROFILE =
            SimulationRuntimeProfiles.resolve(
                    SimulationRuntimeProfileId.PRODUCTION_MATCHUP_COMPOSITION_V1);
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
                || !requirement.draftSelectionPolicyId().equals(DRAFT_SELECTION_POLICY_ID)
                || !requirement.draftSelectionPolicyHash().equals(
                DRAFT_SELECTION_POLICY_SHA256)
                || requirement.runtimeProfileId()
                != SimulationRuntimeProfileId.PRODUCTION_MATCHUP_COMPOSITION_V1
                || !requirement.configurationHash().equals(PROFILE.configurationHash())
                || requirement.economyCandidateActivation()
                || requirement.tempoCandidateActivation()) {
            throw new IllegalArgumentException("MATCH_ENGINE_V1_PRODUCTION_POLICY_MISMATCH");
        }
    }

    public static Requirement requirement() {
        return new Requirement(POLICY_ID, DRAFT_SELECTION_POLICY_ID,
                DRAFT_SELECTION_POLICY_SHA256,
                SimulationRuntimeProfileId.PRODUCTION_MATCHUP_COMPOSITION_V1,
                PROFILE.configurationHash(), false, false);
    }

    /** Low-level defaults can align with production semantics without owning product authority. */
    public static boolean isLowLevelProductionDefaultsAlignedWithAuthoritativeProfile() {
        SimulationOptions lowLevel = SimulationOptions.productionDefaults();
        return lowLevel.championMatchupMode()
                == PROFILE.gameplayConfiguration().championMatchupMode()
                && lowLevel.teamCompositionGameplayMode()
                == PROFILE.gameplayConfiguration().teamCompositionGameplayMode()
                && lowLevel.jungleClearContribution()
                == PROFILE.gameplayConfiguration().jungleClearContribution();
    }

    /** @deprecated Product authority is owned only by this policy, never by low-level defaults. */
    @Deprecated(forRemoval = false)
    public static boolean isLowLevelProductionDefaultsAuthoritative() {
        return false;
    }

    private static Snapshot createAndVerify() {
        SimulationGameplayConfiguration configuration = PROFILE.gameplayConfiguration();
        if (!PROFILE.configurationHash().equals(
                "caaf76274dc148040b0a95eae1ed5181790b2fc840f45af9b109ea7951c1fd5d")
                || !PROFILE.activeGameplayRulesVersion().equals(
                SimulationRuntimeProfiles.PRE_JUNGLE_ACTIVE_GAMEPLAY_RULES_VERSION)
                || configuration.championMatchupMode() != ChampionMatchupMode.GEOMETRIC_V2
                || configuration.teamCompositionGameplayMode()
                != TeamCompositionGameplayMode.PRODUCTION_V2
                || configuration.jungleClearContribution()
                != JungleClearContribution.DISABLED_NOT_INTEGRATED) {
            throw new IllegalStateException("MATCH_ENGINE_V1_APPROVED_RUNTIME_DRIFT");
        }
        String canonical = "policySchema=" + POLICY_SCHEMA + '\n'
                + "policyId=" + POLICY_ID + '\n'
                + "activationDecisionSchema=" + ACTIVATION_DECISION_SCHEMA + '\n'
                + "activationDecisionCode=" + ACTIVATION_DECISION_CODE + '\n'
                + "acceptanceStatus=" + ACCEPTANCE_STATUS + '\n'
                + "knownDiagnosticLimitation=" + KNOWN_DIAGNOSTIC_LIMITATION + '\n'
                + "knownDiagnosticLimitations="
                + String.join("|", KNOWN_DIAGNOSTIC_LIMITATIONS) + '\n'
                + "statisticalHoldoutApproved=false\n"
                + "rollbackProfileId=" + ROLLBACK_PROFILE_ID.name() + '\n'
                + "rollbackMode=" + ROLLBACK_MODE + '\n'
                + "automaticFallback=false\n"
                + "contractSchema=" + CONTRACT_SCHEMA + '\n'
                + "draftSelectionPolicyId=" + DRAFT_SELECTION_POLICY_ID + '\n'
                + "draftSelectionPolicyHash=" + DRAFT_SELECTION_POLICY_SHA256 + '\n'
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
        String policyHash = sha256(canonical);
        if (!APPROVED_POLICY_SHA256.equals(policyHash)) {
            throw new IllegalStateException("MATCH_ENGINE_V1_APPROVED_POLICY_DRIFT");
        }
        return new Snapshot(
                POLICY_SCHEMA, POLICY_ID, ACTIVATION_DECISION_SCHEMA,
                ACTIVATION_DECISION_CODE, ACCEPTANCE_STATUS,
                KNOWN_DIAGNOSTIC_LIMITATION, KNOWN_DIAGNOSTIC_LIMITATIONS, false,
                ROLLBACK_PROFILE_ID, ROLLBACK_MODE, AUTOMATIC_FALLBACK,
                CONTRACT_SCHEMA,
                DRAFT_SELECTION_POLICY_ID, DRAFT_SELECTION_POLICY_SHA256,
                PROFILE.profileId(),
                PROFILE.configurationHash(), SimulationRuntimeProfiles.CONFIGURATION_HASH_ALGORITHM,
                configuration, PROFILE.activeGameplayRulesVersion(),
                SimulationProvenanceService.ENGINE_IMPLEMENTATION_VERSION,
                false, false, true, policyHash, POLICY_HASH_ALGORITHM,
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
            String draftSelectionPolicyId,
            String draftSelectionPolicyHash,
            SimulationRuntimeProfileId runtimeProfileId,
            String configurationHash,
            boolean economyCandidateActivation,
            boolean tempoCandidateActivation
    ) {
        public Requirement {
            policyId = required(policyId, "policyId");
            draftSelectionPolicyId = required(
                    draftSelectionPolicyId, "draftSelectionPolicyId");
            draftSelectionPolicyHash = requiredHash(
                    draftSelectionPolicyHash, "draftSelectionPolicyHash");
            Objects.requireNonNull(runtimeProfileId, "runtimeProfileId");
            configurationHash = requiredHash(configurationHash, "configurationHash");
        }
    }

    public record Snapshot(
            String schemaVersion,
            String policyId,
            String activationDecisionSchema,
            String activationDecisionCode,
            String acceptanceStatus,
            String knownDiagnosticLimitation,
            List<String> knownDiagnosticLimitations,
            boolean statisticalHoldoutApproved,
            SimulationRuntimeProfileId rollbackProfileId,
            String rollbackMode,
            boolean automaticFallback,
            String contractSchemaVersion,
            String draftSelectionPolicyId,
            String draftSelectionPolicyHash,
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
            activationDecisionSchema = required(
                    activationDecisionSchema, "activationDecisionSchema");
            activationDecisionCode = required(activationDecisionCode, "activationDecisionCode");
            acceptanceStatus = required(acceptanceStatus, "acceptanceStatus");
            knownDiagnosticLimitation = required(
                    knownDiagnosticLimitation, "knownDiagnosticLimitation");
            knownDiagnosticLimitations = List.copyOf(Objects.requireNonNull(
                    knownDiagnosticLimitations, "knownDiagnosticLimitations"));
            if (knownDiagnosticLimitations.isEmpty()
                    || !knownDiagnosticLimitations.getFirst().equals(knownDiagnosticLimitation)
                    || knownDiagnosticLimitations.stream().anyMatch(value -> value == null
                    || value.isBlank())) {
                throw new IllegalArgumentException("knownDiagnosticLimitations is invalid");
            }
            Objects.requireNonNull(rollbackProfileId, "rollbackProfileId");
            rollbackMode = required(rollbackMode, "rollbackMode");
            contractSchemaVersion = required(contractSchemaVersion, "contractSchemaVersion");
            draftSelectionPolicyId = required(
                    draftSelectionPolicyId, "draftSelectionPolicyId");
            draftSelectionPolicyHash = requiredHash(
                    draftSelectionPolicyHash, "draftSelectionPolicyHash");
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
