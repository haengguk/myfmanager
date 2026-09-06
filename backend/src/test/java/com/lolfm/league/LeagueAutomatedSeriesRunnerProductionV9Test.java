package com.lolfm.league;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lolfm.application.MatchEngineV1Policy;
import com.lolfm.simulator.SimulationInstrumentation;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {"spring.main.banner-mode=off", "logging.level.root=ERROR"})
class LeagueAutomatedSeriesRunnerProductionV9Test {
    @Autowired LeagueProductionSnapshotProvider snapshots;
    @Autowired LeagueAutomatedSeriesRunner runner;
    @Autowired ObjectMapper mapper;
    @Autowired LeagueRelationalStore leagueStore;
    @Autowired LeagueSimulationApplicationPort leagueJobs;
    @Autowired JdbcTemplate jdbc;
    @Autowired LeagueSeasonApplicationService seasonLifecycle;

    @Autowired com.lolfm.career.CareerApplicationService careers;
    @Autowired com.lolfm.career.CareerRosterStore rosters;
    @Autowired com.lolfm.career.CareerMarketStore market;
    @Autowired com.lolfm.career.CareerCalendarApplicationService calendar;

    @Test
    void selectedReserveRunsThroughActualLeagueAutoAndFrozenReceiptValidation() {
        var career=careers.create(new com.lolfm.dto.CareerApiV1Dtos.CreateRequest(com.lolfm.dto.CareerApiV1Dtos.CREATE_REQUEST_SCHEMA,
                "KT 후보 실제 Auto","감독","KT",java.util.UUID.randomUUID().toString())).career().career();
        rosters.change(career.careerId(),new com.lolfm.career.CareerRosterStore.Request("CAREER_ROSTER_COMMAND_V1",2027,"LCK:KT","player-jiwoo","SELECT_STARTER",null,null,0,java.util.UUID.randomUUID().toString()));
        String id=career.careerId();var view=market.view(id,2027);
        market.command(id,new com.lolfm.career.CareerMarketStore.Request("CAREER_MARKET_COMMAND_V1",2027,view.revision(),"RELEASE","player-cuzz",null,null,null,null,java.util.UUID.randomUUID().toString()));
        view=market.view(id,2027);var bo=view.players().stream().filter(p->p.playerId().equals("player-bo")).findFirst().orElseThrow();
        var terms=new com.lolfm.career.CareerMarketState.Terms(bo.availableStart(),bo.availableStart().plusYears(2).minusDays(1),bo.askingSalary()*150/100,10_000,com.lolfm.career.CareerMarketState.Role.STARTER);
        market.command(id,new com.lolfm.career.CareerMarketStore.Request("CAREER_MARKET_COMMAND_V1",2027,view.revision(),"SUBMIT","player-bo",null,terms,null,null,java.util.UUID.randomUUID().toString()));
        while(calendar.view(career).state().currentDate().isBefore(bo.availableStart()))calendar.advance(career,com.lolfm.dto.CareerApiV1Dtos.ADVANCE_REQUEST_SCHEMA,calendar.view(career).state().calendarRevision(),"ADVANCE_ONE_DAY",java.util.UUID.randomUUID().toString());
        var current=rosters.view(id,2027);rosters.change(id,new com.lolfm.career.CareerRosterStore.Request("CAREER_ROSTER_COMMAND_V1",2027,"LCK:KT","player-bo","SELECT_STARTER",null,null,current.revision(),java.util.UUID.randomUUID().toString()));
        view=market.view(id,2027);var price=view.management().quotes().stream().filter(q->q.playerId().equals("player-life")).findFirst().orElseThrow();
        var selling=view.contracts().stream().filter(c->c.playerId().equals("player-life")&&c.status()==com.lolfm.career.CareerMarketState.ContractStatus.ACTIVE).findFirst().orElseThrow();
        var moveStart=price.earliestStart();var moveEnd=moveStart.plusYears(2).minusDays(1);
        var transfer=new com.lolfm.career.CareerManagementState.TradeTerms(com.lolfm.career.CareerManagementState.Kind.TRANSFER,"player-life",selling.team(),"LCK:KT",moveStart,moveEnd,price.suggestedTransferFee(),0,
                new com.lolfm.career.CareerMarketState.Terms(moveStart,moveEnd,price.referenceSalary()*150/100,0,com.lolfm.career.CareerMarketState.Role.RESERVE),null);
        market.tradeCommand(id,new com.lolfm.career.CareerMarketStore.TradeRequest("CAREER_TRADE_COMMAND_V1",2027,view.revision(),"SUBMIT",null,transfer,null,java.util.UUID.randomUUID().toString()));
        while(calendar.view(career).state().currentDate().isBefore(moveStart))calendar.advance(career,com.lolfm.dto.CareerApiV1Dtos.ADVANCE_REQUEST_SCHEMA,calendar.view(career).state().calendarRevision(),"ADVANCE_ONE_DAY",java.util.UUID.randomUUID().toString());
        current=rosters.view(id,2027);rosters.change(id,new com.lolfm.career.CareerRosterStore.Request("CAREER_ROSTER_COMMAND_V1",2027,"LCK:KT","player-life","SELECT_STARTER",null,null,current.revision(),java.util.UUID.randomUUID().toString()));
        var frozen=com.lolfm.career.CareerRosterStore.eligiblePair(jdbc,id,2027,"LCK:KT","LCK:T1").domesticPair("KT","T1");
        var season=productionSeason(snapshots);var fixture=LeagueDomainTestFixtures.fixture(season.schedule(),"KT","T1");
        var tx=new org.springframework.transaction.support.TransactionTemplate(new org.springframework.jdbc.datasource.DataSourceTransactionManager(jdbc.getDataSource()));
        String observation="PRODUCTION_FIXTURE|"+season.seasonId()+'|'+fixture.fixtureId();
        tx.executeWithoutResult(ignored->{com.lolfm.career.CareerRosterStore.lockCareer(jdbc,id);com.lolfm.career.CareerAppearanceStore.capture(jdbc,id,2027,observation,fixture.boundSeriesId(),null,frozen);});
        assertThat(market.view(id,2027).management().appearances()).isEmpty();
        var input=new LeagueAutomatedSeriesRunnerInput(season,fixture,season.productDecisionHash(),frozen);
        var result=runner.run(input,SimulationInstrumentation.disabled());
        assertThat(result.status()).isEqualTo(LeagueAutomatedSeriesRunResult.Status.COMPLETED);
        assertThat(result.unifiedReceipt().frozenRosterIdentity()).isEqualTo(frozen.identity());
        result.receipt().orderedGameReceipts().forEach(game->assertThat(game.orderedFinalAssignments())
                .extracting(LeagueFixtureGameReceiptV1.FinalAssignmentEvidence::playerId).contains(new com.lolfm.player.PlayerId("player-jiwoo"),new com.lolfm.player.PlayerId("player-bo"),new com.lolfm.player.PlayerId("player-life")).doesNotContain(new com.lolfm.player.PlayerId("player-fenrir"),new com.lolfm.player.PlayerId("player-cuzz")));
        VerifiedLeagueFixtureCompletion.verifyPersisted(season,result.unifiedReceipt(),null,frozen);
        // This existing production fixture is independent of the Career schedule. Only its verified actual result is consumed.
        tx.executeWithoutResult(ignored->com.lolfm.career.CareerAppearanceStore.complete(jdbc,id,observation,result.unifiedReceipt().canonicalFixtureReceiptHash(),result.gameExecutionCount()));
        var appeared=market.view(id,2027);assertThat(appeared.management().appearances()).hasSize(1);
        var lifePromise=appeared.management().promises().stream().filter(p->p.playerId().equals("player-life")&&p.team().equals("LCK:KT")).findFirst().orElseThrow();
        assertThat(lifePromise.opportunities()).isEqualTo(1);assertThat(lifePromise.starts()).isEqualTo(1);assertThat(lifePromise.sets()).isEqualTo(result.gameExecutionCount());
        tx.executeWithoutResult(ignored->com.lolfm.career.CareerAppearanceStore.complete(jdbc,id,observation,result.unifiedReceipt().canonicalFixtureReceiptHash(),result.gameExecutionCount()));
        assertThat(market.view(id,2027)).isEqualTo(appeared);
        org.assertj.core.api.Assertions.assertThatThrownBy(()->VerifiedLeagueFixtureCompletion.verifyPersisted(season,result.unifiedReceipt(),null,null))
                .hasMessage("FIXTURE_LINEUP_IDENTITY_MISMATCH");
    }

