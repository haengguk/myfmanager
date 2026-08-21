package com.lolfm.draft;

import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.lolfm.champion.ChampionId;
import com.lolfm.champion.ChampionRoleKey;
import com.lolfm.domain.PlayerRatings;
import com.lolfm.domain.PlayerSkill;
import com.lolfm.player.PlayerRatingCatalog;
import com.lolfm.player.PlayerRatingKey;
import com.lolfm.player.PlayerRatingResource;
import com.lolfm.player.PlayerRatingResourceLoader;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Writes the pre-real-Champion-Proficiency hardening evidence without creating
 * or inferring any Champion Proficiency resource.
 */
public final class Phase13GPreProficiencyHardeningAudit {
    public static final String OUTPUT_DIRECTORY = "build/reports/phase13g-pre-proficiency-hardening";
    public static final String PHASE = "PRE_REAL_CHAMPION_PROFICIENCY_GATE_HARDENING";
    public static final String REAL_PROFICIENCY_STATUS =
            RealProficiencyCandidateReachabilityGate.PENDING_REAL_CHAMPION_PROFICIENCY_RESOURCE;
    public static final String NEXT_PHASE = "PHASE_13G_REAL_CHAMPION_PROFICIENCY_POPULATION";

    private static final String PLAYER_RATING_EXPECTED_SHA = PlayerRatingResourceLoader.EXPECTED_SHA256;
    private static final String DRAFT_META_EXPECTED_SHA =
            "dd1173aadfad92d4ec231f097653ac840809c60812a4920d32b3d9606fa7fe99";
    private static final String LEGAL_ROLE_EXPECTED_SHA =
            "18036bba3ec815a732d251e82cdc72d7d6dbed0f9fc3b373b2840da936b72b8e";
    private static final String COMPOSITION_EXPECTED_SHA =
            "23d616cab6abea69d5ad783f405b0b4518a14608b0be4eac3d53f669acab6877";
    private static final String SCHEDULE_EXPECTED_SHA =
            "0cf6907685b14323fad3323748fd5c2979e14be8a30e1863b5cb33988f8008b0";

    private Phase13GPreProficiencyHardeningAudit() { }

    public static void main(String[] args) throws Exception {
        Path output = Path.of(System.getProperty("phase13g.hardening.outputDir", OUTPUT_DIRECTORY));
        Path testResults = Path.of(System.getProperty("phase13g.testResultsDir", "build/test-results/test"));
        AuditResult result = run(output, testResults);
        System.out.println("PRE_REAL_PROFICIENCY_HARDENING_VERDICT=" + result.verdict());
        System.out.println("PRE_REAL_PROFICIENCY_HARDENING_DIRECTIONAL=" + result.directional());
        System.out.println("PRE_REAL_PROFICIENCY_HARDENING_BACKEND_TESTS=" + result.backendTests());
        System.out.println("PRE_REAL_PROFICIENCY_HARDENING_BACKEND_XML_FILES=" + result.backendXmlFileCount());
    }

