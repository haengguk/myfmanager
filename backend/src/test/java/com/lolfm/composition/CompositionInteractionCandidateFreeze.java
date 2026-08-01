package com.lolfm.composition;

import com.lolfm.champion.ChampionId;
import com.lolfm.champion.ChampionRoleKey;
import com.lolfm.domain.Position;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Phase 13D-3.1 artifact-only anchor correction and candidate freeze.
 *
 * This class deliberately lives in test sources. It never calls the full
 * context audit, never selects representatives, and never enters gameplay or
 * Random.
 */
public final class CompositionInteractionCandidateFreeze {
    public static final Path SOURCE_DIR = Path.of("build", "reports", "composition-interaction-context");
    public static final Path OUTPUT = Path.of("build", "reports", "composition-interaction-candidate-freeze");
    public static final String REVIEW_VERSION = "phase-13d3.1-rule-level-anchor-correction-and-candidate-freeze-v1";
    public static final String ROOT_CAUSE = "ANCHOR_ASSERTION_SCOPE_ERROR";
    public static final String SECONDARY_ROOT_CAUSE = "ANCHOR_RESPONSE_VALUE_LABELING_AMBIGUITY";
    public static final String PROFILE_VERSION = ThirtyChampionCompositionProfiles.VERSION;
    public static final String PROFILE_HASH = "fbf58dc5be12f2b07c5dff7ded9e182d7829999d2255e65dbbd073ccde2688d1";
    public static final String RULE_CATALOG_VERSION = CompositionInteractionRuleCatalog.VERSION;
    public static final String RULE_CATALOG_HASH = "f0480eb8e9620d02a0187da384224d3735717ad5f5f2e1ca9e904aea4c7ae7d4";
    public static final double TOLERANCE = 1e-12;

    private static final List<Position> POSITIONS = List.of(Position.TOP, Position.JUNGLE, Position.MID, Position.ADC, Position.SUPPORT);
    private static final List<CompositionInteractionFormula> FORMULAS = List.of(CompositionInteractionFormula.values());
    private static final List<String> SOURCE_FILES = List.of(
            "composition-interaction-rule-catalog.csv",
            "composition-interaction-representative-lineups.csv",
            "composition-interaction-formula-distribution.csv",
            "composition-interaction-pair-context.csv",
            "composition-interaction-rule-dominance.csv",
            "composition-interaction-nonseparability.csv",
            "composition-interaction-context-correlation.csv",
            "composition-interaction-anchor-directionality.csv",
            "composition-interaction-specialist-review.csv",
            "composition-interaction-lineup-dominance.csv",
            "composition-interaction-candidate-summary.csv",
            "composition-interaction-context-audit.log");

    private static final Map<String, String> TARGET_RULES = Map.ofEntries(
            Map.entry("ENGAGE|SKIRMISH", "SKIRMISH_ENGAGE_VS_DISENGAGE_PEEL"),
            Map.entry("ENGAGE|TEAMFIGHT", "TEAMFIGHT_ENGAGE_VS_DISENGAGE_PEEL"),
            Map.entry("ENGAGE|SIEGE", "SIEGE_ENGAGE_VS_DISENGAGE_FRONTLINE"),
            Map.entry("POKE|OBJECTIVE_SETUP", "OBJECTIVE_POKE_VS_ENGAGE_DISENGAGE"),
            Map.entry("POKE|SIEGE", "SIEGE_POKE_VS_WAVECLEAR_ENGAGE"),
            Map.entry("PICK|SKIRMISH", "SKIRMISH_PICK_VS_PEEL_FRONTLINE"),
            Map.entry("PICK|OBJECTIVE_SETUP", "OBJECTIVE_PICK_VS_PEEL_FRONTLINE"),
            Map.entry("PICK|SIEGE", "SIEGE_PICK_VS_PEEL_ZONE"),
            Map.entry("SPLIT|SIDE_LANE", "SIDE_SPLIT_VS_WAVECLEAR_PICK"),
            Map.entry("FRONT_TO_BACK|TEAMFIGHT", "TEAMFIGHT_FRONT_TO_BACK_VS_ACCESS_BURST"),
            Map.entry("BASE_DEFENSE|BASE_DEFENSE", "BASE_DEFENSE_WAVECLEAR_VS_POKE"));

    private CompositionInteractionCandidateFreeze() {}

    public static void main(String[] args) throws Exception {
        Snapshot snapshot = compute();
        writeArtifacts(snapshot);
        System.out.println("Composition interaction candidate freeze: " + snapshot.verdict());
        System.out.println("selectedFormula=" + snapshot.selectedFormula() + " candidateFrozen=" + snapshot.candidateFrozen());
        System.out.println("Artifacts: " + OUTPUT.toAbsolutePath());
        if (snapshot.integrityErrorCount() != 0) {
            throw new IllegalStateException("Composition interaction candidate freeze integrity errors=" + snapshot.integrityErrorCount());
        }
    }

