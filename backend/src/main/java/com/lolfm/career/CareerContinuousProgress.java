package com.lolfm.career;

import java.time.LocalDate;
import java.util.List;

/** Career orchestration state; never contains gameplay state or a match timeline. */
public final class CareerContinuousProgress {
    public static final String REQUEST_SCHEMA="CAREER_CONTINUOUS_COMMAND_V1";
    public static final String VIEW_SCHEMA="CAREER_CONTINUOUS_VIEW_V1";
    public enum Mode { NEXT_MANAGED_MATCH, TARGET_DATE }
    public enum Status { RUNNING, WAITING, PAUSE_REQUESTED, PAUSED, STOPPED, COMPLETED, FAILED }
    public enum Category { AUTOMATIC_WAIT, USER_DECISION, RECOVERABLE_ERROR, BLOCKED_ERROR, BOUNDARY }
    public enum Action { ADVANCE, COMPETITION, REFRESH }
    public enum Reason { PLAYER_MATCH, PLAYER_SERIES, PLAYER_CHOICE, CONTRACT_RESPONSE, TRADE_RESPONSE,
        ROSTER_DECISION, FINANCE_DECISION, SEASON_TRANSITION, TARGET_REACHED, USER_PAUSED,
        JOB_PENDING, AUTOMATIC_TRANSITION, AI_ROSTER_REPAIR, NO_PROGRESS, UNSUPPORTED_SAVE,
        EXECUTION_ERROR, SAFETY_LIMIT, TARGET_REPAIR_BOUNDARY }
    public record Command(String schemaVersion,String action,String clientCommandId,String runId,
                          Long expectedRevision,Mode mode,LocalDate targetDate) {}
    public record Stop(Category category,Reason reason,String owner,String referenceId,String nextAction) {}
    public record Intent(Action action,String commandId,Long expectedRevision,String mode,
                         String fixtureId,String jobId,LocalDate beforeDate) {
        public Intent withJob(String job){return new Intent(action,commandId,expectedRevision,mode,fixtureId,job,beforeDate);}
    }
    /** Mutable only within a locked load/save transaction; serialized as the durable checkpoint. */
    public static final class Run {
        public int completedAwards,observedAwards;
        public String runId,careerId;
        public int seasonYear;
        public Mode mode;
        public LocalDate targetDate,startDate;
        public Status status;
        public long revision;
        public int completedDates,completedSeries,completedGames,steps,stalls,observedSeries,observedGames,resumeStep;
        public String previousFingerprint;
        public boolean refreshNeeded=true;
        public Intent intent;
        public Stop stop;
        public Run() {}
    }
    public record View(String schemaVersion,String careerId,LocalDate currentDate,Run run,
                       List<String> allowedCommands) {}
    public record Receipt(String clientCommandId,String runId,String action,long resultingRevision,Status status) {}
    public record Response(boolean replayed,Receipt receipt,View progress) {}
    static List<String> allowedCommands(Run run,int activeYear) {
        if(run==null)return List.of("START");
        if(active(run.status))return List.of("PAUSE");
        if(run.seasonYear!=activeYear&&run.intent==null)return List.of("START");
        if(run.stop!=null&&run.stop.category()==Category.BLOCKED_ERROR)return List.of();
        if(run.status==Status.COMPLETED)return List.of("START");
        return run.status==Status.PAUSED||run.intent!=null?List.of("RESUME"):List.of("RESUME","START");
    }
    public static boolean active(Status status) {
        return status==Status.RUNNING||status==Status.WAITING||status==Status.PAUSE_REQUESTED;
    }
    private CareerContinuousProgress() {}
}
