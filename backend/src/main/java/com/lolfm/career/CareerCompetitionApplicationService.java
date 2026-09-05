package com.lolfm.career;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Service;

/** Career-owned competition reconciliation and bounded Calendar projection. */
@Service
public final class CareerCompetitionApplicationService {
    private final CareerCompetitionRelationalStore store;
    private final CareerCompetitionRules rules;

    public CareerCompetitionApplicationService(
            CareerCompetitionRelationalStore store,
            CareerCompetitionRules rules
    ) {
        this.store = Objects.requireNonNull(store, "store");
        this.rules = Objects.requireNonNull(rules, "rules");
    }

    public void initializeNew(
            CareerRelationalStore.NewCareer career,
            int calendarSeasonYear
    ) {
        store.initialize(career, calendarSeasonYear);
    }

    public void reconcileForAdvance(
            CareerRelationalStore.CareerRow career,
            int calendarSeasonYear,
            CareerCalendarLeaguePort.SeasonProjection season
    ) {
        // Cycle creation/recovery is an explicit lifecycle boundary. Advance only
        // loads the already-bound policy so future seasons cannot fall back to the
        // first-season 2026 bootstrap.
        store.load(career.careerId(), calendarSeasonYear);
        store.reconcileInternational(career.careerId(), calendarSeasonYear);
        if ("COMPLETED".equals(season.seasonLifecycleStatus())
                && season.allFixturesCompleted()) {
            store.reconcileDomesticR1R2(career.careerId(), calendarSeasonYear);
        }
    }

    public CompetitionView view(
            CareerRelationalStore.CareerRow career,
            int calendarSeasonYear,
            LocalDate currentDate,
            String currentCompetitionId,
            String nextCompetitionId
    ) {
        CareerCompetitionRelationalStore.CycleView cycle;
        try {
            cycle = store.load(career.careerId(), calendarSeasonYear);
        } catch (IllegalStateException missingCycle) {
            if (!"CAREER_COMPETITION_CYCLE_NOT_FOUND".equals(
                    missingCycle.getMessage())) throw missingCycle;
            return missingFutureCycle(calendarSeasonYear, currentCompetitionId,
                    nextCompetitionId);
        }
        CompetitionSummary current = summary(cycle, currentCompetitionId);
        CompetitionSummary next = summary(cycle, nextCompetitionId);
        CareerCompetitionRelationalStore.FixtureRow fixture = cycle.fixtures().stream()
                .filter(value -> !"COMPLETED".equals(value.lifecycleStatus()))
                .findFirst().orElse(null);
        CareerCompetitionRelationalStore.ExecutionProjection execution = fixture == null
                ? null : store.executionProjection(career.careerId(),
                calendarSeasonYear, fixture.competitionId(), fixture.matchId());
        CompetitionFixture projectedFixture = fixture == null ? null
                : fixture(fixture, career.managedTeamCode(), execution);
        String pendingStatus = pendingStatus(execution);
        PendingCompetitionCommand pending = pendingStatus == null ? null
                : new PendingCompetitionCommand(execution.clientCommandId(),
                fixture.competitionId(), fixture.matchId(), pendingStatus);
        return new CompetitionView("CAREER_COMPETITION_VIEW_V1", cycle.seasonYear(),
                cycle.ruleResourceHash(), cycle.ruleVersion(),
                cycle.gamePolicyVersion(),
                CareerCompetitionRules.PROJECTION_POLICY,
                CareerCompetitionRules.R3_R4_ALLOCATION_POLICY,
                cycle.lifecycleStatus(), cycle.revision(), cycle.stateHash(),
                current, next, projectedFixture, cycle.outputs().stream().map(value ->
                new QualificationOutput(value.competitionId(), value.outputId(),
                        value.teamCode())).toList(),
                store.cupStandings(career.careerId(), calendarSeasonYear).stream()
                        .map(value -> new CompetitionStanding(value.groupId(),
                                value.groupPoints(), value.groupRank(), value.teamCode(),
                                value.matchWins(), value.matchLosses(), value.gameWins(),
                                value.gameLosses(), value.strengthOfVictory(),
                                value.winTimeSeconds(), value.tieBreakTrace(),
                                value.standingsHash())).toList(),
                store.currentSeeds(career.careerId(), calendarSeasonYear).stream()
                        .map(value -> new CompetitionSeed(value.competitionId(),
                                value.seedScope(), value.seedNumber(), value.teamCode(),
                                value.sourceInputHash())).toList(),
                externalLimited(current) || externalLimited(next), pending,
                CareerCompetitionRules.VERSION.equals(cycle.ruleVersion()) || pending != null ? allowedCommands(projectedFixture, currentDate) : List.of(),
                store.domesticDecisions(career.careerId(), calendarSeasonYear), store.finalRanking(career.careerId(), calendarSeasonYear),
                CareerCompetitionRules.VERSION.equals(cycle.ruleVersion()) ? "CURRENT" : "PRESERVED_PREVIOUS_RULES", store.internationalViews(career.careerId(), calendarSeasonYear));
    }

