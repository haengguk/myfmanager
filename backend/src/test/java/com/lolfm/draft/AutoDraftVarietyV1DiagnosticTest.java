package com.lolfm.draft;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.lolfm.application.MatchEngineV1Policy;
import com.lolfm.application.RealDraftSelectionContextFactory;
import com.lolfm.champion.ChampionCatalog;
import com.lolfm.champion.ChampionId;
import com.lolfm.domain.Position;
import com.lolfm.domain.Team;
import com.lolfm.player.LckTeamAssembler;
import com.lolfm.simulator.TeamSide;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Stream;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {"spring.main.banner-mode=off", "logging.level.root=ERROR"})
@Tag("diagnostic")
@Tag("auto-draft-variety-v1")
class AutoDraftVarietyV1DiagnosticTest {
    private static final String STATUS_ACCEPTED = "AUTO_DRAFT_VARIETY_V1_ACCEPTED";
    private static final String STATUS_REVIEW = "AUTO_DRAFT_VARIETY_V1_REVIEW_REQUIRED";
    private static final String STATUS_BLOCKED = "AUTO_DRAFT_VARIETY_V1_BLOCKED";

    @Autowired ObjectMapper mapper;
    @Autowired ChampionCatalog champions;
    @Autowired LckTeamAssembler teams;

    @Test
    void generateFixedEightyDraftVarietyReport() throws Exception {
        Path backendRoot = Path.of(System.getProperty("autoDraftVarietyBackendRoot"))
                .toAbsolutePath().normalize();
        Path output = Path.of(System.getProperty("autoDraftVarietyOutput"))
                .toAbsolutePath().normalize();
        Path probes = Path.of(System.getProperty("autoDraftVarietyProbeDirectory"))
                .toAbsolutePath().normalize();
        Files.createDirectories(output);
        ObjectMapper json = mapper.copy()
                .enable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
                .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
                .enable(SerializationFeature.INDENT_OUTPUT);
        DraftEngine engine = new DraftEngine(DraftResourceSet.loadDefault(mapper, champions));
        Metrics metrics = new Metrics();

        for (AutoDraftVarietyV1Schedule.Fixture fixture
                : AutoDraftVarietyV1Schedule.FIXTURES) {
            FixtureMetrics fixtureMetrics = new FixtureMetrics(fixture);
            metrics.fixtures.add(fixtureMetrics);
            for (long seed : AutoDraftVarietyV1Schedule.SEEDS) {
                FinalDraftResult result = draft(engine, fixture, seed);
                validate(result, fixtureMetrics, metrics.errors);
                Run run = Run.from(fixture, seed, result);
                metrics.runs.add(run);
                fixtureMetrics.accept(result);
                metrics.accept(result);
            }
            long replaySeed = AutoDraftVarietyV1Schedule.SEEDS.getFirst();
            FinalDraftResult original = draft(engine, fixture, replaySeed);
            FinalDraftResult replay = draft(engine, fixture, replaySeed);
            metrics.sameSeedReplayChecks++;
            if (!exact(original, replay)) metrics.errors.increment("sameSeedReplayMismatch");
            if (!original.selectionTraces().stream().map(
                    DraftSelectionTrace::selectionContextHash).toList().equals(
                    replay.selectionTraces().stream().map(
                            DraftSelectionTrace::selectionContextHash).toList())) {
                metrics.errors.increment("selectionContextMismatch");
            }
        }

        metrics.crossJvmChecks = 1;
        Path probeA = probes.resolve("probe-a.json");
        Path probeB = probes.resolve("probe-b.json");
        if (!Files.exists(probeA) || !Files.exists(probeB)
                || Files.mismatch(probeA, probeB) != -1L) {
            metrics.errors.increment("crossJvmMismatch");
        }

        int completeGate = (int) metrics.fixtures.stream()
                .filter(value -> value.draftIdentities.size() >= 2).count();
        int pickGate = (int) metrics.fixtures.stream()
                .filter(value -> value.finalPickTuples.size() >= 2).count();
        boolean correctness = metrics.errors.total() == 0;
        String status = !correctness ? STATUS_BLOCKED
                : completeGate == 10 && pickGate >= 8 ? STATUS_ACCEPTED : STATUS_REVIEW;

        Map<String, Object> contract = contract(backendRoot);
        Map<String, Object> summary = summary(
                metrics, status, completeGate, pickGate, backendRoot);
        writeJson(json, output.resolve("auto-draft-variety-v1-contract.json"), contract);
        Files.writeString(output.resolve("auto-draft-variety-v1-runs.csv"),
                runsCsv(metrics.runs), StandardCharsets.UTF_8);
        writeJson(json, output.resolve("auto-draft-variety-v1-summary.json"), summary);
        Files.writeString(output.resolve("auto-draft-variety-v1-analysis.md"),
                analysis(metrics, status, completeGate, pickGate), StandardCharsets.UTF_8);
        writeManifest(output);

        assertThat(metrics.runs).hasSize(80);
        assertThat(correctness).isTrue();
        System.out.println(status + " output=" + output);
    }

