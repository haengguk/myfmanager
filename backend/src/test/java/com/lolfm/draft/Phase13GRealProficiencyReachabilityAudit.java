package com.lolfm.draft;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.lolfm.champion.ChampionId;
import com.lolfm.domain.Position;
import com.lolfm.player.ChampionProficiencyCatalog;
import com.lolfm.player.ChampionProficiencyEntry;
import com.lolfm.player.ChampionProficiencyPopulationMetrics;
import com.lolfm.player.LckTeamAssembler;
import com.lolfm.player.PlayerRatingCatalog;
import com.lolfm.simulator.TeamSide;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Bounded diagnostic over the complete real high-proficiency population. */
public final class Phase13GRealProficiencyReachabilityAudit {
    public static final String OUTPUT_DIRECTORY =
            "build/reports/phase13g-real-proficiency-reachability";
    public static final String SUMMARY_FILE =
            "phase13g-real-proficiency-reachability-summary.json";
    public static final String KEY_RESULTS_FILE =
            "phase13g-real-proficiency-reachability-keys.csv";
    public static final String SHA_FILE =
            "phase13g-real-proficiency-reachability-SHA256SUMS.txt";

    private static final List<List<String>> ACTION_VARIANTS = List.of(
            List.of("aatrox", "akali", "akshan", "annie", "amumu", "brand",
                    "camille", "vi", "poppy", "nautilus"),
            List.of("garen", "galio", "gragas", "graves", "ivern", "jayce",
                    "kennen", "kindred", "lissandra", "lulu"),
            List.of("malphite", "maokai", "milio", "morgana", "neeko", "nocturne",
                    "ornn", "rell", "syndra", "tristana"));
    private static final List<Integer> SCENARIO_ACTION_COUNTS = List.of(6, 8, 10);

    private Phase13GRealProficiencyReachabilityAudit() { }

    public static void main(String[] args) throws Exception {
        Path output = Path.of(System.getProperty("phase13g.realProficiency.outputDir", OUTPUT_DIRECTORY));
        AuditResult result = run(output);
        System.out.println("REAL_PROFICIENCY_HIGH_KEYS=" + result.highProficiencyAuthoredCount());
        System.out.println("REAL_PROFICIENCY_CHAMPION_LEVEL_LEGAL_SCENARIOS="
                + result.championLevelLegalScenarioCount());
        System.out.println("REAL_PROFICIENCY_ROLE_SPECIFIC_LEGAL_SCENARIOS="
                + result.roleSpecificLegalScenarioCount());
        System.out.println("REAL_PROFICIENCY_CHAMPION_CANDIDATE_PRESENT_KEYS="
                + result.championCandidatePresentKeyCount());
        System.out.println("REAL_PROFICIENCY_ROLE_KEY_REACHABLE_KEYS="
                + result.roleKeyReachableKeyCount());
        System.out.println("REAL_PROFICIENCY_ROLE_KEY_UNREACHABLE_KEYS="
                + result.roleSpecificUnreachableHighKeyCount());
        System.out.println("REAL_PROFICIENCY_GATE_VERDICT=" + result.verdict());
    }

