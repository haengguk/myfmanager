package com.lolfm.composition;

import java.util.List;

/** Immutable production-side identity/routing mirror of the frozen Phase 13D-4C.4 blueprint. */
public final class FrozenCompositionApplicationSemanticsBlueprint {
    public static final String VERSION = "composition-key-specific-application-semantics-blueprint-v1";
    public static final String HASH = "6287bd537e29c488e0cbc9a2bc7636a3a76b44791c43420fdd8a40703edc8964";

    public enum ChannelState { ACTIVE_EXISTING_FROZEN, DEFINED_UNCALIBRATED, NOT_APPLICABLE }
    public enum SignalSource { EXISTING_CONTEXT_AGGREGATE_EDGE, ROLE_AWARE_RULE_EDGE, SEPARATE_RULE_TRANSFORM_REQUIRED, NOT_APPLICABLE }
    public enum ApplicationMode { EXISTING_FROZEN_HALF_SPLIT_SCORE_PROJECTION, DECISION_LOCAL_GAP_MODIFIER, ROLE_AWARE_DECISION_LOCAL_GAP_MODIFIER, DECISION_LOCAL_SEVERITY_INPUT_MODIFIER, NOT_APPLICABLE }
    public enum RoleSemantics { SYMMETRIC, STRUCTURED_ATTACKER_DEFENDER }
    public enum HalfSplitDisposition { PRESERVE_HALF_SPLIT_FOR_WINNER_ONLY, REPLACE_WITH_DECISION_LOCAL_GAP_MODIFIER, ROLE_AWARE_ASYMMETRIC_ADJUSTMENT_REQUIRED }

    public record Key(TeamCompositionContext context, CompositionActionType actionType,
                      CompositionBaselineScoreDomain scoreDomain, ChannelState winnerState,
                      SignalSource winnerSource, ApplicationMode winnerMode,
                      ChannelState severityState, SignalSource severitySource,
                      ApplicationMode severityMode, RoleSemantics roles,
                      HalfSplitDisposition halfSplit) {
        public String stableId() {
            return context.name() + "|" + actionType.name() + "|" + scoreDomain.name();
        }
    }

    private static final List<Key> KEYS = List.of(
            new Key(TeamCompositionContext.SKIRMISH, CompositionActionType.SKIRMISH,
                    CompositionBaselineScoreDomain.SKIRMISH_COMBAT_SCORE,
                    ChannelState.ACTIVE_EXISTING_FROZEN, SignalSource.EXISTING_CONTEXT_AGGREGATE_EDGE,
                    ApplicationMode.EXISTING_FROZEN_HALF_SPLIT_SCORE_PROJECTION,
                    ChannelState.NOT_APPLICABLE, SignalSource.NOT_APPLICABLE, ApplicationMode.NOT_APPLICABLE,
                    RoleSemantics.SYMMETRIC, HalfSplitDisposition.PRESERVE_HALF_SPLIT_FOR_WINNER_ONLY),
            new Key(TeamCompositionContext.TEAMFIGHT, CompositionActionType.TEAMFIGHT,
                    CompositionBaselineScoreDomain.TEAMFIGHT_COMBAT_SCORE,
                    ChannelState.DEFINED_UNCALIBRATED, SignalSource.EXISTING_CONTEXT_AGGREGATE_EDGE,
                    ApplicationMode.DECISION_LOCAL_GAP_MODIFIER,
                    ChannelState.DEFINED_UNCALIBRATED, SignalSource.SEPARATE_RULE_TRANSFORM_REQUIRED,
                    ApplicationMode.DECISION_LOCAL_SEVERITY_INPUT_MODIFIER,
                    RoleSemantics.SYMMETRIC, HalfSplitDisposition.REPLACE_WITH_DECISION_LOCAL_GAP_MODIFIER),
            new Key(TeamCompositionContext.SIEGE, CompositionActionType.SIEGE_COMBAT,
                    CompositionBaselineScoreDomain.SIEGE_PUSH_SCORE,
                    ChannelState.DEFINED_UNCALIBRATED, SignalSource.EXISTING_CONTEXT_AGGREGATE_EDGE,
                    ApplicationMode.DECISION_LOCAL_GAP_MODIFIER,
                    ChannelState.DEFINED_UNCALIBRATED, SignalSource.SEPARATE_RULE_TRANSFORM_REQUIRED,
                    ApplicationMode.DECISION_LOCAL_SEVERITY_INPUT_MODIFIER,
                    RoleSemantics.SYMMETRIC, HalfSplitDisposition.REPLACE_WITH_DECISION_LOCAL_GAP_MODIFIER),
            new Key(TeamCompositionContext.BASE_DEFENSE, CompositionActionType.BASE_DEFENSE,
                    CompositionBaselineScoreDomain.BASE_DEFENSE_SCORE,
                    ChannelState.DEFINED_UNCALIBRATED, SignalSource.ROLE_AWARE_RULE_EDGE,
                    ApplicationMode.ROLE_AWARE_DECISION_LOCAL_GAP_MODIFIER,
                    ChannelState.DEFINED_UNCALIBRATED, SignalSource.SEPARATE_RULE_TRANSFORM_REQUIRED,
                    ApplicationMode.DECISION_LOCAL_SEVERITY_INPUT_MODIFIER,
                    RoleSemantics.STRUCTURED_ATTACKER_DEFENDER,
                    HalfSplitDisposition.ROLE_AWARE_ASYMMETRIC_ADJUSTMENT_REQUIRED));

    private FrozenCompositionApplicationSemanticsBlueprint() {}

    public static List<Key> keys() { return KEYS; }

    public static Key key(TeamCompositionContext context, CompositionActionType actionType,
                          CompositionBaselineScoreDomain scoreDomain) {
        return KEYS.stream().filter(x -> x.context() == context && x.actionType() == actionType
                && x.scoreDomain() == scoreDomain).findFirst().orElseThrow(() ->
                new CompositionGameplayConfigurationException("COMPOSITION_SEMANTICS_APPLICATION_KEY_UNMAPPED",
                        context + "|" + actionType + "|" + scoreDomain));
    }

    public static void verifyIdentity(String version, String hash) {
        if (!VERSION.equals(version) || !HASH.equals(hash)) {
            throw new CompositionGameplayConfigurationException(
                    "COMPOSITION_SEMANTICS_BLUEPRINT_IDENTITY_MISMATCH",
                    "Composition semantics blueprint identity does not match the frozen runtime blueprint");
        }
        if (KEYS.size() != 4 || KEYS.stream().map(Key::stableId).distinct().count() != 4) {
            throw new IllegalStateException("Frozen composition semantics blueprint key integrity failure");
        }
    }
}
