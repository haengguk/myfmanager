package com.lolfm.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lolfm.application.MatchEngineV1;
import com.lolfm.application.MatchEngineV1Canonicalizer;
import com.lolfm.application.MatchEngineV1Input;
import com.lolfm.application.MatchEngineV1InputFactory;
import com.lolfm.application.RealDraftMatchOrchestrator;
import com.lolfm.application.RealDraftMatchPreflightValidator;
import com.lolfm.application.RealMatchApiV1ResponseMapper;
import com.lolfm.application.RealMatchApiV1Service;
import com.lolfm.application.RealMatchPerformanceBaselineV1Harness;
import com.lolfm.champion.ChampionCatalog;
import com.lolfm.domain.Team;
import com.lolfm.draft.AutoDraftJfrSamplerV1;
import com.lolfm.draft.AutoDraftObservationHarnessV1;
import com.lolfm.draft.AutoDraftScalabilityScheduleV1;
import com.lolfm.draft.DraftDecision;
import com.lolfm.draft.DraftEngine;
import com.lolfm.draft.DraftResourceSet;
import com.lolfm.draft.DraftRuleSet;
import com.lolfm.draft.DraftScoringPolicy;
import com.lolfm.draft.DraftTeamContext;
import com.lolfm.draft.FinalDraftResult;
import com.lolfm.draft.SeriesDraftHistory;
import com.lolfm.dto.RealMatchApiV1Dtos;
import com.lolfm.player.LckTeamAssembler;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/** Candidate/official 12-fixture performance evidence with hard upstream semantic parity. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {"spring.main.banner-mode=off", "logging.level.root=ERROR"})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Tag("diagnostic")
@Tag("draft-engine-performance-hardening-v1")
class DraftEnginePerformanceHardeningV1DiagnosticTest {
    private static final long INPUT_SEED = 73L;

    @Autowired ObjectMapper mapper;
    @Autowired MatchEngineV1Canonicalizer canonicalizer;
    @Autowired RealDraftMatchOrchestrator orchestrator;
    @Autowired LckTeamAssembler teams;
    @Autowired MatchEngineV1InputFactory inputs;
    @Autowired RealMatchApiV1RequestParser requests;
    @Autowired RealMatchApiV1Service service;
    @Autowired RealDraftMatchPreflightValidator preflight;
    @Autowired MatchEngineV1 matchEngine;
    @Autowired RealMatchApiV1ResponseMapper responses;
    @Autowired ChampionCatalog champions;

    @Test
    void captureCandidateOrOfficialEvidence() throws Exception {
        Path backendRoot = Path.of("").toAbsolutePath().normalize();
        Path output = output(backendRoot);
        recreateEmptyOutput(output, backendRoot);
        assertUnaffectedScopes(backendRoot);
        DraftEnginePerformanceHardeningV1Artifacts.UpstreamEvidence upstream =
                DraftEnginePerformanceHardeningV1Artifacts.verifyUpstream(
                        backendRoot, mapper);
        DraftEngine production = field(orchestrator, "drafts", DraftEngine.class);
        AutoDraftObservationHarnessV1 observer =
                new AutoDraftObservationHarnessV1(production);

        warmUpOnce(production);
        ArrayList<DraftEnginePerformanceHardeningV1Artifacts.FixtureRun> fixtures =
                new ArrayList<>();
        ArrayList<DraftEnginePerformanceHardeningV1Artifacts.TurnRun> turns =
                new ArrayList<>();
        ArrayList<Measured> measuredRuns = new ArrayList<>();
        Path jfr = backendRoot.resolve(
                "build/reports/draft-engine-performance-hardening-v1-inputs")
                .resolve(output.getFileName().toString() + ".jfr");
        AutoDraftJfrSamplerV1.Profile profile;
        try (AutoDraftJfrSamplerV1.Session session = AutoDraftJfrSamplerV1.start()) {
            for (AutoDraftScalabilityScheduleV1.Fixture fixture
                    : AutoDraftScalabilityScheduleV1.FIXTURES) {
                for (int ordinal = 1; ordinal <= 2; ordinal++) {
                    measuredRuns.add(measure(observer, fixture, ordinal));
                }
            }
            profile = session.finish(jfr);
        }
        for (AutoDraftScalabilityScheduleV1.Fixture fixture
                : AutoDraftScalabilityScheduleV1.FIXTURES) {
            Measured reference = reference(observer, fixture);
            for (Measured measured : measuredRuns.stream()
                    .filter(value -> value.fixture().equals(fixture)).toList()) {
                    DraftEnginePerformanceHardeningV1Artifacts.FixtureRun fixtureRun =
                            fixtureRun(measured, reference, false);
                    boolean upstreamIdentityExact =
                            DraftEnginePerformanceHardeningV1Artifacts.fixtureParity(
                                    upstream, fixtureRun);
                    boolean fixtureExact = upstreamIdentityExact
                            && sameRunFixtureParity(reference, measured);
                    fixtureRun = fixtureRun(measured, reference, fixtureExact);
                    assertThat(fixtureExact).as("fixture parity %s run %s",
                            fixture.id(), measured.ordinal()).isTrue();
                    fixtures.add(fixtureRun);
                    for (int index = 0; index < measured.observation().turns().size();
                         index++) {
                        DraftEnginePerformanceHardeningV1Artifacts.TurnRun turn =
                                turnRun(measured, index, false, false);
                        boolean upstreamTurnExact =
                                DraftEnginePerformanceHardeningV1Artifacts.turnParity(
                                        upstream, turn)
                                && DraftEnginePerformanceHardeningV1Artifacts
                                .diagnosticRootCandidateScoresParity(upstream, turn);
                        boolean turnExact = sameRunTurnParity(reference, measured, index);
                        assertThat(turnExact).as("same-JVM cached/uncached turn parity %s run %s turn %s",
                                fixture.id(), measured.ordinal(), index + 1).isTrue();
                        turns.add(turnRun(measured, index, turnExact,
                                upstreamTurnExact));
                    }
                }
            }
        assertThat(fixtures).hasSize(24).allMatch(
                DraftEnginePerformanceHardeningV1Artifacts.FixtureRun::exact);
        assertThat(turns).hasSize(480).allMatch(
                DraftEnginePerformanceHardeningV1Artifacts.TurnRun::exact);
        assertThat(fixtures).extracting(
                DraftEnginePerformanceHardeningV1Artifacts.FixtureRun::semanticCounters)
                .containsOnly(fixtures.getFirst().semanticCounters());

        List<DraftEnginePerformanceHardeningV1Artifacts.ApiParity> apiParity =
                apiParity(backendRoot);
        assertThat(apiParity).hasSize(2).allMatch(
                DraftEnginePerformanceHardeningV1Artifacts.ApiParity::exact);
        DraftEnginePerformanceHardeningV1Artifacts.Environment environment =
                environment(backendRoot, upstream, production, apiParity);
        DraftEnginePerformanceHardeningV1Artifacts.write(output, upstream, fixtures,
                turns, profile, environment, apiParity, canonicalizer);
        assertThat(DraftEnginePerformanceHardeningV1Artifacts
                .verifyGeneratedManifest(output)).isEqualTo(7);
        System.out.println(mapper.readTree(output.resolve(
                DraftEnginePerformanceHardeningV1Artifacts.SUMMARY).toFile())
                .path("status").asText() + " output=" + output);
    }

    private void warmUpOnce(DraftEngine production) {
        AutoDraftScalabilityScheduleV1.Fixture fixture =
                AutoDraftScalabilityScheduleV1.FIXTURES.getFirst();
        production.draft(DraftTeamContext.from(teams.assemble(fixture.blueTeamCode())),
                DraftTeamContext.from(teams.assemble(fixture.redTeamCode())),
                new SeriesDraftHistory());
    }

    private Measured measure(AutoDraftObservationHarnessV1 observer,
                             AutoDraftScalabilityScheduleV1.Fixture fixture,
                             int ordinal) {
        Team blueTeam = teams.assemble(fixture.blueTeamCode());
        Team redTeam = teams.assemble(fixture.redTeamCode());
        DraftTeamContext blue = DraftTeamContext.from(blueTeam);
        DraftTeamContext red = DraftTeamContext.from(redTeam);
        AutoDraftObservationHarnessV1.Observation observation = observer.observe(
                blue, red, new SeriesDraftHistory());
        MatchEngineV1Input input = inputs.fromRealDraft(
                fixture.blueTeamCode(), blueTeam, fixture.redTeamCode(), redTeam,
                INPUT_SEED, 1, Set.of(), observation.result());
        return new Measured(fixture, ordinal, observation, input);
    }

    private Measured reference(AutoDraftObservationHarnessV1 observer,
                               AutoDraftScalabilityScheduleV1.Fixture fixture) {
        Team blueTeam = teams.assemble(fixture.blueTeamCode());
        Team redTeam = teams.assemble(fixture.redTeamCode());
        AutoDraftObservationHarnessV1.Observation observation =
                observer.observeUncached(DraftTeamContext.from(blueTeam),
                        DraftTeamContext.from(redTeam), new SeriesDraftHistory());
        MatchEngineV1Input input = inputs.fromRealDraft(
                fixture.blueTeamCode(), blueTeam, fixture.redTeamCode(), redTeam,
                INPUT_SEED, 1, Set.of(), observation.result());
        return new Measured(fixture, 0, observation, input);
    }

    private static boolean sameRunFixtureParity(Measured reference, Measured measured) {
        return AutoDraftObservationHarnessV1.productionEquivalent(
                reference.observation().result(), measured.observation().result())
                && reference.input().canonicalGameplaySerialization().equals(
                measured.input().canonicalGameplaySerialization())
                && reference.input().inputHash().equals(measured.input().inputHash())
                && reference.observation().counters().equals(
                measured.observation().counters());
    }

    private static boolean sameRunTurnParity(Measured reference, Measured measured,
                                             int index) {
        return reference.observation().result().decisions().get(index).equals(
                measured.observation().result().decisions().get(index))
                && reference.observation().turns().get(index).rootCandidateScores().equals(
                measured.observation().turns().get(index).rootCandidateScores())
                && reference.observation().turns().get(index).counters().equals(
                measured.observation().turns().get(index).counters());
    }

    private DraftEnginePerformanceHardeningV1Artifacts.FixtureRun fixtureRun(
            Measured measured, Measured reference, boolean exact) {
        FinalDraftResult result = measured.observation().result();
        return new DraftEnginePerformanceHardeningV1Artifacts.FixtureRun(
                measured.fixture().id(), measured.ordinal(),
                measured.fixture().blueTeamCode(), measured.fixture().redTeamCode(),
                measured.observation().fullDraftNanos(), result.draftIdentity(),
                measured.input().finalDraft().finalDraftHash(),
                measured.input().finalDraft().finalAssignmentHash(),
                measured.input().inputHash(), championList(result.blueBans()),
                championList(result.redBans()), championList(result.bluePicks()),
                championList(result.redPicks()),
                canonicalizer.canonicalJson(roles(result.blueFinalRoleAssignments())),
                canonicalizer.canonicalJson(roles(result.redFinalRoleAssignments())),
                assignments(result), result.decisions().size(),
                (int) result.decisions().stream().filter(value ->
                        value.actionType().name().equals("BAN")).count(),
                (int) result.decisions().stream().filter(value ->
                        value.actionType().name().equals("PICK")).count(),
                measured.observation().counters().toString(),
                reference.observation().computation(),
                measured.observation().computation(), exact);
    }

    private DraftEnginePerformanceHardeningV1Artifacts.TurnRun turnRun(
            Measured measured, int index, boolean exact,
            boolean diagnosticRootCandidateScoresExact) {
        AutoDraftObservationHarnessV1.TurnObservation observed =
                measured.observation().turns().get(index);
        DraftDecision decision = measured.observation().result().decisions().get(index);
        return new DraftEnginePerformanceHardeningV1Artifacts.TurnRun(
                measured.fixture().id(), measured.ordinal(), observed.turn(),
                observed.side().name(), observed.actionType().name(),
                observed.selectedChampionId().value(), observed.turnNanos(),
                decision.immediateScore(), decision.continuationScore(),
                decision.finalSearchScore(), decision.preferredPlan().name(),
                decision.preferredPlanViability(),
                canonicalizer.canonicalJson(decision.componentBreakdown()),
                canonicalizer.canonicalJson(decision.topAlternatives()),
                canonicalizer.canonicalJson(observed.rootCandidateScores()),
                observed.counters().toString(), exact,
                diagnosticRootCandidateScoresExact);
    }

    private List<DraftEnginePerformanceHardeningV1Artifacts.ApiParity> apiParity(
            Path backendRoot) throws IOException {
        RealMatchPerformanceBaselineV1Harness harness =
                new RealMatchPerformanceBaselineV1Harness(mapper, requests, service,
                        orchestrator, teams, preflight, inputs, matchEngine, responses,
                        canonicalizer);
        Path input = backendRoot.resolve(
                "build/reports/real-match-runtime-auto-draft-scalability-v1-inputs");
        return List.of(
                apiParity(harness, input.resolve(
                        "official-hardened-bootrun-fixture-a.json")),
                apiParity(harness, input.resolve(
                        "official-hardened-bootrun-fixture-b.json")));
    }

    private DraftEnginePerformanceHardeningV1Artifacts.ApiParity apiParity(
            RealMatchPerformanceBaselineV1Harness harness, Path baseline) throws IOException {
        JsonNode source = mapper.readTree(baseline.toFile());
        JsonNode fixture = source.path("fixture");
        JsonNode expected = source.path("firstRequest");
        JsonNode request = mapper.createObjectNode()
                .put("schemaVersion", RealMatchApiV1Dtos.REQUEST_SCHEMA)
                .put("blueTeamCode", fixture.path("blueTeamCode").asText())
                .put("redTeamCode", fixture.path("redTeamCode").asText())
                .put("seed", fixture.path("seed").asText());
        RealMatchPerformanceBaselineV1Harness.Execution execution =
                harness.simulate(request, false);
        RealMatchApiV1Dtos.Response response = execution.response();
        RealMatchApiV1Dtos.Integrity integrity = response.integrity();
        RealMatchApiV1Dtos.RandomFingerprint random = integrity.randomFingerprint();
        boolean exact = response.result().winner().name().equals(
                expected.path("winner").asText())
                && response.result().durationSeconds()
                == expected.path("durationSeconds").asInt()
                && response.timeline().events().size()
                == expected.path("eventCount").asInt()
                && response.timeline().snapshots().size()
                == expected.path("snapshotCount").asInt()
                && integrity.outputHash().equals(expected.path("outputHash").asText())
                && integrity.replayProvenanceHash().equals(
                        expected.path("replayProvenanceHash").asText())
                && integrity.simulatorTimelineHash().equals(
                        expected.path("simulatorTimelineHash").asText())
                && integrity.structuredTimelineHash().equals(
                        expected.path("structuredTimelineHash").asText())
                && random.randomDrawCount()
                == expected.path("randomDrawCount").asLong()
                && random.randomTraceHash().equals(
                        expected.path("randomTraceHash").asText())
                && execution.responseCanonicalHash().equals(
                        expected.path("responseCanonicalHash").asText());
        return new DraftEnginePerformanceHardeningV1Artifacts.ApiParity(
                fixture.path("fixtureId").asText(),
                fixture.path("blueTeamCode").asText(),
                fixture.path("redTeamCode").asText(), fixture.path("seed").asText(),
                response.result().winner().name(), response.result().durationSeconds(),
                response.timeline().events().size(),
                response.timeline().snapshots().size(), integrity.outputHash(),
                integrity.replayProvenanceHash(), integrity.simulatorTimelineHash(),
                integrity.structuredTimelineHash(), random.randomDrawCount(),
                random.randomTraceHash(), execution.responseCanonicalHash(),
                execution.resultCanonicalHash(), execution.timelineCanonicalHash(),
                integrity.policyId(), integrity.policyHash(), integrity.runtimeProfileId(),
                integrity.configurationHash(), integrity.engineImplementationVersion(),
                integrity.activeGameplayRulesVersion(),
                integrity.resourceProvenanceHash(), exact);
    }

    private DraftEnginePerformanceHardeningV1Artifacts.Environment environment(
            Path backendRoot,
            DraftEnginePerformanceHardeningV1Artifacts.UpstreamEvidence upstream,
            DraftEngine production,
            List<DraftEnginePerformanceHardeningV1Artifacts.ApiParity> apiParity)
            throws Exception {
        Path repositoryRoot = backendRoot.getParent();
        String currentHead = command(repositoryRoot, "git", "rev-parse", "HEAD");
        boolean ancestor = commandExit(repositoryRoot, "git", "merge-base",
                "--is-ancestor",
                DraftEnginePerformanceHardeningV1Artifacts.REVIEW_BASELINE_COMMIT,
                currentHead) == 0;
        RealMatchApiV1ArtifactWriter.SourceTreeIdentity current =
                RealMatchApiV1ArtifactWriter.productionSourceTree(backendRoot);
        SourceIdentity resourcesIdentity = sourceTree(
                backendRoot.resolve("src/main/resources"),
                "DRAFT_ENGINE_PERFORMANCE_RESOURCES_V1");
        boolean resourcesUnchanged = commandExit(repositoryRoot, "git", "diff",
                "--quiet", "--", "backend/src/main/resources") == 0;
        boolean frontendUnchanged = commandExit(repositoryRoot, "git", "diff",
                "--quiet", "--", "frontend") == 0;
        assertThat(resourcesUnchanged).isTrue();
        assertThat(frontendUnchanged).isTrue();
        DraftResourceSet resources = field(production, "resources", DraftResourceSet.class);
        DraftRuleSet rules = field(production, "rules", DraftRuleSet.class);
        DraftScoringPolicy policy = DraftScoringPolicy.standard();
        String beforeSourceHash = upstream.contract().path("environment")
                .path("productionSourceTreeHash").asText();
        int beforeSourceCount = upstream.contract().path("environment")
                .path("productionSourceFileCount").asInt();
        DraftEnginePerformanceHardeningV1Artifacts.ApiParity api = apiParity.getFirst();
        assertThat(apiParity).allSatisfy(value -> {
            assertThat(value.policyId()).isEqualTo(api.policyId());
            assertThat(value.policyHash()).isEqualTo(api.policyHash());
            assertThat(value.runtimeProfileId()).isEqualTo(api.runtimeProfileId());
            assertThat(value.configurationHash()).isEqualTo(api.configurationHash());
            assertThat(value.engineImplementationVersion())
                    .isEqualTo(api.engineImplementationVersion());
            assertThat(value.activeGameplayRulesVersion())
                    .isEqualTo(api.activeGameplayRulesVersion());
            assertThat(value.resourceProvenanceHash())
                    .isEqualTo(api.resourceProvenanceHash());
        });
        return new DraftEnginePerformanceHardeningV1Artifacts.Environment(
                currentHead, ancestor, System.getProperty("java.version"),
                System.getProperty("java.vm.name"), System.getProperty("os.name"),
                System.getProperty("os.version"), System.getProperty("os.arch"),
                Runtime.getRuntime().availableProcessors(),
                Runtime.getRuntime().maxMemory(),
                System.getProperty("lolfm.gradleVersion", "UNKNOWN"),
                beforeSourceHash, beforeSourceCount, current.hash(), current.fileCount(),
                resourcesIdentity.hash(), resourcesIdentity.hash(),
                resourcesUnchanged,
                DraftEnginePerformanceHardeningV1Artifacts.sha256(
                        canonicalizer.canonicalJson(rules).getBytes(StandardCharsets.UTF_8)),
                resources.meta().metaVersion() + ":"
                        + resources.meta().requiredLegalRoleKeyHash() + ":"
                        + resources.meta().actualLegalRoleKeyHash(),
                DraftEnginePerformanceHardeningV1Artifacts.sha256(
                        canonicalizer.canonicalJson(policy).getBytes(StandardCharsets.UTF_8)),
                api.policyId(), api.policyHash(), api.runtimeProfileId(),
                api.configurationHash(), api.engineImplementationVersion(),
                api.activeGameplayRulesVersion(), api.resourceProvenanceHash(),
                true, frontendUnchanged);
    }

    private static void assertUnaffectedScopes(Path backendRoot) throws Exception {
        Path repositoryRoot = backendRoot.getParent();
        assertThat(commandExit(repositoryRoot, "git", "diff", "--quiet", "--",
                "backend/src/main/resources")).isZero();
        assertThat(commandExit(repositoryRoot, "git", "diff", "--quiet", "--",
                "frontend")).isZero();
    }

    private static SourceIdentity sourceTree(Path root, String schema) throws IOException {
        ArrayList<Path> files = new ArrayList<>();
        try (Stream<Path> paths = Files.walk(root)) {
            paths.filter(Files::isRegularFile).forEach(files::add);
        }
        files.sort(Comparator.comparing(path -> root.relativize(path).toString()
                .replace('\\', '/')));
        StringBuilder canonical = new StringBuilder("schema=").append(schema).append('\n');
        for (Path file : files) {
            canonical.append("file=").append(root.relativize(file).toString()
                            .replace('\\', '/')).append('\n')
                    .append("rawSha256=").append(
                            DraftEnginePerformanceHardeningV1Artifacts.sha256(
                                    Files.readAllBytes(file))).append('\n');
        }
        return new SourceIdentity(files.size(),
                DraftEnginePerformanceHardeningV1Artifacts.sha256(
                        canonical.toString().getBytes(StandardCharsets.UTF_8)));
    }

    private Path output(Path backendRoot) {
        String configured = System.getProperty("draftPerformanceOutput", "candidate");
        Path root = backendRoot.resolve(
                "build/reports/draft-engine-performance-hardening-v1");
        return configured.equals("official") ? root : root.resolve("candidate");
    }

    private static void recreateEmptyOutput(Path output, Path backendRoot)
            throws IOException {
        Path root = backendRoot.resolve(
                "build/reports/draft-engine-performance-hardening-v1")
                .toAbsolutePath().normalize();
        Path normalized = output.toAbsolutePath().normalize();
        if (!(normalized.equals(root) || normalized.equals(root.resolve("candidate")))) {
            throw new IllegalArgumentException("Unexpected performance output directory");
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

    private String assignments(FinalDraftResult result) {
        return result.matchChampionAssignments().asMap().entrySet().stream()
                .sorted(Map.Entry.comparingByKey(
                        Comparator.comparing(value -> value.stableId())))
                .map(entry -> entry.getKey().stableId() + "="
                        + entry.getValue().championId().value() + "@"
                        + entry.getValue().selectedPosition())
                .collect(Collectors.joining("|"));
    }

    private static String championList(List<com.lolfm.champion.ChampionId> values) {
        return values.stream().map(com.lolfm.champion.ChampionId::value)
                .collect(Collectors.joining("|"));
    }

    private static List<Map<String, String>> roles(
            Map<com.lolfm.champion.ChampionId, com.lolfm.domain.Position> values) {
        return values.entrySet().stream().sorted(Comparator.comparing(
                        entry -> entry.getKey().value()))
                .map(entry -> Map.of("championId", entry.getKey().value(),
                        "position", entry.getValue().name())).toList();
    }

    private static <T> T field(Object owner, String name, Class<T> type) {
        try {
            Field field = owner.getClass().getDeclaredField(name);
            field.setAccessible(true);
            return type.cast(field.get(owner));
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException("Production structure changed at " + name,
                    error);
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

    private record Measured(AutoDraftScalabilityScheduleV1.Fixture fixture,
                            int ordinal,
                            AutoDraftObservationHarnessV1.Observation observation,
                            MatchEngineV1Input input) { }

    private record SourceIdentity(int fileCount, String hash) { }
}
