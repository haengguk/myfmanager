package com.lolfm.application;

import com.lolfm.career.CareerApplicationService;
import com.lolfm.career.CareerCalendarApplicationService;
import com.lolfm.career.CareerCalendarTemplate;
import com.lolfm.dto.CareerApiV1Dtos;
import org.springframework.stereotype.Component;

/** Field-by-field Career API projection; no JDBC or domain object is exposed. */
@Component
public final class CareerApiV1ResponseMapper {
    public CareerApiV1Dtos.AdvanceResponse advanced(
            CareerCalendarApplicationService.AdvanceResult result
    ) {
        return new CareerApiV1Dtos.AdvanceResponse(
                CareerApiV1Dtos.ADVANCE_RESPONSE_SCHEMA, result.replayed(),
                result.pending(), result.stopReason(), result.backgroundAccepted(),
                commandResult(result.commandResult()), calendar(result.calendar()));
    }

    public CareerApiV1Dtos.CalendarView calendar(
            CareerCalendarApplicationService.CalendarView view
    ) {
        var state = view.state();
        var counts = view.counts();
        CareerApiV1Dtos.CalendarProvenance provenance =
                new CareerApiV1Dtos.CalendarProvenance(view.referenceYear(),
                        view.sourceAsOf(), view.referenceCatalogSnapshotAt(),
                        state.templateVersion(), state.templateHash(),
                        state.projectionPolicy(), state.anchorAlgorithm(), counts.sources(),
                        counts.calendarDefinitions(), counts.qualificationEdges(),
                        counts.derivedRestWindows(), counts.pendingOfficialFields());
        return new CareerApiV1Dtos.CalendarView(CareerApiV1Dtos.CALENDAR_VIEW_SCHEMA,
                state.careerId(), state.seasonYear(), state.currentDate(),
                state.calendarRevision(), state.lifecycleStatus(), view.blockingReason(),
                state.calendarStateHash(), view.stateHashAlgorithm(), provenance,
                view.projectionStatus(), event(view.currentEvent()), event(view.nextEvent()),
                stage(view.currentStage()), stage(view.nextStage()),
                view.upcomingEvents().stream().map(this::event).toList(),
                new CareerApiV1Dtos.FixtureOverlay(view.fixtureOverlay().schemaVersion(),
                        view.fixtureOverlay().allocationPolicy(),
                        view.fixtureOverlay().overlayHash(),
                        "GAME_DERIVED_SCHEDULE_POLICY",
                        new CareerApiV1Dtos.FixtureOverlayProvenanceV2(
                                view.fixtureOverlay().provenanceV2().schemaVersion(),
                                view.fixtureOverlay().provenanceV2().hashAlgorithm(),
                                view.fixtureOverlay().provenanceV2().leagueId(),
                                view.fixtureOverlay().provenanceV2().seasonId(),
                                view.fixtureOverlay().provenanceV2().scheduleIdentity(),
                                view.fixtureOverlay().provenanceV2().overlayHash())),
                view.upcomingFixtures().stream().map(this::fixture).toList(),
                fixture(view.nextManagedFixture()), view.allowedAdvanceModes(),
                pending(view.activePendingAdvance()),
                competition(view.competition()),
                view.qualificationEdges().stream().map(value ->
                        new CareerApiV1Dtos.QualificationEdge(value.from(), value.to(),
                                value.rule(), value.officialStatus())).toList(),
                view.pendingOfficialFields().stream().map(value ->
                        new CareerApiV1Dtos.PendingOfficialField(value.id(), value.field(),
                                value.reason())).toList(),
                view.sourceDataNotes().stream().map(value ->
                        new CareerApiV1Dtos.SourceDataNote(value.subject(), value.status()))
                        .toList());
    }

    private CareerApiV1Dtos.AdvanceCommandResult commandResult(
            CareerCalendarApplicationService.CommandResult value
    ) {
        var receipt = value.receipt();
        return new CareerApiV1Dtos.AdvanceCommandResult(receipt.clientCommandId(),
                receipt.mode(), receipt.expectedRevision(), receipt.status(),
                receipt.resultingDate(), receipt.resultingRevision(),
                receipt.resultingStateHash(), receipt.resultingLifecycleStatus(),
                receipt.resultingBlockingReason(), receipt.stopReason(),
                "PENDING".equals(receipt.status()), value.backgroundAccepted(),
                receipt.createdAt(), receipt.updatedAt(), receipt.completedAt());
    }

    private CareerApiV1Dtos.PendingAdvance pending(
            com.lolfm.career.CareerCalendarRelationalStore.PendingAdvance value
    ) {
        return value == null ? null : new CareerApiV1Dtos.PendingAdvance(
                value.clientCommandId(), value.mode(), value.expectedRevision(),
                value.status(), value.createdAt(), value.updatedAt());
    }

    private CareerApiV1Dtos.CompetitionView competition(
            com.lolfm.career.CareerCompetitionApplicationService.CompetitionView value
    ) {
        return new CareerApiV1Dtos.CompetitionView(value.schemaVersion(),
                value.calendarSeasonYear(), value.ruleResourceHash(), value.ruleVersion(),
                value.gamePolicyVersion(), value.projectionPolicy(),
                value.r3r4AllocationPolicy(), value.lifecycleStatus(), value.revision(),
                value.stateHash(), competitionSummary(value.currentCompetition()),
                competitionSummary(value.nextCompetition()),
                competitionFixture(value.nextFixture()),
                value.qualificationOutputs().stream().map(output ->
                        new CareerApiV1Dtos.CompetitionQualificationOutput(
                                output.competitionId(), output.outputId(),
                                output.teamCode())).toList(),
                value.externalExecutionLimited(), value.activePendingCommand() == null
                ? null : new CareerApiV1Dtos.PendingCompetitionCommand(
                        value.activePendingCommand().clientCommandId(),
                        value.activePendingCommand().competitionId(),
                        value.activePendingCommand().matchId(),
                        value.activePendingCommand().commandStatus()),
                value.allowedCommands());
    }