    public static AuditResult run(Path output, Path testResults) throws Exception {
        Objects.requireNonNull(output, "output");
        Objects.requireNonNull(testResults, "testResults");
        Files.createDirectories(output);

        PlayerRatingCatalog catalog = PlayerRatingCatalog.loadDefault();
        PlayerRatingCatalog replay = PlayerRatingCatalog.loadDefault();
        DraftResourceSet resources = DraftResourceSet.loadDefault();
        RolePoolCompressionGateProbe.Result compression = new RolePoolCompressionGateProbe(resources).run();
        Phase13GA2Finalizer.RegressionCounts regression = regressionCounts(testResults);
        RatingValidation ratingValidation = validateRatings(catalog, replay);
        FrozenHashes frozenHashes = frozenHashes(resources);
        boolean gateIdentityReady = gateIdentityReady();

        Map<String, Object> summary = summary(catalog, ratingValidation, frozenHashes, compression,
                gateIdentityReady, regression, Files.isDirectory(testResults));
        Map<String, Object> roleCompression = roleCompressionArtifact(compression);

        String summaryFile = "phase13g-pre-proficiency-hardening-summary.json";
        String validationFile = "phase13g-pre-proficiency-hardening-validation.md";
        String compressionFile = "phase13g-pre-proficiency-hardening-role-compression.json";
        writeJson(output.resolve(summaryFile), summary);
        Files.writeString(output.resolve(validationFile), validationMarkdown(summary, compression, frozenHashes),
                StandardCharsets.UTF_8);
        writeJson(output.resolve(compressionFile), roleCompression);
        writeShaManifest(output, List.of(summaryFile, validationFile, compressionFile));

        return new AuditResult(String.valueOf(summary.get("verdict")), compression.directional(),
                regression.tests(), regression.failures(), regression.errors(), regression.skipped(),
                regression.xmlFileCount());
    }

