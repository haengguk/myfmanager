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

    public com.lolfm.career.CareerDevelopmentStore.Request trainingCommand(byte[] body) {
        var json=read(body);var fields=new HashSet<String>();json.fieldNames().forEachRemaining(fields::add);
        if(!json.isObject()||!fields.equals(Set.of("schemaVersion","sourceYear","expectedRevision","playerId","plan","clearOverride","clientCommandId")))throw invalid(null,"훈련 명령 필드를 확인해 주세요.");
        if(!json.path("sourceYear").isIntegralNumber()||!json.path("sourceYear").canConvertToInt()||!json.path("expectedRevision").isIntegralNumber()||!json.path("expectedRevision").canConvertToLong()||!json.path("clearOverride").isBoolean())throw invalid(null,"훈련 연도·revision·해제 여부의 형식을 확인해 주세요.");
        optionalText(json,"playerId");text(json,"schemaVersion");text(json,"clientCommandId");
        var plan=json.path("plan");
        if(!plan.isNull()) {
            var names=new HashSet<String>();plan.fieldNames().forEachRemaining(names::add);
            if(!plan.isObject()||!names.equals(Set.of("intensity","focus","skill","champions"))||!plan.path("champions").isArray())throw invalid("plan","훈련 강도·초점·대상을 확인해 주세요.");
            text(plan,"intensity");text(plan,"focus");optionalText(plan,"skill");for(var c:plan.path("champions"))if(!c.isTextual()||c.asText().isBlank())throw invalid("champions","챔피언 ID를 확인해 주세요.");
        }
        try{return strictMapper.readerFor(com.lolfm.career.CareerDevelopmentStore.Request.class).with(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).readValue(json);}
        catch(IOException|IllegalArgumentException e){throw invalid("plan","훈련 계획 형식을 확인해 주세요.");}
    }

    public com.lolfm.career.CareerMarketStore.TradeRequest tradeCommand(byte[] body) {
        JsonNode json=read(body);var fields=new HashSet<String>();json.fieldNames().forEachRemaining(fields::add);
        if(!json.isObject()||!fields.equals(Set.of("schemaVersion","sourceYear","expectedRevision","action","tradeId","terms","replacementPlayerId","clientCommandId")))throw invalid(null,"이적/임대 명령 필드를 확인해 주세요.");
        if(!json.path("sourceYear").isIntegralNumber()||!json.path("sourceYear").canConvertToInt()||!json.path("expectedRevision").isIntegralNumber()||!json.path("expectedRevision").canConvertToLong())throw invalid(null,"연도와 revision은 정수여야 합니다.");
        var terms=json.path("terms");
        if(!terms.isNull()) {
            for(String field:java.util.List.of("fee","borrowerSalaryPercent"))if(!terms.path(field).isIntegralNumber()||!terms.path(field).canConvertToLong())throw invalid(field,"금액/분담은 정수여야 합니다.");
            if(!terms.path("borrowerSalaryPercent").canConvertToInt())throw invalid("borrowerSalaryPercent","급여 분담 범위를 확인해 주세요.");
            for(String field:java.util.List.of("annualSalary","signingBonus"))if(!terms.path("playerTerms").path(field).isIntegralNumber()||!terms.path("playerTerms").path(field).canConvertToLong())throw invalid(field,"개인 조건 금액은 정수여야 합니다.");
        }
        try{return strictMapper.readerFor(com.lolfm.career.CareerMarketStore.TradeRequest.class).with(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).readValue(json);}
        catch(IOException | IllegalArgumentException e){throw invalid(null,"거래 날짜·역할·조건 형식을 확인해 주세요.");}
    }
    public com.lolfm.career.CareerMarketStore.Request marketCommand(byte[] body) {
        JsonNode json=read(body);
        Set<String> fields=Set.of("schemaVersion","sourceYear","expectedRevision","action","playerId","offerId","terms","replacementPlayerId","competitionId","clientCommandId");
        var actual=new HashSet<String>();json.fieldNames().forEachRemaining(actual::add);
        if(!json.isObject()||!actual.equals(fields))throw invalid(null,"시장 명령 필드를 확인해 주세요.");
        if(!json.path("sourceYear").isIntegralNumber()||!json.path("sourceYear").canConvertToInt()||!json.path("expectedRevision").isIntegralNumber()||!json.path("expectedRevision").canConvertToLong())throw invalid(null,"연도와 revision은 정수여야 합니다.");
        com.lolfm.career.CareerMarketState.Terms terms=null;var t=json.path("terms");
        if(!t.isNull()) {
            var names=new HashSet<String>();t.fieldNames().forEachRemaining(names::add);
            if(!t.isObject()||!names.equals(Set.of("startDate","endDate","annualSalary","signingBonus","role"))||!t.path("annualSalary").isIntegralNumber()||!t.path("annualSalary").canConvertToLong()||!t.path("signingBonus").isIntegralNumber()||!t.path("signingBonus").canConvertToLong())throw invalid("terms","계약 날짜·금액·역할을 확인해 주세요.");
            try { terms=new com.lolfm.career.CareerMarketState.Terms(java.time.LocalDate.parse(text(t,"startDate")),java.time.LocalDate.parse(text(t,"endDate")),t.path("annualSalary").longValue(),t.path("signingBonus").longValue(),com.lolfm.career.CareerMarketState.Role.valueOf(text(t,"role"))); }
            catch(IllegalArgumentException | java.time.DateTimeException invalid){throw invalid("terms","계약 날짜 또는 역할이 올바르지 않습니다.");}
        }
        return new com.lolfm.career.CareerMarketStore.Request(text(json,"schemaVersion"),json.path("sourceYear").intValue(),json.path("expectedRevision").longValue(),text(json,"action"),optionalText(json,"playerId"),optionalText(json,"offerId"),terms,optionalText(json,"replacementPlayerId"),optionalText(json,"competitionId"),text(json,"clientCommandId"));
    }
    private String optionalText(JsonNode json,String field) {return json.path(field).isNull()?null:text(json,field);}

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
