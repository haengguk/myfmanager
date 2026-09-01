package com.lolfm.league;

import com.lolfm.LolfmApplication;
import com.lolfm.simulator.SimulationInstrumentation;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;

/** Fresh-process proof for complete League fixture receipt bytes. */
public final class LeagueAutomatedSeriesRunnerCrossJvmProbe {
    private LeagueAutomatedSeriesRunnerCrossJvmProbe() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 1) throw new IllegalArgumentException("Expected output file");
        try (var context = new SpringApplicationBuilder(LolfmApplication.class)
                .web(WebApplicationType.NONE)
                .properties("spring.main.banner-mode=off", "logging.level.root=ERROR")
                .run()) {
            LeagueProductionSnapshotProvider snapshots = context.getBean(
                    LeagueProductionSnapshotProvider.class);
            LeagueSeasonAggregate season =
                    LeagueAutomatedSeriesRunnerProductionV9Test.productionSeason(snapshots);
            LeagueFixture fixture = LeagueDomainTestFixtures.fixture(
                    season.schedule(), "GEN", "T1");
            LeagueAutomatedSeriesRunResult result = context.getBean(
                    LeagueAutomatedSeriesRunner.class).run(
                    new LeagueAutomatedSeriesRunnerInput(season, fixture,
                            LeagueV1ProductDecisions.productDecisionHash()),
                    SimulationInstrumentation.disabled());
            if (result.status() != LeagueAutomatedSeriesRunResult.Status.COMPLETED) {
                throw new IllegalStateException("Probe did not complete: "
                        + result.failureReason());
            }
            Path output = Path.of(args[0]);
            Files.createDirectories(output.getParent());
            Files.write(output, result.receipt().canonicalBytes());
        }
    }

    static ProcessResult launchFreshJvm(Path output)
            throws IOException, InterruptedException {
        String executable = Path.of(System.getProperty("java.home"), "bin",
                System.getProperty("os.name").toLowerCase(java.util.Locale.ROOT)
                        .contains("win") ? "java.exe" : "java").toString();
        Process process = new ProcessBuilder(executable, "-Xms64m", "-Xmx768m",
                "-XX:MaxMetaspaceSize=256m", "-XX:+UseSerialGC", "-cp",
                System.getProperty("java.class.path"),
                LeagueAutomatedSeriesRunnerCrossJvmProbe.class.getName(),
                output.toString()).redirectErrorStream(true).start();
        String log = new String(process.getInputStream().readAllBytes(),
                StandardCharsets.UTF_8);
        return new ProcessResult(process.waitFor(), log);
    }

    record ProcessResult(int exitCode, String log) { }
}
