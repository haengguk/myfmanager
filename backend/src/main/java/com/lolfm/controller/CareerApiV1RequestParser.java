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
            "schemaVersion", "expectedCompetitionRevision", "clientCommandId", "sourceYear");
    private final ObjectMapper strictMapper;

    public CareerApiV1RequestParser(ObjectMapper mapper) {
        this.strictMapper = mapper.copy()
                .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
                .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    }

    public com.lolfm.career.CareerRosterStore.Request rosterCommand(byte[] body) {
        JsonNode json=read(body);
        Set<String> fields=Set.of("schemaVersion","sourceYear","team","playerId","action","targetOrganizationId","replacementPlayerId","expectedRevision","clientCommandId");
        var actual=new HashSet<String>();json.fieldNames().forEachRemaining(actual::add);
        if(!json.isObject() || !actual.equals(fields))throw invalid(null,"명단 변경 필드를 확인해 주세요.");
        if(!json.path("sourceYear").isIntegralNumber() || !json.path("sourceYear").canConvertToInt()
                || !json.path("expectedRevision").isIntegralNumber() || !json.path("expectedRevision").canConvertToLong())
            throw invalid(null,"연도와 revision은 정수여야 합니다.");
        return new com.lolfm.career.CareerRosterStore.Request(text(json,"schemaVersion"),json.path("sourceYear").intValue(),
                text(json,"team"),text(json,"playerId"),text(json,"action"),
                json.path("targetOrganizationId").isNull()?null:text(json,"targetOrganizationId"),
                json.path("replacementPlayerId").isNull()?null:text(json,"replacementPlayerId"),
                json.path("expectedRevision").longValue(),text(json,"clientCommandId"));
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
                revision.longValue(), commandId, json.has("sourceYear") ? positiveYear(json,"sourceYear") : null);
    }

    public com.lolfm.career.CareerSeasonApplicationService.Request seasonTransition(byte[] body) {
        var value=read(body); var fields=new HashSet<String>(); value.fieldNames().forEachRemaining(fields::add);
        if (!value.isObject() || !fields.equals(Set.of("schemaVersion","sourceYear","expectedCalendarRevision","clientCommandId")))
            throw invalid(null,"시즌 전환 필드를 확인해 주세요.");
        var revision=value.get("expectedCalendarRevision");
        if (!revision.isIntegralNumber() || !revision.canConvertToLong() || revision.longValue()<0) throw invalid("expectedCalendarRevision","정수가 필요합니다.");
        return new com.lolfm.career.CareerSeasonApplicationService.Request(text(value,"schemaVersion"),positiveYear(value,"sourceYear"),
                revision.longValue(),text(value,"clientCommandId"));
    }
    private static int positiveYear(JsonNode value,String name) {
        var year=value.get(name);
        if (year==null || !year.isIntegralNumber() || !year.canConvertToInt() || year.intValue()<2026) throw invalid(name,"시즌 연도를 확인해 주세요.");
        return year.intValue();
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