    private CareerApiV1Dtos.CompetitionSummary competitionSummary(
            com.lolfm.career.CareerCompetitionApplicationService.CompetitionSummary value
    ) {
        return value == null ? null : new CareerApiV1Dtos.CompetitionSummary(
                value.competitionId(), value.stageId(), value.ruleStatus(),
                value.lifecycleStatus(),
                value.blockingReason(), value.revision(), value.stateHash(),
                value.completedFixtures(), value.totalFixtures());
    }

    private CareerApiV1Dtos.CompetitionFixture competitionFixture(
            com.lolfm.career.CareerCompetitionApplicationService.CompetitionFixture value
    ) {
        return value == null ? null : new CareerApiV1Dtos.CompetitionFixture(
                value.competitionId(), value.matchId(), value.fixtureId(),
                value.seriesId(), value.date(), value.scheduleStatus(),
                value.seriesFormat(), value.hardFearless(), value.firstTeamCode(),
                value.secondTeamCode(), value.executionMode(), value.lifecycleStatus(),
                value.managedTeamIncluded(), value.rootSeed(), value.seedAlgorithm());
    }

    public CareerApiV1Dtos.CreateResponse created(
            CareerApplicationService.CreateResult result
    ) {
        return new CareerApiV1Dtos.CreateResponse(
                CareerApiV1Dtos.CREATE_RESPONSE_SCHEMA, result.replayed(),
                view(result.career()));
    }

    public CareerApiV1Dtos.ListResponse list(
            CareerApplicationService.CareerListState state
    ) {
        return new CareerApiV1Dtos.ListResponse(CareerApiV1Dtos.LIST_SCHEMA,
                state.careers().stream().map(this::summary).toList(),
                state.currentCount(), state.maximumCount(), state.remainingCount());
    }

    public CareerApiV1Dtos.CareerView view(
            CareerApplicationService.CareerViewState state
    ) {
        var career = state.career();
        return new CareerApiV1Dtos.CareerView(CareerApiV1Dtos.VIEW_SCHEMA,
                career.careerId(), career.saveName(), career.managerName(),
                career.managedTeamCode(), career.startDate(), state.currentGameDate(),
                career.lifecycleStatus(), career.revision(), career.leagueId(),
                career.seasonId(), career.seedAlgorithmId(),
                Long.toString(career.rootSeed()), career.frozenSnapshotHash(),
                career.productDecisionHash(), career.referenceCatalogVersion(),
                career.referenceCatalogHash(), career.bindingSchema(),
                career.bindingHash(), resume(state.linkedSeason().resume()),
                career.createdAt(), career.updatedAt());
    }

    private CareerApiV1Dtos.CareerSummary summary(
            CareerApplicationService.CareerViewState state
    ) {
        var career = state.career();
        return new CareerApiV1Dtos.CareerSummary(career.careerId(),
                career.saveName(), career.managerName(), career.managedTeamCode(),
                state.currentGameDate(), career.leagueId(), career.seasonId(),
                career.lifecycleStatus(), state.linkedSeason().resume().kind(),
                career.updatedAt());
    }

    private CareerApiV1Dtos.ResumeProjection resume(
            CareerApplicationService.ResumeState resume
    ) {
        return new CareerApiV1Dtos.ResumeProjection(resume.kind(), resume.leagueId(),
                resume.seasonId(), resume.fixtureId(), resume.seriesId(),
                resume.seasonLifecycleStatus(), resume.currentRound(),
                resume.lifecycleRevision(), resume.standingsRevision(),
                resume.allowedCommands());
    }

    private CareerApiV1Dtos.CalendarEvent event(
            CareerCalendarTemplate.ProjectedEvent event
    ) {
        if (event == null) return null;
        return new CareerApiV1Dtos.CalendarEvent(event.eventId(), event.templateId(),
                event.sourceReferenceId(), event.displayNameKo(), event.startDate(),
                event.endDate(), event.timezone(), event.timezoneScope(),
                event.locations(), event.officialStatus(), event.projectionStatus(),
                event.participationType(), event.participation(), event.teamCount(),
                event.seriesCount(), event.format(), event.seriesRules(), event.draftMode(),
                event.draftStatus(), event.executionStatus(), event.stages().stream()
                .map(value -> new CareerApiV1Dtos.CalendarStage(value.stageId(),
                        value.displayNameKo(), value.startDate(), value.endDate(),
                        value.officialStatus(), value.teamCount(), value.seriesCount(),
                        value.format(), value.seriesRules())).toList());
    }

    private CareerApiV1Dtos.CalendarFixture fixture(
            CareerCalendarApplicationService.FixtureView fixture
    ) {
        if (fixture == null) return null;
        return new CareerApiV1Dtos.CalendarFixture(fixture.fixtureId(),
                fixture.roundNumber(), fixture.date(), fixture.scheduleStatus(),
                fixture.executionMode(), fixture.firstTeamCode(), fixture.secondTeamCode(),
                fixture.lifecycleStatus(), fixture.seriesId(), fixture.jobStatus(),
                fixture.pendingOutbox());
    }

    private CareerApiV1Dtos.CalendarStage stage(
            CareerCalendarTemplate.ProjectedStage value
    ) {
        return value == null ? null : new CareerApiV1Dtos.CalendarStage(value.stageId(),
                value.displayNameKo(), value.startDate(), value.endDate(),
                value.officialStatus(), value.teamCount(), value.seriesCount(),
                value.format(), value.seriesRules());
    }
}
