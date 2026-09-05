package com.lolfm.dto;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/** Additive immutable wire contract for /api/v1/careers. */
public final class CareerApiV1Dtos {
    public static final String CREATE_REQUEST_SCHEMA = "CAREER_CREATE_REQUEST_V1";
    public static final String CREATE_RESPONSE_SCHEMA = "CAREER_CREATE_RESPONSE_V1";
    public static final String LIST_SCHEMA = "CAREER_LIST_V1";
    public static final String VIEW_SCHEMA = "CAREER_VIEW_V1";
    public static final String CALENDAR_VIEW_SCHEMA = "CAREER_CALENDAR_VIEW_V1";
    public static final String ADVANCE_REQUEST_SCHEMA =
            "CAREER_CALENDAR_ADVANCE_REQUEST_V1";
    public static final String ADVANCE_RESPONSE_SCHEMA =
            "CAREER_CALENDAR_ADVANCE_RESPONSE_V1";
    public static final String COMPETITION_COMMAND_REQUEST_SCHEMA =
            "CAREER_COMPETITION_COMMAND_REQUEST_V1";
    public static final String COMPETITION_COMMAND_RESPONSE_SCHEMA =
            "CAREER_COMPETITION_COMMAND_RESPONSE_V1";
    public static final String ERROR_SCHEMA = "CAREER_API_ERROR_V1";

    private CareerApiV1Dtos() {}

    public record CreateRequest(
            String schemaVersion,
            String saveName,
            String managerName,
            String managedTeamCode,
            String clientCommandId
    ) {}

    public record CreateResponse(
            String schemaVersion,
            boolean replayed,
            CareerView career
    ) {}

    public record AdvanceRequest(
            String schemaVersion,
            long expectedCalendarRevision,
            String mode,
            String clientCommandId
    ) {}

    public record AdvanceResponse(
            String schemaVersion,
            boolean replayed,
            boolean pending,
            String stopReason,
            boolean backgroundAccepted,
            AdvanceCommandResult commandResult,
            CalendarView calendar
    ) {}

    public record CompetitionCommandRequest(
            String schemaVersion,
            long expectedCompetitionRevision,
            String clientCommandId
    ) {}

    public record CompetitionCommandResponse(
            String schemaVersion,
            String executionMode,
            String fixtureId,
            String matchId,
            String seriesId,
            String bindingHash,
            String jobId,
            String status,
            boolean replayed,
            boolean backgroundAccepted,
            String failureCode
    ) {}

    public record AdvanceCommandResult(
            String clientCommandId,
            String mode,
            long expectedCalendarRevision,
            String commandStatus,
            LocalDate resultingDate,
            long resultingCalendarRevision,
            String resultingStateHash,
            String resultingLifecycleStatus,
            String resultingBlockingReason,
            String stopReason,
            boolean pending,
            boolean backgroundAccepted,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt,
            OffsetDateTime completedAt
    ) {}

    public record ListResponse(
            String schemaVersion,
            List<CareerSummary> careers,
            int currentCount,
            int maximumCount,
            int remainingCount
    ) {
        public ListResponse { careers = List.copyOf(careers); }
    }

    public record CareerSummary(
            String careerId,
            String saveName,
            String managerName,
            String managedTeamCode,
            LocalDate currentDate,
            String leagueId,
            String seasonId,
            String lifecycleStatus,
            String resumeKind,
            OffsetDateTime updatedAt
    ) {}

    public record CareerView(
            String schemaVersion,
            String careerId,
            String saveName,
            String managerName,
            String managedTeamCode,
            LocalDate startDate,
            LocalDate currentDate,
            String lifecycleStatus,
            long revision,
            String leagueId,
            String seasonId,
            String rootSeedAlgorithmId,
            String rootSeed,
            String leagueFrozenSnapshotIdentity,
            String leagueProductDecisionIdentity,
            String referenceCatalogVersion,
            String referenceCatalogHash,
            String bindingSchemaVersion,
            String bindingHash,
            ResumeProjection resume,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt
    ) {}

    public record ResumeProjection(
            String kind,
            String leagueId,
            String seasonId,
            String fixtureId,
            String seriesId,
            String seasonLifecycleStatus,
            int currentRound,
            long lifecycleRevision,
            long standingsRevision,
            List<String> allowedCommands
    ) {
        public ResumeProjection { allowedCommands = List.copyOf(allowedCommands); }
    }

