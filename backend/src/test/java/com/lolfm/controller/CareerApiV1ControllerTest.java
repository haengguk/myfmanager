package com.lolfm.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lolfm.career.CareerIdentity;
import com.lolfm.dto.CareerApiV1Dtos;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class CareerApiV1ControllerTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired JdbcTemplate jdbc;

    @Test
    void createListGetReplayConflictAndStrictErrorsPreserveLeagueState() throws Exception {
        String commandId = UUID.randomUUID().toString();
        int careersBefore = count("career_save");
        int seasonsBefore = count("league_season");
        int roundsBefore = count("league_round");
        int fixturesBefore = count("league_fixture");
        int standingsBefore = count("league_standing");
        int jobsBefore = count("league_job");

        String request = createBody("  GEN 장기 저장  ", "  김 감독  ", "GEN", commandId);
        JsonNode created = json(mvc.perform(post("/api/v1/careers")
                        .contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isCreated()).andReturn().getResponse()
                .getContentAsString()).path("career");

        assertThat(created.path("schemaVersion").asText())
                .isEqualTo(CareerApiV1Dtos.VIEW_SCHEMA);
        assertThat(created.path("saveName").asText()).isEqualTo("GEN 장기 저장");
        assertThat(created.path("managerName").asText()).isEqualTo("김 감독");
        assertThat(created.path("managedTeamCode").asText()).isEqualTo("GEN");
        assertThat(created.path("startDate").asText()).isEqualTo("2026-08-24");
        assertThat(created.path("currentDate").asText()).isEqualTo("2026-08-24");
        assertThat(created.path("lifecycleStatus").asText()).isEqualTo("ACTIVE");
        assertThat(created.path("revision").asLong()).isZero();
        assertThat(created.path("rootSeedAlgorithmId").asText())
                .isEqualTo("CAREER_ROOT_SEED_SHA256_FIRST_8_BYTES_BIG_ENDIAN_SIGNED_LONG_V1");
        assertThat(created.path("rootSeed").isTextual()).isTrue();
        assertThat(created.path("referenceCatalogVersion").asText())
                .isEqualTo("lck-team-and-player-information-2026-08-24-v1");
        assertThat(created.path("referenceCatalogHash").asText())
                .isEqualTo("4b5af4a49b5299b850015ea162be7e28543b1c4cb87e672120f84b26af815504");
        assertThat(created.path("resume").path("kind").asText())
                .isEqualTo("LEAGUE_DASHBOARD");
        assertThat(created.path("resume").path("seasonLifecycleStatus").asText())
                .isEqualTo("READY");
        assertThat(created.path("resume").path("currentRound").asInt()).isOne();
        assertThat(created.path("resume").path("lifecycleRevision").asLong()).isOne();
        assertThat(created.path("resume").path("standingsRevision").asLong()).isZero();
        assertThat(created.path("resume").path("allowedCommands"))
                .extracting(JsonNode::asText)
                .containsExactly("VIEW_STANDINGS",
                        "RUN_CURRENT_ROUND_AUTO_FIXTURES", "CANCEL_SEASON");

        assertThat(count("career_save") - careersBefore).isOne();
        assertThat(count("league_season") - seasonsBefore).isOne();
        assertThat(count("league_round") - roundsBefore).isEqualTo(18);
        assertThat(count("league_fixture") - fixturesBefore).isEqualTo(90);
        assertThat(count("league_standing") - standingsBefore).isEqualTo(10);
        String seasonId = created.path("seasonId").asText();
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM league_fixture
                WHERE season_id = ? AND execution_mode = 'PLAYER_CONTROLLED'
                """, Integer.class, seasonId)).isEqualTo(18);
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM league_fixture
                WHERE season_id = ? AND execution_mode = 'FULL_AUTO'
                """, Integer.class, seasonId)).isEqualTo(72);
        assertThat(count("league_job") - jobsBefore).isZero();

        JsonNode replay = json(mvc.perform(post("/api/v1/careers")
                        .contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isOk()).andReturn().getResponse()
                .getContentAsString());
        assertThat(replay.path("replayed").asBoolean()).isTrue();
        assertThat(replay.path("career").path("careerId")).isEqualTo(
                created.path("careerId"));
        assertThat(replay.path("career").path("leagueId")).isEqualTo(
                created.path("leagueId"));
        assertThat(replay.path("career").path("seasonId")).isEqualTo(
                created.path("seasonId"));
        assertThat(replay.path("career").path("rootSeed")).isEqualTo(
                created.path("rootSeed"));
        assertThat(replay.path("career").path("bindingHash")).isEqualTo(
                created.path("bindingHash"));
        assertThat(count("career_save") - careersBefore).isOne();
        assertThat(count("league_season") - seasonsBefore).isOne();
        assertThat(count("league_fixture") - fixturesBefore).isEqualTo(90);

        JsonNode conflict = json(mvc.perform(post("/api/v1/careers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody("다른 이름", "김 감독", "GEN", commandId)))
                .andExpect(status().isConflict()).andReturn().getResponse()
                .getContentAsString());
        assertThat(conflict.path("schemaVersion").asText())
                .isEqualTo(CareerApiV1Dtos.ERROR_SCHEMA);
        assertThat(conflict.path("code").asText()).isEqualTo("CAREER_COMMAND_CONFLICT");
        assertThat(count("career_save") - careersBefore).isOne();
        assertThat(count("league_season") - seasonsBefore).isOne();

        String careerId = created.path("careerId").asText();
        JsonNode detail = json(mvc.perform(get("/api/v1/careers/" + careerId))
                .andExpect(status().isOk()).andReturn().getResponse()
                .getContentAsString());
        JsonNode list = json(mvc.perform(get("/api/v1/careers"))
                .andExpect(status().isOk()).andReturn().getResponse()
                .getContentAsString());
        JsonNode summary = findCareer(list.path("careers"), careerId);
        assertThat(list.path("currentCount").asInt())
                .isEqualTo(list.path("careers").size());
        assertThat(list.path("maximumCount").asInt()).isEqualTo(100);
        assertThat(list.path("remainingCount").asInt())
                .isEqualTo(100 - list.path("currentCount").asInt());
        assertThat(detail.path("bindingHash")).isEqualTo(created.path("bindingHash"));
        java.util.HashSet<String> summaryFields = new java.util.HashSet<>();
        summary.fieldNames().forEachRemaining(summaryFields::add);
        assertThat(summaryFields).containsExactlyInAnyOrderElementsOf(Set.of(
                "careerId", "saveName", "managerName", "managedTeamCode",
                "currentDate", "leagueId", "seasonId", "lifecycleStatus",
                "resumeKind", "updatedAt"));
        assertThat(summary.path("resumeKind").asText()).isEqualTo("LEAGUE_DASHBOARD");

        long lifecycleBeforeReads = jdbc.queryForObject("""
                SELECT lifecycle_revision FROM league_season WHERE season_id = ?
                """, Long.class, seasonId);
        long standingsRevisionBeforeReads = jdbc.queryForObject("""
                SELECT revision FROM league_season WHERE season_id = ?
                """, Long.class, seasonId);
        mvc.perform(get("/api/v1/careers/" + careerId)).andExpect(status().isOk());
        mvc.perform(get("/api/v1/careers")).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("""
                SELECT lifecycle_revision FROM league_season WHERE season_id = ?
                """, Long.class, seasonId)).isEqualTo(lifecycleBeforeReads);
        assertThat(jdbc.queryForObject("""
                SELECT revision FROM league_season WHERE season_id = ?
                """, Long.class, seasonId)).isEqualTo(standingsRevisionBeforeReads);

        JsonNode calendar = json(mvc.perform(get("/api/v1/careers/" + careerId
                        + "/calendar"))
                .andExpect(status().isOk()).andReturn().getResponse()
                .getContentAsString());
        assertThat(calendar.path("schemaVersion").asText())
                .isEqualTo(CareerApiV1Dtos.CALENDAR_VIEW_SCHEMA);
        assertThat(calendar.path("activeCalendarSeasonYear").asInt()).isEqualTo(2027);
        assertThat(calendar.path("currentDate").asText()).isEqualTo("2026-08-24");
        assertThat(calendar.path("provenance").path("referenceYear").asInt())
                .isEqualTo(2026);
        assertThat(calendar.path("provenance").path("sourceAsOf").asText())
                .isEqualTo("2026-08-23");
        assertThat(calendar.path("provenance").path("referenceCatalogSnapshotAt").asText())
                .isEqualTo("2026-08-24");
        assertThat(calendar.path("provenance").path("sourceCount").asInt()).isEqualTo(15);
        assertThat(calendar.path("provenance").path("calendarDefinitionCount").asInt())
                .isEqualTo(11);
        assertThat(calendar.path("provenance").path("qualificationEdgeCount").asInt())
                .isEqualTo(6);
        assertThat(calendar.path("provenance").path("derivedRestWindowCount").asInt())
                .isEqualTo(7);
        assertThat(calendar.path("pendingOfficialFields")).hasSize(6);
        assertThat(calendar.path("upcomingEvents")).hasSize(8);
        assertThat(calendar.path("upcomingEvents").toString()).doesNotContain("KESPA");
        assertThat(calendar.path("sourceDataNotes").get(0).path("status").asText())
                .isEqualTo("SOURCE_DATA_NOT_PRESENT");
        assertThat(calendar.path("fixtureOverlay").path("scheduleStatus").asText())
                .isEqualTo("GAME_DERIVED_SCHEDULE_POLICY");
        assertThat(jdbc.queryForObject("""
                SELECT lifecycle_revision FROM league_season WHERE season_id = ?
                """, Long.class, seasonId)).isEqualTo(lifecycleBeforeReads);

        String advanceCommand = UUID.randomUUID().toString();
        String advanceBody = advanceBody(0, "ADVANCE_TO_NEXT_EVENT", advanceCommand);
        JsonNode advanced = json(mvc.perform(post("/api/v1/careers/" + careerId
                        + "/advance").contentType(MediaType.APPLICATION_JSON)
                        .content(advanceBody)).andExpect(status().isOk()).andReturn()
                .getResponse().getContentAsString());
        assertThat(advanced.path("schemaVersion").asText())
                .isEqualTo(CareerApiV1Dtos.ADVANCE_RESPONSE_SCHEMA);
        assertThat(advanced.path("pending").asBoolean()).isFalse();
        assertThat(advanced.path("calendar").path("currentDate").asText())
                .isEqualTo("2027-01-14");
        assertThat(advanced.path("calendar").path("calendarRevision").asLong())
                .isEqualTo(1);
        int advanceReceipts = count("career_calendar_advance_command");
        JsonNode advanceReplay = json(mvc.perform(post("/api/v1/careers/" + careerId
                        + "/advance").contentType(MediaType.APPLICATION_JSON)
                        .content(advanceBody)).andExpect(status().isOk()).andReturn()
                .getResponse().getContentAsString());
        assertThat(advanceReplay.path("replayed").asBoolean()).isTrue();
        assertThat(advanceReplay.path("calendar").path("calendarRevision").asLong())
                .isEqualTo(1);
        assertThat(count("career_calendar_advance_command")).isEqualTo(advanceReceipts);
        JsonNode staleAdvance = json(mvc.perform(post("/api/v1/careers/" + careerId
                        + "/advance").contentType(MediaType.APPLICATION_JSON)
                        .content(advanceBody(0, "ADVANCE_ONE_DAY",
                                UUID.randomUUID().toString())))
                .andExpect(status().isConflict()).andReturn().getResponse()
                .getContentAsString());
        assertThat(staleAdvance.path("code").asText())
                .isEqualTo("CAREER_CALENDAR_STALE_REVISION");
        assertThat(count("career_calendar_advance_command")).isEqualTo(advanceReceipts);
        assertThat(json(mvc.perform(get("/api/v1/careers/" + careerId))
                .andExpect(status().isOk()).andReturn().getResponse()
                .getContentAsString()).path("currentDate").asText())
                .isEqualTo("2027-01-14");

        int beforeInvalid = count("career_save");
        assertInvalid("""
                {"schemaVersion":"CAREER_CREATE_REQUEST_V1","saveName":"x",
                 "managerName":"m","managedTeamCode":"GEN",
                 "clientCommandId":"%s","seasonRootSeed":"73"}
                """.formatted(UUID.randomUUID()), 400, "CAREER_REQUEST_INVALID");
        assertInvalid("""
                {"schemaVersion":"CAREER_CREATE_REQUEST_V1","saveName":"x",
                 "saveName":"y","managerName":"m","managedTeamCode":"GEN",
                 "clientCommandId":"%s"}
                """.formatted(UUID.randomUUID()), 400, "CAREER_REQUEST_INVALID");
        assertInvalid(createBody("x", "m", "gen", UUID.randomUUID().toString()),
                422, "CAREER_MANAGED_TEAM_NOT_FOUND");
        assertInvalid(createBody(" ", "m", "GEN", UUID.randomUUID().toString()),
                400, "CAREER_REQUEST_INVALID");
        assertInvalid(createBody("x", "m", "GEN", "not-a-uuid"),
                400, "CAREER_REQUEST_INVALID");
        assertThat(count("career_save")).isEqualTo(beforeInvalid);

        JsonNode missing = json(mvc.perform(get("/api/v1/careers/career_"
                        + "0".repeat(64))).andExpect(status().isNotFound())
                .andReturn().getResponse().getContentAsString());
        assertThat(missing.path("code").asText()).isEqualTo("CAREER_NOT_FOUND");

        JsonNode secondCareer = json(mvc.perform(post("/api/v1/careers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody("GEN 장기 저장", "김 감독", "GEN",
                                UUID.randomUUID().toString())))
                .andExpect(status().isCreated()).andReturn().getResponse()
                .getContentAsString()).path("career");
        assertThat(secondCareer.path("careerId")).isNotEqualTo(created.path("careerId"));
        assertThat(secondCareer.path("leagueId")).isNotEqualTo(created.path("leagueId"));
        assertThat(secondCareer.path("seasonId")).isNotEqualTo(created.path("seasonId"));
        assertThat(secondCareer.path("rootSeed")).isNotEqualTo(created.path("rootSeed"));
        assertThat(count("career_save") - careersBefore).isEqualTo(2);
        assertThat(count("league_season") - seasonsBefore).isEqualTo(2);
        assertThat(count("league_fixture") - fixturesBefore).isEqualTo(180);

        jdbc.update("""
                UPDATE career_create_command SET command_schema = 'TAMPERED'
                WHERE client_command_id = ?
                """, commandId);
        JsonNode tamperedSchema = json(mvc.perform(post("/api/v1/careers")
                        .contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isInternalServerError()).andReturn().getResponse()
                .getContentAsString());
        assertThat(tamperedSchema.path("code").asText())
                .isEqualTo("CAREER_COMMAND_RECEIPT_INTEGRITY_FAILURE");
        assertThat(tamperedSchema.toString()).doesNotContain("Jdbc", "SQL", "/mnt/", "C:\\");
        jdbc.update("""
                UPDATE career_create_command SET command_schema = ?
                WHERE client_command_id = ?
                """, CareerIdentity.COMMAND_SCHEMA, commandId);

        jdbc.update("""
                UPDATE career_create_command SET career_id = ?
                WHERE client_command_id = ?
                """, secondCareer.path("careerId").asText(), commandId);
        JsonNode tamperedTarget = json(mvc.perform(post("/api/v1/careers")
                        .contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isInternalServerError()).andReturn().getResponse()
                .getContentAsString());
        assertThat(tamperedTarget.path("code").asText())
                .isEqualTo("CAREER_COMMAND_RECEIPT_INTEGRITY_FAILURE");
        assertThat(tamperedTarget.toString()).doesNotContain("Jdbc", "SQL", "/mnt/", "C:\\");
        jdbc.update("""
                UPDATE career_create_command SET career_id = ?
                WHERE client_command_id = ?
                """, careerId, commandId);

        JsonNode ordered = json(mvc.perform(get("/api/v1/careers"))
                .andExpect(status().isOk()).andReturn().getResponse()
                .getContentAsString()).path("careers");
        for (int index = 1; index < ordered.size(); index++) {
            JsonNode previous = ordered.get(index - 1);
            JsonNode current = ordered.get(index);
            java.time.OffsetDateTime previousTime = java.time.OffsetDateTime.parse(
                    previous.path("updatedAt").asText());
            java.time.OffsetDateTime currentTime = java.time.OffsetDateTime.parse(
                    current.path("updatedAt").asText());
            assertThat(previousTime).isAfterOrEqualTo(currentTime);
            if (previousTime.equals(currentTime)) {
                assertThat(previous.path("careerId").asText())
                        .isLessThan(current.path("careerId").asText());
            }
        }

        String frozen = created.path("leagueFrozenSnapshotIdentity").asText();
        jdbc.update("""
                UPDATE career_save SET league_frozen_snapshot_hash = ?
                WHERE career_id = ?
                """, "f".repeat(64), careerId);
        JsonNode integrity = json(mvc.perform(get("/api/v1/careers/" + careerId))
                .andExpect(status().isInternalServerError()).andReturn().getResponse()
                .getContentAsString());
        assertThat(integrity.path("code").asText())
                .isEqualTo("CAREER_LINKED_SEASON_INTEGRITY_FAILURE");
        assertThat(integrity.toString()).doesNotContain("Jdbc", "SQL", "/mnt/", "C:\\");
        jdbc.update("""
                UPDATE career_save SET league_frozen_snapshot_hash = ?
                WHERE career_id = ?
                """, frozen, careerId);
    }

    private void assertInvalid(String body, int status, String code) throws Exception {
        JsonNode response = json(mvc.perform(post("/api/v1/careers")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(result -> assertThat(result.getResponse().getStatus())
                        .isEqualTo(status)).andReturn().getResponse().getContentAsString());
        assertThat(response.path("schemaVersion").asText())
                .isEqualTo(CareerApiV1Dtos.ERROR_SCHEMA);
        assertThat(response.path("code").asText()).isEqualTo(code);
    }

    private int count(String table) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
    }

    private JsonNode json(String value) throws Exception {
        return mapper.readTree(value);
    }

    private static JsonNode findCareer(JsonNode careers, String careerId) {
        for (JsonNode career : careers) {
            if (careerId.equals(career.path("careerId").asText())) return career;
        }
        throw new AssertionError("Career summary missing");
    }

    private static String createBody(
            String saveName,
            String managerName,
            String teamCode,
            String commandId
    ) throws Exception {
        return new ObjectMapper().writeValueAsString(java.util.Map.of(
                "schemaVersion", CareerApiV1Dtos.CREATE_REQUEST_SCHEMA,
                "saveName", saveName,
                "managerName", managerName,
                "managedTeamCode", teamCode,
                "clientCommandId", commandId));
    }

    private static String advanceBody(
            long revision,
            String mode,
            String commandId
    ) throws Exception {
        return new ObjectMapper().writeValueAsString(java.util.Map.of(
                "schemaVersion", CareerApiV1Dtos.ADVANCE_REQUEST_SCHEMA,
                "expectedCalendarRevision", revision,
                "mode", mode,
                "clientCommandId", commandId));
    }
}
