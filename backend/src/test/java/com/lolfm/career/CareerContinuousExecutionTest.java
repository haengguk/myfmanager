package com.lolfm.career;
import static com.lolfm.career.CareerContinuousProgress.*;
import static org.assertj.core.api.Assertions.*;
import java.time.*;
import java.util.*;
import com.lolfm.dto.CareerApiV1Dtos;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.NONE,properties={"spring.main.banner-mode=off","logging.level.root=ERROR","spring.main.lazy-initialization=true","lolfm.career.continuous.background.enabled=false","lolfm.career.competition.background.enabled=false"})
class CareerContinuousExecutionTest {
    @Autowired CareerInboxService inbox;@Autowired CareerMarketStore market;
    @Autowired CareerApplicationService careers;@Autowired CareerCalendarApplicationService calendar;
    @Autowired CareerContinuousApplicationService service;@Autowired CareerContinuousStore store;@Autowired JdbcTemplate jdbc;
    @Autowired com.lolfm.application.SeriesApiV1Facade series;
    @Autowired CareerCompetitionRelationalStore competitions;@Autowired CareerCompetitionExecutionService execution;
    CareerRelationalStore.CareerRow create(String name){return careers.create(new CareerApiV1Dtos.CreateRequest(CareerApiV1Dtos.CREATE_REQUEST_SCHEMA,name,"감독","GEN",UUID.randomUUID().toString())).career().career();}
    static Command start(Mode mode,LocalDate date){return new Command(REQUEST_SCHEMA,"START",UUID.randomUUID().toString(),null,null,mode,date);}
    Command action(String career,String action){var r=service.view(career).run();return new Command(REQUEST_SCHEMA,action,UUID.randomUUID().toString(),r.runId,r.revision,null,null);}
    @Test void dailyTargetReplayConflictPauseAndManualBusy() {
        var c=create("연속 날짜 경계");var date=calendar.currentDate(c);CareerInboxStore.add(jdbc,c.careerId(),date.getYear(),CareerInboxStorageTest.item("information",date));var request=start(Mode.TARGET_DATE,date.plusDays(1));
        assertThatThrownBy(()->service.command(c.careerId(),start(Mode.TARGET_DATE,date.minusDays(1)))).isInstanceOf(CareerException.class);
        var accepted=service.command(c.careerId(),request);
        assertThat(service.command(c.careerId(),new Command(REQUEST_SCHEMA,"START",request.clientCommandId().toUpperCase(java.util.Locale.ROOT),null,null,request.mode(),request.targetDate())).receipt()).isEqualTo(accepted.receipt());
        assertThat(service.command(c.careerId(),request).receipt()).isEqualTo(accepted.receipt());
        assertThatThrownBy(()->service.command(c.careerId(),new Command(REQUEST_SCHEMA,"START",request.clientCommandId(),null,null,Mode.TARGET_DATE,date.plusDays(2)))).isInstanceOf(CareerException.class);
        var day=calendar.view(c).state();
        assertThatThrownBy(()->calendar.advance(c,CareerApiV1Dtos.ADVANCE_REQUEST_SCHEMA,day.calendarRevision(),"ADVANCE_ONE_DAY",UUID.randomUUID().toString())).isInstanceOfSatisfying(CareerException.class,e->assertThat(e.type()).isEqualTo(CareerException.Type.CONTINUOUS_BUSY));
        service.step(c.careerId(),"test");
        var pause=service.command(c.careerId(),new Command(REQUEST_SCHEMA,"PAUSE",UUID.randomUUID().toString(),accepted.receipt().runId(),accepted.receipt().resultingRevision(),null,null));assertThat(pause.progress().run().status).isEqualTo(Status.PAUSE_REQUESTED);
        service.step(c.careerId(),"test");assertThat(service.view(c.careerId()).run().status).isEqualTo(Status.PAUSED);
        assertThat(calendar.currentDate(c)).isEqualTo(date);
        service.command(c.careerId(),action(c.careerId(),"RESUME"));
        for(int i=0;i<8&&active(service.view(c.careerId()).run().status);i++)service.step(c.careerId(),"test");
        var done=service.view(c.careerId());assertThat(done.run().status).isEqualTo(Status.COMPLETED);assertThat(done.currentDate()).isEqualTo(date.plusDays(1));assertThat(done.run().completedDates).isEqualTo(1);
        assertThat(inbox.feed(c.careerId(),date.getYear(),"",false,null,0).unread()).isGreaterThanOrEqualTo(1);
        assertThat(service.command(c.careerId(),request).receipt()).isEqualTo(accepted.receipt());
        assertThat(CareerMarketStore.load(jdbc,c.careerId()).state().processedThrough()).isEqualTo(done.currentDate());
    }
    @Test void inboxReadsDoNotResolveCounteroffersOrAlterTheContinuousLease() throws Exception {
        var c=create("소식함 응답 경계");String id=c.careerId();var day=calendar.view(c).state();var old=CareerMarketStore.load(jdbc,id);
        String player=old.state().contracts().values().stream().filter(v->v.team().equals("LCK:GEN")&&v.status()==CareerMarketState.ContractStatus.ACTIVE).findFirst().orElseThrow().playerId();
        var contract=old.state().contracts().values().stream().filter(v->v.playerId().equals(player)&&v.status()==CareerMarketState.ContractStatus.ACTIVE).findFirst().orElseThrow();
        // Small source fixture, then the actual existing WITHDRAW command below.
        store.tx.executeWithoutResult(t->{store.lock(id);var engine=CareerMarketStore.engine(jdbc,id,day.seasonYear(),old);
            var offer=new CareerMarketState.Offer("inbox-offer",player,"LCK:GEN",contract.terms(),day.currentDate(),day.currentDate(),day.currentDate(),day.currentDate().plusDays(5),7,CareerMarketState.OfferStatus.COUNTER,null,1,contract.terms().annualSalary(),"합성 준비 역제안");
            engine.offers.put(offer.offerId(),offer);CareerMarketStore.persist(jdbc,id,day.seasonYear(),old,engine);
        });
        var first=inbox.feed(id,day.seasonYear(),"",false,null,0);assertThat(first.decisions()).filteredOn(d->d.type()==Reason.CONTRACT_RESPONSE).hasSize(1);
        var counter=first.items().stream().filter(e->e.item().kind().equals("CONTRACT_RESPONSE")).findFirst().orElseThrow();
        assertThat(first.decisions()).filteredOn(d->d.type()==Reason.CONTRACT_RESPONSE).allSatisfy(d->{assertThat(d.title()).contains(CareerRosterStore.baseDirectory(jdbc,id).players().get(player).nickname());assertThat(d.link().sourceId()).isEqualTo("inbox-offer");});
        service.command(id,start(Mode.NEXT_MANAGED_MATCH,null));var lease=jdbc.queryForList("SELECT * FROM career_continuous_run WHERE career_id=?",id);
        inbox.markRead(id,new CareerInboxService.ReadRequest(counter.sequence(),null,null,null,false));inbox.markRead(id,new CareerInboxService.ReadRequest(counter.sequence(),null,null,null,false));
        assertThat(jdbc.queryForList("SELECT * FROM career_continuous_run WHERE career_id=?",id)).isEqualTo(lease);assertThat(calendar.view(c).state().calendarRevision()).isEqualTo(day.calendarRevision());
        service.step(id,"inbox-test");assertThat(service.view(id).run().stop.reason()).isEqualTo(Reason.CONTRACT_RESPONSE);
        assertThat(inbox.feed(id,day.seasonYear(),"",false,null,0).decisions()).anyMatch(d->d.type()==Reason.CONTRACT_RESPONSE);
        java.nio.file.Files.createDirectories(java.nio.file.Path.of("build/reports/career-inbox"));
        jdbc.update("UPDATE career_inbox_item SET read_flag=FALSE WHERE career_id=?",id);
        jdbc.execute("SCRIPT TO 'build/reports/career-inbox/browser.sql'");
        java.nio.file.Files.writeString(java.nio.file.Path.of("build/reports/career-inbox/browser-career.txt"),id);
        var current=market.view(id,day.seasonYear());
        market.command(id,new CareerMarketStore.Request(CareerFinanceStore.MARKET_COMMAND,day.seasonYear(),current.revision(),"WITHDRAW",null,"inbox-offer",null,null,null,UUID.randomUUID().toString()));
        assertThat(inbox.feed(id,day.seasonYear(),"",false,null,0).decisions()).noneMatch(d->d.type()==Reason.CONTRACT_RESPONSE);
        assertThat(inbox.detail(id,counter.sequence()).currentStatus()).isEqualTo("WITHDRAWN");assertThat(service.view(id).run().status).isEqualTo(Status.STOPPED);
        store.tx.executeWithoutResult(t->{store.lock(id);var saved=CareerMarketStore.load(jdbc,id);var engine=CareerMarketStore.engine(jdbc,id,day.seasonYear(),saved);var previous=engine.offers.get("inbox-offer");engine.offers.put("inbox-offer",new CareerMarketState.Offer(previous.offerId(),previous.playerId(),previous.team(),previous.terms(),previous.submittedDate(),previous.responseDate(),previous.decisionDate(),previous.expiresDate(),8,CareerMarketState.OfferStatus.COUNTER,null,2,previous.terms().annualSalary()+1,"새 조건"));CareerMarketStore.persist(jdbc,id,day.seasonYear(),saved,engine);});
        var changed=inbox.feed(id,day.seasonYear(),"CONTRACT_RESPONSE",false,null,0);assertThat(changed.items()).hasSize(2);assertThat(changed.items().getFirst().read()).isFalse();assertThat(changed.decisions()).anyMatch(d->d.id().equals("OFFER:inbox-offer")&&d.revision().equals("8"));

    }
    @Test void realAutoBo3PausesAfterAppliedReceiptThenStopsAtPlayerAndAllowsEntry() throws Exception {
        long begin=System.nanoTime();var c=create("연속 실제 BO3");String id=c.careerId();int year=calendar.view(c).state().seasonYear();var date=calendar.currentDate(c);
        // Only schedule preparation: move one real Cup Auto and one real managed fixture to today.
        var rows=competitions.load(id,year).fixtures().stream().filter(f->f.competitionId().equals("LCK_CUP")&&f.lifecycleStatus().equals("READY")).toList();
        var auto=rows.stream().filter(f->"FULL_AUTO".equals(f.executionMode())).findFirst().orElseThrow();
        var player=rows.stream().filter(f->"PLAYER_CONTROLLED".equals(f.executionMode())).findFirst().orElseThrow();
        store.tx.executeWithoutResult(t->{store.lock(id);jdbc.update("UPDATE career_competition_fixture SET scheduled_date=? WHERE career_id=? AND fixture_id=?",date,id,auto.fixtureId());competitions.refreshInstanceHash(id,year,"LCK_CUP");competitions.refreshCycleHash(id,year);});
        java.nio.file.Files.createDirectories(java.nio.file.Path.of("build/reports/career-continuous"));
        jdbc.execute("SCRIPT TO 'build/reports/career-continuous/browser-before-auto.sql'");
        java.nio.file.Files.writeString(java.nio.file.Path.of("build/reports/career-continuous/browser-info.txt"),"career="+id+"\nplayerFixture="+player.fixtureId()+"\n");
        var request=start(Mode.NEXT_MANAGED_MATCH,null);service.command(id,request);
        for(int i=0;i<5&&service.view(id).run().status!=Status.WAITING;i++)service.step(id,"test");
        var waiting=service.view(id);assertThat(waiting.run().status).isEqualTo(Status.WAITING);String job=waiting.run().intent.jobId();String command=waiting.run().intent.commandId();
        service.command(id,action(id,"PAUSE"));service.step(id,"test");assertThat(service.view(id).run().status).isEqualTo(Status.PAUSE_REQUESTED);
        var result=execution.executeAutoJob(job);assertThat(result.status()).isEqualTo("COMPLETED");
        service.step(id,"test");var paused=service.view(id);assertThat(paused.run().status).isEqualTo(Status.PAUSED);assertThat(paused.run().completedSeries).isEqualTo(1);assertThat(paused.run().completedGames).isBetween(2,3);assertThat(paused.currentDate()).isEqualTo(date);
        assertThat(execution.executeAutoJob(job).status()).isEqualTo("COMPLETED");assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM career_competition_command WHERE career_id=? AND client_command_id=?",Integer.class,id,command)).isEqualTo(1);
        store.tx.executeWithoutResult(t->{store.lock(id);jdbc.update("UPDATE career_competition_fixture SET scheduled_date=? WHERE career_id=? AND fixture_id=?",date,id,player.fixtureId());competitions.refreshInstanceHash(id,year,"LCK_CUP");competitions.refreshCycleHash(id,year);});
        service.command(id,action(id,"RESUME"));service.step(id,"test");
        var stopped=service.view(id);assertThat(stopped.run().stop.reason()).isEqualTo(Reason.PLAYER_MATCH);assertThat(stopped.currentDate()).isEqualTo(date);
        var entry=execution.startOrResume(c,year,date,competitions.load(id,year).revision(),UUID.randomUUID().toString());assertThat(entry.executionMode()).isEqualTo("PLAYER_CONTROLLED");assertThat(entry.seriesId()).isNotBlank();
        var before=series.get(entry.seriesId());service.command(id,start(Mode.NEXT_MANAGED_MATCH,null));
        assertThatThrownBy(()->series.createDraft(entry.seriesId(),new com.lolfm.dto.SeriesApiV1Dtos.DraftCreateRequest(com.lolfm.dto.SeriesApiV1Dtos.DRAFT_CREATE_REQUEST_SCHEMA,before.revision(),UUID.randomUUID().toString()))).isInstanceOfSatisfying(CareerException.class,e->assertThat(e.type()).isEqualTo(CareerException.Type.CONTINUOUS_BUSY));
        assertThat(series.get(entry.seriesId()).revision()).isEqualTo(before.revision());service.step(id,"test");

        java.nio.file.Files.createDirectories(java.nio.file.Path.of("build/reports/career-continuous"));
        jdbc.execute("SCRIPT TO 'build/reports/career-continuous/browser-fixture.sql'");
        java.nio.file.Files.writeString(java.nio.file.Path.of("build/reports/career-continuous/actual.txt"),"career="+id+"\nstart="+date+"\nstop="+stopped.currentDate()+"\nreason="+stopped.run().stop.reason()+"\nseries="+paused.run().completedSeries+"\ngames="+paused.run().completedGames+"\nelapsedMs="+(System.nanoTime()-begin)/1000000+"\n");
    }
}
