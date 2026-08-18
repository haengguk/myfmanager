package com.lolfm.champion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.lolfm.composition.ChampionCompositionProfileCatalog;
import com.lolfm.composition.CompositionInteractionFormula;
import com.lolfm.composition.FrozenCompositionInteractionRuntimePolicy;
import com.lolfm.composition.ThirtyChampionCompositionProfiles;
import com.lolfm.domain.Position;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import org.junit.jupiter.api.Test;

class ChampionResourceIdentityScopeTest {
    private static final String EXPANDED_POOL_VERSION = "identity-scope-expanded-pool-v1";
    private static final String EXTRA_CHAMPION_ID = "identity-scope-extra";

    private final ObjectMapper mapper = new ObjectMapper();
    private final ChampionResourceManifest activeManifest = ChampionResourceManifest.loadDefault(mapper);

    @Test
    void frozenCompositionPolicyProtectsHistoricalSelfIdentityOnly() {
        FrozenCompositionInteractionRuntimePolicy policy = FrozenCompositionInteractionRuntimePolicy.current();
        policy.verifyExactIdentity();

        String canonical = FrozenCompositionInteractionRuntimePolicy.candidateCanonicalSerialization(
                CompositionInteractionFormula.PRODUCT_EXPOSURE);
        assertThat(canonical)
                .contains("frozenProfileVersion=" + FrozenCompositionInteractionRuntimePolicy.PROFILE_VERSION)
                .contains("frozenProfileHash=" + FrozenCompositionInteractionRuntimePolicy.PROFILE_HASH)
                .contains("gain=NONE\ndeadzone=NONE\noverrideCount=0\n");
        assertThat(FrozenCompositionInteractionRuntimePolicy.candidateHashFor(policy.formula()))
                .isEqualTo(FrozenCompositionInteractionRuntimePolicy.CANDIDATE_HASH);

        assertThatThrownBy(() -> policyWith("bad-historical-profile-hash", policy.candidateHash())
                .verifyExactIdentity())
                .hasMessageContaining("profile identity");
        assertThatThrownBy(() -> policyWith(policy.profileHash(), "bad-candidate-hash")
                .verifyExactIdentity())
                .hasMessageContaining("candidate identity");
    }

    @Test
    void currentOriginalThirtyRemainExactHistoricalOracles() {
        ChampionResourceSet current = ChampionResourceSet.loadDefault();
        assertHistoricalCompositionSubset(current.composition());
        assertHistoricalMatchupSubset(current.matchup());
        assertThat(current.composition().version())
                .isEqualTo("full-173-composition-profile-2026-08-v2");
        assertThat(current.composition().profileHash())
                .isNotEqualTo(FrozenCompositionInteractionRuntimePolicy.PROFILE_HASH);
        assertThat(current.matchup().version())
                .isEqualTo("full-173-role-matchup-profile-2026-08-v2");
        assertThat(ThirtyChampionCompositionProfiles.profileHash())
                .isEqualTo(FrozenCompositionInteractionRuntimePolicy.PROFILE_HASH);
        assertThat(ChampionMatchupProductionPolicy.PROFILE_VERSION)
                .isEqualTo("initial-30-role-matchup-profile-candidate-v1");
        assertThat(ChampionMatchupProductionPolicy.PROFILE_HASH)
                .isEqualTo("c8956937e8c9032654feb2bb17ff7ef66d68a964b4f1f6ed98853400f5b3dc64");
    }

    @Test
    void expandedActiveResourceSetMayHaveIndependentVersionAndHash() {
        ExpandedResources expanded = loadExpanded(0, 0, true);
        ChampionRoleKey extraTop = new ChampionRoleKey(new ChampionId(EXTRA_CHAMPION_ID), Position.TOP);

        assertThat(expanded.resources().catalog().find(extraTop.championId())).isPresent();
        assertThat(expanded.resources().power().get(extraTop.championId())).isNotNull();
        assertThat(expanded.resources().matchup().find(extraTop)).isPresent();
        assertThat(expanded.resources().composition().profiles()).containsKey(extraTop);
        assertThat(expanded.resources().composition().version())
                .isEqualTo("identity-scope-expanded-composition-v1");
        assertThat(expanded.resources().composition().profileHash())
                .isNotEqualTo(FrozenCompositionInteractionRuntimePolicy.PROFILE_HASH);
        assertThat(expanded.resources().matchup().version())
                .isEqualTo("identity-scope-expanded-matchup-v1");

        assertHistoricalCompositionSubset(expanded.resources().composition());
        assertHistoricalMatchupSubset(expanded.resources().matchup());
        FrozenCompositionInteractionRuntimePolicy.current().verifyExactIdentity();
    }

    @Test
    void historicalSubsetOracleRejectsProfileDrift() {
        ExpandedResources compositionDrift = loadExpanded(1, 0, true);
        assertThatThrownBy(() -> assertHistoricalCompositionSubset(compositionDrift.resources().composition()))
                .isInstanceOf(AssertionError.class);

        ExpandedResources matchupDrift = loadExpanded(0, 1, true);
        assertThatThrownBy(() -> assertHistoricalMatchupSubset(matchupDrift.resources().matchup()))
                .isInstanceOf(AssertionError.class);
    }

    @Test
    void expandedActiveResourceSetRejectsMissingRequiredProfile() {
        assertThatThrownBy(() -> loadExpanded(0, 0, false))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Composition coverage");
    }

