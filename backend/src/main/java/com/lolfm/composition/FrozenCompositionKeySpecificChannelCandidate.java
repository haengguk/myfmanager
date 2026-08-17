package com.lolfm.composition;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/** Immutable semantic identity of the Phase 13D-4C.6 key-specific development candidate. */
public final class FrozenCompositionKeySpecificChannelCandidate {
    public static final String VERSION = "composition-key-specific-channel-calibration-candidate-v1";
    public static final String HASH = "a99f112779a1735339bc124c1d444dda61e69ce336c699da73fdf12c43078b1a";
    public static final String ROLE = "POST_HOLDOUT_DEVELOPMENT_CANDIDATE";
    public static final double SKIRMISH_WINNER_GAIN = 24.509721397259;
    public static final double TEAMFIGHT_WINNER_GAIN = 80.535608461244;
    public static final double SIEGE_WINNER_GAIN = 69.065220882615;
    public static final double BASE_DEFENSE_WINNER_GAIN = 113.604264099974;
    public static final double TEAMFIGHT_SEVERITY_GAIN = 0.0;
    public static final double SIEGE_SEVERITY_GAIN = 0.0;
    public static final double BASE_DEFENSE_SEVERITY_GAIN = 0.0;

    private static final String CANONICAL = """
            candidateVersion=composition-key-specific-channel-calibration-candidate-v1
            candidateRole=POST_HOLDOUT_DEVELOPMENT_CANDIDATE
            profileHash=fbf58dc5be12f2b07c5dff7ded9e182d7829999d2255e65dbbd073ccde2688d1
            ruleCatalogHash=f0480eb8e9620d02a0187da384224d3735717ad5f5f2e1ca9e904aea4c7ae7d4
            interactionCandidateHash=0f92b3f9d3ea81f9d20531341167efe1c0a8c1a9d8b593f27d28b7745c0bb49b
            blueprintVersion=composition-key-specific-application-semantics-blueprint-v1
            blueprintHash=6287bd537e29c488e0cbc9a2bc7636a3a76b44791c43420fdd8a40703edc8964
            winnerSafetyPolicy=composition-winner-decision-space-safety-policy-v1
            severitySafetyPolicy=composition-severity-decision-space-safety-policy-v1
            targetRatioGridId=phase-13d4b2-frozen-target-ratio-grid-v1
            targetRatioGridHash=bfbd00579e85be2354635e94fe55cfcb557771ee679728ebe7701c103bda61aa
            calibrationDatasetHash=a90cfcabed8bac89a9dd9df196f02fa6fc1110b94b91cdeb253f9b6565c0318c
            internalValidationDatasetHash=344d00eb8bc284e980bba916e4d2e42abc1bddfb39ae06af5feb641824fd58a1
            ruleChannelMappingHash=9a2d7946349f4b5213053b422991f95468bf4b0c0e88cb44d8145316cb3907de
            SKIRMISH.winnerTransform=EXISTING_FROZEN_HALF_SPLIT_SCORE_PROJECTION
            SKIRMISH.winnerGain=24.509721397259
            SKIRMISH.winnerStatus=FROZEN_EXISTING_WINNER_GAIN
            SKIRMISH.severity=NOT_APPLICABLE
            TEAMFIGHT.winnerTransform=EXISTING_CONTEXT_AGGREGATE_EDGE_DECISION_LOCAL
            TEAMFIGHT.winnerTransformHash=a81b03a090cdd046f1bd73808024fa596d74a4ddd64a9487439d73c6caeef688
            TEAMFIGHT.winnerTargetRatio=0.050000000000
            TEAMFIGHT.winnerGain=80.535608461244
            TEAMFIGHT.severityTransform=MAX_ABSOLUTE_ELIGIBLE_RULE_PRODUCT_EXPOSURE_EDGE_V1
            TEAMFIGHT.severityTransformHash=8df9df756e9be31ab783d590535b0e552b22b0f2db142f1f0ca1917bdf9cf731
            TEAMFIGHT.severityTargetRatio=0.000000000000
            TEAMFIGHT.severityGain=0.000000000000
            TEAMFIGHT.severityStatus=ZERO_REFERENCE_SELECTED_BY_SCREENING
            SIEGE.winnerTransform=EXISTING_CONTEXT_AGGREGATE_EDGE_DECISION_LOCAL
            SIEGE.winnerTransformHash=a81b03a090cdd046f1bd73808024fa596d74a4ddd64a9487439d73c6caeef688
            SIEGE.winnerTargetRatio=0.050000000000
            SIEGE.winnerGain=69.065220882615
            SIEGE.severityTransform=MAX_ABSOLUTE_ELIGIBLE_RULE_PRODUCT_EXPOSURE_EDGE_V1
            SIEGE.severityTransformHash=9072086b9bbcb4c38b46a634cdd0aa5d175756d32c89cac1a4a8537cad6c175d
            SIEGE.severityTargetRatio=0.000000000000
            SIEGE.severityGain=0.000000000000
            SIEGE.severityStatus=ZERO_REFERENCE_SELECTED_BY_SCREENING
            BASE_DEFENSE.winnerTransform=ROLE_ORIENTED_PRODUCT_EXPOSURE_CONTEXT_EDGE_V1
            BASE_DEFENSE.winnerTransformHash=f9e3a7f221a880632dfeffe6db9707ebaca86f2b456179fb38042d4ced55529a
            BASE_DEFENSE.winnerTargetRatio=0.050000000000
            BASE_DEFENSE.winnerGain=113.604264099974
            BASE_DEFENSE.severityTransform=MAX_ABSOLUTE_ELIGIBLE_RULE_PRODUCT_EXPOSURE_EDGE_V1
            BASE_DEFENSE.severityTransformHash=01372c21383d8b06c3bbdf981d43fa3915887596b0e0d38e15ffcbdb869a49fb
            BASE_DEFENSE.severityTargetRatio=0.000000000000
            BASE_DEFENSE.severityGain=0.000000000000
            BASE_DEFENSE.severityStatus=ZERO_REFERENCE_SELECTED_BY_SCREENING
            freshHoldoutRequired=true
            jointGameplayValidated=false
            freshHoldoutPassed=false
            productionEligible=false
            """;

    private FrozenCompositionKeySpecificChannelCandidate() {}
    public static String canonical() { return CANONICAL; }
    public static String canonicalHash() { return sha256(CANONICAL); }
    public static void verifyIdentity(String version, String hash) {
        if (!VERSION.equals(version) || !HASH.equals(hash) || !HASH.equals(canonicalHash())) {
            throw new CompositionGameplayConfigurationException(
                    "COMPOSITION_KEY_SPECIFIC_CANDIDATE_IDENTITY_MISMATCH",
                    "Key-specific candidate identity does not match the frozen semantic canonical projection");
        }
    }
    public static double winnerGain(TeamCompositionContext context) {
        return switch (context) {
            case SKIRMISH -> SKIRMISH_WINNER_GAIN;
            case TEAMFIGHT -> TEAMFIGHT_WINNER_GAIN;
            case SIEGE -> SIEGE_WINNER_GAIN;
            case BASE_DEFENSE -> BASE_DEFENSE_WINNER_GAIN;
            default -> throw new IllegalArgumentException("Candidate has no winner gain for " + context);
        };
    }
    private static String sha256(String value) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) result.append(String.format("%02x", b));
            return result.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
