package com.lolfm.simulator;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Phase 13C-4.2.1a artifact-only eligibility correction. Never runs simulation. */
public final class GeometricCandidateSummaryRebuilder {
    static final Path OUTPUT = Path.of("build/reports/geometric-candidate-influence");
    static final Path DYNAMIC = OUTPUT.resolve("geometric-candidate-focused-dynamic.csv");
    static final Path SUMMARY = OUTPUT.resolve("geometric-candidate-summary.csv");
    static final Path AUDIT = OUTPUT.resolve("geometric-candidate-audit.log");
    static final String SMALL = "COMBINED_LEAD_SMALL";
    static final String LARGE = "COMBINED_LEAD_LARGE";
    static final String PROFILE_HASH = "c8956937e8c9032654feb2bb17ff7ef66d68a964b4f1f6ed98853400f5b3dc64";
    static final Map<Path, String> BASELINES = Map.of(
            Path.of("baseline/phase12_5/progression-baseline-summary.csv"), "af014896733d568974c91043c24d07917239808e3fcb9277bfba55480974da04",
            Path.of("baseline/phase12_5/progression-combat-contribution.csv"), "f18ab7781284d23a9369a1f8a1ee4ba5df156706727dc588ce42114d90ddc735",
            Path.of("baseline/phase12_5/progression-position-timings.csv"), "464f895021398f6ffa25cfebabc08d0483e3428018321f127f45d82f8725ec5c");

    private GeometricCandidateSummaryRebuilder() {}

    public static void main(String[] args) throws Exception {
        byte[] dynamicBefore = Files.readAllBytes(DYNAMIC);
        List<Row> rows = readDynamic(DYNAMIC);
        if (rows.size() != 1920) throw new IllegalStateException("Expected 1,920 focused dynamic rows");
        GrowthSummary small = summarize(rows, SMALL);
        GrowthSummary large = summarize(rows, LARGE);
        LinkedHashMap<String, String> original = readSummary(SUMMARY);
        verifyFrozenInputs(original);
        String oldRate = required(original, "combinedLargeOvercomeRate");
        String verdictBefore = required(original, "verdict");
        LinkedHashMap<String, Object> corrected = correct(original, small, large);
        ChampionMatchupRuleEngineCsv.summary(SUMMARY, corrected);
        writeAudit(corrected, small, large, oldRate, verdictBefore);
        if (!Arrays.equals(dynamicBefore, Files.readAllBytes(DYNAMIC))) throw new IllegalStateException("Focused dynamic artifact changed during rebuild");
        System.out.println("Geometric candidate summary rebuilt from existing artifacts: " + corrected.get("verdict"));
        System.out.println("Simulation rerun: false");
    }

    static GrowthSummary summarize(List<Row> rows, String scenario) {
        List<Row> selected = rows.stream().filter(row -> scenario.equals(row.scenario())).toList();
        long eligible = selected.stream().filter(Row::growthPackageEligible).count();
        long eligibleOvercome = selected.stream().filter(row -> row.growthPackageEligible() && row.overcome()).count();
        long ineligibleOvercome = selected.stream().filter(row -> !row.growthPackageEligible() && row.overcome()).count();
        return new GrowthSummary(scenario, selected.size(), eligible, selected.size() - eligible,
                eligibleOvercome, eligible - eligibleOvercome, ineligibleOvercome,
                selected.size() - eligible - ineligibleOvercome, true);
    }

