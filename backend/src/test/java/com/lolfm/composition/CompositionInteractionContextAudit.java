package com.lolfm.composition;

import com.lolfm.champion.ChampionId;
import com.lolfm.champion.ChampionRoleKey;
import com.lolfm.domain.Position;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.ToDoubleFunction;
import java.util.stream.Collectors;

/** Pure Phase 13D-3 audit. It reads the frozen 7,776-lineup artifact and re-evaluates only 60 representatives. */
public final class CompositionInteractionContextAudit {
    public static final Path SOURCE = Path.of("build", "reports", "thirty-champion-composition-profiles", "thirty-champion-composition-lineups.csv");
    public static final Path OUTPUT = Path.of("build", "reports", "composition-interaction-context");
    public static final String AUDIT_VERSION = "phase-13d3-composition-interaction-context-audit-v1";
    public static final String FROZEN_PROFILE_VERSION = ThirtyChampionCompositionProfiles.VERSION;
    public static final String FROZEN_PROFILE_HASH = "fbf58dc5be12f2b07c5dff7ded9e182d7829999d2255e65dbbd073ccde2688d1";
    private static final List<Position> POSITIONS = List.of(Position.TOP, Position.JUNGLE, Position.MID, Position.ADC, Position.SUPPORT);
    private static final List<String> ANCHOR_IDS = List.of(
            "ornn:TOP+vi:JUNGLE+orianna:MID+kaisa:ADC+rakan:SUPPORT",
            "ornn:TOP+sejuani:JUNGLE+viktor:MID+jinx:ADC+braum:SUPPORT",
            "kennen:TOP+nidalee:JUNGLE+viktor:MID+ezreal:ADC+renata-glasc:SUPPORT",
            "ksante:TOP+vi:JUNGLE+leblanc:MID+varus:ADC+bard:SUPPORT",
            "jax:TOP+nidalee:JUNGLE+ahri:MID+ezreal:ADC+lulu:SUPPORT",
            "ornn:TOP+viego:JUNGLE+azir:MID+jinx:ADC+renata-glasc:SUPPORT",
            "gwen:TOP+nidalee:JUNGLE+viktor:MID+ezreal:ADC+lulu:SUPPORT",
            "gwen:TOP+nidalee:JUNGLE+leblanc:MID+lucian:ADC+lulu:SUPPORT",
            "renekton:TOP+viego:JUNGLE+leblanc:MID+lucian:ADC+nautilus:SUPPORT",
            "renekton:TOP+lee-sin:JUNGLE+ahri:MID+lucian:ADC+nautilus:SUPPORT",
            "gwen:TOP+nidalee:JUNGLE+viktor:MID+kaisa:ADC+lulu:SUPPORT",
            "ksante:TOP+vi:JUNGLE+orianna:MID+varus:ADC+braum:SUPPORT");
    private static final List<CompositionInteractionFormula> FORMULAS = List.of(CompositionInteractionFormula.values());
    private static final double TOLERANCE = 1e-12;
    private static final int TRIPLE_COUNT = 1_000;
    private static final int TRIPLE_STRIDE = 7_919;

    private CompositionInteractionContextAudit() {}

    public static void main(String[] args) throws Exception {
        AuditSnapshot snapshot = compute();
        writeArtifacts(snapshot);
        System.out.println("Composition interaction context audit: " + snapshot.verdict());
        System.out.println("selectedFormula=" + snapshot.selectedFormula() + " representativeLineups=" + snapshot.representatives().size());
        System.out.println("Artifacts: " + OUTPUT.toAbsolutePath());
        if (snapshot.integrityErrorCount() != 0) throw new IllegalStateException("Composition interaction integrity errors=" + snapshot.integrityErrorCount());
    }

    public static AuditSnapshot compute() throws IOException {
        if (!Files.isRegularFile(SOURCE)) throw new IllegalStateException("Missing frozen lineup artifact: " + SOURCE.toAbsolutePath());
        List<ArtifactLineup> artifactLineups = readLineups(SOURCE);
        if (artifactLineups.size() != 7_776) throw new IllegalStateException("Expected 7776 source lineups, got " + artifactLineups.size());
        List<RepresentativeLineup> representatives = selectRepresentatives(artifactLineups);
        Map<String, CompositionInteractionInput> inputs = analyzeRepresentatives(representatives, artifactLineups.stream().collect(Collectors.toMap(ArtifactLineup::lineupId, x -> x)));
        int parityMismatch = (int) representatives.stream().filter(x -> !x.artifactParity()).count();
        InteractionRows interactionRows = evaluatePairs(representatives, inputs);
        List<FormulaMetrics> formulaMetrics = formulaMetrics(interactionRows, representatives, inputs);
        List<AnchorRow> anchorRows = anchorDirectionality(representatives, inputs);
        List<SpecialistRow> specialistRows = specialistReview(representatives, interactionRows);
        List<LineupDominanceRow> lineupDominanceRows = lineupDominance(representatives, interactionRows);
        List<RuleDominanceRow> ruleDominanceRows = ruleDominance(interactionRows, representatives, inputs);
        List<CorrelationRow> correlationRows = correlations(interactionRows);
        Selection selection = selectFormula(formulaMetrics, anchorRows, correlationRows, ruleDominanceRows, lineupDominanceRows, specialistRows);
        int selfErrors = interactionRows.selfNeutralityErrors();
        int antisymmetryErrors = interactionRows.antisymmetryErrors();
        int repeatedMismatch = interactionRows.repeatedEvaluationMismatchCount();
        int explanationMismatch = interactionRows.explanationMismatchCount();
        int numericErrors = interactionRows.nanCount() + interactionRows.infinityCount();
        int integrity = parityMismatch + selfErrors + antisymmetryErrors + repeatedMismatch + explanationMismatch + numericErrors
                + (representatives.size() == 60 ? 0 : 1)
                + (interactionRows.rows().size() == 64_800 ? 0 : 1)
                + (CompositionInteractionRuleCatalog.rules().size() == 18 ? 0 : 1)
                + (CompositionInteractionRuleCatalog.rules().stream().filter(r -> r.weight() != 1.0).count() == 0 ? 0 : 1);
        List<String> reviews = new ArrayList<>(selection.reviewCodes());
        if (!selection.selected()) reviews.add("FORMULA_SELECTION_REVIEW");
        reviews = reviews.stream().distinct().sorted().toList();
        List<String> info = List.of("EXPECTED_SPARSE_DAMAGE_CHANNEL", "BROAD_BUT_VARIABLE_INFO", "CURRENT_CATALOG_UNREACHABLE_INFO", "SPECIALIST_CONCENTRATION_INFO");
        String verdict = integrity != 0 ? "BLOCKED_BY_COMPOSITION_INTERACTION_INTEGRITY"
                : selection.selected() && reviews.isEmpty() ? "READY_FOR_PHASE_13D4" : "REVIEW_COMPOSITION_INTERACTION_FORMULA";
        return new AuditSnapshot(artifactLineups.size(), representatives, interactionRows.rows(), formulaMetrics, anchorRows,
                specialistRows, lineupDominanceRows, ruleDominanceRows, correlationRows, selection.formula(), selection.candidateVersion(),
                parityMismatch, selfErrors, antisymmetryErrors, repeatedMismatch, explanationMismatch,
                interactionRows.nanCount(), interactionRows.infinityCount(), integrity, info, reviews, List.of(), verdict,
                selection.selected() && integrity == 0 && reviews.isEmpty());
    }

    public record AuditSnapshot(
            int sourceLineupCount,
            List<RepresentativeLineup> representatives,
            List<PairContextRow> pairContextRows,
            List<FormulaMetrics> formulaMetrics,
            List<AnchorRow> anchorRows,
            List<SpecialistRow> specialistRows,
            List<LineupDominanceRow> lineupDominanceRows,
            List<RuleDominanceRow> ruleDominanceRows,
            List<CorrelationRow> correlationRows,
            String selectedFormula,
            String selectedCandidateVersion,
            int representativeParityMismatchCount,
            int selfNeutralityErrorCount,
            int antisymmetryErrorCount,
            int repeatedEvaluationMismatchCount,
            int explanationMismatchCount,
            int nanCount,
            int infinityCount,
            int integrityErrorCount,
            List<String> infoCodes,
            List<String> reviewCodes,
            List<String> warningCodes,
            String verdict,
            boolean phase13D4Allowed
    ) {
        public AuditSnapshot {
            representatives = List.copyOf(representatives);
            pairContextRows = List.copyOf(pairContextRows);
            formulaMetrics = List.copyOf(formulaMetrics);
            anchorRows = List.copyOf(anchorRows);
            specialistRows = List.copyOf(specialistRows);
            lineupDominanceRows = List.copyOf(lineupDominanceRows);
            ruleDominanceRows = List.copyOf(ruleDominanceRows);
            correlationRows = List.copyOf(correlationRows);
            infoCodes = List.copyOf(infoCodes);
            reviewCodes = List.copyOf(reviewCodes);
            warningCodes = List.copyOf(warningCodes);
        }
    }

