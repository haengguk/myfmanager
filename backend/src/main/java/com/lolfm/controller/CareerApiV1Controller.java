package com.lolfm.controller;

import com.lolfm.application.CareerApiV1ResponseMapper;
import com.lolfm.career.CareerApplicationService;
import com.lolfm.career.CareerCalendarApplicationService;
import com.lolfm.career.CareerCompetitionBackgroundExecutionPort;
import com.lolfm.career.CareerCompetitionExecutionService;
import com.lolfm.career.CareerException;
import com.lolfm.dto.CareerApiV1Dtos;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/careers")
@CrossOrigin(origins = "http://localhost:5173")
public final class CareerApiV1Controller {
    private final CareerApplicationService careers;
    private final CareerApiV1RequestParser parser;
    private final CareerApiV1ResponseMapper mapper;
    private final CareerCalendarApplicationService calendar;
    private final CareerCompetitionExecutionService competitions;
    private final CareerCompetitionBackgroundExecutionPort competitionBackground;

    public CareerApiV1Controller(
            CareerApplicationService careers,
            CareerApiV1RequestParser parser,
            CareerApiV1ResponseMapper mapper,
            CareerCalendarApplicationService calendar,
            CareerCompetitionExecutionService competitions,
            CareerCompetitionBackgroundExecutionPort competitionBackground
    ) {
        this.careers = careers;
        this.parser = parser;
        this.mapper = mapper;
        this.calendar = calendar;
        this.competitions = competitions;
        this.competitionBackground = competitionBackground;
    }

    @PostMapping
    public ResponseEntity<CareerApiV1Dtos.CreateResponse> create(
            @RequestBody byte[] body
    ) {
        CareerApplicationService.CreateResult result = careers.create(parser.create(body));
        return ResponseEntity.status(result.replayed() ? 200 : 201)
                .body(mapper.created(result));
    }

    @GetMapping
    public CareerApiV1Dtos.ListResponse list() {
        return mapper.list(careers.list());
    }

    @GetMapping("/{careerId}")
    public CareerApiV1Dtos.CareerView get(@PathVariable String careerId) {
        return mapper.view(careers.get(careerId));
    }

    @GetMapping("/{careerId}/calendar")
    public CareerApiV1Dtos.CalendarView calendar(@PathVariable String careerId) {
        return mapper.calendar(calendar.view(careers.get(careerId).career()));
    }

    @PostMapping("/{careerId}/advance")
    public ResponseEntity<CareerApiV1Dtos.AdvanceResponse> advance(
            @PathVariable String careerId,
            @RequestBody byte[] body
    ) {
        CareerApiV1Dtos.AdvanceRequest request = parser.advance(body);
        CareerCalendarApplicationService.AdvanceResult result = calendar.advance(
                careers.get(careerId).career(), request.schemaVersion(),
                request.expectedCalendarRevision(), request.mode(),
                request.clientCommandId());
        return ResponseEntity.status(result.httpStatus()).body(mapper.advanced(result));
    }

    @PostMapping("/{careerId}/competition/start-or-resume")
    public ResponseEntity<CareerApiV1Dtos.CompetitionCommandResponse>
            startOrResumeCompetition(
            @PathVariable String careerId,
            @RequestBody byte[] body
    ) {
        CareerApiV1Dtos.CompetitionCommandRequest request =
                parser.competitionCommand(body);
        var career = careers.get(careerId);
        var calendarState = calendar.view(career.career()).state();
        int competitionSeasonYear = calendarState.seasonYear();
        CareerCompetitionExecutionService.ExecutionResult result;
        try {
            result = competitions.startOrResume(career.career(),
                    competitionSeasonYear,
                    calendarState.currentDate(),
                    request.expectedCompetitionRevision(),
                    request.clientCommandId());
        } catch (IllegalStateException conflict) {
            throw competitionFailure(conflict);
        }
        boolean accepted = submitCompetitionWork(result);
        int status = "FULL_AUTO".equals(result.executionMode())
                && List.of("PENDING", "RUNNING").contains(result.status())
                ? 202 : 200;
        return ResponseEntity.status(status).body(mapper.competitionCommand(
                result, accepted));
    }

    @PostMapping("/{careerId}/competition/reconcile")
    public ResponseEntity<CareerApiV1Dtos.CompetitionCommandResponse>
            reconcileCompetition(
            @PathVariable String careerId,
            @RequestBody byte[] body
    ) {
        CareerApiV1Dtos.CompetitionCommandRequest request =
                parser.competitionCommand(body);
        var career = careers.get(careerId);
        int competitionSeasonYear = calendar.view(career.career()).state().seasonYear();
        CareerCompetitionExecutionService.ExecutionResult result;
        try {
            result = competitions.reconcile(careerId, competitionSeasonYear,
                    request.expectedCompetitionRevision(),
                    request.clientCommandId());
        } catch (IllegalStateException conflict) {
            throw competitionFailure(conflict);
        }
        boolean accepted = submitCompetitionWork(result);
        int status = "FULL_AUTO".equals(result.executionMode())
                && List.of("PENDING", "RUNNING").contains(result.status())
                ? 202 : 200;
        return ResponseEntity.status(status).body(mapper.competitionCommand(
                result, accepted));
    }

    private boolean submitCompetitionWork(
            CareerCompetitionExecutionService.ExecutionResult result
    ) {
        if (!"FULL_AUTO".equals(result.executionMode())
                || !List.of("PENDING", "RUNNING").contains(result.status())) {
            return false;
        }
        if (!competitionBackground.submit(result.jobId())) {
            throw CareerException.competitionBackgroundUnavailable();
        }
        return true;
    }

    private static RuntimeException competitionFailure(
            IllegalStateException failure
    ) {
        if ("CAREER_COMPETITION_STALE_REVISION".equals(failure.getMessage())) {
            return CareerException.competitionStaleRevision();
        }
        if (List.of("COMPETITION_COMMAND_ID_CONFLICT",
                "CAREER_COMPETITION_NO_PENDING_FIXTURE",
                "CAREER_COMPETITION_FIXTURE_NOT_READY",
                "CAREER_COMPETITION_FIXTURE_NOT_DUE",
                "COMPETITION_FIXTURE_ALREADY_DISPATCHED",
                "COMPETITION_AUTO_JOB_NOT_FOUND").contains(failure.getMessage())) {
            return CareerException.competitionCommandConflict();
        }
        return failure;
    }
}
