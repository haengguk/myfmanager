package com.lolfm.champion;

import static org.assertj.core.api.Assertions.assertThat;

import com.lolfm.domain.Position;
import com.lolfm.simulator.CombatProgressionBreakdown;
import com.lolfm.simulator.CombatProgressionEvaluator;
import com.lolfm.simulator.ProgressionApplicationStage;
import com.lolfm.simulator.ProgressionCombatContext;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class ChampionMatchupIntegrationTest {
    @Test void featureOffPreservesPhase13BScore() {
        ChampionMatchupTestFixture fixture =
                new ChampionMatchupTestFixture(ChampionMatchupMode.OFF, true);
        CombatProgressionBreakdown score = score(fixture);
        assertThat(score.championMatchupContribution()).isZero();
        assertThat(score.finalContribution()).isEqualTo(score.scoreBeforeMatchup());
        assertThat(fixture.state.getChampionMatchupExecutionStats().snapshot()
                .totalPairApplications()).isZero();
    }

    @Test void featureOnNeutralPreservesPhase13BScore() {
        ChampionMatchupTestFixture fixture =
                new ChampionMatchupTestFixture(ChampionMatchupMode.ON, false);
        CombatProgressionBreakdown score = score(fixture);
        assertThat(score.championMatchupContribution()).isZero();
        assertThat(score.finalContribution()).isEqualTo(score.scoreBeforeMatchup());
    }

    @Test void featureOffIgnoresTestCatalog() {
        ChampionMatchupTestFixture fixture =
                new ChampionMatchupTestFixture(ChampionMatchupMode.OFF, true);
        assertThat(score(fixture).championMatchupContribution()).isZero();
    }

    @Test void matchupContributionIsAdditive() {
        ChampionMatchupTestFixture fixture =
                new ChampionMatchupTestFixture(ChampionMatchupMode.ON, true);
        CombatProgressionBreakdown score = score(fixture);
        assertThat(score.championMatchupContribution()).isEqualTo(.25);
        assertThat(score.finalContribution())
                .isEqualTo(score.scoreBeforeMatchup() + .25);
    }

    @Test void matchupContributionIsNotMultipliedByPlayerSkill() {
        ChampionMatchupTestFixture fixture =
                new ChampionMatchupTestFixture(ChampionMatchupMode.ON, true);
        double edge = score(fixture).championMatchupContribution();
        ChampionPowerTestFixture.grow(fixture.blue(Position.TOP), 10_000, 20_000);
        assertThat(score(fixture).championMatchupContribution()).isEqualTo(edge);
    }

    @Test void matchupContributionIsNotMultipliedByChampionPower() {
        ChampionMatchupTestFixture fixture =
                new ChampionMatchupTestFixture(ChampionMatchupMode.ON, true);
        CombatProgressionBreakdown before = score(fixture);
        ChampionPowerTestFixture.grow(fixture.blue(Position.TOP), 10_000, 20_000);
        CombatProgressionBreakdown after = score(fixture);
        assertThat(after.championContribution()).isNotEqualTo(before.championContribution());
        assertThat(after.championMatchupContribution())
                .isEqualTo(before.championMatchupContribution());
    }

    @Test void matchupEvaluationDoesNotConsumeCombatSlot() {
        ChampionMatchupTestFixture fixture =
                new ChampionMatchupTestFixture(ChampionMatchupMode.ON, true);
        score(fixture);
        assertThat(fixture.state.wasMajorCombatAttemptedThisTick()).isFalse();
    }

    @Test void matchupEvaluationDoesNotMutateGameState() {
        ChampionMatchupTestFixture fixture =
                new ChampionMatchupTestFixture(ChampionMatchupMode.ON, true);
        var blue = fixture.blue(Position.TOP);
        int gold = blue.getGold();
        int kills = blue.getKills();
        int deaths = blue.getDeaths();
        score(fixture);
        assertThat(blue.getGold()).isEqualTo(gold);
        assertThat(blue.getKills()).isEqualTo(kills);
        assertThat(blue.getDeaths()).isEqualTo(deaths);
    }

    @Test void matchupEvaluationConsumesNoRandom() {
        ChampionMatchupTestFixture fixture =
                new ChampionMatchupTestFixture(ChampionMatchupMode.ON, true);
        score(fixture);
        assertThat(fixture.state.getChampionMatchupExecutionStats().snapshot()
                .directRandomCalls()).isZero();
        assertThat(Arrays.stream(ChampionMatchupResolver.class.getDeclaredMethods())
                .flatMap(method -> Arrays.stream(method.getParameterTypes()))
                .noneMatch(java.util.Random.class::equals)).isTrue();
    }

    @Test void matchupStatsAreMatchScoped() {
        ChampionMatchupTestFixture first =
                new ChampionMatchupTestFixture(ChampionMatchupMode.ON, true);
        ChampionMatchupTestFixture second =
                new ChampionMatchupTestFixture(ChampionMatchupMode.ON, true);
        score(first);
        assertThat(first.state.getChampionMatchupExecutionStats().snapshot().evaluations())
                .isOne();
        assertThat(second.state.getChampionMatchupExecutionStats().snapshot().evaluations())
                .isZero();
    }

    @Test void nextMatchStartsWithFreshMatchupStats() {
        ChampionMatchupTestFixture first =
                new ChampionMatchupTestFixture(ChampionMatchupMode.ON, true);
        score(first);
        ChampionMatchupTestFixture next =
                new ChampionMatchupTestFixture(ChampionMatchupMode.ON, true);
        assertThat(next.state.getChampionMatchupExecutionStats().snapshot())
                .isEqualTo(ChampionMatchupExecutionStatsSnapshot.empty());
    }

    @Test void sameSeedProducesSameMatchupBreakdown() {
        ChampionMatchupTestFixture first =
                new ChampionMatchupTestFixture(ChampionMatchupMode.ON, true);
        ChampionMatchupTestFixture second =
                new ChampionMatchupTestFixture(ChampionMatchupMode.ON, true);
        assertThat(score(first)).isEqualTo(score(second));
    }

    @Test void exactZeroMatchupDoesNotAlterExistingTieResolution() {
        ChampionMatchupTestFixture fixture =
                new ChampionMatchupTestFixture(ChampionMatchupMode.ON, false);
        CombatProgressionBreakdown result = score(fixture);
        assertThat(result.scoreBeforeMatchup()).isEqualTo(result.finalContribution());
        assertThat(Double.doubleToRawLongBits(result.championMatchupContribution()))
                .isEqualTo(Double.doubleToRawLongBits(0.0));
    }

    @Test void testCatalogCannotReplaceProductionCatalogThroughPublicApi() {
        assertThat(Arrays.stream(ChampionMatchupCatalog.class.getDeclaredMethods())
                .filter(method -> method.getName().equals("testCatalog"))
                .allMatch(method -> !Modifier.isPublic(method.getModifiers()))).isTrue();
    }

    @Test void noDisplayNameOrMessageParsing() {
        assertThat(Arrays.stream(ChampionMatchupPair.class.getRecordComponents())
                .map(component -> component.getType().getName()).toList())
                .containsExactly(ChampionId.class.getName(), ChampionId.class.getName(),
                        Position.class.getName());
        assertThat(Arrays.stream(ChampionMatchupResolver.class.getDeclaredFields())
                .noneMatch(field -> field.getType().equals(String.class))).isTrue();
    }

    @Test void dynamicBreakdownKeepsChampionPowerSeparateFromMatchup() {
        ChampionMatchupTestFixture fixture =
                new ChampionMatchupTestFixture(ChampionMatchupMode.ON, true);
        DynamicCombatScoreEvaluator evaluator = new DynamicCombatScoreEvaluator(
                new ChampionPowerProfileCatalog(
                        new com.fasterxml.jackson.databind.ObjectMapper(), fixture.champions));
        DynamicCombatScoreBreakdown value = evaluator.evaluate(
                fixture.blue(Position.TOP), new ChampionId("renekton"),
                ProgressionCombatContext.LANE_COMBAT, .25);
        assertThat(value.championMatchupContribution()).isEqualTo(.25);
        assertThat(value.scoreAfterMatchup()).isEqualTo(value.scoreBeforeMatchup() + .25);
        assertThat(value.championPowerContribution())
                .isEqualTo(value.championLevelContribution()
                        + value.championItemContribution()
                        + value.championContextContribution());
    }

    private CombatProgressionBreakdown score(ChampionMatchupTestFixture fixture) {
        return new CombatProgressionEvaluator().evaluate(
                fixture.state,
                ProgressionCombatContext.LANE_COMBAT,
                List.of(fixture.blue(Position.TOP)),
                List.of(fixture.red(Position.TOP)),
                0.0,
                0.0,
                ProgressionApplicationStage.COMBAT_SCORE);
    }
}