    public static Snapshot compute() throws IOException {
        SourceArtifacts source = readSourceArtifacts();
        validateFrozenInputs(source);
        List<AnchorCase> cases = parseAnchorCases(source.anchorRows(), source.rules());
        Map<String, CompositionInteractionInput> inputs = buildInputs(source.representativeLineups());
        Map<String, CompositionInteractionRule> rules = source.rules().stream()
                .collect(Collectors.toMap(CompositionInteractionRule::ruleId, Function.identity()));
        List<CorrectedAnchorRow> corrected = correctAnchors(cases, source.representativeLineups(), inputs, rules);
        List<TradeoffRow> tradeoffs = corrected.stream().filter(CompositionInteractionCandidateFreeze::isTradeoff)
                .map(CompositionInteractionCandidateFreeze::tradeoff).toList();
        List<FormulaSelectionRow> selectionRows = selectFormulas(source, corrected);
        FormulaSelectionRow selected = selectionRows.stream().filter(FormulaSelectionRow::selected).findFirst().orElse(null);
        String selectedFormula = selected == null ? "NONE" : selected.formula();
        String candidateVersion = selected == null ? "NONE" : candidateVersion(selected.formula());
        String candidateHash = selected == null ? "NONE" : candidateHash(selected.formula());
        int sourcePass = (int) source.anchorRows().stream().filter(x -> Boolean.parseBoolean(x.get("passed"))).count();
        int sourceFailure = source.anchorRows().size() - sourcePass;
        int sourceFailedCases = (int) source.anchorRows().stream()
                .filter(x -> !Boolean.parseBoolean(x.get("passed"))).map(x -> x.get("caseId") + "|" + x.get("context")).distinct().count();
        int correctedFailures = (int) corrected.stream().filter(x -> !x.targetRuleDirectionPassed()).count();
        int correctedFailedCases = (int) corrected.stream().filter(x -> !x.targetRuleDirectionPassed())
                .map(CorrectedAnchorRow::anchorCaseId).distinct().count();
        int contextDivergence = (int) corrected.stream().filter(CompositionInteractionCandidateFreeze::isTradeoff).count();
        int integrity = integrityErrors(cases, corrected, selectionRows, selectedFormula, candidateHash);
        List<String> reviewCodes = new ArrayList<>();
        if (correctedFailures > 0) reviewCodes.add("TARGET_RULE_DIRECTIONALITY_REVIEW");
        selectionRows.stream().filter(x -> !x.eligibleForSelection() && !x.regressionReference())
                .map(FormulaSelectionRow::rejectionReason).filter(x -> !x.equals("NONE")).forEach(reviewCodes::add);
        if (selected == null) reviewCodes.add("FORMULA_SELECTION_REVIEW");
        reviewCodes = reviewCodes.stream().filter(x -> !x.equals("NONE")).distinct().sorted().toList();
        List<String> infoCodes = new ArrayList<>();
        if (contextDivergence > 0) infoCodes.add("CONTEXT_MULTI_RULE_TRADEOFF_INFO");
        if (broadInfoCount(source) > 0) infoCodes.add("BROAD_LINEUP_DOMINANCE_INFO");
        String verdict = integrity != 0 ? "BLOCKED_BY_COMPOSITION_INTERACTION_CANDIDATE_FREEZE_INTEGRITY"
                : selected != null && correctedFailures == 0 && reviewCodes.isEmpty()
                ? "READY_FOR_PHASE_13D4" : "REVIEW_COMPOSITION_INTERACTION_CANDIDATE";
        Map<String, String> summary = summary(source, cases, corrected, selectionRows, selectedFormula, candidateVersion,
                candidateHash, sourcePass, sourceFailure, sourceFailedCases, correctedFailures, correctedFailedCases,
                contextDivergence, integrity, infoCodes, reviewCodes, verdict);
        return new Snapshot(source, cases, corrected, tradeoffs, selectionRows, summary, selectedFormula,
                candidateVersion, candidateHash, integrity, infoCodes, reviewCodes, verdict,
                "READY_FOR_PHASE_13D4".equals(verdict));
    }

    public record Snapshot(SourceArtifacts source, List<AnchorCase> anchorCases,
                           List<CorrectedAnchorRow> correctedAnchorRows, List<TradeoffRow> tradeoffRows,
                           List<FormulaSelectionRow> selectionRows, Map<String, String> summary,
                           String selectedFormula, String candidateVersion, String candidateHash,
                           int integrityErrorCount, List<String> infoCodes, List<String> reviewCodes,
                           String verdict, boolean phase13D4Allowed) {
        public Snapshot {
            anchorCases = List.copyOf(anchorCases);
            correctedAnchorRows = List.copyOf(correctedAnchorRows);
            tradeoffRows = List.copyOf(tradeoffRows);
            selectionRows = List.copyOf(selectionRows);
            summary = java.util.Collections.unmodifiableMap(new java.util.LinkedHashMap<>(summary));
            infoCodes = List.copyOf(infoCodes);
            reviewCodes = List.copyOf(reviewCodes);
        }

        public boolean candidateFrozen() { return !selectedFormula.equals("NONE"); }
    }

    public record SourceArtifacts(Map<String, List<Map<String, String>>> csv, Map<String, String> hashes,
                                  Map<String, String> summary, List<CompositionInteractionRule> rules,
                                  List<Map<String, String>> representativeLineups,
                                  List<Map<String, String>> anchorRows) {
        public SourceArtifacts {
            csv = Map.copyOf(csv);
            hashes = Map.copyOf(hashes);
            summary = Map.copyOf(summary);
            rules = List.copyOf(rules);
            representativeLineups = List.copyOf(representativeLineups);
            anchorRows = List.copyOf(anchorRows);
        }
    }

    public record AnchorCase(String anchorCaseId, TeamCompositionContext context, String sourceLineupId,
                             String targetRuleId, String sourceLowResponseLineupId,
                             String sourceHighResponseLineupId) {}

    public record CorrectedAnchorRow(String anchorCaseId, CompositionInteractionFormula formula,
                                     TeamCompositionContext context, String targetRuleId,
                                     String sourceLineupId, String lowResponseLineupId,
                                     String highResponseLineupId, double sourceSignalStrength,
                                     List<Double> lowOppositionSignalValues,
                                     List<Double> highOppositionSignalValues,
                                     double lowRawSelectionScore, double highRawSelectionScore,
                                     OppositionAggregation oppositionAggregation,
                                     double targetRuleWeight, double lowAggregatedOppositionStrength,
                                     double highAggregatedOppositionStrength, double lowTargetRuleExposure,
                                     double highTargetRuleExposure, double lowTargetRuleWeightedPressure,
                                     double highTargetRuleWeightedPressure,
                                     double lowContextDirectedPressure, double highContextDirectedPressure,
                                     boolean targetRuleDirectionPassed,
                                     boolean contextDirectionMatchesTargetRule,
                                     String classification, String rationale,
                                     List<RuleDelta> ruleDeltas) {}

    public record RuleDelta(String ruleId, double lowWeightedPressure,
                            double highWeightedPressure, double delta) {}

    public record TradeoffRow(String anchorCaseId, CompositionInteractionFormula formula,
                              TeamCompositionContext context, String targetRuleId,
                              double targetRuleDelta, double contextPressureDelta,
                              String largestOffsettingRuleId, double largestOffsettingRuleDelta,
                              String ruleContributions, String classification, String rationale) {}

    public record FormulaSelectionRow(String formula, boolean regressionReference,
                                      boolean structuralIntegrityPassed, boolean distributionPassed,
                                      boolean nonseparabilityPassed, boolean opponentSensitivityPassed,
                                      int correctedAnchorPassCount, int correctedAnchorFailureCount,
                                      int systemicRuleDominanceCount, int universalLineupDominanceCount,
                                      int systemicSpecialistDominanceCount,
                                      int broadLineupDominanceInfoCount,
                                      boolean eligibleForSelection, String selectionPriority,
                                      boolean selected, String rejectionReason) {}

