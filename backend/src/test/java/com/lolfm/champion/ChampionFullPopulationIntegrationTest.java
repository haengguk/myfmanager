package com.lolfm.champion;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lolfm.composition.CompositionCapability;
import com.lolfm.domain.Position;
import com.lolfm.simulator.ProgressionCombatContext;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.Test;

class ChampionFullPopulationIntegrationTest {
    private static final String FULL_POOL_VERSION = "full-173-2026-08-v1";
    private static final ChampionResourceManifest HISTORICAL_MANIFEST =
            new ChampionResourceManifest(
                    "initial-30-resource-set-v1",
                    "/champions/champion-pool-initial-30-v1.json",
                    "/champions/champion-power-initial-30-v1.json",
                    "/champions/champion-matchup-role-profiles-initial-30-v1.json",
                    "/champions/champion-composition-role-profiles-initial-30-v1.json",
                    "/champions/champion-jungle-clear-initial-30-neutral-v1.json");

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void defaultManifestLoadsTheExactFullPopulation() {
        ChampionResourceSet resources = ChampionResourceSet.loadDefault();

        assertThat(resources.manifest().manifestVersion())
                .isEqualTo("full-173-resource-set-2026-08-v1");
        assertThat(resources.catalog().championPoolVersion()).isEqualTo(FULL_POOL_VERSION);
        assertThat(resources.catalog().championBalanceVersion())
                .isEqualTo("full-173-power-2026-08-v1");
        assertThat(resources.catalog().riotDataVersion()).isEqualTo("16.15.1");
        assertThat(resources.catalog().all()).hasSize(173);
        assertThat(resources.catalog().legalRoleKeys()).hasSize(216);
        assertThat(roleCounts(resources.catalog())).containsExactlyInAnyOrderEntriesOf(Map.of(
                Position.TOP, 54,
                Position.JUNGLE, 51,
                Position.MID, 45,
                Position.ADC, 31,
                Position.SUPPORT, 35));

        assertThat(resources.power().profileVersion()).isEqualTo("full-173-power-2026-08-v1");
        assertThat(resources.power().all()).hasSize(173);
        assertThat(resources.matchup().version())
                .isEqualTo("full-173-role-matchup-profile-2026-08-v2");
        assertThat(resources.matchup().profiles()).hasSize(216);
        assertThat(resources.composition().version())
                .isEqualTo("full-173-composition-profile-2026-08-v2");
        assertThat(resources.composition().profiles()).hasSize(216);
        assertThat(resources.composition().profileHash())
                .isEqualTo("23d616cab6abea69d5ad783f405b0b4518a14608b0be4eac3d53f669acab6877");
        assertThat(resources.jungleClear().profiles()).hasSize(51);
        assertThat(resources.jungleClear().profiles().values())
                .allMatch(profile -> !profile.gameplayEnabled());
    }

    @Test
    void originalThirtyAndFrozenJungleSixRemainExactHistoricalOracles() {
        ChampionResourceSet historical = ChampionResourceSet.load(mapper, HISTORICAL_MANIFEST);
        ChampionResourceSet active = ChampionResourceSet.loadDefault();

        assertThat(historical.catalog().all()).hasSize(30);
        for (ChampionDefinition expected : historical.catalog().all()) {
            ChampionDefinition actual = active.catalog().get(expected.id());
            assertThat(actual.displayNameKo()).isEqualTo(expected.displayNameKo());
            assertThat(actual.displayNameEn()).isEqualTo(expected.displayNameEn());
            assertThat(actual.riotAssetId()).isEqualTo(expected.riotAssetId());
            assertThat(actual.primaryPosition()).isEqualTo(expected.primaryPosition());
            if (expected.id().value().equals("varus")) {
                assertThat(actual.supportedPositions()).containsExactlyInAnyOrder(Position.TOP, Position.ADC);
            } else {
                assertThat(actual.supportedPositions()).isEqualTo(expected.supportedPositions());
            }

            ChampionPowerProfile expectedPower = historical.power().get(expected.id());
            ChampionPowerProfile actualPower = active.power().get(expected.id());
            assertThat(actualPower.levelCurveId()).isEqualTo(expectedPower.levelCurveId());
            assertThat(actualPower.itemCurveId()).isEqualTo(expectedPower.itemCurveId());
            assertThat(actualPower.levelCurve()).isEqualTo(expectedPower.levelCurve());
            assertThat(actualPower.itemModifiers()).isEqualTo(expectedPower.itemModifiers());
            assertThat(actualPower.contextModifiers()).isEqualTo(expectedPower.contextModifiers());
            assertThat(actualPower.tags()).isEqualTo(expectedPower.tags());
        }

        historical.matchup().profiles().forEach((key, expected) ->
                assertThat(active.matchup().profiles().get(key).traits()).isEqualTo(expected.traits()));
        historical.composition().profiles().forEach((key, expected) ->
                assertThat(active.composition().profiles()).containsEntry(key, expected));
        historical.jungleClear().profiles().forEach((key, expected) ->
                assertThat(active.jungleClear().profiles()).containsEntry(key, expected));

        assertThat(historical.matchup().version())
                .isEqualTo("initial-30-role-matchup-profile-candidate-v1");
        assertThat(historical.composition().version())
                .isEqualTo("thirty-champion-composition-profile-candidate-v2");
        assertThat(historical.composition().profileHash())
                .isEqualTo("fbf58dc5be12f2b07c5dff7ded9e182d7829999d2255e65dbbd073ccde2688d1");
    }

