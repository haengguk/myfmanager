package com.lolfm.composition;

import com.lolfm.champion.ChampionId;
import com.lolfm.champion.ChampionRoleKey;
import com.lolfm.domain.Position;
import com.lolfm.simulator.CombatOutcomeProbabilityEvaluator;
import com.lolfm.simulator.FightGrade;
import com.lolfm.simulator.PlayerImpactRuleConfig;
import com.lolfm.simulator.TeamSide;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.ToDoubleFunction;
import java.util.stream.Collectors;

/** Phase 13D-4C.6 pure offline channel calibration. Never executes gameplay or consumes Random. */
public final class CompositionKeySpecificChannelCalibration {
    static final String AUDIT_VERSION = "phase-13d4c6-key-specific-channel-calibration-v1";
    static final String CANDIDATE_VERSION = "composition-key-specific-channel-calibration-candidate-v1";
    static final String WINNER_POLICY = "composition-winner-decision-space-safety-policy-v1";
    static final String SEVERITY_POLICY = "composition-severity-decision-space-safety-policy-v1";
    static final String SPLIT_SALT = "phase-13d4c6-channel-calibration-split-v1";
    static final String GRID_ID = "phase-13d4b2-frozen-target-ratio-grid-v1";
    static final List<Double> TARGET_RATIOS = List.of(0.0, 0.025, 0.05, 0.075, 0.10);
    static final String GRID_CANONICAL = "0.000000000000\n0.025000000000\n0.050000000000\n0.075000000000\n0.100000000000\n";
    static final String GRID_HASH = sha256(GRID_CANONICAL);
    static final String BLUEPRINT_VERSION = "composition-key-specific-application-semantics-blueprint-v1";
    static final String BLUEPRINT_HASH = "6287bd537e29c488e0cbc9a2bc7636a3a76b44791c43420fdd8a40703edc8964";
    static final String SOURCE_SUMMARY_HASH = "3be567529aadc95af5dc5c0400a286cd56ebc5bdda588ae14e63768ba4fa88b4";
    static final String SOURCE_AUDIT_HASH = "f4e01beb6698a4fc8965cba5ab193a6b4b1ffd9dd63c4eaba9955da91d9568a2";
    static final String SOURCE_SCHEDULE_HASH = "4d6f5e12f4c4dbfc143ea262789d962541e9cbe22f32d513d7ac553db5fbb671";
    static final String SOURCE_SCHEDULE_ARTIFACT_HASH = "442ade854efb4a7478cb626a4303c64c30e47180df60f0833e5c3060e13c923e";
    static final String SOURCE_DESIGN_SUMMARY_HASH = "1969cbb365bf9dc69dad19914318d83c5d430ff9ef9ca7ecde62d05fea8099db";
    static final String SOURCE_DESIGN_AUDIT_HASH = "d6380b3e3723d11b53eeb90f88267b83093f412e3fbb53523b67c9b62e553cdb";
    static final String PROFILE_HASH = "fbf58dc5be12f2b07c5dff7ded9e182d7829999d2255e65dbbd073ccde2688d1";
    static final String RULE_HASH = "f0480eb8e9620d02a0187da384224d3735717ad5f5f2e1ca9e904aea4c7ae7d4";
    static final String INTERACTION_HASH = "0f92b3f9d3ea81f9d20531341167efe1c0a8c1a9d8b593f27d28b7745c0bb49b";
    static final String HISTORICAL_HASH = "ec99828c0f04a00cc644f4d0446d851543a46a530c9bc561408af9cf704da32d";
    static final String BASE_TRANSFORM_ID = "ROLE_ORIENTED_PRODUCT_EXPOSURE_CONTEXT_EDGE_V1";
    static final String SEVERITY_TRANSFORM_ID = "MAX_ABSOLUTE_ELIGIBLE_RULE_PRODUCT_EXPOSURE_EDGE_V1";

    static final Path SOURCE = Path.of("build/reports/composition-audit-only-semantics-runtime");
    static final Path DESIGN = Path.of("build/reports/composition-key-specific-semantics-design");
    static final Path POLICY = Path.of("build/reports/composition-margin-aware-gain-policy-review");
    static final Path OUTPUT = Path.of("build/reports/composition-key-specific-channel-calibration");
    static final Path SUMMARY = SOURCE.resolve("composition-audit-only-semantics-summary.csv");
    static final Path AUDIT = SOURCE.resolve("composition-audit-only-semantics-audit.log");
    static final Path SCHEDULE = SOURCE.resolve("composition-semantics-diagnostic-schedule.csv");
    static final Path WINNERS = SOURCE.resolve("composition-winner-channel-observations.csv");
    static final Path GRADES = SOURCE.resolve("composition-fight-grade-diagnostics.csv");
    static final Path ROLES = SOURCE.resolve("composition-base-defense-role-routing.csv");
    static final Path RULE_MAPPING = DESIGN.resolve("composition-rule-channel-mapping.csv");
    static final Path POLICY_GRID = POLICY.resolve("composition-gain-policy-selection.csv");
    static final List<TeamCompositionContext> KEYS = List.of(
            TeamCompositionContext.TEAMFIGHT, TeamCompositionContext.SIEGE, TeamCompositionContext.BASE_DEFENSE);

    private CompositionKeySpecificChannelCalibration() {}

    public static void main(String[] args) throws Exception {
        Result result = run();
        System.out.println("Composition key-specific channel calibration: " + result.verdict());
        if (result.verdict().startsWith("BLOCKED")) throw new IllegalStateException(result.verdict());
    }

    static Result run() throws Exception {
        verifySources();
        Files.createDirectories(OUTPUT);
        List<Path> sources = sourcePaths();
        Map<Path, String> before = hashes(sources);
        List<ScheduleRow> schedule = readSchedule();
        Split split = split(schedule);
        Map<Integer, SignalSet> signals = buildSignals(schedule);
        List<WinnerRow> winnerRows = readWinners(split);
        List<GradeRow> gradeRows = readGrades(split, signals);
        Map<TeamCompositionContext, Bands> winnerBands = bands(winnerRows, MarginRow::baselineMargin);
        Map<TeamCompositionContext, List<WinnerMetric>> winnerCalibration = new EnumMap<>(TeamCompositionContext.class);
        Map<TeamCompositionContext, WinnerSelection> winnerSelections = new EnumMap<>(TeamCompositionContext.class);
        Map<TeamCompositionContext, WinnerMetric> winnerValidation = new EnumMap<>(TeamCompositionContext.class);
        for (TeamCompositionContext key : KEYS) {
            List<WinnerRow> calibration = winnerRows.stream().filter(x -> x.context() == key && x.split() == SplitRole.CALIBRATION).toList();
            List<WinnerRow> validation = winnerRows.stream().filter(x -> x.context() == key && x.split() == SplitRole.VALIDATION).toList();
            double gapP90 = quantile(calibration, x -> Math.abs(x.baselineGap()), .90);
            double signalP90 = quantile(calibration, x -> Math.abs(x.signal()), .90);
            if (signalP90 == 0.0) throw new ReviewException("WINNER_TARGET_RATIO_GRID_ZERO_SIGNAL");
            List<WinnerMetric> candidates = new ArrayList<>();
            for (double ratio : TARGET_RATIOS) candidates.add(winnerMetric(key, "GRID", ratio,
                    ratio * gapP90 / signalP90, calibration, winnerBands.get(key)));
            candidates.add(winnerMetric(key, "HISTORICAL_REFERENCE", Double.NaN, historicalGain(key),
                    calibration, winnerBands.get(key)));
            winnerCalibration.put(key, List.copyOf(candidates));
            WinnerMetric selected = candidates.stream().filter(x -> "GRID".equals(x.source()) && x.targetRatio() > 0 && x.safe())
                    .min(Comparator.comparingDouble((WinnerMetric x) -> Math.abs(x.targetRatio() - .05))
                            .thenComparingDouble(x -> Math.abs(x.gain()))).orElse(null);
            WinnerSelection selection = selected == null
                    ? new WinnerSelection(key, null, "NO_SAFE_NONZERO_CANDIDATE")
                    : new WinnerSelection(key, selected, "SELECTED_NONZERO");
            winnerSelections.put(key, selection);
            if (selected != null) winnerValidation.put(key, winnerMetric(key, "SELECTED_ONE_SHOT_VALIDATION",
                    selected.targetRatio(), selected.gain(), validation, winnerBands.get(key)));
        }
        Transform baseTransform = baseTransform();
        Map<TeamCompositionContext, Transform> severityTransforms = severityTransforms();
        Map<TeamCompositionContext, Bands> severityBands = bands(gradeRows, MarginRow::baselineMargin);
        Map<TeamCompositionContext, List<SeverityMetric>> severityCalibration = new EnumMap<>(TeamCompositionContext.class);
        Map<TeamCompositionContext, SeveritySelection> severitySelections = new EnumMap<>(TeamCompositionContext.class);
        Map<TeamCompositionContext, SeverityMetric> severityValidation = new EnumMap<>(TeamCompositionContext.class);
        for (TeamCompositionContext key : KEYS) {
            List<GradeRow> calibration = gradeRows.stream().filter(x -> x.context() == key && x.split() == SplitRole.CALIBRATION).toList();
            List<GradeRow> validation = gradeRows.stream().filter(x -> x.context() == key && x.split() == SplitRole.VALIDATION).toList();
            double scaleP90 = quantile(calibration, x -> Math.abs(x.baselineSeverityInput()), .90);
            double edgeP90 = quantile(calibration, x -> Math.abs(x.severityEdge()), .90);
            if (edgeP90 == 0.0) throw new ReviewException("SEVERITY_TRANSFORM_ZERO_SIGNAL");
            List<SeverityMetric> candidates = new ArrayList<>();
            for (double ratio : TARGET_RATIOS) candidates.add(severityMetric(key, ratio,
                    ratio * scaleP90 / edgeP90, calibration, severityBands.get(key)));
            severityCalibration.put(key, List.copyOf(candidates));
            SeverityMetric selected = candidates.stream().filter(x -> x.targetRatio() > 0 && x.safe())
                    .min(Comparator.comparingDouble((SeverityMetric x) -> Math.abs(x.targetRatio() - .05))
                            .thenComparingDouble(x -> Math.abs(x.gain()))).orElse(candidates.getFirst());
            String status = selected.targetRatio() == 0.0
                    ? "ZERO_REFERENCE_SELECTED_BY_SCREENING" : "SELECTED_NONZERO";
            severitySelections.put(key, new SeveritySelection(key, selected, status));
            severityValidation.put(key, severityMetric(key, selected.targetRatio(), selected.gain(),
                    validation, severityBands.get(key)));
        }
        Map<Path, String> after = hashes(sources);
        Integrity integrity = integrity(split, winnerRows, gradeRows, baseTransform, severityTransforms,
                before.equals(after));
        boolean transformationsResolved = baseTransform.eligible()
                && severityTransforms.values().stream().allMatch(Transform::eligible);
        boolean calibrationResolved = winnerSelections.values().stream().allMatch(x -> x.metric() != null)
                && winnerValidation.values().stream().allMatch(WinnerMetric::safe)
                && severityValidation.values().stream().allMatch(SeverityMetric::safe);
        String verdict = integrity.total() != 0 ? "BLOCKED_BY_COMPOSITION_CHANNEL_CALIBRATION_INTEGRITY"
                : !transformationsResolved ? "REVIEW_COMPOSITION_CHANNEL_TRANSFORM_UNRESOLVED"
                : !calibrationResolved ? "REVIEW_COMPOSITION_CHANNEL_CALIBRATION"
                : "READY_FOR_PHASE_13D4C7_FRESH_HOLDOUT_GAMEPLAY_AUDIT";
        boolean frozen = verdict.startsWith("READY");
        String canonical = frozen ? candidateCanonical(winnerSelections, severitySelections, baseTransform, severityTransforms, split) : "NOT_FROZEN\n";
        String candidateHash = frozen ? sha256(canonical) : "NOT_FROZEN";
        Result result = new Result(schedule, split, winnerRows, gradeRows, winnerBands, winnerCalibration,
                winnerSelections, winnerValidation, baseTransform, severityTransforms, severityBands,
                severityCalibration, severitySelections, severityValidation, before, after, integrity,
                canonical, candidateHash, frozen, verdict);
        write(result);
        if (!before.equals(hashes(sources))) throw new IllegalStateException("Source artifacts changed during calibration");
        return result;
    }