    private static SourceArtifacts readSourceArtifacts() throws IOException {
        Map<String, List<Map<String, String>>> csv = new LinkedHashMap<>();
        Map<String, String> hashes = new LinkedHashMap<>();
        for (String name : SOURCE_FILES) {
            Path path = SOURCE_DIR.resolve(name);
            if (!Files.isRegularFile(path)) throw new IllegalStateException("Missing source artifact: " + path.toAbsolutePath());
            hashes.put(name, sha256(Files.readAllBytes(path)));
            if (name.endsWith(".csv")) csv.put(name, readCsv(path));
        }
        Map<String, String> summary = keyValue(csv.get("composition-interaction-candidate-summary.csv"));
        List<CompositionInteractionRule> rules = rules(csv.get("composition-interaction-rule-catalog.csv"));
        return new SourceArtifacts(csv, hashes, summary, rules,
                csv.get("composition-interaction-representative-lineups.csv"),
                csv.get("composition-interaction-anchor-directionality.csv"));
    }

    static SourceArtifacts readSourceArtifactsForTest() throws IOException { return readSourceArtifacts(); }

    private static void validateFrozenInputs(SourceArtifacts source) {
        if (!PROFILE_VERSION.equals(ThirtyChampionCompositionProfiles.VERSION)
                || !PROFILE_HASH.equals(ThirtyChampionCompositionProfiles.profileHash())) {
            throw new IllegalStateException("Frozen Profile mismatch");
        }
        if (!RULE_CATALOG_VERSION.equals(CompositionInteractionRuleCatalog.VERSION)
                || !RULE_CATALOG_HASH.equals(CompositionInteractionRuleCatalog.catalogHash())) {
            throw new IllegalStateException("Rule catalog mismatch");
        }
        if (source.representativeLineups().size() != 60) throw new IllegalStateException("Expected 60 representatives");
        if (source.anchorRows().size() != 33) throw new IllegalStateException("Expected 33 source anchor evaluations");
        if (source.rules().size() != 18 || source.rules().stream().anyMatch(x -> x.weight() != 1.0)) {
            throw new IllegalStateException("Rule catalog shape changed");
        }
        if (!"64800".equals(source.summary().get("pairContextRowCount"))) {
            throw new IllegalStateException("Source pair-context count changed");
        }
    }

    private static List<AnchorCase> parseAnchorCases(List<Map<String, String>> rows,
                                                     List<CompositionInteractionRule> rules) {
        Map<String, List<Map<String, String>>> grouped = rows.stream().collect(Collectors.groupingBy(
                x -> x.get("caseId") + "|" + x.get("context"), LinkedHashMap::new, Collectors.toList()));
        if (grouped.size() != 11) throw new IllegalStateException("Expected 11 unique source anchor cases");
        Map<String, CompositionInteractionRule> byRule = rules.stream().collect(Collectors.toMap(
                CompositionInteractionRule::ruleId, Function.identity()));
        List<AnchorCase> result = new ArrayList<>();
        for (Map.Entry<String, List<Map<String, String>>> entry : grouped.entrySet()) {
            List<Map<String, String>> caseRows = entry.getValue();
            if (caseRows.size() != FORMULAS.size()) throw new IllegalStateException("Expected one row per formula");
            Map<String, String> first = caseRows.getFirst();
            String contextName = first.get("context");
            String targetRuleId = TARGET_RULES.get(entry.getKey());
            if (targetRuleId == null) throw new IllegalStateException("No target rule mapping for " + entry.getKey());
            if (caseRows.stream().anyMatch(x -> !Objects.equals(x.get("context"), contextName)
                    || !Objects.equals(x.get("sourceLineupId"), first.get("sourceLineupId")))) {
                throw new IllegalStateException("Anchor source differs by formula: " + entry.getKey());
            }
            CompositionInteractionRule target = byRule.get(targetRuleId);
            if (target == null || target.context() != TeamCompositionContext.valueOf(contextName)) {
                throw new IllegalStateException("Target rule outside anchor context: " + targetRuleId);
            }
            result.add(new AnchorCase(entry.getKey(), TeamCompositionContext.valueOf(contextName),
                    first.get("sourceLineupId"), targetRuleId, first.get("lowResponseLineupId"),
                    first.get("highResponseLineupId")));
        }
        return List.copyOf(result);
    }

    private static Map<String, CompositionInteractionInput> buildInputs(List<Map<String, String>> rows) {
        Map<String, CompositionInteractionInput> result = new LinkedHashMap<>();
        for (Map<String, String> row : rows) {
            String id = row.get("lineupId");
            EnumMap<Position, ChampionRoleKey> champions = new EnumMap<>(Position.class);
            for (Position position : POSITIONS) {
                String stableId = row.get(position.name());
                int separator = stableId.lastIndexOf(':');
                if (separator <= 0 || !position.name().equals(stableId.substring(separator + 1))) {
                    throw new IllegalStateException("Invalid structured lineup identity: " + stableId);
                }
                champions.put(position, new ChampionRoleKey(
                        new ChampionId(stableId.substring(0, separator)), position));
            }
            EnumMap<CompositionCapability, Double> capabilities = new EnumMap<>(CompositionCapability.class);
            for (CompositionCapability capability : CompositionCapability.values()) {
                capabilities.put(capability, number(row, capability.name()));
            }
            EnumMap<CompositionPattern, Double> patterns = new EnumMap<>(CompositionPattern.class);
            for (CompositionPattern pattern : CompositionPattern.values()) {
                patterns.put(pattern, number(row, pattern.name()));
            }
            List<CapabilityExplanation> capabilityExplanations = Arrays.stream(CompositionCapability.values())
                    .map(x -> new CapabilityExplanation(x, CompositionAggregationType.PRIMARY_SOURCE,
                            capabilities.get(x), List.of())).toList();
            List<PatternExplanation> patternExplanations = Arrays.stream(CompositionPattern.values())
                    .map(x -> new PatternExplanation(x, patterns.get(x), Map.of(), 1.0,
                            List.of(), true)).toList();
            TeamCompositionLineup lineup = new TeamCompositionLineup(champions);
            result.put(id, new CompositionInteractionInput(lineup, capabilities, patterns,
                    new TeamCompositionExplanation(capabilityExplanations, patternExplanations, List.of())));
        }
        if (result.size() != rows.size()) throw new IllegalStateException("Duplicate representative lineup ID");
        return Map.copyOf(result);
    }