    static LinkedHashMap<String, Object> correct(Map<String, String> original, GrowthSummary small, GrowthSummary large) {
        LinkedHashMap<String, Object> values = new LinkedHashMap<>(original);
        addGrowth(values, "combinedSmall", small);
        addGrowth(values, "combinedLarge", large);
        values.put("combinedLargeOvercomeRate", large.rateValue());
        values.put("combinedLargeEligibleOvercomeRate", large.rateValue());
        values.put("growthOvercomeRateDenominator", "GROWTH_PACKAGE_ELIGIBLE_ROWS_ONLY");
        List<String> warnings = warningList(original.get("warningCodes"));
        warnings.remove("COMBINED_SMALL_OVERCOME_REVIEW");
        warnings.remove("COMBINED_LARGE_OVERCOME_REVIEW");
        if (small.eligibleRows() > 0 && small.rate() < .99) warnings.add("COMBINED_SMALL_OVERCOME_REVIEW");
        if (large.eligibleRows() > 0 && large.rate() < .99) warnings.add("COMBINED_LARGE_OVERCOME_REVIEW");
        boolean expected = small.totalRows() == 320 && small.eligibleRows() == 240
                && small.eligibleOvercomeRows() == 240 && small.eligibleNotOvercomeRows() == 0
                && large.totalRows() == 320 && large.eligibleRows() == 240
                && large.eligibleOvercomeRows() == 240 && large.eligibleNotOvercomeRows() == 0
                && large.ineligibleNotOvercomeRows() == 4;
        boolean ready = expected && warnings.isEmpty() && baseReady(original) && passes(small, .99) && passes(large, .99);
        values.put("warningCodes", warnings.isEmpty() ? "NONE" : String.join("|", warnings));
        values.put("eligibilityArtifactExpected", expected);
        values.put("integrityErrorCount", required(original, "integrityErrorCount"));
        values.put("verdict", expected ? (ready ? "READY_FOR_PHASE_13C5" : "REVIEW_ELIGIBILITY_ARTIFACT_MISMATCH") : "REVIEW_ELIGIBILITY_ARTIFACT_MISMATCH");
        values.put("phase13c5Allowed", ready);
        values.put("productionActivationAllowed", false);
        return values;
    }

    private static void addGrowth(Map<String, Object> values, String prefix, GrowthSummary summary) {
        values.put(prefix + "TotalRows", summary.totalRows());
        values.put(prefix + "EligibleRows", summary.eligibleRows());
        values.put(prefix + "IneligibleRows", summary.ineligibleRows());
        values.put(prefix + "EligibleOvercomeRows", summary.eligibleOvercomeRows());
        values.put(prefix + "EligibleNotOvercomeRows", summary.eligibleNotOvercomeRows());
        values.put(prefix + "IneligibleOvercomeRows", summary.ineligibleOvercomeRows());
        values.put(prefix + "IneligibleNotOvercomeRows", summary.ineligibleNotOvercomeRows());
        values.put(prefix + "OvercomeRate", summary.rateValue());
        values.put(prefix + "EligibilityApplied", summary.eligibilityApplied());
    }

    static boolean passes(GrowthSummary summary, double threshold) {
        return summary.eligibleRows() == 0 || summary.rate() >= threshold;
    }

    private static boolean baseReady(Map<String, String> values) {
        return "0".equals(required(values, "integrityErrorCount"))
                && "EXPOSURE_GATED_GEOMETRIC_V2".equals(required(values, "selectedFormula"))
                && !"NONE".equals(required(values, "selectedGain"))
                && number(values, "skillPlus3OvercomeRate") >= .95 && number(values, "skillPlus5OvercomeRate") >= .99
                && number(values, "championPowerHardLockCount") == 0 && number(values, "strongMatchupHardLockCount") == 0
                && number(values, "severeBroadReviewCount") == 0 && number(values, "systemicFormulaReviewCount") == 0
                && number(values, "systemicRuleDominanceCount") == 0 && Boolean.parseBoolean(required(values, "localCumulativeEffectObserved"))
                && number(values, "winnerFlipRate") <= .02 && number(values, "addedOrientationDifference") <= .015
                && number(values, "directRandomCallCount") == 0 && number(values, "replayMismatchCount") == 0
                && number(values, "diagnosticsMismatchCount") == 0 && "OFF".equals(required(values, "productionModeDefault"))
                && number(values, "productionNonZeroEdgeCount") == 0 && number(values, "productionOverrideCount") == 0
                && "NONE".equals(required(values, "productionGain")) && "NONE".equals(required(values, "productionDeadzone"))
                && number(values, "candidateApiFrontendExposureCount") == 0;
    }

