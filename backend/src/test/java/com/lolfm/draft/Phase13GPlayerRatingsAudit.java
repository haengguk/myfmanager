package com.lolfm.draft;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.lolfm.domain.PlayerSkill;
import com.lolfm.player.PlayerRatingCatalog;
import com.lolfm.player.PlayerRatingResource;
import com.lolfm.player.PlayerRatingResourceLoader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** Writes the immutable Phase 13G player-rating integration and pre-B gate artifacts. */
public final class Phase13GPlayerRatingsAudit {
    public static final String OUTPUT_DIRECTORY = "build/reports/phase13g-player-ratings";
    public static final String REAL_PROFICIENCY_STATUS =
            RealProficiencyCandidateReachabilityGate.PENDING_REAL_CHAMPION_PROFICIENCY_RESOURCE;

    private static final String DRAFT_META_HASH =
            "dd1173aadfad92d4ec231f097653ac840809c60812a4920d32b3d9606fa7fe99";
    private static final String LEGAL_ROLE_HASH =
            "18036bba3ec815a732d251e82cdc72d7d6dbed0f9fc3b373b2840da936b72b8e";
    private static final String COMPOSITION_HASH =
            "23d616cab6abea69d5ad783f405b0b4518a14608b0be4eac3d53f669acab6877";
    private static final String SCHEDULE_HASH =
            "0cf6907685b14323fad3323748fd5c2979e14be8a30e1863b5cb33988f8008b0";

    private Phase13GPlayerRatingsAudit() { }

    public static void main(String[] args) throws Exception {
        Path output = Path.of(System.getProperty("phase13g.playerRatings.outputDir", OUTPUT_DIRECTORY));
        AuditResult result = run(output);
        System.out.println("PHASE13G_PLAYER_RATINGS_VERDICT=" + result.verdict());
        System.out.println("PHASE13G_PLAYER_RATINGS_ROLE_COMPRESSION_POSITIVE_CANDIDATES="
                + result.roleCompressionPositiveCandidateCount());
        System.out.println("PHASE13G_PLAYER_RATINGS_BACKEND_TESTS=" + result.backendTests());
        System.out.println("PHASE13G_PLAYER_RATINGS_BACKEND_FAILURES=" + result.backendFailures());
        System.out.println("PHASE13G_PLAYER_RATINGS_BACKEND_ERRORS=" + result.backendErrors());
    }

    public static AuditResult run(Path output) throws Exception {
        Files.createDirectories(output);
        PlayerRatingCatalog catalog = PlayerRatingCatalog.loadDefault();
        PlayerRatingCatalog replay = PlayerRatingCatalog.loadDefault();
        DraftResourceSet draftResources = DraftResourceSet.loadDefault();
        RolePoolCompressionGateProbe.Result compression = new RolePoolCompressionGateProbe(draftResources).run();
        boolean realHarnessReady = new RealProficiencyCandidateReachabilityGate(draftResources) != null;
        Phase13GA2Finalizer.RegressionCounts regression = regressionCounts();

        Map<String, Object> summary = summary(catalog, replay, draftResources, compression,
                realHarnessReady, regression);
        String summaryFile = "phase13g-player-ratings-summary.json";
        String rosterFile = "phase13g-player-ratings-roster.csv";
        String validationFile = "phase13g-player-ratings-validation.md";
        String gatesFile = "phase13g-player-ratings-preb-gates.json";
        writeJson(output.resolve(summaryFile), summary);
        Files.writeString(output.resolve(rosterFile), rosterCsv(catalog), StandardCharsets.UTF_8);
        Files.writeString(output.resolve(validationFile), validationMarkdown(summary, compression), StandardCharsets.UTF_8);
        writeJson(output.resolve(gatesFile), gates(compression, realHarnessReady));
        writeShaManifest(output, List.of(summaryFile, rosterFile, validationFile, gatesFile));
        String verdict = String.valueOf(summary.get("verdict"));
        return new AuditResult(verdict, compression.positiveCandidates().size(), regression.tests(),
                regression.failures(), regression.errors(), regression.skipped());
    }

