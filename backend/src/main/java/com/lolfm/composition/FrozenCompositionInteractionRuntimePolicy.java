package com.lolfm.composition;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.Objects;
import java.util.stream.Collectors;

/** Immutable identity of the Phase 13D-3.1 candidate allowed in runtime shadow mode. */
public record FrozenCompositionInteractionRuntimePolicy(
        String profileVersion,
        String profileHash,
        String ruleCatalogVersion,
        String ruleCatalogHash,
        CompositionInteractionFormula formula,
        String candidateVersion,
        String candidateHash,
        String gain,
        String deadzone,
        int overrideCount
) {
    public static final String PROFILE_VERSION = "thirty-champion-composition-profile-candidate-v2";
    public static final String PROFILE_HASH = "fbf58dc5be12f2b07c5dff7ded9e182d7829999d2255e65dbbd073ccde2688d1";
    public static final String RULE_CATALOG_VERSION = "composition-interaction-rule-catalog-v1";
    public static final String RULE_CATALOG_HASH = "f0480eb8e9620d02a0187da384224d3735717ad5f5f2e1ca9e904aea4c7ae7d4";
    public static final String CANDIDATE_VERSION = "composition-interaction-product-exposure-v1";
    public static final String CANDIDATE_HASH = "0f92b3f9d3ea81f9d20531341167efe1c0a8c1a9d8b593f27d28b7745c0bb49b";

    public FrozenCompositionInteractionRuntimePolicy {
        Objects.requireNonNull(profileVersion, "profileVersion");
        Objects.requireNonNull(profileHash, "profileHash");
        Objects.requireNonNull(ruleCatalogVersion, "ruleCatalogVersion");
        Objects.requireNonNull(ruleCatalogHash, "ruleCatalogHash");
        Objects.requireNonNull(formula, "formula");
        Objects.requireNonNull(candidateVersion, "candidateVersion");
        Objects.requireNonNull(candidateHash, "candidateHash");
        Objects.requireNonNull(gain, "gain");
        Objects.requireNonNull(deadzone, "deadzone");
        if (overrideCount < 0) throw new IllegalArgumentException("overrideCount must be non-negative");
    }

    public static FrozenCompositionInteractionRuntimePolicy current() {
        FrozenCompositionInteractionRuntimePolicy policy = new FrozenCompositionInteractionRuntimePolicy(
                PROFILE_VERSION, PROFILE_HASH, RULE_CATALOG_VERSION, RULE_CATALOG_HASH,
                CompositionInteractionFormula.PRODUCT_EXPOSURE, CANDIDATE_VERSION, CANDIDATE_HASH,
                "NONE", "NONE", 0);
        policy.verifyExactIdentity();
        return policy;
    }

    public void verifyExactIdentity() {
        if (!PROFILE_VERSION.equals(profileVersion) || !PROFILE_HASH.equals(profileHash)
                || !ThirtyChampionCompositionProfiles.VERSION.equals(profileVersion)
                || !ThirtyChampionCompositionProfiles.profileHash().equals(profileHash)) {
            throw new IllegalStateException("Frozen composition profile identity mismatch");
        }
        if (!RULE_CATALOG_VERSION.equals(ruleCatalogVersion) || !RULE_CATALOG_HASH.equals(ruleCatalogHash)
                || !CompositionInteractionRuleCatalog.VERSION.equals(ruleCatalogVersion)
                || !CompositionInteractionRuleCatalog.catalogHash().equals(ruleCatalogHash)) {
            throw new IllegalStateException("Frozen composition rule catalog identity mismatch");
        }
        if (formula != CompositionInteractionFormula.PRODUCT_EXPOSURE
                || !CANDIDATE_VERSION.equals(candidateVersion)
                || !CANDIDATE_HASH.equals(candidateHash)
                || !"NONE".equals(gain) || !"NONE".equals(deadzone) || overrideCount != 0) {
            throw new IllegalStateException("Frozen composition candidate identity mismatch");
        }
        if (!CANDIDATE_HASH.equals(candidateHashFor(formula))) {
            throw new IllegalStateException("Frozen composition candidate hash mismatch");
        }
    }

    public static String candidateCanonicalSerialization(CompositionInteractionFormula formula) {
        Objects.requireNonNull(formula, "formula");
        StringBuilder out = new StringBuilder();
        out.append("selectedFormula=").append(formula).append('\n')
                .append("frozenProfileVersion=").append(PROFILE_VERSION).append('\n')
                .append("frozenProfileHash=").append(PROFILE_HASH).append('\n')
                .append("ruleCatalogVersion=").append(RULE_CATALOG_VERSION).append('\n')
                .append("ruleCatalogHash=").append(RULE_CATALOG_HASH).append('\n');
        CompositionInteractionRuleCatalog.rules().stream()
                .sorted(Comparator.comparingInt((CompositionInteractionRule x) -> x.context().ordinal())
                        .thenComparing(CompositionInteractionRule::ruleId))
                .forEach(rule -> out.append(rule.context().name()).append('|').append(rule.ruleId()).append('|')
                        .append(rule.sourceSignal().stableId()).append('|')
                        .append(rule.oppositionSignals().stream().map(CompositionSignalRef::stableId)
                                .collect(Collectors.joining("|"))).append('|')
                        .append(rule.oppositionAggregation().name()).append('|')
                        .append(Double.toString(rule.weight())).append('\n'));
        out.append("gain=NONE\n").append("deadzone=NONE\n")
                .append("overrideCount=0\n").append("productionEnabled=false\n");
        return out.toString();
    }

    public static String candidateHashFor(CompositionInteractionFormula formula) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(candidateCanonicalSerialization(formula).getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