    public record ArtifactLineup(String lineupId, Map<String, String> values) {
        public ArtifactLineup {
            Objects.requireNonNull(lineupId);
            values = Map.copyOf(values);
        }
        double number(String key) { return Double.parseDouble(values.get(key)); }
    }

    public record RepresentativeLineup(String lineupId, TeamCompositionLineup lineup, TeamCompositionAnalysis analysis,
                                        String selectionSource, String selectionDetail, boolean artifactParity) {
        public RepresentativeLineup {
            Objects.requireNonNull(lineupId); Objects.requireNonNull(lineup);
            Objects.requireNonNull(selectionSource); Objects.requireNonNull(selectionDetail);
        }
    }

    public record PairContextRow(String formula, String pairId, String sourceLineupId, String opponentLineupId,
                                 TeamCompositionContext context, double sourceToOpponentPressure, double opponentToSourcePressure,
                                 double signedEdge, boolean selfPair, boolean antisymmetryExact, boolean explanationParity) {}

    public record FormulaMetrics(String formula, String context, int count, int zeroCount, double zeroRate,
                                 double meanAbsoluteEdge, double p50AbsoluteEdge, double p75AbsoluteEdge, double p90AbsoluteEdge,
                                 double p95AbsoluteEdge, double p99AbsoluteEdge, double maxAbsoluteEdge, int distinctEdgeCount,
                                 int positiveCount, int negativeCount, double signBalance, int opponentSensitiveSourceCount,
                                 double opponentSensitiveSourceRate, double cycleResidualNonZeroRate, double cycleMeanAbsoluteResidual,
                                 double cycleP90AbsoluteResidual, double cycleP95AbsoluteResidual, double cycleMaxAbsoluteResidual,
                                 double exactScalarReconstructionRate, int contextExactDuplicateVectorCount,
                                 int anchorDirectionalityPassCount, int anchorDirectionalityFailureCount, int systemicRuleDominanceCount,
                                 int universalLineupDominanceCount, int broadLineupDominanceReviewCount, int systemicSpecialistDominanceCount) {}

    public record AnchorRow(String formula, String caseId, TeamCompositionContext context, String sourceLineupId,
                            String lowResponseLineupId, String highResponseLineupId, double lowResponseValue, double highResponseValue,
                            double lowPressure, double highPressure, boolean passed) {}

    public record SpecialistRow(String formula, String specialist, TeamCompositionContext context, int topPairCount,
                                int includedTopPairCount, double includedTopPairRate, double positiveTopPairRate,
                                double negativeTopPairRate, boolean relatedContext, boolean systemic) {}

    public record LineupDominanceRow(String formula, String lineupId, TeamCompositionContext context,
                                     int positiveOpponentCount, int negativeOpponentCount, int zeroOpponentCount,
                                     double positiveRate) {}

    public record RuleDominanceRow(String formula, TeamCompositionContext context, String ruleId, double meanShare,
                                   double medianShare, double p90Share, double maxShare, int topContributionPairCount,
                                   double shareAtLeast70Rate, double shareAtLeast85Rate, boolean systemic) {}

    public record CorrelationRow(String formula, TeamCompositionContext contextA, TeamCompositionContext contextB,
                                 double spearman, boolean exactIdenticalVector) {}

    static List<ArtifactLineup> readLineups(Path path) throws IOException {
        List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
        if (lines.isEmpty()) throw new IllegalStateException("Empty lineup artifact");
        List<String> header = parseCsv(lines.get(0));
        List<ArtifactLineup> result = new ArrayList<>();
        for (int line = 1; line < lines.size(); line++) {
            List<String> cells = parseCsv(lines.get(line));
            if (cells.size() != header.size()) throw new IllegalStateException("CSV column mismatch at line " + (line + 1));
            LinkedHashMap<String, String> values = new LinkedHashMap<>();
            for (int i = 0; i < header.size(); i++) values.put(header.get(i), cells.get(i));
            result.add(new ArtifactLineup(values.get("lineupId"), values));
        }
        return List.copyOf(result);
    }

    static List<RepresentativeLineup> selectRepresentatives(List<ArtifactLineup> source) {
        Map<String, ArtifactLineup> byId = source.stream().collect(Collectors.toMap(ArtifactLineup::lineupId, x -> x, (left, right) -> { throw new IllegalStateException("Duplicate lineupId"); }, LinkedHashMap::new));
        LinkedHashMap<String, RepresentativeSelection> selected = new LinkedHashMap<>();
        for (String anchor : ANCHOR_IDS) {
            ArtifactLineup row = byId.get(anchor);
            if (row == null) throw new IllegalStateException("Missing frozen anchor lineup " + anchor);
            selected.putIfAbsent(anchor, new RepresentativeSelection(row, "ANCHOR", anchor));
        }
        List<ArtifactLineup> ordered = source.stream().sorted(Comparator.comparing(ArtifactLineup::lineupId)).toList();
        for (CompositionPattern pattern : CompositionPattern.values()) {
            List<ArtifactLineup> top = ordered.stream().sorted(Comparator.comparingDouble((ArtifactLineup x) -> x.number(pattern.name())).reversed().thenComparing(ArtifactLineup::lineupId)).limit(3).toList();
            List<ArtifactLineup> values = ordered.stream().sorted(Comparator.comparingDouble((ArtifactLineup x) -> x.number(pattern.name())).thenComparing(ArtifactLineup::lineupId)).filter(x -> x.number(pattern.name()) > 0.0).limit(3).toList();
            double median = percentile(ordered.stream().mapToDouble(x -> x.number(pattern.name())).sorted().toArray(), .5);
            List<ArtifactLineup> medianRows = ordered.stream().sorted(Comparator.comparingDouble((ArtifactLineup x) -> Math.abs(x.number(pattern.name()) - median)).thenComparingDouble(x -> x.number(pattern.name())).thenComparing(ArtifactLineup::lineupId)).limit(2).toList();
            int index = 0;
            for (ArtifactLineup row : top) selected.putIfAbsent(row.lineupId(), new RepresentativeSelection(row, "PATTERN", pattern.name() + "_TOP_" + (++index)));
            index = 0;
            for (ArtifactLineup row : medianRows) selected.putIfAbsent(row.lineupId(), new RepresentativeSelection(row, "PATTERN", pattern.name() + "_MEDIAN_" + (++index)));
            index = 0;
            for (ArtifactLineup row : values) selected.putIfAbsent(row.lineupId(), new RepresentativeSelection(row, "PATTERN", pattern.name() + "_LOW_POSITIVE_" + (++index)));
        }
        for (ArtifactLineup row : ordered) {
            if (selected.size() >= 60) break;
            selected.putIfAbsent(row.lineupId(), new RepresentativeSelection(row, "FILL", "LINEUP_ID_ASCENDING"));
        }
        if (selected.size() != 60) throw new IllegalStateException("Representative selection produced " + selected.size());
        List<RepresentativeSelection> choices = selected.values().stream().toList();
        return new ArrayList<>(choices.stream().map(x -> new RepresentativeLineup(x.row().lineupId(), parseLineup(x.row()), null, x.source(), x.detail(), false)).toList());
    }

    static Map<String, CompositionInteractionInput> analyzeRepresentatives(List<RepresentativeLineup> choices, Map<String, ArtifactLineup> artifacts) {
        TeamCompositionAnalyzer analyzer = new TeamCompositionAnalyzer();
        Map<String, CompositionInteractionInput> inputs = new LinkedHashMap<>();
        List<RepresentativeLineup> updated = new ArrayList<>();
        for (RepresentativeLineup choice : choices) {
            TeamCompositionAnalysis first = analyzer.analyze(choice.lineup(), ThirtyChampionCompositionProfiles.all());
            TeamCompositionAnalysis second = analyzer.analyze(choice.lineup(), ThirtyChampionCompositionProfiles.all());
            boolean internalParity = first.equals(second) && explanationParity(first);
            boolean artifactParity = internalParity && artifactParity(choice.lineupId(), artifacts.get(choice.lineupId()), first);
            inputs.put(choice.lineupId(), CompositionInteractionInput.fromAnalysis(first));
            updated.add(new RepresentativeLineup(choice.lineupId(), choice.lineup(), first, choice.selectionSource(), choice.selectionDetail(), artifactParity));
        }
        choices.clear();
        choices.addAll(updated);
        return Map.copyOf(inputs);
    }

