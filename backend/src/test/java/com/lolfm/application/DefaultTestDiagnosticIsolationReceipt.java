package com.lolfm.application;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Element;

/** Evidence that the default backend test task did not execute the large Composition diagnostic. */
public final class DefaultTestDiagnosticIsolationReceipt {
    public static final String SCHEMA = "DEFAULT_TEST_DIAGNOSTIC_ISOLATION_RECEIPT_V1";
    private DefaultTestDiagnosticIsolationReceipt() { }

    public static Receipt capture(Path backendRoot, List<String> forbiddenClasses) throws Exception {
        Path root = backendRoot.toAbsolutePath().normalize();
        Path results = root.resolve("build/test-results/test");
        List<Path> xmlFiles;
        try (var walk = Files.walk(results)) {
            xmlFiles = walk.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".xml"))
                    .sorted(Comparator.comparing(path -> path.getFileName().toString())).toList();
        }
        if (xmlFiles.isEmpty()) throw new IllegalStateException("Default test JUnit evidence is missing");
        ArrayList<String> cases = new ArrayList<>();
        int forbidden = 0;
        for (Path xml : xmlFiles) {
            var document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(xml.toFile());
            var nodes = document.getElementsByTagName("testcase");
            for (int index = 0; index < nodes.getLength(); index++) {
                Element value = (Element) nodes.item(index);
                String className = value.getAttribute("classname");
                String method = value.getAttribute("name");
                String status = value.getElementsByTagName("failure").getLength() > 0 ? "FAILURE"
                        : value.getElementsByTagName("error").getLength() > 0 ? "ERROR"
                        : value.getElementsByTagName("skipped").getLength() > 0 ? "SKIPPED" : "PASS";
                cases.add(className + '|' + method + '|' + status);
                if (forbiddenClasses.contains(className)) forbidden++;
            }
        }
        cases.sort(String::compareTo);
        String canonical = "schema=DEFAULT_TESTCASE_SET_V1\n" + String.join("\n", cases) + '\n';
        List<String> orderedForbidden = forbiddenClasses.stream().sorted().toList();
        Receipt unsigned = new Receipt(SCHEMA, "test", "diagnostic", "EXCLUDED",
                orderedForbidden, cases.size(), forbidden,
                DiagnosticDependencyManifest.sha256(canonical.getBytes(StandardCharsets.UTF_8)), "");
        return withPayloadHash(unsigned);
    }

    public static void verify(Receipt receipt) {
        if (!SCHEMA.equals(receipt.schemaVersion()) || !"test".equals(receipt.gradleTask())
                || !"diagnostic".equals(receipt.excludedTag())
                || !"EXCLUDED".equals(receipt.expectedDisposition())
                || receipt.executedForbiddenTestCount() != 0 || receipt.totalDefaultTestCases() <= 0
                || !withPayloadHash(new Receipt(receipt.schemaVersion(), receipt.gradleTask(),
                receipt.excludedTag(), receipt.expectedDisposition(), receipt.forbiddenTestClasses(),
                receipt.totalDefaultTestCases(), receipt.executedForbiddenTestCount(),
                receipt.canonicalJunitEvidenceHash(), "")).payloadSha256()
                .equals(receipt.payloadSha256())) {
            throw new IllegalArgumentException("Default test diagnostic isolation proof mismatch");
        }
    }

    private static Receipt withPayloadHash(Receipt value) {
        String canonical = "schema=" + value.schemaVersion() + '\n'
                + "task=" + value.gradleTask() + '\n'
                + "excludedTag=" + value.excludedTag() + '\n'
                + "disposition=" + value.expectedDisposition() + '\n'
                + "forbidden=" + String.join(",", value.forbiddenTestClasses()) + '\n'
                + "total=" + value.totalDefaultTestCases() + '\n'
                + "executedForbidden=" + value.executedForbiddenTestCount() + '\n'
                + "junit=" + value.canonicalJunitEvidenceHash() + '\n';
        return new Receipt(value.schemaVersion(), value.gradleTask(), value.excludedTag(),
                value.expectedDisposition(), value.forbiddenTestClasses(),
                value.totalDefaultTestCases(), value.executedForbiddenTestCount(),
                value.canonicalJunitEvidenceHash(), DiagnosticDependencyManifest.sha256(
                canonical.getBytes(StandardCharsets.UTF_8)));
    }

    public record Receipt(String schemaVersion, String gradleTask, String excludedTag,
                          String expectedDisposition, List<String> forbiddenTestClasses,
                          int totalDefaultTestCases, int executedForbiddenTestCount,
                          String canonicalJunitEvidenceHash, String payloadSha256) {
        public Receipt { forbiddenTestClasses = List.copyOf(forbiddenTestClasses); }
    }
}