    private static String pendingStatus(
            CareerCompetitionRelationalStore.ExecutionProjection execution
    ) {
        if (execution == null || execution.clientCommandId() == null
                || "APPLIED".equals(execution.resultApplicationStatus())) return null;
        if (execution.jobStatus() != null
                && ("PENDING".equals(execution.jobStatus())
                || "RUNNING".equals(execution.jobStatus()))) {
            return execution.jobStatus();
        }
        return List.of("PLAYER_ACTIVE", "SERIES_COMPLETED").contains(
                execution.bindingStatus()) ? "RUNNING" : null;
    }

    private CompetitionView missingFutureCycle(
            int calendarSeasonYear,
            String currentCompetitionId,
            String nextCompetitionId
    ) {
        CompetitionSummary current = missingFutureSummary(currentCompetitionId);
        CompetitionSummary next = missingFutureSummary(nextCompetitionId);
        return new CompetitionView("CAREER_COMPETITION_VIEW_V1", calendarSeasonYear,
                rules.resourceHash(), CareerCompetitionRules.VERSION,
                CareerCompetitionRules.GAME_POLICY_VERSION,
                CareerCompetitionRules.PROJECTION_POLICY,
                CareerCompetitionRules.R3_R4_ALLOCATION_POLICY,
                "BLOCKED", 0, null, current, next, null, List.of(), List.of(),
                List.of(), false, null, List.of(), List.of(), null, "PRIOR_SEASON_REQUIRED", List.of());
    }

    private static CompetitionSummary missingFutureSummary(String competitionId) {
        if (competitionId == null) return null;
        return new CompetitionSummary(competitionId, "UNMATERIALIZED",
                "VERIFIED_PRIOR_SEASON_REQUIRED", "BLOCKED",
                "PRIOR_SEASON_SEALED_RANKING_REQUIRED", 0, null, 0, 0);
    }

    public boolean transitionReady(
            CareerRelationalStore.CareerRow career,
            int calendarSeasonYear,
            CareerCalendarLeaguePort.SeasonProjection season
    ) {
        if (!"COMPLETED".equals(season.seasonLifecycleStatus())
                || !season.allFixturesCompleted()) return false;
        reconcileForAdvance(career, calendarSeasonYear, season);
        return store.load(career.careerId(), calendarSeasonYear).r1r2ImportHash() != null;
    }

    public CompetitionGate gate(
            CareerRelationalStore.CareerRow career,
            int calendarSeasonYear,
            LocalDate date,
            String competitionId,
            CareerCalendarLeaguePort.SeasonProjection season
    ) {
        CompetitionView view = view(career, calendarSeasonYear, date,
                competitionId, null);
        CompetitionSummary current = view.currentCompetition();
        if ("PRESERVED_PREVIOUS_RULES".equals(view.domesticRuleCompatibility()) && view.activePendingCommand() == null)
            return new CompetitionGate("DOMESTIC_RULE_VERSION_REQUIRES_NEW_CYCLE", null, null);
        if (current != null && blocked(current)) {
            return new CompetitionGate(current.blockingReason(), null, null);
        }
        CompetitionFixture fixture = view.nextFixture();
        if (fixture != null && !fixture.date().isAfter(date)
                && !"COMPLETED".equals(fixture.lifecycleStatus())) {
            if ("READY".equals(fixture.lifecycleStatus())) {
                return new CompetitionGate(fixture.managedTeamIncluded()
                        ? "MANAGED_COMPETITION_FIXTURE_REQUIRED"
                        : "AUTO_COMPETITION_FIXTURE_REQUIRED", fixture.fixtureId(),
                        fixture.seriesId());
            }
            return new CompetitionGate(fixtureBlocker(fixture), fixture.fixtureId(),
                    fixture.seriesId());
        }
        return CompetitionGate.clear();
    }

