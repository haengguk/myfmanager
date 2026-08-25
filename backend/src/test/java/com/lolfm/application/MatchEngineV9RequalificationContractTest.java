package com.lolfm.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.lolfm.simulator.SimulationRuntimeProfileId;
import java.util.HashSet;
import org.junit.jupiter.api.Test;

class MatchEngineV9RequalificationContractTest {
    @Test
    void freezesOneHundredFixturesTwelveFreshSeedsAndThreeProfiles() {
        var schedule = MatchEngineV9RequalificationContract.requireFrozen(
                MatchEngineV9RequalificationContract.schedule());

        assertThat(schedule.fixtures()).hasSize(100);
        assertThat(schedule.fixtures().stream().filter(value -> value.seriesGameNumber() == 1))
                .hasSize(90);
        assertThat(schedule.fixtures().stream().filter(value -> value.seriesGameNumber() == 2))
                .hasSize(10);
        assertThat(schedule.fixtures()).allSatisfy(value -> {
            assertThat(value.calibrationSeeds()).hasSize(8);
            assertThat(value.holdoutSeeds()).hasSize(4);
        });
        assertThat(MatchEngineV9RequalificationContract.PROFILES).containsExactly(
                SimulationRuntimeProfileId.BASELINE_V1,
                SimulationRuntimeProfileId.MATCHUP_ONLY_CANDIDATE_V1,
                SimulationRuntimeProfileId.FULL_SYSTEM_CANDIDATE_V1);
    }

    @Test
    void freshNamespaceHasNoCollisionOrHistoricalB2B3Overlap() {
        var audit = MatchEngineV9RequalificationContract.requireNoSeedOverlap(
                MatchEngineV9RequalificationContract.schedule());
        assertThat(audit.clean()).isTrue();
        assertThat(audit.historicalOverlapCount()).isZero();
        assertThat(audit.freshCollisionCount()).isZero();
        assertThat(audit.freshSeedCount()).isEqualTo(1_200);
    }

    @Test
    void everyProfilePairUsesTheSameFixtureIdentityAndSeed() {
        var schedule = MatchEngineV9RequalificationContract.schedule();
        var jobKeys = new HashSet<String>();
        schedule.fixtures().forEach(fixture -> {
            var seeds = new java.util.ArrayList<Long>();
            seeds.addAll(fixture.calibrationSeeds());
            seeds.addAll(fixture.holdoutSeeds());
            seeds.forEach(seed -> MatchEngineV9RequalificationContract.PROFILES.forEach(profile ->
                    assertThat(jobKeys.add(fixture.fixtureId() + "|" + seed + "|" + profile))
                            .isTrue()));
        });
        assertThat(jobKeys).hasSize(3_600);
    }
}
