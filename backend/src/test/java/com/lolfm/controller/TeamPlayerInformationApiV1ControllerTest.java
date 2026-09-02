package com.lolfm.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lolfm.dto.TeamPlayerInformationApiV1Dtos;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class TeamPlayerInformationApiV1ControllerTest {
    private static final String BASE = "/api/v1/reference/leagues/LCK";

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;

    @Test
    void metadataPublishesExactCatalogCountsHashesAndLimitations() throws Exception {
        mvc.perform(get(BASE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.schemaVersion").value(
                        TeamPlayerInformationApiV1Dtos.METADATA_SCHEMA))
                .andExpect(jsonPath("$.leagueCode").value("LCK"))
                .andExpect(jsonPath("$.catalog.catalogSchemaVersion").value(
                        "TEAM_AND_PLAYER_INFORMATION_CATALOG_V1"))
                .andExpect(jsonPath("$.catalog.catalogHash").value(
                        org.hamcrest.Matchers.matchesPattern("[0-9a-f]{64}")))
                .andExpect(jsonPath("$.catalog.sourceResources.length()").value(4))
                .andExpect(jsonPath("$.catalog.sourceResources[0].rawSha256").value(
                        "badbbaa3ae7fbe5eaaf83ee8e97a93134476493a45167ec3d1637c7243909018"))
                .andExpect(jsonPath("$.catalog.sourceResources[1].rawSha256").value(
                        "2312a8bc7d222fd63b57d1255210fb25104432a90a954d854b2090cc2acb28e0"))
                .andExpect(jsonPath("$.catalog.sourceResources[2].rawSha256").value(
                        "2c36b8a109aba9dfe84c1da319fe02708a72a1341d334dc6d5e3f605b0023aad"))
                .andExpect(jsonPath("$.catalog.sourceResources[3].rawSha256").value(
                        "4e4f01fe72f68aca7dcb93afb72b43273201ce0daa7d63613f628597ff41ff19"))
                .andExpect(jsonPath("$.counts.teams").value(10))
                .andExpect(jsonPath("$.counts.players").value(50))
                .andExpect(jsonPath("$.counts.uniquePlayerIds").value(50))
                .andExpect(jsonPath("$.counts.teamHistoryRows").value(248))
                .andExpect(jsonPath("$.counts.teamAchievementRows").value(154))
                .andExpect(jsonPath("$.counts.individualAwardRows").value(21))
                .andExpect(jsonPath("$.counts.sourceRows").value(248))
                .andExpect(jsonPath("$.counts.authoredProficiencies").value(732))
                .andExpect(jsonPath("$.limitations.startersOnly").value(true))
                .andExpect(jsonPath("$.limitations.overallRatingIncluded").value(false))
                .andExpect(jsonPath("$.limitations.affectsGameplayOrRandomIdentity")
                        .value(false));
    }

    @Test
    void teamsAndPlayersUseExactCanonicalOrdering() throws Exception {
        JsonNode teams = json(BASE + "/teams");
        assertThat(teams.path("schemaVersion").asText())
                .isEqualTo(TeamPlayerInformationApiV1Dtos.TEAMS_SCHEMA);
        assertThat(teams.path("teams")).hasSize(10);
        assertThat(texts(teams.path("teams"), "teamCode"))
                .containsExactly("BFX", "BRO", "DK", "DNS", "GEN", "HLE", "KRX",
                        "KT", "NS", "T1");
        assertThat(teams.path("teams")).allSatisfy(team -> {
            assertThat(team.path("starterCount").asInt()).isEqualTo(5);
            assertThat(texts(team.path("lineup"), "position"))
                    .containsExactly("TOP", "JUNGLE", "MID", "ADC", "SUPPORT");
        });

        JsonNode players = json(BASE + "/players");
        assertThat(players.path("players")).hasSize(50);
        assertThat(players.path("players").get(0).path("currentTeamCode").asText())
                .isEqualTo("BFX");
        assertThat(players.path("players").get(0).path("position").asText())
                .isEqualTo("TOP");

        mvc.perform(get(BASE + "/players").param("teamCode", "GEN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.filters.teamCode").value("GEN"))
                .andExpect(jsonPath("$.players.length()").value(5))
                .andExpect(jsonPath("$.players[2].playerId").value("player-chovy"));
        mvc.perform(get(BASE + "/players").param("position", "MID"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.filters.position").value("MID"))
                .andExpect(jsonPath("$.players.length()").value(10));
        mvc.perform(get(BASE + "/players")
                        .param("teamCode", "GEN").param("position", "MID"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.players.length()").value(1))
                .andExpect(jsonPath("$.players[0].playerId").value("player-chovy"));
    }

    @Test
    void genAndChovyCrossRoutesShareStableIdentity() throws Exception {
        JsonNode gen = json(BASE + "/teams/GEN");
        JsonNode players = json(BASE + "/players?teamCode=GEN");
        assertThat(gen.path("team").path("teamCode").asText()).isEqualTo("GEN");
        assertThat(gen.path("team").path("lineup")).hasSize(5);
        assertThat(texts(gen.path("team").path("lineup"), "playerId"))
                .containsExactlyElementsOf(texts(players.path("players"), "playerId"));
        assertThat(gen.path("team").path("lineup").get(2).path("playerId").asText())
                .isEqualTo("player-chovy");
    }

    @Test
    void chovyDetailPreservesCareerRatingAndSparseProficiencyMeaning() throws Exception {
        JsonNode response = json(BASE + "/players/player-chovy");
        JsonNode player = response.path("player");
        assertThat(response.path("schemaVersion").asText())
                .isEqualTo(TeamPlayerInformationApiV1Dtos.PLAYER_SCHEMA);
        assertThat(player.path("summary").path("playerId").asText())
                .isEqualTo("player-chovy");
        assertThat(player.path("summary").path("currentTeamCode").asText())
                .isEqualTo("GEN");
        assertThat(player.path("personal").path("legalName").asText())
                .isEqualTo("Jeong Ji-hoon");
        assertThat(player.path("personal").path("ageAtSnapshot").asInt()).isEqualTo(25);
        assertThat(player.path("contract").path("daysRemainingAtSnapshot").asInt())
                .isEqualTo(448);
        assertThat(player.path("contract").path("status").asText())
                .isEqualTo("UNDER_CONTRACT_THROUGH_2027");
        assertThat(player.path("career").path("teamHistory")).hasSize(6);
        assertThat(player.path("career").path("teamHistory").get(5).path("to").isNull())
                .isTrue();
        assertThat(player.path("ratings").path("attributes")).hasSize(12);
        assertThat(texts(player.path("ratings").path("attributes"), "key"))
                .containsExactly("mechanics", "decisionMaking", "mapAwareness",
                        "positioning", "combatExecution", "consistency", "csAcquisition",
                        "trading", "waveManagement", "lanePressure",
                        "initiativeConversion", "sideLaneManagement");
        assertThat(player.path("championProficiency").path("neutralFallback").asInt())
                .isEqualTo(14);
        assertThat(player.path("championProficiency").path("sparseOverridesOnly")
                .asBoolean()).isTrue();
        JsonNode authored = player.path("championProficiency").path("authoredEntries");
        for (int index = 1; index < authored.size(); index++) {
            JsonNode previous = authored.get(index - 1);
            JsonNode current = authored.get(index);
            int comparison = Integer.compare(previous.path("value").asInt(),
                    current.path("value").asInt());
            assertThat(comparison).isGreaterThanOrEqualTo(0);
            if (comparison == 0) {
                assertThat(previous.path("championId").asText())
                        .isLessThan(current.path("championId").asText());
            }
        }
        assertThat(hasFieldIgnoringCase(player, "ovr")).isFalse();
        assertThat(hasFieldIgnoringCase(player, "ca")).isFalse();
        assertThat(player.path("careerPrizeMoney").path("currency").asText())
                .isEqualTo("USD");
        assertThat(player.path("careerPrizeMoney").path("meaning").asText())
                .contains("not salary");
    }

    @Test
    void repeatedReadsAreByteStableAndDoNotChangeExistingRealMatchOptions() throws Exception {
        byte[] optionsBefore = mvc.perform(get("/api/v1/real-matches/options"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        byte[] first = mvc.perform(get(BASE + "/players/player-chovy"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        byte[] second = mvc.perform(get(BASE + "/players/player-chovy"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        byte[] optionsAfter = mvc.perform(get("/api/v1/real-matches/options"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();

        assertThat(second).isEqualTo(first);
        assertThat(optionsAfter).isEqualTo(optionsBefore);
        JsonNode options = mapper.readTree(optionsAfter);
        assertThat(options.path("teams")).hasSize(10);
        assertThat(options.path("teams").findValues("playerId")).hasSize(50);
    }

    @Test
    void informationReadsDoNotMutateSeasonOrStandingsState() throws Exception {
        String createdText = mvc.perform(post("/api/v1/leagues")
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"schemaVersion":"AI_LEAGUE_CREATE_REQUEST_V1",
                                 "leagueKey":"information-read-isolation",
                                 "seasonKey":"season-a",
                                 "seasonMode":"HYBRID_MANAGER",
                                 "managedTeamCode":"GEN",
                                 "seasonRootSeed":"73",
                                 "clientCommandId":"information-read-isolation-create"}
                                """))
                .andExpect(status().isCreated()).andReturn().getResponse()
                .getContentAsString();
        JsonNode created = mapper.readTree(createdText).path("season");
        String seasonPath = "/api/v1/leagues/" + created.path("leagueId").asText()
                + "/seasons/" + created.path("seasonId").asText();
        JsonNode seasonBefore = json(seasonPath);
        JsonNode standingsBefore = json(seasonPath + "/standings");

        json(BASE);
        json(BASE + "/teams");
        json(BASE + "/teams/GEN");
        json(BASE + "/players?teamCode=GEN&position=MID");
        json(BASE + "/players/player-chovy");

        assertThat(json(seasonPath)).isEqualTo(seasonBefore);
        assertThat(json(seasonPath + "/standings")).isEqualTo(standingsBefore);
    }

    @Test
    void errorsUseStableScopedMachineReadableContracts() throws Exception {
        mvc.perform(get("/api/v1/reference/leagues/LPL"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.schemaVersion").value(
                        TeamPlayerInformationApiV1Dtos.ERROR_SCHEMA))
                .andExpect(jsonPath("$.code").value("REFERENCE_LEAGUE_NOT_FOUND"));
        mvc.perform(get(BASE + "/teams/UNKNOWN"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("REFERENCE_TEAM_NOT_FOUND"));
        mvc.perform(get(BASE + "/players/player-unknown"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("REFERENCE_PLAYER_NOT_FOUND"));
        mvc.perform(get(BASE + "/players/not-a-player-id"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("REFERENCE_PLAYER_NOT_FOUND"));
        mvc.perform(get(BASE + "/players").param("teamCode", "gen"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("REFERENCE_QUERY_INVALID"))
                .andExpect(jsonPath("$.field").value("teamCode"));
        mvc.perform(get(BASE + "/players").param("position", "mid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("REFERENCE_QUERY_INVALID"))
                .andExpect(jsonPath("$.field").value("position"))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("java."))));
        mvc.perform(get(BASE + "/players").param("nickname", "Chovy"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("REFERENCE_QUERY_INVALID"))
                .andExpect(jsonPath("$.field").value("nickname"));
        mvc.perform(get(BASE + "/players")
                        .param("position", "MID", "TOP"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("REFERENCE_QUERY_INVALID"))
                .andExpect(jsonPath("$.field").value("position"));
    }

    private JsonNode json(String path) throws Exception {
        return mapper.readTree(mvc.perform(get(path)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray());
    }

    private static List<String> texts(JsonNode array, String field) {
        List<String> values = new ArrayList<>();
        array.forEach(value -> values.add(value.path(field).asText()));
        return values;
    }

    private static boolean hasFieldIgnoringCase(JsonNode node, String target) {
        if (node.isObject()) {
            Iterator<String> fields = node.fieldNames();
            while (fields.hasNext()) {
                String field = fields.next();
                if (field.equalsIgnoreCase(target)
                        || hasFieldIgnoringCase(node.get(field), target)) return true;
            }
        } else if (node.isArray()) {
            for (JsonNode child : node) {
                if (hasFieldIgnoringCase(child, target)) return true;
            }
        }
        return false;
    }
}