    private static boolean blocked(CompetitionSummary value) {
        if ("BLOCKED".equals(value.lifecycleStatus())
                || "SOURCE_GAP".equals(value.lifecycleStatus())
                || "POLICY_REQUIRED".equals(value.lifecycleStatus())
                || "EXECUTION_REQUIRED".equals(value.lifecycleStatus())
                || "WAITING_FOR_QUALIFICATION".equals(value.lifecycleStatus())) {
            return true;
        }
        return "RULE_SOURCE_INCOMPLETE".equals(value.ruleStatus())
                || "PRODUCT_POLICY_REQUIRED".equals(value.ruleStatus())
                || value.blockingReason() != null && (
                value.blockingReason().contains("SOURCE_INCOMPLETE")
                        || value.blockingReason().contains("POLICY_REQUIRED")
                        || value.blockingReason().contains("EXECUTION_NOT_IMPLEMENTED")
                        || value.blockingReason().contains("AUTHORITY_MISSING"));
    }

    private CompetitionSummary summary(
            CareerCompetitionRelationalStore.CycleView cycle,
            String competitionId
    ) {
        if (competitionId == null) return null;
        CareerCompetitionRelationalStore.InstanceRow instance = cycle.competitions().stream()
                .filter(value -> competitionId.equals(value.competitionId()))
                .findFirst().orElse(null);
        if (instance == null) return null;
        long total = cycle.fixtures().stream().filter(value -> competitionId.equals(
                value.competitionId())).count();
        long completed = cycle.fixtures().stream().filter(value -> competitionId.equals(
                value.competitionId()) && "COMPLETED".equals(value.lifecycleStatus()))
                .count();
        CareerCompetitionRelationalStore.FixtureRow nextFixture = cycle.fixtures().stream()
                .filter(value -> competitionId.equals(value.competitionId()))
                .filter(value -> !"COMPLETED".equals(value.lifecycleStatus()))
                .findFirst()
                .orElse(null);
        String stage = total > 0 && completed == total ? "COMPLETED"
                : nextFixture == null ? stageId(competitionId) : nextFixture.stageId();
        return new CompetitionSummary(instance.competitionId(), stage,
                instance.ruleStatus(),
                instance.lifecycleStatus(), instance.blockingReason(), instance.revision(),
                instance.stateHash(), (int) completed, (int) total);
    }

    private CompetitionFixture fixture(
            CareerCompetitionRelationalStore.FixtureRow value,
            String managedTeamCode,
            CareerCompetitionRelationalStore.ExecutionProjection execution
    ) {
        if (CareerInternationalRules.COMPETITIONS.contains(value.competitionId()))
            managedTeamCode = CompetitionRosterSnapshot.managedToken(managedTeamCode);
        return new CompetitionFixture(value.competitionId(), value.matchId(),
                value.fixtureId(), value.seriesId(), value.date(), value.scheduleStatus(),
                value.seriesFormat(), value.hardFearless(), value.firstTeamCode(),
                value.secondTeamCode(), value.executionMode(), value.lifecycleStatus(),
                value.firstTeamCode() != null && value.secondTeamCode() != null
                        && (managedTeamCode.equals(value.firstTeamCode())
                        || managedTeamCode.equals(value.secondTeamCode())),
                Long.toString(value.rootSeed()),
                CareerCompetitionAggregate.SEED_ALGORITHM,
                new CareerCompetitionRules.ParticipantSelector(
                        value.firstSelectorType(), value.firstSelectorValue()),
                new CareerCompetitionRules.ParticipantSelector(
                        value.secondSelectorType(), value.secondSelectorValue()),
                value.stageId(), "READY".equals(value.lifecycleStatus())
                ? null : fixtureBlocker(value),
                execution == null ? null : execution.bindingHash(),
                execution == null ? null : execution.jobId(),
                execution == null ? null : execution.jobStatus(),
                execution == null ? "NOT_APPLIED"
                        : execution.resultApplicationStatus(),
                execution == null ? null : execution.failureCode());
    }