    public static AuditResult run(Path output) throws Exception {
        Objects.requireNonNull(output, "output");
        DraftResourceSet resources = DraftResourceSet.loadDefault();
        PlayerRatingCatalog ratings = PlayerRatingCatalog.loadDefault();
        ChampionProficiencyCatalog proficiencies = ChampionProficiencyCatalog.loadDefault(
                ratings, resources.champions().catalog());
        LckTeamAssembler teams = new LckTeamAssembler(ratings, proficiencies);
        RealProficiencyCandidateReachabilityGate gate =
                new RealProficiencyCandidateReachabilityGate(resources, ratings);
        PreDraftPlanner planner = new PreDraftPlanner(resources.champions().catalog(), resources.meta(),
                resources.champions().composition(),
                new RoleAssignmentSolver(resources.champions().catalog()));

        validateActionVariants(resources);
        Map<String, DraftTeamContext> contexts = new LinkedHashMap<>();
        teams.teamCodes().stream().sorted().forEach(team ->
                contexts.put(team, DraftTeamContext.from(teams.assemble(team))));
        Map<ScenarioCacheKey, List<RealProficiencyCandidateReachabilityGate.Scenario>> scenarioCache =
                new HashMap<>();

        List<ChampionProficiencyEntry> authored = proficiencies.authoredEntries();
        List<ChampionProficiencyEntry> highEntries = authored.stream()
                .filter(entry -> entry.value() >= proficiencies.highProficiencyThreshold())
                .sorted(Comparator.comparing((ChampionProficiencyEntry entry) -> entry.playerId().value())
                        .thenComparing(entry -> entry.championRoleKey().stableId()))
                .toList();
        List<KeyResult> keyResults = new ArrayList<>();
        for (ChampionProficiencyEntry entry : highEntries) {
            int variant = actionVariant(entry.championRoleKey().championId());
            List<RealProficiencyCandidateReachabilityGate.Scenario> scenarios = scenarioCache.computeIfAbsent(
                    new ScenarioCacheKey(entry.sourceRatingKey().teamCode(), variant),
                    key -> scenarios(key.teamCode(), key.variant(), contexts, planner));
            RealProficiencyCandidateReachabilityGate.Result result = gate.evaluate(
                    entry.playerId(), entry.sourceRatingKey(), entry.championRoleKey(), scenarios);
            boolean flexChampion = resources.champions().catalog().get(
                    entry.championRoleKey().championId()).supportedPositions().size() > 1;
            keyResults.add(new KeyResult(
                    entry.playerId().value(), entry.sourceRatingKey().stableId(),
                    ratings.get(entry.sourceRatingKey()).nickname(),
                    entry.sourceRatingKey().teamCode(), entry.sourceRatingKey().position().name(),
                    entry.championRoleKey().stableId(), entry.championRoleKey().championId().value(),
                    flexChampion, entry.value(), result.scenarioCount(),
                    result.championLevelLegalScenarioCount(), result.roleSpecificLegalScenarioCount(),
                    result.championCandidateAppearanceCount(), result.roleKeyReachableScenarioCount(),
                    result.championPresentButTargetRoleInfeasibleCount(),
                    result.championPresentButRoleCompletionImpossibleCount(),
                    result.championCandidateScenarioPresence(), result.roleKeyScenarioPresence(),
                    result.roleKeyReachable(), result.reason()));
        }

        Map<String, MutableConcentration> players = new HashMap<>();
        Map<String, MutableConcentration> champions = new HashMap<>();
        EnumMap<Position, MutableConcentration> positions = new EnumMap<>(Position.class);
        for (Position position : Position.values()) {
            positions.put(position, new MutableConcentration(position.name()));
        }
        for (ChampionProficiencyEntry entry : authored) {
            players.computeIfAbsent(entry.playerId().value(), MutableConcentration::new).authored++;
            champions.computeIfAbsent(entry.championRoleKey().championId().value(),
                    MutableConcentration::new).authored++;
            positions.get(entry.championRoleKey().position()).authored++;
        }
        for (KeyResult result : keyResults) {
            addKeyResult(players.get(result.playerId()), result);
            addKeyResult(champions.get(result.championId()), result);
            addKeyResult(positions.get(Position.valueOf(result.position())), result);
        }

        List<Concentration> perPlayer = concentrations(players);
        List<Concentration> perChampion = concentrations(champions);
        List<Concentration> perPosition = positions.values().stream()
                .map(MutableConcentration::snapshot).toList();
        Set<String> flexChampionIds = resources.champions().catalog().all().stream()
                .filter(value -> value.supportedPositions().size() > 1)
                .map(value -> value.id().value())
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        List<Concentration> flexFalsePositiveConcentration = perChampion.stream()
                .filter(value -> flexChampionIds.contains(value.key()))
                .filter(value -> value.championPresentButTargetRoleInfeasible() > 0
                        || value.championPresentButRoleCompletionImpossible() > 0)
                .toList();

        int boundedScenarioCount = sum(keyResults, KeyResult::scenarioCount);
        int championLevelLegalScenarioCount = sum(
                keyResults, KeyResult::championLevelLegalScenarioCount);
        int roleSpecificLegalScenarioCount = sum(
                keyResults, KeyResult::roleSpecificLegalScenarioCount);
        int championCandidateAppearanceCount = sum(
                keyResults, KeyResult::championCandidateAppearanceCount);
        int roleKeyReachableScenarioCount = sum(
                keyResults, KeyResult::roleKeyReachableScenarioCount);
        int championPresentButTargetRoleInfeasibleCount = sum(
                keyResults, KeyResult::championPresentButTargetRoleInfeasibleCount);
        int championPresentButRoleCompletionImpossibleCount = sum(
                keyResults, KeyResult::championPresentButRoleCompletionImpossibleCount);
        int championCandidatePresentKeyCount = count(
                keyResults, KeyResult::championCandidateScenarioPresence);
        int roleKeyReachableKeyCount = count(keyResults, KeyResult::roleKeyReachable);
        int roleSpecificUnreachableHighKeyCount = highEntries.size() - roleKeyReachableKeyCount;
        int noRoleSpecificLegalScenarioKeyCount = count(keyResults,
                value -> value.roleSpecificLegalScenarioCount() == 0);
        int flexFalsePositiveScenarioCount = keyResults.stream().filter(KeyResult::flexChampion)
                .mapToInt(value -> value.championPresentButTargetRoleInfeasibleCount()
                        + value.championPresentButRoleCompletionImpossibleCount()).sum();
        int flexFalsePositiveKeyCount = count(keyResults, value -> value.flexChampion()
                && (value.championPresentButTargetRoleInfeasibleCount() > 0
                || value.championPresentButRoleCompletionImpossibleCount() > 0));
        List<String> roleKeyUnreachableKeys = keyResults.stream()
                .filter(value -> !value.roleKeyReachable()).map(KeyResult::stableKey).toList();
        List<String> noRoleSpecificLegalKeys = keyResults.stream()
                .filter(value -> value.roleSpecificLegalScenarioCount() == 0)
                .map(KeyResult::stableKey).toList();
        List<String> reviewableUnreachableKeys = keyResults.stream()
                .filter(value -> value.roleSpecificLegalScenarioCount() > 0
                        && !value.roleKeyReachable())
                .map(KeyResult::stableKey).toList();
        List<String> reviewCodes = reviewableUnreachableKeys.isEmpty()
                ? List.of() : List.of("REVIEW_REAL_PROFICIENCY_ROLE_KEY_UNREACHABLE");

        ChampionProficiencyPopulationMetrics metrics = proficiencies.metrics();
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("phase", "POST_PROFICIENCY_ROLE_SPECIFIC_REACHABILITY_HARDENING");
        summary.put("playerIdentityResourceVersion", ratings.identities().version());
        summary.put("playerIdentitySnapshotAt", ratings.identities().snapshotAt());
        summary.put("playerRatingResourceVersion", ratings.version());
        summary.put("proficiencyResourceVersion", proficiencies.version());
        summary.put("proficiencyResourceSha256", proficiencies.resourceSha256());
        summary.put("championPoolVersion", resources.champions().catalog().championPoolVersion());
        summary.put("authoredOverrideCount", metrics.authoredOverrideCount());
        summary.put("highProficiencyThreshold", proficiencies.highProficiencyThreshold());
        summary.put("highProficiencyAuthoredCount", highEntries.size());
        summary.put("playerCount", metrics.playerCount());
        summary.put("legalRoleKeyCount", resources.champions().catalog().legalRoleKeys().size());
        summary.put("scenarioCountPerKey", SCENARIO_ACTION_COUNTS.size());
        summary.put("boundedScenarioCount", boundedScenarioCount);

        summary.put("championLevelLegalScenarioCount", championLevelLegalScenarioCount);
        summary.put("championCandidatePresentKeyCount", championCandidatePresentKeyCount);
        summary.put("championCandidateAppearanceCount", championCandidateAppearanceCount);
        summary.put("championCandidatePresenceRatio", ratio(
                championCandidatePresentKeyCount, highEntries.size()));

        summary.put("roleSpecificLegalScenarioCount", roleSpecificLegalScenarioCount);
        summary.put("roleKeyReachableScenarioCount", roleKeyReachableScenarioCount);
        summary.put("roleKeyReachableKeyCount", roleKeyReachableKeyCount);
        summary.put("roleKeyReachabilityRatio", ratio(roleKeyReachableKeyCount, highEntries.size()));
        summary.put("championPresentButTargetRoleInfeasibleCount",
                championPresentButTargetRoleInfeasibleCount);
        summary.put("championPresentButRoleCompletionImpossibleCount",
                championPresentButRoleCompletionImpossibleCount);
        summary.put("roleSpecificUnreachableHighKeyCount", roleSpecificUnreachableHighKeyCount);
        summary.put("roleSpecificUnreachableHighKeys", roleKeyUnreachableKeys);
        summary.put("noRoleSpecificLegalScenarioKeyCount", noRoleSpecificLegalScenarioKeyCount);
        summary.put("noRoleSpecificLegalScenarioKeys", noRoleSpecificLegalKeys);
        summary.put("reviewableRoleKeyUnreachableCount", reviewableUnreachableKeys.size());
        summary.put("reviewableRoleKeyUnreachableKeys", reviewableUnreachableKeys);
        summary.put("flexChampionFalsePositiveScenarioCount", flexFalsePositiveScenarioCount);
        summary.put("flexChampionFalsePositiveKeyCount", flexFalsePositiveKeyCount);

        summary.put("perPlayerConcentration", perPlayer);
        summary.put("perChampionConcentration", perChampion);
        summary.put("perPositionConcentration", perPosition);
        summary.put("flexChampionFalsePositiveConcentration", flexFalsePositiveConcentration);
        summary.put("topConcentratedPlayers", top(perPlayer, 10));
        summary.put("topConcentratedChampions", top(perChampion, 15));
        summary.put("scopeInexpressibleEvidenceCount", metrics.scopeInexpressibleEvidenceCount());
        summary.put("scopeGapPromotedToLegalRole", false);
        summary.put("productionWeightsChanged", false);
        summary.put("candidateGeneratorChanged", false);
        summary.put("shortlistSizeChanged", false);
        summary.put("searchBoundsChanged", false);
        summary.put("draftMetaChanged", false);
        summary.put("proficiencyValuesChanged", false);
        summary.put("causalInfluenceProven", false);
        summary.put("interpretation",
                "A role key is reachable only when its champion is shortlisted and can complete the roster while fixed at the target position; this does not prove proficiency caused shortlist inclusion.");
        summary.put("blockerCodes", List.of());
        summary.put("reviewCodes", reviewCodes);
        String verdict = reviewCodes.isEmpty()
                ? "REAL_PROFICIENCY_ROLE_KEY_REACHABILITY_GATE_EXECUTED"
                : "REAL_PROFICIENCY_ROLE_KEY_REACHABILITY_GATE_EXECUTED_WITH_REVIEWS";
        summary.put("verdict", verdict);

        ReportPaths paths = writeReportArtifacts(output, summary, keyResults);
        return new AuditResult(metrics.authoredOverrideCount(), highEntries.size(), boundedScenarioCount,
                championLevelLegalScenarioCount, roleSpecificLegalScenarioCount,
                championCandidateAppearanceCount, championCandidatePresentKeyCount,
                roleKeyReachableScenarioCount, roleKeyReachableKeyCount,
                roleSpecificUnreachableHighKeyCount, noRoleSpecificLegalScenarioKeyCount,
                flexFalsePositiveScenarioCount, reviewCodes, verdict, keyResults,
                paths.summaryPath(), paths.keyResultsPath(), paths.shaPath());
    }

