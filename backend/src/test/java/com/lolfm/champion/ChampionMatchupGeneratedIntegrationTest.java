package com.lolfm.champion;

import static org.junit.jupiter.api.Assertions.*;

import com.lolfm.domain.Position;
import com.lolfm.simulator.CombatProgressionEvaluator;
import com.lolfm.simulator.ProgressionApplicationStage;
import com.lolfm.simulator.ProgressionCombatContext;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ChampionMatchupGeneratedIntegrationTest {
    @Test void actualParticipantPairingUnchanged() {
        ChampionMatchupTestFixture fixture = generatedFixture();
        var result = resolver(fixture, List.of(fixture.blue(Position.TOP)),
                List.of(fixture.red(Position.TOP)), ProgressionCombatContext.LANE_COMBAT);
        assertEquals(1, result.eligiblePairCount());
        assertEquals("renekton", result.pairContributions().getFirst()
                .pair().first().value().equals("jax")
                ? result.pairContributions().getFirst().pair().second().value()
                : result.pairContributions().getFirst().pair().first().value());
    }

    @Test void generatedEdgeIsAppliedAdditivelyAndSeparateFromChampionPower() {
        ChampionMatchupTestFixture fixture = generatedFixture();
        List<com.lolfm.simulator.PlayerState> own =
                List.of(fixture.blue(Position.TOP));
        List<com.lolfm.simulator.PlayerState> enemy =
                List.of(fixture.red(Position.TOP));
        var on = new CombatProgressionEvaluator().evaluate(
                fixture.state(), ProgressionCombatContext.LANE_COMBAT, own, enemy);
        double expected = on.commonProgressionContribution()
                + on.championContribution() + on.championMatchupContribution();
        assertEquals(expected, on.finalContribution(), 1e-12);
        assertEquals(on.scoreBeforeMatchup() + on.championMatchupContribution(),
                on.finalContribution(), 1e-12);
    }

    @Test void deadParticipantExcluded() {
        ChampionMatchupTestFixture fixture = generatedFixture();
        fixture.blue(Position.TOP).markDead(0, 100);
        assertEquals(0, resolver(fixture, List.of(fixture.blue(Position.TOP)),
                List.of(fixture.red(Position.TOP)),
                ProgressionCombatContext.LANE_COMBAT).eligiblePairCount());
    }

    @Test void nonParticipantExcluded() {
        ChampionMatchupTestFixture fixture = generatedFixture();
        com.lolfm.simulator.PlayerState outsider = new com.lolfm.simulator.PlayerState(
                "outsider", Position.TOP,
                new com.lolfm.domain.PlayerAttributes(14, 14, 14, 14), 500);
        assertEquals(0, resolver(fixture, List.of(outsider),
                List.of(fixture.red(Position.TOP)),
                ProgressionCombatContext.LANE_COMBAT).eligiblePairCount());
    }

    @Test void crossPositionExcluded() {
        ChampionMatchupTestFixture fixture = generatedFixture();
        assertEquals(0, resolver(fixture, List.of(fixture.blue(Position.TOP)),
                List.of(fixture.red(Position.MID)),
                ProgressionCombatContext.LANE_COMBAT).eligiblePairCount());
    }

    @Test void duplicateApplicationZero() {
        ChampionMatchupTestFixture fixture = generatedFixture();
        var result = resolver(fixture,
                List.of(fixture.blue(Position.TOP), fixture.blue(Position.TOP)),
                List.of(fixture.red(Position.TOP), fixture.red(Position.TOP)),
                ProgressionCombatContext.LANE_COMBAT);
        assertEquals(1, result.eligiblePairCount());
        assertTrue(result.duplicateApplicationCount() > 0);
    }

    @Test void legitimateReevaluationAllowed() {
        ChampionMatchupTestFixture fixture = generatedFixture();
        var first = resolver(fixture, List.of(fixture.blue(Position.TOP)),
                List.of(fixture.red(Position.TOP)),
                ProgressionCombatContext.LANE_COMBAT);
        var second = resolver(fixture, List.of(fixture.blue(Position.TOP)),
                List.of(fixture.red(Position.TOP)),
                ProgressionCombatContext.LANE_COMBAT);
        assertEquals(first.matchupEdge(), second.matchupEdge(), 0.0);
    }

    @Test void evaluationConsumesNoCombatSlotAndDoesNotMutateGameplay() {
        ChampionMatchupTestFixture fixture = generatedFixture();
        int time = fixture.state().getCurrentTimeSeconds();
        assertFalse(fixture.state().wasMajorCombatAttemptedThisTick());
        resolver(fixture, List.of(fixture.blue(Position.TOP)),
                List.of(fixture.red(Position.TOP)),
                ProgressionCombatContext.LANE_COMBAT);
        assertEquals(time, fixture.state().getCurrentTimeSeconds());
        assertFalse(fixture.state().wasMajorCombatAttemptedThisTick());
        assertEquals(0, fixture.state().getChampionMatchupExecutionStats()
                .snapshot().directRandomCalls());
    }

    @Test void generatedStatsAreMatchScopedAndNextMatchStartsFresh() {
        ChampionMatchupTestFixture first = generatedFixture();
        resolver(first, List.of(first.blue(Position.TOP)),
                List.of(first.red(Position.TOP)),
                ProgressionCombatContext.LANE_COMBAT);
        ChampionMatchupTestFixture second = generatedFixture();
        assertTrue(first.state().getChampionMatchupExecutionStats()
                .snapshot().evaluations() > 0);
        assertEquals(0, second.state().getChampionMatchupExecutionStats()
                .snapshot().evaluations());
    }

    @Test void runtimeWorkIsBoundedByActualEligiblePositions() {
        ChampionMatchupTestFixture fixture = generatedFixture();
        List<com.lolfm.simulator.PlayerState> blue = new ArrayList<>();
        List<com.lolfm.simulator.PlayerState> red = new ArrayList<>();
        for (Position position : Position.values()) {
            blue.add(fixture.blue(position));
            red.add(fixture.red(position));
        }
        var result = resolver(fixture, blue, red,
                ProgressionCombatContext.GENERIC_SKIRMISH);
        assertEquals(Position.values().length, result.eligiblePairCount());
        assertTrue(result.eligiblePairCount()
                < fixture.state().getChampionMatchupCatalog().orElseThrow()
                .profiles().size());
    }

    private ChampionMatchupTestFixture generatedFixture() {
        ChampionMatchupTestFixture fixture =
                new ChampionMatchupTestFixture(ChampionMatchupMode.ON, false);
        fixture.state().configureChampionMatchup(
                GeneratedChampionMatchupCatalogFactory.prototype(
                        HistoricalChampionCatalog.initialThirty())
                        .catalog(), ChampionMatchupMode.ON);
        return fixture;
    }

    private static ChampionMatchupResult resolver(
            ChampionMatchupTestFixture fixture,
            List<com.lolfm.simulator.PlayerState> source,
            List<com.lolfm.simulator.PlayerState> opponent,
            ProgressionCombatContext context
    ) {
        return new ChampionMatchupResolver().evaluate(
                fixture.state(), source, opponent, context,
                ProgressionApplicationStage.COMBAT_SCORE);
    }
}