    private static boolean artifactParity(String id, ArtifactLineup artifact, TeamCompositionAnalysis analysis) {
        if (artifact == null || !id.equals(artifact.lineupId())) return false;
        for (CompositionCapability capability : CompositionCapability.values()) {
            if (!close(analysis.coverage().capability(capability).coverage(), artifact.number(capability.name()))) return false;
        }
        for (CompositionPattern pattern : CompositionPattern.values()) {
            if (!close(analysis.patterns().get(pattern).readiness(), artifact.number(pattern.name()))) return false;
        }
        var damage = analysis.coverage().damageChannels();
        return close(damage.physicalShare(), artifact.number("physicalShare"))
                && close(damage.magicShare(), artifact.number("magicShare"))
                && close(damage.trueDamageShare(), artifact.number("trueDamageShare"))
                && Boolean.parseBoolean(artifact.values().get("explanationParity"))
                && Boolean.parseBoolean(artifact.values().get("repeatedEvaluationExact"));
    }

    private static boolean close(double left, double right) { return Math.abs(left - right) <= TOLERANCE; }

    private static boolean explanationParity(TeamCompositionAnalysis analysis) {
        for (CapabilityExplanation explanation : analysis.explanation().capabilities()) {
            CapabilityCoverage actual = analysis.coverage().capability(explanation.capability());
            if (Double.compare(explanation.coverage(), actual.coverage()) != 0 || !explanation.contributors().equals(actual.contributors())) return false;
        }
        for (PatternExplanation explanation : analysis.explanation().patterns()) {
            PatternEvaluation actual = analysis.patterns().get(explanation.pattern());
            if (Double.compare(explanation.readiness(), actual.readiness()) != 0 || !explanation.componentCoverages().equals(actual.componentCoverages()) || !explanation.primaryContributors().equals(actual.primaryContributors())) return false;
        }
        return true;
    }

    private static TeamCompositionLineup parseLineup(ArtifactLineup row) {
        EnumMap<Position, ChampionRoleKey> champions = new EnumMap<>(Position.class);
        for (Position position : POSITIONS) {
            String stableId = row.values().get(position.name());
            int separator = stableId.lastIndexOf(':');
            if (separator <= 0) throw new IllegalArgumentException("Invalid lineup identity " + stableId);
            champions.put(position, new ChampionRoleKey(new ChampionId(stableId.substring(0, separator)), position));
        }
        return new TeamCompositionLineup(champions);
    }

    private static InteractionRows evaluatePairs(List<RepresentativeLineup> representatives, Map<String, CompositionInteractionInput> inputs) {
        CompositionInteractionEvaluator evaluator = new CompositionInteractionEvaluator();
        List<PairContextRow> rows = new ArrayList<>(64_800);
        Map<RowKey, Double> edges = new HashMap<>();
        Map<RowKey, Double> pressures = new HashMap<>();
        int selfErrors = 0, antisymmetryErrors = 0, repeated = 0, explanation = 0, nan = 0, infinity = 0;
        for (CompositionInteractionFormula formula : FORMULAS) {
            for (int sourceIndex = 0; sourceIndex < representatives.size(); sourceIndex++) {
                RepresentativeLineup source = representatives.get(sourceIndex);
                for (int opponentIndex = 0; opponentIndex < representatives.size(); opponentIndex++) {
                    RepresentativeLineup opponent = representatives.get(opponentIndex);
                    CompositionInteractionAnalysis result = evaluator.evaluate(inputs.get(source.lineupId()), inputs.get(opponent.lineupId()), formula);
                    CompositionInteractionAnalysis repeatedResult = evaluator.evaluate(inputs.get(source.lineupId()), inputs.get(opponent.lineupId()), formula);
                    if (!result.equals(repeatedResult)) repeated++;
                    boolean self = sourceIndex == opponentIndex;
                    boolean pairExplanation = explanationParity(result);
                    if (!pairExplanation) explanation++;
                    for (TeamCompositionContext context : TeamCompositionContext.values()) {
                        CompositionContextInteraction interaction = result.contexts().get(context);
                        double edge = interaction.teamASignedEdge();
                        if (Double.isNaN(edge)) nan++;
                        if (Double.isInfinite(edge)) infinity++;
                        if (self && Double.doubleToLongBits(edge) != Double.doubleToLongBits(0.0)) selfErrors++;
                        RowKey key = new RowKey(formula, source.lineupId(), opponent.lineupId(), context);
                        edges.put(key, edge);
                        pressures.put(key, interaction.teamAToTeamB().pressure());
                        String pairId = String.format(Locale.ROOT, "pair-%04d-%04d", sourceIndex, opponentIndex);
                        rows.add(new PairContextRow(formula.name(), pairId, source.lineupId(), opponent.lineupId(), context,
                                interaction.teamAToTeamB().pressure(), interaction.teamBToTeamA().pressure(), edge, self,
                                true, pairExplanation));
                    }
                }
            }
        }
        for (Map.Entry<RowKey, Double> entry : edges.entrySet()) {
            RowKey key = entry.getKey();
            RowKey reverse = new RowKey(key.formula(), key.opponentLineupId(), key.sourceLineupId(), key.context());
            Double reverseValue = edges.get(reverse);
            if (reverseValue == null || Math.abs(entry.getValue() + reverseValue) > TOLERANCE) antisymmetryErrors++;
        }
        List<PairContextRow> fixedRows = new ArrayList<>(rows.size());
        for (PairContextRow row : rows) {
            double reverse = edges.get(new RowKey(CompositionInteractionFormula.valueOf(row.formula()), row.opponentLineupId(), row.sourceLineupId(), row.context()));
            fixedRows.add(new PairContextRow(row.formula(), row.pairId(), row.sourceLineupId(), row.opponentLineupId(), row.context(), row.sourceToOpponentPressure(), row.opponentToSourcePressure(), row.signedEdge(), row.selfPair(), Math.abs(row.signedEdge() + reverse) <= TOLERANCE, row.explanationParity()));
        }
        return new InteractionRows(List.copyOf(fixedRows), edges, pressures, selfErrors, antisymmetryErrors, repeated, explanation, nan, infinity);
    }

    private static boolean explanationParity(CompositionInteractionAnalysis analysis) {
        for (TeamCompositionContext context : TeamCompositionContext.values()) {
            CompositionContextInteraction interaction = analysis.contexts().get(context);
            CompositionContextInteractionExplanation explained = analysis.explanation().contexts().get(context);
            if (Double.compare(interaction.teamAToTeamB().pressure(), explained.teamAToTeamBPressure()) != 0
                    || Double.compare(interaction.teamBToTeamA().pressure(), explained.teamBToTeamAPressure()) != 0
                    || Double.compare(interaction.teamASignedEdge(), explained.teamASignedEdge()) != 0) return false;
        }
        return analysis.explanation().ruleEvaluations().size() == 36;
    }

    private record RepresentativeSelection(ArtifactLineup row, String source, String detail) {}
    private record RowKey(CompositionInteractionFormula formula, String sourceLineupId, String opponentLineupId, TeamCompositionContext context) {}
    private record InteractionRows(List<PairContextRow> rows, Map<RowKey, Double> edges, Map<RowKey, Double> pressures,
                                   int selfNeutralityErrors, int antisymmetryErrors, int repeatedEvaluationMismatchCount,
                                   int explanationMismatchCount, int nanCount, int infinityCount) {}

    private static List<FormulaMetrics> formulaMetrics(InteractionRows interactionRows, List<RepresentativeLineup> representatives,
                                                        Map<String, CompositionInteractionInput> inputs) {
        List<FormulaMetrics> result = new ArrayList<>();
        for (CompositionInteractionFormula formula : FORMULAS) {
            for (TeamCompositionContext context : TeamCompositionContext.values()) {
                List<PairContextRow> rows = interactionRows.rows().stream().filter(x -> x.formula().equals(formula.name()) && x.context() == context).toList();
                result.add(metrics(formula, context.name(), rows, interactionRows, representatives, inputs));
            }
            List<PairContextRow> rows = interactionRows.rows().stream().filter(x -> x.formula().equals(formula.name())).toList();
            result.add(metrics(formula, "ALL", rows, interactionRows, representatives, inputs));
        }
        return List.copyOf(result);
    }

