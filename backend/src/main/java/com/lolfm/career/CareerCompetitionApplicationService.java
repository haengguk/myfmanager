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

    public CompetitionView reconcileAndView(
            CareerRelationalStore.CareerRow career,
            int calendarSeasonYear,
            LocalDate currentDate,
            String currentCompetitionId,
            String nextCompetitionId,
            CareerCalendarLeaguePort.SeasonProjection season
    ) {
        store.initialize(career.careerId(), calendarSeasonYear);
        if ("COMPLETED".equals(season.seasonLifecycleStatus())
                && season.allFixturesCompleted()) {
            store.sealR1R2(career.careerId(), calendarSeasonYear,
                    career.managedTeamCode(), career.rootSeed(), season.scheduleIdentity(),
                    season.standingsRevision(), season.ranking().stream().map(value ->
                            new CareerCompetitionAggregate.SeededTeam(value.rank(),
                                    value.teamCode(), value.seriesWins(),
                                    value.seriesLosses(), value.gameWins(),
                                    value.gameLosses())).toList());
        }
        CareerCompetitionRelationalStore.CycleView cycle = store.load(
                career.careerId(), calendarSeasonYear);
        CompetitionSummary current = summary(cycle, currentCompetitionId);
        CompetitionSummary next = summary(cycle, nextCompetitionId);
        CareerCompetitionRelationalStore.FixtureRow fixture = cycle.fixtures().stream()
                .filter(value -> !"COMPLETED".equals(value.lifecycleStatus()))
                .filter(value -> !value.date().isBefore(currentDate))
                .findFirst().orElse(null);
        return new CompetitionView("CAREER_COMPETITION_VIEW_V1", cycle.seasonYear(),
                rules.resourceHash(), CareerCompetitionRules.VERSION,
                CareerCompetitionRules.GAME_POLICY_VERSION,
                CareerCompetitionRules.PROJECTION_POLICY,
                CareerCompetitionRules.R3_R4_ALLOCATION_POLICY,
                cycle.lifecycleStatus(), cycle.revision(), cycle.stateHash(),
                current, next, fixture == null ? null : fixture(fixture,
                career.managedTeamCode()), cycle.outputs().stream().map(value ->
                new QualificationOutput(value.competitionId(), value.outputId(),
                        value.teamCode())).toList(),
                externalLimited(current) || externalLimited(next), null, List.of());
    }

    public boolean transitionReady(
            CareerRelationalStore.CareerRow career,
            int calendarSeasonYear,
            CareerCalendarLeaguePort.SeasonProjection season
    ) {
        if (!"COMPLETED".equals(season.seasonLifecycleStatus())
                || !season.allFixturesCompleted()) return false;
        reconcileAndView(career, calendarSeasonYear, career.currentDate(), null, null,
                season);
        return store.load(career.careerId(), calendarSeasonYear).r1r2ImportHash() != null;
    }

    public CompetitionGate gate(
            CareerRelationalStore.CareerRow career,
            int calendarSeasonYear,
            LocalDate date,
            String competitionId,
            CareerCalendarLeaguePort.SeasonProjection season
    ) {
        CompetitionView view = reconcileAndView(career, calendarSeasonYear, date,
                competitionId, null, season);
        CompetitionSummary current = view.currentCompetition();
        if (current != null && "LCK_PLAYOFFS".equals(current.competitionId())
                && "RULE_SOURCE_INCOMPLETE".equals(current.ruleStatus())) {
            return new CompetitionGate(current.blockingReason(), null, null);
        }
        CompetitionFixture fixture = view.nextFixture();
        if (fixture != null && fixture.date().equals(date)
                && "READY".equals(fixture.lifecycleStatus())) {
            return new CompetitionGate(fixture.managedTeamIncluded()
                    ? "MANAGED_COMPETITION_FIXTURE_REQUIRED"
                    : "AUTO_COMPETITION_FIXTURE_REQUIRED", fixture.fixtureId(),
                    fixture.seriesId());
        }
        return CompetitionGate.clear();
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
        return new CompetitionSummary(instance.competitionId(), stageId(competitionId),
                instance.ruleStatus(),
                instance.lifecycleStatus(), instance.blockingReason(), instance.revision(),
                instance.stateHash(), (int) completed, (int) total);
    }

    private static CompetitionFixture fixture(
            CareerCompetitionRelationalStore.FixtureRow value,
            String managedTeamCode
    ) {
        return new CompetitionFixture(value.competitionId(), value.matchId(),
                value.fixtureId(), value.seriesId(), value.date(), value.scheduleStatus(),
                value.seriesFormat(), value.hardFearless(), value.firstTeamCode(),
                value.secondTeamCode(), value.executionMode(), value.lifecycleStatus(),
                value.firstTeamCode() != null && value.secondTeamCode() != null
                        && (managedTeamCode.equals(value.firstTeamCode())
                        || managedTeamCode.equals(value.secondTeamCode())),
                Long.toString(value.rootSeed()),
                CareerCompetitionAggregate.SEED_ALGORITHM);
    }

    private static boolean externalLimited(CompetitionSummary value) {
        return value != null && "EXTERNAL_COMPETITION_EXECUTION_NOT_IMPLEMENTED".equals(
                value.blockingReason());
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
            boolean externalExecutionLimited,
            PendingCompetitionCommand activePendingCommand,
            List<String> allowedCommands
    ) {
        public CompetitionView {
            qualificationOutputs = List.copyOf(qualificationOutputs);
            allowedCommands = List.copyOf(allowedCommands);
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
            String seedAlgorithm
    ) {}

    public record QualificationOutput(
            String competitionId, String outputId, String teamCode
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