    static ReportPaths writeReportArtifacts(Path output, Map<String, Object> summary,
                                             List<KeyResult> keyResults) throws Exception {
        Files.createDirectories(Objects.requireNonNull(output, "output"));
        ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
        Path summaryPath = output.resolve(SUMMARY_FILE);
        mapper.writeValue(summaryPath.toFile(), new LinkedHashMap<>(summary));
        Path keysPath = output.resolve(KEY_RESULTS_FILE);
        Files.writeString(keysPath, keyCsv(List.copyOf(keyResults)), StandardCharsets.UTF_8);
        Path shaPath = output.resolve(SHA_FILE);
        writeSha(shaPath, List.of(summaryPath, keysPath));
        return new ReportPaths(summaryPath, keysPath, shaPath);
    }

    private static List<RealProficiencyCandidateReachabilityGate.Scenario> scenarios(
            String teamCode, int variant, Map<String, DraftTeamContext> contexts,
            PreDraftPlanner planner) {
        List<String> teams = contexts.keySet().stream().sorted().toList();
        int teamIndex = teams.indexOf(teamCode);
        String opponent = teams.get((teamIndex + 1) % teams.size());
        DraftTeamContext own = contexts.get(teamCode);
        DraftTeamContext enemy = contexts.get(opponent);
        List<RealProficiencyCandidateReachabilityGate.Scenario> result = new ArrayList<>();
        for (int count : SCENARIO_ACTION_COUNTS) {
            DraftState state = stateAfter(ACTION_VARIANTS.get(variant).subList(0, count));
            TeamSide side = state.currentTurn().side();
            result.add(new RealProficiencyCandidateReachabilityGate.Scenario(
                    teamCode.toLowerCase(Locale.ROOT) + "-v" + variant + "-after-" + count,
                    side, state, own, enemy,
                    planner.replan(own, enemy, side, state),
                    planner.replan(enemy, own, side.opposite(), state)));
        }
        return List.copyOf(result);
    }

