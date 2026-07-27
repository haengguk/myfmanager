package com.lolfm.champion;

import static org.assertj.core.api.Assertions.assertThat;

import com.lolfm.domain.PlayerAttributes;
import com.lolfm.domain.Position;
import com.lolfm.simulator.GameState;
import com.lolfm.simulator.PlayerState;
import com.lolfm.simulator.ProgressionApplicationStage;
import com.lolfm.simulator.ProgressionCombatContext;
import java.util.List;
import org.junit.jupiter.api.Test;

class ChampionMatchupResolverTest {
    private final ChampionMatchupResolver resolver = new ChampionMatchupResolver();

    @Test void matchupFeatureDefaultsToOff() {
        ChampionMatchupTestFixture fixture =
                new ChampionMatchupTestFixture(ChampionMatchupMode.OFF, false);
        assertThat(fixture.state.getChampionMatchupMode()).isEqualTo(ChampionMatchupMode.OFF);
    }

    @Test void featureOffRecordsZeroApplications() {
        ChampionMatchupTestFixture fixture =
                new ChampionMatchupTestFixture(ChampionMatchupMode.OFF, true);
        ChampionMatchupResult result = evaluate(
                fixture, Position.TOP, ProgressionCombatContext.LANE_COMBAT);
        var stats = fixture.state.getChampionMatchupExecutionStats().snapshot();
        assertThat(result.enabled()).isFalse();
        assertThat(stats.disabledEvaluations()).isOne();
        assertThat(stats.totalPairApplications()).isZero();
    }

    @Test void featureOnNeutralRecordsOnlyZeroContribution() {
        ChampionMatchupTestFixture fixture =
                new ChampionMatchupTestFixture(ChampionMatchupMode.ON, false);
        ChampionMatchupResult result = evaluate(
                fixture, Position.TOP, ProgressionCombatContext.LANE_COMBAT);
        var stats = fixture.state.getChampionMatchupExecutionStats().snapshot();
        assertThat(result.matchupEdge()).isZero();
        assertThat(stats.zeroContributionApplications()).isOne();
        assertThat(stats.nonZeroContributionApplications()).isZero();
    }

    @Test void testProfileAppliesPositiveForwardEdge() {
        ChampionMatchupTestFixture fixture =
                new ChampionMatchupTestFixture(ChampionMatchupMode.ON, true);
        assertThat(evaluate(fixture, Position.TOP, ProgressionCombatContext.LANE_COMBAT)
                .matchupEdge()).isEqualTo(.25);
    }

    @Test void testProfileAppliesNegativeReverseEdge() {
        ChampionMatchupTestFixture fixture =
                new ChampionMatchupTestFixture(ChampionMatchupMode.ON, true);
        ChampionMatchupResult reverse = resolver.evaluate(
                fixture.state, List.of(fixture.red(Position.TOP)),
                List.of(fixture.blue(Position.TOP)), ProgressionCombatContext.LANE_COMBAT,
                ProgressionApplicationStage.COMBAT_SCORE);
        assertThat(reverse.matchupEdge()).isEqualTo(-.25);
    }

    @Test void wrongContextProducesZeroContribution() {
        ChampionMatchupTestFixture fixture =
                new ChampionMatchupTestFixture(ChampionMatchupMode.ON, true);
        assertThat(evaluate(fixture, Position.TOP, ProgressionCombatContext.TEAMFIGHT)
                .matchupEdge()).isZero();
    }

    @Test void deadParticipantIsExcluded() {
        ChampionMatchupTestFixture fixture =
                new ChampionMatchupTestFixture(ChampionMatchupMode.ON, true);
        fixture.blue(Position.TOP).markDead(0, 60);
        ChampionMatchupResult result = evaluate(
                fixture, Position.TOP, ProgressionCombatContext.LANE_COMBAT);
        assertThat(result.eligiblePairCount()).isZero();
        assertThat(result.deadParticipantSkipped()).isOne();
    }