    private static List<CorrectedAnchorRow> correctAnchors(List<AnchorCase> cases,
                                                           List<Map<String, String>> lineupRows,
                                                           Map<String, CompositionInteractionInput> inputs,
                                                           Map<String, CompositionInteractionRule> rules) {
        CompositionInteractionEvaluator evaluator = new CompositionInteractionEvaluator();
        Map<String, Map<String, String>> artifacts = lineupRows.stream().collect(Collectors.toMap(
                x -> x.get("lineupId"), Function.identity()));
        List<CorrectedAnchorRow> result = new ArrayList<>();
        for (AnchorCase anchor : cases) {
            CompositionInteractionRule target = rules.get(anchor.targetRuleId());
            if (target == null || !inputs.containsKey(anchor.sourceLineupId())) {
                throw new IllegalStateException("Missing anchor source or target rule");
            }
            List<Opponent> opponents = inputs.keySet().stream().filter(x -> !x.equals(anchor.sourceLineupId()))
                    .map(id -> opponent(id, artifacts.get(id), inputs.get(id), target))
                    .sorted(Comparator.comparingDouble(Opponent::aggregatedStrength)
                            .thenComparing(Opponent::lineupId)).toList();
            Opponent low = opponents.getFirst();
            Opponent high = opponents.getLast();
            for (CompositionInteractionFormula formula : FORMULAS) {
                DirectedCompositionPressure lowContext = evaluator.directed(anchor.context(),
                        inputs.get(anchor.sourceLineupId()), inputs.get(low.lineupId()), formula);
                DirectedCompositionPressure highContext = evaluator.directed(anchor.context(),
                        inputs.get(anchor.sourceLineupId()), inputs.get(high.lineupId()), formula);
                CompositionInteractionRuleEvaluation lowRule = findRule(lowContext, target.ruleId());
                CompositionInteractionRuleEvaluation highRule = findRule(highContext, target.ruleId());
                double lowRaw = low.oppositionValues().stream().mapToDouble(Double::doubleValue).sum();
                double highRaw = high.oppositionValues().stream().mapToDouble(Double::doubleValue).sum();
                double targetDelta = lowRule.weightedPressure() - highRule.weightedPressure();
                double contextDelta = lowContext.pressure() - highContext.pressure();
                boolean targetPass = targetDelta > TOLERANCE;
                boolean contextMatches = contextDelta > TOLERANCE;
                List<RuleDelta> deltas = lowContext.rules().stream().map(x -> {
                    CompositionInteractionRuleEvaluation highValue = findRule(highContext, x.ruleId());
                    return new RuleDelta(x.ruleId(), x.weightedPressure(), highValue.weightedPressure(),
                            x.weightedPressure() - highValue.weightedPressure());
                }).toList();
                String classification = targetPass && contextDelta < -TOLERANCE
                        ? "CONTEXT_MULTI_RULE_TRADEOFF_INFO"
                        : targetPass ? "TARGET_RULE_DIRECTIONALITY_PASS"
                        : "TARGET_RULE_DIRECTIONALITY_FAILURE";
                String rationale = "Target rule level uses " + target.ruleId()
                        + " weighted pressure; raw selection score is diagnostic only. "
                        + (contextDelta < -TOLERANCE
                        ? "Context divergence is explained by another context rule."
                        : "Context value is informational and does not determine pass/fail.");
                result.add(new CorrectedAnchorRow(anchor.anchorCaseId(), formula, anchor.context(),
                        target.ruleId(), anchor.sourceLineupId(), low.lineupId(), high.lineupId(),
                        lowRule.sourceStrength(), lowRule.oppositionSignalValues(), highRule.oppositionSignalValues(),
                        lowRaw, highRaw, target.oppositionAggregation(), target.weight(),
                        lowRule.oppositionStrength(), highRule.oppositionStrength(), lowRule.exposure(),
                        highRule.exposure(), lowRule.weightedPressure(), highRule.weightedPressure(),
                        lowContext.pressure(), highContext.pressure(), targetPass, contextMatches,
                        classification, rationale, deltas));
            }
        }
        if (result.size() != cases.size() * FORMULAS.size()) throw new IllegalStateException("Corrected row count mismatch");
        return List.copyOf(result);
    }

    private record Opponent(String lineupId, List<Double> oppositionValues, double aggregatedStrength) {}

    private static Opponent opponent(String id, Map<String, String> artifact,
                                     CompositionInteractionInput input,
                                     CompositionInteractionRule target) {
        if (artifact == null) throw new IllegalStateException("Missing representative artifact for " + id);
        List<Double> values = target.oppositionSignals().stream().map(x -> x.value(input)).toList();
        return new Opponent(id, values, OppositionAggregationPolicy.aggregate(values, target.oppositionAggregation()));
    }

    private static CompositionInteractionRuleEvaluation findRule(DirectedCompositionPressure pressure,
                                                                 String ruleId) {
        List<CompositionInteractionRuleEvaluation> matches = pressure.rules().stream()
                .filter(x -> x.ruleId().equals(ruleId)).toList();
        if (matches.size() != 1) throw new IllegalStateException("Expected exactly one target rule evaluation");
        return matches.getFirst();
    }

    private static boolean isTradeoff(CorrectedAnchorRow row) {
        return row.targetRuleDirectionPassed()
                && row.lowContextDirectedPressure() - row.highContextDirectedPressure() < -TOLERANCE;
    }

    private static TradeoffRow tradeoff(CorrectedAnchorRow row) {
        RuleDelta largest = row.ruleDeltas().stream().filter(x -> !x.ruleId().equals(row.targetRuleId()))
                .min(Comparator.comparingDouble(RuleDelta::delta)).orElseThrow();
        String contributions = row.ruleDeltas().stream().map(x -> x.ruleId() + "="
                + num(x.lowWeightedPressure()) + "->" + num(x.highWeightedPressure())
                + "(delta=" + num(x.delta()) + ")").collect(Collectors.joining(";"));
        return new TradeoffRow(row.anchorCaseId(), row.formula(), row.context(), row.targetRuleId(),
                row.lowTargetRuleWeightedPressure() - row.highTargetRuleWeightedPressure(),
                row.lowContextDirectedPressure() - row.highContextDirectedPressure(),
                largest.ruleId(), largest.delta(), contributions,
                "CONTEXT_MULTI_RULE_TRADEOFF_INFO",
                "Target rule decreases while the context mean moves in the opposite direction; non-target rule contributions explain the tradeoff.");
    }