    private static DraftState stateAfter(List<String> championIds) {
        DraftState state = DraftState.fresh(DraftRuleSet.professional(), new SeriesDraftHistory());
        for (String id : championIds) {
            DraftTurn turn = state.currentTurn();
            state = state.apply(new DraftAction(
                    turn.number(), turn.side(), turn.actionType(), new ChampionId(id)));
        }
        return state;
    }

    private static int actionVariant(ChampionId candidate) {
        for (int index = 0; index < ACTION_VARIANTS.size(); index++) {
            if (!ACTION_VARIANTS.get(index).contains(candidate.value())) return index;
        }
        throw new IllegalStateException("No bounded scenario variant excludes candidate: " + candidate);
    }

    private static void validateActionVariants(DraftResourceSet resources) {
        for (List<String> variant : ACTION_VARIANTS) {
            if (variant.stream().distinct().count() != variant.size()) {
                throw new IllegalStateException("Reachability scenario action variant contains duplicates");
            }
            for (String id : variant) resources.champions().catalog().get(new ChampionId(id));
            stateAfter(variant);
        }
    }

    private static void addKeyResult(MutableConcentration concentration, KeyResult result) {
        concentration.high++;
        if (result.championCandidateScenarioPresence()) concentration.championCandidatePresent++;
        if (result.roleKeyReachable()) concentration.roleKeyReachable++;
        concentration.championPresentButTargetRoleInfeasible +=
                result.championPresentButTargetRoleInfeasibleCount();
        concentration.championPresentButRoleCompletionImpossible +=
                result.championPresentButRoleCompletionImpossibleCount();
    }