    private FinalDraftResult draft(DraftEngine engine,
                                   AutoDraftVarietyV1Schedule.Fixture fixture,
                                   long seed) {
        Team blue = teams.assemble(fixture.blueTeamCode());
        Team red = teams.assemble(fixture.redTeamCode());
        SeriesDraftHistory history = new SeriesDraftHistory();
        return engine.draft(DraftTeamContext.from(blue), DraftTeamContext.from(red), history,
                RealDraftSelectionContextFactory.create(seed, fixture.blueTeamCode(), blue,
                        fixture.redTeamCode(), red, 1, history.consumedPicks()));
    }

    private static void validate(FinalDraftResult result,
                                 FixtureMetrics fixture,
                                 ErrorCounts errors) {
        List<ChampionId> all = allChampions(result);
        if (all.size() != 20 || new HashSet<>(all).size() != 20) {
            errors.increment("duplicateBanOrPick");
        }
        if (!result.hardFearlessExclusions().isEmpty()
                || all.stream().anyMatch(result.hardFearlessExclusions()::contains)) {
            errors.increment("hardFearlessViolation");
        }
        if (result.bluePicks().size() != 5 || result.redPicks().size() != 5
                || result.decisions().size() != 20) {
            errors.increment("illegalDraft");
        }
        if (!Set.copyOf(result.blueFinalRoleAssignments().values())
                .equals(Set.of(Position.values()))
                || !Set.copyOf(result.redFinalRoleAssignments().values())
                .equals(Set.of(Position.values()))
                || result.matchChampionAssignments().asMap().size() != 10) {
            errors.increment("finalAssignmentError");
        }
        if (!result.draftSelectionPolicyId().equals(
                MatchEngineV1Policy.DRAFT_SELECTION_POLICY_ID)
                || !result.draftSelectionPolicyHash().equals(
                MatchEngineV1Policy.DRAFT_SELECTION_POLICY_SHA256)
                || !result.selectionTraceHash().equals(
                DraftSelectionTraceHasher.hash(result.selectionTraces()))) {
            errors.increment("policyOrTraceHashMismatch");
        }
        if (result.selectionTraces().size() != 20) {
            errors.increment("selectionTraceCardinalityError");
            return;
        }
        Set<String> contextHashes = new HashSet<>();
        for (int index = 0; index < 20; index++) {
            DraftDecision decision = result.decisions().get(index);
            DraftSelectionTrace trace = result.selectionTraces().get(index);
            if (!contextHashes.add(trace.selectionContextHash())) {
                errors.increment("selectionContextMismatch");
            }
            if (trace.turn() != decision.turn() || trace.side() != decision.side()
                    || trace.actionType() != decision.actionType()
                    || !trace.selectedChampionId().equals(decision.selectedChampionId())
                    || trace.eligiblePool().stream().noneMatch(entry ->
                    entry.championId().equals(decision.selectedChampionId()))) {
                errors.increment("selectionTraceDecisionMismatch");
            }
            if (trace.selectedRank() > 3) errors.increment("selectedRankAboveThree");
            if (trace.selectedCanonicalScoreLoss() > 2_000_000L) {
                errors.increment("selectedScoreLossAboveWindow");
            }
        }
        if (fixture.fixture.blueTeamCode().equals(fixture.fixture.redTeamCode())) {
            errors.increment("illegalDraft");
        }
    }