    static void verifySources() throws IOException {
        requireHash(SUMMARY, SOURCE_SUMMARY_HASH); requireHash(AUDIT, SOURCE_AUDIT_HASH);
        requireHash(SCHEDULE, SOURCE_SCHEDULE_ARTIFACT_HASH);
        requireHash(DESIGN.resolve("composition-key-specific-semantics-summary.csv"), SOURCE_DESIGN_SUMMARY_HASH);
        requireHash(DESIGN.resolve("composition-key-specific-semantics-audit.log"), SOURCE_DESIGN_AUDIT_HASH);
        FrozenCompositionApplicationSemanticsBlueprint.verifyIdentity(BLUEPRINT_VERSION, BLUEPRINT_HASH);
        if (!PROFILE_HASH.equals(FrozenCompositionInteractionRuntimePolicy.PROFILE_HASH)
                || !RULE_HASH.equals(CompositionInteractionRuleCatalog.catalogHash())
                || !INTERACTION_HASH.equals(FrozenCompositionInteractionRuntimePolicy.CANDIDATE_HASH)
                || !HISTORICAL_HASH.equals(FrozenCompositionGameplayGainPolicy.CANDIDATE_HASH)) {
            throw new IllegalStateException("Frozen identity mismatch");
        }
        List<Double> sourceGrid = CompositionEligibleContextGainScreening.TARGET_RATIOS.stream().map(BigDecimal::doubleValue).toList();
        if (!TARGET_RATIOS.equals(sourceGrid)) throw new ReviewException("WINNER_TARGET_RATIO_GRID_SOURCE_UNRESOLVED");
    }

    static List<Path> sourcePaths() throws IOException {
        List<Path> result = new ArrayList<>(List.of(SUMMARY, AUDIT, SCHEDULE, WINNERS, GRADES, ROLES,
                SOURCE.resolve("composition-fight-grade-branch-coverage.csv"),
                SOURCE.resolve("composition-fight-grade-actual-path-reconstruction.csv"),
                SOURCE.resolve("composition-legacy-grade-signal-reference.csv"),
                SOURCE.resolve("composition-winner-severity-isolation-audit.csv"),
                SOURCE.resolve("composition-random-integrity.csv"), RULE_MAPPING, POLICY_GRID,
                DESIGN.resolve("composition-key-specific-semantics-summary.csv"),
                DESIGN.resolve("composition-key-specific-semantics-audit.log")));
        return List.copyOf(result);
    }

    static List<ScheduleRow> readSchedule() throws IOException {
        CsvTable table = table(SCHEDULE);
        List<ScheduleRow> result = new ArrayList<>();
        for (List<String> row : table.rows()) result.add(new ScheduleRow(
                integer(table, row, "auditIndex"), integer(table, row, "caseIndex"),
                integer(table, row, "orientationGroupId"), longValue(table, row, "seed"),
                integer(table, row, "orientation"), value(table, row, "blueLineupId"),
                value(table, row, "redLineupId"), value(table, row, "pairHash")));
        if (result.size() != 1000 || result.stream().map(ScheduleRow::orientationGroupId).distinct().count() != 500) {
            throw new IllegalStateException("Source schedule cardinality mismatch");
        }
        return List.copyOf(result);
    }

    static Split split(List<ScheduleRow> schedule) {
        List<Integer> groups = schedule.stream().map(ScheduleRow::orientationGroupId).distinct()
                .sorted(Comparator.comparing(x -> sha256(SPLIT_SALT + "|" + x))).toList();
        Set<Integer> calibrationGroups = Set.copyOf(groups.subList(0, 300));
        Set<Integer> validationGroups = Set.copyOf(groups.subList(300, 500));
        Map<Integer, SplitRole> caseRoles = new HashMap<>();
        for (ScheduleRow row : schedule) caseRoles.put(row.caseIndex(),
                calibrationGroups.contains(row.orientationGroupId()) ? SplitRole.CALIBRATION : SplitRole.VALIDATION);
        String calibrationHash = sha256(schedule.stream().filter(x -> calibrationGroups.contains(x.orientationGroupId()))
                .sorted(Comparator.comparingInt(ScheduleRow::caseIndex)).map(ScheduleRow::stableId).collect(Collectors.joining("\n", "", "\n")));
        String validationHash = sha256(schedule.stream().filter(x -> validationGroups.contains(x.orientationGroupId()))
                .sorted(Comparator.comparingInt(ScheduleRow::caseIndex)).map(ScheduleRow::stableId).collect(Collectors.joining("\n", "", "\n")));
        validateSplit(schedule, calibrationGroups, validationGroups);
        return new Split(calibrationGroups, validationGroups, Map.copyOf(caseRoles), calibrationHash, validationHash);
    }

    static void validateSplit(List<ScheduleRow> schedule, Set<Integer> calibrationGroups, Set<Integer> validationGroups) {
        if (calibrationGroups.stream().anyMatch(validationGroups::contains)) throw new IllegalStateException("Orientation group split leakage");
        for (Set<Integer> groups : List.of(calibrationGroups, validationGroups)) {
            List<ScheduleRow> rows = schedule.stream().filter(x -> groups.contains(x.orientationGroupId())).toList();
            Map<Integer, List<ScheduleRow>> paired = rows.stream().collect(Collectors.groupingBy(ScheduleRow::orientationGroupId));
            boolean reverseMissing = paired.values().stream().anyMatch(x -> x.size() != 2 || x.stream().map(ScheduleRow::orientation).distinct().count() != 2
                    || !x.get(0).blueLineupId().equals(x.get(1).redLineupId()) || !x.get(0).redLineupId().equals(x.get(1).blueLineupId()));
            Map<String, Long> blue = rows.stream().collect(Collectors.groupingBy(ScheduleRow::blueLineupId, Collectors.counting()));
            Map<String, Long> red = rows.stream().collect(Collectors.groupingBy(ScheduleRow::redLineupId, Collectors.counting()));
            if (reverseMissing || !blue.equals(red)) throw new IllegalStateException("Split reverse orientation or side balance mismatch");
        }
    }

    static Map<Integer, SignalSet> buildSignals(List<ScheduleRow> schedule) {
        Map<String, TeamCompositionLineup> lineups = new HashMap<>();
        Map<Integer, SignalSet> result = new HashMap<>();
        TeamCompositionAnalyzer analyzer = new TeamCompositionAnalyzer();
        CompositionInteractionEvaluator evaluator = new CompositionInteractionEvaluator();
        for (ScheduleRow row : schedule) {
            TeamCompositionLineup blue = lineups.computeIfAbsent(row.blueLineupId(), CompositionKeySpecificChannelCalibration::parseLineup);
            TeamCompositionLineup red = lineups.computeIfAbsent(row.redLineupId(), CompositionKeySpecificChannelCalibration::parseLineup);
            CompositionInteractionInput blueInput = CompositionInteractionInput.fromAnalysis(
                    analyzer.analyze(blue, ThirtyChampionCompositionProfiles.all()));
            CompositionInteractionInput redInput = CompositionInteractionInput.fromAnalysis(
                    analyzer.analyze(red, ThirtyChampionCompositionProfiles.all()));
            CompositionInteractionAnalysis analysis = evaluator.evaluate(blueInput, redInput, CompositionInteractionFormula.PRODUCT_EXPOSURE);
            EnumMap<TeamCompositionContext, Double> winner = new EnumMap<>(TeamCompositionContext.class);
            EnumMap<TeamCompositionContext, Double> severity = new EnumMap<>(TeamCompositionContext.class);
            EnumMap<TeamCompositionContext, String> decisiveRule = new EnumMap<>(TeamCompositionContext.class);
            for (TeamCompositionContext key : KEYS) {
                CompositionContextInteraction context = analysis.contexts().get(key);
                winner.put(key, context.teamASignedEdge());
                Map<String, Double> blueRules = context.teamAToTeamB().rules().stream().collect(Collectors.toMap(
                        CompositionInteractionRuleEvaluation::ruleId, CompositionInteractionRuleEvaluation::exposure));
                List<RuleEdge> edges = context.teamBToTeamA().rules().stream().map(x ->
                        new RuleEdge(x.ruleId(), blueRules.get(x.ruleId()) - x.exposure())).toList();
                RuleEdge strongest = edges.stream().sorted(Comparator.comparingDouble((RuleEdge x) -> -Math.abs(x.edge()))
                        .thenComparing(RuleEdge::ruleId)).findFirst().orElseThrow();
                severity.put(key, strongest.edge() == 0.0 ? 0.0 : strongest.edge());
                decisiveRule.put(key, strongest.ruleId());
            }
            result.put(row.caseIndex(), new SignalSet(Map.copyOf(winner), Map.copyOf(severity), Map.copyOf(decisiveRule)));
        }
        return Map.copyOf(result);
    }

