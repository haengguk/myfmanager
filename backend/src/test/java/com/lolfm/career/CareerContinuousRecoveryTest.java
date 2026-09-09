package com.lolfm.career;
import static com.lolfm.career.CareerContinuousProgress.*;
import static org.assertj.core.api.Assertions.*;
import com.lolfm.LolfmApplication;
import com.lolfm.dto.CareerApiV1Dtos;
import java.nio.file.Path;
import java.time.*;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.WebApplicationType;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
class CareerContinuousRecoveryTest {
    @Test void schedulerKeepsImmediateProgressButBacksOffWhenRetryWritesFail() {
        var store=org.mockito.Mockito.mock(CareerContinuousStore.class);
        var service=org.mockito.Mockito.mock(CareerContinuousApplicationService.class);
        var worker=new CareerContinuousWorker(store,service,false);
        try {
            org.mockito.Mockito.when(store.due()).thenReturn(java.util.List.of("broken","healthy"));
            assertThat(worker.cycleDelayMillis()).isZero();
            org.mockito.Mockito.when(store.due()).thenReturn(java.util.List.of("healthy"),java.util.List.of());
            assertThat(worker.cycleDelayMillis()).isEqualTo(1000); // External job released with its existing future wake.
            org.mockito.Mockito.when(store.due()).thenReturn(java.util.List.of("broken","healthy"));
            org.mockito.Mockito.doThrow(new IllegalStateException("claim failed")).when(service).step(org.mockito.Mockito.eq("broken"),org.mockito.Mockito.anyString());
            org.mockito.Mockito.doThrow(new IllegalStateException("defer failed")).when(store).defer("broken");
            org.mockito.Mockito.clearInvocations(service);
            for(int attempt=0;attempt<3;attempt++)assertThat(worker.cycleDelayMillis()).isEqualTo(1000);
            org.mockito.Mockito.verify(service,org.mockito.Mockito.atLeastOnce()).step(org.mockito.Mockito.eq("healthy"),org.mockito.Mockito.anyString());
            org.mockito.Mockito.doNothing().when(store).defer("broken");
            org.mockito.Mockito.when(store.due()).thenReturn(java.util.List.of("broken"),java.util.List.of());
            assertThat(worker.cycleDelayMillis()).isEqualTo(1000);
        } finally {worker.close();}
    }
    @TempDir Path temporary;
    ConfigurableApplicationContext open(){return new SpringApplicationBuilder(LolfmApplication.class).web(WebApplicationType.NONE).run(
            "--spring.datasource.url=jdbc:h2:file:"+temporary.resolve("continuous")+";DB_CLOSE_ON_EXIT=FALSE;LOCK_TIMEOUT=30000",
            "--spring.main.banner-mode=off","--logging.level.root=ERROR","--spring.main.lazy-initialization=true",
            "--lolfm.career.continuous.background.enabled=false","--lolfm.career.competition.background.enabled=false","--lolfm.league.background.enabled=false");}
    static final class MutableClock extends Clock {
        Instant now=Instant.now();
        public java.time.ZoneId getZone(){return ZoneOffset.UTC;}
        public Clock withZone(java.time.ZoneId zone){return this;}
        public Instant instant(){return now;}
    }
    CareerContinuousApplicationService service(ConfigurableApplicationContext c,CareerContinuousStore s) {
        return new CareerContinuousApplicationService(s,c.getBean(CareerApplicationService.class),c.getBean(CareerCalendarApplicationService.class),c.getBean(CareerCompetitionApplicationService.class),c.getBean(CareerCompetitionExecutionService.class),c.getBean(CareerCompetitionBackgroundExecutionPort.class),c.getBean(CareerCalendarLeaguePort.class));
    }
    @Test void readSnapshotDoesNotUpgradeCalendarLockAfterAnActualDateCommit() throws Exception {
        try(var context=open();var writer=java.util.concurrent.Executors.newSingleThreadExecutor()) {
            var careers=context.getBean(CareerApplicationService.class);
            var c=careers.create(new CareerApiV1Dtos.CreateRequest(CareerApiV1Dtos.CREATE_REQUEST_SCHEMA,"소식 읽기와 날짜 정산","감독","GEN",UUID.randomUUID().toString())).career().career();
            var calendar=context.getBean(CareerCalendarApplicationService.class);var before=calendar.view(c);
            var db=context.getBean(JdbcTemplate.class);
            var snapshot=new org.springframework.transaction.support.TransactionTemplate(context.getBean(PlatformTransactionManager.class));
            snapshot.setReadOnly(true);snapshot.setIsolationLevel(org.springframework.transaction.TransactionDefinition.ISOLATION_REPEATABLE_READ);
            snapshot.executeWithoutResult(t->{
                assertThat(db.queryForObject("SELECT current_game_date FROM career_calendar_state WHERE career_id=?",LocalDate.class,c.careerId())).isEqualTo(before.state().currentDate());
                try {
                    writer.submit(()->calendar.advance(c,CareerApiV1Dtos.ADVANCE_REQUEST_SCHEMA,before.state().calendarRevision(),"ADVANCE_ONE_DAY",UUID.randomUUID().toString())).get(30,java.util.concurrent.TimeUnit.SECONDS);
                } catch(Exception failure){throw new AssertionError("Actual date commit must not wait for a read snapshot",failure);}
                // This used to acquire FOR UPDATE against a version older than the committed row.
                assertThat(context.getBean(CareerInboxService.class).feed(c.careerId(),before.state().seasonYear(),"",false,null,0).careerId()).isEqualTo(c.careerId());
                assertThat(db.queryForObject("SELECT current_game_date FROM career_calendar_state WHERE career_id=?",LocalDate.class,c.careerId())).isEqualTo(before.state().currentDate());
            });
            assertThat(calendar.currentDate(c)).isEqualTo(before.state().currentDate().plusDays(1));
            assertThat(db.queryForObject("SELECT COUNT(*) FROM career_calendar_advance_command WHERE career_id=?",Integer.class,c.careerId())).isOne();
            db.update("UPDATE career_player_directory SET directory_hash=? WHERE career_id=?","0".repeat(64),c.careerId());
            assertThatThrownBy(()->snapshot.execute(t->careers.get(c.careerId())))
                    .isInstanceOfSatisfying(CareerException.class,e->assertThat(e.type()).isEqualTo(CareerException.Type.RESOURCE_INTEGRITY_FAILURE));
        }
    }

