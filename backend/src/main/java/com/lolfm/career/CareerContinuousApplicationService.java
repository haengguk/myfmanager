package com.lolfm.career;

import static com.lolfm.career.CareerContinuousProgress.*;
import static com.lolfm.career.CareerRosterStore.hash;
import static com.lolfm.career.CareerRosterStore.read;
import static com.lolfm.career.CareerRosterStore.write;
import static com.lolfm.career.CareerRosterStore.lockCareer;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;

@Service
public final class CareerContinuousApplicationService {
    private final CareerContinuousStore store;
    private final CareerApplicationService careers;
    private final CareerCalendarApplicationService calendar;
    private final CareerCompetitionApplicationService competitionView;
    private final CareerCompetitionExecutionService competitions;
    private final CareerCompetitionBackgroundExecutionPort background;
    private final CareerCalendarLeaguePort leagues;
    public CareerContinuousApplicationService(CareerContinuousStore store,CareerApplicationService careers,
            CareerCalendarApplicationService calendar,CareerCompetitionApplicationService competitionView,
            CareerCompetitionExecutionService competitions,CareerCompetitionBackgroundExecutionPort background,
            CareerCalendarLeaguePort leagues) {
        this.store=store;this.careers=careers;this.calendar=calendar;this.competitionView=competitionView;
        this.competitions=competitions;this.background=background;this.leagues=leagues;
    }
    public View view(String career) {
        CareerIdentity.requireCareerId(career);
        return project(career,store.load(career));
    }
    private View project(String career,Run run) {
        var commands=allowedCommands(run,store.activeYear(career));
        return new View(VIEW_SCHEMA,career,store.date(career),run,commands);
    }
    public Response command(String career,Command input) {
        if(input==null)throw CareerException.invalid(null,"연속 진행 요청이 필요합니다.");
        final Command request;
        try {request=new Command(input.schemaVersion(),input.action(),CareerIdentity.canonicalCommandId(input.clientCommandId()),input.runId(),input.expectedRevision(),input.mode(),input.targetDate());}
        catch(RuntimeException error){throw CareerException.invalid("clientCommandId","원본 UUID가 필요합니다.");}
        if(request==null||request.action()==null||!REQUEST_SCHEMA.equals(request.schemaVersion())||!Set.of("START","PAUSE","RESUME").contains(request.action()))
            throw CareerException.invalid(null,"연속 진행 요청 형식을 확인해 주세요.");
        try{CareerIdentity.canonicalCommandId(request.clientCommandId());}catch(RuntimeException e){throw CareerException.invalid("clientCommandId","원본 UUID가 필요합니다.");}
        return store.tx.execute(t->{
            store.lock(career);
            var prior=store.replay(career,request);
            if(prior!=null)return new Response(true,prior,project(career,store.load(career)));
            var saved=careers.get(career);var day=calendar.view(saved.career()).state();
            Run run=store.load(career);
            if("START".equals(request.action())) {
                if(!allowedCommands(run,day.seasonYear()).contains("START"))throw CareerException.continuousBusy();
                if(request.mode()==null||request.runId()!=null||request.expectedRevision()!=null
                        ||(request.mode()==Mode.TARGET_DATE)!=(request.targetDate()!=null))throw CareerException.invalid("mode","모드와 목표 날짜를 확인해 주세요.");
                if(request.targetDate()!=null&&(request.targetDate().isBefore(day.currentDate())||request.targetDate().isAfter(LocalDate.of(day.seasonYear(),12,31))))
                    throw CareerException.invalid("targetDate","현재 날짜부터 같은 시즌의 날짜를 선택해 주세요.");
                run=new Run();run.careerId=career;run.runId="continuous_"+request.clientCommandId();run.seasonYear=day.seasonYear();
                run.startDate=day.currentDate();run.mode=request.mode();run.targetDate=request.targetDate();run.status=Status.RUNNING;
                baseline(run);store.insert(run);
            } else {
                if(run==null||!run.runId.equals(request.runId())||request.expectedRevision()==null||(request.expectedRevision()>run.revision||(!"PAUSE".equals(request.action())&&request.expectedRevision()!=run.revision)))
                    throw CareerException.calendarStaleRevision();
                if(request.mode()!=null||request.targetDate()!=null)throw CareerException.invalid("mode","재개는 저장된 모드와 목표를 사용합니다.");
                if("PAUSE".equals(request.action())) {
                    if(!active(run.status))throw CareerException.invalid("action","현재 실행 중이 아닙니다.");
                    run.status=Status.PAUSE_REQUESTED;
                } else {
                    if(!allowedCommands(run,day.seasonYear()).contains("RESUME"))throw CareerException.continuousBusy();
                    if(run.seasonYear!=day.seasonYear())throw CareerException.invalid("runId","새 시즌에서 새 연속 진행을 시작해 주세요.");
                    if(run.intent!=null&&!store.childAccepted(run.intent)){run.intent=null;run.steps++;}
                    run.status=Status.RUNNING;run.stop=null;run.stalls=0;run.refreshNeeded=true;run.resumeStep=run.steps;if(run.intent==null)baseline(run);
                }
                run.revision++;store.save(run);store.wake(career);
            }
            var receipt=new Receipt(request.clientCommandId(),run.runId,request.action(),run.revision,run.status);
            store.record(career,request,receipt);
            return new Response(false,receipt,project(career,run));
        });
    }
    record Claimed(String career,String owner,long fence,Run run) {}
    record Child(boolean pending,String jobId) {}
    /** One bounded scheduling step. No match is simulated in this transaction or request. */
    public void step(String career,String owner) {
        Claimed claim=store.tx.execute(t->{
            store.lock(career);long fence=store.claim(career,owner);if(fence<0)return null;
            Run run=store.load(career);
            try {
                if(run.status==Status.PAUSE_REQUESTED&&run.intent==null) {
                    stop(run,new Stop(Category.BOUNDARY,Reason.USER_PAUSED,null,null,"RESUME"));
                    store.save(run);store.release(career,owner,fence,0);return null;
                }
                if(run.intent==null)prepare(run);
                store.save(run);
                if(!active(run.status)){store.release(career,owner,fence,0);return null;}
                return new Claimed(career,owner,fence,run);
            } catch(RuntimeException error) {
                fail(run,error);store.save(run);store.release(career,owner,fence,0);return null;
            }
        });
        if(claim==null)return;
        try {
            Child child=store.tx.execute(t->{
                store.lock(career);
                if(!store.owns(career,owner,claim.fence()))return null;
                Run latest=store.load(career);
                if(latest.status==Status.PAUSE_REQUESTED&&!store.childAccepted(latest.intent)) {
                    latest.intent=null;latest.steps++;
                    stop(latest,new Stop(Category.BOUNDARY,Reason.USER_PAUSED,null,null,"RESUME"));
                    store.save(latest);store.release(career,owner,claim.fence(),0);return null;
                }
                return CareerContinuousGuard.execute(career,owner,claim.fence(),store,()->execute(claim.run()));
            });
            if(child==null)return;
            // A child receipt is committed before this checkpoint. Replaying its original UUID closes this gap.
            finish(claim,child);
        } catch(RuntimeException error) {
            store.tx.executeWithoutResult(t->{store.lock(career);if(!store.owns(career,owner,claim.fence()))return;
                Run run=store.load(career);fail(run,error);store.save(run);store.release(career,owner,claim.fence(),0);});
        }
    }
    private void prepare(Run run) {
        var career=careers.get(run.careerId).career();var view=calendar.view(career);var day=view.state();
        Stop decision=CareerContinuousPlanner.marketDecision(Optional.ofNullable(CareerMarketStore.load(store.jdbc,run.careerId)).map(CareerMarketStore.Saved::state).orElse(null),"LCK:"+career.managedTeamCode());
        var league=leagues.load(view.fixtureOverlay().provenanceV2().leagueId(),view.fixtureOverlay().provenanceV2().seasonId());
        var indexed=new HashMap<String,LocalDate>();view.fixtureOverlay().fixtures().forEach(f->indexed.put(f.fixtureId(),f.date()));
        boolean leagueDue=false;
        for(var f:league.fixtures())if(!"COMPLETED".equals(f.fixtureStatus())&&!indexed.get(f.fixtureId()).isAfter(day.currentDate())) {
            if("PLAYER_CONTROLLED".equals(f.executionMode())&&decision==null)
                decision=new Stop(Category.USER_DECISION,Reason.PLAYER_MATCH,career.managedTeamCode(),f.boundSeriesId(),"MATCH");
            else if("FULL_AUTO".equals(f.executionMode()))leagueDue=true;
        }
        var c=view.competition();var fixture=c.nextFixture();boolean due=fixture!=null&&!fixture.date().isAfter(day.currentDate());
        if(due&&"PLAYER_CONTROLLED".equals(fixture.executionMode())&&decision==null)
            decision=new Stop(Category.USER_DECISION,fixture.bindingHash()==null?Reason.PLAYER_MATCH:Reason.PLAYER_SERIES,career.managedTeamCode(),fixture.seriesId(),"MATCH");
        Intent pending=null;
        if(view.activePendingAdvance()!=null) {
            var p=view.activePendingAdvance();pending=new Intent(Action.ADVANCE,p.clientCommandId(),p.expectedRevision(),p.mode(),null,null,day.currentDate());
        } else if(due&&fixture.jobId()!=null&&c.activePendingCommand()!=null) {
            pending=new Intent(Action.COMPETITION,c.activePendingCommand().clientCommandId(),null,null,fixture.fixtureId(),fixture.jobId(),day.currentDate());
        }
        boolean repair="ROSTER_REPAIR_REQUIRED".equals(view.blockingReason());
        var registrationDecision=CareerContinuousPlanner.registrationDecision(calendar.registrationRepairWaits(career,day.seasonYear(),day.currentDate()));
        if(decision==null&&registrationDecision!=null)decision=registrationDecision;
        boolean auto=due&&c.allowedCommands().contains("DISPATCH_AUTO_COMPETITION_FIXTURE");
        var next=CareerContinuousPlanner.next(run,new CareerContinuousPlanner.Situation(day.currentDate(),day.seasonYear()==run.seasonYear,
                view.allowedAdvanceModes().contains(CareerCalendarApplicationService.ADVANCE_ONE_DAY),run.refreshNeeded,decision,pending,auto,repair,view.blockingReason()));
        // A current-day League gate must run even when target equals today; it never skips due games.
        if(next.stop()!=null&&Set.of(Reason.TARGET_REACHED,Reason.SEASON_TRANSITION).contains(next.stop().reason())&&leagueDue&&!repair)next=CareerContinuousPlanner.Next.action(Action.ADVANCE);
        if(next.stop()!=null){stop(run,next.stop());return;}
        if(run.steps-run.resumeStep>=20000){stop(run,new Stop(Category.RECOVERABLE_ERROR,Reason.SAFETY_LIMIT,null,null,"RESUME"));return;}
        String fingerprint=day.currentDate()+"|"+day.calendarRevision()+"|"+c.revision()+"|"+league.standingsRevision()+"|"+view.blockingReason();
        if(fingerprint.equals(run.previousFingerprint))run.stalls++;else run.stalls=0;
        if(run.stalls>=4){stop(run,new Stop(Category.RECOVERABLE_ERROR,Reason.NO_PROGRESS,null,null,"CALENDAR"));return;}
        run.previousFingerprint=fingerprint;
        String id=UUID.nameUUIDFromBytes((run.runId+"|"+run.steps+"|"+next.action()).getBytes(StandardCharsets.UTF_8)).toString();
        run.intent=pending!=null?pending:new Intent(next.action(),id,next.action()==Action.COMPETITION?c.revision():day.calendarRevision(),
                CareerCalendarApplicationService.ADVANCE_ONE_DAY,auto?fixture.fixtureId():null,null,day.currentDate());
        run.revision++;
    }
    Child execute(Run run) {
        var career=careers.get(run.careerId).career();var intent=run.intent;
        if(intent.action()==Action.REFRESH) {
            var view=calendar.view(career);
            competitionView.reconcileForAdvance(career,run.seasonYear,leagues.load(view.fixtureOverlay().provenanceV2().leagueId(),view.fixtureOverlay().provenanceV2().seasonId()));
            return new Child(false,null);
        }
        if(intent.action()==Action.ADVANCE) {
            var r=calendar.advance(career,com.lolfm.dto.CareerApiV1Dtos.ADVANCE_REQUEST_SCHEMA,intent.expectedRevision(),intent.mode(),intent.commandId());
            return new Child(r.pending(),null);
        }
        var result=intent.jobId()!=null?competitions.observeAutoJob(run.careerId,run.seasonYear,intent.jobId())
                :competitions.startOrResume(career,run.seasonYear,store.date(run.careerId),intent.expectedRevision(),intent.commandId());
        if(!"FULL_AUTO".equals(result.executionMode()))throw new IllegalStateException("CONTINUOUS_MAY_NOT_START_PLAYER");
        if(!Set.of("PENDING","RUNNING","COMPLETED").contains(result.status()))throw new BlockedChild(result.jobId(),result.failureCode());
        return new Child(!"COMPLETED".equals(result.status()),result.jobId());
    }
    void finish(Claimed claim,Child child) {
        store.tx.executeWithoutResult(t->{
            store.lock(claim.career());if(!store.owns(claim.career(),claim.owner(),claim.fence()))return;
            Run run=store.load(claim.career());
            if(child.pending()) {
                if(child.jobId()!=null)run.intent=run.intent.withJob(child.jobId());
                // Enqueue only while this worker still owns the Calendar fence. Execution has its own lease.
                if(child.jobId()!=null&&store.childNeedsWake(child.jobId()))background.submit(child.jobId());
                if(run.status!=Status.PAUSE_REQUESTED)run.status=Status.WAITING;
                run.stop=new Stop(Category.AUTOMATIC_WAIT,Reason.JOB_PENDING,null,child.jobId(),null);
            } else {
                run.completedDates+=Math.toIntExact(java.time.temporal.ChronoUnit.DAYS.between(run.intent.beforeDate(),store.date(run.careerId)));
                int[] totals=store.totals(run.careerId,run.seasonYear);
                run.completedSeries+=totals[0]-run.observedSeries;run.completedGames+=totals[1]-run.observedGames;
                run.observedSeries=totals[0];run.observedGames=totals[1];
                int awards=awardCount(run);run.completedAwards+=awards-run.observedAwards;run.observedAwards=awards;
                run.refreshNeeded=run.intent.action()!=Action.REFRESH;run.intent=null;run.steps++;
                if(run.status==Status.PAUSE_REQUESTED)stop(run,new Stop(Category.BOUNDARY,Reason.USER_PAUSED,null,null,"RESUME"));
                else {run.status=Status.RUNNING;run.stop=null;}
            }
            run.revision++;store.save(run);store.release(run.careerId,claim.owner(),claim.fence(),child.pending()?2:0);
        });
    }
    private int awardCount(Run run){return store.jdbc.queryForObject("SELECT COUNT(*) FROM career_record_award WHERE career_id=? AND season_year=? AND status='FINALIZED' AND definition_id<>'TEAM_STANDOUT'",Integer.class,run.careerId,run.seasonYear);}
    private void baseline(Run run) {
        run.observedAwards=awardCount(run);
        int[] counts=store.totals(run.careerId,run.seasonYear);run.observedSeries=counts[0];run.observedGames=counts[1];
    }
    private static void stop(Run run,Stop reason) {
        run.stop=reason;run.status=reason.reason()==Reason.USER_PAUSED?Status.PAUSED:reason.reason()==Reason.TARGET_REACHED?Status.COMPLETED:Status.STOPPED;run.revision++;
    }
    static final class BlockedChild extends RuntimeException {
        final String jobId;
        BlockedChild(String jobId,String code){super(code);this.jobId=jobId;}
    }
    static void fail(Run run,RuntimeException failure) {
        boolean unsupported=failure instanceof CareerException c&&CareerSaveCompatibility.unsupported(c);
        boolean blocked=unsupported||failure instanceof BlockedChild||failure instanceof IllegalStateException
                ||failure instanceof CareerException c&&Set.of(CareerException.Type.RESOURCE_INTEGRITY_FAILURE,CareerException.Type.COMMAND_RECEIPT_INTEGRITY_FAILURE,
                        CareerException.Type.LINKED_SEASON_INTEGRITY_FAILURE,CareerException.Type.CALENDAR_COMMAND_INTEGRITY_FAILURE,
                        CareerException.Type.CALENDAR_INTEGRITY_FAILURE).contains(c.type());
        run.status=Status.FAILED;run.stop=new Stop(blocked?Category.BLOCKED_ERROR:Category.RECOVERABLE_ERROR,
                unsupported?Reason.UNSUPPORTED_SAVE:Reason.EXECUTION_ERROR,null,
                failure instanceof BlockedChild child?child.jobId:null,blocked?"SAVE":"RETRY");run.revision++;
        org.slf4j.LoggerFactory.getLogger(CareerContinuousApplicationService.class).warn("Career continuous run {} stopped",run.runId,failure);
    }
}