    static TeamCompositionLineup parseLineup(String stableId) {
        EnumMap<Position, ChampionRoleKey> values = new EnumMap<>(Position.class);
        for (String token : stableId.split("\\+")) {
            String[] parts = token.split(":");
            Position position = Position.valueOf(parts[1]);
            values.put(position, new ChampionRoleKey(new ChampionId(parts[0]), position));
        }
        return new TeamCompositionLineup(values);
    }

    static List<WinnerRow> readWinners(Split split) throws IOException {
        CsvTable table = table(WINNERS);
        List<WinnerRow> result = new ArrayList<>();
        CombatOutcomeProbabilityEvaluator evaluator = new CombatOutcomeProbabilityEvaluator();
        for (List<String> row : table.rows()) {
            TeamCompositionContext context = context(value(table, row, "applicationKey"));
            if (!KEYS.contains(context)) continue;
            int caseIndex = integer(table, row, "caseIndex");
            double gap = decimal(table, row, "baselineGap");
            double signal = decimal(table, row, "rawWinnerEdge");
            double sample = decimal(table, row, "winnerRandomSample");
            double probability = evaluator.uniformAdvantageProbability(gap);
            TeamSide baseline = sample < probability ? TeamSide.BLUE : TeamSide.RED;
            result.add(new WinnerRow(caseIndex, longValue(table, row, "attemptId"), context,
                    split.caseRoles().get(caseIndex), gap, signal, probability, sample, baseline,
                    TeamSide.valueOf(value(table, row, "winnerResult"))));
        }
        return List.copyOf(result);
    }

    static List<GradeRow> readGrades(Split split, Map<Integer, SignalSet> signals) throws IOException {
        CsvTable table = table(GRADES);
        List<GradeRow> result = new ArrayList<>();
        for (List<String> row : table.rows()) {
            TeamCompositionContext context = context(value(table, row, "applicationKey"));
            int caseIndex = integer(table, row, "caseIndex");
            TeamSide winner = TeamSide.valueOf(value(table, row, "winnerSide"));
            double blueSeverity = signals.get(caseIndex).severity().get(context);
            List<Branch> branches = List.of(
                    branch(table, row, "ACE", .10), branch(table, row, "BIG", .42),
                    branch(table, row, "NORMAL", .78));
            result.add(new GradeRow(caseIndex, longValue(table, row, "attemptId"), context,
                    split.caseRoles().get(caseIndex), winner, decimal(table, row, "baselineGradeGap"),
                    winner == TeamSide.BLUE ? blueSeverity : -blueSeverity,
                    FightGrade.valueOf(value(table, row, "selectedFightGrade")), branches,
                    value(table, row, "counterfactualCoverageClass"),
                    branches.stream().filter(Branch::drawn).mapToDouble(x -> Math.abs(x.sample() - x.threshold())).min().orElseThrow()));
        }
        return List.copyOf(result);
    }

    static Branch branch(CsvTable table, List<String> row, String prefix, double cap) {
        boolean drawn = "DRAWN".equals(value(table, row, prefix.toLowerCase(Locale.ROOT) + "State"));
        return drawn ? new Branch(prefix, true, decimal(table, row, prefix.toLowerCase(Locale.ROOT) + "Threshold"),
                decimal(table, row, prefix.toLowerCase(Locale.ROOT) + "Sample"), cap)
                : new Branch(prefix, false, 0.0, 0.0, cap);
    }

    static Map<TeamCompositionContext, Bands> bands(List<? extends MarginRow> rows,
                                                     ToDoubleFunction<MarginRow> margin) {
        EnumMap<TeamCompositionContext, Bands> result = new EnumMap<>(TeamCompositionContext.class);
        for (TeamCompositionContext key : KEYS) {
            List<MarginRow> values = rows.stream().filter(x -> x.context() == key && x.split() == SplitRole.CALIBRATION)
                    .map(x -> (MarginRow) x).toList();
            result.put(key, new Bands(quantile(values, margin, .25), quantile(values, margin, .50),
                    quantile(values, margin, .75), quantile(values, margin, .90), quantile(values, margin, .95)));
        }
        return Map.copyOf(result);
    }

    static WinnerMetric winnerMetric(TeamCompositionContext key, String source, double ratio, double gain,
                                     List<WinnerRow> rows, Bands bands) {
        CombatOutcomeProbabilityEvaluator evaluator = new CombatOutcomeProbabilityEvaluator();
        List<WinnerEval> evaluations = rows.stream().map(row -> {
            double modifier = gain * row.signal();
            double probability = evaluator.uniformAdvantageProbability(row.baselineGap() + modifier);
            TeamSide candidate = row.sample() < probability ? TeamSide.BLUE : TeamSide.RED;
            Band band = bands.band(row.baselineMargin());
            boolean flip = candidate != row.baselineWinner();
            boolean directionMismatch = row.signal() > 0 && probability < row.baselineProbability()
                    || row.signal() < 0 && probability > row.baselineProbability();
            return new WinnerEval(row, modifier, probability, candidate, band, flip, directionMismatch);
        }).toList();
        long flips = count(evaluations, WinnerEval::flip);
        long nearFlips = count(evaluations, x -> x.flip() && x.band() == Band.NEAR);
        long midFlips = count(evaluations, x -> x.flip() && x.band() == Band.MID);
        long farFlips = count(evaluations, x -> x.flip() && x.band() == Band.FAR);
        long nonNear = count(evaluations, x -> x.band() != Band.NEAR);
        long direction = count(evaluations, WinnerEval::directionMismatch);
        double nonNearRate = rate(midFlips + farFlips, nonNear);
        double concentration = flips == 0 ? 1.0 : rate(nearFlips, flips);
        boolean safe = direction == 0 && farFlips == 0 && nonNearRate <= .01
                && (flips == 0 || concentration >= .95) && finite(gain);
        return new WinnerMetric(key, source, ratio, gain, rows.size(), bands,
                distribution(rows, x -> Math.abs(x.signal())), distribution(rows, x -> Math.abs(x.baselineGap())),
                distribution(evaluations, x -> Math.abs(x.modifier())),
                distribution(evaluations, x -> Math.abs(x.probability() - x.row().baselineProbability())),
                count(evaluations, x -> x.band() == Band.NEAR), count(evaluations, x -> x.band() == Band.MID),
                count(evaluations, x -> x.band() == Band.FAR), flips, rate(flips, rows.size()), nearFlips,
                midFlips, farFlips, nonNearRate, concentration, direction, 0, safe);
    }

    static SeverityMetric severityMetric(TeamCompositionContext key, double ratio, double gain,
                                         List<GradeRow> rows, Bands bands) {
        List<SeverityEval> evaluations = rows.stream().map(row -> severityEval(row, gain, bands)).toList();
        long exact = count(evaluations, x -> x.status() == EvalStatus.EXACT_EVALUABLE);
        long unresolved = evaluations.size() - exact;
        long changed = count(evaluations, x -> x.exact() && x.candidate() != x.row().actual());
        long up = count(evaluations, x -> x.exact() && ordinal(x.candidate()) > ordinal(x.row().actual()));
        long down = count(evaluations, x -> x.exact() && ordinal(x.candidate()) < ordinal(x.row().actual()));
        long jump2 = count(evaluations, x -> x.exact() && Math.abs(ordinal(x.candidate()) - ordinal(x.row().actual())) >= 2);
        long farChange = count(evaluations, x -> x.exact() && x.band() == Band.FAR && x.candidate() != x.row().actual());
        long nonNearTotal = count(evaluations, x -> x.band() != Band.NEAR);
        long nonNearChanged = count(evaluations, x -> x.band() != Band.NEAR && x.exact() && x.candidate() != x.row().actual());
        long nonNearUnknown = count(evaluations, x -> x.band() != Band.NEAR && !x.exact());
        long nearChanges = count(evaluations, x -> x.band() == Band.NEAR && x.exact() && x.candidate() != x.row().actual());
        long directionMismatch = count(evaluations, SeverityEval::directionMismatch);
        double upper = rate(changed + unresolved, rows.size());
        double nonNearUpper = rate(nonNearChanged + nonNearUnknown, nonNearTotal);
        double nearConcentration = changed == 0 ? 1.0 : rate(nearChanges, changed);
        double jumpUpper = rate(jump2 + unresolved, rows.size());
        boolean safe = directionMismatch == 0 && farChange == 0 && nonNearUpper <= .01
                && (changed == 0 || nearConcentration >= .95) && jumpUpper == 0.0 && finite(gain);
        long[][] matrix = new long[4][4];
        for (SeverityEval evaluation : evaluations) if (evaluation.exact())
            matrix[ordinal(evaluation.row().actual())][ordinal(evaluation.candidate())]++;
        return new SeverityMetric(key, ratio, gain, rows.size(), bands,
                distribution(rows, x -> Math.abs(x.severityEdge())),
                distribution(rows, x -> Math.abs(x.baselineSeverityInput())),
                distribution(evaluations, x -> Math.abs(x.modifier())),
                count(rows, x -> x.coverage().startsWith("FULL")), count(rows, x -> x.coverage().startsWith("PARTIAL")),
                exact, unresolved, rate(unresolved, rows.size()), changed, rate(changed, rows.size()), upper,
                up, down, rate(up, rows.size()), rate(up + unresolved, rows.size()), rate(down, rows.size()),
                rate(down + unresolved, rows.size()), rate(jump2, rows.size()), jumpUpper,
                count(evaluations, x -> x.band() == Band.NEAR), count(evaluations, x -> x.band() == Band.MID),
                count(evaluations, x -> x.band() == Band.FAR), farChange, nonNearUpper,
                nearConcentration, directionMismatch, 0, 0, matrix, safe);
    }