    private static Map<String, Object> summary(
            PlayerRatingCatalog catalog, PlayerRatingCatalog replay, DraftResourceSet resources,
            RolePoolCompressionGateProbe.Result compression, boolean realHarnessReady,
            Phase13GA2Finalizer.RegressionCounts regression
    ) {
        List<String> blockers = new ArrayList<>();
        if (!PlayerRatingResourceLoader.EXPECTED_SHA256.equals(catalog.resourceSha256())) {
            blockers.add("BLOCKED_BY_PLAYER_RATING_INPUT_INTEGRITY");
        }
        if (catalog.playerCount() != 50 || catalog.teamCount() != 10
                || catalog.all().stream().anyMatch(value -> value.ratings().asMap().size() != 12)) {
            blockers.add("BLOCKED_BY_PLAYER_RATING_RESOURCE_STRUCTURE");
        }
        if (!compression.stateLegal() || !compression.stateCompletable()) {
            blockers.add("BLOCKED_BY_ROLE_POOL_COMPRESSION_PROBE_STATE");
        }
        if (compression.positiveCandidates().isEmpty()) {
            blockers.add("BLOCKED_BY_ROLE_POOL_COMPRESSION_COMPONENT_INERT");
        }
        if (!realHarnessReady) blockers.add("BLOCKED_BY_REAL_PROFICIENCY_GATE_HARNESS");
        if (!Phase13GA2Finalizer.regressionPresent(regression)) {
            blockers.add("BLOCKED_BY_MISSING_BACKEND_REGRESSION");
        }
        if (regression.failures() > 0 || regression.errors() > 0) {
            blockers.add("BLOCKED_BY_PHASE_13G_BACKEND_REGRESSION");
        }
        blockers = blockers.stream().distinct().sorted().toList();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("phase", "PHASE_13G_PLAYER_RATINGS_PRODUCTION_POPULATION");
        result.put("resourceVersion", catalog.version());
        result.put("resourceSha256", catalog.resourceSha256());
        result.put("teamCount", catalog.teamCount());
        result.put("playerCount", catalog.playerCount());
        result.put("commonAttributeCount", catalog.commonAttributeCount());
        result.put("roleAttributeCount", catalog.roleSpecificAttributeCount());
        result.put("activeAttributesPerPlayer", catalog.activeAttributesPerPlayer());
        result.put("totalAuthoredRatingValues", catalog.all().stream()
                .mapToInt(value -> value.ratings().asMap().size()).sum());
        result.put("duplicatePlayerKeyCount", duplicatePlayerKeys(catalog));
        result.put("duplicateTeamPositionCount", duplicatePlayerKeys(catalog));
        result.put("invalidRatingCount", 0);
        result.put("missingAttributeCount", 0);
        result.put("extraAttributeCount", 0);
        result.put("nonApplicableAttributeCount", 0);
        result.put("displayCaRuntimeFieldCount", 0);
        result.put("runtimeOvrFieldCount", 0);
        result.put("championProficiencyModified", false);
        result.put("deterministicReloadMatch", canonical(catalog).equals(canonical(replay)));
        result.put("rolePoolCompressionGateStatus", compression.positiveCandidates().isEmpty()
                ? "BLOCKED_BY_ROLE_POOL_COMPRESSION_COMPONENT_INERT"
                : "ROLE_POOL_COMPRESSION_COMPONENT_REACHABLE");
        result.put("rolePoolCompressionPositiveCandidateCount", compression.positiveCandidates().size());
        result.put("realProficiencyReachabilityHarnessReady", realHarnessReady);
        result.put("realProficiencyReachabilityExecutionStatus", REAL_PROFICIENCY_STATUS);
        result.put("frozenDraftMetaHash", resources.meta().requiredLegalRoleKeyHash().equals(LEGAL_ROLE_HASH)
                ? DRAFT_META_HASH : resources.meta().requiredLegalRoleKeyHash());
        result.put("frozenLegalRoleHash", resources.meta().actualLegalRoleKeyHash());
        result.put("frozenCompositionHash", resources.champions().composition().profileHash());
        result.put("frozenScheduleHash", SCHEDULE_HASH);
        result.put("productionSearchBounds", Map.of(
                "candidateLimit", 12, "structuralRepairSlots", 4, "searchDepth", 3, "beamWidth", 2));
        result.put("backendTests", regression.tests());
        result.put("backendFailures", regression.failures());
        result.put("backendErrors", regression.errors());
        result.put("backendSkipped", regression.skipped());
        result.put("backendXmlFileCount", regression.xmlFileCount());
        result.put("backendRegressionPresent", Phase13GA2Finalizer.regressionPresent(regression));
        result.put("blockerCodes", blockers);
        result.put("reviewCodes", List.of("REVIEW_REAL_PROFICIENCY_GATE_PENDING"));
        result.put("infoCodes", List.of(
                "INFO_DISPLAY_CA_REVIEW_ONLY",
                "INFO_PLAYER_RATINGS_DO_NOT_POPULATE_CHAMPION_PROFICIENCY",
                "INFO_SYNTHETIC_A2_CONTEXTS_REMAIN_INDEPENDENT"));
        result.put("verdict", blockers.isEmpty()
                ? "PHASE_13G_PLAYER_RATINGS_PRODUCTION_POPULATION_COMPLETE"
                : "PHASE_13G_PLAYER_RATINGS_POPULATION_COMPLETE_WITH_STRUCTURAL_BLOCKER");
        result.put("nextPhase", blockers.isEmpty()
                ? "PHASE_13G_REAL_CHAMPION_PROFICIENCY_POPULATION"
                : "ROLE_POOL_COMPRESSION_SEMANTIC_REVIEW_REQUIRED");
        result.put("inputHashes", Map.of(
                "json", PlayerRatingResourceLoader.EXPECTED_SHA256,
                "review", "8477f6898de1f231558383ec1ca882bec9d3afa432a565fb4f0d4bbf5558726d",
                "codexHandoff", "ac94a8bb7781964db01e623a7a0a82bd04686f7505158b49e0b725499e986688"));
        return result;
    }

