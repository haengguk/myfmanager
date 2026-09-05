package com.lolfm.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lolfm.draft.PlayerControlledDraftEngine;
import com.lolfm.player.LckTeamAssembler;
import com.lolfm.simulator.TeamSide;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/** Explicitly environment-gated official small profiling schedule. */
@EnabledIfEnvironmentVariable(named = "LOLMANAGER_RUN_PLAYER_DRAFT_LATENCY_PROFILE_V1", matches = "1")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {"spring.main.banner-mode=off", "logging.level.root=ERROR"})
class PlayerDraftLatencyProfilingV1DiagnosticTest {

    @Autowired ObjectMapper mapper;
    @Autowired LckTeamAssembler teams;
    @Autowired PlayerControlledDraftEngine drafts;
    @Autowired PlayerDraftApiV1Service service;
    @Autowired PlayerDraftSessionRepository sessions;
    @Autowired PlayerControlledDraftMatchInputBoundary inputs;
    @Autowired MatchEngineV1 matches;
    @Autowired PlayerDraftMatchSimulationExecutor simulations;
    @Autowired PlayerDraftApiV1ResponseMapper responses;
    @Autowired MatchEngineV1Canonicalizer canonicalizer;

    @Test
    void captureOfficialPlayerDraftInteractiveAndSimulationLatencyProfile()
            throws Exception {
        Path backend = Path.of("").toAbsolutePath().normalize();
        Path input = backend.resolve("build/reports/"
                + "player-draft-interactive-simulation-latency-profiling-v1-inputs/"
                + "browser-runs.json");
        Path output = backend.resolve("build/reports/"
                + "player-draft-interactive-simulation-latency-profiling-v1");
        assertThat(Files.isRegularFile(input)).as("actual Chromium input").isTrue();
        assertThat(Files.notExists(output)).as("fresh official output").isTrue();
        List<JsonNode> browserRuns = readBrowserRuns(input);

        PlayerDraftLatencyProfilingV1Harness harness = harness();
        ArrayList<PlayerDraftLatencyProfilingV1Harness.FlowObservation> flows =
                new ArrayList<>();
        var blueCold = harness.run("direct-blue-cold", "GEN", "T1", 73L,
                TeamSide.BLUE, List.of(), true,
                PlayerDraftLatencyProfilingV1Harness.RunKind.COLD);
        flows.add(blueCold);
        flows.add(harness.run("direct-blue-warm-1", "GEN", "T1", 73L,
                TeamSide.BLUE, blueCold.actionScript(), true,
                PlayerDraftLatencyProfilingV1Harness.RunKind.WARM));
        flows.add(harness.run("direct-blue-warm-2", "GEN", "T1", 73L,
                TeamSide.BLUE, blueCold.actionScript(), true,
                PlayerDraftLatencyProfilingV1Harness.RunKind.WARM));
        var redWarm = harness.run("direct-red-warm-1", "GEN", "T1", 73L,
                TeamSide.RED, List.of(), true,
                PlayerDraftLatencyProfilingV1Harness.RunKind.WARM);
        flows.add(redWarm);
        flows.add(harness.run("direct-red-warm-2", "GEN", "T1", 73L,
                TeamSide.RED, redWarm.actionScript(), true,
                PlayerDraftLatencyProfilingV1Harness.RunKind.WARM));

        Path jfrFile = backend.resolve("build/reports/"
                + "player-draft-interactive-simulation-latency-profiling-v1-inputs/"
                + "official-small-profile.jfr");
        PlayerDraftLatencyJfrSamplerV1.Profile jfr;
        try (PlayerDraftLatencyJfrSamplerV1.Session recording =
                     PlayerDraftLatencyJfrSamplerV1.start()) {
            harness.run("jfr-blue-warm", "GEN", "T1", 73L, TeamSide.BLUE,
                    blueCold.actionScript(), true,
                    PlayerDraftLatencyProfilingV1Harness.RunKind.WARM);
            jfr = recording.finish(jfrFile);
        }

        PlayerDraftLatencyProfilingV1Artifacts.writeOfficial(
                output, flows, browserRuns, jfr, environment(), canonicalizer);
        PlayerDraftLatencyProfilingV1Artifacts.verifyManifest(output);
        assertThat(Files.readAllLines(output.resolve(
                PlayerDraftLatencyProfilingV1Artifacts.ACTIONS))).hasSize(51);
        int expectedAiCsvLines = 1 + flows.stream()
                .mapToInt(flow -> flow.aiTurns().size()).sum();
        assertThat(Files.readAllLines(output.resolve(
                PlayerDraftLatencyProfilingV1Artifacts.AI_TURNS)))
                .hasSize(expectedAiCsvLines);
        assertThat(Files.readAllLines(output.resolve(
                PlayerDraftLatencyProfilingV1Artifacts.SIMULATIONS))).hasSize(11);
        assertThat(Files.readAllLines(output.resolve(
                PlayerDraftLatencyProfilingV1Artifacts.BROWSER))).hasSize(23);
        assertThat(Files.list(output).map(path -> path.getFileName().toString())
                .sorted().toList()).containsExactly(
                "SHA256SUMS.txt", "analysis.md", "browser-runs.csv", "hotspots.json",
                "interactive-action-runs.csv", "interactive-ai-turns.csv",
                "phase-summary.json", "profiling-contract.json", "recommendation.json",
                "simulation-runs.csv");
        System.out.println(PlayerDraftLatencyProfilingV1Artifacts.STATUS
                + " output=" + output);
    }

