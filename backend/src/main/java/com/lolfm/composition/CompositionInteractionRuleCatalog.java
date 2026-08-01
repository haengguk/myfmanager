package com.lolfm.composition;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Comparator;

/** Frozen Phase 13D-3 interaction rule catalog. */
public final class CompositionInteractionRuleCatalog {
    public static final String VERSION = "composition-interaction-rule-catalog-v1";
    private static final List<CompositionInteractionRule> RULES = build();
    private static final Map<TeamCompositionContext, List<CompositionInteractionRule>> BY_CONTEXT = byContext(RULES);
    private static final String HASH = sha256(canonicalSerialization(RULES));

    private CompositionInteractionRuleCatalog() {}

    public static List<CompositionInteractionRule> rules() { return RULES; }
    public static List<CompositionInteractionRule> rules(TeamCompositionContext context) { return BY_CONTEXT.get(context); }
    public static String canonicalSerialization() { return canonicalSerialization(RULES); }
    public static String catalogHash() { return HASH; }

    static String canonicalSerialization(List<CompositionInteractionRule> rules) {
        StringBuilder out = new StringBuilder();
        rules.stream().sorted(Comparator.comparingInt((CompositionInteractionRule rule) -> rule.context().ordinal())
                        .thenComparing(CompositionInteractionRule::ruleId)
                        .thenComparing(rule -> rule.sourceSignal().stableId())
                        .thenComparing(rule -> rule.oppositionSignals().stream().map(CompositionSignalRef::stableId).collect(java.util.stream.Collectors.joining("|")))
                        .thenComparing(rule -> rule.oppositionAggregation().name())
                        .thenComparingDouble(CompositionInteractionRule::weight))
                .forEach(rule -> {
            out.append(rule.context().name()).append('|')
                    .append(rule.ruleId()).append('|')
                    .append(rule.sourceSignal().stableId()).append('|');
            for (CompositionSignalRef opposition : rule.oppositionSignals()) out.append(opposition.stableId()).append('|');
            out.append(rule.oppositionAggregation().name()).append('|')
                    .append(Double.toString(rule.weight())).append('\n');
                });
        return out.toString();
    }

    private static Map<TeamCompositionContext, List<CompositionInteractionRule>> byContext(List<CompositionInteractionRule> rules) {
        EnumMap<TeamCompositionContext, List<CompositionInteractionRule>> grouped = new EnumMap<>(TeamCompositionContext.class);
        for (TeamCompositionContext context : TeamCompositionContext.values()) grouped.put(context, new ArrayList<>());
        for (CompositionInteractionRule rule : rules) grouped.get(rule.context()).add(rule);
        EnumMap<TeamCompositionContext, List<CompositionInteractionRule>> result = new EnumMap<>(TeamCompositionContext.class);
        for (TeamCompositionContext context : TeamCompositionContext.values()) result.put(context, List.copyOf(grouped.get(context)));
        return Map.copyOf(result);
    }

