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

    @Autowired com.lolfm.career.CareerCalendarRelationalStore calendarStore;
    @Autowired com.lolfm.career.CareerCalendarTemplate calendarTemplate;
    @Autowired com.lolfm.career.CareerCompetitionApplicationService competitionService;

    @Test
    void calendarDateAdvanceCapturesSettledStartAndAppliesActualAutoExactlyOnce() {
        var c=careers.create(new com.lolfm.dto.CareerApiV1Dtos.CreateRequest(com.lolfm.dto.CareerApiV1Dtos.CREATE_REQUEST_SCHEMA,
                "Calendar 실제 완료", "감독", "T1",java.util.UUID.randomUUID().toString())).career().career();
        String id=c.careerId();var season=leagueStore.loadSeason(c.seasonId());
        // This fixture verifies generated-player capture, not recruitment by a low-budget club.
        var wageHeadroom=new java.util.TreeMap<String,Long>();market.view(id,2027).finances().forEach(a->wageHeadroom.put(a.team(),a.annualBudget()-a.committedPeakSalary()));
        var fixture=season.schedule().fixtures().stream().filter(f->f.roundNumber()==1&&f.executionMode()==LeagueFixtureExecutionMode.FULL_AUTO)
                .max(java.util.Comparator.comparingLong(f->wageHeadroom.get("LCK:"+f.firstTeamCode()))).orElseThrow();
        var target=calendarTemplate.leagueRoundDates(2027).get(1);var previous=target.minusDays(1);
        var tx=new org.springframework.transaction.support.TransactionTemplate(new org.springframework.jdbc.datasource.DataSourceTransactionManager(jdbc.getDataSource()));
        var rookie=new java.util.concurrent.atomic.AtomicReference<String>();
        var initial=calendar.view(c).state();String setup=java.util.UUID.randomUUID().toString();
        // Test-only preparation isolates one due fixture; earlier competitions are outside this regression.
        calendarStore.execute(setup,id,initial.calendarRevision(),"ADVANCE_ONE_DAY",calendarTemplate.advancePayloadHash(id,initial.calendarRevision(),"ADVANCE_ONE_DAY"),row->{
            // Recruit immediately before capture; months of AI operation may legitimately move a reserve away.
            com.lolfm.career.CareerMarketStore.processThrough(jdbc,id,previous.minusDays(com.lolfm.career.CareerMarketPolicy.DECISION_DAYS));
            rookie.set(com.lolfm.career.CareerLifecycleTestSupport.recruitGeneratedStarter(jdbc,id,"LCK:"+fixture.firstTeamCode()));
            com.lolfm.career.CareerMarketStore.processThrough(jdbc,id,previous);
            com.lolfm.career.CareerLifecycleTestSupport.prepareOnlyStarter(jdbc,id,"LCK:"+fixture.firstTeamCode(),rookie.get());
            jdbc.update("UPDATE league_fixture SET lifecycle_status='COMPLETED' WHERE season_id=? AND round_number=1 AND fixture_id<>?",c.seasonId(),fixture.fixtureId());
            return new com.lolfm.career.CareerCalendarRelationalStore.AdvanceMutation(previous,calendarTemplate.eventCursor(calendarTemplate.project(2027),previous),row.lastProcessedEventId(),row.lastProcessedDate(),"ACTIVE",null,true,false,200,null,false);
        });
        var competitions=org.mockito.Mockito.spy(competitionService);
        // The direct jump to the regular season intentionally omits earlier competitions.
        org.mockito.Mockito.doNothing().when(competitions).reconcileForAdvance(org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.anyInt(),org.mockito.ArgumentMatchers.any());
        org.mockito.Mockito.doReturn(com.lolfm.career.CareerCompetitionApplicationService.CompetitionGate.clear()).when(competitions).gate(org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.anyInt(),org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.any());
        var realCalendar=new com.lolfm.career.CareerCalendarApplicationService(calendarStore,calendarTemplate,new LeagueCareerCalendarService(leagueStore,leagueJobs,owner->true),competitions);
        var before=com.lolfm.career.CareerDevelopmentStore.load(jdbc,id);var state=realCalendar.view(c).state();String command=java.util.UUID.randomUUID().toString();
        org.assertj.core.api.Assertions.assertThatThrownBy(()->tx.executeWithoutResult(status->{
            realCalendar.advance(c,com.lolfm.dto.CareerApiV1Dtos.ADVANCE_REQUEST_SCHEMA,state.calendarRevision(),"ADVANCE_ONE_DAY",command);
            throw new IllegalStateException("ROLLBACK_CALENDAR_START");
        })).hasMessage("ROLLBACK_CALENDAR_START");
        assertThat(com.lolfm.career.CareerDevelopmentStore.load(jdbc,id)).isEqualTo(before);assertThat(realCalendar.view(c).state().currentDate()).isEqualTo(previous);
        assertThat(leagueJobs.findJob(c.seasonId(),fixture.fixtureId())).isEmpty();
        realCalendar.advance(c,com.lolfm.dto.CareerApiV1Dtos.ADVANCE_REQUEST_SCHEMA,state.calendarRevision(),"ADVANCE_ONE_DAY",command);
        String identity="LEAGUE|"+c.seasonId()+'|'+fixture.fixtureId();
        var appearance=com.lolfm.career.CareerRosterStore.read(jdbc.queryForObject("SELECT snapshot_json FROM career_appearance_binding WHERE career_id=? AND fixture_identity=?",String.class,id,identity),com.lolfm.career.CareerManagementState.Appearance.class);
        assertThat(appearance.date()).isEqualTo(target);assertThat(realCalendar.view(c).state().currentDate()).isEqualTo(target);
        var settled=com.lolfm.career.CareerDevelopmentStore.load(jdbc,id);assertThat(settled.state().nextSettlement()).isEqualTo(target);
        var frozen=rosters.leagueFixtureRoster(c.seasonId(),fixture.fixtureId());
        assertThat(frozen.teams().values().stream().flatMap(t->t.players().stream()).map(p->p.playerId())).contains(rookie.get());
        realCalendar.advance(c,com.lolfm.dto.CareerApiV1Dtos.ADVANCE_REQUEST_SCHEMA,state.calendarRevision(),"ADVANCE_ONE_DAY",command);
        assertThat(rosters.leagueFixtureRoster(c.seasonId(),fixture.fixtureId())).isEqualTo(frozen);assertThat(com.lolfm.career.CareerDevelopmentStore.load(jdbc,id)).isEqualTo(settled);
        var lease=leagueJobs.leaseNext("calendar-development-regression").orElseThrow();assertThat(lease.fixtureId()).isEqualTo(fixture.fixtureId());
        var result=leagueJobs.execute(lease,SimulationInstrumentation.disabled());assertThat(result.status()).isEqualTo(LeagueSimulationApplicationPort.Status.COMPLETED);
        assertThat(leagueStore.deliverNextOutbox()).isTrue();var rewarded=com.lolfm.career.CareerDevelopmentStore.load(jdbc,id);
        assertThat(rewarded.revision()).isEqualTo(settled.revision()+1);
        var recorded=market.view(id,2027).management().appearances().stream().filter(a->a.fixtureId().equals(identity)).findFirst().orElseThrow();
        assertThat(recorded.date()).isEqualTo(target);assertThat(recorded.completedSets()).isBetween(2,3);
        for(var roster:frozen.teams().values())for(var player:roster.players())assertThat(rewarded.state().players().get(player.playerId()).playedOn()).isEqualTo(target);
        jdbc.update("UPDATE league_outbox SET lifecycle_status='PENDING',delivered_at=NULL WHERE receipt_hash=?",result.receiptHash());
        assertThat(leagueStore.deliverNextOutbox()).isTrue();assertThat(com.lolfm.career.CareerDevelopmentStore.load(jdbc,id)).isEqualTo(rewarded);
        realCalendar.advance(c,com.lolfm.dto.CareerApiV1Dtos.ADVANCE_REQUEST_SCHEMA,state.calendarRevision(),"ADVANCE_ONE_DAY",command);
        assertThat(com.lolfm.career.CareerDevelopmentStore.load(jdbc,id)).isEqualTo(rewarded);
        System.out.println("CALENDAR_ACTUAL_COMPLETION previous="+previous+" captured="+appearance.date()+" settled="+settled.state().nextSettlement()+" rookie="+rookie.get()+" games="+recorded.completedSets()+" receipt="+result.receiptHash());
    }

    @Test
    void selectedReserveRunsThroughActualLeagueAutoAndFrozenReceiptValidation() {
        var career=careers.create(new com.lolfm.dto.CareerApiV1Dtos.CreateRequest(com.lolfm.dto.CareerApiV1Dtos.CREATE_REQUEST_SCHEMA,
                "KT 후보 실제 Auto","감독","KT",java.util.UUID.randomUUID().toString())).career().career();
        rosters.change(career.careerId(),new com.lolfm.career.CareerRosterStore.Request("CAREER_ROSTER_COMMAND_V1",2027,"LCK:KT","player-jiwoo","SELECT_STARTER",null,null,rosters.view(career.careerId(),2027).revision(),java.util.UUID.randomUUID().toString()));
        String id=career.careerId();var view=market.view(id,2027);
        market.command(id,new com.lolfm.career.CareerMarketStore.Request("CAREER_MARKET_COMMAND_KRW_V1",2027,view.revision(),"RELEASE","player-cuzz",null,null,null,null,java.util.UUID.randomUUID().toString()));
        view=market.view(id,2027);var bo=view.players().stream().filter(p->p.playerId().equals("player-bo")).findFirst().orElseThrow();
        var terms=new com.lolfm.career.CareerMarketState.Terms(bo.availableStart(),bo.availableStart().plusYears(2).minusDays(1),bo.askingSalary()*150/100,10_000,com.lolfm.career.CareerMarketState.Role.STARTER);
        market.command(id,new com.lolfm.career.CareerMarketStore.Request("CAREER_MARKET_COMMAND_KRW_V1",2027,view.revision(),"SUBMIT","player-bo",null,terms,null,null,java.util.UUID.randomUUID().toString()));
        while(calendar.view(career).state().currentDate().isBefore(bo.availableStart()))calendar.advance(career,com.lolfm.dto.CareerApiV1Dtos.ADVANCE_REQUEST_SCHEMA,calendar.view(career).state().calendarRevision(),"ADVANCE_ONE_DAY",java.util.UUID.randomUUID().toString());
        var current=rosters.view(id,2027);rosters.change(id,new com.lolfm.career.CareerRosterStore.Request("CAREER_ROSTER_COMMAND_V1",2027,"LCK:KT","player-bo","SELECT_STARTER",null,null,current.revision(),java.util.UUID.randomUUID().toString()));
        view=market.view(id,2027);var price=view.management().quotes().stream().filter(q->q.playerId().equals("player-life")).findFirst().orElseThrow();
        var selling=view.contracts().stream().filter(c->c.playerId().equals("player-life")&&c.status()==com.lolfm.career.CareerMarketState.ContractStatus.ACTIVE).findFirst().orElseThrow();
        var moveStart=price.earliestStart();var moveEnd=moveStart.plusYears(2).minusDays(1);
        var transfer=new com.lolfm.career.CareerManagementState.TradeTerms(com.lolfm.career.CareerManagementState.Kind.TRANSFER,"player-life",selling.team(),"LCK:KT",moveStart,moveEnd,price.suggestedTransferFee(),0,
                new com.lolfm.career.CareerMarketState.Terms(moveStart,moveEnd,price.referenceSalary()*150/100,0,com.lolfm.career.CareerMarketState.Role.RESERVE),null);
        market.tradeCommand(id,new com.lolfm.career.CareerMarketStore.TradeRequest("CAREER_TRADE_COMMAND_KRW_V1",2027,view.revision(),"SUBMIT",null,transfer,null,java.util.UUID.randomUUID().toString()));
        while(calendar.view(career).state().currentDate().isBefore(moveStart))calendar.advance(career,com.lolfm.dto.CareerApiV1Dtos.ADVANCE_REQUEST_SCHEMA,calendar.view(career).state().calendarRevision(),"ADVANCE_ONE_DAY",java.util.UUID.randomUUID().toString());
        current=rosters.view(id,2027);rosters.change(id,new com.lolfm.career.CareerRosterStore.Request("CAREER_ROSTER_COMMAND_V1",2027,"LCK:KT","player-life","SELECT_STARTER",null,null,current.revision(),java.util.UUID.randomUUID().toString()));
        // Synthetic pre-earned progress prepares a grown input; the following Series is real Production V9.
        var developmentBefore=com.lolfm.career.CareerDevelopmentStore.load(jdbc,id);var state=developmentBefore.state();
        var base=com.lolfm.career.CareerRosterStore.baseDirectory(jdbc,id);var life=base.players().get("player-life");
        var players=new java.util.TreeMap<>(state.players());var starting=players.get("player-life");
        var grown=com.lolfm.career.CareerDevelopmentPolicy.grow(starting,life,com.lolfm.career.CareerDevelopmentPolicy.metadata(life),state.nextSettlement(),12000,1000,1000,com.lolfm.career.CareerDevelopmentPolicy.DEFAULT);
        assertThat(com.lolfm.career.CareerDevelopmentPolicy.sum(grown)).isGreaterThan(com.lolfm.career.CareerDevelopmentPolicy.sum(starting));players.put("player-life",grown);
        var prepared=new com.lolfm.career.CareerDevelopmentState(state.policyVersion(),state.initializationVersion(),state.initializedOn(),state.nextSettlement(),players,state.teamPlans(),state.gains(),state.monthly());
        String developmentPayload=com.lolfm.career.CareerRosterStore.write(prepared);jdbc.update("UPDATE career_development_state SET state_json=?,state_hash=? WHERE career_id=?",developmentPayload,com.lolfm.career.CareerRosterStore.hash(developmentPayload),id);
        var frozen=com.lolfm.career.CareerRosterStore.eligiblePair(jdbc,id,2027,"LCK:KT","LCK:T1").domesticPair("KT","T1");
        assertThat(frozen.roster("KT").players().stream().filter(p->p.playerId().equals("player-life")).findFirst().orElseThrow().ratings()).isNotEqualTo(life.gameplay().ratings());
        var currentPrice=market.view(id,2027).management().quotes().stream().filter(q->q.playerId().equals("player-life")).findFirst().orElseThrow();
        var priceBasis=com.lolfm.career.CareerRosterStore.read(jdbc.queryForObject("SELECT state_json FROM career_market_state WHERE career_id=?",String.class,id),com.lolfm.career.CareerMarketState.class).finance().prices().get("player-life");
        long currentStrength=frozen.roster("KT").players().stream().filter(p->p.playerId().equals("player-life")).findFirst().orElseThrow().ratings().values().stream().mapToLong(Integer::longValue).sum();
        assertThat(currentPrice.referenceSalary()).isEqualTo(com.lolfm.career.CareerFinancePolicy.ratio(priceBasis.referenceSalary(),currentStrength,priceBasis.referenceStrength()));
        assertThat(market.view(id,2027).management().trades().stream().filter(t->t.terms().playerId().equals("player-life")).findFirst().orElseThrow().terms().fee()).isEqualTo(transfer.fee());
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
        org.assertj.core.api.Assertions.assertThatThrownBy(()->tx.executeWithoutResult(ignored->{com.lolfm.career.CareerAppearanceStore.complete(jdbc,id,observation,result.unifiedReceipt().canonicalFixtureReceiptHash(),result.receipt().orderedGameReceipts());throw new IllegalStateException("ROLLBACK_COMPLETION");})).hasMessage("ROLLBACK_COMPLETION");
        assertThat(com.lolfm.career.CareerDevelopmentStore.load(jdbc,id).state()).isEqualTo(prepared);
        tx.executeWithoutResult(ignored->com.lolfm.career.CareerAppearanceStore.complete(jdbc,id,observation,result.unifiedReceipt().canonicalFixtureReceiptHash(),result.receipt().orderedGameReceipts()));
        var rewarded=com.lolfm.career.CareerDevelopmentStore.load(jdbc,id);
        System.out.println("DEVELOPMENT_PRODUCTION games="+result.gameExecutionCount()+" lifeFrozen="+frozen.roster("KT").players().stream().filter(p->p.playerId().equals("player-life")).findFirst().orElseThrow().ratings()+" lifeBefore="+prepared.players().get("player-life").internalRatings()+" lifeAfter="+rewarded.state().players().get("player-life").internalRatings()+" currentReferenceSalary="+currentPrice.referenceSalary());
        for(var game:result.receipt().orderedGameReceipts())for(var assignment:game.orderedFinalAssignments()) {
            String playerId=assignment.playerId().value();String key=com.lolfm.career.CareerDevelopmentPolicy.key(assignment.championId().value(),assignment.position());
            int before=prepared.players().get(playerId).internalProficiencies().getOrDefault(key,14000);
            if(before==20000)assertThat(rewarded.state().players().get(playerId).internalProficiencies().get(key)).isEqualTo(20000);
            else assertThat(rewarded.state().players().get(playerId).internalProficiencies().get(key)).isGreaterThan(before);
            assertThat(rewarded.state().players().get(playerId).fatigue()).isEqualTo(Math.min(1000,prepared.players().get(playerId).fatigue()+90*result.gameExecutionCount()));
        }
        assertThat(rewarded.state().players().get("player-fenrir")).isEqualTo(prepared.players().get("player-fenrir"));
        // Same receipt identity with changed structured games is rejected before any mutation.
        org.assertj.core.api.Assertions.assertThatThrownBy(()->tx.executeWithoutResult(ignored->com.lolfm.career.CareerAppearanceStore.complete(jdbc,id,observation,result.unifiedReceipt().canonicalFixtureReceiptHash(),result.receipt().orderedGameReceipts().subList(0,1)))).hasMessage("DEVELOPMENT_COMPLETION_CONFLICT");
        var appeared=market.view(id,2027);assertThat(appeared.management().appearances()).hasSize(1);
        var lifePromise=appeared.management().promises().stream().filter(p->p.playerId().equals("player-life")&&p.team().equals("LCK:KT")).findFirst().orElseThrow();
        assertThat(lifePromise.opportunities()).isEqualTo(1);assertThat(lifePromise.starts()).isEqualTo(1);assertThat(lifePromise.sets()).isEqualTo(result.gameExecutionCount());
        tx.executeWithoutResult(ignored->com.lolfm.career.CareerAppearanceStore.complete(jdbc,id,observation,result.unifiedReceipt().canonicalFixtureReceiptHash(),result.receipt().orderedGameReceipts()));
        assertThat(market.view(id,2027)).isEqualTo(appeared);
        assertThat(com.lolfm.career.CareerDevelopmentStore.load(jdbc,id)).isEqualTo(rewarded);
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
            assertThat(game.policyId()).isEqualTo(MatchEngineV1Policy.REALISM_POLICY_ID);
            assertThat(game.runtimeProfileId()).isEqualTo(
                    "PRODUCTION_REALISM_V1");
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
