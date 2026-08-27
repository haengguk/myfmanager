package com.lolfm.application;

import com.lolfm.draft.DraftControlEvidence;
import com.lolfm.simulator.GameEndReason;
import com.lolfm.simulator.SimulationRandomFingerprint;
import com.lolfm.simulator.TeamSide;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/** Compact immutable identity retained after a player-controlled match execution. */
record SimulationReceipt(
        String schemaVersion,
        String matchIdentity,
        String productionPolicyId,
        String productionPolicyHash,
        String runtimeProfileId,
        String configurationHash,
        String engineImplementationVersion,
        String activeGameplayRulesVersion,
        String inputHash,
        String replayProvenanceHash,
        String resourceProvenanceHash,
        String draftDecisionHash,
        String finalDraftHash,
        String finalAssignmentHash,
        String controlPolicyId,
        String controlPolicyHash,
        String controlEvidenceHash,
        String simulatorTimelineHash,
        String structuredTimelineHash,
        String outputHash,
        String randomFingerprintSchema,
        long randomDrawCount,
        String randomTraceHash,
        String randomTraceHashAlgorithm,
        TeamSide winner,
        int durationSeconds,
        GameEndReason endReason
) {
    static final String SCHEMA = "PLAYER_DRAFT_SIMULATION_RECEIPT_V1";
    static final int MAX_CANONICAL_BYTES = 16 * 1024;

    SimulationReceipt {
        if (!SCHEMA.equals(required(schemaVersion, "schemaVersion"))) {
            throw new IllegalArgumentException("Unsupported simulation receipt schema");
        }
        matchIdentity = required(matchIdentity, "matchIdentity");
        productionPolicyId = required(productionPolicyId, "productionPolicyId");
        requireHash(productionPolicyHash, "productionPolicyHash");
        runtimeProfileId = required(runtimeProfileId, "runtimeProfileId");
        requireHash(configurationHash, "configurationHash");
        engineImplementationVersion = required(
                engineImplementationVersion, "engineImplementationVersion");
        activeGameplayRulesVersion = required(
                activeGameplayRulesVersion, "activeGameplayRulesVersion");
        requireHash(inputHash, "inputHash");
        requireHash(replayProvenanceHash, "replayProvenanceHash");
        requireHash(resourceProvenanceHash, "resourceProvenanceHash");
        requireHash(draftDecisionHash, "draftDecisionHash");
        requireHash(finalDraftHash, "finalDraftHash");
        requireHash(finalAssignmentHash, "finalAssignmentHash");
        controlPolicyId = required(controlPolicyId, "controlPolicyId");
        requireHash(controlPolicyHash, "controlPolicyHash");
        requireHash(controlEvidenceHash, "controlEvidenceHash");
        requireHash(simulatorTimelineHash, "simulatorTimelineHash");
        requireHash(structuredTimelineHash, "structuredTimelineHash");
        requireHash(outputHash, "outputHash");
        randomFingerprintSchema = required(
                randomFingerprintSchema, "randomFingerprintSchema");
        if (randomDrawCount < 0) throw new IllegalArgumentException("randomDrawCount");
        requireHash(randomTraceHash, "randomTraceHash");
        randomTraceHashAlgorithm = required(
                randomTraceHashAlgorithm, "randomTraceHashAlgorithm");
        if (durationSeconds < 0) throw new IllegalArgumentException("durationSeconds");
        Objects.requireNonNull(endReason, "endReason");
        if ((endReason == GameEndReason.NEXUS_DESTROYED && winner == null)
                || (endReason == GameEndReason.SIMULATION_TIMEOUT && winner != null)) {
            throw new IllegalArgumentException("Simulation receipt winner/end mismatch");
        }
    }

    static SimulationReceipt from(MatchEngineV1Output output) {
        Objects.requireNonNull(output, "output");
        SimulationExecutionProvenance execution = output.executionProvenance();
        MatchEngineV1Input.DraftInput draft = output.finalDraft();
        DraftControlEvidence control = Objects.requireNonNull(
                draft.controlEvidence(), "controlEvidence");
        SimulationRandomFingerprint random = execution.randomFingerprint();
        MatchEngineV1Output.MatchResultSummaryV1 summary = output.resultSummary();
        SimulationReceipt receipt = new SimulationReceipt(
                SCHEMA,
                output.matchIdentity(),
                output.productionPolicy().policyId(),
                output.productionPolicy().policyHash(),
                execution.runtimeProfileId().name(),
                output.configurationHash(),
                execution.engineImplementationVersion(),
                execution.activeGameplayRulesVersion(),
                output.inputHash(),
                execution.replayProvenanceHash(),
                execution.resourceProvenance().resourceProvenanceHash(),
                draft.draftDecisionHash(),
                draft.finalDraftHash(),
                draft.finalAssignmentHash(),
                control.policyId(),
                control.policyHash(),
                control.controlEvidenceHash(),
                output.simulatorTimelineHash(),
                output.structuredTimelineHash(),
                output.outputHash(),
                random.schemaVersion(),
                random.randomDrawCount(),
                random.randomTraceHash(),
                random.randomTraceHashAlgorithm(),
                summary.winner(),
                summary.durationSeconds(),
                summary.endReason());
        if (receipt.canonicalBytes().length > MAX_CANONICAL_BYTES) {
            throw new IllegalArgumentException("Simulation receipt exceeds compact size contract");
        }
        return receipt;
    }

    byte[] canonicalBytes() {
        return canonicalText().getBytes(StandardCharsets.UTF_8);
    }

    String canonicalText() {
        return new StringBuilder()
                .append("receiptSchema=").append(schemaVersion).append('\n')
                .append("matchIdentity=").append(matchIdentity).append('\n')
                .append("productionPolicyId=").append(productionPolicyId).append('\n')
                .append("productionPolicyHash=").append(productionPolicyHash).append('\n')
                .append("runtimeProfileId=").append(runtimeProfileId).append('\n')
                .append("configurationHash=").append(configurationHash).append('\n')
                .append("engineImplementationVersion=").append(engineImplementationVersion)
                .append('\n')
                .append("activeGameplayRulesVersion=").append(activeGameplayRulesVersion)
                .append('\n')
                .append("inputHash=").append(inputHash).append('\n')
                .append("replayProvenanceHash=").append(replayProvenanceHash).append('\n')
                .append("resourceProvenanceHash=").append(resourceProvenanceHash).append('\n')
                .append("draftDecisionHash=").append(draftDecisionHash).append('\n')
                .append("finalDraftHash=").append(finalDraftHash).append('\n')
                .append("finalAssignmentHash=").append(finalAssignmentHash).append('\n')
                .append("controlPolicyId=").append(controlPolicyId).append('\n')
                .append("controlPolicyHash=").append(controlPolicyHash).append('\n')
                .append("controlEvidenceHash=").append(controlEvidenceHash).append('\n')
                .append("simulatorTimelineHash=").append(simulatorTimelineHash).append('\n')
                .append("structuredTimelineHash=").append(structuredTimelineHash).append('\n')
                .append("outputHash=").append(outputHash).append('\n')
                .append("randomFingerprintSchema=").append(randomFingerprintSchema).append('\n')
                .append("randomDrawCount=").append(randomDrawCount).append('\n')
                .append("randomTraceHash=").append(randomTraceHash).append('\n')
                .append("randomTraceHashAlgorithm=").append(randomTraceHashAlgorithm).append('\n')
                .append("winner=").append(winner == null ? "NONE" : winner.name()).append('\n')
                .append("durationSeconds=").append(durationSeconds).append('\n')
                .append("endReason=").append(endReason.name()).append('\n')
                .toString();
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
