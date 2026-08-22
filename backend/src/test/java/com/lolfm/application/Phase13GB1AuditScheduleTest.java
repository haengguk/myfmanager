package com.lolfm.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.lolfm.application.Phase13GB1AuditSchedule.Fixture;
import com.lolfm.application.Phase13GB1AuditSchedule.FixtureLane;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class Phase13GB1AuditScheduleTest {
    @Test
    void freezesEveryRealLckGameOnePairInBothOrientations() {
        var schedule = Phase13GB1AuditSchedule.create();

        assertThat(schedule.teamCodes()).containsExactlyElementsOf(
                Phase13GB1AuditSchedule.TEAM_CODES);
        assertThat(schedule.primaryFixtures()).hasSize(90);
        assertThat(schedule.primaryFixtures()).allSatisfy(fixture -> {
            assertThat(fixture.fixtureLane()).isEqualTo(FixtureLane.PRIMARY_LEAGUE_G1);
            assertThat(fixture.seriesGameNumber()).isOne();
            assertSeedContract(fixture);
        });
        assertThat(schedule.primaryFixtures().stream().map(Fixture::pairId).distinct())
                .hasSize(45);

        Map<String, Long> appearances = schedule.primaryFixtures().stream()
                .flatMap(fixture -> java.util.stream.Stream.of(
                        fixture.blueTeamCode(), fixture.redTeamCode()))
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));
        assertThat(appearances).containsOnlyKeys(schedule.teamCodes().toArray(String[]::new));
        assertThat(appearances.values()).containsOnly(18L);

        schedule.primaryFixtures().stream().collect(Collectors.groupingBy(Fixture::pairId))
                .values().forEach(orientations -> {
                    assertThat(orientations).hasSize(2);
                    Fixture first = orientations.get(0);
                    Fixture second = orientations.get(1);
                    assertThat(first.blueTeamCode()).isEqualTo(second.redTeamCode());
                    assertThat(first.redTeamCode()).isEqualTo(second.blueTeamCode());
                });
    }

    @Test
    void freezesDisjointAllTeamHardFearlessGameTwoSensitivityPairs() {
        var fixtures = Phase13GB1AuditSchedule.create().secondaryHardFearlessFixtures();

        assertThat(fixtures).hasSize(10);
        assertThat(fixtures.stream().map(Fixture::pairId).distinct()).hasSize(5);
        assertThat(fixtures).allSatisfy(fixture -> {
            assertThat(fixture.fixtureLane())
                    .isEqualTo(FixtureLane.SECONDARY_HARD_FEARLESS_G2);
            assertThat(fixture.seriesGameNumber()).isEqualTo(2);
            assertSeedContract(fixture);
        });
        Map<String, Long> appearances = fixtures.stream()
                .flatMap(fixture -> java.util.stream.Stream.of(
                        fixture.blueTeamCode(), fixture.redTeamCode()))
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));
        assertThat(appearances).containsOnlyKeys(
                Phase13GB1AuditSchedule.TEAM_CODES.toArray(String[]::new));
        assertThat(appearances.values()).containsOnly(2L);
        fixtures.stream().collect(Collectors.groupingBy(Fixture::pairId))
                .values().forEach(orientations -> {
                    assertThat(orientations).hasSize(2);
                    Fixture first = orientations.get(0);
                    Fixture second = orientations.get(1);
                    assertThat(first.blueTeamCode()).isEqualTo(second.redTeamCode());
                    assertThat(first.redTeamCode()).isEqualTo(second.blueTeamCode());
                });
    }

    @Test
    void scheduleAndDryRunSeedAreDeterministicAndSeparatedFromReservedSamples() {
        var first = Phase13GB1AuditSchedule.create();
        var second = Phase13GB1AuditSchedule.create();

        assertThat(second).isEqualTo(first);
        assertThat(first.scheduleHash()).matches("[0-9a-f]{64}");
        assertThat(first.scheduleHash())
                .isEqualTo(Phase13GB1AuditSchedule.EXPECTED_SCHEDULE_HASH)
                .isEqualTo(second.scheduleHash());
        assertThat(first.allFixtures()).allSatisfy(fixture -> {
            long dryRun = Phase13GB1AuditSchedule.dryRunSeed(fixture);
            assertThat(fixture.calibrationSeeds()).doesNotContain(dryRun);
            assertThat(fixture.holdoutSeeds()).doesNotContain(dryRun);
        });
    }

    private static void assertSeedContract(Fixture fixture) {
        assertThat(fixture.calibrationSeeds())
                .hasSize(Phase13GB1AuditSchedule.CALIBRATION_SEEDS_PER_FIXTURE);
        assertThat(fixture.holdoutSeeds())
                .hasSize(Phase13GB1AuditSchedule.HOLDOUT_SEEDS_PER_FIXTURE);
        Set<Long> all = new HashSet<>(fixture.calibrationSeeds());
        all.addAll(fixture.holdoutSeeds());
        assertThat(all).hasSize(Phase13GB1AuditSchedule.CALIBRATION_SEEDS_PER_FIXTURE
                + Phase13GB1AuditSchedule.HOLDOUT_SEEDS_PER_FIXTURE);
    }
}
