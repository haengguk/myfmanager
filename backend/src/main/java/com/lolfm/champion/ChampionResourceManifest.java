package com.lolfm.champion;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;

/** Stable bootstrap descriptor selecting one coherent champion resource set. */
public record ChampionResourceManifest(
        String manifestVersion,
        String catalog,
        String power,
        String matchup,
        String composition,
        String jungleClear
) {
    public static final String BOOTSTRAP_RESOURCE = "/champions/champion-resource-manifest.json";

    public ChampionResourceManifest {
        manifestVersion = required(manifestVersion, "manifestVersion");
        catalog = resourcePath(catalog, "catalog");
        power = resourcePath(power, "power");
        matchup = resourcePath(matchup, "matchup");
        composition = resourcePath(composition, "composition");
        jungleClear = resourcePath(jungleClear, "jungleClear");
    }

    public static ChampionResourceManifest loadDefault(ObjectMapper mapper) {
        return load(mapper, open(BOOTSTRAP_RESOURCE));
    }

    public static ChampionResourceManifest load(ObjectMapper mapper, InputStream input) {
        Objects.requireNonNull(mapper, "mapper");
        try (input) {
            if (input == null) throw new IllegalStateException("Champion resource manifest not found");
            return mapper.readValue(input, ChampionResourceManifest.class);
        } catch (IOException error) {
            throw new IllegalStateException("Failed to load champion resource manifest", error);
        }
    }

    public static InputStream open(String resourcePath) {
        return ChampionResourceManifest.class.getResourceAsStream(resourcePath);
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("Missing manifest field: " + field);
        return value;
    }

    private static String resourcePath(String value, String field) {
        String path = required(value, field);
        if (!path.startsWith("/champions/") || !path.endsWith(".json")) {
            throw new IllegalArgumentException("Invalid champion resource path for " + field + ": " + path);
        }
        return path;
    }
}
