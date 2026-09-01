package com.lolfm.league;

import com.lolfm.LolfmApplication;
import com.lolfm.application.PlayerDraftSessionStatus;
import com.lolfm.application.SeriesApiV1Facade;
import com.lolfm.application.SeriesStatus;
import com.lolfm.dto.SeriesApiV1Dtos;
import com.lolfm.simulator.SimulationInstrumentation;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;

/** Fresh-process proof for server binding plus completed Player fixture receipt. */
public final class LeaguePlayerSeriesHandoffCrossJvmProbe {
    private LeaguePlayerSeriesHandoffCrossJvmProbe() {}

    public static void main(String[] args) throws Exception {
        if (args.length != 1) throw new IllegalArgumentException("Expected output file");
        try (var context = new SpringApplicationBuilder(LolfmApplication.class)
                .web(WebApplicationType.NONE)
                .properties("spring.main.banner-mode=off", "logging.level.root=ERROR")
                .run()) {
            LeagueProductionSnapshotProvider snapshots = context.getBean(
                    LeagueProductionSnapshotProvider.class);
            LeagueSeasonAggregate season =
                    LeaguePlayerSeriesHandoffProductionV9Test.productionHybridSeason(
                            snapshots);
            LeagueFixture fixture = LeagueDomainTestFixtures.fixture(
                    season.schedule(), "GEN", "T1");
            LeaguePlayerSeriesHandoffService handoff = context.getBean(
                    LeaguePlayerSeriesHandoffService.class);
            SeriesApiV1Facade series = context.getBean(SeriesApiV1Facade.class);
            var started = handoff.startOrResume(
                    new LeaguePlayerSeriesHandoffService.StartCommand(
                            season.leagueId(), season, fixture.fixtureId(), season.revision(),
                            "fresh-jvm-player-start"));
            if (started.status() != LeaguePlayerSeriesHandoffService.StartStatus.STARTED) {
                throw new IllegalStateException("Player handoff did not start: "
                        + started.reason());
            }
            LeagueFixtureSeriesBindingV1 binding = started.bindingState().binding();
            var view = series.get(binding.boundSeriesId());
            int action = 0;
            while (view.status() == SeriesStatus.ACTIVE) {
                int game = view.currentGameNumber();
                var draft = series.createDraft(view.seriesId(),
                        new SeriesApiV1Dtos.DraftCreateRequest(
                                SeriesApiV1Dtos.DRAFT_CREATE_REQUEST_SCHEMA,
                                view.revision(), "fresh-jvm-draft-" + game));
                view = draft.series();
                var child = draft.draftSession().session();
                while (child.status() == PlayerDraftSessionStatus.ACTIVE) {
                    String champion = child.selectableChampions().getFirst()
                            .champion().championId();
                    var selected = series.draftAction(view.seriesId(), game,
                            new SeriesApiV1Dtos.DraftActionRequest(
                                    SeriesApiV1Dtos.DRAFT_ACTION_REQUEST_SCHEMA,
                                    view.revision(), child.revision(),
                                    "fresh-jvm-action-" + action++, champion));
                    view = selected.series();
                    child = selected.draftSession().session();
                }
                view = series.simulate(view.seriesId(), game,
                        new SeriesApiV1Dtos.SimulateRequest(
                                SeriesApiV1Dtos.SIMULATE_REQUEST_SCHEMA,
                                view.revision(), child.revision(),
                                "fresh-jvm-simulate-" + game)).response().series();
            }
            var completed = handoff.complete(
                    new LeaguePlayerSeriesHandoffService.CompletionCommand(
                            season.leagueId(), season, fixture.fixtureId(),
                            binding.bindingHash()), SimulationInstrumentation.disabled());
            if (completed.status()
                    != LeaguePlayerSeriesHandoffService.CompletionStatus.VERIFIED) {
                throw new IllegalStateException("Player handoff did not verify: "
                        + completed.reason());
            }
            String canonical = "bindingBegin\n" + binding.canonicalText()
                    + "bindingEnd\nreceiptBegin\n"
                    + completed.receipt().canonicalText() + "receiptEnd\n";
            Path output = Path.of(args[0]);
            Files.createDirectories(output.getParent());
            Files.writeString(output, canonical, StandardCharsets.UTF_8);
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
                LeaguePlayerSeriesHandoffCrossJvmProbe.class.getName(),
                output.toString()).redirectErrorStream(true).start();
        String log = new String(process.getInputStream().readAllBytes(),
                StandardCharsets.UTF_8);
        return new ProcessResult(process.waitFor(), log);
    }

    record ProcessResult(int exitCode, String log) {}
}
