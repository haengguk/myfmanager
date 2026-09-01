package com.lolfm.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class LeagueApiV1ControllerTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;

    @Test
    void hybridAndSpectatorCreationAreStrictDurableAndIdempotent() throws Exception {
        String hybridBody = createBody("create-hybrid", "hybrid", "season-a",
                "HYBRID_MANAGER", "GEN", "73");
        String first = mvc.perform(post("/api/v1/leagues")
                        .contentType(MediaType.APPLICATION_JSON).content(hybridBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.schemaVersion").value("AI_LEAGUE_SEASON_VIEW_V1"))
                .andExpect(jsonPath("$.replayed").value(false))
                .andExpect(jsonPath("$.season.seasonMode").value("HYBRID_MANAGER"))
                .andExpect(jsonPath("$.season.managedTeamCode").value("GEN"))
                .andExpect(jsonPath("$.season.fixtureCounters.total").value(90))
                .andExpect(jsonPath("$.season.standings.length()").value(10))
                .andExpect(jsonPath("$.season.currentRound").value(1))
                .andExpect(jsonPath("$.season.allowedCommands[?(@ == 'RUN_CURRENT_ROUND_AUTO_FIXTURES')]")
                        .exists())
                .andReturn().getResponse().getContentAsString();
        JsonNode hybrid = mapper.readTree(first).path("season");
        String leagueId = hybrid.path("leagueId").asText();
        String seasonId = hybrid.path("seasonId").asText();

        mvc.perform(post("/api/v1/leagues")
                        .contentType(MediaType.APPLICATION_JSON).content(hybridBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.replayed").value(true))
                .andExpect(jsonPath("$.season.leagueId").value(leagueId))
                .andExpect(jsonPath("$.season.seasonId").value(seasonId));
        String fixtureText = mvc.perform(get(path(leagueId, seasonId) + "/fixtures"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fixtures.length()").value(90))
                .andReturn().getResponse().getContentAsString();
        int playerFixtures = 0;
        for (JsonNode fixture : mapper.readTree(fixtureText).path("fixtures")) {
            if ("PLAYER_CONTROLLED".equals(fixture.path("executionMode").asText())) {
                playerFixtures++;
            }
        }
        assertThat(playerFixtures).isEqualTo(18);
        mvc.perform(get(path(leagueId, seasonId) + "/standings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.standingsRevision").value(0))
                .andExpect(jsonPath("$.rows.length()").value(10));
        String beforeGet = mvc.perform(get(path(leagueId, seasonId)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String afterGet = mvc.perform(get(path(leagueId, seasonId)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(mapper.readTree(afterGet).path("season").path("lifecycleRevision"))
                .isEqualTo(mapper.readTree(beforeGet).path("season")
                        .path("lifecycleRevision"));
        assertThat(mapper.readTree(afterGet).path("season").path("updatedAt"))
                .isEqualTo(mapper.readTree(beforeGet).path("season").path("updatedAt"));

        mvc.perform(post("/api/v1/leagues")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(hybridBody.replace("\"73\"", "\"74\"")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(
                        "LEAGUE_COMMAND_ID_PAYLOAD_CONFLICT"));
        mvc.perform(post("/api/v1/leagues")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(hybridBody.replace("create-hybrid", "create-hybrid-again")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("LEAGUE_STABLE_KEY_CONFLICT"));
        mvc.perform(get("/api/v1/leagues/league_" + "0".repeat(64)
                        + "/seasons/season_" + "0".repeat(64)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("LEAGUE_SEASON_NOT_FOUND"));

        mvc.perform(post("/api/v1/leagues")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody("create-spectator", "spectator", "season-a",
                                "SPECTATOR_FULL_AUTO", null, "-73")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.season.seasonMode")
                        .value("SPECTATOR_FULL_AUTO"))
                .andExpect(jsonPath("$.season.managedTeamCode").doesNotExist());

        assertCreateRejected(createBody("bad-hybrid", "bad-hybrid", "season-a",
                "HYBRID_MANAGER", null, "73"), 422,
                "LEAGUE_INVALID_MANAGED_TEAM");
        assertCreateRejected(createBody("bad-spectator", "bad-spectator", "season-a",
                "SPECTATOR_FULL_AUTO", "GEN", "73"), 422,
                "LEAGUE_SPECTATOR_MANAGED_TEAM_FORBIDDEN");
        assertCreateRejected(createBody("bad-team", "bad-team", "season-a",
                "HYBRID_MANAGER", "UNKNOWN", "73"), 422,
                "LEAGUE_INVALID_MANAGED_TEAM");
        assertCreateRejected(createBody("bad-seed", "bad-seed", "season-a",
                "SPECTATOR_FULL_AUTO", null, "+73"), 400,
                "LEAGUE_INVALID_ROOT_SEED");
        assertCreateRejected(createBody("authority", "authority", "season-a",
                "SPECTATOR_FULL_AUTO", null, "73").replace("}",
                ",\"winnerTeamCode\":\"GEN\"}"), 400,
                "LEAGUE_UNKNOWN_REQUEST_FIELD");
    }

    @Test
    void runPauseResumeCancelUse202PollingRevisionAndResponseLossReplay()
            throws Exception {
        JsonNode created = create("flow", "HYBRID_MANAGER", "GEN", "101");
        String leagueId = created.path("leagueId").asText();
        String seasonId = created.path("seasonId").asText();
        long readyRevision = created.path("lifecycleRevision").asLong();
        String runBody = runBody("flow-run", readyRevision);
        String runText = mvc.perform(post(path(leagueId, seasonId)
                        + "/commands/run-current-round")
                        .contentType(MediaType.APPLICATION_JSON).content(runBody))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.replayed").value(false))
                .andExpect(jsonPath("$.queued").value(4))
                .andExpect(jsonPath("$.playerFixturesExcluded").value(1))
                .andExpect(jsonPath("$.jobs.length()").value(4))
                .andReturn().getResponse().getContentAsString();
        JsonNode run = mapper.readTree(runText);
        String jobId = run.path("jobs").get(0).path("jobId").asText();
        mvc.perform(get(path(leagueId, seasonId) + "/jobs/" + jobId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.job.lifecycleStatus").value("QUEUED"))
                .andExpect(jsonPath("$.job.leaseToken").doesNotExist());
        mvc.perform(post(path(leagueId, seasonId) + "/commands/run-current-round")
                        .contentType(MediaType.APPLICATION_JSON).content(runBody))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.replayed").value(true))
                .andExpect(jsonPath("$.jobs.length()").value(4));
        mvc.perform(post(path(leagueId, seasonId) + "/commands/run-current-round")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(runBody.replace("\"expectedLifecycleRevision\":"
                                + readyRevision, "\"expectedLifecycleRevision\":999")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(
                        "LEAGUE_COMMAND_ID_PAYLOAD_CONFLICT"));

        long runningRevision = run.path("season").path("lifecycleRevision").asLong();
        mvc.perform(post(path(leagueId, seasonId) + "/commands/run-current-round")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(runBody("flow-stale", readyRevision)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("LEAGUE_STALE_LIFECYCLE_REVISION"))
                .andExpect(jsonPath("$.currentLifecycleRevision").value(runningRevision));

        String pausedText = mvc.perform(post(path(leagueId, seasonId) + "/commands/pause")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(lifecycleBody("flow-pause", runningRevision)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.season.lifecycleStatus").value("PAUSED"))
                .andReturn().getResponse().getContentAsString();
        long pausedRevision = mapper.readTree(pausedText).path("season")
                .path("lifecycleRevision").asLong();
        mvc.perform(post(path(leagueId, seasonId) + "/commands/run-current-round")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(runBody("flow-run-paused", pausedRevision)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(
                        "LEAGUE_ILLEGAL_LIFECYCLE_TRANSITION"));
        String resumedText = mvc.perform(post(path(leagueId, seasonId) + "/commands/resume")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(lifecycleBody("flow-resume", pausedRevision)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.season.lifecycleStatus").value("RUNNING"))
                .andReturn().getResponse().getContentAsString();
        long resumedRevision = mapper.readTree(resumedText).path("season")
                .path("lifecycleRevision").asLong();
        String cancelBody = lifecycleBody("flow-cancel", resumedRevision);
        mvc.perform(delete(path(leagueId, seasonId))
                        .contentType(MediaType.APPLICATION_JSON).content(cancelBody))
                .andExpect(status().isNoContent()).andExpect(content().string(""));
        mvc.perform(delete(path(leagueId, seasonId))
                        .contentType(MediaType.APPLICATION_JSON).content(cancelBody))
                .andExpect(status().isNoContent());
        mvc.perform(get(path(leagueId, seasonId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.season.lifecycleStatus").value("CANCELLED"))
                .andExpect(jsonPath("$.season.allowedCommands.length()").value(1));
    }

    @Test
    void playerFixtureStartsServerOwnedSeriesAndRejectsAuthorityInjection()
            throws Exception {
        JsonNode created = create("player", "HYBRID_MANAGER", "GEN", "202");
        String leagueId = created.path("leagueId").asText();
        String seasonId = created.path("seasonId").asText();
        long revision = created.path("lifecycleRevision").asLong();
        JsonNode fixtures = mapper.readTree(mvc.perform(get(
                        path(leagueId, seasonId) + "/fixtures"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        JsonNode player = firstByMode(fixtures.path("fixtures"), "PLAYER_CONTROLLED");
        JsonNode auto = firstByMode(fixtures.path("fixtures"), "FULL_AUTO");
        String fixtureId = player.path("fixtureId").asText();
        String startBody = playerBody("player-start", revision);
        String startedText = mvc.perform(post(path(leagueId, seasonId) + "/fixtures/"
                        + fixtureId + "/player-series")
                        .contentType(MediaType.APPLICATION_JSON).content(startBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.playerSeries.boundSeriesId")
                        .value(player.path("boundSeriesId").asText()))
                .andReturn().getResponse().getContentAsString();
        JsonNode started = mapper.readTree(startedText).path("playerSeries");
        String bindingHash = started.path("bindingHash").asText();
        String seriesId = started.path("boundSeriesId").asText();
        mvc.perform(post(path(leagueId, seasonId) + "/fixtures/" + fixtureId
                        + "/player-series")
                        .contentType(MediaType.APPLICATION_JSON).content(startBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.replayed").value(true))
                .andExpect(jsonPath("$.playerSeries.bindingHash").value(bindingHash));
        mvc.perform(get(path(leagueId, seasonId) + "/fixtures/" + fixtureId
                        + "/player-series"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.playerSeries.boundSeriesId").value(seriesId));
        mvc.perform(get("/api/v1/series/" + seriesId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.seriesId").value(seriesId));

        mvc.perform(post(path(leagueId, seasonId) + "/fixtures/"
                        + auto.path("fixtureId").asText() + "/player-series")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(playerBody("auto-player-start", revision)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value(
                        "PLAYER_SERIES_REQUIRES_PLAYER_FIXTURE"));
        mvc.perform(post(path(leagueId, seasonId) + "/fixtures/" + fixtureId
                        + "/player-series/completion")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(completionBody("player-complete-early", revision,
                                bindingHash)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PLAYER_SERIES_NOT_COMPLETED"));
        mvc.perform(post(path(leagueId, seasonId) + "/fixtures/" + fixtureId
                        + "/player-series/completion")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(completionBody("player-cross-binding", revision,
                                "a".repeat(64))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value(
                        "PLAYER_SERIES_BINDING_SCOPE_MISMATCH"));
        mvc.perform(get(path(leagueId, seasonId) + "/fixtures/" + fixtureId
                        + "/completion-status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.completion.standingsApplied").value(false));
    }

    @Test
    void twentyConcurrentExactCreateCommandsProduceOneSeason() throws Exception {
        String body = createBody("concurrent-create", "concurrent", "season-a",
                "SPECTATOR_FULL_AUTO", null, "303");
        CountDownLatch start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(20);
        try {
            var futures = new ArrayList<java.util.concurrent.Future<String>>();
            for (int index = 0; index < 20; index++) {
                futures.add(executor.submit(() -> {
                    start.await();
                    return mvc.perform(post("/api/v1/leagues")
                                    .contentType(MediaType.APPLICATION_JSON).content(body))
                            .andReturn().getResponse().getContentAsString();
                }));
            }
            start.countDown();
            List<JsonNode> responses = new ArrayList<>();
            for (var future : futures) {
                responses.add(mapper.readTree(future.get(30, TimeUnit.SECONDS)));
            }
            assertThat(new HashSet<>(responses.stream().map(value -> value.path("season")
                    .path("seasonId").asText()).toList())).hasSize(1);
            assertThat(responses).filteredOn(value -> !value.path("replayed").asBoolean())
                    .hasSize(1);
            assertThat(responses).filteredOn(value -> value.path("replayed").asBoolean())
                    .hasSize(19);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void completedPlayerProductionV9ReconcilesThroughHttpExactlyOnce()
            throws Exception {
        JsonNode created = create("player-complete", "HYBRID_MANAGER", "GEN", "404");
        String leagueId = created.path("leagueId").asText();
        String seasonId = created.path("seasonId").asText();
        long lifecycleRevision = created.path("lifecycleRevision").asLong();
        JsonNode fixtures = mapper.readTree(mvc.perform(get(
                        path(leagueId, seasonId) + "/fixtures"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        JsonNode fixture = firstByMode(fixtures.path("fixtures"), "PLAYER_CONTROLLED");
        String fixtureId = fixture.path("fixtureId").asText();
        JsonNode start = mapper.readTree(mvc.perform(post(path(leagueId, seasonId)
                        + "/fixtures/" + fixtureId + "/player-series")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(playerBody("player-complete-start", lifecycleRevision)))
                .andExpect(status().isCreated()).andReturn().getResponse()
                .getContentAsString()).path("playerSeries");
        String seriesId = start.path("boundSeriesId").asText();
        String bindingHash = start.path("bindingHash").asText();

        JsonNode series = mapper.readTree(mvc.perform(get("/api/v1/series/" + seriesId))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        int command = 0;
        while ("ACTIVE".equals(series.path("status").asText())) {
            int game = series.path("currentGameNumber").asInt();
            String draftText = mvc.perform(post("/api/v1/series/" + seriesId
                            + "/games/current/draft-session")
                            .contentType(MediaType.APPLICATION_JSON).content("""
                                    {"schemaVersion":"SERIES_DRAFT_SESSION_CREATE_REQUEST_V1",
                                     "expectedRevision":%d,
                                     "clientCommandId":"league-http-draft-%d"}
                                    """.formatted(series.path("revision").asLong(), game)))
                    .andExpect(status().isCreated()).andReturn().getResponse()
                    .getContentAsString();
            JsonNode draft = mapper.readTree(draftText);
            series = draft.path("series");
            JsonNode child = draft.at("/draftSession/session");
            while ("ACTIVE".equals(child.path("status").asText())) {
                String champion = child.path("selectableChampions").get(0)
                        .path("champion").path("championId").asText();
                String actionText = mvc.perform(post("/api/v1/series/" + seriesId
                                + "/games/" + game + "/draft-session/actions")
                                .contentType(MediaType.APPLICATION_JSON).content("""
                                        {"schemaVersion":"SERIES_DRAFT_ACTION_REQUEST_V1",
                                         "expectedSeriesRevision":%d,
                                         "expectedDraftRevision":%d,
                                         "clientCommandId":"league-http-action-%d",
                                         "championId":"%s"}
                                        """.formatted(series.path("revision").asLong(),
                                        child.path("revision").asLong(), command++, champion)))
                        .andExpect(status().isOk()).andReturn().getResponse()
                        .getContentAsString();
                JsonNode action = mapper.readTree(actionText);
                series = action.path("series");
                child = action.at("/draftSession/session");
            }
            String simulationText = mvc.perform(post("/api/v1/series/" + seriesId
                            + "/games/" + game + "/simulate")
                            .contentType(MediaType.APPLICATION_JSON).content("""
                                    {"schemaVersion":"SERIES_SIMULATE_REQUEST_V1",
                                     "expectedSeriesRevision":%d,
                                     "expectedDraftRevision":%d,
                                     "clientCommandId":"league-http-simulate-%d"}
                                    """.formatted(series.path("revision").asLong(),
                                    child.path("revision").asLong(), game)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.match.integrity.runtimeProfileId")
                            .value("PRODUCTION_MATCHUP_COMPOSITION_V1"))
                    .andExpect(jsonPath("$.match.integrity.engineImplementationVersion")
                            .value("MATCH_SIMULATOR_ENGINE_IMPLEMENTATION_V9"))
                    .andReturn().getResponse().getContentAsString();
            series = mapper.readTree(simulationText).path("series");
        }
        assertThat(series.path("status").asText()).isEqualTo("COMPLETED");

        String completion = completionBody("player-complete-command",
                lifecycleRevision, bindingHash);
        mvc.perform(post(path(leagueId, seasonId) + "/fixtures/" + fixtureId
                        + "/player-series/completion")
                        .contentType(MediaType.APPLICATION_JSON).content(completion))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.completion.fixtureStatus").value("COMPLETED"))
                .andExpect(jsonPath("$.completion.outboxStatus").value("DELIVERED"))
                .andExpect(jsonPath("$.completion.standingsApplied").value(true))
                .andExpect(jsonPath("$.completion.standingsRevision").value(1));
        mvc.perform(post(path(leagueId, seasonId) + "/fixtures/" + fixtureId
                        + "/player-series/completion")
                        .contentType(MediaType.APPLICATION_JSON).content(completion))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.replayed").value(true))
                .andExpect(jsonPath("$.completion.standingsRevision").value(1));
        mvc.perform(get(path(leagueId, seasonId) + "/standings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.standingsRevision").value(1));
    }

    private JsonNode create(String key, String mode, String managed, String seed)
            throws Exception {
        String text = mvc.perform(post("/api/v1/leagues")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody(key + "-create", key, "season-a", mode,
                                managed, seed)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return mapper.readTree(text).path("season");
    }

    private void assertCreateRejected(String body, int expected, String code)
            throws Exception {
        mvc.perform(post("/api/v1/leagues")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().is(expected)).andExpect(jsonPath("$.code").value(code))
                .andExpect(jsonPath("$.schemaVersion").value("AI_LEAGUE_API_ERROR_V1"));
    }

    private static JsonNode firstByMode(JsonNode fixtures, String mode) {
        for (JsonNode fixture : fixtures) {
            if (mode.equals(fixture.path("executionMode").asText())) return fixture;
        }
        throw new AssertionError("fixture mode not found: " + mode);
    }

    private static String path(String leagueId, String seasonId) {
        return "/api/v1/leagues/" + leagueId + "/seasons/" + seasonId;
    }

    private static String createBody(
            String command,
            String leagueKey,
            String seasonKey,
            String mode,
            String managed,
            String seed
    ) {
        String managedField = managed == null ? "" :
                ",\"managedTeamCode\":\"" + managed + "\"";
        return """
                {"schemaVersion":"AI_LEAGUE_CREATE_REQUEST_V1",
                 "leagueKey":"%s","seasonKey":"%s","seasonMode":"%s"%s,
                 "seasonRootSeed":"%s","clientCommandId":"%s"}
                """.formatted(leagueKey, seasonKey, mode, managedField, seed, command);
    }

    private static String runBody(String command, long revision) {
        return """
                {"schemaVersion":"AI_LEAGUE_RUN_ROUND_COMMAND_V1",
                 "expectedLifecycleRevision":%d,"clientCommandId":"%s"}
                """.formatted(revision, command);
    }

    private static String lifecycleBody(String command, long revision) {
        return """
                {"schemaVersion":"AI_LEAGUE_LIFECYCLE_COMMAND_V1",
                 "expectedLifecycleRevision":%d,"clientCommandId":"%s"}
                """.formatted(revision, command);
    }

    private static String playerBody(String command, long revision) {
        return """
                {"schemaVersion":"AI_LEAGUE_PLAYER_SERIES_COMMAND_V1",
                 "expectedLifecycleRevision":%d,"clientCommandId":"%s"}
                """.formatted(revision, command);
    }

    private static String completionBody(String command, long revision, String binding) {
        return """
                {"schemaVersion":"AI_LEAGUE_PLAYER_COMPLETION_COMMAND_V1",
                 "expectedLifecycleRevision":%d,"clientCommandId":"%s",
                 "bindingHash":"%s"}
                """.formatted(revision, command, binding);
    }
}
