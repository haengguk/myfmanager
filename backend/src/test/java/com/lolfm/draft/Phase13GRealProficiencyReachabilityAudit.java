package com.lolfm.draft;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.lolfm.champion.ChampionId;
import com.lolfm.player.ChampionProficiencyCatalog;
import com.lolfm.player.ChampionProficiencyEntry;
import com.lolfm.player.ChampionProficiencyPopulationMetrics;
import com.lolfm.player.LckTeamAssembler;
import com.lolfm.player.PlayerId;
import com.lolfm.player.PlayerRatingCatalog;
import com.lolfm.player.PlayerRatingKey;
import com.lolfm.simulator.TeamSide;
import java.io.IOException;
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

/** Bounded real-population execution of the existing candidate reachability gate. */
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
        System.out.println("REAL_PROFICIENCY_LEGAL_SCENARIOS=" + result.legalScenarioCount());
        System.out.println("REAL_PROFICIENCY_CANDIDATE_APPEARANCES=" + result.candidateAppearanceCount());
        System.out.println("REAL_PROFICIENCY_UNREACHABLE_KEYS=" + result.unreachableHighProficiencyKeyCount());
        System.out.println("REAL_PROFICIENCY_NO_LEGAL_SCENARIO_KEYS=" + result.noLegalScenarioKeyCount());
        System.out.println("REAL_PROFICIENCY_GATE_VERDICT=" + result.verdict());
    }

    public static AuditResult run(Path output) throws Exception {
        Objects.requireNonNull(output, "output");
        Files.createDirectories(output);

        DraftResourceSet resources = DraftResourceSet.loadDefault();
        PlayerRatingCatalog ratings = PlayerRatingCatalog.loadDefault();
        ChampionProficiencyCatalog proficiencies = ChampionProficiencyCatalog.loadDefault();
        LckTeamAssembler teams = LckTeamAssembler.loadDefault();
        RealProficiencyCandidateReachabilityGate gate =
                new RealProficiencyCandidateReachabilityGate(resources);
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
            keyResults.add(new KeyResult(
                    entry.playerId().value(), entry.sourceRatingKey().stableId(),
                    ratings.get(entry.sourceRatingKey()).nickname(),
                    entry.sourceRatingKey().teamCode(), entry.sourceRatingKey().position().name(),
                    entry.championRoleKey().stableId(), entry.championRoleKey().championId().value(),
                    entry.value(), result.scenarioCount(), result.legalScenarioCount(),
                    result.candidateAppearanceCount(), result.candidateScenarioPresence(),
                    result.reachable(), result.reason()));
        }

        Map<String, MutableConcentration> players = new HashMap<>();
        Map<String, MutableConcentration> champions = new HashMap<>();
        EnumMap<com.lolfm.domain.Position, MutableConcentration> positions =
                new EnumMap<>(com.lolfm.domain.Position.class);
        for (com.lolfm.domain.Position position : com.lolfm.domain.Position.values()) {
            positions.put(position, new MutableConcentration(position.name()));
        }
        for (ChampionProficiencyEntry entry : authored) {
            players.computeIfAbsent(entry.playerId().value(), MutableConcentration::new).authored++;
            champions.computeIfAbsent(entry.championRoleKey().championId().value(),
                    MutableConcentration::new).authored++;
            positions.get(entry.championRoleKey().position()).authored++;
        }
        for (KeyResult result : keyResults) {
            MutableConcentration player = players.get(result.playerId());
            MutableConcentration champion = champions.get(result.championId());
            MutableConcentration position = positions.get(
                    com.lolfm.domain.Position.valueOf(result.position()));
            player.high++;
            champion.high++;
            position.high++;
            if (result.reachable()) {
                player.reachable++;
                champion.reachable++;
                position.reachable++;
            }
        }

        List<Concentration> perPlayer = concentrations(players);
        List<Concentration> perChampion = concentrations(champions);
        List<Concentration> perPosition = positions.values().stream()
                .map(MutableConcentration::snapshot).toList();
        int legalScenarioCount = keyResults.stream().mapToInt(KeyResult::legalScenarioCount).sum();
        int candidateAppearanceCount = keyResults.stream()
                .mapToInt(KeyResult::candidateAppearanceCount).sum();
        int presenceCount = (int) keyResults.stream().filter(KeyResult::candidateScenarioPresence).count();
        List<String> unreachable = keyResults.stream()
                .filter(result -> result.legalScenarioCount() > 0 && !result.candidateScenarioPresence())
                .map(KeyResult::stableKey).toList();
        List<String> noLegal = keyResults.stream().filter(result -> result.legalScenarioCount() == 0)
                .map(KeyResult::stableKey).toList();
        List<String> reviews = unreachable.isEmpty()
                ? List.of() : List.of("REVIEW_REAL_PROFICIENCY_CANDIDATE_UNREACHABLE");

        ChampionProficiencyPopulationMetrics metrics = proficiencies.metrics();
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("phase", "PHASE_13G_REAL_CHAMPION_PROFICIENCY_POPULATION");
        summary.put("playerIdentityResourceVersion", ratings.identities().version());
        summary.put("playerRatingResourceVersion", ratings.version());
        summary.put("proficiencyResourceVersion", proficiencies.version());
        summary.put("proficiencyResourceSha256", proficiencies.resourceSha256());
        summary.put("championPoolVersion", resources.champions().catalog().championPoolVersion());
        summary.put("legalRoleKeyCount", resources.champions().catalog().legalRoleKeys().size());
        summary.put("authoredOverrideCount", metrics.authoredOverrideCount());
        summary.put("highProficiencyThreshold", proficiencies.highProficiencyThreshold());
        summary.put("highProficiencyAuthoredCount", highEntries.size());
        summary.put("scenarioCountPerKey", SCENARIO_ACTION_COUNTS.size());
        summary.put("boundedScenarioCount", keyResults.stream().mapToInt(KeyResult::scenarioCount).sum());
        summary.put("legalScenarioCount", legalScenarioCount);
        summary.put("candidateAppearanceCount", candidateAppearanceCount);
        summary.put("candidateScenarioPresenceCount", presenceCount);
        summary.put("candidateScenarioPresenceRatio",
                highEntries.isEmpty() ? 0.0 : (double) presenceCount / highEntries.size());
        summary.put("unreachableHighProficiencyKeyCount", unreachable.size());
        summary.put("unreachableHighProficiencyKeys", unreachable);
        summary.put("noLegalScenarioKeyCount", noLegal.size());
        summary.put("noLegalScenarioKeys", noLegal);
        summary.put("perPlayerConcentration", perPlayer);
        summary.put("perChampionConcentration", perChampion);
        summary.put("perPositionConcentration", perPosition);
        summary.put("topConcentratedPlayers", top(perPlayer, 10));
        summary.put("topConcentratedChampions", top(perChampion, 15));
        summary.put("phase13GA2CandidateStarvationComparison", phase13GA2Comparison());
        summary.put("scopeInexpressibleEvidenceCount", metrics.scopeInexpressibleEvidenceCount());
        summary.put("scopeGapPromotedToLegalRole", false);
        summary.put("productionWeightsChanged", false);
        summary.put("candidateGeneratorChanged", false);
        summary.put("shortlistSizeChanged", false);
        summary.put("searchBoundsChanged", false);
        summary.put("proficiencyValuesChanged", false);
        summary.put("blockerCodes", List.of());
        summary.put("reviewCodes", reviews);
        String verdict = reviews.isEmpty()
                ? "REAL_PROFICIENCY_CANDIDATE_REACHABILITY_GATE_EXECUTED"
                : "REAL_PROFICIENCY_CANDIDATE_REACHABILITY_GATE_EXECUTED_WITH_REVIEWS";
        summary.put("verdict", verdict);

        ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
        Path summaryPath = output.resolve(SUMMARY_FILE);
        mapper.writeValue(summaryPath.toFile(), summary);
        Path keysPath = output.resolve(KEY_RESULTS_FILE);
        Files.writeString(keysPath, keyCsv(keyResults), StandardCharsets.UTF_8);
        writeSha(output, List.of(summaryPath, keysPath));
        return new AuditResult(highEntries.size(), legalScenarioCount, candidateAppearanceCount,
                presenceCount, unreachable.size(), noLegal.size(), verdict, keyResults,
                summaryPath, keysPath);
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
            for (String id : variant) {
                resources.champions().catalog().get(new ChampionId(id));
            }
            stateAfter(variant);
        }
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

    private static Map<String, Object> phase13GA2Comparison() {
        Path path = Path.of("build/reports/phase13g-a-v2/phase13g-a-v2-structural-integrated-audit-summary.json");
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("artifactPath", path.toString().replace('\\', '/'));
        result.put("artifactFound", Files.isRegularFile(path));
        result.put("baselineGranularity", "SYNTHETIC_CHAMPION_ID");
        result.put("currentGranularity", "REAL_PLAYER_ID_X_CHAMPION_ROLE_KEY");
        result.put("directlyComparable", false);
        result.put("numericDelta", null);
        if (!Files.isRegularFile(path)) {
            result.put("status", "A2_ARTIFACT_NOT_FOUND_NO_GUESS");
            return result;
        }
        try {
            JsonNode root = new ObjectMapper().readTree(path.toFile());
            JsonNode starved = root.path("candidateStarvedHighProficiencyChampions");
            result.put("baselineStarvedChampionCount", starved.isArray() ? starved.size() : null);
            result.put("status", "FOUND_SYNTHETIC_ARTIFACT_NOT_CURRENT_HEAD_PROVEN_NO_DIRECT_DELTA");
        } catch (IOException error) {
            result.put("status", "A2_ARTIFACT_UNREADABLE_NO_GUESS");
        }
        return result;
    }

    private static String keyCsv(List<KeyResult> results) {
        StringBuilder out = new StringBuilder();
        out.append("playerId,playerRatingKey,nickname,team,position,championRoleKey,championId,")
                .append("proficiency,scenarioCount,legalScenarioCount,candidateAppearanceCount,")
                .append("candidateScenarioPresence,reachable,reason\n");
        for (KeyResult result : results) {
            out.append(csv(result.playerId())).append(',').append(csv(result.playerRatingKey())).append(',')
                    .append(csv(result.nickname())).append(',').append(csv(result.team())).append(',')
                    .append(csv(result.position())).append(',').append(csv(result.championRoleKey())).append(',')
                    .append(csv(result.championId())).append(',').append(result.proficiency()).append(',')
                    .append(result.scenarioCount()).append(',').append(result.legalScenarioCount()).append(',')
                    .append(result.candidateAppearanceCount()).append(',')
                    .append(result.candidateScenarioPresence()).append(',').append(result.reachable()).append(',')
                    .append(csv(result.reason())).append('\n');
        }
        return out.toString();
    }

    private static String csv(String value) {
        if (value == null) return "";
        if (value.indexOf(',') < 0 && value.indexOf('"') < 0 && value.indexOf('\n') < 0) return value;
        return '"' + value.replace("\"", "\"\"") + '"';
    }

    private static void writeSha(Path output, List<Path> files) throws Exception {
        StringBuilder manifest = new StringBuilder();
        for (Path file : files) {
            byte[] bytes = Files.readAllBytes(file);
            String sha = java.util.HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(bytes));
            manifest.append(sha).append("  ").append(file.getFileName()).append('\n');
        }
        Files.writeString(output.resolve(SHA_FILE), manifest, StandardCharsets.UTF_8);
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
            int proficiency,
            int scenarioCount,
            int legalScenarioCount,
            int candidateAppearanceCount,
            boolean candidateScenarioPresence,
            boolean reachable,
            String reason
    ) {
        String stableKey() { return playerId + "/" + championRoleKey; }
    }

    public record Concentration(String key, int authored, int high, int reachable) { }

    private static final class MutableConcentration {
        private final String key;
        private int authored;
        private int high;
        private int reachable;

        private MutableConcentration(String key) { this.key = key; }
        private Concentration snapshot() { return new Concentration(key, authored, high, reachable); }
    }

    public record AuditResult(
            int highProficiencyAuthoredCount,
            int legalScenarioCount,
            int candidateAppearanceCount,
            int candidateScenarioPresenceCount,
            int unreachableHighProficiencyKeyCount,
            int noLegalScenarioKeyCount,
            String verdict,
            List<KeyResult> keyResults,
            Path summaryPath,
            Path keyResultsPath
    ) {
        public AuditResult { keyResults = List.copyOf(keyResults); }
    }
}