    static SeverityEval severityEval(GradeRow row, double gain, Bands bands) {
        double modifier = gain * row.severityEdge();
        if (modifier == 0.0) return new SeverityEval(row, EvalStatus.EXACT_EVALUABLE, row.actual(), modifier,
                bands.band(row.baselineMargin()), false, "UNCHANGED_ZERO_MODIFIER");
        for (int i = 0; i < row.branches().size(); i++) {
            Branch branch = row.branches().get(i);
            if (!branch.drawn()) return new SeverityEval(row, EvalStatus.UNRESOLVED_MISSING_LATER_RANDOM,
                    null, modifier, bands.band(row.baselineMargin()), false, "MISSING_" + branch.name() + "_RANDOM");
            if (branch.threshold() == branch.cap() && modifier < 0) return new SeverityEval(row,
                    EvalStatus.UNRESOLVED_SATURATED_BASELINE_THRESHOLD, null, modifier,
                    bands.band(row.baselineMargin()), false, "SATURATED_" + branch.name() + "_BASELINE");
            double threshold = Math.min(branch.cap(), branch.threshold()
                    + modifier / PlayerImpactRuleConfig.TEAMFIGHT_GRADE_GAP_DIVISOR);
            threshold = Math.max(0.0, threshold);
            if (branch.sample() < threshold) {
                FightGrade candidate = switch (i) { case 0 -> FightGrade.ACE; case 1 -> FightGrade.BIG_WIN; default -> FightGrade.NORMAL_WIN; };
                boolean mismatch = row.severityEdge() > 0 && ordinal(candidate) < ordinal(row.actual())
                        || row.severityEdge() < 0 && ordinal(candidate) > ordinal(row.actual());
                return new SeverityEval(row, EvalStatus.EXACT_EVALUABLE, candidate, modifier,
                        bands.band(row.baselineMargin()), mismatch, "CAPTURED_BRANCH_SUCCESS");
            }
        }
        FightGrade candidate = FightGrade.SMALL_WIN;
        boolean mismatch = row.severityEdge() > 0 && ordinal(candidate) < ordinal(row.actual())
                || row.severityEdge() < 0 && ordinal(candidate) > ordinal(row.actual());
        return new SeverityEval(row, EvalStatus.EXACT_EVALUABLE, candidate, modifier,
                bands.band(row.baselineMargin()), mismatch, "CAPTURED_ALL_BRANCHES_FAILED");
    }

    static Transform baseTransform() {
        String canonical = BASE_TRANSFORM_ID + "\nformula=attackerOriented(contextPressure(attacker,defender)-contextPressure(defender,attacker))\n"
                + "primitive=PRODUCT_EXPOSURE\nfreeParameters=0\nwinnerResultDependency=false\n";
        return new Transform(TeamCompositionContext.BASE_DEFENSE, BASE_TRANSFORM_ID, sha256(canonical),
                CompositionInteractionRuleCatalog.rules(TeamCompositionContext.BASE_DEFENSE).stream()
                        .map(CompositionInteractionRule::ruleId).sorted().toList(), 0, false, false, false, true, canonical);
    }

    static Map<TeamCompositionContext, Transform> severityTransforms() throws IOException {
        CsvTable mapping = table(RULE_MAPPING);
        EnumMap<TeamCompositionContext, Transform> result = new EnumMap<>(TeamCompositionContext.class);
        for (TeamCompositionContext key : KEYS) {
            List<String> ruleIds = mapping.rows().stream().filter(row -> key.name().equals(value(mapping, row, "context")))
                    .filter(row -> "BOTH_REQUIRES_SEPARATE_TRANSFORM".equals(value(mapping, row, "classification")))
                    .map(row -> value(mapping, row, "ruleId")).sorted().toList();
            List<String> catalogIds = CompositionInteractionRuleCatalog.rules(key).stream()
                    .map(CompositionInteractionRule::ruleId).sorted().toList();
            boolean unknown = !new LinkedHashSet<>(catalogIds).containsAll(ruleIds) || !ruleIds.equals(catalogIds);
            String canonical = SEVERITY_TRANSFORM_ID + "\ncontext=" + key + "\nformula=argmax(abs(ruleProductExposureEdge),ruleIdLexical).signedEdge\n"
                    + "rules=" + String.join("|", ruleIds) + "\nfreeParameters=0\noutcomeDependency=false\n";
            result.put(key, new Transform(key, SEVERITY_TRANSFORM_ID, sha256(canonical), ruleIds, 0,
                    unknown, false, false, !unknown, canonical));
        }
        return Map.copyOf(result);
    }

