package com.lolfm.composition;

import java.util.List;
import java.util.Objects;

/**
 * Immutable main-source identity for the Phase 13D-4B.2 gameplay-gain candidate.
 * It deliberately contains no artifact, test-source, environment, or runtime
 * configuration lookup.
 */
public record FrozenCompositionGameplayGainPolicy(
        String candidateVersion,
        String candidateHash,
        String safetyPolicyVersion,
        String adjustmentFormula,
        boolean frozen,
        String deadzone,
        String clamp,
        String cap,
        int productionOverrideCount,
        boolean midpointPreserved,
        boolean freshAuditRequired,
        boolean productionEnabled,
        boolean candidateEnabled,
        List<CompositionGameplayApplicationKey> approvedKeys
) {
    public static final String CANDIDATE_VERSION = "composition-gameplay-margin-aware-gain-candidate-v1";
    public static final String CANDIDATE_HASH = "ec99828c0f04a00cc644f4d0446d851543a46a530c9bc561408af9cf704da32d";
    public static final String SAFETY_POLICY_VERSION = "composition-gain-margin-aware-safety-policy-v1";
    public static final String ADJUSTMENT_FORMULA = "GAP_MODIFIER_HALF_SPLIT_V1";
    public static final double SKIRMISH_GAIN = 24.509721397259;
    public static final double TEAMFIGHT_GAIN = 11.595061941148;
    public static final double SIEGE_GAIN = 6.805985567298;
    public static final double BASE_DEFENSE_GAIN = 10.837956658606;
    public static final String POLICY_HASH = CANDIDATE_HASH;

    public FrozenCompositionGameplayGainPolicy {
        Objects.requireNonNull(candidateVersion, "candidateVersion");
        Objects.requireNonNull(candidateHash, "candidateHash");
        Objects.requireNonNull(safetyPolicyVersion, "safetyPolicyVersion");
        Objects.requireNonNull(adjustmentFormula, "adjustmentFormula");
        Objects.requireNonNull(deadzone, "deadzone");
        Objects.requireNonNull(clamp, "clamp");
        Objects.requireNonNull(cap, "cap");
        Objects.requireNonNull(approvedKeys, "approvedKeys");
        if (productionOverrideCount < 0) throw new IllegalArgumentException("productionOverrideCount must be non-negative");
        approvedKeys = List.copyOf(approvedKeys);
    }

    public static FrozenCompositionGameplayGainPolicy current() {
        FrozenCompositionGameplayGainPolicy policy = new FrozenCompositionGameplayGainPolicy(
                CANDIDATE_VERSION, CANDIDATE_HASH, SAFETY_POLICY_VERSION, ADJUSTMENT_FORMULA,
                true, "NONE", "NONE", "NONE", 0, true, true, false, false,
                List.of(
                        new CompositionGameplayApplicationKey(TeamCompositionContext.SKIRMISH,
                                CompositionActionType.SKIRMISH, CompositionBaselineScoreDomain.SKIRMISH_COMBAT_SCORE, SKIRMISH_GAIN),
                        new CompositionGameplayApplicationKey(TeamCompositionContext.TEAMFIGHT,
                                CompositionActionType.TEAMFIGHT, CompositionBaselineScoreDomain.TEAMFIGHT_COMBAT_SCORE, TEAMFIGHT_GAIN),
                        new CompositionGameplayApplicationKey(TeamCompositionContext.SIEGE,
                                CompositionActionType.SIEGE_COMBAT, CompositionBaselineScoreDomain.SIEGE_PUSH_SCORE, SIEGE_GAIN),
                        new CompositionGameplayApplicationKey(TeamCompositionContext.BASE_DEFENSE,
                                CompositionActionType.BASE_DEFENSE, CompositionBaselineScoreDomain.BASE_DEFENSE_SCORE, BASE_DEFENSE_GAIN)));
        policy.verifyExactIdentity();
        return policy;
    }

    public void verifyExactIdentity() {
        if (!CANDIDATE_VERSION.equals(candidateVersion)
                || !CANDIDATE_HASH.equals(candidateHash)
                || !SAFETY_POLICY_VERSION.equals(safetyPolicyVersion)
                || !ADJUSTMENT_FORMULA.equals(adjustmentFormula)
                || !frozen || !"NONE".equals(deadzone) || !"NONE".equals(clamp) || !"NONE".equals(cap)
                || productionOverrideCount != 0 || !midpointPreserved || !freshAuditRequired
                || productionEnabled || candidateEnabled || approvedKeys.size() != 4) {
            throw new IllegalStateException("Frozen composition gameplay gain policy identity mismatch");
        }
        List<CompositionGameplayApplicationKey> expected = currentKeys();
        if (!approvedKeys.equals(expected)) throw new IllegalStateException("Frozen composition gameplay key catalog mismatch");
    }

    public static List<CompositionGameplayApplicationKey> currentKeys() {
        return List.of(
                new CompositionGameplayApplicationKey(TeamCompositionContext.SKIRMISH,
                        CompositionActionType.SKIRMISH, CompositionBaselineScoreDomain.SKIRMISH_COMBAT_SCORE, SKIRMISH_GAIN),
                new CompositionGameplayApplicationKey(TeamCompositionContext.TEAMFIGHT,
                        CompositionActionType.TEAMFIGHT, CompositionBaselineScoreDomain.TEAMFIGHT_COMBAT_SCORE, TEAMFIGHT_GAIN),
                new CompositionGameplayApplicationKey(TeamCompositionContext.SIEGE,
                        CompositionActionType.SIEGE_COMBAT, CompositionBaselineScoreDomain.SIEGE_PUSH_SCORE, SIEGE_GAIN),
                new CompositionGameplayApplicationKey(TeamCompositionContext.BASE_DEFENSE,
                        CompositionActionType.BASE_DEFENSE, CompositionBaselineScoreDomain.BASE_DEFENSE_SCORE, BASE_DEFENSE_GAIN));
    }

    public double gainFor(TeamCompositionContext context, CompositionActionType actionType,
                          CompositionBaselineScoreDomain scoreDomain) {
        return approvedKeys.stream()
                .filter(key -> key.context() == context && key.actionType() == actionType && key.scoreDomain() == scoreDomain)
                .mapToDouble(CompositionGameplayApplicationKey::selectedGain)
                .findFirst().orElse(0.0);
    }

    public boolean approved(TeamCompositionContext context, CompositionActionType actionType,
                            CompositionBaselineScoreDomain scoreDomain) {
        return gainFor(context, actionType, scoreDomain) != 0.0;
    }

    /** Frozen calibration metadata; no fresh-match percentile is calculated. */
    public static String marginBand(String applicationKey, double baselineGap) {
        double magnitude = Math.abs(baselineGap);
        double[] band = switch (applicationKey) {
            case "SKIRMISH|SKIRMISH|SKIRMISH_COMBAT_SCORE" -> new double[]{5.104214137066, 109.230158346492};
            case "TEAMFIGHT|TEAMFIGHT|TEAMFIGHT_COMBAT_SCORE" -> new double[]{0.820000000000, 31.960000000000};
            case "SIEGE|SIEGE_COMBAT|SIEGE_PUSH_SCORE" -> new double[]{0.820000000000, 22.820000000000};
            case "BASE_DEFENSE|BASE_DEFENSE|BASE_DEFENSE_SCORE" -> new double[]{0.820000000000, 27.820000000000};
            default -> null;
        };
        if (band == null) return "NOT_AVAILABLE";
        return magnitude <= band[0] ? "CLOSE" : magnitude < band[1] ? "MEDIUM" : "HIGH";
    }
}