    private static boolean exact(FinalDraftResult left, FinalDraftResult right) {
        return left.draftIdentity().equals(right.draftIdentity())
                && left.decisions().equals(right.decisions())
                && left.selectionTraces().equals(right.selectionTraces())
                && left.selectionTraceHash().equals(right.selectionTraceHash())
                && left.blueFinalRoleAssignments().equals(right.blueFinalRoleAssignments())
                && left.redFinalRoleAssignments().equals(right.redFinalRoleAssignments())
                && left.matchChampionAssignments().asMap().equals(
                right.matchChampionAssignments().asMap());
    }

    private static Map<String, Object> contract(Path backendRoot) throws IOException {
        AutoDraftSelectionPolicy policy = AutoDraftSelectionPolicy.production();
        LinkedHashMap<String, Object> value = new LinkedHashMap<>();
        value.put("schemaVersion", "AUTO_DRAFT_VARIETY_V1_CONTRACT_V1");
        value.put("purpose", "BOUNDED_PRODUCT_VARIETY_OBSERVATION_NOT_BALANCE_HOLDOUT");
        value.put("populationDraftCount", 80);
        value.put("matchSimulatorExecutionCount", 0);
        value.put("profilePopulationExecutionCount", 0);
        value.put("seeds", AutoDraftVarietyV1Schedule.SEEDS.stream()
                .map(String::valueOf).toList());
        value.put("fixtures", AutoDraftVarietyV1Schedule.FIXTURES);
        value.put("selectionPolicy", policy);
        value.put("selectionPolicyHash", policy.policyHash());
        value.put("correctnessGates", List.of(
                "illegalDraft=0", "duplicateBanOrPick=0", "hardFearlessViolation=0",
                "finalAssignmentError=0", "selectedRankAboveThree=0",
                "selectedScoreLossAboveWindow=0", "sameSeedReplayMismatch=0",
                "policyOrTraceHashMismatch=0", "selectionContextMismatch=0",
                "crossJvmMismatch=0"));
        value.put("varietyGates", Map.of(
                "fixturesWithAtLeastTwoCompleteDrafts", "10/10",
                "fixturesWithAtLeastTwoFinalPickTuples", ">=8/10"));
        value.put("productionSourceTreeHash", treeHash(backendRoot.resolve("src/main/java")));
        value.put("productionResourceTreeHash", treeHash(
                backendRoot.resolve("src/main/resources")));
        value.put("buildGradleSha256", sha256(Files.readAllBytes(
                backendRoot.resolve("build.gradle"))));
        return value;
    }

