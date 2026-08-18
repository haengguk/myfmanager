package com.lolfm.champion;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.lolfm.composition.ChampionCompositionProfileCatalog;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import org.junit.jupiter.api.Test;

class ChampionResourceCompletenessStartupTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final ChampionResourceManifest manifest = ChampionResourceManifest.loadDefault(mapper);
    private final ChampionCatalog champions = new ChampionCatalog(mapper, resource(manifest.catalog()));

    @Test
    void missingCatalogEntriesFailDuringResourceMaterialization() {
        assertThatThrownBy(() -> new ChampionPowerProfileCatalog(
                mapper, champions, withoutFirst(manifest.power(), "championProfiles")))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> ChampionMatchupProfileResourceLoader.load(
                mapper, champions, withoutFirst(manifest.matchup(), "championProfiles"), false))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> ChampionCompositionProfileCatalog.load(
                mapper, champions, withoutFirst(manifest.composition(), "championProfiles")))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> ChampionJungleClearProfileCatalog.load(
                mapper, champions, withoutFirst(manifest.jungleClear(), "profiles")))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void extraUnknownChampionFailsDuringResourceMaterialization() {
        assertThatThrownBy(() -> new ChampionPowerProfileCatalog(
                mapper, champions, withUnknownChampion(manifest.power(), "championProfiles")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Unknown champion power profile");
    }

    private InputStream withoutFirst(String path, String arrayField) {
        ObjectNode root = read(path);
        ((ArrayNode) root.required(arrayField)).remove(0);
        return bytes(root);
    }

    private InputStream withUnknownChampion(String path, String arrayField) {
        ObjectNode root = read(path);
        ArrayNode profiles = (ArrayNode) root.required(arrayField);
        ObjectNode unknown = profiles.get(0).deepCopy();
        unknown.put("championId", "unknown-startup-fixture");
        profiles.add(unknown);
        return bytes(root);
    }

    private ObjectNode read(String path) {
        try (InputStream input = resource(path)) {
            return (ObjectNode) mapper.readTree(input);
        } catch (IOException error) {
            throw new IllegalStateException("Failed to prepare synthetic resource", error);
        }
    }

    private InputStream bytes(ObjectNode root) {
        try {
            return new ByteArrayInputStream(mapper.writeValueAsBytes(root));
        } catch (IOException error) {
            throw new IllegalStateException("Failed to serialize synthetic resource", error);
        }
    }

    private InputStream resource(String path) {
        InputStream input = ChampionResourceManifest.open(path);
        if (input == null) throw new IllegalStateException("Missing test resource: " + path);
        return input;
    }
}
