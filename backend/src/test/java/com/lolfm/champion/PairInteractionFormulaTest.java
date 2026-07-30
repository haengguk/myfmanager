package com.lolfm.champion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lolfm.simulator.ProgressionCombatContext;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

class PairInteractionFormulaTest {
    private final ChampionRoleMatchupProfileCatalog profiles =
            ThirtyChampionRoleProfiles.catalog();
    private final CenteredPairInteractionFormula formula =
            new CenteredPairInteractionFormula(new ChampionMatchupRuleCatalog());

    @Test void phase13C3ProfilesRemainByteEquivalent() {
        assertThat(ThirtyChampionRoleProfiles.VERSION).isEqualTo(
                "initial-30-role-matchup-profile-candidate-v1");
        assertThat(ThirtyChampionRoleProfiles.entries()).hasSize(30)
                .allMatch(entry -> entry.profile().profileVersion()
                        .equals(ThirtyChampionRoleProfiles.VERSION))
                .allMatch(entry -> entry.profile().traits().size()
                        == ChampionMatchupTrait.values().length);
    }
    @Test void allThirtyProfilesRemainUnchanged() {
        assertThat(profiles.profiles()).hasSize(30);
    }
    @Test void ruleWeightsRemainUnchanged() {
        ChampionMatchupRuleCatalog rules = new ChampionMatchupRuleCatalog();
        for (ProgressionCombatContext context :
                ProgressionCombatContext.values()) {
            assertThat(rules.weightSum(context)).isCloseTo(1, within(1e-12));
        }
    }
    @Test void productionCatalogRemainsNeutral() {
        ChampionCatalog champions = new ChampionCatalog(new ObjectMapper());
        assertThat(ChampionMatchupCatalog.neutral(champions).profiles().values())
                .allMatch(profile -> profile.firstChampionEdges().values().stream()
                        .allMatch(edge -> edge == 0));
    }
    @Test void rawTraitIsNormalizedCorrectly() {
        var profile = profile("leblanc");
        var vector = ChampionMatchupInteractionVector.from(profile);
        assertThat(vector.trait(ChampionMatchupTrait.MOBILITY).raw())
                .isEqualTo(1);
        assertThat(vector.trait(ChampionMatchupTrait.ANTI_TANK).raw())
                .isCloseTo(1.0 / 19, within(1e-12));
    }
    @Test void centeredTraitsUseProfileMean() {
        var vector = ChampionMatchupInteractionVector.from(profile("leblanc"));
        assertThat(vector.traits().values().stream().mapToDouble(value ->
                value.centered()).sum()).isCloseTo(0, within(1e-12));
    }
    @Test void strengthUsesPositiveCenteredValue() {
        var value = ChampionMatchupInteractionVector.from(profile("leblanc"))
                .trait(ChampionMatchupTrait.MOBILITY);
        assertThat(value.interactionStrength())
                .isEqualTo(value.centered() * value.raw());
    }
    @Test void vulnerabilityUsesNegativeCenteredValue() {
        var value = ChampionMatchupInteractionVector.from(profile("leblanc"))
                .trait(ChampionMatchupTrait.ANTI_TANK);
        assertThat(value.interactionVulnerability()).isEqualTo(
                -value.centered() * (1 - value.raw()));
    }
    @Test void lowAbsoluteTraitCannotBecomeLargeStrength() {
        var vector = ChampionMatchupInteractionVector.from(profile("leblanc"));
        assertThat(vector.trait(ChampionMatchupTrait.ANTI_TANK)
                .interactionStrength()).isZero();
    }
    @Test void interactionVectorIsImmutable() {
        var vector = ChampionMatchupInteractionVector.from(profile("leblanc"));
        assertThatThrownBy(() -> vector.traits().clear())
                .isInstanceOf(UnsupportedOperationException.class);
    }
    @Test void negativeZeroIsNormalized() {
        var result = formula.evaluate(profile("jax"), profile("jax"),
                ProgressionCombatContext.LANE_COMBAT);
        assertThat(Double.doubleToRawLongBits(result.finalEdge()))
                .isEqualTo(Double.doubleToRawLongBits(0.0));
    }
    @Test void identicalProfilesProduceZeroEdge() {
        assertThat(formula.evaluate(profile("jax"), profile("jax"),
                ProgressionCombatContext.TEAMFIGHT).finalEdge()).isZero();
    }
    @Test void reverseInteractionNegatesForward() {
        double forward = formula.evaluate(profile("jax"), profile("renekton"),
                ProgressionCombatContext.LANE_COMBAT).finalEdge();
        double reverse = formula.evaluate(profile("renekton"), profile("jax"),
                ProgressionCombatContext.LANE_COMBAT).finalEdge();
        assertThat(forward).isEqualTo(-reverse);
    }
    @Test void interactionEdgeIsFinite() {
        assertThat(PairInteractionGeneratedCatalog.build(
                new ChampionCatalog(new ObjectMapper())).rows())
                .allMatch(row -> Double.isFinite(row.interactionEdge()));
    }
    @Test void interactionEdgeRespectsCap() {
        assertThat(PairInteractionGeneratedCatalog.build(
                new ChampionCatalog(new ObjectMapper())).rows())
                .allMatch(row -> Math.abs(row.interactionEdge()) <= .30);
    }
    @Test void interactionExplanationSumsExactly() {
        assertThat(PairInteractionGeneratedCatalog.build(
                new ChampionCatalog(new ObjectMapper())).results().values())
                .allMatch(result -> Math.abs(result.ruleContributions().stream()
                        .mapToDouble(value -> value.weightedContribution()).sum()
                        - result.weightedRawEdge()) <= 1e-12);
    }
    @Test void formulaContainsNoChampionSpecificBranch() throws Exception {
        String source = Files.readString(Path.of(
                "src/test/java/com/lolfm/champion/"
                        + "CenteredPairInteractionFormula.java"));
        for (String id : championIds()) assertThat(source).doesNotContain("\"" + id + "\"");
    }
    @Test void formulaContainsNoManualPairTable() throws Exception {
        String source = Files.readString(Path.of(
                "src/test/java/com/lolfm/champion/"
                        + "CenteredPairInteractionFormula.java"));
        assertThat(source).doesNotContain("Map<ChampionMatchupPair");
    }
    @Test void candidateFormulaCannotReachProductionApi() {
        assertThat(java.util.Arrays.stream(
                CenteredPairInteractionFormula.class.getDeclaredMethods()))
                .allMatch(method -> method.getDeclaringClass()
                        == CenteredPairInteractionFormula.class);
        assertThat(CenteredPairInteractionFormula.class.getProtectionDomain()
                .getCodeSource().getLocation().toString()).contains("test");
    }

    @ParameterizedTest
    @EnumSource(ChampionMatchupTrait.class)
    void everyTraitProducesFiniteInteractionVector(ChampionMatchupTrait trait) {
        var value = ChampionMatchupInteractionVector.from(profile("leblanc"))
                .trait(trait);
        assertThat(value.raw()).isFinite();
        assertThat(value.centered()).isFinite();
        assertThat(value.interactionStrength()).isFinite();
        assertThat(value.interactionVulnerability()).isFinite();
    }

    @ParameterizedTest
    @MethodSource("championIds")
    void everyProfileProducesCompleteImmutableVector(String champion) {
        var vector = ChampionMatchupInteractionVector.from(profile(champion));
        assertThat(vector.traits()).hasSize(15);
        assertThat(vector.profileMean()).isBetween(0.0, 1.0);
    }

    private ChampionRoleMatchupProfile profile(String id) {
        return profiles.profiles().values().stream().filter(value ->
                value.roleKey().championId().value().equals(id))
                .findFirst().orElseThrow();
    }
    static List<String> championIds() {
        return ThirtyChampionRoleProfiles.entries().stream().map(entry ->
                entry.profile().roleKey().championId().value()).toList();
    }
}