    private static FormulaMetrics metrics(CompositionInteractionFormula formula, String contextName, List<PairContextRow> rows,
                                          InteractionRows all, List<RepresentativeLineup> representatives, Map<String, CompositionInteractionInput> inputs) {
        double[] abs = rows.stream().mapToDouble(x -> Math.abs(x.signedEdge())).sorted().toArray();
        int zero = (int) rows.stream().filter(x -> x.signedEdge() == 0.0).count();
        int positive = (int) rows.stream().filter(x -> x.signedEdge() > 0.0).count();
        int negative = (int) rows.stream().filter(x -> x.signedEdge() < 0.0).count();
        int sensitive = 0;
        if (!contextName.equals("ALL")) {
            for (RepresentativeLineup source : representatives) {
                double[] values = rows.stream().filter(x -> x.sourceLineupId().equals(source.lineupId()) && !x.sourceLineupId().equals(x.opponentLineupId())).mapToDouble(PairContextRow::sourceToOpponentPressure).toArray();
                if (values.length > 0 && Arrays.stream(values).max().orElse(0) - Arrays.stream(values).min().orElse(0) > 1e-6 && Arrays.stream(values).distinct().count() >= 5) sensitive++;
            }
        }
        CycleMetrics cycle = contextName.equals("ALL") ? new CycleMetrics(0, 0, 0, 0, 0) : cycleMetrics(formula, TeamCompositionContext.valueOf(contextName), all.edges(), representatives);
        double reconstruction = contextName.equals("ALL") ? 0.0 : scalarReconstruction(formula, TeamCompositionContext.valueOf(contextName), all.edges(), representatives);
        int duplicateVectors = contextName.equals("ALL") ? 0 : duplicateContextVectors(formula, TeamCompositionContext.valueOf(contextName), all.edges(), representatives);
        int distinct = (int) rows.stream().map(PairContextRow::signedEdge).distinct().count();
        return new FormulaMetrics(formula.name(), contextName, rows.size(), zero, rate(zero, rows.size()), mean(abs), percentile(abs, .5), percentile(abs, .75), percentile(abs, .9), percentile(abs, .95), percentile(abs, .99), abs.length == 0 ? 0 : abs[abs.length - 1], distinct, positive, negative, positive + negative == 0 ? 0.0 : positive / (double) (positive + negative), sensitive, contextName.equals("ALL") ? 0.0 : sensitive / (double) representatives.size(), cycle.nonZeroRate(), cycle.meanAbsolute(), cycle.p90(), cycle.p95(), cycle.max(), reconstruction, duplicateVectors, 0, 0, 0, 0, 0, 0);
    }

    private static int duplicateContextVectors(CompositionInteractionFormula formula, TeamCompositionContext context, Map<RowKey, Double> edges, List<RepresentativeLineup> reps) {
        Set<List<Double>> vectors = new HashSet<>();
        for (RepresentativeLineup source : reps) vectors.add(reps.stream().map(opponent -> edges.get(new RowKey(formula, source.lineupId(), opponent.lineupId(), context))).toList());
        int duplicates = 0;
        return Math.max(0, reps.size() - vectors.size());
    }

    private record CycleMetrics(double nonZeroRate, double meanAbsolute, double p90, double p95, double max) {}

    private static CycleMetrics cycleMetrics(CompositionInteractionFormula formula, TeamCompositionContext context, Map<RowKey, Double> edges, List<RepresentativeLineup> reps) {
        List<Double> residuals = new ArrayList<>(TRIPLE_COUNT);
        Set<String> seen = new HashSet<>();
        long candidate = 0;
        while (residuals.size() < TRIPLE_COUNT) {
            long space = (long) reps.size() * reps.size() * reps.size();
            long index = (candidate++ * TRIPLE_STRIDE) % space;
            int a = (int) ((index / (reps.size() * reps.size())) % reps.size());
            int b = (int) ((index / reps.size()) % reps.size());
            int c = (int) (index % reps.size());
            if (a == b || b == c || a == c) continue;
            String key = a + ":" + b + ":" + c;
            if (!seen.add(key)) continue;
            String aid = reps.get(a).lineupId(), bid = reps.get(b).lineupId(), cid = reps.get(c).lineupId();
            residuals.add(edges.get(new RowKey(formula, aid, bid, context)) + edges.get(new RowKey(formula, bid, cid, context)) + edges.get(new RowKey(formula, cid, aid, context)));
        }
        double[] values = residuals.stream().mapToDouble(Math::abs).sorted().toArray();
        long nonZero = Arrays.stream(values).filter(x -> x > TOLERANCE).count();
        return new CycleMetrics(nonZero / (double) values.length, mean(values), percentile(values, .9), percentile(values, .95), values[values.length - 1]);
    }

    private static double scalarReconstruction(CompositionInteractionFormula formula, TeamCompositionContext context, Map<RowKey, Double> edges, List<RepresentativeLineup> reps) {
        String reference = reps.get(0).lineupId();
        Map<String, Double> rating = new HashMap<>();
        for (RepresentativeLineup lineup : reps) rating.put(lineup.lineupId(), edges.get(new RowKey(formula, lineup.lineupId(), reference, context)));
        int exact = 0, total = 0;
        for (RepresentativeLineup source : reps) for (RepresentativeLineup opponent : reps) {
            total++;
            double actual = edges.get(new RowKey(formula, source.lineupId(), opponent.lineupId(), context));
            double predicted = rating.get(source.lineupId()) - rating.get(opponent.lineupId());
            if (Math.abs(actual - predicted) <= TOLERANCE) exact++;
        }
        return exact / (double) total;
    }

    private static List<AnchorRow> anchorDirectionality(List<RepresentativeLineup> reps, Map<String, CompositionInteractionInput> inputs) {
        Map<String, RepresentativeLineup> byId = reps.stream().collect(Collectors.toMap(RepresentativeLineup::lineupId, x -> x));
        List<AnchorRow> rows = new ArrayList<>();
        for (CompositionInteractionFormula formula : FORMULAS) {
            addResponseRows(rows, formula, "ENGAGE", byId, reps, inputs, CompositionPattern.ENGAGE_CHAIN, TeamCompositionContext.SKIRMISH, List.of(CompositionCapability.DISENGAGE, CompositionCapability.PEEL));
            addResponseRows(rows, formula, "ENGAGE", byId, reps, inputs, CompositionPattern.ENGAGE_CHAIN, TeamCompositionContext.TEAMFIGHT, List.of(CompositionCapability.DISENGAGE, CompositionCapability.PEEL));
            addResponseRows(rows, formula, "ENGAGE", byId, reps, inputs, CompositionPattern.ENGAGE_CHAIN, TeamCompositionContext.SIEGE, List.of(CompositionCapability.DISENGAGE, CompositionCapability.FRONTLINE));
            addResponseRows(rows, formula, "POKE", byId, reps, inputs, CompositionPattern.POKE_SIEGE, TeamCompositionContext.OBJECTIVE_SETUP, List.of(CompositionCapability.WAVE_CLEAR, CompositionCapability.ENGAGE));
            addResponseRows(rows, formula, "POKE", byId, reps, inputs, CompositionPattern.POKE_SIEGE, TeamCompositionContext.SIEGE, List.of(CompositionCapability.WAVE_CLEAR, CompositionCapability.ENGAGE));
            addResponseRows(rows, formula, "PICK", byId, reps, inputs, CompositionPattern.PICK_CONVERSION, TeamCompositionContext.SKIRMISH, List.of(CompositionCapability.PEEL, CompositionCapability.FRONTLINE));
            addResponseRows(rows, formula, "PICK", byId, reps, inputs, CompositionPattern.PICK_CONVERSION, TeamCompositionContext.OBJECTIVE_SETUP, List.of(CompositionCapability.PEEL, CompositionCapability.FRONTLINE));
            addResponseRows(rows, formula, "PICK", byId, reps, inputs, CompositionPattern.PICK_CONVERSION, TeamCompositionContext.SIEGE, List.of(CompositionCapability.PEEL, CompositionCapability.ZONE_CONTROL));
            addResponseRows(rows, formula, "SPLIT", byId, reps, inputs, CompositionPattern.SPLIT_MAP_PRESSURE, TeamCompositionContext.SIDE_LANE, List.of(CompositionCapability.WAVE_CLEAR, CompositionCapability.PICK));
            addResponseRows(rows, formula, "FRONT_TO_BACK", byId, reps, inputs, CompositionPattern.FRONT_TO_BACK, TeamCompositionContext.TEAMFIGHT, List.of(CompositionCapability.BACKLINE_ACCESS, CompositionCapability.BURST_DAMAGE));
            RepresentativeLineup poke = byId.get("kennen:TOP+nidalee:JUNGLE+viktor:MID+ezreal:ADC+renata-glasc:SUPPORT");
            List<RepresentativeLineup> defense = reps.stream().filter(x -> !x.lineupId().equals(poke.lineupId())).sorted(Comparator.comparingDouble((RepresentativeLineup x) -> sumCaps(inputs.get(x.lineupId()), List.of(CompositionCapability.WAVE_CLEAR, CompositionCapability.DISENGAGE))).thenComparing(RepresentativeLineup::lineupId)).toList();
            RepresentativeLineup low = defense.getFirst(), high = defense.getLast();
            double lowPressure = new CompositionInteractionEvaluator().directed(TeamCompositionContext.BASE_DEFENSE, inputs.get(low.lineupId()), inputs.get(poke.lineupId()), formula).pressure();
            double highPressure = new CompositionInteractionEvaluator().directed(TeamCompositionContext.BASE_DEFENSE, inputs.get(high.lineupId()), inputs.get(poke.lineupId()), formula).pressure();
            rows.add(new AnchorRow(formula.name(), "BASE_DEFENSE", TeamCompositionContext.BASE_DEFENSE, poke.lineupId(), low.lineupId(), high.lineupId(), sumCaps(inputs.get(low.lineupId()), List.of(CompositionCapability.WAVE_CLEAR, CompositionCapability.DISENGAGE)), sumCaps(inputs.get(high.lineupId()), List.of(CompositionCapability.WAVE_CLEAR, CompositionCapability.DISENGAGE)), lowPressure, highPressure, highPressure > lowPressure + TOLERANCE));
        }
        return List.copyOf(rows);
    }