    @Test void reopenedFileDatabaseReplaysCommittedChildAndPreservesPausedRunAndFences() {
        String career,child;LocalDate date;long staleFence;
        var start=new Command(REQUEST_SCHEMA,"START",UUID.randomUUID().toString(),null,null,Mode.NEXT_MANAGED_MATCH,null);
        try(var context=open()) {
            var careers=context.getBean(CareerApplicationService.class);var c=careers.create(new CareerApiV1Dtos.CreateRequest(CareerApiV1Dtos.CREATE_REQUEST_SCHEMA,"연속 파일 복구","감독","GEN",UUID.randomUUID().toString())).career().career();career=c.careerId();
            var calendar=context.getBean(CareerCalendarApplicationService.class);date=calendar.currentDate(c);
            var clock=new MutableClock();var store=new CareerContinuousStore(context.getBean(JdbcTemplate.class),context.getBean(PlatformTransactionManager.class),clock);var service=service(context,store);service.command(career,start);
            child=UUID.randomUUID().toString();final String original=child;
            var claim=store.tx.execute(t->{store.lock(career);long fence=store.claim(career,"crashed-worker");var r=store.load(career);
                r.intent=new Intent(Action.ADVANCE,original,calendar.view(c).state().calendarRevision(),"ADVANCE_ONE_DAY",null,null,date);r.revision++;store.save(r);
                return new CareerContinuousApplicationService.Claimed(career,"crashed-worker",fence,r);});staleFence=claim.fence();
            assertThatThrownBy(()->store.tx.execute(t->{store.lock(career);return CareerContinuousGuard.execute(career,claim.owner(),claim.fence(),store,()->{
                var value=service.execute(claim.run());clock.now=clock.now.plusSeconds(91);return value;
            });})).hasMessage("STALE_CONTINUOUS_WORKER");
            assertThat(calendar.currentDate(c)).isEqualTo(date);
            assertThat(store.jdbc.queryForObject("SELECT COUNT(*) FROM career_calendar_advance_command WHERE client_command_id=?",Integer.class,child)).isZero();
            var reclaimed=store.tx.execute(t->{store.lock(career);return new CareerContinuousApplicationService.Claimed(career,"crashed-worker",store.claim(career,"crashed-worker"),store.load(career));});
            assertThat(reclaimed.fence()).isGreaterThan(claim.fence());
            var result=store.tx.execute(t->{store.lock(career);return CareerContinuousGuard.execute(career,reclaimed.owner(),reclaimed.fence(),store,()->service.execute(reclaimed.run()));});
            assertThat(result.pending()).isFalse();assertThat(calendar.currentDate(c)).isEqualTo(date.plusDays(1));
            assertThat(store.load(career).completedDates).isZero();assertThat(store.load(career).intent.commandId()).isEqualTo(child);
            // Close the actual file DB after child commit, deliberately before finish(claim,result).
        }
        String pausedHash;
        try(var context=open()) {
            var db=context.getBean(JdbcTemplate.class);var store=new CareerContinuousStore(db,context.getBean(PlatformTransactionManager.class),Clock.offset(Clock.systemUTC(),Duration.ofMinutes(5)));
            var service=new CareerContinuousApplicationService(store,context.getBean(CareerApplicationService.class),context.getBean(CareerCalendarApplicationService.class),context.getBean(CareerCompetitionApplicationService.class),context.getBean(CareerCompetitionExecutionService.class),context.getBean(CareerCompetitionBackgroundExecutionPort.class),context.getBean(CareerCalendarLeaguePort.class));
            assertThat(service.command(career,start).replayed()).isTrue();service.step(career,"restarted-worker");
            var r=service.view(career).run();assertThat(r.completedDates).isEqualTo(1);assertThat(service.view(career).currentDate()).isEqualTo(date.plusDays(1));assertThat(r.intent).isNull();
            assertThat(db.queryForObject("SELECT COUNT(*) FROM career_calendar_advance_command WHERE client_command_id=?",Integer.class,child)).isEqualTo(1);
            var before=CareerRosterStore.write(r);service.finish(new CareerContinuousApplicationService.Claimed(career,"crashed-worker",staleFence,r),new CareerContinuousApplicationService.Child(false,null));assertThat(CareerRosterStore.write(store.load(career))).isEqualTo(before);
            store.tx.executeWithoutResult(t->{store.lock(career);var planned=store.load(career);planned.intent=new Intent(Action.ADVANCE,UUID.randomUUID().toString(),context.getBean(CareerCalendarApplicationService.class).view(context.getBean(CareerApplicationService.class).get(career).career()).state().calendarRevision(),"ADVANCE_ONE_DAY",null,null,store.date(career));store.save(planned);});
            service.command(career,new Command(REQUEST_SCHEMA,"PAUSE",UUID.randomUUID().toString(),r.runId,r.revision,null,null));service.step(career,"restarted-worker");
            assertThat(service.view(career).run().status).isEqualTo(Status.PAUSED);assertThat(service.view(career).run().intent).isNull();assertThat(service.view(career).currentDate()).isEqualTo(date.plusDays(1));pausedHash=db.queryForObject("SELECT state_hash FROM career_continuous_run WHERE career_id=?",String.class,career);
        }
        try(var context=open()) {
            var service=context.getBean(CareerContinuousApplicationService.class);var store=context.getBean(CareerContinuousStore.class);
            assertThat(service.view(career).run().status).isEqualTo(Status.PAUSED);assertThat(store.due()).doesNotContain(career);service.step(career,"third-worker");
            assertThat(store.jdbc.queryForObject("SELECT state_hash FROM career_continuous_run WHERE career_id=?",String.class,career)).isEqualTo(pausedHash);
            var c=context.getBean(CareerApplicationService.class).get(career).career();var calendar=context.getBean(CareerCalendarApplicationService.class);
            calendar.advance(c,CareerApiV1Dtos.ADVANCE_REQUEST_SCHEMA,calendar.view(c).state().calendarRevision(),"ADVANCE_ONE_DAY",UUID.randomUUID().toString());
            var paused=store.load(career);
            service.command(career,new Command(REQUEST_SCHEMA,"RESUME",UUID.randomUUID().toString(),paused.runId,paused.revision,null,null));service.step(career,"third-worker");
            assertThat(store.load(career).status).isEqualTo(Status.RUNNING);assertThat(store.load(career).completedDates).isEqualTo(1);
            assertThat(store.date(career)).isEqualTo(date.plusDays(2));
            // A corrupt run must not starve a second healthy Career in the bounded scheduler.
            var other=context.getBean(CareerApplicationService.class).create(new CareerApiV1Dtos.CreateRequest(CareerApiV1Dtos.CREATE_REQUEST_SCHEMA,"정상 복구 격리","감독","KT",UUID.randomUUID().toString())).career().career();
            service.command(other.careerId(),new Command(REQUEST_SCHEMA,"START",UUID.randomUUID().toString(),null,null,Mode.TARGET_DATE,store.date(other.careerId())));
            String raw=store.jdbc.queryForObject("SELECT state_json FROM career_continuous_run WHERE career_id=?",String.class,career);
            store.jdbc.update("UPDATE career_continuous_run SET state_hash=? WHERE career_id=?","0".repeat(64),career);
            var worker=new CareerContinuousWorker(store,service,false);
            try {worker.tick();worker.tick();} finally {worker.close();}
            assertThat(store.load(other.careerId()).status).isEqualTo(Status.COMPLETED);
            assertThat(store.jdbc.queryForObject("SELECT state_json FROM career_continuous_run WHERE career_id=?",String.class,career)).isEqualTo(raw);
            assertThat(store.jdbc.queryForObject("SELECT state_hash FROM career_continuous_run WHERE career_id=?",String.class,career)).isEqualTo("0".repeat(64));
            assertThat(store.due()).doesNotContain(career);

        }
    }
}
