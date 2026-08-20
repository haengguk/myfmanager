package com.lolfm.draft;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.stream.Collectors;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

/** Finalizes the V2 audit only after the final backend regression has run. */
public final class Phase13GA2Finalizer {
    private static final Path DEFAULT_OUTPUT = Path.of(Phase13GA2StructuralIntegratedAudit.OUTPUT_DIRECTORY);
    private static final Path DEFAULT_TEST_RESULTS = Path.of("build/test-results/test");

    private Phase13GA2Finalizer() { }

    public record RegressionCounts(long tests, long failures, long errors, long skipped,
                                   int xmlFileCount) { }

    public static void main(String[] args) throws Exception {
        Path output = Path.of(System.getProperty("phase13g.outputDir", DEFAULT_OUTPUT.toString()));
        Path testResults = Path.of(System.getProperty("phase13g.testResultsDir", DEFAULT_TEST_RESULTS.toString()));
        RegressionCounts counts = aggregateXml(testResults);
        finalizeArtifacts(output, counts);
        System.out.println("PHASE13G_A2_FINALIZED_TESTS=" + counts.tests());
        System.out.println("PHASE13G_A2_FINALIZED_FAILURES=" + counts.failures());
        System.out.println("PHASE13G_A2_FINALIZED_ERRORS=" + counts.errors());
        System.out.println("PHASE13G_A2_FINALIZED_SKIPPED=" + counts.skipped());
        System.out.println("PHASE13G_A2_FINALIZED_XML_FILES=" + counts.xmlFileCount());
    }

    public static RegressionCounts aggregateXml(Path testResultsDirectory) throws Exception {
        if (!Files.isDirectory(testResultsDirectory)) {
            throw new IOException("Missing JUnit XML directory: " + testResultsDirectory);
        }
        List<Path> files;
        try (var stream = Files.list(testResultsDirectory)) {
            files = stream.filter(Files::isRegularFile)
                    .filter(value -> value.getFileName().toString().startsWith("TEST-"))
                    .filter(value -> value.getFileName().toString().endsWith(".xml"))
                    .sorted(Comparator.comparing(value -> value.getFileName().toString()))
                    .toList();
        }
        long tests = 0L;
        long failures = 0L;
        long errors = 0L;
        long skipped = 0L;
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        for (Path file : files) {
            Document document = factory.newDocumentBuilder().parse(file.toFile());
            Element root = document.getDocumentElement();
            tests += attributeOrTestcaseCount(root, "tests");
            failures += attributeOrTestcaseCount(root, "failures");
            errors += attributeOrTestcaseCount(root, "errors");
            skipped += attributeOrTestcaseCount(root, "skipped");
        }
        return new RegressionCounts(tests, failures, errors, skipped, files.size());
    }

    public static void finalizeArtifacts(Path output, RegressionCounts counts) throws Exception {
        for (String name : Phase13GA2AuditArtifactWriter.finalArtifactNames()) {
            if (!Files.isRegularFile(output.resolve(name))) {
                throw new IOException("Missing V2 artifact before finalization: " + name);
            }
        }
        ObjectMapper mapper = mapper();
        Path summaryPath = output.resolve("phase13g-a-v2-structural-integrated-audit-summary.json");
        Map<String, Object> summary = mapper.readValue(summaryPath.toFile(), new TypeReference<>() { });
        List<String> blockers = strings(summary.get("blockerCodes"));
        if (counts.failures() > 0 || counts.errors() > 0) {
            blockers.add("BLOCKED_BY_PHASE_13G_A_V2_BACKEND_REGRESSION");
        }
        blockers = blockers.stream().distinct().sorted().toList();
        List<String> reviews = strings(summary.get("reviewCodes"));
        summary.put("backendTests", counts.tests());
        summary.put("backendFailures", counts.failures());
        summary.put("backendErrors", counts.errors());
        summary.put("backendSkipped", counts.skipped());
        summary.put("backendResultXmlFileCount", counts.xmlFileCount());
        summary.put("backendRegression", Map.of(
                "tests", counts.tests(), "failures", counts.failures(), "errors", counts.errors(),
                "skipped", counts.skipped(), "xmlFileCount", counts.xmlFileCount(),
                "source", "build/test-results/test"));
        summary.put("blockerCodes", blockers);
        summary.put("verdict", Phase13GA2StructuralIntegratedAudit.computedVerdict(blockers, reviews));
        summary.put("phase13GRealDataPopulationAllowed", blockers.isEmpty());
        summary.put("nextPhase", blockers.isEmpty()
                ? "PHASE_13G_REAL_PLAYER_DATA_POPULATION" : "PHASE_13G_A_V2_FIX_REQUIRED");
        summary.put("finalizationStatus", "FINAL");
        summary.put("finalizationOrder", List.of("AUDIT", "SEMANTIC_VALIDATION", "BACKEND_REGRESSION",
                "JUNIT_XML_AGGREGATION", "SUMMARY", "MARKDOWN", "SHA256", "SHA256_REREAD_VERIFICATION"));
        mapper.writeValue(summaryPath.toFile(), summary);

        Path markdownPath = output.resolve("phase13g-a-v2-structural-integrated-audit.md");
        String markdown = Files.readString(markdownPath, StandardCharsets.UTF_8);
        String regressionLine = "tests=" + counts.tests() + ", failures=" + counts.failures()
                + ", errors=" + counts.errors() + ", skipped=" + counts.skipped() + ".";
        markdown = markdown.replaceFirst("tests=[^\\n]*\\.", Matcher.quoteReplacement(regressionLine));
        Files.writeString(markdownPath, markdown, StandardCharsets.UTF_8);

        Phase13GA2AuditArtifactWriter.writeShaManifest(output);
        Phase13GA2AuditArtifactWriter.verifyShaManifest(output);
    }

    private static ObjectMapper mapper() {
        return new ObjectMapper()
                .enable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
                .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
                .enable(SerializationFeature.INDENT_OUTPUT);
    }

    private static long attributeOrTestcaseCount(Element root, String attribute) {
        String value = root.getAttribute(attribute);
        if (value != null && !value.isBlank()) return Long.parseLong(value);
        return root.getElementsByTagName("testcase").getLength();
    }

    private static List<String> strings(Object value) {
        if (!(value instanceof Collection<?> collection)) return new ArrayList<>();
        return collection.stream().map(String::valueOf).collect(Collectors.toCollection(ArrayList::new));
    }
}
