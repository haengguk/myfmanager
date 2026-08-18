package com.lolfm.champion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lolfm.domain.Position;
import com.lolfm.simulator.ProgressionCombatContext;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ChampionMatchupCatalogTest {
    private ChampionCatalog champions;
    private ChampionMatchupCatalog catalog;

    @BeforeEach
    void setUp() {
        champions = new ChampionCatalog(new ObjectMapper());
        catalog = ChampionMatchupCatalog.neutral(champions);
    }

    @Test void neutralCatalogContainsFullChampionPopulation() {
        assertThat(catalog.championIds()).hasSize(173);
    }

    @Test void neutralCatalogUsesExactPrimaryPositionPopulation() {
        assertThat(champions.all().stream().filter(c -> c.primaryPosition() == Position.TOP)).hasSize(41);
        assertThat(champions.all().stream().filter(c -> c.primaryPosition() == Position.JUNGLE)).hasSize(42);
        assertThat(champions.all().stream().filter(c -> c.primaryPosition() == Position.MID)).hasSize(37);
        assertThat(champions.all().stream().filter(c -> c.primaryPosition() == Position.ADC)).hasSize(25);
        assertThat(champions.all().stream().filter(c -> c.primaryPosition() == Position.SUPPORT)).hasSize(28);
    }

    @Test void neutralCatalogDoesNotMaterializeQuadraticZeroPairs() {
        assertThat(catalog.profiles()).isEmpty();
    }

    @Test void neutralCatalogContainsNoSelfPair() {
        assertThat(catalog.profiles().keySet())
                .allMatch(pair -> !pair.first().equals(pair.second()));
    }

    @Test void neutralCatalogContainsNoCrossPositionPair() {
        assertThat(catalog.profiles().keySet()).allMatch(pair ->
                champions.get(pair.first()).primaryPosition() == pair.position()
                        && champions.get(pair.second()).primaryPosition() == pair.position());
    }

    @Test void neutralCatalogContainsNoDuplicatePair() {
        assertThat(catalog.profiles().keySet()).doesNotHaveDuplicates();
    }

    @Test void sparseNeutralCatalogReturnsZeroForFullPopulationPair() {
        assertThat(catalog.contribution(
                new ChampionId("aatrox"), new ChampionId("camille"),
                Position.TOP, ProgressionCombatContext.LANE_COMBAT)).isZero();
    }

    @Test void productionCatalogContainsOnlyZeroEdges() {
        assertThat(catalog.profiles().values())
                .allMatch(profile -> profile.firstChampionEdges().values().stream()
                        .allMatch(edge -> edge == 0.0));
    }

    @Test void productionCatalogContainsOnlyFiniteValues() {
        assertThat(catalog.profiles().values())
                .allMatch(profile -> profile.firstChampionEdges().values().stream()
                        .allMatch(Double::isFinite));
    }

    @Test void catalogCollectionsAreImmutable() {
        assertThatThrownBy(() -> catalog.profiles().clear())
                .isInstanceOf(UnsupportedOperationException.class);
        ChampionMatchupCatalog focused = ChampionMatchupTestCatalogFactory.focused(champions);
        assertThatThrownBy(() -> focused.profiles().values().iterator().next()
                .firstChampionEdges().clear()).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test void canonicalPairDoesNotUseDisplayNameOrEnumOrdinal() {
        ChampionMatchupPair pair = ChampionMatchupPair.of(
                champions.get(new ChampionId("renekton")),
                champions.get(new ChampionId("jax")));
        assertThat(pair.first().value()).isEqualTo("jax");
        assertThat(pair.second().value()).isEqualTo("renekton");
        assertThat(pair.position()).isEqualTo(Position.TOP);
    }

    @Test void reverseLookupNegatesForwardLookup() {
        ChampionMatchupCatalog focused = ChampionMatchupTestCatalogFactory.focused(champions);
        double forward = focused.contribution(
                new ChampionId("renekton"), new ChampionId("jax"),
                Position.TOP, ProgressionCombatContext.LANE_COMBAT);
        double reverse = focused.contribution(
                new ChampionId("jax"), new ChampionId("renekton"),
                Position.TOP, ProgressionCombatContext.LANE_COMBAT);
        assertThat(forward).isEqualTo(.25);
        assertThat(reverse).isEqualTo(-forward);
    }

    @Test void selfLookupReturnsZero() {
        assertThat(catalog.contribution(
                new ChampionId("jax"), new ChampionId("jax"),
                Position.TOP, ProgressionCombatContext.LANE_COMBAT)).isZero();
    }

    @Test void crossPositionLookupIsNotApplied() {
        assertThat(catalog.contribution(
                new ChampionId("jax"), new ChampionId("viktor"),
                Position.TOP, ProgressionCombatContext.LANE_COMBAT)).isZero();
    }

    @Test void negativeZeroIsNormalizedToZero() {
        ChampionMatchupPair pair = ChampionMatchupPair.of(
                champions.get(new ChampionId("renekton")),
                champions.get(new ChampionId("jax")));
        EnumMap<ProgressionCombatContext, Double> edges =
                new EnumMap<>(ProgressionCombatContext.class);
        edges.put(ProgressionCombatContext.LANE_COMBAT, -0.0);
        ChampionMatchupCatalog value = ChampionMatchupCatalog.testCatalog(
                champions, List.of(new ChampionMatchupProfile(pair, edges)));
        double edge = value.contribution(
                pair.first(), pair.second(), Position.TOP,
                ProgressionCombatContext.LANE_COMBAT);
        assertThat(Double.doubleToRawLongBits(edge))
                .isEqualTo(Double.doubleToRawLongBits(0.0));
    }

    @Test void missingProductionPairFailsCatalogValidation() {
        assertThatThrownBy(() ->
                ChampionMatchupCatalog.validatedNeutralCatalog(champions, List.of()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("3025");
    }
}
