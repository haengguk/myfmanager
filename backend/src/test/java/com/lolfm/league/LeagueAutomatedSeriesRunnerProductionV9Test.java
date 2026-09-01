package com.lolfm.league;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lolfm.application.MatchEngineV1Policy;
import com.lolfm.simulator.SimulationInstrumentation;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {"spring.main.banner-mode=off", "logging.level.root=ERROR"})
class LeagueAutomatedSeriesRunnerProductionV9Test {
    @Autowired LeagueProductionSnapshotProvider snapshots;
    @Autowired LeagueAutomatedSeriesRunner runner;
    @Autowired ObjectMapper mapper;

    @Test
    void frozenFullAutoFixtureUsesActualProductionAutoDraftAndV9WithDiagnosticsParity()
            throws Exception {
        LeagueSeasonAggregate season = productionSeason(snapshots);
        LeagueFixture fixture = LeagueDomainTestFixtures.fixture(
                season.schedule(), "GEN", "T1");
        LeagueAutomatedSeriesRunnerInput input = new LeagueAutomatedSeriesRunnerInput(
                season, fixture, LeagueV1ProductDecisions.productDecisionHash());

        LeagueAutomatedSeriesRunResult enabled = runner.run(
                input, SimulationInstrumentation.enabled());
        LeagueAutomatedSeriesRunResult disabled = runner.run(
                input, SimulationInstrumentation.disabled());

        assertThat(enabled.status()).isEqualTo(
                LeagueAutomatedSeriesRunResult.Status.COMPLETED);
        assertThat(disabled.status()).isEqualTo(
                LeagueAutomatedSeriesRunResult.Status.COMPLETED);
        assertThat(disabled.receipt().canonicalBytes())
                .isEqualTo(enabled.receipt().canonicalBytes());
        String serialized = mapper.writeValueAsString(enabled.receipt());
        LeagueFixtureCompletionReceiptV1 restored = mapper.readValue(
                serialized, LeagueFixtureCompletionReceiptV1.class);
        assertThat(restored).isEqualTo(enabled.receipt());
        assertThat(restored.canonicalBytes()).isEqualTo(enabled.receipt().canonicalBytes());
        assertThat(enabled.gameExecutionCount())
                .isEqualTo(enabled.receipt().actualGameCount())
                .isBetween(2, 3);
        assertThat(enabled.receipt().canonicalBytes().length)
                .isPositive().isLessThanOrEqualTo(
                        LeagueFixtureCompletionReceiptV1.MAX_CANONICAL_BYTES);
        assertThat(enabled.receipt().orderedGameReceipts()).allSatisfy(game -> {
            assertThat(game.orderedDraftDecisions()).hasSize(20);
            assertThat(game.orderedFinalAssignments()).hasSize(10);
            assertThat(game.bluePicks()).hasSize(5);
            assertThat(game.redPicks()).hasSize(5);
            assertThat(game.policyId()).isEqualTo(MatchEngineV1Policy.POLICY_ID);
            assertThat(game.runtimeProfileId()).isEqualTo(
                    "PRODUCTION_MATCHUP_COMPOSITION_V1");
            assertThat(game.engineImplementationVersion()).isEqualTo(
                    "MATCH_SIMULATOR_ENGINE_IMPLEMENTATION_V9");
            assertThat(game.resourceProvenanceHash()).isEqualTo(
                    snapshots.currentResourceProvenanceHash());
            assertThat(game.outputHash()).matches("[0-9a-f]{64}");
            assertThat(game.replayProvenanceHash()).matches("[0-9a-f]{64}");
            assertThat(game.structuredTimelineHash()).matches("[0-9a-f]{64}");
            assertThat(game.randomTraceHash()).matches("[0-9a-f]{64}");
        });

        LeagueSeasonAggregate applied = season.applyVerifiedCompletion(
                enabled.verifiedCompletion());
        assertThat(applied.revision()).isEqualTo(1);
        assertThat(applied.standings().appliedFixtureCount()).isEqualTo(1);
    }

    static LeagueSeasonAggregate productionSeason(
            LeagueProductionSnapshotProvider snapshots
    ) {
        Set<String> teamCodes = Set.copyOf(LeagueDomainTestFixtures.TEAM_CODES);
        LeagueSeasonFrozenSnapshot snapshot = snapshots.currentSnapshot(teamCodes);
        String leagueId = LeagueIdentity.leagueId("production-runner-test-league");
        String seasonId = LeagueIdentity.seasonId(
                leagueId, "production-runner-test-season");
        return LeagueSeasonAggregate.create(seasonId,
                LeagueSeasonMode.SPECTATOR_FULL_AUTO, null, null, snapshot,
                LeagueDomainTestFixtures.ROOT_SEED,
                LeagueSchedulePolicy.productionDefault());
    }
}