    private static List<String> allowedCommands(
            CompetitionFixture fixture,
            LocalDate currentDate
    ) {
        if (fixture == null || fixture.date().isAfter(currentDate)
                || !"READY".equals(fixture.lifecycleStatus())) {
            return List.of();
        }
        if (fixture.bindingHash() == null) {
            return List.of(fixture.managedTeamIncluded()
                    ? "START_PLAYER_COMPETITION_SERIES"
                    : "DISPATCH_AUTO_COMPETITION_FIXTURE");
        }
        if (fixture.managedTeamIncluded()) {
            return List.of("RECONCILE_COMPETITION_FIXTURE",
                    "RESUME_PLAYER_COMPETITION_SERIES");
        }
        return List.of("RECONCILE_COMPETITION_FIXTURE");
    }

    private static boolean externalLimited(CompetitionSummary value) {
        return value != null && "EXTERNAL_COMPETITION_EXECUTION_NOT_IMPLEMENTED".equals(
                value.blockingReason());
    }

    private static String fixtureBlocker(CompetitionFixture fixture) {
        CareerCompetitionRules.ParticipantSelector first = fixture.firstSelector();
        CareerCompetitionRules.ParticipantSelector second = fixture.secondSelector();
        List<String> types = List.of(first.type(), second.type());
        if ("LCK_CUP".equals(fixture.competitionId()) && types.stream().anyMatch(type ->
                "CUP_PLAY_IN_SEED".equals(type) || "CUP_PLAYOFF_SEED".equals(type))) {
            return "LCK_CUP_GROUP_STANDINGS_REQUIRED";
        }
        if (types.stream().anyMatch(type -> type.contains("LOWEST_AVAILABLE")
                || type.contains("REMAINING"))) {
            return "COMPETITION_OPPONENT_CHOICE_REQUIRED";
        }
        if (types.stream().anyMatch(type -> type.endsWith("_SEED"))) {
            return "COMPETITION_SEED_REQUIRED";
        }
        return "COMPETITION_PREDECESSOR_RESULT_REQUIRED";
    }

    private static String fixtureBlocker(
            CareerCompetitionRelationalStore.FixtureRow fixture
    ) {
        List<String> types = List.of(fixture.firstSelectorType(),
                fixture.secondSelectorType());
        if ("LCK_CUP".equals(fixture.competitionId()) && types.stream().anyMatch(type ->
                "CUP_PLAY_IN_SEED".equals(type) || "CUP_PLAYOFF_SEED".equals(type))) {
            return "LCK_CUP_GROUP_STANDINGS_REQUIRED";
        }
        if (types.stream().anyMatch(type -> type.contains("LOWEST_AVAILABLE")
                || type.contains("REMAINING"))) {
            return "COMPETITION_OPPONENT_CHOICE_REQUIRED";
        }
        if (types.stream().anyMatch(type -> type.endsWith("_SEED"))) {
            return "COMPETITION_SEED_REQUIRED";
        }
        return "COMPETITION_PREDECESSOR_RESULT_REQUIRED";
    }

    private static String stageId(String competitionId) {
        return switch (competitionId) {
            case "LCK_CUP" -> "CUP_INITIALIZATION";
            case "LCK_REGULAR_R1_R2" -> "R1_R2";
            case "LCK_ROAD_TO_MSI" -> "ROAD_TO_MSI";
            case "LCK_REGULAR_R3_R4" -> "LEGEND_RISE";
            case "LCK_PLAY_IN" -> "PLAY_IN";
            case "LCK_PLAYOFFS" -> "PLAYOFFS";
            case "ASIAN_GAMES_LOL_RELEASE" -> "NATIONAL_TEAM_RELEASE_WINDOW";
            default -> "EXTERNAL_HANDOFF";
        };
    }

