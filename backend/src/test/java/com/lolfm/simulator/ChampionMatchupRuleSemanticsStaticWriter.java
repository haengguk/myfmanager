package com.lolfm.simulator;

import com.lolfm.champion.ChampionMatchupDilutionMetrics;
import com.lolfm.champion.ChampionMatchupRuleCatalog;
import com.lolfm.champion.ChampionMatchupRuleType;
import com.lolfm.champion.GeneratedChampionMatchupCatalogFactory;
import com.lolfm.champion.LaneMatchupSemantic;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

final class ChampionMatchupRuleSemanticsStaticWriter {
    private static final ChampionMatchupRuleType[] RULES =
            ChampionMatchupRuleType.values();
    private static final double[] ORIGINAL =
            {.25, .20, .20, .10, .20, .05, .00};
    private static final double[] SELECTED =
            {.05, .25, .20, .20, .05, .20, .05};
    private static final double[] ENGAGE_HEAVY =
            {.10, .30, .20, .15, .05, .15, .05};

    private ChampionMatchupRuleSemanticsStaticWriter() {
    }

    static StaticResult write(
            Path output,
            ChampionMatchupRuleCatalog catalog,
            GeneratedChampionMatchupCatalogFactory.BuildResult build
    ) throws IOException {
        writeLaneSemantics(output);
        writeWeights(output, catalog);
        List<DilutionRow> dilution = dilutionRows();
        ChampionMatchupRuleEngineCsv.records(
                output.resolve("champion-matchup-dilution-metrics.csv"), dilution);
        List<DeadzoneRow> deadzones = deadzoneRows(build);
        ChampionMatchupRuleEngineCsv.records(
                output.resolve("champion-matchup-deadzone-candidates.csv"), deadzones);
        return new StaticResult(dilution, deadzones);
    }

    private static void writeLaneSemantics(Path output) throws IOException {
        ChampionMatchupRuleEngineCsv.lines(
                output.resolve("champion-matchup-lane-semantics.csv"),
                new String[]{"candidateSemantic", "sourceClass", "sourceMethod",
                        "actionStage", "lanePressureOverlap", "selected", "rationale"},
                List.of(
                        new String[]{"BROAD_LANE_EXCHANGE",
                                "LanePressureResolver", "resolve",
                                "AMBIENT_PRESSURE_STATE", "false", "false",
                                "Pressure owns recurring attribute/gold/random state, not combat outcome"},
                        new String[]{"COMMITTED_LANE_COMBAT",
                                "LaneCombatResolver", "resolve/combatEdge",
                                "ACTUAL_MAJOR_COMBAT_ATTEMPT", "false", "true",
                                "Resolver marks an attempt, participants, outcome, kill rewards and pressure shock"}));
    }

    private static void writeWeights(
            Path output,
            ChampionMatchupRuleCatalog catalog
    ) throws IOException {
        List<String[]> rows = new ArrayList<>();
        candidate(rows, "ORIGINAL_BROAD", LaneMatchupSemantic.BROAD_LANE_EXCHANGE,
                ORIGINAL, catalog, false,
                "Range and wave retain ambient-lane emphasis");
        candidate(rows, "COMMITTED_BALANCED", LaneMatchupSemantic.COMMITTED_LANE_COMBAT,
                SELECTED, catalog, true,
                "Reduce range/wave; emphasize engage, extended fight and peel");
        candidate(rows, "COMMITTED_ENGAGE_HEAVY",
                LaneMatchupSemantic.COMMITTED_LANE_COMBAT,
                ENGAGE_HEAVY, catalog, false,
                "Comparison candidate with stronger engage concentration");
        ChampionMatchupRuleEngineCsv.lines(
                output.resolve("champion-matchup-lane-weight-comparison.csv"),
                new String[]{"candidateId", "semantic", "context", "rule",
                        "originalWeight", "candidateWeight", "selectedWeight", "reason"},
                rows);
    }