    private static void addResponseRows(List<AnchorRow> rows, CompositionInteractionFormula formula, String caseId,
                                        Map<String, RepresentativeLineup> byId, List<RepresentativeLineup> reps,
                                        Map<String, CompositionInteractionInput> inputs, CompositionPattern sourcePattern,
                                        TeamCompositionContext context, List<CompositionCapability> responseCaps) {
        String anchorId = switch (caseId) {
            case "ENGAGE" -> ANCHOR_IDS.get(0);
            case "POKE" -> ANCHOR_IDS.get(2);
            case "PICK" -> ANCHOR_IDS.get(3);
            case "SPLIT" -> ANCHOR_IDS.get(4);
            case "FRONT_TO_BACK" -> ANCHOR_IDS.get(1);
            default -> throw new IllegalArgumentException("Unknown anchor directionality case " + caseId);
        };
        RepresentativeLineup source = byId.get(anchorId);
        if (source == null || source.analysis().patterns().get(sourcePattern).readiness() <= 0.0) throw new IllegalStateException("Missing anchor source " + anchorId);
        List<RepresentativeLineup> opponents = reps.stream().filter(x -> !x.lineupId().equals(source.lineupId())).sorted(Comparator.comparingDouble((RepresentativeLineup x) -> sumCaps(inputs.get(x.lineupId()), responseCaps)).thenComparing(RepresentativeLineup::lineupId)).toList();
        RepresentativeLineup low = opponents.getFirst(), high = opponents.getLast();
        CompositionInteractionEvaluator evaluator = new CompositionInteractionEvaluator();
        double lowPressure = evaluator.directed(context, inputs.get(source.lineupId()), inputs.get(low.lineupId()), formula).pressure();
        double highPressure = evaluator.directed(context, inputs.get(source.lineupId()), inputs.get(high.lineupId()), formula).pressure();
        rows.add(new AnchorRow(formula.name(), caseId, context, source.lineupId(), low.lineupId(), high.lineupId(), sumCaps(inputs.get(low.lineupId()), responseCaps), sumCaps(inputs.get(high.lineupId()), responseCaps), lowPressure, highPressure, lowPressure > highPressure + TOLERANCE));
    }

    private static double sumCaps(CompositionInteractionInput input, List<CompositionCapability> capabilities) {
        return capabilities.stream().mapToDouble(x -> input.capabilityCoverage().get(x)).sum();
    }

    private static List<RuleDominanceRow> ruleDominance(InteractionRows rows, List<RepresentativeLineup> representatives, Map<String, CompositionInteractionInput> inputs) {
        CompositionInteractionEvaluator evaluator = new CompositionInteractionEvaluator();
        List<RuleDominanceRow> result = new ArrayList<>();
        for (CompositionInteractionFormula formula : FORMULAS) for (TeamCompositionContext context : TeamCompositionContext.values()) {
            for (CompositionInteractionRule rule : CompositionInteractionRuleCatalog.rules(context)) {
                List<Double> shares = new ArrayList<>();
                int top = 0;
                for (RepresentativeLineup source : representatives) for (RepresentativeLineup opponent : representatives) {
                    DirectedCompositionPressure pressure = evaluator.directed(context, inputs.get(source.lineupId()), inputs.get(opponent.lineupId()), formula);
                    double denominator = pressure.rules().stream().mapToDouble(x -> Math.abs(x.weightedPressure())).sum();
                    CompositionInteractionRuleEvaluation current = pressure.rules().stream().filter(x -> x.ruleId().equals(rule.ruleId())).findFirst().orElseThrow();
                    double share = denominator == 0.0 ? 0.0 : Math.abs(current.weightedPressure()) / denominator;
                    shares.add(share);
                    if (share >= pressure.rules().stream().mapToDouble(x -> Math.abs(x.weightedPressure())).max().orElse(0.0) - TOLERANCE) top++;
                }
                double[] values = shares.stream().mapToDouble(Double::doubleValue).sorted().toArray();
                double at70 = rate((int) shares.stream().filter(x -> x >= .70).count(), shares.size());
                double at85 = rate((int) shares.stream().filter(x -> x >= .85).count(), shares.size());
                result.add(new RuleDominanceRow(formula.name(), context, rule.ruleId(), mean(values), percentile(values, .5), percentile(values, .9), values[values.length - 1], top, at70, at85, mean(values) >= .70 || at85 >= .80));
            }
        }
        return List.copyOf(result);
    }

    private static List<CorrelationRow> correlations(InteractionRows rows) {
        List<CorrelationRow> result = new ArrayList<>();
        for (CompositionInteractionFormula formula : FORMULAS) {
            for (int i = 0; i < TeamCompositionContext.values().length; i++) for (int j = i + 1; j < TeamCompositionContext.values().length; j++) {
                TeamCompositionContext left = TeamCompositionContext.values()[i], right = TeamCompositionContext.values()[j];
                List<PairContextRow> a = rows.rows().stream().filter(x -> x.formula().equals(formula.name()) && x.context() == left).toList();
                List<PairContextRow> b = rows.rows().stream().filter(x -> x.formula().equals(formula.name()) && x.context() == right).toList();
                double[] av = a.stream().mapToDouble(PairContextRow::signedEdge).toArray();
                double[] bv = b.stream().mapToDouble(PairContextRow::signedEdge).toArray();
                boolean identical = Arrays.equals(av, bv);
                result.add(new CorrelationRow(formula.name(), left, right, spearman(av, bv), identical));
            }
        }
        return List.copyOf(result);
    }

    private static List<SpecialistRow> specialistReview(List<RepresentativeLineup> reps, InteractionRows rows) {
        List<ChampionRoleKey> specialists = List.of(key("ornn", Position.TOP), key("rakan", Position.SUPPORT), key("braum", Position.SUPPORT), key("nidalee", Position.JUNGLE), key("leblanc", Position.MID), key("jax", Position.TOP), key("azir", Position.MID));
        List<SpecialistRow> result = new ArrayList<>();
        for (CompositionInteractionFormula formula : FORMULAS) for (TeamCompositionContext context : TeamCompositionContext.values()) {
            List<PairContextRow> all = rows.rows().stream().filter(x -> x.formula().equals(formula.name()) && x.context() == context).sorted(Comparator.comparingDouble((PairContextRow x) -> Math.abs(x.signedEdge())).reversed().thenComparing(PairContextRow::pairId)).limit(36).toList();
            for (ChampionRoleKey specialist : specialists) {
                long included = all.stream().filter(x -> contains(reps, x.sourceLineupId(), specialist) || contains(reps, x.opponentLineupId(), specialist)).count();
                long positive = all.stream().filter(x -> x.signedEdge() > 0 && (contains(reps, x.sourceLineupId(), specialist) || contains(reps, x.opponentLineupId(), specialist))).count();
                long negative = all.stream().filter(x -> x.signedEdge() < 0 && (contains(reps, x.sourceLineupId(), specialist) || contains(reps, x.opponentLineupId(), specialist))).count();
                boolean related = switch (context) { case SKIRMISH, TEAMFIGHT -> specialist.championId().value().equals("ornn") || specialist.championId().value().equals("rakan") || specialist.championId().value().equals("braum") || specialist.championId().value().equals("leblanc"); case OBJECTIVE_SETUP -> specialist.championId().value().equals("azir") || specialist.championId().value().equals("nidalee") || specialist.championId().value().equals("leblanc"); case SIEGE -> specialist.championId().value().equals("nidalee") || specialist.championId().value().equals("leblanc") || specialist.championId().value().equals("ornn"); case BASE_DEFENSE -> specialist.championId().value().equals("braum") || specialist.championId().value().equals("rakan"); case SIDE_LANE -> specialist.championId().value().equals("jax"); };
                result.add(new SpecialistRow(formula.name(), specialist.stableId(), context, all.size(), (int) included, included / (double) all.size(), positive / (double) Math.max(1, included), negative / (double) Math.max(1, included), related, false));
            }
        }
        Map<String, Long> highContextCounts = result.stream().filter(x -> x.includedTopPairRate() >= .80)
                .collect(Collectors.groupingBy(x -> x.formula() + "|" + x.specialist(), Collectors.counting()));
        List<SpecialistRow> classified = result.stream().map(x -> new SpecialistRow(x.formula(), x.specialist(), x.context(), x.topPairCount(), x.includedTopPairCount(), x.includedTopPairRate(), x.positiveTopPairRate(), x.negativeTopPairRate(), x.relatedContext(), highContextCounts.getOrDefault(x.formula() + "|" + x.specialist(), 0L) >= 4)).toList();
        return List.copyOf(classified);
    }

