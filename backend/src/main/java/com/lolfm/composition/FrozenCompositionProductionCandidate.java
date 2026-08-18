package com.lolfm.composition;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/** Immutable Phase 13D production candidate, frozen before the final blind holdout. */
public final class FrozenCompositionProductionCandidate {
    public static final String VERSION = "composition-key-specific-channel-calibration-candidate-v2";
    public static final String HASH = "9b86f821ed038fac31b73c635d8f1d1ea7e52a682661e09102afc787fd9dc1b7";
    public static final String ROLE = "PHASE_13D_FINAL_PRODUCTION_CANDIDATE";
    public static final double SKIRMISH_WINNER_GAIN = 24.509721397259;
    public static final double TEAMFIGHT_WINNER_GAIN = 80.535608461244;
    public static final double SIEGE_WINNER_GAIN = 69.065220882615;
    public static final double BASE_DEFENSE_WINNER_GAIN = 56.802132049987;
    public static final double TEAMFIGHT_SEVERITY_GAIN = 0.0;
    public static final double SIEGE_SEVERITY_GAIN = 0.0;
    public static final double BASE_DEFENSE_SEVERITY_GAIN = 0.0;

    private static final String CANONICAL = """
            candidateVersion=composition-key-specific-channel-calibration-candidate-v2
            candidateRole=PHASE_13D_FINAL_PRODUCTION_CANDIDATE
            sourceCandidateVersion=composition-key-specific-channel-calibration-candidate-v1
            sourceCandidateHash=a99f112779a1735339bc124c1d444dda61e69ce336c699da73fdf12c43078b1a
            correctedCalibrationSourceSummaryHash=bd1a8de72183ec9be14fa6eca0c08aaa95fae42756236f06c40b88802fb783a2
            correctedCalibrationSourceAuditHash=c77af827be80b24d906d7e0023a6a4a2766cd0229222efc38fd72c5b9f0a2aa6
            profileHash=fbf58dc5be12f2b07c5dff7ded9e182d7829999d2255e65dbbd073ccde2688d1
            ruleCatalogHash=f0480eb8e9620d02a0187da384224d3735717ad5f5f2e1ca9e904aea4c7ae7d4
            interactionCandidateHash=0f92b3f9d3ea81f9d20531341167efe1c0a8c1a9d8b593f27d28b7745c0bb49b
            blueprintVersion=composition-key-specific-application-semantics-blueprint-v1
            blueprintHash=6287bd537e29c488e0cbc9a2bc7636a3a76b44791c43420fdd8a40703edc8964
            winnerSafetyPolicy=composition-winner-decision-space-safety-policy-v1
            finalizationSanityGuardrail=phase-13d-finalization-gameplay-sanity-guardrail-v1
            SKIRMISH.winnerTransform=EXISTING_FROZEN_HALF_SPLIT_SCORE_PROJECTION
            SKIRMISH.winnerGain=24.509721397259
            SKIRMISH.severity=NOT_APPLICABLE
            TEAMFIGHT.winnerTransform=EXISTING_CONTEXT_AGGREGATE_EDGE_DECISION_LOCAL
            TEAMFIGHT.winnerTargetRatio=0.050000000000
            TEAMFIGHT.winnerGain=80.535608461244
            TEAMFIGHT.severityGain=0.000000000000
            SIEGE.winnerTransform=EXISTING_CONTEXT_AGGREGATE_EDGE_DECISION_LOCAL
            SIEGE.winnerTargetRatio=0.050000000000
            SIEGE.winnerGain=69.065220882615
            SIEGE.severityGain=0.000000000000
            BASE_DEFENSE.winnerTransform=ROLE_ORIENTED_PRODUCT_EXPOSURE_CONTEXT_EDGE_V1
            BASE_DEFENSE.sign=POSITIVE_ATTACKER_ADVANTAGE_NEGATIVE_DEFENDER_ADVANTAGE
            BASE_DEFENSE.winnerTargetRatio=0.025000000000
            BASE_DEFENSE.winnerGain=56.802132049987
            BASE_DEFENSE.severityGain=0.000000000000
            candidateFrozenBeforeFinalHoldout=true
            productionDefault=CANDIDATE
            explicitOffRollback=true
            """;

    private FrozenCompositionProductionCandidate() {}
    public static String canonical() { return CANONICAL; }
    public static String canonicalHash() { return sha256(CANONICAL); }
    public static void verifyExact() {
        if (!HASH.equals(canonicalHash())) throw new CompositionGameplayConfigurationException(
                "COMPOSITION_PRODUCTION_CANDIDATE_IDENTITY_MISMATCH", "Production composition candidate identity drift");
    }
    public static double winnerGain(TeamCompositionContext context) {
        return switch (context) {
            case SKIRMISH -> SKIRMISH_WINNER_GAIN;
            case TEAMFIGHT -> TEAMFIGHT_WINNER_GAIN;
            case SIEGE -> SIEGE_WINNER_GAIN;
            case BASE_DEFENSE -> BASE_DEFENSE_WINNER_GAIN;
            default -> 0.0;
        };
    }
    private static String sha256(String value) {
        try { return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (Exception e) { throw new IllegalStateException(e); }
    }
}