    private static void candidate(
            List<String[]> rows,
            String id,
            LaneMatchupSemantic semantic,
            double[] candidate,
            ChampionMatchupRuleCatalog catalog,
            boolean selected,
            String reason
    ) {
        for (int index = 0; index < RULES.length; index++) {
            rows.add(new String[]{
                    id, semantic.name(), ProgressionCombatContext.LANE_COMBAT.name(),
                    RULES[index].name(), String.valueOf(ORIGINAL[index]),
                    String.valueOf(candidate[index]),
                    String.valueOf(catalog.weight(
                            ProgressionCombatContext.LANE_COMBAT, RULES[index])),
                    selected ? reason : "NOT_SELECTED: " + reason});
        }
    }

    private static List<DilutionRow> dilutionRows() {
        return List.of(
                dilution("ONE_OF_ONE", List.of(.20)),
                dilution("ONE_OF_TWO", List.of(.20, 0.0)),
                dilution("ONE_OF_FIVE", List.of(.20, 0.0, 0.0, 0.0, 0.0)),
                dilution("SMALL_ONE_OF_FIVE",
                        List.of(.002, 0.0, 0.0, 0.0, 0.0)),
                dilution("ALL_FIVE_SAME",
                        List.of(.10, .10, .10, .10, .10)),
                dilution("OPPOSITE_CANCEL", List.of(.20, -.20)));
    }

    private static DilutionRow dilution(String id, List<Double> edges) {
        ChampionMatchupDilutionMetrics value =
                ChampionMatchupDilutionMetrics.calculate(edges.size(), edges);
        return new DilutionRow(
                id, value.eligiblePairCount(), value.nonZeroPairCount(),
                text(value.coverageRatio()), value.allEligibleAverage(),
                text(value.nonZeroAverage()), text(value.absoluteNonZeroMean()),
                text(value.netDirectionalRetention()),
                text(value.coverageAttenuation()),
                text(value.expectedCoverageAttenuation()),
                text(value.coverageAttenuationError()),
                value.classification().name());
    }

    private static List<DeadzoneRow> deadzoneRows(
            GeneratedChampionMatchupCatalogFactory.BuildResult build
    ) {
        List<DeadzoneRow> rows = new ArrayList<>();
        for (double threshold : new double[]{0.0, .001, .0025, .005, .01}) {
            int neutralized = 0;
            Set<String> contexts = new HashSet<>();
            Set<String> pairs = new HashSet<>();
            for (var entry : build.generatedResults().entrySet()) {
                double edge = entry.getValue().finalGeneratedEdge();
                if (edge != 0.0 && Math.abs(edge) <= threshold) {
                    neutralized++;
                    contexts.add(entry.getKey().context().name());
                    pairs.add(entry.getKey().pair().stableId());
                }
            }
            rows.add(new DeadzoneRow(
                    threshold, neutralized, contexts.size(), pairs.size(),
                    expectedDirectionChanges(build, threshold), 0,
                    "DIAGNOSTIC_ONLY; explanations preserved; runtime unchanged"));
        }
        return rows;
    }

    private static int expectedDirectionChanges(
            GeneratedChampionMatchupCatalogFactory.BuildResult build,
            double threshold
    ) {
        return (int) build.generatedResults().entrySet().stream()
                .filter(entry -> !entry.getValue().neutralFallback())
                .filter(entry -> entry.getValue().finalGeneratedEdge() != 0.0)
                .filter(entry -> Math.abs(entry.getValue().finalGeneratedEdge())
                        <= threshold)
                .count();
    }

    private static String text(Double value) {
        return value == null ? "NOT_APPLICABLE" : String.valueOf(value);
    }

    record StaticResult(List<DilutionRow> dilution, List<DeadzoneRow> deadzones) {
    }

    record DilutionRow(
            String fixtureId, int eligiblePairCount, int nonZeroPairCount,
            String coverageRatio, double allEligibleAverage,
            String nonZeroAverage, String absoluteNonZeroMean,
            String netDirectionalRetention, String coverageAttenuation,
            String expectedCoverageAttenuation, String coverageAttenuationError,
            String classification
    ) {
    }

    record DeadzoneRow(
            double threshold, int rowsNeutralized, int contextsNeutralized,
            int pairsAffected, int expectedDirectionChanges,
            int directionalityErrors, String notes
    ) {
    }
}
