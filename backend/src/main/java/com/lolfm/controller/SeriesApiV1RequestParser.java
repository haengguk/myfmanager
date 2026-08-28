package com.lolfm.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.lolfm.application.SeriesFormat;
import com.lolfm.dto.SeriesApiV1Dtos;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/** Exact-schema parsing without changing global Jackson or standalone APIs. */
@Component
public final class SeriesApiV1RequestParser {
    private static final String SEED_PATTERN = "0|-?[1-9][0-9]*";
    private static final String COMMAND_PATTERN = "[A-Za-z0-9._:-]{1,100}";
    private static final String SERIES_ID_PATTERN = "series_[0-9a-f]{64}";

    public SeriesApiV1Dtos.CreateRequest create(JsonNode body) {
        object(body);
        exact(body, Set.of("schemaVersion", "format", "teamACode", "teamBCode",
                "managedTeamCode", "game1BlueTeamCode", "rootSeed", "clientCommandId"));
        schema(body, SeriesApiV1Dtos.CREATE_REQUEST_SCHEMA);
        SeriesFormat format;
        try { format = SeriesFormat.valueOf(text(body, "format")); }
        catch (IllegalArgumentException error) {
            throw bad("SERIES_UNSUPPORTED_FORMAT", "format", "format은 BO3 또는 BO5여야 합니다.");
        }
        String rootSeed = text(body, "rootSeed");
        seed(rootSeed);
        return new SeriesApiV1Dtos.CreateRequest(
                SeriesApiV1Dtos.CREATE_REQUEST_SCHEMA, format, team(body, "teamACode"),
                team(body, "teamBCode"), team(body, "managedTeamCode"),
                team(body, "game1BlueTeamCode"), rootSeed,
                command(text(body, "clientCommandId")));
    }

    public SeriesApiV1Dtos.DraftCreateRequest draftCreate(JsonNode body) {
        object(body); exact(body, Set.of("schemaVersion", "expectedRevision", "clientCommandId"));
        schema(body, SeriesApiV1Dtos.DRAFT_CREATE_REQUEST_SCHEMA);
        return new SeriesApiV1Dtos.DraftCreateRequest(
                SeriesApiV1Dtos.DRAFT_CREATE_REQUEST_SCHEMA, revision(body, "expectedRevision"),
                command(text(body, "clientCommandId")));
    }

    public SeriesApiV1Dtos.DraftActionRequest draftAction(JsonNode body) {
        object(body); exact(body, Set.of("schemaVersion", "expectedSeriesRevision",
                "expectedDraftRevision", "clientCommandId", "championId"));
        schema(body, SeriesApiV1Dtos.DRAFT_ACTION_REQUEST_SCHEMA);
        String champion = text(body, "championId");
        if (!champion.equals(champion.trim()) || champion.length() > 100) {
            throw bad("SERIES_INVALID_CHAMPION_ID", "championId", "championId 형식이 올바르지 않습니다.");
        }
        return new SeriesApiV1Dtos.DraftActionRequest(
                SeriesApiV1Dtos.DRAFT_ACTION_REQUEST_SCHEMA,
                revision(body, "expectedSeriesRevision"),
                revision(body, "expectedDraftRevision"),
                command(text(body, "clientCommandId")), champion);
    }

    public SeriesApiV1Dtos.DraftCancelRequest draftCancel(JsonNode body) {
        object(body); exact(body, Set.of("schemaVersion", "expectedRevision", "clientCommandId"));
        schema(body, SeriesApiV1Dtos.DRAFT_CANCEL_REQUEST_SCHEMA);
        return new SeriesApiV1Dtos.DraftCancelRequest(
                SeriesApiV1Dtos.DRAFT_CANCEL_REQUEST_SCHEMA,
                revision(body, "expectedRevision"), command(text(body, "clientCommandId")));
    }

    public SeriesApiV1Dtos.SimulateRequest simulate(JsonNode body) {
        object(body); exact(body, Set.of("schemaVersion", "expectedSeriesRevision",
                "expectedDraftRevision", "clientCommandId"));
        schema(body, SeriesApiV1Dtos.SIMULATE_REQUEST_SCHEMA);
        return new SeriesApiV1Dtos.SimulateRequest(
                SeriesApiV1Dtos.SIMULATE_REQUEST_SCHEMA,
                revision(body, "expectedSeriesRevision"),
                revision(body, "expectedDraftRevision"),
                command(text(body, "clientCommandId")));
    }