    @Test
    void frozenFullAutoFixtureUsesActualProductionAutoDraftAndV9WithDiagnosticsParity()
            throws Exception {
        LeagueSeasonAggregate season = productionSeason(snapshots);
        LeagueFixture fixture = LeagueDomainTestFixtures.fixture(
                season.schedule(), "GEN", "T1");
        LeagueAutomatedSeriesRunnerInput input = new LeagueAutomatedSeriesRunnerInput(
                season, fixture, LeagueV1ProductDecisions.productDecisionHash());
        leagueStore.freeze(season);
        seasonLifecycle.ready(season.seasonId(), 0);

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
        assertThat(disabled.unifiedReceipt().canonicalBytes())
                .isEqualTo(enabled.unifiedReceipt().canonicalBytes());
        String serialized = mapper.writeValueAsString(enabled.unifiedReceipt());
        LeagueFixtureCompletionReceiptV2 restored = mapper.readValue(
                serialized, LeagueFixtureCompletionReceiptV2.class);
        assertThat(restored).isEqualTo(enabled.unifiedReceipt());
        assertThat(restored.canonicalBytes())
                .isEqualTo(enabled.unifiedReceipt().canonicalBytes());
        assertThat(enabled.gameExecutionCount())
                .isEqualTo(enabled.receipt().actualGameCount())
                .isBetween(2, 3);
        assertThat(enabled.receipt().canonicalBytes().length)
                .isPositive().isLessThanOrEqualTo(
                        LeagueFixtureCompletionReceiptV1.MAX_CANONICAL_BYTES);
        assertThat(enabled.unifiedReceipt().canonicalBytes().length)
                .isPositive().isLessThanOrEqualTo(
                        LeagueFixtureCompletionReceiptV2.MAX_CANONICAL_BYTES);
        assertThat(enabled.unifiedReceipt().leagueId()).isEqualTo(season.leagueId());
        assertThat(enabled.unifiedReceipt().playerSeriesBindingHash()).isNull();
        assertThat(enabled.unifiedReceipt().orderedDraftAuthorityReceipts())
                .allSatisfy(authority -> {
                    assertThat(authority.executionMode())
                            .isEqualTo(LeagueFixtureExecutionMode.FULL_AUTO);
                    assertThat(authority.controlledSide()).isNull();
                });
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

        leagueJobs.dispatchFullAutoFixture(season.seasonId(), fixture.fixtureId());
        var workerResult = leagueJobs.executeQueued(
                "production-v9-worker-primary", 1,
                SimulationInstrumentation.disabled());
        assertThat(workerResult).singleElement().satisfies(result -> {
            assertThat(result.status()).isEqualTo(
                    LeagueSimulationApplicationPort.Status.COMPLETED);
            assertThat(result.receiptHash()).isEqualTo(
                    enabled.unifiedReceipt().canonicalFixtureReceiptHash());
        });
        assertThat(leagueStore.pendingOutboxCount()).isOne();
        assertThat(leagueStore.loadSeason(season.seasonId()).revision()).isZero();
        assertThat(leagueStore.deliverNextOutbox()).isTrue();
        assertThat(leagueStore.loadSeason(season.seasonId()).revision()).isOne();
        assertThat(leagueStore.loadSeason(season.seasonId()).standings()
                .appliedFixtureCount()).isOne();
        assertThat(leagueStore.deliverNextOutbox()).isFalse();
        jdbc.update("""
                UPDATE league_outbox SET lifecycle_status = 'PENDING', delivered_at = NULL
                WHERE receipt_hash = ?
                """, enabled.unifiedReceipt().canonicalFixtureReceiptHash());
        assertThat(leagueStore.deliverNextOutbox()).isTrue();
        assertThat(leagueStore.loadSeason(season.seasonId()).revision()).isOne();
        leagueStore.storeVerifiedCompletion(
                enabled.unifiedReceipt(), enabled.verifiedCompletion());
        assertThat(leagueStore.loadSeason(season.seasonId()).revision()).isOne();

        LeagueFixture backgroundFixture = LeagueDomainTestFixtures.fixture(
                season.schedule(), "DK", "HLE");
        assertThat(leagueJobs.dispatchFullAutoFixture(
                season.seasonId(), backgroundFixture.fixtureId()).replayed()).isFalse();
        var background = leagueJobs.executeQueued(
                "production-v9-worker-secondary", SimulationInstrumentation.disabled());
        assertThat(background).singleElement().satisfies(result -> {
            assertThat(result.status()).isEqualTo(
                    LeagueSimulationApplicationPort.Status.COMPLETED);
            assertThat(result.attemptNumber()).isOne();
            assertThat(result.receiptHash()).matches("[0-9a-f]{64}");
        });
        assertThat(leagueStore.pendingOutboxCount()).isOne();
        assertThat(leagueStore.drainOutbox(10)).isOne();
        assertThat(leagueStore.loadSeason(season.seasonId()).revision()).isEqualTo(2);
    }

    static LeagueSeasonAggregate productionSeason(
            LeagueProductionSnapshotProvider snapshots
    ) {
        Set<String> teamCodes = Set.copyOf(LeagueDomainTestFixtures.TEAM_CODES);
        LeagueSeasonFrozenSnapshot snapshot = snapshots.currentSnapshot(teamCodes);
        String leagueId = LeagueIdentity.leagueId("production-runner-test-league");
        String seasonId = LeagueIdentity.seasonId(
                leagueId, "production-runner-test-season");
        return LeagueSeasonAggregate.create(leagueId, seasonId,
                LeagueSeasonMode.SPECTATOR_FULL_AUTO, null, null, snapshot,
                LeagueDomainTestFixtures.ROOT_SEED,
                LeagueSchedulePolicy.productionDefault());
    }
}
