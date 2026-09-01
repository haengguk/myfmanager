package com.lolfm.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.lolfm.dto.LeagueApiV1Dtos;
import java.util.HashSet;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public final class LeagueApiV1RequestParser {
    public LeagueApiV1Dtos.CreateRequest create(JsonNode body) {
        object(body);
        exact(body, Set.of("schemaVersion", "leagueKey", "seasonKey", "seasonMode",
                "managedTeamCode", "seasonRootSeed", "clientCommandId"));
        schema(body, LeagueApiV1Dtos.CREATE_REQUEST_SCHEMA);
        return new LeagueApiV1Dtos.CreateRequest(
                LeagueApiV1Dtos.CREATE_REQUEST_SCHEMA,
                key(text(body, "leagueKey"), "leagueKey"),
                key(text(body, "seasonKey"), "seasonKey"),
                text(body, "seasonMode"), optionalText(body, "managedTeamCode"),
                signedLongText(body, "seasonRootSeed"),
                command(text(body, "clientCommandId")));
    }

    public LeagueApiV1Dtos.RunCurrentRoundRequest run(JsonNode body) {
        object(body);
        exact(body, Set.of("schemaVersion", "expectedLifecycleRevision",
                "clientCommandId"));
        schema(body, LeagueApiV1Dtos.RUN_COMMAND_SCHEMA);
        return new LeagueApiV1Dtos.RunCurrentRoundRequest(
                LeagueApiV1Dtos.RUN_COMMAND_SCHEMA,
                revision(body, "expectedLifecycleRevision"),
                command(text(body, "clientCommandId")));
    }

    public LeagueApiV1Dtos.LifecycleCommandRequest lifecycle(JsonNode body) {
        object(body);
        exact(body, Set.of("schemaVersion", "expectedLifecycleRevision",
                "clientCommandId"));
        schema(body, LeagueApiV1Dtos.LIFECYCLE_COMMAND_SCHEMA);
        return new LeagueApiV1Dtos.LifecycleCommandRequest(
                LeagueApiV1Dtos.LIFECYCLE_COMMAND_SCHEMA,
                revision(body, "expectedLifecycleRevision"),
                command(text(body, "clientCommandId")));
    }

    public LeagueApiV1Dtos.PlayerSeriesCommandRequest playerSeries(JsonNode body) {
        object(body);
        exact(body, Set.of("schemaVersion", "expectedLifecycleRevision",
                "clientCommandId"));
        schema(body, LeagueApiV1Dtos.PLAYER_SERIES_COMMAND_SCHEMA);
        return new LeagueApiV1Dtos.PlayerSeriesCommandRequest(
                LeagueApiV1Dtos.PLAYER_SERIES_COMMAND_SCHEMA,
                revision(body, "expectedLifecycleRevision"),
                command(text(body, "clientCommandId")));
    }

    public LeagueApiV1Dtos.PlayerCompletionCommandRequest completion(JsonNode body) {
        object(body);
        exact(body, Set.of("schemaVersion", "expectedLifecycleRevision",
                "clientCommandId", "bindingHash"));
        schema(body, LeagueApiV1Dtos.PLAYER_COMPLETION_COMMAND_SCHEMA);
        String bindingHash = text(body, "bindingHash");
        if (!bindingHash.matches("[0-9a-f]{64}")) {
            throw bad("LEAGUE_INVALID_BINDING_HASH", "bindingHash",
                    "bindingHash 형식이 올바르지 않습니다.");
        }
        return new LeagueApiV1Dtos.PlayerCompletionCommandRequest(
                LeagueApiV1Dtos.PLAYER_COMPLETION_COMMAND_SCHEMA,
                revision(body, "expectedLifecycleRevision"),
                command(text(body, "clientCommandId")), bindingHash);
    }

    private static void object(JsonNode body) {
        if (body == null || !body.isObject()) {
            throw bad("LEAGUE_MALFORMED_REQUEST", null,
                    "요청 본문은 JSON 객체여야 합니다.");
        }
    }

    private static void exact(JsonNode body, Set<String> allowed) {
        HashSet<String> actual = new HashSet<>();
        body.fieldNames().forEachRemaining(actual::add);
        actual.removeAll(allowed);
        if (!actual.isEmpty()) {
            throw bad("LEAGUE_UNKNOWN_REQUEST_FIELD", actual.stream().sorted()
                    .findFirst().orElse(null), "지원하지 않는 요청 필드입니다.");
        }
    }

    private static void schema(JsonNode body, String expected) {
        if (!expected.equals(text(body, "schemaVersion"))) {
            throw bad("LEAGUE_INVALID_REQUEST_SCHEMA", "schemaVersion",
                    "지원하지 않는 League 요청 schema입니다.");
        }
    }

    private static String text(JsonNode body, String field) {
        JsonNode value = body.get(field);
        if (value == null || !value.isTextual() || value.textValue().isBlank()) {
            throw bad("LEAGUE_INVALID_REQUEST_FIELD", field,
                    field + " 값이 필요합니다.");
        }
        return value.textValue();
    }

    private static String optionalText(JsonNode body, String field) {
        JsonNode value = body.get(field);
        if (value == null || value.isNull()) return null;
        if (!value.isTextual() || value.textValue().isBlank()) {
            throw bad("LEAGUE_INVALID_REQUEST_FIELD", field,
                    field + " 형식이 올바르지 않습니다.");
        }
        return value.textValue();
    }

    private static long revision(JsonNode body, String field) {
        JsonNode value = body.get(field);
        if (value == null || !value.isIntegralNumber() || !value.canConvertToLong()
                || value.longValue() < 0) {
            throw bad("LEAGUE_INVALID_REVISION", field,
                    "revision은 0 이상의 정수여야 합니다.");
        }
        return value.longValue();
    }

    private static String signedLongText(JsonNode body, String field) {
        String value = text(body, field);
        try {
            long parsed = Long.parseLong(value);
            if (!Long.toString(parsed).equals(value)) throw new NumberFormatException();
            return value;
        } catch (NumberFormatException invalid) {
            throw bad("LEAGUE_INVALID_ROOT_SEED", field,
                    "seasonRootSeed는 signed-long 문자열이어야 합니다.");
        }
    }

    private static String key(String value, String field) {
        if (!value.matches("[0-9A-Za-z][0-9A-Za-z._-]{0,79}")) {
            throw bad("LEAGUE_INVALID_STABLE_KEY", field,
                    field + " 형식이 올바르지 않습니다.");
        }
        return value;
    }

    private static String command(String value) {
        if (!value.matches("[0-9A-Za-z][0-9A-Za-z._:-]{0,159}")) {
            throw bad("LEAGUE_INVALID_COMMAND_ID", "clientCommandId",
                    "clientCommandId 형식이 올바르지 않습니다.");
        }
        return value;
    }

    private static LeagueApiV1Exception bad(String code, String field, String message) {
        return LeagueApiV1Exception.of(HttpStatus.BAD_REQUEST, code, field, message);
    }
}
