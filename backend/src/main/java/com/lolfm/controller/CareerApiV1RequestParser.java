package com.lolfm.controller;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lolfm.career.CareerException;
import com.lolfm.career.CareerIdentity;
import com.lolfm.dto.CareerApiV1Dtos;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import org.springframework.stereotype.Component;

/** Career-only strict JSON parser, including duplicate-field rejection. */
@Component
public final class CareerApiV1RequestParser {
    private static final Set<String> CREATE_FIELDS = Set.of(
            "schemaVersion", "saveName", "managerName", "managedTeamCode",
            "clientCommandId");
    private static final Set<String> ADVANCE_FIELDS = Set.of(
            "schemaVersion", "expectedCalendarRevision", "mode", "clientCommandId");
    private static final Set<String> COMPETITION_COMMAND_FIELDS = Set.of(
            "schemaVersion", "expectedCompetitionRevision", "clientCommandId");
    private final ObjectMapper strictMapper;

    public CareerApiV1RequestParser(ObjectMapper mapper) {
        this.strictMapper = mapper.copy()
                .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
                .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    }

    public CareerApiV1Dtos.CreateRequest create(byte[] body) {
        JsonNode json = read(body);
        if (!json.isObject()) {
            throw invalid(null, "요청 본문은 JSON 객체여야 합니다.");
        }
        HashSet<String> unknown = new HashSet<>();
        json.fieldNames().forEachRemaining(unknown::add);
        unknown.removeAll(CREATE_FIELDS);
        if (!unknown.isEmpty()) {
            throw invalid(unknown.stream().sorted().findFirst().orElse(null),
                    "지원하지 않는 Career 생성 필드입니다.");
        }
        String schema = text(json, "schemaVersion");
        if (!CareerApiV1Dtos.CREATE_REQUEST_SCHEMA.equals(schema)) {
            throw invalid("schemaVersion", "지원하지 않는 Career 요청 schema입니다.");
        }
        String commandId = text(json, "clientCommandId");
        try {
            commandId = CareerIdentity.canonicalCommandId(commandId);
        } catch (IllegalArgumentException invalid) {
            throw invalid("clientCommandId", "clientCommandId는 UUID 형식이어야 합니다.");
        }
        return new CareerApiV1Dtos.CreateRequest(schema, text(json, "saveName"),
                text(json, "managerName"), text(json, "managedTeamCode"), commandId);
    }

    public CareerApiV1Dtos.AdvanceRequest advance(byte[] body) {
        JsonNode json = read(body);
        if (!json.isObject()) {
            throw invalid(null, "요청 본문은 JSON 객체여야 합니다.");
        }
        HashSet<String> unknown = new HashSet<>();
        json.fieldNames().forEachRemaining(unknown::add);
        unknown.removeAll(ADVANCE_FIELDS);
        if (!unknown.isEmpty()) {
            throw invalid(unknown.stream().sorted().findFirst().orElse(null),
                    "지원하지 않는 Career 캘린더 진행 필드입니다.");
        }
        String schema = text(json, "schemaVersion");
        if (!CareerApiV1Dtos.ADVANCE_REQUEST_SCHEMA.equals(schema)) {
            throw invalid("schemaVersion", "지원하지 않는 Career 캘린더 요청 schema입니다.");
        }
        JsonNode revision = json.get("expectedCalendarRevision");
        if (revision == null || !revision.isIntegralNumber()
                || !revision.canConvertToLong() || revision.longValue() < 0) {
            throw invalid("expectedCalendarRevision",
                    "expectedCalendarRevision은 0 이상의 정수여야 합니다.");
        }
        String commandId = text(json, "clientCommandId");
        try {
            commandId = CareerIdentity.canonicalCommandId(commandId);
        } catch (IllegalArgumentException invalid) {
            throw invalid("clientCommandId", "clientCommandId는 UUID 형식이어야 합니다.");
        }
        return new CareerApiV1Dtos.AdvanceRequest(schema, revision.longValue(),
                text(json, "mode"), commandId);
    }

    public CareerApiV1Dtos.CompetitionCommandRequest competitionCommand(byte[] body) {
        JsonNode json = read(body);
        if (!json.isObject()) {
            throw invalid(null, "요청 본문은 JSON 객체여야 합니다.");
        }
        HashSet<String> unknown = new HashSet<>();
        json.fieldNames().forEachRemaining(unknown::add);
        unknown.removeAll(COMPETITION_COMMAND_FIELDS);
        if (!unknown.isEmpty()) {
            throw invalid(unknown.stream().sorted().findFirst().orElse(null),
                    "지원하지 않는 Career 대회 명령 필드입니다.");
        }
        String schema = text(json, "schemaVersion");
        if (!CareerApiV1Dtos.COMPETITION_COMMAND_REQUEST_SCHEMA.equals(schema)) {
            throw invalid("schemaVersion", "지원하지 않는 Career 대회 요청 schema입니다.");
        }
        JsonNode revision = json.get("expectedCompetitionRevision");
        if (revision == null || !revision.isIntegralNumber()
                || !revision.canConvertToLong() || revision.longValue() < 0) {
            throw invalid("expectedCompetitionRevision",
                    "expectedCompetitionRevision은 0 이상의 정수여야 합니다.");
        }
        String commandId = text(json, "clientCommandId");
        try {
            commandId = CareerIdentity.canonicalCommandId(commandId);
        } catch (IllegalArgumentException invalid) {
            throw invalid("clientCommandId", "clientCommandId는 UUID 형식이어야 합니다.");
        }
        return new CareerApiV1Dtos.CompetitionCommandRequest(schema,
                revision.longValue(), commandId);
    }

    private JsonNode read(byte[] body) {
        if (body == null || body.length == 0) {
            throw invalid(null, "요청 본문은 유효한 JSON 객체여야 합니다.");
        }
        try {
            JsonNode value = strictMapper.readTree(body);
            if (value == null) throw invalid(null,
                    "요청 본문은 유효한 JSON 객체여야 합니다.");
            return value;
        } catch (IOException malformed) {
            throw invalid(null, "요청 본문은 유효한 JSON 객체여야 합니다.");
        }
    }

    private static String text(JsonNode body, String field) {
        JsonNode value = body.get(field);
        if (value == null || !value.isTextual()) {
            throw invalid(field, field + " 문자열 값이 필요합니다.");
        }
        return value.textValue();
    }

    private static CareerException invalid(String field, String message) {
        return CareerException.invalid(field, message);
    }
}