    private List<JsonNode> readBrowserRuns(Path input) throws Exception {
        JsonNode root = mapper.readTree(Files.readString(input));
        assertThat(root.isArray()).isTrue();
        ArrayList<JsonNode> result = new ArrayList<>();
        root.forEach(result::add);
        return List.copyOf(result);
    }

    private PlayerDraftLatencyProfilingV1Harness harness() {
        return new PlayerDraftLatencyProfilingV1Harness(
                mapper, teams, drafts, service, sessions, inputs, matches, simulations,
                responses, canonicalizer);
    }

    private PlayerDraftLatencyProfilingV1Artifacts.Environment environment() {
        Map<String, String> manifests = new LinkedHashMap<>();
        manifests.put("real-match-performance-baseline-v1",
                "c9b4659c4d602fb33c7295885cdc2685a4991469cc4cc0b097ca2d1a20cb26ee");
        manifests.put("real-match-runtime-auto-draft-scalability-v1",
                "751cb19ccf55b34cc0bf4a410a292ba66df4e84d566dd1e217b4a68712d3be8b");
        manifests.put("draft-engine-performance-hardening-v1",
                "ae11f4eb368a8b796a113b32963048a764509b0bb98e27ebce313b7ec645d694");
        manifests.put("real-match-transport-compression-v1",
                "860f6cea4e8dfc42e1a38148dc5c2763331bcd899d784670af4e3222d89a068f");
        return new PlayerDraftLatencyProfilingV1Artifacts.Environment(
                environment("LOLMANAGER_PROFILE_HEAD"),
                PlayerDraftLatencyProfilingV1Artifacts.REVIEW_BASELINE_COMMIT,
                environment("LOLMANAGER_PROFILE_SOURCE_IDENTITY"),
                "SHA256_OF_HEAD_PLUS_OWNED_EXECUTABLE_AND_VERIFICATION_DIFF",
                System.getProperty("java.version"), System.getProperty("java.vendor"),
                System.getProperty("java.vm.name"),
                "NORMAL_TIERED_COMPILATION_NO_TIERED_STOP_AT_LEVEL_OVERRIDE",
                System.getProperty("os.name"), System.getProperty("os.version"),
                System.getProperty("os.arch"), Runtime.getRuntime().availableProcessors(),
                Runtime.getRuntime().maxMemory(),
                "SPRING_BOOT_TEST_NONE_SEQUENTIAL_2G_MAX_HEAP",
                "FRESH_BOOTRUN_PER_CONTROLLED_SIDE",
                "VITE_DEV_LIVE_PROVIDER_PLAYWRIGHT_CHROMIUM",
                "PRECHECK_NO_COMPETING_GRADLE_JAVA_NODE_VITE_CHROME_PROCESS",
                Map.copyOf(manifests));
    }

    private static String environment(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing profiling environment identity: " + name);
        }
        return value;
    }
}