    public record CalendarView(
            String schemaVersion,
            String careerId,
            int activeCalendarSeasonYear,
            LocalDate currentDate,
            long calendarRevision,
            String lifecycleStatus,
            String blockingReason,
            String calendarStateHash,
            String stateHashAlgorithm,
            CalendarProvenance provenance,
            String projectionStatus,
            CalendarEvent currentEvent,
            CalendarEvent nextEvent,
            CalendarStage currentStage,
            CalendarStage nextStage,
            List<CalendarEvent> upcomingEvents,
            FixtureOverlay fixtureOverlay,
            List<CalendarFixture> upcomingFixtures,
            CalendarFixture nextManagedFixture,
            List<String> allowedAdvanceModes,
            PendingAdvance activePendingAdvance,
            String advanceRecoveryStatus,
            CompetitionView competition,
            List<QualificationEdge> qualificationEdges,
            List<PendingOfficialField> pendingOfficialFields,
            List<SourceDataNote> sourceDataNotes
    ) {
        public CalendarView {
            upcomingEvents = List.copyOf(upcomingEvents);
            upcomingFixtures = List.copyOf(upcomingFixtures);
            allowedAdvanceModes = List.copyOf(allowedAdvanceModes);
            qualificationEdges = List.copyOf(qualificationEdges);
            pendingOfficialFields = List.copyOf(pendingOfficialFields);
            sourceDataNotes = List.copyOf(sourceDataNotes);
        }
    }

    public record CalendarProvenance(
            int referenceYear,
            String sourceAsOf,
            String referenceCatalogSnapshotAt,
            String templateVersion,
            String templateHash,
            String projectionPolicy,
            String anchorAlgorithm,
            int sourceCount,
            int calendarDefinitionCount,
            int qualificationEdgeCount,
            int derivedRestWindowCount,
            int pendingOfficialFieldCount
    ) {}

    public record CalendarEvent(
            String eventId,
            String templateId,
            String sourceReferenceId,
            String displayNameKo,
            LocalDate startDate,
            LocalDate endDate,
            String timezone,
            String timezoneScope,
            List<String> locations,
            String officialStatus,
            String projectionStatus,
            String participationType,
            String participation,
            Integer teamCount,
            Integer seriesCount,
            String format,
            List<String> seriesRules,
            String draftMode,
            String draftStatus,
            String executionStatus,
            List<CalendarStage> stages
    ) {
        public CalendarEvent {
            locations = List.copyOf(locations);
            seriesRules = List.copyOf(seriesRules);
            stages = List.copyOf(stages);
        }
    }

    public record CalendarStage(
            String stageId,
            String displayNameKo,
            LocalDate startDate,
            LocalDate endDate,
            String officialStatus,
            Integer teamCount,
            Integer seriesCount,
            String format,
            List<String> seriesRules
    ) {
        public CalendarStage { seriesRules = List.copyOf(seriesRules); }
    }

    public record FixtureOverlay(
            String schemaVersion,
            String allocationPolicy,
            String overlayHash,
            String scheduleStatus,
            FixtureOverlayProvenanceV2 provenanceV2
    ) {}

    public record FixtureOverlayProvenanceV2(
            String schemaVersion,
            String hashAlgorithm,
            String leagueId,
            String seasonId,
            String scheduleIdentity,
            String overlayHash
    ) {}

    public record PendingAdvance(
            String clientCommandId,
            String mode,
            long expectedCalendarRevision,
            String commandStatus,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt
    ) {}

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
            List<CompetitionQualificationOutput> qualificationOutputs,
            List<CompetitionStanding> groupStandings,
            List<CompetitionSeed> currentSeeds,
            boolean externalExecutionLimited,
            PendingCompetitionCommand activePendingCommand,
            List<String> allowedCommands,
            List<com.lolfm.career.CareerCompetitionRelationalStore.DomesticDecisionView> domesticRankingDecisions,
            com.lolfm.career.CareerCompetitionRelationalStore.FinalRankingView finalRanking,
            String domesticRuleCompatibility,
            List<com.lolfm.career.CareerCompetitionRelationalStore.InternationalView> internationalCompetitions
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
            String firstSelectorType,
            String firstSelectorValue,
            String secondSelectorType,
            String secondSelectorValue,
            String stageId,
            String blockingReason,
            String bindingHash,
            String jobId,
            String jobStatus,
            String resultApplicationStatus,
            String failureCode
    ) {}

    public record CompetitionQualificationOutput(
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

    public record CalendarFixture(
            String fixtureId,
            int roundNumber,
            LocalDate date,
            String scheduleStatus,
            String executionMode,
            String firstTeamCode,
            String secondTeamCode,
            String lifecycleStatus,
            String seriesId,
            String jobStatus,
            boolean pendingOutbox
    ) {}

    public record PendingOfficialField(String id, String field, String reason) {}
    public record QualificationEdge(
            String fromTemplateId,
            String toTemplateId,
            String rule,
            String officialStatus
    ) {}
    public record SourceDataNote(
            String subject, String status, int sourceReferenceYear,
            String ruleVersion, List<String> blockers
    ) {
        public SourceDataNote { blockers = List.copyOf(blockers); }
    }

    public record ErrorResponse(
            String schemaVersion,
            String code,
            String field,
            String message
    ) {}
}
