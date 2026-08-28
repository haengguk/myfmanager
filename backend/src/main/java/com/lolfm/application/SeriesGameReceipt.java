package com.lolfm.application;

import com.lolfm.draft.DraftControlEvidence;
import com.lolfm.simulator.GameEndReason;
import com.lolfm.simulator.SimulationRandomFingerprint;
import com.lolfm.simulator.TeamSide;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/** Compact committed-game identity; the full timeline is deliberately not retained. */
public record SeriesGameReceipt(
        String schemaVersion,
        String matchIdentity,
        String policyId,
        String policyHash,
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
        TeamSide winnerSide,
        int durationSeconds,
        GameEndReason endReason
) {
    public static final String SCHEMA = "SERIES_GAME_RECEIPT_V1";
    public static final int MAX_CANONICAL_BYTES = 16 * 1024;

    public SeriesGameReceipt {
        if (!SCHEMA.equals(required(schemaVersion, "schemaVersion"))) {
            throw new IllegalArgumentException("Unsupported Series game receipt schema");
        }
        matchIdentity = required(matchIdentity, "matchIdentity");
        policyId = required(policyId, "policyId");
        policyHash = hash(policyHash, "policyHash");
        runtimeProfileId = required(runtimeProfileId, "runtimeProfileId");
        configurationHash = hash(configurationHash, "configurationHash");
        engineImplementationVersion = required(
                engineImplementationVersion, "engineImplementationVersion");
        activeGameplayRulesVersion = required(
                activeGameplayRulesVersion, "activeGameplayRulesVersion");
        inputHash = hash(inputHash, "inputHash");
        replayProvenanceHash = hash(replayProvenanceHash, "replayProvenanceHash");
        resourceProvenanceHash = hash(resourceProvenanceHash, "resourceProvenanceHash");
        draftDecisionHash = hash(draftDecisionHash, "draftDecisionHash");
        finalDraftHash = hash(finalDraftHash, "finalDraftHash");
        finalAssignmentHash = hash(finalAssignmentHash, "finalAssignmentHash");
        controlPolicyId = required(controlPolicyId, "controlPolicyId");
        controlPolicyHash = hash(controlPolicyHash, "controlPolicyHash");
        controlEvidenceHash = hash(controlEvidenceHash, "controlEvidenceHash");
        simulatorTimelineHash = hash(simulatorTimelineHash, "simulatorTimelineHash");
        structuredTimelineHash = hash(structuredTimelineHash, "structuredTimelineHash");
        outputHash = hash(outputHash, "outputHash");
        randomFingerprintSchema = required(
                randomFingerprintSchema, "randomFingerprintSchema");
        if (randomDrawCount < 0 || durationSeconds < 0) {
            throw new IllegalArgumentException("Invalid Series game receipt number");
        }
        randomTraceHash = hash(randomTraceHash, "randomTraceHash");
        randomTraceHashAlgorithm = required(
                randomTraceHashAlgorithm, "randomTraceHashAlgorithm");
        Objects.requireNonNull(endReason, "endReason");
    }

    static SeriesGameReceipt from(MatchEngineV1Output output) {
        SimulationExecutionProvenance execution = output.executionProvenance();
        DraftControlEvidence control = Objects.requireNonNull(
                output.finalDraft().controlEvidence(), "controlEvidence");
        SimulationRandomFingerprint random = execution.randomFingerprint();
        SeriesGameReceipt receipt = new SeriesGameReceipt(
                SCHEMA, output.matchIdentity(), output.productionPolicy().policyId(),
                output.productionPolicy().policyHash(), execution.runtimeProfileId().name(),
                output.configurationHash(), execution.engineImplementationVersion(),
                execution.activeGameplayRulesVersion(), output.inputHash(),
                execution.replayProvenanceHash(),
                execution.resourceProvenance().resourceProvenanceHash(),
                output.finalDraft().draftDecisionHash(), output.finalDraft().finalDraftHash(),
                output.finalDraft().finalAssignmentHash(), control.policyId(),
                control.policyHash(), control.controlEvidenceHash(),
                output.simulatorTimelineHash(), output.structuredTimelineHash(),
                output.outputHash(), random.schemaVersion(), random.randomDrawCount(),
                random.randomTraceHash(), random.randomTraceHashAlgorithm(),
                output.resultSummary().winner(), output.resultSummary().durationSeconds(),
                output.resultSummary().endReason());
        if (receipt.canonicalBytes().length > MAX_CANONICAL_BYTES) {
            throw new IllegalArgumentException("Series game receipt exceeds compact size contract");
        }
        return receipt;
    }

    public byte[] canonicalBytes() {
        return canonicalText().getBytes(StandardCharsets.UTF_8);
    }

    public String canonicalText() {
        StringBuilder value = new StringBuilder();
        java.util.Arrays.stream(getClass().getRecordComponents()).forEach(component -> {
            try {
                Object field = component.getAccessor().invoke(this);
                value.append(component.getName()).append('=').append(
                        field == null ? "NONE" : field).append('\n');
            } catch (ReflectiveOperationException error) {
                throw new IllegalStateException(error);
            }
        });
        return value.toString();
    }

    private static String required(String value, String field) {
        String normalized = Objects.requireNonNull(value, field).trim();
        if (normalized.isEmpty()) throw new IllegalArgumentException(field);
        return normalized;
    }

    private static String hash(String value, String field) {
        String normalized = required(value, field);
        if (!normalized.matches("[0-9a-f]{64}")) throw new IllegalArgumentException(field);
        return normalized;
    }
}