    @Test
    void fullAuthoringDataHasExpectedFlexOverridesAndNoDuplicateChampionVectors() {
        ChampionResourceSet active = ChampionResourceSet.loadDefault();
        ChampionResourceSet historical = ChampionResourceSet.load(mapper, HISTORICAL_MANIFEST);
        Set<String> historicalIds = historical.catalog().all().stream()
                .map(champion -> champion.id().value()).collect(Collectors.toSet());

        assertThat(active.catalog().all().stream()
                .filter(champion -> !historicalIds.contains(champion.id().value()))
                .filter(champion -> champion.supportedPositions().size() > 1))
                .hasSize(35);
        assertThat(active.power().warnings()).noneMatch(warning -> warning.startsWith("EXACT_DUPLICATE:"));
        assertThat(active.power().warnings()).noneMatch(warning ->
                (warning.startsWith("NO_STRENGTH:") || warning.startsWith("NO_WEAKNESS:"))
                        && !historicalIds.contains(warning.substring(warning.indexOf(':') + 1)));

        JsonNode matchup = read(active.manifest().matchup());
        JsonNode composition = read(active.manifest().composition());
        assertThat(newOverrideCount(matchup, historicalIds)).isEqualTo(28);
        assertThat(newOverrideCount(composition, historicalIds)).isEqualTo(29);
        assertUniqueNewSignatures(matchup, historicalIds, profile ->
                profile.required("baseTraits").toString());
        assertUniqueNewSignatures(composition, historicalIds, profile ->
                profile.required("baseCapabilities") + "|" + profile.required("damageProfile"));

        active.matchup().profiles().values().forEach(profile -> {
            assertThat(profile.traits()).hasSize(ChampionMatchupTrait.values().length);
            assertThat(profile.traits().values()).allMatch(value -> value >= 1 && value <= 20);
        });
        active.composition().profiles().values().forEach(profile -> {
            for (CompositionCapability capability : CompositionCapability.values()) {
                assertThat(profile.capability(capability)).isBetween(1, 20);
            }
        });
        active.power().all().forEach(profile -> {
            assertThat(profile.contextModifiers()).hasSize(ProgressionCombatContext.values().length);
            assertThat(profile.contextModifiers().values()).allMatch(Double::isFinite);
        });
    }

    private Map<Position, Integer> roleCounts(ChampionCatalog catalog) {
        return java.util.Arrays.stream(Position.values()).collect(Collectors.toMap(
                Function.identity(), position -> catalog.forPosition(position).size()));
    }

    private int newOverrideCount(JsonNode root, Set<String> historicalIds) {
        return StreamSupport.stream(root.required("championProfiles").spliterator(), false)
                .filter(profile -> !historicalIds.contains(profile.required("championId").asText()))
                .mapToInt(profile -> profile.required("roleOverrides").size())
                .sum();
    }

    private void assertUniqueNewSignatures(
            JsonNode root, Set<String> historicalIds, Function<JsonNode, String> signature
    ) {
        Set<String> oldSignatures = new HashSet<>();
        Set<String> newSignatures = new HashSet<>();
        for (JsonNode profile : root.required("championProfiles")) {
            String value = signature.apply(profile);
            if (historicalIds.contains(profile.required("championId").asText())) {
                oldSignatures.add(value);
            } else {
                assertThat(newSignatures.add(value)).isTrue();
                assertThat(oldSignatures).doesNotContain(value);
            }
        }
    }

    private JsonNode read(String resourcePath) {
        try (InputStream input = ChampionResourceManifest.open(resourcePath)) {
            if (input == null) throw new IllegalStateException("Missing test resource " + resourcePath);
            return mapper.readTree(input);
        } catch (IOException error) {
            throw new IllegalStateException("Failed to read test resource " + resourcePath, error);
        }
    }
}
