package com.lolfm.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import org.springframework.stereotype.Component;

/** Canonical JSON and recursively immutable JSON-value projection for V1 contracts. */
@Component
public final class MatchEngineV1Canonicalizer {
    public static final String HASH_ALGORITHM =
            "SHA256_CANONICAL_JSON_SORTED_PROPERTIES_AND_MAP_KEYS_UTF8_V1";
    private final ObjectMapper mapper;

    public MatchEngineV1Canonicalizer(ObjectMapper mapper) {
        this.mapper = Objects.requireNonNull(mapper, "mapper").copy()
                .enable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
                .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
                .disable(SerializationFeature.INDENT_OUTPUT);
    }

    public String canonicalJson(Object value) {
        try {
            return mapper.writeValueAsString(Objects.requireNonNull(value, "value"));
        } catch (JsonProcessingException error) {
            throw new IllegalStateException("Failed to canonicalize Match Engine V1 value", error);
        }
    }

    public String hash(Object value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(
                    canonicalJson(value).getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException(error);
        }
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> immutableObject(Object value) {
        if (value == null) return Map.of();
        Object converted = mapper.convertValue(value, Object.class);
        Object frozen = freeze(converted);
        if (!(frozen instanceof Map<?, ?> map)) {
            throw new IllegalArgumentException("Structured projection value must be an object");
        }
        return (Map<String, Object>) map;
    }

    private static Object freeze(Object value) {
        if (value == null || value instanceof String || value instanceof Number
                || value instanceof Boolean) {
            return value;
        }
        if (value instanceof Map<?, ?> map) {
            TreeMap<String, Object> copy = new TreeMap<>();
            map.forEach((key, nested) -> copy.put(String.valueOf(key), freeze(nested)));
            return Collections.unmodifiableMap(copy);
        }
        if (value instanceof Iterable<?> iterable) {
            ArrayList<Object> copy = new ArrayList<>();
            iterable.forEach(nested -> copy.add(freeze(nested)));
            return Collections.unmodifiableList(copy);
        }
        if (value.getClass().isArray()) {
            int length = java.lang.reflect.Array.getLength(value);
            ArrayList<Object> copy = new ArrayList<>(length);
            for (int index = 0; index < length; index++) {
                copy.add(freeze(java.lang.reflect.Array.get(value, index)));
            }
            return Collections.unmodifiableList(copy);
        }
        return freeze(String.valueOf(value));
    }
}
