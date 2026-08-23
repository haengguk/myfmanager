package com.lolfm.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.lolfm.dto.RealMatchApiV1Dtos;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import org.springframework.stereotype.Component;

/** Strict request parser scoped to Real Match V1 without changing global Jackson behavior. */
@Component
public final class RealMatchApiV1RequestParser {
    private static final Set<String> ALLOWED_FIELDS = Set.of(
            "schemaVersion", "blueTeamCode", "redTeamCode", "seed");
    private static final String CANONICAL_SEED_PATTERN = "0|-?[1-9][0-9]*";

    public RealMatchApiV1Dtos.SimulateRequest parse(JsonNode body) {
        if (body == null || body.isNull() || !body.isObject()) {
            throw error("MALFORMED_REQUEST", null,
                    "요청 본문은 유효한 JSON 객체여야 합니다.");
        }
        TreeSet<String> unsupported = new TreeSet<>();
        body.fieldNames().forEachRemaining(field -> {
            if (!ALLOWED_FIELDS.contains(field)) unsupported.add(field);
        });
        if (!unsupported.isEmpty()) {
            String field = unsupported.getFirst();
            throw error("UNSUPPORTED_REQUEST_FIELD", field,
                    "Real Match API V1에서 지원하지 않는 요청 필드입니다.");
        }

        String schema = exactText(body, "schemaVersion", "INVALID_REQUEST_SCHEMA",
                "schemaVersion이 필요합니다.");
        if (!RealMatchApiV1Dtos.REQUEST_SCHEMA.equals(schema)) {
            throw error("INVALID_REQUEST_SCHEMA", "schemaVersion",
                    "지원하지 않는 Real Match 요청 schema입니다.");
        }
        String blue = normalizedTeamCode(body, "blueTeamCode", "BLUE_TEAM_REQUIRED");
        String red = normalizedTeamCode(body, "redTeamCode", "RED_TEAM_REQUIRED");
        if (blue.equals(red)) {
            throw error("SAME_TEAM_NOT_ALLOWED", "redTeamCode",
                    "BLUE 팀과 RED 팀은 서로 달라야 합니다.");
        }
        String seed = exactText(body, "seed", "INVALID_SEED",
                "seed는 signed 64-bit decimal string이어야 합니다.");
        if (!seed.matches(CANONICAL_SEED_PATTERN)) {
            throw error("INVALID_SEED", "seed",
                    "seed는 canonical signed 64-bit decimal string이어야 합니다.");
        }
        try {
            Long.parseLong(seed);
        } catch (NumberFormatException error) {
            throw error("INVALID_SEED", "seed",
                    "seed가 signed 64-bit 범위를 벗어났습니다.");
        }
        return new RealMatchApiV1Dtos.SimulateRequest(schema, blue, red, seed);
    }

    private static String normalizedTeamCode(JsonNode body, String field, String code) {
        String value = exactText(body, field, code, field + "가 필요합니다.").trim();
        if (value.isEmpty()) throw error(code, field, field + "가 필요합니다.");
        return value.toUpperCase(Locale.ROOT);
    }

    private static String exactText(
            JsonNode body, String field, String code, String message
    ) {
        JsonNode value = body.get(field);
        if (value == null || !value.isTextual() || value.textValue().isEmpty()) {
            throw error(code, field, message);
        }
        return value.textValue();
    }

    private static RealMatchApiV1Exception error(String code, String field, String message) {
        return RealMatchApiV1Exception.badRequest(code, field, message);
    }
}