    private static List<Concentration> concentrations(Map<String, MutableConcentration> values) {
        return values.values().stream().map(MutableConcentration::snapshot)
                .sorted(Comparator.comparing(Concentration::key)).toList();
    }

    private static List<Concentration> top(List<Concentration> values, int limit) {
        return values.stream().sorted(Comparator.comparingInt(Concentration::high).reversed()
                        .thenComparing(Concentration::key))
                .limit(limit).toList();
    }

    private static int sum(List<KeyResult> values,
                           java.util.function.ToIntFunction<KeyResult> mapper) {
        return values.stream().mapToInt(mapper).sum();
    }

    private static int count(List<KeyResult> values,
                             java.util.function.Predicate<KeyResult> predicate) {
        return (int) values.stream().filter(predicate).count();
    }

    private static double ratio(int numerator, int denominator) {
        return denominator == 0 ? 0.0 : (double) numerator / denominator;
    }

    private static String keyCsv(List<KeyResult> results) {
        StringBuilder out = new StringBuilder();
        out.append("playerId,playerRatingKey,nickname,team,position,championRoleKey,championId,")
                .append("flexChampion,proficiency,scenarioCount,championLevelLegalScenarioCount,")
                .append("roleSpecificLegalScenarioCount,championCandidateAppearanceCount,")
                .append("roleKeyReachableScenarioCount,championPresentButTargetRoleInfeasibleCount,")
                .append("championPresentButRoleCompletionImpossibleCount,")
                .append("championCandidateScenarioPresence,roleKeyScenarioPresence,")
                .append("roleKeyReachable,reason\n");
        for (KeyResult result : results) {
            out.append(csv(result.playerId())).append(',').append(csv(result.playerRatingKey())).append(',')
                    .append(csv(result.nickname())).append(',').append(csv(result.team())).append(',')
                    .append(csv(result.position())).append(',').append(csv(result.championRoleKey())).append(',')
                    .append(csv(result.championId())).append(',').append(result.flexChampion()).append(',')
                    .append(result.proficiency()).append(',').append(result.scenarioCount()).append(',')
                    .append(result.championLevelLegalScenarioCount()).append(',')
                    .append(result.roleSpecificLegalScenarioCount()).append(',')
                    .append(result.championCandidateAppearanceCount()).append(',')
                    .append(result.roleKeyReachableScenarioCount()).append(',')
                    .append(result.championPresentButTargetRoleInfeasibleCount()).append(',')
                    .append(result.championPresentButRoleCompletionImpossibleCount()).append(',')
                    .append(result.championCandidateScenarioPresence()).append(',')
                    .append(result.roleKeyScenarioPresence()).append(',')
                    .append(result.roleKeyReachable()).append(',')
                    .append(csv(result.reason())).append('\n');
        }
        return out.toString();
    }