    private ExpandedResources loadExpanded(
            int compositionDrift, int matchupDrift, boolean includeExtraComposition
    ) {
        ObjectNode catalogJson = read(activeManifest.catalog());
        catalogJson.put("championPoolVersion", EXPANDED_POOL_VERSION);
        ObjectNode extraChampion = cloneFirst(catalogJson, "champions");
        extraChampion.put("id", EXTRA_CHAMPION_ID);
        extraChampion.put("displayNameKo", "아이덴티티 스코프 테스트");
        extraChampion.put("displayNameEn", "Identity Scope Test");
        extraChampion.put("riotAssetId", "IdentityScopeTest");
        extraChampion.put("primaryPosition", "TOP");
        extraChampion.putArray("supportedPositions").add("TOP");
        ((ArrayNode) catalogJson.required("champions")).add(extraChampion);
        ChampionCatalog champions = new ChampionCatalog(mapper, bytes(catalogJson));

        ObjectNode powerJson = expandedCatalog(activeManifest.power(), "identity-scope-expanded-power-v1");
        addExtraProfile(powerJson, "championProfiles");
        ChampionPowerProfileCatalog power = new ChampionPowerProfileCatalog(mapper, champions, bytes(powerJson));

        ObjectNode matchupJson = expandedCatalog(
                activeManifest.matchup(), "identity-scope-expanded-matchup-v1");
        if (matchupDrift != 0) increment(matchupJson, "championProfiles", "baseTraits", "POKE", matchupDrift);
        addExtraProfile(matchupJson, "championProfiles");
        ChampionRoleMatchupProfileCatalog matchup = ChampionMatchupProfileResourceLoader.load(
                mapper, champions, bytes(matchupJson), false);

        ObjectNode compositionJson = expandedCatalog(
                activeManifest.composition(), "identity-scope-expanded-composition-v1");
        if (compositionDrift != 0) {
            increment(compositionJson, "championProfiles", "baseCapabilities", "ENGAGE", compositionDrift);
        }
        if (includeExtraComposition) addExtraProfile(compositionJson, "championProfiles");
        ChampionCompositionProfileCatalog composition = ChampionCompositionProfileCatalog.load(
                mapper, champions, bytes(compositionJson));

        ObjectNode jungleJson = expandedCatalog(
                activeManifest.jungleClear(), "identity-scope-expanded-jungle-v1");
        ChampionJungleClearProfileCatalog jungle = ChampionJungleClearProfileCatalog.load(
                mapper, champions, bytes(jungleJson));

        ChampionResourceManifest syntheticManifest = new ChampionResourceManifest(
                "identity-scope-expanded-manifest-v1",
                activeManifest.catalog(), activeManifest.power(), activeManifest.matchup(),
                activeManifest.composition(), activeManifest.jungleClear());
        return new ExpandedResources(new ChampionResourceSet(
                syntheticManifest, champions, power, matchup, composition, jungle));
    }

    private ObjectNode expandedCatalog(String path, String profileVersion) {
        ObjectNode root = read(path);
        root.put("requiredChampionPoolVersion", EXPANDED_POOL_VERSION);
        root.put("profileVersion", profileVersion);
        return root;
    }

    private void addExtraProfile(ObjectNode root, String arrayField) {
        ObjectNode extra = cloneFirst(root, arrayField);
        extra.put("championId", EXTRA_CHAMPION_ID);
        ((ArrayNode) root.required(arrayField)).add(extra);
    }

    private void increment(
            ObjectNode root, String profileField, String valuesField, String valueName, int delta
    ) {
        ObjectNode profile = (ObjectNode) root.required(profileField).get(0);
        ObjectNode values = (ObjectNode) profile.required(valuesField);
        values.put(valueName, values.required(valueName).asInt() + delta);
    }

    private void assertHistoricalCompositionSubset(ChampionCompositionProfileCatalog active) {
        ThirtyChampionCompositionProfiles.all().forEach((key, historical) ->
                assertThat(active.profiles()).containsEntry(key, historical));
    }

    private void assertHistoricalMatchupSubset(ChampionRoleMatchupProfileCatalog active) {
        for (ThirtyChampionRoleProfiles.Entry historical : ThirtyChampionRoleProfiles.entries()) {
            ChampionRoleMatchupProfile actual = active.find(historical.profile().roleKey()).orElseThrow();
            assertThat(actual.traits()).isEqualTo(historical.profile().traits());
        }
    }

    private FrozenCompositionInteractionRuntimePolicy policyWith(String profileHash, String candidateHash) {
        return new FrozenCompositionInteractionRuntimePolicy(
                FrozenCompositionInteractionRuntimePolicy.PROFILE_VERSION,
                profileHash,
                FrozenCompositionInteractionRuntimePolicy.RULE_CATALOG_VERSION,
                FrozenCompositionInteractionRuntimePolicy.RULE_CATALOG_HASH,
                CompositionInteractionFormula.PRODUCT_EXPOSURE,
                FrozenCompositionInteractionRuntimePolicy.CANDIDATE_VERSION,
                candidateHash,
                "NONE", "NONE", 0);
    }

    private ObjectNode cloneFirst(ObjectNode root, String arrayField) {
        return ((ObjectNode) root.required(arrayField).get(0)).deepCopy();
    }

    private ObjectNode read(String path) {
        try (InputStream input = resource(path)) {
            return (ObjectNode) mapper.readTree(input);
        } catch (IOException error) {
            throw new IllegalStateException("Failed to read synthetic source " + path, error);
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
        if (input == null) throw new IllegalStateException("Missing test resource " + path);
        return input;
    }

    private record ExpandedResources(ChampionResourceSet resources) {}
}