    public record CompetitionView(
            String schemaVersion,
            int calendarSeasonYear,
            String ruleResourceHash,
            String ruleVersion,
            String gamePolicyVersion,
            String projectionPolicy,
            String r3r4AllocationPolicy,
            String lifecycleStatus,
            long revision,
            String stateHash,
            CompetitionSummary currentCompetition,
            CompetitionSummary nextCompetition,
            CompetitionFixture nextFixture,
            List<QualificationOutput> qualificationOutputs,
            List<CompetitionStanding> groupStandings,
            List<CompetitionSeed> currentSeeds,
            boolean externalExecutionLimited,
            PendingCompetitionCommand activePendingCommand,
            List<String> allowedCommands,
            List<CareerCompetitionRelationalStore.DomesticDecisionView> domesticRankingDecisions,
            CareerCompetitionRelationalStore.FinalRankingView finalRanking,
            String domesticRuleCompatibility,
            List<CareerCompetitionRelationalStore.InternationalView> internationalCompetitions
    ) {
        public CompetitionView {
            qualificationOutputs = List.copyOf(qualificationOutputs);
            groupStandings = List.copyOf(groupStandings);
            currentSeeds = List.copyOf(currentSeeds);
            allowedCommands = List.copyOf(allowedCommands);
            domesticRankingDecisions = List.copyOf(domesticRankingDecisions);
            internationalCompetitions = List.copyOf(internationalCompetitions);
        }
    }

    public record CompetitionSummary(
            String competitionId,
            String stageId,
            String ruleStatus,
            String lifecycleStatus,
            String blockingReason,
            long revision,
            String stateHash,
            int completedFixtures,
            int totalFixtures
    ) {}

    public record CompetitionFixture(
            String competitionId,
            String matchId,
            String fixtureId,
            String seriesId,
            LocalDate date,
            String scheduleStatus,
            String seriesFormat,
            boolean hardFearless,
            String firstTeamCode,
            String secondTeamCode,
            String executionMode,
            String lifecycleStatus,
            boolean managedTeamIncluded,
            String rootSeed,
            String seedAlgorithm,
            CareerCompetitionRules.ParticipantSelector firstSelector,
            CareerCompetitionRules.ParticipantSelector secondSelector,
            String stageId,
            String blockingReason,
            String bindingHash,
            String jobId,
            String jobStatus,
            String resultApplicationStatus,
            String failureCode
    ) {
        public CompetitionFixture(
                String competitionId, String matchId, String fixtureId, String seriesId,
                LocalDate date, String scheduleStatus, String seriesFormat,
                boolean hardFearless, String firstTeamCode, String secondTeamCode,
                String executionMode, String lifecycleStatus, boolean managedTeamIncluded,
                String rootSeed, String seedAlgorithm
        ) {
            this(competitionId, matchId, fixtureId, seriesId, date, scheduleStatus,
                    seriesFormat, hardFearless, firstTeamCode, secondTeamCode,
                    executionMode, lifecycleStatus, managedTeamIncluded, rootSeed,
                    seedAlgorithm, null, null, null, null, null, null, null,
                    "NOT_APPLIED", null);
        }
    }

    public record QualificationOutput(
            String competitionId, String outputId, String teamCode
    ) {}

    public record CompetitionStanding(
            String groupId, int groupPoints, int groupRank, String teamCode,
            int matchWins, int matchLosses, int gameWins, int gameLosses,
            int strengthOfVictory, int winTimeSeconds, String tieBreakTrace,
            String standingsHash
    ) {}

    public record CompetitionSeed(
            String competitionId, String seedScope, int seedNumber,
            String teamCode, String sourceInputHash
    ) {}

    public record PendingCompetitionCommand(
            String clientCommandId, String competitionId, String matchId,
            String commandStatus
    ) {}

    public record CompetitionGate(String stopReason, String fixtureId, String seriesId) {
        public static CompetitionGate clear() {
            return new CompetitionGate(null, null, null);
        }
    }
}
