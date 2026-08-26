package com.lolfm.champion;

import static org.assertj.core.api.Assertions.assertThat;

import com.lolfm.domain.Position;
import com.lolfm.simulator.Lane;
import com.lolfm.simulator.ProgressionApplicationStage;
import com.lolfm.simulator.ProgressionCombatContext;
import java.util.List;
import org.junit.jupiter.api.Test;

class ChampionMatchupConsumedApplicationIdentityTest {
    private final ChampionMatchupResolver resolver = new ChampionMatchupResolver();

    @Test
    void exactDuplicateIsIdempotentButConflictingPayloadIsRejected() {
        ChampionMatchupTestFixture fixture = fixture();
        ChampionMatchupResult result = result(fixture);
        ChampionMatchupExecutionStats stats = fixture.state()
                .getChampionMatchupExecutionStats();

        recordCombat(stats, fixture, result, "COMBAT_AT:300", 1.0, 1.25);
        recordCombat(stats, fixture, result, "COMBAT_AT:300", 1.0, 1.25);
        var exact = stats.snapshot();
        assertThat(exact.consumedApplicationCount()).isOne();
        assertThat(exact.applicationProvenance()).hasSize(1);
        assertThat(exact.idempotentDuplicateConsumedApplicationCount()).isOne();
        assertThat(exact.duplicateConsumedApplicationErrors()).isZero();

        recordCombat(stats, fixture, result, "COMBAT_AT:300", 1.0, 1.30);
        var conflict = stats.snapshot();
        assertThat(conflict.consumedApplicationCount()).isOne();
        assertThat(conflict.applicationProvenance()).hasSize(1);
        assertThat(conflict.duplicateConsumedApplicationErrors()).isOne();
    }

    @Test
    void sameTickDistinctActionsAndPressureMutationsAreDistinctSlots() {
        ChampionMatchupTestFixture fixture = fixture();
        ChampionMatchupResult result = result(fixture);
        ChampionMatchupExecutionStats stats = fixture.state()
                .getChampionMatchupExecutionStats();
        recordCombat(stats, fixture, result, "ACTION:ONE", 1.0, 1.25);
        recordCombat(stats, fixture, result, "ACTION:TWO", 1.0, 1.25);
        ChampionMatchupStateMutationLineage first = new ChampionMatchupStateMutationLineage(
                "LANE_PRESSURE:0:TOP:1", 1, 0, Lane.TOP, 0.0, 0.5, 0.5, 0.0);
        ChampionMatchupStateMutationLineage second = new ChampionMatchupStateMutationLineage(
                "LANE_PRESSURE:0:TOP:2", 2, 0, Lane.TOP, 0.5, 1.0, 0.5, 0.0);
        recordPressure(stats, fixture, result, first);
        recordPressure(stats, fixture, result, second);

        var snapshot = stats.snapshot();
        assertThat(snapshot.applicationProvenance()).hasSize(4);
        assertThat(snapshot.duplicateConsumedApplicationErrors()).isZero();
        assertThat(snapshot.applicationBindingErrors()).isZero();
    }

    @Test
    void newAndConcurrentMatchesDoNotShareSlots() {
        ChampionMatchupTestFixture first = fixture();
        ChampionMatchupTestFixture second = fixture();
        recordCombat(first.state().getChampionMatchupExecutionStats(), first, result(first),
                "COMBAT_AT:0", 0.0, 0.25);
        assertThat(first.state().getChampionMatchupExecutionStats().snapshot()
                .consumedApplicationCount()).isOne();
        assertThat(second.state().getChampionMatchupExecutionStats().snapshot()
                .consumedApplicationCount()).isZero();
        recordCombat(second.state().getChampionMatchupExecutionStats(), second, result(second),
                "COMBAT_AT:0", 0.0, 0.25);
        assertThat(second.state().getChampionMatchupExecutionStats().snapshot()
                .duplicateConsumedApplicationErrors()).isZero();
    }

    private static void recordCombat(
            ChampionMatchupExecutionStats stats,
            ChampionMatchupTestFixture fixture,
            ChampionMatchupResult result,
            String actionId,
            double before,
            double after
    ) {
        stats.recordConsumedApplication(fixture.state(), result,
                ProgressionCombatContext.LANE_COMBAT,
                ProgressionApplicationStage.COMBAT_SCORE,
                ChampionMatchupApplicationPoint.COMBAT_PROGRESSION_SCORE,
                ChampionMatchupLaneScope.TOP, before, after, actionId);
    }

    private static void recordPressure(
            ChampionMatchupExecutionStats stats,
            ChampionMatchupTestFixture fixture,
            ChampionMatchupResult result,
            ChampionMatchupStateMutationLineage lineage
    ) {
        stats.recordConsumedApplication(fixture.state(), result,
                ProgressionCombatContext.LANE_COMBAT,
                ProgressionApplicationStage.INITIATIVE,
                ChampionMatchupApplicationPoint.LANE_OPPORTUNITY_PRESSURE,
                ChampionMatchupLaneScope.TOP, lineage.pressureAfter()
                        - lineage.matchupPressureDelta(), lineage.pressureAfter(),
                null, lineage);
    }

    private ChampionMatchupTestFixture fixture() {
        return new ChampionMatchupTestFixture(ChampionMatchupMode.ON, true);
    }

    private ChampionMatchupResult result(ChampionMatchupTestFixture fixture) {
        return resolver.evaluate(fixture.state(), List.of(fixture.blue(Position.TOP)),
                List.of(fixture.red(Position.TOP)), ProgressionCombatContext.LANE_COMBAT,
                ProgressionApplicationStage.COMBAT_SCORE);
    }
}
