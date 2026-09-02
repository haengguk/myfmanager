package com.lolfm.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
        "lolfm.league.background.enabled=true",
        "spring.main.banner-mode=off",
        "logging.level.root=ERROR"
})
@AutoConfigureMockMvc
class LeagueApiV1BackgroundExecutionIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;

    @Test
    void public202PollsActualBackgroundRoundToTerminalExactlyOnce() throws Exception {
        JsonNode created = json(mvc.perform(post("/api/v1/leagues")
                        .contentType(MediaType.APPLICATION_JSON).content("""
                        {"schemaVersion":"AI_LEAGUE_CREATE_REQUEST_V1",
                         "leagueKey":"background-public-terminal",
                         "seasonKey":"season-a",
                         "seasonMode":"SPECTATOR_FULL_AUTO",
                         "managedTeamCode":null,
                         "seasonRootSeed":"905",
                         "clientCommandId":"background-public-create"}
                        """))
                .andExpect(status().isCreated()).andReturn().getResponse()
                .getContentAsString()).path("season");
        String base = "/api/v1/leagues/" + created.path("leagueId").asText()
                + "/seasons/" + created.path("seasonId").asText();
        String command = """
                {"schemaVersion":"AI_LEAGUE_RUN_ROUND_COMMAND_V1",
                 "expectedLifecycleRevision":%d,
                 "clientCommandId":"background-public-run"}
                """.formatted(created.path("lifecycleRevision").asLong());
        JsonNode accepted = json(mvc.perform(post(base + "/commands/run-current-round")
                        .contentType(MediaType.APPLICATION_JSON).content(command))
                .andExpect(status().isAccepted()).andReturn().getResponse()
                .getContentAsString());
        assertThat(accepted.path("replayed").asBoolean()).isFalse();
        assertThat(accepted.path("queued").asInt()).isEqualTo(5);
        List<String> jobIds = new ArrayList<>();
        accepted.path("jobs").forEach(job -> jobIds.add(job.path("jobId").asText()));

        Instant deadline = Instant.now().plus(Duration.ofMinutes(2));
        List<JsonNode> terminal = List.of();
        while (Instant.now().isBefore(deadline)) {
            ArrayList<JsonNode> latest = new ArrayList<>();
            for (String jobId : jobIds) {
                latest.add(json(mvc.perform(get(base + "/jobs/" + jobId))
                        .andExpect(status().isOk()).andReturn().getResponse()
                        .getContentAsString()).path("job"));
            }
            if (latest.stream().allMatch(job -> "COMPLETED".equals(
                    job.path("lifecycleStatus").asText()))) {
                terminal = List.copyOf(latest);
                break;
            }
            TimeUnit.MILLISECONDS.sleep(100);
        }
        assertThat(terminal).hasSize(5).allSatisfy(job -> {
            assertThat(job.path("lifecycleStatus").asText()).isEqualTo("COMPLETED");
            assertThat(job.path("attemptNumber").asInt()).isOne();
            assertThat(job.path("failureCode").isNull()).isTrue();
        });
        JsonNode season = json(mvc.perform(get(base)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString()).path("season");
        assertThat(season.path("fixtureCounters").path("completed").asInt()).isEqualTo(5);
        assertThat(season.path("standingsRevision").asInt()).isEqualTo(5);
    }

    private JsonNode json(String value) throws Exception {
        return mapper.readTree(value);
    }
}