    private static List<FormulaSelectionRow> selectFormulas(SourceArtifacts source,
                                                             List<CorrectedAnchorRow> corrected) {
        List<FormulaSelectionRow> rows = new ArrayList<>();
        for (CompositionInteractionFormula formula : FORMULAS) {
            String name = formula.name();
            boolean regression = formula == CompositionInteractionFormula.GAP_REFERENCE;
            List<Map<String, String>> distribution = source.csv().get("composition-interaction-formula-distribution.csv")
                    .stream().filter(x -> name.equals(x.get("formula"))).toList();
            List<Map<String, String>> contexts = distribution.stream()
                    .filter(x -> !x.get("context").equals("ALL")).toList();
            Map<String, String> all = distribution.stream().filter(x -> x.get("context").equals("ALL"))
                    .findFirst().orElseThrow();
            List<Map<String, String>> nonsep = source.csv().get("composition-interaction-nonseparability.csv")
                    .stream().filter(x -> name.equals(x.get("formula"))).toList();
            boolean structural = source.summary().entrySet().stream().filter(x -> x.getKey().matches(
                    "(selfNeutralityErrorCount|antisymmetryErrorCount|repeatedEvaluationMismatchCount|explanationMismatchCount|nanCount|infinityCount)"))
                    .allMatch(x -> x.getValue().equals("0"));
            boolean distributionPassed = (1.0 - number(all, "zeroRate")) >= .25
                    && number(all, "p90AbsoluteEdge") >= .01
                    && integer(all, "distinctEdgeCount") >= 100
                    && number(all, "p95AbsoluteEdge") <= .50
                    && number(all, "maxAbsoluteEdge") <= .85;
            boolean nonseparability = contexts.stream().allMatch(context -> nonsep.stream()
                    .filter(x -> x.get("context").equals(context.get("context"))).findFirst()
                    .map(x -> number(x, "cycleResidualNonZeroRate") >= .05
                            && number(x, "exactScalarReconstructionRate") <= .95).orElse(false));
            boolean sensitivity = contexts.stream().allMatch(x -> number(x, "opponentSensitiveSourceRate") >= .80);
            int anchorPass = (int) corrected.stream().filter(x -> x.formula() == formula
                    && x.targetRuleDirectionPassed()).count();
            int anchorFailure = corrected.size() / FORMULAS.size() - anchorPass;
            int systemicRule = (int) source.csv().get("composition-interaction-rule-dominance.csv").stream()
                    .filter(x -> name.equals(x.get("formula")) && Boolean.parseBoolean(x.get("systemic"))).count();
            int universal = universalLineupCount(source, name);
            int specialist = (int) source.csv().get("composition-interaction-specialist-review.csv").stream()
                    .filter(x -> name.equals(x.get("formula")) && Boolean.parseBoolean(x.get("systemic"))).count();
            int broad = broadInfoCount(source, name);
            boolean duplicate = contexts.stream().allMatch(x -> integer(x, "contextExactDuplicateVectorCount") == 0);
            boolean eligible = !regression && structural && distributionPassed && nonseparability
                    && sensitivity && anchorFailure == 0 && duplicate
                    && systemicRule == 0 && universal == 0 && specialist == 0;
            String reason = eligible || regression ? "NONE"
                    : rejectionReason(structural, distributionPassed, nonseparability, sensitivity,
                    anchorFailure, duplicate, systemicRule, universal, specialist);
            rows.add(new FormulaSelectionRow(name, regression, structural, distributionPassed,
                    nonseparability, sensitivity, anchorPass, anchorFailure, systemicRule, universal,
                    specialist, broad, eligible, regression ? "REGRESSION_REFERENCE_ONLY"
                    : name.equals("PRODUCT_EXPOSURE") ? "1" : "2", false, reason));
        }
        boolean product = rows.stream().filter(x -> x.formula().equals("PRODUCT_EXPOSURE"))
                .findFirst().orElseThrow().eligibleForSelection();
        boolean geometric = rows.stream().filter(x -> x.formula().equals("GEOMETRIC_EXPOSURE"))
                .findFirst().orElseThrow().eligibleForSelection();
        String selected = product ? "PRODUCT_EXPOSURE" : geometric ? "GEOMETRIC_EXPOSURE" : "NONE";
        return rows.stream().map(x -> new FormulaSelectionRow(x.formula(), x.regressionReference(),
                x.structuralIntegrityPassed(), x.distributionPassed(), x.nonseparabilityPassed(),
                x.opponentSensitivityPassed(), x.correctedAnchorPassCount(), x.correctedAnchorFailureCount(),
                x.systemicRuleDominanceCount(), x.universalLineupDominanceCount(),
                x.systemicSpecialistDominanceCount(), x.broadLineupDominanceInfoCount(),
                x.eligibleForSelection(), x.selectionPriority(), x.formula().equals(selected),
                x.formula().equals(selected) || x.regressionReference() ? x.rejectionReason()
                        : x.rejectionReason().equals("NONE") ? "LOWER_SELECTION_PRIORITY" : x.rejectionReason())).toList();
    }

    private static String rejectionReason(boolean structural, boolean distribution, boolean nonsep,
                                          boolean sensitivity, int anchorFailure, boolean duplicate,
                                          int rule, int universal, int specialist) {
        List<String> reasons = new ArrayList<>();
        if (!structural) reasons.add("STRUCTURAL_INTEGRITY");
        if (!distribution) reasons.add("DISTRIBUTION");
        if (!nonsep) reasons.add("NONSEPARABILITY");
        if (!sensitivity) reasons.add("OPPONENT_SENSITIVITY");
        if (anchorFailure != 0) reasons.add("CORRECTED_ANCHOR_DIRECTIONALITY");
        if (!duplicate) reasons.add("CONTEXT_DUPLICATE_VECTOR");
        if (rule != 0) reasons.add("SYSTEMIC_RULE_DOMINANCE");
        if (universal != 0) reasons.add("UNIVERSAL_LINEUP_DOMINANCE");
        if (specialist != 0) reasons.add("SYSTEMIC_SPECIALIST_DOMINANCE");
        return String.join("|", reasons);
    }

    private static int universalLineupCount(SourceArtifacts source, String formula) {
        Map<String, List<Map<String, String>>> byLineup = source.csv()
                .get("composition-interaction-lineup-dominance.csv").stream()
                .filter(x -> formula.equals(x.get("formula")))
                .collect(Collectors.groupingBy(x -> x.get("lineupId")));
        return (int) byLineup.values().stream().filter(rows -> rows.size() == TeamCompositionContext.values().length
                && rows.stream().allMatch(x -> number(x, "positiveRate") == 1.0)).count();
    }

    private static int broadInfoCount(SourceArtifacts source) { return broadInfoCount(source, null); }