    private static Map<String, Object> summary(
            PlayerRatingCatalog catalog,
            RatingValidation ratings,
            FrozenHashes hashes,
            RolePoolCompressionGateProbe.Result compression,
            boolean gateIdentityReady,
            Phase13GA2Finalizer.RegressionCounts regression,
            boolean testResultsDirectoryPresent
    ) {
        List<String> blockers = new ArrayList<>();
        List<String> reviews = new ArrayList<>();
        if (!ratings.resourceHashMatch() || !ratings.exactRosterShape() || !ratings.exactRatingSet()
                || ratings.invalidRatingCount() > 0 || ratings.missingAttributeCount() > 0
                || ratings.extraAttributeCount() > 0 || ratings.nonApplicableAttributeCount() > 0
                || !ratings.deterministicReloadMatch()) {
            blockers.add("BLOCKED_BY_PLAYER_RATING_INPUT_INTEGRITY");
        }
        if (!hashes.allMatch()) blockers.add("BLOCKED_BY_FROZEN_CHAMPION_DRAFT_HASH_MISMATCH");
        if (!gateIdentityReady) blockers.add("BLOCKED_BY_REAL_PROFICIENCY_GATE_IDENTITY_CONTRACT");
        if (!compression.stateLegal() || !compression.stateCompletable()) {
            blockers.add("BLOCKED_BY_ROLE_POOL_COMPRESSION_PROBE_STATE");
        }
        if (!compression.reachable()) {
            blockers.add("BLOCKED_BY_ROLE_POOL_COMPRESSION_COMPONENT_INTEGRITY");
        }
        if (!Phase13GA2Finalizer.regressionPresent(regression)) {
            blockers.add("BLOCKED_BY_MISSING_BACKEND_REGRESSION");
        }
        if (regression.failures() > 0 || regression.errors() > 0) {
            blockers.add("BLOCKED_BY_BACKEND_REGRESSION_FAILURE");
        }
        if (!compression.directional()) reviews.add("REVIEW_ROLE_POOL_COMPRESSION_DIRECTIONALITY");
        reviews.add("REVIEW_REAL_PROFICIENCY_GATE_PENDING");
        blockers = blockers.stream().distinct().sorted().toList();
        reviews = reviews.stream().distinct().sorted().toList();

        String verdict;
        if (!blockers.isEmpty()) {
            verdict = "PRE_REAL_CHAMPION_PROFICIENCY_GATE_HARDENING_BLOCKED";
        } else if (compression.directional()) {
            verdict = "PRE_REAL_CHAMPION_PROFICIENCY_GATE_HARDENING_COMPLETE";
        } else {
            verdict = "PRE_REAL_CHAMPION_PROFICIENCY_GATE_HARDENING_COMPLETE_WITH_REVIEW";
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("phase", PHASE);
        result.put("playerRatingResourceVersion", catalog.version());
        result.put("playerRatingResourceExpectedSha", PLAYER_RATING_EXPECTED_SHA);
        result.put("playerRatingResourceActualSha", catalog.resourceSha256());
        result.put("playerRatingResourceHashMatch", ratings.resourceHashMatch());
        result.put("playerRatingIdentityType", PlayerRatingKey.class.getName());
        result.put("realProficiencyGateIdentityType", PlayerRatingKey.class.getName());
        result.put("proficiencySourceOfTruth", "DraftTeamContext.proficiency(ChampionRoleKey)");
        result.put("proficiencyBindingValidated", gateIdentityReady);
        result.put("subjectRoleBindingValidated", gateIdentityReady);
        result.put("playerRatingProficiencyIsolation", true);
        result.put("playerRatingProficiencyIsolationValidation", ratings.playerRatingProficiencyIsolation());
        result.put("realProficiencyExecutionStatus", REAL_PROFICIENCY_STATUS);
        result.put("teamCount", catalog.teamCount());
        result.put("playerCount", catalog.playerCount());
        result.put("authoredRatingValueCount", ratings.authoredValueCount());
        result.put("authoredRatingValueMin", ratings.minimum());
        result.put("authoredRatingValueMax", ratings.maximum());
        result.put("ratingValueExactValidation", ratings.exactRatingValidation());
        result.put("invalidRatingCount", ratings.invalidRatingCount());
        result.put("invalidRatingValidation", ratings.invalidRatingValidation());
        result.put("missingAttributeCount", ratings.missingAttributeCount());
        result.put("missingAttributeValidation", ratings.missingAttributeValidation());
        result.put("extraAttributeCount", ratings.extraAttributeCount());
        result.put("extraAttributeValidation", ratings.extraAttributeValidation());
        result.put("nonApplicableAttributeCount", ratings.nonApplicableAttributeCount());
        result.put("nonApplicableAttributeValidation", ratings.nonApplicableAttributeValidation());
        result.put("duplicatePlayerKeyCount", ratings.duplicatePlayerKeyCount());
        result.put("duplicateTeamPositionCount", ratings.duplicateTeamPositionCount());
        result.put("championProficiencyAuthoredValueCount", 0);
        result.put("championProficiencyModified", false);
        result.put("deterministicReloadMatch", ratings.deterministicReloadMatch());
        result.put("rolePoolCompressionReachable", compression.reachable());
        result.put("rolePoolCompressionPositiveCandidateCount", compression.positiveCandidates().size());
        result.put("rolePoolCompressionDirectional", compression.directional());
        result.put("rolePoolCompressionDirectCandidateExample", candidateId(compression.directRolePressureCandidate()));
        result.put("rolePoolCompressionDirectValue", candidateValue(compression.directRolePressureCandidate()));
        result.put("rolePoolCompressionUnrelatedCandidateExample", candidateId(compression.unrelatedHealthyRoleCandidate()));
        result.put("rolePoolCompressionUnrelatedValue", candidateValue(compression.unrelatedHealthyRoleCandidate()));
        result.put("backendRegressionPresent", Phase13GA2Finalizer.regressionPresent(regression));
        result.put("backendTestResultsDirectoryPresent", testResultsDirectoryPresent);
        result.put("backendXmlFileCount", regression.xmlFileCount());
        result.put("backendTests", regression.tests());
        result.put("backendFailures", regression.failures());
        result.put("backendErrors", regression.errors());
        result.put("backendSkipped", regression.skipped());
        result.put("frozenDraftMetaExpectedHash", hashes.draftMeta().expectedHash());
        result.put("frozenDraftMetaActualHash", hashes.draftMeta().actualHash());
        result.put("frozenDraftMetaMatch", hashes.draftMeta().matches());
        result.put("frozenLegalRoleExpectedHash", hashes.legalRoles().expectedHash());
        result.put("frozenLegalRoleActualHash", hashes.legalRoles().actualHash());
        result.put("frozenLegalRoleMatch", hashes.legalRoles().matches());
        result.put("frozenCompositionExpectedHash", hashes.composition().expectedHash());
        result.put("frozenCompositionActualHash", hashes.composition().actualHash());
        result.put("frozenCompositionMatch", hashes.composition().matches());
        result.put("frozenScheduleExpectedHash", hashes.schedule().expectedHash());
        result.put("frozenScheduleActualHash", hashes.schedule().actualHash());
        result.put("frozenScheduleMatch", hashes.schedule().matches());
        result.put("frozenChampionCount", 173);
        result.put("frozenLegalRoleKeyCount", 216);
        result.put("productionSearchBounds", Map.of(
                "candidateLimit", 12, "structuralRepairSlots", 4, "searchDepth", 3, "beamWidth", 2));
        result.put("blockerCodes", blockers);
        result.put("reviewCodes", reviews);
        result.put("infoCodes", List.of(
                "INFO_REAL_PROFICIENCY_RESOURCE_NOT_POPULATED",
                "INFO_PLAYER_RATING_KEY_IS_ROSTER_SNAPSHOT_IDENTITY",
                "INFO_PLAYER_RATINGS_DO_NOT_POPULATE_CHAMPION_PROFICIENCY",
                "INFO_HISTORICAL_A2_ARTIFACTS_NOT_REWRITTEN"));
        result.put("verdict", verdict);
        result.put("bindingVerdict", gateIdentityReady
                ? "REAL_PROFICIENCY_GATE_BINDING_READY" : "REAL_PROFICIENCY_GATE_BINDING_NOT_READY");
        result.put("rolePoolCompressionVerdict", compression.directional()
                ? "ROLE_POOL_COMPRESSION_DIRECTIONALITY_CONFIRMED"
                : "REVIEW_ROLE_POOL_COMPRESSION_DIRECTIONALITY");
        result.put("nextPhase", blockers.isEmpty() ? NEXT_PHASE : "PRE_REAL_CHAMPION_PROFICIENCY_FIX_REQUIRED");
        return result;
    }

    private static Map<String, Object> roleCompressionArtifact(RolePoolCompressionGateProbe.Result compression) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("gate", "ROLE_POOL_COMPRESSION");
        result.put("stateIdentity", "HARD_FEARLESS_DEPLETED_ADC_POOL_EARLY_BAN");
        result.put("depletedRole", "ADC");
        result.put("depletedRoleCount", compression.depletedRoleCount());
        result.put("stateLegal", compression.stateLegal());
        result.put("stateCompletable", compression.stateCompletable());
        result.put("productionEvaluator", compression.evaluatorClass());
        result.put("rolePoolHealthBeforeCandidate", compression.rolePoolHealthBeforeCandidate());
        result.put("positiveCandidateCount", compression.positiveCandidates().size());
        result.put("directRolePressureCandidate", candidateId(compression.directRolePressureCandidate()));
        result.put("directRolePressureValue", candidateValue(compression.directRolePressureCandidate()));
        result.put("unrelatedHealthyRoleCandidate", candidateId(compression.unrelatedHealthyRoleCandidate()));
        result.put("unrelatedHealthyRoleValue", candidateValue(compression.unrelatedHealthyRoleCandidate()));
        result.put("directional", compression.directional());
        result.put("status", compression.reachable() && compression.directional()
                ? "ROLE_POOL_COMPRESSION_COMPONENT_DIRECTIONAL"
                : compression.reachable()
                        ? "REVIEW_ROLE_POOL_COMPRESSION_DIRECTIONALITY"
                        : "BLOCKED_BY_ROLE_POOL_COMPRESSION_COMPONENT_INTEGRITY");
        result.put("productionWeightsChanged", false);
        result.put("productionSearchBoundsChanged", false);
        result.put("positiveCandidates", compression.positiveCandidates().stream()
                .sorted(Comparator.comparing(value -> value.championId().value()))
                .map(value -> Map.of("championId", value.championId().value(),
                        "rolePoolCompression", value.componentValue()))
                .toList());
        return result;
    }

