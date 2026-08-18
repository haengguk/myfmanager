package com.lolfm.champion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lolfm.composition.ChampionCompositionProfileCatalog;
import com.lolfm.composition.CompositionCapability;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ChampionResourceHardeningTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void defaultManifestLoadsOneValidatedResourceSetInDeterministicOrder() {
        ChampionResourceSet first = ChampionResourceSet.loadDefault();
        ChampionResourceSet second = ChampionResourceSet.loadDefault();
        assertThat(first.manifest()).isEqualTo(second.manifest());
        assertThat(first.catalog().all()).isEqualTo(second.catalog().all());
        assertThat(first.matchup().profiles()).isEqualTo(second.matchup().profiles());
        assertThat(first.composition().canonicalSerialization())
                .isEqualTo(second.composition().canonicalSerialization());
        assertThat(first.composition().profileHash())
                .isEqualTo("fbf58dc5be12f2b07c5dff7ded9e182d7829999d2255e65dbbd073ccde2688d1");
    }

    @Test
    void manifestRejectsMissingFieldsAndMissingReferencedResources() {
        assertThatThrownBy(() -> ChampionResourceManifest.load(mapper, bytes(
                "{\"manifestVersion\":\"bad\",\"catalog\":\"/champions/x.json\"}")))
                .hasRootCauseInstanceOf(IllegalArgumentException.class);
        ChampionResourceManifest missing = new ChampionResourceManifest(
                "missing-v1", "/champions/missing.json", "/champions/missing.json",
                "/champions/missing.json", "/champions/missing.json", "/champions/missing.json");
        assertThatThrownBy(() -> ChampionResourceSet.load(mapper, missing))
                .hasMessageContaining("Missing catalog resource");
    }

    @Test
    void productionDefaultInitializationUsesValidatedResourceSet() throws Exception {
        String simulator = Files.readString(Path.of(
                "src/main/java/com/lolfm/simulator/MatchSimulator.java"));
        assertThat(simulator).contains("ChampionResourceSet.loadDefault()")
                .doesNotContain("ChampionResourceCompletenessValidator.validate(");
    }

    @Test
    void compositionCapabilitiesAcceptOnlyOneThroughTwenty() throws Exception {
        assertThat(loadComposition(1).profiles()).hasSize(1);
        assertThat(loadComposition(20).profiles()).hasSize(1);
        assertInvalidCapability(0);
        assertInvalidCapability(-1);
        assertInvalidCapability(21);
        assertThatThrownBy(() -> ChampionCompositionProfileCatalog.load(
                mapper, oneChampionCatalog(), bytes(compositionJson(10, true))))
                .hasMessageContaining("All 15 composition capabilities required");
    }

    private ChampionCompositionProfileCatalog loadComposition(int rating) throws Exception {
        return ChampionCompositionProfileCatalog.load(
                mapper, oneChampionCatalog(), bytes(compositionJson(rating, false)));
    }

    private void assertInvalidCapability(int rating) {
        assertThatThrownBy(() -> loadComposition(rating))
                .hasMessageContaining("Invalid capability ENGAGE");
    }

    private ChampionCatalog oneChampionCatalog() {
        return new ChampionCatalog(mapper, bytes("""
                {
                  "championPoolVersion":"boundary-v1",
                  "championBalanceVersion":"boundary-b1",
                  "riotDataVersion":"test",
                  "champions":[{
                    "id":"boundary",
                    "displayNameKo":"경계",
                    "displayNameEn":"Boundary",
                    "riotAssetId":"Boundary",
                    "primaryPosition":"TOP",
                    "supportedPositions":["TOP"]
                  }]
                }
                """));
    }

    private String compositionJson(int engage, boolean omitLast) throws Exception {
        Map<String, Integer> capabilities = new LinkedHashMap<>();
        for (CompositionCapability capability : CompositionCapability.values()) {
            capabilities.put(capability.name(), capability == CompositionCapability.ENGAGE ? engage : 10);
        }
        if (omitLast) capabilities.remove(CompositionCapability.BACKLINE_ACCESS.name());
        Map<String, Object> profile = new LinkedHashMap<>();
        profile.put("championId", "boundary");
        profile.put("baseCapabilities", capabilities);
        profile.put("damageProfile", Map.of(
                "physicalThreat", 10, "magicThreat", 10, "trueDamageThreat", 0));
        profile.put("roleOverrides", List.of());
        return mapper.writeValueAsString(Map.of(
                "profileVersion", "boundary-composition-v1",
                "requiredChampionPoolVersion", "boundary-v1",
                "championProfiles", List.of(profile)));
    }

    private InputStream bytes(String value) {
        return new ByteArrayInputStream(value.getBytes(StandardCharsets.UTF_8));
    }
}
