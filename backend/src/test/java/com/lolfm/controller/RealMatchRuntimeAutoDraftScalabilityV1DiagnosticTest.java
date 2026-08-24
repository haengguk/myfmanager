package com.lolfm.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lolfm.application.MatchEngineV1Canonicalizer;
import com.lolfm.application.MatchEngineV1Input;
import com.lolfm.application.MatchEngineV1InputFactory;
import com.lolfm.application.RealDraftMatchOrchestrator;
import com.lolfm.domain.Team;
import com.lolfm.draft.AutoDraftJfrSamplerV1;
import com.lolfm.draft.AutoDraftObservationHarnessV1;
import com.lolfm.draft.AutoDraftScalabilityScheduleV1;
import com.lolfm.draft.DraftDecision;
import com.lolfm.draft.DraftEngine;
import com.lolfm.draft.DraftTeamContext;
import com.lolfm.draft.FinalDraftResult;
import com.lolfm.draft.SeriesDraftHistory;
import com.lolfm.player.LckTeamAssembler;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootVersion;
import org.springframework.boot.test.context.SpringBootTest;

/** Official sequential one-global-warmup/two-measured-per-fixture Draft audit. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {"spring.main.banner-mode=off", "logging.level.root=ERROR"})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Tag("diagnostic")
@Tag("real-match-runtime-auto-draft-scalability-v1")
class RealMatchRuntimeAutoDraftScalabilityV1DiagnosticTest {
    private static final long INPUT_PROJECTION_SEED = 73L;
    private static final List<String> RUNTIME_FILES = List.of(
            "official-hardened-bootrun-fixture-a.json",
            "official-hardened-bootrun-fixture-b.json",
            "official-packaged-jar-fixture-a.json",
            "official-packaged-jar-fixture-b.json");

    @Autowired ObjectMapper mapper;
    @Autowired MatchEngineV1Canonicalizer canonicalizer;
    @Autowired RealDraftMatchOrchestrator orchestrator;
    @Autowired LckTeamAssembler teams;
    @Autowired MatchEngineV1InputFactory inputs;

    @Test
    void captureOfficialRuntimeAndAutoDraftAudit() throws Exception {
        Path backendRoot = Path.of("").toAbsolutePath().normalize();
        Path inputDirectory = backendRoot.resolve(
                "build/reports/real-match-runtime-auto-draft-scalability-v1-inputs");
        Path output = backendRoot.resolve(
                "build/reports/real-match-runtime-auto-draft-scalability-v1");
        recreateEmptyOutput(output);
        List<JsonNode> runtime = readRuntime(inputDirectory);
        var upstream = RealMatchRuntimeAutoDraftScalabilityV1Artifacts
                .verifyPerformanceBaselineManifest(backendRoot);
        DraftEngine production = field(orchestrator, "drafts", DraftEngine.class);
        AutoDraftObservationHarnessV1 observer =
                new AutoDraftObservationHarnessV1(production);

        warmUpOnce(production);
        ArrayList<Measured> measured = new ArrayList<>();
        Path jfr = inputDirectory.resolve("official-auto-draft-profile.jfr");
        AutoDraftJfrSamplerV1.Profile profile;
        try (AutoDraftJfrSamplerV1.Session session = AutoDraftJfrSamplerV1.start()) {
            for (AutoDraftScalabilityScheduleV1.Fixture fixture
                    : AutoDraftScalabilityScheduleV1.FIXTURES) {
                for (int ordinal = 1; ordinal <= 2; ordinal++) {
                    measured.add(measure(observer, fixture, ordinal));
                }
            }
            profile = session.finish(jfr);
        }

        ArrayList<RealMatchRuntimeAutoDraftScalabilityV1Artifacts.FixtureRun>
                fixtureRuns = new ArrayList<>();
        ArrayList<RealMatchRuntimeAutoDraftScalabilityV1Artifacts.TurnRun>
                turnRuns = new ArrayList<>();
        for (AutoDraftScalabilityScheduleV1.Fixture fixture
                : AutoDraftScalabilityScheduleV1.FIXTURES) {
            List<Measured> fixtureMeasured = measured.stream()
                    .filter(value -> value.fixture().equals(fixture)).toList();
            Measured representative = fixtureMeasured.getFirst();
            FinalDraftResult reference = production.draft(
                    representative.blueContext(), representative.redContext(),
                    new SeriesDraftHistory());
            MatchEngineV1Input referenceInput = input(
                    fixture, representative.blueTeam(), representative.redTeam(), reference);
            for (Measured value : fixtureMeasured) {
                boolean exact = AutoDraftObservationHarnessV1.productionEquivalent(
                        reference, value.observation().result())
                        && referenceInput.canonicalGameplaySerialization().equals(
                        value.input().canonicalGameplaySerialization())
                        && referenceInput.inputHash().equals(value.input().inputHash());
                assertThat(exact).as("production decomposition parity %s run %s",
                        fixture.id(), value.ordinal()).isTrue();
                fixtureRuns.add(fixtureRun(value, exact));
                turnRuns.addAll(turnRuns(value));
            }
        }

        var environment = environment(backendRoot);
        RealMatchRuntimeAutoDraftScalabilityV1Artifacts.writeOfficial(
                output, runtime, fixtureRuns, turnRuns, profile, environment,
                upstream, canonicalizer);
        RealMatchRuntimeAutoDraftScalabilityV1Artifacts.verifyManifest(output);
        try (Stream<Path> files = Files.list(output)) {
            assertThat(files.map(path -> path.getFileName().toString()).sorted().toList())
                    .containsExactly(
                            "SHA256SUMS.txt", "auto-draft-fixture-runs.csv",
                            "auto-draft-hotspots.json", "auto-draft-turn-runs.csv",
                            "real-match-runtime-auto-draft-scalability-v1-analysis.md",
                            "real-match-runtime-auto-draft-scalability-v1-contract.json",
                            "real-match-runtime-auto-draft-scalability-v1-summary.json",
                            "real-match-runtime-runs.csv");
        }
        System.out.println(RealMatchRuntimeAutoDraftScalabilityV1Artifacts.STATUS
                + " output=" + output);
    }

    private void warmUpOnce(DraftEngine production) {
        AutoDraftScalabilityScheduleV1.Fixture fixture =
                AutoDraftScalabilityScheduleV1.FIXTURES.getFirst();
        Team blueTeam = teams.assemble(fixture.blueTeamCode());
        Team redTeam = teams.assemble(fixture.redTeamCode());
        FinalDraftResult result = production.draft(DraftTeamContext.from(blueTeam),
                DraftTeamContext.from(redTeam), new SeriesDraftHistory());
        input(fixture, blueTeam, redTeam, result);
    }

    private Measured measure(AutoDraftObservationHarnessV1 observer,
                             AutoDraftScalabilityScheduleV1.Fixture fixture,
                             int ordinal) {
        long totalStart = System.nanoTime();
        long start = System.nanoTime();
        Team blueTeam = teams.assemble(fixture.blueTeamCode());
        Team redTeam = teams.assemble(fixture.redTeamCode());
        long rosterNanos = elapsed(start);

        start = System.nanoTime();
        DraftTeamContext blue = DraftTeamContext.from(blueTeam);
        DraftTeamContext red = DraftTeamContext.from(redTeam);
        long contextNanos = elapsed(start);

        start = System.nanoTime();
        SeriesDraftHistory history = new SeriesDraftHistory();
        long historyNanos = elapsed(start);

        AutoDraftObservationHarnessV1.Observation observation =
                observer.observe(blue, red, history);

        start = System.nanoTime();
        MatchEngineV1Input input = input(fixture, blueTeam, redTeam,
                observation.result());
        long inputNanos = elapsed(start);
        long totalNanos = elapsed(totalStart);
        return new Measured(fixture, ordinal, blueTeam, redTeam, blue, red,
                rosterNanos, contextNanos, historyNanos, inputNanos, totalNanos,
                observation, input);
    }

    private MatchEngineV1Input input(AutoDraftScalabilityScheduleV1.Fixture fixture,
                                     Team blueTeam, Team redTeam,
                                     FinalDraftResult result) {
        return inputs.fromRealDraft(fixture.blueTeamCode(), blueTeam,
                fixture.redTeamCode(), redTeam, INPUT_PROJECTION_SEED, 1,
                Set.of(), result);
    }

    private RealMatchRuntimeAutoDraftScalabilityV1Artifacts.FixtureRun fixtureRun(
            Measured value, boolean exact) {
        FinalDraftResult result = value.observation().result();
        MatchEngineV1Input input = value.input();
        long preparation = value.totalNanos();
        return new RealMatchRuntimeAutoDraftScalabilityV1Artifacts.FixtureRun(
                value.fixture().index(), value.fixture().id(), value.ordinal(),
                value.fixture().blueTeamCode(), value.fixture().redTeamCode(),
                value.rosterNanos(), value.contextNanos(), value.historyNanos(),
                value.observation().fullDraftNanos(),
                value.observation().initialPlanNanos(),
                value.observation().finalRoleResolutionNanos(),
                value.observation().finalPlanNanos(),
                value.observation().matchAssignmentProjectionNanos(),
                value.inputNanos(), preparation,
                value.observation().fullDraftNanos() / (double) preparation,
                result.draftIdentity(), input.finalDraft().finalDraftHash(),
                input.finalDraft().finalAssignmentHash(), input.inputHash(),
                championList(result.blueBans()), championList(result.redBans()),
                championList(result.bluePicks()), championList(result.redPicks()),
                canonicalizer.canonicalJson(roles(result.blueFinalRoleAssignments())),
                canonicalizer.canonicalJson(roles(result.redFinalRoleAssignments())),
                assignments(result), result.decisions().size(),
                (int) result.decisions().stream().filter(
                        decision -> decision.actionType()
                                == com.lolfm.draft.DraftActionType.BAN).count(),
                (int) result.decisions().stream().filter(
                        decision -> decision.actionType()
                                == com.lolfm.draft.DraftActionType.PICK).count(),
                exact, value.observation().counters());
    }

    private List<RealMatchRuntimeAutoDraftScalabilityV1Artifacts.TurnRun> turnRuns(
            Measured value) {
        ArrayList<RealMatchRuntimeAutoDraftScalabilityV1Artifacts.TurnRun> result =
                new ArrayList<>();
        for (int index = 0; index < value.observation().turns().size(); index++) {
            AutoDraftObservationHarnessV1.TurnObservation turn =
                    value.observation().turns().get(index);
            DraftDecision decision = value.observation().result().decisions().get(index);
            result.add(new RealMatchRuntimeAutoDraftScalabilityV1Artifacts.TurnRun(
                    value.fixture().id(), value.ordinal(), turn.turn(), turn.side(),
                    turn.actionType(), turn.selectedChampionId().value(), turn.turnNanos(),
                    decision.immediateScore(), decision.continuationScore(),
                    decision.finalSearchScore(), decision.preferredPlan().name(),
                    decision.preferredPlanViability(),
                    canonicalizer.canonicalJson(decision.componentBreakdown()),
                    canonicalizer.canonicalJson(decision.topAlternatives()),
                    canonicalizer.canonicalJson(turn.rootCandidateScores()),
                    turn.counters()));
        }
        return List.copyOf(result);
    }

    private String assignments(FinalDraftResult result) {
        return result.matchChampionAssignments().asMap().entrySet().stream()
                .sorted(java.util.Map.Entry.comparingByKey(
                        Comparator.comparing(value -> value.stableId())))
                .map(entry -> entry.getKey().stableId() + "="
                        + entry.getValue().championId().value() + "@"
                        + entry.getValue().selectedPosition())
                .collect(java.util.stream.Collectors.joining("|"));
    }

    private static String championList(List<com.lolfm.champion.ChampionId> values) {
        return values.stream().map(com.lolfm.champion.ChampionId::value)
                .collect(java.util.stream.Collectors.joining("|"));
    }

    private static List<java.util.Map<String, String>> roles(
            java.util.Map<com.lolfm.champion.ChampionId,
                    com.lolfm.domain.Position> values) {
        return values.entrySet().stream().sorted(Comparator.comparing(
                        entry -> entry.getKey().value()))
                .map(entry -> java.util.Map.of(
                        "championId", entry.getKey().value(),
                        "position", entry.getValue().name())).toList();
    }

    private List<JsonNode> readRuntime(Path directory) throws IOException {
        ArrayList<JsonNode> result = new ArrayList<>();
        for (String file : RUNTIME_FILES) {
            Path path = directory.resolve(file);
            if (!Files.isRegularFile(path)) {
                throw new IllegalStateException("Missing official runtime input " + path);
            }
            result.add(mapper.readTree(path.toFile()));
        }
        return List.copyOf(result);
    }

    private static RealMatchRuntimeAutoDraftScalabilityV1Artifacts.Environment environment(
            Path backendRoot) throws Exception {
        Path repositoryRoot = backendRoot.getParent();
        String currentHead = command(repositoryRoot, "git", "rev-parse", "HEAD");
        boolean ancestor = commandExit(repositoryRoot, "git", "merge-base", "--is-ancestor",
                RealMatchRuntimeAutoDraftScalabilityV1Artifacts.REVIEW_BASELINE_COMMIT,
                currentHead) == 0;
        RealMatchApiV1ArtifactWriter.SourceTreeIdentity production =
                RealMatchApiV1ArtifactWriter.productionSourceTree(backendRoot);
        SourceIdentity verification = verificationSourceTree(backendRoot);
        Runtime runtime = Runtime.getRuntime();
        return new RealMatchRuntimeAutoDraftScalabilityV1Artifacts.Environment(
                currentHead, ancestor, System.getProperty("java.version"),
                System.getProperty("java.vm.name"), System.getProperty("os.name"),
                System.getProperty("os.version"), System.getProperty("os.arch"),
                runtime.availableProcessors(), runtime.maxMemory(),
                System.getProperty("lolfm.gradleVersion", "UNKNOWN"),
                SpringBootVersion.getVersion(), production.hash(), production.fileCount(),
                verification.hash(), verification.fileCount(),
                "OPTIMIZED_LAUNCH_FALSE_NORMAL_TIERED_C2_CAPABLE",
                "UNCHANGED_NORMAL_TIERED_C2_CAPABLE");
    }

    private static SourceIdentity verificationSourceTree(Path backendRoot)
            throws IOException {
        Path root = backendRoot.toAbsolutePath().normalize();
        ArrayList<Path> files = new ArrayList<>();
        try (Stream<Path> paths = Files.walk(root.resolve("src/test/java"))) {
            paths.filter(Files::isRegularFile).filter(path -> {
                String name = path.getFileName().toString();
                return name.startsWith("AutoDraft")
                        || name.startsWith("RealMatchRuntimeAutoDraft")
                        || name.equals("RealMatchExternalRuntimeProbeV1.java");
            }).forEach(files::add);
        }
        files.sort(Comparator.comparing(path -> root.relativize(path).toString()
                .replace('\\', '/')));
        StringBuilder canonical = new StringBuilder(
                "sourceTreeIdentitySchema=REAL_MATCH_RUNTIME_AUTO_DRAFT_SCALABILITY_V1\n");
        for (Path file : files) {
            canonical.append("file=").append(root.relativize(file).toString()
                            .replace('\\', '/')).append('\n')
                    .append("rawSha256=").append(sha256(Files.readAllBytes(file)))
                    .append('\n');
        }
        return new SourceIdentity(sha256(canonical.toString().getBytes(
                StandardCharsets.UTF_8)), files.size());
    }

    private static long elapsed(long start) {
        long value = System.nanoTime() - start;
        if (value < 0L) throw new IllegalStateException("AUDIT_CLOCK_MOVED_BACKWARDS");
        return value;
    }

    private static <T> T field(Object owner, String name, Class<T> type) {
        try {
            Field field = owner.getClass().getDeclaredField(name);
            field.setAccessible(true);
            return type.cast(field.get(owner));
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException("Production field changed: " + name, error);
        }
    }

    private static String command(Path directory, String... command) throws Exception {
        Process process = new ProcessBuilder(command).directory(directory.toFile())
                .redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes(),
                StandardCharsets.UTF_8).trim();
        int exit = process.waitFor();
        if (exit != 0) throw new IllegalStateException("Command failed: " + output);
        return output;
    }

    private static int commandExit(Path directory, String... command) throws Exception {
        Process process = new ProcessBuilder(command).directory(directory.toFile())
                .redirectErrorStream(true).start();
        process.getInputStream().readAllBytes();
        return process.waitFor();
    }

    private static String sha256(byte[] value) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(value));
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException(error);
        }
    }

    private static void recreateEmptyOutput(Path output) throws IOException {
        Path normalized = output.toAbsolutePath().normalize();
        if (!normalized.endsWith(Path.of("build", "reports",
                "real-match-runtime-auto-draft-scalability-v1"))) {
            throw new IllegalArgumentException("Unexpected audit output directory");
        }
        if (Files.exists(normalized)) {
            try (Stream<Path> paths = Files.walk(normalized)) {
                for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                    Files.delete(path);
                }
            }
        }
        Files.createDirectories(normalized);
    }

    private record Measured(
            AutoDraftScalabilityScheduleV1.Fixture fixture, int ordinal,
            Team blueTeam, Team redTeam, DraftTeamContext blueContext,
            DraftTeamContext redContext, long rosterNanos, long contextNanos,
            long historyNanos, long inputNanos, long totalNanos,
            AutoDraftObservationHarnessV1.Observation observation,
            MatchEngineV1Input input) { }

    private record SourceIdentity(String hash, int fileCount) { }
}