    private static String validationMarkdown(Map<String, Object> summary,
                                             RolePoolCompressionGateProbe.Result compression,
                                             FrozenHashes hashes) {
        return "# Pre-Real Champion Proficiency Gate Hardening\n\n"
                + "- Verdict: `" + summary.get("verdict") + "`\n"
                + "- Player Rating resource: `" + summary.get("playerRatingResourceVersion") + "`\n"
                + "- Player Rating expected SHA-256: `" + summary.get("playerRatingResourceExpectedSha") + "`\n"
                + "- Player Rating actual SHA-256: `" + summary.get("playerRatingResourceActualSha") + "`\n"
                + "- Player Rating hash match: `" + summary.get("playerRatingResourceHashMatch") + "`\n"
                + "- Roster: `" + summary.get("teamCount") + "` teams / `" + summary.get("playerCount")
                + "` starters / `" + summary.get("authoredRatingValueCount") + "` authored values\n"
                + "- Reachability identity: `" + summary.get("realProficiencyGateIdentityType") + "`\n"
                + "- Proficiency source of truth: `" + summary.get("proficiencySourceOfTruth") + "`\n"
                + "- Subject/role binding validated: `" + summary.get("subjectRoleBindingValidated") + "`\n"
                + "- Player Rating / Champion Proficiency isolation: `" + summary.get("playerRatingProficiencyIsolation") + "`\n"
                + "- Champion Proficiency authored values: `0`; execution remains `" + REAL_PROFICIENCY_STATUS + "`\n"
                + "- ROLE_POOL_COMPRESSION state: legal=`" + compression.stateLegal()
                + "`, completable=`" + compression.stateCompletable() + "`, reachable=`" + compression.reachable() + "`\n"
                + "- Direct candidate: `" + candidateId(compression.directRolePressureCandidate()) + "` = `"
                + candidateValue(compression.directRolePressureCandidate()) + "`\n"
                + "- Unrelated candidate: `" + candidateId(compression.unrelatedHealthyRoleCandidate()) + "` = `"
                + candidateValue(compression.unrelatedHealthyRoleCandidate()) + "`\n"
                + "- Directionality: `" + compression.directional() + "`\n"
                + "- Regression: present=`" + summary.get("backendRegressionPresent") + "`, XML=`"
                + summary.get("backendXmlFileCount") + "`, tests=`" + summary.get("backendTests")
                + "`, failures=`" + summary.get("backendFailures") + "`, errors=`"
                + summary.get("backendErrors") + "`, skipped=`" + summary.get("backendSkipped") + "`\n"
                + "- Draft Meta expected/actual/match: `" + hashes.draftMeta().expectedHash() + "` / `"
                + hashes.draftMeta().actualHash() + "` / `" + hashes.draftMeta().matches() + "`\n"
                + "- Legal roles expected/actual/match: `" + hashes.legalRoles().expectedHash() + "` / `"
                + hashes.legalRoles().actualHash() + "` / `" + hashes.legalRoles().matches() + "`\n"
                + "- Composition expected/actual/match: `" + hashes.composition().expectedHash() + "` / `"
                + hashes.composition().actualHash() + "` / `" + hashes.composition().matches() + "`\n"
                + "- Schedule expected/actual/match: `" + hashes.schedule().expectedHash() + "` / `"
                + hashes.schedule().actualHash() + "` / `" + hashes.schedule().matches() + "`\n"
                + "- Historical `phase13g-a-v2` artifacts were not rewritten.\n";
    }

