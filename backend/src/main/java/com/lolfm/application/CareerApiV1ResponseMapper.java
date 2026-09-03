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
                calendar(result.calendar()));
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
                state.calendarRevision(), state.lifecycleStatus(), state.blockingReason(),
                state.calendarStateHash(), view.stateHashAlgorithm(), provenance,
                view.projectionStatus(), event(view.currentEvent()), event(view.nextEvent()),
                stage(view.currentStage()), stage(view.nextStage()),
                view.upcomingEvents().stream().map(this::event).toList(),
                new CareerApiV1Dtos.FixtureOverlay(view.fixtureOverlay().schemaVersion(),
                        view.fixtureOverlay().allocationPolicy(),
                        view.fixtureOverlay().overlayHash(),
                        "GAME_DERIVED_SCHEDULE_POLICY"),
                view.upcomingFixtures().stream().map(this::fixture).toList(),
                fixture(view.nextManagedFixture()), view.allowedAdvanceModes(),
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