    private static Map<String, Object> summary(Metrics metrics, String status,
                                               int completeGate, int pickGate,
                                               Path backendRoot) throws IOException {
        LinkedHashMap<String, Object> value = new LinkedHashMap<>();
        value.put("schemaVersion", "AUTO_DRAFT_VARIETY_V1_SUMMARY_V1");
        value.put("status", status);
        value.put("populationDraftCount", metrics.runs.size());
        value.put("sameSeedReplayProbeDraftCount", metrics.sameSeedReplayChecks * 2);
        value.put("sameSeedReplayChecks", metrics.sameSeedReplayChecks);
        value.put("crossJvmChecks", metrics.crossJvmChecks);
        value.put("matchSimulatorExecutionCount", 0);
        value.put("profilePopulationExecutionCount", 0);
        value.put("fixturesWithAtLeastTwoCompleteDrafts", completeGate);
        value.put("fixturesWithAtLeastTwoFinalPickTuples", pickGate);
        value.put("selectedRankCounts", metrics.rankCounts);
        value.put("scoreLossByAction", Map.of(
                "BAN", distribution(metrics.losses.get(DraftActionType.BAN)),
                "PICK", distribution(metrics.losses.get(DraftActionType.PICK))));
        value.put("poolCardinalityCounts", metrics.poolCounts);
        value.put("onlyOneWithinWindowCount", metrics.onlyOneWithinWindow);
        value.put("correctnessErrors", metrics.errors.values);
        value.put("correctnessErrorTotal", metrics.errors.total());
        value.put("fixtureMetrics", metrics.fixtures.stream()
                .map(FixtureMetrics::summary).toList());
        value.put("sourceBinding", Map.of(
                "productionSourceTreeHash", treeHash(backendRoot.resolve("src/main/java")),
                "selectionPolicyHash", MatchEngineV1Policy.DRAFT_SELECTION_POLICY_SHA256));
        return value;
    }

    private static String runsCsv(List<Run> runs) {
        StringBuilder csv = new StringBuilder("fixtureId,blueTeamCode,redTeamCode,seed,")
                .append("draftIdentity,finalPickTuple,selectionTraceHash,selectedRankCounts,")
                .append("poolCardinalityCounts,onlyOneWithinWindowCount\n");
        runs.forEach(run -> csv.append(run.fixtureId).append(',')
                .append(run.blueTeamCode).append(',').append(run.redTeamCode).append(',')
                .append(run.seed).append(',').append(run.draftIdentity).append(',')
                .append(run.finalPickTuple).append(',').append(run.selectionTraceHash).append(',')
                .append(run.selectedRankCounts).append(',').append(run.poolCardinalityCounts)
                .append(',').append(run.onlyOneWithinWindowCount).append('\n'));
        return csv.toString();
    }

    private static String analysis(Metrics metrics, String status,
                                   int completeGate, int pickGate) {
        StringBuilder value = new StringBuilder("# Auto Draft Variety V1\n\n")
                .append("- status: `").append(status).append("`\n")
                .append("- population Drafts: 80 (10 fixtures × 8 fixed seeds)\n")
                .append("- Match Simulator executions: 0\n")
                .append("- same-seed replay checks: ").append(metrics.sameSeedReplayChecks)
                .append("\n- fresh-JVM byte checks: ").append(metrics.crossJvmChecks)
                .append("\n- correctness errors: ").append(metrics.errors.total())
                .append("\n- fixtures with >=2 complete Draft identities: ")
                .append(completeGate).append("/10\n")
                .append("- fixtures with >=2 final pick tuples: ").append(pickGate)
                .append("/10\n\n## Fixture observations\n\n")
                .append("| Fixture | Unique complete Drafts | Unique final pick tuples | ")
                .append("BLUE bans | RED bans | BLUE picks | RED picks |\n")
                .append("| --- | ---: | ---: | ---: | ---: | ---: | ---: |\n");
        metrics.fixtures.forEach(fixture -> value.append("| ")
                .append(fixture.fixture.fixtureId()).append(" | ")
                .append(fixture.draftIdentities.size()).append(" | ")
                .append(fixture.finalPickTuples.size()).append(" | ")
                .append(fixture.uniqueBlueBans.size()).append(" | ")
                .append(fixture.uniqueRedBans.size()).append(" | ")
                .append(fixture.uniqueBluePicks.size()).append(" | ")
                .append(fixture.uniqueRedPicks.size()).append(" |\n"));
        value.append("\nThe fixed seeds are structural variety observations only. ")
                .append("They are not balance, Matchup, Composition, or win-rate holdouts.\n");
        return value.toString();
    }