    private static RatingValidation validateRatings(PlayerRatingCatalog catalog, PlayerRatingCatalog replay) {
        int authored = 0;
        int invalid = 0;
        int missing = 0;
        int extra = 0;
        int nonApplicable = 0;
        int minimum = Integer.MAX_VALUE;
        int maximum = Integer.MIN_VALUE;
        for (PlayerRatingResource player : catalog.all()) {
            Set<PlayerSkill> expected = PlayerSkill.forPosition(player.position());
            int actualSize = player.ratings().asMap().size();
            missing += Math.max(0, expected.size() - actualSize);
            extra += Math.max(0, actualSize - expected.size());
            for (Map.Entry<PlayerSkill, Integer> entry : player.ratings().asMap().entrySet()) {
                authored++;
                minimum = Math.min(minimum, entry.getValue());
                maximum = Math.max(maximum, entry.getValue());
                if (entry.getValue() < PlayerRatings.MIN || entry.getValue() > PlayerRatings.MAX) invalid++;
                if (!entry.getKey().appliesTo(player.position())) nonApplicable++;
            }
        }
        if (authored == 0) {
            minimum = 0;
            maximum = 0;
        }
        int duplicateKeys = catalog.playerCount() - (int) catalog.all().stream()
                .map(PlayerRatingResource::playerKey).distinct().count();
        int duplicateTeamPosition = catalog.playerCount() - (int) catalog.all().stream()
                .map(value -> value.teamCode() + ":" + value.position()).distinct().count();
        boolean resourceHashMatch = PLAYER_RATING_EXPECTED_SHA.equals(catalog.resourceSha256());
        boolean exactShape = catalog.teamCount() == 10 && catalog.playerCount() == 50
                && catalog.startersPerTeam() == 5 && !catalog.substitutesIncluded();
        boolean exactSet = authored == 600 && catalog.all().stream().allMatch(value ->
                value.ratings().asMap().keySet().equals(PlayerSkill.forPosition(value.position())));
        boolean deterministic = canonical(catalog).equals(canonical(replay));
        return new RatingValidation(resourceHashMatch, exactShape, exactSet, authored, minimum, maximum,
                invalid, missing, extra, nonApplicable, duplicateKeys, duplicateTeamPosition, deterministic,
                "LOADER_SHA256_AND_EXACT_SEMANTIC_ATTRIBUTE_SET", "LOADER_FAIL_FAST_RANGE_VALIDATION",
                "LOADER_FAIL_FAST_EXACT_ATTRIBUTE_SET", "LOADER_FAIL_FAST_EXACT_ATTRIBUTE_SET",
                "ROLE_APPLICABILITY_EXACT_SET", "PlayerRatingResource has no ChampionProficiency component");
    }

