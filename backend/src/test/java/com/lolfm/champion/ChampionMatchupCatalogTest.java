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

    @Test void neutralCatalogContainsThirtyChampions() {
        assertThat(catalog.championIds()).hasSize(30);
    }

    @Test void neutralCatalogContainsSixChampionsPerPosition() {
        for (Position position : Position.values()) {
            assertThat(champions.forPosition(position)).hasSize(6);
        }
    }

    @Test void neutralCatalogContainsSeventyFiveUnorderedPairs() {
        assertThat(catalog.profiles()).hasSize(75);
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

    @Test void neutralCatalogContainsEverySamePositionPairExactlyOnce() {
        for (Position position : Position.values()) {
            long count = catalog.profiles().keySet().stream()
                    .filter(pair -> pair.position() == position).count();
            assertThat(count).isEqualTo(15);
        }
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
        assertThatThrownBy(() -> catalog.profiles().values().iterator().next()
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
        List<ChampionMatchupProfile> profiles =
                new ArrayList<>(catalog.profiles().values());
        profiles.removeLast();
        assertThatThrownBy(() ->
                ChampionMatchupCatalog.validatedNeutralCatalog(champions, profiles))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("75");
    }
}