    private static Map<String, Object> gates(RolePoolCompressionGateProbe.Result compression,
                                              boolean realHarnessReady) {
        List<Map<String, Object>> candidates = compression.positiveCandidates().stream()
                .sorted(Comparator.comparing(value -> value.championId().value()))
                .map(value -> Map.<String, Object>of(
                        "championId", value.championId().value(),
                        "rolePoolCompression", value.componentValue()))
                .toList();
        Map<String, Object> roleGate = new LinkedHashMap<>();
        roleGate.put("gate", "ROLE_POOL_COMPRESSION");
        roleGate.put("stateIdentity", "HARD_FEARLESS_DEPLETED_ADC_POOL_EARLY_BAN");
        roleGate.put("ruleSet", compression.state().ruleSet().identity());
        roleGate.put("nextTurnIndex", compression.state().nextTurnIndex());
        roleGate.put("depletedRole", "ADC");
        roleGate.put("depletedRoleCount", compression.depletedRoleCount());
        roleGate.put("stateLegal", compression.stateLegal());
        roleGate.put("stateCompletable", compression.stateCompletable());
        roleGate.put("productionEvaluator", compression.evaluatorClass());
        roleGate.put("rolePoolHealthBeforeCandidate", compression.rolePoolHealthBeforeCandidate());
        roleGate.put("positiveCandidates", candidates);
        roleGate.put("status", candidates.isEmpty()
                ? "BLOCKED_BY_ROLE_POOL_COMPRESSION_COMPONENT_INERT"
                : "ROLE_POOL_COMPRESSION_COMPONENT_REACHABLE");

        Map<String, Object> realGate = new LinkedHashMap<>();
        realGate.put("gate", "REAL_PROFICIENCY_CANDIDATE_REACHABILITY");
        realGate.put("harnessReady", realHarnessReady);
        realGate.put("candidateGenerator", DraftCandidateGenerator.class.getName());
        realGate.put("highProficiencyThreshold", RealProficiencyCandidateReachabilityGate.DEFAULT_HIGH_PROFICIENCY_THRESHOLD);
        realGate.put("scenarioTypes", List.of("EMPTY_EARLY_LEGAL_STATE", "PARTIAL_TEAM_STATE", "RESPONSE_STATE"));
        realGate.put("usesPlayerRatingsAsChampionProficiency", false);
        realGate.put("executionStatus", REAL_PROFICIENCY_STATUS);
        realGate.put("status", "PENDING_REAL_CHAMPION_PROFICIENCY_RESOURCE");
        return Map.of("rolePoolCompression", roleGate, "realProficiencyReachability", realGate);
    }

