package com.lolfm.application;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Element;

/** Canonical PASS evidence captured from an exact focused Gradle Test execution. */
public final class FocusedInvariantProofReceipt {
    public static final String SCHEMA = "FOCUSED_INVARIANT_PROOF_RECEIPT_V1";
    public static final String PASS = "PASS";
    private static final Map<String, ProofContract> PRODUCTION_PROOFS = Map.of(
            "com.lolfm.composition.CompositionProductionApplicationProvenanceTest#offAndFreshMatchStateRemainExactZeroAndIsolated",
            new ProofContract("verifyCompositionV9CausalityFocusedProof",
                    "src/test/java/com/lolfm/composition/CompositionProductionApplicationProvenanceTest.java"),
            "com.lolfm.composition.CompositionProductionApplicationProvenanceTest#unsupportedContextIsStructuredDisabledAndCannotReachConsumer",
            new ProofContract("verifyCompositionV9CausalityFocusedProof",
                    "src/test/java/com/lolfm/composition/CompositionProductionApplicationProvenanceTest.java"),
            "com.lolfm.simulator.StructureEngineRedesignTest#sameDisplayNamesCannotBorrowOpponentsRecentAce",
            new ProofContract("verifyMatchupV9AttributionFocusedProof",
                    "src/test/java/com/lolfm/simulator/StructureEngineRedesignTest.java"),
            "com.lolfm.simulator.StructureEngineRedesignTest#twoPostFightAttackersCannotFallThroughIntoBaseAndConsumeNoRandom",
            new ProofContract("verifyMatchupV9AttributionFocusedProof",
                    "src/test/java/com/lolfm/simulator/StructureEngineRedesignTest.java"));
    private FocusedInvariantProofReceipt() { }

