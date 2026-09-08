package com.lolfm.career;

import com.lolfm.league.*;
import com.lolfm.dto.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.NONE,properties={"spring.main.banner-mode=off","logging.level.root=ERROR","spring.main.lazy-initialization=true"})
class CareerOverseasExecutionTest {
    @Autowired CareerApplicationService careers;
    @Autowired CareerOverseasStore overseas;
    @Autowired CareerCompetitionRelationalStore competitions;
    @Autowired CareerMarketStore market;
    @Autowired JdbcTemplate jdbc;
    @Autowired LeagueProductionSnapshotProvider snapshots;
    @Autowired CareerCompetitionExecutionService execution;
    @Autowired CareerCalendarApplicationService calendar;
    private org.springframework.transaction.support.TransactionTemplate transaction(){return new org.springframework.transaction.support.TransactionTemplate(new org.springframework.jdbc.datasource.DataSourceTransactionManager(jdbc.getDataSource()));}
    @Autowired CareerPersistenceStartupRecovery startup;
    @Test void startupRecoveryIsolatesMissingOperatingRosterAndStillRecoversHealthySave() {
        var broken=careers.create(new CareerApiV1Dtos.CreateRequest(CareerApiV1Dtos.CREATE_REQUEST_SCHEMA,"명부 누락 복구 격리","감독","KT",UUID.randomUUID().toString())).career().career();
        var good=careers.create(new CareerApiV1Dtos.CreateRequest(CareerApiV1Dtos.CREATE_REQUEST_SCHEMA,"정상 시작 복구","감독","GEN",UUID.randomUUID().toString())).career().career();
        String id=broken.careerId();int year=2027;
        transaction().executeWithoutResult(t->{
            jdbc.update("UPDATE career_competition_fixture SET lifecycle_status='COMPLETED' WHERE career_id=? AND competition_id='LCK_CUP'",id);
            jdbc.update("UPDATE career_competition_instance SET lifecycle_status='COMPLETED',blocking_reason=NULL WHERE career_id=? AND competition_id='LCK_CUP'",id);
            var source=jdbc.queryForObject("SELECT match_id FROM career_competition_fixture WHERE career_id=? AND competition_id='LCK_CUP' ORDER BY match_order LIMIT 1",String.class,id);
            for(int i=1;i<=2;i++)jdbc.update("INSERT INTO career_competition_output VALUES (?,?,?,?,?,?,?,CURRENT_TIMESTAMP)",id,year,"LCK_CUP","FIRST_STAND_LCK_SEED_"+i,i==1?"GEN":"T1",source,"a".repeat(64));
            competitions.refreshAllInstanceHashes(id,year);competitions.refreshCycleHash(id,year);
            jdbc.update("DELETE FROM career_roster_state WHERE career_id=? AND season_year=?",id,year);
            // A valid pending initialization on the healthy save must actually run.
            jdbc.update("DELETE FROM career_development_state WHERE career_id=?",good.careerId());
            jdbc.update("UPDATE career_player_directory SET development_version=NULL WHERE career_id=?",good.careerId());
        });
        var preserved=new TreeMap<String,List<Map<String,Object>>>();
        for(String table:List.of("career_player_directory","career_market_state","career_development_state","career_lifecycle_state","career_create_command","career_competition_instance"))preserved.put(table,jdbc.queryForList("SELECT * FROM "+table+" WHERE career_id=?",id));
        assertThatThrownBy(()->careers.get(id)).isInstanceOfSatisfying(CareerException.class,e->assertThat(e.type()).isEqualTo(CareerException.Type.SAVE_COMPATIBILITY_DATA_MISSING));
        startup.recover();
        assertThat(CareerDevelopmentStore.load(jdbc,good.careerId())).isNotNull();
        assertThat(careers.get(good.careerId()).compatibility().status()).isEqualTo("SUPPORTED");
        var day=calendar.view(good).state();calendar.advance(good,CareerApiV1Dtos.ADVANCE_REQUEST_SCHEMA,day.calendarRevision(),"ADVANCE_ONE_DAY",UUID.randomUUID().toString());
        assertThat(calendar.view(good).state().currentDate()).isAfter(day.currentDate());
        assertThat(careers.list().careers()).anyMatch(c->c.career().careerId().equals(id)&&c.compatibility().status().equals("UNSUPPORTED"));
        assertThat(CareerRosterStore.saved(jdbc,id,year)).isNull();
        preserved.forEach((table,rows)->assertThat(jdbc.queryForList("SELECT * FROM "+table+" WHERE career_id=?",id)).as(table).isEqualTo(rows));
        startup.recover();assertThat(CareerRosterStore.saved(jdbc,id,year)).isNull();
        String digest=jdbc.queryForObject("SELECT directory_hash FROM career_player_directory WHERE career_id=?",String.class,id);
        jdbc.update("UPDATE career_player_directory SET directory_hash=? WHERE career_id=?","0".repeat(64),id);
        assertThatThrownBy(startup::recover).isInstanceOf(IllegalStateException.class).hasMessage("DIRECTORY_INTEGRITY");
        jdbc.update("UPDATE career_player_directory SET directory_hash=? WHERE career_id=?",digest,id);
    }
    @Test void registrationWithoutFixturesRepairsThroughMarketDatesAndKeepsIndependentGates() {
        var c=careers.create(new CareerApiV1Dtos.CreateRequest(CareerApiV1Dtos.CREATE_REQUEST_SCHEMA,"등록 전 명부 복구","감독","GEN",UUID.randomUUID().toString())).career().career();
        String id=c.careerId();int year=2027;var date=java.time.LocalDate.of(year,3,16);
        var readyMarket=new java.util.concurrent.atomic.AtomicReference<String>();var readyRoster=new java.util.concurrent.atomic.AtomicReference<String>();
        var calendarStore=new CareerCalendarRelationalStore(jdbc,new org.springframework.jdbc.datasource.DataSourceTransactionManager(jdbc.getDataSource()),new CareerCalendarTemplate(new com.fasterxml.jackson.databind.ObjectMapper()));
        var template=new CareerCalendarTemplate(new com.fasterxml.jackson.databind.ObjectMapper());
        CareerOperatingDateFixture.fresh(jdbc,id,date.minusDays(1));
        var initial=calendar.view(c).state();String setup=UUID.randomUUID().toString();
        calendarStore.execute(setup,id,initial.calendarRevision(),"ADVANCE_ONE_DAY",template.advancePayloadHash(id,initial.calendarRevision(),"ADVANCE_ONE_DAY"),row->{
            // Controlled qualification preparation: no claim that these preceding seasons ran through the engine.
            CareerMarketStore.processThrough(jdbc,id,date);
            for(var event:new ArrayList<>(CareerOverseasQualification.required("FIRST_STAND")))sealControlledRegional(id,event);
            sealControlledRegional(id,CareerOverseasRules.Event.AMERICAS_CUP);
            jdbc.update("UPDATE career_competition_fixture SET lifecycle_status='COMPLETED' WHERE career_id=? AND scheduled_date<=?",id,date);
            jdbc.update("UPDATE career_competition_instance SET lifecycle_status='COMPLETED',blocking_reason=NULL WHERE career_id=? AND competition_id='LCK_CUP'",id);
            var source=jdbc.queryForObject("SELECT match_id FROM career_competition_fixture WHERE career_id=? AND competition_id='LCK_CUP' ORDER BY match_order LIMIT 1",String.class,id);
            for(int i=1;i<=2;i++)jdbc.update("INSERT INTO career_competition_output VALUES (?,?,?,?,?,?,?,CURRENT_TIMESTAMP)",id,year,"LCK_CUP","FIRST_STAND_LCK_SEED_"+i,i==1?"GEN":"T1",source,"a".repeat(64));
            competitions.refreshAllInstanceHashes(id,year);competitions.refreshCycleHash(id,year);
            var old=CareerMarketStore.load(jdbc,id);var engine=CareerMarketStore.engine(jdbc,id,year,old);
            readyMarket.set(CareerRosterStore.write(old.state()));readyRoster.set(CareerRosterStore.write(CareerRosterStore.saved(jdbc,id,year).state()));
            String top=engine.lineups.get("LPL:AL").stream().filter(pid->engine.player(pid).position()==com.lolfm.domain.Position.TOP).findFirst().orElseThrow();
            engine.release("LPL:AL",top,null,date);CareerMarketStore.persist(jdbc,id,year,old,engine);
            competitions.reconcileInternational(id,year);
            return new CareerCalendarRelationalStore.AdvanceMutation(date,template.eventCursor(template.project(year),date),row.lastProcessedEventId(),row.lastProcessedDate(),"ACTIVE",null,true,false,200,null,false);
        });
        var before=CareerMarketStore.load(jdbc,id);var waiting=calendar.view(c);
        assertThat(waiting.competition().currentCompetition().competitionId()).isEqualTo("FIRST_STAND");
        var reason=waiting.competition().currentCompetition().registrationWait();
        assertThat(reason.code()).isEqualTo("ROSTER_REPAIR_REQUIRED");assertThat(reason.teamId()).isEqualTo("LPL:AL");assertThat(reason.responsibility()).isEqualTo("AI_CLUB");
        assertThat(waiting.allowedAdvanceModes()).hasSize(2);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM career_competition_fixture WHERE career_id=? AND competition_id='FIRST_STAND'",Integer.class,id)).isZero();
        assertThat(CareerMarketStore.load(jdbc,id)).isEqualTo(before);
        // Another legal due fixture must still be played before repair may advance the day.
        var unrelated=competitions.load(id,year).fixtures().stream().filter(f->f.competitionId().equals("LEC_VERSUS")).findFirst().orElseThrow();
        transaction().executeWithoutResult(t->{jdbc.update("UPDATE career_competition_fixture SET lifecycle_status='READY',scheduled_date=? WHERE career_id=? AND fixture_id=?",date,id,unrelated.fixtureId());competitions.refreshInstanceHash(id,year,"LEC_VERSUS");competitions.refreshCycleHash(id,year);});
        assertThat(calendar.view(c).allowedAdvanceModes()).isEmpty();
        transaction().executeWithoutResult(t->{jdbc.update("UPDATE career_competition_fixture SET lifecycle_status='COMPLETED',scheduled_date=? WHERE career_id=? AND fixture_id=?",unrelated.date(),id,unrelated.fixtureId());competitions.refreshInstanceHash(id,year,"LEC_VERSUS");competitions.refreshCycleHash(id,year);});
        // Controlled repair-complete boundary: preserve the old wait while restoring an already legal squad.
        // Roll back this branch so the actual market/date repair below still begins with the missing TOP.
        transaction().executeWithoutResult(t->{
            jdbc.update("UPDATE career_market_state SET state_json=?,state_hash=? WHERE career_id=?",readyMarket.get(),CareerRosterStore.hash(readyMarket.get()),id);
            jdbc.update("UPDATE career_roster_state SET state_json=?,state_hash=? WHERE career_id=? AND season_year=?",readyRoster.get(),CareerRosterStore.hash(readyRoster.get()),id,year);
            var legal=CareerMarketStore.load(jdbc,id);var ready=calendar.view(c);
            assertThat(ready.competition().currentCompetition().registrationWait()).isNull();assertThat(ready.allowedAdvanceModes()).hasSize(2);
            assertThat(CareerInternationalCompetition.load(competitions,id,year,"FIRST_STAND")).isNull();assertThat(CareerMarketStore.load(jdbc,id)).isEqualTo(legal);
            jdbc.update("UPDATE career_competition_fixture SET lifecycle_status='READY',scheduled_date=? WHERE career_id=? AND fixture_id=?",date,id,unrelated.fixtureId());competitions.refreshInstanceHash(id,year,"LEC_VERSUS");competitions.refreshCycleHash(id,year);
            assertThat(calendar.view(c).allowedAdvanceModes()).isEmpty();
            jdbc.update("UPDATE career_competition_fixture SET lifecycle_status='COMPLETED',scheduled_date=? WHERE career_id=? AND fixture_id=?",unrelated.date(),id,unrelated.fixtureId());competitions.refreshInstanceHash(id,year,"LEC_VERSUS");competitions.refreshCycleHash(id,year);
            calendar.advance(c,CareerApiV1Dtos.ADVANCE_REQUEST_SCHEMA,ready.state().calendarRevision(),"ADVANCE_ONE_DAY",UUID.randomUUID().toString());
            assertThat(CareerInternationalCompetition.load(competitions,id,year,"FIRST_STAND")).isNotNull();
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM career_opportunity_registration WHERE career_id=? AND competition_id='FIRST_STAND'",Integer.class,id)).isEqualTo(1);
            t.setRollbackOnly();
        });
        assertThat(CareerMarketStore.load(jdbc,id)).isEqualTo(before);assertThat(CareerInternationalCompetition.load(competitions,id,year,"FIRST_STAND")).isNull();
        // Ordinary budget-checked offer, then the actual Calendar command settles the player's response and start.
        transaction().executeWithoutResult(t->{var old=CareerMarketStore.load(jdbc,id);var m=CareerMarketStore.engine(jdbc,id,year,old);String candidate=m.freeAgents.stream().filter(pid->m.player(pid).position()==com.lolfm.domain.Position.TOP).sorted().findFirst().orElseThrow();var start=date.plusDays(CareerMarketPolicy.FA_START_DELAY_DAYS);m.submit("LPL:AL",candidate,new CareerMarketState.Terms(start,start.plusYears(1).minusDays(1),m.demand(candidate)*150/100,0,CareerMarketState.Role.STARTER),null,date);CareerMarketStore.persist(jdbc,id,year,old,m);});
        int days=0;
        while(CareerInternationalCompetition.load(competitions,id,year,"FIRST_STAND")==null&&days++<12) {
            var state=calendar.view(c).state();String command=UUID.randomUUID().toString();
            var result=calendar.advance(c,CareerApiV1Dtos.ADVANCE_REQUEST_SCHEMA,state.calendarRevision(),"ADVANCE_ONE_DAY",command);
            var marketAfter=CareerMarketStore.load(jdbc,id);
            assertThat(calendar.advance(c,CareerApiV1Dtos.ADVANCE_REQUEST_SCHEMA,state.calendarRevision(),"ADVANCE_ONE_DAY",command).replayed()).isTrue();
            assertThat(CareerMarketStore.load(jdbc,id)).isEqualTo(marketAfter);
            assertThat(result.calendar().state().currentDate()).isAfter(state.currentDate());
        }
        var registered=CareerInternationalCompetition.load(competitions,id,year,"FIRST_STAND");assertThat(registered).isNotNull();assertThat(registered.rosters().teams()).containsKey("LPL:AL");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM career_opportunity_registration WHERE career_id=? AND competition_id='FIRST_STAND'",Integer.class,id)).isEqualTo(1);
        calendar.view(c);assertThat(CareerInternationalCompetition.load(competitions,id,year,"FIRST_STAND")).isEqualTo(registered);
        System.out.println("REGISTRATION_REPAIR days="+days+" AL="+registered.rosters().roster("LPL:AL").players().stream().map(CompetitionRosterSnapshot.Starter::nickname).toList());
    }
    private void sealControlledRegional(String id,CareerOverseasRules.Event event) {
        var input=CareerOverseasTournamentTest.input(event);var state=new CareerOverseasStore.State(CareerOverseasRules.PROJECTION_VERSION,CareerOverseasStore.activation(jdbc,id).ruleHash(),input,CareerOverseasTournamentTest.finish(input,new TreeMap<>()));String text=CareerRosterStore.write(state);
        jdbc.update("MERGE INTO career_overseas_state KEY(career_id,season_year,competition_id) VALUES (?,?,?,?,?)",id,2027,event.name(),text,CareerRosterStore.hash(text));
        jdbc.update("UPDATE career_competition_instance SET lifecycle_status='COMPLETED',blocking_reason=NULL WHERE career_id=? AND competition_id=?",id,event.name());
    }
    @Test void correctionAdoptsUnusedEventsAndPreservesStartedAndCompletedHistory() {
        var c=careers.create(new CareerApiV1Dtos.CreateRequest(CareerApiV1Dtos.CREATE_REQUEST_SCHEMA,"해외 규칙 적용 경계","감독","GEN",UUID.randomUUID().toString())).career().career();String id=c.careerId();int year=2027;
        transaction().executeWithoutResult(t->{
            for(var event:List.of(CareerOverseasRules.Event.LCP_SPLIT_1,CareerOverseasRules.Event.LPL_SPLIT_1)) {
                var old=CareerOverseasStore.load(jdbc,id,year,event);var legacy=new CareerOverseasStore.State(CareerOverseasRules.VERSION,old.ruleHash(),old.input(),CareerOverseasTournament.project(old.input(),Map.of(),CareerOverseasRules.VERSION));String json=CareerRosterStore.write(legacy);
                jdbc.update("UPDATE career_overseas_state SET state_json=?,state_hash=? WHERE career_id=? AND season_year=? AND competition_id=?",json,CareerRosterStore.hash(json),id,year,event.name());
            }
            sealControlledRegional(id,CareerOverseasRules.Event.LEC_VERSUS);
            var sealed=CareerOverseasStore.load(jdbc,id,year,CareerOverseasRules.Event.LEC_VERSUS);
            String historical=CareerRosterStore.write(new CareerOverseasStore.State(CareerOverseasRules.VERSION,sealed.ruleHash(),sealed.input(),sealed.plan()));
            jdbc.update("UPDATE career_overseas_state SET state_json=?,state_hash=? WHERE career_id=? AND season_year=? AND competition_id='LEC_VERSUS'",historical,CareerRosterStore.hash(historical),id,year);
            competitions.refreshAllInstanceHashes(id,year);competitions.refreshCycleHash(id,year);
        });
        var unused=competitions.load(id,year).fixtures().stream().filter(f->f.competitionId().equals("LCP_SPLIT_1")).toList();
        var target=competitions.load(id,year).fixtures().stream().filter(f->f.competitionId().equals("LPL_SPLIT_1")&&!Set.of("LPL:OMG","LPL:UP").contains(f.firstTeamCode())&&!Set.of("LPL:OMG","LPL:UP").contains(f.secondTeamCode())).findFirst().orElseThrow();
        transaction().executeWithoutResult(t->{jdbc.update("UPDATE career_competition_fixture SET scheduled_date=? WHERE career_id=? AND fixture_id=?",CareerMarketStore.date(jdbc,id),id,target.fixtureId());competitions.refreshInstanceHash(id,year,"LPL_SPLIT_1");competitions.refreshCycleHash(id,year);});
        var bound=competitions.bindFixture(id,year,"LPL_SPLIT_1",target.matchId(),snapshots.currentSnapshot(snapshots.currentTeamCodes()),snapshots.currentResourceProvenanceHash());
        var beforeFixture=competitions.load(id,year).fixtures().stream().filter(f->f.fixtureId().equals(target.fixtureId())).findFirst().orElseThrow();
        var complete=CareerOverseasStore.load(jdbc,id,year,CareerOverseasRules.Event.LEC_VERSUS);var marketBefore=CareerMarketStore.load(jdbc,id);
        transaction().executeWithoutResult(t->CareerOverseasStore.reconcile(competitions,id,year));
        assertThat(CareerOverseasStore.load(jdbc,id,year,CareerOverseasRules.Event.LCP_SPLIT_1).policyVersion()).isEqualTo(CareerOverseasRules.PROJECTION_VERSION);
        assertThat(CareerOverseasStore.load(jdbc,id,year,CareerOverseasRules.Event.LPL_SPLIT_1).policyVersion()).isEqualTo(CareerOverseasRules.VERSION);
        assertThat(CareerOverseasStore.load(jdbc,id,year,CareerOverseasRules.Event.LEC_VERSUS)).isEqualTo(complete);
        assertThat(competitions.loadBinding(id,year,"LPL_SPLIT_1",target.matchId()).canonicalText()).isEqualTo(bound.canonicalText());
        assertThat(competitions.load(id,year).fixtures().stream().filter(f->f.fixtureId().equals(target.fixtureId())).findFirst().orElseThrow()).isEqualTo(beforeFixture);
        var adopted=competitions.load(id,year).fixtures().stream().filter(f->f.competitionId().equals("LCP_SPLIT_1")).toList();
        assertThat(adopted.stream().map(f->List.of(f.fixtureId(),f.rootSeed())).toList()).isEqualTo(unused.stream().map(f->List.of(f.fixtureId(),f.rootSeed())).toList());
        assertThat(CareerMarketStore.load(jdbc,id)).isEqualTo(marketBefore);
    }
    @Test void newCareerActivatesSeventeenEventsAndKeepsReadOnlyViews() {
        var c=careers.create(new CareerApiV1Dtos.CreateRequest(CareerApiV1Dtos.CREATE_REQUEST_SCHEMA,"해외 리그 저장 연결","감독","T1",UUID.randomUUID().toString())).career().career();
        String id=c.careerId();int year=2027;
        var saved=CareerMarketStore.load(jdbc,id);var cycle=competitions.load(id,year);
        assertThat(saved.state().accounts()).hasSize(59).containsKeys("LPL:OMG","LPL:UP","LEC:LR").doesNotContainKey("LEC:KCB");
        assertThat(cycle.competitions().stream().filter(i->CareerOverseasRules.isOverseas(i.competitionId()))).hasSize(17);
        var v=overseas.view(id,year,"LEC","LEC_VERSUS");
        assertThat(v.active()).isTrue();assertThat(v.activation().activationYear()).isEqualTo(year);
        assertThat(v.events()).hasSize(1);assertThat(v.events().getFirst().result().regularRanking()).isEmpty();
        assertThat(v.events().getFirst().fixtures()).hasSize(66);
        var engine=CareerMarketStore.engine(jdbc,id,year,saved);
        var guest=CareerOverseasRoster.roster(engine,"LEC:KCB");
        assertThat(guest.players()).hasSize(5).allSatisfy(p->assertThat(engine.members.get(p.playerId()).ownerTeam()).isEqualTo("LEC:KC"));
        assertThat(guest.players().stream().map(CompetitionRosterSnapshot.Starter::playerId)).doesNotContainAnyElementsOf(engine.lineups.get("LEC:KC"));
        for(String league:List.of("LPL","LEC","LCP","CBLOL","LCS"))assertThat(overseas.view(id,year,league,null).events()).isNotEmpty();
        overseas.recover();
        assertThat(CareerMarketStore.load(jdbc,id)).isEqualTo(saved);
        assertThat(competitions.load(id,year)).isEqualTo(cycle);
        assertThatThrownBy(()->CareerOverseasQualification.select(competitions,id,year,"FIRST_STAND")).isInstanceOf(CareerOverseasQualification.Waiting.class).hasMessageContaining("LPL_SPLIT_1");
        var snapshot=snapshots.currentSnapshot(snapshots.currentTeamCodes());
        for(var event:List.of(CareerOverseasRules.Event.LPL_SPLIT_1,CareerOverseasRules.Event.LEC_VERSUS,CareerOverseasRules.Event.LCP_SPLIT_1,CareerOverseasRules.Event.CBLOL_COPA,CareerOverseasRules.Event.LCS_LOCK_IN)){
            var target=competitions.load(id,year).fixtures().stream().filter(f->f.competitionId().equals(event.name()))
                .filter(f->!Set.of("LPL:OMG","LPL:UP","LEC:LR").contains(f.firstTeamCode())&&!Set.of("LPL:OMG","LPL:UP","LEC:LR").contains(f.secondTeamCode())).findFirst().orElseThrow();
            transaction().executeWithoutResult(t->{jdbc.update("UPDATE career_competition_fixture SET scheduled_date=? WHERE career_id=? AND competition_id=? AND match_id=?",CareerMarketStore.date(jdbc,id),id,event.name(),target.matchId());competitions.refreshInstanceHash(id,year,event.name());competitions.refreshCycleHash(id,year);});
            var binding=competitions.bindFixture(id,year,event.name(),target.matchId(),snapshot,snapshots.currentResourceProvenanceHash());
            assertThat(binding.frozenRosters().teams()).hasSize(2);assertThat(binding.executionMode()).isEqualTo("FULL_AUTO");
            // Controlled completion checks each adapter. Actual engine evidence is the separate BO matrix.
            if(event==CareerOverseasRules.Event.LCS_LOCK_IN){
                var beforeCycle=competitions.load(id,year);var beforeMarket=CareerMarketStore.load(jdbc,id);var beforeGrowth=CareerDevelopmentStore.load(jdbc,id);
                assertThatThrownBy(()->transaction().executeWithoutResult(t->{CareerCompetitionTestSupport.applySyntheticVerifiedCompletion(competitions,binding,binding.firstTeamCode());throw new IllegalStateException("OVERSEAS_ROLLBACK");})).hasMessage("OVERSEAS_ROLLBACK");
                assertThat(competitions.load(id,year)).isEqualTo(beforeCycle);assertThat(CareerMarketStore.load(jdbc,id)).isEqualTo(beforeMarket);assertThat(CareerDevelopmentStore.load(jdbc,id)).isEqualTo(beforeGrowth);
            }
            transaction().executeWithoutResult(t->CareerCompetitionTestSupport.applySyntheticVerifiedCompletion(competitions,binding,binding.firstTeamCode()));
            assertThat(competitions.hasAppliedCompletion(binding)).isTrue();assertThat(overseas.view(id,year,event.league,event.name()).events().getFirst().scores()).containsKey(target.matchId());
        }
        // New organizations must obtain missing roles through normal, budget-checked AI offers.
        var recruitment=CareerMarketStore.engine(jdbc,id,year,CareerMarketStore.load(jdbc,id));
        recruitment.advance(CareerMarketStore.date(jdbc,id).plusDays(42));
        for(String team:List.of("LPL:OMG","LPL:UP","LEC:LR")){
            assertThat(CareerOverseasRoster.roster(recruitment,team).players()).hasSize(5);
            int initialRoles=(int)CareerOverseasRoster.candidates(engine,team).stream().map(pid->engine.player(pid).position()).distinct().count();
            long negotiated=recruitment.contracts.values().stream().filter(cn->team.equals(cn.team())&&Set.of("NEGOTIATED_FREE_AGENT","PAID_TRANSFER_AGREEMENT").contains(cn.origin())).count()
                +recruitment.tradeEngine.loans.values().stream().filter(loan->team.equals(loan.borrowingTeam())).count();
            assertThat(negotiated).as(team+" normal FA/transfer/loan recruitment").isGreaterThanOrEqualTo(5-initialRoles);
            System.out.println("OVERSEAS_RECRUITMENT "+team+" initialRoles="+initialRoles+" negotiated="+negotiated+" roster="+CareerRosterStore.write(CareerOverseasRoster.roster(recruitment,team).players().stream().map(CompetitionRosterSnapshot.Starter::nickname).toList()));
        }
    }
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(ints={1,3,5})
    void actualAutoSeriesUpdatesBothRostersAndReplaysOnce(int bestOf) {
        var c=careers.create(new CareerApiV1Dtos.CreateRequest(CareerApiV1Dtos.CREATE_REQUEST_SCHEMA,"해외 실제 BO"+bestOf,"감독","T1",UUID.randomUUID().toString())).career().career();
        String id=c.careerId();int year=2027;String event=bestOf==1?"LEC_VERSUS":bestOf==3?"LPL_SPLIT_1":"LCP_SPLIT_1";
        var snapshot=snapshots.currentSnapshot(snapshots.currentTeamCodes());String provenance=snapshots.currentResourceProvenanceHash();
        if(bestOf==5){
            // Only the 28 preceding RR scores are controlled. The BO5 below uses the production runner.
            for(var f:competitions.load(id,year).fixtures().stream().filter(f->f.competitionId().equals(event)&&f.stageId().equals("REGULAR")).toList()){
                var cycle=competitions.load(id,year);
                var binding=CareerCompetitionSeriesBindingV1.createInternational(cycle,competitions.instance(id,year,event),f,"LCK:T1",CareerOverseasStore.activation(jdbc,id).ruleHash(),snapshot,provenance,CareerOverseasRoster.pair(jdbc,id,year,f.firstTeamCode(),f.secondTeamCode()));
                transaction().executeWithoutResult(t->{
                    jdbc.update("INSERT INTO career_competition_series_binding(binding_hash,career_id,calendar_season_year,competition_id,match_id,fixture_id,series_id,execution_mode,binding_schema,binding_canonical,lifecycle_status,created_at,updated_at) VALUES (?,?,?,?,?,?,?,?,?,?,'CREATED',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",binding.bindingHash(),id,year,event,f.matchId(),f.fixtureId(),f.seriesId(),f.executionMode(),CareerCompetitionSeriesBindingV1.SCHEMA,binding.canonicalText());
                    CareerCompetitionTestSupport.applySyntheticVerifiedCompletion(competitions,binding,f.firstTeamCode());
                });
            }
            assertThat(CareerMarketStore.load(jdbc,id).state().management().appearances()).isEmpty();
        }
        var target=competitions.load(id,year).fixtures().stream().filter(f->f.competitionId().equals(event)&&!f.lifecycleStatus().equals("COMPLETED")&&f.seriesFormat().equals("BO"+bestOf))
                .filter(f->!Set.of("LPL:OMG","LPL:UP","LEC:LR").contains(f.firstTeamCode())&&!Set.of("LPL:OMG","LPL:UP","LEC:LR").contains(f.secondTeamCode())).findFirst().orElseThrow();
        transaction().executeWithoutResult(t->{jdbc.update("UPDATE career_competition_fixture SET scheduled_date=? WHERE career_id=? AND competition_id=? AND match_id=?",CareerMarketStore.date(jdbc,id),id,event,target.matchId());competitions.refreshInstanceHash(id,year,event);competitions.refreshCycleHash(id,year);});
        if(bestOf==1){calendar.advance(c,CareerApiV1Dtos.ADVANCE_REQUEST_SCHEMA,0,"ADVANCE_ONE_DAY",UUID.randomUUID().toString());assertThat(calendar.view(c).blockingReason()).isEqualTo("AUTO_COMPETITION_FIXTURE_REQUIRED");}
        var cycle=competitions.load(id,year);String command=UUID.randomUUID().toString();var growthBefore=CareerDevelopmentStore.load(jdbc,id);
        var expectedGrowth=new CareerDevelopmentEngine(CareerRosterStore.baseDirectory(jdbc,id),growthBefore.state());
        var queued=execution.startOrResume(c,year,CareerMarketStore.date(jdbc,id),cycle.revision(),command);
        var done=execution.executeAutoJob(queued.jobId());assertThat(done.status()).isEqualTo("COMPLETED");
        if(bestOf==1){assertThat(calendar.view(c).blockingReason()).isNull();assertThat(calendar.view(c).allowedAdvanceModes()).containsExactly("ADVANCE_ONE_DAY","ADVANCE_TO_NEXT_EVENT");}
        var binding=competitions.loadBinding(id,year,event,target.matchId());assertThat(competitions.hasAppliedCompletion(binding)).isTrue();
        var result=overseas.result(id,year,event,target.matchId());assertThat(result.games()).hasSizeBetween(bestOf/2+1,bestOf);
        var marketAfter=CareerMarketStore.load(jdbc,id);var growthAfter=CareerDevelopmentStore.load(jdbc,id);
        var appearance=marketAfter.state().management().appearances().values().stream().filter(a->a.seriesId().equals(binding.boundSeriesId())).findFirst().orElseThrow();
        assertThat(appearance.squad()).isEqualTo("FIRST_TEAM");assertThat(appearance.competitionId()).isEqualTo(event);
        assertThat(appearance.opportunities().stream().filter(CareerManagementState.Opportunity::selected)).hasSize(10);
        for(var game:result.games())for(var pick:game.picks())expectedGrowth.game(pick.playerId(),pick.champion(),pick.position(),appearance.date(),year);
        // The unchanged growth policy includes PA/missing-PA caps. Compare full state, including every unplayed candidate.
        assertThat(growthAfter.state()).isEqualTo(expectedGrowth.state());
        int totalGrowth=0;
        for(var roster:binding.frozenRosters().teams().values())for(var player:roster.players()){
            var performance=CareerAppearanceStore.performance(jdbc,id,year,player.playerId());assertThat(performance).hasSize(1);
            var played=performance.getFirst();assertThat(played.sets()).isEqualTo(result.games().size());assertThat(played.champions().values().stream().mapToInt(Integer::intValue).sum()).isEqualTo(played.sets());var before=growthBefore.state().players().get(player.playerId());var after=growthAfter.state().players().get(player.playerId());
            assertThat(played.internalGain()).isEqualTo(CareerDevelopmentPolicy.sum(after)-CareerDevelopmentPolicy.sum(before));assertThat(played.proficiencyGain()).isNotNegative();assertThat(after.fatigue()).isEqualTo(Math.min(1000,before.fatigue()+played.sets()*CareerDevelopmentPolicy.GAME_FATIGUE));totalGrowth+=played.internalGain();
            System.out.println("OVERSEAS_GROWTH "+player.nickname()+" BO"+bestOf+" before="+CareerDevelopmentPolicy.sum(before)+" after="+CareerDevelopmentPolicy.sum(after)+" fatigue="+before.fatigue()+"->"+after.fatigue()+" "+CareerRosterStore.write(played));
        }
        assertThat(totalGrowth).isPositive();
        assertThat(overseas.view(id,year,CareerOverseasRules.Event.valueOf(event).league,event).events().getFirst().scores()).containsKey(target.matchId());
        execution.startOrResume(c,year,CareerMarketStore.date(jdbc,id),cycle.revision(),command);execution.executeAutoJob(queued.jobId());
        assertThat(CareerMarketStore.load(jdbc,id)).isEqualTo(marketAfter);assertThat(CareerDevelopmentStore.load(jdbc,id)).isEqualTo(growthAfter);
        System.out.println("OVERSEAS_ACTUAL BO"+bestOf+" "+CareerRosterStore.write(appearance));
    }

}
