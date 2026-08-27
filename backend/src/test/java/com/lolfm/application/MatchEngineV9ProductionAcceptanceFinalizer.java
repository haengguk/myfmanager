package com.lolfm.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/** Raw-only deterministic finalizer. It never creates a simulator or consumes Random. */
public final class MatchEngineV9ProductionAcceptanceFinalizer {
    private static final List<String> RAW_FILES = List.of(
            "acceptance-contract.json",
            "fixed-draft-archetypes.json",
            "player-proficiency-bindings.csv",
            "fixed-draft-runs.csv",
            "paired-profile-effects.csv",
            "composition-context-effects.csv",
            "baseline-rollback-oracle.json",
            "diagnostic-checks.json",
            "raw-correctness.json");
    private static final List<String> GENERATED_FILES = List.of(
            "acceptance-summary.json", "final-recommendation.json", "analysis.md");

    private MatchEngineV9ProductionAcceptanceFinalizer() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 3) {
            throw new IllegalArgumentException("backendRoot rawDirectory outputDirectory");
        }
        finalizeArtifact(Path.of(args[0]), Path.of(args[1]), Path.of(args[2]));
    }

    public static Finalized finalizeArtifact(Path backendRoot, Path raw, Path output)
            throws Exception {
        backendRoot = backendRoot.toAbsolutePath().normalize();
        raw = raw.toAbsolutePath().normalize();
        output = output.toAbsolutePath().normalize();
        for (String file : RAW_FILES) {
            if (!Files.isRegularFile(raw.resolve(file))) {
                throw new IllegalStateException("Missing raw acceptance file: " + file);
            }
        }
        ObjectMapper mapper = new ObjectMapper()
                .enable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
                .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);
        JsonNode contract = mapper.readTree(raw.resolve("acceptance-contract.json").toFile());
        JsonNode correctness = mapper.readTree(raw.resolve("raw-correctness.json").toFile());
        if (contract.path("coreSimulationCount").asInt() != 1_200
                || !contract.path("purposeNamespace").asText().equals(
                MatchEngineV9ProductionAcceptanceContract.PURPOSE_NAMESPACE)
                || !contract.path("scheduleHash").asText().equals(
                MatchEngineV9ProductionAcceptanceContract.scheduleHash())
                || !correctness.path("clean").asBoolean()
                || !contract.path("productionPolicy").path("policyHash").asText().equals(
                MatchEngineV1Policy.authoritative().policyHash())
                || !contract.path("productionSourceTree").path("hash").asText().equals(
                Phase13GB1AuditArtifactWriter.productionSourceTree(backendRoot).hash())) {
            throw new IllegalStateException("Acceptance raw/current-tree binding mismatch");
        }

        CsvTable runs = CsvTable.read(raw.resolve("fixed-draft-runs.csv"));
        CsvTable pairs = CsvTable.read(raw.resolve("paired-profile-effects.csv"));
        CsvTable contexts = CsvTable.read(raw.resolve("composition-context-effects.csv"));
        if (runs.rows().size() != 1_200 || pairs.rows().size() != 400) {
            throw new IllegalStateException("Acceptance raw population mismatch");
        }
        LinkedHashMap<String, Object> summary = summary(mapper, contract, correctness,
                runs, pairs, contexts, raw);
        LinkedHashMap<String, Object> recommendation = recommendation(summary);

        Files.createDirectories(output);
        for (String file : RAW_FILES) {
            Files.copy(raw.resolve(file), output.resolve(file),
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
        writeJson(mapper, output.resolve("acceptance-summary.json"), summary);
        writeJson(mapper, output.resolve("final-recommendation.json"), recommendation);
        Files.writeString(output.resolve("analysis.md"), analysis(summary),
                StandardCharsets.UTF_8);
        writeManifest(output);
        return new Finalized(output, runs.rows().size(), pairs.rows().size(),
                sha256(Files.readAllBytes(output.resolve("SHA256SUMS.txt"))));
    }

    private static LinkedHashMap<String, Object> summary(
            ObjectMapper mapper, JsonNode contract, JsonNode correctness,
            CsvTable runs, CsvTable pairs, CsvTable contexts, Path raw
    ) throws IOException {
        LinkedHashMap<String, Object> value = new LinkedHashMap<>();
        value.put("schemaVersion", "MATCH_ENGINE_V9_PRODUCTION_ACCEPTANCE_SUMMARY_V1");
        value.put("status", "MATCH_ENGINE_V9_PRODUCTION_ACCEPTED_WITH_KNOWN_LIMITATIONS");
        value.put("acceptanceStatus", MatchEngineV1Policy.ACCEPTANCE_STATUS);
        value.put("statisticalHoldoutApproved", false);
        value.put("purposeNamespace", MatchEngineV9ProductionAcceptanceContract.PURPOSE_NAMESPACE);
        value.put("coreSimulationCount", runs.rows().size());
        value.put("pairedCellCount", pairs.rows().size());
        value.put("additionalUniqueSimulationCount",
                contract.path("additionalUniqueSimulationCount").asInt());
        value.put("profileObservations", profileObservations(runs));
        value.put("pairedEffects", pairedEffects(pairs));
        value.put("compositionContextEffects", contextEffects(contexts));
        value.put("correctness", mapper.convertValue(correctness, Map.class));
        value.put("knownDiagnosticLimitations",
                mapper.convertValue(contract.path("productionPolicy")
                        .path("knownDiagnosticLimitations"), List.class));
        value.put("rollback", mapper.convertValue(
                mapper.readTree(raw.resolve("baseline-rollback-oracle.json").toFile()), Map.class));
        value.put("interpretation", List.of(
                "PRODUCT_SANITY_OBSERVATION_ONLY",
                "NOT_CALIBRATION_OR_STATISTICAL_HOLDOUT",
                "RAW_WIN_RATE_INCLUDES_PLAYER_RATINGS_PROFICIENCY_CHAMPION_POWER_AND_MATCHUP",
                "WIN_DIRECTION_IS_NOT_A_CORRECTNESS_GATE",
                "COUNTER_RESPONSE_IS_COMBINED_CAPABILITY_AND_INTERACTION_BEHAVIOR_NOT_ONE_ENUM"));
        return value;
    }

    private static Map<String, Object> profileObservations(CsvTable runs) {
        TreeMap<String, List<Map<String, String>>> grouped = new TreeMap<>();
        for (Map<String, String> row : runs.rows()) {
            String key = row.get("scenarioId") + "|" + row.get("orientationId") + "|"
                    + row.get("profileId");
            grouped.computeIfAbsent(key, ignored -> new ArrayList<>()).add(row);
        }
        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        grouped.forEach((key, rows) -> {
            List<Integer> durations = rows.stream().map(value -> Integer.parseInt(
                    value.get("durationSeconds"))).sorted().toList();
            long blue = count(rows, "winnerSide", "BLUE");
            long red = count(rows, "winnerSide", "RED");
            long t1 = count(rows, "winnerTeamCode", "T1");
            long gen = count(rows, "winnerTeamCode", "GEN");
            long archetype = count(rows, "winnerRole", "ARCHETYPE");
            long counter = count(rows, "winnerRole", "COUNTER");
            LinkedHashMap<String, Object> observation = new LinkedHashMap<>();
            observation.put("games", rows.size());
            observation.put("blueWins", blue);
            observation.put("redWins", red);
            observation.put("t1Wins", t1);
            observation.put("genWins", gen);
            observation.put("archetypeWins", archetype);
            observation.put("counterWins", counter);
            observation.put("blueWinRate", rate(blue, rows.size()));
            observation.put("t1WinRate", rate(t1, rows.size()));
            observation.put("sideNormalizedArchetypeWinRate", rate(archetype, rows.size()));
            observation.put("meanDurationSeconds", durations.stream()
                    .mapToInt(Integer::intValue).average().orElse(0.0));
            observation.put("medianDurationSeconds", quantile(durations, 0.50));
            observation.put("p95DurationSeconds", quantile(durations, 0.95));
            result.put(key, observation);
        });
        return result;
    }

    private static Map<String, Object> pairedEffects(CsvTable pairs) {
        TreeMap<String, List<Map<String, String>>> grouped = new TreeMap<>();
        for (Map<String, String> row : pairs.rows()) {
            grouped.computeIfAbsent(row.get("scenarioId"), ignored -> new ArrayList<>()).add(row);
        }
        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        grouped.forEach((scenario, rows) -> {
            LinkedHashMap<String, Object> metrics = new LinkedHashMap<>();
            metrics.put("pairs", rows.size());
            metrics.put("baselineToMatchupWinnerChanges",
                    changed(rows, "baselineWinnerRole", "matchupWinnerRole"));
            metrics.put("matchupToProductionWinnerChanges",
                    changed(rows, "matchupWinnerRole", "productionWinnerRole"));
            metrics.put("baselineToProductionWinnerChanges",
                    changed(rows, "baselineWinnerRole", "productionWinnerRole"));
            metrics.put("baselineToMatchupObjectiveChanges", truth(rows, "matchupObjectiveChanged"));
            metrics.put("matchupToProductionObjectiveChanges",
                    truth(rows, "compositionObjectiveChanged"));
            metrics.put("baselineToProductionObjectiveChanges", truth(rows, "productObjectiveChanged"));
            metrics.put("baselineToMatchupStructureChanges", truth(rows, "matchupStructureChanged"));
            metrics.put("matchupToProductionStructureChanges",
                    truth(rows, "compositionStructureChanged"));
            metrics.put("baselineToProductionStructureChanges", truth(rows, "productStructureChanged"));
            metrics.put("baselineToMatchupNexusEndingChanges",
                    truth(rows, "matchupNexusEndingChanged"));
            metrics.put("matchupToProductionNexusEndingChanges",
                    truth(rows, "compositionNexusEndingChanged"));
            metrics.put("baselineToProductionNexusEndingChanges",
                    truth(rows, "productNexusEndingChanged"));
            metrics.put("meanBaselineToMatchupDurationDeltaSeconds",
                    average(rows, "matchupDurationDelta"));
            metrics.put("meanMatchupToProductionDurationDeltaSeconds",
                    average(rows, "compositionDurationDelta"));
            metrics.put("meanBaselineToProductionDurationDeltaSeconds",
                    average(rows, "productDurationDelta"));
            metrics.put("productionCompositionReachablePairs",
                    truth(rows, "productionCompositionReachable"));
            result.put(scenario, metrics);
        });
        return result;
    }

    private static Map<String, Object> contextEffects(CsvTable contexts) {
        TreeMap<String, ContextAggregate> aggregate = new TreeMap<>();
        for (Map<String, String> row : contexts.rows()) {
            String key = row.get("profileId") + "|" + row.get("context");
            aggregate.computeIfAbsent(key, ignored -> new ContextAggregate()).add(row);
        }
        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        aggregate.forEach((key, value) -> result.put(key, value.value()));
        return result;
    }

    private static LinkedHashMap<String, Object> recommendation(Map<String, Object> summary) {
        LinkedHashMap<String, Object> value = new LinkedHashMap<>();
        value.put("schemaVersion", "MATCH_ENGINE_V9_PRODUCTION_ACCEPTANCE_RECOMMENDATION_V1");
        value.put("decision", "MATCH_ENGINE_V9_PRODUCTION_ACCEPTED_WITH_KNOWN_LIMITATIONS");
        value.put("acceptanceStatus", MatchEngineV1Policy.ACCEPTANCE_STATUS);
        value.put("statisticalHoldoutApproved", false);
        value.put("authoritativeProfile", "PRODUCTION_MATCHUP_COMPOSITION_V1");
        value.put("rollbackProfile", "BASELINE_V1");
        value.put("rollbackMode", MatchEngineV1Policy.ROLLBACK_MODE);
        value.put("automaticFallback", false);
        value.put("knownDiagnosticLimitations", summary.get("knownDiagnosticLimitations"));
        value.put("balanceProven", false);
        value.put("matchupCausalLineageComplete", false);
        return value;
    }

    private static String analysis(Map<String, Object> summary) {
        return "# Match Engine V9 production acceptance\n\n"
                + "- Decision: `MATCH_ENGINE_V9_PRODUCTION_ACCEPTED_WITH_KNOWN_LIMITATIONS`\n"
                + "- Acceptance: `" + MatchEngineV1Policy.ACCEPTANCE_STATUS + "`\n"
                + "- Core simulations: " + summary.get("coreSimulationCount") + "\n"
                + "- Paired cells: " + summary.get("pairedCellCount") + "\n"
                + "- Additional unique replay/instrumentation/rollback simulations: "
                + summary.get("additionalUniqueSimulationCount") + "\n"
                + "- Statistical holdout approved: false\n\n"
                + "This fixed-Draft population is a product sanity observation. Raw win rates "
                + "combine real T1/GEN player ratings and proficiency, Champion Power, Matchup, "
                + "and Composition. They are not a real-world LCK win-rate estimate or a balance gate.\n\n"
                + "The counter/response lineup is represented by DISENGAGE, PEEL, FRONTLINE, "
                + "WAVE_CLEAR, ZONE_CONTROL and opponent interaction rules, not a single counter enum. "
                + "Bans are not applicable after the final ten role assignments.\n";
    }

    private static void writeJson(ObjectMapper mapper, Path target, Object value) throws IOException {
        Files.write(target, mapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(value));
        Files.writeString(target, "\n", StandardCharsets.UTF_8,
                java.nio.file.StandardOpenOption.APPEND);
    }

    private static void writeManifest(Path output) throws IOException {
        List<Path> files;
        try (var stream = Files.list(output)) {
            files = stream.filter(Files::isRegularFile)
                    .filter(value -> !value.getFileName().toString().equals("SHA256SUMS.txt"))
                    .sorted(Comparator.comparing(value -> value.getFileName().toString())).toList();
        }
        StringBuilder manifest = new StringBuilder();
        for (Path file : files) {
            manifest.append(sha256(Files.readAllBytes(file))).append("  ")
                    .append(file.getFileName()).append('\n');
        }
        Files.writeString(output.resolve("SHA256SUMS.txt"), manifest.toString(),
                StandardCharsets.UTF_8);
    }

    private static long count(List<Map<String, String>> rows, String field, String expected) {
        return rows.stream().filter(value -> value.get(field).equals(expected)).count();
    }

    private static long changed(List<Map<String, String>> rows, String first, String second) {
        return rows.stream().filter(value -> !value.get(first).equals(value.get(second))).count();
    }

    private static long truth(List<Map<String, String>> rows, String field) {
        return rows.stream().filter(value -> Boolean.parseBoolean(value.get(field))).count();
    }

    private static double average(List<Map<String, String>> rows, String field) {
        return rows.stream().mapToInt(value -> Integer.parseInt(value.get(field)))
                .average().orElse(0.0);
    }

    private static double rate(long count, long total) {
        return total == 0 ? 0.0 : (double) count / total;
    }

    private static int quantile(List<Integer> sorted, double quantile) {
        if (sorted.isEmpty()) return 0;
        int index = Math.max(0, (int) Math.ceil(quantile * sorted.size()) - 1);
        return sorted.get(Math.min(index, sorted.size() - 1));
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException(error);
        }
    }

    private record CsvTable(List<String> headers, List<Map<String, String>> rows) {
        static CsvTable read(Path path) throws IOException {
            List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
            if (lines.isEmpty()) throw new IllegalStateException("Empty CSV: " + path);
            List<String> headers = List.of(lines.getFirst().split(",", -1));
            ArrayList<Map<String, String>> rows = new ArrayList<>();
            for (String line : lines.subList(1, lines.size())) {
                if (line.isEmpty()) continue;
                String[] fields = line.split(",", -1);
                if (fields.length != headers.size()) {
                    throw new IllegalStateException("CSV width mismatch: " + path);
                }
                LinkedHashMap<String, String> row = new LinkedHashMap<>();
                for (int index = 0; index < fields.length; index++) {
                    row.put(headers.get(index), fields[index]);
                }
                rows.add(Map.copyOf(row));
            }
            return new CsvTable(headers, List.copyOf(rows));
        }
    }

    private static final class ContextAggregate {
        private long games;
        private long observations;
        private long applications;
        private long consumed;
        private double weightedSum;
        private double min = Double.POSITIVE_INFINITY;
        private double max = Double.NEGATIVE_INFINITY;

        void add(Map<String, String> row) {
            games += Long.parseLong(row.get("games"));
            observations += Long.parseLong(row.get("observationCount"));
            applications += Long.parseLong(row.get("applicationCount"));
            long count = Long.parseLong(row.get("modifierConsumedCount"));
            double mean = Double.parseDouble(row.get("signedModifierMean"));
            consumed += count;
            weightedSum += count * mean;
            if (count > 0) {
                min = Math.min(min, Double.parseDouble(row.get("signedModifierMin")));
                max = Math.max(max, Double.parseDouble(row.get("signedModifierMax")));
            }
        }

        Map<String, Object> value() {
            LinkedHashMap<String, Object> value = new LinkedHashMap<>();
            value.put("games", games);
            value.put("observationCount", observations);
            value.put("applicationCount", applications);
            value.put("modifierConsumedCount", consumed);
            value.put("signedModifierMean", consumed == 0 ? 0.0 : weightedSum / consumed);
            value.put("signedModifierMin", consumed == 0 ? 0.0 : min);
            value.put("signedModifierMax", consumed == 0 ? 0.0 : max);
            return value;
        }
    }

    public record Finalized(Path output, int coreSimulationCount, int pairedCellCount,
                            String manifestSha256) { }
}