    public SeriesApiV1Dtos.ReplayRequest replay(JsonNode body) {
        object(body); exact(body, Set.of("schemaVersion", "clientCommandId"));
        schema(body, SeriesApiV1Dtos.REPLAY_REQUEST_SCHEMA);
        return new SeriesApiV1Dtos.ReplayRequest(
                SeriesApiV1Dtos.REPLAY_REQUEST_SCHEMA,
                command(text(body, "clientCommandId")));
    }

    public SeriesApiV1Dtos.CancelRequest cancel(JsonNode body) {
        object(body); exact(body, Set.of("schemaVersion", "expectedRevision", "clientCommandId"));
        schema(body, SeriesApiV1Dtos.CANCEL_REQUEST_SCHEMA);
        return new SeriesApiV1Dtos.CancelRequest(
                SeriesApiV1Dtos.CANCEL_REQUEST_SCHEMA, revision(body, "expectedRevision"),
                command(text(body, "clientCommandId")));
    }

    public String seriesId(String value) {
        if (value == null || !value.matches(SERIES_ID_PATTERN)) {
            throw bad("SERIES_INVALID_ID", "seriesId", "seriesId 형식이 올바르지 않습니다.");
        }
        return value;
    }

    public int gameNumber(String value) {
        try {
            int game = Integer.parseInt(value);
            if (game < 1) throw new NumberFormatException();
            return game;
        } catch (NumberFormatException error) {
            throw bad("SERIES_INVALID_GAME_NUMBER", "gameNumber", "gameNumber는 양의 정수여야 합니다.");
        }
    }

    private static void object(JsonNode body) {
        if (body == null || body.isNull() || !body.isObject()) {
            throw bad("MALFORMED_REQUEST", null, "요청 본문은 유효한 JSON 객체여야 합니다.");
        }
    }

    private static void exact(JsonNode body, Set<String> allowed) {
        TreeSet<String> unsupported = new TreeSet<>();
        body.fieldNames().forEachRemaining(field -> { if (!allowed.contains(field)) unsupported.add(field); });
        if (!unsupported.isEmpty()) throw bad("SERIES_UNSUPPORTED_REQUEST_FIELD",
                unsupported.getFirst(), "Series API V1에서 지원하지 않는 요청 필드입니다.");
    }

    private static void schema(JsonNode body, String expected) {
        if (!expected.equals(text(body, "schemaVersion"))) {
            throw bad("SERIES_INVALID_REQUEST_SCHEMA", "schemaVersion", "지원하지 않는 Series 요청 schema입니다.");
        }
    }

    private static long revision(JsonNode body, String field) {
        JsonNode value = body.get(field);
        if (value == null || !value.isIntegralNumber() || !value.canConvertToLong()
                || value.longValue() < 0) {
            throw bad("SERIES_INVALID_EXPECTED_REVISION", field, field + "은 0 이상의 정수여야 합니다.");
        }
        return value.longValue();
    }

    private static String team(JsonNode body, String field) {
        String value = text(body, field);
        if (!value.equals(value.trim()) || value.isBlank()) {
            throw bad("SERIES_UNKNOWN_TEAM", field, "팀 코드 형식이 올바르지 않습니다.");
        }
        return value.toUpperCase(Locale.ROOT);
    }

    private static String command(String value) {
        if (!value.matches(COMMAND_PATTERN)) {
            throw bad("SERIES_INVALID_COMMAND_ID", "clientCommandId", "clientCommandId 형식이 올바르지 않습니다.");
        }
        return value;
    }

    private static void seed(String value) {
        if (!value.matches(SEED_PATTERN)) {
            throw bad("SERIES_INVALID_ROOT_SEED", "rootSeed", "rootSeed는 canonical signed 64-bit decimal string이어야 합니다.");
        }
        try { Long.parseLong(value); }
        catch (NumberFormatException error) {
            throw bad("SERIES_INVALID_ROOT_SEED", "rootSeed", "rootSeed가 signed 64-bit 범위를 벗어났습니다.");
        }
    }

    private static String text(JsonNode body, String field) {
        JsonNode value = body.get(field);
        if (value == null || !value.isTextual() || value.textValue().isEmpty()) {
            throw bad("SERIES_MISSING_REQUEST_FIELD", field, field + "가 필요합니다.");
        }
        return value.textValue();
    }

    private static SeriesApiV1Exception bad(String code, String field, String message) {
        return SeriesApiV1Exception.of(HttpStatus.BAD_REQUEST, code, field, message,
                false, null, null);
    }
}
