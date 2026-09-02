package com.lolfm.application;

import com.lolfm.career.CareerApplicationService;
import com.lolfm.dto.CareerApiV1Dtos;
import java.util.List;
import org.springframework.stereotype.Component;

/** Field-by-field Career API projection; no JDBC or domain object is exposed. */
@Component
public final class CareerApiV1ResponseMapper {
    public CareerApiV1Dtos.CreateResponse created(
            CareerApplicationService.CreateResult result
    ) {
        return new CareerApiV1Dtos.CreateResponse(
                CareerApiV1Dtos.CREATE_RESPONSE_SCHEMA, result.replayed(),
                view(result.career()));
    }

    public CareerApiV1Dtos.ListResponse list(
            List<CareerApplicationService.CareerViewState> careers
    ) {
        return new CareerApiV1Dtos.ListResponse(CareerApiV1Dtos.LIST_SCHEMA,
                careers.stream().map(this::summary).toList());
    }

    public CareerApiV1Dtos.CareerView view(
            CareerApplicationService.CareerViewState state
    ) {
        var career = state.career();
        return new CareerApiV1Dtos.CareerView(CareerApiV1Dtos.VIEW_SCHEMA,
                career.careerId(), career.saveName(), career.managerName(),
                career.managedTeamCode(), career.startDate(), career.currentDate(),
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
                career.currentDate(), career.leagueId(), career.seasonId(),
                career.lifecycleStatus(), state.linkedSeason().resume().kind(),
                career.updatedAt());
    }

    private CareerApiV1Dtos.ResumeProjection resume(
            CareerApplicationService.ResumeState resume
    ) {
        return new CareerApiV1Dtos.ResumeProjection(resume.kind(), resume.leagueId(),
                resume.seasonId(), resume.fixtureId(), resume.seriesId(),
                resume.seasonLifecycleStatus(), resume.currentRound(),
                resume.lifecycleRevision(), resume.standingsRevision());
    }
}
