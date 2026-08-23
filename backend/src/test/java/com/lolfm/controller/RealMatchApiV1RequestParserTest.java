package com.lolfm.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lolfm.dto.RealMatchApiV1Dtos;
import java.util.List;
import org.junit.jupiter.api.Test;

class RealMatchApiV1RequestParserTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final RealMatchApiV1RequestParser parser = new RealMatchApiV1RequestParser();

    @Test
    void acceptsOnlyExactSchemaAndCanonicalStringSeedWhileNormalizingTeamCodes() throws Exception {
        RealMatchApiV1Dtos.SimulateRequest request = parser.parse(json("""
                {"schemaVersion":"REAL_MATCH_SIMULATE_REQUEST_V1",
                 "blueTeamCode":" gen ","redTeamCode":"t1","seed":"-73"}
                """));

        assertThat(request.schemaVersion()).isEqualTo(RealMatchApiV1Dtos.REQUEST_SCHEMA);
        assertThat(request.blueTeamCode()).isEqualTo("GEN");
        assertThat(request.redTeamCode()).isEqualTo("T1");
        assertThat(request.seed()).isEqualTo("-73");
        assertThat(request.seedAsLong()).isEqualTo(-73L);
    }

    @Test
    void rejectsMissingMalformedWrongSchemaAndMissingTeamsWithStableCodes() throws Exception {
        assertFailure(null, "MALFORMED_REQUEST", null);
        assertFailure(json("[]"), "MALFORMED_REQUEST", null);
        assertFailure(json("{}"), "INVALID_REQUEST_SCHEMA", "schemaVersion");
        assertFailure(json("""
                {"schemaVersion":"WRONG","blueTeamCode":"GEN",
                 "redTeamCode":"T1","seed":"73"}
                """), "INVALID_REQUEST_SCHEMA", "schemaVersion");
        assertFailure(json("""
                {"schemaVersion":"REAL_MATCH_SIMULATE_REQUEST_V1",
                 "redTeamCode":"T1","seed":"73"}
                """), "BLUE_TEAM_REQUIRED", "blueTeamCode");
        assertFailure(json("""
                {"schemaVersion":"REAL_MATCH_SIMULATE_REQUEST_V1",
                 "blueTeamCode":"GEN","seed":"73"}
                """), "RED_TEAM_REQUIRED", "redTeamCode");
        assertFailure(json("""
                {"schemaVersion":"REAL_MATCH_SIMULATE_REQUEST_V1",
                 "blueTeamCode":"GEN","redTeamCode":"gen","seed":"73"}
                """), "SAME_TEAM_NOT_ALLOWED", "redTeamCode");
    }

    @Test
    void rejectsNumberOverflowAndNonCanonicalSeedStrings() throws Exception {
        List<String> invalidStringSeeds = List.of(
                "+1", "01", "-0", " 1", "1 ", "1.0",
                "9223372036854775808", "-9223372036854775809");
        for (String seed : invalidStringSeeds) {
            assertFailure(json("""
                    {"schemaVersion":"REAL_MATCH_SIMULATE_REQUEST_V1",
                     "blueTeamCode":"GEN","redTeamCode":"T1","seed":"%s"}
                    """.formatted(seed)), "INVALID_SEED", "seed");
        }
        assertFailure(json("""
                {"schemaVersion":"REAL_MATCH_SIMULATE_REQUEST_V1",
                 "blueTeamCode":"GEN","redTeamCode":"T1","seed":73}
                """), "INVALID_SEED", "seed");
        assertFailure(json("""
                {"schemaVersion":"REAL_MATCH_SIMULATE_REQUEST_V1",
                 "blueTeamCode":"GEN","redTeamCode":"T1"}
                """), "INVALID_SEED", "seed");
    }

    @Test
    void rejectsEveryUnknownOrCandidateInjectionFieldRatherThanIgnoringIt() throws Exception {
        for (String field : List.of(
                "runtimeProfile", "profileId", "matchupEnabled", "compositionEnabled",
                "jungleEconomyEnabled", "jungleTempoEnabled", "diagnosticsEnabled",
                "ratings", "proficiencies", "draft", "seriesHistory")) {
            assertFailure(json("""
                    {"schemaVersion":"REAL_MATCH_SIMULATE_REQUEST_V1",
                     "blueTeamCode":"GEN","redTeamCode":"T1","seed":"73","%s":false}
                    """.formatted(field)), "UNSUPPORTED_REQUEST_FIELD", field);
        }
    }

    private JsonNode json(String value) throws Exception {
        return mapper.readTree(value);
    }

    private void assertFailure(JsonNode body, String code, String field) {
        assertThatExceptionOfType(RealMatchApiV1Exception.class)
                .isThrownBy(() -> parser.parse(body))
                .satisfies(error -> {
                    assertThat(error.code()).isEqualTo(code);
                    assertThat(error.field()).isEqualTo(field);
                    assertThat(error.clientMessage()).isNotBlank();
                });
    }
}