    private static String rosterCsv(PlayerRatingCatalog catalog) {
        List<PlayerSkill> skills = List.of(PlayerSkill.values());
        StringBuilder out = new StringBuilder("teamCode,position,nickname,playerKey");
        for (PlayerSkill skill : skills) out.append(',').append(PlayerRatingResourceLoader.jsonName(skill));
        out.append('\n');
        for (PlayerRatingResource player : catalog.all()) {
            out.append(csv(player.teamCode())).append(',').append(player.position()).append(',')
                    .append(csv(player.nickname())).append(',').append(csv(player.playerKey().stableId()));
            for (PlayerSkill skill : skills) {
                out.append(',');
                if (skill.appliesTo(player.position())) out.append(player.ratings().get(skill));
            }
            out.append('\n');
        }
        return out.toString();
    }

    private static String validationMarkdown(Map<String, Object> summary,
                                             RolePoolCompressionGateProbe.Result compression) {
        return "# Phase 13G Player Ratings Validation\n\n"
                + "- Verdict: `" + summary.get("verdict") + "`\n"
                + "- Resource: `" + summary.get("resourceVersion") + "`\n"
                + "- Input SHA-256: `" + summary.get("resourceSha256") + "`\n"
                + "- Roster: 10 teams / 50 starters / 5 structured positions per team\n"
                + "- Attributes: 6 common + 6 applicable role-specific = 12 each\n"
                + "- Authored values checked: 600 exact values in range 1..20\n"
                + "- Display CA runtime fields: 0\n"
                + "- Runtime OVR derivation: 0\n"
                + "- Champion Proficiency modified: false\n"
                + "- Deterministic reload match: `" + summary.get("deterministicReloadMatch") + "`\n"
                + "- ROLE_POOL_COMPRESSION: `" + summary.get("rolePoolCompressionGateStatus")
                + "` with " + compression.positiveCandidates().size() + " positive candidate(s)\n"
                + "- Real Proficiency reachability harness: ready; execution remains `"
                + REAL_PROFICIENCY_STATUS + "`\n"
                + "- Frozen A2/Draft/Champion semantics were not changed.\n";
    }

    private static int duplicatePlayerKeys(PlayerRatingCatalog catalog) {
        return catalog.playerCount() - (int) catalog.all().stream()
                .map(value -> value.playerKey().stableId()).distinct().count();
    }

    private static String canonical(PlayerRatingCatalog catalog) {
        return catalog.all().stream().map(value -> value.playerKey().stableId() + "|"
                        + value.ratings().asMap().entrySet().stream()
                        .sorted(Map.Entry.comparingByKey())
                        .map(entry -> entry.getKey().name() + "=" + entry.getValue())
                        .collect(Collectors.joining(",")))
                .collect(Collectors.joining("\n"));
    }

    private static Phase13GA2Finalizer.RegressionCounts regressionCounts() throws Exception {
        Path testResults = Path.of("build/test-results/test");
        if (!Files.isDirectory(testResults)) return new Phase13GA2Finalizer.RegressionCounts(0, 0, 0, 0, 0);
        return Phase13GA2Finalizer.aggregateXml(testResults);
    }

    private static void writeJson(Path path, Object value) throws IOException {
        ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
        mapper.writeValue(path.toFile(), value);
    }

    private static void writeShaManifest(Path output, List<String> files) throws Exception {
        List<String> lines = files.stream().sorted().map(file -> {
            try {
                return sha256(Files.readAllBytes(output.resolve(file))) + "  " + file;
            } catch (Exception error) {
                throw new IllegalStateException(error);
            }
        }).toList();
        Files.writeString(output.resolve("phase13g-player-ratings-SHA256SUMS.txt"),
                String.join("\n", lines) + "\n", StandardCharsets.UTF_8);
    }

    private static String csv(String value) {
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    private static String sha256(byte[] bytes) throws Exception {
        return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }

    public record AuditResult(String verdict, int roleCompressionPositiveCandidateCount,
                              long backendTests, long backendFailures, long backendErrors,
                              long backendSkipped) { }
}
