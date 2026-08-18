package com.lolfm.champion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lolfm.domain.Position;
import java.lang.reflect.Modifier;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

class ThirtyChampionRoleProfilesTest {
    private final ChampionCatalog champions = new ChampionCatalog(new ObjectMapper());
    private final ChampionRoleMatchupProfileCatalog candidate =
            ThirtyChampionRoleProfiles.catalog();

    @Test void candidateCatalogContainsExactlyThirtyProfiles() {
        assertThat(candidate.profiles()).hasSize(30);
    }
    @Test void everyCatalogChampionHasExactlyOneRoleProfile() {
        assertThat(ThirtyChampionRoleProfiles.entries().stream()
                .map(entry -> entry.profile().roleKey()).distinct()).hasSize(30);
    }
    @Test void eachPositionContainsExactlySixProfiles() {
        for (Position position : Position.values()) {
            assertThat(candidate.profiles().keySet().stream()
                    .filter(key -> key.position() == position)).hasSize(6);
        }
    }
    @Test void noChampionRoleKeyIsDuplicated() {
        assertThat(candidate.profiles().keySet().stream().distinct()).hasSize(30);
    }
    @Test void noCatalogChampionIsMissing() {
        assertThat(candidate.profiles().keySet()).allMatch(key ->
                champions.find(key.championId()).isPresent());
    }
    @Test void noUnknownChampionRoleKeyExists() {
        assertThat(candidate.profiles().keySet()).allMatch(key ->
                champions.find(key.championId()).isPresent());
    }
    @Test void everyProfileContainsAllFifteenTraits() {
        assertThat(candidate.profiles().values()).allMatch(profile ->
                profile.traits().size() == ChampionMatchupTrait.values().length);
    }
    @Test void everyTraitIsWithinOneToTwenty() {
        assertThat(candidate.profiles().values().stream()
                .flatMap(profile -> profile.traits().values().stream()))
                .allMatch(value -> value >= 1 && value <= 20);
    }
    @Test void profileCatalogIsImmutable() {
        assertThatThrownBy(() -> candidate.profiles().clear())
                .isInstanceOf(UnsupportedOperationException.class);
    }
    @Test void profileTraitMapIsImmutable() {
        var profile = candidate.profiles().values().iterator().next();
        assertThatThrownBy(() -> profile.traits().clear())
                .isInstanceOf(UnsupportedOperationException.class);
    }
    @Test void profileIdentityUsesChampionRoleKey() {
        assertThat(candidate.profiles().keySet()).allMatch(key ->
                key.championId() != null && key.position() != null);
    }
    @Test void candidateProfilesCannotReachProductionApi() {
        assertThat(java.util.Arrays.stream(
                ChampionRoleMatchupProfileCatalog.class.getMethods())
                .filter(method -> Modifier.isPublic(method.getModifiers()))
                .map(method -> method.getName()))
                .doesNotContain("diagnosticsCandidate", "candidate");
    }
    @Test void productionCatalogContainsApprovedFrozenProfiles() {
        var production = ChampionRoleMatchupProfileCatalog.production();
        assertThat(production.profiles()).hasSize(216);
        candidate.profiles().forEach((key, historical) ->
                assertThat(production.profiles().get(key).traits()).isEqualTo(historical.traits()));
    }
    @Test void activeProfileVersionIsIndependentFromHistoricalVersion() {
        assertThat(ChampionRoleMatchupProfileCatalog.production().version())
                .isEqualTo("full-173-role-matchup-profile-2026-08-v2");
        assertThat(ChampionRoleMatchupProfileCatalog.PRODUCTION_VERSION)
                .isEqualTo("initial-30-role-matchup-profile-candidate-v1");
    }
    @Test void candidateVersionIsExact() {
        assertThat(candidate.version()).isEqualTo(
                "initial-30-role-matchup-profile-candidate-v1");
    }
    @Test void candidateIsExplicitlyNonProduction() {
        assertThat(candidate.prototypeOnly()).isTrue();
    }
    @Test void rationalesCoverAllThirtyProfiles() {
        assertThat(ThirtyChampionRoleProfiles.entries()).hasSize(30);
    }
    @Test void rationaleStrengthCountIsThreeToFive() {
        assertThat(ThirtyChampionRoleProfiles.entries()).allMatch(entry ->
                entry.primaryStrengthTraits().size() >= 3
                        && entry.primaryStrengthTraits().size() <= 5);
    }
    @Test void rationaleWeaknessCountIsTwoToFour() {
        assertThat(ThirtyChampionRoleProfiles.entries()).allMatch(entry ->
                entry.primaryWeaknessTraits().size() >= 2
                        && entry.primaryWeaknessTraits().size() <= 4);
    }
    @Test void rationaleSummaryIsPresent() {
        assertThat(ThirtyChampionRoleProfiles.entries()).allMatch(entry ->
                !entry.kitInteractionSummary().isBlank());
    }
    @Test void exactlyTenPrototypeProfilesAreRetained() {
        assertThat(ThirtyChampionRoleProfiles.entries().stream().filter(entry ->
                entry.profileSource()
                        == ThirtyChampionRoleProfiles.Source
                        .EXISTING_PROTOTYPE_RETAINED)).hasSize(10);
    }
    @Test void noPrototypeProfileWasRevised() {
        assertThat(ThirtyChampionRoleProfiles.entries()).noneMatch(entry ->
                entry.profileSource()
                        == ThirtyChampionRoleProfiles.Source
                        .EXISTING_PROTOTYPE_REVISED);
    }

    @ParameterizedTest(name = "trait {0} exists in every profile")
    @EnumSource(ChampionMatchupTrait.class)
    void everyNamedTraitExistsInEveryProfile(ChampionMatchupTrait trait) {
        assertThat(candidate.profiles().values()).allMatch(profile ->
                profile.traits().containsKey(trait));
    }

    @ParameterizedTest(name = "profile {0} matches catalog")
    @MethodSource("championIds")
    void everyIndividualProfileMatchesCatalog(String id) {
        var definition = champions.get(new ChampionId(id));
        assertThat(candidate.find(new ChampionRoleKey(definition.id(),
                definition.primaryPosition()))).isPresent();
    }

    @ParameterizedTest(name = "prototype {0} retained verbatim")
    @MethodSource("prototypeIds")
    void existingPrototypeProfilesAreRetainedVerbatim(String id) {
        ChampionRoleMatchupProfile expected =
                ChampionRoleMatchupProfileCatalog.prototype().profiles().entrySet()
                        .stream().filter(entry ->
                                entry.getKey().championId().value().equals(id))
                        .map(Map.Entry::getValue).findFirst().orElseThrow();
        ChampionRoleMatchupProfile actual = candidate.profiles().entrySet().stream()
                .filter(entry -> entry.getKey().championId().value().equals(id))
                .map(Map.Entry::getValue).findFirst().orElseThrow();
        assertThat(actual.traits()).isEqualTo(expected.traits());
    }

    static List<String> championIds() {
        return ThirtyChampionRoleProfiles.entries().stream()
                .map(entry -> entry.profile().roleKey().championId().value()).toList();
    }
    static List<String> prototypeIds() {
        return List.of("renekton", "jax", "lee-sin", "viego", "leblanc",
                "viktor", "lucian", "jinx", "nautilus", "lulu");
    }
}