    @Test void nonParticipantIsExcluded() {
        ChampionMatchupTestFixture fixture =
                new ChampionMatchupTestFixture(ChampionMatchupMode.ON, true);
        PlayerState outside = new PlayerState(
                "outside", Position.TOP, new PlayerAttributes(20, 20, 20, 20), 500);
        ChampionMatchupResult result = resolver.evaluate(
                fixture.state, List.of(outside), List.of(fixture.red(Position.TOP)),
                ProgressionCombatContext.LANE_COMBAT,
                ProgressionApplicationStage.COMBAT_SCORE);
        assertThat(result.eligiblePairCount()).isZero();
        assertThat(result.nonParticipantSkipped()).isOne();
    }

    @Test void sameTeamPlayersAreNeverPaired() {
        ChampionMatchupTestFixture fixture =
                new ChampionMatchupTestFixture(ChampionMatchupMode.ON, true);
        ChampionMatchupResult result = resolver.evaluate(
                fixture.state, List.of(fixture.blue(Position.TOP)),
                List.of(fixture.blue(Position.TOP)), ProgressionCombatContext.LANE_COMBAT,
                ProgressionApplicationStage.COMBAT_SCORE);
        assertThat(result.eligiblePairCount()).isZero();
        assertThat(result.sameTeamSkipped()).isOne();
    }

    @Test void samePositionParticipantsArePaired() {
        ChampionMatchupTestFixture fixture =
                new ChampionMatchupTestFixture(ChampionMatchupMode.ON, true);
        assertThat(evaluate(fixture, Position.MID, ProgressionCombatContext.ROAM)
                .eligiblePairCount()).isOne();
    }

    @Test void crossPositionParticipantsAreNotPaired() {
        ChampionMatchupTestFixture fixture =
                new ChampionMatchupTestFixture(ChampionMatchupMode.ON, true);
        ChampionMatchupResult result = resolver.evaluate(
                fixture.state, List.of(fixture.blue(Position.TOP)),
                List.of(fixture.red(Position.MID)), ProgressionCombatContext.LANE_COMBAT,
                ProgressionApplicationStage.COMBAT_SCORE);
        assertThat(result.eligiblePairCount()).isZero();
        assertThat(result.crossPositionSkipped()).isEqualTo(2);
    }

    @Test void missingChampionAssignmentIsRecorded() {
        ChampionMatchupTestFixture fixture =
                new ChampionMatchupTestFixture(ChampionMatchupMode.ON, true);
        GameState state = new GameState(fixture.blue, fixture.red);
        state.configureChampionMatchup(
                ChampionMatchupTestCatalogFactory.focused(fixture.champions),
                ChampionMatchupMode.ON);
        ChampionMatchupResult result = resolver.evaluate(
                state, List.of(fixture.blue(Position.TOP)), List.of(fixture.red(Position.TOP)),
                ProgressionCombatContext.LANE_COMBAT,
                ProgressionApplicationStage.COMBAT_SCORE);
        assertThat(result.missingAssignmentCount()).isEqualTo(2);
        assertThat(result.eligiblePairCount()).isZero();
    }

    @Test void noEligiblePairReturnsZero() {
        ChampionMatchupTestFixture fixture =
                new ChampionMatchupTestFixture(ChampionMatchupMode.ON, true);
        ChampionMatchupResult result = resolver.evaluate(
                fixture.state, List.of(), List.of(), ProgressionCombatContext.TEAMFIGHT,
                ProgressionApplicationStage.COMBAT_SCORE);
        assertThat(result.eligiblePairCount()).isZero();
        assertThat(result.matchupEdge()).isZero();
    }