    private static Map<String, Object> distribution(List<Long> values) {
        List<Long> ordered = values.stream().sorted().toList();
        if (ordered.isEmpty()) return Map.of();
        double median = ordered.size() % 2 == 1
                ? points(ordered.get(ordered.size() / 2))
                : (points(ordered.get(ordered.size() / 2 - 1))
                + points(ordered.get(ordered.size() / 2))) / 2.0;
        int p95Index = Math.max(0, (int) Math.ceil(ordered.size() * 0.95) - 1);
        return Map.of("count", ordered.size(), "min", points(ordered.getFirst()),
                "median", median, "p95", points(ordered.get(p95Index)),
                "max", points(ordered.getLast()));
    }

    private static double points(long fixed) {
        return fixed / 1_000_000.0;
    }

    private static List<ChampionId> allChampions(FinalDraftResult result) {
        return Stream.of(result.blueBans(), result.redBans(),
                        result.bluePicks(), result.redPicks())
                .flatMap(List::stream).toList();
    }

    private static void writeJson(ObjectMapper mapper, Path path, Object value)
            throws IOException {
        mapper.writeValue(path.toFile(), value);
        Files.writeString(path, Files.readString(path, StandardCharsets.UTF_8) + "\n",
                StandardCharsets.UTF_8);
    }

    private static void writeManifest(Path output) throws IOException {
        List<String> names = List.of(
                "auto-draft-variety-v1-analysis.md",
                "auto-draft-variety-v1-contract.json",
                "auto-draft-variety-v1-runs.csv",
                "auto-draft-variety-v1-summary.json");
        StringBuilder manifest = new StringBuilder();
        for (String name : names) {
            manifest.append(sha256(Files.readAllBytes(output.resolve(name))))
                    .append("  ").append(name).append('\n');
        }
        Files.writeString(output.resolve("SHA256SUMS.txt"), manifest,
                StandardCharsets.UTF_8);
    }

