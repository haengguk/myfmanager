package com.lolfm.league;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.springframework.stereotype.Component;

/** Canonical JSON boundary for durable League records. */
@Component
final class LeagueJsonCodec {
    private final ObjectMapper mapper;

    LeagueJsonCodec(ObjectMapper applicationMapper) {
        this.mapper = applicationMapper.copy()
                .enable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
                .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);
    }

    String write(Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("LEAGUE_JSON_WRITE_FAILED", exception);
        }
    }

    <T> T read(String value, Class<T> type) {
        try {
            return mapper.readValue(value, type);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("LEAGUE_JSON_READ_FAILED", exception);
        }
    }
}
