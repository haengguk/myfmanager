package com.lolfm.application;

import com.lolfm.simulator.SimulationGameplayConfiguration;
import com.lolfm.simulator.SimulationInstrumentation;
import com.lolfm.simulator.SimulationRandomFingerprint;
import com.lolfm.simulator.SimulationRuntimeProfileId;
import java.util.Objects;

/** Structured configuration, replay-input, and output identity for one match execution. */
public record SimulationExecutionProvenance(
        String schemaVersion,
        SimulationRuntimeProfileId runtimeProfileId,
        SimulationGameplayConfiguration resolvedGameplayConfiguration,
        String configurationHash,
        String configurationHashAlgorithm,
        SimulationInstrumentation instrumentation,
        String engineRulesVersion,
        String engineImplementationVersion,
        String activeGameplayRulesVersion,
        SimulationResourceProvenance resourceProvenance,
        String blueTeamCode,
        String redTeamCode,
        String rosterIdentityHash,
        long matchSeed,
        int seriesGameNumber,
        String seriesHistoryBeforeHash,
        String draftRuleSetIdentity,
        String draftRuleSetHash,
        String draftScoringPolicyHash,
        String draftSelectionPolicyId,
        String draftSelectionPolicyHash,
        String draftSelectionTraceHash,
        String draftDecisionHash,
        String finalDraftHash,
        String finalAssignmentHash,
        String replayProvenanceHash,
        String replayProvenanceHashAlgorithm,
        String timelineHash,
        String timelineHashAlgorithm,
        SimulationRandomFingerprint randomFingerprint
) {
    public static final String SCHEMA = "SIMULATION_EXECUTION_PROVENANCE_V3";

    public SimulationExecutionProvenance {
        schemaVersion = required(schemaVersion, "schemaVersion");
        if (!SCHEMA.equals(schemaVersion)) {
            throw new IllegalArgumentException("Unsupported execution provenance schema");
        }
        Objects.requireNonNull(runtimeProfileId, "runtimeProfileId");
        Objects.requireNonNull(resolvedGameplayConfiguration, "resolvedGameplayConfiguration");
        configurationHash = requiredHash(configurationHash, "configurationHash");
        configurationHashAlgorithm = required(
                configurationHashAlgorithm, "configurationHashAlgorithm");
        Objects.requireNonNull(instrumentation, "instrumentation");
        engineRulesVersion = required(engineRulesVersion, "engineRulesVersion");
        engineImplementationVersion = required(
                engineImplementationVersion, "engineImplementationVersion");
        activeGameplayRulesVersion = required(
                activeGameplayRulesVersion, "activeGameplayRulesVersion");
        if (!engineRulesVersion.equals(activeGameplayRulesVersion)) {
            throw new IllegalArgumentException(
                    "engineRulesVersion compatibility alias must equal activeGameplayRulesVersion");
        }
        Objects.requireNonNull(resourceProvenance, "resourceProvenance");
        blueTeamCode = required(blueTeamCode, "blueTeamCode");
        redTeamCode = required(redTeamCode, "redTeamCode");
        rosterIdentityHash = requiredHash(rosterIdentityHash, "rosterIdentityHash");
        if (seriesGameNumber < 1) {
            throw new IllegalArgumentException("seriesGameNumber must be positive");
        }
        seriesHistoryBeforeHash = requiredHash(
                seriesHistoryBeforeHash, "seriesHistoryBeforeHash");
        draftRuleSetIdentity = required(draftRuleSetIdentity, "draftRuleSetIdentity");
        draftRuleSetHash = requiredHash(draftRuleSetHash, "draftRuleSetHash");
        draftScoringPolicyHash = requiredHash(
                draftScoringPolicyHash, "draftScoringPolicyHash");
        draftSelectionPolicyId = required(
                draftSelectionPolicyId, "draftSelectionPolicyId");
        draftSelectionPolicyHash = requiredHash(
                draftSelectionPolicyHash, "draftSelectionPolicyHash");
        draftSelectionTraceHash = requiredHash(
                draftSelectionTraceHash, "draftSelectionTraceHash");
        draftDecisionHash = requiredHash(draftDecisionHash, "draftDecisionHash");
        finalDraftHash = requiredHash(finalDraftHash, "finalDraftHash");
        finalAssignmentHash = requiredHash(finalAssignmentHash, "finalAssignmentHash");
        replayProvenanceHash = requiredHash(
                replayProvenanceHash, "replayProvenanceHash");
        replayProvenanceHashAlgorithm = required(
                replayProvenanceHashAlgorithm, "replayProvenanceHashAlgorithm");
        timelineHash = requiredHash(timelineHash, "timelineHash");
        timelineHashAlgorithm = required(timelineHashAlgorithm, "timelineHashAlgorithm");
        Objects.requireNonNull(randomFingerprint, "randomFingerprint");
    }

    private static String required(String value, String field) {
        String normalized = Objects.requireNonNull(value, field).trim();
        if (normalized.isEmpty()) throw new IllegalArgumentException(field + " is required");
        return normalized;
    }

    private static String requiredHash(String value, String field) {
        String hash = required(value, field);
        if (!hash.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException(field + " must be lowercase SHA-256");
        }
        return hash;
    }
}
