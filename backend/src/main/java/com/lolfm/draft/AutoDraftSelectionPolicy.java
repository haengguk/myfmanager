package com.lolfm.draft;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** Code-owned production policy for bounded, seeded Auto Draft selection. */
public record AutoDraftSelectionPolicy(
        String policyId,
        String mode,
        int maximumSelectableCandidates,
        long scoreScale,
        long maximumScoreLossFixed,
        String scoreCanonicalizationVersion,
        String contextHashAlgorithm,
        String drawAlgorithm,
        int banRankOneWeight,
        int banRankTwoWeight,
        int banRankThreeWeight,
        int pickRankOneWeight,
        int pickRankTwoWeight,
        int pickRankThreeWeight
) {
    public static final String POLICY_SCHEMA = "AUTO_DRAFT_SELECTION_POLICY_V1";
    public static final String POLICY_ID = "AUTO_DRAFT_VARIETY_V1";
    public static final String MODE = "SEEDED_BOUNDED_RANK_WEIGHTED_V1";
    public static final String SCORE_CANONICALIZATION_VERSION =
            "DECIMAL_DOUBLE_TO_FIXED_1E6_HALF_UP_V1";
    public static final String CONTEXT_HASH_ALGORITHM =
            "SHA256_UTF8_EXPLICIT_ORDERED_DRAFT_SELECTION_CONTEXT_LINES_TRAILING_NEWLINE_V1";
    public static final String DRAW_ALGORITHM =
            "SHA256_FULL_UNSIGNED_BIG_INTEGER_MOD_TOTAL_WEIGHT_V1";
    public static final String POLICY_HASH_ALGORITHM =
            "SHA256_UTF8_EXPLICIT_ORDERED_DRAFT_SELECTION_POLICY_LINES_TRAILING_NEWLINE_V1";
    public static final String APPROVED_POLICY_SHA256 =
            "b4645a9897329b6b0d50405a22ef788885a40ecede4b0fedd04e168211cf75cc";

    private static final AutoDraftSelectionPolicy PRODUCTION = new AutoDraftSelectionPolicy(
            POLICY_ID, MODE, 3, 1_000_000L, 2_000_000L,
            SCORE_CANONICALIZATION_VERSION, CONTEXT_HASH_ALGORITHM, DRAW_ALGORITHM,
            55, 30, 15, 70, 22, 8);

    static {
        if (!APPROVED_POLICY_SHA256.equals(PRODUCTION.policyHash())) {
            throw new IllegalStateException("Auto Draft selection policy hash drift");
        }
    }

    public AutoDraftSelectionPolicy {
        if (!POLICY_ID.equals(policyId) || !MODE.equals(mode)) {
            throw new IllegalArgumentException("Unsupported Auto Draft selection policy");
        }
        if (maximumSelectableCandidates != 3 || scoreScale != 1_000_000L
                || maximumScoreLossFixed != 2_000_000L) {
            throw new IllegalArgumentException("Auto Draft selection bounds drift");
        }
        if (!SCORE_CANONICALIZATION_VERSION.equals(scoreCanonicalizationVersion)
                || !CONTEXT_HASH_ALGORITHM.equals(contextHashAlgorithm)
                || !DRAW_ALGORITHM.equals(drawAlgorithm)) {
            throw new IllegalArgumentException("Auto Draft selection algorithm drift");
        }
        if (banRankOneWeight != 55 || banRankTwoWeight != 30
                || banRankThreeWeight != 15 || pickRankOneWeight != 70
                || pickRankTwoWeight != 22 || pickRankThreeWeight != 8) {
            throw new IllegalArgumentException("Auto Draft rank weights drift");
        }
    }

    public static AutoDraftSelectionPolicy production() {
        return PRODUCTION;
    }

    public int rankWeight(DraftActionType actionType, int canonicalRank) {
        if (canonicalRank < 1 || canonicalRank > maximumSelectableCandidates) {
            throw new IllegalArgumentException("Unsupported canonical rank: " + canonicalRank);
        }
        return switch (actionType) {
            case BAN -> switch (canonicalRank) {
                case 1 -> banRankOneWeight;
                case 2 -> banRankTwoWeight;
                case 3 -> banRankThreeWeight;
                default -> throw new IllegalStateException();
            };
            case PICK -> switch (canonicalRank) {
                case 1 -> pickRankOneWeight;
                case 2 -> pickRankTwoWeight;
                case 3 -> pickRankThreeWeight;
                default -> throw new IllegalStateException();
            };
        };
    }

    public String policyHash() {
        return sha256(canonicalPolicy());
    }

    public String canonicalPolicy() {
        return "policySchema=" + POLICY_SCHEMA + '\n'
                + "policyId=" + policyId + '\n'
                + "mode=" + mode + '\n'
                + "maximumSelectableCandidates=" + maximumSelectableCandidates + '\n'
                + "scoreCanonicalizationVersion=" + scoreCanonicalizationVersion + '\n'
                + "scoreScale=" + scoreScale + '\n'
                + "maximumScoreLossFixed=" + maximumScoreLossFixed + '\n'
                + "banRankWeight=1|" + banRankOneWeight + '\n'
                + "banRankWeight=2|" + banRankTwoWeight + '\n'
                + "banRankWeight=3|" + banRankThreeWeight + '\n'
                + "pickRankWeight=1|" + pickRankOneWeight + '\n'
                + "pickRankWeight=2|" + pickRankTwoWeight + '\n'
                + "pickRankWeight=3|" + pickRankThreeWeight + '\n'
                + "contextHashAlgorithm=" + contextHashAlgorithm + '\n'
                + "drawAlgorithm=" + drawAlgorithm + '\n';
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