    private static List<CompositionInteractionRule> build() {
        List<CompositionInteractionRule> rules = new ArrayList<>();
        add(rules, "SKIRMISH_PICK_VS_PEEL_FRONTLINE", TeamCompositionContext.SKIRMISH,
                pattern(CompositionPattern.PICK_CONVERSION), two(capability(CompositionCapability.PEEL), capability(CompositionCapability.FRONTLINE)));
        add(rules, "SKIRMISH_ENGAGE_VS_DISENGAGE_PEEL", TeamCompositionContext.SKIRMISH,
                pattern(CompositionPattern.ENGAGE_CHAIN), two(capability(CompositionCapability.DISENGAGE), capability(CompositionCapability.PEEL)));
        add(rules, "SKIRMISH_BACKLINE_VS_PEEL_ZONE", TeamCompositionContext.SKIRMISH,
                capability(CompositionCapability.BACKLINE_ACCESS), two(capability(CompositionCapability.PEEL), capability(CompositionCapability.ZONE_CONTROL)));

        add(rules, "TEAMFIGHT_ENGAGE_VS_DISENGAGE_PEEL", TeamCompositionContext.TEAMFIGHT,
                pattern(CompositionPattern.ENGAGE_CHAIN), two(capability(CompositionCapability.DISENGAGE), capability(CompositionCapability.PEEL)));
        add(rules, "TEAMFIGHT_FRONT_TO_BACK_VS_ACCESS_BURST", TeamCompositionContext.TEAMFIGHT,
                pattern(CompositionPattern.FRONT_TO_BACK), two(capability(CompositionCapability.BACKLINE_ACCESS), capability(CompositionCapability.BURST_DAMAGE)));
        add(rules, "TEAMFIGHT_BACKLINE_VS_PEEL_ZONE_FRONTLINE", TeamCompositionContext.TEAMFIGHT,
                capability(CompositionCapability.BACKLINE_ACCESS), three(capability(CompositionCapability.PEEL), capability(CompositionCapability.ZONE_CONTROL), capability(CompositionCapability.FRONTLINE)));

        add(rules, "OBJECTIVE_CONTROL_VS_ENGAGE_POKE", TeamCompositionContext.OBJECTIVE_SETUP,
                pattern(CompositionPattern.OBJECTIVE_CONTROL), two(capability(CompositionCapability.ENGAGE), capability(CompositionCapability.POKE)));
        add(rules, "OBJECTIVE_PICK_VS_PEEL_FRONTLINE", TeamCompositionContext.OBJECTIVE_SETUP,
                pattern(CompositionPattern.PICK_CONVERSION), two(capability(CompositionCapability.PEEL), capability(CompositionCapability.FRONTLINE)));
        add(rules, "OBJECTIVE_POKE_VS_ENGAGE_DISENGAGE", TeamCompositionContext.OBJECTIVE_SETUP,
                pattern(CompositionPattern.POKE_SIEGE), two(capability(CompositionCapability.ENGAGE), capability(CompositionCapability.DISENGAGE)));

        add(rules, "SIEGE_POKE_VS_WAVECLEAR_ENGAGE", TeamCompositionContext.SIEGE,
                pattern(CompositionPattern.POKE_SIEGE), two(capability(CompositionCapability.WAVE_CLEAR), capability(CompositionCapability.ENGAGE)));
        add(rules, "SIEGE_PICK_VS_PEEL_ZONE", TeamCompositionContext.SIEGE,
                pattern(CompositionPattern.PICK_CONVERSION), two(capability(CompositionCapability.PEEL), capability(CompositionCapability.ZONE_CONTROL)));
        add(rules, "SIEGE_ENGAGE_VS_DISENGAGE_FRONTLINE", TeamCompositionContext.SIEGE,
                pattern(CompositionPattern.ENGAGE_CHAIN), two(capability(CompositionCapability.DISENGAGE), capability(CompositionCapability.FRONTLINE)));

        add(rules, "BASE_DEFENSE_WAVECLEAR_VS_POKE", TeamCompositionContext.BASE_DEFENSE,
                capability(CompositionCapability.WAVE_CLEAR), single(pattern(CompositionPattern.POKE_SIEGE)));
        add(rules, "BASE_DEFENSE_DISENGAGE_VS_ENGAGE", TeamCompositionContext.BASE_DEFENSE,
                capability(CompositionCapability.DISENGAGE), single(pattern(CompositionPattern.ENGAGE_CHAIN)));
        add(rules, "BASE_DEFENSE_PEEL_VS_PICK_BACKLINE", TeamCompositionContext.BASE_DEFENSE,
                capability(CompositionCapability.PEEL), two(pattern(CompositionPattern.PICK_CONVERSION), capability(CompositionCapability.BACKLINE_ACCESS)));

        add(rules, "SIDE_SPLIT_VS_WAVECLEAR_PICK", TeamCompositionContext.SIDE_LANE,
                pattern(CompositionPattern.SPLIT_MAP_PRESSURE), two(capability(CompositionCapability.WAVE_CLEAR), capability(CompositionCapability.PICK)));
        add(rules, "SIDE_PRESSURE_VS_SIDE_PRESSURE", TeamCompositionContext.SIDE_LANE,
                capability(CompositionCapability.SIDE_LANE_PRESSURE), single(capability(CompositionCapability.SIDE_LANE_PRESSURE)));
        add(rules, "SIDE_DISENGAGE_VS_PICK_ENGAGE", TeamCompositionContext.SIDE_LANE,
                capability(CompositionCapability.DISENGAGE), two(pattern(CompositionPattern.PICK_CONVERSION), pattern(CompositionPattern.ENGAGE_CHAIN)));
        if (rules.size() != 18) throw new IllegalStateException("Expected exactly eighteen interaction rules");
        return List.copyOf(rules);
    }

    private static void add(List<CompositionInteractionRule> rules, String id, TeamCompositionContext context,
                            CompositionSignalRef source, SignalGroup opposition) {
        rules.add(new CompositionInteractionRule(id, context, source, opposition.signals(), opposition.aggregation(), 1.0));
    }

    private static SignalGroup single(CompositionSignalRef signal) { return new SignalGroup(List.of(signal), OppositionAggregation.SINGLE); }
    private static SignalGroup two(CompositionSignalRef first, CompositionSignalRef second) { return new SignalGroup(List.of(first, second), OppositionAggregation.COMPLEMENTARY_TWO); }
    private static SignalGroup three(CompositionSignalRef first, CompositionSignalRef second, CompositionSignalRef third) { return new SignalGroup(List.of(first, second, third), OppositionAggregation.COMPLEMENTARY_THREE); }
    private static CompositionSignalRef pattern(CompositionPattern pattern) { return new PatternSignalRef(pattern); }
    private static CompositionSignalRef capability(CompositionCapability capability) { return new CapabilitySignalRef(capability); }
    private record SignalGroup(List<CompositionSignalRef> signals, OppositionAggregation aggregation) {}

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