    @Test void multipleEligiblePairsUseAverageNotSum() {
        ChampionMatchupTestFixture fixture =
                new ChampionMatchupTestFixture(ChampionMatchupMode.ON, true);
        configureTwoLaneEdges(fixture);
        ChampionMatchupResult result = resolver.evaluate(
                fixture.state,
                List.of(fixture.blue(Position.TOP), fixture.blue(Position.MID)),
                List.of(fixture.red(Position.TOP), fixture.red(Position.MID)),
                ProgressionCombatContext.LANE_COMBAT,
                ProgressionApplicationStage.COMBAT_SCORE);
        assertThat(result.eligiblePairCount()).isEqualTo(2);
        assertThat(result.totalBeforeAverage()).isEqualTo(.5);
        assertThat(result.matchupEdge()).isEqualTo(.25);
    }

    @Test void onePositionIsAppliedAtMostOnce() {
        ChampionMatchupTestFixture fixture =
                new ChampionMatchupTestFixture(ChampionMatchupMode.ON, true);
        PlayerState top = fixture.blue(Position.TOP);
        ChampionMatchupResult result = resolver.evaluate(
                fixture.state, List.of(top, top), List.of(fixture.red(Position.TOP)),
                ProgressionCombatContext.LANE_COMBAT,
                ProgressionApplicationStage.COMBAT_SCORE);
        assertThat(result.eligiblePairCount()).isOne();
        assertThat(result.duplicateApplicationCount()).isOne();
    }

    @Test void legitimateReevaluationIsAllowed() {
        ChampionMatchupTestFixture fixture =
                new ChampionMatchupTestFixture(ChampionMatchupMode.ON, true);
        evaluate(fixture, Position.TOP, ProgressionCombatContext.LANE_COMBAT);
        evaluate(fixture, Position.TOP, ProgressionCombatContext.LANE_COMBAT);
        var stats = fixture.state.getChampionMatchupExecutionStats().snapshot();
        assertThat(stats.totalPairApplications()).isEqualTo(2);
        assertThat(stats.duplicateApplicationErrors()).isZero();
    }

    @Test void mirrorReversesDirectionalEdge() {
        ChampionMatchupTestFixture fixture =
                new ChampionMatchupTestFixture(ChampionMatchupMode.ON, true);
        double original = evaluate(
                fixture, Position.SUPPORT, ProgressionCombatContext.OBJECTIVE_FIGHT)
                .matchupEdge();
        double mirrored = resolver.evaluate(
                fixture.state, List.of(fixture.red(Position.SUPPORT)),
                List.of(fixture.blue(Position.SUPPORT)),
                ProgressionCombatContext.OBJECTIVE_FIGHT,
                ProgressionApplicationStage.COMBAT_SCORE).matchupEdge();
        assertThat(mirrored).isEqualTo(-original);
    }

    private ChampionMatchupResult evaluate(
            ChampionMatchupTestFixture fixture,
            Position position,
            ProgressionCombatContext context
    ) {
        return resolver.evaluate(
                fixture.state, List.of(fixture.blue(position)), List.of(fixture.red(position)),
                context, ProgressionApplicationStage.COMBAT_SCORE);
    }

    private void configureTwoLaneEdges(ChampionMatchupTestFixture fixture) {
        List<ChampionMatchupProfile> values = List.of(
                profile(fixture, "renekton", "jax"),
                profile(fixture, "leblanc", "viktor"));
        fixture.state.configureChampionMatchup(
                ChampionMatchupCatalog.testCatalog(fixture.champions, values),
                ChampionMatchupMode.ON);
    }

    private ChampionMatchupProfile profile(
            ChampionMatchupTestFixture fixture,
            String first,
            String second
    ) {
        ChampionDefinition left = fixture.champions.get(new ChampionId(first));
        ChampionDefinition right = fixture.champions.get(new ChampionId(second));
        ChampionMatchupPair pair = ChampionMatchupPair.of(left, right);
        double edge = pair.first().equals(left.id()) ? .25 : -.25;
        return new ChampionMatchupProfile(
                pair, java.util.Map.of(ProgressionCombatContext.LANE_COMBAT, edge));
    }
}