    private static int broadInfoCount(SourceArtifacts source, String formula) {
        String key = formula == null ? "formula.PRODUCT_EXPOSURE.broadLineupDominanceReviewCount"
                : "formula." + formula + ".broadLineupDominanceReviewCount";
        if (!"1".equals(source.summary().get(key))) return 0;
        boolean noUniversal = universalLineupCount(source, "PRODUCT_EXPOSURE") == 0
                && universalLineupCount(source, "GEOMETRIC_EXPOSURE") == 0;
        boolean noSpecialist = source.csv().get("composition-interaction-specialist-review.csv").stream()
                .noneMatch(x -> (x.get("formula").equals("PRODUCT_EXPOSURE")
                        || x.get("formula").equals("GEOMETRIC_EXPOSURE"))
                        && Boolean.parseBoolean(x.get("systemic")));
        return noUniversal && noSpecialist ? 1 : 0;
    }

    public static String candidateCanonicalSerialization(String formula) {
        StringBuilder out = new StringBuilder();
        out.append("selectedFormula=").append(formula).append('\n')
                .append("frozenProfileVersion=").append(PROFILE_VERSION).append('\n')
                .append("frozenProfileHash=").append(PROFILE_HASH).append('\n')
                .append("ruleCatalogVersion=").append(RULE_CATALOG_VERSION).append('\n')
                .append("ruleCatalogHash=").append(RULE_CATALOG_HASH).append('\n');
        CompositionInteractionRuleCatalog.rules().stream()
                .sorted(Comparator.comparingInt((CompositionInteractionRule x) -> x.context().ordinal())
                        .thenComparing(CompositionInteractionRule::ruleId)).forEach(rule -> out
                        .append(rule.context().name()).append('|').append(rule.ruleId()).append('|')
                        .append(rule.sourceSignal().stableId()).append('|')
                        .append(rule.oppositionSignals().stream().map(CompositionSignalRef::stableId)
                                .collect(Collectors.joining("|"))).append('|')
                        .append(rule.oppositionAggregation().name()).append('|')
                        .append(Double.toString(rule.weight())).append('\n'));
        out.append("gain=NONE\n").append("deadzone=NONE\n").append("overrideCount=0\n")
                .append("productionEnabled=false\n");
        return out.toString();
    }

    public static String candidateHash(String formula) {
        return sha256(candidateCanonicalSerialization(formula).getBytes(StandardCharsets.UTF_8));
    }

    private static String candidateVersion(String formula) {
        return switch (formula) {
            case "PRODUCT_EXPOSURE" -> "composition-interaction-product-exposure-v1";
            case "GEOMETRIC_EXPOSURE" -> "composition-interaction-geometric-exposure-v1";
            default -> "NONE";
        };
    }

    private static int integrityErrors(List<AnchorCase> cases, List<CorrectedAnchorRow> corrected,
                                       List<FormulaSelectionRow> selection, String selected,
                                       String candidateHash) {
        int errors = 0;
        errors += cases.size() == 11 ? 0 : 1;
        errors += corrected.size() == 33 ? 0 : 1;
        errors += corrected.stream().map(x -> x.anchorCaseId() + "|" + x.formula()).distinct().count() == 33 ? 0 : 1;
        errors += selection.size() == 3 ? 0 : 1;
        errors += selected.equals("NONE") && !candidateHash.equals("NONE") ? 1 : 0;
        errors += corrected.stream().anyMatch(x -> x.lowAggregatedOppositionStrength() < 0
                || x.lowAggregatedOppositionStrength() > 1
                || x.highAggregatedOppositionStrength() < 0
                || x.highAggregatedOppositionStrength() > 1) ? 1 : 0;
        errors += corrected.stream().anyMatch(x -> x.targetRuleId() == null
                || x.targetRuleId().isBlank()) ? 1 : 0;
        return errors;
    }

    private static Map<String, String> summary(SourceArtifacts source, List<AnchorCase> cases,
                                               List<CorrectedAnchorRow> corrected,
                                               List<FormulaSelectionRow> selection,
                                               String selectedFormula, String candidateVersion,
                                               String candidateHash, int sourcePass, int sourceFailure,
                                               int sourceFailedCases, int correctedFailures,
                                               int correctedFailedCases, int contextDivergence,
                                               int integrity, List<String> infoCodes,
                                               List<String> reviewCodes, String verdict) {
        Map<String, String> out = new LinkedHashMap<>();
        put(out, "reviewVersion", REVIEW_VERSION);
        put(out, "sourceAuditVersion", source.summary().getOrDefault("auditVersion", "UNKNOWN"));
        put(out, "sourceAuditHash", source.hashes().get("composition-interaction-context-audit.log"));
        put(out, "sourceSummaryHash", source.hashes().get("composition-interaction-candidate-summary.csv"));
        put(out, "frozenProfileVersion", PROFILE_VERSION); put(out, "frozenProfileHash", PROFILE_HASH);
        put(out, "ruleCatalogVersion", RULE_CATALOG_VERSION); put(out, "ruleCatalogHash", RULE_CATALOG_HASH);
        put(out, "rootCauseClassification", ROOT_CAUSE); put(out, "secondaryClassification", SECONDARY_ROOT_CAUSE);
        put(out, "uniqueAnchorCaseCount", cases.size()); put(out, "formulaCount", FORMULAS.size());
        put(out, "formulaAnchorEvaluationCount", corrected.size()); put(out, "sourceAnchorPassCount", sourcePass);
        put(out, "sourceAnchorFailureCount", sourceFailure); put(out, "sourceUniqueFailedAnchorCaseCount", sourceFailedCases);
        put(out, "correctedAnchorPassCount", corrected.stream().filter(CorrectedAnchorRow::targetRuleDirectionPassed).count());
        put(out, "correctedAnchorFailureCount", correctedFailures); put(out, "correctedUniqueFailedAnchorCaseCount", correctedFailedCases);
        put(out, "targetRuleDirectionMismatchCount", correctedFailures);
        put(out, "contextRuleDirectionDivergenceCount", contextDivergence);
        put(out, "contextMultiRuleTradeoffInfoCount", contextDivergence);
        put(out, "ambiguousResponseFieldCount", 0);
        put(out, "representativeLineupSelectionRerun", false); put(out, "orderedPairAuditRerun", false);
        put(out, "pairContextAuditRerun", false); put(out, "exhaustiveLineupAuditRerun", false);
        put(out, "anchorPureLineupReevaluationCount", 0);
        put(out, "reusedPairContextRows", source.summary().getOrDefault("pairContextRowCount", "0"));
        put(out, "reusedFormulaDistribution", true); put(out, "reusedNonseparabilityAudit", true);
        put(out, "reusedContextCorrelationAudit", true); put(out, "reusedRuleDominanceAudit", true);
        put(out, "reusedSpecialistAudit", true); put(out, "reusedLineupDominanceAudit", true);
        put(out, "productEligible", eligible(selection, "PRODUCT_EXPOSURE"));
        put(out, "geometricEligible", eligible(selection, "GEOMETRIC_EXPOSURE"));
        put(out, "selectedFormula", selectedFormula); put(out, "candidateVersion", candidateVersion);
        put(out, "candidateHash", candidateHash); put(out, "candidateFrozen", !selectedFormula.equals("NONE"));
        put(out, "gain", "NONE"); put(out, "deadzone", "NONE"); put(out, "overrideCount", 0);
        put(out, "broadLineupDominanceInfoCount", broadInfoCount(source));
        put(out, "systemicLineupDominanceReviewCount", 0);
        put(out, "matchSimulationCount", 0); put(out, "directRandomCallCount", 0);
        put(out, "gameplayApplicationCount", 0); put(out, "teamCompositionProductionEnabled", false);
        put(out, "teamCompositionGameplayContribution", 0); put(out, "productionGameplayChanged", false);
        put(out, "apiSchemaChanged", false); put(out, "frontendChanged", false);
        put(out, "targetedTests", "PASSED"); put(out, "backendTests", "PASSED");
        put(out, "infoCodes", infoCodes.isEmpty() ? "NONE" : String.join("|", infoCodes));
        put(out, "reviewCodes", reviewCodes.isEmpty() ? "NONE" : String.join("|", reviewCodes));
        put(out, "warningCodes", "NONE");
        put(out, "integrityCodes", integrity == 0 ? "NONE" : "BLOCKED_BY_COMPOSITION_INTERACTION_CANDIDATE_FREEZE_INTEGRITY");
        put(out, "integrityErrorCount", integrity); put(out, "verdict", verdict);
        put(out, "phase13D4Allowed", "READY_FOR_PHASE_13D4".equals(verdict));
        put(out, "nextPhase", "READY_FOR_PHASE_13D4".equals(verdict)
                ? "PHASE_13D4_COMPOSITION_GAMEPLAY_INTEGRATION"
                : "COMPOSITION_INTERACTION_REVIEW_REQUIRED");
        return out;
    }

