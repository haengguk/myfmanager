package com.lolfm.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.lolfm.composition.TeamCompositionGameplayMode;
import com.lolfm.simulator.SimulationRuntimeProfileId;
import com.lolfm.simulator.SimulationRuntimeProfiles;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import org.junit.jupiter.api.Test;

class CompositionV9ApplicationCausalityContractTest {
    @Test
    void scheduleFreezesExact100By4CoverageAndTwoCompositionMarginalProfiles() {
        var schedule = CompositionV9ApplicationCausalityContract.requireFrozen(
                CompositionV9ApplicationCausalityContract.schedule());
        assertThat(schedule.fixtures()).hasSize(100);
        assertThat(schedule.fixtures()).allSatisfy(value -> assertThat(value.seeds()).hasSize(4));
        assertThat(schedule.fixtures().stream().flatMap(value -> value.seeds().stream()).toList())
                .hasSize(400).doesNotHaveDuplicates();
        assertThat(CompositionV9ApplicationCausalityContract.PROFILES).containsExactly(
                SimulationRuntimeProfileId.MATCHUP_ONLY_CANDIDATE_V1,
                SimulationRuntimeProfileId.FULL_SYSTEM_CANDIDATE_V1);
        assertThat(schedule.consumptionStatus()).isEqualTo("CONSUMED_AS_DIAGNOSTIC_NOT_HOLDOUT");
    }

    @Test
    void freshNamespaceHasExactZeroOverlapWithAllConsumedEvidence() {
        var audit = CompositionV9ApplicationCausalityContract.requireNoSeedOverlap(
                CompositionV9ApplicationCausalityContract.schedule());
        assertThat(audit.clean()).isTrue();
        assertThat(audit.freshSeedCount()).isEqualTo(400);
        assertThat(audit.duplicateCount()).isZero();
        assertThat(audit.phase13OverlapCount()).isZero();
        assertThat(audit.v9CalibrationOverlapCount()).isZero();
        assertThat(audit.v9HoldoutOverlapCount()).isZero();
        assertThat(audit.matchupAttributionOverlapCount()).isZero();
        assertThat(audit.failedWorkerIsolationV1SeedCount()).isEqualTo(400);
        assertThat(audit.failedWorkerIsolationV1OverlapCount()).isZero();
        assertThat(audit.blockedProvenanceGapV2SeedCount()).isEqualTo(400);
        assertThat(audit.blockedProvenanceGapV2OverlapCount()).isZero();
        assertThat(audit.blockedProvenanceGapV3SeedCount()).isEqualTo(400);
        assertThat(audit.blockedProvenanceGapV3OverlapCount()).isZero();
        assertThat(audit.blockedProvenanceGapV4SeedCount()).isEqualTo(400);
        assertThat(audit.blockedProvenanceGapV4OverlapCount()).isZero();
        assertThat(audit.reservedFutureOverlapCount()).isZero();
    }

    @Test
    void profileMarginalKeepsMatchupExactAndChangesOnlyCompositionMode() {
        var matchup = SimulationRuntimeProfiles.resolve(SimulationRuntimeProfileId.MATCHUP_ONLY_CANDIDATE_V1);
        var full = SimulationRuntimeProfiles.resolve(SimulationRuntimeProfileId.FULL_SYSTEM_CANDIDATE_V1);
        assertThat(matchup.gameplayConfiguration().championMatchupMode())
                .isEqualTo(full.gameplayConfiguration().championMatchupMode());
        assertThat(matchup.gameplayConfiguration().teamCompositionGameplayMode())
                .isEqualTo(TeamCompositionGameplayMode.OFF);
        assertThat(full.gameplayConfiguration().teamCompositionGameplayMode())
                .isEqualTo(TeamCompositionGameplayMode.PRODUCTION_V2);
        assertThat(matchup.activeGameplayRulesVersion()).isEqualTo(full.activeGameplayRulesVersion());
        assertThat(matchup.configurationHash()).isEqualTo("58714464c19a2cffd108d47a93a0909126513c8bb10cb0e19bbd87f8e78532ec");
        assertThat(full.configurationHash()).isEqualTo("caaf76274dc148040b0a95eae1ed5181790b2fc840f45af9b109ea7951c1fd5d");
    }

    @Test
    void fixtureOrSeedMutationCannotBeRelabeledAsFrozenSchedule() {
        var source = CompositionV9ApplicationCausalityContract.schedule();
        ArrayList<CompositionV9ApplicationCausalityContract.Fixture> fixtures =
                new ArrayList<>(source.fixtures());
        var first = fixtures.getFirst();
        ArrayList<Long> seeds = new ArrayList<>(first.seeds());
        seeds.set(0, seeds.getFirst() + 1);
        fixtures.set(0, new CompositionV9ApplicationCausalityContract.Fixture(first.fixtureId(),
                first.fixtureLane(), first.pairId(), first.blueTeamCode(), first.redTeamCode(),
                first.seriesGameNumber(), seeds));
        var mutated = new CompositionV9ApplicationCausalityContract.Schedule(source.schemaVersion(),
                source.scheduleVersion(), source.seedNamespace(), source.seedBindingHash(),
                source.consumptionStatus(), source.seedsPerFixture(), fixtures, source.scheduleHash());
        assertThatThrownBy(() -> CompositionV9ApplicationCausalityContract.requireFrozen(mutated))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aggregateOrdersFreezeWorkersFinalizeAndStandaloneFinalizerDoesNotRunWorkers() throws Exception {
        String build = Files.readString(Path.of("build.gradle")).replace("\r\n", "\n");
        String finalizer = taskBlock(build, "tasks.named(\"finalizeCompositionV9ApplicationCausality\")");
        String aggregate = taskBlock(build, "tasks.register(\"runCompositionV9ApplicationCausality\")");
        assertThat(finalizer).contains("mustRunAfter(\"runCompositionV9ApplicationCausalityWorkers\")");
        assertThat(finalizer).doesNotContain("dependsOn");
        assertThat(aggregate).contains("dependsOn(\"runCompositionV9ApplicationCausalityWorkers\")")
                .contains("dependsOn(\"finalizeCompositionV9ApplicationCausality\")");
    }

    private static String taskBlock(String build, String marker) {
        int start = build.indexOf(marker);
        if (start < 0) throw new AssertionError("Missing task marker " + marker);
        int end = build.indexOf("\n}", start);
        return build.substring(start, end + 2);
    }
}
