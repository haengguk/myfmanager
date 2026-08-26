package com.lolfm.draft;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;

public final class DraftSelectionTraceHasher {
    public static final String TRACE_HASH_ALGORITHM =
            "SHA256_UTF8_EXPLICIT_ORDERED_DRAFT_SELECTION_TRACE_LINES_TRAILING_NEWLINE_V1";

    private DraftSelectionTraceHasher() {
    }

    public static String hash(List<DraftSelectionTrace> traces) {
        Objects.requireNonNull(traces, "traces");
        StringBuilder canonical = new StringBuilder(
                "traceSetSchema=AUTO_DRAFT_SELECTION_TRACE_SET_V1\n")
                .append("traceCount=").append(traces.size()).append('\n');
        for (int index = 0; index < traces.size(); index++) {
            DraftSelectionTrace trace = traces.get(index);
            canonical.append("trace=").append(index).append('|')
                    .append(traceHash(trace)).append('\n');
        }
        return sha256(canonical.toString());
    }

    public static String traceHash(DraftSelectionTrace trace) {
        Objects.requireNonNull(trace, "trace");
        StringBuilder canonical = new StringBuilder(
                "traceSchema=AUTO_DRAFT_SELECTION_TRACE_V1\n")
                .append("policyId=").append(trace.policyId()).append('\n')
                .append("policyMode=").append(trace.policyMode()).append('\n')
                .append("policyHash=").append(trace.policyHash()).append('\n')
                .append("selectionContextHash=").append(trace.selectionContextHash()).append('\n')
                .append("turn=").append(trace.turn()).append('\n')
                .append("side=").append(trace.side().name()).append('\n')
                .append("actionType=").append(trace.actionType().name()).append('\n')
                .append("bestCandidateId=").append(trace.bestCandidateId().value()).append('\n')
                .append("bestCanonicalScore=").append(trace.bestCanonicalScore()).append('\n');
        trace.eligiblePool().forEach(entry -> canonical.append("eligibleCandidate=")
                .append(entry.canonicalRank()).append('|').append(entry.championId().value())
                .append('|').append(Double.toHexString(entry.rawFinalSearchScore()))
                .append('|').append(entry.canonicalFinalScore()).append('|')
                .append(entry.canonicalScoreLoss()).append('|').append(entry.rankWeight())
                .append('\n'));
        canonical.append("selectedChampionId=").append(trace.selectedChampionId().value())
                .append('\n')
                .append("selectedRank=").append(trace.selectedRank()).append('\n')
                .append("selectedCanonicalScoreLoss=")
                .append(trace.selectedCanonicalScoreLoss()).append('\n')
                .append("drawBucket=")
                .append(trace.drawBucket() == null ? "NONE" : trace.drawBucket()).append('\n')
                .append("totalEligibleWeight=").append(trace.totalEligibleWeight()).append('\n')
                .append("reason=").append(trace.reason().name()).append('\n');
        return sha256(canonical.toString());
    }

    private static String sha256(String canonical) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(
                    canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException(error);
        }
    }
}
