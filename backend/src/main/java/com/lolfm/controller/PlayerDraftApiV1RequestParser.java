package com.lolfm.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.lolfm.dto.PlayerDraftApiV1Dtos;
import com.lolfm.simulator.TeamSide;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Strict parsing isolated from global Jackson behavior and legacy endpoints. */
@Component
public final class PlayerDraftApiV1RequestParser {
    private static final String SEED_PATTERN = "0|-?[1-9][0-9]*";
    private static final String ACTION_ID_PATTERN = "[A-Za-z0-9._:-]{1,100}";

    public PlayerDraftApiV1Dtos.StartRequest start(JsonNode body) {
        requireObject(body);
        rejectUnsupported(body, Set.of(
                "schemaVersion", "blueTeamCode", "redTeamCode", "controlledSide", "seed"));
        requireSchema(body, PlayerDraftApiV1Dtos.START_REQUEST_SCHEMA);
        String blue = teamCode(body, "blueTeamCode");
        String red = teamCode(body, "redTeamCode");
        TeamSide controlled = teamSide(text(body, "controlledSide"));
        String seed = text(body, "seed");
        validateSeed(seed);
        return new PlayerDraftApiV1Dtos.StartRequest(
                PlayerDraftApiV1Dtos.START_REQUEST_SCHEMA, blue, red, controlled, seed);
    }

    public PlayerDraftApiV1Dtos.ActionRequest action(JsonNode body) {
        requireObject(body);
        rejectUnsupported(body, Set.of(
                "schemaVersion", "expectedRevision", "clientActionId", "championId"));
        requireSchema(body, PlayerDraftApiV1Dtos.ACTION_REQUEST_SCHEMA);
        JsonNode revisionNode = body.get("expectedRevision");
        if (revisionNode == null || !revisionNode.isIntegralNumber()
                || !revisionNode.canConvertToLong() || revisionNode.longValue() < 0) {
            throw bad("INVALID_EXPECTED_REVISION", "expectedRevision",
                    "expectedRevision은 0 이상의 정수여야 합니다.");
        }
        String actionId = text(body, "clientActionId");
        if (!actionId.matches(ACTION_ID_PATTERN)) {
            throw bad("INVALID_CLIENT_ACTION_ID", "clientActionId",
                    "clientActionId 형식이 올바르지 않습니다.");
        }
        String championId = text(body, "championId");
        if (!championId.equals(championId.trim()) || championId.length() > 100) {
            throw bad("INVALID_CHAMPION_ID", "championId",
                    "championId 형식이 올바르지 않습니다.");
        }
        return new PlayerDraftApiV1Dtos.ActionRequest(
                PlayerDraftApiV1Dtos.ACTION_REQUEST_SCHEMA,
                revisionNode.longValue(), actionId, championId);
    }

    public PlayerDraftApiV1Dtos.SimulateRequest simulate(JsonNode body) {
        requireObject(body);
        rejectUnsupported(body, Set.of("schemaVersion"));
        requireSchema(body, PlayerDraftApiV1Dtos.SIMULATE_REQUEST_SCHEMA);
        return new PlayerDraftApiV1Dtos.SimulateRequest(
                PlayerDraftApiV1Dtos.SIMULATE_REQUEST_SCHEMA);
    }

    public String sessionId(String value) {
        try {
            return UUID.fromString(value).toString();
        } catch (RuntimeException error) {
            throw bad("INVALID_SESSION_ID", "sessionId",
                    "sessionId는 canonical UUID여야 합니다.");
        }
    }

    private static void requireObject(JsonNode body) {
        if (body == null || body.isNull() || !body.isObject()) {
            throw bad("MALFORMED_REQUEST", null,
                    "요청 본문은 유효한 JSON 객체여야 합니다.");
        }
    }

    private static void rejectUnsupported(JsonNode body, Set<String> allowed) {
        TreeSet<String> unsupported = new TreeSet<>();
        body.fieldNames().forEachRemaining(field -> {
            if (!allowed.contains(field)) unsupported.add(field);
        });
        if (!unsupported.isEmpty()) {
            throw bad("UNSUPPORTED_REQUEST_FIELD", unsupported.getFirst(),
                    "Player Draft API V1에서 지원하지 않는 요청 필드입니다.");
        }
    }

    private static void requireSchema(JsonNode body, String expected) {
        String schema = text(body, "schemaVersion");
        if (!expected.equals(schema)) {
            throw bad("INVALID_REQUEST_SCHEMA", "schemaVersion",
                    "지원하지 않는 Player Draft 요청 schema입니다.");
        }
    }

    private static String teamCode(JsonNode body, String field) {
        String value = text(body, field).trim();
        if (value.isEmpty()) {
            throw bad("TEAM_REQUIRED", field, field + "가 필요합니다.");
        }
        return value.toUpperCase(Locale.ROOT);
    }

    private static TeamSide teamSide(String value) {
        try {
            TeamSide side = TeamSide.valueOf(value);
            if (side != TeamSide.BLUE && side != TeamSide.RED) throw new IllegalArgumentException();
            return side;
        } catch (IllegalArgumentException error) {
            throw bad("INVALID_CONTROLLED_SIDE", "controlledSide",
                    "controlledSide는 BLUE 또는 RED여야 합니다.");
        }
    }

    private static void validateSeed(String seed) {
        if (!seed.matches(SEED_PATTERN)) {
            throw bad("INVALID_SEED", "seed",
                    "seed는 canonical signed 64-bit decimal string이어야 합니다.");
        }
        try {
            Long.parseLong(seed);
        } catch (NumberFormatException error) {
            throw bad("INVALID_SEED", "seed", "seed가 signed 64-bit 범위를 벗어났습니다.");
        }
    }

    private static String text(JsonNode body, String field) {
        JsonNode value = body.get(field);
        if (value == null || !value.isTextual() || value.textValue().isEmpty()) {
            throw bad("MISSING_REQUEST_FIELD", field, field + "가 필요합니다.");
        }
        return value.textValue();
    }

    private static PlayerDraftApiV1Exception bad(
            String code, String field, String message
    ) {
        return PlayerDraftApiV1Exception.badRequest(code, field, message);
    }
}