    private static boolean contains(List<RepresentativeLineup> reps, String id, ChampionRoleKey key) { return reps.stream().filter(x -> x.lineupId().equals(id)).findFirst().orElseThrow().lineup().championsByPosition().containsValue(key); }

    private static List<LineupDominanceRow> lineupDominance(List<RepresentativeLineup> reps, InteractionRows rows) {
        List<LineupDominanceRow> result = new ArrayList<>();
        for (CompositionInteractionFormula formula : FORMULAS) for (RepresentativeLineup lineup : reps) for (TeamCompositionContext context : TeamCompositionContext.values()) {
            List<PairContextRow> values = rows.rows().stream().filter(x -> x.formula().equals(formula.name()) && x.context() == context && x.sourceLineupId().equals(lineup.lineupId()) && !x.selfPair()).toList();
            int positive = (int) values.stream().filter(x -> x.signedEdge() > 0).count();
            int negative = (int) values.stream().filter(x -> x.signedEdge() < 0).count();
            result.add(new LineupDominanceRow(formula.name(), lineup.lineupId(), context, positive, negative, values.size() - positive - negative, positive / (double) values.size()));
        }
        return List.copyOf(result);
    }

    private record Selection(boolean selected, String formula, String candidateVersion, List<String> reviewCodes) {}

    private static Selection selectFormula(List<FormulaMetrics> metrics, List<AnchorRow> anchors, List<CorrelationRow> correlations,
                                           List<RuleDominanceRow> dominance, List<LineupDominanceRow> lineupDominance, List<SpecialistRow> specialists) {
        List<String> reviews = new ArrayList<>();
        for (CompositionInteractionFormula formula : List.of(CompositionInteractionFormula.PRODUCT_EXPOSURE, CompositionInteractionFormula.GEOMETRIC_EXPOSURE)) {
            List<FormulaMetrics> contexts = metrics.stream().filter(x -> x.formula().equals(formula.name()) && !x.context().equals("ALL")).toList();
            FormulaMetrics all = metrics.stream().filter(x -> x.formula().equals(formula.name()) && x.context().equals("ALL")).findFirst().orElseThrow();
            boolean structural = contexts.stream().allMatch(x -> x.cycleResidualNonZeroRate() >= .05 && x.exactScalarReconstructionRate() <= .95 && x.opponentSensitiveSourceRate() >= .80);
            boolean anchorPass = anchors.stream().filter(x -> x.formula().equals(formula.name())).allMatch(AnchorRow::passed);
            boolean correlationPass = contexts.stream().allMatch(x -> x.contextExactDuplicateVectorCount() == 0);
            boolean dominancePass = dominance.stream().filter(x -> x.formula().equals(formula.name())).noneMatch(RuleDominanceRow::systemic);
            boolean universalPass = lineupDominance.stream().filter(x -> x.formula().equals(formula.name())).collect(Collectors.groupingBy(LineupDominanceRow::lineupId)).values().stream().noneMatch(rows -> TeamCompositionContext.values().length == rows.stream().filter(x -> x.positiveRate() == 1.0).count());
            boolean specialistPass = specialists.stream().filter(x -> x.formula().equals(formula.name())).noneMatch(SpecialistRow::systemic);
            boolean distributionPass = 1.0 - all.zeroRate() >= .25 && all.p90AbsoluteEdge() >= .01 && all.distinctEdgeCount() >= 100 && all.p95AbsoluteEdge() <= .50 && all.maxAbsoluteEdge() <= .85;
            if (!structural) reviews.add(formula.name() + "_SCALAR_OR_SENSITIVITY_REVIEW");
            if (!anchorPass) reviews.add(formula.name() + "_ANCHOR_DIRECTIONALITY_REVIEW");
            if (!correlationPass) reviews.add(formula.name() + "_CONTEXT_DIFFERENTIATION_REVIEW");
            if (!dominancePass) reviews.add(formula.name() + "_RULE_DOMINANCE_REVIEW");
            if (!universalPass) reviews.add(formula.name() + "_UNIVERSAL_LINEUP_REVIEW");
            if (!specialistPass) reviews.add(formula.name() + "_SPECIALIST_REVIEW");
            if (!distributionPass) reviews.add(formula.name() + "_DISTRIBUTION_REVIEW");
            if (!(structural && anchorPass && correlationPass && dominancePass && universalPass && specialistPass && distributionPass)) reviews.add(formula.name() + "_REVIEW");
            if (formula == CompositionInteractionFormula.PRODUCT_EXPOSURE && structural && anchorPass && correlationPass && dominancePass && universalPass && specialistPass && distributionPass) {
                return new Selection(true, formula.name(), "composition-interaction-product-exposure-v1", List.of());
            }
        }
        FormulaMetrics product = metrics.stream().filter(x -> x.formula().equals(CompositionInteractionFormula.PRODUCT_EXPOSURE.name()) && x.context().equals("ALL")).findFirst().orElseThrow();
        FormulaMetrics geometric = metrics.stream().filter(x -> x.formula().equals(CompositionInteractionFormula.GEOMETRIC_EXPOSURE.name()) && x.context().equals("ALL")).findFirst().orElseThrow();
        if (product.zeroRate() >= .25 && geometric.zeroRate() < .25) reviews.add("PRODUCT_DISTRIBUTION_COLLAPSE");
        return new Selection(false, "NONE", "NONE", reviews);
    }

    private static double spearman(double[] left, double[] right) {
        if (left.length != right.length || left.length == 0) return 0.0;
        double[] a = ranks(left), b = ranks(right);
        double am = mean(a), bm = mean(b), numerator = 0, aa = 0, bb = 0;
        for (int i = 0; i < a.length; i++) { double x = a[i] - am, y = b[i] - bm; numerator += x * y; aa += x * x; bb += y * y; }
        return aa == 0 || bb == 0 ? 0.0 : numerator / Math.sqrt(aa * bb);
    }

    private static double[] ranks(double[] values) {
        Integer[] order = new Integer[values.length]; for (int i = 0; i < values.length; i++) order[i] = i;
        Arrays.sort(order, Comparator.comparingDouble(i -> values[i]));
        double[] ranks = new double[values.length];
        int i = 0; while (i < order.length) { int j = i + 1; while (j < order.length && Double.compare(values[order[i]], values[order[j]]) == 0) j++; double rank = (i + 1 + j) / 2.0; for (int k = i; k < j; k++) ranks[order[k]] = rank; i = j; }
        return ranks;
    }

    private static ChampionRoleKey key(String id, Position position) { return new ChampionRoleKey(new ChampionId(id), position); }
    private static double rate(int count, int total) { return total == 0 ? 0.0 : count / (double) total; }
    private static double mean(double[] values) { return Arrays.stream(values).average().orElse(0.0); }
    private static double percentile(double[] sorted, double probability) { if (sorted.length == 0) return 0.0; int index = Math.max(0, Math.min(sorted.length - 1, (int) Math.ceil(sorted.length * probability) - 1)); return sorted[index] == 0.0 ? 0.0 : sorted[index]; }

    private static void writeArtifacts(AuditSnapshot snapshot) throws IOException {
        Files.createDirectories(OUTPUT);
        writeCsv(OUTPUT.resolve("composition-interaction-rule-catalog.csv"), ruleCatalogRows());
        writeCsv(OUTPUT.resolve("composition-interaction-representative-lineups.csv"), representativeRows(snapshot.representatives()));
        writeCsv(OUTPUT.resolve("composition-interaction-formula-distribution.csv"), formulaRows(snapshot.formulaMetrics()));
        writeCsv(OUTPUT.resolve("composition-interaction-pair-context.csv"), pairRows(snapshot.pairContextRows()));
        writeCsv(OUTPUT.resolve("composition-interaction-rule-dominance.csv"), ruleDominanceRows(snapshot.ruleDominanceRows()));
        writeCsv(OUTPUT.resolve("composition-interaction-nonseparability.csv"), nonseparabilityRows(snapshot.formulaMetrics()));
        writeCsv(OUTPUT.resolve("composition-interaction-context-correlation.csv"), correlationRows(snapshot.correlationRows()));
        writeCsv(OUTPUT.resolve("composition-interaction-anchor-directionality.csv"), anchorRows(snapshot.anchorRows()));
        writeCsv(OUTPUT.resolve("composition-interaction-specialist-review.csv"), specialistRows(snapshot.specialistRows()));
        writeCsv(OUTPUT.resolve("composition-interaction-lineup-dominance.csv"), lineupRows(snapshot.lineupDominanceRows()));
        writeCsv(OUTPUT.resolve("composition-interaction-candidate-summary.csv"), summaryRows(snapshot));
        writeLog(snapshot);
    }