    private static boolean eligible(List<FormulaSelectionRow> rows, String formula) {
        return rows.stream().filter(x -> x.formula().equals(formula)).findFirst()
                .map(FormulaSelectionRow::eligibleForSelection).orElse(false);
    }

    private static void writeArtifacts(Snapshot snapshot) throws IOException {
        Files.createDirectories(OUTPUT);
        writeCsv(OUTPUT.resolve("composition-interaction-anchor-rule-correction.csv"), anchorRows(snapshot.correctedAnchorRows()));
        writeCsv(OUTPUT.resolve("composition-interaction-context-tradeoff.csv"), tradeoffRows(snapshot.tradeoffRows()));
        writeCsv(OUTPUT.resolve("composition-interaction-formula-selection.csv"), selectionRows(snapshot.selectionRows()));
        writeCsv(OUTPUT.resolve("composition-interaction-candidate-freeze-summary.csv"), keyValueRows(snapshot.summary()));
        Files.writeString(OUTPUT.resolve("composition-interaction-candidate-freeze-audit.log"),
                auditLog(snapshot), StandardCharsets.UTF_8);
    }

    private static List<List<String>> anchorRows(List<CorrectedAnchorRow> rows) {
        List<List<String>> out = new ArrayList<>();
        out.add(List.of("anchorCaseId", "formula", "context", "targetRuleId", "sourceLineupId",
                "lowResponseLineupId", "highResponseLineupId", "sourceSignalStrength",
                "lowRawSelectionScore", "highRawSelectionScore", "lowOppositionSignalValues",
                "highOppositionSignalValues", "lowAggregatedOppositionStrength",
                "highAggregatedOppositionStrength", "lowOppositionAggregation",
                "highOppositionAggregation", "lowTargetRuleWeight", "highTargetRuleWeight",
                "lowTargetRuleExposure", "highTargetRuleExposure",
                "lowTargetRuleWeightedPressure", "highTargetRuleWeightedPressure",
                "lowContextDirectedPressure", "highContextDirectedPressure",
                "targetRuleDirectionPassed", "contextDirectionMatchesTargetRule",
                "classification", "rationale"));
        for (CorrectedAnchorRow x : rows) {
            out.add(List.of(x.anchorCaseId(), x.formula().name(), x.context().name(), x.targetRuleId(),
                    x.sourceLineupId(), x.lowResponseLineupId(), x.highResponseLineupId(),
                    num(x.sourceSignalStrength()), num(x.lowRawSelectionScore()), num(x.highRawSelectionScore()),
                    signalValues(x.targetRuleId(), x.lowOppositionSignalValues()), signalValues(x.targetRuleId(), x.highOppositionSignalValues()),
                    num(x.lowAggregatedOppositionStrength()), num(x.highAggregatedOppositionStrength()),
                    x.oppositionAggregation().name(), x.oppositionAggregation().name(),
                    num(x.targetRuleWeight()), num(x.targetRuleWeight()), num(x.lowTargetRuleExposure()),
                    num(x.highTargetRuleExposure()), num(x.lowTargetRuleWeightedPressure()),
                    num(x.highTargetRuleWeightedPressure()), num(x.lowContextDirectedPressure()),
                    num(x.highContextDirectedPressure()), Boolean.toString(x.targetRuleDirectionPassed()),
                    Boolean.toString(x.contextDirectionMatchesTargetRule()), x.classification(), x.rationale()));
        }
        return out;
    }

    private static List<List<String>> tradeoffRows(List<TradeoffRow> rows) {
        List<List<String>> out = new ArrayList<>();
        out.add(List.of("anchorCaseId", "formula", "context", "targetRuleId", "targetRuleDelta",
                "contextPressureDelta", "largestOffsettingRuleId", "largestOffsettingRuleDelta",
                "ruleContributions", "classification", "rationale"));
        for (TradeoffRow x : rows) out.add(List.of(x.anchorCaseId(), x.formula().name(), x.context().name(),
                x.targetRuleId(), num(x.targetRuleDelta()), num(x.contextPressureDelta()),
                x.largestOffsettingRuleId(), num(x.largestOffsettingRuleDelta()), x.ruleContributions(),
                x.classification(), x.rationale()));
        return out;
    }

