package com.lolfm.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lolfm.simulator.SimulationRuntimeProfileId;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class MatchEngineV9FreshRequalificationContractTest {
    @Test
    void freezesOneHundredFixturesEightFreshSeedsAndThreeOrderedProfiles() {
        var schedule = MatchEngineV9FreshRequalificationContract.requireFrozen(
                MatchEngineV9FreshRequalificationContract.schedule());
        assertThat(schedule.fixtures()).hasSize(100);
        assertThat(schedule.fixtures().stream()
                .filter(value -> value.seriesGameNumber() == 1)).hasSize(90);
        assertThat(schedule.fixtures().stream()
                .filter(value -> value.seriesGameNumber() == 2)).hasSize(10);
        assertThat(schedule.fixtures()).allSatisfy(value -> {
            assertThat(value.calibrationSeeds()).hasSize(4);
            assertThat(value.holdoutSeeds()).hasSize(4);
            assertThat(value.calibrationSeeds()).doesNotContainAnyElementsOf(value.holdoutSeeds());
        });
        assertThat(MatchEngineV9FreshRequalificationContract.PROFILES).containsExactly(
                SimulationRuntimeProfileId.BASELINE_V1,
                SimulationRuntimeProfileId.MATCHUP_ONLY_CANDIDATE_V1,
                SimulationRuntimeProfileId.FULL_SYSTEM_CANDIDATE_V1);
        assertThat(schedule.draftReusePolicy()).isEqualTo(
                "ONE_PRODUCTION_AUTO_DRAFT_PER_FIXTURE_AND_SEED_SHARED_BY_ALL_PROFILES");
        assertThat(MatchEngineV9FreshRequalificationV2Contract.CONTRACT_SCHEMA)
                .isEqualTo("MATCH_ENGINE_V9_AUTO_DRAFT_MATCHUP_COMPOSITION_"
                        + "FRESH_REQUALIFICATION_CONTRACT_V2");
        assertThat(schedule.scheduleVersion()).isEqualTo(
                "MATCH_ENGINE_V9_REAL_LCK_AUTO_DRAFT_PAIRED_100_FIXTURE_4_4_V2");
        assertThat(schedule.seedNamespace()).isEqualTo(
                "MATCH_ENGINE_V9_AUTO_DRAFT_FRESH_REQUALIFICATION_SEEDS_V2_"
                        + "AFTER_CAUSALITY_EVIDENCE_HARDENING");
    }

    @Test
    void completeConsumedLedgerProvesAllOfficialAndDryRunSeedsFresh() throws Exception {
        var ledger = MatchEngineV9ConsumedSeedLedger.create(new ObjectMapper(), Path.of("."));
        assertThat(ledger.complete()).isTrue();
        assertThat(ledger.sourceCount()).isGreaterThanOrEqualTo(12);
        assertThat(ledger.sources()).anySatisfy(value -> {
            assertThat(value.sourceId()).isEqualTo("COMPOSITION_V9_V6");
            assertThat(value.relationship()).contains("REUSES_COMPOSITION_V9_V5");
        });
        assertThat(ledger.sources()).anySatisfy(value -> {
            assertThat(value.sourceId()).isEqualTo(
                    "MATCH_ENGINE_V9_FRESH_V1_CALIBRATION");
            assertThat(value.relationship()).isEqualTo("CONSUMED_BLOCKED_EVIDENCE");
            assertThat(value.uniqueSeedCount()).isEqualTo(400);
        });
        assertThat(ledger.sources()).anySatisfy(value -> {
            assertThat(value.sourceId()).isEqualTo("MATCH_ENGINE_V9_FRESH_V1_HOLDOUT");
            assertThat(value.relationship()).isEqualTo(
                    "RETIRED_UNCONSUMED_SOURCE_CONTRACT_CHANGED");
            assertThat(value.uniqueSeedCount()).isEqualTo(400);
        });
        var audit = MatchEngineV9FreshRequalificationContract.requireNoSeedOverlap(
                MatchEngineV9FreshRequalificationContract.schedule(), ledger.seedSet());
        assertThat(audit.clean()).isTrue();
        assertThat(audit.officialUniqueSeedCount()).isEqualTo(800);
        assertThat(audit.dryRunUniqueSeedCount()).isEqualTo(100);
    }

    @Test
    void scheduleMutationAndHistoricalOverlapAreRejected() {
        var schedule = MatchEngineV9FreshRequalificationContract.schedule();
        ArrayList<MatchEngineV9FreshRequalificationContract.Fixture> fixtures =
                new ArrayList<>(schedule.fixtures());
        var first = fixtures.getFirst();
        ArrayList<Long> calibration = new ArrayList<>(first.calibrationSeeds());
        calibration.set(0, 73L);
        fixtures.set(0, new MatchEngineV9FreshRequalificationContract.Fixture(
                first.fixtureId(), first.fixtureLane(), first.pairId(), first.blueTeamCode(),
                first.redTeamCode(), first.seriesGameNumber(), calibration, first.holdoutSeeds()));
        var changed = new MatchEngineV9FreshRequalificationContract.Schedule(
                schedule.schemaVersion(), schedule.scheduleVersion(), schedule.seedNamespace(),
                schedule.seedBindingHash(), schedule.draftReusePolicy(),
                schedule.calibrationSeedsPerFixture(), schedule.holdoutSeedsPerFixture(),
                fixtures, schedule.scheduleHash());
        assertThatThrownBy(() -> MatchEngineV9FreshRequalificationContract.requireFrozen(changed))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> MatchEngineV9FreshRequalificationContract.requireNoSeedOverlap(
                schedule, java.util.Set.of(first.calibrationSeeds().getFirst())))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void macroGatesAreFrozenBeforePopulationExecution() {
        var gates = MatchEngineV9FreshRequalificationContract.GATES;
        assertThat(gates.absoluteBlueWinRateDeltaPercentagePoints()).isEqualTo(2.0);
        assertThat(gates.directionalWinnerFlipImbalancePercentagePoints()).isEqualTo(2.0);
        assertThat(gates.pairedWinnerChangedRatePercent()).isEqualTo(15.0);
        assertThat(gates.objectiveChangedRatePercent()).isEqualTo(20.0);
        assertThat(gates.actualStructureProgressionChangedRatePercent()).isEqualTo(15.0);
        assertThat(gates.nexusOrEndingProgressionChangedRatePercent()).isEqualTo(7.5);
        assertThat(gates.absoluteMeanDurationDeltaSeconds()).isEqualTo(30.0);
        assertThat(gates.absoluteAggregateP95DurationDeltaSeconds()).isEqualTo(120.0);
        assertThat(gates.timeoutIncrease()).isZero();
    }

    @Test
    void diagnosticComponentHashCanonicalizesStructuredIncomparableMapKeys() {
        var first = new java.util.LinkedHashMap<com.lolfm.simulator.PlayerKey, Integer>();
        first.put(new com.lolfm.simulator.PlayerKey(
                com.lolfm.simulator.TeamSide.RED, com.lolfm.domain.Position.MID), 2);
        first.put(new com.lolfm.simulator.PlayerKey(
                com.lolfm.simulator.TeamSide.BLUE, com.lolfm.domain.Position.TOP), 1);
        var reversed = new java.util.LinkedHashMap<com.lolfm.simulator.PlayerKey, Integer>();
        first.entrySet().stream().toList().reversed().forEach(entry ->
                reversed.put(entry.getKey(), entry.getValue()));
        assertThat(com.lolfm.simulator.Phase13GB1SimulationExecutor
                .structuredValueHash(first)).isEqualTo(
                com.lolfm.simulator.Phase13GB1SimulationExecutor
                        .structuredValueHash(reversed));
        assertThat(com.lolfm.simulator.Phase13GB1SimulationExecutor
                .structuredValueHash(null)).matches("[0-9a-f]{64}");
    }

    @Test
    void signedActionIdsRejectMissingOrBlankAndCanonicalizeDuplicates() throws Exception {
        assertThat(MatchEngineV9FreshRequalificationRunner.canonicalActionIds(
                List.of("ACTION:Z", "ACTION:A", "ACTION:Z")))
                .containsExactly("ACTION:A", "ACTION:Z");
        assertThatThrownBy(() -> MatchEngineV9FreshRequalificationRunner
                .canonicalActionIds(java.util.Arrays.asList("ACTION:A", null)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> MatchEngineV9FreshRequalificationRunner
                .canonicalActionIds(List.of("ACTION:A", " ")))
                .isInstanceOf(IllegalArgumentException.class);

        var preflight = new MatchEngineV9FreshSerializationPreflight();
        assertThat(preflight.bytes(false)).isEqualTo(preflight.bytes(true));
        byte[] original = preflight.bytes(false);
        byte[] mutated = original.clone();
        mutated[mutated.length / 2] ^= 1;
        assertThat(MatchEngineV9FreshRequalificationContract.sha256(mutated))
                .isNotEqualTo(MatchEngineV9FreshRequalificationContract.sha256(original));
    }

    @Test
    void gradleKeepsHistoricalFreshJvmsAndStandaloneFinalizersWorkerFree()
            throws Exception {
        String build = java.nio.file.Files.readString(Path.of("build.gradle"));
        int historical = build.indexOf("def registerV9RequalificationTest");
        int historicalEnd = build.indexOf(
                "// MATCH_ENGINE_V9_REQUALIFICATION_BUILD_CONTRACT_END", historical);
        assertThat(build.substring(historical, historicalEnd)).contains("forkEvery = 1");
        assertThat(build).doesNotContain(
                "tasks.named(\"finalizeMatchEngineV9FreshCalibration\") {\n"
                        + "    dependsOn(\"runMatchEngineV9FreshCalibrationWorkers\")");
        assertThat(build).contains(
                "tasks.register(\"runMatchEngineV9FreshRequalification\")");
        assertThat(build).contains(
                "dependsOn(\"runMatchEngineV9FreshSerializationPreflight\"");
    }
}