    private static void verifyFrozenInputs(Map<String, String> summary) throws Exception {
        if (!PROFILE_HASH.equals(required(summary, "frozenProfileHash"))) throw new IllegalStateException("Frozen profile hash mismatch");
        for (var entry : BASELINES.entrySet()) if (!entry.getValue().equals(sha256(entry.getKey()))) throw new IllegalStateException("Baseline hash mismatch: " + entry.getKey());
    }

    private static void writeAudit(Map<String, Object> corrected, GrowthSummary small, GrowthSummary large, String oldRate, String verdictBefore) throws Exception {
        LinkedHashMap<String, Object> audit = new LinkedHashMap<>(corrected);
        audit.put("oldCombinedLargeRate", oldRate);
        audit.put("oldCombinedLargeDenominator", large.totalRows());
        audit.put("correctedEligibleDenominator", large.eligibleRows());
        audit.put("correctedEligibleNumerator", large.eligibleOvercomeRows());
        audit.put("correctedRate", large.rateValue());
        audit.put("excludedIneligibleRowCount", large.ineligibleRows());
        audit.put("excludedIneligibleFailedRowCount", large.ineligibleNotOvercomeRows());
        audit.put("combinedSmallExcludedIneligibleRowCount", small.ineligibleRows());
        audit.put("correctionReason", "GROWTH_PACKAGE_ELIGIBILITY_FILTER");
        audit.put("simulationRerun", false);
        audit.put("fullMatchRerun", false);
        audit.put("dynamicArtifactReused", true);
        audit.put("verdictBefore", verdictBefore);
        audit.put("verdictAfter", corrected.get("verdict"));
        Files.writeString(AUDIT, audit.entrySet().stream().map(entry -> entry.getKey() + "=" + entry.getValue()).collect(Collectors.joining("\n", "", "\n")));
    }

    static List<Row> readDynamic(Path path) throws Exception {
        List<String> lines = Files.readAllLines(path);
        String[] header = lines.getFirst().split(",", -1);
        int scenario = index(header, "scenario"), overcome = index(header, "overcome"), eligible = index(header, "growthPackageEligible");
        List<Row> rows = new ArrayList<>(lines.size() - 1);
        for (String line : lines.subList(1, lines.size())) {
            String[] values = line.split(",", -1);
            if (values.length != header.length) throw new IllegalStateException("Dynamic artifact parse failure");
            rows.add(new Row(values[scenario], Boolean.parseBoolean(values[eligible]), Boolean.parseBoolean(values[overcome])));
        }
        return List.copyOf(rows);
    }

    static LinkedHashMap<String, String> readSummary(Path path) throws Exception {
        List<String> lines = Files.readAllLines(path);
        LinkedHashMap<String, String> values = new LinkedHashMap<>();
        for (String line : lines.subList(1, lines.size())) {
            int comma = line.indexOf(',');
            if (comma <= 0) throw new IllegalStateException("Summary artifact parse failure");
            values.put(line.substring(0, comma), line.substring(comma + 1));
        }
        return values;
    }

    private static List<String> warningList(String value) {
        if (value == null || value.isBlank() || "NONE".equals(value)) return new ArrayList<>();
        return new ArrayList<>(List.of(value.split("\\|")));
    }
    private static double number(Map<String, String> values, String key) { return Double.parseDouble(required(values, key)); }
    private static String required(Map<String, String> values, String key) { String value = values.get(key); if (value == null) throw new IllegalStateException("Missing summary field " + key); return value; }
    private static int index(String[] header, String name) { for (int i = 0; i < header.length; i++) if (name.equals(header[i])) return i; throw new IllegalStateException("Missing dynamic field " + name); }
    private static String sha256(Path path) throws Exception { return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path))); }

    record Row(String scenario, boolean growthPackageEligible, boolean overcome) {}
    record GrowthSummary(String scenario, long totalRows, long eligibleRows, long ineligibleRows, long eligibleOvercomeRows,
                         long eligibleNotOvercomeRows, long ineligibleOvercomeRows, long ineligibleNotOvercomeRows, boolean eligibilityApplied) {
        double rate() { return eligibleRows == 0 ? Double.NaN : eligibleOvercomeRows / (double) eligibleRows; }
        Object rateValue() { return eligibleRows == 0 ? "NOT_APPLICABLE" : rate(); }
    }
}