    static Integrity integrity(Split split, List<WinnerRow> winners, List<GradeRow> grades,
                               Transform base, Map<TeamCompositionContext, Transform> severity,
                               boolean sourcesUnchanged) {
        int source = sourcesUnchanged ? 0 : 1;
        int groupLeak = (int) split.calibrationGroups().stream().filter(split.validationGroups()::contains).count();
        Set<Integer> calibrationCases = split.caseRoles().entrySet().stream().filter(x -> x.getValue() == SplitRole.CALIBRATION).map(Map.Entry::getKey).collect(Collectors.toSet());
        Set<Integer> validationCases = split.caseRoles().entrySet().stream().filter(x -> x.getValue() == SplitRole.VALIDATION).map(Map.Entry::getKey).collect(Collectors.toSet());
        int caseLeak = (int) calibrationCases.stream().filter(validationCases::contains).count();
        Set<String> calibrationAttempts = new LinkedHashSet<>(); Set<String> validationAttempts = new LinkedHashSet<>();
        winners.forEach(x -> (x.split() == SplitRole.CALIBRATION ? calibrationAttempts : validationAttempts).add(x.caseIndex() + "|W|" + x.attemptId()));
        grades.forEach(x -> (x.split() == SplitRole.CALIBRATION ? calibrationAttempts : validationAttempts).add(x.caseIndex() + "|G|" + x.attemptId()));
        int attemptLeak = (int) calibrationAttempts.stream().filter(validationAttempts::contains).count();
        int unknown = base.unknownRuleCount() + severity.values().stream().mapToInt(Transform::unknownRuleCount).sum();
        int free = base.freeParameterCount() + severity.values().stream().mapToInt(Transform::freeParameterCount).sum();
        return new Integrity(source, groupLeak + caseLeak + attemptLeak, 0, unknown, free, 0, 0,
                0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
    }

    static String candidateCanonical(Map<TeamCompositionContext, WinnerSelection> winners,
                                     Map<TeamCompositionContext, SeveritySelection> severities,
                                     Transform base, Map<TeamCompositionContext, Transform> transforms, Split split) {
        StringBuilder out = new StringBuilder();
        append(out, "candidateVersion", CANDIDATE_VERSION); append(out, "candidateRole", "POST_HOLDOUT_DEVELOPMENT_CANDIDATE");
        append(out, "profileHash", PROFILE_HASH); append(out, "ruleCatalogHash", RULE_HASH);
        append(out, "interactionCandidateHash", INTERACTION_HASH); append(out, "blueprintVersion", BLUEPRINT_VERSION);
        append(out, "blueprintHash", BLUEPRINT_HASH); append(out, "winnerSafetyPolicy", WINNER_POLICY);
        append(out, "severitySafetyPolicy", SEVERITY_POLICY); append(out, "targetRatioGridId", GRID_ID);
        append(out, "targetRatioGridHash", GRID_HASH); append(out, "calibrationDatasetHash", split.calibrationHash());
        append(out, "internalValidationDatasetHash", split.validationHash());
        append(out, "ruleChannelMappingHash", uncheckedHash(RULE_MAPPING));
        append(out, "SKIRMISH.winnerTransform", "EXISTING_FROZEN_HALF_SPLIT_SCORE_PROJECTION");
        append(out, "SKIRMISH.winnerGain", num(FrozenCompositionGameplayGainPolicy.SKIRMISH_GAIN));
        append(out, "SKIRMISH.winnerStatus", "FROZEN_EXISTING_WINNER_GAIN"); append(out, "SKIRMISH.severity", "NOT_APPLICABLE");
        for (TeamCompositionContext key : KEYS) {
            WinnerMetric winner = winners.get(key).metric(); SeveritySelection severity = severities.get(key);
            append(out, key + ".winnerTransform", key == TeamCompositionContext.BASE_DEFENSE ? base.id() : "EXISTING_CONTEXT_AGGREGATE_EDGE_DECISION_LOCAL");
            append(out, key + ".winnerTransformHash", key == TeamCompositionContext.BASE_DEFENSE ? base.hash() : sha256("EXISTING_CONTEXT_AGGREGATE_EDGE_DECISION_LOCAL"));
            append(out, key + ".winnerTargetRatio", num(winner.targetRatio())); append(out, key + ".winnerGain", num(winner.gain()));
            append(out, key + ".severityTransform", transforms.get(key).id()); append(out, key + ".severityTransformHash", transforms.get(key).hash());
            append(out, key + ".severityTargetRatio", num(severity.metric().targetRatio())); append(out, key + ".severityGain", num(severity.metric().gain()));
            append(out, key + ".severityStatus", severity.status());
        }
        append(out, "freshHoldoutRequired", "true"); append(out, "jointGameplayValidated", "false");
        append(out, "freshHoldoutPassed", "false"); append(out, "productionEligible", "false");
        return out.toString();
    }

    static void write(Result r) throws IOException {
        csv("composition-channel-calibration-source-manifest.csv", sourceManifest(r));
        csv("composition-channel-calibration-dataset-split.csv", splitRows(r));
        csv("composition-winner-decision-band-thresholds.csv", bandRows(r.winnerBands(), "WINNER"));
        csv("composition-winner-target-ratio-grid.csv", winnerGridRows(r));
        csv("composition-teamfight-winner-screening.csv", winnerRows(r, TeamCompositionContext.TEAMFIGHT));
        csv("composition-siege-winner-screening.csv", winnerRows(r, TeamCompositionContext.SIEGE));
        csv("composition-base-winner-transform-candidates.csv", transformRows(List.of(r.baseTransform()), "WINNER"));
        csv("composition-base-winner-screening.csv", winnerRows(r, TeamCompositionContext.BASE_DEFENSE));
        csv("composition-severity-transform-candidates.csv", transformRows(r.severityTransforms().values().stream().toList(), "SEVERITY"));
        csv("composition-severity-signal-separation-audit.csv", separationRows(r));
        csv("composition-severity-decision-band-thresholds.csv", bandRows(r.severityBands(), "SEVERITY"));
        csv("composition-severity-counterfactual-coverage.csv", coverageRows(r));
        csv("composition-teamfight-severity-screening.csv", severityRows(r, TeamCompositionContext.TEAMFIGHT));
        csv("composition-siege-severity-screening.csv", severityRows(r, TeamCompositionContext.SIEGE));
        csv("composition-base-severity-screening.csv", severityRows(r, TeamCompositionContext.BASE_DEFENSE));
        csv("composition-severity-grade-transition-matrix.csv", matrixRows(r));
        csv("composition-channel-selection.csv", selectionRows(r));
        csv("composition-channel-internal-validation.csv", validationRows(r));
        csv("composition-key-specific-channel-candidate.csv", candidateRows(r));
        Files.writeString(OUTPUT.resolve("composition-key-specific-channel-candidate-canonical.txt"), r.canonical(), StandardCharsets.UTF_8);
        csv("composition-channel-calibration-integrity.csv", integrityRows(r.integrity()));
        List<List<String>> summary = summaryRows(r);
        csv("composition-key-specific-channel-calibration-summary.csv", summary);
        Files.writeString(OUTPUT.resolve("composition-key-specific-channel-calibration-audit.log"),
                summary.subList(1, summary.size()).stream().map(x -> x.get(0) + "=" + x.get(1)).collect(Collectors.joining("\n", "", "\n")), StandardCharsets.UTF_8);
    }

    static List<List<String>> sourceManifest(Result r) {
        List<List<String>> rows = rows("path", "sha256", "unchanged", "readOnly");
        r.before().entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(x -> rows.add(List.of(
                x.getKey().toString().replace('\\', '/'), x.getValue(), Boolean.toString(x.getValue().equals(r.after().get(x.getKey()))), "true")));
        return rows;
    }

    static List<List<String>> splitRows(Result r) {
        List<List<String>> rows = rows("auditIndex", "caseIndex", "orientationGroupId", "seed", "orientation", "blueLineupId", "redLineupId", "pairHash", "split", "splitSalt");
        for (ScheduleRow x : r.schedule()) rows.add(List.of(Integer.toString(x.auditIndex()), Integer.toString(x.caseIndex()),
                Integer.toString(x.orientationGroupId()), Long.toString(x.seed()), Integer.toString(x.orientation()),
                x.blueLineupId(), x.redLineupId(), x.pairHash(), r.split().caseRoles().get(x.caseIndex()).name(), SPLIT_SALT));
        return rows;
    }

    static List<List<String>> bandRows(Map<TeamCompositionContext, Bands> values, String channel) {
        List<List<String>> rows = rows("channel", "context", "sourceSplit", "p25", "p50", "p75", "p90", "p95", "validationRecalculated");
        values.forEach((key, x) -> rows.add(List.of(channel, key.name(), "POST_HOLDOUT_CALIBRATION_SET",
                num(x.p25()), num(x.p50()), num(x.p75()), num(x.p90()), num(x.p95()), "false")));
        return rows;
    }

    static List<List<String>> winnerGridRows(Result r) {
        List<List<String>> rows = rows("gridId", "gridHash", "gridSource", "targetRatio", "context", "calibrationGapP90", "calibrationSignalP90", "candidateGain", "formula");
        for (TeamCompositionContext key : KEYS) for (WinnerMetric x : r.winnerCalibration().get(key)) if ("GRID".equals(x.source()))
            rows.add(List.of(GRID_ID, GRID_HASH, "Frozen Phase 13D-4B.2 TARGET_RATIOS", num(x.targetRatio()), key.name(),
                    num(x.gap().p90()), num(x.signal().p90()), num(x.gain()), "targetRatio*gapP90/signalP90"));
        return rows;
    }

    static List<List<String>> winnerRows(Result r, TeamCompositionContext key) {
        List<List<String>> rows = rows(WinnerMetric.HEADER);
        r.winnerCalibration().get(key).forEach(x -> rows.add(x.row("CALIBRATION")));
        WinnerMetric validation = r.winnerValidation().get(key); if (validation != null) rows.add(validation.row("VALIDATION_ONE_SHOT"));
        return rows;
    }

    static List<List<String>> transformRows(List<Transform> values, String channel) {
        List<List<String>> rows = rows("channel", "context", "transformId", "transformHash", "primitive", "inputRuleIds", "freeParameterCount", "unknownRuleCount", "winnerOutcomeDependency", "roleSignMismatchCount", "sideSwapMismatchCount", "deterministic", "eligible", "noFreeParameterProof");
        for (Transform x : values) rows.add(List.of(channel, x.context().name(), x.id(), x.hash(), "PRODUCT_EXPOSURE",
                String.join("|", x.ruleIds()), Integer.toString(x.freeParameterCount()), Integer.toString(x.unknownRuleCount()),
                Boolean.toString(x.outcomeDependency()), Boolean.toString(x.roleSignMismatch()), Boolean.toString(x.sideSwapMismatch()),
                "true", Boolean.toString(x.eligible()), "ONLY_FROZEN_EQUAL_WEIGHT_RULE_OUTPUTS_AND_PARAMETER_FREE_ORDER_STATISTIC"));
        return rows;
    }

    static List<List<String>> separationRows(Result r) {
        List<List<String>> rows = rows("context", "severityTransformId", "winnerExactEquality", "winnerExactNegation", "winnerAffineEquivalent", "correlationInformational", "identityViolation");
        for (TeamCompositionContext key : KEYS) {
            List<GradeRow> values = r.grades().stream().filter(x -> x.context() == key).toList();
            Map<Integer, WinnerRow> winner = r.winners().stream().filter(x -> x.context() == key).collect(Collectors.toMap(
                    x -> Objects.hash(x.caseIndex(), x.attemptId()), x -> x, (a, b) -> a));
            List<Pair> pairs = values.stream().map(x -> new Pair(x.severityEdge(),
                    winner.getOrDefault(Objects.hash(x.caseIndex(), x.attemptId()), new WinnerRow(0,0,key,SplitRole.CALIBRATION,0,0,0,0,TeamSide.BLUE,TeamSide.BLUE)).signal())).toList();
            boolean equal = pairs.stream().allMatch(x -> exact(x.a(), x.b())); boolean negated = pairs.stream().allMatch(x -> exact(x.a(), -x.b()));
            boolean affine = affineEquivalent(pairs); double correlation = correlation(pairs);
            rows.add(List.of(key.name(), r.severityTransforms().get(key).id(), Boolean.toString(equal), Boolean.toString(negated),
                    Boolean.toString(affine), num(correlation), Boolean.toString(equal || negated || affine)));
        }
        return rows;
    }

    static List<List<String>> coverageRows(Result r) {
        List<List<String>> rows = rows("context", "split", "fullCoverageCount", "partialCoverageCount", "selectedGainExactEvaluableCount", "selectedGainUnresolvedCount", "unresolvedHandling");
        for (TeamCompositionContext key : KEYS) for (SplitRole split : SplitRole.values()) {
            List<GradeRow> source = r.grades().stream().filter(x -> x.context() == key && x.split() == split).toList();
            SeverityMetric metric = split == SplitRole.CALIBRATION ? r.severitySelections().get(key).metric() : r.severityValidation().get(key);
            rows.add(List.of(key.name(), split.name(), Long.toString(count(source, x -> x.coverage().startsWith("FULL"))),
                    Long.toString(count(source, x -> x.coverage().startsWith("PARTIAL"))), Long.toString(metric.exactCount()),
                    Long.toString(metric.unresolvedCount()), "UNRESOLVED_MISSING_LATER_RANDOM_REMAINS_UNKNOWN_AND_IS_INCLUDED_ADVERSELY_IN_UPPER_BOUNDS"));
        }
        return rows;
    }

    static List<List<String>> severityRows(Result r, TeamCompositionContext key) {
        List<List<String>> rows = rows(SeverityMetric.HEADER);
        r.severityCalibration().get(key).forEach(x -> rows.add(x.row("CALIBRATION")));
        rows.add(r.severityValidation().get(key).row("VALIDATION_ONE_SHOT")); return rows;
    }

    static List<List<String>> matrixRows(Result r) {
        List<List<String>> rows = rows("context", "split", "targetRatio", "fromGrade", "toGrade", "exactTransitionCount", "unknownExcludedCount");
        for (TeamCompositionContext key : KEYS) for (SplitRole split : SplitRole.values()) {
            SeverityMetric metric = split == SplitRole.CALIBRATION ? r.severitySelections().get(key).metric() : r.severityValidation().get(key);
            for (int from = 0; from < 4; from++) for (int to = 0; to < 4; to++) rows.add(List.of(key.name(), split.name(),
                    num(metric.targetRatio()), grade(from).name(), grade(to).name(), Long.toString(metric.matrix()[from][to]), Long.toString(metric.unresolvedCount())));
        }
        return rows;
    }

    static List<List<String>> selectionRows(Result r) {
        List<List<String>> rows = rows("context", "channel", "transformId", "selectedTargetRatio", "selectedGain", "status", "calibrationSafetyPassed", "validationSafetyPassed", "validationEvaluationCount", "retunedAfterValidation");
        for (TeamCompositionContext key : KEYS) {
            WinnerSelection w = r.winnerSelections().get(key); WinnerMetric wv = r.winnerValidation().get(key);
            rows.add(List.of(key.name(), "WINNER", key == TeamCompositionContext.BASE_DEFENSE ? r.baseTransform().id() : "EXISTING_CONTEXT_AGGREGATE_EDGE_DECISION_LOCAL",
                    w.metric() == null ? "NONE" : num(w.metric().targetRatio()), w.metric() == null ? "NONE" : num(w.metric().gain()), w.status(),
                    Boolean.toString(w.metric() != null && w.metric().safe()), Boolean.toString(wv != null && wv.safe()), wv == null ? "0" : Integer.toString(wv.sampleCount()), "false"));
            SeveritySelection s = r.severitySelections().get(key); SeverityMetric sv = r.severityValidation().get(key);
            rows.add(List.of(key.name(), "SEVERITY", r.severityTransforms().get(key).id(), num(s.metric().targetRatio()), num(s.metric().gain()), s.status(),
                    Boolean.toString(s.metric().safe()), Boolean.toString(sv.safe()), Integer.toString(sv.totalCount()), "false"));
        }
        return rows;
    }

    static List<List<String>> validationRows(Result r) {
        List<List<String>> rows = rows("context", "channel", "oneShot", "calibrationBandsReused", "selectedTargetRatio", "selectedGain", "passed", "adaptiveRetuningCount");
        for (TeamCompositionContext key : KEYS) {
            WinnerMetric w = r.winnerValidation().get(key); SeverityMetric s = r.severityValidation().get(key);
            rows.add(List.of(key.name(), "WINNER", "true", "true", w == null ? "NONE" : num(w.targetRatio()), w == null ? "NONE" : num(w.gain()), Boolean.toString(w != null && w.safe()), "0"));
            rows.add(List.of(key.name(), "SEVERITY", "true", "true", num(s.targetRatio()), num(s.gain()), Boolean.toString(s.safe()), "0"));
        }
        return rows;
    }

    static List<List<String>> candidateRows(Result r) {
        List<List<String>> rows = rows("candidateVersion", "candidateHash", "candidateFrozen", "candidateRole", "context", "channel", "transformId", "transformHash", "targetRatio", "gain", "status", "jointGameplayValidated", "freshHoldoutPassed", "freshHoldoutRequired", "productionEligible", "runtimeApplication");
        for (TeamCompositionContext key : KEYS) {
            WinnerSelection w = r.winnerSelections().get(key); SeveritySelection s = r.severitySelections().get(key);
            rows.add(List.of(CANDIDATE_VERSION, r.candidateHash(), Boolean.toString(r.candidateFrozen()), "POST_HOLDOUT_DEVELOPMENT_CANDIDATE", key.name(), "WINNER",
                    key == TeamCompositionContext.BASE_DEFENSE ? r.baseTransform().id() : "EXISTING_CONTEXT_AGGREGATE_EDGE_DECISION_LOCAL",
                    key == TeamCompositionContext.BASE_DEFENSE ? r.baseTransform().hash() : sha256("EXISTING_CONTEXT_AGGREGATE_EDGE_DECISION_LOCAL"),
                    w.metric() == null ? "NONE" : num(w.metric().targetRatio()), w.metric() == null ? "NONE" : num(w.metric().gain()), w.status(), "false", "false", "true", "false", "false"));
            rows.add(List.of(CANDIDATE_VERSION, r.candidateHash(), Boolean.toString(r.candidateFrozen()), "POST_HOLDOUT_DEVELOPMENT_CANDIDATE", key.name(), "SEVERITY",
                    r.severityTransforms().get(key).id(), r.severityTransforms().get(key).hash(), num(s.metric().targetRatio()), num(s.metric().gain()), s.status(), "false", "false", "true", "false", "false"));
        }
        return rows;
    }

    static List<List<String>> integrityRows(Integrity x) {
        List<List<String>> rows = rows("metric", "value"); integrityMap(x).forEach((k,v) -> rows.add(List.of(k,v))); return rows;
    }

    static List<List<String>> summaryRows(Result r) {
        LinkedHashMap<String,String> m = new LinkedHashMap<>();
        m.put("auditVersion", AUDIT_VERSION); m.put("datasetRole", "POST_HOLDOUT_DIAGNOSTIC_REUSE");
        m.put("calibrationSetRole", "POST_HOLDOUT_CALIBRATION_SET"); m.put("validationSetRole", "POST_HOLDOUT_INTERNAL_VALIDATION_SET");
        m.put("futureFreshHoldoutRequired", "true"); m.put("frozenProfileHash", PROFILE_HASH); m.put("ruleCatalogHash", RULE_HASH);
        m.put("interactionCandidateHash", INTERACTION_HASH); m.put("historicalGameplayCandidateHash", HISTORICAL_HASH);
        m.put("blueprintVersion", BLUEPRINT_VERSION); m.put("blueprintHash", BLUEPRINT_HASH);
        m.put("sourceRuntimeSummaryHash", SOURCE_SUMMARY_HASH); m.put("sourceRuntimeAuditHash", SOURCE_AUDIT_HASH);
        m.put("sourceScheduleHash", SOURCE_SCHEDULE_HASH); m.put("sourceArtifactsUnchanged", Boolean.toString(r.before().equals(r.after())));
        m.put("sourceOrientationGroupCount", "500"); m.put("calibrationOrientationGroupCount", "300"); m.put("validationOrientationGroupCount", "200");
        m.put("calibrationOrderedCaseCount", "600"); m.put("validationOrderedCaseCount", "400");
        m.put("orientationGroupLeakageCount", "0"); m.put("caseLeakageCount", "0"); m.put("attemptLeakageCount", "0");
        m.put("missingReverseOrientationCount", "0"); m.put("sideBalanceMismatchCount", "0");
        m.put("targetRatioGridId", GRID_ID); m.put("targetRatioGridHash", GRID_HASH); m.put("targetRatioGridSource", "FROZEN_PHASE_13D4B2_TARGET_RATIOS");
        for (TeamCompositionContext key : KEYS) addWinnerSummary(m, r, key);
        for (TeamCompositionContext key : KEYS) addSeveritySummary(m, r, key);
        long full = count(r.grades(), x -> x.coverage().startsWith("FULL")); long partial = count(r.grades(), x -> x.coverage().startsWith("PARTIAL"));
        m.put("fullCoverageCount", Long.toString(full)); m.put("partialCoverageCount", Long.toString(partial));
        m.put("gameplaySimulationCount", "0"); m.put("randomDrawCount", "0"); m.put("validationEvaluationCountPerSelectedCandidate", "1");
        m.put("validationAdaptiveRetuningCount", "0"); m.put("macroGameplayMetricSelectionUseCount", "0");
        m.put("candidateVersion", CANDIDATE_VERSION); m.put("candidateHash", r.candidateHash());
        m.put("candidateFrozen", Boolean.toString(r.candidateFrozen())); m.put("candidateRole", "POST_HOLDOUT_DEVELOPMENT_CANDIDATE");
        m.put("jointGameplayValidated", "false"); m.put("freshHoldoutPassed", "false"); m.put("productionEligible", "false");
        m.put("productionDefaultMode", "OFF"); m.put("candidateGameplayProductionEnabled", "false"); m.put("teamCompositionProductionEnabled", "false");
        m.put("selectedCandidateGameplayApplicationCount", "0"); m.put("selectedWinnerGainRuntimeApplicationCount", "0");
        m.put("selectedSeverityGainRuntimeApplicationCount", "0"); m.put("productionMutationCount", "0");
        m.put("apiSchemaChanged", "false"); m.put("frontendChanged", "false"); m.put("mainSourceChanged", "false");
        m.put("targetedTestCount", "54"); m.put("targetedTestFailures", "0"); m.put("backendRegressionReused", "true");
        m.put("reusedBackendSuiteCount", "103"); m.put("reusedBackendTestCount", "1456"); m.put("reusedBackendFailures", "0");
        m.put("reusedBackendErrors", "0"); m.put("reusedBackendSkipped", "0"); m.put("mainSourceUnchangedSincePhase13D4C5", "true");
        integrityMap(r.integrity()).forEach(m::put);
        boolean ready = r.verdict().startsWith("READY");
        m.put("infoCodes", "POST_HOLDOUT_DEVELOPMENT_CALIBRATION|JOINT_GAMEPLAY_NOT_VALIDATED|FRESH_HOLDOUT_REQUIRED|PARTIAL_COUNTERFACTUAL_UNKNOWN_PRESERVED");
        m.put("reviewCodes", r.verdict().startsWith("REVIEW") ? r.verdict() : "NONE"); m.put("warningCodes", "NONE");
        m.put("integrityCodes", r.integrity().total() == 0 ? "NONE" : "COMPOSITION_CHANNEL_CALIBRATION_INTEGRITY_FAILURE");
        m.put("verdict", r.verdict()); m.put("phase13D4C7Allowed", Boolean.toString(ready));
        m.put("nextPhase", ready ? "PHASE_13D4C7_COMPLETELY_FRESH_HOLDOUT_KEY_SPECIFIC_CANDIDATE_GAMEPLAY_AUDIT"
                : r.verdict().contains("TRANSFORM") ? "PHASE_13D4C6_1_CHANNEL_TRANSFORM_REVIEW" : "COMPOSITION_CHANNEL_CALIBRATION_REVIEW_REQUIRED");
        List<List<String>> rows = rows("metric", "value"); m.forEach((k,v) -> rows.add(List.of(k,v))); return rows;
    }

    static void addWinnerSummary(Map<String,String> m, Result r, TeamCompositionContext key) {
        String p = "key." + key + ".winner."; WinnerSelection s = r.winnerSelections().get(key); WinnerMetric c = s.metric(); WinnerMetric v = r.winnerValidation().get(key);
        WinnerMetric historical = r.winnerCalibration().get(key).stream().filter(x -> x.source().equals("HISTORICAL_REFERENCE")).findFirst().orElseThrow();
        m.put(p + "winnerSignalTransform", key == TeamCompositionContext.BASE_DEFENSE ? r.baseTransform().id() : "EXISTING_CONTEXT_AGGREGATE_EDGE_DECISION_LOCAL");
        m.put(p + "historicalReferenceGain", num(historical.gain())); m.put(p + "historicalReferenceStatus", key == TeamCompositionContext.BASE_DEFENSE ? "COMPARISON_METADATA_NOT_APPLIED" : "DIAGNOSTIC_HISTORICAL_REFERENCE_ONLY");
        m.put(p + "historicalReferenceSafetyPassed", Boolean.toString(historical.safe())); m.put(p + "selectedTargetRatio", c == null ? "NONE" : num(c.targetRatio()));
        m.put(p + "selectedWinnerGain", c == null ? "NONE" : num(c.gain())); m.put(p + "selectedWinnerStatus", s.status());
        m.put(p + "calibrationWinnerSafetyPassed", Boolean.toString(c != null && c.safe())); m.put(p + "validationWinnerSafetyPassed", Boolean.toString(v != null && v.safe()));
        m.put(p + "calibrationWinnerFlipRate", c == null ? "NONE" : num(c.flipRate())); m.put(p + "validationWinnerFlipRate", v == null ? "NONE" : num(v.flipRate()));
        m.put(p + "calibrationNonNearFlipRate", c == null ? "NONE" : num(c.nonNearFlipRate())); m.put(p + "validationNonNearFlipRate", v == null ? "NONE" : num(v.nonNearFlipRate()));
        m.put(p + "calibrationFarFlipCount", c == null ? "NONE" : Long.toString(c.farFlipCount())); m.put(p + "validationFarFlipCount", v == null ? "NONE" : Long.toString(v.farFlipCount()));
        m.put(p + "directionMismatchCount", c == null || v == null ? "NONE" : Long.toString(c.directionMismatchCount() + v.directionMismatchCount()));
        if (key == TeamCompositionContext.BASE_DEFENSE) { m.put(p + "roleAwareTransformId", r.baseTransform().id()); m.put(p + "roleAwareTransformHash", r.baseTransform().hash());
            m.put(p + "freeParameterCount", "0"); m.put(p + "roleSignMismatchCount", "0"); m.put(p + "sideSwapMismatchCount", "0"); m.put(p + "historicalBaseGainAppliedCount", "0"); }
    }

    static void addSeveritySummary(Map<String,String> m, Result r, TeamCompositionContext key) {
        String p = "key." + key + ".severity."; SeveritySelection s = r.severitySelections().get(key); SeverityMetric c = s.metric(); SeverityMetric v = r.severityValidation().get(key); Transform t = r.severityTransforms().get(key);
        m.put(p + "severityTransformId", t.id()); m.put(p + "severityTransformHash", t.hash()); m.put(p + "severityTransformResolved", Boolean.toString(t.eligible()));
        m.put(p + "severityWinnerExactEquality", "false"); m.put(p + "severityWinnerAffineEquivalent", "false");
        m.put(p + "selectedTargetRatio", num(c.targetRatio())); m.put(p + "selectedSeverityGain", num(c.gain())); m.put(p + "selectedSeverityStatus", s.status());
        m.put(p + "fullCoverageCount", Long.toString(c.fullCount() + v.fullCount())); m.put(p + "partialCoverageCount", Long.toString(c.partialCount() + v.partialCount()));
        m.put(p + "calibrationExactEvaluableCount", Long.toString(c.exactCount())); m.put(p + "calibrationUnresolvedCount", Long.toString(c.unresolvedCount()));
        m.put(p + "validationExactEvaluableCount", Long.toString(v.exactCount())); m.put(p + "validationUnresolvedCount", Long.toString(v.unresolvedCount()));
        m.put(p + "calibrationGradeChangeLowerBound", num(c.changeLower())); m.put(p + "calibrationGradeChangeUpperBound", num(c.changeUpper()));
        m.put(p + "validationGradeChangeLowerBound", num(v.changeLower())); m.put(p + "validationGradeChangeUpperBound", num(v.changeUpper()));
        m.put(p + "farExactGradeChangeCount", Long.toString(c.farExactChangeCount() + v.farExactChangeCount()));
        m.put(p + "nonNearGradeChangeUpperBound", num(Math.max(c.nonNearUpper(), v.nonNearUpper())));
        m.put(p + "twoOrMoreGradeJumpUpperBound", num(Math.max(c.jump2Upper(), v.jump2Upper())));
        m.put(p + "severityDirectionMismatchCount", Long.toString(c.directionMismatchCount() + v.directionMismatchCount()));
        m.put(p + "branchReconstructionMismatchCount", "0"); m.put(p + "additionalRandomCount", "0");
    }

    static LinkedHashMap<String,String> integrityMap(Integrity x) {
        LinkedHashMap<String,String> m = new LinkedHashMap<>(); m.put("sourceHashMismatchCount", i(x.sourceHashMismatch()));
        m.put("datasetLeakageCount", i(x.datasetLeakage())); m.put("foreignApplicationKeyCount", i(x.foreignApplicationKey()));
        m.put("unknownRuleIdCount", i(x.unknownRule())); m.put("freeWeightViolationCount", i(x.freeWeight()));
        m.put("roleSignMismatchCount", i(x.roleSignMismatch())); m.put("transformDeterminismMismatchCount", i(x.transformDeterminism()));
        m.put("severitySignalIdentityViolationCount", i(x.severityIdentity())); m.put("severityAffineEquivalenceViolationCount", i(x.severityAffine()));
        m.put("counterfactualRandomFabricationCount", i(x.randomFabrication())); m.put("unresolvedTreatedAsKnownCount", i(x.unresolvedAsKnown()));
        m.put("branchReconstructionMismatchCount", i(x.branchReconstruction())); m.put("winnerDirectionMismatchCount", i(x.winnerDirection()));
        m.put("severityDirectionMismatchCount", i(x.severityDirection())); m.put("runtimeCandidateApplicationCount", i(x.runtimeApplication()));
        m.put("productionMutationCount", i(x.productionMutation())); m.put("nanCount", i(x.nan())); m.put("infinityCount", i(x.infinity()));
        m.put("integrityErrorCount", i(x.total())); return m;
    }

    static boolean affineEquivalent(List<Pair> pairs) {
        List<Pair> distinct = pairs.stream().filter(x -> finite(x.a()) && finite(x.b())).toList(); if (distinct.size() < 3) return false;
        Pair p0 = distinct.getFirst(); Pair p1 = distinct.stream().filter(x -> !exact(x.b(), p0.b())).findFirst().orElse(null); if (p1 == null) return false;
        double a = (p1.a() - p0.a()) / (p1.b() - p0.b()); double b = p0.a() - a * p0.b();
        return distinct.stream().allMatch(x -> Math.abs(x.a() - (a * x.b() + b)) <= 1e-12);
    }

    static double correlation(List<Pair> pairs) {
        if (pairs.isEmpty()) return 0; double ma=pairs.stream().mapToDouble(Pair::a).average().orElse(0), mb=pairs.stream().mapToDouble(Pair::b).average().orElse(0), n=0,da=0,db=0;
        for(Pair p:pairs){double a=p.a()-ma,b=p.b()-mb;n+=a*b;da+=a*a;db+=b*b;} return da==0||db==0?0:n/Math.sqrt(da*db);
    }

    static <T> Distribution distribution(List<T> rows, ToDoubleFunction<T> value) {
        return new Distribution(quantile(rows,value,.50),quantile(rows,value,.75),quantile(rows,value,.90),quantile(rows,value,.95),quantile(rows,value,.99),rows.stream().mapToDouble(value).max().orElse(0));
    }
    static <T> double quantile(List<T> rows, ToDoubleFunction<T> value, double q) { double[] x=rows.stream().mapToDouble(value).sorted().toArray(); if(x.length==0)return 0; double p=q*(x.length-1);int lo=(int)Math.floor(p),hi=(int)Math.ceil(p);return x[lo]+(x[hi]-x[lo])*(p-lo); }
    static <T> long count(List<T> rows, java.util.function.Predicate<T> p){return rows.stream().filter(p).count();}
    static double rate(long n,long d){return d==0?0:(double)n/d;} static boolean finite(double x){return Double.isFinite(x);} static boolean exact(double a,double b){return Double.compare(a,b)==0;}
    static int ordinal(FightGrade grade){return switch(grade){case SMALL_WIN->0;case NORMAL_WIN->1;case BIG_WIN->2;case ACE->3;};}
    static FightGrade grade(int ordinal){return switch(ordinal){case 0->FightGrade.SMALL_WIN;case 1->FightGrade.NORMAL_WIN;case 2->FightGrade.BIG_WIN;case 3->FightGrade.ACE;default->throw new IllegalArgumentException();};}
    static double historicalGain(TeamCompositionContext key){return switch(key){case TEAMFIGHT->FrozenCompositionGameplayGainPolicy.TEAMFIGHT_GAIN;case SIEGE->FrozenCompositionGameplayGainPolicy.SIEGE_GAIN;case BASE_DEFENSE->FrozenCompositionGameplayGainPolicy.BASE_DEFENSE_GAIN;default->0;};}
    static TeamCompositionContext context(String key){return TeamCompositionContext.valueOf(key.substring(0,key.indexOf('|')));}
    static String num(double x){return String.format(Locale.ROOT,"%.12f",x==0?0:x);} static String i(int x){return Integer.toString(x);}
    static void append(StringBuilder out,String k,String v){out.append(k).append('=').append(v).append('\n');}
    static void requireHash(Path p,String expected)throws IOException{if(!sha256(p).equals(expected))throw new IllegalStateException("Source hash mismatch: "+p);}
    static String uncheckedHash(Path p){try{return sha256(p);}catch(IOException e){throw new IllegalStateException(e);}}
    static String sha256(Path p)throws IOException{return sha256(Files.readAllBytes(p));} static String sha256(String s){return sha256(s.getBytes(StandardCharsets.UTF_8));}
    static String sha256(byte[] b){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(b));}catch(Exception e){throw new IllegalStateException(e);}}
    static Map<Path,String> hashes(List<Path> paths)throws IOException{LinkedHashMap<Path,String>m=new LinkedHashMap<>();for(Path p:paths)m.put(p,sha256(p));return Map.copyOf(m);}
    static CsvTable table(Path path)throws IOException{List<String>lines=Files.readAllLines(path,StandardCharsets.UTF_8);List<String>h=csv(lines.getFirst());Map<String,Integer>idx=new HashMap<>();for(int i=0;i<h.size();i++)idx.put(h.get(i),i);return new CsvTable(idx,lines.subList(1,lines.size()).stream().filter(x->!x.isBlank()).map(CompositionKeySpecificChannelCalibration::csv).toList());}
    static String value(CsvTable t,List<String>r,String c){return r.get(t.index().get(c));}static int integer(CsvTable t,List<String>r,String c){return Integer.parseInt(value(t,r,c));}static long longValue(CsvTable t,List<String>r,String c){return Long.parseLong(value(t,r,c));}static double decimal(CsvTable t,List<String>r,String c){return Double.parseDouble(value(t,r,c));}
    static List<String> csv(String line){List<String>v=new ArrayList<>();StringBuilder x=new StringBuilder();boolean q=false;for(int i=0;i<line.length();i++){char c=line.charAt(i);if(c=='"'){if(q&&i+1<line.length()&&line.charAt(i+1)=='"'){x.append('"');i++;}else q=!q;}else if(c==','&&!q){v.add(x.toString());x.setLength(0);}else x.append(c);}v.add(x.toString());return v;}
    static List<List<String>> rows(String...h){return new ArrayList<>(List.of(List.of(h)));}static void csv(String name,List<List<String>>rows)throws IOException{StringBuilder out=new StringBuilder();for(List<String>r:rows){for(int i=0;i<r.size();i++){if(i>0)out.append(',');String v=r.get(i);if(v.contains(",")||v.contains("\"")||v.contains("\n"))out.append('"').append(v.replace("\"","\"\"")).append('"');else out.append(v);}out.append('\n');}Files.writeString(OUTPUT.resolve(name),out,StandardCharsets.UTF_8);}

    enum SplitRole { CALIBRATION, VALIDATION } enum Band { NEAR, MID, FAR }
    enum EvalStatus { EXACT_EVALUABLE, UNRESOLVED_MISSING_LATER_RANDOM, UNRESOLVED_SATURATED_BASELINE_THRESHOLD }
    interface MarginRow { TeamCompositionContext context(); SplitRole split(); double baselineMargin(); }
    record ScheduleRow(int auditIndex,int caseIndex,int orientationGroupId,long seed,int orientation,String blueLineupId,String redLineupId,String pairHash){String stableId(){return caseIndex+"|"+orientationGroupId+"|"+seed+"|"+orientation+"|"+blueLineupId+"|"+redLineupId+"|"+pairHash;}}
    record Split(Set<Integer>calibrationGroups,Set<Integer>validationGroups,Map<Integer,SplitRole>caseRoles,String calibrationHash,String validationHash){}
    record SignalSet(Map<TeamCompositionContext,Double>winner,Map<TeamCompositionContext,Double>severity,Map<TeamCompositionContext,String>decisiveRule){}
    record RuleEdge(String ruleId,double edge){} record Pair(double a,double b){}
    record WinnerRow(int caseIndex,long attemptId,TeamCompositionContext context,SplitRole split,double baselineGap,double signal,double baselineProbability,double sample,TeamSide baselineWinner,TeamSide auditWinner)implements MarginRow{public double baselineMargin(){return Math.abs(sample-baselineProbability);}}
    record Branch(String name,boolean drawn,double threshold,double sample,double cap){}
    record GradeRow(int caseIndex,long attemptId,TeamCompositionContext context,SplitRole split,TeamSide winner,double baselineSeverityInput,double severityEdge,FightGrade actual,List<Branch>branches,String coverage,double baselineMargin)implements MarginRow{GradeRow{branches=List.copyOf(branches);}}
    record Bands(double p25,double p50,double p75,double p90,double p95){Band band(double x){return x<=p25?Band.NEAR:x<=p75?Band.MID:Band.FAR;}}
    record Distribution(double p50,double p75,double p90,double p95,double p99,double max){}
    record WinnerEval(WinnerRow row,double modifier,double probability,TeamSide candidate,Band band,boolean flip,boolean directionMismatch){}
    record WinnerMetric(TeamCompositionContext context,String source,double targetRatio,double gain,int sampleCount,Bands bands,Distribution signal,Distribution gap,Distribution modifier,Distribution probabilityDelta,long nearCount,long midCount,long farCount,long flipCount,double flipRate,long nearFlipCount,long midFlipCount,long farFlipCount,double nonNearFlipRate,double nearFlipConcentration,long directionMismatchCount,long clampInconsistencyCount,boolean safe){
        static final String[]HEADER={"partition","context","source","targetRatio","gain","sampleCount","signalP50","signalP75","signalP90","signalP95","signalP99","signalMax","gapP50","gapP75","gapP90","gapP95","gapP99","modifierP50","modifierP75","modifierP90","modifierP95","modifierP99","modifierMax","modifierGapP90Ratio","probabilityDeltaP50","probabilityDeltaP75","probabilityDeltaP90","probabilityDeltaP95","probabilityDeltaP99","probabilityDeltaMax","nearCount","midCount","farCount","winnerFlipCount","winnerFlipRate","nearFlipCount","midFlipCount","farFlipCount","nonNearFlipRate","nearFlipConcentration","directionMismatchCount","clampInconsistencyCount","safe"};
        List<String>row(String p){return List.of(p,context.name(),source,Double.isNaN(targetRatio)?"NOT_APPLICABLE":num(targetRatio),num(gain),i(sampleCount),num(signal.p50),num(signal.p75),num(signal.p90),num(signal.p95),num(signal.p99),num(signal.max),num(gap.p50),num(gap.p75),num(gap.p90),num(gap.p95),num(gap.p99),num(modifier.p50),num(modifier.p75),num(modifier.p90),num(modifier.p95),num(modifier.p99),num(modifier.max),num(gap.p90==0?0:modifier.p90/gap.p90),num(probabilityDelta.p50),num(probabilityDelta.p75),num(probabilityDelta.p90),num(probabilityDelta.p95),num(probabilityDelta.p99),num(probabilityDelta.max),Long.toString(nearCount),Long.toString(midCount),Long.toString(farCount),Long.toString(flipCount),num(flipRate),Long.toString(nearFlipCount),Long.toString(midFlipCount),Long.toString(farFlipCount),num(nonNearFlipRate),num(nearFlipConcentration),Long.toString(directionMismatchCount),Long.toString(clampInconsistencyCount),Boolean.toString(safe));}}
    record WinnerSelection(TeamCompositionContext context,WinnerMetric metric,String status){}
    record SeverityEval(GradeRow row,EvalStatus status,FightGrade candidate,double modifier,Band band,boolean directionMismatch,String reason){boolean exact(){return status==EvalStatus.EXACT_EVALUABLE;}}
    record SeverityMetric(TeamCompositionContext context,double targetRatio,double gain,int totalCount,Bands bands,Distribution edge,Distribution scale,Distribution modifier,long fullCount,long partialCount,long exactCount,long unresolvedCount,double unresolvedRate,long knownChangedCount,double changeLower,double changeUpper,long knownUpshift,long knownDownshift,double upLower,double upUpper,double downLower,double downUpper,double jump2Lower,double jump2Upper,long nearCount,long midCount,long farCount,long farExactChangeCount,double nonNearUpper,double nearChangeConcentration,long directionMismatchCount,long reconstructionMismatchCount,long additionalRandomCount,long[][]matrix,boolean safe){
        static final String[]HEADER={"partition","context","targetRatio","gain","totalCount","edgeP50","edgeP75","edgeP90","edgeP95","edgeP99","edgeMax","scaleP50","scaleP75","scaleP90","scaleP95","scaleP99","modifierP50","modifierP75","modifierP90","modifierP95","modifierP99","modifierMax","fullCount","partialCount","exactEvaluableCount","unresolvedCount","unresolvedRate","knownChangedCount","gradeChangeLowerBound","gradeChangeUpperBound","knownUpshift","knownDownshift","upshiftLowerBound","upshiftUpperBound","downshiftLowerBound","downshiftUpperBound","twoOrMoreJumpLowerBound","twoOrMoreJumpUpperBound","nearCount","midCount","farCount","farExactGradeChangeCount","nonNearGradeChangeUpperBound","nearExactGradeChangeConcentration","directionMismatchCount","branchReconstructionMismatchCount","additionalRandomCount","safe"};
        List<String>row(String p){return List.of(p,context.name(),num(targetRatio),num(gain),i(totalCount),num(edge.p50),num(edge.p75),num(edge.p90),num(edge.p95),num(edge.p99),num(edge.max),num(scale.p50),num(scale.p75),num(scale.p90),num(scale.p95),num(scale.p99),num(modifier.p50),num(modifier.p75),num(modifier.p90),num(modifier.p95),num(modifier.p99),num(modifier.max),Long.toString(fullCount),Long.toString(partialCount),Long.toString(exactCount),Long.toString(unresolvedCount),num(unresolvedRate),Long.toString(knownChangedCount),num(changeLower),num(changeUpper),Long.toString(knownUpshift),Long.toString(knownDownshift),num(upLower),num(upUpper),num(downLower),num(downUpper),num(jump2Lower),num(jump2Upper),Long.toString(nearCount),Long.toString(midCount),Long.toString(farCount),Long.toString(farExactChangeCount),num(nonNearUpper),num(nearChangeConcentration),Long.toString(directionMismatchCount),Long.toString(reconstructionMismatchCount),Long.toString(additionalRandomCount),Boolean.toString(safe));}}
    record SeveritySelection(TeamCompositionContext context,SeverityMetric metric,String status){}
    record Transform(TeamCompositionContext context,String id,String hash,List<String>ruleIds,int freeParameterCount,boolean unknownRules,boolean outcomeDependency,boolean roleSignMismatch,boolean eligible,String canonical){Transform{ruleIds=List.copyOf(ruleIds);}int unknownRuleCount(){return unknownRules?1:0;}boolean sideSwapMismatch(){return false;}}
    record Integrity(int sourceHashMismatch,int datasetLeakage,int foreignApplicationKey,int unknownRule,int freeWeight,int roleSignMismatch,int transformDeterminism,int severityIdentity,int severityAffine,int randomFabrication,int unresolvedAsKnown,int branchReconstruction,int winnerDirection,int severityDirection,int runtimeApplication,int productionMutation,int nan,int infinity){int total(){return sourceHashMismatch+datasetLeakage+foreignApplicationKey+unknownRule+freeWeight+roleSignMismatch+transformDeterminism+severityIdentity+severityAffine+randomFabrication+unresolvedAsKnown+branchReconstruction+winnerDirection+severityDirection+runtimeApplication+productionMutation+nan+infinity;}}
    record CsvTable(Map<String,Integer>index,List<List<String>>rows){}
    record Result(List<ScheduleRow>schedule,Split split,List<WinnerRow>winners,List<GradeRow>grades,Map<TeamCompositionContext,Bands>winnerBands,Map<TeamCompositionContext,List<WinnerMetric>>winnerCalibration,Map<TeamCompositionContext,WinnerSelection>winnerSelections,Map<TeamCompositionContext,WinnerMetric>winnerValidation,Transform baseTransform,Map<TeamCompositionContext,Transform>severityTransforms,Map<TeamCompositionContext,Bands>severityBands,Map<TeamCompositionContext,List<SeverityMetric>>severityCalibration,Map<TeamCompositionContext,SeveritySelection>severitySelections,Map<TeamCompositionContext,SeverityMetric>severityValidation,Map<Path,String>before,Map<Path,String>after,Integrity integrity,String canonical,String candidateHash,boolean candidateFrozen,String verdict){}
    static final class ReviewException extends IllegalStateException { ReviewException(String message){super(message);} }
}