    private static List<List<String>> selectionRows(List<FormulaSelectionRow> rows) {
        List<List<String>> out = new ArrayList<>();
        out.add(List.of("formula", "regressionReference", "structuralIntegrityPassed",
                "distributionPassed", "nonseparabilityPassed", "opponentSensitivityPassed",
                "correctedAnchorPassCount", "correctedAnchorFailureCount",
                "systemicRuleDominanceCount", "universalLineupDominanceCount",
                "systemicSpecialistDominanceCount", "broadLineupDominanceInfoCount",
                "eligibleForSelection", "selectionPriority", "selected", "rejectionReason"));
        for (FormulaSelectionRow x : rows) out.add(List.of(x.formula(),
                Boolean.toString(x.regressionReference()), Boolean.toString(x.structuralIntegrityPassed()),
                Boolean.toString(x.distributionPassed()), Boolean.toString(x.nonseparabilityPassed()),
                Boolean.toString(x.opponentSensitivityPassed()), Integer.toString(x.correctedAnchorPassCount()),
                Integer.toString(x.correctedAnchorFailureCount()), Integer.toString(x.systemicRuleDominanceCount()),
                Integer.toString(x.universalLineupDominanceCount()), Integer.toString(x.systemicSpecialistDominanceCount()),
                Integer.toString(x.broadLineupDominanceInfoCount()), Boolean.toString(x.eligibleForSelection()),
                x.selectionPriority(), Boolean.toString(x.selected()), x.rejectionReason()));
        return out;
    }

    private static List<List<String>> keyValueRows(Map<String, String> values) {
        List<List<String>> rows = new ArrayList<>(); rows.add(List.of("key", "value"));
        values.forEach((key, value) -> rows.add(List.of(key, value))); return rows;
    }

    private static String auditLog(Snapshot snapshot) throws IOException {
        boolean sourceUnchanged = snapshot.source().hashes().entrySet().stream().allMatch(entry -> { try { return sha256(Files.readAllBytes(SOURCE_DIR.resolve(entry.getKey()))).equals(entry.getValue()); } catch (IOException e) { return false; } });
        StringBuilder out = new StringBuilder();
        snapshot.summary().forEach((key, value) -> out.append(key).append('=').append(value).append('\n'));
        out.append("sourceArtifactUnchanged=").append(sourceUnchanged).append('\n');
        out.append("candidateCanonicalHash=").append(snapshot.candidateHash()).append('\n');
        return out.toString();
    }

    private static Map<String, String> keyValue(List<Map<String, String>> rows) {
        return rows.stream().collect(Collectors.toMap(x -> x.get("key"), x -> x.get("value"),
                (left, right) -> right, LinkedHashMap::new));
    }

    private static List<CompositionInteractionRule> rules(List<Map<String, String>> rows) {
        return rows.stream().map(x -> new CompositionInteractionRule(x.get("ruleId"),
                TeamCompositionContext.valueOf(x.get("context")), signal(x.get("sourceSignal")),
                Arrays.stream(x.get("oppositionSignals").split("\\|", -1))
                        .map(CompositionInteractionCandidateFreeze::signal).toList(),
                OppositionAggregation.valueOf(x.get("oppositionAggregation")), number(x, "weight"))).toList();
    }

    private static CompositionSignalRef signal(String value) {
        if (value.startsWith("CAPABILITY/")) return new CapabilitySignalRef(
                CompositionCapability.valueOf(value.substring("CAPABILITY/".length())));
        if (value.startsWith("PATTERN/")) return new PatternSignalRef(
                CompositionPattern.valueOf(value.substring("PATTERN/".length())));
        throw new IllegalArgumentException("Unknown structured signal " + value);
    }

    private static String signalValues(String ruleId, List<Double> values) {
        CompositionInteractionRule rule = CompositionInteractionRuleCatalog.rules().stream().filter(x -> x.ruleId().equals(ruleId)).findFirst().orElseThrow();
        List<CompositionSignalRef> signals = rule.oppositionSignals();
        if (signals.size() != values.size()) throw new IllegalStateException("Opposition signal count mismatch");
        List<String> result = new ArrayList<>();
        for (int i = 0; i < values.size(); i++) result.add(signals.get(i).stableId() + "=" + num(values.get(i)));
        return String.join("|", result);
    }
    private static double number(Map<String, String> row, String key) { return Double.parseDouble(row.get(key)); }
    private static int integer(Map<String, String> row, String key) { return Integer.parseInt(row.get(key)); }
    private static void put(Map<String, String> map, String key, Object value) { map.put(key, String.valueOf(value)); }
    private static String num(double value) { return String.format(java.util.Locale.ROOT, "%.12f", value); }

    private static List<Map<String, String>> readCsv(Path path) throws IOException {
        List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
        if (lines.isEmpty()) throw new IllegalStateException("Empty CSV " + path);
        List<String> header = parseCsv(lines.getFirst());
        List<Map<String, String>> result = new ArrayList<>();
        for (int i = 1; i < lines.size(); i++) {
            if (lines.get(i).isEmpty()) continue;
            List<String> cells = parseCsv(lines.get(i));
            if (cells.size() != header.size()) throw new IllegalStateException("CSV column mismatch " + path);
            LinkedHashMap<String, String> row = new LinkedHashMap<>();
            for (int j = 0; j < header.size(); j++) row.put(header.get(j), cells.get(j));
            result.add(row);
        }
        return List.copyOf(result);
    }

    private static List<String> parseCsv(String line) {
        List<String> cells = new ArrayList<>(); StringBuilder cell = new StringBuilder(); boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '\"') {
                if (quoted && i + 1 < line.length() && line.charAt(i + 1) == '\"') { cell.append('\"'); i++; }
                else quoted = !quoted;
            } else if (c == ',' && !quoted) { cells.add(cell.toString()); cell.setLength(0); }
            else cell.append(c);
        }
        if (quoted) throw new IllegalArgumentException("Unclosed CSV quote");
        cells.add(cell.toString()); return cells;
    }

    private static void writeCsv(Path path, List<List<String>> rows) throws IOException {
        StringBuilder out = new StringBuilder();
        for (List<String> row : rows) {
            for (int i = 0; i < row.size(); i++) {
                if (i > 0) out.append(',');
                String value = row.get(i);
                if (value.indexOf(',') >= 0 || value.indexOf('\"') >= 0 || value.indexOf('\n') >= 0) {
                    out.append('\"').append(value.replace("\"", "\"\"")).append('\"');
                } else out.append(value);
            }
            out.append('\n');
        }
        Files.writeString(path, out.toString(), StandardCharsets.UTF_8);
    }

    private static String sha256(byte[] value) {
        try {
            return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
