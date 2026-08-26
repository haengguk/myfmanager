package com.lolfm.draft;

import com.lolfm.champion.ChampionId;
import com.lolfm.simulator.TeamSide;
import java.util.List;
import java.util.Objects;

/** Structured observational proof for one actual Draft selection. */
public record DraftSelectionTrace(
        String policyId,
        String policyMode,
        String policyHash,
        String selectionContextHash,
        int turn,
        TeamSide side,
        DraftActionType actionType,
        ChampionId bestCandidateId,
        long bestCanonicalScore,
        List<DraftSelectionPoolEntry> eligiblePool,
        ChampionId selectedChampionId,
        int selectedRank,
        long selectedCanonicalScoreLoss,
        Integer drawBucket,
        int totalEligibleWeight,
        DraftSelectionReason reason
) {
    public DraftSelectionTrace {
        policyId = required(policyId, "policyId");
        policyMode = required(policyMode, "policyMode");
        policyHash = requiredHash(policyHash, "policyHash");
        selectionContextHash = requiredHash(selectionContextHash, "selectionContextHash");
        if (turn < 1) throw new IllegalArgumentException("turn must be positive");
        Objects.requireNonNull(side, "side");
        Objects.requireNonNull(actionType, "actionType");
        Objects.requireNonNull(bestCandidateId, "bestCandidateId");
        eligiblePool = List.copyOf(eligiblePool);
        Objects.requireNonNull(selectedChampionId, "selectedChampionId");
        Objects.requireNonNull(reason, "reason");
        if (eligiblePool.isEmpty() || eligiblePool.size() > 3 || selectedRank < 1
                || selectedRank > eligiblePool.size() || selectedCanonicalScoreLoss < 0
                || totalEligibleWeight < 1) {
            throw new IllegalArgumentException("Invalid Draft selection trace bounds");
        }
        for (int index = 0; index < eligiblePool.size(); index++) {
            if (eligiblePool.get(index).canonicalRank() != index + 1) {
                throw new IllegalArgumentException("Draft selection pool ranks must be contiguous");
            }
        }
        DraftSelectionPoolEntry selected = eligiblePool.get(selectedRank - 1);
        if (!eligiblePool.getFirst().championId().equals(bestCandidateId)
                || eligiblePool.getFirst().canonicalFinalScore() != bestCanonicalScore
                || !selected.championId().equals(selectedChampionId)
                || selected.canonicalScoreLoss() != selectedCanonicalScoreLoss) {
            throw new IllegalArgumentException("Draft selection trace identity mismatch");
        }
        if (reason == DraftSelectionReason.ONLY_ONE_WITHIN_WINDOW) {
            if (eligiblePool.size() != 1 || selectedRank != 1 || drawBucket != null) {
                throw new IllegalArgumentException("Singleton Draft selection must not draw");
            }
        } else if (drawBucket == null || drawBucket < 0 || drawBucket >= totalEligibleWeight) {
            throw new IllegalArgumentException("Weighted Draft selection requires a valid bucket");
        }
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