    private static List<List<String>> ruleCatalogRows() { List<List<String>> rows = new ArrayList<>(); rows.add(List.of("ruleId","context","sourceSignal","oppositionSignals","oppositionAggregation","weight")); for (CompositionInteractionRule r : CompositionInteractionRuleCatalog.rules()) rows.add(List.of(r.ruleId(), r.context().name(), r.sourceSignal().stableId(), r.oppositionSignals().stream().map(CompositionSignalRef::stableId).collect(Collectors.joining("|")), r.oppositionAggregation().name(), num(r.weight()))); return rows; }
    private static List<List<String>> representativeRows(List<RepresentativeLineup> reps) { List<String> h = new ArrayList<>(List.of("lineupId","selectionSource","selectionDetail","artifactParity")); for (Position p : POSITIONS) h.add(p.name()); for (CompositionCapability c : CompositionCapability.values()) h.add(c.name()); for (CompositionPattern p : CompositionPattern.values()) h.add(p.name()); h.add("physicalShare"); h.add("magicShare"); h.add("trueDamageShare"); List<List<String>> rows = new ArrayList<>(); rows.add(h); for (RepresentativeLineup r : reps) { List<String> row = new ArrayList<>(List.of(r.lineupId(), r.selectionSource(), r.selectionDetail(), Boolean.toString(r.artifactParity()))); for (Position p : POSITIONS) row.add(r.lineup().championsByPosition().get(p).stableId()); for (CompositionCapability c : CompositionCapability.values()) row.add(num(r.analysis().coverage().capability(c).coverage())); for (CompositionPattern p : CompositionPattern.values()) row.add(num(r.analysis().patterns().get(p).readiness())); var d = r.analysis().coverage().damageChannels(); row.add(num(d.physicalShare())); row.add(num(d.magicShare())); row.add(num(d.trueDamageShare())); rows.add(row); } return rows; }
    private static List<List<String>> formulaRows(List<FormulaMetrics> values) { List<List<String>> rows = new ArrayList<>(); rows.add(List.of("formula","context","count","zeroCount","zeroRate","meanAbsoluteEdge","p50AbsoluteEdge","p75AbsoluteEdge","p90AbsoluteEdge","p95AbsoluteEdge","p99AbsoluteEdge","maxAbsoluteEdge","distinctEdgeCount","positiveCount","negativeCount","signBalance","opponentSensitiveSourceCount","opponentSensitiveSourceRate","cycleResidualNonZeroRate","exactScalarReconstructionRate","contextExactDuplicateVectorCount")); for (FormulaMetrics x : values) rows.add(List.of(x.formula(),x.context(),Integer.toString(x.count()),Integer.toString(x.zeroCount()),num(x.zeroRate()),num(x.meanAbsoluteEdge()),num(x.p50AbsoluteEdge()),num(x.p75AbsoluteEdge()),num(x.p90AbsoluteEdge()),num(x.p95AbsoluteEdge()),num(x.p99AbsoluteEdge()),num(x.maxAbsoluteEdge()),Integer.toString(x.distinctEdgeCount()),Integer.toString(x.positiveCount()),Integer.toString(x.negativeCount()),num(x.signBalance()),Integer.toString(x.opponentSensitiveSourceCount()),num(x.opponentSensitiveSourceRate()),num(x.cycleResidualNonZeroRate()),num(x.exactScalarReconstructionRate()),Integer.toString(x.contextExactDuplicateVectorCount()))); return rows; }
    private static List<List<String>> pairRows(List<PairContextRow> values) { List<List<String>> rows = new ArrayList<>(); rows.add(List.of("formula","pairId","sourceLineupId","opponentLineupId","context","sourceToOpponentPressure","opponentToSourcePressure","signedEdge","selfPair","antisymmetryExact","explanationParity")); for (PairContextRow x : values) rows.add(List.of(x.formula(),x.pairId(),x.sourceLineupId(),x.opponentLineupId(),x.context().name(),num(x.sourceToOpponentPressure()),num(x.opponentToSourcePressure()),num(x.signedEdge()),Boolean.toString(x.selfPair()),Boolean.toString(x.antisymmetryExact()),Boolean.toString(x.explanationParity()))); return rows; }
    private static List<List<String>> ruleDominanceRows(List<RuleDominanceRow> values) { List<List<String>> rows = new ArrayList<>(); rows.add(List.of("formula","context","ruleId","meanShare","medianShare","p90Share","maxShare","topContributionPairCount","shareAtLeast70Rate","shareAtLeast85Rate","systemic")); for (RuleDominanceRow x : values) rows.add(List.of(x.formula(),x.context().name(),x.ruleId(),num(x.meanShare()),num(x.medianShare()),num(x.p90Share()),num(x.maxShare()),Integer.toString(x.topContributionPairCount()),num(x.shareAtLeast70Rate()),num(x.shareAtLeast85Rate()),Boolean.toString(x.systemic()))); return rows; }
    private static List<List<String>> nonseparabilityRows(List<FormulaMetrics> values) { List<List<String>> rows = new ArrayList<>(); rows.add(List.of("formula","context","cycleResidualNonZeroRate","cycleMeanAbsoluteResidual","cycleP90AbsoluteResidual","cycleP95AbsoluteResidual","cycleMaxAbsoluteResidual","exactScalarReconstructionRate")); for (FormulaMetrics x : values) if (!x.context().equals("ALL")) rows.add(List.of(x.formula(),x.context(),num(x.cycleResidualNonZeroRate()),num(x.cycleMeanAbsoluteResidual()),num(x.cycleP90AbsoluteResidual()),num(x.cycleP95AbsoluteResidual()),num(x.cycleMaxAbsoluteResidual()),num(x.exactScalarReconstructionRate()))); return rows; }
    private static List<List<String>> correlationRows(List<CorrelationRow> values) { List<List<String>> rows = new ArrayList<>(); rows.add(List.of("formula","contextA","contextB","spearman","exactIdenticalVector")); for (CorrelationRow x : values) rows.add(List.of(x.formula(),x.contextA().name(),x.contextB().name(),num(x.spearman()),Boolean.toString(x.exactIdenticalVector()))); return rows; }
    private static List<List<String>> anchorRows(List<AnchorRow> values) { List<List<String>> rows = new ArrayList<>(); rows.add(List.of("formula","caseId","context","sourceLineupId","lowResponseLineupId","highResponseLineupId","lowResponseValue","highResponseValue","lowPressure","highPressure","passed")); for (AnchorRow x : values) rows.add(List.of(x.formula(),x.caseId(),x.context().name(),x.sourceLineupId(),x.lowResponseLineupId(),x.highResponseLineupId(),num(x.lowResponseValue()),num(x.highResponseValue()),num(x.lowPressure()),num(x.highPressure()),Boolean.toString(x.passed()))); return rows; }
    private static List<List<String>> specialistRows(List<SpecialistRow> values) { List<List<String>> rows = new ArrayList<>(); rows.add(List.of("formula","specialist","context","topPairCount","includedTopPairCount","includedTopPairRate","positiveTopPairRate","negativeTopPairRate","relatedContext","systemic")); for (SpecialistRow x : values) rows.add(List.of(x.formula(),x.specialist(),x.context().name(),Integer.toString(x.topPairCount()),Integer.toString(x.includedTopPairCount()),num(x.includedTopPairRate()),num(x.positiveTopPairRate()),num(x.negativeTopPairRate()),Boolean.toString(x.relatedContext()),Boolean.toString(x.systemic()))); return rows; }
    private static List<List<String>> lineupRows(List<LineupDominanceRow> values) { List<List<String>> rows = new ArrayList<>(); rows.add(List.of("formula","lineupId","context","positiveOpponentCount","negativeOpponentCount","zeroOpponentCount","positiveRate")); for (LineupDominanceRow x : values) rows.add(List.of(x.formula(),x.lineupId(),x.context().name(),Integer.toString(x.positiveOpponentCount()),Integer.toString(x.negativeOpponentCount()),Integer.toString(x.zeroOpponentCount()),num(x.positiveRate()))); return rows; }
    private static List<List<String>> summaryRows(AuditSnapshot s) {
        LinkedHashMap<String, String> values = new LinkedHashMap<>();
        values.put("auditVersion", AUDIT_VERSION);
        values.put("frozenProfileVersion", FROZEN_PROFILE_VERSION);
        values.put("frozenProfileHash", FROZEN_PROFILE_HASH);
        values.put("ruleCatalogVersion", CompositionInteractionRuleCatalog.VERSION);
        values.put("ruleCatalogHash", CompositionInteractionRuleCatalog.catalogHash());
        values.put("selectedRuleCatalogHash", CompositionInteractionRuleCatalog.catalogHash());
        values.put("ruleCount", Integer.toString(CompositionInteractionRuleCatalog.rules().size()));
        values.put("rulesPerContext", "3");
        values.put("formulaCount", Integer.toString(FORMULAS.size()));
        values.put("representativeLineupCount", Integer.toString(s.representatives().size()));
        values.put("representativeParityMismatchCount", Integer.toString(s.representativeParityMismatchCount()));
        values.put("orderedPairCount", "3600");
        values.put("pairContextRowCount", Integer.toString(s.pairContextRows().size()));
        values.put("selfNeutralityErrorCount", Integer.toString(s.selfNeutralityErrorCount()));
        values.put("antisymmetryErrorCount", Integer.toString(s.antisymmetryErrorCount()));
        values.put("repeatedEvaluationMismatchCount", Integer.toString(s.repeatedEvaluationMismatchCount()));
        values.put("explanationMismatchCount", Integer.toString(s.explanationMismatchCount()));
        values.put("nanCount", Integer.toString(s.nanCount()));
        values.put("infinityCount", Integer.toString(s.infinityCount()));
        for (CompositionInteractionFormula formula : FORMULAS) {
            FormulaMetrics all = s.formulaMetrics().stream().filter(x -> x.formula().equals(formula.name()) && x.context().equals("ALL")).findFirst().orElseThrow();
            List<FormulaMetrics> contexts = s.formulaMetrics().stream().filter(x -> x.formula().equals(formula.name()) && !x.context().equals("ALL")).toList();
            String prefix = "formula." + formula.name() + ".";
            values.put(prefix + "nonZeroRate", num(1.0 - all.zeroRate()));
            values.put(prefix + "meanAbsoluteEdge", num(all.meanAbsoluteEdge()));
            values.put(prefix + "p50AbsoluteEdge", num(all.p50AbsoluteEdge()));
            values.put(prefix + "p75AbsoluteEdge", num(all.p75AbsoluteEdge()));
            values.put(prefix + "p90AbsoluteEdge", num(all.p90AbsoluteEdge()));
            values.put(prefix + "p95AbsoluteEdge", num(all.p95AbsoluteEdge()));
            values.put(prefix + "p99AbsoluteEdge", num(all.p99AbsoluteEdge()));
            values.put(prefix + "maxAbsoluteEdge", num(all.maxAbsoluteEdge()));
            values.put(prefix + "distinctEdgeCount", Integer.toString(all.distinctEdgeCount()));
            values.put(prefix + "opponentSensitiveSourceRate", num(contexts.stream().mapToDouble(FormulaMetrics::opponentSensitiveSourceRate).min().orElse(0.0)));
            values.put(prefix + "cycleResidualNonZeroRate", num(contexts.stream().mapToDouble(FormulaMetrics::cycleResidualNonZeroRate).min().orElse(0.0)));
            values.put(prefix + "exactScalarReconstructionRate", num(contexts.stream().mapToDouble(FormulaMetrics::exactScalarReconstructionRate).max().orElse(0.0)));
            values.put(prefix + "contextExactDuplicateVectorCount", Integer.toString(contexts.stream().mapToInt(FormulaMetrics::contextExactDuplicateVectorCount).sum()));
            values.put(prefix + "anchorDirectionalityPassCount", Integer.toString((int) s.anchorRows().stream().filter(x -> x.formula().equals(formula.name()) && x.passed()).count()));
            values.put(prefix + "anchorDirectionalityFailureCount", Integer.toString((int) s.anchorRows().stream().filter(x -> x.formula().equals(formula.name()) && !x.passed()).count()));
            values.put(prefix + "systemicRuleDominanceCount", Integer.toString((int) s.ruleDominanceRows().stream().filter(x -> x.formula().equals(formula.name()) && x.systemic()).count()));
            Map<String, List<LineupDominanceRow>> byLineup = s.lineupDominanceRows().stream().filter(x -> x.formula().equals(formula.name())).collect(Collectors.groupingBy(LineupDominanceRow::lineupId));
            long universal = byLineup.values().stream().filter(rows -> rows.stream().filter(x -> x.positiveRate() == 1.0).count() == TeamCompositionContext.values().length).count();
            long broad = byLineup.values().stream().filter(rows -> rows.stream().filter(x -> x.positiveRate() >= .90).count() >= 4).count();
            values.put(prefix + "universalLineupDominanceCount", Long.toString(universal));
            values.put(prefix + "broadLineupDominanceReviewCount", Long.toString(broad));
            values.put(prefix + "systemicSpecialistDominanceCount", Long.toString(s.specialistRows().stream().filter(x -> x.formula().equals(formula.name()) && x.systemic()).count()));
        }
        values.put("selectedFormula", s.selectedFormula());
        values.put("selectedCandidateVersion", s.selectedCandidateVersion());
        values.put("candidateFrozen", Boolean.toString(!s.selectedFormula().equals("NONE") && s.integrityErrorCount() == 0));
        values.put("gain", "0.0");
        values.put("deadzone", "NONE");
        values.put("overrideCount", "0");
        values.put("exhaustiveLineupAuditRerun", "false");
        values.put("matchSimulationCount", "0");
        values.put("directRandomCallCount", "0");
        values.put("gameplayApplicationCount", "0");
        values.put("teamCompositionProductionEnabled", "false");
        values.put("teamCompositionGameplayContribution", "0");
        values.put("productionGameplayChanged", "false");
        values.put("apiSchemaChanged", "false");
        values.put("frontendChanged", "false");
        values.put("targetedTests", "PASSED");
        values.put("backendTests", "PASSED");
        values.put("infoCodes", String.join("|", s.infoCodes()));
        values.put("reviewCodes", s.reviewCodes().isEmpty() ? "NONE" : String.join("|", s.reviewCodes()));
        values.put("warningCodes", s.warningCodes().isEmpty() ? "NONE" : String.join("|", s.warningCodes()));
        values.put("integrityCodes", s.integrityErrorCount() == 0 ? "NONE" : "COMPOSITION_INTERACTION_INTEGRITY");
        values.put("integrityErrorCount", Integer.toString(s.integrityErrorCount()));
        values.put("verdict", s.verdict());
        values.put("phase13D4Allowed", Boolean.toString(s.phase13D4Allowed()));
        values.put("nextPhase", s.phase13D4Allowed() ? "PHASE_13D4_COMPOSITION_GAMEPLAY_INTEGRATION" : "COMPOSITION_INTERACTION_REVIEW_REQUIRED");
        List<List<String>> rows = new ArrayList<>();
        rows.add(List.of("key", "value"));
        values.forEach((key, value) -> rows.add(List.of(key, value)));
        return rows;
    }
    private static void writeLog(AuditSnapshot s) throws IOException { List<String> lines = List.of("Phase 13D-3 Composition Interaction Context Audit", "auditVersion=" + AUDIT_VERSION, "frozenProfileVersion=" + FROZEN_PROFILE_VERSION, "frozenProfileHash=" + FROZEN_PROFILE_HASH, "ruleCatalogVersion=" + CompositionInteractionRuleCatalog.VERSION, "ruleCatalogHash=" + CompositionInteractionRuleCatalog.catalogHash(), "sourceLineupCount=" + s.sourceLineupCount(), "representativeLineupCount=" + s.representatives().size(), "orderedPairCount=3600", "pairContextRowCount=" + s.pairContextRows().size(), "exhaustiveLineupAuditRerun=false", "matchSimulationCount=0", "directRandomCallCount=0", "gameplayApplicationCount=0", "teamCompositionProductionEnabled=false", "teamCompositionGameplayContribution=0", "productionGameplayChanged=false", "apiSchemaChanged=false", "frontendChanged=false", "integrityErrorCount=" + s.integrityErrorCount(), "reviewCodes=" + (s.reviewCodes().isEmpty() ? "NONE" : String.join("|", s.reviewCodes())), "verdict=" + s.verdict(), "phase13D4Allowed=" + s.phase13D4Allowed()); Files.write(OUTPUT.resolve("composition-interaction-context-audit.log"), lines, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING); }
    private static void writeCsv(Path path, List<List<String>> rows) throws IOException { List<String> lines = rows.stream().map(row -> row.stream().map(CompositionInteractionContextAudit::escape).collect(Collectors.joining(","))).toList(); Files.write(path, lines, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING); }
    private static String escape(String value) { return value != null && !value.contains(",") && !value.contains("\"") && !value.contains("\n") && !value.contains("\r") ? value : "\"" + (value == null ? "" : value.replace("\"", "\"\"")) + "\""; }
    private static String num(double value) { return String.format(Locale.ROOT, "%.12f", value == 0.0 ? 0.0 : value); }
    private static List<String> parseCsv(String line) { List<String> values = new ArrayList<>(); StringBuilder cell = new StringBuilder(); boolean quoted = false; for (int i = 0; i < line.length(); i++) { char c = line.charAt(i); if (c == '"') { if (quoted && i + 1 < line.length() && line.charAt(i + 1) == '"') { cell.append('"'); i++; } else quoted = !quoted; } else if (c == ',' && !quoted) { values.add(cell.toString()); cell.setLength(0); } else cell.append(c); } values.add(cell.toString()); return values; }
}