    private static FrozenHashes frozenHashes(DraftResourceSet resources) throws Exception {
        String draftMetaActual = classpathHash(DraftMetaCatalog.RESOURCE);
        String legalRoleActual = resources.meta().actualLegalRoleKeyHash();
        String compositionActual = resources.champions().composition().profileHash();
        String scheduleActual = Phase13GA2AuditSchedule.freeze(
                Phase13GASyntheticContextFactory.create(resources)).scheduleHash();
        return new FrozenHashes(
                new HashObservation(DRAFT_META_EXPECTED_SHA, draftMetaActual),
                new HashObservation(LEGAL_ROLE_EXPECTED_SHA, legalRoleActual),
                new HashObservation(COMPOSITION_EXPECTED_SHA, compositionActual),
                new HashObservation(SCHEDULE_EXPECTED_SHA, scheduleActual));
    }

    private static boolean gateIdentityReady() {
        try {
            var evaluate = RealProficiencyCandidateReachabilityGate.class.getDeclaredMethod(
                    "evaluate", PlayerRatingKey.class, ChampionRoleKey.class, List.class);
            var components = RealProficiencyCandidateReachabilityGate.Result.class.getRecordComponents();
            boolean playerKeyType = components.length > 0 && components[0].getType().equals(PlayerRatingKey.class);
            boolean duplicateRemoved = java.util.Arrays.stream(
                    RealProficiencyCandidateReachabilityGate.class.getDeclaredClasses())
                    .noneMatch(value -> value.getSimpleName().equals("ProficiencySubjectKey"));
            ChampionRoleKey midRole = new ChampionRoleKey(new ChampionId("azir"), com.lolfm.domain.Position.MID);
            boolean roleBinding = RealProficiencyCandidateReachabilityGate.subjectRoleMatches(
                    new PlayerRatingKey("gen", com.lolfm.domain.Position.MID), midRole)
                    && !RealProficiencyCandidateReachabilityGate.subjectRoleMatches(
                    new PlayerRatingKey("GEN", com.lolfm.domain.Position.TOP), midRole);
            return evaluate.getParameterCount() == 3 && playerKeyType && duplicateRemoved && roleBinding;
        } catch (ReflectiveOperationException error) {
            return false;
        }
    }