    public static Receipt capture(Path backendRoot, String gradleTask, String exactSelector,
                                  String testSourceLogicalPath, String productionGuardHash)
            throws Exception {
        Path root = backendRoot.toAbsolutePath().normalize();
        String[] selector = splitSelector(exactSelector);
        Path source = root.resolve(testSourceLogicalPath).normalize();
        if (!source.startsWith(root) || !Files.isRegularFile(source)) {
            throw new IllegalArgumentException("Focused proof source is missing");
        }
        String sourceText = Files.readString(source, StandardCharsets.UTF_8);
        String declaredPackage = declaredPackage(sourceText);
        String declaredClass = declaredPackage.isEmpty() ? simpleName(selector[0])
                : declaredPackage + "." + simpleName(selector[0]);
        if (!declaredClass.equals(selector[0])
                || !sourceText.contains("class " + simpleName(selector[0]))
                || !sourceText.matches("(?s).*\\b" + java.util.regex.Pattern.quote(selector[1])
                + "\\s*\\(.*")) {
            throw new IllegalArgumentException("Focused proof class/method does not exist");
        }
        String resultsLogicalPath = "build/test-results/" + gradleTask;
        Path results = root.resolve(resultsLogicalPath);
        List<Path> xmlFiles;
        try (var walk = Files.walk(results)) {
            xmlFiles = walk.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".xml"))
                    .sorted(Comparator.comparing(path -> portable(results.relativize(path))))
                    .toList();
        }
        if (xmlFiles.isEmpty()) throw new IllegalStateException("Focused proof JUnit XML is missing");
        ArrayList<TestCaseResult> matched = new ArrayList<>();
        StringBuilder rawIdentity = new StringBuilder("schema=RAW_JUNIT_XML_SET_V1\n");
        for (Path xml : xmlFiles) {
            byte[] raw = Files.readAllBytes(xml);
            rawIdentity.append(portable(results.relativize(xml))).append('|')
                    .append(DiagnosticDependencyManifest.sha256(raw)).append('\n');
            var document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(xml.toFile());
            var cases = document.getElementsByTagName("testcase");
            for (int index = 0; index < cases.getLength(); index++) {
                Element value = (Element) cases.item(index);
                String className = value.getAttribute("classname");
                String method = normalizeMethod(value.getAttribute("name"));
                if (!className.equals(selector[0]) || !method.equals(selector[1])) continue;
                String status = childCount(value, "failure") > 0 ? "FAILURE"
                        : childCount(value, "error") > 0 ? "ERROR"
                        : childCount(value, "skipped") > 0 ? "SKIPPED" : PASS;
                matched.add(new TestCaseResult(className, method, status));
            }
        }
        if (matched.size() != 1) {
            throw new IllegalStateException("Focused proof selector coverage is not exact");
        }
        TestCaseResult result = matched.getFirst();
        int failures = result.status().equals("FAILURE") ? 1 : 0;
        int errors = result.status().equals("ERROR") ? 1 : 0;
        int skipped = result.status().equals("SKIPPED") ? 1 : 0;
        String canonicalEvidence = canonicalJunitHash(
                result.testClass(), result.testMethod(), result.status());
        Receipt unsigned = new Receipt(SCHEMA, result.testClass(), result.testMethod(),
                exactSelector, testSourceLogicalPath,
                DiagnosticDependencyManifest.sha256(Files.readAllBytes(source)), gradleTask,
                "--tests '" + exactSelector.replace('#', '.') + "'", resultsLogicalPath,
                DiagnosticDependencyManifest.sha256(("task=" + gradleTask + "\nresults="
                        + resultsLogicalPath + '\n').getBytes(StandardCharsets.UTF_8)),
                productionGuardHash, 1, failures, errors, skipped,
                failures + errors + skipped == 0 ? PASS : result.status(), canonicalEvidence,
                DiagnosticDependencyManifest.sha256(rawIdentity.toString()
                        .getBytes(StandardCharsets.UTF_8)), "");
        return withPayloadHash(unsigned);
    }

    public static void verify(Receipt receipt, String productionGuardHash,
                              DiagnosticDependencyManifest.Manifest dependencies) {
        Objects.requireNonNull(receipt, "receipt");
        ProofContract proofContract = expectedProofContract(receipt, dependencies);
        String expectedGradleSelector = "--tests '" + receipt.exactSelector().replace('#', '.') + "'";
        String expectedTaskIdentity = hash("task=" + receipt.gradleTask() + "\nresults="
                + receipt.resultsLogicalPath() + '\n');
        String expectedCanonicalJunit = canonicalJunitHash(
                receipt.testClass(), receipt.testMethod(), PASS);
        if (!SCHEMA.equals(receipt.schemaVersion()) || !PASS.equals(receipt.normalizedResult())
                || receipt.tests() != 1 || receipt.failures() != 0 || receipt.errors() != 0
                || receipt.skipped() != 0 || !receipt.productionGuardHash().equals(productionGuardHash)
                || !receipt.resultsLogicalPath().equals("build/test-results/" + receipt.gradleTask())
                || !receipt.exactSelector().equals(receipt.testClass() + "#" + receipt.testMethod())
                || !receipt.gradleTask().equals(proofContract.gradleTask())
                || !receipt.testSourceLogicalPath().equals(proofContract.sourceLogicalPath())
                || !receipt.gradleSelector().equals(expectedGradleSelector)
                || !receipt.gradleTaskIdentityHash().equals(expectedTaskIdentity)
                || !receipt.canonicalJunitEvidenceHash().equals(expectedCanonicalJunit)) {
            throw new IllegalArgumentException("Focused proof receipt result/task/selector mismatch");
        }
        DiagnosticDependencyManifest.Entry source =
                DiagnosticDependencyManifest.requireDependency(
                        dependencies, receipt.testSourceLogicalPath());
        if (!source.rawSha256().equals(receipt.testSourceSha256())) {
            throw new IllegalArgumentException("Focused proof test source SHA mismatch");
        }
        requireHash(receipt.gradleTaskIdentityHash());
        requireHash(receipt.productionGuardHash());
        requireHash(receipt.canonicalJunitEvidenceHash());
        requireHash(receipt.rawJunitXmlSetSha256());
        if (!withPayloadHash(new Receipt(receipt.schemaVersion(), receipt.testClass(),
                receipt.testMethod(), receipt.exactSelector(), receipt.testSourceLogicalPath(),
                receipt.testSourceSha256(), receipt.gradleTask(), receipt.gradleSelector(),
                receipt.resultsLogicalPath(), receipt.gradleTaskIdentityHash(),
                receipt.productionGuardHash(), receipt.tests(), receipt.failures(), receipt.errors(),
                receipt.skipped(), receipt.normalizedResult(), receipt.canonicalJunitEvidenceHash(),
                receipt.rawJunitXmlSetSha256(), "")).proofReceiptPayloadSha256()
                .equals(receipt.proofReceiptPayloadSha256())) {
            throw new IllegalArgumentException("Focused proof receipt payload mismatch");
        }
    }

    static Receipt syntheticPassing(String testClass, String method, String sourcePath,
                                    String sourceSha, String task, String productionHash) {
        String selector = testClass + "#" + method;
        String results = "build/test-results/" + task;
        Receipt unsigned = new Receipt(SCHEMA, testClass, method, selector, sourcePath,
                sourceSha, task, "--tests '" + selector.replace('#', '.') + "'", results,
                hash("task=" + task + "\nresults=" + results + '\n'), productionHash,
                1, 0, 0, 0, PASS, canonicalJunitHash(testClass, method, PASS),
                hash("raw " + selector), "");
        return withPayloadHash(unsigned);
    }

    private static ProofContract expectedProofContract(
            Receipt receipt, DiagnosticDependencyManifest.Manifest dependencies) {
        ProofContract production = PRODUCTION_PROOFS.get(receipt.exactSelector());
        if (production != null) return production;
        if ("TEST".equals(dependencies.manifestId())) {
            return new ProofContract("focusedProof", receipt.testSourceLogicalPath());
        }
        if ("SYNTHETIC_DEPENDENCIES".equals(dependencies.manifestId())
                && receipt.testSourceLogicalPath().startsWith("synthetic/")) {
            return new ProofContract("syntheticFocusedProof", receipt.testSourceLogicalPath());
        }
        throw new IllegalArgumentException("Focused proof selector is not predeclared");
    }

    private static Receipt withPayloadHash(Receipt value) {
        String canonical = "schema=" + value.schemaVersion() + '\n'
                + "testClass=" + value.testClass() + '\n'
                + "testMethod=" + value.testMethod() + '\n'
                + "selector=" + value.exactSelector() + '\n'
                + "source=" + value.testSourceLogicalPath() + '\n'
                + "sourceSha=" + value.testSourceSha256() + '\n'
                + "task=" + value.gradleTask() + '\n'
                + "gradleSelector=" + value.gradleSelector() + '\n'
                + "results=" + value.resultsLogicalPath() + '\n'
                + "taskIdentity=" + value.gradleTaskIdentityHash() + '\n'
                + "production=" + value.productionGuardHash() + '\n'
                + "counts=" + value.tests() + '|' + value.failures() + '|'
                + value.errors() + '|' + value.skipped() + '\n'
                + "result=" + value.normalizedResult() + '\n'
                + "canonicalJunit=" + value.canonicalJunitEvidenceHash() + '\n'
                + "rawJunit=" + value.rawJunitXmlSetSha256() + '\n';
        return new Receipt(value.schemaVersion(), value.testClass(), value.testMethod(),
                value.exactSelector(), value.testSourceLogicalPath(), value.testSourceSha256(),
                value.gradleTask(), value.gradleSelector(), value.resultsLogicalPath(),
                value.gradleTaskIdentityHash(), value.productionGuardHash(), value.tests(),
                value.failures(), value.errors(), value.skipped(), value.normalizedResult(),
                value.canonicalJunitEvidenceHash(), value.rawJunitXmlSetSha256(), hash(canonical));
    }

    private static int childCount(Element value, String name) {
        return value.getElementsByTagName(name).getLength();
    }

    private static String normalizeMethod(String value) {
        int parameter = value.indexOf('(');
        return parameter < 0 ? value : value.substring(0, parameter);
    }

    private static String[] splitSelector(String value) {
        int separator = value.lastIndexOf('#');
        if (separator <= 0 || separator == value.length() - 1) {
            throw new IllegalArgumentException("Focused proof selector must be Class#method");
        }
        return new String[]{value.substring(0, separator), value.substring(separator + 1)};
    }

    private static String simpleName(String className) {
        int separator = className.lastIndexOf('.');
        return separator < 0 ? className : className.substring(separator + 1);
    }

    private static String declaredPackage(String sourceText) {
        var matcher = java.util.regex.Pattern.compile(
                "(?m)^\\s*package\\s+([A-Za-z_][A-Za-z0-9_.]*)\\s*;").matcher(sourceText);
        return matcher.find() ? matcher.group(1) : "";
    }

    private static String portable(Path value) {
        return value.normalize().toString().replace('\\', '/');
    }

    private static String hash(String value) {
        return DiagnosticDependencyManifest.sha256(value.getBytes(StandardCharsets.UTF_8));
    }

    private static String canonicalJunitHash(String testClass, String testMethod, String status) {
        return hash("schema=CANONICAL_JUNIT_TESTCASE_RESULT_V1\n"
                + testClass + '|' + testMethod + '|' + status + '\n');
    }

    private static void requireHash(String value) {
        if (value == null || !value.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("Focused proof SHA-256 is invalid");
        }
    }

    private record TestCaseResult(String testClass, String testMethod, String status) { }
    private record ProofContract(String gradleTask, String sourceLogicalPath) { }

    public record Receipt(String schemaVersion, String testClass, String testMethod,
                          String exactSelector, String testSourceLogicalPath,
                          String testSourceSha256, String gradleTask, String gradleSelector,
                          String resultsLogicalPath, String gradleTaskIdentityHash,
                          String productionGuardHash, int tests, int failures, int errors,
                          int skipped, String normalizedResult, String canonicalJunitEvidenceHash,
                          String rawJunitXmlSetSha256, String proofReceiptPayloadSha256) { }
}