    private static String csv(String value) {
        if (value == null) return "";
        if (value.indexOf(',') < 0 && value.indexOf('"') < 0 && value.indexOf('\n') < 0) return value;
        return '"' + value.replace("\"", "\"\"") + '"';
    }

    private static void writeSha(Path shaPath, List<Path> files) throws Exception {
        StringBuilder manifest = new StringBuilder();
        for (Path file : files) {
            byte[] bytes = Files.readAllBytes(file);
            String sha = java.util.HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(bytes));
            manifest.append(sha).append("  ").append(file.getFileName()).append('\n');
        }
        Files.writeString(shaPath, manifest, StandardCharsets.UTF_8);
    }

    private record ScenarioCacheKey(String teamCode, int variant) { }

    public record KeyResult(
            String playerId,
            String playerRatingKey,
            String nickname,
            String team,
            String position,
            String championRoleKey,
            String championId,
            boolean flexChampion,
            int proficiency,
            int scenarioCount,
            int championLevelLegalScenarioCount,
            int roleSpecificLegalScenarioCount,
            int championCandidateAppearanceCount,
            int roleKeyReachableScenarioCount,
            int championPresentButTargetRoleInfeasibleCount,
            int championPresentButRoleCompletionImpossibleCount,
            boolean championCandidateScenarioPresence,
            boolean roleKeyScenarioPresence,
            boolean roleKeyReachable,
            String reason
    ) {
        String stableKey() { return playerId + "/" + championRoleKey; }
    }

    public record Concentration(
            String key,
            int authored,
            int high,
            int championCandidatePresent,
            int roleKeyReachable,
            int championPresentButTargetRoleInfeasible,
            int championPresentButRoleCompletionImpossible
    ) { }

    private static final class MutableConcentration {
        private final String key;
        private int authored;
        private int high;
        private int championCandidatePresent;
        private int roleKeyReachable;
        private int championPresentButTargetRoleInfeasible;
        private int championPresentButRoleCompletionImpossible;

        private MutableConcentration(String key) { this.key = key; }
        private Concentration snapshot() {
            return new Concentration(key, authored, high, championCandidatePresent, roleKeyReachable,
                    championPresentButTargetRoleInfeasible,
                    championPresentButRoleCompletionImpossible);
        }
    }

    record ReportPaths(Path summaryPath, Path keyResultsPath, Path shaPath) { }

    public record AuditResult(
            int authoredOverrideCount,
            int highProficiencyAuthoredCount,
            int boundedScenarioCount,
            int championLevelLegalScenarioCount,
            int roleSpecificLegalScenarioCount,
            int championCandidateAppearanceCount,
            int championCandidatePresentKeyCount,
            int roleKeyReachableScenarioCount,
            int roleKeyReachableKeyCount,
            int roleSpecificUnreachableHighKeyCount,
            int noRoleSpecificLegalScenarioKeyCount,
            int flexChampionFalsePositiveScenarioCount,
            List<String> reviewCodes,
            String verdict,
            List<KeyResult> keyResults,
            Path summaryPath,
            Path keyResultsPath,
            Path shaPath
    ) {
        public AuditResult {
            reviewCodes = List.copyOf(reviewCodes);
            keyResults = List.copyOf(keyResults);
        }
    }
}