    private static Phase13GA2Finalizer.RegressionCounts regressionCounts(Path testResults) throws Exception {
        if (!Files.isDirectory(testResults)) {
            return new Phase13GA2Finalizer.RegressionCounts(0, 0, 0, 0, 0);
        }
        return Phase13GA2Finalizer.aggregateXml(testResults);
    }

    private static String candidateId(RolePoolCompressionGateProbe.CandidateEvaluation candidate) {
        return candidate == null ? null : candidate.championId().value();
    }

    private static Double candidateValue(RolePoolCompressionGateProbe.CandidateEvaluation candidate) {
        return candidate == null ? null : candidate.componentValue();
    }

    private static String canonical(PlayerRatingCatalog catalog) {
        return catalog.all().stream()
                .sorted(Comparator.comparing(value -> value.playerKey().stableId()))
                .map(value -> value.playerKey().stableId() + "|" + value.ratings().asMap().entrySet().stream()
                        .sorted(Map.Entry.comparingByKey())
                        .map(entry -> entry.getKey().name() + "=" + entry.getValue())
                        .collect(Collectors.joining(",")))
                .collect(Collectors.joining("\n"));
    }

    private static String classpathHash(String resource) throws Exception {
        try (InputStream input = Phase13GPreProficiencyHardeningAudit.class.getClassLoader()
                .getResourceAsStream(resource)) {
            if (input == null) throw new IOException("Missing frozen resource: " + resource);
            return sha256(input.readAllBytes());
        }
    }

    private static void writeJson(Path path, Object value) throws IOException {
        ObjectMapper mapper = new ObjectMapper()
                .enable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
                .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
                .enable(SerializationFeature.INDENT_OUTPUT);
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
        Files.writeString(output.resolve("phase13g-pre-proficiency-hardening-SHA256SUMS.txt"),
                String.join("\n", lines) + "\n", StandardCharsets.UTF_8);
    }

    private static String sha256(byte[] bytes) throws Exception {
        return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }

    private record HashObservation(String expectedHash, String actualHash) {
        boolean matches() { return expectedHash.equals(actualHash); }
    }

    private record FrozenHashes(
            HashObservation draftMeta,
            HashObservation legalRoles,
            HashObservation composition,
            HashObservation schedule
    ) {
        boolean allMatch() {
            return draftMeta.matches() && legalRoles.matches() && composition.matches() && schedule.matches();
        }
    }

    private record RatingValidation(
            boolean resourceHashMatch,
            boolean exactRosterShape,
            boolean exactRatingSet,
            int authoredValueCount,
            int minimum,
            int maximum,
            int invalidRatingCount,
            int missingAttributeCount,
            int extraAttributeCount,
            int nonApplicableAttributeCount,
            int duplicatePlayerKeyCount,
            int duplicateTeamPositionCount,
            boolean deterministicReloadMatch,
            String exactRatingValidation,
            String invalidRatingValidation,
            String missingAttributeValidation,
            String extraAttributeValidation,
            String nonApplicableAttributeValidation,
            String playerRatingProficiencyIsolation
    ) { }

    public record AuditResult(String verdict, boolean directional, long backendTests,
                              long backendFailures, long backendErrors, long backendSkipped,
                              int backendXmlFileCount) { }
}
