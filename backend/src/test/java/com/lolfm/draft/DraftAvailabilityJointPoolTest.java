package com.lolfm.draft;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.lolfm.champion.ChampionCatalog;
import com.lolfm.champion.ChampionDefinition;
import com.lolfm.champion.ChampionId;
import com.lolfm.domain.Position;
import com.lolfm.simulator.TeamSide;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class DraftAvailabilityJointPoolTest {
    @Test
    void independentFiveRoleChecksCannotReuseTheSameFiveChampionsAcrossBothTeams() {
        ChampionCatalog catalog = catalog(1);
        DraftAvailability availability = new DraftAvailability(
                catalog, new RoleAssignmentSolver(catalog));
        DraftState state = fresh();

        assertThat(availability.canCompleteAfterExcluding(
                state, TeamSide.BLUE, null)).isTrue();
        assertThat(availability.canCompleteAfterExcluding(
                state, TeamSide.RED, null)).isTrue();
        assertThat(availability.canCompleteBothTeams(state)).isFalse();
    }

    @Test
    void exactJointBoundaryAcceptsTwoDistinctChampionsForEveryRole() {
        ChampionCatalog catalog = catalog(2);
        DraftAvailability availability = new DraftAvailability(
                catalog, new RoleAssignmentSolver(catalog));

        assertThat(availability.canCompleteBothTeams(fresh())).isTrue();
    }

    @Test
    void poolCountsFlexChampionOnceAndPreservesSparseAndCompleteBoundaries() {
        var catalog=catalog(1);
        var flex=new ChampionDefinition(new ChampionId("flex"),"flex","flex","flex",Position.TOP,
                Set.of(Position.TOP,Position.MID),"https://example.invalid/flex","pool","data");
        var definitions=new ArrayList<>(catalog.all());definitions.add(flex);
        when(catalog.all()).thenReturn(List.copyOf(definitions));when(catalog.get(flex.id())).thenReturn(flex);
        var availability=new DraftAvailability(catalog,new RoleAssignmentSolver(catalog));
        assertThat(Double.doubleToLongBits(availability.poolHealth(fresh(),TeamSide.BLUE,null)))
                .isEqualTo(Double.doubleToLongBits(1 * 1.6 + (7.0 / 5) * 0.35 + 1 * 0.5));
        assertThat(availability.poolHealth(fresh(),TeamSide.BLUE,flex.id())).isEqualTo(1.6 + 0.35);
        var missingJungle=new DraftState(DraftRuleSet.professional(),0,List.of(),List.of(),List.of(),List.of(),Set.of(new ChampionId("jungle0")));
        assertThat(availability.poolHealth(missingJungle,TeamSide.BLUE,null)).isZero();
        var full=new DraftState(DraftRuleSet.professional(),0,definitions.subList(0,5).stream().map(ChampionDefinition::id).toList(),List.of(),List.of(),List.of(),Set.of());
        assertThat(availability.poolHealth(full,TeamSide.BLUE,null)).isEqualTo(20);
    }

    private static DraftState fresh() {
        return new DraftState(DraftRuleSet.professional(), 0,
                List.of(), List.of(), List.of(), List.of(), Set.of());
    }

    private static ChampionCatalog catalog(int championsPerRole) {
        ChampionCatalog catalog = mock(ChampionCatalog.class);
        ArrayList<ChampionDefinition> definitions = new ArrayList<>();
        LinkedHashMap<ChampionId, ChampionDefinition> byId = new LinkedHashMap<>();
        for (Position position : Position.values()) {
            for (int index = 0; index < championsPerRole; index++) {
                ChampionId id = new ChampionId(position.name().toLowerCase() + index);
                ChampionDefinition definition = new ChampionDefinition(
                        id, id.value(), id.value(), id.value(), position,
                        Set.of(position), "https://example.invalid/" + id.value(),
                        "pool", "data");
                definitions.add(definition);
                byId.put(id, definition);
            }
        }
        when(catalog.all()).thenReturn(List.copyOf(definitions));
        for (Map.Entry<ChampionId, ChampionDefinition> entry : byId.entrySet()) {
            when(catalog.get(entry.getKey())).thenReturn(entry.getValue());
        }
        return catalog;
    }
}