    private static String treeHash(Path root) throws IOException {
        if (!Files.exists(root)) return sha256(new byte[0]);
        StringBuilder canonical = new StringBuilder();
        try (Stream<Path> files = Files.walk(root)) {
            for (Path path : files.filter(Files::isRegularFile).sorted().toList()) {
                canonical.append(root.relativize(path).toString().replace('\\', '/'))
                        .append('|').append(sha256(Files.readAllBytes(path))).append('\n');
            }
        }
        return sha256(canonical.toString().getBytes(StandardCharsets.UTF_8));
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException(error);
        }
    }

    private static final class Metrics {
        private final List<Run> runs = new ArrayList<>();
        private final List<FixtureMetrics> fixtures = new ArrayList<>();
        private final Map<Integer, Integer> rankCounts = new TreeMap<>();
        private final Map<Integer, Integer> poolCounts = new TreeMap<>();
        private final Map<DraftActionType, List<Long>> losses =
                new EnumMap<>(DraftActionType.class);
        private final ErrorCounts errors = new ErrorCounts();
        private int onlyOneWithinWindow;
        private int sameSeedReplayChecks;
        private int crossJvmChecks;

        private Metrics() {
            losses.put(DraftActionType.BAN, new ArrayList<>());
            losses.put(DraftActionType.PICK, new ArrayList<>());
        }

        private void accept(FinalDraftResult result) {
            result.selectionTraces().forEach(trace -> {
                rankCounts.merge(trace.selectedRank(), 1, Integer::sum);
                poolCounts.merge(trace.eligiblePool().size(), 1, Integer::sum);
                losses.get(trace.actionType()).add(trace.selectedCanonicalScoreLoss());
                if (trace.reason() == DraftSelectionReason.ONLY_ONE_WITHIN_WINDOW) {
                    onlyOneWithinWindow++;
                }
            });
        }
    }

    private static final class ErrorCounts {
        private final Map<String, Integer> values = new TreeMap<>();

        private ErrorCounts() {
            List.of("illegalDraft", "duplicateBanOrPick", "hardFearlessViolation",
                    "finalAssignmentError", "selectedRankAboveThree",
                    "selectedScoreLossAboveWindow", "sameSeedReplayMismatch",
                    "policyOrTraceHashMismatch", "selectionTraceCardinalityError",
                    "selectionTraceDecisionMismatch", "selectionContextMismatch",
                    "crossJvmMismatch").forEach(key -> values.put(key, 0));
        }

        private void increment(String key) {
            values.merge(key, 1, Integer::sum);
        }

        private int total() {
            return values.values().stream().mapToInt(Integer::intValue).sum();
        }
    }

    private static final class FixtureMetrics {
        private final AutoDraftVarietyV1Schedule.Fixture fixture;
        private final Set<String> draftIdentities = new HashSet<>();
        private final Set<String> finalPickTuples = new HashSet<>();
        private final Set<ChampionId> uniqueBlueBans = new HashSet<>();
        private final Set<ChampionId> uniqueRedBans = new HashSet<>();
        private final Set<ChampionId> uniqueBluePicks = new HashSet<>();
        private final Set<ChampionId> uniqueRedPicks = new HashSet<>();

        private FixtureMetrics(AutoDraftVarietyV1Schedule.Fixture fixture) {
            this.fixture = fixture;
        }

        private void accept(FinalDraftResult result) {
            draftIdentities.add(result.draftIdentity());
            finalPickTuples.add(finalPickTuple(result));
            uniqueBlueBans.addAll(result.blueBans());
            uniqueRedBans.addAll(result.redBans());
            uniqueBluePicks.addAll(result.bluePicks());
            uniqueRedPicks.addAll(result.redPicks());
        }

        private Map<String, Object> summary() {
            return Map.of(
                    "fixtureId", fixture.fixtureId(),
                    "blueTeamCode", fixture.blueTeamCode(),
                    "redTeamCode", fixture.redTeamCode(),
                    "uniqueCompleteDraftIdentityCount", draftIdentities.size(),
                    "uniqueFinalPickTupleCount", finalPickTuples.size(),
                    "uniqueBlueBanCount", uniqueBlueBans.size(),
                    "uniqueRedBanCount", uniqueRedBans.size(),
                    "uniqueBluePickCount", uniqueBluePicks.size(),
                    "uniqueRedPickCount", uniqueRedPicks.size());
        }
    }

    private record Run(String fixtureId, String blueTeamCode, String redTeamCode,
                       long seed, String draftIdentity, String finalPickTuple,
                       String selectionTraceHash, String selectedRankCounts,
                       String poolCardinalityCounts, int onlyOneWithinWindowCount) {
        private static Run from(AutoDraftVarietyV1Schedule.Fixture fixture,
                                long seed, FinalDraftResult result) {
            Map<Integer, Long> ranks = result.selectionTraces().stream().collect(
                    java.util.stream.Collectors.groupingBy(DraftSelectionTrace::selectedRank,
                            TreeMap::new, java.util.stream.Collectors.counting()));
            Map<Integer, Long> pools = result.selectionTraces().stream().collect(
                    java.util.stream.Collectors.groupingBy(
                            trace -> trace.eligiblePool().size(), TreeMap::new,
                            java.util.stream.Collectors.counting()));
            int singleton = (int) result.selectionTraces().stream().filter(trace ->
                    trace.reason() == DraftSelectionReason.ONLY_ONE_WITHIN_WINDOW).count();
            return new Run(fixture.fixtureId(), fixture.blueTeamCode(), fixture.redTeamCode(),
                    seed, result.draftIdentity(),
                    AutoDraftVarietyV1DiagnosticTest.finalPickTuple(result),
                    result.selectionTraceHash(), compact(ranks), compact(pools), singleton);
        }

        private static String compact(Map<Integer, Long> values) {
            return values.entrySet().stream().map(entry ->
                    entry.getKey() + ":" + entry.getValue())
                    .collect(java.util.stream.Collectors.joining("|"));
        }
    }

    private static String finalPickTuple(FinalDraftResult result) {
        return Stream.concat(result.bluePicks().stream(), result.redPicks().stream())
                .map(ChampionId::value).collect(java.util.stream.Collectors.joining("|"));
    }
}
